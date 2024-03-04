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
  * ver. 0.0.0  2024-01-10 kkossev  - first version
  * ver. 0.0.1  2024-01-13 kkossev  - added 'Invert Motion' option
  * ver. 0.0.2  2024-01-25 kkossev  - 'Invert Motion' change updates the motion state immediateltely.
  * ver. 0.0.3  2024-03-04 kkossev  - added importUrl; disabled healthStatus
  *
  *                                   TODO:
  *
*/

import groovy.transform.Field

@Field static final String matterComponentMotionVersion = '0.0.3'
@Field static final String matterComponentMotionStamp   = '2024/03/04 8:49 AM'

metadata {
    definition(name: 'Matter Generic Component Motion Sensor', namespace: 'kkossev', author: 'Krassimir Kossev', importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Matter%20Advanced%20Bridge/Matter_Generic_Component_Motion_Sensor.groovy') {
        capability 'Sensor'
        capability 'MotionSensor'
        capability 'Refresh'
        //capability 'Health Check'

        //attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']

        command 'setMotion', [[name: 'setMotion', type: 'ENUM', constraints: ['No selection', 'active', 'inactive'], description: 'Force motion active/inactive (for tests)']]
    }
}

preferences {
    section {
        input name: 'txtEnable', type: 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
        input name: 'logEnable', type: 'bool', title: 'Enable debug logging', required: false, defaultValue: false
        input name: 'invertMotion', type: 'bool', title: 'Invert Motion', description: '<i>Some motion sensors (mmWave radars) report active when no motion detected. Enable this option to invert the motion state.</i>', required: false, defaultValue: false
    }
}

/* groovylint-disable-next-line UnusedMethodParameter */
void parse(String description) { log.warn 'parse(String description) not implemented' }

// parse commands from parent
void parse(List<Map> description) {
    if (logEnable) { log.debug "${device.displayName} ${description}" }
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
    log.info "${device.displayName} driver installed"
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
    log.info "${device.displayName} driver uninstalled"
}

// Called when the settings are updated
void updated() {
    log.info "${device.displayName} driver configuration updated"
    if (logEnable) {
        log.debug settings
        runIn(86400, 'logsOff')
    }
    if ((state.invertMotion ?: false) != settings?.invertMotion) {
        state.invertMotion = settings?.invertMotion
        if (logEnable) { log.debug "${device.displayName} invertMotion: ${settings?.invertMotion}" }
        String motion = device.currentMotion == 'active' ? 'inactive' : 'active'
        sendEvent([name:'motion', value:motion, type: 'digital', descriptionText: "motion state inverted to ${motion}", isStateChange:true])
    }
    else {
        if (logEnable) { log.debug "${device.displayName} invertMotion: no change" }
    }
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void logsOff() {
    log.warn "debug logging disabled for ${device.displayName}"
    device.updateSetting('logEnable', [value: 'false', type: 'bool'] )
}

void refresh() {
    parent?.componentRefresh(this.device)
}

void ping() {
    refresh()
}
