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
  * ver. 1.0.1  2024-01-10 kkossev - first version for Matter Advanced Bridge
  *
  *                                   TODO:
  *
*/

metadata {
    definition(name: 'Matter Generic Component Window Shade', namespace: 'kkossev', author: 'Jonathan Bradshaw') {
        capability 'Actuator'
        capability 'WindowShade'
        capability 'Refresh'
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

// Called when the device is first created
void installed() {
    log.info "${device} driver installed"
}

// Component command to open device
void open() {
    parent?.componentOpen(device)
}

// Component command to close device
void close() {
    parent?.componentClose(device)
}

// parse commands from parent
void parse(List<Map> description) {
    if (logEnable) { log.debug description }
    description.each { d ->
        if (d.descriptionText && txtEnable) { log.info "${device} ${d.descriptionText}" }
        sendEvent(d)
    }
}

// Component command to set position of device
void setPosition(BigDecimal position) {
    parent?.componentSetPosition(device, position)
}

// Component command to start position change of device
void startPositionChange(String change) {
    parent?.componentStartPositionChange(device, change)
}

// Component command to start position change of device
void stopPositionChange() {
    parent?.componentStopPositionChange(device)
}

// Called when the device is removed
void uninstalled() {
    log.info "${device} driver uninstalled"
}

// Called when the settings are updated
void updated() {
    log.info "${device} driver configuration updated"
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
