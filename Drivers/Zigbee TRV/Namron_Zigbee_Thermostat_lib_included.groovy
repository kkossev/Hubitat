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
 * ver. 3.3.0  2024-06-09 kkossev  - first Namron Zigbee Thermostat version
 * ver. 3.3.1  2024-06-15 kkossev  - calibrationTemp negative values bug fix; added hysteresis, floorCalibrationTemp, powerUpStatus, emergencyHeatTime, modeAfterDry, controlType, floorSensorType, childLock
 * ver. 3.3.2  2024-06-16 kkossev  - added windowOpenCheck preference (not working?); added 'auto' mode, added emergency heat' mode; modeAfterDry default changed to 'heat'; added 'advanced:true' to the attributes map
 *                                    added 'eco' (away) mode; added overHeatAlarm advanced attribute; displayAutoOff is disabled by default; added refreshAll command; added add factoryReset command; fixed energy reporting configuration
 * ver. 3.3.3  2024-06-25 kkossev  - release 3.3.3
 * ver. 3.3.4  2024-06-29 kkossev  - added NAMRON_RADIATOR device profile and attributes;
 * ver. 3.3.5  2024-07-09 kkossev  - release 3.3.5
 * ver. 3.3.6  2024-08-28 kkossev  - added Sunricher thermostat model 'HK-LN-HEATER-A' fingerprint
 * ver. 3.3.7  2024-09-01 kkossev  - New 'Sunricher Thermostat' device profile; fixed missing eco() method; rounded the floorTemperature to 0.1; heatingSetpoint is updated to the ecoSetpoint when eco mode is activated; added ecoSetPoint attribute;
 *                                   removed systemMode'; added command 'eco'; added state.lastHeatingSetpoint; fixed thermostatMode switching from 'eco' to 'off'; fixed emergencyHeating mode update;
 * ver. 3.4.0  2024-10-05 kkossev  - added to HPM
 *
 *                                   TODO:
*/

static String version() { '3.4.0' }
static String timeStamp() { '2024/10/05 7:14 PM' }

@Field static final Boolean _DEBUG = false
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true

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
        name: 'Namron Zigbee Thermostat',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee%20TRV/Namron_Zigbee_Thermostat_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        // capbilities are defined in the thermostatLib
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
        attribute 'ecoSetPoint', 'number'                           // NAMRON
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
        attribute 'temperatureDisplayMode', 'enum', ['room Temp', 'floor temp']  // NAMRON
        attribute 'windowOpenCheck', 'number'                       // NAMRON
        attribute 'windowOpenCheckActivation', 'enum', ['enabled', 'disabled']  // NAMRON_RADIATOR
        attribute 'windowOpenState', 'enum', ['notOpened', 'opened']  // NAMRON_RADIATOR
        attribute 'overHeatMark', 'enum', ['no', 'temperature over 85ºC and lower than 90ºC', 'temperature over 90ºC']  // NAMRON_RADIATOR

        command 'eco', [[name: 'eco', type: 'STRING', description: 'Set the thermostat to Eco mode', defaultValue : '']]
        command 'refreshAll', [[name: 'refreshAll', type: 'STRING', description: 'Refreshes all parameters', defaultValue : '']]
        command 'factoryResetThermostat', [[name: 'factoryResetThermostat', type: 'STRING', description: 'Factory reset the thermostat', defaultValue : '']]
        if (_DEBUG) { command 'testT', [[name: 'testT', type: 'STRING', description: 'testT', defaultValue : '']]  }
        command 'sendCommand', [
            [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']],
            [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']]
        ]
        command 'setPar', [
                [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']],
                [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']]
        ]
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
    'NAMRON'   : [
            description   : 'NAMRON Thermostat',
            device        : [manufacturers: ['NAMRON AS'], type: 'TRV', powerSource: 'mains', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [calibrationTemp: '0x0201:0x0010', ecoSetPoint: '0x0201:0x0014', lcdBrightnesss:'0x0201:0x1000', keyVibration:'0x0201:0x1001', temperatureDisplayMode:'0x0201:0x1008', displayAutoOff:'0x0201:0x100B', childLock:'0x0204:0x0001',
                             floorSensorType:'0x0201:0x1002', controlType:'0x0201:0x1003', powerUpStatus:'0x0201:0x1004', floorCalibrationTemp:'0x0201:0x1005', emergencyHeatTime:'0x0201:0x1006', modeAfterDry:'0x0201:0x1007', windowOpenCheck:'0x0201:0x1009', hysteresis:'0x0201:0x100A',
                             overHeatAlarm:'0x0201:0x2001'/*, ecoMode:'0x0201:0x2002' */// remove ecoMode after testing!
                             ],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0009,000A,0201,0204,0702,0B04", outClusters:"0003,0019", model:"4512737", manufacturer:"NAMRON AS", controllerType: "ZGB", deviceJoinName: 'NAMRON'] ,
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,0009,0408,0702,0B04,0B05,1000,FCCC', outClusters:'0019,1000', model:'4512749-N', manufacturer:'NAMRON AS', deviceJoinName: 'NAMRON']   // EP02: 0000,0004,0005,0201  // EPF2: 0021
            ],
            commands      : [tesT:'testT', resetStats:'resetStats', refresh:'refresh', initialize:'initialize', updateAllPreferences: 'updateAllPreferences', resetPreferencesToDefaults:'resetPreferencesToDefaults', validateAndFixPreferences:'validateAndFixPreferences',
                              factoryResetThermostat:'factoryResetThermostat', sendSupportedThermostatModes: 'sendSupportedThermostatModes', refreshAll: 'refreshAll', configureNamron:'configureNamron'
            ],
            attributes    : [
                [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt:'0x29', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', description:'Measured room temperature'],                                                                      // ^^^ (int16S, read-only) reportable LocalTemperature : Attribute This is room temperature, the maximum resolution this format allows is 0.01 ºC.
                [at:'0x0201:0x0001',  name:'floorTemperature',         type:'decimal', dt:'0x29', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'Floor temperature'],                                                                             // ^^^ (int16S, read-only) reportable OutdoorTemperature : This is floor temperature, the maximum resolution this format allows is 0.01 ºC.
                [at:'0x0201:0x0002',  name:'occupancy',                type:'enum',    dt:'0x30', rw: 'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'away', 1: 'heat'], unit:'',  description:'Occupancy'],                                                          // ^^^ (bitmap8, read-only) Occupancy : When this flag is set as 1, it means occupied, OccupiedHeatingSetpoint will be used, otherwise UnoccupiedHeatingSetpoint will be used
                [at:'0x0201:0x0010',  name:'calibrationTemp',          type:'decimal', dt:'0x28', rw: 'rw', min:-3.0, max:3.0,  defVal:0.0, step:0.1, scale:10,  unit:'°C', title: '<b>Local Temperature Calibration</b>', description:'Room temperature calibration'],         // ^^^ (Int8S, reportable) TODO: check dt!!!    LocalTemperatureCalibration : Room temperature calibration, range is -30-30, the maximum resolution this format allows 0.1°C. Default value: 0
                [at:'0x0201:0x0011',  name:'coolingSetpoint',          type:'decimal', dt:'0x29', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Cooling Setpoint</b>',              description:'This system is not invalid'],                      // not used
                [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt:'0x29', rw: 'rw', min:0.0,  max:40.0, defVal:30.0, step:0.01, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],          // ^^^(int16S, reportable)  OccupiedHeatingSetpoint : Range is 0-4000,the maximum resolution this format allows is 0.01 ºC. Default is 0xbb8(30.00ºC)
                [at:'0x0201:0x0014',  name:'ecoSetPoint',              type:'decimal', dt:'0x29', rw: 'rw', min:0.0,  max:40.0, defVal:6.0,  step:0.01, scale:100,  unit:'°C', title: '<b>Eco (Away) Heating Setpoint</b>',    description:'Away (Eco, unoccupied) heating setpoint'],                // ^^^(int16S, reportable)  Un-OccupiedHeatingSetpoint : Range is 0-4000,the maximum resolution this format allows is 0.01 ºC. Default is 0x258(6.00ºC)
                [at:'0x0201:0x001B',  name:'controlSequenceOfOperation', type:'enum',  dt:'0x30', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 2: 'heat'], unit:'',  description:'device supported operation type'],  // always 2 (heat)                   // ^^^(Map16, read-only, reportable) HVAC relay state/ termostatRunningState Indicates the relay on/off status, here only supports bit0( Heat State)
                [at:'0x0201:0x001C',  name:'thermostatMode',           type:'enum',    dt:'0x30', rw: 'rw', map:[0: 'off', 1: 'auto', 4: 'heat', 8: 'emergency heat'], title: '<b>Thermostat Mode</b>', description:'Thermostat (System) Mode'],
                [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum',    dt:'0x30', rw: 'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heating'], unit:'',  description:'Thermostat Operating State (relay on/off status)'],                  // ^^^(Map16, read-only, reportable) HVAC relay state/ termostatRunningState Indicates the relay on/off status, here only supports bit0( Heat State)
                // Thermostat User Interface Configuration-0x0204(Server)
                [at:'0x0204:0x0001',  name:'childLock',                type:'enum',    dt:'0x30', rw: 'rw', min:0,    max:1,    step:1,  scale:1,  defVal:'0',  map:[0: 'off', 1: 'on'], unit:'', title: '<b>Child Lock</b>', description:'Keyboard lockout'],
                // Proprietary Attributes: Manufacturer code 0x1224
                [at:'0x0201:0x1000',  name:'lcdBrightnesss',           type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defVal:'1',   step:1,  scale:1,    map:[0: 'low Level', 1: 'mid Level', 2: 'high Level'], unit:'',  title: '<b>OLED brightness</b>', description:'OLED brightness'],                 // ^^^ (ENUM8,reportable) TODO: check dt!!!  OLED brightness when operate the buttons: Value=0 : Low Level Value=1 : Mid Level(default) Value=2 : High Level
                [at:'0x0201:0x1001',  name:'keyVibration',             type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defVal:'1',   step:1,  scale:1,    map:[0: 'off', 1: 'low level', 2: 'high Level'], unit:'',  title: '<b>Key Vibration</b>', description:'Key Vibration'],                 // ^^^ (ENUM8,reportable) TODO: check dt!!!  OLED brightness when operate the buttons: Value=0 : Low Level Value=1 : Mid Level(default) Value=2 : High Level
                [at:'0x0201:0x1002',  name:'floorSensorType',          type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:1,    max:5, defVal:'1', advanced:true,         map:[1: 'NTC 10K/25', 2: 'NTC 15K/25', 3: 'NTC 12K/25', 4: 'NTC 100K/25', 5: 'NTC 50K/25'], unit:'',  title: '<b>Floor Sensor Type</b>', description:'Floor Sensor Type'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  TODO: check why there are 3 diferent enum groups???    FloorSenserType Value=5 : NTC 12K/25  Value=4 : NTC 100K/25 Value=3 : NTC 50K/25 Select external (Floor) sensor type: Value=1 : NTC 10K/25 (Default) Value=2 : NTC 15K/25 Value=5 : NTC 12K/25 Value=4 : NTC 100K/25 Value=3 : NTC 50K/25 Select external (Floor) sensor type: Value=1 : NTC 10K/25 (Default) Value=2 : NTC 15K/25
                [at:'0x0201:0x1003',  name:'controlType',              type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defVal:'0', advanced:true,         map:[0: 'room sensor', 1: 'floor sensor', 2: 'room+floor sensor'], unit:'',  title: '<b>Control Type</b>', description:'Control Type'],              // ^^^ (ENUM8,reportable) TODO: check dt!!!  ControlType The referring sensor for heat control: Value=0 : Room sensor(Default) Value=1 : floor sensor Value=2 : Room+floor sensor
                [at:'0x0201:0x1004',  name:'powerUpStatus',            type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'1',   step:1,  scale:1,    map:[0: 'default', 1: 'last'], title: '<b>Power Up Status</b>',description:'Power Up Status'],                                                   // ^^^ (ENUM8,reportable) TODO: check dt!!! PowerUpStatus Value=0 : default mode The mode after reset power of the device: Value=1 : last status before power off (Default)
                [at:'0x0201:0x1005',  name:'floorCalibrationTemp',     type:'decimal', dt:'0x28',  mfgCode:'0x1224', rw: 'rw', min:-3.0,  max:3.0, defVal:0.0, step:0.1, scale:10,  unit:'°C', title: '<b>Floor Sensor Calibration</b>', description:'Floor Sensor Calibration/i>'],                                                // ^^^ (Int8S, reportable) TODO: check dt!!!    FloorSenserCalibration The temp compensation for the external (floor) sensor, range is -30-30, unit is 0.1°C. default value 0
                [at:'0x0201:0x1006',  name:'emergencyHeatTime',        type:'number',  dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:5,  max:100, defVal:5, step:1, scale:1,  unit:'minutes', title: '<b>Emergency Heat Time</b>', description:'The duration of Emergency Heat time (dry time)>'],                                    // ^^^ (Int8S, reportable) TODO: check dt!!!    DryTime The duration of Dry Mode, range is 5-100, unit is min. Default value is 5.
                [at:'0x0201:0x1007',  name:'modeAfterDry',             type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:3, defVal:'1', advanced:true,         map:[0: 'off', 1: 'manual', 2: 'auto', 3: 'eco'], unit:'',  title: '<b>Mode After Emergency Heat (Dry) mode</b>', description:'The mode the thermostat will switch automatically after the Emergency Heat (Dry) Mode'],   // ^^^ (ENUM8,reportable) TODO: check dt!!! ModeAfterDry The mode after Dry Mode: Value=0 : OFF Value=1 : Manual mode Value=2 : Auto mode –schedule (default) Value=3 : Away mode
                [at:'0x0201:0x1008',  name:'temperatureDisplayMode',   type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'1',   step:1,  scale:1,    map:[0: 'room Temp', 1: 'floor temp'], unit:'',  title: '<b>Temperature Display</b>',description:'Temperature Display'],                         // ^^^ (ENUM8,reportable) TODO: check dt!!! TemperatureDisplay Value=0 : Room Temp (Default) Value=1 : Floor Temp
                [at:'0x0201:0x1009',  name:'windowOpenCheck',          type:'decimal', dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0.0, max:8.0, defVal:0.0, step:0.5, scale:10,  unit:'°C', title: '<b>Window Open Check</b>', description:'The threshold to detect open window, 0 means disabled'],                               // ^^^ (INT8U,reportable) TODO: check dt!!!    WindowOpenCheck The threshold to detect open window, range is 0.3-8, unit is 0.5ºC, 0 means disabled, default is 0
                [at:'0x0201:0x100A',  name:'hysteresis',               type:'decimal', dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0.5, max:2.0, defVal:0.5, step:0.1, scale:10,  unit:'°C', title: '<b>Hysteresis</b>', description:'Hysteresis'],                                                                                 // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  Hysteresis setting, range is 5-20, unit is 0.1ºC, default value is 5
                [at:'0x0201:0x100B',  name:'displayAutoOff',           type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'0',   step:1,  scale:1,    map:[0: 'disabled', 1: 'enabled'], unit:'',  title: '<b>Display Auto Off</b>',description:'Display Auto Off disable/enable'],                    // ^^^ (ENUM8,reportable) TODO: check dt!!!  DisplayAutoOffEnable 0, disable Display Auto Off function 1, enable Display Auto Off function
                [at:'0x0201:0x2001',  name:'overHeatAlarm',            type:'decimal', dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:0.0, max:6.0, defVal:4.5, step:0.1, scale:10, advanced:true, unit:'°C', title: '<b>Over Heat Alarm</b>', description:'Room temp alarm threshold, 0 means disabled,'],                                  // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  AlarmAirTempOverValue Room temp alarm threshold, range is 0.20-60, unit is 1ºC,0 means disabled, default is 45
                [at:'0x0201:0x2002',  name:'ecoMode',                  type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'0',   step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'',  title: '<b>Eco (Away) Mode</b>', description:'Eco (Away) Mode'],                                            // ^^^ (ENUM8,reportable) TODO: check dt!!!  Away Mode Set: Value=1: away Value=0: not away (default)
                // Command supported  !!!!!!!!!! TODO !!!!!!!!!!!! not attribute, but a command !!!!!!!!!!!!!!!!
                [cmd:'0x0201:0x0000',  name:'setpointRaiseLower',       type:'decimal', dt:'0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:10,  unit:'°C', description:'Setpoint Raise/Lower']                // ^^^ Setpoint Raise/Lower Increase or decrease the set temperature according to current mode, unit is 0.1ºC
            ],
            supportedThermostatModes : ['off', 'heat', 'auto', 'emergency heat', 'eco'],
            supportedThermostatFanModes : ['off'],
            refresh: ['pollThermostatCluster'], // definded in the thermostatLib
            deviceJoinName: 'NAMRON Thermostat',
            configuration : [:]
    ],

    'NAMRON_RADIATOR'   : [
            description   : 'NAMRON Radiator',
            device        : [manufacturers: ['NAMRON AS'], type: 'TRV', powerSource: 'mains', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [calibrationTemp: '0x0201:0x0010', lcdBrightnesss:'0x0201:0x1000', displayAutoOffActivation:'0x0201:0x1001', temperatureDisplayMode:'0x0201:0x1008', displayAutoOffActivation:'0x0201:0x1001', childLock:'0x0204:0x0001',
                             powerUpStatus:'0x0201:0x1004', windowOpenCheckActivation:'0x0201:0x1009', hysteresis:'0x0201:0x100A',
                             overHeatAlarm:'0x0201:0x2001'/*, ecoMode:'0x0201:0x2002' */// remove ecoMode after testing!
                             ],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0009,000A,0201,0204,0702,0B04", outClusters:"0001,0003,0019", model:"5401392", manufacturer:"NAMRON AS", controllerType: "ZGB", deviceJoinName: 'NAMRON'] ,
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0009,000A,0201,0204,0702,0B04", outClusters:"0001,0003,0019", model:"5401396", manufacturer:"NAMRON AS", controllerType: "ZGB", deviceJoinName: 'NAMRON'] ,
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0009,000A,0201,0204,0702,0B04", outClusters:"0001,0003,0019", model:"5401393", manufacturer:"NAMRON AS", controllerType: "ZGB", deviceJoinName: 'NAMRON'] ,
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0009,000A,0201,0204,0702,0B04", outClusters:"0001,0003,0019", model:"5401397", manufacturer:"NAMRON AS", controllerType: "ZGB", deviceJoinName: 'NAMRON'] ,  // @tomas
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0009,000A,0201,0204,0702,0B04", outClusters:"0001,0003,0019", model:"5401394", manufacturer:"NAMRON AS", controllerType: "ZGB", deviceJoinName: 'NAMRON'] ,
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0009,000A,0201,0204,0702,0B04", outClusters:"0001,0003,0019", model:"5401398", manufacturer:"NAMRON AS", controllerType: "ZGB", deviceJoinName: 'NAMRON'] ,  // @tomas
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0009,000A,0201,0204,0702,0B04", outClusters:"0001,0003,0019", model:"5401395", manufacturer:"NAMRON AS", controllerType: "ZGB", deviceJoinName: 'NAMRON'] ,
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0009,000A,0201,0204,0702,0B04", outClusters:"0001,0003,0019", model:"5401399", manufacturer:"NAMRON AS", controllerType: "ZGB", deviceJoinName: 'NAMRON'] ,
            ],
            commands      : [resetStats:'resetStats', refresh:'refresh', initialize:'initialize', updateAllPreferences: 'updateAllPreferences', resetPreferencesToDefaults:'resetPreferencesToDefaults', validateAndFixPreferences:'validateAndFixPreferences',
                              factoryResetThermostat:'factoryResetThermostat', sendSupportedThermostatModes: 'sendSupportedThermostatModes', refreshAll: 'refreshAll', configureNamron:'configureNamron'
            ],
            attributes    : [
                [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt:'0x29', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', description:'Measured room temperature'],
                [at:'0x0201:0x0010',  name:'calibrationTemp',          type:'decimal', dt:'0x28', rw: 'rw', min:-3.0, max:3.0,  defVal:0.0, step:0.1, scale:10,  unit:'°C', title: '<b>Local Temperature Calibration</b>', description:'Room temperature calibration'],
                [at:'0x0201:0x0011',  name:'coolingSetpoint',          type:'decimal', dt:'0x29', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Cooling Setpoint</b>',              description:'This system is not invalid'],                      // not used
                [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt:'0x29', rw: 'rw', min:5.0,  max:35.0, defVal:30.0, step:0.01, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
                [at:'0x0201:0x001B',  name:'controlSequenceOfOperation', type:'enum',  dt:'0x30', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 2: 'heat'], unit:'',  description:'device supported operation type'],
                [at:'0x0201:0x001C',  name:'thermostatMode',           type:'enum',    dt:'0x30', rw: 'rw', map:[0: 'off', 1: 'auto', 4: 'heat', 8: 'emergency heat'], title: '<b>Thermostat Mode</b>', description:'Thermostat (System) Mode'],
                [at:'0x0201:0x0020',  name:'startOfWeek',              type:'enum',    dt:'0x30', rw: 'rw', map:[0: 'Sun', 1: 'Mon'], title: '<b>Start Of Week</b>', description:'Start of week'],
                [at:'0x0201:0x0021',  name:'numberOfWeeklyTransitions',type:'enum',    dt:'0x30', rw: 'ro', map:[0: '7 transitions'], title: '<b>Number of Weekly Transitions</b>', description:'Number of weekly transitions'],
                [at:'0x0201:0x0022',  name:'numberOfDailyTransitions', type:'enum',    dt:'0x30', rw: 'ro', map:[0: '4 time periods'], title: '<b>Number of Daily Transitions</b>', description:'Number of daily transitions'],
                [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum',    dt:'0x30', rw: 'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heating'], unit:'',  description:'Thermostat Operating State (relay on/off status)'],
                // Thermostat User Interface Configuration-0x0204(Server)
                [at:'0x0204:0x0000',  name:'temperatureDisplayMode',   type:'enum',    dt:'0x30', rw: 'ro', map:[0: 'Temperature in °C'], description:'Temperature display mode'],
                [at:'0x0204:0x0001',  name:'childLock',                type:'enum',    dt:'0x30', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'', title: '<b>Child Lock</b>', description:'Keyboard lockout'],
                // Proprietary Attributes: Manufacturer code 0x1224
                [at:'0x0201:0x1000',  name:'lcdBrightnesss',           type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:1,    max:7, defVal:'1', map:[1: 'Level 1', 2: 'Level 2', 3: 'Level 3', 4: 'Level 4', 5: 'Level 5', 6: 'Level 6', 7: 'Level 7'], unit:'',  title: '<b>OLED brightness</b>', description:'OLED brightness'],
                [at:'0x0201:0x1001',  name:'displayAutoOffActivation', type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'0', map:[0: 'deactivated', 1: 'activated'], title: '<b>Display Auto Off Activation</b>', description:'Display auto off activation'],
                [at:'0x0201:0x1004',  name:'powerUpStatus',            type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'1',   step:1,  scale:1,    map:[0: 'default', 1: 'last'], title: '<b>Power Up Status</b>',description:'Power Up Status'],
                [at:'0x0201:0x1009',  name:'windowOpenCheckActivation', type:'enum',   dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'1',   step:1,  scale:1,    map:[0: 'enabled', 1: 'disabled'], title: '<b>Window Open Check Activation</b>',description:'Window open check activation'],
                [at:'0x0201:0x100A',  name:'hysteresis',               type:'decimal', dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0.5, max:5.0, defVal:0.5, step:0.1, scale:10,  unit:'°C', title: '<b>Hysteresis</b>', description:'Hysteresis'],
                [at:'0x0201:0x100B',  name:'windowOpenState',          type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'ro', min:0,    max:1, defVal:'0', map:[0: 'notOpened', 1: 'opened'], title: '<b>Window Open State/b>',description:'Window open state'],
                [at:'0x0201:0x2002',  name:'overHeatMark',             type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'ro', min:0,    max:2, defVal:'0', map:[0: 'no', 1: 'temperature over 85ºC and lower than 90ºC', 2: 'temperature over 90ºC'], description:'OoverHeatMark'],
                // commands supported
                [cmd:'0x0201:0x0000',  name:'setpointRaiseLower',      type:'decimal', dt:'0x21', rw: 'ow', min:5.0,  max:35.0, step:0.5, scale:10,  unit:'°C', description:'Setpoint Raise/Lower'],
                [cmd:'0x0201:0x0001',  name:'setWeeklySchedule',       type:'decimal', dt:'0x21', rw: 'ow', description:'Set Weekly Schedule'],
                [cmd:'0x0201:0x0002',  name:'gettWeeklySchedule',      type:'decimal', dt:'0x21', rw: 'ro', description:'Get Weekly Schedule']
            ],
            supportedThermostatModes : ['off', 'heat', 'auto'],
            supportedThermostatFanModes : ['off'],
            refresh: ['pollThermostatCluster'],
            deviceJoinName: 'NAMRON Radiator',
            configuration : [:]
    ],

    // Sunricher HK-LN-HEATER-A' Zigbee Heating Thermostat SR-ZG9092A
    'SUNRICHER'   : [       // https://www.sunricher.com/zigbee-heating-thermostat-sr-zg9092a.html#product_tabs_description_tabbed
            description   : 'Sunricher Thermostat', // https://www.sunricher.com/media/resources/manual/SR-ZG9092A%20instruction.pdf
            device        : [manufacturers: ['Sunricher'], type: 'TRV', powerSource: 'mains', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [calibrationTemp: '0x0201:0x0010', ecoSetPoint: '0x0201:0x0014', lcdBrightnesss:'0x0201:0x1000', keyVibration:'0x0201:0x1001', temperatureDisplayMode:'0x0201:0x1008', displayAutoOff:'0x0201:0x100B', childLock:'0x0204:0x0001',
                             floorSensorType:'0x0201:0x1002', controlType:'0x0201:0x1003', powerUpStatus:'0x0201:0x1004', floorCalibrationTemp:'0x0201:0x1005', emergencyHeatTime:'0x0201:0x1006', modeAfterDry:'0x0201:0x1007', windowOpenCheck:'0x0201:0x1009', hysteresis:'0x0201:0x100A',
                             overHeatAlarm:'0x0201:0x2001'/*, ecoMode:'0x0201:0x2002' */// remove ecoMode after testing!
                             ],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0009,000A,0201,0204,0702,0B04", outClusters:"0003,0019", model:"HK-LN-HEATER-A", manufacturer:"Sunricher", controllerType: "ZGB", deviceJoinName: 'Sunricher HK-LN-HEATER-A'] , //Sunricher Thermostat
            ],
            commands      : [tesT:'testT', resetStats:'resetStats', refresh:'refresh', initialize:'initialize', updateAllPreferences: 'updateAllPreferences', resetPreferencesToDefaults:'resetPreferencesToDefaults', validateAndFixPreferences:'validateAndFixPreferences',
                              factoryResetThermostat:'factoryResetThermostat', sendSupportedThermostatModes: 'sendSupportedThermostatModes', refreshAll: 'refreshAll', configureNamron:'configureNamron'
            ],
            attributes    : [
                [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt:'0x29', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', description:'Measured room temperature'],
                [at:'0x0201:0x0001',  name:'floorTemperature',         type:'decimal', dt:'0x29', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'Floor temperature'],
                [at:'0x0201:0x0002',  name:'occupancy',                type:'enum',    dt:'0x30', rw: 'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'away', 1: 'heat'], unit:'',  description:'Occupancy'],    // When this flag is set as 1, it means occupied, OccupiedHeatingSetpoint will be used, otherwise UnoccupiedHeatingSetpoint will be used.                                                          // ^^^ (bitmap8, read-only) Occupancy : When this flag is set as 1, it means occupied, OccupiedHeatingSetpoint will be used, otherwise UnoccupiedHeatingSetpoint will be used
                [at:'0x0201:0x0010',  name:'calibrationTemp',          type:'decimal', dt:'0x28', rw: 'rw', min:-3.0, max:3.0,  defVal:0.0, step:0.1, scale:10,  unit:'°C', title: '<b>Local Temperature Calibration</b>', description:'Room temperature calibration'],
                [at:'0x0201:0x0011',  name:'coolingSetpoint',          type:'decimal', dt:'0x29', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Cooling Setpoint</b>',              description:'This system is not invalid'],
                [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt:'0x29', rw: 'rw', min:0.0,  max:40.0, defVal:30.0, step:0.01, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
                [at:'0x0201:0x0014',  name:'ecoSetPoint',             type:'decimal', dt:'0x29', rw: 'rw', min:0.0,  max:40.0, defVal:6.0,  step:0.01, scale:100,  unit:'°C', title: '<b>Eco (Away) Heating Setpoint</b>',    description:'Away (Eco, unoccupied) heating setpoint'],
                [at:'0x0201:0x001B',  name:'controlSequenceOfOperation', type:'enum',  dt:'0x30', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 2: 'heat'], unit:'',  description:'device supported operation type'],  // always 2 (heat)
                [at:'0x0201:0x001C',  name:'thermostatMode',           type:'enum',    dt:'0x30', rw: 'rw', map:[0: 'off', 1: 'auto', 4: 'heat', 8: 'emergency heat'], title: '<b>Thermostat Mode</b>', description:'Thermostat (System) Mode'],
                [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum',    dt:'0x30', rw: 'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heating'], unit:'',  description:'Thermostat Operating State (relay on/off status)'],
                // Thermostat User Interface Configuration-0x0204(Server)
                [at:'0x0204:0x0000',  name:'temperatureDisplayMode2',   type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'0',   step:1,  scale:1,    map:[0: 'room Temp', 1: 'floor temp'], unit:'',  title: '<b>Temperature Display</b>',description:'Temperature Display'],
                [at:'0x0204:0x0001',  name:'childLock',                type:'enum',    dt:'0x30', rw: 'rw', min:0,    max:1,    step:1,  scale:1,  defVal:'0',  map:[0: 'off', 1: 'on'], unit:'', title: '<b>Child Lock</b>', description:'Keyboard lockout'],
                // Proprietary Attributes: Manufacturer code 0x1224
                [at:'0x0201:0x1000',  name:'lcdBrightnesss',           type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defVal:'1',   step:1,  scale:1,    map:[0: 'low Level', 1: 'mid Level', 2: 'high Level'], unit:'',  title: '<b>OLED brightness</b>', description:'OLED brightness when operating the buttons'],
                [at:'0x0201:0x1001',  name:'keyVibration',             type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defVal:'1',   step:1,  scale:1,    map:[0: 'off', 1: 'low level', 2: 'high Level'], unit:'',  title: '<b>Key Vibration</b>', description:'Key beep volume & vibration level'],
                [at:'0x0201:0x1002',  name:'floorSensorType',          type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:1,    max:5, defVal:'1', advanced:true,         map:[1: 'NTC 10K/25', 2: 'NTC 15K/25', 3: 'NTC 12K/25', 4: 'NTC 100K/25', 5: 'NTC 50K/25'], unit:'',  title: '<b>Floor Sensor Type</b>', description:'External (Floor) sensor type'],
                [at:'0x0201:0x1003',  name:'controlType',              type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defVal:'0', advanced:true,         map:[0: 'room sensor', 1: 'floor sensor', 2: 'room+floor sensor'], unit:'',  title: '<b>Control Type</b>', description:'The referring sensor for heat control'],
                [at:'0x0201:0x1004',  name:'powerUpStatus',            type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'1',   step:1,  scale:1,    map:[0: 'default', 1: 'last'], title: '<b>Power Up Status</b>',description:'The mode after reset power of the device'],
                [at:'0x0201:0x1005',  name:'floorCalibrationTemp',     type:'decimal', dt:'0x28',  mfgCode:'0x1224', rw: 'rw', min:-3.0,  max:3.0, defVal:0.0, step:0.1, scale:10,  unit:'°C', title: '<b>Floor Sensor Calibration</b>', description:'The temp compensation for the external (floor) sensor'],                                                // ^^^ (Int8S, reportable) TODO: check dt!!!    FloorSenserCalibration The temp compensation for the external (floor) sensor, range is -30-30, unit is 0.1°C. default value 0
                [at:'0x0201:0x1006',  name:'emergencyHeatTime',        type:'number',  dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:5,  max:100, defVal:5, step:1, scale:1,  unit:'minutes', title: '<b>Emergency Heat Time</b>', description:'The duration of Emergency Heat time (Dry) time)>'],                                    // ^^^ (Int8S, reportable) TODO: check dt!!!    DryTime The duration of Dry Mode, range is 5-100, unit is min. Default value is 5.
                [at:'0x0201:0x1007',  name:'modeAfterDry',             type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:3, defVal:'1', advanced:true,         map:[0: 'off', 1: 'manual', 2: 'auto', 3: 'eco'], unit:'',  title: '<b>Mode After Emergency Heat (Dry) mode</b>', description:'The mode the thermostat will switch automatically after the Emergency Heat (Dry) Mode'],   // ^^^ (ENUM8,reportable) TODO: check dt!!! ModeAfterDry The mode after Dry Mode: Value=0 : OFF Value=1 : Manual mode Value=2 : Auto mode –schedule (default) Value=3 : Away mode
                [at:'0x0201:0x1008',  name:'temperatureDisplayMode',   type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'0',   step:1,  scale:1,    map:[0: 'room Temp', 1: 'floor temp'], unit:'',  title: '<b>Temperature Display</b>',description:'Temperature Display, default is room temp'],                         // ^^^ (ENUM8,reportable) TODO: check dt!!! TemperatureDisplay Value=0 : Room Temp (Default) Value=1 : Floor Temp
                [at:'0x0201:0x1009',  name:'windowOpenCheck',          type:'decimal', dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0.0, max:8.0, defVal:0.0, step:0.5, scale:10,  unit:'°C', title: '<b>Window Open Check</b>', description:'The threshold to detect open window, 0 means disabled'],                               // ^^^ (INT8U,reportable) TODO: check dt!!!    WindowOpenCheck The threshold to detect open window, range is 0.3-8, unit is 0.5ºC, 0 means disabled, default is 0
                [at:'0x0201:0x100A',  name:'hysteresis',               type:'decimal', dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0.5, max:2.0, defVal:0.5, step:0.1, scale:10,  unit:'°C', title: '<b>Hysteresis</b>', description:'Hysteresis setting'],                                                                                 // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  Hysteresis setting, range is 5-20, unit is 0.1ºC, default value is 5
                [at:'0x0201:0x100B',  name:'displayAutoOff',           type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'0',   step:1,  scale:1,    map:[0: 'disabled', 1: 'enabled'], unit:'',  title: '<b>Display Auto Off</b>',description:'Display Auto Off disable/enable'],                    // ^^^ (ENUM8,reportable) TODO: check dt!!!  DisplayAutoOffEnable 0, disable Display Auto Off function 1, enable Display Auto Off function
                [at:'0x0201:0x2001',  name:'overHeatAlarm',            type:'decimal', dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:0.0, max:35.0, defVal:27.0, step:1, scale:1, advanced:true, unit:'°C', title: '<b>Over Heat Alarm</b>', description:'Floor temperature over heating threshold, 0 means disabled,'],                                  // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  AlarmAirTempOverValue Room temp alarm threshold, range is 0.20-60, unit is 1ºC,0 means disabled, default is 45
                [at:'0x0201:0x2002',  name:'ecoMode',                  type:'enum',    dt:'0x30',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'0',   step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'',  title: '<b>Eco (Away) Mode</b>', description:'Eco (Away) Mode'],                                            // ^^^ (ENUM8,reportable) TODO: check dt!!!  Away Mode Set: Value=1: away Value=0: not away (default)
            ],
            supportedThermostatModes : ['off', 'heat', 'auto', 'emergency heat', 'eco'],
            supportedThermostatFanModes : ['off'],
            refresh: ['pollThermostatCluster'/*, 'pollOccupancy'*/],
            deviceJoinName: 'Sunricher Thermostat',
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
                [at:'0x0201:0x001C',  name:'thermostatMode',           type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'auto', 2:'unknown_2', 4:'heat', 8:'emergency heat'], unit:'', title: '<b>thermostatMode</b>', description:'System Mode ?'],
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
    logInfo 'Refreshing all parameters...'
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
    setRefreshRequest()    // 3 seconds
    sendZigbeeCommands(cmds)
}

List<String> refreshNamron() {
    List<String> cmds = []
    logDebug 'refreshNamron() ...'
    cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0001, 0x0010, 0x0012,0x0014, 0x001c, 0x0029], [:], delay = 255) // temperature, floorTemperature, occupancy, calibration, heatingSetpoint, unoccupiedHeatingSetpoint, termostatRunningState, systemMode, thermostatOperatingState
    cmds += zigbee.readAttribute(0x0b04, [0x0505, 0x0508, 0x050b, 0x0802], [:], delay = 255)    // ActivePower, ActiveEnergy, ActivePower, ACCurrentOverload
    cmds += zigbee.readAttribute(0x0702, 0x0000, [:], delay = 255)    // CurrentSummationDelivered - energy consumption
    return cmds
}

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
    cmds += configureNamron()
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
        case 'floorTemperature' :
            eventMap.value = Math.round(valueScaled * 10) / 10
            eventMap.unit = '°C'
            sendThermostatEvent(eventMap)
            break
        case 'humidity' :
            handleHumidityEvent(valueScaled)
            break
        case 'heatingSetpoint' :
            sendHeatingSetpointEvent(valueScaled)
            state.lastHeatingSetpoint = valueScaled
            break
        case 'thermostatMode' :  // changed 09/01/2024 (0x0201:0x001c)
            if (device.currentValue('ecoMode') == 'on' && valueScaled != 'off') {
                logWarn "customProcessDeviceProfileEvent: ignoring the thermostatMode <b>${valueScaled}</b> event, because the ecoMode is on!"
            }
            else {
                sendThermostatEvent(eventMap)
                state.lastThermostatMode = valueScaled
            }
            break
        case 'ecoMode' :    // changed 09/01/2024
            logTrace "ecoMode = ${valueScaled}"
            sendThermostatEvent(eventMap)
            if (valueScaled == 'on') {  // ecoMode is on
                sendThermostatEvent([name: 'thermostatMode', value: 'eco', isStateChange: true, descriptionText: 'thermostatMode is eco (eco on)', type: 'digital'])
                state.lastThermostatMode = 'eco'
                // added 09/01/2024
                Float ecoSetpoint = device.currentValue('ecoSetPoint') as Float ?: 6.0
                sendThermostatEvent([name: 'heatingSetpoint',    value: ecoSetpoint, isStateChange: true, descriptionText: 'heatingSetpoint is ecoSetpoint', type: 'digital'])
                sendThermostatEvent([name: 'thermostatSetpoint', value: ecoSetpoint, isStateChange: true, descriptionText: 'thermostatSetpoint is ecoSetpoint', type: 'digital'])
            }
            else {
                sendThermostatEvent([name: 'thermostatMode', value: 'heat', isStateChange: true, descriptionText: 'thermostatMode is heat (eco off)', type: 'digital'])
                state.lastThermostatMode = 'heat'
                // added 09/01/2024
                Float lastHeatingSetpoint = state.lastHeatingSetpoint as Float ?: 20.0
                sendThermostatEvent([name: 'heatingSetpoint',    value: state.lastHeatingSetpoint, isStateChange: true, descriptionText: 'heatingSetpoint is lastHeatingSetpoint', type: 'digital'])
                sendThermostatEvent([name: 'thermostatSetpoint', value: state.lastHeatingSetpoint, isStateChange: true, descriptionText: 'thermostatSetpoint is lastHeatingSetpoint', type: 'digital'])
            }
            break
        case 'ecoSetPoint' :
            sendThermostatEvent(eventMap)
            if (device.currentValue('ecoMode') == 'on') {
                sendThermostatEvent([name: 'heatingSetpoint', value: valueScaled, isStateChange: true, descriptionText: 'heatingSetpoint is ecoSetpoint', type: 'digital'])
                sendThermostatEvent([name: 'thermostatSetpoint', value: valueScaled, isStateChange: true, descriptionText: 'thermostatSetpoint is ecoSetpoint', type: 'digital'])
            }
            break
        case 'emergencyHeating' :
            sendEvent(eventMap)
            logInfo "${descText}"
            if (valueScaled == 'on') {  // the valve shoud be completely open, however the level and the working states are NOT updated! :(
                sendEvent(name: 'thermostatMode', value: 'emergency heat', isStateChange: true, descriptionText: 'emergencyHeating is on')
                sendEvent(name: 'thermostatOperatingState', value: 'heating', isStateChange: true, descriptionText: 'emergencyHeating is on')
            }
            else {
                sendEvent(name: 'thermostatMode', value: 'heat', isStateChange: true, descriptionText: 'emergencyHeating is off')
            }
            break
        default :
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event !
            logDebug "event ${name} sent w/ value ${valueScaled}"
            logInfo "${descText}"                                 // send an Info log also (because value changed )  // TODO - check whether Info log will be sent also for spammy DPs ?
            break
    }
}

import java.time.Instant
import java.time.ZoneOffset
import java.time.LocalDateTime

void testT(String par) {

    List<String> cmds = []

    cmds =   ["he raw 0x${device.deviceNetworkId} 1 1 0x000a {40 01 01 00 00 00 e2 78 83 1f 2e       07 00  00    23      a8 ad 1f 2e}", "delay 200",]

    sendZigbeeCommands(cmds)
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDouble, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', documentationLink: '', // library marker kkossev.commonLib, line 4
    version: '3.3.2' // library marker kkossev.commonLib, line 5
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
  * ver. 2.0.0  2023-05-08 kkossev  - first published version 2.x.x // library marker kkossev.commonLib, line 24
  * ver. 2.1.6  2023-11-06 kkossev  - last update on version 2.x.x // library marker kkossev.commonLib, line 25
  * ver. 3.0.0  2023-11-16 kkossev  - first version 3.x.x // library marker kkossev.commonLib, line 26
  * ver. 3.0.1  2023-12-06 kkossev  - info event renamed to Status; txtEnable and logEnable moved to the custom driver settings; 0xFC11 cluster; logEnable is false by default; checkDriverVersion is called on updated() and on healthCheck(); // library marker kkossev.commonLib, line 27
  * ver. 3.0.2  2023-12-17 kkossev  - configure() changes; Groovy Lint, Format and Fix v3.0.0 // library marker kkossev.commonLib, line 28
  * ver. 3.0.3  2024-03-17 kkossev  - more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization // library marker kkossev.commonLib, line 29
  * ver. 3.0.4  2024-04-02 kkossev  - removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; parseE002Cluster; // library marker kkossev.commonLib, line 30
  * ver. 3.0.5  2024-04-05 kkossev  - button methods bug fix; configure() bug fix; handlePm25Event bug fix; // library marker kkossev.commonLib, line 31
  * ver. 3.0.6  2024-04-08 kkossev  - removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups and level and battery methods to dedicated libs + setLevel bug fix; // library marker kkossev.commonLib, line 32
  * ver. 3.0.7  2024-04-23 kkossev  - tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr, activeEpRqCtr; trace ZDO commands; added 0x0406 OccupancyCluster; reduced debug for chatty devices; // library marker kkossev.commonLib, line 33
  * ver. 3.1.0  2024-04-28 kkossev  - unnecesery unschedule() speed optimization; added syncTuyaDateTime(); tuyaBlackMagic() initialization bug fix. // library marker kkossev.commonLib, line 34
  * ver. 3.1.1  2024-05-05 kkossev  - getTuyaAttributeValue bug fix; added customCustomParseIlluminanceCluster method // library marker kkossev.commonLib, line 35
  * ver. 3.2.0  2024-05-23 kkossev  - standardParse____Cluster and customParse___Cluster methods; moved onOff methods to a new library; rename all custom handlers in the libs to statdndardParseXXX // library marker kkossev.commonLib, line 36
  * ver. 3.2.1  2024-06-05 kkossev  - 4 in 1 V3 compatibility; added IAS cluster; setDeviceNameAndProfile() fix; // library marker kkossev.commonLib, line 37
  * ver. 3.2.2  2024-06-12 kkossev  - removed isAqaraTRV_OLD() and isAqaraTVOC_OLD() dependencies from the lib; added timeToHMS(); metering and electricalMeasure clusters swapped bug fix; added cluster 0x0204; // library marker kkossev.commonLib, line 38
  * ver. 3.3.0  2024-06-25 kkossev  - fixed exception for unknown clusters; added cluster 0xE001; added powerSource - if 5 minutes after initialize() the powerSource is still unknown, query the device for the powerSource // library marker kkossev.commonLib, line 39
  * ver. 3.3.1  2024-07-06 kkossev  - removed isFingerbot() dependancy; added FC03 cluster (Frient); removed noDef from the linter; added customParseIasMessage and standardParseIasMessage; powerSource set to unknown on initialize(); // library marker kkossev.commonLib, line 40
  * ver. 3.3.2  2024-07-12 kkossev  - added PollControl (0x0020) cluster; ping for SONOFF // library marker kkossev.commonLib, line 41
  * ver. 3.3.3  2024-09-15 kkossev  - added queryAllTuyaDP(); 2 minutes healthCheck option; // library marker kkossev.commonLib, line 42
  * // library marker kkossev.commonLib, line 43
  *                                   TODO: check deviceCommandTimeout() // library marker kkossev.commonLib, line 44
  *                                   TODO: offlineCtr is not increasing! (ZBMicro); // library marker kkossev.commonLib, line 45
  *                                   TODO: when device rejoins the network, read the battery percentage again (probably in custom handler, not for all devices) // library marker kkossev.commonLib, line 46
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 47
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 48
  *                                   TODO: MOVE ZDO counters to health state? // library marker kkossev.commonLib, line 49
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 50
  *                                   TODO: Versions of the main module + included libraries (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 51
  *                                   TODO: add GetInfo (endpoints list) command (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 52
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 53
  * // library marker kkossev.commonLib, line 54
*/ // library marker kkossev.commonLib, line 55

String commonLibVersion() { '3.3.3' } // library marker kkossev.commonLib, line 57
String commonLibStamp() { '2024/09/15 10:22 AM' } // library marker kkossev.commonLib, line 58

import groovy.transform.Field // library marker kkossev.commonLib, line 60
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 61
import hubitat.device.Protocol // library marker kkossev.commonLib, line 62
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 63
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 64
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 65
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 66
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 67
import java.math.BigDecimal // library marker kkossev.commonLib, line 68

metadata { // library marker kkossev.commonLib, line 70
        if (_DEBUG) { // library marker kkossev.commonLib, line 71
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 72
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 73
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 74
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 75
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 76
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 77
            ] // library marker kkossev.commonLib, line 78
        } // library marker kkossev.commonLib, line 79

        // common capabilities for all device types // library marker kkossev.commonLib, line 81
        capability 'Configuration' // library marker kkossev.commonLib, line 82
        capability 'Refresh' // library marker kkossev.commonLib, line 83
        capability 'HealthCheck' // library marker kkossev.commonLib, line 84
        capability 'PowerSource'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 85

        // common attributes for all device types // library marker kkossev.commonLib, line 87
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 88
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 89
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 90

        // common commands for all device types // library marker kkossev.commonLib, line 92
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 93

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 95
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 96

    preferences { // library marker kkossev.commonLib, line 98
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 99
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 100
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 101

        if (device) { // library marker kkossev.commonLib, line 103
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device...', defaultValue: false // library marker kkossev.commonLib, line 104
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 105
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 106
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 107
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 108
            } // library marker kkossev.commonLib, line 109
        } // library marker kkossev.commonLib, line 110
    } // library marker kkossev.commonLib, line 111
} // library marker kkossev.commonLib, line 112

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 114
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 115
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 116
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 117
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 118
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 119
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 120
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 121
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 122
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 123
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 124

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 126
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 127
] // library marker kkossev.commonLib, line 128
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 129
    defaultValue: 240, options: [2: 'Every 2 Mins', 10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 130
] // library marker kkossev.commonLib, line 131

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 133
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 134
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 135
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 136
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 137
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 138
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 139
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 140
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 141
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 142
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 143
] // library marker kkossev.commonLib, line 144

public boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 146

/** // library marker kkossev.commonLib, line 148
 * Parse Zigbee message // library marker kkossev.commonLib, line 149
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 150
 */ // library marker kkossev.commonLib, line 151
public void parse(final String description) { // library marker kkossev.commonLib, line 152
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 153
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 154
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 155
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 156

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 158
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 159
        if (this.respondsTo('customParseIasMessage')) { customParseIasMessage(description) } // library marker kkossev.commonLib, line 160
        else if (this.respondsTo('standardParseIasMessage')) { standardParseIasMessage(description) } // library marker kkossev.commonLib, line 161
        else if (this.respondsTo('parseIasMessage')) { parseIasMessage(description) } // library marker kkossev.commonLib, line 162
        else { logDebug "ignored IAS zone status (no IAS parser) description: $description" } // library marker kkossev.commonLib, line 163
        return // library marker kkossev.commonLib, line 164
    } // library marker kkossev.commonLib, line 165
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 166
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 167
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 168
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 169
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 170
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 171
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 172
        return // library marker kkossev.commonLib, line 173
    } // library marker kkossev.commonLib, line 174

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 176
        return // library marker kkossev.commonLib, line 177
    } // library marker kkossev.commonLib, line 178
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 179

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 181
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 182

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 184
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 185
        return // library marker kkossev.commonLib, line 186
    } // library marker kkossev.commonLib, line 187
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 188
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 189
        return // library marker kkossev.commonLib, line 190
    } // library marker kkossev.commonLib, line 191
    // // library marker kkossev.commonLib, line 192
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 193
    // // library marker kkossev.commonLib, line 194
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 195
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 196
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 197
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 198
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 199
            } // library marker kkossev.commonLib, line 200
            break // library marker kkossev.commonLib, line 201
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 202
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 203
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 204
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 205
            } // library marker kkossev.commonLib, line 206
            break // library marker kkossev.commonLib, line 207
        default: // library marker kkossev.commonLib, line 208
            if (settings.logEnable) { // library marker kkossev.commonLib, line 209
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 210
            } // library marker kkossev.commonLib, line 211
            break // library marker kkossev.commonLib, line 212
    } // library marker kkossev.commonLib, line 213
} // library marker kkossev.commonLib, line 214

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 216
    0x0000: 'Basic',             0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x0006: 'OnOff',           0x0008: 'LevelControl',  // library marker kkossev.commonLib, line 217
    0x000C: 'AnalogInput',       0x0012: 'MultistateInput',  0x0020: 'PollControl',      0x0102: 'WindowCovering',   0x0201: 'Thermostat',  0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 218
    0x0400: 'Illuminance',       0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 219
    0x0B04: 'ElectricalMeasure', 0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC03: 'FC03',            0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 220
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 221
] // library marker kkossev.commonLib, line 222

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 224
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 225
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 226
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 227
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 228
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 229
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 230
        return false // library marker kkossev.commonLib, line 231
    } // library marker kkossev.commonLib, line 232
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 233
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 234
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 235
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 236
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 237
        return true // library marker kkossev.commonLib, line 238
    } // library marker kkossev.commonLib, line 239
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 240
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 241
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 242
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 243
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 244
        return true // library marker kkossev.commonLib, line 245
    } // library marker kkossev.commonLib, line 246
    if (device?.getDataValue('model') != 'ZigUSB' && descMap.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 247
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 248
    } // library marker kkossev.commonLib, line 249
    return false // library marker kkossev.commonLib, line 250
} // library marker kkossev.commonLib, line 251

private static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 253
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 254
} // library marker kkossev.commonLib, line 255

public boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 257
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 258
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 259
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 260
    } // library marker kkossev.commonLib, line 261
    return false // library marker kkossev.commonLib, line 262
} // library marker kkossev.commonLib, line 263

public boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 265
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 266
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 267
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 268
    } // library marker kkossev.commonLib, line 269
    return false // library marker kkossev.commonLib, line 270
} // library marker kkossev.commonLib, line 271

public boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 273
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 274
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 275
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 276
    } // library marker kkossev.commonLib, line 277
    return false // library marker kkossev.commonLib, line 278
} // library marker kkossev.commonLib, line 279

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 281
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 282
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 283
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 284
] // library marker kkossev.commonLib, line 285

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 287
private void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 288
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 289
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 290
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 291
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 292
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 293
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 294
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 295
    List<String> cmds = [] // library marker kkossev.commonLib, line 296
    switch (clusterId) { // library marker kkossev.commonLib, line 297
        case 0x0005 : // library marker kkossev.commonLib, line 298
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 299
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 300
            // send the active endpoint response // library marker kkossev.commonLib, line 301
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 302
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 303
            break // library marker kkossev.commonLib, line 304
        case 0x0006 : // library marker kkossev.commonLib, line 305
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 306
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 307
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 308
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 309
            break // library marker kkossev.commonLib, line 310
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 311
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 312
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 313
            break // library marker kkossev.commonLib, line 314
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 315
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 316
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 317
            break // library marker kkossev.commonLib, line 318
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 319
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 320
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 321
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 322
            break // library marker kkossev.commonLib, line 323
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 324
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 325
            break // library marker kkossev.commonLib, line 326
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 327
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 328
            if (settings?.logEnable) { log.debug "${clusterInfo}" } // library marker kkossev.commonLib, line 329
            break // library marker kkossev.commonLib, line 330
        default : // library marker kkossev.commonLib, line 331
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 332
            break // library marker kkossev.commonLib, line 333
    } // library marker kkossev.commonLib, line 334
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 335
} // library marker kkossev.commonLib, line 336

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 338
private void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 339
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 340
    switch (commandId) { // library marker kkossev.commonLib, line 341
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 342
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 343
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 344
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 345
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 346
        default: // library marker kkossev.commonLib, line 347
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 348
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 349
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 350
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 351
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 352
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 353
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 354
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 355
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 356
            } // library marker kkossev.commonLib, line 357
            break // library marker kkossev.commonLib, line 358
    } // library marker kkossev.commonLib, line 359
} // library marker kkossev.commonLib, line 360

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 362
private void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 363
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 364
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 365
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 366
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 367
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 368
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 369
    } // library marker kkossev.commonLib, line 370
    else { // library marker kkossev.commonLib, line 371
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 372
    } // library marker kkossev.commonLib, line 373
} // library marker kkossev.commonLib, line 374

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 376
private void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 377
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 378
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 379
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 380
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 381
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 382
    } // library marker kkossev.commonLib, line 383
    else { // library marker kkossev.commonLib, line 384
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 385
    } // library marker kkossev.commonLib, line 386
} // library marker kkossev.commonLib, line 387

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 389
private void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 390
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 391
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 392
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 393
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 394
        state.reportingEnabled = true // library marker kkossev.commonLib, line 395
    } // library marker kkossev.commonLib, line 396
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 397
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 398
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 399
    } else { // library marker kkossev.commonLib, line 400
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 401
    } // library marker kkossev.commonLib, line 402
} // library marker kkossev.commonLib, line 403

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 405
private void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 406
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 407
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 408
    if (status == 0) { // library marker kkossev.commonLib, line 409
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 410
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 411
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 412
        int delta = 0 // library marker kkossev.commonLib, line 413
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 414
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 415
        } // library marker kkossev.commonLib, line 416
        else { // library marker kkossev.commonLib, line 417
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 418
        } // library marker kkossev.commonLib, line 419
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 420
    } // library marker kkossev.commonLib, line 421
    else { // library marker kkossev.commonLib, line 422
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 423
    } // library marker kkossev.commonLib, line 424
} // library marker kkossev.commonLib, line 425

private Boolean executeCustomHandler(String handlerName, Object handlerArgs) { // library marker kkossev.commonLib, line 427
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 428
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 429
        return false // library marker kkossev.commonLib, line 430
    } // library marker kkossev.commonLib, line 431
    // execute the customHandler function // library marker kkossev.commonLib, line 432
    Boolean result = false // library marker kkossev.commonLib, line 433
    try { // library marker kkossev.commonLib, line 434
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 435
    } // library marker kkossev.commonLib, line 436
    catch (e) { // library marker kkossev.commonLib, line 437
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 438
        return false // library marker kkossev.commonLib, line 439
    } // library marker kkossev.commonLib, line 440
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 441
    return result // library marker kkossev.commonLib, line 442
} // library marker kkossev.commonLib, line 443

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 445
private void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 446
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 447
    final String commandId = data[0] // library marker kkossev.commonLib, line 448
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 449
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 450
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 451
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 452
    } else { // library marker kkossev.commonLib, line 453
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 454
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 455
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 456
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 457
        } // library marker kkossev.commonLib, line 458
    } // library marker kkossev.commonLib, line 459
} // library marker kkossev.commonLib, line 460

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 462
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 463
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 464
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 465

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 467
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 468
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 469
] // library marker kkossev.commonLib, line 470

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 472
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 473
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 474
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 475
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 476
] // library marker kkossev.commonLib, line 477

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 479
private BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 480
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 481
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 482
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 483
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 484
    return avg // library marker kkossev.commonLib, line 485
} // library marker kkossev.commonLib, line 486

void handlePingResponse() { // library marker kkossev.commonLib, line 488
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 489
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 490
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 491

    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 493
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 494
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 495
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 496
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 497
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 498
        sendRttEvent() // library marker kkossev.commonLib, line 499
    } // library marker kkossev.commonLib, line 500
    else { // library marker kkossev.commonLib, line 501
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 502
    } // library marker kkossev.commonLib, line 503
    state.states['isPing'] = false // library marker kkossev.commonLib, line 504
} // library marker kkossev.commonLib, line 505

/* // library marker kkossev.commonLib, line 507
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 508
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 509
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 510
*/ // library marker kkossev.commonLib, line 511
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 512

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 514
private void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 515
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 516
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 517
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 518
    boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 519
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 520
        case 0x0000: // library marker kkossev.commonLib, line 521
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 522
            break // library marker kkossev.commonLib, line 523
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 524
            if (isPing) { // library marker kkossev.commonLib, line 525
                handlePingResponse() // library marker kkossev.commonLib, line 526
            } // library marker kkossev.commonLib, line 527
            else { // library marker kkossev.commonLib, line 528
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 529
            } // library marker kkossev.commonLib, line 530
            break // library marker kkossev.commonLib, line 531
        case 0x0004: // library marker kkossev.commonLib, line 532
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 533
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 534
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 535
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 536
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 537
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 538
            } // library marker kkossev.commonLib, line 539
            break // library marker kkossev.commonLib, line 540
        case 0x0005: // library marker kkossev.commonLib, line 541
            if (isPing) { // library marker kkossev.commonLib, line 542
                handlePingResponse() // library marker kkossev.commonLib, line 543
            } // library marker kkossev.commonLib, line 544
            else { // library marker kkossev.commonLib, line 545
                logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 546
                // received device model Remote Control N2 // library marker kkossev.commonLib, line 547
                String model = device.getDataValue('model') // library marker kkossev.commonLib, line 548
                if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 549
                    logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 550
                    device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 551
                } // library marker kkossev.commonLib, line 552
            } // library marker kkossev.commonLib, line 553
            break // library marker kkossev.commonLib, line 554
        case 0x0007: // library marker kkossev.commonLib, line 555
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 556
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 557
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 558
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 559
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 560
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 561
            } // library marker kkossev.commonLib, line 562
            break // library marker kkossev.commonLib, line 563
        case 0xFFDF: // library marker kkossev.commonLib, line 564
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 565
            break // library marker kkossev.commonLib, line 566
        case 0xFFE2: // library marker kkossev.commonLib, line 567
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 568
            break // library marker kkossev.commonLib, line 569
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 570
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 571
            break // library marker kkossev.commonLib, line 572
        case 0xFFFE: // library marker kkossev.commonLib, line 573
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 574
            break // library marker kkossev.commonLib, line 575
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 576
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 577
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 578
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 579
            break // library marker kkossev.commonLib, line 580
        default: // library marker kkossev.commonLib, line 581
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 582
            break // library marker kkossev.commonLib, line 583
    } // library marker kkossev.commonLib, line 584
} // library marker kkossev.commonLib, line 585

private void standardParsePollControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 587
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 588
        case 0x0000: logDebug "PollControl cluster: CheckInInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 589
        case 0x0001: logDebug "PollControl cluster: LongPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 590
        case 0x0002: logDebug "PollControl cluster: ShortPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 591
        case 0x0003: logDebug "PollControl cluster: FastPollTimeout = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 592
        case 0x0004: logDebug "PollControl cluster: CheckInIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 593
        case 0x0005: logDebug "PollControl cluster: LongPollIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 594
        case 0x0006: logDebug "PollControl cluster: FastPollTimeoutMax = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 595
        default: logWarn "zigbee received unknown PollControl cluster attribute 0x${descMap.attrId} (value ${descMap.value})" ; break // library marker kkossev.commonLib, line 596
    } // library marker kkossev.commonLib, line 597
} // library marker kkossev.commonLib, line 598

public void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 600
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 601
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 602

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 604
    Map descMap = [:] // library marker kkossev.commonLib, line 605
    try { // library marker kkossev.commonLib, line 606
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 607
    } // library marker kkossev.commonLib, line 608
    catch (e1) { // library marker kkossev.commonLib, line 609
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 610
        // try alternative custom parsing // library marker kkossev.commonLib, line 611
        descMap = [:] // library marker kkossev.commonLib, line 612
        try { // library marker kkossev.commonLib, line 613
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 614
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 615
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 616
            } // library marker kkossev.commonLib, line 617
        } // library marker kkossev.commonLib, line 618
        catch (e2) { // library marker kkossev.commonLib, line 619
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 620
            return [:] // library marker kkossev.commonLib, line 621
        } // library marker kkossev.commonLib, line 622
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 623
    } // library marker kkossev.commonLib, line 624
    return descMap // library marker kkossev.commonLib, line 625
} // library marker kkossev.commonLib, line 626

private boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 628
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 629
        return false // library marker kkossev.commonLib, line 630
    } // library marker kkossev.commonLib, line 631
    // try to parse ... // library marker kkossev.commonLib, line 632
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 633
    Map descMap = [:] // library marker kkossev.commonLib, line 634
    try { // library marker kkossev.commonLib, line 635
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 636
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 637
    } // library marker kkossev.commonLib, line 638
    catch (e) { // library marker kkossev.commonLib, line 639
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 640
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 641
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 642
        return true // library marker kkossev.commonLib, line 643
    } // library marker kkossev.commonLib, line 644

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 646
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 647
    } // library marker kkossev.commonLib, line 648
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 649
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 650
    } // library marker kkossev.commonLib, line 651
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 652
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 653
    } // library marker kkossev.commonLib, line 654
    else { // library marker kkossev.commonLib, line 655
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 656
        return false // library marker kkossev.commonLib, line 657
    } // library marker kkossev.commonLib, line 658
    return true    // processed // library marker kkossev.commonLib, line 659
} // library marker kkossev.commonLib, line 660

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 662
private boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 663
  /* // library marker kkossev.commonLib, line 664
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 665
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 666
        return true // library marker kkossev.commonLib, line 667
    } // library marker kkossev.commonLib, line 668
*/ // library marker kkossev.commonLib, line 669
    Map descMap = [:] // library marker kkossev.commonLib, line 670
    try { // library marker kkossev.commonLib, line 671
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 672
    } // library marker kkossev.commonLib, line 673
    catch (e1) { // library marker kkossev.commonLib, line 674
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 675
        // try alternative custom parsing // library marker kkossev.commonLib, line 676
        descMap = [:] // library marker kkossev.commonLib, line 677
        try { // library marker kkossev.commonLib, line 678
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 679
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 680
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 681
            } // library marker kkossev.commonLib, line 682
        } // library marker kkossev.commonLib, line 683
        catch (e2) { // library marker kkossev.commonLib, line 684
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 685
            return true // library marker kkossev.commonLib, line 686
        } // library marker kkossev.commonLib, line 687
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 688
    } // library marker kkossev.commonLib, line 689
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 690
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 691
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 692
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 693
        return false // library marker kkossev.commonLib, line 694
    } // library marker kkossev.commonLib, line 695
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 696
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 697
    // attribute report received // library marker kkossev.commonLib, line 698
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 699
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 700
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 701
    } // library marker kkossev.commonLib, line 702
    attrData.each { // library marker kkossev.commonLib, line 703
        if (it.status == '86') { // library marker kkossev.commonLib, line 704
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 705
        // TODO - skip parsing? // library marker kkossev.commonLib, line 706
        } // library marker kkossev.commonLib, line 707
        switch (it.cluster) { // library marker kkossev.commonLib, line 708
            case '0000' : // library marker kkossev.commonLib, line 709
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 710
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 711
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 712
                } // library marker kkossev.commonLib, line 713
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 714
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 715
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 716
                } // library marker kkossev.commonLib, line 717
                else { // library marker kkossev.commonLib, line 718
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 719
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 720
                } // library marker kkossev.commonLib, line 721
                break // library marker kkossev.commonLib, line 722
            default : // library marker kkossev.commonLib, line 723
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 724
                break // library marker kkossev.commonLib, line 725
        } // switch // library marker kkossev.commonLib, line 726
    } // for each attribute // library marker kkossev.commonLib, line 727
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 728
} // library marker kkossev.commonLib, line 729

public String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 731
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 732
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 733
} // library marker kkossev.commonLib, line 734

public String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 736
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 737
} // library marker kkossev.commonLib, line 738

/* // library marker kkossev.commonLib, line 740
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 741
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 742
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 743
*/ // library marker kkossev.commonLib, line 744
private static int getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 745
private static int getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 746
private static int getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 747

// Tuya Commands // library marker kkossev.commonLib, line 749
private static int getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 750
private static int getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 751
private static int getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 752
private static int getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 753
private static int getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 754

// tuya DP type // library marker kkossev.commonLib, line 756
private static String getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 757
private static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 758
private static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 759
private static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 760
private static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 761
private static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 762

private void syncTuyaDateTime() { // library marker kkossev.commonLib, line 764
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 765
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 766
    long offset = 0 // library marker kkossev.commonLib, line 767
    int offsetHours = 0 // library marker kkossev.commonLib, line 768
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 769
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 770
    try { // library marker kkossev.commonLib, line 771
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 772
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 773
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 774
    } catch (e) { // library marker kkossev.commonLib, line 775
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 776
    } // library marker kkossev.commonLib, line 777
    // // library marker kkossev.commonLib, line 778
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 779
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 780
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 781
} // library marker kkossev.commonLib, line 782

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 784
public void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 785
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 786
        syncTuyaDateTime() // library marker kkossev.commonLib, line 787
    } // library marker kkossev.commonLib, line 788
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 789
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 790
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 791
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 792
        if (status != '00') { // library marker kkossev.commonLib, line 793
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 794
        } // library marker kkossev.commonLib, line 795
    } // library marker kkossev.commonLib, line 796
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 797
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 798
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 799
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 800
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 801
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 802
            return // library marker kkossev.commonLib, line 803
        } // library marker kkossev.commonLib, line 804
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 805
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 806
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 807
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 808
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 809
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 810
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 811
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 812
            } // library marker kkossev.commonLib, line 813
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 814
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 815
        } // library marker kkossev.commonLib, line 816
    } // library marker kkossev.commonLib, line 817
    else { // library marker kkossev.commonLib, line 818
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 819
    } // library marker kkossev.commonLib, line 820
} // library marker kkossev.commonLib, line 821

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 823
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 824
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 825
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 826
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 827
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 828
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 829
        } // library marker kkossev.commonLib, line 830
    } // library marker kkossev.commonLib, line 831
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 832
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 833
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 834
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 835
        } // library marker kkossev.commonLib, line 836
    } // library marker kkossev.commonLib, line 837
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 838
} // library marker kkossev.commonLib, line 839

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 841
    int retValue = 0 // library marker kkossev.commonLib, line 842
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 843
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 844
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 845
        int power = 1 // library marker kkossev.commonLib, line 846
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 847
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 848
            power = power * 256 // library marker kkossev.commonLib, line 849
        } // library marker kkossev.commonLib, line 850
    } // library marker kkossev.commonLib, line 851
    return retValue // library marker kkossev.commonLib, line 852
} // library marker kkossev.commonLib, line 853

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 855

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 857
    List<String> cmds = [] // library marker kkossev.commonLib, line 858
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 859
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 860
    //int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 861
    int tuyaCmd // library marker kkossev.commonLib, line 862
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified!\ // library marker kkossev.commonLib, line 863
    if (this.respondsTo('getDEVICE') && DEVICE?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 864
        tuyaCmd = DEVICE?.device?.tuyaCmd // library marker kkossev.commonLib, line 865
    } // library marker kkossev.commonLib, line 866
    else { // library marker kkossev.commonLib, line 867
        tuyaCmd = /*isFingerbot() ? 0x04 : */ tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 868
    } // library marker kkossev.commonLib, line 869
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 870
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 871
    return cmds // library marker kkossev.commonLib, line 872
} // library marker kkossev.commonLib, line 873

private String getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 875

public void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 877
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 878
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 879
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 880
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 881
} // library marker kkossev.commonLib, line 882


public List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 885
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 886
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 887
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 888
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 889
} // library marker kkossev.commonLib, line 890

List<String> queryAllTuyaDP() { // library marker kkossev.commonLib, line 892
    logTrace 'queryAllTuyaDP()' // library marker kkossev.commonLib, line 893
    List<String> cmds = zigbee.command(0xEF00, 0x03) // library marker kkossev.commonLib, line 894
    return cmds // library marker kkossev.commonLib, line 895
} // library marker kkossev.commonLib, line 896

public void aqaraBlackMagic() { // library marker kkossev.commonLib, line 898
    List<String> cmds = [] // library marker kkossev.commonLib, line 899
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 900
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 901
    } // library marker kkossev.commonLib, line 902
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 903
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 904
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 905
        return // library marker kkossev.commonLib, line 906
    } // library marker kkossev.commonLib, line 907
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 908
} // library marker kkossev.commonLib, line 909

// Invoked from configure() // library marker kkossev.commonLib, line 911
public List<String> initializeDevice() { // library marker kkossev.commonLib, line 912
    List<String> cmds = [] // library marker kkossev.commonLib, line 913
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 914
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 915
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 916
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 917
    } // library marker kkossev.commonLib, line 918
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 919
    return cmds // library marker kkossev.commonLib, line 920
} // library marker kkossev.commonLib, line 921

// Invoked from configure() // library marker kkossev.commonLib, line 923
public List<String> configureDevice() { // library marker kkossev.commonLib, line 924
    List<String> cmds = [] // library marker kkossev.commonLib, line 925
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 926
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 927
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 928
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 929
    } // library marker kkossev.commonLib, line 930
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 931
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 932
    return cmds // library marker kkossev.commonLib, line 933
} // library marker kkossev.commonLib, line 934

/* // library marker kkossev.commonLib, line 936
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 937
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 938
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 939
*/ // library marker kkossev.commonLib, line 940

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 942
    List<String> cmds = [] // library marker kkossev.commonLib, line 943
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 944
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 945
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 946
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 947
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 948
            } // library marker kkossev.commonLib, line 949
        } // library marker kkossev.commonLib, line 950
    } // library marker kkossev.commonLib, line 951
    return cmds // library marker kkossev.commonLib, line 952
} // library marker kkossev.commonLib, line 953

void refresh() { // library marker kkossev.commonLib, line 955
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 956
    checkDriverVersion(state) // library marker kkossev.commonLib, line 957
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 958
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 959
        customCmds = customRefresh() // library marker kkossev.commonLib, line 960
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 961
    } // library marker kkossev.commonLib, line 962
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 963
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 964
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 965
    } // library marker kkossev.commonLib, line 966
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 967
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 968
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 969
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 970
    } // library marker kkossev.commonLib, line 971
    else { // library marker kkossev.commonLib, line 972
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 973
    } // library marker kkossev.commonLib, line 974
} // library marker kkossev.commonLib, line 975

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, 'clearRefreshRequest', [overwrite: true]) } // library marker kkossev.commonLib, line 977
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 978
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 979

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 981
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 982
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 983
        sendEvent(name: 'Status', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 984
    } // library marker kkossev.commonLib, line 985
    else { // library marker kkossev.commonLib, line 986
        logInfo "${info}" // library marker kkossev.commonLib, line 987
        sendEvent(name: 'Status', value: info, type: 'digital') // library marker kkossev.commonLib, line 988
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 989
    } // library marker kkossev.commonLib, line 990
} // library marker kkossev.commonLib, line 991

public void ping() { // library marker kkossev.commonLib, line 993
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 994
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 995
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 996
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID // library marker kkossev.commonLib, line 997
    if (isVirtual()) { runInMillis(10, 'virtualPong') } // library marker kkossev.commonLib, line 998
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) } // library marker kkossev.commonLib, line 999
    logDebug 'ping...' // library marker kkossev.commonLib, line 1000
} // library marker kkossev.commonLib, line 1001

private void virtualPong() { // library marker kkossev.commonLib, line 1003
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1004
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1005
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1006
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1007
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1008
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1009
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1010
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1011
        sendRttEvent() // library marker kkossev.commonLib, line 1012
    } // library marker kkossev.commonLib, line 1013
    else { // library marker kkossev.commonLib, line 1014
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1015
    } // library marker kkossev.commonLib, line 1016
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1017
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1018
} // library marker kkossev.commonLib, line 1019

public void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1021
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1022
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1023
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1024
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1025
    if (value == null) { // library marker kkossev.commonLib, line 1026
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1027
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1028
    } // library marker kkossev.commonLib, line 1029
    else { // library marker kkossev.commonLib, line 1030
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1031
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1032
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1033
    } // library marker kkossev.commonLib, line 1034
} // library marker kkossev.commonLib, line 1035

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1037
    if (cluster != null) { // library marker kkossev.commonLib, line 1038
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1039
    } // library marker kkossev.commonLib, line 1040
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1041
    return 'NULL' // library marker kkossev.commonLib, line 1042
} // library marker kkossev.commonLib, line 1043

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1045
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1046
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1047
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1048
} // library marker kkossev.commonLib, line 1049

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1051
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1052
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1053
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1054
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1055
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1056
    } // library marker kkossev.commonLib, line 1057
} // library marker kkossev.commonLib, line 1058

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1060
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1061
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1062
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1063
} // library marker kkossev.commonLib, line 1064

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1066
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1067
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1068
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1069
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1070
    } // library marker kkossev.commonLib, line 1071
    else { // library marker kkossev.commonLib, line 1072
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1073
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1074
    } // library marker kkossev.commonLib, line 1075
} // library marker kkossev.commonLib, line 1076

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1078
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1079
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1080
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1081
} // library marker kkossev.commonLib, line 1082

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1084
private void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1085
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1086
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1087
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1088
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1089
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1090
    } // library marker kkossev.commonLib, line 1091
} // library marker kkossev.commonLib, line 1092

private void deviceHealthCheck() { // library marker kkossev.commonLib, line 1094
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1095
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1096
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1097
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1098
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1099
            logWarn 'not present!' // library marker kkossev.commonLib, line 1100
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1101
        } // library marker kkossev.commonLib, line 1102
    } // library marker kkossev.commonLib, line 1103
    else { // library marker kkossev.commonLib, line 1104
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})" // library marker kkossev.commonLib, line 1105
    } // library marker kkossev.commonLib, line 1106
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1107
} // library marker kkossev.commonLib, line 1108

private void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1110
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1111
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1112
    if (value == 'online') { // library marker kkossev.commonLib, line 1113
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1114
    } // library marker kkossev.commonLib, line 1115
    else { // library marker kkossev.commonLib, line 1116
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1117
    } // library marker kkossev.commonLib, line 1118
} // library marker kkossev.commonLib, line 1119

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1121
void updated() { // library marker kkossev.commonLib, line 1122
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1123
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1124
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1125
    unschedule() // library marker kkossev.commonLib, line 1126

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1128
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1129
        runIn(86400, 'logsOff') // library marker kkossev.commonLib, line 1130
    } // library marker kkossev.commonLib, line 1131
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1132
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1133
        runIn(1800, 'traceOff') // library marker kkossev.commonLib, line 1134
    } // library marker kkossev.commonLib, line 1135

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1137
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1138
        // schedule the periodic timer // library marker kkossev.commonLib, line 1139
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1140
        if (interval > 0) { // library marker kkossev.commonLib, line 1141
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1142
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1143
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1144
        } // library marker kkossev.commonLib, line 1145
    } // library marker kkossev.commonLib, line 1146
    else { // library marker kkossev.commonLib, line 1147
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1148
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1149
    } // library marker kkossev.commonLib, line 1150
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1151
        customUpdated() // library marker kkossev.commonLib, line 1152
    } // library marker kkossev.commonLib, line 1153

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1155
} // library marker kkossev.commonLib, line 1156

private void logsOff() { // library marker kkossev.commonLib, line 1158
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1159
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1160
} // library marker kkossev.commonLib, line 1161
private void traceOff() { // library marker kkossev.commonLib, line 1162
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1163
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1164
} // library marker kkossev.commonLib, line 1165

public void configure(String command) { // library marker kkossev.commonLib, line 1167
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1168
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1169
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1170
        return // library marker kkossev.commonLib, line 1171
    } // library marker kkossev.commonLib, line 1172
    // // library marker kkossev.commonLib, line 1173
    String func // library marker kkossev.commonLib, line 1174
    try { // library marker kkossev.commonLib, line 1175
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1176
        "$func"() // library marker kkossev.commonLib, line 1177
    } // library marker kkossev.commonLib, line 1178
    catch (e) { // library marker kkossev.commonLib, line 1179
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1180
        return // library marker kkossev.commonLib, line 1181
    } // library marker kkossev.commonLib, line 1182
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1183
} // library marker kkossev.commonLib, line 1184

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1186
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1187
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1188
} // library marker kkossev.commonLib, line 1189

public void loadAllDefaults() { // library marker kkossev.commonLib, line 1191
    logDebug 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1192
    deleteAllSettings() // library marker kkossev.commonLib, line 1193
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1194
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1195
    deleteAllStates() // library marker kkossev.commonLib, line 1196
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1197

    initialize() // library marker kkossev.commonLib, line 1199
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1200
    updated() // library marker kkossev.commonLib, line 1201
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1202
} // library marker kkossev.commonLib, line 1203

private void configureNow() { // library marker kkossev.commonLib, line 1205
    configure() // library marker kkossev.commonLib, line 1206
} // library marker kkossev.commonLib, line 1207

/** // library marker kkossev.commonLib, line 1209
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1210
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1211
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1212
 */ // library marker kkossev.commonLib, line 1213
void configure() { // library marker kkossev.commonLib, line 1214
    List<String> cmds = [] // library marker kkossev.commonLib, line 1215
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1216
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1217
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1218
    if (isTuya()) { // library marker kkossev.commonLib, line 1219
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1220
    } // library marker kkossev.commonLib, line 1221
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1222
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1223
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1224
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1225
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1226
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1227
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1228
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1229
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1230
    } // library marker kkossev.commonLib, line 1231
    else { // library marker kkossev.commonLib, line 1232
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1233
    } // library marker kkossev.commonLib, line 1234
} // library marker kkossev.commonLib, line 1235

 // Invoked when the device is installed with this driver automatically selected. // library marker kkossev.commonLib, line 1237
void installed() { // library marker kkossev.commonLib, line 1238
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1239
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1240
    // populate some default values for attributes // library marker kkossev.commonLib, line 1241
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1242
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1243
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1244
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1245
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1246
} // library marker kkossev.commonLib, line 1247

private void queryPowerSource() { // library marker kkossev.commonLib, line 1249
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1250
} // library marker kkossev.commonLib, line 1251

 // Invoked from 'LoadAllDefaults' // library marker kkossev.commonLib, line 1253
private void initialize() { // library marker kkossev.commonLib, line 1254
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1255
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1256
    if (device.getDataValue('powerSource') == null) { // library marker kkossev.commonLib, line 1257
        logInfo "initializing device powerSource 'unknown'" // library marker kkossev.commonLib, line 1258
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1259
    } // library marker kkossev.commonLib, line 1260
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1261
    updateTuyaVersion() // library marker kkossev.commonLib, line 1262
    updateAqaraVersion() // library marker kkossev.commonLib, line 1263
} // library marker kkossev.commonLib, line 1264

/* // library marker kkossev.commonLib, line 1266
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1267
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1268
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1269
*/ // library marker kkossev.commonLib, line 1270

static Integer safeToInt(Object val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1272
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1273
} // library marker kkossev.commonLib, line 1274

static Double safeToDouble(Object val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1276
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1277
} // library marker kkossev.commonLib, line 1278

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1280
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1281
} // library marker kkossev.commonLib, line 1282

public void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1284
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1285
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1286
        return // library marker kkossev.commonLib, line 1287
    } // library marker kkossev.commonLib, line 1288
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1289
    cmd.each { // library marker kkossev.commonLib, line 1290
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1291
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1292
            return // library marker kkossev.commonLib, line 1293
        } // library marker kkossev.commonLib, line 1294
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1295
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1296
    } // library marker kkossev.commonLib, line 1297
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1298
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1299
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1300
} // library marker kkossev.commonLib, line 1301

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1303

private String getDeviceInfo() { // library marker kkossev.commonLib, line 1305
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1306
} // library marker kkossev.commonLib, line 1307

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1309
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1310
} // library marker kkossev.commonLib, line 1311

@CompileStatic // library marker kkossev.commonLib, line 1313
public void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1314
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1315
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1316
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1317
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1318
        initializeVars(false) // library marker kkossev.commonLib, line 1319
        updateTuyaVersion() // library marker kkossev.commonLib, line 1320
        updateAqaraVersion() // library marker kkossev.commonLib, line 1321
    } // library marker kkossev.commonLib, line 1322
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1323
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1324
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1325
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1326
} // library marker kkossev.commonLib, line 1327

// credits @thebearmay // library marker kkossev.commonLib, line 1329
String getModel() { // library marker kkossev.commonLib, line 1330
    try { // library marker kkossev.commonLib, line 1331
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1332
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1333
    } catch (ignore) { // library marker kkossev.commonLib, line 1334
        try { // library marker kkossev.commonLib, line 1335
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1336
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1337
                return model // library marker kkossev.commonLib, line 1338
            } // library marker kkossev.commonLib, line 1339
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1340
            return '' // library marker kkossev.commonLib, line 1341
        } // library marker kkossev.commonLib, line 1342
    } // library marker kkossev.commonLib, line 1343
} // library marker kkossev.commonLib, line 1344

// credits @thebearmay // library marker kkossev.commonLib, line 1346
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1347
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1348
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1349
    String revision = tokens.last() // library marker kkossev.commonLib, line 1350
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1351
} // library marker kkossev.commonLib, line 1352

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1354
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1355
    unschedule() // library marker kkossev.commonLib, line 1356
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1357
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1358

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1360
} // library marker kkossev.commonLib, line 1361

void resetStatistics() { // library marker kkossev.commonLib, line 1363
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1364
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1365
} // library marker kkossev.commonLib, line 1366

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1368
void resetStats() { // library marker kkossev.commonLib, line 1369
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1370
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1371
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1372
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1373
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1374
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1375
} // library marker kkossev.commonLib, line 1376

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1378
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1379
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1380
        state.clear() // library marker kkossev.commonLib, line 1381
        unschedule() // library marker kkossev.commonLib, line 1382
        resetStats() // library marker kkossev.commonLib, line 1383
        if (deviceProfilesV3 != null && this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1384
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1385
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1386
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1387
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1388
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1389
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1390
    } // library marker kkossev.commonLib, line 1391

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1393
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1394
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1395
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1396
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1397

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1399
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1400
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1401
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1402
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1403
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1404
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1405

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1407

    // common libraries initialization // library marker kkossev.commonLib, line 1409
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024 // library marker kkossev.commonLib, line 1410
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024 // library marker kkossev.commonLib, line 1411
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1412
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1413
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1414
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1415
    // // library marker kkossev.commonLib, line 1416
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers! // library marker kkossev.commonLib, line 1417
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024 // library marker kkossev.commonLib, line 1418
    // // library marker kkossev.commonLib, line 1419
    // custom device driver specific initialization should be at the end // library marker kkossev.commonLib, line 1420
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1421
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1422
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1423

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1425
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1426
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1427
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1428
    if ( ep  != null) { // library marker kkossev.commonLib, line 1429
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1430
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1431
    } // library marker kkossev.commonLib, line 1432
    else { // library marker kkossev.commonLib, line 1433
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1434
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1435
    } // library marker kkossev.commonLib, line 1436
} // library marker kkossev.commonLib, line 1437

// not used!? // library marker kkossev.commonLib, line 1439
void setDestinationEP() { // library marker kkossev.commonLib, line 1440
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1441
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1442
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1443
} // library marker kkossev.commonLib, line 1444

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1446
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1447
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1448
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1449

// _DEBUG mode only // library marker kkossev.commonLib, line 1451
void getAllProperties() { // library marker kkossev.commonLib, line 1452
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1453
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1454
} // library marker kkossev.commonLib, line 1455

// delete all Preferences // library marker kkossev.commonLib, line 1457
void deleteAllSettings() { // library marker kkossev.commonLib, line 1458
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1459
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1460
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1461
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1462
} // library marker kkossev.commonLib, line 1463

// delete all attributes // library marker kkossev.commonLib, line 1465
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1466
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1467
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1468
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1469
} // library marker kkossev.commonLib, line 1470

// delete all State Variables // library marker kkossev.commonLib, line 1472
void deleteAllStates() { // library marker kkossev.commonLib, line 1473
    String stateDeleted = '' // library marker kkossev.commonLib, line 1474
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1475
    state.clear() // library marker kkossev.commonLib, line 1476
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1477
} // library marker kkossev.commonLib, line 1478

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1480
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1481
} // library marker kkossev.commonLib, line 1482

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1484
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1485
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1486
} // library marker kkossev.commonLib, line 1487

void testParse(String par) { // library marker kkossev.commonLib, line 1489
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1490
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1491
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1492
    parse(par) // library marker kkossev.commonLib, line 1493
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1494
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1495
} // library marker kkossev.commonLib, line 1496

Object testJob() { // library marker kkossev.commonLib, line 1498
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1499
} // library marker kkossev.commonLib, line 1500

/** // library marker kkossev.commonLib, line 1502
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1503
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1504
 */ // library marker kkossev.commonLib, line 1505
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1506
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1507
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1508
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1509
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1510
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1511
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1512
    String cron // library marker kkossev.commonLib, line 1513
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1514
    else { // library marker kkossev.commonLib, line 1515
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1516
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1517
    } // library marker kkossev.commonLib, line 1518
    return cron // library marker kkossev.commonLib, line 1519
} // library marker kkossev.commonLib, line 1520

// credits @thebearmay // library marker kkossev.commonLib, line 1522
String formatUptime() { // library marker kkossev.commonLib, line 1523
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1524
} // library marker kkossev.commonLib, line 1525

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1527
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1528
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1529
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1530
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1531
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1532
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1533
} // library marker kkossev.commonLib, line 1534

boolean isTuya() { // library marker kkossev.commonLib, line 1536
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1537
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1538
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1539
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1540
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1541
} // library marker kkossev.commonLib, line 1542

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1544
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1545
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1546
    if (application != null) { // library marker kkossev.commonLib, line 1547
        Integer ver // library marker kkossev.commonLib, line 1548
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1549
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1550
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1551
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1552
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1553
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1554
        } // library marker kkossev.commonLib, line 1555
    } // library marker kkossev.commonLib, line 1556
} // library marker kkossev.commonLib, line 1557

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1559

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1561
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1562
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1563
    if (application != null) { // library marker kkossev.commonLib, line 1564
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1565
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1566
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1567
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1568
        } // library marker kkossev.commonLib, line 1569
    } // library marker kkossev.commonLib, line 1570
} // library marker kkossev.commonLib, line 1571

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1573
    try { // library marker kkossev.commonLib, line 1574
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1575
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1576
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1577
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1578
    } catch (e) { // library marker kkossev.commonLib, line 1579
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1580
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1581
    } // library marker kkossev.commonLib, line 1582
} // library marker kkossev.commonLib, line 1583

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1585
    try { // library marker kkossev.commonLib, line 1586
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1587
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1588
        return date.getTime() // library marker kkossev.commonLib, line 1589
    } catch (e) { // library marker kkossev.commonLib, line 1590
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1591
        return now() // library marker kkossev.commonLib, line 1592
    } // library marker kkossev.commonLib, line 1593
} // library marker kkossev.commonLib, line 1594

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1596
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1597
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1598
    int seconds = time % 60 // library marker kkossev.commonLib, line 1599
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1600
} // library marker kkossev.commonLib, line 1601

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (172) kkossev.temperatureLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.temperatureLib, line 1
library( // library marker kkossev.temperatureLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Temperature Library', name: 'temperatureLib', namespace: 'kkossev', // library marker kkossev.temperatureLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/temperatureLib.groovy', documentationLink: '', // library marker kkossev.temperatureLib, line 4
    version: '3.2.3' // library marker kkossev.temperatureLib, line 5
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
 * // library marker kkossev.temperatureLib, line 18
 *                                   TODO: // library marker kkossev.temperatureLib, line 19
 *                                   TODO: add temperatureOffset // library marker kkossev.temperatureLib, line 20
 *                                   TODO: unschedule('sendDelayedTempEvent') only if needed (add boolean flag to sendDelayedTempEvent()) // library marker kkossev.temperatureLib, line 21
 *                                   TODO: check for negative temperature values in standardParseTemperatureCluster() // library marker kkossev.temperatureLib, line 22
*/ // library marker kkossev.temperatureLib, line 23

static String temperatureLibVersion()   { '3.2.3' } // library marker kkossev.temperatureLib, line 25
static String temperatureLibStamp() { '2024/07/18 3:08 PM' } // library marker kkossev.temperatureLib, line 26

metadata { // library marker kkossev.temperatureLib, line 28
    capability 'TemperatureMeasurement' // library marker kkossev.temperatureLib, line 29
    // no commands // library marker kkossev.temperatureLib, line 30
    preferences { // library marker kkossev.temperatureLib, line 31
        if (device && advancedOptions == true) { // library marker kkossev.temperatureLib, line 32
            if ('ReportingConfiguration' in DEVICE?.capabilities) { // library marker kkossev.temperatureLib, line 33
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: 'Minimum reporting interval, seconds <i>(1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 34
                if (!(deviceType in ['mmWaveSensor', 'Thermostat', 'TRV'])) { // library marker kkossev.temperatureLib, line 35
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: 'Maximum reporting interval, seconds <i>(120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.temperatureLib, line 36
                } // library marker kkossev.temperatureLib, line 37
            } // library marker kkossev.temperatureLib, line 38
        } // library marker kkossev.temperatureLib, line 39
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

// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NestedBlockDepth, NoDouble, NoFloat, NoWildcardImports, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.deviceProfileLib, line 1
library( // library marker kkossev.deviceProfileLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLib', namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy', documentationLink: '', // library marker kkossev.deviceProfileLib, line 4
    version: '3.3.4' // library marker kkossev.deviceProfileLib, line 5
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
 * ver. 3.3.4  2024-09-28 kkossev  - (dev.branch) fixed exceptions in resetPreferencesToDefaults() and initEventsDeviceProfile() // library marker kkossev.deviceProfileLib, line 34
 * // library marker kkossev.deviceProfileLib, line 35
 *                                   TODO - remove the 2-in-1 patch ! // library marker kkossev.deviceProfileLib, line 36
 *                                   TODO - add defaults for profileId:'0104', endpointId:'01', inClusters, outClusters, in the deviceProfilesV3 map // library marker kkossev.deviceProfileLib, line 37
 *                                   TODO - add updateStateUnknownDPs (from the 4-in-1 driver) // library marker kkossev.deviceProfileLib, line 38
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 39
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 40
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off) // library marker kkossev.deviceProfileLib, line 41
 *                                   TODO: allow NULL parameters default values in the device profiles // library marker kkossev.deviceProfileLib, line 42
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 43
 * // library marker kkossev.deviceProfileLib, line 44
*/ // library marker kkossev.deviceProfileLib, line 45

static String deviceProfileLibVersion()   { '3.3.4' } // library marker kkossev.deviceProfileLib, line 47
static String deviceProfileLibStamp() { '2024/09/28 6:33 PM' } // library marker kkossev.deviceProfileLib, line 48
import groovy.json.* // library marker kkossev.deviceProfileLib, line 49
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 50
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 51
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 52
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 53

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 55

metadata { // library marker kkossev.deviceProfileLib, line 57
    // no capabilities // library marker kkossev.deviceProfileLib, line 58
    // no attributes // library marker kkossev.deviceProfileLib, line 59
    /* // library marker kkossev.deviceProfileLib, line 60
    // copy the following commands to the main driver, if needed // library marker kkossev.deviceProfileLib, line 61
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 62
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 63
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 64
    ] // library marker kkossev.deviceProfileLib, line 65
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 66
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 67
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 68
    ] // library marker kkossev.deviceProfileLib, line 69
    */ // library marker kkossev.deviceProfileLib, line 70
    preferences { // library marker kkossev.deviceProfileLib, line 71
        if (device) { // library marker kkossev.deviceProfileLib, line 72
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 73
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 74
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 75
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 76
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 77
                        input inputMap // library marker kkossev.deviceProfileLib, line 78
                    } // library marker kkossev.deviceProfileLib, line 79
                } // library marker kkossev.deviceProfileLib, line 80
            } // library marker kkossev.deviceProfileLib, line 81
            //if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 82
                input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 83
            //} // library marker kkossev.deviceProfileLib, line 84
        } // library marker kkossev.deviceProfileLib, line 85
    } // library marker kkossev.deviceProfileLib, line 86
} // library marker kkossev.deviceProfileLib, line 87

private boolean is2in1() { return getDeviceProfile().contains('TS0601_2IN1') }    // patch removed 05/29/2024 // library marker kkossev.deviceProfileLib, line 89

public String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 91
public Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 92
public Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 93
//List<String> getDeviceProfilesMap()   { deviceProfilesV3 != null ? deviceProfilesV3.values().description as List<String> : deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 94

public List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLib, line 96
    if (deviceProfilesV3 == null) { // library marker kkossev.deviceProfileLib, line 97
        if (deviceProfilesV2 == null) { return [] } // library marker kkossev.deviceProfileLib, line 98
        return deviceProfilesV2.values().description as List<String> // library marker kkossev.deviceProfileLib, line 99
    } // library marker kkossev.deviceProfileLib, line 100
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLib, line 101
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 102
        if ((profileMap.device?.isDepricated ?: false) != true) { // library marker kkossev.deviceProfileLib, line 103
            activeProfiles.add(profileMap.description ?: '---') // library marker kkossev.deviceProfileLib, line 104
        } // library marker kkossev.deviceProfileLib, line 105
    } // library marker kkossev.deviceProfileLib, line 106
    return activeProfiles // library marker kkossev.deviceProfileLib, line 107
} // library marker kkossev.deviceProfileLib, line 108

// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 110

/** // library marker kkossev.deviceProfileLib, line 112
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 113
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 114
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 115
 */ // library marker kkossev.deviceProfileLib, line 116
String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 117
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 118
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 119
    else { return null } // library marker kkossev.deviceProfileLib, line 120
} // library marker kkossev.deviceProfileLib, line 121

/** // library marker kkossev.deviceProfileLib, line 123
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 124
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 125
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 126
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 127
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 128
 */ // library marker kkossev.deviceProfileLib, line 129
Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 130
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 131
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 132
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 133
    def preference // library marker kkossev.deviceProfileLib, line 134
    try { // library marker kkossev.deviceProfileLib, line 135
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 136
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 137
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 138
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 139
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 140
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 141
        } // library marker kkossev.deviceProfileLib, line 142
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 143
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 144
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 145
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 146
        } // library marker kkossev.deviceProfileLib, line 147
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 148
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 149
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 150
        } // library marker kkossev.deviceProfileLib, line 151
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 152
    } catch (e) { // library marker kkossev.deviceProfileLib, line 153
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 154
        return [:] // library marker kkossev.deviceProfileLib, line 155
    } // library marker kkossev.deviceProfileLib, line 156
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 157
    return foundMap // library marker kkossev.deviceProfileLib, line 158
} // library marker kkossev.deviceProfileLib, line 159

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 161
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 162
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 163
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 164
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 165
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 166
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 167
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 168
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 169
            return foundMap // library marker kkossev.deviceProfileLib, line 170
        } // library marker kkossev.deviceProfileLib, line 171
    } // library marker kkossev.deviceProfileLib, line 172
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 173
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 174
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 175
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 176
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 177
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 178
            return foundMap // library marker kkossev.deviceProfileLib, line 179
        } // library marker kkossev.deviceProfileLib, line 180
    } // library marker kkossev.deviceProfileLib, line 181
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 182
    return [:] // library marker kkossev.deviceProfileLib, line 183
} // library marker kkossev.deviceProfileLib, line 184

/** // library marker kkossev.deviceProfileLib, line 186
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 187
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 188
 */ // library marker kkossev.deviceProfileLib, line 189
void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 190
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 191
    if (DEVICE == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 192
    Map preferences = DEVICE?.preferences ?: [:] // library marker kkossev.deviceProfileLib, line 193
    if (preferences == null || preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 194
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 195
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 196
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 197
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 198
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 199
            return // continue // library marker kkossev.deviceProfileLib, line 200
        } // library marker kkossev.deviceProfileLib, line 201
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 202
        if (parMap == null || parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 203
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 204
        if (parMap?.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 205
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 206
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 207
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 208
    } // library marker kkossev.deviceProfileLib, line 209
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 210
} // library marker kkossev.deviceProfileLib, line 211

/** // library marker kkossev.deviceProfileLib, line 213
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 214
 * // library marker kkossev.deviceProfileLib, line 215
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 216
 */ // library marker kkossev.deviceProfileLib, line 217
List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 218
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 219
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 220
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 221
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 222
    } // library marker kkossev.deviceProfileLib, line 223
    return validPars // library marker kkossev.deviceProfileLib, line 224
} // library marker kkossev.deviceProfileLib, line 225

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 227
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 228
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 229
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 230
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 231
    def scaledValue // library marker kkossev.deviceProfileLib, line 232
    if (value == null) { // library marker kkossev.deviceProfileLib, line 233
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 234
        return null // library marker kkossev.deviceProfileLib, line 235
    } // library marker kkossev.deviceProfileLib, line 236
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 237
        case 'number' : // library marker kkossev.deviceProfileLib, line 238
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 239
            break // library marker kkossev.deviceProfileLib, line 240
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 241
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 242
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 243
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 244
            } // library marker kkossev.deviceProfileLib, line 245
            break // library marker kkossev.deviceProfileLib, line 246
        case 'bool' : // library marker kkossev.deviceProfileLib, line 247
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 248
            break // library marker kkossev.deviceProfileLib, line 249
        case 'enum' : // library marker kkossev.deviceProfileLib, line 250
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 251
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 252
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 253
                return null // library marker kkossev.deviceProfileLib, line 254
            } // library marker kkossev.deviceProfileLib, line 255
            scaledValue = value // library marker kkossev.deviceProfileLib, line 256
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 257
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 258
            } // library marker kkossev.deviceProfileLib, line 259
            break // library marker kkossev.deviceProfileLib, line 260
        default : // library marker kkossev.deviceProfileLib, line 261
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 262
            return null // library marker kkossev.deviceProfileLib, line 263
    } // library marker kkossev.deviceProfileLib, line 264
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 265
    return scaledValue // library marker kkossev.deviceProfileLib, line 266
} // library marker kkossev.deviceProfileLib, line 267

// called from customUpdated() method in the custom driver // library marker kkossev.deviceProfileLib, line 269
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 270
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 271
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 272
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 273
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 274
        return // library marker kkossev.deviceProfileLib, line 275
    } // library marker kkossev.deviceProfileLib, line 276
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 277
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 278
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 279
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 280
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 281
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 282
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 283
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 284
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 285
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 286
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 287
                // scale the value // library marker kkossev.deviceProfileLib, line 288
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 289
            } // library marker kkossev.deviceProfileLib, line 290
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLib, line 291
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLib, line 292
            } // library marker kkossev.deviceProfileLib, line 293
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLib, line 294
        } // library marker kkossev.deviceProfileLib, line 295
        else { logDebug "warning: couldn't find map for preference ${name}" ; return } // library marker kkossev.deviceProfileLib, line 296
    } // library marker kkossev.deviceProfileLib, line 297
    return // library marker kkossev.deviceProfileLib, line 298
} // library marker kkossev.deviceProfileLib, line 299

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 301
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 302
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 303
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 304
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 305
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 306
} // library marker kkossev.deviceProfileLib, line 307
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 308
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 309
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 310
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 311
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 312
} // library marker kkossev.deviceProfileLib, line 313
int invert(int val) { // library marker kkossev.deviceProfileLib, line 314
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 315
    else { return val } // library marker kkossev.deviceProfileLib, line 316
} // library marker kkossev.deviceProfileLib, line 317

// called from setPar and sendAttribite methods for non-Tuya DPs // library marker kkossev.deviceProfileLib, line 319
List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 320
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 321
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 322
    Map map = [:] // library marker kkossev.deviceProfileLib, line 323
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 324
    try { // library marker kkossev.deviceProfileLib, line 325
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 326
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 327
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 328
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 329
    } // library marker kkossev.deviceProfileLib, line 330
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLib, line 331
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 332
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 333
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLib, line 334
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 335
    } // library marker kkossev.deviceProfileLib, line 336
    if (map.mfgCode != null && map.mfgCode != '') { // library marker kkossev.deviceProfileLib, line 337
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 338
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mfgCode, delay = 50) // library marker kkossev.deviceProfileLib, line 339
    } // library marker kkossev.deviceProfileLib, line 340
    else { // library marker kkossev.deviceProfileLib, line 341
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 50) // library marker kkossev.deviceProfileLib, line 342
    } // library marker kkossev.deviceProfileLib, line 343
    return cmds // library marker kkossev.deviceProfileLib, line 344
} // library marker kkossev.deviceProfileLib, line 345

/** // library marker kkossev.deviceProfileLib, line 347
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 348
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 349
 * // library marker kkossev.deviceProfileLib, line 350
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 351
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 352
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 353
 */ // library marker kkossev.deviceProfileLib, line 354
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 355
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 356
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 357
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 358
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 359
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 360
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 361
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 362
        case 'number' : // library marker kkossev.deviceProfileLib, line 363
            // TODO - negative values ! // library marker kkossev.deviceProfileLib, line 364
            // TODO - better conversion to integer! // library marker kkossev.deviceProfileLib, line 365
            value = safeToInt(val, 0) // library marker kkossev.deviceProfileLib, line 366
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 367
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 368
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 369
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 370
            } // library marker kkossev.deviceProfileLib, line 371
            else { // library marker kkossev.deviceProfileLib, line 372
                scaledValue = value // library marker kkossev.deviceProfileLib, line 373
            } // library marker kkossev.deviceProfileLib, line 374
            break // library marker kkossev.deviceProfileLib, line 375

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 377
            value = safeToDouble(val, 0.0) // library marker kkossev.deviceProfileLib, line 378
            // scale the value // library marker kkossev.deviceProfileLib, line 379
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 380
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 381
            } // library marker kkossev.deviceProfileLib, line 382
            else { // library marker kkossev.deviceProfileLib, line 383
                scaledValue = value // library marker kkossev.deviceProfileLib, line 384
            } // library marker kkossev.deviceProfileLib, line 385
            break // library marker kkossev.deviceProfileLib, line 386

        case 'bool' : // library marker kkossev.deviceProfileLib, line 388
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 389
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 390
            else { // library marker kkossev.deviceProfileLib, line 391
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 392
                return null // library marker kkossev.deviceProfileLib, line 393
            } // library marker kkossev.deviceProfileLib, line 394
            break // library marker kkossev.deviceProfileLib, line 395
        case 'enum' : // library marker kkossev.deviceProfileLib, line 396
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 397
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 398
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 399
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 400
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 401
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 402
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 403
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 404
            } // library marker kkossev.deviceProfileLib, line 405
            else { // library marker kkossev.deviceProfileLib, line 406
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 407
            } // library marker kkossev.deviceProfileLib, line 408
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 409
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 410
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 411
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 412
                // find the key for the value // library marker kkossev.deviceProfileLib, line 413
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 414
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 415
                if (key == null) { // library marker kkossev.deviceProfileLib, line 416
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 417
                    return null // library marker kkossev.deviceProfileLib, line 418
                } // library marker kkossev.deviceProfileLib, line 419
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 420
            //return null // library marker kkossev.deviceProfileLib, line 421
            } // library marker kkossev.deviceProfileLib, line 422
            break // library marker kkossev.deviceProfileLib, line 423
        default : // library marker kkossev.deviceProfileLib, line 424
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 425
            return null // library marker kkossev.deviceProfileLib, line 426
    } // library marker kkossev.deviceProfileLib, line 427
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 428
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 429
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 430
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 431
        return null // library marker kkossev.deviceProfileLib, line 432
    } // library marker kkossev.deviceProfileLib, line 433
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 434
    return scaledValue // library marker kkossev.deviceProfileLib, line 435
} // library marker kkossev.deviceProfileLib, line 436

/** // library marker kkossev.deviceProfileLib, line 438
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 439
 * // library marker kkossev.deviceProfileLib, line 440
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 441
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 442
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 443
 */ // library marker kkossev.deviceProfileLib, line 444
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 445
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 446
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 447
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 448
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 449
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 450
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 451
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 452
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 453
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 454
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 455
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 456
    if (scaledValue == null) { logInfo "setPar: invalid parameter ${par} value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 457

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 459
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 460
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 461
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 462
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 463
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 464
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 465
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 466
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 467
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 468
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 469
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 470
            return true // library marker kkossev.deviceProfileLib, line 471
        } // library marker kkossev.deviceProfileLib, line 472
        else { // library marker kkossev.deviceProfileLib, line 473
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 474
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 475
        } // library marker kkossev.deviceProfileLib, line 476
    } // library marker kkossev.deviceProfileLib, line 477
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 478
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 479
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 480
        def valMiscType // library marker kkossev.deviceProfileLib, line 481
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 482
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 483
            // find the key for the value // library marker kkossev.deviceProfileLib, line 484
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 485
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 486
            if (key == null) { // library marker kkossev.deviceProfileLib, line 487
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 488
                return false // library marker kkossev.deviceProfileLib, line 489
            } // library marker kkossev.deviceProfileLib, line 490
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 491
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 492
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 493
        } // library marker kkossev.deviceProfileLib, line 494
        else { // library marker kkossev.deviceProfileLib, line 495
            valMiscType = val // library marker kkossev.deviceProfileLib, line 496
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 497
        } // library marker kkossev.deviceProfileLib, line 498
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 499
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 500
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 501
        return true // library marker kkossev.deviceProfileLib, line 502
    } // library marker kkossev.deviceProfileLib, line 503

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 505
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 506

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 508
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 509
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 510
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 511
        // Tuya DP // library marker kkossev.deviceProfileLib, line 512
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 513
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 514
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 515
            return false // library marker kkossev.deviceProfileLib, line 516
        } // library marker kkossev.deviceProfileLib, line 517
        else { // library marker kkossev.deviceProfileLib, line 518
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 519
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 520
            return false // library marker kkossev.deviceProfileLib, line 521
        } // library marker kkossev.deviceProfileLib, line 522
    } // library marker kkossev.deviceProfileLib, line 523
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 524
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 525
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 526
        int signedIntScaled = convertSignedInts(scaledValue, dpMap) // library marker kkossev.deviceProfileLib, line 527
        cmds = zclWriteAttribute(dpMap, signedIntScaled) // library marker kkossev.deviceProfileLib, line 528
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 529
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 530
            return false // library marker kkossev.deviceProfileLib, line 531
        } // library marker kkossev.deviceProfileLib, line 532
    } // library marker kkossev.deviceProfileLib, line 533
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 534
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 535
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 536
    return true // library marker kkossev.deviceProfileLib, line 537
} // library marker kkossev.deviceProfileLib, line 538

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 540
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 541
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 542
public List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 543
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 544
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 545
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 546
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 547
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 548
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 549
        return [] // library marker kkossev.deviceProfileLib, line 550
    } // library marker kkossev.deviceProfileLib, line 551
    String dpType // library marker kkossev.deviceProfileLib, line 552
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 553
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 554
    } // library marker kkossev.deviceProfileLib, line 555
    else { // library marker kkossev.deviceProfileLib, line 556
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 557
    } // library marker kkossev.deviceProfileLib, line 558
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 559
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 560
        return [] // library marker kkossev.deviceProfileLib, line 561
    } // library marker kkossev.deviceProfileLib, line 562
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 563
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 564
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 565
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 566
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 567
    } // library marker kkossev.deviceProfileLib, line 568
    else { // library marker kkossev.deviceProfileLib, line 569
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 570
    } // library marker kkossev.deviceProfileLib, line 571
    return cmds // library marker kkossev.deviceProfileLib, line 572
} // library marker kkossev.deviceProfileLib, line 573

int convertSignedInts(int val, Map dpMap) { // library marker kkossev.deviceProfileLib, line 575
    if (dpMap.dt == '0x28') { // library marker kkossev.deviceProfileLib, line 576
        if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 577
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 578
    } // library marker kkossev.deviceProfileLib, line 579
    else if (dpMap.dt == '0x29') { // library marker kkossev.deviceProfileLib, line 580
        if (val > 32767) { return (val as int) - 65536 } // library marker kkossev.deviceProfileLib, line 581
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 582
    } // library marker kkossev.deviceProfileLib, line 583
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 584
} // library marker kkossev.deviceProfileLib, line 585

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 587
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 588
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 589
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 590
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 591
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'DEVICE.preferences is empty!' ; return false } // library marker kkossev.deviceProfileLib, line 592

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 594
    l//log.trace "sendAttribute: dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 595
    if (dpMap == null || dpMap?.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 596
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 597
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 598
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 599
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 600
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 601
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 602
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 603
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 604
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 605
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 606
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 607
        try { // library marker kkossev.deviceProfileLib, line 608
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 609
        } // library marker kkossev.deviceProfileLib, line 610
        catch (e) { // library marker kkossev.deviceProfileLib, line 611
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 612
            return false // library marker kkossev.deviceProfileLib, line 613
        } // library marker kkossev.deviceProfileLib, line 614
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 615
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 616
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 617
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 618
            return true // library marker kkossev.deviceProfileLib, line 619
        } // library marker kkossev.deviceProfileLib, line 620
        else { // library marker kkossev.deviceProfileLib, line 621
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 622
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 623
        } // library marker kkossev.deviceProfileLib, line 624
    } // library marker kkossev.deviceProfileLib, line 625
    else { // library marker kkossev.deviceProfileLib, line 626
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 627
    } // library marker kkossev.deviceProfileLib, line 628
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 629
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 630
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 631
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 632
        // patch !! // library marker kkossev.deviceProfileLib, line 633
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 634
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 635
        } // library marker kkossev.deviceProfileLib, line 636
        else { // library marker kkossev.deviceProfileLib, line 637
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 638
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 639
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 640
        } // library marker kkossev.deviceProfileLib, line 641
        return true // library marker kkossev.deviceProfileLib, line 642
    } // library marker kkossev.deviceProfileLib, line 643
    else { // library marker kkossev.deviceProfileLib, line 644
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 645
    } // library marker kkossev.deviceProfileLib, line 646
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 647
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 648
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 649
    try { // library marker kkossev.deviceProfileLib, line 650
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 651
    } // library marker kkossev.deviceProfileLib, line 652
    catch (e) { // library marker kkossev.deviceProfileLib, line 653
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 654
        return false // library marker kkossev.deviceProfileLib, line 655
    } // library marker kkossev.deviceProfileLib, line 656
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 657
        // Tuya DP // library marker kkossev.deviceProfileLib, line 658
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 659
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 660
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 661
            return false // library marker kkossev.deviceProfileLib, line 662
        } // library marker kkossev.deviceProfileLib, line 663
        else { // library marker kkossev.deviceProfileLib, line 664
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 665
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 666
            return true // library marker kkossev.deviceProfileLib, line 667
        } // library marker kkossev.deviceProfileLib, line 668
    } // library marker kkossev.deviceProfileLib, line 669
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 670
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 671
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 672
    } // library marker kkossev.deviceProfileLib, line 673
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 674
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 675
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 676
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 677
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 678
            return false // library marker kkossev.deviceProfileLib, line 679
        } // library marker kkossev.deviceProfileLib, line 680
    } // library marker kkossev.deviceProfileLib, line 681
    else { // library marker kkossev.deviceProfileLib, line 682
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 683
        return false // library marker kkossev.deviceProfileLib, line 684
    } // library marker kkossev.deviceProfileLib, line 685
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 686
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 687
    return true // library marker kkossev.deviceProfileLib, line 688
} // library marker kkossev.deviceProfileLib, line 689

/** // library marker kkossev.deviceProfileLib, line 691
 * SENDS a list of Zigbee commands to be sent to the device. // library marker kkossev.deviceProfileLib, line 692
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 693
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 694
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 695
 */ // library marker kkossev.deviceProfileLib, line 696
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 697
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 698
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 699
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 700
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 701
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 702
    if (supportedCommandsMap == null || supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 703
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 704
        return false // library marker kkossev.deviceProfileLib, line 705
    } // library marker kkossev.deviceProfileLib, line 706
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 707
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 708
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 709
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 710
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 711
        return false // library marker kkossev.deviceProfileLib, line 712
    } // library marker kkossev.deviceProfileLib, line 713
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 714
    def func, funcResult // library marker kkossev.deviceProfileLib, line 715
    try { // library marker kkossev.deviceProfileLib, line 716
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 717
        if (val != null && val != '') { // library marker kkossev.deviceProfileLib, line 718
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 719
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 720
        } // library marker kkossev.deviceProfileLib, line 721
        else { // library marker kkossev.deviceProfileLib, line 722
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 723
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 724
        } // library marker kkossev.deviceProfileLib, line 725
    } // library marker kkossev.deviceProfileLib, line 726
    catch (e) { // library marker kkossev.deviceProfileLib, line 727
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 728
        return false // library marker kkossev.deviceProfileLib, line 729
    } // library marker kkossev.deviceProfileLib, line 730
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 731
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 732
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 733
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 734
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 735
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 736
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 737
        } // library marker kkossev.deviceProfileLib, line 738
    } // library marker kkossev.deviceProfileLib, line 739
    else if (funcResult == null) { // library marker kkossev.deviceProfileLib, line 740
        return false // library marker kkossev.deviceProfileLib, line 741
    } // library marker kkossev.deviceProfileLib, line 742
     else { // library marker kkossev.deviceProfileLib, line 743
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 744
        return false // library marker kkossev.deviceProfileLib, line 745
    } // library marker kkossev.deviceProfileLib, line 746
    return true // library marker kkossev.deviceProfileLib, line 747
} // library marker kkossev.deviceProfileLib, line 748

/** // library marker kkossev.deviceProfileLib, line 750
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 751
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 752
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 753
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 754
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 755
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 756
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 757
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 758
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 759
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 760
 */ // library marker kkossev.deviceProfileLib, line 761
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 762
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 763
    Map input = [:] // library marker kkossev.deviceProfileLib, line 764
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 765
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 766
    Object preference // library marker kkossev.deviceProfileLib, line 767
    try { preference = DEVICE?.preferences["$param"] } // library marker kkossev.deviceProfileLib, line 768
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 769
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 770
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } } // library marker kkossev.deviceProfileLib, line 771
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 772
    /* // library marker kkossev.deviceProfileLib, line 773
    // TODO - check if this is neccessary? isTuyaDP is not defined! // library marker kkossev.deviceProfileLib, line 774
    try { isTuyaDP = preference.isNumber() } // library marker kkossev.deviceProfileLib, line 775
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 776
    */ // library marker kkossev.deviceProfileLib, line 777
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 778
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 779
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 780
    if (foundMap == null || foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 781
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 782
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) { // library marker kkossev.deviceProfileLib, line 783
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" } // library marker kkossev.deviceProfileLib, line 784
        return [:] // library marker kkossev.deviceProfileLib, line 785
    } // library marker kkossev.deviceProfileLib, line 786
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 787
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 788
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 789
    //input.description = (foundMap.description ?: foundMap.title)?.replaceAll(/<\/?b>/, '')  // if description is not defined, use the title // library marker kkossev.deviceProfileLib, line 790
    input.description = foundMap.description ?: ''   // if description is not defined, skip it // library marker kkossev.deviceProfileLib, line 791
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 792
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 793
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 794
        } // library marker kkossev.deviceProfileLib, line 795
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 796
            if (input.description != '') { input.description += '<br>' } // library marker kkossev.deviceProfileLib, line 797
            input.description += "<i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 798
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 799
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 800
            } // library marker kkossev.deviceProfileLib, line 801
        } // library marker kkossev.deviceProfileLib, line 802
    } // library marker kkossev.deviceProfileLib, line 803
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 804
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 805
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 806
    }/* // library marker kkossev.deviceProfileLib, line 807
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 808
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 809
    }*/ // library marker kkossev.deviceProfileLib, line 810
    else { // library marker kkossev.deviceProfileLib, line 811
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 812
        return [:] // library marker kkossev.deviceProfileLib, line 813
    } // library marker kkossev.deviceProfileLib, line 814
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 815
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 816
    } // library marker kkossev.deviceProfileLib, line 817
    return input // library marker kkossev.deviceProfileLib, line 818
} // library marker kkossev.deviceProfileLib, line 819

/** // library marker kkossev.deviceProfileLib, line 821
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 822
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 823
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 824
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 825
 */ // library marker kkossev.deviceProfileLib, line 826
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 827
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 828
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 829
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 830
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 831
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 832
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 833
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 834
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 835
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 836
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 837
            } // library marker kkossev.deviceProfileLib, line 838
        } // library marker kkossev.deviceProfileLib, line 839
    } // library marker kkossev.deviceProfileLib, line 840
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 841
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 842
    } // library marker kkossev.deviceProfileLib, line 843
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 844
} // library marker kkossev.deviceProfileLib, line 845

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 847
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 848
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 849
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 850
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 851
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 852
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 853
    } // library marker kkossev.deviceProfileLib, line 854
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 855
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 856
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 857
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 858
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 859
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 860
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 861
    } else { // library marker kkossev.deviceProfileLib, line 862
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 863
    } // library marker kkossev.deviceProfileLib, line 864
} // library marker kkossev.deviceProfileLib, line 865

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLib, line 867
List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLib, line 868
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLib, line 869
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 870
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLib, line 871
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 872
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 873
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 874
            if (k != null) { // library marker kkossev.deviceProfileLib, line 875
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 876
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 877
                if (map != null) { // library marker kkossev.deviceProfileLib, line 878
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 879
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 880
                } // library marker kkossev.deviceProfileLib, line 881
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 882
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 883
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 884
                } // library marker kkossev.deviceProfileLib, line 885
            } // library marker kkossev.deviceProfileLib, line 886
        } // library marker kkossev.deviceProfileLib, line 887
    } // library marker kkossev.deviceProfileLib, line 888
    return cmds // library marker kkossev.deviceProfileLib, line 889
} // library marker kkossev.deviceProfileLib, line 890

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 892
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 893
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 894
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 895
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 896
    return cmds // library marker kkossev.deviceProfileLib, line 897
} // library marker kkossev.deviceProfileLib, line 898

// TODO ! // library marker kkossev.deviceProfileLib, line 900
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 901
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 902
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 903
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 904
    return cmds // library marker kkossev.deviceProfileLib, line 905
} // library marker kkossev.deviceProfileLib, line 906

// TODO // library marker kkossev.deviceProfileLib, line 908
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 909
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 910
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 911
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 912
    return cmds // library marker kkossev.deviceProfileLib, line 913
} // library marker kkossev.deviceProfileLib, line 914

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 916
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 917
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 918
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 919
    } // library marker kkossev.deviceProfileLib, line 920
} // library marker kkossev.deviceProfileLib, line 921

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 923
    String ps = DEVICE?.device?.powerSource // library marker kkossev.deviceProfileLib, line 924
    logDebug "initEventsDeviceProfile(${fullInit}) for deviceProfile=${state.deviceProfile} DEVICE?.device?.powerSource=${ps} ps.isEmpty()=${ps?.isEmpty()}" // library marker kkossev.deviceProfileLib, line 925
    if (ps != null && !ps.isEmpty()) { // library marker kkossev.deviceProfileLib, line 926
        sendEvent(name: 'powerSource', value: ps, descriptionText: "Power Source set to '${ps}'", type: 'digital') // library marker kkossev.deviceProfileLib, line 927
    } // library marker kkossev.deviceProfileLib, line 928
} // library marker kkossev.deviceProfileLib, line 929

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 931

// // library marker kkossev.deviceProfileLib, line 933
// called from parse() // library marker kkossev.deviceProfileLib, line 934
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 935
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 936
// // library marker kkossev.deviceProfileLib, line 937
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 938
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 939
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 940
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 941
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 942
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 943
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 944
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 945
} // library marker kkossev.deviceProfileLib, line 946

// // library marker kkossev.deviceProfileLib, line 948
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 949
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 950
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 951
// // library marker kkossev.deviceProfileLib, line 952
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 953
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 954
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 955
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 956
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 957
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 958
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 959
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 960
} // library marker kkossev.deviceProfileLib, line 961

// all DPs are spammy - sent periodically! // library marker kkossev.deviceProfileLib, line 963
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 964
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 965
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 966
    return isSpammy // library marker kkossev.deviceProfileLib, line 967
} // library marker kkossev.deviceProfileLib, line 968

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 970
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 971
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 972
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 973
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 974
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 975
    } // library marker kkossev.deviceProfileLib, line 976
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 977
} // library marker kkossev.deviceProfileLib, line 978

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 980
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 981
    boolean isEqual // library marker kkossev.deviceProfileLib, line 982
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 983
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 984
    } // library marker kkossev.deviceProfileLib, line 985
    else { // library marker kkossev.deviceProfileLib, line 986
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 987
    } // library marker kkossev.deviceProfileLib, line 988
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 989
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 990
} // library marker kkossev.deviceProfileLib, line 991

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 993
    Double convertedValue // library marker kkossev.deviceProfileLib, line 994
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 995
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 996
    } // library marker kkossev.deviceProfileLib, line 997
    else { // library marker kkossev.deviceProfileLib, line 998
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 999
    } // library marker kkossev.deviceProfileLib, line 1000
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1001
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1002
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1003
} // library marker kkossev.deviceProfileLib, line 1004

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1006
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1007
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1008
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1009
    def convertedValue // library marker kkossev.deviceProfileLib, line 1010
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1011
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1012
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1013
    } // library marker kkossev.deviceProfileLib, line 1014
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1015
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1016
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1017
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1018
            isEqual = false // library marker kkossev.deviceProfileLib, line 1019
        } // library marker kkossev.deviceProfileLib, line 1020
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1021
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1022
        } // library marker kkossev.deviceProfileLib, line 1023
    } // library marker kkossev.deviceProfileLib, line 1024
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1025
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1026
} // library marker kkossev.deviceProfileLib, line 1027

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1029
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1030
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1031
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1032
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1033
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1034
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1035
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1036
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1037
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1038
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1039
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1040
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1041
            break // library marker kkossev.deviceProfileLib, line 1042
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1043
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1044
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1045
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1046
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1047
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1048
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1049
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1050
            } // library marker kkossev.deviceProfileLib, line 1051
            else { // library marker kkossev.deviceProfileLib, line 1052
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1053
            } // library marker kkossev.deviceProfileLib, line 1054
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1055
            break // library marker kkossev.deviceProfileLib, line 1056
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1057
        case 'number' : // library marker kkossev.deviceProfileLib, line 1058
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1059
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1060
            break // library marker kkossev.deviceProfileLib, line 1061
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1062
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1063
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1064
            break // library marker kkossev.deviceProfileLib, line 1065
        default : // library marker kkossev.deviceProfileLib, line 1066
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1067
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1068
    } // library marker kkossev.deviceProfileLib, line 1069
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1070
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1071
    } // library marker kkossev.deviceProfileLib, line 1072
    // // library marker kkossev.deviceProfileLib, line 1073
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1074
} // library marker kkossev.deviceProfileLib, line 1075

// // library marker kkossev.deviceProfileLib, line 1077
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1078
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1079
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1080
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1081
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1082
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1083
// // library marker kkossev.deviceProfileLib, line 1084
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1085
// // library marker kkossev.deviceProfileLib, line 1086
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1087
// // library marker kkossev.deviceProfileLib, line 1088
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1089
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1090
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1091
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1092
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1093
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1094
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1095
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1096
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1097
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1098
            break // library marker kkossev.deviceProfileLib, line 1099
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1100
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1101
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1102
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1103
            logTrace "latestEvent is ${latestEvent} dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1104
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1105
            if (dataType == null || dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1106
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1107
            } // library marker kkossev.deviceProfileLib, line 1108
            else { // library marker kkossev.deviceProfileLib, line 1109
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1110
            } // library marker kkossev.deviceProfileLib, line 1111
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1112
            break // library marker kkossev.deviceProfileLib, line 1113
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1114
        case 'number' : // library marker kkossev.deviceProfileLib, line 1115
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1116
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1117
            break // library marker kkossev.deviceProfileLib, line 1118
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1119
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1120
            break // library marker kkossev.deviceProfileLib, line 1121
        default : // library marker kkossev.deviceProfileLib, line 1122
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1123
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1124
    } // library marker kkossev.deviceProfileLib, line 1125
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1126
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1127
} // library marker kkossev.deviceProfileLib, line 1128

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1130
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1131
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1132
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1133
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1134
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1135
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1136
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1137
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1138
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1139
    } // library marker kkossev.deviceProfileLib, line 1140
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1141
    try { // library marker kkossev.deviceProfileLib, line 1142
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1143
    } // library marker kkossev.deviceProfileLib, line 1144
    catch (e) { // library marker kkossev.deviceProfileLib, line 1145
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1146
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1147
    } // library marker kkossev.deviceProfileLib, line 1148
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1149
    return fncmd // library marker kkossev.deviceProfileLib, line 1150
} // library marker kkossev.deviceProfileLib, line 1151

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1153
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1154
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1155
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1156
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1157
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1158
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1159

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1161
    if (attribMap == null || attribMap?.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1162

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1164
    int value // library marker kkossev.deviceProfileLib, line 1165
    try { // library marker kkossev.deviceProfileLib, line 1166
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1167
    } // library marker kkossev.deviceProfileLib, line 1168
    catch (e) { // library marker kkossev.deviceProfileLib, line 1169
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1170
        return false // library marker kkossev.deviceProfileLib, line 1171
    } // library marker kkossev.deviceProfileLib, line 1172
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1173
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1174
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1175
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1176
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1177
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1178
        return false // library marker kkossev.deviceProfileLib, line 1179
    } // library marker kkossev.deviceProfileLib, line 1180
    value = convertSignedInts(value, foundItem) // library marker kkossev.deviceProfileLib, line 1181
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1182
} // library marker kkossev.deviceProfileLib, line 1183

/** // library marker kkossev.deviceProfileLib, line 1185
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1186
 * // library marker kkossev.deviceProfileLib, line 1187
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1188
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1189
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1190
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1191
 * // library marker kkossev.deviceProfileLib, line 1192
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1193
 */ // library marker kkossev.deviceProfileLib, line 1194
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1195
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1196
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1197
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1198
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1199

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1201
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1202

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1204
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1205
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1206
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1207
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1208
        return false // library marker kkossev.deviceProfileLib, line 1209
    } // library marker kkossev.deviceProfileLib, line 1210
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1211
} // library marker kkossev.deviceProfileLib, line 1212

/* // library marker kkossev.deviceProfileLib, line 1214
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1215
 */ // library marker kkossev.deviceProfileLib, line 1216
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1217
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1218
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1219
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1220
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1221
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1222
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1223
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1224
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1225
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1226
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1227
        } // library marker kkossev.deviceProfileLib, line 1228
    } // library marker kkossev.deviceProfileLib, line 1229
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1230

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1232
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1233
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1234
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1235
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1236
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences?.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1237
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1238
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1239
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1240
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1241
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1242
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1243
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1244
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1245
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1246
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1247
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1248

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1250
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1251
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1252
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1253
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1254
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1255
            if (settings.logEnable) { logInfo "${descText} (Debug logging is enabled)" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1256
        } // library marker kkossev.deviceProfileLib, line 1257
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1258
    } // library marker kkossev.deviceProfileLib, line 1259

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1261
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1262
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1263
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1264
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1265
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1266
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1267
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1268
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1269
            } // library marker kkossev.deviceProfileLib, line 1270
        } // library marker kkossev.deviceProfileLib, line 1271
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1272
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1273
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1274
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1275
            } // library marker kkossev.deviceProfileLib, line 1276
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1277
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1278
            try { // library marker kkossev.deviceProfileLib, line 1279
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1280
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1281
            } // library marker kkossev.deviceProfileLib, line 1282
            catch (e) { // library marker kkossev.deviceProfileLib, line 1283
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1284
            } // library marker kkossev.deviceProfileLib, line 1285
        } // library marker kkossev.deviceProfileLib, line 1286
    } // library marker kkossev.deviceProfileLib, line 1287
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1288
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1289
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1290
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1291
    } // library marker kkossev.deviceProfileLib, line 1292

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1294
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1295
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1296
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1297
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1298
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1299
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1300
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1301
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1302
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1303
            } // library marker kkossev.deviceProfileLib, line 1304
            if (foundItem.processDuplicated == true) { // library marker kkossev.deviceProfileLib, line 1305
                logDebug 'processDuplicated=true -> continue' // library marker kkossev.deviceProfileLib, line 1306
            } // library marker kkossev.deviceProfileLib, line 1307

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1309
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1310
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1311
            // continue ... // library marker kkossev.deviceProfileLib, line 1312
            } // library marker kkossev.deviceProfileLib, line 1313

            else { // library marker kkossev.deviceProfileLib, line 1315
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1316
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1317
                } // library marker kkossev.deviceProfileLib, line 1318
                else { // library marker kkossev.deviceProfileLib, line 1319
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLib, line 1320
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1321
                } // library marker kkossev.deviceProfileLib, line 1322
            } // library marker kkossev.deviceProfileLib, line 1323
        } // library marker kkossev.deviceProfileLib, line 1324

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1326
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1327
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1328
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1329
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1330
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1331
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1332
        } // library marker kkossev.deviceProfileLib, line 1333
        else { // library marker kkossev.deviceProfileLib, line 1334
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1335
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1336
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1337
                logTrace "event ${name} sent w/ valueScaled ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1338
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1339
            } // library marker kkossev.deviceProfileLib, line 1340
        } // library marker kkossev.deviceProfileLib, line 1341
    } // library marker kkossev.deviceProfileLib, line 1342
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1343
} // library marker kkossev.deviceProfileLib, line 1344

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1346
public boolean validateAndFixPreferences(String debugStr) { return validateAndFixPreferences(debugStr.toBoolean() as boolean) } // library marker kkossev.deviceProfileLib, line 1347
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1348
    //debug = true // library marker kkossev.deviceProfileLib, line 1349
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1350
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLib, line 1351
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLib, line 1352
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1353
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLib, line 1354
    String settingType = '' // library marker kkossev.deviceProfileLib, line 1355
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1356
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1357
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1358
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1359
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1360
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1361
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1362
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1363
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1364
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1365
            try { // library marker kkossev.deviceProfileLib, line 1366
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1367
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1368
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLib, line 1369
            } // library marker kkossev.deviceProfileLib, line 1370
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1371
            try { // library marker kkossev.deviceProfileLib, line 1372
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1373
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1374
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1375
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1376
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1377
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1378
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1379
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1380
                    } // library marker kkossev.deviceProfileLib, line 1381
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1382
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1383
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1384
                    } else { // library marker kkossev.deviceProfileLib, line 1385
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1386
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1387
                    } // library marker kkossev.deviceProfileLib, line 1388
                } // library marker kkossev.deviceProfileLib, line 1389
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1390
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1391
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1392
            } // library marker kkossev.deviceProfileLib, line 1393
            catch (e) { // library marker kkossev.deviceProfileLib, line 1394
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1395
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1396
                try { // library marker kkossev.deviceProfileLib, line 1397
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1398
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1399
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1400
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1401
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1402
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLib, line 1403
                } // library marker kkossev.deviceProfileLib, line 1404
            } // library marker kkossev.deviceProfileLib, line 1405
        } // library marker kkossev.deviceProfileLib, line 1406
        total ++ // library marker kkossev.deviceProfileLib, line 1407
    } // library marker kkossev.deviceProfileLib, line 1408
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1409
    return true // library marker kkossev.deviceProfileLib, line 1410
} // library marker kkossev.deviceProfileLib, line 1411

// command for debugging // library marker kkossev.deviceProfileLib, line 1413
public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1414
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1415
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1416
            logInfo "${fingerprint}" // library marker kkossev.deviceProfileLib, line 1417
        } // library marker kkossev.deviceProfileLib, line 1418
    } // library marker kkossev.deviceProfileLib, line 1419
} // library marker kkossev.deviceProfileLib, line 1420

// command for debugging // library marker kkossev.deviceProfileLib, line 1422
public void printPreferences() { // library marker kkossev.deviceProfileLib, line 1423
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 1424
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 1425
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 1426
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLib, line 1427
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 1428
                log.trace inputMap // library marker kkossev.deviceProfileLib, line 1429
            } // library marker kkossev.deviceProfileLib, line 1430
        } // library marker kkossev.deviceProfileLib, line 1431
    } // library marker kkossev.deviceProfileLib, line 1432
} // library marker kkossev.deviceProfileLib, line 1433

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~

// ~~~~~ start include (179) kkossev.thermostatLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.thermostatLib, line 1
library( // library marker kkossev.thermostatLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Thermostat Library', name: 'thermostatLib', namespace: 'kkossev', // library marker kkossev.thermostatLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/thermostatLib.groovy', documentationLink: '', // library marker kkossev.thermostatLib, line 4
    version: '3.3.3') // library marker kkossev.thermostatLib, line 5
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
 * ver. 3.3.3  2024-09-01 kkossev  - (dev.branch) // library marker kkossev.thermostatLib, line 21
 * // library marker kkossev.thermostatLib, line 22
 *                                   TODO: add eco() method // library marker kkossev.thermostatLib, line 23
 *                                   TODO: refactor sendHeatingSetpointEvent // library marker kkossev.thermostatLib, line 24
*/ // library marker kkossev.thermostatLib, line 25

public static String thermostatLibVersion()   { '3.3.3' } // library marker kkossev.thermostatLib, line 27
public static String thermostatLibStamp() { '2024/09/01 10:44 AM' } // library marker kkossev.thermostatLib, line 28

metadata { // library marker kkossev.thermostatLib, line 30
    capability 'Actuator'           // also in onOffLib // library marker kkossev.thermostatLib, line 31
    capability 'Sensor' // library marker kkossev.thermostatLib, line 32
    capability 'Thermostat'                 // needed for HomeKit // library marker kkossev.thermostatLib, line 33
                // coolingSetpoint - NUMBER; heatingSetpoint - NUMBER; supportedThermostatFanModes - JSON_OBJECT; supportedThermostatModes - JSON_OBJECT; temperature - NUMBER, unit:°F || °C; thermostatFanMode - ENUM ["on", "circulate", "auto"] // library marker kkossev.thermostatLib, line 34
                // thermostatMode - ENUM ["auto", "off", "heat", "emergency heat", "cool"]; thermostatOperatingState - ENUM ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]; thermostatSetpoint - NUMBER, unit:°F || °C // library marker kkossev.thermostatLib, line 35
    capability 'ThermostatHeatingSetpoint' // library marker kkossev.thermostatLib, line 36
    capability 'ThermostatCoolingSetpoint' // library marker kkossev.thermostatLib, line 37
    capability 'ThermostatOperatingState'   // thermostatOperatingState - ENUM ["vent economizer", "pending cool", "cooling", "heating", "pending heat", "fan only", "idle"] // library marker kkossev.thermostatLib, line 38
    capability 'ThermostatSetpoint' // library marker kkossev.thermostatLib, line 39
    capability 'ThermostatMode' // library marker kkossev.thermostatLib, line 40
    capability 'ThermostatFanMode' // library marker kkossev.thermostatLib, line 41
    // no attributes // library marker kkossev.thermostatLib, line 42

    command 'setThermostatMode', [[name: 'thermostat mode (not all are available!)', type: 'ENUM', constraints: ['--- select ---'] + AllPossibleThermostatModesOpts.options.values() as List<String>]] // library marker kkossev.thermostatLib, line 44
    //    command 'setTemperature', ['NUMBER']                        // Virtual thermostat  TODO - decide if it is needed // library marker kkossev.thermostatLib, line 45

    preferences { // library marker kkossev.thermostatLib, line 47
        if (device) { // TODO -  move it to the deviceProfile preferences // library marker kkossev.thermostatLib, line 48
            input name: 'temperaturePollingInterval', type: 'enum', title: '<b>Temperature polling interval</b>', options: TrvTemperaturePollingIntervalOpts.options, defaultValue: TrvTemperaturePollingIntervalOpts.defaultValue, required: true, description: 'Changes how often the hub will poll the TRV for faster temperature reading updates and nice looking graphs.' // library marker kkossev.thermostatLib, line 49
        } // library marker kkossev.thermostatLib, line 50
    } // library marker kkossev.thermostatLib, line 51
} // library marker kkossev.thermostatLib, line 52

@Field static final Map TrvTemperaturePollingIntervalOpts = [ // library marker kkossev.thermostatLib, line 54
    defaultValue: 600, // library marker kkossev.thermostatLib, line 55
    options     : [0: 'Disabled', 60: 'Every minute (not recommended)', 120: 'Every 2 minutes', 300: 'Every 5 minutes', 600: 'Every 10 minutes', 900: 'Every 15 minutes', 1800: 'Every 30 minutes', 3600: 'Every 1 hour'] // library marker kkossev.thermostatLib, line 56
] // library marker kkossev.thermostatLib, line 57

@Field static final Map AllPossibleThermostatModesOpts = [ // library marker kkossev.thermostatLib, line 59
    defaultValue: 1, // library marker kkossev.thermostatLib, line 60
    options     : [0: 'off', 1: 'heat', 2: 'cool', 3: 'auto', 4: 'emergency heat', 5: 'eco'] // library marker kkossev.thermostatLib, line 61
] // library marker kkossev.thermostatLib, line 62

public void heat() { setThermostatMode('heat') } // library marker kkossev.thermostatLib, line 64
public void auto() { setThermostatMode('auto') } // library marker kkossev.thermostatLib, line 65
public void cool() { setThermostatMode('cool') } // library marker kkossev.thermostatLib, line 66
public void emergencyHeat() { setThermostatMode('emergency heat') } // library marker kkossev.thermostatLib, line 67
public void eco() { setThermostatMode('eco') } // library marker kkossev.thermostatLib, line 68

public void setThermostatFanMode(final String fanMode) { sendEvent(name: 'thermostatFanMode', value: "${fanMode}", descriptionText: getDescriptionText("thermostatFanMode is ${fanMode}")) } // library marker kkossev.thermostatLib, line 70
public void fanAuto() { setThermostatFanMode('auto') } // library marker kkossev.thermostatLib, line 71
public void fanCirculate() { setThermostatFanMode('circulate') } // library marker kkossev.thermostatLib, line 72
public void fanOn() { setThermostatFanMode('on') } // library marker kkossev.thermostatLib, line 73

public void customOff() { setThermostatMode('off') }    // invoked from the common library // library marker kkossev.thermostatLib, line 75
public void customOn()  { setThermostatMode('heat') }   // invoked from the common library // library marker kkossev.thermostatLib, line 76

/* // library marker kkossev.thermostatLib, line 78
 * ----------------------------------------------------------------------------- // library marker kkossev.thermostatLib, line 79
 * thermostat cluster 0x0201 // library marker kkossev.thermostatLib, line 80
 * ----------------------------------------------------------------------------- // library marker kkossev.thermostatLib, line 81
*/ // library marker kkossev.thermostatLib, line 82
// * should be implemented in the custom driver code ... // library marker kkossev.thermostatLib, line 83
public void standardParseThermostatCluster(final Map descMap) { // library marker kkossev.thermostatLib, line 84
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value)) // library marker kkossev.thermostatLib, line 85
    logTrace "standardParseThermostatCluster: zigbee received Thermostat cluster (0x0201) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})" // library marker kkossev.thermostatLib, line 86
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return } // library marker kkossev.thermostatLib, line 87
    if (deviceProfilesV3 != null) { // library marker kkossev.thermostatLib, line 88
        boolean result = processClusterAttributeFromDeviceProfile(descMap) // library marker kkossev.thermostatLib, line 89
        if ( result == false ) { // library marker kkossev.thermostatLib, line 90
            logWarn "standardParseThermostatCluster: received unknown Thermostat cluster (0x0201) attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.thermostatLib, line 91
        } // library marker kkossev.thermostatLib, line 92
    } // library marker kkossev.thermostatLib, line 93
    // try to process the attribute value // library marker kkossev.thermostatLib, line 94
    standardHandleThermostatEvent(value) // library marker kkossev.thermostatLib, line 95
} // library marker kkossev.thermostatLib, line 96

//  setHeatingSetpoint thermostat capability standard command // library marker kkossev.thermostatLib, line 98
//  1°C steps. (0.5°C setting on the TRV itself, rounded for zigbee interface) // library marker kkossev.thermostatLib, line 99
// // library marker kkossev.thermostatLib, line 100
void setHeatingSetpoint(final Number temperaturePar ) { // library marker kkossev.thermostatLib, line 101
    BigDecimal temperature = temperaturePar.toBigDecimal() // library marker kkossev.thermostatLib, line 102
    logTrace "setHeatingSetpoint(${temperature}) called!" // library marker kkossev.thermostatLib, line 103
    BigDecimal previousSetpoint = (device.currentState('heatingSetpoint')?.value ?: 0.0G).toBigDecimal() // library marker kkossev.thermostatLib, line 104
    BigDecimal tempDouble = temperature // library marker kkossev.thermostatLib, line 105
    //logDebug "setHeatingSetpoint temperature = ${temperature}  as int = ${temperature as int} (previousSetpointt = ${previousSetpoint})" // library marker kkossev.thermostatLib, line 106
    /* groovylint-disable-next-line ConstantIfExpression */ // library marker kkossev.thermostatLib, line 107
    if (true) { // library marker kkossev.thermostatLib, line 108
        //logDebug "0.5 C correction of the heating setpoint${temperature}" // library marker kkossev.thermostatLib, line 109
        //log.trace "tempDouble = ${tempDouble}" // library marker kkossev.thermostatLib, line 110
        tempDouble = (tempDouble * 2).setScale(0, RoundingMode.HALF_UP) / 2 // library marker kkossev.thermostatLib, line 111
    } // library marker kkossev.thermostatLib, line 112
    else { // library marker kkossev.thermostatLib, line 113
        if (temperature != (temperature as int)) { // library marker kkossev.thermostatLib, line 114
            if ((temperature as double) > (previousSetpoint as double)) { // library marker kkossev.thermostatLib, line 115
                temperature = (temperature + 0.5 ) as int // library marker kkossev.thermostatLib, line 116
            } // library marker kkossev.thermostatLib, line 117
            else { // library marker kkossev.thermostatLib, line 118
                temperature = temperature as int // library marker kkossev.thermostatLib, line 119
            } // library marker kkossev.thermostatLib, line 120
            logDebug "corrected heating setpoint ${temperature}" // library marker kkossev.thermostatLib, line 121
        } // library marker kkossev.thermostatLib, line 122
        tempDouble = temperature // library marker kkossev.thermostatLib, line 123
    } // library marker kkossev.thermostatLib, line 124
    BigDecimal maxTemp = settings?.maxHeatingSetpoint ? new BigDecimal(settings.maxHeatingSetpoint) : new BigDecimal(50) // library marker kkossev.thermostatLib, line 125
    BigDecimal minTemp = settings?.minHeatingSetpoint ? new BigDecimal(settings.minHeatingSetpoint) : new BigDecimal(5) // library marker kkossev.thermostatLib, line 126
    tempBigDecimal = new BigDecimal(tempDouble) // library marker kkossev.thermostatLib, line 127
    tempBigDecimal = tempDouble.min(maxTemp).max(minTemp).setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.thermostatLib, line 128

    logDebug "setHeatingSetpoint: calling sendAttribute heatingSetpoint ${tempBigDecimal}" // library marker kkossev.thermostatLib, line 130
    sendAttribute('heatingSetpoint', tempBigDecimal as double) // library marker kkossev.thermostatLib, line 131
} // library marker kkossev.thermostatLib, line 132

// TODO - use sendThermostatEvent instead! // library marker kkossev.thermostatLib, line 134
void sendHeatingSetpointEvent(Number temperature) { // library marker kkossev.thermostatLib, line 135
    tempDouble = safeToDouble(temperature) // library marker kkossev.thermostatLib, line 136
    Map eventMap = [name: 'heatingSetpoint',  value: tempDouble, unit: '\u00B0C', type: 'physical'] // library marker kkossev.thermostatLib, line 137
    eventMap.descriptionText = "heatingSetpoint is ${tempDouble}" // library marker kkossev.thermostatLib, line 138
    if (state.states['isRefresh'] == true) { // library marker kkossev.thermostatLib, line 139
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.thermostatLib, line 140
        eventMap.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 141
    } // library marker kkossev.thermostatLib, line 142
    sendEvent(eventMap) // library marker kkossev.thermostatLib, line 143
    if (eventMap.descriptionText != null) { logInfo "${eventMap.descriptionText}" } // library marker kkossev.thermostatLib, line 144

    eventMap.name = 'thermostatSetpoint' // library marker kkossev.thermostatLib, line 146
    logDebug "sending event ${eventMap}" // library marker kkossev.thermostatLib, line 147
    sendEvent(eventMap) // library marker kkossev.thermostatLib, line 148
    updateDataValue('lastRunningMode', 'heat') // library marker kkossev.thermostatLib, line 149
} // library marker kkossev.thermostatLib, line 150

// thermostat capability standard command // library marker kkossev.thermostatLib, line 152
// do nothing in TRV - just send an event // library marker kkossev.thermostatLib, line 153
void setCoolingSetpoint(Number temperaturePar) { // library marker kkossev.thermostatLib, line 154
    logDebug "setCoolingSetpoint(${temperaturePar}) called!" // library marker kkossev.thermostatLib, line 155
    /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.thermostatLib, line 156
    BigDecimal temperature = Math.round(temperaturePar * 2) / 2 // library marker kkossev.thermostatLib, line 157
    String descText = "coolingSetpoint is set to ${temperature} \u00B0C" // library marker kkossev.thermostatLib, line 158
    sendEvent(name: 'coolingSetpoint', value: temperature, unit: '\u00B0C', descriptionText: descText, type: 'digital') // library marker kkossev.thermostatLib, line 159
    logInfo "${descText}" // library marker kkossev.thermostatLib, line 160
} // library marker kkossev.thermostatLib, line 161

public void sendThermostatEvent(Map eventMap, final boolean isDigital = false) { // library marker kkossev.thermostatLib, line 163
    if (eventMap.descriptionText == null) { eventMap.descriptionText = "${eventName} is ${value}" } // library marker kkossev.thermostatLib, line 164
    if (eventMap.type == null) { eventMap.type = isDigital == true ? 'digital' : 'physical' } // library marker kkossev.thermostatLib, line 165
    if (state.states['isRefresh'] == true) { // library marker kkossev.thermostatLib, line 166
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.thermostatLib, line 167
        eventMap.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 168
    } // library marker kkossev.thermostatLib, line 169
    sendEvent(eventMap) // library marker kkossev.thermostatLib, line 170
    logInfo "${eventMap.descriptionText}" // library marker kkossev.thermostatLib, line 171
} // library marker kkossev.thermostatLib, line 172

private void sendEventMap(final Map event, final boolean isDigital = false) { // library marker kkossev.thermostatLib, line 174
    if (event.descriptionText == null) { // library marker kkossev.thermostatLib, line 175
        event.descriptionText = "${event.name} is ${event.value} ${event.unit ?: ''}" // library marker kkossev.thermostatLib, line 176
    } // library marker kkossev.thermostatLib, line 177
    if (state.states['isRefresh'] == true) { // library marker kkossev.thermostatLib, line 178
        event.descriptionText += ' [refresh]' // library marker kkossev.thermostatLib, line 179
        event.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 180
    } // library marker kkossev.thermostatLib, line 181
    event.type = event.type != null ? event.type : isDigital == true ? 'digital' : 'physical' // library marker kkossev.thermostatLib, line 182
    if (event.type == 'digital') { // library marker kkossev.thermostatLib, line 183
        event.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 184
        event.descriptionText += ' [digital]' // library marker kkossev.thermostatLib, line 185
    } // library marker kkossev.thermostatLib, line 186
    sendEvent(event) // library marker kkossev.thermostatLib, line 187
    logInfo "${event.descriptionText}" // library marker kkossev.thermostatLib, line 188
} // library marker kkossev.thermostatLib, line 189

private String getDescriptionText(final String msg) { // library marker kkossev.thermostatLib, line 191
    String descriptionText = "${device.displayName} ${msg}" // library marker kkossev.thermostatLib, line 192
    if (settings?.txtEnable) { log.info "${descriptionText}" } // library marker kkossev.thermostatLib, line 193
    return descriptionText // library marker kkossev.thermostatLib, line 194
} // library marker kkossev.thermostatLib, line 195

/** // library marker kkossev.thermostatLib, line 197
 * Sets the thermostat mode based on the requested mode. // library marker kkossev.thermostatLib, line 198
 * // library marker kkossev.thermostatLib, line 199
 * if the requestedMode is supported directly in the thermostatMode attribute, it is set directly. // library marker kkossev.thermostatLib, line 200
 * Otherwise, the thermostatMode is substituted with another command, if supported by the device. // library marker kkossev.thermostatLib, line 201
 * // library marker kkossev.thermostatLib, line 202
 * @param requestedMode The mode to set the thermostat to. // library marker kkossev.thermostatLib, line 203
 */ // library marker kkossev.thermostatLib, line 204
public void setThermostatMode(final String requestedMode) { // library marker kkossev.thermostatLib, line 205
    String mode = requestedMode // library marker kkossev.thermostatLib, line 206
    boolean result = false // library marker kkossev.thermostatLib, line 207
    List nativelySupportedModesList = getAttributesMap('thermostatMode')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 208
    List systemModesList = getAttributesMap('systemMode')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 209
    List ecoModesList = getAttributesMap('ecoMode')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 210
    List emergencyHeatingModesList = getAttributesMap('emergencyHeating')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 211

    logDebug "setThermostatMode: sending setThermostatMode(${mode}). Natively supported: ${nativelySupportedModesList}" // library marker kkossev.thermostatLib, line 213

    // some TRVs require some checks and additional commands to be sent before setting the mode // library marker kkossev.thermostatLib, line 215
    final String currentMode = device.currentValue('thermostatMode') // library marker kkossev.thermostatLib, line 216
    logDebug "setThermostatMode: currentMode = ${currentMode}, switching to ${mode} ..." // library marker kkossev.thermostatLib, line 217

    switch (mode) { // library marker kkossev.thermostatLib, line 219
        case 'heat': // library marker kkossev.thermostatLib, line 220
        case 'auto': // library marker kkossev.thermostatLib, line 221
            if (device.currentValue('ecoMode') == 'on') { // library marker kkossev.thermostatLib, line 222
                logDebug 'setThermostatMode: pre-processing: switching first the eco mode off' // library marker kkossev.thermostatLib, line 223
                sendAttribute('ecoMode', 0) // library marker kkossev.thermostatLib, line 224
            } // library marker kkossev.thermostatLib, line 225
            if (device.currentValue('emergencyHeating') == 'on') { // library marker kkossev.thermostatLib, line 226
                logDebug 'setThermostatMode: pre-processing: switching first the emergencyHeating mode off' // library marker kkossev.thermostatLib, line 227
                sendAttribute('emergencyHeating', 0) // library marker kkossev.thermostatLib, line 228
            } // library marker kkossev.thermostatLib, line 229
            if ((device.currentValue('systemMode') ?: 'off') == 'off') { // library marker kkossev.thermostatLib, line 230
                logDebug 'setThermostatMode: pre-processing: switching first the systemMode on' // library marker kkossev.thermostatLib, line 231
                sendAttribute('systemMode', 'on') // library marker kkossev.thermostatLib, line 232
            } // library marker kkossev.thermostatLib, line 233
            break // library marker kkossev.thermostatLib, line 234
        case 'cool':        // TODO !!!!!!!!!! // library marker kkossev.thermostatLib, line 235
            if (!('cool' in DEVICE.supportedThermostatModes)) { // library marker kkossev.thermostatLib, line 236
                // replace cool with 'eco' mode, if supported by the device // library marker kkossev.thermostatLib, line 237
                if ('eco' in DEVICE.supportedThermostatModes) { // library marker kkossev.thermostatLib, line 238
                    logDebug 'setThermostatMode: pre-processing: switching to eco mode instead' // library marker kkossev.thermostatLib, line 239
                    mode = 'eco' // library marker kkossev.thermostatLib, line 240
                    break // library marker kkossev.thermostatLib, line 241
                } // library marker kkossev.thermostatLib, line 242
                else if ('off' in DEVICE.supportedThermostatModes) { // library marker kkossev.thermostatLib, line 243
                    logDebug 'setThermostatMode: pre-processing: switching to off mode instead' // library marker kkossev.thermostatLib, line 244
                    mode = 'off' // library marker kkossev.thermostatLib, line 245
                    break // library marker kkossev.thermostatLib, line 246
                } // library marker kkossev.thermostatLib, line 247
                else if (device.currentValue('ecoMode') != null) { // library marker kkossev.thermostatLib, line 248
                    // BRT-100 has a dediceted 'ecoMode' command   // TODO - check how to switch BRT-100 low temp protection mode (5 degrees) ? // library marker kkossev.thermostatLib, line 249
                    logDebug "setThermostatMode: pre-processing: setting eco mode on (${settings.ecoTemp} &degC)" // library marker kkossev.thermostatLib, line 250
                    sendAttribute('ecoMode', 1) // library marker kkossev.thermostatLib, line 251
                } // library marker kkossev.thermostatLib, line 252
                else { // library marker kkossev.thermostatLib, line 253
                    logDebug "setThermostatMode: pre-processing: switching to 'cool' mode is not supported by this device!" // library marker kkossev.thermostatLib, line 254
                    return // library marker kkossev.thermostatLib, line 255
                } // library marker kkossev.thermostatLib, line 256
            } // library marker kkossev.thermostatLib, line 257
            break // library marker kkossev.thermostatLib, line 258
        case 'emergency heat':     // TODO for Aqara and Sonoff TRVs // library marker kkossev.thermostatLib, line 259
            if ('emergency heat' in nativelySupportedModesList) { // library marker kkossev.thermostatLib, line 260
                break // library marker kkossev.thermostatLib, line 261
            } // library marker kkossev.thermostatLib, line 262
            // look for a dedicated 'emergencyMode' deviceProfile attribute       (BRT-100) // library marker kkossev.thermostatLib, line 263
            if ('on' in emergencyHeatingModesList)  { // library marker kkossev.thermostatLib, line 264
                logInfo "setThermostatMode: pre-processing: switching the emergencyMode mode on for (${settings.emergencyHeatingTime} seconds )" // library marker kkossev.thermostatLib, line 265
                sendAttribute('emergencyHeating', 'on') // library marker kkossev.thermostatLib, line 266
                return // library marker kkossev.thermostatLib, line 267
            } // library marker kkossev.thermostatLib, line 268
            break // library marker kkossev.thermostatLib, line 269
        case 'eco': // library marker kkossev.thermostatLib, line 270
            if (device.hasAttribute('ecoMode')) {   // changed 06/16/2024 : was : (device.currentValue('ecoMode') != null)  { // library marker kkossev.thermostatLib, line 271
                logDebug 'setThermostatMode: pre-processing: switching the eco mode on' // library marker kkossev.thermostatLib, line 272
                sendAttribute('ecoMode', 1) // library marker kkossev.thermostatLib, line 273
                return // library marker kkossev.thermostatLib, line 274
            } // library marker kkossev.thermostatLib, line 275
            else { // library marker kkossev.thermostatLib, line 276
                logWarn "setThermostatMode: pre-processing: switching to 'eco' mode is not supported by this device!" // library marker kkossev.thermostatLib, line 277
                return // library marker kkossev.thermostatLib, line 278
            } // library marker kkossev.thermostatLib, line 279
            break // library marker kkossev.thermostatLib, line 280
        case 'off':     // OK! // library marker kkossev.thermostatLib, line 281
            if ('off' in nativelySupportedModesList) { // library marker kkossev.thermostatLib, line 282
                break // library marker kkossev.thermostatLib, line 283
            } // library marker kkossev.thermostatLib, line 284
            logDebug "setThermostatMode: pre-processing: switching to 'off' mode" // library marker kkossev.thermostatLib, line 285
            // if the 'off' mode is not directly supported, try substituting it with 'eco' mode // library marker kkossev.thermostatLib, line 286
            if ('eco' in nativelySupportedModesList) { // library marker kkossev.thermostatLib, line 287
                logDebug 'setThermostatMode: pre-processing: switching to eco mode instead' // library marker kkossev.thermostatLib, line 288
                mode = 'eco' // library marker kkossev.thermostatLib, line 289
                break // library marker kkossev.thermostatLib, line 290
            } // library marker kkossev.thermostatLib, line 291
            // look for a dedicated 'ecoMode' deviceProfile attribute       (BRT-100) // library marker kkossev.thermostatLib, line 292
            if ('on' in ecoModesList)  { // library marker kkossev.thermostatLib, line 293
                logDebug 'setThermostatMode: pre-processing: switching the eco mode on' // library marker kkossev.thermostatLib, line 294
                sendAttribute('ecoMode', 'on') // library marker kkossev.thermostatLib, line 295
                return // library marker kkossev.thermostatLib, line 296
            } // library marker kkossev.thermostatLib, line 297
            // look for a dedicated 'systemMode' attribute with map 'off' (Aqara E1) // library marker kkossev.thermostatLib, line 298
            if ('off' in systemModesList)  { // library marker kkossev.thermostatLib, line 299
                logDebug 'setThermostatMode: pre-processing: switching the systemMode off' // library marker kkossev.thermostatLib, line 300
                sendAttribute('systemMode', 'off') // library marker kkossev.thermostatLib, line 301
                return // library marker kkossev.thermostatLib, line 302
            } // library marker kkossev.thermostatLib, line 303
            break // library marker kkossev.thermostatLib, line 304
        default: // library marker kkossev.thermostatLib, line 305
            logWarn "setThermostatMode: pre-processing: unknown mode ${mode}" // library marker kkossev.thermostatLib, line 306
            break // library marker kkossev.thermostatLib, line 307
    } // library marker kkossev.thermostatLib, line 308

    // try using the standard thermostat capability to switch to the selected new mode // library marker kkossev.thermostatLib, line 310
    result = sendAttribute('thermostatMode', mode) // library marker kkossev.thermostatLib, line 311
    logTrace "setThermostatMode: sendAttribute returned ${result}" // library marker kkossev.thermostatLib, line 312
    if (result == true) { return } // library marker kkossev.thermostatLib, line 313

    // post-process mode switching for some TRVs // library marker kkossev.thermostatLib, line 315
    switch (mode) { // library marker kkossev.thermostatLib, line 316
        case 'cool' : // library marker kkossev.thermostatLib, line 317
        case 'heat' : // library marker kkossev.thermostatLib, line 318
        case 'auto' : // library marker kkossev.thermostatLib, line 319
        case 'off' : // library marker kkossev.thermostatLib, line 320
        case 'eco' : // library marker kkossev.thermostatLib, line 321
            logTrace "setThermostatMode: post-processing: no post-processing required for mode ${mode}" // library marker kkossev.thermostatLib, line 322
            break // library marker kkossev.thermostatLib, line 323
        case 'emergency heat' : // library marker kkossev.thermostatLib, line 324
            logDebug "setThermostatMode: post-processing: setting emergency heat mode on (${settings.emergencyHeatingTime} minutes)" // library marker kkossev.thermostatLib, line 325
            sendAttribute('emergencyHeating', 1) // library marker kkossev.thermostatLib, line 326
            break // library marker kkossev.thermostatLib, line 327
        default : // library marker kkossev.thermostatLib, line 328
            logWarn "setThermostatMode: post-processing: unsupported thermostat mode '${mode}'" // library marker kkossev.thermostatLib, line 329
            break // library marker kkossev.thermostatLib, line 330
    } // library marker kkossev.thermostatLib, line 331
    return // library marker kkossev.thermostatLib, line 332
} // library marker kkossev.thermostatLib, line 333

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.thermostatLib, line 335
void sendSupportedThermostatModes(boolean debug = false) { // library marker kkossev.thermostatLib, line 336
    List<String> supportedThermostatModes = [] // library marker kkossev.thermostatLib, line 337
    supportedThermostatModes = ['off', 'heat', 'auto', 'emergency heat'] // library marker kkossev.thermostatLib, line 338
    if (DEVICE.supportedThermostatModes != null) { // library marker kkossev.thermostatLib, line 339
        supportedThermostatModes = DEVICE.supportedThermostatModes // library marker kkossev.thermostatLib, line 340
    } // library marker kkossev.thermostatLib, line 341
    else { // library marker kkossev.thermostatLib, line 342
        logWarn 'sendSupportedThermostatModes: DEVICE.supportedThermostatModes is not set!' // library marker kkossev.thermostatLib, line 343
        supportedThermostatModes =  ['off', 'auto', 'heat'] // library marker kkossev.thermostatLib, line 344
    } // library marker kkossev.thermostatLib, line 345
    logInfo "supportedThermostatModes: ${supportedThermostatModes}" // library marker kkossev.thermostatLib, line 346
    sendEvent(name: 'supportedThermostatModes', value:  JsonOutput.toJson(supportedThermostatModes), isStateChange: true, type: 'digital') // library marker kkossev.thermostatLib, line 347
    if (DEVICE.supportedThermostatFanModes != null) { // library marker kkossev.thermostatLib, line 348
        sendEvent(name: 'supportedThermostatFanModes', value: JsonOutput.toJson(DEVICE.supportedThermostatFanModes), isStateChange: true, type: 'digital') // library marker kkossev.thermostatLib, line 349
    } // library marker kkossev.thermostatLib, line 350
    else { // library marker kkossev.thermostatLib, line 351
        sendEvent(name: 'supportedThermostatFanModes', value: JsonOutput.toJson(['auto', 'circulate', 'on']), isStateChange: true, type: 'digital') // library marker kkossev.thermostatLib, line 352
    } // library marker kkossev.thermostatLib, line 353
} // library marker kkossev.thermostatLib, line 354

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.thermostatLib, line 356
void standardHandleThermostatEvent(int value, boolean isDigital=false) { // library marker kkossev.thermostatLib, line 357
    logWarn 'standardHandleThermostatEvent()... NOT IMPLEMENTED!' // library marker kkossev.thermostatLib, line 358
} // library marker kkossev.thermostatLib, line 359

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.thermostatLib, line 361
private void sendDelayedThermostatEvent(Map eventMap) { // library marker kkossev.thermostatLib, line 362
    logWarn "${device.displayName} NOT IMPLEMENTED! <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.thermostatLib, line 363
} // library marker kkossev.thermostatLib, line 364

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.thermostatLib, line 366
void thermostatProcessTuyaDP(final Map descMap, int dp, int dp_id, int fncmd) { // library marker kkossev.thermostatLib, line 367
    logWarn "thermostatProcessTuyaDP()... NOT IMPLEMENTED! dp=${dp} dp_id=${dp_id} fncmd=${fncmd}" // library marker kkossev.thermostatLib, line 368
} // library marker kkossev.thermostatLib, line 369

/** // library marker kkossev.thermostatLib, line 371
 * Schedule thermostat polling // library marker kkossev.thermostatLib, line 372
 * @param intervalMins interval in seconds // library marker kkossev.thermostatLib, line 373
 */ // library marker kkossev.thermostatLib, line 374
public void scheduleThermostatPolling(final int intervalSecs) { // library marker kkossev.thermostatLib, line 375
    String cron = getCron( intervalSecs ) // library marker kkossev.thermostatLib, line 376
    logDebug "cron = ${cron}" // library marker kkossev.thermostatLib, line 377
    schedule(cron, 'autoPollThermostat') // library marker kkossev.thermostatLib, line 378
} // library marker kkossev.thermostatLib, line 379

public void unScheduleThermostatPolling() { // library marker kkossev.thermostatLib, line 381
    unschedule('autoPollThermostat') // library marker kkossev.thermostatLib, line 382
} // library marker kkossev.thermostatLib, line 383

/** // library marker kkossev.thermostatLib, line 385
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.thermostatLib, line 386
 */ // library marker kkossev.thermostatLib, line 387
public void autoPollThermostat() { // library marker kkossev.thermostatLib, line 388
    logDebug 'autoPollThermostat()...' // library marker kkossev.thermostatLib, line 389
    checkDriverVersion(state) // library marker kkossev.thermostatLib, line 390
    List<String> cmds = refreshFromDeviceProfileList() // library marker kkossev.thermostatLib, line 391
    if (cmds != null && cmds != [] ) { // library marker kkossev.thermostatLib, line 392
        sendZigbeeCommands(cmds) // library marker kkossev.thermostatLib, line 393
    } // library marker kkossev.thermostatLib, line 394
} // library marker kkossev.thermostatLib, line 395

private int getElapsedTimeFromEventInSeconds(final String eventName) { // library marker kkossev.thermostatLib, line 397
    /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.thermostatLib, line 398
    final Long now = new Date().time // library marker kkossev.thermostatLib, line 399
    final Object lastEventState = device.currentState(eventName) // library marker kkossev.thermostatLib, line 400
    logDebug "getElapsedTimeFromEventInSeconds: eventName = ${eventName} lastEventState = ${lastEventState}" // library marker kkossev.thermostatLib, line 401
    if (lastEventState == null) { // library marker kkossev.thermostatLib, line 402
        logTrace 'getElapsedTimeFromEventInSeconds: lastEventState is null, returning 0' // library marker kkossev.thermostatLib, line 403
        return 0 // library marker kkossev.thermostatLib, line 404
    } // library marker kkossev.thermostatLib, line 405
    Long lastEventStateTime = lastEventState.date.time // library marker kkossev.thermostatLib, line 406
    //def lastEventStateValue = lastEventState.value // library marker kkossev.thermostatLib, line 407
    int diff = ((now - lastEventStateTime) / 1000) as int // library marker kkossev.thermostatLib, line 408
    // convert diff to minutes and seconds // library marker kkossev.thermostatLib, line 409
    logTrace "getElapsedTimeFromEventInSeconds: lastEventStateTime = ${lastEventStateTime} diff = ${diff} seconds" // library marker kkossev.thermostatLib, line 410
    return diff // library marker kkossev.thermostatLib, line 411
} // library marker kkossev.thermostatLib, line 412

// called from pollTuya() // library marker kkossev.thermostatLib, line 414
public void sendDigitalEventIfNeeded(final String eventName) { // library marker kkossev.thermostatLib, line 415
    final Object lastEventState = device.currentState(eventName) // library marker kkossev.thermostatLib, line 416
    final int diff = getElapsedTimeFromEventInSeconds(eventName) // library marker kkossev.thermostatLib, line 417
    final String diffStr = timeToHMS(diff) // library marker kkossev.thermostatLib, line 418
    if (diff >= (settings.temperaturePollingInterval as int)) { // library marker kkossev.thermostatLib, line 419
        logDebug "pollTuya: ${eventName} was sent more than ${settings.temperaturePollingInterval} seconds ago (${diffStr}), sending digital event" // library marker kkossev.thermostatLib, line 420
        sendEventMap([name: lastEventState.name, value: lastEventState.value, unit: lastEventState.unit, type: 'digital']) // library marker kkossev.thermostatLib, line 421
    } // library marker kkossev.thermostatLib, line 422
    else { // library marker kkossev.thermostatLib, line 423
        logDebug "pollTuya: ${eventName} was sent less than ${settings.temperaturePollingInterval} seconds ago, skipping" // library marker kkossev.thermostatLib, line 424
    } // library marker kkossev.thermostatLib, line 425
} // library marker kkossev.thermostatLib, line 426

public void thermostatInitializeVars( boolean fullInit = false ) { // library marker kkossev.thermostatLib, line 428
    logDebug "thermostatInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.thermostatLib, line 429
    if (fullInit == true || state.lastThermostatMode == null) { state.lastThermostatMode = 'unknown' } // library marker kkossev.thermostatLib, line 430
    if (fullInit == true || state.lastThermostatOperatingState == null) { state.lastThermostatOperatingState = 'unknown' } // library marker kkossev.thermostatLib, line 431
    if (fullInit || settings?.temperaturePollingInterval == null) { device.updateSetting('temperaturePollingInterval', [value: TrvTemperaturePollingIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.thermostatLib, line 432
} // library marker kkossev.thermostatLib, line 433

// called from initializeVars() in the main code ... // library marker kkossev.thermostatLib, line 435
public void thermostatInitEvents(final boolean fullInit=false) { // library marker kkossev.thermostatLib, line 436
    logDebug "thermostatInitEvents()... fullInit = ${fullInit}" // library marker kkossev.thermostatLib, line 437
    if (fullInit == true) { // library marker kkossev.thermostatLib, line 438
        String descText = 'inital attribute setting' // library marker kkossev.thermostatLib, line 439
        sendSupportedThermostatModes() // library marker kkossev.thermostatLib, line 440
        sendEvent(name: 'thermostatMode', value: 'heat', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 441
        state.lastThermostatMode = 'heat' // library marker kkossev.thermostatLib, line 442
        sendEvent(name: 'thermostatFanMode', value: 'auto', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 443
        state.lastThermostatOperatingState = 'idle' // library marker kkossev.thermostatLib, line 444
        sendEvent(name: 'thermostatOperatingState', value: 'idle', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 445
        sendEvent(name: 'thermostatSetpoint', value:  20.0, unit: '\u00B0C', isStateChange: true, description: descText)        // Google Home compatibility // library marker kkossev.thermostatLib, line 446
        sendEvent(name: 'heatingSetpoint', value: 20.0, unit: '\u00B0C', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 447
        state.lastHeatingSetpoint = 20.0 // library marker kkossev.thermostatLib, line 448
        sendEvent(name: 'coolingSetpoint', value: 35.0, unit: '\u00B0C', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 449
        sendEvent(name: 'temperature', value: 18.0, unit: '\u00B0', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 450
        updateDataValue('lastRunningMode', 'heat') // library marker kkossev.thermostatLib, line 451
    } // library marker kkossev.thermostatLib, line 452
    else { // library marker kkossev.thermostatLib, line 453
        logDebug "thermostatInitEvents: fullInit = ${fullInit}" // library marker kkossev.thermostatLib, line 454
    } // library marker kkossev.thermostatLib, line 455
} // library marker kkossev.thermostatLib, line 456

/* // library marker kkossev.thermostatLib, line 458
  Reset to Factory Defaults Command (0x00) // library marker kkossev.thermostatLib, line 459
  On receipt of this command, the device resets all the attributes of all its clusters to their factory defaults. // library marker kkossev.thermostatLib, line 460
  Note that networking functionality, bindings, groups, or other persistent data are not affected by this command // library marker kkossev.thermostatLib, line 461
*/ // library marker kkossev.thermostatLib, line 462
public void factoryResetThermostat() { // library marker kkossev.thermostatLib, line 463
    logDebug 'factoryResetThermostat() called!' // library marker kkossev.thermostatLib, line 464
    List<String> cmds  = zigbee.command(0x0000, 0x00) // library marker kkossev.thermostatLib, line 465
    sendZigbeeCommands(cmds) // library marker kkossev.thermostatLib, line 466
    sendInfoEvent 'The thermostat parameters were FACTORY RESET!' // library marker kkossev.thermostatLib, line 467
    if (this.respondsTo('refreshAll')) { // library marker kkossev.thermostatLib, line 468
        runIn(3, 'refreshAll') // library marker kkossev.thermostatLib, line 469
    } // library marker kkossev.thermostatLib, line 470
} // library marker kkossev.thermostatLib, line 471

// ========================================= Virtual thermostat functions  ========================================= // library marker kkossev.thermostatLib, line 473

public void setTemperature(Number temperaturePar) { // library marker kkossev.thermostatLib, line 475
    logDebug "setTemperature(${temperature}) called!" // library marker kkossev.thermostatLib, line 476
    if (isVirtual()) { // library marker kkossev.thermostatLib, line 477
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.thermostatLib, line 478
        double temperature = Math.round(temperaturePar * 2) / 2 // library marker kkossev.thermostatLib, line 479
        String descText = "temperature is set to ${temperature} \u00B0C" // library marker kkossev.thermostatLib, line 480
        sendEvent(name: 'temperature', value: temperature, unit: '\u00B0C', descriptionText: descText, type: 'digital') // library marker kkossev.thermostatLib, line 481
        logInfo "${descText}" // library marker kkossev.thermostatLib, line 482
    } // library marker kkossev.thermostatLib, line 483
    else { // library marker kkossev.thermostatLib, line 484
        logWarn 'setTemperature: not a virtual thermostat!' // library marker kkossev.thermostatLib, line 485
    } // library marker kkossev.thermostatLib, line 486
} // library marker kkossev.thermostatLib, line 487

// TODO - not used? // library marker kkossev.thermostatLib, line 489
List<String> thermostatRefresh() { // library marker kkossev.thermostatLib, line 490
    logDebug 'thermostatRefresh()...' // library marker kkossev.thermostatLib, line 491
    return [] // library marker kkossev.thermostatLib, line 492
} // library marker kkossev.thermostatLib, line 493

// TODO - configure in the deviceProfile refresh: tag // library marker kkossev.thermostatLib, line 495
public List<String> pollThermostatCluster() { // library marker kkossev.thermostatLib, line 496
    return  zigbee.readAttribute(0x0201, [0x0000, 0x0001, /*0x0002,*/ 0x0012, 0x001B, 0x001C, 0x0029], [:], delay = 1500)      // 0x0000 = local temperature, 0x0001 = outdoor temperature, 0x0002 = occupancy, 0x0012 = heating setpoint, 0x001B = controlledSequenceOfOperation, 0x001C = system mode (enum8 ) // library marker kkossev.thermostatLib, line 497
} // library marker kkossev.thermostatLib, line 498

// TODO - configure in the deviceProfile refresh: tag // library marker kkossev.thermostatLib, line 500
public List<String> pollBatteryPercentage() { // library marker kkossev.thermostatLib, line 501
    return zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)                          // battery percentage // library marker kkossev.thermostatLib, line 502
} // library marker kkossev.thermostatLib, line 503

public List<String> pollOccupancy() { // library marker kkossev.thermostatLib, line 505
    return  zigbee.readAttribute(0x0406, 0x0000, [:], delay = 100)      // Bit 0 specifies the sensed occupancy as follows: 1 = occupied, 0 = unoccupied. This flag bit will affect the Occupancy attribute of HVAC cluster, and the operation mode. // library marker kkossev.thermostatLib, line 506
} // library marker kkossev.thermostatLib, line 507

// ~~~~~ end include (179) kkossev.thermostatLib ~~~~~

// ~~~~~ start include (166) kkossev.energyLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.energyLib, line 1
library( // library marker kkossev.energyLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Energy Library', name: 'energyLib', namespace: 'kkossev', // library marker kkossev.energyLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/energyLib.groovy', documentationLink: '', // library marker kkossev.energyLib, line 4
    version: '3.3.0' // library marker kkossev.energyLib, line 5

) // library marker kkossev.energyLib, line 7
/* // library marker kkossev.energyLib, line 8
 *  Zigbee Energy Library // library marker kkossev.energyLib, line 9
 * // library marker kkossev.energyLib, line 10
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.energyLib, line 11
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.energyLib, line 12
 * // library marker kkossev.energyLib, line 13
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.energyLib, line 14
 * // library marker kkossev.energyLib, line 15
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.energyLib, line 16
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.energyLib, line 17
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.energyLib, line 18
 * // library marker kkossev.energyLib, line 19
 * ver. 3.0.0  2024-04-06 kkossev  - added energyLib.groovy // library marker kkossev.energyLib, line 20
 * ver. 3.2.0  2024-05-24 kkossev  - CommonLib 3.2.0 allignment // library marker kkossev.energyLib, line 21
 * ver. 3.3.0  2024-06-09 kkossev  - added energy, power, voltage, current events parsing // library marker kkossev.energyLib, line 22
 * // library marker kkossev.energyLib, line 23
 *                                   TODO: add energyRefresh() // library marker kkossev.energyLib, line 24
*/ // library marker kkossev.energyLib, line 25

static String energyLibVersion()   { '3.3.0' } // library marker kkossev.energyLib, line 27
static String energyLibStamp() { '2024/06/09 6:53 PM' } // library marker kkossev.energyLib, line 28

metadata { // library marker kkossev.energyLib, line 30
    capability 'PowerMeter' // library marker kkossev.energyLib, line 31
    capability 'EnergyMeter' // library marker kkossev.energyLib, line 32
    capability 'VoltageMeasurement' // library marker kkossev.energyLib, line 33
    capability 'CurrentMeter' // library marker kkossev.energyLib, line 34
    // no attributes // library marker kkossev.energyLib, line 35
    // no commands // library marker kkossev.energyLib, line 36
    preferences { // library marker kkossev.energyLib, line 37
        // no prefrences // library marker kkossev.energyLib, line 38
    } // library marker kkossev.energyLib, line 39
} // library marker kkossev.energyLib, line 40

@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.energyLib, line 42
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.energyLib, line 43
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.energyLib, line 44
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.energyLib, line 45
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.energyLib, line 46
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.energyLib, line 47
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.energyLib, line 48
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.energyLib, line 49
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.energyLib, line 50
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.energyLib, line 51
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.energyLib, line 52
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.energyLib, line 53
@Field static final int CURRENT_SUMMATION_DELIVERED = 0x0000 // Energy // library marker kkossev.energyLib, line 54

@Field static  int    DEFAULT_REPORTING_TIME = 30 // library marker kkossev.energyLib, line 56
@Field static  int    DEFAULT_PRECISION = 3           // 3 decimal places // library marker kkossev.energyLib, line 57
@Field static  BigDecimal DEFAULT_DELTA = 0.001 // library marker kkossev.energyLib, line 58
@Field static  int    MAX_POWER_LIMIT = 999 // library marker kkossev.energyLib, line 59

void sendVoltageEvent(BigDecimal voltage, boolean isDigital=false) { // library marker kkossev.energyLib, line 61
    Map map = [:] // library marker kkossev.energyLib, line 62
    map.name = 'voltage' // library marker kkossev.energyLib, line 63
    map.value = voltage.setScale((settings?.defaultPrecision ?: DEFAULT_PRECISION) as int, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 64
    map.unit = 'V' // library marker kkossev.energyLib, line 65
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 66
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 67
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 68
    final BigDecimal lastVoltage = device.currentValue('voltage') ?: 0.0 // library marker kkossev.energyLib, line 69
    final BigDecimal  voltageThreshold = DEFAULT_DELTA // library marker kkossev.energyLib, line 70
    if (Math.abs(voltage - lastVoltage) >= voltageThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 71
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 72
        sendEvent(map) // library marker kkossev.energyLib, line 73
    } // library marker kkossev.energyLib, line 74
    else { // library marker kkossev.energyLib, line 75
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastVoltage} is less than ${voltageThreshold} V)" // library marker kkossev.energyLib, line 76
    } // library marker kkossev.energyLib, line 77
} // library marker kkossev.energyLib, line 78

void sendAmperageEvent(BigDecimal amperage, boolean isDigital=false) { // library marker kkossev.energyLib, line 80
    Map map = [:] // library marker kkossev.energyLib, line 81
    map.name = 'amperage' // library marker kkossev.energyLib, line 82
    map.value = amperage.setScale((settings?.defaultPrecision ?: DEFAULT_PRECISION) as int, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 83
    map.unit = 'A' // library marker kkossev.energyLib, line 84
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 85
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 86
    if (state.states.isRefresh  == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 87
    final BigDecimal lastAmperage = device.currentValue('amperage') ?: 0.00000001 // library marker kkossev.energyLib, line 88
    final BigDecimal amperageThreshold = DEFAULT_DELTA // library marker kkossev.energyLib, line 89
    if (Math.abs(amperage - lastAmperage ) >= amperageThreshold || state.states.isRefresh  == true) { // library marker kkossev.energyLib, line 90
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 91
        sendEvent(map) // library marker kkossev.energyLib, line 92
    } // library marker kkossev.energyLib, line 93
    else { // library marker kkossev.energyLib, line 94
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastAmperage} is less than ${amperageThreshold} mA)" // library marker kkossev.energyLib, line 95
    } // library marker kkossev.energyLib, line 96
} // library marker kkossev.energyLib, line 97

void sendPowerEvent(BigDecimal power, boolean isDigital=false) { // library marker kkossev.energyLib, line 99
    Map map = [:] // library marker kkossev.energyLib, line 100
    map.name = 'power' // library marker kkossev.energyLib, line 101
    map.value = power.setScale((settings?.defaultPrecision ?: DEFAULT_PRECISION) as int, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 102
    map.unit = 'W' // library marker kkossev.energyLib, line 103
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 104
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 105
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 106
    final BigDecimal lastPower = device.currentValue('power') ?: 0.00000001 // library marker kkossev.energyLib, line 107
    final BigDecimal powerThreshold = DEFAULT_DELTA // library marker kkossev.energyLib, line 108
    if (power  > MAX_POWER_LIMIT) { // library marker kkossev.energyLib, line 109
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (exceeds maximum power cap ${MAX_POWER_LIMIT} W)" // library marker kkossev.energyLib, line 110
        return // library marker kkossev.energyLib, line 111
    } // library marker kkossev.energyLib, line 112
    if (Math.abs(power - lastPower ) >= powerThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 113
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 114
        sendEvent(map) // library marker kkossev.energyLib, line 115
    } // library marker kkossev.energyLib, line 116
    else { // library marker kkossev.energyLib, line 117
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastPower} is less than ${powerThreshold} W)" // library marker kkossev.energyLib, line 118
    } // library marker kkossev.energyLib, line 119
} // library marker kkossev.energyLib, line 120

void sendFrequencyEvent(BigDecimal frequency, boolean isDigital=false) { // library marker kkossev.energyLib, line 122
    Map map = [:] // library marker kkossev.energyLib, line 123
    map.name = 'frequency' // library marker kkossev.energyLib, line 124
    map.value = frequency.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 125
    map.unit = 'Hz' // library marker kkossev.energyLib, line 126
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 127
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 128
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 129
    final BigDecimal lastFrequency = device.currentValue('frequency') ?: 0.00000001 // library marker kkossev.energyLib, line 130
    final BigDecimal frequencyThreshold = 0.1 // library marker kkossev.energyLib, line 131
    if (Math.abs(frequency - lastFrequency) >= frequencyThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 132
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 133
        sendEvent(map) // library marker kkossev.energyLib, line 134
    } // library marker kkossev.energyLib, line 135
    else { // library marker kkossev.energyLib, line 136
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastFrequency} is less than ${frequencyThreshold} Hz)" // library marker kkossev.energyLib, line 137
    } // library marker kkossev.energyLib, line 138
} // library marker kkossev.energyLib, line 139

void sendPowerFactorEvent(BigDecimal pf, boolean isDigital=false) { // library marker kkossev.energyLib, line 141
    Map map = [:] // library marker kkossev.energyLib, line 142
    map.name = 'powerFactor' // library marker kkossev.energyLib, line 143
    map.value = pf.setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 144
    map.unit = '%' // library marker kkossev.energyLib, line 145
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 146
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 147
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 148
    final BigDecimal lastPF = device.currentValue('powerFactor') ?: 0.00000001 // library marker kkossev.energyLib, line 149
    final BigDecimal powerFactorThreshold = 0.01 // library marker kkossev.energyLib, line 150
    if (Math.abs(pf - lastPF) >= powerFactorThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 151
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 152
        sendEvent(map) // library marker kkossev.energyLib, line 153
    } // library marker kkossev.energyLib, line 154
    else { // library marker kkossev.energyLib, line 155
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastFrequency} is less than ${powerFactorThreshold} %)" // library marker kkossev.energyLib, line 156
    } // library marker kkossev.energyLib, line 157
} // library marker kkossev.energyLib, line 158

void sendEnergyEvent(BigDecimal energy_total, boolean isDigital=false) { // library marker kkossev.energyLib, line 160
    BigDecimal energy = energy_total // library marker kkossev.energyLib, line 161
    Map map = [:] // library marker kkossev.energyLib, line 162
    logDebug "energy_total=${energy_total}" // library marker kkossev.energyLib, line 163
    map.name = 'energy' // library marker kkossev.energyLib, line 164
    map.value = energy // library marker kkossev.energyLib, line 165
    map.unit = 'kWh' // library marker kkossev.energyLib, line 166
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 167
    if (isDigital == true) { map.isStateChange = true  } // library marker kkossev.energyLib, line 168
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 169
    if (state.states.isRefreshRequest == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 170
    BigDecimal lastEnergy = device.currentValue('energy') ?: 0.00000001 // library marker kkossev.energyLib, line 171
    if (lastEnergy  != energy || state.states.isRefreshRequest == true || isDigital == true) { // library marker kkossev.energyLib, line 172
        sendEvent(map) // library marker kkossev.energyLib, line 173
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 174
    } // library marker kkossev.energyLib, line 175
    else { // library marker kkossev.energyLib, line 176
        logDebug "${device.displayName} ${map.name} is ${map.value} ${map.unit} (no change)" // library marker kkossev.energyLib, line 177
    } // library marker kkossev.energyLib, line 178
} // library marker kkossev.energyLib, line 179

// parse the electrical measurement cluster 0x0B04 // library marker kkossev.energyLib, line 181
boolean standardParseElectricalMeasureCluster(Map descMap) { // library marker kkossev.energyLib, line 182
    if (descMap.value == null || descMap.value == 'FFFF') { return true } // invalid or unknown value // library marker kkossev.energyLib, line 183
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.energyLib, line 184
    int attributeInt = hexStrToUnsignedInt(descMap.attrId) // library marker kkossev.energyLib, line 185
    logTrace "standardParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}" // library marker kkossev.energyLib, line 186
    switch (attributeInt) { // library marker kkossev.energyLib, line 187
        case ACTIVE_POWER_ID:   // 0x050B // library marker kkossev.energyLib, line 188
            BigDecimal power = new BigDecimal(value).divide(new BigDecimal(1/*0*/)) // library marker kkossev.energyLib, line 189
            sendPowerEvent(power) // library marker kkossev.energyLib, line 190
            break // library marker kkossev.energyLib, line 191
        case RMS_CURRENT_ID:    // 0x0508 // library marker kkossev.energyLib, line 192
            BigDecimal current = new BigDecimal(value).divide(new BigDecimal(1000)) // library marker kkossev.energyLib, line 193
            sendAmperageEvent(current) // library marker kkossev.energyLib, line 194
            break // library marker kkossev.energyLib, line 195
        case RMS_VOLTAGE_ID:    // 0x0505 // library marker kkossev.energyLib, line 196
            BigDecimal voltage = new BigDecimal(value).divide(new BigDecimal(10)) // library marker kkossev.energyLib, line 197
            sendVoltageEvent(voltage) // library marker kkossev.energyLib, line 198
            break // library marker kkossev.energyLib, line 199
        case AC_FREQUENCY_ID:   // 0x0300 // library marker kkossev.energyLib, line 200
            BigDecimal frequency = new BigDecimal(value).divide(new BigDecimal(10)) // library marker kkossev.energyLib, line 201
            sendFrequencyEvent(frequency) // library marker kkossev.energyLib, line 202
            break // library marker kkossev.energyLib, line 203
        case 0x0800:    // AC Alarms Mask // library marker kkossev.energyLib, line 204
            logDebug "standardParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} AC Alarms Mask value=${value}" // library marker kkossev.energyLib, line 205
            break // library marker kkossev.energyLib, line 206
        case 0x0802:    // AC Current Overload // library marker kkossev.energyLib, line 207
            logDebug "standardParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} AC Current Overload value=${value / 1000} (raw: ${value})" // library marker kkossev.energyLib, line 208
            break // library marker kkossev.energyLib, line 209
        case [AC_VOLTAGE_MULTIPLIER_ID, AC_VOLTAGE_DIVISOR_ID, AC_CURRENT_MULTIPLIER_ID, AC_CURRENT_DIVISOR_ID, AC_POWER_MULTIPLIER_ID, AC_POWER_DIVISOR_ID].contains(descMap.attrId): // library marker kkossev.energyLib, line 210
            logDebug "standardParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}" // library marker kkossev.energyLib, line 211
            break // library marker kkossev.energyLib, line 212
        default: // library marker kkossev.energyLib, line 213
            logDebug "standardParseElectricalMeasureCluster: (0x0B04) <b>not parsed</b> attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}" // library marker kkossev.energyLib, line 214
            return false    // not parsed // library marker kkossev.energyLib, line 215
    } // library marker kkossev.energyLib, line 216
    return true // parsed and processed // library marker kkossev.energyLib, line 217
} // library marker kkossev.energyLib, line 218

// parse the metering cluster 0x0702 // library marker kkossev.energyLib, line 220
boolean standardParseMeteringCluster(Map descMap) { // library marker kkossev.energyLib, line 221
    if (descMap.value == null || descMap.value == 'FFFF') { return true } // invalid or unknown value // library marker kkossev.energyLib, line 222
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.energyLib, line 223
    int attributeInt = hexStrToUnsignedInt(descMap.attrId) // library marker kkossev.energyLib, line 224
    logTrace "standardParseMeteringCluster: (0x0702)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}" // library marker kkossev.energyLib, line 225
    switch (attributeInt) { // library marker kkossev.energyLib, line 226
        case CURRENT_SUMMATION_DELIVERED:   // 0x0000 // library marker kkossev.energyLib, line 227
            BigDecimal energyScaled = new BigDecimal(value).divide(new BigDecimal(10/*00*/)) // library marker kkossev.energyLib, line 228
            sendEnergyEvent(energyScaled) // library marker kkossev.energyLib, line 229
            break // library marker kkossev.energyLib, line 230
        default: // library marker kkossev.energyLib, line 231
            logWarn "standardParseMeteringCluster: (0x0702) <b>not parsed</b> attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}" // library marker kkossev.energyLib, line 232
            return false    // not parsed // library marker kkossev.energyLib, line 233
    } // library marker kkossev.energyLib, line 234
    return true // parsed and processed // library marker kkossev.energyLib, line 235
} // library marker kkossev.energyLib, line 236

void energyInitializeVars( boolean fullInit = false ) { // library marker kkossev.energyLib, line 238
    logDebug "energyInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.energyLib, line 239
    if (fullInit || settings?.defaultPrecision == null) { device.updateSetting('defaultPrecision', [value: DEFAULT_PRECISION, type: 'number']) } // library marker kkossev.energyLib, line 240
} // library marker kkossev.energyLib, line 241

// ~~~~~ end include (166) kkossev.energyLib ~~~~~
