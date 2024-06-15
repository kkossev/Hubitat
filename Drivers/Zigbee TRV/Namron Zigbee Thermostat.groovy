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
 * ver. 3.3.0  2024-06-09 kkossev  - (dev. branch) first Namron Zigbee Thermostat version
 * ver. 3.3.1  2024-06-14 kkossev  - (dev. branch) calibrationTemp negative values bug fix; added hysteresis, floorCalibrationTemp, powerUpStatus, emergencyHeatTime, modeAfterDry, controlType, floorSensorType
 *
 *                                   TODO: childLock, cluster 0x0204
 *                                   TODO: research how the Namron internal clock is set (Zigbee2MQTT sniff)?
 *                                   TODO: add a link to GitHub WiKi 
 *                                   TODO: add factoryReset command
 *                                   TODO: add 'emergency heat' mode
 *                                   TODO: add 'auto' mode
 *                                   TODO: add 'eco' (away) mode
 *                                   TODO: energy reporting configuration
 *                                   TODO: add all other Namron models fingerprints
 *                                   TODO: warning Info message (permanent) when both model and manufacturer are missing (commonLib)
 *                                   TODO: warning Info message (temporary when device is unknown.
 *                                   TODO: deviceProfileV3 - default configuration if not found
 *                                   TODO: energy automatic reporting configuration (this driver)
 *                                   TODO: add powerSource capability
 *                                   TODO: add Info dummy preference to the driver with a hyperlink
 *                                   TODO: add state.thermostat for storing last attributesthermostatOperatingState 
 *                                   TODO: Healthcheck to be every hour (not 4 hours) for mains powered thermostats
 *                                   TODO: initializeDeviceThermostat() - configure in the device profile !
 *                                   TODO: Ping the device on initialize; refresh the device on initialize
 *                                   TODO: add factoryReset command Basic -0x0000 (Server); command 0x00
 *                                   TODO: add option 'Simple TRV' (no additinal attributes)
 *                                   TODO: add receiveCheck() methods for heatingSetpint and mode (option)
 *                                   TODO: separate the autoPoll commands from the refresh commands (lite)
 */

static String version() { '3.3.1' }
static String timeStamp() { '2024/06/14 7:17 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput
import java.math.RoundingMode

#include kkossev.commonLib
#include kkossev.temperatureLib
#include kkossev.deviceProfileLib
#include kkossev.thermostatLib
#include kkossev.energyLib

deviceType = 'Thermostat'
@Field static final String DEVICE_TYPE = 'Thermostat'

metadata {
    definition(
        name: 'Namron Zigbee Thermostat',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee%20TRV/Namron_Zigbee_Thermostat_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        // capbilities are defined in the thermostatLib
        // TODO - add all other models attributes possible values
        attribute 'occupancy', 'enum', ['away', 'heat']       // NAMRON
        attribute 'away', 'enum', ['off', 'on']               // Tuya Saswell, AVATTO, NAMRON
        attribute 'awaySetPoint', 'number'                    // NAMRON
        attribute 'batteryVoltage', 'number'
        attribute 'boostTime', 'number'                             // BRT-100
        attribute 'calibrated', 'enum', ['false', 'true']           // Aqara E1
        attribute 'calibrationTemp', 'number'                       // BRT-100, Sonoff, NAMRON
        attribute 'controlType', 'enum', ['Room sensor', 'floor sensor', 'Room+floor sensor']  // NAMRON
        attribute 'emergencyHeatTime', 'number'                     // NAMRON
        attribute 'floorCalibrationTemp', 'number'                  // NAMRON
        attribute 'childLock', 'enum', ['off', 'on']                // BRT-100, Aqara E1, Sonoff, AVATTO
        attribute 'ecoMode', 'enum', ['off', 'on']                  // BRT-100
        attribute 'ecoTemp', 'number'                               // BRT-100
        attribute 'emergencyHeating', 'enum', ['off', 'on']         // BRT-100
        attribute 'emergencyHeatingTime', 'number'                  // BRT-100
        attribute 'floorTemperature', 'number'                      // AVATTO/MOES floor thermostats
        attribute 'floorSensorType', 'enum', ['NTC 10K/25', 'NTC 15K/25', 'NTC 12K/25', 'NTC 100K/25', 'NTC 50K/25']  // NAMRON
        attribute 'frostProtectionTemperature', 'number'            // Sonoff
        attribute 'hysteresis', 'number'                            // AVATTO, Virtual thermostat, NAMRON
        attribute 'level', 'number'                                 // BRT-100
        attribute 'maxHeatingSetpoint', 'number'                    // BRT-100, Sonoff, AVATTO
        attribute 'minHeatingSetpoint', 'number'                    // BRT-100, Sonoff, AVATTO
        attribute 'modeAfterDry', 'enum', ['off', 'manual', 'auto', 'away']      // NAMRON  
        attribute 'powerUpStatus', 'enum', ['default', 'last']      // NAMRON
        attribute 'sensor', 'enum', ['internal', 'external', 'both']         // Aqara E1, AVATTO
        attribute 'systemMode', 'enum', ['off', 'heat', 'on']               // Aqara E1, AVATTO     // TODO - decide on or heat?
        attribute 'valveAlarm', 'enum',  ['false', 'true']          // Aqara E1
        attribute 'valveDetection', 'enum', ['off', 'on']           // Aqara E1
        attribute 'weeklyProgram', 'number'                         // BRT-100
        attribute 'windowOpenDetection', 'enum', ['off', 'on']      // BRT-100, Aqara E1, Sonoff
        attribute 'windowsState', 'enum', ['open', 'closed']        // BRT-100, Aqara E1
        attribute 'batteryLowAlarm', 'enum', ['batteryOK', 'batteryLow']        // TUYA_SASWELL
        //attribute 'workingState', "enum", ["open", "closed"]        // BRT-100

        // Aqaura E1 attributes     TODO - consolidate a common set of attributes
        attribute 'preset', 'enum', ['manual', 'auto', 'away']      // TODO - remove?

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
    'NAMRON'   : [
            description   : 'NAMRON Thermostat',
            device        : [manufacturers: ['NAMRON AS'], type: 'TRV', powerSource: 'mains', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [calibrationTemp: '0x0201:0x0010', awaySetPoint: '0x0201:0x0014', lcdBrightnesss:'0x0201:0x1000', temperatureDisplayMode:'0x0201:0x1008', displayAutoOffEnable:'0x0201:0x100B', childLock:'0x0204:0x0001',
                             floorSensorType:'0x0201:0x1002', controlType:'0x0201:0x1003', powerUpStatus:'0x0201:0x1004', floorCalibrationTemp:'0x0201:0x1005', emergencyHeatTime:'0x0201:0x1006', modeAfterDry:'0x0201:0x1007', /*windowOpenCheck:'0x0201:0x1009',*/ hysteresis:'0x0201:0x100A',
                             /*alarmAirTempOverValue:'0x0201:0x2001', awayModeSet:'0x0201:0x2002'*/
                             ],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0009,000A,0201,0204,0702,0B04", outClusters:"0003,0019", model:"4512737", manufacturer:"NAMRON AS", controllerType: "ZGB", deviceJoinName: 'NAMRON'] ,
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,0009,0408,0702,0B04,0B05,1000,FCCC', outClusters:'0019,1000', model:'4512749-N', manufacturer:'NAMRON AS', deviceJoinName: 'NAMRON']   // EP02: 0000,0004,0005,0201  // EPF2: 0021
            ],
            commands      : [resetStats:'resetStats', refresh:'refresh', initialize:'initialize', updateAllPreferences: 'updateAllPreferences', resetPreferencesToDefaults:'resetPreferencesToDefaults', validateAndFixPreferences:'validateAndFixPreferences',
                              factoryResetThermostat:'factoryResetThermostat', sendSupportedThermostatModes: 'sendSupportedThermostatModes', refreshAll: 'refreshAll'
            ],
            attributes    : [
                [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt:'0x29', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', description:'Measured room temperature'],                                                                      // ^^^ (int16S, read-only) reportable LocalTemperature : Attribute This is room temperature, the maximum resolution this format allows is 0.01 ºC.
                [at:'0x0201:0x0001',  name:'floorTemperature',         type:'decimal', dt:'0x29', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'Floor temperature'],                                                                             // ^^^ (int16S, read-only) reportable OutdoorTemperature : This is floor temperature, the maximum resolution this format allows is 0.01 ºC.
                //[at:'0x0201:0x0002',  name:'occupancy',                type:'enum',    dt:'0x30', rw: 'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'away', 1: 'heat'], unit:'',  description:'Occupancy'],                                                      // ^^^ (bitmap8, read-only) Occupancy : When this flag is set as 1, it means occupied, OccupiedHeatingSetpoint will be used, otherwise UnoccupiedHeatingSetpoint will be used
                [at:'0x0201:0x0010',  name:'calibrationTemp',          type:'decimal', dt:'0x28', rw: 'rw', min:-3.0, max:3.0,  defVal:0.0, step:0.1, scale:10,  unit:'°C', title: '<b>Local Temperature Calibration</b>', description:'Room temperature calibration'],         // ^^^ (Int8S, reportable) TODO: check dt!!!    LocalTemperatureCalibration : Room temperature calibration, range is -30-30, the maximum resolution this format allows 0.1°C. Default value: 0
                [at:'0x0201:0x0011',  name:'coolingSetpoint',          type:'decimal', dt:'0x29', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Cooling Setpoint</b>',              description:'This system is not invalid'],                      // not used
                [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt:'0x29', rw: 'rw', min:0.0,  max:40.0, defVal:30.0, step:0.01, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],          // ^^^(int16S, reportable)  OccupiedHeatingSetpoint : Range is 0-4000,the maximum resolution this format allows is 0.01 ºC. Default is 0xbb8(30.00ºC)
                [at:'0x0201:0x0014',  name:'awaySetPoint',             type:'decimal', dt:'0x29', rw: 'rw', min:0.0,  max:40.0, defVal:6.0,  step:0.01, scale:100,  unit:'°C', title: '<b>Away Heating Setpoint</b>',      description:'Away heating setpoint'],    // ^^^(int16S, reportable)  Un-OccupiedHeatingSetpoint : Range is 0-4000,the maximum resolution this format allows is 0.01 ºC. Default is 0x258(6.00ºC)
                [at:'0x0201:0x001B',  name:'controlSequenceOfOperation', type:'enum',  dt:'0x30', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 2: 'heat'], unit:'',  description:'device supported operation type'],                                  // ^^^(Map16, read-only, reportable) HVAC relay state/ termostatRunningState Indicates the relay on/off status, here only supports bit0( Heat State)
                [at:'0x0201:0x001C',  name:'thermostatMode',           type:'enum',   dt:'0x30', rw: 'rw', map:[0: 'off', 4: 'heat'], title: '<b>Thermostat Mode</b>', description:'Thermostat (System) Mode'],
                [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum',    dt:'0x30', rw: 'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heating'], unit:'',  description:'Thermostat Operating State (relay on/off status)'],                      // ^^^(Map16, read-only, reportable) HVAC relay state/ termostatRunningState Indicates the relay on/off status, here only supports bit0( Heat State)
                // ===
                [at:'0x0204:0x0001',  name:'childLock',                type:'enum',    dt:'0x30', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'', title: '<b>Child Lock</b>', description:'Keyboard lockout'],
                // ============ Proprietary Attributes: Manufacturer code 0x1224 ============
                [at:'0x0201:0x1000',  name:'lcdBrightnesss',           type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defVal:'1',   step:1,  scale:1,    map:[0: 'Low Level', 1: 'Mid Level', 2: 'High Level'], unit:'',  title: '<b>OLED brightness</b>',description:'OLED brightness'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  OLED brightness when operate the buttons: Value=0 : Low Level Value=1 : Mid Level(default) Value=2 : High Level
                [at:'0x0201:0x1002',  name:'floorSensorType',          type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:1,    max:5, defVal:'1',step:1,  scale:1,    map:[1: 'NTC 10K/25', 2: 'NTC 15K/25', 3: 'NTC 12K/25', 4: 'NTC 100K/25', 5: 'NTC 50K/25'], unit:'',  title: '<b>Floor Sensor Type</b>',description:'Floor Sensor Type'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  TODO: check why there are 3 diferent enum groups???    FloorSenserType Value=5 : NTC 12K/25  Value=4 : NTC 100K/25 Value=3 : NTC 50K/25 Select external (Floor) sensor type: Value=1 : NTC 10K/25 (Default) Value=2 : NTC 15K/25 Value=5 : NTC 12K/25 Value=4 : NTC 100K/25 Value=3 : NTC 50K/25 Select external (Floor) sensor type: Value=1 : NTC 10K/25 (Default) Value=2 : NTC 15K/25
                [at:'0x0201:0x1003',  name:'controlType',              type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defVal:'0',step:1,  scale:1,    map:[0: 'Room sensor', 1: 'floor sensor', 2: 'Room+floor sensor'], unit:'',  title: '<b>Control Type</b>',description:'Control Type'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  ControlType The referring sensor for heat control: Value=0 : Room sensor(Default) Value=1 : floor sensor Value=2 : Room+floor sensor
                [at:'0x0201:0x1004',  name:'powerUpStatus',            type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'1',   step:1,  scale:1,    map:[0: 'default', 1: 'last'], title: '<b>Power Up Status</b>',description:'Power Up Status'],                // ^^^ (ENUM8,reportable) TODO: check dt!!! PowerUpStatus Value=0 : default mode The mode after reset power of the device: Value=1 : last status before power off (Default)
                [at:'0x0201:0x1005',  name:'floorCalibrationTemp',     type:'decimal', dt:'0x28',  mfgCode:'0x1224', rw: 'rw', min:-3.0,  max:3.0, defVal:0.0, step:0.1, scale:10,  unit:'°C', title: '<b>Floor Sensor Calibration</b>', description:'Floor Sensor Calibration/i>'],                // ^^^ (Int8S, reportable) TODO: check dt!!!    FloorSenserCalibration The temp compensation for the external (floor) sensor, range is -30-30, unit is 0.1°C. default value 0
                [at:'0x0201:0x1006',  name:'emergencyHeatTime',        type:'number',  dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:5,  max:100, defVal:5, step:1, scale:1,  unit:'minutes', title: '<b>Emergency Heat Time</b>', description:'The duration of Emergency Heat time (dry time)>'],                // ^^^ (Int8S, reportable) TODO: check dt!!!    DryTime The duration of Dry Mode, range is 5-100, unit is min. Default value is 5.
                [at:'0x0201:0x1007',  name:'modeAfterDry',             type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defVal:'2',   step:1,  scale:1,    map:[0: 'off', 1: 'manual mode', 2: 'auto mode', 3: 'away mode'], unit:'',  title: '<b>Mode After Dry</b>',description:'The mode after Dry Mode'],                // ^^^ (ENUM8,reportable) TODO: check dt!!! ModeAfterDry The mode after Dry Mode: Value=0 : OFF Value=1 : Manual mode Value=2 : Auto mode –schedule (default) Value=3 : Away mode
                [at:'0x0201:0x1008',  name:'temperatureDisplayMode',   type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'1',   step:1,  scale:1,    map:[0: 'Room Temp', 1: 'Floor Temp'], unit:'',  title: '<b>Temperature Display</b>',description:'Temperature Display'],                // ^^^ (ENUM8,reportable) TODO: check dt!!! TemperatureDisplay Value=0 : Room Temp (Default) Value=1 : Floor Temp
                [at:'0x0201:0x1009',  name:'windowOpenCheck',          type:'decimal', dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:0.3, max:8.0, defVal:0, step:0.5, scale:10,  unit:'', title: '<b>Window Open Check</b>', description:'The threshold to detect open window, 0 means disabled'],                // ^^^ (INT8U,reportable) TODO: check dt!!!    WindowOpenCheck The threshold to detect open window, range is 0.3-8, unit is 0.5ºC, 0 means disabled, default is 0
                [at:'0x0201:0x100A',  name:'hysteresis',               type:'decimal', dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0.5, max:2.0, defVal:0.5, step:0.1, scale:10,  unit:'', title: '<b>Hysteresis</b>', description:'Hysteresis'],                // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  Hysteresis setting, range is 5-20, unit is 0.1ºC, default value is 5
                [at:'0x0201:0x100B',  name:'displayAutoOffEnable',     type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'1',   step:1,  scale:1,    map:[0: 'Disabled', 1: 'Enabled'], unit:'',  title: '<b>Display Auto Off</b>',description:'Display Auto Off disable/enable'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  DisplayAutoOffEnable 0, disable Display Auto Off function 1, enable Display Auto Off function
                [at:'0x0201:0x2001',  name:'alarmAirTempOverValue',    type:'decimal', dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:0.2, max:6.0, defVal:4.5, step:0.1, scale:10,  unit:'', title: '<b>Alarm Air Temp Over Value</b>', description:'Alarm Air Temp Over Value, 0 means disabled,'],                // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  AlarmAirTempOverValue Room temp alarm threshold, range is 0.20-60, unit is 1ºC,0 means disabled, default is 45
                [at:'0x0201:0x2002',  name:'awayModeSet',              type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'0',   step:1,  scale:1,    map:[0: 'Not away', 1: 'Away'], unit:'',  title: '<b>Away Mode Set</b>',description:'Away Mode Set'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  Away Mode Set: Value=1: away Value=0: not away (default)
                // Command supported  !!!!!!!!!! TODO !!!!!!!!!!!! not attribute, but a command !!!!!!!!!!!!!!!!
                [cmd:'0x0201:0x0000',  name:'setpointRaiseLower',       type:'decimal', dt:'0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:10,  unit:'°C', description:'Setpoint Raise/Lower']                // ^^^ Setpoint Raise/Lower Increase or decrease the set temperature according to current mode, unit is 0.1ºC
            ],
            supportedThermostatModes : ['off', 'heat'],
            supportedThermostatFanModes : ['off'],
            refresh: ['pollThermostatCluster'],
            deviceJoinName: 'NAMRON Thermostat',
            configuration : [:]
    ],

    '---'   : [
            description   : '--------------------------------------',
    ],

    // TODO = check constants! https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/lib/constants.ts#L17
    'GENERIC_ZIGBEE_THERMOSTAT' : [
            description   : 'Generic Zigbee Thermostat',
            device        : [type: 'TRV', powerSource: 'battery', isSleepy:false],
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
            refresh: ['pollThermostatCluster'],
            deviceJoinName: 'Generic Zigbee Thermostat',
            configuration : [:]
    ]
]

void off() { setThermostatMode('off') }
void on()  { setThermostatMode('heat') }    // not used

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
    List<String> cmds = []
    DEVICE.attributes.each { attr ->
        Map map = attr as Map
        log.trace "refreshAll: ${map} "
        if (map != null && map.at != null) {
            Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:]
            cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100)
        }
    }
    logDebug "refreshAll: ${cmds} "
    sendZigbeeCommands(cmds)
}

List<String> refreshNamron() {
    List<String> cmds = []
    logDebug 'refreshNamron() ...'
    cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0001, 0x0010, 0x0012,0x0014, 0x001b, 0x001c, 0x0029], [:], delay = 255) // temperature, floorTemperature, occupancy, calibration, heatingSetpoint, unoccupiedHeatingSetpoint, termostatRunningState, systemMode, thermostatOperatingState
    cmds += zigbee.readAttribute(0x0b04, [0x0505, 0x0508, 0x050b, 0x0802], [:], delay = 255)    // ActivePower, ActiveEnergy, ActivePower, ACCurrentOverload
    cmds += zigbee.readAttribute(0x0702, 0x0000, [:], delay = 255)    // CurrentSummationDelivered - energy consumption
    return cmds
}

List<String> configureNamron() {
    List<String> cmds = []
    logDebug 'configureNamron() ...'
    cmds += zigbee.configureReporting(0x201, 0x0000, 0x29, 0, 600, 50) // Configure LocalTemperature reporting
    cmds += zigbee.configureReporting(0x201, 0x0010, 0x21, 0, 600, 50) // Configure LocalTemperatureCalibration reporting
    cmds += zigbee.configureReporting(0x201, 0x0012, 0x29, 0, 600, 50) // Configure OccupiedHeatingSetpoint reporting
    cmds += zigbee.configureReporting(0x201, 0x001c, 0x20, 0, 600, 1) // Configure SystemMode reporting
    cmds += zigbee.configureReporting(0x0b04, 0x050b, 0x21, 0, 600, 50) // Configure ActivePower reporting
    return cmds

}

List<String> customRefresh() {
    List<String> cmds = refreshFromDeviceProfileList()
    cmds += refreshNamron()
    logDebug "customRefresh: ${cmds} "
    return cmds
}

List<String> customConfigure() {
    List<String> cmds = []
    cmds += configureNamron()
    logDebug "customConfigure() : ${cmds} "
    return cmds
}

// called from initializeDevice in the commonLib code
List<String> customInitializeDevice() {
    List<String> cmds = []
    //int intMinTime = 300
    //int intMaxTime = 600    // report temperature every 10 minutes !
    cmds += configureNamron()
/*
    if ( getDeviceProfile() == 'SONOFF_TRV') {
        cmds += initializeSonoff()
    }
    else if (getDeviceProfile() == 'AQARA_E1_TRV' ) {
        cmds += initializeAqara()
    }
    else { */
        logDebug"customInitializeDevice: nothing to initialize for device group ${getDeviceProfile()}"
    //}
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
//  TODO !!!!!!!!!!!!!!!!!!!
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
    Map result
    if (DEVICE != null && DEVICE.preferences != null && DEVICE.preferences != [:]) {
        (DEVICE.preferences).each { key, value ->
            log.trace "testT: ${key} = ${value}"
            result = inputIt(key, debug = true)
            logDebug "inputIt: ${result}"
        }
    }
    */
    //log.trace "device.lastActivity = ${device.lastActivity}"
    //log.trace "device.lastMessage = ${device.lastMessage}"
    device.name = 'testT'
    //device.roomName = 'TestRoom' read only
    //updateDataValue('lastRunningMode', 'heat')
    device.properties.each { property ->
    log.trace "${property.key} = ${property.value}"
}
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
