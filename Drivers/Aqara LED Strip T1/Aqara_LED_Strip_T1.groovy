/* groovylint-disable LineLength */
/**
 *  Aqara LED Strip T1 - Device Driver for Hubitat Elevation
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
 * ver. 2.1.5  2023-11-06 kkossev  - (dev. branch) Aqara E1 thermostat; added deviceProfileLib; Aqara LED Strip T1 driver;
 * ver. 3.2.0  2024-05-21 kkossev  - (dev. branch) commonLib 3.2.0 allignment
 *
 *                                   TODO: 
 */

static String version() { "3.2.0" }
static String timeStamp() {"2024/05/21 9:40 PM"}

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput

deviceType = "Bulb"
@Field static final String DEVICE_TYPE = "Bulb"
#include kkossev.commonLib
#include kkossev.xiaomiLib
#include kkossev.levelLib
#include kkossev.ctLib
#include kkossev.rgbLib

metadata {
    definition (
        name: 'Aqara LED Strip T1',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%LED%20Strip%20T1/Aqara%20LED%20Strip%20T1.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {
        attribute 'deviceTemperature', 'number'
        attribute 'musicMode', 'enum', MusicModeOpts.options.values() as List<String>
        
        command 'musicMode', [[name:'Select Music Mode', type: 'ENUM',   constraints: ['--- select ---'] + MusicModeOpts.options.values() as List<String>]]
    }
    fingerprint profileId:'0104', endpointId:'01', inClusters:'0005,0004,0003,0000,0300,0008,0006,FCC0', outClusters:'0019,000A', model:'lumi.light.acn132', manufacturer:'Aqara'
    // https://github.com/dresden-elektronik/deconz-rest-plugin/issues/7200
    // https://github.com/dresden-elektronik/deconz-rest-plugin/blob/50555f9350dc1872f266ebe9a5b3620b76e99af6/devices/xiaomi/lumi_light_acn132.json#L4

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
    }
}

@Field static final Map MusicModeOpts = [            // preset
    defaultValue: 0,
    options     : [0: 'off', 1: 'on']
]

def musicMode(mode) {
    List<String> cmds = []
    if (mode in MusicModeOpts.options.values()) {
        logDebug "sending musicMode: ${mode}"
        if (mode == 'on') {
            cmds = zigbee.writeAttribute(0xFCC0, 0x051C, 0x20, 0x01, [mfgCode: 0x115F], delay = 200)
        }
        else if (mode == 'off') {
            cmds = zigbee.writeAttribute(0xFCC0, 0x051C, 0x20, 0x00, [mfgCode: 0x115F], delay = 200)
        }
    }
    else {
        logWarn "musicMode: invalid mode ${mode}"
        return
    }
    if (cmds == []) { cmds = ['delay 299'] }
    sendZigbeeCommands(cmds)
}

// called from parseXiaomiClusterLib in xiaomiLib.groovy (xiaomi cluster 0xFCC0 )
//
void parseXiaomiClusterRgbLib(final Map descMap) {
    //logWarn "parseXiaomiClusterRgbLib: received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
    final Integer raw
    final String  value
    switch (descMap.attrInt as Integer) {
        case 0x00EE:    // attr/swversion"
            raw = hexStrToUnsignedInt(descMap.value)        // val = '0.0.0_' + ('0000' + ((Attr.val & 0xFF00) >> 8).toString() + (Attr.val & 0xFF).toString()).slice(-4)"
            logInfo "Aqara Version is ${raw}"
            break
        case 0x00F7 :   // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
            parseXiaomiClusterRgbTags(tags)
            break
        case 0x0515:    // config/bri/min                   // r/w "dt": "0x20"
            raw = hexStrToUnsignedInt(descMap.value)        // .val = Math.round(Attr.val * 2.54)
            logInfo "Aqara min brightness is ${raw}"
            break
        case 0x0516:    // config/bri/max                   // r/w "dt": "0x20"
            raw = hexStrToUnsignedInt(descMap.value)
            logInfo "Aqara max brightness is ${raw}"
            break
        case 0x0517:    // config/on/startup               // r/w "dt": "0x20"
            raw = hexStrToUnsignedInt(descMap.value)       // val = [1, 255, 0][Attr.val]       // val === 1 ? 0 : Item.val === 0 ? 2 : 1"
            logInfo "Aqara on startup is ${raw}"
            break
        case 0x051B:    // config/color/gradient/pixel_count                  // r/w "dt": "0x20" , Math.max(5, Math.min(Item.val, 50))
            raw = hexStrToUnsignedInt(descMap.value)
            logInfo "Aqara pixel count is ${raw}"
            break
        case 0x051C:    // state/music_sync                 // r/w "dt": "0x20" , val = Attr.val === 1      // Item.val ? 1 : 0
            raw = hexStrToUnsignedInt(descMap.value)
            value = MusicModeOpts.options[raw as int]
            aqaraEvent('musicMode', value, raw)
            break
        case 0x0509:    // state/gradient                   // r/w "dt": "0x20" , val = Attr.val === 1      // Item.val ? 1 : 0
            raw = hexStrToUnsignedInt(descMap.value)
            logInfo "Aqara gradient is ${raw}"
            break
        case 0x051F:    // state/gradient/flow              // r/w "dt": "0x20" , val = Attr.val === 1      // Item.val ? 1 : 0
            raw = hexStrToUnsignedInt(descMap.value)
            logInfo "Aqara gradient flow is ${raw}"
            break
        case 0x051D:    // state/gradient/flow/speed        // r/w "dt": "0x20" , val = Math.max(1, Math.min(Item.val, 10))
            raw = hexStrToUnsignedInt(descMap.value)
            logInfo "Aqara gradient flow speed is ${raw}"
            break
        default:
            logWarn "parseXiaomiClusterRgbLib: received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
}

void aqaraEvent(eventName, value, raw) {
    sendEvent(name: eventName, value: value, type: 'physical')
    logInfo "${eventName} is ${value} (raw ${raw})"
}

//
// called from parseXiaomiClusterRgbLib
//
void parseXiaomiClusterRgbTags(final Map<Integer, Object> tags) {       // TODO: check https://github.com/sprut/Hub/issues/2420
    tags.each { final Integer tag, final Object value ->
        switch (tag) {
            case 0x01:    // battery voltage
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})"
                break
            case 0x03:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device internal chip temperature is ${value}&deg;"
                sendEvent(name: 'deviceTemperature', value: value, unit: 'C')
                break
            case 0x05:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}"
                break
            case 0x06:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}"
                break
            case 0x08:            // SWBUILD_TAG_ID:
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0')
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})"
                device.updateDataValue('aqaraVersion', swBuild)
                break
            case 0x0a:
                String nwk = intToHexStr(value as Integer, 2)
                if (state.health == null) { state.health = [:] }
                String oldNWK = state.health['parentNWK'] ?: 'n/a'
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>"
                if (oldNWK != nwk ) {
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}"
                    state.health['parentNWK']  = nwk
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1
                }
                break
            default:
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
        }
    }
}

void customInitEvents(boolean fullInit=false) {
    logDebug "customInitEvents: ${fullInit}"
    if (this.respondsTo('initEventsBulb')) {
        initEventsBulb(fullInit)
    }
}

void customInitializeVars(boolean fullInit=false) {
    logDebug "customInitializeVars: ${fullInit}"
    if (this.respondsTo('initVarsRGB')) {
        initVarsRGB(fullInit)
    }
}

List<String> customConfigureDevice() {
    logDebug "customConfigureDevice: "
    List<String> cmds = []
    if (this.respondsTo('configureRGB')) {
        cmds += configureRGB()
    }
    return cmds
}

List<String> customRefresh() {
    logDebug "customRefresh: ${device.displayName}"
    List<String> cmds = []
    if (this.respondsTo('refreshLevel')) {
        cmds += refreshLevel()
    }
    if (this.respondsTo('refreshRGB')) {
        cmds += refreshRGB()
    }
    return cmds
}

void customUpdated() {
    logDebug "customUpdated: ${device.displayName}"
    if (this.respondsTo('updatedLevel')) {
        updatedLevel()
    }
    if (this.respondsTo('updatedRGB')) {
        updatedRGB()
    }
}

def test(par) {
    ArrayList<String> cmds = []
    log.warn "test... ${par}"

    parse(par)

   // sendZigbeeCommands(cmds)
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
