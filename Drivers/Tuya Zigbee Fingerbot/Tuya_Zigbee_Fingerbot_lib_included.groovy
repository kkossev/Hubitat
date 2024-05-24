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
 * ver. 3.2.0  2024-04-06 kkossev  - (dev. branch) commonLib 3.2.0 allignment
 * 
 *
 *                                   TODO:
 */

static String version() { '3.2.0' }
static String timeStamp() { '2024/05/24 11:43 AM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput






deviceType = 'Fingerbot'
@Field static final String DEVICE_TYPE = 'Fingerbot'

metadata {
    definition(
        name: 'Tuya Zigbee Fingerbot',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Fingerbot/Tuya_Zigbee_Fingerbot_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
       // capability 'Actuator'
       // capability 'Battery'
        //capability 'Switch'
       // capability "PushableButton"
       // capability 'Momentary'

        //attribute 'batteryVoltage', 'number'
        /*
        if (_THREE_STATE == true) {
            attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String>
        }
        */

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

void customInitEvents(boolean fullInit=false) {
    logDebug "customInitEvents(${fullInit})"
    sendNumberOfButtonsEvent(1)     // defind in buttonLib
    sendSupportedButtonValuesEvent(['pushed'])     // defind in buttonLib
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
/*
List<String> customRefresh() {
    List<String> cmds = []
    //cmds = zigbee.command(0xEF00, 0x02)
    logDebug "customRefresh() (n/a) : ${cmds} "
    return cmds
}
*/

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', documentationLink: '', // library marker kkossev.commonLib, line 4
    version: '3.2.0' // library marker kkossev.commonLib, line 5
) // library marker kkossev.commonLib, line 6
/* // library marker kkossev.commonLib, line 7
  *  Common ZCL Library // library marker kkossev.commonLib, line 8
  * // library marker kkossev.commonLib, line 9
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.commonLib, line 10
  *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.commonLib, line 11
  * // library marker kkossev.commonLib, line 12
  *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.commonLib, line 13
  * // library marker kkossev.commonLib, line 14
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.commonLib, line 15
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.commonLib, line 16
  *  for the specific language governing permissions and limitations under the License. // library marker kkossev.commonLib, line 17
  * // library marker kkossev.commonLib, line 18
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project). // library marker kkossev.commonLib, line 19
  * For a big portions of code all credits go to Jonathan Bradshaw. // library marker kkossev.commonLib, line 20
  * // library marker kkossev.commonLib, line 21
  * // library marker kkossev.commonLib, line 22
  * ver. 1.0.0  2022-06-18 kkossev  - first beta version // library marker kkossev.commonLib, line 23
  * ver. 2.0.0  2023-05-08 kkossev  - first published version 2.x.x // library marker kkossev.commonLib, line 24
  * ver. 2.1.6  2023-11-06 kkossev  - last update on version 2.x.x // library marker kkossev.commonLib, line 25
  * ver. 3.0.0  2023-11-16 kkossev  - first version 3.x.x // library marker kkossev.commonLib, line 26
  * ver. 3.0.1  2023-12-06 kkossev  - info event renamed to Status; txtEnable and logEnable moved to the custom driver settings; 0xFC11 cluster; logEnable is false by default; checkDriverVersion is called on updated() and on healthCheck(); // library marker kkossev.commonLib, line 27
  * ver. 3.0.2  2023-12-17 kkossev  - configure() changes; Groovy Lint, Format and Fix v3.0.0 // library marker kkossev.commonLib, line 28
  * ver. 3.0.3  2024-03-17 kkossev  - more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization // library marker kkossev.commonLib, line 29
  * ver. 3.0.4  2024-04-02 kkossev  - removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; parseE002Cluster; // library marker kkossev.commonLib, line 30
  * ver. 3.0.5  2024-04-05 kkossev  - button methods bug fix; configure() bug fix; handlePm25Event bug fix; // library marker kkossev.commonLib, line 31
  * ver. 3.0.6  2024-04-08 kkossev  - removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups and level and battery methods to dedicated libs + setLevel bug fix; // library marker kkossev.commonLib, line 32
  * ver. 3.0.7  2024-04-23 kkossev  - tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr, activeEpRqCtr; trace ZDO commands; added 0x0406 OccupancyCluster; reduced debug for chatty devices; // library marker kkossev.commonLib, line 33
  * ver. 3.1.0  2024-04-28 kkossev  - unnecesery unschedule() speed optimization; added syncTuyaDateTime(); tuyaBlackMagic() initialization bug fix. // library marker kkossev.commonLib, line 34
  * ver. 3.1.1  2024-05-05 kkossev  - getTuyaAttributeValue bug fix; added customCustomParseIlluminanceCluster method // library marker kkossev.commonLib, line 35
  * ver. 3.2.0  2024-05-23 kkossev  - (dev.branch) W.I.P - standardParse____Cluster and customParse___Cluster methods; // library marker kkossev.commonLib, line 36
  * // library marker kkossev.commonLib, line 37
  *                                   TODO: move onOff methods to a new library // library marker kkossev.commonLib, line 38
  *                                   TODO: rename all custom handlers in the libs to statdndardParseXXX !! W.I.P. // library marker kkossev.commonLib, line 39
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 40
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 41
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib // library marker kkossev.commonLib, line 42
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 43
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 44
  * // library marker kkossev.commonLib, line 45
*/ // library marker kkossev.commonLib, line 46

String commonLibVersion() { '3.2.0' } // library marker kkossev.commonLib, line 48
String commonLibStamp() { '2024/05/23 11:00 PM' } // library marker kkossev.commonLib, line 49

import groovy.transform.Field // library marker kkossev.commonLib, line 51
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 52
import hubitat.device.Protocol // library marker kkossev.commonLib, line 53
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 54
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 55
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 56
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 57
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 58
import java.math.BigDecimal // library marker kkossev.commonLib, line 59

metadata { // library marker kkossev.commonLib, line 61
        if (_DEBUG) { // library marker kkossev.commonLib, line 62
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 63
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 64
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 65
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 66
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 67
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 68
            ] // library marker kkossev.commonLib, line 69
        } // library marker kkossev.commonLib, line 70

        // common capabilities for all device types // library marker kkossev.commonLib, line 72
        capability 'Configuration' // library marker kkossev.commonLib, line 73
        capability 'Refresh' // library marker kkossev.commonLib, line 74
        capability 'Health Check' // library marker kkossev.commonLib, line 75

        // common attributes for all device types // library marker kkossev.commonLib, line 77
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 78
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 79
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 80

        // common commands for all device types // library marker kkossev.commonLib, line 82
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 83

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 85
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 86

    preferences { // library marker kkossev.commonLib, line 88
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 89
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 90
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 91

        if (device) { // library marker kkossev.commonLib, line 93
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 94
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 95
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 96
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 97
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 98
            } // library marker kkossev.commonLib, line 99
        } // library marker kkossev.commonLib, line 100
    } // library marker kkossev.commonLib, line 101
} // library marker kkossev.commonLib, line 102

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 104
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 105
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 106
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 107
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 108
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 109
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 110
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 111
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 112
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 113
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 114

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 116
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 117
] // library marker kkossev.commonLib, line 118
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 119
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 120
] // library marker kkossev.commonLib, line 121

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 123
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 124
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 125
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 126
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 127
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 128
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 129
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 130
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 131
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 132
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 133
] // library marker kkossev.commonLib, line 134

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 136
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 137
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 138
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 139
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 140
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 141

/** // library marker kkossev.commonLib, line 143
 * Parse Zigbee message // library marker kkossev.commonLib, line 144
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 145
 */ // library marker kkossev.commonLib, line 146
void parse(final String description) { // library marker kkossev.commonLib, line 147
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 148
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 149
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 150
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 151

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 153
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 154
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 155
            parseIasMessage(description) // library marker kkossev.commonLib, line 156
        } // library marker kkossev.commonLib, line 157
        else { // library marker kkossev.commonLib, line 158
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 159
        } // library marker kkossev.commonLib, line 160
        return // library marker kkossev.commonLib, line 161
    } // library marker kkossev.commonLib, line 162
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 163
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 164
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 165
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 166
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 167
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 168
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 169
        return // library marker kkossev.commonLib, line 170
    } // library marker kkossev.commonLib, line 171

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 173
        return // library marker kkossev.commonLib, line 174
    } // library marker kkossev.commonLib, line 175
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 176

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 178
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 179

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 181
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 182
        return // library marker kkossev.commonLib, line 183
    } // library marker kkossev.commonLib, line 184
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 185
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 186
        return // library marker kkossev.commonLib, line 187
    } // library marker kkossev.commonLib, line 188
    // // library marker kkossev.commonLib, line 189
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 190
    // // library marker kkossev.commonLib, line 191
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 192
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 193
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 194
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 195
            break // library marker kkossev.commonLib, line 196
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 197
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 198
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 199
            break // library marker kkossev.commonLib, line 200
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 201
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 202
            break // library marker kkossev.commonLib, line 203
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 204
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 205
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 206
            break // library marker kkossev.commonLib, line 207
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 208
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 209
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 210
            break // library marker kkossev.commonLib, line 211
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 212
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 213
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 214
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 215
            } // library marker kkossev.commonLib, line 216
            break // library marker kkossev.commonLib, line 217
        default: // library marker kkossev.commonLib, line 218
            if (settings.logEnable) { // library marker kkossev.commonLib, line 219
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 220
            } // library marker kkossev.commonLib, line 221
            break // library marker kkossev.commonLib, line 222
    } // library marker kkossev.commonLib, line 223
} // library marker kkossev.commonLib, line 224

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 226
    0x0000: 'Basic', // library marker kkossev.commonLib, line 227
    0x0001: 'Power', // library marker kkossev.commonLib, line 228
    0x0003: 'Identify', // library marker kkossev.commonLib, line 229
    0x000C: 'AnalogInput', // library marker kkossev.commonLib, line 230
    0x0006: 'OnOff', // library marker kkossev.commonLib, line 231
    0x0008: 'LevelControl', // library marker kkossev.commonLib, line 232
    0x0012: 'MultistateInput', // library marker kkossev.commonLib, line 233
    0x0201: 'Thermostat', // library marker kkossev.commonLib, line 234
    0x0300: 'ColorControl', // library marker kkossev.commonLib, line 235
    0x0400: 'Illuminance', // library marker kkossev.commonLib, line 236
    0x0402: 'Temperature', // library marker kkossev.commonLib, line 237
    0x0405: 'Humidity', // library marker kkossev.commonLib, line 238
    0x0406: 'Occupancy', // library marker kkossev.commonLib, line 239
    0x042A: 'Pm25', // library marker kkossev.commonLib, line 240
    0xE002: 'E002', // library marker kkossev.commonLib, line 241
    0xEC03: 'EC03', // library marker kkossev.commonLib, line 242
    0xEF00: 'Tuya', // library marker kkossev.commonLib, line 243
    0xFC11: 'FC11', // library marker kkossev.commonLib, line 244
    0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 245
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 246
] // library marker kkossev.commonLib, line 247

boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 249
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 250
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 251
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 252
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 253
        return false // library marker kkossev.commonLib, line 254
    } // library marker kkossev.commonLib, line 255
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 256
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 257
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 258
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 259
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 260
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 261
        return true // library marker kkossev.commonLib, line 262
    } // library marker kkossev.commonLib, line 263
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 264
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 265
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 266
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 267
        return true // library marker kkossev.commonLib, line 268
    } // library marker kkossev.commonLib, line 269
    if (device?.getDataValue('model') != 'ZigUSB') {    // patch! // library marker kkossev.commonLib, line 270
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 271
    } // library marker kkossev.commonLib, line 272
    return false // library marker kkossev.commonLib, line 273
} // library marker kkossev.commonLib, line 274

static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 276
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 277
} // library marker kkossev.commonLib, line 278

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 280
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 281
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 282
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 283
    } // library marker kkossev.commonLib, line 284
    return false // library marker kkossev.commonLib, line 285
} // library marker kkossev.commonLib, line 286

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 288
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 289
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 290
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 291
    } // library marker kkossev.commonLib, line 292
    return false // library marker kkossev.commonLib, line 293
} // library marker kkossev.commonLib, line 294

boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 296
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 297
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 298
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 299
    } // library marker kkossev.commonLib, line 300
    return false // library marker kkossev.commonLib, line 301
} // library marker kkossev.commonLib, line 302

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 304
    0x0002: 'Node Descriptor Request', 0x0005: 'Active Endpoints Request', 0x0006: 'Match Descriptor Request', 0x0022: 'Unbind Request', 0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 305
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 306
    0x8021: 'Bind Response', 0x8022: 'Unbind Response', 0x8023: 'Bind Register Response', 0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 307
] // library marker kkossev.commonLib, line 308

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 310
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 311
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 312
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 313
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 314
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 315
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 316
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 317
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 318
    List<String> cmds = [] // library marker kkossev.commonLib, line 319
    switch (clusterId) { // library marker kkossev.commonLib, line 320
        case 0x0005 : // library marker kkossev.commonLib, line 321
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 322
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 323
            // send the active endpoint response // library marker kkossev.commonLib, line 324
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 325
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 326
            break // library marker kkossev.commonLib, line 327
        case 0x0006 : // library marker kkossev.commonLib, line 328
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 329
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 330
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 331
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 332
            break // library marker kkossev.commonLib, line 333
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 334
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 335
            if (settings?.logEnable) { log.info "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 336
            break // library marker kkossev.commonLib, line 337
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 338
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 339
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 340
            break // library marker kkossev.commonLib, line 341
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 342
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 343
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 344
            if (settings?.logEnable) { log.info "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 345
            break // library marker kkossev.commonLib, line 346
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 347
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 348
            break // library marker kkossev.commonLib, line 349
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 350
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 351
            if (settings?.logEnable) { log.info "${clusterInfo}" } // library marker kkossev.commonLib, line 352
            break // library marker kkossev.commonLib, line 353
        default : // library marker kkossev.commonLib, line 354
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 355
            break // library marker kkossev.commonLib, line 356
    } // library marker kkossev.commonLib, line 357
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 358
} // library marker kkossev.commonLib, line 359

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 361
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 362
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 363
    switch (commandId) { // library marker kkossev.commonLib, line 364
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 365
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 366
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 367
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 368
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 369
        default: // library marker kkossev.commonLib, line 370
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 371
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 372
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 373
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 374
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 375
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 376
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 377
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 378
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 379
            } // library marker kkossev.commonLib, line 380
            break // library marker kkossev.commonLib, line 381
    } // library marker kkossev.commonLib, line 382
} // library marker kkossev.commonLib, line 383

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 385
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

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 399
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 400
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 401
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 402
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 403
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 404
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 405
    } // library marker kkossev.commonLib, line 406
    else { // library marker kkossev.commonLib, line 407
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 408
    } // library marker kkossev.commonLib, line 409
} // library marker kkossev.commonLib, line 410

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 412
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 413
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 414
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 415
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 416
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 417
        state.reportingEnabled = true // library marker kkossev.commonLib, line 418
    } // library marker kkossev.commonLib, line 419
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 420
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 421
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 422
    } else { // library marker kkossev.commonLib, line 423
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 424
    } // library marker kkossev.commonLib, line 425
} // library marker kkossev.commonLib, line 426

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 428
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 429
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 430
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 431
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 432
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 433
    if (status == 0) { // library marker kkossev.commonLib, line 434
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 435
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 436
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 437
        int delta = 0 // library marker kkossev.commonLib, line 438
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 439
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 440
        } // library marker kkossev.commonLib, line 441
        else { // library marker kkossev.commonLib, line 442
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 443
        } // library marker kkossev.commonLib, line 444
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 445
    } // library marker kkossev.commonLib, line 446
    else { // library marker kkossev.commonLib, line 447
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 448
    } // library marker kkossev.commonLib, line 449
} // library marker kkossev.commonLib, line 450

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 452
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 453
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 454
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 455
        return false // library marker kkossev.commonLib, line 456
    } // library marker kkossev.commonLib, line 457
    // execute the customHandler function // library marker kkossev.commonLib, line 458
    boolean result = false // library marker kkossev.commonLib, line 459
    try { // library marker kkossev.commonLib, line 460
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 461
    } // library marker kkossev.commonLib, line 462
    catch (e) { // library marker kkossev.commonLib, line 463
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 464
        return false // library marker kkossev.commonLib, line 465
    } // library marker kkossev.commonLib, line 466
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 467
    return result // library marker kkossev.commonLib, line 468
} // library marker kkossev.commonLib, line 469

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 471
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 472
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 473
    final String commandId = data[0] // library marker kkossev.commonLib, line 474
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 475
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 476
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 477
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 478
    } else { // library marker kkossev.commonLib, line 479
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 480
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 481
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 482
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 483
        } // library marker kkossev.commonLib, line 484
    } // library marker kkossev.commonLib, line 485
} // library marker kkossev.commonLib, line 486

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 488
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 489
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 490
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 491

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 493
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 494
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 495
] // library marker kkossev.commonLib, line 496

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 498
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 499
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 500
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 501
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 502
] // library marker kkossev.commonLib, line 503

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 505
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 506
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 507
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 508
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 509
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 510
    return avg // library marker kkossev.commonLib, line 511
} // library marker kkossev.commonLib, line 512

/* // library marker kkossev.commonLib, line 514
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 515
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 516
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 517
*/ // library marker kkossev.commonLib, line 518
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 519

// Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 521
void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 522
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 523
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 524
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 525
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 526
        case 0x0000: // library marker kkossev.commonLib, line 527
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 528
            break // library marker kkossev.commonLib, line 529
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 530
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 531
            if (isPing) { // library marker kkossev.commonLib, line 532
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 533
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 534
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 535
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 536
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 537
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 538
                    sendRttEvent() // library marker kkossev.commonLib, line 539
                } // library marker kkossev.commonLib, line 540
                else { // library marker kkossev.commonLib, line 541
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 542
                } // library marker kkossev.commonLib, line 543
                state.states['isPing'] = false // library marker kkossev.commonLib, line 544
            } // library marker kkossev.commonLib, line 545
            else { // library marker kkossev.commonLib, line 546
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 547
            } // library marker kkossev.commonLib, line 548
            break // library marker kkossev.commonLib, line 549
        case 0x0004: // library marker kkossev.commonLib, line 550
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 551
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 552
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 553
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 554
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 555
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 556
            } // library marker kkossev.commonLib, line 557
            break // library marker kkossev.commonLib, line 558
        case 0x0005: // library marker kkossev.commonLib, line 559
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 560
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 561
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 562
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 563
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 564
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 565
            } // library marker kkossev.commonLib, line 566
            break // library marker kkossev.commonLib, line 567
        case 0x0007: // library marker kkossev.commonLib, line 568
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 569
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 570
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 571
            break // library marker kkossev.commonLib, line 572
        case 0xFFDF: // library marker kkossev.commonLib, line 573
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 574
            break // library marker kkossev.commonLib, line 575
        case 0xFFE2: // library marker kkossev.commonLib, line 576
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 577
            break // library marker kkossev.commonLib, line 578
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 579
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 580
            break // library marker kkossev.commonLib, line 581
        case 0xFFFE: // library marker kkossev.commonLib, line 582
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 583
            break // library marker kkossev.commonLib, line 584
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 585
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 586
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 587
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 588
            break // library marker kkossev.commonLib, line 589
        default: // library marker kkossev.commonLib, line 590
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 591
            break // library marker kkossev.commonLib, line 592
    } // library marker kkossev.commonLib, line 593
} // library marker kkossev.commonLib, line 594

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 596
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 597
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 598

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 600
    Map descMap = [:] // library marker kkossev.commonLib, line 601
    try { // library marker kkossev.commonLib, line 602
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 603
    } // library marker kkossev.commonLib, line 604
    catch (e1) { // library marker kkossev.commonLib, line 605
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 606
        // try alternative custom parsing // library marker kkossev.commonLib, line 607
        descMap = [:] // library marker kkossev.commonLib, line 608
        try { // library marker kkossev.commonLib, line 609
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 610
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 611
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 612
            } // library marker kkossev.commonLib, line 613
        } // library marker kkossev.commonLib, line 614
        catch (e2) { // library marker kkossev.commonLib, line 615
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 616
            return [:] // library marker kkossev.commonLib, line 617
        } // library marker kkossev.commonLib, line 618
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 619
    } // library marker kkossev.commonLib, line 620
    return descMap // library marker kkossev.commonLib, line 621
} // library marker kkossev.commonLib, line 622

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 624
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 625
        return false // library marker kkossev.commonLib, line 626
    } // library marker kkossev.commonLib, line 627
    // try to parse ... // library marker kkossev.commonLib, line 628
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 629
    Map descMap = [:] // library marker kkossev.commonLib, line 630
    try { // library marker kkossev.commonLib, line 631
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 632
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 633
    } // library marker kkossev.commonLib, line 634
    catch (e) { // library marker kkossev.commonLib, line 635
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 636
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 637
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 638
        return true // library marker kkossev.commonLib, line 639
    } // library marker kkossev.commonLib, line 640

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 642
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 643
    } // library marker kkossev.commonLib, line 644
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 645
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 646
    } // library marker kkossev.commonLib, line 647
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 648
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 649
    } // library marker kkossev.commonLib, line 650
    else { // library marker kkossev.commonLib, line 651
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 652
        return false // library marker kkossev.commonLib, line 653
    } // library marker kkossev.commonLib, line 654
    return true    // processed // library marker kkossev.commonLib, line 655
} // library marker kkossev.commonLib, line 656

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 658
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 659
  /* // library marker kkossev.commonLib, line 660
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 661
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 662
        return true // library marker kkossev.commonLib, line 663
    } // library marker kkossev.commonLib, line 664
*/ // library marker kkossev.commonLib, line 665
    Map descMap = [:] // library marker kkossev.commonLib, line 666
    try { // library marker kkossev.commonLib, line 667
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 668
    } // library marker kkossev.commonLib, line 669
    catch (e1) { // library marker kkossev.commonLib, line 670
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 671
        // try alternative custom parsing // library marker kkossev.commonLib, line 672
        descMap = [:] // library marker kkossev.commonLib, line 673
        try { // library marker kkossev.commonLib, line 674
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 675
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 676
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 677
            } // library marker kkossev.commonLib, line 678
        } // library marker kkossev.commonLib, line 679
        catch (e2) { // library marker kkossev.commonLib, line 680
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 681
            return true // library marker kkossev.commonLib, line 682
        } // library marker kkossev.commonLib, line 683
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 684
    } // library marker kkossev.commonLib, line 685
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 686
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 687
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 688
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 689
        return false // library marker kkossev.commonLib, line 690
    } // library marker kkossev.commonLib, line 691
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 692
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 693
    // attribute report received // library marker kkossev.commonLib, line 694
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 695
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 696
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 697
    } // library marker kkossev.commonLib, line 698
    attrData.each { // library marker kkossev.commonLib, line 699
        if (it.status == '86') { // library marker kkossev.commonLib, line 700
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 701
        // TODO - skip parsing? // library marker kkossev.commonLib, line 702
        } // library marker kkossev.commonLib, line 703
        switch (it.cluster) { // library marker kkossev.commonLib, line 704
            case '0000' : // library marker kkossev.commonLib, line 705
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 706
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 707
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 708
                } // library marker kkossev.commonLib, line 709
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 710
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 711
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 712
                } // library marker kkossev.commonLib, line 713
                else { // library marker kkossev.commonLib, line 714
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 715
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 716
                } // library marker kkossev.commonLib, line 717
                break // library marker kkossev.commonLib, line 718
            default : // library marker kkossev.commonLib, line 719
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 720
                break // library marker kkossev.commonLib, line 721
        } // switch // library marker kkossev.commonLib, line 722
    } // for each attribute // library marker kkossev.commonLib, line 723
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 724
} // library marker kkossev.commonLib, line 725


String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 728
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 729
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 730
} // library marker kkossev.commonLib, line 731

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 733
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 734
} // library marker kkossev.commonLib, line 735

// Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 737
void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 738
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { logWarn 'parseElectricalMeasureCluster is NOT implemented1' } // library marker kkossev.commonLib, line 739
} // library marker kkossev.commonLib, line 740

// Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 742
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 743
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { logWarn 'parseMeteringCluster is NOT implemented1' } // library marker kkossev.commonLib, line 744
} // library marker kkossev.commonLib, line 745

// Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 747
void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 748
    if (this.respondsTo('customParseWindowCoveringCluster')) { customParseWindowCoveringCluster(descMap) } else { logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 749
} // library marker kkossev.commonLib, line 750

/* // library marker kkossev.commonLib, line 752
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 753
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 754
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 755
*/ // library marker kkossev.commonLib, line 756
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 757
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 758
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 759

// Tuya Commands // library marker kkossev.commonLib, line 761
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 762
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 763
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 764
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 765
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 766

// tuya DP type // library marker kkossev.commonLib, line 768
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 769
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 770
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 771
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 772
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 773
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 774

void syncTuyaDateTime() { // library marker kkossev.commonLib, line 776
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 777
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 778
    long offset = 0 // library marker kkossev.commonLib, line 779
    int offsetHours = 0 // library marker kkossev.commonLib, line 780
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 781
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 782
    try { // library marker kkossev.commonLib, line 783
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 784
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 785
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 786
    } catch (e) { // library marker kkossev.commonLib, line 787
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 788
    } // library marker kkossev.commonLib, line 789
    // // library marker kkossev.commonLib, line 790
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 791
    String dateTimeNow = unix2formattedDate(now()) // library marker kkossev.commonLib, line 792
    logDebug "sending time data : ${dateTimeNow} (${cmds})" // library marker kkossev.commonLib, line 793
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 794
    logInfo "Tuya device time synchronized to ${dateTimeNow}" // library marker kkossev.commonLib, line 795
} // library marker kkossev.commonLib, line 796

void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 798
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 799
        syncTuyaDateTime() // library marker kkossev.commonLib, line 800
    } // library marker kkossev.commonLib, line 801
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 802
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 803
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 804
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 805
        if (status != '00') { // library marker kkossev.commonLib, line 806
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 807
        } // library marker kkossev.commonLib, line 808
    } // library marker kkossev.commonLib, line 809
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 810
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 811
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 812
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 813
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 814
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 815
            return // library marker kkossev.commonLib, line 816
        } // library marker kkossev.commonLib, line 817
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 818
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 819
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 820
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 821
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 822
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 823
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 824
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 825
            } // library marker kkossev.commonLib, line 826
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 827
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 828
        } // library marker kkossev.commonLib, line 829
    } // library marker kkossev.commonLib, line 830
    else { // library marker kkossev.commonLib, line 831
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 832
    } // library marker kkossev.commonLib, line 833
} // library marker kkossev.commonLib, line 834

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 836
    logTrace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 837
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 838
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 839
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 840
            return // library marker kkossev.commonLib, line 841
        } // library marker kkossev.commonLib, line 842
    } // library marker kkossev.commonLib, line 843
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) {  // check if the method  method exists // library marker kkossev.commonLib, line 844
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 845
            return // library marker kkossev.commonLib, line 846
        } // library marker kkossev.commonLib, line 847
    } // library marker kkossev.commonLib, line 848
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 849
} // library marker kkossev.commonLib, line 850

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 852
    int retValue = 0 // library marker kkossev.commonLib, line 853
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 854
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 855
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 856
        int power = 1 // library marker kkossev.commonLib, line 857
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 858
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 859
            power = power * 256 // library marker kkossev.commonLib, line 860
        } // library marker kkossev.commonLib, line 861
    } // library marker kkossev.commonLib, line 862
    return retValue // library marker kkossev.commonLib, line 863
} // library marker kkossev.commonLib, line 864

private List<String> getTuyaCommand(String dp, String dp_type, String fncmd) { return sendTuyaCommand(dp, dp_type, fncmd) } // library marker kkossev.commonLib, line 866

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 868
    List<String> cmds = [] // library marker kkossev.commonLib, line 869
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 870
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 871
    int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 872
    //tuyaCmd = 0x04  // !!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.commonLib, line 873
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 874
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 875
    return cmds // library marker kkossev.commonLib, line 876
} // library marker kkossev.commonLib, line 877

private getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 879

void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 881
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 882
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 883
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 884
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 885
} // library marker kkossev.commonLib, line 886

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 888
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 889

List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 891
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 892
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 893
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 894
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 895
} // library marker kkossev.commonLib, line 896

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 898
    List<String> cmds = [] // library marker kkossev.commonLib, line 899
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 900
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 901
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 902
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 903
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 904
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 905
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 906
        } // library marker kkossev.commonLib, line 907
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 908
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 909
    } // library marker kkossev.commonLib, line 910
    else { // library marker kkossev.commonLib, line 911
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 912
    } // library marker kkossev.commonLib, line 913
} // library marker kkossev.commonLib, line 914

// Invoked from configure() // library marker kkossev.commonLib, line 916
List<String> initializeDevice() { // library marker kkossev.commonLib, line 917
    List<String> cmds = [] // library marker kkossev.commonLib, line 918
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 919
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 920
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 921
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 922
    } // library marker kkossev.commonLib, line 923
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 924
    return cmds // library marker kkossev.commonLib, line 925
} // library marker kkossev.commonLib, line 926

// Invoked from configure() // library marker kkossev.commonLib, line 928
List<String> configureDevice() { // library marker kkossev.commonLib, line 929
    List<String> cmds = [] // library marker kkossev.commonLib, line 930
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 931
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 932
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 933
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 934
    } // library marker kkossev.commonLib, line 935
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 936
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 937
    return cmds // library marker kkossev.commonLib, line 938
} // library marker kkossev.commonLib, line 939

/* // library marker kkossev.commonLib, line 941
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 942
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 943
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 944
*/ // library marker kkossev.commonLib, line 945

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 947
    List<String> cmds = [] // library marker kkossev.commonLib, line 948
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 949
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 950
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 951
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 952
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 953
            } // library marker kkossev.commonLib, line 954
        } // library marker kkossev.commonLib, line 955
    } // library marker kkossev.commonLib, line 956
    return cmds // library marker kkossev.commonLib, line 957
} // library marker kkossev.commonLib, line 958

void refresh() { // library marker kkossev.commonLib, line 960
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 961
    checkDriverVersion(state) // library marker kkossev.commonLib, line 962
    List<String> cmds = [] // library marker kkossev.commonLib, line 963
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 964
    List<String> customCmds = customHandlers(['batteryRefresh', 'groupsRefresh', 'onOffRefresh', 'customRefresh']) // library marker kkossev.commonLib, line 965
    if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customHandlers refresh() defined' } // library marker kkossev.commonLib, line 966
    if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 967
        cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 968
        cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 969
    } // library marker kkossev.commonLib, line 970
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 971
        cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 972
        cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 973
    } // library marker kkossev.commonLib, line 974
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 975
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 976
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 977
    } // library marker kkossev.commonLib, line 978
    else { // library marker kkossev.commonLib, line 979
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 980
    } // library marker kkossev.commonLib, line 981
} // library marker kkossev.commonLib, line 982

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 984
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 985
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 986

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 988
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 989
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 990
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 991
    } // library marker kkossev.commonLib, line 992
    else { // library marker kkossev.commonLib, line 993
        logInfo "${info}" // library marker kkossev.commonLib, line 994
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 995
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 996
    } // library marker kkossev.commonLib, line 997
} // library marker kkossev.commonLib, line 998

public void ping() { // library marker kkossev.commonLib, line 1000
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1001
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 1002
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1003
    if (isVirtual()) { runInMillis(10, virtualPong) } // library marker kkossev.commonLib, line 1004
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) } // library marker kkossev.commonLib, line 1005
    logDebug 'ping...' // library marker kkossev.commonLib, line 1006
} // library marker kkossev.commonLib, line 1007

def virtualPong() { // library marker kkossev.commonLib, line 1009
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1010
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1011
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1012
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1013
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1014
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1015
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1016
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1017
        sendRttEvent() // library marker kkossev.commonLib, line 1018
    } // library marker kkossev.commonLib, line 1019
    else { // library marker kkossev.commonLib, line 1020
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1021
    } // library marker kkossev.commonLib, line 1022
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1023
    //unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1024
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1025
} // library marker kkossev.commonLib, line 1026

void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1028
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1029
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1030
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1031
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1032
    if (value == null) { // library marker kkossev.commonLib, line 1033
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1034
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 1035
    } // library marker kkossev.commonLib, line 1036
    else { // library marker kkossev.commonLib, line 1037
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1038
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1039
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 1040
    } // library marker kkossev.commonLib, line 1041
} // library marker kkossev.commonLib, line 1042

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1044
    if (cluster != null) { // library marker kkossev.commonLib, line 1045
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1046
    } // library marker kkossev.commonLib, line 1047
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1048
    return 'NULL' // library marker kkossev.commonLib, line 1049
} // library marker kkossev.commonLib, line 1050

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1052
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1053
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1054
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1055
} // library marker kkossev.commonLib, line 1056

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1058
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1059
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1060
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1061
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1062
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1063
    } // library marker kkossev.commonLib, line 1064
} // library marker kkossev.commonLib, line 1065

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1067
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1068
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1069
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1070
} // library marker kkossev.commonLib, line 1071

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1073
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1074
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1075
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1076
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1077
    } // library marker kkossev.commonLib, line 1078
    else { // library marker kkossev.commonLib, line 1079
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1080
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1081
    } // library marker kkossev.commonLib, line 1082
} // library marker kkossev.commonLib, line 1083

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1085
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1086
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1087
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1088
} // library marker kkossev.commonLib, line 1089

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1091
void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1092
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1093
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1094
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1095
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1096
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1097
    } // library marker kkossev.commonLib, line 1098
} // library marker kkossev.commonLib, line 1099

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1101
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1102
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1103
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1104
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1105
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1106
            logWarn 'not present!' // library marker kkossev.commonLib, line 1107
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1108
        } // library marker kkossev.commonLib, line 1109
    } // library marker kkossev.commonLib, line 1110
    else { // library marker kkossev.commonLib, line 1111
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1112
    } // library marker kkossev.commonLib, line 1113
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1114
} // library marker kkossev.commonLib, line 1115

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1117
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1118
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 1119
    if (value == 'online') { // library marker kkossev.commonLib, line 1120
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1121
    } // library marker kkossev.commonLib, line 1122
    else { // library marker kkossev.commonLib, line 1123
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1124
    } // library marker kkossev.commonLib, line 1125
} // library marker kkossev.commonLib, line 1126

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1128
void updated() { // library marker kkossev.commonLib, line 1129
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1130
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1131
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1132
    unschedule() // library marker kkossev.commonLib, line 1133

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1135
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1136
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1137
    } // library marker kkossev.commonLib, line 1138
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1139
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1140
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1141
    } // library marker kkossev.commonLib, line 1142

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1144
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1145
        // schedule the periodic timer // library marker kkossev.commonLib, line 1146
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1147
        if (interval > 0) { // library marker kkossev.commonLib, line 1148
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1149
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1150
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1151
        } // library marker kkossev.commonLib, line 1152
    } // library marker kkossev.commonLib, line 1153
    else { // library marker kkossev.commonLib, line 1154
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1155
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1156
    } // library marker kkossev.commonLib, line 1157
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1158
        customUpdated() // library marker kkossev.commonLib, line 1159
    } // library marker kkossev.commonLib, line 1160

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1162
} // library marker kkossev.commonLib, line 1163

void logsOff() { // library marker kkossev.commonLib, line 1165
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1166
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1167
} // library marker kkossev.commonLib, line 1168
void traceOff() { // library marker kkossev.commonLib, line 1169
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1170
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1171
} // library marker kkossev.commonLib, line 1172

void configure(String command) { // library marker kkossev.commonLib, line 1174
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1175
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1176
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1177
        return // library marker kkossev.commonLib, line 1178
    } // library marker kkossev.commonLib, line 1179
    // // library marker kkossev.commonLib, line 1180
    String func // library marker kkossev.commonLib, line 1181
    try { // library marker kkossev.commonLib, line 1182
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1183
        "$func"() // library marker kkossev.commonLib, line 1184
    } // library marker kkossev.commonLib, line 1185
    catch (e) { // library marker kkossev.commonLib, line 1186
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1187
        return // library marker kkossev.commonLib, line 1188
    } // library marker kkossev.commonLib, line 1189
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1190
} // library marker kkossev.commonLib, line 1191

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1193
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1194
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1195
} // library marker kkossev.commonLib, line 1196

void loadAllDefaults() { // library marker kkossev.commonLib, line 1198
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1199
    deleteAllSettings() // library marker kkossev.commonLib, line 1200
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1201
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1202
    deleteAllStates() // library marker kkossev.commonLib, line 1203
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1204
    initialize() // library marker kkossev.commonLib, line 1205
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1206
    updated() // library marker kkossev.commonLib, line 1207
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1208
} // library marker kkossev.commonLib, line 1209

void configureNow() { // library marker kkossev.commonLib, line 1211
    configure() // library marker kkossev.commonLib, line 1212
} // library marker kkossev.commonLib, line 1213

/** // library marker kkossev.commonLib, line 1215
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1216
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1217
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1218
 */ // library marker kkossev.commonLib, line 1219
void configure() { // library marker kkossev.commonLib, line 1220
    List<String> cmds = [] // library marker kkossev.commonLib, line 1221
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1222
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1223
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1224
    if (isTuya()) { // library marker kkossev.commonLib, line 1225
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1226
    } // library marker kkossev.commonLib, line 1227
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1228
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1229
    } // library marker kkossev.commonLib, line 1230
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1231
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1232
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1233
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1234
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1235
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1236
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1237
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1238
    } // library marker kkossev.commonLib, line 1239
    else { // library marker kkossev.commonLib, line 1240
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1241
    } // library marker kkossev.commonLib, line 1242
} // library marker kkossev.commonLib, line 1243

 // Invoked when the device is installed or when driver is installed ? // library marker kkossev.commonLib, line 1245
void installed() { // library marker kkossev.commonLib, line 1246
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1247
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1248
    // populate some default values for attributes // library marker kkossev.commonLib, line 1249
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 1250
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 1251
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1252
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1253
} // library marker kkossev.commonLib, line 1254

 // Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1256
void initialize() { // library marker kkossev.commonLib, line 1257
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1258
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1259
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1260
    updateTuyaVersion() // library marker kkossev.commonLib, line 1261
    updateAqaraVersion() // library marker kkossev.commonLib, line 1262
} // library marker kkossev.commonLib, line 1263

/* // library marker kkossev.commonLib, line 1265
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1266
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1267
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1268
*/ // library marker kkossev.commonLib, line 1269

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1271
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1272
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1273
} // library marker kkossev.commonLib, line 1274

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1276
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1277
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1278
} // library marker kkossev.commonLib, line 1279

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1281
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1282
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1283
} // library marker kkossev.commonLib, line 1284

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1286
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1287
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1288
        return // library marker kkossev.commonLib, line 1289
    } // library marker kkossev.commonLib, line 1290
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1291
    cmd.each { // library marker kkossev.commonLib, line 1292
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1293
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1294
            return // library marker kkossev.commonLib, line 1295
        } // library marker kkossev.commonLib, line 1296
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1297
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1298
    } // library marker kkossev.commonLib, line 1299
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1300
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1301
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1302
} // library marker kkossev.commonLib, line 1303

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1305

String getDeviceInfo() { // library marker kkossev.commonLib, line 1307
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1308
} // library marker kkossev.commonLib, line 1309

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1311
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1312
} // library marker kkossev.commonLib, line 1313

@CompileStatic // library marker kkossev.commonLib, line 1315
void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1316
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1317
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1318
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1319
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1320
        initializeVars(false) // library marker kkossev.commonLib, line 1321
        updateTuyaVersion() // library marker kkossev.commonLib, line 1322
        updateAqaraVersion() // library marker kkossev.commonLib, line 1323
    } // library marker kkossev.commonLib, line 1324
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1325
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1326
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1327
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1328
} // library marker kkossev.commonLib, line 1329

// credits @thebearmay // library marker kkossev.commonLib, line 1331
String getModel() { // library marker kkossev.commonLib, line 1332
    try { // library marker kkossev.commonLib, line 1333
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1334
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1335
    } catch (ignore) { // library marker kkossev.commonLib, line 1336
        try { // library marker kkossev.commonLib, line 1337
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1338
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1339
                return model // library marker kkossev.commonLib, line 1340
            } // library marker kkossev.commonLib, line 1341
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1342
            return '' // library marker kkossev.commonLib, line 1343
        } // library marker kkossev.commonLib, line 1344
    } // library marker kkossev.commonLib, line 1345
} // library marker kkossev.commonLib, line 1346

// credits @thebearmay // library marker kkossev.commonLib, line 1348
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1349
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1350
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1351
    String revision = tokens.last() // library marker kkossev.commonLib, line 1352
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1353
} // library marker kkossev.commonLib, line 1354

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1356
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1357
    unschedule() // library marker kkossev.commonLib, line 1358
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1359
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1360

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1362
} // library marker kkossev.commonLib, line 1363

void resetStatistics() { // library marker kkossev.commonLib, line 1365
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1366
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1367
} // library marker kkossev.commonLib, line 1368

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1370
void resetStats() { // library marker kkossev.commonLib, line 1371
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1372
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1373
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1374
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1375
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1376
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1377
} // library marker kkossev.commonLib, line 1378

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1380
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1381
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1382
        state.clear() // library marker kkossev.commonLib, line 1383
        unschedule() // library marker kkossev.commonLib, line 1384
        resetStats() // library marker kkossev.commonLib, line 1385
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 1386
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1387
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1388
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1389
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1390
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1391
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1392
    } // library marker kkossev.commonLib, line 1393

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1395
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1396
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1397
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1398
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1399

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1401
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1402
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1403
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1404
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1405
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1406
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1407

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1409

    // common libraries initialization // library marker kkossev.commonLib, line 1411
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1412
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1413
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1414
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1415

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1417
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1418
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1419
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1420

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1422
    if ( mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1423
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1424
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1425
    if ( ep  != null) { // library marker kkossev.commonLib, line 1426
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1427
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1428
    } // library marker kkossev.commonLib, line 1429
    else { // library marker kkossev.commonLib, line 1430
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1431
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1432
    } // library marker kkossev.commonLib, line 1433
} // library marker kkossev.commonLib, line 1434

void setDestinationEP() { // library marker kkossev.commonLib, line 1436
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1437
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1438
        state.destinationEP = ep // library marker kkossev.commonLib, line 1439
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1440
    } // library marker kkossev.commonLib, line 1441
    else { // library marker kkossev.commonLib, line 1442
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1443
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 1444
    } // library marker kkossev.commonLib, line 1445
} // library marker kkossev.commonLib, line 1446

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1448
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1449
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1450
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1451

// _DEBUG mode only // library marker kkossev.commonLib, line 1453
void getAllProperties() { // library marker kkossev.commonLib, line 1454
    log.trace 'Properties:' // library marker kkossev.commonLib, line 1455
    device.properties.each { it -> // library marker kkossev.commonLib, line 1456
        log.debug it // library marker kkossev.commonLib, line 1457
    } // library marker kkossev.commonLib, line 1458
    log.trace 'Settings:' // library marker kkossev.commonLib, line 1459
    settings.each { it -> // library marker kkossev.commonLib, line 1460
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1461
    } // library marker kkossev.commonLib, line 1462
    log.trace 'Done' // library marker kkossev.commonLib, line 1463
} // library marker kkossev.commonLib, line 1464

// delete all Preferences // library marker kkossev.commonLib, line 1466
void deleteAllSettings() { // library marker kkossev.commonLib, line 1467
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1468
    settings.each { it -> // library marker kkossev.commonLib, line 1469
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 1470
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 1471
    } // library marker kkossev.commonLib, line 1472
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1473
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1474
} // library marker kkossev.commonLib, line 1475

// delete all attributes // library marker kkossev.commonLib, line 1477
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1478
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1479
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1480
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1481
} // library marker kkossev.commonLib, line 1482

// delete all State Variables // library marker kkossev.commonLib, line 1484
void deleteAllStates() { // library marker kkossev.commonLib, line 1485
    String stateDeleted = '' // library marker kkossev.commonLib, line 1486
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1487
    state.clear() // library marker kkossev.commonLib, line 1488
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1489
} // library marker kkossev.commonLib, line 1490

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1492
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1493
} // library marker kkossev.commonLib, line 1494

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1496
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 1497
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 1498
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 1499
    } // library marker kkossev.commonLib, line 1500
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1501
} // library marker kkossev.commonLib, line 1502

void testParse(String par) { // library marker kkossev.commonLib, line 1504
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1505
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1506
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1507
    parse(par) // library marker kkossev.commonLib, line 1508
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1509
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1510
} // library marker kkossev.commonLib, line 1511

def testJob() { // library marker kkossev.commonLib, line 1513
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1514
} // library marker kkossev.commonLib, line 1515

/** // library marker kkossev.commonLib, line 1517
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1518
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1519
 */ // library marker kkossev.commonLib, line 1520
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1521
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1522
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1523
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1524
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1525
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1526
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1527
    String cron // library marker kkossev.commonLib, line 1528
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1529
    else { // library marker kkossev.commonLib, line 1530
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1531
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1532
    } // library marker kkossev.commonLib, line 1533
    return cron // library marker kkossev.commonLib, line 1534
} // library marker kkossev.commonLib, line 1535

// credits @thebearmay // library marker kkossev.commonLib, line 1537
String formatUptime() { // library marker kkossev.commonLib, line 1538
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1539
} // library marker kkossev.commonLib, line 1540

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1542
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1543
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1544
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1545
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1546
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1547
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1548
} // library marker kkossev.commonLib, line 1549

boolean isTuya() { // library marker kkossev.commonLib, line 1551
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1552
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1553
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1554
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1555
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1556
} // library marker kkossev.commonLib, line 1557

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1559
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1560
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1561
    if (application != null) { // library marker kkossev.commonLib, line 1562
        Integer ver // library marker kkossev.commonLib, line 1563
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1564
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1565
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1566
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1567
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1568
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1569
        } // library marker kkossev.commonLib, line 1570
    } // library marker kkossev.commonLib, line 1571
} // library marker kkossev.commonLib, line 1572

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1574

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1576
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1577
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1578
    if (application != null) { // library marker kkossev.commonLib, line 1579
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1580
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1581
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1582
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1583
        } // library marker kkossev.commonLib, line 1584
    } // library marker kkossev.commonLib, line 1585
} // library marker kkossev.commonLib, line 1586

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1588
    try { // library marker kkossev.commonLib, line 1589
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1590
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1591
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1592
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1593
    } catch (e) { // library marker kkossev.commonLib, line 1594
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1595
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1596
    } // library marker kkossev.commonLib, line 1597
} // library marker kkossev.commonLib, line 1598

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1600
    try { // library marker kkossev.commonLib, line 1601
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1602
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1603
        return date.getTime() // library marker kkossev.commonLib, line 1604
    } catch (e) { // library marker kkossev.commonLib, line 1605
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1606
        return now() // library marker kkossev.commonLib, line 1607
    } // library marker kkossev.commonLib, line 1608
} // library marker kkossev.commonLib, line 1609

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (176) kkossev.onOffLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.onOffLib, line 1
library( // library marker kkossev.onOffLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee OnOff Cluster Library', name: 'onOffLib', namespace: 'kkossev', // library marker kkossev.onOffLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/onOffLib.groovy', documentationLink: '', // library marker kkossev.onOffLib, line 4
    version: '3.2.0' // library marker kkossev.onOffLib, line 5
) // library marker kkossev.onOffLib, line 6
/* // library marker kkossev.onOffLib, line 7
 *  Zigbee OnOff Cluster Library // library marker kkossev.onOffLib, line 8
 * // library marker kkossev.onOffLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.onOffLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.onOffLib, line 11
 * // library marker kkossev.onOffLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.onOffLib, line 13
 * // library marker kkossev.onOffLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.onOffLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.onOffLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.onOffLib, line 17
 * // library marker kkossev.onOffLib, line 18
 * ver. 3.2.0  2024-05-24 kkossev  - commonLib 3.2.0 allignment // library marker kkossev.onOffLib, line 19
 * // library marker kkossev.onOffLib, line 20
 *                                   TODO: // library marker kkossev.onOffLib, line 21
*/ // library marker kkossev.onOffLib, line 22

static String onOffLibVersion()   { '3.2.0' } // library marker kkossev.onOffLib, line 24
static String onOffLibStamp() { '2024/05/24 10:44 AM' } // library marker kkossev.onOffLib, line 25

@Field static final Boolean _THREE_STATE = true // library marker kkossev.onOffLib, line 27

metadata { // library marker kkossev.onOffLib, line 29
    capability 'Actuator' // library marker kkossev.onOffLib, line 30
    capability 'Switch' // library marker kkossev.onOffLib, line 31
    if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 32
        attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.onOffLib, line 33
    } // library marker kkossev.onOffLib, line 34
    // no commands // library marker kkossev.onOffLib, line 35
    preferences { // library marker kkossev.onOffLib, line 36
        if (advancedOptions == true) { // library marker kkossev.onOffLib, line 37
            if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 38
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.onOffLib, line 39
            } // library marker kkossev.onOffLib, line 40
        } // library marker kkossev.onOffLib, line 41
    } // library marker kkossev.onOffLib, line 42
} // library marker kkossev.onOffLib, line 43

@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.onOffLib, line 45
    defaultValue: 0, options: [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.onOffLib, line 46
] // library marker kkossev.onOffLib, line 47

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.onOffLib, line 49
    '0': 'switch off', '1': 'switch on', '2': 'switch last state' // library marker kkossev.onOffLib, line 50
] // library marker kkossev.onOffLib, line 51

@Field static final Map switchTypeOptions = [ // library marker kkossev.onOffLib, line 53
    '0': 'toggle', '1': 'state', '2': 'momentary' // library marker kkossev.onOffLib, line 54
] // library marker kkossev.onOffLib, line 55

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.onOffLib, line 57

/* // library marker kkossev.onOffLib, line 59
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 60
 * on/off cluster            0x0006     TODO - move to a library !!!!!!!!!!!!!!! // library marker kkossev.onOffLib, line 61
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 62
*/ // library marker kkossev.onOffLib, line 63
void standardParseOnOffCluster(final Map descMap) { // library marker kkossev.onOffLib, line 64
    /* // library marker kkossev.onOffLib, line 65
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.onOffLib, line 66
        customParseOnOffCluster(descMap) // library marker kkossev.onOffLib, line 67
    } // library marker kkossev.onOffLib, line 68
    else */ // library marker kkossev.onOffLib, line 69
    if (descMap.attrId == '0000') { // library marker kkossev.onOffLib, line 70
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.onOffLib, line 71
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.onOffLib, line 72
        sendSwitchEvent(rawValue) // library marker kkossev.onOffLib, line 73
    } // library marker kkossev.onOffLib, line 74
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.onOffLib, line 75
        parseOnOffAttributes(descMap) // library marker kkossev.onOffLib, line 76
    } // library marker kkossev.onOffLib, line 77
    else { // library marker kkossev.onOffLib, line 78
        if (descMap.attrId != null) { logWarn "standardParseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.onOffLib, line 79
        else { logDebug "standardParseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.onOffLib, line 80
    } // library marker kkossev.onOffLib, line 81
} // library marker kkossev.onOffLib, line 82

void toggle() { // library marker kkossev.onOffLib, line 84
    String descriptionText = 'central button switch is ' // library marker kkossev.onOffLib, line 85
    String state = '' // library marker kkossev.onOffLib, line 86
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.onOffLib, line 87
        state = 'on' // library marker kkossev.onOffLib, line 88
    } // library marker kkossev.onOffLib, line 89
    else { // library marker kkossev.onOffLib, line 90
        state = 'off' // library marker kkossev.onOffLib, line 91
    } // library marker kkossev.onOffLib, line 92
    descriptionText += state // library marker kkossev.onOffLib, line 93
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.onOffLib, line 94
    logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 95
} // library marker kkossev.onOffLib, line 96

void off() { // library marker kkossev.onOffLib, line 98
    if (this.respondsTo('customOff')) { // library marker kkossev.onOffLib, line 99
        customOff() // library marker kkossev.onOffLib, line 100
        return // library marker kkossev.onOffLib, line 101
    } // library marker kkossev.onOffLib, line 102
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.onOffLib, line 103
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.onOffLib, line 104
        return // library marker kkossev.onOffLib, line 105
    } // library marker kkossev.onOffLib, line 106
    List<String> cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.onOffLib, line 107
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 108
    logDebug "off() currentState=${currentState}" // library marker kkossev.onOffLib, line 109
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 110
        if (currentState == 'off') { // library marker kkossev.onOffLib, line 111
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 112
        } // library marker kkossev.onOffLib, line 113
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.onOffLib, line 114
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 115
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 116
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 117
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 118
    } // library marker kkossev.onOffLib, line 119
    /* // library marker kkossev.onOffLib, line 120
    else { // library marker kkossev.onOffLib, line 121
        if (currentState != 'off') { // library marker kkossev.onOffLib, line 122
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.onOffLib, line 123
        } // library marker kkossev.onOffLib, line 124
        else { // library marker kkossev.onOffLib, line 125
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.onOffLib, line 126
            return // library marker kkossev.onOffLib, line 127
        } // library marker kkossev.onOffLib, line 128
    } // library marker kkossev.onOffLib, line 129
    */ // library marker kkossev.onOffLib, line 130

    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 132
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 133
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 134
} // library marker kkossev.onOffLib, line 135

void on() { // library marker kkossev.onOffLib, line 137
    if (this.respondsTo('customOn')) { // library marker kkossev.onOffLib, line 138
        customOn() // library marker kkossev.onOffLib, line 139
        return // library marker kkossev.onOffLib, line 140
    } // library marker kkossev.onOffLib, line 141
    List<String> cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.onOffLib, line 142
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 143
    logDebug "on() currentState=${currentState}" // library marker kkossev.onOffLib, line 144
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 145
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.onOffLib, line 146
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 147
        } // library marker kkossev.onOffLib, line 148
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.onOffLib, line 149
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 150
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 151
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 152
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 153
    } // library marker kkossev.onOffLib, line 154
    /* // library marker kkossev.onOffLib, line 155
    else { // library marker kkossev.onOffLib, line 156
        if (currentState != 'on') { // library marker kkossev.onOffLib, line 157
            logDebug "Switching ${device.displayName} On" // library marker kkossev.onOffLib, line 158
        } // library marker kkossev.onOffLib, line 159
        else { // library marker kkossev.onOffLib, line 160
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.onOffLib, line 161
            return // library marker kkossev.onOffLib, line 162
        } // library marker kkossev.onOffLib, line 163
    } // library marker kkossev.onOffLib, line 164
    */ // library marker kkossev.onOffLib, line 165
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 166
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 167
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 168
} // library marker kkossev.onOffLib, line 169

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.onOffLib, line 171
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.onOffLib, line 172
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.onOffLib, line 173
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.onOffLib, line 174
    } // library marker kkossev.onOffLib, line 175
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.onOffLib, line 176
    Map map = [:] // library marker kkossev.onOffLib, line 177
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.onOffLib, line 178
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.onOffLib, line 179
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.onOffLib, line 180
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.onOffLib, line 181
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 182
        return // library marker kkossev.onOffLib, line 183
    } // library marker kkossev.onOffLib, line 184
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.onOffLib, line 185
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.onOffLib, line 186
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.onOffLib, line 187
    if (lastSwitch != value) { // library marker kkossev.onOffLib, line 188
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.onOffLib, line 189
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 190
        state.states['lastSwitch'] = value // library marker kkossev.onOffLib, line 191
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 192
    } else { // library marker kkossev.onOffLib, line 193
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 194
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 195
    } // library marker kkossev.onOffLib, line 196
    map.name = 'switch' // library marker kkossev.onOffLib, line 197
    map.value = value // library marker kkossev.onOffLib, line 198
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.onOffLib, line 199
    if (isRefresh) { // library marker kkossev.onOffLib, line 200
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.onOffLib, line 201
        map.isStateChange = true // library marker kkossev.onOffLib, line 202
    } else { // library marker kkossev.onOffLib, line 203
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.onOffLib, line 204
    } // library marker kkossev.onOffLib, line 205
    logInfo "${map.descriptionText}" // library marker kkossev.onOffLib, line 206
    sendEvent(map) // library marker kkossev.onOffLib, line 207
    clearIsDigital() // library marker kkossev.onOffLib, line 208
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.onOffLib, line 209
        customSwitchEventPostProcesing(map) // library marker kkossev.onOffLib, line 210
    } // library marker kkossev.onOffLib, line 211
} // library marker kkossev.onOffLib, line 212

void parseOnOffAttributes(final Map it) { // library marker kkossev.onOffLib, line 214
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 215
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.onOffLib, line 216
    def mode // library marker kkossev.onOffLib, line 217
    String attrName // library marker kkossev.onOffLib, line 218
    if (it.value == null) { // library marker kkossev.onOffLib, line 219
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.onOffLib, line 220
        return // library marker kkossev.onOffLib, line 221
    } // library marker kkossev.onOffLib, line 222
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.onOffLib, line 223
    switch (it.attrId) { // library marker kkossev.onOffLib, line 224
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.onOffLib, line 225
            attrName = 'Global Scene Control' // library marker kkossev.onOffLib, line 226
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.onOffLib, line 227
            break // library marker kkossev.onOffLib, line 228
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.onOffLib, line 229
            attrName = 'On Time' // library marker kkossev.onOffLib, line 230
            mode = value // library marker kkossev.onOffLib, line 231
            break // library marker kkossev.onOffLib, line 232
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.onOffLib, line 233
            attrName = 'Off Wait Time' // library marker kkossev.onOffLib, line 234
            mode = value // library marker kkossev.onOffLib, line 235
            break // library marker kkossev.onOffLib, line 236
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.onOffLib, line 237
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 238
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.onOffLib, line 239
            break // library marker kkossev.onOffLib, line 240
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.onOffLib, line 241
            attrName = 'Child Lock' // library marker kkossev.onOffLib, line 242
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.onOffLib, line 243
            break // library marker kkossev.onOffLib, line 244
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.onOffLib, line 245
            attrName = 'LED mode' // library marker kkossev.onOffLib, line 246
            if (isCircuitBreaker()) { // library marker kkossev.onOffLib, line 247
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.onOffLib, line 248
            } // library marker kkossev.onOffLib, line 249
            else { // library marker kkossev.onOffLib, line 250
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.onOffLib, line 251
            } // library marker kkossev.onOffLib, line 252
            break // library marker kkossev.onOffLib, line 253
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.onOffLib, line 254
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 255
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.onOffLib, line 256
            break // library marker kkossev.onOffLib, line 257
        case '8003' : //  Over current alarm // library marker kkossev.onOffLib, line 258
            attrName = 'Over current alarm' // library marker kkossev.onOffLib, line 259
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.onOffLib, line 260
            break // library marker kkossev.onOffLib, line 261
        default : // library marker kkossev.onOffLib, line 262
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 263
            return // library marker kkossev.onOffLib, line 264
    } // library marker kkossev.onOffLib, line 265
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.onOffLib, line 266
} // library marker kkossev.onOffLib, line 267


List<String> onOffRefresh() { // library marker kkossev.onOffLib, line 270
    logDebug "onOffRefresh()" // library marker kkossev.onOffLib, line 271
    List<String> cmds = [] // library marker kkossev.onOffLib, line 272
    cmds = zigbee.readAttribute(0x0006, 0x0000, [:], delay = 100) // library marker kkossev.onOffLib, line 273
    return cmds // library marker kkossev.onOffLib, line 274
} // library marker kkossev.onOffLib, line 275


void onOfInitializeVars( boolean fullInit = false ) { // library marker kkossev.onOffLib, line 278
    logDebug "onOfInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.onOffLib, line 279
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.onOffLib, line 280
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.onOffLib, line 281
} // library marker kkossev.onOffLib, line 282




// ~~~~~ end include (176) kkossev.onOffLib ~~~~~

// ~~~~~ start include (167) kkossev.buttonLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.buttonLib, line 1
library( // library marker kkossev.buttonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Button Library', name: 'buttonLib', namespace: 'kkossev', // library marker kkossev.buttonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/buttonLib.groovy', documentationLink: '', // library marker kkossev.buttonLib, line 4
    version: '3.2.0' // library marker kkossev.buttonLib, line 5
) // library marker kkossev.buttonLib, line 6
/* // library marker kkossev.buttonLib, line 7
 *  Zigbee Button Library // library marker kkossev.buttonLib, line 8
 * // library marker kkossev.buttonLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.buttonLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.buttonLib, line 11
 * // library marker kkossev.buttonLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.buttonLib, line 13
 * // library marker kkossev.buttonLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.buttonLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.buttonLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.buttonLib, line 17
 * // library marker kkossev.buttonLib, line 18
 * ver. 3.0.0  2024-04-06 kkossev  - added energyLib.groovy // library marker kkossev.buttonLib, line 19
 * ver. 3.2.0  2024-05-24 kkossev  - commonLib 3.2.0 allignment; added capability 'PushableButton' and 'Momentary' // library marker kkossev.buttonLib, line 20
 * // library marker kkossev.buttonLib, line 21
 *                                   TODO: // library marker kkossev.buttonLib, line 22
*/ // library marker kkossev.buttonLib, line 23

static String buttonLibVersion()   { '3.2.0' } // library marker kkossev.buttonLib, line 25
static String buttonLibStamp() { '2024/05/24 11:43 AM' } // library marker kkossev.buttonLib, line 26

metadata { // library marker kkossev.buttonLib, line 28
    capability 'PushableButton' // library marker kkossev.buttonLib, line 29
    capability 'Momentary' // library marker kkossev.buttonLib, line 30
    // the other capabilities must be declared in the custom driver, if applicable for the particular device! // library marker kkossev.buttonLib, line 31
    // the custom driver must allso call sendNumberOfButtonsEvent() and sendSupportedButtonValuesEvent()! // library marker kkossev.buttonLib, line 32
    // capability 'DoubleTapableButton' // library marker kkossev.buttonLib, line 33
    // capability 'HoldableButton' // library marker kkossev.buttonLib, line 34
    // capability 'ReleasableButton' // library marker kkossev.buttonLib, line 35

    // no attributes // library marker kkossev.buttonLib, line 37
    // no commands // library marker kkossev.buttonLib, line 38
    preferences { // library marker kkossev.buttonLib, line 39
        // no prefrences // library marker kkossev.buttonLib, line 40
    } // library marker kkossev.buttonLib, line 41
} // library marker kkossev.buttonLib, line 42

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

/* // library marker kkossev.buttonLib, line 63
void push(BigDecimal buttonNumber) {    //pushableButton capability // library marker kkossev.buttonLib, line 64
    logDebug "push button $buttonNumber" // library marker kkossev.buttonLib, line 65
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.buttonLib, line 66
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true) // library marker kkossev.buttonLib, line 67
} // library marker kkossev.buttonLib, line 68
*/ // library marker kkossev.buttonLib, line 69

void push(Object bn) {    //pushableButton capability // library marker kkossev.buttonLib, line 71
    Integer buttonNumber = bn.toInteger() // library marker kkossev.buttonLib, line 72
    logDebug "push button $buttonNumber" // library marker kkossev.buttonLib, line 73
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.buttonLib, line 74
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true) // library marker kkossev.buttonLib, line 75
} // library marker kkossev.buttonLib, line 76

void doubleTap(Object bn) { // library marker kkossev.buttonLib, line 78
    Integer buttonNumber = safeToInt(bn) // library marker kkossev.buttonLib, line 79
    sendButtonEvent(buttonNumber as int, 'doubleTapped', isDigital = true) // library marker kkossev.buttonLib, line 80
} // library marker kkossev.buttonLib, line 81

void hold(Object bn) { // library marker kkossev.buttonLib, line 83
    Integer buttonNumber = safeToInt(bn) // library marker kkossev.buttonLib, line 84
    sendButtonEvent(buttonNumber as int, 'held', isDigital = true) // library marker kkossev.buttonLib, line 85
} // library marker kkossev.buttonLib, line 86

void release(Object bn) { // library marker kkossev.buttonLib, line 88
    Integer buttonNumber = safeToInt(bn) // library marker kkossev.buttonLib, line 89
    sendButtonEvent(buttonNumber as int, 'released', isDigital = true) // library marker kkossev.buttonLib, line 90
} // library marker kkossev.buttonLib, line 91

// must be called from the custom driver! // library marker kkossev.buttonLib, line 93
void sendNumberOfButtonsEvent(int numberOfButtons) { // library marker kkossev.buttonLib, line 94
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 95
} // library marker kkossev.buttonLib, line 96
// must be called from the custom driver! // library marker kkossev.buttonLib, line 97
void sendSupportedButtonValuesEvent(List<String> supportedValues) { // library marker kkossev.buttonLib, line 98
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 99
} // library marker kkossev.buttonLib, line 100


// ~~~~~ end include (167) kkossev.buttonLib ~~~~~

// ~~~~~ start include (171) kkossev.batteryLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
library( // library marker kkossev.batteryLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Battery Library', name: 'batteryLib', namespace: 'kkossev', // library marker kkossev.batteryLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/batteryLib.groovy', documentationLink: '', // library marker kkossev.batteryLib, line 4
    version: '3.2.0' // library marker kkossev.batteryLib, line 5
) // library marker kkossev.batteryLib, line 6
/* // library marker kkossev.batteryLib, line 7
 *  Zigbee Level Library // library marker kkossev.batteryLib, line 8
 * // library marker kkossev.batteryLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.batteryLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.batteryLib, line 11
 * // library marker kkossev.batteryLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.batteryLib, line 13
 * // library marker kkossev.batteryLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.batteryLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.batteryLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.batteryLib, line 17
 * // library marker kkossev.batteryLib, line 18
 * ver. 3.0.0  2024-04-06 kkossev  - added batteryLib.groovy // library marker kkossev.batteryLib, line 19
 * ver. 3.0.1  2024-04-06 kkossev  - customParsePowerCluster bug fix // library marker kkossev.batteryLib, line 20
 * ver. 3.0.2  2024-04-14 kkossev  - batteryPercentage bug fix (was x2); added bVoltCtr; added battertRefresh // library marker kkossev.batteryLib, line 21
 * ver. 3.2.0  2024-04-14 kkossev  - (dev. branch) commonLib 3.2.0 allignment; added lastBattery // library marker kkossev.batteryLib, line 22
 * // library marker kkossev.batteryLib, line 23
 *                                   TODO: // library marker kkossev.batteryLib, line 24
 *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.batteryLib, line 25
*/ // library marker kkossev.batteryLib, line 26

static String batteryLibVersion()   { '3.2.0' } // library marker kkossev.batteryLib, line 28
static String batteryLibStamp() { '2024/05/21 5:57 PM' } // library marker kkossev.batteryLib, line 29

metadata { // library marker kkossev.batteryLib, line 31
    capability 'Battery' // library marker kkossev.batteryLib, line 32
    attribute  'batteryVoltage', 'number' // library marker kkossev.batteryLib, line 33
    attribute  'lastBattery', 'date'         // last battery event time - added in 3.2.0 05/21/2024 // library marker kkossev.batteryLib, line 34
    // no commands // library marker kkossev.batteryLib, line 35
    preferences { // library marker kkossev.batteryLib, line 36
        if (device && advancedOptions == true) { // library marker kkossev.batteryLib, line 37
            input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.batteryLib, line 38
        } // library marker kkossev.batteryLib, line 39
    } // library marker kkossev.batteryLib, line 40
} // library marker kkossev.batteryLib, line 41

void standardParsePowerCluster(final Map descMap) { // library marker kkossev.batteryLib, line 43
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.batteryLib, line 44
    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.batteryLib, line 45
    if (descMap.attrId == '0020') { // battery voltage // library marker kkossev.batteryLib, line 46
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 47
        state.stats['bVoltCtr'] = (state.stats['bVoltCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 48
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.batteryLib, line 49
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.batteryLib, line 50
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.batteryLib, line 51
        } // library marker kkossev.batteryLib, line 52
    } // library marker kkossev.batteryLib, line 53
    else if (descMap.attrId == '0021') { // battery percentage // library marker kkossev.batteryLib, line 54
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 55
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 56
        if (isTuya()) { // library marker kkossev.batteryLib, line 57
            sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 58
        } // library marker kkossev.batteryLib, line 59
        else { // library marker kkossev.batteryLib, line 60
            sendBatteryPercentageEvent((rawValue / 2) as int) // library marker kkossev.batteryLib, line 61
        } // library marker kkossev.batteryLib, line 62
    } // library marker kkossev.batteryLib, line 63
    else { // library marker kkossev.batteryLib, line 64
        logWarn "customParsePowerCluster: zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.batteryLib, line 65
    } // library marker kkossev.batteryLib, line 66
} // library marker kkossev.batteryLib, line 67

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.batteryLib, line 69
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.batteryLib, line 70
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 71
    Map result = [:] // library marker kkossev.batteryLib, line 72
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.batteryLib, line 73
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.batteryLib, line 74
        BigDecimal minVolts = 2.2 // library marker kkossev.batteryLib, line 75
        BigDecimal maxVolts = 3.2 // library marker kkossev.batteryLib, line 76
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.batteryLib, line 77
        int roundedPct = Math.round(pct * 100) // library marker kkossev.batteryLib, line 78
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.batteryLib, line 79
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.batteryLib, line 80
        if (convertToPercent == true) { // library marker kkossev.batteryLib, line 81
            result.value = Math.min(100, roundedPct) // library marker kkossev.batteryLib, line 82
            result.name = 'battery' // library marker kkossev.batteryLib, line 83
            result.unit  = '%' // library marker kkossev.batteryLib, line 84
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.batteryLib, line 85
        } // library marker kkossev.batteryLib, line 86
        else { // library marker kkossev.batteryLib, line 87
            result.value = volts // library marker kkossev.batteryLib, line 88
            result.name = 'batteryVoltage' // library marker kkossev.batteryLib, line 89
            result.unit  = 'V' // library marker kkossev.batteryLib, line 90
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.batteryLib, line 91
        } // library marker kkossev.batteryLib, line 92
        result.type = 'physical' // library marker kkossev.batteryLib, line 93
        result.isStateChange = true // library marker kkossev.batteryLib, line 94
        logInfo "${result.descriptionText}" // library marker kkossev.batteryLib, line 95
        sendEvent(result) // library marker kkossev.batteryLib, line 96
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 97
    } // library marker kkossev.batteryLib, line 98
    else { // library marker kkossev.batteryLib, line 99
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.batteryLib, line 100
    } // library marker kkossev.batteryLib, line 101
} // library marker kkossev.batteryLib, line 102

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.batteryLib, line 104
    if ((batteryPercent as int) == 255) { // library marker kkossev.batteryLib, line 105
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.batteryLib, line 106
        return // library marker kkossev.batteryLib, line 107
    } // library marker kkossev.batteryLib, line 108
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 109
    Map map = [:] // library marker kkossev.batteryLib, line 110
    map.name = 'battery' // library marker kkossev.batteryLib, line 111
    map.timeStamp = now() // library marker kkossev.batteryLib, line 112
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.batteryLib, line 113
    map.unit  = '%' // library marker kkossev.batteryLib, line 114
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.batteryLib, line 115
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.batteryLib, line 116
    map.isStateChange = true // library marker kkossev.batteryLib, line 117
    // // library marker kkossev.batteryLib, line 118
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.batteryLib, line 119
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.batteryLib, line 120
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.batteryLib, line 121
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.batteryLib, line 122
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.batteryLib, line 123
        // send it now! // library marker kkossev.batteryLib, line 124
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.batteryLib, line 125
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 126
    } // library marker kkossev.batteryLib, line 127
    else { // library marker kkossev.batteryLib, line 128
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.batteryLib, line 129
        map.delayed = delayedTime // library marker kkossev.batteryLib, line 130
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.batteryLib, line 131
        map.lastBattery = lastBattery // library marker kkossev.batteryLib, line 132
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.batteryLib, line 133
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.batteryLib, line 134
    } // library marker kkossev.batteryLib, line 135
} // library marker kkossev.batteryLib, line 136

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.batteryLib, line 138
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 139
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 140
    sendEvent(map) // library marker kkossev.batteryLib, line 141
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 142
} // library marker kkossev.batteryLib, line 143

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.batteryLib, line 145
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.batteryLib, line 146
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 147
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 148
    sendEvent(map) // library marker kkossev.batteryLib, line 149
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 150
} // library marker kkossev.batteryLib, line 151

List<String> batteryRefresh() { // library marker kkossev.batteryLib, line 153
    List<String> cmds = [] // library marker kkossev.batteryLib, line 154
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 100)         // battery voltage // library marker kkossev.batteryLib, line 155
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 100)         // battery percentage // library marker kkossev.batteryLib, line 156
    return cmds // library marker kkossev.batteryLib, line 157
} // library marker kkossev.batteryLib, line 158

// ~~~~~ end include (171) kkossev.batteryLib ~~~~~
