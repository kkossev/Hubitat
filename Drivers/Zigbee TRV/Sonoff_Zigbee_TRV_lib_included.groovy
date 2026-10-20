/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateStringLiteral, ImplicitClosureParameter, MethodCount, MethodSize, NglParseError, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessarySetter, UnusedImport */
/**
 *  Sonoff Zigbee TRV - Device Driver for Hubitat Elevation
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
 * ver. 3.3.0  2024-06-08 kkossev  - searate new driver for TRVZB thermostat
 * ver. 3.3.1  2024-07-18 kkossev  - TimeSync() magic;
 * ver. 3.4.0  2024-10-05 kkossev  - added to HPM
 * ver. 3.4.1  2025-03-06 kkossev  - healthCheck by pinging the TRV
 * ver. 3.5.0  2025-04-08 kkossev  - urgent fix for java.lang.CloneNotSupportedException
 * ver. 3.5.2  2025-05-25 kkossev  - HE platfrom version 2.4.1.x decimal preferences patch/workaround.
 * ver. 3.6.0  2025-11-01 kkossev  - autoPollThermostat() fixes; added reporting configuration; added refreshAll() command (inccluding valveOpeningDegree and valveClosingDegree)
 * ver. 3.6.1  2025-12-06 kkossev  - (dev. branch) added [physical] or [digital] type to heatingSetpoint events; added ignoreDuplicatedZigbeeMessages setting
 * ver. 3.6.2  2026-10-20 kkossev  - (dev. branch) added SONOFF TRV-ZBT core thermostat support and display rotation.
 *
 *                                   TODO: 
 */

static String version() { '3.6.2' }
static String timeStamp() { '2026/10/20 10:22 PM' }

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
        name: 'Sonoff Zigbee TRV',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee%20TRV/Sonoff_Zigbee_TRV_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {

        // TODO - add all other models attributes possible values
        attribute 'antiFreeze', 'enum', ['off', 'on']               // Tuya Saswell, AVATTO
        attribute 'batteryVoltage', 'number'
        attribute 'calibrated', 'enum', ['false', 'true']           // Aqara E1
        attribute 'calibrationTemp', 'number'                       // BRT-100, Sonoff
        attribute 'childLock', 'enum', ['off', 'on']                // BRT-100, Aqara E1, Sonoff, AVATTO
        attribute 'frostProtectionTemperature', 'number'            // Sonoff
        attribute 'hysteresis', 'number'                            // AVATTO, Virtual thermostat
        attribute 'maxHeatingSetpoint', 'number'                    // BRT-100, Sonoff, AVATTO
        attribute 'minHeatingSetpoint', 'number'                    // BRT-100, Sonoff, AVATTO
        attribute 'windowOpenDetection', 'enum', ['off', 'on']      // BRT-100, Aqara E1, Sonoff
        attribute 'screenDirection', 'enum', ['0°', '90°', '180°', '270°'] // TRV-ZBT

        command 'setThermostatMode', [[name: 'thermostat mode (not all are available!)', type: 'ENUM', constraints: ['--- select ---'] + AllPossibleThermostatModesOpts.options.values() as List<String>]]
        command 'setTemperature', ['NUMBER']                        // Virtual thermostat
        command 'refreshAll', [[name: 'Refresh All Attributes', description: 'Refresh all defined device attributes']]
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
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.'
    }
}

@Field static final Map deviceProfilesV3 = [

    // Sonoff TRVZB : https://github.com/Koenkk/zigbee-herdsman-converters/blob/b89af815cf41bd309d63f3f01d352dbabcf4ebb2/src/devices/sonoff.ts#L454
    //                https://github.com/photomoose/zigbee-herdsman-converters/blob/59f927ef0f152268125426854bd65ae6b963c99a/src/devices/sonoff.ts
    //                https://github.com/Koenkk/zigbee2mqtt/issues/19269
    //                https://github.com/Koenkk/zigbee-herdsman-converters/pull/6469
    // fromZigbee:  https://github.com/Koenkk/zigbee-herdsman-converters/blob/b89af815cf41bd309d63f3f01d352dbabcf4ebb2/src/converters/fromZigbee.ts#L44
    // toZigbee:    https://github.com/Koenkk/zigbee-herdsman-converters/blob/b89af815cf41bd309d63f3f01d352dbabcf4ebb2/src/converters/toZigbee.ts#L1516
    //
    'SONOFF_TRV'   : [
            description   : 'Sonoff TRVZB',
            device        : [manufacturers: ['SONOFF'], type: 'TRV', powerSource: 'battery', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true, 'BatteryVoltage':true],

            preferences   : ['childLock':'0xFC11:0x0000', 'windowOpenDetection':'0xFC11:0x6000', 'frostProtectionTemperature':'0xFC11:0x6002', /*'valveOpeningDegree':'0xFC11:0x600B', 'valveClosingDegree':'0xFC11:0x600C',*/ 'minHeatingSetpoint':'0x0201:0x0015', 'maxHeatingSetpoint':'0x0201:0x0016', 'calibrationTemp':'0x0201:0x0010' ],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0006,0020,0201,FC57,FC11', outClusters:'000A,0019', model:'TRVZB', manufacturer:'SONOFF', deviceJoinName: 'Sonoff TRVZB']
            ],
            commands      : [testT:'testT',initializeSonoff:'initializeSonoff', 'printFingerprints':'printFingerprints', 'autoPollThermostat':'autoPollThermostat', 'resetStats':'resetStats', 'refresh':'refresh', 'refreshAll':'refreshAll', 'initialize':'initialize', 'configureTRV':'configureTRV', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [:],
            attributes    : [   // TODO - configure the reporting for the 0x0201:0x0000 temperature !  (300..3600)
                [at:'0x0201:0x0000',  name:'temperature',           type:'decimal', dt:'0x29', rw:'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'Local temperature'],
                [at:'0x0201:0x0002',  name:'occupancy',             type:'enum',    dt:'0x18', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'unoccupied', 1: 'occupied'], unit:'',  description:'Occupancy'],
                [at:'0x0201:0x0003',  name:'absMinHeatingSetpointLimit',  type:'decimal', dt:'0x29', rw:'ro', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'Abs Min Heat Setpoint Limit'],
                [at:'0x0201:0x0004',  name:'absMaxHeatingSetpointLimit',  type:'decimal', dt:'0x29', rw:'ro', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'Abs Max Heat Setpoint Limit'],
                [at:'0x0201:0x0010',  name:'calibrationTemp',  preProc:'signedInt',     type:'decimal', dt:'0x28', rw:'rw', min:-7.0,  max:7.0, defVal:0.0, step:0.2, scale:10,  unit:'°C', title: '<b>Local Temperature Calibration</b>', description:'Room temperature calibration'],
                [at:'0x0201:0x0012',  name:'heatingSetpoint',       type:'decimal', dt:'0x29', rw:'rw', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Heating Setpoint</b>',      description:'Occupied heating setpoint'],
                // minHeatingSetpoint and maxHeatingSetpoint don't seem to have any effect on the device operation
                [at:'0x0201:0x0015',  name:'minHeatingSetpoint',    type:'decimal', dt:'0x29', rw:'rw', min:4.0,  max:35.0, defVal:4.0, step:0.5, scale:100,  unit:'°C', title: '<b>Min Heating Setpoint</b>', description:'Min Heating Setpoint Limit'],
                [at:'0x0201:0x0016',  name:'maxHeatingSetpoint',    type:'decimal', dt:'0x29', rw:'rw', min:4.0,  max:35.0, defVal:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Max Heating Setpoint</b>', description:'Max Heating Setpoint Limit'],
                [at:'0x0201:0x001A',  name:'remoteSensing',         type:'enum',    dt:'0x18', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',  title: '<b>Remote Sensing<</b>', description:'Remote Sensing'],
                [at:'0x0201:0x001B',  name:'termostatRunningState', type:'enum',    dt:'0x30', rw:'rw', min:0,    max:2,    step:1,  scale:1,    map:[0: 'off', 1: 'heat', 2: 'unknown'], unit:'',  description:'termostatRunningState (relay on/off status)'],      //  nothing happens when WRITING ????
                [at:'0x0201:0x001C',  name:'thermostatMode',        type:'enum',    dt:'0x30', rw:'rw', min:0,    max:4,    step:1,  scale:1,    map:[0: 'off', 1: 'auto', 2: 'invalid', 3: 'invalid', 4: 'heat'], unit:'', title: '<b>System Mode</b>',  description:'Thermostat Mode'],
                [at:'0x0201:0x001E',  name:'thermostatRunMode',     type:'enum',    dt:'0x30', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heat'], unit:'', title: '<b>Thermostat Run Mode</b>',   description:'Thermostat run mode'],
                [at:'0x0201:0x0020',  name:'startOfWeek',           type:'enum',    dt:'0x30', rw:'ro', min:0,    max:6,    step:1,  scale:1,    map:[0: 'Sun', 1: 'Mon', 2: 'Tue', 3: 'Wed', 4: 'Thu', 5: 'Fri', 6: 'Sat'], unit:'',  description:'Start of week'],
                [at:'0x0201:0x0021',  name:'numWeeklyTransitions',  type:'number',  dt:'0x20', rw:'ro', min:0,    max:255,  step:1,  scale:1,    unit:'',  description:'Number Of Weekly Transitions'],
                [at:'0x0201:0x0022',  name:'numDailyTransitions',   type:'number',  dt:'0x20', rw:'ro', min:0,    max:255,  step:1,  scale:1,    unit:'',  description:'Number Of Daily Transitions'],
                [at:'0x0201:0x0025',  name:'thermostatProgrammingOperationMode', type:'enum',  dt:'0x18', rw:'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'mode1', 1: 'mode2'], unit:'',  title: '<b>Thermostat Programming Operation Mode/b>', description:'Thermostat programming operation mode'],  // nothing happens when WRITING ????
                [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum', dt:'0x19', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heating'], unit:'',  description:'termostatRunningState (relay on/off status)'],   // read only!
                // https://github.com/photomoose/zigbee-herdsman-converters/blob/master/src/devices/sonoff.ts#L1200
                [at:'0x0006:0x0000',  name:'onOffReport',          type:'number',  dt: '0x10', rw: 'ro', min:0,    max:255,  step:1,  scale:1,   unit:'',  description:'TRV on/off report'],     // read only, 00 = off; 01 - thermostat is on
                [at:'0xFC11:0x0000',  name:'childLock',             type:'enum',    dt: '0x10', rw: 'rw', min:0,    max:1,  defVal:'0', step:1,  scale:1,   map:[0: 'off', 1: 'on'], unit:'',   title: '<b>Child Lock</b>',   description:'Child lock<br>unlocked/locked'],
                [at:'0xFC11:0x6000',  name:'windowOpenDetection',   type:'enum',    dt: '0x10', rw: 'rw', min:0,    max:1,  defVal:'0', step:1,  scale:1,   map:[0: 'off', 1: 'on'], unit:'',   title: '<b>Open Window Detection</b>',   description:'Automatically turns off the radiator when local temperature drops by more than 1.5°C in 4.5 minutes.'],
                [at:'0xFC11:0x6002',  name:'frostProtectionTemperature', type:'decimal',  dt: '0x29', rw: 'rw', min:4.0,    max:35.0,  defVal:7.0, step:0.5,  scale:100,   unit:'°C',   title: '<b>Frost Protection Temperature</b>',   description:'Minimum temperature at which to automatically turn on the radiator, if system mode is off, to prevent pipes freezing.'],
                [at:'0xFC11:0x6003',  name:'idleSteps ',            type:'number',  dt: '0x21', rw: 'ro', min:0,    max:9999, step:1,  scale:1,   unit:'', description:'Number of steps used for calibration (no-load steps)'],
                [at:'0xFC11:0x6004',  name:'closingSteps',          type:'number',  dt: '0x21', rw: 'ro', min:0,    max:9999, step:1,  scale:1,   unit:'', description:'Number of steps it takes to close the valve'],
                [at:'0xFC11:0x6005',  name:'valve_opening_limit_voltage',  type:'decimal',  dt: '0x21', rw: 'ro', min:0,    max:9999, step:1,  scale:1000,   unit:'V', description:'Valve opening limit voltage'],
                [at:'0xFC11:0x6006',  name:'valve_closing_limit_voltage',  type:'decimal',  dt: '0x21', rw: 'ro', min:0,    max:9999, step:1,  scale:1000,   unit:'V', description:'Valve closing limit voltage'],
                [at:'0xFC11:0x6007',  name:'valve_motor_running_voltage',  type:'decimal',  dt: '0x21', rw: 'ro', min:0,    max:9999, step:1,  scale:1000,   unit:'V', description:'Valve motor running voltage'],
                [at:'0xFC11:0x6008',  name:'unknown1',              type:'number',  dt: '0x20', rw: 'rw', min:0,    max:255, step:1,  scale:1,   unit:'', description:'unknown1 (0xFC11:0x6008)/i>'],
                [at:'0xFC11:0x6009',  name:'heatingSetpoint_FC11',  type:'decimal',  dt: '0x29', rw: 'rw', min:4.0,  max:35.0, step:1,  scale:100,   unit:'°C', title: '<b>Heating Setpoint</b>',      description:'Occupied heating setpoint'],
                [at:'0xFC11:0x600A',  name:'unknown2',              type:'number',  dt: '0x29', rw: 'rw', min:0,    max:9999, step:1,  scale:1,   unit:'', description:'unknown2 (0xFC11:0x600A)/i>'],
                // Additional Z2M-aligned attributes
                [at:'0xFC11:0x600B',  name:'valveOpeningDegree',    type:'number',  dt: '0x20', rw: 'rw', min:0,    max:100,  step:1,  scale:1,   unit:'%',  title: '<b>Valve Opening Degree</b>', description:'Valve opening position (percentage) control. If set to 100%, valve is fully opened. If set to 0%, valve is fully closed. Default is 100%.'],
                [at:'0xFC11:0x600C',  name:'valveClosingDegree',    type:'number',  dt: '0x20', rw: 'rw', min:0,    max:100,  step:1,  scale:1,   unit:'%',  title: '<b>Valve Closing Degree</b>', description:'Valve closed position (percentage) control. If set to 100%, valve is fully closed. If set to 0%, valve is fully opened. Default is 100%.'],
                [at:'0xFC11:0x2000',  name:'tamper',                type:'enum',    dt: '0x20', rw: 'ro', min:0,    max:1,    step:1,  scale:1,   map:[0: 'clear', 1: 'tampered'], unit:'', description:'Tamper-proof status'],
                [at:'0xFC11:0x2001',  name:'illumination',          type:'enum',    dt: '0x20', rw: 'ro', min:0,    max:1,    step:1,  scale:1,   map:[0: 'dim', 1: 'bright'], unit:'', description:'Illumination sensor status - only updated when occupancy is detected'],
            ],
            refresh: [/*'temperature', 'heatingSetpoint', 'thermostatMode', 'thermostatOperatingState'*/ 'refreshSonoff'],
            deviceJoinName: 'Sonoff TRVZB',
            configuration : [:]
    ],

    // Protocol: https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/devices/sonoff.ts
    // Fingerprint: bobbles, https://community.hubitat.com/t/-/128886/98 (softwareBuild 1.0.9).
    // Separate from SONOFF_TRV: Gen2 settings and supported ranges differ.
    'SONOFF_TRV_ZBT' : [
            description   : 'Sonoff TRV-ZBT',
            device        : [manufacturers: ['SONOFF'], type: 'TRV', powerSource: 'battery'],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint': true, 'ThermostatMode': true, 'BatteryVoltage': true],
            preferences   : ['childLock':'0xFC11:0x0000', 'windowOpenDetection':'0xFC11:0x6000', 'frostProtectionTemperature':'0xFC11:0x6002', 'calibrationTemp':'0x0201:0x0010', 'screenDirection':'0xFC11:0x0021'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0006,0020,0201,FC11,FC57', outClusters:'000A,0019', model:'TRV-ZBT', manufacturer:'SONOFF', deviceJoinName:'Sonoff TRV-ZBT'] // not tested!
            ],
            commands      : ['refresh':'refresh', 'refreshAll':'refreshAll', 'initialize':'initialize', 'configureTRV':'configureTRV', 'updateAllPreferences':'updateAllPreferences', 'resetStats':'resetStats'],
            tuyaDPs       : [:],
            attributes    : [
                [at:'0x0201:0x0000', name:'temperature', type:'decimal', dt:'0x29', rw:'ro', scale:100, unit:'°C', description:'Local temperature'],
                [at:'0x0201:0x0010', name:'calibrationTemp', type:'decimal', dt:'0x28', rw:'rw', min:-10.0, max:10.0, step:0.2, scale:10, unit:'°C', title:'<b>Local Temperature Calibration</b>', description:'Room temperature calibration, in 0.2°C steps'],
                [at:'0x0201:0x0012', name:'heatingSetpoint', type:'decimal', dt:'0x29', rw:'rw', min:5.0, max:30.0, step:0.5, scale:100, unit:'°C', description:'Occupied heating setpoint'],
                [at:'0x0201:0x001B', name:'controlSequenceOfOperation', type:'enum', dt:'0x30', rw:'ro', scale:1, map:[2:'heating only'], description:'Control sequence of operation'],
                [at:'0x0201:0x001C', name:'thermostatMode', type:'enum', dt:'0x30', rw:'rw', min:0, max:4, scale:1, map:[0:'off', 1:'auto', 4:'heat'], description:'Thermostat mode'],
                [at:'0x0201:0x0029', name:'thermostatOperatingState', type:'enum', dt:'0x19', rw:'ro', scale:1, map:[0:'idle', 1:'heating'], description:'Thermostat operating state'],
                [at:'0xFC11:0x0000', name:'childLock', type:'enum', dt:'0x10', rw:'rw', min:0, max:1, scale:1, map:[0:'off', 1:'on'], title:'<b>Child Lock</b>', description:'Lock physical controls'],
                [at:'0xFC11:0x0021', name:'screenDirection', type:'enum', dt:'0x20', rw:'rw', min:0, max:3, scale:1, map:[0:'0°', 1:'90°', 2:'180°', 3:'270°'], title:'<b>Display Rotation</b>', description:'Display orientation'],
                [at:'0xFC11:0x6000', name:'windowOpenDetection', type:'enum', dt:'0x10', rw:'rw', min:0, max:1, scale:1, map:[0:'off', 1:'on'], title:'<b>Open Window Detection</b>', description:'Enable automatic open-window detection'],
                [at:'0xFC11:0x6002', name:'frostProtectionTemperature', type:'decimal', dt:'0x29', rw:'rw', min:5.0, max:15.0, step:0.5, scale:100, unit:'°C', title:'<b>Frost Protection Temperature</b>', description:'Minimum temperature for frost protection'],
            ],
            refresh       : ['temperature', 'calibrationTemp', 'heatingSetpoint', 'thermostatMode', 'thermostatOperatingState', 'childLock', 'screenDirection', 'windowOpenDetection', 'frostProtectionTemperature', 'pollBatteryPercentage'],
            deviceJoinName: 'Sonoff TRV-ZBT',
            configuration : [:]
    ],

    // TODO = check constants! https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/lib/constants.ts#L17
    'UNKNOWN'   : [
            description   : 'GENERIC TRV',
            device        : [type: 'TRV', powerSource: 'battery', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [:],
            fingerprints  : [],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'refreshAll':'refreshAll', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences',
                            'getDeviceNameAndProfile':'getDeviceNameAndProfile'
            ],
            tuyaDPs       : [:],
            attributes    : [
                [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt: '0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Temperature</b>',                   description:'Measured temperature'],
                [at:'0x0201:0x0011',  name:'coolingSetpoint',          type:'decimal', dt: '0x21', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Cooling Setpoint</b>',              description:'cooling setpoint'],
                [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt: '0x21', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
                [at:'0x0201:0x001C',  name:'mode',                     type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b> Mode</b>',                   description:'System Mode ?'],
                [at:'0x0201:0x001E',  name:'thermostatRunMode',        type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatRunMode</b>',                   description:'thermostatRunMode'],
                [at:'0x0201:0x0020',  name:'battery2',                 type:'number',  dt: '0x21', rw: 'ro', min:0,    max:100,  step:1,  scale:1,    unit:'%',  description:'Battery percentage remaining'],
                [at:'0x0201:0x0023',  name:'thermostatHoldMode',       type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatHoldMode</b>',                   description:'thermostatHoldMode'],
                [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatOperatingState</b>',                   description:'thermostatOperatingState'],
            ],
            refresh: ['pollThermostatCluster'],
            deviceJoinName: 'UNKWNOWN TRV',
            configuration : [:]
    ]

]

/*
 * -----------------------------------------------------------------------------
 * thermostat cluster 0x0201
 * called from parseThermostatCluster() in the main code ...
 * -----------------------------------------------------------------------------
*/
void customParseThermostatCluster(final Map descMap) {
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return }
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseThermostatCluster: zigbee received Thermostat cluster (0x0201) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "parseThermostatClusterThermostat: received unknown Thermostat cluster (0x0201) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

/*
 * -----------------------------------------------------------------------------
 * Sonoff custom cluster 0xFC11
 * called from parse() in the main code ...
 * -----------------------------------------------------------------------------
*/
void customParseFC11Cluster(final Map descMap) {
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return }
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseFC11Cluster: zigbee received Sonoff custom cluster (0xFC11) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "customParseFC11Cluster: received unknown Sonoff custom cluster (0xFC11) attribute 0x${descMap.attrId} (value ${descMap.value})"
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

List<String> customRefresh() {
    List<String> cmds = refreshFromDeviceProfileList()
    logDebug "customRefresh: ${cmds} "
    return cmds
}

List<String> refreshSonoff() {
    List<String> cmds = []
    cmds += pollThermostatCluster()

    // Check if more than 12 hours have passed since last battery poll
    Long currentTime = now()
    Long lastBatteryTime = state.lastRx?.batteryTime ?: 0
    Long twelveHoursInMs = 12 * 60 * 60 * 1000 // 12 hours in milliseconds

    if ((currentTime - lastBatteryTime) > twelveHoursInMs) {
        logDebug "refreshSonoff(): Battery not polled for >12h, reading battery percentage..."
        cmds += pollBatteryPercentage() // defined in thermostatLib
    } else {
        logTrace "refreshSonoff(): Battery polled recently, skipping battery read"
    }
   
    logDebug "refreshSonoff() : ${cmds}"
    return cmds
}

boolean isSonoffZBT() { state.deviceProfile == 'SONOFF_TRV_ZBT' }

List<String> customConfigure() {
    List<String> cmds = []
    logInfo "customConfigure() : Configuring ${DEVICE?.description} following Z2M pattern..."
    String temperatureChange = isSonoffZBT() ? '32 00' : '05 00'
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x00 0x0201 {${device.zigbeeId}} {}", 'delay 200']      // hvacThermostat
    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0000 0x29 30 3600 {${temperatureChange}}", 'delay 300']          // thermostatTemperature
    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0012 0x29 0 3600 {0A 00}", 'delay 300']           // thermostatOccupiedHeatingSetpoint
    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x001C 0x30 10 3600 {}", 'delay 300']               // thermostatSystemMode 
    
    if (isSonoffZBT()) {
        cmds += refreshFromConfigureReadList(DEVICE.refresh)
    }
    else {
        cmds += zigbee.readAttribute(0x0201, 0x0010, [:], delay = 400)                          // localTemperatureCalibration
        cmds += zigbee.readAttribute(0xFC11, [0x0000, 0x6000, 0x6002, 0x6003, 0x6004, 0x6005, 0x6006, 0x6007], [:], delay = 500)
        cmds += zigbee.readAttribute(0xFC11, [0x600B, 0x600C, 0x2000, 0x2001], [:], delay = 600)            // valve opening/closing degree, tamper, ?illumination?
        cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)  // Battery percentage remaining
    }

    logDebug "customConfigure() : ${cmds}"
    return cmds
}

// Command to manually trigger Z2M-style configuration
void configureTRV() {
    logInfo 'configureTRV() : Manually triggering Z2M-style device configuration...'
    List<String> cmds = customConfigure()
    if (cmds != null && cmds != []) {
        sendZigbeeCommands(cmds)
    }
    else {
        logWarn 'configureTRV() : No configuration commands generated!'
    }
}

// Command to refresh all defined attributes
void refreshAll() {
    logInfo 'refreshAll() : Refreshing all defined attributes...'
    List<String> cmds = []
    
    if (DEVICE?.attributes != null) {
        DEVICE.attributes.each { Map attributeMap ->
            if (attributeMap.at != null && attributeMap.name != null) {
                String[] clusterAndAttribute = attributeMap.at.split(':')
                if (clusterAndAttribute.length == 2) {
                    String clusterHex = clusterAndAttribute[0]
                    String attributeHex = clusterAndAttribute[1]
                    
                    try {
                        int cluster = hubitat.helper.HexUtils.hexStringToInt(clusterHex)
                        int attribute = hubitat.helper.HexUtils.hexStringToInt(attributeHex)
                        
                        Map mfgCode = attributeMap.mfgCode != null ? ['mfgCode': attributeMap.mfgCode] : [:]
                        cmds += zigbee.readAttribute(cluster, attribute, mfgCode, delay = 100)
                        
                        logTrace "refreshAll() : Added read for ${attributeMap.name} (${clusterHex}:${attributeHex})"
                    } catch (Exception e) {
                        logWarn "refreshAll() : Failed to parse cluster/attribute for ${attributeMap.name} (${attributeMap.at}): ${e.message}"
                    }
                }
            }
        }
    }
    cmds += pollBatteryPercentage() // defined in thermostatLib 
    
    if (cmds != null && cmds != []) {
        logInfo "refreshAll() : Sending ${cmds.size()} read commands for all attributes"
        setRefreshRequest() // Set refresh flag like autoPollThermostat does
        sendZigbeeCommands(cmds)
    }
    else {
        logWarn 'refreshAll() : No refresh commands generated!'
    }
}

List<String> initializeSonoff()
{
    if (isSonoffZBT()) { return customConfigure() }
    logWarn 'initializeSonoff() ...'
    List<String> cmds = []

    //        cmds = ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 200",]
    // cmds =   ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 86 12 52 52 00 41 2c 52 00 00} {0x0000}", "delay 200",]
    cmds += zigbee.readAttribute(0x0000, 0x0001, [:], delay = 118)       // Seq: 18

    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x00 0x0000 {${device.zigbeeId}} {}", 'delay 112' ]     // Basic cluster
    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0000 0x0000 0x20 0 300 {00 00}", 'delay 122' ]           //

    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0020 {${device.zigbeeId}} {}", 'delay 112' ]     // Poll Control Cluster    Seq: 68
    cmds += zigbee.readAttribute(0x0020, 0x0000, [:], delay = 121)       // Seq: 21 (Check-in interval)

    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x00 0x0201 {${device.zigbeeId}} {}", 'delay 169' ]     // Thermostat Cluster  Seq: 69
    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0000 0x29 0 3600 {05 00}", 'delay 122' ]          // Seq: 22 configure reproting - local temperature  min 0 max 3600 delta 5 (0.5°C)
    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0012 0x29 0 3600 {0A 00}", 'delay 123' ]          // Seq: 23 configure reproting - occupied heatingSetpoint  min 0 max 3600 delta 10
    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x001c 0x30 10 3600 {00 00}", 'delay 124' ]         // Seq: 24 configure reproting - SystemMode  min 10 max 3600 delta 10
    cmds += zigbee.readAttribute(0x0201, 0x0010, [:], delay = 125)       // Seq: 25 (LocalTemperatureCalibration)
    cmds += zigbee.readAttribute(0xFC11, [0x0000, 0x6000, 0x6002, 0x6003, 0x6004, 0x6005, 0x6006, 0x6007], [:], delay = 226)     // Seq: 26
    cmds += zigbee.writeAttribute(0x0201, 0x001c, 0x30, 0x01, [:], delay = 140)                                    // Seq: 29 (System Mode)

    cmds +=   ["he raw 0x${device.deviceNetworkId} 1 1 0x000a {40 01 01 00 00 00 e2 78 83 1f 2e 07 00 00 23 a8 ad 1f 2e}", "delay 141",]

    cmds += zigbee.writeAttribute(0x0201, 0x001c, 0x30, 0x04, [:], delay = 142)                                    // Seq: 30 (System Mode)

/*
    cmds += zigbee.readAttribute(0x0201, 0x0029, [:], delay = 131)                                                 // Seq: 31 (ThermostatRunningMode)
    cmds += zigbee.readAttribute(0xFC11, 0x6002, [:], delay = 132)                                                 // Seq: 32 (Attribute: 0x6002)

    cmds += zigbee.readAttribute(0xFC11, 0x6005, [:], delay = 133)                                                 // Seq: 33 (Attribute: 0x6005)
    cmds += zigbee.readAttribute(0xFC11, 0x6006, [:], delay = 134)                                                 // Seq: 34 (Attribute: 0x6006)
    cmds += zigbee.readAttribute(0xFC11, 0x6007, [:], delay = 135)                                                 // Seq: 35 (Attribute: 0x6007) (first time)
    cmds += zigbee.readAttribute(0xFC11, 0x6007, [:], delay = 136)                                                 // Seq: 36 (Attribute: 0x6007) (second time)
    cmds += zigbee.readAttribute(0xFC11, 0x6007, [:], delay = 137)                                                 // Seq: 37 (Attribute: 0x6007) (third time)
    cmds += zigbee.readAttribute(0xFC11, 0x6007, [:], delay = 1138)                                                // Seq: 38 (Attribute: 0x6007) (fourth time)
    cmds += zigbee.readAttribute(0xFC11, 0x6002, [:], delay = 1139)                                                // Seq: 39 (Attribute: 0x6002)
*/
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)  // Battery percentage remaining
    return cmds
}

// called from initializeDevice in the commonLib code
List<String> customInitializeDevice() {
    List<String> cmds = []
    cmds = initializeSonoff()
    logDebug "initializeThermostat() : ${cmds}"
    return cmds
}

void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (state.deviceProfile == null) {
        setDeviceNameAndProfile()               // in deviceProfileiLib.groovy
    }
    thermostatInitializeVars(fullInit)
    if (fullInit == true && !isSonoffZBT()) {
        resetPreferencesToDefaults()
    }
}

// called from initializeVars() in the main code ...
void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents(${fullInit})"
    if (isSonoffZBT()) {
        // Publish capabilities, then wait for real reports instead of synthetic temperatures/modes.
        sendSupportedThermostatModes()
        return
    }
    thermostatInitEvents(fullInit)
}

// called from processFoundItem  (processTuyaDPfromDeviceProfile and ) processClusterAttributeFromDeviceProfile in deviceProfileLib when a Zigbee message was found defined in the device profile map
//
// TODO !!
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
                boolean isDigital = state.states['isDigital'] ?: false
                eventMap.type = isDigital ? 'digital' : 'physical'
                eventMap.descriptionText = "${descText}${isDigital ? ' [digital]' : ' [physical]'}"
                sendEvent(eventMap)
                logInfo "${eventMap.descriptionText}"
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
    List<String> cmds = []
    //cmds =   ["he raw 0x${device.deviceNetworkId} 1 1 0x000a {40 01 01 00 00 00 e2 78 83 1f 2e 07 00 00 23 a8 ad 1f 2e}", "delay 200",]

    // Read battery percentage from Power Configuration cluster
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)  // Battery percentage remaining
    logInfo "testT: Reading battery percentage..."
    
    sendZigbeeCommands(cmds)
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDouble, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/commonLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-commonLib', // library marker kkossev.commonLib, line 4
    version: '4.1.1' // library marker kkossev.commonLib, line 5
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
  * ver. 4.0.0  2025-09-17 kkossev  - deviceProfileV4; HOBEIAN as Tuya device; customInitialize() hook; // library marker kkossev.commonLib, line 26
  * ver. 4.0.1  2025-10-14 kkossev  - added clusters 0xFC80 and 0xFC81 // library marker kkossev.commonLib, line 27
  * ver. 4.0.2  2025-10-18 kkossev  - added tuyaDelay in sendTuyaCommand() // library marker kkossev.commonLib, line 28
  * ver. 4.0.3  2025-10-18 kkossev  - added ignoreDuplicatedZigbeeMessages setting; DIGITAL_TIMER increased to 5000 ms // library marker kkossev.commonLib, line 29
  * ver. 4.0.4  2026-06-04 kkossev  - added ED00 cluster; // library marker kkossev.commonLib, line 30
  * ver. 4.0.5  2026-08-03 kkossev  - bug fixes // library marker kkossev.commonLib, line 31
  * ver. 4.1.0  2026-08-05 kkossev  - the administrative commands drop-down moved from configure(par) to the new deviceUtilities(par) command, so that configure() is again a plain Configuration capability button; removed the two separator entries from ConfigureOpts; configureHelp() is callable again and shows the command list and a '_status_' event when nothing was selected; do not use 'defaultValue' in a command parameter - it does not preselect the drop-down, but it IS submitted when Run is pressed without a selection!; configure() now shows a 'sleepy devices can not be configured' warning text; ping() icon changed to the antenna bars; added a one-click 'loadAllDefaults' command button // library marker kkossev.commonLib, line 32
  * ver. 4.1.1  2026-08-23 kkossev  - (dev. branch) bug fix: quoted the respondsTo('processTuyaDPfromDeviceProfile') argument in standardProcessTuyaDP(); the bare identifier threw a NullPointerException in drivers without deviceProfileLib; cosmetic: parse() and standardAndCustomParseCluster() log the cluster id from clusterId/clusterInt when descMap.cluster is null (catchall messages), instead of 'cluster:0xnull'; removed a stray '}' from the healthStatus warning text // library marker kkossev.commonLib, line 33
  * // library marker kkossev.commonLib, line 34
  *                                   TODO: change the offline threshold to 2  // library marker kkossev.commonLib, line 35
  *                                   TODO: add GetInfo (endpoints list) command (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 36
  *                                   TODO: make the configure() without parameter smart - analyze the State variables and call delete states.... call ActiveAndpoints() or/amd initialize() or/and configure() // library marker kkossev.commonLib, line 37
  *                                   TODO: check - offlineCtr is not increasing? (ZBMicro); // library marker kkossev.commonLib, line 38
  *                                   TODO: check deviceCommandTimeout() // library marker kkossev.commonLib, line 39
  *                                   TODO: when device rejoins the network, read the battery percentage again (probably in custom handler, not for all devices) // library marker kkossev.commonLib, line 40
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 41
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 42
  *                                   TODO: MOVE ZDO counters to health state? // library marker kkossev.commonLib, line 43
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 44
  *                                   TODO: Versions of the main module + included libraries (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 45
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 46
  * // library marker kkossev.commonLib, line 47
*/ // library marker kkossev.commonLib, line 48

String commonLibVersion() { '4.1.1' } // library marker kkossev.commonLib, line 50
String commonLibStamp() { '2026/08/23 4:28 PM' } // library marker kkossev.commonLib, line 51

import groovy.transform.Field // library marker kkossev.commonLib, line 53
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 54
import hubitat.device.Protocol // library marker kkossev.commonLib, line 55
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 56
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 57
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 58
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 59
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 60
import java.math.BigDecimal // library marker kkossev.commonLib, line 61

metadata { // library marker kkossev.commonLib, line 63
        if (_DEBUG) { // library marker kkossev.commonLib, line 64
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 65
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 66
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 67
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 68
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 69
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 70
            ] // library marker kkossev.commonLib, line 71
        } // library marker kkossev.commonLib, line 72

        // common capabilities for all device types // library marker kkossev.commonLib, line 74
        capability 'Configuration' // library marker kkossev.commonLib, line 75
        capability 'Refresh' // library marker kkossev.commonLib, line 76
        capability 'HealthCheck' // library marker kkossev.commonLib, line 77
        capability 'PowerSource'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 78

        // common attributes for all device types // library marker kkossev.commonLib, line 80
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 81
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 82
        attribute '_status_', 'string' // library marker kkossev.commonLib, line 83

        // common commands for all device types // library marker kkossev.commonLib, line 85
        // 'configure' below carries a description-only parameter map (NO 'type' key!), exactly like ping and refresh - it just renders the help text under the button and submits nothing. // library marker kkossev.commonLib, line 86
        // NEVER give it a typed parameter: an ENUM here used to shadow the no-argument configure() of capability 'Configuration', making the dispatch depend on whether the platform happened to supply a value. // library marker kkossev.commonLib, line 87
        command 'configure', [[name:"✋ This button can not configure battery-powered 'sleepy' devices. Pair the device again to your hub, without deleting it!"]] // library marker kkossev.commonLib, line 88
        command 'deviceUtilities', [[name:'⚙️ Advanced administrative and diagnostic commands • Use only when troubleshooting or reconfiguring the device', type: 'ENUM', constraints: ConfigureOpts.keySet() as List<String>]]    // do NOT add a 'defaultValue' here! The drop-down still displays '- No selection -', but the platform submits the defaultValue when Run is pressed - i.e. an un-selected Run silently executed 'LOAD ALL DEFAULTS' (tested on C-8 Pro 2.5.1.143) // library marker kkossev.commonLib, line 89
        // one-click shortcut for the most used deviceUtilities entry. Description-only parameter map again - NEVER give loadAllDefaults a typed parameter: deviceUtilities dispatches it as "$func"() with no arguments, so an un-selected Run would hit the no-argument overload and wipe the device immediately. // library marker kkossev.commonLib, line 90
        command 'loadAllDefaults', [[name:'⚠️ Erases all preferences, states, scheduled jobs and child devices, then reloads the driver defaults • Use after switching drivers, or when the device was not recognised by an older version']] // library marker kkossev.commonLib, line 91
        command 'ping', [[name:'📶 Test device connectivity and measure response time • Updates the RTT attribute with round-trip time in milliseconds']] // library marker kkossev.commonLib, line 92
        command 'refresh', [[name:"🔄 Query the device for current state and update the attributes. • ⚠️ Battery-powered 'sleepy' devices may not respond!"]] // library marker kkossev.commonLib, line 93

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 95
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 96

    preferences { // library marker kkossev.commonLib, line 98
        // txtEnable and logEnable moved to the custom driver settings - copy& paste there ... // library marker kkossev.commonLib, line 99
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 100
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 101

        if (device) { // library marker kkossev.commonLib, line 103
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'The advanced options should be already automatically set in an optimal way for your device...Click on the "Save and Close" button when toggling this option!', defaultValue: false // library marker kkossev.commonLib, line 104
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 105
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 106
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 107
                input name: 'ignoreDuplicatedZigbeeMessages', type: 'bool', title: '<b>Ignore Duplicated Zigbee Messages</b>', defaultValue: false, description: 'Ignore identical Zigbee attribute reports received within short time periods to reduce log spam and redundant processing' // library marker kkossev.commonLib, line 108
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 109
            } // library marker kkossev.commonLib, line 110
        } // library marker kkossev.commonLib, line 111
    } // library marker kkossev.commonLib, line 112
} // library marker kkossev.commonLib, line 113

@Field static final Integer IGNORE_DUPLICATED_ZIGBEE_MESSAGES_TIMER = 1000  // 1 second // library marker kkossev.commonLib, line 115
@Field static final Integer DIGITAL_TIMER = 5000             // command was sent by this driver // library marker kkossev.commonLib, line 116
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 117
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 118
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 119
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 120
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 121
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 122
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 123
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 124
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 125
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 126

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 128
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 129
] // library marker kkossev.commonLib, line 130
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 131
    defaultValue: 240, options: [2: 'Every 2 Mins', 10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 132
] // library marker kkossev.commonLib, line 133

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 135
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'], // library marker kkossev.commonLib, line 136
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 137
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 138
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 139
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 140
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 141
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 142
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'] // library marker kkossev.commonLib, line 143
] // library marker kkossev.commonLib, line 144

public boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 146

/** // library marker kkossev.commonLib, line 148
 * Parse Zigbee message // library marker kkossev.commonLib, line 149
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 150
 */ // library marker kkossev.commonLib, line 151
public void parse(final String description) { // library marker kkossev.commonLib, line 152

    Map stateCopy = state            // .clone() throws java.lang.CloneNotSupportedException in HE platform version 2.4.1.155 ! // library marker kkossev.commonLib, line 154
    checkDriverVersion(stateCopy)    // +1 ms // library marker kkossev.commonLib, line 155
    if (state.stats != null) { state.stats?.rxCtr= (state.stats?.rxCtr ?: 0) + 1 } else { state.stats = [:] }  // updateRxStats(state) // +1 ms // library marker kkossev.commonLib, line 156
    if (state.lastRx != null) { state.lastRx?.timeStamp = unix2formattedDate(now()) } else { state.lastRx = [:] } // library marker kkossev.commonLib, line 157
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 158
    setHealthStatusOnline(state)    // +2 ms // library marker kkossev.commonLib, line 159

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 161
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 162
        if (this.respondsTo('customParseIasMessage')) { customParseIasMessage(description) } // library marker kkossev.commonLib, line 163
        else if (this.respondsTo('standardParseIasMessage')) { standardParseIasMessage(description) } // library marker kkossev.commonLib, line 164
        else if (this.respondsTo('parseIasMessage')) { parseIasMessage(description) } // library marker kkossev.commonLib, line 165
        else { logDebug "ignored IAS zone status (no IAS parser) description: $description" } // library marker kkossev.commonLib, line 166
        return // library marker kkossev.commonLib, line 167
    } // library marker kkossev.commonLib, line 168
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 169
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 170
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 171
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 172
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 173
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 174
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 175
        return // library marker kkossev.commonLib, line 176
    } // library marker kkossev.commonLib, line 177

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 179
        return // library marker kkossev.commonLib, line 180
    } // library marker kkossev.commonLib, line 181
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 182

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 184
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 185

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 187
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 188
        return // library marker kkossev.commonLib, line 189
    } // library marker kkossev.commonLib, line 190
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 191
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 192
        return // library marker kkossev.commonLib, line 193
    } // library marker kkossev.commonLib, line 194
    // // library marker kkossev.commonLib, line 195
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 196
    // // library marker kkossev.commonLib, line 197
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 198
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 199
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 200
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 201
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 202
            } // library marker kkossev.commonLib, line 203
            break // library marker kkossev.commonLib, line 204
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 205
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 206
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 207
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 208
            } // library marker kkossev.commonLib, line 209
            break // library marker kkossev.commonLib, line 210
        default: // library marker kkossev.commonLib, line 211
            if (settings.logEnable) { // library marker kkossev.commonLib, line 212
                // descMap.cluster is null for catchall messages - fall back to clusterId, or format clusterInt // library marker kkossev.commonLib, line 213
                String clusterHex = descMap.cluster ?: descMap.clusterId ?: zigbee.convertToHexString(descMap.clusterInt as Integer, 4) // library marker kkossev.commonLib, line 214
                logWarn "parse: zigbee received <b>unknown cluster:0x${clusterHex} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 215
            } // library marker kkossev.commonLib, line 216
            break // library marker kkossev.commonLib, line 217
    } // library marker kkossev.commonLib, line 218
} // library marker kkossev.commonLib, line 219

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 221
    0x0000: 'Basic',             0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x0006: 'OnOff',           0x0007:'onOffConfiguration',      0x0008: 'LevelControl',  // library marker kkossev.commonLib, line 222
    0x000C: 'AnalogInput',       0x0012: 'MultistateInput',  0x0020: 'PollControl',      0x0102: 'WindowCovering',   0x0201: 'Thermostat',  0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 223
    0x0400: 'Illuminance',       0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 224
    0x0B04: 'ElectricalMeasure', 0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC03: 'FC03',            0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 225
    0xFC80: 'FC80',              0xFC81: 'FC81',             0xFCC0: 'XiaomiFCC0',       0xED00: 'ED00' // library marker kkossev.commonLib, line 226
] // library marker kkossev.commonLib, line 227

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 229
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 230
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 231
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 232
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 233
    // descMap.cluster is null for catchall messages - fall back to clusterId, or format clusterInt, so that the logs never show 'cluster:0xnull' // library marker kkossev.commonLib, line 234
    String  clusterHex = descMap.cluster ?: descMap.clusterId ?: zigbee.convertToHexString(clusterInt, 4) // library marker kkossev.commonLib, line 235
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 236
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${clusterHex} (${clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 237
        return false // library marker kkossev.commonLib, line 238
    } // library marker kkossev.commonLib, line 239
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 240
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 241
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 242
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 243
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 244
        return true // library marker kkossev.commonLib, line 245
    } // library marker kkossev.commonLib, line 246
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 247
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 248
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 249
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 250
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 251
        return true // library marker kkossev.commonLib, line 252
    } // library marker kkossev.commonLib, line 253
    if (device?.getDataValue('model') != 'ZigUSB' && descMap.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 254
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${clusterHex} (${clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 255
    } // library marker kkossev.commonLib, line 256
    return false // library marker kkossev.commonLib, line 257
} // library marker kkossev.commonLib, line 258

// not used - throws exception :  error groovy.lang.MissingPropertyException: No such property: rxCtr for class: java.lang.String on line 1568 (method parse) // library marker kkossev.commonLib, line 260
private static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 261
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 262
} // library marker kkossev.commonLib, line 263

public boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 265
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 266
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 267
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 268
    } // library marker kkossev.commonLib, line 269
    return false // library marker kkossev.commonLib, line 270
} // library marker kkossev.commonLib, line 271

public boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 273
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 274
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 275
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 276
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
            if (this.respondsTo('parseSimpleDescriptorResponse')) { parseSimpleDescriptorResponse(descMap) } // library marker kkossev.commonLib, line 317
            break // library marker kkossev.commonLib, line 318
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 319
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 320
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 321
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 322
            break // library marker kkossev.commonLib, line 323
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 324
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 325
            break // library marker kkossev.commonLib, line 326
        case 0x0002 : // Node Descriptor Request // library marker kkossev.commonLib, line 327
        case 0x0036 : // Permit Joining Request // library marker kkossev.commonLib, line 328
        case 0x8022 : // unbind request // library marker kkossev.commonLib, line 329
        case 0x8034 : // leave response // library marker kkossev.commonLib, line 330
            if (settings?.logEnable) { log.debug "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 331
            break // library marker kkossev.commonLib, line 332
        default : // library marker kkossev.commonLib, line 333
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 334
            break // library marker kkossev.commonLib, line 335
    } // library marker kkossev.commonLib, line 336
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 337
} // library marker kkossev.commonLib, line 338

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 340
private void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 341
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 342
    switch (commandId) { // library marker kkossev.commonLib, line 343
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 344
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 345
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 346
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 347
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 348
        default: // library marker kkossev.commonLib, line 349
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 350
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 351
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 352
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 353
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 354
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 355
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 356
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 357
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 358
            } // library marker kkossev.commonLib, line 359
            break // library marker kkossev.commonLib, line 360
    } // library marker kkossev.commonLib, line 361
} // library marker kkossev.commonLib, line 362

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 364
private void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 365
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 366
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 367
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 368
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 369
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 370
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 371
    } // library marker kkossev.commonLib, line 372
    else { // library marker kkossev.commonLib, line 373
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 374
    } // library marker kkossev.commonLib, line 375
} // library marker kkossev.commonLib, line 376

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 378
private void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 379
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 380
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 381
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 382
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 383
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 384
    } // library marker kkossev.commonLib, line 385
    else { // library marker kkossev.commonLib, line 386
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 387
    } // library marker kkossev.commonLib, line 388
} // library marker kkossev.commonLib, line 389

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 391
private void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 392
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 393
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 394
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 395
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 396
        state.reportingEnabled = true // library marker kkossev.commonLib, line 397
    } // library marker kkossev.commonLib, line 398
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 399
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 400
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 401
    } else { // library marker kkossev.commonLib, line 402
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 403
    } // library marker kkossev.commonLib, line 404
} // library marker kkossev.commonLib, line 405

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 407
private void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 408
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 409
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 410
    if (status == 0) { // library marker kkossev.commonLib, line 411
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 412
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 413
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 414
        int delta = 0 // library marker kkossev.commonLib, line 415
        if (descMap.data.size() >= 11) { // library marker kkossev.commonLib, line 416
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 417
        } // library marker kkossev.commonLib, line 418
        else if (descMap.data.size() == 10) { // library marker kkossev.commonLib, line 419
            delta = zigbee.convertHexToInt(descMap.data[9])      // 1-byte reportable change (uint8/int8) // library marker kkossev.commonLib, line 420
        } // library marker kkossev.commonLib, line 421
        else { // library marker kkossev.commonLib, line 422
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 423
        } // library marker kkossev.commonLib, line 424
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 425
    } // library marker kkossev.commonLib, line 426
    else { // library marker kkossev.commonLib, line 427
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 428
    } // library marker kkossev.commonLib, line 429
} // library marker kkossev.commonLib, line 430

private Boolean executeCustomHandler(String handlerName, Object handlerArgs) { // library marker kkossev.commonLib, line 432
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 433
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 434
        return false // library marker kkossev.commonLib, line 435
    } // library marker kkossev.commonLib, line 436
    // execute the customHandler function // library marker kkossev.commonLib, line 437
    Boolean result = false // library marker kkossev.commonLib, line 438
    try { // library marker kkossev.commonLib, line 439
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 440
    } // library marker kkossev.commonLib, line 441
    catch (e) { // library marker kkossev.commonLib, line 442
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 443
        return false // library marker kkossev.commonLib, line 444
    } // library marker kkossev.commonLib, line 445
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 446
    return result // library marker kkossev.commonLib, line 447
} // library marker kkossev.commonLib, line 448

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 450
private void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 451
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 452
    final String commandId = data[0] // library marker kkossev.commonLib, line 453
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 454
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 455
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 456
        // Tuya EF00 devices answer every DP write (command 0x00) with a Default Response of 0x01 'Failure' // library marker kkossev.commonLib, line 457
        // regardless of the outcome - hub-verified 2026-08-24 on _TZE200_2aaelwxk (ZG-204ZM): a write that // library marker kkossev.commonLib, line 458
        // genuinely changed dp 102 from 30 to 60 was acknowledged with the same 'Failure'. Not worth a warning. // library marker kkossev.commonLib, line 459
        if (descMap.clusterInt == CLUSTER_TUYA && commandId == '00') { // library marker kkossev.commonLib, line 460
            logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status} (Tuya EF00 write - status byte is not meaningful)" // library marker kkossev.commonLib, line 461
        } // library marker kkossev.commonLib, line 462
        else { // library marker kkossev.commonLib, line 463
            logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 464
        } // library marker kkossev.commonLib, line 465
    } else { // library marker kkossev.commonLib, line 466
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 467
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 468
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 469
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 470
        } // library marker kkossev.commonLib, line 471
    } // library marker kkossev.commonLib, line 472
} // library marker kkossev.commonLib, line 473

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 475
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 476
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 477
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 478

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 480
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 481
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 482
] // library marker kkossev.commonLib, line 483

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 485
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 486
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 487
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 488
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 489
] // library marker kkossev.commonLib, line 490

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 492
private BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 493
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 494
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 495
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 496
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 497
    return avg // library marker kkossev.commonLib, line 498
} // library marker kkossev.commonLib, line 499

private void handlePingResponse() { // library marker kkossev.commonLib, line 501
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 502
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 503
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 504

    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 506
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 507
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 508
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '9999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 509
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 510
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 511
        sendRttEvent() // library marker kkossev.commonLib, line 512
    } // library marker kkossev.commonLib, line 513
    else { // library marker kkossev.commonLib, line 514
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 515
    } // library marker kkossev.commonLib, line 516
    state.states['isPing'] = false // library marker kkossev.commonLib, line 517
} // library marker kkossev.commonLib, line 518

/* // library marker kkossev.commonLib, line 520
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 521
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 522
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 523
*/ // library marker kkossev.commonLib, line 524
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 525

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 527
private void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 528
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 529
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 530
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 531
    boolean isPing = state.states?.isPing ?: false // library marker kkossev.commonLib, line 532
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 533
        case 0x0000: // library marker kkossev.commonLib, line 534
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 535
            break // library marker kkossev.commonLib, line 536
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 537
            if (isPing) { // library marker kkossev.commonLib, line 538
                handlePingResponse() // library marker kkossev.commonLib, line 539
            } // library marker kkossev.commonLib, line 540
            else { // library marker kkossev.commonLib, line 541
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 542
            } // library marker kkossev.commonLib, line 543
            break // library marker kkossev.commonLib, line 544
        case 0x0004: // library marker kkossev.commonLib, line 545
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 546
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 547
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 548
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 549
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 550
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 551
            } // library marker kkossev.commonLib, line 552
            break // library marker kkossev.commonLib, line 553
        case 0x0005: // library marker kkossev.commonLib, line 554
            if (isPing) { // library marker kkossev.commonLib, line 555
                handlePingResponse() // library marker kkossev.commonLib, line 556
            } // library marker kkossev.commonLib, line 557
            else { // library marker kkossev.commonLib, line 558
                logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 559
                // received device model Remote Control N2 // library marker kkossev.commonLib, line 560
                String model = device.getDataValue('model') // library marker kkossev.commonLib, line 561
                if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 562
                    logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 563
                    device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 564
                } // library marker kkossev.commonLib, line 565
            } // library marker kkossev.commonLib, line 566
            break // library marker kkossev.commonLib, line 567
        case 0x0007: // library marker kkossev.commonLib, line 568
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 569
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 570
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 571
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 572
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 573
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 574
            } // library marker kkossev.commonLib, line 575
            break // library marker kkossev.commonLib, line 576
        case 0xFFDF: // library marker kkossev.commonLib, line 577
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 578
            break // library marker kkossev.commonLib, line 579
        case 0xFFE2: // library marker kkossev.commonLib, line 580
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 581
            break // library marker kkossev.commonLib, line 582
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 583
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 584
            break // library marker kkossev.commonLib, line 585
        case 0xFFFE: // library marker kkossev.commonLib, line 586
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 587
            break // library marker kkossev.commonLib, line 588
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 589
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 590
            logInfo "device firmware version is ${version}" // library marker kkossev.commonLib, line 591
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 592
            break // library marker kkossev.commonLib, line 593
        default: // library marker kkossev.commonLib, line 594
            logDebug "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 595
            break // library marker kkossev.commonLib, line 596
    } // library marker kkossev.commonLib, line 597
} // library marker kkossev.commonLib, line 598

private void standardParsePollControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 600
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 601
        case 0x0000: logDebug "PollControl cluster: CheckInInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 602
        case 0x0001: logDebug "PollControl cluster: LongPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 603
        case 0x0002: logDebug "PollControl cluster: ShortPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 604
        case 0x0003: logDebug "PollControl cluster: FastPollTimeout = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 605
        case 0x0004: logDebug "PollControl cluster: CheckInIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 606
        case 0x0005: logDebug "PollControl cluster: LongPollIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 607
        case 0x0006: logDebug "PollControl cluster: FastPollTimeoutMax = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 608
        default: logDebug "zigbee received unknown PollControl cluster attribute 0x${descMap.attrId} (value ${descMap.value})" ; break // library marker kkossev.commonLib, line 609
    } // library marker kkossev.commonLib, line 610
} // library marker kkossev.commonLib, line 611

public void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 613
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 614
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 615

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 617
    Map descMap = [:] // library marker kkossev.commonLib, line 618
    try { // library marker kkossev.commonLib, line 619
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 620
    } // library marker kkossev.commonLib, line 621
    catch (e1) { // library marker kkossev.commonLib, line 622
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 623
        // try alternative custom parsing // library marker kkossev.commonLib, line 624
        descMap = [:] // library marker kkossev.commonLib, line 625
        try { // library marker kkossev.commonLib, line 626
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 627
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 628
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 629
            } // library marker kkossev.commonLib, line 630
        } // library marker kkossev.commonLib, line 631
        catch (e2) { // library marker kkossev.commonLib, line 632
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 633
            return [:] // library marker kkossev.commonLib, line 634
        } // library marker kkossev.commonLib, line 635
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 636
    } // library marker kkossev.commonLib, line 637
    return descMap // library marker kkossev.commonLib, line 638
} // library marker kkossev.commonLib, line 639

// return true if the messages is processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 641
// return false if the cluster is not a Tuya cluster // library marker kkossev.commonLib, line 642
private boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 643
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 644
        return false // library marker kkossev.commonLib, line 645
    } // library marker kkossev.commonLib, line 646
    // try to parse ... // library marker kkossev.commonLib, line 647
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 648
    Map descMap = [:] // library marker kkossev.commonLib, line 649
    try { // library marker kkossev.commonLib, line 650
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 651
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 652
    } // library marker kkossev.commonLib, line 653
    catch (e) { // library marker kkossev.commonLib, line 654
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 655
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 656
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 657
        return true // library marker kkossev.commonLib, line 658
    } // library marker kkossev.commonLib, line 659

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 661
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 662
    } // library marker kkossev.commonLib, line 663
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 664
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 665
    } // library marker kkossev.commonLib, line 666
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 667
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 668
    } // library marker kkossev.commonLib, line 669
    else { // library marker kkossev.commonLib, line 670
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 671
        return false // library marker kkossev.commonLib, line 672
    } // library marker kkossev.commonLib, line 673
    return true    // processed // library marker kkossev.commonLib, line 674
} // library marker kkossev.commonLib, line 675

// return true if processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 677
private boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 678
  /* // library marker kkossev.commonLib, line 679
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 680
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 681
        return true // library marker kkossev.commonLib, line 682
    } // library marker kkossev.commonLib, line 683
*/ // library marker kkossev.commonLib, line 684
    Map descMap = [:] // library marker kkossev.commonLib, line 685
    try { // library marker kkossev.commonLib, line 686
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 687
    } // library marker kkossev.commonLib, line 688
    catch (e1) { // library marker kkossev.commonLib, line 689
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 690
        // try alternative custom parsing // library marker kkossev.commonLib, line 691
        descMap = [:] // library marker kkossev.commonLib, line 692
        try { // library marker kkossev.commonLib, line 693
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 694
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 695
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 696
            } // library marker kkossev.commonLib, line 697
        } // library marker kkossev.commonLib, line 698
        catch (e2) { // library marker kkossev.commonLib, line 699
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 700
            return true // library marker kkossev.commonLib, line 701
        } // library marker kkossev.commonLib, line 702
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 703
    } // library marker kkossev.commonLib, line 704
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 705
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 706
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 707
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 708
        return false // library marker kkossev.commonLib, line 709
    } // library marker kkossev.commonLib, line 710
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 711
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 712
    // attribute report received // library marker kkossev.commonLib, line 713
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 714
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 715
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 716
    } // library marker kkossev.commonLib, line 717
    attrData.each { // library marker kkossev.commonLib, line 718
        if (it.status == '86') { // library marker kkossev.commonLib, line 719
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 720
        // TODO - skip parsing? // library marker kkossev.commonLib, line 721
        } // library marker kkossev.commonLib, line 722
        switch (it.cluster) { // library marker kkossev.commonLib, line 723
            case '0000' : // library marker kkossev.commonLib, line 724
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 725
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 726
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 727
                } // library marker kkossev.commonLib, line 728
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 729
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 730
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 731
                } // library marker kkossev.commonLib, line 732
                else { // library marker kkossev.commonLib, line 733
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 734
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 735
                } // library marker kkossev.commonLib, line 736
                break // library marker kkossev.commonLib, line 737
            default : // library marker kkossev.commonLib, line 738
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 739
                break // library marker kkossev.commonLib, line 740
        } // switch // library marker kkossev.commonLib, line 741
    } // for each attribute // library marker kkossev.commonLib, line 742
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 743
} // library marker kkossev.commonLib, line 744

public String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 746
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 747
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 748
} // library marker kkossev.commonLib, line 749

public String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 751
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 752
} // library marker kkossev.commonLib, line 753

/* // library marker kkossev.commonLib, line 755
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 756
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 757
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 758
*/ // library marker kkossev.commonLib, line 759
private static int getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 760
private static int getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 761
private static int getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 762

// Tuya Commands // library marker kkossev.commonLib, line 764
private static int getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 765
private static int getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 766
private static int getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 767
private static int getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 768
private static int getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 769

// tuya DP type // library marker kkossev.commonLib, line 771
private static String getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 772
private static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 773
private static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 774
private static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 775
private static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 776
private static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 777

private void syncTuyaDateTime() { // library marker kkossev.commonLib, line 779
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 780
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 781
    long offset = 0 // library marker kkossev.commonLib, line 782
    int offsetHours = 0 // library marker kkossev.commonLib, line 783
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 784
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 785
    try { // library marker kkossev.commonLib, line 786
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 787
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 788
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 789
    } catch (e) { // library marker kkossev.commonLib, line 790
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 791
    } // library marker kkossev.commonLib, line 792
    // // library marker kkossev.commonLib, line 793
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 794
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 795
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 796
} // library marker kkossev.commonLib, line 797

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 799
public void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 800
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 801
        syncTuyaDateTime() // library marker kkossev.commonLib, line 802
    } // library marker kkossev.commonLib, line 803
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 804
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 805
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 806
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 807
        if (status != '00') { // library marker kkossev.commonLib, line 808
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 809
        } // library marker kkossev.commonLib, line 810
    } // library marker kkossev.commonLib, line 811
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 812
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 813
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 814
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 815
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 816
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} data=${descMap?.data})" // library marker kkossev.commonLib, line 817
            return // library marker kkossev.commonLib, line 818
        } // library marker kkossev.commonLib, line 819
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 820
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 821
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 822
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 823
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 824
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 825
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 826
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 827
            } // library marker kkossev.commonLib, line 828
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 829
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 830
        } // library marker kkossev.commonLib, line 831
    } // library marker kkossev.commonLib, line 832
    else { // library marker kkossev.commonLib, line 833
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 834
    } // library marker kkossev.commonLib, line 835
} // library marker kkossev.commonLib, line 836

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 838
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 839
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 840
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 841
        //logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 842
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 843
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 844
        } // library marker kkossev.commonLib, line 845
    } // library marker kkossev.commonLib, line 846
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 847
    if (this.respondsTo('processTuyaDPfromDeviceProfile')) { // library marker kkossev.commonLib, line 848
        //logTrace 'standardProcessTuyaDP: processTuyaDPfromDeviceProfile exists, calling it...' // library marker kkossev.commonLib, line 849
        if (this.respondsTo('isInCooldown') && isInCooldown()) { // library marker kkossev.commonLib, line 850
            logDebug "standardProcessTuyaDP: device is in cooldown, skipping processing of dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 851
            return // library marker kkossev.commonLib, line 852
        } // library marker kkossev.commonLib, line 853
        if (this.respondsTo('ensureCurrentProfileLoaded')) { // library marker kkossev.commonLib, line 854
            ensureCurrentProfileLoaded() // library marker kkossev.commonLib, line 855
        } // library marker kkossev.commonLib, line 856
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 857
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 858
        } // library marker kkossev.commonLib, line 859
    } // library marker kkossev.commonLib, line 860
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 861
} // library marker kkossev.commonLib, line 862

public int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 864
    int retValue = 0 // library marker kkossev.commonLib, line 865
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 866
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 867
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 868
        int power = 1 // library marker kkossev.commonLib, line 869
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 870
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 871
            power = power * 256 // library marker kkossev.commonLib, line 872
        } // library marker kkossev.commonLib, line 873
    } // library marker kkossev.commonLib, line 874
    return retValue // library marker kkossev.commonLib, line 875
} // library marker kkossev.commonLib, line 876

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 878

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 880
    List<String> cmds = [] // library marker kkossev.commonLib, line 881
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 882
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 883
    int tuyaCmd // library marker kkossev.commonLib, line 884
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified! // library marker kkossev.commonLib, line 885
    if (this.respondsTo('getDEVICE') && getDEVICE()?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 886
        tuyaCmd = getDEVICE().device.tuyaCmd // library marker kkossev.commonLib, line 887
    } // library marker kkossev.commonLib, line 888
    else { // library marker kkossev.commonLib, line 889
        tuyaCmd = tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 890
    } // library marker kkossev.commonLib, line 891
    // Get delay from device profile or use default - guarded the same way as tuyaCmd above, because a driver that // library marker kkossev.commonLib, line 892
    // includes commonLib but NOT deviceProfileLib has no DEVICE at all (BUGS.md A3). // library marker kkossev.commonLib, line 893
    int tuyaDelay = (this.respondsTo('getDEVICE') ? (getDEVICE()?.device?.tuyaDelay as Integer) : null) ?: 201 // library marker kkossev.commonLib, line 894
    String tuyaPayload = PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd // library marker kkossev.commonLib, line 895
    // deviceProfile device key disableDefaultResponse:true - suppress the ZCL Default Response that the device is // library marker kkossev.commonLib, line 896
    // otherwise obliged to send for every EF00 command (Tuya answers 0x01 'Failure' regardless of the outcome). // library marker kkossev.commonLib, line 897
    // zigbee.command() always leaves the frame control 'disable default response' bit clear, so the frame has to be // library marker kkossev.commonLib, line 898
    // hand-built as 'he raw' with frame control 0x11 (bit0-1 = cluster specific, bit4 = disable default response). // library marker kkossev.commonLib, line 899
    boolean disableDefaultRsp = this.respondsTo('getDEVICE') ? (getDEVICE()?.device?.disableDefaultResponse == true) : false // library marker kkossev.commonLib, line 900
    if (disableDefaultRsp) { // library marker kkossev.commonLib, line 901
        String epHex  = zigbee.convertToHexString(ep, 2) // library marker kkossev.commonLib, line 902
        String cmdHex = zigbee.convertToHexString(tuyaCmd, 2) // library marker kkossev.commonLib, line 903
        cmds = ["he raw 0x${device.deviceNetworkId} 0x01 0x${epHex} 0x${zigbee.convertToHexString(CLUSTER_TUYA, 4)} {11 ${getZclSeqNo()} ${cmdHex} ${tuyaPayload}} {0x0104}", "delay ${tuyaDelay}"] // library marker kkossev.commonLib, line 904
    } // library marker kkossev.commonLib, line 905
    else { // library marker kkossev.commonLib, line 906
        cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = tuyaDelay, tuyaPayload) // library marker kkossev.commonLib, line 907
    } // library marker kkossev.commonLib, line 908
    logDebug "getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type disableDefaultRsp=${disableDefaultRsp}) = ${cmds}" // library marker kkossev.commonLib, line 909
    return cmds // library marker kkossev.commonLib, line 910
} // library marker kkossev.commonLib, line 911

// ZCL sequence number for hand-built 'he raw' frames - zigbee.command() manages its own, 'he raw' does not. // library marker kkossev.commonLib, line 913
// Must increment, otherwise a burst of writes goes out with a duplicate sequence number. // library marker kkossev.commonLib, line 914
private String getZclSeqNo() { // library marker kkossev.commonLib, line 915
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 916
    int seq = safeToInt(state.lastTx['zclSeq']) + 1 // library marker kkossev.commonLib, line 917
    if (seq > 0xFF) { seq = 1 } // library marker kkossev.commonLib, line 918
    state.lastTx['zclSeq'] = seq // library marker kkossev.commonLib, line 919
    return zigbee.convertToHexString(seq, 2) // library marker kkossev.commonLib, line 920
} // library marker kkossev.commonLib, line 921

private String getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 923

public void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 925
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 926
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 927
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 928
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 929
} // library marker kkossev.commonLib, line 930


public List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 933
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 934
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 935
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 936
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 937
} // library marker kkossev.commonLib, line 938

public List<String> queryAllTuyaDP() { // library marker kkossev.commonLib, line 940
    logTrace 'queryAllTuyaDP()' // library marker kkossev.commonLib, line 941
    List<String> cmds = zigbee.command(0xEF00, 0x03) // library marker kkossev.commonLib, line 942
    return cmds // library marker kkossev.commonLib, line 943
} // library marker kkossev.commonLib, line 944

public void aqaraBlackMagic() { // library marker kkossev.commonLib, line 946
    List<String> cmds = [] // library marker kkossev.commonLib, line 947
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 948
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 949
    } // library marker kkossev.commonLib, line 950
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 951
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 952
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 953
        return // library marker kkossev.commonLib, line 954
    } // library marker kkossev.commonLib, line 955
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 956
} // library marker kkossev.commonLib, line 957

// Invoked from configure() // library marker kkossev.commonLib, line 959
public List<String> initializeDevice() { // library marker kkossev.commonLib, line 960
    List<String> cmds = [] // library marker kkossev.commonLib, line 961
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 962
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 963
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 964
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 965
    } // library marker kkossev.commonLib, line 966
    else { logDebug 'no customInitializeDevice method defined' } // library marker kkossev.commonLib, line 967
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 968
    return cmds // library marker kkossev.commonLib, line 969
} // library marker kkossev.commonLib, line 970

// Invoked from configure() // library marker kkossev.commonLib, line 972
public List<String> configureDevice() { // library marker kkossev.commonLib, line 973
    List<String> cmds = [] // library marker kkossev.commonLib, line 974
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 975
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 976
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 977
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 978
    } // library marker kkossev.commonLib, line 979
    else { logDebug 'no customConfigureDevice method defined' } // library marker kkossev.commonLib, line 980
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 981
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 982
    return cmds // library marker kkossev.commonLib, line 983
} // library marker kkossev.commonLib, line 984

/* // library marker kkossev.commonLib, line 986
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 987
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 988
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 989
*/ // library marker kkossev.commonLib, line 990

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 992
    List<String> cmds = [] // library marker kkossev.commonLib, line 993
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 994
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 995
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 996
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 997
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 998
            } // library marker kkossev.commonLib, line 999
        } // library marker kkossev.commonLib, line 1000
    } // library marker kkossev.commonLib, line 1001
    return cmds // library marker kkossev.commonLib, line 1002
} // library marker kkossev.commonLib, line 1003

public void refresh() { // library marker kkossev.commonLib, line 1005
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 1006
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1007
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 1008
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 1009
        customCmds = customRefresh() // library marker kkossev.commonLib, line 1010
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 1011
    } // library marker kkossev.commonLib, line 1012
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 1013
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 1014
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 1015
    } // library marker kkossev.commonLib, line 1016
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1017
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 1018
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 1019
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1020
    } // library marker kkossev.commonLib, line 1021
    else { // library marker kkossev.commonLib, line 1022
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1023
    } // library marker kkossev.commonLib, line 1024
} // library marker kkossev.commonLib, line 1025

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, 'clearRefreshRequest', [overwrite: true]) } // library marker kkossev.commonLib, line 1027
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1028
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 1029

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 1031
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 1032
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 1033
        sendEvent(name: '_status_', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 1034
    } // library marker kkossev.commonLib, line 1035
    else { // library marker kkossev.commonLib, line 1036
        logInfo "${info}" // library marker kkossev.commonLib, line 1037
        sendEvent(name: '_status_', value: info, type: 'digital') // library marker kkossev.commonLib, line 1038
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1039
    } // library marker kkossev.commonLib, line 1040
} // library marker kkossev.commonLib, line 1041

public void ping() { // library marker kkossev.commonLib, line 1043
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1044
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true // library marker kkossev.commonLib, line 1045
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1046
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID // library marker kkossev.commonLib, line 1047
    if (isVirtual()) { runInMillis(10, 'virtualPong') } // library marker kkossev.commonLib, line 1048
    else if (device.getDataValue('manufacturer') == 'Aqara') { // library marker kkossev.commonLib, line 1049
        logDebug 'Aqara device ping...' // library marker kkossev.commonLib, line 1050
        sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [destEndpoint: 0x01], 0) ) // library marker kkossev.commonLib, line 1051
    } // library marker kkossev.commonLib, line 1052
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) } // library marker kkossev.commonLib, line 1053
    logDebug 'ping...' // library marker kkossev.commonLib, line 1054
} // library marker kkossev.commonLib, line 1055

private void virtualPong() { // library marker kkossev.commonLib, line 1057
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1058
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1059
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1060
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1061
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1062
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '9999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1063
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1064
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1065
        sendRttEvent() // library marker kkossev.commonLib, line 1066
    } // library marker kkossev.commonLib, line 1067
    else { // library marker kkossev.commonLib, line 1068
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1069
    } // library marker kkossev.commonLib, line 1070
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1071
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1072
} // library marker kkossev.commonLib, line 1073

public void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1075
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1076
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1077
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1078
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1079
    if (value == null) { // library marker kkossev.commonLib, line 1080
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1081
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1082
    } // library marker kkossev.commonLib, line 1083
    else { // library marker kkossev.commonLib, line 1084
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1085
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1086
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1087
    } // library marker kkossev.commonLib, line 1088
} // library marker kkossev.commonLib, line 1089

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1091
    if (cluster != null) { // library marker kkossev.commonLib, line 1092
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1093
    } // library marker kkossev.commonLib, line 1094
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1095
    return 'NULL' // library marker kkossev.commonLib, line 1096
} // library marker kkossev.commonLib, line 1097

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1099
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1100
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1101
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1102
} // library marker kkossev.commonLib, line 1103

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1105
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1106
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1107
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1108
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1109
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1110
    } // library marker kkossev.commonLib, line 1111
} // library marker kkossev.commonLib, line 1112

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1114
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1115
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1116
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1117
    if (state.health?.isHealthCheck == true) { // library marker kkossev.commonLib, line 1118
        logWarn 'device health check failed!' // library marker kkossev.commonLib, line 1119
        state.health?.checkCtr3 = (state.health?.checkCtr3 ?: 0 ) + 1 // library marker kkossev.commonLib, line 1120
        if (state.health?.checkCtr3 >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1121
            if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1122
                sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1123
            } // library marker kkossev.commonLib, line 1124
        } // library marker kkossev.commonLib, line 1125
        state.health['isHealthCheck'] = false // library marker kkossev.commonLib, line 1126
    } // library marker kkossev.commonLib, line 1127
} // library marker kkossev.commonLib, line 1128

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1130
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1131
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1132
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1133
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1134
    } // library marker kkossev.commonLib, line 1135
    else { // library marker kkossev.commonLib, line 1136
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1137
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1138
    } // library marker kkossev.commonLib, line 1139
} // library marker kkossev.commonLib, line 1140

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1142
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1143
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1144
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1145
} // library marker kkossev.commonLib, line 1146

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1148
private void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1149
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1150
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1151
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1152
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1153
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1154
    } // library marker kkossev.commonLib, line 1155
} // library marker kkossev.commonLib, line 1156

private void deviceHealthCheck() { // library marker kkossev.commonLib, line 1158
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1159
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1160
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1161
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1162
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1163
            logWarn 'not present!' // library marker kkossev.commonLib, line 1164
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1165
        } // library marker kkossev.commonLib, line 1166
    } // library marker kkossev.commonLib, line 1167
    else { // library marker kkossev.commonLib, line 1168
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})" // library marker kkossev.commonLib, line 1169
    } // library marker kkossev.commonLib, line 1170
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1171
    // added 03/06/2025 // library marker kkossev.commonLib, line 1172
    if (settings?.healthCheckMethod as int == 2) { // library marker kkossev.commonLib, line 1173
        state.health['isHealthCheck'] = true // library marker kkossev.commonLib, line 1174
        ping()  // proactively ping the device... // library marker kkossev.commonLib, line 1175
    } // library marker kkossev.commonLib, line 1176
} // library marker kkossev.commonLib, line 1177

private void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1179
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1180
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1181
    if (value == 'online') { // library marker kkossev.commonLib, line 1182
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1183
    } // library marker kkossev.commonLib, line 1184
    else { // library marker kkossev.commonLib, line 1185
        if (settings?.txtEnable) { log.warn "${device.displayName} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1186
    } // library marker kkossev.commonLib, line 1187
} // library marker kkossev.commonLib, line 1188

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1190
void updated() { // library marker kkossev.commonLib, line 1191
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1192
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1193
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1194
    unschedule() // library marker kkossev.commonLib, line 1195

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1197
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1198
        runIn(86400, 'logsOff') // library marker kkossev.commonLib, line 1199
    } // library marker kkossev.commonLib, line 1200
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1201
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1202
        runIn(1800, 'traceOff') // library marker kkossev.commonLib, line 1203
    } // library marker kkossev.commonLib, line 1204

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1206
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1207
        // schedule the periodic timer // library marker kkossev.commonLib, line 1208
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1209
        if (interval > 0) { // library marker kkossev.commonLib, line 1210
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1211
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthMethod]} method" // library marker kkossev.commonLib, line 1212
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1213
        } // library marker kkossev.commonLib, line 1214
    } // library marker kkossev.commonLib, line 1215
    else { // library marker kkossev.commonLib, line 1216
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1217
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1218
    } // library marker kkossev.commonLib, line 1219
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1220
        customUpdated() // library marker kkossev.commonLib, line 1221
    } // library marker kkossev.commonLib, line 1222

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1224
} // library marker kkossev.commonLib, line 1225

private void logsOff() { // library marker kkossev.commonLib, line 1227
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1228
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1229
} // library marker kkossev.commonLib, line 1230
private void traceOff() { // library marker kkossev.commonLib, line 1231
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1232
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1233
} // library marker kkossev.commonLib, line 1234

// the administrative / diagnostic commands drop-down list. Deliberately NOT named 'configure' - overloading the Configuration capability command made the dispatch depend on whether the platform happens to supply an argument // library marker kkossev.commonLib, line 1236
public void deviceUtilities(String command = null) { // library marker kkossev.commonLib, line 1237
    logInfo "deviceUtilities(${command})..." // library marker kkossev.commonLib, line 1238
    if (command == null || !(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1239
        configureHelp(command)      // nothing was selected, or the value is not one of ours - show the help and do nothing else // library marker kkossev.commonLib, line 1240
        return // library marker kkossev.commonLib, line 1241
    } // library marker kkossev.commonLib, line 1242
    // // library marker kkossev.commonLib, line 1243
    String func // library marker kkossev.commonLib, line 1244
    try { // library marker kkossev.commonLib, line 1245
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1246
        "$func"() // library marker kkossev.commonLib, line 1247
    } // library marker kkossev.commonLib, line 1248
    catch (e) { // library marker kkossev.commonLib, line 1249
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1250
        return // library marker kkossev.commonLib, line 1251
    } // library marker kkossev.commonLib, line 1252
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1253
} // library marker kkossev.commonLib, line 1254

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1256
void configureHelp(final String val = null) { // library marker kkossev.commonLib, line 1257
    logInfo "select one of the commands from the list: ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1258
    sendInfoEvent('Please select a command from the drop-down list')      // short _status_ event, auto-cleared after INFO_AUTO_CLEAR_PERIOD // library marker kkossev.commonLib, line 1259
} // library marker kkossev.commonLib, line 1260

public void loadAllDefaults() { // library marker kkossev.commonLib, line 1262
    logDebug 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1263
    deleteAllSettings() // library marker kkossev.commonLib, line 1264
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1265
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1266
    deleteAllStates() // library marker kkossev.commonLib, line 1267
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1268

    initialize() // library marker kkossev.commonLib, line 1270
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1271
    updated() // library marker kkossev.commonLib, line 1272
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1273
} // library marker kkossev.commonLib, line 1274

private void configureNow() { // library marker kkossev.commonLib, line 1276
    configure() // library marker kkossev.commonLib, line 1277
} // library marker kkossev.commonLib, line 1278

/** // library marker kkossev.commonLib, line 1280
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1281
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1282
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1283
 */ // library marker kkossev.commonLib, line 1284
void configure() { // library marker kkossev.commonLib, line 1285
    List<String> cmds = [] // library marker kkossev.commonLib, line 1286
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1287
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1288
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1289
    if (isTuya()) { // library marker kkossev.commonLib, line 1290
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1291
    } // library marker kkossev.commonLib, line 1292
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1293
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1294
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1295
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1296
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1297
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1298
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1299
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1300
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1301
    } // library marker kkossev.commonLib, line 1302
    else { // library marker kkossev.commonLib, line 1303
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1304
    } // library marker kkossev.commonLib, line 1305
} // library marker kkossev.commonLib, line 1306

 // Invoked when the device is installed with this driver automatically selected. // library marker kkossev.commonLib, line 1308
void installed() { // library marker kkossev.commonLib, line 1309
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1310
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1311
    // populate some default values for attributes // library marker kkossev.commonLib, line 1312
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1313
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1314
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1315
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1316
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1317
} // library marker kkossev.commonLib, line 1318

private void queryPowerSource() { // library marker kkossev.commonLib, line 1320
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1321
} // library marker kkossev.commonLib, line 1322

 // Invoked from 'LoadAllDefaults' // library marker kkossev.commonLib, line 1324
private void initialize() { // library marker kkossev.commonLib, line 1325
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1326
    logDebug "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1327
    if (device.getDataValue('powerSource') == null) { // library marker kkossev.commonLib, line 1328
        logDebug "initializing device powerSource 'unknown'" // library marker kkossev.commonLib, line 1329
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1330
    } // library marker kkossev.commonLib, line 1331
    if (this.respondsTo('customInitialize')) { customInitialize() }  // library marker kkossev.commonLib, line 1332
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1333
    updateTuyaVersion() // library marker kkossev.commonLib, line 1334
    updateAqaraVersion() // library marker kkossev.commonLib, line 1335
} // library marker kkossev.commonLib, line 1336

/* // library marker kkossev.commonLib, line 1338
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1339
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1340
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1341
*/ // library marker kkossev.commonLib, line 1342

static Integer safeToInt(Object val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1344
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1345
} // library marker kkossev.commonLib, line 1346

static Double safeToDouble(Object val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1348
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1349
} // library marker kkossev.commonLib, line 1350

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1352
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1353
} // library marker kkossev.commonLib, line 1354

public void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1356
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1357
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1358
        return // library marker kkossev.commonLib, line 1359
    } // library marker kkossev.commonLib, line 1360
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1361
    cmd.each { // library marker kkossev.commonLib, line 1362
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1363
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1364
            return // library marker kkossev.commonLib, line 1365
        } // library marker kkossev.commonLib, line 1366
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1367
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1368
    } // library marker kkossev.commonLib, line 1369
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1370
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1371
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1372
} // library marker kkossev.commonLib, line 1373

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1375

private String getDeviceInfo() { // library marker kkossev.commonLib, line 1377
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1378
} // library marker kkossev.commonLib, line 1379

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1381
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1382
} // library marker kkossev.commonLib, line 1383

//@CompileStatic // library marker kkossev.commonLib, line 1385
public void checkDriverVersion(final Map stateCopy) { // library marker kkossev.commonLib, line 1386
    if (stateCopy.driverVersion == null || driverVersionAndTimeStamp() != stateCopy.driverVersion) { // library marker kkossev.commonLib, line 1387
        logDebug "checkDriverVersion: updating the settings from the current driver version ${stateCopy.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1388
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()} from version ${stateCopy.driverVersion ?: 'unknown'}") // library marker kkossev.commonLib, line 1389
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1390
        initializeVars(false) // library marker kkossev.commonLib, line 1391
        updateTuyaVersion() // library marker kkossev.commonLib, line 1392
        updateAqaraVersion() // library marker kkossev.commonLib, line 1393
        if (this.respondsTo('customcheckDriverVersion')) { customcheckDriverVersion(stateCopy) } // library marker kkossev.commonLib, line 1394
    } // library marker kkossev.commonLib, line 1395
    if (state.states == null) { state.states = [:] } ; if (state.lastRx == null) { state.lastRx = [:] } ; if (state.lastTx == null) { state.lastTx = [:] } ; if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1396
} // library marker kkossev.commonLib, line 1397

// credits @thebearmay // library marker kkossev.commonLib, line 1399
String getModel() { // library marker kkossev.commonLib, line 1400
    try { // library marker kkossev.commonLib, line 1401
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1402
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1403
    } catch (ignore) { // library marker kkossev.commonLib, line 1404
        try { // library marker kkossev.commonLib, line 1405
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1406
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1407
                return model // library marker kkossev.commonLib, line 1408
            } // library marker kkossev.commonLib, line 1409
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1410
            return '' // library marker kkossev.commonLib, line 1411
        } // library marker kkossev.commonLib, line 1412
    } // library marker kkossev.commonLib, line 1413
} // library marker kkossev.commonLib, line 1414

// credits @thebearmay // library marker kkossev.commonLib, line 1416
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1417
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1418
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1419
    String revision = tokens.last() // library marker kkossev.commonLib, line 1420
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1421
} // library marker kkossev.commonLib, line 1422

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1424
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1425
    unschedule() // library marker kkossev.commonLib, line 1426
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1427
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1428

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1430
} // library marker kkossev.commonLib, line 1431

void resetStatistics() { // library marker kkossev.commonLib, line 1433
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1434
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1435
} // library marker kkossev.commonLib, line 1436

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1438
void resetStats() { // library marker kkossev.commonLib, line 1439
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1440
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1441
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1442
    state.stats.rxCtr = 0 ; state.stats.txCtr = 0 // library marker kkossev.commonLib, line 1443
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1444
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1445
    if (this.respondsTo('customResetStats')) { customResetStats() } // library marker kkossev.commonLib, line 1446
    logInfo 'statistics reset!' // library marker kkossev.commonLib, line 1447
} // library marker kkossev.commonLib, line 1448

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1450
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1451
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1452
        state.clear() // library marker kkossev.commonLib, line 1453
        unschedule() // library marker kkossev.commonLib, line 1454
        resetStats() // library marker kkossev.commonLib, line 1455
        if (this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1456
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1457
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1458
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1459
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1460
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1461
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1462
    } // library marker kkossev.commonLib, line 1463

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1465
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1466
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1467
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1468
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1469

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1471
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1472
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1473
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1474
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1475
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1476
    if (fullInit || settings?.ignoreDuplicatedZigbeeMessages == null) { device.updateSetting('ignoreDuplicatedZigbeeMessages', false) } // library marker kkossev.commonLib, line 1477
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1478

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1480

    // common libraries initialization // library marker kkossev.commonLib, line 1482
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024 // library marker kkossev.commonLib, line 1483
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024 // library marker kkossev.commonLib, line 1484
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1485
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1486
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1487
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1488
    // // library marker kkossev.commonLib, line 1489
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers! // library marker kkossev.commonLib, line 1490
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024 // library marker kkossev.commonLib, line 1491
    // // library marker kkossev.commonLib, line 1492
    // custom device driver specific initialization should be at the end // library marker kkossev.commonLib, line 1493
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1494
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1495
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1496

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1498
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1499
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1500
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1501
    if ( ep  != null) { // library marker kkossev.commonLib, line 1502
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1503
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1504
    } // library marker kkossev.commonLib, line 1505
    else { // library marker kkossev.commonLib, line 1506
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1507
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1508
    } // library marker kkossev.commonLib, line 1509
} // library marker kkossev.commonLib, line 1510

// not used!? // library marker kkossev.commonLib, line 1512
void setDestinationEP() { // library marker kkossev.commonLib, line 1513
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1514
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1515
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1516
} // library marker kkossev.commonLib, line 1517

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1519
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1520
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1521
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1522
void logError(final String msg) { if (settings?.txtEnable)   { log.error "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1523

// _DEBUG mode only // library marker kkossev.commonLib, line 1525
void getAllProperties() { // library marker kkossev.commonLib, line 1526
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1527
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1528
} // library marker kkossev.commonLib, line 1529

// delete all Preferences // library marker kkossev.commonLib, line 1531
void deleteAllSettings() { // library marker kkossev.commonLib, line 1532
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1533
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1534
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1535
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1536
} // library marker kkossev.commonLib, line 1537

// delete all attributes // library marker kkossev.commonLib, line 1539
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1540
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1541
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1542
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1543
} // library marker kkossev.commonLib, line 1544

// delete all State Variables // library marker kkossev.commonLib, line 1546
void deleteAllStates() { // library marker kkossev.commonLib, line 1547
    String stateDeleted = '' // library marker kkossev.commonLib, line 1548
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1549
    state.clear() // library marker kkossev.commonLib, line 1550
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1551
} // library marker kkossev.commonLib, line 1552

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1554
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1555
} // library marker kkossev.commonLib, line 1556

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1558
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1559
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1560
} // library marker kkossev.commonLib, line 1561

void testParse(String par) { // library marker kkossev.commonLib, line 1563
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1564
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1565
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1566
    parse(par) // library marker kkossev.commonLib, line 1567
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1568
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1569
} // library marker kkossev.commonLib, line 1570

Object testJob() { // library marker kkossev.commonLib, line 1572
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1573
} // library marker kkossev.commonLib, line 1574

/** // library marker kkossev.commonLib, line 1576
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1577
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1578
 */ // library marker kkossev.commonLib, line 1579
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1580
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1581
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1582
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1583
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1584
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1585
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1586
    String cron // library marker kkossev.commonLib, line 1587
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1588
    else { // library marker kkossev.commonLib, line 1589
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1590
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1591
    } // library marker kkossev.commonLib, line 1592
    return cron // library marker kkossev.commonLib, line 1593
} // library marker kkossev.commonLib, line 1594

// credits @thebearmay // library marker kkossev.commonLib, line 1596
String formatUptime() { // library marker kkossev.commonLib, line 1597
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1598
} // library marker kkossev.commonLib, line 1599

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1601
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1602
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1603
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1604
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1605
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1606
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1607
} // library marker kkossev.commonLib, line 1608

boolean isTuya() { // library marker kkossev.commonLib, line 1610
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1611
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1612
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1613
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1614
    return ((model?.startsWith('TS') && manufacturer?.startsWith('_T')) || model == 'HOBEIAN') ? true : false // library marker kkossev.commonLib, line 1615
} // library marker kkossev.commonLib, line 1616

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1618
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1619
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1620
    if (application != null) { // library marker kkossev.commonLib, line 1621
        Integer ver // library marker kkossev.commonLib, line 1622
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1623
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1624
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1625
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1626
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1627
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1628
        } // library marker kkossev.commonLib, line 1629
    } // library marker kkossev.commonLib, line 1630
} // library marker kkossev.commonLib, line 1631

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1633

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1635
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1636
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1637
    if (application != null) { // library marker kkossev.commonLib, line 1638
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1639
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1640
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1641
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1642
        } // library marker kkossev.commonLib, line 1643
    } // library marker kkossev.commonLib, line 1644
} // library marker kkossev.commonLib, line 1645

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1647
    try { // library marker kkossev.commonLib, line 1648
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1649
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1650
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1651
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1652
    } catch (e) { // library marker kkossev.commonLib, line 1653
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1654
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1655
    } // library marker kkossev.commonLib, line 1656
} // library marker kkossev.commonLib, line 1657

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1659
    try { // library marker kkossev.commonLib, line 1660
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1661
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1662
        return date.getTime() // library marker kkossev.commonLib, line 1663
    } catch (e) { // library marker kkossev.commonLib, line 1664
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1665
        return now() // library marker kkossev.commonLib, line 1666
    } // library marker kkossev.commonLib, line 1667
} // library marker kkossev.commonLib, line 1668

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1670
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1671
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1672
    int seconds = time % 60 // library marker kkossev.commonLib, line 1673
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1674
} // library marker kkossev.commonLib, line 1675

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (176) kkossev.onOffLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.onOffLib, line 1
library( // library marker kkossev.onOffLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee OnOff Cluster Library', name: 'onOffLib', namespace: 'kkossev', // library marker kkossev.onOffLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/onOffLib.groovy', documentationLink: '', // library marker kkossev.onOffLib, line 4
    version: '3.2.4' // library marker kkossev.onOffLib, line 5
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
 * ver. 3.2.3  2025-12-06 kkossev  - fixed a bug in off() and on() methods where clearIsDigital() was called too early // library marker kkossev.onOffLib, line 22
 * ver. 3.2.4  2026-08-23 kkossev  - bug fix: quoted the respondsTo('getDEVICE') argument in on() and off(); the bare identifier threw a NullPointerException in drivers without deviceProfileLib // library marker kkossev.onOffLib, line 23
 * // library marker kkossev.onOffLib, line 24
 *                                   TODO: // library marker kkossev.onOffLib, line 25
*/ // library marker kkossev.onOffLib, line 26

static String onOffLibVersion()   { '3.2.4' } // library marker kkossev.onOffLib, line 28
static String onOffLibStamp() { '2026/08/23 4:27 PM' } // library marker kkossev.onOffLib, line 29

@Field static final Boolean _THREE_STATE = true // library marker kkossev.onOffLib, line 31

metadata { // library marker kkossev.onOffLib, line 33
    capability 'Actuator' // library marker kkossev.onOffLib, line 34
    capability 'Switch' // library marker kkossev.onOffLib, line 35
    if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 36
        attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.onOffLib, line 37
    } // library marker kkossev.onOffLib, line 38
    // no commands // library marker kkossev.onOffLib, line 39
    preferences { // library marker kkossev.onOffLib, line 40
        if (settings?.advancedOptions == true && device != null && !(DEVICE_TYPE in ['Device', 'Thermostat'])) { // library marker kkossev.onOffLib, line 41
            input(name: 'ignoreDuplicated', type: 'bool', title: '<b>Ignore Duplicated Switch Events</b>', description: 'Some switches and plugs send periodically the switch status as a heart-beet ', defaultValue: true) // library marker kkossev.onOffLib, line 42
            input(name: 'alwaysOn', type: 'bool', title: '<b>Always On</b>', description: 'Disable switching off plugs and switches that must stay always On', defaultValue: false) // library marker kkossev.onOffLib, line 43
            if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 44
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: 'Experimental multi-state switch events', defaultValue: false // library marker kkossev.onOffLib, line 45
            } // library marker kkossev.onOffLib, line 46
        } // library marker kkossev.onOffLib, line 47
    } // library marker kkossev.onOffLib, line 48
} // library marker kkossev.onOffLib, line 49

@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.onOffLib, line 51
    defaultValue: 0, options: [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.onOffLib, line 52
] // library marker kkossev.onOffLib, line 53

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.onOffLib, line 55
    '0': 'switch off', '1': 'switch on', '2': 'switch last state' // library marker kkossev.onOffLib, line 56
] // library marker kkossev.onOffLib, line 57

@Field static final Map switchTypeOptions = [ // library marker kkossev.onOffLib, line 59
    '0': 'toggle', '1': 'state', '2': 'momentary' // library marker kkossev.onOffLib, line 60
] // library marker kkossev.onOffLib, line 61

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.onOffLib, line 63

/* // library marker kkossev.onOffLib, line 65
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 66
 * on/off cluster            0x0006     TODO - move to a library !!!!!!!!!!!!!!! // library marker kkossev.onOffLib, line 67
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 68
*/ // library marker kkossev.onOffLib, line 69
void standardParseOnOffCluster(final Map descMap) { // library marker kkossev.onOffLib, line 70
    /* // library marker kkossev.onOffLib, line 71
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.onOffLib, line 72
        customParseOnOffCluster(descMap) // library marker kkossev.onOffLib, line 73
    } // library marker kkossev.onOffLib, line 74
    else */ // library marker kkossev.onOffLib, line 75
    if (descMap.attrId == '0000') { // library marker kkossev.onOffLib, line 76
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.onOffLib, line 77
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.onOffLib, line 78
        sendSwitchEvent(rawValue) // library marker kkossev.onOffLib, line 79
    } // library marker kkossev.onOffLib, line 80
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.onOffLib, line 81
        parseOnOffAttributes(descMap) // library marker kkossev.onOffLib, line 82
    } // library marker kkossev.onOffLib, line 83
    else { // library marker kkossev.onOffLib, line 84
        if (descMap.attrId != null) { logWarn "standardParseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.onOffLib, line 85
        else { logDebug "standardParseOnOffCluster: skipped processing OnOff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.onOffLib, line 86
    } // library marker kkossev.onOffLib, line 87
} // library marker kkossev.onOffLib, line 88

void toggleX() { // library marker kkossev.onOffLib, line 90
    String descriptionText = 'central button switch is ' // library marker kkossev.onOffLib, line 91
    String state = '' // library marker kkossev.onOffLib, line 92
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.onOffLib, line 93
        state = 'on' // library marker kkossev.onOffLib, line 94
    } // library marker kkossev.onOffLib, line 95
    else { // library marker kkossev.onOffLib, line 96
        state = 'off' // library marker kkossev.onOffLib, line 97
    } // library marker kkossev.onOffLib, line 98
    descriptionText += state // library marker kkossev.onOffLib, line 99
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.onOffLib, line 100
    logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 101
} // library marker kkossev.onOffLib, line 102

void off() { // library marker kkossev.onOffLib, line 104
    if (this.respondsTo('customOff')) { customOff() ; return  } // library marker kkossev.onOffLib, line 105
    if ((settings?.alwaysOn ?: false) == true) { logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" ; return } // library marker kkossev.onOffLib, line 106
    List<String> cmds = [] // library marker kkossev.onOffLib, line 107
    // added 06/29/2024 - control Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 108
    if (this.respondsTo('getDEVICE')) {   // defined in deviceProfileLib // library marker kkossev.onOffLib, line 109
        Map switchMap = getAttributesMap('switch') // library marker kkossev.onOffLib, line 110
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  0  : 1 // library marker kkossev.onOffLib, line 111
        if (switchMap != null && switchMap != [:]) { // library marker kkossev.onOffLib, line 112
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue) // library marker kkossev.onOffLib, line 113
            logTrace "off() Tuya cmds=${cmds}" // library marker kkossev.onOffLib, line 114
        } // library marker kkossev.onOffLib, line 115
    } // library marker kkossev.onOffLib, line 116
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 117
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.onOffLib, line 118
    } // library marker kkossev.onOffLib, line 119

    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 121
    logDebug "off() currentState=${currentState}" // library marker kkossev.onOffLib, line 122
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 123
        if (currentState == 'off') { // library marker kkossev.onOffLib, line 124
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 125
        } // library marker kkossev.onOffLib, line 126
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.onOffLib, line 127
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 128
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 129
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 130
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 131
    } // library marker kkossev.onOffLib, line 132
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 133
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 134
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 135
} // library marker kkossev.onOffLib, line 136

void on() { // library marker kkossev.onOffLib, line 138
    if (this.respondsTo('customOn')) { customOn() ; return } // library marker kkossev.onOffLib, line 139
    List<String> cmds = [] // library marker kkossev.onOffLib, line 140
    // added 06/29/2024 - control Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 141
    if (this.respondsTo('getDEVICE')) {   // defined in deviceProfileLib // library marker kkossev.onOffLib, line 142
        Map switchMap = getAttributesMap('switch') // library marker kkossev.onOffLib, line 143
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  1  : 0 // library marker kkossev.onOffLib, line 144
        if (switchMap != null && switchMap != [:]) { // library marker kkossev.onOffLib, line 145
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue) // library marker kkossev.onOffLib, line 146
            logTrace "on() Tuya cmds=${cmds}" // library marker kkossev.onOffLib, line 147
        } // library marker kkossev.onOffLib, line 148
    } // library marker kkossev.onOffLib, line 149
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 150
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.onOffLib, line 151
    } // library marker kkossev.onOffLib, line 152
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 153
    logDebug "on() currentState=${currentState}" // library marker kkossev.onOffLib, line 154
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 155
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.onOffLib, line 156
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 157
        } // library marker kkossev.onOffLib, line 158
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.onOffLib, line 159
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 160
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 161
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 162
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 163
    } // library marker kkossev.onOffLib, line 164
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 165
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 166
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 167
} // library marker kkossev.onOffLib, line 168

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.onOffLib, line 170
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.onOffLib, line 171
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.onOffLib, line 172
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.onOffLib, line 173
    } // library marker kkossev.onOffLib, line 174
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.onOffLib, line 175
    Map map = [:] // library marker kkossev.onOffLib, line 176
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.onOffLib, line 177
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.onOffLib, line 178
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.onOffLib, line 179
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false)) && !isRefresh) { // library marker kkossev.onOffLib, line 180
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.onOffLib, line 181
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 182
        return // library marker kkossev.onOffLib, line 183
    } // library marker kkossev.onOffLib, line 184
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.onOffLib, line 185
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.onOffLib, line 186
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.onOffLib, line 187
    if (lastSwitch != value) { // library marker kkossev.onOffLib, line 188
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.onOffLib, line 189
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 190
        state.states['lastSwitch'] = value // library marker kkossev.onOffLib, line 191
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 192
    } else { // library marker kkossev.onOffLib, line 193
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 194
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 195
    } // library marker kkossev.onOffLib, line 196
    map.name = 'switch' // library marker kkossev.onOffLib, line 197
    map.value = value // library marker kkossev.onOffLib, line 198
    if (isRefresh) { // library marker kkossev.onOffLib, line 199
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.onOffLib, line 200
        map.isStateChange = true // library marker kkossev.onOffLib, line 201
    } else { // library marker kkossev.onOffLib, line 202
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.onOffLib, line 203
    } // library marker kkossev.onOffLib, line 204
    logInfo "${map.descriptionText}" // library marker kkossev.onOffLib, line 205
    sendEvent(map) // library marker kkossev.onOffLib, line 206
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.onOffLib, line 207
        customSwitchEventPostProcesing(map) // library marker kkossev.onOffLib, line 208
    } // library marker kkossev.onOffLib, line 209
} // library marker kkossev.onOffLib, line 210

void parseOnOffAttributes(final Map it) { // library marker kkossev.onOffLib, line 212
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 213
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.onOffLib, line 214
    String mode // library marker kkossev.onOffLib, line 215
    String attrName // library marker kkossev.onOffLib, line 216
    if (it.value == null) { // library marker kkossev.onOffLib, line 217
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.onOffLib, line 218
        return // library marker kkossev.onOffLib, line 219
    } // library marker kkossev.onOffLib, line 220
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.onOffLib, line 221
    switch (it.attrId) { // library marker kkossev.onOffLib, line 222
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.onOffLib, line 223
            attrName = 'Global Scene Control' // library marker kkossev.onOffLib, line 224
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.onOffLib, line 225
            break // library marker kkossev.onOffLib, line 226
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.onOffLib, line 227
            attrName = 'On Time' // library marker kkossev.onOffLib, line 228
            mode = value // library marker kkossev.onOffLib, line 229
            break // library marker kkossev.onOffLib, line 230
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.onOffLib, line 231
            attrName = 'Off Wait Time' // library marker kkossev.onOffLib, line 232
            mode = value // library marker kkossev.onOffLib, line 233
            break // library marker kkossev.onOffLib, line 234
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.onOffLib, line 235
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 236
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.onOffLib, line 237
            break // library marker kkossev.onOffLib, line 238
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.onOffLib, line 239
            attrName = 'Child Lock' // library marker kkossev.onOffLib, line 240
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.onOffLib, line 241
            break // library marker kkossev.onOffLib, line 242
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.onOffLib, line 243
            attrName = 'LED mode' // library marker kkossev.onOffLib, line 244
            if (isCircuitBreaker()) { // library marker kkossev.onOffLib, line 245
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.onOffLib, line 246
            } // library marker kkossev.onOffLib, line 247
            else { // library marker kkossev.onOffLib, line 248
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.onOffLib, line 249
            } // library marker kkossev.onOffLib, line 250
            break // library marker kkossev.onOffLib, line 251
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.onOffLib, line 252
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 253
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.onOffLib, line 254
            break // library marker kkossev.onOffLib, line 255
        case '8003' : //  Over current alarm // library marker kkossev.onOffLib, line 256
            attrName = 'Over current alarm' // library marker kkossev.onOffLib, line 257
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.onOffLib, line 258
            break // library marker kkossev.onOffLib, line 259
        default : // library marker kkossev.onOffLib, line 260
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 261
            return // library marker kkossev.onOffLib, line 262
    } // library marker kkossev.onOffLib, line 263
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.onOffLib, line 264
} // library marker kkossev.onOffLib, line 265

List<String> onOffRefresh() { // library marker kkossev.onOffLib, line 267
    logDebug 'onOffRefresh()' // library marker kkossev.onOffLib, line 268
    List<String> cmds = zigbee.readAttribute(0x0006, 0x0000, [:], delay = 100) // library marker kkossev.onOffLib, line 269
    return cmds // library marker kkossev.onOffLib, line 270
} // library marker kkossev.onOffLib, line 271

void onOfInitializeVars( boolean fullInit = false ) { // library marker kkossev.onOffLib, line 273
    logDebug "onOfInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.onOffLib, line 274
    if (fullInit || settings?.ignoreDuplicated == null) { device.updateSetting('ignoreDuplicated', true) } // library marker kkossev.onOffLib, line 275
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.onOffLib, line 276
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.onOffLib, line 277
} // library marker kkossev.onOffLib, line 278

// ~~~~~ end include (176) kkossev.onOffLib ~~~~~

// ~~~~~ start include (171) kkossev.batteryLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoJavaUtilDate, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
library( // library marker kkossev.batteryLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Battery Library', name: 'batteryLib', namespace: 'kkossev', // library marker kkossev.batteryLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/batteryLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-batteryLib', // library marker kkossev.batteryLib, line 4
    version: '3.2.4' // library marker kkossev.batteryLib, line 5
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
 * ver. 3.2.4  2026-08-23 kkossev  - bug fix: non-Tuya battery percentage is now rounded instead of truncated (raw 1 was reported as 0%) // library marker kkossev.batteryLib, line 19
 * // library marker kkossev.batteryLib, line 20
 *                                   TODO: add an Advanced Option resetBatteryToZeroWhenOffline // library marker kkossev.batteryLib, line 21
 *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.batteryLib, line 22
*/ // library marker kkossev.batteryLib, line 23

static String batteryLibVersion()   { '3.2.4' } // library marker kkossev.batteryLib, line 25
static String batteryLibStamp() { '2026/08/23 3:43 PM' } // library marker kkossev.batteryLib, line 26

metadata { // library marker kkossev.batteryLib, line 28
    capability 'Battery' // library marker kkossev.batteryLib, line 29
    attribute  'batteryVoltage', 'number' // library marker kkossev.batteryLib, line 30
    attribute  'lastBattery', 'date'         // last battery event time - added in 3.2.0 05/21/2024 // library marker kkossev.batteryLib, line 31
    // no commands // library marker kkossev.batteryLib, line 32
    preferences { // library marker kkossev.batteryLib, line 33
        if (device && advancedOptions == true) { // library marker kkossev.batteryLib, line 34
            if ('BatteryVoltage' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 35
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: 'Convert battery voltage to battery Percentage remaining.' // library marker kkossev.batteryLib, line 36
            } // library marker kkossev.batteryLib, line 37
            if ('BatteryDelay' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 38
                input(name: 'batteryDelay', type: 'enum', title: '<b>Battery Events Delay</b>', description:'Select the Battery Events Delay<br>(default is <b>no delay</b>)', options: DelayBatteryOpts.options, defaultValue: DelayBatteryOpts.defaultValue) // library marker kkossev.batteryLib, line 39
            } // library marker kkossev.batteryLib, line 40
        } // library marker kkossev.batteryLib, line 41
    } // library marker kkossev.batteryLib, line 42
} // library marker kkossev.batteryLib, line 43

@Field static final Map DelayBatteryOpts = [ defaultValue: 0, options: [0: 'No delay', 30: '30 seconds', 3600: '1 hour', 14400: '4 hours', 28800: '8 hours', 43200: '12 hours']] // library marker kkossev.batteryLib, line 45

public void standardParsePowerCluster(final Map descMap) { // library marker kkossev.batteryLib, line 47
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.batteryLib, line 48
    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.batteryLib, line 49
    if (descMap.attrId == '0020') { // battery voltage // library marker kkossev.batteryLib, line 50
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 51
        state.stats['bVoltCtr'] = (state.stats['bVoltCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 52
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.batteryLib, line 53
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.batteryLib, line 54
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.batteryLib, line 55
        } // library marker kkossev.batteryLib, line 56
    } // library marker kkossev.batteryLib, line 57
    else if (descMap.attrId == '0021') { // battery percentage // library marker kkossev.batteryLib, line 58
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 59
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 60
        if (isTuya()) { // library marker kkossev.batteryLib, line 61
            sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 62
        } // library marker kkossev.batteryLib, line 63
        else { // library marker kkossev.batteryLib, line 64
            sendBatteryPercentageEvent(Math.round(rawValue / 2.0) as int) // library marker kkossev.batteryLib, line 65
        } // library marker kkossev.batteryLib, line 66
    } // library marker kkossev.batteryLib, line 67
    else { // library marker kkossev.batteryLib, line 68
        logWarn "customParsePowerCluster: zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.batteryLib, line 69
    } // library marker kkossev.batteryLib, line 70
} // library marker kkossev.batteryLib, line 71

public void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.batteryLib, line 73
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.batteryLib, line 74
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 75
    Map result = [:] // library marker kkossev.batteryLib, line 76
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.batteryLib, line 77
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.batteryLib, line 78
        BigDecimal minVolts = 2.2 // library marker kkossev.batteryLib, line 79
        BigDecimal maxVolts = 3.2 // library marker kkossev.batteryLib, line 80
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.batteryLib, line 81
        int roundedPct = Math.round(pct * 100) // library marker kkossev.batteryLib, line 82
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.batteryLib, line 83
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.batteryLib, line 84
        if (convertToPercent == true) { // library marker kkossev.batteryLib, line 85
            result.value = Math.min(100, roundedPct) // library marker kkossev.batteryLib, line 86
            result.name = 'battery' // library marker kkossev.batteryLib, line 87
            result.unit  = '%' // library marker kkossev.batteryLib, line 88
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.batteryLib, line 89
        } // library marker kkossev.batteryLib, line 90
        else { // library marker kkossev.batteryLib, line 91
            result.value = volts // library marker kkossev.batteryLib, line 92
            result.name = 'batteryVoltage' // library marker kkossev.batteryLib, line 93
            result.unit  = 'V' // library marker kkossev.batteryLib, line 94
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.batteryLib, line 95
        } // library marker kkossev.batteryLib, line 96
        result.type = 'physical' // library marker kkossev.batteryLib, line 97
        result.isStateChange = true // library marker kkossev.batteryLib, line 98
        logInfo "${result.descriptionText}" // library marker kkossev.batteryLib, line 99
        sendEvent(result) // library marker kkossev.batteryLib, line 100
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 101
    } // library marker kkossev.batteryLib, line 102
    else { // library marker kkossev.batteryLib, line 103
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.batteryLib, line 104
    } // library marker kkossev.batteryLib, line 105
} // library marker kkossev.batteryLib, line 106

public void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.batteryLib, line 108
    if ((batteryPercent as int) == 255) { // library marker kkossev.batteryLib, line 109
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.batteryLib, line 110
        return // library marker kkossev.batteryLib, line 111
    } // library marker kkossev.batteryLib, line 112
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 113
    Map map = [:] // library marker kkossev.batteryLib, line 114
    map.name = 'battery' // library marker kkossev.batteryLib, line 115
    map.timeStamp = now() // library marker kkossev.batteryLib, line 116
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.batteryLib, line 117
    map.unit  = '%' // library marker kkossev.batteryLib, line 118
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.batteryLib, line 119
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.batteryLib, line 120
    map.isStateChange = true // library marker kkossev.batteryLib, line 121
    // // library marker kkossev.batteryLib, line 122
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.batteryLib, line 123
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.batteryLib, line 124
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.batteryLib, line 125
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.batteryLib, line 126
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.batteryLib, line 127
        // send it now! // library marker kkossev.batteryLib, line 128
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.batteryLib, line 129
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 130
    } // library marker kkossev.batteryLib, line 131
    else { // library marker kkossev.batteryLib, line 132
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.batteryLib, line 133
        map.delayed = delayedTime // library marker kkossev.batteryLib, line 134
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.batteryLib, line 135
        map.lastBattery = lastBattery // library marker kkossev.batteryLib, line 136
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.batteryLib, line 137
        runIn(delayedTime, 'sendDelayedBatteryPercentageEvent', [overwrite: true, data: map]) // library marker kkossev.batteryLib, line 138
    } // library marker kkossev.batteryLib, line 139
} // library marker kkossev.batteryLib, line 140

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.batteryLib, line 142
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 143
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 144
    sendEvent(map) // library marker kkossev.batteryLib, line 145
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 146
} // library marker kkossev.batteryLib, line 147

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.batteryLib, line 149
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.batteryLib, line 150
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 151
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 152
    sendEvent(map) // library marker kkossev.batteryLib, line 153
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 154
} // library marker kkossev.batteryLib, line 155

public int tuyaToBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 157
    int rawValue = fncmd // library marker kkossev.batteryLib, line 158
    switch (fncmd) { // library marker kkossev.batteryLib, line 159
        case 0: rawValue = 100; break // Battery Full // library marker kkossev.batteryLib, line 160
        case 1: rawValue = 75;  break // Battery High // library marker kkossev.batteryLib, line 161
        case 2: rawValue = 50;  break // Battery Medium // library marker kkossev.batteryLib, line 162
        case 3: rawValue = 25;  break // Battery Low // library marker kkossev.batteryLib, line 163
        case 4: rawValue = 100; break // Tuya 3 in 1 -> USB powered // library marker kkossev.batteryLib, line 164
        // for all other values >4 we will use the raw value, expected to be the real battery level 4..100% // library marker kkossev.batteryLib, line 165
    } // library marker kkossev.batteryLib, line 166
    return rawValue // library marker kkossev.batteryLib, line 167
} // library marker kkossev.batteryLib, line 168

public void handleTuyaBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 170
    int rawValue = tuyaToBatteryLevel(fncmd) // library marker kkossev.batteryLib, line 171
    sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 172
} // library marker kkossev.batteryLib, line 173

public void batteryInitializeVars( boolean fullInit = false ) { // library marker kkossev.batteryLib, line 175
    logDebug "batteryInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.batteryLib, line 176
    if (device.hasCapability('Battery')) { // library marker kkossev.batteryLib, line 177
        if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.batteryLib, line 178
        if (fullInit || settings?.batteryDelay == null) { device.updateSetting('batteryDelay', [value: DelayBatteryOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.batteryLib, line 179
    } // library marker kkossev.batteryLib, line 180
} // library marker kkossev.batteryLib, line 181

public List<String> batteryRefresh() { // library marker kkossev.batteryLib, line 183
    List<String> cmds = [] // library marker kkossev.batteryLib, line 184
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 100)         // battery voltage // library marker kkossev.batteryLib, line 185
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 100)         // battery percentage // library marker kkossev.batteryLib, line 186
    return cmds // library marker kkossev.batteryLib, line 187
} // library marker kkossev.batteryLib, line 188

// ~~~~~ end include (171) kkossev.batteryLib ~~~~~

// ~~~~~ start include (172) kkossev.temperatureLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.temperatureLib, line 1
library( // library marker kkossev.temperatureLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Temperature Library', name: 'temperatureLib', namespace: 'kkossev', // library marker kkossev.temperatureLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/temperatureLib.groovy', documentationLink: '', // library marker kkossev.temperatureLib, line 4
    version: '3.3.2' // library marker kkossev.temperatureLib, line 5
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
 * ver. 3.3.0  2025-09-15 kkossev  - commonLib 4.0.0 allignment; added temperatureOffset // library marker kkossev.temperatureLib, line 18
 * ver. 3.3.1  2025-10-31 kkossev  - bugfix: isRefresh was not checked if temperature delta < 0.1 // library marker kkossev.temperatureLib, line 19
 * ver. 3.3.2  2026-08-23 kkossev  - bugfix: isRefresh now also bypasses the minReportingTime queue; bugfix: the first event after the states are cleared is no longer delayed by minReportingTime // library marker kkossev.temperatureLib, line 20
 * // library marker kkossev.temperatureLib, line 21
 *                                   TODO: unschedule('sendDelayedTempEvent') only if needed (add boolean flag to sendDelayedTempEvent()) // library marker kkossev.temperatureLib, line 22
 *                                   TODO: check for negative temperature values in standardParseTemperatureCluster() // library marker kkossev.temperatureLib, line 23
*/ // library marker kkossev.temperatureLib, line 24

static String temperatureLibVersion()   { '3.3.2' } // library marker kkossev.temperatureLib, line 26
static String temperatureLibStamp() { '2026/08/23 3:31 PM' } // library marker kkossev.temperatureLib, line 27

metadata { // library marker kkossev.temperatureLib, line 29
    capability 'TemperatureMeasurement' // library marker kkossev.temperatureLib, line 30
    // no commands // library marker kkossev.temperatureLib, line 31
    preferences { // library marker kkossev.temperatureLib, line 32
        if (device && advancedOptions == true) { // library marker kkossev.temperatureLib, line 33
            if ('ReportingConfiguration' in DEVICE?.capabilities) { // library marker kkossev.temperatureLib, line 34
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: 'Minimum reporting interval, seconds <i>(1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 35
                if (!(deviceType in ['mmWaveSensor', 'Thermostat', 'TRV'])) { // library marker kkossev.temperatureLib, line 36
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: 'Maximum reporting interval, seconds <i>(120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.temperatureLib, line 37
                } // library marker kkossev.temperatureLib, line 38
            } // library marker kkossev.temperatureLib, line 39
        } // library marker kkossev.temperatureLib, line 40
        input name: 'temperatureOffset', type: 'decimal', title: '<b>Temperature Offset</b>', description: '<i>Adjust temperature by this many degrees</i>', range: '-100..100', defaultValue: 0 // library marker kkossev.temperatureLib, line 41
   } // library marker kkossev.temperatureLib, line 42
} // library marker kkossev.temperatureLib, line 43

void standardParseTemperatureCluster(final Map descMap) { // library marker kkossev.temperatureLib, line 45
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.temperatureLib, line 46
    if (descMap.attrId == '0000') { // library marker kkossev.temperatureLib, line 47
        int value = hexStrToSignedInt(descMap.value) // library marker kkossev.temperatureLib, line 48
        handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.temperatureLib, line 49
    } // library marker kkossev.temperatureLib, line 50
    else { // library marker kkossev.temperatureLib, line 51
        logWarn "standardParseTemperatureCluster() - unknown attribute ${descMap.attrId} value=${descMap.value}" // library marker kkossev.temperatureLib, line 52
    } // library marker kkossev.temperatureLib, line 53
} // library marker kkossev.temperatureLib, line 54

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.temperatureLib, line 56
    Map eventMap = [:] // library marker kkossev.temperatureLib, line 57
    BigDecimal temperature = safeToBigDecimal(temperaturePar).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 58
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.temperatureLib, line 59
    eventMap.name = 'temperature' // library marker kkossev.temperatureLib, line 60
    if (location.temperatureScale == 'F') { // library marker kkossev.temperatureLib, line 61
        temperature = ((temperature * 1.8) + 32).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 62
        eventMap.unit = '\u00B0F' // library marker kkossev.temperatureLib, line 63
    } // library marker kkossev.temperatureLib, line 64
    else { // library marker kkossev.temperatureLib, line 65
        eventMap.unit = '\u00B0C' // library marker kkossev.temperatureLib, line 66
    } // library marker kkossev.temperatureLib, line 67
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 68
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 69
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.temperatureLib, line 70
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.temperatureLib, line 71

    boolean isRefresh = state.states['isRefresh'] == true // library marker kkossev.temperatureLib, line 73

    if (!isRefresh && Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.temperatureLib, line 75
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.temperatureLib, line 76
        return // library marker kkossev.temperatureLib, line 77
    } // library marker kkossev.temperatureLib, line 78
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.temperatureLib, line 79
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.temperatureLib, line 80
    if (isRefresh) { // library marker kkossev.temperatureLib, line 81
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.temperatureLib, line 82
        eventMap.isStateChange = true // library marker kkossev.temperatureLib, line 83
    } // library marker kkossev.temperatureLib, line 84
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 85
    // a null lastRx time means there is no previous event to space this one from - send it right away instead of queueing it for minTime seconds // library marker kkossev.temperatureLib, line 86
    Integer timeElapsed = state.lastRx['tempTime'] != null ? Math.round((now() - (state.lastRx['tempTime'] as long)) / 1000) as Integer : minTime // library marker kkossev.temperatureLib, line 87
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.temperatureLib, line 88
    if (isRefresh || timeElapsed >= minTime) { // library marker kkossev.temperatureLib, line 89
        logInfo "${eventMap.descriptionText}" // library marker kkossev.temperatureLib, line 90
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.temperatureLib, line 91
        state.lastRx['tempTime'] = now() // library marker kkossev.temperatureLib, line 92
        sendEvent(eventMap) // library marker kkossev.temperatureLib, line 93
    } // library marker kkossev.temperatureLib, line 94
    else {         // queue the event // library marker kkossev.temperatureLib, line 95
        eventMap.type = 'delayed' // library marker kkossev.temperatureLib, line 96
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.temperatureLib, line 97
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.temperatureLib, line 98
    } // library marker kkossev.temperatureLib, line 99
} // library marker kkossev.temperatureLib, line 100

void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.temperatureLib, line 102
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.temperatureLib, line 103
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.temperatureLib, line 104
    sendEvent(eventMap) // library marker kkossev.temperatureLib, line 105
} // library marker kkossev.temperatureLib, line 106

List<String> temperatureLibInitializeDevice() { // library marker kkossev.temperatureLib, line 108
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 109
    cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.temperatureLib, line 110
    logDebug "temperatureLibInitializeDevice() cmds=${cmds}" // library marker kkossev.temperatureLib, line 111
    return cmds // library marker kkossev.temperatureLib, line 112
} // library marker kkossev.temperatureLib, line 113

List<String> temperatureRefresh() { // library marker kkossev.temperatureLib, line 115
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 116
    cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.temperatureLib, line 117
    return cmds // library marker kkossev.temperatureLib, line 118
} // library marker kkossev.temperatureLib, line 119

// ~~~~~ end include (172) kkossev.temperatureLib ~~~~~

// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
library( // library marker kkossev.deviceProfileLib, line 1
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLib', namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 2
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/deviceProfileLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-deviceProfileLib', // library marker kkossev.deviceProfileLib, line 3
    version: '3.5.8' // library marker kkossev.deviceProfileLib, line 4
) // library marker kkossev.deviceProfileLib, line 5
/* // library marker kkossev.deviceProfileLib, line 6
 *  Device Profile Library (V3) // library marker kkossev.deviceProfileLib, line 7
 * // library marker kkossev.deviceProfileLib, line 8
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.deviceProfileLib, line 9
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.deviceProfileLib, line 10
 * // library marker kkossev.deviceProfileLib, line 11
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.deviceProfileLib, line 12
 * // library marker kkossev.deviceProfileLib, line 13
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.deviceProfileLib, line 14
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.deviceProfileLib, line 15
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.deviceProfileLib, line 16
 * // library marker kkossev.deviceProfileLib, line 17
 * ver. 1.0.0  2023-11-04 kkossev  - added deviceProfileLib (based on Tuya 4 In 1 driver) // library marker kkossev.deviceProfileLib, line 18
 * ver. 3.0.0  2023-11-27 kkossev  - fixes for use with commonLib; added processClusterAttributeFromDeviceProfile() method; added validateAndFixPreferences() method;  inputIt bug fix; signedInt Preproc method; // library marker kkossev.deviceProfileLib, line 19
 * ver. 3.0.1  2023-12-02 kkossev  - release candidate // library marker kkossev.deviceProfileLib, line 20
 * ver. 3.0.2  2023-12-17 kkossev  - inputIt moved to the preferences section; setfunction replaced by customSetFunction; Groovy Linting; // library marker kkossev.deviceProfileLib, line 21
 * ver. 3.0.4  2024-03-30 kkossev  - more Groovy Linting; processClusterAttributeFromDeviceProfile exception fix; // library marker kkossev.deviceProfileLib, line 22
 * ver. 3.1.0  2024-04-03 kkossev  - more Groovy Linting; deviceProfilesV3, enum pars bug fix; // library marker kkossev.deviceProfileLib, line 23
 * ver. 3.1.1  2024-04-21 kkossev  - deviceProfilesV3 bug fix; tuyaDPs list of maps bug fix; resetPreferencesToDefaults bug fix; // library marker kkossev.deviceProfileLib, line 24
 * ver. 3.1.2  2024-05-05 kkossev  - added isSpammyDeviceProfile() // library marker kkossev.deviceProfileLib, line 25
 * ver. 3.1.3  2024-05-21 kkossev  - skip processClusterAttributeFromDeviceProfile if cluster or attribute or value is missing // library marker kkossev.deviceProfileLib, line 26
 * ver. 3.2.0  2024-05-25 kkossev  - commonLib 3.2.0 allignment; // library marker kkossev.deviceProfileLib, line 27
 * ver. 3.2.1  2024-06-06 kkossev  - Tuya Multi Sensor 4 In 1 (V3) driver allignment (customProcessDeviceProfileEvent); getDeviceProfilesMap bug fix; forcedProfile is always shown in preferences; // library marker kkossev.deviceProfileLib, line 28
 * ver. 3.3.0  2024-06-29 kkossev  - empty preferences bug fix; zclWriteAttribute delay 50 ms; added advanced check in inputIt(); fixed 'Cannot get property 'rw' on null object' bug; fixed enum attributes first event numeric value bug; // library marker kkossev.deviceProfileLib, line 29
 * ver. 3.3.1  2024-07-06 kkossev  - added powerSource event in the initEventsDeviceProfile // library marker kkossev.deviceProfileLib, line 30
 * ver. 3.3.2  2024-08-18 kkossev  - release 3.3.2 // library marker kkossev.deviceProfileLib, line 31
 * ver. 3.3.3  2024-08-18 kkossev  - sendCommand and setPar commands commented out; must be declared in the main driver where really needed // library marker kkossev.deviceProfileLib, line 32
 * ver. 3.3.4  2024-09-28 kkossev  - fixed exceptions in resetPreferencesToDefaults() and initEventsDeviceProfile() // library marker kkossev.deviceProfileLib, line 33
 * ver. 3.4.0  2025-02-02 kkossev  - deviceProfilesV3 optimizations (defaultFingerprint); is2in1() mod // library marker kkossev.deviceProfileLib, line 34
 * ver. 3.4.1  2025-02-02 kkossev  - setPar help improvements; // library marker kkossev.deviceProfileLib, line 35
 * ver. 3.4.2  2025-03-24 kkossev  - added refreshFromConfigureReadList() method; documentation update; getDeviceNameAndProfile uses DEVICE.description instead of deviceJoinName // library marker kkossev.deviceProfileLib, line 36
 * ver. 3.4.3  2025-04-25 kkossev  - HE platfrom version 2.4.1.x decimal preferences patch/workaround. // library marker kkossev.deviceProfileLib, line 37
 * ver. 3.5.0  2025-08-14 kkossev  - zclWriteAttribute() support for forced destinationEndpoint in the attributes map // library marker kkossev.deviceProfileLib, line 38
 * ver. 3.5.1  2025-09-15 kkossev  - commonLib ver 4.0.0 allignment; log.trace leftover removed;  // library marker kkossev.deviceProfileLib, line 39
 * ver. 3.5.2  2025-10-04 kkossev  - SIMULATED_DEVICE_MODEL and SIMULATED_DEVICE_MANUFACTURER added (for testing with simulated devices) // library marker kkossev.deviceProfileLib, line 40
 * ver. 3.5.3  2025-12-06 kkossev  - added digital/physical type to events in customProcessDeviceProfileEvent() // library marker kkossev.deviceProfileLib, line 41
 * ver. 3.5.4  2026-02-04 kkossev  - changed inputIt min param rounding to floor instead of ceil // library marker kkossev.deviceProfileLib, line 42
 * ver. 3.5.5  2026-03-05 kkossev  - added deviceProfilesV3defaults?.defaultCommands // library marker kkossev.deviceProfileLib, line 43
 * ver. 3.5.6  2026-06-04 kkossev  - fixed setPar() invalid virtual enum parameter false error when preference key is passed instead of label // library marker kkossev.deviceProfileLib, line 44
 * ver. 3.5.7  2026-08-03 kkossev  - (BUGS.md B13) processFoundItem() no longer skips illuminance events based on the raw-DP dedupe, which ignored illuminanceCoeff // library marker kkossev.deviceProfileLib, line 45
 * ver. 3.5.8  2026-08-23 kkossev  - (TODO.md B6) getDeviceNameAndProfile() rewritten with explicit for loops so the first fingerprint match really exits the method - a 'return' inside the nested .each only left the closure, so the last match won; validateAndFixPreferences() closure 'return false' changed to plain 'return' to make the skip-this-preference intent explicit; (TODO.md B10) back-ported shouldDetectDeviceProfile() from deviceProfileLibV4 - deviceProfileInitializeVars() now re-resolves a profile stored as UNKNOWN, which the old 'state.deviceProfile == null' guard skipped on the checkDriverVersion() -> initializeVars(false) path taken after a driver code update // library marker kkossev.deviceProfileLib, line 46
 * // library marker kkossev.deviceProfileLib, line 47
 *                                   TODO - remove the 2-in-1 patch ! // library marker kkossev.deviceProfileLib, line 48
 *                                   TODO - add updateStateUnknownDPs (from the 4-in-1 driver) // library marker kkossev.deviceProfileLib, line 49
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 50
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 51
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off) // library marker kkossev.deviceProfileLib, line 52
 *                                   TODO: allow NULL parameters default values in the device profiles // library marker kkossev.deviceProfileLib, line 53
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 54
 * // library marker kkossev.deviceProfileLib, line 55
*/ // library marker kkossev.deviceProfileLib, line 56

static String deviceProfileLibVersion()   { '3.5.8' } // library marker kkossev.deviceProfileLib, line 58
static String deviceProfileLibStamp() { '2026/08/23 4:48 PM' } // library marker kkossev.deviceProfileLib, line 59
import groovy.json.* // library marker kkossev.deviceProfileLib, line 60
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 61
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 62
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 63
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 64

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 66

metadata { // library marker kkossev.deviceProfileLib, line 68
    // no capabilities // library marker kkossev.deviceProfileLib, line 69
    // no attributes // library marker kkossev.deviceProfileLib, line 70
    /* // library marker kkossev.deviceProfileLib, line 71
    // copy the following commands to the main driver, if needed // library marker kkossev.deviceProfileLib, line 72
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 73
        [name:'command', type: 'STRING', description: '▶️ Run one of the commands supported by this device profile • Leave empty to list the valid names in the log', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 74
        [name:'val',     type: 'STRING', description: 'Optional value, needed only by some commands', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 75
    ] // library marker kkossev.deviceProfileLib, line 76
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 77
            [name:'par', type: 'STRING', description: '🎛️ Set a device profile preference and write it to the device • Leave empty to list the valid parameter names in the log', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 78
            [name:'val', type: 'STRING', description: 'Leave empty to see the allowed range or values for that parameter', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 79
    ] // library marker kkossev.deviceProfileLib, line 80
    */ // library marker kkossev.deviceProfileLib, line 81
    preferences { // library marker kkossev.deviceProfileLib, line 82
        if (device) { // library marker kkossev.deviceProfileLib, line 83
            input(name: 'forcedProfile', type: 'enum', title: '<b>⚠️ Device Profile</b>', description: 'Which set of datapoints, attributes and preferences this driver uses for your device. Matched automatically from the model and manufacturer when the device is paired.<br>Change it manually only if your device was not recognized - the wrong profile stops it working correctly.<br>After changing the profile, pair the device again to your hub, without deleting it! Otherwise the new configuration may never reach a battery-powered sleepy device.',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 84
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 85
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 86
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 87
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 88
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 89
                        input inputMap // library marker kkossev.deviceProfileLib, line 90
                    } // library marker kkossev.deviceProfileLib, line 91
                } // library marker kkossev.deviceProfileLib, line 92
            } // library marker kkossev.deviceProfileLib, line 93
        } // library marker kkossev.deviceProfileLib, line 94
    } // library marker kkossev.deviceProfileLib, line 95
} // library marker kkossev.deviceProfileLib, line 96

private boolean is2in1() { return getDeviceProfile().startsWith('TS0601_2IN1')  }   // patch! // library marker kkossev.deviceProfileLib, line 98

public String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 100
public Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 101
public Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 102

public List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLib, line 104
    if (deviceProfilesV3 == null) { // library marker kkossev.deviceProfileLib, line 105
        if (deviceProfilesV2 == null) { return [] } // library marker kkossev.deviceProfileLib, line 106
        return deviceProfilesV2.values().description as List<String> // library marker kkossev.deviceProfileLib, line 107
    } // library marker kkossev.deviceProfileLib, line 108
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLib, line 109
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 110
        if ((profileMap.device?.isDepricated ?: false) != true) { // library marker kkossev.deviceProfileLib, line 111
            activeProfiles.add(profileMap.description ?: '---') // library marker kkossev.deviceProfileLib, line 112
        } // library marker kkossev.deviceProfileLib, line 113
    } // library marker kkossev.deviceProfileLib, line 114
    return activeProfiles // library marker kkossev.deviceProfileLib, line 115
} // library marker kkossev.deviceProfileLib, line 116

// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 118

/** // library marker kkossev.deviceProfileLib, line 120
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 121
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 122
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 123
 */ // library marker kkossev.deviceProfileLib, line 124
public String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 125
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 126
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 127
    else { return null } // library marker kkossev.deviceProfileLib, line 128
} // library marker kkossev.deviceProfileLib, line 129

/** // library marker kkossev.deviceProfileLib, line 131
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 132
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 133
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 134
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 135
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 136
 */ // library marker kkossev.deviceProfileLib, line 137
private Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 138
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 139
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 140
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 141
    def preference // library marker kkossev.deviceProfileLib, line 142
    try { // library marker kkossev.deviceProfileLib, line 143
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 144
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 145
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 146
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 147
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 148
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 149
        } // library marker kkossev.deviceProfileLib, line 150
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 151
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 152
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 153
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 154
        } // library marker kkossev.deviceProfileLib, line 155
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 156
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 157
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 158
        } // library marker kkossev.deviceProfileLib, line 159
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 160
    } catch (e) { // library marker kkossev.deviceProfileLib, line 161
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 162
        return [:] // library marker kkossev.deviceProfileLib, line 163
    } // library marker kkossev.deviceProfileLib, line 164
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 165
    return foundMap // library marker kkossev.deviceProfileLib, line 166
} // library marker kkossev.deviceProfileLib, line 167

public Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 169
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 170
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 171
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 172
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 173
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 174
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 175
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 176
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 177
            return foundMap // library marker kkossev.deviceProfileLib, line 178
        } // library marker kkossev.deviceProfileLib, line 179
    } // library marker kkossev.deviceProfileLib, line 180
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 181
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 182
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 183
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 184
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 185
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 186
            return foundMap // library marker kkossev.deviceProfileLib, line 187
        } // library marker kkossev.deviceProfileLib, line 188
    } // library marker kkossev.deviceProfileLib, line 189
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 190
    return [:] // library marker kkossev.deviceProfileLib, line 191
} // library marker kkossev.deviceProfileLib, line 192

/** // library marker kkossev.deviceProfileLib, line 194
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 195
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 196
 */ // library marker kkossev.deviceProfileLib, line 197
public void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 198
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 199
    if (DEVICE == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 200
    Map preferences = DEVICE?.preferences ?: [:] // library marker kkossev.deviceProfileLib, line 201
    if (preferences == null || preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 202
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 203
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 204
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 205
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 206
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 207
            return // continue // library marker kkossev.deviceProfileLib, line 208
        } // library marker kkossev.deviceProfileLib, line 209
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 210
        if (parMap == null || parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 211
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 212
        if (parMap?.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 213
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 214
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 215
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 216
    } // library marker kkossev.deviceProfileLib, line 217
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 218
} // library marker kkossev.deviceProfileLib, line 219

/** // library marker kkossev.deviceProfileLib, line 221
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 222
 * // library marker kkossev.deviceProfileLib, line 223
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 224
 */ // library marker kkossev.deviceProfileLib, line 225
private List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 226
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 227
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 228
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 229
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 230
    } // library marker kkossev.deviceProfileLib, line 231
    return validPars // library marker kkossev.deviceProfileLib, line 232
} // library marker kkossev.deviceProfileLib, line 233

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 235
private def getScaledPreferenceValue(String preference, Map dpMap) {        // TODO - not used ??? // library marker kkossev.deviceProfileLib, line 236
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 237
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 238
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 239
    def scaledValue // library marker kkossev.deviceProfileLib, line 240
    if (value == null) { // library marker kkossev.deviceProfileLib, line 241
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 242
        return null // library marker kkossev.deviceProfileLib, line 243
    } // library marker kkossev.deviceProfileLib, line 244
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 245
        case 'number' : // library marker kkossev.deviceProfileLib, line 246
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 247
            break // library marker kkossev.deviceProfileLib, line 248
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 249
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 250
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 251
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 252
            } // library marker kkossev.deviceProfileLib, line 253
            break // library marker kkossev.deviceProfileLib, line 254
        case 'bool' : // library marker kkossev.deviceProfileLib, line 255
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 256
            break // library marker kkossev.deviceProfileLib, line 257
        case 'enum' : // library marker kkossev.deviceProfileLib, line 258
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 259
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 260
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 261
                return null // library marker kkossev.deviceProfileLib, line 262
            } // library marker kkossev.deviceProfileLib, line 263
            scaledValue = value // library marker kkossev.deviceProfileLib, line 264
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 265
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 266
            } // library marker kkossev.deviceProfileLib, line 267
            break // library marker kkossev.deviceProfileLib, line 268
        default : // library marker kkossev.deviceProfileLib, line 269
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 270
            return null // library marker kkossev.deviceProfileLib, line 271
    } // library marker kkossev.deviceProfileLib, line 272
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 273
    return scaledValue // library marker kkossev.deviceProfileLib, line 274
} // library marker kkossev.deviceProfileLib, line 275

// called from customUpdated() method in the custom driver // library marker kkossev.deviceProfileLib, line 277
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 278
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 279
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 280
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 281
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 282
        return // library marker kkossev.deviceProfileLib, line 283
    } // library marker kkossev.deviceProfileLib, line 284
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 285
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 286
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 287
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 288
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 289
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 290
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 291
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 292
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 293
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 294
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 295
                // scale the value // library marker kkossev.deviceProfileLib, line 296
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 297
            } // library marker kkossev.deviceProfileLib, line 298
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLib, line 299
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLib, line 300
            } // library marker kkossev.deviceProfileLib, line 301
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLib, line 302
        } // library marker kkossev.deviceProfileLib, line 303
        else { logDebug "warning: couldn't find map for preference ${name}" ; return }  // TODO - supress the warning if the preference was boolean true/false // library marker kkossev.deviceProfileLib, line 304
    } // library marker kkossev.deviceProfileLib, line 305
    return // library marker kkossev.deviceProfileLib, line 306
} // library marker kkossev.deviceProfileLib, line 307

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 309
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 310
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 311
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 312
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 313
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 314
} // library marker kkossev.deviceProfileLib, line 315
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 316
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 317
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 318
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 319
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 320
} // library marker kkossev.deviceProfileLib, line 321
int invert(int val) { // library marker kkossev.deviceProfileLib, line 322
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 323
    else { return val } // library marker kkossev.deviceProfileLib, line 324
} // library marker kkossev.deviceProfileLib, line 325

// called from setPar and sendAttribite methods for non-Tuya DPs // library marker kkossev.deviceProfileLib, line 327
private List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 328
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 329
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 330
    Map map = [:] // library marker kkossev.deviceProfileLib, line 331
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 332
    try { // library marker kkossev.deviceProfileLib, line 333
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 334
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 335
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 336
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 337
        map['ep'] = (attributesMap.ep != null && attributesMap.ep != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.ep) as Integer : null // library marker kkossev.deviceProfileLib, line 338
    } // library marker kkossev.deviceProfileLib, line 339
    catch (e) { logWarn "zclWriteAttribute: Exception caught while splitting the cluster and attribute <b>${attributesMap?.at}</b> (scaledValue=${scaledValue}) : '${e}'" ; return [] } // library marker kkossev.deviceProfileLib, line 340
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 341
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 342
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLib, line 343
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 344
    } // library marker kkossev.deviceProfileLib, line 345
    if ((map.mfgCode != null && map.mfgCode != '') || (map.ep != null && map.ep != '')) { // library marker kkossev.deviceProfileLib, line 346
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 347
        Map ep = map.ep != null ? ['destEndpoint':map.ep] : [:] // library marker kkossev.deviceProfileLib, line 348
        Map mapOptions = [:] // library marker kkossev.deviceProfileLib, line 349
        if (mfgCode) mapOptions.putAll(mfgCode) // library marker kkossev.deviceProfileLib, line 350
        if (ep) mapOptions.putAll(ep) // library marker kkossev.deviceProfileLib, line 351
        //log.trace "$mapOptions" // library marker kkossev.deviceProfileLib, line 352
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mapOptions, delay = 50) // library marker kkossev.deviceProfileLib, line 353
    } // library marker kkossev.deviceProfileLib, line 354
    else { // library marker kkossev.deviceProfileLib, line 355
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 50) // library marker kkossev.deviceProfileLib, line 356
    } // library marker kkossev.deviceProfileLib, line 357
    return cmds // library marker kkossev.deviceProfileLib, line 358
} // library marker kkossev.deviceProfileLib, line 359

/** // library marker kkossev.deviceProfileLib, line 361
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 362
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 363
 * // library marker kkossev.deviceProfileLib, line 364
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 365
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 366
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 367
 */ // library marker kkossev.deviceProfileLib, line 368
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 369
private def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 370
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 371
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 372
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 373
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 374
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 375
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 376
        case 'number' : // library marker kkossev.deviceProfileLib, line 377
            // TODO - negative values ! // library marker kkossev.deviceProfileLib, line 378
            // TODO - better conversion to integer! // library marker kkossev.deviceProfileLib, line 379
            value = safeToInt(val, 0) // library marker kkossev.deviceProfileLib, line 380
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 381
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 382
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 383
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 384
            } // library marker kkossev.deviceProfileLib, line 385
            else { // library marker kkossev.deviceProfileLib, line 386
                scaledValue = value // library marker kkossev.deviceProfileLib, line 387
            } // library marker kkossev.deviceProfileLib, line 388
            break // library marker kkossev.deviceProfileLib, line 389

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 391
            value = safeToDouble(val, 0.0) // library marker kkossev.deviceProfileLib, line 392
            // scale the value // library marker kkossev.deviceProfileLib, line 393
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 394
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 395
            } // library marker kkossev.deviceProfileLib, line 396
            else { // library marker kkossev.deviceProfileLib, line 397
                scaledValue = value // library marker kkossev.deviceProfileLib, line 398
            } // library marker kkossev.deviceProfileLib, line 399
            break // library marker kkossev.deviceProfileLib, line 400

        case 'bool' : // library marker kkossev.deviceProfileLib, line 402
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 403
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 404
            else { // library marker kkossev.deviceProfileLib, line 405
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 406
                return null // library marker kkossev.deviceProfileLib, line 407
            } // library marker kkossev.deviceProfileLib, line 408
            break // library marker kkossev.deviceProfileLib, line 409
        case 'enum' : // library marker kkossev.deviceProfileLib, line 410
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 411
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 412
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 413
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 414
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 415
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 416
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 417
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 418
            } // library marker kkossev.deviceProfileLib, line 419
            else { // library marker kkossev.deviceProfileLib, line 420
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 421
            } // library marker kkossev.deviceProfileLib, line 422
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 423
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 424
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 425
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 426
                // find the key for the value // library marker kkossev.deviceProfileLib, line 427
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 428
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 429
                if (key == null) { // library marker kkossev.deviceProfileLib, line 430
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 431
                    return null // library marker kkossev.deviceProfileLib, line 432
                } // library marker kkossev.deviceProfileLib, line 433
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 434
            //return null // library marker kkossev.deviceProfileLib, line 435
            } // library marker kkossev.deviceProfileLib, line 436
            break // library marker kkossev.deviceProfileLib, line 437
        default : // library marker kkossev.deviceProfileLib, line 438
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 439
            return null // library marker kkossev.deviceProfileLib, line 440
    } // library marker kkossev.deviceProfileLib, line 441
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 442
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 443
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 444
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 445
        return null // library marker kkossev.deviceProfileLib, line 446
    } // library marker kkossev.deviceProfileLib, line 447
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 448
    return scaledValue // library marker kkossev.deviceProfileLib, line 449
} // library marker kkossev.deviceProfileLib, line 450

/** // library marker kkossev.deviceProfileLib, line 452
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 453
 * // library marker kkossev.deviceProfileLib, line 454
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 455
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 456
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 457
 */ // library marker kkossev.deviceProfileLib, line 458
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 459
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 460
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 461
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 462
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 463
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 464
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 465
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 466
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 467
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 468
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 469
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 470
    if (scaledValue == null) { // library marker kkossev.deviceProfileLib, line 471
        logTrace "$dpMap  ${dpMap.map}" // library marker kkossev.deviceProfileLib, line 472
        String helpTxt = "setPar: invalid parameter ${par} value <b>${val}</b>." // library marker kkossev.deviceProfileLib, line 473
        if (dpMap.min != null && dpMap.max != null) { helpTxt += " Must be in the range ${dpMap.min} to ${dpMap.max}" } // library marker kkossev.deviceProfileLib, line 474
        if (dpMap.map != null) { helpTxt += " Must be one of ${dpMap.map}" } // library marker kkossev.deviceProfileLib, line 475
        logInfo helpTxt // library marker kkossev.deviceProfileLib, line 476
        return false // library marker kkossev.deviceProfileLib, line 477
    } // library marker kkossev.deviceProfileLib, line 478

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 480
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 481
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 482
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 483
        logDebug "setPar: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 484
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 485
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 486
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 487
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 488
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 489
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 490
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 491
            return true // library marker kkossev.deviceProfileLib, line 492
        } // library marker kkossev.deviceProfileLib, line 493
        else { // library marker kkossev.deviceProfileLib, line 494
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 495
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 496
        } // library marker kkossev.deviceProfileLib, line 497
    } // library marker kkossev.deviceProfileLib, line 498
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 499
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 500
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 501
        def valMiscType // library marker kkossev.deviceProfileLib, line 502
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 503
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 504
            // find the key for the value // library marker kkossev.deviceProfileLib, line 505
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 506
            if (key == null) { // library marker kkossev.deviceProfileLib, line 507
                // val may be the numeric key itself (e.g. when called from updated()) // library marker kkossev.deviceProfileLib, line 508
                key = dpMap.map.containsKey(safeToInt(val)) ? val : null // library marker kkossev.deviceProfileLib, line 509
            } // library marker kkossev.deviceProfileLib, line 510
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 511
            if (key == null) { // library marker kkossev.deviceProfileLib, line 512
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 513
                return false // library marker kkossev.deviceProfileLib, line 514
            } // library marker kkossev.deviceProfileLib, line 515
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 516
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 517
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 518
        } // library marker kkossev.deviceProfileLib, line 519
        else { // library marker kkossev.deviceProfileLib, line 520
            valMiscType = val // library marker kkossev.deviceProfileLib, line 521
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 522
        } // library marker kkossev.deviceProfileLib, line 523
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 524
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 525
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 526
        return true // library marker kkossev.deviceProfileLib, line 527
    } // library marker kkossev.deviceProfileLib, line 528

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 530
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 531

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 533
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 534
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 535
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 536
        // Tuya DP // library marker kkossev.deviceProfileLib, line 537
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 538
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 539
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 540
            return false // library marker kkossev.deviceProfileLib, line 541
        } // library marker kkossev.deviceProfileLib, line 542
        else { // library marker kkossev.deviceProfileLib, line 543
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 544
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 545
            return true // library marker kkossev.deviceProfileLib, line 546
        } // library marker kkossev.deviceProfileLib, line 547
    } // library marker kkossev.deviceProfileLib, line 548
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 549
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 550
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 551
        int signedIntScaled = convertSignedInts(scaledValue, dpMap) // library marker kkossev.deviceProfileLib, line 552
        cmds = zclWriteAttribute(dpMap, signedIntScaled) // library marker kkossev.deviceProfileLib, line 553
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 554
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 555
            return false // library marker kkossev.deviceProfileLib, line 556
        } // library marker kkossev.deviceProfileLib, line 557
    } // library marker kkossev.deviceProfileLib, line 558
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 559
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 560
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 561
    return true // library marker kkossev.deviceProfileLib, line 562
} // library marker kkossev.deviceProfileLib, line 563

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 565
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 566
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 567
public List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 568
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 569
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 570
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 571
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 572
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 573
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 574
        return [] // library marker kkossev.deviceProfileLib, line 575
    } // library marker kkossev.deviceProfileLib, line 576
    String dpType // library marker kkossev.deviceProfileLib, line 577
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 578
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 579
    } // library marker kkossev.deviceProfileLib, line 580
    else { // library marker kkossev.deviceProfileLib, line 581
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 582
    } // library marker kkossev.deviceProfileLib, line 583
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 584
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 585
        return [] // library marker kkossev.deviceProfileLib, line 586
    } // library marker kkossev.deviceProfileLib, line 587
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 588
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 589
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 590
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 591
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 592
    } // library marker kkossev.deviceProfileLib, line 593
    else { // library marker kkossev.deviceProfileLib, line 594
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 595
    } // library marker kkossev.deviceProfileLib, line 596
    return cmds // library marker kkossev.deviceProfileLib, line 597
} // library marker kkossev.deviceProfileLib, line 598

private int convertSignedInts(int val, Map dpMap) { // library marker kkossev.deviceProfileLib, line 600
    if (dpMap.dt == '0x28') { // library marker kkossev.deviceProfileLib, line 601
        if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 602
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 603
    } // library marker kkossev.deviceProfileLib, line 604
    else if (dpMap.dt == '0x29') { // library marker kkossev.deviceProfileLib, line 605
        if (val > 32767) { return (val as int) - 65536 } // library marker kkossev.deviceProfileLib, line 606
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 607
    } // library marker kkossev.deviceProfileLib, line 608
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 609
} // library marker kkossev.deviceProfileLib, line 610

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 612
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 613
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 614
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 615
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 616
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'DEVICE.preferences is empty!' ; return false } // library marker kkossev.deviceProfileLib, line 617

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 619
    //log.trace "sendAttribute: dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 620
    if (dpMap == null || dpMap?.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 621
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 622
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 623
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 624
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 625
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 626
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 627
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 628
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 629
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 630
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 631
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 632
        try { // library marker kkossev.deviceProfileLib, line 633
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 634
        } // library marker kkossev.deviceProfileLib, line 635
        catch (e) { // library marker kkossev.deviceProfileLib, line 636
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 637
            return false // library marker kkossev.deviceProfileLib, line 638
        } // library marker kkossev.deviceProfileLib, line 639
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 640
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 641
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 642
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 643
            return true // library marker kkossev.deviceProfileLib, line 644
        } // library marker kkossev.deviceProfileLib, line 645
        else { // library marker kkossev.deviceProfileLib, line 646
            logDebug "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 647
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 648
        } // library marker kkossev.deviceProfileLib, line 649
    } // library marker kkossev.deviceProfileLib, line 650
    else { // library marker kkossev.deviceProfileLib, line 651
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 652
    } // library marker kkossev.deviceProfileLib, line 653
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 654
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 655
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 656
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 657
        // patch !! // library marker kkossev.deviceProfileLib, line 658
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 659
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 660
        } // library marker kkossev.deviceProfileLib, line 661
        else { // library marker kkossev.deviceProfileLib, line 662
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 663
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 664
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 665
        } // library marker kkossev.deviceProfileLib, line 666
        return true // library marker kkossev.deviceProfileLib, line 667
    } // library marker kkossev.deviceProfileLib, line 668
    else { // library marker kkossev.deviceProfileLib, line 669
        logTrace "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 670
    } // library marker kkossev.deviceProfileLib, line 671
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 672
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 673
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 674
    try { // library marker kkossev.deviceProfileLib, line 675
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 676
    } // library marker kkossev.deviceProfileLib, line 677
    catch (e) { // library marker kkossev.deviceProfileLib, line 678
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 679
        return false // library marker kkossev.deviceProfileLib, line 680
    } // library marker kkossev.deviceProfileLib, line 681
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 682
        // Tuya DP // library marker kkossev.deviceProfileLib, line 683
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 684
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 685
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 686
            return false // library marker kkossev.deviceProfileLib, line 687
        } // library marker kkossev.deviceProfileLib, line 688
        else { // library marker kkossev.deviceProfileLib, line 689
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 690
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 691
            return true // library marker kkossev.deviceProfileLib, line 692
        } // library marker kkossev.deviceProfileLib, line 693
    } // library marker kkossev.deviceProfileLib, line 694
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 695
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 696
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 697
    } // library marker kkossev.deviceProfileLib, line 698
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 699
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 700
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 701
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 702
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 703
            return false // library marker kkossev.deviceProfileLib, line 704
        } // library marker kkossev.deviceProfileLib, line 705
    } // library marker kkossev.deviceProfileLib, line 706
    else { // library marker kkossev.deviceProfileLib, line 707
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 708
        return false // library marker kkossev.deviceProfileLib, line 709
    } // library marker kkossev.deviceProfileLib, line 710
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 711
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 712
    return true // library marker kkossev.deviceProfileLib, line 713
} // library marker kkossev.deviceProfileLib, line 714

/** // library marker kkossev.deviceProfileLib, line 716
 * SENDS a list of Zigbee commands to be sent to the device. // library marker kkossev.deviceProfileLib, line 717
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 718
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 719
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 720
 */ // library marker kkossev.deviceProfileLib, line 721
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 722
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 723
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 724
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 725
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 726
    // merge default commands with device-specific commands (device-specific takes precedence) // library marker kkossev.deviceProfileLib, line 727
    Map allCommandsMap = [:]  // library marker kkossev.deviceProfileLib, line 728
    if (deviceProfilesV3defaults?.defaultCommands != null) { allCommandsMap.putAll(deviceProfilesV3defaults.defaultCommands) } // library marker kkossev.deviceProfileLib, line 729
    if (DEVICE?.commands != null) { allCommandsMap.putAll(DEVICE.commands) } // library marker kkossev.deviceProfileLib, line 730
    if (allCommandsMap.isEmpty()) { // library marker kkossev.deviceProfileLib, line 731
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 732
        return false // library marker kkossev.deviceProfileLib, line 733
    } // library marker kkossev.deviceProfileLib, line 734
    // build case-insensitive command lookup map (lowercase -> actual command name) // library marker kkossev.deviceProfileLib, line 735
    Map<String, String> commandLookupMap = [:] // library marker kkossev.deviceProfileLib, line 736
    allCommandsMap.each { k, v ->  // library marker kkossev.deviceProfileLib, line 737
        commandLookupMap[k.toLowerCase()] = k  // library marker kkossev.deviceProfileLib, line 738
    } // library marker kkossev.deviceProfileLib, line 739
    // find the actual command name (case-insensitive lookup) // library marker kkossev.deviceProfileLib, line 740
    String actualCommand = command != null ? commandLookupMap[command.toLowerCase()] : null // library marker kkossev.deviceProfileLib, line 741
    if (actualCommand == null) { // library marker kkossev.deviceProfileLib, line 742
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${commandLookupMap.values()}" // library marker kkossev.deviceProfileLib, line 743
        return false // library marker kkossev.deviceProfileLib, line 744
    } // library marker kkossev.deviceProfileLib, line 745
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 746
    def func, funcResult // library marker kkossev.deviceProfileLib, line 747
    try { // library marker kkossev.deviceProfileLib, line 748
        // look up function from merged commands map // library marker kkossev.deviceProfileLib, line 749
        func = allCommandsMap.find { it.key == actualCommand }?.value // library marker kkossev.deviceProfileLib, line 750
        // added 01/25/2025 : the commands now can be shorted : instead of a map kay and value 'printFingerprints':'printFingerprints' we can skip the value when it is the same:  'printFingerprints:'  - the value is the same as the key // library marker kkossev.deviceProfileLib, line 751
        if (func == null || func == '') { // library marker kkossev.deviceProfileLib, line 752
            func = actualCommand // library marker kkossev.deviceProfileLib, line 753
        } // library marker kkossev.deviceProfileLib, line 754
        if (val != null && val != '') { // library marker kkossev.deviceProfileLib, line 755
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 756
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 757
        } // library marker kkossev.deviceProfileLib, line 758
        else { // library marker kkossev.deviceProfileLib, line 759
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 760
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 761
        } // library marker kkossev.deviceProfileLib, line 762
    } // library marker kkossev.deviceProfileLib, line 763
    catch (e) { // library marker kkossev.deviceProfileLib, line 764
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 765
        return false // library marker kkossev.deviceProfileLib, line 766
    } // library marker kkossev.deviceProfileLib, line 767
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 768
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 769
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 770
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 771
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 772
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 773
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 774
        } // library marker kkossev.deviceProfileLib, line 775
    } // library marker kkossev.deviceProfileLib, line 776
    else if (funcResult == null) { // library marker kkossev.deviceProfileLib, line 777
        return false // library marker kkossev.deviceProfileLib, line 778
    } // library marker kkossev.deviceProfileLib, line 779
     else { // library marker kkossev.deviceProfileLib, line 780
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 781
        return false // library marker kkossev.deviceProfileLib, line 782
    } // library marker kkossev.deviceProfileLib, line 783
    return true // library marker kkossev.deviceProfileLib, line 784
} // library marker kkossev.deviceProfileLib, line 785

/** // library marker kkossev.deviceProfileLib, line 787
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 788
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 789
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 790
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 791
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 792
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 793
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 794
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 795
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 796
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 797
 */ // library marker kkossev.deviceProfileLib, line 798
public Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 799
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 800
    Map input = [:] // library marker kkossev.deviceProfileLib, line 801
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 802
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 803
    Object preference // library marker kkossev.deviceProfileLib, line 804
    try { preference = DEVICE?.preferences["$param"] } // library marker kkossev.deviceProfileLib, line 805
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 806
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 807
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } } // library marker kkossev.deviceProfileLib, line 808
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 809
    /* // library marker kkossev.deviceProfileLib, line 810
    // TODO - check if this is neccessary? isTuyaDP is not defined! // library marker kkossev.deviceProfileLib, line 811
    try { isTuyaDP = preference.isNumber() } // library marker kkossev.deviceProfileLib, line 812
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 813
    */ // library marker kkossev.deviceProfileLib, line 814
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 815
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 816
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 817
    if (foundMap == null || foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 818
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 819
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) { // library marker kkossev.deviceProfileLib, line 820
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" } // library marker kkossev.deviceProfileLib, line 821
        return [:] // library marker kkossev.deviceProfileLib, line 822
    } // library marker kkossev.deviceProfileLib, line 823
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 824
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 825
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 826
    //input.description = (foundMap.description ?: foundMap.title)?.replaceAll(/<\/?b>/, '')  // if description is not defined, use the title // library marker kkossev.deviceProfileLib, line 827
    input.description = foundMap.description ?: ''   // if description is not defined, skip it // library marker kkossev.deviceProfileLib, line 828
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 829
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 830
            //input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 831
            input.range = "${Math.floor(foundMap.min) as int}..${Math.ceil(foundMap.max) as int}" // library marker kkossev.deviceProfileLib, line 832
        } // library marker kkossev.deviceProfileLib, line 833
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 834
            if (input.description != '') { input.description += '<br>' } // library marker kkossev.deviceProfileLib, line 835
            input.description += "<i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 836
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 837
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 838
            } // library marker kkossev.deviceProfileLib, line 839
        } // library marker kkossev.deviceProfileLib, line 840
    } // library marker kkossev.deviceProfileLib, line 841
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 842
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 843
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 844
    }/* // library marker kkossev.deviceProfileLib, line 845
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 846
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 847
    }*/ // library marker kkossev.deviceProfileLib, line 848
    else { // library marker kkossev.deviceProfileLib, line 849
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 850
        return [:] // library marker kkossev.deviceProfileLib, line 851
    } // library marker kkossev.deviceProfileLib, line 852
    if (foundMap.defVal != null) { // library marker kkossev.deviceProfileLib, line 853
        input.defaultValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 854
    } // library marker kkossev.deviceProfileLib, line 855
    return input // library marker kkossev.deviceProfileLib, line 856
} // library marker kkossev.deviceProfileLib, line 857

/** // library marker kkossev.deviceProfileLib, line 859
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 860
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 861
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 862
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 863
 */ // library marker kkossev.deviceProfileLib, line 864
public List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 865
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 866
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 867
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 868
    if (_DEBUG && SIMULATED_DEVICE_MODEL != null && SIMULATED_DEVICE_MANUFACTURER != null) { // library marker kkossev.deviceProfileLib, line 869
        deviceModel = SIMULATED_DEVICE_MODEL // library marker kkossev.deviceProfileLib, line 870
        deviceManufacturer = SIMULATED_DEVICE_MANUFACTURER // library marker kkossev.deviceProfileLib, line 871
        logWarn "<b>getDeviceNameAndProfile: using SIMULATED_DEVICE_MODEL ${SIMULATED_DEVICE_MODEL} and SIMULATED_DEVICE_MANUFACTURER ${SIMULATED_DEVICE_MANUFACTURER} in _DEBUG mode</b>" // library marker kkossev.deviceProfileLib, line 872
    } // library marker kkossev.deviceProfileLib, line 873
    // explicit loops (not .each{}) so 'return' here exits the whole method on first match - // library marker kkossev.deviceProfileLib, line 874
    // closure 'return' inside nested .each only exits the innermost closure, causing last-match-wins on duplicate fingerprints // library marker kkossev.deviceProfileLib, line 875
    if (deviceProfilesV3 != null && !deviceProfilesV3.isEmpty()) { // library marker kkossev.deviceProfileLib, line 876
        for (profileEntry in deviceProfilesV3) { // library marker kkossev.deviceProfileLib, line 877
            String profileName = profileEntry.key as String // library marker kkossev.deviceProfileLib, line 878
            Map profileMap = profileEntry.value as Map // library marker kkossev.deviceProfileLib, line 879
            for (fingerprint in (profileMap.fingerprints ?: [])) { // library marker kkossev.deviceProfileLib, line 880
                if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 881
                    deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 882
                    deviceName = fingerprint.deviceJoinName ?: profileMap.description ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 883
                    logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 884
                    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 885
                } // library marker kkossev.deviceProfileLib, line 886
            } // library marker kkossev.deviceProfileLib, line 887
        } // library marker kkossev.deviceProfileLib, line 888
    } // library marker kkossev.deviceProfileLib, line 889
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 890
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 891
    } // library marker kkossev.deviceProfileLib, line 892
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 893
} // library marker kkossev.deviceProfileLib, line 894

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 896
public void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 897
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 898
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 899
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 900
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 901
        logInfo "unknown model ${dataValueModel} manufacturer ${dataValueManufacturer}" // library marker kkossev.deviceProfileLib, line 902
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 903
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 904
    } // library marker kkossev.deviceProfileLib, line 905
    if (deviceName != null && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 906
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 907
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 908
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 909
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 910
    } else { // library marker kkossev.deviceProfileLib, line 911
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 912
    } // library marker kkossev.deviceProfileLib, line 913
} // library marker kkossev.deviceProfileLib, line 914

public List<String> refreshFromConfigureReadList(List<String> refreshList) { // library marker kkossev.deviceProfileLib, line 916
    logDebug "refreshFromConfigureReadList(${refreshList})" // library marker kkossev.deviceProfileLib, line 917
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 918
    if (refreshList != null && !refreshList.isEmpty()) { // library marker kkossev.deviceProfileLib, line 919
        //List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 920
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 921
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 922
            if (k != null) { // library marker kkossev.deviceProfileLib, line 923
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 924
                Map map = DEVICE.attributes?.find { it.name == k } // library marker kkossev.deviceProfileLib, line 925
                if (map != null) { // library marker kkossev.deviceProfileLib, line 926
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 927
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 928
                } // library marker kkossev.deviceProfileLib, line 929
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 930
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 931
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 932
                } // library marker kkossev.deviceProfileLib, line 933
            } // library marker kkossev.deviceProfileLib, line 934
        } // library marker kkossev.deviceProfileLib, line 935
    } // library marker kkossev.deviceProfileLib, line 936
    return cmds // library marker kkossev.deviceProfileLib, line 937
} // library marker kkossev.deviceProfileLib, line 938

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLib, line 940
public List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLib, line 941
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLib, line 942
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 943
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLib, line 944
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 945
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 946
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 947
            if (k != null) { // library marker kkossev.deviceProfileLib, line 948
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 949
                Map map = DEVICE.attributes?.find { it.name == k } // library marker kkossev.deviceProfileLib, line 950
                if (map != null) { // library marker kkossev.deviceProfileLib, line 951
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 952
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 953
                } // library marker kkossev.deviceProfileLib, line 954
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 955
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 956
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 957
                } // library marker kkossev.deviceProfileLib, line 958
            } // library marker kkossev.deviceProfileLib, line 959
        } // library marker kkossev.deviceProfileLib, line 960
    } // library marker kkossev.deviceProfileLib, line 961
    return cmds // library marker kkossev.deviceProfileLib, line 962
} // library marker kkossev.deviceProfileLib, line 963

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 965
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 966
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 967
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 968
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 969
    return cmds // library marker kkossev.deviceProfileLib, line 970
} // library marker kkossev.deviceProfileLib, line 971

// TODO ! - remove? // library marker kkossev.deviceProfileLib, line 973
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 974
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 975
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 976
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 977
    return cmds // library marker kkossev.deviceProfileLib, line 978
} // library marker kkossev.deviceProfileLib, line 979

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 981
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 982
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 983
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 984
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 985
    return cmds // library marker kkossev.deviceProfileLib, line 986
} // library marker kkossev.deviceProfileLib, line 987

// true when the stored profile is missing or was never resolved. Back-ported from deviceProfileLibV4. // library marker kkossev.deviceProfileLib, line 989
// A plain 'state.deviceProfile == null' test is not enough: a device that paired against a driver version // library marker kkossev.deviceProfileLib, line 990
// which did not yet contain its profile is stored as the string UNKNOWN, and UNKNOWN is not null - so it // library marker kkossev.deviceProfileLib, line 991
// never re-resolved on the checkDriverVersion() -> initializeVars(false) path taken after a code update. // library marker kkossev.deviceProfileLib, line 992
public boolean shouldDetectDeviceProfile() { // library marker kkossev.deviceProfileLib, line 993
    String currentProfile = state?.deviceProfile // library marker kkossev.deviceProfileLib, line 994
    return currentProfile == null || currentProfile == '' || currentProfile == UNKNOWN // library marker kkossev.deviceProfileLib, line 995
} // library marker kkossev.deviceProfileLib, line 996

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 998
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 999
    if (shouldDetectDeviceProfile()) { // library marker kkossev.deviceProfileLib, line 1000
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 1001
    } // library marker kkossev.deviceProfileLib, line 1002
} // library marker kkossev.deviceProfileLib, line 1003

public void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 1005
    String ps = DEVICE?.device?.powerSource // library marker kkossev.deviceProfileLib, line 1006
    logDebug "initEventsDeviceProfile(${fullInit}) for deviceProfile=${state.deviceProfile} DEVICE?.device?.powerSource=${ps} ps.isEmpty()=${ps?.isEmpty()}" // library marker kkossev.deviceProfileLib, line 1007
    if (ps != null && !ps.isEmpty()) { // library marker kkossev.deviceProfileLib, line 1008
        sendEvent(name: 'powerSource', value: ps, descriptionText: "Power Source set to '${ps}'", type: 'digital') // library marker kkossev.deviceProfileLib, line 1009
    } // library marker kkossev.deviceProfileLib, line 1010
} // library marker kkossev.deviceProfileLib, line 1011

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 1013

// // library marker kkossev.deviceProfileLib, line 1015
// called from parse() // library marker kkossev.deviceProfileLib, line 1016
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profile // library marker kkossev.deviceProfileLib, line 1017
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 1018
// // library marker kkossev.deviceProfileLib, line 1019
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 1020
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 1021
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 1022
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 1023
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 1024
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 1025
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 1026
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 1027
} // library marker kkossev.deviceProfileLib, line 1028

// // library marker kkossev.deviceProfileLib, line 1030
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 1031
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profile // library marker kkossev.deviceProfileLib, line 1032
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 1033
// // library marker kkossev.deviceProfileLib, line 1034
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 1035
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 1036
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 1037
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 1038
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 1039
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 1040
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 1041
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 1042
} // library marker kkossev.deviceProfileLib, line 1043

// all DPs are spammy - sent periodically! (this function is not used?) // library marker kkossev.deviceProfileLib, line 1045
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 1046
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 1047
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 1048
    return isSpammy // library marker kkossev.deviceProfileLib, line 1049
} // library marker kkossev.deviceProfileLib, line 1050

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1052
private List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 1053
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 1054
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 1055
    if (foundItem?.scale != null && foundItem?.scale != 0 && foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 1056
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1057
    } // library marker kkossev.deviceProfileLib, line 1058
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1059
} // library marker kkossev.deviceProfileLib, line 1060

private List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 1062
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 1063
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1064
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 1065
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1066
    } // library marker kkossev.deviceProfileLib, line 1067
    else { // library marker kkossev.deviceProfileLib, line 1068
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 1069
    } // library marker kkossev.deviceProfileLib, line 1070
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 1071
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1072
} // library marker kkossev.deviceProfileLib, line 1073

private List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 1075
    Double convertedValue // library marker kkossev.deviceProfileLib, line 1076
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1077
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 1078
    } // library marker kkossev.deviceProfileLib, line 1079
    else { // library marker kkossev.deviceProfileLib, line 1080
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1081
    } // library marker kkossev.deviceProfileLib, line 1082
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1083
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1084
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1085
} // library marker kkossev.deviceProfileLib, line 1086

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1088
private List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1089
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1090
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1091
    def convertedValue // library marker kkossev.deviceProfileLib, line 1092
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1093
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1094
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1095
    } // library marker kkossev.deviceProfileLib, line 1096
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1097
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1098
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1099
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1100
            isEqual = false // library marker kkossev.deviceProfileLib, line 1101
        } // library marker kkossev.deviceProfileLib, line 1102
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1103
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1104
        } // library marker kkossev.deviceProfileLib, line 1105
    } // library marker kkossev.deviceProfileLib, line 1106
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1107
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1108
} // library marker kkossev.deviceProfileLib, line 1109

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1111
private List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1112
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1113
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1114
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1115
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1116
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1117
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1118
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1119
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1120
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1121
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1122
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1123
            break // library marker kkossev.deviceProfileLib, line 1124
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1125
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1126
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1127
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1128
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1129
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1130
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1131
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1132
            } // library marker kkossev.deviceProfileLib, line 1133
            else { // library marker kkossev.deviceProfileLib, line 1134
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1135
            } // library marker kkossev.deviceProfileLib, line 1136
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1137
            break // library marker kkossev.deviceProfileLib, line 1138
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1139
        case 'number' : // library marker kkossev.deviceProfileLib, line 1140
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1141
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1142
            break // library marker kkossev.deviceProfileLib, line 1143
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1144
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1145
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1146
            break // library marker kkossev.deviceProfileLib, line 1147
        default : // library marker kkossev.deviceProfileLib, line 1148
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1149
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1150
    } // library marker kkossev.deviceProfileLib, line 1151
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1152
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1153
    } // library marker kkossev.deviceProfileLib, line 1154
    // // library marker kkossev.deviceProfileLib, line 1155
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1156
} // library marker kkossev.deviceProfileLib, line 1157

// // library marker kkossev.deviceProfileLib, line 1159
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1160
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1161
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1162
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1163
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1164
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1165
// // library marker kkossev.deviceProfileLib, line 1166
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1167
// // library marker kkossev.deviceProfileLib, line 1168
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1169
// // library marker kkossev.deviceProfileLib, line 1170
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1171
private List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1172
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1173
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1174
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1175
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1176
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1177
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1178
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1179
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1180
            break // library marker kkossev.deviceProfileLib, line 1181
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1182
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1183
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1184
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1185
            logTrace "latestEvent is ${latestEvent} dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1186
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1187
            if (dataType == null || dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1188
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1189
            } // library marker kkossev.deviceProfileLib, line 1190
            else { // library marker kkossev.deviceProfileLib, line 1191
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1192
            } // library marker kkossev.deviceProfileLib, line 1193
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1194
            break // library marker kkossev.deviceProfileLib, line 1195
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1196
        case 'number' : // library marker kkossev.deviceProfileLib, line 1197
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1198
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1199
            break // library marker kkossev.deviceProfileLib, line 1200
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1201
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1202
            break // library marker kkossev.deviceProfileLib, line 1203
        default : // library marker kkossev.deviceProfileLib, line 1204
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1205
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1206
    } // library marker kkossev.deviceProfileLib, line 1207
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1208
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1209
} // library marker kkossev.deviceProfileLib, line 1210

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1212
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1213
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1214
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1215
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1216
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1217
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1218
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1219
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1220
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1221
    } // library marker kkossev.deviceProfileLib, line 1222
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1223
    try { // library marker kkossev.deviceProfileLib, line 1224
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1225
    } // library marker kkossev.deviceProfileLib, line 1226
    catch (e) { // library marker kkossev.deviceProfileLib, line 1227
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1228
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1229
    } // library marker kkossev.deviceProfileLib, line 1230
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1231
    return fncmd // library marker kkossev.deviceProfileLib, line 1232
} // library marker kkossev.deviceProfileLib, line 1233

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1235
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1236
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1237
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1238
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1239
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1240
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1241

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1243
    if (attribMap == null || attribMap?.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1244

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1246
    int value // library marker kkossev.deviceProfileLib, line 1247
    try { // library marker kkossev.deviceProfileLib, line 1248
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1249
    } // library marker kkossev.deviceProfileLib, line 1250
    catch (e) { // library marker kkossev.deviceProfileLib, line 1251
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1252
        return false // library marker kkossev.deviceProfileLib, line 1253
    } // library marker kkossev.deviceProfileLib, line 1254
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1255
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1256
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1257
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1258
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1259
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1260
        return false // library marker kkossev.deviceProfileLib, line 1261
    } // library marker kkossev.deviceProfileLib, line 1262
    value = convertSignedInts(value, foundItem) // library marker kkossev.deviceProfileLib, line 1263
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1264
} // library marker kkossev.deviceProfileLib, line 1265

/** // library marker kkossev.deviceProfileLib, line 1267
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1268
 * // library marker kkossev.deviceProfileLib, line 1269
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1270
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1271
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1272
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1273
 * // library marker kkossev.deviceProfileLib, line 1274
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1275
 */ // library marker kkossev.deviceProfileLib, line 1276
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1277
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1278
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1279
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1280
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1281

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1283
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1284

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1286
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1287
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1288
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1289
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1290
        return false // library marker kkossev.deviceProfileLib, line 1291
    } // library marker kkossev.deviceProfileLib, line 1292
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1293
} // library marker kkossev.deviceProfileLib, line 1294

/* // library marker kkossev.deviceProfileLib, line 1296
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1297
 */ // library marker kkossev.deviceProfileLib, line 1298
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1299
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1300
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1301
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1302
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1303
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1304
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1305
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1306
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1307
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1308
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1309
        } // library marker kkossev.deviceProfileLib, line 1310
    } // library marker kkossev.deviceProfileLib, line 1311
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1312

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1314
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1315
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1316
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1317
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1318
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences?.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1319
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1320
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1321
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1322
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1323
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1324
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1325
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1326
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1327
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1328
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1329
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1330

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1332
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1333
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1334
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1335
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1336
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1337
            if (settings.logEnable) { logInfo "${descText} (Debug logging is enabled)" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1338
        } // library marker kkossev.deviceProfileLib, line 1339
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1340
    } // library marker kkossev.deviceProfileLib, line 1341

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1343
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1344
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1345
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1346
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1347
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1348
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1349
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1350
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1351
            } // library marker kkossev.deviceProfileLib, line 1352
        } // library marker kkossev.deviceProfileLib, line 1353
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1354
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1355
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1356
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1357
            } // library marker kkossev.deviceProfileLib, line 1358
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1359
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1360
            try { // library marker kkossev.deviceProfileLib, line 1361
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1362
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1363
            } // library marker kkossev.deviceProfileLib, line 1364
            catch (e) { // library marker kkossev.deviceProfileLib, line 1365
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1366
            } // library marker kkossev.deviceProfileLib, line 1367
        } // library marker kkossev.deviceProfileLib, line 1368
    } // library marker kkossev.deviceProfileLib, line 1369
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1370
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1371
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1372
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1373
    } // library marker kkossev.deviceProfileLib, line 1374

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1376
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1377
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1378
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1379
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1380
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1381
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1382
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1383
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1384
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1385
            } // library marker kkossev.deviceProfileLib, line 1386
            if (foundItem.processDuplicated == true) { // library marker kkossev.deviceProfileLib, line 1387
                logDebug 'processDuplicated=true -> continue' // library marker kkossev.deviceProfileLib, line 1388
            } // library marker kkossev.deviceProfileLib, line 1389

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1391
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch ! // library marker kkossev.deviceProfileLib, line 1392
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1393
            // continue ... // library marker kkossev.deviceProfileLib, line 1394
            } // library marker kkossev.deviceProfileLib, line 1395
            // B13: raw-DP dedupe above ignores illuminanceCoeff - let handleIlluminanceEvent() do its own correct-space delta filter // library marker kkossev.deviceProfileLib, line 1396
            else if (name == 'illuminance' || name == 'illuminance_lux') { // library marker kkossev.deviceProfileLib, line 1397
                logDebug "patch for ${name} (B13)" // library marker kkossev.deviceProfileLib, line 1398
            // continue ... // library marker kkossev.deviceProfileLib, line 1399
            } // library marker kkossev.deviceProfileLib, line 1400

            else { // library marker kkossev.deviceProfileLib, line 1402
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1403
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1404
                } // library marker kkossev.deviceProfileLib, line 1405
                else { // library marker kkossev.deviceProfileLib, line 1406
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLib, line 1407
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1408
                } // library marker kkossev.deviceProfileLib, line 1409
            } // library marker kkossev.deviceProfileLib, line 1410
        } // library marker kkossev.deviceProfileLib, line 1411

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1413
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1414
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1415
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1416
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1417
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1418
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1419
        } // library marker kkossev.deviceProfileLib, line 1420
        else { // library marker kkossev.deviceProfileLib, line 1421
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1422
            boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.deviceProfileLib, line 1423
            String eventType = isDigital ? 'digital' : 'physical' // library marker kkossev.deviceProfileLib, line 1424
            String eventDescText = "${descText}${isDigital ? ' [digital]' : ' [physical]'}" // library marker kkossev.deviceProfileLib, line 1425
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: eventDescText, type: eventType, isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1426
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1427
                logTrace "event ${name} sent w/ valueScaled ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1428
                logInfo "${eventDescText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1429
            } // library marker kkossev.deviceProfileLib, line 1430
        } // library marker kkossev.deviceProfileLib, line 1431
    } // library marker kkossev.deviceProfileLib, line 1432
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1433
} // library marker kkossev.deviceProfileLib, line 1434

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1436
public boolean validateAndFixPreferences(String debugStr) { return validateAndFixPreferences(debugStr.toBoolean() as boolean) } // library marker kkossev.deviceProfileLib, line 1437
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1438
    //debug = true // library marker kkossev.deviceProfileLib, line 1439
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1440
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLib, line 1441
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLib, line 1442
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1443
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLib, line 1444
    String settingType = '' // library marker kkossev.deviceProfileLib, line 1445
    // 'return' inside this closure skips to the next preference (it is a continue, not a method return) - that is intentional here: // library marker kkossev.deviceProfileLib, line 1446
    // one unusable preference must not stop the remaining ones from being validated. The method's own return value is not consumed anywhere. // library marker kkossev.deviceProfileLib, line 1447
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1448
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1449
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return } // library marker kkossev.deviceProfileLib, line 1450
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1451
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return } // library marker kkossev.deviceProfileLib, line 1452
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1453
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1454
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1455
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1456
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1457
            try { // library marker kkossev.deviceProfileLib, line 1458
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1459
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1460
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return // library marker kkossev.deviceProfileLib, line 1461
            } // library marker kkossev.deviceProfileLib, line 1462
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1463
            try { // library marker kkossev.deviceProfileLib, line 1464
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1465
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1466
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1467
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1468
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1469
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1470
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1471
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1472
                    } // library marker kkossev.deviceProfileLib, line 1473
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1474
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1475
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1476
                    } else { // library marker kkossev.deviceProfileLib, line 1477
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1478
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1479
                    } // library marker kkossev.deviceProfileLib, line 1480
                } // library marker kkossev.deviceProfileLib, line 1481
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1482
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1483
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1484
            } // library marker kkossev.deviceProfileLib, line 1485
            catch (e) { // library marker kkossev.deviceProfileLib, line 1486
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1487
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1488
                try { // library marker kkossev.deviceProfileLib, line 1489
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1490
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1491
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1492
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1493
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1494
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up on this preference!</b>" ; return // library marker kkossev.deviceProfileLib, line 1495
                } // library marker kkossev.deviceProfileLib, line 1496
            } // library marker kkossev.deviceProfileLib, line 1497
        } // library marker kkossev.deviceProfileLib, line 1498
        total ++ // library marker kkossev.deviceProfileLib, line 1499
    } // library marker kkossev.deviceProfileLib, line 1500
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1501
    return true // library marker kkossev.deviceProfileLib, line 1502
} // library marker kkossev.deviceProfileLib, line 1503

public String fingerprintIt(Map profileMap, Map fingerprint) { // library marker kkossev.deviceProfileLib, line 1505
    if (profileMap == null) { return 'profileMap is null' } // library marker kkossev.deviceProfileLib, line 1506
    if (fingerprint == null) { return 'fingerprint is null' } // library marker kkossev.deviceProfileLib, line 1507
    Map defaultFingerprint = profileMap.defaultFingerprint ?: [:] // library marker kkossev.deviceProfileLib, line 1508
    // if there is no defaultFingerprint, use the fingerprint as is // library marker kkossev.deviceProfileLib, line 1509
    if (defaultFingerprint == [:]) { // library marker kkossev.deviceProfileLib, line 1510
        return fingerprint.toString() // library marker kkossev.deviceProfileLib, line 1511
    } // library marker kkossev.deviceProfileLib, line 1512
    // for the missing keys, use the default values // library marker kkossev.deviceProfileLib, line 1513
    String fingerprintStr = '' // library marker kkossev.deviceProfileLib, line 1514
    defaultFingerprint.each { key, value -> // library marker kkossev.deviceProfileLib, line 1515
        String keyValue = fingerprint[key] ?: value // library marker kkossev.deviceProfileLib, line 1516
        fingerprintStr += "${key}:'${keyValue}', " // library marker kkossev.deviceProfileLib, line 1517
    } // library marker kkossev.deviceProfileLib, line 1518
    // remove the last comma and space // library marker kkossev.deviceProfileLib, line 1519
    fingerprintStr = fingerprintStr[0..-3] // library marker kkossev.deviceProfileLib, line 1520
    return fingerprintStr // library marker kkossev.deviceProfileLib, line 1521
} // library marker kkossev.deviceProfileLib, line 1522

public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1524
    int count = 0 // library marker kkossev.deviceProfileLib, line 1525
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1526
        logInfo "Device Profile: ${profileName}" // library marker kkossev.deviceProfileLib, line 1527
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1528
            log.info "${fingerprintIt(profileMap, fingerprint)}" // library marker kkossev.deviceProfileLib, line 1529
            count++ // library marker kkossev.deviceProfileLib, line 1530
        } // library marker kkossev.deviceProfileLib, line 1531
    } // library marker kkossev.deviceProfileLib, line 1532
    logInfo "Total fingerprints: ${count}" // library marker kkossev.deviceProfileLib, line 1533
} // library marker kkossev.deviceProfileLib, line 1534

public void printPreferences() { // library marker kkossev.deviceProfileLib, line 1536
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 1537
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 1538
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 1539
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLib, line 1540
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 1541
                log.info inputMap // library marker kkossev.deviceProfileLib, line 1542
            } // library marker kkossev.deviceProfileLib, line 1543
        } // library marker kkossev.deviceProfileLib, line 1544
    } // library marker kkossev.deviceProfileLib, line 1545
} // library marker kkossev.deviceProfileLib, line 1546

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~

// ~~~~~ start include (179) kkossev.thermostatLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.thermostatLib, line 1
library( // library marker kkossev.thermostatLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Thermostat Library', name: 'thermostatLib', namespace: 'kkossev', // library marker kkossev.thermostatLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/thermostatLib.groovy', documentationLink: '', // library marker kkossev.thermostatLib, line 4
    version: '3.6.4') // library marker kkossev.thermostatLib, line 5
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
 * ver. 3.6.1  2025-10-31 kkossev  - added setRefreshRequest() in the autoPollThermostat() method, so that events are always sent with [refresh] tag  // library marker kkossev.thermostatLib, line 25
 * ver. 3.6.2  2025-12-06 kkossev  - changed setheatingSetpoint to use [digital] / [physical]  type for the event logic 77 // library marker kkossev.thermostatLib, line 26
 * ver. 3.6.3  2026-02-08 kkossev  - setCoolingSetpoint() BOT-R15W_ZB_THERMOSTAT special handling; added sendCoolingSetpointEvent(); moved 'systemMode' 'off' check before 'eco' mode check in setThermostatMode(); info messages when unsupported modes are attempted to be set; // library marker kkossev.thermostatLib, line 27
 * ver. 3.6.4  2026-10-20 kkossev  - prevent errors on incomplete thermostat reports and mode retries before the first mode report. // library marker kkossev.thermostatLib, line 28
 * // library marker kkossev.thermostatLib, line 29
 *                                   TODO: add eco() method // library marker kkossev.thermostatLib, line 30
 *                                   TODO: refactor sendHeatingSetpointEvent // library marker kkossev.thermostatLib, line 31
*/ // library marker kkossev.thermostatLib, line 32

public static String thermostatLibVersion()   { '3.6.4' } // library marker kkossev.thermostatLib, line 34
public static String thermostatLibStamp() { '2026/10/20 1:31 PM' } // library marker kkossev.thermostatLib, line 35

metadata { // library marker kkossev.thermostatLib, line 37
    capability 'Actuator'           // also in onOffLib // library marker kkossev.thermostatLib, line 38
    capability 'Sensor' // library marker kkossev.thermostatLib, line 39
    capability 'Thermostat'                 // needed for HomeKit // library marker kkossev.thermostatLib, line 40
                // coolingSetpoint - NUMBER; heatingSetpoint - NUMBER; supportedThermostatFanModes - JSON_OBJECT; supportedThermostatModes - JSON_OBJECT; temperature - NUMBER, unit:°F || °C; thermostatFanMode - ENUM ["on", "circulate", "auto"] // library marker kkossev.thermostatLib, line 41
                // thermostatMode - ENUM ["auto", "off", "heat", "emergency heat", "cool"]; thermostatOperatingState - ENUM ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]; thermostatSetpoint - NUMBER, unit:°F || °C // library marker kkossev.thermostatLib, line 42
    capability 'ThermostatHeatingSetpoint' // library marker kkossev.thermostatLib, line 43
    capability 'ThermostatCoolingSetpoint' // library marker kkossev.thermostatLib, line 44
    capability 'ThermostatOperatingState'   // thermostatOperatingState - ENUM ["vent economizer", "pending cool", "cooling", "heating", "pending heat", "fan only", "idle"] // library marker kkossev.thermostatLib, line 45
    capability 'ThermostatSetpoint' // library marker kkossev.thermostatLib, line 46
    capability 'ThermostatMode' // library marker kkossev.thermostatLib, line 47
    capability 'ThermostatFanMode' // library marker kkossev.thermostatLib, line 48
    // no attributes // library marker kkossev.thermostatLib, line 49

    command 'setThermostatMode', [[name: 'thermostat mode (not all are available!)', type: 'ENUM', constraints: AllPossibleThermostatModesOpts.options.values() as List<String>]] // library marker kkossev.thermostatLib, line 51
    //    command 'setTemperature', ['NUMBER']                        // Virtual thermostat  TODO - decide if it is needed // library marker kkossev.thermostatLib, line 52

    preferences { // library marker kkossev.thermostatLib, line 54
        if (device) { // TODO -  move it to the deviceProfile preferences // library marker kkossev.thermostatLib, line 55
            input name: 'temperaturePollingInterval', type: 'enum', title: '<b>Temperature polling interval</b>', options: TrvTemperaturePollingIntervalOpts.options, defaultValue: TrvTemperaturePollingIntervalOpts.defaultValue, required: true, description: 'Changes how often the hub will poll the TRV for faster temperature reading updates and nice looking graphs.' // library marker kkossev.thermostatLib, line 56
        } // library marker kkossev.thermostatLib, line 57
    } // library marker kkossev.thermostatLib, line 58
} // library marker kkossev.thermostatLib, line 59

@Field static final Map TrvTemperaturePollingIntervalOpts = [ // library marker kkossev.thermostatLib, line 61
    defaultValue: 600, // library marker kkossev.thermostatLib, line 62
    options     : [0: 'Disabled', 60: 'Every minute (not recommended)', 120: 'Every 2 minutes', 300: 'Every 5 minutes', 600: 'Every 10 minutes', 900: 'Every 15 minutes', 1800: 'Every 30 minutes', 3600: 'Every 1 hour'] // library marker kkossev.thermostatLib, line 63
] // library marker kkossev.thermostatLib, line 64

@Field static final Map AllPossibleThermostatModesOpts = [ // library marker kkossev.thermostatLib, line 66
    defaultValue: 1, // library marker kkossev.thermostatLib, line 67
    options     : [0: 'off', 1: 'heat', 2: 'cool', 3: 'auto', 4: 'emergency heat', 5: 'eco'] // library marker kkossev.thermostatLib, line 68
] // library marker kkossev.thermostatLib, line 69

public void heat() { setThermostatMode('heat') } // library marker kkossev.thermostatLib, line 71
public void auto() { setThermostatMode('auto') } // library marker kkossev.thermostatLib, line 72
public void cool() { setThermostatMode('cool') } // library marker kkossev.thermostatLib, line 73
public void emergencyHeat() { setThermostatMode('emergency heat') } // library marker kkossev.thermostatLib, line 74
public void eco() { setThermostatMode('eco') } // library marker kkossev.thermostatLib, line 75

public void setThermostatFanMode(final String fanMode) { sendEvent(name: 'thermostatFanMode', value: "${fanMode}", descriptionText: getDescriptionText("thermostatFanMode is ${fanMode}")) } // library marker kkossev.thermostatLib, line 77
public void fanAuto() { setThermostatFanMode('auto') } // library marker kkossev.thermostatLib, line 78
public void fanCirculate() { setThermostatFanMode('circulate') } // library marker kkossev.thermostatLib, line 79
public void fanOn() { setThermostatFanMode('on') } // library marker kkossev.thermostatLib, line 80

public void customOff() { setThermostatMode('off') }    // invoked from the common library // library marker kkossev.thermostatLib, line 82
public void customOn()  { setThermostatMode('heat') }   // invoked from the common library // library marker kkossev.thermostatLib, line 83

/* // library marker kkossev.thermostatLib, line 85
 * ----------------------------------------------------------------------------- // library marker kkossev.thermostatLib, line 86
 * thermostat cluster 0x0201 // library marker kkossev.thermostatLib, line 87
 * ----------------------------------------------------------------------------- // library marker kkossev.thermostatLib, line 88
*/ // library marker kkossev.thermostatLib, line 89
// * should be implemented in the custom driver code ... // library marker kkossev.thermostatLib, line 90
public void standardParseThermostatCluster(final Map descMap) { // library marker kkossev.thermostatLib, line 91
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return } // library marker kkossev.thermostatLib, line 92
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value)) // library marker kkossev.thermostatLib, line 93
    logTrace "standardParseThermostatCluster: zigbee received Thermostat cluster (0x0201) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})" // library marker kkossev.thermostatLib, line 94
    if (deviceProfilesV3 != null || g_deviceProfilesV4 != null) { // library marker kkossev.thermostatLib, line 95
        boolean result = processClusterAttributeFromDeviceProfile(descMap) // library marker kkossev.thermostatLib, line 96
        if ( result == false ) { // library marker kkossev.thermostatLib, line 97
            logWarn "standardParseThermostatCluster: received unknown Thermostat cluster (0x0201) attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.thermostatLib, line 98
        } // library marker kkossev.thermostatLib, line 99
    } // library marker kkossev.thermostatLib, line 100
    // try to process the attribute value // library marker kkossev.thermostatLib, line 101
    standardHandleThermostatEvent(value) // library marker kkossev.thermostatLib, line 102
} // library marker kkossev.thermostatLib, line 103

//  setHeatingSetpoint thermostat capability standard command // library marker kkossev.thermostatLib, line 105
//  1°C steps. (0.5°C setting on the TRV itself, rounded for zigbee interface) // library marker kkossev.thermostatLib, line 106
// // library marker kkossev.thermostatLib, line 107
void setHeatingSetpoint(final Number temperaturePar ) { // library marker kkossev.thermostatLib, line 108
    BigDecimal temperature = temperaturePar.toBigDecimal() // library marker kkossev.thermostatLib, line 109
    logTrace "setHeatingSetpoint(${temperature}) called!" // library marker kkossev.thermostatLib, line 110
    BigDecimal previousSetpoint = (device.currentState('heatingSetpoint')?.value ?: 0.0G).toBigDecimal() // library marker kkossev.thermostatLib, line 111
    BigDecimal tempDouble = temperature // library marker kkossev.thermostatLib, line 112
    //logDebug "setHeatingSetpoint temperature = ${temperature}  as int = ${temperature as int} (previousSetpointt = ${previousSetpoint})" // library marker kkossev.thermostatLib, line 113
    /* groovylint-disable-next-line ConstantIfExpression */ // library marker kkossev.thermostatLib, line 114
    if (true) { // library marker kkossev.thermostatLib, line 115
        //logDebug "0.5 C correction of the heating setpoint${temperature}" // library marker kkossev.thermostatLib, line 116
        //log.trace "tempDouble = ${tempDouble}" // library marker kkossev.thermostatLib, line 117
        tempDouble = (tempDouble * 2).setScale(0, RoundingMode.HALF_UP) / 2 // library marker kkossev.thermostatLib, line 118
    } // library marker kkossev.thermostatLib, line 119
    else { // library marker kkossev.thermostatLib, line 120
        if (temperature != (temperature as int)) { // library marker kkossev.thermostatLib, line 121
            if ((temperature as double) > (previousSetpoint as double)) { // library marker kkossev.thermostatLib, line 122
                temperature = (temperature + 0.5 ) as int // library marker kkossev.thermostatLib, line 123
            } // library marker kkossev.thermostatLib, line 124
            else { // library marker kkossev.thermostatLib, line 125
                temperature = temperature as int // library marker kkossev.thermostatLib, line 126
            } // library marker kkossev.thermostatLib, line 127
            logDebug "corrected heating setpoint ${temperature}" // library marker kkossev.thermostatLib, line 128
        } // library marker kkossev.thermostatLib, line 129
        tempDouble = temperature // library marker kkossev.thermostatLib, line 130
    } // library marker kkossev.thermostatLib, line 131
    BigDecimal maxTemp = settings?.maxHeatingSetpoint ? new BigDecimal(settings.maxHeatingSetpoint) : new BigDecimal(50) // library marker kkossev.thermostatLib, line 132
    BigDecimal minTemp = settings?.minHeatingSetpoint ? new BigDecimal(settings.minHeatingSetpoint) : new BigDecimal(5) // library marker kkossev.thermostatLib, line 133
    tempBigDecimal = new BigDecimal(tempDouble) // library marker kkossev.thermostatLib, line 134
    tempBigDecimal = tempDouble.min(maxTemp).max(minTemp).setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.thermostatLib, line 135

    logDebug "setHeatingSetpoint: calling sendAttribute heatingSetpoint ${tempBigDecimal}" // library marker kkossev.thermostatLib, line 137
    sendAttribute('heatingSetpoint', tempBigDecimal as double) // library marker kkossev.thermostatLib, line 138

    // Mark this as a digital command // library marker kkossev.thermostatLib, line 140
    state.states['isDigital'] = true // library marker kkossev.thermostatLib, line 141
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.thermostatLib, line 142

    // added 02/16/2025 // library marker kkossev.thermostatLib, line 144
    state.lastTx.isSetPointReq = true // library marker kkossev.thermostatLib, line 145
    state.lastTx.setPoint = tempBigDecimal    // BEOK - float value! // library marker kkossev.thermostatLib, line 146
    runIn(3, setpointReceiveCheck) // library marker kkossev.thermostatLib, line 147

} // library marker kkossev.thermostatLib, line 149

// TODO - use sendThermostatEvent instead! // library marker kkossev.thermostatLib, line 151
void sendHeatingSetpointEvent(Number temperature) { // library marker kkossev.thermostatLib, line 152
    tempDouble = safeToDouble(temperature) // library marker kkossev.thermostatLib, line 153
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.thermostatLib, line 154
    Map eventMap = [name: 'heatingSetpoint',  value: tempDouble, unit: '\u00B0C', type: isDigital ? 'digital' : 'physical'] // library marker kkossev.thermostatLib, line 155
    eventMap.descriptionText = "heatingSetpoint is ${tempDouble}" // library marker kkossev.thermostatLib, line 156
    if (state.states['isRefresh'] == true) { // library marker kkossev.thermostatLib, line 157
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.thermostatLib, line 158
        eventMap.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 159
    } // library marker kkossev.thermostatLib, line 160
    if (isDigital) { // library marker kkossev.thermostatLib, line 161
        eventMap.descriptionText += ' [digital]' // library marker kkossev.thermostatLib, line 162
        eventMap.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 163
    } // library marker kkossev.thermostatLib, line 164
    else { // library marker kkossev.thermostatLib, line 165
        eventMap.descriptionText += ' [physical]' // library marker kkossev.thermostatLib, line 166
    } // library marker kkossev.thermostatLib, line 167
    sendEvent(eventMap) // library marker kkossev.thermostatLib, line 168
    if (eventMap.descriptionText != null) { logInfo "${eventMap.descriptionText}" } // library marker kkossev.thermostatLib, line 169

    eventMap.name = 'thermostatSetpoint' // library marker kkossev.thermostatLib, line 171
    logDebug "sending event ${eventMap}" // library marker kkossev.thermostatLib, line 172
    sendEvent(eventMap) // library marker kkossev.thermostatLib, line 173
    updateDataValue('lastRunningMode', 'heat') // library marker kkossev.thermostatLib, line 174
    // added 02/16/2025 // library marker kkossev.thermostatLib, line 175
    state.lastRx.setPoint = tempDouble // library marker kkossev.thermostatLib, line 176
} // library marker kkossev.thermostatLib, line 177

void sendCoolingSetpointEvent(Number temperature) { // library marker kkossev.thermostatLib, line 179
    tempDouble = safeToDouble(temperature) // library marker kkossev.thermostatLib, line 180
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.thermostatLib, line 181
    Map eventMap = [name: 'coolingSetpoint',  value: tempDouble, unit: '\u00B0C', type: isDigital ? 'digital' : 'physical'] // library marker kkossev.thermostatLib, line 182
    eventMap.descriptionText = "coolingSetpoint is ${tempDouble}" // library marker kkossev.thermostatLib, line 183
    if (state.states['isRefresh'] == true) { // library marker kkossev.thermostatLib, line 184
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.thermostatLib, line 185
        eventMap.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 186
    } // library marker kkossev.thermostatLib, line 187
    if (isDigital) { // library marker kkossev.thermostatLib, line 188
        eventMap.descriptionText += ' [digital]' // library marker kkossev.thermostatLib, line 189
        eventMap.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 190
    } // library marker kkossev.thermostatLib, line 191
    else { // library marker kkossev.thermostatLib, line 192
        eventMap.descriptionText += ' [physical]' // library marker kkossev.thermostatLib, line 193
    } // library marker kkossev.thermostatLib, line 194
    sendEvent(eventMap) // library marker kkossev.thermostatLib, line 195
    if (eventMap.descriptionText != null) { logInfo "${eventMap.descriptionText}" } // library marker kkossev.thermostatLib, line 196

    updateDataValue('lastRunningMode', 'cool') // library marker kkossev.thermostatLib, line 198
} // library marker kkossev.thermostatLib, line 199

// thermostat capability standard command // library marker kkossev.thermostatLib, line 201
// Default behavior: do nothing in TRV - just send an event. // library marker kkossev.thermostatLib, line 202
// Exception: BOT_R15W_ZB_THERMOSTAT uses a shared setpoint (dp2) for both heating and cooling, // library marker kkossev.thermostatLib, line 203
// so setting coolingSetpoint must send a real command (write heatingSetpoint). // library marker kkossev.thermostatLib, line 204
void setCoolingSetpoint(Number temperaturePar) { // library marker kkossev.thermostatLib, line 205
    logDebug "setCoolingSetpoint(${temperaturePar}) called!" // library marker kkossev.thermostatLib, line 206

    // BOT-R15W special handling (profile-driven via deviceProfileLib) // library marker kkossev.thermostatLib, line 208
    if (this.respondsTo('getDeviceProfile') && (getDeviceProfile() == 'BOT_R15W_ZB_THERMOSTAT')) { // library marker kkossev.thermostatLib, line 209
        logDebug "setCoolingSetpoint(${temperaturePar}) [BOT-R15W]" // library marker kkossev.thermostatLib, line 210
        sendCoolingSetpointEvent(temperaturePar) // library marker kkossev.thermostatLib, line 211
        return // library marker kkossev.thermostatLib, line 212
    } // library marker kkossev.thermostatLib, line 213

    BigDecimal temperature = Math.round(temperaturePar * 2) / 2 // library marker kkossev.thermostatLib, line 215
    String descText = "coolingSetpoint is set to ${temperature} \u00B0C" // library marker kkossev.thermostatLib, line 216
    sendEvent(name: 'coolingSetpoint', value: temperature, unit: '\u00B0C', descriptionText: descText, type: 'digital') // library marker kkossev.thermostatLib, line 217
    logInfo "${descText}" // library marker kkossev.thermostatLib, line 218
} // library marker kkossev.thermostatLib, line 219

public void sendThermostatEvent(Map eventMap, final boolean isDigital = false) { // library marker kkossev.thermostatLib, line 221
    if (eventMap.descriptionText == null) { eventMap.descriptionText = "${eventName} is ${value}" } // library marker kkossev.thermostatLib, line 222
    if (eventMap.type == null) { eventMap.type = isDigital == true ? 'digital' : 'physical' } // library marker kkossev.thermostatLib, line 223
    if (state.states['isRefresh'] == true) { // library marker kkossev.thermostatLib, line 224
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.thermostatLib, line 225
        eventMap.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 226
    } // library marker kkossev.thermostatLib, line 227
    sendEvent(eventMap) // library marker kkossev.thermostatLib, line 228
    logInfo "${eventMap.descriptionText}" // library marker kkossev.thermostatLib, line 229
} // library marker kkossev.thermostatLib, line 230

private void sendEventMap(final Map event, final boolean isDigital = false) { // library marker kkossev.thermostatLib, line 232
    if (event.descriptionText == null) { // library marker kkossev.thermostatLib, line 233
        event.descriptionText = "${event.name} is ${event.value} ${event.unit ?: ''}" // library marker kkossev.thermostatLib, line 234
    } // library marker kkossev.thermostatLib, line 235
    if (state.states['isRefresh'] == true) { // library marker kkossev.thermostatLib, line 236
        event.descriptionText += ' [refresh]' // library marker kkossev.thermostatLib, line 237
        event.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 238
    } // library marker kkossev.thermostatLib, line 239
    event.type = event.type != null ? event.type : isDigital == true ? 'digital' : 'physical' // library marker kkossev.thermostatLib, line 240
    if (event.type == 'digital') { // library marker kkossev.thermostatLib, line 241
        event.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 242
        event.descriptionText += ' [digital]' // library marker kkossev.thermostatLib, line 243
    } // library marker kkossev.thermostatLib, line 244
    sendEvent(event) // library marker kkossev.thermostatLib, line 245
    logInfo "${event.descriptionText}" // library marker kkossev.thermostatLib, line 246
} // library marker kkossev.thermostatLib, line 247

private String getDescriptionText(final String msg) { // library marker kkossev.thermostatLib, line 249
    String descriptionText = "${device.displayName} ${msg}" // library marker kkossev.thermostatLib, line 250
    if (settings?.txtEnable) { log.info "${descriptionText}" } // library marker kkossev.thermostatLib, line 251
    return descriptionText // library marker kkossev.thermostatLib, line 252
} // library marker kkossev.thermostatLib, line 253

/** // library marker kkossev.thermostatLib, line 255
 * Sets the thermostat mode based on the requested mode. // library marker kkossev.thermostatLib, line 256
 * // library marker kkossev.thermostatLib, line 257
 * if the requestedMode is supported directly in the thermostatMode attribute, it is set directly. // library marker kkossev.thermostatLib, line 258
 * Otherwise, the thermostatMode is substituted with another command, if supported by the device. // library marker kkossev.thermostatLib, line 259
 * // library marker kkossev.thermostatLib, line 260
 * @param requestedMode The mode to set the thermostat to. // library marker kkossev.thermostatLib, line 261
 */ // library marker kkossev.thermostatLib, line 262
public void setThermostatMode(final String requestedMode) { // library marker kkossev.thermostatLib, line 263
    String mode = requestedMode // library marker kkossev.thermostatLib, line 264
    boolean result = false // library marker kkossev.thermostatLib, line 265
    List nativelySupportedModesList = getAttributesMap('thermostatMode')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 266
    List systemModesList = getAttributesMap('systemMode')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 267
    List ecoModesList = getAttributesMap('ecoMode')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 268
    List emergencyHeatingModesList = getAttributesMap('emergencyHeating')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 269
    List operationModesList = getAttributesMap('operationMode')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 270

    logDebug "setThermostatMode: sending setThermostatMode(${mode}). Natively supported: ${nativelySupportedModesList}" // library marker kkossev.thermostatLib, line 272

    // Mark this as a digital command at the very beginning // library marker kkossev.thermostatLib, line 274
    state.states['isDigital'] = true // library marker kkossev.thermostatLib, line 275
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.thermostatLib, line 276

    // some TRVs require some checks and additional commands to be sent before setting the mode // library marker kkossev.thermostatLib, line 278
    final String currentMode = device.currentValue('thermostatMode') // library marker kkossev.thermostatLib, line 279
    logDebug "setThermostatMode: currentMode = ${currentMode}, switching to ${mode} ..." // library marker kkossev.thermostatLib, line 280

    // added 02/16/2025 // library marker kkossev.thermostatLib, line 282
    setLastTx( mode = requestedMode, isModeSetReq = true) // library marker kkossev.thermostatLib, line 283
    runIn(4, modeReceiveCheck) // library marker kkossev.thermostatLib, line 284

    switch (mode) { // library marker kkossev.thermostatLib, line 286
        case 'heat': // library marker kkossev.thermostatLib, line 287
            if ('heating' in operationModesList) { // library marker kkossev.thermostatLib, line 288
                logDebug 'setThermostatMode: pre-processing: switching the operationMode to heating' // library marker kkossev.thermostatLib, line 289
                sendAttribute('operationMode', 'heating') // library marker kkossev.thermostatLib, line 290
            } // library marker kkossev.thermostatLib, line 291
            // continue  // library marker kkossev.thermostatLib, line 292
        case 'auto': // library marker kkossev.thermostatLib, line 293
            if (device.currentValue('ecoMode') == 'on') { // library marker kkossev.thermostatLib, line 294
                logDebug 'setThermostatMode: pre-processing: switching first the eco mode off' // library marker kkossev.thermostatLib, line 295
                sendAttribute('ecoMode', 0) // library marker kkossev.thermostatLib, line 296
            } // library marker kkossev.thermostatLib, line 297
            if (device.currentValue('emergencyHeating') == 'on') { // library marker kkossev.thermostatLib, line 298
                logDebug 'setThermostatMode: pre-processing: switching first the emergencyHeating mode off' // library marker kkossev.thermostatLib, line 299
                sendAttribute('emergencyHeating', 0) // library marker kkossev.thermostatLib, line 300
            } // library marker kkossev.thermostatLib, line 301
            if ((device.currentValue('systemMode') ?: 'off') == 'off') { // library marker kkossev.thermostatLib, line 302
                logDebug 'setThermostatMode: pre-processing: switching first the systemMode on' // library marker kkossev.thermostatLib, line 303
                sendAttribute('systemMode', 'on') // library marker kkossev.thermostatLib, line 304
            } // library marker kkossev.thermostatLib, line 305
            break // library marker kkossev.thermostatLib, line 306
        case 'cool':        // disabled the cool mode 03/04/2025 // library marker kkossev.thermostatLib, line 307
            if ('cooling' in operationModesList) { // library marker kkossev.thermostatLib, line 308
                logDebug 'setThermostatMode: pre-processing: switching the operationMode to cooling' // library marker kkossev.thermostatLib, line 309
                sendAttribute('operationMode', 'cooling') // library marker kkossev.thermostatLib, line 310
                return // library marker kkossev.thermostatLib, line 311
            } // library marker kkossev.thermostatLib, line 312
            if (!('cool' in DEVICE.supportedThermostatModes)) { // library marker kkossev.thermostatLib, line 313
                    logInfo "'cool' mode is not supported by this device" // library marker kkossev.thermostatLib, line 314
                    return // library marker kkossev.thermostatLib, line 315
            } // library marker kkossev.thermostatLib, line 316
            break // library marker kkossev.thermostatLib, line 317
        case 'emergency heat':     // TODO for Aqara and Sonoff TRVs // library marker kkossev.thermostatLib, line 318
            if ('emergency heat' in nativelySupportedModesList) { // library marker kkossev.thermostatLib, line 319
                break // library marker kkossev.thermostatLib, line 320
            } // library marker kkossev.thermostatLib, line 321
            // look for a dedicated 'emergencyMode' deviceProfile attribute       (BRT-100) // library marker kkossev.thermostatLib, line 322
            if ('on' in emergencyHeatingModesList)  { // library marker kkossev.thermostatLib, line 323
                logInfo "setThermostatMode: pre-processing: switching the emergencyMode mode on for (${settings.emergencyHeatingTime} seconds )" // library marker kkossev.thermostatLib, line 324
                sendAttribute('emergencyHeating', 'on') // library marker kkossev.thermostatLib, line 325
                return // library marker kkossev.thermostatLib, line 326
            } // library marker kkossev.thermostatLib, line 327
            else { // library marker kkossev.thermostatLib, line 328
                logInfo "'emergency heat' mode is not supported by this device" // library marker kkossev.thermostatLib, line 329
                return // library marker kkossev.thermostatLib, line 330
            } // library marker kkossev.thermostatLib, line 331
            break // library marker kkossev.thermostatLib, line 332
        case 'eco': // library marker kkossev.thermostatLib, line 333
            if ('eco' in nativelySupportedModesList) {   // library marker kkossev.thermostatLib, line 334
                logDebug 'setThermostatMode: pre-processing: switching to natively supported eco mode' // library marker kkossev.thermostatLib, line 335
                break // library marker kkossev.thermostatLib, line 336
            } // library marker kkossev.thermostatLib, line 337
            if (device.hasAttribute('ecoMode')) {   // changed 06/16/2024 : was : (device.currentValue('ecoMode') != null)  { // library marker kkossev.thermostatLib, line 338
                logDebug 'setThermostatMode: pre-processing: switching the eco mode on' // library marker kkossev.thermostatLib, line 339
                sendAttribute('ecoMode', 1) // library marker kkossev.thermostatLib, line 340
                return // library marker kkossev.thermostatLib, line 341
            } // library marker kkossev.thermostatLib, line 342
            else { // library marker kkossev.thermostatLib, line 343
                logInfo "'eco' mode is not supported by this device" // library marker kkossev.thermostatLib, line 344
                return // library marker kkossev.thermostatLib, line 345
            } // library marker kkossev.thermostatLib, line 346
            break // library marker kkossev.thermostatLib, line 347
        case 'off':     // OK! // library marker kkossev.thermostatLib, line 348
            if ('off' in nativelySupportedModesList) { // library marker kkossev.thermostatLib, line 349
                break // library marker kkossev.thermostatLib, line 350
            } // library marker kkossev.thermostatLib, line 351
            logDebug "setThermostatMode: pre-processing: switching to 'off' mode" // library marker kkossev.thermostatLib, line 352
            // look for a dedicated 'systemMode' attribute with map 'off' (Aqara E1, MOES Star Ring ...)  - 2026-02-04 :  moved this check before the 'eco' mode checks // library marker kkossev.thermostatLib, line 353
            if ('off' in systemModesList)  { // library marker kkossev.thermostatLib, line 354
                logDebug 'setThermostatMode: pre-processing: switching the systemMode off' // library marker kkossev.thermostatLib, line 355
                sendAttribute('systemMode', 'off') // library marker kkossev.thermostatLib, line 356
                return // library marker kkossev.thermostatLib, line 357
            } // library marker kkossev.thermostatLib, line 358
            // if the 'off' mode is not directly supported, try substituting it with 'eco' mode // library marker kkossev.thermostatLib, line 359
            if ('eco' in nativelySupportedModesList) { // library marker kkossev.thermostatLib, line 360
                logDebug 'setThermostatMode: pre-processing: switching to eco mode instead' // library marker kkossev.thermostatLib, line 361
                mode = 'eco' // library marker kkossev.thermostatLib, line 362
                break // library marker kkossev.thermostatLib, line 363
            } // library marker kkossev.thermostatLib, line 364
            // look for a dedicated 'ecoMode' deviceProfile attribute       (BRT-100) // library marker kkossev.thermostatLib, line 365
            if ('on' in ecoModesList)  { // library marker kkossev.thermostatLib, line 366
                logDebug 'setThermostatMode: pre-processing: switching the eco mode on' // library marker kkossev.thermostatLib, line 367
                sendAttribute('ecoMode', 'on') // library marker kkossev.thermostatLib, line 368
                return // library marker kkossev.thermostatLib, line 369
            } // library marker kkossev.thermostatLib, line 370
            break // library marker kkossev.thermostatLib, line 371
        default: // library marker kkossev.thermostatLib, line 372
            logWarn "setThermostatMode: pre-processing: unknown mode ${mode}" // library marker kkossev.thermostatLib, line 373
            break // library marker kkossev.thermostatLib, line 374
    } // library marker kkossev.thermostatLib, line 375

    // try using the standard thermostat capability to switch to the selected new mode // library marker kkossev.thermostatLib, line 377
    result = sendAttribute('thermostatMode', mode) // library marker kkossev.thermostatLib, line 378
    logTrace "setThermostatMode: sendAttribute returned ${result}" // library marker kkossev.thermostatLib, line 379
    if (result == true) { return } // library marker kkossev.thermostatLib, line 380

    // post-process mode switching for some TRVs // library marker kkossev.thermostatLib, line 382
    switch (mode) { // library marker kkossev.thermostatLib, line 383
        case 'cool' : // library marker kkossev.thermostatLib, line 384
        case 'heat' : // library marker kkossev.thermostatLib, line 385
        case 'auto' : // library marker kkossev.thermostatLib, line 386
        case 'off' : // library marker kkossev.thermostatLib, line 387
        case 'eco' : // library marker kkossev.thermostatLib, line 388
            logTrace "setThermostatMode: post-processing: no post-processing required for mode ${mode}" // library marker kkossev.thermostatLib, line 389
            break // library marker kkossev.thermostatLib, line 390
        case 'emergency heat' : // library marker kkossev.thermostatLib, line 391
            logInfo "setting emergency heat mode on (${settings.emergencyHeatingTime} minutes)" // library marker kkossev.thermostatLib, line 392
            sendAttribute('emergencyHeating', 1) // library marker kkossev.thermostatLib, line 393
            break // library marker kkossev.thermostatLib, line 394
        default : // library marker kkossev.thermostatLib, line 395
            logWarn "setThermostatMode: post-processing: unsupported thermostat mode '${mode}'" // library marker kkossev.thermostatLib, line 396
            break // library marker kkossev.thermostatLib, line 397
    } // library marker kkossev.thermostatLib, line 398
    return // library marker kkossev.thermostatLib, line 399
} // library marker kkossev.thermostatLib, line 400

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.thermostatLib, line 402
void sendSupportedThermostatModes(boolean debug = false) { // library marker kkossev.thermostatLib, line 403
    List<String> supportedThermostatModes = [] // library marker kkossev.thermostatLib, line 404
    supportedThermostatModes = ['off', 'heat', 'auto', 'emergency heat'] // library marker kkossev.thermostatLib, line 405
    if (DEVICE.supportedThermostatModes != null) { // library marker kkossev.thermostatLib, line 406
        supportedThermostatModes = DEVICE.supportedThermostatModes // library marker kkossev.thermostatLib, line 407
    } // library marker kkossev.thermostatLib, line 408
    else { // library marker kkossev.thermostatLib, line 409
        logWarn 'sendSupportedThermostatModes: DEVICE.supportedThermostatModes is not set!' // library marker kkossev.thermostatLib, line 410
        supportedThermostatModes =  ['off', 'auto', 'heat'] // library marker kkossev.thermostatLib, line 411
    } // library marker kkossev.thermostatLib, line 412
    logInfo "supportedThermostatModes: ${supportedThermostatModes}" // library marker kkossev.thermostatLib, line 413
    sendEvent(name: 'supportedThermostatModes', value:  JsonOutput.toJson(supportedThermostatModes), isStateChange: true, type: 'digital') // library marker kkossev.thermostatLib, line 414
    if (DEVICE.supportedThermostatFanModes != null) { // library marker kkossev.thermostatLib, line 415
        sendEvent(name: 'supportedThermostatFanModes', value: JsonOutput.toJson(DEVICE.supportedThermostatFanModes), isStateChange: true, type: 'digital') // library marker kkossev.thermostatLib, line 416
    } // library marker kkossev.thermostatLib, line 417
    else { // library marker kkossev.thermostatLib, line 418
        sendEvent(name: 'supportedThermostatFanModes', value: JsonOutput.toJson(['auto', 'circulate', 'on']), isStateChange: true, type: 'digital') // library marker kkossev.thermostatLib, line 419
    } // library marker kkossev.thermostatLib, line 420
} // library marker kkossev.thermostatLib, line 421

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.thermostatLib, line 423
void standardHandleThermostatEvent(int value, boolean isDigital=false) { // library marker kkossev.thermostatLib, line 424
    logWarn 'standardHandleThermostatEvent()... NOT IMPLEMENTED!' // library marker kkossev.thermostatLib, line 425
} // library marker kkossev.thermostatLib, line 426

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.thermostatLib, line 428
private void sendDelayedThermostatEvent(Map eventMap) { // library marker kkossev.thermostatLib, line 429
    logWarn "${device.displayName} NOT IMPLEMENTED! <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.thermostatLib, line 430
} // library marker kkossev.thermostatLib, line 431

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.thermostatLib, line 433
void thermostatProcessTuyaDP(final Map descMap, int dp, int dp_id, int fncmd) { // library marker kkossev.thermostatLib, line 434
    logWarn "thermostatProcessTuyaDP()... NOT IMPLEMENTED! dp=${dp} dp_id=${dp_id} fncmd=${fncmd}" // library marker kkossev.thermostatLib, line 435
} // library marker kkossev.thermostatLib, line 436

/** // library marker kkossev.thermostatLib, line 438
 * Schedule thermostat polling // library marker kkossev.thermostatLib, line 439
 * @param intervalMins interval in seconds // library marker kkossev.thermostatLib, line 440
 */ // library marker kkossev.thermostatLib, line 441
public void scheduleThermostatPolling(final int intervalSecs) { // library marker kkossev.thermostatLib, line 442
    String cron = getCron( intervalSecs ) // library marker kkossev.thermostatLib, line 443
    logDebug "cron = ${cron}" // library marker kkossev.thermostatLib, line 444
    schedule(cron, 'autoPollThermostat') // library marker kkossev.thermostatLib, line 445
} // library marker kkossev.thermostatLib, line 446

public void unScheduleThermostatPolling() { // library marker kkossev.thermostatLib, line 448
    unschedule('autoPollThermostat') // library marker kkossev.thermostatLib, line 449
} // library marker kkossev.thermostatLib, line 450

/** // library marker kkossev.thermostatLib, line 452
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.thermostatLib, line 453
 */ // library marker kkossev.thermostatLib, line 454
public void autoPollThermostat() { // library marker kkossev.thermostatLib, line 455
    logDebug 'autoPollThermostat()...' // library marker kkossev.thermostatLib, line 456
    checkDriverVersion(state) // library marker kkossev.thermostatLib, line 457
    List<String> cmds = refreshFromDeviceProfileList() // library marker kkossev.thermostatLib, line 458
    if (cmds != null && cmds != [] ) { // library marker kkossev.thermostatLib, line 459
        setRefreshRequest() // set the refresh flag and start a REFRESH_TIMER timer to clear it // library marker kkossev.thermostatLib, line 460
        sendZigbeeCommands(cmds) // library marker kkossev.thermostatLib, line 461
    } // library marker kkossev.thermostatLib, line 462
} // library marker kkossev.thermostatLib, line 463

private int getElapsedTimeFromEventInSeconds(final String eventName) { // library marker kkossev.thermostatLib, line 465
    /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.thermostatLib, line 466
    final Long now = new Date().time // library marker kkossev.thermostatLib, line 467
    final Object lastEventState = device.currentState(eventName) // library marker kkossev.thermostatLib, line 468
    logDebug "getElapsedTimeFromEventInSeconds: eventName = ${eventName} lastEventState = ${lastEventState}" // library marker kkossev.thermostatLib, line 469
    if (lastEventState == null) { // library marker kkossev.thermostatLib, line 470
        logTrace 'getElapsedTimeFromEventInSeconds: lastEventState is null, returning 0' // library marker kkossev.thermostatLib, line 471
        return 0 // library marker kkossev.thermostatLib, line 472
    } // library marker kkossev.thermostatLib, line 473
    Long lastEventStateTime = lastEventState.date.time // library marker kkossev.thermostatLib, line 474
    //def lastEventStateValue = lastEventState.value // library marker kkossev.thermostatLib, line 475
    int diff = ((now - lastEventStateTime) / 1000) as int // library marker kkossev.thermostatLib, line 476
    // convert diff to minutes and seconds // library marker kkossev.thermostatLib, line 477
    logTrace "getElapsedTimeFromEventInSeconds: lastEventStateTime = ${lastEventStateTime} diff = ${diff} seconds" // library marker kkossev.thermostatLib, line 478
    return diff // library marker kkossev.thermostatLib, line 479
} // library marker kkossev.thermostatLib, line 480

// called from pollTuya() // library marker kkossev.thermostatLib, line 482
public void sendDigitalEventIfNeeded(final String eventName) { // library marker kkossev.thermostatLib, line 483
    final Object lastEventState = device.currentState(eventName) // library marker kkossev.thermostatLib, line 484
    if (lastEventState == null) { // library marker kkossev.thermostatLib, line 485
        logDebug "sendDigitalEventIfNeeded: lastEventState ${eventName} is null, skipping" // library marker kkossev.thermostatLib, line 486
        return // library marker kkossev.thermostatLib, line 487
    } // library marker kkossev.thermostatLib, line 488
    final int diff = getElapsedTimeFromEventInSeconds(eventName) // library marker kkossev.thermostatLib, line 489
    final String diffStr = timeToHMS(diff) // library marker kkossev.thermostatLib, line 490
    if (diff >= (settings.temperaturePollingInterval as int)) { // library marker kkossev.thermostatLib, line 491
        logDebug "pollTuya: ${eventName} was sent more than ${settings.temperaturePollingInterval} seconds ago (${diffStr}), sending digital event" // library marker kkossev.thermostatLib, line 492
        sendEventMap([name: lastEventState.name, value: lastEventState.value, unit: lastEventState.unit, type: 'digital']) // library marker kkossev.thermostatLib, line 493
    } // library marker kkossev.thermostatLib, line 494
    else { // library marker kkossev.thermostatLib, line 495
        logDebug "pollTuya: ${eventName} was sent less than ${settings.temperaturePollingInterval} seconds ago, skipping" // library marker kkossev.thermostatLib, line 496
    } // library marker kkossev.thermostatLib, line 497
} // library marker kkossev.thermostatLib, line 498

public void thermostatInitializeVars( boolean fullInit = false ) { // library marker kkossev.thermostatLib, line 500
    logDebug "thermostatInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.thermostatLib, line 501
    if (state.states == null) { state.states = [:] } // library marker kkossev.thermostatLib, line 502
    if (fullInit == true || state.states['isDigital'] == null) { state.states['isDigital'] = false } // library marker kkossev.thermostatLib, line 503
    if (fullInit == true || state.lastThermostatMode == null) { state.lastThermostatMode = 'unknown' } // library marker kkossev.thermostatLib, line 504
    if (fullInit == true || state.lastThermostatOperatingState == null) { state.lastThermostatOperatingState = 'unknown' } // library marker kkossev.thermostatLib, line 505
    if (fullInit || settings?.temperaturePollingInterval == null) { device.updateSetting('temperaturePollingInterval', [value: TrvTemperaturePollingIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.thermostatLib, line 506
} // library marker kkossev.thermostatLib, line 507

// called from initializeVars() in the main code ... // library marker kkossev.thermostatLib, line 509
public void thermostatInitEvents(final boolean fullInit=false) { // library marker kkossev.thermostatLib, line 510
    logDebug "thermostatInitEvents()... fullInit = ${fullInit}" // library marker kkossev.thermostatLib, line 511
    if (fullInit == true) { // library marker kkossev.thermostatLib, line 512
        String descText = 'inital attribute setting' // library marker kkossev.thermostatLib, line 513
        sendSupportedThermostatModes() // library marker kkossev.thermostatLib, line 514
        sendEvent(name: 'thermostatMode', value: 'heat', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 515
        state.lastThermostatMode = 'heat' // library marker kkossev.thermostatLib, line 516
        sendEvent(name: 'thermostatFanMode', value: 'auto', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 517
        state.lastThermostatOperatingState = 'idle' // library marker kkossev.thermostatLib, line 518
        sendEvent(name: 'thermostatOperatingState', value: 'idle', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 519
        sendEvent(name: 'thermostatSetpoint', value:  20.0, unit: '\u00B0C', isStateChange: true, description: descText)        // Google Home compatibility // library marker kkossev.thermostatLib, line 520
        sendEvent(name: 'heatingSetpoint', value: 20.0, unit: '\u00B0C', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 521
        state.lastHeatingSetpoint = 20.0 // library marker kkossev.thermostatLib, line 522
        sendEvent(name: 'coolingSetpoint', value: 35.0, unit: '\u00B0C', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 523
        sendEvent(name: 'temperature', value: 18.0, unit: '\u00B0', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 524
        updateDataValue('lastRunningMode', 'heat') // library marker kkossev.thermostatLib, line 525
    } // library marker kkossev.thermostatLib, line 526
    else { // library marker kkossev.thermostatLib, line 527
        logDebug "thermostatInitEvents: fullInit = ${fullInit}" // library marker kkossev.thermostatLib, line 528
    } // library marker kkossev.thermostatLib, line 529
} // library marker kkossev.thermostatLib, line 530

/* // library marker kkossev.thermostatLib, line 532
  Reset to Factory Defaults Command (0x00) // library marker kkossev.thermostatLib, line 533
  On receipt of this command, the device resets all the attributes of all its clusters to their factory defaults. // library marker kkossev.thermostatLib, line 534
  Note that networking functionality, bindings, groups, or other persistent data are not affected by this command // library marker kkossev.thermostatLib, line 535
*/ // library marker kkossev.thermostatLib, line 536
public void factoryResetThermostat() { // library marker kkossev.thermostatLib, line 537
    logDebug 'factoryResetThermostat() called!' // library marker kkossev.thermostatLib, line 538
    List<String> cmds  = zigbee.command(0x0000, 0x00) // library marker kkossev.thermostatLib, line 539
    sendZigbeeCommands(cmds) // library marker kkossev.thermostatLib, line 540
    sendInfoEvent 'The thermostat parameters were FACTORY RESET!' // library marker kkossev.thermostatLib, line 541
    if (this.respondsTo('refreshAll')) { // library marker kkossev.thermostatLib, line 542
        runIn(3, 'refreshAll') // library marker kkossev.thermostatLib, line 543
    } // library marker kkossev.thermostatLib, line 544
} // library marker kkossev.thermostatLib, line 545

// ========================================= Virtual thermostat functions  ========================================= // library marker kkossev.thermostatLib, line 547

public void setTemperature(Number temperaturePar) { // library marker kkossev.thermostatLib, line 549
    logDebug "setTemperature(${temperature}) called!" // library marker kkossev.thermostatLib, line 550
    if (isVirtual()) { // library marker kkossev.thermostatLib, line 551
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.thermostatLib, line 552
        double temperature = Math.round(temperaturePar * 2) / 2 // library marker kkossev.thermostatLib, line 553
        String descText = "temperature is set to ${temperature} \u00B0C" // library marker kkossev.thermostatLib, line 554
        sendEvent(name: 'temperature', value: temperature, unit: '\u00B0C', descriptionText: descText, type: 'digital') // library marker kkossev.thermostatLib, line 555
        logInfo "${descText}" // library marker kkossev.thermostatLib, line 556
    } // library marker kkossev.thermostatLib, line 557
    else { // library marker kkossev.thermostatLib, line 558
        logWarn 'setTemperature: not a virtual thermostat!' // library marker kkossev.thermostatLib, line 559
    } // library marker kkossev.thermostatLib, line 560
} // library marker kkossev.thermostatLib, line 561

// TODO - not used? // library marker kkossev.thermostatLib, line 563
List<String> thermostatRefresh() { // library marker kkossev.thermostatLib, line 564
    logDebug 'thermostatRefresh()...' // library marker kkossev.thermostatLib, line 565
    return [] // library marker kkossev.thermostatLib, line 566
} // library marker kkossev.thermostatLib, line 567

// Starting from library version 3.6.1  2025-10-31 the [Refresh] flag is set in the calling function  // library marker kkossev.thermostatLib, line 569
// before polling the thermostat in order to force sending events with [refresh] tag // library marker kkossev.thermostatLib, line 570
public List<String> pollThermostatCluster() { // library marker kkossev.thermostatLib, line 571
    return  zigbee.readAttribute(0x0201, [0x0000, /*0x0001, 0x0002,*/ 0x0012, 0x001B, 0x001C, 0x0029], [:], delay = 202)      // 0x0000 = local temperature, 0x0001 = outdoor temperature, 0x0002 = occupancy, 0x0012 = heating setpoint, 0x001B = controlledSequenceOfOperation, 0x001C = system mode (enum8 ) // library marker kkossev.thermostatLib, line 572
} // library marker kkossev.thermostatLib, line 573

// TODO - configure in the deviceProfile refresh: tag // library marker kkossev.thermostatLib, line 575
public List<String> pollBatteryPercentage() { // library marker kkossev.thermostatLib, line 576
    return zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)                          // battery percentage // library marker kkossev.thermostatLib, line 577
} // library marker kkossev.thermostatLib, line 578

public List<String> pollOccupancy() { // library marker kkossev.thermostatLib, line 580
    return  zigbee.readAttribute(0x0406, 0x0000, [:], delay = 100)      // Bit 0 specifies the sensed occupancy as follows: 1 = occupied, 0 = unoccupied. This flag bit will affect the Occupancy attribute of HVAC cluster, and the operation mode. // library marker kkossev.thermostatLib, line 581
} // library marker kkossev.thermostatLib, line 582

////////////////////////////// added 02/16/2024 ////////////////////////////// // library marker kkossev.thermostatLib, line 584

// scheduled for call from setThermostatMode() 4 seconds after the mode was potentiually changed. // library marker kkossev.thermostatLib, line 586
// also, called every 1 minute from receiveCheck() // library marker kkossev.thermostatLib, line 587
void modeReceiveCheck() { // library marker kkossev.thermostatLib, line 588
    if (settings?.resendFailed != true) { return } // library marker kkossev.thermostatLib, line 589
    if (state.lastTx?.isModeSetReq != true) { return }    // no mode change was requested // library marker kkossev.thermostatLib, line 590

    if (state.lastTx.mode != device.currentState('thermostatMode', true)?.value) { // library marker kkossev.thermostatLib, line 592
        state.lastTx['setModeRetries'] = (state.lastTx['setModeRetries'] ?: 0) + 1 // library marker kkossev.thermostatLib, line 593
        logWarn "modeReceiveCheck() <b>failed</b> (expected ${state.lastTx['mode']}, current ${device.currentState('thermostatMode', true)?.value}), retry#${state.lastTx['setModeRetries']} of ${MaxRetries}" // library marker kkossev.thermostatLib, line 594
        if (state.lastTx['setModeRetries'] < MaxRetries) { // library marker kkossev.thermostatLib, line 595
            logDebug "resending mode command : ${state.lastTx['mode']}" // library marker kkossev.thermostatLib, line 596
            state.stats['txFailCtr'] = (state.stats['txFailCtr']  ?: 0) + 1 // library marker kkossev.thermostatLib, line 597
            setThermostatMode( state.lastTx['mode'] ) // library marker kkossev.thermostatLib, line 598
        } // library marker kkossev.thermostatLib, line 599
        else { // library marker kkossev.thermostatLib, line 600
            logWarn "modeReceiveCheck(${state.lastTx['mode'] }}) <b>giving up retrying<b/>" // library marker kkossev.thermostatLib, line 601
            state.lastTx['isModeSetReq'] = false    // giving up // library marker kkossev.thermostatLib, line 602
            state.lastTx['setModeRetries'] = 0 // library marker kkossev.thermostatLib, line 603
        } // library marker kkossev.thermostatLib, line 604
    } // library marker kkossev.thermostatLib, line 605
    else { // library marker kkossev.thermostatLib, line 606
        logDebug "modeReceiveCheck mode was changed OK to (${state.lastTx['mode']}). No need for further checks." // library marker kkossev.thermostatLib, line 607
        state.lastTx.isModeSetReq = false    // setting mode was successfuly confimed, no need for further checks // library marker kkossev.thermostatLib, line 608
        state.lastTx.setModeRetries = 0 // library marker kkossev.thermostatLib, line 609
    } // library marker kkossev.thermostatLib, line 610
} // library marker kkossev.thermostatLib, line 611

// // library marker kkossev.thermostatLib, line 613
//  also, called every 1 minute from receiveCheck() // library marker kkossev.thermostatLib, line 614
public void setpointReceiveCheck() { // library marker kkossev.thermostatLib, line 615
    if (settings?.resendFailed != true) { return } // library marker kkossev.thermostatLib, line 616
    if (state.lastTx.isSetPointReq != true) { return } // library marker kkossev.thermostatLib, line 617

    if (state.lastTx.setPoint != NOT_SET && ((state.lastTx.setPoint as String) != (state.lastRx.setPoint as String))) { // library marker kkossev.thermostatLib, line 619
        state.lastTx.setPointRetries = (state.lastTx.setPointRetries ?: 0) + 1 // library marker kkossev.thermostatLib, line 620
        if (state.lastTx.setPointRetries < MaxRetries) { // library marker kkossev.thermostatLib, line 621
            logWarn "setpointReceiveCheck(${state.lastTx.setPoint}) <b>failed<b/> (last received is still ${state.lastRx.setPoint})" // library marker kkossev.thermostatLib, line 622
            logDebug "resending setpoint command : ${state.lastTx.setPoint} (retry# ${state.lastTx.setPointRetries}) of ${MaxRetries}" // library marker kkossev.thermostatLib, line 623
            state.stats.txFailCtr = (state.stats.txFailCtr ?: 0) + 1 // library marker kkossev.thermostatLib, line 624
            // TODO !! sendTuyaHeatingSetpoint(state.lastTx.setPoint) // library marker kkossev.thermostatLib, line 625
            setHeatingSetpoint(state.lastTx.setPoint as Number) // library marker kkossev.thermostatLib, line 626
        } // library marker kkossev.thermostatLib, line 627
        else { // library marker kkossev.thermostatLib, line 628
            logWarn "setpointReceiveCheck(${state.lastTx.setPoint}) <b>giving up retrying<b/>" // library marker kkossev.thermostatLib, line 629
            state.lastTx.isSetPointReq = false // library marker kkossev.thermostatLib, line 630
            state.lastTx.setPointRetries = 0 // library marker kkossev.thermostatLib, line 631
        } // library marker kkossev.thermostatLib, line 632
    } // library marker kkossev.thermostatLib, line 633
    else { // library marker kkossev.thermostatLib, line 634
        logDebug "setpointReceiveCheck setPoint was changed successfuly to (${state.lastTx.setPoint}). No need for further checks." // library marker kkossev.thermostatLib, line 635
        state.lastTx.setPoint = NOT_SET // library marker kkossev.thermostatLib, line 636
        state.lastTx.isSetPointReq = false // library marker kkossev.thermostatLib, line 637
    } // library marker kkossev.thermostatLib, line 638
} // library marker kkossev.thermostatLib, line 639

public void setLastRx( int dp, int fncmd) { // library marker kkossev.thermostatLib, line 641
    state.lastRx['dp'] = dp // library marker kkossev.thermostatLib, line 642
    state.lastRx['fncmd'] = fncmd // library marker kkossev.thermostatLib, line 643
} // library marker kkossev.thermostatLib, line 644

public void setLastTx( String mode=null, Boolean isModeSetReq=null) { // library marker kkossev.thermostatLib, line 646
    if (mode != null) { // library marker kkossev.thermostatLib, line 647
        state.lastTx['mode'] = mode // library marker kkossev.thermostatLib, line 648
    } // library marker kkossev.thermostatLib, line 649
    if (isModeSetReq != null) { // library marker kkossev.thermostatLib, line 650
        state.lastTx['isModeSetReq'] = isModeSetReq // library marker kkossev.thermostatLib, line 651
    } // library marker kkossev.thermostatLib, line 652
} // library marker kkossev.thermostatLib, line 653

public String getLastMode() { // library marker kkossev.thermostatLib, line 655
    return state.lastTx.mode ?: 'exception' // library marker kkossev.thermostatLib, line 656
} // library marker kkossev.thermostatLib, line 657

public boolean checkIfIsDuplicated( int dp, int fncmd ) { // library marker kkossev.thermostatLib, line 659
    Map oldDpFncmd = state.lastRx // library marker kkossev.thermostatLib, line 660
    return dp == oldDpFncmd.dp && fncmd == oldDpFncmd.fncmd // library marker kkossev.thermostatLib, line 661
} // library marker kkossev.thermostatLib, line 662

// ~~~~~ end include (179) kkossev.thermostatLib ~~~~~
