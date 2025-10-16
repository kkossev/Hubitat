/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateStringLiteral, ImplicitClosureParameter, MethodCount, MethodSize, NglParseError, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessarySetter, UnusedImport */
/**
 *  Zigbee TRVs and Thermostats - Device Driver for Hubitat Elevation
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
 * ver. 3.0.0  2023-11-16 kkossev  - Refactored version 2.x.x drivers and libraries; adding MOES BRT-100 support - setHeatingSettpoint OK; off OK; level OK; workingState OK
 *                                   Emergency Heat OK;   setThermostatMode OK; Heat OK, Auto OK, Cool OK; setThermostatFanMode OK
 * ver. 3.0.1  2023-12-02 kkossev  - added NAMRON thermostat profile; added Sonoff TRVZB 0x0201 (Thermostat Cluster) support; thermostatOperatingState ; childLock OK; windowOpenDetection OK; setPar OK for BRT-100 and Aqara;
 *                                   minHeatingSetpoint & maxHeatingSetpoint OK; calibrationTemp negative values OK!; auto OK; heat OK; cool and emergency heat OK (unsupported); Sonoff off mode OK;
 * ver. 3.0.2  2023-12-02 kkossev  - importUrl correction; BRT-100: auto OK, heat OK, eco OK, supportedThermostatModes is defined in the device profile; refresh OK; autPoll OK (both BRT-100 and Sonoff);
 *                                   removed isBRT100TRV() ; removed isSonoffTRV(), removed 'off' mode for BRT-100; heatingSetPoint 12.3 bug fixed;
 * ver. 3.0.3  2023-12-03 kkossev  - Aqara E1 thermostat refactoring : removed isAqaraTRV(); heatingSetpoint OK; off mode OK, auto OK heat OK; driverVersion state is updated on healthCheck and on preferences saving;
 * ver. 3.0.4  2023-12-08 kkossev  - code cleanup; fingerpints not generated bug fix; initializeDeviceThermostat() bug fix; debug logs are enabled by default; added VIRTUAL thermostat : ping, auto, cool, emergency heat, heat, off, eco - OK!
 *                                   setTemperature, setHeatingSetpoint, setCoolingSetpoint - OK setPar() OK  setCommand() OK; Google Home compatibility for virtual thermostat;  BRT-100: Google Home exceptions bug fix; setHeatingSetpoint to update also the thermostatSetpoint for Google Home compatibility; added 'off' mode for BRT-100;
 * ver. 3.0.5  2023-12-09 kkossev  - BRT-100 - off mode (substitutues with eco mode); emergency heat mode ; BRT-100 - digital events for temperature, heatingSetpoint and level on autoPollThermostat() and Refresh(); BRT-100: workingState open/closed replaced with thermostatOperatingState
 * ver. 3.0.6  2023-12-18 kkossev  - configure() changes (SONOFF still not initialized properly!); adding TUYA_SASWELL group; TUYA_SASWELL heatingSetpoint correction; Groovy linting;
 * ver. 3.0.7  2024-03-04 kkossev  - commonLib 3.0.3 check; more Groovy lint;
 * ver. 3.0.8  2024-04-01 kkossev  - commonLib 3.0.4 check; more Groovy lint; tested w/ Sonoff TRVZB;
 * ver. 3.1.0  2024-04-19 kkossev  - commonLib 3.1.0 check; deviceProfilesV3; enum attributes bug fix
 * ver. 3.2.0  2024-05-25 kkossev  - commonLib 3.2.0 allignment;
 * ver. 3.2.1  2024-05-28 kkossev  - customProcessDeviceProfileEvent; Xiaomi cluster value exception bug fix
 * ver. 3.2.2  2024-06-06 kkossev  - added AVATTO TS0601 _TZE200_ye5jkfsb
 * ver. 3.2.3  2024-06-07 kkossev  - hide onOff and maxReportingTime advanced options; hysteresis fix; added "_TZE200_aoclfnxz in 'TUYA/MOES_BHT-002_THERMOSTAT'; added _TZE200_2ekuz3dz in TUYA/BEOK_THERMOSTAT
 * ver. 3.3.0  2024-06-07 kkossev  - moved all Tuya, Aqara E1, NAMRON and TRVZB to separate drivers;
 * ver. 3.3.5  2024-07-15 kkossev  - using the new thermostatLib; Stelpro thermostats;
 * ver. 3.4.0  2024-10-05 kkossev  - driver renamed to Zigbee TRVs and Thermostats (Misc); code cleanup; added to HPM
 * ver. 3.5.0  2025-04-08 kkossev  - urgent fix for java.lang.CloneNotSupportedException
 * ver. 3.5.2  2025-05-25 kkossev  - HE platfrom version 2.4.1.x decimal preferences patch/workaround.
 * ver. 3.5.3  2025-10-14 kkossev  - (dev. branch) adding IMOU TRV602WZ into new 'IMOU_IOT_TRV1_EU' profile
 *
 *                                   DONE: added IMOU unknown attributes
 *                                   DONE: check refresh() for IMOU - only the major attributes are refreshed
 *                                   DONE: check refreshAll() for IMOU - all attributes are refreshed, including model and manufacturer
 *                                   TODO: thermostatOperatingState instead of on/off
 *                                   DONE: fix zigbee configure reporting error: Unsupported Attribute
 *                                   TODO:
 *                                   TODO:
 */

static String version() { '3.5.3' }
static String timeStamp() { '2025/10/14 7:53 AM' }

@Field static final Boolean _DEBUG = false
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput
import java.math.RoundingMode

#include kkossev.commonLib
#include kkossev.onOffLib
#include kkossev.batteryLib
#include kkossev.temperatureLib
#include kkossev.deviceProfileLib
#include kkossev.thermostatLib

deviceType = 'Thermostat'
@Field static final String DEVICE_TYPE = 'Thermostat'

//@Field static final String SIMULATED_DEVICE_MODEL = 'TRV602WZ'            // comment out for production!
//@Field static final String SIMULATED_DEVICE_MANUFACTURER = 'IMOU'        // comment out for production!

metadata {
    definition(
        name: 'Zigbee TRVs and Thermostats (Misc)',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Drivers/Zigbee%20TRV/Zigbee_TRVs_and_Thermostats_(Misc)_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        // capbilities are defined in the thermostatLib
        attribute 'antiFreeze', 'enum', ['off', 'on']               // TUYA_SASWELL_TRV, AVATTO_TRV06_TRV16_ME167_ME168_TRV, 
        attribute 'occupancy', 'enum', ['away', 'heat']             // NAMRON
        attribute 'away', 'enum', ['off', 'on']                     // Tuya Saswell, AVATTO, NAMRON
        attribute 'awaySetPoint', 'number'                          // NAMRON
        attribute 'boostTime', 'number'                             // BRT-100
        attribute 'calibrationTemp', 'number'                       // BRT-100, Sonoff, NAMRON
        attribute 'controlType', 'enum', ['room sensor', 'floor sensor', 'room+floor sensor']  // NAMRON
        attribute 'displayAutoOff', 'enum', ['disabled', 'enabled']  // NAMRON
        attribute 'emergencyHeatTime', 'number'                     // NAMRON
        attribute 'floorCalibrationTemp', 'number'                  // NAMRON
        attribute 'childLock', 'enum', ['off', 'on']                // BRT-100, Aqara E1, Sonoff, AVATTO, NAMRON
        attribute 'ecoMode', 'enum', ['off', 'on']                  // BRT-100, NAMRON
        attribute 'ecoTemp', 'number'                               // BRT-100
        attribute 'emergencyHeating', 'enum', ['off', 'on']         // BRT-100
        attribute 'emergencyHeatingTime', 'number'                  // BRT-100
        attribute 'floorTemperature', 'number'                      // AVATTO/MOES floor thermostats NAMRON
        attribute 'floorSensorType', 'enum', ['NTC 10K/25', 'NTC 15K/25', 'NTC 12K/25', 'NTC 100K/25', 'NTC 50K/25']  // NAMRON
        attribute 'hysteresis', 'number'                            // AVATTO, Virtual thermostat, NAMRON
        attribute 'lcdBrightnesss', 'enum', ['low Level', 'mid Level', 'high Level']  // NAMRON
        attribute 'keyVibration', 'enum', ['off', 'low level', 'high Level']  // NAMRON
        attribute 'displayAutoOffActivation', 'enum', ['deactivated', 'activated']  // NAMRON_RADIATOR
        attribute 'maxHeatingSetpoint', 'number'                    // BRT-100, Sonoff, AVATTO
        attribute 'minHeatingSetpoint', 'number'                    // BRT-100, Sonoff, AVATTO
        attribute 'modeAfterDry', 'enum', ['off', 'manual', 'auto', 'eco']      // NAMRON
        attribute 'overHeatAlarm', 'number'                         // NAMRON
        attribute 'powerUpStatus', 'enum', ['default', 'last']      // NAMRON
        attribute 'systemMode', 'enum', ['off', 'heat', 'on']               // GENERIC
        attribute 'temperatureDisplayMode', 'enum', ['room Temp', 'floor temp']  // NAMRON
        attribute 'windowOpenCheck', 'number'                       // NAMRON
        attribute 'windowOpenCheckActivation', 'enum', ['enabled', 'disabled']  // NAMRON_RADIATOR
        attribute 'windowOpenState', 'enum', ['notOpened', 'opened']  // NAMRON_RADIATOR
        attribute 'overHeatMark', 'enum', ['no', 'temperature over 85ºC and lower than 90ºC', 'temperature over 90ºC']  // NAMRON_RADIATOR
        attribute 'unknown_0201_8000', 'enum', ['unknown0', 'unknown1']  // IMOU
        attribute 'unknown_0201_8002', 'enum', ['unknown0', 'unknown1']  // IMOU
        attribute 'unknown_FC80_8001', 'enum', ['off', 'on']  // IMOU
        attribute 'unknown_FC80_8002', 'number'  // IMOU
        attribute 'unknown_FC80_8003', 'number'  // IMOU
        attribute 'unknown_FC80_8004', 'number'  // IMOU
        attribute 'unknown_FC81_8000', 'enum', ['off', 'on']  // IMOU
        attribute 'unknown_FC81_8001', 'enum', ['off', 'on']  // IMOU

        command 'refreshAll', [[name: 'refreshAll', type: 'STRING', description: 'Refreshes all parameters', defaultValue : '']]
        command 'factoryResetThermostat', [[name: 'factoryResetThermostat', type: 'STRING', description: 'Factory reset the thermostat', defaultValue : '']]
        command 'setThermostatMode', [[name: 'thermostat mode (not all are available!)', type: 'ENUM', constraints: ['--- select ---'] + AllPossibleThermostatModesOpts.options.values() as List<String>]]
        if (_DEBUG) { 
            command 'testT', [[name: 'testT', type: 'STRING', description: 'testT', defaultValue : '']]  
            command 'sendCommand', [
                [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']],
                [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']]
            ]
            command 'setPar', [
                    [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']],
                    [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']]
            ]
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
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_DEBUG_LOGGING, description: 'Turns on debug logging for 24 hours.'
        // the rest of the preferences are inputed from the deviceProfile maps in the deviceProfileLib
    }
}

@Field static final Map deviceProfilesV3 = [
    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/f83254ca55f890514744a5902edbebf8d998307d/src/devices/stelpro.ts#L46
    // Stelpro ST : https://github.com/stelpro/Ki-ZigBee-Thermostat/blob/master/devicetypes/stelpro/stelpro-ki-zigbee-thermostat.src/stelpro-ki-zigbee-thermostat.groovy#L481
    // Stelpro MaestroStat - SMT402                             // https://github.com/jrfarrar/hubitat/blob/master/devicehandlers/Stelpro%20Maestro%20ZigBee/StelproMaestroZigBee.groovy
    // https://community.hubitat.com/t/release-introducing-driver-for-zigbee-thermostat-stelpro-allia/107011
    // https://raw.githubusercontent.com/Philippe-Charette/Hubitat-Stelpro-Maestro-Thermostat/master/stelpro-maestro-thermostat.groovy

    'STELPRO_MAESTRO_SMT402' : [   // https://github.com/Koenkk/zigbee-herdsman-converters/blob/f83254ca55f890514744a5902edbebf8d998307d/src/devices/stelpro.ts#L33
        description   : 'Stelpro HT402 Hilo thermostat',
        device        : [type: 'Thermostat', powerSource: 'ac', isSleepy:false],
        capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true, 'Power':true, 'Energy':true],
        //  tz.thermostat_local_temperature, tz.thermostat_occupancy,  tz.thermostat_occupied_heating_setpoint, tz.thermostat_temperature_display_mode, tz.thermostat_keypad_lockout, tz.thermostat_system_mode, tz.thermostat_running_state, tz.stelpro_thermostat_outdoor_temperature,
            preferences   : [:],
        fingerprints  : [
            [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0009,000A,0201,0204,0702,0B04", outClusters:"0003,0019", model:"SMT402", manufacturer:"Stelpro", deviceJoinName: 'Stelpro MaestroStat SMT402 Thermostat'] ,
            // fingerprint profileId: "0104", endpointId: "19", inClusters: " 0000,0003,0201,0204,0405", outClusters: "0402"
        ],
        commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'
        ],
        attributes    : [
            [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt: '0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Temperature</b>',                   description:'Measured temperature'],
            [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt: '0x21', rw: 'rw', min:5.0,  max:30.0, step:0.5, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
            [at:'0x0201:0x001C',  name:'systemMode',               type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b> Mode</b>',                   description:'System Mode ?'],
            [at:'0x0201:0x001E',  name:'thermostatRunMode',        type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatRunMode</b>',                   description:'thermostatRunMode'],
            [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatOperatingState</b>',                   description:'thermostatOperatingState'],
            // Thermostat User Interface Configuration-0x0204(Server)
            [at:'0x0204:0x0000',  name:'temperatureDisplayMode',   type:'enum',    dt:'0x30', rw: 'ro', map:[0: 'Temperature in °C'], description:'Temperature display mode'],
            [at:'0x0204:0x0001',  name:'childLock',                type:'enum',    dt:'0x30', rw: 'rw', min:0,    max:1,    step:1,  scale:1,  defVal:'0',  map:[0: 'off', 1: 'on'], unit:'', title: '<b>Child Lock</b>', description:'Keyboard lockout'],
        ],
        supportedThermostatModes : ['heat'],
        refresh: ['pollThermostatCluster'], // in thermostatLib
        deviceJoinName: 'Stelpro HT402 Hilo thermostat',
        configuration : [:]
    ],

    'STELPRO_THERMOSTAT_ST218' : [    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/f83254ca55f890514744a5902edbebf8d998307d/src/devices/stelpro.ts#L76
        // https://github.com/dresden-elektronik/deconz-rest-plugin/issues/2307
        description   : 'Stelpro ST218 - Built-In Electronic Thermostat',   // no humidity
        device        : [type: 'Thermostat', powerSource: 'ac', isSleepy:false],
        capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
        preferences   : [:],
        fingerprints  : [
            [profileId:'0104', endpointId:'19', inClusters:'0000,0003,0201,0204,0405', outClusters:'0402', model:'ST218', manufacturer:'Stelpro', deviceJoinName: 'Stelpro ST218 - Built-In Electronic Thermostat']
        ],
        commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize'],
        attributes    : [
            [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt: '0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Temperature</b>',                   description:'Measured temperature'],
            [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt: '0x21', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
            [at:'0x0201:0x001C',  name:'systemMode',               type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b> Mode</b>',                   description:'System Mode ?'],
            [at:'0x0201:0x001E',  name:'thermostatRunMode',        type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatRunMode</b>',                   description:'thermostatRunMode'],
            [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatOperatingState</b>',                   description:'thermostatOperatingState'],
        ],
        supportedThermostatModes : ['off', 'heat', 'auto'],
        refresh: ['pollThermostatCluster'], // in thermostatLib
        deviceJoinName: 'Stelpro ST218 - Built-In Electronic Thermostat',
        configuration : [:]
    ],

    // @alex1
    // Stelpro Alia ??? https://community.hubitat.com/t/help-with-new-zigbee-driver/106856?u=kkossev
    'STELPRO_MAESTRO_STZB402' : [    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/f83254ca55f890514744a5902edbebf8d998307d/src/devices/stelpro.ts#L124
        description   : 'Stelpro STZB402 Ki line-voltage thermostat',
        device        : [type: 'Thermostat', powerSource: 'ac', isSleepy:false],
        capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true, 'RelativeHumidityMeasurement':true],
        preferences   : [:],
        fingerprints  : [
            [profileId:'0104', endpointId:'19', inClusters:'0000,0003,0201,0204,0405', outClusters:'0402', model:'STZB402', manufacturer:'Stelpro', deviceJoinName: 'Stelpro STZB402 Ki line-voltage thermostat'],
            [profileId:'0104', endpointId:'19', inClusters:'0000,0003,0201,0204,0405', outClusters:'0402', model:'STZB402+', manufacturer:'Stelpro', deviceJoinName: 'Stelpro STZB402 Ki line-voltage thermostat'],
            [profileId:'0104', endpointId:'19', inClusters:'0000,0003,0004,0201,0204', outClusters:'0003,000A,0402', model:'HT402', manufacturer:'Stello', deviceJoinName: '(Stelpro Allia) HT402 thhermostat']
            // https://github.com/Philippe-Charette/Hubitat-Stelpro-Maestro-Thermostat/blob/master/stelpro-maestro-thermostat.groovy#L36
        ],
        commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'
        ],
        attributes    : [
            [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt: '0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Temperature</b>',                   description:'Measured temperature'],
            [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt: '0x21', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
            [at:'0x0201:0x001C',  name:'systemMode',               type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b> Mode</b>',                   description:'System Mode ?'],
            [at:'0x0201:0x001E',  name:'thermostatRunMode',        type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatRunMode</b>',                   description:'thermostatRunMode'],
            [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatOperatingState</b>',                   description:'thermostatOperatingState'],
        ],
        supportedThermostatModes : ['off', 'heat', 'auto'],
        refresh: ['pollThermostatCluster'], // in thermostatLib
        deviceJoinName: 'Stelpro STZB402 Ki line-voltage thermostat',
        configuration : [:]
    ],

    'STELPRO_MAESTRO_SMT402' : [   // https://community.hubitat.com/t/release-device-driver-for-stelpro-maestro-thermostat-zigbee/34840/30?u=kkossev
        // https://github.com/Koenkk/zigbee-herdsman-converters/blob/f83254ca55f890514744a5902edbebf8d998307d/src/devices/stelpro.ts#L277
        description   : 'Stelpro SMT402 MaestroStat  Thermostat',    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/f83254ca55f890514744a5902edbebf8d998307d/src/devices/stelpro.ts#L174
        device        : [type: 'Thermostat', powerSource: 'ac', isSleepy:false],
        capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true, 'RelativeHumidityMeasurement':true],
        preferences   : [:],
        fingerprints  : [
            [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,0201", outClusters:"0003,0019", model:"SMT402", manufacturer:"Stelpro", deviceJoinName: 'Stelpro MaestroStat SMT402 Thermostat'] ,
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,0201', outClusters:'0019,1000', model:'SMT402AD', manufacturer:'Stelpro', deviceJoinName: 'Stelpro MaestroStat SMT402AD Thermostat']
            // https://github.com/Koenkk/zigbee-herdsman-converters/blob/f83254ca55f890514744a5902edbebf8d998307d/src/devices/stelpro.ts#L277
        ],
        commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'
        ],
        attributes    : [
            [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt: '0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Temperature</b>',                   description:'Measured temperature'],
            [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt: '0x21', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
            [at:'0x0201:0x001C',  name:'systemMode',               type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b> Mode</b>',                   description:'System Mode ?'],
            [at:'0x0201:0x001E',  name:'thermostatRunMode',        type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatRunMode</b>',                   description:'thermostatRunMode'],
            [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatOperatingState</b>',                   description:'thermostatOperatingState'],
        ],
        refresh: ['pollThermostatCluster'], // in thermostatLib
        deviceJoinName: 'Stelpro SMT402 MaestroStat Thermostat',
        configuration : [:]
    ],
    // Stelpro will return -325.65 when set to off, value is not realistic anyway

    'STELPRO_ORLÉANS_SORB' : [    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/f83254ca55f890514744a5902edbebf8d998307d/src/devices/stelpro.ts#L231C24-L231C28
            description   : 'Stelpro SORB ORLÉANS fan heater',   // no humidity
            device        : [type: 'Thermostat', powerSource: 'ac', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [:],
            fingerprints  : [
                [profileId:'0104', endpointId:'19', inClusters:'0000,0003,0201,0204,0405', outClusters:'0402', model:'ST218', manufacturer:'Stelpro', deviceJoinName: 'Stelpro SORB ORLÉANS fan heater']
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize'],
            attributes    : [
                [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt: '0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Temperature</b>',                   description:'Measured temperature'],
                [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt: '0x21', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
                [at:'0x0201:0x001C',  name:'systemMode',               type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b> Mode</b>',                   description:'System Mode ?'],
                [at:'0x0201:0x001E',  name:'thermostatRunMode',        type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatRunMode</b>',                   description:'thermostatRunMode'],
                [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatOperatingState</b>',                   description:'thermostatOperatingState'],
            ],
            supportedThermostatModes : ['off', 'heat', 'auto'],
            refresh: ['pollThermostatCluster'], // in thermostatLib
            deviceJoinName: 'Stelpro SORB ORLÉANS fan heater',
            configuration : [:]
    ],

    'IMOU_IOT_TRV1_EU' : [  // https://github.com/Koenkk/zigbee-herdsman-converters/issues/10212 
        description   : 'IMOU IOT-TRV1-EU TRV',
        device        : [type: 'TRV', powerSource: 'battery', isSleepy:false],
        capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
        preferences   : ['minHeatingSetpoint':'0x0201:0x0015', 'maxHeatingSetpoint':'0x0201:0x0016', 'childLock':'0xFC80:0x8000', 'antiFreeze':'0x0201:0x8001', 'unknown_0201_8002':'0x0201:0x8002'],
        fingerprints  : [
            [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0004,0005,0006,000A,000B,0201,0B05,FC80,FC81', outClusters:'0003,000A,0019', model:'TRV602WZ', manufacturer:'Topband', deviceJoinName: 'IMOU IOT-TRV1-EU TRV'],
            [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0004,0005,0006,000A,000B,0201,0B05,FC80,FC81', outClusters:'0003,000A,0019', model:'TRV602WZ', manufacturer:'', deviceJoinName: 'IMOU IOT-TRV1-EU TRV'],
            [profileId:'0104', endpointId:'02', inClusters:'0000', outClusters:'0019', model:'TRV602WZ', manufacturer:'IMOU', deviceJoinName: 'IMOU IOT-TRV1-EU TRV'],
        ],
        commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences',
                        'getDeviceNameAndProfile':'getDeviceNameAndProfile'
        ],
        attributes    : [
            [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt: '0x29', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Temperature</b>',                   description:'Measured temperature'],
            [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt: '0x29', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
            [at:'0x0201:0x0015',  name:'minHeatingSetpoint',       type:'decimal', dt: '0x29', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100, defVal: 5.0, unit:'°C', title: '<b>Min Heating Setpoint</b>',          description:'Minimum heating setpoint limit'],
            [at:'0x0201:0x0016',  name:'maxHeatingSetpoint',       type:'decimal', dt: '0x29', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100, defVal: 35.0, unit:'°C', title: '<b>Max Heating Setpoint</b>',          description:'Maximum heating setpoint limit'],
            [at:'0x0201:0x001B',  name:'thermostatOperatingState', type:'enum',    dt: '0x30', rw: 'rw', min:0,    max:5,    step:1,   scale:1,    map:[0: 'cooling', 1: 'cooling', 2: 'heating', 3: 'heating', 4: 'idle', 5: 'idle'], unit:'', title: '<b>Thermostat Operating State</b>', description:'Operating state derived from control sequence'],
            [at:'0x0006:0x0000',  name:'thermostatOperatingState', type:'enum',    dt: '0x10', rw: 'rw', min:0,    max:5,    step:1,   scale:1,    map:[0: 'cooling', 1: 'cooling', 2: 'heating', 3: 'heating', 4: 'idle', 5: 'idle'], unit:'', title: '<b>Thermostat Operating State</b>', description:'Operating state derived from control sequence'],
            [at:'0x0201:0x001C',  name:'thermostatMode',           type:'enum',    dt: '0x30', rw: 'rw', min:0,    max:4,    step:1,   scale:1,    map:[0: 'off', 3: 'auto', 4: 'heat'], unit:'',         title: '<b> Mode</b>',                   description:'System Mode'],
            [at:'0x0201:0x001E',  name:'thermostatRunMode',        type:'enum',    dt: '0x30', rw: 'rw', min:0,    max:1,    step:1,   scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatRunMode</b>',                   description:'thermostatRunMode'],
            [at:'0x0201:0x8000',  name:'unknown_0201_8000',        type:'enum',    dt: '0x30', mfgCode: '0x1329', rw: 'rw', min:0,    max:5,    step:1,   scale:1,    map:[0: 'unknown0', 1: 'unknown1'], unit:'', title: '<b>unknown_0201_8000</b>', description:'unknown_0201_8000'],
            [at:'0x0201:0x8001',  name:'antiFreeze',               type:'decimal', dt: '0x29', mfgCode: '0x1329', rw: 'rw', min:0.0,  max:35.0 ,step:1,   scale:100,  unit:'°C',  title: '<b>AntiFreeze</b>', description:'AntiFreeze temperature'],
            [at:'0x0201:0x8002',  name:'unknown_0201_8002',        type:'enum',    dt: '0x30', mfgCode: '0x1329', rw: 'rw', min:0,    max:5,    step:1,   scale:1,    map:[0: 'unknown0', 1: 'unknown1'], unit:'', title: '<b>unknown_0201_8002</b>', description:'unknown_0201_8002'],
            [at:'0xFC80:0x8000',  name:'childLock',                type:'enum',    dt: '0x10', mfgCode: '0x1329', rw: 'rw', min:0,    max:1,    step:1,   scale:1,    map:[0: 'off', 1: 'on'], unit:'',  title: '<b>childLock</b>', description:'childLock'],
            [at:'0xFC80:0x8001',  name:'unknown_FC80_8001',        type:'enum',    dt: '0x10', mfgCode: '0x1329', rw: 'rw', min:0,    max:1,    step:1,   scale:1,    map:[0: 'off', 1: 'on'], unit:'',  title: '<b>unknown_FC80_8001</b>', description:'unknown_FC80_8001'],
            [at:'0xFC80:0x8002',  name:'unknown_FC80_8002',        type:'number',  dt: '0x20', mfgCode: '0x1329', rw: 'rw', min:0,    max:9999, step:1,   scale:1,    unit:'',  title: '<b>unknown_FC80_8002</b>', description:'unknown_FC80_8002'],
            [at:'0xFC80:0x8003',  name:'unknown_FC80_8003',        type:'number',  dt: '0x1B', mfgCode: '0x1329', rw: 'rw', min:0,    max:9999, step:1,   scale:1,    unit:'',  title: '<b>unknown_FC80_8003</b>', description:'unknown_FC80_8003'],
            [at:'0xFC80:0x8004',  name:'unknown_FC80_8004',        type:'number',  dt: '0x1B', mfgCode: '0x1329', rw: 'rw', min:0,    max:9999, step:1,   scale:1,    unit:'',  title: '<b>unknown_FC80_8004</b>', description:'unknown_FC80_8004'],
            [at:'0xFC81:0x8000',  name:'unknown_FC81_8000',        type:'enum',    dt: '0x10', mfgCode: '0x1329', rw: 'rw', min:0,    max:1,    step:1,   scale:1,    map:[0: 'off', 1: 'on'], unit:'',  title: '<b>unknown_FC81_8000</b>', description:'unknown_FC81_8000'],
            [at:'0xFC81:0x8001',  name:'unknown_FC81_8001',        type:'enum',    dt: '0x10', mfgCode: '0x1329', rw: 'rw', min:0,    max:1,    step:1,   scale:1,    map:[0: 'off', 1: 'on'], unit:'',  title: '<b>unknown_FC81_8001</b>', description:'unknown_FC81_8001'],
           

        ],
        refresh: ['refreshAll'],
        supportedThermostatModes : ['off', 'heat'],
        deviceJoinName: 'IMOU-IOT-TRV1-EU TRV',
        configuration : [:]
    ],

    '---'   : [
            description   : '--------------------------------------',
    ],

    'VIRTUAL'   : [        // https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/virtualThermostat.groovy
            description   : 'Virtual thermostat',
            device        : [type: 'TRV', powerSource: 'unknown', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : ['hysteresis':'hysteresis', 'minHeatingSetpoint':'minHeatingSetpoint', 'maxHeatingSetpoint':'maxHeatingSetpoint', 'simulateThermostatOperatingState':'simulateThermostatOperatingState'],
            commands      : ['printFingerprints':'printFingerprints', 'sendSupportedThermostatModes':'sendSupportedThermostatModes', 'autoPollThermostat':'autoPollThermostat', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            attributes    : [
                [at:'hysteresis',          name:'hysteresis',       type:'enum',    dt:'virtual', rw:'rw',  min:0,   max:4,    defaultValue:'3',  step:1,  scale:1,  map:[0:'0.1', 1:'0.25', 2:'0.5', 3:'1', 4:'2'],   unit:'', title:'<b>Hysteresis</b>',  description:'<i>hysteresis</i>'],
                [at:'minHeatingSetpoint',  name:'minHeatingSetpoint',    type:'decimal', dt:'virtual', rw:'rw', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Min Heating Setpoint</b>', description:'<i>Min Heating Setpoint Limit</i>'],
                [at:'maxHeatingSetpoint',  name:'maxHeatingSetpoint',    type:'decimal', dt:'virtual', rw:'rw', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Max Heating Setpoint</b>', description:'<i>Max Heating Setpoint Limit</i>'],
                [at:'thermostatMode',      name:'thermostatMode',   type:'enum',    dt:'virtual', rw:'rw',  min:0,    max:5,    defaultValue:'0',  step:1,  scale:1,  map:[0: 'heat', 1: 'auto', 2: 'eco', 3:'emergency heat', 4:'off', 5:'cool'],            unit:'', title: '<b>Thermostat Mode</b>',           description:'<i>Thermostat Mode</i>'],
                [at:'heatingSetpoint',     name:'heatingSetpoint',  type:'decimal', dt:'virtual', rw: 'rw', min:5.0, max:45.0, defaultValue:20.0, step:0.5, scale:1,  unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'<i>Current heating setpoint</i>'],
                [at:'coolingSetpoint',     name:'coolingSetpoint',  type:'decimal', dt:'virtual', rw: 'rw', min:5.0, max:45.0, defaultValue:20.0, step:0.5, scale:1,  unit:'°C',  title: '<b>Current Cooling Setpoint</b>',      description:'<i>Current cooling setpoint</i>'],
                [at:'thermostatOperatingState',  name:'thermostatOperatingState', type:'enum', dt:'virtual', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heating'], unit:'',  description:'<i>termostatRunningState (relay on/off status)</i>'],   // read only!
                [at:'simulateThermostatOperatingState',  name:'simulateThermostatOperatingState', type:'enum',    dt: 'virtual', rw: 'rw', min:0,    max:1,   defaultValue:'0',  step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'',         title: '<b>Simulate Thermostat Operating State</b>',      \
                             description:'<i>Simulate the thermostat operating state<br>* idle - when the temperature is less than the heatingSetpoint<br>* heat - when the temperature is above tha heatingSetpoint</i>'],

            ],
            //refresh: ['pollTuya', 'pollTuya'],
            supportedThermostatModes: ['auto', 'heat', 'emergency heat', 'eco', 'off', 'cool'],
            deviceJoinName: 'Virtual thermostat',
            configuration : [:]
    ],

    'GENERIC_ZIGBEE_THERMOSTAT' : [
            description   : 'Generic Zigbee Thermostat',
            device        : [type: 'Thermostat', powerSource: 'battery', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [:],
            fingerprints  : [],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences',
                            'getDeviceNameAndProfile':'getDeviceNameAndProfile'
            ],
            attributes    : [
                [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt: '0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Temperature</b>',                   description:'Measured temperature'],
                [at:'0x0201:0x0011',  name:'coolingSetpoint',          type:'decimal', dt: '0x21', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Cooling Setpoint</b>',              description:'cooling setpoint'],
                [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt: '0x21', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
                [at:'0x0201:0x001C',  name:'systemMode',                     type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b> Mode</b>',                   description:'System Mode ?'],
                [at:'0x0201:0x001E',  name:'thermostatRunMode',        type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatRunMode</b>',                   description:'thermostatRunMode'],
                [at:'0x0201:0x0020',  name:'battery2',                 type:'number',  dt: '0x21', rw: 'ro', min:0,    max:100,  step:1,  scale:1,    unit:'%',  description:'Battery percentage remaining'],
                [at:'0x0201:0x0023',  name:'thermostatHoldMode',       type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatHoldMode</b>',                   description:'thermostatHoldMode'],
                [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatOperatingState</b>',                   description:'thermostatOperatingState'],
            ],
            refresh: ['pollThermostatCluster'], // in thermostatLib
            deviceJoinName: 'Generic Zigbee Thermostat',
            configuration : [:]
    ]
]

//void customOff() { setThermostatMode('off') }
//void customOn()  { setThermostatMode('heat') }

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

// ThermostatConfig cluster 0x0204
void customParseThermostatConfigCluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseThermostatConfigCluster: zigbee received Thermostat Config cluster (0x0204) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return }
    boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "parseThermostatConfigCluster: received unknown Thermostat Config cluster (0x0204) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }

}

void customParseFC80Cluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseFC80Cluster: zigbee received FC80 cluster attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return }
    boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "parseFC80Cluster: received unknown FC80 cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

void customParseFC81Cluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseFC81Cluster: zigbee received FC81 cluster attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return }
    boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "parseFC81Cluster: received unknown FC81 cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
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

void refreshAll() {
    logInfo 'Refreshing all parameters...'
    List<String> cmds = []
    DEVICE.attributes.each { attr ->
        Map map = attr as Map
        //log.trace "refreshAll: ${map} "
        if (map != null && map.at != null) {
            Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:]
            cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100)
        }
    }
    logDebug "refreshAll: ${cmds} "
    setRefreshRequest()    // 3 seconds
    sendZigbeeCommands(cmds)
}

/*
List<String> refreshNamron() {
    List<String> cmds = []
    logDebug 'refreshNamron() ...'
    cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0001, 0x0010, 0x0012,0x0014, 0x001c, 0x0029], [:], delay = 255) // temperature, floorTemperature, occupancy, calibration, heatingSetpoint, unoccupiedHeatingSetpoint, termostatRunningState, systemMode, thermostatOperatingState
    cmds += zigbee.readAttribute(0x0b04, [0x0505, 0x0508, 0x050b, 0x0802], [:], delay = 255)    // ActivePower, ActiveEnergy, ActivePower, ACCurrentOverload
    cmds += zigbee.readAttribute(0x0702, 0x0000, [:], delay = 255)    // CurrentSummationDelivered - energy consumption
    return cmds
}
*/

/*
List<String> configureNamron() {
    List<String> cmds = []
    logDebug 'configureNamron() ...'
    cmds += zigbee.configureReporting(0x0201, 0x0000, 0x29, 0, 600, 50, [:], delay=200)                 // Configure LocalTemperature reporting
    cmds += zigbee.configureReporting(0x0201, 0x0001, 0x29, 0, 600, 50, [:], delay=200)                 // Configure floorTemperature reporting
    cmds += zigbee.configureReporting(0x0201, 0x0012, 0x29, 0, 600, 50, [:], delay=200)                 // Configure OccupiedHeatingSetpoint reporting
    cmds += zigbee.configureReporting(0x0201, 0x001c, 0x30, 0, 600, 1, [:], delay=200)                  // Configure thermostatMode reporting
    // zigbee configure reporting error: Invalid Data Type [8D, 00, 29, 00] // cmds += zigbee.configureReporting(0x0201, 0x0029, 0x20, 0, 600, 1)  // Configure thermostatOperatingState reporting
    cmds += zigbee.configureReporting(0x0b04, 0x050b, DataType.INT16, 0, 600, 50, [:], delay=200)       // Configure ActivePower reporting
    cmds += zigbee.configureReporting(0x0b04, 0x0505, DataType.UINT16, 0, 600, 10, [:], delay=200)      // Configure Voltage reporting
    cmds += zigbee.configureReporting(0x0b04, 0x0508, DataType.UINT16, 0, 600, 50, [:], delay=200)      // Configure Amperage reporting
    cmds += zigbee.configureReporting(0x0702, 0x0000, DataType.UINT48, 0, 600, 1, [:], delay=200)       // Configure Energy reporting
    return cmds
}
*/

List<String> customRefresh() {
    List<String> cmds = refreshFromDeviceProfileList()
    //cmds += refreshNamron()
    logDebug "customRefresh: ${cmds} "
    return cmds
}

/*
List<String> customConfigure() {
    List<String> cmds = []
    //cmds += configureNamron()
    logDebug "customConfigure() : ${cmds} "
    return cmds
}
*/

// called from initializeDevice in the commonLib code
List<String> customInitializeDevice() {
    List<String> cmds = []
    //cmds += configureNamron()
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
    device.updateSetting('defaultPrecision', [value: 1, type: 'number'])
}

// called from initializeVars() in the main code ...
void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents(${fullInit})"
    thermostatInitEvents(fullInit)
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
        case 'humidity' :
            handleHumidityEvent(valueScaled)
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

import java.time.Instant
import java.time.ZoneOffset
import java.time.LocalDateTime
void testT(String par) {
    logWarn "testT(${par}) called"
    List<String> cmds = []
    cmds =   ["he raw 0x${device.deviceNetworkId} 1 1 0x000a {40 01 01 00 00 00 e2 78 83 1f 2e       07 00  00    23      a8 ad 1f 2e}", "delay 200",]
    sendZigbeeCommands(cmds)
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

