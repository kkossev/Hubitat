/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee OnOff Cluster Library', name: 'onOffLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/onOffLib.groovy', documentationLink: '',
    version: '3.2.2'
)
/*
 *  Zigbee OnOff Cluster Library
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
 * ver. 3.2.0  2024-06-04 kkossev  - commonLib 3.2.1 allignment; if isRefresh then sendEvent with isStateChange = true
 * ver. 3.2.1  2024-06-07 kkossev  - the advanced options are excpluded for DEVICE_TYPE Thermostat
 * ver. 3.2.2  2024-06-29 kkossev  - added on/off control for Tuya device profiles with 'switch' dp;
 *
 *                                   TODO:
*/

static String onOffLibVersion()   { '3.2.2' }
static String onOffLibStamp() { '2024/06/29 12:27 PM' }

@Field static final Boolean _THREE_STATE = true

metadata {
    capability 'Actuator'
    capability 'Switch'
    if (_THREE_STATE == true) {
        attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String>
    }
    // no commands
    preferences {
        if (settings?.advancedOptions == true && device != null && !(DEVICE_TYPE in ['Device', 'Thermostat'])) {
            input(name: 'ignoreDuplicated', type: 'bool', title: '<b>Ignore Duplicated Switch Events</b>', description: 'Some switches and plugs send periodically the switch status as a heart-beet ', defaultValue: true)
            input(name: 'alwaysOn', type: 'bool', title: '<b>Always On</b>', description: 'Disable switching off plugs and switches that must stay always On', defaultValue: false)
            if (_THREE_STATE == true) {
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: 'Experimental multi-state switch events', defaultValue: false
            }
        }
    }
}

@Field static final Map SwitchThreeStateOpts = [
    defaultValue: 0, options: [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure']
]

@Field static final Map powerOnBehaviourOptions = [
    '0': 'switch off', '1': 'switch on', '2': 'switch last state'
]

@Field static final Map switchTypeOptions = [
    '0': 'toggle', '1': 'state', '2': 'momentary'
]

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] }

/*
 * -----------------------------------------------------------------------------
 * on/off cluster            0x0006     TODO - move to a library !!!!!!!!!!!!!!!
 * -----------------------------------------------------------------------------
*/
void standardParseOnOffCluster(final Map descMap) {
    /*
    if (this.respondsTo('customParseOnOffCluster')) {
        customParseOnOffCluster(descMap)
    }
    else */
    if (descMap.attrId == '0000') {
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value
        int rawValue = hexStrToUnsignedInt(descMap.value)
        sendSwitchEvent(rawValue)
    }
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) {
        parseOnOffAttributes(descMap)
    }
    else {
        if (descMap.attrId != null) { logWarn "standardParseOnOffCluster: unprocessed attrId ${descMap.attrId}"  }
        else { logDebug "standardParseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :(
    }
}

void toggle() {
    String descriptionText = 'central button switch is '
    String state = ''
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') {
        state = 'on'
    }
    else {
        state = 'off'
    }
    descriptionText += state
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true)
    logInfo "${descriptionText}"
}

void off() {
    if (this.respondsTo('customOff')) { customOff() ; return  }
    if ((settings?.alwaysOn ?: false) == true) { logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" ; return }
    List<String> cmds = []
    // added 06/29/2024 - control Tuya 0xEF00 switch
    if (this.respondsTo(getDEVICE)) {   // defined in deviceProfileLib
        Map switchMap = getAttributesMap('switch')
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  0  : 1
        if (switchMap != null && switchMap != [:]) {
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue)
            logTrace "off() Tuya cmds=${cmds}"
        }
    }
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on()
    }

    String currentState = device.currentState('switch')?.value ?: 'n/a'
    logDebug "off() currentState=${currentState}"
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

void on() {
    if (this.respondsTo('customOn')) { customOn() ; return }
    List<String> cmds = []
    // added 06/29/2024 - control Tuya 0xEF00 switch
    if (this.respondsTo(getDEVICE)) {   // defined in deviceProfileLib
        Map switchMap = getAttributesMap('switch')
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  1  : 0
        if (switchMap != null && switchMap != [:]) {
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue)
            logTrace "on() Tuya cmds=${cmds}"
        }
    }
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off()
    }
    String currentState = device.currentState('switch')?.value ?: 'n/a'
    logDebug "on() currentState=${currentState}"
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

void sendSwitchEvent(int switchValuePar) {
    int switchValue = safeToInt(switchValuePar)
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) {
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00
    }
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown'
    Map map = [:]
    boolean isRefresh = state.states['isRefresh'] ?: false
    boolean debounce = state.states['debounce'] ?: false
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown'
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false)) && !isRefresh) {
        logDebug "Ignored duplicated switch event ${value}"
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])
        return
    }
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}"
    boolean isDigital = state.states['isDigital'] ?: false
    map.type = isDigital ? 'digital' : 'physical'
    if (lastSwitch != value) {
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>"
        state.states['debounce'] = true
        state.states['lastSwitch'] = value
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])
    } else {
        state.states['debounce'] = true
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])
    }
    map.name = 'switch'
    map.value = value
    if (isRefresh) {
        map.descriptionText = "${device.displayName} is ${value} [Refresh]"
        map.isStateChange = true
    } else {
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]"
    }
    logInfo "${map.descriptionText}"
    sendEvent(map)
    clearIsDigital()
    if (this.respondsTo('customSwitchEventPostProcesing')) {
        customSwitchEventPostProcesing(map)
    }
}

void parseOnOffAttributes(final Map it) {
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}"
    /* groovylint-disable-next-line VariableTypeRequired */
    String mode
    String attrName
    if (it.value == null) {
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}"
        return
    }
    int value = zigbee.convertHexToInt(it.value)
    switch (it.attrId) {
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only
            attrName = 'Global Scene Control'
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null
            break
        case '4001' :    // non-Tuya OnTime (UINT16), read-only
            attrName = 'On Time'
            mode = value
            break
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only
            attrName = 'Off Wait Time'
            mode = value
            break
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1
            attrName = 'Power On State'
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN'
            break
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]]
            attrName = 'Child Lock'
            mode = value == 0 ? 'off' : 'on'
            break
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]]
            attrName = 'LED mode'
            if (isCircuitBreaker()) {
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null
            }
            else {
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null
            }
            break
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]]
            attrName = 'Power On State'
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null
            break
        case '8003' : //  Over current alarm
            attrName = 'Over current alarm'
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null
            break
        default :
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}"
            return
    }
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" }
}

List<String> onOffRefresh() {
    logDebug 'onOffRefresh()'
    List<String> cmds = zigbee.readAttribute(0x0006, 0x0000, [:], delay = 100)
    return cmds
}

void onOfInitializeVars( boolean fullInit = false ) {
    logDebug "onOfInitializeVars()... fullInit = ${fullInit}"
    if (fullInit || settings?.ignoreDuplicated == null) { device.updateSetting('ignoreDuplicated', true) }
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) }
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) }
}
