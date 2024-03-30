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
 * ver. 3.0.8  2024-03-30 kkossev  - (dev. branch) commonLib 3.0.4 check; more Groovy lint; tested w/ Sonoff TRVZB
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
 *                                   TODO: change deviceProfilesV2 to deviceProfilesV3 in the lib
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
static String version() { '3.0.8' }
/* groovylint-disable-next-line ImplicitReturnStatement */
static String timeStamp() { '2024/03/30 12:52 PM' }

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
        deviceProfilesV2.each { profileName, profileMap ->
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

@Field static final Map deviceProfilesV2 = [
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
    version: '3.0.4', // library marker kkossev.commonLib, line 10
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
  * ver. 3.0.3  2024-03-17 kkossev  - (dev.branch) more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization // library marker kkossev.commonLib, line 35
  * ver. 3.0.4  2024-03-29 kkossev  - (dev.branch) removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; // library marker kkossev.commonLib, line 36
  * // library marker kkossev.commonLib, line 37
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 38
  *                                   TODO: add custom* handlers for the new drivers! // library marker kkossev.commonLib, line 39
  *                                   TODO: remove the automatic capabilities selectionm for the new drivers! // library marker kkossev.commonLib, line 40
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib ! // library marker kkossev.commonLib, line 41
  *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.commonLib, line 42
  *                                   TODO: add GetInof (endpoints list) command // library marker kkossev.commonLib, line 43
  *                                   TODO: handle Virtual Switch sendZigbeeCommands(cmd=[he cmd 0xbb14c77a-5810-4e65-b16d-22bc665767ed 0xnull 6 1 {}, delay 2000]) // library marker kkossev.commonLib, line 44
  *                                   TODO: move zigbeeGroups : {} to dedicated lib // library marker kkossev.commonLib, line 45
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 46
  *                                   TODO: ping() for a virtual device (runIn 1 milissecond a callback nethod) // library marker kkossev.commonLib, line 47
 * // library marker kkossev.commonLib, line 48
*/ // library marker kkossev.commonLib, line 49

String commonLibVersion() { '3.0.4' } // library marker kkossev.commonLib, line 51
String commonLibStamp() { '2024/03/29 11:56 PM' } // library marker kkossev.commonLib, line 52

import groovy.transform.Field // library marker kkossev.commonLib, line 54
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 55
import hubitat.device.Protocol // library marker kkossev.commonLib, line 56
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 57
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 58
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 59
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 60
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 61
import java.math.BigDecimal // library marker kkossev.commonLib, line 62

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 64

metadata { // library marker kkossev.commonLib, line 66
        if (_DEBUG) { // library marker kkossev.commonLib, line 67
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 68
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 69
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 70
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 71
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 72
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 73
            ] // library marker kkossev.commonLib, line 74
        } // library marker kkossev.commonLib, line 75

        // common capabilities for all device types // library marker kkossev.commonLib, line 77
        capability 'Configuration' // library marker kkossev.commonLib, line 78
        capability 'Refresh' // library marker kkossev.commonLib, line 79
        capability 'Health Check' // library marker kkossev.commonLib, line 80

        // common attributes for all device types // library marker kkossev.commonLib, line 82
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 83
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 84
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 85

        // common commands for all device types // library marker kkossev.commonLib, line 87
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability! // library marker kkossev.commonLib, line 88
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 89

        // deviceType specific capabilities, commands and attributes // library marker kkossev.commonLib, line 91
        if (deviceType in ['Device']) { // library marker kkossev.commonLib, line 92
            if (_DEBUG) { // library marker kkossev.commonLib, line 93
                command 'getAllProperties',       [[name: 'Get All Properties']] // library marker kkossev.commonLib, line 94
            } // library marker kkossev.commonLib, line 95
        } // library marker kkossev.commonLib, line 96
        if (_DEBUG || (deviceType in ['Dimmer', 'Switch', 'Valve'])) { // library marker kkossev.commonLib, line 97
            command 'zigbeeGroups', [ // library marker kkossev.commonLib, line 98
                [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.commonLib, line 99
                [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']] // library marker kkossev.commonLib, line 100
            ] // library marker kkossev.commonLib, line 101
        } // library marker kkossev.commonLib, line 102
        if (deviceType in  ['Device', 'THSensor', 'MotionSensor', 'LightSensor', 'AqaraCube']) { // library marker kkossev.commonLib, line 103
            capability 'Sensor' // library marker kkossev.commonLib, line 104
        } // library marker kkossev.commonLib, line 105
        if (deviceType in  ['Device', 'MotionSensor']) { // library marker kkossev.commonLib, line 106
            capability 'MotionSensor' // library marker kkossev.commonLib, line 107
        } // library marker kkossev.commonLib, line 108
        if (deviceType in  ['Device', 'Switch', 'Relay', 'Outlet', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 109
            capability 'Actuator' // library marker kkossev.commonLib, line 110
        } // library marker kkossev.commonLib, line 111
        if (deviceType in  ['Device', 'THSensor', 'LightSensor', 'MotionSensor', 'Thermostat', 'AqaraCube']) { // library marker kkossev.commonLib, line 112
            capability 'Battery' // library marker kkossev.commonLib, line 113
            attribute 'batteryVoltage', 'number' // library marker kkossev.commonLib, line 114
        } // library marker kkossev.commonLib, line 115
        if (deviceType in  ['Device', 'Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 116
            capability 'Switch' // library marker kkossev.commonLib, line 117
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 118
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 119
            } // library marker kkossev.commonLib, line 120
        } // library marker kkossev.commonLib, line 121
        if (deviceType in ['Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 122
            capability 'SwitchLevel' // library marker kkossev.commonLib, line 123
        } // library marker kkossev.commonLib, line 124
        if (deviceType in  ['AqaraCube']) { // library marker kkossev.commonLib, line 125
            capability 'PushableButton' // library marker kkossev.commonLib, line 126
            capability 'DoubleTapableButton' // library marker kkossev.commonLib, line 127
            capability 'HoldableButton' // library marker kkossev.commonLib, line 128
            capability 'ReleasableButton' // library marker kkossev.commonLib, line 129
        } // library marker kkossev.commonLib, line 130
        if (deviceType in  ['Device']) { // library marker kkossev.commonLib, line 131
            capability 'Momentary' // library marker kkossev.commonLib, line 132
        } // library marker kkossev.commonLib, line 133
        if (deviceType in  ['Device', 'THSensor']) { // library marker kkossev.commonLib, line 134
            capability 'TemperatureMeasurement' // library marker kkossev.commonLib, line 135
        } // library marker kkossev.commonLib, line 136
        if (deviceType in  ['Device', 'THSensor']) { // library marker kkossev.commonLib, line 137
            capability 'RelativeHumidityMeasurement' // library marker kkossev.commonLib, line 138
        } // library marker kkossev.commonLib, line 139
        if (deviceType in  ['Device', 'LightSensor']) { // library marker kkossev.commonLib, line 140
            capability 'IlluminanceMeasurement' // library marker kkossev.commonLib, line 141
        } // library marker kkossev.commonLib, line 142

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 144
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 145

    preferences { // library marker kkossev.commonLib, line 147
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 148
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 149
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 150

        if (device) { // library marker kkossev.commonLib, line 152
            if ((device.hasCapability('TemperatureMeasurement') || device.hasCapability('RelativeHumidityMeasurement') || device.hasCapability('IlluminanceMeasurement')) && !isZigUSB()) { // library marker kkossev.commonLib, line 153
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 154
                input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.commonLib, line 155
            } // library marker kkossev.commonLib, line 156
            if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 157
                input name: 'illuminanceThreshold', type: 'number', title: '<b>Illuminance Reporting Threshold</b>', description: '<i>Illuminance reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>', range: '1..255', defaultValue: DEFAULT_ILLUMINANCE_THRESHOLD // library marker kkossev.commonLib, line 158
                input name: 'illuminanceCoeff', type: 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: '<i>Illuminance correction coefficient, range (0.10..10.00)</i>', range: '0.10..10.00', defaultValue: 1.00 // library marker kkossev.commonLib, line 159
            } // library marker kkossev.commonLib, line 160

            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 162
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 163
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 164
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 165
                if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 166
                    input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.commonLib, line 167
                } // library marker kkossev.commonLib, line 168
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 169
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 170
                } // library marker kkossev.commonLib, line 171
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 172
            } // library marker kkossev.commonLib, line 173
        } // library marker kkossev.commonLib, line 174
    } // library marker kkossev.commonLib, line 175
} // library marker kkossev.commonLib, line 176

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 178
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 179
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 180
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 181
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 182
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 183
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 184
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 185
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 186
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 187
@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 5 // library marker kkossev.commonLib, line 188
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 189

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 191
    defaultValue: 1, // library marker kkossev.commonLib, line 192
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 193
] // library marker kkossev.commonLib, line 194
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 195
    defaultValue: 240, // library marker kkossev.commonLib, line 196
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 197
] // library marker kkossev.commonLib, line 198
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 199
    defaultValue: 0, // library marker kkossev.commonLib, line 200
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 201
] // library marker kkossev.commonLib, line 202

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.commonLib, line 204
    defaultValue: 0, // library marker kkossev.commonLib, line 205
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 206
] // library marker kkossev.commonLib, line 207
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 208
    defaultValue: 0, // library marker kkossev.commonLib, line 209
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.commonLib, line 210
] // library marker kkossev.commonLib, line 211

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 213
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 214
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 215
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 216
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 217
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 218
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 219
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 220
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 221
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 222
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 223
] // library marker kkossev.commonLib, line 224

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 226
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 227
boolean isChattyDeviceReport(final String description)  { return false /*(description?.contains("cluster: FC7E")) */ } // library marker kkossev.commonLib, line 228
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 229
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 230
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 231
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 232
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 233
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 234
boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 235

/** // library marker kkossev.commonLib, line 237
 * Parse Zigbee message // library marker kkossev.commonLib, line 238
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 239
 */ // library marker kkossev.commonLib, line 240
void parse(final String description) { // library marker kkossev.commonLib, line 241
    checkDriverVersion() // library marker kkossev.commonLib, line 242
    if (!isChattyDeviceReport(description)) { logDebug "parse: ${description}" } // library marker kkossev.commonLib, line 243
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 244
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 245
    setHealthStatusOnline() // library marker kkossev.commonLib, line 246

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 248
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 249
        /* groovylint-disable-next-line ConstantIfExpression */ // library marker kkossev.commonLib, line 250
        if (true /*isHL0SS9OAradar() && _IGNORE_ZCL_REPORTS == true*/) {    // TODO! // library marker kkossev.commonLib, line 251
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 252
            return // library marker kkossev.commonLib, line 253
        } // library marker kkossev.commonLib, line 254
        parseIasMessage(description)    // TODO! // library marker kkossev.commonLib, line 255
    } // library marker kkossev.commonLib, line 256
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 257
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 258
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 259
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 260
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 261
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 262
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 263
    } // library marker kkossev.commonLib, line 264
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 265
        return // library marker kkossev.commonLib, line 266
    } // library marker kkossev.commonLib, line 267
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 268

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 270
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 271
        return // library marker kkossev.commonLib, line 272
    } // library marker kkossev.commonLib, line 273
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 274
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 275
        return // library marker kkossev.commonLib, line 276
    } // library marker kkossev.commonLib, line 277
    if (!isChattyDeviceReport(description)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 278
    // // library marker kkossev.commonLib, line 279
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 280
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 281
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 282

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 284
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 285
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 286
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 287
            break // library marker kkossev.commonLib, line 288
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 289
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 290
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 291
            break // library marker kkossev.commonLib, line 292
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 293
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 294
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 295
            break // library marker kkossev.commonLib, line 296
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 297
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 298
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 299
            break // library marker kkossev.commonLib, line 300
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 301
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 302
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 303
            break // library marker kkossev.commonLib, line 304
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 305
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 306
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 307
            break // library marker kkossev.commonLib, line 308
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 309
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 310
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 311
            break // library marker kkossev.commonLib, line 312
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 313
            if (isZigUSB()) { // library marker kkossev.commonLib, line 314
                parseZigUSBAnlogInputCluster(description) // library marker kkossev.commonLib, line 315
            } // library marker kkossev.commonLib, line 316
            else { // library marker kkossev.commonLib, line 317
                parseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 318
                descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map) } // library marker kkossev.commonLib, line 319
            } // library marker kkossev.commonLib, line 320
            break // library marker kkossev.commonLib, line 321
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 322
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 323
            break // library marker kkossev.commonLib, line 324
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 325
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 326
            break // library marker kkossev.commonLib, line 327
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 328
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 329
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 330
            break // library marker kkossev.commonLib, line 331
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 332
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 333
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 334
            break // library marker kkossev.commonLib, line 335
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 336
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 337
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 338
            break // library marker kkossev.commonLib, line 339
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 340
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 341
            break // library marker kkossev.commonLib, line 342
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 343
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 344
            break // library marker kkossev.commonLib, line 345
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 346
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 347
            break // library marker kkossev.commonLib, line 348
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 349
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 350
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 351
            break // library marker kkossev.commonLib, line 352
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 353
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 354
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 355
            break // library marker kkossev.commonLib, line 356
        case 0xE002 : // library marker kkossev.commonLib, line 357
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 358
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 359
            break // library marker kkossev.commonLib, line 360
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 361
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 362
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 363
            break // library marker kkossev.commonLib, line 364
        case 0xFC11 :                                    // Sonoff // library marker kkossev.commonLib, line 365
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 366
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 367
            break // library marker kkossev.commonLib, line 368
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 369
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 370
            break // library marker kkossev.commonLib, line 371
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 372
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 373
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 374
            break // library marker kkossev.commonLib, line 375
        default: // library marker kkossev.commonLib, line 376
            if (settings.logEnable) { // library marker kkossev.commonLib, line 377
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 378
            } // library marker kkossev.commonLib, line 379
            break // library marker kkossev.commonLib, line 380
    } // library marker kkossev.commonLib, line 381
} // library marker kkossev.commonLib, line 382

/** // library marker kkossev.commonLib, line 384
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 385
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 386
 */ // library marker kkossev.commonLib, line 387
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 388
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 389
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 390
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 391
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 392
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 393
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 394
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 395
    } // library marker kkossev.commonLib, line 396
    else { // library marker kkossev.commonLib, line 397
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 398
    } // library marker kkossev.commonLib, line 399
} // library marker kkossev.commonLib, line 400

/** // library marker kkossev.commonLib, line 402
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 403
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 404
 */ // library marker kkossev.commonLib, line 405
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 406
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 407
    switch (commandId) { // library marker kkossev.commonLib, line 408
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 409
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 410
            break // library marker kkossev.commonLib, line 411
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 412
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 413
            break // library marker kkossev.commonLib, line 414
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 415
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 416
            break // library marker kkossev.commonLib, line 417
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 418
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 419
            break // library marker kkossev.commonLib, line 420
        case 0x0B: // default command response // library marker kkossev.commonLib, line 421
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 422
            break // library marker kkossev.commonLib, line 423
        default: // library marker kkossev.commonLib, line 424
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 425
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 426
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 427
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 428
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 429
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 430
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 431
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 432
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 433
            } // library marker kkossev.commonLib, line 434
            break // library marker kkossev.commonLib, line 435
    } // library marker kkossev.commonLib, line 436
} // library marker kkossev.commonLib, line 437

/** // library marker kkossev.commonLib, line 439
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 440
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 441
 */ // library marker kkossev.commonLib, line 442
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 443
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 444
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 445
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 446
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 447
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 448
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 449
    } // library marker kkossev.commonLib, line 450
    else { // library marker kkossev.commonLib, line 451
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 452
    } // library marker kkossev.commonLib, line 453
} // library marker kkossev.commonLib, line 454

/** // library marker kkossev.commonLib, line 456
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 457
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 458
 */ // library marker kkossev.commonLib, line 459
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 460
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 461
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 462
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 463
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 464
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 465
    } // library marker kkossev.commonLib, line 466
    else { // library marker kkossev.commonLib, line 467
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 468
    } // library marker kkossev.commonLib, line 469
} // library marker kkossev.commonLib, line 470

/** // library marker kkossev.commonLib, line 472
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 473
 */ // library marker kkossev.commonLib, line 474
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 475
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 476
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 477
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 478
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 479
        state.reportingEnabled = true // library marker kkossev.commonLib, line 480
    } // library marker kkossev.commonLib, line 481
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 482
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 483
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 484
    } else { // library marker kkossev.commonLib, line 485
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 486
    } // library marker kkossev.commonLib, line 487
} // library marker kkossev.commonLib, line 488

/** // library marker kkossev.commonLib, line 490
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 491
 */ // library marker kkossev.commonLib, line 492
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 493
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 494
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 495
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 496
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 497
    if (status == 0) { // library marker kkossev.commonLib, line 498
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 499
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 500
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 501
        int delta = 0 // library marker kkossev.commonLib, line 502
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 503
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 504
        } // library marker kkossev.commonLib, line 505
        else { // library marker kkossev.commonLib, line 506
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 507
        } // library marker kkossev.commonLib, line 508
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 509
    } // library marker kkossev.commonLib, line 510
    else { // library marker kkossev.commonLib, line 511
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 512
    } // library marker kkossev.commonLib, line 513
} // library marker kkossev.commonLib, line 514

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 516
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 517
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 518
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 519
        return false // library marker kkossev.commonLib, line 520
    } // library marker kkossev.commonLib, line 521
    // execute the customHandler function // library marker kkossev.commonLib, line 522
    boolean result = false // library marker kkossev.commonLib, line 523
    try { // library marker kkossev.commonLib, line 524
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 525
    } // library marker kkossev.commonLib, line 526
    catch (e) { // library marker kkossev.commonLib, line 527
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 528
        return false // library marker kkossev.commonLib, line 529
    } // library marker kkossev.commonLib, line 530
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 531
    return result // library marker kkossev.commonLib, line 532
} // library marker kkossev.commonLib, line 533

/** // library marker kkossev.commonLib, line 535
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 536
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 537
 */ // library marker kkossev.commonLib, line 538
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 539
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 540
    final String commandId = data[0] // library marker kkossev.commonLib, line 541
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 542
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 543
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 544
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 545
    } else { // library marker kkossev.commonLib, line 546
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 547
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 548
        if (isZigUSB()) { // library marker kkossev.commonLib, line 549
            executeCustomHandler('customParseDefaultCommandResponse', descMap) // library marker kkossev.commonLib, line 550
        } // library marker kkossev.commonLib, line 551
    } // library marker kkossev.commonLib, line 552
} // library marker kkossev.commonLib, line 553

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 555
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.commonLib, line 556
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.commonLib, line 557
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.commonLib, line 558
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.commonLib, line 559
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.commonLib, line 560
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.commonLib, line 561
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.commonLib, line 562
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.commonLib, line 563
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 564
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 565
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 566
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.commonLib, line 567
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.commonLib, line 568
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.commonLib, line 569
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.commonLib, line 570

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 572
    0x00: 'Success', // library marker kkossev.commonLib, line 573
    0x01: 'Failure', // library marker kkossev.commonLib, line 574
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 575
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 576
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 577
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 578
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 579
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 580
    0x88: 'Read Only', // library marker kkossev.commonLib, line 581
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 582
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 583
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 584
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 585
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 586
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 587
    0x94: 'Time out', // library marker kkossev.commonLib, line 588
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 589
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 590
] // library marker kkossev.commonLib, line 591

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 593
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 594
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 595
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 596
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 597
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 598
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 599
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 600
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 601
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 602
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 603
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 604
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 605
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 606
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 607
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 608
] // library marker kkossev.commonLib, line 609

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 611
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 612
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 613
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 614
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 615
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 616
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 617
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 618
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 619
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 620
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 621
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 622
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 623
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 624
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 625
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 626
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 627
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 628
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 629
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 630
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 631
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 632
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 633
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 634
] // library marker kkossev.commonLib, line 635

/* // library marker kkossev.commonLib, line 637
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 638
 * Xiaomi cluster 0xFCC0 parser. // library marker kkossev.commonLib, line 639
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 640
 */ // library marker kkossev.commonLib, line 641
void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 642
    if (xiaomiLibVersion() != null) { // library marker kkossev.commonLib, line 643
        parseXiaomiClusterLib(descMap) // library marker kkossev.commonLib, line 644
    } // library marker kkossev.commonLib, line 645
    else { // library marker kkossev.commonLib, line 646
        logWarn 'Xiaomi cluster 0xFCC0' // library marker kkossev.commonLib, line 647
    } // library marker kkossev.commonLib, line 648
} // library marker kkossev.commonLib, line 649

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 651
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 652
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 653
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 654
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 655
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 656
    return avg // library marker kkossev.commonLib, line 657
} // library marker kkossev.commonLib, line 658

/* // library marker kkossev.commonLib, line 660
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 661
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 662
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 663
*/ // library marker kkossev.commonLib, line 664
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 665

/** // library marker kkossev.commonLib, line 667
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 668
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 669
 */ // library marker kkossev.commonLib, line 670
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 671
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 672
    /* // library marker kkossev.commonLib, line 673
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 674
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 675
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 676
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 677
    */ // library marker kkossev.commonLib, line 678
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 679
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 680
        case 0x0000: // library marker kkossev.commonLib, line 681
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 682
            break // library marker kkossev.commonLib, line 683
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 684
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 685
            if (isPing) { // library marker kkossev.commonLib, line 686
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 687
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 688
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 689
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 690
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 691
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 692
                    sendRttEvent() // library marker kkossev.commonLib, line 693
                } // library marker kkossev.commonLib, line 694
                else { // library marker kkossev.commonLib, line 695
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 696
                } // library marker kkossev.commonLib, line 697
                state.states['isPing'] = false // library marker kkossev.commonLib, line 698
            } // library marker kkossev.commonLib, line 699
            else { // library marker kkossev.commonLib, line 700
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 701
            } // library marker kkossev.commonLib, line 702
            break // library marker kkossev.commonLib, line 703
        case 0x0004: // library marker kkossev.commonLib, line 704
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 705
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 706
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 707
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 708
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 709
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 710
            } // library marker kkossev.commonLib, line 711
            break // library marker kkossev.commonLib, line 712
        case 0x0005: // library marker kkossev.commonLib, line 713
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 714
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 715
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 716
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 717
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 718
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 719
            } // library marker kkossev.commonLib, line 720
            break // library marker kkossev.commonLib, line 721
        case 0x0007: // library marker kkossev.commonLib, line 722
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 723
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 724
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 725
            break // library marker kkossev.commonLib, line 726
        case 0xFFDF: // library marker kkossev.commonLib, line 727
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 728
            break // library marker kkossev.commonLib, line 729
        case 0xFFE2: // library marker kkossev.commonLib, line 730
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 731
            break // library marker kkossev.commonLib, line 732
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 733
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 734
            break // library marker kkossev.commonLib, line 735
        case 0xFFFE: // library marker kkossev.commonLib, line 736
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 737
            break // library marker kkossev.commonLib, line 738
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 739
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 740
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 741
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 742
            break // library marker kkossev.commonLib, line 743
        default: // library marker kkossev.commonLib, line 744
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 745
            break // library marker kkossev.commonLib, line 746
    } // library marker kkossev.commonLib, line 747
} // library marker kkossev.commonLib, line 748

/* // library marker kkossev.commonLib, line 750
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 751
 * power cluster            0x0001 // library marker kkossev.commonLib, line 752
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 753
*/ // library marker kkossev.commonLib, line 754
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 755
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 756
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 757
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 758
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 759
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 760
    } // library marker kkossev.commonLib, line 761

    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 763
    if (descMap.attrId == '0020') { // library marker kkossev.commonLib, line 764
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 765
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 766
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.commonLib, line 767
        } // library marker kkossev.commonLib, line 768
    } // library marker kkossev.commonLib, line 769
    else if (descMap.attrId == '0021') { // library marker kkossev.commonLib, line 770
        sendBatteryPercentageEvent(rawValue * 2) // library marker kkossev.commonLib, line 771
    } // library marker kkossev.commonLib, line 772
    else { // library marker kkossev.commonLib, line 773
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 774
    } // library marker kkossev.commonLib, line 775
} // library marker kkossev.commonLib, line 776

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.commonLib, line 778
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 779
    Map result = [:] // library marker kkossev.commonLib, line 780
    BigDecimal volts = BigDecimal(rawValue) / 10G // library marker kkossev.commonLib, line 781
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.commonLib, line 782
        BigDecimal minVolts = 2.2 // library marker kkossev.commonLib, line 783
        BigDecimal maxVolts = 3.2 // library marker kkossev.commonLib, line 784
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 785
        int roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 786
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.commonLib, line 787
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.commonLib, line 788
        if (convertToPercent == true) { // library marker kkossev.commonLib, line 789
            result.value = Math.min(100, roundedPct) // library marker kkossev.commonLib, line 790
            result.name = 'battery' // library marker kkossev.commonLib, line 791
            result.unit  = '%' // library marker kkossev.commonLib, line 792
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.commonLib, line 793
        } // library marker kkossev.commonLib, line 794
        else { // library marker kkossev.commonLib, line 795
            result.value = volts // library marker kkossev.commonLib, line 796
            result.name = 'batteryVoltage' // library marker kkossev.commonLib, line 797
            result.unit  = 'V' // library marker kkossev.commonLib, line 798
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.commonLib, line 799
        } // library marker kkossev.commonLib, line 800
        result.type = 'physical' // library marker kkossev.commonLib, line 801
        result.isStateChange = true // library marker kkossev.commonLib, line 802
        logInfo "${result.descriptionText}" // library marker kkossev.commonLib, line 803
        sendEvent(result) // library marker kkossev.commonLib, line 804
    } // library marker kkossev.commonLib, line 805
    else { // library marker kkossev.commonLib, line 806
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.commonLib, line 807
    } // library marker kkossev.commonLib, line 808
} // library marker kkossev.commonLib, line 809

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.commonLib, line 811
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 812
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 813
        return // library marker kkossev.commonLib, line 814
    } // library marker kkossev.commonLib, line 815
    Map map = [:] // library marker kkossev.commonLib, line 816
    map.name = 'battery' // library marker kkossev.commonLib, line 817
    map.timeStamp = now() // library marker kkossev.commonLib, line 818
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 819
    map.unit  = '%' // library marker kkossev.commonLib, line 820
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 821
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 822
    map.isStateChange = true // library marker kkossev.commonLib, line 823
    // // library marker kkossev.commonLib, line 824
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.commonLib, line 825
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 826
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 827
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 828
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 829
        // send it now! // library marker kkossev.commonLib, line 830
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 831
    } // library marker kkossev.commonLib, line 832
    else { // library marker kkossev.commonLib, line 833
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 834
        map.delayed = delayedTime // library marker kkossev.commonLib, line 835
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 836
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 837
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 838
    } // library marker kkossev.commonLib, line 839
} // library marker kkossev.commonLib, line 840

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 842
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 843
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 844
    sendEvent(map) // library marker kkossev.commonLib, line 845
} // library marker kkossev.commonLib, line 846

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 848
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 849
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 850
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 851
    sendEvent(map) // library marker kkossev.commonLib, line 852
} // library marker kkossev.commonLib, line 853

/* // library marker kkossev.commonLib, line 855
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 856
 * Zigbee Identity Cluster 0x0003 // library marker kkossev.commonLib, line 857
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 858
*/ // library marker kkossev.commonLib, line 859
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 860
void parseIdentityCluster(final Map descMap) { // library marker kkossev.commonLib, line 861
    logDebug 'unprocessed parseIdentityCluster' // library marker kkossev.commonLib, line 862
} // library marker kkossev.commonLib, line 863

/* // library marker kkossev.commonLib, line 865
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 866
 * Zigbee Scenes Cluster 0x005 // library marker kkossev.commonLib, line 867
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 868
*/ // library marker kkossev.commonLib, line 869
void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 870
    if (this.respondsTo('customParseScenesCluster')) { // library marker kkossev.commonLib, line 871
        customParseScenesCluster(descMap) // library marker kkossev.commonLib, line 872
    } // library marker kkossev.commonLib, line 873
    else { // library marker kkossev.commonLib, line 874
        logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 875
    } // library marker kkossev.commonLib, line 876
} // library marker kkossev.commonLib, line 877

/* // library marker kkossev.commonLib, line 879
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 880
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.commonLib, line 881
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 882
*/ // library marker kkossev.commonLib, line 883
void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 884
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.commonLib, line 885
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.commonLib, line 886
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 887
    switch (descMap.command as Integer) { // library marker kkossev.commonLib, line 888
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.commonLib, line 889
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 890
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 891
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 892
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 893
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 894
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 895
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.commonLib, line 896
            } // library marker kkossev.commonLib, line 897
            else { // library marker kkossev.commonLib, line 898
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.commonLib, line 899
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.commonLib, line 900
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.commonLib, line 901
                for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 902
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.commonLib, line 903
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.commonLib, line 904
                        return // library marker kkossev.commonLib, line 905
                    } // library marker kkossev.commonLib, line 906
                } // library marker kkossev.commonLib, line 907
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.commonLib, line 908
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt, 4)})" // library marker kkossev.commonLib, line 909
                state.zigbeeGroups['groups'].sort() // library marker kkossev.commonLib, line 910
            } // library marker kkossev.commonLib, line 911
            break // library marker kkossev.commonLib, line 912
        case 0x01: // View group // library marker kkossev.commonLib, line 913
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.commonLib, line 914
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 915
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 916
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 917
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 918
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 919
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 920
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 921
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 922
            } // library marker kkossev.commonLib, line 923
            else { // library marker kkossev.commonLib, line 924
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 925
            } // library marker kkossev.commonLib, line 926
            break // library marker kkossev.commonLib, line 927
        case 0x02: // Get group membership // library marker kkossev.commonLib, line 928
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 929
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 930
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 931
            final Set<String> groups = [] // library marker kkossev.commonLib, line 932
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 933
                int pos = (i * 2) + 2 // library marker kkossev.commonLib, line 934
                String group = data[pos + 1] + data[pos] // library marker kkossev.commonLib, line 935
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.commonLib, line 936
            } // library marker kkossev.commonLib, line 937
            state.zigbeeGroups['groups'] = groups // library marker kkossev.commonLib, line 938
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.commonLib, line 939
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.commonLib, line 940
            break // library marker kkossev.commonLib, line 941
        case 0x03: // Remove group // library marker kkossev.commonLib, line 942
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 943
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 944
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 945
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 946
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 947
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 948
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 949
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 950
            } // library marker kkossev.commonLib, line 951
            else { // library marker kkossev.commonLib, line 952
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 953
            } // library marker kkossev.commonLib, line 954
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.commonLib, line 955
            int index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.commonLib, line 956
            if (index >= 0) { // library marker kkossev.commonLib, line 957
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.commonLib, line 958
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.commonLib, line 959
            } // library marker kkossev.commonLib, line 960
            break // library marker kkossev.commonLib, line 961
        case 0x04: //Remove all groups // library marker kkossev.commonLib, line 962
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 963
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 964
            break // library marker kkossev.commonLib, line 965
        case 0x05: // Add group if identifying // library marker kkossev.commonLib, line 966
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). // library marker kkossev.commonLib, line 967
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 968
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 969
            break // library marker kkossev.commonLib, line 970
        default: // library marker kkossev.commonLib, line 971
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 972
            break // library marker kkossev.commonLib, line 973
    } // library marker kkossev.commonLib, line 974
} // library marker kkossev.commonLib, line 975

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 977
List<String> addGroupMembership(groupNr) { // library marker kkossev.commonLib, line 978
    List<String> cmds = [] // library marker kkossev.commonLib, line 979
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 980
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 981
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 982
        return [] // library marker kkossev.commonLib, line 983
    } // library marker kkossev.commonLib, line 984
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 985
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 986
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 987
    return cmds // library marker kkossev.commonLib, line 988
} // library marker kkossev.commonLib, line 989

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 991
List<String> viewGroupMembership(groupNr) { // library marker kkossev.commonLib, line 992
    List<String> cmds = [] // library marker kkossev.commonLib, line 993
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 994
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 995
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 996
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 997
    return cmds // library marker kkossev.commonLib, line 998
} // library marker kkossev.commonLib, line 999

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1001
List<String> getGroupMembership(dummy) { // library marker kkossev.commonLib, line 1002
    List<String> cmds = [] // library marker kkossev.commonLib, line 1003
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00') // library marker kkossev.commonLib, line 1004
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1005
    return cmds // library marker kkossev.commonLib, line 1006
} // library marker kkossev.commonLib, line 1007

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1009
List<String> removeGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1010
    List<String> cmds = [] // library marker kkossev.commonLib, line 1011
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1012
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 1013
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 1014
        return [] // library marker kkossev.commonLib, line 1015
    } // library marker kkossev.commonLib, line 1016
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1017
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1018
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1019
    return cmds // library marker kkossev.commonLib, line 1020
} // library marker kkossev.commonLib, line 1021

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1023
List<String> removeAllGroups(groupNr) { // library marker kkossev.commonLib, line 1024
    List<String> cmds = [] // library marker kkossev.commonLib, line 1025
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1026
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1027
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1028
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1029
    return cmds // library marker kkossev.commonLib, line 1030
} // library marker kkossev.commonLib, line 1031

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1033
List<String> notImplementedGroups(groupNr) { // library marker kkossev.commonLib, line 1034
    List<String> cmds = [] // library marker kkossev.commonLib, line 1035
    //final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1036
    //final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1037
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1038
    return cmds // library marker kkossev.commonLib, line 1039
} // library marker kkossev.commonLib, line 1040

@Field static final Map GroupCommandsMap = [ // library marker kkossev.commonLib, line 1042
    '--- select ---'           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'groupCommandsHelp'], // library marker kkossev.commonLib, line 1043
    'Add group'                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.commonLib, line 1044
    'View group'               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.commonLib, line 1045
    'Get group membership'     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.commonLib, line 1046
    'Remove group'             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.commonLib, line 1047
    'Remove all groups'        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.commonLib, line 1048
    'Add group if identifying' : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.commonLib, line 1049
] // library marker kkossev.commonLib, line 1050

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1052
void zigbeeGroups(final String command=null, par=null) { // library marker kkossev.commonLib, line 1053
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.commonLib, line 1054
    List<String> cmds = [] // library marker kkossev.commonLib, line 1055
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1056
    if (state.zigbeeGroups['groups'] == null) { state.zigbeeGroups['groups'] = [] } // library marker kkossev.commonLib, line 1057
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1058
    def value // library marker kkossev.commonLib, line 1059
    Boolean validated = false // library marker kkossev.commonLib, line 1060
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.commonLib, line 1061
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.commonLib, line 1062
        return // library marker kkossev.commonLib, line 1063
    } // library marker kkossev.commonLib, line 1064
    value = GroupCommandsMap[command]?.type == 'number' ? safeToInt(par, -1) : 0 // library marker kkossev.commonLib, line 1065
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) { validated = true } // library marker kkossev.commonLib, line 1066
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.commonLib, line 1067
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.commonLib, line 1068
        return // library marker kkossev.commonLib, line 1069
    } // library marker kkossev.commonLib, line 1070
    // // library marker kkossev.commonLib, line 1071
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1072
    def func // library marker kkossev.commonLib, line 1073
    try { // library marker kkossev.commonLib, line 1074
        func = GroupCommandsMap[command]?.function // library marker kkossev.commonLib, line 1075
        //def type = GroupCommandsMap[command]?.type // library marker kkossev.commonLib, line 1076
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.commonLib, line 1077
        cmds = "$func"(value) // library marker kkossev.commonLib, line 1078
    } // library marker kkossev.commonLib, line 1079
    catch (e) { // library marker kkossev.commonLib, line 1080
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1081
        return // library marker kkossev.commonLib, line 1082
    } // library marker kkossev.commonLib, line 1083

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1085
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1086
} // library marker kkossev.commonLib, line 1087

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1089
void groupCommandsHelp(val) { // library marker kkossev.commonLib, line 1090
    logWarn 'GroupCommands: select one of the commands in this list!' // library marker kkossev.commonLib, line 1091
} // library marker kkossev.commonLib, line 1092

/* // library marker kkossev.commonLib, line 1094
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1095
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 1096
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1097
*/ // library marker kkossev.commonLib, line 1098

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 1100
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 1101
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 1102
    } // library marker kkossev.commonLib, line 1103
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1104
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1105
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1106
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 1107
    } // library marker kkossev.commonLib, line 1108
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 1109
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 1110
    } // library marker kkossev.commonLib, line 1111
    else { // library marker kkossev.commonLib, line 1112
        logWarn "unprocessed OnOffCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1113
    } // library marker kkossev.commonLib, line 1114
} // library marker kkossev.commonLib, line 1115

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 1117
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 1118
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1119

void toggle() { // library marker kkossev.commonLib, line 1121
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 1122
    String state = '' // library marker kkossev.commonLib, line 1123
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 1124
        state = 'on' // library marker kkossev.commonLib, line 1125
    } // library marker kkossev.commonLib, line 1126
    else { // library marker kkossev.commonLib, line 1127
        state = 'off' // library marker kkossev.commonLib, line 1128
    } // library marker kkossev.commonLib, line 1129
    descriptionText += state // library marker kkossev.commonLib, line 1130
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 1131
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1132
} // library marker kkossev.commonLib, line 1133

void off() { // library marker kkossev.commonLib, line 1135
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 1136
        customOff() // library marker kkossev.commonLib, line 1137
        return // library marker kkossev.commonLib, line 1138
    } // library marker kkossev.commonLib, line 1139
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 1140
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 1141
        return // library marker kkossev.commonLib, line 1142
    } // library marker kkossev.commonLib, line 1143
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 1144
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1145
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 1146
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1147
        if (currentState == 'off') { // library marker kkossev.commonLib, line 1148
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1149
        } // library marker kkossev.commonLib, line 1150
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 1151
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1152
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1153
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1154
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1155
    } // library marker kkossev.commonLib, line 1156
    /* // library marker kkossev.commonLib, line 1157
    else { // library marker kkossev.commonLib, line 1158
        if (currentState != 'off') { // library marker kkossev.commonLib, line 1159
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 1160
        } // library marker kkossev.commonLib, line 1161
        else { // library marker kkossev.commonLib, line 1162
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 1163
            return // library marker kkossev.commonLib, line 1164
        } // library marker kkossev.commonLib, line 1165
    } // library marker kkossev.commonLib, line 1166
    */ // library marker kkossev.commonLib, line 1167

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1169
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1170
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1171
} // library marker kkossev.commonLib, line 1172

void on() { // library marker kkossev.commonLib, line 1174
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 1175
        customOn() // library marker kkossev.commonLib, line 1176
        return // library marker kkossev.commonLib, line 1177
    } // library marker kkossev.commonLib, line 1178
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 1179
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1180
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 1181
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1182
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 1183
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1184
        } // library marker kkossev.commonLib, line 1185
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 1186
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1187
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1188
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1189
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1190
    } // library marker kkossev.commonLib, line 1191
    /* // library marker kkossev.commonLib, line 1192
    else { // library marker kkossev.commonLib, line 1193
        if (currentState != 'on') { // library marker kkossev.commonLib, line 1194
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 1195
        } // library marker kkossev.commonLib, line 1196
        else { // library marker kkossev.commonLib, line 1197
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 1198
            return // library marker kkossev.commonLib, line 1199
        } // library marker kkossev.commonLib, line 1200
    } // library marker kkossev.commonLib, line 1201
    */ // library marker kkossev.commonLib, line 1202
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1203
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1204
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1205
} // library marker kkossev.commonLib, line 1206

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 1208
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 1209
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 1210
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 1211
    } // library marker kkossev.commonLib, line 1212
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 1213
    Map map = [:] // library marker kkossev.commonLib, line 1214
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 1215
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 1216
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 1217
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 1218
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1219
        return // library marker kkossev.commonLib, line 1220
    } // library marker kkossev.commonLib, line 1221
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 1222
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 1223
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1224
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 1225
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 1226
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1227
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 1228
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1229
    } else { // library marker kkossev.commonLib, line 1230
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1231
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1232
    } // library marker kkossev.commonLib, line 1233
    map.name = 'switch' // library marker kkossev.commonLib, line 1234
    map.value = value // library marker kkossev.commonLib, line 1235
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1236
    if (isRefresh) { // library marker kkossev.commonLib, line 1237
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1238
        map.isStateChange = true // library marker kkossev.commonLib, line 1239
    } else { // library marker kkossev.commonLib, line 1240
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 1241
    } // library marker kkossev.commonLib, line 1242
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1243
    sendEvent(map) // library marker kkossev.commonLib, line 1244
    clearIsDigital() // library marker kkossev.commonLib, line 1245
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 1246
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 1247
    }     // library marker kkossev.commonLib, line 1248
} // library marker kkossev.commonLib, line 1249

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 1251
    '0': 'switch off', // library marker kkossev.commonLib, line 1252
    '1': 'switch on', // library marker kkossev.commonLib, line 1253
    '2': 'switch last state' // library marker kkossev.commonLib, line 1254
] // library marker kkossev.commonLib, line 1255

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 1257
    '0': 'toggle', // library marker kkossev.commonLib, line 1258
    '1': 'state', // library marker kkossev.commonLib, line 1259
    '2': 'momentary' // library marker kkossev.commonLib, line 1260
] // library marker kkossev.commonLib, line 1261

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 1263
    Map descMap = [:] // library marker kkossev.commonLib, line 1264
    try { // library marker kkossev.commonLib, line 1265
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1266
    } // library marker kkossev.commonLib, line 1267
    catch (e1) { // library marker kkossev.commonLib, line 1268
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1269
        // try alternative custom parsing // library marker kkossev.commonLib, line 1270
        descMap = [:] // library marker kkossev.commonLib, line 1271
        try { // library marker kkossev.commonLib, line 1272
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1273
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1274
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1275
            } // library marker kkossev.commonLib, line 1276
        } // library marker kkossev.commonLib, line 1277
        catch (e2) { // library marker kkossev.commonLib, line 1278
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1279
            return [:] // library marker kkossev.commonLib, line 1280
        } // library marker kkossev.commonLib, line 1281
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1282
    } // library marker kkossev.commonLib, line 1283
    return descMap // library marker kkossev.commonLib, line 1284
} // library marker kkossev.commonLib, line 1285

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 1287
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1288
        return false // library marker kkossev.commonLib, line 1289
    } // library marker kkossev.commonLib, line 1290
    // try to parse ... // library marker kkossev.commonLib, line 1291
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1292
    Map descMap = [:] // library marker kkossev.commonLib, line 1293
    try { // library marker kkossev.commonLib, line 1294
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1295
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1296
    } // library marker kkossev.commonLib, line 1297
    catch (e) { // library marker kkossev.commonLib, line 1298
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1299
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1300
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1301
        return true // library marker kkossev.commonLib, line 1302
    } // library marker kkossev.commonLib, line 1303

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 1305
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1306
    } // library marker kkossev.commonLib, line 1307
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 1308
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1309
    } // library marker kkossev.commonLib, line 1310
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 1311
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1312
    } // library marker kkossev.commonLib, line 1313
    else { // library marker kkossev.commonLib, line 1314
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1315
        return false // library marker kkossev.commonLib, line 1316
    } // library marker kkossev.commonLib, line 1317
    return true    // processed // library marker kkossev.commonLib, line 1318
} // library marker kkossev.commonLib, line 1319

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1321
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 1322
  /* // library marker kkossev.commonLib, line 1323
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1324
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 1325
        return true // library marker kkossev.commonLib, line 1326
    } // library marker kkossev.commonLib, line 1327
*/ // library marker kkossev.commonLib, line 1328
    Map descMap = [:] // library marker kkossev.commonLib, line 1329
    try { // library marker kkossev.commonLib, line 1330
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1331
    } // library marker kkossev.commonLib, line 1332
    catch (e1) { // library marker kkossev.commonLib, line 1333
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1334
        // try alternative custom parsing // library marker kkossev.commonLib, line 1335
        descMap = [:] // library marker kkossev.commonLib, line 1336
        try { // library marker kkossev.commonLib, line 1337
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1338
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1339
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1340
            } // library marker kkossev.commonLib, line 1341
        } // library marker kkossev.commonLib, line 1342
        catch (e2) { // library marker kkossev.commonLib, line 1343
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1344
            return true // library marker kkossev.commonLib, line 1345
        } // library marker kkossev.commonLib, line 1346
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1347
    } // library marker kkossev.commonLib, line 1348
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 1349
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 1350
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1351
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1352
        return false // library marker kkossev.commonLib, line 1353
    } // library marker kkossev.commonLib, line 1354
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1355
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1356
    // attribute report received // library marker kkossev.commonLib, line 1357
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1358
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1359
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1360
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1361
    } // library marker kkossev.commonLib, line 1362
    attrData.each { // library marker kkossev.commonLib, line 1363
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1364
        //def map = [:] // library marker kkossev.commonLib, line 1365
        if (it.status == '86') { // library marker kkossev.commonLib, line 1366
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1367
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1368
        } // library marker kkossev.commonLib, line 1369
        switch (it.cluster) { // library marker kkossev.commonLib, line 1370
            case '0000' : // library marker kkossev.commonLib, line 1371
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1372
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1373
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1374
                } // library marker kkossev.commonLib, line 1375
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1376
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1377
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1378
                } // library marker kkossev.commonLib, line 1379
                else { // library marker kkossev.commonLib, line 1380
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1381
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1382
                } // library marker kkossev.commonLib, line 1383
                break // library marker kkossev.commonLib, line 1384
            default : // library marker kkossev.commonLib, line 1385
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1386
                break // library marker kkossev.commonLib, line 1387
        } // switch // library marker kkossev.commonLib, line 1388
    } // for each attribute // library marker kkossev.commonLib, line 1389
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1390
} // library marker kkossev.commonLib, line 1391

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1393

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1395
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1396
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1397
    def mode // library marker kkossev.commonLib, line 1398
    String attrName // library marker kkossev.commonLib, line 1399
    if (it.value == null) { // library marker kkossev.commonLib, line 1400
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1401
        return // library marker kkossev.commonLib, line 1402
    } // library marker kkossev.commonLib, line 1403
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1404
    switch (it.attrId) { // library marker kkossev.commonLib, line 1405
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1406
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1407
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1408
            break // library marker kkossev.commonLib, line 1409
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1410
            attrName = 'On Time' // library marker kkossev.commonLib, line 1411
            mode = value // library marker kkossev.commonLib, line 1412
            break // library marker kkossev.commonLib, line 1413
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1414
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1415
            mode = value // library marker kkossev.commonLib, line 1416
            break // library marker kkossev.commonLib, line 1417
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1418
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1419
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1420
            break // library marker kkossev.commonLib, line 1421
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1422
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1423
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1424
            break // library marker kkossev.commonLib, line 1425
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1426
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1427
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1428
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1429
            } // library marker kkossev.commonLib, line 1430
            else { // library marker kkossev.commonLib, line 1431
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1432
            } // library marker kkossev.commonLib, line 1433
            break // library marker kkossev.commonLib, line 1434
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1435
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1436
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1437
            break // library marker kkossev.commonLib, line 1438
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1439
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1440
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1441
            break // library marker kkossev.commonLib, line 1442
        default : // library marker kkossev.commonLib, line 1443
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1444
            return // library marker kkossev.commonLib, line 1445
    } // library marker kkossev.commonLib, line 1446
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1447
} // library marker kkossev.commonLib, line 1448

void sendButtonEvent(int buttonNumber, String buttonState, boolean isDigital=false) { // library marker kkossev.commonLib, line 1450
    Map event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true, type: isDigital == true ? 'digital' : 'physical'] // library marker kkossev.commonLib, line 1451
    if (txtEnable) { log.info "${device.displayName } $event.descriptionText" } // library marker kkossev.commonLib, line 1452
    sendEvent(event) // library marker kkossev.commonLib, line 1453
} // library marker kkossev.commonLib, line 1454

void push() {                // Momentary capability // library marker kkossev.commonLib, line 1456
    logDebug 'push momentary' // library marker kkossev.commonLib, line 1457
    if (this.respondsTo('customPush')) { customPush(); return } // library marker kkossev.commonLib, line 1458
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.commonLib, line 1459
} // library marker kkossev.commonLib, line 1460

void push(int buttonNumber) {    //pushableButton capability // library marker kkossev.commonLib, line 1462
    logDebug "push button $buttonNumber" // library marker kkossev.commonLib, line 1463
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.commonLib, line 1464
    sendButtonEvent(buttonNumber, 'pushed', isDigital = true) // library marker kkossev.commonLib, line 1465
} // library marker kkossev.commonLib, line 1466

void doubleTap(int buttonNumber) { // library marker kkossev.commonLib, line 1468
    sendButtonEvent(buttonNumber, 'doubleTapped', isDigital = true) // library marker kkossev.commonLib, line 1469
} // library marker kkossev.commonLib, line 1470

void hold(int buttonNumber) { // library marker kkossev.commonLib, line 1472
    sendButtonEvent(buttonNumber, 'held', isDigital = true) // library marker kkossev.commonLib, line 1473
} // library marker kkossev.commonLib, line 1474

void release(int buttonNumber) { // library marker kkossev.commonLib, line 1476
    sendButtonEvent(buttonNumber, 'released', isDigital = true) // library marker kkossev.commonLib, line 1477
} // library marker kkossev.commonLib, line 1478

void sendNumberOfButtonsEvent(int numberOfButtons) { // library marker kkossev.commonLib, line 1480
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1481
} // library marker kkossev.commonLib, line 1482

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1484
void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.commonLib, line 1485
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1486
} // library marker kkossev.commonLib, line 1487

/* // library marker kkossev.commonLib, line 1489
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1490
 * Level Control Cluster            0x0008 // library marker kkossev.commonLib, line 1491
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1492
*/ // library marker kkossev.commonLib, line 1493
void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1494
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1495
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1496
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1497
    } // library marker kkossev.commonLib, line 1498
    else if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1499
        parseLevelControlClusterBulb(descMap) // library marker kkossev.commonLib, line 1500
    } // library marker kkossev.commonLib, line 1501
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1502
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1503
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1504
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1505
    } // library marker kkossev.commonLib, line 1506
    else { // library marker kkossev.commonLib, line 1507
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1508
    } // library marker kkossev.commonLib, line 1509
} // library marker kkossev.commonLib, line 1510

void sendLevelControlEvent(final int rawValue) { // library marker kkossev.commonLib, line 1512
    int value = rawValue as int // library marker kkossev.commonLib, line 1513
    if (value < 0) { value = 0 } // library marker kkossev.commonLib, line 1514
    if (value > 100) { value = 100 } // library marker kkossev.commonLib, line 1515
    Map map = [:] // library marker kkossev.commonLib, line 1516

    boolean isDigital = state.states['isDigital'] // library marker kkossev.commonLib, line 1518
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1519

    map.name = 'level' // library marker kkossev.commonLib, line 1521
    map.value = value // library marker kkossev.commonLib, line 1522
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1523
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1524
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1525
        map.isStateChange = true // library marker kkossev.commonLib, line 1526
    } // library marker kkossev.commonLib, line 1527
    else { // library marker kkossev.commonLib, line 1528
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.commonLib, line 1529
    } // library marker kkossev.commonLib, line 1530
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1531
    sendEvent(map) // library marker kkossev.commonLib, line 1532
    clearIsDigital() // library marker kkossev.commonLib, line 1533
} // library marker kkossev.commonLib, line 1534

/** // library marker kkossev.commonLib, line 1536
 * Get the level transition rate // library marker kkossev.commonLib, line 1537
 * @param level desired target level (0-100) // library marker kkossev.commonLib, line 1538
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.commonLib, line 1539
 * @return transition rate in 1/10ths of a second // library marker kkossev.commonLib, line 1540
 */ // library marker kkossev.commonLib, line 1541
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.commonLib, line 1542
    int rate = 0 // library marker kkossev.commonLib, line 1543
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.commonLib, line 1544
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.commonLib, line 1545
    if (!isOn) { // library marker kkossev.commonLib, line 1546
        currentLevel = 0 // library marker kkossev.commonLib, line 1547
    } // library marker kkossev.commonLib, line 1548
    // Check if 'transitionTime' has a value // library marker kkossev.commonLib, line 1549
    if (transitionTime > 0) { // library marker kkossev.commonLib, line 1550
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.commonLib, line 1551
        rate = transitionTime * 10 // library marker kkossev.commonLib, line 1552
    } else { // library marker kkossev.commonLib, line 1553
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.commonLib, line 1554
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.commonLib, line 1555
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.commonLib, line 1556
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.commonLib, line 1557
        } // library marker kkossev.commonLib, line 1558
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.commonLib, line 1559
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.commonLib, line 1560
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.commonLib, line 1561
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.commonLib, line 1562
        } // library marker kkossev.commonLib, line 1563
    } // library marker kkossev.commonLib, line 1564
    logDebug "using level transition rate ${rate}" // library marker kkossev.commonLib, line 1565
    return rate // library marker kkossev.commonLib, line 1566
} // library marker kkossev.commonLib, line 1567

// Command option that enable changes when off // library marker kkossev.commonLib, line 1569
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.commonLib, line 1570

/** // library marker kkossev.commonLib, line 1572
 * Constrain a value to a range // library marker kkossev.commonLib, line 1573
 * @param value value to constrain // library marker kkossev.commonLib, line 1574
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1575
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1576
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1577
 */ // library marker kkossev.commonLib, line 1578
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.commonLib, line 1579
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1580
        return value // library marker kkossev.commonLib, line 1581
    } // library marker kkossev.commonLib, line 1582
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.commonLib, line 1583
} // library marker kkossev.commonLib, line 1584

/** // library marker kkossev.commonLib, line 1586
 * Constrain a value to a range // library marker kkossev.commonLib, line 1587
 * @param value value to constrain // library marker kkossev.commonLib, line 1588
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1589
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1590
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1591
 */ // library marker kkossev.commonLib, line 1592
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.commonLib, line 1593
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1594
        return value as Integer // library marker kkossev.commonLib, line 1595
    } // library marker kkossev.commonLib, line 1596
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.commonLib, line 1597
} // library marker kkossev.commonLib, line 1598

// Delay before reading attribute (when using polling) // library marker kkossev.commonLib, line 1600
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.commonLib, line 1601

/** // library marker kkossev.commonLib, line 1603
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.commonLib, line 1604
 * @param delayMs delay in milliseconds // library marker kkossev.commonLib, line 1605
 * @param commands commands to execute // library marker kkossev.commonLib, line 1606
 * @return list of commands to be sent to the device // library marker kkossev.commonLib, line 1607
 */ // library marker kkossev.commonLib, line 1608
/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1609
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.commonLib, line 1610
    if (state.reportingEnabled == false) { // library marker kkossev.commonLib, line 1611
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.commonLib, line 1612
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.commonLib, line 1613
    } // library marker kkossev.commonLib, line 1614
    return [] // library marker kkossev.commonLib, line 1615
} // library marker kkossev.commonLib, line 1616

def intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1618
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1619
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1620
} // library marker kkossev.commonLib, line 1621

def intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1623
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1624
} // library marker kkossev.commonLib, line 1625

/** // library marker kkossev.commonLib, line 1627
 * Send 'switchLevel' attribute event // library marker kkossev.commonLib, line 1628
 * @param isOn true if light is on, false otherwise // library marker kkossev.commonLib, line 1629
 * @param level brightness level (0-254) // library marker kkossev.commonLib, line 1630
 */ // library marker kkossev.commonLib, line 1631
/* groovylint-disable-next-line UnusedPrivateMethodParameter */ // library marker kkossev.commonLib, line 1632
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) { // library marker kkossev.commonLib, line 1633
    List<String> cmds = [] // library marker kkossev.commonLib, line 1634
    final Integer level = constrain(value) // library marker kkossev.commonLib, line 1635
    //final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.commonLib, line 1636
    //final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.commonLib, line 1637
    //final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.commonLib, line 1638
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.commonLib, line 1639
        // If light is off, first go to level 0 then to desired level // library marker kkossev.commonLib, line 1640
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.commonLib, line 1641
    } // library marker kkossev.commonLib, line 1642
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.commonLib, line 1643
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.commonLib, line 1644
    /* // library marker kkossev.commonLib, line 1645
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.commonLib, line 1646
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.commonLib, line 1647
    */ // library marker kkossev.commonLib, line 1648
    int duration = 10            // TODO !!! // library marker kkossev.commonLib, line 1649
    String endpointId = '01'     // TODO !!! // library marker kkossev.commonLib, line 1650
    cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.commonLib, line 1651

    return cmds // library marker kkossev.commonLib, line 1653
} // library marker kkossev.commonLib, line 1654

/** // library marker kkossev.commonLib, line 1656
 * Set Level Command // library marker kkossev.commonLib, line 1657
 * @param value level percent (0-100) // library marker kkossev.commonLib, line 1658
 * @param transitionTime transition time in seconds // library marker kkossev.commonLib, line 1659
 * @return List of zigbee commands // library marker kkossev.commonLib, line 1660
 */ // library marker kkossev.commonLib, line 1661
void setLevel(final Object value, final Object transitionTime = null) { // library marker kkossev.commonLib, line 1662
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.commonLib, line 1663
    if (this.respondsTo('customSetLevel')) { // library marker kkossev.commonLib, line 1664
        customSetLevel(value, transitionTime) // library marker kkossev.commonLib, line 1665
        return // library marker kkossev.commonLib, line 1666
    } // library marker kkossev.commonLib, line 1667
    if (DEVICE_TYPE in  ['Bulb']) { setLevelBulb(value, transitionTime); return } // library marker kkossev.commonLib, line 1668
    final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer) // library marker kkossev.commonLib, line 1669
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1670
    sendZigbeeCommands(setLevelPrivate(value, rate)) // library marker kkossev.commonLib, line 1671
} // library marker kkossev.commonLib, line 1672

/* // library marker kkossev.commonLib, line 1674
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1675
 * Color Control Cluster            0x0300 // library marker kkossev.commonLib, line 1676
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1677
*/ // library marker kkossev.commonLib, line 1678
void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1679
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1680
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1681
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1682
    } // library marker kkossev.commonLib, line 1683
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1684
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1685
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1686
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1687
    } // library marker kkossev.commonLib, line 1688
    else { // library marker kkossev.commonLib, line 1689
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1690
    } // library marker kkossev.commonLib, line 1691
} // library marker kkossev.commonLib, line 1692

/* // library marker kkossev.commonLib, line 1694
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1695
 * Illuminance    cluster 0x0400 // library marker kkossev.commonLib, line 1696
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1697
*/ // library marker kkossev.commonLib, line 1698
void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1699
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1700
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1701
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1702
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.commonLib, line 1703
    handleIlluminanceEvent(lux) // library marker kkossev.commonLib, line 1704
} // library marker kkossev.commonLib, line 1705

void handleIlluminanceEvent(int illuminance, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1707
    Map eventMap = [:] // library marker kkossev.commonLib, line 1708
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1709
    eventMap.name = 'illuminance' // library marker kkossev.commonLib, line 1710
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.commonLib, line 1711
    eventMap.value  = illumCorrected // library marker kkossev.commonLib, line 1712
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1713
    eventMap.unit = 'lx' // library marker kkossev.commonLib, line 1714
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1715
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1716
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1717
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1718
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.commonLib, line 1719
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.commonLib, line 1720
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.commonLib, line 1721
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.commonLib, line 1722
        return // library marker kkossev.commonLib, line 1723
    } // library marker kkossev.commonLib, line 1724
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1725
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1726
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1727
        state.lastRx['illumTime'] = now() // library marker kkossev.commonLib, line 1728
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1729
    } // library marker kkossev.commonLib, line 1730
    else {         // queue the event // library marker kkossev.commonLib, line 1731
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1732
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.commonLib, line 1733
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1734
    } // library marker kkossev.commonLib, line 1735
} // library marker kkossev.commonLib, line 1736

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1738
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.commonLib, line 1739
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1740
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1741
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1742
} // library marker kkossev.commonLib, line 1743

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.commonLib, line 1745

/* // library marker kkossev.commonLib, line 1747
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1748
 * temperature // library marker kkossev.commonLib, line 1749
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1750
*/ // library marker kkossev.commonLib, line 1751
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1752
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1753
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1754
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.commonLib, line 1755
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1756
} // library marker kkossev.commonLib, line 1757

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.commonLib, line 1759
    Map eventMap = [:] // library marker kkossev.commonLib, line 1760
    BigDecimal temperature = safeToBigDecimal(temperaturePar) // library marker kkossev.commonLib, line 1761
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1762
    eventMap.name = 'temperature' // library marker kkossev.commonLib, line 1763
    if (location.temperatureScale == 'F') { // library marker kkossev.commonLib, line 1764
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1765
        eventMap.unit = '\u00B0F' // library marker kkossev.commonLib, line 1766
    } // library marker kkossev.commonLib, line 1767
    else { // library marker kkossev.commonLib, line 1768
        eventMap.unit = '\u00B0C' // library marker kkossev.commonLib, line 1769
    } // library marker kkossev.commonLib, line 1770
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)) // library marker kkossev.commonLib, line 1771
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1772
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.commonLib, line 1773
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.commonLib, line 1774
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.commonLib, line 1775
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.commonLib, line 1776
        return // library marker kkossev.commonLib, line 1777
    } // library marker kkossev.commonLib, line 1778
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1779
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1780
    if (state.states['isRefresh'] == true) { // library marker kkossev.commonLib, line 1781
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.commonLib, line 1782
        eventMap.isStateChange = true // library marker kkossev.commonLib, line 1783
    } // library marker kkossev.commonLib, line 1784
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1785
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1786
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1787
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1788
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1789
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1790
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1791
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1792
    } // library marker kkossev.commonLib, line 1793
    else {         // queue the event // library marker kkossev.commonLib, line 1794
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1795
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1796
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1797
    } // library marker kkossev.commonLib, line 1798
} // library marker kkossev.commonLib, line 1799

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1801
private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.commonLib, line 1802
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1803
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1804
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1805
} // library marker kkossev.commonLib, line 1806

/* // library marker kkossev.commonLib, line 1808
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1809
 * humidity // library marker kkossev.commonLib, line 1810
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1811
*/ // library marker kkossev.commonLib, line 1812
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1813
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1814
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1815
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1816
    handleHumidityEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1817
} // library marker kkossev.commonLib, line 1818

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1820
    Map eventMap = [:] // library marker kkossev.commonLib, line 1821
    BigDecimal humidity = safeToBigDecimal(humidityPar) // library marker kkossev.commonLib, line 1822
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1823
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1824
    if (humidity <= 0.0 || humidity > 100.0) { // library marker kkossev.commonLib, line 1825
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})" // library marker kkossev.commonLib, line 1826
        return // library marker kkossev.commonLib, line 1827
    } // library marker kkossev.commonLib, line 1828
    eventMap.value = humidity.setScale(0, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1829
    eventMap.name = 'humidity' // library marker kkossev.commonLib, line 1830
    eventMap.unit = '% RH' // library marker kkossev.commonLib, line 1831
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1832
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1833
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1834
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1835
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1836
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1837
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1838
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1839
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.commonLib, line 1840
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1841
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1842
    } // library marker kkossev.commonLib, line 1843
    else { // library marker kkossev.commonLib, line 1844
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1845
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1846
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1847
    } // library marker kkossev.commonLib, line 1848
} // library marker kkossev.commonLib, line 1849

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1851
private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1852
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1853
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1854
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1855
} // library marker kkossev.commonLib, line 1856

/* // library marker kkossev.commonLib, line 1858
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1859
 * Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1860
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1861
*/ // library marker kkossev.commonLib, line 1862

void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1864
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { // library marker kkossev.commonLib, line 1865
        logWarn 'parseElectricalMeasureCluster is NOT implemented1' // library marker kkossev.commonLib, line 1866
    } // library marker kkossev.commonLib, line 1867
} // library marker kkossev.commonLib, line 1868

/* // library marker kkossev.commonLib, line 1870
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1871
 * Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1872
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1873
*/ // library marker kkossev.commonLib, line 1874

void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1876
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { // library marker kkossev.commonLib, line 1877
        logWarn 'parseMeteringCluster is NOT implemented1' // library marker kkossev.commonLib, line 1878
    } // library marker kkossev.commonLib, line 1879
} // library marker kkossev.commonLib, line 1880

/* // library marker kkossev.commonLib, line 1882
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1883
 * pm2.5 // library marker kkossev.commonLib, line 1884
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1885
*/ // library marker kkossev.commonLib, line 1886
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1887
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1888
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1889
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1890
    BigInteger bigIntegerValue = intBitsToFloat(value.intValue()).toBigInteger() // library marker kkossev.commonLib, line 1891
    handlePm25Event(bigIntegerValue as Integer) // library marker kkossev.commonLib, line 1892
} // library marker kkossev.commonLib, line 1893
// TODO - check if handlePm25Event handler exists !! // library marker kkossev.commonLib, line 1894

/* // library marker kkossev.commonLib, line 1896
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1897
 * Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1898
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1899
*/ // library marker kkossev.commonLib, line 1900
void parseAnalogInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1901
    if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1902
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1903
    } // library marker kkossev.commonLib, line 1904
    else if (DEVICE_TYPE in ['AqaraCube']) { // library marker kkossev.commonLib, line 1905
        parseAqaraCubeAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1906
    } // library marker kkossev.commonLib, line 1907
    else if (isZigUSB()) { // library marker kkossev.commonLib, line 1908
        parseZigUSBAnlogInputCluster(descMap) // library marker kkossev.commonLib, line 1909
    } // library marker kkossev.commonLib, line 1910
    else { // library marker kkossev.commonLib, line 1911
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1912
    } // library marker kkossev.commonLib, line 1913
} // library marker kkossev.commonLib, line 1914

/* // library marker kkossev.commonLib, line 1916
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1917
 * Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1918
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1919
*/ // library marker kkossev.commonLib, line 1920

void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1922
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1923
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1924
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1925
    //Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1926
    if (DEVICE_TYPE in  ['AqaraCube']) { // library marker kkossev.commonLib, line 1927
        parseMultistateInputClusterAqaraCube(descMap) // library marker kkossev.commonLib, line 1928
    } // library marker kkossev.commonLib, line 1929
    else { // library marker kkossev.commonLib, line 1930
        handleMultistateInputEvent(value as int) // library marker kkossev.commonLib, line 1931
    } // library marker kkossev.commonLib, line 1932
} // library marker kkossev.commonLib, line 1933

void handleMultistateInputEvent(int value, boolean isDigital=false) { // library marker kkossev.commonLib, line 1935
    Map eventMap = [:] // library marker kkossev.commonLib, line 1936
    eventMap.value = value // library marker kkossev.commonLib, line 1937
    eventMap.name = 'multistateInput' // library marker kkossev.commonLib, line 1938
    eventMap.unit = '' // library marker kkossev.commonLib, line 1939
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1940
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1941
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1942
    logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1943
} // library marker kkossev.commonLib, line 1944

/* // library marker kkossev.commonLib, line 1946
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1947
 * Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1948
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1949
*/ // library marker kkossev.commonLib, line 1950

void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1952
    if (this.respondsTo('customParseWindowCoveringCluster')) { // library marker kkossev.commonLib, line 1953
        customParseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 1954
    } // library marker kkossev.commonLib, line 1955
    else { // library marker kkossev.commonLib, line 1956
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1957
    } // library marker kkossev.commonLib, line 1958
} // library marker kkossev.commonLib, line 1959

/* // library marker kkossev.commonLib, line 1961
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1962
 * thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1963
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1964
*/ // library marker kkossev.commonLib, line 1965
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1966
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1967
    if (this.respondsTo('customParseThermostatCluster')) { // library marker kkossev.commonLib, line 1968
        customParseThermostatCluster(descMap) // library marker kkossev.commonLib, line 1969
    } // library marker kkossev.commonLib, line 1970
    else { // library marker kkossev.commonLib, line 1971
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1972
    } // library marker kkossev.commonLib, line 1973
} // library marker kkossev.commonLib, line 1974

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1976

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1978
    if (this.respondsTo('customParseFC11Cluster')) { // library marker kkossev.commonLib, line 1979
        customParseFC11Cluster(descMap) // library marker kkossev.commonLib, line 1980
    } // library marker kkossev.commonLib, line 1981
    else { // library marker kkossev.commonLib, line 1982
        logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1983
    } // library marker kkossev.commonLib, line 1984
} // library marker kkossev.commonLib, line 1985

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1987
    logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"    // radars // library marker kkossev.commonLib, line 1988
} // library marker kkossev.commonLib, line 1989

/* // library marker kkossev.commonLib, line 1991
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1992
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1993
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1994
*/ // library marker kkossev.commonLib, line 1995
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1996
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1997
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1998

// Tuya Commands // library marker kkossev.commonLib, line 2000
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 2001
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 2002
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 2003
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 2004
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 2005

// tuya DP type // library marker kkossev.commonLib, line 2007
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 2008
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 2009
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 2010
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 2011
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 2012
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 2013

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 2015
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 2016
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 2017
        Long offset = 0 // library marker kkossev.commonLib, line 2018
        try { // library marker kkossev.commonLib, line 2019
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 2020
        } // library marker kkossev.commonLib, line 2021
        catch (e) { // library marker kkossev.commonLib, line 2022
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 2023
        } // library marker kkossev.commonLib, line 2024
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 2025
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 2026
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 2027
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 2028
    } // library marker kkossev.commonLib, line 2029
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 2030
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 2031
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 2032
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 2033
        if (status != '00') { // library marker kkossev.commonLib, line 2034
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 2035
        } // library marker kkossev.commonLib, line 2036
    } // library marker kkossev.commonLib, line 2037
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 2038
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 2039
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 2040
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 2041
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 2042
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 2043
            return // library marker kkossev.commonLib, line 2044
        } // library marker kkossev.commonLib, line 2045
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 2046
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 2047
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 2048
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 2049
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 2050
            logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 2051
            processTuyaDP( descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 2052
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 2053
        } // library marker kkossev.commonLib, line 2054
    } // library marker kkossev.commonLib, line 2055
    else { // library marker kkossev.commonLib, line 2056
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 2057
    } // library marker kkossev.commonLib, line 2058
} // library marker kkossev.commonLib, line 2059

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 2061
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 2062
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 2063
            return // library marker kkossev.commonLib, line 2064
        } // library marker kkossev.commonLib, line 2065
    } // library marker kkossev.commonLib, line 2066
    // check if the method  method exists // library marker kkossev.commonLib, line 2067
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 2068
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 2069
            return // library marker kkossev.commonLib, line 2070
        } // library marker kkossev.commonLib, line 2071
    } // library marker kkossev.commonLib, line 2072
    switch (dp) { // library marker kkossev.commonLib, line 2073
        case 0x01 : // on/off // library marker kkossev.commonLib, line 2074
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.commonLib, line 2075
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.commonLib, line 2076
            } // library marker kkossev.commonLib, line 2077
            else { // library marker kkossev.commonLib, line 2078
                sendSwitchEvent(fncmd as int) // library marker kkossev.commonLib, line 2079
            } // library marker kkossev.commonLib, line 2080
            break // library marker kkossev.commonLib, line 2081
        case 0x02 : // library marker kkossev.commonLib, line 2082
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.commonLib, line 2083
                handleIlluminanceEvent(fncmd) // library marker kkossev.commonLib, line 2084
            } // library marker kkossev.commonLib, line 2085
            else { // library marker kkossev.commonLib, line 2086
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 2087
            } // library marker kkossev.commonLib, line 2088
            break // library marker kkossev.commonLib, line 2089
        case 0x04 : // battery // library marker kkossev.commonLib, line 2090
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.commonLib, line 2091
            break // library marker kkossev.commonLib, line 2092
        default : // library marker kkossev.commonLib, line 2093
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 2094
            break // library marker kkossev.commonLib, line 2095
    } // library marker kkossev.commonLib, line 2096
} // library marker kkossev.commonLib, line 2097

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 2099
    int retValue = 0 // library marker kkossev.commonLib, line 2100
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 2101
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 2102
        int power = 1 // library marker kkossev.commonLib, line 2103
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 2104
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 2105
            power = power * 256 // library marker kkossev.commonLib, line 2106
        } // library marker kkossev.commonLib, line 2107
    } // library marker kkossev.commonLib, line 2108
    return retValue // library marker kkossev.commonLib, line 2109
} // library marker kkossev.commonLib, line 2110

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 2112
    List<String> cmds = [] // library marker kkossev.commonLib, line 2113
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 2114
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2115
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 2116
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 2117
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 2118
    return cmds // library marker kkossev.commonLib, line 2119
} // library marker kkossev.commonLib, line 2120

private getPACKET_ID() { // library marker kkossev.commonLib, line 2122
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 2123
} // library marker kkossev.commonLib, line 2124

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2126
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 2127
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 2128
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 2129
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 2130
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 2131
} // library marker kkossev.commonLib, line 2132

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 2134
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 2135

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 2137
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 2138
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2139
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 2140
} // library marker kkossev.commonLib, line 2141

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 2143
    List<String> cmds = [] // library marker kkossev.commonLib, line 2144
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2145
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 2146
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2147
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2148
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 2149
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2150
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 2151
        } // library marker kkossev.commonLib, line 2152
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 2153
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 2154
    } // library marker kkossev.commonLib, line 2155
    else { // library marker kkossev.commonLib, line 2156
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 2157
    } // library marker kkossev.commonLib, line 2158
} // library marker kkossev.commonLib, line 2159

/** // library marker kkossev.commonLib, line 2161
 * initializes the device // library marker kkossev.commonLib, line 2162
 * Invoked from configure() // library marker kkossev.commonLib, line 2163
 * @return zigbee commands // library marker kkossev.commonLib, line 2164
 */ // library marker kkossev.commonLib, line 2165
List<String> initializeDevice() { // library marker kkossev.commonLib, line 2166
    List<String> cmds = [] // library marker kkossev.commonLib, line 2167
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 2168

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 2170
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 2171
        return customInitializeDevice() // library marker kkossev.commonLib, line 2172
    } // library marker kkossev.commonLib, line 2173
    // not specific device type - do some generic initializations // library marker kkossev.commonLib, line 2174
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2175
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.commonLib, line 2176
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.commonLib, line 2177
    } // library marker kkossev.commonLib, line 2178
    // // library marker kkossev.commonLib, line 2179
    if (cmds == []) { // library marker kkossev.commonLib, line 2180
        cmds = ['delay 299'] // library marker kkossev.commonLib, line 2181
    } // library marker kkossev.commonLib, line 2182
    return cmds // library marker kkossev.commonLib, line 2183
} // library marker kkossev.commonLib, line 2184

/** // library marker kkossev.commonLib, line 2186
 * configures the device // library marker kkossev.commonLib, line 2187
 * Invoked from configure() // library marker kkossev.commonLib, line 2188
 * @return zigbee commands // library marker kkossev.commonLib, line 2189
 */ // library marker kkossev.commonLib, line 2190
List<String> configureDevice() { // library marker kkossev.commonLib, line 2191
    List<String> cmds = [] // library marker kkossev.commonLib, line 2192
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 2193

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 2195
        cmds += customConfigureDevice() // library marker kkossev.commonLib, line 2196
    } // library marker kkossev.commonLib, line 2197
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += configureDeviceAqaraCube() } // library marker kkossev.commonLib, line 2198
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 2199
    if ( cmds == null || cmds == []) { // library marker kkossev.commonLib, line 2200
        cmds = ['delay 277',] // library marker kkossev.commonLib, line 2201
    } // library marker kkossev.commonLib, line 2202
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 2203
    return cmds // library marker kkossev.commonLib, line 2204
} // library marker kkossev.commonLib, line 2205

/* // library marker kkossev.commonLib, line 2207
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2208
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 2209
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2210
*/ // library marker kkossev.commonLib, line 2211

void refresh() { // library marker kkossev.commonLib, line 2213
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2214
    checkDriverVersion() // library marker kkossev.commonLib, line 2215
    List<String> cmds = [] // library marker kkossev.commonLib, line 2216
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 2217

    // device type specific refresh handlers // library marker kkossev.commonLib, line 2219
    if (this.respondsTo('customRefresh')) { // library marker kkossev.commonLib, line 2220
        cmds += customRefresh() // library marker kkossev.commonLib, line 2221
    } // library marker kkossev.commonLib, line 2222
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += refreshAqaraCube() } // library marker kkossev.commonLib, line 2223
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 2224
    else { // library marker kkossev.commonLib, line 2225
        // generic refresh handling, based on teh device capabilities // library marker kkossev.commonLib, line 2226
        if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 2227
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)         // battery voltage // library marker kkossev.commonLib, line 2228
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)         // battery percentage // library marker kkossev.commonLib, line 2229
        } // library marker kkossev.commonLib, line 2230
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2231
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2232
            cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership // library marker kkossev.commonLib, line 2233
        } // library marker kkossev.commonLib, line 2234
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2235
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2236
        } // library marker kkossev.commonLib, line 2237
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2238
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2239
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2240
        } // library marker kkossev.commonLib, line 2241
    } // library marker kkossev.commonLib, line 2242

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2244
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2245
    } // library marker kkossev.commonLib, line 2246
    else { // library marker kkossev.commonLib, line 2247
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2248
    } // library marker kkossev.commonLib, line 2249
} // library marker kkossev.commonLib, line 2250

/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2252
void setRefreshRequest()   { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 2253
/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2254
void clearRefreshRequest() { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 2255

void clearInfoEvent() { // library marker kkossev.commonLib, line 2257
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 2258
} // library marker kkossev.commonLib, line 2259

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 2261
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 2262
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 2263
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 2264
    } // library marker kkossev.commonLib, line 2265
    else { // library marker kkossev.commonLib, line 2266
        logInfo "${info}" // library marker kkossev.commonLib, line 2267
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 2268
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 2269
    } // library marker kkossev.commonLib, line 2270
} // library marker kkossev.commonLib, line 2271

void ping() { // library marker kkossev.commonLib, line 2273
    if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2274
        // Aqara TVOC is sleepy or does not respond to the ping. // library marker kkossev.commonLib, line 2275
        logInfo 'ping() command is not available for this sleepy device.' // library marker kkossev.commonLib, line 2276
        sendRttEvent('n/a') // library marker kkossev.commonLib, line 2277
    } // library marker kkossev.commonLib, line 2278
    else { // library marker kkossev.commonLib, line 2279
        if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2280
        state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 2281
        //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 2282
        state.states['isPing'] = true // library marker kkossev.commonLib, line 2283
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 2284
        if (isVirtual()) { // library marker kkossev.commonLib, line 2285
            runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 2286
        } // library marker kkossev.commonLib, line 2287
        else { // library marker kkossev.commonLib, line 2288
            sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 2289
        } // library marker kkossev.commonLib, line 2290
        logDebug 'ping...' // library marker kkossev.commonLib, line 2291
    } // library marker kkossev.commonLib, line 2292
} // library marker kkossev.commonLib, line 2293

def virtualPong() { // library marker kkossev.commonLib, line 2295
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 2296
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2297
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 2298
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 2299
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 2300
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 2301
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 2302
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 2303
        sendRttEvent() // library marker kkossev.commonLib, line 2304
    } // library marker kkossev.commonLib, line 2305
    else { // library marker kkossev.commonLib, line 2306
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 2307
    } // library marker kkossev.commonLib, line 2308
    state.states['isPing'] = false // library marker kkossev.commonLib, line 2309
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 2310
} // library marker kkossev.commonLib, line 2311

/** // library marker kkossev.commonLib, line 2313
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 2314
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 2315
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 2316
 * @return none // library marker kkossev.commonLib, line 2317
 */ // library marker kkossev.commonLib, line 2318
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 2319
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2320
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2321
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 2322
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 2323
    if (value == null) { // library marker kkossev.commonLib, line 2324
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2325
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 2326
    } // library marker kkossev.commonLib, line 2327
    else { // library marker kkossev.commonLib, line 2328
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 2329
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2330
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 2331
    } // library marker kkossev.commonLib, line 2332
} // library marker kkossev.commonLib, line 2333

/** // library marker kkossev.commonLib, line 2335
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 2336
 * @param cluster cluster ID // library marker kkossev.commonLib, line 2337
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 2338
 */ // library marker kkossev.commonLib, line 2339
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 2340
    if (cluster != null) { // library marker kkossev.commonLib, line 2341
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 2342
    } // library marker kkossev.commonLib, line 2343
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 2344
    return 'NULL' // library marker kkossev.commonLib, line 2345
} // library marker kkossev.commonLib, line 2346

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 2348
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 2349
} // library marker kkossev.commonLib, line 2350

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 2352
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 2353
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 2354
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 2355
} // library marker kkossev.commonLib, line 2356

/** // library marker kkossev.commonLib, line 2358
 * Schedule a device health check // library marker kkossev.commonLib, line 2359
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 2360
 */ // library marker kkossev.commonLib, line 2361
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 2362
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 2363
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 2364
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 2365
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 2366
    } // library marker kkossev.commonLib, line 2367
    else { // library marker kkossev.commonLib, line 2368
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 2369
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2370
    } // library marker kkossev.commonLib, line 2371
} // library marker kkossev.commonLib, line 2372

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 2374
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2375
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 2376
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 2377
} // library marker kkossev.commonLib, line 2378

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 2380
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 2381
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2382
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 2383
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 2384
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 2385
        logInfo 'is now online!' // library marker kkossev.commonLib, line 2386
    } // library marker kkossev.commonLib, line 2387
} // library marker kkossev.commonLib, line 2388

void deviceHealthCheck() { // library marker kkossev.commonLib, line 2390
    checkDriverVersion() // library marker kkossev.commonLib, line 2391
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2392
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 2393
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 2394
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 2395
            logWarn 'not present!' // library marker kkossev.commonLib, line 2396
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 2397
        } // library marker kkossev.commonLib, line 2398
    } // library marker kkossev.commonLib, line 2399
    else { // library marker kkossev.commonLib, line 2400
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 2401
    } // library marker kkossev.commonLib, line 2402
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 2403
} // library marker kkossev.commonLib, line 2404

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 2406
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 2407
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 2408
    if (value == 'online') { // library marker kkossev.commonLib, line 2409
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2410
    } // library marker kkossev.commonLib, line 2411
    else { // library marker kkossev.commonLib, line 2412
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 2413
    } // library marker kkossev.commonLib, line 2414
} // library marker kkossev.commonLib, line 2415

/** // library marker kkossev.commonLib, line 2417
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 2418
 */ // library marker kkossev.commonLib, line 2419
void autoPoll() { // library marker kkossev.commonLib, line 2420
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 2421
    checkDriverVersion() // library marker kkossev.commonLib, line 2422
    List<String> cmds = [] // library marker kkossev.commonLib, line 2423
    //if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2424
    //state.states["isRefresh"] = true // library marker kkossev.commonLib, line 2425
    // TODO !!!!!!!! // library marker kkossev.commonLib, line 2426
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 2427
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 2428
    } // library marker kkossev.commonLib, line 2429

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2431
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2432
    } // library marker kkossev.commonLib, line 2433
} // library marker kkossev.commonLib, line 2434

/** // library marker kkossev.commonLib, line 2436
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 2437
 */ // library marker kkossev.commonLib, line 2438
void updated() { // library marker kkossev.commonLib, line 2439
    logInfo 'updated()...' // library marker kkossev.commonLib, line 2440
    checkDriverVersion() // library marker kkossev.commonLib, line 2441
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2442
    unschedule() // library marker kkossev.commonLib, line 2443

    if (settings.logEnable) { // library marker kkossev.commonLib, line 2445
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2446
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 2447
    } // library marker kkossev.commonLib, line 2448
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2449
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2450
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 2451
    } // library marker kkossev.commonLib, line 2452

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 2454
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 2455
        // schedule the periodic timer // library marker kkossev.commonLib, line 2456
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 2457
        if (interval > 0) { // library marker kkossev.commonLib, line 2458
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 2459
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 2460
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 2461
        } // library marker kkossev.commonLib, line 2462
    } // library marker kkossev.commonLib, line 2463
    else { // library marker kkossev.commonLib, line 2464
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 2465
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 2466
    } // library marker kkossev.commonLib, line 2467
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 2468
        customUpdated() // library marker kkossev.commonLib, line 2469
    } // library marker kkossev.commonLib, line 2470

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 2472
} // library marker kkossev.commonLib, line 2473

/** // library marker kkossev.commonLib, line 2475
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 2476
 */ // library marker kkossev.commonLib, line 2477
void logsOff() { // library marker kkossev.commonLib, line 2478
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 2479
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2480
} // library marker kkossev.commonLib, line 2481
void traceOff() { // library marker kkossev.commonLib, line 2482
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 2483
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2484
} // library marker kkossev.commonLib, line 2485

void configure(String command) { // library marker kkossev.commonLib, line 2487
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 2488
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 2489
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 2490
        return // library marker kkossev.commonLib, line 2491
    } // library marker kkossev.commonLib, line 2492
    // // library marker kkossev.commonLib, line 2493
    String func // library marker kkossev.commonLib, line 2494
    try { // library marker kkossev.commonLib, line 2495
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 2496
        "$func"() // library marker kkossev.commonLib, line 2497
    } // library marker kkossev.commonLib, line 2498
    catch (e) { // library marker kkossev.commonLib, line 2499
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 2500
        return // library marker kkossev.commonLib, line 2501
    } // library marker kkossev.commonLib, line 2502
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 2503
} // library marker kkossev.commonLib, line 2504

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 2506
void configureHelp(final String val) { // library marker kkossev.commonLib, line 2507
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 2508
} // library marker kkossev.commonLib, line 2509

void loadAllDefaults() { // library marker kkossev.commonLib, line 2511
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 2512
    deleteAllSettings() // library marker kkossev.commonLib, line 2513
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 2514
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 2515
    deleteAllStates() // library marker kkossev.commonLib, line 2516
    deleteAllChildDevices() // library marker kkossev.commonLib, line 2517
    initialize() // library marker kkossev.commonLib, line 2518
    configure()     // calls  also   configureDevice() // library marker kkossev.commonLib, line 2519
    updated() // library marker kkossev.commonLib, line 2520
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 2521
} // library marker kkossev.commonLib, line 2522

void configureNow() { // library marker kkossev.commonLib, line 2524
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 2525
} // library marker kkossev.commonLib, line 2526

/** // library marker kkossev.commonLib, line 2528
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 2529
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 2530
 * @return sends zigbee commands // library marker kkossev.commonLib, line 2531
 */ // library marker kkossev.commonLib, line 2532
List<String> configure() { // library marker kkossev.commonLib, line 2533
    List<String> cmds = [] // library marker kkossev.commonLib, line 2534
    logInfo 'configure...' // library marker kkossev.commonLib, line 2535
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 2536
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 2537
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2538
        aqaraBlackMagic() // library marker kkossev.commonLib, line 2539
    } // library marker kkossev.commonLib, line 2540
    cmds += initializeDevice() // library marker kkossev.commonLib, line 2541
    cmds += configureDevice() // library marker kkossev.commonLib, line 2542
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2543
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 2544
    return cmds // library marker kkossev.commonLib, line 2545
} // library marker kkossev.commonLib, line 2546

/** // library marker kkossev.commonLib, line 2548
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 2549
 */ // library marker kkossev.commonLib, line 2550
void installed() { // library marker kkossev.commonLib, line 2551
    logInfo 'installed...' // library marker kkossev.commonLib, line 2552
    // populate some default values for attributes // library marker kkossev.commonLib, line 2553
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 2554
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 2555
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 2556
    runIn(3, 'updated') // library marker kkossev.commonLib, line 2557
} // library marker kkossev.commonLib, line 2558

/** // library marker kkossev.commonLib, line 2560
 * Invoked when initialize button is clicked // library marker kkossev.commonLib, line 2561
 */ // library marker kkossev.commonLib, line 2562
void initialize() { // library marker kkossev.commonLib, line 2563
    logInfo 'initialize...' // library marker kkossev.commonLib, line 2564
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 2565
    updateTuyaVersion() // library marker kkossev.commonLib, line 2566
    updateAqaraVersion() // library marker kkossev.commonLib, line 2567
} // library marker kkossev.commonLib, line 2568

/* // library marker kkossev.commonLib, line 2570
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2571
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 2572
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2573
*/ // library marker kkossev.commonLib, line 2574

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2576
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 2577
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 2578
} // library marker kkossev.commonLib, line 2579

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 2581
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 2582
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 2583
} // library marker kkossev.commonLib, line 2584

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2586
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 2587
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 2588
} // library marker kkossev.commonLib, line 2589

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 2591
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 2592
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 2593
    cmd.each { // library marker kkossev.commonLib, line 2594
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 2595
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 2596
    } // library marker kkossev.commonLib, line 2597
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 2598
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 2599
} // library marker kkossev.commonLib, line 2600

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 2602

String getDeviceInfo() { // library marker kkossev.commonLib, line 2604
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 2605
} // library marker kkossev.commonLib, line 2606

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 2608
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 2609
} // library marker kkossev.commonLib, line 2610

void checkDriverVersion() { // library marker kkossev.commonLib, line 2612
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 2613
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2614
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 2615
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2616
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 2617
        updateTuyaVersion() // library marker kkossev.commonLib, line 2618
        updateAqaraVersion() // library marker kkossev.commonLib, line 2619
    } // library marker kkossev.commonLib, line 2620
    // no driver version change // library marker kkossev.commonLib, line 2621
} // library marker kkossev.commonLib, line 2622

// credits @thebearmay // library marker kkossev.commonLib, line 2624
String getModel() { // library marker kkossev.commonLib, line 2625
    try { // library marker kkossev.commonLib, line 2626
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 2627
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2628
    } catch (ignore) { // library marker kkossev.commonLib, line 2629
        try { // library marker kkossev.commonLib, line 2630
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2631
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2632
                return model // library marker kkossev.commonLib, line 2633
            } // library marker kkossev.commonLib, line 2634
        } catch (ignore_again) { // library marker kkossev.commonLib, line 2635
            return '' // library marker kkossev.commonLib, line 2636
        } // library marker kkossev.commonLib, line 2637
    } // library marker kkossev.commonLib, line 2638
} // library marker kkossev.commonLib, line 2639

// credits @thebearmay // library marker kkossev.commonLib, line 2641
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2642
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2643
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2644
    String revision = tokens.last() // library marker kkossev.commonLib, line 2645
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2646
} // library marker kkossev.commonLib, line 2647

/** // library marker kkossev.commonLib, line 2649
 * called from TODO // library marker kkossev.commonLib, line 2650
 */ // library marker kkossev.commonLib, line 2651

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2653
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2654
    unschedule() // library marker kkossev.commonLib, line 2655
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2656
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2657

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2659
} // library marker kkossev.commonLib, line 2660

void resetStatistics() { // library marker kkossev.commonLib, line 2662
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 2663
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 2664
} // library marker kkossev.commonLib, line 2665

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 2667
void resetStats() { // library marker kkossev.commonLib, line 2668
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 2669
    state.stats = [:] // library marker kkossev.commonLib, line 2670
    state.states = [:] // library marker kkossev.commonLib, line 2671
    state.lastRx = [:] // library marker kkossev.commonLib, line 2672
    state.lastTx = [:] // library marker kkossev.commonLib, line 2673
    state.health = [:] // library marker kkossev.commonLib, line 2674
    state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 2675
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 2676
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 2677
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 2678
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 2679
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 2680
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 2681
} // library marker kkossev.commonLib, line 2682

/** // library marker kkossev.commonLib, line 2684
 * called from TODO // library marker kkossev.commonLib, line 2685
 */ // library marker kkossev.commonLib, line 2686
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2687
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2688
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2689
        state.clear() // library marker kkossev.commonLib, line 2690
        unschedule() // library marker kkossev.commonLib, line 2691
        resetStats() // library marker kkossev.commonLib, line 2692
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2693
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2694
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 2695
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2696
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2697
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2698
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 2699
    } // library marker kkossev.commonLib, line 2700

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2702
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2703
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2704
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2705
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2706
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 2707

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 2709
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', false) } // library marker kkossev.commonLib, line 2710
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 2711
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 2712
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 2713
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2714
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2715
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 2716
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 2717
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 2718

    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2720
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2721
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2722
    } // library marker kkossev.commonLib, line 2723
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2724
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.commonLib, line 2725
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.commonLib, line 2726
    } // library marker kkossev.commonLib, line 2727
    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2728
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 2729
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 2730
    if (DEVICE_TYPE in ['AqaraCube'])  { initVarsAqaraCube(fullInit); initEventsAqaraCube(fullInit) } // library marker kkossev.commonLib, line 2731
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2732

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 2734
    if ( mm != null) { // library marker kkossev.commonLib, line 2735
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 2736
    } // library marker kkossev.commonLib, line 2737
    else { // library marker kkossev.commonLib, line 2738
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 2739
    } // library marker kkossev.commonLib, line 2740
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2741
    if ( ep  != null) { // library marker kkossev.commonLib, line 2742
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2743
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2744
    } // library marker kkossev.commonLib, line 2745
    else { // library marker kkossev.commonLib, line 2746
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 2747
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2748
    } // library marker kkossev.commonLib, line 2749
} // library marker kkossev.commonLib, line 2750

/** // library marker kkossev.commonLib, line 2752
 * called from TODO // library marker kkossev.commonLib, line 2753
 */ // library marker kkossev.commonLib, line 2754
void setDestinationEP() { // library marker kkossev.commonLib, line 2755
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2756
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2757
        state.destinationEP = ep // library marker kkossev.commonLib, line 2758
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2759
    } // library marker kkossev.commonLib, line 2760
    else { // library marker kkossev.commonLib, line 2761
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2762
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 2763
    } // library marker kkossev.commonLib, line 2764
} // library marker kkossev.commonLib, line 2765

void  logDebug(final String msg) { // library marker kkossev.commonLib, line 2767
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2768
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2769
    } // library marker kkossev.commonLib, line 2770
} // library marker kkossev.commonLib, line 2771

void logInfo(final String msg) { // library marker kkossev.commonLib, line 2773
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 2774
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2775
    } // library marker kkossev.commonLib, line 2776
} // library marker kkossev.commonLib, line 2777

void logWarn(final String msg) { // library marker kkossev.commonLib, line 2779
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2780
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2781
    } // library marker kkossev.commonLib, line 2782
} // library marker kkossev.commonLib, line 2783

void logTrace(final String msg) { // library marker kkossev.commonLib, line 2785
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 2786
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2787
    } // library marker kkossev.commonLib, line 2788
} // library marker kkossev.commonLib, line 2789

// _DEBUG mode only // library marker kkossev.commonLib, line 2791
void getAllProperties() { // library marker kkossev.commonLib, line 2792
    log.trace 'Properties:' // library marker kkossev.commonLib, line 2793
    device.properties.each { it -> // library marker kkossev.commonLib, line 2794
        log.debug it // library marker kkossev.commonLib, line 2795
    } // library marker kkossev.commonLib, line 2796
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2797
    settings.each { it -> // library marker kkossev.commonLib, line 2798
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2799
    } // library marker kkossev.commonLib, line 2800
    log.trace 'Done' // library marker kkossev.commonLib, line 2801
} // library marker kkossev.commonLib, line 2802

// delete all Preferences  // library marker kkossev.commonLib, line 2804
void deleteAllSettings() { // library marker kkossev.commonLib, line 2805
    settings.each { it -> // library marker kkossev.commonLib, line 2806
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2807
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2808
    } // library marker kkossev.commonLib, line 2809
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2810
} // library marker kkossev.commonLib, line 2811

// delete all attributes // library marker kkossev.commonLib, line 2813
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2814
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 2815
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2816
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2817
    } // library marker kkossev.commonLib, line 2818
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2819
} // library marker kkossev.commonLib, line 2820

// delete all State Variables // library marker kkossev.commonLib, line 2822
void deleteAllStates() { // library marker kkossev.commonLib, line 2823
    state.each { it -> // library marker kkossev.commonLib, line 2824
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2825
    } // library marker kkossev.commonLib, line 2826
    state.clear() // library marker kkossev.commonLib, line 2827
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2828
} // library marker kkossev.commonLib, line 2829

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2831
    unschedule() // library marker kkossev.commonLib, line 2832
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2833
} // library marker kkossev.commonLib, line 2834

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2836
    logDebug 'deleteAllChildDevices : not implemented!' // library marker kkossev.commonLib, line 2837
} // library marker kkossev.commonLib, line 2838

void parseTest(String par) { // library marker kkossev.commonLib, line 2840
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2841
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2842
    parse(par) // library marker kkossev.commonLib, line 2843
} // library marker kkossev.commonLib, line 2844

def testJob() { // library marker kkossev.commonLib, line 2846
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2847
} // library marker kkossev.commonLib, line 2848

/** // library marker kkossev.commonLib, line 2850
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2851
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2852
 */ // library marker kkossev.commonLib, line 2853
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2854
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2855
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2856
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2857
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2858
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2859
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2860
    String cron // library marker kkossev.commonLib, line 2861
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2862
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2863
    } // library marker kkossev.commonLib, line 2864
    else { // library marker kkossev.commonLib, line 2865
        if (minutes < 60) { // library marker kkossev.commonLib, line 2866
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2867
        } // library marker kkossev.commonLib, line 2868
        else { // library marker kkossev.commonLib, line 2869
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2870
        } // library marker kkossev.commonLib, line 2871
    } // library marker kkossev.commonLib, line 2872
    return cron // library marker kkossev.commonLib, line 2873
} // library marker kkossev.commonLib, line 2874

// credits @thebearmay // library marker kkossev.commonLib, line 2876
String formatUptime() { // library marker kkossev.commonLib, line 2877
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2878
} // library marker kkossev.commonLib, line 2879

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2881
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2882
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2883
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2884
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2885
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2886
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2887
} // library marker kkossev.commonLib, line 2888

boolean isTuya() { // library marker kkossev.commonLib, line 2890
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2891
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2892
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2893
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2894
} // library marker kkossev.commonLib, line 2895

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2897
    if (!isTuya()) { // library marker kkossev.commonLib, line 2898
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2899
        return // library marker kkossev.commonLib, line 2900
    } // library marker kkossev.commonLib, line 2901
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2902
    if (application != null) { // library marker kkossev.commonLib, line 2903
        Integer ver // library marker kkossev.commonLib, line 2904
        try { // library marker kkossev.commonLib, line 2905
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2906
        } // library marker kkossev.commonLib, line 2907
        catch (e) { // library marker kkossev.commonLib, line 2908
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2909
            return // library marker kkossev.commonLib, line 2910
        } // library marker kkossev.commonLib, line 2911
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2912
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2913
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2914
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2915
        } // library marker kkossev.commonLib, line 2916
    } // library marker kkossev.commonLib, line 2917
} // library marker kkossev.commonLib, line 2918

boolean isAqara() { // library marker kkossev.commonLib, line 2920
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2921
} // library marker kkossev.commonLib, line 2922

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2924
    if (!isAqara()) { // library marker kkossev.commonLib, line 2925
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2926
        return // library marker kkossev.commonLib, line 2927
    } // library marker kkossev.commonLib, line 2928
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2929
    if (application != null) { // library marker kkossev.commonLib, line 2930
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2931
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2932
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2933
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2934
        } // library marker kkossev.commonLib, line 2935
    } // library marker kkossev.commonLib, line 2936
} // library marker kkossev.commonLib, line 2937

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2939
    try { // library marker kkossev.commonLib, line 2940
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2941
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2942
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2943
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2944
    } catch (e) { // library marker kkossev.commonLib, line 2945
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2946
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2947
    } // library marker kkossev.commonLib, line 2948
} // library marker kkossev.commonLib, line 2949

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2951
    try { // library marker kkossev.commonLib, line 2952
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2953
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2954
        return date.getTime() // library marker kkossev.commonLib, line 2955
    } catch (e) { // library marker kkossev.commonLib, line 2956
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2957
        return now() // library marker kkossev.commonLib, line 2958
    } // library marker kkossev.commonLib, line 2959
} // library marker kkossev.commonLib, line 2960

void test(String par) { // library marker kkossev.commonLib, line 2962
    List<String> cmds = [] // library marker kkossev.commonLib, line 2963
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2964

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2966
    //parse(par) // library marker kkossev.commonLib, line 2967

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2969
} // library marker kkossev.commonLib, line 2970

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (141) kkossev.xiaomiLib ~~~~~
/* groovylint-disable PublicMethodsBeforeNonPublicMethods */ // library marker kkossev.xiaomiLib, line 1
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
 * ver. 1.0.2  2024-03-04 kkossev  - (dev. branch) Groovy linting // library marker kkossev.xiaomiLib, line 27
 * // library marker kkossev.xiaomiLib, line 28
 *                                   TODO: remove the isAqaraXXX  dependencies !! // library marker kkossev.xiaomiLib, line 29
*/ // library marker kkossev.xiaomiLib, line 30

/* groovylint-disable-next-line ImplicitReturnStatement */ // library marker kkossev.xiaomiLib, line 32
static String xiaomiLibVersion()   { '1.0.2' } // library marker kkossev.xiaomiLib, line 33
/* groovylint-disable-next-line ImplicitReturnStatement */ // library marker kkossev.xiaomiLib, line 34
static String xiaomiLibStamp() { '2024/03/04 10:02 PM' } // library marker kkossev.xiaomiLib, line 35

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
void parseXiaomiClusterLib(final Map descMap) { // library marker kkossev.xiaomiLib, line 68
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 69
        logTrace "zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 70
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
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 82
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 83
            if (DEVICE_TYPE in  ['AqaraCube']) { logDebug "AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 84
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 85
            break // library marker kkossev.xiaomiLib, line 86
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 87
            log.info 'unknown attribute - resetting?' // library marker kkossev.xiaomiLib, line 88
            break // library marker kkossev.xiaomiLib, line 89
        case PRESENCE_ATTR_ID:            // 0x0142 FP1 // library marker kkossev.xiaomiLib, line 90
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 91
            parseXiaomiClusterPresence(value) // library marker kkossev.xiaomiLib, line 92
            break // library marker kkossev.xiaomiLib, line 93
        case PRESENCE_ACTIONS_ATTR_ID:    // 0x0143 FP1 // library marker kkossev.xiaomiLib, line 94
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 95
            parseXiaomiClusterPresenceAction(value) // library marker kkossev.xiaomiLib, line 96
            break // library marker kkossev.xiaomiLib, line 97
        case REGION_EVENT_ATTR_ID:        // 0x0151 FP1 // library marker kkossev.xiaomiLib, line 98
            // Region events can be sent fast and furious so buffer them // library marker kkossev.xiaomiLib, line 99
            final Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1]) // library marker kkossev.xiaomiLib, line 100
            final Integer value = HexUtils.hexStringToInt(descMap.value[2..3]) // library marker kkossev.xiaomiLib, line 101
            if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 102
                log.debug "xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 103
            } // library marker kkossev.xiaomiLib, line 104
            if (device.currentValue("region${regionId}") != null) { // library marker kkossev.xiaomiLib, line 105
                RegionUpdateBuffer.get(device.id).put(regionId, value) // library marker kkossev.xiaomiLib, line 106
                runInMillis(REGION_UPDATE_DELAY_MS, 'updateRegions') // library marker kkossev.xiaomiLib, line 107
            } // library marker kkossev.xiaomiLib, line 108
            break // library marker kkossev.xiaomiLib, line 109
        case SENSITIVITY_LEVEL_ATTR_ID:   // 0x010C FP1 // library marker kkossev.xiaomiLib, line 110
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 111
            log.info "sensitivity level is '${SensitivityLevelOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 112
            device.updateSetting('sensitivityLevel', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 113
            break // library marker kkossev.xiaomiLib, line 114
        case TRIGGER_DISTANCE_ATTR_ID:    // 0x0146 FP1 // library marker kkossev.xiaomiLib, line 115
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 116
            log.info "approach distance is '${ApproachDistanceOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 117
            device.updateSetting('approachDistance', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 118
            break // library marker kkossev.xiaomiLib, line 119
        case DIRECTION_MODE_ATTR_ID:     // 0x0144 FP1 // library marker kkossev.xiaomiLib, line 120
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 121
            log.info "monitoring direction mode is '${DirectionModeOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 122
            device.updateSetting('directionMode', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 123
            break // library marker kkossev.xiaomiLib, line 124
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode // library marker kkossev.xiaomiLib, line 125
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 126
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 127
            break // library marker kkossev.xiaomiLib, line 128
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 129
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 130
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 131
            break // library marker kkossev.xiaomiLib, line 132
        case XIAOMI_SPECIAL_REPORT_ID:   // 0x00F7 sent every 55 minutes // library marker kkossev.xiaomiLib, line 133
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value) // library marker kkossev.xiaomiLib, line 134
            parseXiaomiClusterTags(tags) // library marker kkossev.xiaomiLib, line 135
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 136
                sendZigbeeCommands(refreshAqaraCube()) // library marker kkossev.xiaomiLib, line 137
            } // library marker kkossev.xiaomiLib, line 138
            break // library marker kkossev.xiaomiLib, line 139
        case XIAOMI_RAW_ATTR_ID:        // 0xFFF2 FP1 // library marker kkossev.xiaomiLib, line 140
            final byte[] rawData = HexUtils.hexStringToByteArray(descMap.value) // library marker kkossev.xiaomiLib, line 141
            if (rawData.size() == 24 && settings.enableDistanceDirection) { // library marker kkossev.xiaomiLib, line 142
                final int degrees = rawData[19] // library marker kkossev.xiaomiLib, line 143
                final int distanceCm = (rawData[17] << 8) | (rawData[18] & 0x00ff) // library marker kkossev.xiaomiLib, line 144
                if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 145
                    log.debug "location ${degrees}&deg;, ${distanceCm}cm" // library marker kkossev.xiaomiLib, line 146
                } // library marker kkossev.xiaomiLib, line 147
                runIn(1, 'updateLocation', [ data: [ degrees: degrees, distanceCm: distanceCm ] ]) // library marker kkossev.xiaomiLib, line 148
            } // library marker kkossev.xiaomiLib, line 149
            break // library marker kkossev.xiaomiLib, line 150
        default: // library marker kkossev.xiaomiLib, line 151
            log.warn "zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 152
            break // library marker kkossev.xiaomiLib, line 153
    } // library marker kkossev.xiaomiLib, line 154
} // library marker kkossev.xiaomiLib, line 155

void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 157
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 158
        switch (tag) { // library marker kkossev.xiaomiLib, line 159
            case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 160
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 161
                break // library marker kkossev.xiaomiLib, line 162
            case 0x03: // library marker kkossev.xiaomiLib, line 163
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 164
                break // library marker kkossev.xiaomiLib, line 165
            case 0x05: // library marker kkossev.xiaomiLib, line 166
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 167
                break // library marker kkossev.xiaomiLib, line 168
            case 0x06: // library marker kkossev.xiaomiLib, line 169
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 170
                break // library marker kkossev.xiaomiLib, line 171
            case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 172
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 173
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 174
                device.updateDataValue('aqaraVersion', swBuild) // library marker kkossev.xiaomiLib, line 175
                break // library marker kkossev.xiaomiLib, line 176
            case 0x0a: // library marker kkossev.xiaomiLib, line 177
                String nwk = intToHexStr(value as Integer, 2) // library marker kkossev.xiaomiLib, line 178
                if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 179
                String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 180
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 181
                if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 182
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 183
                    state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 184
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 185
                } // library marker kkossev.xiaomiLib, line 186
                break // library marker kkossev.xiaomiLib, line 187
            case 0x0b: // library marker kkossev.xiaomiLib, line 188
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 189
                break // library marker kkossev.xiaomiLib, line 190
            case 0x64: // library marker kkossev.xiaomiLib, line 191
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 192
                // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 193
                break // library marker kkossev.xiaomiLib, line 194
            case 0x65: // library marker kkossev.xiaomiLib, line 195
                if (isAqaraFP1()) { logDebug "xiaomi decode PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 196
                else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 197
                break // library marker kkossev.xiaomiLib, line 198
            case 0x66: // library marker kkossev.xiaomiLib, line 199
                if (isAqaraFP1()) { logDebug "xiaomi decode SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 200
                else if (isAqaraTVOC()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 201
                else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" } // library marker kkossev.xiaomiLib, line 202
                break // library marker kkossev.xiaomiLib, line 203
            case 0x67: // library marker kkossev.xiaomiLib, line 204
                if (isAqaraFP1()) { logDebug "xiaomi decode DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 205
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC: // library marker kkossev.xiaomiLib, line 206
                // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 207
                break // library marker kkossev.xiaomiLib, line 208
            case 0x69: // library marker kkossev.xiaomiLib, line 209
                if (isAqaraFP1()) { logDebug "xiaomi decode TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 210
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 211
                break // library marker kkossev.xiaomiLib, line 212
            case 0x6a: // library marker kkossev.xiaomiLib, line 213
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 214
                else              { logDebug "xiaomi decode MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 215
                break // library marker kkossev.xiaomiLib, line 216
            case 0x6b: // library marker kkossev.xiaomiLib, line 217
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 218
                else              { logDebug "xiaomi decode MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 219
                break // library marker kkossev.xiaomiLib, line 220
            case 0x95: // library marker kkossev.xiaomiLib, line 221
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 222
                break // library marker kkossev.xiaomiLib, line 223
            case 0x96: // library marker kkossev.xiaomiLib, line 224
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 225
                break // library marker kkossev.xiaomiLib, line 226
            case 0x97: // library marker kkossev.xiaomiLib, line 227
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 228
                break // library marker kkossev.xiaomiLib, line 229
            case 0x98: // library marker kkossev.xiaomiLib, line 230
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 231
                break // library marker kkossev.xiaomiLib, line 232
            case 0x9b: // library marker kkossev.xiaomiLib, line 233
                if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 234
                    logDebug "Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})" // library marker kkossev.xiaomiLib, line 235
                    sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 236
                } // library marker kkossev.xiaomiLib, line 237
                else { logDebug "xiaomi decode CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 238
                break // library marker kkossev.xiaomiLib, line 239
            default: // library marker kkossev.xiaomiLib, line 240
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 241
        } // library marker kkossev.xiaomiLib, line 242
    } // library marker kkossev.xiaomiLib, line 243
} // library marker kkossev.xiaomiLib, line 244

/** // library marker kkossev.xiaomiLib, line 246
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 247
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 248
 */ // library marker kkossev.xiaomiLib, line 249
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 250
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 251
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 252
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 253
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 254
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 255
    } // library marker kkossev.xiaomiLib, line 256
    return bigInt // library marker kkossev.xiaomiLib, line 257
} // library marker kkossev.xiaomiLib, line 258

/** // library marker kkossev.xiaomiLib, line 260
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 261
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 262
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 263
 */ // library marker kkossev.xiaomiLib, line 264
private static Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 265
    final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 266
    final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 267
    new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 268
        while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 269
            int tag = stream.read() // library marker kkossev.xiaomiLib, line 270
            int dataType = stream.read() // library marker kkossev.xiaomiLib, line 271
            Object value // library marker kkossev.xiaomiLib, line 272
            if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 273
                int length = stream.read() // library marker kkossev.xiaomiLib, line 274
                byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 275
                stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 276
                value = new String(byteArr) // library marker kkossev.xiaomiLib, line 277
            } else { // library marker kkossev.xiaomiLib, line 278
                int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 279
                value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 280
            } // library marker kkossev.xiaomiLib, line 281
            results[tag] = value // library marker kkossev.xiaomiLib, line 282
        } // library marker kkossev.xiaomiLib, line 283
    } // library marker kkossev.xiaomiLib, line 284
    return results // library marker kkossev.xiaomiLib, line 285
} // library marker kkossev.xiaomiLib, line 286

List<String> refreshXiaomi() { // library marker kkossev.xiaomiLib, line 288
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 289
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.xiaomiLib, line 290
    return cmds // library marker kkossev.xiaomiLib, line 291
} // library marker kkossev.xiaomiLib, line 292

List<String> configureXiaomi() { // library marker kkossev.xiaomiLib, line 294
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 295
    logDebug "configureThermostat() : ${cmds}" // library marker kkossev.xiaomiLib, line 296
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.xiaomiLib, line 297
    return cmds // library marker kkossev.xiaomiLib, line 298
} // library marker kkossev.xiaomiLib, line 299

List<String> initializeXiaomi() { // library marker kkossev.xiaomiLib, line 301
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 302
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 303
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.xiaomiLib, line 304
    return cmds // library marker kkossev.xiaomiLib, line 305
} // library marker kkossev.xiaomiLib, line 306

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 308
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 309
} // library marker kkossev.xiaomiLib, line 310

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 312
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 313
} // library marker kkossev.xiaomiLib, line 314

// ~~~~~ end include (141) kkossev.xiaomiLib ~~~~~

// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterName, UnnecessaryGetter, UnusedImport */ // library marker kkossev.deviceProfileLib, line 1
library( // library marker kkossev.deviceProfileLib, line 2
    base: 'driver', // library marker kkossev.deviceProfileLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.deviceProfileLib, line 4
    category: 'zigbee', // library marker kkossev.deviceProfileLib, line 5
    description: 'Device Profile Library', // library marker kkossev.deviceProfileLib, line 6
    name: 'deviceProfileLib', // library marker kkossev.deviceProfileLib, line 7
    namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy', // library marker kkossev.deviceProfileLib, line 9
    version: '3.0.4', // library marker kkossev.deviceProfileLib, line 10
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
 * // library marker kkossev.deviceProfileLib, line 30
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 31
*/ // library marker kkossev.deviceProfileLib, line 32

static String deviceProfileLibVersion()   { '3.0.4' } // library marker kkossev.deviceProfileLib, line 34
static String deviceProfileLibStamp() { '2024/03/30 12:30 PM' } // library marker kkossev.deviceProfileLib, line 35
import groovy.json.* // library marker kkossev.deviceProfileLib, line 36
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 37
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 38
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 39
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 40

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 42

metadata { // library marker kkossev.deviceProfileLib, line 44
    // no capabilities // library marker kkossev.deviceProfileLib, line 45
    // no attributes // library marker kkossev.deviceProfileLib, line 46
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 47
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 48
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 49
    ] // library marker kkossev.deviceProfileLib, line 50
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 51
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 52
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 53
    ] // library marker kkossev.deviceProfileLib, line 54

    preferences { // library marker kkossev.deviceProfileLib, line 56
        // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 57
        if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 58
            (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 59
                if (inputIt(key) != null) { // library marker kkossev.deviceProfileLib, line 60
                    input inputIt(key) // library marker kkossev.deviceProfileLib, line 61
                } // library marker kkossev.deviceProfileLib, line 62
            } // library marker kkossev.deviceProfileLib, line 63
            if (('motionReset' in DEVICE?.preferences) && (DEVICE?.preferences.motionReset == true)) { // library marker kkossev.deviceProfileLib, line 64
                input(name: 'motionReset', type: 'bool', title: '<b>Reset Motion to Inactive</b>', description: '<i>Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b></i>', defaultValue: false) // library marker kkossev.deviceProfileLib, line 65
                if (motionReset.value == true) { // library marker kkossev.deviceProfileLib, line 66
                    input('motionResetTimer', 'number', title: '<b>Motion Reset Timer</b>', description: '<i>After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds</i>', range: '0..7200', defaultValue: 60) // library marker kkossev.deviceProfileLib, line 67
                } // library marker kkossev.deviceProfileLib, line 68
            } // library marker kkossev.deviceProfileLib, line 69
        } // library marker kkossev.deviceProfileLib, line 70
        if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 71
            input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: '<i>Forcely change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!</i>',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 72
        } // library marker kkossev.deviceProfileLib, line 73
    } // library marker kkossev.deviceProfileLib, line 74
} // library marker kkossev.deviceProfileLib, line 75

String getDeviceProfile()    { state.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 77
Map getDEVICE()              { deviceProfilesV2[getDeviceProfile()] } // library marker kkossev.deviceProfileLib, line 78
Set getDeviceProfiles()      { deviceProfilesV2.keySet() } // library marker kkossev.deviceProfileLib, line 79
List<String> getDeviceProfilesMap()   { deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 80
// ---------------------------------- deviceProfilesV2 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 81

/** // library marker kkossev.deviceProfileLib, line 83
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 84
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 85
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 86
 */ // library marker kkossev.deviceProfileLib, line 87

String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 89
    String key = deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key // library marker kkossev.deviceProfileLib, line 90
    if (key == null) { // library marker kkossev.deviceProfileLib, line 91
        key = deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key // library marker kkossev.deviceProfileLib, line 92
    } // library marker kkossev.deviceProfileLib, line 93
    return key // library marker kkossev.deviceProfileLib, line 94
} // library marker kkossev.deviceProfileLib, line 95

/** // library marker kkossev.deviceProfileLib, line 97
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 98
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 99
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 100
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 101
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 102
 */ // library marker kkossev.deviceProfileLib, line 103
Map getPreferencesMap(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 104
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 105
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 106
        if (debug) { log.warn "getPreferencesMap: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 107
        return [:] // library marker kkossev.deviceProfileLib, line 108
    } // library marker kkossev.deviceProfileLib, line 109
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 110
    def preference // library marker kkossev.deviceProfileLib, line 111
    try { // library marker kkossev.deviceProfileLib, line 112
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 113
        if (debug) { log.debug "getPreferencesMap: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 114
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 115
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 116
            logDebug "getPreferencesMap: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 117
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 118
        } // library marker kkossev.deviceProfileLib, line 119
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 120
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 121
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 122
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 123
        } // library marker kkossev.deviceProfileLib, line 124
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 125
            if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 126
            //def dpMaps   =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 127
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 128
        } // library marker kkossev.deviceProfileLib, line 129
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 130
    } catch (e) { // library marker kkossev.deviceProfileLib, line 131
        if (debug) { log.warn "getPreferencesMap: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 132
        return [:] // library marker kkossev.deviceProfileLib, line 133
    } // library marker kkossev.deviceProfileLib, line 134
    if (debug) { log.debug "getPreferencesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 135
    return foundMap // library marker kkossev.deviceProfileLib, line 136
} // library marker kkossev.deviceProfileLib, line 137

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 139
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 140
    def searchMap = [] // library marker kkossev.deviceProfileLib, line 141
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 142
    if (DEVICE?.tuyaDPs != null) { // library marker kkossev.deviceProfileLib, line 143
        searchMap =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 144
        foundMap = searchMap.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 145
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 146
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 147
            return foundMap // library marker kkossev.deviceProfileLib, line 148
        } // library marker kkossev.deviceProfileLib, line 149
    } // library marker kkossev.deviceProfileLib, line 150
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 151
    if (DEVICE?.attributes != null) { // library marker kkossev.deviceProfileLib, line 152
        searchMap  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 153
        foundMap = searchMap.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 154
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 155
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 156
            return foundMap // library marker kkossev.deviceProfileLib, line 157
        } // library marker kkossev.deviceProfileLib, line 158
    } // library marker kkossev.deviceProfileLib, line 159
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 160
    return [:] // library marker kkossev.deviceProfileLib, line 161
} // library marker kkossev.deviceProfileLib, line 162

/** // library marker kkossev.deviceProfileLib, line 164
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 165
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 166
 */ // library marker kkossev.deviceProfileLib, line 167
void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 168
    logTrace "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 169
    Map preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 170
    if (preferences == null || preferences.isEmpty()) { // library marker kkossev.deviceProfileLib, line 171
        logDebug 'Preferences not found!' // library marker kkossev.deviceProfileLib, line 172
        return // library marker kkossev.deviceProfileLib, line 173
    } // library marker kkossev.deviceProfileLib, line 174
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 175
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 176
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 177
        // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 178
        if (mapValue in [true, false]) { // library marker kkossev.deviceProfileLib, line 179
            logDebug "Preference ${parName} is predefined -> (${mapValue})" // library marker kkossev.deviceProfileLib, line 180
            // TODO - set the predefined value // library marker kkossev.deviceProfileLib, line 181
            /* // library marker kkossev.deviceProfileLib, line 182
            if (debug) log.info "par ${parName} defVal = ${parMap.defVal}" // library marker kkossev.deviceProfileLib, line 183
            device.updateSetting("${parMap.name}",[value:parMap.defVal, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 184
            */ // library marker kkossev.deviceProfileLib, line 185
            return // continue // library marker kkossev.deviceProfileLib, line 186
        } // library marker kkossev.deviceProfileLib, line 187
        // find the individual preference map // library marker kkossev.deviceProfileLib, line 188
        parMap = getPreferencesMap(parName, false) // library marker kkossev.deviceProfileLib, line 189
        if (parMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 190
            logDebug "Preference ${parName} not found in tuyaDPs or attributes map!" // library marker kkossev.deviceProfileLib, line 191
            return // continue // library marker kkossev.deviceProfileLib, line 192
        } // library marker kkossev.deviceProfileLib, line 193
        // parMap = [at:0xE002:0xE005, name:staticDetectionSensitivity, type:number, dt:UINT8, rw:rw, min:0, max:5, scale:1, unit:x, title:Static Detection Sensitivity, description:Static detection sensitivity] // library marker kkossev.deviceProfileLib, line 194
        if (parMap.defVal == null) { // library marker kkossev.deviceProfileLib, line 195
            logDebug "no default value for preference ${parName} !" // library marker kkossev.deviceProfileLib, line 196
            return // continue // library marker kkossev.deviceProfileLib, line 197
        } // library marker kkossev.deviceProfileLib, line 198
        if (debug) { log.info "par ${parName} defVal = ${parMap.defVal}" } // library marker kkossev.deviceProfileLib, line 199
        device.updateSetting("${parMap.name}", [value:parMap.defVal, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 200
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

/** // library marker kkossev.deviceProfileLib, line 219
 * Returns the scaled value of a preference based on its type and scale. // library marker kkossev.deviceProfileLib, line 220
 * @param preference The name of the preference to retrieve. // library marker kkossev.deviceProfileLib, line 221
 * @param dpMap A map containing the type and scale of the preference. // library marker kkossev.deviceProfileLib, line 222
 * @return The scaled value of the preference, or null if the preference is not found or has an unsupported type. // library marker kkossev.deviceProfileLib, line 223
 */ // library marker kkossev.deviceProfileLib, line 224
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 225
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 226
    def scaledValue // library marker kkossev.deviceProfileLib, line 227
    if (value == null) { // library marker kkossev.deviceProfileLib, line 228
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 229
        return null // library marker kkossev.deviceProfileLib, line 230
    } // library marker kkossev.deviceProfileLib, line 231
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 232
        case 'number' : // library marker kkossev.deviceProfileLib, line 233
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 234
            break // library marker kkossev.deviceProfileLib, line 235
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 236
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 237
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 238
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 239
            } // library marker kkossev.deviceProfileLib, line 240
            break // library marker kkossev.deviceProfileLib, line 241
        case 'bool' : // library marker kkossev.deviceProfileLib, line 242
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 243
            break // library marker kkossev.deviceProfileLib, line 244
        case 'enum' : // library marker kkossev.deviceProfileLib, line 245
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 246
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 247
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 248
                return null // library marker kkossev.deviceProfileLib, line 249
            } // library marker kkossev.deviceProfileLib, line 250
            scaledValue = value // library marker kkossev.deviceProfileLib, line 251
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 252
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 253
            } // library marker kkossev.deviceProfileLib, line 254
            break // library marker kkossev.deviceProfileLib, line 255
        default : // library marker kkossev.deviceProfileLib, line 256
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 257
            return null // library marker kkossev.deviceProfileLib, line 258
    } // library marker kkossev.deviceProfileLib, line 259
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 260
    return scaledValue // library marker kkossev.deviceProfileLib, line 261
} // library marker kkossev.deviceProfileLib, line 262

// called from updated() method // library marker kkossev.deviceProfileLib, line 264
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 265
void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 266
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 267
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 268
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 269
        return // library marker kkossev.deviceProfileLib, line 270
    } // library marker kkossev.deviceProfileLib, line 271
    //Integer dpInt = 0 // library marker kkossev.deviceProfileLib, line 272
    def scaledValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 273
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 274
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 275
        /* // library marker kkossev.deviceProfileLib, line 276
        dpInt = safeToInt(dp, -1) // library marker kkossev.deviceProfileLib, line 277
        if (dpInt <= 0) { // library marker kkossev.deviceProfileLib, line 278
            // this is the IAS and other non-Tuya DPs preferences .... // library marker kkossev.deviceProfileLib, line 279
            logDebug "updateAllPreferences: preference ${name} has invalid Tuya dp value ${dp}" // library marker kkossev.deviceProfileLib, line 280
            return // library marker kkossev.deviceProfileLib, line 281
        } // library marker kkossev.deviceProfileLib, line 282
        def dpMaps   =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 283
        */ // library marker kkossev.deviceProfileLib, line 284
        Map foundMap // library marker kkossev.deviceProfileLib, line 285
        foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 286
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 287

        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 289
            // scaledValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 290
            scaledValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 291
            logTrace"scaledValue = ${scaledValue}"                                          // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 292
            if (scaledValue != null) { // library marker kkossev.deviceProfileLib, line 293
                setPar(name, scaledValue) // library marker kkossev.deviceProfileLib, line 294
            } // library marker kkossev.deviceProfileLib, line 295
            else { // library marker kkossev.deviceProfileLib, line 296
                logDebug "updateAllPreferences: preference ${name} is not set (scaledValue was null)" // library marker kkossev.deviceProfileLib, line 297
                return // library marker kkossev.deviceProfileLib, line 298
            } // library marker kkossev.deviceProfileLib, line 299
        } // library marker kkossev.deviceProfileLib, line 300
        else { // library marker kkossev.deviceProfileLib, line 301
            logDebug "warning: couldn't find map for preference ${name}" // library marker kkossev.deviceProfileLib, line 302
            return // library marker kkossev.deviceProfileLib, line 303
        } // library marker kkossev.deviceProfileLib, line 304
    } // library marker kkossev.deviceProfileLib, line 305
    return // library marker kkossev.deviceProfileLib, line 306
} // library marker kkossev.deviceProfileLib, line 307

def divideBy100( val ) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 309
def multiplyBy100( val ) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 310
def divideBy10( val ) { // library marker kkossev.deviceProfileLib, line 311
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 312
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 313
} // library marker kkossev.deviceProfileLib, line 314
def multiplyBy10( val ) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 315
def divideBy1( val ) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 316
def signedInt( val ) { // library marker kkossev.deviceProfileLib, line 317
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 318
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 319
} // library marker kkossev.deviceProfileLib, line 320

/** // library marker kkossev.deviceProfileLib, line 322
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 323
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 324
 * // library marker kkossev.deviceProfileLib, line 325
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 326
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 327
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 328
 */ // library marker kkossev.deviceProfileLib, line 329
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 330
    def value = null    // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 331
    def scaledValue = null // library marker kkossev.deviceProfileLib, line 332
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 333
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 334
        case 'number' : // library marker kkossev.deviceProfileLib, line 335
            value = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 336
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 337
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 338
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 339
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 340
            } // library marker kkossev.deviceProfileLib, line 341
            else { // library marker kkossev.deviceProfileLib, line 342
                scaledValue = value // library marker kkossev.deviceProfileLib, line 343
            } // library marker kkossev.deviceProfileLib, line 344
            break // library marker kkossev.deviceProfileLib, line 345

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 347
            value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 348
            // scale the value // library marker kkossev.deviceProfileLib, line 349
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 350
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 351
            } // library marker kkossev.deviceProfileLib, line 352
            else { // library marker kkossev.deviceProfileLib, line 353
                scaledValue = value // library marker kkossev.deviceProfileLib, line 354
            } // library marker kkossev.deviceProfileLib, line 355
            break // library marker kkossev.deviceProfileLib, line 356

        case 'bool' : // library marker kkossev.deviceProfileLib, line 358
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 359
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 360
            else { // library marker kkossev.deviceProfileLib, line 361
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 362
                return null // library marker kkossev.deviceProfileLib, line 363
            } // library marker kkossev.deviceProfileLib, line 364
            break // library marker kkossev.deviceProfileLib, line 365
        case 'enum' : // library marker kkossev.deviceProfileLib, line 366
            // val could be both integer or float value ... check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 367
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 368
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) {   // TODO - check this !!! // library marker kkossev.deviceProfileLib, line 369
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 370
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 371
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 372
            } // library marker kkossev.deviceProfileLib, line 373
            else { // library marker kkossev.deviceProfileLib, line 374
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 375
            } // library marker kkossev.deviceProfileLib, line 376
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 377
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 378
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 379
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 380
                // find the key for the value // library marker kkossev.deviceProfileLib, line 381
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 382
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 383
                if (key == null) { // library marker kkossev.deviceProfileLib, line 384
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 385
                    return null // library marker kkossev.deviceProfileLib, line 386
                } // library marker kkossev.deviceProfileLib, line 387
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 388
            //return null // library marker kkossev.deviceProfileLib, line 389
            } // library marker kkossev.deviceProfileLib, line 390
            break // library marker kkossev.deviceProfileLib, line 391
        default : // library marker kkossev.deviceProfileLib, line 392
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 393
            return null // library marker kkossev.deviceProfileLib, line 394
    } // library marker kkossev.deviceProfileLib, line 395
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 396
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 397
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 398
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 399
        return null // library marker kkossev.deviceProfileLib, line 400
    } // library marker kkossev.deviceProfileLib, line 401
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 402
    return scaledValue // library marker kkossev.deviceProfileLib, line 403
} // library marker kkossev.deviceProfileLib, line 404

/** // library marker kkossev.deviceProfileLib, line 406
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 407
 * // library marker kkossev.deviceProfileLib, line 408
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 409
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 410
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 411
 */ // library marker kkossev.deviceProfileLib, line 412
boolean setPar(final String par=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 413
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 414
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 415
    logDebug "setPar(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 416
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 417
    if (par == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 418
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 419
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 420
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 421
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 422
    if (scaledValue == null) { logInfo "setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 423
    /* // library marker kkossev.deviceProfileLib, line 424
    // update the device setting // TODO: decide whether the setting must be updated here, or after it is echeod back from the device // library marker kkossev.deviceProfileLib, line 425
    try { // library marker kkossev.deviceProfileLib, line 426
        device.updateSetting("$par", [value:val, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 427
    } // library marker kkossev.deviceProfileLib, line 428
    catch (e) { // library marker kkossev.deviceProfileLib, line 429
        logWarn "setPar: Exception '${e}'caught while updateSetting <b>$par</b>(<b>$val</b>) type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 430
        return false // library marker kkossev.deviceProfileLib, line 431
    } // library marker kkossev.deviceProfileLib, line 432
    */ // library marker kkossev.deviceProfileLib, line 433
    //logDebug "setPar: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 434
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 435
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 436
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 437
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 438
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 439
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 440
        try { // library marker kkossev.deviceProfileLib, line 441
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 442
        } // library marker kkossev.deviceProfileLib, line 443
        catch (e) { // library marker kkossev.deviceProfileLib, line 444
            logWarn "setPar: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 445
            return false // library marker kkossev.deviceProfileLib, line 446
        } // library marker kkossev.deviceProfileLib, line 447
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 448
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 449
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 450
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 451
            return false // library marker kkossev.deviceProfileLib, line 452
        } // library marker kkossev.deviceProfileLib, line 453
        else { // library marker kkossev.deviceProfileLib, line 454
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 455
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 456
        } // library marker kkossev.deviceProfileLib, line 457
    } // library marker kkossev.deviceProfileLib, line 458
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 459
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 460
        def valMiscType // library marker kkossev.deviceProfileLib, line 461
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 462
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 463
            // find the key for the value // library marker kkossev.deviceProfileLib, line 464
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 465
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 466
            if (key == null) { // library marker kkossev.deviceProfileLib, line 467
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 468
                return false // library marker kkossev.deviceProfileLib, line 469
            } // library marker kkossev.deviceProfileLib, line 470
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 471
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 472
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 473
        } // library marker kkossev.deviceProfileLib, line 474
        else { // library marker kkossev.deviceProfileLib, line 475
            valMiscType = val // library marker kkossev.deviceProfileLib, line 476
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 477
        } // library marker kkossev.deviceProfileLib, line 478
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 479
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 480
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 481
        return true // library marker kkossev.deviceProfileLib, line 482
    } // library marker kkossev.deviceProfileLib, line 483

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 485
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 486

    try { // library marker kkossev.deviceProfileLib, line 488
        // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 489
        /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 490
        isTuyaDP = dpMap.dp instanceof Number // library marker kkossev.deviceProfileLib, line 491
    } // library marker kkossev.deviceProfileLib, line 492
    catch (e) { // library marker kkossev.deviceProfileLib, line 493
        logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" // library marker kkossev.deviceProfileLib, line 494
        isTuyaDP = false // library marker kkossev.deviceProfileLib, line 495
    //return false // library marker kkossev.deviceProfileLib, line 496
    } // library marker kkossev.deviceProfileLib, line 497
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 498
        // Tuya DP // library marker kkossev.deviceProfileLib, line 499
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 500
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 501
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 502
            return false // library marker kkossev.deviceProfileLib, line 503
        } // library marker kkossev.deviceProfileLib, line 504
        else { // library marker kkossev.deviceProfileLib, line 505
            logInfo "setPar: (2) successfluly executed setPar <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 506
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 507
            return false // library marker kkossev.deviceProfileLib, line 508
        } // library marker kkossev.deviceProfileLib, line 509
    } // library marker kkossev.deviceProfileLib, line 510
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 511
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 512
        int cluster // library marker kkossev.deviceProfileLib, line 513
        int attribute // library marker kkossev.deviceProfileLib, line 514
        int dt // library marker kkossev.deviceProfileLib, line 515
        def mfgCode // library marker kkossev.deviceProfileLib, line 516
        try { // library marker kkossev.deviceProfileLib, line 517
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[0]) // library marker kkossev.deviceProfileLib, line 518
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 519
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[1]) // library marker kkossev.deviceProfileLib, line 520
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 521
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 522
            //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 523
            mfgCode = dpMap.mfgCode // library marker kkossev.deviceProfileLib, line 524
        //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 525
        } // library marker kkossev.deviceProfileLib, line 526
        catch (e) { // library marker kkossev.deviceProfileLib, line 527
            logWarn "setPar: Exception '${e}' caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 528
            return false // library marker kkossev.deviceProfileLib, line 529
        } // library marker kkossev.deviceProfileLib, line 530
        Map mapMfCode = ['mfgCode':mfgCode] // library marker kkossev.deviceProfileLib, line 531
        logDebug "setPar: found cluster=${cluster} attribute=${attribute} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 532
        if (mfgCode != null) { // library marker kkossev.deviceProfileLib, line 533
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay = 200) // library marker kkossev.deviceProfileLib, line 534
        } // library marker kkossev.deviceProfileLib, line 535
        else { // library marker kkossev.deviceProfileLib, line 536
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 537
        } // library marker kkossev.deviceProfileLib, line 538
    } // library marker kkossev.deviceProfileLib, line 539
    else { // library marker kkossev.deviceProfileLib, line 540
        logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 541
        return false // library marker kkossev.deviceProfileLib, line 542
    } // library marker kkossev.deviceProfileLib, line 543
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 544
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 545
    return true // library marker kkossev.deviceProfileLib, line 546
} // library marker kkossev.deviceProfileLib, line 547

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 549
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 550
def sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 551
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 552
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 553
    if (dpMap == null) { // library marker kkossev.deviceProfileLib, line 554
        logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 555
        return null // library marker kkossev.deviceProfileLib, line 556
    } // library marker kkossev.deviceProfileLib, line 557
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 558
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 559
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 560
        return null // library marker kkossev.deviceProfileLib, line 561
    } // library marker kkossev.deviceProfileLib, line 562
    String dpType // library marker kkossev.deviceProfileLib, line 563
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 564
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 565
    } // library marker kkossev.deviceProfileLib, line 566
    else { // library marker kkossev.deviceProfileLib, line 567
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 568
    } // library marker kkossev.deviceProfileLib, line 569
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 570
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 571
        return null // library marker kkossev.deviceProfileLib, line 572
    } // library marker kkossev.deviceProfileLib, line 573
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 574
    def dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 575
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 576
    cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 577
    return cmds // library marker kkossev.deviceProfileLib, line 578
} // library marker kkossev.deviceProfileLib, line 579

boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 581
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 582
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 583
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 584
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 585

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 587
    if (dpMap == null || dpMap.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 588
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 589
    /* groovylint-disable-next-line NoDef */ // library marker kkossev.deviceProfileLib, line 590
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 591
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 592
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 593
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 594
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 595
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 596
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 597
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 598
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 599
        try { // library marker kkossev.deviceProfileLib, line 600
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 601
        } // library marker kkossev.deviceProfileLib, line 602
        catch (e) { // library marker kkossev.deviceProfileLib, line 603
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 604
            return false // library marker kkossev.deviceProfileLib, line 605
        } // library marker kkossev.deviceProfileLib, line 606
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 607
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 608
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 609
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 610
            return true // library marker kkossev.deviceProfileLib, line 611
        } // library marker kkossev.deviceProfileLib, line 612
        else { // library marker kkossev.deviceProfileLib, line 613
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 614
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 615
        } // library marker kkossev.deviceProfileLib, line 616
    } // library marker kkossev.deviceProfileLib, line 617
    else { // library marker kkossev.deviceProfileLib, line 618
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 619
    } // library marker kkossev.deviceProfileLib, line 620
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 621
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 622
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 623
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 624
        // patch !! // library marker kkossev.deviceProfileLib, line 625
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 626
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 627
        } // library marker kkossev.deviceProfileLib, line 628
        else { // library marker kkossev.deviceProfileLib, line 629
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 630
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 631
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 632
        } // library marker kkossev.deviceProfileLib, line 633
        return true // library marker kkossev.deviceProfileLib, line 634
    } // library marker kkossev.deviceProfileLib, line 635
    else { // library marker kkossev.deviceProfileLib, line 636
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 637
    } // library marker kkossev.deviceProfileLib, line 638
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 639
    def preference = dpMap.dp // library marker kkossev.deviceProfileLib, line 640
    try { // library marker kkossev.deviceProfileLib, line 641
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 642
    } // library marker kkossev.deviceProfileLib, line 643
    catch (e) { // library marker kkossev.deviceProfileLib, line 644
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 645
        return false // library marker kkossev.deviceProfileLib, line 646
    } // library marker kkossev.deviceProfileLib, line 647
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 648
        // Tuya DP // library marker kkossev.deviceProfileLib, line 649
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 650
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 651
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 652
            return false // library marker kkossev.deviceProfileLib, line 653
        } // library marker kkossev.deviceProfileLib, line 654
        else { // library marker kkossev.deviceProfileLib, line 655
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 656
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 657
            return true // library marker kkossev.deviceProfileLib, line 658
        } // library marker kkossev.deviceProfileLib, line 659
    } // library marker kkossev.deviceProfileLib, line 660
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 661
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 662
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 663
    } // library marker kkossev.deviceProfileLib, line 664
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 665
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 666
        int cluster // library marker kkossev.deviceProfileLib, line 667
        int attribute // library marker kkossev.deviceProfileLib, line 668
        int dt // library marker kkossev.deviceProfileLib, line 669
        // int mfgCode // library marker kkossev.deviceProfileLib, line 670
        try { // library marker kkossev.deviceProfileLib, line 671
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[0]) // library marker kkossev.deviceProfileLib, line 672
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 673
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[1]) // library marker kkossev.deviceProfileLib, line 674
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 675
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 676
        //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 677
        //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 678
        //  mfgCode = dpMap.mfgCode != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.mfgCode) : null // library marker kkossev.deviceProfileLib, line 679
        //  log.trace "mfgCode = ${mfgCode}" // library marker kkossev.deviceProfileLib, line 680
        } // library marker kkossev.deviceProfileLib, line 681
        catch (e) { // library marker kkossev.deviceProfileLib, line 682
            logWarn "sendAttribute: Exception '${e}'caught while splitting cluster and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 683
            return false // library marker kkossev.deviceProfileLib, line 684
        } // library marker kkossev.deviceProfileLib, line 685

        logDebug "sendAttribute: found cluster=${cluster} attribute=${attribute} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 687
        if (dpMap.mfgCode != null) { // library marker kkossev.deviceProfileLib, line 688
            Map mapMfCode = ['mfgCode':dpMap.mfgCode] // library marker kkossev.deviceProfileLib, line 689
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay = 200) // library marker kkossev.deviceProfileLib, line 690
        } // library marker kkossev.deviceProfileLib, line 691
        else { // library marker kkossev.deviceProfileLib, line 692
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 693
        } // library marker kkossev.deviceProfileLib, line 694
    } // library marker kkossev.deviceProfileLib, line 695
    else { // library marker kkossev.deviceProfileLib, line 696
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 697
        return false // library marker kkossev.deviceProfileLib, line 698
    } // library marker kkossev.deviceProfileLib, line 699
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 700
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 701
    return true // library marker kkossev.deviceProfileLib, line 702
} // library marker kkossev.deviceProfileLib, line 703

/** // library marker kkossev.deviceProfileLib, line 705
 * Sends a command to the device. // library marker kkossev.deviceProfileLib, line 706
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 707
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 708
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 709
 */ // library marker kkossev.deviceProfileLib, line 710
boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 711
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 712
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 713
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 714
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 715
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 716
    if (supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 717
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 718
        return false // library marker kkossev.deviceProfileLib, line 719
    } // library marker kkossev.deviceProfileLib, line 720
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 721
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 722
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 723
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 724
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 725
        return false // library marker kkossev.deviceProfileLib, line 726
    } // library marker kkossev.deviceProfileLib, line 727
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 728
    def func // library marker kkossev.deviceProfileLib, line 729
    /* groovylint-disable-next-line NoDef */ // library marker kkossev.deviceProfileLib, line 730
    def funcResult // library marker kkossev.deviceProfileLib, line 731
    try { // library marker kkossev.deviceProfileLib, line 732
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 733
        if (val != null) { // library marker kkossev.deviceProfileLib, line 734
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 735
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 736
        } // library marker kkossev.deviceProfileLib, line 737
        else { // library marker kkossev.deviceProfileLib, line 738
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 739
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 740
        } // library marker kkossev.deviceProfileLib, line 741
    } // library marker kkossev.deviceProfileLib, line 742
    catch (e) { // library marker kkossev.deviceProfileLib, line 743
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 744
        return false // library marker kkossev.deviceProfileLib, line 745
    } // library marker kkossev.deviceProfileLib, line 746
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 747
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 748
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 749
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 750
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 751
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 752
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 753
        } // library marker kkossev.deviceProfileLib, line 754
    } else { // library marker kkossev.deviceProfileLib, line 755
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 756
        return false // library marker kkossev.deviceProfileLib, line 757
    } // library marker kkossev.deviceProfileLib, line 758
    cmds = funcResult // library marker kkossev.deviceProfileLib, line 759
    if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 760
        sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 761
    } // library marker kkossev.deviceProfileLib, line 762
    return true // library marker kkossev.deviceProfileLib, line 763
} // library marker kkossev.deviceProfileLib, line 764

/** // library marker kkossev.deviceProfileLib, line 766
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 767
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 768
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 769
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 770
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 771
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 772
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 773
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 774
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 775
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 776
 */ // library marker kkossev.deviceProfileLib, line 777
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 778
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 779
    Map input = [:] // library marker kkossev.deviceProfileLib, line 780
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 781
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 782
        if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 783
        return [:] // library marker kkossev.deviceProfileLib, line 784
    } // library marker kkossev.deviceProfileLib, line 785
    /* groovylint-disable-next-line NoDef */ // library marker kkossev.deviceProfileLib, line 786
    def preference // library marker kkossev.deviceProfileLib, line 787
    try { // library marker kkossev.deviceProfileLib, line 788
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 789
    } // library marker kkossev.deviceProfileLib, line 790
    catch (e) { // library marker kkossev.deviceProfileLib, line 791
        if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 792
        return [:] // library marker kkossev.deviceProfileLib, line 793
    } // library marker kkossev.deviceProfileLib, line 794
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 795
    try { // library marker kkossev.deviceProfileLib, line 796
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 797
            if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } // library marker kkossev.deviceProfileLib, line 798
            return [:] // library marker kkossev.deviceProfileLib, line 799
        } // library marker kkossev.deviceProfileLib, line 800
    } // library marker kkossev.deviceProfileLib, line 801
    catch (e) { // library marker kkossev.deviceProfileLib, line 802
        if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 803
        return [:] // library marker kkossev.deviceProfileLib, line 804
    } // library marker kkossev.deviceProfileLib, line 805

    try { // library marker kkossev.deviceProfileLib, line 807
        isTuyaDP = preference.isNumber() // library marker kkossev.deviceProfileLib, line 808
    } // library marker kkossev.deviceProfileLib, line 809
    catch (e) { // library marker kkossev.deviceProfileLib, line 810
        if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 811
        return [:] // library marker kkossev.deviceProfileLib, line 812
    } // library marker kkossev.deviceProfileLib, line 813

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 815
    foundMap = getPreferencesMap(param) // library marker kkossev.deviceProfileLib, line 816
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 817
    if (foundMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 818
        if (debug) { log.warn "inputIt: map not found for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 819
        return [:] // library marker kkossev.deviceProfileLib, line 820
    } // library marker kkossev.deviceProfileLib, line 821
    if (foundMap.rw != 'rw') { // library marker kkossev.deviceProfileLib, line 822
        if (debug) { log.warn "inputIt: param '${param}' is read only!" } // library marker kkossev.deviceProfileLib, line 823
        return [:] // library marker kkossev.deviceProfileLib, line 824
    } // library marker kkossev.deviceProfileLib, line 825
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 826
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 827
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 828
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 829
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 830
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 831
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 832
        } // library marker kkossev.deviceProfileLib, line 833
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 834
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 835
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 836
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 837
            } // library marker kkossev.deviceProfileLib, line 838
        } // library marker kkossev.deviceProfileLib, line 839
    } // library marker kkossev.deviceProfileLib, line 840
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 841
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 842
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 843
    }/* // library marker kkossev.deviceProfileLib, line 844
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 845
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 846
    }*/ // library marker kkossev.deviceProfileLib, line 847
    else { // library marker kkossev.deviceProfileLib, line 848
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 849
        return [:] // library marker kkossev.deviceProfileLib, line 850
    } // library marker kkossev.deviceProfileLib, line 851
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 852
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 853
    } // library marker kkossev.deviceProfileLib, line 854
    return input // library marker kkossev.deviceProfileLib, line 855
} // library marker kkossev.deviceProfileLib, line 856


/** // library marker kkossev.deviceProfileLib, line 859
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 860
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 861
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 862
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 863
 */ // library marker kkossev.deviceProfileLib, line 864
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 865
    String deviceName         = UNKNOWN // library marker kkossev.deviceProfileLib, line 866
    String deviceProfile      = UNKNOWN // library marker kkossev.deviceProfileLib, line 867
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 868
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 869
    deviceProfilesV2.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 870
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 871
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 872
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 873
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV2[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 874
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 875
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 876
            } // library marker kkossev.deviceProfileLib, line 877
        } // library marker kkossev.deviceProfileLib, line 878
    } // library marker kkossev.deviceProfileLib, line 879
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 880
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 881
    } // library marker kkossev.deviceProfileLib, line 882
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 883
} // library marker kkossev.deviceProfileLib, line 884

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 886
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 887
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 888
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 889
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 890
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 891
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 892
    } // library marker kkossev.deviceProfileLib, line 893
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 894
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 895
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 896
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 897
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 898
        device.updateSetting('forcedProfile', [value:deviceProfilesV2[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 899
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 900
    } else { // library marker kkossev.deviceProfileLib, line 901
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 902
    } // library marker kkossev.deviceProfileLib, line 903
} // library marker kkossev.deviceProfileLib, line 904

List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 906
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 907
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 908
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 909
    return cmds // library marker kkossev.deviceProfileLib, line 910
} // library marker kkossev.deviceProfileLib, line 911

List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 913
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 914
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 915
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.deviceProfileLib, line 916
    return cmds // library marker kkossev.deviceProfileLib, line 917
} // library marker kkossev.deviceProfileLib, line 918

List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 920
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 921
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 922
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 923
    return cmds // library marker kkossev.deviceProfileLib, line 924
} // library marker kkossev.deviceProfileLib, line 925

void initVarsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 927
    logDebug "initVarsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 928
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 929
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 930
    } // library marker kkossev.deviceProfileLib, line 931
} // library marker kkossev.deviceProfileLib, line 932

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 934
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 935
} // library marker kkossev.deviceProfileLib, line 936

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 938

// // library marker kkossev.deviceProfileLib, line 940
// called from parse() // library marker kkossev.deviceProfileLib, line 941
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 942
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 943
// // library marker kkossev.deviceProfileLib, line 944
boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 945
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 946
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 947
    Integer dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 948
    List spammyList = deviceProfilesV2[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 949
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 950
} // library marker kkossev.deviceProfileLib, line 951

// // library marker kkossev.deviceProfileLib, line 953
// called from processTuyaDP(), processTuyaDPfromDeviceProfile() // library marker kkossev.deviceProfileLib, line 954
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 955
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 956
// // library marker kkossev.deviceProfileLib, line 957
boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 958
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 959
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 960
    Integer dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 961
    List spammyList = deviceProfilesV2[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 962
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 963
} // library marker kkossev.deviceProfileLib, line 964

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 966
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 967
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 968
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 969
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 970
} // library marker kkossev.deviceProfileLib, line 971

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 973
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 974
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 975
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 976
    } // library marker kkossev.deviceProfileLib, line 977
    else { // library marker kkossev.deviceProfileLib, line 978
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 979
    } // library marker kkossev.deviceProfileLib, line 980
    boolean isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 981
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
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 994
} // library marker kkossev.deviceProfileLib, line 995

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 997
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 998
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 999
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1000
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1001
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1002
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1003
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1004
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1005
        case 'enum' :       // [0:"inactive", 1:"active"] // library marker kkossev.deviceProfileLib, line 1006
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1007
            //logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1008
            break // library marker kkossev.deviceProfileLib, line 1009
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1010
        case 'number' : // library marker kkossev.deviceProfileLib, line 1011
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1012
            //log.warn "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1013
            break // library marker kkossev.deviceProfileLib, line 1014
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1015
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1016
            //logDebug "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1017
            break // library marker kkossev.deviceProfileLib, line 1018
        default : // library marker kkossev.deviceProfileLib, line 1019
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1020
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1021
    } // library marker kkossev.deviceProfileLib, line 1022
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1023
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1024
    } // library marker kkossev.deviceProfileLib, line 1025
    // // library marker kkossev.deviceProfileLib, line 1026
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1027
} // library marker kkossev.deviceProfileLib, line 1028

// // library marker kkossev.deviceProfileLib, line 1030
// called from processTuyaDPfromDeviceProfile() // library marker kkossev.deviceProfileLib, line 1031
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1032
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1033
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1034
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1035
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1036
// // library marker kkossev.deviceProfileLib, line 1037
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1038
// // library marker kkossev.deviceProfileLib, line 1039
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1040
// // library marker kkossev.deviceProfileLib, line 1041
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1042
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1043
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1044
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1045
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1046
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1047
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1048
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1049
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1050
        case 'enum' :       // [0:"inactive", 1:"active"] // library marker kkossev.deviceProfileLib, line 1051
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1052
            break // library marker kkossev.deviceProfileLib, line 1053
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1054
        case 'number' : // library marker kkossev.deviceProfileLib, line 1055
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1056
            break // library marker kkossev.deviceProfileLib, line 1057
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1058
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1059
            break // library marker kkossev.deviceProfileLib, line 1060
        default : // library marker kkossev.deviceProfileLib, line 1061
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1062
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1063
    } // library marker kkossev.deviceProfileLib, line 1064
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1065
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1066
} // library marker kkossev.deviceProfileLib, line 1067

int preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1069
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1070
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1071
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1072
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1073
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1074
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1075
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1076
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1077
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1078
    } // library marker kkossev.deviceProfileLib, line 1079
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1080
    try { // library marker kkossev.deviceProfileLib, line 1081
        fncmd = "$preProcFunction"(fncmd_orig) as int // library marker kkossev.deviceProfileLib, line 1082
    } // library marker kkossev.deviceProfileLib, line 1083
    catch (e) { // library marker kkossev.deviceProfileLib, line 1084
        logWarn "preProc: Exception '${e}'caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1085
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1086
    } // library marker kkossev.deviceProfileLib, line 1087
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1088
    return fncmd // library marker kkossev.deviceProfileLib, line 1089
} // library marker kkossev.deviceProfileLib, line 1090

/** // library marker kkossev.deviceProfileLib, line 1092
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1093
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1094
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1095
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1096
 * // library marker kkossev.deviceProfileLib, line 1097
 * @param descMap The description map of the received DP. // library marker kkossev.deviceProfileLib, line 1098
 * @param dp The value of the received DP. // library marker kkossev.deviceProfileLib, line 1099
 * @param dp_id The ID of the received DP. // library marker kkossev.deviceProfileLib, line 1100
 * @param fncmd The command of the received DP. // library marker kkossev.deviceProfileLib, line 1101
 * @param dp_len The length of the received DP. // library marker kkossev.deviceProfileLib, line 1102
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1103
 */ // library marker kkossev.deviceProfileLib, line 1104
boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1105
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1106
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1107
    //if (!(DEVICE?.device?.type == "radar"))      { return false }   // enabled for all devices - 10/22/2023 !!!    // only these models are handled here for now ... // library marker kkossev.deviceProfileLib, line 1108
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1109

    Map tuyaDPsMap = deviceProfilesV2[state.deviceProfile]?.tuyaDPs as Map // library marker kkossev.deviceProfileLib, line 1111
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1112

    Map foundItem = null // library marker kkossev.deviceProfileLib, line 1114
    tuyaDPsMap.each { item -> // library marker kkossev.deviceProfileLib, line 1115
        if (item['dp'] == (dp as int)) { // library marker kkossev.deviceProfileLib, line 1116
            foundItem = item // library marker kkossev.deviceProfileLib, line 1117
            return // library marker kkossev.deviceProfileLib, line 1118
        } // library marker kkossev.deviceProfileLib, line 1119
    } // library marker kkossev.deviceProfileLib, line 1120
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1121
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1122
        updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len) // library marker kkossev.deviceProfileLib, line 1123
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1124
        return false // library marker kkossev.deviceProfileLib, line 1125
    } // library marker kkossev.deviceProfileLib, line 1126

    return processFoundItem(foundItem, fncmd_orig) // library marker kkossev.deviceProfileLib, line 1128
} // library marker kkossev.deviceProfileLib, line 1129

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1131
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1132
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1133
    if (state.deviceProfile == null)  { logTrace "<b>state.deviceProfile is missing!<b>"; return false } // library marker kkossev.deviceProfileLib, line 1134

    List<Map> attribMap = deviceProfilesV2[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1136
    if (attribMap == null || attribMap.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1137

    Map foundItem = null // library marker kkossev.deviceProfileLib, line 1139
    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1140
    int value // library marker kkossev.deviceProfileLib, line 1141
    try { // library marker kkossev.deviceProfileLib, line 1142
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1143
    } // library marker kkossev.deviceProfileLib, line 1144
    catch (e) { // library marker kkossev.deviceProfileLib, line 1145
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1146
        return false // library marker kkossev.deviceProfileLib, line 1147
    } // library marker kkossev.deviceProfileLib, line 1148
    //logTrace "clusterAttribute = ${clusterAttribute}" // library marker kkossev.deviceProfileLib, line 1149
    attribMap.each { item -> // library marker kkossev.deviceProfileLib, line 1150
        if (item['at'] == clusterAttribute) { // library marker kkossev.deviceProfileLib, line 1151
            foundItem = item // library marker kkossev.deviceProfileLib, line 1152
            return // library marker kkossev.deviceProfileLib, line 1153
        } // library marker kkossev.deviceProfileLib, line 1154
    } // library marker kkossev.deviceProfileLib, line 1155
    if (foundItem == null) { // library marker kkossev.deviceProfileLib, line 1156
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1157
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1158
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1159
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1160
        return false // library marker kkossev.deviceProfileLib, line 1161
    } // library marker kkossev.deviceProfileLib, line 1162

    return processFoundItem(foundItem, value) // library marker kkossev.deviceProfileLib, line 1164
} // library marker kkossev.deviceProfileLib, line 1165

// modifies the value of the foundItem if needed !!! // library marker kkossev.deviceProfileLib, line 1167
/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.deviceProfileLib, line 1168
boolean processFoundItem(final Map foundItem, int value) { // library marker kkossev.deviceProfileLib, line 1169
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1170
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1171
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1172
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1173
        value = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1174
        logDebug "<b>preProc</b> changed ${foundItem.name} value to ${value}" // library marker kkossev.deviceProfileLib, line 1175
    } // library marker kkossev.deviceProfileLib, line 1176
    else { // library marker kkossev.deviceProfileLib, line 1177
        logTrace "no preProc for ${foundItem.name} : ${foundItem}" // library marker kkossev.deviceProfileLib, line 1178
    } // library marker kkossev.deviceProfileLib, line 1179

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1181
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1182
    def existingPrefValue = settings[name]                        // preference name as in Hubitat settings (preferences), if already created. // library marker kkossev.deviceProfileLib, line 1183
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1184
    def perfValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1185
    boolean preferenceExists = existingPrefValue != null          // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1186
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1187
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1188
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1189
    boolean doNotTrace = false  // isSpammyDPsToNotTrace(descMap)          // do not log/trace the spammy clusterAttribute's TODO! // library marker kkossev.deviceProfileLib, line 1190
    if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1191
        logTrace "processFoundItem: clusterAttribute=${clusterAttribute} ${foundItem.name} (type ${foundItem.type}, rw=${foundItem.rw} isAttribute=${isAttribute}, preferenceExists=${preferenceExists}) value is ${value} - ${foundItem.description}" // library marker kkossev.deviceProfileLib, line 1192
    } // library marker kkossev.deviceProfileLib, line 1193
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1194
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1195
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1196
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1197
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1198
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1199

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1201
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1202
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1203
            (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1204
            descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1205
            if (settings.logEnable) { logInfo "${descText }" } // library marker kkossev.deviceProfileLib, line 1206
        } // library marker kkossev.deviceProfileLib, line 1207
        // no more processing is needed, as this clusterAttribute is not a preference and not an attribute // library marker kkossev.deviceProfileLib, line 1208
        return true // library marker kkossev.deviceProfileLib, line 1209
    } // library marker kkossev.deviceProfileLib, line 1210

    // first, check if there is a preference defined to be updated // library marker kkossev.deviceProfileLib, line 1212
    if (preferenceExists) { // library marker kkossev.deviceProfileLib, line 1213
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1214
        //def oldPerfValue = device.getSetting(name) // library marker kkossev.deviceProfileLib, line 1215
        (isEqual, perfValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1216
        if (isEqual == true) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1217
            logDebug "no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${perfValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1218
        } // library marker kkossev.deviceProfileLib, line 1219
        else { // library marker kkossev.deviceProfileLib, line 1220
            logDebug "preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${perfValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1221
            if (debug) { logInfo "updating par ${name} from ${existingPrefValue} to ${perfValue} type ${foundItem.type}" } // library marker kkossev.deviceProfileLib, line 1222
            try { // library marker kkossev.deviceProfileLib, line 1223
                device.updateSetting("${name}", [value:perfValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1224
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1225
            } // library marker kkossev.deviceProfileLib, line 1226
            catch (e) { // library marker kkossev.deviceProfileLib, line 1227
                logWarn "exception ${e} caught while updating preference ${name} to ${value}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1228
            } // library marker kkossev.deviceProfileLib, line 1229
        } // library marker kkossev.deviceProfileLib, line 1230
    } // library marker kkossev.deviceProfileLib, line 1231
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1232
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1233
        unitText = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1234
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1235
    } // library marker kkossev.deviceProfileLib, line 1236

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1238
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1239
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1240
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1241
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1242

        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1244
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1245
                if (settings.logEnable) { logInfo "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1246
            } // library marker kkossev.deviceProfileLib, line 1247
            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1248
            if (name == 'motion' && is2in1()) { // library marker kkossev.deviceProfileLib, line 1249
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1250
            // continue ... // library marker kkossev.deviceProfileLib, line 1251
            } // library marker kkossev.deviceProfileLib, line 1252
            else { // library marker kkossev.deviceProfileLib, line 1253
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1254
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1255
                } // library marker kkossev.deviceProfileLib, line 1256
                else { // library marker kkossev.deviceProfileLib, line 1257
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1258
                } // library marker kkossev.deviceProfileLib, line 1259
            } // library marker kkossev.deviceProfileLib, line 1260
        } // library marker kkossev.deviceProfileLib, line 1261

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an event! // library marker kkossev.deviceProfileLib, line 1263

        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1265
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1266
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1267
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1268
        if (DEVICE_TYPE in ['Thermostat'])  { processDeviceEventThermostat(name, valueScaled, unitText, descText) } // library marker kkossev.deviceProfileLib, line 1269
        else { // library marker kkossev.deviceProfileLib, line 1270
            switch (name) { // library marker kkossev.deviceProfileLib, line 1271
                case 'motion' : // library marker kkossev.deviceProfileLib, line 1272
                    handleMotion(motionActive = value)  // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1273
                    break // library marker kkossev.deviceProfileLib, line 1274
                case 'temperature' : // library marker kkossev.deviceProfileLib, line 1275
                    //temperatureEvent(value / getTemperatureDiv()) // library marker kkossev.deviceProfileLib, line 1276
                    handleTemperatureEvent(valueScaled as Float) // library marker kkossev.deviceProfileLib, line 1277
                    break // library marker kkossev.deviceProfileLib, line 1278
                case 'humidity' : // library marker kkossev.deviceProfileLib, line 1279
                    handleHumidityEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1280
                    break // library marker kkossev.deviceProfileLib, line 1281
                case 'illuminance' : // library marker kkossev.deviceProfileLib, line 1282
                case 'illuminance_lux' : // library marker kkossev.deviceProfileLib, line 1283
                    handleIlluminanceEvent(valueCorrected.toInteger()) // library marker kkossev.deviceProfileLib, line 1284
                    break // library marker kkossev.deviceProfileLib, line 1285
                case 'pushed' : // library marker kkossev.deviceProfileLib, line 1286
                    logDebug "button event received value=${value} valueScaled=${valueScaled} valueCorrected=${valueCorrected}" // library marker kkossev.deviceProfileLib, line 1287
                    buttonEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1288
                    break // library marker kkossev.deviceProfileLib, line 1289
                default : // library marker kkossev.deviceProfileLib, line 1290
                    sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1291
                    if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1292
                        logDebug "event ${name} sent w/ value ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1293
                        logInfo "${descText}"                                 // send an Info log also (because value changed )  // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1294
                    } // library marker kkossev.deviceProfileLib, line 1295
                    break // library marker kkossev.deviceProfileLib, line 1296
            } // library marker kkossev.deviceProfileLib, line 1297
        //logTrace "attrValue=${attrValue} valueScaled=${valueScaled} equal=${isEqual}" // library marker kkossev.deviceProfileLib, line 1298
        } // library marker kkossev.deviceProfileLib, line 1299
    } // library marker kkossev.deviceProfileLib, line 1300
    // all processing was done here! // library marker kkossev.deviceProfileLib, line 1301
    return true // library marker kkossev.deviceProfileLib, line 1302
} // library marker kkossev.deviceProfileLib, line 1303

boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1305
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1306
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 1307
        logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1308
        return false // library marker kkossev.deviceProfileLib, line 1309
    } // library marker kkossev.deviceProfileLib, line 1310
    int validationFailures = 0 // library marker kkossev.deviceProfileLib, line 1311
    int validationFixes = 0 // library marker kkossev.deviceProfileLib, line 1312
    int total = 0 // library marker kkossev.deviceProfileLib, line 1313
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1314
    def oldSettingValue // library marker kkossev.deviceProfileLib, line 1315
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1316
    def newValue // library marker kkossev.deviceProfileLib, line 1317
    String settingType // library marker kkossev.deviceProfileLib, line 1318
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1319
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1320
        if (foundMap == null || foundMap == [:]) { // library marker kkossev.deviceProfileLib, line 1321
            logDebug "validateAndFixPreferences: map not found for preference ${it.key}"    // 10/21/2023 - sevirity lowered to debug // library marker kkossev.deviceProfileLib, line 1322
            return false // library marker kkossev.deviceProfileLib, line 1323
        } // library marker kkossev.deviceProfileLib, line 1324
        settingType = device.getSettingType(it.key) // library marker kkossev.deviceProfileLib, line 1325
        oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1326
        if (settingType == null) { // library marker kkossev.deviceProfileLib, line 1327
            logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" // library marker kkossev.deviceProfileLib, line 1328
            return false // library marker kkossev.deviceProfileLib, line 1329
        } // library marker kkossev.deviceProfileLib, line 1330
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1331
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1332
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1333
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1334
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1335
            try { // library marker kkossev.deviceProfileLib, line 1336
                device.removeSetting(it.key) // library marker kkossev.deviceProfileLib, line 1337
                logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1338
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1339
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1340
                return false // library marker kkossev.deviceProfileLib, line 1341
            } // library marker kkossev.deviceProfileLib, line 1342
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1343
            try { // library marker kkossev.deviceProfileLib, line 1344
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1345
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1346
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1347
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1348
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1349
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1350
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1351
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1352
                    } // library marker kkossev.deviceProfileLib, line 1353
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1354
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1355
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1356
                    } else { // library marker kkossev.deviceProfileLib, line 1357
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1358
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1359
                    } // library marker kkossev.deviceProfileLib, line 1360
                } // library marker kkossev.deviceProfileLib, line 1361
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1362
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1363
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1364
            } // library marker kkossev.deviceProfileLib, line 1365
            catch (e) { // library marker kkossev.deviceProfileLib, line 1366
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1367
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1368
                try { // library marker kkossev.deviceProfileLib, line 1369
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1370
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1371
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1372
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1373
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1374
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" // library marker kkossev.deviceProfileLib, line 1375
                    return false // library marker kkossev.deviceProfileLib, line 1376
                } // library marker kkossev.deviceProfileLib, line 1377
            } // library marker kkossev.deviceProfileLib, line 1378
        } // library marker kkossev.deviceProfileLib, line 1379
        total ++ // library marker kkossev.deviceProfileLib, line 1380
    } // library marker kkossev.deviceProfileLib, line 1381
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1382
    return true // library marker kkossev.deviceProfileLib, line 1383
} // library marker kkossev.deviceProfileLib, line 1384

void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1386
    deviceProfilesV2.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1387
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1388
            logInfo fingerprint // library marker kkossev.deviceProfileLib, line 1389
        } // library marker kkossev.deviceProfileLib, line 1390
    } // library marker kkossev.deviceProfileLib, line 1391
} // library marker kkossev.deviceProfileLib, line 1392

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~
