/* groovylint-disable-next-line CompileStatic */
/*
  *  'Matter Generic Component Motion Sensor' - component driver for Matter Advanced Bridge
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
  * ver. 0.0.0  2024-01-11 kkossev  - first version
  * ver. 0.0.1  2024-01-12 kkossev  - ignoring switch events if no change
  * ver. 0.0.2  2024-01-20 kkossev  - debug logs
  * ver. 0.0.3  2024-03-04 kkossev  - matterComponentSwitchVersion, matterComponentSwitchStamp, added importUrl; disabled healthStatus
  *
  *                                   TODO:
  *
*/

import groovy.transform.Field

@Field static final String matterComponentSwitchVersion = '0.0.3'
@Field static final String matterComponentSwitchStamp   = '2024/03/04 8:14 AM'

metadata {
    definition(name: 'Matter Generic Component Switch', namespace: 'kkossev', author: 'Krassimir Kossev', importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Matter%20Advanced%20Bridge/Matter_Generic_Component_Switch.groovy') {
        capability 'Actuator'
        capability 'Switch'             // Commands:[off, on, refresh]
        capability 'Refresh'
        //capability 'Health Check'       // Commands:[ping]

        //attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
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

/* groovylint-disable-next-line UnusedMethodParameter */
void parse(String description) { log.warn 'parse(String description) not implemented' }

// parse commands from parent
void parse(List<Map> description) {
    //if (logEnable) { log.debug "${description}" }
    description.each { d ->
        if (d.name == 'switch') {
            if (device.currentValue('switch') != d.value) {
                if (d.descriptionText && txtEnable) { log.info "${d.descriptionText} (value changed)" }
                sendEvent(d)
            }
            else {
                if (logEnable) { log.debug "${device.displayName} : ignored switch event '${d.value}' (no change)" }
            }
        }
        else {
            if (d.descriptionText && txtEnable) { log.info "${d.descriptionText}" }
            sendEvent(d)
        }
    }
}

// Component command to turn device on
void on() {
    if (logEnable) { log.debug "${device.displayName} turning on ..." }
    parent?.componentOn(device)
}

// Component command to turn device off
void off() {
    if (logEnable) { log.debug "${device.displayName} turning off ..." }
    parent?.componentOff(device)
}

// Component command to ping the device
void ping() {
    parent?.componentPing(device)
}

// Called when the device is first created
void installed() {
    log.info "${device.displayName} driver installed"
}

// Called when the device is removed
void uninstalled() {
    log.info "${device.displayName} driver uninstalled"
}

// Called when the settings are updated
void updated() {
    log.info "${device.displayName} driver configuration updated"
    if (logEnable) {
        log.debug settings
        runIn(86400, 'logsOff')
    }
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void logsOff() {
    log.warn "debug logging disabled for ${device.displayName} "
    device.updateSetting('logEnable', [value: 'false', type: 'bool'] )
}

void refresh() {
    parent?.componentRefresh(this.device)
}

public void setState(String stateName, String stateValue) {
    if (logEnable) { log.debug "${device.displayName} setting state '${stateName}' to '${stateValue}'" }
    state[stateName] = stateValue
}

public String getState(String stateName) {
    if (logEnable) { log.debug "${device.displayName} getting state '${stateName}'" }
    return state[stateName]
}
