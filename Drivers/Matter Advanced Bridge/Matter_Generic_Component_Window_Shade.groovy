/* groovylint-disable CouldBeSwitchStatement, DuplicateNumberLiteral, MethodCount, MethodParameterTypeRequired, NoDef, StaticMethodsBeforeInstanceMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessarySetter, VariableTypeRequired */
/*
  *  ''Matter Generic Component Window Shade' - component driver for Matter Advanced Bridge
  *
  *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
  *  https://community.hubitat.com/t/project-zemismart-m1-matter-bridge-for-tuya-zigbee-devices-matter/127009
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
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project).
  * For a big portions of code all credits go to Jonathan Bradshaw.
  *
  *
  * ver. 0.0.0  2022-11-28 bradsjm - original code (Tuya Cloud driver)
  * ver. 0.0.1  2024-01-14 kkossev - first version for the Matter Advanced Bridge driver
  * ver. 0.0.2  2024-02-04 kkossev - use device.displayName in logs
  * ver. 0.0.3  2024-03-02 kkossev - added refresh() command; added a timeout preference for the position change; added reverse option (normal: OPEN=100% CLOSED=0%)
  * ver. 0.0.4  2024-03-03 kkossev - disabled the ping() command (capability 'Health Check' - not supported yet)
  * ver. 0.0.5  2024-03-04 kkossev - close() bug fix @kwon2288
  * ver. 0.0.6  2024-03-09 kkossev - added Battery capability; added batteryVoltage; added invertPosition and targetAsCurrentPosition preferences;
  * ver. 0.0.7  2024-03-10 kkossev - added help info and community link (credits @jtp10181)
  * ver. 0.0.8  2024-03-11 kkossev - added parseTest(map as string) _DEBUg command in the 'Matter Generic Component Window Shade' driver; battery attributes corrections;
  * ver. 0.0.8  2024-03-11 kkossev - (dev.branch) another exception bug fix;
  *
  *                                   TODO:
*/

import groovy.transform.Field

@Field static final String matterComponentWindowShadeVersion = '0.0.8'
@Field static final String matterComponentWindowShadeStamp   = '2024/03/11 11:22 PM'

@Field static final Boolean _DEBUG = true

@Field static final Integer OPEN   = 0      // this is the standard!  Hubitat is inverted?
@Field static final Integer CLOSED = 100    // this is the standard!  Hubitat is inverted?
@Field static final Integer POSITION_DELTA = 5
@Field static final Integer MAX_TRAVEL_TIME = 15

metadata {
    definition(name: 'Matter Generic Component Window Shade', namespace: 'kkossev', author: 'Krassimir Kossev', importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Matter%20Advanced%20Bridge/Matter_Generic_Component_Window_Shade.groovy', singleThreaded: true) {
        capability 'Actuator'
        capability 'WindowShade'    // Attributes: position - NUMBER, unit:% windowShade - ENUM ["opening", "partially open", "closed", "open", "closing", "unknown"]
                                    // Commands: close(); open(); setPosition(position) position required (NUMBER) - Shade position (0 to 100);
                                    //           startPositionChange(direction): direction required (ENUM) - Direction for position change request ["open", "close"]
                                    //            stopPositionChange()
        capability 'Refresh'
        capability 'Battery'
        //capability 'Health Check'       // Commands:[ping]

        //attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'targetPosition', 'number'            // ZemiSmart M1 is updating this attribute, not the position :(
        attribute 'operationalStatus', 'number'         // 'enum', ['unknown', 'open', 'closed', 'opening', 'closing', 'partially open']

        attribute 'batteryVoltage', 'number'
        attribute 'batStatus', 'string'             // Aqara E1 blinds
        attribute 'batOrder', 'string'              // Aqara E1 blinds
        attribute 'batDescription', 'string'        // Aqara E1 blinds
        attribute 'batTimeRemaining', 'string'
        attribute 'batChargeLevel', 'string'            // Aqara E1 blinds
        attribute 'batReplacementNeeded', 'string'      // Aqara E1 blinds
        attribute 'batReplaceability', 'string'
        attribute 'batReplacementDescription', 'string'
        attribute 'batQuantity', 'string'


        if (_DEBUG) {
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']]
        }        
    }
}

preferences {
    section {
	    input name: "helpInfo", type: "hidden", title: fmtHelpInfo("Community Link")
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', required: false, defaultValue: true
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>',           required: false, defaultValue: false
        input name: 'maxTravelTime', type: 'number', title: '<b>Maximum travel time</b>', description: '<i>The maximum time to fully open or close (Seconds)</i>', required: false, defaultValue: MAX_TRAVEL_TIME
        input name: 'deltaPosition', type: 'number', title: '<b>Position delta</b>', description: '<i>The maximum error step reaching the target position</i>', required: false, defaultValue: POSITION_DELTA
        input name: 'substituteOpenClose', type: 'bool', title: '<b>Substitute Open/Close w/ setPosition</b>', description: '<i>Non-standard Zemismart motors</i>', required: false, defaultValue: false
        input name: 'invertPosition', type: 'bool', title: '<b>Reverse Position Reports</b>', description: '<i>Non-standard Zemismart motors</i>', required: false, defaultValue: false
        input name: 'targetAsCurrentPosition', type: 'bool', title: '<b>Reverse Target and Current Position</b>', description: '<i>Non-standard Zemismart motors</i>', required: false, defaultValue: false
    }
}

int getDelta() { return settings?.deltaPosition != null ? settings?.deltaPosition as int : POSITION_DELTA }
//int getFullyOpen()   { return settings?.invertOpenClose ? CLOSED : OPEN }
//int getFullyClosed() { return settings?.invertOpenClose ? OPEN : CLOSED }
//int getFullyOpen()   { return settings?.invertPosition ? CLOSED : OPEN }
//int getFullyClosed() { return settings?.invertPosition ? OPEN : CLOSED }
int getFullyOpen()   { return  OPEN }
int getFullyClosed() { return CLOSED }
boolean isFullyOpen(int position)   { return Math.abs(position - getFullyOpen()) < getDelta() }
boolean isFullyClosed(int position) { return Math.abs(position - getFullyClosed()) < getDelta() }

// parse commands from parent
void parse(List<Map> description) {
    if (logEnable) { log.debug "parse: ${description}" }
    description.each { d ->
        if (d?.name == 'position') {
            processCurrentPositionBridgeEvent(d)
        }
        else if (d?.name == 'targetPosition') {
            processTargetPositionBridgeEvent(d)
        }
        else if (d?.name == 'operationalStatus') {
            processOperationalStatusBridgeEvent(d)
        }
        else {
            if (d?.descriptionText && txtEnable) { log.info "${d.descriptionText}" }
            log.trace "parse: ${d}"
            sendEvent(d)
        }
    }
}

int invertPositionIfNeeded(int position) {
    int value =  (settings?.invertPosition ?: false) ? (100 - position) as Integer : position
    if (value < 0)   { value = 0 }
    if (value > 100) { value = 100 }
    return value
}

void processCurrentPositionBridgeEvent(final Map d) {
    Map map = new HashMap(d)
    //stopOperationTimeoutTimer()
    if (settings?.targetAsCurrentPosition == true) {
        map.name = 'targetPosition'
        if (logEnable) { log.debug "${device.displayName} processCurrentPositionBridgeEvent: targetAsCurrentPosition is true -> <b>processing as targetPosition ${map.value} !</b>" }
        processTargetPosition(map)
    }
    else {
        if (logEnable) { log.debug "${device.displayName} processCurrentPositionBridgeEvent: currentPosition reported is ${map.value}" }
        processCurrentPosition(map)
    }
}

void processCurrentPosition(final Map d) {
    Map map = new HashMap(d)
    stopOperationTimeoutTimer()
    // we may have the currentPosition reported inverted !
    map.value = invertPositionIfNeeded(d.value as int)
    if (logEnable) { log.debug "${device.displayName} processCurrentPosition: ${map.value} (was ${d.value})" }
    map.name = 'position'
    map.unit = '%'
    map.descriptionText = "${device.displayName} position is ${map.value}%"
    if (map.isRefresh) {
        map.descriptionText += ' [refresh]'
    }
    if (txtEnable) { log.info "${map.descriptionText}" }
    sendEvent(map)
    updateWindowShadeStatus(map.value as int, device.currentValue('targetPosition') as int, /*isFinal =*/ true, /*isDigital =*/ false)
}

void updateWindowShadeStatus(int currentPositionPar, int targetPositionPar, Boolean isFinal, Boolean isDigital) {
    String value = 'unknown'
    String descriptionText = 'unknown'
    String type = isDigital ? 'digital' : 'physical'
    //log.trace "updateWindowShadeStatus: currentPositionPar = ${currentPositionPar}, targetPositionPar = ${targetPositionPar}"
    Integer currentPosition = currentPositionPar as int
    Integer targetPosition = targetPositionPar as int

    if (isFinal == true) {
        if (isFullyClosed(currentPosition)) {
            value = 'closed'
        }
        else if (isFullyOpen(currentPosition)) {
            value = 'open'
        }
        else {
            value = 'partially open'
        }
    }
    else {
        if (targetPosition < currentPosition) {
            value =  'opening'
        }
        else if (targetPosition > currentPosition) {
            value = 'closing'
        }
        else {
            //value = 'stopping'
            if (isFullyClosed(currentPosition)) {
                value = 'closed'
            }
            else if (isFullyOpen(currentPosition)) {
                value = 'open'
            }            
        }
    }
    descriptionText = "${device.displayName} windowShade is ${value} [${type}]"
    sendEvent(name: 'windowShade', value: value, descriptionText: descriptionText, type: type)
    if (logEnable) { log.debug "${device.displayName} updateWindowShadeStatus: isFinal: ${isFinal}, substituteOpenClose: ${settings?.substituteOpenClose}, targetPosition: ${targetPosition}, currentPosition: ${currentPosition}, windowShade: ${device.currentValue('windowShade')}" }
    if (txtEnable) { log.info "${descriptionText}" }
}

void sendWindowShadeEvent(String value, String descriptionText) {
    sendEvent(name: 'windowShade', value: value, descriptionText: descriptionText)
    if (txtEnable) { log.info "${device.displayName} windowShade is ${value}" }
}

void processTargetPositionBridgeEvent(final Map d) {
    Map map = new HashMap(d)
    stopOperationTimeoutTimer()
    if (logEnable) { log.debug "${device.displayName} processTargetPositionBridgeEvent: ${d}" }
    if (settings?.targetAsCurrentPosition) {
        if (logEnable) { log.debug "${device.displayName} processTargetPositionBridgeEvent: targetAsCurrentPosition is true" }
        map.name = 'position'
        processCurrentPosition(map)
        return
    }
    processTargetPosition(map)
}

void processTargetPosition(final Map d) {
    //log.trace "processTargetPosition: value: ${d.value}"
    Map map = new HashMap(d)
    map.value = invertPositionIfNeeded(safeToInt(d.value))
    map.descriptionText = "${device.displayName} targetPosition is ${map.value}%"
    if (map.isRefresh) {
        map.descriptionText += ' [refresh]'
    }
    map.name = 'targetPosition'
    map.unit = '%'
    if (logEnable) { log.debug "${device.displayName} processTargetPosition: ${map.value} (was ${d.value})" }
    if (txtEnable) { log.info "${map.descriptionText}" }
    //
    //stopOperationTimeoutTimer()
    sendEvent(map)
    if (!map.isRefresh) {
        // skip upddating the windowShade status on targetPosition refresh
        updateWindowShadeStatus(device.currentValue('position') as int, map.value as int, /*isFinal =*/ false, /*isDigital =*/ false)
    }
}

void processOperationalStatusBridgeEvent(Map d) {
    stopOperationTimeoutTimer()
    if (logEnable) { log.debug "${device.displayName} processOperationalStatusBridgeEvent: ${d}" }
    if (d.descriptionText && txtEnable) { log.info "${device.displayName} ${d.descriptionText}" }
    sendEvent(d)
}

// Called when the device is first created
void installed() {
    log.info "${device.displayName} driver installed"
}

// Component command to open device
void open() {
    if (txtEnable) { log.info "${device.displayName} opening" }
    sendEvent(name: 'targetPosition', value: OPEN, descriptionText: "targetPosition set to ${OPEN}", type: 'digital')
    if (settings?.substituteOpenClose == false) {
        parent?.componentOpen(device)
    }
    else {
        setPosition(getFullyOpen())
    }
    startOperationTimeoutTimer()
    sendWindowShadeEvent('opening', "${device.displayName} windowShade is opening")
}

// Component command to close device
void close() {
    if (logEnable) { log.debug "${device.displayName} closing [digital]" }
    sendEvent(name: 'targetPosition', value: CLOSED, descriptionText: "targetPosition set to ${CLOSED}", type: 'digital')
    if (settings?.substituteOpenClose == false) {
        if (logEnable) { log.debug "${device.displayName} sending componentClose() command to the parent" }
        parent?.componentClose(device)
    }
    else {
        if (logEnable) { log.debug "${device.displayName} sending componentSetPosition(${getFullyClosed()}) command to the parent" }
        setPosition(getFullyClosed())
    }
    startOperationTimeoutTimer()
    sendWindowShadeEvent('closing', "${device.displayName} windowShade is closing [digital]")
}

// Component command to set position of device
void setPosition(BigDecimal targetPosition) {
    if (txtEnable) { log.info "${device.displayName} setting target position ${targetPosition}% (current position is ${device.currentValue('position')})" }
    sendEvent(name: 'targetPosition', value: targetPosition as int, descriptionText: "targetPosition set to ${targetPosition}", type: 'digital')
    updateWindowShadeStatus(device.currentValue('position') as int, targetPosition as int, isFinal = false, isDigital = true)
    BigDecimal componentTargetPosition = invertPositionIfNeeded(targetPosition as int)
    if (logEnable) { log.debug "inverted componentTargetPosition: ${componentTargetPosition}" }
    parent?.componentSetPosition(device, componentTargetPosition)
    startOperationTimeoutTimer()
}

// Component command to start position change of device
void startPositionChange(String change) {
    if (logEnable) { log.debug "${device.displayName} startPositionChange ${change}" }
    if (change == 'open') {
        open()
    }
    else {
        close()
    }
}

// Component command to start position change of device
void stopPositionChange() {
    if (logEnable) { log.debug "${device.displayName} stopPositionChange" }
    parent?.componentStopPositionChange(device)
}

// Component command to ping the device
void ping() {
    parent?.componentPing(device)
}

// Component command to refresh the device
void refresh() {
    if (txtEnable) { log.info "${device.displayName} refreshing ..." }
    state.standardOpenClose = 'OPEN = 0% CLOSED = 100%'
    state.driverVersion = matterComponentWindowShadeVersion + ' (' + matterComponentWindowShadeStamp + ')'
    parent?.componentRefresh(device)
}

// Called when the device is removed
void uninstalled() {
    log.info "${device.displayName} driver uninstalled"
}

// Called when the settings are updated
void updated() {
    if (txtEnable) { log.info "${device.displayName} driver configuration updated" }
    if (logEnable) {
        log.debug settings
        runIn(86400, 'logsOff')
    }
    if ((state.substituteOpenClose ?: false) != settings?.substituteOpenClose) {
        state.substituteOpenClose = settings?.substituteOpenClose
        if (logEnable) { log.debug "${device.displayName} substituteOpenClose: ${settings?.substituteOpenClose}" }
        /*
        String currentOpenClose = device.currentWindowShade
        String newOpenClose = currentOpenClose == 'open' ? 'closed' : currentOpenClose == 'closed' ? 'open' : currentOpenClose
        if (currentOpenClose != newOpenClose) {
            sendEvent([name:'windowShade', value: newOpenClose, type: 'digital', descriptionText: "windowShade state inverted to ${newOpenClose}", isStateChange:true])
        }
        */
    }
    else {
        if (logEnable) { log.debug "${device.displayName} invertMotion: no change" }
    }
    //
    if ((state.invertPosition ?: false) != settings?.invertPosition) {
        state.invertPosition = settings?.invertPosition
        if (logEnable) { log.debug "${device.displayName} invertPosition: ${settings?.invertPosition}" }
    }
    else {
        if (logEnable) { log.debug "${device.displayName} invertPosition: no change" }
    }
}

BigDecimal scale(int value, int fromLow, int fromHigh, int toLow, int toHigh) {
    return  BigDecimal.valueOf(toHigh - toLow) *  BigDecimal.valueOf(value - fromLow) /  BigDecimal.valueOf(fromHigh - fromLow) + toLow
}

void startOperationTimeoutTimer() {
    int travelTime = Math.abs(device.currentValue('position') - device.currentValue('targetPosition'))
    Integer scaledTimerValue = scale(travelTime, 0, 100, 1, settings?.maxTravelTime as int) + 1.5
    if (logEnable) { log.debug "${device.displayName} startOperationTimeoutTimer: ${scaledTimerValue} seconds" }
    runIn(scaledTimerValue, 'operationTimeoutTimer', [overwrite: true])
}

void stopOperationTimeoutTimer() {
    if (logEnable) { log.debug "${device.displayName} stopOperationTimeoutTimer" }
    unschedule('operationTimeoutTimer')
}

void operationTimeoutTimer() {
    if (logEnable) { log.warn "${device.displayName} operationTimeout!" }
    updateWindowShadeStatus(device.currentValue('position') as int, device.currentValue('targetPosition') as int, /*isFinal =*/ true, /*isDigital =*/ true)
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void logsOff() {
    log.warn "debug logging disabled for ${device.displayName}"
    device.updateSetting('logEnable', [value: 'false', type: 'bool'] )
}

static Integer safeToInt(val, Integer defaultVal=0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

@Field static final String DRIVER = 'Matter Advanced Bridge'
@Field static final String COMPONENT = 'Matter Generic Component Window Shade'
@Field static final String WIKI   = 'Get help on GitHub Wiki page:'
@Field static final String COMM_LINK =   "https://community.hubitat.com/t/project-nearing-beta-release-zemismart-m1-matter-bridge-for-tuya-zigbee-devices-matter/127009"
@Field static final String GITHUB_LINK = "https://github.com/kkossev/Hubitat/wiki/Matter-Advanced-Bridge-%E2%80%90-Window-Covering"
// credits @jtp10181
String fmtHelpInfo(String str) {
	String info = "${DRIVER} v${parent?.version()}<br> ${COMPONENT} v${matterComponentWindowShadeVersion}"
	String prefLink = "<a href='${GITHUB_LINK}' target='_blank'>${WIKI}<br><div style='font-size: 70%;'>${info}</div></a>"
    String topStyle = "style='font-size: 18px; padding: 1px 12px; border: 2px solid green; border-radius: 6px; color: green;'"
    String topLink = "<a ${topStyle} href='${COMM_LINK}' target='_blank'>${str}<br><div style='font-size: 14px;'>${info}</div></a>"

	return "<div style='font-size: 160%; font-style: bold; padding: 2px 0px; text-align: center;'>${prefLink}</div>" +
		"<div style='text-align: center; position: absolute; top: 46px; right: 60px; padding: 0px;'><ul class='nav'><li>${topLink}</ul></li></div>"
}

void parseTest(description) {
    log.warn "parseTest: ${description}"
    //String str = "name:position, value:0, descriptionText:Bridge#4266 Device#32 (tuya CURTAIN) position is is reported as 0 (to be re-processed in the child driver!) [refresh], unit:null, type:physical, isStateChange:true, isRefresh:true"
    String str = description
    // Split the string into key-value pairs
    List<String> pairs = str.split(', ')
    Map map = [:]
    pairs.each { pair ->
        // Split each pair into a key and a value
        List<String> keyValue = pair.split(':')
        String key = keyValue[0]
        String value = keyValue[1..-1].join(':') // Join the rest of the elements in case the value contains colons
        // Try to convert the value to a boolean or integer if possible
        if (value == 'true' || value == 'false' || value == true || value == false) {
            value = Boolean.parseBoolean(value)
        } else if (value.isInteger()) {
            value = Integer.parseInt(value)
        } else if (value == 'null') {
            value = null
        }
        // Add the key-value pair to the map
        map[key] = value
    }
    log.debug map
    parse([map])
}