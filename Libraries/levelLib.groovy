/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Level Library', name: 'levelLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/levelLib.groovy', documentationLink: '',
    version: '3.2.0'
)
/*
 *  Zigbee Level Library
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
 * ver. 3.0.0  2024-04-06 kkossev  - added levelLib.groovy
 * ver. 3.2.0  2024-05-22 kkossev  - commonLib 3.2.0 allignment
 *
 *                                   TODO:
*/

static String levelLibVersion()   { '3.2.0' }
static String levelLibStamp() { '2024/05/22 10:00 PM' }

metadata {
    capability 'Switch'             // TODO - move to a new library
    capability 'Switch Level'       // Attributes: level - NUMBER, unit:%; Commands:setLevel(level, duration) level required (NUMBER) - Level to set (0 to 100); duration optional (NUMBER) - Transition duration in seconds
    capability 'ChangeLevel'        // Commands : startLevelChange(direction);  direction required (ENUM) - Direction for level change request; stopLevelChange()
    // no attributes
    // no commands
    preferences {
        input name: 'levelUpTransition', type: 'enum', title: '<b>Dim up transition length</b>', options: TransitionOpts.options, defaultValue: TransitionOpts.defaultValue, required: true, description: '<i>Changes the speed the light dims up. Increasing the value slows down the transition.</i>'
        input name: 'levelDownTransition', type: 'enum', title: '<b>Dim down transition length</b>', options: TransitionOpts.options, defaultValue: TransitionOpts.defaultValue, required: true, description: '<i>Changes the speed the light dims down. Increasing the value slows down the transition.</i>'
        input name: 'levelChangeRate', type: 'enum', title: '<b>Level change rate</b>', options: LevelRateOpts.options, defaultValue: LevelRateOpts.defaultValue, required: true, description: '<i>Changes the speed that the light changes when using <b>start level change</b> until <b>stop level change</b> is sent.</i>'
    }
}

import groovy.transform.Field

@Field static final Map TransitionOpts = [
    defaultValue: 0x0004,
    options: [
        0x0000: 'No Delay',
        0x0002: '200ms',
        0x0004: '400ms',
        0x000A: '1s',
        0x000F: '1.5s',
        0x0014: '2s',
        0x001E: '3s',
        0x0028: '4s',
        0x0032: '5s',
        0x0064: '10s'
    ]
]

@Field static final Map LevelRateOpts = [
    defaultValue: 0x64,
    options: [ 0xFF: 'Device Default', 0x16: 'Very Slow', 0x32: 'Slow', 0x64: 'Medium', 0x96: 'Medium Fast', 0xC8: 'Fast' ]
]


/*
 * -----------------------------------------------------------------------------
 * Level Control Cluster            0x0008
 * -----------------------------------------------------------------------------
*/
void standardParseLevelControlCluster(final Map descMap) {
    logDebug "standardParseLevelControlCluster: 0x${descMap.value}"
    if (descMap.attrId == '0000') {
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "standardParseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value
        final long rawValue = hexStrToUnsignedInt(descMap.value)
        // Aqara LED Strip T1 sends the level in the range 0..255
        int scaledValue = ((rawValue as double) / 2.55F + 0.5) as int
        sendLevelControlEvent(scaledValue)
    }
    else {
        logWarn "standardParseLevelControlCluster: unprocessed LevelControl attribute ${descMap.attrId}"
    }
}

void sendLevelControlEvent(final int rawValue) {
    int value = rawValue as int
    if (value < 0) { value = 0 }
    if (value > 100) { value = 100 }
    Map map = [:]

    boolean isDigital = state.states['isDigital']
    map.type = isDigital == true ? 'digital' : 'physical'

    map.name = 'level'
    map.value = value
    boolean isRefresh = state.states['isRefresh'] ?: false
    if (isRefresh == true) {
        map.descriptionText = "${device.displayName} is ${value} [Refresh]"
        map.isStateChange = true
    }
    else {
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]"
    }
    logInfo "${map.descriptionText}"
    sendEvent(map)
    clearIsDigital()
}

/**
 * Set Level Command
 * @param value level percent (0-100)
 * @param transitionTime transition time in seconds
 * @return List of zigbee commands
 */
void setLevel(final BigDecimal value, final BigDecimal transitionTime = null) {
    logInfo "setLevel (${value}, ${transitionTime})"
/*    
    if (this.respondsTo('customSetLevel')) {
        logDebug "calling customSetLevel: ${value}, ${transitionTime}"
        customSetLevel(value.intValue(), transitionTime.intValue())
        return
    }
*/    
    //if (DEVICE_TYPE in  ['Bulb']) { 
    setLevelBulb(value.intValue(), transitionTime ? transitionTime.intValue() : null)
    return 
    //}
    /*
    final Integer rate = getLevelTransitionRate(value.intValue(), transitionTime.intValue())
    scheduleCommandTimeoutCheck()
    sendZigbeeCommands(setLevelPrivate(value.intValue(), rate as int))
    */
}

void setLevelBulb(value, rate=null) {
    logDebug "setLevelBulb: $value, $rate"

    state.pendingLevelChange = value
    if (state.cmds == null) {
        state.cmds = []
    }
    if (rate == null) {
        state.cmds += zigbee.setLevel(value)
    } else {
        state.cmds += zigbee.setLevel(value, rate * 10)
    }

    unschedule(sendLevelZigbeeCommandsDelayed)
    runInMillis(100, sendLevelZigbeeCommandsDelayed)
}

void sendLevelZigbeeCommandsDelayed() {
    List cmds = state.cmds
    if (cmds != null) {
        state.cmds = []
        sendZigbeeCommands(cmds)
    }
}



/**
 * Send 'switchLevel' attribute event
 * @param isOn true if light is on, false otherwise
 * @param level brightness level (0-254)
 */
/* groovylint-disable-next-line UnusedPrivateMethodParameter */
private List<String> setLevelPrivate(final BigDecimal value, final int rate = 0, final int delay = 0, final Boolean levelPreset = false) {
    List<String> cmds = []
    final Integer level = constrain(value)
    //final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8)
    //final String hexRate = DataType.pack(rate, DataType.UINT16, true)
    //final int levelCommand = levelPreset ? 0x00 : 0x04
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) {
        // If light is off, first go to level 0 then to desired level
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}")
    }
    // Payload: Level | Transition Time | Options Mask | Options Override
    // Options: Bit 0x01 enables pre-staging level
    /*
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") +
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) }
    */
    int duration = 10            // TODO !!!
    String endpointId = '01'     // TODO !!!
    cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",]

    return cmds
}


/**
 * Get the level transition rate
 * @param level desired target level (0-100)
 * @param transitionTime transition time in seconds (optional)
 * @return transition rate in 1/10ths of a second
 */
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) {
    int rate = 0
    final Boolean isOn = device.currentValue('switch') == 'on'
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0
    if (!isOn) {
        currentLevel = 0
    }
    // Check if 'transitionTime' has a value
    if (transitionTime > 0) {
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer
        rate = transitionTime * 10
    } else {
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) {
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer
            rate = settings.levelUpTransition.toInteger()
        }
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) {
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer
            rate = settings.levelDownTransition.toInteger()
        }
    }
    logDebug "using level transition rate ${rate}"
    return rate
}

List<String> startLevelChange(String direction) {
    if (settings.txtEnable) { log.info "startLevelChange (${direction})" }
    String upDown = direction == 'down' ? '01' : '00'
    String rateHex = intToHexStr(settings.levelChangeRate as Integer)
    scheduleCommandTimeoutCheck()
    return zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x05, [:], 0, "${upDown} ${rateHex}")
}


List<String> stopLevelChange() {
    if (settings.txtEnable) { log.info 'stopLevelChange' }
    scheduleCommandTimeoutCheck()
    return zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x03, [:], 0) +
        ifPolling { zigbee.levelRefresh(0) + zigbee.onOffRefresh(0) }
}


// Delay before reading attribute (when using polling)
@Field static final int POLL_DELAY_MS = 1000
// Command option that enable changes when off
@Field static final String PRE_STAGING_OPTION = '01 01'

/**
 * If the device is polling, delay the execution of the provided commands
 * @param delayMs delay in milliseconds
 * @param commands commands to execute
 * @return list of commands to be sent to the device
 */
/* groovylint-disable-next-line UnusedPrivateMethod */
private List<String> ifPolling(final int delayMs = 0, final Closure commands) {
    if (state.reportingEnabled == false) {
        final int value = Math.max(delayMs, POLL_DELAY_MS)
        return ["delay ${value}"] + (commands() as List<String>) as List<String>
    }
    return []
}

/**
 * Constrain a value to a range
 * @param value value to constrain
 * @param min minimum value (default 0)
 * @param max maximum value (default 100)
 * @param nullValue value to return if value is null (default 0)
 */
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) {
    if (min == null || max == null) {
        return value
    }
    return value != null ? max.min(value.max(min)) : nullValue
}

/**
 * Constrain a value to a range
 * @param value value to constrain
 * @param min minimum value (default 0)
 * @param max maximum value (default 100)
 * @param nullValue value to return if value is null (default 0)
 */
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) {
    if (min == null || max == null) {
        return value as Integer
    }
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue
}

void updatedLevel() {
    logDebug "updatedLevel: ${device.currentValue('level')}"
}

List<String> refreshLevel() {
    List<String> cmds = []
    cmds = zigbee.onOffRefresh(100) + zigbee.levelRefresh(101)
    return cmds
}