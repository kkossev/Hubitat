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
  * ver. 1.0.0  2022-11-28 bradsjm - original code for Tuya Cloud driver
  * ver. 1.0.1  2024-01-14 kkossev - first version for the Matter Advanced Bridge driver
  *
  *                                   TODO: add a timeout preference for the position change
  *                                   TODO: restart the timer on each position change event
  *                                   TODO: when the timer expires, re-evaluate the windowShade state (should be either open, closed or partially open)
  *
*/

import groovy.transform.Field

@Field static final String matterComponentMotionVersion = '1.0.1'
@Field static final String matterComponentMotionStamp   = '2024/01/14 12:08 AM'

@Field static final Integer POSITION_DELTA = 5

metadata {
    definition(name: 'Matter Generic Component Window Shade', namespace: 'kkossev', author: 'Jonathan Bradshaw') {
        capability 'Actuator'
        capability 'WindowShade'    // Attributes: position - NUMBER, unit:% windowShade - ENUM ["opening", "partially open", "closed", "open", "closing", "unknown"]
                                    // Commands: close(); open(); setPosition(position) position required (NUMBER) - Shade position (0 to 100);
                                    //           startPositionChange(direction): direction required (ENUM) - Direction for position change request ["open", "close"]
                                    //            stopPositionChange()
        capability 'Refresh'
        capability 'Health Check'       // Commands:[ping]

        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'targetPosition', 'number'    // ZemiSmart M1 is updating this attribute, not the position :(
        //attribute 'operationalStatus', 'enum', ['unknown', 'open', 'closed', 'opening', 'closing', 'partially open']
        attribute 'operationalStatus', 'number'
    }
}

preferences {
    section {
        input name: 'logEnable',
              type: 'bool',
              title: 'Enable debug logging',
              required: false,
              defaultValue: true

        input name: 'txtEnable',
              type: 'bool',
              title: 'Enable descriptionText logging',
              required: false,
              defaultValue: true
    }
}

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
            log.warn "${device} : unexpected '${d.name}' event"
            if (d.descriptionText && txtEnable) { log.info "${device} ${d.descriptionText}" }
            sendEvent(d)
        }
    }
}

void processCurrentPosition(Map d) {
    if (logEnable) { log.debug "${device} processCurrentPosition: ${d}" }
    if (d.descriptionText && txtEnable) { log.info "${device} ${d.descriptionText}" }
    sendEvent(d)
    // if the currentPosition is greater than POSITION_DELTA, then the shade is closed
    // if the currentPosition is less than 100 - POSITION_DELTA, then the shade is open
    // if the currentPosition is between 100 - POSITION_DELTA and POSITION_DELTA, then the shade is partially open
    String descriptionText
    Integer currentPosition = safeToInt(d.value)
    Integer targetPosition = device.currentValue('targetPosition') ?: 0
    log.debug "${device} processCurrentPosition: targetPosition: ${targetPosition}, currentPosition: ${currentPosition}, windowShade: ${device.currentValue('windowShade')}"

    // if the windowShade is moving, then do not change the windowShade state!
    if ((device.currentValue('windowShade') ?: 'unknown') in ['opening', 'closing']) {
        // of the windowShade state was changed less than 1000 ms ago, then do not change the windowShade state!
        def latestEvent = device.latestState('windowShade', skipCache=true)
        //Integer latestEventTime = latestEvent != null ? latestEvent.getDate().getTime() : now()
        def latestEventTime = latestEvent != null ? latestEvent.date.time : now()
        def timeDiff = (now() - latestEventTime)  as int
        log.warn "${device} timeDiff: ${timeDiff} latestEvent=${latestEvent} latestEventTime=${latestEventTime}"
        Integer timeSinceLastChange = timeDiff
        if (timeSinceLastChange < 1200) {
            if (logEnable) { log.debug "${device} windowShade is currently ${device.currentValue('windowShade')}, do not change the state!" }
            return
        }
        else {
            if (logEnable) { log.debug "${device} windowShade is currently ${device.currentValue('windowShade')}, but the state was changed ${timeSinceLastChange} ms ago, change the state!" }
        }
    }
    if (currentPosition > 100 - POSITION_DELTA) {
        descriptionText = 'closed'
        sendEvent(name: 'windowShade', value: 'closed', descriptionText: descriptionText)
    }
    else if (currentPosition < POSITION_DELTA) {
        descriptionText = 'open'
        sendEvent(name: 'windowShade', value: 'open', descriptionText: descriptionText)
    }
    else {
        descriptionText = 'partially open'
        sendEvent(name: 'windowShade', value: 'partially open', descriptionText: descriptionText)
    }
    if (txtEnable) { log.info "${device} windowShade is ${descriptionText}" }
}

void processTargetPosition(Map d) {
    if (logEnable) { log.debug "${device} processTargetPosition: ${d}" }
    if (d.descriptionText && txtEnable) { log.info "${device} ${d.descriptionText}" }
    sendEvent(d)
    // if the currentPosition is less than the targetPosition, then the shade is opening
    // if the currentPosition is greater than the targetPosition, then the shade is closing
    String descriptionText
    Integer currentPosition = device.currentValue('position') ?: 0
    Integer targetPosition = safeToInt(d.value)
    log.debug "${device} processTargetPosition: targetPosition: ${targetPosition}, currentPosition: ${currentPosition}, windowShade: ${device.currentValue('windowShade')}"
    /*
    // if the windowShade is moving, then do not change the windowShade state!
    if ((device.currentValue('windowShade') ?: 'unknown') in ['opening', 'closing']) {
        if (logEnable) { log.debug "${device} windowShade is currently ${device.currentValue('windowShade')}, do not change the state!" }
        return
    }
    */
    if (targetPosition < currentPosition) {
        descriptionText = 'opening'
        sendEvent(name: 'windowShade', value: 'opening', descriptionText: descriptionText)
    }
    else {
        descriptionText = 'closing'
        sendEvent(name: 'windowShade', value: 'closing', descriptionText: descriptionText)
    }
    if (txtEnable) { log.info "${device} windowShade is ${descriptionText}" }
}

void processOperationalStatus(Map d) {
    if (logEnable) { log.debug "${device} processOperationalStatus: ${d}" }
    if (d.descriptionText && txtEnable) { log.info "${device} ${d.descriptionText}" }
    sendEvent(d)
}

// Called when the device is first created
void installed() {
    log.info "${device} driver installed"
}

// Component command to open device
void open() {
    if (logEnable) { log.debug "${device} open" }
    sendEvent(name: 'windowShade', value: 'opening', descriptionText: 'opening', type: 'digital')
    sendEvent(name: 'targetPosition', value: 0, descriptionText: 'targetPosition set to 0', type: 'digital')
    if (txtEnable) { log.info "${device} opening" }
    parent?.componentOpen(device)
}

// Component command to close device
void close() {
    if (logEnable) { log.debug "${device} close" }
    sendEvent(name: 'windowShade', value: 'closing', descriptionText: 'closing', type: 'digital')
    sendEvent(name: 'targetPosition', value: 100, descriptionText: 'targetPosition set to 100', type: 'digital')
    if (txtEnable) { log.info "${device} closing" }
    parent?.componentClose(device)
}

// Component command to set position of device
void setPosition(BigDecimal position) {
    if (logEnable) { log.debug "${device} setPosition ${position}" }
    parent?.componentSetPosition(device, position)
}

// Component command to start position change of device
void startPositionChange(String change) {
    if (logEnable) { log.debug "${device} startPositionChange ${change}" }
    String operation = change == 'open' ? 'opening' : 'closing'
    String descriptionText = change == 'open' ? 'opening' : 'closing'
    sendEvent(name: 'windowShade', value: operation, descriptionText: descriptionText, type: 'digital')
    if (txtEnable) { log.info "${device} ${descriptionText}" }
    parent?.componentStartPositionChange(device, change)
}

// Component command to start position change of device
void stopPositionChange() {
    if (logEnable) { log.debug "${device} stopPositionChange" }
    parent?.componentStopPositionChange(device)
}

// Component command to ping the device
void ping() {
    parent?.componentPing(device)
}

// Called when the device is removed
void uninstalled() {
    log.info "${device} driver uninstalled"
}

// Called when the settings are updated
void updated() {
    if (txtEnable) { log.info "${device} driver configuration updated" }
    if (logEnable) {
        log.debug settings
        runIn(1800, 'logsOff')
    }
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void logsOff() {
    log.warn "debug logging disabled for ${device}"
    device.updateSetting('logEnable', [value: 'false', type: 'bool'] )
}

static Integer safeToInt(val, Integer defaultVal=0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}
