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
 * ver. 3.0.6  2024-04-06 kkossev  - commonLib 3.0.6
 * ver. 3.2.0  2024-04-06 kkossev  - commonLib 3.2.0 allignment
 * ver. 3.3.0  2024-07-01 kkossev  - (dev. branch) removed isFingerprint() dependency from the commonLib ver 3.3.1
 * 
 *
 *                                   TODO:
 */

static String version() { '3.3.0' }
static String timeStamp() { '2024/07/01 11:44 AM' }

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
    // 07/01/2024 removed isFingerbot() dependency by addin the optional parameter 0x04 when configuring the fingerbot device   
    final int mode = settings.fingerbotMode != null ? (settings.fingerbotMode as int) : FingerbotModeOpts.defaultValue
    logDebug "setting fingerbotMode to ${FingerbotModeOpts.options[mode as int]} (${mode})"
    cmds = sendTuyaCommand('65', DP_TYPE_BOOL, zigbee.convertToHexString(mode as int, 2), 0x04)

    final int duration = settings.pushTime != null ? (settings.pushTime as int) : 1
    logDebug "setting pushTime to ${duration} seconds)"
    cmds += sendTuyaCommand('67', DP_TYPE_VALUE, zigbee.convertToHexString(duration as int, 8), 0x04 )

    final int dnPos = settings.dnPosition != null ? (settings.dnPosition as int) : 100
    logDebug "setting dnPosition to ${dnPos} %"
    cmds += sendTuyaCommand('66', DP_TYPE_VALUE, zigbee.convertToHexString(dnPos as int, 8), 0x04 )

    final int upPos = settings.upPosition != null ? (settings.upPosition as int) : 0
    logDebug "setting upPosition to ${upPos} %"
    cmds += sendTuyaCommand('6A', DP_TYPE_VALUE, zigbee.convertToHexString(upPos as int, 8), 0x04 )

    final int dir = settings.direction != null ? (settings.direction as int) : FingerbotDirectionOpts.defaultValue
    logDebug "setting fingerbot direction to ${FingerbotDirectionOpts.options[dir]} (${dir})"
    cmds += sendTuyaCommand('68', DP_TYPE_BOOL, zigbee.convertToHexString(dir as int, 2), 0x04 )

    int button = settings.fingerbotButton != null ? (settings.fingerbotButton as int) : FingerbotButtonOpts.defaultValue
    logDebug "setting fingerbotButton to ${FingerbotButtonOpts.options[button as int]} (${button})"
    cmds += sendTuyaCommand('6B', DP_TYPE_BOOL, zigbee.convertToHexString(button as int, 2), 0x04 )

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
    version: '3.3.1' // library marker kkossev.commonLib, line 5
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
  * ver. 3.2.0  2024-05-23 kkossev  - standardParse____Cluster and customParse___Cluster methods; moved onOff methods to a new library; rename all custom handlers in the libs to statdndardParseXXX // library marker kkossev.commonLib, line 36
  * ver. 3.2.1  2024-06-05 kkossev  - 4 in 1 V3 compatibility; added IAS cluster; setDeviceNameAndProfile() fix; // library marker kkossev.commonLib, line 37
  * ver. 3.2.2  2024-06-12 kkossev  - removed isAqaraTRV_OLD() and isAqaraTVOC_OLD() dependencies from the lib; added timeToHMS(); metering and electricalMeasure clusters swapped bug fix; added cluster 0x0204; // library marker kkossev.commonLib, line 38
  * ver. 3.3.0  2024-06-25 kkossev  - fixed exception for unknown clusters; added cluster 0xE001; added powerSource - if 5 minutes after initialize() the powerSource is still unknown, query the device for the powerSource // library marker kkossev.commonLib, line 39
  * ver. 3.3.1  2024-07-01 kkossev  - (dev.branch) remove isFingerbot() dependancy  // library marker kkossev.commonLib, line 40
  * // library marker kkossev.commonLib, line 41
  *                                   TODO: offlineCtr is not increasing! (ZBMicro) // library marker kkossev.commonLib, line 42
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 43
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 44
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 45
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 46
  *                                   TODO: Versions of the main module + included libraries // library marker kkossev.commonLib, line 47
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 48
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 49
  * // library marker kkossev.commonLib, line 50
*/ // library marker kkossev.commonLib, line 51

String commonLibVersion() { '3.3.1' } // library marker kkossev.commonLib, line 53
String commonLibStamp() { '2024/07/01 11:40 AM' } // library marker kkossev.commonLib, line 54

import groovy.transform.Field // library marker kkossev.commonLib, line 56
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 57
import hubitat.device.Protocol // library marker kkossev.commonLib, line 58
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 59
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 60
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 61
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 62
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 63
import java.math.BigDecimal // library marker kkossev.commonLib, line 64

metadata { // library marker kkossev.commonLib, line 66
        if (_DEBUG) { // library marker kkossev.commonLib, line 67
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 68
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 69
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
        capability 'Power Source'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 81

        // common attributes for all device types // library marker kkossev.commonLib, line 83
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 84
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 85
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 86

        // common commands for all device types // library marker kkossev.commonLib, line 88
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 89

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 91
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 92

    preferences { // library marker kkossev.commonLib, line 94
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 95
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 96
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 97

        if (device) { // library marker kkossev.commonLib, line 99
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device...', defaultValue: false // library marker kkossev.commonLib, line 100
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 101
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 102
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 103
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 104
            } // library marker kkossev.commonLib, line 105
        } // library marker kkossev.commonLib, line 106
    } // library marker kkossev.commonLib, line 107
} // library marker kkossev.commonLib, line 108

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 110
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 111
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 112
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 113
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 114
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 115
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 116
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 117
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 118
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 119
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 120

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 122
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 123
] // library marker kkossev.commonLib, line 124
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 125
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 126
] // library marker kkossev.commonLib, line 127

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 129
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 130
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 131
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 132
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 133
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 134
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 135
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 136
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 137
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 138
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 139
] // library marker kkossev.commonLib, line 140

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 142
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 143
//boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 144
//boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 145
//boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 146
//boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 147

/** // library marker kkossev.commonLib, line 149
 * Parse Zigbee message // library marker kkossev.commonLib, line 150
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 151
 */ // library marker kkossev.commonLib, line 152
void parse(final String description) { // library marker kkossev.commonLib, line 153
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 154
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 155
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 156
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 157

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 159
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 160
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 161
            parseIasMessage(description) // library marker kkossev.commonLib, line 162
        } // library marker kkossev.commonLib, line 163
        else { // library marker kkossev.commonLib, line 164
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 165
        } // library marker kkossev.commonLib, line 166
        return // library marker kkossev.commonLib, line 167
    } // library marker kkossev.commonLib, line 168
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 169
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 170
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 171
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 172
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 173
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 174
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 175
        return // library marker kkossev.commonLib, line 176
    } // library marker kkossev.commonLib, line 177

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 179
        return // library marker kkossev.commonLib, line 180
    } // library marker kkossev.commonLib, line 181
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 182

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 184
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 185

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 187
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 188
        return // library marker kkossev.commonLib, line 189
    } // library marker kkossev.commonLib, line 190
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 191
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 192
        return // library marker kkossev.commonLib, line 193
    } // library marker kkossev.commonLib, line 194
    // // library marker kkossev.commonLib, line 195
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 196
    // // library marker kkossev.commonLib, line 197
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 198
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 199
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 200
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 201
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 202
            } // library marker kkossev.commonLib, line 203
            break // library marker kkossev.commonLib, line 204
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 205
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 206
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 207
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 208
            } // library marker kkossev.commonLib, line 209
            break // library marker kkossev.commonLib, line 210
        default: // library marker kkossev.commonLib, line 211
            if (settings.logEnable) { // library marker kkossev.commonLib, line 212
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 213
            } // library marker kkossev.commonLib, line 214
            break // library marker kkossev.commonLib, line 215
    } // library marker kkossev.commonLib, line 216
} // library marker kkossev.commonLib, line 217

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 219
    0x0000: 'Basic',                0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x000C: 'AnalogInput', // library marker kkossev.commonLib, line 220
    0x0006: 'OnOff',                0x0008: 'LevelControl',     0x0012: 'MultistateInput',  0x0102: 'WindowCovering',   0x0201: 'Thermostat',   0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 221
    0x0400: 'Illuminance',          0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 222
    0x0B04: 'ElectricalMeasure',    0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 223
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 224
] // library marker kkossev.commonLib, line 225

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 227
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 228
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 229
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 230
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 231
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 232
        return false // library marker kkossev.commonLib, line 233
    } // library marker kkossev.commonLib, line 234
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 235
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 236
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 237
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 238
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 239
        return true // library marker kkossev.commonLib, line 240
    } // library marker kkossev.commonLib, line 241
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 242
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 243
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 244
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 245
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 246
        return true // library marker kkossev.commonLib, line 247
    } // library marker kkossev.commonLib, line 248
    if (device?.getDataValue('model') != 'ZigUSB' && descMap.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 249
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 250
    } // library marker kkossev.commonLib, line 251
    return false // library marker kkossev.commonLib, line 252
} // library marker kkossev.commonLib, line 253

static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 255
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 256
} // library marker kkossev.commonLib, line 257

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 259
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 260
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 261
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 262
    } // library marker kkossev.commonLib, line 263
    return false // library marker kkossev.commonLib, line 264
} // library marker kkossev.commonLib, line 265

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 267
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 268
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 269
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 270
    } // library marker kkossev.commonLib, line 271
    return false // library marker kkossev.commonLib, line 272
} // library marker kkossev.commonLib, line 273

boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 275
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 276
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 277
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 278
    } // library marker kkossev.commonLib, line 279
    return false // library marker kkossev.commonLib, line 280
} // library marker kkossev.commonLib, line 281

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 283
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 284
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 285
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 286
] // library marker kkossev.commonLib, line 287

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 289
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 290
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 291
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 292
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 293
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 294
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 295
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 296
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 297
    List<String> cmds = [] // library marker kkossev.commonLib, line 298
    switch (clusterId) { // library marker kkossev.commonLib, line 299
        case 0x0005 : // library marker kkossev.commonLib, line 300
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 301
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 302
            // send the active endpoint response // library marker kkossev.commonLib, line 303
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 304
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 305
            break // library marker kkossev.commonLib, line 306
        case 0x0006 : // library marker kkossev.commonLib, line 307
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 308
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 309
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 310
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 311
            break // library marker kkossev.commonLib, line 312
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 313
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 314
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 315
            break // library marker kkossev.commonLib, line 316
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 317
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 318
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 319
            break // library marker kkossev.commonLib, line 320
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 321
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 322
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 323
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 324
            break // library marker kkossev.commonLib, line 325
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 326
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 327
            break // library marker kkossev.commonLib, line 328
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 329
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 330
            if (settings?.logEnable) { log.debug "${clusterInfo}" } // library marker kkossev.commonLib, line 331
            break // library marker kkossev.commonLib, line 332
        default : // library marker kkossev.commonLib, line 333
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 334
            break // library marker kkossev.commonLib, line 335
    } // library marker kkossev.commonLib, line 336
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 337
} // library marker kkossev.commonLib, line 338

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 340
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 341
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 342
    switch (commandId) { // library marker kkossev.commonLib, line 343
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 344
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 345
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 346
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 347
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 348
        default: // library marker kkossev.commonLib, line 349
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 350
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 351
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 352
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 353
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 354
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 355
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 356
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 357
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 358
            } // library marker kkossev.commonLib, line 359
            break // library marker kkossev.commonLib, line 360
    } // library marker kkossev.commonLib, line 361
} // library marker kkossev.commonLib, line 362

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 364
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 365
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 366
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 367
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 368
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 369
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 370
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 371
    } // library marker kkossev.commonLib, line 372
    else { // library marker kkossev.commonLib, line 373
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 374
    } // library marker kkossev.commonLib, line 375
} // library marker kkossev.commonLib, line 376

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 378
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 379
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 380
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 381
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 382
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 383
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 384
    } // library marker kkossev.commonLib, line 385
    else { // library marker kkossev.commonLib, line 386
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 387
    } // library marker kkossev.commonLib, line 388
} // library marker kkossev.commonLib, line 389

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 391
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 392
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 393
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 394
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 395
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 396
        state.reportingEnabled = true // library marker kkossev.commonLib, line 397
    } // library marker kkossev.commonLib, line 398
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 399
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 400
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 401
    } else { // library marker kkossev.commonLib, line 402
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 403
    } // library marker kkossev.commonLib, line 404
} // library marker kkossev.commonLib, line 405

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 407
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 408
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 409
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 410
    if (status == 0) { // library marker kkossev.commonLib, line 411
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 412
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 413
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 414
        int delta = 0 // library marker kkossev.commonLib, line 415
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 416
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 417
        } // library marker kkossev.commonLib, line 418
        else { // library marker kkossev.commonLib, line 419
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 420
        } // library marker kkossev.commonLib, line 421
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 422
    } // library marker kkossev.commonLib, line 423
    else { // library marker kkossev.commonLib, line 424
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 425
    } // library marker kkossev.commonLib, line 426
} // library marker kkossev.commonLib, line 427

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 429
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 430
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 431
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 432
        return false // library marker kkossev.commonLib, line 433
    } // library marker kkossev.commonLib, line 434
    // execute the customHandler function // library marker kkossev.commonLib, line 435
    boolean result = false // library marker kkossev.commonLib, line 436
    try { // library marker kkossev.commonLib, line 437
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 438
    } // library marker kkossev.commonLib, line 439
    catch (e) { // library marker kkossev.commonLib, line 440
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 441
        return false // library marker kkossev.commonLib, line 442
    } // library marker kkossev.commonLib, line 443
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 444
    return result // library marker kkossev.commonLib, line 445
} // library marker kkossev.commonLib, line 446

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 448
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 449
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 450
    final String commandId = data[0] // library marker kkossev.commonLib, line 451
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 452
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 453
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 454
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 455
    } else { // library marker kkossev.commonLib, line 456
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 457
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 458
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 459
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 460
        } // library marker kkossev.commonLib, line 461
    } // library marker kkossev.commonLib, line 462
} // library marker kkossev.commonLib, line 463

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 465
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 466
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 467
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 468

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 470
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 471
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 472
] // library marker kkossev.commonLib, line 473

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 475
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 476
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 477
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 478
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 479
] // library marker kkossev.commonLib, line 480

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 482
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 483
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 484
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 485
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 486
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 487
    return avg // library marker kkossev.commonLib, line 488
} // library marker kkossev.commonLib, line 489

/* // library marker kkossev.commonLib, line 491
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 492
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 493
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 494
*/ // library marker kkossev.commonLib, line 495
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 496

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 498
void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 499
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 500
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 501
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 502
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 503
        case 0x0000: // library marker kkossev.commonLib, line 504
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 505
            break // library marker kkossev.commonLib, line 506
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 507
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 508
            if (isPing) { // library marker kkossev.commonLib, line 509
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 510
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 511
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 512
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 513
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 514
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 515
                    sendRttEvent() // library marker kkossev.commonLib, line 516
                } // library marker kkossev.commonLib, line 517
                else { // library marker kkossev.commonLib, line 518
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 519
                } // library marker kkossev.commonLib, line 520
                state.states['isPing'] = false // library marker kkossev.commonLib, line 521
            } // library marker kkossev.commonLib, line 522
            else { // library marker kkossev.commonLib, line 523
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 524
            } // library marker kkossev.commonLib, line 525
            break // library marker kkossev.commonLib, line 526
        case 0x0004: // library marker kkossev.commonLib, line 527
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 528
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 529
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 530
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 531
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 532
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 533
            } // library marker kkossev.commonLib, line 534
            break // library marker kkossev.commonLib, line 535
        case 0x0005: // library marker kkossev.commonLib, line 536
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 537
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 538
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 539
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 540
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 541
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 542
            } // library marker kkossev.commonLib, line 543
            break // library marker kkossev.commonLib, line 544
        case 0x0007: // library marker kkossev.commonLib, line 545
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 546
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 547
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 548
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 549
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 550
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 551
            } // library marker kkossev.commonLib, line 552
            break // library marker kkossev.commonLib, line 553
        case 0xFFDF: // library marker kkossev.commonLib, line 554
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 555
            break // library marker kkossev.commonLib, line 556
        case 0xFFE2: // library marker kkossev.commonLib, line 557
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 558
            break // library marker kkossev.commonLib, line 559
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 560
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 561
            break // library marker kkossev.commonLib, line 562
        case 0xFFFE: // library marker kkossev.commonLib, line 563
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 564
            break // library marker kkossev.commonLib, line 565
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 566
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 567
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 568
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 569
            break // library marker kkossev.commonLib, line 570
        default: // library marker kkossev.commonLib, line 571
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 572
            break // library marker kkossev.commonLib, line 573
    } // library marker kkossev.commonLib, line 574
} // library marker kkossev.commonLib, line 575

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 577
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 578
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 579

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 581
    Map descMap = [:] // library marker kkossev.commonLib, line 582
    try { // library marker kkossev.commonLib, line 583
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 584
    } // library marker kkossev.commonLib, line 585
    catch (e1) { // library marker kkossev.commonLib, line 586
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 587
        // try alternative custom parsing // library marker kkossev.commonLib, line 588
        descMap = [:] // library marker kkossev.commonLib, line 589
        try { // library marker kkossev.commonLib, line 590
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 591
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 592
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 593
            } // library marker kkossev.commonLib, line 594
        } // library marker kkossev.commonLib, line 595
        catch (e2) { // library marker kkossev.commonLib, line 596
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 597
            return [:] // library marker kkossev.commonLib, line 598
        } // library marker kkossev.commonLib, line 599
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 600
    } // library marker kkossev.commonLib, line 601
    return descMap // library marker kkossev.commonLib, line 602
} // library marker kkossev.commonLib, line 603

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 605
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 606
        return false // library marker kkossev.commonLib, line 607
    } // library marker kkossev.commonLib, line 608
    // try to parse ... // library marker kkossev.commonLib, line 609
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 610
    Map descMap = [:] // library marker kkossev.commonLib, line 611
    try { // library marker kkossev.commonLib, line 612
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 613
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 614
    } // library marker kkossev.commonLib, line 615
    catch (e) { // library marker kkossev.commonLib, line 616
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 617
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 618
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 619
        return true // library marker kkossev.commonLib, line 620
    } // library marker kkossev.commonLib, line 621

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 623
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 624
    } // library marker kkossev.commonLib, line 625
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 626
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 627
    } // library marker kkossev.commonLib, line 628
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 629
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 630
    } // library marker kkossev.commonLib, line 631
    else { // library marker kkossev.commonLib, line 632
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 633
        return false // library marker kkossev.commonLib, line 634
    } // library marker kkossev.commonLib, line 635
    return true    // processed // library marker kkossev.commonLib, line 636
} // library marker kkossev.commonLib, line 637

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 639
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 640
  /* // library marker kkossev.commonLib, line 641
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 642
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 643
        return true // library marker kkossev.commonLib, line 644
    } // library marker kkossev.commonLib, line 645
*/ // library marker kkossev.commonLib, line 646
    Map descMap = [:] // library marker kkossev.commonLib, line 647
    try { // library marker kkossev.commonLib, line 648
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 649
    } // library marker kkossev.commonLib, line 650
    catch (e1) { // library marker kkossev.commonLib, line 651
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 652
        // try alternative custom parsing // library marker kkossev.commonLib, line 653
        descMap = [:] // library marker kkossev.commonLib, line 654
        try { // library marker kkossev.commonLib, line 655
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 656
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 657
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 658
            } // library marker kkossev.commonLib, line 659
        } // library marker kkossev.commonLib, line 660
        catch (e2) { // library marker kkossev.commonLib, line 661
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 662
            return true // library marker kkossev.commonLib, line 663
        } // library marker kkossev.commonLib, line 664
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 665
    } // library marker kkossev.commonLib, line 666
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 667
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 668
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 669
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 670
        return false // library marker kkossev.commonLib, line 671
    } // library marker kkossev.commonLib, line 672
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 673
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 674
    // attribute report received // library marker kkossev.commonLib, line 675
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 676
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 677
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 678
    } // library marker kkossev.commonLib, line 679
    attrData.each { // library marker kkossev.commonLib, line 680
        if (it.status == '86') { // library marker kkossev.commonLib, line 681
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 682
        // TODO - skip parsing? // library marker kkossev.commonLib, line 683
        } // library marker kkossev.commonLib, line 684
        switch (it.cluster) { // library marker kkossev.commonLib, line 685
            case '0000' : // library marker kkossev.commonLib, line 686
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 687
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 688
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 689
                } // library marker kkossev.commonLib, line 690
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 691
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 692
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 693
                } // library marker kkossev.commonLib, line 694
                else { // library marker kkossev.commonLib, line 695
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 696
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 697
                } // library marker kkossev.commonLib, line 698
                break // library marker kkossev.commonLib, line 699
            default : // library marker kkossev.commonLib, line 700
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 701
                break // library marker kkossev.commonLib, line 702
        } // switch // library marker kkossev.commonLib, line 703
    } // for each attribute // library marker kkossev.commonLib, line 704
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 705
} // library marker kkossev.commonLib, line 706

String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 708
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 709
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 710
} // library marker kkossev.commonLib, line 711

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 713
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 714
} // library marker kkossev.commonLib, line 715

/* // library marker kkossev.commonLib, line 717
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 718
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 719
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 720
*/ // library marker kkossev.commonLib, line 721
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 722
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 723
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 724

// Tuya Commands // library marker kkossev.commonLib, line 726
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 727
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 728
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 729
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 730
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 731

// tuya DP type // library marker kkossev.commonLib, line 733
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 734
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 735
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 736
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 737
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 738
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 739

void syncTuyaDateTime() { // library marker kkossev.commonLib, line 741
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 742
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 743
    long offset = 0 // library marker kkossev.commonLib, line 744
    int offsetHours = 0 // library marker kkossev.commonLib, line 745
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 746
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 747
    try { // library marker kkossev.commonLib, line 748
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 749
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 750
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 751
    } catch (e) { // library marker kkossev.commonLib, line 752
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 753
    } // library marker kkossev.commonLib, line 754
    // // library marker kkossev.commonLib, line 755
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 756
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 757
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 758
} // library marker kkossev.commonLib, line 759

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 761
void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 762
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 763
        syncTuyaDateTime() // library marker kkossev.commonLib, line 764
    } // library marker kkossev.commonLib, line 765
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 766
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 767
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 768
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 769
        if (status != '00') { // library marker kkossev.commonLib, line 770
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 771
        } // library marker kkossev.commonLib, line 772
    } // library marker kkossev.commonLib, line 773
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 774
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 775
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 776
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 777
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 778
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 779
            return // library marker kkossev.commonLib, line 780
        } // library marker kkossev.commonLib, line 781
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 782
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 783
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 784
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 785
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 786
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 787
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 788
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 789
            } // library marker kkossev.commonLib, line 790
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 791
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 792
        } // library marker kkossev.commonLib, line 793
    } // library marker kkossev.commonLib, line 794
    else { // library marker kkossev.commonLib, line 795
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 796
    } // library marker kkossev.commonLib, line 797
} // library marker kkossev.commonLib, line 798

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 800
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 801
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 802
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 803
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 804
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 805
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 806
        } // library marker kkossev.commonLib, line 807
    } // library marker kkossev.commonLib, line 808
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 809
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 810
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 811
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 812
        } // library marker kkossev.commonLib, line 813
    } // library marker kkossev.commonLib, line 814
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 815
} // library marker kkossev.commonLib, line 816

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 818
    int retValue = 0 // library marker kkossev.commonLib, line 819
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 820
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 821
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 822
        int power = 1 // library marker kkossev.commonLib, line 823
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 824
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 825
            power = power * 256 // library marker kkossev.commonLib, line 826
        } // library marker kkossev.commonLib, line 827
    } // library marker kkossev.commonLib, line 828
    return retValue // library marker kkossev.commonLib, line 829
} // library marker kkossev.commonLib, line 830

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 832

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 834
    List<String> cmds = [] // library marker kkossev.commonLib, line 835
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 836
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 837
    //int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 838
    int tuyaCmd // library marker kkossev.commonLib, line 839
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified!\ // library marker kkossev.commonLib, line 840
    if (this.respondsTo('getDEVICE') && DEVICE?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 841
        tuyaCmd = DEVICE?.device?.tuyaCmd // library marker kkossev.commonLib, line 842
    } // library marker kkossev.commonLib, line 843
    else { // library marker kkossev.commonLib, line 844
        tuyaCmd = /*isFingerbot() ? 0x04 : */ tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 845
    } // library marker kkossev.commonLib, line 846
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 847
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 848
    return cmds // library marker kkossev.commonLib, line 849
} // library marker kkossev.commonLib, line 850

private getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 852

void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 854
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 855
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 856
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 857
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 858
} // library marker kkossev.commonLib, line 859

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 861
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 862

List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 864
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 865
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 866
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 867
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 868
} // library marker kkossev.commonLib, line 869

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 871
    List<String> cmds = [] // library marker kkossev.commonLib, line 872
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 873
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 874
    } // library marker kkossev.commonLib, line 875
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 876
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 877
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 878
        return // library marker kkossev.commonLib, line 879
    } // library marker kkossev.commonLib, line 880
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 881
} // library marker kkossev.commonLib, line 882

// Invoked from configure() // library marker kkossev.commonLib, line 884
List<String> initializeDevice() { // library marker kkossev.commonLib, line 885
    List<String> cmds = [] // library marker kkossev.commonLib, line 886
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 887
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 888
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 889
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 890
    } // library marker kkossev.commonLib, line 891
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 892
    return cmds // library marker kkossev.commonLib, line 893
} // library marker kkossev.commonLib, line 894

// Invoked from configure() // library marker kkossev.commonLib, line 896
List<String> configureDevice() { // library marker kkossev.commonLib, line 897
    List<String> cmds = [] // library marker kkossev.commonLib, line 898
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 899
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 900
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 901
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 902
    } // library marker kkossev.commonLib, line 903
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 904
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 905
    return cmds // library marker kkossev.commonLib, line 906
} // library marker kkossev.commonLib, line 907

/* // library marker kkossev.commonLib, line 909
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 910
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 911
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 912
*/ // library marker kkossev.commonLib, line 913

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 915
    List<String> cmds = [] // library marker kkossev.commonLib, line 916
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 917
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 918
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 919
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 920
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 921
            } // library marker kkossev.commonLib, line 922
        } // library marker kkossev.commonLib, line 923
    } // library marker kkossev.commonLib, line 924
    return cmds // library marker kkossev.commonLib, line 925
} // library marker kkossev.commonLib, line 926

void refresh() { // library marker kkossev.commonLib, line 928
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 929
    checkDriverVersion(state) // library marker kkossev.commonLib, line 930
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 931
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 932
        customCmds = customRefresh() // library marker kkossev.commonLib, line 933
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 934
    } // library marker kkossev.commonLib, line 935
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 936
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 937
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 938
    } // library marker kkossev.commonLib, line 939
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 940
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 941
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 942
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 943
    } // library marker kkossev.commonLib, line 944
    else { // library marker kkossev.commonLib, line 945
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 946
    } // library marker kkossev.commonLib, line 947
} // library marker kkossev.commonLib, line 948

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 950
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 951
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 952

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 954
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 955
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 956
        sendEvent(name: 'Status', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 957
    } // library marker kkossev.commonLib, line 958
    else { // library marker kkossev.commonLib, line 959
        logInfo "${info}" // library marker kkossev.commonLib, line 960
        sendEvent(name: 'Status', value: info, type: 'digital') // library marker kkossev.commonLib, line 961
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 962
    } // library marker kkossev.commonLib, line 963
} // library marker kkossev.commonLib, line 964

public void ping() { // library marker kkossev.commonLib, line 966
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 967
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 968
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 969
    if (isVirtual()) { runInMillis(10, virtualPong) } // library marker kkossev.commonLib, line 970
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) } // library marker kkossev.commonLib, line 971
    logDebug 'ping...' // library marker kkossev.commonLib, line 972
} // library marker kkossev.commonLib, line 973

def virtualPong() { // library marker kkossev.commonLib, line 975
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 976
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 977
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 978
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 979
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 980
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 981
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 982
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 983
        sendRttEvent() // library marker kkossev.commonLib, line 984
    } // library marker kkossev.commonLib, line 985
    else { // library marker kkossev.commonLib, line 986
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 987
    } // library marker kkossev.commonLib, line 988
    state.states['isPing'] = false // library marker kkossev.commonLib, line 989
    //unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 990
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 991
} // library marker kkossev.commonLib, line 992

void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 994
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 995
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 996
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 997
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 998
    if (value == null) { // library marker kkossev.commonLib, line 999
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1000
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1001
    } // library marker kkossev.commonLib, line 1002
    else { // library marker kkossev.commonLib, line 1003
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1004
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1005
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1006
    } // library marker kkossev.commonLib, line 1007
} // library marker kkossev.commonLib, line 1008

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1010
    if (cluster != null) { // library marker kkossev.commonLib, line 1011
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1012
    } // library marker kkossev.commonLib, line 1013
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1014
    return 'NULL' // library marker kkossev.commonLib, line 1015
} // library marker kkossev.commonLib, line 1016

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1018
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1019
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1020
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1021
} // library marker kkossev.commonLib, line 1022

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1024
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1025
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1026
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1027
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1028
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1029
    } // library marker kkossev.commonLib, line 1030
} // library marker kkossev.commonLib, line 1031

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1033
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1034
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1035
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1036
} // library marker kkossev.commonLib, line 1037

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1039
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1040
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1041
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1042
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1043
    } // library marker kkossev.commonLib, line 1044
    else { // library marker kkossev.commonLib, line 1045
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1046
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1047
    } // library marker kkossev.commonLib, line 1048
} // library marker kkossev.commonLib, line 1049

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1051
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1052
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1053
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1054
} // library marker kkossev.commonLib, line 1055

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1057
void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1058
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1059
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1060
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1061
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1062
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1063
    } // library marker kkossev.commonLib, line 1064
} // library marker kkossev.commonLib, line 1065

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1067
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1068
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1069
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1070
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1071
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1072
            logWarn 'not present!' // library marker kkossev.commonLib, line 1073
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1074
        } // library marker kkossev.commonLib, line 1075
    } // library marker kkossev.commonLib, line 1076
    else { // library marker kkossev.commonLib, line 1077
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1078
    } // library marker kkossev.commonLib, line 1079
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1080
} // library marker kkossev.commonLib, line 1081

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1083
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1084
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1085
    if (value == 'online') { // library marker kkossev.commonLib, line 1086
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1087
    } // library marker kkossev.commonLib, line 1088
    else { // library marker kkossev.commonLib, line 1089
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1090
    } // library marker kkossev.commonLib, line 1091
} // library marker kkossev.commonLib, line 1092

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1094
void updated() { // library marker kkossev.commonLib, line 1095
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1096
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1097
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1098
    unschedule() // library marker kkossev.commonLib, line 1099

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1101
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1102
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1103
    } // library marker kkossev.commonLib, line 1104
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1105
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1106
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1107
    } // library marker kkossev.commonLib, line 1108

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1110
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1111
        // schedule the periodic timer // library marker kkossev.commonLib, line 1112
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1113
        if (interval > 0) { // library marker kkossev.commonLib, line 1114
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1115
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1116
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1117
        } // library marker kkossev.commonLib, line 1118
    } // library marker kkossev.commonLib, line 1119
    else { // library marker kkossev.commonLib, line 1120
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1121
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1122
    } // library marker kkossev.commonLib, line 1123
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1124
        customUpdated() // library marker kkossev.commonLib, line 1125
    } // library marker kkossev.commonLib, line 1126

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1128
} // library marker kkossev.commonLib, line 1129

void logsOff() { // library marker kkossev.commonLib, line 1131
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1132
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1133
} // library marker kkossev.commonLib, line 1134
void traceOff() { // library marker kkossev.commonLib, line 1135
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1136
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1137
} // library marker kkossev.commonLib, line 1138

void configure(String command) { // library marker kkossev.commonLib, line 1140
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1141
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1142
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1143
        return // library marker kkossev.commonLib, line 1144
    } // library marker kkossev.commonLib, line 1145
    // // library marker kkossev.commonLib, line 1146
    String func // library marker kkossev.commonLib, line 1147
    try { // library marker kkossev.commonLib, line 1148
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1149
        "$func"() // library marker kkossev.commonLib, line 1150
    } // library marker kkossev.commonLib, line 1151
    catch (e) { // library marker kkossev.commonLib, line 1152
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1153
        return // library marker kkossev.commonLib, line 1154
    } // library marker kkossev.commonLib, line 1155
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1156
} // library marker kkossev.commonLib, line 1157

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1159
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1160
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1161
} // library marker kkossev.commonLib, line 1162

void loadAllDefaults() { // library marker kkossev.commonLib, line 1164
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1165
    deleteAllSettings() // library marker kkossev.commonLib, line 1166
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1167
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1168
    deleteAllStates() // library marker kkossev.commonLib, line 1169
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1170

    initialize() // library marker kkossev.commonLib, line 1172
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1173
    updated() // library marker kkossev.commonLib, line 1174
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1175
} // library marker kkossev.commonLib, line 1176

void configureNow() { // library marker kkossev.commonLib, line 1178
    configure() // library marker kkossev.commonLib, line 1179
} // library marker kkossev.commonLib, line 1180

/** // library marker kkossev.commonLib, line 1182
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1183
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1184
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1185
 */ // library marker kkossev.commonLib, line 1186
void configure() { // library marker kkossev.commonLib, line 1187
    List<String> cmds = [] // library marker kkossev.commonLib, line 1188
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1189
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1190
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1191
    if (isTuya()) { // library marker kkossev.commonLib, line 1192
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1193
    } // library marker kkossev.commonLib, line 1194
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1195
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1196
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1197
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1198
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1199
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1200
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1201
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1202
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1203
    } // library marker kkossev.commonLib, line 1204
    else { // library marker kkossev.commonLib, line 1205
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1206
    } // library marker kkossev.commonLib, line 1207
} // library marker kkossev.commonLib, line 1208

 // Invoked when the device is installed or when driver is installed ? // library marker kkossev.commonLib, line 1210
void installed() { // library marker kkossev.commonLib, line 1211
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1212
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1213
    // populate some default values for attributes // library marker kkossev.commonLib, line 1214
    sendEvent(name: 'healthStatus', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1215
    sendEvent(name: 'powerSource',  value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1216
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1217
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1218
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1219
} // library marker kkossev.commonLib, line 1220

void queryPowerSource() { // library marker kkossev.commonLib, line 1222
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1223
} // library marker kkossev.commonLib, line 1224

 // Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1226
void initialize() { // library marker kkossev.commonLib, line 1227
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1228
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1229
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1230
    updateTuyaVersion() // library marker kkossev.commonLib, line 1231
    updateAqaraVersion() // library marker kkossev.commonLib, line 1232
} // library marker kkossev.commonLib, line 1233

/* // library marker kkossev.commonLib, line 1235
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1236
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1237
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1238
*/ // library marker kkossev.commonLib, line 1239

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1241
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1242
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1243
} // library marker kkossev.commonLib, line 1244

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1246
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1247
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1248
} // library marker kkossev.commonLib, line 1249

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1251
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1252
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1253
} // library marker kkossev.commonLib, line 1254

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1256
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1257
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1258
        return // library marker kkossev.commonLib, line 1259
    } // library marker kkossev.commonLib, line 1260
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1261
    cmd.each { // library marker kkossev.commonLib, line 1262
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1263
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1264
            return // library marker kkossev.commonLib, line 1265
        } // library marker kkossev.commonLib, line 1266
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1267
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1268
    } // library marker kkossev.commonLib, line 1269
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1270
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1271
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1272
} // library marker kkossev.commonLib, line 1273

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1275

String getDeviceInfo() { // library marker kkossev.commonLib, line 1277
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1278
} // library marker kkossev.commonLib, line 1279

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1281
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1282
} // library marker kkossev.commonLib, line 1283

@CompileStatic // library marker kkossev.commonLib, line 1285
void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1286
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1287
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1288
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1289
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1290
        initializeVars(false) // library marker kkossev.commonLib, line 1291
        updateTuyaVersion() // library marker kkossev.commonLib, line 1292
        updateAqaraVersion() // library marker kkossev.commonLib, line 1293
    } // library marker kkossev.commonLib, line 1294
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1295
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1296
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1297
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1298
} // library marker kkossev.commonLib, line 1299

// credits @thebearmay // library marker kkossev.commonLib, line 1301
String getModel() { // library marker kkossev.commonLib, line 1302
    try { // library marker kkossev.commonLib, line 1303
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1304
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1305
    } catch (ignore) { // library marker kkossev.commonLib, line 1306
        try { // library marker kkossev.commonLib, line 1307
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1308
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1309
                return model // library marker kkossev.commonLib, line 1310
            } // library marker kkossev.commonLib, line 1311
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1312
            return '' // library marker kkossev.commonLib, line 1313
        } // library marker kkossev.commonLib, line 1314
    } // library marker kkossev.commonLib, line 1315
} // library marker kkossev.commonLib, line 1316

// credits @thebearmay // library marker kkossev.commonLib, line 1318
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1319
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1320
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1321
    String revision = tokens.last() // library marker kkossev.commonLib, line 1322
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1323
} // library marker kkossev.commonLib, line 1324

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1326
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1327
    unschedule() // library marker kkossev.commonLib, line 1328
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1329
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1330

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1332
} // library marker kkossev.commonLib, line 1333

void resetStatistics() { // library marker kkossev.commonLib, line 1335
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1336
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1337
} // library marker kkossev.commonLib, line 1338

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1340
void resetStats() { // library marker kkossev.commonLib, line 1341
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1342
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1343
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1344
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1345
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1346
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1347
} // library marker kkossev.commonLib, line 1348

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1350
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1351
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1352
        state.clear() // library marker kkossev.commonLib, line 1353
        unschedule() // library marker kkossev.commonLib, line 1354
        resetStats() // library marker kkossev.commonLib, line 1355
        if (deviceProfilesV3 != null && this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1356
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1357
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1358
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1359
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1360
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1361
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1362
    } // library marker kkossev.commonLib, line 1363

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1365
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1366
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1367
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1368
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1369

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1371
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1372
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1373
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1374
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1375
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1376
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1377

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1379

    // common libraries initialization // library marker kkossev.commonLib, line 1381
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1382
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1383
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1384
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1385
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1386

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1388
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1389
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1390
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1391

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1393
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1394
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1395
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1396
    if ( ep  != null) {  // library marker kkossev.commonLib, line 1397
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1398
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1399
    } // library marker kkossev.commonLib, line 1400
    else { // library marker kkossev.commonLib, line 1401
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1402
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1403
    } // library marker kkossev.commonLib, line 1404
} // library marker kkossev.commonLib, line 1405

// not used!? // library marker kkossev.commonLib, line 1407
void setDestinationEP() { // library marker kkossev.commonLib, line 1408
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1409
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1410
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1411
} // library marker kkossev.commonLib, line 1412

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1414
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1415
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1416
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1417

// _DEBUG mode only // library marker kkossev.commonLib, line 1419
void getAllProperties() { // library marker kkossev.commonLib, line 1420
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1421
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1422
} // library marker kkossev.commonLib, line 1423

// delete all Preferences // library marker kkossev.commonLib, line 1425
void deleteAllSettings() { // library marker kkossev.commonLib, line 1426
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1427
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1428
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1429
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1430
} // library marker kkossev.commonLib, line 1431

// delete all attributes // library marker kkossev.commonLib, line 1433
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1434
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1435
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1436
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1437
} // library marker kkossev.commonLib, line 1438

// delete all State Variables // library marker kkossev.commonLib, line 1440
void deleteAllStates() { // library marker kkossev.commonLib, line 1441
    String stateDeleted = '' // library marker kkossev.commonLib, line 1442
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1443
    state.clear() // library marker kkossev.commonLib, line 1444
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1445
} // library marker kkossev.commonLib, line 1446

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1448
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1449
} // library marker kkossev.commonLib, line 1450

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1452
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1453
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1454
} // library marker kkossev.commonLib, line 1455

void testParse(String par) { // library marker kkossev.commonLib, line 1457
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1458
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1459
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1460
    parse(par) // library marker kkossev.commonLib, line 1461
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1462
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1463
} // library marker kkossev.commonLib, line 1464

def testJob() { // library marker kkossev.commonLib, line 1466
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1467
} // library marker kkossev.commonLib, line 1468

/** // library marker kkossev.commonLib, line 1470
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1471
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1472
 */ // library marker kkossev.commonLib, line 1473
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1474
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1475
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1476
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1477
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1478
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1479
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1480
    String cron // library marker kkossev.commonLib, line 1481
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1482
    else { // library marker kkossev.commonLib, line 1483
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1484
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1485
    } // library marker kkossev.commonLib, line 1486
    return cron // library marker kkossev.commonLib, line 1487
} // library marker kkossev.commonLib, line 1488

// credits @thebearmay // library marker kkossev.commonLib, line 1490
String formatUptime() { // library marker kkossev.commonLib, line 1491
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1492
} // library marker kkossev.commonLib, line 1493

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1495
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1496
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1497
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1498
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1499
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1500
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1501
} // library marker kkossev.commonLib, line 1502

boolean isTuya() { // library marker kkossev.commonLib, line 1504
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1505
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1506
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1507
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1508
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1509
} // library marker kkossev.commonLib, line 1510

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1512
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1513
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1514
    if (application != null) { // library marker kkossev.commonLib, line 1515
        Integer ver // library marker kkossev.commonLib, line 1516
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1517
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1518
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1519
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1520
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1521
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1522
        } // library marker kkossev.commonLib, line 1523
    } // library marker kkossev.commonLib, line 1524
} // library marker kkossev.commonLib, line 1525

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1527

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1529
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1530
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1531
    if (application != null) { // library marker kkossev.commonLib, line 1532
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1533
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1534
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1535
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1536
        } // library marker kkossev.commonLib, line 1537
    } // library marker kkossev.commonLib, line 1538
} // library marker kkossev.commonLib, line 1539

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1541
    try { // library marker kkossev.commonLib, line 1542
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1543
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1544
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1545
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1546
    } catch (e) { // library marker kkossev.commonLib, line 1547
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1548
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1549
    } // library marker kkossev.commonLib, line 1550
} // library marker kkossev.commonLib, line 1551

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1553
    try { // library marker kkossev.commonLib, line 1554
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1555
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1556
        return date.getTime() // library marker kkossev.commonLib, line 1557
    } catch (e) { // library marker kkossev.commonLib, line 1558
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1559
        return now() // library marker kkossev.commonLib, line 1560
    } // library marker kkossev.commonLib, line 1561
} // library marker kkossev.commonLib, line 1562

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1564
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1565
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1566
    int seconds = time % 60 // library marker kkossev.commonLib, line 1567
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1568
} // library marker kkossev.commonLib, line 1569

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (176) kkossev.onOffLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.onOffLib, line 1
library( // library marker kkossev.onOffLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee OnOff Cluster Library', name: 'onOffLib', namespace: 'kkossev', // library marker kkossev.onOffLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/onOffLib.groovy', documentationLink: '', // library marker kkossev.onOffLib, line 4
    version: '3.2.2' // library marker kkossev.onOffLib, line 5
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
 * ver. 3.2.0  2024-06-04 kkossev  - commonLib 3.2.1 allignment; if isRefresh then sendEvent with isStateChange = true // library marker kkossev.onOffLib, line 19
 * ver. 3.2.1  2024-06-07 kkossev  - the advanced options are excpluded for DEVICE_TYPE Thermostat // library marker kkossev.onOffLib, line 20
 * ver. 3.2.2  2024-06-29 kkossev  - (dev.branch) added on/off control for Tuya device profiles with 'switch' dp; // library marker kkossev.onOffLib, line 21
 * // library marker kkossev.onOffLib, line 22
 *                                   TODO: // library marker kkossev.onOffLib, line 23
*/ // library marker kkossev.onOffLib, line 24

static String onOffLibVersion()   { '3.2.2' } // library marker kkossev.onOffLib, line 26
static String onOffLibStamp() { '2024/06/29 12:27 PM' } // library marker kkossev.onOffLib, line 27

@Field static final Boolean _THREE_STATE = true // library marker kkossev.onOffLib, line 29

metadata { // library marker kkossev.onOffLib, line 31
    capability 'Actuator' // library marker kkossev.onOffLib, line 32
    capability 'Switch' // library marker kkossev.onOffLib, line 33
    if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 34
        attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.onOffLib, line 35
    } // library marker kkossev.onOffLib, line 36
    // no commands // library marker kkossev.onOffLib, line 37
    preferences { // library marker kkossev.onOffLib, line 38
        if (settings?.advancedOptions == true && device != null && !(DEVICE_TYPE in ['Device', 'Thermostat'])) { // library marker kkossev.onOffLib, line 39
            input(name: 'ignoreDuplicated', type: 'bool', title: '<b>Ignore Duplicated Switch Events</b>', description: 'Some switches and plugs send periodically the switch status as a heart-beet ', defaultValue: true) // library marker kkossev.onOffLib, line 40
            input(name: 'alwaysOn', type: 'bool', title: '<b>Always On</b>', description: 'Disable switching off plugs and switches that must stay always On', defaultValue: false) // library marker kkossev.onOffLib, line 41
            if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 42
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: 'Experimental multi-state switch events', defaultValue: false // library marker kkossev.onOffLib, line 43
            } // library marker kkossev.onOffLib, line 44
        } // library marker kkossev.onOffLib, line 45
    } // library marker kkossev.onOffLib, line 46
} // library marker kkossev.onOffLib, line 47

@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.onOffLib, line 49
    defaultValue: 0, options: [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.onOffLib, line 50
] // library marker kkossev.onOffLib, line 51

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.onOffLib, line 53
    '0': 'switch off', '1': 'switch on', '2': 'switch last state' // library marker kkossev.onOffLib, line 54
] // library marker kkossev.onOffLib, line 55

@Field static final Map switchTypeOptions = [ // library marker kkossev.onOffLib, line 57
    '0': 'toggle', '1': 'state', '2': 'momentary' // library marker kkossev.onOffLib, line 58
] // library marker kkossev.onOffLib, line 59

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.onOffLib, line 61

/* // library marker kkossev.onOffLib, line 63
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 64
 * on/off cluster            0x0006     TODO - move to a library !!!!!!!!!!!!!!! // library marker kkossev.onOffLib, line 65
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 66
*/ // library marker kkossev.onOffLib, line 67
void standardParseOnOffCluster(final Map descMap) { // library marker kkossev.onOffLib, line 68
    /* // library marker kkossev.onOffLib, line 69
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.onOffLib, line 70
        customParseOnOffCluster(descMap) // library marker kkossev.onOffLib, line 71
    } // library marker kkossev.onOffLib, line 72
    else */ // library marker kkossev.onOffLib, line 73
    if (descMap.attrId == '0000') { // library marker kkossev.onOffLib, line 74
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.onOffLib, line 75
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.onOffLib, line 76
        sendSwitchEvent(rawValue) // library marker kkossev.onOffLib, line 77
    } // library marker kkossev.onOffLib, line 78
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.onOffLib, line 79
        parseOnOffAttributes(descMap) // library marker kkossev.onOffLib, line 80
    } // library marker kkossev.onOffLib, line 81
    else { // library marker kkossev.onOffLib, line 82
        if (descMap.attrId != null) { logWarn "standardParseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.onOffLib, line 83
        else { logDebug "standardParseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.onOffLib, line 84
    } // library marker kkossev.onOffLib, line 85
} // library marker kkossev.onOffLib, line 86

void toggle() { // library marker kkossev.onOffLib, line 88
    String descriptionText = 'central button switch is ' // library marker kkossev.onOffLib, line 89
    String state = '' // library marker kkossev.onOffLib, line 90
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.onOffLib, line 91
        state = 'on' // library marker kkossev.onOffLib, line 92
    } // library marker kkossev.onOffLib, line 93
    else { // library marker kkossev.onOffLib, line 94
        state = 'off' // library marker kkossev.onOffLib, line 95
    } // library marker kkossev.onOffLib, line 96
    descriptionText += state // library marker kkossev.onOffLib, line 97
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.onOffLib, line 98
    logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 99
} // library marker kkossev.onOffLib, line 100

void off() { // library marker kkossev.onOffLib, line 102
    if (this.respondsTo('customOff')) { customOff() ; return  } // library marker kkossev.onOffLib, line 103
    if ((settings?.alwaysOn ?: false) == true) { logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" ; return } // library marker kkossev.onOffLib, line 104
    List<String> cmds = [] // library marker kkossev.onOffLib, line 105
    // added 06/29/2024 - control Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 106
    if (this.respondsTo(getDEVICE)) {   // defined in deviceProfileLib // library marker kkossev.onOffLib, line 107
        Map switchMap = getAttributesMap('switch') // library marker kkossev.onOffLib, line 108
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  0  : 1 // library marker kkossev.onOffLib, line 109
        if (switchMap != null && switchMap != [:]) { // library marker kkossev.onOffLib, line 110
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue) // library marker kkossev.onOffLib, line 111
            logTrace "off() Tuya cmds=${cmds}" // library marker kkossev.onOffLib, line 112
        } // library marker kkossev.onOffLib, line 113
    } // library marker kkossev.onOffLib, line 114
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 115
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.onOffLib, line 116
    } // library marker kkossev.onOffLib, line 117

    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 119
    logDebug "off() currentState=${currentState}" // library marker kkossev.onOffLib, line 120
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 121
        if (currentState == 'off') { // library marker kkossev.onOffLib, line 122
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 123
        } // library marker kkossev.onOffLib, line 124
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.onOffLib, line 125
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 126
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 127
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 128
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 129
    } // library marker kkossev.onOffLib, line 130
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 131
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 132
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 133
} // library marker kkossev.onOffLib, line 134

void on() { // library marker kkossev.onOffLib, line 136
    if (this.respondsTo('customOn')) { customOn() ; return } // library marker kkossev.onOffLib, line 137
    List<String> cmds = [] // library marker kkossev.onOffLib, line 138
    // added 06/29/2024 - control Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 139
    if (this.respondsTo(getDEVICE)) {   // defined in deviceProfileLib // library marker kkossev.onOffLib, line 140
        Map switchMap = getAttributesMap('switch') // library marker kkossev.onOffLib, line 141
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  1  : 0 // library marker kkossev.onOffLib, line 142
        if (switchMap != null && switchMap != [:]) { // library marker kkossev.onOffLib, line 143
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue) // library marker kkossev.onOffLib, line 144
            logTrace "on() Tuya cmds=${cmds}" // library marker kkossev.onOffLib, line 145
        } // library marker kkossev.onOffLib, line 146
    } // library marker kkossev.onOffLib, line 147
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 148
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.onOffLib, line 149
    } // library marker kkossev.onOffLib, line 150
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 151
    logDebug "on() currentState=${currentState}" // library marker kkossev.onOffLib, line 152
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 153
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.onOffLib, line 154
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 155
        } // library marker kkossev.onOffLib, line 156
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.onOffLib, line 157
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 158
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 159
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 160
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 161
    } // library marker kkossev.onOffLib, line 162
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 163
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 164
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 165
} // library marker kkossev.onOffLib, line 166

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.onOffLib, line 168
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.onOffLib, line 169
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.onOffLib, line 170
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.onOffLib, line 171
    } // library marker kkossev.onOffLib, line 172
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.onOffLib, line 173
    Map map = [:] // library marker kkossev.onOffLib, line 174
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.onOffLib, line 175
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.onOffLib, line 176
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.onOffLib, line 177
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false)) && !isRefresh) { // library marker kkossev.onOffLib, line 178
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.onOffLib, line 179
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 180
        return // library marker kkossev.onOffLib, line 181
    } // library marker kkossev.onOffLib, line 182
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.onOffLib, line 183
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.onOffLib, line 184
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.onOffLib, line 185
    if (lastSwitch != value) { // library marker kkossev.onOffLib, line 186
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.onOffLib, line 187
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 188
        state.states['lastSwitch'] = value // library marker kkossev.onOffLib, line 189
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 190
    } else { // library marker kkossev.onOffLib, line 191
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 192
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 193
    } // library marker kkossev.onOffLib, line 194
    map.name = 'switch' // library marker kkossev.onOffLib, line 195
    map.value = value // library marker kkossev.onOffLib, line 196
    if (isRefresh) { // library marker kkossev.onOffLib, line 197
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.onOffLib, line 198
        map.isStateChange = true // library marker kkossev.onOffLib, line 199
    } else { // library marker kkossev.onOffLib, line 200
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.onOffLib, line 201
    } // library marker kkossev.onOffLib, line 202
    logInfo "${map.descriptionText}" // library marker kkossev.onOffLib, line 203
    sendEvent(map) // library marker kkossev.onOffLib, line 204
    clearIsDigital() // library marker kkossev.onOffLib, line 205
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.onOffLib, line 206
        customSwitchEventPostProcesing(map) // library marker kkossev.onOffLib, line 207
    } // library marker kkossev.onOffLib, line 208
} // library marker kkossev.onOffLib, line 209

void parseOnOffAttributes(final Map it) { // library marker kkossev.onOffLib, line 211
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 212
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.onOffLib, line 213
    String mode // library marker kkossev.onOffLib, line 214
    String attrName // library marker kkossev.onOffLib, line 215
    if (it.value == null) { // library marker kkossev.onOffLib, line 216
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.onOffLib, line 217
        return // library marker kkossev.onOffLib, line 218
    } // library marker kkossev.onOffLib, line 219
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.onOffLib, line 220
    switch (it.attrId) { // library marker kkossev.onOffLib, line 221
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.onOffLib, line 222
            attrName = 'Global Scene Control' // library marker kkossev.onOffLib, line 223
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.onOffLib, line 224
            break // library marker kkossev.onOffLib, line 225
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.onOffLib, line 226
            attrName = 'On Time' // library marker kkossev.onOffLib, line 227
            mode = value // library marker kkossev.onOffLib, line 228
            break // library marker kkossev.onOffLib, line 229
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.onOffLib, line 230
            attrName = 'Off Wait Time' // library marker kkossev.onOffLib, line 231
            mode = value // library marker kkossev.onOffLib, line 232
            break // library marker kkossev.onOffLib, line 233
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.onOffLib, line 234
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 235
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.onOffLib, line 236
            break // library marker kkossev.onOffLib, line 237
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.onOffLib, line 238
            attrName = 'Child Lock' // library marker kkossev.onOffLib, line 239
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.onOffLib, line 240
            break // library marker kkossev.onOffLib, line 241
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.onOffLib, line 242
            attrName = 'LED mode' // library marker kkossev.onOffLib, line 243
            if (isCircuitBreaker()) { // library marker kkossev.onOffLib, line 244
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.onOffLib, line 245
            } // library marker kkossev.onOffLib, line 246
            else { // library marker kkossev.onOffLib, line 247
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.onOffLib, line 248
            } // library marker kkossev.onOffLib, line 249
            break // library marker kkossev.onOffLib, line 250
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.onOffLib, line 251
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 252
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.onOffLib, line 253
            break // library marker kkossev.onOffLib, line 254
        case '8003' : //  Over current alarm // library marker kkossev.onOffLib, line 255
            attrName = 'Over current alarm' // library marker kkossev.onOffLib, line 256
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.onOffLib, line 257
            break // library marker kkossev.onOffLib, line 258
        default : // library marker kkossev.onOffLib, line 259
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 260
            return // library marker kkossev.onOffLib, line 261
    } // library marker kkossev.onOffLib, line 262
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.onOffLib, line 263
} // library marker kkossev.onOffLib, line 264

List<String> onOffRefresh() { // library marker kkossev.onOffLib, line 266
    logDebug 'onOffRefresh()' // library marker kkossev.onOffLib, line 267
    List<String> cmds = zigbee.readAttribute(0x0006, 0x0000, [:], delay = 100) // library marker kkossev.onOffLib, line 268
    return cmds // library marker kkossev.onOffLib, line 269
} // library marker kkossev.onOffLib, line 270

void onOfInitializeVars( boolean fullInit = false ) { // library marker kkossev.onOffLib, line 272
    logDebug "onOfInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.onOffLib, line 273
    if (fullInit || settings?.ignoreDuplicated == null) { device.updateSetting('ignoreDuplicated', true) } // library marker kkossev.onOffLib, line 274
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.onOffLib, line 275
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.onOffLib, line 276
} // library marker kkossev.onOffLib, line 277

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
static String buttonLibStamp() { '2024/05/24 12:48 PM' } // library marker kkossev.buttonLib, line 26

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
    Integer buttonNumber = bn.toInteger() // library marker kkossev.buttonLib, line 79
    sendButtonEvent(buttonNumber as int, 'doubleTapped', isDigital = true) // library marker kkossev.buttonLib, line 80
} // library marker kkossev.buttonLib, line 81

void hold(Object bn) { // library marker kkossev.buttonLib, line 83
    Integer buttonNumber = bn.toInteger() // library marker kkossev.buttonLib, line 84
    sendButtonEvent(buttonNumber as int, 'held', isDigital = true) // library marker kkossev.buttonLib, line 85
} // library marker kkossev.buttonLib, line 86

void release(Object bn) { // library marker kkossev.buttonLib, line 88
    Integer buttonNumber = bn.toInteger() // library marker kkossev.buttonLib, line 89
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
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoJavaUtilDate, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
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
            input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: 'Convert battery voltage to battery Percentage remaining.' // library marker kkossev.batteryLib, line 38
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
