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
  * ver. 1.0.0  2024-01-10 kkossev  - first version
  * ver. 1.0.1  2024-01-13 kkossev  - added 'Invert Motion' option
  *
  *                                   TODO:
  *
*/

import groovy.transform.Field

@Field static final String matterComponentMotionVersion = '1.0.1'
@Field static final String matterComponentMotionStamp   = '2024/01/13 6:16 PM'

metadata {
    definition(name: 'Matter Generic Component Motion Sensor', namespace: 'kkossev', author: 'Krassimir Kossev') {
        capability 'Sensor'
        capability 'MotionSensor'
        capability 'Refresh'
        capability 'Health Check'

        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']

        command 'setMotion', [[name: 'setMotion', type: 'ENUM', constraints: ['No selection', 'active', 'inactive'], description: 'Force motion active/inactive (for tests)']]
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

        input name: 'invertMotion',
              type: 'bool',
              title: 'Invert Motion',
              description: '<i>Some motion sensors (mmWave radars) report active when no motion detected. Enable this option to invert the motion state.</i>',
              required: false,
              defaultValue: false
    }
}

/* groovylint-disable-next-line UnusedMethodParameter */
void parse(String description) { log.warn 'parse(String description) not implemented' }

// parse commands from parent
void parse(List<Map> description) {
    if (logEnable) { log.debug "${device} ${description}" }
    description.each { d ->
        if (d.name == 'motion') {
            if (invertMotion) {
                if (d.value == 'active') {
                    d.value = 'inactive'
                    d.descriptionText = d.descriptionText.replace('active', 'inactive')
                }
                else {
                    d.value = 'active'
                    d.descriptionText = d.descriptionText.replace('inactive', 'active')
                }
            }
        }
        if (d.descriptionText && txtEnable) { log.info "${d.descriptionText}" }
        sendEvent(d)
    }
}

// Called when the device is first created
void installed() {
    log.info "${device} driver installed"
}

// for tests
void setMotion( String mode ) {
    switch (mode) {
        case 'active' :
            sendEvent([name:'motion', value:'active', type: 'digital', descriptionText: 'motion set to active', isStateChange:true])
            if (settings?.txtEnable) { log.info "${device.displayName} motion set to active" }
            break
        case 'inactive' :
            sendEvent([name:'motion', value:'inactive', type: 'digital', descriptionText: 'motion set to inactive', isStateChange:true])
            if (settings?.txtEnable) { log.info "${device.displayName} motion set to inactive" }
            break
        default :
            if (settings?.logEnable) { log.warn "${device.displayName} please select motion action" }
            break
    }
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
        runIn(86400, 'logsOff')
    }
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void logsOff() {
    log.warn "debug logging disabled for ${device}"
    device.updateSetting('logEnable', [value: 'false', type: 'bool'] )
}

void refresh() {
    parent?.componentRefresh(this.device)
}

void ping() {
    refresh()
}
