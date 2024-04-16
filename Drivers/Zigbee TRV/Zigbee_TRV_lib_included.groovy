/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateStringLiteral, ImplicitClosureParameter, MethodCount, MethodSize, NglParseError, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessarySetter */
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
 * ver. 3.0.0  2023-11-16 kkossev  - (dev. branch) Refactored version 2.x.x drivers and libraries; adding MOES BRT-100 support - setHeatingSettpoint OK; off OK; level OK; workingState OK
 *                                    Emergency Heat OK;   setThermostatMode OK; Heat OK, Auto OK, Cool OK; setThermostatFanMode OK
 * ver. 3.0.1  2023-12-02 kkossev  - (dev. branch) added NAMRON thermostat profile; added Sonoff TRVZB 0x0201 (Thermostat Cluster) support; thermostatOperatingState ; childLock OK; windowOpenDetection OK; setPar OK for BRT-100 and Aqara;
 *                                    minHeatingSetpoint & maxHeatingSetpoint OK; calibrationTemp negative values OK!; auto OK; heat OK; cool and emergency heat OK (unsupported); Sonoff off mode OK;
 * ver. 3.0.2  2023-12-02 kkossev  - (dev. branch) importUrl correction; BRT-100: auto OK, heat OK, eco OK, supportedThermostatModes is defined in the device profile; refresh OK; autPoll OK (both BRT-100 and Sonoff);
 *                                   removed isBRT100TRV() ; removed isSonoffTRV(), removed 'off' mode for BRT-100; heatingSetPoint 12.3 bug fixed;
 * ver. 3.0.3  2023-12-03 kkossev  - (dev. branch) Aqara E1 thermostat refactoring : removed isAqaraTRV(); heatingSetpoint OK; off mode OK, auto OK heat OK; driverVersion state is updated on healthCheck and on preferences saving;
 * ver. 3.0.4  2023-12-08 kkossev  - (dev. branch) code cleanup; fingerpints not generated bug fix; initializeDeviceThermostat() bug fix; debug logs are enabled by default; added VIRTUAL thermostat : ping, auto, cool, emergency heat, heat, off, eco - OK!
 *                                   setTemperature, setHeatingSetpoint, setCoolingSetpoint - OK setPar() OK  setCommand() OK; Google Home compatibility for virtual thermostat;  BRT-100: Google Home exceptions bug fix; setHeatingSetpoint to update also the thermostatSetpoint for Google Home compatibility; added 'off' mode for BRT-100;
 * ver. 3.0.5  2023-12-09 kkossev  - (dev. branch) BRT-100 - off mode (substitutues with eco mode); emergency heat mode ; BRT-100 - digital events for temperature, heatingSetpoint and level on autoPollThermostat() and Refresh(); BRT-100: workingState open/closed replaced with thermostatOperatingState
 * ver. 3.0.6  2023-12-18 kkossev  - (dev. branch) configure() changes (SONOFF still not initialized properly!); adding TUYA_SASWELL group; TUYA_SASWELL heatingSetpoint correction; Groovy linting;
 * ver. 3.0.7  2024-03-04 kkossev  - (dev. branch) commonLib 3.0.3 check; more Groovy lint;
 * ver. 3.0.8  2024-04-01 kkossev  - (dev. branch) commonLib 3.0.4 check; more Groovy lint; tested w/ Sonoff TRVZB;
 * ver. 3.1.0  2024-04-17 kkossev  - (dev. branch) commonLib 3.0.7 check; changed to deviceProfilesV3
 *
 *                                   TODO: Test VRT-100
 *                                   TODO: Test Aqara TRV
 *                                   TODO: Sonoff : decode weekly shcedule responses (command 0x00)
 *                                   TODO: Aqara : dev:42172023-12-14 06:48:22.925errorjava.lang.NumberFormatException: For input string: "03281A052101000A21E2CC0D231E0A00001123010000006520006629D809672940066823000000006920646A2000" on line 473 (method parse)
 *                                   TODO: BRT-100 : what is emergencyHeatingTime and boostTime ?
 *                                   TODO: initializeDeviceThermostat() - configure in the device profile !
 *                                   TODO: partial match for the fingerprint (model if Tuya, manufacturer for the rest)
 *                                   TODO: remove (raw:) when debug is off
 *                                   TODO: prevent from showing "invalid enum parameter emergency heat. value must be one of [0, 1, 2, 3, 4]"; also for  invalid enum parameter cool. *
 *                                   TODO: add [refresh] for battery heatingSetpoint thermostatOperatingState events and logs
 *                                   TODO: hide the maxTimeBetweenReport preferences (not used here)
 *                                   TODO: option to disable the Auto mode ! (like in the wall thermostat driver)
 *                                   TODO: allow NULL parameters default values in the device profiles
 *                                   TODO: autoPollThermostat: no polling for device profile UNKNOWN
 *                                   TODO: configure the reporting for the 0x0201:0x0000 temperature !  (300..3600)
 *                                   TODO: Ping the device on initialize
 *                                   TODO: add factoryReset command Basic -0x0000 (Server); command 0x00
 *                                   TODO: initializeThermostat()
 *                                   TODO: Healthcheck to be every hour (not 4 hours) for thermostats
 *                                   TODO: add option 'Simple TRV' (no additinal attributes)
 *                                   TODO: add state.thermostat for stroring last attributes
 *                                   TODO: add 'force manual mode' preference (like in the wall thermostat driver)
 *                                   TODO: move debug and info logging preferences from the common library to the driver, so that they are the first preferences in the list
 *                                   TODO: add Info dummy preference to the driver with a hyperlink
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off)
 *                                   TODO: make a driver template for new drivers
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

/* groovylint-disable-next-line ImplicitReturnStatement */
static String version() { '3.1.0' }
/* groovylint-disable-next-line ImplicitReturnStatement */
static String timeStamp() { '2024/04/17 5:00 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput







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
        capability 'ThermostatHeatingSetpoint'
        capability 'ThermostatCoolingSetpoint'
        capability 'ThermostatOperatingState'   // thermostatOperatingState - ENUM ["vent economizer", "pending cool", "cooling", "heating", "pending heat", "fan only", "idle"]
        capability 'ThermostatSetpoint'
        capability 'ThermostatMode'
        capability 'ThermostatFanMode'

        // TODO - add all other models attributes possible values
        //attribute 'battery', 'number'                               // Aqara, BRT-100
        attribute 'batteryVoltage', 'number'
        attribute 'boostTime', 'number'                             // BRT-100
        attribute 'calibrated', 'enum', ['false', 'true']           // Aqara E1
        attribute 'calibrationTemp', 'number'                       // BRT-100, Sonoff
        attribute 'childLock', 'enum', ['off', 'on']                // BRT-100, Aqara E1, Sonoff
        attribute 'ecoMode', 'enum', ['off', 'on']                  // BRT-100
        attribute 'ecoTemp', 'number'                               // BRT-100
        attribute 'emergencyHeating', 'enum', ['off', 'on']         // BRT-100
        attribute 'emergencyHeatingTime', 'number'                  // BRT-100
        attribute 'frostProtectionTemperature', 'number'            // Sonoff
        attribute 'hysteresis', 'NUMBER'                            // Virtual thermostat
        attribute 'level', 'number'                                 // BRT-100
        attribute 'minHeatingSetpoint', 'number'                    // BRT-100, Sonoff
        attribute 'maxHeatingSetpoint', 'number'                    // BRT-100, Sonoff
        attribute 'sensor', 'enum', ['internal', 'external']         // Aqara E1
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
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
        if (advancedOptions == true) { // TODO -  move it to the deviceProfile preferences
            input name: 'temperaturePollingInterval', type: 'enum', title: '<b>Temperature polling interval</b>', options: TrvTemperaturePollingIntervalOpts.options, defaultValue: TrvTemperaturePollingIntervalOpts.defaultValue, required: true, description: '<i>Changes how often the hub will poll the TRV for faster temperature reading updates and nice looking graphs.</i>'
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
                [at:'0xFCC0:0x040A',  name:'battery',               type:'number',  dt:'0x20', mfgCode:'0x115f',  rw: 'ro', min:0,    max:100,  step:1,  scale:1,    unit:'%',  description:'<i>Battery percentage remaining</i>'],
                [at:'0xFCC0:0x0270',  name:'unknown1',              type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',   title: '<b>Unknown 0x0270</b>',   description:'<i>Unknown 0x0270</i>'],
                [at:'0xFCC0:0x0271',  name:'systemMode',            type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'',     title: '<b>System Mode</b>',      description:'<i>Switch the TRV OFF or in operation (on)</i>'],
                [at:'0xFCC0:0x0272',  name:'thermostatMode',        type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,    max:2,    step:1,  scale:1,    map:[0: 'heat', 1: 'auto', 2: 'away'], unit:'',                   title: '<b>Preset</b>',           description:'<i>Preset</i>'],
                [at:'0xFCC0:0x0273',  name:'windowOpenDetection',   type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,    max:1,    defaultValue:'0',    step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'',             title: '<b>Window Detection</b>', description:'<i>Window detection</i>'],
                [at:'0xFCC0:0x0274',  name:'valveDetection',        type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,    max:1,    defaultValue:'0',    step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'',             title: '<b>Valve Detection</b>',  description:'<i>Valve detection</i>'],
                [at:'0xFCC0:0x0275',  name:'valveAlarm',            type:'enum',    dt:'0x23', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    defaultValue:'0',    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',         title: '<b>Valve Alarm</b>',      description:'<i>Valve alarm</i>'],  // read only
                [at:'0xFCC0:0x0276',  name:'unknown2',              type:'enum',    dt:'0x41', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',         title: '<b>Unknown 0x0270</b>',                        description:'<i>Unknown 0x0270</i>'],
                [at:'0xFCC0:0x0277',  name:'childLock',             type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,    max:1,    defaultValue:'0',    step:1,  scale:1,    map:[0: 'unlock', 1: 'lock'], unit:'',        title: '<b>Child Lock</b>',       description:'<i>Child lock</i>'],
                [at:'0xFCC0:0x0278',  name:'unknown3',              type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ow', min:0,    max:1,    defaultValue:'0',    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',         title: '<b>Unknown 3</b>',        description:'<i>Unknown 3</i>'],   // WRITE ONLY !
                [at:'0xFCC0:0x0279',  name:'awayPresetTemperature', type:'decimal', dt:'0x23', mfgCode:'0x115f',  rw: 'rw', min:5.0,  max:35.0, defaultValue:5.0,    step:0.5, scale:100,  unit:'°C', title: '<b>Away Preset Temperature</b>',       description:'<i>Away preset temperature</i>'],
                [at:'0xFCC0:0x027A',  name:'windowsState',          type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    defaultValue:'0',    step:1,  scale:1,    map:[0: 'open', 1: 'closed'], unit:'',        title: '<b>Window Open</b>',      description:'<i>Window open</i>'],
                [at:'0xFCC0:0x027B',  name:'calibrated',            type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    defaultValue:'0',    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',         title: '<b>Calibrated</b>',       description:'<i>Calibrated</i>'],
                [at:'0xFCC0:0x027C',  name:'unknown4',              type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    defaultValue:'0',    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',         title: '<b>Unknown 4</b>',        description:'<i>Unknown 4</i>'],
                [at:'0xFCC0:0x027D',  name:'schedule',              type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    defaultValue:'0',    step:1,  scale:1,    map:[0: 'off', 1: 'on'], unit:'',             title: '<b>Schedule</b>',        description:'<i>Schedule</i>'],
                [at:'0xFCC0:0x027E',  name:'sensor',                type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', min:0,    max:1,    defaultValue:'0',    step:1,  scale:1,    map:[0: 'internal', 1: 'external'], unit:'',  title: '<b>Sensor</b>',           description:'<i>Sensor</i>'],
                //   0xFCC0:0x027F ... 0xFCC0:0x0284 - unknown
                [at:'0x0201:0x0000',  name:'temperature',           type:'decimal', dt:'0x29', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Temperature</b>',                   description:'<i>Measured temperature</i>'],
                [at:'0x0201:0x0011',  name:'coolingSetpoint',       type:'decimal', dt:'0x29', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Cooling Setpoint</b>',              description:'<i>cooling setpoint</i>'],
                [at:'0x0201:0x0012',  name:'heatingSetpoint',       type:'decimal', dt:'0x29', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'<i>Current heating setpoint</i>'],
                [at:'0x0201:0x001B',  name:'thermostatOperatingState', type:'enum',    dt:'0x30', rw:'rw',  min:0,    max:4,    step:1,  scale:1,    map:[0: 'off', 1: 'heating', 2: 'unknown', 3: 'unknown3', 4: 'idle'], unit:'',  description:'<i>thermostatOperatingState (relay on/off status)</i>'],      //  nothing happens when WRITING ????
                //                      ^^^^                                reporting only ?
                [at:'0x0201:0x001C',  name:'mode',                  type:'enum',    dt:'0x30', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b> Mode</b>',                   description:'<i>System Mode ?</i>'],
                //                      ^^^^ TODO - check if this is the same as system_mode
                [at:'0x0201:0x001E',  name:'thermostatRunMode',     type:'enum',    dt:'0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatRunMode</b>',                   description:'<i>thermostatRunMode</i>'],
                //                          ^^ unsupported attribute?  or reporting only ?
                [at:'0x0201:0x0020',  name:'battery2',              type:'number',  dt:'0x20', rw: 'ro', min:0,    max:100,  step:1,  scale:1,    unit:'%',  description:'<i>Battery percentage remaining</i>'],
                //                          ^^ unsupported attribute?  or reporting only ?
                [at:'0x0201:0x0023',  name:'thermostatHoldMode',    type:'enum',    dt:'0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatHoldMode</b>',                   description:'<i>thermostatHoldMode</i>'],
                //                          ^^ unsupported attribute?  or reporting only ?
                [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum', dt:'0x20', rw: 'ow', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heating'], unit:'',         title: '<b>thermostatOperatingState</b>',                   description:'<i>thermostatOperatingState</i>'],
                //                          ^^ unsupported attribute?  or reporting only ?   encoding - 0x29 ?? ^^
                [at:'0x0201:0xFFF2',  name:'unknown',                type:'number', dt:'0x21', rw: 'ro', min:0,    max:100,  step:1,  scale:1,    unit:'%',  description:'<i>Battery percentage remaining</i>'],
            //                          ^^ unsupported attribute?  or reporting only ?
            ],
            supportedThermostatModes: ['off', 'auto', 'heat', 'away'/*, "emergency heat"*/],
            refresh: ['pollAqara'],
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
                [at:'0x0201:0x0000',  name:'temperature',           type:'decimal', dt:'0x29', rw:'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'<i>Local temperature</i>'],
                [at:'0x0201:0x0002',  name:'occupancy',             type:'enum',    dt:'0x18', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'unoccupied', 1: 'occupied'], unit:'',  description:'<i>Occupancy</i>'],
                [at:'0x0201:0x0003',  name:'absMinHeatingSetpointLimit',  type:'decimal', dt:'0x29', rw:'ro', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'<i>Abs Min Heat Setpoint Limit</i>'],
                [at:'0x0201:0x0004',  name:'absMaxHeatingSetpointLimit',  type:'decimal', dt:'0x29', rw:'ro', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'<i>Abs Max Heat Setpoint Limit</i>'],
                [at:'0x0201:0x0010',  name:'calibrationTemp',  preProc:'signedInt',     type:'decimal', dt:'0x28', rw:'rw', min:-7.0,  max:7.0, defaultValue:0.0, step:0.2, scale:10,  unit:'°C', title: '<b>Local Temperature Calibration</b>', description:'<i>Room temperature calibration</i>'],
                [at:'0x0201:0x0012',  name:'heatingSetpoint',       type:'decimal', dt:'0x29', rw:'rw', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Heating Setpoint</b>',      description:'<i>Occupied heating setpoint</i>'],
                [at:'0x0201:0x0015',  name:'minHeatingSetpoint',    type:'decimal', dt:'0x29', rw:'rw', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Min Heating Setpoint</b>', description:'<i>Min Heating Setpoint Limit</i>'],
                [at:'0x0201:0x0016',  name:'maxHeatingSetpoint',    type:'decimal', dt:'0x29', rw:'rw', min:4.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Max Heating Setpoint</b>', description:'<i>Max Heating Setpoint Limit</i>'],
                [at:'0x0201:0x001A',  name:'remoteSensing',         type:'enum',    dt:'0x18', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'false', 1: 'true'], unit:'',  title: '<b>Remote Sensing<</b>', description:'<i>Remote Sensing</i>'],
                [at:'0x0201:0x001B',  name:'termostatRunningState', type:'enum',    dt:'0x30', rw:'rw', min:0,    max:2,    step:1,  scale:1,    map:[0: 'off', 1: 'heat', 2: 'unknown'], unit:'',  description:'<i>termostatRunningState (relay on/off status)</i>'],      //  nothing happens when WRITING ????
                [at:'0x0201:0x001C',  name:'thermostatMode',        type:'enum',    dt:'0x30', rw:'rw', min:0,    max:4,    step:1,  scale:1,    map:[0: 'off', 1: 'auto', 2: 'invalid', 3: 'invalid', 4: 'heat'], unit:'', title: '<b>System Mode</b>',  description:'<i>Thermostat Mode</i>'],
                [at:'0x0201:0x001E',  name:'thermostatRunMode',     type:'enum',    dt:'0x30', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heat'], unit:'', title: '<b>Thermostat Run Mode</b>',   description:'<i>Thermostat run mode</i>'],
                [at:'0x0201:0x0020',  name:'startOfWeek',           type:'enum',    dt:'0x30', rw:'ro', min:0,    max:6,    step:1,  scale:1,    map:[0: 'Sun', 1: 'Mon', 2: 'Tue', 3: 'Wed', 4: 'Thu', 5: 'Fri', 6: 'Sat'], unit:'',  description:'<i>Start of week</i>'],
                [at:'0x0201:0x0021',  name:'numWeeklyTransitions',  type:'number',  dt:'0x20', rw:'ro', min:0,    max:255,  step:1,  scale:1,    unit:'',  description:'<i>Number Of Weekly Transitions</i>'],
                [at:'0x0201:0x0022',  name:'numDailyTransitions',   type:'number',  dt:'0x20', rw:'ro', min:0,    max:255,  step:1,  scale:1,    unit:'',  description:'<i>Number Of Daily Transitions</i>'],
                [at:'0x0201:0x0025',  name:'thermostatProgrammingOperationMode', type:'enum',  dt:'0x18', rw:'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'mode1', 1: 'mode2'], unit:'',  title: '<b>Thermostat Programming Operation Mode/b>', description:'<i>Thermostat programming operation mode</i>'],  // nothing happens when WRITING ????
                [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum', dt:'0x19', rw:'ro', min:0,    max:1,    step:1,  scale:1,    map:[0: 'idle', 1: 'heating'], unit:'',  description:'<i>termostatRunningState (relay on/off status)</i>'],   // read only!
                // https://github.com/photomoose/zigbee-herdsman-converters/blob/227b28b23455f1a767c94889f57293c26e4a1e75/src/devices/sonoff.ts
                [at:'0x0006:0x0000',  name:'onOffReport',          type:'number',  dt: '0x10', rw: 'ro', min:0,    max:255,  step:1,  scale:1,   unit:'',  description:'<i>TRV on/off report</i>'],     // read only, 00 = off; 01 - thermostat is on
                [at:'0xFC11:0x0000',  name:'childLock',             type:'enum',    dt: '0x10', rw: 'rw', min:0,    max:1,  defaultValue:'0', step:1,  scale:1,   map:[0: 'off', 1: 'on'], unit:'',   title: '<b>Child Lock</b>',   description:'<i>Child lock<br>unlocked/locked</i>'],
                [at:'0xFC11:0x6000',  name:'windowOpenDetection',   type:'enum',    dt: '0x10', rw: 'rw', min:0,    max:1,  defaultValue:'0', step:1,  scale:1,   map:[0: 'off', 1: 'on'], unit:'',   title: '<b>Open Window Detection</b>',   description:'<i>Automatically turns off the radiator when local temperature drops by more than 1.5°C in 4.5 minutes.</i>'],
                [at:'0xFC11:0x6002',  name:'frostProtectionTemperature', type:'decimal',  dt: '0x29', rw: 'rw', min:4.0,    max:35.0,  defaultValue:7.0, step:0.5,  scale:100,   unit:'°C',   title: '<b>Frost Protection Temperature</b>',   description:'<i>Minimum temperature at which to automatically turn on the radiator, if system mode is off, to prevent pipes freezing.</i>'],
                [at:'0xFC11:0x6003',  name:'idleSteps ',            type:'number',  dt: '0x21', rw: 'ro', min:0,    max:9999, step:1,  scale:1,   unit:'', description:'<i>Number of steps used for calibration (no-load steps)</i>'],
                [at:'0xFC11:0x6004',  name:'closingSteps',          type:'number',  dt: '0x21', rw: 'ro', min:0,    max:9999, step:1,  scale:1,   unit:'', description:'<i>Number of steps it takes to close the valve</i>'],
                [at:'0xFC11:0x6005',  name:'valve_opening_limit_voltage',  type:'decimal',  dt: '0x21', rw: 'ro', min:0,    max:9999, step:1,  scale:1000,   unit:'V', description:'<i>Valve opening limit voltage</i>'],
                [at:'0xFC11:0x6006',  name:'valve_closing_limit_voltage',  type:'decimal',  dt: '0x21', rw: 'ro', min:0,    max:9999, step:1,  scale:1000,   unit:'V', description:'<i>Valve closing limit voltage</i>'],
                [at:'0xFC11:0x6007',  name:'valve_motor_running_voltage',  type:'decimal',  dt: '0x21', rw: 'ro', min:0,    max:9999, step:1,  scale:1000,   unit:'V', description:'<i>Valve motor running voltage</i>'],
                [at:'0xFC11:0x6008',  name:'unknown1',              type:'number',  dt: '0x20', rw: 'rw', min:0,    max:255, step:1,  scale:1,   unit:'', description:'<i>unknown1 (0xFC11:0x6008)/i>'],
                [at:'0xFC11:0x6009',  name:'heatingSetpoint_FC11',  type:'decimal',  dt: '0x29', rw: 'rw', min:4.0,  max:35.0, step:1,  scale:100,   unit:'°C', title: '<b>Heating Setpoint</b>',      description:'<i>Occupied heating setpoint</i>'],
                [at:'0xFC11:0x600A',  name:'unknown2',              type:'number',  dt: '0x29', rw: 'rw', min:0,    max:9999, step:1,  scale:1,   unit:'', description:'<i>unknown2 (0xFC11:0x600A)/i>'],
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
                [dp:1,   name:'thermostatMode',     type:'enum',            rw: 'rw', min:0,     max:3,    defaultValue:'1',  step:1,   scale:1,  map:[0: 'auto', 1: 'heat', 2: 'TempHold', 3: 'holidays'] ,   unit:'', title:'<b>Thermostat Mode</b>',  description:'<i>Thermostat mode</i>'],
                [dp:2,   name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,   max:45.0, defaultValue:20.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'<i>Current heating setpoint</i>'],
                [dp:3,   name:'temperature',        type:'decimal',         rw: 'ro', min:-10.0, max:50.0, defaultValue:20.0, step:0.5, scale:10, unit:'°C',  description:'<i>Temperature</i>'],
                [dp:4,   name:'emergencyHeating',   type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defaultValue:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Emergency Heating</b>',  description:'<i>Emergency heating</i>'],
                [dp:5,   name:'emergencyHeatingTime',   type:'number',      rw: 'rw', min:0,     max:720 , defaultValue:15,   step:15,  scale:1,  unit:'minutes', title:'<b>Emergency Heating Timer</b>',  description:'<i>Emergency heating timer</i>'],
                [dp:7,   name:'thermostatOperatingState',  type:'enum',     rw: 'rw', min:0,     max:1 ,   defaultValue:'0',  step:1,   scale:1,  map:[0:'heating', 1:'idle'] ,  unit:'', description:'<i>Thermostat Operating State(working state)</i>'],
                [dp:8,   name:'windowOpenDetection', type:'enum', dt: '01', rw: 'rw', min:0,     max:1 ,   defaultValue:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Window Detection</b>',  description:'<i>Window detection</i>'],
                [dp:9,   name:'windowsState',       type:'enum',            rw: 'rw', min:0,     max:1 ,   defaultValue:'0',  step:1,   scale:1,  map:[0:'open', 1:'closed'] ,   unit:'', title:'<bWindow State</b>',  description:'<i>Window state</i>'],
                [dp:13,  name:'childLock',          type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defaultValue:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Child Lock</b>',  description:'<i>Child lock</i>'],
                [dp:14,  name:'battery',            type:'number',          rw: 'ro', min:0,     max:100,  defaultValue:100,  step:1,   scale:1,  unit:'%',          description:'<i>Battery level</i>'],
                [dp:101, name:'weeklyProgram',      type:'number',          rw: 'ro', min:0,     max:9999, defaultValue:0,    step:1,   scale:1,  unit:'',          description:'<i>Weekly program</i>'],
                [dp:103, name:'boostTime',          type:'number',          rw: 'rw', min:100,   max:900 , defaultValue:100,  step:1,   scale:1,  unit:'seconds', title:'<b>Boost Timer</b>',  description:'<i>Boost timer</i>'],
                [dp:104, name:'level',              type:'number',          rw: 'ro', min:0,     max:100,  defaultValue:100,  step:1,   scale:1,  unit:'%',          description:'<i>Valve level</i>'],
                [dp:105, name:'calibrationTemp',    type:'decimal',         rw: 'rw', min:-9.0,  max:9.0,  defaultValue:00.0, step:1,   scale:1,  unit:'°C',  title:'<b>Calibration Temperature</b>', description:'<i>Calibration Temperature</i>'],
                [dp:106, name:'ecoMode',            type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defaultValue:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Eco mode</b>',  description:'<i>Eco mode</i>'],
                [dp:107, name:'ecoTemp',            type:'decimal',         rw: 'rw', min:5.0,   max:35.0, defaultValue:7.0,  step:1.0, scale:1,  unit:'°C',  title: '<b>Eco Temperature</b>',      description:'<i>Eco temperature</i>'],
                [dp:108, name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:15.0,  max:45.0, defaultValue:35.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Maximum Temperature</b>',      description:'<i>Maximum temperature</i>'],
                [dp:109, name:'minHeatingSetpoint', type:'decimal',         rw: 'rw', min:5.0,   max:15.0, defaultValue:10.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Minimum Temperature</b>',      description:'<i>Minimum temperature</i>'],
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
                [dp:8,   name:'windowOpenDetection', type:'enum', dt: '01', rw: 'rw', min:0,     max:1 ,   defaultValue:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Window Detection</b>',  description:'<i>Open Window Detection support</i>'],      // SASWELL_WINDOW_DETECT_ATTR
                [dp:10,  name:'antiFreeze',         type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defaultValue:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Anti-Freeze</b>',  description:'<i>Anti-Freeze support</i>'],                      // SASWELL_ANTI_FREEZE_ATTR
                [dp:27,  name:'calibrationTemp',    type:'decimal',         rw: 'rw', min:-6.0,  max:6.0,  defaultValue:0.0,  step:1,   scale:1,  unit:'°C',  title:'<b>Calibration Temperature</b>', description:'<i>Calibration Temperature</i>'],                // SASWELL_TEMP_CORRECTION_ATTR = 0x021B  # uint32 - temp correction 539 (27 dec)
                [dp:40,  name:'childLock',          type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defaultValue:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Child Lock</b>',  description:'<i>Child Lock setting support. Please remember that CL has to be set manually on the device. This only controls if locking is possible at all</i>'],                  // SASWELL_CHILD_LOCK_ATTR
                [dp:101, name:'thermostatMode',     type:'enum',            rw: 'rw', min:0,     max:1,    defaultValue:'1',  step:1,   scale:1,  map:[0: 'off', 1: 'heat'] ,   unit:'', title:'<b>Thermostat Mode</b>',  description:'<i>Thermostat mode</i>'],    // SASWELL_ONOFF_ATTR = 0x0165  # [0/1] on/off 357                     (101 dec)
                [dp:102, name:'temperature',        type:'decimal',         rw: 'ro', min:-10.0, max:50.0, defaultValue:20.0, step:0.5, scale:10, unit:'°C',  description:'<i>Temperature</i>'],                                                                    // SASWELL_ROOM_TEMP_ATTR = 0x0266  # uint32 - current room temp 614   (102 dec)
                [dp:103, name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,   max:30.0, defaultValue:20.0, step:1.0, scale:10, unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'<i>Current heating setpoint</i>'],        // SASWELL_TARGET_TEMP_ATTR = 0x0267  # uint32 - target temp 615       (103 dec)
                [dp:105, name:'batteryLowAlarm',    type:'enum',  dt: '01', rw: 'r0', min:0,     max:1 ,   defaultValue:'0',  step:1,   scale:1,  map:[0:'batteryOK', 1:'batteryLow'] ,   unit:'',  description:'<i>Battery low</i>'],                                            // SASWELL_BATTERY_ALARM_ATTR = 0x569  # [0/1] on/off - battery low 1385   (105 dec)
                [dp:106, name:'awayMode',           type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defaultValue:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Away Mode</b>',  description:'<i>Away Mode On/Off support</i>'],                    // SASWELL_AWAY_MODE_ATTR = 0x016A  # [0/1] on/off 362                 (106 dec)
                [dp:108, name:'scheduleMode',       type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defaultValue:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Schedule Mode</b>',  description:'<i>Schedule Mode On/Off support</i>'],            // SASWELL_SCHEDULE_MODE_ATTR = 0x016C  # [0/1] on/off 364             (108 dec)
                [dp:130, name:'limescaleProtect',   type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defaultValue:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Limescale Protect</b>',  description:'<i>Limescale Protection support</i>'],    // SASWELL_LIMESCALE_PROTECT_ATTR
            // missing !                 [dp:7,   name:'thermostatOperatingState',  type:"enum",     rw: "rw", min:0,     max:1 ,   defaultValue:"0",  step:1,   scale:1,  map:[0:"heating", 1:"idle"] ,  unit:"", description:'<i>Thermostat Operating State(working state)</i>'],
            ],
            supportedThermostatModes: ['off', 'heat'],
            refresh: ['pollTuya'],
            deviceJoinName: 'TUYA_SASWELL TRV',
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
                [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt:'0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', description:'<i>Measured room temperature</i>'],       // ^^^ (int16S, read-only) reportable LocalTemperature : Attribute This is room temperature, the maximum resolution this format allows is 0.01 ºC.
                [at:'0x0201:0x0001',  name:'outdoorTemperature',       type:'decimal', dt:'0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C',  description:'<i>Floor temperature</i>'],          // ^^^ (int16S, read-only) reportable OutdoorTemperature : This is floor temperature, the maximum resolution this format allows is 0.01 ºC.
                [at:'0x0201:0x0002',  name:'occupancy',                type:'enum',    dt:'0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',  description:'<i>Occupancy</i>'],      // ^^^ (bitmap8, read-only) Occupancy : When this flag is set as 1, it means occupied, OccupiedHeatingSetpoint will be used, otherwise UnoccupiedHeatingSetpoint will be used
                [at:'0x0201:0x0010',  name:'localTemperatureCalibration', type:'decimal', dt:'0x21', rw: 'rw', min:-30.0,  max:30.0, defaultValue:0.0, step:0.5, scale:10,  unit:'°C', title: '<b>Local Temperature Calibration</b>', description:'<i>Room temperature calibration</i>'],       // ^^^ (Int8S, reportable) TODO: check dt!!!    LocalTemperatureCalibration : Room temperature calibration, range is -30-30, the maximum resolution this format allows 0.1°C. Default value: 0
                [at:'0x0201:0x0011',  name:'coolingSetpoint',          type:'decimal', dt:'0x21', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Cooling Setpoint</b>',              description:'<i>This system is not invalid</i>'],       // not used
                [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt:'0x21', rw: 'rw', min:0.0,  max:40.0, defaultValue:30.0, step:0.01, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'<i>Current heating setpoint</i>'],                // ^^^(int16S, reportable)  OccupiedHeatingSetpoint : Range is 0-4000,the maximum resolution this format allows is 0.01 ºC. Default is 0xbb8(30.00ºC)
                [at:'0x0201:0x0014',  name:'unoccupiedHeatingSetpoint', type:'decimal', dt:'0x21', rw: 'rw', min:0.0,  max:40.0, defaultValue:30.0, step:0.01, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'<i>Current heating setpoint</i>'],                // ^^^(int16S, reportable)  OccupiedHeatingSetpoint : Range is 0-4000,the maximum resolution this format allows is 0.01 ºC. Default is 0x258(6.00ºC)
                [at:'0x0201:0x001B',  name:'termostatRunningState',    type:'enum',    dt:'0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',  description:'<i>termostatRunningState (relay on/off status)</i>'],                // ^^^(Map16, read-only, reportable) HVAC relay state/ termostatRunningState Indicates the relay on/off status, here only supports bit0( Heat State)
                // ============ Proprietary Attributes: Manufacturer code 0x1224 ============
                [at:'0x0201:0x0000',  name:'lcdBrightnesss',           type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defaultValue:'1',   step:1,  scale:1,    map:[0: 'Low Level', 1: 'Mid Level', 2: 'High Level'], unit:'',  title: '<b>OLED brightness</b>',description:'<i>OLED brightness</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  OLED brightness when operate the buttons: Value=0 : Low Level Value=1 : Mid Level(default) Value=2 : High Level
                [at:'0x0201:0x1002',  name:'floorSensorType',          type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:1,    max:5, defaultValue:'1',step:1,  scale:1,    map:[1: 'NTC 10K/25', 2: 'NTC 15K/25', 3: 'NTC 12K/25', 4: 'NTC 100K/25', 5: 'NTC 50K/25'], unit:'',  title: '<b>Floor Sensor Type</b>',description:'<i>Floor Sensor Type</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  TODO: check why there are 3 diferent enum groups???    FloorSenserType Value=5 : NTC 12K/25  Value=4 : NTC 100K/25 Value=3 : NTC 50K/25 Select external (Floor) sensor type: Value=1 : NTC 10K/25 (Default) Value=2 : NTC 15K/25 Value=5 : NTC 12K/25 Value=4 : NTC 100K/25 Value=3 : NTC 50K/25 Select external (Floor) sensor type: Value=1 : NTC 10K/25 (Default) Value=2 : NTC 15K/25
                [at:'0x0201:0x1003',  name:'controlType',              type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defaultValue:'0',step:1,  scale:1,    map:[0: 'Room sensor', 1: 'floor sensor', 2: 'Room+floor sensor'], unit:'',  title: '<b>Control Type</b>',description:'<i>Control Type</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  ControlType The referring sensor for heat control: Value=0 : Room sensor(Default) Value=1 : floor sensor Value=2 : Room+floor sensor
                [at:'0x0201:0x1004',  name:'powerUpStatus',            type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defaultValue:'1',   step:1,  scale:1,    map:[0: 'Low Level', 1: 'Mid Level', 2: 'High Level'], unit:'',  title: '<b>Power Up Status</b>',description:'<i>Power Up Status</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!! PowerUpStatus Value=0 : default mode The mode after reset power of the device: Value=1 : last status before power off (Default)
                [at:'0x0201:0x1005',  name:'floorSensorCalibration',   type:'decimal', dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:-30.0,  max:30.0, defaultValue:0.0, step:0.5, scale:10,  unit:'°C', title: '<b>Floor Sensor Calibration</b>', description:'<i>Floor Sensor Calibration/i>'],                // ^^^ (Int8S, reportable) TODO: check dt!!!    FloorSenserCalibration The temp compensation for the external (floor) sensor, range is -30-30, unit is 0.1°C. default value 0
                [at:'0x0201:0x1006',  name:'dryTime',                  type:'number',  dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:5,  max:100, defaultValue:5, step:1, scale:1,  unit:'minutes', title: '<b>Dry Time</b>', description:'<i>The duration of Dry Mode/i>'],                // ^^^ (Int8S, reportable) TODO: check dt!!!    DryTime The duration of Dry Mode, range is 5-100, unit is min. Default value is 5.
                [at:'0x0201:0x1007',  name:'modeAfterDry',             type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:2, defaultValue:'2',   step:1,  scale:1,    map:[0: 'OFF', 1: 'Manual mode', 2: 'Auto mode', 3: 'Away mode'], unit:'',  title: '<b>Mode After Dry</b>',description:'<i>The mode after Dry Mode</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!! ModeAfterDry The mode after Dry Mode: Value=0 : OFF Value=1 : Manual mode Value=2 : Auto mode –schedule (default) Value=3 : Away mode
                [at:'0x0201:0x1008',  name:'temperatureDisplay',       type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defaultValue:'1',   step:1,  scale:1,    map:[0: 'Room Temp', 1: 'Floor Temp'], unit:'',  title: '<b>Temperature Display</b>',description:'<i>Temperature Display</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!! TemperatureDisplay Value=0 : Room Temp (Default) Value=1 : Floor Temp
                [at:'0x0201:0x1009',  name:'windowOpenCheck',          type:'decimal', dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:0.3, max:8.0, defaultValue:0, step:0.5, scale:10,  unit:'', title: '<b>Window Open Check</b>', description:'<i>The threshold to detect open window, 0 means disabled</i>'],                // ^^^ (INT8U,reportable) TODO: check dt!!!    WindowOpenCheck The threshold to detect open window, range is 0.3-8, unit is 0.5ºC, 0 means disabled, default is 0
                [at:'0x0201:0x100A',  name:'hysterersis',              type:'decimal', dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:5.0, max:20.0, defaultValue:5.0, step:0.5, scale:10,  unit:'', title: '<b>Hysterersis</b>', description:'<i>Hysterersis</i>'],                // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  Hysterersis setting, range is 5-20, unit is 0.1ºC, default value is 5
                [at:'0x0201:0x100B',  name:'displayAutoOffEnable',     type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defaultValue:'1',   step:1,  scale:1,    map:[0: 'Disabled', 1: 'Enabled'], unit:'',  title: '<b>Display Auto Off Enable</b>',description:'<i>Display Auto Off Enable</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  DisplayAutoOffEnable 0, disable Display Auto Off function 1, enable Display Auto Off function
                [at:'0x0201:0x2001',  name:'alarmAirTempOverValue',    type:'decimal', dt:'0x21',  mfgCode:'0x1224', rw: 'rw', min:0.2, max:6.0, defaultValue:4.5, step:0.1, scale:10,  unit:'', title: '<b>Alarm Air Temp Over Value</b>', description:'<i>Alarm Air Temp Over Value, 0 means disabled,</i>'],                // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  AlarmAirTempOverValue Room temp alarm threshold, range is 0.20-60, unit is 1ºC,0 means disabled, default is 45
                [at:'0x0201:0x2002',  name:'awayModeSet',              type:'enum',    dt:'0x20',  mfgCode:'0x1224', rw: 'rw', min:0,    max:1, defaultValue:'0',   step:1,  scale:1,    map:[0: 'Not away', 1: 'Away'], unit:'',  title: '<b>Away Mode Set</b>',description:'<i>Away Mode Set</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  Away Mode Set: Value=1: away Value=0: not away (default)
                // Command supported  !!!!!!!!!! TODO !!!!!!!!!!!! not attribute, but a command !!!!!!!!!!!!!!!!
                [cmd:'0x0201:0x0000',  name:'setpointRaiseLower',       type:'decimal', dt:'0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:10,  unit:'°C', description:'<i>Setpoint Raise/Lower</i>'],                // ^^^ Setpoint Raise/Lower Increase or decrease the set temperature according to current mode, unit is 0.1ºC
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
                [at:'0x0201:0x0000',  name:'temperature',              type:'decimal', dt: '0x21', rw: 'ro', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Temperature</b>',                   description:'<i>Measured temperature</i>'],
                [at:'0x0201:0x0011',  name:'coolingSetpoint',          type:'decimal', dt: '0x21', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Cooling Setpoint</b>',              description:'<i>cooling setpoint</i>'],
                [at:'0x0201:0x0012',  name:'heatingSetpoint',          type:'decimal', dt: '0x21', rw: 'rw', min:5.0,  max:35.0, step:0.5, scale:100,  unit:'°C', title: '<b>Current Heating Setpoint</b>',      description:'<i>Current heating setpoint</i>'],
                [at:'0x0201:0x001C',  name:'mode',                     type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b> Mode</b>',                   description:'<i>System Mode ?</i>'],
                [at:'0x0201:0x001E',  name:'thermostatRunMode',        type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatRunMode</b>',                   description:'<i>thermostatRunMode</i>'],
                [at:'0x0201:0x0020',  name:'battery2',                 type:'number',  dt: '0x21', rw: 'ro', min:0,    max:100,  step:1,  scale:1,    unit:'%',  description:'<i>Battery percentage remaining</i>'],
                [at:'0x0201:0x0023',  name:'thermostatHoldMode',       type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatHoldMode</b>',                   description:'<i>thermostatHoldMode</i>'],
                [at:'0x0201:0x0029',  name:'thermostatOperatingState', type:'enum',    dt: '0x20', rw: 'rw', min:0,    max:1,    step:1,  scale:1,    map:[0: 'off', 1: 'heat'], unit:'',         title: '<b>thermostatOperatingState</b>',                   description:'<i>thermostatOperatingState</i>'],
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
    //logWarn "parseXiaomiClusterThermostatLib: received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})"

    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "zigbee received Thermostat 0xFCC0 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    Boolean result

    if ((descMap.attrInt as Integer) == 0x00F7 ) {      // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
        final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
        parseXiaomiClusterThermostatTags(tags)
        return
    }

    result = processClusterAttributeFromDeviceProfile(descMap)

    if ( result == false ) {
        logWarn "parseXiaomiClusterThermostatLib: received unknown Thermostat cluster (0xFCC0) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }

    return

/*
    final Integer raw
    //final String  value
    switch (descMap.attrInt as Integer) {
        case 0x040a:    // E1 battery - read only
            raw = hexStrToUnsignedInt(descMap.value)
            thermostatEvent("battery", raw, raw)
            break
        case 0x00F7 :   // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
            parseXiaomiClusterThermostatTags(tags)
            break
        case 0x0271:    // result['system_mode'] = {1: 'heat', 0: 'off'}[value]; (heating state) - rw
            raw = hexStrToUnsignedInt(descMap.value)
            value = SystemModeOpts.options[raw as int]
            thermostatEvent("systemMode", value, raw)
            break;
        case 0x0272:    // result['preset'] = {2: 'away', 1: 'auto', 0: 'manual'}[value]; - rw  ['manual', 'auto', 'holiday']
            raw = hexStrToUnsignedInt(descMap.value)
            value = PresetOpts.options[raw as int]
            thermostatEvent("preset", value, raw)
            break;
        case 0x0273:    // result['window_detection'] = {1: 'ON', 0: 'OFF'}[value]; - rw
            raw = hexStrToUnsignedInt(descMap.value)
            value = WindowDetectionOpts.options[raw as int]
            thermostatEvent("windowOpenDetection", value, raw)
            break;
        case 0x0274:    // result['valve_detection'] = {1: 'ON', 0: 'OFF'}[value]; -rw
            raw = hexStrToUnsignedInt(descMap.value)
            value = ValveDetectionOpts.options[raw as int]
            thermostatEvent("valveDetection", value, raw)
            break;
        case 0x0275:    // result['valve_alarm'] = {1: true, 0: false}[value]; - read only!
            raw = hexStrToUnsignedInt(descMap.value)
            value = ValveAlarmOpts.options[raw as int]
            thermostatEvent("valveAlarm", value, raw)
            break;
        case 0x0277:    // result['child_lock'] = {1: 'LOCK', 0: 'UNLOCK'}[value]; - rw
            raw = hexStrToUnsignedInt(descMap.value)
            value = ChildLockOpts.options[raw as int]
            thermostatEvent("childLock", value, raw)
            break;
        case 0x0279:    // result['away_preset_temperature'] = (value / 100).toFixed(1); - rw
            raw = hexStrToUnsignedInt(descMap.value)
            value = raw / 100
            thermostatEvent("awayPresetTemperature", value, raw)
            break;
        case 0x027a:    // result['window_open'] = {1: true, 0: false}[value]; - read only
            raw = hexStrToUnsignedInt(descMap.value)
            value = WindowOpenOpts.options[raw as int]
            thermostatEvent("windowsState", value, raw)
            break;
        case 0x027b:    // result['calibrated'] = {1: true, 0: false}[value]; - read only
            raw = hexStrToUnsignedInt(descMap.value)
            value = CalibratedOpts.options[raw as int]
            thermostatEvent("calibrated", value, raw)
            break;
        case 0x00FF:
            try {
                raw = hexStrToUnsignedInt(descMap.value)
                logDebug "Aqara E1 TRV unknown attribute ${descMap.attrInt} value raw = ${raw}"
            }
            catch (e) {
                logWarn "exception caught while processing Aqara E1 TRV unknown attribute ${descMap.attrInt} descMap.value = ${descMap.value}"
            }
            break;
        case 0x027e:    // result['sensor'] = {1: 'external', 0: 'internal'}[value]; - read only?
            raw = hexStrToUnsignedInt(descMap.value)
            value = SensorOpts.options[raw as int]
            thermostatEvent("sensor", value, raw)
            break;
        default:
            logWarn "parseXiaomiClusterThermostatLib: received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
    */
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
    boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "parseThermostatClusterThermostat: received unknown Thermostat cluster (0x0201) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }

    return

    /*
    switch (descMap.attrInt as Integer) {
        case 0x000:                      // temperature
            logDebug "temperature = ${value/100.0} (raw ${value})"
            handleTemperatureEvent(value/100.0)
            break
        case 0x0011:                      // cooling setpoint
            logInfo "cooling setpoint = ${value/100.0} (raw ${value})"
            break
        case 0x0012:                      // heating setpoint
            logDebug "heating setpoint = ${value/100.0} (raw ${value})"
            sendHeatingSetpointEvent(value/100.0)
            break
        case 0x001b:                      // mode
            logInfo "controlledSequenceOfOperation = ${value} (raw ${value})"
            break
        case 0x001c:                      // mode
            logInfo "mode = ${value} (raw ${value})"
            break
        case 0x001e:                      // thermostatRunMode
            logInfo "thermostatRunMode = ${value} (raw ${value})"
            break
        case 0x0020:                      // battery
            logInfo "battery = ${value} (raw ${value})"
            break
        case 0x0023:                      // thermostatHoldMode
            logInfo "thermostatHoldMode = ${value} (raw ${value})"
            break
        case 0x0029:                      // thermostatOperatingState
            logInfo "thermostatOperatingState = ${value} (raw ${value})"
            break
        case 0xfff2:    // unknown
            logDebug "Aqara E1 TRV unknown attribute ${descMap.attrInt} value raw = ${value}"
            break;
        default:
            log.warn "zigbee received unknown Thermostat cluster (0x0201) attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
    */
}

void customParseFC11Cluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseFC11Cluster: zigbee received Thermostat 0xFC11 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logWarn "customParseFC11Cluster: received unknown Thermostat cluster (0xFC11) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}
import java.math.RoundingMode

//  setHeatingSetpoint thermostat capability standard command
//  1°C steps. (0.5°C setting on the TRV itself, rounded for zigbee interface)
//
void setHeatingSetpoint(final String temperaturePar ) {
    setHeatingSetpoint(temperaturePar.toBigDecimal())
}

void setHeatingSetpoint(final BigDecimal temperaturePar ) {
    BigDecimal temperature = temperaturePar
    logTrace "setHeatingSetpoint(${temperature}) called!"
    BigDecimal previousSetpoint = (device.currentState('heatingSetpoint')?.value ?: 0.0G).toBigDecimal()
    BigDecimal tempDouble = temperature
    //logDebug "setHeatingSetpoint temperature = ${temperature}  as int = ${temperature as int} (previousSetpointt = ${previousSetpoint})"
    /* groovylint-disable-next-line ConstantIfExpression */
    if (true) {
        //logDebug "0.5 C correction of the heating setpoint${temperature}"
        log.trace "tempDouble = ${tempDouble}"
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

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
void sendHeatingSetpointEvent(temperature) {
    tempDouble = safeToDouble(temperature)
    Map eventMap = [name: 'heatingSetpoint',  value: tempDouble, unit: '\u00B0C']
    eventMap.descriptionText = "heatingSetpoint is ${tempDouble}"
    if (state.states['isRefresh'] == true) {
        eventMap.descriptionText += ' [refresh]'
        eventMap.isStateChange = true   // force event to be sent
    }
    sendEvent(eventMap)
    if (eventMap.descriptionText != null) { logInfo "${eventMap.descriptionText}" }
/*
    eventMap = [name: "thermostatSetpoint", value: tempDouble, unit: '\u00B0C']
    eventMap.descriptionText = null
*/
    eventMap.name = 'thermostatSetpoint'
    logDebug "sending event ${eventMap}"
    sendEvent(eventMap)
    updateDataValue('lastRunningMode', 'heat')
}

// thermostat capability standard command
// do nothing in TRV - just send an event
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
void setCoolingSetpoint(temperature) {
    logDebug "setCoolingSetpoint(${temperature}) called!"
    /* groovylint-disable-next-line ParameterReassignment */
    temperature = Math.round(temperature * 2) / 2
    String descText = "coolingSetpoint is set to ${temperature} \u00B0C"
    sendEvent(name: 'coolingSetpoint', value: temperature, unit: '\u00B0C', descriptionText: descText, isDigital: true)
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
    List nativelySupportedModes = getAttributesMap('thermostatMode')?.map?.values() ?: []
    List systemModes = getAttributesMap('systemMode')?.map?.values() ?: []
    List ecoModes = getAttributesMap('ecoMode')?.map?.values() ?: []
    List emergencyHeatingModes = getAttributesMap('emergencyHeating')?.map?.values() ?: []

    logDebug "setThermostatMode: sending setThermostatMode(${mode}). Natively supported: ${nativelySupportedModes}"

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
            if ('emergency heat' in nativelySupportedModes) {
                break
            }
            // look for a dedicated 'emergencyMode' deviceProfile attribute       (BRT-100)
            if ('on' in emergencyHeatingModes)  {
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
            if ('off' in nativelySupportedModes) {
                break
            }
            logDebug "setThermostatMode: pre-processing: switching to 'off' mode"
            // if the 'off' mode is not directly supported, try substituting it with 'eco' mode
            if ('eco' in nativelySupportedModes) {
                logInfo 'setThermostatMode: pre-processing: switching to eco mode instead'
                mode = 'eco'
                break
            }
            // look for a dedicated 'ecoMode' deviceProfile attribute       (BRT-100)
            if ('on' in ecoModes)  {
                logInfo 'setThermostatMode: pre-processing: switching the eco mode on'
                sendAttribute('ecoMode', 'on')
                return
            }
            // look for a dedicated 'systemMode' attribute with map 'off' (Aqara E1)
            if ('off' in systemModes)  {
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

static String timeToHMS(final int time) {
    int hours = (time / 3600) as int
    int minutes = ((time % 3600) / 60) as int
    int seconds = time % 60
    return "${hours}h ${minutes}m ${seconds}s"
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
    checkDriverVersion()
    List<String> cmds = []
    setRefreshRequest()

    if (DEVICE.refresh != null && DEVICE.refresh != []) {
        logDebug "autoPollThermostat: calling DEVICE.refresh() ${DEVICE.refresh}"
        DEVICE.refresh.each {
            logTrace "autoPollThermostat: calling ${it}()"
            cmds += "${it}"()
        }
        if (cmds != null && cmds != [] ) {
            sendZigbeeCommands(cmds)
        }
        else {
            clearRefreshRequest()     // nothing to poll
        }
        return
    }
}

//
// called from updated() in the main code
void updatedThermostat() {
    //ArrayList<String> cmds = []
    logDebug 'updatedThermostat: ...'
    //
    if (settings?.forcedProfile != null) {
        //logDebug "current state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${getProfileKey(settings?.forcedProfile)}"
        if (getProfileKey(settings?.forcedProfile) != state.deviceProfile) {
            logWarn "changing the device profile from ${state.deviceProfile} to ${getProfileKey(settings?.forcedProfile)}"
            state.deviceProfile = getProfileKey(settings?.forcedProfile)
            //initializeVars(fullInit = false)
            initVarsThermostat(fullInit = false)
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
    //
    /*
    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }
    */
}

List<String> refreshAqaraE1() {
    List<String> cmds = []
    //cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)                                         // battery voltage (E1 does not send percentage)
    cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0011, 0x0012, 0x001B, 0x001C], [:], delay = 3500)       // 0x0000 = local temperature, 0x0011 = cooling setpoint, 0x0012 = heating setpoint, 0x001B = controlledSequenceOfOperation, 0x001C = system mode (enum8 )
    cmds += zigbee.readAttribute(0xFCC0, [0x0271, 0x0272, 0x0273, 0x0274, 0x0275, 0x0277, 0x0279, 0x027A, 0x027B, 0x027E], [mfgCode: 0x115F], delay = 3500)
    cmds += zigbee.readAttribute(0xFCC0, 0x040a, [mfgCode: 0x115F], delay = 500)
    // stock Generic Zigbee Thermostat Refresh answer:
    // raw:F669010201441C0030011E008600000029640A2900861B0000300412000029540B110000299808, dni:F669, endpoint:01, cluster:0201, size:44, attrId:001C, encoding:30, command:01, value:01, clusterInt:513, attrInt:28, additionalAttrs:[[status:86, attrId:001E, attrInt:30], [value:0A64, encoding:29, attrId:0000, consumedBytes:5, attrInt:0], [status:86, attrId:0029, attrInt:41], [value:04, encoding:30, attrId:001B, consumedBytes:4, attrInt:27], [value:0B54, encoding:29, attrId:0012, consumedBytes:5, attrInt:18], [value:0898, encoding:29, attrId:0011, consumedBytes:5, attrInt:17]]
    // conclusion : binding and reporting configuration for this Aqara E1 thermostat does nothing... We need polling mechanism for faster updates of the internal temperature readings.
    return cmds
}

// TODO - not actually used! pollAqara is called instead! TODO !
List<String> customRefresh() {
    // state.states["isRefresh"] = true is already set in the commonLib
    List<String> cmds = []
    setRefreshRequest()
    if (DEVICE.refresh != null && DEVICE.refresh != []) {
        logDebug "customRefresh: calling DEVICE.refresh methods: ${DEVICE.refresh}"
        DEVICE.refresh.each {
            logTrace "customRefresh: calling ${it}()"
            cmds += "${it}"()
        }
        return cmds
    }
    logDebug "customRefresh: no refresh methods defined for device profile ${getDeviceProfile()}"
    if (cmds == []) { cmds = ['delay 299'] }
    logDebug "customRefresh: ${cmds} "
    return cmds
}

List<String> configureThermostat() {
    List<String> cmds = []
    // TODO !!
    logDebug "configureThermostat() : ${cmds}"
    if (cmds == []) { cmds = ['delay 299'] }    // no ,
    return cmds
}

// called from initializeDevice in the commonLib code
List<String> customInitializeDevice() {
    List<String> cmds = []
    //int intMinTime = 300
    //int intMaxTime = 600    // report temperature every 10 minutes !
    logDebug 'customInitializeDevice() ...'

    if ( getDeviceProfile() == 'SONOFF_TRV') {
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
    }
    else if (getDeviceProfile() == 'AQARA_E1_TRV' ) {
        logDebug 'configuring cluster 0x0201 ...'
        cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0201 {${device.zigbeeId}} {}", 'delay 251', ]
        //cmds += zigbee.configureReporting(0x0201, 0x0012, 0x29, intMinTime as int, intMaxTime as int, 0x01, [:], delay=541)
        //cmds += zigbee.configureReporting(0x0201, 0x0000, 0x29, 20, 120, 0x01, [:], delay=542)

        cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0012 0x29 1 600 {}", 'delay 551', ]
        cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0000 0x29 20 300 {}", 'delay 551', ]
        cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x001C 0x30 1 600 {}", 'delay 551', ]

        cmds +=  zigbee.reportingConfiguration(0x0201, 0x0012, [:], 551)    // read it back - doesn't work
        cmds +=  zigbee.reportingConfiguration(0x0201, 0x0000, [:], 552)    // read it back - doesn't wor
        cmds +=  zigbee.reportingConfiguration(0x0201, 0x001C, [:], 552)    // read it back - doesn't wor
    }
    else {
        logDebug"customInitializeDevice: nothing to initialize for device group ${getDeviceProfile()}"
    }
    logDebug "initializeThermostat() : ${cmds}"
    if (cmds == []) { cmds = ['delay 299',] }
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
void processDeviceEventThermostat(final String name, final valueScaled, final String unitText, final String descText) {
    logTrace "processDeviceEventThermostat(${name}, ${valueScaled}) called"
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
        case 'systemMode' : // Aqara E1
            sendEvent(eventMap)
            logInfo "${descText}"
            if (valueScaled == 'on') {  // should be initialized with 'unknown' value
                sendEvent(name: 'thermostatMode', value: 'heat', isStateChange: true, description: 'TRV systemMode is on')  // TODO - send the last mode instead of 'heat' ?
            }
            else {
                sendEvent(name: 'thermostatMode', value: 'off', isStateChange: true, description: 'TRV systemMode is off')
            }
            break
        case 'ecoMode' :    // BRT-100 - simulate OFF mode ?? or keep the ecoMode on ?
            sendEvent(eventMap)
            logInfo "${descText}"
            if (valueScaled == 'on') {  // ecoMode is on
                sendEvent(name: 'thermostatMode', value: 'eco', isStateChange: true, description: 'BRT-100 ecoMode is on')
                sendEvent(name: 'thermostatOperatingState', value: 'idle', isStateChange: true, description: 'BRT-100 ecoMode is on')
            }
            else {
                sendEvent(name: 'thermostatMode', value: 'heat', isStateChange: true, description: 'BRT-100 ecoMode is off')
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
        sendEvent(name: 'temperature', value: temperature, unit: '\u00B0C', descriptionText: descText, isDigital:true)
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
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', // library marker kkossev.commonLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.commonLib, line 4
    category: 'zigbee', // library marker kkossev.commonLib, line 5
    description: 'Common ZCL Library', // library marker kkossev.commonLib, line 6
    name: 'commonLib', // library marker kkossev.commonLib, line 7
    namespace: 'kkossev', // library marker kkossev.commonLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', // library marker kkossev.commonLib, line 9
    version: '3.0.7', // library marker kkossev.commonLib, line 10
    documentationLink: '' // library marker kkossev.commonLib, line 11
) // library marker kkossev.commonLib, line 12
/* // library marker kkossev.commonLib, line 13
  *  Common ZCL Library // library marker kkossev.commonLib, line 14
  * // library marker kkossev.commonLib, line 15
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.commonLib, line 16
  *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.commonLib, line 17
  * // library marker kkossev.commonLib, line 18
  *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.commonLib, line 19
  * // library marker kkossev.commonLib, line 20
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.commonLib, line 21
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.commonLib, line 22
  *  for the specific language governing permissions and limitations under the License. // library marker kkossev.commonLib, line 23
  * // library marker kkossev.commonLib, line 24
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project). // library marker kkossev.commonLib, line 25
  * For a big portions of code all credits go to Jonathan Bradshaw. // library marker kkossev.commonLib, line 26
  * // library marker kkossev.commonLib, line 27
  * // library marker kkossev.commonLib, line 28
  * ver. 1.0.0  2022-06-18 kkossev  - first beta version // library marker kkossev.commonLib, line 29
  * ver. 2.0.0  2023-05-08 kkossev  - first published version 2.x.x // library marker kkossev.commonLib, line 30
  * ver. 2.1.6  2023-11-06 kkossev  - last update on version 2.x.x // library marker kkossev.commonLib, line 31
  * ver. 3.0.0  2023-11-16 kkossev  - first version 3.x.x // library marker kkossev.commonLib, line 32
  * ver. 3.0.1  2023-12-06 kkossev  - nfo event renamed to Status; txtEnable and logEnable moved to the custom driver settings; 0xFC11 cluster; logEnable is false by default; checkDriverVersion is called on updated() and on healthCheck(); // library marker kkossev.commonLib, line 33
  * ver. 3.0.2  2023-12-17 kkossev  - configure() changes; Groovy Lint, Format and Fix v3.0.0 // library marker kkossev.commonLib, line 34
  * ver. 3.0.3  2024-03-17 kkossev  - more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization // library marker kkossev.commonLib, line 35
  * ver. 3.0.4  2024-04-02 kkossev  - removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; parseE002Cluster; // library marker kkossev.commonLib, line 36
  * ver. 3.0.5  2024-04-05 kkossev  - button methods bug fix; configure() bug fix; handlePm25Event bug fix; // library marker kkossev.commonLib, line 37
  * ver. 3.0.6  2024-04-08 kkossev  - removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups and level and battery methods to dedicated libs + setLevel bug fix; // library marker kkossev.commonLib, line 38
  * ver. 3.0.7  2024-04-14 kkossev  - (dev. branch) tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr; trace ZDO commands; // library marker kkossev.commonLib, line 39
  * // library marker kkossev.commonLib, line 40
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 41
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib // library marker kkossev.commonLib, line 42
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 43
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 44
  * // library marker kkossev.commonLib, line 45
*/ // library marker kkossev.commonLib, line 46

String commonLibVersion() { '3.0.7' } // library marker kkossev.commonLib, line 48
String commonLibStamp() { '2024/04/14 8:54 PM' } // library marker kkossev.commonLib, line 49

import groovy.transform.Field // library marker kkossev.commonLib, line 51
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 52
import hubitat.device.Protocol // library marker kkossev.commonLib, line 53
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 54
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 55
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 56
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 57
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 58
import java.math.BigDecimal // library marker kkossev.commonLib, line 59

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 61

metadata { // library marker kkossev.commonLib, line 63
        if (_DEBUG) { // library marker kkossev.commonLib, line 64
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 65
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 66
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 67
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 68
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 69
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 70
            ] // library marker kkossev.commonLib, line 71
        } // library marker kkossev.commonLib, line 72

        // common capabilities for all device types // library marker kkossev.commonLib, line 74
        capability 'Configuration' // library marker kkossev.commonLib, line 75
        capability 'Refresh' // library marker kkossev.commonLib, line 76
        capability 'Health Check' // library marker kkossev.commonLib, line 77

        // common attributes for all device types // library marker kkossev.commonLib, line 79
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 80
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 81
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 82

        // common commands for all device types // library marker kkossev.commonLib, line 84
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 85

        if (deviceType in  ['Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 87
            capability 'Switch' // library marker kkossev.commonLib, line 88
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 89
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 90
            } // library marker kkossev.commonLib, line 91
        } // library marker kkossev.commonLib, line 92

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 94
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 95

    preferences { // library marker kkossev.commonLib, line 97
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 98
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 99
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 100

        if (device) { // library marker kkossev.commonLib, line 102
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 103
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 104
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 105
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 106
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 107
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 108
                } // library marker kkossev.commonLib, line 109
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 110
            } // library marker kkossev.commonLib, line 111
        } // library marker kkossev.commonLib, line 112
    } // library marker kkossev.commonLib, line 113
} // library marker kkossev.commonLib, line 114

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 116
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
    defaultValue: 1, // library marker kkossev.commonLib, line 129
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 130
] // library marker kkossev.commonLib, line 131
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 132
    defaultValue: 240, // library marker kkossev.commonLib, line 133
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 134
] // library marker kkossev.commonLib, line 135
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 136
    defaultValue: 0, // library marker kkossev.commonLib, line 137
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 138
] // library marker kkossev.commonLib, line 139

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 141
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 142
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 143
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 144
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 145
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 146
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 147
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 148
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 149
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 150
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 151
] // library marker kkossev.commonLib, line 152

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 154
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 155
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 156
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 157
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 158
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 159
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 160
//boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 161
//boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 162

/** // library marker kkossev.commonLib, line 164
 * Parse Zigbee message // library marker kkossev.commonLib, line 165
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 166
 */ // library marker kkossev.commonLib, line 167
void parse(final String description) { // library marker kkossev.commonLib, line 168
    checkDriverVersion() // library marker kkossev.commonLib, line 169
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 170
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 171
    setHealthStatusOnline() // library marker kkossev.commonLib, line 172

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 174
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 175
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 176
            parseIasMessage(description) // library marker kkossev.commonLib, line 177
        } // library marker kkossev.commonLib, line 178
        else { // library marker kkossev.commonLib, line 179
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 180
        } // library marker kkossev.commonLib, line 181
        return // library marker kkossev.commonLib, line 182
    } // library marker kkossev.commonLib, line 183
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 184
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 185
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 186
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 187
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 188
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 189
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 190
        return // library marker kkossev.commonLib, line 191
    } // library marker kkossev.commonLib, line 192
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 193
        return // library marker kkossev.commonLib, line 194
    } // library marker kkossev.commonLib, line 195
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 196

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 198
    if (isSpammyDeviceReport(descMap)) { return } // library marker kkossev.commonLib, line 199

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 201
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 202
        return // library marker kkossev.commonLib, line 203
    } // library marker kkossev.commonLib, line 204
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 205
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 206
        return // library marker kkossev.commonLib, line 207
    } // library marker kkossev.commonLib, line 208
    // // library marker kkossev.commonLib, line 209
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 210
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 211
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 212

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 214
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 215
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 216
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 217
            break // library marker kkossev.commonLib, line 218
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 219
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 220
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 221
            break // library marker kkossev.commonLib, line 222
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 223
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 224
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 225
            break // library marker kkossev.commonLib, line 226
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 227
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 228
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 229
            break // library marker kkossev.commonLib, line 230
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 231
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 232
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 233
            break // library marker kkossev.commonLib, line 234
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 235
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 236
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 237
            break // library marker kkossev.commonLib, line 238
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 239
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 240
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 241
            break // library marker kkossev.commonLib, line 242
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 243
            parseAnalogInputCluster(descMap, description) // library marker kkossev.commonLib, line 244
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map, description) } // library marker kkossev.commonLib, line 245
            break // library marker kkossev.commonLib, line 246
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 247
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 248
            break // library marker kkossev.commonLib, line 249
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 250
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 251
            break // library marker kkossev.commonLib, line 252
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 253
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 254
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 255
            break // library marker kkossev.commonLib, line 256
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 257
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 258
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 259
            break // library marker kkossev.commonLib, line 260
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 261
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 262
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 263
            break // library marker kkossev.commonLib, line 264
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 265
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 266
            break // library marker kkossev.commonLib, line 267
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 268
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 269
            break // library marker kkossev.commonLib, line 270
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 271
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 272
            break // library marker kkossev.commonLib, line 273
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 274
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 275
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 276
            break // library marker kkossev.commonLib, line 277
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 278
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 279
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 280
            break // library marker kkossev.commonLib, line 281
        case 0xE002 : // library marker kkossev.commonLib, line 282
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 283
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 284
            break // library marker kkossev.commonLib, line 285
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 286
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 287
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 288
            break // library marker kkossev.commonLib, line 289
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 290
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 291
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 292
            break // library marker kkossev.commonLib, line 293
        case 0xFC11 :                                    // Sonoff // library marker kkossev.commonLib, line 294
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 295
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 296
            break // library marker kkossev.commonLib, line 297
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 298
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 299
            break // library marker kkossev.commonLib, line 300
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 301
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 302
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 303
            break // library marker kkossev.commonLib, line 304
        default: // library marker kkossev.commonLib, line 305
            if (settings.logEnable) { // library marker kkossev.commonLib, line 306
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 307
            } // library marker kkossev.commonLib, line 308
            break // library marker kkossev.commonLib, line 309
    } // library marker kkossev.commonLib, line 310
} // library marker kkossev.commonLib, line 311

boolean isChattyDeviceReport(final Map descMap)  { // library marker kkossev.commonLib, line 313
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 314
    if (this.respondsTo('isSpammyDPsToNotTrace')) { // library marker kkossev.commonLib, line 315
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 316
    } // library marker kkossev.commonLib, line 317
    return false // library marker kkossev.commonLib, line 318
} // library marker kkossev.commonLib, line 319

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 321
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 322
    if (this.respondsTo('isSpammyDPsToIgnore')) { // library marker kkossev.commonLib, line 323
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 324
    } // library marker kkossev.commonLib, line 325
    return false // library marker kkossev.commonLib, line 326
} // library marker kkossev.commonLib, line 327

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 329
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 330
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 331
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 332
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 333
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 334
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 335
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 336
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 337
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 338
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 339
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 340
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 341
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 342
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 343
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 344
] // library marker kkossev.commonLib, line 345

/** // library marker kkossev.commonLib, line 347
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 348
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 349
 */ // library marker kkossev.commonLib, line 350
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 351
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 352
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 353
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 354
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 355
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 356
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 357
    switch (clusterId) { // library marker kkossev.commonLib, line 358
        case 0x0005 : // library marker kkossev.commonLib, line 359
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 360
            break // library marker kkossev.commonLib, line 361
        case 0x0006 : // library marker kkossev.commonLib, line 362
            if (state.stats == null) { state.stats = [:] } ; state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 363
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 364
            break // library marker kkossev.commonLib, line 365
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 366
            if (state.stats == null) { state.stats = [:] } ; state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 367
            if (settings?.logEnable) { log.info "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 368
            break // library marker kkossev.commonLib, line 369
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 370
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 371
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 372
            break // library marker kkossev.commonLib, line 373
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 374
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 375
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 376
            if (settings?.logEnable) { log.info "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 377
            break // library marker kkossev.commonLib, line 378
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 379
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 380
            break // library marker kkossev.commonLib, line 381
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 382
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 383
            if (settings?.logEnable) { log.info "${clusterInfo}" } // library marker kkossev.commonLib, line 384
            break // library marker kkossev.commonLib, line 385
        default : // library marker kkossev.commonLib, line 386
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 387
            break // library marker kkossev.commonLib, line 388
    } // library marker kkossev.commonLib, line 389
} // library marker kkossev.commonLib, line 390

/** // library marker kkossev.commonLib, line 392
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 393
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 394
 */ // library marker kkossev.commonLib, line 395
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 396
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 397
    switch (commandId) { // library marker kkossev.commonLib, line 398
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 399
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 400
            break // library marker kkossev.commonLib, line 401
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 402
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 403
            break // library marker kkossev.commonLib, line 404
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 405
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 406
            break // library marker kkossev.commonLib, line 407
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 408
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 409
            break // library marker kkossev.commonLib, line 410
        case 0x0B: // default command response // library marker kkossev.commonLib, line 411
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 412
            break // library marker kkossev.commonLib, line 413
        default: // library marker kkossev.commonLib, line 414
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 415
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 416
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 417
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 418
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 419
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 420
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 421
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 422
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 423
            } // library marker kkossev.commonLib, line 424
            break // library marker kkossev.commonLib, line 425
    } // library marker kkossev.commonLib, line 426
} // library marker kkossev.commonLib, line 427

/** // library marker kkossev.commonLib, line 429
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 430
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 431
 */ // library marker kkossev.commonLib, line 432
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 433
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 434
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 435
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 436
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 437
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 438
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 439
    } // library marker kkossev.commonLib, line 440
    else { // library marker kkossev.commonLib, line 441
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 442
    } // library marker kkossev.commonLib, line 443
} // library marker kkossev.commonLib, line 444

/** // library marker kkossev.commonLib, line 446
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 447
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 448
 */ // library marker kkossev.commonLib, line 449
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 450
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 451
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 452
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 453
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 454
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 455
    } // library marker kkossev.commonLib, line 456
    else { // library marker kkossev.commonLib, line 457
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 458
    } // library marker kkossev.commonLib, line 459
} // library marker kkossev.commonLib, line 460

/** // library marker kkossev.commonLib, line 462
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 463
 */ // library marker kkossev.commonLib, line 464
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 465
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 466
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 467
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 468
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 469
        state.reportingEnabled = true // library marker kkossev.commonLib, line 470
    } // library marker kkossev.commonLib, line 471
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 472
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 473
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 474
    } else { // library marker kkossev.commonLib, line 475
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 476
    } // library marker kkossev.commonLib, line 477
} // library marker kkossev.commonLib, line 478

/** // library marker kkossev.commonLib, line 480
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 481
 */ // library marker kkossev.commonLib, line 482
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 483
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 484
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 485
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 486
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 487
    if (status == 0) { // library marker kkossev.commonLib, line 488
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 489
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 490
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 491
        int delta = 0 // library marker kkossev.commonLib, line 492
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 493
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 494
        } // library marker kkossev.commonLib, line 495
        else { // library marker kkossev.commonLib, line 496
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 497
        } // library marker kkossev.commonLib, line 498
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 499
    } // library marker kkossev.commonLib, line 500
    else { // library marker kkossev.commonLib, line 501
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 502
    } // library marker kkossev.commonLib, line 503
} // library marker kkossev.commonLib, line 504

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 506
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 507
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 508
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 509
        return false // library marker kkossev.commonLib, line 510
    } // library marker kkossev.commonLib, line 511
    // execute the customHandler function // library marker kkossev.commonLib, line 512
    boolean result = false // library marker kkossev.commonLib, line 513
    try { // library marker kkossev.commonLib, line 514
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 515
    } // library marker kkossev.commonLib, line 516
    catch (e) { // library marker kkossev.commonLib, line 517
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 518
        return false // library marker kkossev.commonLib, line 519
    } // library marker kkossev.commonLib, line 520
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 521
    return result // library marker kkossev.commonLib, line 522
} // library marker kkossev.commonLib, line 523

/** // library marker kkossev.commonLib, line 525
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 526
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 527
 */ // library marker kkossev.commonLib, line 528
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 529
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 530
    final String commandId = data[0] // library marker kkossev.commonLib, line 531
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 532
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 533
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 534
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 535
    } else { // library marker kkossev.commonLib, line 536
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 537
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 538
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 539
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 540
        } // library marker kkossev.commonLib, line 541
    } // library marker kkossev.commonLib, line 542
} // library marker kkossev.commonLib, line 543

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 545
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 546
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 547
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 548

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 550
    0x00: 'Success', // library marker kkossev.commonLib, line 551
    0x01: 'Failure', // library marker kkossev.commonLib, line 552
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 553
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 554
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 555
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 556
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 557
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 558
    0x88: 'Read Only', // library marker kkossev.commonLib, line 559
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 560
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 561
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 562
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 563
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 564
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 565
    0x94: 'Time out', // library marker kkossev.commonLib, line 566
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 567
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 568
] // library marker kkossev.commonLib, line 569

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 571
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 572
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 573
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 574
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 575
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 576
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 577
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 578
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 579
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 580
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 581
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 582
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 583
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 584
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 585
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 586
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 587
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 588
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 589
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 590
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 591
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 592
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 593
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 594
] // library marker kkossev.commonLib, line 595

void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 597
    if (xiaomiLibVersion() != null) { parseXiaomiClusterLib(descMap) } else { logWarn 'Xiaomi cluster 0xFCC0' } // library marker kkossev.commonLib, line 598
} // library marker kkossev.commonLib, line 599

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 601
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 602
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 603
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 604
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 605
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 606
    return avg // library marker kkossev.commonLib, line 607
} // library marker kkossev.commonLib, line 608

/* // library marker kkossev.commonLib, line 610
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 611
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 612
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 613
*/ // library marker kkossev.commonLib, line 614
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 615

/** // library marker kkossev.commonLib, line 617
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 618
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 619
 */ // library marker kkossev.commonLib, line 620
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 621
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 622
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 623
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 624
        case 0x0000: // library marker kkossev.commonLib, line 625
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 626
            break // library marker kkossev.commonLib, line 627
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 628
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 629
            if (isPing) { // library marker kkossev.commonLib, line 630
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 631
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 632
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 633
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 634
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 635
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 636
                    sendRttEvent() // library marker kkossev.commonLib, line 637
                } // library marker kkossev.commonLib, line 638
                else { // library marker kkossev.commonLib, line 639
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 640
                } // library marker kkossev.commonLib, line 641
                state.states['isPing'] = false // library marker kkossev.commonLib, line 642
            } // library marker kkossev.commonLib, line 643
            else { // library marker kkossev.commonLib, line 644
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 645
            } // library marker kkossev.commonLib, line 646
            break // library marker kkossev.commonLib, line 647
        case 0x0004: // library marker kkossev.commonLib, line 648
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 649
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 650
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 651
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 652
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 653
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 654
            } // library marker kkossev.commonLib, line 655
            break // library marker kkossev.commonLib, line 656
        case 0x0005: // library marker kkossev.commonLib, line 657
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 658
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 659
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 660
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 661
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 662
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 663
            } // library marker kkossev.commonLib, line 664
            break // library marker kkossev.commonLib, line 665
        case 0x0007: // library marker kkossev.commonLib, line 666
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 667
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 668
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 669
            break // library marker kkossev.commonLib, line 670
        case 0xFFDF: // library marker kkossev.commonLib, line 671
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 672
            break // library marker kkossev.commonLib, line 673
        case 0xFFE2: // library marker kkossev.commonLib, line 674
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 675
            break // library marker kkossev.commonLib, line 676
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 677
            logDebug "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 678
            break // library marker kkossev.commonLib, line 679
        case 0xFFFE: // library marker kkossev.commonLib, line 680
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 681
            break // library marker kkossev.commonLib, line 682
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 683
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 684
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 685
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 686
            break // library marker kkossev.commonLib, line 687
        default: // library marker kkossev.commonLib, line 688
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 689
            break // library marker kkossev.commonLib, line 690
    } // library marker kkossev.commonLib, line 691
} // library marker kkossev.commonLib, line 692

// power cluster            0x0001 // library marker kkossev.commonLib, line 694
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 695
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 696
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 697
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 698
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 699
    } // library marker kkossev.commonLib, line 700
    if (this.respondsTo('customParsePowerCluster')) { // library marker kkossev.commonLib, line 701
        customParsePowerCluster(descMap) // library marker kkossev.commonLib, line 702
    } // library marker kkossev.commonLib, line 703
    else { // library marker kkossev.commonLib, line 704
        logDebug "zigbee received Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 705
    } // library marker kkossev.commonLib, line 706
} // library marker kkossev.commonLib, line 707

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 709
void parseIdentityCluster(final Map descMap) { logDebug 'unprocessed parseIdentityCluster' } // library marker kkossev.commonLib, line 710

void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 712
    if (this.respondsTo('customParseScenesCluster')) { customParseScenesCluster(descMap) } else { logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 713
} // library marker kkossev.commonLib, line 714

void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 716
    if (this.respondsTo('customParseGroupsCluster')) { customParseGroupsCluster(descMap) } else { logWarn "unprocessed GroupsCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 717
} // library marker kkossev.commonLib, line 718

/* // library marker kkossev.commonLib, line 720
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 721
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 722
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 723
*/ // library marker kkossev.commonLib, line 724

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 726
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 727
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 728
    } // library marker kkossev.commonLib, line 729
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 730
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 731
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 732
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 733
    } // library marker kkossev.commonLib, line 734
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 735
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 736
    } // library marker kkossev.commonLib, line 737
    else { // library marker kkossev.commonLib, line 738
        if (descMap.attrId != null) { logWarn "parseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.commonLib, line 739
        else { logDebug "parseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 740
    } // library marker kkossev.commonLib, line 741
} // library marker kkossev.commonLib, line 742

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 744
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 745
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 746

void toggle() { // library marker kkossev.commonLib, line 748
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 749
    String state = '' // library marker kkossev.commonLib, line 750
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 751
        state = 'on' // library marker kkossev.commonLib, line 752
    } // library marker kkossev.commonLib, line 753
    else { // library marker kkossev.commonLib, line 754
        state = 'off' // library marker kkossev.commonLib, line 755
    } // library marker kkossev.commonLib, line 756
    descriptionText += state // library marker kkossev.commonLib, line 757
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 758
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 759
} // library marker kkossev.commonLib, line 760

void off() { // library marker kkossev.commonLib, line 762
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 763
        customOff() // library marker kkossev.commonLib, line 764
        return // library marker kkossev.commonLib, line 765
    } // library marker kkossev.commonLib, line 766
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 767
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 768
        return // library marker kkossev.commonLib, line 769
    } // library marker kkossev.commonLib, line 770
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 771
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 772
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 773
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 774
        if (currentState == 'off') { // library marker kkossev.commonLib, line 775
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 776
        } // library marker kkossev.commonLib, line 777
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 778
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 779
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 780
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 781
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 782
    } // library marker kkossev.commonLib, line 783
    /* // library marker kkossev.commonLib, line 784
    else { // library marker kkossev.commonLib, line 785
        if (currentState != 'off') { // library marker kkossev.commonLib, line 786
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 787
        } // library marker kkossev.commonLib, line 788
        else { // library marker kkossev.commonLib, line 789
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 790
            return // library marker kkossev.commonLib, line 791
        } // library marker kkossev.commonLib, line 792
    } // library marker kkossev.commonLib, line 793
    */ // library marker kkossev.commonLib, line 794

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 796
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 797
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 798
} // library marker kkossev.commonLib, line 799

void on() { // library marker kkossev.commonLib, line 801
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 802
        customOn() // library marker kkossev.commonLib, line 803
        return // library marker kkossev.commonLib, line 804
    } // library marker kkossev.commonLib, line 805
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 806
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 807
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 808
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 809
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 810
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 811
        } // library marker kkossev.commonLib, line 812
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 813
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 814
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 815
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 816
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 817
    } // library marker kkossev.commonLib, line 818
    /* // library marker kkossev.commonLib, line 819
    else { // library marker kkossev.commonLib, line 820
        if (currentState != 'on') { // library marker kkossev.commonLib, line 821
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 822
        } // library marker kkossev.commonLib, line 823
        else { // library marker kkossev.commonLib, line 824
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 825
            return // library marker kkossev.commonLib, line 826
        } // library marker kkossev.commonLib, line 827
    } // library marker kkossev.commonLib, line 828
    */ // library marker kkossev.commonLib, line 829
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 830
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 831
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 832
} // library marker kkossev.commonLib, line 833

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 835
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 836
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 837
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 838
    } // library marker kkossev.commonLib, line 839
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 840
    Map map = [:] // library marker kkossev.commonLib, line 841
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 842
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 843
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 844
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 845
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 846
        return // library marker kkossev.commonLib, line 847
    } // library marker kkossev.commonLib, line 848
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 849
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 850
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 851
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 852
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 853
        state.states['debounce'] = true // library marker kkossev.commonLib, line 854
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 855
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 856
    } else { // library marker kkossev.commonLib, line 857
        state.states['debounce'] = true // library marker kkossev.commonLib, line 858
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 859
    } // library marker kkossev.commonLib, line 860
    map.name = 'switch' // library marker kkossev.commonLib, line 861
    map.value = value // library marker kkossev.commonLib, line 862
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 863
    if (isRefresh) { // library marker kkossev.commonLib, line 864
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 865
        map.isStateChange = true // library marker kkossev.commonLib, line 866
    } else { // library marker kkossev.commonLib, line 867
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 868
    } // library marker kkossev.commonLib, line 869
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 870
    sendEvent(map) // library marker kkossev.commonLib, line 871
    clearIsDigital() // library marker kkossev.commonLib, line 872
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 873
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 874
    } // library marker kkossev.commonLib, line 875
} // library marker kkossev.commonLib, line 876

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 878
    '0': 'switch off', // library marker kkossev.commonLib, line 879
    '1': 'switch on', // library marker kkossev.commonLib, line 880
    '2': 'switch last state' // library marker kkossev.commonLib, line 881
] // library marker kkossev.commonLib, line 882

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 884
    '0': 'toggle', // library marker kkossev.commonLib, line 885
    '1': 'state', // library marker kkossev.commonLib, line 886
    '2': 'momentary' // library marker kkossev.commonLib, line 887
] // library marker kkossev.commonLib, line 888

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 890
    Map descMap = [:] // library marker kkossev.commonLib, line 891
    try { // library marker kkossev.commonLib, line 892
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 893
    } // library marker kkossev.commonLib, line 894
    catch (e1) { // library marker kkossev.commonLib, line 895
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 896
        // try alternative custom parsing // library marker kkossev.commonLib, line 897
        descMap = [:] // library marker kkossev.commonLib, line 898
        try { // library marker kkossev.commonLib, line 899
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 900
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 901
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 902
            } // library marker kkossev.commonLib, line 903
        } // library marker kkossev.commonLib, line 904
        catch (e2) { // library marker kkossev.commonLib, line 905
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 906
            return [:] // library marker kkossev.commonLib, line 907
        } // library marker kkossev.commonLib, line 908
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 909
    } // library marker kkossev.commonLib, line 910
    return descMap // library marker kkossev.commonLib, line 911
} // library marker kkossev.commonLib, line 912

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 914
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 915
        return false // library marker kkossev.commonLib, line 916
    } // library marker kkossev.commonLib, line 917
    // try to parse ... // library marker kkossev.commonLib, line 918
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 919
    Map descMap = [:] // library marker kkossev.commonLib, line 920
    try { // library marker kkossev.commonLib, line 921
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 922
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 923
    } // library marker kkossev.commonLib, line 924
    catch (e) { // library marker kkossev.commonLib, line 925
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 926
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 927
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 928
        return true // library marker kkossev.commonLib, line 929
    } // library marker kkossev.commonLib, line 930

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 932
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 933
    } // library marker kkossev.commonLib, line 934
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 935
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 936
    } // library marker kkossev.commonLib, line 937
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 938
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 939
    } // library marker kkossev.commonLib, line 940
    else { // library marker kkossev.commonLib, line 941
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 942
        return false // library marker kkossev.commonLib, line 943
    } // library marker kkossev.commonLib, line 944
    return true    // processed // library marker kkossev.commonLib, line 945
} // library marker kkossev.commonLib, line 946

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 948
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 949
  /* // library marker kkossev.commonLib, line 950
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 951
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 952
        return true // library marker kkossev.commonLib, line 953
    } // library marker kkossev.commonLib, line 954
*/ // library marker kkossev.commonLib, line 955
    Map descMap = [:] // library marker kkossev.commonLib, line 956
    try { // library marker kkossev.commonLib, line 957
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 958
    } // library marker kkossev.commonLib, line 959
    catch (e1) { // library marker kkossev.commonLib, line 960
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 961
        // try alternative custom parsing // library marker kkossev.commonLib, line 962
        descMap = [:] // library marker kkossev.commonLib, line 963
        try { // library marker kkossev.commonLib, line 964
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 965
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 966
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 967
            } // library marker kkossev.commonLib, line 968
        } // library marker kkossev.commonLib, line 969
        catch (e2) { // library marker kkossev.commonLib, line 970
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 971
            return true // library marker kkossev.commonLib, line 972
        } // library marker kkossev.commonLib, line 973
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 974
    } // library marker kkossev.commonLib, line 975
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 976
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 977
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 978
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 979
        return false // library marker kkossev.commonLib, line 980
    } // library marker kkossev.commonLib, line 981
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 982
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 983
    // attribute report received // library marker kkossev.commonLib, line 984
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 985
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 986
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 987
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 988
    } // library marker kkossev.commonLib, line 989
    attrData.each { // library marker kkossev.commonLib, line 990
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 991
        //def map = [:] // library marker kkossev.commonLib, line 992
        if (it.status == '86') { // library marker kkossev.commonLib, line 993
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 994
        // TODO - skip parsing? // library marker kkossev.commonLib, line 995
        } // library marker kkossev.commonLib, line 996
        switch (it.cluster) { // library marker kkossev.commonLib, line 997
            case '0000' : // library marker kkossev.commonLib, line 998
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 999
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1000
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1001
                } // library marker kkossev.commonLib, line 1002
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1003
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1004
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1005
                } // library marker kkossev.commonLib, line 1006
                else { // library marker kkossev.commonLib, line 1007
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1008
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1009
                } // library marker kkossev.commonLib, line 1010
                break // library marker kkossev.commonLib, line 1011
            default : // library marker kkossev.commonLib, line 1012
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1013
                break // library marker kkossev.commonLib, line 1014
        } // switch // library marker kkossev.commonLib, line 1015
    } // for each attribute // library marker kkossev.commonLib, line 1016
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1017
} // library marker kkossev.commonLib, line 1018

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1020

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1022
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1023
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1024
    def mode // library marker kkossev.commonLib, line 1025
    String attrName // library marker kkossev.commonLib, line 1026
    if (it.value == null) { // library marker kkossev.commonLib, line 1027
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1028
        return // library marker kkossev.commonLib, line 1029
    } // library marker kkossev.commonLib, line 1030
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1031
    switch (it.attrId) { // library marker kkossev.commonLib, line 1032
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1033
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1034
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1035
            break // library marker kkossev.commonLib, line 1036
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1037
            attrName = 'On Time' // library marker kkossev.commonLib, line 1038
            mode = value // library marker kkossev.commonLib, line 1039
            break // library marker kkossev.commonLib, line 1040
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1041
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1042
            mode = value // library marker kkossev.commonLib, line 1043
            break // library marker kkossev.commonLib, line 1044
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1045
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1046
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1047
            break // library marker kkossev.commonLib, line 1048
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1049
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1050
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1051
            break // library marker kkossev.commonLib, line 1052
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1053
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1054
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1055
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1056
            } // library marker kkossev.commonLib, line 1057
            else { // library marker kkossev.commonLib, line 1058
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1059
            } // library marker kkossev.commonLib, line 1060
            break // library marker kkossev.commonLib, line 1061
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1062
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1063
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1064
            break // library marker kkossev.commonLib, line 1065
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1066
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1067
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1068
            break // library marker kkossev.commonLib, line 1069
        default : // library marker kkossev.commonLib, line 1070
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1071
            return // library marker kkossev.commonLib, line 1072
    } // library marker kkossev.commonLib, line 1073
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1074
} // library marker kkossev.commonLib, line 1075

void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1077
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1078
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1079
    } // library marker kkossev.commonLib, line 1080
    else if (this.respondsTo('levelLibParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1081
        levelLibParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1082
    } // library marker kkossev.commonLib, line 1083
    else { // library marker kkossev.commonLib, line 1084
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1085
    } // library marker kkossev.commonLib, line 1086
} // library marker kkossev.commonLib, line 1087

String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1089
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1090
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1091
} // library marker kkossev.commonLib, line 1092

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1094
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1095
} // library marker kkossev.commonLib, line 1096

void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1098
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1099
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1100
    } // library marker kkossev.commonLib, line 1101
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1102
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1103
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1104
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1105
    } // library marker kkossev.commonLib, line 1106
    else { // library marker kkossev.commonLib, line 1107
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1108
    } // library marker kkossev.commonLib, line 1109
} // library marker kkossev.commonLib, line 1110

void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1112
    if (this.respondsTo('customParseIlluminanceCluster')) { customParseIlluminanceCluster(descMap) } else { logWarn "unprocessed Illuminance attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 1113
} // library marker kkossev.commonLib, line 1114

// Temperature Measurement Cluster 0x0402 // library marker kkossev.commonLib, line 1116
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1117
    if (this.respondsTo('customParseTemperatureCluster')) { // library marker kkossev.commonLib, line 1118
        customParseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 1119
    } // library marker kkossev.commonLib, line 1120
    else { // library marker kkossev.commonLib, line 1121
        logWarn "unprocessed Temperature attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1122
    } // library marker kkossev.commonLib, line 1123
} // library marker kkossev.commonLib, line 1124

// Humidity Measurement Cluster 0x0405 // library marker kkossev.commonLib, line 1126
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1127
    if (this.respondsTo('customParseHumidityCluster')) { // library marker kkossev.commonLib, line 1128
        customParseHumidityCluster(descMap) // library marker kkossev.commonLib, line 1129
    } // library marker kkossev.commonLib, line 1130
    else { // library marker kkossev.commonLib, line 1131
        logWarn "unprocessed Humidity attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1132
    } // library marker kkossev.commonLib, line 1133
} // library marker kkossev.commonLib, line 1134

// Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1136
void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1137
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { logWarn 'parseElectricalMeasureCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1138
} // library marker kkossev.commonLib, line 1139

// Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1141
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1142
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { logWarn 'parseMeteringCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1143
} // library marker kkossev.commonLib, line 1144

// pm2.5 // library marker kkossev.commonLib, line 1146
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1147
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1148
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1149
    /* groovylint-disable-next-line NoFloat */ // library marker kkossev.commonLib, line 1150
    float floatValue  = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1151
    if (this.respondsTo('handlePm25Event')) { // library marker kkossev.commonLib, line 1152
        handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1153
    } // library marker kkossev.commonLib, line 1154
    else { // library marker kkossev.commonLib, line 1155
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1156
    } // library marker kkossev.commonLib, line 1157
} // library marker kkossev.commonLib, line 1158

// Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1160
void parseAnalogInputCluster(final Map descMap, String description=null) { // library marker kkossev.commonLib, line 1161
    if (this.respondsTo('customParseAnalogInputCluster')) { // library marker kkossev.commonLib, line 1162
        customParseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1163
    } // library marker kkossev.commonLib, line 1164
    else if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 1165
        customParseAnalogInputClusterDescription(description)                   // ZigUSB // library marker kkossev.commonLib, line 1166
    } // library marker kkossev.commonLib, line 1167
    else if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1168
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1169
    } // library marker kkossev.commonLib, line 1170
    else { // library marker kkossev.commonLib, line 1171
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1172
    } // library marker kkossev.commonLib, line 1173
} // library marker kkossev.commonLib, line 1174

// Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1176
void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1177
    if (this.respondsTo('customParseMultistateInputCluster')) { customParseMultistateInputCluster(descMap) } else { logWarn "parseMultistateInputCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1178
} // library marker kkossev.commonLib, line 1179

// Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1181
void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1182
    if (this.respondsTo('customParseWindowCoveringCluster')) { customParseWindowCoveringCluster(descMap) } else { logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1183
} // library marker kkossev.commonLib, line 1184

// thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1186
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1187
    if (this.respondsTo('customParseThermostatCluster')) { customParseThermostatCluster(descMap) } else { logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1188
} // library marker kkossev.commonLib, line 1189

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1191
    if (this.respondsTo('customParseFC11Cluster')) { customParseFC11Cluster(descMap) } else { logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1192
} // library marker kkossev.commonLib, line 1193

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1195
    if (this.respondsTo('customParseE002Cluster')) { customParseE002Cluster(descMap) } else { logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }    // radars // library marker kkossev.commonLib, line 1196
} // library marker kkossev.commonLib, line 1197

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1199
    if (this.respondsTo('customParseEC03Cluster')) { customParseEC03Cluster(descMap) } else { logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }   // radars // library marker kkossev.commonLib, line 1200
} // library marker kkossev.commonLib, line 1201

/* // library marker kkossev.commonLib, line 1203
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1204
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1205
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1206
*/ // library marker kkossev.commonLib, line 1207
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1208
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1209
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1210

// Tuya Commands // library marker kkossev.commonLib, line 1212
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 1213
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 1214
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 1215
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 1216
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 1217

// tuya DP type // library marker kkossev.commonLib, line 1219
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 1220
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 1221
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 1222
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 1223
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 1224
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 1225

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 1227
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 1228
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 1229
        Long offset = 0 // library marker kkossev.commonLib, line 1230
        try { // library marker kkossev.commonLib, line 1231
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 1232
        } // library marker kkossev.commonLib, line 1233
        catch (e) { // library marker kkossev.commonLib, line 1234
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 1235
        } // library marker kkossev.commonLib, line 1236
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 1237
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 1238
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 1239
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 1240
    } // library marker kkossev.commonLib, line 1241
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 1242
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 1243
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 1244
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 1245
        if (status != '00') { // library marker kkossev.commonLib, line 1246
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 1247
        } // library marker kkossev.commonLib, line 1248
    } // library marker kkossev.commonLib, line 1249
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 1250
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 1251
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 1252
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 1253
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 1254
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 1255
            return // library marker kkossev.commonLib, line 1256
        } // library marker kkossev.commonLib, line 1257
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 1258
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 1259
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 1260
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 1261
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 1262
            logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 1263
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 1264
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 1265
        } // library marker kkossev.commonLib, line 1266
    } // library marker kkossev.commonLib, line 1267
    else { // library marker kkossev.commonLib, line 1268
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 1269
    } // library marker kkossev.commonLib, line 1270
} // library marker kkossev.commonLib, line 1271

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 1273
    logTrace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 1274
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 1275
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 1276
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 1277
            return // library marker kkossev.commonLib, line 1278
        } // library marker kkossev.commonLib, line 1279
    } // library marker kkossev.commonLib, line 1280
    // check if the method  method exists // library marker kkossev.commonLib, line 1281
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 1282
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 1283
            return // library marker kkossev.commonLib, line 1284
        } // library marker kkossev.commonLib, line 1285
    } // library marker kkossev.commonLib, line 1286
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 1287
} // library marker kkossev.commonLib, line 1288

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 1290
    int retValue = 0 // library marker kkossev.commonLib, line 1291
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 1292
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 1293
        int power = 1 // library marker kkossev.commonLib, line 1294
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 1295
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 1296
            power = power * 256 // library marker kkossev.commonLib, line 1297
        } // library marker kkossev.commonLib, line 1298
    } // library marker kkossev.commonLib, line 1299
    return retValue // library marker kkossev.commonLib, line 1300
} // library marker kkossev.commonLib, line 1301

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 1303
    List<String> cmds = [] // library marker kkossev.commonLib, line 1304
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 1305
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1306
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 1307
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 1308
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 1309
    return cmds // library marker kkossev.commonLib, line 1310
} // library marker kkossev.commonLib, line 1311

private getPACKET_ID() { // library marker kkossev.commonLib, line 1313
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 1314
} // library marker kkossev.commonLib, line 1315

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1317
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 1318
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 1319
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 1320
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 1321
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 1322
} // library marker kkossev.commonLib, line 1323

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 1325
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 1326

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 1328
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 1329
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1330
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 1331
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 1332
} // library marker kkossev.commonLib, line 1333

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 1335
    List<String> cmds = [] // library marker kkossev.commonLib, line 1336
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1337
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 1338
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1339
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1340
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 1341
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 1342
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 1343
        } // library marker kkossev.commonLib, line 1344
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 1345
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 1346
    } // library marker kkossev.commonLib, line 1347
    else { // library marker kkossev.commonLib, line 1348
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 1349
    } // library marker kkossev.commonLib, line 1350
} // library marker kkossev.commonLib, line 1351

/** // library marker kkossev.commonLib, line 1353
 * initializes the device // library marker kkossev.commonLib, line 1354
 * Invoked from configure() // library marker kkossev.commonLib, line 1355
 * @return zigbee commands // library marker kkossev.commonLib, line 1356
 */ // library marker kkossev.commonLib, line 1357
List<String> initializeDevice() { // library marker kkossev.commonLib, line 1358
    List<String> cmds = [] // library marker kkossev.commonLib, line 1359
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 1360
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 1361
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 1362
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1363
    } // library marker kkossev.commonLib, line 1364
    return cmds // library marker kkossev.commonLib, line 1365
} // library marker kkossev.commonLib, line 1366

/** // library marker kkossev.commonLib, line 1368
 * configures the device // library marker kkossev.commonLib, line 1369
 * Invoked from configure() // library marker kkossev.commonLib, line 1370
 * @return zigbee commands // library marker kkossev.commonLib, line 1371
 */ // library marker kkossev.commonLib, line 1372
List<String> configureDevice() { // library marker kkossev.commonLib, line 1373
    List<String> cmds = [] // library marker kkossev.commonLib, line 1374
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 1375

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 1377
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 1378
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1379
    } // library marker kkossev.commonLib, line 1380
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 1381
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 1382
    return cmds // library marker kkossev.commonLib, line 1383
} // library marker kkossev.commonLib, line 1384

/* // library marker kkossev.commonLib, line 1386
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1387
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 1388
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1389
*/ // library marker kkossev.commonLib, line 1390

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 1392
    List<String> cmds = [] // library marker kkossev.commonLib, line 1393
    if (customHandlersList != null && customHandlersList != []) { // library marker kkossev.commonLib, line 1394
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 1395
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 1396
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 1397
                if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1398
            } // library marker kkossev.commonLib, line 1399
        } // library marker kkossev.commonLib, line 1400
    } // library marker kkossev.commonLib, line 1401
    return cmds // library marker kkossev.commonLib, line 1402
} // library marker kkossev.commonLib, line 1403

void refresh() { // library marker kkossev.commonLib, line 1405
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1406
    checkDriverVersion() // library marker kkossev.commonLib, line 1407
    List<String> cmds = [] // library marker kkossev.commonLib, line 1408
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 1409

    List<String> customCmds = customHandlers(['batteryRefresh', 'groupsRefresh', 'customRefresh']) // library marker kkossev.commonLib, line 1411
    if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1412

    if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 1414
    else { // library marker kkossev.commonLib, line 1415
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 1416
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1417
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1418
        } // library marker kkossev.commonLib, line 1419
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 1420
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1421
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1422
        } // library marker kkossev.commonLib, line 1423
    } // library marker kkossev.commonLib, line 1424

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1426
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1427
    } // library marker kkossev.commonLib, line 1428
    else { // library marker kkossev.commonLib, line 1429
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1430
    } // library marker kkossev.commonLib, line 1431
} // library marker kkossev.commonLib, line 1432

void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 1434
void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1435

void clearInfoEvent() { // library marker kkossev.commonLib, line 1437
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 1438
} // library marker kkossev.commonLib, line 1439

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 1441
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 1442
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 1443
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 1444
    } // library marker kkossev.commonLib, line 1445
    else { // library marker kkossev.commonLib, line 1446
        logInfo "${info}" // library marker kkossev.commonLib, line 1447
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 1448
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1449
    } // library marker kkossev.commonLib, line 1450
} // library marker kkossev.commonLib, line 1451

void ping() { // library marker kkossev.commonLib, line 1453
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1454
    state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1455
    //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 1456
    state.states['isPing'] = true // library marker kkossev.commonLib, line 1457
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1458
    if (isVirtual()) { // library marker kkossev.commonLib, line 1459
        runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 1460
    } // library marker kkossev.commonLib, line 1461
    else { // library marker kkossev.commonLib, line 1462
        sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 1463
    } // library marker kkossev.commonLib, line 1464
    logDebug 'ping...' // library marker kkossev.commonLib, line 1465
} // library marker kkossev.commonLib, line 1466

def virtualPong() { // library marker kkossev.commonLib, line 1468
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1469
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1470
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1471
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1472
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1473
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1474
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1475
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1476
        sendRttEvent() // library marker kkossev.commonLib, line 1477
    } // library marker kkossev.commonLib, line 1478
    else { // library marker kkossev.commonLib, line 1479
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1480
    } // library marker kkossev.commonLib, line 1481
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1482
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1483
} // library marker kkossev.commonLib, line 1484

/** // library marker kkossev.commonLib, line 1486
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 1487
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 1488
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 1489
 * @return none // library marker kkossev.commonLib, line 1490
 */ // library marker kkossev.commonLib, line 1491
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1492
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1493
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1494
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1495
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1496
    if (value == null) { // library marker kkossev.commonLib, line 1497
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1498
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 1499
    } // library marker kkossev.commonLib, line 1500
    else { // library marker kkossev.commonLib, line 1501
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1502
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1503
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 1504
    } // library marker kkossev.commonLib, line 1505
} // library marker kkossev.commonLib, line 1506

/** // library marker kkossev.commonLib, line 1508
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 1509
 * @param cluster cluster ID // library marker kkossev.commonLib, line 1510
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 1511
 */ // library marker kkossev.commonLib, line 1512
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1513
    if (cluster != null) { // library marker kkossev.commonLib, line 1514
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1515
    } // library marker kkossev.commonLib, line 1516
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1517
    return 'NULL' // library marker kkossev.commonLib, line 1518
} // library marker kkossev.commonLib, line 1519

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1521
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1522
} // library marker kkossev.commonLib, line 1523

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1525
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1526
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1527
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1528
} // library marker kkossev.commonLib, line 1529

/** // library marker kkossev.commonLib, line 1531
 * Schedule a device health check // library marker kkossev.commonLib, line 1532
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 1533
 */ // library marker kkossev.commonLib, line 1534
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1535
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1536
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1537
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1538
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1539
    } // library marker kkossev.commonLib, line 1540
    else { // library marker kkossev.commonLib, line 1541
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1542
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1543
    } // library marker kkossev.commonLib, line 1544
} // library marker kkossev.commonLib, line 1545

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1547
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1548
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1549
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1550
} // library marker kkossev.commonLib, line 1551

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1553
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 1554
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1555
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1556
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1557
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1558
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1559
    } // library marker kkossev.commonLib, line 1560
} // library marker kkossev.commonLib, line 1561

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1563
    checkDriverVersion() // library marker kkossev.commonLib, line 1564
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1565
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1566
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1567
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1568
            logWarn 'not present!' // library marker kkossev.commonLib, line 1569
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1570
        } // library marker kkossev.commonLib, line 1571
    } // library marker kkossev.commonLib, line 1572
    else { // library marker kkossev.commonLib, line 1573
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1574
    } // library marker kkossev.commonLib, line 1575
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1576
} // library marker kkossev.commonLib, line 1577

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1579
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1580
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 1581
    if (value == 'online') { // library marker kkossev.commonLib, line 1582
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1583
    } // library marker kkossev.commonLib, line 1584
    else { // library marker kkossev.commonLib, line 1585
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1586
    } // library marker kkossev.commonLib, line 1587
} // library marker kkossev.commonLib, line 1588

/** // library marker kkossev.commonLib, line 1590
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 1591
 */ // library marker kkossev.commonLib, line 1592
void autoPoll() { // library marker kkossev.commonLib, line 1593
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 1594
    checkDriverVersion() // library marker kkossev.commonLib, line 1595
    List<String> cmds = [] // library marker kkossev.commonLib, line 1596
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 1597
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 1598
    } // library marker kkossev.commonLib, line 1599

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1601
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1602
    } // library marker kkossev.commonLib, line 1603
} // library marker kkossev.commonLib, line 1604

/** // library marker kkossev.commonLib, line 1606
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1607
 */ // library marker kkossev.commonLib, line 1608
void updated() { // library marker kkossev.commonLib, line 1609
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1610
    checkDriverVersion() // library marker kkossev.commonLib, line 1611
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1612
    unschedule() // library marker kkossev.commonLib, line 1613

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1615
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1616
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1617
    } // library marker kkossev.commonLib, line 1618
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1619
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1620
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1621
    } // library marker kkossev.commonLib, line 1622

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1624
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1625
        // schedule the periodic timer // library marker kkossev.commonLib, line 1626
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1627
        if (interval > 0) { // library marker kkossev.commonLib, line 1628
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1629
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1630
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1631
        } // library marker kkossev.commonLib, line 1632
    } // library marker kkossev.commonLib, line 1633
    else { // library marker kkossev.commonLib, line 1634
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1635
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1636
    } // library marker kkossev.commonLib, line 1637
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1638
        customUpdated() // library marker kkossev.commonLib, line 1639
    } // library marker kkossev.commonLib, line 1640

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1642
} // library marker kkossev.commonLib, line 1643

/** // library marker kkossev.commonLib, line 1645
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 1646
 */ // library marker kkossev.commonLib, line 1647
void logsOff() { // library marker kkossev.commonLib, line 1648
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1649
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1650
} // library marker kkossev.commonLib, line 1651
void traceOff() { // library marker kkossev.commonLib, line 1652
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1653
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1654
} // library marker kkossev.commonLib, line 1655

void configure(String command) { // library marker kkossev.commonLib, line 1657
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1658
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1659
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1660
        return // library marker kkossev.commonLib, line 1661
    } // library marker kkossev.commonLib, line 1662
    // // library marker kkossev.commonLib, line 1663
    String func // library marker kkossev.commonLib, line 1664
    try { // library marker kkossev.commonLib, line 1665
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1666
        "$func"() // library marker kkossev.commonLib, line 1667
    } // library marker kkossev.commonLib, line 1668
    catch (e) { // library marker kkossev.commonLib, line 1669
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1670
        return // library marker kkossev.commonLib, line 1671
    } // library marker kkossev.commonLib, line 1672
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1673
} // library marker kkossev.commonLib, line 1674

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1676
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1677
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1678
} // library marker kkossev.commonLib, line 1679

void loadAllDefaults() { // library marker kkossev.commonLib, line 1681
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1682
    deleteAllSettings() // library marker kkossev.commonLib, line 1683
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1684
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1685
    deleteAllStates() // library marker kkossev.commonLib, line 1686
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1687
    initialize() // library marker kkossev.commonLib, line 1688
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1689
    updated() // library marker kkossev.commonLib, line 1690
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1691
} // library marker kkossev.commonLib, line 1692

void configureNow() { // library marker kkossev.commonLib, line 1694
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 1695
} // library marker kkossev.commonLib, line 1696

/** // library marker kkossev.commonLib, line 1698
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1699
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1700
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1701
 */ // library marker kkossev.commonLib, line 1702
List<String> configure() { // library marker kkossev.commonLib, line 1703
    List<String> cmds = [] // library marker kkossev.commonLib, line 1704
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1705
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1706
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1707
    if (isTuya()) { // library marker kkossev.commonLib, line 1708
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1709
    } // library marker kkossev.commonLib, line 1710
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1711
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1712
    } // library marker kkossev.commonLib, line 1713
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1714
    if (initCmds != null && initCmds != [] ) { cmds += initCmds } // library marker kkossev.commonLib, line 1715
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1716
    if (cfgCmds != null && cfgCmds != [] ) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1717
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1718
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1719
    logDebug "configure(): returning cmds = ${cmds}" // library marker kkossev.commonLib, line 1720
    //return cmds // library marker kkossev.commonLib, line 1721
    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1722
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1723
    } // library marker kkossev.commonLib, line 1724
    else { // library marker kkossev.commonLib, line 1725
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1726
    } // library marker kkossev.commonLib, line 1727
} // library marker kkossev.commonLib, line 1728

/** // library marker kkossev.commonLib, line 1730
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 1731
 */ // library marker kkossev.commonLib, line 1732
void installed() { // library marker kkossev.commonLib, line 1733
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1734
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1735
    // populate some default values for attributes // library marker kkossev.commonLib, line 1736
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 1737
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 1738
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1739
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1740
} // library marker kkossev.commonLib, line 1741

/** // library marker kkossev.commonLib, line 1743
 * Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1744
 */ // library marker kkossev.commonLib, line 1745
void initialize() { // library marker kkossev.commonLib, line 1746
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1747
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1748
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1749
    updateTuyaVersion() // library marker kkossev.commonLib, line 1750
    updateAqaraVersion() // library marker kkossev.commonLib, line 1751
} // library marker kkossev.commonLib, line 1752

/* // library marker kkossev.commonLib, line 1754
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1755
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1756
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1757
*/ // library marker kkossev.commonLib, line 1758

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1760
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1761
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1762
} // library marker kkossev.commonLib, line 1763

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1765
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1766
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1767
} // library marker kkossev.commonLib, line 1768

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1770
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1771
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1772
} // library marker kkossev.commonLib, line 1773

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1775
    if (cmd == null || cmd == [] || cmd == 'null') { // library marker kkossev.commonLib, line 1776
        logWarn 'sendZigbeeCommands: no commands to send!' // library marker kkossev.commonLib, line 1777
        return // library marker kkossev.commonLib, line 1778
    } // library marker kkossev.commonLib, line 1779
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 1780
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1781
    cmd.each { // library marker kkossev.commonLib, line 1782
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1783
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1784
    } // library marker kkossev.commonLib, line 1785
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1786
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1787
} // library marker kkossev.commonLib, line 1788

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1790

String getDeviceInfo() { // library marker kkossev.commonLib, line 1792
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1793
} // library marker kkossev.commonLib, line 1794

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1796
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1797
} // library marker kkossev.commonLib, line 1798

void checkDriverVersion() { // library marker kkossev.commonLib, line 1800
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1801
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1802
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1803
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1804
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 1805
        updateTuyaVersion() // library marker kkossev.commonLib, line 1806
        updateAqaraVersion() // library marker kkossev.commonLib, line 1807
    } // library marker kkossev.commonLib, line 1808
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1809
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1810
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1811
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1812
} // library marker kkossev.commonLib, line 1813

// credits @thebearmay // library marker kkossev.commonLib, line 1815
String getModel() { // library marker kkossev.commonLib, line 1816
    try { // library marker kkossev.commonLib, line 1817
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1818
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1819
    } catch (ignore) { // library marker kkossev.commonLib, line 1820
        try { // library marker kkossev.commonLib, line 1821
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1822
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1823
                return model // library marker kkossev.commonLib, line 1824
            } // library marker kkossev.commonLib, line 1825
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1826
            return '' // library marker kkossev.commonLib, line 1827
        } // library marker kkossev.commonLib, line 1828
    } // library marker kkossev.commonLib, line 1829
} // library marker kkossev.commonLib, line 1830

// credits @thebearmay // library marker kkossev.commonLib, line 1832
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1833
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1834
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1835
    String revision = tokens.last() // library marker kkossev.commonLib, line 1836
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1837
} // library marker kkossev.commonLib, line 1838

/** // library marker kkossev.commonLib, line 1840
 * called from TODO // library marker kkossev.commonLib, line 1841
 */ // library marker kkossev.commonLib, line 1842

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1844
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1845
    unschedule() // library marker kkossev.commonLib, line 1846
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1847
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1848

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1850
} // library marker kkossev.commonLib, line 1851

void resetStatistics() { // library marker kkossev.commonLib, line 1853
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1854
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1855
} // library marker kkossev.commonLib, line 1856

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1858
void resetStats() { // library marker kkossev.commonLib, line 1859
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1860
    state.stats = [:] // library marker kkossev.commonLib, line 1861
    state.states = [:] // library marker kkossev.commonLib, line 1862
    state.lastRx = [:] // library marker kkossev.commonLib, line 1863
    state.lastTx = [:] // library marker kkossev.commonLib, line 1864
    state.health = [:] // library marker kkossev.commonLib, line 1865
    if (this.respondsTo('groupsLibVersion')) { // library marker kkossev.commonLib, line 1866
        state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 1867
    } // library marker kkossev.commonLib, line 1868
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 1869
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1870
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 1871
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 1872
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 1873
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1874
} // library marker kkossev.commonLib, line 1875

/** // library marker kkossev.commonLib, line 1877
 * called from TODO // library marker kkossev.commonLib, line 1878
 */ // library marker kkossev.commonLib, line 1879
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1880
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1881
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1882
        state.clear() // library marker kkossev.commonLib, line 1883
        unschedule() // library marker kkossev.commonLib, line 1884
        resetStats() // library marker kkossev.commonLib, line 1885
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 1886
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1887
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1888
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1889
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1890
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1891
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1892
    } // library marker kkossev.commonLib, line 1893

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1895
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1896
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1897
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1898
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1899

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1901
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1902
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1903
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 1904
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1905
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1906
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1907
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1908
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1909
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 1910

    // common libraries initialization // library marker kkossev.commonLib, line 1912
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1913

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1915
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1916
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1917
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1918
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 1919

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1921
    if ( mm != null) { // library marker kkossev.commonLib, line 1922
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 1923
    } // library marker kkossev.commonLib, line 1924
    else { // library marker kkossev.commonLib, line 1925
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 1926
    } // library marker kkossev.commonLib, line 1927
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1928
    if ( ep  != null) { // library marker kkossev.commonLib, line 1929
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1930
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1931
    } // library marker kkossev.commonLib, line 1932
    else { // library marker kkossev.commonLib, line 1933
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1934
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1935
    } // library marker kkossev.commonLib, line 1936
} // library marker kkossev.commonLib, line 1937

/** // library marker kkossev.commonLib, line 1939
 * called from TODO // library marker kkossev.commonLib, line 1940
 */ // library marker kkossev.commonLib, line 1941
void setDestinationEP() { // library marker kkossev.commonLib, line 1942
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1943
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1944
        state.destinationEP = ep // library marker kkossev.commonLib, line 1945
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1946
    } // library marker kkossev.commonLib, line 1947
    else { // library marker kkossev.commonLib, line 1948
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1949
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 1950
    } // library marker kkossev.commonLib, line 1951
} // library marker kkossev.commonLib, line 1952

void logDebug(final String msg) { // library marker kkossev.commonLib, line 1954
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 1955
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 1956
    } // library marker kkossev.commonLib, line 1957
} // library marker kkossev.commonLib, line 1958

void logInfo(final String msg) { // library marker kkossev.commonLib, line 1960
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 1961
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 1962
    } // library marker kkossev.commonLib, line 1963
} // library marker kkossev.commonLib, line 1964

void logWarn(final String msg) { // library marker kkossev.commonLib, line 1966
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 1967
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 1968
    } // library marker kkossev.commonLib, line 1969
} // library marker kkossev.commonLib, line 1970

void logTrace(final String msg) { // library marker kkossev.commonLib, line 1972
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 1973
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 1974
    } // library marker kkossev.commonLib, line 1975
} // library marker kkossev.commonLib, line 1976

// _DEBUG mode only // library marker kkossev.commonLib, line 1978
void getAllProperties() { // library marker kkossev.commonLib, line 1979
    log.trace 'Properties:' // library marker kkossev.commonLib, line 1980
    device.properties.each { it -> // library marker kkossev.commonLib, line 1981
        log.debug it // library marker kkossev.commonLib, line 1982
    } // library marker kkossev.commonLib, line 1983
    log.trace 'Settings:' // library marker kkossev.commonLib, line 1984
    settings.each { it -> // library marker kkossev.commonLib, line 1985
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1986
    } // library marker kkossev.commonLib, line 1987
    log.trace 'Done' // library marker kkossev.commonLib, line 1988
} // library marker kkossev.commonLib, line 1989

// delete all Preferences // library marker kkossev.commonLib, line 1991
void deleteAllSettings() { // library marker kkossev.commonLib, line 1992
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1993
    settings.each { it -> // library marker kkossev.commonLib, line 1994
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 1995
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 1996
    } // library marker kkossev.commonLib, line 1997
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1998
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1999
} // library marker kkossev.commonLib, line 2000

// delete all attributes // library marker kkossev.commonLib, line 2002
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2003
    String attributesDeleted = '' // library marker kkossev.commonLib, line 2004
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 2005
        attributesDeleted += "${it}, " // library marker kkossev.commonLib, line 2006
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2007
    } // library marker kkossev.commonLib, line 2008
    logDebug "Deleted attributes: ${attributesDeleted}" // library marker kkossev.commonLib, line 2009
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2010
} // library marker kkossev.commonLib, line 2011

// delete all State Variables // library marker kkossev.commonLib, line 2013
void deleteAllStates() { // library marker kkossev.commonLib, line 2014
    state.each { it -> // library marker kkossev.commonLib, line 2015
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2016
    } // library marker kkossev.commonLib, line 2017
    state.clear() // library marker kkossev.commonLib, line 2018
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2019
} // library marker kkossev.commonLib, line 2020

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2022
    unschedule() // library marker kkossev.commonLib, line 2023
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2024
} // library marker kkossev.commonLib, line 2025

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2027
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 2028
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 2029
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 2030
    } // library marker kkossev.commonLib, line 2031
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 2032
} // library marker kkossev.commonLib, line 2033

void parseTest(String par) { // library marker kkossev.commonLib, line 2035
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2036
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2037
    parse(par) // library marker kkossev.commonLib, line 2038
} // library marker kkossev.commonLib, line 2039

def testJob() { // library marker kkossev.commonLib, line 2041
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2042
} // library marker kkossev.commonLib, line 2043

/** // library marker kkossev.commonLib, line 2045
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2046
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2047
 */ // library marker kkossev.commonLib, line 2048
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2049
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2050
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2051
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2052
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2053
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2054
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2055
    String cron // library marker kkossev.commonLib, line 2056
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2057
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2058
    } // library marker kkossev.commonLib, line 2059
    else { // library marker kkossev.commonLib, line 2060
        if (minutes < 60) { // library marker kkossev.commonLib, line 2061
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2062
        } // library marker kkossev.commonLib, line 2063
        else { // library marker kkossev.commonLib, line 2064
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2065
        } // library marker kkossev.commonLib, line 2066
    } // library marker kkossev.commonLib, line 2067
    return cron // library marker kkossev.commonLib, line 2068
} // library marker kkossev.commonLib, line 2069

// credits @thebearmay // library marker kkossev.commonLib, line 2071
String formatUptime() { // library marker kkossev.commonLib, line 2072
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2073
} // library marker kkossev.commonLib, line 2074

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2076
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2077
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2078
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2079
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2080
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2081
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2082
} // library marker kkossev.commonLib, line 2083

boolean isTuya() { // library marker kkossev.commonLib, line 2085
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 2086
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2087
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2088
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2089
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2090
} // library marker kkossev.commonLib, line 2091

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2093
    if (!isTuya()) { // library marker kkossev.commonLib, line 2094
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2095
        return // library marker kkossev.commonLib, line 2096
    } // library marker kkossev.commonLib, line 2097
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2098
    if (application != null) { // library marker kkossev.commonLib, line 2099
        Integer ver // library marker kkossev.commonLib, line 2100
        try { // library marker kkossev.commonLib, line 2101
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2102
        } // library marker kkossev.commonLib, line 2103
        catch (e) { // library marker kkossev.commonLib, line 2104
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2105
            return // library marker kkossev.commonLib, line 2106
        } // library marker kkossev.commonLib, line 2107
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2108
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2109
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2110
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2111
        } // library marker kkossev.commonLib, line 2112
    } // library marker kkossev.commonLib, line 2113
} // library marker kkossev.commonLib, line 2114

boolean isAqara() { // library marker kkossev.commonLib, line 2116
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2117
} // library marker kkossev.commonLib, line 2118

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2120
    if (!isAqara()) { // library marker kkossev.commonLib, line 2121
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2122
        return // library marker kkossev.commonLib, line 2123
    } // library marker kkossev.commonLib, line 2124
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2125
    if (application != null) { // library marker kkossev.commonLib, line 2126
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2127
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2128
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2129
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2130
        } // library marker kkossev.commonLib, line 2131
    } // library marker kkossev.commonLib, line 2132
} // library marker kkossev.commonLib, line 2133

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2135
    try { // library marker kkossev.commonLib, line 2136
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2137
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2138
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2139
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2140
    } catch (e) { // library marker kkossev.commonLib, line 2141
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2142
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2143
    } // library marker kkossev.commonLib, line 2144
} // library marker kkossev.commonLib, line 2145

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2147
    try { // library marker kkossev.commonLib, line 2148
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2149
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2150
        return date.getTime() // library marker kkossev.commonLib, line 2151
    } catch (e) { // library marker kkossev.commonLib, line 2152
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2153
        return now() // library marker kkossev.commonLib, line 2154
    } // library marker kkossev.commonLib, line 2155
} // library marker kkossev.commonLib, line 2156
/* // library marker kkossev.commonLib, line 2157
void test(String par) { // library marker kkossev.commonLib, line 2158
    List<String> cmds = [] // library marker kkossev.commonLib, line 2159
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2160

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2162
    //parse(par) // library marker kkossev.commonLib, line 2163

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2165
} // library marker kkossev.commonLib, line 2166
*/ // library marker kkossev.commonLib, line 2167

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (171) kkossev.batteryLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
library( // library marker kkossev.batteryLib, line 2
    base: 'driver', // library marker kkossev.batteryLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.batteryLib, line 4
    category: 'zigbee', // library marker kkossev.batteryLib, line 5
    description: 'Zigbee Battery Library', // library marker kkossev.batteryLib, line 6
    name: 'batteryLib', // library marker kkossev.batteryLib, line 7
    namespace: 'kkossev', // library marker kkossev.batteryLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/batteryLib.groovy', // library marker kkossev.batteryLib, line 9
    version: '3.0.2', // library marker kkossev.batteryLib, line 10
    documentationLink: '' // library marker kkossev.batteryLib, line 11
) // library marker kkossev.batteryLib, line 12
/* // library marker kkossev.batteryLib, line 13
 *  Zigbee Level Library // library marker kkossev.batteryLib, line 14
 * // library marker kkossev.batteryLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.batteryLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.batteryLib, line 17
 * // library marker kkossev.batteryLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.batteryLib, line 19
 * // library marker kkossev.batteryLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.batteryLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.batteryLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.batteryLib, line 23
 * // library marker kkossev.batteryLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added batteryLib.groovy // library marker kkossev.batteryLib, line 25
 * ver. 3.0.1  2024-04-06 kkossev  - customParsePowerCluster bug fix // library marker kkossev.batteryLib, line 26
 * ver. 3.0.2  2024-04-14 kkossev  - batteryPercentage bug fix (was x2); added bVoltCtr; added battertRefresh // library marker kkossev.batteryLib, line 27
 * // library marker kkossev.batteryLib, line 28
 *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.batteryLib, line 29
*/ // library marker kkossev.batteryLib, line 30

static String batteryLibVersion()   { '3.0.2' } // library marker kkossev.batteryLib, line 32
static String batteryLibStamp() { '2024/04/15 8:07 AM' } // library marker kkossev.batteryLib, line 33

metadata { // library marker kkossev.batteryLib, line 35
    capability 'Battery' // library marker kkossev.batteryLib, line 36
    attribute 'batteryVoltage', 'number' // library marker kkossev.batteryLib, line 37
    // no commands // library marker kkossev.batteryLib, line 38
    preferences { // library marker kkossev.batteryLib, line 39
        if (device && advancedOptions == true) { // library marker kkossev.batteryLib, line 40
            input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.batteryLib, line 41
        } // library marker kkossev.batteryLib, line 42
    } // library marker kkossev.batteryLib, line 43
} // library marker kkossev.batteryLib, line 44

void customParsePowerCluster(final Map descMap) { // library marker kkossev.batteryLib, line 46
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

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.batteryLib, line 72
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.batteryLib, line 73
    Map result = [:] // library marker kkossev.batteryLib, line 74
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.batteryLib, line 75
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.batteryLib, line 76
        BigDecimal minVolts = 2.2 // library marker kkossev.batteryLib, line 77
        BigDecimal maxVolts = 3.2 // library marker kkossev.batteryLib, line 78
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.batteryLib, line 79
        int roundedPct = Math.round(pct * 100) // library marker kkossev.batteryLib, line 80
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.batteryLib, line 81
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.batteryLib, line 82
        if (convertToPercent == true) { // library marker kkossev.batteryLib, line 83
            result.value = Math.min(100, roundedPct) // library marker kkossev.batteryLib, line 84
            result.name = 'battery' // library marker kkossev.batteryLib, line 85
            result.unit  = '%' // library marker kkossev.batteryLib, line 86
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.batteryLib, line 87
        } // library marker kkossev.batteryLib, line 88
        else { // library marker kkossev.batteryLib, line 89
            result.value = volts // library marker kkossev.batteryLib, line 90
            result.name = 'batteryVoltage' // library marker kkossev.batteryLib, line 91
            result.unit  = 'V' // library marker kkossev.batteryLib, line 92
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.batteryLib, line 93
        } // library marker kkossev.batteryLib, line 94
        result.type = 'physical' // library marker kkossev.batteryLib, line 95
        result.isStateChange = true // library marker kkossev.batteryLib, line 96
        logInfo "${result.descriptionText}" // library marker kkossev.batteryLib, line 97
        sendEvent(result) // library marker kkossev.batteryLib, line 98
    } // library marker kkossev.batteryLib, line 99
    else { // library marker kkossev.batteryLib, line 100
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.batteryLib, line 101
    } // library marker kkossev.batteryLib, line 102
} // library marker kkossev.batteryLib, line 103

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.batteryLib, line 105
    if ((batteryPercent as int) == 255) { // library marker kkossev.batteryLib, line 106
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.batteryLib, line 107
        return // library marker kkossev.batteryLib, line 108
    } // library marker kkossev.batteryLib, line 109
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
    } // library marker kkossev.batteryLib, line 126
    else { // library marker kkossev.batteryLib, line 127
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.batteryLib, line 128
        map.delayed = delayedTime // library marker kkossev.batteryLib, line 129
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.batteryLib, line 130
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.batteryLib, line 131
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.batteryLib, line 132
    } // library marker kkossev.batteryLib, line 133
} // library marker kkossev.batteryLib, line 134

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.batteryLib, line 136
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 137
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 138
    sendEvent(map) // library marker kkossev.batteryLib, line 139
} // library marker kkossev.batteryLib, line 140

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.batteryLib, line 142
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.batteryLib, line 143
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 144
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 145
    sendEvent(map) // library marker kkossev.batteryLib, line 146
} // library marker kkossev.batteryLib, line 147

List<String> batteryRefresh() { // library marker kkossev.batteryLib, line 149
    List<String> cmds = [] // library marker kkossev.batteryLib, line 150
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 100)         // battery voltage // library marker kkossev.batteryLib, line 151
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 100)         // battery percentage // library marker kkossev.batteryLib, line 152
    return cmds // library marker kkossev.batteryLib, line 153
} // library marker kkossev.batteryLib, line 154

// ~~~~~ end include (171) kkossev.batteryLib ~~~~~

// ~~~~~ start include (172) kkossev.temperatureLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.temperatureLib, line 1
library( // library marker kkossev.temperatureLib, line 2
    base: 'driver', // library marker kkossev.temperatureLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.temperatureLib, line 4
    category: 'zigbee', // library marker kkossev.temperatureLib, line 5
    description: 'Zigbee Temperature Library', // library marker kkossev.temperatureLib, line 6
    name: 'temperatureLib', // library marker kkossev.temperatureLib, line 7
    namespace: 'kkossev', // library marker kkossev.temperatureLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/temperatureLib.groovy', // library marker kkossev.temperatureLib, line 9
    version: '3.0.0', // library marker kkossev.temperatureLib, line 10
    documentationLink: '' // library marker kkossev.temperatureLib, line 11
) // library marker kkossev.temperatureLib, line 12
/* // library marker kkossev.temperatureLib, line 13
 *  Zigbee Temperature Library // library marker kkossev.temperatureLib, line 14
 * // library marker kkossev.temperatureLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.temperatureLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.temperatureLib, line 17
 * // library marker kkossev.temperatureLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.temperatureLib, line 19
 * // library marker kkossev.temperatureLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.temperatureLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.temperatureLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.temperatureLib, line 23
 * // library marker kkossev.temperatureLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added temperatureLib.groovy // library marker kkossev.temperatureLib, line 25
 * // library marker kkossev.temperatureLib, line 26
 *                                   TODO: // library marker kkossev.temperatureLib, line 27
*/ // library marker kkossev.temperatureLib, line 28

static String temperatureLibVersion()   { '3.0.0' } // library marker kkossev.temperatureLib, line 30
static String temperatureLibStamp() { '2024/04/06 11:49 PM' } // library marker kkossev.temperatureLib, line 31

metadata { // library marker kkossev.temperatureLib, line 33
    capability 'TemperatureMeasurement' // library marker kkossev.temperatureLib, line 34
    // no commands // library marker kkossev.temperatureLib, line 35
    preferences { // library marker kkossev.temperatureLib, line 36
        if (device) { // library marker kkossev.temperatureLib, line 37
            if (settings?.minReportingTime == null) { // library marker kkossev.temperatureLib, line 38
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 39
            } // library marker kkossev.temperatureLib, line 40
            if (settings?.minReportingTime == null) { // library marker kkossev.temperatureLib, line 41
                if (deviceType != 'mmWaveSensor') { // library marker kkossev.temperatureLib, line 42
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.temperatureLib, line 43
                } // library marker kkossev.temperatureLib, line 44
            } // library marker kkossev.temperatureLib, line 45
        } // library marker kkossev.temperatureLib, line 46
    } // library marker kkossev.temperatureLib, line 47
} // library marker kkossev.temperatureLib, line 48

void customParseTemperatureCluster(final Map descMap) { // library marker kkossev.temperatureLib, line 50
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.temperatureLib, line 51
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.temperatureLib, line 52
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.temperatureLib, line 53
} // library marker kkossev.temperatureLib, line 54

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.temperatureLib, line 56
    Map eventMap = [:] // library marker kkossev.temperatureLib, line 57
    BigDecimal temperature = safeToBigDecimal(temperaturePar) // library marker kkossev.temperatureLib, line 58
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.temperatureLib, line 59
    eventMap.name = 'temperature' // library marker kkossev.temperatureLib, line 60
    if (location.temperatureScale == 'F') { // library marker kkossev.temperatureLib, line 61
        temperature = (temperature * 1.8) + 32 // library marker kkossev.temperatureLib, line 62
        eventMap.unit = '\u00B0F' // library marker kkossev.temperatureLib, line 63
    } // library marker kkossev.temperatureLib, line 64
    else { // library marker kkossev.temperatureLib, line 65
        eventMap.unit = '\u00B0C' // library marker kkossev.temperatureLib, line 66
    } // library marker kkossev.temperatureLib, line 67
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)) // library marker kkossev.temperatureLib, line 68
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 69
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.temperatureLib, line 70
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.temperatureLib, line 71
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.temperatureLib, line 72
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.temperatureLib, line 73
        return // library marker kkossev.temperatureLib, line 74
    } // library marker kkossev.temperatureLib, line 75
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.temperatureLib, line 76
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.temperatureLib, line 77
    if (state.states['isRefresh'] == true) { // library marker kkossev.temperatureLib, line 78
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.temperatureLib, line 79
        eventMap.isStateChange = true // library marker kkossev.temperatureLib, line 80
    } // library marker kkossev.temperatureLib, line 81
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.temperatureLib, line 82
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 83
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.temperatureLib, line 84
    if (timeElapsed >= minTime) { // library marker kkossev.temperatureLib, line 85
        logInfo "${eventMap.descriptionText}" // library marker kkossev.temperatureLib, line 86
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.temperatureLib, line 87
        state.lastRx['tempTime'] = now() // library marker kkossev.temperatureLib, line 88
        sendEvent(eventMap) // library marker kkossev.temperatureLib, line 89
    } // library marker kkossev.temperatureLib, line 90
    else {         // queue the event // library marker kkossev.temperatureLib, line 91
        eventMap.type = 'delayed' // library marker kkossev.temperatureLib, line 92
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.temperatureLib, line 93
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.temperatureLib, line 94
    } // library marker kkossev.temperatureLib, line 95
} // library marker kkossev.temperatureLib, line 96

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.temperatureLib, line 98
private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.temperatureLib, line 99
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.temperatureLib, line 100
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.temperatureLib, line 101
    sendEvent(eventMap) // library marker kkossev.temperatureLib, line 102
} // library marker kkossev.temperatureLib, line 103

List<String> temperatureLibInitializeDevice() { // library marker kkossev.temperatureLib, line 105
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 106
    cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.temperatureLib, line 107
    return cmds // library marker kkossev.temperatureLib, line 108
} // library marker kkossev.temperatureLib, line 109

// ~~~~~ end include (172) kkossev.temperatureLib ~~~~~

// ~~~~~ start include (165) kkossev.xiaomiLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitReturnStatement, LineLength, PublicMethodsBeforeNonPublicMethods, UnnecessaryGetter */ // library marker kkossev.xiaomiLib, line 1
library( // library marker kkossev.xiaomiLib, line 2
    base: 'driver', // library marker kkossev.xiaomiLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.xiaomiLib, line 4
    category: 'zigbee', // library marker kkossev.xiaomiLib, line 5
    description: 'Xiaomi Library', // library marker kkossev.xiaomiLib, line 6
    name: 'xiaomiLib', // library marker kkossev.xiaomiLib, line 7
    namespace: 'kkossev', // library marker kkossev.xiaomiLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/xiaomiLib.groovy', // library marker kkossev.xiaomiLib, line 9
    version: '1.0.2', // library marker kkossev.xiaomiLib, line 10
    documentationLink: '' // library marker kkossev.xiaomiLib, line 11
) // library marker kkossev.xiaomiLib, line 12
/* // library marker kkossev.xiaomiLib, line 13
 *  Xiaomi Library // library marker kkossev.xiaomiLib, line 14
 * // library marker kkossev.xiaomiLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.xiaomiLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.xiaomiLib, line 17
 * // library marker kkossev.xiaomiLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.xiaomiLib, line 19
 * // library marker kkossev.xiaomiLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.xiaomiLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.xiaomiLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.xiaomiLib, line 23
 * // library marker kkossev.xiaomiLib, line 24
 * ver. 1.0.0  2023-09-09 kkossev  - added xiaomiLib // library marker kkossev.xiaomiLib, line 25
 * ver. 1.0.1  2023-11-07 kkossev  - (dev. branch) // library marker kkossev.xiaomiLib, line 26
 * ver. 1.0.2  2024-04-06 kkossev  - (dev. branch) Groovy linting; aqaraCube specific code; // library marker kkossev.xiaomiLib, line 27
 * // library marker kkossev.xiaomiLib, line 28
 *                                   TODO: remove the isAqaraXXX  dependencies !! // library marker kkossev.xiaomiLib, line 29
*/ // library marker kkossev.xiaomiLib, line 30

/* groovylint-disable-next-line ImplicitReturnStatement */ // library marker kkossev.xiaomiLib, line 32
static String xiaomiLibVersion()   { '1.0.2' } // library marker kkossev.xiaomiLib, line 33
/* groovylint-disable-next-line ImplicitReturnStatement */ // library marker kkossev.xiaomiLib, line 34
static String xiaomiLibStamp() { '2024/04/06 12:14 PM' } // library marker kkossev.xiaomiLib, line 35

boolean isAqaraTVOC_Lib()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 37
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.xiaomiLib, line 38

// no metadata for this library! // library marker kkossev.xiaomiLib, line 40

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.xiaomiLib, line 42

// Zigbee Attributes // library marker kkossev.xiaomiLib, line 44
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.xiaomiLib, line 45
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.xiaomiLib, line 46
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.xiaomiLib, line 47
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.xiaomiLib, line 48
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.xiaomiLib, line 49
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.xiaomiLib, line 50
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.xiaomiLib, line 51
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.xiaomiLib, line 52
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.xiaomiLib, line 53
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.xiaomiLib, line 54
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.xiaomiLib, line 55
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.xiaomiLib, line 56
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.xiaomiLib, line 57
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.xiaomiLib, line 58
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.xiaomiLib, line 59

// Xiaomi Tags // library marker kkossev.xiaomiLib, line 61
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.xiaomiLib, line 62
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 63
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.xiaomiLib, line 64
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.xiaomiLib, line 65
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 66
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.xiaomiLib, line 67

// called from parseXiaomiCluster() in the main code ... // library marker kkossev.xiaomiLib, line 69
// // library marker kkossev.xiaomiLib, line 70
void parseXiaomiClusterLib(final Map descMap) { // library marker kkossev.xiaomiLib, line 71
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 72
        logTrace "zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 73
    } // library marker kkossev.xiaomiLib, line 74
    if (DEVICE_TYPE in  ['Thermostat']) { // library marker kkossev.xiaomiLib, line 75
        parseXiaomiClusterThermostatLib(descMap) // library marker kkossev.xiaomiLib, line 76
        return // library marker kkossev.xiaomiLib, line 77
    } // library marker kkossev.xiaomiLib, line 78
    if (DEVICE_TYPE in  ['Bulb']) { // library marker kkossev.xiaomiLib, line 79
        parseXiaomiClusterRgbLib(descMap) // library marker kkossev.xiaomiLib, line 80
        return // library marker kkossev.xiaomiLib, line 81
    } // library marker kkossev.xiaomiLib, line 82
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 83
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 84
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 85
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 86
            if (DEVICE_TYPE in  ['AqaraCube']) { logDebug "AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 87
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 88
            break // library marker kkossev.xiaomiLib, line 89
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 90
            log.info 'unknown attribute - resetting?' // library marker kkossev.xiaomiLib, line 91
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
                log.debug "xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 106
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
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 130
            break // library marker kkossev.xiaomiLib, line 131
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 132
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 133
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 134
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
            log.warn "zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 155
            break // library marker kkossev.xiaomiLib, line 156
    } // library marker kkossev.xiaomiLib, line 157
} // library marker kkossev.xiaomiLib, line 158

void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 160
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 161
        switch (tag) { // library marker kkossev.xiaomiLib, line 162
            case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 163
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 164
                break // library marker kkossev.xiaomiLib, line 165
            case 0x03: // library marker kkossev.xiaomiLib, line 166
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 167
                break // library marker kkossev.xiaomiLib, line 168
            case 0x05: // library marker kkossev.xiaomiLib, line 169
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 170
                break // library marker kkossev.xiaomiLib, line 171
            case 0x06: // library marker kkossev.xiaomiLib, line 172
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 173
                break // library marker kkossev.xiaomiLib, line 174
            case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 175
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 176
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 177
                device.updateDataValue('aqaraVersion', swBuild) // library marker kkossev.xiaomiLib, line 178
                break // library marker kkossev.xiaomiLib, line 179
            case 0x0a: // library marker kkossev.xiaomiLib, line 180
                String nwk = intToHexStr(value as Integer, 2) // library marker kkossev.xiaomiLib, line 181
                if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 182
                String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 183
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 184
                if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 185
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 186
                    state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 187
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 188
                } // library marker kkossev.xiaomiLib, line 189
                break // library marker kkossev.xiaomiLib, line 190
            case 0x0b: // library marker kkossev.xiaomiLib, line 191
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 192
                break // library marker kkossev.xiaomiLib, line 193
            case 0x64: // library marker kkossev.xiaomiLib, line 194
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 195
                // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 196
                break // library marker kkossev.xiaomiLib, line 197
            case 0x65: // library marker kkossev.xiaomiLib, line 198
                if (isAqaraFP1()) { logDebug "xiaomi decode PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 199
                else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 200
                break // library marker kkossev.xiaomiLib, line 201
            case 0x66: // library marker kkossev.xiaomiLib, line 202
                if (isAqaraFP1()) { logDebug "xiaomi decode SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 203
                else if (isAqaraTVOC_Lib()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 204
                else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" } // library marker kkossev.xiaomiLib, line 205
                break // library marker kkossev.xiaomiLib, line 206
            case 0x67: // library marker kkossev.xiaomiLib, line 207
                if (isAqaraFP1()) { logDebug "xiaomi decode DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 208
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC: // library marker kkossev.xiaomiLib, line 209
                // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 210
                break // library marker kkossev.xiaomiLib, line 211
            case 0x69: // library marker kkossev.xiaomiLib, line 212
                if (isAqaraFP1()) { logDebug "xiaomi decode TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 213
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 214
                break // library marker kkossev.xiaomiLib, line 215
            case 0x6a: // library marker kkossev.xiaomiLib, line 216
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 217
                else              { logDebug "xiaomi decode MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 218
                break // library marker kkossev.xiaomiLib, line 219
            case 0x6b: // library marker kkossev.xiaomiLib, line 220
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 221
                else              { logDebug "xiaomi decode MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 222
                break // library marker kkossev.xiaomiLib, line 223
            case 0x95: // library marker kkossev.xiaomiLib, line 224
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 225
                break // library marker kkossev.xiaomiLib, line 226
            case 0x96: // library marker kkossev.xiaomiLib, line 227
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 228
                break // library marker kkossev.xiaomiLib, line 229
            case 0x97: // library marker kkossev.xiaomiLib, line 230
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 231
                break // library marker kkossev.xiaomiLib, line 232
            case 0x98: // library marker kkossev.xiaomiLib, line 233
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 234
                break // library marker kkossev.xiaomiLib, line 235
            case 0x9b: // library marker kkossev.xiaomiLib, line 236
                if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 237
                    logDebug "Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})" // library marker kkossev.xiaomiLib, line 238
                    sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 239
                } // library marker kkossev.xiaomiLib, line 240
                else { logDebug "xiaomi decode CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 241
                break // library marker kkossev.xiaomiLib, line 242
            default: // library marker kkossev.xiaomiLib, line 243
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 244
        } // library marker kkossev.xiaomiLib, line 245
    } // library marker kkossev.xiaomiLib, line 246
} // library marker kkossev.xiaomiLib, line 247

/** // library marker kkossev.xiaomiLib, line 249
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 250
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 251
 */ // library marker kkossev.xiaomiLib, line 252
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 253
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 254
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 255
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 256
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 257
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 258
    } // library marker kkossev.xiaomiLib, line 259
    return bigInt // library marker kkossev.xiaomiLib, line 260
} // library marker kkossev.xiaomiLib, line 261

/** // library marker kkossev.xiaomiLib, line 263
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 264
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 265
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 266
 */ // library marker kkossev.xiaomiLib, line 267
private static Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 268
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

List<String> refreshXiaomi() { // library marker kkossev.xiaomiLib, line 291
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 292
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.xiaomiLib, line 293
    return cmds // library marker kkossev.xiaomiLib, line 294
} // library marker kkossev.xiaomiLib, line 295

List<String> configureXiaomi() { // library marker kkossev.xiaomiLib, line 297
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 298
    logDebug "configureThermostat() : ${cmds}" // library marker kkossev.xiaomiLib, line 299
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.xiaomiLib, line 300
    return cmds // library marker kkossev.xiaomiLib, line 301
} // library marker kkossev.xiaomiLib, line 302

List<String> initializeXiaomi() { // library marker kkossev.xiaomiLib, line 304
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 305
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 306
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.xiaomiLib, line 307
    return cmds // library marker kkossev.xiaomiLib, line 308
} // library marker kkossev.xiaomiLib, line 309

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 311
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 312
} // library marker kkossev.xiaomiLib, line 313

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 315
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 316
} // library marker kkossev.xiaomiLib, line 317

// ~~~~~ end include (165) kkossev.xiaomiLib ~~~~~

// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.deviceProfileLib, line 1
library( // library marker kkossev.deviceProfileLib, line 2
    base: 'driver', // library marker kkossev.deviceProfileLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.deviceProfileLib, line 4
    category: 'zigbee', // library marker kkossev.deviceProfileLib, line 5
    description: 'Device Profile Library', // library marker kkossev.deviceProfileLib, line 6
    name: 'deviceProfileLib', // library marker kkossev.deviceProfileLib, line 7
    namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy', // library marker kkossev.deviceProfileLib, line 9
    version: '3.1.1', // library marker kkossev.deviceProfileLib, line 10
    documentationLink: '' // library marker kkossev.deviceProfileLib, line 11
) // library marker kkossev.deviceProfileLib, line 12
/* // library marker kkossev.deviceProfileLib, line 13
 *  Device Profile Library // library marker kkossev.deviceProfileLib, line 14
 * // library marker kkossev.deviceProfileLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.deviceProfileLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.deviceProfileLib, line 17
 * // library marker kkossev.deviceProfileLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.deviceProfileLib, line 19
 * // library marker kkossev.deviceProfileLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.deviceProfileLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.deviceProfileLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.deviceProfileLib, line 23
 * // library marker kkossev.deviceProfileLib, line 24
 * ver. 1.0.0  2023-11-04 kkossev  - added deviceProfileLib (based on Tuya 4 In 1 driver) // library marker kkossev.deviceProfileLib, line 25
 * ver. 3.0.0  2023-11-27 kkossev  - (dev. branch) fixes for use with commonLib; added processClusterAttributeFromDeviceProfile() method; added validateAndFixPreferences() method;  inputIt bug fix; signedInt Preproc method; // library marker kkossev.deviceProfileLib, line 26
 * ver. 3.0.1  2023-12-02 kkossev  - (dev. branch) release candidate // library marker kkossev.deviceProfileLib, line 27
 * ver. 3.0.2  2023-12-17 kkossev  - (dev. branch) inputIt moved to the preferences section; setfunction replaced by customSetFunction; Groovy Linting; // library marker kkossev.deviceProfileLib, line 28
 * ver. 3.0.4  2024-03-30 kkossev  - (dev. branch) more Groovy Linting; processClusterAttributeFromDeviceProfile exception fix; // library marker kkossev.deviceProfileLib, line 29
 * ver. 3.1.0  2024-04-03 kkossev  - (dev. branch) more Groovy Linting; deviceProfilesV3, enum pars bug fix; // library marker kkossev.deviceProfileLib, line 30
 * ver. 3.1.1  2024-04-16 kkossev  - (dev. branch) deviceProfilesV3 bug fix // library marker kkossev.deviceProfileLib, line 31
 * // library marker kkossev.deviceProfileLib, line 32
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 33
*/ // library marker kkossev.deviceProfileLib, line 34

static String deviceProfileLibVersion()   { '3.1.1' } // library marker kkossev.deviceProfileLib, line 36
static String deviceProfileLibStamp() { '2024/04/16 5:09 PM' } // library marker kkossev.deviceProfileLib, line 37
import groovy.json.* // library marker kkossev.deviceProfileLib, line 38
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 39
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 40
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 41
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 42

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 44

metadata { // library marker kkossev.deviceProfileLib, line 46
    // no capabilities // library marker kkossev.deviceProfileLib, line 47
    // no attributes // library marker kkossev.deviceProfileLib, line 48
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 49
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 50
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 51
    ] // library marker kkossev.deviceProfileLib, line 52
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 53
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 54
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 55
    ] // library marker kkossev.deviceProfileLib, line 56

    preferences { // library marker kkossev.deviceProfileLib, line 58
        // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 59
        if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 60
            (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 61
                if (inputIt(key) != null) { // library marker kkossev.deviceProfileLib, line 62
                    input inputIt(key) // library marker kkossev.deviceProfileLib, line 63
                } // library marker kkossev.deviceProfileLib, line 64
            } // library marker kkossev.deviceProfileLib, line 65
            if (('motionReset' in DEVICE?.preferences) && (DEVICE?.preferences.motionReset == true)) { // library marker kkossev.deviceProfileLib, line 66
                input(name: 'motionReset', type: 'bool', title: '<b>Reset Motion to Inactive</b>', description: '<i>Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b></i>', defaultValue: false) // library marker kkossev.deviceProfileLib, line 67
                if (motionReset.value == true) { // library marker kkossev.deviceProfileLib, line 68
                    input('motionResetTimer', 'number', title: '<b>Motion Reset Timer</b>', description: '<i>After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds</i>', range: '0..7200', defaultValue: 60) // library marker kkossev.deviceProfileLib, line 69
                } // library marker kkossev.deviceProfileLib, line 70
            } // library marker kkossev.deviceProfileLib, line 71
        } // library marker kkossev.deviceProfileLib, line 72
        if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 73
            input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: '<i>Forcely change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!</i>',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 74
        } // library marker kkossev.deviceProfileLib, line 75
    } // library marker kkossev.deviceProfileLib, line 76
} // library marker kkossev.deviceProfileLib, line 77

String getDeviceProfile()    { state.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 79
Map getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2[getDeviceProfile()] } // library marker kkossev.deviceProfileLib, line 80
Set getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2?.keySet() } // library marker kkossev.deviceProfileLib, line 81
List<String> getDeviceProfilesMap()   { deviceProfilesV3 != null ? deviceProfilesV3.values().description as List<String> : deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 82
// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 83

/** // library marker kkossev.deviceProfileLib, line 85
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 86
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 87
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 88
 */ // library marker kkossev.deviceProfileLib, line 89

String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 91
    String key = deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key // library marker kkossev.deviceProfileLib, line 92
    if (key == null) { // library marker kkossev.deviceProfileLib, line 93
        key = deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key // library marker kkossev.deviceProfileLib, line 94
    } // library marker kkossev.deviceProfileLib, line 95
    return key // library marker kkossev.deviceProfileLib, line 96
} // library marker kkossev.deviceProfileLib, line 97

/** // library marker kkossev.deviceProfileLib, line 99
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 100
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 101
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 102
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 103
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 104
 */ // library marker kkossev.deviceProfileLib, line 105
Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 106
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 107
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 108
        if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 109
        return [:] // library marker kkossev.deviceProfileLib, line 110
    } // library marker kkossev.deviceProfileLib, line 111
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 112
    def preference // library marker kkossev.deviceProfileLib, line 113
    try { // library marker kkossev.deviceProfileLib, line 114
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 115
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 116
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 117
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 118
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 119
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 120
        } // library marker kkossev.deviceProfileLib, line 121
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 122
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 123
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 124
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 125
        } // library marker kkossev.deviceProfileLib, line 126
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 127
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 128
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 129
        } // library marker kkossev.deviceProfileLib, line 130
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 131
    } catch (e) { // library marker kkossev.deviceProfileLib, line 132
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 133
        return [:] // library marker kkossev.deviceProfileLib, line 134
    } // library marker kkossev.deviceProfileLib, line 135
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 136
    return foundMap // library marker kkossev.deviceProfileLib, line 137
} // library marker kkossev.deviceProfileLib, line 138

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 140
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 141
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 142
    def searchMap = [] // library marker kkossev.deviceProfileLib, line 143
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 144
    if (DEVICE?.tuyaDPs != null) { // library marker kkossev.deviceProfileLib, line 145
        searchMap =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 146
        foundMap = searchMap.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 147
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 148
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 149
            return foundMap // library marker kkossev.deviceProfileLib, line 150
        } // library marker kkossev.deviceProfileLib, line 151
    } // library marker kkossev.deviceProfileLib, line 152
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 153
    if (DEVICE?.attributes != null) { // library marker kkossev.deviceProfileLib, line 154
        searchMap  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 155
        foundMap = searchMap.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 156
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 157
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 158
            return foundMap // library marker kkossev.deviceProfileLib, line 159
        } // library marker kkossev.deviceProfileLib, line 160
    } // library marker kkossev.deviceProfileLib, line 161
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 162
    return [:] // library marker kkossev.deviceProfileLib, line 163
} // library marker kkossev.deviceProfileLib, line 164

/** // library marker kkossev.deviceProfileLib, line 166
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 167
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 168
 */ // library marker kkossev.deviceProfileLib, line 169
void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 170
    logTrace "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 171
    Map preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 172
    if (preferences == null || preferences.isEmpty()) { // library marker kkossev.deviceProfileLib, line 173
        logDebug 'Preferences not found!' // library marker kkossev.deviceProfileLib, line 174
        return // library marker kkossev.deviceProfileLib, line 175
    } // library marker kkossev.deviceProfileLib, line 176
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 177
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 178
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 179
        // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 180
        if (mapValue in [true, false]) { // library marker kkossev.deviceProfileLib, line 181
            logDebug "Preference ${parName} is predefined -> (${mapValue})" // library marker kkossev.deviceProfileLib, line 182
            // TODO - set the predefined value // library marker kkossev.deviceProfileLib, line 183
            /* // library marker kkossev.deviceProfileLib, line 184
            if (debug) log.info "par ${parName} defVal = ${parMap.defVal}" // library marker kkossev.deviceProfileLib, line 185
            device.updateSetting("${parMap.name}",[value:parMap.defVal, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 186
            */ // library marker kkossev.deviceProfileLib, line 187
            return // continue // library marker kkossev.deviceProfileLib, line 188
        } // library marker kkossev.deviceProfileLib, line 189
        // find the individual preference map // library marker kkossev.deviceProfileLib, line 190
        parMap = getPreferencesMapByName(parName, false) // library marker kkossev.deviceProfileLib, line 191
        if (parMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 192
            logDebug "Preference ${parName} not found in tuyaDPs or attributes map!" // library marker kkossev.deviceProfileLib, line 193
            return // continue // library marker kkossev.deviceProfileLib, line 194
        } // library marker kkossev.deviceProfileLib, line 195
        // parMap = [at:0xE002:0xE005, name:staticDetectionSensitivity, type:number, dt:UINT8, rw:rw, min:0, max:5, scale:1, unit:x, title:Static Detection Sensitivity, description:Static detection sensitivity] // library marker kkossev.deviceProfileLib, line 196
        if (parMap.defVal == null) { // library marker kkossev.deviceProfileLib, line 197
            logDebug "no default value for preference ${parName} !" // library marker kkossev.deviceProfileLib, line 198
            return // continue // library marker kkossev.deviceProfileLib, line 199
        } // library marker kkossev.deviceProfileLib, line 200
        if (debug) { log.info "par ${parName} defVal = ${parMap.defVal}" } // library marker kkossev.deviceProfileLib, line 201
        device.updateSetting("${parMap.name}", [value:parMap.defVal, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 202
    } // library marker kkossev.deviceProfileLib, line 203
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 204
} // library marker kkossev.deviceProfileLib, line 205

/** // library marker kkossev.deviceProfileLib, line 207
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 208
 * // library marker kkossev.deviceProfileLib, line 209
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 210
 */ // library marker kkossev.deviceProfileLib, line 211
List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 212
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 213
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 214
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 215
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 216
    } // library marker kkossev.deviceProfileLib, line 217
    return validPars // library marker kkossev.deviceProfileLib, line 218
} // library marker kkossev.deviceProfileLib, line 219

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 221
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 222
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 223
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 224
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 225
    def scaledValue // library marker kkossev.deviceProfileLib, line 226
    if (value == null) { // library marker kkossev.deviceProfileLib, line 227
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 228
        return null // library marker kkossev.deviceProfileLib, line 229
    } // library marker kkossev.deviceProfileLib, line 230
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 231
        case 'number' : // library marker kkossev.deviceProfileLib, line 232
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 233
            break // library marker kkossev.deviceProfileLib, line 234
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 235
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 236
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 237
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 238
            } // library marker kkossev.deviceProfileLib, line 239
            break // library marker kkossev.deviceProfileLib, line 240
        case 'bool' : // library marker kkossev.deviceProfileLib, line 241
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 242
            break // library marker kkossev.deviceProfileLib, line 243
        case 'enum' : // library marker kkossev.deviceProfileLib, line 244
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 245
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 246
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 247
                return null // library marker kkossev.deviceProfileLib, line 248
            } // library marker kkossev.deviceProfileLib, line 249
            scaledValue = value // library marker kkossev.deviceProfileLib, line 250
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 251
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 252
            } // library marker kkossev.deviceProfileLib, line 253
            break // library marker kkossev.deviceProfileLib, line 254
        default : // library marker kkossev.deviceProfileLib, line 255
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 256
            return null // library marker kkossev.deviceProfileLib, line 257
    } // library marker kkossev.deviceProfileLib, line 258
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 259
    return scaledValue // library marker kkossev.deviceProfileLib, line 260
} // library marker kkossev.deviceProfileLib, line 261

// called from updated() method // library marker kkossev.deviceProfileLib, line 263
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 264
void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 265
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 266
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 267
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 268
        return // library marker kkossev.deviceProfileLib, line 269
    } // library marker kkossev.deviceProfileLib, line 270
    //Integer dpInt = 0 // library marker kkossev.deviceProfileLib, line 271
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 272
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 273
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 274
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 275
        Map foundMap // library marker kkossev.deviceProfileLib, line 276
        foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 277
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 278

        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 280
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 281
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 282
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 283
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 284
                // scale the value // library marker kkossev.deviceProfileLib, line 285
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 286
            } // library marker kkossev.deviceProfileLib, line 287
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLib, line 288
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLib, line 289
            } // library marker kkossev.deviceProfileLib, line 290
            else { // library marker kkossev.deviceProfileLib, line 291
                logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" // library marker kkossev.deviceProfileLib, line 292
                return // library marker kkossev.deviceProfileLib, line 293
            } // library marker kkossev.deviceProfileLib, line 294
        } // library marker kkossev.deviceProfileLib, line 295
        else { // library marker kkossev.deviceProfileLib, line 296
            logDebug "warning: couldn't find map for preference ${name}" // library marker kkossev.deviceProfileLib, line 297
            return // library marker kkossev.deviceProfileLib, line 298
        } // library marker kkossev.deviceProfileLib, line 299
    } // library marker kkossev.deviceProfileLib, line 300
    return // library marker kkossev.deviceProfileLib, line 301
} // library marker kkossev.deviceProfileLib, line 302

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 304
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 305
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 306
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 307
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 308
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 309
} // library marker kkossev.deviceProfileLib, line 310
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 311
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 312
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 313
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 314
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 315
} // library marker kkossev.deviceProfileLib, line 316

/** // library marker kkossev.deviceProfileLib, line 318
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 319
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 320
 * // library marker kkossev.deviceProfileLib, line 321
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 322
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 323
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 324
 */ // library marker kkossev.deviceProfileLib, line 325
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 326
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 327
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 328
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 329
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 330
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 331
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 332
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 333
        case 'number' : // library marker kkossev.deviceProfileLib, line 334
            value = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 335
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 336
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 337
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 338
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 339
            } // library marker kkossev.deviceProfileLib, line 340
            else { // library marker kkossev.deviceProfileLib, line 341
                scaledValue = value // library marker kkossev.deviceProfileLib, line 342
            } // library marker kkossev.deviceProfileLib, line 343
            break // library marker kkossev.deviceProfileLib, line 344

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 346
            value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 347
            // scale the value // library marker kkossev.deviceProfileLib, line 348
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 349
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 350
            } // library marker kkossev.deviceProfileLib, line 351
            else { // library marker kkossev.deviceProfileLib, line 352
                scaledValue = value // library marker kkossev.deviceProfileLib, line 353
            } // library marker kkossev.deviceProfileLib, line 354
            break // library marker kkossev.deviceProfileLib, line 355

        case 'bool' : // library marker kkossev.deviceProfileLib, line 357
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 358
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 359
            else { // library marker kkossev.deviceProfileLib, line 360
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 361
                return null // library marker kkossev.deviceProfileLib, line 362
            } // library marker kkossev.deviceProfileLib, line 363
            break // library marker kkossev.deviceProfileLib, line 364
        case 'enum' : // library marker kkossev.deviceProfileLib, line 365
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 366
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 367
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 368
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 369
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 370
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 371
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 372
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 373
            } // library marker kkossev.deviceProfileLib, line 374
            else { // library marker kkossev.deviceProfileLib, line 375
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 376
            } // library marker kkossev.deviceProfileLib, line 377
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 378
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 379
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 380
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 381
                // find the key for the value // library marker kkossev.deviceProfileLib, line 382
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 383
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 384
                if (key == null) { // library marker kkossev.deviceProfileLib, line 385
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 386
                    return null // library marker kkossev.deviceProfileLib, line 387
                } // library marker kkossev.deviceProfileLib, line 388
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 389
            //return null // library marker kkossev.deviceProfileLib, line 390
            } // library marker kkossev.deviceProfileLib, line 391
            break // library marker kkossev.deviceProfileLib, line 392
        default : // library marker kkossev.deviceProfileLib, line 393
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 394
            return null // library marker kkossev.deviceProfileLib, line 395
    } // library marker kkossev.deviceProfileLib, line 396
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 397
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 398
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 399
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 400
        return null // library marker kkossev.deviceProfileLib, line 401
    } // library marker kkossev.deviceProfileLib, line 402
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 403
    return scaledValue // library marker kkossev.deviceProfileLib, line 404
} // library marker kkossev.deviceProfileLib, line 405

/** // library marker kkossev.deviceProfileLib, line 407
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 408
 * // library marker kkossev.deviceProfileLib, line 409
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 410
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 411
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 412
 */ // library marker kkossev.deviceProfileLib, line 413
boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 414
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 415
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 416
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 417
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 418
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 419
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 420
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 421
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 422
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 423
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 424
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 425
    if (scaledValue == null) { logInfo "setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 426
    /* // library marker kkossev.deviceProfileLib, line 427
    // update the device setting // TODO: decide whether the setting must be updated here, or after it is echeod back from the device // library marker kkossev.deviceProfileLib, line 428
    try { // library marker kkossev.deviceProfileLib, line 429
        device.updateSetting("$par", [value:val, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 430
    } // library marker kkossev.deviceProfileLib, line 431
    catch (e) { // library marker kkossev.deviceProfileLib, line 432
        logWarn "setPar: Exception '${e}'caught while updateSetting <b>$par</b>(<b>$val</b>) type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 433
        return false // library marker kkossev.deviceProfileLib, line 434
    } // library marker kkossev.deviceProfileLib, line 435
    */ // library marker kkossev.deviceProfileLib, line 436
    //logDebug "setPar: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 437
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 438
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 439
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 440
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 441
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 442
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 443
        try { // library marker kkossev.deviceProfileLib, line 444
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 445
        } // library marker kkossev.deviceProfileLib, line 446
        catch (e) { // library marker kkossev.deviceProfileLib, line 447
            logWarn "setPar: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 448
            return false // library marker kkossev.deviceProfileLib, line 449
        } // library marker kkossev.deviceProfileLib, line 450
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 451
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 452
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 453
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 454
            return false // library marker kkossev.deviceProfileLib, line 455
        } // library marker kkossev.deviceProfileLib, line 456
        else { // library marker kkossev.deviceProfileLib, line 457
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 458
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 459
        } // library marker kkossev.deviceProfileLib, line 460
    } // library marker kkossev.deviceProfileLib, line 461
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 462
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 463
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 464
        def valMiscType // library marker kkossev.deviceProfileLib, line 465
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 466
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 467
            // find the key for the value // library marker kkossev.deviceProfileLib, line 468
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 469
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 470
            if (key == null) { // library marker kkossev.deviceProfileLib, line 471
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 472
                return false // library marker kkossev.deviceProfileLib, line 473
            } // library marker kkossev.deviceProfileLib, line 474
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 475
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 476
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 477
        } // library marker kkossev.deviceProfileLib, line 478
        else { // library marker kkossev.deviceProfileLib, line 479
            valMiscType = val // library marker kkossev.deviceProfileLib, line 480
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 481
        } // library marker kkossev.deviceProfileLib, line 482
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 483
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 484
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 485
        return true // library marker kkossev.deviceProfileLib, line 486
    } // library marker kkossev.deviceProfileLib, line 487

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 489
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 490

    try { // library marker kkossev.deviceProfileLib, line 492
        // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 493
        /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 494
        isTuyaDP = dpMap.dp instanceof Number // library marker kkossev.deviceProfileLib, line 495
    } // library marker kkossev.deviceProfileLib, line 496
    catch (e) { // library marker kkossev.deviceProfileLib, line 497
        logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" // library marker kkossev.deviceProfileLib, line 498
        isTuyaDP = false // library marker kkossev.deviceProfileLib, line 499
    //return false // library marker kkossev.deviceProfileLib, line 500
    } // library marker kkossev.deviceProfileLib, line 501
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 502
        // Tuya DP // library marker kkossev.deviceProfileLib, line 503
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 504
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 505
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 506
            return false // library marker kkossev.deviceProfileLib, line 507
        } // library marker kkossev.deviceProfileLib, line 508
        else { // library marker kkossev.deviceProfileLib, line 509
            logInfo "setPar: (2) successfluly executed setPar <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 510
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 511
            return false // library marker kkossev.deviceProfileLib, line 512
        } // library marker kkossev.deviceProfileLib, line 513
    } // library marker kkossev.deviceProfileLib, line 514
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 515
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 516
        int cluster // library marker kkossev.deviceProfileLib, line 517
        int attribute // library marker kkossev.deviceProfileLib, line 518
        int dt // library marker kkossev.deviceProfileLib, line 519
        String mfgCode // library marker kkossev.deviceProfileLib, line 520
        try { // library marker kkossev.deviceProfileLib, line 521
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[0]) // library marker kkossev.deviceProfileLib, line 522
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 523
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[1]) // library marker kkossev.deviceProfileLib, line 524
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 525
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 526
            //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 527
            mfgCode = dpMap.mfgCode // library marker kkossev.deviceProfileLib, line 528
        //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 529
        } // library marker kkossev.deviceProfileLib, line 530
        catch (e) { // library marker kkossev.deviceProfileLib, line 531
            logWarn "setPar: Exception '${e}' caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 532
            return false // library marker kkossev.deviceProfileLib, line 533
        } // library marker kkossev.deviceProfileLib, line 534
        Map mapMfCode = ['mfgCode':mfgCode] // library marker kkossev.deviceProfileLib, line 535
        logDebug "setPar: found cluster=0x${zigbee.convertToHexString(cluster, 2)} attribute=0x${zigbee.convertToHexString(attribute, 2)} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 536
        if (mfgCode != null) { // library marker kkossev.deviceProfileLib, line 537
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay = 200) // library marker kkossev.deviceProfileLib, line 538
        } // library marker kkossev.deviceProfileLib, line 539
        else { // library marker kkossev.deviceProfileLib, line 540
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 541
        } // library marker kkossev.deviceProfileLib, line 542
    } // library marker kkossev.deviceProfileLib, line 543
    else { // library marker kkossev.deviceProfileLib, line 544
        logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 545
        return false // library marker kkossev.deviceProfileLib, line 546
    } // library marker kkossev.deviceProfileLib, line 547
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 548
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 549
    return true // library marker kkossev.deviceProfileLib, line 550
} // library marker kkossev.deviceProfileLib, line 551

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 553
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 554
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 555
List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 556
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 557
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 558
    if (dpMap == null) { // library marker kkossev.deviceProfileLib, line 559
        logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 560
        return [] // library marker kkossev.deviceProfileLib, line 561
    } // library marker kkossev.deviceProfileLib, line 562
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 563
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 564
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 565
        return [] // library marker kkossev.deviceProfileLib, line 566
    } // library marker kkossev.deviceProfileLib, line 567
    String dpType // library marker kkossev.deviceProfileLib, line 568
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 569
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 570
    } // library marker kkossev.deviceProfileLib, line 571
    else { // library marker kkossev.deviceProfileLib, line 572
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 573
    } // library marker kkossev.deviceProfileLib, line 574
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 575
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 576
        return [] // library marker kkossev.deviceProfileLib, line 577
    } // library marker kkossev.deviceProfileLib, line 578
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 579
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 580
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 581
    cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 582
    return cmds // library marker kkossev.deviceProfileLib, line 583
} // library marker kkossev.deviceProfileLib, line 584

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 586
boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 587
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 588
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 589
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 590
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 591

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 593
    if (dpMap == null || dpMap.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 594
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 595
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 596
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 597
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 598
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 599
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 600
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 601
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 602
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 603
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 604
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 605
        try { // library marker kkossev.deviceProfileLib, line 606
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 607
        } // library marker kkossev.deviceProfileLib, line 608
        catch (e) { // library marker kkossev.deviceProfileLib, line 609
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 610
            return false // library marker kkossev.deviceProfileLib, line 611
        } // library marker kkossev.deviceProfileLib, line 612
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 613
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 614
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 615
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 616
            return true // library marker kkossev.deviceProfileLib, line 617
        } // library marker kkossev.deviceProfileLib, line 618
        else { // library marker kkossev.deviceProfileLib, line 619
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 620
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 621
        } // library marker kkossev.deviceProfileLib, line 622
    } // library marker kkossev.deviceProfileLib, line 623
    else { // library marker kkossev.deviceProfileLib, line 624
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 625
    } // library marker kkossev.deviceProfileLib, line 626
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 627
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 628
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 629
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 630
        // patch !! // library marker kkossev.deviceProfileLib, line 631
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 632
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 633
        } // library marker kkossev.deviceProfileLib, line 634
        else { // library marker kkossev.deviceProfileLib, line 635
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 636
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 637
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 638
        } // library marker kkossev.deviceProfileLib, line 639
        return true // library marker kkossev.deviceProfileLib, line 640
    } // library marker kkossev.deviceProfileLib, line 641
    else { // library marker kkossev.deviceProfileLib, line 642
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 643
    } // library marker kkossev.deviceProfileLib, line 644
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 645
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 646
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 647
    try { // library marker kkossev.deviceProfileLib, line 648
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 649
    } // library marker kkossev.deviceProfileLib, line 650
    catch (e) { // library marker kkossev.deviceProfileLib, line 651
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 652
        return false // library marker kkossev.deviceProfileLib, line 653
    } // library marker kkossev.deviceProfileLib, line 654
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 655
        // Tuya DP // library marker kkossev.deviceProfileLib, line 656
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 657
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 658
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 659
            return false // library marker kkossev.deviceProfileLib, line 660
        } // library marker kkossev.deviceProfileLib, line 661
        else { // library marker kkossev.deviceProfileLib, line 662
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 663
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 664
            return true // library marker kkossev.deviceProfileLib, line 665
        } // library marker kkossev.deviceProfileLib, line 666
    } // library marker kkossev.deviceProfileLib, line 667
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 668
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 669
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 670
    } // library marker kkossev.deviceProfileLib, line 671
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 672
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 673
        int cluster // library marker kkossev.deviceProfileLib, line 674
        int attribute // library marker kkossev.deviceProfileLib, line 675
        int dt // library marker kkossev.deviceProfileLib, line 676
        // int mfgCode // library marker kkossev.deviceProfileLib, line 677
        try { // library marker kkossev.deviceProfileLib, line 678
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[0]) // library marker kkossev.deviceProfileLib, line 679
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 680
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[1]) // library marker kkossev.deviceProfileLib, line 681
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 682
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 683
        //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 684
        //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 685
        //  mfgCode = dpMap.mfgCode != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.mfgCode) : null // library marker kkossev.deviceProfileLib, line 686
        //  log.trace "mfgCode = ${mfgCode}" // library marker kkossev.deviceProfileLib, line 687
        } // library marker kkossev.deviceProfileLib, line 688
        catch (e) { // library marker kkossev.deviceProfileLib, line 689
            logWarn "sendAttribute: Exception '${e}'caught while splitting cluster and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 690
            return false // library marker kkossev.deviceProfileLib, line 691
        } // library marker kkossev.deviceProfileLib, line 692

        logDebug "sendAttribute: found cluster=${cluster} attribute=${attribute} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 694
        if (dpMap.mfgCode != null) { // library marker kkossev.deviceProfileLib, line 695
            Map mapMfCode = ['mfgCode':dpMap.mfgCode] // library marker kkossev.deviceProfileLib, line 696
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay = 200) // library marker kkossev.deviceProfileLib, line 697
        } // library marker kkossev.deviceProfileLib, line 698
        else { // library marker kkossev.deviceProfileLib, line 699
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 700
        } // library marker kkossev.deviceProfileLib, line 701
    } // library marker kkossev.deviceProfileLib, line 702
    else { // library marker kkossev.deviceProfileLib, line 703
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 704
        return false // library marker kkossev.deviceProfileLib, line 705
    } // library marker kkossev.deviceProfileLib, line 706
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 707
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 708
    return true // library marker kkossev.deviceProfileLib, line 709
} // library marker kkossev.deviceProfileLib, line 710

/** // library marker kkossev.deviceProfileLib, line 712
 * Sends a command to the device. // library marker kkossev.deviceProfileLib, line 713
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 714
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 715
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 716
 */ // library marker kkossev.deviceProfileLib, line 717
boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 718
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 719
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 720
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 721
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 722
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 723
    if (supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 724
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 725
        return false // library marker kkossev.deviceProfileLib, line 726
    } // library marker kkossev.deviceProfileLib, line 727
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 728
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 729
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 730
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 731
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 732
        return false // library marker kkossev.deviceProfileLib, line 733
    } // library marker kkossev.deviceProfileLib, line 734
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 735
    def func // library marker kkossev.deviceProfileLib, line 736
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 737
    def funcResult // library marker kkossev.deviceProfileLib, line 738
    try { // library marker kkossev.deviceProfileLib, line 739
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 740
        if (val != null) { // library marker kkossev.deviceProfileLib, line 741
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 742
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 743
        } // library marker kkossev.deviceProfileLib, line 744
        else { // library marker kkossev.deviceProfileLib, line 745
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 746
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 747
        } // library marker kkossev.deviceProfileLib, line 748
    } // library marker kkossev.deviceProfileLib, line 749
    catch (e) { // library marker kkossev.deviceProfileLib, line 750
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 751
        return false // library marker kkossev.deviceProfileLib, line 752
    } // library marker kkossev.deviceProfileLib, line 753
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 754
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 755
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 756
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 757
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 758
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 759
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 760
        } // library marker kkossev.deviceProfileLib, line 761
    } else { // library marker kkossev.deviceProfileLib, line 762
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 763
        return false // library marker kkossev.deviceProfileLib, line 764
    } // library marker kkossev.deviceProfileLib, line 765
    cmds = funcResult // library marker kkossev.deviceProfileLib, line 766
    if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 767
        sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 768
    } // library marker kkossev.deviceProfileLib, line 769
    return true // library marker kkossev.deviceProfileLib, line 770
} // library marker kkossev.deviceProfileLib, line 771

/** // library marker kkossev.deviceProfileLib, line 773
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 774
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 775
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 776
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 777
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 778
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 779
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 780
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 781
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 782
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 783
 */ // library marker kkossev.deviceProfileLib, line 784
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 785
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 786
    Map input = [:] // library marker kkossev.deviceProfileLib, line 787
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 788
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 789
        if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 790
        return [:] // library marker kkossev.deviceProfileLib, line 791
    } // library marker kkossev.deviceProfileLib, line 792
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 793
    def preference // library marker kkossev.deviceProfileLib, line 794
    try { // library marker kkossev.deviceProfileLib, line 795
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 796
    } // library marker kkossev.deviceProfileLib, line 797
    catch (e) { // library marker kkossev.deviceProfileLib, line 798
        if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 799
        return [:] // library marker kkossev.deviceProfileLib, line 800
    } // library marker kkossev.deviceProfileLib, line 801
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 802
    try { // library marker kkossev.deviceProfileLib, line 803
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 804
            if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } // library marker kkossev.deviceProfileLib, line 805
            return [:] // library marker kkossev.deviceProfileLib, line 806
        } // library marker kkossev.deviceProfileLib, line 807
    } // library marker kkossev.deviceProfileLib, line 808
    catch (e) { // library marker kkossev.deviceProfileLib, line 809
        if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 810
        return [:] // library marker kkossev.deviceProfileLib, line 811
    } // library marker kkossev.deviceProfileLib, line 812

    try { // library marker kkossev.deviceProfileLib, line 814
        isTuyaDP = preference.isNumber() // library marker kkossev.deviceProfileLib, line 815
    } // library marker kkossev.deviceProfileLib, line 816
    catch (e) { // library marker kkossev.deviceProfileLib, line 817
        if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 818
        return [:] // library marker kkossev.deviceProfileLib, line 819
    } // library marker kkossev.deviceProfileLib, line 820

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 822
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 823
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 824
    if (foundMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 825
        if (debug) { log.warn "inputIt: map not found for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 826
        return [:] // library marker kkossev.deviceProfileLib, line 827
    } // library marker kkossev.deviceProfileLib, line 828
    if (foundMap.rw != 'rw') { // library marker kkossev.deviceProfileLib, line 829
        if (debug) { log.warn "inputIt: param '${param}' is read only!" } // library marker kkossev.deviceProfileLib, line 830
        return [:] // library marker kkossev.deviceProfileLib, line 831
    } // library marker kkossev.deviceProfileLib, line 832
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 833
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 834
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 835
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 836
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 837
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 838
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 839
        } // library marker kkossev.deviceProfileLib, line 840
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 841
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 842
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 843
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 844
            } // library marker kkossev.deviceProfileLib, line 845
        } // library marker kkossev.deviceProfileLib, line 846
    } // library marker kkossev.deviceProfileLib, line 847
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 848
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 849
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 850
    }/* // library marker kkossev.deviceProfileLib, line 851
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 852
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 853
    }*/ // library marker kkossev.deviceProfileLib, line 854
    else { // library marker kkossev.deviceProfileLib, line 855
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 856
        return [:] // library marker kkossev.deviceProfileLib, line 857
    } // library marker kkossev.deviceProfileLib, line 858
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 859
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 860
    } // library marker kkossev.deviceProfileLib, line 861
    return input // library marker kkossev.deviceProfileLib, line 862
} // library marker kkossev.deviceProfileLib, line 863

/** // library marker kkossev.deviceProfileLib, line 865
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 866
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 867
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 868
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 869
 */ // library marker kkossev.deviceProfileLib, line 870
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 871
    String deviceName         = UNKNOWN // library marker kkossev.deviceProfileLib, line 872
    String deviceProfile      = UNKNOWN // library marker kkossev.deviceProfileLib, line 873
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 874
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 875
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 876
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 877
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 878
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 879
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 880
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 881
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 882
            } // library marker kkossev.deviceProfileLib, line 883
        } // library marker kkossev.deviceProfileLib, line 884
    } // library marker kkossev.deviceProfileLib, line 885
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 886
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 887
    } // library marker kkossev.deviceProfileLib, line 888
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 889
} // library marker kkossev.deviceProfileLib, line 890

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 892
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 893
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 894
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 895
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 896
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 897
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 898
    } // library marker kkossev.deviceProfileLib, line 899
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 900
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 901
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 902
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 903
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 904
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 905
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 906
    } else { // library marker kkossev.deviceProfileLib, line 907
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 908
    } // library marker kkossev.deviceProfileLib, line 909
} // library marker kkossev.deviceProfileLib, line 910

List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 912
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 913
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 914
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 915
    return cmds // library marker kkossev.deviceProfileLib, line 916
} // library marker kkossev.deviceProfileLib, line 917

List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 919
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 920
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 921
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.deviceProfileLib, line 922
    return cmds // library marker kkossev.deviceProfileLib, line 923
} // library marker kkossev.deviceProfileLib, line 924

List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 926
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 927
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 928
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 929
    return cmds // library marker kkossev.deviceProfileLib, line 930
} // library marker kkossev.deviceProfileLib, line 931

void initVarsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 933
    logDebug "initVarsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 934
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 935
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 936
    } // library marker kkossev.deviceProfileLib, line 937
} // library marker kkossev.deviceProfileLib, line 938

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 940
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 941
} // library marker kkossev.deviceProfileLib, line 942

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 944

// // library marker kkossev.deviceProfileLib, line 946
// called from parse() // library marker kkossev.deviceProfileLib, line 947
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 948
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 949
// // library marker kkossev.deviceProfileLib, line 950
boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 951
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 952
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 953
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 954
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 955
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 956
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 957
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 958
} // library marker kkossev.deviceProfileLib, line 959

// // library marker kkossev.deviceProfileLib, line 961
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 962
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 963
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 964
// // library marker kkossev.deviceProfileLib, line 965
boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 966
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 967
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 968
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 969
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 970
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 971
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 972
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 973
} // library marker kkossev.deviceProfileLib, line 974

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 976
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 977
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 978
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 979
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 980
        log.warn "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 981
    } // library marker kkossev.deviceProfileLib, line 982
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 983
} // library marker kkossev.deviceProfileLib, line 984

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 986
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 987
    boolean isEqual // library marker kkossev.deviceProfileLib, line 988
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 989
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 990
    } // library marker kkossev.deviceProfileLib, line 991
    else { // library marker kkossev.deviceProfileLib, line 992
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 993
    } // library marker kkossev.deviceProfileLib, line 994
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 995
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 996
} // library marker kkossev.deviceProfileLib, line 997

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 999
    Double convertedValue // library marker kkossev.deviceProfileLib, line 1000
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1001
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 1002
    } // library marker kkossev.deviceProfileLib, line 1003
    else { // library marker kkossev.deviceProfileLib, line 1004
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1005
    } // library marker kkossev.deviceProfileLib, line 1006
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1007
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1008
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1009
} // library marker kkossev.deviceProfileLib, line 1010

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1012
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1013
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1014
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1015
    def convertedValue // library marker kkossev.deviceProfileLib, line 1016
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1017
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1018
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1019
    } // library marker kkossev.deviceProfileLib, line 1020
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1021
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1022
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1023
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1024
            isEqual = false // library marker kkossev.deviceProfileLib, line 1025
        } // library marker kkossev.deviceProfileLib, line 1026
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1027
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1028
        } // library marker kkossev.deviceProfileLib, line 1029
    } // library marker kkossev.deviceProfileLib, line 1030
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1031
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1032
} // library marker kkossev.deviceProfileLib, line 1033

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1035
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1036
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1037
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1038
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1039
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1040
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1041
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1042
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1043
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1044
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1045
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1046
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1047
            break // library marker kkossev.deviceProfileLib, line 1048
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1049
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1050
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1051
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1052
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1053
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1054
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1055
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1056
            } // library marker kkossev.deviceProfileLib, line 1057
            else { // library marker kkossev.deviceProfileLib, line 1058
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1059
            } // library marker kkossev.deviceProfileLib, line 1060
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1061
            break // library marker kkossev.deviceProfileLib, line 1062
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1063
        case 'number' : // library marker kkossev.deviceProfileLib, line 1064
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1065
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1066
            break // library marker kkossev.deviceProfileLib, line 1067
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1068
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1069
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1070
            break // library marker kkossev.deviceProfileLib, line 1071
        default : // library marker kkossev.deviceProfileLib, line 1072
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1073
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1074
    } // library marker kkossev.deviceProfileLib, line 1075
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1076
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1077
    } // library marker kkossev.deviceProfileLib, line 1078
    // // library marker kkossev.deviceProfileLib, line 1079
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1080
} // library marker kkossev.deviceProfileLib, line 1081

// // library marker kkossev.deviceProfileLib, line 1083
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1084
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1085
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1086
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1087
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1088
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1089
// // library marker kkossev.deviceProfileLib, line 1090
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1091
// // library marker kkossev.deviceProfileLib, line 1092
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1093
// // library marker kkossev.deviceProfileLib, line 1094
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1095
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1096
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1097
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1098
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1099
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1100
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1101
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1102
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1103
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1104
            break // library marker kkossev.deviceProfileLib, line 1105
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1106
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(foundItem.name)=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1107
            (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1108
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1109
            break // library marker kkossev.deviceProfileLib, line 1110
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1111
        case 'number' : // library marker kkossev.deviceProfileLib, line 1112
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1113
            break // library marker kkossev.deviceProfileLib, line 1114
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1115
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1116
            break // library marker kkossev.deviceProfileLib, line 1117
        default : // library marker kkossev.deviceProfileLib, line 1118
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1119
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1120
    } // library marker kkossev.deviceProfileLib, line 1121
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1122
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1123
} // library marker kkossev.deviceProfileLib, line 1124

Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1126
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1127
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1128
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1129
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1130
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1131
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1132
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1133
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1134
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1135
    } // library marker kkossev.deviceProfileLib, line 1136
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1137
    try { // library marker kkossev.deviceProfileLib, line 1138
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1139
    } // library marker kkossev.deviceProfileLib, line 1140
    catch (e) { // library marker kkossev.deviceProfileLib, line 1141
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1142
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1143
    } // library marker kkossev.deviceProfileLib, line 1144
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1145
    return fncmd // library marker kkossev.deviceProfileLib, line 1146
} // library marker kkossev.deviceProfileLib, line 1147

/** // library marker kkossev.deviceProfileLib, line 1149
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1150
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1151
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1152
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1153
 * // library marker kkossev.deviceProfileLib, line 1154
 * @param descMap The description map of the received DP. // library marker kkossev.deviceProfileLib, line 1155
 * @param dp The value of the received DP. // library marker kkossev.deviceProfileLib, line 1156
 * @param dp_id The ID of the received DP. // library marker kkossev.deviceProfileLib, line 1157
 * @param fncmd The command of the received DP. // library marker kkossev.deviceProfileLib, line 1158
 * @param dp_len The length of the received DP. // library marker kkossev.deviceProfileLib, line 1159
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1160
 */ // library marker kkossev.deviceProfileLib, line 1161
boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1162
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1163
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1164
    //if (!(DEVICE?.device?.type == "radar"))      { return false }   // enabled for all devices - 10/22/2023 !!!    // only these models are handled here for now ... // library marker kkossev.deviceProfileLib, line 1165
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1166

    Map tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs as Map // library marker kkossev.deviceProfileLib, line 1168
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1169

    Map foundItem = null // library marker kkossev.deviceProfileLib, line 1171
    tuyaDPsMap.each { item -> // library marker kkossev.deviceProfileLib, line 1172
        if (item['dp'] == (dp as int)) { // library marker kkossev.deviceProfileLib, line 1173
            foundItem = item // library marker kkossev.deviceProfileLib, line 1174
            return // library marker kkossev.deviceProfileLib, line 1175
        } // library marker kkossev.deviceProfileLib, line 1176
    } // library marker kkossev.deviceProfileLib, line 1177
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1178
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1179
        updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len) // library marker kkossev.deviceProfileLib, line 1180
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1181
        return false // library marker kkossev.deviceProfileLib, line 1182
    } // library marker kkossev.deviceProfileLib, line 1183

    return processFoundItem(foundItem, fncmd_orig) // library marker kkossev.deviceProfileLib, line 1185
} // library marker kkossev.deviceProfileLib, line 1186

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1188
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1189
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1190
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1191

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1193
    if (attribMap == null || attribMap.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1194

    Map foundItem = null // library marker kkossev.deviceProfileLib, line 1196
    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1197
    int value // library marker kkossev.deviceProfileLib, line 1198
    try { // library marker kkossev.deviceProfileLib, line 1199
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1200
    } // library marker kkossev.deviceProfileLib, line 1201
    catch (e) { // library marker kkossev.deviceProfileLib, line 1202
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1203
        return false // library marker kkossev.deviceProfileLib, line 1204
    } // library marker kkossev.deviceProfileLib, line 1205
    //logTrace "clusterAttribute = ${clusterAttribute}" // library marker kkossev.deviceProfileLib, line 1206
    attribMap.each { item -> // library marker kkossev.deviceProfileLib, line 1207
        if (item['at'] == clusterAttribute) { // library marker kkossev.deviceProfileLib, line 1208
            foundItem = item // library marker kkossev.deviceProfileLib, line 1209
            return // library marker kkossev.deviceProfileLib, line 1210
        } // library marker kkossev.deviceProfileLib, line 1211
    } // library marker kkossev.deviceProfileLib, line 1212
    if (foundItem == null) { // library marker kkossev.deviceProfileLib, line 1213
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1214
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1215
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1216
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1217
        return false // library marker kkossev.deviceProfileLib, line 1218
    } // library marker kkossev.deviceProfileLib, line 1219
    return processFoundItem(foundItem, value) // library marker kkossev.deviceProfileLib, line 1220
} // library marker kkossev.deviceProfileLib, line 1221

// modifies the value of the foundItem if needed !!! // library marker kkossev.deviceProfileLib, line 1223
/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.deviceProfileLib, line 1224
boolean processFoundItem(final Map foundItem, int value) { // library marker kkossev.deviceProfileLib, line 1225
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1226
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1227
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1228
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1229
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1230
        if (preProcValue == null) { // library marker kkossev.deviceProfileLib, line 1231
            logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" // library marker kkossev.deviceProfileLib, line 1232
            return true // library marker kkossev.deviceProfileLib, line 1233
        } // library marker kkossev.deviceProfileLib, line 1234
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1235
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1236
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1237
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1238
        } // library marker kkossev.deviceProfileLib, line 1239
    } // library marker kkossev.deviceProfileLib, line 1240
    else { // library marker kkossev.deviceProfileLib, line 1241
        logTrace "processFoundItem: no preProc for ${foundItem.name}" // library marker kkossev.deviceProfileLib, line 1242
    } // library marker kkossev.deviceProfileLib, line 1243

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1245
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1246
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1247
    //existingPrefValue = existingPrefValue?.replace("[", "").replace("]", "")               // preference name as in Hubitat settings (preferences), if already created. // library marker kkossev.deviceProfileLib, line 1248
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1249
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1250
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1251
    //boolean preferenceExists = settings.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1252
    boolean preferenceExists = DEVICE?.preferences?.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1253
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1254
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1255
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1256
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1257
    boolean doNotTrace = false  // isSpammyDPsToNotTrace(descMap)          // do not log/trace the spammy clusterAttribute's TODO! // library marker kkossev.deviceProfileLib, line 1258
    if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1259
        logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" // library marker kkossev.deviceProfileLib, line 1260
    } // library marker kkossev.deviceProfileLib, line 1261
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1262
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1263
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1264
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1265
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1266
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1267

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1269
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1270
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1271
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1272
            (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1273
            descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1274
            if (settings.logEnable) { logInfo "${descText }" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1275
        } // library marker kkossev.deviceProfileLib, line 1276
        // no more processing is needed, as this clusterAttribute is not a preference and not an attribute // library marker kkossev.deviceProfileLib, line 1277
        return true // library marker kkossev.deviceProfileLib, line 1278
    } // library marker kkossev.deviceProfileLib, line 1279

    // first, check if there is a preference defined to be updated // library marker kkossev.deviceProfileLib, line 1281
    if (preferenceExists) { // library marker kkossev.deviceProfileLib, line 1282
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1283
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1284
        //log.trace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1285
        if (isEqual == true) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1286
            logDebug "no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1287
        } // library marker kkossev.deviceProfileLib, line 1288
        else { // library marker kkossev.deviceProfileLib, line 1289
            String scaledPreferenceValue = preferenceValue      //.toString() is not neccessary // library marker kkossev.deviceProfileLib, line 1290
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1291
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1292
            } // library marker kkossev.deviceProfileLib, line 1293
            logDebug "preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1294
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1295
            try { // library marker kkossev.deviceProfileLib, line 1296
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1297
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1298
            } // library marker kkossev.deviceProfileLib, line 1299
            catch (e) { // library marker kkossev.deviceProfileLib, line 1300
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1301
            } // library marker kkossev.deviceProfileLib, line 1302
        } // library marker kkossev.deviceProfileLib, line 1303
    } // library marker kkossev.deviceProfileLib, line 1304
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1305
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1306
        unitText = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1307
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1308
    } // library marker kkossev.deviceProfileLib, line 1309

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1311
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1312
        logTrace "attribute '${name}' exists (type ${foundItem.type})" // library marker kkossev.deviceProfileLib, line 1313
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1314
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1315
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1316

        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1318
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1319
                if (settings.logEnable) { logInfo "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1320
            } // library marker kkossev.deviceProfileLib, line 1321
            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1322
            if (name == 'motion' && is2in1()) { // library marker kkossev.deviceProfileLib, line 1323
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1324
            // continue ... // library marker kkossev.deviceProfileLib, line 1325
            } // library marker kkossev.deviceProfileLib, line 1326
            else { // library marker kkossev.deviceProfileLib, line 1327
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1328
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1329
                } // library marker kkossev.deviceProfileLib, line 1330
                else { // library marker kkossev.deviceProfileLib, line 1331
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1332
                } // library marker kkossev.deviceProfileLib, line 1333
            } // library marker kkossev.deviceProfileLib, line 1334
        } // library marker kkossev.deviceProfileLib, line 1335

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an event! // library marker kkossev.deviceProfileLib, line 1337

        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1339
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1340
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1341
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1342
        if (DEVICE_TYPE in ['Thermostat'])  { processDeviceEventThermostat(name, valueScaled, unitText, descText) } // library marker kkossev.deviceProfileLib, line 1343
        else { // library marker kkossev.deviceProfileLib, line 1344
            switch (name) { // library marker kkossev.deviceProfileLib, line 1345
                case 'motion' : // library marker kkossev.deviceProfileLib, line 1346
                    handleMotion(motionActive = value)  // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1347
                    break // library marker kkossev.deviceProfileLib, line 1348
                case 'temperature' : // library marker kkossev.deviceProfileLib, line 1349
                    //temperatureEvent(value / getTemperatureDiv()) // library marker kkossev.deviceProfileLib, line 1350
                    handleTemperatureEvent(valueScaled as Float) // library marker kkossev.deviceProfileLib, line 1351
                    break // library marker kkossev.deviceProfileLib, line 1352
                case 'humidity' : // library marker kkossev.deviceProfileLib, line 1353
                    handleHumidityEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1354
                    break // library marker kkossev.deviceProfileLib, line 1355
                case 'illuminance' : // library marker kkossev.deviceProfileLib, line 1356
                case 'illuminance_lux' : // library marker kkossev.deviceProfileLib, line 1357
                    handleIlluminanceEvent(valueCorrected.toInteger()) // library marker kkossev.deviceProfileLib, line 1358
                    break // library marker kkossev.deviceProfileLib, line 1359
                case 'pushed' : // library marker kkossev.deviceProfileLib, line 1360
                    logDebug "button event received value=${value} valueScaled=${valueScaled} valueCorrected=${valueCorrected}" // library marker kkossev.deviceProfileLib, line 1361
                    buttonEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1362
                    break // library marker kkossev.deviceProfileLib, line 1363
                default : // library marker kkossev.deviceProfileLib, line 1364
                    sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1365
                    if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1366
                        logDebug "event ${name} sent w/ value ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1367
                        logInfo "${descText}"                                 // send an Info log also (because value changed )  // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1368
                    } // library marker kkossev.deviceProfileLib, line 1369
                    break // library marker kkossev.deviceProfileLib, line 1370
            } // library marker kkossev.deviceProfileLib, line 1371
        //logTrace "attrValue=${attrValue} valueScaled=${valueScaled} equal=${isEqual}" // library marker kkossev.deviceProfileLib, line 1372
        } // library marker kkossev.deviceProfileLib, line 1373
    } // library marker kkossev.deviceProfileLib, line 1374
    // all processing was done here! // library marker kkossev.deviceProfileLib, line 1375
    return true // library marker kkossev.deviceProfileLib, line 1376
} // library marker kkossev.deviceProfileLib, line 1377

boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1379
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1380
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 1381
        logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1382
        return false // library marker kkossev.deviceProfileLib, line 1383
    } // library marker kkossev.deviceProfileLib, line 1384
    int validationFailures = 0 // library marker kkossev.deviceProfileLib, line 1385
    int validationFixes = 0 // library marker kkossev.deviceProfileLib, line 1386
    int total = 0 // library marker kkossev.deviceProfileLib, line 1387
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1388
    def oldSettingValue // library marker kkossev.deviceProfileLib, line 1389
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1390
    def newValue // library marker kkossev.deviceProfileLib, line 1391
    String settingType // library marker kkossev.deviceProfileLib, line 1392
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1393
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1394
        if (foundMap == null || foundMap == [:]) { // library marker kkossev.deviceProfileLib, line 1395
            logDebug "validateAndFixPreferences: map not found for preference ${it.key}"    // 10/21/2023 - sevirity lowered to debug // library marker kkossev.deviceProfileLib, line 1396
            return false // library marker kkossev.deviceProfileLib, line 1397
        } // library marker kkossev.deviceProfileLib, line 1398
        settingType = device.getSettingType(it.key) // library marker kkossev.deviceProfileLib, line 1399
        oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1400
        if (settingType == null) { // library marker kkossev.deviceProfileLib, line 1401
            logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" // library marker kkossev.deviceProfileLib, line 1402
            return false // library marker kkossev.deviceProfileLib, line 1403
        } // library marker kkossev.deviceProfileLib, line 1404
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1405
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1406
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1407
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1408
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1409
            try { // library marker kkossev.deviceProfileLib, line 1410
                device.removeSetting(it.key) // library marker kkossev.deviceProfileLib, line 1411
                logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1412
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1413
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1414
                return false // library marker kkossev.deviceProfileLib, line 1415
            } // library marker kkossev.deviceProfileLib, line 1416
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1417
            try { // library marker kkossev.deviceProfileLib, line 1418
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1419
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1420
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1421
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1422
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1423
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1424
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1425
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1426
                    } // library marker kkossev.deviceProfileLib, line 1427
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1428
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1429
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1430
                    } else { // library marker kkossev.deviceProfileLib, line 1431
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1432
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1433
                    } // library marker kkossev.deviceProfileLib, line 1434
                } // library marker kkossev.deviceProfileLib, line 1435
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1436
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1437
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1438
            } // library marker kkossev.deviceProfileLib, line 1439
            catch (e) { // library marker kkossev.deviceProfileLib, line 1440
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1441
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1442
                try { // library marker kkossev.deviceProfileLib, line 1443
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1444
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1445
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1446
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1447
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1448
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" // library marker kkossev.deviceProfileLib, line 1449
                    return false // library marker kkossev.deviceProfileLib, line 1450
                } // library marker kkossev.deviceProfileLib, line 1451
            } // library marker kkossev.deviceProfileLib, line 1452
        } // library marker kkossev.deviceProfileLib, line 1453
        total ++ // library marker kkossev.deviceProfileLib, line 1454
    } // library marker kkossev.deviceProfileLib, line 1455
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1456
    return true // library marker kkossev.deviceProfileLib, line 1457
} // library marker kkossev.deviceProfileLib, line 1458

void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1460
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1461
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1462
            logInfo fingerprint // library marker kkossev.deviceProfileLib, line 1463
        } // library marker kkossev.deviceProfileLib, line 1464
    } // library marker kkossev.deviceProfileLib, line 1465
} // library marker kkossev.deviceProfileLib, line 1466

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~
