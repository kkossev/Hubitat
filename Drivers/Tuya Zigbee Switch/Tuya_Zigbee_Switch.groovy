/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, NglParseError, ParameterCount, UnnecessaryGetter, UnusedImport */
/**
 *  Tuya Zigbee Switch - Device Driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *     in compliance with the License. You may obtain a copy of the License at:
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *     on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *     for the specific language governing permissions and limitations under the License.
 *
 * This driver is inspired by @w35l3y work on Tuya device driver (Edge project).
 * For a big portions of code all credits go to Jonathan Bradshaw.
 *
 * ver. 2.0.4  2023-06-29 kkossev  - Tuya Zigbee Switch;
 * ver. 2.1.2  2023-07-23 kkossev  - Switch library;
 * ver. 2.1.3  2023-08-12 kkossev  - ping() improvements; added ping OK, Fail, Min, Max, rolling average counters; added clearStatistics(); added updateTuyaVersion() updateAqaraVersion(); added HE hub model and platform version;
 * ver. 3.0.0  2023-11-24 kkossev  - (dev. branch) use commonLib; added AlwaysOn option; added ignore duplcated on/off events option;
 * ver. 3.0.1  2023-11-25 kkossev  - (dev. branch) added LEDVANCE Plug 03; added TS0101 _TZ3000_pnzfdr9y SilverCrest Outdoor Plug Model HG06619 manufactured by Lidl; added configuration for 0x0006 cluster reproting for all devices;
 * ver. 3.0.2  2023-12-12 kkossev  - (dev. branch) added ZBMINIL2
 * ver. 3.0.3  2024-02-24 kkossev  - (dev. branch) commonLib 3.0.3 allignment
 * ver. 3.0.7  2024-04-18 kkossev  - (dev. branch) commonLib 3.0.7 and groupsLib allignment
 * ver. 3.1.1  2024-05-15 kkossev  - added SONOFF ZBMicro; commonLib 3.1.1 allignment; Groovy linting;
 * ver. 3.2.0  2024-05-25 kkossev  - (dev. branch) commonLib 3.2.0 allignment
 *
 *                                   TODO: Sonof ZBMINIL2 :zigbee read BASIC_CLUSTER attribute 0x0001 error: Unsupported Attribute
 *                                   TODO: add toggle() command; initialize 'switch' to unknown
 *                                   TODO: add power-on behavior option
 *                                   TODO: add 'allStatus' attribute
 *                                   TODO: add Info dummy preference w/ link to Hubitat forum page
 */

static String version() { '3.2.0' }
static String timeStamp() { '2024/05/25 7:31 AM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput

deviceType = 'Switch'
@Field static final String DEVICE_TYPE = 'Switch'

#include kkossev.commonLib
#include kkossev.onOffLib
#include kkossev.reportingLib
#include kkossev.groupsLib

metadata {
    definition(
        name: 'Tuya Zigbee Switch',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Switch/Tuya_Zigbee_Switch_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        // all the capabilities are already declared in the libraries

        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0006,0007,0B05,FC57', outClusters:'0019', model:'ZBMINIL2', manufacturer:'SONOFF', deviceJoinName: 'SONOFF ZBMINIL2'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,0008,1000,FC7C', outClusters:'0005,0019,0020,1000', model:'TRADFRI control outlet', manufacturer:'IKEA of Sweden', deviceJoinName: 'TRADFRI control outlet'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,0008,1000,FC7C', outClusters:'0019,0020,1000', model:'TRADFRI control outlet', manufacturer:'IKEA of Sweden', deviceJoinName: 'TRADFRI control outlet'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006', outClusters:'0019,000A', model:'TS0101', manufacturer:'_TZ3000_pnzfdr9y', deviceJoinName: 'SONOFF ZBMINIL2'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0006,0B05,FC0F', outClusters:'0019', model:'Plug Z3', manufacturer:'LEDVANCE', deviceJoinName: 'Plug Z3'
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0006,FC57,FC11", outClusters:"0003,0019", model:"ZBMicro", manufacturer:"SONOFF", deviceJoinName: "SONOFF ZBMicro"
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
    }
}


boolean isZBMINIL2()   { return (device?.getDataValue('model') ?: 'n/a') in ['ZBMINIL2'] }

List<String> customRefresh() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200)
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership
    logDebug "customRefresh() : ${cmds}"
    return cmds
}

void customInitVars(boolean fullInit=false) {
    logDebug "customInitVars(${fullInit})"
    if (fullInit || settings?.threeStateEnable == null) { device.updateSetting('threeStateEnable', false) }
    if (fullInit || settings?.ignoreDuplicated == null) { device.updateSetting('ignoreDuplicated', false) }
}

/* groovylint-disable-next-line EmptyMethod, UnusedMethodParameter */
void customInitEvents(boolean fullInit=false) {
}

List<String> customConfigureDevice() {
    List<String> cmds = []
    if (isZBMINIL2()) {
        logDebug 'customConfigureDevice() : unbind ZBMINIL2 poll control cluster'
        // Unbind genPollCtrl (0x0020) to prevent device from sending checkin message.
        // Zigbee-herdsmans responds to the checkin message which causes the device to poll slower.
        // https://github.com/Koenkk/zigbee2mqtt/issues/11676
        // https://github.com/Koenkk/zigbee2mqtt/issues/10282
        // https://github.com/zigpy/zha-device-handlers/issues/1519
        cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",]
    }
/*
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0000 {${device.zigbeeId}} {}", "delay 251", ]
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}", "delay 251", ]
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}", "delay 251", ]

    cmds += zigbee.readAttribute(0xFCC0, 0x0009, [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, 0x0148, [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, 0x0149, [mfgCode: 0x115F], delay=200)
*/
    cmds += configureReporting('Write', ONOFF,  '1', '65534', '0', sendNow = false)    // switch state should be always reported
    logDebug "customConfigureDevice() : ${cmds}"
    return cmds
}

void customParseElectricalMeasureCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    int value = hexStrToUnsignedInt(descMap.value)
    logDebug "customParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
}

void customParseMeteringCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    int value = hexStrToUnsignedInt(descMap.value)
    logDebug "customParseMeteringCluster: (0x0702)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
