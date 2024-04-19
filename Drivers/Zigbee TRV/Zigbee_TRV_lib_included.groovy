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
 * ver. 3.1.0  2024-04-19 kkossev  - (dev. branch) commonLib 3.0.7 check; changed to deviceProfilesV3
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
static String timeStamp() { '2024/04/19 8:46 PM' }

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
    List nativelySupportedModes = getAttributesMap('thermostatMode')?.map?.values() as List ?: []
    List systemModes = getAttributesMap('systemMode')?.map?.values() as List ?: []
    List ecoModes = getAttributesMap('ecoMode')?.map?.values() as List ?: []
    List emergencyHeatingModes = getAttributesMap('emergencyHeating')?.map?.values() as List ?: []

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
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
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
  * ver. 3.0.7  2024-04-18 kkossev  - (dev. branch) tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr, activeEpRqCtr; trace ZDO commands; added 0x0406 OccupancyCluster; // library marker kkossev.commonLib, line 39
  * // library marker kkossev.commonLib, line 40
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 41
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 42
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib // library marker kkossev.commonLib, line 43
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 44
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 45
  * // library marker kkossev.commonLib, line 46
*/ // library marker kkossev.commonLib, line 47

String commonLibVersion() { '3.0.7' } // library marker kkossev.commonLib, line 49
String commonLibStamp() { '2024/04/18 11:13 AM' } // library marker kkossev.commonLib, line 50

import groovy.transform.Field // library marker kkossev.commonLib, line 52
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 53
import hubitat.device.Protocol // library marker kkossev.commonLib, line 54
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 55
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 56
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 57
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 58
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 59
import java.math.BigDecimal // library marker kkossev.commonLib, line 60

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 62

metadata { // library marker kkossev.commonLib, line 64
        if (_DEBUG) { // library marker kkossev.commonLib, line 65
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 66
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 67
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 68
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 69
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 70
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 71
            ] // library marker kkossev.commonLib, line 72
        } // library marker kkossev.commonLib, line 73

        // common capabilities for all device types // library marker kkossev.commonLib, line 75
        capability 'Configuration' // library marker kkossev.commonLib, line 76
        capability 'Refresh' // library marker kkossev.commonLib, line 77
        capability 'Health Check' // library marker kkossev.commonLib, line 78

        // common attributes for all device types // library marker kkossev.commonLib, line 80
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 81
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 82
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 83

        // common commands for all device types // library marker kkossev.commonLib, line 85
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 86

        if (deviceType in  ['Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 88
            capability 'Switch' // library marker kkossev.commonLib, line 89
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 90
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 91
            } // library marker kkossev.commonLib, line 92
        } // library marker kkossev.commonLib, line 93

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 95
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 96

    preferences { // library marker kkossev.commonLib, line 98
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 99
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 100
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 101

        if (device) { // library marker kkossev.commonLib, line 103
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 104
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 105
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 106
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 107
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 108
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 109
                } // library marker kkossev.commonLib, line 110
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 111
            } // library marker kkossev.commonLib, line 112
        } // library marker kkossev.commonLib, line 113
    } // library marker kkossev.commonLib, line 114
} // library marker kkossev.commonLib, line 115

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 117
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 118
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 119
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 120
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 121
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 122
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 123
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 124
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 125
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 126
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 127

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 129
    defaultValue: 1, // library marker kkossev.commonLib, line 130
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 131
] // library marker kkossev.commonLib, line 132
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 133
    defaultValue: 240, // library marker kkossev.commonLib, line 134
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 135
] // library marker kkossev.commonLib, line 136
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 137
    defaultValue: 0, // library marker kkossev.commonLib, line 138
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 139
] // library marker kkossev.commonLib, line 140

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 142
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 143
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 144
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 145
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 146
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 147
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 148
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 149
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 150
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 151
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 152
] // library marker kkossev.commonLib, line 153

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 155
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 156
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 157
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 158
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 159
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 160
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 161
//boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 162
//boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 163

/** // library marker kkossev.commonLib, line 165
 * Parse Zigbee message // library marker kkossev.commonLib, line 166
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 167
 */ // library marker kkossev.commonLib, line 168
void parse(final String description) { // library marker kkossev.commonLib, line 169
    checkDriverVersion() // library marker kkossev.commonLib, line 170
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 171
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 172
    setHealthStatusOnline() // library marker kkossev.commonLib, line 173

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 175
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 176
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 177
            parseIasMessage(description) // library marker kkossev.commonLib, line 178
        } // library marker kkossev.commonLib, line 179
        else { // library marker kkossev.commonLib, line 180
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 181
        } // library marker kkossev.commonLib, line 182
        return // library marker kkossev.commonLib, line 183
    } // library marker kkossev.commonLib, line 184
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 185
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 186
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 187
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 188
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 189
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 190
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 191
        return // library marker kkossev.commonLib, line 192
    } // library marker kkossev.commonLib, line 193
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 194
        return // library marker kkossev.commonLib, line 195
    } // library marker kkossev.commonLib, line 196
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 197

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 199
    if (isSpammyDeviceReport(descMap)) { return } // library marker kkossev.commonLib, line 200

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 202
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 203
        return // library marker kkossev.commonLib, line 204
    } // library marker kkossev.commonLib, line 205
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 206
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 207
        return // library marker kkossev.commonLib, line 208
    } // library marker kkossev.commonLib, line 209
    // // library marker kkossev.commonLib, line 210
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 211
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 212
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 213

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 215
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 216
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 217
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 218
            break // library marker kkossev.commonLib, line 219
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 220
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 221
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 222
            break // library marker kkossev.commonLib, line 223
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 224
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 225
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 226
            break // library marker kkossev.commonLib, line 227
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 228
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 229
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 230
            break // library marker kkossev.commonLib, line 231
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 232
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 233
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 234
            break // library marker kkossev.commonLib, line 235
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 236
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 237
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 238
            break // library marker kkossev.commonLib, line 239
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 240
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 241
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 242
            break // library marker kkossev.commonLib, line 243
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 244
            parseAnalogInputCluster(descMap, description) // library marker kkossev.commonLib, line 245
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map, description) } // library marker kkossev.commonLib, line 246
            break // library marker kkossev.commonLib, line 247
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 248
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 249
            break // library marker kkossev.commonLib, line 250
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 251
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 252
            break // library marker kkossev.commonLib, line 253
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 254
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 255
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 256
            break // library marker kkossev.commonLib, line 257
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 258
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 259
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 260
            break // library marker kkossev.commonLib, line 261
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 262
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 263
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 264
            break // library marker kkossev.commonLib, line 265
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 266
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 267
            break // library marker kkossev.commonLib, line 268
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 269
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 270
            break // library marker kkossev.commonLib, line 271
        case 0x0406 : //OCCUPANCY_CLUSTER                   // Sonoff SNZB-06 // library marker kkossev.commonLib, line 272
            parseOccupancyCluster(descMap) // library marker kkossev.commonLib, line 273
            break // library marker kkossev.commonLib, line 274
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 275
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 276
            break // library marker kkossev.commonLib, line 277
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 278
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 279
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 280
            break // library marker kkossev.commonLib, line 281
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 282
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 283
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 284
            break // library marker kkossev.commonLib, line 285
        case 0xE002 : // library marker kkossev.commonLib, line 286
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 287
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 288
            break // library marker kkossev.commonLib, line 289
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 290
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 291
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 292
            break // library marker kkossev.commonLib, line 293
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 294
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 295
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 296
            break // library marker kkossev.commonLib, line 297
        case 0xFC11 :                                       // Sonoff // library marker kkossev.commonLib, line 298
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 299
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 300
            break // library marker kkossev.commonLib, line 301
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 302
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 303
            break // library marker kkossev.commonLib, line 304
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 305
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 306
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 307
            break // library marker kkossev.commonLib, line 308
        default: // library marker kkossev.commonLib, line 309
            if (settings.logEnable) { // library marker kkossev.commonLib, line 310
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 311
            } // library marker kkossev.commonLib, line 312
            break // library marker kkossev.commonLib, line 313
    } // library marker kkossev.commonLib, line 314
} // library marker kkossev.commonLib, line 315

boolean isChattyDeviceReport(final Map descMap)  { // library marker kkossev.commonLib, line 317
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 318
    if (this.respondsTo('isSpammyDPsToNotTrace')) { // library marker kkossev.commonLib, line 319
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 320
    } // library marker kkossev.commonLib, line 321
    return false // library marker kkossev.commonLib, line 322
} // library marker kkossev.commonLib, line 323

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 325
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 326
    if (this.respondsTo('isSpammyDPsToIgnore')) { // library marker kkossev.commonLib, line 327
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 328
    } // library marker kkossev.commonLib, line 329
    return false // library marker kkossev.commonLib, line 330
} // library marker kkossev.commonLib, line 331

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 333
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 334
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 335
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 336
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 337
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 338
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 339
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 340
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 341
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 342
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 343
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 344
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 345
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 346
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 347
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 348
] // library marker kkossev.commonLib, line 349

/** // library marker kkossev.commonLib, line 351
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 352
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 353
 */ // library marker kkossev.commonLib, line 354
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 355
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 356
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 357
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 358
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 359
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 360
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 361
    switch (clusterId) { // library marker kkossev.commonLib, line 362
        case 0x0005 : // library marker kkossev.commonLib, line 363
            if (state.stats == null) { state.stats = [:] } ; state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 364
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 365
            break // library marker kkossev.commonLib, line 366
        case 0x0006 : // library marker kkossev.commonLib, line 367
            if (state.stats == null) { state.stats = [:] } ; state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 368
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 369
            break // library marker kkossev.commonLib, line 370
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 371
            if (state.stats == null) { state.stats = [:] } ; state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 372
            if (settings?.logEnable) { log.info "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 373
            break // library marker kkossev.commonLib, line 374
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 375
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 376
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 377
            break // library marker kkossev.commonLib, line 378
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 379
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 380
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 381
            if (settings?.logEnable) { log.info "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 382
            break // library marker kkossev.commonLib, line 383
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 384
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 385
            break // library marker kkossev.commonLib, line 386
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 387
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 388
            if (settings?.logEnable) { log.info "${clusterInfo}" } // library marker kkossev.commonLib, line 389
            break // library marker kkossev.commonLib, line 390
        default : // library marker kkossev.commonLib, line 391
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 392
            break // library marker kkossev.commonLib, line 393
    } // library marker kkossev.commonLib, line 394
} // library marker kkossev.commonLib, line 395

/** // library marker kkossev.commonLib, line 397
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 398
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 399
 */ // library marker kkossev.commonLib, line 400
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 401
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 402
    switch (commandId) { // library marker kkossev.commonLib, line 403
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 404
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 405
            break // library marker kkossev.commonLib, line 406
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 407
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 408
            break // library marker kkossev.commonLib, line 409
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 410
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 411
            break // library marker kkossev.commonLib, line 412
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 413
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 414
            break // library marker kkossev.commonLib, line 415
        case 0x0B: // default command response // library marker kkossev.commonLib, line 416
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 417
            break // library marker kkossev.commonLib, line 418
        default: // library marker kkossev.commonLib, line 419
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 420
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 421
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 422
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 423
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 424
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 425
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 426
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 427
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 428
            } // library marker kkossev.commonLib, line 429
            break // library marker kkossev.commonLib, line 430
    } // library marker kkossev.commonLib, line 431
} // library marker kkossev.commonLib, line 432

/** // library marker kkossev.commonLib, line 434
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 435
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 436
 */ // library marker kkossev.commonLib, line 437
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 438
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 439
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 440
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 441
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 442
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 443
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 444
    } // library marker kkossev.commonLib, line 445
    else { // library marker kkossev.commonLib, line 446
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 447
    } // library marker kkossev.commonLib, line 448
} // library marker kkossev.commonLib, line 449

/** // library marker kkossev.commonLib, line 451
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 452
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 453
 */ // library marker kkossev.commonLib, line 454
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 455
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 456
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 457
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 458
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 459
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 460
    } // library marker kkossev.commonLib, line 461
    else { // library marker kkossev.commonLib, line 462
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 463
    } // library marker kkossev.commonLib, line 464
} // library marker kkossev.commonLib, line 465

/** // library marker kkossev.commonLib, line 467
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 468
 */ // library marker kkossev.commonLib, line 469
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 470
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 471
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 472
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 473
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 474
        state.reportingEnabled = true // library marker kkossev.commonLib, line 475
    } // library marker kkossev.commonLib, line 476
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 477
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 478
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 479
    } else { // library marker kkossev.commonLib, line 480
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 481
    } // library marker kkossev.commonLib, line 482
} // library marker kkossev.commonLib, line 483

/** // library marker kkossev.commonLib, line 485
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 486
 */ // library marker kkossev.commonLib, line 487
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 488
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 489
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 490
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 491
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 492
    if (status == 0) { // library marker kkossev.commonLib, line 493
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 494
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 495
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 496
        int delta = 0 // library marker kkossev.commonLib, line 497
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 498
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 499
        } // library marker kkossev.commonLib, line 500
        else { // library marker kkossev.commonLib, line 501
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 502
        } // library marker kkossev.commonLib, line 503
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 504
    } // library marker kkossev.commonLib, line 505
    else { // library marker kkossev.commonLib, line 506
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 507
    } // library marker kkossev.commonLib, line 508
} // library marker kkossev.commonLib, line 509

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 511
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 512
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 513
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 514
        return false // library marker kkossev.commonLib, line 515
    } // library marker kkossev.commonLib, line 516
    // execute the customHandler function // library marker kkossev.commonLib, line 517
    boolean result = false // library marker kkossev.commonLib, line 518
    try { // library marker kkossev.commonLib, line 519
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 520
    } // library marker kkossev.commonLib, line 521
    catch (e) { // library marker kkossev.commonLib, line 522
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 523
        return false // library marker kkossev.commonLib, line 524
    } // library marker kkossev.commonLib, line 525
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 526
    return result // library marker kkossev.commonLib, line 527
} // library marker kkossev.commonLib, line 528

/** // library marker kkossev.commonLib, line 530
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 531
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 532
 */ // library marker kkossev.commonLib, line 533
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 534
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 535
    final String commandId = data[0] // library marker kkossev.commonLib, line 536
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 537
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 538
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 539
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 540
    } else { // library marker kkossev.commonLib, line 541
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 542
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 543
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 544
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 545
        } // library marker kkossev.commonLib, line 546
    } // library marker kkossev.commonLib, line 547
} // library marker kkossev.commonLib, line 548

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 550
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 551
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 552
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 553

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 555
    0x00: 'Success', // library marker kkossev.commonLib, line 556
    0x01: 'Failure', // library marker kkossev.commonLib, line 557
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 558
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 559
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 560
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 561
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 562
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 563
    0x88: 'Read Only', // library marker kkossev.commonLib, line 564
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 565
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 566
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 567
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 568
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 569
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 570
    0x94: 'Time out', // library marker kkossev.commonLib, line 571
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 572
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 573
] // library marker kkossev.commonLib, line 574

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 576
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 577
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 578
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 579
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 580
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 581
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 582
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 583
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 584
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 585
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 586
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 587
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 588
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 589
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 590
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 591
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 592
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 593
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 594
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 595
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 596
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 597
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 598
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 599
] // library marker kkossev.commonLib, line 600

void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 602
    if (xiaomiLibVersion() != null) { parseXiaomiClusterLib(descMap) } else { logWarn 'Xiaomi cluster 0xFCC0' } // library marker kkossev.commonLib, line 603
} // library marker kkossev.commonLib, line 604

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 606
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 607
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 608
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 609
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 610
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 611
    return avg // library marker kkossev.commonLib, line 612
} // library marker kkossev.commonLib, line 613

/* // library marker kkossev.commonLib, line 615
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 616
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 617
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 618
*/ // library marker kkossev.commonLib, line 619
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 620

/** // library marker kkossev.commonLib, line 622
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 623
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 624
 */ // library marker kkossev.commonLib, line 625
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 626
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 627
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 628
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 629
        case 0x0000: // library marker kkossev.commonLib, line 630
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 631
            break // library marker kkossev.commonLib, line 632
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 633
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 634
            if (isPing) { // library marker kkossev.commonLib, line 635
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 636
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 637
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 638
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 639
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 640
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 641
                    sendRttEvent() // library marker kkossev.commonLib, line 642
                } // library marker kkossev.commonLib, line 643
                else { // library marker kkossev.commonLib, line 644
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 645
                } // library marker kkossev.commonLib, line 646
                state.states['isPing'] = false // library marker kkossev.commonLib, line 647
            } // library marker kkossev.commonLib, line 648
            else { // library marker kkossev.commonLib, line 649
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 650
            } // library marker kkossev.commonLib, line 651
            break // library marker kkossev.commonLib, line 652
        case 0x0004: // library marker kkossev.commonLib, line 653
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 654
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 655
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 656
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 657
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 658
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 659
            } // library marker kkossev.commonLib, line 660
            break // library marker kkossev.commonLib, line 661
        case 0x0005: // library marker kkossev.commonLib, line 662
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 663
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 664
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 665
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 666
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 667
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 668
            } // library marker kkossev.commonLib, line 669
            break // library marker kkossev.commonLib, line 670
        case 0x0007: // library marker kkossev.commonLib, line 671
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 672
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 673
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 674
            break // library marker kkossev.commonLib, line 675
        case 0xFFDF: // library marker kkossev.commonLib, line 676
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 677
            break // library marker kkossev.commonLib, line 678
        case 0xFFE2: // library marker kkossev.commonLib, line 679
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 680
            break // library marker kkossev.commonLib, line 681
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 682
            logDebug "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 683
            break // library marker kkossev.commonLib, line 684
        case 0xFFFE: // library marker kkossev.commonLib, line 685
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 686
            break // library marker kkossev.commonLib, line 687
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 688
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 689
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 690
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 691
            break // library marker kkossev.commonLib, line 692
        default: // library marker kkossev.commonLib, line 693
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 694
            break // library marker kkossev.commonLib, line 695
    } // library marker kkossev.commonLib, line 696
} // library marker kkossev.commonLib, line 697

// power cluster            0x0001 // library marker kkossev.commonLib, line 699
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 700
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 701
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 702
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 703
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 704
    } // library marker kkossev.commonLib, line 705
    if (this.respondsTo('customParsePowerCluster')) { // library marker kkossev.commonLib, line 706
        customParsePowerCluster(descMap) // library marker kkossev.commonLib, line 707
    } // library marker kkossev.commonLib, line 708
    else { // library marker kkossev.commonLib, line 709
        logDebug "zigbee received Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 710
    } // library marker kkossev.commonLib, line 711
} // library marker kkossev.commonLib, line 712

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 714
void parseIdentityCluster(final Map descMap) { logDebug 'unprocessed parseIdentityCluster' } // library marker kkossev.commonLib, line 715

void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 717
    if (this.respondsTo('customParseScenesCluster')) { customParseScenesCluster(descMap) } else { logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 718
} // library marker kkossev.commonLib, line 719

void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 721
    if (this.respondsTo('customParseGroupsCluster')) { customParseGroupsCluster(descMap) } else { logWarn "unprocessed GroupsCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 722
} // library marker kkossev.commonLib, line 723

/* // library marker kkossev.commonLib, line 725
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 726
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 727
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 728
*/ // library marker kkossev.commonLib, line 729

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 731
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 732
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 733
    } // library marker kkossev.commonLib, line 734
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 735
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 736
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 737
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 738
    } // library marker kkossev.commonLib, line 739
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 740
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 741
    } // library marker kkossev.commonLib, line 742
    else { // library marker kkossev.commonLib, line 743
        if (descMap.attrId != null) { logWarn "parseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.commonLib, line 744
        else { logDebug "parseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 745
    } // library marker kkossev.commonLib, line 746
} // library marker kkossev.commonLib, line 747

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 749
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 750
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 751

void toggle() { // library marker kkossev.commonLib, line 753
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 754
    String state = '' // library marker kkossev.commonLib, line 755
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 756
        state = 'on' // library marker kkossev.commonLib, line 757
    } // library marker kkossev.commonLib, line 758
    else { // library marker kkossev.commonLib, line 759
        state = 'off' // library marker kkossev.commonLib, line 760
    } // library marker kkossev.commonLib, line 761
    descriptionText += state // library marker kkossev.commonLib, line 762
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 763
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 764
} // library marker kkossev.commonLib, line 765

void off() { // library marker kkossev.commonLib, line 767
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 768
        customOff() // library marker kkossev.commonLib, line 769
        return // library marker kkossev.commonLib, line 770
    } // library marker kkossev.commonLib, line 771
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 772
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 773
        return // library marker kkossev.commonLib, line 774
    } // library marker kkossev.commonLib, line 775
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 776
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 777
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 778
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 779
        if (currentState == 'off') { // library marker kkossev.commonLib, line 780
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 781
        } // library marker kkossev.commonLib, line 782
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 783
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 784
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 785
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 786
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 787
    } // library marker kkossev.commonLib, line 788
    /* // library marker kkossev.commonLib, line 789
    else { // library marker kkossev.commonLib, line 790
        if (currentState != 'off') { // library marker kkossev.commonLib, line 791
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 792
        } // library marker kkossev.commonLib, line 793
        else { // library marker kkossev.commonLib, line 794
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 795
            return // library marker kkossev.commonLib, line 796
        } // library marker kkossev.commonLib, line 797
    } // library marker kkossev.commonLib, line 798
    */ // library marker kkossev.commonLib, line 799

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 801
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 802
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 803
} // library marker kkossev.commonLib, line 804

void on() { // library marker kkossev.commonLib, line 806
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 807
        customOn() // library marker kkossev.commonLib, line 808
        return // library marker kkossev.commonLib, line 809
    } // library marker kkossev.commonLib, line 810
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 811
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 812
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 813
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 814
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 815
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 816
        } // library marker kkossev.commonLib, line 817
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 818
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 819
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 820
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 821
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 822
    } // library marker kkossev.commonLib, line 823
    /* // library marker kkossev.commonLib, line 824
    else { // library marker kkossev.commonLib, line 825
        if (currentState != 'on') { // library marker kkossev.commonLib, line 826
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 827
        } // library marker kkossev.commonLib, line 828
        else { // library marker kkossev.commonLib, line 829
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 830
            return // library marker kkossev.commonLib, line 831
        } // library marker kkossev.commonLib, line 832
    } // library marker kkossev.commonLib, line 833
    */ // library marker kkossev.commonLib, line 834
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 835
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 836
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 837
} // library marker kkossev.commonLib, line 838

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 840
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 841
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 842
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 843
    } // library marker kkossev.commonLib, line 844
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 845
    Map map = [:] // library marker kkossev.commonLib, line 846
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 847
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 848
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 849
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 850
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 851
        return // library marker kkossev.commonLib, line 852
    } // library marker kkossev.commonLib, line 853
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 854
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 855
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 856
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 857
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 858
        state.states['debounce'] = true // library marker kkossev.commonLib, line 859
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 860
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 861
    } else { // library marker kkossev.commonLib, line 862
        state.states['debounce'] = true // library marker kkossev.commonLib, line 863
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 864
    } // library marker kkossev.commonLib, line 865
    map.name = 'switch' // library marker kkossev.commonLib, line 866
    map.value = value // library marker kkossev.commonLib, line 867
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 868
    if (isRefresh) { // library marker kkossev.commonLib, line 869
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 870
        map.isStateChange = true // library marker kkossev.commonLib, line 871
    } else { // library marker kkossev.commonLib, line 872
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 873
    } // library marker kkossev.commonLib, line 874
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 875
    sendEvent(map) // library marker kkossev.commonLib, line 876
    clearIsDigital() // library marker kkossev.commonLib, line 877
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 878
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 879
    } // library marker kkossev.commonLib, line 880
} // library marker kkossev.commonLib, line 881

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 883
    '0': 'switch off', // library marker kkossev.commonLib, line 884
    '1': 'switch on', // library marker kkossev.commonLib, line 885
    '2': 'switch last state' // library marker kkossev.commonLib, line 886
] // library marker kkossev.commonLib, line 887

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 889
    '0': 'toggle', // library marker kkossev.commonLib, line 890
    '1': 'state', // library marker kkossev.commonLib, line 891
    '2': 'momentary' // library marker kkossev.commonLib, line 892
] // library marker kkossev.commonLib, line 893

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 895
    Map descMap = [:] // library marker kkossev.commonLib, line 896
    try { // library marker kkossev.commonLib, line 897
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 898
    } // library marker kkossev.commonLib, line 899
    catch (e1) { // library marker kkossev.commonLib, line 900
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 901
        // try alternative custom parsing // library marker kkossev.commonLib, line 902
        descMap = [:] // library marker kkossev.commonLib, line 903
        try { // library marker kkossev.commonLib, line 904
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 905
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 906
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 907
            } // library marker kkossev.commonLib, line 908
        } // library marker kkossev.commonLib, line 909
        catch (e2) { // library marker kkossev.commonLib, line 910
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 911
            return [:] // library marker kkossev.commonLib, line 912
        } // library marker kkossev.commonLib, line 913
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 914
    } // library marker kkossev.commonLib, line 915
    return descMap // library marker kkossev.commonLib, line 916
} // library marker kkossev.commonLib, line 917

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 919
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 920
        return false // library marker kkossev.commonLib, line 921
    } // library marker kkossev.commonLib, line 922
    // try to parse ... // library marker kkossev.commonLib, line 923
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 924
    Map descMap = [:] // library marker kkossev.commonLib, line 925
    try { // library marker kkossev.commonLib, line 926
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 927
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 928
    } // library marker kkossev.commonLib, line 929
    catch (e) { // library marker kkossev.commonLib, line 930
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 931
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 932
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 933
        return true // library marker kkossev.commonLib, line 934
    } // library marker kkossev.commonLib, line 935

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 937
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 938
    } // library marker kkossev.commonLib, line 939
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 940
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 941
    } // library marker kkossev.commonLib, line 942
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 943
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 944
    } // library marker kkossev.commonLib, line 945
    else { // library marker kkossev.commonLib, line 946
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 947
        return false // library marker kkossev.commonLib, line 948
    } // library marker kkossev.commonLib, line 949
    return true    // processed // library marker kkossev.commonLib, line 950
} // library marker kkossev.commonLib, line 951

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 953
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 954
  /* // library marker kkossev.commonLib, line 955
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 956
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 957
        return true // library marker kkossev.commonLib, line 958
    } // library marker kkossev.commonLib, line 959
*/ // library marker kkossev.commonLib, line 960
    Map descMap = [:] // library marker kkossev.commonLib, line 961
    try { // library marker kkossev.commonLib, line 962
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 963
    } // library marker kkossev.commonLib, line 964
    catch (e1) { // library marker kkossev.commonLib, line 965
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 966
        // try alternative custom parsing // library marker kkossev.commonLib, line 967
        descMap = [:] // library marker kkossev.commonLib, line 968
        try { // library marker kkossev.commonLib, line 969
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 970
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 971
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 972
            } // library marker kkossev.commonLib, line 973
        } // library marker kkossev.commonLib, line 974
        catch (e2) { // library marker kkossev.commonLib, line 975
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 976
            return true // library marker kkossev.commonLib, line 977
        } // library marker kkossev.commonLib, line 978
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 979
    } // library marker kkossev.commonLib, line 980
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 981
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 982
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 983
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 984
        return false // library marker kkossev.commonLib, line 985
    } // library marker kkossev.commonLib, line 986
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 987
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 988
    // attribute report received // library marker kkossev.commonLib, line 989
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 990
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 991
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 992
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 993
    } // library marker kkossev.commonLib, line 994
    attrData.each { // library marker kkossev.commonLib, line 995
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 996
        //def map = [:] // library marker kkossev.commonLib, line 997
        if (it.status == '86') { // library marker kkossev.commonLib, line 998
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 999
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1000
        } // library marker kkossev.commonLib, line 1001
        switch (it.cluster) { // library marker kkossev.commonLib, line 1002
            case '0000' : // library marker kkossev.commonLib, line 1003
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1004
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1005
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1006
                } // library marker kkossev.commonLib, line 1007
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1008
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1009
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1010
                } // library marker kkossev.commonLib, line 1011
                else { // library marker kkossev.commonLib, line 1012
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1013
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1014
                } // library marker kkossev.commonLib, line 1015
                break // library marker kkossev.commonLib, line 1016
            default : // library marker kkossev.commonLib, line 1017
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1018
                break // library marker kkossev.commonLib, line 1019
        } // switch // library marker kkossev.commonLib, line 1020
    } // for each attribute // library marker kkossev.commonLib, line 1021
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1022
} // library marker kkossev.commonLib, line 1023

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1025

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1027
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1028
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1029
    def mode // library marker kkossev.commonLib, line 1030
    String attrName // library marker kkossev.commonLib, line 1031
    if (it.value == null) { // library marker kkossev.commonLib, line 1032
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1033
        return // library marker kkossev.commonLib, line 1034
    } // library marker kkossev.commonLib, line 1035
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1036
    switch (it.attrId) { // library marker kkossev.commonLib, line 1037
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1038
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1039
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1040
            break // library marker kkossev.commonLib, line 1041
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1042
            attrName = 'On Time' // library marker kkossev.commonLib, line 1043
            mode = value // library marker kkossev.commonLib, line 1044
            break // library marker kkossev.commonLib, line 1045
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1046
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1047
            mode = value // library marker kkossev.commonLib, line 1048
            break // library marker kkossev.commonLib, line 1049
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1050
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1051
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1052
            break // library marker kkossev.commonLib, line 1053
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1054
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1055
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1056
            break // library marker kkossev.commonLib, line 1057
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1058
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1059
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1060
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1061
            } // library marker kkossev.commonLib, line 1062
            else { // library marker kkossev.commonLib, line 1063
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1064
            } // library marker kkossev.commonLib, line 1065
            break // library marker kkossev.commonLib, line 1066
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1067
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1068
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1069
            break // library marker kkossev.commonLib, line 1070
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1071
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1072
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1073
            break // library marker kkossev.commonLib, line 1074
        default : // library marker kkossev.commonLib, line 1075
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1076
            return // library marker kkossev.commonLib, line 1077
    } // library marker kkossev.commonLib, line 1078
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1079
} // library marker kkossev.commonLib, line 1080

void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1082
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1083
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1084
    } // library marker kkossev.commonLib, line 1085
    else if (this.respondsTo('levelLibParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1086
        levelLibParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1087
    } // library marker kkossev.commonLib, line 1088
    else { // library marker kkossev.commonLib, line 1089
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1090
    } // library marker kkossev.commonLib, line 1091
} // library marker kkossev.commonLib, line 1092

String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1094
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1095
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1096
} // library marker kkossev.commonLib, line 1097

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1099
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1100
} // library marker kkossev.commonLib, line 1101

void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1103
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1104
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1105
    } // library marker kkossev.commonLib, line 1106
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1107
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1108
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1109
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1110
    } // library marker kkossev.commonLib, line 1111
    else { // library marker kkossev.commonLib, line 1112
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1113
    } // library marker kkossev.commonLib, line 1114
} // library marker kkossev.commonLib, line 1115

void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1117
    if (this.respondsTo('customParseIlluminanceCluster')) { customParseIlluminanceCluster(descMap) } else { logWarn "unprocessed Illuminance attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 1118
} // library marker kkossev.commonLib, line 1119

// Temperature Measurement Cluster 0x0402 // library marker kkossev.commonLib, line 1121
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1122
    if (this.respondsTo('customParseTemperatureCluster')) { // library marker kkossev.commonLib, line 1123
        customParseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 1124
    } // library marker kkossev.commonLib, line 1125
    else { // library marker kkossev.commonLib, line 1126
        logWarn "unprocessed Temperature attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1127
    } // library marker kkossev.commonLib, line 1128
} // library marker kkossev.commonLib, line 1129

// Humidity Measurement Cluster 0x0405 // library marker kkossev.commonLib, line 1131
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1132
    if (this.respondsTo('customParseHumidityCluster')) { // library marker kkossev.commonLib, line 1133
        customParseHumidityCluster(descMap) // library marker kkossev.commonLib, line 1134
    } // library marker kkossev.commonLib, line 1135
    else { // library marker kkossev.commonLib, line 1136
        logWarn "unprocessed Humidity attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1137
    } // library marker kkossev.commonLib, line 1138
} // library marker kkossev.commonLib, line 1139

// Occupancy Sensing Cluster 0x0406 // library marker kkossev.commonLib, line 1141
void parseOccupancyCluster(final Map descMap) { // library marker kkossev.commonLib, line 1142
    if (this.respondsTo('customParseOccupancyCluster')) { // library marker kkossev.commonLib, line 1143
        customParseOccupancyCluster(descMap) // library marker kkossev.commonLib, line 1144
    } // library marker kkossev.commonLib, line 1145
    else { // library marker kkossev.commonLib, line 1146
        logWarn "unprocessed Occupancy attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1147
    } // library marker kkossev.commonLib, line 1148
} // library marker kkossev.commonLib, line 1149

// Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1151
void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1152
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { logWarn 'parseElectricalMeasureCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1153
} // library marker kkossev.commonLib, line 1154

// Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1156
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1157
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { logWarn 'parseMeteringCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1158
} // library marker kkossev.commonLib, line 1159

// pm2.5 // library marker kkossev.commonLib, line 1161
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1162
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1163
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1164
    /* groovylint-disable-next-line NoFloat */ // library marker kkossev.commonLib, line 1165
    float floatValue  = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1166
    if (this.respondsTo('handlePm25Event')) { // library marker kkossev.commonLib, line 1167
        handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1168
    } // library marker kkossev.commonLib, line 1169
    else { // library marker kkossev.commonLib, line 1170
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1171
    } // library marker kkossev.commonLib, line 1172
} // library marker kkossev.commonLib, line 1173

// Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1175
void parseAnalogInputCluster(final Map descMap, String description=null) { // library marker kkossev.commonLib, line 1176
    if (this.respondsTo('customParseAnalogInputCluster')) { // library marker kkossev.commonLib, line 1177
        customParseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1178
    } // library marker kkossev.commonLib, line 1179
    else if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 1180
        customParseAnalogInputClusterDescription(description)                   // ZigUSB // library marker kkossev.commonLib, line 1181
    } // library marker kkossev.commonLib, line 1182
    else if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1183
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1184
    } // library marker kkossev.commonLib, line 1185
    else { // library marker kkossev.commonLib, line 1186
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1187
    } // library marker kkossev.commonLib, line 1188
} // library marker kkossev.commonLib, line 1189

// Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1191
void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1192
    if (this.respondsTo('customParseMultistateInputCluster')) { customParseMultistateInputCluster(descMap) } else { logWarn "parseMultistateInputCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1193
} // library marker kkossev.commonLib, line 1194

// Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1196
void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1197
    if (this.respondsTo('customParseWindowCoveringCluster')) { customParseWindowCoveringCluster(descMap) } else { logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1198
} // library marker kkossev.commonLib, line 1199

// thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1201
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1202
    if (this.respondsTo('customParseThermostatCluster')) { customParseThermostatCluster(descMap) } else { logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1203
} // library marker kkossev.commonLib, line 1204

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1206
    if (this.respondsTo('customParseFC11Cluster')) { customParseFC11Cluster(descMap) } else { logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1207
} // library marker kkossev.commonLib, line 1208

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1210
    if (this.respondsTo('customParseE002Cluster')) { customParseE002Cluster(descMap) } else { logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }    // radars // library marker kkossev.commonLib, line 1211
} // library marker kkossev.commonLib, line 1212

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1214
    if (this.respondsTo('customParseEC03Cluster')) { customParseEC03Cluster(descMap) } else { logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }   // radars // library marker kkossev.commonLib, line 1215
} // library marker kkossev.commonLib, line 1216

/* // library marker kkossev.commonLib, line 1218
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1219
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1220
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1221
*/ // library marker kkossev.commonLib, line 1222
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1223
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1224
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1225

// Tuya Commands // library marker kkossev.commonLib, line 1227
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 1228
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 1229
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 1230
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 1231
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 1232

// tuya DP type // library marker kkossev.commonLib, line 1234
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 1235
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 1236
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 1237
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 1238
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 1239
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 1240

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 1242
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 1243
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 1244
        Long offset = 0 // library marker kkossev.commonLib, line 1245
        try { // library marker kkossev.commonLib, line 1246
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 1247
        } // library marker kkossev.commonLib, line 1248
        catch (e) { // library marker kkossev.commonLib, line 1249
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 1250
        } // library marker kkossev.commonLib, line 1251
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 1252
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 1253
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 1254
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 1255
    } // library marker kkossev.commonLib, line 1256
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 1257
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 1258
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 1259
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 1260
        if (status != '00') { // library marker kkossev.commonLib, line 1261
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 1262
        } // library marker kkossev.commonLib, line 1263
    } // library marker kkossev.commonLib, line 1264
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 1265
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 1266
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 1267
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 1268
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 1269
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 1270
            return // library marker kkossev.commonLib, line 1271
        } // library marker kkossev.commonLib, line 1272
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 1273
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 1274
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 1275
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 1276
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 1277
            logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 1278
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 1279
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 1280
        } // library marker kkossev.commonLib, line 1281
    } // library marker kkossev.commonLib, line 1282
    else { // library marker kkossev.commonLib, line 1283
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 1284
    } // library marker kkossev.commonLib, line 1285
} // library marker kkossev.commonLib, line 1286

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 1288
    logTrace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 1289
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 1290
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 1291
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 1292
            return // library marker kkossev.commonLib, line 1293
        } // library marker kkossev.commonLib, line 1294
    } // library marker kkossev.commonLib, line 1295
    // check if the method  method exists // library marker kkossev.commonLib, line 1296
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 1297
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 1298
            return // library marker kkossev.commonLib, line 1299
        } // library marker kkossev.commonLib, line 1300
    } // library marker kkossev.commonLib, line 1301
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 1302
} // library marker kkossev.commonLib, line 1303

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 1305
    int retValue = 0 // library marker kkossev.commonLib, line 1306
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 1307
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 1308
        int power = 1 // library marker kkossev.commonLib, line 1309
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 1310
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 1311
            power = power * 256 // library marker kkossev.commonLib, line 1312
        } // library marker kkossev.commonLib, line 1313
    } // library marker kkossev.commonLib, line 1314
    return retValue // library marker kkossev.commonLib, line 1315
} // library marker kkossev.commonLib, line 1316

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 1318
    List<String> cmds = [] // library marker kkossev.commonLib, line 1319
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 1320
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1321
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 1322
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 1323
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 1324
    return cmds // library marker kkossev.commonLib, line 1325
} // library marker kkossev.commonLib, line 1326

private getPACKET_ID() { // library marker kkossev.commonLib, line 1328
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 1329
} // library marker kkossev.commonLib, line 1330

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1332
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 1333
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 1334
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 1335
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 1336
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 1337
} // library marker kkossev.commonLib, line 1338

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 1340
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 1341

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 1343
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 1344
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1345
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 1346
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 1347
} // library marker kkossev.commonLib, line 1348

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 1350
    List<String> cmds = [] // library marker kkossev.commonLib, line 1351
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1352
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 1353
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1354
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1355
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 1356
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 1357
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 1358
        } // library marker kkossev.commonLib, line 1359
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 1360
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 1361
    } // library marker kkossev.commonLib, line 1362
    else { // library marker kkossev.commonLib, line 1363
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 1364
    } // library marker kkossev.commonLib, line 1365
} // library marker kkossev.commonLib, line 1366

/** // library marker kkossev.commonLib, line 1368
 * initializes the device // library marker kkossev.commonLib, line 1369
 * Invoked from configure() // library marker kkossev.commonLib, line 1370
 * @return zigbee commands // library marker kkossev.commonLib, line 1371
 */ // library marker kkossev.commonLib, line 1372
List<String> initializeDevice() { // library marker kkossev.commonLib, line 1373
    List<String> cmds = [] // library marker kkossev.commonLib, line 1374
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 1375
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 1376
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 1377
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1378
    } // library marker kkossev.commonLib, line 1379
    return cmds // library marker kkossev.commonLib, line 1380
} // library marker kkossev.commonLib, line 1381

/** // library marker kkossev.commonLib, line 1383
 * configures the device // library marker kkossev.commonLib, line 1384
 * Invoked from configure() // library marker kkossev.commonLib, line 1385
 * @return zigbee commands // library marker kkossev.commonLib, line 1386
 */ // library marker kkossev.commonLib, line 1387
List<String> configureDevice() { // library marker kkossev.commonLib, line 1388
    List<String> cmds = [] // library marker kkossev.commonLib, line 1389
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 1390

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 1392
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 1393
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1394
    } // library marker kkossev.commonLib, line 1395
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 1396
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 1397
    return cmds // library marker kkossev.commonLib, line 1398
} // library marker kkossev.commonLib, line 1399

/* // library marker kkossev.commonLib, line 1401
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1402
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 1403
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1404
*/ // library marker kkossev.commonLib, line 1405

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 1407
    List<String> cmds = [] // library marker kkossev.commonLib, line 1408
    if (customHandlersList != null && customHandlersList != []) { // library marker kkossev.commonLib, line 1409
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 1410
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 1411
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 1412
                if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1413
            } // library marker kkossev.commonLib, line 1414
        } // library marker kkossev.commonLib, line 1415
    } // library marker kkossev.commonLib, line 1416
    return cmds // library marker kkossev.commonLib, line 1417
} // library marker kkossev.commonLib, line 1418

void refresh() { // library marker kkossev.commonLib, line 1420
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 1421
    checkDriverVersion() // library marker kkossev.commonLib, line 1422
    List<String> cmds = [] // library marker kkossev.commonLib, line 1423
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 1424

    List<String> customCmds = customHandlers(['batteryRefresh', 'groupsRefresh', 'customRefresh']) // library marker kkossev.commonLib, line 1426
    if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1427

    if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 1429
    else { // library marker kkossev.commonLib, line 1430
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 1431
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1432
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1433
        } // library marker kkossev.commonLib, line 1434
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 1435
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1436
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1437
        } // library marker kkossev.commonLib, line 1438
    } // library marker kkossev.commonLib, line 1439

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1441
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1442
    } // library marker kkossev.commonLib, line 1443
    else { // library marker kkossev.commonLib, line 1444
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1445
    } // library marker kkossev.commonLib, line 1446
} // library marker kkossev.commonLib, line 1447

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 1449
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1450

public void clearInfoEvent() { // library marker kkossev.commonLib, line 1452
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 1453
} // library marker kkossev.commonLib, line 1454

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 1456
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 1457
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 1458
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 1459
    } // library marker kkossev.commonLib, line 1460
    else { // library marker kkossev.commonLib, line 1461
        logInfo "${info}" // library marker kkossev.commonLib, line 1462
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 1463
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1464
    } // library marker kkossev.commonLib, line 1465
} // library marker kkossev.commonLib, line 1466

public void ping() { // library marker kkossev.commonLib, line 1468
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1469
    state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1470
    //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 1471
    state.states['isPing'] = true // library marker kkossev.commonLib, line 1472
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1473
    if (isVirtual()) { // library marker kkossev.commonLib, line 1474
        runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 1475
    } // library marker kkossev.commonLib, line 1476
    else { // library marker kkossev.commonLib, line 1477
        sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 1478
    } // library marker kkossev.commonLib, line 1479
    logDebug 'ping...' // library marker kkossev.commonLib, line 1480
} // library marker kkossev.commonLib, line 1481

def virtualPong() { // library marker kkossev.commonLib, line 1483
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1484
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1485
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1486
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1487
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1488
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1489
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1490
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1491
        sendRttEvent() // library marker kkossev.commonLib, line 1492
    } // library marker kkossev.commonLib, line 1493
    else { // library marker kkossev.commonLib, line 1494
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1495
    } // library marker kkossev.commonLib, line 1496
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1497
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1498
} // library marker kkossev.commonLib, line 1499

/** // library marker kkossev.commonLib, line 1501
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 1502
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 1503
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 1504
 * @return none // library marker kkossev.commonLib, line 1505
 */ // library marker kkossev.commonLib, line 1506
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1507
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1508
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1509
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1510
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1511
    if (value == null) { // library marker kkossev.commonLib, line 1512
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1513
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 1514
    } // library marker kkossev.commonLib, line 1515
    else { // library marker kkossev.commonLib, line 1516
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1517
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1518
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 1519
    } // library marker kkossev.commonLib, line 1520
} // library marker kkossev.commonLib, line 1521

/** // library marker kkossev.commonLib, line 1523
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 1524
 * @param cluster cluster ID // library marker kkossev.commonLib, line 1525
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 1526
 */ // library marker kkossev.commonLib, line 1527
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1528
    if (cluster != null) { // library marker kkossev.commonLib, line 1529
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1530
    } // library marker kkossev.commonLib, line 1531
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1532
    return 'NULL' // library marker kkossev.commonLib, line 1533
} // library marker kkossev.commonLib, line 1534

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1536
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1537
} // library marker kkossev.commonLib, line 1538

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1540
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1541
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1542
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1543
} // library marker kkossev.commonLib, line 1544

/** // library marker kkossev.commonLib, line 1546
 * Schedule a device health check // library marker kkossev.commonLib, line 1547
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 1548
 */ // library marker kkossev.commonLib, line 1549
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1550
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1551
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1552
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1553
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1554
    } // library marker kkossev.commonLib, line 1555
    else { // library marker kkossev.commonLib, line 1556
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1557
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1558
    } // library marker kkossev.commonLib, line 1559
} // library marker kkossev.commonLib, line 1560

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1562
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1563
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1564
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1565
} // library marker kkossev.commonLib, line 1566

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1568
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 1569
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1570
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1571
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1572
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1573
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1574
    } // library marker kkossev.commonLib, line 1575
} // library marker kkossev.commonLib, line 1576

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1578
    checkDriverVersion() // library marker kkossev.commonLib, line 1579
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1580
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1581
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1582
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1583
            logWarn 'not present!' // library marker kkossev.commonLib, line 1584
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1585
        } // library marker kkossev.commonLib, line 1586
    } // library marker kkossev.commonLib, line 1587
    else { // library marker kkossev.commonLib, line 1588
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1589
    } // library marker kkossev.commonLib, line 1590
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1591
} // library marker kkossev.commonLib, line 1592

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1594
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1595
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 1596
    if (value == 'online') { // library marker kkossev.commonLib, line 1597
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1598
    } // library marker kkossev.commonLib, line 1599
    else { // library marker kkossev.commonLib, line 1600
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1601
    } // library marker kkossev.commonLib, line 1602
} // library marker kkossev.commonLib, line 1603

/** // library marker kkossev.commonLib, line 1605
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 1606
 */ // library marker kkossev.commonLib, line 1607
void autoPoll() { // library marker kkossev.commonLib, line 1608
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 1609
    checkDriverVersion() // library marker kkossev.commonLib, line 1610
    List<String> cmds = [] // library marker kkossev.commonLib, line 1611
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 1612
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 1613
    } // library marker kkossev.commonLib, line 1614

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1616
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1617
    } // library marker kkossev.commonLib, line 1618
} // library marker kkossev.commonLib, line 1619

/** // library marker kkossev.commonLib, line 1621
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1622
 */ // library marker kkossev.commonLib, line 1623
void updated() { // library marker kkossev.commonLib, line 1624
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1625
    checkDriverVersion() // library marker kkossev.commonLib, line 1626
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1627
    unschedule() // library marker kkossev.commonLib, line 1628

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1630
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1631
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1632
    } // library marker kkossev.commonLib, line 1633
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1634
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1635
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1636
    } // library marker kkossev.commonLib, line 1637

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1639
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1640
        // schedule the periodic timer // library marker kkossev.commonLib, line 1641
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1642
        if (interval > 0) { // library marker kkossev.commonLib, line 1643
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1644
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1645
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1646
        } // library marker kkossev.commonLib, line 1647
    } // library marker kkossev.commonLib, line 1648
    else { // library marker kkossev.commonLib, line 1649
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1650
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1651
    } // library marker kkossev.commonLib, line 1652
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1653
        customUpdated() // library marker kkossev.commonLib, line 1654
    } // library marker kkossev.commonLib, line 1655

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1657
} // library marker kkossev.commonLib, line 1658

/** // library marker kkossev.commonLib, line 1660
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 1661
 */ // library marker kkossev.commonLib, line 1662
void logsOff() { // library marker kkossev.commonLib, line 1663
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1664
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1665
} // library marker kkossev.commonLib, line 1666
void traceOff() { // library marker kkossev.commonLib, line 1667
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1668
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1669
} // library marker kkossev.commonLib, line 1670

void configure(String command) { // library marker kkossev.commonLib, line 1672
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1673
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1674
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1675
        return // library marker kkossev.commonLib, line 1676
    } // library marker kkossev.commonLib, line 1677
    // // library marker kkossev.commonLib, line 1678
    String func // library marker kkossev.commonLib, line 1679
    try { // library marker kkossev.commonLib, line 1680
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1681
        "$func"() // library marker kkossev.commonLib, line 1682
    } // library marker kkossev.commonLib, line 1683
    catch (e) { // library marker kkossev.commonLib, line 1684
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1685
        return // library marker kkossev.commonLib, line 1686
    } // library marker kkossev.commonLib, line 1687
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1688
} // library marker kkossev.commonLib, line 1689

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1691
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1692
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1693
} // library marker kkossev.commonLib, line 1694

void loadAllDefaults() { // library marker kkossev.commonLib, line 1696
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1697
    deleteAllSettings() // library marker kkossev.commonLib, line 1698
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1699
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1700
    deleteAllStates() // library marker kkossev.commonLib, line 1701
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1702
    initialize() // library marker kkossev.commonLib, line 1703
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1704
    updated() // library marker kkossev.commonLib, line 1705
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1706
} // library marker kkossev.commonLib, line 1707

void configureNow() { // library marker kkossev.commonLib, line 1709
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 1710
} // library marker kkossev.commonLib, line 1711

/** // library marker kkossev.commonLib, line 1713
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1714
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1715
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1716
 */ // library marker kkossev.commonLib, line 1717
List<String> configure() { // library marker kkossev.commonLib, line 1718
    List<String> cmds = [] // library marker kkossev.commonLib, line 1719
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1720
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1721
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1722
    if (isTuya()) { // library marker kkossev.commonLib, line 1723
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1724
    } // library marker kkossev.commonLib, line 1725
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1726
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1727
    } // library marker kkossev.commonLib, line 1728
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1729
    if (initCmds != null && initCmds != [] ) { cmds += initCmds } // library marker kkossev.commonLib, line 1730
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1731
    if (cfgCmds != null && cfgCmds != [] ) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1732
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1733
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1734
    logDebug "configure(): returning cmds = ${cmds}" // library marker kkossev.commonLib, line 1735
    //return cmds // library marker kkossev.commonLib, line 1736
    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1737
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1738
    } // library marker kkossev.commonLib, line 1739
    else { // library marker kkossev.commonLib, line 1740
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1741
    } // library marker kkossev.commonLib, line 1742
} // library marker kkossev.commonLib, line 1743

/** // library marker kkossev.commonLib, line 1745
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 1746
 */ // library marker kkossev.commonLib, line 1747
void installed() { // library marker kkossev.commonLib, line 1748
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1749
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1750
    // populate some default values for attributes // library marker kkossev.commonLib, line 1751
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 1752
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 1753
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1754
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1755
} // library marker kkossev.commonLib, line 1756

/** // library marker kkossev.commonLib, line 1758
 * Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1759
 */ // library marker kkossev.commonLib, line 1760
void initialize() { // library marker kkossev.commonLib, line 1761
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1762
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1763
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1764
    updateTuyaVersion() // library marker kkossev.commonLib, line 1765
    updateAqaraVersion() // library marker kkossev.commonLib, line 1766
} // library marker kkossev.commonLib, line 1767

/* // library marker kkossev.commonLib, line 1769
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1770
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1771
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1772
*/ // library marker kkossev.commonLib, line 1773

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1775
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1776
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1777
} // library marker kkossev.commonLib, line 1778

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1780
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1781
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1782
} // library marker kkossev.commonLib, line 1783

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1785
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1786
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1787
} // library marker kkossev.commonLib, line 1788

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1790
    if (cmd == null || cmd == [] || cmd == 'null') { // library marker kkossev.commonLib, line 1791
        logWarn 'sendZigbeeCommands: no commands to send!' // library marker kkossev.commonLib, line 1792
        return // library marker kkossev.commonLib, line 1793
    } // library marker kkossev.commonLib, line 1794
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 1795
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1796
    cmd.each { // library marker kkossev.commonLib, line 1797
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1798
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1799
    } // library marker kkossev.commonLib, line 1800
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1801
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1802
} // library marker kkossev.commonLib, line 1803

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1805

String getDeviceInfo() { // library marker kkossev.commonLib, line 1807
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1808
} // library marker kkossev.commonLib, line 1809

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1811
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1812
} // library marker kkossev.commonLib, line 1813

void checkDriverVersion() { // library marker kkossev.commonLib, line 1815
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1816
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1817
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1818
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1819
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 1820
        updateTuyaVersion() // library marker kkossev.commonLib, line 1821
        updateAqaraVersion() // library marker kkossev.commonLib, line 1822
    } // library marker kkossev.commonLib, line 1823
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1824
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1825
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1826
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1827
} // library marker kkossev.commonLib, line 1828

// credits @thebearmay // library marker kkossev.commonLib, line 1830
String getModel() { // library marker kkossev.commonLib, line 1831
    try { // library marker kkossev.commonLib, line 1832
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1833
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1834
    } catch (ignore) { // library marker kkossev.commonLib, line 1835
        try { // library marker kkossev.commonLib, line 1836
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1837
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1838
                return model // library marker kkossev.commonLib, line 1839
            } // library marker kkossev.commonLib, line 1840
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1841
            return '' // library marker kkossev.commonLib, line 1842
        } // library marker kkossev.commonLib, line 1843
    } // library marker kkossev.commonLib, line 1844
} // library marker kkossev.commonLib, line 1845

// credits @thebearmay // library marker kkossev.commonLib, line 1847
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1848
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1849
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1850
    String revision = tokens.last() // library marker kkossev.commonLib, line 1851
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1852
} // library marker kkossev.commonLib, line 1853

/** // library marker kkossev.commonLib, line 1855
 * called from TODO // library marker kkossev.commonLib, line 1856
 */ // library marker kkossev.commonLib, line 1857

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1859
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1860
    unschedule() // library marker kkossev.commonLib, line 1861
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1862
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1863

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1865
} // library marker kkossev.commonLib, line 1866

void resetStatistics() { // library marker kkossev.commonLib, line 1868
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1869
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1870
} // library marker kkossev.commonLib, line 1871

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1873
void resetStats() { // library marker kkossev.commonLib, line 1874
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1875
    state.stats = [:] // library marker kkossev.commonLib, line 1876
    state.states = [:] // library marker kkossev.commonLib, line 1877
    state.lastRx = [:] // library marker kkossev.commonLib, line 1878
    state.lastTx = [:] // library marker kkossev.commonLib, line 1879
    state.health = [:] // library marker kkossev.commonLib, line 1880
    if (this.respondsTo('groupsLibVersion')) { // library marker kkossev.commonLib, line 1881
        state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 1882
    } // library marker kkossev.commonLib, line 1883
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 1884
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1885
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 1886
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 1887
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 1888
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1889
} // library marker kkossev.commonLib, line 1890

/** // library marker kkossev.commonLib, line 1892
 * called from TODO // library marker kkossev.commonLib, line 1893
 */ // library marker kkossev.commonLib, line 1894
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1895
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1896
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1897
        state.clear() // library marker kkossev.commonLib, line 1898
        unschedule() // library marker kkossev.commonLib, line 1899
        resetStats() // library marker kkossev.commonLib, line 1900
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 1901
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1902
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1903
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1904
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1905
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1906
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1907
    } // library marker kkossev.commonLib, line 1908

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1910
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1911
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1912
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1913
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1914

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1916
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1917
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1918
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 1919
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1920
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1921
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1922
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1923
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1924
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 1925

    // common libraries initialization // library marker kkossev.commonLib, line 1927
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1928
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1929

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1931
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1932
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1933
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1934
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 1935

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1937
    if ( mm != null) { // library marker kkossev.commonLib, line 1938
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 1939
    } // library marker kkossev.commonLib, line 1940
    else { // library marker kkossev.commonLib, line 1941
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 1942
    } // library marker kkossev.commonLib, line 1943
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1944
    if ( ep  != null) { // library marker kkossev.commonLib, line 1945
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1946
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1947
    } // library marker kkossev.commonLib, line 1948
    else { // library marker kkossev.commonLib, line 1949
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1950
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1951
    } // library marker kkossev.commonLib, line 1952
} // library marker kkossev.commonLib, line 1953

/** // library marker kkossev.commonLib, line 1955
 * called from TODO // library marker kkossev.commonLib, line 1956
 */ // library marker kkossev.commonLib, line 1957
void setDestinationEP() { // library marker kkossev.commonLib, line 1958
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1959
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1960
        state.destinationEP = ep // library marker kkossev.commonLib, line 1961
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1962
    } // library marker kkossev.commonLib, line 1963
    else { // library marker kkossev.commonLib, line 1964
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1965
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 1966
    } // library marker kkossev.commonLib, line 1967
} // library marker kkossev.commonLib, line 1968

void logDebug(final String msg) { // library marker kkossev.commonLib, line 1970
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 1971
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 1972
    } // library marker kkossev.commonLib, line 1973
} // library marker kkossev.commonLib, line 1974

void logInfo(final String msg) { // library marker kkossev.commonLib, line 1976
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 1977
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 1978
    } // library marker kkossev.commonLib, line 1979
} // library marker kkossev.commonLib, line 1980

void logWarn(final String msg) { // library marker kkossev.commonLib, line 1982
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 1983
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 1984
    } // library marker kkossev.commonLib, line 1985
} // library marker kkossev.commonLib, line 1986

void logTrace(final String msg) { // library marker kkossev.commonLib, line 1988
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 1989
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 1990
    } // library marker kkossev.commonLib, line 1991
} // library marker kkossev.commonLib, line 1992

// _DEBUG mode only // library marker kkossev.commonLib, line 1994
void getAllProperties() { // library marker kkossev.commonLib, line 1995
    log.trace 'Properties:' // library marker kkossev.commonLib, line 1996
    device.properties.each { it -> // library marker kkossev.commonLib, line 1997
        log.debug it // library marker kkossev.commonLib, line 1998
    } // library marker kkossev.commonLib, line 1999
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2000
    settings.each { it -> // library marker kkossev.commonLib, line 2001
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2002
    } // library marker kkossev.commonLib, line 2003
    log.trace 'Done' // library marker kkossev.commonLib, line 2004
} // library marker kkossev.commonLib, line 2005

// delete all Preferences // library marker kkossev.commonLib, line 2007
void deleteAllSettings() { // library marker kkossev.commonLib, line 2008
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 2009
    settings.each { it -> // library marker kkossev.commonLib, line 2010
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 2011
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2012
    } // library marker kkossev.commonLib, line 2013
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 2014
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2015
} // library marker kkossev.commonLib, line 2016

// delete all attributes // library marker kkossev.commonLib, line 2018
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2019
    String attributesDeleted = '' // library marker kkossev.commonLib, line 2020
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 2021
        attributesDeleted += "${it}, " // library marker kkossev.commonLib, line 2022
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2023
    } // library marker kkossev.commonLib, line 2024
    logDebug "Deleted attributes: ${attributesDeleted}" // library marker kkossev.commonLib, line 2025
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2026
} // library marker kkossev.commonLib, line 2027

// delete all State Variables // library marker kkossev.commonLib, line 2029
void deleteAllStates() { // library marker kkossev.commonLib, line 2030
    state.each { it -> // library marker kkossev.commonLib, line 2031
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2032
    } // library marker kkossev.commonLib, line 2033
    state.clear() // library marker kkossev.commonLib, line 2034
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2035
} // library marker kkossev.commonLib, line 2036

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2038
    unschedule() // library marker kkossev.commonLib, line 2039
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2040
} // library marker kkossev.commonLib, line 2041

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2043
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 2044
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 2045
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 2046
    } // library marker kkossev.commonLib, line 2047
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 2048
} // library marker kkossev.commonLib, line 2049

void parseTest(String par) { // library marker kkossev.commonLib, line 2051
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2052
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2053
    parse(par) // library marker kkossev.commonLib, line 2054
} // library marker kkossev.commonLib, line 2055

def testJob() { // library marker kkossev.commonLib, line 2057
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2058
} // library marker kkossev.commonLib, line 2059

/** // library marker kkossev.commonLib, line 2061
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2062
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2063
 */ // library marker kkossev.commonLib, line 2064
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2065
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2066
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2067
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2068
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2069
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2070
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2071
    String cron // library marker kkossev.commonLib, line 2072
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2073
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2074
    } // library marker kkossev.commonLib, line 2075
    else { // library marker kkossev.commonLib, line 2076
        if (minutes < 60) { // library marker kkossev.commonLib, line 2077
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2078
        } // library marker kkossev.commonLib, line 2079
        else { // library marker kkossev.commonLib, line 2080
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2081
        } // library marker kkossev.commonLib, line 2082
    } // library marker kkossev.commonLib, line 2083
    return cron // library marker kkossev.commonLib, line 2084
} // library marker kkossev.commonLib, line 2085

// credits @thebearmay // library marker kkossev.commonLib, line 2087
String formatUptime() { // library marker kkossev.commonLib, line 2088
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2089
} // library marker kkossev.commonLib, line 2090

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2092
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2093
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2094
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2095
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2096
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2097
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2098
} // library marker kkossev.commonLib, line 2099

boolean isTuya() { // library marker kkossev.commonLib, line 2101
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 2102
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2103
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2104
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2105
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2106
} // library marker kkossev.commonLib, line 2107

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2109
    if (!isTuya()) { // library marker kkossev.commonLib, line 2110
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2111
        return // library marker kkossev.commonLib, line 2112
    } // library marker kkossev.commonLib, line 2113
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2114
    if (application != null) { // library marker kkossev.commonLib, line 2115
        Integer ver // library marker kkossev.commonLib, line 2116
        try { // library marker kkossev.commonLib, line 2117
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2118
        } // library marker kkossev.commonLib, line 2119
        catch (e) { // library marker kkossev.commonLib, line 2120
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2121
            return // library marker kkossev.commonLib, line 2122
        } // library marker kkossev.commonLib, line 2123
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2124
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2125
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2126
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2127
        } // library marker kkossev.commonLib, line 2128
    } // library marker kkossev.commonLib, line 2129
} // library marker kkossev.commonLib, line 2130

boolean isAqara() { // library marker kkossev.commonLib, line 2132
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2133
} // library marker kkossev.commonLib, line 2134

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2136
    if (!isAqara()) { // library marker kkossev.commonLib, line 2137
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2138
        return // library marker kkossev.commonLib, line 2139
    } // library marker kkossev.commonLib, line 2140
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2141
    if (application != null) { // library marker kkossev.commonLib, line 2142
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2143
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2144
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2145
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2146
        } // library marker kkossev.commonLib, line 2147
    } // library marker kkossev.commonLib, line 2148
} // library marker kkossev.commonLib, line 2149

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2151
    try { // library marker kkossev.commonLib, line 2152
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2153
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2154
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2155
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2156
    } catch (e) { // library marker kkossev.commonLib, line 2157
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2158
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2159
    } // library marker kkossev.commonLib, line 2160
} // library marker kkossev.commonLib, line 2161

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2163
    try { // library marker kkossev.commonLib, line 2164
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2165
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2166
        return date.getTime() // library marker kkossev.commonLib, line 2167
    } catch (e) { // library marker kkossev.commonLib, line 2168
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2169
        return now() // library marker kkossev.commonLib, line 2170
    } // library marker kkossev.commonLib, line 2171
} // library marker kkossev.commonLib, line 2172
/* // library marker kkossev.commonLib, line 2173
void test(String par) { // library marker kkossev.commonLib, line 2174
    List<String> cmds = [] // library marker kkossev.commonLib, line 2175
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2176

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2178
    //parse(par) // library marker kkossev.commonLib, line 2179

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2181
} // library marker kkossev.commonLib, line 2182
*/ // library marker kkossev.commonLib, line 2183

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
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.temperatureLib, line 1
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

void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.temperatureLib, line 98
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.temperatureLib, line 99
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.temperatureLib, line 100
    sendEvent(eventMap) // library marker kkossev.temperatureLib, line 101
} // library marker kkossev.temperatureLib, line 102

List<String> temperatureLibInitializeDevice() { // library marker kkossev.temperatureLib, line 104
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 105
    cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.temperatureLib, line 106
    return cmds // library marker kkossev.temperatureLib, line 107
} // library marker kkossev.temperatureLib, line 108

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
 * ver. 3.1.1  2024-04-16 kkossev  - (dev. branch) deviceProfilesV3 bug fix; tuyaDPs list of maps bug fix; // library marker kkossev.deviceProfileLib, line 31
 * // library marker kkossev.deviceProfileLib, line 32
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 33
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 34
 * // library marker kkossev.deviceProfileLib, line 35
*/ // library marker kkossev.deviceProfileLib, line 36

static String deviceProfileLibVersion()   { '3.1.1' } // library marker kkossev.deviceProfileLib, line 38
static String deviceProfileLibStamp() { '2024/04/18 10:18 AM' } // library marker kkossev.deviceProfileLib, line 39
import groovy.json.* // library marker kkossev.deviceProfileLib, line 40
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 41
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 42
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 43
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 44

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 46

metadata { // library marker kkossev.deviceProfileLib, line 48
    // no capabilities // library marker kkossev.deviceProfileLib, line 49
    // no attributes // library marker kkossev.deviceProfileLib, line 50
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 51
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 52
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 53
    ] // library marker kkossev.deviceProfileLib, line 54
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 55
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 56
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 57
    ] // library marker kkossev.deviceProfileLib, line 58

    preferences { // library marker kkossev.deviceProfileLib, line 60
        // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 61
        if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 62
            (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 63
                if (inputIt(key) != null) { // library marker kkossev.deviceProfileLib, line 64
                    input inputIt(key) // library marker kkossev.deviceProfileLib, line 65
                } // library marker kkossev.deviceProfileLib, line 66
            } // library marker kkossev.deviceProfileLib, line 67
            if (('motionReset' in DEVICE?.preferences) && (DEVICE?.preferences.motionReset == true)) { // library marker kkossev.deviceProfileLib, line 68
                input(name: 'motionReset', type: 'bool', title: '<b>Reset Motion to Inactive</b>', description: '<i>Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b></i>', defaultValue: false) // library marker kkossev.deviceProfileLib, line 69
                if (motionReset.value == true) { // library marker kkossev.deviceProfileLib, line 70
                    input('motionResetTimer', 'number', title: '<b>Motion Reset Timer</b>', description: '<i>After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds</i>', range: '0..7200', defaultValue: 60) // library marker kkossev.deviceProfileLib, line 71
                } // library marker kkossev.deviceProfileLib, line 72
            } // library marker kkossev.deviceProfileLib, line 73
        } // library marker kkossev.deviceProfileLib, line 74
        if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 75
            input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: '<i>Forcely change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!</i>',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 76
        } // library marker kkossev.deviceProfileLib, line 77
    } // library marker kkossev.deviceProfileLib, line 78
} // library marker kkossev.deviceProfileLib, line 79

boolean is2in1() { return getDeviceProfile().contains('TS0601_2IN1') } // library marker kkossev.deviceProfileLib, line 81

String  getDeviceProfile()       { state.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 83
Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2[getDeviceProfile()] } // library marker kkossev.deviceProfileLib, line 84
Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2?.keySet() } // library marker kkossev.deviceProfileLib, line 85
List<String> getDeviceProfilesMap()   { deviceProfilesV3 != null ? deviceProfilesV3.values().description as List<String> : deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 86

// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 88

/** // library marker kkossev.deviceProfileLib, line 90
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 91
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 92
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 93
 */ // library marker kkossev.deviceProfileLib, line 94
String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 95
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 96
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 97
    else { return null } // library marker kkossev.deviceProfileLib, line 98
} // library marker kkossev.deviceProfileLib, line 99

/** // library marker kkossev.deviceProfileLib, line 101
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 102
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 103
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 104
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 105
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 106
 */ // library marker kkossev.deviceProfileLib, line 107
Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 108
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 109
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 110
        if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 111
        return [:] // library marker kkossev.deviceProfileLib, line 112
    } // library marker kkossev.deviceProfileLib, line 113
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 114
    def preference // library marker kkossev.deviceProfileLib, line 115
    try { // library marker kkossev.deviceProfileLib, line 116
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 117
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 118
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 119
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 120
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 121
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 122
        } // library marker kkossev.deviceProfileLib, line 123
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 124
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 125
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 126
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 127
        } // library marker kkossev.deviceProfileLib, line 128
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 129
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 130
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 131
        } // library marker kkossev.deviceProfileLib, line 132
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 133
    } catch (e) { // library marker kkossev.deviceProfileLib, line 134
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 135
        return [:] // library marker kkossev.deviceProfileLib, line 136
    } // library marker kkossev.deviceProfileLib, line 137
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 138
    return foundMap // library marker kkossev.deviceProfileLib, line 139
} // library marker kkossev.deviceProfileLib, line 140

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 142
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 143
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 144
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 145
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 146
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 147
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 148
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 149
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 150
            return foundMap // library marker kkossev.deviceProfileLib, line 151
        } // library marker kkossev.deviceProfileLib, line 152
    } // library marker kkossev.deviceProfileLib, line 153
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 154
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 155
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 156
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 157
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 158
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 159
            return foundMap // library marker kkossev.deviceProfileLib, line 160
        } // library marker kkossev.deviceProfileLib, line 161
    } // library marker kkossev.deviceProfileLib, line 162
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 163
    return [:] // library marker kkossev.deviceProfileLib, line 164
} // library marker kkossev.deviceProfileLib, line 165

/** // library marker kkossev.deviceProfileLib, line 167
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 168
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 169
 */ // library marker kkossev.deviceProfileLib, line 170
void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 171
    logTrace "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 172
    Map preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 173
    if (preferences == null || preferences.isEmpty()) { // library marker kkossev.deviceProfileLib, line 174
        logDebug 'Preferences not found!' // library marker kkossev.deviceProfileLib, line 175
        return // library marker kkossev.deviceProfileLib, line 176
    } // library marker kkossev.deviceProfileLib, line 177
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 178
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 179
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 180
        // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 181
        if (mapValue in [true, false]) { // library marker kkossev.deviceProfileLib, line 182
            logDebug "Preference ${parName} is predefined -> (${mapValue})" // library marker kkossev.deviceProfileLib, line 183
            // TODO - set the predefined value // library marker kkossev.deviceProfileLib, line 184
            /* // library marker kkossev.deviceProfileLib, line 185
            if (debug) log.info "par ${parName} defVal = ${parMap.defVal}" // library marker kkossev.deviceProfileLib, line 186
            device.updateSetting("${parMap.name}",[value:parMap.defVal, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 187
            */ // library marker kkossev.deviceProfileLib, line 188
            return // continue // library marker kkossev.deviceProfileLib, line 189
        } // library marker kkossev.deviceProfileLib, line 190
        // find the individual preference map // library marker kkossev.deviceProfileLib, line 191
        parMap = getPreferencesMapByName(parName, false) // library marker kkossev.deviceProfileLib, line 192
        if (parMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 193
            logDebug "Preference ${parName} not found in tuyaDPs or attributes map!" // library marker kkossev.deviceProfileLib, line 194
            return // continue // library marker kkossev.deviceProfileLib, line 195
        } // library marker kkossev.deviceProfileLib, line 196
        // parMap = [at:0xE002:0xE005, name:staticDetectionSensitivity, type:number, dt:UINT8, rw:rw, min:0, max:5, scale:1, unit:x, title:Static Detection Sensitivity, description:Static detection sensitivity] // library marker kkossev.deviceProfileLib, line 197
        if (parMap.defVal == null) { // library marker kkossev.deviceProfileLib, line 198
            logDebug "no default value for preference ${parName} !" // library marker kkossev.deviceProfileLib, line 199
            return // continue // library marker kkossev.deviceProfileLib, line 200
        } // library marker kkossev.deviceProfileLib, line 201
        if (debug) { log.info "par ${parName} defVal = ${parMap.defVal}" } // library marker kkossev.deviceProfileLib, line 202
        device.updateSetting("${parMap.name}", [value:parMap.defVal, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 203
    } // library marker kkossev.deviceProfileLib, line 204
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 205
} // library marker kkossev.deviceProfileLib, line 206

/** // library marker kkossev.deviceProfileLib, line 208
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 209
 * // library marker kkossev.deviceProfileLib, line 210
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 211
 */ // library marker kkossev.deviceProfileLib, line 212
List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 213
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 214
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 215
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 216
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 217
    } // library marker kkossev.deviceProfileLib, line 218
    return validPars // library marker kkossev.deviceProfileLib, line 219
} // library marker kkossev.deviceProfileLib, line 220

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 222
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 223
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 224
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 225
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 226
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
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 266
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 267
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 268
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 269
        return // library marker kkossev.deviceProfileLib, line 270
    } // library marker kkossev.deviceProfileLib, line 271
    //Integer dpInt = 0 // library marker kkossev.deviceProfileLib, line 272
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 273
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 274
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 275
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 276
        Map foundMap // library marker kkossev.deviceProfileLib, line 277
        foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 278
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 279

        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 281
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 282
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 283
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 284
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 285
                // scale the value // library marker kkossev.deviceProfileLib, line 286
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 287
            } // library marker kkossev.deviceProfileLib, line 288
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLib, line 289
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLib, line 290
            } // library marker kkossev.deviceProfileLib, line 291
            else { // library marker kkossev.deviceProfileLib, line 292
                logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" // library marker kkossev.deviceProfileLib, line 293
                return // library marker kkossev.deviceProfileLib, line 294
            } // library marker kkossev.deviceProfileLib, line 295
        } // library marker kkossev.deviceProfileLib, line 296
        else { // library marker kkossev.deviceProfileLib, line 297
            logDebug "warning: couldn't find map for preference ${name}" // library marker kkossev.deviceProfileLib, line 298
            return // library marker kkossev.deviceProfileLib, line 299
        } // library marker kkossev.deviceProfileLib, line 300
    } // library marker kkossev.deviceProfileLib, line 301
    return // library marker kkossev.deviceProfileLib, line 302
} // library marker kkossev.deviceProfileLib, line 303

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 305
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 306
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 307
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 308
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 309
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 310
} // library marker kkossev.deviceProfileLib, line 311
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 312
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 313
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 314
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 315
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 316
} // library marker kkossev.deviceProfileLib, line 317

/** // library marker kkossev.deviceProfileLib, line 319
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 320
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 321
 * // library marker kkossev.deviceProfileLib, line 322
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 323
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 324
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 325
 */ // library marker kkossev.deviceProfileLib, line 326
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 327
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 328
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 329
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 330
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 331
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 332
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
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 367
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 368
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 369
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 370
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 371
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 372
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 373
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 374
            } // library marker kkossev.deviceProfileLib, line 375
            else { // library marker kkossev.deviceProfileLib, line 376
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 377
            } // library marker kkossev.deviceProfileLib, line 378
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 379
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 380
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 381
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 382
                // find the key for the value // library marker kkossev.deviceProfileLib, line 383
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 384
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 385
                if (key == null) { // library marker kkossev.deviceProfileLib, line 386
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 387
                    return null // library marker kkossev.deviceProfileLib, line 388
                } // library marker kkossev.deviceProfileLib, line 389
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 390
            //return null // library marker kkossev.deviceProfileLib, line 391
            } // library marker kkossev.deviceProfileLib, line 392
            break // library marker kkossev.deviceProfileLib, line 393
        default : // library marker kkossev.deviceProfileLib, line 394
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 395
            return null // library marker kkossev.deviceProfileLib, line 396
    } // library marker kkossev.deviceProfileLib, line 397
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 398
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 399
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 400
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 401
        return null // library marker kkossev.deviceProfileLib, line 402
    } // library marker kkossev.deviceProfileLib, line 403
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 404
    return scaledValue // library marker kkossev.deviceProfileLib, line 405
} // library marker kkossev.deviceProfileLib, line 406

/** // library marker kkossev.deviceProfileLib, line 408
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 409
 * // library marker kkossev.deviceProfileLib, line 410
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 411
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 412
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 413
 */ // library marker kkossev.deviceProfileLib, line 414
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 415
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 416
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 417
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 418
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 419
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 420
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 421
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 422
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 423
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 424
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 425
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 426
    if (scaledValue == null) { logInfo "setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 427
    /* // library marker kkossev.deviceProfileLib, line 428
    // update the device setting // TODO: decide whether the setting must be updated here, or after it is echeod back from the device // library marker kkossev.deviceProfileLib, line 429
    try { // library marker kkossev.deviceProfileLib, line 430
        device.updateSetting("$par", [value:val, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 431
    } // library marker kkossev.deviceProfileLib, line 432
    catch (e) { // library marker kkossev.deviceProfileLib, line 433
        logWarn "setPar: Exception '${e}'caught while updateSetting <b>$par</b>(<b>$val</b>) type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 434
        return false // library marker kkossev.deviceProfileLib, line 435
    } // library marker kkossev.deviceProfileLib, line 436
    */ // library marker kkossev.deviceProfileLib, line 437
    //logDebug "setPar: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 438
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 439
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 440
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 441
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 442
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 443
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 444
        try { // library marker kkossev.deviceProfileLib, line 445
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 446
        } // library marker kkossev.deviceProfileLib, line 447
        catch (e) { // library marker kkossev.deviceProfileLib, line 448
            logWarn "setPar: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 449
            return false // library marker kkossev.deviceProfileLib, line 450
        } // library marker kkossev.deviceProfileLib, line 451
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 452
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 453
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 454
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 455
            return false // library marker kkossev.deviceProfileLib, line 456
        } // library marker kkossev.deviceProfileLib, line 457
        else { // library marker kkossev.deviceProfileLib, line 458
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 459
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 460
        } // library marker kkossev.deviceProfileLib, line 461
    } // library marker kkossev.deviceProfileLib, line 462
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 463
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 464
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 465
        def valMiscType // library marker kkossev.deviceProfileLib, line 466
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 467
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 468
            // find the key for the value // library marker kkossev.deviceProfileLib, line 469
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 470
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 471
            if (key == null) { // library marker kkossev.deviceProfileLib, line 472
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 473
                return false // library marker kkossev.deviceProfileLib, line 474
            } // library marker kkossev.deviceProfileLib, line 475
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 476
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 477
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 478
        } // library marker kkossev.deviceProfileLib, line 479
        else { // library marker kkossev.deviceProfileLib, line 480
            valMiscType = val // library marker kkossev.deviceProfileLib, line 481
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 482
        } // library marker kkossev.deviceProfileLib, line 483
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 484
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 485
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 486
        return true // library marker kkossev.deviceProfileLib, line 487
    } // library marker kkossev.deviceProfileLib, line 488

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 490
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 491

    try { // library marker kkossev.deviceProfileLib, line 493
        // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 494
        /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 495
        isTuyaDP = dpMap.dp instanceof Number // library marker kkossev.deviceProfileLib, line 496
    } // library marker kkossev.deviceProfileLib, line 497
    catch (e) { // library marker kkossev.deviceProfileLib, line 498
        logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" // library marker kkossev.deviceProfileLib, line 499
        isTuyaDP = false // library marker kkossev.deviceProfileLib, line 500
    //return false // library marker kkossev.deviceProfileLib, line 501
    } // library marker kkossev.deviceProfileLib, line 502
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 503
        // Tuya DP // library marker kkossev.deviceProfileLib, line 504
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 505
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 506
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 507
            return false // library marker kkossev.deviceProfileLib, line 508
        } // library marker kkossev.deviceProfileLib, line 509
        else { // library marker kkossev.deviceProfileLib, line 510
            logInfo "setPar: (2) successfluly executed setPar <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 511
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 512
            return false // library marker kkossev.deviceProfileLib, line 513
        } // library marker kkossev.deviceProfileLib, line 514
    } // library marker kkossev.deviceProfileLib, line 515
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 516
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 517
        int cluster // library marker kkossev.deviceProfileLib, line 518
        int attribute // library marker kkossev.deviceProfileLib, line 519
        int dt // library marker kkossev.deviceProfileLib, line 520
        String mfgCode // library marker kkossev.deviceProfileLib, line 521
        try { // library marker kkossev.deviceProfileLib, line 522
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[0]) // library marker kkossev.deviceProfileLib, line 523
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 524
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[1]) // library marker kkossev.deviceProfileLib, line 525
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 526
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 527
            //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 528
            mfgCode = dpMap.mfgCode // library marker kkossev.deviceProfileLib, line 529
        //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 530
        } // library marker kkossev.deviceProfileLib, line 531
        catch (e) { // library marker kkossev.deviceProfileLib, line 532
            logWarn "setPar: Exception '${e}' caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 533
            return false // library marker kkossev.deviceProfileLib, line 534
        } // library marker kkossev.deviceProfileLib, line 535
        Map mapMfCode = ['mfgCode':mfgCode] // library marker kkossev.deviceProfileLib, line 536
        logDebug "setPar: found cluster=0x${zigbee.convertToHexString(cluster, 2)} attribute=0x${zigbee.convertToHexString(attribute, 2)} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 537
        if (mfgCode != null) { // library marker kkossev.deviceProfileLib, line 538
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay = 200) // library marker kkossev.deviceProfileLib, line 539
        } // library marker kkossev.deviceProfileLib, line 540
        else { // library marker kkossev.deviceProfileLib, line 541
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 542
        } // library marker kkossev.deviceProfileLib, line 543
    } // library marker kkossev.deviceProfileLib, line 544
    else { // library marker kkossev.deviceProfileLib, line 545
        logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 546
        return false // library marker kkossev.deviceProfileLib, line 547
    } // library marker kkossev.deviceProfileLib, line 548
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 549
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 550
    return true // library marker kkossev.deviceProfileLib, line 551
} // library marker kkossev.deviceProfileLib, line 552

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 554
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 555
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 556
List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 557
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 558
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 559
    if (dpMap == null) { // library marker kkossev.deviceProfileLib, line 560
        logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 561
        return [] // library marker kkossev.deviceProfileLib, line 562
    } // library marker kkossev.deviceProfileLib, line 563
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 564
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 565
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 566
        return [] // library marker kkossev.deviceProfileLib, line 567
    } // library marker kkossev.deviceProfileLib, line 568
    String dpType // library marker kkossev.deviceProfileLib, line 569
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 570
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 571
    } // library marker kkossev.deviceProfileLib, line 572
    else { // library marker kkossev.deviceProfileLib, line 573
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 574
    } // library marker kkossev.deviceProfileLib, line 575
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 576
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 577
        return [] // library marker kkossev.deviceProfileLib, line 578
    } // library marker kkossev.deviceProfileLib, line 579
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 580
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 581
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 582
    cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 583
    return cmds // library marker kkossev.deviceProfileLib, line 584
} // library marker kkossev.deviceProfileLib, line 585

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 587
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 588
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 589
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 590
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 591
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 592

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 594
    if (dpMap == null || dpMap.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 595
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 596
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 597
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 598
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 599
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 600
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 601
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 602
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 603
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 604
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 605
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 606
        try { // library marker kkossev.deviceProfileLib, line 607
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 608
        } // library marker kkossev.deviceProfileLib, line 609
        catch (e) { // library marker kkossev.deviceProfileLib, line 610
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 611
            return false // library marker kkossev.deviceProfileLib, line 612
        } // library marker kkossev.deviceProfileLib, line 613
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 614
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 615
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 616
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 617
            return true // library marker kkossev.deviceProfileLib, line 618
        } // library marker kkossev.deviceProfileLib, line 619
        else { // library marker kkossev.deviceProfileLib, line 620
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 621
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 622
        } // library marker kkossev.deviceProfileLib, line 623
    } // library marker kkossev.deviceProfileLib, line 624
    else { // library marker kkossev.deviceProfileLib, line 625
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 626
    } // library marker kkossev.deviceProfileLib, line 627
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 628
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 629
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 630
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 631
        // patch !! // library marker kkossev.deviceProfileLib, line 632
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 633
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 634
        } // library marker kkossev.deviceProfileLib, line 635
        else { // library marker kkossev.deviceProfileLib, line 636
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 637
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 638
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 639
        } // library marker kkossev.deviceProfileLib, line 640
        return true // library marker kkossev.deviceProfileLib, line 641
    } // library marker kkossev.deviceProfileLib, line 642
    else { // library marker kkossev.deviceProfileLib, line 643
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 644
    } // library marker kkossev.deviceProfileLib, line 645
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 646
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 647
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 648
    try { // library marker kkossev.deviceProfileLib, line 649
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 650
    } // library marker kkossev.deviceProfileLib, line 651
    catch (e) { // library marker kkossev.deviceProfileLib, line 652
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 653
        return false // library marker kkossev.deviceProfileLib, line 654
    } // library marker kkossev.deviceProfileLib, line 655
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 656
        // Tuya DP // library marker kkossev.deviceProfileLib, line 657
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 658
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 659
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 660
            return false // library marker kkossev.deviceProfileLib, line 661
        } // library marker kkossev.deviceProfileLib, line 662
        else { // library marker kkossev.deviceProfileLib, line 663
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 664
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 665
            return true // library marker kkossev.deviceProfileLib, line 666
        } // library marker kkossev.deviceProfileLib, line 667
    } // library marker kkossev.deviceProfileLib, line 668
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 669
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 670
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 671
    } // library marker kkossev.deviceProfileLib, line 672
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 673
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 674
        int cluster // library marker kkossev.deviceProfileLib, line 675
        int attribute // library marker kkossev.deviceProfileLib, line 676
        int dt // library marker kkossev.deviceProfileLib, line 677
        // int mfgCode // library marker kkossev.deviceProfileLib, line 678
        try { // library marker kkossev.deviceProfileLib, line 679
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[0]) // library marker kkossev.deviceProfileLib, line 680
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 681
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[1]) // library marker kkossev.deviceProfileLib, line 682
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 683
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 684
        //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 685
        //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 686
        //  mfgCode = dpMap.mfgCode != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.mfgCode) : null // library marker kkossev.deviceProfileLib, line 687
        //  log.trace "mfgCode = ${mfgCode}" // library marker kkossev.deviceProfileLib, line 688
        } // library marker kkossev.deviceProfileLib, line 689
        catch (e) { // library marker kkossev.deviceProfileLib, line 690
            logWarn "sendAttribute: Exception '${e}'caught while splitting cluster and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 691
            return false // library marker kkossev.deviceProfileLib, line 692
        } // library marker kkossev.deviceProfileLib, line 693

        logDebug "sendAttribute: found cluster=${cluster} attribute=${attribute} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 695
        if (dpMap.mfgCode != null) { // library marker kkossev.deviceProfileLib, line 696
            Map mapMfCode = ['mfgCode':dpMap.mfgCode] // library marker kkossev.deviceProfileLib, line 697
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay = 200) // library marker kkossev.deviceProfileLib, line 698
        } // library marker kkossev.deviceProfileLib, line 699
        else { // library marker kkossev.deviceProfileLib, line 700
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 701
        } // library marker kkossev.deviceProfileLib, line 702
    } // library marker kkossev.deviceProfileLib, line 703
    else { // library marker kkossev.deviceProfileLib, line 704
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 705
        return false // library marker kkossev.deviceProfileLib, line 706
    } // library marker kkossev.deviceProfileLib, line 707
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 708
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 709
    return true // library marker kkossev.deviceProfileLib, line 710
} // library marker kkossev.deviceProfileLib, line 711

/** // library marker kkossev.deviceProfileLib, line 713
 * Sends a command to the device. // library marker kkossev.deviceProfileLib, line 714
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 715
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 716
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 717
 */ // library marker kkossev.deviceProfileLib, line 718
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 719
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 720
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 721
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 722
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 723
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 724
    if (supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 725
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 726
        return false // library marker kkossev.deviceProfileLib, line 727
    } // library marker kkossev.deviceProfileLib, line 728
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 729
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 730
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 731
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 732
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 733
        return false // library marker kkossev.deviceProfileLib, line 734
    } // library marker kkossev.deviceProfileLib, line 735
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 736
    def func // library marker kkossev.deviceProfileLib, line 737
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 738
    def funcResult // library marker kkossev.deviceProfileLib, line 739
    try { // library marker kkossev.deviceProfileLib, line 740
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 741
        if (val != null) { // library marker kkossev.deviceProfileLib, line 742
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 743
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 744
        } // library marker kkossev.deviceProfileLib, line 745
        else { // library marker kkossev.deviceProfileLib, line 746
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 747
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 748
        } // library marker kkossev.deviceProfileLib, line 749
    } // library marker kkossev.deviceProfileLib, line 750
    catch (e) { // library marker kkossev.deviceProfileLib, line 751
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 752
        return false // library marker kkossev.deviceProfileLib, line 753
    } // library marker kkossev.deviceProfileLib, line 754
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 755
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 756
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 757
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 758
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 759
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 760
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 761
        } // library marker kkossev.deviceProfileLib, line 762
    } else { // library marker kkossev.deviceProfileLib, line 763
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 764
        return false // library marker kkossev.deviceProfileLib, line 765
    } // library marker kkossev.deviceProfileLib, line 766
    cmds = funcResult // library marker kkossev.deviceProfileLib, line 767
    if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 768
        sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 769
    } // library marker kkossev.deviceProfileLib, line 770
    return true // library marker kkossev.deviceProfileLib, line 771
} // library marker kkossev.deviceProfileLib, line 772

/** // library marker kkossev.deviceProfileLib, line 774
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 775
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 776
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 777
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 778
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 779
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 780
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 781
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 782
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 783
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 784
 */ // library marker kkossev.deviceProfileLib, line 785
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 786
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 787
    Map input = [:] // library marker kkossev.deviceProfileLib, line 788
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 789
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 790
        if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 791
        return [:] // library marker kkossev.deviceProfileLib, line 792
    } // library marker kkossev.deviceProfileLib, line 793
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 794
    def preference // library marker kkossev.deviceProfileLib, line 795
    try { // library marker kkossev.deviceProfileLib, line 796
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 797
    } // library marker kkossev.deviceProfileLib, line 798
    catch (e) { // library marker kkossev.deviceProfileLib, line 799
        if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 800
        return [:] // library marker kkossev.deviceProfileLib, line 801
    } // library marker kkossev.deviceProfileLib, line 802
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 803
    try { // library marker kkossev.deviceProfileLib, line 804
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 805
            if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } // library marker kkossev.deviceProfileLib, line 806
            return [:] // library marker kkossev.deviceProfileLib, line 807
        } // library marker kkossev.deviceProfileLib, line 808
    } // library marker kkossev.deviceProfileLib, line 809
    catch (e) { // library marker kkossev.deviceProfileLib, line 810
        if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 811
        return [:] // library marker kkossev.deviceProfileLib, line 812
    } // library marker kkossev.deviceProfileLib, line 813

    try { // library marker kkossev.deviceProfileLib, line 815
        isTuyaDP = preference.isNumber() // library marker kkossev.deviceProfileLib, line 816
    } // library marker kkossev.deviceProfileLib, line 817
    catch (e) { // library marker kkossev.deviceProfileLib, line 818
        if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 819
        return [:] // library marker kkossev.deviceProfileLib, line 820
    } // library marker kkossev.deviceProfileLib, line 821

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 823
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 824
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 825
    if (foundMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 826
        if (debug) { log.warn "inputIt: map not found for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 827
        return [:] // library marker kkossev.deviceProfileLib, line 828
    } // library marker kkossev.deviceProfileLib, line 829
    if (foundMap.rw != 'rw') { // library marker kkossev.deviceProfileLib, line 830
        if (debug) { log.warn "inputIt: param '${param}' is read only!" } // library marker kkossev.deviceProfileLib, line 831
        return [:] // library marker kkossev.deviceProfileLib, line 832
    } // library marker kkossev.deviceProfileLib, line 833
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 834
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 835
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 836
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 837
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 838
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 839
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 840
        } // library marker kkossev.deviceProfileLib, line 841
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 842
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 843
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 844
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 845
            } // library marker kkossev.deviceProfileLib, line 846
        } // library marker kkossev.deviceProfileLib, line 847
    } // library marker kkossev.deviceProfileLib, line 848
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 849
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 850
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 851
    }/* // library marker kkossev.deviceProfileLib, line 852
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 853
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 854
    }*/ // library marker kkossev.deviceProfileLib, line 855
    else { // library marker kkossev.deviceProfileLib, line 856
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 857
        return [:] // library marker kkossev.deviceProfileLib, line 858
    } // library marker kkossev.deviceProfileLib, line 859
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 860
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 861
    } // library marker kkossev.deviceProfileLib, line 862
    return input // library marker kkossev.deviceProfileLib, line 863
} // library marker kkossev.deviceProfileLib, line 864

/** // library marker kkossev.deviceProfileLib, line 866
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 867
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 868
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 869
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 870
 */ // library marker kkossev.deviceProfileLib, line 871
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 872
    String deviceName         = UNKNOWN // library marker kkossev.deviceProfileLib, line 873
    String deviceProfile      = UNKNOWN // library marker kkossev.deviceProfileLib, line 874
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 875
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 876
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 877
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 878
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 879
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 880
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 881
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 882
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 883
            } // library marker kkossev.deviceProfileLib, line 884
        } // library marker kkossev.deviceProfileLib, line 885
    } // library marker kkossev.deviceProfileLib, line 886
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 887
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 888
    } // library marker kkossev.deviceProfileLib, line 889
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 890
} // library marker kkossev.deviceProfileLib, line 891

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 893
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 894
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 895
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 896
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 897
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 898
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 899
    } // library marker kkossev.deviceProfileLib, line 900
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 901
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 902
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 903
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 904
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 905
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 906
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 907
    } else { // library marker kkossev.deviceProfileLib, line 908
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 909
    } // library marker kkossev.deviceProfileLib, line 910
} // library marker kkossev.deviceProfileLib, line 911

// TODO! // library marker kkossev.deviceProfileLib, line 913
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 914
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 915
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 916
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 917
    return cmds // library marker kkossev.deviceProfileLib, line 918
} // library marker kkossev.deviceProfileLib, line 919

// TODO ! // library marker kkossev.deviceProfileLib, line 921
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 922
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 923
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 924
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.deviceProfileLib, line 925
    return cmds // library marker kkossev.deviceProfileLib, line 926
} // library marker kkossev.deviceProfileLib, line 927

// TODO // library marker kkossev.deviceProfileLib, line 929
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 930
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 931
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 932
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 933
    return cmds // library marker kkossev.deviceProfileLib, line 934
} // library marker kkossev.deviceProfileLib, line 935

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 937
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 938
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 939
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 940
    } // library marker kkossev.deviceProfileLib, line 941
} // library marker kkossev.deviceProfileLib, line 942

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 944
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 945
} // library marker kkossev.deviceProfileLib, line 946

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 948

// // library marker kkossev.deviceProfileLib, line 950
// called from parse() // library marker kkossev.deviceProfileLib, line 951
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 952
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 953
// // library marker kkossev.deviceProfileLib, line 954
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 955
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 956
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 957
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 958
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 959
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 960
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 961
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 962
} // library marker kkossev.deviceProfileLib, line 963

// // library marker kkossev.deviceProfileLib, line 965
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 966
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 967
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 968
// // library marker kkossev.deviceProfileLib, line 969
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 970
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 971
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 972
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 973
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 974
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 975
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 976
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 977
} // library marker kkossev.deviceProfileLib, line 978

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 980
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 981
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 982
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 983
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 984
        log.warn "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 985
    } // library marker kkossev.deviceProfileLib, line 986
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 987
} // library marker kkossev.deviceProfileLib, line 988

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 990
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 991
    boolean isEqual // library marker kkossev.deviceProfileLib, line 992
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 993
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 994
    } // library marker kkossev.deviceProfileLib, line 995
    else { // library marker kkossev.deviceProfileLib, line 996
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 997
    } // library marker kkossev.deviceProfileLib, line 998
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 999
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1000
} // library marker kkossev.deviceProfileLib, line 1001

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 1003
    Double convertedValue // library marker kkossev.deviceProfileLib, line 1004
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1005
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 1006
    } // library marker kkossev.deviceProfileLib, line 1007
    else { // library marker kkossev.deviceProfileLib, line 1008
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1009
    } // library marker kkossev.deviceProfileLib, line 1010
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1011
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1012
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1013
} // library marker kkossev.deviceProfileLib, line 1014

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1016
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1017
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1018
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1019
    def convertedValue // library marker kkossev.deviceProfileLib, line 1020
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1021
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1022
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1023
    } // library marker kkossev.deviceProfileLib, line 1024
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1025
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1026
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1027
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1028
            isEqual = false // library marker kkossev.deviceProfileLib, line 1029
        } // library marker kkossev.deviceProfileLib, line 1030
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1031
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1032
        } // library marker kkossev.deviceProfileLib, line 1033
    } // library marker kkossev.deviceProfileLib, line 1034
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1035
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1036
} // library marker kkossev.deviceProfileLib, line 1037

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1039
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1040
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1041
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1042
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1043
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1044
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1045
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1046
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1047
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1048
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1049
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1050
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1051
            break // library marker kkossev.deviceProfileLib, line 1052
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1053
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1054
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1055
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1056
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1057
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1058
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1059
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1060
            } // library marker kkossev.deviceProfileLib, line 1061
            else { // library marker kkossev.deviceProfileLib, line 1062
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1063
            } // library marker kkossev.deviceProfileLib, line 1064
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1065
            break // library marker kkossev.deviceProfileLib, line 1066
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1067
        case 'number' : // library marker kkossev.deviceProfileLib, line 1068
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1069
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1070
            break // library marker kkossev.deviceProfileLib, line 1071
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1072
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1073
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1074
            break // library marker kkossev.deviceProfileLib, line 1075
        default : // library marker kkossev.deviceProfileLib, line 1076
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1077
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1078
    } // library marker kkossev.deviceProfileLib, line 1079
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1080
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1081
    } // library marker kkossev.deviceProfileLib, line 1082
    // // library marker kkossev.deviceProfileLib, line 1083
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1084
} // library marker kkossev.deviceProfileLib, line 1085

// // library marker kkossev.deviceProfileLib, line 1087
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1088
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1089
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1090
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1091
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1092
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1093
// // library marker kkossev.deviceProfileLib, line 1094
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1095
// // library marker kkossev.deviceProfileLib, line 1096
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1097
// // library marker kkossev.deviceProfileLib, line 1098
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1099
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1100
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1101
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1102
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1103
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1104
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1105
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1106
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1107
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1108
            break // library marker kkossev.deviceProfileLib, line 1109
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1110
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1111
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1112
            String dataType = latestEvent?.dataType  // library marker kkossev.deviceProfileLib, line 1113
            logTrace "latestEvent is dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1114
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1115
            if (dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1116
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1117
            } // library marker kkossev.deviceProfileLib, line 1118
            else { // library marker kkossev.deviceProfileLib, line 1119
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1120
            } // library marker kkossev.deviceProfileLib, line 1121
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1122
            break // library marker kkossev.deviceProfileLib, line 1123
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1124
        case 'number' : // library marker kkossev.deviceProfileLib, line 1125
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1126
            break // library marker kkossev.deviceProfileLib, line 1127
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1128
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1129
            break // library marker kkossev.deviceProfileLib, line 1130
        default : // library marker kkossev.deviceProfileLib, line 1131
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1132
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1133
    } // library marker kkossev.deviceProfileLib, line 1134
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1135
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1136
} // library marker kkossev.deviceProfileLib, line 1137

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1139
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1140
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1141
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1142
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1143
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1144
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1145
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1146
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1147
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1148
    } // library marker kkossev.deviceProfileLib, line 1149
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1150
    try { // library marker kkossev.deviceProfileLib, line 1151
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1152
    } // library marker kkossev.deviceProfileLib, line 1153
    catch (e) { // library marker kkossev.deviceProfileLib, line 1154
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1155
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1156
    } // library marker kkossev.deviceProfileLib, line 1157
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1158
    return fncmd // library marker kkossev.deviceProfileLib, line 1159
} // library marker kkossev.deviceProfileLib, line 1160

/** // library marker kkossev.deviceProfileLib, line 1162
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1163
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1164
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1165
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1166
 * // library marker kkossev.deviceProfileLib, line 1167
 * @param descMap The description map of the received DP. // library marker kkossev.deviceProfileLib, line 1168
 * @param dp The value of the received DP. // library marker kkossev.deviceProfileLib, line 1169
 * @param dp_id The ID of the received DP. // library marker kkossev.deviceProfileLib, line 1170
 * @param fncmd The command of the received DP. // library marker kkossev.deviceProfileLib, line 1171
 * @param dp_len The length of the received DP. // library marker kkossev.deviceProfileLib, line 1172
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1173
 */ // library marker kkossev.deviceProfileLib, line 1174
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1175
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1176
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1177
    //if (!(DEVICE?.device?.type == "radar"))      { return false }   // enabled for all devices - 10/22/2023 !!!    // only these models are handled here for now ... // library marker kkossev.deviceProfileLib, line 1178
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1179

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1181
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1182

    Map foundItem = null // library marker kkossev.deviceProfileLib, line 1184
    tuyaDPsMap.each { item -> // library marker kkossev.deviceProfileLib, line 1185
        if (item['dp'] == (dp as int)) { // library marker kkossev.deviceProfileLib, line 1186
            foundItem = item // library marker kkossev.deviceProfileLib, line 1187
            return // library marker kkossev.deviceProfileLib, line 1188
        } // library marker kkossev.deviceProfileLib, line 1189
    } // library marker kkossev.deviceProfileLib, line 1190
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1191
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1192
        updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len) // library marker kkossev.deviceProfileLib, line 1193
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1194
        return false // library marker kkossev.deviceProfileLib, line 1195
    } // library marker kkossev.deviceProfileLib, line 1196

    return processFoundItem(foundItem, fncmd_orig) // library marker kkossev.deviceProfileLib, line 1198
} // library marker kkossev.deviceProfileLib, line 1199

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1201
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1202
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1203
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1204

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1206
    if (attribMap == null || attribMap.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1207

    Map foundItem = null // library marker kkossev.deviceProfileLib, line 1209
    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1210
    int value // library marker kkossev.deviceProfileLib, line 1211
    try { // library marker kkossev.deviceProfileLib, line 1212
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1213
    } // library marker kkossev.deviceProfileLib, line 1214
    catch (e) { // library marker kkossev.deviceProfileLib, line 1215
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1216
        return false // library marker kkossev.deviceProfileLib, line 1217
    } // library marker kkossev.deviceProfileLib, line 1218
    //logTrace "clusterAttribute = ${clusterAttribute}" // library marker kkossev.deviceProfileLib, line 1219
    attribMap.each { item -> // library marker kkossev.deviceProfileLib, line 1220
        if (item['at'] == clusterAttribute) { // library marker kkossev.deviceProfileLib, line 1221
            foundItem = item // library marker kkossev.deviceProfileLib, line 1222
            return // library marker kkossev.deviceProfileLib, line 1223
        } // library marker kkossev.deviceProfileLib, line 1224
    } // library marker kkossev.deviceProfileLib, line 1225
    if (foundItem == null) { // library marker kkossev.deviceProfileLib, line 1226
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1227
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1228
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1229
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1230
        return false // library marker kkossev.deviceProfileLib, line 1231
    } // library marker kkossev.deviceProfileLib, line 1232
    return processFoundItem(foundItem, value) // library marker kkossev.deviceProfileLib, line 1233
} // library marker kkossev.deviceProfileLib, line 1234

// modifies the value of the foundItem if needed !!! // library marker kkossev.deviceProfileLib, line 1236
/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.deviceProfileLib, line 1237
boolean processFoundItem(final Map foundItem, int value) { // library marker kkossev.deviceProfileLib, line 1238
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1239
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1240
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1241
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1242
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1243
        if (preProcValue == null) { // library marker kkossev.deviceProfileLib, line 1244
            logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" // library marker kkossev.deviceProfileLib, line 1245
            return true // library marker kkossev.deviceProfileLib, line 1246
        } // library marker kkossev.deviceProfileLib, line 1247
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1248
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1249
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1250
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1251
        } // library marker kkossev.deviceProfileLib, line 1252
    } // library marker kkossev.deviceProfileLib, line 1253
    else { // library marker kkossev.deviceProfileLib, line 1254
        logTrace "processFoundItem: no preProc for ${foundItem.name}" // library marker kkossev.deviceProfileLib, line 1255
    } // library marker kkossev.deviceProfileLib, line 1256

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1258
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1259
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1260
    //existingPrefValue = existingPrefValue?.replace("[", "").replace("]", "")               // preference name as in Hubitat settings (preferences), if already created. // library marker kkossev.deviceProfileLib, line 1261
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1262
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1263
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1264
    //boolean preferenceExists = settings.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1265
    boolean preferenceExists = DEVICE?.preferences?.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1266
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1267
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1268
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1269
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1270
    boolean doNotTrace = false  // isSpammyDPsToNotTrace(descMap)          // do not log/trace the spammy clusterAttribute's TODO! // library marker kkossev.deviceProfileLib, line 1271
    if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1272
        logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" // library marker kkossev.deviceProfileLib, line 1273
    } // library marker kkossev.deviceProfileLib, line 1274
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1275
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1276
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1277
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1278
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1279
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1280

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1282
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1283
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1284
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1285
            // TODO - scaledValue ????? // library marker kkossev.deviceProfileLib, line 1286
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1287
            if (settings.logEnable) { logInfo "${descText }" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1288
        } // library marker kkossev.deviceProfileLib, line 1289
        // no more processing is needed, as this clusterAttribute is not a preference and not an attribute // library marker kkossev.deviceProfileLib, line 1290
        return true // library marker kkossev.deviceProfileLib, line 1291
    } // library marker kkossev.deviceProfileLib, line 1292

    // first, check if there is a preference defined to be updated // library marker kkossev.deviceProfileLib, line 1294
    if (preferenceExists) { // library marker kkossev.deviceProfileLib, line 1295
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1296
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1297
        //log.trace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1298
        if (isEqual == true) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1299
            logDebug "no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1300
        } // library marker kkossev.deviceProfileLib, line 1301
        else { // library marker kkossev.deviceProfileLib, line 1302
            String scaledPreferenceValue = preferenceValue      //.toString() is not neccessary // library marker kkossev.deviceProfileLib, line 1303
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1304
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1305
            } // library marker kkossev.deviceProfileLib, line 1306
            logDebug "preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1307
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1308
            try { // library marker kkossev.deviceProfileLib, line 1309
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1310
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1311
            } // library marker kkossev.deviceProfileLib, line 1312
            catch (e) { // library marker kkossev.deviceProfileLib, line 1313
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1314
            } // library marker kkossev.deviceProfileLib, line 1315
        } // library marker kkossev.deviceProfileLib, line 1316
    } // library marker kkossev.deviceProfileLib, line 1317
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1318
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1319
        unitText = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1320
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1321
    } // library marker kkossev.deviceProfileLib, line 1322

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1324
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1325
        logTrace "attribute '${name}' exists (type ${foundItem.type})" // library marker kkossev.deviceProfileLib, line 1326
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1327
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1328
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1329
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1330
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1331
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1332
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1333
            } // library marker kkossev.deviceProfileLib, line 1334
            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1335
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1336
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1337
            // continue ... // library marker kkossev.deviceProfileLib, line 1338
            } // library marker kkossev.deviceProfileLib, line 1339
            else { // library marker kkossev.deviceProfileLib, line 1340
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1341
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1342
                } // library marker kkossev.deviceProfileLib, line 1343
                else { // library marker kkossev.deviceProfileLib, line 1344
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1345
                } // library marker kkossev.deviceProfileLib, line 1346
            } // library marker kkossev.deviceProfileLib, line 1347
        } // library marker kkossev.deviceProfileLib, line 1348

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an event! // library marker kkossev.deviceProfileLib, line 1350

        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1352
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1353
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1354
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1355
        if (DEVICE_TYPE in ['Thermostat'])  { processDeviceEventThermostat(name, valueScaled, unitText, descText) } // library marker kkossev.deviceProfileLib, line 1356
        else { // library marker kkossev.deviceProfileLib, line 1357
            switch (name) { // library marker kkossev.deviceProfileLib, line 1358
                case 'motion' : // library marker kkossev.deviceProfileLib, line 1359
                    handleMotion(value as boolean)  // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1360
                    break // library marker kkossev.deviceProfileLib, line 1361
                case 'temperature' : // library marker kkossev.deviceProfileLib, line 1362
                    //temperatureEvent(value / getTemperatureDiv()) // library marker kkossev.deviceProfileLib, line 1363
                    handleTemperatureEvent(valueScaled as Float) // library marker kkossev.deviceProfileLib, line 1364
                    break // library marker kkossev.deviceProfileLib, line 1365
                case 'humidity' : // library marker kkossev.deviceProfileLib, line 1366
                    handleHumidityEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1367
                    break // library marker kkossev.deviceProfileLib, line 1368
                case 'illuminance' : // library marker kkossev.deviceProfileLib, line 1369
                case 'illuminance_lux' : // library marker kkossev.deviceProfileLib, line 1370
                    handleIlluminanceEvent(valueCorrected.toInteger()) // library marker kkossev.deviceProfileLib, line 1371
                    break // library marker kkossev.deviceProfileLib, line 1372
                case 'pushed' : // library marker kkossev.deviceProfileLib, line 1373
                    logDebug "button event received value=${value} valueScaled=${valueScaled} valueCorrected=${valueCorrected}" // library marker kkossev.deviceProfileLib, line 1374
                    buttonEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1375
                    break // library marker kkossev.deviceProfileLib, line 1376
                default : // library marker kkossev.deviceProfileLib, line 1377
                    sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1378
                    if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1379
                        logDebug "event ${name} sent w/ value ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1380
                        logTrace "${descText}"                                 // send an Info log also (because value changed )  // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1381
                    } // library marker kkossev.deviceProfileLib, line 1382
                    break // library marker kkossev.deviceProfileLib, line 1383
            } // library marker kkossev.deviceProfileLib, line 1384
        //logTrace "attrValue=${attrValue} valueScaled=${valueScaled} equal=${isEqual}" // library marker kkossev.deviceProfileLib, line 1385
        } // library marker kkossev.deviceProfileLib, line 1386
    } // library marker kkossev.deviceProfileLib, line 1387
    // all processing was done here! // library marker kkossev.deviceProfileLib, line 1388
    return true // library marker kkossev.deviceProfileLib, line 1389
} // library marker kkossev.deviceProfileLib, line 1390

public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1392
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1393
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 1394
        logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1395
        return false // library marker kkossev.deviceProfileLib, line 1396
    } // library marker kkossev.deviceProfileLib, line 1397
    int validationFailures = 0 // library marker kkossev.deviceProfileLib, line 1398
    int validationFixes = 0 // library marker kkossev.deviceProfileLib, line 1399
    int total = 0 // library marker kkossev.deviceProfileLib, line 1400
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1401
    def oldSettingValue // library marker kkossev.deviceProfileLib, line 1402
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1403
    def newValue // library marker kkossev.deviceProfileLib, line 1404
    String settingType // library marker kkossev.deviceProfileLib, line 1405
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1406
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1407
        if (foundMap == null || foundMap == [:]) { // library marker kkossev.deviceProfileLib, line 1408
            logDebug "validateAndFixPreferences: map not found for preference ${it.key}"    // 10/21/2023 - sevirity lowered to debug // library marker kkossev.deviceProfileLib, line 1409
            return false // library marker kkossev.deviceProfileLib, line 1410
        } // library marker kkossev.deviceProfileLib, line 1411
        settingType = device.getSettingType(it.key) // library marker kkossev.deviceProfileLib, line 1412
        oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1413
        if (settingType == null) { // library marker kkossev.deviceProfileLib, line 1414
            logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" // library marker kkossev.deviceProfileLib, line 1415
            return false // library marker kkossev.deviceProfileLib, line 1416
        } // library marker kkossev.deviceProfileLib, line 1417
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1418
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1419
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1420
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1421
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1422
            try { // library marker kkossev.deviceProfileLib, line 1423
                device.removeSetting(it.key) // library marker kkossev.deviceProfileLib, line 1424
                logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1425
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1426
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1427
                return false // library marker kkossev.deviceProfileLib, line 1428
            } // library marker kkossev.deviceProfileLib, line 1429
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1430
            try { // library marker kkossev.deviceProfileLib, line 1431
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1432
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1433
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1434
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1435
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1436
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1437
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1438
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1439
                    } // library marker kkossev.deviceProfileLib, line 1440
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1441
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1442
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1443
                    } else { // library marker kkossev.deviceProfileLib, line 1444
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1445
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1446
                    } // library marker kkossev.deviceProfileLib, line 1447
                } // library marker kkossev.deviceProfileLib, line 1448
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1449
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1450
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1451
            } // library marker kkossev.deviceProfileLib, line 1452
            catch (e) { // library marker kkossev.deviceProfileLib, line 1453
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1454
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1455
                try { // library marker kkossev.deviceProfileLib, line 1456
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1457
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1458
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1459
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1460
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1461
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" // library marker kkossev.deviceProfileLib, line 1462
                    return false // library marker kkossev.deviceProfileLib, line 1463
                } // library marker kkossev.deviceProfileLib, line 1464
            } // library marker kkossev.deviceProfileLib, line 1465
        } // library marker kkossev.deviceProfileLib, line 1466
        total ++ // library marker kkossev.deviceProfileLib, line 1467
    } // library marker kkossev.deviceProfileLib, line 1468
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1469
    return true // library marker kkossev.deviceProfileLib, line 1470
} // library marker kkossev.deviceProfileLib, line 1471

void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1473
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1474
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1475
            logInfo fingerprint // library marker kkossev.deviceProfileLib, line 1476
        } // library marker kkossev.deviceProfileLib, line 1477
    } // library marker kkossev.deviceProfileLib, line 1478
} // library marker kkossev.deviceProfileLib, line 1479

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~
