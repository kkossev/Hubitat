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
 *
 * ver. 3.0.0  2023-11-16 kkossev  - (dev. branch) Refactored version 2.x.x drivers and libraries; adding MOES BRT-100 support - setHeatingSettpoint OK; off OK; level OK; workingState OK
 *                                    Emergency Heat OK;   setThermostatMode OK; Heat OK, Auto OK, Cool OK; setThermostatFanMode OK
 * ver. 3.0.1  2023-12-02 kkossev  - (dev. branch) added NAMRON thermostat profile; added Sonoff TRVZB 0x0201 (Thermostat Cluster) support; thermostatOperatingState ; childLock OK; windowOpenDetection OK; setPar OK for BRT-100 and Aqara;
 *                                    minHeatingSetpoint & maxHeatingSetpoint OK; calibrationTemp negative values OK!; auto OK; heat OK; cool and emergency heat OK (unsupported); Sonoff off mode OK;
 * ver. 3.0.2  2023-12-02 kkossev  - (dev. branch) importUrl correction; BRT-100: auto OK, heat OK, eco OK, supportedThermostatModes is defined in the device profile; refresh OK; autPoll OK (both BRT-100 and Sonoff);
 *                                   removed isBRT100TRV() ; removed isSonoffTRV(), removed 'off' mode for BRT-100; heatingSetPoint 12.3 bug fixed; 
 * ver. 3.0.3  2023-12-03 kkossev  - (dev. branch) Aqara E1 thermostat refactoring : removed isAqaraTRV(); heatingSetpoint OK; off mode OK, auto OK heat OK; driverVersion state is updated on healthCheck and on preferences saving;
 * ver. 3.0.4  2023-12-08 kkossev  - (dev. branch) code cleanup; fingerpints not generated bug fix; initializeDeviceThermostat() bug fix; debug logs are enabled by default; added VIRTUAL thermostat : ping, auto, cool, emergency heat, heat, off, eco - OK! 
 *                                   setTemperature, setHeatingSetpoint, setCoolingSetpoint - OK setPar() OK  setCommand() OK
 *
 *                                   WIP : adding VIRTUAL thermostat - option to simualate the thermostatOperatingState  
 *                                   WIP : Google Home exceptions bug fix; update thermostatSetpoint for Google Home compatibility
 *                                   TODO: initializeDeviceThermostat() - configure in the device profile ! 
 *                                   TODO: partial match for the fingerprint (model if Tuya, manufacturer for the rest)
 *                                   TODO: Sonoff - add 'emergency heat' simulation ?  ( +timer ?)
 *                                   TODO: Aqara TRV refactoring : add 'defaults' in the device profile to set up the systemMode initial value as 'unknown'
 *                                   TODO: remove (raw:) when debug is off
 *                                   TODO: BRT-100 dev:32912023-12-02 14:10:56.995debugBRT-100 TRV DEV preference 'ecoMode' value [1] differs from the new scaled value 1 (clusterAttribute raw value 1)
 *                                   TODO: BRT-100 dev:32912023-12-02 14:10:56.989debugBRT-100 TRV DEV compareAndConvertTuyaToHubitatPreferenceValue: preference = [1] type=enum foundItem=ecoMode isEqual=false tuyaValueScaled=1 (scale=1) fncmd=1
 *                                   TODO: BRT-100 - after emergency heat, the mode was set to 'auto' (not 'heat') !
 *                                   TODO: prevent from showing "invalid enum parameter emergency heat. value must be one of [0, 1, 2, 3, 4]"; also for  invalid enum parameter cool. *                                   
 *                                   TODO: add [refresh] for battery heatingSetpoint thermostatOperatingState events and logs
 *                                   TODO: hide the maxTimeBetweenReport preferences (not used here)
 *                                   TODO: option to disale the Auto mode ! (like in the wall thermostat driver)
 *                                   TODO: allow NULL parameters default values in the device profiles
 *                                   TODO: autoPollThermostat: no polling for device profile UNKNOWN
 *                                   TODO: // TODO - configure the reporting for the 0x0201:0x0000 temperature !  (300..3600)
 *                                   TODO: Ping the device on initialize
 *                                   TODO: add factoryReset command Basic -0x0000 (Server); command 0x00
 *                                   TODO: handle UNKNOWN TRV
 *                                   TODO: initializeThermostat() 
 *                                   TODO: Healthcheck to be every hour (not 4 hours)
 *                                   TODO: add option 'Cool similation'
 *                                   TODO: add option 'Simple TRV' (no additinal attributes)
 *                                   TODO: add state.trv for stroring attributes
 *                                   TODO: add 'force manual mode' preference
 *                                   TODO: move debug and info logging preferences from the common library to the driver, so that they are the first preferences in the list
 *                                   TODO: add Info dummy preference to the driver with a hyperlink 
 *                                   TODO: change deviceProfilesV2 to deviceProfilesV3 in the lib
 *                                   TODO: add test command to list the fingerprints generated by the deviceProfileLib
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off)
 *                                   TODO: make a driver template for new drivers
 *                                   TODO: Versions of the main module + included libraries 
 *                                   TODO: HomeKit - min and max temperature limits?
 *                                   TODO: add receiveCheck() methods for heatingSetpint and mode (option)
 *                                   TODO: separate the autoPoll commands from the refresh commands (lite)
 *                                   TODO: Aqara TRV refactoring : 'cool' and 'emergency heat' and 'eco' modes to return meaningfull error message (check in the device profile if this mode is supported)
 *                                   TODO: Aqara TRV refactoring : simulate the 'emergency heat' mode by setting maxTemp and when off - restore the previous temperature 
 *                                   TODO: Aqara TRV refactoring : calibration as a command ! 
 *                                   TODO: Aqara TRV refactoring : physical vs digital events ?
 *                                   TODO: Aqara E1 external sensor
 */

static String version() { "3.0.4" }
static String timeStamp() {"2023/12/08 8:24 AM"}

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput


deviceType = "Thermostat"
@Field static final String DEVICE_TYPE = "Thermostat"
#include kkossev.commonLib
#include kkossev.xiaomiLib
#include kkossev.deviceProfileLib


metadata {
    definition (
        name: 'Zigbee TRVs and Thermostats',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee%20TRV/Zigbee_TRV_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true ) 
    {    
        capability "Actuator"
        capability "Refresh"
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Thermostat"                 // needed for HomeKit
        capability "ThermostatHeatingSetpoint"
        capability "ThermostatCoolingSetpoint"
        capability "ThermostatOperatingState"   // thermostatOperatingState - ENUM ["vent economizer", "pending cool", "cooling", "heating", "pending heat", "fan only", "idle"]
        capability "ThermostatSetpoint"
        capability "ThermostatMode"    
        capability "ThermostatFanMode"

        // TODO - add all other models attributes possible values
        attribute 'battery', "number"                               // Aqara, BRT-100
        attribute 'boostTime', "number"                             // BRT-100
        attribute "calibrated", 'enum', ['false', 'true']           // Aqara E1
        attribute 'calibrationTemp', "number"                       // BRT-100, Sonoff
        attribute 'childLock', "enum", ["off", "on"]                // BRT-100, Aqara E1, Sonoff
        attribute 'ecoMode', "enum", ["off", "on"]                  // BRT-100
        attribute 'ecoTemp', "number"                               // BRT-100
        attribute 'emergencyHeating', "enum", ["off", "on"]         // BRT-100
        attribute 'emergencyHeatingTime', "number"                  // BRT-100
        attribute 'frostProtectionTemperature', "number"            // Sonoff
        attribute "hysteresis", "NUMBER"                            // Virtual thermostat
        attribute 'level', "number"                                 // BRT-100          
        attribute 'minHeatingSetpoint', "number"                    // BRT-100, Sonoff
        attribute 'maxHeatingSetpoint', "number"                    // BRT-100, Sonoff
        attribute "sensor", 'enum', ['internal','external']         // Aqara E1
        attribute "valveAlarm", 'enum',  ['false', 'true']          // Aqara E1
        attribute "valveDetection", 'enum', ['off', 'on']           // Aqara E1
        attribute 'weeklyProgram', "number"                         // BRT-100
        attribute 'windowOpenDetection', "enum", ["off", "on"]      // BRT-100, Aqara E1, Sonoff
        attribute 'windowsState', "enum", ["open", "closed"]        // BRT-100, Aqara E1
        attribute 'workingState', "enum", ["open", "closed"]        // BRT-100 

        // Aqaura E1 attributes     TODO - consolidate a common set of attributes
        attribute "systemMode", 'enum', SystemModeOpts.options.values() as List<String>            // 'off','on'    - used!
        attribute "preset", 'enum', PresetOpts.options.values() as List<String>                     // 'manual','auto','away'
        attribute "awayPresetTemperature", 'number'

        command "setThermostatMode", [[name: "thermostat mode (not all are available!)", type: "ENUM", constraints: ["--- select ---"]+AllPossibleThermostatModesOpts.options.values() as List<String>]]
        command "setTemperature", ["NUMBER"]                        // Virtual thermostat
        if (_DEBUG) { command "testT", [[name: "testT", type: "STRING", description: "testT", defaultValue : ""]]  }

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
        if (advancedOptions == true) {
            input name: 'temperaturePollingInterval', type: 'enum', title: '<b>Temperature polling interval</b>', options: TrvTemperaturePollingIntervalOpts.options, defaultValue: TrvTemperaturePollingIntervalOpts.defaultValue, required: true, description: '<i>Changes how often the hub will poll the TRV for faster temperature reading updates.</i>'
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
    "AQARA_E1_TRV"   : [
            description   : "Aqara E1 Thermostat model SRTS-A01",
            device        : [manufacturers: ["LUMI"], type: "TRV", powerSource: "battery", isSleepy:false],
            capabilities  : ["ThermostatHeatingSetpoint": true, "ThermostatOperatingState": true, "ThermostatSetpoint":true, "ThermostatMode":true],

            preferences   : ["windowOpenDetection":"0xFCC0:0x0273", "valveDetection":"0xFCC0:0x0274",, "childLock":"0xFCC0:0x0277", "awayPresetTemperature":"0xFCC0:0x0279"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,FCC0,000A,0201", outClusters:"0003,FCC0,0201", model:"lumi.airrtc.agl001", manufacturer:"LUMI", deviceJoinName: "Aqara E1 Thermostat"] 
            ],
            commands      : ["sendSupportedThermostatModes":"sendSupportedThermostatModes", "autoPollThermostat":"autoPollThermostat", "resetStats":"resetStats", 'refresh':'refresh', "initialize":"initialize", "updateAllPreferences": "updateAllPreferences", "resetPreferencesToDefaults":"resetPreferencesToDefaults", "validateAndFixPreferences":"validateAndFixPreferences"],
            tuyaDPs       : [:],
            attributes    : [
                [at:"0xFCC0:0x040A",  name:'battery',               type:"number",  dt:"0x20", mfgCode:"0x115f",  rw: "ro", min:0,    max:100,  step:1,  scale:1,    unit:"%",  description:'<i>Battery percentage remaining</i>'],
                [at:"0xFCC0:0x0270",  name:'unknown1',              type:"enum",    dt:"0x20", mfgCode:"0x115f",  rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "false", 1: "true"], unit:"",   title: "<b>Unknown 0x0270</b>",   description:'<i>Unknown 0x0270</i>'],
                [at:"0xFCC0:0x0271",  name:'systemMode',            type:"enum",    dt:"0x20", mfgCode:"0x115f",  rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "on"], unit:"",     title: "<b>System Mode</b>",      description:'<i>Switch the TRV OFF or in operation (on)</i>'],
                [at:"0xFCC0:0x0272",  name:'thermostatMode',        type:"enum",    dt:"0x20", mfgCode:"0x115f",  rw: "rw", min:0,    max:2,    step:1,  scale:1,    map:[0: "heat", 1: "auto", 2: "away"], unit:"",                   title: "<b>Preset</b>",           description:'<i>Preset</i>'],
                [at:"0xFCC0:0x0273",  name:'windowOpenDetection',   type:"enum",    dt:"0x20", mfgCode:"0x115f",  rw: "rw", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "off", 1: "on"], unit:"",             title: "<b>Window Detection</b>", description:'<i>Window detection</i>'],
                [at:"0xFCC0:0x0274",  name:'valveDetection',        type:"enum",    dt:"0x20", mfgCode:"0x115f",  rw: "rw", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "off", 1: "on"], unit:"",             title: "<b>Valve Detection</b>",  description:'<i>Valve detection</i>'],
                [at:"0xFCC0:0x0275",  name:'valveAlarm',            type:"enum",    dt:"0x23", mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "false", 1: "true"], unit:"",         title: "<b>Valve Alarm</b>",      description:'<i>Valve alarm</i>'],  // read only
                [at:"0xFCC0:0x0276",  name:'unknown2',              type:"enum",    dt:"0x41", mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    step:1,  scale:1,    map:[0: "false", 1: "true"], unit:"",         title: "<b>Unknown 0x0270</b>",                        description:'<i>Unknown 0x0270</i>'],
                [at:"0xFCC0:0x0277",  name:'childLock',             type:"enum",    dt:"0x20", mfgCode:"0x115f",  rw: "rw", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "unlock", 1: "lock"], unit:"",        title: "<b>Child Lock</b>",       description:'<i>Child lock</i>'],
                [at:"0xFCC0:0x0278",  name:'unknown3',              type:"enum",    dt:"0x20", mfgCode:"0x115f",  rw: "ow", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "false", 1: "true"], unit:"",         title: "<b>Unknown 3</b>",        description:'<i>Unknown 3</i>'],   // WRITE ONLY !
                [at:"0xFCC0:0x0279",  name:'awayPresetTemperature', type:"decimal", dt:"0x23", mfgCode:"0x115f",  rw: "rw", min:5.0,  max:35.0, defaultValue:5.0,    step:0.5, scale:100,  unit:"°C", title: "<b>Away Preset Temperature</b>",       description:'<i>Away preset temperature</i>'],
                [at:"0xFCC0:0x027A",  name:'windowsState',          type:"enum",    dt:"0x20", mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "open", 1: "closed"], unit:"",        title: "<b>Window Open</b>",      description:'<i>Window open</i>'],
                [at:"0xFCC0:0x027B",  name:'calibrated',            type:"enum",    dt:"0x20", mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "false", 1: "true"], unit:"",         title: "<b>Calibrated</b>",       description:'<i>Calibrated</i>'],
                [at:"0xFCC0:0x027C",  name:'unknown4',              type:"enum",    dt:"0x20", mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "false", 1: "true"], unit:"",         title: "<b>Unknown 4</b>",        description:'<i>Unknown 4</i>'],
                [at:"0xFCC0:0x027D",  name:'schedule',              type:"enum",    dt:"0x20", mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "off", 1: "on"], unit:"",             title: "<b>Schedule</b>",        description:'<i>Schedule</i>'],
                [at:"0xFCC0:0x027E",  name:'sensor',                type:"enum",    dt:"0x20", mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "internal", 1: "external"], unit:"",  title: "<b>Sensor</b>",           description:'<i>Sensor</i>'],
                //   0xFCC0:0x027F ... 0xFCC0:0x0284 - unknown
                [at:"0x0201:0x0000",  name:'temperature',           type:"decimal", dt:"0x29", rw: "ro", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Temperature</b>",                   description:'<i>Measured temperature</i>'],
                [at:"0x0201:0x0011",  name:'coolingSetpoint',       type:"decimal", dt:"0x29", rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Cooling Setpoint</b>",              description:'<i>cooling setpoint</i>'],
                [at:"0x0201:0x0012",  name:'heatingSetpoint',       type:"decimal", dt:"0x29", rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Current Heating Setpoint</b>",      description:'<i>Current heating setpoint</i>'], 
                [at:"0x0201:0x001B",  name:'thermostatOperatingState', type:"enum",    dt:"0x30", rw:"rw",  min:0,    max:4,    step:1,  scale:1,    map:[0: "off", 1: "heating", 2: "unknown", 3: "unknown3", 4: "idle"], unit:"",  description:'<i>thermostatOperatingState (relay on/off status)</i>'],      //  nothing happens when WRITING ????
                //                      ^^^^                                reporting only ?
                [at:"0x0201:0x001C",  name:'mode',                  type:"enum",    dt:"0x30", rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b> Mode</b>",                   description:'<i>System Mode ?</i>'],
                //                      ^^^^ TODO - check if this is the same as system_mode    
                [at:"0x0201:0x001E",  name:'thermostatRunMode',     type:"enum",    dt:"0x20", rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatRunMode</b>",                   description:'<i>thermostatRunMode</i>'],
                //                          ^^ unsupported attribute?  or reporting only ?
                [at:"0x0201:0x0020",  name:'battery2',              type:"number",  dt:"0x20", rw: "ro", min:0,    max:100,  step:1,  scale:1,    unit:"%",  description:'<i>Battery percentage remaining</i>'],
                //                          ^^ unsupported attribute?  or reporting only ?
                [at:"0x0201:0x0023",  name:'thermostatHoldMode',    type:"enum",    dt:"0x20", rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatHoldMode</b>",                   description:'<i>thermostatHoldMode</i>'],
                //                          ^^ unsupported attribute?  or reporting only ? 
                [at:"0x0201:0x0029",  name:'thermostatOperatingState', type:"enum", dt:"0x20", rw: "ow", min:0,    max:1,    step:1,  scale:1,    map:[0: "idle", 1: "heating"], unit:"",         title: "<b>thermostatOperatingState</b>",                   description:'<i>thermostatOperatingState</i>'],
                //                          ^^ unsupported attribute?  or reporting only ?   encoding - 0x29 ?? ^^
                [at:"0x0201:0xFFF2",  name:'unknown',                type:"number", dt:"0x21", rw: "ro", min:0,    max:100,  step:1,  scale:1,    unit:"%",  description:'<i>Battery percentage remaining</i>'],
                //                          ^^ unsupported attribute?  or reporting only ?  
            ],
            supportedThermostatModes: ["off","auto", "heat", "away"/*, "emergency heat"*/],
            refresh: ["pollAqara"],
            deviceJoinName: "Aqara E1 Thermostat",
            configuration : [:]
    ],

    // Sonoff TRVZB : https://github.com/Koenkk/zigbee-herdsman-converters/blob/b89af815cf41bd309d63f3f01d352dbabcf4ebb2/src/devices/sonoff.ts#L454
    //                https://github.com/photomoose/zigbee-herdsman-converters/blob/59f927ef0f152268125426854bd65ae6b963c99a/src/devices/sonoff.ts
    //                https://github.com/Koenkk/zigbee2mqtt/issues/19269
    //                https://github.com/Koenkk/zigbee-herdsman-converters/pull/6469 
    // fromZigbee:  https://github.com/Koenkk/zigbee-herdsman-converters/blob/b89af815cf41bd309d63f3f01d352dbabcf4ebb2/src/converters/fromZigbee.ts#L44
    // toZigbee:    https://github.com/Koenkk/zigbee-herdsman-converters/blob/b89af815cf41bd309d63f3f01d352dbabcf4ebb2/src/converters/toZigbee.ts#L1516
    //
    "SONOFF_TRV"   : [
            description   : "Sonoff TRVZB",
            device        : [manufacturers: ["SONOFF"], type: "TRV", powerSource: "battery", isSleepy:false],
            capabilities  : ["ThermostatHeatingSetpoint": true, "ThermostatOperatingState": true, "ThermostatSetpoint":true, "ThermostatMode":true],

            preferences   : ["childLock":"0xFC11:0x0000", "windowOpenDetection":"0xFC11:0x6000", "frostProtectionTemperature":"0xFC11:0x6002", "minHeatingSetpoint":"0x0201:0x0015", "maxHeatingSetpoint":"0x0201:0x0016", "calibrationTemp":"0x0201:0x0010" ],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0006,0020,0201,FC57,FC11", outClusters:"000A,0019", model:"TRVZB", manufacturer:"SONOFF", deviceJoinName: "Sonoff TRVZB"] 
            ],
            commands      : ["printFingerprints":"printFingerprints", "autoPollThermostat":"autoPollThermostat", "resetStats":"resetStats", 'refresh':'refresh', "initialize":"initialize", "updateAllPreferences": "updateAllPreferences", "resetPreferencesToDefaults":"resetPreferencesToDefaults", "validateAndFixPreferences":"validateAndFixPreferences"],
            tuyaDPs       : [:],
            attributes    : [   // TODO - configure the reporting for the 0x0201:0x0000 temperature !  (300..3600)
                [at:"0x0201:0x0000",  name:'temperature',           type:"decimal", dt:"0x29", rw:"ro", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C",  description:'<i>Local temperature</i>'],
                [at:"0x0201:0x0002",  name:'occupancy',             type:"enum",    dt:"0x18", rw:"ro", min:0,    max:1,    step:1,  scale:1,    map:[0: "unoccupied", 1: "occupied"], unit:"",  description:'<i>Occupancy</i>'],
                [at:"0x0201:0x0003",  name:'absMinHeatingSetpointLimit',  type:"decimal", dt:"0x29", rw:"ro", min:4.0,  max:35.0, step:0.5, scale:100,  unit:"°C",  description:'<i>Abs Min Heat Setpoint Limit</i>'],
                [at:"0x0201:0x0004",  name:'absMaxHeatingSetpointLimit',  type:"decimal", dt:"0x29", rw:"ro", min:4.0,  max:35.0, step:0.5, scale:100,  unit:"°C",  description:'<i>Abs Max Heat Setpoint Limit</i>'],
                [at:"0x0201:0x0010",  name:'calibrationTemp',  preProc:"signedInt",     type:"decimal", dt:"0x28", rw:"rw", min:-7.0,  max:7.0, defaultValue:0.0, step:0.2, scale:10,  unit:"°C", title: "<b>Local Temperature Calibration</b>", description:'<i>Room temperature calibration</i>'],
                [at:"0x0201:0x0012",  name:'heatingSetpoint',       type:"decimal", dt:"0x29", rw:"rw", min:4.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Heating Setpoint</b>",      description:'<i>Occupied heating setpoint</i>'],
                [at:"0x0201:0x0015",  name:'minHeatingSetpoint',    type:"decimal", dt:"0x29", rw:"rw", min:4.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Min Heating Setpoint</b>", description:'<i>Min Heating Setpoint Limit</i>'],
                [at:"0x0201:0x0016",  name:'maxHeatingSetpoint',    type:"decimal", dt:"0x29", rw:"rw", min:4.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Max Heating Setpoint</b>", description:'<i>Max Heating Setpoint Limit</i>'],
                [at:"0x0201:0x001A",  name:'remoteSensing',         type:"enum",    dt:"0x18", rw:"ro", min:0,    max:1,    step:1,  scale:1,    map:[0: "false", 1: "true"], unit:"",  title: "<b>Remote Sensing<</b>", description:'<i>Remote Sensing</i>'],
                [at:"0x0201:0x001B",  name:'termostatRunningState', type:"enum",    dt:"0x30", rw:"rw", min:0,    max:2,    step:1,  scale:1,    map:[0: "off", 1: "heat", 2: "unknown"], unit:"",  description:'<i>termostatRunningState (relay on/off status)</i>'],      //  nothing happens when WRITING ????
                [at:"0x0201:0x001C",  name:'thermostatMode',        type:"enum",    dt:"0x30", rw:"rw", min:0,    max:4,    step:1,  scale:1,    map:[0: "off", 1: "auto", 2: "invalid", 3: "invalid", 4: "heat"], unit:"", title: "<b>System Mode</b>",  description:'<i>Thermostat Mode</i>'],
                [at:"0x0201:0x001E",  name:'thermostatRunMode',     type:"enum",    dt:"0x30", rw:"ro", min:0,    max:1,    step:1,  scale:1,    map:[0: "idle", 1: "heat"], unit:"", title: "<b>Thermostat Run Mode</b>",   description:'<i>Thermostat run mode</i>'],
                [at:"0x0201:0x0020",  name:'startOfWeek',           type:"enum",    dt:"0x30", rw:"ro", min:0,    max:6,    step:1,  scale:1,    map:[0: "Sun", 1: "Mon", 2: "Tue", 3: "Wed", 4: "Thu", 5: "Fri", 6: "Sat"], unit:"",  description:'<i>Start of week</i>'],
                [at:"0x0201:0x0021",  name:'numWeeklyTransitions',  type:"number",  dt:"0x20", rw:"ro", min:0,    max:255,  step:1,  scale:1,    unit:"",  description:'<i>Number Of Weekly Transitions</i>'],
                [at:"0x0201:0x0022",  name:'numDailyTransitions',   type:"number",  dt:"0x20", rw:"ro", min:0,    max:255,  step:1,  scale:1,    unit:"",  description:'<i>Number Of Daily Transitions</i>'],
                [at:"0x0201:0x0025",  name:'thermostatProgrammingOperationMode', type:"enum",  dt:"0x18", rw:"rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "mode1", 1: "mode2"], unit:"",  title: "<b>Thermostat Programming Operation Mode/b>", description:'<i>Thermostat programming operation mode</i>'],  // nothing happens when WRITING ????
                [at:"0x0201:0x0029",  name:'thermostatOperatingState', type:"enum", dt:"0x19", rw:"ro", min:0,    max:1,    step:1,  scale:1,    map:[0: "idle", 1: "heating"], unit:"",  description:'<i>termostatRunningState (relay on/off status)</i>'],   // read only!
                // https://github.com/photomoose/zigbee-herdsman-converters/blob/227b28b23455f1a767c94889f57293c26e4a1e75/src/devices/sonoff.ts 
                [at:"0x0006:0x0000",  name:'onOffReport',          type:"number",  dt: "0x10", rw: "ro", min:0,    max:255,  step:1,  scale:1,   unit:"",  description:'<i>TRV on/off report</i>'],     // read only, 00 = off; 01 - thermostat is on
                [at:"0xFC11:0x0000",  name:'childLock',             type:"enum",    dt: "0x10", rw: "rw", min:0,    max:1,  defaultValue:"0", step:1,  scale:1,   map:[0: "off", 1: "on"], unit:"",   title: "<b>Child Lock</b>",   description:'<i>Child lock<br>unlocked/locked</i>'],
                [at:"0xFC11:0x6000",  name:'windowOpenDetection',   type:"enum",    dt: "0x10", rw: "rw", min:0,    max:1,  defaultValue:"0", step:1,  scale:1,   map:[0: "off", 1: "on"], unit:"",   title: "<b>Open Window Detection</b>",   description:'<i>Automatically turns off the radiator when local temperature drops by more than 1.5°C in 4.5 minutes.</i>'],
                [at:"0xFC11:0x6002",  name:'frostProtectionTemperature', type:"decimal",  dt: "0x29", rw: "rw", min:4.0,    max:35.0,  defaultValue:7.0, step:0.5,  scale:100,   unit:"°C",   title: "<b>Frost Protection Temperature</b>",   description:'<i>Minimum temperature at which to automatically turn on the radiator, if system mode is off, to prevent pipes freezing.</i>'],
                [at:"0xFC11:0x6003",  name:'idleSteps ',            type:"number",  dt: "0x21", rw: "ro", min:0,    max:9999, step:1,  scale:1,   unit:"", description:'<i>Number of steps used for calibration (no-load steps)</i>'],
                [at:"0xFC11:0x6004",  name:'closingSteps',          type:"number",  dt: "0x21", rw: "ro", min:0,    max:9999, step:1,  scale:1,   unit:"", description:'<i>Number of steps it takes to close the valve</i>'],
                [at:"0xFC11:0x6005",  name:'valve_opening_limit_voltage',  type:"decimal",  dt: "0x21", rw: "ro", min:0,    max:9999, step:1,  scale:1000,   unit:"V", description:'<i>Valve opening limit voltage</i>'],
                [at:"0xFC11:0x6006",  name:'valve_closing_limit_voltage',  type:"decimal",  dt: "0x21", rw: "ro", min:0,    max:9999, step:1,  scale:1000,   unit:"V", description:'<i>Valve closing limit voltage</i>'],
                [at:"0xFC11:0x6007",  name:'valve_motor_running_voltage',  type:"decimal",  dt: "0x21", rw: "ro", min:0,    max:9999, step:1,  scale:1000,   unit:"V", description:'<i>Valve motor running voltage</i>'],
                [at:"0xFC11:0x6008",  name:'unknown1',              type:"number",  dt: "0x20", rw: "rw", min:0,    max:255, step:1,  scale:1,   unit:"", description:'<i>unknown1 (0xFC11:0x6008)/i>'],
                [at:"0xFC11:0x6009",  name:'heatingSetpoint_FC11',  type:"decimal",  dt: "0x29", rw: "rw", min:4.0,,    max:35.0, step:1,  scale:100,   unit:"°C", title: "<b>Heating Setpoint</b>",      description:'<i>Occupied heating setpoint</i>'],
                [at:"0xFC11:0x600A",  name:'unknown2',              type:"number",  dt: "0x29", rw: "rw", min:0,    max:9999, step:1,  scale:1,   unit:"", description:'<i>unknown2 (0xFC11:0x600A)/i>'],
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
            refresh: ["pollBatteryPercentage","pollThermostatCluster"],
            deviceJoinName: "Sonoff TRVZB",
            configuration : [:]
    ],


// BRT-100-B0
//              https://github.com/Koenkk/zigbee-herdsman-converters/blob/47f56c19a3fdec5f23e74f805ff640a931721099/src/devices/moes.ts#L282
    "MOES_BRT-100"   : [
            description   : "MOES BRT-100 TRV",
            
            device        : [models: ["TS0601"], type: "TRV", powerSource: "battery", isSleepy:false],
            capabilities  : ["ThermostatHeatingSetpoint": true, "ThermostatOperatingState": true, "ThermostatSetpoint":true, "ThermostatMode":true],
            preferences   : ["windowOpenDetection":"8", "childLock":"13", "boostTime":"103", "calibrationTemp":"105", "ecoMode":"106", "ecoTemp":"107", "minHeatingSetpoint":"109", "maxHeatingSetpoint":"108"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_b6wax7g0", deviceJoinName: "MOES BRT-100 TRV"] 
            ],
            commands      : ["autoPollThermostat":"autoPollThermostat", "sendSupportedThermostatModes":"sendSupportedThermostatModes", "setHeatingSetpoint":"setHeatingSetpoint", "resetStats":"resetStats", 'refresh':'refresh', "initialize":"initialize", "updateAllPreferences": "updateAllPreferences", "resetPreferencesToDefaults":"resetPreferencesToDefaults", "validateAndFixPreferences":"validateAndFixPreferences"],
            tuyaDPs       : [
                [dp:1,   name:'thermostatMode',     type:"enum",            rw: "rw", min:0,     max:3,    defaultValue:"1",  step:1,   scale:1,  map:[0: "auto", 1: "heat", 2: "TempHold", 3: "holidays"] ,   unit:"", title:"<b>Thermostat Mode</b>",  description:'<i>Thermostat mode</i>'], 
                [dp:2,   name:'heatingSetpoint',    type:"decimal",         rw: "rw", min:5.0,   max:45.0, defaultValue:20.0, step:1.0, scale:1,  unit:"°C",  title: "<b>Current Heating Setpoint</b>",      description:'<i>Current heating setpoint</i>'],
                [dp:3,   name:'temperature',        type:"decimal",         rw: "ro", min:-10.0, max:50.0, defaultValue:20.0, step:0.5, scale:10, unit:"°C",  description:'<i>Temperature</i>'],
                [dp:4,   name:'emergencyHeating',   type:"enum",  dt: "01", rw: "rw", min:0,     max:1 ,   defaultValue:"0",  step:1,   scale:1,  map:[0:"off", 1:"on"] ,   unit:"", title:"<b>Emergency Heating</b>",  description:'<i>Emergency heating</i>'], 
                [dp:5,   name:'emergencyHeatingTime',   type:"number",      rw: "rw", min:0,     max:720 , defaultValue:15,   step:15,  scale:1,  unit:"minutes", title:"<b>Emergency Heating Timer</b>",  description:'<i>Emergency heating timer</i>'], 
                [dp:7,   name:'workingState',       type:"enum",            rw: "rw", min:0,     max:1 ,   defaultValue:"0",  step:1,   scale:1,  map:[0:"open", 1:"closed"] ,   unit:"", title:"<bWorking State</b>",  description:'<i>working state</i>'], 
                [dp:8,   name:'windowOpenDetection', type:"enum", dt: "01", rw: "rw", min:0,     max:1 ,   defaultValue:"0",  step:1,   scale:1,  map:[0:"off", 1:"on"] ,   unit:"", title:"<b>Window Detection</b>",  description:'<i>Window detection</i>'], 
                [dp:9,   name:'windowsState',       type:"enum",            rw: "rw", min:0,     max:1 ,   defaultValue:"0",  step:1,   scale:1,  map:[0:"open", 1:"closed"] ,   unit:"", title:"<bWindow State</b>",  description:'<i>Window state</i>'], 
                [dp:13,  name:'childLock',          type:"enum",  dt: "01", rw: "rw", min:0,     max:1 ,   defaultValue:"0",  step:1,   scale:1,  map:[0:"off", 1:"on"] ,   unit:"", title:"<b>Child Lock</b>",  description:'<i>Child lock</i>'], 
                [dp:14,  name:'battery',            type:"number",          rw: "ro", min:0,     max:100,  defaultValue:100,  step:1,   scale:1,  unit:"%",          description:'<i>Battery level</i>'],
                [dp:101, name:'weeklyProgram',      type:"number",          rw: "ro", min:0,     max:9999, defaultValue:0,    step:1,   scale:1,  unit:"",          description:'<i>Weekly program</i>'],
                [dp:103, name:'boostTime',          type:"number",          rw: "rw", min:100,   max:900 , defaultValue:100,  step:1,   scale:1,  unit:"seconds", title:"<b>Boost Timer</b>",  description:'<i>Boost timer</i>'], 
                [dp:104, name:'level',              type:"number",          rw: "ro", min:0,     max:100,  defaultValue:100,  step:1,   scale:1,  unit:"%",          description:'<i>Valve level</i>'],
                [dp:105, name:'calibrationTemp',    type:"decimal",         rw: "rw", min:-9.0,  max:9.0,  defaultValue:00.0, step:1,   scale:1,  unit:"°C",  title:"<b>Calibration Temperature</b>", description:'<i>Calibration Temperature</i>'],
                [dp:106, name:'ecoMode',            type:"enum",  dt: "01", rw: "rw", min:0,     max:1 ,   defaultValue:"0",  step:1,   scale:1,  map:[0:"off", 1:"on"] ,   unit:"", title:"<b>Eco mode</b>",  description:'<i>Eco mode</i>'], 
                [dp:107, name:'ecoTemp',            type:"decimal",         rw: "rw", min:5.0,   max:35.0, defaultValue:20.0, step:1.0, scale:1,  unit:"°C",  title: "<b>Eco Temperature</b>",      description:'<i>Eco temperature</i>'],
                [dp:108, name:'maxHeatingSetpoint', type:"decimal",         rw: "rw", min:15.0,  max:45.0, defaultValue:35.0, step:1.0, scale:1,  unit:"°C",  title: "<b>Maximum Temperature</b>",      description:'<i>Maximum temperature</i>'],
                [dp:109, name:'minHeatingSetpoint', type:"decimal",         rw: "rw", min:5.0,   max:15.0, defaultValue:10.0, step:1.0, scale:1,  unit:"°C",  title: "<b>Minimum Temperature</b>",      description:'<i>Minimum temperature</i>'],
            ],
            supportedThermostatModes: ["auto", "heat", "emergency heat", "eco"],
            refresh: ["tuyaBlackMagic"],
            deviceJoinName: "MOES BRT-100 TRV",
            configuration : [:]
    ],

    "NAMRON"   : [
            description   : "NAMRON Thermostat`(not working yet)",
            device        : [manufacturers: ["NAMRON AS"], type: "TRV", powerSource: "mains", isSleepy:false],
            capabilities  : ["ThermostatHeatingSetpoint": true, "ThermostatOperatingState": true, "ThermostatSetpoint":true, "ThermostatMode":true],
            preferences   : [],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,0009,0408,0702,0B04,0B05,1000,FCCC", outClusters:"0019,1000", model:"4512749-N", manufacturer:"NAMRON AS", deviceJoinName: "NAMRON"]   // EP02: 0000,0004,0005,0201  // EPF2: 0021
            ],
            commands      : ["resetStats":"resetStats", 'refresh':'refresh', "initialize":"initialize", "updateAllPreferences": "updateAllPreferences", "resetPreferencesToDefaults":"resetPreferencesToDefaults", "validateAndFixPreferences":"validateAndFixPreferences",
                              "factoryResetThermostat":"factoryResetThermostat"
            ],
            attributes    : [
                [at:"0x0201:0x0000",  name:'temperature',              type:"decimal", dt:"0x21", rw: "ro", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", description:'<i>Measured room temperature</i>'],       // ^^^ (int16S, read-only) reportable LocalTemperature : Attribute This is room temperature, the maximum resolution this format allows is 0.01 ºC.
                [at:"0x0201:0x0001",  name:'outdoorTemperature',       type:"decimal", dt:"0x21", rw: "ro", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C",  description:'<i>Floor temperature</i>'],          // ^^^ (int16S, read-only) reportable OutdoorTemperature : This is floor temperature, the maximum resolution this format allows is 0.01 ºC.
                [at:"0x0201:0x0002",  name:'occupancy',                type:"enum",    dt:"0x20", rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",  description:'<i>Occupancy</i>'],      // ^^^ (bitmap8, read-only) Occupancy : When this flag is set as 1, it means occupied, OccupiedHeatingSetpoint will be used, otherwise UnoccupiedHeatingSetpoint will be used
                [at:"0x0201:0x0010",  name:'localTemperatureCalibration', type:"decimal", dt:"0x21", rw: "rw", min:-30.0,  max:30.0, defaultValue:0.0, step:0.5, scale:10,  unit:"°C", title: "<b>Local Temperature Calibration</b>", description:'<i>Room temperature calibration</i>'],       // ^^^ (Int8S, reportable) TODO: check dt!!!    LocalTemperatureCalibration : Room temperature calibration, range is -30-30, the maximum resolution this format allows 0.1°C. Default value: 0
                [at:"0x0201:0x0011",  name:'coolingSetpoint',          type:"decimal", dt:"0x21", rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Cooling Setpoint</b>",              description:'<i>This system is not invalid</i>'],       // not used 
                [at:"0x0201:0x0012",  name:'heatingSetpoint',          type:"decimal", dt:"0x21", rw: "rw", min:0.0,  max:40.0, defaultValue:30.0, step:0.01, scale:100,  unit:"°C", title: "<b>Current Heating Setpoint</b>",      description:'<i>Current heating setpoint</i>'],                // ^^^(int16S, reportable)  OccupiedHeatingSetpoint : Range is 0-4000,the maximum resolution this format allows is 0.01 ºC. Default is 0xbb8(30.00ºC)
                [at:"0x0201:0x0014",  name:'unoccupiedHeatingSetpoint', type:"decimal", dt:"0x21", rw: "rw", min:0.0,  max:40.0, defaultValue:30.0, step:0.01, scale:100,  unit:"°C", title: "<b>Current Heating Setpoint</b>",      description:'<i>Current heating setpoint</i>'],                // ^^^(int16S, reportable)  OccupiedHeatingSetpoint : Range is 0-4000,the maximum resolution this format allows is 0.01 ºC. Default is 0x258(6.00ºC)
                [at:"0x0201:0x001B",  name:'termostatRunningState',    type:"enum",    dt:"0x20", rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",  description:'<i>termostatRunningState (relay on/off status)</i>'],                // ^^^(Map16, read-only, reportable) HVAC relay state/ termostatRunningState Indicates the relay on/off status, here only supports bit0( Heat State)
                // ============ Proprietary Attributes: Manufacturer code 0x1224 ============
                [at:"0x0201:0x0000",  name:'lcdBrightnesss',           type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:2, defaultValue:"1",   step:1,  scale:1,    map:[0: "Low Level", 1: "Mid Level", 2: "High Level"], unit:"",  title: "<b>OLED brightness</b>",description:'<i>OLED brightness</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  OLED brightness when operate the buttons: Value=0 : Low Level Value=1 : Mid Level(default) Value=2 : High Level
                [at:"0x0201:0x1002",  name:'floorSensorType',          type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:1,    max:5, defaultValue:"1",step:1,  scale:1,    map:[1: "NTC 10K/25", 2: "NTC 15K/25", 3: "NTC 12K/25", 4: "NTC 100K/25", 5: "NTC 50K/25"], unit:"",  title: "<b>Floor Sensor Type</b>",description:'<i>Floor Sensor Type</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  TODO: check why there are 3 diferent enum groups???    FloorSenserType Value=5 : NTC 12K/25  Value=4 : NTC 100K/25 Value=3 : NTC 50K/25 Select external (Floor) sensor type: Value=1 : NTC 10K/25 (Default) Value=2 : NTC 15K/25 Value=5 : NTC 12K/25 Value=4 : NTC 100K/25 Value=3 : NTC 50K/25 Select external (Floor) sensor type: Value=1 : NTC 10K/25 (Default) Value=2 : NTC 15K/25             
                [at:"0x0201:0x1003",  name:'controlType',              type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:2, defaultValue:"0",step:1,  scale:1,    map:[0: "Room sensor", 1: "floor sensor", 2: "Room+floor sensor"], unit:"",  title: "<b>Control Type</b>",description:'<i>Control Type</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  ControlType The referring sensor for heat control: Value=0 : Room sensor(Default) Value=1 : floor sensor Value=2 : Room+floor sensor
                [at:"0x0201:0x1004",  name:'powerUpStatus',            type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:2, defaultValue:"1",   step:1,  scale:1,    map:[0: "Low Level", 1: "Mid Level", 2: "High Level"], unit:"",  title: "<b>Power Up Status</b>",description:'<i>Power Up Status</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!! PowerUpStatus Value=0 : default mode The mode after reset power of the device: Value=1 : last status before power off (Default) 
                [at:"0x0201:0x1005",  name:'floorSensorCalibration',   type:"decimal", dt:"0x21",  mfgCode:"0x1224", rw: "rw", min:-30.0,  max:30.0, defaultValue:0.0, step:0.5, scale:10,  unit:"°C", title: "<b>Floor Sensor Calibration</b>", description:'<i>Floor Sensor Calibration/i>'],                // ^^^ (Int8S, reportable) TODO: check dt!!!    FloorSenserCalibration The temp compensation for the external (floor) sensor, range is -30-30, unit is 0.1°C. default value 0
                [at:"0x0201:0x1006",  name:'dryTime',                  type:"number",  dt:"0x21",  mfgCode:"0x1224", rw: "rw", min:5,  max:100, defaultValue:5, step:1, scale:1,  unit:"minutes", title: "<b>Dry Time</b>", description:'<i>The duration of Dry Mode/i>'],                // ^^^ (Int8S, reportable) TODO: check dt!!!    DryTime The duration of Dry Mode, range is 5-100, unit is min. Default value is 5.
                [at:"0x0201:0x1007",  name:'modeAfterDry',             type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:2, defaultValue:"2",   step:1,  scale:1,    map:[0: "OFF", 1: "Manual mode", 2: "Auto mode", 3: "Away mode"], unit:"",  title: "<b>Mode After Dry</b>",description:'<i>The mode after Dry Mode</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!! ModeAfterDry The mode after Dry Mode: Value=0 : OFF Value=1 : Manual mode Value=2 : Auto mode –schedule (default) Value=3 : Away mode
                [at:"0x0201:0x1008",  name:'temperatureDisplay',       type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:1, defaultValue:"1",   step:1,  scale:1,    map:[0: "Room Temp", 1: "Floor Temp"], unit:"",  title: "<b>Temperature Display</b>",description:'<i>Temperature Display</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!! TemperatureDisplay Value=0 : Room Temp (Default) Value=1 : Floor Temp
                [at:"0x0201:0x1009",  name:'windowOpenCheck',          type:"decimal", dt:"0x21",  mfgCode:"0x1224", rw: "rw", min:0.3, max:8.0, defaultValue:0, step:0.5, scale:10,  unit:"", title: "<b>Window Open Check</b>", description:'<i>The threshold to detect open window, 0 means disabled</i>'],                // ^^^ (INT8U,reportable) TODO: check dt!!!    WindowOpenCheck The threshold to detect open window, range is 0.3-8, unit is 0.5ºC, 0 means disabled, default is 0
                [at:"0x0201:0x100A",  name:'hysterersis',              type:"decimal", dt:"0x21",  mfgCode:"0x1224", rw: "rw", min:5.0, max:20.0, defaultValue:5.0, step:0.5, scale:10,  unit:"", title: "<b>Hysterersis</b>", description:'<i>Hysterersis</i>'],                // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  Hysterersis setting, range is 5-20, unit is 0.1ºC, default value is 5 
                [at:"0x0201:0x100B",  name:'displayAutoOffEnable',     type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:1, defaultValue:"1",   step:1,  scale:1,    map:[0: "Disabled", 1: "Enabled"], unit:"",  title: "<b>Display Auto Off Enable</b>",description:'<i>Display Auto Off Enable</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  DisplayAutoOffEnable 0, disable Display Auto Off function 1, enable Display Auto Off function
                [at:"0x0201:0x2001",  name:'alarmAirTempOverValue',    type:"decimal", dt:"0x21",  mfgCode:"0x1224", rw: "rw", min:0.2, max:6.0, defaultValue:4.5, step:0.1, scale:10,  unit:"", title: "<b>Alarm Air Temp Over Value</b>", description:'<i>Alarm Air Temp Over Value, 0 means disabled,</i>'],                // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  AlarmAirTempOverValue Room temp alarm threshold, range is 0.20-60, unit is 1ºC,0 means disabled, default is 45
                [at:"0x0201:0x2002",  name:'awayModeSet',              type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:1, defaultValue:"0",   step:1,  scale:1,    map:[0: "Not away", 1: "Away"], unit:"",  title: "<b>Away Mode Set</b>",description:'<i>Away Mode Set</i>'],                // ^^^ (ENUM8,reportable) TODO: check dt!!!  Away Mode Set: Value=1: away Value=0: not away (default)
                // Command supported  !!!!!!!!!! TODO !!!!!!!!!!!! not attribute, but a command !!!!!!!!!!!!!!!!
                [cmd:"0x0201:0x0000",  name:'setpointRaiseLower',       type:"decimal", dt:"0x21", rw: "ro", min:5.0,  max:35.0, step:0.5, scale:10,  unit:"°C", description:'<i>Setpoint Raise/Lower</i>'],                // ^^^ Setpoint Raise/Lower Increase or decrease the set temperature according to current mode, unit is 0.1ºC
            ],
            refresh: ["pollThermostatCluster"],
            deviceJoinName: "NAMRON Thermostat",
            configuration : [:]
    ],

    "---"   : [
            description   : "--------------------------------------",
//            models        : [],
//            fingerprints  : [],
    ],           

    "VIRTUAL"   : [        // https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/virtualThermostat.groovy
            description   : "Virtual thermostat",
            device        : [type: "TRV", powerSource: "battery", isSleepy:false],
            capabilities  : ["ThermostatHeatingSetpoint": true, "ThermostatOperatingState": true, "ThermostatSetpoint":true, "ThermostatMode":true],
            preferences   : ["hysteresis":"hysteresis", "simulateThermostatOperatingState":"simulateThermostatOperatingState"],
            commands      : ["printFingerprints":"printFingerprints", "autoPollThermostat":"autoPollThermostat", "resetStats":"resetStats", 'refresh':'refresh', "initialize":"initialize", "updateAllPreferences": "updateAllPreferences", "resetPreferencesToDefaults":"resetPreferencesToDefaults", "validateAndFixPreferences":"validateAndFixPreferences"],
            attributes    : [
                [at:"hysteresis",      name:'hysteresis',       type:"enum",    dt:"virtual", rw:"rw",  min:0,   max:4,    defaultValue:"3",  step:1,  scale:1,  map:[0:"0.1", 1:"0.25", 2:"0.5", 3:"1", 4:"2"],   unit:"", title:"<b>Hysteresis</b>",  description:'<i>hysteresis</i>'], 
                [at:"thermostatMode",  name:'thermostatMode',   type:"enum",    dt:"virtual", rw:"rw",  min:0,    max:5,    defaultValue:"0",  step:1,  scale:1,  map:[0: "heat", 1: "auto", 2: "eco", 3:"emergency heat", 4:"off", 5:"cool"],            unit:"", title: "<b>Thermostat Mode</b>",           description:'<i>Thermostat Mode</i>'],
                [at:"heatingSetpoint", name:'heatingSetpoint',  type:"decimal", dt:"virtual", rw: "rw", min:5.0, max:45.0, defaultValue:20.0, step:0.5, scale:1,  unit:"°C",  title: "<b>Current Heating Setpoint</b>",      description:'<i>Current heating setpoint</i>'],
                [at:"coolingSetpoint", name:'coolingSetpoint',  type:"decimal", dt:"virtual", rw: "rw", min:5.0, max:45.0, defaultValue:20.0, step:0.5, scale:1,  unit:"°C",  title: "<b>Current Cooling Setpoint</b>",      description:'<i>Current cooling setpoint</i>'],
                [at:"thermostatOperatingState",  name:'thermostatOperatingState', type:"enum", dt:"virtual", rw:"ro", min:0,    max:1,    step:1,  scale:1,    map:[0: "idle", 1: "heating"], unit:"",  description:'<i>termostatRunningState (relay on/off status)</i>'],   // read only!
                [at:"simulateThermostatOperatingState",  name:'simulateThermostatOperatingState', type:"enum",    dt: "virtual", rw: "rw", min:0,    max:1,   defaultValue:"0",  step:1,  scale:1,    map:[0: "off", 1: "on"], unit:"",         title: "<b>Simulate Thermostat Operating State</b>",      \
                             description:'<i>Simulate the thermostat operating state<br>* idle - when the temperature is less than the heatingSetpoint<br>* heat - when the temperature is above tha heatingSetpoint</i>'],
               

            ],
            refresh: ["dummy","dummy"],
            supportedThermostatModes: ["auto", "heat", "emergency heat", "eco", "off", "cool"],
            deviceJoinName: "Virtual thermostat",
            configuration : [:]
    ],

                // TODO = check constants! https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/lib/constants.ts#L17 
    "UNKNOWN"   : [
            description   : "GENERIC TRV",
            device        : [type: "TRV", powerSource: "battery", isSleepy:false],
            capabilities  : ["ThermostatHeatingSetpoint": true, "ThermostatOperatingState": true, "ThermostatSetpoint":true, "ThermostatMode":true],
            preferences   : [],
            fingerprints  : [],
            commands      : ["resetStats":"resetStats", 'refresh':'refresh', "initialize":"initialize", "updateAllPreferences": "updateAllPreferences", "resetPreferencesToDefaults":"resetPreferencesToDefaults", "validateAndFixPreferences":"validateAndFixPreferences"],
            tuyaDPs       : [:],
            attributes    : [
                [at:"0x0201:0x0000",  name:'temperature',              type:"decimal", dt: "0x21", rw: "ro", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Temperature</b>",                   description:'<i>Measured temperature</i>'],
                [at:"0x0201:0x0011",  name:'coolingSetpoint',          type:"decimal", dt: "0x21", rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Cooling Setpoint</b>",              description:'<i>cooling setpoint</i>'],
                [at:"0x0201:0x0012",  name:'heatingSetpoint',          type:"decimal", dt: "0x21", rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Current Heating Setpoint</b>",      description:'<i>Current heating setpoint</i>'],
                [at:"0x0201:0x001C",  name:'mode',                     type:"enum",    dt: "0x20", rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b> Mode</b>",                   description:'<i>System Mode ?</i>'],
                [at:"0x0201:0x001E",  name:'thermostatRunMode',        type:"enum",    dt: "0x20", rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatRunMode</b>",                   description:'<i>thermostatRunMode</i>'],
                [at:"0x0201:0x0020",  name:'battery2',                 type:"number",  dt: "0x21", rw: "ro", min:0,    max:100,  step:1,  scale:1,    unit:"%",  description:'<i>Battery percentage remaining</i>'],
                [at:"0x0201:0x0023",  name:'thermostatHoldMode',       type:"enum",    dt: "0x20", rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatHoldMode</b>",                   description:'<i>thermostatHoldMode</i>'],
                [at:"0x0201:0x0029",  name:'thermostatOperatingState', type:"enum",    dt: "0x20", rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatOperatingState</b>",                   description:'<i>thermostatOperatingState</i>'],
            ],
            refresh: ["pollThermostatCluster"],
            deviceJoinName: "UNKWNOWN TRV",
            configuration : [:]
    ]

]

// TODO - use for all events sent by this driver !!
void thermostatEvent(eventName, value, raw) {
    def descriptionText = "${eventName} is ${value}"
    Map eventMap = [name: eventName, value: value, descriptionText: descriptionText, type: "physical"]
    if (state.states["isRefresh"] == true) {
        eventMap.descriptionText += " [refresh]"
        eventMap.isStateChange = true   // force event to be sent
    }
    if (logEnable) { eventMap.descriptionText += " (raw ${raw})" }
    sendEvent(eventMap)
    logInfo "${eventMap.descriptionText}"
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
        logWarn "parseFC11ClusterThermostat: received unknown Thermostat cluster (0xFCC0) attribute 0x${descMap.attrId} (value ${descMap.value})"
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
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value/1000}V (raw=${value})"
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
                device.updateDataValue("aqaraVersion", swBuild)
                break
            case 0x0a:
                String nwk = intToHexStr(value as Integer,2)
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
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value/100} (raw ${value})"    // Aqara TVOC
                break
            case 0x65:
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0x66:
                logDebug "xiaomi decode E1 thermostat temperature tag: 0x${intToHexStr(tag, 1)}=${value}"
                handleTemperatureEvent(value/100.0)
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
void parseThermostatClusterThermostat(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    //logTrace "zigbee received Thermostat cluster (0x0201) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    Boolean result = processClusterAttributeFromDeviceProfile(descMap)
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

def parseFC11ClusterThermostat(descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "zigbee received Thermostat 0xFC11 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    Boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib 
    if ( result == false ) {
        logWarn "parseFC11ClusterThermostat: received unknown Thermostat cluster (0xFC11) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

//  setHeatingSetpoint thermostat capability standard command
//  1°C steps. (0.5°C setting on the TRV itself, rounded for zigbee interface)
//
def setHeatingSetpoint( temperature ) {
    logTrace "setHeatingSetpoint(${temperature}) called!"
    def previousSetpoint = device.currentState('heatingSetpoint')?.value ?: 0
    double tempDouble
    //logDebug "setHeatingSetpoint temperature = ${temperature}  as int = ${temperature as int} (previousSetpointt = ${previousSetpoint})"
    if (true) {
        //logDebug "0.5 C correction of the heating setpoint${temperature}"
        tempDouble = safeToDouble(temperature)
        tempDouble = Math.round(tempDouble * 2) / 2.0
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
    def maxTemp = settings?.maxHeatingSetpoint ?: 50
    def minTemp = settings?.minHeatingSetpoint ?: 5
    if (tempDouble > maxTemp ) tempDouble = maxTemp
    if (tempDouble < minTemp) tempDouble = minTemp
    tempDouble = tempDouble.round(1)

    logDebug "calling sendAttribute heatingSetpoint ${tempDouble}"
    sendAttribute("heatingSetpoint", tempDouble)
}

void sendHeatingSetpointEvent(temperature) {
    tempDouble = safeToDouble(temperature)
    Map eventMap = [name: "heatingSetpoint",  value: tempDouble, unit: "\u00B0"+"C"]
    eventMap.descriptionText = "heatingSetpoint is ${tempDouble}"
    if (state.states["isRefresh"] == true) {
        eventMap.descriptionText += " [refresh]"
        eventMap.isStateChange = true   // force event to be sent
    }
    sendEvent(eventMap)
    if (eventMap.descriptionText != null) { logInfo "${eventMap.descriptionText}" }
/*
    eventMap = [name: "thermostatSetpoint", value: tempDouble, unit: "\u00B0"+"C"]
    eventMap.descriptionText = null
*/  
    eventMap.name = "thermostatSetpoint"
    logDebug "sending event ${eventMap}"
    sendEvent(eventMap)
    updateDataValue("lastRunningMode", "heat")
}

// thermostat capability standard command
// do nothing in TRV - just send an event
def setCoolingSetpoint(temperature) {
    logDebug "setCoolingSetpoint(${temperature}) called!"
    temperature = Math.round(temperature * 2) / 2
    String descText = "coolingSetpoint is set to ${temperature} \u00B0C"
    sendEvent(name: "coolingSetpoint", value: temperature, unit: "\u00B0"+"C", descriptionText: descText, isDigital:true)
    logInfo "${descText}"
}


def setThermostatMode(requestedMode) {
    String mode = requestedMode
    List<String> cmds = []
    Boolean result = false
    logDebug "setThermostatMode: sending setThermostatMode(${mode})"

    // some TRVs require some checks and additional commands to be sent before setting the mode
    def currentMode = device.currentValue('thermostatMode')
    logDebug "setThermostatMode: currentMode = ${currentMode}, switching to ${mode} ..."

    switch(mode) {
        case "heat":
        case "auto":
            if (device.currentValue('ecoMode') == 'on') {
                logInfo "setThermostatMode: pre-processing: switching first the eco mode off"
                sendAttribute("ecoMode", 0)
            }
            if (device.currentValue('emergencyHeating') == 'on') {
                logInfo "setThermostatMode: pre-processing: switching first the emergencyHeating mode off"
                sendAttribute("emergencyHeating", 0)
            }
            break
        case "cool":        // TODO !!!!!!!!!!
            if (!("cool" in DEVICE.supportedThermostatModes)) {
                // replace cool with 'eco' mode, if supported by the device
                if ("eco" in DEVICE.supportedThermostatModes) {
                    logInfo "setThermostatMode: pre-processing: switching to eco mode instead"
                    mode = "eco"
                    break
                }
                else if ("off" in DEVICE.supportedThermostatModes) {
                    logInfo "setThermostatMode: pre-processing: switching to off mode instead"
                    mode = "off"
                    break
                }
                else if (device.currentValue('ecoMode') != null) {
                    // BRT-100 has a dediceted 'ecoMode' command   // TODO - check how to switch BRT-100 low temp protection mode (5 degrees) ?
                    logInfo "setThermostatMode: pre-processing: setting eco mode on (${settings.ecoTemp} &degC)"
                    sendAttribute("ecoMode", 1)
                }
                else {
                    logWarn "setThermostatMode: pre-processing: switching to 'cool' mode is not supported by this device!"
                    return
                }
            
            }
            break
        case "emergency heat":
            if (device.currentValue('emergencyHeating') != null)  {
                // BRT-100 has a dedicated 'emergencyHeating' command
                logInfo "setThermostatMode: setting emergency heat mode on (${settings.emergencyHeatingTime} minutes)"
                sendAttribute("emergencyHeating", 1)
                return
            }
            break
        case 'eco':
            if (device.currentValue('ecoMode') != null)  {
                logDebug "setThermostatMode: pre-processing: switching the eco mode on"
                sendAttribute("ecoMode", 1)
                return
            }
            break
        case 'off':     // TODO 
            // if systemMode attribute exists, set it to 'off'  (Aqara E1)
            def sysMode = device.currentValue('systemMode')     // off or on
            if (sysMode != null) {  // !!!!!!!!!!! Patch for Aqara E1 !
                if ( true /*sysMode != 'off'*/) {
                    logInfo "setThermostatMode: pre-processing: setting systemMode to 'off'"
                    // get the key of the 'off' value
                    def key = SystemModeOpts.options.find { key, value -> value == 'off' }.key
                    sendAttribute("systemMode", key)
                }
                else {
                    logInfo "setThermostatMode: pre-processing: systemMode is already 'off'"
                }
                return
            }
            logDebug "setThermostatMode: pre-processing: no pre-processing for mode ${mode}"
            break
        default:
            logWarn "setThermostatMode: pre-processing: unknown mode ${mode}"
            break
    }

    // try using the standard thermostat capability to switch to the selected new mode
    result = sendAttribute("thermostatMode", mode)
    logTrace "setThermostatMode: sendAttribute returned ${result}"
    if (result == true) { return }

    // post-process mode switching for some TRVs
    switch(mode) {
        case 'cool':
        case 'heat':
        case 'auto':
        case 'off':
        case 'emergency heat':
            logTrace "setThermostatMode: post-processing: no post-processing required for mode ${mode}"        
            break
        case 'emergency heat':
            logInfo "setThermostatMode: post-processing: setting emergency heat mode on (${settings.emergencyHeatingTime} minutes)"
            sendAttribute("emergencyHeating", 1)
            break
            /*
        case 'eco':
            logDebug "setThermostatMode: post-processing: switching the eco mode on"
            sendAttribute("ecoMode", 1)
            break
            */
        default:
            logWarn "setThermostatMode: post-processing: unsupported thermostat mode '${mode}'"
            break
    }
    return
}

def thermostatOff() { setThermostatMode("off") }    // invoked from the common library
def thermostatOn()  { setThermostatMode("heat") }   // invoked from the common library

def heat() { setThermostatMode("heat") }
def auto() { setThermostatMode("auto") }
def cool() { setThermostatMode("cool") }
def emergencyHeat() { setThermostatMode("emergency heat") }

def setThermostatFanMode(fanMode) { sendEvent(name: "thermostatFanMode", value: "${fanMode}", descriptionText: getDescriptionText("thermostatFanMode is ${fanMode}")) }
def fanAuto() { setThermostatFanMode("auto") }
def fanCirculate() { setThermostatFanMode("circulate") }
def fanOn() { setThermostatFanMode("on") }


void sendSupportedThermostatModes() {
    def supportedThermostatModes = []
    supportedThermostatModes = ["off", "heat", "auto", "emergency heat"]
    if (DEVICE.supportedThermostatModes != null) {
        supportedThermostatModes = DEVICE.supportedThermostatModes
    }
    else {
        logWarn "sendSupportedThermostatModes: DEVICE.supportedThermostatModes is not set!"
    }
    logInfo "supportedThermostatModes: ${supportedThermostatModes}"
    sendEvent(name: "supportedThermostatModes", value:  JsonOutput.toJson(supportedThermostatModes), isStateChange: true)
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

def dummy() {
    logDebug "dummy() called!"
    return []
}

// TODO - configure in the deviceProfile 
def pollThermostatCluster()
{
    return  zigbee.readAttribute(0x0201, [0x0000, 0x0012, 0x001B, 0x001C, 0x0029], [:], delay=3500)      // 0x0000=local temperature, 0x0012=heating setpoint, 0x001B=controlledSequenceOfOperation, 0x001C=system mode (enum8 )   
}

// TODO - configure in the deviceProfile 
def pollAqara()
{
    return  zigbee.readAttribute(0x0201, [0x0000, 0x0012, 0x001B, 0x001C], [:], delay=3500)      // 0x0000=local temperature, 0x0012=heating setpoint, 0x001B=controlledSequenceOfOperation, 0x001C=system mode (enum8 )   
}

// TODO - configure in the deviceProfile 
def pollBatteryPercentage()
{
    return zigbee.readAttribute(0x0001, 0x0021, [:], delay=200)                          // battery percentage 
}


/**
 * Scheduled job for polling device specific attribute(s)
 */
void autoPollThermostat() {
    logDebug "autoPollThermostat()..."
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
    ArrayList<String> cmds = []
    logDebug "updatedThermostat: ..."
    //
    if (settings?.forcedProfile != null) {
        //logDebug "current state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${getProfileKey(settings?.forcedProfile)}"
        if (getProfileKey(settings?.forcedProfile) != state.deviceProfile) {
            logWarn "changing the device profile from ${state.deviceProfile} to ${getProfileKey(settings?.forcedProfile)}"
            state.deviceProfile = getProfileKey(settings?.forcedProfile)
            //initializeVars(fullInit = false) 
            initVarsThermostat(fullInit = false)
            resetPreferencesToDefaults(debug=true)
            logInfo "press F5 to refresh the page"
        }
    }
    else {
        logDebug "forcedProfile is not set"
    }    
    final int pollingInterval = (settings.temperaturePollingInterval as Integer) ?: 0
    if (pollingInterval > 0) {
        logInfo "updatedThermostat: scheduling temperature polling every ${pollingInterval} seconds"
        scheduleThermostatPolling(pollingInterval)
    }
    else {
        unScheduleThermostatPolling()
        logInfo "updatedThermostat: thermostat polling is disabled!"
    }
    // Itterates through all settings
    logDebug "updatedThermostat: updateAllPreferences()..."
    /*cmds =*/ updateAllPreferences()     
    //
    /*
    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }    
    */
}

def refreshAqaraE1() {
    List<String> cmds = []
    //cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)                                         // battery voltage (E1 does not send percentage)
    cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0011, 0x0012, 0x001B, 0x001C], [:], delay=3500)       // 0x0000=local temperature, 0x0011=cooling setpoint, 0x0012=heating setpoint, 0x001B=controlledSequenceOfOperation, 0x001C=system mode (enum8 )       
    cmds += zigbee.readAttribute(0xFCC0, [0x0271, 0x0272, 0x0273, 0x0274, 0x0275, 0x0277, 0x0279, 0x027A, 0x027B, 0x027E], [mfgCode: 0x115F], delay=3500)       
    cmds += zigbee.readAttribute(0xFCC0, 0x040a, [mfgCode: 0x115F], delay=500)       
    // stock Generic Zigbee Thermostat Refresh answer:
    // raw:F669010201441C0030011E008600000029640A2900861B0000300412000029540B110000299808, dni:F669, endpoint:01, cluster:0201, size:44, attrId:001C, encoding:30, command:01, value:01, clusterInt:513, attrInt:28, additionalAttrs:[[status:86, attrId:001E, attrInt:30], [value:0A64, encoding:29, attrId:0000, consumedBytes:5, attrInt:0], [status:86, attrId:0029, attrInt:41], [value:04, encoding:30, attrId:001B, consumedBytes:4, attrInt:27], [value:0B54, encoding:29, attrId:0012, consumedBytes:5, attrInt:18], [value:0898, encoding:29, attrId:0011, consumedBytes:5, attrInt:17]]
    // conclusion : binding and reporting configuration for this Aqara E1 thermostat does nothing... We need polling mechanism for faster updates of the internal temperature readings.
    return cmds
}

// TODO - not actually used! pollAqara is called instead! TODO !
def refreshThermostat() {
    // state.states["isRefresh"] = true is already set in the commonLib
    List<String> cmds = []
    setRefreshRequest()    
    if (DEVICE.refresh != null && DEVICE.refresh != []) {
        logDebug "refreshThermostat: calling DEVICE.refresh methods: ${DEVICE.refresh}"
        DEVICE.refresh.each { 
            logTrace "refreshThermostat: calling ${it}()"
            cmds += "${it}"()
        }
        return cmds
    }
    else {
        logDebug "refreshThermostat: no refresh methods defined for device profile ${getDeviceGroup()}"
    }
    if (cmds == []) { cmds = ["delay 299"] }
    logDebug "refreshThermostat: ${cmds} "
    return cmds
}

def configureThermostat() {
    List<String> cmds = []
    // TODO !!
    logDebug "configureThermostat() : ${cmds}"
    if (cmds == []) { cmds = ["delay 299"] }    // no , 
    return cmds    
}


// called from initializeDevice in the commonLib code
def initializeDeviceThermostat()
{
    List<String> cmds = []
    int intMinTime = 300
    int intMaxTime = 600    // report temperature every 10 minutes !

    logDebug "configuring cluster 0x0201 ..."
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0201 {${device.zigbeeId}} {}", "delay 251", ]
    //cmds += zigbee.configureReporting(0x0201, 0x0012, 0x29, intMinTime as int, intMaxTime as int, 0x01, [:], delay=541)
    //cmds += zigbee.configureReporting(0x0201, 0x0000, 0x29, 20, 120, 0x01, [:], delay=542)

    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0012 0x29 1 600 {}", "delay 551", ]
    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0000 0x29 20 300 {}", "delay 551", ]
    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x001C 0x30 1 600 {}", "delay 551", ]
    
    cmds +=  zigbee.reportingConfiguration(0x0201, 0x0012, [:], 551)    // read it back - doesn't work
    cmds +=  zigbee.reportingConfiguration(0x0201, 0x0000, [:], 552)    // read it back - doesn't wor
    cmds +=  zigbee.reportingConfiguration(0x0201, 0x001C, [:], 552)    // read it back - doesn't wor


    logDebug "initializeThermostat() : ${cmds}"
    if (cmds == []) { cmds = ["delay 299",] }
    return cmds        
}


void initVarsThermostat(boolean fullInit=false) {
    logDebug "initVarsThermostat(${fullInit})"
    if (state.deviceProfile == null) {
        setDeviceNameAndProfile()               // in deviceProfileiLib.groovy
    }

    if (fullInit == true || state.lastThermostatMode == null) state.lastThermostatMode = "unknown"
    if (fullInit == true || state.lastThermostatOperatingState == null) state.lastThermostatOperatingState = "unknown"
    if (fullInit || settings?.temperaturePollingInterval == null) device.updateSetting('temperaturePollingInterval', [value: TrvTemperaturePollingIntervalOpts.defaultValue.toString(), type: 'enum'])

    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
    //    
    
}

// called from initializeVars() in the main code ...
void initEventsThermostat(boolean fullInit=false) {
    if (fullInit==true) {
        String descText = "inital attribute setting"
        sendSupportedThermostatModes()
        sendEvent(name: "supportedThermostatFanModes", value: JsonOutput.toJson(["on", "auto"]), isStateChange: true)    
        sendEvent(name: "thermostatMode", value: "heat", isStateChange: true, description: descText)
        state.lastThermostatMode = "heat"
        sendEvent(name: "thermostatFanMode", value: "auto", isStateChange: true, description: descText)
        state.lastThermostatOperatingState = "idle"
        sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true, description: descText)
        sendEvent(name: "thermostatSetpoint", value:  12.3, unit: "\u00B0"+"C", isStateChange: true, description: descText)        // Google Home compatibility
        sendEvent(name: "heatingSetpoint", value: 12.3, unit: "\u00B0"+"C", isStateChange: true, description: descText)
        sendEvent(name: "coolingSetpoint", value: 34.5, unit: "\u00B0"+"C", isStateChange: true, description: descText)
        sendEvent(name: "temperature", value: 23.4, unit: "\u00B0"+"C", isStateChange: true, description: descText)    
        updateDataValue("lastRunningMode", "heat")    
    }
    else {
        logDebug "initEventsThermostat: fullInit = false"
    }
}

private getDescriptionText(msg) {
    def descriptionText = "${device.displayName} ${msg}"
    if (settings?.txtEnable) log.info "${descriptionText}"
    return descriptionText
}

// called from processFoundItem  (processTuyaDPfromDeviceProfile and ) processClusterAttributeFromDeviceProfile in deviceProfileLib when a Zigbee message was found defined in the device profile map
//
// (works for BRT-100, Sonoff TRVZV) 
//
def processDeviceEventThermostat(name, valueScaled, unitText, descText) {
    logTrace "processDeviceEventThermostat(${name}, ${valueScaled}) called"
    Map eventMap = [name: name, value: valueScaled, unit: unitText, descriptionText: descText, type: "physical", isStateChange: true]
    switch (name) {
        case "temperature" :
            handleTemperatureEvent(valueScaled as Float)
            break
        case "humidity" :
            handleHumidityEvent(valueScaled)
            break
        case "systemMode" : // Aqara E1 
            sendEvent(eventMap)
            logInfo "${descText}"
            if (valueScaled == "on") {  // should be initialized with 'unknown' value
                sendEvent(name: "thermostatMode", value: "heat", isStateChange: true, description: "TRV systemMode is on")  // TODO - send the last mode instead of 'heat' ?
            }
            else {
                sendEvent(name: "thermostatMode", value: "off", isStateChange: true, description: "TRV systemMode is off")
            }
            break
        case "ecoMode" :    // BRT-100 - simulate OFF mode ?? or keep the ecoMode on ?
            sendEvent(eventMap)
            logInfo "${descText}"
            if (valueScaled == "on") {  // ecoMode is on
                sendEvent(name: "thermostatMode", value: "eco", isStateChange: true, description: "BRT-100 ecoMode is on")
                sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true, description: "BRT-100 ecoMode is on")
            }
            else {
                sendEvent(name: "thermostatMode", value: "heat", isStateChange: true, description: "BRT-100 ecoMode is off")
            }
            break
            
        case "emergencyHeating" :   // BRT-100
            sendEvent(eventMap)
            logInfo "${descText}"
            if (valueScaled == "on") {  // the valve shoud be completely open, however the level and the working states are NOT updated! :( 
                sendEvent(name: "thermostatMode", value: "emergency heat", isStateChange: true, description: "BRT-100 emergencyHeating is on")
                sendEvent(name: "thermostatOperatingState", value: "heating", isStateChange: true, description: "BRT-100 emergencyHeating is on")
            }
            else {
                sendEvent(name: "thermostatMode", value: "heat", isStateChange: true, description: "BRT-100 emergencyHeating is off")
            }
            break
        case "level" :      // BRT-100
            sendEvent(eventMap)
            logInfo "${descText}"
            if (valueScaled == 0) {  // the valve is closed
                sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true, description: "BRT-100 valve is closed")
            }
            else {
                sendEvent(name: "thermostatOperatingState", value: "heating", isStateChange: true, description: "BRT-100 valve is open %{valueScaled} %")
            }
            break
        case "workingState" :      // BRT-100
            sendEvent(eventMap)
            logInfo "${descText}"
            if (valueScaled == "closed") {  // the valve is closed
                sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true, description: "BRT-100 workingState is closed")
            }
            else {
                sendEvent(name: "thermostatOperatingState", value: "heating", isStateChange: true, description: "BRT-100 workingState is open")
            }
            break
        default :
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: "physical", isStateChange: true)    // attribute value is changed - send an event !
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
def factoryResetThermostat() {
    logDebug "factoryResetThermostat() called!"
    List<String> cmds = []
    // TODO
    logWarn "factoryResetThermostat: NOT IMPLEMENTED"
    if (cmds == []) { cmds = ["delay 299"] }
    return cmds
}

// ========================================= Virtual thermostat functions  =========================================


def setTemperature(temperature) {
    logDebug "setTemperature(${temperature}) called!"
    if (isVirtual()) {
        temperature = Math.round(temperature * 2) / 2
        String descText = "temperature is set to ${temperature} \u00B0C"
        sendEvent(name: "temperature", value: temperature, unit: "\u00B0"+"C", descriptionText: descText, isDigital:true)
        logInfo "${descText}"
    }
    else {
        logWarn "setTemperature: not a virtual thermostat!"
    }
}



// ========================================= end of the Virtual thermostat functions  =========================================


def testT(par) {
    /*
    def descMap = [raw:"3A870102010A120029C409", dni:"3A87", endpoint:"01", cluster:"0201", size:"0A", attrId:"0012", encoding:"29", command:"0A", value:"09C5", clusterInt:513, attrInt:18]
    log.trace "testT(${descMap})"
    def result = processClusterAttributeFromDeviceProfile(descMap)
    log.trace "result=${result}"
    */
    /*
    List<String> cmds = []
    cmds = zigbee.readAttribute(0xFC11, [0x6003, 0x6004, 0x6005, 0x6006, 0x6007], [:], delay=300) 
    sendZigbeeCommands(cmds)
    */

    log.trace "testT(${par}) : DEVICE.preferences = ${DEVICE.preferences}"
    def result
    if (DEVICE != null && DEVICE.preferences != null && DEVICE.preferences != [:]) {
        (DEVICE.preferences).each { key, value ->
            log.trace "testT: ${key} = ${value}"
            result = inputIt(key, debug=true)
            logDebug "inputIt: ${result}"
        }    
    }

}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////


