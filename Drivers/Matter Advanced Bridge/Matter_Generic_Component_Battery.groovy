/* groovylint-disable-next-line CompileStatic */
/*
  *  'Matter Generic Component Battery' - component driver for Matter Advanced Bridge
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
  * ver. 0.0.0  2024-02-02 kkossev  - first version
  * ver. 0.0.1  2024-03-03 kkossev  - disabled healthStatus
  * ver. 0.0.2  2024-03-03 kkossev  - (dev. branch) componentBatteryVersion, componentBatteryStamp bug fix; added importUrl
  * ver. 0.0.3  2024-03-09 kkossev  - (dev. branch) batQuantity typo bug fix;
  * ver. 0.0.4  2024-03-11 kkossev  - (dev. branch) battery attributes corrected;
  *
  *                                   TODO:
  *
*/

import groovy.transform.Field

@Field static final String matterComponentBatteryVersion = '0.0.4'
@Field static final String matterComponentBatteryStamp   = '2024/03/11 9:45 PM'

metadata {
    definition(name: 'Matter Generic Component Battery', namespace: 'kkossev', author: 'Krassimir Kossev', importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Matter%20Advanced%20Bridge/Matter_Generic_Component_Battery.groovy') {
        capability 'Sensor'
        capability 'Battery'
        capability 'Refresh'
        //capability 'Health Check'
        attribute  'batteryVoltage', 'number'
        attribute  'batStatus', 'string'
        attribute  'batOrder', 'string'
        attribute  'batDescription', 'string'
        attribute  'batTimeRemaining', 'string'
        attribute  'batChargeLevel', 'string'
        attribute  'batReplacementNeeded', 'string'
        attribute  'batReplaceability', 'string'
        attribute  'batReplacementDescription', 'string'
        attribute  'batQuantity', 'string'

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
    if (logEnable) { log.debug "${device.displayName} ${description}" }
    description.each { d ->
        if (d.descriptionText && txtEnable) { log.info "${d.descriptionText}" }
        sendEvent(d)
    }
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
    log.warn "debug logging disabled for ${device.displayName}"
    device.updateSetting('logEnable', [value: 'false', type: 'bool'] )
}

void refresh() {
    parent?.componentRefresh(this.device)
}

static void ping() {
    refresh()
}
