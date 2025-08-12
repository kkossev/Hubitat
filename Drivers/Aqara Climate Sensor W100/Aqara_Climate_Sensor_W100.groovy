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
 * ver. 1.1.0  2025-07-26 kkossev  - added external temperature and humidity sensor support
 * ver. 1.2.0  2025-08-12 kkossev  - (dev. branch) HVAC Thermostat support - work-in-progress
 *
 *                        TODO: 0x0168 and 0x016F attributes (alarms)
 *                        TODO: add support for external temperature and humidity sensors
 *                        TODO: add support for battery level reporting
 *                        TODO: foundMap.advanced == true && settings.advancedOptions != true
 */

static String version() { '1.2.0' }
static String timeStamp() { '2025/08/12 4:41 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput
import java.math.RoundingMode

#include kkossev.commonLib
#include kkossev.batteryLib
#include kkossev.temperatureLib
#include kkossev.humidityLib
#include kkossev.buttonLib
#include kkossev.xiaomiLib
#include kkossev.deviceProfileLib
//#include kkossev.thermostatLib

deviceType = 'Sensor' // Aqara Climate Sensor W100 is not a Thermostat, but a Temperature and Humidity Sensor
@Field static final String DEVICE_TYPE = 'Sensor'

metadata {
    definition(
        name: 'Aqara Climate Sensor W100',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Drivers/Aqara%20Climate%20Sensor%20W100/Aqara_Climate_Sensor_W100_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        attribute 'displayOff', 'enum', ['disabled', 'enabled']   // 0xFCC0:0x0173
        attribute 'highTemperature', 'decimal'                    // 0xFCC0:0x0167
        attribute 'lowTemperature', 'decimal'                     // 0xFCC0:0x0166
        attribute 'highHumidity', 'decimal'                       // 0xFCC0:0x0169
        attribute 'lowHumidity', 'decimal'                        // 0xFCC0:0x016A
        attribute 'sampling', 'enum', ['low', 'standard', 'high', 'custom'] // 0xFCC0:0x0170
        attribute 'period', 'decimal'                             // 0xFCC0:0x016D
        attribute 'tempReportMode', 'enum', ['no', 'threshold', 'period', 'threshold_period'] // 0xFCC0:0x0165
        attribute 'tempPeriod', 'decimal'                         // 0xFCC0:0x0163
        attribute 'tempThreshold', 'decimal'                      // 0xFCC0:0x0164
        attribute 'humiReportMode', 'enum', ['no', 'threshold', 'period', 'threshold_period'] // 0xFCC0:0x016C
        attribute 'humiPeriod', 'decimal'                         // 0xFCC0:0x016A
        attribute 'humiThreshold', 'decimal'                      // 0xFCC0:0x016B
        attribute 'sensor', 'enum', ['internal', 'external']       // 0xFCC0:0x0172
        attribute 'externalTemperature', 'decimal'                // Virtual attribute for external temperature
        attribute 'externalHumidity', 'decimal'                   // Virtual attribute for external humidity
        attribute 'powerOutageCount', 'number'                     // Power outage counter from 0x00F7 reports
        
        // HVAC Thermostat capabilities and attributes
        capability 'Thermostat'
        capability 'ThermostatHeatingSetpoint'
        capability 'ThermostatCoolingSetpoint'
        capability 'ThermostatMode'
        capability 'ThermostatOperatingState'
        capability 'ThermostatSetpoint'
        capability 'FanControl'
        
        attribute 'hvacMode', 'enum', ['disabled', 'enabled']
        attribute 'thermostatMode', 'enum', ['off', 'heat', 'cool', 'auto']
        attribute 'thermostatOperatingState', 'enum', ['idle', 'heating', 'cooling', 'fan only']
        attribute 'heatingSetpoint', 'number'
        attribute 'coolingSetpoint', 'number'
        attribute 'thermostatSetpoint', 'number'
        attribute 'fanMode', 'enum', ['auto', 'low', 'medium', 'high']
        attribute 'hvacDisplayMode', 'enum', ['normal', 'auto_off']
        attribute 'supportedThermostatModes', 'JSON_OBJECT'
        attribute 'supportedThermostatFanModes', 'JSON_OBJECT'
        
        // Button capabilities
        capability 'PushableButton'
        capability 'HoldableButton'
        capability 'ReleasableButton'
        capability 'DoubleTapableButton'
        
        attribute 'numberOfButtons', 'number'
        attribute 'supportedButtonValues', 'JSON_OBJECT'
        attribute 'pushed', 'number'
        attribute 'held', 'number'
        attribute 'released', 'number'
        attribute 'doubleTapped', 'number'
        
        command 'setExternalTemperature', [[name: 'temperature', type: 'NUMBER', description: 'External temperature value (-100 to 100°C)', range: '-100..100', required: true]]
        command 'setExternalHumidity', [[name: 'humidity', type: 'NUMBER', description: 'External humidity value (0 to 100%)', range: '0..100', required: true]]
        command 'setSensorMode', [[name: 'mode', type: 'ENUM', constraints: ['internal', 'external'], description: 'Set sensor mode: internal or external']]
        command 'setExternalThermostat', [[name: 'mode', type: 'ENUM', constraints: ['disabled', 'enabled'], description: 'Enable or disable external thermostat connection']]
        
        // HVAC Thermostat commands
        command 'enableHVAC', [[name: 'Enable HVAC thermostat mode']]
        command 'disableHVAC', [[name: 'Disable HVAC thermostat mode']]
        command 'setThermostatMode', [[name: 'mode', type: 'ENUM', constraints: ['off', 'heat', 'cool', 'auto'], description: 'Set thermostat mode']]
        command 'setHeatingSetpoint', [[name: 'temperature', type: 'NUMBER', description: 'Set heating setpoint (10-35°C)', range: '10..35', required: true]]
        command 'setCoolingSetpoint', [[name: 'temperature', type: 'NUMBER', description: 'Set cooling setpoint (10-35°C)', range: '10..35', required: true]]
        command 'setThermostatSetpoint', [[name: 'temperature', type: 'NUMBER', description: 'Set thermostat setpoint (10-35°C)', range: '10..35', required: true]]
        command 'setFanMode', [[name: 'mode', type: 'ENUM', constraints: ['auto', 'low', 'medium', 'high'], description: 'Set fan mode']]
        command 'sendPMTSDCommand', [[name: 'power', type: 'NUMBER', description: 'Power (0=On, 1=Off)', range: '0..1'], [name: 'mode', type: 'NUMBER', description: 'Mode (0=Cool, 1=Heat, 2=Auto)', range: '0..2'], [name: 'temp', type: 'NUMBER', description: 'Temperature (10-35°C)', range: '10..35'], [name: 'speed', type: 'NUMBER', description: 'Fan Speed (0=Auto, 1=Low, 2=Med, 3=High)', range: '0..3'], [name: 'display', type: 'NUMBER', description: 'Display mode (0/1)', range: '0..1']]
        
        if (_DEBUG) { 
            command 'testT', [[name: 'testT', type: 'STRING', description: 'testT', defaultValue : '']]
            command 'getCounterCommand', [[name: 'Get current counter value']]
            command 'resetCounterCommand', [[name: 'value', type: 'NUMBER', description: 'Reset counter to value (default 0x10)', range: '0..255']]
            command 'testHVACEnable', [[name: 'Test HVAC enable command']]
            command 'testPMTSDSend', [[name: 'pmtsdString', type: 'STRING', description: 'Test PMTSD string (e.g., P0_M1_T22_S0_D0)', defaultValue: 'P0_M1_T22_S0_D0']]
            command 'parseTestPMTSD', [[name: 'hexData', type: 'STRING', description: 'Parse test PMTSD hex data']]
        }

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
            capabilities  : ['ReportingConfiguration': false, 'TemperatureMeasurement': true, 'RelativeHumidityMeasurement': true, 'Battery': true, 'BatteryVoltage': true, 'Configuration': true, 'Refresh': true, 'HealthCheck': true, 'Thermostat': true, 'ThermostatMode': true, 'ThermostatSetpoint': true, 'FanControl': true],
            preferences   : ['displayOff':'0xFCC0:0x0173', 'highTemperature':'0xFCC0:0x0167', 'lowTemperature':'0xFCC0:0x0166', 'highHumidity':'0xFCC0:0x016E', 'lowHumidity':'0xFCC0:0x016D', 'sampling':'0xFCC0:0x0170', 'period':'0xFCC0:0x0162', 'tempReportMode':'0xFCC0:0x0165', 'tempPeriod':'0xFCC0:0x0163', 'tempThreshold':'0xFCC0:0x0164', 'humiReportMode':'0xFCC0:0x016C', 'humiPeriod':'0xFCC0:0x016A', 'humiThreshold':'0xFCC0:0x016B', 'sensor':'0xFCC0:0x0172'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0012,0405,0402,00001,0003,0x0000,FD20', outClusters:'0019', model:'lumi.sensor_ht.agl001', manufacturer:'Aqara', deviceJoinName: 'Aqara Climate Sensor W100'],      //  "TH-S04D" - main endpoint
                [profileId:'0104', endpointId:'02', inClusters:'0012', model:'lumi.sensor_ht.agl001', manufacturer:'Aqara', deviceJoinName: 'Aqara Climate Sensor W100'],      //  center button endpoint
                [profileId:'0104', endpointId:'03', inClusters:'0012', model:'lumi.sensor_ht.agl001', manufacturer:'Aqara', deviceJoinName: 'Aqara Climate Sensor W100']      //  minus button endpoint

            ],
            commands      : ['sendSupportedThermostatModes':'sendSupportedThermostatModes', 'autoPollThermostat':'autoPollThermostat', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [:],
            attributes    : [
                [at:'0xFCC0:0x0173',  name:'displayOff',       ep:'0x01', type:'enum',    dt:'0x10', mfgCode:'0x115f',  rw: 'rw', min:0,     max:1,     step:1,   scale:1,    map:[0: 'disabled', 1: 'enabled'], unit:'',     title: '<b>Display Off</b>',      description:'Enables/disables auto display off'],
                [at:'0xFCC0:0x0167',  name:'highTemperature',  ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:26.0,  max:60.0,  step:0.5, scale:100,  unit:'°C', title: '<b>High Temperature</b>', description:'High temperature alert'],
                [at:'0xFCC0:0x0166',  name:'lowTemperature',   ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:-20.0, max:20.0,  step:0.5, scale:100,  unit:'°C', title: '<b>Low Temperature</b>', description:'Low temperature alert'],
                [at:'0xFCC0:0x016E',  name:'highHumidity',     ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:65.0,  max:100.0, step:1.0, scale:100,  unit:'%',   title: '<b>High Humidity</b>',   description:'High humidity alert'],
                [at:'0xFCC0:0x016D',  name:'lowHumidity',      ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:0.0,   max:30.0,  step:1.0, scale:100,  unit:'%',   title: '<b>Low Humidity</b>',    description:'Low humidity alert'],
                [at:'0xFCC0:0x0170',  name:'sampling',          ep:'0x01', type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:1,     max:4,     step:1,   scale:1,    map:[1: 'low', 2: 'standard', 3: 'high', 4: 'custom'], unit:'', title: '<b>Sampling</b>', description:'Temperature and Humidity sampling settings'],
                [at:'0xFCC0:0x0162',  name:'period',            ep:'0x01', type:'decimal', dt:'0x23', mfgCode:'0x115f',  rw: 'rw', min:0.5,   max:600.0, step:0.5, scale:1000, unit:'sec', title: '<b>Sampling Period</b>', description:'Sampling period'], // result['period'] = (value / 1000).toFixed(1); - rw
                [at:'0xFCC0:0x0165',  name:'tempReportMode',  ep:'0x01', type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,     max:3,     step:1,   scale:1,    map:[0: 'no', 1: 'threshold', 2: 'period', 3: 'threshold_period'], unit:'', title: '<b>Temperature Report Mode</b>', description:'Temperature reporting mode'],
                [at:'0xFCC0:0x0163',  name:'tempPeriod',       ep:'0x01', type:'decimal', dt:'0x23', mfgCode:'0x115f',  rw: 'rw', min:1.0,   max:10.0,  step:1.0, scale:1000, unit:'sec', title: '<b>Temperature Period</b>', description:'Temperature reporting period'],
                [at:'0xFCC0:0x0164',  name:'tempThreshold',    ep:'0x01', type:'decimal', dt:'0x21', mfgCode:'0x115f',  rw: 'rw', min:0,     max:3,     step:0.1, scale:100,  unit:'°C', title: '<b>Temperature Threshold</b>', description:'Temperature reporting threshold'],
                [at:'0xFCC0:0x016C',  name:'humiReportMode',  ep:'0x01', type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,     max:3,     step:1,   scale:1,    map:[0: 'no', 1: 'threshold', 2: 'period', 3: 'threshold_period'], unit:'', title: '<b>Humidity Report Mode</b>', description:'Humidity reporting mode'],
                [at:'0xFCC0:0x016A',  name:'humiPeriod',       ep:'0x01', type:'decimal', dt:'0x23', mfgCode:'0x115f',  rw: 'rw', min:1.0,   max:10.0,  step:1.0, scale:1000, unit:'sec', title: '<b>Humidity Period</b>', description:'Humidity reporting period'],
                [at:'0xFCC0:0x016B',  name:'humiThreshold',    ep:'0x01', type:'decimal', dt:'0x21', mfgCode:'0x115f',  rw: 'rw', min:2.0,   max:10.0,  step:0.5, scale:100,  unit:'%', title: '<b>Humidity Threshold</b>', description:'Humidity reporting threshold'],
                [at:'0xFCC0:0x0172',  name:'sensor',            ep:'0x01', type:'enum',    dt:'0x23', mfgCode:'0x115f',  rw: 'ro', min:0,     max:255,   step:1,   scale:1,    map:[0: 'internal', 1: 'internal', 2: 'external', 3: 'external', 255: 'unknown'], unit:'', title: '<b>Sensor Mode</b>', description:'Select sensor mode: internal or external'],
                // HVAC Thermostat attributes
                [at:'virtual',        name:'hvacMode',          ep:'0x01', type:'enum',    dt:'0x10', mfgCode:'0x115f',  rw: 'rw', min:0,     max:1,     step:1,   scale:1,    map:[0: 'disabled', 1: 'enabled'], unit:'', title: '<b>HVAC Mode</b>', description:'Enable/disable HVAC thermostat mode'],
                [at:'virtual',        name:'thermostatMode',    ep:'0x01', type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,     max:3,     step:1,   scale:1,    map:[0: 'off', 1: 'heat', 2: 'cool', 3: 'auto'], unit:'', title: '<b>Thermostat Mode</b>', description:'Thermostat operating mode'],
                [at:'virtual',        name:'heatingSetpoint',   ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:10.0,  max:35.0,  step:0.5, scale:100,  unit:'°C', title: '<b>Heating Setpoint</b>', description:'Heating temperature setpoint'],
                [at:'virtual',        name:'coolingSetpoint',   ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:10.0,  max:35.0,  step:0.5, scale:100,  unit:'°C', title: '<b>Cooling Setpoint</b>', description:'Cooling temperature setpoint'],
                [at:'virtual',        name:'thermostatSetpoint', ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:10.0,  max:35.0,  step:0.5, scale:100,  unit:'°C', title: '<b>Thermostat Setpoint</b>', description:'Current temperature setpoint'],
                [at:'virtual',        name:'fanMode',           ep:'0x01', type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,     max:3,     step:1,   scale:1,    map:[0: 'auto', 1: 'low', 2: 'medium', 3: 'high'], unit:'', title: '<b>Fan Mode</b>', description:'Fan speed mode'],
                [at:'virtual',        name:'thermostatOperatingState', ep:'0x01', type:'enum', dt:'0x20', mfgCode:'0x115f', rw: 'ro', min:0, max:3, step:1, scale:1, map:[0: 'idle', 1: 'heating', 2: 'cooling', 3: 'fan only'], unit:'', title: '<b>Operating State</b>', description:'Current thermostat operating state'],
            ],
            supportedThermostatModes: ['off', 'heat', 'cool', 'auto'],
            supportedThermostatFanModes: ['auto', 'low', 'medium', 'high'],
            //refresh: ['refreshAqaraE1'],
            deviceJoinName: 'Aqara Climate Sensor W100',
            configuration : [:]
    ]
]

// called from commonLib (Xiaomi cluster 0xFCC0 )
//
void customParseXiaomiFCC0Cluster(final Map descMap) {
    logDebug "customParseXiaomiFCC0Cluster: zigbee received 0xFCC0 attribute 0x${descMap.attrId} (raw value = ${descMap.value})"
    if ((descMap.attrInt as Integer) == 0x00F7 ) {      // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
        final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
        customParseXiaomiClusterTags(tags)
        return
    }
    
    // Handle external sensor response attribute 0xFFF2
    if ((descMap.attrInt as Integer) == 0xFFF2 ) {
        logDebug "customParseXiaomiFCC0Cluster: received external sensor response attribute 0xFFF2"
        parseExternalSensorResponse(descMap.value)
        return
    }
    
    Boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "customParseXiaomiFCC0Cluster: received cluster (0xFCC0) unknown attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

// XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
// called from customParseXiaomiFCC0Cluster
//
void customParseXiaomiClusterTags(final Map<Integer, Object> tags) {
    tags.each { final Integer tag, final Object value ->
        //log.trace "xiaomi decode tag: 0x${intToHexStr(tag, 1)} value=${value}"
        switch (tag) {
            case 0x01:    // battery voltage
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})"
                // Send battery voltage event using the battery library
                sendBatteryVoltageEvent(value as Integer)
                if ((settings.voltageToPercent ?: false) == true) {
                    sendBatteryVoltageEvent(value as Integer, convertToPercent = true)
                }
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
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} sensor mode/state is ${value}"
                // May indicate sensor mode or operational state
                break
            case 0x11:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} trigger count is ${value}"
                // Button press or sensor trigger count
                if (state.health == null) { state.health = [:] }
                state.health['triggerCount'] = value
                break
            case 0x64:  // (100)
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value / 100}°C (raw ${value})"
                // W100 main temperature sensor reading
                //handleTemperatureEvent(value / 100.0)
                break
            case 0x65:  // (101)
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100}% (raw ${value})"
                // W100 humidity sensor reading
                //handleHumidityEvent(value / 100.0)
                break
            case 0x66:  // (102)    TODO - check if this is an alternative battery percentage reading ?
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature (alternate) is ${value / 100}°C (raw ${value})"
                // Alternative temperature reading - may be external sensor
                if (device.currentValue('sensor') == 'external') {
                    BigDecimal tempValue = (value / 100.0).setScale(1, RoundingMode.HALF_UP)
                    sendEvent(name: 'externalTemperature', value: tempValue, unit: '°C', 
                             descriptionText: "External temperature: ${tempValue}°C", type: 'physical')
                    logInfo "External temperature updated: ${tempValue}°C"
                }
                break
            case 0x67:  // (103)
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity (alternate) is ${value / 100}% (raw ${value})"
                // Alternative humidity reading - may be external sensor
                if (device.currentValue('sensor') == 'external') {
                    BigDecimal humidityValue = (value / 100.0).setScale(1, RoundingMode.HALF_UP)
                    sendEvent(name: 'externalHumidity', value: humidityValue, unit: '%', 
                             descriptionText: "External humidity: ${humidityValue}%", type: 'physical')
                    logInfo "External humidity updated: ${humidityValue}%"
                }
                break
            case 0x68:  // (104)
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} power outage count is ${value}"
                // Power outage counter - useful for device health monitoring
                if (state.health == null) { state.health = [:] }
                state.health['powerOutageCount'] = value
                sendEvent(name: 'powerOutageCount', value: value, descriptionText: "Power outage count: ${value}", type: 'physical')
                break
            case 0x69:  // (105)
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery percentage is ${value}% (raw ${value})"
                // Battery percentage from device (if available)
                if (value > 0 && value <= 100) {
                    sendBatteryPercentageEvent(value as Integer)
                }
                break
            case 0x6a:  // (106)
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device uptime is ${value} (raw ${value})"
                // Device uptime information
                if (state.health == null) { state.health = [:] }
                state.health['uptime'] = value
                break
            default:
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
        }
    }
}

// Initialize button functionality
void initializeButtons() {
    logDebug "initializeButtons: setting up 3 buttons (plus, center, minus)"
    sendNumberOfButtonsEvent(3)
    sendSupportedButtonValuesEvent(['pushed', 'held', 'doubleTapped', 'released'])
}

// Custom push implementation for buttonLib Momentary capability
void customPush() {
    logDebug "customPush: momentary push for W100"
    // For W100, we can simulate pushing the center button (button 2)
    sendButtonEvent(2, 'pushed', isDigital = true)
}

// Custom push implementation for buttonLib PushableButton capability
void customPush(Integer buttonNumber) {
    logDebug "customPush: simulating push for button ${buttonNumber}"
    if (buttonNumber >= 1 && buttonNumber <= 3) {
        sendButtonEvent(buttonNumber, 'pushed', isDigital = true)
    } else {
        logWarn "customPush: invalid button number ${buttonNumber}, valid range is 1-3"
    }
}

// Button parsing for W100 - based on GitHub zigbee2mqtt implementation
void parseButtonAction(final Map descMap) {
    // From GitHub: actionLookup: {hold: 0, single: 1, double: 2, release: 255}
    // endpointNames: ["plus", "center", "minus"] - endpoints 1, 2, 3
    
    logDebug "parseButtonAction: endpoint ${descMap.endpoint}, cluster ${descMap.cluster ?: descMap.clusterId}, attr ${descMap.attrId}, value ${descMap.value}"
    
    Integer endpoint = Integer.parseInt(descMap.endpoint ?: "01", 16)
    String buttonName = getButtonName(endpoint)
    
    if (buttonName == 'unknown') {
        logWarn "parseButtonAction: unknown button endpoint ${endpoint}"
        return
    }
    
    // Parse the action value - looking for cluster 0x0012 multistate input reports
    String cluster = descMap.cluster ?: descMap.clusterId
    if (cluster == '0012' && descMap.attrId == '0055') {
        Integer actionValue = Integer.parseInt(descMap.value, 16)
        String action = getButtonAction(actionValue)
        
        if (action != 'unknown') {
            logInfo "Button ${buttonName} (${endpoint}) ${action}"
            
            // Use buttonLib's sendButtonEvent function
            sendButtonEvent(endpoint, action, isDigital = false)
        } else {
            logWarn "parseButtonAction: unknown action value ${actionValue} for button ${buttonName}"
        }
    } else {
        logDebug "parseButtonAction: skipping - cluster=${cluster}, attrId=${descMap.attrId}"
    }
}

// Get button name from endpoint number
String getButtonName(Integer endpoint) {
    switch (endpoint) {
        case 1: return 'plus'
        case 2: return 'center' 
        case 3: return 'minus'
        default: return 'unknown'
    }
}

// Get button action from value
String getButtonAction(Integer value) {
    // From GitHub actionLookup: {hold: 0, single: 1, double: 2, release: 255}
    switch (value) {
        case 0: return 'held'
        case 1: return 'pushed'
        case 2: return 'doubleTapped'
        case 255: return 'released'
        default: return 'unknown'
    }
}

// Custom parsing for multistate input cluster (0x0012) - button events
void customParseMultistateInputCluster(final Map descMap) {
    logDebug "customParseMultistateInputCluster: cluster 0x0012 endpoint ${descMap.endpoint} attribute 0x${descMap.attrId} value ${descMap.value}"
    
    // W100 buttons send multistate input events on cluster 0x0012, attribute 0x0055
    if (descMap.attrId == '0055') {
        parseButtonAction(descMap)
    } else {
        logDebug "customParseMultistateInputCluster: unhandled attribute 0x${descMap.attrId}"
    }
}

// Parse external sensor response from attribute 0xFFF2
void parseExternalSensorResponse(String value) {
    logDebug "parseExternalSensorResponse: parsing response value: ${value}"
    
    try {
        if (!value || value.length() < 2) {
            logWarn "parseExternalSensorResponse: invalid response value length"
            return
        }
        
        // Check if this is an HVAC PMTSD response by looking for ASCII payload markers
        // HVAC responses contain 0x08, 0x44 sequence followed by ASCII data
        if (value.contains('0844') || value.toUpperCase().contains('0844')) {
            logDebug "parseExternalSensorResponse: detected HVAC PMTSD response"
            parsePMTSDResponse(value)
            return
        }
        
        // Handle external sensor mode response (original functionality)
        // Convert hex string to integer for basic parsing
        Integer responseValue = Integer.parseInt(value.substring(0, 2), 16)
        
        // Parse sensor mode from response (based on GitHub lookup: {2: "external", 0: "internal", 1: "internal", 3: "external"})
        String sensorMode = null
        switch (responseValue) {
            case 0:
            case 1:
                sensorMode = 'internal'
                break
            case 2:
            case 3:
                sensorMode = 'external'
                break
            default:
                logWarn "parseExternalSensorResponse: unknown sensor mode value: ${responseValue}"
                return
        }
            
        // Update the sensor attribute if it changed
        if (device.currentValue('sensor') != sensorMode) {
            sendEvent(name: 'sensor', value: sensorMode, descriptionText: "Sensor mode changed to ${sensorMode}", type: 'physical')
            logInfo "Sensor mode updated to: ${sensorMode}"
        }
    } catch (Exception e) {
        logWarn "parseExternalSensorResponse: error parsing response: ${e.message}"
    }
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


List<String> customRefresh() {
    // TODO - use the refreshFromDeviceProfileList() !
    /*
    List<String> cmds = refreshFromDeviceProfileList()
    */
    List<String> cmds = []
    
    // Standard Zigbee clusters
    cmds += zigbee.readAttribute(0x0402, 0x0000, [destEndpoint: 0x01], 200)    // temperature
    cmds += zigbee.readAttribute(0x0405, 0x0000, [destEndpoint: 0x01], 200)    // humidity
    
    // Aqara proprietary cluster attributes - grouped reads
    cmds += zigbee.readAttribute(0xFCC0, [0x0172, 0x0173], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // sensor, displayOff
    cmds += zigbee.readAttribute(0xFCC0, [0x0167, 0x0166], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // highTemperature, lowTemperature
    cmds += zigbee.readAttribute(0xFCC0, [0x016E, 0x016D], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // highHumidity, lowHumidity
    cmds += zigbee.readAttribute(0xFCC0, [0x0170, 0x0162], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // sampling, period
    cmds += zigbee.readAttribute(0xFCC0, [0x0165, 0x0163, 0x0164], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // tempReportMode, tempPeriod, tempThreshold
    cmds += zigbee.readAttribute(0xFCC0, [0x016C, 0x016A, 0x016B], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // humiReportMode, humiPeriod, humiThreshold
    cmds += zigbee.readAttribute(0xFCC0, 0x00F7, [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
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
    logDebug 'initializeAqara() ...'
    return cmds
}

// called from initializeDevice in the commonLib code
List<String> customInitializeDevice() {
    List<String> cmds = []
    logDebug 'customInitializeDevice() ...'
    cmds = initializeAqara()
    
    // Initialize button functionality
    initializeButtons()
    
    logDebug "initializeThermostat() : ${cmds}"
    return cmds
}

void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (state.deviceProfile == null) {
        setDeviceNameAndProfile()               // in deviceProfileiLib.groovy
    }
    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
    // Initialize counter if not present
    if (state.counter == null) {
        state.counter = 0x10  // Start from 0x10 like other functions in the driver
        logDebug "customInitializeVars: initialized counter to ${state.counter}"
    }
    
    // Initialize HVAC state variables
    if (state.hvac == null) {
        state.hvac = [:]
        state.hvac.enabled = false
        state.hvac.mode = 'off'  // off, heat, cool, auto
        state.hvac.heatingSetpoint = 22.0
        state.hvac.coolingSetpoint = 24.0
        state.hvac.fanMode = 'auto'  // auto, low, medium, high
        state.hvac.operatingState = 'idle'  // idle, heating, cooling, fan only
        state.hvac.displayMode = 'normal'  // normal, auto_off
        logDebug "customInitializeVars: initialized HVAC state variables"
    }
    
    // Initialize device MAC addresses for HVAC commands (will be updated when needed)
    if (state.deviceMAC == null) {
        state.deviceMAC = device.zigbeeId ?: 'unknown'
        logDebug "customInitializeVars: device MAC = ${state.deviceMAC}"
    }
    if (state.hubMAC == null) {
        state.hubMAC = 'unknown'  // Will be set when HVAC is enabled
        logDebug "customInitializeVars: hub MAC placeholder set"
    }
}

// called from initializeVars() in the main code ...
void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents(${fullInit})"
}

List<String> customAqaraBlackMagic() {
    logDebug 'customAqaraBlackMagic() - not needed...'
    List<String> cmds = []
    return cmds
}

// called from processFoundItem  (processTuyaDPfromDeviceProfile and ) processClusterAttributeFromDeviceProfile in deviceProfileLib when a Zigbee message was found defined in the device profile map
//
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
void customProcessDeviceProfileEvent(final Map descMap, final String name, final valueScaled, final String unitText, final String descText) {
    logTrace "customProcessDeviceProfileEvent(${name}, ${valueScaled}) called"
    Map eventMap = [name: name, value: valueScaled, unit: unitText, descriptionText: descText, type: 'physical', isStateChange: true]
    switch (name) {
        case 'temperature' :
            handleTemperatureEvent(valueScaled as Float)
            break
        case 'sensor' :    // Sensor mode selection
            sendEvent(eventMap)
            logInfo "${descText}"
            
            // When switching to internal mode, we might want to trigger a refresh of internal sensor readings
            if (valueScaled == 'internal') {
                logDebug "Sensor mode switched to internal - refreshing internal sensor readings"
                runInMillis(1000, customRefresh)
            }
            break
        default :
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event !
                //if (!doNotTrace) {
            logDebug "event ${name} sent w/ value ${valueScaled}"
            logInfo "${descText}"                                 // send an Info log also (because value changed )  // TODO - check whether Info log will be sent also for spammy DPs ?
            //}
            break
    }
}

// Command to set external temperature using GitHub-compliant protocol
// https://github.com/13717033460/zigbee-herdsman-converters/blob/4ac6b66c7dd37e7b90a7487f3aa796f491dd24af/src/lib/lumi.ts#L2534
void setExternalTemperature(BigDecimal temperature) {
    logDebug "setExternalTemperature(${temperature}) - START"
    
    String currentSensorMode = device.currentValue('sensor')
    logDebug "setExternalTemperature: current sensor mode = '${currentSensorMode}'"
    if (currentSensorMode != 'external') {
        logWarn "setExternalTemperature: sensor mode is '${currentSensorMode}', must be 'external' to set external temperature"
        //return
    }
    
    if (temperature < -100 || temperature > 100) {
        logWarn "setExternalTemperature: temperature ${temperature} is out of range (-100 to 100°C)"
        //return
    }
    
    List<String> cmds = []
    
    // Fixed fictive sensor IEEE address from GitHub implementation
    byte[] fictiveSensor = hubitat.helper.HexUtils.hexStringToByteArray("00158d00019d1b98")
    logDebug "setExternalTemperature: fictiveSensor = ${fictiveSensor.collect { String.format('%02X', it & 0xFF) }.join('')}"
    
    // Build temperature buffer using writeFloatBE like GitHub implementation
    byte[] temperatureBuf = new byte[4]
    Float tempValue = Math.round(temperature * 100) as Float
    logDebug "setExternalTemperature: tempValue (Float) = ${tempValue}"
    
    // Convert to IEEE 754 32-bit float and write as big-endian (GitHub uses writeFloatBE)
    int floatBits = Float.floatToIntBits(tempValue)
    logDebug "setExternalTemperature: IEEE 754 floatBits = 0x${String.format('%08X', floatBits)} (${floatBits})"
    temperatureBuf[0] = (byte)((floatBits >> 24) & 0xFF)
    temperatureBuf[1] = (byte)((floatBits >> 16) & 0xFF)
    temperatureBuf[2] = (byte)((floatBits >> 8) & 0xFF)
    temperatureBuf[3] = (byte)(floatBits & 0xFF)
    logDebug "setExternalTemperature: temperatureBuf bytes = ${temperatureBuf.collect { String.format('%02X', it & 0xFF) }.join('')}"
    
    // Build params array exactly like GitHub: [...fictiveSensor, 0x00, 0x01, 0x00, 0x55, ...temperatureBuf]
    List<Integer> params = []
    params.addAll(fictiveSensor.collect { it & 0xFF })
    logDebug "setExternalTemperature: params after fictiveSensor = ${params.collect { String.format('%02X', it) }.join('')}"
    params.addAll([0x00, 0x01, 0x00, 0x55])
    logDebug "setExternalTemperature: params after temp identifier = ${params.collect { String.format('%02X', it) }.join('')}"
    params.addAll(temperatureBuf.collect { it & 0xFF })
    logDebug "setExternalTemperature: final params (${params.size()} bytes) = ${params.collect { String.format('%02X', it) }.join('')}"
    
    // Build complete message using GitHub's lumiHeader function - use hardcoded 0x12 counter like GitHub
    List<Integer> data = buildLumiHeader(0x12, params.size(), 0x05)
    logDebug "setExternalTemperature: lumiHeader with counter 0x12 = ${data.collect { String.format('%02X', it) }.join('')}"
    data.addAll(params)
    logDebug "setExternalTemperature: complete data (${data.size()} bytes) = ${data.collect { String.format('%02X', it) }.join('')}"
    
    String hexString = data.collect { String.format('%02X', it) }.join('')
    logDebug "setExternalTemperature: final hexString length = ${hexString.length()} chars"
    
    //String command = "he wattr 0x${device.deviceNetworkId} 0x01 0xFCC0 0xFFF2 0x41 {${hexString}} {0x115F}"
    cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, hexString, [mfgCode: 0x115F])
    //cmds += [command]
    logDebug "setExternalTemperature: Zigbee command = ${cmds}"
    
    logDebug "setExternalTemperature: sending GitHub-compliant command: ${hexString}"
    sendZigbeeCommands(cmds)
    
    // Update the state
    sendEvent(name: 'externalTemperature', value: temperature, unit: '°C', descriptionText: "External temperature set to ${temperature}°C", type: 'digital')
    logDebug "setExternalTemperature(${temperature}) - COMPLETED"
}

// Command to set external humidity using GitHub-compliant protocol
void setExternalHumidity(BigDecimal humidity) {
    logDebug "setExternalHumidity(${humidity})"
    
    String currentSensorMode = device.currentValue('sensor')
    if (currentSensorMode != 'external') {
        logWarn "setExternalHumidity: sensor mode is '${currentSensorMode}', must be 'external' to set external humidity"
       // return
    }
    
    if (humidity < 0 || humidity > 100) {
        logWarn "setExternalHumidity: humidity ${humidity} is out of range (0 to 100%)"
       // return
    }
    
    List<String> cmds = []
    
    // Fixed fictive sensor IEEE address from GitHub implementation
    byte[] fictiveSensor = hubitat.helper.HexUtils.hexStringToByteArray("00158d00019d1b98")
    
    // Build humidity buffer using writeFloatBE like GitHub implementation
    byte[] humidityBuf = new byte[4]
    Float humiValue = (humidity * 100) as Float
    
    // Convert to IEEE 754 32-bit float and write as big-endian (GitHub uses writeFloatBE)
    int floatBits = Float.floatToIntBits(humiValue)
    humidityBuf[0] = (byte)((floatBits >> 24) & 0xFF)
    humidityBuf[1] = (byte)((floatBits >> 16) & 0xFF)
    humidityBuf[2] = (byte)((floatBits >> 8) & 0xFF)
    humidityBuf[3] = (byte)(floatBits & 0xFF)
    
    // Build params array exactly like GitHub: [...fictiveSensor, 0x00, 0x02, 0x00, 0x55, ...humidityBuf]
    List<Integer> params = []
    params.addAll(fictiveSensor.collect { it & 0xFF })
    params.addAll([0x00, 0x02, 0x00, 0x55])  // 0x02 indicates humidity
    params.addAll(humidityBuf.collect { it & 0xFF })
    
    // Build complete message using GitHub's lumiHeader function - use hardcoded 0x12 counter like GitHub
    List<Integer> data = buildLumiHeader(0x12, params.size(), 0x05)
    data.addAll(params)
    
    String hexString = data.collect { String.format('%02X', it) }.join('')
    
    cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, hexString, [mfgCode: 0x115F])
    
    logDebug "setExternalHumidity: sending GitHub-compliant command: ${hexString}"
    sendZigbeeCommands(cmds)
    
    // Update the state
    sendEvent(name: 'externalHumidity', value: humidity, unit: '%', descriptionText: "External humidity set to ${humidity}%", type: 'digital')
}

// Build Lumi header based on GitHub implementation
// https://github.com/13717033460/zigbee-herdsman-converters/blob/4ac6b66c7dd37e7b90a7487f3aa796f491dd24af/src/lib/lumi.ts#L2403
private List<Integer> buildLumiHeader(Integer counter, Integer paramsLength, Integer action) {
    // const lumiHeader = (counter: number, length: number, action: number) => {
    //     const header = [0xaa, 0x71, length + 3, 0x44, counter];
    //     const integrity = 512 - header.reduce((sum, elem) => sum + elem, 0);
    //     return [...header, integrity, action, 0x41, length];
    // };
    
    List<Integer> header = [0xaa, 0x71, paramsLength + 3, 0x44, counter]
    Integer integrity = 512 - header.sum()
    List<Integer> result = []
    result.addAll(header)
    result.addAll([integrity, action, 0x41, paramsLength])
    return result
}

// Get the current counter value from state
private Integer getCounter() {
    if (state.counter == null) {
        state.counter = 0x10  // Initialize if not present
        logDebug "getCounter: initialized counter to ${state.counter}"
    }
    return state.counter as Integer
}

// Get the next counter value and increment for future use
private Integer getNextCounter() {
    Integer currentCounter = getCounter()
    state.counter = (currentCounter + 1) & 0xFF  // Wrap at 255 to stay within byte range
    logDebug "getNextCounter: returning ${currentCounter}, next will be ${state.counter}"
    return currentCounter
}

// Reset counter to a specific value (useful for testing or synchronization)
private void resetCounterValue(Integer value = 0x10) {
    state.counter = value & 0xFF
    logDebug "resetCounterValue: counter reset to ${state.counter}"
}

ArrayList zigbeeWriteLongAttribute(Integer cluster, Integer attributeId, String payload, Map additionalParams = [:], int delay = 200) {
    String mfgCode = ""
    def lengthByte = HexUtils.integerToHexString((payload.length() / 2) as Integer, 1)
    if (additionalParams.containsKey("mfgCode")) {
        Integer code = additionalParams.get("mfgCode") as Integer
        mfgCode = " {${HexUtils.integerToHexString(code, 2)}}"
    }
    String wattrArgs = "0x${device.deviceNetworkId} 0x01 0x${HexUtils.integerToHexString(cluster, 2)} " + 
                       "0x${HexUtils.integerToHexString(attributeId, 2)} " + 
                       "0x41 " + 
                       "{${lengthByte + payload}}" + 
                       "$mfgCode"
    ArrayList cmdList = ["he wattr $wattrArgs", "delay $delay"]
    return cmdList
}

// Command to set sensor mode (internal/external) using GitHub implementation
void setSensorMode(String mode) {
    logDebug "setSensorMode(${mode})"
    
    if (mode != 'internal' && mode != 'external') {
        logWarn "setSensorMode: invalid mode '${mode}', must be 'internal' or 'external'"
        return
    }
    
    List<String> cmds = []
    
    if (mode == 'external') {
        // GitHub implementation for external mode - complex multi-step process
        String deviceIeee = device.zigbeeId
        //log.trace "${deviceIeee} - deviceIeee"
        // Remove 0x prefix if present and ensure proper format
        String hexString = deviceIeee.startsWith('0x') ? deviceIeee.substring(2) : deviceIeee
        hexString = hexString.padLeft(16, '0')
        byte[] deviceBytes = hubitat.helper.HexUtils.hexStringToByteArray(hexString)
        //log.trace "deviceBytes: ${deviceBytes.collect { String.format('%02X', it & 0xFF) }.join('')}"
        byte[] fictiveSensor = hubitat.helper.HexUtils.hexStringToByteArray("00158d00019d1b98")
        
        // Create timestamp
        byte[] timestamp = new byte[4]
        Long currentTime = (now() / 1000) as Long
        timestamp[0] = (byte)((currentTime >> 24) & 0xFF)
        timestamp[1] = (byte)((currentTime >> 16) & 0xFF)
        timestamp[2] = (byte)((currentTime >> 8) & 0xFF)
        timestamp[3] = (byte)(currentTime & 0xFF)
        
        // First command - params1 from GitHub
        List<Integer> params1 = []
        params1.addAll(timestamp.collect { it & 0xFF })
        params1.addAll([0x15])
        params1.addAll(deviceBytes.collect { it & 0xFF })
        params1.addAll(fictiveSensor.collect { it & 0xFF })
        params1.addAll([0x00, 0x02, 0x00, 0x55, 0x15, 0x0a, 0x01, 0x00, 0x00, 0x01, 0x06, 0xe6, 0xb9, 0xbf, 0xe5, 0xba, 0xa6, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x08, 0x65])
        
        List<Integer> val1 = buildLumiHeader(getNextCounter(), params1.size(), 0x02)
        val1.addAll(params1)
        
        // Second command - params2 from GitHub  
        List<Integer> params2 = []
        params2.addAll(timestamp.collect { it & 0xFF })
        params2.addAll([0x14])
        params2.addAll(deviceBytes.collect { it & 0xFF })
        params2.addAll(fictiveSensor.collect { it & 0xFF })
        params2.addAll([0x00, 0x01, 0x00, 0x55, 0x15, 0x0a, 0x01, 0x00, 0x00, 0x01, 0x06, 0xe6, 0xb8, 0xa9, 0xe5, 0xba, 0xa6, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x07, 0x63])
        
        List<Integer> val2 = buildLumiHeader(getNextCounter(), params2.size(), 0x02)
        val2.addAll(params2)
        
        String hexString1 = val1.collect { String.format('%02X', it) }.join('')
        String hexString2 = val2.collect { String.format('%02X', it) }.join('')
        
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, hexString1, [mfgCode: 0x115F])
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, hexString2, [mfgCode: 0x115F])
        
        logDebug "setSensorMode: sending external mode setup commands"
        sendZigbeeCommands(cmds)
        
        // Read sensor mode after setup
       //runInMillis(3000, 'readSensorModeAfterSetup')
        
    } else {
        // Internal mode - GitHub implementation for internal mode
        String deviceIeee = device.zigbeeId
        //log.trace "${deviceIeee} - deviceIeee for internal mode"
        // Remove 0x prefix if present and ensure proper format
        String hexString = deviceIeee.startsWith('0x') ? deviceIeee.substring(2) : deviceIeee
        hexString = hexString.padLeft(16, '0')
        byte[] deviceBytes = hubitat.helper.HexUtils.hexStringToByteArray(hexString)
        //log.trace "deviceBytes: ${deviceBytes.collect { String.format('%02X', it & 0xFF) }.join('')}"
        
        // Create timestamp
        byte[] timestamp = new byte[4]
        Long currentTime = (now() / 1000) as Long
        timestamp[0] = (byte)((currentTime >> 24) & 0xFF)
        timestamp[1] = (byte)((currentTime >> 16) & 0xFF)
        timestamp[2] = (byte)((currentTime >> 8) & 0xFF)
        timestamp[3] = (byte)(currentTime & 0xFF)
        
        // First command - params1 for internal mode (based on GitHub lines 2488-2533)
        List<Integer> params1 = []
        params1.addAll(timestamp.collect { it & 0xFF })
        params1.addAll([0x3d])
        params1.addAll([0x05])
        params1.addAll(deviceBytes.collect { it & 0xFF })
        params1.addAll([0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
        
        List<Integer> val1 = buildLumiHeader(getNextCounter(), params1.size(), 0x04)
        val1.addAll(params1)
        
        // Second command - params2 for internal mode
        List<Integer> params2 = []
        params2.addAll(timestamp.collect { it & 0xFF })
        params2.addAll([0x3d])
        params2.addAll([0x04])
        params2.addAll(deviceBytes.collect { it & 0xFF })
        params2.addAll([0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
        
        List<Integer> val2 = buildLumiHeader(getNextCounter(), params2.size(), 0x04)
        val2.addAll(params2)
        
        String hexString1 = val1.collect { String.format('%02X', it) }.join('')
        String hexString2 = val2.collect { String.format('%02X', it) }.join('')
        
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, hexString1, [mfgCode: 0x115F])
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, hexString2, [mfgCode: 0x115F])
        
        logDebug "setSensorMode: sending internal mode setup commands"
        sendZigbeeCommands(cmds)
        
        // Read sensor mode after switching to internal mode
        runInMillis(5000, 'readSensorModeAfterSetup')
    }
    
    // Update the state immediately for UI responsiveness
    sendEvent(name: 'sensor', value: mode, descriptionText: "Sensor mode set to ${mode}", type: 'digital')
}

// Helper to read sensor mode after external setup
void readSensorModeAfterSetup() {
    logDebug "readSensorModeAfterSetup: reading sensor mode attribute"
    List<String> cmds = []
    cmds += zigbee.readAttribute(0xFCC0, 0x0172, [destEndpoint: 0x01, mfgCode: 0x115F])
    sendZigbeeCommands(cmds)
}

// Helper to read local temperature after switching to internal mode
void readLocalTemperature() {
    logDebug "readLocalTemperature: reading local temperature from hvacThermostat cluster"
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0201, 0x0000, [destEndpoint: 0x01])  // localTemp from hvacThermostat
    sendZigbeeCommands(cmds)
}

// Command to enable/disable external thermostat connection using dynamic buildLumiHeader
void setExternalThermostat(String mode) {
    logDebug "setExternalThermostat(${mode})"
    
    if (mode != 'disabled' && mode != 'enabled') {
        logWarn "setExternalThermostat: invalid mode '${mode}', must be 'disabled' or 'enabled'"
        return
    }
    
    List<String> cmds = []
    
    if (mode == 'enabled') {
        // Extract common parameters from Wireshark analysis for external thermostat enable
        // Device information and thermostat connection data
        List<Integer> enableParams1 = [
            0x41, 0x19, 0x68, 0x83, 0x0f, 0x72, 0xc1, 0x85, 0x4e, 0xf4, 0x41, 0x00, 0x12, 0x39, 0x92, 0x50,
            0x00, 0x00, 0x54, 0xef, 0x44, 0x61, 0x53, 0x5f, 0x08, 0x00, 0x08, 0x44, 0x15, 0x0a, 0x01, 0x09,
            0xe7, 0xa9, 0xba, 0xe8, 0xb0, 0x83, 0xe5, 0x8a, 0x9f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x01, 0x2a, 0x40
        ]
        
        List<Integer> enableParams2 = [
            0x41, 0x2f, 0x68, 0x83, 0xf8, 0x2c, 0x18, 0x54, 0xef, 0x44, 0x10, 0x01, 0x23, 0x99, 0x25, 0x00,
            0x00, 0x54, 0xef, 0x44, 0x61, 0x53, 0x5f, 0x08, 0x00, 0x08, 0x44, 0x15, 0x0a, 0x01, 0x09, 0xe7,
            0xa9, 0xba, 0xe8, 0xb0, 0x83, 0xe5, 0x8a, 0x9f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x01, 0x2a, 0x40
        ]
        
        // Build first enable command using dynamic counter and action 0x04
        List<Integer> data1 = buildLumiHeader(getNextCounter(), enableParams1.size(), 0x04)
        data1.addAll(enableParams1)
        String hexString1 = data1.collect { String.format('%02X', it) }.join('')
        logDebug "setExternalThermostat: enable command 1 with dynamic counter: ${hexString1}"
        
        // Build second enable command using dynamic counter and action 0x02  
        List<Integer> data2 = buildLumiHeader(getNextCounter(), enableParams2.size(), 0x02)
        data2.addAll(enableParams2)
        String hexString2 = data2.collect { String.format('%02X', it) }.join('')
        logDebug "setExternalThermostat: enable command 2 with dynamic counter: ${hexString2}"
        
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, hexString1, [mfgCode: 0x115F])
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, hexString2, [mfgCode: 0x115F])
        
        logDebug "setExternalThermostat: sending dynamic enable commands"
        sendZigbeeCommands(cmds)
        
        logInfo "External thermostat connection enabled"
        
    } else {
        // Extract parameters from Wireshark analysis for external thermostat disable
        // Original payload: aa711c44bbca0441196884714c1454ef4410012399250000000000000000000000
        // Total Wireshark message length: 0x38 (56 bytes)
        // buildLumiHeader adds 9 bytes, so parameters should be: 56 - 9 = 47 bytes
        List<Integer> disableParams = [
            0x41, 0x19, 0x68, 0x84, 0x71, 0x4c, 0x14, 0x54, 0xef, 0x44, 0x10, 0x01, 0x23, 0x99, 0x25, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00  // 47 bytes total
        ]
        
        logDebug "setExternalThermostat: disable params length = ${disableParams.size()} bytes (for total message length 0x38)"
        
        // Build disable command using dynamic counter and action 0x04
        List<Integer> data = buildLumiHeader(getNextCounter(), disableParams.size(), 0x04)
        data.addAll(disableParams)
        String hexString = data.collect { String.format('%02X', it) }.join('')
        logDebug "setExternalThermostat: disable command total length = ${hexString.length()/2} bytes (0x${String.format('%02X', hexString.length()/2)}): ${hexString}"
        
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, hexString, [mfgCode: 0x115F])
        
        logDebug "setExternalThermostat: sending dynamic disable command"
        sendZigbeeCommands(cmds)
        
        logInfo "External thermostat connection disabled"
    }
    
    // Update virtual attribute if needed
    sendEvent(name: 'externalThermostatMode', value: mode, descriptionText: "External thermostat ${mode}", type: 'digital')
}

// Debug command to get current counter value
void getCounterCommand() {
    Integer currentCounter = getCounter()
    logInfo "Current counter value: 0x${String.format('%02X', currentCounter)} (${currentCounter})"
    sendEvent(name: 'info', value: "Counter: 0x${String.format('%02X', currentCounter)}", descriptionText: "Current counter value: 0x${String.format('%02X', currentCounter)}", type: 'digital')
}

// Debug command to reset counter to a specific value
void resetCounterCommand(Integer value = null) {
    if (value == null) {
        value = 0x10  // Default starting value
    }
    if (value < 0 || value > 255) {
        logWarn "resetCounterCommand: value ${value} is out of range (0-255), using 0x10"
        value = 0x10
    }
    resetCounterValue(value)
    logInfo "Counter reset to: 0x${String.format('%02X', value)} (${value})"
    sendEvent(name: 'info', value: "Counter reset to: 0x${String.format('%02X', value)}", descriptionText: "Counter reset to: 0x${String.format('%02X', value)}", type: 'digital')
}

// =============================================================================================================
// HVAC Thermostat Functions - Based on PMTSD Protocol
// =============================================================================================================

// PMTSD Helper Functions
private String buildPMTSDString(Integer power, Integer mode, Integer temp, Integer speed, Integer display) {
    return "P${power}_M${mode}_T${temp}_S${speed}_D${display}"
}

private List<Integer> encodePMTSDString(String pmtsd) {
    return pmtsd.getBytes('ASCII').collect { it & 0xFF }
}

private Integer calculateChecksum(List<Integer> packetBytes) {
    return packetBytes.sum() & 0xFF
}

// Parse PMTSD from incoming hex data
private void parsePMTSDResponse(String hexValue) {
    logDebug "parsePMTSDResponse: parsing hex value: ${hexValue}"
    
    try {
        // Find ASCII payload in hex data using pattern from DecodePMTSD_FD.py
        byte[] data = hubitat.helper.HexUtils.hexStringToByteArray(hexValue)
        
        // Look for 0x08, 0x44 sequence to find payload start
        Integer payloadStart = -1
        for (int i = 0; i < data.length - 1; i++) {
            if (data[i] == 0x08 && data[i + 1] == 0x44) {
                payloadStart = i + 2  // Skip 0x08, 0x44
                break
            }
        }
        
        if (payloadStart == -1 || payloadStart >= data.length) {
            logDebug "parsePMTSDResponse: could not find PMTSD payload markers (0x08, 0x44) in hex data"
            // Check if this is an HVAC enable/disable response
            if (hexValue.contains('08440101') || hexValue.contains('08440100')) {
                logInfo "HVAC command response received: ${hexValue.contains('08440101') ? 'Enable confirmed' : 'Disable confirmed'}"
            }
            return
        }
        
        // Check if there's a length byte
        if (payloadStart >= data.length) {
            logDebug "parsePMTSDResponse: no payload length byte available"
            return
        }
        
        // Extract payload length
        Integer payloadLength = data[payloadStart] & 0xFF
        Integer asciiStart = payloadStart + 1
        
        // Calculate actual remaining bytes vs claimed length
        Integer remainingBytes = data.length - asciiStart
        Integer actualLength = Math.min(payloadLength, remainingBytes)
        
        logDebug "parsePMTSDResponse: claimed length=${payloadLength}, remaining bytes=${remainingBytes}, using length=${actualLength}"
        
        if (actualLength <= 0) {
            logDebug "parsePMTSDResponse: no ASCII data available"
            // Check for simple HVAC responses (just 0x01 for enable, 0x00 for disable)
            if (payloadLength == 1 && asciiStart < data.length) {
                Integer responseValue = data[asciiStart] & 0xFF
                logInfo "HVAC simple response: ${responseValue == 1 ? 'Enabled' : 'Disabled'}"
            }
            return
        }
        
        // Convert available payload to ASCII string
        byte[] payloadBytes = new byte[actualLength]
        for (int i = 0; i < actualLength; i++) {
            payloadBytes[i] = data[asciiStart + i]
        }
        String payloadAscii = new String(payloadBytes, 'ASCII')
        logDebug "parsePMTSDResponse: extracted ASCII payload: '${payloadAscii}' (${actualLength} bytes)"
        
        // Try to extract readable ASCII characters only
        String cleanAscii = payloadAscii.replaceAll(/[^\x20-\x7E]/, '')  // Keep only printable ASCII
        logDebug "parsePMTSDResponse: clean ASCII: '${cleanAscii}'"
        
        // Parse PMTSD using regex pattern P([01])_M([012])_T(\d+)_S([0-3])_D([01])
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(/P([01])_M([012])_T(\d+)_S([0-3])_D([01])/)
        java.util.regex.Matcher matcher = pattern.matcher(cleanAscii)
        
        if (matcher.find()) {
            Integer power = Integer.parseInt(matcher.group(1))
            Integer mode = Integer.parseInt(matcher.group(2))
            Integer temp = Integer.parseInt(matcher.group(3))
            Integer speed = Integer.parseInt(matcher.group(4))
            Integer display = Integer.parseInt(matcher.group(5))
            
            logInfo "PMTSD received: Power=${power} (${power == 0 ? 'On' : 'Off'}), Mode=${mode} (${['Cool', 'Heat', 'Auto'][mode]}), Temp=${temp}°C, Speed=${speed} (${['Auto', 'Low', 'Med', 'High'][speed]}), Display=${display}"
            
            // Update device state
            updateHVACFromPMTSD(power, mode, temp, speed, display)
        } else {
            logDebug "parsePMTSDResponse: no valid PMTSD pattern found in clean ASCII: '${cleanAscii}'"
            // Log what we received for debugging
            if (cleanAscii.length() > 0) {
                logInfo "PMTSD partial response: '${cleanAscii}' - likely device acknowledgment"
            }
        }
        
    } catch (Exception e) {
        logWarn "parsePMTSDResponse: error parsing PMTSD data: ${e.message}"
    }
}

// Update device attributes from received PMTSD data
private void updateHVACFromPMTSD(Integer power, Integer mode, Integer temp, Integer speed, Integer display) {
    // Update state
    state.hvac.mode = (power == 1) ? 'off' : ['cool', 'heat', 'auto'][mode]
    state.hvac.heatingSetpoint = (mode == 1) ? temp : state.hvac.heatingSetpoint
    state.hvac.coolingSetpoint = (mode == 0) ? temp : state.hvac.coolingSetpoint
    state.hvac.fanMode = ['auto', 'low', 'medium', 'high'][speed]
    state.hvac.operatingState = (power == 1) ? 'idle' : (['cooling', 'heating', 'idle'][mode] ?: 'idle')
    
    // Send events
    sendEvent(name: 'thermostatMode', value: state.hvac.mode, descriptionText: "Thermostat mode: ${state.hvac.mode}", type: 'physical')
    sendEvent(name: 'thermostatSetpoint', value: temp, unit: '°C', descriptionText: "Thermostat setpoint: ${temp}°C", type: 'physical')
    sendEvent(name: 'fanMode', value: state.hvac.fanMode, descriptionText: "Fan mode: ${state.hvac.fanMode}", type: 'physical')
    sendEvent(name: 'thermostatOperatingState', value: state.hvac.operatingState, descriptionText: "Operating state: ${state.hvac.operatingState}", type: 'physical')
    
    if (mode == 1) {  // Heating mode
        sendEvent(name: 'heatingSetpoint', value: temp, unit: '°C', descriptionText: "Heating setpoint: ${temp}°C", type: 'physical')
    } else if (mode == 0) {  // Cooling mode
        sendEvent(name: 'coolingSetpoint', value: temp, unit: '°C', descriptionText: "Cooling setpoint: ${temp}°C", type: 'physical')
    }
}

// Core HVAC Enable/Disable Commands
void enableHVAC() {
    logDebug "enableHVAC: enabling HVAC thermostat mode"
    
    try {
        // Initialize HVAC state if null
        if (state.hvac == null) {
            state.hvac = [:]
            state.hvac.enabled = false
            state.hvac.mode = 'off'
            state.hvac.heatingSetpoint = 22.0
            state.hvac.coolingSetpoint = 24.0
            state.hvac.fanMode = 'auto'
            state.hvac.operatingState = 'idle'
            state.hvac.displayMode = 'normal'
            logDebug "enableHVAC: initialized HVAC state variables"
        }
        
        // Build HVAC enable packet based on GenerateHVACOn_TD.py
        List<Integer> packet = buildHVACEnablePacket()
        String hexString = packet.collect { String.format('%02X', it) }.join('')
        
        List<String> cmds = []
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, hexString, [mfgCode: 0x115F])
        
        logDebug "enableHVAC: sending enable command: ${hexString}"
        sendZigbeeCommands(cmds)
        
        // Update state
        state.hvac.enabled = true
        sendEvent(name: 'hvacMode', value: 'enabled', descriptionText: "HVAC mode enabled", type: 'digital')
        
        // Initialize with default PMTSD values
        runInMillis(2000, 'initializeHVACDefaults')
        
        logInfo "HVAC thermostat mode enabled"
        
    } catch (Exception e) {
        logWarn "enableHVAC: error enabling HVAC: ${e.message}"
    }
}

void disableHVAC() {
    logDebug "disableHVAC: disabling HVAC thermostat mode"
    
    try {
        // Build HVAC disable packet based on GenerateHVACOff_TD.py
        List<Integer> packet = buildHVACDisablePacket()
        String hexString = packet.collect { String.format('%02X', it) }.join('')
        
        List<String> cmds = []
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, hexString, [mfgCode: 0x115F])
        
        logDebug "disableHVAC: sending disable command: ${hexString}"
        sendZigbeeCommands(cmds)
        
        // Update state
        state.hvac.enabled = false
        state.hvac.mode = 'off'
        state.hvac.operatingState = 'idle'
        
        sendEvent(name: 'hvacMode', value: 'disabled', descriptionText: "HVAC mode disabled", type: 'digital')
        sendEvent(name: 'thermostatMode', value: 'off', descriptionText: "Thermostat mode: off", type: 'digital')
        sendEvent(name: 'thermostatOperatingState', value: 'idle', descriptionText: "Operating state: idle", type: 'digital')
        
        logInfo "HVAC thermostat mode disabled"
        
    } catch (Exception e) {
        logWarn "disableHVAC: error disabling HVAC: ${e.message}"
    }
}

// Initialize HVAC with default values after enabling
void initializeHVACDefaults() {
    logDebug "initializeHVACDefaults: setting default thermostat values"
    
    // Send default PMTSD command: P0_M2_T22_S0_D0 (On, Auto mode, 22°C, Auto fan, Display normal)
    sendPMTSDCommand(0, 2, 22, 0, 0)
    
    // Initialize supported modes
    sendEvent(name: 'supportedThermostatModes', value: JsonOutput.toJson(['off', 'heat', 'cool', 'auto']), descriptionText: "Supported thermostat modes", type: 'digital')
    sendEvent(name: 'supportedThermostatFanModes', value: JsonOutput.toJson(['auto', 'low', 'medium', 'high']), descriptionText: "Supported fan modes", type: 'digital')
}

// High-Level Thermostat Commands
void setThermostatMode(String mode) {
    logDebug "setThermostatMode(${mode})"
    
    if (!state.hvac?.enabled) {
        logWarn "setThermostatMode: HVAC mode is not enabled"
        return
    }
    
    if (!(mode in ['off', 'heat', 'cool', 'auto'])) {
        logWarn "setThermostatMode: invalid mode '${mode}', must be off/heat/cool/auto"
        return
    }
    
    Integer powerValue = (mode == 'off') ? 1 : 0
    Integer modeValue = ['off': 0, 'cool': 0, 'heat': 1, 'auto': 2][mode]
    Integer currentTemp = getCurrentSetpoint(mode)
    Integer currentSpeed = getCurrentFanSpeed()
    Integer currentDisplay = getCurrentDisplayMode()
    
    sendPMTSDCommand(powerValue, modeValue, currentTemp, currentSpeed, currentDisplay)
    
    // Update state immediately for UI responsiveness
    state.hvac.mode = mode
    sendEvent(name: 'thermostatMode', value: mode, descriptionText: "Thermostat mode set to ${mode}", type: 'digital')
    
    logInfo "Thermostat mode set to: ${mode}"
}

void setHeatingSetpoint(BigDecimal temperature) {
    logDebug "setHeatingSetpoint(${temperature})"
    
    if (!state.hvac?.enabled) {
        logWarn "setHeatingSetpoint: HVAC mode is not enabled"
        return
    }
    
    if (temperature < 10 || temperature > 35) {
        logWarn "setHeatingSetpoint: temperature ${temperature} is out of range (10-35°C)"
        return
    }
    
    Integer tempValue = temperature as Integer
    state.hvac.heatingSetpoint = tempValue
    
    // If currently in heating mode, send PMTSD command
    if (state.hvac.mode == 'heat') {
        sendPMTSDCommand(0, 1, tempValue, getCurrentFanSpeed(), getCurrentDisplayMode())
    }
    
    sendEvent(name: 'heatingSetpoint', value: tempValue, unit: '°C', descriptionText: "Heating setpoint set to ${tempValue}°C", type: 'digital')
    sendEvent(name: 'thermostatSetpoint', value: tempValue, unit: '°C', descriptionText: "Thermostat setpoint: ${tempValue}°C", type: 'digital')
    
    logInfo "Heating setpoint set to: ${tempValue}°C"
}

void setCoolingSetpoint(BigDecimal temperature) {
    logDebug "setCoolingSetpoint(${temperature})"
    
    if (!state.hvac?.enabled) {
        logWarn "setCoolingSetpoint: HVAC mode is not enabled"
        return
    }
    
    if (temperature < 10 || temperature > 35) {
        logWarn "setCoolingSetpoint: temperature ${temperature} is out of range (10-35°C)"
        return
    }
    
    Integer tempValue = temperature as Integer
    state.hvac.coolingSetpoint = tempValue
    
    // If currently in cooling mode, send PMTSD command
    if (state.hvac.mode == 'cool') {
        sendPMTSDCommand(0, 0, tempValue, getCurrentFanSpeed(), getCurrentDisplayMode())
    }
    
    sendEvent(name: 'coolingSetpoint', value: tempValue, unit: '°C', descriptionText: "Cooling setpoint set to ${tempValue}°C", type: 'digital')
    sendEvent(name: 'thermostatSetpoint', value: tempValue, unit: '°C', descriptionText: "Thermostat setpoint: ${tempValue}°C", type: 'digital')
    
    logInfo "Cooling setpoint set to: ${tempValue}°C"
}

void setThermostatSetpoint(BigDecimal temperature) {
    logDebug "setThermostatSetpoint(${temperature})"
    
    // Set both heating and cooling setpoints, and update current mode
    setHeatingSetpoint(temperature)
    setCoolingSetpoint(temperature)
    
    // If in a specific mode, send the appropriate command
    if (state.hvac.mode in ['heat', 'cool', 'auto']) {
        Integer tempValue = temperature as Integer
        Integer modeValue = ['cool': 0, 'heat': 1, 'auto': 2][state.hvac.mode]
        sendPMTSDCommand(0, modeValue, tempValue, getCurrentFanSpeed(), getCurrentDisplayMode())
    }
}

void setFanMode(String mode) {
    logDebug "setFanMode(${mode})"
    
    if (!state.hvac?.enabled) {
        logWarn "setFanMode: HVAC mode is not enabled"
        return
    }
    
    if (!(mode in ['auto', 'low', 'medium', 'high'])) {
        logWarn "setFanMode: invalid mode '${mode}', must be auto/low/medium/high"
        return
    }
    
    Integer speedValue = ['auto': 0, 'low': 1, 'medium': 2, 'high': 3][mode]
    state.hvac.fanMode = mode
    
    // Send PMTSD command with current settings
    if (state.hvac.mode != 'off') {
        Integer powerValue = 0
        Integer modeValue = ['cool': 0, 'heat': 1, 'auto': 2][state.hvac.mode] ?: 2
        Integer currentTemp = getCurrentSetpoint(state.hvac.mode)
        
        sendPMTSDCommand(powerValue, modeValue, currentTemp, speedValue, getCurrentDisplayMode())
    }
    
    sendEvent(name: 'fanMode', value: mode, descriptionText: "Fan mode set to ${mode}", type: 'digital')
    
    logInfo "Fan mode set to: ${mode}"
}

// Core PMTSD Command Function
void sendPMTSDCommand(BigDecimal power, BigDecimal mode, BigDecimal temp, BigDecimal speed, BigDecimal display) {
    logDebug "sendPMTSDCommand: P=${power}, M=${mode}, T=${temp}, S=${speed}, D=${display}"
    
    try {
        // Build PMTSD string
        String pmtsdString = buildPMTSDString(power as int, mode as int, temp as int, speed as int, display as int)
        logDebug "sendPMTSDCommand: PMTSD string = '${pmtsdString}'"
        
        // Build packet based on GeneratePMTSD_TD.py
        List<Integer> packet = buildPMTSDPacket(pmtsdString)
        String hexString = packet.collect { String.format('%02X', it) }.join('')
        
        List<String> cmds = []
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, hexString, [mfgCode: 0x115F])
        
        logDebug "sendPMTSDCommand: sending command: ${hexString}"
        sendZigbeeCommands(cmds)
        
        logInfo "PMTSD command sent: ${pmtsdString}"
        
    } catch (Exception e) {
        logWarn "sendPMTSDCommand: error sending PMTSD command: ${e.message}"
    }
}

// Helper Functions for Current State Values
private Integer getCurrentSetpoint(String mode) {
    switch (mode) {
        case 'heat':
            return state.hvac?.heatingSetpoint ?: 22
        case 'cool':
            return state.hvac?.coolingSetpoint ?: 22
        case 'auto':
        default:
            return state.hvac?.heatingSetpoint ?: 22
    }
}

private Integer getCurrentFanSpeed() {
    String fanMode = state.hvac?.fanMode ?: 'auto'
    return ['auto': 0, 'low': 1, 'medium': 2, 'high': 3][fanMode] ?: 0
}

private Integer getCurrentDisplayMode() {
    return 0  // Normal display mode (could be configurable in future)
}

// Packet Building Functions - Based on Python Scripts from GitHub Issue #27262
private List<Integer> buildHVACEnablePacket() {
    // Based on GenerateHVACOn_TD.py - exact implementation
    logDebug "buildHVACEnablePacket: generating HVAC enable message using Python script format"
    
    // Default device MAC from Python script: "54:EF:44:10:01:2D:D6:31"
    List<Integer> deviceMac = [0x54, 0xEF, 0x44, 0x10, 0x01, 0x2D, 0xD6, 0x31]
    
    // Default hub MAC from Python script: "54:EF:44:80:71:1A" (6 bytes)
    List<Integer> hubMac = [0x54, 0xEF, 0x44, 0x80, 0x71, 0x1A]
    
    // Fixed Zigbee prefix + 2 random bytes (use counter values instead of random)
    List<Integer> prefix = [0xaa, 0x71, 0x32, 0x44]
    prefix.addAll([getNextCounter() & 0xFF, getNextCounter() & 0xFF])  // 2 random bytes
    
    // Static Zigbee middle (do not touch)
    List<Integer> zigbeeHeader = [0x02, 0x41, 0x2f, 0x68, 0x91]
    
    // 2-byte message ID (use counter values) + static 0x18
    List<Integer> messageId = [getNextCounter() & 0xFF, getNextCounter() & 0xFF]
    List<Integer> messageControl = [0x18]
    
    // Device and hub MACs
    List<Integer> payloadMacs = []
    payloadMacs.addAll(deviceMac)
    payloadMacs.addAll([0x00, 0x00])  // 2 zero bytes
    payloadMacs.addAll(hubMac)
    
    // Static tail
    List<Integer> payloadTail = [
        0x08, 0x00, 0x08, 0x44, 0x15, 0x0a, 0x01, 0x09, 0xe7, 0xa9, 0xba, 0xe8, 
        0xb0, 0x83, 0xe5, 0x8a, 0x9f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x01, 0x2a, 0x40
    ]
    
    // Build final frame
    List<Integer> frame = []
    frame.addAll(prefix)
    frame.addAll(zigbeeHeader)
    frame.addAll(messageId)
    frame.addAll(messageControl)
    frame.addAll(payloadMacs)
    frame.addAll(payloadTail)
    
    logDebug "buildHVACEnablePacket: frame length = ${frame.size()} bytes"
    logDebug "buildHVACEnablePacket: packet = ${frame.collect { String.format('0x%02X', it) }.join(' ')}"
    return frame
}

private List<Integer> buildHVACDisablePacket() {
    // Based on GenerateHVACOff_TD.py - exact implementation
    logDebug "buildHVACDisablePacket: generating HVAC disable message using Python script format"
    
    // Default target MAC from Python script: "54:ef:44:10:01:2d:d6:31"
    List<Integer> targetMac = [0x54, 0xef, 0x44, 0x10, 0x01, 0x2d, 0xd6, 0x31]
    
    // Get frame ID and sequence (use counter for both)
    Integer frameId = getNextCounter()
    Integer seq = getNextCounter()
    
    // Build base message exactly like Python script
    List<Integer> base = [
        0xaa, 0x71, 0x1c, 0x44, 0x69, 0x1c, 0x04, 0x41,
        0x19, 0x68, 0x91,
        frameId & 0xFF,
        seq & 0xFF,
        0x18
    ]
    
    // Add target MAC
    base.addAll(targetMac)
    
    // Pad to 34 bytes total (like Python script)
    while (base.size() < 34) {
        base.add(0x00)
    }
    
    logDebug "buildHVACDisablePacket: base message length = ${base.size()}, frameId = 0x${String.format('%02X', frameId)}, seq = 0x${String.format('%02X', seq)}"
    logDebug "buildHVACDisablePacket: packet = ${base.collect { String.format('0x%02X', it) }.join(' ')}"
    return base
}

private List<Integer> buildPMTSDPacket(String pmtsdString) {
    // Based on GeneratePMTSD_TD.py - exact implementation
    logDebug "buildPMTSDPacket: generating PMTSD message using Python script format"
    
    // Default hub MAC from Python script: "54:EF:44:80:71:1A" (6 bytes)
    List<Integer> hubMac = [0x54, 0xEF, 0x44, 0x80, 0x71, 0x1A]
    
    // Encode PMTSD string to ASCII bytes
    List<Integer> pmtsdBytes = encodePMTSDString(pmtsdString)
    Integer pmtsdLen = pmtsdBytes.size()
    
    // Build packet exactly like Python script
    List<Integer> packet = [
        0xAA, 0x71, 0x1F, 0x44,
        0x00, 0x00, 0x05, 0x41, 0x1C,  // counter and checksum will be set below
        0x00, 0x00
    ]
    
    // Add MAC bytes
    packet.addAll(hubMac)
    
    // Add PMTSD payload header and data
    packet.addAll([0x08, 0x00, 0x08, 0x44, pmtsdLen])
    packet.addAll(pmtsdBytes)
    
    // Set counter (random value like Python script)
    Integer counter = getNextCounter()
    packet[4] = counter
    
    // Calculate checksum (sum of all bytes & 0xFF like Python script)
    Integer checksum = packet.sum() & 0xFF
    packet[5] = checksum
    
    logDebug "buildPMTSDPacket: PMTSD='${pmtsdString}', counter=0x${String.format('%02X', counter)}, checksum=0x${String.format('%02X', checksum)}"
    logDebug "buildPMTSDPacket: packet = ${packet.collect { String.format('0x%02X', it) }.join(' ')}"
    return packet
}

// Convenience Methods for Thermostat Capability Compatibility
void heat() { setThermostatMode('heat') }
void cool() { setThermostatMode('cool') }
void auto() { setThermostatMode('auto') }
void off() { setThermostatMode('off') }
void emergencyHeat() { 
    logWarn "emergencyHeat: Emergency heat mode not supported by W100 HVAC"
    setThermostatMode('heat')  // Fallback to heat mode
}
void eco() { 
    logWarn "eco: Eco mode not supported by W100 HVAC" 
    setThermostatMode('off')   // Fallback to off mode
}

// Debug Commands for HVAC Testing
void debugSendPMTSD(String pmtsdString) {
    logDebug "debugSendPMTSD(${pmtsdString})"
    
    try {
        // Validate PMTSD format
        if (!pmtsdString.matches(/^P[01]_M[012]_T\d+_S[0-3]_D[01]$/)) {
            logWarn "debugSendPMTSD: invalid PMTSD format '${pmtsdString}'"
            return
        }
        
        // Parse and send
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(/P([01])_M([012])_T(\d+)_S([0-3])_D([01])/)
        java.util.regex.Matcher matcher = pattern.matcher(pmtsdString)
        
        if (matcher.find()) {
            Integer power = Integer.parseInt(matcher.group(1))
            Integer mode = Integer.parseInt(matcher.group(2))
            Integer temp = Integer.parseInt(matcher.group(3))
            Integer speed = Integer.parseInt(matcher.group(4))
            Integer display = Integer.parseInt(matcher.group(5))
            
            sendPMTSDCommand(power, mode, temp, speed, display)
        }
        
    } catch (Exception e) {
        logWarn "debugSendPMTSD: error parsing PMTSD string: ${e.message}"
    }
}

void debugHVACStatus() {
    logInfo "=== HVAC Status Debug ==="
    logInfo "HVAC Enabled: ${state.hvac?.enabled ?: false}"
    logInfo "Current Mode: ${state.hvac?.mode ?: 'unknown'}"
    logInfo "Heating Setpoint: ${state.hvac?.heatingSetpoint ?: 'not set'}°C"
    logInfo "Cooling Setpoint: ${state.hvac?.coolingSetpoint ?: 'not set'}°C"
    logInfo "Fan Mode: ${state.hvac?.fanMode ?: 'unknown'}"
    logInfo "Operating State: ${state.hvac?.operatingState ?: 'unknown'}"
    logInfo "========================"
}

void debugToggleHVAC() {
    logDebug "debugToggleHVAC: toggling HVAC state"
    
    if (state.hvac?.enabled) {
        disableHVAC()
    } else {
        enableHVAC()
    }
}

// Comprehensive HVAC Test Function
void debugHVACTest() {
    logInfo "=== HVAC Comprehensive Test ==="
    
    // Test 1: Enable HVAC
    logInfo "Test 1: Enabling HVAC mode"
    enableHVAC()
    
    // Test 2: Set thermostat mode to heat
    runInMillis(3000, 'debugHVACTestStep2')
}

void debugHVACTestStep2() {
    logInfo "Test 2: Setting thermostat mode to heat"
    setThermostatMode('heat')
    setHeatingSetpoint(24)
    
    runInMillis(3000, 'debugHVACTestStep3')
}

void debugHVACTestStep3() {
    logInfo "Test 3: Setting thermostat mode to cool"
    setThermostatMode('cool')
    setCoolingSetpoint(20)
    
    runInMillis(3000, 'debugHVACTestStep4')
}

void debugHVACTestStep4() {
    logInfo "Test 4: Setting fan mode to medium"
    setFanMode('medium')
    
    runInMillis(3000, 'debugHVACTestStep5')
}

void debugHVACTestStep5() {
    logInfo "Test 5: Sending raw PMTSD command"
    debugSendPMTSD('P0_M2_T22_S1_D0')  // Auto mode, 22°C, Low fan
    
    runInMillis(3000, 'debugHVACTestStep6')
}

void debugHVACTestStep6() {
    logInfo "Test 6: Final status check"
    debugHVACStatus()
    
    logInfo "=== HVAC Test Complete ==="
    logInfo "All HVAC functions have been tested. Check device events and logs for responses."
}

// Test Functions for HVAC Commands
void testHVACEnable() {
    logInfo "=== Testing HVAC Enable Command ==="
    logInfo "Calling enableHVAC() using new Python script format..."
    enableHVAC()
    logInfo "HVAC Enable test completed - check logs for packet details"
}

void testHVACDisable() {
    logInfo "=== Testing HVAC Disable Command ==="  
    logInfo "Calling disableHVAC() using new Python script format..."
    disableHVAC()
    logInfo "HVAC Disable test completed - check logs for packet details"
}

void testPMTSDSend(String pmtsdString = 'P0_M1_T22_S0_D0') {
    logInfo "=== Testing PMTSD Send Command ==="
    logInfo "Sending PMTSD: ${pmtsdString} using new Python script format..."
    
    // Validate PMTSD format
    if (!pmtsdString.matches(/^P[01]_M[012]_T\d+_S[0-3]_D[01]$/)) {
        logWarn "testPMTSDSend: invalid PMTSD format '${pmtsdString}'"
        logInfo "Expected format: P0_M1_T22_S0_D0 (Power_Mode_Temp_Speed_Display)"
        return
    }
    
    // Parse and send using debugSendPMTSD
    debugSendPMTSD(pmtsdString)
    logInfo "PMTSD Send test completed - check logs for packet details"
}

void parseTestPMTSD(String hexData) {
    logInfo "=== Testing PMTSD Parse Function ==="
    logInfo "Parsing hex data: ${hexData}"
    parsePMTSDResponse(hexData)
    logInfo "PMTSD Parse test completed - check logs for results"
}


void testT(String par) {

    logInfo "Test function called with parameter: ${par}"

    debugHVACTest()
/*    
    def cmds = []
    def payload = "aa713244254a02412f6883f82c1854ef441001239925000054ef4461535f08000844150a0109e7a9bae8b083e58a9f000000000001012a40"

    cmds = zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, payload, [mfgCode: 0x115F])
    sendZigbeeCommands(cmds)
*/    
    
    logDebug "testT(${par}) - COMPLETED"   
}


// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
