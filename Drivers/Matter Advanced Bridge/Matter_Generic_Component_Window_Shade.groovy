/* groovylint-disable CouldBeSwitchStatement, DuplicateNumberLiteral, MethodParameterTypeRequired, NoDef, StaticMethodsBeforeInstanceMethods, UnnecessaryElseStatement, UnnecessaryGetter, VariableTypeRequired */
/* groovylint-disable-next-line CompileStatic */
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
  * ver. 0.0.4  2024-03-02 kkossev - (dev.branch) disabled ping() command (capability 'Health Check' - not supported yet)
  *
  *                                   TODO: 
  *
*/

import groovy.transform.Field

@Field static final String matterComponentMotionVersion = '0.0.4'
@Field static final String matterComponentMotionStamp   = '2024/03/03 12:52 AM'

@Field static final Integer OPEN   = 0      // this is the sandard!  Hubitat is inverted!
@Field static final Integer CLOSED = 100    // this is the sandard!  Hubitat is inverted!
@Field static final Integer POSITION_DELTA = 5
@Field static final Integer MAX_TRAVEL_TIME = 15

metadata {
    definition(name: 'Matter Generic Component Window Shade', namespace: 'kkossev', author: 'Krassimir Kossev') {
        capability 'Actuator'
        capability 'WindowShade'    // Attributes: position - NUMBER, unit:% windowShade - ENUM ["opening", "partially open", "closed", "open", "closing", "unknown"]
                                    // Commands: close(); open(); setPosition(position) position required (NUMBER) - Shade position (0 to 100);
                                    //           startPositionChange(direction): direction required (ENUM) - Direction for position change request ["open", "close"]
                                    //            stopPositionChange()
        capability 'Refresh'
        //capability 'Health Check'       // Commands:[ping]

        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'targetPosition', 'number'    // ZemiSmart M1 is updating this attribute, not the position :(
        attribute 'operationalStatus', 'number' // 'enum', ['unknown', 'open', 'closed', 'opening', 'closing', 'partially open']
    }
}

preferences {
    section {
        input name: 'txtEnable', type: 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
        input name: 'logEnable', type: 'bool', title: 'Enable debug logging',           required: false, defaultValue: false
        input name: 'maxTravelTime', type: 'number', title: 'Maximum travel time (Seconds)', required: false, defaultValue: MAX_TRAVEL_TIME
        input name: 'invertOpenClose', type: 'bool', title: 'Reverse Open and Close', required: false, defaultValue: true
    }
}

int getFullyOpen()   { return settings?.invertOpenClose ? CLOSED : OPEN }
int getFullyClosed() { return settings?.invertOpenClose ? OPEN : CLOSED }
boolean isFullyOpen(int position)   { return Math.abs(position - getFullyOpen()) < POSITION_DELTA }
boolean isFullyClosed(int position) { return Math.abs(position - getFullyClosed()) < POSITION_DELTA }

// parse commands from parent
void parse(List<Map> description) {
    if (logEnable) { log.debug description }
    description.each { d ->
        if (d.name == 'position') {
            processCurrentPosition(d)
        }
        else if (d.name == 'targetPosition') {
            processTargetPosition(d)
        }
        else if (d.name == 'operationalStatus') {
            processOperationalStatus(d)
        }
        else {
            if (d.descriptionText && txtEnable) { log.info "${device.displayName} ${d.descriptionText}" }
            sendEvent(d)
        }
    }
}

void processCurrentPosition(Map d) {
    stopOperationTimeoutTimer()
    if (logEnable) { log.debug "${device.displayName} processCurrentPosition: ${d}" }
    if (d.descriptionText && txtEnable) { log.info "${device.displayName} ${d.descriptionText}" }
    sendEvent(d)
    String descriptionText
    Integer currentPosition = safeToInt(d.value)
    Integer targetPosition = device.currentValue('targetPosition') ?: -1
    if (logEnable) { log.debug "${device.displayName} processCurrentPosition: invertOpenClose: ${settings?.invertOpenClose}, targetPosition: ${targetPosition}, currentPosition: ${currentPosition}, windowShade: ${device.currentValue('windowShade')}" }

    if (isFullyClosed(currentPosition)) {
        descriptionText = 'closed'
        sendEvent(name: 'windowShade', value: 'closed', descriptionText: descriptionText)
    }
    else if (isFullyOpen(currentPosition)) {
        descriptionText = 'open'
        sendEvent(name: 'windowShade', value: 'open', descriptionText: descriptionText)
    }
    else {
        descriptionText = 'partially open'
        sendEvent(name: 'windowShade', value: 'partially open', descriptionText: descriptionText)
    }
    if (txtEnable) { log.info "${device.displayName} windowShade is ${descriptionText}" }
}

void updatewindowShadeMovingStatus(int targetPosition, String type = 'digital') {
    String movementDirection
    Integer currentPosition = device.currentValue('position', true) ?: -1
    /*
    if (currentPosition == targetPosition) {
        if (logEnable) { log.debug "${device.displayName} updatewindowShadeMovingStatus: currentPosition ${currentPosition} == targetPosition ${currentPosition} - windowShade ${device.currentValue('windowShade') } will not be changed!" }
        return
    }
    */
    if (logEnable) { log.debug "${device.displayName} updatewindowShadeMovingStatus: targetPosition: ${targetPosition}, currentPosition: ${currentPosition}, windowShade: ${device.currentValue('windowShade')}" }

    if (targetPosition < currentPosition) {
        movementDirection = settings?.invertOpenClose ? 'closing' : 'opening'
    }
    else {
        movementDirection = settings?.invertOpenClose ? 'opening' : 'closing'
    }
    sendEvent(name: 'windowShade', value: movementDirection, descriptionText: movementDirection, type: type)
    if (txtEnable) { log.info "${device.displayName} windowShade is ${movementDirection}" }
}

void processTargetPosition(Map d) {
    stopOperationTimeoutTimer()
    if (logEnable) { log.debug "${device.displayName} processTargetPosition: ${d}" }
    if (d.descriptionText && txtEnable) { log.info "${device.displayName} ${d.descriptionText}" }
    sendEvent(d)
    if (d.descriptionText.contains('[refresh]')) {
        if (logEnable) { log.debug "${device.displayName} processTargetPosition: [refresh] - skipping updatewindowShadeMovingStatus!" }
        return
    }
    updatewindowShadeMovingStatus(safeToInt(d.value), 'physical')
}

void processOperationalStatus(Map d) {
    stopOperationTimeoutTimer()
    if (logEnable) { log.debug "${device.displayName} processOperationalStatus: ${d}" }
    if (d.descriptionText && txtEnable) { log.info "${device.displayName} ${d.descriptionText}" }
    sendEvent(d)
}

// Called when the device is first created
void installed() {
    log.info "${device.displayName} driver installed"
}

// Component command to open device
void open() {
    sendEvent(name: 'windowShade', value: 'opening', descriptionText: 'opening', type: 'digital')
    sendEvent(name: 'targetPosition', value: getFullyOpen(), descriptionText: "targetPosition set to ${getFullyOpen()}", type: 'digital')
    if (txtEnable) { log.info "${device.displayName} opening" }
    if (settings?.invertOpenClose == false) {
        parent?.componentOpen(device)
    }
    else {
        parent?.componentClose(device)
    }
    startOperationTimeoutTimer()
}

// Component command to close device
void close() {
    sendEvent(name: 'windowShade', value: 'closing', descriptionText: 'closing', type: 'digital')
    sendEvent(name: 'targetPosition', value: getFullyClosed(), descriptionText: "targetPosition set to ${getFullyClosed()}", type: 'digital')
    if (txtEnable) { log.info "${device.displayName} closing" }
    if (settings?.invertOpenClose == false) {
        parent?.componentClose(device)
    }
    else {
        parent?.componentOpen(device)
    }
    startOperationTimeoutTimer()
}

// Component command to set position of device
void setPosition(BigDecimal position) {
    if (logEnable) { log.debug "${device.displayName} setPosition ${position}" }
    sendEvent(name: 'targetPosition', value: position, descriptionText: "targetPosition set to ${position}", type: 'digital')
    parent?.componentSetPosition(device, position)
    updatewindowShadeMovingStatus(position.toInteger())
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
    if ((state.invertOpenClose ?: false) != settings?.invertOpenClose) {
        state.invertOpenClose = settings?.invertOpenClose
        if (logEnable) { log.debug "${device.displayName} invertOpenClose: ${settings?.invertOpenClose}" }
        String currentOpenClose = device.currentWindowShade
        String newOpenClose = currentOpenClose == 'open' ? 'closed' : currentOpenClose == 'closed' ? 'open' : currentOpenClose
        if (currentOpenClose != newOpenClose) {
            sendEvent([name:'windowShade', value: newOpenClose, type: 'digital', descriptionText: "windowShade state inverted to ${newOpenClose}", isStateChange:true])
        }
    }
    else {
        if (logEnable) { log.debug "${device.displayName} invertMotion: no change" }
    }    
}

void updatewindowShade() {
    if (logEnable) { log.debug "${device.displayName} updatewindowShade" }
    Integer currentPosition = device.currentValue('position') ?: -1
    if (isFullyClosed(currentPosition)) {
        descriptionText = 'closed'
        sendEvent(name: 'windowShade', value: 'closed', descriptionText: descriptionText)
    }
    else if (isFullyOpen(currentPosition)) {
        descriptionText = 'open'
        sendEvent(name: 'windowShade', value: 'open', descriptionText: descriptionText)
    }
    else {
        descriptionText = 'partially open'
        sendEvent(name: 'windowShade', value: 'partially open', descriptionText: descriptionText)
    }
}

BigDecimal scale(int value, int fromLow, int fromHigh, int toLow, int toHigh) {
    return  BigDecimal.valueOf(toHigh - toLow) *  BigDecimal.valueOf(value - fromLow) /  BigDecimal.valueOf(fromHigh - fromLow) + toLow
}

void startOperationTimeoutTimer() {
    if (logEnable) { log.debug "${device.displayName} startOperationTimeoutTimer" }
    int travelTime = Math.abs(device.currentValue('position', true) - device.currentValue('targetPosition', true))
    Integer scaledTimerValue = scale(travelTime, 0, 100, 1, settings?.maxTravelTime as int) + 0.5
    runIn(scaledTimerValue, 'operationTimeoutTimer', [overwrite: true])
}

void stopOperationTimeoutTimer() {
    if (logEnable) { log.debug "${device.displayName} stopOperationTimeoutTimer" }
    unschedule('operationTimeoutTimer')
}

void operationTimeoutTimer() {
    if (logEnable) { log.warn "${device.displayName} operationTimeout!" }
    updatewindowShade()
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void logsOff() {
    log.warn "debug logging disabled for ${device.displayName}"
    device.updateSetting('logEnable', [value: 'false', type: 'bool'] )
}

static Integer safeToInt(val, Integer defaultVal=0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}
