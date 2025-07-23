/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateStringLiteral, ImplicitClosureParameter, MethodCount, MethodSize, NglParseError, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessarySetter, UnusedImport */
/**
 *  Aqara Climate Sensor W100 - Device Driver for Hubitat Elevation
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
 * ver. 1.0.0  2025-07-23 kkossev  - Initial version
 *
 */

static String version() { '1.0.0' }
static String timeStamp() { '2025/07/23 3:00 PM' }

@Field static final Boolean _DEBUG = true

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput
import java.math.RoundingMode

#include kkossev.commonLib
//#include kkossev.onOffLib
#include kkossev.batteryLib
#include kkossev.temperatureLib
#include kkossev.humidityLib
#include kkossev.xiaomiLib
#include kkossev.deviceProfileLib
//#include kkossev.thermostatLib

deviceType = 'Sensor' // Aqara Climate Sensor W100 is a Thermostat, but it is also a Temperature and Humidity Sensor
@Field static final String DEVICE_TYPE = 'Sensor'

metadata {
    definition(
        name: 'Aqara Climate Sensor W100',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20Climate%20Sensor%20W100/Aqara_Climate_Sensor_W100.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        attribute 'display_off', 'enum', ['disabled', 'enabled']   // 0xFCC0:0x0173
        attribute 'high_temperature', 'decimal'                    // 0xFCC0:0x0167
        attribute 'low_temperature', 'decimal'                     // 0xFCC0:0x0166
        attribute 'high_humidity', 'decimal'                       // 0xFCC0:0x0169
        attribute 'low_humidity', 'decimal'                        // 0xFCC0:0x016A
        attribute 'sampling', 'enum', ['low', 'standard', 'high', 'custom'] // 0xFCC0:0x0170
        attribute 'period', 'decimal'                              // 0xFCC0:0x016D
        
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
    // https://www.aqara.com/en/product/climate-sensor-w100/
    // https://www.zigbee2mqtt.io/devices/TH-S04D.html
    // https://github.com/Koenkk/zigbee2mqtt/issues/27262
    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/devices/lumi.ts#L4571 
    // https://github.com/rohankapoorcom/zigbee-herdsman-converters/blob/753c114f428d36e8164837922ea0ac89039f0bf6/src/devices/lumi.ts#L4569 
    'AQARA_CLIMATE_SENSOR_W100'   : [
            description   : 'Aqara Climate Sensor W100',
            device        : [manufacturers: ['Aqara'], type: 'Sensor', powerSource: 'battery', isSleepy:false],
            //capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode': true],
            capabilities  : ['ReportingConfiguration': false, 'TemperatureMeasurement': true, 'RelativeHumidityMeasurement': true, 'Battery': true, 'Configuration': true, 'Refresh': true, 'HealthCheck': true],
            preferences   : ['display_off':'0xFCC0:0x0173', 'high_temperature':'0xFCC0:0x0167', 'low_temperature':'0xFCC0:0x0166', 'high_humidity':'0xFCC0:0x016E', 'low_humidity':'0xFCC0:0x016D', 'sampling':'0xFCC0:0x0170', 'period':'0xFCC0:0x0162'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0012,0405,0402,00001,0003,0x0000,FD20', outClusters:'0019', model:'lumi.sensor_ht.agl001', manufacturer:'Aqara', deviceJoinName: 'Aqara Climate Sensor W100'],      //  "TH-S04D"
                [profileId:'0104', endpointId:'03', inClusters:'0012', model:'lumi.sensor_ht.agl001', manufacturer:'Aqara', deviceJoinName: 'Aqara Climate Sensor W100']      //  workaround for Hubitat bug with multiple endpoints

            ],
            commands      : ['sendSupportedThermostatModes':'sendSupportedThermostatModes', 'autoPollThermostat':'autoPollThermostat', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [:],
            attributes    : [
                [at:'0xFCC0:0x0173',  name:'display_off',       ep:'0x01', type:'enum',    dt:'0x10', mfgCode:'0x115f',  rw: 'rw', min:0,     max:1,     step:1,   scale:1,    map:[0: 'disabled', 1: 'enabled'], unit:'',     title: '<b>Display Off</b>',      description:'Enables/disables auto display off'],
                [at:'0xFCC0:0x0167',  name:'high_temperature',  ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:26.0,  max:60.0,  step:0.5, scale:100,  unit:'°C', title: '<b>High Temperature</b>', description:'High temperature alert'],
                [at:'0xFCC0:0x0166',  name:'low_temperature',   ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:-20.0, max:20.0,  step:0.5, scale:100,  unit:'°C', title: '<b>Low Temperature</b>', description:'Low temperature alert'],
                [at:'0xFCC0:0x016E',  name:'high_humidity',     ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:65.0,  max:100.0, step:1.0, scale:100,  unit:'%',   title: '<b>High Humidity</b>',   description:'High humidity alert'],
                [at:'0xFCC0:0x016D',  name:'low_humidity',      ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:0.0,   max:30.0,  step:1.0, scale:100,  unit:'%',   title: '<b>Low Humidity</b>',    description:'Low humidity alert'],
                [at:'0xFCC0:0x0170',  name:'sampling',          ep:'0x01', type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:1,     max:4,     step:1,   scale:1,    map:[1: 'low', 2: 'standard', 3: 'high', 4: 'custom'], unit:'', title: '<b>Sampling</b>', description:'Temperature and Humidity sampling settings'],
                [at:'0xFCC0:0x0162',  name:'period',            ep:'0x01', type:'decimal', dt:'0x23', mfgCode:'0x115f',  rw: 'rw', min:0.5,   max:600.0, step:0.5, scale:1000, unit:'sec', title: '<b>Sampling Period</b>', description:'Sampling period'], // result['period'] = (value / 1000).toFixed(1); - rw
            
                /*
                [at:'0xFCC0:0x040A',  name:'battery',               type:'number',  dt:'0x20', mfgCode:'0x115f',  rw: 'ro', min:0,    max:100,  step:1,  scale:1,    unit:'%',  description:'Battery percentage remaining'],
                [at:'0xFCC0:0x0270',  name:'unknown1',              type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',   title: '<b>Unknown 0x0270</b>',   description:'Unknown 0x0270'],
                [at:'0xFCC0:0x0279',  name:'awayPresetTemperature', type:'decimal', dt:'0x23', mfgCode:'0x115f',  rw: 'rw', min:5.0,  max:35.0, defVal:5.0,    step:0.5, scale:100,  unit:'°C', title: '<b>Away Preset Temperature</b>',       description:'Away preset temperature'],                     // result['away_preset_temperature'] = (value / 100).toFixed(1); - rw
                [at:'0x0201:0x001B',  name:'thermostatOperatingState', type:'enum',    dt:'0x30', rw:'rw',  min:0,    max:4,    step:1,  scale:1,    map:[0: 'off', 1: 'heating', 2: 'unknown', 3: 'unknown3', 4: 'idle'], unit:'',  description:'thermostatOperatingState (relay on/off status)'],      //  nothing happens when WRITING ????
            */
            ],
            //supportedThermostatModes: ['off', 'auto', 'heat', 'away'/*, "emergency heat"*/],
            //refresh: ['refreshAqaraE1'],
            deviceJoinName: 'Aqara Climate Sensor W100',
            configuration : [:]
    ]
]

// called from commonLib (Xiaomi cluster 0xFCC0 )
//
void customParseXiaomiFCC0Cluster(final Map descMap) {
    logDebug "customParseXiaomiFCC0Cluster: zigbee received Thermostat 0xFCC0 attribute 0x${descMap.attrId} (raw value = ${descMap.value})"
    if ((descMap.attrInt as Integer) == 0x00F7 ) {      // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
        final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
        customParseXiaomiClusterTags(tags)
        return
    }
    Boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "customParseXiaomiFCC0Cluster: received unknown Thermostat cluster (0xFCC0) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

// XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
// called from customParseXiaomiFCC0Cluster
//
void customParseXiaomiClusterTags(final Map<Integer, Object> tags) {
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
    /*
    final int pollingInterval = (settings.temperaturePollingInterval as Integer) ?: 0
    if (pollingInterval > 0) {
        logInfo "updatedThermostat: scheduling temperature polling every ${pollingInterval} seconds"
        scheduleThermostatPolling(pollingInterval)
    }
    else {
        unScheduleThermostatPolling()
        logInfo 'updatedThermostat: thermostat polling is disabled!'
    }
    */
    // Itterates through all settings
    logDebug 'customUpdated: updateAllPreferences()...'
    updateAllPreferences()
}

/*
// binding and reporting configuration for this Aqara E1 thermostat does nothing... We need polling mechanism for faster updates of the internal temperature readings.
List<String> refreshAqaraE1() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0011, 0x0012, 0x001B, 0x001C], [:], delay = 3500)       // 0x0000 = local temperature, 0x0011 = cooling setpoint, 0x0012 = heating setpoint, 0x001B = controlledSequenceOfOperation, 0x001C = system mode (enum8 )
    cmds += zigbee.readAttribute(0xFCC0, [0x0271, 0x0272, 0x0273, 0x0274, 0x0275, 0x0277, 0x0279, 0x027A, 0x027B, 0x027E], [mfgCode: 0x115F], delay = 3500)
    cmds += zigbee.readAttribute(0xFCC0, 0x040a, [mfgCode: 0x115F], delay = 500)
    return cmds
}
*/

List<String> customRefresh() {
    // TODO - use the refreshFromDeviceProfileList() !
    /*
    List<String> cmds = refreshFromDeviceProfileList()
    */
    List<String> cmds = []
    // Unsupported Attribute !!
    //cmds += zigbee.readAttribute(0x0001, [0x0020, 0x0021], [destEndpoint: 0x01], delay = 200)    // battery voltage and battery percentage remaining
    cmds += zigbee.readAttribute(0x0402, 0x0000, [destEndpoint: 0x01], 200)    // temperature
    cmds += zigbee.readAttribute(0x0405, 0x0000, [destEndpoint: 0x01], 200)    // humidity
    cmds += zigbee.readAttribute(0xFCC0, 0x0173, [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // display_off
    cmds += zigbee.readAttribute(0xFCC0, [0x0167, 0x0166, 0x016E, 0x016D], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // high_temperature, low_temperature, high_humidity, low_humidity
    cmds += zigbee.readAttribute(0xFCC0, [0x0170, 0x0162], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // sampling, period
    /*
    cmds += zigbee.readAttribute(0xFCC0, 0x0165, [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // temp_report_mode
    cmds += zigbee.readAttribute(0xFCC0, 0x0163, [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // temp_period
    cmds += zigbee.readAttribute(0xFCC0, 0x0164, [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // temp_threshold
    cmds += zigbee.readAttribute(0xFCC0, 0x016C, [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // humi_report_mode
    cmds += zigbee.readAttribute(0xFCC0, 0x016B, [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // humi_period
*/

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
    /*
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
    */
    List<String> cmds = []
    cmds += zigbee.readAttribute(0xFCC0, 0x0173, [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // display_off
    logDebug "testT: ${cmds} "
    sendZigbeeCommands(cmds)    
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
