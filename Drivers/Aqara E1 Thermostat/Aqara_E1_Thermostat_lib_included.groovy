/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateStringLiteral, ImplicitClosureParameter, MethodCount, MethodSize, NglParseError, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessarySetter, UnusedImport */
/**
 *  Aqara E1 Thermostat - Device Driver for Hubitat Elevation
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
 * ver. 2.0.2  2023-05-29 kkossev  - Aqara E1 thermostat driver initial version
 * ver. 2.1.6  2023-11-06 kkossev  - Aqara E1 thermostat improvements;
 * ver. 3.3.0  2024-06-08 kkossev  - new driver for Aqara E1 thermostat using thermostatLib
 * ver. 3.4.0  2024-10-05 kkossev  - added to HPM
 * ver. 3.4.1  2025-03-04 kkossev  - disabled 'cool' mode for the Aqara E1 thermostat
 * ver. 3.5.0  2025-04-08 kkossev  - urgent fix for java.lang.CloneNotSupportedException
 * ver. 3.5.2  2025-05-25 kkossev  - HE platfrom version 2.4.1.x decimal preferences patch/workaround.
 * ver. 3.6.0  2025-09-15 kkossev  - (dev. branch) commonLib 4.0.0 allignment;
 *
 *                                   TODO: 
 */

static String version() { '3.6.0' }
static String timeStamp() { '2025/09/15 8:11 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput
import java.math.RoundingMode









deviceType = 'Thermostat'
@Field static final String DEVICE_TYPE = 'Thermostat'

metadata {
    definition(
        name: 'Aqara E1 Thermostat',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20E1%20Thermostat/Aqara_E1_Thermostat_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        // capbilities are defined in the thermostatLib
        // TODO - add all other models attributes possible values
        attribute 'antiFreeze', 'enum', ['off', 'on']               // Tuya Saswell, AVATTO
        attribute 'batteryVoltage', 'number'
        attribute 'boostTime', 'number'                             // BRT-100
        attribute 'calibrated', 'enum', ['false', 'true']           // Aqara E1
        attribute 'calibrationTemp', 'number'                       // BRT-100, Sonoff
        attribute 'childLock', 'enum', ['off', 'on']                // BRT-100, Aqara E1, Sonoff, AVATTO
        attribute 'ecoMode', 'enum', ['off', 'on']                  // BRT-100
        attribute 'ecoTemp', 'number'                               // BRT-100
        attribute 'emergencyHeating', 'enum', ['off', 'on']         // BRT-100
        attribute 'emergencyHeatingTime', 'number'                  // BRT-100
        attribute 'floorTemperature', 'number'                      // AVATTO/MOES floor thermostats
        attribute 'frostProtectionTemperature', 'number'            // Sonoff
        attribute 'hysteresis', 'number'                            // AVATTO, Virtual thermostat
        attribute 'level', 'number'                                 // BRT-100
        attribute 'maxHeatingSetpoint', 'number'                    // BRT-100, Sonoff, AVATTO
        attribute 'minHeatingSetpoint', 'number'                    // BRT-100, Sonoff, AVATTO
        attribute 'sensor', 'enum', ['internal', 'external', 'both']         // Aqara E1, AVATTO
        attribute 'systemMode', 'enum', ['off', 'on']               // Aqara E1, AVATTO
        attribute 'valveAlarm', 'enum',  ['false', 'true']          // Aqara E1
        attribute 'valveDetection', 'enum', ['off', 'on']           // Aqara E1
        attribute 'weeklyProgram', 'number'                         // BRT-100
        attribute 'windowOpenDetection', 'enum', ['off', 'on']      // BRT-100, Aqara E1, Sonoff
        attribute 'windowsState', 'enum', ['open', 'closed']        // BRT-100, Aqara E1
        attribute 'batteryLowAlarm', 'enum', ['batteryOK', 'batteryLow']        // TUYA_SASWELL
        //attribute 'workingState', "enum", ["open", "closed"]        // BRT-100

        // Aqaura E1 attributes     TODO - consolidate a common set of attributes
        attribute 'preset', 'enum', ['manual', 'auto', 'away']      // TODO - remove?
        attribute 'awayPresetTemperature', 'number'

        if (_DEBUG) { command 'testT', [[name: 'testT', type: 'STRING', description: 'testT', defaultValue : '']]  }

        // itterate through all the figerprints and add them on the fly
        deviceProfilesV3.each { profileName, profileMap ->
            if (profileMap.fingerprints != null) {
                profileMap.fingerprints.each {
                    fingerprint it
                }
            }
        }
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: 'Enables command logging.'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.'
        // the rest of the preferences are inputed from the deviceProfile maps in the deviceProfileLib
    }
}

@Field static final Map deviceProfilesV3 = [
    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/6339b6034de34f8a633e4f753dc6e506ac9b001c/src/devices/xiaomi.ts#L3197
    // https://github.com/Smanar/deconz-rest-plugin/blob/6efd103c1a43eb300a19bf3bf3745742239e9fee/devices/xiaomi/xiaomi_lumi.airrtc.agl001.json
    // https://github.com/dresden-elektronik/deconz-rest-plugin/issues/6351
    'AQARA_E1_TRV'   : [
            description   : 'Aqara E1 Thermostat model SRTS-A01',
            device        : [manufacturers: ['LUMI'], type: 'TRV', powerSource: 'battery', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode': true],

            preferences   : ['windowOpenDetection':'0xFCC0:0x0273', 'valveDetection':'0xFCC0:0x0274', 'childLock':'0xFCC0:0x0277', 'awayPresetTemperature':'0xFCC0:0x0279'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,FCC0,000A,0201', outClusters:'0003,FCC0,0201', model:'lumi.airrtc.agl001', manufacturer:'LUMI', deviceJoinName: 'Aqara E1 Thermostat']
            ],
            commands      : ['sendSupportedThermostatModes':'sendSupportedThermostatModes', 'autoPollThermostat':'autoPollThermostat', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [:],
            attributes    : [
                [at:'0xFCC0:0x040A',  name:'battery',               type:'number',  dt:'0x20', mfgCode:'0x115f',  rw: 'ro', min:0,    max:100,  step:1,  scale:1,    unit:'%',  description:'Battery percentage remaining'],
                [at:'0xFCC0:0x0270',  name:'unknown1',              type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',   title: '<b>Unknown 0x0270</b>',   description:'Unknown 0x0270'],
                [at:'0xFCC0:0x0271',  name:'systemMode',            type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'',     title: '<b>System Mode</b>',      description:'Switch the TRV OFF or in operation (on)'],             // result['system_mode'] = {1: 'heat', 0: 'off'}[value]; (heating state) - rw
                [at:'0xFCC0:0x0272',  name:'thermostatMode',        type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,    max:2,    step:1,  scale:1,    map:[0: 'heat', 1: 'auto', 2: 'away'], unit:'',                   title: '<b>Preset</b>',           description:'Preset'],                  // result['preset'] = {2: 'away', 1: 'auto', 0: 'manual'}[value]; - rw  ['manual', 'auto', 'holiday']
                [at:'0xFCC0:0x0273',  name:'windowOpenDetection',   type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,    max:1,    defVal:'0',    step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'',             title: '<b>Window Detection</b>', description:'Window detection'],       // result['window_detection'] = {1: 'ON', 0: 'OFF'}[value]; - rw
                [at:'0xFCC0:0x0274',  name:'valveDetection',        type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,    max:1,    defVal:'0',    step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'',             title: '<b>Valve Detection</b>',  description:'Valve detection'],        // result['valve_detection'] = {1: 'ON', 0: 'OFF'}[value]; -rw
                [at:'0xFCC0:0x0275',  name:'valveAlarm',            type:'enum',    dt:'0x23', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    defVal:'0',    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',         title: '<b>Valve Alarm</b>',      description:'Valve alarm'],            // result['valve_alarm'] = {1: true, 0: false}[value]; - read only!
                [at:'0xFCC0:0x0276',  name:'unknown2',              type:'enum',    dt:'0x41', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',         title: '<b>Unknown 0x0270</b>',                        description:'Unknown 0x0270'],
                [at:'0xFCC0:0x0277',  name:'childLock',             type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,    max:1,    defVal:'0',    step:1,  scale:1,    map:[0: 'unlock', 1: 'lock'], unit:'',        title: '<b>Child Lock</b>',       description:'Child lock'],             // result['child_lock'] = {1: 'LOCK', 0: 'UNLOCK'}[value]; - rw
                [at:'0xFCC0:0x0278',  name:'unknown3',              type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ow', min:0,    max:1,    defVal:'0',    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',         title: '<b>Unknown 3</b>',        description:'Unknown 3'],              // WRITE ONLY !
                [at:'0xFCC0:0x0279',  name:'awayPresetTemperature', type:'decimal', dt:'0x23', mfgCode:'0x115f',  rw: 'rw', min:5.0,  max:35.0, defVal:5.0,    step:0.5, scale:100,  unit:'°C', title: '<b>Away Preset Temperature</b>',       description:'Away preset temperature'],                     // result['away_preset_temperature'] = (value / 100).toFixed(1); - rw
                [at:'0xFCC0:0x027A',  name:'windowsState',          type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    defVal:'0',    step:1,  scale:1,    map:[0: 'open', 1: 'closed'], unit:'',        title: '<b>Window Open</b>',      description:'Window open'],            // result['window_open'] = {1: true, 0: false}[value]; - read only
                [at:'0xFCC0:0x027B',  name:'calibrated',            type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    defVal:'0',    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',         title: '<b>Calibrated</b>',       description:'Calibrated'],             // result['calibrated'] = {1: true, 0: false}[value]; - read only
                [at:'0xFCC0:0x027C',  name:'unknown4',              type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    defVal:'0',    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',         title: '<b>Unknown 4</b>',        description:'Unknown 4'],
                [at:'0xFCC0:0x027D',  name:'schedule',              type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    defVal:'0',    step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'',             title: '<b>Schedule</b>',        description:'Schedule'],
                [at:'0xFCC0:0x027E',  name:'sensor',                type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    defVal:'0',    step:1,  scale:1,    map:[0: 'internal', 1: 'external'], unit:'',  title: '<b>Sensor</b>',           description:'Sensor'],                 // result['sensor'] = {1: 'EXTERNAL', 0: 'INTERNAL'}[value]; - read only
                //   0xFCC0:0x027F ... 0xFCC0:0x0284 - unknown
                [at:'0x0201:0x0000',  name:'temperature',           type:'decimal', dt:'0x29', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Temperature</b>',                   description:'Measured temperature'],
                [at:'0x0201:0x0011',  name:'coolingSetpoint',       type:'decimal', dt:'0x29', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Cooling Setpoint</b>',              description:'cooling setpoint'],
                [at:'0x0201:0x0012',  name:'heatingSetpoint',       type:'decimal', dt:'0x29', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
                [at:'0x0201:0x001B',  name:'thermostatOperatingState', type:'enum',    dt:'0x30', rw:'rw',  min:0,    max:4,    step:1,  scale:1,    map:[0: 'off', 1: 'heating', 2: 'unknown', 3: 'unknown3', 4: 'idle'], unit:'',  description:'thermostatOperatingState (relay on/off status)'],      //  nothing happens when WRITING ????
                //                      ^^^^                                reporting only ?
                [at:'0x0201:0x001C',  name:'mode',                  type:'enum',    dt:'0x30', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b> Mode</b>',                   description:'System Mode ?'],
                //                      ^^^^ TODO - check if this is the same as system_mode
                [at:'0x0201:0x001E',  name:'thermostatRunMode',     type:'enum',    dt:'0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatRunMode</b>',                   description:'thermostatRunMode'],
                //                          ^^ unsupported attribute?  or reporting only ?
                [at:'0x0201:0x0020',  name:'battery2',              type:'number',  dt:'0x20', rw: 'ro', min:0,    max:100,  step:1,  scale:1,    unit:'%',  description:'Battery percentage remaining'],
                //                          ^^ unsupported attribute?  or reporting only ?
                [at:'0x0201:0x0023',  name:'thermostatHoldMode',    type:'enum',    dt:'0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatHoldMode</b>',                   description:'thermostatHoldMode'],
                //                          ^^ unsupported attribute?  or reporting only ?
                [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum', dt:'0x20', rw: 'ow', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heating'], unit:'',         title: '<b>thermostatOperatingState</b>',                   description:'thermostatOperatingState'],
                //                          ^^ unsupported attribute?  or reporting only ?   encoding - 0x29 ?? ^^
                [at:'0x0201:0xFFF2',  name:'unknown',                type:'number', dt:'0x21', rw: 'ro', min:0,    max:100,  step:1,  scale:1,    unit:'%',  description:'Battery percentage remaining'],
            //                          ^^ unsupported attribute?  or reporting only ?
            ],
            supportedThermostatModes: ['off', 'auto', 'heat', 'away'/*, "emergency heat"*/],
            refresh: ['refreshAqaraE1'],
            deviceJoinName: 'Aqara E1 Thermostat',
            configuration : [:]
    ]
]

// called from parseXiaomiClusterLib in xiaomiLib.groovy (xiaomi cluster 0xFCC0 )
//
void parseXiaomiClusterThermostatLib(final Map descMap) {
    logTrace "zigbee received Thermostat 0xFCC0 attribute 0x${descMap.attrId} (raw value = ${descMap.value})"
    if ((descMap.attrInt as Integer) == 0x00F7 ) {      // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
        final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
        parseXiaomiClusterThermostatTags(tags)
        return
    }
    Boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "parseXiaomiClusterThermostatLib: received unknown Thermostat cluster (0xFCC0) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

// XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
// called from parseXiaomiClusterThermostatLib
//
void parseXiaomiClusterThermostatTags(final Map<Integer, Object> tags) {
    tags.each { final Integer tag, final Object value ->
        switch (tag) {
            case 0x01:    // battery voltage
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})"
                break
            case 0x03:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device internal chip temperature is ${value}&deg; (ignore it!)"
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
            case 0x0d:
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0x11:
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0x64:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})" / Aqara TVOC
                break
            case 0x65:
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0x66:
                logDebug "xiaomi decode E1 thermostat temperature tag: 0x${intToHexStr(tag, 1)}=${value}"
                handleTemperatureEvent(value / 100.0)
                break
            case 0x67:
                logDebug "xiaomi decode E1 thermostat heatingSetpoint tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0x68:
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0x69:
                logDebug "xiaomi decode E1 thermostat battery tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0x6a:
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            default:
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
        }
    }
}

/*
 * -----------------------------------------------------------------------------
 * thermostat cluster 0x0201
 * called from parseThermostatCluster() in the main code ...
 * -----------------------------------------------------------------------------
*/
void customParseThermostatCluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseThermostatCluster: zigbee received Thermostat cluster (0x0201) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return }
    boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "parseThermostatClusterThermostat: received unknown Thermostat cluster (0x0201) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

// TODO - configure in the deviceProfile
List pollAqara() {
    return  zigbee.readAttribute(0x0201, [0x0000, 0x0012, 0x001B, 0x001C], [:], delay = 3500)      // 0x0000 = local temperature, 0x0012 = heating setpoint, 0x001B = controlledSequenceOfOperation, 0x001C = system mode (enum8 )
}

//
// called from updated() in the main code
void customUpdated() {
    //ArrayList<String> cmds = []
    logDebug 'customUpdated: ...'
    //
    if (settings?.forcedProfile != null) {
        //logDebug "current state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${getProfileKey(settings?.forcedProfile)}"
        if (getProfileKey(settings?.forcedProfile) != state.deviceProfile) {
            logWarn "changing the device profile from ${state.deviceProfile} to ${getProfileKey(settings?.forcedProfile)}"
            state.deviceProfile = getProfileKey(settings?.forcedProfile)
            //initializeVars(fullInit = false)
            customInitializeVars(fullInit = false)
            resetPreferencesToDefaults(debug = true)
            logInfo 'press F5 to refresh the page'
        }
    }
    else {
        logDebug 'forcedProfile is not set'
    }
    final int pollingInterval = (settings.temperaturePollingInterval as Integer) ?: 0
    if (pollingInterval > 0) {
        logInfo "updatedThermostat: scheduling temperature polling every ${pollingInterval} seconds"
        scheduleThermostatPolling(pollingInterval)
    }
    else {
        unScheduleThermostatPolling()
        logInfo 'updatedThermostat: thermostat polling is disabled!'
    }
    // Itterates through all settings
    logDebug 'updatedThermostat: updateAllPreferences()...'
    updateAllPreferences()
}

// binding and reporting configuration for this Aqara E1 thermostat does nothing... We need polling mechanism for faster updates of the internal temperature readings.
List<String> refreshAqaraE1() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0011, 0x0012, 0x001B, 0x001C], [:], delay = 3500)       // 0x0000 = local temperature, 0x0011 = cooling setpoint, 0x0012 = heating setpoint, 0x001B = controlledSequenceOfOperation, 0x001C = system mode (enum8 )
    cmds += zigbee.readAttribute(0xFCC0, [0x0271, 0x0272, 0x0273, 0x0274, 0x0275, 0x0277, 0x0279, 0x027A, 0x027B, 0x027E], [mfgCode: 0x115F], delay = 3500)
    cmds += zigbee.readAttribute(0xFCC0, 0x040a, [mfgCode: 0x115F], delay = 500)
    return cmds
}

List<String> customRefresh() {
    List<String> cmds = refreshFromDeviceProfileList()
    logDebug "customRefresh: ${cmds} "
    return cmds
}

List<String> customConfigure() {
    List<String> cmds = []
    logDebug "customConfigure() : ${cmds} (not implemented!)"
    return cmds
}

List<String> initializeAqara() {
    List<String> cmds = []
    logDebug 'configuring cluster 0x0201 ...'
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0201 {${device.zigbeeId}} {}", 'delay 251', ]
    //cmds += zigbee.configureReporting(0x0201, 0x0012, 0x29, intMinTime as int, intMaxTime as int, 0x01, [:], delay=541)
    //cmds += zigbee.configureReporting(0x0201, 0x0000, 0x29, 20, 120, 0x01, [:], delay=542)

    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0012 0x29 1 600 {}", 'delay 551', ]
    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0000 0x29 20 300 {}", 'delay 551', ]
    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x001C 0x30 1 600 {}", 'delay 551', ]

    cmds +=  zigbee.reportingConfiguration(0x0201, 0x0012, [:], 551)    // read it back - doesn't work
    cmds +=  zigbee.reportingConfiguration(0x0201, 0x0000, [:], 552)    // read it back - doesn't work
    cmds +=  zigbee.reportingConfiguration(0x0201, 0x001C, [:], 552)    // read it back - doesn't work
    return cmds
}

// called from initializeDevice in the commonLib code
List<String> customInitializeDevice() {
    List<String> cmds = []
    logDebug 'customInitializeDevice() ...'
    cmds = initializeAqara()
    logDebug "initializeThermostat() : ${cmds}"
    return cmds
}

void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (state.deviceProfile == null) {
        setDeviceNameAndProfile()               // in deviceProfileiLib.groovy
    }
    thermostatInitializeVars(fullInit)
    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
}

// called from initializeVars() in the main code ...
void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents(${fullInit})"
    thermostatInitEvents(fullInit)
}

List<String> customAqaraBlackMagic() {
    List<String> cmds = []
    if (isAqaraTRV_OLD()) {
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',]
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}"
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage
        logDebug 'customAqaraBlackMagic()'
    }
    return cmds
}

// called from processFoundItem  (processTuyaDPfromDeviceProfile and ) processClusterAttributeFromDeviceProfile in deviceProfileLib when a Zigbee message was found defined in the device profile map
//
// (works for BRT-100, Sonoff TRVZV)
//
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
void customProcessDeviceProfileEvent(final Map descMap, final String name, final valueScaled, final String unitText, final String descText) {
    logTrace "customProcessDeviceProfileEvent(${name}, ${valueScaled}) called"
    Map eventMap = [name: name, value: valueScaled, unit: unitText, descriptionText: descText, type: 'physical', isStateChange: true]
    switch (name) {
        case 'temperature' :
            handleTemperatureEvent(valueScaled as Float)
            break
        case 'heatingSetpoint' :
            sendHeatingSetpointEvent(valueScaled)
            break
        case 'systemMode' : // Aqara E1 and AVATTO thermostat (off/on)
            sendEvent(eventMap)
            logInfo "${descText}"
            if (valueScaled == 'on') {  // should be initialized with 'unknown' value
                String lastThermostatMode = state.lastThermostatMode
                sendEvent(name: 'thermostatMode', value: lastThermostatMode, isStateChange: true, description: 'TRV systemMode is on', type: 'digital')
            }
            else {
                sendEvent(name: 'thermostatMode', value: 'off', isStateChange: true, description: 'TRV systemMode is off', type: 'digital')
            }
            break
        case 'thermostatMode' :  // AVATTO send the thermostat mode a second after being switched off - ignore it !
            if (device.currentValue('systemMode') == 'off' ) {
                logWarn "customProcessDeviceProfileEvent: ignoring the thermostatMode <b>${valueScaled}</b> event, because the systemMode is off"
            }
            else {
                sendEvent(eventMap)
                logInfo "${descText}"
                state.lastThermostatMode = valueScaled
            }
            break
        case 'ecoMode' :    // BRT-100 - simulate OFF mode ?? or keep the ecoMode on ?
            sendEvent(eventMap)
            logInfo "${descText}"
            if (valueScaled == 'on') {  // ecoMode is on
                sendEvent(name: 'thermostatMode', value: 'eco', isStateChange: true, description: 'BRT-100 ecoMode is on', type: 'digital')
                sendEvent(name: 'thermostatOperatingState', value: 'idle', isStateChange: true, description: 'BRT-100 ecoMode is on', type: 'digital')
                state.lastThermostatMode = 'eco'
            }
            else {
                sendEvent(name: 'thermostatMode', value: 'heat', isStateChange: true, description: 'BRT-100 ecoMode is off')
                state.lastThermostatMode = 'heat'
            }
            break

        case 'emergencyHeating' :   // BRT-100
            sendEvent(eventMap)
            logInfo "${descText}"
            if (valueScaled == 'on') {  // the valve shoud be completely open, however the level and the working states are NOT updated! :(
                sendEvent(name: 'thermostatMode', value: 'emergency heat', isStateChange: true, description: 'BRT-100 emergencyHeating is on')
                sendEvent(name: 'thermostatOperatingState', value: 'heating', isStateChange: true, description: 'BRT-100 emergencyHeating is on')
            }
            else {
                sendEvent(name: 'thermostatMode', value: 'heat', isStateChange: true, description: 'BRT-100 emergencyHeating is off')
            }
            break
        case 'level' :      // BRT-100
            sendEvent(eventMap)
            logInfo "${descText}"
            if (valueScaled == 0) {  // the valve is closed
                sendEvent(name: 'thermostatOperatingState', value: 'idle', isStateChange: true, description: 'BRT-100 valve is closed')
            }
            else {
                sendEvent(name: 'thermostatOperatingState', value: 'heating', isStateChange: true, description: 'BRT-100 valve is open %{valueScaled} %')
            }
            break
            /*
        case "workingState" :      // BRT-100   replaced with thermostatOperatingState
            sendEvent(eventMap)
            logInfo "${descText}"
            if (valueScaled == "closed") {  // the valve is closed
                sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true, description: "BRT-100 workingState is closed")
            }
            else {
                sendEvent(name: "thermostatOperatingState", value: "heating", isStateChange: true, description: "BRT-100 workingState is open")
            }
            break
            */
        default :
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event !
                //if (!doNotTrace) {
            logDebug "event ${name} sent w/ value ${valueScaled}"
            logInfo "${descText}"                                 // send an Info log also (because value changed )  // TODO - check whether Info log will be sent also for spammy DPs ?
            //}
            break
    }
}

void testT(String par) {
    log.trace "testT(${par}) : DEVICE.preferences = ${DEVICE.preferences}"
    log.trace "testT: ${settings}"
    Map result
    if (DEVICE != null && DEVICE.preferences != null && DEVICE.preferences != [:]) {
        (DEVICE.preferences).each { key, value ->
            log.trace "testT: ${key} = ${value}"
            result = inputIt(key, debug = true)
            logDebug "inputIt: ${result}"
        }
    }
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDouble, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/commonLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-commonLib', // library marker kkossev.commonLib, line 4
    version: '4.0.0' // library marker kkossev.commonLib, line 5
) // library marker kkossev.commonLib, line 6
/* // library marker kkossev.commonLib, line 7
  *  Common ZCL Library // library marker kkossev.commonLib, line 8
  * // library marker kkossev.commonLib, line 9
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.commonLib, line 10
  *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.commonLib, line 11
  * // library marker kkossev.commonLib, line 12
  *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.commonLib, line 13
  * // library marker kkossev.commonLib, line 14
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.commonLib, line 15
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.commonLib, line 16
  *  for the specific language governing permissions and limitations under the License. // library marker kkossev.commonLib, line 17
  * // library marker kkossev.commonLib, line 18
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project). // library marker kkossev.commonLib, line 19
  * For a big portions of code all credits go to Jonathan Bradshaw. // library marker kkossev.commonLib, line 20
  * // library marker kkossev.commonLib, line 21
  * // library marker kkossev.commonLib, line 22
  * ver. 1.0.0  2022-06-18 kkossev  - first beta version // library marker kkossev.commonLib, line 23
  * .............................. // library marker kkossev.commonLib, line 24
  * ver. 3.5.2  2025-08-13 kkossev  - Status attribute renamed to _status_ // library marker kkossev.commonLib, line 25
  * ver. 4.0.0  2025-09-08 kkossev  - deviceProfileV4 // library marker kkossev.commonLib, line 26
  * // library marker kkossev.commonLib, line 27
  *                                   TODO: change the offline threshold to 2  // library marker kkossev.commonLib, line 28
  *                                   TODO:  // library marker kkossev.commonLib, line 29
  *                                   TODO: add GetInfo (endpoints list) command (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 30
  *                                   TODO: make the configure() without parameter smart - analyze the State variables and call delete states.... call ActiveAndpoints() or/amd initialize() or/and configure() // library marker kkossev.commonLib, line 31
  *                                   TODO: check - offlineCtr is not increasing? (ZBMicro); // library marker kkossev.commonLib, line 32
  *                                   TODO: check deviceCommandTimeout() // library marker kkossev.commonLib, line 33
  *                                   TODO: when device rejoins the network, read the battery percentage again (probably in custom handler, not for all devices) // library marker kkossev.commonLib, line 34
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 35
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 36
  *                                   TODO: MOVE ZDO counters to health state? // library marker kkossev.commonLib, line 37
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 38
  *                                   TODO: Versions of the main module + included libraries (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 39
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 40
  * // library marker kkossev.commonLib, line 41
*/ // library marker kkossev.commonLib, line 42

String commonLibVersion() { '4.0.0' } // library marker kkossev.commonLib, line 44
String commonLibStamp() { '2025/09/15 12:44 PM' } // library marker kkossev.commonLib, line 45

import groovy.transform.Field // library marker kkossev.commonLib, line 47
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 48
import hubitat.device.Protocol // library marker kkossev.commonLib, line 49
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 50
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 51
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 52
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 53
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 54
import java.math.BigDecimal // library marker kkossev.commonLib, line 55

metadata { // library marker kkossev.commonLib, line 57
        if (_DEBUG) { // library marker kkossev.commonLib, line 58
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 59
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 60
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 61
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 62
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 63
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 64
            ] // library marker kkossev.commonLib, line 65
        } // library marker kkossev.commonLib, line 66

        // common capabilities for all device types // library marker kkossev.commonLib, line 68
        capability 'Configuration' // library marker kkossev.commonLib, line 69
        capability 'Refresh' // library marker kkossev.commonLib, line 70
        capability 'HealthCheck' // library marker kkossev.commonLib, line 71
        capability 'PowerSource'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 72

        // common attributes for all device types // library marker kkossev.commonLib, line 74
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 75
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 76
        attribute '_status_', 'string' // library marker kkossev.commonLib, line 77

        // common commands for all device types // library marker kkossev.commonLib, line 79
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM', constraints: ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 80

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 82
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 83

    preferences { // library marker kkossev.commonLib, line 85
        // txtEnable and logEnable moved to the custom driver settings - copy& paste there ... // library marker kkossev.commonLib, line 86
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 87
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 88

        if (device) { // library marker kkossev.commonLib, line 90
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'The advanced options should be already automatically set in an optimal way for your device...Click on the "Save and Close" button when toggling this option!', defaultValue: false // library marker kkossev.commonLib, line 91
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 92
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 93
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 94
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 95
            } // library marker kkossev.commonLib, line 96
        } // library marker kkossev.commonLib, line 97
    } // library marker kkossev.commonLib, line 98
} // library marker kkossev.commonLib, line 99

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 101
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 102
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 103
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 104
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 105
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 106
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 107
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 108
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 109
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 110
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 111

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 113
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 114
] // library marker kkossev.commonLib, line 115
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 116
    defaultValue: 240, options: [2: 'Every 2 Mins', 10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 117
] // library marker kkossev.commonLib, line 118

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 120
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'], // library marker kkossev.commonLib, line 121
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 122
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 123
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 124
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 125
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 126
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 127
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 128
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 129
    '           -             '  : [key:1, function: 'configureHelp'] // library marker kkossev.commonLib, line 130
] // library marker kkossev.commonLib, line 131

public boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 133

/** // library marker kkossev.commonLib, line 135
 * Parse Zigbee message // library marker kkossev.commonLib, line 136
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 137
 */ // library marker kkossev.commonLib, line 138
public void parse(final String description) { // library marker kkossev.commonLib, line 139

    Map stateCopy = state            // .clone() throws java.lang.CloneNotSupportedException in HE platform version 2.4.1.155 ! // library marker kkossev.commonLib, line 141
    checkDriverVersion(stateCopy)    // +1 ms // library marker kkossev.commonLib, line 142
    if (state.stats != null) { state.stats?.rxCtr= (state.stats?.rxCtr ?: 0) + 1 } else { state.stats = [:] }  // updateRxStats(state) // +1 ms // library marker kkossev.commonLib, line 143
    if (state.lastRx != null) { state.lastRx?.timeStamp = unix2formattedDate(now()) } else { state.lastRx = [:] } // library marker kkossev.commonLib, line 144
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 145
    setHealthStatusOnline(state)    // +2 ms // library marker kkossev.commonLib, line 146

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 148
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 149
        if (this.respondsTo('customParseIasMessage')) { customParseIasMessage(description) } // library marker kkossev.commonLib, line 150
        else if (this.respondsTo('standardParseIasMessage')) { standardParseIasMessage(description) } // library marker kkossev.commonLib, line 151
        else if (this.respondsTo('parseIasMessage')) { parseIasMessage(description) } // library marker kkossev.commonLib, line 152
        else { logDebug "ignored IAS zone status (no IAS parser) description: $description" } // library marker kkossev.commonLib, line 153
        return // library marker kkossev.commonLib, line 154
    } // library marker kkossev.commonLib, line 155
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 156
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 157
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 158
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 159
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 160
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 161
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 162
        return // library marker kkossev.commonLib, line 163
    } // library marker kkossev.commonLib, line 164

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 166
        return // library marker kkossev.commonLib, line 167
    } // library marker kkossev.commonLib, line 168
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 169

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 171
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 172

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 174
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 175
        return // library marker kkossev.commonLib, line 176
    } // library marker kkossev.commonLib, line 177
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 178
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 179
        return // library marker kkossev.commonLib, line 180
    } // library marker kkossev.commonLib, line 181
    // // library marker kkossev.commonLib, line 182
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 183
    // // library marker kkossev.commonLib, line 184
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 185
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 186
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 187
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 188
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 189
            } // library marker kkossev.commonLib, line 190
            break // library marker kkossev.commonLib, line 191
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 192
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 193
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 194
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 195
            } // library marker kkossev.commonLib, line 196
            break // library marker kkossev.commonLib, line 197
        default: // library marker kkossev.commonLib, line 198
            if (settings.logEnable) { // library marker kkossev.commonLib, line 199
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 200
            } // library marker kkossev.commonLib, line 201
            break // library marker kkossev.commonLib, line 202
    } // library marker kkossev.commonLib, line 203
} // library marker kkossev.commonLib, line 204

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 206
    0x0000: 'Basic',             0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x0006: 'OnOff',           0x0007:'onOffConfiguration',      0x0008: 'LevelControl',  // library marker kkossev.commonLib, line 207
    0x000C: 'AnalogInput',       0x0012: 'MultistateInput',  0x0020: 'PollControl',      0x0102: 'WindowCovering',   0x0201: 'Thermostat',  0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 208
    0x0400: 'Illuminance',       0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 209
    0x0B04: 'ElectricalMeasure', 0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC03: 'FC03',            0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 210
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 211
] // library marker kkossev.commonLib, line 212

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 214
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 215
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 216
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 217
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 218
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 219
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 220
        return false // library marker kkossev.commonLib, line 221
    } // library marker kkossev.commonLib, line 222
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 223
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 224
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 225
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 226
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 227
        return true // library marker kkossev.commonLib, line 228
    } // library marker kkossev.commonLib, line 229
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 230
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 231
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 232
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 233
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 234
        return true // library marker kkossev.commonLib, line 235
    } // library marker kkossev.commonLib, line 236
    if (device?.getDataValue('model') != 'ZigUSB' && descMap.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 237
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 238
    } // library marker kkossev.commonLib, line 239
    return false // library marker kkossev.commonLib, line 240
} // library marker kkossev.commonLib, line 241

// not used - throws exception :  error groovy.lang.MissingPropertyException: No such property: rxCtr for class: java.lang.String on line 1568 (method parse) // library marker kkossev.commonLib, line 243
private static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 244
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 245
} // library marker kkossev.commonLib, line 246

public boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 248
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 249
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 250
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 251
    } // library marker kkossev.commonLib, line 252
    return false // library marker kkossev.commonLib, line 253
} // library marker kkossev.commonLib, line 254

public boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 256
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 257
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 258
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 259
    } // library marker kkossev.commonLib, line 260
    return false // library marker kkossev.commonLib, line 261
} // library marker kkossev.commonLib, line 262

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 264
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 265
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 266
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 267
] // library marker kkossev.commonLib, line 268

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 270
private void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 271
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 272
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 273
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 274
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 275
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 276
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 277
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 278
    List<String> cmds = [] // library marker kkossev.commonLib, line 279
    switch (clusterId) { // library marker kkossev.commonLib, line 280
        case 0x0005 : // library marker kkossev.commonLib, line 281
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 282
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 283
            // send the active endpoint response // library marker kkossev.commonLib, line 284
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 285
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 286
            break // library marker kkossev.commonLib, line 287
        case 0x0006 : // library marker kkossev.commonLib, line 288
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 289
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 290
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 291
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 292
            break // library marker kkossev.commonLib, line 293
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 294
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 295
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 296
            break // library marker kkossev.commonLib, line 297
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 298
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 299
            if (this.respondsTo('parseSimpleDescriptorResponse')) { parseSimpleDescriptorResponse(descMap) } // library marker kkossev.commonLib, line 300
            break // library marker kkossev.commonLib, line 301
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 302
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 303
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 304
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 305
            break // library marker kkossev.commonLib, line 306
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 307
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 308
            break // library marker kkossev.commonLib, line 309
        case 0x0002 : // Node Descriptor Request // library marker kkossev.commonLib, line 310
        case 0x0036 : // Permit Joining Request // library marker kkossev.commonLib, line 311
        case 0x8022 : // unbind request // library marker kkossev.commonLib, line 312
        case 0x8034 : // leave response // library marker kkossev.commonLib, line 313
            if (settings?.logEnable) { log.debug "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 314
            break // library marker kkossev.commonLib, line 315
        default : // library marker kkossev.commonLib, line 316
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 317
            break // library marker kkossev.commonLib, line 318
    } // library marker kkossev.commonLib, line 319
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 320
} // library marker kkossev.commonLib, line 321

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 323
private void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 324
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 325
    switch (commandId) { // library marker kkossev.commonLib, line 326
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 327
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 328
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 329
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 330
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 331
        default: // library marker kkossev.commonLib, line 332
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 333
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 334
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 335
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 336
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 337
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 338
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 339
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 340
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 341
            } // library marker kkossev.commonLib, line 342
            break // library marker kkossev.commonLib, line 343
    } // library marker kkossev.commonLib, line 344
} // library marker kkossev.commonLib, line 345

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 347
private void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 348
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 349
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 350
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 351
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 352
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 353
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 354
    } // library marker kkossev.commonLib, line 355
    else { // library marker kkossev.commonLib, line 356
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 357
    } // library marker kkossev.commonLib, line 358
} // library marker kkossev.commonLib, line 359

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 361
private void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 362
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 363
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 364
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 365
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 366
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 367
    } // library marker kkossev.commonLib, line 368
    else { // library marker kkossev.commonLib, line 369
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 370
    } // library marker kkossev.commonLib, line 371
} // library marker kkossev.commonLib, line 372

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 374
private void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 375
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 376
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 377
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 378
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 379
        state.reportingEnabled = true // library marker kkossev.commonLib, line 380
    } // library marker kkossev.commonLib, line 381
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 382
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 383
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 384
    } else { // library marker kkossev.commonLib, line 385
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 386
    } // library marker kkossev.commonLib, line 387
} // library marker kkossev.commonLib, line 388

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 390
private void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 391
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 392
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 393
    if (status == 0) { // library marker kkossev.commonLib, line 394
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 395
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 396
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 397
        int delta = 0 // library marker kkossev.commonLib, line 398
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 399
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 400
        } // library marker kkossev.commonLib, line 401
        else { // library marker kkossev.commonLib, line 402
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 403
        } // library marker kkossev.commonLib, line 404
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 405
    } // library marker kkossev.commonLib, line 406
    else { // library marker kkossev.commonLib, line 407
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 408
    } // library marker kkossev.commonLib, line 409
} // library marker kkossev.commonLib, line 410

private Boolean executeCustomHandler(String handlerName, Object handlerArgs) { // library marker kkossev.commonLib, line 412
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 413
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 414
        return false // library marker kkossev.commonLib, line 415
    } // library marker kkossev.commonLib, line 416
    // execute the customHandler function // library marker kkossev.commonLib, line 417
    Boolean result = false // library marker kkossev.commonLib, line 418
    try { // library marker kkossev.commonLib, line 419
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 420
    } // library marker kkossev.commonLib, line 421
    catch (e) { // library marker kkossev.commonLib, line 422
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 423
        return false // library marker kkossev.commonLib, line 424
    } // library marker kkossev.commonLib, line 425
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 426
    return result // library marker kkossev.commonLib, line 427
} // library marker kkossev.commonLib, line 428

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 430
private void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 431
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 432
    final String commandId = data[0] // library marker kkossev.commonLib, line 433
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 434
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 435
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 436
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 437
    } else { // library marker kkossev.commonLib, line 438
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 439
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 440
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 441
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 442
        } // library marker kkossev.commonLib, line 443
    } // library marker kkossev.commonLib, line 444
} // library marker kkossev.commonLib, line 445

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 447
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 448
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 449
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 450

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 452
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 453
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 454
] // library marker kkossev.commonLib, line 455

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 457
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 458
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 459
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 460
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 461
] // library marker kkossev.commonLib, line 462

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 464
private BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 465
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 466
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 467
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 468
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 469
    return avg // library marker kkossev.commonLib, line 470
} // library marker kkossev.commonLib, line 471

private void handlePingResponse() { // library marker kkossev.commonLib, line 473
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 474
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 475
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 476

    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 478
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 479
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 480
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 481
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 482
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 483
        sendRttEvent() // library marker kkossev.commonLib, line 484
    } // library marker kkossev.commonLib, line 485
    else { // library marker kkossev.commonLib, line 486
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 487
    } // library marker kkossev.commonLib, line 488
    state.states['isPing'] = false // library marker kkossev.commonLib, line 489
} // library marker kkossev.commonLib, line 490

/* // library marker kkossev.commonLib, line 492
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 493
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 494
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 495
*/ // library marker kkossev.commonLib, line 496
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 497

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 499
private void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 500
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 501
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 502
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 503
    boolean isPing = state.states?.isPing ?: false // library marker kkossev.commonLib, line 504
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 505
        case 0x0000: // library marker kkossev.commonLib, line 506
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 507
            break // library marker kkossev.commonLib, line 508
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 509
            if (isPing) { // library marker kkossev.commonLib, line 510
                handlePingResponse() // library marker kkossev.commonLib, line 511
            } // library marker kkossev.commonLib, line 512
            else { // library marker kkossev.commonLib, line 513
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 514
            } // library marker kkossev.commonLib, line 515
            break // library marker kkossev.commonLib, line 516
        case 0x0004: // library marker kkossev.commonLib, line 517
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 518
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 519
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 520
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 521
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 522
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 523
            } // library marker kkossev.commonLib, line 524
            break // library marker kkossev.commonLib, line 525
        case 0x0005: // library marker kkossev.commonLib, line 526
            if (isPing) { // library marker kkossev.commonLib, line 527
                handlePingResponse() // library marker kkossev.commonLib, line 528
            } // library marker kkossev.commonLib, line 529
            else { // library marker kkossev.commonLib, line 530
                logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 531
                // received device model Remote Control N2 // library marker kkossev.commonLib, line 532
                String model = device.getDataValue('model') // library marker kkossev.commonLib, line 533
                if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 534
                    logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 535
                    device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 536
                } // library marker kkossev.commonLib, line 537
            } // library marker kkossev.commonLib, line 538
            break // library marker kkossev.commonLib, line 539
        case 0x0007: // library marker kkossev.commonLib, line 540
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 541
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 542
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 543
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 544
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 545
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 546
            } // library marker kkossev.commonLib, line 547
            break // library marker kkossev.commonLib, line 548
        case 0xFFDF: // library marker kkossev.commonLib, line 549
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 550
            break // library marker kkossev.commonLib, line 551
        case 0xFFE2: // library marker kkossev.commonLib, line 552
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 553
            break // library marker kkossev.commonLib, line 554
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 555
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 556
            break // library marker kkossev.commonLib, line 557
        case 0xFFFE: // library marker kkossev.commonLib, line 558
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 559
            break // library marker kkossev.commonLib, line 560
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 561
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 562
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 563
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 564
            break // library marker kkossev.commonLib, line 565
        default: // library marker kkossev.commonLib, line 566
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 567
            break // library marker kkossev.commonLib, line 568
    } // library marker kkossev.commonLib, line 569
} // library marker kkossev.commonLib, line 570

private void standardParsePollControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 572
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 573
        case 0x0000: logDebug "PollControl cluster: CheckInInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 574
        case 0x0001: logDebug "PollControl cluster: LongPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 575
        case 0x0002: logDebug "PollControl cluster: ShortPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 576
        case 0x0003: logDebug "PollControl cluster: FastPollTimeout = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 577
        case 0x0004: logDebug "PollControl cluster: CheckInIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 578
        case 0x0005: logDebug "PollControl cluster: LongPollIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 579
        case 0x0006: logDebug "PollControl cluster: FastPollTimeoutMax = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 580
        default: logWarn "zigbee received unknown PollControl cluster attribute 0x${descMap.attrId} (value ${descMap.value})" ; break // library marker kkossev.commonLib, line 581
    } // library marker kkossev.commonLib, line 582
} // library marker kkossev.commonLib, line 583

public void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 585
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 586
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 587

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 589
    Map descMap = [:] // library marker kkossev.commonLib, line 590
    try { // library marker kkossev.commonLib, line 591
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 592
    } // library marker kkossev.commonLib, line 593
    catch (e1) { // library marker kkossev.commonLib, line 594
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 595
        // try alternative custom parsing // library marker kkossev.commonLib, line 596
        descMap = [:] // library marker kkossev.commonLib, line 597
        try { // library marker kkossev.commonLib, line 598
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 599
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 600
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 601
            } // library marker kkossev.commonLib, line 602
        } // library marker kkossev.commonLib, line 603
        catch (e2) { // library marker kkossev.commonLib, line 604
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 605
            return [:] // library marker kkossev.commonLib, line 606
        } // library marker kkossev.commonLib, line 607
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 608
    } // library marker kkossev.commonLib, line 609
    return descMap // library marker kkossev.commonLib, line 610
} // library marker kkossev.commonLib, line 611

// return true if the messages is processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 613
// return false if the cluster is not a Tuya cluster // library marker kkossev.commonLib, line 614
private boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 615
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 616
        return false // library marker kkossev.commonLib, line 617
    } // library marker kkossev.commonLib, line 618
    // try to parse ... // library marker kkossev.commonLib, line 619
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 620
    Map descMap = [:] // library marker kkossev.commonLib, line 621
    try { // library marker kkossev.commonLib, line 622
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 623
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 624
    } // library marker kkossev.commonLib, line 625
    catch (e) { // library marker kkossev.commonLib, line 626
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 627
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 628
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 629
        return true // library marker kkossev.commonLib, line 630
    } // library marker kkossev.commonLib, line 631

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 633
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 634
    } // library marker kkossev.commonLib, line 635
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 636
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 637
    } // library marker kkossev.commonLib, line 638
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 639
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 640
    } // library marker kkossev.commonLib, line 641
    else { // library marker kkossev.commonLib, line 642
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 643
        return false // library marker kkossev.commonLib, line 644
    } // library marker kkossev.commonLib, line 645
    return true    // processed // library marker kkossev.commonLib, line 646
} // library marker kkossev.commonLib, line 647

// return true if processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 649
private boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 650
  /* // library marker kkossev.commonLib, line 651
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 652
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 653
        return true // library marker kkossev.commonLib, line 654
    } // library marker kkossev.commonLib, line 655
*/ // library marker kkossev.commonLib, line 656
    Map descMap = [:] // library marker kkossev.commonLib, line 657
    try { // library marker kkossev.commonLib, line 658
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 659
    } // library marker kkossev.commonLib, line 660
    catch (e1) { // library marker kkossev.commonLib, line 661
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 662
        // try alternative custom parsing // library marker kkossev.commonLib, line 663
        descMap = [:] // library marker kkossev.commonLib, line 664
        try { // library marker kkossev.commonLib, line 665
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 666
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 667
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 668
            } // library marker kkossev.commonLib, line 669
        } // library marker kkossev.commonLib, line 670
        catch (e2) { // library marker kkossev.commonLib, line 671
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 672
            return true // library marker kkossev.commonLib, line 673
        } // library marker kkossev.commonLib, line 674
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 675
    } // library marker kkossev.commonLib, line 676
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 677
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 678
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 679
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 680
        return false // library marker kkossev.commonLib, line 681
    } // library marker kkossev.commonLib, line 682
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 683
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 684
    // attribute report received // library marker kkossev.commonLib, line 685
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 686
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 687
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 688
    } // library marker kkossev.commonLib, line 689
    attrData.each { // library marker kkossev.commonLib, line 690
        if (it.status == '86') { // library marker kkossev.commonLib, line 691
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 692
        // TODO - skip parsing? // library marker kkossev.commonLib, line 693
        } // library marker kkossev.commonLib, line 694
        switch (it.cluster) { // library marker kkossev.commonLib, line 695
            case '0000' : // library marker kkossev.commonLib, line 696
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 697
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 698
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 699
                } // library marker kkossev.commonLib, line 700
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 701
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 702
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 703
                } // library marker kkossev.commonLib, line 704
                else { // library marker kkossev.commonLib, line 705
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 706
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 707
                } // library marker kkossev.commonLib, line 708
                break // library marker kkossev.commonLib, line 709
            default : // library marker kkossev.commonLib, line 710
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 711
                break // library marker kkossev.commonLib, line 712
        } // switch // library marker kkossev.commonLib, line 713
    } // for each attribute // library marker kkossev.commonLib, line 714
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 715
} // library marker kkossev.commonLib, line 716

public String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 718
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 719
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 720
} // library marker kkossev.commonLib, line 721

public String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 723
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 724
} // library marker kkossev.commonLib, line 725

/* // library marker kkossev.commonLib, line 727
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 728
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 729
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 730
*/ // library marker kkossev.commonLib, line 731
private static int getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 732
private static int getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 733
private static int getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 734

// Tuya Commands // library marker kkossev.commonLib, line 736
private static int getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 737
private static int getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 738
private static int getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 739
private static int getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 740
private static int getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 741

// tuya DP type // library marker kkossev.commonLib, line 743
private static String getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 744
private static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 745
private static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 746
private static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 747
private static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 748
private static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 749

private void syncTuyaDateTime() { // library marker kkossev.commonLib, line 751
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 752
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 753
    long offset = 0 // library marker kkossev.commonLib, line 754
    int offsetHours = 0 // library marker kkossev.commonLib, line 755
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 756
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 757
    try { // library marker kkossev.commonLib, line 758
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 759
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 760
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 761
    } catch (e) { // library marker kkossev.commonLib, line 762
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 763
    } // library marker kkossev.commonLib, line 764
    // // library marker kkossev.commonLib, line 765
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 766
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 767
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 768
} // library marker kkossev.commonLib, line 769

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 771
public void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 772
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 773
        syncTuyaDateTime() // library marker kkossev.commonLib, line 774
    } // library marker kkossev.commonLib, line 775
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 776
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 777
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 778
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 779
        if (status != '00') { // library marker kkossev.commonLib, line 780
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 781
        } // library marker kkossev.commonLib, line 782
    } // library marker kkossev.commonLib, line 783
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 784
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 785
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 786
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 787
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 788
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 789
            return // library marker kkossev.commonLib, line 790
        } // library marker kkossev.commonLib, line 791
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 792
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 793
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 794
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 795
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 796
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 797
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 798
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 799
            } // library marker kkossev.commonLib, line 800
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 801
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 802
        } // library marker kkossev.commonLib, line 803
    } // library marker kkossev.commonLib, line 804
    else { // library marker kkossev.commonLib, line 805
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 806
    } // library marker kkossev.commonLib, line 807
} // library marker kkossev.commonLib, line 808

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 810
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 811
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 812
    if (this.respondsTo('ensureCurrentProfileLoaded')) { // library marker kkossev.commonLib, line 813
        ensureCurrentProfileLoaded() // library marker kkossev.commonLib, line 814
    } // library marker kkossev.commonLib, line 815
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 816
        //logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 817
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 818
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 819
        } // library marker kkossev.commonLib, line 820
    } // library marker kkossev.commonLib, line 821
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 822
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 823
        //logTrace 'standardProcessTuyaDP: processTuyaDPfromDeviceProfile exists, calling it...' // library marker kkossev.commonLib, line 824
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 825
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 826
        } // library marker kkossev.commonLib, line 827
    } // library marker kkossev.commonLib, line 828
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 829
} // library marker kkossev.commonLib, line 830

public int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 832
    int retValue = 0 // library marker kkossev.commonLib, line 833
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 834
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 835
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 836
        int power = 1 // library marker kkossev.commonLib, line 837
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 838
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 839
            power = power * 256 // library marker kkossev.commonLib, line 840
        } // library marker kkossev.commonLib, line 841
    } // library marker kkossev.commonLib, line 842
    return retValue // library marker kkossev.commonLib, line 843
} // library marker kkossev.commonLib, line 844

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 846

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 848
    List<String> cmds = [] // library marker kkossev.commonLib, line 849
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 850
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 851
    int tuyaCmd // library marker kkossev.commonLib, line 852
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified! // library marker kkossev.commonLib, line 853
    if (this.respondsTo('getDEVICE') && DEVICE?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 854
        tuyaCmd = DEVICE?.device?.tuyaCmd // library marker kkossev.commonLib, line 855
    } // library marker kkossev.commonLib, line 856
    else { // library marker kkossev.commonLib, line 857
        tuyaCmd = tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 858
    } // library marker kkossev.commonLib, line 859
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 860
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 861
    return cmds // library marker kkossev.commonLib, line 862
} // library marker kkossev.commonLib, line 863

private String getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 865

public void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 867
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 868
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 869
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 870
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 871
} // library marker kkossev.commonLib, line 872


public List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 875
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 876
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 877
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 878
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 879
} // library marker kkossev.commonLib, line 880

public List<String> queryAllTuyaDP() { // library marker kkossev.commonLib, line 882
    logTrace 'queryAllTuyaDP()' // library marker kkossev.commonLib, line 883
    List<String> cmds = zigbee.command(0xEF00, 0x03) // library marker kkossev.commonLib, line 884
    return cmds // library marker kkossev.commonLib, line 885
} // library marker kkossev.commonLib, line 886

public void aqaraBlackMagic() { // library marker kkossev.commonLib, line 888
    List<String> cmds = [] // library marker kkossev.commonLib, line 889
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 890
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 891
    } // library marker kkossev.commonLib, line 892
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 893
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 894
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 895
        return // library marker kkossev.commonLib, line 896
    } // library marker kkossev.commonLib, line 897
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 898
} // library marker kkossev.commonLib, line 899

// Invoked from configure() // library marker kkossev.commonLib, line 901
public List<String> initializeDevice() { // library marker kkossev.commonLib, line 902
    List<String> cmds = [] // library marker kkossev.commonLib, line 903
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 904
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 905
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 906
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 907
    } // library marker kkossev.commonLib, line 908
    else { logDebug 'no customInitializeDevice method defined' } // library marker kkossev.commonLib, line 909
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 910
    return cmds // library marker kkossev.commonLib, line 911
} // library marker kkossev.commonLib, line 912

// Invoked from configure() // library marker kkossev.commonLib, line 914
public List<String> configureDevice() { // library marker kkossev.commonLib, line 915
    List<String> cmds = [] // library marker kkossev.commonLib, line 916
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 917
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 918
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 919
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 920
    } // library marker kkossev.commonLib, line 921
    else { logDebug 'no customConfigureDevice method defined' } // library marker kkossev.commonLib, line 922
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 923
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 924
    return cmds // library marker kkossev.commonLib, line 925
} // library marker kkossev.commonLib, line 926

/* // library marker kkossev.commonLib, line 928
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 929
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 930
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 931
*/ // library marker kkossev.commonLib, line 932

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 934
    List<String> cmds = [] // library marker kkossev.commonLib, line 935
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 936
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 937
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 938
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 939
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 940
            } // library marker kkossev.commonLib, line 941
        } // library marker kkossev.commonLib, line 942
    } // library marker kkossev.commonLib, line 943
    return cmds // library marker kkossev.commonLib, line 944
} // library marker kkossev.commonLib, line 945

public void refresh() { // library marker kkossev.commonLib, line 947
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 948
    checkDriverVersion(state) // library marker kkossev.commonLib, line 949
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 950
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 951
        customCmds = customRefresh() // library marker kkossev.commonLib, line 952
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 953
    } // library marker kkossev.commonLib, line 954
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 955
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 956
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 957
    } // library marker kkossev.commonLib, line 958
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 959
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 960
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 961
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 962
    } // library marker kkossev.commonLib, line 963
    else { // library marker kkossev.commonLib, line 964
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 965
    } // library marker kkossev.commonLib, line 966
} // library marker kkossev.commonLib, line 967

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, 'clearRefreshRequest', [overwrite: true]) } // library marker kkossev.commonLib, line 969
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 970
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 971

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 973
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 974
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 975
        sendEvent(name: '_status_', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 976
    } // library marker kkossev.commonLib, line 977
    else { // library marker kkossev.commonLib, line 978
        logInfo "${info}" // library marker kkossev.commonLib, line 979
        sendEvent(name: '_status_', value: info, type: 'digital') // library marker kkossev.commonLib, line 980
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 981
    } // library marker kkossev.commonLib, line 982
} // library marker kkossev.commonLib, line 983

public void ping() { // library marker kkossev.commonLib, line 985
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 986
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true // library marker kkossev.commonLib, line 987
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 988
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID // library marker kkossev.commonLib, line 989
    if (isVirtual()) { runInMillis(10, 'virtualPong') } // library marker kkossev.commonLib, line 990
    else if (device.getDataValue('manufacturer') == 'Aqara') { // library marker kkossev.commonLib, line 991
        logDebug 'Aqara device ping...' // library marker kkossev.commonLib, line 992
        sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [destEndpoint: 0x01], 0) ) // library marker kkossev.commonLib, line 993
    } // library marker kkossev.commonLib, line 994
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) } // library marker kkossev.commonLib, line 995
    logDebug 'ping...' // library marker kkossev.commonLib, line 996
} // library marker kkossev.commonLib, line 997

private void virtualPong() { // library marker kkossev.commonLib, line 999
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1000
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1001
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1002
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1003
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1004
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '9999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1005
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1006
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1007
        sendRttEvent() // library marker kkossev.commonLib, line 1008
    } // library marker kkossev.commonLib, line 1009
    else { // library marker kkossev.commonLib, line 1010
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1011
    } // library marker kkossev.commonLib, line 1012
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1013
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1014
} // library marker kkossev.commonLib, line 1015

public void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1017
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1018
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1019
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1020
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1021
    if (value == null) { // library marker kkossev.commonLib, line 1022
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1023
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1024
    } // library marker kkossev.commonLib, line 1025
    else { // library marker kkossev.commonLib, line 1026
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1027
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1028
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1029
    } // library marker kkossev.commonLib, line 1030
} // library marker kkossev.commonLib, line 1031

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1033
    if (cluster != null) { // library marker kkossev.commonLib, line 1034
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1035
    } // library marker kkossev.commonLib, line 1036
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1037
    return 'NULL' // library marker kkossev.commonLib, line 1038
} // library marker kkossev.commonLib, line 1039

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1041
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1042
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1043
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1044
} // library marker kkossev.commonLib, line 1045

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1047
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1048
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1049
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1050
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1051
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1052
    } // library marker kkossev.commonLib, line 1053
} // library marker kkossev.commonLib, line 1054

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1056
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1057
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1058
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1059
    if (state.health?.isHealthCheck == true) { // library marker kkossev.commonLib, line 1060
        logWarn 'device health check failed!' // library marker kkossev.commonLib, line 1061
        state.health?.checkCtr3 = (state.health?.checkCtr3 ?: 0 ) + 1 // library marker kkossev.commonLib, line 1062
        if (state.health?.checkCtr3 >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1063
            if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1064
                sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1065
            } // library marker kkossev.commonLib, line 1066
        } // library marker kkossev.commonLib, line 1067
        state.health['isHealthCheck'] = false // library marker kkossev.commonLib, line 1068
    } // library marker kkossev.commonLib, line 1069
} // library marker kkossev.commonLib, line 1070

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1072
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1073
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1074
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1075
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1076
    } // library marker kkossev.commonLib, line 1077
    else { // library marker kkossev.commonLib, line 1078
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1079
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1080
    } // library marker kkossev.commonLib, line 1081
} // library marker kkossev.commonLib, line 1082

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1084
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1085
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1086
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1087
} // library marker kkossev.commonLib, line 1088

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1090
private void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1091
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1092
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1093
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1094
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1095
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1096
    } // library marker kkossev.commonLib, line 1097
} // library marker kkossev.commonLib, line 1098

private void deviceHealthCheck() { // library marker kkossev.commonLib, line 1100
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1101
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1102
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1103
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1104
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1105
            logWarn 'not present!' // library marker kkossev.commonLib, line 1106
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1107
        } // library marker kkossev.commonLib, line 1108
    } // library marker kkossev.commonLib, line 1109
    else { // library marker kkossev.commonLib, line 1110
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})" // library marker kkossev.commonLib, line 1111
    } // library marker kkossev.commonLib, line 1112
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1113
    // added 03/06/2025 // library marker kkossev.commonLib, line 1114
    if (settings?.healthCheckMethod as int == 2) { // library marker kkossev.commonLib, line 1115
        state.health['isHealthCheck'] = true // library marker kkossev.commonLib, line 1116
        ping()  // proactively ping the device... // library marker kkossev.commonLib, line 1117
    } // library marker kkossev.commonLib, line 1118
} // library marker kkossev.commonLib, line 1119

private void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1121
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1122
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1123
    if (value == 'online') { // library marker kkossev.commonLib, line 1124
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1125
    } // library marker kkossev.commonLib, line 1126
    else { // library marker kkossev.commonLib, line 1127
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1128
    } // library marker kkossev.commonLib, line 1129
} // library marker kkossev.commonLib, line 1130

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1132
void updated() { // library marker kkossev.commonLib, line 1133
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1134
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1135
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1136
    unschedule() // library marker kkossev.commonLib, line 1137

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1139
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1140
        runIn(86400, 'logsOff') // library marker kkossev.commonLib, line 1141
    } // library marker kkossev.commonLib, line 1142
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1143
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1144
        runIn(1800, 'traceOff') // library marker kkossev.commonLib, line 1145
    } // library marker kkossev.commonLib, line 1146

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1148
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1149
        // schedule the periodic timer // library marker kkossev.commonLib, line 1150
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1151
        if (interval > 0) { // library marker kkossev.commonLib, line 1152
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1153
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1154
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1155
        } // library marker kkossev.commonLib, line 1156
    } // library marker kkossev.commonLib, line 1157
    else { // library marker kkossev.commonLib, line 1158
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1159
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1160
    } // library marker kkossev.commonLib, line 1161
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1162
        customUpdated() // library marker kkossev.commonLib, line 1163
    } // library marker kkossev.commonLib, line 1164

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1166
} // library marker kkossev.commonLib, line 1167

private void logsOff() { // library marker kkossev.commonLib, line 1169
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1170
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1171
} // library marker kkossev.commonLib, line 1172
private void traceOff() { // library marker kkossev.commonLib, line 1173
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1174
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1175
} // library marker kkossev.commonLib, line 1176

public void configure(String command) { // library marker kkossev.commonLib, line 1178
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1179
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1180
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1181
        return // library marker kkossev.commonLib, line 1182
    } // library marker kkossev.commonLib, line 1183
    // // library marker kkossev.commonLib, line 1184
    String func // library marker kkossev.commonLib, line 1185
    try { // library marker kkossev.commonLib, line 1186
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1187
        "$func"() // library marker kkossev.commonLib, line 1188
    } // library marker kkossev.commonLib, line 1189
    catch (e) { // library marker kkossev.commonLib, line 1190
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1191
        return // library marker kkossev.commonLib, line 1192
    } // library marker kkossev.commonLib, line 1193
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1194
} // library marker kkossev.commonLib, line 1195

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1197
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1198
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1199
} // library marker kkossev.commonLib, line 1200

public void loadAllDefaults() { // library marker kkossev.commonLib, line 1202
    logDebug 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1203
    deleteAllSettings() // library marker kkossev.commonLib, line 1204
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1205
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1206
    deleteAllStates() // library marker kkossev.commonLib, line 1207
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1208

    initialize() // library marker kkossev.commonLib, line 1210
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1211
    updated() // library marker kkossev.commonLib, line 1212
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1213
} // library marker kkossev.commonLib, line 1214

private void configureNow() { // library marker kkossev.commonLib, line 1216
    configure() // library marker kkossev.commonLib, line 1217
} // library marker kkossev.commonLib, line 1218

/** // library marker kkossev.commonLib, line 1220
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1221
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1222
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1223
 */ // library marker kkossev.commonLib, line 1224
void configure() { // library marker kkossev.commonLib, line 1225
    List<String> cmds = [] // library marker kkossev.commonLib, line 1226
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1227
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1228
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1229
    if (isTuya()) { // library marker kkossev.commonLib, line 1230
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1231
    } // library marker kkossev.commonLib, line 1232
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1233
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1234
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1235
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1236
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1237
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1238
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1239
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1240
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1241
    } // library marker kkossev.commonLib, line 1242
    else { // library marker kkossev.commonLib, line 1243
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1244
    } // library marker kkossev.commonLib, line 1245
} // library marker kkossev.commonLib, line 1246

 // Invoked when the device is installed with this driver automatically selected. // library marker kkossev.commonLib, line 1248
void installed() { // library marker kkossev.commonLib, line 1249
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1250
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1251
    // populate some default values for attributes // library marker kkossev.commonLib, line 1252
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1253
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1254
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1255
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1256
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1257
} // library marker kkossev.commonLib, line 1258

private void queryPowerSource() { // library marker kkossev.commonLib, line 1260
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1261
} // library marker kkossev.commonLib, line 1262

 // Invoked from 'LoadAllDefaults' // library marker kkossev.commonLib, line 1264
private void initialize() { // library marker kkossev.commonLib, line 1265
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1266
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1267
    if (device.getDataValue('powerSource') == null) { // library marker kkossev.commonLib, line 1268
        logInfo "initializing device powerSource 'unknown'" // library marker kkossev.commonLib, line 1269
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1270
    } // library marker kkossev.commonLib, line 1271
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1272
    updateTuyaVersion() // library marker kkossev.commonLib, line 1273
    updateAqaraVersion() // library marker kkossev.commonLib, line 1274
} // library marker kkossev.commonLib, line 1275

/* // library marker kkossev.commonLib, line 1277
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1278
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1279
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1280
*/ // library marker kkossev.commonLib, line 1281

static Integer safeToInt(Object val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1283
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1284
} // library marker kkossev.commonLib, line 1285

static Double safeToDouble(Object val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1287
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1288
} // library marker kkossev.commonLib, line 1289

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1291
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1292
} // library marker kkossev.commonLib, line 1293

public void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1295
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1296
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1297
        return // library marker kkossev.commonLib, line 1298
    } // library marker kkossev.commonLib, line 1299
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1300
    cmd.each { // library marker kkossev.commonLib, line 1301
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1302
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1303
            return // library marker kkossev.commonLib, line 1304
        } // library marker kkossev.commonLib, line 1305
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1306
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1307
    } // library marker kkossev.commonLib, line 1308
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1309
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1310
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1311
} // library marker kkossev.commonLib, line 1312

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1314

private String getDeviceInfo() { // library marker kkossev.commonLib, line 1316
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1317
} // library marker kkossev.commonLib, line 1318

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1320
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1321
} // library marker kkossev.commonLib, line 1322

//@CompileStatic // library marker kkossev.commonLib, line 1324
public void checkDriverVersion(final Map stateCopy) { // library marker kkossev.commonLib, line 1325
    if (stateCopy.driverVersion == null || driverVersionAndTimeStamp() != stateCopy.driverVersion) { // library marker kkossev.commonLib, line 1326
        logDebug "checkDriverVersion: updating the settings from the current driver version ${stateCopy.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1327
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()} from version ${stateCopy.driverVersion ?: 'unknown'}") // library marker kkossev.commonLib, line 1328
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1329
        initializeVars(false) // library marker kkossev.commonLib, line 1330
        updateTuyaVersion() // library marker kkossev.commonLib, line 1331
        updateAqaraVersion() // library marker kkossev.commonLib, line 1332
        if (this.respondsTo('customcheckDriverVersion')) { customcheckDriverVersion(stateCopy) } // library marker kkossev.commonLib, line 1333
    } // library marker kkossev.commonLib, line 1334
    if (state.states == null) { state.states = [:] } ; if (state.lastRx == null) { state.lastRx = [:] } ; if (state.lastTx == null) { state.lastTx = [:] } ; if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1335
} // library marker kkossev.commonLib, line 1336

// credits @thebearmay // library marker kkossev.commonLib, line 1338
String getModel() { // library marker kkossev.commonLib, line 1339
    try { // library marker kkossev.commonLib, line 1340
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1341
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1342
    } catch (ignore) { // library marker kkossev.commonLib, line 1343
        try { // library marker kkossev.commonLib, line 1344
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1345
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1346
                return model // library marker kkossev.commonLib, line 1347
            } // library marker kkossev.commonLib, line 1348
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1349
            return '' // library marker kkossev.commonLib, line 1350
        } // library marker kkossev.commonLib, line 1351
    } // library marker kkossev.commonLib, line 1352
} // library marker kkossev.commonLib, line 1353

// credits @thebearmay // library marker kkossev.commonLib, line 1355
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1356
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1357
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1358
    String revision = tokens.last() // library marker kkossev.commonLib, line 1359
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1360
} // library marker kkossev.commonLib, line 1361

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1363
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1364
    unschedule() // library marker kkossev.commonLib, line 1365
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1366
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1367

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1369
} // library marker kkossev.commonLib, line 1370

void resetStatistics() { // library marker kkossev.commonLib, line 1372
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1373
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1374
} // library marker kkossev.commonLib, line 1375

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1377
void resetStats() { // library marker kkossev.commonLib, line 1378
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1379
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1380
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1381
    state.stats.rxCtr = 0 ; state.stats.txCtr = 0 // library marker kkossev.commonLib, line 1382
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1383
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1384
    if (this.respondsTo('customResetStats')) { customResetStats() } // library marker kkossev.commonLib, line 1385
    logInfo 'statistics reset!' // library marker kkossev.commonLib, line 1386
} // library marker kkossev.commonLib, line 1387

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1389
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1390
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1391
        state.clear() // library marker kkossev.commonLib, line 1392
        unschedule() // library marker kkossev.commonLib, line 1393
        resetStats() // library marker kkossev.commonLib, line 1394
        if (this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1395
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1396
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1397
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1398
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1399
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1400
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1401
    } // library marker kkossev.commonLib, line 1402

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1404
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1405
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1406
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1407
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1408

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1410
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1411
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1412
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1413
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1414
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1415
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1416

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1418

    // common libraries initialization // library marker kkossev.commonLib, line 1420
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024 // library marker kkossev.commonLib, line 1421
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024 // library marker kkossev.commonLib, line 1422
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1423
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1424
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1425
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1426
    // // library marker kkossev.commonLib, line 1427
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers! // library marker kkossev.commonLib, line 1428
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024 // library marker kkossev.commonLib, line 1429
    // // library marker kkossev.commonLib, line 1430
    // custom device driver specific initialization should be at the end // library marker kkossev.commonLib, line 1431
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1432
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1433
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1434

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1436
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1437
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1438
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1439
    if ( ep  != null) { // library marker kkossev.commonLib, line 1440
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1441
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1442
    } // library marker kkossev.commonLib, line 1443
    else { // library marker kkossev.commonLib, line 1444
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1445
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1446
    } // library marker kkossev.commonLib, line 1447
} // library marker kkossev.commonLib, line 1448

// not used!? // library marker kkossev.commonLib, line 1450
void setDestinationEP() { // library marker kkossev.commonLib, line 1451
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1452
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1453
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1454
} // library marker kkossev.commonLib, line 1455

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1457
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1458
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1459
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1460
void logError(final String msg) { if (settings?.txtEnable)   { log.error "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1461

// _DEBUG mode only // library marker kkossev.commonLib, line 1463
void getAllProperties() { // library marker kkossev.commonLib, line 1464
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1465
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1466
} // library marker kkossev.commonLib, line 1467

// delete all Preferences // library marker kkossev.commonLib, line 1469
void deleteAllSettings() { // library marker kkossev.commonLib, line 1470
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1471
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1472
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1473
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1474
} // library marker kkossev.commonLib, line 1475

// delete all attributes // library marker kkossev.commonLib, line 1477
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1478
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1479
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1480
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1481
} // library marker kkossev.commonLib, line 1482

// delete all State Variables // library marker kkossev.commonLib, line 1484
void deleteAllStates() { // library marker kkossev.commonLib, line 1485
    String stateDeleted = '' // library marker kkossev.commonLib, line 1486
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1487
    state.clear() // library marker kkossev.commonLib, line 1488
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1489
} // library marker kkossev.commonLib, line 1490

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1492
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1493
} // library marker kkossev.commonLib, line 1494

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1496
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1497
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1498
} // library marker kkossev.commonLib, line 1499

void testParse(String par) { // library marker kkossev.commonLib, line 1501
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1502
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1503
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1504
    parse(par) // library marker kkossev.commonLib, line 1505
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1506
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1507
} // library marker kkossev.commonLib, line 1508

Object testJob() { // library marker kkossev.commonLib, line 1510
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1511
} // library marker kkossev.commonLib, line 1512

/** // library marker kkossev.commonLib, line 1514
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1515
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1516
 */ // library marker kkossev.commonLib, line 1517
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1518
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1519
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1520
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1521
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1522
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1523
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1524
    String cron // library marker kkossev.commonLib, line 1525
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1526
    else { // library marker kkossev.commonLib, line 1527
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1528
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1529
    } // library marker kkossev.commonLib, line 1530
    return cron // library marker kkossev.commonLib, line 1531
} // library marker kkossev.commonLib, line 1532

// credits @thebearmay // library marker kkossev.commonLib, line 1534
String formatUptime() { // library marker kkossev.commonLib, line 1535
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1536
} // library marker kkossev.commonLib, line 1537

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1539
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1540
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1541
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1542
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1543
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1544
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1545
} // library marker kkossev.commonLib, line 1546

boolean isTuya() { // library marker kkossev.commonLib, line 1548
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1549
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1550
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1551
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1552
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1553
} // library marker kkossev.commonLib, line 1554

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1556
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1557
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1558
    if (application != null) { // library marker kkossev.commonLib, line 1559
        Integer ver // library marker kkossev.commonLib, line 1560
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1561
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1562
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1563
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1564
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1565
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1566
        } // library marker kkossev.commonLib, line 1567
    } // library marker kkossev.commonLib, line 1568
} // library marker kkossev.commonLib, line 1569

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1571

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1573
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1574
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1575
    if (application != null) { // library marker kkossev.commonLib, line 1576
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1577
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1578
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1579
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1580
        } // library marker kkossev.commonLib, line 1581
    } // library marker kkossev.commonLib, line 1582
} // library marker kkossev.commonLib, line 1583

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1585
    try { // library marker kkossev.commonLib, line 1586
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1587
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1588
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1589
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1590
    } catch (e) { // library marker kkossev.commonLib, line 1591
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1592
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1593
    } // library marker kkossev.commonLib, line 1594
} // library marker kkossev.commonLib, line 1595

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1597
    try { // library marker kkossev.commonLib, line 1598
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1599
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1600
        return date.getTime() // library marker kkossev.commonLib, line 1601
    } catch (e) { // library marker kkossev.commonLib, line 1602
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1603
        return now() // library marker kkossev.commonLib, line 1604
    } // library marker kkossev.commonLib, line 1605
} // library marker kkossev.commonLib, line 1606

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1608
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1609
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1610
    int seconds = time % 60 // library marker kkossev.commonLib, line 1611
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1612
} // library marker kkossev.commonLib, line 1613

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (176) kkossev.onOffLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.onOffLib, line 1
library( // library marker kkossev.onOffLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee OnOff Cluster Library', name: 'onOffLib', namespace: 'kkossev', // library marker kkossev.onOffLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/onOffLib.groovy', documentationLink: '', // library marker kkossev.onOffLib, line 4
    version: '3.2.2' // library marker kkossev.onOffLib, line 5
) // library marker kkossev.onOffLib, line 6
/* // library marker kkossev.onOffLib, line 7
 *  Zigbee OnOff Cluster Library // library marker kkossev.onOffLib, line 8
 * // library marker kkossev.onOffLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.onOffLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.onOffLib, line 11
 * // library marker kkossev.onOffLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.onOffLib, line 13
 * // library marker kkossev.onOffLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.onOffLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.onOffLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.onOffLib, line 17
 * // library marker kkossev.onOffLib, line 18
 * ver. 3.2.0  2024-06-04 kkossev  - commonLib 3.2.1 allignment; if isRefresh then sendEvent with isStateChange = true // library marker kkossev.onOffLib, line 19
 * ver. 3.2.1  2024-06-07 kkossev  - the advanced options are excpluded for DEVICE_TYPE Thermostat // library marker kkossev.onOffLib, line 20
 * ver. 3.2.2  2024-06-29 kkossev  - added on/off control for Tuya device profiles with 'switch' dp; // library marker kkossev.onOffLib, line 21
 * // library marker kkossev.onOffLib, line 22
 *                                   TODO: // library marker kkossev.onOffLib, line 23
*/ // library marker kkossev.onOffLib, line 24

static String onOffLibVersion()   { '3.2.2' } // library marker kkossev.onOffLib, line 26
static String onOffLibStamp() { '2024/06/29 12:27 PM' } // library marker kkossev.onOffLib, line 27

@Field static final Boolean _THREE_STATE = true // library marker kkossev.onOffLib, line 29

metadata { // library marker kkossev.onOffLib, line 31
    capability 'Actuator' // library marker kkossev.onOffLib, line 32
    capability 'Switch' // library marker kkossev.onOffLib, line 33
    if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 34
        attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.onOffLib, line 35
    } // library marker kkossev.onOffLib, line 36
    // no commands // library marker kkossev.onOffLib, line 37
    preferences { // library marker kkossev.onOffLib, line 38
        if (settings?.advancedOptions == true && device != null && !(DEVICE_TYPE in ['Device', 'Thermostat'])) { // library marker kkossev.onOffLib, line 39
            input(name: 'ignoreDuplicated', type: 'bool', title: '<b>Ignore Duplicated Switch Events</b>', description: 'Some switches and plugs send periodically the switch status as a heart-beet ', defaultValue: true) // library marker kkossev.onOffLib, line 40
            input(name: 'alwaysOn', type: 'bool', title: '<b>Always On</b>', description: 'Disable switching off plugs and switches that must stay always On', defaultValue: false) // library marker kkossev.onOffLib, line 41
            if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 42
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: 'Experimental multi-state switch events', defaultValue: false // library marker kkossev.onOffLib, line 43
            } // library marker kkossev.onOffLib, line 44
        } // library marker kkossev.onOffLib, line 45
    } // library marker kkossev.onOffLib, line 46
} // library marker kkossev.onOffLib, line 47

@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.onOffLib, line 49
    defaultValue: 0, options: [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.onOffLib, line 50
] // library marker kkossev.onOffLib, line 51

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.onOffLib, line 53
    '0': 'switch off', '1': 'switch on', '2': 'switch last state' // library marker kkossev.onOffLib, line 54
] // library marker kkossev.onOffLib, line 55

@Field static final Map switchTypeOptions = [ // library marker kkossev.onOffLib, line 57
    '0': 'toggle', '1': 'state', '2': 'momentary' // library marker kkossev.onOffLib, line 58
] // library marker kkossev.onOffLib, line 59

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.onOffLib, line 61

/* // library marker kkossev.onOffLib, line 63
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 64
 * on/off cluster            0x0006     TODO - move to a library !!!!!!!!!!!!!!! // library marker kkossev.onOffLib, line 65
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 66
*/ // library marker kkossev.onOffLib, line 67
void standardParseOnOffCluster(final Map descMap) { // library marker kkossev.onOffLib, line 68
    /* // library marker kkossev.onOffLib, line 69
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.onOffLib, line 70
        customParseOnOffCluster(descMap) // library marker kkossev.onOffLib, line 71
    } // library marker kkossev.onOffLib, line 72
    else */ // library marker kkossev.onOffLib, line 73
    if (descMap.attrId == '0000') { // library marker kkossev.onOffLib, line 74
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.onOffLib, line 75
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.onOffLib, line 76
        sendSwitchEvent(rawValue) // library marker kkossev.onOffLib, line 77
    } // library marker kkossev.onOffLib, line 78
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.onOffLib, line 79
        parseOnOffAttributes(descMap) // library marker kkossev.onOffLib, line 80
    } // library marker kkossev.onOffLib, line 81
    else { // library marker kkossev.onOffLib, line 82
        if (descMap.attrId != null) { logWarn "standardParseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.onOffLib, line 83
        else { logDebug "standardParseOnOffCluster: skipped processing OnOff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.onOffLib, line 84
    } // library marker kkossev.onOffLib, line 85
} // library marker kkossev.onOffLib, line 86

void toggleX() { // library marker kkossev.onOffLib, line 88
    String descriptionText = 'central button switch is ' // library marker kkossev.onOffLib, line 89
    String state = '' // library marker kkossev.onOffLib, line 90
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.onOffLib, line 91
        state = 'on' // library marker kkossev.onOffLib, line 92
    } // library marker kkossev.onOffLib, line 93
    else { // library marker kkossev.onOffLib, line 94
        state = 'off' // library marker kkossev.onOffLib, line 95
    } // library marker kkossev.onOffLib, line 96
    descriptionText += state // library marker kkossev.onOffLib, line 97
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.onOffLib, line 98
    logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 99
} // library marker kkossev.onOffLib, line 100

void off() { // library marker kkossev.onOffLib, line 102
    if (this.respondsTo('customOff')) { customOff() ; return  } // library marker kkossev.onOffLib, line 103
    if ((settings?.alwaysOn ?: false) == true) { logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" ; return } // library marker kkossev.onOffLib, line 104
    List<String> cmds = [] // library marker kkossev.onOffLib, line 105
    // added 06/29/2024 - control Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 106
    if (this.respondsTo(getDEVICE)) {   // defined in deviceProfileLib // library marker kkossev.onOffLib, line 107
        Map switchMap = getAttributesMap('switch') // library marker kkossev.onOffLib, line 108
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  0  : 1 // library marker kkossev.onOffLib, line 109
        if (switchMap != null && switchMap != [:]) { // library marker kkossev.onOffLib, line 110
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue) // library marker kkossev.onOffLib, line 111
            logTrace "off() Tuya cmds=${cmds}" // library marker kkossev.onOffLib, line 112
        } // library marker kkossev.onOffLib, line 113
    } // library marker kkossev.onOffLib, line 114
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 115
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.onOffLib, line 116
    } // library marker kkossev.onOffLib, line 117

    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 119
    logDebug "off() currentState=${currentState}" // library marker kkossev.onOffLib, line 120
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 121
        if (currentState == 'off') { // library marker kkossev.onOffLib, line 122
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 123
        } // library marker kkossev.onOffLib, line 124
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.onOffLib, line 125
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 126
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 127
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 128
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 129
    } // library marker kkossev.onOffLib, line 130
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 131
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 132
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 133
} // library marker kkossev.onOffLib, line 134

void on() { // library marker kkossev.onOffLib, line 136
    if (this.respondsTo('customOn')) { customOn() ; return } // library marker kkossev.onOffLib, line 137
    List<String> cmds = [] // library marker kkossev.onOffLib, line 138
    // added 06/29/2024 - control Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 139
    if (this.respondsTo(getDEVICE)) {   // defined in deviceProfileLib // library marker kkossev.onOffLib, line 140
        Map switchMap = getAttributesMap('switch') // library marker kkossev.onOffLib, line 141
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  1  : 0 // library marker kkossev.onOffLib, line 142
        if (switchMap != null && switchMap != [:]) { // library marker kkossev.onOffLib, line 143
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue) // library marker kkossev.onOffLib, line 144
            logTrace "on() Tuya cmds=${cmds}" // library marker kkossev.onOffLib, line 145
        } // library marker kkossev.onOffLib, line 146
    } // library marker kkossev.onOffLib, line 147
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 148
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.onOffLib, line 149
    } // library marker kkossev.onOffLib, line 150
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 151
    logDebug "on() currentState=${currentState}" // library marker kkossev.onOffLib, line 152
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 153
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.onOffLib, line 154
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 155
        } // library marker kkossev.onOffLib, line 156
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.onOffLib, line 157
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 158
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 159
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 160
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 161
    } // library marker kkossev.onOffLib, line 162
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 163
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 164
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 165
} // library marker kkossev.onOffLib, line 166

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.onOffLib, line 168
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.onOffLib, line 169
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.onOffLib, line 170
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.onOffLib, line 171
    } // library marker kkossev.onOffLib, line 172
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.onOffLib, line 173
    Map map = [:] // library marker kkossev.onOffLib, line 174
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.onOffLib, line 175
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.onOffLib, line 176
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.onOffLib, line 177
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false)) && !isRefresh) { // library marker kkossev.onOffLib, line 178
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.onOffLib, line 179
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 180
        return // library marker kkossev.onOffLib, line 181
    } // library marker kkossev.onOffLib, line 182
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.onOffLib, line 183
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.onOffLib, line 184
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.onOffLib, line 185
    if (lastSwitch != value) { // library marker kkossev.onOffLib, line 186
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.onOffLib, line 187
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 188
        state.states['lastSwitch'] = value // library marker kkossev.onOffLib, line 189
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 190
    } else { // library marker kkossev.onOffLib, line 191
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 192
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 193
    } // library marker kkossev.onOffLib, line 194
    map.name = 'switch' // library marker kkossev.onOffLib, line 195
    map.value = value // library marker kkossev.onOffLib, line 196
    if (isRefresh) { // library marker kkossev.onOffLib, line 197
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.onOffLib, line 198
        map.isStateChange = true // library marker kkossev.onOffLib, line 199
    } else { // library marker kkossev.onOffLib, line 200
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.onOffLib, line 201
    } // library marker kkossev.onOffLib, line 202
    logInfo "${map.descriptionText}" // library marker kkossev.onOffLib, line 203
    sendEvent(map) // library marker kkossev.onOffLib, line 204
    clearIsDigital() // library marker kkossev.onOffLib, line 205
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.onOffLib, line 206
        customSwitchEventPostProcesing(map) // library marker kkossev.onOffLib, line 207
    } // library marker kkossev.onOffLib, line 208
} // library marker kkossev.onOffLib, line 209

void parseOnOffAttributes(final Map it) { // library marker kkossev.onOffLib, line 211
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 212
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.onOffLib, line 213
    String mode // library marker kkossev.onOffLib, line 214
    String attrName // library marker kkossev.onOffLib, line 215
    if (it.value == null) { // library marker kkossev.onOffLib, line 216
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.onOffLib, line 217
        return // library marker kkossev.onOffLib, line 218
    } // library marker kkossev.onOffLib, line 219
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.onOffLib, line 220
    switch (it.attrId) { // library marker kkossev.onOffLib, line 221
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.onOffLib, line 222
            attrName = 'Global Scene Control' // library marker kkossev.onOffLib, line 223
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.onOffLib, line 224
            break // library marker kkossev.onOffLib, line 225
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.onOffLib, line 226
            attrName = 'On Time' // library marker kkossev.onOffLib, line 227
            mode = value // library marker kkossev.onOffLib, line 228
            break // library marker kkossev.onOffLib, line 229
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.onOffLib, line 230
            attrName = 'Off Wait Time' // library marker kkossev.onOffLib, line 231
            mode = value // library marker kkossev.onOffLib, line 232
            break // library marker kkossev.onOffLib, line 233
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.onOffLib, line 234
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 235
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.onOffLib, line 236
            break // library marker kkossev.onOffLib, line 237
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.onOffLib, line 238
            attrName = 'Child Lock' // library marker kkossev.onOffLib, line 239
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.onOffLib, line 240
            break // library marker kkossev.onOffLib, line 241
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.onOffLib, line 242
            attrName = 'LED mode' // library marker kkossev.onOffLib, line 243
            if (isCircuitBreaker()) { // library marker kkossev.onOffLib, line 244
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.onOffLib, line 245
            } // library marker kkossev.onOffLib, line 246
            else { // library marker kkossev.onOffLib, line 247
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.onOffLib, line 248
            } // library marker kkossev.onOffLib, line 249
            break // library marker kkossev.onOffLib, line 250
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.onOffLib, line 251
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 252
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.onOffLib, line 253
            break // library marker kkossev.onOffLib, line 254
        case '8003' : //  Over current alarm // library marker kkossev.onOffLib, line 255
            attrName = 'Over current alarm' // library marker kkossev.onOffLib, line 256
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.onOffLib, line 257
            break // library marker kkossev.onOffLib, line 258
        default : // library marker kkossev.onOffLib, line 259
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 260
            return // library marker kkossev.onOffLib, line 261
    } // library marker kkossev.onOffLib, line 262
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.onOffLib, line 263
} // library marker kkossev.onOffLib, line 264

List<String> onOffRefresh() { // library marker kkossev.onOffLib, line 266
    logDebug 'onOffRefresh()' // library marker kkossev.onOffLib, line 267
    List<String> cmds = zigbee.readAttribute(0x0006, 0x0000, [:], delay = 100) // library marker kkossev.onOffLib, line 268
    return cmds // library marker kkossev.onOffLib, line 269
} // library marker kkossev.onOffLib, line 270

void onOfInitializeVars( boolean fullInit = false ) { // library marker kkossev.onOffLib, line 272
    logDebug "onOfInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.onOffLib, line 273
    if (fullInit || settings?.ignoreDuplicated == null) { device.updateSetting('ignoreDuplicated', true) } // library marker kkossev.onOffLib, line 274
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.onOffLib, line 275
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.onOffLib, line 276
} // library marker kkossev.onOffLib, line 277

// ~~~~~ end include (176) kkossev.onOffLib ~~~~~

// ~~~~~ start include (171) kkossev.batteryLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoJavaUtilDate, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
library( // library marker kkossev.batteryLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Battery Library', name: 'batteryLib', namespace: 'kkossev', // library marker kkossev.batteryLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/batteryLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-batteryLib', // library marker kkossev.batteryLib, line 4
    version: '3.2.3' // library marker kkossev.batteryLib, line 5
) // library marker kkossev.batteryLib, line 6
/* // library marker kkossev.batteryLib, line 7
 *  Zigbee Battery Library // library marker kkossev.batteryLib, line 8
 * // library marker kkossev.batteryLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.batteryLib, line 10
 * // library marker kkossev.batteryLib, line 11
 * ver. 3.0.0  2024-04-06 kkossev  - added batteryLib.groovy // library marker kkossev.batteryLib, line 12
 * ver. 3.0.1  2024-04-06 kkossev  - customParsePowerCluster bug fix // library marker kkossev.batteryLib, line 13
 * ver. 3.0.2  2024-04-14 kkossev  - batteryPercentage bug fix (was x2); added bVoltCtr; added battertRefresh // library marker kkossev.batteryLib, line 14
 * ver. 3.2.0  2024-05-21 kkossev  - commonLib 3.2.0 allignment; added lastBattery; added handleTuyaBatteryLevel // library marker kkossev.batteryLib, line 15
 * ver. 3.2.1  2024-07-06 kkossev  - added tuyaToBatteryLevel and handleTuyaBatteryLevel; added batteryInitializeVars // library marker kkossev.batteryLib, line 16
 * ver. 3.2.2  2024-07-18 kkossev  - added BatteryVoltage and BatteryDelay device capability checks // library marker kkossev.batteryLib, line 17
 * ver. 3.2.3  2025-07-13 kkossev  - bug fix: corrected runIn method name from 'sendDelayedBatteryEvent' to 'sendDelayedBatteryPercentageEvent' // library marker kkossev.batteryLib, line 18
 * // library marker kkossev.batteryLib, line 19
 *                                   TODO: add an Advanced Option resetBatteryToZeroWhenOffline // library marker kkossev.batteryLib, line 20
 *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.batteryLib, line 21
*/ // library marker kkossev.batteryLib, line 22

static String batteryLibVersion()   { '3.2.3' } // library marker kkossev.batteryLib, line 24
static String batteryLibStamp() { '2025/07/13 7:45 PM' } // library marker kkossev.batteryLib, line 25

metadata { // library marker kkossev.batteryLib, line 27
    capability 'Battery' // library marker kkossev.batteryLib, line 28
    attribute  'batteryVoltage', 'number' // library marker kkossev.batteryLib, line 29
    attribute  'lastBattery', 'date'         // last battery event time - added in 3.2.0 05/21/2024 // library marker kkossev.batteryLib, line 30
    // no commands // library marker kkossev.batteryLib, line 31
    preferences { // library marker kkossev.batteryLib, line 32
        if (device && advancedOptions == true) { // library marker kkossev.batteryLib, line 33
            if ('BatteryVoltage' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 34
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: 'Convert battery voltage to battery Percentage remaining.' // library marker kkossev.batteryLib, line 35
            } // library marker kkossev.batteryLib, line 36
            if ('BatteryDelay' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 37
                input(name: 'batteryDelay', type: 'enum', title: '<b>Battery Events Delay</b>', description:'Select the Battery Events Delay<br>(default is <b>no delay</b>)', options: DelayBatteryOpts.options, defaultValue: DelayBatteryOpts.defaultValue) // library marker kkossev.batteryLib, line 38
            } // library marker kkossev.batteryLib, line 39
        } // library marker kkossev.batteryLib, line 40
    } // library marker kkossev.batteryLib, line 41
} // library marker kkossev.batteryLib, line 42

@Field static final Map DelayBatteryOpts = [ defaultValue: 0, options: [0: 'No delay', 30: '30 seconds', 3600: '1 hour', 14400: '4 hours', 28800: '8 hours', 43200: '12 hours']] // library marker kkossev.batteryLib, line 44

public void standardParsePowerCluster(final Map descMap) { // library marker kkossev.batteryLib, line 46
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.batteryLib, line 47
    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.batteryLib, line 48
    if (descMap.attrId == '0020') { // battery voltage // library marker kkossev.batteryLib, line 49
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 50
        state.stats['bVoltCtr'] = (state.stats['bVoltCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 51
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.batteryLib, line 52
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.batteryLib, line 53
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.batteryLib, line 54
        } // library marker kkossev.batteryLib, line 55
    } // library marker kkossev.batteryLib, line 56
    else if (descMap.attrId == '0021') { // battery percentage // library marker kkossev.batteryLib, line 57
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 58
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 59
        if (isTuya()) { // library marker kkossev.batteryLib, line 60
            sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 61
        } // library marker kkossev.batteryLib, line 62
        else { // library marker kkossev.batteryLib, line 63
            sendBatteryPercentageEvent((rawValue / 2) as int) // library marker kkossev.batteryLib, line 64
        } // library marker kkossev.batteryLib, line 65
    } // library marker kkossev.batteryLib, line 66
    else { // library marker kkossev.batteryLib, line 67
        logWarn "customParsePowerCluster: zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.batteryLib, line 68
    } // library marker kkossev.batteryLib, line 69
} // library marker kkossev.batteryLib, line 70

public void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.batteryLib, line 72
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.batteryLib, line 73
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 74
    Map result = [:] // library marker kkossev.batteryLib, line 75
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.batteryLib, line 76
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.batteryLib, line 77
        BigDecimal minVolts = 2.2 // library marker kkossev.batteryLib, line 78
        BigDecimal maxVolts = 3.2 // library marker kkossev.batteryLib, line 79
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.batteryLib, line 80
        int roundedPct = Math.round(pct * 100) // library marker kkossev.batteryLib, line 81
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.batteryLib, line 82
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.batteryLib, line 83
        if (convertToPercent == true) { // library marker kkossev.batteryLib, line 84
            result.value = Math.min(100, roundedPct) // library marker kkossev.batteryLib, line 85
            result.name = 'battery' // library marker kkossev.batteryLib, line 86
            result.unit  = '%' // library marker kkossev.batteryLib, line 87
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.batteryLib, line 88
        } // library marker kkossev.batteryLib, line 89
        else { // library marker kkossev.batteryLib, line 90
            result.value = volts // library marker kkossev.batteryLib, line 91
            result.name = 'batteryVoltage' // library marker kkossev.batteryLib, line 92
            result.unit  = 'V' // library marker kkossev.batteryLib, line 93
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.batteryLib, line 94
        } // library marker kkossev.batteryLib, line 95
        result.type = 'physical' // library marker kkossev.batteryLib, line 96
        result.isStateChange = true // library marker kkossev.batteryLib, line 97
        logInfo "${result.descriptionText}" // library marker kkossev.batteryLib, line 98
        sendEvent(result) // library marker kkossev.batteryLib, line 99
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 100
    } // library marker kkossev.batteryLib, line 101
    else { // library marker kkossev.batteryLib, line 102
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.batteryLib, line 103
    } // library marker kkossev.batteryLib, line 104
} // library marker kkossev.batteryLib, line 105

public void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.batteryLib, line 107
    if ((batteryPercent as int) == 255) { // library marker kkossev.batteryLib, line 108
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.batteryLib, line 109
        return // library marker kkossev.batteryLib, line 110
    } // library marker kkossev.batteryLib, line 111
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 112
    Map map = [:] // library marker kkossev.batteryLib, line 113
    map.name = 'battery' // library marker kkossev.batteryLib, line 114
    map.timeStamp = now() // library marker kkossev.batteryLib, line 115
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.batteryLib, line 116
    map.unit  = '%' // library marker kkossev.batteryLib, line 117
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.batteryLib, line 118
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.batteryLib, line 119
    map.isStateChange = true // library marker kkossev.batteryLib, line 120
    // // library marker kkossev.batteryLib, line 121
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.batteryLib, line 122
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.batteryLib, line 123
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.batteryLib, line 124
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.batteryLib, line 125
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.batteryLib, line 126
        // send it now! // library marker kkossev.batteryLib, line 127
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.batteryLib, line 128
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 129
    } // library marker kkossev.batteryLib, line 130
    else { // library marker kkossev.batteryLib, line 131
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.batteryLib, line 132
        map.delayed = delayedTime // library marker kkossev.batteryLib, line 133
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.batteryLib, line 134
        map.lastBattery = lastBattery // library marker kkossev.batteryLib, line 135
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.batteryLib, line 136
        runIn(delayedTime, 'sendDelayedBatteryPercentageEvent', [overwrite: true, data: map]) // library marker kkossev.batteryLib, line 137
    } // library marker kkossev.batteryLib, line 138
} // library marker kkossev.batteryLib, line 139

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.batteryLib, line 141
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 142
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 143
    sendEvent(map) // library marker kkossev.batteryLib, line 144
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 145
} // library marker kkossev.batteryLib, line 146

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.batteryLib, line 148
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.batteryLib, line 149
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 150
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 151
    sendEvent(map) // library marker kkossev.batteryLib, line 152
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 153
} // library marker kkossev.batteryLib, line 154

public int tuyaToBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 156
    int rawValue = fncmd // library marker kkossev.batteryLib, line 157
    switch (fncmd) { // library marker kkossev.batteryLib, line 158
        case 0: rawValue = 100; break // Battery Full // library marker kkossev.batteryLib, line 159
        case 1: rawValue = 75;  break // Battery High // library marker kkossev.batteryLib, line 160
        case 2: rawValue = 50;  break // Battery Medium // library marker kkossev.batteryLib, line 161
        case 3: rawValue = 25;  break // Battery Low // library marker kkossev.batteryLib, line 162
        case 4: rawValue = 100; break // Tuya 3 in 1 -> USB powered // library marker kkossev.batteryLib, line 163
        // for all other values >4 we will use the raw value, expected to be the real battery level 4..100% // library marker kkossev.batteryLib, line 164
    } // library marker kkossev.batteryLib, line 165
    return rawValue // library marker kkossev.batteryLib, line 166
} // library marker kkossev.batteryLib, line 167

public void handleTuyaBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 169
    int rawValue = tuyaToBatteryLevel(fncmd) // library marker kkossev.batteryLib, line 170
    sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 171
} // library marker kkossev.batteryLib, line 172

public void batteryInitializeVars( boolean fullInit = false ) { // library marker kkossev.batteryLib, line 174
    logDebug "batteryInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.batteryLib, line 175
    if (device.hasCapability('Battery')) { // library marker kkossev.batteryLib, line 176
        if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.batteryLib, line 177
        if (fullInit || settings?.batteryDelay == null) { device.updateSetting('batteryDelay', [value: DelayBatteryOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.batteryLib, line 178
    } // library marker kkossev.batteryLib, line 179
} // library marker kkossev.batteryLib, line 180

public List<String> batteryRefresh() { // library marker kkossev.batteryLib, line 182
    List<String> cmds = [] // library marker kkossev.batteryLib, line 183
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 100)         // battery voltage // library marker kkossev.batteryLib, line 184
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 100)         // battery percentage // library marker kkossev.batteryLib, line 185
    return cmds // library marker kkossev.batteryLib, line 186
} // library marker kkossev.batteryLib, line 187

// ~~~~~ end include (171) kkossev.batteryLib ~~~~~

// ~~~~~ start include (172) kkossev.temperatureLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.temperatureLib, line 1
library( // library marker kkossev.temperatureLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Temperature Library', name: 'temperatureLib', namespace: 'kkossev', // library marker kkossev.temperatureLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/temperatureLib.groovy', documentationLink: '', // library marker kkossev.temperatureLib, line 4
    version: '3.3.0' // library marker kkossev.temperatureLib, line 5
) // library marker kkossev.temperatureLib, line 6
/* // library marker kkossev.temperatureLib, line 7
 *  Zigbee Temperature Library // library marker kkossev.temperatureLib, line 8
 * // library marker kkossev.temperatureLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.temperatureLib, line 10
 * // library marker kkossev.temperatureLib, line 11
 * ver. 3.0.0  2024-04-06 kkossev  - added temperatureLib.groovy // library marker kkossev.temperatureLib, line 12
 * ver. 3.0.1  2024-04-19 kkossev  - temperature rounding fix // library marker kkossev.temperatureLib, line 13
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0 allignment; added temperatureRefresh() // library marker kkossev.temperatureLib, line 14
 * ver. 3.2.1  2024-06-07 kkossev  - excluded maxReportingTime for mmWaveSensor and Thermostat // library marker kkossev.temperatureLib, line 15
 * ver. 3.2.2  2024-07-06 kkossev  - fixed T/H clusters attribute different than 0 (temperature, humidity MeasuredValue) bug // library marker kkossev.temperatureLib, line 16
 * ver. 3.2.3  2024-07-18 kkossev  - added 'ReportingConfiguration' capability check for minReportingTime and maxReportingTime // library marker kkossev.temperatureLib, line 17
 * ver. 3.3.0  2025-09-15 kkossev  - (dev. branch) commonLib 4.0.0 allignment; added temperatureOffset // library marker kkossev.temperatureLib, line 18
 * // library marker kkossev.temperatureLib, line 19
 *                                   TODO: unschedule('sendDelayedTempEvent') only if needed (add boolean flag to sendDelayedTempEvent()) // library marker kkossev.temperatureLib, line 20
 *                                   TODO: check for negative temperature values in standardParseTemperatureCluster() // library marker kkossev.temperatureLib, line 21
*/ // library marker kkossev.temperatureLib, line 22

static String temperatureLibVersion()   { '3.3.0' } // library marker kkossev.temperatureLib, line 24
static String temperatureLibStamp() { '2025/09/15 7:54 PM' } // library marker kkossev.temperatureLib, line 25

metadata { // library marker kkossev.temperatureLib, line 27
    capability 'TemperatureMeasurement' // library marker kkossev.temperatureLib, line 28
    // no commands // library marker kkossev.temperatureLib, line 29
    preferences { // library marker kkossev.temperatureLib, line 30
        if (device && advancedOptions == true) { // library marker kkossev.temperatureLib, line 31
            if ('ReportingConfiguration' in DEVICE?.capabilities) { // library marker kkossev.temperatureLib, line 32
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: 'Minimum reporting interval, seconds <i>(1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 33
                if (!(deviceType in ['mmWaveSensor', 'Thermostat', 'TRV'])) { // library marker kkossev.temperatureLib, line 34
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: 'Maximum reporting interval, seconds <i>(120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.temperatureLib, line 35
                } // library marker kkossev.temperatureLib, line 36
            } // library marker kkossev.temperatureLib, line 37
        } // library marker kkossev.temperatureLib, line 38
        input name: 'temperatureOffset', type: 'decimal', title: '<b>Temperature Offset</b>', description: '<i>Adjust temperature by this many degrees</i>', range: '-100..100', defaultValue: 0 // library marker kkossev.temperatureLib, line 39
   } // library marker kkossev.temperatureLib, line 40
} // library marker kkossev.temperatureLib, line 41

void standardParseTemperatureCluster(final Map descMap) { // library marker kkossev.temperatureLib, line 43
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.temperatureLib, line 44
    if (descMap.attrId == '0000') { // library marker kkossev.temperatureLib, line 45
        int value = hexStrToSignedInt(descMap.value) // library marker kkossev.temperatureLib, line 46
        handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.temperatureLib, line 47
    } // library marker kkossev.temperatureLib, line 48
    else { // library marker kkossev.temperatureLib, line 49
        logWarn "standardParseTemperatureCluster() - unknown attribute ${descMap.attrId} value=${descMap.value}" // library marker kkossev.temperatureLib, line 50
    } // library marker kkossev.temperatureLib, line 51
} // library marker kkossev.temperatureLib, line 52

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.temperatureLib, line 54
    Map eventMap = [:] // library marker kkossev.temperatureLib, line 55
    BigDecimal temperature = safeToBigDecimal(temperaturePar).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 56
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.temperatureLib, line 57
    eventMap.name = 'temperature' // library marker kkossev.temperatureLib, line 58
    if (location.temperatureScale == 'F') { // library marker kkossev.temperatureLib, line 59
        temperature = ((temperature * 1.8) + 32).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 60
        eventMap.unit = '\u00B0F' // library marker kkossev.temperatureLib, line 61
    } // library marker kkossev.temperatureLib, line 62
    else { // library marker kkossev.temperatureLib, line 63
        eventMap.unit = '\u00B0C' // library marker kkossev.temperatureLib, line 64
    } // library marker kkossev.temperatureLib, line 65
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 66
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 67
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.temperatureLib, line 68
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.temperatureLib, line 69
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.temperatureLib, line 70
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.temperatureLib, line 71
        return // library marker kkossev.temperatureLib, line 72
    } // library marker kkossev.temperatureLib, line 73
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.temperatureLib, line 74
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.temperatureLib, line 75
    if (state.states['isRefresh'] == true) { // library marker kkossev.temperatureLib, line 76
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.temperatureLib, line 77
        eventMap.isStateChange = true // library marker kkossev.temperatureLib, line 78
    } // library marker kkossev.temperatureLib, line 79
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.temperatureLib, line 80
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 81
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.temperatureLib, line 82
    if (timeElapsed >= minTime) { // library marker kkossev.temperatureLib, line 83
        logInfo "${eventMap.descriptionText}" // library marker kkossev.temperatureLib, line 84
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.temperatureLib, line 85
        state.lastRx['tempTime'] = now() // library marker kkossev.temperatureLib, line 86
        sendEvent(eventMap) // library marker kkossev.temperatureLib, line 87
    } // library marker kkossev.temperatureLib, line 88
    else {         // queue the event // library marker kkossev.temperatureLib, line 89
        eventMap.type = 'delayed' // library marker kkossev.temperatureLib, line 90
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.temperatureLib, line 91
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.temperatureLib, line 92
    } // library marker kkossev.temperatureLib, line 93
} // library marker kkossev.temperatureLib, line 94

void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.temperatureLib, line 96
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.temperatureLib, line 97
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.temperatureLib, line 98
    sendEvent(eventMap) // library marker kkossev.temperatureLib, line 99
} // library marker kkossev.temperatureLib, line 100

List<String> temperatureLibInitializeDevice() { // library marker kkossev.temperatureLib, line 102
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 103
    cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.temperatureLib, line 104
    logDebug "temperatureLibInitializeDevice() cmds=${cmds}" // library marker kkossev.temperatureLib, line 105
    return cmds // library marker kkossev.temperatureLib, line 106
} // library marker kkossev.temperatureLib, line 107

List<String> temperatureRefresh() { // library marker kkossev.temperatureLib, line 109
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 110
    cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.temperatureLib, line 111
    return cmds // library marker kkossev.temperatureLib, line 112
} // library marker kkossev.temperatureLib, line 113

// ~~~~~ end include (172) kkossev.temperatureLib ~~~~~

// ~~~~~ start include (165) kkossev.xiaomiLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitReturnStatement, LineLength, PublicMethodsBeforeNonPublicMethods, UnnecessaryGetter, UnnecessaryPublicModifier */ // library marker kkossev.xiaomiLib, line 1
library( // library marker kkossev.xiaomiLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Xiaomi Library', name: 'xiaomiLib', namespace: 'kkossev', importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/xiaomiLib.groovy', documentationLink: '', // library marker kkossev.xiaomiLib, line 3
    version: '3.3.0' // library marker kkossev.xiaomiLib, line 4
) // library marker kkossev.xiaomiLib, line 5
/* // library marker kkossev.xiaomiLib, line 6
 *  Xiaomi Library // library marker kkossev.xiaomiLib, line 7
 * // library marker kkossev.xiaomiLib, line 8
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.xiaomiLib, line 9
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.xiaomiLib, line 10
 * // library marker kkossev.xiaomiLib, line 11
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.xiaomiLib, line 12
 * // library marker kkossev.xiaomiLib, line 13
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.xiaomiLib, line 14
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.xiaomiLib, line 15
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.xiaomiLib, line 16
 * // library marker kkossev.xiaomiLib, line 17
 * ver. 1.0.0  2023-09-09 kkossev  - added xiaomiLib // library marker kkossev.xiaomiLib, line 18
 * ver. 1.0.1  2023-11-07 kkossev  - (dev. branch) // library marker kkossev.xiaomiLib, line 19
 * ver. 1.0.2  2024-04-06 kkossev  - (dev. branch) Groovy linting; aqaraCube specific code; // library marker kkossev.xiaomiLib, line 20
 * ver. 1.1.0  2024-06-01 kkossev  - (dev. branch) comonLib 3.2.0 alignmment // library marker kkossev.xiaomiLib, line 21
 * ver. 3.2.2  2024-06-01 kkossev  - (dev. branch) comonLib 3.2.2 alignmment // library marker kkossev.xiaomiLib, line 22
 * ver. 3.3.0  2024-06-23 kkossev  - comonLib 3.3.0 alignmment; added parseXiaomiClusterSingeTag() method // library marker kkossev.xiaomiLib, line 23
 * // library marker kkossev.xiaomiLib, line 24
 *                                   TODO: remove the DEVICE_TYPE dependencies for Bulb, Thermostat, AqaraCube, FP1, TRV_OLD // library marker kkossev.xiaomiLib, line 25
 *                                   TODO: remove the isAqaraXXX  dependencies !! // library marker kkossev.xiaomiLib, line 26
*/ // library marker kkossev.xiaomiLib, line 27

static String xiaomiLibVersion()   { '3.3.0' } // library marker kkossev.xiaomiLib, line 29
static String xiaomiLibStamp() { '2024/06/23 9:36 AM' } // library marker kkossev.xiaomiLib, line 30

boolean isAqaraTVOC_Lib()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 32
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 33
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.xiaomiLib, line 34
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.xiaomiLib, line 35
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.xiaomiLib, line 36

// no metadata for this library! // library marker kkossev.xiaomiLib, line 38

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.xiaomiLib, line 40

// Zigbee Attributes // library marker kkossev.xiaomiLib, line 42
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.xiaomiLib, line 43
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.xiaomiLib, line 44
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.xiaomiLib, line 45
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.xiaomiLib, line 46
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.xiaomiLib, line 47
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.xiaomiLib, line 48
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.xiaomiLib, line 49
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.xiaomiLib, line 50
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.xiaomiLib, line 51
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.xiaomiLib, line 52
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.xiaomiLib, line 53
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.xiaomiLib, line 54
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.xiaomiLib, line 55
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.xiaomiLib, line 56
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.xiaomiLib, line 57

// Xiaomi Tags // library marker kkossev.xiaomiLib, line 59
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.xiaomiLib, line 60
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 61
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.xiaomiLib, line 62
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.xiaomiLib, line 63
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 64
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.xiaomiLib, line 65

// called from parseXiaomiCluster() in the main code, if no customParse is defined // library marker kkossev.xiaomiLib, line 67
// TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 68
// TODO - refactor for Thermostat and Bulb specific code // library marker kkossev.xiaomiLib, line 69
void standardParseXiaomiFCC0Cluster(final Map descMap) { // library marker kkossev.xiaomiLib, line 70
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 71
        logTrace "standardParseXiaomiFCC0Cluster: zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 72
    } // library marker kkossev.xiaomiLib, line 73
    if (DEVICE_TYPE in  ['Thermostat']) { // library marker kkossev.xiaomiLib, line 74
        parseXiaomiClusterThermostatLib(descMap) // library marker kkossev.xiaomiLib, line 75
        return // library marker kkossev.xiaomiLib, line 76
    } // library marker kkossev.xiaomiLib, line 77
    if (DEVICE_TYPE in  ['Bulb']) { // library marker kkossev.xiaomiLib, line 78
        parseXiaomiClusterRgbLib(descMap) // library marker kkossev.xiaomiLib, line 79
        return // library marker kkossev.xiaomiLib, line 80
    } // library marker kkossev.xiaomiLib, line 81
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 82
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 83
    final String funcName = 'standardParseXiaomiFCC0Cluster' // library marker kkossev.xiaomiLib, line 84
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 85
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 86
            if (DEVICE_TYPE in  ['AqaraCube']) { logDebug "standardParseXiaomiFCC0Cluster: AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 87
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 88
            break // library marker kkossev.xiaomiLib, line 89
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 90
            logWarn "${funcName}: unknown attribute - resetting?" // library marker kkossev.xiaomiLib, line 91
            break // library marker kkossev.xiaomiLib, line 92
        case PRESENCE_ATTR_ID:            // 0x0142 FP1 // library marker kkossev.xiaomiLib, line 93
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 94
            parseXiaomiClusterPresence(value) // library marker kkossev.xiaomiLib, line 95
            break // library marker kkossev.xiaomiLib, line 96
        case PRESENCE_ACTIONS_ATTR_ID:    // 0x0143 FP1 // library marker kkossev.xiaomiLib, line 97
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 98
            parseXiaomiClusterPresenceAction(value) // library marker kkossev.xiaomiLib, line 99
            break // library marker kkossev.xiaomiLib, line 100
        case REGION_EVENT_ATTR_ID:        // 0x0151 FP1 // library marker kkossev.xiaomiLib, line 101
            // Region events can be sent fast and furious so buffer them // library marker kkossev.xiaomiLib, line 102
            final Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1]) // library marker kkossev.xiaomiLib, line 103
            final Integer value = HexUtils.hexStringToInt(descMap.value[2..3]) // library marker kkossev.xiaomiLib, line 104
            if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 105
                log.debug "${funcName}: xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 106
            } // library marker kkossev.xiaomiLib, line 107
            if (device.currentValue("region${regionId}") != null) { // library marker kkossev.xiaomiLib, line 108
                RegionUpdateBuffer.get(device.id).put(regionId, value) // library marker kkossev.xiaomiLib, line 109
                runInMillis(REGION_UPDATE_DELAY_MS, 'updateRegions') // library marker kkossev.xiaomiLib, line 110
            } // library marker kkossev.xiaomiLib, line 111
            break // library marker kkossev.xiaomiLib, line 112
        case SENSITIVITY_LEVEL_ATTR_ID:   // 0x010C FP1 // library marker kkossev.xiaomiLib, line 113
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 114
            log.info "sensitivity level is '${SensitivityLevelOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 115
            device.updateSetting('sensitivityLevel', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 116
            break // library marker kkossev.xiaomiLib, line 117
        case TRIGGER_DISTANCE_ATTR_ID:    // 0x0146 FP1 // library marker kkossev.xiaomiLib, line 118
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 119
            log.info "approach distance is '${ApproachDistanceOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 120
            device.updateSetting('approachDistance', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 121
            break // library marker kkossev.xiaomiLib, line 122
        case DIRECTION_MODE_ATTR_ID:     // 0x0144 FP1 // library marker kkossev.xiaomiLib, line 123
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 124
            log.info "monitoring direction mode is '${DirectionModeOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 125
            device.updateSetting('directionMode', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 126
            break // library marker kkossev.xiaomiLib, line 127
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode // library marker kkossev.xiaomiLib, line 128
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 129
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 130
            break // library marker kkossev.xiaomiLib, line 131
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 132
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 133
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 134
            break // library marker kkossev.xiaomiLib, line 135
        case XIAOMI_SPECIAL_REPORT_ID:   // 0x00F7 sent every 55 minutes // library marker kkossev.xiaomiLib, line 136
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value) // library marker kkossev.xiaomiLib, line 137
            parseXiaomiClusterTags(tags) // library marker kkossev.xiaomiLib, line 138
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 139
                sendZigbeeCommands(customRefresh()) // library marker kkossev.xiaomiLib, line 140
            } // library marker kkossev.xiaomiLib, line 141
            break // library marker kkossev.xiaomiLib, line 142
        case XIAOMI_RAW_ATTR_ID:        // 0xFFF2 FP1 // library marker kkossev.xiaomiLib, line 143
            final byte[] rawData = HexUtils.hexStringToByteArray(descMap.value) // library marker kkossev.xiaomiLib, line 144
            if (rawData.size() == 24 && settings.enableDistanceDirection) { // library marker kkossev.xiaomiLib, line 145
                final int degrees = rawData[19] // library marker kkossev.xiaomiLib, line 146
                final int distanceCm = (rawData[17] << 8) | (rawData[18] & 0x00ff) // library marker kkossev.xiaomiLib, line 147
                if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 148
                    log.debug "location ${degrees}&deg;, ${distanceCm}cm" // library marker kkossev.xiaomiLib, line 149
                } // library marker kkossev.xiaomiLib, line 150
                runIn(1, 'updateLocation', [ data: [ degrees: degrees, distanceCm: distanceCm ] ]) // library marker kkossev.xiaomiLib, line 151
            } // library marker kkossev.xiaomiLib, line 152
            break // library marker kkossev.xiaomiLib, line 153
        default: // library marker kkossev.xiaomiLib, line 154
            log.warn "${funcName}: zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 155
            break // library marker kkossev.xiaomiLib, line 156
    } // library marker kkossev.xiaomiLib, line 157
} // library marker kkossev.xiaomiLib, line 158

// cluster 0xFCC0 attribute  0x00F7 is sent as a keep-alive beakon every 55 minutes // library marker kkossev.xiaomiLib, line 160
public void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 161
    final String funcName = 'parseXiaomiClusterTags' // library marker kkossev.xiaomiLib, line 162
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 163
        parseXiaomiClusterSingeTag(tag, value) // library marker kkossev.xiaomiLib, line 164
    } // library marker kkossev.xiaomiLib, line 165
} // library marker kkossev.xiaomiLib, line 166

public void parseXiaomiClusterSingeTag(final Integer tag, final Object value) { // library marker kkossev.xiaomiLib, line 168
    final String funcName = 'parseXiaomiClusterSingeTag' // library marker kkossev.xiaomiLib, line 169
    switch (tag) { // library marker kkossev.xiaomiLib, line 170
        case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 171
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 172
            break // library marker kkossev.xiaomiLib, line 173
        case 0x03: // library marker kkossev.xiaomiLib, line 174
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 175
            break // library marker kkossev.xiaomiLib, line 176
        case 0x05: // library marker kkossev.xiaomiLib, line 177
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 178
            break // library marker kkossev.xiaomiLib, line 179
        case 0x06: // library marker kkossev.xiaomiLib, line 180
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 181
            break // library marker kkossev.xiaomiLib, line 182
        case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 183
            final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 184
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 185
            device.updateDataValue('aqaraVersion', swBuild) // library marker kkossev.xiaomiLib, line 186
            break // library marker kkossev.xiaomiLib, line 187
        case 0x0a: // library marker kkossev.xiaomiLib, line 188
            String nwk = intToHexStr(value as Integer, 2) // library marker kkossev.xiaomiLib, line 189
            if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 190
            String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 191
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 192
            if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 193
                logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 194
                state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 195
                state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 196
            } // library marker kkossev.xiaomiLib, line 197
            break // library marker kkossev.xiaomiLib, line 198
        case 0x0b: // library marker kkossev.xiaomiLib, line 199
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 200
            break // library marker kkossev.xiaomiLib, line 201
        case 0x64: // library marker kkossev.xiaomiLib, line 202
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 203
            // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 204
            break // library marker kkossev.xiaomiLib, line 205
        case 0x65: // library marker kkossev.xiaomiLib, line 206
            if (isAqaraFP1()) { logDebug "${funcName} PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 207
            else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 208
            break // library marker kkossev.xiaomiLib, line 209
        case 0x66: // library marker kkossev.xiaomiLib, line 210
            if (isAqaraFP1()) { logDebug "${funcName} SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 211
            else if (isAqaraTVOC_Lib()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 212
            else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" } // library marker kkossev.xiaomiLib, line 213
            break // library marker kkossev.xiaomiLib, line 214
        case 0x67: // library marker kkossev.xiaomiLib, line 215
            if (isAqaraFP1()) { logDebug "${funcName} DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 216
            else              { logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC: // library marker kkossev.xiaomiLib, line 217
            // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 218
            break // library marker kkossev.xiaomiLib, line 219
        case 0x69: // library marker kkossev.xiaomiLib, line 220
            if (isAqaraFP1()) { logDebug "${funcName} TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 221
            else              { logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 222
            break // library marker kkossev.xiaomiLib, line 223
        case 0x6a: // library marker kkossev.xiaomiLib, line 224
            if (isAqaraFP1()) { logDebug "${funcName} FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 225
            else              { logDebug "${funcName} MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 226
            break // library marker kkossev.xiaomiLib, line 227
        case 0x6b: // library marker kkossev.xiaomiLib, line 228
            if (isAqaraFP1()) { logDebug "${funcName} FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 229
            else              { logDebug "${funcName} MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 230
            break // library marker kkossev.xiaomiLib, line 231
        case 0x95: // library marker kkossev.xiaomiLib, line 232
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 233
            break // library marker kkossev.xiaomiLib, line 234
        case 0x96: // library marker kkossev.xiaomiLib, line 235
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 236
            break // library marker kkossev.xiaomiLib, line 237
        case 0x97: // library marker kkossev.xiaomiLib, line 238
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 239
            break // library marker kkossev.xiaomiLib, line 240
        case 0x98: // library marker kkossev.xiaomiLib, line 241
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 242
            break // library marker kkossev.xiaomiLib, line 243
        case 0x9b: // library marker kkossev.xiaomiLib, line 244
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 245
                logDebug "${funcName} Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})" // library marker kkossev.xiaomiLib, line 246
                sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 247
            } // library marker kkossev.xiaomiLib, line 248
            else { logDebug "${funcName} CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 249
            break // library marker kkossev.xiaomiLib, line 250
        default: // library marker kkossev.xiaomiLib, line 251
            logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 252
    } // library marker kkossev.xiaomiLib, line 253
} // library marker kkossev.xiaomiLib, line 254

/** // library marker kkossev.xiaomiLib, line 256
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 257
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 258
 */ // library marker kkossev.xiaomiLib, line 259
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 260
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 261
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 262
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 263
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 264
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 265
    } // library marker kkossev.xiaomiLib, line 266
    return bigInt // library marker kkossev.xiaomiLib, line 267
} // library marker kkossev.xiaomiLib, line 268

/** // library marker kkossev.xiaomiLib, line 270
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 271
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 272
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 273
 */ // library marker kkossev.xiaomiLib, line 274
private Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 275
    try { // library marker kkossev.xiaomiLib, line 276
        final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 277
        final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 278
        new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 279
            while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 280
                int tag = stream.read() // library marker kkossev.xiaomiLib, line 281
                int dataType = stream.read() // library marker kkossev.xiaomiLib, line 282
                Object value // library marker kkossev.xiaomiLib, line 283
                if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 284
                    int length = stream.read() // library marker kkossev.xiaomiLib, line 285
                    byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 286
                    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 287
                    value = new String(byteArr) // library marker kkossev.xiaomiLib, line 288
                } else { // library marker kkossev.xiaomiLib, line 289
                    int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 290
                    value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 291
                } // library marker kkossev.xiaomiLib, line 292
                results[tag] = value // library marker kkossev.xiaomiLib, line 293
            } // library marker kkossev.xiaomiLib, line 294
        } // library marker kkossev.xiaomiLib, line 295
        return results // library marker kkossev.xiaomiLib, line 296
    } // library marker kkossev.xiaomiLib, line 297
    catch (e) { // library marker kkossev.xiaomiLib, line 298
        if (settings.logEnable) { "${device.displayName} decodeXiaomiTags: ${e}" } // library marker kkossev.xiaomiLib, line 299
        return [:] // library marker kkossev.xiaomiLib, line 300
    } // library marker kkossev.xiaomiLib, line 301
} // library marker kkossev.xiaomiLib, line 302

List<String> refreshXiaomi() { // library marker kkossev.xiaomiLib, line 304
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 305
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.xiaomiLib, line 306
    return cmds // library marker kkossev.xiaomiLib, line 307
} // library marker kkossev.xiaomiLib, line 308

List<String> configureXiaomi() { // library marker kkossev.xiaomiLib, line 310
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 311
    logDebug "configureXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 312
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.xiaomiLib, line 313
    return cmds // library marker kkossev.xiaomiLib, line 314
} // library marker kkossev.xiaomiLib, line 315

List<String> initializeXiaomi() { // library marker kkossev.xiaomiLib, line 317
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 318
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 319
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.xiaomiLib, line 320
    return cmds // library marker kkossev.xiaomiLib, line 321
} // library marker kkossev.xiaomiLib, line 322

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 324
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 325
} // library marker kkossev.xiaomiLib, line 326

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 328
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 329
} // library marker kkossev.xiaomiLib, line 330

List<String> standardAqaraBlackMagic() { // library marker kkossev.xiaomiLib, line 332
    return [] // library marker kkossev.xiaomiLib, line 333
    ///////////////////////////////////////// // library marker kkossev.xiaomiLib, line 334
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 335
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.xiaomiLib, line 336
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.xiaomiLib, line 337
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.xiaomiLib, line 338
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.xiaomiLib, line 339
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.xiaomiLib, line 340
        if (isAqaraTVOC_OLD()) { // library marker kkossev.xiaomiLib, line 341
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.xiaomiLib, line 342
        } // library marker kkossev.xiaomiLib, line 343
        logDebug 'standardAqaraBlackMagic()' // library marker kkossev.xiaomiLib, line 344
    } // library marker kkossev.xiaomiLib, line 345
    return cmds // library marker kkossev.xiaomiLib, line 346
} // library marker kkossev.xiaomiLib, line 347

// ~~~~~ end include (165) kkossev.xiaomiLib ~~~~~

// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NestedBlockDepth, NoDouble, NoFloat, NoWildcardImports, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.deviceProfileLib, line 1
library( // library marker kkossev.deviceProfileLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLib', namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/deviceProfileLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-deviceProfileLib', // library marker kkossev.deviceProfileLib, line 4
    version: '3.5.1' // library marker kkossev.deviceProfileLib, line 5
) // library marker kkossev.deviceProfileLib, line 6
/* // library marker kkossev.deviceProfileLib, line 7
 *  Device Profile Library // library marker kkossev.deviceProfileLib, line 8
 * // library marker kkossev.deviceProfileLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.deviceProfileLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.deviceProfileLib, line 11
 * // library marker kkossev.deviceProfileLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.deviceProfileLib, line 13
 * // library marker kkossev.deviceProfileLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.deviceProfileLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.deviceProfileLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.deviceProfileLib, line 17
 * // library marker kkossev.deviceProfileLib, line 18
 * ver. 1.0.0  2023-11-04 kkossev  - added deviceProfileLib (based on Tuya 4 In 1 driver) // library marker kkossev.deviceProfileLib, line 19
 * ver. 3.0.0  2023-11-27 kkossev  - fixes for use with commonLib; added processClusterAttributeFromDeviceProfile() method; added validateAndFixPreferences() method;  inputIt bug fix; signedInt Preproc method; // library marker kkossev.deviceProfileLib, line 20
 * ver. 3.0.1  2023-12-02 kkossev  - release candidate // library marker kkossev.deviceProfileLib, line 21
 * ver. 3.0.2  2023-12-17 kkossev  - inputIt moved to the preferences section; setfunction replaced by customSetFunction; Groovy Linting; // library marker kkossev.deviceProfileLib, line 22
 * ver. 3.0.4  2024-03-30 kkossev  - more Groovy Linting; processClusterAttributeFromDeviceProfile exception fix; // library marker kkossev.deviceProfileLib, line 23
 * ver. 3.1.0  2024-04-03 kkossev  - more Groovy Linting; deviceProfilesV3, enum pars bug fix; // library marker kkossev.deviceProfileLib, line 24
 * ver. 3.1.1  2024-04-21 kkossev  - deviceProfilesV3 bug fix; tuyaDPs list of maps bug fix; resetPreferencesToDefaults bug fix; // library marker kkossev.deviceProfileLib, line 25
 * ver. 3.1.2  2024-05-05 kkossev  - added isSpammyDeviceProfile() // library marker kkossev.deviceProfileLib, line 26
 * ver. 3.1.3  2024-05-21 kkossev  - skip processClusterAttributeFromDeviceProfile if cluster or attribute or value is missing // library marker kkossev.deviceProfileLib, line 27
 * ver. 3.2.0  2024-05-25 kkossev  - commonLib 3.2.0 allignment; // library marker kkossev.deviceProfileLib, line 28
 * ver. 3.2.1  2024-06-06 kkossev  - Tuya Multi Sensor 4 In 1 (V3) driver allignment (customProcessDeviceProfileEvent); getDeviceProfilesMap bug fix; forcedProfile is always shown in preferences; // library marker kkossev.deviceProfileLib, line 29
 * ver. 3.3.0  2024-06-29 kkossev  - empty preferences bug fix; zclWriteAttribute delay 50 ms; added advanced check in inputIt(); fixed 'Cannot get property 'rw' on null object' bug; fixed enum attributes first event numeric value bug; // library marker kkossev.deviceProfileLib, line 30
 * ver. 3.3.1  2024-07-06 kkossev  - added powerSource event in the initEventsDeviceProfile // library marker kkossev.deviceProfileLib, line 31
 * ver. 3.3.2  2024-08-18 kkossev  - release 3.3.2 // library marker kkossev.deviceProfileLib, line 32
 * ver. 3.3.3  2024-08-18 kkossev  - sendCommand and setPar commands commented out; must be declared in the main driver where really needed // library marker kkossev.deviceProfileLib, line 33
 * ver. 3.3.4  2024-09-28 kkossev  - fixed exceptions in resetPreferencesToDefaults() and initEventsDeviceProfile() // library marker kkossev.deviceProfileLib, line 34
 * ver. 3.4.0  2025-02-02 kkossev  - deviceProfilesV3 optimizations (defaultFingerprint); is2in1() mod // library marker kkossev.deviceProfileLib, line 35
 * ver. 3.4.1  2025-02-02 kkossev  - setPar help improvements; // library marker kkossev.deviceProfileLib, line 36
 * ver. 3.4.2  2025-03-24 kkossev  - added refreshFromConfigureReadList() method; documentation update; getDeviceNameAndProfile uses DEVICE.description instead of deviceJoinName // library marker kkossev.deviceProfileLib, line 37
 * ver. 3.4.3  2025-04-25 kkossev  - HE platfrom version 2.4.1.x decimal preferences patch/workaround. // library marker kkossev.deviceProfileLib, line 38
 * ver. 3.5.0  2025-08-14 kkossev  - zclWriteAttribute() support for forced destinationEndpoint in the attributes map // library marker kkossev.deviceProfileLib, line 39
 * ver. 3.5.1  2025-09-15 kkossev  - (dev. branch)commonLib ver 4.0.0 allignment; log.trace leftover removed;  // library marker kkossev.deviceProfileLib, line 40
 * // library marker kkossev.deviceProfileLib, line 41
 *                                   TODO - remove the 2-in-1 patch ! // library marker kkossev.deviceProfileLib, line 42
 *                                   TODO - add updateStateUnknownDPs (from the 4-in-1 driver) // library marker kkossev.deviceProfileLib, line 43
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 44
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 45
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off) // library marker kkossev.deviceProfileLib, line 46
 *                                   TODO: allow NULL parameters default values in the device profiles // library marker kkossev.deviceProfileLib, line 47
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 48
 * // library marker kkossev.deviceProfileLib, line 49
*/ // library marker kkossev.deviceProfileLib, line 50

static String deviceProfileLibVersion()   { '3.5.1' } // library marker kkossev.deviceProfileLib, line 52
static String deviceProfileLibStamp() { '2025/09/15 1:23 PM' } // library marker kkossev.deviceProfileLib, line 53
import groovy.json.* // library marker kkossev.deviceProfileLib, line 54
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 55
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 56
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 57
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 58

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 60

metadata { // library marker kkossev.deviceProfileLib, line 62
    // no capabilities // library marker kkossev.deviceProfileLib, line 63
    // no attributes // library marker kkossev.deviceProfileLib, line 64
    /* // library marker kkossev.deviceProfileLib, line 65
    // copy the following commands to the main driver, if needed // library marker kkossev.deviceProfileLib, line 66
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 67
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 68
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 69
    ] // library marker kkossev.deviceProfileLib, line 70
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 71
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 72
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 73
    ] // library marker kkossev.deviceProfileLib, line 74
    */ // library marker kkossev.deviceProfileLib, line 75
    preferences { // library marker kkossev.deviceProfileLib, line 76
        if (device) { // library marker kkossev.deviceProfileLib, line 77
            input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 78
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 79
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 80
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 81
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 82
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 83
                        input inputMap // library marker kkossev.deviceProfileLib, line 84
                    } // library marker kkossev.deviceProfileLib, line 85
                } // library marker kkossev.deviceProfileLib, line 86
            } // library marker kkossev.deviceProfileLib, line 87
        } // library marker kkossev.deviceProfileLib, line 88
    } // library marker kkossev.deviceProfileLib, line 89
} // library marker kkossev.deviceProfileLib, line 90

private boolean is2in1() { return getDeviceProfile().startsWith('TS0601_2IN1')  }   // patch! // library marker kkossev.deviceProfileLib, line 92

public String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 94
public Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 95
public Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 96

public List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLib, line 98
    if (deviceProfilesV3 == null) { // library marker kkossev.deviceProfileLib, line 99
        if (deviceProfilesV2 == null) { return [] } // library marker kkossev.deviceProfileLib, line 100
        return deviceProfilesV2.values().description as List<String> // library marker kkossev.deviceProfileLib, line 101
    } // library marker kkossev.deviceProfileLib, line 102
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLib, line 103
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 104
        if ((profileMap.device?.isDepricated ?: false) != true) { // library marker kkossev.deviceProfileLib, line 105
            activeProfiles.add(profileMap.description ?: '---') // library marker kkossev.deviceProfileLib, line 106
        } // library marker kkossev.deviceProfileLib, line 107
    } // library marker kkossev.deviceProfileLib, line 108
    return activeProfiles // library marker kkossev.deviceProfileLib, line 109
} // library marker kkossev.deviceProfileLib, line 110

// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 112

/** // library marker kkossev.deviceProfileLib, line 114
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 115
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 116
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 117
 */ // library marker kkossev.deviceProfileLib, line 118
public String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 119
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 120
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 121
    else { return null } // library marker kkossev.deviceProfileLib, line 122
} // library marker kkossev.deviceProfileLib, line 123

/** // library marker kkossev.deviceProfileLib, line 125
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 126
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 127
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 128
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 129
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 130
 */ // library marker kkossev.deviceProfileLib, line 131
private Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 132
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 133
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 134
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 135
    def preference // library marker kkossev.deviceProfileLib, line 136
    try { // library marker kkossev.deviceProfileLib, line 137
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 138
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 139
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 140
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 141
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 142
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 143
        } // library marker kkossev.deviceProfileLib, line 144
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 145
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 146
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 147
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 148
        } // library marker kkossev.deviceProfileLib, line 149
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 150
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 151
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 152
        } // library marker kkossev.deviceProfileLib, line 153
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 154
    } catch (e) { // library marker kkossev.deviceProfileLib, line 155
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 156
        return [:] // library marker kkossev.deviceProfileLib, line 157
    } // library marker kkossev.deviceProfileLib, line 158
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 159
    return foundMap // library marker kkossev.deviceProfileLib, line 160
} // library marker kkossev.deviceProfileLib, line 161

public Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 163
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 164
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 165
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 166
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 167
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 168
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 169
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 170
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 171
            return foundMap // library marker kkossev.deviceProfileLib, line 172
        } // library marker kkossev.deviceProfileLib, line 173
    } // library marker kkossev.deviceProfileLib, line 174
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 175
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 176
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 177
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 178
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 179
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 180
            return foundMap // library marker kkossev.deviceProfileLib, line 181
        } // library marker kkossev.deviceProfileLib, line 182
    } // library marker kkossev.deviceProfileLib, line 183
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 184
    return [:] // library marker kkossev.deviceProfileLib, line 185
} // library marker kkossev.deviceProfileLib, line 186

/** // library marker kkossev.deviceProfileLib, line 188
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 189
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 190
 */ // library marker kkossev.deviceProfileLib, line 191
public void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 192
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 193
    if (DEVICE == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 194
    Map preferences = DEVICE?.preferences ?: [:] // library marker kkossev.deviceProfileLib, line 195
    if (preferences == null || preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 196
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 197
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 198
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 199
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 200
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 201
            return // continue // library marker kkossev.deviceProfileLib, line 202
        } // library marker kkossev.deviceProfileLib, line 203
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 204
        if (parMap == null || parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 205
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 206
        if (parMap?.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 207
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 208
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 209
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 210
    } // library marker kkossev.deviceProfileLib, line 211
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 212
} // library marker kkossev.deviceProfileLib, line 213

/** // library marker kkossev.deviceProfileLib, line 215
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 216
 * // library marker kkossev.deviceProfileLib, line 217
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 218
 */ // library marker kkossev.deviceProfileLib, line 219
private List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 220
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 221
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 222
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 223
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 224
    } // library marker kkossev.deviceProfileLib, line 225
    return validPars // library marker kkossev.deviceProfileLib, line 226
} // library marker kkossev.deviceProfileLib, line 227

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 229
private def getScaledPreferenceValue(String preference, Map dpMap) {        // TODO - not used ??? // library marker kkossev.deviceProfileLib, line 230
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 231
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 232
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 233
    def scaledValue // library marker kkossev.deviceProfileLib, line 234
    if (value == null) { // library marker kkossev.deviceProfileLib, line 235
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 236
        return null // library marker kkossev.deviceProfileLib, line 237
    } // library marker kkossev.deviceProfileLib, line 238
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 239
        case 'number' : // library marker kkossev.deviceProfileLib, line 240
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 241
            break // library marker kkossev.deviceProfileLib, line 242
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 243
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 244
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 245
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 246
            } // library marker kkossev.deviceProfileLib, line 247
            break // library marker kkossev.deviceProfileLib, line 248
        case 'bool' : // library marker kkossev.deviceProfileLib, line 249
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 250
            break // library marker kkossev.deviceProfileLib, line 251
        case 'enum' : // library marker kkossev.deviceProfileLib, line 252
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 253
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 254
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 255
                return null // library marker kkossev.deviceProfileLib, line 256
            } // library marker kkossev.deviceProfileLib, line 257
            scaledValue = value // library marker kkossev.deviceProfileLib, line 258
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 259
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 260
            } // library marker kkossev.deviceProfileLib, line 261
            break // library marker kkossev.deviceProfileLib, line 262
        default : // library marker kkossev.deviceProfileLib, line 263
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 264
            return null // library marker kkossev.deviceProfileLib, line 265
    } // library marker kkossev.deviceProfileLib, line 266
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 267
    return scaledValue // library marker kkossev.deviceProfileLib, line 268
} // library marker kkossev.deviceProfileLib, line 269

// called from customUpdated() method in the custom driver // library marker kkossev.deviceProfileLib, line 271
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 272
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 273
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 274
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 275
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 276
        return // library marker kkossev.deviceProfileLib, line 277
    } // library marker kkossev.deviceProfileLib, line 278
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 279
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 280
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 281
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 282
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 283
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 284
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 285
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 286
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 287
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 288
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 289
                // scale the value // library marker kkossev.deviceProfileLib, line 290
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 291
            } // library marker kkossev.deviceProfileLib, line 292
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLib, line 293
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLib, line 294
            } // library marker kkossev.deviceProfileLib, line 295
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLib, line 296
        } // library marker kkossev.deviceProfileLib, line 297
        else { logDebug "warning: couldn't find map for preference ${name}" ; return }  // TODO - supress the warning if the preference was boolean true/false // library marker kkossev.deviceProfileLib, line 298
    } // library marker kkossev.deviceProfileLib, line 299
    return // library marker kkossev.deviceProfileLib, line 300
} // library marker kkossev.deviceProfileLib, line 301

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 303
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 304
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 305
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 306
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 307
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 308
} // library marker kkossev.deviceProfileLib, line 309
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 310
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 311
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 312
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 313
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 314
} // library marker kkossev.deviceProfileLib, line 315
int invert(int val) { // library marker kkossev.deviceProfileLib, line 316
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 317
    else { return val } // library marker kkossev.deviceProfileLib, line 318
} // library marker kkossev.deviceProfileLib, line 319

// called from setPar and sendAttribite methods for non-Tuya DPs // library marker kkossev.deviceProfileLib, line 321
private List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 322
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 323
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 324
    Map map = [:] // library marker kkossev.deviceProfileLib, line 325
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 326
    try { // library marker kkossev.deviceProfileLib, line 327
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 328
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 329
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 330
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 331
        map['ep'] = (attributesMap.ep != null && attributesMap.ep != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.ep) as Integer : null // library marker kkossev.deviceProfileLib, line 332
    } // library marker kkossev.deviceProfileLib, line 333
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLib, line 334
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 335
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 336
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLib, line 337
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 338
    } // library marker kkossev.deviceProfileLib, line 339
    if ((map.mfgCode != null && map.mfgCode != '') || (map.ep != null && map.ep != '')) { // library marker kkossev.deviceProfileLib, line 340
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 341
        Map ep = map.ep != null ? ['destEndpoint':map.ep] : [:] // library marker kkossev.deviceProfileLib, line 342
        Map mapOptions = [:] // library marker kkossev.deviceProfileLib, line 343
        if (mfgCode) mapOptions.putAll(mfgCode) // library marker kkossev.deviceProfileLib, line 344
        if (ep) mapOptions.putAll(ep) // library marker kkossev.deviceProfileLib, line 345
        //log.trace "$mapOptions" // library marker kkossev.deviceProfileLib, line 346
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mapOptions, delay = 50) // library marker kkossev.deviceProfileLib, line 347
    } // library marker kkossev.deviceProfileLib, line 348
    else { // library marker kkossev.deviceProfileLib, line 349
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 50) // library marker kkossev.deviceProfileLib, line 350
    } // library marker kkossev.deviceProfileLib, line 351
    return cmds // library marker kkossev.deviceProfileLib, line 352
} // library marker kkossev.deviceProfileLib, line 353

/** // library marker kkossev.deviceProfileLib, line 355
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 356
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 357
 * // library marker kkossev.deviceProfileLib, line 358
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 359
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 360
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 361
 */ // library marker kkossev.deviceProfileLib, line 362
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 363
private def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 364
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 365
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 366
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 367
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 368
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 369
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 370
        case 'number' : // library marker kkossev.deviceProfileLib, line 371
            // TODO - negative values ! // library marker kkossev.deviceProfileLib, line 372
            // TODO - better conversion to integer! // library marker kkossev.deviceProfileLib, line 373
            value = safeToInt(val, 0) // library marker kkossev.deviceProfileLib, line 374
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 375
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 376
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 377
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 378
            } // library marker kkossev.deviceProfileLib, line 379
            else { // library marker kkossev.deviceProfileLib, line 380
                scaledValue = value // library marker kkossev.deviceProfileLib, line 381
            } // library marker kkossev.deviceProfileLib, line 382
            break // library marker kkossev.deviceProfileLib, line 383

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 385
            value = safeToDouble(val, 0.0) // library marker kkossev.deviceProfileLib, line 386
            // scale the value // library marker kkossev.deviceProfileLib, line 387
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 388
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 389
            } // library marker kkossev.deviceProfileLib, line 390
            else { // library marker kkossev.deviceProfileLib, line 391
                scaledValue = value // library marker kkossev.deviceProfileLib, line 392
            } // library marker kkossev.deviceProfileLib, line 393
            break // library marker kkossev.deviceProfileLib, line 394

        case 'bool' : // library marker kkossev.deviceProfileLib, line 396
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 397
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 398
            else { // library marker kkossev.deviceProfileLib, line 399
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 400
                return null // library marker kkossev.deviceProfileLib, line 401
            } // library marker kkossev.deviceProfileLib, line 402
            break // library marker kkossev.deviceProfileLib, line 403
        case 'enum' : // library marker kkossev.deviceProfileLib, line 404
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 405
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 406
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 407
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 408
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 409
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 410
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 411
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 412
            } // library marker kkossev.deviceProfileLib, line 413
            else { // library marker kkossev.deviceProfileLib, line 414
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 415
            } // library marker kkossev.deviceProfileLib, line 416
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 417
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 418
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 419
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 420
                // find the key for the value // library marker kkossev.deviceProfileLib, line 421
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 422
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 423
                if (key == null) { // library marker kkossev.deviceProfileLib, line 424
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 425
                    return null // library marker kkossev.deviceProfileLib, line 426
                } // library marker kkossev.deviceProfileLib, line 427
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 428
            //return null // library marker kkossev.deviceProfileLib, line 429
            } // library marker kkossev.deviceProfileLib, line 430
            break // library marker kkossev.deviceProfileLib, line 431
        default : // library marker kkossev.deviceProfileLib, line 432
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 433
            return null // library marker kkossev.deviceProfileLib, line 434
    } // library marker kkossev.deviceProfileLib, line 435
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 436
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 437
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 438
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 439
        return null // library marker kkossev.deviceProfileLib, line 440
    } // library marker kkossev.deviceProfileLib, line 441
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 442
    return scaledValue // library marker kkossev.deviceProfileLib, line 443
} // library marker kkossev.deviceProfileLib, line 444

/** // library marker kkossev.deviceProfileLib, line 446
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 447
 * // library marker kkossev.deviceProfileLib, line 448
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 449
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 450
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 451
 */ // library marker kkossev.deviceProfileLib, line 452
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 453
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 454
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 455
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 456
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 457
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 458
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 459
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 460
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 461
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 462
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 463
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 464
    if (scaledValue == null) { // library marker kkossev.deviceProfileLib, line 465
        logTrace "$dpMap  ${dpMap.map}" // library marker kkossev.deviceProfileLib, line 466
        String helpTxt = "setPar: invalid parameter ${par} value <b>${val}</b>." // library marker kkossev.deviceProfileLib, line 467
        if (dpMap.min != null && dpMap.max != null) { helpTxt += " Must be in the range ${dpMap.min} to ${dpMap.max}" } // library marker kkossev.deviceProfileLib, line 468
        if (dpMap.map != null) { helpTxt += " Must be one of ${dpMap.map}" } // library marker kkossev.deviceProfileLib, line 469
        logInfo helpTxt // library marker kkossev.deviceProfileLib, line 470
        return false // library marker kkossev.deviceProfileLib, line 471
    } // library marker kkossev.deviceProfileLib, line 472

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 474
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 475
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 476
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 477
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 478
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 479
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 480
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 481
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 482
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 483
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 484
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 485
            return true // library marker kkossev.deviceProfileLib, line 486
        } // library marker kkossev.deviceProfileLib, line 487
        else { // library marker kkossev.deviceProfileLib, line 488
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 489
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 490
        } // library marker kkossev.deviceProfileLib, line 491
    } // library marker kkossev.deviceProfileLib, line 492
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 493
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 494
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 495
        def valMiscType // library marker kkossev.deviceProfileLib, line 496
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 497
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 498
            // find the key for the value // library marker kkossev.deviceProfileLib, line 499
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 500
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 501
            if (key == null) { // library marker kkossev.deviceProfileLib, line 502
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 503
                return false // library marker kkossev.deviceProfileLib, line 504
            } // library marker kkossev.deviceProfileLib, line 505
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 506
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 507
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 508
        } // library marker kkossev.deviceProfileLib, line 509
        else { // library marker kkossev.deviceProfileLib, line 510
            valMiscType = val // library marker kkossev.deviceProfileLib, line 511
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 512
        } // library marker kkossev.deviceProfileLib, line 513
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 514
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 515
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 516
        return true // library marker kkossev.deviceProfileLib, line 517
    } // library marker kkossev.deviceProfileLib, line 518

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 520
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 521

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 523
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 524
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 525
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 526
        // Tuya DP // library marker kkossev.deviceProfileLib, line 527
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 528
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 529
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 530
            return false // library marker kkossev.deviceProfileLib, line 531
        } // library marker kkossev.deviceProfileLib, line 532
        else { // library marker kkossev.deviceProfileLib, line 533
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 534
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 535
            return false // library marker kkossev.deviceProfileLib, line 536
        } // library marker kkossev.deviceProfileLib, line 537
    } // library marker kkossev.deviceProfileLib, line 538
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 539
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 540
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 541
        int signedIntScaled = convertSignedInts(scaledValue, dpMap) // library marker kkossev.deviceProfileLib, line 542
        cmds = zclWriteAttribute(dpMap, signedIntScaled) // library marker kkossev.deviceProfileLib, line 543
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 544
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 545
            return false // library marker kkossev.deviceProfileLib, line 546
        } // library marker kkossev.deviceProfileLib, line 547
    } // library marker kkossev.deviceProfileLib, line 548
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 549
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 550
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 551
    return true // library marker kkossev.deviceProfileLib, line 552
} // library marker kkossev.deviceProfileLib, line 553

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 555
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 556
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 557
public List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 558
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 559
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 560
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 561
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 562
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 563
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 564
        return [] // library marker kkossev.deviceProfileLib, line 565
    } // library marker kkossev.deviceProfileLib, line 566
    String dpType // library marker kkossev.deviceProfileLib, line 567
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 568
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 569
    } // library marker kkossev.deviceProfileLib, line 570
    else { // library marker kkossev.deviceProfileLib, line 571
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 572
    } // library marker kkossev.deviceProfileLib, line 573
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 574
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 575
        return [] // library marker kkossev.deviceProfileLib, line 576
    } // library marker kkossev.deviceProfileLib, line 577
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 578
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 579
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 580
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 581
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 582
    } // library marker kkossev.deviceProfileLib, line 583
    else { // library marker kkossev.deviceProfileLib, line 584
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 585
    } // library marker kkossev.deviceProfileLib, line 586
    return cmds // library marker kkossev.deviceProfileLib, line 587
} // library marker kkossev.deviceProfileLib, line 588

private int convertSignedInts(int val, Map dpMap) { // library marker kkossev.deviceProfileLib, line 590
    if (dpMap.dt == '0x28') { // library marker kkossev.deviceProfileLib, line 591
        if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 592
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 593
    } // library marker kkossev.deviceProfileLib, line 594
    else if (dpMap.dt == '0x29') { // library marker kkossev.deviceProfileLib, line 595
        if (val > 32767) { return (val as int) - 65536 } // library marker kkossev.deviceProfileLib, line 596
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 597
    } // library marker kkossev.deviceProfileLib, line 598
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 599
} // library marker kkossev.deviceProfileLib, line 600

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 602
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 603
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 604
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 605
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 606
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'DEVICE.preferences is empty!' ; return false } // library marker kkossev.deviceProfileLib, line 607

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 609
    l//log.trace "sendAttribute: dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 610
    if (dpMap == null || dpMap?.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 611
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 612
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 613
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 614
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 615
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 616
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 617
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 618
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 619
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 620
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 621
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 622
        try { // library marker kkossev.deviceProfileLib, line 623
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 624
        } // library marker kkossev.deviceProfileLib, line 625
        catch (e) { // library marker kkossev.deviceProfileLib, line 626
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 627
            return false // library marker kkossev.deviceProfileLib, line 628
        } // library marker kkossev.deviceProfileLib, line 629
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 630
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 631
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 632
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 633
            return true // library marker kkossev.deviceProfileLib, line 634
        } // library marker kkossev.deviceProfileLib, line 635
        else { // library marker kkossev.deviceProfileLib, line 636
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 637
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 638
        } // library marker kkossev.deviceProfileLib, line 639
    } // library marker kkossev.deviceProfileLib, line 640
    else { // library marker kkossev.deviceProfileLib, line 641
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 642
    } // library marker kkossev.deviceProfileLib, line 643
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 644
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 645
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 646
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 647
        // patch !! // library marker kkossev.deviceProfileLib, line 648
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 649
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 650
        } // library marker kkossev.deviceProfileLib, line 651
        else { // library marker kkossev.deviceProfileLib, line 652
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 653
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 654
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 655
        } // library marker kkossev.deviceProfileLib, line 656
        return true // library marker kkossev.deviceProfileLib, line 657
    } // library marker kkossev.deviceProfileLib, line 658
    else { // library marker kkossev.deviceProfileLib, line 659
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 660
    } // library marker kkossev.deviceProfileLib, line 661
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 662
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 663
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 664
    try { // library marker kkossev.deviceProfileLib, line 665
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 666
    } // library marker kkossev.deviceProfileLib, line 667
    catch (e) { // library marker kkossev.deviceProfileLib, line 668
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 669
        return false // library marker kkossev.deviceProfileLib, line 670
    } // library marker kkossev.deviceProfileLib, line 671
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 672
        // Tuya DP // library marker kkossev.deviceProfileLib, line 673
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 674
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 675
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 676
            return false // library marker kkossev.deviceProfileLib, line 677
        } // library marker kkossev.deviceProfileLib, line 678
        else { // library marker kkossev.deviceProfileLib, line 679
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 680
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 681
            return true // library marker kkossev.deviceProfileLib, line 682
        } // library marker kkossev.deviceProfileLib, line 683
    } // library marker kkossev.deviceProfileLib, line 684
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 685
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 686
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 687
    } // library marker kkossev.deviceProfileLib, line 688
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 689
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 690
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 691
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 692
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 693
            return false // library marker kkossev.deviceProfileLib, line 694
        } // library marker kkossev.deviceProfileLib, line 695
    } // library marker kkossev.deviceProfileLib, line 696
    else { // library marker kkossev.deviceProfileLib, line 697
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 698
        return false // library marker kkossev.deviceProfileLib, line 699
    } // library marker kkossev.deviceProfileLib, line 700
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 701
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 702
    return true // library marker kkossev.deviceProfileLib, line 703
} // library marker kkossev.deviceProfileLib, line 704

/** // library marker kkossev.deviceProfileLib, line 706
 * SENDS a list of Zigbee commands to be sent to the device. // library marker kkossev.deviceProfileLib, line 707
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 708
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 709
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 710
 */ // library marker kkossev.deviceProfileLib, line 711
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 712
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 713
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 714
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 715
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 716
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 717
    if (supportedCommandsMap == null || supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 718
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 719
        return false // library marker kkossev.deviceProfileLib, line 720
    } // library marker kkossev.deviceProfileLib, line 721
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 722
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 723
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 724
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 725
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 726
        return false // library marker kkossev.deviceProfileLib, line 727
    } // library marker kkossev.deviceProfileLib, line 728
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 729
    def func, funcResult // library marker kkossev.deviceProfileLib, line 730
    try { // library marker kkossev.deviceProfileLib, line 731
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 732
        // added 01/25/2025 : the commands now can be shorted : instead of a map kay and value 'printFingerprints':'printFingerprints' we can skip the value when it is the same:  'printFingerprints:'  - the value is the same as the key // library marker kkossev.deviceProfileLib, line 733
        if (func == null || func == '') { // library marker kkossev.deviceProfileLib, line 734
            func = command // library marker kkossev.deviceProfileLib, line 735
        } // library marker kkossev.deviceProfileLib, line 736
        if (val != null && val != '') { // library marker kkossev.deviceProfileLib, line 737
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 738
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 739
        } // library marker kkossev.deviceProfileLib, line 740
        else { // library marker kkossev.deviceProfileLib, line 741
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 742
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 743
        } // library marker kkossev.deviceProfileLib, line 744
    } // library marker kkossev.deviceProfileLib, line 745
    catch (e) { // library marker kkossev.deviceProfileLib, line 746
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 747
        return false // library marker kkossev.deviceProfileLib, line 748
    } // library marker kkossev.deviceProfileLib, line 749
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 750
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 751
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 752
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 753
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 754
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 755
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 756
        } // library marker kkossev.deviceProfileLib, line 757
    } // library marker kkossev.deviceProfileLib, line 758
    else if (funcResult == null) { // library marker kkossev.deviceProfileLib, line 759
        return false // library marker kkossev.deviceProfileLib, line 760
    } // library marker kkossev.deviceProfileLib, line 761
     else { // library marker kkossev.deviceProfileLib, line 762
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 763
        return false // library marker kkossev.deviceProfileLib, line 764
    } // library marker kkossev.deviceProfileLib, line 765
    return true // library marker kkossev.deviceProfileLib, line 766
} // library marker kkossev.deviceProfileLib, line 767

/** // library marker kkossev.deviceProfileLib, line 769
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 770
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 771
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 772
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 773
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 774
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 775
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 776
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 777
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 778
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 779
 */ // library marker kkossev.deviceProfileLib, line 780
public Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 781
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 782
    Map input = [:] // library marker kkossev.deviceProfileLib, line 783
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 784
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 785
    Object preference // library marker kkossev.deviceProfileLib, line 786
    try { preference = DEVICE?.preferences["$param"] } // library marker kkossev.deviceProfileLib, line 787
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 788
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 789
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } } // library marker kkossev.deviceProfileLib, line 790
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 791
    /* // library marker kkossev.deviceProfileLib, line 792
    // TODO - check if this is neccessary? isTuyaDP is not defined! // library marker kkossev.deviceProfileLib, line 793
    try { isTuyaDP = preference.isNumber() } // library marker kkossev.deviceProfileLib, line 794
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 795
    */ // library marker kkossev.deviceProfileLib, line 796
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 797
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 798
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 799
    if (foundMap == null || foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 800
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 801
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) { // library marker kkossev.deviceProfileLib, line 802
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" } // library marker kkossev.deviceProfileLib, line 803
        return [:] // library marker kkossev.deviceProfileLib, line 804
    } // library marker kkossev.deviceProfileLib, line 805
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 806
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 807
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 808
    //input.description = (foundMap.description ?: foundMap.title)?.replaceAll(/<\/?b>/, '')  // if description is not defined, use the title // library marker kkossev.deviceProfileLib, line 809
    input.description = foundMap.description ?: ''   // if description is not defined, skip it // library marker kkossev.deviceProfileLib, line 810
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 811
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 812
            //input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 813
            input.range = "${Math.floor(foundMap.min) as int}..${Math.ceil(foundMap.max) as int}" // library marker kkossev.deviceProfileLib, line 814
        } // library marker kkossev.deviceProfileLib, line 815
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 816
            if (input.description != '') { input.description += '<br>' } // library marker kkossev.deviceProfileLib, line 817
            input.description += "<i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 818
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 819
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 820
            } // library marker kkossev.deviceProfileLib, line 821
        } // library marker kkossev.deviceProfileLib, line 822
    } // library marker kkossev.deviceProfileLib, line 823
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 824
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 825
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 826
    }/* // library marker kkossev.deviceProfileLib, line 827
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 828
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 829
    }*/ // library marker kkossev.deviceProfileLib, line 830
    else { // library marker kkossev.deviceProfileLib, line 831
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 832
        return [:] // library marker kkossev.deviceProfileLib, line 833
    } // library marker kkossev.deviceProfileLib, line 834
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 835
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 836
    } // library marker kkossev.deviceProfileLib, line 837
    return input // library marker kkossev.deviceProfileLib, line 838
} // library marker kkossev.deviceProfileLib, line 839

/** // library marker kkossev.deviceProfileLib, line 841
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 842
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 843
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 844
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 845
 */ // library marker kkossev.deviceProfileLib, line 846
public List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 847
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 848
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 849
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 850
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 851
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 852
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 853
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 854
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].description ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 855
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 856
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 857
            } // library marker kkossev.deviceProfileLib, line 858
        } // library marker kkossev.deviceProfileLib, line 859
    } // library marker kkossev.deviceProfileLib, line 860
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 861
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 862
    } // library marker kkossev.deviceProfileLib, line 863
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 864
} // library marker kkossev.deviceProfileLib, line 865

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 867
public void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 868
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 869
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 870
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 871
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 872
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 873
    } // library marker kkossev.deviceProfileLib, line 874
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 875
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 876
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 877
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 878
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 879
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 880
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 881
    } else { // library marker kkossev.deviceProfileLib, line 882
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 883
    } // library marker kkossev.deviceProfileLib, line 884
} // library marker kkossev.deviceProfileLib, line 885

public List<String> refreshFromConfigureReadList(List<String> refreshList) { // library marker kkossev.deviceProfileLib, line 887
    logDebug "refreshFromConfigureReadList(${refreshList})" // library marker kkossev.deviceProfileLib, line 888
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 889
    if (refreshList != null && !refreshList.isEmpty()) { // library marker kkossev.deviceProfileLib, line 890
        //List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 891
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 892
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 893
            if (k != null) { // library marker kkossev.deviceProfileLib, line 894
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 895
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 896
                if (map != null) { // library marker kkossev.deviceProfileLib, line 897
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 898
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 899
                } // library marker kkossev.deviceProfileLib, line 900
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 901
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 902
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 903
                } // library marker kkossev.deviceProfileLib, line 904
            } // library marker kkossev.deviceProfileLib, line 905
        } // library marker kkossev.deviceProfileLib, line 906
    } // library marker kkossev.deviceProfileLib, line 907
    return cmds // library marker kkossev.deviceProfileLib, line 908
} // library marker kkossev.deviceProfileLib, line 909

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLib, line 911
public List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLib, line 912
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLib, line 913
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 914
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLib, line 915
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 916
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 917
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 918
            if (k != null) { // library marker kkossev.deviceProfileLib, line 919
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 920
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 921
                if (map != null) { // library marker kkossev.deviceProfileLib, line 922
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 923
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 924
                } // library marker kkossev.deviceProfileLib, line 925
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 926
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 927
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 928
                } // library marker kkossev.deviceProfileLib, line 929
            } // library marker kkossev.deviceProfileLib, line 930
        } // library marker kkossev.deviceProfileLib, line 931
    } // library marker kkossev.deviceProfileLib, line 932
    return cmds // library marker kkossev.deviceProfileLib, line 933
} // library marker kkossev.deviceProfileLib, line 934

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 936
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 937
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 938
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 939
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 940
    return cmds // library marker kkossev.deviceProfileLib, line 941
} // library marker kkossev.deviceProfileLib, line 942

// TODO ! - remove? // library marker kkossev.deviceProfileLib, line 944
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 945
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 946
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 947
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 948
    return cmds // library marker kkossev.deviceProfileLib, line 949
} // library marker kkossev.deviceProfileLib, line 950

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 952
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 953
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 954
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 955
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 956
    return cmds // library marker kkossev.deviceProfileLib, line 957
} // library marker kkossev.deviceProfileLib, line 958

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 960
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 961
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 962
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 963
    } // library marker kkossev.deviceProfileLib, line 964
} // library marker kkossev.deviceProfileLib, line 965

public void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 967
    String ps = DEVICE?.device?.powerSource // library marker kkossev.deviceProfileLib, line 968
    logDebug "initEventsDeviceProfile(${fullInit}) for deviceProfile=${state.deviceProfile} DEVICE?.device?.powerSource=${ps} ps.isEmpty()=${ps?.isEmpty()}" // library marker kkossev.deviceProfileLib, line 969
    if (ps != null && !ps.isEmpty()) { // library marker kkossev.deviceProfileLib, line 970
        sendEvent(name: 'powerSource', value: ps, descriptionText: "Power Source set to '${ps}'", type: 'digital') // library marker kkossev.deviceProfileLib, line 971
    } // library marker kkossev.deviceProfileLib, line 972
} // library marker kkossev.deviceProfileLib, line 973

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 975

// // library marker kkossev.deviceProfileLib, line 977
// called from parse() // library marker kkossev.deviceProfileLib, line 978
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profile // library marker kkossev.deviceProfileLib, line 979
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 980
// // library marker kkossev.deviceProfileLib, line 981
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 982
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 983
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 984
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 985
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 986
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 987
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 988
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 989
} // library marker kkossev.deviceProfileLib, line 990

// // library marker kkossev.deviceProfileLib, line 992
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 993
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profile // library marker kkossev.deviceProfileLib, line 994
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 995
// // library marker kkossev.deviceProfileLib, line 996
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 997
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 998
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 999
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 1000
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 1001
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 1002
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 1003
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 1004
} // library marker kkossev.deviceProfileLib, line 1005

// all DPs are spammy - sent periodically! (this function is not used?) // library marker kkossev.deviceProfileLib, line 1007
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 1008
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 1009
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 1010
    return isSpammy // library marker kkossev.deviceProfileLib, line 1011
} // library marker kkossev.deviceProfileLib, line 1012

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1014
private List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 1015
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 1016
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 1017
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 1018
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1019
    } // library marker kkossev.deviceProfileLib, line 1020
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1021
} // library marker kkossev.deviceProfileLib, line 1022

private List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 1024
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 1025
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1026
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 1027
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1028
    } // library marker kkossev.deviceProfileLib, line 1029
    else { // library marker kkossev.deviceProfileLib, line 1030
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 1031
    } // library marker kkossev.deviceProfileLib, line 1032
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 1033
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1034
} // library marker kkossev.deviceProfileLib, line 1035

private List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 1037
    Double convertedValue // library marker kkossev.deviceProfileLib, line 1038
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1039
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 1040
    } // library marker kkossev.deviceProfileLib, line 1041
    else { // library marker kkossev.deviceProfileLib, line 1042
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1043
    } // library marker kkossev.deviceProfileLib, line 1044
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1045
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1046
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1047
} // library marker kkossev.deviceProfileLib, line 1048

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1050
private List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1051
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1052
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1053
    def convertedValue // library marker kkossev.deviceProfileLib, line 1054
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1055
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1056
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1057
    } // library marker kkossev.deviceProfileLib, line 1058
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1059
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1060
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1061
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1062
            isEqual = false // library marker kkossev.deviceProfileLib, line 1063
        } // library marker kkossev.deviceProfileLib, line 1064
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1065
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1066
        } // library marker kkossev.deviceProfileLib, line 1067
    } // library marker kkossev.deviceProfileLib, line 1068
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1069
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1070
} // library marker kkossev.deviceProfileLib, line 1071

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1073
private List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1074
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1075
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1076
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1077
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1078
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1079
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1080
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1081
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1082
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1083
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1084
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1085
            break // library marker kkossev.deviceProfileLib, line 1086
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1087
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1088
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1089
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1090
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1091
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1092
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1093
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1094
            } // library marker kkossev.deviceProfileLib, line 1095
            else { // library marker kkossev.deviceProfileLib, line 1096
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1097
            } // library marker kkossev.deviceProfileLib, line 1098
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1099
            break // library marker kkossev.deviceProfileLib, line 1100
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1101
        case 'number' : // library marker kkossev.deviceProfileLib, line 1102
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1103
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1104
            break // library marker kkossev.deviceProfileLib, line 1105
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1106
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1107
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1108
            break // library marker kkossev.deviceProfileLib, line 1109
        default : // library marker kkossev.deviceProfileLib, line 1110
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1111
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1112
    } // library marker kkossev.deviceProfileLib, line 1113
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1114
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1115
    } // library marker kkossev.deviceProfileLib, line 1116
    // // library marker kkossev.deviceProfileLib, line 1117
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1118
} // library marker kkossev.deviceProfileLib, line 1119

// // library marker kkossev.deviceProfileLib, line 1121
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1122
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1123
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1124
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1125
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1126
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1127
// // library marker kkossev.deviceProfileLib, line 1128
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1129
// // library marker kkossev.deviceProfileLib, line 1130
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1131
// // library marker kkossev.deviceProfileLib, line 1132
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1133
private List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1134
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1135
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1136
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1137
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1138
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1139
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1140
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1141
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1142
            break // library marker kkossev.deviceProfileLib, line 1143
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1144
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1145
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1146
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1147
            logTrace "latestEvent is ${latestEvent} dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1148
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1149
            if (dataType == null || dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1150
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1151
            } // library marker kkossev.deviceProfileLib, line 1152
            else { // library marker kkossev.deviceProfileLib, line 1153
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1154
            } // library marker kkossev.deviceProfileLib, line 1155
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1156
            break // library marker kkossev.deviceProfileLib, line 1157
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1158
        case 'number' : // library marker kkossev.deviceProfileLib, line 1159
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1160
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1161
            break // library marker kkossev.deviceProfileLib, line 1162
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1163
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1164
            break // library marker kkossev.deviceProfileLib, line 1165
        default : // library marker kkossev.deviceProfileLib, line 1166
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1167
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1168
    } // library marker kkossev.deviceProfileLib, line 1169
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1170
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1171
} // library marker kkossev.deviceProfileLib, line 1172

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1174
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1175
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1176
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1177
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1178
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1179
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1180
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1181
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1182
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1183
    } // library marker kkossev.deviceProfileLib, line 1184
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1185
    try { // library marker kkossev.deviceProfileLib, line 1186
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1187
    } // library marker kkossev.deviceProfileLib, line 1188
    catch (e) { // library marker kkossev.deviceProfileLib, line 1189
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1190
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1191
    } // library marker kkossev.deviceProfileLib, line 1192
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1193
    return fncmd // library marker kkossev.deviceProfileLib, line 1194
} // library marker kkossev.deviceProfileLib, line 1195

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1197
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1198
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1199
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1200
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1201
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1202
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1203

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1205
    if (attribMap == null || attribMap?.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1206

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1208
    int value // library marker kkossev.deviceProfileLib, line 1209
    try { // library marker kkossev.deviceProfileLib, line 1210
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1211
    } // library marker kkossev.deviceProfileLib, line 1212
    catch (e) { // library marker kkossev.deviceProfileLib, line 1213
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1214
        return false // library marker kkossev.deviceProfileLib, line 1215
    } // library marker kkossev.deviceProfileLib, line 1216
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1217
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1218
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1219
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1220
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1221
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1222
        return false // library marker kkossev.deviceProfileLib, line 1223
    } // library marker kkossev.deviceProfileLib, line 1224
    value = convertSignedInts(value, foundItem) // library marker kkossev.deviceProfileLib, line 1225
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1226
} // library marker kkossev.deviceProfileLib, line 1227

/** // library marker kkossev.deviceProfileLib, line 1229
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1230
 * // library marker kkossev.deviceProfileLib, line 1231
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1232
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1233
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1234
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1235
 * // library marker kkossev.deviceProfileLib, line 1236
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1237
 */ // library marker kkossev.deviceProfileLib, line 1238
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1239
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1240
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1241
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1242
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1243

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1245
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1246

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1248
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1249
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1250
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1251
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1252
        return false // library marker kkossev.deviceProfileLib, line 1253
    } // library marker kkossev.deviceProfileLib, line 1254
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1255
} // library marker kkossev.deviceProfileLib, line 1256

/* // library marker kkossev.deviceProfileLib, line 1258
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1259
 */ // library marker kkossev.deviceProfileLib, line 1260
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1261
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1262
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1263
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1264
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1265
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1266
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1267
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1268
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1269
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1270
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1271
        } // library marker kkossev.deviceProfileLib, line 1272
    } // library marker kkossev.deviceProfileLib, line 1273
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1274

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1276
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1277
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1278
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1279
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1280
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences?.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1281
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1282
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1283
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1284
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1285
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1286
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1287
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1288
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1289
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1290
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1291
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1292

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1294
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1295
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1296
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1297
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1298
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1299
            if (settings.logEnable) { logInfo "${descText} (Debug logging is enabled)" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1300
        } // library marker kkossev.deviceProfileLib, line 1301
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1302
    } // library marker kkossev.deviceProfileLib, line 1303

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1305
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1306
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1307
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1308
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1309
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1310
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1311
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1312
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1313
            } // library marker kkossev.deviceProfileLib, line 1314
        } // library marker kkossev.deviceProfileLib, line 1315
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1316
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1317
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1318
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1319
            } // library marker kkossev.deviceProfileLib, line 1320
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1321
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1322
            try { // library marker kkossev.deviceProfileLib, line 1323
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1324
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1325
            } // library marker kkossev.deviceProfileLib, line 1326
            catch (e) { // library marker kkossev.deviceProfileLib, line 1327
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1328
            } // library marker kkossev.deviceProfileLib, line 1329
        } // library marker kkossev.deviceProfileLib, line 1330
    } // library marker kkossev.deviceProfileLib, line 1331
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1332
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1333
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1334
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1335
    } // library marker kkossev.deviceProfileLib, line 1336

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1338
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1339
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1340
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1341
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1342
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1343
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1344
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1345
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1346
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1347
            } // library marker kkossev.deviceProfileLib, line 1348
            if (foundItem.processDuplicated == true) { // library marker kkossev.deviceProfileLib, line 1349
                logDebug 'processDuplicated=true -> continue' // library marker kkossev.deviceProfileLib, line 1350
            } // library marker kkossev.deviceProfileLib, line 1351

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1353
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1354
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1355
            // continue ... // library marker kkossev.deviceProfileLib, line 1356
            } // library marker kkossev.deviceProfileLib, line 1357

            else { // library marker kkossev.deviceProfileLib, line 1359
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1360
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1361
                } // library marker kkossev.deviceProfileLib, line 1362
                else { // library marker kkossev.deviceProfileLib, line 1363
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLib, line 1364
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1365
                } // library marker kkossev.deviceProfileLib, line 1366
            } // library marker kkossev.deviceProfileLib, line 1367
        } // library marker kkossev.deviceProfileLib, line 1368

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1370
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1371
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1372
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1373
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1374
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1375
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1376
        } // library marker kkossev.deviceProfileLib, line 1377
        else { // library marker kkossev.deviceProfileLib, line 1378
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1379
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1380
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1381
                logTrace "event ${name} sent w/ valueScaled ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1382
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1383
            } // library marker kkossev.deviceProfileLib, line 1384
        } // library marker kkossev.deviceProfileLib, line 1385
    } // library marker kkossev.deviceProfileLib, line 1386
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1387
} // library marker kkossev.deviceProfileLib, line 1388

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1390
public boolean validateAndFixPreferences(String debugStr) { return validateAndFixPreferences(debugStr.toBoolean() as boolean) } // library marker kkossev.deviceProfileLib, line 1391
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1392
    //debug = true // library marker kkossev.deviceProfileLib, line 1393
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1394
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLib, line 1395
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLib, line 1396
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1397
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLib, line 1398
    String settingType = '' // library marker kkossev.deviceProfileLib, line 1399
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1400
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1401
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1402
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1403
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1404
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1405
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1406
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1407
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1408
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1409
            try { // library marker kkossev.deviceProfileLib, line 1410
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1411
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1412
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLib, line 1413
            } // library marker kkossev.deviceProfileLib, line 1414
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1415
            try { // library marker kkossev.deviceProfileLib, line 1416
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1417
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1418
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1419
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1420
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1421
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1422
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1423
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1424
                    } // library marker kkossev.deviceProfileLib, line 1425
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1426
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1427
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1428
                    } else { // library marker kkossev.deviceProfileLib, line 1429
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1430
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1431
                    } // library marker kkossev.deviceProfileLib, line 1432
                } // library marker kkossev.deviceProfileLib, line 1433
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1434
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1435
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1436
            } // library marker kkossev.deviceProfileLib, line 1437
            catch (e) { // library marker kkossev.deviceProfileLib, line 1438
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1439
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1440
                try { // library marker kkossev.deviceProfileLib, line 1441
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1442
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1443
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1444
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1445
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1446
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLib, line 1447
                } // library marker kkossev.deviceProfileLib, line 1448
            } // library marker kkossev.deviceProfileLib, line 1449
        } // library marker kkossev.deviceProfileLib, line 1450
        total ++ // library marker kkossev.deviceProfileLib, line 1451
    } // library marker kkossev.deviceProfileLib, line 1452
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1453
    return true // library marker kkossev.deviceProfileLib, line 1454
} // library marker kkossev.deviceProfileLib, line 1455

public String fingerprintIt(Map profileMap, Map fingerprint) { // library marker kkossev.deviceProfileLib, line 1457
    if (profileMap == null) { return 'profileMap is null' } // library marker kkossev.deviceProfileLib, line 1458
    if (fingerprint == null) { return 'fingerprint is null' } // library marker kkossev.deviceProfileLib, line 1459
    Map defaultFingerprint = profileMap.defaultFingerprint ?: [:] // library marker kkossev.deviceProfileLib, line 1460
    // if there is no defaultFingerprint, use the fingerprint as is // library marker kkossev.deviceProfileLib, line 1461
    if (defaultFingerprint == [:]) { // library marker kkossev.deviceProfileLib, line 1462
        return fingerprint.toString() // library marker kkossev.deviceProfileLib, line 1463
    } // library marker kkossev.deviceProfileLib, line 1464
    // for the missing keys, use the default values // library marker kkossev.deviceProfileLib, line 1465
    String fingerprintStr = '' // library marker kkossev.deviceProfileLib, line 1466
    defaultFingerprint.each { key, value -> // library marker kkossev.deviceProfileLib, line 1467
        String keyValue = fingerprint[key] ?: value // library marker kkossev.deviceProfileLib, line 1468
        fingerprintStr += "${key}:'${keyValue}', " // library marker kkossev.deviceProfileLib, line 1469
    } // library marker kkossev.deviceProfileLib, line 1470
    // remove the last comma and space // library marker kkossev.deviceProfileLib, line 1471
    fingerprintStr = fingerprintStr[0..-3] // library marker kkossev.deviceProfileLib, line 1472
    return fingerprintStr // library marker kkossev.deviceProfileLib, line 1473
} // library marker kkossev.deviceProfileLib, line 1474

public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1476
    int count = 0 // library marker kkossev.deviceProfileLib, line 1477
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1478
        logInfo "Device Profile: ${profileName}" // library marker kkossev.deviceProfileLib, line 1479
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1480
            log.info "${fingerprintIt(profileMap, fingerprint)}" // library marker kkossev.deviceProfileLib, line 1481
            count++ // library marker kkossev.deviceProfileLib, line 1482
        } // library marker kkossev.deviceProfileLib, line 1483
    } // library marker kkossev.deviceProfileLib, line 1484
    logInfo "Total fingerprints: ${count}" // library marker kkossev.deviceProfileLib, line 1485
} // library marker kkossev.deviceProfileLib, line 1486

public void printPreferences() { // library marker kkossev.deviceProfileLib, line 1488
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 1489
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 1490
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 1491
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLib, line 1492
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 1493
                log.info inputMap // library marker kkossev.deviceProfileLib, line 1494
            } // library marker kkossev.deviceProfileLib, line 1495
        } // library marker kkossev.deviceProfileLib, line 1496
    } // library marker kkossev.deviceProfileLib, line 1497
} // library marker kkossev.deviceProfileLib, line 1498

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~

// ~~~~~ start include (179) kkossev.thermostatLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.thermostatLib, line 1
library( // library marker kkossev.thermostatLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Thermostat Library', name: 'thermostatLib', namespace: 'kkossev', // library marker kkossev.thermostatLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/thermostatLib.groovy', documentationLink: '', // library marker kkossev.thermostatLib, line 4
    version: '3.6.0') // library marker kkossev.thermostatLib, line 5
/* // library marker kkossev.thermostatLib, line 6
 *  Zigbee Thermostat Library // library marker kkossev.thermostatLib, line 7
 * // library marker kkossev.thermostatLib, line 8
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.thermostatLib, line 9
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.thermostatLib, line 10
 * // library marker kkossev.thermostatLib, line 11
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.thermostatLib, line 12
 * // library marker kkossev.thermostatLib, line 13
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.thermostatLib, line 14
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.thermostatLib, line 15
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.thermostatLib, line 16
 * // library marker kkossev.thermostatLib, line 17
 * ver. 3.3.0  2024-06-09 kkossev  - added thermostatLib.groovy // library marker kkossev.thermostatLib, line 18
 * ver. 3.3.1  2024-06-16 kkossev  - added factoryResetThermostat() command // library marker kkossev.thermostatLib, line 19
 * ver. 3.3.2  2024-07-09 kkossev  - release 3.3.2 // library marker kkossev.thermostatLib, line 20
 * ver. 3.3.4  2024-10-23 kkossev  - fixed exception in sendDigitalEventIfNeeded when the attribute is not found (level) // library marker kkossev.thermostatLib, line 21
 * ver. 3.5.0  2025-02-16 kkossev  - added setpointReceiveCheck() and modeReceiveCheck() retries // library marker kkossev.thermostatLib, line 22
 * ver. 3.5.1  2025-03-04 kkossev  - == false bug fix; disabled switching to 'cool' mode. // library marker kkossev.thermostatLib, line 23
 * ver. 3.6.0  2025-09-15 kkossev  - deviceProfileLibV4 alignment // library marker kkossev.thermostatLib, line 24
 * // library marker kkossev.thermostatLib, line 25
 *                                   TODO: add eco() method // library marker kkossev.thermostatLib, line 26
 *                                   TODO: refactor sendHeatingSetpointEvent // library marker kkossev.thermostatLib, line 27
*/ // library marker kkossev.thermostatLib, line 28

public static String thermostatLibVersion()   { '3.6.0' } // library marker kkossev.thermostatLib, line 30
public static String thermostatLibStamp() { '2025/09/15 8:10 PM' } // library marker kkossev.thermostatLib, line 31

metadata { // library marker kkossev.thermostatLib, line 33
    capability 'Actuator'           // also in onOffLib // library marker kkossev.thermostatLib, line 34
    capability 'Sensor' // library marker kkossev.thermostatLib, line 35
    capability 'Thermostat'                 // needed for HomeKit // library marker kkossev.thermostatLib, line 36
                // coolingSetpoint - NUMBER; heatingSetpoint - NUMBER; supportedThermostatFanModes - JSON_OBJECT; supportedThermostatModes - JSON_OBJECT; temperature - NUMBER, unit:°F || °C; thermostatFanMode - ENUM ["on", "circulate", "auto"] // library marker kkossev.thermostatLib, line 37
                // thermostatMode - ENUM ["auto", "off", "heat", "emergency heat", "cool"]; thermostatOperatingState - ENUM ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]; thermostatSetpoint - NUMBER, unit:°F || °C // library marker kkossev.thermostatLib, line 38
    capability 'ThermostatHeatingSetpoint' // library marker kkossev.thermostatLib, line 39
    capability 'ThermostatCoolingSetpoint' // library marker kkossev.thermostatLib, line 40
    capability 'ThermostatOperatingState'   // thermostatOperatingState - ENUM ["vent economizer", "pending cool", "cooling", "heating", "pending heat", "fan only", "idle"] // library marker kkossev.thermostatLib, line 41
    capability 'ThermostatSetpoint' // library marker kkossev.thermostatLib, line 42
    capability 'ThermostatMode' // library marker kkossev.thermostatLib, line 43
    capability 'ThermostatFanMode' // library marker kkossev.thermostatLib, line 44
    // no attributes // library marker kkossev.thermostatLib, line 45

    command 'setThermostatMode', [[name: 'thermostat mode (not all are available!)', type: 'ENUM', constraints: ['--- select ---'] + AllPossibleThermostatModesOpts.options.values() as List<String>]] // library marker kkossev.thermostatLib, line 47
    //    command 'setTemperature', ['NUMBER']                        // Virtual thermostat  TODO - decide if it is needed // library marker kkossev.thermostatLib, line 48

    preferences { // library marker kkossev.thermostatLib, line 50
        if (device) { // TODO -  move it to the deviceProfile preferences // library marker kkossev.thermostatLib, line 51
            input name: 'temperaturePollingInterval', type: 'enum', title: '<b>Temperature polling interval</b>', options: TrvTemperaturePollingIntervalOpts.options, defaultValue: TrvTemperaturePollingIntervalOpts.defaultValue, required: true, description: 'Changes how often the hub will poll the TRV for faster temperature reading updates and nice looking graphs.' // library marker kkossev.thermostatLib, line 52
        } // library marker kkossev.thermostatLib, line 53
    } // library marker kkossev.thermostatLib, line 54
} // library marker kkossev.thermostatLib, line 55

@Field static final Map TrvTemperaturePollingIntervalOpts = [ // library marker kkossev.thermostatLib, line 57
    defaultValue: 600, // library marker kkossev.thermostatLib, line 58
    options     : [0: 'Disabled', 60: 'Every minute (not recommended)', 120: 'Every 2 minutes', 300: 'Every 5 minutes', 600: 'Every 10 minutes', 900: 'Every 15 minutes', 1800: 'Every 30 minutes', 3600: 'Every 1 hour'] // library marker kkossev.thermostatLib, line 59
] // library marker kkossev.thermostatLib, line 60

@Field static final Map AllPossibleThermostatModesOpts = [ // library marker kkossev.thermostatLib, line 62
    defaultValue: 1, // library marker kkossev.thermostatLib, line 63
    options     : [0: 'off', 1: 'heat', 2: 'cool', 3: 'auto', 4: 'emergency heat', 5: 'eco'] // library marker kkossev.thermostatLib, line 64
] // library marker kkossev.thermostatLib, line 65

public void heat() { setThermostatMode('heat') } // library marker kkossev.thermostatLib, line 67
public void auto() { setThermostatMode('auto') } // library marker kkossev.thermostatLib, line 68
public void cool() { setThermostatMode('cool') } // library marker kkossev.thermostatLib, line 69
public void emergencyHeat() { setThermostatMode('emergency heat') } // library marker kkossev.thermostatLib, line 70
public void eco() { setThermostatMode('eco') } // library marker kkossev.thermostatLib, line 71

public void setThermostatFanMode(final String fanMode) { sendEvent(name: 'thermostatFanMode', value: "${fanMode}", descriptionText: getDescriptionText("thermostatFanMode is ${fanMode}")) } // library marker kkossev.thermostatLib, line 73
public void fanAuto() { setThermostatFanMode('auto') } // library marker kkossev.thermostatLib, line 74
public void fanCirculate() { setThermostatFanMode('circulate') } // library marker kkossev.thermostatLib, line 75
public void fanOn() { setThermostatFanMode('on') } // library marker kkossev.thermostatLib, line 76

public void customOff() { setThermostatMode('off') }    // invoked from the common library // library marker kkossev.thermostatLib, line 78
public void customOn()  { setThermostatMode('heat') }   // invoked from the common library // library marker kkossev.thermostatLib, line 79

/* // library marker kkossev.thermostatLib, line 81
 * ----------------------------------------------------------------------------- // library marker kkossev.thermostatLib, line 82
 * thermostat cluster 0x0201 // library marker kkossev.thermostatLib, line 83
 * ----------------------------------------------------------------------------- // library marker kkossev.thermostatLib, line 84
*/ // library marker kkossev.thermostatLib, line 85
// * should be implemented in the custom driver code ... // library marker kkossev.thermostatLib, line 86
public void standardParseThermostatCluster(final Map descMap) { // library marker kkossev.thermostatLib, line 87
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value)) // library marker kkossev.thermostatLib, line 88
    logTrace "standardParseThermostatCluster: zigbee received Thermostat cluster (0x0201) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})" // library marker kkossev.thermostatLib, line 89
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return } // library marker kkossev.thermostatLib, line 90
    if (deviceProfilesV3 != null || g_deviceProfilesV4 != null) { // library marker kkossev.thermostatLib, line 91
        boolean result = processClusterAttributeFromDeviceProfile(descMap) // library marker kkossev.thermostatLib, line 92
        if ( result == false ) { // library marker kkossev.thermostatLib, line 93
            logWarn "standardParseThermostatCluster: received unknown Thermostat cluster (0x0201) attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.thermostatLib, line 94
        } // library marker kkossev.thermostatLib, line 95
    } // library marker kkossev.thermostatLib, line 96
    // try to process the attribute value // library marker kkossev.thermostatLib, line 97
    standardHandleThermostatEvent(value) // library marker kkossev.thermostatLib, line 98
} // library marker kkossev.thermostatLib, line 99

//  setHeatingSetpoint thermostat capability standard command // library marker kkossev.thermostatLib, line 101
//  1°C steps. (0.5°C setting on the TRV itself, rounded for zigbee interface) // library marker kkossev.thermostatLib, line 102
// // library marker kkossev.thermostatLib, line 103
void setHeatingSetpoint(final Number temperaturePar ) { // library marker kkossev.thermostatLib, line 104
    BigDecimal temperature = temperaturePar.toBigDecimal() // library marker kkossev.thermostatLib, line 105
    logTrace "setHeatingSetpoint(${temperature}) called!" // library marker kkossev.thermostatLib, line 106
    BigDecimal previousSetpoint = (device.currentState('heatingSetpoint')?.value ?: 0.0G).toBigDecimal() // library marker kkossev.thermostatLib, line 107
    BigDecimal tempDouble = temperature // library marker kkossev.thermostatLib, line 108
    //logDebug "setHeatingSetpoint temperature = ${temperature}  as int = ${temperature as int} (previousSetpointt = ${previousSetpoint})" // library marker kkossev.thermostatLib, line 109
    /* groovylint-disable-next-line ConstantIfExpression */ // library marker kkossev.thermostatLib, line 110
    if (true) { // library marker kkossev.thermostatLib, line 111
        //logDebug "0.5 C correction of the heating setpoint${temperature}" // library marker kkossev.thermostatLib, line 112
        //log.trace "tempDouble = ${tempDouble}" // library marker kkossev.thermostatLib, line 113
        tempDouble = (tempDouble * 2).setScale(0, RoundingMode.HALF_UP) / 2 // library marker kkossev.thermostatLib, line 114
    } // library marker kkossev.thermostatLib, line 115
    else { // library marker kkossev.thermostatLib, line 116
        if (temperature != (temperature as int)) { // library marker kkossev.thermostatLib, line 117
            if ((temperature as double) > (previousSetpoint as double)) { // library marker kkossev.thermostatLib, line 118
                temperature = (temperature + 0.5 ) as int // library marker kkossev.thermostatLib, line 119
            } // library marker kkossev.thermostatLib, line 120
            else { // library marker kkossev.thermostatLib, line 121
                temperature = temperature as int // library marker kkossev.thermostatLib, line 122
            } // library marker kkossev.thermostatLib, line 123
            logDebug "corrected heating setpoint ${temperature}" // library marker kkossev.thermostatLib, line 124
        } // library marker kkossev.thermostatLib, line 125
        tempDouble = temperature // library marker kkossev.thermostatLib, line 126
    } // library marker kkossev.thermostatLib, line 127
    BigDecimal maxTemp = settings?.maxHeatingSetpoint ? new BigDecimal(settings.maxHeatingSetpoint) : new BigDecimal(50) // library marker kkossev.thermostatLib, line 128
    BigDecimal minTemp = settings?.minHeatingSetpoint ? new BigDecimal(settings.minHeatingSetpoint) : new BigDecimal(5) // library marker kkossev.thermostatLib, line 129
    tempBigDecimal = new BigDecimal(tempDouble) // library marker kkossev.thermostatLib, line 130
    tempBigDecimal = tempDouble.min(maxTemp).max(minTemp).setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.thermostatLib, line 131

    logDebug "setHeatingSetpoint: calling sendAttribute heatingSetpoint ${tempBigDecimal}" // library marker kkossev.thermostatLib, line 133
    sendAttribute('heatingSetpoint', tempBigDecimal as double) // library marker kkossev.thermostatLib, line 134

    // added 02/16/2025 // library marker kkossev.thermostatLib, line 136
    state.lastTx.isSetPointReq = true // library marker kkossev.thermostatLib, line 137
    state.lastTx.setPoint = tempBigDecimal    // BEOK - float value! // library marker kkossev.thermostatLib, line 138
    runIn(3, setpointReceiveCheck) // library marker kkossev.thermostatLib, line 139

} // library marker kkossev.thermostatLib, line 141

// TODO - use sendThermostatEvent instead! // library marker kkossev.thermostatLib, line 143
void sendHeatingSetpointEvent(Number temperature) { // library marker kkossev.thermostatLib, line 144
    tempDouble = safeToDouble(temperature) // library marker kkossev.thermostatLib, line 145
    Map eventMap = [name: 'heatingSetpoint',  value: tempDouble, unit: '\u00B0C', type: 'physical'] // library marker kkossev.thermostatLib, line 146
    eventMap.descriptionText = "heatingSetpoint is ${tempDouble}" // library marker kkossev.thermostatLib, line 147
    if (state.states['isRefresh'] == true) { // library marker kkossev.thermostatLib, line 148
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.thermostatLib, line 149
        eventMap.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 150
    } // library marker kkossev.thermostatLib, line 151
    sendEvent(eventMap) // library marker kkossev.thermostatLib, line 152
    if (eventMap.descriptionText != null) { logInfo "${eventMap.descriptionText}" } // library marker kkossev.thermostatLib, line 153

    eventMap.name = 'thermostatSetpoint' // library marker kkossev.thermostatLib, line 155
    logDebug "sending event ${eventMap}" // library marker kkossev.thermostatLib, line 156
    sendEvent(eventMap) // library marker kkossev.thermostatLib, line 157
    updateDataValue('lastRunningMode', 'heat') // library marker kkossev.thermostatLib, line 158
    // added 02/16/2025 // library marker kkossev.thermostatLib, line 159
    state.lastRx.setPoint = tempDouble // library marker kkossev.thermostatLib, line 160
} // library marker kkossev.thermostatLib, line 161

// thermostat capability standard command // library marker kkossev.thermostatLib, line 163
// do nothing in TRV - just send an event // library marker kkossev.thermostatLib, line 164
void setCoolingSetpoint(Number temperaturePar) { // library marker kkossev.thermostatLib, line 165
    logDebug "setCoolingSetpoint(${temperaturePar}) called!" // library marker kkossev.thermostatLib, line 166
    /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.thermostatLib, line 167
    BigDecimal temperature = Math.round(temperaturePar * 2) / 2 // library marker kkossev.thermostatLib, line 168
    String descText = "coolingSetpoint is set to ${temperature} \u00B0C" // library marker kkossev.thermostatLib, line 169
    sendEvent(name: 'coolingSetpoint', value: temperature, unit: '\u00B0C', descriptionText: descText, type: 'digital') // library marker kkossev.thermostatLib, line 170
    logInfo "${descText}" // library marker kkossev.thermostatLib, line 171
} // library marker kkossev.thermostatLib, line 172

public void sendThermostatEvent(Map eventMap, final boolean isDigital = false) { // library marker kkossev.thermostatLib, line 174
    if (eventMap.descriptionText == null) { eventMap.descriptionText = "${eventName} is ${value}" } // library marker kkossev.thermostatLib, line 175
    if (eventMap.type == null) { eventMap.type = isDigital == true ? 'digital' : 'physical' } // library marker kkossev.thermostatLib, line 176
    if (state.states['isRefresh'] == true) { // library marker kkossev.thermostatLib, line 177
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.thermostatLib, line 178
        eventMap.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 179
    } // library marker kkossev.thermostatLib, line 180
    sendEvent(eventMap) // library marker kkossev.thermostatLib, line 181
    logInfo "${eventMap.descriptionText}" // library marker kkossev.thermostatLib, line 182
} // library marker kkossev.thermostatLib, line 183

private void sendEventMap(final Map event, final boolean isDigital = false) { // library marker kkossev.thermostatLib, line 185
    if (event.descriptionText == null) { // library marker kkossev.thermostatLib, line 186
        event.descriptionText = "${event.name} is ${event.value} ${event.unit ?: ''}" // library marker kkossev.thermostatLib, line 187
    } // library marker kkossev.thermostatLib, line 188
    if (state.states['isRefresh'] == true) { // library marker kkossev.thermostatLib, line 189
        event.descriptionText += ' [refresh]' // library marker kkossev.thermostatLib, line 190
        event.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 191
    } // library marker kkossev.thermostatLib, line 192
    event.type = event.type != null ? event.type : isDigital == true ? 'digital' : 'physical' // library marker kkossev.thermostatLib, line 193
    if (event.type == 'digital') { // library marker kkossev.thermostatLib, line 194
        event.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 195
        event.descriptionText += ' [digital]' // library marker kkossev.thermostatLib, line 196
    } // library marker kkossev.thermostatLib, line 197
    sendEvent(event) // library marker kkossev.thermostatLib, line 198
    logInfo "${event.descriptionText}" // library marker kkossev.thermostatLib, line 199
} // library marker kkossev.thermostatLib, line 200

private String getDescriptionText(final String msg) { // library marker kkossev.thermostatLib, line 202
    String descriptionText = "${device.displayName} ${msg}" // library marker kkossev.thermostatLib, line 203
    if (settings?.txtEnable) { log.info "${descriptionText}" } // library marker kkossev.thermostatLib, line 204
    return descriptionText // library marker kkossev.thermostatLib, line 205
} // library marker kkossev.thermostatLib, line 206

/** // library marker kkossev.thermostatLib, line 208
 * Sets the thermostat mode based on the requested mode. // library marker kkossev.thermostatLib, line 209
 * // library marker kkossev.thermostatLib, line 210
 * if the requestedMode is supported directly in the thermostatMode attribute, it is set directly. // library marker kkossev.thermostatLib, line 211
 * Otherwise, the thermostatMode is substituted with another command, if supported by the device. // library marker kkossev.thermostatLib, line 212
 * // library marker kkossev.thermostatLib, line 213
 * @param requestedMode The mode to set the thermostat to. // library marker kkossev.thermostatLib, line 214
 */ // library marker kkossev.thermostatLib, line 215
public void setThermostatMode(final String requestedMode) { // library marker kkossev.thermostatLib, line 216
    String mode = requestedMode // library marker kkossev.thermostatLib, line 217
    boolean result = false // library marker kkossev.thermostatLib, line 218
    List nativelySupportedModesList = getAttributesMap('thermostatMode')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 219
    List systemModesList = getAttributesMap('systemMode')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 220
    List ecoModesList = getAttributesMap('ecoMode')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 221
    List emergencyHeatingModesList = getAttributesMap('emergencyHeating')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 222

    logDebug "setThermostatMode: sending setThermostatMode(${mode}). Natively supported: ${nativelySupportedModesList}" // library marker kkossev.thermostatLib, line 224

    // some TRVs require some checks and additional commands to be sent before setting the mode // library marker kkossev.thermostatLib, line 226
    final String currentMode = device.currentValue('thermostatMode') // library marker kkossev.thermostatLib, line 227
    logDebug "setThermostatMode: currentMode = ${currentMode}, switching to ${mode} ..." // library marker kkossev.thermostatLib, line 228

    // added 02/16/2025 // library marker kkossev.thermostatLib, line 230
    setLastTx( mode = requestedMode, isModeSetReq = true) // library marker kkossev.thermostatLib, line 231
    runIn(4, modeReceiveCheck) // library marker kkossev.thermostatLib, line 232

    switch (mode) { // library marker kkossev.thermostatLib, line 234
        case 'heat': // library marker kkossev.thermostatLib, line 235
        case 'auto': // library marker kkossev.thermostatLib, line 236
            if (device.currentValue('ecoMode') == 'on') { // library marker kkossev.thermostatLib, line 237
                logDebug 'setThermostatMode: pre-processing: switching first the eco mode off' // library marker kkossev.thermostatLib, line 238
                sendAttribute('ecoMode', 0) // library marker kkossev.thermostatLib, line 239
            } // library marker kkossev.thermostatLib, line 240
            if (device.currentValue('emergencyHeating') == 'on') { // library marker kkossev.thermostatLib, line 241
                logDebug 'setThermostatMode: pre-processing: switching first the emergencyHeating mode off' // library marker kkossev.thermostatLib, line 242
                sendAttribute('emergencyHeating', 0) // library marker kkossev.thermostatLib, line 243
            } // library marker kkossev.thermostatLib, line 244
            if ((device.currentValue('systemMode') ?: 'off') == 'off') { // library marker kkossev.thermostatLib, line 245
                logDebug 'setThermostatMode: pre-processing: switching first the systemMode on' // library marker kkossev.thermostatLib, line 246
                sendAttribute('systemMode', 'on') // library marker kkossev.thermostatLib, line 247
            } // library marker kkossev.thermostatLib, line 248
            break // library marker kkossev.thermostatLib, line 249
        case 'cool':        // disabled the cool mode 03/04/2025 // library marker kkossev.thermostatLib, line 250
            if (!('cool' in DEVICE.supportedThermostatModes)) { // library marker kkossev.thermostatLib, line 251
                // why shoud we replace 'cool' with 'eco' and 'off' modes ???? // library marker kkossev.thermostatLib, line 252
                /* // library marker kkossev.thermostatLib, line 253
                // replace cool with 'eco' mode, if supported by the device // library marker kkossev.thermostatLib, line 254
                if ('eco' in DEVICE.supportedThermostatModes) { // library marker kkossev.thermostatLib, line 255
                    logDebug 'setThermostatMode: pre-processing: switching to eco mode instead' // library marker kkossev.thermostatLib, line 256
                    mode = 'eco' // library marker kkossev.thermostatLib, line 257
                    break // library marker kkossev.thermostatLib, line 258
                } // library marker kkossev.thermostatLib, line 259
                else if ('off' in DEVICE.supportedThermostatModes) { // library marker kkossev.thermostatLib, line 260
                    logDebug 'setThermostatMode: pre-processing: switching to off mode instead' // library marker kkossev.thermostatLib, line 261
                    mode = 'off' // library marker kkossev.thermostatLib, line 262
                    break // library marker kkossev.thermostatLib, line 263
                } // library marker kkossev.thermostatLib, line 264
                else if (device.currentValue('ecoMode') != null) { // library marker kkossev.thermostatLib, line 265
                    // BRT-100 has a dediceted 'ecoMode' command   // TODO - check how to switch BRT-100 low temp protection mode (5 degrees) ? // library marker kkossev.thermostatLib, line 266
                    logDebug "setThermostatMode: pre-processing: setting eco mode on (${settings.ecoTemp} &degC)" // library marker kkossev.thermostatLib, line 267
                    sendAttribute('ecoMode', 1) // library marker kkossev.thermostatLib, line 268
                } // library marker kkossev.thermostatLib, line 269
                */ // library marker kkossev.thermostatLib, line 270
                //else { // library marker kkossev.thermostatLib, line 271
                    logDebug "setThermostatMode: pre-processing: switching to 'cool' mode is not supported by this device!" // library marker kkossev.thermostatLib, line 272
                    return // library marker kkossev.thermostatLib, line 273
                //} // library marker kkossev.thermostatLib, line 274
            } // library marker kkossev.thermostatLib, line 275
            break // library marker kkossev.thermostatLib, line 276
        case 'emergency heat':     // TODO for Aqara and Sonoff TRVs // library marker kkossev.thermostatLib, line 277
            if ('emergency heat' in nativelySupportedModesList) { // library marker kkossev.thermostatLib, line 278
                break // library marker kkossev.thermostatLib, line 279
            } // library marker kkossev.thermostatLib, line 280
            // look for a dedicated 'emergencyMode' deviceProfile attribute       (BRT-100) // library marker kkossev.thermostatLib, line 281
            if ('on' in emergencyHeatingModesList)  { // library marker kkossev.thermostatLib, line 282
                logInfo "setThermostatMode: pre-processing: switching the emergencyMode mode on for (${settings.emergencyHeatingTime} seconds )" // library marker kkossev.thermostatLib, line 283
                sendAttribute('emergencyHeating', 'on') // library marker kkossev.thermostatLib, line 284
                return // library marker kkossev.thermostatLib, line 285
            } // library marker kkossev.thermostatLib, line 286
            break // library marker kkossev.thermostatLib, line 287
        case 'eco': // library marker kkossev.thermostatLib, line 288
            if ('eco' in nativelySupportedModesList) {   // library marker kkossev.thermostatLib, line 289
                logDebug 'setThermostatMode: pre-processing: switching to natively supported eco mode' // library marker kkossev.thermostatLib, line 290
                break // library marker kkossev.thermostatLib, line 291
            } // library marker kkossev.thermostatLib, line 292
            if (device.hasAttribute('ecoMode')) {   // changed 06/16/2024 : was : (device.currentValue('ecoMode') != null)  { // library marker kkossev.thermostatLib, line 293
                logDebug 'setThermostatMode: pre-processing: switching the eco mode on' // library marker kkossev.thermostatLib, line 294
                sendAttribute('ecoMode', 1) // library marker kkossev.thermostatLib, line 295
                return // library marker kkossev.thermostatLib, line 296
            } // library marker kkossev.thermostatLib, line 297
            else { // library marker kkossev.thermostatLib, line 298
                logWarn "setThermostatMode: pre-processing: switching to 'eco' mode is not supported by this device!" // library marker kkossev.thermostatLib, line 299
                return // library marker kkossev.thermostatLib, line 300
            } // library marker kkossev.thermostatLib, line 301
            break // library marker kkossev.thermostatLib, line 302
        case 'off':     // OK! // library marker kkossev.thermostatLib, line 303
            if ('off' in nativelySupportedModesList) { // library marker kkossev.thermostatLib, line 304
                break // library marker kkossev.thermostatLib, line 305
            } // library marker kkossev.thermostatLib, line 306
            logDebug "setThermostatMode: pre-processing: switching to 'off' mode" // library marker kkossev.thermostatLib, line 307
            // if the 'off' mode is not directly supported, try substituting it with 'eco' mode // library marker kkossev.thermostatLib, line 308
            if ('eco' in nativelySupportedModesList) { // library marker kkossev.thermostatLib, line 309
                logDebug 'setThermostatMode: pre-processing: switching to eco mode instead' // library marker kkossev.thermostatLib, line 310
                mode = 'eco' // library marker kkossev.thermostatLib, line 311
                break // library marker kkossev.thermostatLib, line 312
            } // library marker kkossev.thermostatLib, line 313
            // look for a dedicated 'ecoMode' deviceProfile attribute       (BRT-100) // library marker kkossev.thermostatLib, line 314
            if ('on' in ecoModesList)  { // library marker kkossev.thermostatLib, line 315
                logDebug 'setThermostatMode: pre-processing: switching the eco mode on' // library marker kkossev.thermostatLib, line 316
                sendAttribute('ecoMode', 'on') // library marker kkossev.thermostatLib, line 317
                return // library marker kkossev.thermostatLib, line 318
            } // library marker kkossev.thermostatLib, line 319
            // look for a dedicated 'systemMode' attribute with map 'off' (Aqara E1) // library marker kkossev.thermostatLib, line 320
            if ('off' in systemModesList)  { // library marker kkossev.thermostatLib, line 321
                logDebug 'setThermostatMode: pre-processing: switching the systemMode off' // library marker kkossev.thermostatLib, line 322
                sendAttribute('systemMode', 'off') // library marker kkossev.thermostatLib, line 323
                return // library marker kkossev.thermostatLib, line 324
            } // library marker kkossev.thermostatLib, line 325
            break // library marker kkossev.thermostatLib, line 326
        default: // library marker kkossev.thermostatLib, line 327
            logWarn "setThermostatMode: pre-processing: unknown mode ${mode}" // library marker kkossev.thermostatLib, line 328
            break // library marker kkossev.thermostatLib, line 329
    } // library marker kkossev.thermostatLib, line 330

    // try using the standard thermostat capability to switch to the selected new mode // library marker kkossev.thermostatLib, line 332
    result = sendAttribute('thermostatMode', mode) // library marker kkossev.thermostatLib, line 333
    logTrace "setThermostatMode: sendAttribute returned ${result}" // library marker kkossev.thermostatLib, line 334
    if (result == true) { return } // library marker kkossev.thermostatLib, line 335

    // post-process mode switching for some TRVs // library marker kkossev.thermostatLib, line 337
    switch (mode) { // library marker kkossev.thermostatLib, line 338
        case 'cool' : // library marker kkossev.thermostatLib, line 339
        case 'heat' : // library marker kkossev.thermostatLib, line 340
        case 'auto' : // library marker kkossev.thermostatLib, line 341
        case 'off' : // library marker kkossev.thermostatLib, line 342
        case 'eco' : // library marker kkossev.thermostatLib, line 343
            logTrace "setThermostatMode: post-processing: no post-processing required for mode ${mode}" // library marker kkossev.thermostatLib, line 344
            break // library marker kkossev.thermostatLib, line 345
        case 'emergency heat' : // library marker kkossev.thermostatLib, line 346
            logDebug "setThermostatMode: post-processing: setting emergency heat mode on (${settings.emergencyHeatingTime} minutes)" // library marker kkossev.thermostatLib, line 347
            sendAttribute('emergencyHeating', 1) // library marker kkossev.thermostatLib, line 348
            break // library marker kkossev.thermostatLib, line 349
        default : // library marker kkossev.thermostatLib, line 350
            logWarn "setThermostatMode: post-processing: unsupported thermostat mode '${mode}'" // library marker kkossev.thermostatLib, line 351
            break // library marker kkossev.thermostatLib, line 352
    } // library marker kkossev.thermostatLib, line 353
    return // library marker kkossev.thermostatLib, line 354
} // library marker kkossev.thermostatLib, line 355

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.thermostatLib, line 357
void sendSupportedThermostatModes(boolean debug = false) { // library marker kkossev.thermostatLib, line 358
    List<String> supportedThermostatModes = [] // library marker kkossev.thermostatLib, line 359
    supportedThermostatModes = ['off', 'heat', 'auto', 'emergency heat'] // library marker kkossev.thermostatLib, line 360
    if (DEVICE.supportedThermostatModes != null) { // library marker kkossev.thermostatLib, line 361
        supportedThermostatModes = DEVICE.supportedThermostatModes // library marker kkossev.thermostatLib, line 362
    } // library marker kkossev.thermostatLib, line 363
    else { // library marker kkossev.thermostatLib, line 364
        logWarn 'sendSupportedThermostatModes: DEVICE.supportedThermostatModes is not set!' // library marker kkossev.thermostatLib, line 365
        supportedThermostatModes =  ['off', 'auto', 'heat'] // library marker kkossev.thermostatLib, line 366
    } // library marker kkossev.thermostatLib, line 367
    logInfo "supportedThermostatModes: ${supportedThermostatModes}" // library marker kkossev.thermostatLib, line 368
    sendEvent(name: 'supportedThermostatModes', value:  JsonOutput.toJson(supportedThermostatModes), isStateChange: true, type: 'digital') // library marker kkossev.thermostatLib, line 369
    if (DEVICE.supportedThermostatFanModes != null) { // library marker kkossev.thermostatLib, line 370
        sendEvent(name: 'supportedThermostatFanModes', value: JsonOutput.toJson(DEVICE.supportedThermostatFanModes), isStateChange: true, type: 'digital') // library marker kkossev.thermostatLib, line 371
    } // library marker kkossev.thermostatLib, line 372
    else { // library marker kkossev.thermostatLib, line 373
        sendEvent(name: 'supportedThermostatFanModes', value: JsonOutput.toJson(['auto', 'circulate', 'on']), isStateChange: true, type: 'digital') // library marker kkossev.thermostatLib, line 374
    } // library marker kkossev.thermostatLib, line 375
} // library marker kkossev.thermostatLib, line 376

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.thermostatLib, line 378
void standardHandleThermostatEvent(int value, boolean isDigital=false) { // library marker kkossev.thermostatLib, line 379
    logWarn 'standardHandleThermostatEvent()... NOT IMPLEMENTED!' // library marker kkossev.thermostatLib, line 380
} // library marker kkossev.thermostatLib, line 381

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.thermostatLib, line 383
private void sendDelayedThermostatEvent(Map eventMap) { // library marker kkossev.thermostatLib, line 384
    logWarn "${device.displayName} NOT IMPLEMENTED! <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.thermostatLib, line 385
} // library marker kkossev.thermostatLib, line 386

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.thermostatLib, line 388
void thermostatProcessTuyaDP(final Map descMap, int dp, int dp_id, int fncmd) { // library marker kkossev.thermostatLib, line 389
    logWarn "thermostatProcessTuyaDP()... NOT IMPLEMENTED! dp=${dp} dp_id=${dp_id} fncmd=${fncmd}" // library marker kkossev.thermostatLib, line 390
} // library marker kkossev.thermostatLib, line 391

/** // library marker kkossev.thermostatLib, line 393
 * Schedule thermostat polling // library marker kkossev.thermostatLib, line 394
 * @param intervalMins interval in seconds // library marker kkossev.thermostatLib, line 395
 */ // library marker kkossev.thermostatLib, line 396
public void scheduleThermostatPolling(final int intervalSecs) { // library marker kkossev.thermostatLib, line 397
    String cron = getCron( intervalSecs ) // library marker kkossev.thermostatLib, line 398
    logDebug "cron = ${cron}" // library marker kkossev.thermostatLib, line 399
    schedule(cron, 'autoPollThermostat') // library marker kkossev.thermostatLib, line 400
} // library marker kkossev.thermostatLib, line 401

public void unScheduleThermostatPolling() { // library marker kkossev.thermostatLib, line 403
    unschedule('autoPollThermostat') // library marker kkossev.thermostatLib, line 404
} // library marker kkossev.thermostatLib, line 405

/** // library marker kkossev.thermostatLib, line 407
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.thermostatLib, line 408
 */ // library marker kkossev.thermostatLib, line 409
public void autoPollThermostat() { // library marker kkossev.thermostatLib, line 410
    logDebug 'autoPollThermostat()...' // library marker kkossev.thermostatLib, line 411
    checkDriverVersion(state) // library marker kkossev.thermostatLib, line 412
    List<String> cmds = refreshFromDeviceProfileList() // library marker kkossev.thermostatLib, line 413
    if (cmds != null && cmds != [] ) { // library marker kkossev.thermostatLib, line 414
        sendZigbeeCommands(cmds) // library marker kkossev.thermostatLib, line 415
    } // library marker kkossev.thermostatLib, line 416
} // library marker kkossev.thermostatLib, line 417

private int getElapsedTimeFromEventInSeconds(final String eventName) { // library marker kkossev.thermostatLib, line 419
    /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.thermostatLib, line 420
    final Long now = new Date().time // library marker kkossev.thermostatLib, line 421
    final Object lastEventState = device.currentState(eventName) // library marker kkossev.thermostatLib, line 422
    logDebug "getElapsedTimeFromEventInSeconds: eventName = ${eventName} lastEventState = ${lastEventState}" // library marker kkossev.thermostatLib, line 423
    if (lastEventState == null) { // library marker kkossev.thermostatLib, line 424
        logTrace 'getElapsedTimeFromEventInSeconds: lastEventState is null, returning 0' // library marker kkossev.thermostatLib, line 425
        return 0 // library marker kkossev.thermostatLib, line 426
    } // library marker kkossev.thermostatLib, line 427
    Long lastEventStateTime = lastEventState.date.time // library marker kkossev.thermostatLib, line 428
    //def lastEventStateValue = lastEventState.value // library marker kkossev.thermostatLib, line 429
    int diff = ((now - lastEventStateTime) / 1000) as int // library marker kkossev.thermostatLib, line 430
    // convert diff to minutes and seconds // library marker kkossev.thermostatLib, line 431
    logTrace "getElapsedTimeFromEventInSeconds: lastEventStateTime = ${lastEventStateTime} diff = ${diff} seconds" // library marker kkossev.thermostatLib, line 432
    return diff // library marker kkossev.thermostatLib, line 433
} // library marker kkossev.thermostatLib, line 434

// called from pollTuya() // library marker kkossev.thermostatLib, line 436
public void sendDigitalEventIfNeeded(final String eventName) { // library marker kkossev.thermostatLib, line 437
    final Object lastEventState = device.currentState(eventName) // library marker kkossev.thermostatLib, line 438
    if (lastEventState == null) { // library marker kkossev.thermostatLib, line 439
        logDebug "sendDigitalEventIfNeeded: lastEventState ${eventName} is null, skipping" // library marker kkossev.thermostatLib, line 440
        return // library marker kkossev.thermostatLib, line 441
    } // library marker kkossev.thermostatLib, line 442
    final int diff = getElapsedTimeFromEventInSeconds(eventName) // library marker kkossev.thermostatLib, line 443
    final String diffStr = timeToHMS(diff) // library marker kkossev.thermostatLib, line 444
    if (diff >= (settings.temperaturePollingInterval as int)) { // library marker kkossev.thermostatLib, line 445
        logDebug "pollTuya: ${eventName} was sent more than ${settings.temperaturePollingInterval} seconds ago (${diffStr}), sending digital event" // library marker kkossev.thermostatLib, line 446
        sendEventMap([name: lastEventState.name, value: lastEventState.value, unit: lastEventState.unit, type: 'digital']) // library marker kkossev.thermostatLib, line 447
    } // library marker kkossev.thermostatLib, line 448
    else { // library marker kkossev.thermostatLib, line 449
        logDebug "pollTuya: ${eventName} was sent less than ${settings.temperaturePollingInterval} seconds ago, skipping" // library marker kkossev.thermostatLib, line 450
    } // library marker kkossev.thermostatLib, line 451
} // library marker kkossev.thermostatLib, line 452

public void thermostatInitializeVars( boolean fullInit = false ) { // library marker kkossev.thermostatLib, line 454
    logDebug "thermostatInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.thermostatLib, line 455
    if (fullInit == true || state.lastThermostatMode == null) { state.lastThermostatMode = 'unknown' } // library marker kkossev.thermostatLib, line 456
    if (fullInit == true || state.lastThermostatOperatingState == null) { state.lastThermostatOperatingState = 'unknown' } // library marker kkossev.thermostatLib, line 457
    if (fullInit || settings?.temperaturePollingInterval == null) { device.updateSetting('temperaturePollingInterval', [value: TrvTemperaturePollingIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.thermostatLib, line 458
} // library marker kkossev.thermostatLib, line 459

// called from initializeVars() in the main code ... // library marker kkossev.thermostatLib, line 461
public void thermostatInitEvents(final boolean fullInit=false) { // library marker kkossev.thermostatLib, line 462
    logDebug "thermostatInitEvents()... fullInit = ${fullInit}" // library marker kkossev.thermostatLib, line 463
    if (fullInit == true) { // library marker kkossev.thermostatLib, line 464
        String descText = 'inital attribute setting' // library marker kkossev.thermostatLib, line 465
        sendSupportedThermostatModes() // library marker kkossev.thermostatLib, line 466
        sendEvent(name: 'thermostatMode', value: 'heat', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 467
        state.lastThermostatMode = 'heat' // library marker kkossev.thermostatLib, line 468
        sendEvent(name: 'thermostatFanMode', value: 'auto', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 469
        state.lastThermostatOperatingState = 'idle' // library marker kkossev.thermostatLib, line 470
        sendEvent(name: 'thermostatOperatingState', value: 'idle', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 471
        sendEvent(name: 'thermostatSetpoint', value:  20.0, unit: '\u00B0C', isStateChange: true, description: descText)        // Google Home compatibility // library marker kkossev.thermostatLib, line 472
        sendEvent(name: 'heatingSetpoint', value: 20.0, unit: '\u00B0C', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 473
        state.lastHeatingSetpoint = 20.0 // library marker kkossev.thermostatLib, line 474
        sendEvent(name: 'coolingSetpoint', value: 35.0, unit: '\u00B0C', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 475
        sendEvent(name: 'temperature', value: 18.0, unit: '\u00B0', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 476
        updateDataValue('lastRunningMode', 'heat') // library marker kkossev.thermostatLib, line 477
    } // library marker kkossev.thermostatLib, line 478
    else { // library marker kkossev.thermostatLib, line 479
        logDebug "thermostatInitEvents: fullInit = ${fullInit}" // library marker kkossev.thermostatLib, line 480
    } // library marker kkossev.thermostatLib, line 481
} // library marker kkossev.thermostatLib, line 482

/* // library marker kkossev.thermostatLib, line 484
  Reset to Factory Defaults Command (0x00) // library marker kkossev.thermostatLib, line 485
  On receipt of this command, the device resets all the attributes of all its clusters to their factory defaults. // library marker kkossev.thermostatLib, line 486
  Note that networking functionality, bindings, groups, or other persistent data are not affected by this command // library marker kkossev.thermostatLib, line 487
*/ // library marker kkossev.thermostatLib, line 488
public void factoryResetThermostat() { // library marker kkossev.thermostatLib, line 489
    logDebug 'factoryResetThermostat() called!' // library marker kkossev.thermostatLib, line 490
    List<String> cmds  = zigbee.command(0x0000, 0x00) // library marker kkossev.thermostatLib, line 491
    sendZigbeeCommands(cmds) // library marker kkossev.thermostatLib, line 492
    sendInfoEvent 'The thermostat parameters were FACTORY RESET!' // library marker kkossev.thermostatLib, line 493
    if (this.respondsTo('refreshAll')) { // library marker kkossev.thermostatLib, line 494
        runIn(3, 'refreshAll') // library marker kkossev.thermostatLib, line 495
    } // library marker kkossev.thermostatLib, line 496
} // library marker kkossev.thermostatLib, line 497

// ========================================= Virtual thermostat functions  ========================================= // library marker kkossev.thermostatLib, line 499

public void setTemperature(Number temperaturePar) { // library marker kkossev.thermostatLib, line 501
    logDebug "setTemperature(${temperature}) called!" // library marker kkossev.thermostatLib, line 502
    if (isVirtual()) { // library marker kkossev.thermostatLib, line 503
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.thermostatLib, line 504
        double temperature = Math.round(temperaturePar * 2) / 2 // library marker kkossev.thermostatLib, line 505
        String descText = "temperature is set to ${temperature} \u00B0C" // library marker kkossev.thermostatLib, line 506
        sendEvent(name: 'temperature', value: temperature, unit: '\u00B0C', descriptionText: descText, type: 'digital') // library marker kkossev.thermostatLib, line 507
        logInfo "${descText}" // library marker kkossev.thermostatLib, line 508
    } // library marker kkossev.thermostatLib, line 509
    else { // library marker kkossev.thermostatLib, line 510
        logWarn 'setTemperature: not a virtual thermostat!' // library marker kkossev.thermostatLib, line 511
    } // library marker kkossev.thermostatLib, line 512
} // library marker kkossev.thermostatLib, line 513

// TODO - not used? // library marker kkossev.thermostatLib, line 515
List<String> thermostatRefresh() { // library marker kkossev.thermostatLib, line 516
    logDebug 'thermostatRefresh()...' // library marker kkossev.thermostatLib, line 517
    return [] // library marker kkossev.thermostatLib, line 518
} // library marker kkossev.thermostatLib, line 519

// TODO - configure in the deviceProfile refresh: tag // library marker kkossev.thermostatLib, line 521
public List<String> pollThermostatCluster() { // library marker kkossev.thermostatLib, line 522
    return  zigbee.readAttribute(0x0201, [0x0000, 0x0001, /*0x0002,*/ 0x0012, 0x001B, 0x001C, 0x0029], [:], delay = 1500)      // 0x0000 = local temperature, 0x0001 = outdoor temperature, 0x0002 = occupancy, 0x0012 = heating setpoint, 0x001B = controlledSequenceOfOperation, 0x001C = system mode (enum8 ) // library marker kkossev.thermostatLib, line 523
} // library marker kkossev.thermostatLib, line 524

// TODO - configure in the deviceProfile refresh: tag // library marker kkossev.thermostatLib, line 526
public List<String> pollBatteryPercentage() { // library marker kkossev.thermostatLib, line 527
    return zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)                          // battery percentage // library marker kkossev.thermostatLib, line 528
} // library marker kkossev.thermostatLib, line 529

public List<String> pollOccupancy() { // library marker kkossev.thermostatLib, line 531
    return  zigbee.readAttribute(0x0406, 0x0000, [:], delay = 100)      // Bit 0 specifies the sensed occupancy as follows: 1 = occupied, 0 = unoccupied. This flag bit will affect the Occupancy attribute of HVAC cluster, and the operation mode. // library marker kkossev.thermostatLib, line 532
} // library marker kkossev.thermostatLib, line 533

////////////////////////////// added 02/16/2024 ////////////////////////////// // library marker kkossev.thermostatLib, line 535

// scheduled for call from setThermostatMode() 4 seconds after the mode was potentiually changed. // library marker kkossev.thermostatLib, line 537
// also, called every 1 minute from receiveCheck() // library marker kkossev.thermostatLib, line 538
void modeReceiveCheck() { // library marker kkossev.thermostatLib, line 539
    if (settings?.resendFailed != true) { return } // library marker kkossev.thermostatLib, line 540
    if (state.lastTx?.isModeSetReq != true) { return }    // no mode change was requested // library marker kkossev.thermostatLib, line 541

    if (state.lastTx.mode != device.currentState('thermostatMode', true).value) { // library marker kkossev.thermostatLib, line 543
        state.lastTx['setModeRetries'] = (state.lastTx['setModeRetries'] ?: 0) + 1 // library marker kkossev.thermostatLib, line 544
        logWarn "modeReceiveCheck() <b>failed</b> (expected ${state.lastTx['mode']}, current ${device.currentState('thermostatMode', true).value}), retry#${state.lastTx['setModeRetries']} of ${MaxRetries}" // library marker kkossev.thermostatLib, line 545
        if (state.lastTx['setModeRetries'] < MaxRetries) { // library marker kkossev.thermostatLib, line 546
            logDebug "resending mode command : ${state.lastTx['mode']}" // library marker kkossev.thermostatLib, line 547
            state.stats['txFailCtr'] = (state.stats['txFailCtr']  ?: 0) + 1 // library marker kkossev.thermostatLib, line 548
            setThermostatMode( state.lastTx['mode'] ) // library marker kkossev.thermostatLib, line 549
        } // library marker kkossev.thermostatLib, line 550
        else { // library marker kkossev.thermostatLib, line 551
            logWarn "modeReceiveCheck(${state.lastTx['mode'] }}) <b>giving up retrying<b/>" // library marker kkossev.thermostatLib, line 552
            state.lastTx['isModeSetReq'] = false    // giving up // library marker kkossev.thermostatLib, line 553
            state.lastTx['setModeRetries'] = 0 // library marker kkossev.thermostatLib, line 554
        } // library marker kkossev.thermostatLib, line 555
    } // library marker kkossev.thermostatLib, line 556
    else { // library marker kkossev.thermostatLib, line 557
        logDebug "modeReceiveCheck mode was changed OK to (${state.lastTx['mode']}). No need for further checks." // library marker kkossev.thermostatLib, line 558
        state.lastTx.isModeSetReq = false    // setting mode was successfuly confimed, no need for further checks // library marker kkossev.thermostatLib, line 559
        state.lastTx.setModeRetries = 0 // library marker kkossev.thermostatLib, line 560
    } // library marker kkossev.thermostatLib, line 561
} // library marker kkossev.thermostatLib, line 562

// // library marker kkossev.thermostatLib, line 564
//  also, called every 1 minute from receiveCheck() // library marker kkossev.thermostatLib, line 565
public void setpointReceiveCheck() { // library marker kkossev.thermostatLib, line 566
    if (settings?.resendFailed != true) { return } // library marker kkossev.thermostatLib, line 567
    if (state.lastTx.isSetPointReq != true) { return } // library marker kkossev.thermostatLib, line 568

    if (state.lastTx.setPoint != NOT_SET && ((state.lastTx.setPoint as String) != (state.lastRx.setPoint as String))) { // library marker kkossev.thermostatLib, line 570
        state.lastTx.setPointRetries = (state.lastTx.setPointRetries ?: 0) + 1 // library marker kkossev.thermostatLib, line 571
        if (state.lastTx.setPointRetries < MaxRetries) { // library marker kkossev.thermostatLib, line 572
            logWarn "setpointReceiveCheck(${state.lastTx.setPoint}) <b>failed<b/> (last received is still ${state.lastRx.setPoint})" // library marker kkossev.thermostatLib, line 573
            logDebug "resending setpoint command : ${state.lastTx.setPoint} (retry# ${state.lastTx.setPointRetries}) of ${MaxRetries}" // library marker kkossev.thermostatLib, line 574
            state.stats.txFailCtr = (state.stats.txFailCtr ?: 0) + 1 // library marker kkossev.thermostatLib, line 575
            // TODO !! sendTuyaHeatingSetpoint(state.lastTx.setPoint) // library marker kkossev.thermostatLib, line 576
            setHeatingSetpoint(state.lastTx.setPoint as Number) // library marker kkossev.thermostatLib, line 577
        } // library marker kkossev.thermostatLib, line 578
        else { // library marker kkossev.thermostatLib, line 579
            logWarn "setpointReceiveCheck(${state.lastTx.setPoint}) <b>giving up retrying<b/>" // library marker kkossev.thermostatLib, line 580
            state.lastTx.isSetPointReq = false // library marker kkossev.thermostatLib, line 581
            state.lastTx.setPointRetries = 0 // library marker kkossev.thermostatLib, line 582
        } // library marker kkossev.thermostatLib, line 583
    } // library marker kkossev.thermostatLib, line 584
    else { // library marker kkossev.thermostatLib, line 585
        logDebug "setpointReceiveCheck setPoint was changed successfuly to (${state.lastTx.setPoint}). No need for further checks." // library marker kkossev.thermostatLib, line 586
        state.lastTx.setPoint = NOT_SET // library marker kkossev.thermostatLib, line 587
        state.lastTx.isSetPointReq = false // library marker kkossev.thermostatLib, line 588
    } // library marker kkossev.thermostatLib, line 589
} // library marker kkossev.thermostatLib, line 590

public void setLastRx( int dp, int fncmd) { // library marker kkossev.thermostatLib, line 592
    state.lastRx['dp'] = dp // library marker kkossev.thermostatLib, line 593
    state.lastRx['fncmd'] = fncmd // library marker kkossev.thermostatLib, line 594
} // library marker kkossev.thermostatLib, line 595

public void setLastTx( String mode=null, Boolean isModeSetReq=null) { // library marker kkossev.thermostatLib, line 597
    if (mode != null) { // library marker kkossev.thermostatLib, line 598
        state.lastTx['mode'] = mode // library marker kkossev.thermostatLib, line 599
    } // library marker kkossev.thermostatLib, line 600
    if (isModeSetReq != null) { // library marker kkossev.thermostatLib, line 601
        state.lastTx['isModeSetReq'] = isModeSetReq // library marker kkossev.thermostatLib, line 602
    } // library marker kkossev.thermostatLib, line 603
} // library marker kkossev.thermostatLib, line 604

public String getLastMode() { // library marker kkossev.thermostatLib, line 606
    return state.lastTx.mode ?: 'exception' // library marker kkossev.thermostatLib, line 607
} // library marker kkossev.thermostatLib, line 608

public boolean checkIfIsDuplicated( int dp, int fncmd ) { // library marker kkossev.thermostatLib, line 610
    Map oldDpFncmd = state.lastRx // library marker kkossev.thermostatLib, line 611
    return dp == oldDpFncmd.dp && fncmd == oldDpFncmd.fncmd // library marker kkossev.thermostatLib, line 612
} // library marker kkossev.thermostatLib, line 613

// ~~~~~ end include (179) kkossev.thermostatLib ~~~~~
