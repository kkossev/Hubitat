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
 * ver. 3.2.2  2024-06-06 kkossev  - (dev. branch) added AVATTO TS0601 _TZE200_ye5jkfsb
 *
 *                                   TODO: add powerSource capability
 *                                   TODO: hide the alwaysOn and Enable three-states events (not used here)
 *                                   TODO: hide the maxTimeBetweenReport preferences (not used here)
 *                                   TODO: Test BRT-100
 *                                   TODO: Test Aqara TRV
 *                                   TODO: Sonoff : decode weekly schedule responses (command 0x00)
 *                                   TODO: BRT-100 : what is emergencyHeatingTime and boostTime ?
 *                                   TODO: initializeDeviceThermostat() - configure in the device profile !
 *                                   TODO: partial match for the fingerprint (model if Tuya, manufacturer for the rest)
 *                                   TODO: remove (raw:) when debug is off
 *                                   TODO: add [refresh] for battery heatingSetpoint thermostatOperatingState events and logs
 *                                   TODO: option to disable the Auto mode ! (like in the wall thermostat driver)
 *                                   TODO: allow NULL parameters default values in the device profiles
 *                                   TODO: autoPollThermostat: no polling for device profile UNKNOWN
 *                                   TODO: configure the reporting for the 0x0201:0x0000 temperature !  (300..3600)
 *                                   TODO: Ping the device on initialize
 *                                   TODO: add factoryReset command Basic -0x0000 (Server); command 0x00
 *                                   TODO: Healthcheck to be every hour (not 4 hours) for thermostats
 *                                   TODO: add option 'Simple TRV' (no additinal attributes)
 *                                   TODO: add state.thermostat for stroring last attributes
 *                                   TODO: add 'force manual mode' preference (like in the wall thermostat driver)
 *                                   TODO: add Info dummy preference to the driver with a hyperlink
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off)
 *                                   TODO: Versions of the main module + included libraries
 *                                   TODO: HomeKit - min and max temperature limits?
 *                                   TODO: add receiveCheck() methods for heatingSetpint and mode (option)
 *                                   TODO: separate the autoPoll commands from the refresh commands (lite)
 *                                   TODO: All TRVs - after emergency heat, restpre the last mode and heatingSetpoint
 *                                   TODO: VIRTUAL thermostat - option to simualate the thermostatOperatingState
 *                                   TODO: UNKNOWN TRV - update the deviceProfile - separate 'Unknown Tuya' and 'Unknown ZCL'
 *                                   TODO: Sonoff - add 'emergency heat' simulation ?  ( +timer ?)
 *                                   TODO: Aqara TRV refactoring : add 'defaults' in the device profile to set up the systemMode initial value as 'unknown' ?
 *                                   TODO: Aqara TRV refactoring : eco mode simualtion
 *                                   TODO: Aqara TRV refactoring : emergency heat mode similation by setting maxTemp and when off - restore the previous temperature
 *                                   TODO: Aqara TRV refactoring : calibration as a command !
 *                                   TODO: Aqara TRV refactoring : physical vs digital events ?
 *                                   TODO: Aqara E1 external sensor
 */

static String version() { '3.2.2' }
static String timeStamp() { '2024/06/06 11:06 PM' }

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
        name: 'Zigbee TRVs and Thermostats',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee%20TRV/Zigbee_TRV_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        capability 'Actuator'
        capability 'Refresh'
        capability 'Sensor'
        capability 'Battery'
        capability 'Temperature Measurement'
        capability 'Thermostat'                 // needed for HomeKit
                    // coolingSetpoint - NUMBER; heatingSetpoint - NUMBER; supportedThermostatFanModes - JSON_OBJECT; supportedThermostatModes - JSON_OBJECT; temperature - NUMBER, unit:°F || °C; thermostatFanMode - ENUM ["on", "circulate", "auto"]
                    // thermostatMode - ENUM ["auto", "off", "heat", "emergency heat", "cool"]; thermostatOperatingState - ENUM ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]; thermostatSetpoint - NUMBER, unit:°F || °C
        capability 'ThermostatHeatingSetpoint'
        capability 'ThermostatCoolingSetpoint'
        capability 'ThermostatOperatingState'   // thermostatOperatingState - ENUM ["vent economizer", "pending cool", "cooling", "heating", "pending heat", "fan only", "idle"]
        capability 'ThermostatSetpoint'
        capability 'ThermostatMode'
        capability 'ThermostatFanMode'

        // TODO - add all other models attributes possible values
        //attribute 'battery', 'number'                               // Aqara, BRT-100
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
        attribute 'hysteresis', 'NUMBER'                            // Virtual thermostat
        attribute 'level', 'number'                                 // BRT-100
        attribute 'minHeatingSetpoint', 'number'                    // BRT-100, Sonoff, AVATTO
        attribute 'maxHeatingSetpoint', 'number'                    // BRT-100, Sonoff, AVATTO
        attribute 'sensor', 'enum', ['internal', 'external', 'both']         // Aqara E1, AVATTO
        attribute 'valveAlarm', 'enum',  ['false', 'true']          // Aqara E1
        attribute 'valveDetection', 'enum', ['off', 'on']           // Aqara E1
        attribute 'weeklyProgram', 'number'                         // BRT-100
        attribute 'windowOpenDetection', 'enum', ['off', 'on']      // BRT-100, Aqara E1, Sonoff
        attribute 'windowsState', 'enum', ['open', 'closed']        // BRT-100, Aqara E1
        attribute 'batteryLowAlarm', 'enum', ['batteryOK', 'batteryLow']        // TUYA_SASWELL
        //attribute 'workingState', "enum", ["open", "closed"]        // BRT-100

        // Aqaura E1 attributes     TODO - consolidate a common set of attributes
        attribute 'systemMode', 'enum', SystemModeOpts.options.values() as List<String>            // 'off','on'    - used!
        attribute 'preset', 'enum', PresetOpts.options.values() as List<String>                     // 'manual','auto','away'
        attribute 'awayPresetTemperature', 'number'

        command 'setThermostatMode', [[name: 'thermostat mode (not all are available!)', type: 'ENUM', constraints: ['--- select ---'] + AllPossibleThermostatModesOpts.options.values() as List<String>]]
        command 'setTemperature', ['NUMBER']                        // Virtual thermostat
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
        if (advancedOptions == true) { // TODO -  move it to the deviceProfile preferences
            input name: 'temperaturePollingInterval', type: 'enum', title: '<b>Temperature polling interval</b>', options: TrvTemperaturePollingIntervalOpts.options, defaultValue: TrvTemperaturePollingIntervalOpts.defaultValue, required: true, description: 'Changes how often the hub will poll the TRV for faster temperature reading updates and nice looking graphs.'
        }
    // the rest of the preferences are inputed from the deviceProfile maps in the deviceProfileLib
    }
}

@Field static final Map TrvTemperaturePollingIntervalOpts = [
    defaultValue: 600,
    options     : [0: 'Disabled', 60: 'Every minute (not recommended)', 120: 'Every 2 minutes', 300: 'Every 5 minutes', 600: 'Every 10 minutes', 900: 'Every 15 minutes', 1800: 'Every 30 minutes', 3600: 'Every 1 hour']
]
@Field static final Map AllPossibleThermostatModesOpts = [
    defaultValue: 1,
    options     : [0: 'off', 1: 'heat', 2: 'cool', 3: 'auto', 4: 'emergency heat', 5: 'eco']
]
@Field static final Map SystemModeOpts = [        //system_mode     TODO - remove it !!
    defaultValue: 1,
    options     : [0: 'off', 1: 'on']
]
@Field static final Map PresetOpts = [            // preset      TODO - remove it !!
    defaultValue: 1,
    options     : [0: 'manual', 1: 'auto', 2: 'away']
]

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
            refresh: ['refreshAqaraE1'/*,'pollAqara'*/],
            deviceJoinName: 'Aqara E1 Thermostat',
            configuration : [:]
    ],

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
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],

            preferences   : ['childLock':'0xFC11:0x0000', 'windowOpenDetection':'0xFC11:0x6000', 'frostProtectionTemperature':'0xFC11:0x6002', 'minHeatingSetpoint':'0x0201:0x0015', 'maxHeatingSetpoint':'0x0201:0x0016', 'calibrationTemp':'0x0201:0x0010' ],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0006,0020,0201,FC57,FC11', outClusters:'000A,0019', model:'TRVZB', manufacturer:'SONOFF', deviceJoinName: 'Sonoff TRVZB']
            ],
            commands      : ['printFingerprints':'printFingerprints', 'autoPollThermostat':'autoPollThermostat', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [:],
            attributes    : [   // TODO - configure the reporting for the 0x0201:0x0000 temperature !  (300..3600)
                [at:'0x0201:0x0000',  name:'temperature',           type:'decimal', dt:'0x29', rw:'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'Local temperature'],
                [at:'0x0201:0x0002',  name:'occupancy',             type:'enum',    dt:'0x18', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'unoccupied', 1: 'occupied'], unit:'',  description:'Occupancy'],
                [at:'0x0201:0x0003',  name:'absMinHeatingSetpointLimit',  type:'decimal', dt:'0x29', rw:'ro', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'Abs Min Heat Setpoint Limit'],
                [at:'0x0201:0x0004',  name:'absMaxHeatingSetpointLimit',  type:'decimal', dt:'0x29', rw:'ro', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'Abs Max Heat Setpoint Limit'],
                [at:'0x0201:0x0010',  name:'calibrationTemp',  preProc:'signedInt',     type:'decimal', dt:'0x28', rw:'rw', min:-7.0,  max:7.0, defVal:0.0, step:0.2, scale:10,  unit:'°C', title: '<b>Local Temperature Calibration</b>', description:'Room temperature calibration'],
                [at:'0x0201:0x0012',  name:'heatingSetpoint',       type:'decimal', dt:'0x29', rw:'rw', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Heating Setpoint</b>',      description:'Occupied heating setpoint'],
                [at:'0x0201:0x0015',  name:'minHeatingSetpoint',    type:'decimal', dt:'0x29', rw:'rw', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Min Heating Setpoint</b>', description:'Min Heating Setpoint Limit'],
                [at:'0x0201:0x0016',  name:'maxHeatingSetpoint',    type:'decimal', dt:'0x29', rw:'rw', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Max Heating Setpoint</b>', description:'Max Heating Setpoint Limit'],
                [at:'0x0201:0x001A',  name:'remoteSensing',         type:'enum',    dt:'0x18', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',  title: '<b>Remote Sensing<</b>', description:'Remote Sensing'],
                [at:'0x0201:0x001B',  name:'termostatRunningState', type:'enum',    dt:'0x30', rw:'rw', min:0,    max:2,    step:1,  scale:1,    map:[0: 'off', 1: 'heat', 2: 'unknown'], unit:'',  description:'termostatRunningState (relay on/off status)'],      //  nothing happens when WRITING ????
                [at:'0x0201:0x001C',  name:'thermostatMode',        type:'enum',    dt:'0x30', rw:'rw', min:0,    max:4,    step:1,  scale:1,    map:[0: 'off', 1: 'auto', 2: 'invalid', 3: 'invalid', 4: 'heat'], unit:'', title: '<b>System Mode</b>',  description:'Thermostat Mode'],
                [at:'0x0201:0x001E',  name:'thermostatRunMode',     type:'enum',    dt:'0x30', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heat'], unit:'', title: '<b>Thermostat Run Mode</b>',   description:'Thermostat run mode'],
                [at:'0x0201:0x0020',  name:'startOfWeek',           type:'enum',    dt:'0x30', rw:'ro', min:0,    max:6,    step:1,  scale:1,    map:[0: 'Sun', 1: 'Mon', 2: 'Tue', 3: 'Wed', 4: 'Thu', 5: 'Fri', 6: 'Sat'], unit:'',  description:'Start of week'],
                [at:'0x0201:0x0021',  name:'numWeeklyTransitions',  type:'number',  dt:'0x20', rw:'ro', min:0,    max:255,  step:1,  scale:1,    unit:'',  description:'Number Of Weekly Transitions'],
                [at:'0x0201:0x0022',  name:'numDailyTransitions',   type:'number',  dt:'0x20', rw:'ro', min:0,    max:255,  step:1,  scale:1,    unit:'',  description:'Number Of Daily Transitions'],
                [at:'0x0201:0x0025',  name:'thermostatProgrammingOperationMode', type:'enum',  dt:'0x18', rw:'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'mode1', 1: 'mode2'], unit:'',  title: '<b>Thermostat Programming Operation Mode/b>', description:'Thermostat programming operation mode'],  // nothing happens when WRITING ????
                [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum', dt:'0x19', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heating'], unit:'',  description:'termostatRunningState (relay on/off status)'],   // read only!
                // https://github.com/photomoose/zigbee-herdsman-converters/blob/227b28b23455f1a767c94889f57293c26e4a1e75/src/devices/sonoff.ts
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
            // TODO :         configure: async (device, coordinatorEndpoint, logger) => {
            // const endpoint = device.getEndpoint(1);
            // await reporting.bind(endpoint, coordinatorEndpoint, ['hvacThermostat']);
            // await reporting.thermostatTemperature(endpoint);
            // await reporting.thermostatOccupiedHeatingSetpoint(endpoint);
            // await reporting.thermostatSystemMode(endpoint);
            // await endpoint.read('hvacThermostat', ['localTemperatureCalibration']);
            // await endpoint.read(0xFC11, [0x0000, 0x6000, 0x6002]);
            //                          ^^ TODO
            ],
            refresh: ['pollBatteryPercentage', 'pollThermostatCluster'],
            deviceJoinName: 'Sonoff TRVZB',
            configuration : [:]
    ],

    // BRT-100-B0
    //              https://github.com/Koenkk/zigbee-herdsman-converters/blob/47f56c19a3fdec5f23e74f805ff640a931721099/src/devices/moes.ts#L282
    //              TODO - what is the difference between 'holidays' mode and 'ecoMode' ?  Which one to use to substitute the 'off' mode ?
    'MOES_BRT-100'   : [
            description   : 'MOES BRT-100 TRV',

            device        : [models: ['TS0601'], type: 'TRV', powerSource: 'battery', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : ['windowOpenDetection':'8', 'childLock':'13', 'boostTime':'103', 'calibrationTemp':'105', 'ecoTemp':'107', 'minHeatingSetpoint':'109', 'maxHeatingSetpoint':'108'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_b6wax7g0', deviceJoinName: 'MOES BRT-100 TRV']
            ],
            commands      : [/*"pollTuya":"pollTuya","autoPollThermostat":"autoPollThermostat",*/ 'sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [
                [dp:1,   name:'thermostatMode',     type:'enum',            rw: 'rw', min:0,     max:3,    defVal:'1',  step:1,   scale:1,  map:[0: 'auto', 1: 'heat', 2: 'TempHold', 3: 'holidays'] ,   unit:'', title:'<b>Thermostat Mode</b>',  description:'Thermostat mode'],
                [dp:2,   name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,   max:45.0, defVal:20.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
                [dp:3,   name:'temperature',        type:'decimal',         rw: 'ro', min:-10.0, max:50.0, defVal:20.0, step:0.5, scale:10, unit:'°C',  description:'Temperature'],
                [dp:4,   name:'emergencyHeating',   type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Emergency Heating</b>',  description:'Emergency heating'],
                [dp:5,   name:'emergencyHeatingTime',   type:'number',      rw: 'rw', min:0,     max:720 , defVal:15,   step:15,  scale:1,  unit:'minutes', title:'<b>Emergency Heating Timer</b>',  description:'Emergency heating timer'],
                [dp:7,   name:'thermostatOperatingState',  type:'enum',     rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'heating', 1:'idle'] ,  unit:'', description:'Thermostat Operating State(working state)'],
                [dp:8,   name:'windowOpenDetection', type:'enum', dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Window Detection</b>',  description:'Window detection'],
                [dp:9,   name:'windowsState',       type:'enum',            rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'open', 1:'closed'] ,   unit:'', title:'<bWindow State</b>',  description:'Window state'],
                [dp:13,  name:'childLock',          type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Child Lock</b>',  description:'Child lock'],
                [dp:14,  name:'battery',            type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:1,   scale:1,  unit:'%',          description:'Battery level'],
                [dp:101, name:'weeklyProgram',      type:'number',          rw: 'ro', min:0,     max:9999, defVal:0,    step:1,   scale:1,  unit:'',          description:'Weekly program'],
                [dp:103, name:'boostTime',          type:'number',          rw: 'rw', min:100,   max:900 , defVal:100,  step:1,   scale:1,  unit:'seconds', title:'<b>Boost Timer</b>',  description:'Boost timer'],
                [dp:104, name:'level',              type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:1,   scale:1,  unit:'%',          description:'Valve level'],
                [dp:105, name:'calibrationTemp',    type:'decimal',         rw: 'rw', min:-9.0,  max:9.0,  defVal:00.0, step:1,   scale:1,  unit:'°C',  title:'<b>Calibration Temperature</b>', description:'Calibration Temperature'],
                [dp:106, name:'ecoMode',            type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Eco mode</b>',  description:'Eco mode'],
                [dp:107, name:'ecoTemp',            type:'decimal',         rw: 'rw', min:5.0,   max:35.0, defVal:7.0,  step:1.0, scale:1,  unit:'°C',  title: '<b>Eco Temperature</b>',      description:'Eco temperature'],
                [dp:108, name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:15.0,  max:45.0, defVal:35.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Maximum Temperature</b>',      description:'Maximum temperature'],
                [dp:109, name:'minHeatingSetpoint', type:'decimal',         rw: 'rw', min:5.0,   max:15.0, defVal:10.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Minimum Temperature</b>',      description:'Minimum temperature'],
            ],
            supportedThermostatModes: ['off', 'heat', 'auto', 'emergency heat', 'eco'],
            refresh: ['pollTuya'],
            deviceJoinName: 'MOES BRT-100 TRV',
            configuration : [:]
    ],

    // TUYA_SASWELL
    //              https://github.com/jacekk015/zha_quirks/blob/main/trv_saswell.py        https://github.com/jacekk015/zha_quirks?tab=readme-ov-file#trv_saswellpy
    //              https://github.com/Koenkk/zigbee-herdsman-converters/blob/11e06a1b28a7ea2c3722c515f0ef3a148e81a3c3/src/devices/saswell.ts#L37
    //              TODO - what is the difference between 'holidays' mode and 'ecoMode' ?  Which one to use to substitute the 'off' mode ?
    'TUYA_SASWELL'   : [
            description   : 'Tuya Saswell TRV (not fully working yet!)',
            device        : [models: ['TS0601'], type: 'TRV', powerSource: 'battery', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : ['windowOpenDetection':'8', 'childLock':'40', 'calibrationTemp':'27'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_0dvm9mva', deviceJoinName: 'TRV RTX ZB-RT1'],         // https://community.hubitat.com/t/zigbee-radiator-trv-rtx-zb-rt1/129812?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_yw7cahqs', deviceJoinName: 'TUYA_SASWELL TRV'],       // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_c88teujp', deviceJoinName: 'TUYA_SASWELL TRV'],       // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_azqp6ssj', deviceJoinName: 'TUYA_SASWELL TRV'],       // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_9gvruqf5', deviceJoinName: 'TUYA_SASWELL TRV'],       // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_zuhszj9s', deviceJoinName: 'TUYA_SASWELL TRV'],       // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_zr9c0day', deviceJoinName: 'TUYA_SASWELL TRV'],       // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_h4cgnbzg', deviceJoinName: 'TUYA_SASWELL TRV'],       // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_exfrnlow', deviceJoinName: 'TUYA_SASWELL TRV'],       // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_9m4kmbfu', deviceJoinName: 'TUYA_SASWELL TRV'],       // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_3yp57tby', deviceJoinName: 'TUYA_SASWELL TRV'],       // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_mz5y07w2', deviceJoinName: 'Garza Smart TRV']         // not tested              //https://github.com/zigpy/zha-device-handlers/issues/2486
            ],
            commands      : ['limescaleProtect':'limescaleProtect', 'printFingerprints':'printFingerprints', 'resetStats':'resetStats', 'refresh':'refresh', 'customRefresh':'customRefresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [
                [dp:8,   name:'windowOpenDetection', type:'enum', dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Window Detection</b>',  description:'Open Window Detection support'],      // SASWELL_WINDOW_DETECT_ATTR
                [dp:10,  name:'antiFreeze',         type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Anti-Freeze</b>',  description:'Anti-Freeze support'],                      // SASWELL_ANTI_FREEZE_ATTR
                [dp:27,  name:'calibrationTemp',    type:'decimal',         rw: 'rw', min:-6.0,  max:6.0,  defVal:0.0,  step:1,   scale:1,  unit:'°C',  title:'<b>Calibration Temperature</b>', description:'Calibration Temperature'],                // SASWELL_TEMP_CORRECTION_ATTR = 0x021B  # uint32 - temp correction 539 (27 dec)
                [dp:40,  name:'childLock',          type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Child Lock</b>',  description:'Child Lock setting support. Please remember that CL has to be set manually on the device. This only controls if locking is possible at all'],                  // SASWELL_CHILD_LOCK_ATTR
                [dp:101, name:'thermostatMode',     type:'enum',            rw: 'rw', min:0,     max:1,    defVal:'1',  step:1,   scale:1,  map:[0: 'off', 1: 'heat'] ,   unit:'', title:'<b>Thermostat Mode</b>',  description:'Thermostat mode'],    // SASWELL_ONOFF_ATTR = 0x0165  # [0/1] on/off 357                     (101 dec)
                [dp:102, name:'temperature',        type:'decimal',         rw: 'ro', min:-10.0, max:50.0, defVal:20.0, step:0.5, scale:10, unit:'°C',  description:'Temperature'],                                                                    // SASWELL_ROOM_TEMP_ATTR = 0x0266  # uint32 - current room temp 614   (102 dec)
                [dp:103, name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,   max:30.0, defVal:20.0, step:1.0, scale:10, unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],        // SASWELL_TARGET_TEMP_ATTR = 0x0267  # uint32 - target temp 615       (103 dec)
                [dp:105, name:'batteryLowAlarm',    type:'enum',  dt: '01', rw: 'r0', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'batteryOK', 1:'batteryLow'] ,   unit:'',  description:'Battery low'],                                            // SASWELL_BATTERY_ALARM_ATTR = 0x569  # [0/1] on/off - battery low 1385   (105 dec)
                [dp:106, name:'awayMode',           type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Away Mode</b>',  description:'Away Mode On/Off support'],                    // SASWELL_AWAY_MODE_ATTR = 0x016A  # [0/1] on/off 362                 (106 dec)
                [dp:108, name:'scheduleMode',       type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Schedule Mode</b>',  description:'Schedule Mode On/Off support'],            // SASWELL_SCHEDULE_MODE_ATTR = 0x016C  # [0/1] on/off 364             (108 dec)
                [dp:130, name:'limescaleProtect',   type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Limescale Protect</b>',  description:'Limescale Protection support'],    // SASWELL_LIMESCALE_PROTECT_ATTR
            // missing !                 [dp:7,   name:'thermostatOperatingState',  type:"enum",     rw: "rw", min:0,     max:1 ,   defVal:"0",  step:1,   scale:1,  map:[0:"heating", 1:"idle"] ,  unit:"", description:'Thermostat Operating State(working state)'],
            ],
            supportedThermostatModes: ['off', 'heat'],
            refresh: ['pollTuya'],
            deviceJoinName: 'TUYA_SASWELL TRV',
            configuration : [:]
    ],

    'TUYA/AVATTO/MOES_ME81H_THERMOSTAT'   : [       // https://github.com/Koenkk/zigbee-herdsman-converters/blob/3ec951e4c16310be29cec0473030827fb9a5bc23/src/devices/moes.ts#L97-L165 
            description   : 'Tuya/Avatto/Moes ME81H BHT-002-GCLZB Thermostat',
            device        : [models: ['TS0601'], type: 'Thermostat', powerSource: 'ac', isSleepy:false],    //  model: 'BHT-002-GCLZB' 
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [childLock:'40', minHeatingSetpoint:'26', maxHeatingSetpoint:'19', sensorSelection:'43', antiFreeze:'103', hysterersis:'106', temperatureCalibration:'105', hysteresis:106],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ye5jkfsb", controllerType: "ZGB", deviceJoinName: 'AVATTO ME81H Thermostat'],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_aoclfnxz", deviceJoinName: 'Moes BHT series Thermostat'],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ztvwu4nk", deviceJoinName: 'Moes BHT series Thermostat'],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_5toc8efa", deviceJoinName: 'Moes BHT series Thermostat'],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_5toc8efa", deviceJoinName: 'Moes BHT series Thermostat'],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_aoclfnxz", deviceJoinName: 'Moes BHT series Thermostat'],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_u9bfwha0", deviceJoinName: 'Moes BHT series Thermostat'],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_u9bfwha0", deviceJoinName: 'Moes BHT series Thermostat']
            ],
            commands      : [/*"pollTuya":"pollTuya","autoPollThermostat":"autoPollThermostat",*/ 'sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [   // https://github.com/Koenkk/zigbee-herdsman-converters/blob/a4a2ac2e382508fc911d721edbb9d4fa308a68bb/lib/tuya.js#L326-L339
                [dp:1,   name:'systemMode',         type:'enum',            rw: 'rw', min:0,     max:3,    defVal:'1',  step:1,   scale:1,  map:[0: 'off', 1: 'on'] ,   unit:'', title:'<b>Thermostat Switch</b>',  description:'Thermostat Switch (system mode)'],
                [dp:2,   name:'thermostatMode',     type:'enum',            rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'heat', 1:'auto'] ,  unit:'', description:'Thermostat Working Mode'],                             // moesHold: 2,
                [dp:16,  name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,   max:99.0, defVal:20.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],    // moesHeatingSetpoint: 16,
                [dp:19,  name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:30.0,  max:95.0, defVal:40.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Maximum Heating Setpoint</b>',      description:'Maximum heating setpoint'],    // moesMaxTemp: 18, 
                [dp:23,  name:'temperatureScale',   type:'enum',            rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'Celsius', 1:'Fahrenheit'] ,  unit:'', description:'Thermostat Operating State(working state)'], // temperature scale              temp_unit_convert	Enum	{  "range": [    "c",    "f"  ] }
                [dp:24,  name:'temperature',        type:'decimal',         rw: 'ro', min:0.0,   max:99.0, defVal:20.0, step:0.5, scale:1 , unit:'°C',  description:'Temperature'],                                                                // moesLocalTemp: 24,
                [dp:26,  name:'minHeatingSetpoint', type:'decimal',         rw: 'rw', min:5.0,   max:20.0, defVal:10.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Minimum Heating Setpoint</b>',      description:'Minimum heating setpoint'],
                [dp:27,  name:'temperatureCorrection',     type:'decimal',         rw: 'rw', min:-9.0,  max:9.0,  defVal:0.0,  step:1,   scale:1,  unit:'°C',  title:'<b>Temperature Correction</b>',  description:'Temperature correction'],            // moesTempCalibration: 27,
                [dp:36,  name:'thermostatOperatingState',  type:'enum',     rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'heating', 1:'idle'] ,  unit:'', description:'Thermostat Operating State(working state)'],      // state of the valve     valve_state	Enum	{  "range": [    "open",    "close"  ] }
                [dp:39,  name:'reset',              type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Reset</b>',  description:'Reset'],
                [dp:40,  name:'childLock',          type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Child Lock</b>',  description:'Child lock'],              // moesChildLock: 40,
                [dp:43,  name:'sensor',             type:'enum',  dt: '01', rw: 'rw', min:0,     max:2 ,   defVal:'0',  step:1,   scale:1,  map:[0:'internal', 1:'external', 2:'both'] ,   unit:'', title:'<b>Sensor Selection</b>',  description:'Sensor Selection'],              // sensor_choose	Enum	{  "range": [    "in",    "out"  ] }
                [dp:45,  name:'faultAlarm',         type:'enum',            rw: 'ro', min:0,     max:2 ,   defVal:'0',  step:1,   scale:1,  map:[0:'e1', 1:'e2', 2:'e3'] ,   unit:'', title:'<b>Fault Alarm Selection</b>',  description:'Fault alarm'],              // fault alarm  Bitmap	{  "label": [    "e1",    "e2",    "e3"  ] } // etopErrorStatus: 13,
                [dp:101, name:'floorTemperature',   type:'decimal',         rw: 'ro', min:0.0,   max:99.0, defVal:20.0, step:0.5, scale:1 , unit:'°C',  description:'Temperature'],                                                                // moesLocalTemp: 24,
                [dp:103, name:'antiFreeze',         type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Anti-Freeze</b>',  description:'Anti-Freeze support'],  
                [dp:104, name:'programmingMode',    type:'enum',            rw: 'ro', min:0,     max:2 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'two weekends', 2:'one weeked', 3:'no weekends'] ,   unit:'', title:'<b>Programming Mode</b>',  description:'Programming Mode'], 
                [dp:105, name:'temperatureCalibration',  type:'decimal',    rw: 'rw', min:-9.0,  max:9.0,  defVal:-2.0, step:1,   scale:1,  unit:'°C',  title:'<b>Temperature Calibration</b>',  description:'Temperature calibration'],
                [dp:106, name:'hysteresis',        type:'decimal',         rw: 'rw', min:1.0,   max:5.0,  defVal:1.0,  step:1.5, scale:1,  unit:'',    title: '<b>Hysteresis</b>', description:'hysteresis']                                       // moesDeadZoneTemp: 20,
            ],
            supportedThermostatModes: ['off', 'heat', 'auto'],
            refresh: ['pollTuya'],
            deviceJoinName: 'Avatto Thermostat',
            configuration : [:]
    ],

/*
    'NAMRON'   : [
            description   : 'NAMRON Thermostat`(not working yet)',
            device        : [manufacturers: ['NAMRON AS'], type: 'TRV', powerSource: 'mains', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,0009,0408,0702,0B04,0B05,1000,FCCC', outClusters:'0019,1000', model:'4512749-N', manufacturer:'NAMRON AS', deviceJoinName: 'NAMRON']   // EP02: 0000,0004,0005,0201  // EPF2: 0021
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences',
                              'factoryResetThermostat':'factoryResetThermostat'
            ],
            attributes    : [
                [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt:'0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', description:'Measured room temperature'],       // ^^^ (int16S, read-only) reportable LocalTemperature : Attribute This is room temperature, the maximum resolution this format allows is 0.01 ºC.
                [at:'0x0201:0x0001',  name:'outdoorTemperature',       type:'decimal', dt:'0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'Floor temperature'],          // ^^^ (int16S, read-only) reportable OutdoorTemperature : This is floor temperature, the maximum resolution this format allows is 0.01 ºC.
                [at:'0x0201:0x0002',  name:'occupancy',                type:'enum',    dt:'0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',  description:'Occupancy'],      // ^^^ (bitmap8, read-only) Occupancy : When this flag is set as 1, it means occupied, OccupiedHeatingSetpoint will be used, otherwise UnoccupiedHeatingSetpoint will be used
                [at:'0x0201:0x0010',  name:'localTemperatureCalibration', type:'decimal', dt:'0x21', rw: 'rw', min:-30.0,  max:30.0, defVal:0.0, step:0.5, scale:10,  unit:'°C', title: '<b>Local Temperature Calibration</b>', description:'Room temperature calibration'],       // ^^^ (Int8S, reportable) TODO: check dt!!!    LocalTemperatureCalibration : Room temperature calibration, range is -30-30, the maximum resolution this format allows 0.1°C. Default value: 0
                [at:'0x0201:0x0011',  name:'coolingSetpoint',          type:'decimal', dt:'0x21', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Cooling Setpoint</b>',              description:'This system is not invalid'],       // not used
                [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt:'0x21', rw: 'rw', min:0.0,  max:40.0, defVal:30.0, step:0.01, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],                // ^^^(int16S, reportable)  OccupiedHeatingSetpoint : Range is 0-4000,the maximum resolution this format allows is 0.01 ºC. Default is 0xbb8(30.00ºC)
                [at:'0x0201:0x0014',  name:'unoccupiedHeatingSetpoint', type:'decimal', dt:'0x21', rw: 'rw', min:0.0,  max:40.0, defVal:30.0, step:0.01, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],                // ^^^(int16S, reportable)  OccupiedHeatingSetpoint : Range is 0-4000,the maximum resolution this format allows is 0.01 ºC. Default is 0x258(6.00ºC)
                [at:'0x0201:0x001B',  name:'termostatRunningState',    type:'enum',    dt:'0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',  description:'termostatRunningState (relay on/off status)'],                // ^^^(Map16, read-only, reportable) HVAC relay state/ termostatRunningState Indicates the relay on/off status, here only supports bit0( Heat State)
                // ============ Proprietary Attributes: Manufacturer code 0x1224 ============
                [at:'0x0201:0x0000',  name:'lcdBrightnesss',           type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defVal:'1',   step:1,  scale:1,    map:[0: 'Low Level', 1: 'Mid Level', 2: 'High Level'], unit:'',  title: '<b>OLED brightness</b>',description:'OLED brightness'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  OLED brightness when operate the buttons: Value=0 : Low Level Value=1 : Mid Level(default) Value=2 : High Level
                [at:'0x0201:0x1002',  name:'floorSensorType',          type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:1,    max:5, defVal:'1',step:1,  scale:1,    map:[1: 'NTC 10K/25', 2: 'NTC 15K/25', 3: 'NTC 12K/25', 4: 'NTC 100K/25', 5: 'NTC 50K/25'], unit:'',  title: '<b>Floor Sensor Type</b>',description:'Floor Sensor Type'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  TODO: check why there are 3 diferent enum groups???    FloorSenserType Value=5 : NTC 12K/25  Value=4 : NTC 100K/25 Value=3 : NTC 50K/25 Select external (Floor) sensor type: Value=1 : NTC 10K/25 (Default) Value=2 : NTC 15K/25 Value=5 : NTC 12K/25 Value=4 : NTC 100K/25 Value=3 : NTC 50K/25 Select external (Floor) sensor type: Value=1 : NTC 10K/25 (Default) Value=2 : NTC 15K/25
                [at:'0x0201:0x1003',  name:'controlType',              type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defVal:'0',step:1,  scale:1,    map:[0: 'Room sensor', 1: 'floor sensor', 2: 'Room+floor sensor'], unit:'',  title: '<b>Control Type</b>',description:'Control Type'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  ControlType The referring sensor for heat control: Value=0 : Room sensor(Default) Value=1 : floor sensor Value=2 : Room+floor sensor
                [at:'0x0201:0x1004',  name:'powerUpStatus',            type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defVal:'1',   step:1,  scale:1,    map:[0: 'Low Level', 1: 'Mid Level', 2: 'High Level'], unit:'',  title: '<b>Power Up Status</b>',description:'Power Up Status'],                // ^^^ (ENUM8,reportable) TODO: check dt!!! PowerUpStatus Value=0 : default mode The mode after reset power of the device: Value=1 : last status before power off (Default)
                [at:'0x0201:0x1005',  name:'floorSensorCalibration',   type:'decimal', dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:-30.0,  max:30.0, defVal:0.0, step:0.5, scale:10,  unit:'°C', title: '<b>Floor Sensor Calibration</b>', description:'Floor Sensor Calibration/i>'],                // ^^^ (Int8S, reportable) TODO: check dt!!!    FloorSenserCalibration The temp compensation for the external (floor) sensor, range is -30-30, unit is 0.1°C. default value 0
                [at:'0x0201:0x1006',  name:'dryTime',                  type:'number',  dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:5,  max:100, defVal:5, step:1, scale:1,  unit:'minutes', title: '<b>Dry Time</b>', description:'The duration of Dry Mode/i>'],                // ^^^ (Int8S, reportable) TODO: check dt!!!    DryTime The duration of Dry Mode, range is 5-100, unit is min. Default value is 5.
                [at:'0x0201:0x1007',  name:'modeAfterDry',             type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defVal:'2',   step:1,  scale:1,    map:[0: 'OFF', 1: 'Manual mode', 2: 'Auto mode', 3: 'Away mode'], unit:'',  title: '<b>Mode After Dry</b>',description:'The mode after Dry Mode'],                // ^^^ (ENUM8,reportable) TODO: check dt!!! ModeAfterDry The mode after Dry Mode: Value=0 : OFF Value=1 : Manual mode Value=2 : Auto mode –schedule (default) Value=3 : Away mode
                [at:'0x0201:0x1008',  name:'temperatureDisplay',       type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'1',   step:1,  scale:1,    map:[0: 'Room Temp', 1: 'Floor Temp'], unit:'',  title: '<b>Temperature Display</b>',description:'Temperature Display'],                // ^^^ (ENUM8,reportable) TODO: check dt!!! TemperatureDisplay Value=0 : Room Temp (Default) Value=1 : Floor Temp
                [at:'0x0201:0x1009',  name:'windowOpenCheck',          type:'decimal', dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:0.3, max:8.0, defVal:0, step:0.5, scale:10,  unit:'', title: '<b>Window Open Check</b>', description:'The threshold to detect open window, 0 means disabled'],                // ^^^ (INT8U,reportable) TODO: check dt!!!    WindowOpenCheck The threshold to detect open window, range is 0.3-8, unit is 0.5ºC, 0 means disabled, default is 0
                [at:'0x0201:0x100A',  name:'hysterersis',              type:'decimal', dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:5.0, max:20.0, defVal:5.0, step:0.5, scale:10,  unit:'', title: '<b>Hysterersis</b>', description:'Hysterersis'],                // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  Hysterersis setting, range is 5-20, unit is 0.1ºC, default value is 5
                [at:'0x0201:0x100B',  name:'displayAutoOffEnable',     type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'1',   step:1,  scale:1,    map:[0: 'Disabled', 1: 'Enabled'], unit:'',  title: '<b>Display Auto Off Enable</b>',description:'Display Auto Off Enable'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  DisplayAutoOffEnable 0, disable Display Auto Off function 1, enable Display Auto Off function
                [at:'0x0201:0x2001',  name:'alarmAirTempOverValue',    type:'decimal', dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:0.2, max:6.0, defVal:4.5, step:0.1, scale:10,  unit:'', title: '<b>Alarm Air Temp Over Value</b>', description:'Alarm Air Temp Over Value, 0 means disabled,'],                // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  AlarmAirTempOverValue Room temp alarm threshold, range is 0.20-60, unit is 1ºC,0 means disabled, default is 45
                [at:'0x0201:0x2002',  name:'awayModeSet',              type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defVal:'0',   step:1,  scale:1,    map:[0: 'Not away', 1: 'Away'], unit:'',  title: '<b>Away Mode Set</b>',description:'Away Mode Set'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  Away Mode Set: Value=1: away Value=0: not away (default)
                // Command supported  !!!!!!!!!! TODO !!!!!!!!!!!! not attribute, but a command !!!!!!!!!!!!!!!!
                [cmd:'0x0201:0x0000',  name:'setpointRaiseLower',       type:'decimal', dt:'0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:10,  unit:'°C', description:'Setpoint Raise/Lower'],                // ^^^ Setpoint Raise/Lower Increase or decrease the set temperature according to current mode, unit is 0.1ºC
            ],
            refresh: ['pollThermostatCluster'],
            deviceJoinName: 'NAMRON Thermostat',
            configuration : [:]
    ],
*/

    '---'   : [
            description   : '--------------------------------------',
    //            models        : [],
    //            fingerprints  : [],
    ],

    'VIRTUAL'   : [        // https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/virtualThermostat.groovy
            description   : 'Virtual thermostat',
            device        : [type: 'TRV', powerSource: 'battery', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : ['hysteresis':'hysteresis', 'minHeatingSetpoint':'minHeatingSetpoint', 'maxHeatingSetpoint':'maxHeatingSetpoint', 'simulateThermostatOperatingState':'simulateThermostatOperatingState'],
            commands      : ['printFingerprints':'printFingerprints', 'sendSupportedThermostatModes':'sendSupportedThermostatModes', 'autoPollThermostat':'autoPollThermostat', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            attributes    : [
                [at:'hysteresis',          name:'hysteresis',       type:'enum',    dt:'virtual', rw:'rw',  min:0,   max:4,    defVal:'3',  step:1,  scale:1,  map:[0:'0.1', 1:'0.25', 2:'0.5', 3:'1', 4:'2'],   unit:'', title:'<b>Hysteresis</b>',  description:'hysteresis'],
                [at:'minHeatingSetpoint',  name:'minHeatingSetpoint',    type:'decimal', dt:'virtual', rw:'rw', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Min Heating Setpoint</b>', description:'Min Heating Setpoint Limit'],
                [at:'maxHeatingSetpoint',  name:'maxHeatingSetpoint',    type:'decimal', dt:'virtual', rw:'rw', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Max Heating Setpoint</b>', description:'Max Heating Setpoint Limit'],
                [at:'thermostatMode',      name:'thermostatMode',   type:'enum',    dt:'virtual', rw:'rw',  min:0,    max:5,    defVal:'0',  step:1,  scale:1,  map:[0: 'heat', 1: 'auto', 2: 'eco', 3:'emergency heat', 4:'off', 5:'cool'],            unit:'', title: '<b>Thermostat Mode</b>',           description:'Thermostat Mode'],
                [at:'heatingSetpoint',     name:'heatingSetpoint',  type:'decimal', dt:'virtual', rw: 'rw', min:5.0, max:45.0, defVal:20.0, step:0.5, scale:1,  unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
                [at:'coolingSetpoint',     name:'coolingSetpoint',  type:'decimal', dt:'virtual', rw: 'rw', min:5.0, max:45.0, defVal:20.0, step:0.5, scale:1,  unit:'°C',  title: '<b>Current Cooling Setpoint</b>',      description:'Current cooling setpoint'],
                [at:'thermostatOperatingState',  name:'thermostatOperatingState', type:'enum', dt:'virtual', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heating'], unit:'',  description:'termostatRunningState (relay on/off status)'],   // read only!
                [at:'simulateThermostatOperatingState',  name:'simulateThermostatOperatingState', type:'enum',    dt: 'virtual', rw: 'rw', min:0,    max:1,   defVal:'0',  step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'',         title: '<b>Simulate Thermostat Operating State</b>',      \
                             description:'Simulate the thermostat operating state<br>* idle - when the temperature is less than the heatingSetpoint<br>* heat - when the temperature is above tha heatingSetpoint'],

            ],
            refresh: ['pollTuya', 'pollTuya'],
            supportedThermostatModes: ['auto', 'heat', 'emergency heat', 'eco', 'off', 'cool'],
            deviceJoinName: 'Virtual thermostat',
            configuration : [:]
    ],

    // TODO = check constants! https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/lib/constants.ts#L17
    'UNKNOWN'   : [
            description   : 'GENERIC TRV',
            device        : [type: 'TRV', powerSource: 'battery', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [:],
            fingerprints  : [],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences',
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

// TODO - use for all events sent by this driver !!
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
void sendThermostatEvent(final String eventName, final value, final raw, final boolean isDigital = false) {
    final String descriptionText = "${eventName} is ${value}"
    Map eventMap = [name: eventName, value: value, descriptionText: descriptionText, type: isDigital ? 'digital' : 'physical']
    if (state.states['isRefresh'] == true) {
        eventMap.descriptionText += ' [refresh]'
        eventMap.isStateChange = true   // force event to be sent
    }
    if (logEnable) { eventMap.descriptionText += " (raw ${raw})" }
    sendEvent(eventMap)
    logInfo "${eventMap.descriptionText}"
}

void sendEventMap(final Map event, final boolean isDigital = false) {
    if (event.descriptionText == null) {
        event.descriptionText = "${event.name} is ${event.value} ${event.unit ?: ''}"
    }
    if (state.states['isRefresh'] == true) {
        event.descriptionText += ' [refresh]'
        event.isStateChange = true   // force event to be sent
    }
    event.type = event.type != null ? event.type : isDigital == true ? 'digital' : 'physical'
    if (event.type == 'digital') {
        event.isStateChange = true   // force event to be sent
        event.descriptionText += ' [digital]'
    }
    sendEvent(event)
    logInfo "${event.descriptionText}"
}

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

void customParseFC11Cluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseFC11Cluster: zigbee received Thermostat 0xFC11 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logWarn "customParseFC11Cluster: received unknown Thermostat cluster (0xFC11) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

//  setHeatingSetpoint thermostat capability standard command
//  1°C steps. (0.5°C setting on the TRV itself, rounded for zigbee interface)
//
void setHeatingSetpoint(final Number temperaturePar ) {
    BigDecimal temperature = temperaturePar.toBigDecimal()
    logTrace "setHeatingSetpoint(${temperature}) called!"
    BigDecimal previousSetpoint = (device.currentState('heatingSetpoint')?.value ?: 0.0G).toBigDecimal()
    BigDecimal tempDouble = temperature
    //logDebug "setHeatingSetpoint temperature = ${temperature}  as int = ${temperature as int} (previousSetpointt = ${previousSetpoint})"
    /* groovylint-disable-next-line ConstantIfExpression */
    if (true) {
        //logDebug "0.5 C correction of the heating setpoint${temperature}"
        //log.trace "tempDouble = ${tempDouble}"
        tempDouble = (tempDouble * 2).setScale(0, RoundingMode.HALF_UP) / 2
    }
    else {
        if (temperature != (temperature as int)) {
            if ((temperature as double) > (previousSetpoint as double)) {
                temperature = (temperature + 0.5 ) as int
            }
            else {
                temperature = temperature as int
            }
            logDebug "corrected heating setpoint ${temperature}"
        }
        tempDouble = temperature
    }
    BigDecimal maxTemp = settings?.maxHeatingSetpoint ? new BigDecimal(settings.maxHeatingSetpoint) : new BigDecimal(50)
    BigDecimal minTemp = settings?.minHeatingSetpoint ? new BigDecimal(settings.minHeatingSetpoint) : new BigDecimal(5)
    tempBigDecimal = new BigDecimal(tempDouble)
    tempBigDecimal = tempDouble.min(maxTemp).max(minTemp).setScale(1, BigDecimal.ROUND_HALF_UP)

    logDebug "calling sendAttribute heatingSetpoint ${tempBigDecimal}"
    sendAttribute('heatingSetpoint', tempBigDecimal as double)
}

void sendHeatingSetpointEvent(Number temperature) {
    tempDouble = safeToDouble(temperature)
    Map eventMap = [name: 'heatingSetpoint',  value: tempDouble, unit: '\u00B0C', type: 'physical']
    eventMap.descriptionText = "heatingSetpoint is ${tempDouble}"
    if (state.states['isRefresh'] == true) {
        eventMap.descriptionText += ' [refresh]'
        eventMap.isStateChange = true   // force event to be sent
    }
    sendEvent(eventMap)
    if (eventMap.descriptionText != null) { logInfo "${eventMap.descriptionText}" }

    eventMap.name = 'thermostatSetpoint'
    logDebug "sending event ${eventMap}"
    sendEvent(eventMap)
    updateDataValue('lastRunningMode', 'heat')
}

// thermostat capability standard command
// do nothing in TRV - just send an event
void setCoolingSetpoint(Number temperaturePar) {
    logDebug "setCoolingSetpoint(${temperaturePar}) called!"
    /* groovylint-disable-next-line ParameterReassignment */
    BigDecimal temperature = Math.round(temperaturePar * 2) / 2
    String descText = "coolingSetpoint is set to ${temperature} \u00B0C"
    sendEvent(name: 'coolingSetpoint', value: temperature, unit: '\u00B0C', descriptionText: descText, type: 'digital')
    logInfo "${descText}"
}

/**
 * Sets the thermostat mode based on the requested mode.
 *
 * if the requestedMode is supported directly in the thermostatMode attribute, it is set directly.
 * Otherwise, the thermostatMode is substituted with another command, if supported by the device.
 *
 * @param requestedMode The mode to set the thermostat to.
 */
void setThermostatMode(final String requestedMode) {
    String mode = requestedMode
    boolean result = false
    List nativelySupportedModesList = getAttributesMap('thermostatMode')?.map?.values() as List ?: []
    List systemModesList = getAttributesMap('systemMode')?.map?.values() as List ?: []
    List ecoModesList = getAttributesMap('ecoMode')?.map?.values() as List ?: []
    List emergencyHeatingModesList = getAttributesMap('emergencyHeating')?.map?.values() as List ?: []

    logDebug "setThermostatMode: sending setThermostatMode(${mode}). Natively supported: ${nativelySupportedModesList}"

    // some TRVs require some checks and additional commands to be sent before setting the mode
    final String currentMode = device.currentValue('thermostatMode')
    logDebug "setThermostatMode: currentMode = ${currentMode}, switching to ${mode} ..."

    switch (mode) {
        case 'heat':
        case 'auto':
            if (device.currentValue('ecoMode') == 'on') {
                logInfo 'setThermostatMode: pre-processing: switching first the eco mode off'
                sendAttribute('ecoMode', 0)
            }
            if (device.currentValue('emergencyHeating') == 'on') {
                logInfo 'setThermostatMode: pre-processing: switching first the emergencyHeating mode off'
                sendAttribute('emergencyHeating', 0)
            }
            if ((device.currentValue('systemMode') ?: 'off') == 'off') {
                logInfo 'setThermostatMode: pre-processing: switching first the systemMode on'
                sendAttribute('systemMode', 'on')
            }
            break
        case 'cool':        // TODO !!!!!!!!!!
            if (!('cool' in DEVICE.supportedThermostatModes)) {
                // replace cool with 'eco' mode, if supported by the device
                if ('eco' in DEVICE.supportedThermostatModes) {
                    logInfo 'setThermostatMode: pre-processing: switching to eco mode instead'
                    mode = 'eco'
                    break
                }
                else if ('off' in DEVICE.supportedThermostatModes) {
                    logInfo 'setThermostatMode: pre-processing: switching to off mode instead'
                    mode = 'off'
                    break
                }
                else if (device.currentValue('ecoMode') != null) {
                    // BRT-100 has a dediceted 'ecoMode' command   // TODO - check how to switch BRT-100 low temp protection mode (5 degrees) ?
                    logInfo "setThermostatMode: pre-processing: setting eco mode on (${settings.ecoTemp} &degC)"
                    sendAttribute('ecoMode', 1)
                }
                else {
                    logWarn "setThermostatMode: pre-processing: switching to 'cool' mode is not supported by this device!"
                    return
                }
            }
            break
        case 'emergency heat':     // TODO for Aqara and Sonoff TRVs
            if ('emergency heat' in nativelySupportedModesList) {
                break
            }
            // look for a dedicated 'emergencyMode' deviceProfile attribute       (BRT-100)
            if ('on' in emergencyHeatingModesList)  {
                logInfo "setThermostatMode: pre-processing: switching the emergencyMode mode on for (${settings.emergencyHeatingTime} seconds )"
                sendAttribute('emergencyHeating', 'on')
                return
            }
            break
        case 'eco':
            if (device.currentValue('ecoMode') != null)  {
                logDebug 'setThermostatMode: pre-processing: switching the eco mode on'
                sendAttribute('ecoMode', 1)
                return
            }
            break
        case 'off':     // OK!
            if ('off' in nativelySupportedModesList) {
                break
            }
            logDebug "setThermostatMode: pre-processing: switching to 'off' mode"
            // if the 'off' mode is not directly supported, try substituting it with 'eco' mode
            if ('eco' in nativelySupportedModesList) {
                logInfo 'setThermostatMode: pre-processing: switching to eco mode instead'
                mode = 'eco'
                break
            }
            // look for a dedicated 'ecoMode' deviceProfile attribute       (BRT-100)
            if ('on' in ecoModesList)  {
                logInfo 'setThermostatMode: pre-processing: switching the eco mode on'
                sendAttribute('ecoMode', 'on')
                return
            }
            // look for a dedicated 'systemMode' attribute with map 'off' (Aqara E1)
            if ('off' in systemModesList)  {
                logInfo 'setThermostatMode: pre-processing: switching the systemMode off'
                sendAttribute('systemMode', 'off')
                return
            }
            break
        default:
            logWarn "setThermostatMode: pre-processing: unknown mode ${mode}"
            break
    }

    // try using the standard thermostat capability to switch to the selected new mode
    result = sendAttribute('thermostatMode', mode)
    logTrace "setThermostatMode: sendAttribute returned ${result}"
    if (result == true) { return }

    // post-process mode switching for some TRVs
    switch (mode) {
        case 'cool' :
        case 'heat' :
        case 'auto' :
        case 'off' :
            logTrace "setThermostatMode: post-processing: no post-processing required for mode ${mode}"
            break
        case 'emergency heat' :
            logInfo "setThermostatMode: post-processing: setting emergency heat mode on (${settings.emergencyHeatingTime} minutes)"
            sendAttribute('emergencyHeating', 1)
            break
            /*
        case 'eco' :
            logDebug "setThermostatMode: post-processing: switching the eco mode on"
            sendAttribute("ecoMode", 1)
            break
            */
        default :
            logWarn "setThermostatMode: post-processing: unsupported thermostat mode '${mode}'"
            break
    }
    return
}

void customOff() { setThermostatMode('off') }    // invoked from the common library
void customOn()  { setThermostatMode('heat') }   // invoked from the common library

void heat() { setThermostatMode('heat') }
void auto() { setThermostatMode('auto') }
void cool() { setThermostatMode('cool') }
void emergencyHeat() { setThermostatMode('emergency heat') }

void setThermostatFanMode(final String fanMode) { sendEvent(name: 'thermostatFanMode', value: "${fanMode}", descriptionText: getDescriptionText("thermostatFanMode is ${fanMode}")) }
void fanAuto() { setThermostatFanMode('auto') }
void fanCirculate() { setThermostatFanMode('circulate') }
void fanOn() { setThermostatFanMode('on') }

void sendSupportedThermostatModes() {
    List<String> supportedThermostatModes = []
    supportedThermostatModes = ['off', 'heat', 'auto', 'emergency heat']
    if (DEVICE.supportedThermostatModes != null) {
        supportedThermostatModes = DEVICE.supportedThermostatModes
    }
    else {
        logWarn 'sendSupportedThermostatModes: DEVICE.supportedThermostatModes is not set!'
        supportedThermostatModes =  ['off', 'auto', 'heat']
    }
    logInfo "supportedThermostatModes: ${supportedThermostatModes}"
    sendEvent(name: 'supportedThermostatModes', value:  JsonOutput.toJson(supportedThermostatModes), isStateChange: true)
    sendEvent(name: 'supportedThermostatFanModes', value: JsonOutput.toJson(['auto', 'circulate', 'on']), isStateChange: true)
}

/**
 * Schedule thermostat polling
 * @param intervalMins interval in seconds
 */
private void scheduleThermostatPolling(final int intervalSecs) {
    String cron = getCron( intervalSecs )
    logDebug "cron = ${cron}"
    schedule(cron, 'autoPollThermostat')
}

private void unScheduleThermostatPolling() {
    unschedule('autoPollThermostat')
}

int getElapsedTimeFromEventInSeconds(final String eventName) {
    /* groovylint-disable-next-line NoJavaUtilDate */
    final Long now = new Date().time
    final Object lastEventState = device.currentState(eventName)
    logDebug "getElapsedTimeFromEventInSeconds: eventName = ${eventName} lastEventState = ${lastEventState}"
    if (lastEventState == null) {
        logTrace 'getElapsedTimeFromEventInSeconds: lastEventState is null, returning 0'
        return 0
    }
    Long lastEventStateTime = lastEventState.date.time
    //def lastEventStateValue = lastEventState.value
    int diff = ((now - lastEventStateTime) / 1000) as int
    // convert diff to minutes and seconds
    logTrace "getElapsedTimeFromEventInSeconds: lastEventStateTime = ${lastEventStateTime} diff = ${diff} seconds"
    return diff
}

void sendDigitalEventIfNeeded(final String eventName) {
    final Object lastEventState = device.currentState(eventName)
    final int diff = getElapsedTimeFromEventInSeconds(eventName)
    final String diffStr = timeToHMS(diff)
    if (diff >= (settings.temperaturePollingInterval as int)) {
        logDebug "pollTuya: ${eventName} was sent more than ${settings.temperaturePollingInterval} seconds ago (${diffStr}), sending digital event"
        sendEventMap([name: lastEventState.name, value: lastEventState.value, unit: lastEventState.unit, type: 'digital'])
    }
    else {
        logDebug "pollTuya: ${eventName} was sent less than ${settings.temperaturePollingInterval} seconds ago, skipping"
    }
}

List pollTuya() {
    logDebug 'pollTuya() called!'
    // check if the device is online
    if (device.currentState('healthStatus')?.value != 'online') {
        logWarn 'pollTuya: device is offline, skipping pollTuya()'
        return []
    }
    sendDigitalEventIfNeeded('temperature')
    sendDigitalEventIfNeeded('heatingSetpoint')
    sendDigitalEventIfNeeded('level')
    ping()
    return []
}

// TODO - configure in the deviceProfile
List pollThermostatCluster() {
    return  zigbee.readAttribute(0x0201, [0x0000, 0x0012, 0x001B, 0x001C, 0x0029], [:], delay = 3500)      // 0x0000 = local temperature, 0x0012 = heating setpoint, 0x001B = controlledSequenceOfOperation, 0x001C = system mode (enum8 )
}

// TODO - configure in the deviceProfile
List pollAqara() {
    return  zigbee.readAttribute(0x0201, [0x0000, 0x0012, 0x001B, 0x001C], [:], delay = 3500)      // 0x0000 = local temperature, 0x0012 = heating setpoint, 0x001B = controlledSequenceOfOperation, 0x001C = system mode (enum8 )
}

// TODO - configure in the deviceProfile
List pollBatteryPercentage() {
    return zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)                          // battery percentage
}

/**
 * Scheduled job for polling device specific attribute(s)
 */
void autoPollThermostat() {
    logDebug 'autoPollThermostat()...'
    checkDriverVersion(state)
    List<String> cmds = []
    cmds = refreshFromDeviceProfileList()
    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
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

List<String> initializeSonoff()
{
    List<String> cmds = []
        //cmds = ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 200",]
        //cmds =   ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 86 12 52 52 00 41 2c 52 00 00} {0x0000}", "delay 200",]

        cmds += zigbee.readAttribute(0x0000, [0x0004, 0x0005, 0x4000], [:], delay = 2711)

        cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0020 {${device.zigbeeId}} {}", 'delay 612', ]     // Poll Control Cluster    112
        cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}", 'delay 613', ]     // Power Configuration Cluster     113
        cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0001 0x0021 0x20 3600 7200 {02}", 'delay 314', ]          // battery reporting   114
        cmds += zigbee.readAttribute(0x0019, 0x0002, [:], delay = 315)                                                 // current file version    115
        cmds += zigbee.writeAttribute(0xFC11, 0x6008, 0x20, 0x7F, [:], delay = 116)                                     // unknown 1  116
        cmds += zigbee.writeAttribute(0xFC11, 0x6008, 0x20, 0x7F, [:], delay = 317)                                     // unknown 1  117
        cmds += zigbee.writeAttribute(0xFC11, 0x6008, 0x20, 0x7F, [:], delay = 118)                                     // unknown 1``  118
        logDebug 'configuring cluster 0x0201 ...'
        cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x00 0x0201 {${device.zigbeeId}} {}", 'delay 619', ]     // Thermostat Cluster  119 // TODO : check source EP - 0 or 1 ?
        cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0000 0x29 1800 3540 {0x32}", 'delay 600', ]        // local temperature   120
        cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 1210)                                                 // battery 121
        cmds += zigbee.command(0xEF00, 0x03, '00')        //, "00 00", "00")                               // sequence 123

        cmds += zigbee.writeAttribute(0xFC11, 0x0000, 0x10, 0x01, [:], delay = 140)                                    // 140
        cmds += zigbee.writeAttribute(0xFC11, 0x0000, 0x10, 0x00, [:], delay = 141)                                    // 141
        cmds += zigbee.writeAttribute(0xFC11, 0x6000, 0x10, 0x01, [:], delay = 142)                                    // 142
        cmds += zigbee.writeAttribute(0xFC11, 0x6000, 0x10, 0x00, [:], delay = 143)                                    // 143
        cmds += zigbee.writeAttribute(0xFC11, 0x6002, 0x29, 0750, [:], delay = 144)                                    // 144
        cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x01, [:], delay = 145)                                    // 145

    /*
        cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0012 0x29 1 600 {}", "delay 252", ]   // heating setpoint
        cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x001C 0x30 1 600 {}", "delay 253", ]   // thermostat mode
    */

        cmds +=  zigbee.reportingConfiguration(0x0201, 0x0000, [:], 552)    // read it back - doesn't work
        cmds +=  zigbee.reportingConfiguration(0x0201, 0x0012, [:], 551)    // read it back - doesn't work
        cmds +=  zigbee.reportingConfiguration(0x0201, 0x001C, [:], 553)    // read it back - doesn't work

        cmds += zigbee.readAttribute(0x0201, 0x0010, [:], delay = 254)      // calibration
        cmds += zigbee.readAttribute(0xFC11, [0x0000, 0x6000, 0x6002], [:], delay = 255)

    /*
        configure: async (device, coordinatorEndpoint, logger) => {
                const endpoint = device.getEndpoint(1);
                await reporting.bind(endpoint, coordinatorEndpoint, ['hvacThermostat']);    x 250
                await reporting.thermostatTemperature(endpoint);                            x 251
                await reporting.thermostatOccupiedHeatingSetpoint(endpoint);                x 252
                await reporting.thermostatSystemMode(endpoint);                             x 253
                await endpoint.read('hvacThermostat', ['localTemperatureCalibration']);     x 254
                await endpoint.read(0xFC11, [0x0000, 0x6000, 0x6002]);
            },
    */
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
    //int intMinTime = 300
    //int intMaxTime = 600    // report temperature every 10 minutes !
    logDebug 'customInitializeDevice() ...'

    if ( getDeviceProfile() == 'SONOFF_TRV') {
        cmds += initializeSonoff()
    }
    else if (getDeviceProfile() == 'AQARA_E1_TRV' ) {
        cmds += initializeAqara()
    }
    else {
        logDebug"customInitializeDevice: nothing to initialize for device group ${getDeviceProfile()}"
    }
    logDebug "initializeThermostat() : ${cmds}"
    return cmds
}

void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (state.deviceProfile == null) {
        setDeviceNameAndProfile()               // in deviceProfileiLib.groovy
    }
    if (fullInit == true || state.lastThermostatMode == null) { state.lastThermostatMode = 'unknown' }
    if (fullInit == true || state.lastThermostatOperatingState == null) { state.lastThermostatOperatingState = 'unknown' }
    if (fullInit || settings?.temperaturePollingInterval == null) { device.updateSetting('temperaturePollingInterval', [value: TrvTemperaturePollingIntervalOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
}

// called from initializeVars() in the main code ...
void customInitEvents(final boolean fullInit=false) {
    if (fullInit == true) {
        String descText = 'inital attribute setting'
        sendSupportedThermostatModes()
        sendEvent(name: 'thermostatMode', value: 'heat', isStateChange: true, description: descText)
        state.lastThermostatMode = 'heat'
        sendEvent(name: 'thermostatFanMode', value: 'auto', isStateChange: true, description: descText)
        state.lastThermostatOperatingState = 'idle'
        sendEvent(name: 'thermostatOperatingState', value: 'idle', isStateChange: true, description: descText)
        sendEvent(name: 'thermostatSetpoint', value:  12.3, unit: '\u00B0C', isStateChange: true, description: descText)        // Google Home compatibility
        sendEvent(name: 'heatingSetpoint', value: 12.3, unit: '\u00B0C', isStateChange: true, description: descText)
        sendEvent(name: 'coolingSetpoint', value: 34.5, unit: '\u00B0C', isStateChange: true, description: descText)
        sendEvent(name: 'temperature', value: 23.4, unit: '\u00B0', isStateChange: true, description: descText)
        updateDataValue('lastRunningMode', 'heat')
    }
    else {
        logDebug "customInitEvents: fullInit = ${fullInit}"
    }
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

private String getDescriptionText(final String msg) {
    String descriptionText = "${device.displayName} ${msg}"
    if (settings?.txtEnable) { log.info "${descriptionText}" }
    return descriptionText
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

/*
  Reset to Factory Defaults Command
  On receipt of this command, the device resets all the attributes of all its clusters to their factory defaults.
  Note that networking functionality, bindings, groups, or other persistent data are not affected by this command
*/
void factoryResetThermostat() {
    logDebug 'factoryResetThermostat() called!'
    //List<String> cmds = []
    // TODO
    logWarn 'factoryResetThermostat: NOT IMPLEMENTED'
}

// ========================================= Virtual thermostat functions  =========================================

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
void setTemperature(temperature) {
    logDebug "setTemperature(${temperature}) called!"
    if (isVirtual()) {
        /* groovylint-disable-next-line ParameterReassignment */
        temperature = Math.round(temperature * 2) / 2
        String descText = "temperature is set to ${temperature} \u00B0C"
        sendEvent(name: 'temperature', value: temperature, unit: '\u00B0C', descriptionText: descText, type: 'digital')
        logInfo "${descText}"
    }
    else {
        logWarn 'setTemperature: not a virtual thermostat!'
    }
}

// ========================================= end of the Virtual thermostat functions  =========================================

void testT(String par) {
    log.trace "testT(${par}) : DEVICE.preferences = ${DEVICE.preferences}"
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
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', documentationLink: '', // library marker kkossev.commonLib, line 4
    version: '3.2.1' // library marker kkossev.commonLib, line 5
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
  * ver. 3.2.2  2024-06-05 kkossev  - (dev. branch) removed isAqaraTRV_OLD() and isAqaraTVOC_OLD() dependencies from the lib; added timeToHMS(); // library marker kkossev.commonLib, line 38
  * // library marker kkossev.commonLib, line 39
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 40
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 41
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 42
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 43
  * // library marker kkossev.commonLib, line 44
*/ // library marker kkossev.commonLib, line 45

String commonLibVersion() { '3.2.2' } // library marker kkossev.commonLib, line 47
String commonLibStamp() { '2024/06/05 4:57 PM' } // library marker kkossev.commonLib, line 48

import groovy.transform.Field // library marker kkossev.commonLib, line 50
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 51
import hubitat.device.Protocol // library marker kkossev.commonLib, line 52
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 53
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 54
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 55
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 56
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 57
import java.math.BigDecimal // library marker kkossev.commonLib, line 58

metadata { // library marker kkossev.commonLib, line 60
        if (_DEBUG) { // library marker kkossev.commonLib, line 61
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 62
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 63
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 64
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 65
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 66
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 67
            ] // library marker kkossev.commonLib, line 68
        } // library marker kkossev.commonLib, line 69

        // common capabilities for all device types // library marker kkossev.commonLib, line 71
        capability 'Configuration' // library marker kkossev.commonLib, line 72
        capability 'Refresh' // library marker kkossev.commonLib, line 73
        capability 'Health Check' // library marker kkossev.commonLib, line 74

        // common attributes for all device types // library marker kkossev.commonLib, line 76
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 77
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 78
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 79

        // common commands for all device types // library marker kkossev.commonLib, line 81
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 82

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 84
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 85

    preferences { // library marker kkossev.commonLib, line 87
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 88
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 89
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 90

        if (device) { // library marker kkossev.commonLib, line 92
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device...', defaultValue: false // library marker kkossev.commonLib, line 93
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 94
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 95
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 96
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 97
            } // library marker kkossev.commonLib, line 98
        } // library marker kkossev.commonLib, line 99
    } // library marker kkossev.commonLib, line 100
} // library marker kkossev.commonLib, line 101

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 103
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 104
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 105
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 106
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 107
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 108
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 109
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 110
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 111
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 112
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 113

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 115
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 116
] // library marker kkossev.commonLib, line 117
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 118
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 119
] // library marker kkossev.commonLib, line 120

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 122
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 123
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 124
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 125
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 126
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 127
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 128
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 129
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 130
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 131
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 132
] // library marker kkossev.commonLib, line 133

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 135
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 136
//boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 137
//boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 138
//boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 139
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 140

/** // library marker kkossev.commonLib, line 142
 * Parse Zigbee message // library marker kkossev.commonLib, line 143
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 144
 */ // library marker kkossev.commonLib, line 145
void parse(final String description) { // library marker kkossev.commonLib, line 146
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 147
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 148
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 149
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 150

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 152
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 153
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 154
            parseIasMessage(description) // library marker kkossev.commonLib, line 155
        } // library marker kkossev.commonLib, line 156
        else { // library marker kkossev.commonLib, line 157
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 158
        } // library marker kkossev.commonLib, line 159
        return // library marker kkossev.commonLib, line 160
    } // library marker kkossev.commonLib, line 161
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 162
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 163
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 164
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 165
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 166
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 167
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 168
        return // library marker kkossev.commonLib, line 169
    } // library marker kkossev.commonLib, line 170

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 172
        return // library marker kkossev.commonLib, line 173
    } // library marker kkossev.commonLib, line 174
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 175

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 177
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 178

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 180
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 181
        return // library marker kkossev.commonLib, line 182
    } // library marker kkossev.commonLib, line 183
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 184
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 185
        return // library marker kkossev.commonLib, line 186
    } // library marker kkossev.commonLib, line 187
    // // library marker kkossev.commonLib, line 188
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 189
    // // library marker kkossev.commonLib, line 190
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 191
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 192
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 193
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 194
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 195
            } // library marker kkossev.commonLib, line 196
            break // library marker kkossev.commonLib, line 197
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 198
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 199
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 200
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 201
            } // library marker kkossev.commonLib, line 202
            break // library marker kkossev.commonLib, line 203
        default: // library marker kkossev.commonLib, line 204
            if (settings.logEnable) { // library marker kkossev.commonLib, line 205
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 206
            } // library marker kkossev.commonLib, line 207
            break // library marker kkossev.commonLib, line 208
    } // library marker kkossev.commonLib, line 209
} // library marker kkossev.commonLib, line 210

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 212
    0x0000: 'Basic',                0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x000C: 'AnalogInput', // library marker kkossev.commonLib, line 213
    0x0006: 'OnOff',                0x0008: 'LevelControl',     0x0012: 'MultistateInput',  0x0102: 'WindowCovering',   0x0201: 'Thermostat',   /*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 214
    0x0400: 'Illuminance',          0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'ElectricalMeasure', // library marker kkossev.commonLib, line 215
    0x0B04: 'Metering',             0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',             0xFC11: 'FC11',         0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 216
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 217
] // library marker kkossev.commonLib, line 218

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 220
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 221
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 222
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 223
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 224
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 225
        return false // library marker kkossev.commonLib, line 226
    } // library marker kkossev.commonLib, line 227
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 228
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 229
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 230
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 231
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 232
        return true // library marker kkossev.commonLib, line 233
    } // library marker kkossev.commonLib, line 234
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 235
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 236
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 237
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 238
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 239
        return true // library marker kkossev.commonLib, line 240
    } // library marker kkossev.commonLib, line 241
    if (device?.getDataValue('model') != 'ZigUSB' && description.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 242
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 243
    } // library marker kkossev.commonLib, line 244
    return false // library marker kkossev.commonLib, line 245
} // library marker kkossev.commonLib, line 246

static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 248
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 249
} // library marker kkossev.commonLib, line 250

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 252
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 253
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 254
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 255
    } // library marker kkossev.commonLib, line 256
    return false // library marker kkossev.commonLib, line 257
} // library marker kkossev.commonLib, line 258

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 260
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 261
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 262
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 263
    } // library marker kkossev.commonLib, line 264
    return false // library marker kkossev.commonLib, line 265
} // library marker kkossev.commonLib, line 266

boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 268
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 269
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 270
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 271
    } // library marker kkossev.commonLib, line 272
    return false // library marker kkossev.commonLib, line 273
} // library marker kkossev.commonLib, line 274

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 276
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 277
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 278
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 279
] // library marker kkossev.commonLib, line 280

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 282
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 283
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 284
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 285
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 286
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 287
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 288
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 289
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 290
    List<String> cmds = [] // library marker kkossev.commonLib, line 291
    switch (clusterId) { // library marker kkossev.commonLib, line 292
        case 0x0005 : // library marker kkossev.commonLib, line 293
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 294
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 295
            // send the active endpoint response // library marker kkossev.commonLib, line 296
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 297
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 298
            break // library marker kkossev.commonLib, line 299
        case 0x0006 : // library marker kkossev.commonLib, line 300
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 301
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 302
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 303
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 304
            break // library marker kkossev.commonLib, line 305
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 306
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 307
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 308
            break // library marker kkossev.commonLib, line 309
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 310
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 311
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 312
            break // library marker kkossev.commonLib, line 313
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 314
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 315
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 316
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 317
            break // library marker kkossev.commonLib, line 318
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 319
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 320
            break // library marker kkossev.commonLib, line 321
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 322
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 323
            if (settings?.logEnable) { log.debug "${clusterInfo}" } // library marker kkossev.commonLib, line 324
            break // library marker kkossev.commonLib, line 325
        default : // library marker kkossev.commonLib, line 326
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 327
            break // library marker kkossev.commonLib, line 328
    } // library marker kkossev.commonLib, line 329
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 330
} // library marker kkossev.commonLib, line 331

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 333
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 334
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 335
    switch (commandId) { // library marker kkossev.commonLib, line 336
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 337
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 338
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 339
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 340
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 341
        default: // library marker kkossev.commonLib, line 342
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 343
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 344
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 345
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 346
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 347
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 348
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 349
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 350
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 351
            } // library marker kkossev.commonLib, line 352
            break // library marker kkossev.commonLib, line 353
    } // library marker kkossev.commonLib, line 354
} // library marker kkossev.commonLib, line 355

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 357
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 358
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 359
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 360
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 361
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 362
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 363
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 364
    } // library marker kkossev.commonLib, line 365
    else { // library marker kkossev.commonLib, line 366
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 367
    } // library marker kkossev.commonLib, line 368
} // library marker kkossev.commonLib, line 369

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 371
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 372
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 373
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 374
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 375
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 376
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 377
    } // library marker kkossev.commonLib, line 378
    else { // library marker kkossev.commonLib, line 379
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 380
    } // library marker kkossev.commonLib, line 381
} // library marker kkossev.commonLib, line 382

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 384
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 385
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 386
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 387
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 388
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 389
        state.reportingEnabled = true // library marker kkossev.commonLib, line 390
    } // library marker kkossev.commonLib, line 391
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 392
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 393
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 394
    } else { // library marker kkossev.commonLib, line 395
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 396
    } // library marker kkossev.commonLib, line 397
} // library marker kkossev.commonLib, line 398

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 400
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 401
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 402
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 403
    if (status == 0) { // library marker kkossev.commonLib, line 404
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 405
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 406
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 407
        int delta = 0 // library marker kkossev.commonLib, line 408
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 409
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 410
        } // library marker kkossev.commonLib, line 411
        else { // library marker kkossev.commonLib, line 412
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 413
        } // library marker kkossev.commonLib, line 414
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 415
    } // library marker kkossev.commonLib, line 416
    else { // library marker kkossev.commonLib, line 417
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 418
    } // library marker kkossev.commonLib, line 419
} // library marker kkossev.commonLib, line 420

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 422
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 423
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 424
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 425
        return false // library marker kkossev.commonLib, line 426
    } // library marker kkossev.commonLib, line 427
    // execute the customHandler function // library marker kkossev.commonLib, line 428
    boolean result = false // library marker kkossev.commonLib, line 429
    try { // library marker kkossev.commonLib, line 430
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 431
    } // library marker kkossev.commonLib, line 432
    catch (e) { // library marker kkossev.commonLib, line 433
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 434
        return false // library marker kkossev.commonLib, line 435
    } // library marker kkossev.commonLib, line 436
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 437
    return result // library marker kkossev.commonLib, line 438
} // library marker kkossev.commonLib, line 439

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 441
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 442
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 443
    final String commandId = data[0] // library marker kkossev.commonLib, line 444
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 445
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 446
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 447
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 448
    } else { // library marker kkossev.commonLib, line 449
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 450
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 451
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 452
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 453
        } // library marker kkossev.commonLib, line 454
    } // library marker kkossev.commonLib, line 455
} // library marker kkossev.commonLib, line 456

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 458
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 459
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 460
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 461

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 463
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 464
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 465
] // library marker kkossev.commonLib, line 466

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 468
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 469
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 470
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 471
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 472
] // library marker kkossev.commonLib, line 473

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 475
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 476
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 477
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 478
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 479
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 480
    return avg // library marker kkossev.commonLib, line 481
} // library marker kkossev.commonLib, line 482

/* // library marker kkossev.commonLib, line 484
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 485
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 486
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 487
*/ // library marker kkossev.commonLib, line 488
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 489

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 491
void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 492
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 493
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 494
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 495
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 496
        case 0x0000: // library marker kkossev.commonLib, line 497
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 498
            break // library marker kkossev.commonLib, line 499
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 500
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 501
            if (isPing) { // library marker kkossev.commonLib, line 502
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 503
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 504
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 505
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 506
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 507
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 508
                    sendRttEvent() // library marker kkossev.commonLib, line 509
                } // library marker kkossev.commonLib, line 510
                else { // library marker kkossev.commonLib, line 511
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 512
                } // library marker kkossev.commonLib, line 513
                state.states['isPing'] = false // library marker kkossev.commonLib, line 514
            } // library marker kkossev.commonLib, line 515
            else { // library marker kkossev.commonLib, line 516
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 517
            } // library marker kkossev.commonLib, line 518
            break // library marker kkossev.commonLib, line 519
        case 0x0004: // library marker kkossev.commonLib, line 520
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 521
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 522
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 523
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 524
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 525
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 526
            } // library marker kkossev.commonLib, line 527
            break // library marker kkossev.commonLib, line 528
        case 0x0005: // library marker kkossev.commonLib, line 529
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 530
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 531
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 532
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 533
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 534
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 535
            } // library marker kkossev.commonLib, line 536
            break // library marker kkossev.commonLib, line 537
        case 0x0007: // library marker kkossev.commonLib, line 538
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 539
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 540
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 541
            break // library marker kkossev.commonLib, line 542
        case 0xFFDF: // library marker kkossev.commonLib, line 543
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 544
            break // library marker kkossev.commonLib, line 545
        case 0xFFE2: // library marker kkossev.commonLib, line 546
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 547
            break // library marker kkossev.commonLib, line 548
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 549
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 550
            break // library marker kkossev.commonLib, line 551
        case 0xFFFE: // library marker kkossev.commonLib, line 552
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 553
            break // library marker kkossev.commonLib, line 554
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 555
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 556
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 557
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 558
            break // library marker kkossev.commonLib, line 559
        default: // library marker kkossev.commonLib, line 560
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 561
            break // library marker kkossev.commonLib, line 562
    } // library marker kkossev.commonLib, line 563
} // library marker kkossev.commonLib, line 564

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 566
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 567
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 568

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 570
    Map descMap = [:] // library marker kkossev.commonLib, line 571
    try { // library marker kkossev.commonLib, line 572
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 573
    } // library marker kkossev.commonLib, line 574
    catch (e1) { // library marker kkossev.commonLib, line 575
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 576
        // try alternative custom parsing // library marker kkossev.commonLib, line 577
        descMap = [:] // library marker kkossev.commonLib, line 578
        try { // library marker kkossev.commonLib, line 579
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 580
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 581
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 582
            } // library marker kkossev.commonLib, line 583
        } // library marker kkossev.commonLib, line 584
        catch (e2) { // library marker kkossev.commonLib, line 585
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 586
            return [:] // library marker kkossev.commonLib, line 587
        } // library marker kkossev.commonLib, line 588
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 589
    } // library marker kkossev.commonLib, line 590
    return descMap // library marker kkossev.commonLib, line 591
} // library marker kkossev.commonLib, line 592

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 594
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 595
        return false // library marker kkossev.commonLib, line 596
    } // library marker kkossev.commonLib, line 597
    // try to parse ... // library marker kkossev.commonLib, line 598
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 599
    Map descMap = [:] // library marker kkossev.commonLib, line 600
    try { // library marker kkossev.commonLib, line 601
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 602
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 603
    } // library marker kkossev.commonLib, line 604
    catch (e) { // library marker kkossev.commonLib, line 605
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 606
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 607
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 608
        return true // library marker kkossev.commonLib, line 609
    } // library marker kkossev.commonLib, line 610

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 612
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 613
    } // library marker kkossev.commonLib, line 614
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 615
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 616
    } // library marker kkossev.commonLib, line 617
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 618
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 619
    } // library marker kkossev.commonLib, line 620
    else { // library marker kkossev.commonLib, line 621
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 622
        return false // library marker kkossev.commonLib, line 623
    } // library marker kkossev.commonLib, line 624
    return true    // processed // library marker kkossev.commonLib, line 625
} // library marker kkossev.commonLib, line 626

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 628
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 629
  /* // library marker kkossev.commonLib, line 630
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 631
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 632
        return true // library marker kkossev.commonLib, line 633
    } // library marker kkossev.commonLib, line 634
*/ // library marker kkossev.commonLib, line 635
    Map descMap = [:] // library marker kkossev.commonLib, line 636
    try { // library marker kkossev.commonLib, line 637
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 638
    } // library marker kkossev.commonLib, line 639
    catch (e1) { // library marker kkossev.commonLib, line 640
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 641
        // try alternative custom parsing // library marker kkossev.commonLib, line 642
        descMap = [:] // library marker kkossev.commonLib, line 643
        try { // library marker kkossev.commonLib, line 644
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 645
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 646
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 647
            } // library marker kkossev.commonLib, line 648
        } // library marker kkossev.commonLib, line 649
        catch (e2) { // library marker kkossev.commonLib, line 650
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 651
            return true // library marker kkossev.commonLib, line 652
        } // library marker kkossev.commonLib, line 653
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 654
    } // library marker kkossev.commonLib, line 655
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 656
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 657
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 658
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 659
        return false // library marker kkossev.commonLib, line 660
    } // library marker kkossev.commonLib, line 661
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 662
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 663
    // attribute report received // library marker kkossev.commonLib, line 664
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 665
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 666
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 667
    } // library marker kkossev.commonLib, line 668
    attrData.each { // library marker kkossev.commonLib, line 669
        if (it.status == '86') { // library marker kkossev.commonLib, line 670
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 671
        // TODO - skip parsing? // library marker kkossev.commonLib, line 672
        } // library marker kkossev.commonLib, line 673
        switch (it.cluster) { // library marker kkossev.commonLib, line 674
            case '0000' : // library marker kkossev.commonLib, line 675
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 676
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 677
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 678
                } // library marker kkossev.commonLib, line 679
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 680
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 681
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 682
                } // library marker kkossev.commonLib, line 683
                else { // library marker kkossev.commonLib, line 684
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 685
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 686
                } // library marker kkossev.commonLib, line 687
                break // library marker kkossev.commonLib, line 688
            default : // library marker kkossev.commonLib, line 689
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 690
                break // library marker kkossev.commonLib, line 691
        } // switch // library marker kkossev.commonLib, line 692
    } // for each attribute // library marker kkossev.commonLib, line 693
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 694
} // library marker kkossev.commonLib, line 695

String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 697
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 698
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 699
} // library marker kkossev.commonLib, line 700

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 702
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 703
} // library marker kkossev.commonLib, line 704

/* // library marker kkossev.commonLib, line 706
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 707
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 708
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 709
*/ // library marker kkossev.commonLib, line 710
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 711
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 712
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 713

// Tuya Commands // library marker kkossev.commonLib, line 715
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 716
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 717
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 718
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 719
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 720

// tuya DP type // library marker kkossev.commonLib, line 722
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 723
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 724
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 725
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 726
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 727
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 728

void syncTuyaDateTime() { // library marker kkossev.commonLib, line 730
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 731
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 732
    long offset = 0 // library marker kkossev.commonLib, line 733
    int offsetHours = 0 // library marker kkossev.commonLib, line 734
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 735
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 736
    try { // library marker kkossev.commonLib, line 737
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 738
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 739
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 740
    } catch (e) { // library marker kkossev.commonLib, line 741
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 742
    } // library marker kkossev.commonLib, line 743
    // // library marker kkossev.commonLib, line 744
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 745
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 746
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 747
} // library marker kkossev.commonLib, line 748

// called from the main parse method when the cluster is 0xEF00 // library marker kkossev.commonLib, line 750
void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 751
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 752
        syncTuyaDateTime() // library marker kkossev.commonLib, line 753
    } // library marker kkossev.commonLib, line 754
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 755
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 756
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 757
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 758
        if (status != '00') { // library marker kkossev.commonLib, line 759
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 760
        } // library marker kkossev.commonLib, line 761
    } // library marker kkossev.commonLib, line 762
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 763
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 764
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 765
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 766
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 767
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 768
            return // library marker kkossev.commonLib, line 769
        } // library marker kkossev.commonLib, line 770
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 771
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 772
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 773
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 774
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 775
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 776
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 777
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 778
            } // library marker kkossev.commonLib, line 779
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 780
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 781
        } // library marker kkossev.commonLib, line 782
    } // library marker kkossev.commonLib, line 783
    else { // library marker kkossev.commonLib, line 784
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 785
    } // library marker kkossev.commonLib, line 786
} // library marker kkossev.commonLib, line 787

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 789
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 790
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 791
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 792
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 793
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 794
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 795
        } // library marker kkossev.commonLib, line 796
    } // library marker kkossev.commonLib, line 797
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 798
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 799
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 800
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 801
        } // library marker kkossev.commonLib, line 802
    } // library marker kkossev.commonLib, line 803
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 804
} // library marker kkossev.commonLib, line 805

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 807
    int retValue = 0 // library marker kkossev.commonLib, line 808
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 809
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 810
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 811
        int power = 1 // library marker kkossev.commonLib, line 812
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 813
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 814
            power = power * 256 // library marker kkossev.commonLib, line 815
        } // library marker kkossev.commonLib, line 816
    } // library marker kkossev.commonLib, line 817
    return retValue // library marker kkossev.commonLib, line 818
} // library marker kkossev.commonLib, line 819

private List<String> getTuyaCommand(String dp, String dp_type, String fncmd) { return sendTuyaCommand(dp, dp_type, fncmd) } // library marker kkossev.commonLib, line 821

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 823
    List<String> cmds = [] // library marker kkossev.commonLib, line 824
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 825
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 826
    //int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 827
    int tuyaCmd = isFingerbot() ? 0x04 : tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 828
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 829
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 830
    return cmds // library marker kkossev.commonLib, line 831
} // library marker kkossev.commonLib, line 832

private getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 834

void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 836
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 837
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 838
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 839
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 840
} // library marker kkossev.commonLib, line 841

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 843
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 844

List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 846
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 847
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 848
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 849
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 850
} // library marker kkossev.commonLib, line 851

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 853
    List<String> cmds = [] // library marker kkossev.commonLib, line 854
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 855
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 856
    } // library marker kkossev.commonLib, line 857
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 858
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 859
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 860
        return // library marker kkossev.commonLib, line 861
    } // library marker kkossev.commonLib, line 862
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 863
} // library marker kkossev.commonLib, line 864

// Invoked from configure() // library marker kkossev.commonLib, line 866
List<String> initializeDevice() { // library marker kkossev.commonLib, line 867
    List<String> cmds = [] // library marker kkossev.commonLib, line 868
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 869
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 870
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 871
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 872
    } // library marker kkossev.commonLib, line 873
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 874
    return cmds // library marker kkossev.commonLib, line 875
} // library marker kkossev.commonLib, line 876

// Invoked from configure() // library marker kkossev.commonLib, line 878
List<String> configureDevice() { // library marker kkossev.commonLib, line 879
    List<String> cmds = [] // library marker kkossev.commonLib, line 880
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 881
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 882
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 883
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 884
    } // library marker kkossev.commonLib, line 885
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 886
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 887
    return cmds // library marker kkossev.commonLib, line 888
} // library marker kkossev.commonLib, line 889

/* // library marker kkossev.commonLib, line 891
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 892
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 893
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 894
*/ // library marker kkossev.commonLib, line 895

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 897
    List<String> cmds = [] // library marker kkossev.commonLib, line 898
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 899
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 900
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 901
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 902
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 903
            } // library marker kkossev.commonLib, line 904
        } // library marker kkossev.commonLib, line 905
    } // library marker kkossev.commonLib, line 906
    return cmds // library marker kkossev.commonLib, line 907
} // library marker kkossev.commonLib, line 908

void refresh() { // library marker kkossev.commonLib, line 910
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 911
    checkDriverVersion(state) // library marker kkossev.commonLib, line 912
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 913
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 914
        customCmds = customRefresh() // library marker kkossev.commonLib, line 915
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 916
    } // library marker kkossev.commonLib, line 917
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 918
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 919
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 920
    } // library marker kkossev.commonLib, line 921
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 922
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 923
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 924
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 925
    } // library marker kkossev.commonLib, line 926
    else { // library marker kkossev.commonLib, line 927
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 928
    } // library marker kkossev.commonLib, line 929
} // library marker kkossev.commonLib, line 930

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 932
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 933
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 934

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 936
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 937
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 938
        sendEvent(name: 'Status', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 939
    } // library marker kkossev.commonLib, line 940
    else { // library marker kkossev.commonLib, line 941
        logInfo "${info}" // library marker kkossev.commonLib, line 942
        sendEvent(name: 'Status', value: info, type: 'digital') // library marker kkossev.commonLib, line 943
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 944
    } // library marker kkossev.commonLib, line 945
} // library marker kkossev.commonLib, line 946

public void ping() { // library marker kkossev.commonLib, line 948
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 949
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 950
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 951
    if (isVirtual()) { runInMillis(10, virtualPong) } // library marker kkossev.commonLib, line 952
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) } // library marker kkossev.commonLib, line 953
    logDebug 'ping...' // library marker kkossev.commonLib, line 954
} // library marker kkossev.commonLib, line 955

def virtualPong() { // library marker kkossev.commonLib, line 957
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 958
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 959
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 960
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 961
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 962
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 963
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 964
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 965
        sendRttEvent() // library marker kkossev.commonLib, line 966
    } // library marker kkossev.commonLib, line 967
    else { // library marker kkossev.commonLib, line 968
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 969
    } // library marker kkossev.commonLib, line 970
    state.states['isPing'] = false // library marker kkossev.commonLib, line 971
    //unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 972
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 973
} // library marker kkossev.commonLib, line 974

void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 976
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 977
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 978
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 979
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 980
    if (value == null) { // library marker kkossev.commonLib, line 981
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 982
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 983
    } // library marker kkossev.commonLib, line 984
    else { // library marker kkossev.commonLib, line 985
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 986
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 987
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 988
    } // library marker kkossev.commonLib, line 989
} // library marker kkossev.commonLib, line 990

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 992
    if (cluster != null) { // library marker kkossev.commonLib, line 993
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 994
    } // library marker kkossev.commonLib, line 995
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 996
    return 'NULL' // library marker kkossev.commonLib, line 997
} // library marker kkossev.commonLib, line 998

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1000
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1001
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1002
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1003
} // library marker kkossev.commonLib, line 1004

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1006
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1007
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1008
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1009
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1010
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1011
    } // library marker kkossev.commonLib, line 1012
} // library marker kkossev.commonLib, line 1013

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1015
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1016
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1017
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1018
} // library marker kkossev.commonLib, line 1019

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1021
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1022
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1023
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1024
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1025
    } // library marker kkossev.commonLib, line 1026
    else { // library marker kkossev.commonLib, line 1027
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1028
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1029
    } // library marker kkossev.commonLib, line 1030
} // library marker kkossev.commonLib, line 1031

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1033
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1034
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1035
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1036
} // library marker kkossev.commonLib, line 1037

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1039
void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1040
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1041
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1042
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1043
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1044
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1045
    } // library marker kkossev.commonLib, line 1046
} // library marker kkossev.commonLib, line 1047

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1049
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1050
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1051
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1052
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1053
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1054
            logWarn 'not present!' // library marker kkossev.commonLib, line 1055
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1056
        } // library marker kkossev.commonLib, line 1057
    } // library marker kkossev.commonLib, line 1058
    else { // library marker kkossev.commonLib, line 1059
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1060
    } // library marker kkossev.commonLib, line 1061
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1062
} // library marker kkossev.commonLib, line 1063

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1065
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1066
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1067
    if (value == 'online') { // library marker kkossev.commonLib, line 1068
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1069
    } // library marker kkossev.commonLib, line 1070
    else { // library marker kkossev.commonLib, line 1071
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1072
    } // library marker kkossev.commonLib, line 1073
} // library marker kkossev.commonLib, line 1074

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1076
void updated() { // library marker kkossev.commonLib, line 1077
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1078
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1079
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1080
    unschedule() // library marker kkossev.commonLib, line 1081

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1083
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1084
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1085
    } // library marker kkossev.commonLib, line 1086
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1087
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1088
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1089
    } // library marker kkossev.commonLib, line 1090

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1092
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1093
        // schedule the periodic timer // library marker kkossev.commonLib, line 1094
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1095
        if (interval > 0) { // library marker kkossev.commonLib, line 1096
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1097
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1098
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1099
        } // library marker kkossev.commonLib, line 1100
    } // library marker kkossev.commonLib, line 1101
    else { // library marker kkossev.commonLib, line 1102
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1103
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1104
    } // library marker kkossev.commonLib, line 1105
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1106
        customUpdated() // library marker kkossev.commonLib, line 1107
    } // library marker kkossev.commonLib, line 1108

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1110
} // library marker kkossev.commonLib, line 1111

void logsOff() { // library marker kkossev.commonLib, line 1113
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1114
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1115
} // library marker kkossev.commonLib, line 1116
void traceOff() { // library marker kkossev.commonLib, line 1117
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1118
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1119
} // library marker kkossev.commonLib, line 1120

void configure(String command) { // library marker kkossev.commonLib, line 1122
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1123
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1124
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1125
        return // library marker kkossev.commonLib, line 1126
    } // library marker kkossev.commonLib, line 1127
    // // library marker kkossev.commonLib, line 1128
    String func // library marker kkossev.commonLib, line 1129
    try { // library marker kkossev.commonLib, line 1130
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1131
        "$func"() // library marker kkossev.commonLib, line 1132
    } // library marker kkossev.commonLib, line 1133
    catch (e) { // library marker kkossev.commonLib, line 1134
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1135
        return // library marker kkossev.commonLib, line 1136
    } // library marker kkossev.commonLib, line 1137
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1138
} // library marker kkossev.commonLib, line 1139

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1141
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1142
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1143
} // library marker kkossev.commonLib, line 1144

void loadAllDefaults() { // library marker kkossev.commonLib, line 1146
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1147
    deleteAllSettings() // library marker kkossev.commonLib, line 1148
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1149
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1150
    deleteAllStates() // library marker kkossev.commonLib, line 1151
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1152

    initialize() // library marker kkossev.commonLib, line 1154
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1155
    updated() // library marker kkossev.commonLib, line 1156
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1157
} // library marker kkossev.commonLib, line 1158

void configureNow() { // library marker kkossev.commonLib, line 1160
    configure() // library marker kkossev.commonLib, line 1161
} // library marker kkossev.commonLib, line 1162

/** // library marker kkossev.commonLib, line 1164
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1165
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1166
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1167
 */ // library marker kkossev.commonLib, line 1168
void configure() { // library marker kkossev.commonLib, line 1169
    List<String> cmds = [] // library marker kkossev.commonLib, line 1170
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1171
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1172
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1173
    if (isTuya()) { // library marker kkossev.commonLib, line 1174
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1175
    } // library marker kkossev.commonLib, line 1176
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1177
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1178
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1179
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1180
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1181
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1182
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1183
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1184
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1185
    } // library marker kkossev.commonLib, line 1186
    else { // library marker kkossev.commonLib, line 1187
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1188
    } // library marker kkossev.commonLib, line 1189
} // library marker kkossev.commonLib, line 1190

 // Invoked when the device is installed or when driver is installed ? // library marker kkossev.commonLib, line 1192
void installed() { // library marker kkossev.commonLib, line 1193
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1194
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1195
    // populate some default values for attributes // library marker kkossev.commonLib, line 1196
    sendEvent(name: 'healthStatus', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1197
    sendEvent(name: 'powerSource',  value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1198
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1199
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1200
} // library marker kkossev.commonLib, line 1201

 // Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1203
void initialize() { // library marker kkossev.commonLib, line 1204
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1205
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1206
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1207
    updateTuyaVersion() // library marker kkossev.commonLib, line 1208
    updateAqaraVersion() // library marker kkossev.commonLib, line 1209
} // library marker kkossev.commonLib, line 1210

/* // library marker kkossev.commonLib, line 1212
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1213
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1214
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1215
*/ // library marker kkossev.commonLib, line 1216

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1218
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1219
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1220
} // library marker kkossev.commonLib, line 1221

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1223
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1224
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1225
} // library marker kkossev.commonLib, line 1226

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1228
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1229
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1230
} // library marker kkossev.commonLib, line 1231

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1233
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1234
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1235
        return // library marker kkossev.commonLib, line 1236
    } // library marker kkossev.commonLib, line 1237
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1238
    cmd.each { // library marker kkossev.commonLib, line 1239
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1240
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1241
            return // library marker kkossev.commonLib, line 1242
        } // library marker kkossev.commonLib, line 1243
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1244
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1245
    } // library marker kkossev.commonLib, line 1246
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1247
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1248
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1249
} // library marker kkossev.commonLib, line 1250

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1252

String getDeviceInfo() { // library marker kkossev.commonLib, line 1254
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1255
} // library marker kkossev.commonLib, line 1256

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1258
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1259
} // library marker kkossev.commonLib, line 1260

@CompileStatic // library marker kkossev.commonLib, line 1262
void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1263
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1264
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1265
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1266
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1267
        initializeVars(false) // library marker kkossev.commonLib, line 1268
        updateTuyaVersion() // library marker kkossev.commonLib, line 1269
        updateAqaraVersion() // library marker kkossev.commonLib, line 1270
    } // library marker kkossev.commonLib, line 1271
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1272
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1273
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1274
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1275
} // library marker kkossev.commonLib, line 1276

// credits @thebearmay // library marker kkossev.commonLib, line 1278
String getModel() { // library marker kkossev.commonLib, line 1279
    try { // library marker kkossev.commonLib, line 1280
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1281
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1282
    } catch (ignore) { // library marker kkossev.commonLib, line 1283
        try { // library marker kkossev.commonLib, line 1284
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1285
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1286
                return model // library marker kkossev.commonLib, line 1287
            } // library marker kkossev.commonLib, line 1288
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1289
            return '' // library marker kkossev.commonLib, line 1290
        } // library marker kkossev.commonLib, line 1291
    } // library marker kkossev.commonLib, line 1292
} // library marker kkossev.commonLib, line 1293

// credits @thebearmay // library marker kkossev.commonLib, line 1295
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1296
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1297
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1298
    String revision = tokens.last() // library marker kkossev.commonLib, line 1299
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1300
} // library marker kkossev.commonLib, line 1301

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1303
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1304
    unschedule() // library marker kkossev.commonLib, line 1305
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1306
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1307

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1309
} // library marker kkossev.commonLib, line 1310

void resetStatistics() { // library marker kkossev.commonLib, line 1312
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1313
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1314
} // library marker kkossev.commonLib, line 1315

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1317
void resetStats() { // library marker kkossev.commonLib, line 1318
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1319
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1320
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1321
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1322
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1323
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1324
} // library marker kkossev.commonLib, line 1325

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1327
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1328
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1329
        state.clear() // library marker kkossev.commonLib, line 1330
        unschedule() // library marker kkossev.commonLib, line 1331
        resetStats() // library marker kkossev.commonLib, line 1332
        if (deviceProfilesV3 != null && this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1333
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1334
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1335
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1336
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1337
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1338
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1339
    } // library marker kkossev.commonLib, line 1340

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1342
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1343
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1344
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1345
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1346

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1348
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1349
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1350
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1351
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1352
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1353
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1354

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1356

    // common libraries initialization // library marker kkossev.commonLib, line 1358
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1359
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1360
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1361
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1362

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1364
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1365
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1366
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1367

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1369
    if ( mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1370
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1371
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1372
    if ( ep  != null) { // library marker kkossev.commonLib, line 1373
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1374
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1375
    } // library marker kkossev.commonLib, line 1376
    else { // library marker kkossev.commonLib, line 1377
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1378
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1379
    } // library marker kkossev.commonLib, line 1380
} // library marker kkossev.commonLib, line 1381

void setDestinationEP() { // library marker kkossev.commonLib, line 1383
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1384
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1385
        state.destinationEP = ep // library marker kkossev.commonLib, line 1386
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1387
    } // library marker kkossev.commonLib, line 1388
    else { // library marker kkossev.commonLib, line 1389
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1390
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 1391
    } // library marker kkossev.commonLib, line 1392
} // library marker kkossev.commonLib, line 1393

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1395
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1396
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1397
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1398

// _DEBUG mode only // library marker kkossev.commonLib, line 1400
void getAllProperties() { // library marker kkossev.commonLib, line 1401
    log.trace 'Properties:' // library marker kkossev.commonLib, line 1402
    device.properties.each { it -> // library marker kkossev.commonLib, line 1403
        log.debug it // library marker kkossev.commonLib, line 1404
    } // library marker kkossev.commonLib, line 1405
    log.trace 'Settings:' // library marker kkossev.commonLib, line 1406
    settings.each { it -> // library marker kkossev.commonLib, line 1407
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1408
    } // library marker kkossev.commonLib, line 1409
    log.trace 'Done' // library marker kkossev.commonLib, line 1410
} // library marker kkossev.commonLib, line 1411

// delete all Preferences // library marker kkossev.commonLib, line 1413
void deleteAllSettings() { // library marker kkossev.commonLib, line 1414
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1415
    settings.each { it -> // library marker kkossev.commonLib, line 1416
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 1417
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 1418
    } // library marker kkossev.commonLib, line 1419
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1420
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1421
} // library marker kkossev.commonLib, line 1422

// delete all attributes // library marker kkossev.commonLib, line 1424
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1425
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1426
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1427
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1428
} // library marker kkossev.commonLib, line 1429

// delete all State Variables // library marker kkossev.commonLib, line 1431
void deleteAllStates() { // library marker kkossev.commonLib, line 1432
    String stateDeleted = '' // library marker kkossev.commonLib, line 1433
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1434
    state.clear() // library marker kkossev.commonLib, line 1435
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1436
} // library marker kkossev.commonLib, line 1437

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1439
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1440
} // library marker kkossev.commonLib, line 1441

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1443
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 1444
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 1445
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 1446
    } // library marker kkossev.commonLib, line 1447
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1448
} // library marker kkossev.commonLib, line 1449

void testParse(String par) { // library marker kkossev.commonLib, line 1451
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1452
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1453
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1454
    parse(par) // library marker kkossev.commonLib, line 1455
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1456
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1457
} // library marker kkossev.commonLib, line 1458

def testJob() { // library marker kkossev.commonLib, line 1460
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1461
} // library marker kkossev.commonLib, line 1462

/** // library marker kkossev.commonLib, line 1464
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1465
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1466
 */ // library marker kkossev.commonLib, line 1467
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1468
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1469
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1470
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1471
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1472
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1473
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1474
    String cron // library marker kkossev.commonLib, line 1475
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1476
    else { // library marker kkossev.commonLib, line 1477
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1478
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1479
    } // library marker kkossev.commonLib, line 1480
    return cron // library marker kkossev.commonLib, line 1481
} // library marker kkossev.commonLib, line 1482

// credits @thebearmay // library marker kkossev.commonLib, line 1484
String formatUptime() { // library marker kkossev.commonLib, line 1485
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1486
} // library marker kkossev.commonLib, line 1487

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1489
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1490
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1491
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1492
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1493
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1494
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1495
} // library marker kkossev.commonLib, line 1496

boolean isTuya() { // library marker kkossev.commonLib, line 1498
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1499
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1500
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1501
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1502
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1503
} // library marker kkossev.commonLib, line 1504

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1506
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1507
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1508
    if (application != null) { // library marker kkossev.commonLib, line 1509
        Integer ver // library marker kkossev.commonLib, line 1510
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1511
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1512
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1513
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1514
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1515
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1516
        } // library marker kkossev.commonLib, line 1517
    } // library marker kkossev.commonLib, line 1518
} // library marker kkossev.commonLib, line 1519

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1521

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1523
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1524
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1525
    if (application != null) { // library marker kkossev.commonLib, line 1526
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1527
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1528
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1529
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1530
        } // library marker kkossev.commonLib, line 1531
    } // library marker kkossev.commonLib, line 1532
} // library marker kkossev.commonLib, line 1533

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1535
    try { // library marker kkossev.commonLib, line 1536
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1537
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1538
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1539
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1540
    } catch (e) { // library marker kkossev.commonLib, line 1541
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1542
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1543
    } // library marker kkossev.commonLib, line 1544
} // library marker kkossev.commonLib, line 1545

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1547
    try { // library marker kkossev.commonLib, line 1548
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1549
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1550
        return date.getTime() // library marker kkossev.commonLib, line 1551
    } catch (e) { // library marker kkossev.commonLib, line 1552
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1553
        return now() // library marker kkossev.commonLib, line 1554
    } // library marker kkossev.commonLib, line 1555
} // library marker kkossev.commonLib, line 1556

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1558
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1559
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1560
    int seconds = time % 60 // library marker kkossev.commonLib, line 1561
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1562
} // library marker kkossev.commonLib, line 1563

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (176) kkossev.onOffLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.onOffLib, line 1
library( // library marker kkossev.onOffLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee OnOff Cluster Library', name: 'onOffLib', namespace: 'kkossev', // library marker kkossev.onOffLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/onOffLib.groovy', documentationLink: '', // library marker kkossev.onOffLib, line 4
    version: '3.2.0' // library marker kkossev.onOffLib, line 5
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
 * ver. 3.2.0  2024-06-04 kkossev  - commonLib 3.2.0 allignment; if isRefresh then sendEvent with isStateChange = true // library marker kkossev.onOffLib, line 19
 * // library marker kkossev.onOffLib, line 20
 *                                   TODO: // library marker kkossev.onOffLib, line 21
*/ // library marker kkossev.onOffLib, line 22

static String onOffLibVersion()   { '3.2.0' } // library marker kkossev.onOffLib, line 24
static String onOffLibStamp() { '2024/06/04 1:54 PM' } // library marker kkossev.onOffLib, line 25

@Field static final Boolean _THREE_STATE = true // library marker kkossev.onOffLib, line 27

metadata { // library marker kkossev.onOffLib, line 29
    capability 'Actuator' // library marker kkossev.onOffLib, line 30
    capability 'Switch' // library marker kkossev.onOffLib, line 31
    if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 32
        attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.onOffLib, line 33
    } // library marker kkossev.onOffLib, line 34
    // no commands // library marker kkossev.onOffLib, line 35
    preferences { // library marker kkossev.onOffLib, line 36
        if (settings?.advancedOptions == true && device != null && DEVICE_TYPE != 'Device') { // library marker kkossev.onOffLib, line 37
            input(name: 'ignoreDuplicated', type: 'bool', title: '<b>Ignore Duplicated Switch Events</b>', description: 'Some switches and plugs send periodically the switch status as a heart-beet ', defaultValue: true) // library marker kkossev.onOffLib, line 38
            input(name: 'alwaysOn', type: 'bool', title: '<b>Always On</b>', description: 'Disable switching OFF for plugs that must be always On', defaultValue: false) // library marker kkossev.onOffLib, line 39
            if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 40
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: 'Experimental multi-state switch events', defaultValue: false // library marker kkossev.onOffLib, line 41
            } // library marker kkossev.onOffLib, line 42
        } // library marker kkossev.onOffLib, line 43
    } // library marker kkossev.onOffLib, line 44
} // library marker kkossev.onOffLib, line 45

@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.onOffLib, line 47
    defaultValue: 0, options: [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.onOffLib, line 48
] // library marker kkossev.onOffLib, line 49

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.onOffLib, line 51
    '0': 'switch off', '1': 'switch on', '2': 'switch last state' // library marker kkossev.onOffLib, line 52
] // library marker kkossev.onOffLib, line 53

@Field static final Map switchTypeOptions = [ // library marker kkossev.onOffLib, line 55
    '0': 'toggle', '1': 'state', '2': 'momentary' // library marker kkossev.onOffLib, line 56
] // library marker kkossev.onOffLib, line 57

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.onOffLib, line 59

/* // library marker kkossev.onOffLib, line 61
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 62
 * on/off cluster            0x0006     TODO - move to a library !!!!!!!!!!!!!!! // library marker kkossev.onOffLib, line 63
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 64
*/ // library marker kkossev.onOffLib, line 65
void standardParseOnOffCluster(final Map descMap) { // library marker kkossev.onOffLib, line 66
    /* // library marker kkossev.onOffLib, line 67
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.onOffLib, line 68
        customParseOnOffCluster(descMap) // library marker kkossev.onOffLib, line 69
    } // library marker kkossev.onOffLib, line 70
    else */ // library marker kkossev.onOffLib, line 71
    if (descMap.attrId == '0000') { // library marker kkossev.onOffLib, line 72
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.onOffLib, line 73
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.onOffLib, line 74
        sendSwitchEvent(rawValue) // library marker kkossev.onOffLib, line 75
    } // library marker kkossev.onOffLib, line 76
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.onOffLib, line 77
        parseOnOffAttributes(descMap) // library marker kkossev.onOffLib, line 78
    } // library marker kkossev.onOffLib, line 79
    else { // library marker kkossev.onOffLib, line 80
        if (descMap.attrId != null) { logWarn "standardParseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.onOffLib, line 81
        else { logDebug "standardParseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.onOffLib, line 82
    } // library marker kkossev.onOffLib, line 83
} // library marker kkossev.onOffLib, line 84

void toggle() { // library marker kkossev.onOffLib, line 86
    String descriptionText = 'central button switch is ' // library marker kkossev.onOffLib, line 87
    String state = '' // library marker kkossev.onOffLib, line 88
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.onOffLib, line 89
        state = 'on' // library marker kkossev.onOffLib, line 90
    } // library marker kkossev.onOffLib, line 91
    else { // library marker kkossev.onOffLib, line 92
        state = 'off' // library marker kkossev.onOffLib, line 93
    } // library marker kkossev.onOffLib, line 94
    descriptionText += state // library marker kkossev.onOffLib, line 95
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.onOffLib, line 96
    logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 97
} // library marker kkossev.onOffLib, line 98

void off() { // library marker kkossev.onOffLib, line 100
    if (this.respondsTo('customOff')) { // library marker kkossev.onOffLib, line 101
        customOff() // library marker kkossev.onOffLib, line 102
        return // library marker kkossev.onOffLib, line 103
    } // library marker kkossev.onOffLib, line 104
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.onOffLib, line 105
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.onOffLib, line 106
        return // library marker kkossev.onOffLib, line 107
    } // library marker kkossev.onOffLib, line 108
    List<String> cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.onOffLib, line 109
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 110
    logDebug "off() currentState=${currentState}" // library marker kkossev.onOffLib, line 111
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 112
        if (currentState == 'off') { // library marker kkossev.onOffLib, line 113
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 114
        } // library marker kkossev.onOffLib, line 115
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.onOffLib, line 116
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 117
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 118
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 119
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 120
    } // library marker kkossev.onOffLib, line 121
    /* // library marker kkossev.onOffLib, line 122
    else { // library marker kkossev.onOffLib, line 123
        if (currentState != 'off') { // library marker kkossev.onOffLib, line 124
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.onOffLib, line 125
        } // library marker kkossev.onOffLib, line 126
        else { // library marker kkossev.onOffLib, line 127
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.onOffLib, line 128
            return // library marker kkossev.onOffLib, line 129
        } // library marker kkossev.onOffLib, line 130
    } // library marker kkossev.onOffLib, line 131
    */ // library marker kkossev.onOffLib, line 132

    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 134
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 135
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 136
} // library marker kkossev.onOffLib, line 137

void on() { // library marker kkossev.onOffLib, line 139
    if (this.respondsTo('customOn')) { // library marker kkossev.onOffLib, line 140
        customOn() // library marker kkossev.onOffLib, line 141
        return // library marker kkossev.onOffLib, line 142
    } // library marker kkossev.onOffLib, line 143
    List<String> cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.onOffLib, line 144
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 145
    logDebug "on() currentState=${currentState}" // library marker kkossev.onOffLib, line 146
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 147
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.onOffLib, line 148
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 149
        } // library marker kkossev.onOffLib, line 150
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.onOffLib, line 151
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 152
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 153
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 154
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 155
    } // library marker kkossev.onOffLib, line 156
    /* // library marker kkossev.onOffLib, line 157
    else { // library marker kkossev.onOffLib, line 158
        if (currentState != 'on') { // library marker kkossev.onOffLib, line 159
            logDebug "Switching ${device.displayName} On" // library marker kkossev.onOffLib, line 160
        } // library marker kkossev.onOffLib, line 161
        else { // library marker kkossev.onOffLib, line 162
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.onOffLib, line 163
            return // library marker kkossev.onOffLib, line 164
        } // library marker kkossev.onOffLib, line 165
    } // library marker kkossev.onOffLib, line 166
    */ // library marker kkossev.onOffLib, line 167
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 168
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 169
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 170
} // library marker kkossev.onOffLib, line 171

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.onOffLib, line 173
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.onOffLib, line 174
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.onOffLib, line 175
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.onOffLib, line 176
    } // library marker kkossev.onOffLib, line 177
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.onOffLib, line 178
    Map map = [:] // library marker kkossev.onOffLib, line 179
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.onOffLib, line 180
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.onOffLib, line 181
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.onOffLib, line 182
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false)) && !isRefresh) { // library marker kkossev.onOffLib, line 183
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.onOffLib, line 184
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 185
        return // library marker kkossev.onOffLib, line 186
    } // library marker kkossev.onOffLib, line 187
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.onOffLib, line 188
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.onOffLib, line 189
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.onOffLib, line 190
    if (lastSwitch != value) { // library marker kkossev.onOffLib, line 191
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.onOffLib, line 192
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 193
        state.states['lastSwitch'] = value // library marker kkossev.onOffLib, line 194
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 195
    } else { // library marker kkossev.onOffLib, line 196
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 197
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 198
    } // library marker kkossev.onOffLib, line 199
    map.name = 'switch' // library marker kkossev.onOffLib, line 200
    map.value = value // library marker kkossev.onOffLib, line 201
    if (isRefresh) { // library marker kkossev.onOffLib, line 202
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.onOffLib, line 203
        map.isStateChange = true // library marker kkossev.onOffLib, line 204
    } else { // library marker kkossev.onOffLib, line 205
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.onOffLib, line 206
    } // library marker kkossev.onOffLib, line 207
    logInfo "${map.descriptionText}" // library marker kkossev.onOffLib, line 208
    sendEvent(map) // library marker kkossev.onOffLib, line 209
    clearIsDigital() // library marker kkossev.onOffLib, line 210
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.onOffLib, line 211
        customSwitchEventPostProcesing(map) // library marker kkossev.onOffLib, line 212
    } // library marker kkossev.onOffLib, line 213
} // library marker kkossev.onOffLib, line 214

void parseOnOffAttributes(final Map it) { // library marker kkossev.onOffLib, line 216
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 217
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.onOffLib, line 218
    String mode // library marker kkossev.onOffLib, line 219
    String attrName // library marker kkossev.onOffLib, line 220
    if (it.value == null) { // library marker kkossev.onOffLib, line 221
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.onOffLib, line 222
        return // library marker kkossev.onOffLib, line 223
    } // library marker kkossev.onOffLib, line 224
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.onOffLib, line 225
    switch (it.attrId) { // library marker kkossev.onOffLib, line 226
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.onOffLib, line 227
            attrName = 'Global Scene Control' // library marker kkossev.onOffLib, line 228
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.onOffLib, line 229
            break // library marker kkossev.onOffLib, line 230
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.onOffLib, line 231
            attrName = 'On Time' // library marker kkossev.onOffLib, line 232
            mode = value // library marker kkossev.onOffLib, line 233
            break // library marker kkossev.onOffLib, line 234
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.onOffLib, line 235
            attrName = 'Off Wait Time' // library marker kkossev.onOffLib, line 236
            mode = value // library marker kkossev.onOffLib, line 237
            break // library marker kkossev.onOffLib, line 238
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.onOffLib, line 239
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 240
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.onOffLib, line 241
            break // library marker kkossev.onOffLib, line 242
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.onOffLib, line 243
            attrName = 'Child Lock' // library marker kkossev.onOffLib, line 244
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.onOffLib, line 245
            break // library marker kkossev.onOffLib, line 246
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.onOffLib, line 247
            attrName = 'LED mode' // library marker kkossev.onOffLib, line 248
            if (isCircuitBreaker()) { // library marker kkossev.onOffLib, line 249
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.onOffLib, line 250
            } // library marker kkossev.onOffLib, line 251
            else { // library marker kkossev.onOffLib, line 252
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.onOffLib, line 253
            } // library marker kkossev.onOffLib, line 254
            break // library marker kkossev.onOffLib, line 255
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.onOffLib, line 256
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 257
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.onOffLib, line 258
            break // library marker kkossev.onOffLib, line 259
        case '8003' : //  Over current alarm // library marker kkossev.onOffLib, line 260
            attrName = 'Over current alarm' // library marker kkossev.onOffLib, line 261
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.onOffLib, line 262
            break // library marker kkossev.onOffLib, line 263
        default : // library marker kkossev.onOffLib, line 264
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 265
            return // library marker kkossev.onOffLib, line 266
    } // library marker kkossev.onOffLib, line 267
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.onOffLib, line 268
} // library marker kkossev.onOffLib, line 269

List<String> onOffRefresh() { // library marker kkossev.onOffLib, line 271
    logDebug 'onOffRefresh()' // library marker kkossev.onOffLib, line 272
    List<String> cmds = zigbee.readAttribute(0x0006, 0x0000, [:], delay = 100) // library marker kkossev.onOffLib, line 273
    return cmds // library marker kkossev.onOffLib, line 274
} // library marker kkossev.onOffLib, line 275

void onOfInitializeVars( boolean fullInit = false ) { // library marker kkossev.onOffLib, line 277
    logDebug "onOfInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.onOffLib, line 278
    if (fullInit || settings?.ignoreDuplicated == null) { device.updateSetting('ignoreDuplicated', true) } // library marker kkossev.onOffLib, line 279
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.onOffLib, line 280
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.onOffLib, line 281
} // library marker kkossev.onOffLib, line 282

// ~~~~~ end include (176) kkossev.onOffLib ~~~~~

// ~~~~~ start include (171) kkossev.batteryLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoJavaUtilDate, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
library( // library marker kkossev.batteryLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Battery Library', name: 'batteryLib', namespace: 'kkossev', // library marker kkossev.batteryLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/batteryLib.groovy', documentationLink: '', // library marker kkossev.batteryLib, line 4
    version: '3.2.0' // library marker kkossev.batteryLib, line 5
) // library marker kkossev.batteryLib, line 6
/* // library marker kkossev.batteryLib, line 7
 *  Zigbee Level Library // library marker kkossev.batteryLib, line 8
 * // library marker kkossev.batteryLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.batteryLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.batteryLib, line 11
 * // library marker kkossev.batteryLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.batteryLib, line 13
 * // library marker kkossev.batteryLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.batteryLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.batteryLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.batteryLib, line 17
 * // library marker kkossev.batteryLib, line 18
 * ver. 3.0.0  2024-04-06 kkossev  - added batteryLib.groovy // library marker kkossev.batteryLib, line 19
 * ver. 3.0.1  2024-04-06 kkossev  - customParsePowerCluster bug fix // library marker kkossev.batteryLib, line 20
 * ver. 3.0.2  2024-04-14 kkossev  - batteryPercentage bug fix (was x2); added bVoltCtr; added battertRefresh // library marker kkossev.batteryLib, line 21
 * ver. 3.2.0  2024-04-14 kkossev  - (dev. branch) commonLib 3.2.0 allignment; added lastBattery // library marker kkossev.batteryLib, line 22
 * // library marker kkossev.batteryLib, line 23
 *                                   TODO: // library marker kkossev.batteryLib, line 24
 *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.batteryLib, line 25
*/ // library marker kkossev.batteryLib, line 26

static String batteryLibVersion()   { '3.2.0' } // library marker kkossev.batteryLib, line 28
static String batteryLibStamp() { '2024/05/21 5:57 PM' } // library marker kkossev.batteryLib, line 29

metadata { // library marker kkossev.batteryLib, line 31
    capability 'Battery' // library marker kkossev.batteryLib, line 32
    attribute  'batteryVoltage', 'number' // library marker kkossev.batteryLib, line 33
    attribute  'lastBattery', 'date'         // last battery event time - added in 3.2.0 05/21/2024 // library marker kkossev.batteryLib, line 34
    // no commands // library marker kkossev.batteryLib, line 35
    preferences { // library marker kkossev.batteryLib, line 36
        if (device && advancedOptions == true) { // library marker kkossev.batteryLib, line 37
            input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: 'Convert battery voltage to battery Percentage remaining.' // library marker kkossev.batteryLib, line 38
        } // library marker kkossev.batteryLib, line 39
    } // library marker kkossev.batteryLib, line 40
} // library marker kkossev.batteryLib, line 41

void standardParsePowerCluster(final Map descMap) { // library marker kkossev.batteryLib, line 43
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.batteryLib, line 44
    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.batteryLib, line 45
    if (descMap.attrId == '0020') { // battery voltage // library marker kkossev.batteryLib, line 46
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 47
        state.stats['bVoltCtr'] = (state.stats['bVoltCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 48
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.batteryLib, line 49
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.batteryLib, line 50
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.batteryLib, line 51
        } // library marker kkossev.batteryLib, line 52
    } // library marker kkossev.batteryLib, line 53
    else if (descMap.attrId == '0021') { // battery percentage // library marker kkossev.batteryLib, line 54
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 55
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 56
        if (isTuya()) { // library marker kkossev.batteryLib, line 57
            sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 58
        } // library marker kkossev.batteryLib, line 59
        else { // library marker kkossev.batteryLib, line 60
            sendBatteryPercentageEvent((rawValue / 2) as int) // library marker kkossev.batteryLib, line 61
        } // library marker kkossev.batteryLib, line 62
    } // library marker kkossev.batteryLib, line 63
    else { // library marker kkossev.batteryLib, line 64
        logWarn "customParsePowerCluster: zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.batteryLib, line 65
    } // library marker kkossev.batteryLib, line 66
} // library marker kkossev.batteryLib, line 67

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.batteryLib, line 69
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.batteryLib, line 70
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 71
    Map result = [:] // library marker kkossev.batteryLib, line 72
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.batteryLib, line 73
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.batteryLib, line 74
        BigDecimal minVolts = 2.2 // library marker kkossev.batteryLib, line 75
        BigDecimal maxVolts = 3.2 // library marker kkossev.batteryLib, line 76
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.batteryLib, line 77
        int roundedPct = Math.round(pct * 100) // library marker kkossev.batteryLib, line 78
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.batteryLib, line 79
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.batteryLib, line 80
        if (convertToPercent == true) { // library marker kkossev.batteryLib, line 81
            result.value = Math.min(100, roundedPct) // library marker kkossev.batteryLib, line 82
            result.name = 'battery' // library marker kkossev.batteryLib, line 83
            result.unit  = '%' // library marker kkossev.batteryLib, line 84
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.batteryLib, line 85
        } // library marker kkossev.batteryLib, line 86
        else { // library marker kkossev.batteryLib, line 87
            result.value = volts // library marker kkossev.batteryLib, line 88
            result.name = 'batteryVoltage' // library marker kkossev.batteryLib, line 89
            result.unit  = 'V' // library marker kkossev.batteryLib, line 90
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.batteryLib, line 91
        } // library marker kkossev.batteryLib, line 92
        result.type = 'physical' // library marker kkossev.batteryLib, line 93
        result.isStateChange = true // library marker kkossev.batteryLib, line 94
        logInfo "${result.descriptionText}" // library marker kkossev.batteryLib, line 95
        sendEvent(result) // library marker kkossev.batteryLib, line 96
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 97
    } // library marker kkossev.batteryLib, line 98
    else { // library marker kkossev.batteryLib, line 99
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.batteryLib, line 100
    } // library marker kkossev.batteryLib, line 101
} // library marker kkossev.batteryLib, line 102

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.batteryLib, line 104
    if ((batteryPercent as int) == 255) { // library marker kkossev.batteryLib, line 105
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.batteryLib, line 106
        return // library marker kkossev.batteryLib, line 107
    } // library marker kkossev.batteryLib, line 108
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 109
    Map map = [:] // library marker kkossev.batteryLib, line 110
    map.name = 'battery' // library marker kkossev.batteryLib, line 111
    map.timeStamp = now() // library marker kkossev.batteryLib, line 112
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.batteryLib, line 113
    map.unit  = '%' // library marker kkossev.batteryLib, line 114
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.batteryLib, line 115
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.batteryLib, line 116
    map.isStateChange = true // library marker kkossev.batteryLib, line 117
    // // library marker kkossev.batteryLib, line 118
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.batteryLib, line 119
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.batteryLib, line 120
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.batteryLib, line 121
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.batteryLib, line 122
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.batteryLib, line 123
        // send it now! // library marker kkossev.batteryLib, line 124
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.batteryLib, line 125
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 126
    } // library marker kkossev.batteryLib, line 127
    else { // library marker kkossev.batteryLib, line 128
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.batteryLib, line 129
        map.delayed = delayedTime // library marker kkossev.batteryLib, line 130
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.batteryLib, line 131
        map.lastBattery = lastBattery // library marker kkossev.batteryLib, line 132
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.batteryLib, line 133
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.batteryLib, line 134
    } // library marker kkossev.batteryLib, line 135
} // library marker kkossev.batteryLib, line 136

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.batteryLib, line 138
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 139
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 140
    sendEvent(map) // library marker kkossev.batteryLib, line 141
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 142
} // library marker kkossev.batteryLib, line 143

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.batteryLib, line 145
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.batteryLib, line 146
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 147
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 148
    sendEvent(map) // library marker kkossev.batteryLib, line 149
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 150
} // library marker kkossev.batteryLib, line 151

List<String> batteryRefresh() { // library marker kkossev.batteryLib, line 153
    List<String> cmds = [] // library marker kkossev.batteryLib, line 154
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 100)         // battery voltage // library marker kkossev.batteryLib, line 155
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 100)         // battery percentage // library marker kkossev.batteryLib, line 156
    return cmds // library marker kkossev.batteryLib, line 157
} // library marker kkossev.batteryLib, line 158

// ~~~~~ end include (171) kkossev.batteryLib ~~~~~

// ~~~~~ start include (172) kkossev.temperatureLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.temperatureLib, line 1
library( // library marker kkossev.temperatureLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Temperature Library', name: 'temperatureLib', namespace: 'kkossev', // library marker kkossev.temperatureLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/temperatureLib.groovy', documentationLink: '', // library marker kkossev.temperatureLib, line 4
    version: '3.2.0' // library marker kkossev.temperatureLib, line 5
) // library marker kkossev.temperatureLib, line 6
/* // library marker kkossev.temperatureLib, line 7
 *  Zigbee Temperature Library // library marker kkossev.temperatureLib, line 8
 * // library marker kkossev.temperatureLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.temperatureLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.temperatureLib, line 11
 * // library marker kkossev.temperatureLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.temperatureLib, line 13
 * // library marker kkossev.temperatureLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.temperatureLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.temperatureLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.temperatureLib, line 17
 * // library marker kkossev.temperatureLib, line 18
 * ver. 3.0.0  2024-04-06 kkossev  - added temperatureLib.groovy // library marker kkossev.temperatureLib, line 19
 * ver. 3.0.1  2024-04-19 kkossev  - temperature rounding fix // library marker kkossev.temperatureLib, line 20
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0 allignment; added temperatureRefresh() // library marker kkossev.temperatureLib, line 21
 * // library marker kkossev.temperatureLib, line 22
 *                                   TODO: check why  if (settings?.minReportingTime...) condition in the preferences ? // library marker kkossev.temperatureLib, line 23
 *                                   TODO: add temperatureOffset // library marker kkossev.temperatureLib, line 24
 *                                   TODO: unschedule('sendDelayedTempEvent') only if needed (add boolean flag to sendDelayedTempEvent()) // library marker kkossev.temperatureLib, line 25
 *                                   TODO: check for negative temperature values in standardParseTemperatureCluster() // library marker kkossev.temperatureLib, line 26
*/ // library marker kkossev.temperatureLib, line 27

static String temperatureLibVersion()   { '3.2.0' } // library marker kkossev.temperatureLib, line 29
static String temperatureLibStamp() { '2024/05/29 9:06 PM' } // library marker kkossev.temperatureLib, line 30

metadata { // library marker kkossev.temperatureLib, line 32
    capability 'TemperatureMeasurement' // library marker kkossev.temperatureLib, line 33
    // no commands // library marker kkossev.temperatureLib, line 34
    preferences { // library marker kkossev.temperatureLib, line 35
        if (device) { // library marker kkossev.temperatureLib, line 36
            input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: 'Minimum reporting interval, seconds <i>(1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 37
            if (deviceType != 'mmWaveSensor') { // library marker kkossev.temperatureLib, line 38
                input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: 'Maximum reporting interval, seconds <i>(120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.temperatureLib, line 39
           } // library marker kkossev.temperatureLib, line 40
        } // library marker kkossev.temperatureLib, line 41
    } // library marker kkossev.temperatureLib, line 42
} // library marker kkossev.temperatureLib, line 43

void standardParseTemperatureCluster(final Map descMap) { // library marker kkossev.temperatureLib, line 45
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.temperatureLib, line 46
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.temperatureLib, line 47
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.temperatureLib, line 48
} // library marker kkossev.temperatureLib, line 49

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.temperatureLib, line 51
    Map eventMap = [:] // library marker kkossev.temperatureLib, line 52
    BigDecimal temperature = safeToBigDecimal(temperaturePar).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 53
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.temperatureLib, line 54
    eventMap.name = 'temperature' // library marker kkossev.temperatureLib, line 55
    if (location.temperatureScale == 'F') { // library marker kkossev.temperatureLib, line 56
        temperature = ((temperature * 1.8) + 32).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 57
        eventMap.unit = '\u00B0F' // library marker kkossev.temperatureLib, line 58
    } // library marker kkossev.temperatureLib, line 59
    else { // library marker kkossev.temperatureLib, line 60
        eventMap.unit = '\u00B0C' // library marker kkossev.temperatureLib, line 61
    } // library marker kkossev.temperatureLib, line 62
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 63
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 64
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.temperatureLib, line 65
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.temperatureLib, line 66
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.temperatureLib, line 67
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.temperatureLib, line 68
        return // library marker kkossev.temperatureLib, line 69
    } // library marker kkossev.temperatureLib, line 70
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.temperatureLib, line 71
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.temperatureLib, line 72
    if (state.states['isRefresh'] == true) { // library marker kkossev.temperatureLib, line 73
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.temperatureLib, line 74
        eventMap.isStateChange = true // library marker kkossev.temperatureLib, line 75
    } // library marker kkossev.temperatureLib, line 76
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.temperatureLib, line 77
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 78
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.temperatureLib, line 79
    if (timeElapsed >= minTime) { // library marker kkossev.temperatureLib, line 80
        logInfo "${eventMap.descriptionText}" // library marker kkossev.temperatureLib, line 81
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.temperatureLib, line 82
        state.lastRx['tempTime'] = now() // library marker kkossev.temperatureLib, line 83
        sendEvent(eventMap) // library marker kkossev.temperatureLib, line 84
    } // library marker kkossev.temperatureLib, line 85
    else {         // queue the event // library marker kkossev.temperatureLib, line 86
        eventMap.type = 'delayed' // library marker kkossev.temperatureLib, line 87
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.temperatureLib, line 88
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.temperatureLib, line 89
    } // library marker kkossev.temperatureLib, line 90
} // library marker kkossev.temperatureLib, line 91

void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.temperatureLib, line 93
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.temperatureLib, line 94
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.temperatureLib, line 95
    sendEvent(eventMap) // library marker kkossev.temperatureLib, line 96
} // library marker kkossev.temperatureLib, line 97

List<String> temperatureLibInitializeDevice() { // library marker kkossev.temperatureLib, line 99
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 100
    cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.temperatureLib, line 101
    return cmds // library marker kkossev.temperatureLib, line 102
} // library marker kkossev.temperatureLib, line 103

List<String> temperatureRefresh() { // library marker kkossev.temperatureLib, line 105
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 106
    cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.temperatureLib, line 107
    return cmds // library marker kkossev.temperatureLib, line 108
} // library marker kkossev.temperatureLib, line 109

// ~~~~~ end include (172) kkossev.temperatureLib ~~~~~

// ~~~~~ start include (165) kkossev.xiaomiLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitReturnStatement, LineLength, PublicMethodsBeforeNonPublicMethods, UnnecessaryGetter */ // library marker kkossev.xiaomiLib, line 1
library( // library marker kkossev.xiaomiLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Xiaomi Library', name: 'xiaomiLib', namespace: 'kkossev', importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/xiaomiLib.groovy', documentationLink: '', // library marker kkossev.xiaomiLib, line 3
    version: '3.2.2' // library marker kkossev.xiaomiLib, line 4
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
 * // library marker kkossev.xiaomiLib, line 23
 *                                   TODO: remove the DEVICE_TYPE dependencies for Bulb, Thermostat, AqaraCube, FP1, TRV_OLD // library marker kkossev.xiaomiLib, line 24
 *                                   TODO: remove the isAqaraXXX  dependencies !! // library marker kkossev.xiaomiLib, line 25
*/ // library marker kkossev.xiaomiLib, line 26

static String xiaomiLibVersion()   { '3.2.2' } // library marker kkossev.xiaomiLib, line 28
static String xiaomiLibStamp() { '2024/06/05 4:57 AM' } // library marker kkossev.xiaomiLib, line 29

boolean isAqaraTVOC_Lib()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 31
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 32
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.xiaomiLib, line 33
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.xiaomiLib, line 34
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.xiaomiLib, line 35

// no metadata for this library! // library marker kkossev.xiaomiLib, line 37

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.xiaomiLib, line 39

// Zigbee Attributes // library marker kkossev.xiaomiLib, line 41
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.xiaomiLib, line 42
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.xiaomiLib, line 43
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.xiaomiLib, line 44
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.xiaomiLib, line 45
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.xiaomiLib, line 46
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.xiaomiLib, line 47
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.xiaomiLib, line 48
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.xiaomiLib, line 49
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.xiaomiLib, line 50
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.xiaomiLib, line 51
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.xiaomiLib, line 52
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.xiaomiLib, line 53
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.xiaomiLib, line 54
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.xiaomiLib, line 55
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.xiaomiLib, line 56

// Xiaomi Tags // library marker kkossev.xiaomiLib, line 58
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.xiaomiLib, line 59
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 60
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.xiaomiLib, line 61
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.xiaomiLib, line 62
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 63
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.xiaomiLib, line 64

// called from parseXiaomiCluster() in the main code ... // library marker kkossev.xiaomiLib, line 66
// // library marker kkossev.xiaomiLib, line 67
void standardParseXiaomiFCC0Cluster(final Map descMap) { // library marker kkossev.xiaomiLib, line 68
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 69
        logTrace "standardParseXiaomiFCC0Cluster: zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 70
    } // library marker kkossev.xiaomiLib, line 71
    if (DEVICE_TYPE in  ['Thermostat']) { // library marker kkossev.xiaomiLib, line 72
        parseXiaomiClusterThermostatLib(descMap) // library marker kkossev.xiaomiLib, line 73
        return // library marker kkossev.xiaomiLib, line 74
    } // library marker kkossev.xiaomiLib, line 75
    if (DEVICE_TYPE in  ['Bulb']) { // library marker kkossev.xiaomiLib, line 76
        parseXiaomiClusterRgbLib(descMap) // library marker kkossev.xiaomiLib, line 77
        return // library marker kkossev.xiaomiLib, line 78
    } // library marker kkossev.xiaomiLib, line 79
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 80
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 81
    final String funcName = 'standardParseXiaomiFCC0Cluster' // library marker kkossev.xiaomiLib, line 82
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 83
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 84
            if (DEVICE_TYPE in  ['AqaraCube']) { logDebug "standardParseXiaomiFCC0Cluster: AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 85
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 86
            break // library marker kkossev.xiaomiLib, line 87
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 88
            logWarn "${funcName}: unknown attribute - resetting?" // library marker kkossev.xiaomiLib, line 89
            break // library marker kkossev.xiaomiLib, line 90
        case PRESENCE_ATTR_ID:            // 0x0142 FP1 // library marker kkossev.xiaomiLib, line 91
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 92
            parseXiaomiClusterPresence(value) // library marker kkossev.xiaomiLib, line 93
            break // library marker kkossev.xiaomiLib, line 94
        case PRESENCE_ACTIONS_ATTR_ID:    // 0x0143 FP1 // library marker kkossev.xiaomiLib, line 95
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 96
            parseXiaomiClusterPresenceAction(value) // library marker kkossev.xiaomiLib, line 97
            break // library marker kkossev.xiaomiLib, line 98
        case REGION_EVENT_ATTR_ID:        // 0x0151 FP1 // library marker kkossev.xiaomiLib, line 99
            // Region events can be sent fast and furious so buffer them // library marker kkossev.xiaomiLib, line 100
            final Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1]) // library marker kkossev.xiaomiLib, line 101
            final Integer value = HexUtils.hexStringToInt(descMap.value[2..3]) // library marker kkossev.xiaomiLib, line 102
            if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 103
                log.debug "${funcName}: xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 104
            } // library marker kkossev.xiaomiLib, line 105
            if (device.currentValue("region${regionId}") != null) { // library marker kkossev.xiaomiLib, line 106
                RegionUpdateBuffer.get(device.id).put(regionId, value) // library marker kkossev.xiaomiLib, line 107
                runInMillis(REGION_UPDATE_DELAY_MS, 'updateRegions') // library marker kkossev.xiaomiLib, line 108
            } // library marker kkossev.xiaomiLib, line 109
            break // library marker kkossev.xiaomiLib, line 110
        case SENSITIVITY_LEVEL_ATTR_ID:   // 0x010C FP1 // library marker kkossev.xiaomiLib, line 111
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 112
            log.info "sensitivity level is '${SensitivityLevelOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 113
            device.updateSetting('sensitivityLevel', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 114
            break // library marker kkossev.xiaomiLib, line 115
        case TRIGGER_DISTANCE_ATTR_ID:    // 0x0146 FP1 // library marker kkossev.xiaomiLib, line 116
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 117
            log.info "approach distance is '${ApproachDistanceOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 118
            device.updateSetting('approachDistance', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 119
            break // library marker kkossev.xiaomiLib, line 120
        case DIRECTION_MODE_ATTR_ID:     // 0x0144 FP1 // library marker kkossev.xiaomiLib, line 121
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 122
            log.info "monitoring direction mode is '${DirectionModeOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 123
            device.updateSetting('directionMode', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 124
            break // library marker kkossev.xiaomiLib, line 125
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode // library marker kkossev.xiaomiLib, line 126
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 127
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 128
            break // library marker kkossev.xiaomiLib, line 129
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 130
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 131
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 132
            break // library marker kkossev.xiaomiLib, line 133
        case XIAOMI_SPECIAL_REPORT_ID:   // 0x00F7 sent every 55 minutes // library marker kkossev.xiaomiLib, line 134
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value) // library marker kkossev.xiaomiLib, line 135
            parseXiaomiClusterTags(tags) // library marker kkossev.xiaomiLib, line 136
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 137
                sendZigbeeCommands(customRefresh()) // library marker kkossev.xiaomiLib, line 138
            } // library marker kkossev.xiaomiLib, line 139
            break // library marker kkossev.xiaomiLib, line 140
        case XIAOMI_RAW_ATTR_ID:        // 0xFFF2 FP1 // library marker kkossev.xiaomiLib, line 141
            final byte[] rawData = HexUtils.hexStringToByteArray(descMap.value) // library marker kkossev.xiaomiLib, line 142
            if (rawData.size() == 24 && settings.enableDistanceDirection) { // library marker kkossev.xiaomiLib, line 143
                final int degrees = rawData[19] // library marker kkossev.xiaomiLib, line 144
                final int distanceCm = (rawData[17] << 8) | (rawData[18] & 0x00ff) // library marker kkossev.xiaomiLib, line 145
                if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 146
                    log.debug "location ${degrees}&deg;, ${distanceCm}cm" // library marker kkossev.xiaomiLib, line 147
                } // library marker kkossev.xiaomiLib, line 148
                runIn(1, 'updateLocation', [ data: [ degrees: degrees, distanceCm: distanceCm ] ]) // library marker kkossev.xiaomiLib, line 149
            } // library marker kkossev.xiaomiLib, line 150
            break // library marker kkossev.xiaomiLib, line 151
        default: // library marker kkossev.xiaomiLib, line 152
            log.warn "${funcName}: zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 153
            break // library marker kkossev.xiaomiLib, line 154
    } // library marker kkossev.xiaomiLib, line 155
} // library marker kkossev.xiaomiLib, line 156

void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 158
    final String funcName = 'parseXiaomiClusterTags' // library marker kkossev.xiaomiLib, line 159
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 160
        switch (tag) { // library marker kkossev.xiaomiLib, line 161
            case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 162
                logDebug "${funcName}: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 163
                break // library marker kkossev.xiaomiLib, line 164
            case 0x03: // library marker kkossev.xiaomiLib, line 165
                logDebug "${funcName}: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 166
                break // library marker kkossev.xiaomiLib, line 167
            case 0x05: // library marker kkossev.xiaomiLib, line 168
                logDebug "${funcName}: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 169
                break // library marker kkossev.xiaomiLib, line 170
            case 0x06: // library marker kkossev.xiaomiLib, line 171
                logDebug "${funcName}: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 172
                break // library marker kkossev.xiaomiLib, line 173
            case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 174
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 175
                logDebug "${funcName}: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 176
                device.updateDataValue('aqaraVersion', swBuild) // library marker kkossev.xiaomiLib, line 177
                break // library marker kkossev.xiaomiLib, line 178
            case 0x0a: // library marker kkossev.xiaomiLib, line 179
                String nwk = intToHexStr(value as Integer, 2) // library marker kkossev.xiaomiLib, line 180
                if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 181
                String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 182
                logDebug "${funcName}: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 183
                if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 184
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 185
                    state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 186
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 187
                } // library marker kkossev.xiaomiLib, line 188
                break // library marker kkossev.xiaomiLib, line 189
            case 0x0b: // library marker kkossev.xiaomiLib, line 190
                logDebug "${funcName}: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 191
                break // library marker kkossev.xiaomiLib, line 192
            case 0x64: // library marker kkossev.xiaomiLib, line 193
                logDebug "${funcName}: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 194
                // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 195
                break // library marker kkossev.xiaomiLib, line 196
            case 0x65: // library marker kkossev.xiaomiLib, line 197
                if (isAqaraFP1()) { logDebug "${funcName} PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 198
                else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 199
                break // library marker kkossev.xiaomiLib, line 200
            case 0x66: // library marker kkossev.xiaomiLib, line 201
                if (isAqaraFP1()) { logDebug "${funcName} SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 202
                else if (isAqaraTVOC_Lib()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 203
                else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" } // library marker kkossev.xiaomiLib, line 204
                break // library marker kkossev.xiaomiLib, line 205
            case 0x67: // library marker kkossev.xiaomiLib, line 206
                if (isAqaraFP1()) { logDebug "${funcName} DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 207
                else              { logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC: // library marker kkossev.xiaomiLib, line 208
                // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 209
                break // library marker kkossev.xiaomiLib, line 210
            case 0x69: // library marker kkossev.xiaomiLib, line 211
                if (isAqaraFP1()) { logDebug "${funcName} TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 212
                else              { logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 213
                break // library marker kkossev.xiaomiLib, line 214
            case 0x6a: // library marker kkossev.xiaomiLib, line 215
                if (isAqaraFP1()) { logDebug "${funcName} FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 216
                else              { logDebug "${funcName} MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 217
                break // library marker kkossev.xiaomiLib, line 218
            case 0x6b: // library marker kkossev.xiaomiLib, line 219
                if (isAqaraFP1()) { logDebug "${funcName} FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 220
                else              { logDebug "${funcName} MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 221
                break // library marker kkossev.xiaomiLib, line 222
            case 0x95: // library marker kkossev.xiaomiLib, line 223
                logDebug "${funcName}: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 224
                break // library marker kkossev.xiaomiLib, line 225
            case 0x96: // library marker kkossev.xiaomiLib, line 226
                logDebug "${funcName}: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 227
                break // library marker kkossev.xiaomiLib, line 228
            case 0x97: // library marker kkossev.xiaomiLib, line 229
                logDebug "${funcName}: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 230
                break // library marker kkossev.xiaomiLib, line 231
            case 0x98: // library marker kkossev.xiaomiLib, line 232
                logDebug "${funcName}: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 233
                break // library marker kkossev.xiaomiLib, line 234
            case 0x9b: // library marker kkossev.xiaomiLib, line 235
                if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 236
                    logDebug "${funcName} Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})" // library marker kkossev.xiaomiLib, line 237
                    sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 238
                } // library marker kkossev.xiaomiLib, line 239
                else { logDebug "${funcName} CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 240
                break // library marker kkossev.xiaomiLib, line 241
            default: // library marker kkossev.xiaomiLib, line 242
                logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 243
        } // library marker kkossev.xiaomiLib, line 244
    } // library marker kkossev.xiaomiLib, line 245
} // library marker kkossev.xiaomiLib, line 246

/** // library marker kkossev.xiaomiLib, line 248
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 249
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 250
 */ // library marker kkossev.xiaomiLib, line 251
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 252
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 253
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 254
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 255
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 256
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 257
    } // library marker kkossev.xiaomiLib, line 258
    return bigInt // library marker kkossev.xiaomiLib, line 259
} // library marker kkossev.xiaomiLib, line 260

/** // library marker kkossev.xiaomiLib, line 262
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 263
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 264
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 265
 */ // library marker kkossev.xiaomiLib, line 266
private Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 267
    try { // library marker kkossev.xiaomiLib, line 268
        final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 269
        final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 270
        new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 271
            while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 272
                int tag = stream.read() // library marker kkossev.xiaomiLib, line 273
                int dataType = stream.read() // library marker kkossev.xiaomiLib, line 274
                Object value // library marker kkossev.xiaomiLib, line 275
                if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 276
                    int length = stream.read() // library marker kkossev.xiaomiLib, line 277
                    byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 278
                    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 279
                    value = new String(byteArr) // library marker kkossev.xiaomiLib, line 280
                } else { // library marker kkossev.xiaomiLib, line 281
                    int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 282
                    value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 283
                } // library marker kkossev.xiaomiLib, line 284
                results[tag] = value // library marker kkossev.xiaomiLib, line 285
            } // library marker kkossev.xiaomiLib, line 286
        } // library marker kkossev.xiaomiLib, line 287
        return results // library marker kkossev.xiaomiLib, line 288
    } // library marker kkossev.xiaomiLib, line 289
    catch (e) { // library marker kkossev.xiaomiLib, line 290
        if (settings.logEnable) "${device.displayName} decodeXiaomiTags: ${e}" // library marker kkossev.xiaomiLib, line 291
        return [:] // library marker kkossev.xiaomiLib, line 292
    } // library marker kkossev.xiaomiLib, line 293
} // library marker kkossev.xiaomiLib, line 294

List<String> refreshXiaomi() { // library marker kkossev.xiaomiLib, line 296
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 297
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.xiaomiLib, line 298
    return cmds // library marker kkossev.xiaomiLib, line 299
} // library marker kkossev.xiaomiLib, line 300

List<String> configureXiaomi() { // library marker kkossev.xiaomiLib, line 302
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 303
    logDebug "configureThermostat() : ${cmds}" // library marker kkossev.xiaomiLib, line 304
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.xiaomiLib, line 305
    return cmds // library marker kkossev.xiaomiLib, line 306
} // library marker kkossev.xiaomiLib, line 307

List<String> initializeXiaomi() { // library marker kkossev.xiaomiLib, line 309
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 310
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 311
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.xiaomiLib, line 312
    return cmds // library marker kkossev.xiaomiLib, line 313
} // library marker kkossev.xiaomiLib, line 314

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 316
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 317
} // library marker kkossev.xiaomiLib, line 318

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 320
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 321
} // library marker kkossev.xiaomiLib, line 322

List<String> standardAqaraBlackMagic() { // library marker kkossev.xiaomiLib, line 324
    return [] // library marker kkossev.xiaomiLib, line 325
    ///////////////////////////////////////// // library marker kkossev.xiaomiLib, line 326
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 327
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.xiaomiLib, line 328
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.xiaomiLib, line 329
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.xiaomiLib, line 330
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.xiaomiLib, line 331
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.xiaomiLib, line 332
        if (isAqaraTVOC_OLD()) { // library marker kkossev.xiaomiLib, line 333
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.xiaomiLib, line 334
        } // library marker kkossev.xiaomiLib, line 335
        logDebug 'standardAqaraBlackMagic()' // library marker kkossev.xiaomiLib, line 336
    } // library marker kkossev.xiaomiLib, line 337
    return cmds // library marker kkossev.xiaomiLib, line 338
} // library marker kkossev.xiaomiLib, line 339

// ~~~~~ end include (165) kkossev.xiaomiLib ~~~~~

// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NestedBlockDepth, NoDouble, NoFloat, NoWildcardImports, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.deviceProfileLib, line 1
library( // library marker kkossev.deviceProfileLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLib', namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy', documentationLink: '', // library marker kkossev.deviceProfileLib, line 4
    version: '3.2.1' // library marker kkossev.deviceProfileLib, line 5
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
 * ver. 3.0.0  2023-11-27 kkossev  - (dev. branch) fixes for use with commonLib; added processClusterAttributeFromDeviceProfile() method; added validateAndFixPreferences() method;  inputIt bug fix; signedInt Preproc method; // library marker kkossev.deviceProfileLib, line 20
 * ver. 3.0.1  2023-12-02 kkossev  - (dev. branch) release candidate // library marker kkossev.deviceProfileLib, line 21
 * ver. 3.0.2  2023-12-17 kkossev  - (dev. branch) inputIt moved to the preferences section; setfunction replaced by customSetFunction; Groovy Linting; // library marker kkossev.deviceProfileLib, line 22
 * ver. 3.0.4  2024-03-30 kkossev  - (dev. branch) more Groovy Linting; processClusterAttributeFromDeviceProfile exception fix; // library marker kkossev.deviceProfileLib, line 23
 * ver. 3.1.0  2024-04-03 kkossev  - (dev. branch) more Groovy Linting; deviceProfilesV3, enum pars bug fix; // library marker kkossev.deviceProfileLib, line 24
 * ver. 3.1.1  2024-04-21 kkossev  - (dev. branch) deviceProfilesV3 bug fix; tuyaDPs list of maps bug fix; resetPreferencesToDefaults bug fix; // library marker kkossev.deviceProfileLib, line 25
 * ver. 3.1.2  2024-05-05 kkossev  - (dev. branch) added isSpammyDeviceProfile() // library marker kkossev.deviceProfileLib, line 26
 * ver. 3.1.3  2024-05-21 kkossev  - skip processClusterAttributeFromDeviceProfile if cluster or attribute or value is missing // library marker kkossev.deviceProfileLib, line 27
 * ver. 3.2.0  2024-05-25 kkossev  - commonLib 3.2.0 allignment; // library marker kkossev.deviceProfileLib, line 28
 * ver. 3.2.1  2024-06-06 kkossev  - (dev. branch) Tuya Multi Sensor 4 In 1 (V3) driver allignment (customProcessDeviceProfileEvent); getDeviceProfilesMap bug fix; forcedProfile is always shown in preferences; // library marker kkossev.deviceProfileLib, line 29
 * // library marker kkossev.deviceProfileLib, line 30
 *                                   TODO - remove 2-in-1 patch ! // library marker kkossev.deviceProfileLib, line 31
 *                                   TODO - add defaults for profileId:'0104', endpointId:'01', inClusters, outClusters, in the deviceProfilesV3 map // library marker kkossev.deviceProfileLib, line 32
 *                                   TODO - updateStateUnknownDPs !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 33
 *                                   TODO - check why the forcedProfile preference is not initialized? // library marker kkossev.deviceProfileLib, line 34
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 35
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 36
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 37
 * // library marker kkossev.deviceProfileLib, line 38
*/ // library marker kkossev.deviceProfileLib, line 39

static String deviceProfileLibVersion()   { '3.2.1' } // library marker kkossev.deviceProfileLib, line 41
static String deviceProfileLibStamp() { '2024/06/06 5:43 PM' } // library marker kkossev.deviceProfileLib, line 42
import groovy.json.* // library marker kkossev.deviceProfileLib, line 43
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 44
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 45
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 46
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 47

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 49

metadata { // library marker kkossev.deviceProfileLib, line 51
    // no capabilities // library marker kkossev.deviceProfileLib, line 52
    // no attributes // library marker kkossev.deviceProfileLib, line 53
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 54
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 55
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 56
    ] // library marker kkossev.deviceProfileLib, line 57
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 58
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 59
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 60
    ] // library marker kkossev.deviceProfileLib, line 61

    preferences { // library marker kkossev.deviceProfileLib, line 63
        if (device) { // library marker kkossev.deviceProfileLib, line 64
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 65
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 66
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 67
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 68
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 69
                        input inputMap // library marker kkossev.deviceProfileLib, line 70
                    } // library marker kkossev.deviceProfileLib, line 71
                } // library marker kkossev.deviceProfileLib, line 72
            } // library marker kkossev.deviceProfileLib, line 73
            //if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 74
                input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 75
            //} // library marker kkossev.deviceProfileLib, line 76
        } // library marker kkossev.deviceProfileLib, line 77
    } // library marker kkossev.deviceProfileLib, line 78
} // library marker kkossev.deviceProfileLib, line 79

boolean is2in1() { return getDeviceProfile().contains('TS0601_2IN1') }    // patch removed 05/29/2024 // library marker kkossev.deviceProfileLib, line 81

String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 83
Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 84
Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 85
//List<String> getDeviceProfilesMap()   { deviceProfilesV3 != null ? deviceProfilesV3.values().description as List<String> : deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 86

List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLib, line 88
    if (deviceProfilesV3 == null) { // library marker kkossev.deviceProfileLib, line 89
        if (deviceProfilesV2 == null) { return [] } // library marker kkossev.deviceProfileLib, line 90
        return deviceProfilesV2.values().description as List<String> // library marker kkossev.deviceProfileLib, line 91
    } // library marker kkossev.deviceProfileLib, line 92
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLib, line 93
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 94
        if ((profileMap.device?.isDepricated ?: false) != true) { // library marker kkossev.deviceProfileLib, line 95
            activeProfiles.add(profileMap.description ?: '---') // library marker kkossev.deviceProfileLib, line 96
        } // library marker kkossev.deviceProfileLib, line 97
    } // library marker kkossev.deviceProfileLib, line 98
    return activeProfiles // library marker kkossev.deviceProfileLib, line 99
} // library marker kkossev.deviceProfileLib, line 100


// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 103

/** // library marker kkossev.deviceProfileLib, line 105
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 106
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 107
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 108
 */ // library marker kkossev.deviceProfileLib, line 109
String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 110
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 111
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 112
    else { return null } // library marker kkossev.deviceProfileLib, line 113
} // library marker kkossev.deviceProfileLib, line 114

/** // library marker kkossev.deviceProfileLib, line 116
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 117
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 118
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 119
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 120
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 121
 */ // library marker kkossev.deviceProfileLib, line 122
Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 123
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 124
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 125
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 126
    def preference // library marker kkossev.deviceProfileLib, line 127
    try { // library marker kkossev.deviceProfileLib, line 128
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 129
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 130
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 131
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 132
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 133
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 134
        } // library marker kkossev.deviceProfileLib, line 135
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 136
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 137
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 138
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 139
        } // library marker kkossev.deviceProfileLib, line 140
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 141
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 142
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 143
        } // library marker kkossev.deviceProfileLib, line 144
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 145
    } catch (e) { // library marker kkossev.deviceProfileLib, line 146
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 147
        return [:] // library marker kkossev.deviceProfileLib, line 148
    } // library marker kkossev.deviceProfileLib, line 149
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 150
    return foundMap // library marker kkossev.deviceProfileLib, line 151
} // library marker kkossev.deviceProfileLib, line 152

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 154
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 155
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 156
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 157
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 158
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 159
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 160
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 161
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 162
            return foundMap // library marker kkossev.deviceProfileLib, line 163
        } // library marker kkossev.deviceProfileLib, line 164
    } // library marker kkossev.deviceProfileLib, line 165
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 166
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 167
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 168
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 169
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 170
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 171
            return foundMap // library marker kkossev.deviceProfileLib, line 172
        } // library marker kkossev.deviceProfileLib, line 173
    } // library marker kkossev.deviceProfileLib, line 174
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 175
    return [:] // library marker kkossev.deviceProfileLib, line 176
} // library marker kkossev.deviceProfileLib, line 177

/** // library marker kkossev.deviceProfileLib, line 179
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 180
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 181
 */ // library marker kkossev.deviceProfileLib, line 182
void resetPreferencesToDefaults(boolean debug=true) { // library marker kkossev.deviceProfileLib, line 183
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 184
    Map preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 185
    if (preferences == null || preferences.isEmpty()) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 186
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 187
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 188
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 189
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 190
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 191
            return // continue // library marker kkossev.deviceProfileLib, line 192
        } // library marker kkossev.deviceProfileLib, line 193
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 194
        if (parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 195
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 196
        if (parMap.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 197
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 198
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 199
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 200
    } // library marker kkossev.deviceProfileLib, line 201
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 202
} // library marker kkossev.deviceProfileLib, line 203

/** // library marker kkossev.deviceProfileLib, line 205
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 206
 * // library marker kkossev.deviceProfileLib, line 207
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 208
 */ // library marker kkossev.deviceProfileLib, line 209
List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 210
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 211
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 212
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 213
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 214
    } // library marker kkossev.deviceProfileLib, line 215
    return validPars // library marker kkossev.deviceProfileLib, line 216
} // library marker kkossev.deviceProfileLib, line 217

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 219
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 220
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 221
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 222
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 223
    def scaledValue // library marker kkossev.deviceProfileLib, line 224
    if (value == null) { // library marker kkossev.deviceProfileLib, line 225
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 226
        return null // library marker kkossev.deviceProfileLib, line 227
    } // library marker kkossev.deviceProfileLib, line 228
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 229
        case 'number' : // library marker kkossev.deviceProfileLib, line 230
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 231
            break // library marker kkossev.deviceProfileLib, line 232
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 233
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 234
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 235
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 236
            } // library marker kkossev.deviceProfileLib, line 237
            break // library marker kkossev.deviceProfileLib, line 238
        case 'bool' : // library marker kkossev.deviceProfileLib, line 239
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 240
            break // library marker kkossev.deviceProfileLib, line 241
        case 'enum' : // library marker kkossev.deviceProfileLib, line 242
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 243
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 244
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 245
                return null // library marker kkossev.deviceProfileLib, line 246
            } // library marker kkossev.deviceProfileLib, line 247
            scaledValue = value // library marker kkossev.deviceProfileLib, line 248
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 249
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 250
            } // library marker kkossev.deviceProfileLib, line 251
            break // library marker kkossev.deviceProfileLib, line 252
        default : // library marker kkossev.deviceProfileLib, line 253
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 254
            return null // library marker kkossev.deviceProfileLib, line 255
    } // library marker kkossev.deviceProfileLib, line 256
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 257
    return scaledValue // library marker kkossev.deviceProfileLib, line 258
} // library marker kkossev.deviceProfileLib, line 259

// called from customUpdated() method in the custom driver // library marker kkossev.deviceProfileLib, line 261
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 262
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 263
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 264
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 265
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 266
        return // library marker kkossev.deviceProfileLib, line 267
    } // library marker kkossev.deviceProfileLib, line 268
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 269
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 270
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 271
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 272
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 273
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 274
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 275
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 276
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 277
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 278
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 279
                // scale the value // library marker kkossev.deviceProfileLib, line 280
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 281
            } // library marker kkossev.deviceProfileLib, line 282
            if (preferenceValue != null) { setPar(name, preferenceValue.toString()) } // library marker kkossev.deviceProfileLib, line 283
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLib, line 284
        } // library marker kkossev.deviceProfileLib, line 285
        else { logDebug "warning: couldn't find map for preference ${name}" ; return } // library marker kkossev.deviceProfileLib, line 286
    } // library marker kkossev.deviceProfileLib, line 287
    return // library marker kkossev.deviceProfileLib, line 288
} // library marker kkossev.deviceProfileLib, line 289

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 291
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 292
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 293
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 294
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 295
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 296
} // library marker kkossev.deviceProfileLib, line 297
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 298
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 299
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 300
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 301
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 302
} // library marker kkossev.deviceProfileLib, line 303
int invert(int val) { // library marker kkossev.deviceProfileLib, line 304
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 305
    else { return val } // library marker kkossev.deviceProfileLib, line 306
} // library marker kkossev.deviceProfileLib, line 307

List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 309
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 310
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 311
    Map map = [:] // library marker kkossev.deviceProfileLib, line 312
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 313
    try { // library marker kkossev.deviceProfileLib, line 314
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 315
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 316
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 317
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 318
    } // library marker kkossev.deviceProfileLib, line 319
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLib, line 320
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 321
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 322
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLib, line 323
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 324
    } // library marker kkossev.deviceProfileLib, line 325
    if (map.mfgCode != null && map.mfgCode != '') { // library marker kkossev.deviceProfileLib, line 326
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 327
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mfgCode, delay = 200) // library marker kkossev.deviceProfileLib, line 328
    } // library marker kkossev.deviceProfileLib, line 329
    else { // library marker kkossev.deviceProfileLib, line 330
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 331
    } // library marker kkossev.deviceProfileLib, line 332
    return cmds // library marker kkossev.deviceProfileLib, line 333
} // library marker kkossev.deviceProfileLib, line 334

/** // library marker kkossev.deviceProfileLib, line 336
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 337
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 338
 * // library marker kkossev.deviceProfileLib, line 339
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 340
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 341
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 342
 */ // library marker kkossev.deviceProfileLib, line 343
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 344
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 345
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 346
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 347
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 348
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 349
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 350
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 351
        case 'number' : // library marker kkossev.deviceProfileLib, line 352
            value = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 353
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 354
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 355
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 356
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 357
            } // library marker kkossev.deviceProfileLib, line 358
            else { // library marker kkossev.deviceProfileLib, line 359
                scaledValue = value // library marker kkossev.deviceProfileLib, line 360
            } // library marker kkossev.deviceProfileLib, line 361
            break // library marker kkossev.deviceProfileLib, line 362

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 364
            value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 365
            // scale the value // library marker kkossev.deviceProfileLib, line 366
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 367
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 368
            } // library marker kkossev.deviceProfileLib, line 369
            else { // library marker kkossev.deviceProfileLib, line 370
                scaledValue = value // library marker kkossev.deviceProfileLib, line 371
            } // library marker kkossev.deviceProfileLib, line 372
            break // library marker kkossev.deviceProfileLib, line 373

        case 'bool' : // library marker kkossev.deviceProfileLib, line 375
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 376
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 377
            else { // library marker kkossev.deviceProfileLib, line 378
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 379
                return null // library marker kkossev.deviceProfileLib, line 380
            } // library marker kkossev.deviceProfileLib, line 381
            break // library marker kkossev.deviceProfileLib, line 382
        case 'enum' : // library marker kkossev.deviceProfileLib, line 383
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 384
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 385
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 386
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 387
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 388
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 389
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 390
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 391
            } // library marker kkossev.deviceProfileLib, line 392
            else { // library marker kkossev.deviceProfileLib, line 393
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 394
            } // library marker kkossev.deviceProfileLib, line 395
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 396
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 397
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 398
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 399
                // find the key for the value // library marker kkossev.deviceProfileLib, line 400
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 401
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 402
                if (key == null) { // library marker kkossev.deviceProfileLib, line 403
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 404
                    return null // library marker kkossev.deviceProfileLib, line 405
                } // library marker kkossev.deviceProfileLib, line 406
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 407
            //return null // library marker kkossev.deviceProfileLib, line 408
            } // library marker kkossev.deviceProfileLib, line 409
            break // library marker kkossev.deviceProfileLib, line 410
        default : // library marker kkossev.deviceProfileLib, line 411
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 412
            return null // library marker kkossev.deviceProfileLib, line 413
    } // library marker kkossev.deviceProfileLib, line 414
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 415
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 416
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 417
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 418
        return null // library marker kkossev.deviceProfileLib, line 419
    } // library marker kkossev.deviceProfileLib, line 420
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 421
    return scaledValue // library marker kkossev.deviceProfileLib, line 422
} // library marker kkossev.deviceProfileLib, line 423

/** // library marker kkossev.deviceProfileLib, line 425
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 426
 * // library marker kkossev.deviceProfileLib, line 427
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 428
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 429
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 430
 */ // library marker kkossev.deviceProfileLib, line 431
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 432
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 433
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 434
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 435
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 436
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 437
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 438
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 439
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 440
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 441
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 442
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 443
    if (scaledValue == null) { logInfo "setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 444

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 446
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 447
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 448
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 449
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 450
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 451
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 452
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 453
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 454
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 455
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 456
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 457
            return true // library marker kkossev.deviceProfileLib, line 458
        } // library marker kkossev.deviceProfileLib, line 459
        else { // library marker kkossev.deviceProfileLib, line 460
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 461
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 462
        } // library marker kkossev.deviceProfileLib, line 463
    } // library marker kkossev.deviceProfileLib, line 464
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 465
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 466
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 467
        def valMiscType // library marker kkossev.deviceProfileLib, line 468
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 469
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 470
            // find the key for the value // library marker kkossev.deviceProfileLib, line 471
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 472
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 473
            if (key == null) { // library marker kkossev.deviceProfileLib, line 474
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 475
                return false // library marker kkossev.deviceProfileLib, line 476
            } // library marker kkossev.deviceProfileLib, line 477
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 478
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 479
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 480
        } // library marker kkossev.deviceProfileLib, line 481
        else { // library marker kkossev.deviceProfileLib, line 482
            valMiscType = val // library marker kkossev.deviceProfileLib, line 483
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 484
        } // library marker kkossev.deviceProfileLib, line 485
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 486
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 487
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 488
        return true // library marker kkossev.deviceProfileLib, line 489
    } // library marker kkossev.deviceProfileLib, line 490

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 492
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 493

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 495
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 496
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 497
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 498
        // Tuya DP // library marker kkossev.deviceProfileLib, line 499
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 500
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 501
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 502
            return false // library marker kkossev.deviceProfileLib, line 503
        } // library marker kkossev.deviceProfileLib, line 504
        else { // library marker kkossev.deviceProfileLib, line 505
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 506
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 507
            return false // library marker kkossev.deviceProfileLib, line 508
        } // library marker kkossev.deviceProfileLib, line 509
    } // library marker kkossev.deviceProfileLib, line 510
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 511
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 512
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mapMfCode=${dpMap.mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 513
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 514
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 515
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 516
            return false // library marker kkossev.deviceProfileLib, line 517
        } // library marker kkossev.deviceProfileLib, line 518
    } // library marker kkossev.deviceProfileLib, line 519
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 520
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 521
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 522
    return true // library marker kkossev.deviceProfileLib, line 523
} // library marker kkossev.deviceProfileLib, line 524

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 526
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 527
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 528
List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 529
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 530
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 531
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 532
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 533
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 534
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 535
        return [] // library marker kkossev.deviceProfileLib, line 536
    } // library marker kkossev.deviceProfileLib, line 537
    String dpType // library marker kkossev.deviceProfileLib, line 538
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 539
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 540
    } // library marker kkossev.deviceProfileLib, line 541
    else { // library marker kkossev.deviceProfileLib, line 542
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 543
    } // library marker kkossev.deviceProfileLib, line 544
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 545
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 546
        return [] // library marker kkossev.deviceProfileLib, line 547
    } // library marker kkossev.deviceProfileLib, line 548
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 549
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 550
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 551
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 552
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 553
    } // library marker kkossev.deviceProfileLib, line 554
    else { // library marker kkossev.deviceProfileLib, line 555
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 556
    } // library marker kkossev.deviceProfileLib, line 557
    return cmds // library marker kkossev.deviceProfileLib, line 558
} // library marker kkossev.deviceProfileLib, line 559

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 561
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 562
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 563
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 564
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 565
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 566

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 568
    if (dpMap == null || dpMap.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 569
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 570
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 571
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 572
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 573
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 574
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 575
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 576
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 577
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 578
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 579
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 580
        try { // library marker kkossev.deviceProfileLib, line 581
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 582
        } // library marker kkossev.deviceProfileLib, line 583
        catch (e) { // library marker kkossev.deviceProfileLib, line 584
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 585
            return false // library marker kkossev.deviceProfileLib, line 586
        } // library marker kkossev.deviceProfileLib, line 587
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 588
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 589
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 590
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 591
            return true // library marker kkossev.deviceProfileLib, line 592
        } // library marker kkossev.deviceProfileLib, line 593
        else { // library marker kkossev.deviceProfileLib, line 594
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 595
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 596
        } // library marker kkossev.deviceProfileLib, line 597
    } // library marker kkossev.deviceProfileLib, line 598
    else { // library marker kkossev.deviceProfileLib, line 599
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 600
    } // library marker kkossev.deviceProfileLib, line 601
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 602
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 603
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 604
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 605
        // patch !! // library marker kkossev.deviceProfileLib, line 606
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 607
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 608
        } // library marker kkossev.deviceProfileLib, line 609
        else { // library marker kkossev.deviceProfileLib, line 610
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 611
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 612
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 613
        } // library marker kkossev.deviceProfileLib, line 614
        return true // library marker kkossev.deviceProfileLib, line 615
    } // library marker kkossev.deviceProfileLib, line 616
    else { // library marker kkossev.deviceProfileLib, line 617
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 618
    } // library marker kkossev.deviceProfileLib, line 619
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 620
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 621
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 622
    try { // library marker kkossev.deviceProfileLib, line 623
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 624
    } // library marker kkossev.deviceProfileLib, line 625
    catch (e) { // library marker kkossev.deviceProfileLib, line 626
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 627
        return false // library marker kkossev.deviceProfileLib, line 628
    } // library marker kkossev.deviceProfileLib, line 629
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 630
        // Tuya DP // library marker kkossev.deviceProfileLib, line 631
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 632
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 633
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 634
            return false // library marker kkossev.deviceProfileLib, line 635
        } // library marker kkossev.deviceProfileLib, line 636
        else { // library marker kkossev.deviceProfileLib, line 637
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 638
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 639
            return true // library marker kkossev.deviceProfileLib, line 640
        } // library marker kkossev.deviceProfileLib, line 641
    } // library marker kkossev.deviceProfileLib, line 642
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 643
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 644
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 645
    } // library marker kkossev.deviceProfileLib, line 646
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 647
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 648
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 649
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 650
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 651
            return false // library marker kkossev.deviceProfileLib, line 652
        } // library marker kkossev.deviceProfileLib, line 653
    } // library marker kkossev.deviceProfileLib, line 654
    else { // library marker kkossev.deviceProfileLib, line 655
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 656
        return false // library marker kkossev.deviceProfileLib, line 657
    } // library marker kkossev.deviceProfileLib, line 658
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 659
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 660
    return true // library marker kkossev.deviceProfileLib, line 661
} // library marker kkossev.deviceProfileLib, line 662

/** // library marker kkossev.deviceProfileLib, line 664
 * Sends a command to the device. // library marker kkossev.deviceProfileLib, line 665
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 666
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 667
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 668
 */ // library marker kkossev.deviceProfileLib, line 669
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 670
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 671
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 672
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 673
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 674
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 675
    if (supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 676
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 677
        return false // library marker kkossev.deviceProfileLib, line 678
    } // library marker kkossev.deviceProfileLib, line 679
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 680
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 681
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 682
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 683
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 684
        return false // library marker kkossev.deviceProfileLib, line 685
    } // library marker kkossev.deviceProfileLib, line 686
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 687
    def func, funcResult // library marker kkossev.deviceProfileLib, line 688
    try { // library marker kkossev.deviceProfileLib, line 689
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 690
        if (val != null && val != '') { // library marker kkossev.deviceProfileLib, line 691
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 692
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 693
        } // library marker kkossev.deviceProfileLib, line 694
        else { // library marker kkossev.deviceProfileLib, line 695
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 696
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 697
        } // library marker kkossev.deviceProfileLib, line 698
    }  // library marker kkossev.deviceProfileLib, line 699
    catch (e) { // library marker kkossev.deviceProfileLib, line 700
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 701
        return false // library marker kkossev.deviceProfileLib, line 702
    }  // library marker kkossev.deviceProfileLib, line 703
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 704
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 705
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 706
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 707
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 708
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 709
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 710
        } // library marker kkossev.deviceProfileLib, line 711
    } else { // library marker kkossev.deviceProfileLib, line 712
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 713
        return false // library marker kkossev.deviceProfileLib, line 714
    } // library marker kkossev.deviceProfileLib, line 715
    /* // library marker kkossev.deviceProfileLib, line 716
    cmds = funcResult // library marker kkossev.deviceProfileLib, line 717
    if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 718
        sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 719
    } // library marker kkossev.deviceProfileLib, line 720
    */ // library marker kkossev.deviceProfileLib, line 721
    return true // library marker kkossev.deviceProfileLib, line 722
} // library marker kkossev.deviceProfileLib, line 723

/** // library marker kkossev.deviceProfileLib, line 725
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 726
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 727
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 728
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 729
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 730
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 731
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 732
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 733
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 734
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 735
 */ // library marker kkossev.deviceProfileLib, line 736
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 737
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 738
    Map input = [:] // library marker kkossev.deviceProfileLib, line 739
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 740
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 741
        if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 742
        return [:] // library marker kkossev.deviceProfileLib, line 743
    } // library marker kkossev.deviceProfileLib, line 744
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 745
    def preference // library marker kkossev.deviceProfileLib, line 746
    try { // library marker kkossev.deviceProfileLib, line 747
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 748
    } // library marker kkossev.deviceProfileLib, line 749
    catch (e) { // library marker kkossev.deviceProfileLib, line 750
        if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 751
        return [:] // library marker kkossev.deviceProfileLib, line 752
    } // library marker kkossev.deviceProfileLib, line 753
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 754
    try { // library marker kkossev.deviceProfileLib, line 755
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 756
            if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } // library marker kkossev.deviceProfileLib, line 757
            return [:] // library marker kkossev.deviceProfileLib, line 758
        } // library marker kkossev.deviceProfileLib, line 759
    } // library marker kkossev.deviceProfileLib, line 760
    catch (e) { // library marker kkossev.deviceProfileLib, line 761
        if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 762
        return [:] // library marker kkossev.deviceProfileLib, line 763
    } // library marker kkossev.deviceProfileLib, line 764

    try { // library marker kkossev.deviceProfileLib, line 766
        isTuyaDP = preference.isNumber() // library marker kkossev.deviceProfileLib, line 767
    } // library marker kkossev.deviceProfileLib, line 768
    catch (e) { // library marker kkossev.deviceProfileLib, line 769
        if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 770
        return [:] // library marker kkossev.deviceProfileLib, line 771
    } // library marker kkossev.deviceProfileLib, line 772

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 774
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 775
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 776
    if (foundMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 777
        if (debug) { log.warn "inputIt: map not found for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 778
        return [:] // library marker kkossev.deviceProfileLib, line 779
    } // library marker kkossev.deviceProfileLib, line 780
    if (foundMap.rw != 'rw') { // library marker kkossev.deviceProfileLib, line 781
        if (debug) { log.warn "inputIt: param '${param}' is read only!" } // library marker kkossev.deviceProfileLib, line 782
        return [:] // library marker kkossev.deviceProfileLib, line 783
    } // library marker kkossev.deviceProfileLib, line 784
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 785
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 786
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 787
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 788
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 789
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 790
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 791
        } // library marker kkossev.deviceProfileLib, line 792
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 793
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 794
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 795
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 796
            } // library marker kkossev.deviceProfileLib, line 797
        } // library marker kkossev.deviceProfileLib, line 798
    } // library marker kkossev.deviceProfileLib, line 799
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 800
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 801
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 802
    }/* // library marker kkossev.deviceProfileLib, line 803
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 804
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 805
    }*/ // library marker kkossev.deviceProfileLib, line 806
    else { // library marker kkossev.deviceProfileLib, line 807
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 808
        return [:] // library marker kkossev.deviceProfileLib, line 809
    } // library marker kkossev.deviceProfileLib, line 810
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 811
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 812
    } // library marker kkossev.deviceProfileLib, line 813
    return input // library marker kkossev.deviceProfileLib, line 814
} // library marker kkossev.deviceProfileLib, line 815

/** // library marker kkossev.deviceProfileLib, line 817
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 818
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 819
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 820
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 821
 */ // library marker kkossev.deviceProfileLib, line 822
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 823
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 824
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 825
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 826
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 827
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 828
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 829
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 830
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 831
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 832
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 833
            } // library marker kkossev.deviceProfileLib, line 834
        } // library marker kkossev.deviceProfileLib, line 835
    } // library marker kkossev.deviceProfileLib, line 836
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 837
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 838
    } // library marker kkossev.deviceProfileLib, line 839
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 840
} // library marker kkossev.deviceProfileLib, line 841

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 843
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 844
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 845
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 846
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 847
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 848
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 849
    } // library marker kkossev.deviceProfileLib, line 850
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 851
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 852
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 853
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 854
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 855
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 856
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 857
    } else { // library marker kkossev.deviceProfileLib, line 858
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 859
    } // library marker kkossev.deviceProfileLib, line 860
} // library marker kkossev.deviceProfileLib, line 861

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLib, line 863
List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLib, line 864
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLib, line 865
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 866
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLib, line 867
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 868
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 869
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 870
            if (k != null) { // library marker kkossev.deviceProfileLib, line 871
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 872
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 873
                if (map != null) { // library marker kkossev.deviceProfileLib, line 874
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 875
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 876
                } // library marker kkossev.deviceProfileLib, line 877
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 878
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 879
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 880
                } // library marker kkossev.deviceProfileLib, line 881
            } // library marker kkossev.deviceProfileLib, line 882
        } // library marker kkossev.deviceProfileLib, line 883
    } // library marker kkossev.deviceProfileLib, line 884
    return cmds // library marker kkossev.deviceProfileLib, line 885
} // library marker kkossev.deviceProfileLib, line 886

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 888
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 889
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 890
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 891
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 892
    return cmds // library marker kkossev.deviceProfileLib, line 893
} // library marker kkossev.deviceProfileLib, line 894

// TODO ! // library marker kkossev.deviceProfileLib, line 896
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 897
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 898
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 899
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 900
    return cmds // library marker kkossev.deviceProfileLib, line 901
} // library marker kkossev.deviceProfileLib, line 902

// TODO // library marker kkossev.deviceProfileLib, line 904
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 905
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 906
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 907
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 908
    return cmds // library marker kkossev.deviceProfileLib, line 909
} // library marker kkossev.deviceProfileLib, line 910

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 912
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 913
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 914
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 915
    } // library marker kkossev.deviceProfileLib, line 916
} // library marker kkossev.deviceProfileLib, line 917

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 919
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 920
} // library marker kkossev.deviceProfileLib, line 921

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 923

// // library marker kkossev.deviceProfileLib, line 925
// called from parse() // library marker kkossev.deviceProfileLib, line 926
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 927
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 928
// // library marker kkossev.deviceProfileLib, line 929
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 930
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 931
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 932
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 933
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 934
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 935
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 936
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 937
} // library marker kkossev.deviceProfileLib, line 938

// // library marker kkossev.deviceProfileLib, line 940
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 941
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 942
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 943
// // library marker kkossev.deviceProfileLib, line 944
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 945
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 946
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 947
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 948
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 949
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 950
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 951
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 952
} // library marker kkossev.deviceProfileLib, line 953

// all DPs are spammy - sent periodically! // library marker kkossev.deviceProfileLib, line 955
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 956
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 957
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 958
    return isSpammy // library marker kkossev.deviceProfileLib, line 959
} // library marker kkossev.deviceProfileLib, line 960

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 962
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 963
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 964
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 965
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 966
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 967
    } // library marker kkossev.deviceProfileLib, line 968
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 969
} // library marker kkossev.deviceProfileLib, line 970

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 972
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 973
    boolean isEqual // library marker kkossev.deviceProfileLib, line 974
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 975
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 976
    } // library marker kkossev.deviceProfileLib, line 977
    else { // library marker kkossev.deviceProfileLib, line 978
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 979
    } // library marker kkossev.deviceProfileLib, line 980
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 981
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 982
} // library marker kkossev.deviceProfileLib, line 983

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 985
    Double convertedValue // library marker kkossev.deviceProfileLib, line 986
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 987
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 988
    } // library marker kkossev.deviceProfileLib, line 989
    else { // library marker kkossev.deviceProfileLib, line 990
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 991
    } // library marker kkossev.deviceProfileLib, line 992
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 993
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 994
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 995
} // library marker kkossev.deviceProfileLib, line 996

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 998
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 999
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1000
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1001
    def convertedValue // library marker kkossev.deviceProfileLib, line 1002
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1003
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1004
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1005
    } // library marker kkossev.deviceProfileLib, line 1006
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1007
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1008
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1009
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1010
            isEqual = false // library marker kkossev.deviceProfileLib, line 1011
        } // library marker kkossev.deviceProfileLib, line 1012
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1013
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1014
        } // library marker kkossev.deviceProfileLib, line 1015
    } // library marker kkossev.deviceProfileLib, line 1016
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1017
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1018
} // library marker kkossev.deviceProfileLib, line 1019

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1021
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1022
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1023
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1024
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1025
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1026
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1027
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1028
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1029
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1030
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1031
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1032
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1033
            break // library marker kkossev.deviceProfileLib, line 1034
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1035
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1036
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1037
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1038
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1039
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1040
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1041
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1042
            } // library marker kkossev.deviceProfileLib, line 1043
            else { // library marker kkossev.deviceProfileLib, line 1044
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1045
            } // library marker kkossev.deviceProfileLib, line 1046
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1047
            break // library marker kkossev.deviceProfileLib, line 1048
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1049
        case 'number' : // library marker kkossev.deviceProfileLib, line 1050
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1051
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1052
            break // library marker kkossev.deviceProfileLib, line 1053
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1054
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1055
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1056
            break // library marker kkossev.deviceProfileLib, line 1057
        default : // library marker kkossev.deviceProfileLib, line 1058
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1059
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1060
    } // library marker kkossev.deviceProfileLib, line 1061
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1062
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1063
    } // library marker kkossev.deviceProfileLib, line 1064
    // // library marker kkossev.deviceProfileLib, line 1065
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1066
} // library marker kkossev.deviceProfileLib, line 1067

// // library marker kkossev.deviceProfileLib, line 1069
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1070
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1071
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1072
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1073
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1074
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1075
// // library marker kkossev.deviceProfileLib, line 1076
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1077
// // library marker kkossev.deviceProfileLib, line 1078
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1079
// // library marker kkossev.deviceProfileLib, line 1080
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1081
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1082
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1083
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1084
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1085
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1086
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1087
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1088
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1089
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1090
            break // library marker kkossev.deviceProfileLib, line 1091
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1092
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1093
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1094
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1095
            logTrace "latestEvent is dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1096
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1097
            if (dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1098
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1099
            } // library marker kkossev.deviceProfileLib, line 1100
            else { // library marker kkossev.deviceProfileLib, line 1101
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1102
            } // library marker kkossev.deviceProfileLib, line 1103
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1104
            break // library marker kkossev.deviceProfileLib, line 1105
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1106
        case 'number' : // library marker kkossev.deviceProfileLib, line 1107
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1108
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1109
            break // library marker kkossev.deviceProfileLib, line 1110
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1111
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1112
            break // library marker kkossev.deviceProfileLib, line 1113
        default : // library marker kkossev.deviceProfileLib, line 1114
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1115
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1116
    } // library marker kkossev.deviceProfileLib, line 1117
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1118
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1119
} // library marker kkossev.deviceProfileLib, line 1120

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1122
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1123
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1124
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1125
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1126
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1127
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1128
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1129
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1130
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1131
    } // library marker kkossev.deviceProfileLib, line 1132
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1133
    try { // library marker kkossev.deviceProfileLib, line 1134
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1135
    } // library marker kkossev.deviceProfileLib, line 1136
    catch (e) { // library marker kkossev.deviceProfileLib, line 1137
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1138
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1139
    } // library marker kkossev.deviceProfileLib, line 1140
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1141
    return fncmd // library marker kkossev.deviceProfileLib, line 1142
} // library marker kkossev.deviceProfileLib, line 1143

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1145
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1146
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1147
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1148
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1149
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1150
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1151

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1153
    if (attribMap == null || attribMap.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1154

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1156
    int value // library marker kkossev.deviceProfileLib, line 1157
    try { // library marker kkossev.deviceProfileLib, line 1158
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1159
    } // library marker kkossev.deviceProfileLib, line 1160
    catch (e) { // library marker kkossev.deviceProfileLib, line 1161
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1162
        return false // library marker kkossev.deviceProfileLib, line 1163
    } // library marker kkossev.deviceProfileLib, line 1164
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1165
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1166
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1167
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1168
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1169
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1170
        return false // library marker kkossev.deviceProfileLib, line 1171
    } // library marker kkossev.deviceProfileLib, line 1172
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1173
} // library marker kkossev.deviceProfileLib, line 1174

/** // library marker kkossev.deviceProfileLib, line 1176
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1177
 * // library marker kkossev.deviceProfileLib, line 1178
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1179
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1180
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1181
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1182
 * // library marker kkossev.deviceProfileLib, line 1183
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1184
 */ // library marker kkossev.deviceProfileLib, line 1185
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1186
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1187
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1188
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1189
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1190

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1192
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1193

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1195
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1196
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1197
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1198
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1199
        return false // library marker kkossev.deviceProfileLib, line 1200
    } // library marker kkossev.deviceProfileLib, line 1201
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1202
} // library marker kkossev.deviceProfileLib, line 1203

/* // library marker kkossev.deviceProfileLib, line 1205
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1206
 */ // library marker kkossev.deviceProfileLib, line 1207
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1208
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1209
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1210
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1211
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1212
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1213
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1214
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1215
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1216
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1217
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1218
        } // library marker kkossev.deviceProfileLib, line 1219
    } // library marker kkossev.deviceProfileLib, line 1220
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1221

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1223
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1224
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1225
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1226
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1227
    boolean preferenceExists = DEVICE?.preferences?.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1228
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1229
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1230
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1231
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1232
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1233
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1234
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1235
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1236
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1237
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1238
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1239

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1241
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1242
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1243
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1244
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1245
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1246
            if (settings.logEnable) { logInfo "${descText }" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1247
        } // library marker kkossev.deviceProfileLib, line 1248
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1249
    } // library marker kkossev.deviceProfileLib, line 1250

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1252
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1253
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1254
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1255
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1256
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1257
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1258
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1259
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1260
            } // library marker kkossev.deviceProfileLib, line 1261
        } // library marker kkossev.deviceProfileLib, line 1262
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1263
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1264
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1265
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1266
            } // library marker kkossev.deviceProfileLib, line 1267
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1268
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1269
            try { // library marker kkossev.deviceProfileLib, line 1270
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1271
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1272
            } // library marker kkossev.deviceProfileLib, line 1273
            catch (e) { // library marker kkossev.deviceProfileLib, line 1274
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1275
            } // library marker kkossev.deviceProfileLib, line 1276
        } // library marker kkossev.deviceProfileLib, line 1277
    } // library marker kkossev.deviceProfileLib, line 1278
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1279
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1280
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1281
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1282
    } // library marker kkossev.deviceProfileLib, line 1283

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1285
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1286
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1287
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1288
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1289
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1290
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1291
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1292
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1293
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1294
            } // library marker kkossev.deviceProfileLib, line 1295

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1297
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1298
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1299
            // continue ... // library marker kkossev.deviceProfileLib, line 1300
            } // library marker kkossev.deviceProfileLib, line 1301

            else { // library marker kkossev.deviceProfileLib, line 1303
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1304
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1305
                } // library marker kkossev.deviceProfileLib, line 1306
                else { // library marker kkossev.deviceProfileLib, line 1307
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLib, line 1308
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1309
                } // library marker kkossev.deviceProfileLib, line 1310
            } // library marker kkossev.deviceProfileLib, line 1311
        } // library marker kkossev.deviceProfileLib, line 1312

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1314
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1315
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1316
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1317
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1318
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1319
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1320
        } // library marker kkossev.deviceProfileLib, line 1321
        else { // library marker kkossev.deviceProfileLib, line 1322
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1323
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1324
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1325
                logTrace "event ${name} sent w/ value ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1326
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1327
            } // library marker kkossev.deviceProfileLib, line 1328
        } // library marker kkossev.deviceProfileLib, line 1329
    } // library marker kkossev.deviceProfileLib, line 1330
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1331
} // library marker kkossev.deviceProfileLib, line 1332

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1334
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1335
    //debug = true // library marker kkossev.deviceProfileLib, line 1336
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1337
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLib, line 1338
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLib, line 1339
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1340
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLib, line 1341
    String settingType = '' // library marker kkossev.deviceProfileLib, line 1342
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1343
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1344
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1345
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1346
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1347
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1348
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1349
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1350
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1351
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1352
            try { // library marker kkossev.deviceProfileLib, line 1353
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1354
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1355
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLib, line 1356
            } // library marker kkossev.deviceProfileLib, line 1357
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1358
            try { // library marker kkossev.deviceProfileLib, line 1359
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1360
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1361
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1362
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1363
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1364
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1365
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1366
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1367
                    } // library marker kkossev.deviceProfileLib, line 1368
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1369
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1370
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1371
                    } else { // library marker kkossev.deviceProfileLib, line 1372
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1373
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1374
                    } // library marker kkossev.deviceProfileLib, line 1375
                } // library marker kkossev.deviceProfileLib, line 1376
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1377
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1378
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1379
            } // library marker kkossev.deviceProfileLib, line 1380
            catch (e) { // library marker kkossev.deviceProfileLib, line 1381
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1382
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1383
                try { // library marker kkossev.deviceProfileLib, line 1384
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1385
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1386
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1387
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1388
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1389
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLib, line 1390
                } // library marker kkossev.deviceProfileLib, line 1391
            } // library marker kkossev.deviceProfileLib, line 1392
        } // library marker kkossev.deviceProfileLib, line 1393
        total ++ // library marker kkossev.deviceProfileLib, line 1394
    } // library marker kkossev.deviceProfileLib, line 1395
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1396
    return true // library marker kkossev.deviceProfileLib, line 1397
} // library marker kkossev.deviceProfileLib, line 1398

// command for debugging // library marker kkossev.deviceProfileLib, line 1400
public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1401
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1402
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1403
            logInfo "${fingerprint}" // library marker kkossev.deviceProfileLib, line 1404
        } // library marker kkossev.deviceProfileLib, line 1405
    } // library marker kkossev.deviceProfileLib, line 1406
} // library marker kkossev.deviceProfileLib, line 1407

// command for debugging // library marker kkossev.deviceProfileLib, line 1409
public void printPreferences() { // library marker kkossev.deviceProfileLib, line 1410
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 1411
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 1412
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 1413
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLib, line 1414
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 1415
                log.trace inputMap // library marker kkossev.deviceProfileLib, line 1416
            } // library marker kkossev.deviceProfileLib, line 1417
        } // library marker kkossev.deviceProfileLib, line 1418
    } // library marker kkossev.deviceProfileLib, line 1419
} // library marker kkossev.deviceProfileLib, line 1420

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~
