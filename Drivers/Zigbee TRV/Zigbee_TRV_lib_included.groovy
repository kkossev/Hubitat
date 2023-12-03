/**
 *  Zigbee TRV - Device Driver for Hubitat Elevation
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
 *
 *                                   TODO: Aqara TRV refactoring : done ? publish the driver.
 *                                   TODO: Aqara TRV refactoring : add 'defaults' in the device profile to set up the systemMode initial value as 'unknown'
 *                                   TODO: remove (raw:) when debug is off
 *                                   TODO: BRT-100 dev:32912023-12-02 14:10:56.995debugBRT-100 TRV DEV preference 'ecoMode' value [1] differs from the new scaled value 1 (clusterAttribute raw value 1)
 *                                   TODO: BRT-100 dev:32912023-12-02 14:10:56.989debugBRT-100 TRV DEV compareAndConvertTuyaToHubitatPreferenceValue: preference = [1] type=enum foundItem=ecoMode isEqual=false tuyaValueScaled=1 (scale=1) fncmd=1
 *                                   TODO: BRT-100 - after emergency heat, the mode was set to 'auto' (not 'heat') !
 *                                   TODO: prevent from showing "invalid enum parameter emergency heat. value must be one of [0, 1, 2, 3, 4]"; also for  invalid enum parameter cool. *                                   TODO: prevent from showing "invalid enum parameter emergency heat. value must be one of [0, 1, 2, 3, 4]"; also for  invalid enum parameter cool. *                                   TODO: prevent from showing "invalid enum parameter emergency heat. value must be one of [0, 1, 2, 3, 4]"; also for  invalid enum parameter cool. 
 *                                   TODO: add [refresh] for battery heatingSetpoint thermostatOperatingState events and logs
 *                                   TODO: cleanup trace and debug logs  invalid enum parameter emergency heat. value must be one of [0, 1, 2, 3, 4]
 *                                   TODO: hide the maxTimeBetweenReport preferences (not used here)
 *                                   TODO: prepare for publishing the first version of this driver w/ Sonoff support
 *                                   TODO: option to disale the Auto mode ! (like in the wall thermostat driver)
 *                                   TODO: allow NULL parameters default values in the device profiles
 *                                   TODO: autoPollThermostat: no polling for device profile UNKNOWN
 *                                   TODO: Sonoff - add 'emergency heat' simulation ?  ( +timer ?)
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
 *                                   TODO: add VIRTUAL thermostat
 *                                   TODO: Aqara TRV refactoring : 'cool' and 'emergency heat' and 'eco' modes to return meaningfull error message (check in the device profile if this mode is supported)
 *                                   TODO: Aqara TRV refactoring : simulate the 'emergency heat' mode by setting maxTemp and when off - restore the previous temperature 
 *                                   TODO: Aqara TRV refactoring : calibration as a command ! 
 *                                   TODO: Aqara TRV refactoring : physical vs digital events ?
 *                                   TODO: Aqara E1 external sensor
 */

static String version() { "3.0.3" }
static String timeStamp() {"2023/12/03 7:42 PM"}

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





metadata {
    definition (
        name: 'Zigbee TRVs and Thermostats',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee%20TRV/Zigbee_TRV_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true 
    ) {    
        
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

    // Aqara E1 thermostat attributes
    // TODO - add all other models attributes possible values
    // BRT-100 attributes
    attribute 'battery', "number"                               // Aqara, BRT-100
    attribute 'boostTime', "number"                             // BRT-100
    attribute 'calibrationTemp', "number"                       // BRT-100, Sonoff
    attribute 'childLock', "enum", ["off", "on"]                // BRT-100, Aqara E1, Sonoff
    attribute 'ecoMode', "enum", ["off", "on"]                  // BRT-100
    attribute 'ecoTemp', "number"                               // BRT-100
    attribute 'emergencyHeating', "enum", ["off", "on"]         // BRT-100
    attribute 'emergencyHeatingTime', "number"                  // BRT-100
    attribute 'frostProtectionTemperature', "number"            // Sonoff
    attribute 'level', "number"                                 // BRT-100          
    attribute 'minHeatingSetpoint', "number"                    // BRT-100, Sonoff
    attribute 'maxHeatingSetpoint', "number"                    // BRT-100, Sonoff
    //attribute 'trvMode', "enum",  ["auto", "manual", "TempHold", "holidays"]        // BRT-100
    attribute 'weeklyProgram', "number"                         // BRT-100
    attribute 'windowOpenDetection', "enum", ["off", "on"]      // BRT-100, Aqara E1, Sonoff
    attribute 'windowsState', "enum", ["open", "closed"]        // BRT-100, Aqara E1
    attribute 'workingState', "enum", ["open", "closed"]        // BRT-100 

    // Aqaura E1 attributes
    attribute "systemMode", 'enum', SystemModeOpts.options.values() as List<String>            // 'off','on'
    attribute "preset", 'enum', PresetOpts.options.values() as List<String>                     // 'manual','auto','away'
    attribute "valveDetection", 'enum', ValveDetectionOpts.options.values() as List<String>     // 'off','on'
    attribute "valveAlarm", 'enum', ValveAlarmOpts.options.values() as List<String>             // 'false','true'
    attribute "awayPresetTemperature", 'number'
    attribute "calibrated", 'enum', CalibratedOpts.options.values() as List<String>
    attribute "sensor", 'enum', SensorOpts.options.values() as List<String>

    //command "preset", [[name:"select preset option", type: "ENUM",   constraints: ["--- select ---"]+PresetOpts.options.values() as List<String>]]

    command "setThermostatMode", [[name: "thermostat mode (not all are available!)", type: "ENUM", constraints: ["--- select ---"]+AllThermostatModesOpts.options.values() as List<String>]]

    if (_DEBUG) { command "testT", [[name: "testT", type: "STRING", description: "testT", defaultValue : ""]]  }

    // TODO - add Sonoff TRVZB fingerprint

    //fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,FCC0,000A,0201", outClusters:"0003,FCC0,0201", model:"lumi.airrtc.agl001", manufacturer:"LUMI", deviceJoinName: "Aqara E1 Thermostat"     // model: 'SRTS-A01'
    // fingerprints are inputed from the deviceProfile maps

    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/6339b6034de34f8a633e4f753dc6e506ac9b001c/src/devices/xiaomi.ts#L3197
    // https://github.com/Smanar/deconz-rest-plugin/blob/6efd103c1a43eb300a19bf3bf3745742239e9fee/devices/xiaomi/xiaomi_lumi.airrtc.agl001.json 
    // https://github.com/dresden-elektronik/deconz-rest-plugin/issues/6351
    }
    
    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
        if (advancedOptions == true) {
            input name: 'temperaturePollingInterval', type: 'enum', title: '<b>Temperature polling interval</b>', options: TemperaturePollingIntervalOpts.options, defaultValue: TemperaturePollingIntervalOpts.defaultValue, required: true, description: '<i>Changes how often the hub will poll the TRV for faster temperature reading updates.</i>'
        }
    }
    
}

//def isAqaraTRV()                     { return getDeviceGroup().contains("AQARA_E1_TRV") }
//def isBRT100TRV()                    { return getDeviceGroup().contains("MOES_BRT-100") }
//def isSonoffTRV()                    { return getDeviceGroup().contains("SONOFF_TRV") }



// TODO = check constants! https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/lib/constants.ts#L17 

@Field static final Map TemperaturePollingIntervalOpts = [
    defaultValue: 600,
    options     : [0: 'Disabled', 60: 'Every minute (not recommended)', 120: 'Every 2 minutes', 300: 'Every 5 minutes', 600: 'Every 10 minutes', 900: 'Every 15 minutes', 1800: 'Every 30 minutes', 3600: 'Every 1 hour']
]

@Field static final Map AllThermostatModesOpts = [
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
@Field static final Map WindowDetectionOpts = [   // window_detection   TODO - remove it !!
    defaultValue: 1,
    options     : [0: 'off', 1: 'on']
]
@Field static final Map ValveDetectionOpts = [    // valve_detection    TODO - remove it !!
    defaultValue: 1,
    options     : [0: 'off', 1: 'on']
]
@Field static final Map ValveAlarmOpts = [    // valve_alarm     TODO - remove it !!
    defaultValue: 1,
    options     : [0: false, 1: true]
]
@Field static final Map ChildLockOpts = [    // child_lock    TODO - remove it !!
    defaultValue: 1,
    options     : [0: 'unlock', 1: 'lock']
]
@Field static final Map WindowOpenOpts = [    // window_open     TODO - remove it !!
    defaultValue: 1,
    options     : [0: false, 1: true]
]
@Field static final Map CalibratedOpts = [    // calibrated    TODO - remove it !!
    defaultValue: 1,
    options     : [0: false, 1: true]
]
@Field static final Map SensorOpts = [  // sensor    TODO - remove it !!
    defaultValue: 1,
    options     : [0: 'internal', 1: 'external']
]

@Field static final Map deviceProfilesV2 = [
    "AQARA_E1_TRV"   : [
            description   : "Aqara E1 Thermostat model SRTS-A01",
            models        : ["LUMI"],
            device        : [type: "TRV", powerSource: "battery", isSleepy:false],
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
            models        : ["SONOFF"],
            device        : [type: "TRV", powerSource: "battery", isSleepy:false],
            capabilities  : ["ThermostatHeatingSetpoint": true, "ThermostatOperatingState": true, "ThermostatSetpoint":true, "ThermostatMode":true],

            preferences   : ["childLock":"0xFC11:0x0000", "windowOpenDetection":"0xFC11:0x6000", "frostProtectionTemperature":"0xFC11:0x6002", "minHeatingSetpoint":"0x0201:0x0015", "maxHeatingSetpoint":"0x0201:0x0016", "calibrationTemp":"0x0201:0x0010" ],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0006,0020,0201,FC57,FC11", outClusters:"000A,0019", model:"TRVZB", manufacturer:"SONOFF", deviceJoinName: "Sonoff TRVZB"] 
            ],
            commands      : ["setHeatingSetpoint":"setHeatingSetpoint", "autoPollThermostat":"autoPollThermostat", "resetStats":"resetStats", 'refresh':'refresh', "initialize":"initialize", "updateAllPreferences": "updateAllPreferences", "resetPreferencesToDefaults":"resetPreferencesToDefaults", "validateAndFixPreferences":"validateAndFixPreferences"],
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

                //
                // TODO :  e.battery(),
                    // e.battery_low(),
                    // e.child_lock().setAccess('state', ea.ALL),
                    // e.open_window()                 .withLabel('Open window detection')   .withDescription('Automatically turns off the radiator when local temperature drops by more than 1.5°C in 4.5 minutes.')                 .withAccess(ea.ALL),
                    // e.numeric('frost_protection_temperature', ea.ALL)                 .withValueMin(4.0)                 .withValueMax(35.0)                 .withValueStep(0.5)                 .withUnit('°C')  
                    //               .withDescription(                    'Minimum temperature at which to automatically turn on the radiator, if system mode is off, to prevent pipes freezing.'),

                // TODO :         toZigbee: [   tz.thermostat_local_temperature, tz.thermostat_local_temperature_calibration, tz.thermostat_occupied_heating_setpoint,             tz.thermostat_system_mode, tz.thermostat_running_state, tzLocal.child_lock, tzLocal.open_window, tzLocal.frost_protection_temperature],

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
            models        : ["TS0601"],
            device        : [type: "TRV", powerSource: "battery", isSleepy:false],
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
            description   : "NAMRON Thermostat",
            models        : ["*"],
            device        : [type: "TRV", powerSource: "mains", isSleepy:false],
            capabilities  : ["ThermostatHeatingSetpoint": true, "ThermostatOperatingState": true, "ThermostatSetpoint":true, "ThermostatMode":true],
            preferences   : [],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,0009,0408,0702,0B04,0B05,1000,FCCC", outClusters:"0019,1000", model:"4512749-N", manufacturer:"NAMRON AS", deviceJoinName: "NAMRON"] 
                // EP02: 0000,0004,0005,0201 
                // EPF2: 0021
            ],
            commands      : ["resetStats":"resetStats", 'refresh':'refresh', "initialize":"initialize", "updateAllPreferences": "updateAllPreferences", "resetPreferencesToDefaults":"resetPreferencesToDefaults", "validateAndFixPreferences":"validateAndFixPreferences",
                              "factoryResetThermostat":"factoryResetThermostat"
            ],
            attributes    : [
                [at:"0x0201:0x0000",  name:'temperature',              type:"decimal", dt:"0x21", rw: "ro", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", description:'<i>Measured room temperature</i>'],
                // ^^^ (int16S, read-only) reportable LocalTemperature : Attribute This is room temperature, the maximum resolution this format allows is 0.01 ºC.
                [at:"0x0201:0x0001",  name:'outdoorTemperature',       type:"decimal", dt:"0x21", rw: "ro", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C",  description:'<i>Floor temperature</i>'],
                // ^^^ (int16S, read-only) reportable OutdoorTemperature : This is floor temperature, the maximum resolution this format allows is 0.01 ºC.
                [at:"0x0201:0x0002",  name:'occupancy',                type:"enum",    dt:"0x20", rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",  description:'<i>Occupancy</i>'],
                // ^^^ (bitmap8, read-only) Occupancy : When this flag is set as 1, it means occupied, OccupiedHeatingSetpoint will be used, otherwise UnoccupiedHeatingSetpoint will be used
                [at:"0x0201:0x0010",  name:'localTemperatureCalibration', type:"decimal", dt:"0x21", rw: "rw", min:-30.0,  max:30.0, defaultValue:0.0, step:0.5, scale:10,  unit:"°C", title: "<b>Local Temperature Calibration</b>", description:'<i>Room temperature calibration</i>'],
                // ^^^ (Int8S, reportable) TODO: check dt!!!    LocalTemperatureCalibration : Room temperature calibration, range is -30-30, the maximum resolution this format allows 0.1°C. Default value: 0
                [at:"0x0201:0x0011",  name:'coolingSetpoint',          type:"decimal", dt:"0x21", rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Cooling Setpoint</b>",              description:'<i>This system is not invalid</i>'],
                // not used 
                [at:"0x0201:0x0012",  name:'heatingSetpoint',          type:"decimal", dt:"0x21", rw: "rw", min:0.0,  max:40.0, defaultValue:30.0, step:0.01, scale:100,  unit:"°C", title: "<b>Current Heating Setpoint</b>",      description:'<i>Current heating setpoint</i>'],
                // ^^^(int16S, reportable)  OccupiedHeatingSetpoint : Range is 0-4000,the maximum resolution this format allows is 0.01 ºC. Default is 0xbb8(30.00ºC)
                [at:"0x0201:0x0014",  name:'unoccupiedHeatingSetpoint', type:"decimal", dt:"0x21", rw: "rw", min:0.0,  max:40.0, defaultValue:30.0, step:0.01, scale:100,  unit:"°C", title: "<b>Current Heating Setpoint</b>",      description:'<i>Current heating setpoint</i>'],
                // ^^^(int16S, reportable)  OccupiedHeatingSetpoint : Range is 0-4000,the maximum resolution this format allows is 0.01 ºC. Default is 0x258(6.00ºC)
                [at:"0x0201:0x001B",  name:'termostatRunningState',    type:"enum",    dt:"0x20", rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",  description:'<i>termostatRunningState (relay on/off status)</i>'],
                // ^^^(Map16, read-only, reportable) HVAC relay state/ termostatRunningState Indicates the relay on/off status, here only supports bit0( Heat State)

                // ============ Proprietary Attributes: Manufacturer code 0x1224 ============
                [at:"0x0201:0x0000",  name:'lcdBrightnesss',           type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:2, defaultValue:"1",   step:1,  scale:1,    map:[0: "Low Level", 1: "Mid Level", 2: "High Level"], unit:"",  title: "<b>OLED brightness</b>",description:'<i>OLED brightness</i>'],
                // ^^^ (ENUM8,reportable) TODO: check dt!!!  OLED brightness when operate the buttons: Value=0 : Low Level Value=1 : Mid Level(default) Value=2 : High Level
                [at:"0x0201:0x1002",  name:'floorSensorType',          type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:1,    max:5, defaultValue:"1",step:1,  scale:1,    map:[1: "NTC 10K/25", 2: "NTC 15K/25", 3: "NTC 12K/25", 4: "NTC 100K/25", 5: "NTC 50K/25"], unit:"",  title: "<b>Floor Sensor Type</b>",description:'<i>Floor Sensor Type</i>'],
                // ^^^ (ENUM8,reportable) TODO: check dt!!!  TODO: check why there are 3 diferent enum groups???    FloorSenserType Value=5 : NTC 12K/25  Value=4 : NTC 100K/25 Value=3 : NTC 50K/25 Select external (Floor) sensor type: Value=1 : NTC 10K/25 (Default) Value=2 : NTC 15K/25 Value=5 : NTC 12K/25 Value=4 : NTC 100K/25 Value=3 : NTC 50K/25 Select external (Floor) sensor type: Value=1 : NTC 10K/25 (Default) Value=2 : NTC 15K/25             
                [at:"0x0201:0x1003",  name:'controlType',              type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:2, defaultValue:"0",step:1,  scale:1,    map:[0: "Room sensor", 1: "floor sensor", 2: "Room+floor sensor"], unit:"",  title: "<b>Control Type</b>",description:'<i>Control Type</i>'],
                // ^^^ (ENUM8,reportable) TODO: check dt!!!  ControlType The referring sensor for heat control: Value=0 : Room sensor(Default) Value=1 : floor sensor Value=2 : Room+floor sensor
                [at:"0x0201:0x1004",  name:'powerUpStatus',            type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:2, defaultValue:"1",   step:1,  scale:1,    map:[0: "Low Level", 1: "Mid Level", 2: "High Level"], unit:"",  title: "<b>Power Up Status</b>",description:'<i>Power Up Status</i>'],
                // ^^^ (ENUM8,reportable) TODO: check dt!!! PowerUpStatus Value=0 : default mode The mode after reset power of the device: Value=1 : last status before power off (Default) 
                [at:"0x0201:0x1005",  name:'floorSensorCalibration',   type:"decimal", dt:"0x21",  mfgCode:"0x1224", rw: "rw", min:-30.0,  max:30.0, defaultValue:0.0, step:0.5, scale:10,  unit:"°C", title: "<b>Floor Sensor Calibration</b>", description:'<i>Floor Sensor Calibration/i>'],
                // ^^^ (Int8S, reportable) TODO: check dt!!!    FloorSenserCalibration The temp compensation for the external (floor) sensor, range is -30-30, unit is 0.1°C. default value 0
                [at:"0x0201:0x1006",  name:'dryTime',                  type:"number",  dt:"0x21",  mfgCode:"0x1224", rw: "rw", min:5,  max:100, defaultValue:5, step:1, scale:1,  unit:"minutes", title: "<b>Dry Time</b>", description:'<i>The duration of Dry Mode/i>'],
                // ^^^ (Int8S, reportable) TODO: check dt!!!    DryTime The duration of Dry Mode, range is 5-100, unit is min. Default value is 5.
                [at:"0x0201:0x1007",  name:'modeAfterDry',             type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:2, defaultValue:"2",   step:1,  scale:1,    map:[0: "OFF", 1: "Manual mode", 2: "Auto mode", 3: "Away mode"], unit:"",  title: "<b>Mode After Dry</b>",description:'<i>The mode after Dry Mode</i>'],
                // ^^^ (ENUM8,reportable) TODO: check dt!!! ModeAfterDry The mode after Dry Mode: Value=0 : OFF Value=1 : Manual mode Value=2 : Auto mode –schedule (default) Value=3 : Away mode
                [at:"0x0201:0x1008",  name:'temperatureDisplay',       type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:1, defaultValue:"1",   step:1,  scale:1,    map:[0: "Room Temp", 1: "Floor Temp"], unit:"",  title: "<b>Temperature Display</b>",description:'<i>Temperature Display</i>'],
                // ^^^ (ENUM8,reportable) TODO: check dt!!! TemperatureDisplay Value=0 : Room Temp (Default) Value=1 : Floor Temp
                [at:"0x0201:0x1009",  name:'windowOpenCheck',          type:"decimal", dt:"0x21",  mfgCode:"0x1224", rw: "rw", min:0.3, max:8.0, defaultValue:0, step:0.5, scale:10,  unit:"", title: "<b>Window Open Check</b>", description:'<i>The threshold to detect open window, 0 means disabled</i>'],
                // ^^^ (INT8U,reportable) TODO: check dt!!!    WindowOpenCheck The threshold to detect open window, range is 0.3-8, unit is 0.5ºC, 0 means disabled, default is 0
                [at:"0x0201:0x100A",  name:'hysterersis',              type:"decimal", dt:"0x21",  mfgCode:"0x1224", rw: "rw", min:5.0, max:20.0, defaultValue:5.0, step:0.5, scale:10,  unit:"", title: "<b>Hysterersis</b>", description:'<i>Hysterersis</i>'],
                // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  Hysterersis setting, range is 5-20, unit is 0.1ºC, default value is 5 
                [at:"0x0201:0x100B",  name:'displayAutoOffEnable',     type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:1, defaultValue:"1",   step:1,  scale:1,    map:[0: "Disabled", 1: "Enabled"], unit:"",  title: "<b>Display Auto Off Enable</b>",description:'<i>Display Auto Off Enable</i>'],
                // ^^^ (ENUM8,reportable) TODO: check dt!!!  DisplayAutoOffEnable 0, disable Display Auto Off function 1, enable Display Auto Off function
                [at:"0x0201:0x2001",  name:'alarmAirTempOverValue',    type:"decimal", dt:"0x21",  mfgCode:"0x1224", rw: "rw", min:0.2, max:6.0, defaultValue:4.5, step:0.1, scale:10,  unit:"", title: "<b>Alarm Air Temp Over Value</b>", description:'<i>Alarm Air Temp Over Value, 0 means disabled,</i>'],
                // ^^^ (INT8U,reportable) TODO: check dt!!!  TODO - check the scailing !!!  AlarmAirTempOverValue Room temp alarm threshold, range is 0.20-60, unit is 1ºC,0 means disabled, default is 45
                [at:"0x0201:0x2002",  name:'awayModeSet',              type:"enum",    dt:"0x20",  mfgCode:"0x1224", rw: "rw", min:0,    max:1, defaultValue:"0",   step:1,  scale:1,    map:[0: "Not away", 1: "Away"], unit:"",  title: "<b>Away Mode Set</b>",description:'<i>Away Mode Set</i>'],
                // ^^^ (ENUM8,reportable) TODO: check dt!!!  Away Mode Set: Value=1: away Value=0: not away (default)

                // Command supported  !!!!!!!!!! TODO !!!!!!!!!!!! not attribute, but a command !!!!!!!!!!!!!!!!
                [cmd:"0x0201:0x0000",  name:'setpointRaiseLower',       type:"decimal", dt:"0x21", rw: "ro", min:5.0,  max:35.0, step:0.5, scale:10,  unit:"°C", description:'<i>Setpoint Raise/Lower</i>'],
                // ^^^ Setpoint Raise/Lower Increase or decrease the set temperature according to current mode, unit is 0.1ºC

                // Simple Meter-0x0702 (Server) 
                // Attribute: 0x0000 (unsigned48, readonly, reportable)  CurrentSummationDelivered Indicates the current amount of electrical energy delivered to the load.
                // Attribute: 0x0200 (bitmap8, read-only)                Status Flags indicating current device status, always is 0x00
                // Attribute: 0x0300 (bitmap8, read-only)                UnitOfMeasure The unit of metering data, this is always kWh(0x00).
                // Attribute: 0x0303 (bitmap8, read-only)                SummationFormatting The decimal point on the left and right sides of the data, this is always 0x00
                // Attribute: 0x0306 (bitmap8, read-only)                MeteringDeviceType Metering data type, this is always Electric Metering (0x00)

                // Electrical Measurement-0x0b04(Server)
                // Attribute: 0x0000 (bitmap32, read-only)               MeasurementType Indicates the physical entities that this devices is able to measure. Supports only bit0: Active measurement (AC)
                // Attribute: 0x0505 (int16U, read-only, reportable)     RMSVoltage Single phase valid voltage, unit is V
                // Attribute: 0x0508 (int16U, read-only, reportable)     RMSCurrent Single phase valid current, unit is A
                // Attribute: 0x050B (int16U, read-only, reportable)     ActivePower Single phase valid power, unit is W
                // Attribute: 0x0600 (int16U, read-only)                 ACVoltageMultiplier
                // Attribute: 0x0601 (int16U, read-only, reportable)     ACVoltageDivisor Used together with above attributes, the real displayed voltage = RMSVoltage* ACVoltageMultiplier / ACVoltageDivisor
                // Attribute: 0x0602 (int16U, read-only)                 ACCurrentMultiplier
                // Attribute: 0x0603 (int16U, read-only, reportable)     ACCurrentDivisor Used together with above attributes, the real displayed current = RMSCurrent * ACCurrentMultiplier / ACCurrentDivisor
                // Attribute: 0x0604 (int16U, read-only)                 ACPowerMultiplier 0x01
                // Attribute: 0x0605 (int16U, read-only, reportable)     ACPowerDivisor 0x01 Used together with above attributes, the real displayed power = Active Power * ACPowerMultiplie / ACPowerDivisor
                // Attribute: 0x0800 (int16U)                            ACAlarmsMask Specifies which configurable alarms may be generated, only Bit1: Current Overload is set, if set=0, then ACCurrentOverload=0, over current will not be detected
                // Attribute: 0x0802 (int16U, reportable, read-only)     ACCurrentOverload Alarms when the current is over a certain value, 0, 10-16, unit is A, for the unit please refer to RMSCurrent, ACCurrentMultiplier, ACCurrent Divisor
                // Attribute: 0x0605 (Int8u,reportable, mfgCode: 0x1224) OverCurrent Over current value set by the user, range is 0, 10-16, ACCurrentOverload will change spontaneously if this value is modified.

                // Alarm-0x0009(Server)                                  Please set a valid value for ACAlarmsMask of Electrical Measurement. The Alarm Server cluster can generate the following commands : 
                // [Command!!!!] 0x00                                    Alarm: The alarm code of Electrical Measurement is 0
                // [Command!!!!] 0x00 , mfgCode: 0x1224                  Room air temperature over heat, not alarm code

                // OccupancySensing-0x0406(client)                       The attributes that can be received:
                // Attribute: 0x0000 (bitmap8)                           Occupancy Bit 0 specifies the sensed occupancy as follows:1=occupied,0=unoccupied. This flag bit will affect the Occupancy attribute of HVAC cluster, and the operation mode 

                // Thermostat User Interface Configuration- 0x0204(Server)
                // Attribute: 0x0000 (Enum8, reportable)                 TemperatureDisplayMode 0x00 Temperature in, only support 
                // Attribute: 0x0000 (Enum8, reportable)                 KeypadLockout 0x00 No Lockout, 0x01 - 0x05 lockout


            ],
            refresh: ["pollThermostatCluster"],
            deviceJoinName: "NAMRON Thermostat",
            configuration : [:]
    ],

    "UNKNOWN"   : [
            description   : "GENERIC TRV",
            models        : ["*"],
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
        case 0x0276:    // unknown
        case 0x027c:    // unknown
        case 0x027d:    // unknown
        case 0x0280:    // unknown
        case 0xfff2:    // unknown
        case 0x00ff:    // unknown
        case 0x00f7:    // unknown
        case 0xfff2:    // unknown
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

    eventMap = [name: "thermostatSetpoint", value: tempDouble, unit: "\u00B0"+"C"]
    eventMap.descriptionText = null
    sendEvent(eventMap)
    updateDataValue("lastRunningMode", "heat")
}

// thermostat capability standard command
// do nothing in TRV - just send an event
def setCoolingSetpoint(temperature){
    logTrace "setCoolingSetpoint(${temperature}) called!"
    if (temperature != (temperature as int)) {
        temperature = (temperature + 0.5 ) as int
        logDebug "corrected temperature: ${temperature}"
    }
    sendEvent(name: "coolingSetpoint", value: temperature, unit: "\u00B0"+"C")
}

/*
// TODO - remove !
def preset( preset ) {
    logTrace "preset(${preset}) called!"
    if (preset == "auto") {
        setPresetMode("auto")               // hand symbol NOT shown
    }
    else if (preset == "manual") {
        setPresetMode("manual")             // hand symbol is shown on the LCD
    }
    else if (preset == "away") {
        setPresetMode("away")               // 5 degreees 
    }
    else {
        logWarn "preset: unknown preset ${preset}"
    }
}
*/
/*
// TODO - remove !
def setPresetMode(mode) {
    List<String> cmds = []
    logDebug "sending setPresetMode(${mode})"    
    if (isAqaraTRV()) {
        // {'manual': 0, 'auto': 1, 'away': 2}), type: 0x20}
        if (mode == "auto") {
            cmds = zigbee.writeAttribute(0xFCC0, 0x0272, 0x20, 0x01, [mfgCode: 0x115F], delay=200)
        }
        else if (mode == "manual") {
            cmds = zigbee.writeAttribute(0xFCC0, 0x0272, 0x20, 0x00, [mfgCode: 0x115F], delay=200)
        }
        else if (mode == "away") {
            cmds = zigbee.writeAttribute(0xFCC0, 0x0272, 0x20, 0x02, [mfgCode: 0x115F], delay=200)
        }
        else {
            logWarn "setPresetMode: Aqara TRV unknown preset ${mode}"
        }
    }
    else {
        // TODO - set generic thermostat mode
        logWarn "setPresetMode NOT IMPLEMENTED"
        return
    }
    if (cmds == []) { cmds = ["delay 299"] }
    sendZigbeeCommands(cmds)
}
*/

def setThermostatMode( mode ) {
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
            // BRT-100 does not have an explicit off command, so we use the  mode (16 degrees)      // TODO - check how to switch BRT-100 low temp protection mode (5 degrees) ?
            logInfo "setThermostatMode: pre-processing: setting eco mode on (${settings.ecoTemp} &degC)"
            sendAttribute("ecoMode", 1)
            break
        case "emergency heat":  // TODO !!!!!!!!!!!!
            logInfo "setThermostatMode: setting emergency heat mode on (${settings.emergencyHeatingTime} minutes)"
            sendAttribute("emergencyHeating", 1)
            return
        case 'eco':
            logDebug "setThermostatMode: pre-processing: switching the eco mode on"
            sendAttribute("ecoMode", 1)
            return
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


/*    
    // TODO - remove the code below
    //state.mode = mode
    if (isAqaraTRV()) {
        // TODO - set Aqara E1 thermostat mode
        switch(mode) {
            case "heat":
            case "auto":
                cmds = zigbee.writeAttribute(0xFCC0, 0x0271, 0x20, 0x01, [mfgCode: 0x115F], delay=200)        // 'off': 0, 'heat': 1
                break
            case "off":
                cmds = zigbee.writeAttribute(0xFCC0, 0x0271, 0x20, 0x00, [mfgCode: 0x115F], delay=200)        // 'off': 0, 'heat': 1
                break
            default:
                logWarn "setThermostatMode: unknown AqaraTRV mode ${mode}"
                break
        }
    }
    else {
        // TODO - set generic thermostat mode
        switch(mode) {
            case "heat":
            case "auto":
                if (device.currentValue('ecoMode') != 'off') {
                    logInfo "setThermostatMode: switching the eco mode off"
                    sendAttribute("ecoMode", 0)
                }
                if (device.currentValue('emergencyHeating') != 'off') {
                    logInfo "setThermostatMode: switching the emergencyHeating mode off"
                    sendAttribute("emergencyHeating", 0)
                }
                logInfo "setThermostatMode: setting manual mode on (${device.currentValue('heatingSetpoint')} &degC)"
                sendAttribute("trvMode", 1)
                break
            case "off":
            case "cool":
                // BRT-100 does not have an explicit off command, so we use the  mode (16 degrees)      // TODO - check how to switch BRT-100 low temp protection mode (5 degrees) ?
                logInfo "setThermostatMode: setting eco mode on (${settings.ecoTemp} &degC)"
                sendAttribute("ecoMode", 1)
                break
            case "emergency heat":
                logInfo "setThermostatMode: setting emergency heat mode on (${settings.emergencyHeatingTime} minutes)"
                sendAttribute("emergencyHeating", 1)
                break
            default:
                logWarn "setThermostatMode: unknown mode ${mode}"
                break
        }
    }
    if (cmds == []) { cmds = ["delay 299"] }
    sendZigbeeCommands(cmds)
    */
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

/*
    if (isBRT100TRV()) {
        logDebug "autoPollThermostat: no polling for device profile ${getDeviceGroup()}"
        clearRefreshRequest()
    }
    else if (isAqaraTRV()) {
        cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0012, 0x001B, 0x001C], [:], delay=3500)      // 0x0000=local temperature, 0x0012=heating setpoint, 0x001B=controlledSequenceOfOperation, 0x001C=system mode (enum8 )       
    }
    else {
        cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0012, 0x001B, 0x001C, 0x0029], [:], delay=3500)      // 0x0000=local temperature, 0x0012=heating setpoint, 0x001B=controlledSequenceOfOperation, 0x001C=system mode (enum8 )       
    }
   
    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }    
*/

}

//
// called from updated() in the main code ...
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
    // state.states["isRefresh"] = true is set in the commonLib
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

        /*

    // TODO - remove the speicfics !!
    else if (isAqaraTRV()) {
        //cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)                                         // battery voltage (E1 does not send percentage)
        cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0011, 0x0012, 0x001B, 0x001C], [:], delay=3500)       // 0x0000=local temperature, 0x0011=cooling setpoint, 0x0012=heating setpoint, 0x001B=controlledSequenceOfOperation, 0x001C=system mode (enum8 )       
        cmds += zigbee.readAttribute(0xFCC0, [0x0271, 0x0272, 0x0273, 0x0274, 0x0275, 0x0277, 0x0279, 0x027A, 0x027B, 0x027E], [mfgCode: 0x115F], delay=3500)       
        cmds += zigbee.readAttribute(0xFCC0, 0x040a, [mfgCode: 0x115F], delay=500)       
        // stock Generic Zigbee Thermostat Refresh answer:
        // raw:F669010201441C0030011E008600000029640A2900861B0000300412000029540B110000299808, dni:F669, endpoint:01, cluster:0201, size:44, attrId:001C, encoding:30, command:01, value:01, clusterInt:513, attrInt:28, additionalAttrs:[[status:86, attrId:001E, attrInt:30], [value:0A64, encoding:29, attrId:0000, consumedBytes:5, attrInt:0], [status:86, attrId:0029, attrInt:41], [value:04, encoding:30, attrId:001B, consumedBytes:4, attrInt:27], [value:0B54, encoding:29, attrId:0012, consumedBytes:5, attrInt:18], [value:0898, encoding:29, attrId:0011, consumedBytes:5, attrInt:17]]
        // conclusion : binding and reporting configuration for this Aqara E1 thermostat does nothing... We need polling mechanism for faster updates of the internal temperature readings.
    }

    else if (isSonoffTRV()) {
        cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay=200)                          // battery percentage 
        cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0012, 0x0029], [:], delay=3500)       // 0x0000=local temperature, 0x0012=heating setpoint,        
    }

    else if (isBRT100TRV()) {
        //logDebug "no refresh commands for MOES_BRT-100"
        // TODO - research how to get the temperature updates from the BRT-100 !!
        cmds += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200)

    }
    */
    /*
    else if (getDeviceGroup() == "UNKNOWN") {
        cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0012, 0x001B, 0x001C], [:], delay=3500)       // 0x0000=local temperature, 0x0012=heating setpoint, 0x001B=controlledSequenceOfOperation, 0x001C=system mode (enum8 )       
    }
 
    else {
        logWarn "refreshThermostat: unknown device profile ${getDeviceGroup()}"
    }
    
    if (cmds == []) { cmds = ["delay 299"] }
    logDebug "refreshThermostat: ${cmds} "
    return cmds
       */
}

def configureThermostat() {
    List<String> cmds = []
    // TODO !!
    logDebug "configureThermostat() : ${cmds}"
    if (cmds == []) { cmds = ["delay 299"] }    // no , 
    return cmds    
}

// TODO - check ! - called even for Tuya devices ?
// TODO - remove specifics !!
def initializeThermostat()
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
    if (fullInit || settings?.temperaturePollingInterval == null) device.updateSetting('temperaturePollingInterval', [value: TemperaturePollingIntervalOpts.defaultValue.toString(), type: 'enum'])

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
                //sendEvent(name: "thermostatMode", value: "off", isStateChange: true, description: "BRT-100 ecoMode is on")
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



// ~~~~~ start include (144) kkossev.commonLib ~~~~~
library ( // library marker kkossev.commonLib, line 1
    base: "driver", // library marker kkossev.commonLib, line 2
    author: "Krassimir Kossev", // library marker kkossev.commonLib, line 3
    category: "zigbee", // library marker kkossev.commonLib, line 4
    description: "Common ZCL Library", // library marker kkossev.commonLib, line 5
    name: "commonLib", // library marker kkossev.commonLib, line 6
    namespace: "kkossev", // library marker kkossev.commonLib, line 7
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy", // library marker kkossev.commonLib, line 8
    version: "3.0.0", // library marker kkossev.commonLib, line 9
    documentationLink: "" // library marker kkossev.commonLib, line 10
) // library marker kkossev.commonLib, line 11
/* // library marker kkossev.commonLib, line 12
  *  Common ZCL Library // library marker kkossev.commonLib, line 13
  * // library marker kkossev.commonLib, line 14
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.commonLib, line 15
  *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.commonLib, line 16
  * // library marker kkossev.commonLib, line 17
  *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.commonLib, line 18
  * // library marker kkossev.commonLib, line 19
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.commonLib, line 20
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.commonLib, line 21
  *  for the specific language governing permissions and limitations under the License. // library marker kkossev.commonLib, line 22
  * // library marker kkossev.commonLib, line 23
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project). // library marker kkossev.commonLib, line 24
  * For a big portions of code all credits go to Jonathan Bradshaw. // library marker kkossev.commonLib, line 25
  * // library marker kkossev.commonLib, line 26
  * // library marker kkossev.commonLib, line 27
  * ver. 1.0.0  2022-06-18 kkossev  - first beta version // library marker kkossev.commonLib, line 28
  * ver. 2.0.0  2023-05-08 kkossev  - first published version 2.x.x // library marker kkossev.commonLib, line 29
  * ver. 2.1.6  2023-11-06 kkossev  - last update on version 2.x.x // library marker kkossev.commonLib, line 30
  * ver. 3.0.0  2023-11-16 kkossev  - first version 3.x.x // library marker kkossev.commonLib, line 31
  * ver. 3.0.1  2023-12-02 kkossev  - (dev.branch) Info event renamed to Status; txtEnable and logEnable moved to the custom driver settings; 0xFC11 cluster; logEnable is false by default // library marker kkossev.commonLib, line 32
  * // library marker kkossev.commonLib, line 33
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib ! // library marker kkossev.commonLib, line 34
  *                                   TODO: add GetInof (endpoints list) command // library marker kkossev.commonLib, line 35
  *                                   TODO: handle Virtual Switch sendZigbeeCommands(cmd=[he cmd 0xbb14c77a-5810-4e65-b16d-22bc665767ed 0xnull 6 1 {}, delay 2000]) // library marker kkossev.commonLib, line 36
  *                                   TODO: move zigbeeGroups : {} to dedicated lib // library marker kkossev.commonLib, line 37
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 38
 * // library marker kkossev.commonLib, line 39
*/ // library marker kkossev.commonLib, line 40

def commonLibVersion()   {"3.0.1"} // library marker kkossev.commonLib, line 42
def thermostatLibStamp() {"2023/12/02 10:43 AM"} // library marker kkossev.commonLib, line 43

import groovy.transform.Field // library marker kkossev.commonLib, line 45
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 46
import hubitat.device.Protocol // library marker kkossev.commonLib, line 47
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 48
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 49
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 50
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 51


@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 54

metadata { // library marker kkossev.commonLib, line 56

        if (_DEBUG) { // library marker kkossev.commonLib, line 58
            command 'test', [[name: "test", type: "STRING", description: "test", defaultValue : ""]]  // library marker kkossev.commonLib, line 59
            command 'parseTest', [[name: "parseTest", type: "STRING", description: "parseTest", defaultValue : ""]] // library marker kkossev.commonLib, line 60
            command "tuyaTest", [ // library marker kkossev.commonLib, line 61
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]], // library marker kkossev.commonLib, line 62
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]], // library marker kkossev.commonLib, line 63
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"] // library marker kkossev.commonLib, line 64
            ] // library marker kkossev.commonLib, line 65
        } // library marker kkossev.commonLib, line 66


        // common capabilities for all device types // library marker kkossev.commonLib, line 69
        capability 'Configuration' // library marker kkossev.commonLib, line 70
        capability 'Refresh' // library marker kkossev.commonLib, line 71
        capability 'Health Check' // library marker kkossev.commonLib, line 72

        // common attributes for all device types // library marker kkossev.commonLib, line 74
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 75
        attribute "rtt", "number"  // library marker kkossev.commonLib, line 76
        attribute "Status", "string" // library marker kkossev.commonLib, line 77

        // common commands for all device types // library marker kkossev.commonLib, line 79
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability! // library marker kkossev.commonLib, line 80
        command "configure", [[name:"normally it is not needed to configure anything", type: "ENUM",   constraints: ["--- select ---"]+ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 81

        // deviceType specific capabilities, commands and attributes          // library marker kkossev.commonLib, line 83
        if (deviceType in ["Device"]) { // library marker kkossev.commonLib, line 84
            if (_DEBUG) { // library marker kkossev.commonLib, line 85
                command "getAllProperties",       [[name: "Get All Properties"]] // library marker kkossev.commonLib, line 86
            } // library marker kkossev.commonLib, line 87
        } // library marker kkossev.commonLib, line 88
        if (_DEBUG || (deviceType in ["Dimmer", "ButtonDimmer", "Switch", "Valve"])) { // library marker kkossev.commonLib, line 89
            command "zigbeeGroups", [ // library marker kkossev.commonLib, line 90
                [name:"command", type: "ENUM",   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.commonLib, line 91
                [name:"value",   type: "STRING", description: "Group number", constraints: ["STRING"]] // library marker kkossev.commonLib, line 92
            ] // library marker kkossev.commonLib, line 93
        }         // library marker kkossev.commonLib, line 94
        if (deviceType in  ["Device", "THSensor", "MotionSensor", "LightSensor", "AirQuality", "Thermostat", "AqaraCube", "Radar"]) { // library marker kkossev.commonLib, line 95
            capability "Sensor" // library marker kkossev.commonLib, line 96
        } // library marker kkossev.commonLib, line 97
        if (deviceType in  ["Device", "MotionSensor", "Radar"]) { // library marker kkossev.commonLib, line 98
            capability "MotionSensor" // library marker kkossev.commonLib, line 99
        } // library marker kkossev.commonLib, line 100
        if (deviceType in  ["Device", "Switch", "Relay", "Plug", "Outlet", "Thermostat", "Fingerbot", "Dimmer", "Bulb", "IRBlaster"]) { // library marker kkossev.commonLib, line 101
            capability "Actuator" // library marker kkossev.commonLib, line 102
        } // library marker kkossev.commonLib, line 103
        if (deviceType in  ["Device", "THSensor", "LightSensor", "MotionSensor", "Thermostat", "Fingerbot", "ButtonDimmer", "AqaraCube", "IRBlaster"]) { // library marker kkossev.commonLib, line 104
            capability "Battery" // library marker kkossev.commonLib, line 105
            attribute "batteryVoltage", "number" // library marker kkossev.commonLib, line 106
        } // library marker kkossev.commonLib, line 107
        if (deviceType in  ["Thermostat"]) { // library marker kkossev.commonLib, line 108
            capability "Thermostat" // library marker kkossev.commonLib, line 109
        } // library marker kkossev.commonLib, line 110
        if (deviceType in  ["Plug", "Outlet"]) { // library marker kkossev.commonLib, line 111
            capability "Outlet" // library marker kkossev.commonLib, line 112
        }         // library marker kkossev.commonLib, line 113
        if (deviceType in  ["Device", "Switch", "Plug", "Outlet", "Dimmer", "Fingerbot", "Bulb"]) { // library marker kkossev.commonLib, line 114
            capability "Switch" // library marker kkossev.commonLib, line 115
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 116
                attribute "switch", "enum", SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 117
            } // library marker kkossev.commonLib, line 118
        }         // library marker kkossev.commonLib, line 119
        if (deviceType in ["Dimmer", "ButtonDimmer", "Bulb"]) { // library marker kkossev.commonLib, line 120
            capability "SwitchLevel" // library marker kkossev.commonLib, line 121
        } // library marker kkossev.commonLib, line 122
        if (deviceType in  ["Button", "ButtonDimmer", "AqaraCube"]) { // library marker kkossev.commonLib, line 123
            capability "PushableButton" // library marker kkossev.commonLib, line 124
            capability "DoubleTapableButton" // library marker kkossev.commonLib, line 125
            capability "HoldableButton" // library marker kkossev.commonLib, line 126
            capability "ReleasableButton" // library marker kkossev.commonLib, line 127
        } // library marker kkossev.commonLib, line 128
        if (deviceType in  ["Device", "Fingerbot"]) { // library marker kkossev.commonLib, line 129
            capability "Momentary" // library marker kkossev.commonLib, line 130
        } // library marker kkossev.commonLib, line 131
        if (deviceType in  ["Device", "THSensor", "AirQuality", "Thermostat"]) { // library marker kkossev.commonLib, line 132
            capability "TemperatureMeasurement" // library marker kkossev.commonLib, line 133
        } // library marker kkossev.commonLib, line 134
        if (deviceType in  ["Device", "THSensor", "AirQuality"]) { // library marker kkossev.commonLib, line 135
            capability "RelativeHumidityMeasurement"             // library marker kkossev.commonLib, line 136
        } // library marker kkossev.commonLib, line 137
        if (deviceType in  ["Device", "LightSensor", "Radar"]) { // library marker kkossev.commonLib, line 138
            capability "IlluminanceMeasurement" // library marker kkossev.commonLib, line 139
        } // library marker kkossev.commonLib, line 140
        if (deviceType in  ["AirQuality"]) { // library marker kkossev.commonLib, line 141
            capability "AirQuality"            // Attributes: airQualityIndex - NUMBER, range:0..500 // library marker kkossev.commonLib, line 142
        } // library marker kkossev.commonLib, line 143

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 145
        fingerprint profileId:"0104", endpointId:"F2", inClusters:"", outClusters:"", model:"unknown", manufacturer:"unknown", deviceJoinName: "Zigbee device affected by Hubitat F2 bug"  // library marker kkossev.commonLib, line 146

    preferences { // library marker kkossev.commonLib, line 148
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 149
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 150
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 151

        if (advancedOptions == true || advancedOptions == false) { // groovy ... // library marker kkossev.commonLib, line 153
            if (device.hasCapability("TemperatureMeasurement") || device.hasCapability("RelativeHumidityMeasurement") || device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 154
                input name: "minReportingTime", type: "number", title: "<b>Minimum time between reports</b>", description: "<i>Minimum reporting interval, seconds (1..300)</i>", range: "1..300", defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 155
                input name: "maxReportingTime", type: "number", title: "<b>Maximum time between reports</b>", description: "<i>Maximum reporting interval, seconds (120..10000)</i>", range: "120..10000", defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.commonLib, line 156
            } // library marker kkossev.commonLib, line 157
            if (device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 158
                input name: "illuminanceThreshold", type: "number", title: "<b>Illuminance Reporting Threshold</b>", description: "<i>Illuminance reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>", range: "1..255", defaultValue: DEFAULT_ILLUMINANCE_THRESHOLD // library marker kkossev.commonLib, line 159
                input name: "illuminanceCoeff", type: "decimal", title: "<b>Illuminance Correction Coefficient</b>", description: "<i>Illuminance correction coefficient, range (0.10..10.00)</i>", range: "0.10..10.00", defaultValue: 1.00 // library marker kkossev.commonLib, line 160

            } // library marker kkossev.commonLib, line 162
        } // library marker kkossev.commonLib, line 163

        input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: "<i>These advanced options should be already automatically set in an optimal way for your device...</i>", defaultValue: false // library marker kkossev.commonLib, line 165
        if (advancedOptions == true || advancedOptions == true) { // library marker kkossev.commonLib, line 166
            input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 167
            //if (healthCheckMethod != null && safeToInt(healthCheckMethod.value) != 0) { // library marker kkossev.commonLib, line 168
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 169
            //} // library marker kkossev.commonLib, line 170
            if (device.hasCapability("Battery")) { // library marker kkossev.commonLib, line 171
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.commonLib, line 172

            } // library marker kkossev.commonLib, line 174
            if ((deviceType in  ["Switch", "Plug", "Dimmer"]) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 175
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 176
            } // library marker kkossev.commonLib, line 177
            input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 178
        } // library marker kkossev.commonLib, line 179
    } // library marker kkossev.commonLib, line 180

} // library marker kkossev.commonLib, line 182


@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 185
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 186
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events  // library marker kkossev.commonLib, line 187
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 188
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 189
@Field static final String  UNKNOWN = "UNKNOWN" // library marker kkossev.commonLib, line 190
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 191
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 192
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 193
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 194
@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 5 // library marker kkossev.commonLib, line 195
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 196

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 198
    defaultValue: 1, // library marker kkossev.commonLib, line 199
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 200
] // library marker kkossev.commonLib, line 201
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 202
    defaultValue: 240, // library marker kkossev.commonLib, line 203
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 204
] // library marker kkossev.commonLib, line 205
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 206
    defaultValue: 0, // library marker kkossev.commonLib, line 207
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 208
] // library marker kkossev.commonLib, line 209

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.commonLib, line 211
    defaultValue: 0, // library marker kkossev.commonLib, line 212
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 213
] // library marker kkossev.commonLib, line 214
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 215
    defaultValue: 0, // library marker kkossev.commonLib, line 216
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.commonLib, line 217
] // library marker kkossev.commonLib, line 218

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 220
    "Configure the device only"  : [key:2, function: 'configure'], // library marker kkossev.commonLib, line 221
    "Reset Statistics"           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 222
    "           --            "  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 223
    "Delete All Preferences"     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 224
    "Delete All Current States"  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 225
    "Delete All Scheduled Jobs"  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 226
    "Delete All State Variables" : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 227
    "Delete All Child Devices"   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 228
    "           -             "  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 229
    "*** LOAD ALL DEFAULTS ***"  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 230
] // library marker kkossev.commonLib, line 231


def isChattyDeviceReport(description)  {return false /*(description?.contains("cluster: FC7E")) */} // library marker kkossev.commonLib, line 234
def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 235
def isAqaraTVOC()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 236
def isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 237
def isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 238
def isFingerbot()  { (device?.getDataValue('manufacturer') ?: 'n/a') in ['_TZ3210_dse8ogfy'] } // library marker kkossev.commonLib, line 239
def isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 240

/** // library marker kkossev.commonLib, line 242
 * Parse Zigbee message // library marker kkossev.commonLib, line 243
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 244
 */ // library marker kkossev.commonLib, line 245
void parse(final String description) { // library marker kkossev.commonLib, line 246
    checkDriverVersion() // library marker kkossev.commonLib, line 247
    if (!isChattyDeviceReport(description)) { logDebug "parse: ${description}" } // library marker kkossev.commonLib, line 248
    if (state.stats != null) state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 249
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 250
    setHealthStatusOnline() // library marker kkossev.commonLib, line 251

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) {     // library marker kkossev.commonLib, line 253
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 254
        if (true /*isHL0SS9OAradar() && _IGNORE_ZCL_REPORTS == true*/) {    // TODO! // library marker kkossev.commonLib, line 255
            logDebug "ignored IAS zone status" // library marker kkossev.commonLib, line 256
            return // library marker kkossev.commonLib, line 257
        } // library marker kkossev.commonLib, line 258
        else { // library marker kkossev.commonLib, line 259
            parseIasMessage(description)    // TODO! // library marker kkossev.commonLib, line 260
        } // library marker kkossev.commonLib, line 261
    } // library marker kkossev.commonLib, line 262
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 263
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 264
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 265
        if (settings?.logEnable) logInfo "Sending IAS enroll response..." // library marker kkossev.commonLib, line 266
        ArrayList<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 267
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 268
        sendZigbeeCommands( cmds )   // library marker kkossev.commonLib, line 269
    }  // library marker kkossev.commonLib, line 270
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 271
        return // library marker kkossev.commonLib, line 272
    }         // library marker kkossev.commonLib, line 273
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 274

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 276
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 277
        return // library marker kkossev.commonLib, line 278
    } // library marker kkossev.commonLib, line 279
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 280
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 281
        return // library marker kkossev.commonLib, line 282
    } // library marker kkossev.commonLib, line 283
    if (!isChattyDeviceReport(description)) {logDebug "parse: descMap = ${descMap} description=${description}"} // library marker kkossev.commonLib, line 284
    // // library marker kkossev.commonLib, line 285
    final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 286
    final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 287
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 288

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 290
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 291
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 292
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 293
            break // library marker kkossev.commonLib, line 294
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 295
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 296
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 297
            break // library marker kkossev.commonLib, line 298
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 299
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 300
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 301
            break // library marker kkossev.commonLib, line 302
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 303
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 304
            descMap.remove('additionalAttrs')?.each {final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 305
            break // library marker kkossev.commonLib, line 306
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 307
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 308
            descMap.remove('additionalAttrs')?.each {final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 309
            break // library marker kkossev.commonLib, line 310
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 311
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 312
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 313
            break // library marker kkossev.commonLib, line 314
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 315
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 316
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 317
            break // library marker kkossev.commonLib, line 318
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro // library marker kkossev.commonLib, line 319
            parseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 320
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map) } // library marker kkossev.commonLib, line 321
            break // library marker kkossev.commonLib, line 322
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 323
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 324
            break // library marker kkossev.commonLib, line 325
         case 0x0102 :                                      // window covering  // library marker kkossev.commonLib, line 326
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 327
            break        // library marker kkossev.commonLib, line 328
        case 0x0201 :                                       // Aqara E1 TRV  // library marker kkossev.commonLib, line 329
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 330
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 331
            break // library marker kkossev.commonLib, line 332
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 333
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 334
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 335
            break // library marker kkossev.commonLib, line 336
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 337
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 338
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 339
            break // library marker kkossev.commonLib, line 340
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 341
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 342
            break // library marker kkossev.commonLib, line 343
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 344
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 345
            break // library marker kkossev.commonLib, line 346
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 347
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 348
            break // library marker kkossev.commonLib, line 349
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 350
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 351
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 352
            break // library marker kkossev.commonLib, line 353
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 354
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 355
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 356
            break // library marker kkossev.commonLib, line 357
        case 0xE002 : // library marker kkossev.commonLib, line 358
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 359
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 360
            break // library marker kkossev.commonLib, line 361
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 362
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 363
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 364
            break // library marker kkossev.commonLib, line 365
        case 0xFC11 :                                    // Sonoff  // library marker kkossev.commonLib, line 366
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 367
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 368
            break // library marker kkossev.commonLib, line 369
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 370
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 371
            break // library marker kkossev.commonLib, line 372
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 373
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 374
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 375
            break // library marker kkossev.commonLib, line 376
        default: // library marker kkossev.commonLib, line 377
            if (settings.logEnable) { // library marker kkossev.commonLib, line 378
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 379
            } // library marker kkossev.commonLib, line 380
            break // library marker kkossev.commonLib, line 381
    } // library marker kkossev.commonLib, line 382

} // library marker kkossev.commonLib, line 384

/** // library marker kkossev.commonLib, line 386
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 387
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 388
 */ // library marker kkossev.commonLib, line 389
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 390
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 391
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 392
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 393
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 394
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 395
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 396
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 397
    }  // library marker kkossev.commonLib, line 398
    else { // library marker kkossev.commonLib, line 399
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 400
    } // library marker kkossev.commonLib, line 401
} // library marker kkossev.commonLib, line 402

/** // library marker kkossev.commonLib, line 404
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 405
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 406
 */ // library marker kkossev.commonLib, line 407
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 408
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 409
    switch (commandId) { // library marker kkossev.commonLib, line 410
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 411
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 412
            break // library marker kkossev.commonLib, line 413
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 414
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 415
            break // library marker kkossev.commonLib, line 416
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 417
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 418
            break // library marker kkossev.commonLib, line 419
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 420
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 421
            break // library marker kkossev.commonLib, line 422
        case 0x0B: // default command response // library marker kkossev.commonLib, line 423
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 424
            break // library marker kkossev.commonLib, line 425
        default: // library marker kkossev.commonLib, line 426
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 427
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 428
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 429
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 430
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 431
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 432
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 433
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 434
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 435
            } // library marker kkossev.commonLib, line 436
            break // library marker kkossev.commonLib, line 437
    } // library marker kkossev.commonLib, line 438
} // library marker kkossev.commonLib, line 439

/** // library marker kkossev.commonLib, line 441
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 442
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 443
 */ // library marker kkossev.commonLib, line 444
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 445
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 446
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 447
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 448
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 449
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 450
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 451
    } // library marker kkossev.commonLib, line 452
    else { // library marker kkossev.commonLib, line 453
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 454
    } // library marker kkossev.commonLib, line 455
} // library marker kkossev.commonLib, line 456

/** // library marker kkossev.commonLib, line 458
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 459
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 460
 */ // library marker kkossev.commonLib, line 461
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 462
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 463
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 464
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 465
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 466
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 467
    } // library marker kkossev.commonLib, line 468
    else { // library marker kkossev.commonLib, line 469
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 470
    } // library marker kkossev.commonLib, line 471
} // library marker kkossev.commonLib, line 472

/** // library marker kkossev.commonLib, line 474
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 475
 */ // library marker kkossev.commonLib, line 476
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 477
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 478
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 479
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 480
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 481
        state.reportingEnabled = true // library marker kkossev.commonLib, line 482
    } // library marker kkossev.commonLib, line 483
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 484
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 485
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 486
    } else { // library marker kkossev.commonLib, line 487
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 488
    } // library marker kkossev.commonLib, line 489
} // library marker kkossev.commonLib, line 490

/** // library marker kkossev.commonLib, line 492
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 493
 */ // library marker kkossev.commonLib, line 494
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 495
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 496
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 497
    def status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 498
    def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 499
    if (status == 0) { // library marker kkossev.commonLib, line 500
        def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 501
        def min = zigbee.convertHexToInt(descMap.data[6])*256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 502
        def max = zigbee.convertHexToInt(descMap.data[8]+descMap.data[7]) // library marker kkossev.commonLib, line 503
        def delta = 0 // library marker kkossev.commonLib, line 504
        if (descMap.data.size()>=10) {  // library marker kkossev.commonLib, line 505
            delta = zigbee.convertHexToInt(descMap.data[10]+descMap.data[9]) // library marker kkossev.commonLib, line 506
        } // library marker kkossev.commonLib, line 507
        else { // library marker kkossev.commonLib, line 508
            logDebug "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 509
        } // library marker kkossev.commonLib, line 510
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 511
    } // library marker kkossev.commonLib, line 512
    else { // library marker kkossev.commonLib, line 513
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 514
    } // library marker kkossev.commonLib, line 515
} // library marker kkossev.commonLib, line 516

/** // library marker kkossev.commonLib, line 518
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 519
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 520
 */ // library marker kkossev.commonLib, line 521
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 522
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 523
    final String commandId = data[0] // library marker kkossev.commonLib, line 524
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 525
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 526
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 527
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 528
    } else { // library marker kkossev.commonLib, line 529
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 530
    } // library marker kkossev.commonLib, line 531
} // library marker kkossev.commonLib, line 532


// Zigbee Attribute IDs // library marker kkossev.commonLib, line 535
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.commonLib, line 536
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.commonLib, line 537
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.commonLib, line 538
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.commonLib, line 539
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.commonLib, line 540
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.commonLib, line 541
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.commonLib, line 542
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.commonLib, line 543
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 544
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 545
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 546
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.commonLib, line 547
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.commonLib, line 548
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.commonLib, line 549
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.commonLib, line 550

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 552
    0x00: 'Success', // library marker kkossev.commonLib, line 553
    0x01: 'Failure', // library marker kkossev.commonLib, line 554
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 555
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 556
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 557
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 558
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 559
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 560
    0x88: 'Read Only', // library marker kkossev.commonLib, line 561
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 562
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 563
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 564
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 565
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 566
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 567
    0x94: 'Time out', // library marker kkossev.commonLib, line 568
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 569
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 570
] // library marker kkossev.commonLib, line 571

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 573
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 574
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 575
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 576
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 577
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 578
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 579
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 580
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 581
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 582
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 583
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 584
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 585
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 586
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 587
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 588
] // library marker kkossev.commonLib, line 589

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 591
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 592
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 593
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 594
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 595
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 596
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 597
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 598
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 599
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 600
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 601
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 602
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 603
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 604
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 605
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 606
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 607
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 608
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 609
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 610
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 611
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 612
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 613
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 614
] // library marker kkossev.commonLib, line 615


/* // library marker kkossev.commonLib, line 618
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 619
 * Xiaomi cluster 0xFCC0 parser. // library marker kkossev.commonLib, line 620
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 621
 */ // library marker kkossev.commonLib, line 622
void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 623
    if (xiaomiLibVersion() != null) { // library marker kkossev.commonLib, line 624
        parseXiaomiClusterLib(descMap) // library marker kkossev.commonLib, line 625
    }     // library marker kkossev.commonLib, line 626
    else { // library marker kkossev.commonLib, line 627
        logWarn "Xiaomi cluster 0xFCC0" // library marker kkossev.commonLib, line 628
    } // library marker kkossev.commonLib, line 629
} // library marker kkossev.commonLib, line 630


/* // library marker kkossev.commonLib, line 633
@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.commonLib, line 634

// Zigbee Attributes // library marker kkossev.commonLib, line 636
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.commonLib, line 637
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.commonLib, line 638
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.commonLib, line 639
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.commonLib, line 640
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.commonLib, line 641
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.commonLib, line 642
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.commonLib, line 643
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.commonLib, line 644
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.commonLib, line 645
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.commonLib, line 646
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.commonLib, line 647
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.commonLib, line 648
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.commonLib, line 649
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.commonLib, line 650
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.commonLib, line 651

// Xiaomi Tags // library marker kkossev.commonLib, line 653
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.commonLib, line 654
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.commonLib, line 655
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.commonLib, line 656
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.commonLib, line 657
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.commonLib, line 658
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.commonLib, line 659
*/ // library marker kkossev.commonLib, line 660


// TODO - move to xiaomiLib // library marker kkossev.commonLib, line 663
// TODO - move to thermostatLib // library marker kkossev.commonLib, line 664
// TODO - move to aqaraQubeLib // library marker kkossev.commonLib, line 665




@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 670
double approxRollingAverage (double avg, double new_sample) { // library marker kkossev.commonLib, line 671
    if (avg == null || avg == 0) { avg = new_sample} // library marker kkossev.commonLib, line 672
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 673
    avg += new_sample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 674
    // TOSO: try Method II : New average = old average * (n-1)/n + new value /n // library marker kkossev.commonLib, line 675
    return avg // library marker kkossev.commonLib, line 676
} // library marker kkossev.commonLib, line 677

/* // library marker kkossev.commonLib, line 679
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 680
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 681
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 682
*/ // library marker kkossev.commonLib, line 683
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 684

/** // library marker kkossev.commonLib, line 686
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 687
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 688
 */ // library marker kkossev.commonLib, line 689
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 690
    def now = new Date().getTime() // library marker kkossev.commonLib, line 691
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 692
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 693
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 694
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 695
    state.lastRx["checkInTime"] = now // library marker kkossev.commonLib, line 696
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 697
        case 0x0000: // library marker kkossev.commonLib, line 698
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 699
            break // library marker kkossev.commonLib, line 700
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 701
            boolean isPing = state.states["isPing"] ?: false // library marker kkossev.commonLib, line 702
            if (isPing) { // library marker kkossev.commonLib, line 703
                def timeRunning = now.toInteger() - (state.lastTx["pingTime"] ?: '0').toInteger() // library marker kkossev.commonLib, line 704
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 705
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 706
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 707
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 708
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']),safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 709
                    sendRttEvent() // library marker kkossev.commonLib, line 710
                } // library marker kkossev.commonLib, line 711
                else { // library marker kkossev.commonLib, line 712
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 713
                } // library marker kkossev.commonLib, line 714
                state.states["isPing"] = false // library marker kkossev.commonLib, line 715
            } // library marker kkossev.commonLib, line 716
            else { // library marker kkossev.commonLib, line 717
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 718
            } // library marker kkossev.commonLib, line 719
            break // library marker kkossev.commonLib, line 720
        case 0x0004: // library marker kkossev.commonLib, line 721
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 722
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 723
            def manufacturer = device.getDataValue("manufacturer") // library marker kkossev.commonLib, line 724
            if ((manufacturer == null || manufacturer == "unknown") && (descMap?.value != null) ) { // library marker kkossev.commonLib, line 725
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 726
                device.updateDataValue("manufacturer", descMap?.value) // library marker kkossev.commonLib, line 727
            } // library marker kkossev.commonLib, line 728
            break // library marker kkossev.commonLib, line 729
        case 0x0005: // library marker kkossev.commonLib, line 730
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 731
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 732
            def model = device.getDataValue("model") // library marker kkossev.commonLib, line 733
            if ((model == null || model == "unknown") && (descMap?.value != null) ) { // library marker kkossev.commonLib, line 734
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 735
                device.updateDataValue("model", descMap?.value) // library marker kkossev.commonLib, line 736
            } // library marker kkossev.commonLib, line 737
            break // library marker kkossev.commonLib, line 738
        case 0x0007: // library marker kkossev.commonLib, line 739
            def powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 740
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 741
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 742
            break // library marker kkossev.commonLib, line 743
        case 0xFFDF: // library marker kkossev.commonLib, line 744
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 745
            break // library marker kkossev.commonLib, line 746
        case 0xFFE2: // library marker kkossev.commonLib, line 747
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 748
            break // library marker kkossev.commonLib, line 749
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 750
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 751
            break // library marker kkossev.commonLib, line 752
        case 0xFFFE: // library marker kkossev.commonLib, line 753
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 754
            break // library marker kkossev.commonLib, line 755
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 756
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 757
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 758
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 759
            break // library marker kkossev.commonLib, line 760
        default: // library marker kkossev.commonLib, line 761
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 762
            break // library marker kkossev.commonLib, line 763
    } // library marker kkossev.commonLib, line 764
} // library marker kkossev.commonLib, line 765

/* // library marker kkossev.commonLib, line 767
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 768
 * power cluster            0x0001 // library marker kkossev.commonLib, line 769
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 770
*/ // library marker kkossev.commonLib, line 771
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 772
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 773
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 774
    if (descMap.attrId in ["0020", "0021"]) { // library marker kkossev.commonLib, line 775
        state.lastRx["batteryTime"] = new Date().getTime() // library marker kkossev.commonLib, line 776
        state.stats["battCtr"] = (state.stats["battCtr"] ?: 0 ) + 1 // library marker kkossev.commonLib, line 777
    } // library marker kkossev.commonLib, line 778

    final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 780
    if (descMap.attrId == "0020") { // library marker kkossev.commonLib, line 781
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 782
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 783
            sendBatteryVoltageEvent(rawValue, convertToPercent=true) // library marker kkossev.commonLib, line 784
        } // library marker kkossev.commonLib, line 785
    } // library marker kkossev.commonLib, line 786
    else if (descMap.attrId == "0021") { // library marker kkossev.commonLib, line 787
        sendBatteryPercentageEvent(rawValue * 2)     // library marker kkossev.commonLib, line 788
    } // library marker kkossev.commonLib, line 789
    else { // library marker kkossev.commonLib, line 790
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 791
    } // library marker kkossev.commonLib, line 792
} // library marker kkossev.commonLib, line 793

def sendBatteryVoltageEvent(rawValue, Boolean convertToPercent=false) { // library marker kkossev.commonLib, line 795
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 796
    def result = [:] // library marker kkossev.commonLib, line 797
    def volts = rawValue / 10 // library marker kkossev.commonLib, line 798
    if (!(rawValue == 0 || rawValue == 255)) { // library marker kkossev.commonLib, line 799
        def minVolts = 2.2 // library marker kkossev.commonLib, line 800
        def maxVolts = 3.2 // library marker kkossev.commonLib, line 801
        def pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 802
        def roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 803
        if (roundedPct <= 0) roundedPct = 1 // library marker kkossev.commonLib, line 804
        if (roundedPct >100) roundedPct = 100 // library marker kkossev.commonLib, line 805
        if (convertToPercent == true) { // library marker kkossev.commonLib, line 806
            result.value = Math.min(100, roundedPct) // library marker kkossev.commonLib, line 807
            result.name = 'battery' // library marker kkossev.commonLib, line 808
            result.unit  = '%' // library marker kkossev.commonLib, line 809
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.commonLib, line 810
        } // library marker kkossev.commonLib, line 811
        else { // library marker kkossev.commonLib, line 812
            result.value = volts // library marker kkossev.commonLib, line 813
            result.name = 'batteryVoltage' // library marker kkossev.commonLib, line 814
            result.unit  = 'V' // library marker kkossev.commonLib, line 815
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.commonLib, line 816
        } // library marker kkossev.commonLib, line 817
        result.type = 'physical' // library marker kkossev.commonLib, line 818
        result.isStateChange = true // library marker kkossev.commonLib, line 819
        logInfo "${result.descriptionText}" // library marker kkossev.commonLib, line 820
        sendEvent(result) // library marker kkossev.commonLib, line 821
    } // library marker kkossev.commonLib, line 822
    else { // library marker kkossev.commonLib, line 823
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.commonLib, line 824
    }     // library marker kkossev.commonLib, line 825
} // library marker kkossev.commonLib, line 826

def sendBatteryPercentageEvent( batteryPercent, isDigital=false ) { // library marker kkossev.commonLib, line 828
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 829
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 830
        return // library marker kkossev.commonLib, line 831
    } // library marker kkossev.commonLib, line 832
    def map = [:] // library marker kkossev.commonLib, line 833
    map.name = 'battery' // library marker kkossev.commonLib, line 834
    map.timeStamp = now() // library marker kkossev.commonLib, line 835
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 836
    map.unit  = '%' // library marker kkossev.commonLib, line 837
    map.type = isDigital ? 'digital' : 'physical'     // library marker kkossev.commonLib, line 838
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 839
    map.isStateChange = true // library marker kkossev.commonLib, line 840
    //  // library marker kkossev.commonLib, line 841
    def latestBatteryEvent = device.latestState('battery', skipCache=true) // library marker kkossev.commonLib, line 842
    def latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 843
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 844
    def timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 845
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 846
        // send it now! // library marker kkossev.commonLib, line 847
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 848
    } // library marker kkossev.commonLib, line 849
    else { // library marker kkossev.commonLib, line 850
        def delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 851
        map.delayed = delayedTime // library marker kkossev.commonLib, line 852
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 853
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 854
        runIn( delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 855
    } // library marker kkossev.commonLib, line 856
} // library marker kkossev.commonLib, line 857

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 859
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 860
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 861
    sendEvent(map) // library marker kkossev.commonLib, line 862
} // library marker kkossev.commonLib, line 863

private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 865
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 866
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 867
    sendEvent(map) // library marker kkossev.commonLib, line 868
} // library marker kkossev.commonLib, line 869


/* // library marker kkossev.commonLib, line 872
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 873
 * Zigbee Identity Cluster 0x0003 // library marker kkossev.commonLib, line 874
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 875
*/ // library marker kkossev.commonLib, line 876

void parseIdentityCluster(final Map descMap) { // library marker kkossev.commonLib, line 878
    logDebug "unprocessed parseIdentityCluster" // library marker kkossev.commonLib, line 879
} // library marker kkossev.commonLib, line 880



/* // library marker kkossev.commonLib, line 884
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 885
 * Zigbee Scenes Cluster 0x005 // library marker kkossev.commonLib, line 886
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 887
*/ // library marker kkossev.commonLib, line 888

void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 890
    if (DEVICE_TYPE in ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 891
        parseScenesClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 892
    }     // library marker kkossev.commonLib, line 893
    else { // library marker kkossev.commonLib, line 894
        logWarn "unprocessed ScenesCluste attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 895
    } // library marker kkossev.commonLib, line 896
} // library marker kkossev.commonLib, line 897


/* // library marker kkossev.commonLib, line 900
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 901
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.commonLib, line 902
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 903
*/ // library marker kkossev.commonLib, line 904

void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 906
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.commonLib, line 907
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.commonLib, line 908
    if (state.zigbeeGroups == null) state.zigbeeGroups = [:]     // library marker kkossev.commonLib, line 909
    switch (descMap.command as Integer) { // library marker kkossev.commonLib, line 910
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.commonLib, line 911
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 912
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 913
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 914
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 915
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 916
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 917
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.commonLib, line 918
            } // library marker kkossev.commonLib, line 919
            else { // library marker kkossev.commonLib, line 920
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.commonLib, line 921
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.commonLib, line 922
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.commonLib, line 923
                for (int i=0; i<groupCount; i++ ) { // library marker kkossev.commonLib, line 924
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.commonLib, line 925
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.commonLib, line 926
                        return // library marker kkossev.commonLib, line 927
                    } // library marker kkossev.commonLib, line 928
                } // library marker kkossev.commonLib, line 929
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.commonLib, line 930
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt,4)})" // library marker kkossev.commonLib, line 931
                state.zigbeeGroups['groups'].sort() // library marker kkossev.commonLib, line 932
            } // library marker kkossev.commonLib, line 933
            break // library marker kkossev.commonLib, line 934
        case 0x01: // View group // library marker kkossev.commonLib, line 935
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.commonLib, line 936
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 937
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 938
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 939
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 940
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 941
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 942
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 943
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 944
            } // library marker kkossev.commonLib, line 945
            else { // library marker kkossev.commonLib, line 946
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 947
            } // library marker kkossev.commonLib, line 948
            break // library marker kkossev.commonLib, line 949
        case 0x02: // Get group membership // library marker kkossev.commonLib, line 950
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 951
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 952
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 953
            final Set<String> groups = [] // library marker kkossev.commonLib, line 954
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 955
                int pos = (i * 2) + 2 // library marker kkossev.commonLib, line 956
                String group = data[pos + 1] + data[pos] // library marker kkossev.commonLib, line 957
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.commonLib, line 958
            } // library marker kkossev.commonLib, line 959
            state.zigbeeGroups['groups'] = groups // library marker kkossev.commonLib, line 960
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.commonLib, line 961
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.commonLib, line 962
            break // library marker kkossev.commonLib, line 963
        case 0x03: // Remove group // library marker kkossev.commonLib, line 964
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 965
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 966
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 967
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 968
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 969
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 970
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 971
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 972
            } // library marker kkossev.commonLib, line 973
            else { // library marker kkossev.commonLib, line 974
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 975
            } // library marker kkossev.commonLib, line 976
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.commonLib, line 977
            def index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.commonLib, line 978
            if (index >= 0) { // library marker kkossev.commonLib, line 979
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.commonLib, line 980
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.commonLib, line 981
            } // library marker kkossev.commonLib, line 982
            break // library marker kkossev.commonLib, line 983
        case 0x04: //Remove all groups // library marker kkossev.commonLib, line 984
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 985
            logWarn "not implemented!" // library marker kkossev.commonLib, line 986
            break // library marker kkossev.commonLib, line 987
        case 0x05: // Add group if identifying // library marker kkossev.commonLib, line 988
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5).  // library marker kkossev.commonLib, line 989
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 990
            logWarn "not implemented!" // library marker kkossev.commonLib, line 991
            break // library marker kkossev.commonLib, line 992
        default: // library marker kkossev.commonLib, line 993
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 994
            break // library marker kkossev.commonLib, line 995
    } // library marker kkossev.commonLib, line 996
} // library marker kkossev.commonLib, line 997

List<String> addGroupMembership(groupNr) { // library marker kkossev.commonLib, line 999
    List<String> cmds = [] // library marker kkossev.commonLib, line 1000
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1001
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 1002
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 1003
        return // library marker kkossev.commonLib, line 1004
    } // library marker kkossev.commonLib, line 1005
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1006
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1007
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1008
    return cmds // library marker kkossev.commonLib, line 1009
} // library marker kkossev.commonLib, line 1010

List<String> viewGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1012
    List<String> cmds = [] // library marker kkossev.commonLib, line 1013
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1014
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1015
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1016
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1017
    return cmds // library marker kkossev.commonLib, line 1018
} // library marker kkossev.commonLib, line 1019

List<String> getGroupMembership(dummy) { // library marker kkossev.commonLib, line 1021
    List<String> cmds = [] // library marker kkossev.commonLib, line 1022
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, "00") // library marker kkossev.commonLib, line 1023
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1024
    return cmds // library marker kkossev.commonLib, line 1025
} // library marker kkossev.commonLib, line 1026

List<String> removeGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1028
    List<String> cmds = [] // library marker kkossev.commonLib, line 1029
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1030
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 1031
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 1032
        return // library marker kkossev.commonLib, line 1033
    } // library marker kkossev.commonLib, line 1034
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1035
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1036
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1037
    return cmds // library marker kkossev.commonLib, line 1038
} // library marker kkossev.commonLib, line 1039

List<String> removeAllGroups(groupNr) { // library marker kkossev.commonLib, line 1041
    List<String> cmds = [] // library marker kkossev.commonLib, line 1042
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1043
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1044
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1045
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1046
    return cmds // library marker kkossev.commonLib, line 1047
} // library marker kkossev.commonLib, line 1048

List<String> notImplementedGroups(groupNr) { // library marker kkossev.commonLib, line 1050
    List<String> cmds = [] // library marker kkossev.commonLib, line 1051
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1052
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1053
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1054
    return cmds // library marker kkossev.commonLib, line 1055
} // library marker kkossev.commonLib, line 1056

@Field static final Map GroupCommandsMap = [ // library marker kkossev.commonLib, line 1058
    "--- select ---"           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'GroupCommandsHelp'], // library marker kkossev.commonLib, line 1059
    "Add group"                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.commonLib, line 1060
    "View group"               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.commonLib, line 1061
    "Get group membership"     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.commonLib, line 1062
    "Remove group"             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.commonLib, line 1063
    "Remove all groups"        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.commonLib, line 1064
    "Add group if identifying" : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.commonLib, line 1065
] // library marker kkossev.commonLib, line 1066
/* // library marker kkossev.commonLib, line 1067
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 1068
    defaultValue: 0, // library marker kkossev.commonLib, line 1069
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 1070
] // library marker kkossev.commonLib, line 1071
*/ // library marker kkossev.commonLib, line 1072

def zigbeeGroups( command=null, par=null ) // library marker kkossev.commonLib, line 1074
{ // library marker kkossev.commonLib, line 1075
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.commonLib, line 1076
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 1077
    if (state.zigbeeGroups == null) state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 1078
    if (state.zigbeeGroups['groups'] == null) state.zigbeeGroups['groups'] = [] // library marker kkossev.commonLib, line 1079
    def value // library marker kkossev.commonLib, line 1080
    Boolean validated = false // library marker kkossev.commonLib, line 1081
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.commonLib, line 1082
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.commonLib, line 1083
        return // library marker kkossev.commonLib, line 1084
    } // library marker kkossev.commonLib, line 1085
    value = GroupCommandsMap[command]?.type == "number" ? safeToInt(par, -1) : 0 // library marker kkossev.commonLib, line 1086
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) validated = true // library marker kkossev.commonLib, line 1087
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.commonLib, line 1088
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.commonLib, line 1089
        return // library marker kkossev.commonLib, line 1090
    } // library marker kkossev.commonLib, line 1091
    // // library marker kkossev.commonLib, line 1092
    def func // library marker kkossev.commonLib, line 1093
   // try { // library marker kkossev.commonLib, line 1094
        func = GroupCommandsMap[command]?.function // library marker kkossev.commonLib, line 1095
        def type = GroupCommandsMap[command]?.type // library marker kkossev.commonLib, line 1096
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.commonLib, line 1097
        cmds = "$func"(value) // library marker kkossev.commonLib, line 1098
 //   } // library marker kkossev.commonLib, line 1099
//    catch (e) { // library marker kkossev.commonLib, line 1100
//        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1101
//        return // library marker kkossev.commonLib, line 1102
//    } // library marker kkossev.commonLib, line 1103

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1105
    sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 1106
} // library marker kkossev.commonLib, line 1107

def GroupCommandsHelp( val ) { // library marker kkossev.commonLib, line 1109
    logWarn "GroupCommands: select one of the commands in this list!"              // library marker kkossev.commonLib, line 1110
} // library marker kkossev.commonLib, line 1111

/* // library marker kkossev.commonLib, line 1113
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1114
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 1115
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1116
*/ // library marker kkossev.commonLib, line 1117

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 1119
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1120
    if (DEVICE_TYPE in ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 1121
        parseOnOffClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1122
    }     // library marker kkossev.commonLib, line 1123

    else if (descMap.attrId == "0000") { // library marker kkossev.commonLib, line 1125
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1126
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1127
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 1128
    } // library marker kkossev.commonLib, line 1129
    else if (descMap.attrId in ["4000", "4001", "4002", "4004", "8000", "8001", "8002", "8003"]) { // library marker kkossev.commonLib, line 1130
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 1131
    } // library marker kkossev.commonLib, line 1132
    else { // library marker kkossev.commonLib, line 1133
        logWarn "unprocessed OnOffCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1134
    } // library marker kkossev.commonLib, line 1135
} // library marker kkossev.commonLib, line 1136

def clearIsDigital()        { state.states["isDigital"] = false } // library marker kkossev.commonLib, line 1138
def switchDebouncingClear() { state.states["debounce"]  = false } // library marker kkossev.commonLib, line 1139
def isRefreshRequestClear() { state.states["isRefresh"] = false } // library marker kkossev.commonLib, line 1140

def toggle() { // library marker kkossev.commonLib, line 1142
    def descriptionText = "central button switch is " // library marker kkossev.commonLib, line 1143
    def state = "" // library marker kkossev.commonLib, line 1144
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off' ) { // library marker kkossev.commonLib, line 1145
        state = "on" // library marker kkossev.commonLib, line 1146
    } // library marker kkossev.commonLib, line 1147
    else { // library marker kkossev.commonLib, line 1148
        state = "off" // library marker kkossev.commonLib, line 1149
    } // library marker kkossev.commonLib, line 1150
    descriptionText += state // library marker kkossev.commonLib, line 1151
    sendEvent(name: "switch", value: state, descriptionText: descriptionText, type: "physical", isStateChange: true) // library marker kkossev.commonLib, line 1152
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1153
} // library marker kkossev.commonLib, line 1154

def off() { // library marker kkossev.commonLib, line 1156
    if (DEVICE_TYPE in ["Thermostat"]) { thermostatOff(); return } // library marker kkossev.commonLib, line 1157
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 1158
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 1159
        return // library marker kkossev.commonLib, line 1160
    } // library marker kkossev.commonLib, line 1161
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1162
    state.states["isDigital"] = true // library marker kkossev.commonLib, line 1163
    logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 1164
    def cmds = zigbee.off() // library marker kkossev.commonLib, line 1165
    /* // library marker kkossev.commonLib, line 1166
    if (device.getDataValue("model") == "HY0105") { // library marker kkossev.commonLib, line 1167
        cmds += zigbee.command(0x0006, 0x00, "", [destEndpoint: 0x02]) // library marker kkossev.commonLib, line 1168
    } // library marker kkossev.commonLib, line 1169
        else if (state.model == "TS0601") { // library marker kkossev.commonLib, line 1170
            if (isDinRail() || isRTXCircuitBreaker()) { // library marker kkossev.commonLib, line 1171
                cmds = sendTuyaCommand("10", DP_TYPE_BOOL, "00") // library marker kkossev.commonLib, line 1172
            } // library marker kkossev.commonLib, line 1173
            else { // library marker kkossev.commonLib, line 1174
                cmds = zigbee.command(0xEF00, 0x0, "00010101000100") // library marker kkossev.commonLib, line 1175
            } // library marker kkossev.commonLib, line 1176
        } // library marker kkossev.commonLib, line 1177
        else if (isHEProblematic()) { // library marker kkossev.commonLib, line 1178
            cmds = ["he cmd 0x${device.deviceNetworkId}  0x01 0x0006 0 {}","delay 200"] // library marker kkossev.commonLib, line 1179
            logWarn "isHEProblematic() : sending off() : ${cmds}" // library marker kkossev.commonLib, line 1180
        } // library marker kkossev.commonLib, line 1181
        else if (device.endpointId == "F2") { // library marker kkossev.commonLib, line 1182
            cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0 {}","delay 200"] // library marker kkossev.commonLib, line 1183
        } // library marker kkossev.commonLib, line 1184
*/ // library marker kkossev.commonLib, line 1185
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1186
        if ((device.currentState('switch')?.value ?: 'n/a') == 'off' ) { // library marker kkossev.commonLib, line 1187
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1188
        } // library marker kkossev.commonLib, line 1189
        def value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 1190
        def descriptionText = "${value}" // library marker kkossev.commonLib, line 1191
        if (logEnable) { descriptionText += " (2)" } // library marker kkossev.commonLib, line 1192
        sendEvent(name: "switch", value: value, descriptionText: descriptionText, type: "digital", isStateChange: true) // library marker kkossev.commonLib, line 1193
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1194
    } // library marker kkossev.commonLib, line 1195
    else { // library marker kkossev.commonLib, line 1196
        logWarn "_THREE_STATE=${_THREE_STATE} settings?.threeStateEnable=${settings?.threeStateEnable}" // library marker kkossev.commonLib, line 1197
    } // library marker kkossev.commonLib, line 1198


    runInMillis( DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1201
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1202
} // library marker kkossev.commonLib, line 1203

def on() { // library marker kkossev.commonLib, line 1205
    if (DEVICE_TYPE in ["Thermostat"]) { thermostatOn(); return } // library marker kkossev.commonLib, line 1206
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1207
    state.states["isDigital"] = true // library marker kkossev.commonLib, line 1208
    logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 1209
    def cmds = zigbee.on() // library marker kkossev.commonLib, line 1210
/* // library marker kkossev.commonLib, line 1211
    if (device.getDataValue("model") == "HY0105") { // library marker kkossev.commonLib, line 1212
        cmds += zigbee.command(0x0006, 0x01, "", [destEndpoint: 0x02]) // library marker kkossev.commonLib, line 1213
    }     // library marker kkossev.commonLib, line 1214
    else if (state.model == "TS0601") { // library marker kkossev.commonLib, line 1215
        if (isDinRail() || isRTXCircuitBreaker()) { // library marker kkossev.commonLib, line 1216
            cmds = sendTuyaCommand("10", DP_TYPE_BOOL, "01") // library marker kkossev.commonLib, line 1217
        } // library marker kkossev.commonLib, line 1218
        else { // library marker kkossev.commonLib, line 1219
            cmds = zigbee.command(0xEF00, 0x0, "00010101000101") // library marker kkossev.commonLib, line 1220
        } // library marker kkossev.commonLib, line 1221
    } // library marker kkossev.commonLib, line 1222
    else if (isHEProblematic()) { // library marker kkossev.commonLib, line 1223
        cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 1 {}","delay 200"] // library marker kkossev.commonLib, line 1224
        logWarn "isHEProblematic() : sending off() : ${cmds}" // library marker kkossev.commonLib, line 1225
    } // library marker kkossev.commonLib, line 1226
    else if (device.endpointId == "F2") { // library marker kkossev.commonLib, line 1227
        cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 1 {}","delay 200"] // library marker kkossev.commonLib, line 1228
    } // library marker kkossev.commonLib, line 1229
*/ // library marker kkossev.commonLib, line 1230
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1231
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on' ) { // library marker kkossev.commonLib, line 1232
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1233
        } // library marker kkossev.commonLib, line 1234
        def value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 1235
        def descriptionText = "${value}" // library marker kkossev.commonLib, line 1236
        if (logEnable) { descriptionText += " (2)" } // library marker kkossev.commonLib, line 1237
        sendEvent(name: "switch", value: value, descriptionText: descriptionText, type: "digital", isStateChange: true) // library marker kkossev.commonLib, line 1238
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1239
    } // library marker kkossev.commonLib, line 1240
    else { // library marker kkossev.commonLib, line 1241
        logWarn "_THREE_STATE=${_THREE_STATE} settings?.threeStateEnable=${settings?.threeStateEnable}" // library marker kkossev.commonLib, line 1242
    } // library marker kkossev.commonLib, line 1243

    runInMillis( DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1245
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1246
} // library marker kkossev.commonLib, line 1247

def sendSwitchEvent( switchValue ) { // library marker kkossev.commonLib, line 1249
    def value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 1250
    def map = [:]  // library marker kkossev.commonLib, line 1251
    boolean bWasChange = false // library marker kkossev.commonLib, line 1252
    boolean debounce   = state.states["debounce"] ?: false // library marker kkossev.commonLib, line 1253
    def lastSwitch = state.states["lastSwitch"] ?: "unknown" // library marker kkossev.commonLib, line 1254
    if (value == lastSwitch && (debounce == true || (settings.ignoreDuplicated ?: false) == true)) {    // some devices send only catchall events, some only readattr reports, but some will fire both... // library marker kkossev.commonLib, line 1255
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 1256
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1257
        return null // library marker kkossev.commonLib, line 1258
    } // library marker kkossev.commonLib, line 1259
    else { // library marker kkossev.commonLib, line 1260
        logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 1261
    } // library marker kkossev.commonLib, line 1262
    def isDigital = state.states["isDigital"] // library marker kkossev.commonLib, line 1263
    map.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1264
    if (lastSwitch != value ) { // library marker kkossev.commonLib, line 1265
        bWasChange = true // library marker kkossev.commonLib, line 1266
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 1267
        state.states["debounce"]   = true // library marker kkossev.commonLib, line 1268
        state.states["lastSwitch"] = value // library marker kkossev.commonLib, line 1269
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])         // library marker kkossev.commonLib, line 1270
    } // library marker kkossev.commonLib, line 1271
    else { // library marker kkossev.commonLib, line 1272
        state.states["debounce"] = true // library marker kkossev.commonLib, line 1273
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])      // library marker kkossev.commonLib, line 1274
    } // library marker kkossev.commonLib, line 1275

    map.name = "switch" // library marker kkossev.commonLib, line 1277
    map.value = value // library marker kkossev.commonLib, line 1278
    boolean isRefresh = state.states["isRefresh"] ?: false // library marker kkossev.commonLib, line 1279
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1280
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1281
        map.isStateChange = true // library marker kkossev.commonLib, line 1282
    } // library marker kkossev.commonLib, line 1283
    else { // library marker kkossev.commonLib, line 1284
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 1285
    } // library marker kkossev.commonLib, line 1286
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1287
    sendEvent(map) // library marker kkossev.commonLib, line 1288
    clearIsDigital() // library marker kkossev.commonLib, line 1289
} // library marker kkossev.commonLib, line 1290

@Field static final Map powerOnBehaviourOptions = [    // library marker kkossev.commonLib, line 1292
    '0': 'switch off', // library marker kkossev.commonLib, line 1293
    '1': 'switch on', // library marker kkossev.commonLib, line 1294
    '2': 'switch last state' // library marker kkossev.commonLib, line 1295
] // library marker kkossev.commonLib, line 1296

@Field static final Map switchTypeOptions = [    // library marker kkossev.commonLib, line 1298
    '0': 'toggle', // library marker kkossev.commonLib, line 1299
    '1': 'state', // library marker kkossev.commonLib, line 1300
    '2': 'momentary' // library marker kkossev.commonLib, line 1301
] // library marker kkossev.commonLib, line 1302

Map myParseDescriptionAsMap( String description ) // library marker kkossev.commonLib, line 1304
{ // library marker kkossev.commonLib, line 1305
    def descMap = [:] // library marker kkossev.commonLib, line 1306
    try { // library marker kkossev.commonLib, line 1307
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1308
    } // library marker kkossev.commonLib, line 1309
    catch (e1) { // library marker kkossev.commonLib, line 1310
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1311
        // try alternative custom parsing // library marker kkossev.commonLib, line 1312
        descMap = [:] // library marker kkossev.commonLib, line 1313
        try { // library marker kkossev.commonLib, line 1314
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1315
                def pair = entry.split(':') // library marker kkossev.commonLib, line 1316
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1317
            }         // library marker kkossev.commonLib, line 1318
        } // library marker kkossev.commonLib, line 1319
        catch (e2) { // library marker kkossev.commonLib, line 1320
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1321
            return [:] // library marker kkossev.commonLib, line 1322
        } // library marker kkossev.commonLib, line 1323
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1324
    } // library marker kkossev.commonLib, line 1325
    return descMap // library marker kkossev.commonLib, line 1326
} // library marker kkossev.commonLib, line 1327

boolean isTuyaE00xCluster( String description ) // library marker kkossev.commonLib, line 1329
{ // library marker kkossev.commonLib, line 1330
    if(description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1331
        return false  // library marker kkossev.commonLib, line 1332
    } // library marker kkossev.commonLib, line 1333
    // try to parse ... // library marker kkossev.commonLib, line 1334
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1335
    def descMap = [:] // library marker kkossev.commonLib, line 1336
    try { // library marker kkossev.commonLib, line 1337
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1338
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1339
    } // library marker kkossev.commonLib, line 1340
    catch ( e ) { // library marker kkossev.commonLib, line 1341
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1342
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1343
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1344
        return true // library marker kkossev.commonLib, line 1345
    } // library marker kkossev.commonLib, line 1346

    if (descMap.cluster == "E000" && descMap.attrId in ["D001", "D002", "D003"]) { // library marker kkossev.commonLib, line 1348
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1349
    } // library marker kkossev.commonLib, line 1350
    else if (descMap.cluster == "E001" && descMap.attrId == "D010") { // library marker kkossev.commonLib, line 1351
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1352
    } // library marker kkossev.commonLib, line 1353
    else if (descMap.cluster == "E001" && descMap.attrId == "D030") { // library marker kkossev.commonLib, line 1354
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1355
    } // library marker kkossev.commonLib, line 1356
    else { // library marker kkossev.commonLib, line 1357
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1358
        return false  // library marker kkossev.commonLib, line 1359
    } // library marker kkossev.commonLib, line 1360
    return true    // processed // library marker kkossev.commonLib, line 1361
} // library marker kkossev.commonLib, line 1362

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1364
boolean otherTuyaOddities( String description ) { // library marker kkossev.commonLib, line 1365
  /* // library marker kkossev.commonLib, line 1366
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1367
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4  // library marker kkossev.commonLib, line 1368
        return true // library marker kkossev.commonLib, line 1369
    } // library marker kkossev.commonLib, line 1370
*/ // library marker kkossev.commonLib, line 1371
    def descMap = [:] // library marker kkossev.commonLib, line 1372
    try { // library marker kkossev.commonLib, line 1373
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1374
    } // library marker kkossev.commonLib, line 1375
    catch (e1) { // library marker kkossev.commonLib, line 1376
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1377
        // try alternative custom parsing // library marker kkossev.commonLib, line 1378
        descMap = [:] // library marker kkossev.commonLib, line 1379
        try { // library marker kkossev.commonLib, line 1380
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1381
                def pair = entry.split(':') // library marker kkossev.commonLib, line 1382
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1383
            }         // library marker kkossev.commonLib, line 1384
        } // library marker kkossev.commonLib, line 1385
        catch (e2) { // library marker kkossev.commonLib, line 1386
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1387
            return true // library marker kkossev.commonLib, line 1388
        } // library marker kkossev.commonLib, line 1389
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1390
    } // library marker kkossev.commonLib, line 1391
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"}         // library marker kkossev.commonLib, line 1392
    if (descMap.attrId == null ) { // library marker kkossev.commonLib, line 1393
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1394
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1395
        return false // library marker kkossev.commonLib, line 1396
    } // library marker kkossev.commonLib, line 1397
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1398
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1399
    // attribute report received // library marker kkossev.commonLib, line 1400
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1401
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1402
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1403
        //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1404
    } // library marker kkossev.commonLib, line 1405
    attrData.each { // library marker kkossev.commonLib, line 1406
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1407
        def map = [:] // library marker kkossev.commonLib, line 1408
        if (it.status == "86") { // library marker kkossev.commonLib, line 1409
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1410
            // TODO - skip parsing? // library marker kkossev.commonLib, line 1411
        } // library marker kkossev.commonLib, line 1412
        switch (it.cluster) { // library marker kkossev.commonLib, line 1413
            case "0000" : // library marker kkossev.commonLib, line 1414
                if (it.attrId in ["FFE0", "FFE1", "FFE2", "FFE4"]) { // library marker kkossev.commonLib, line 1415
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1416
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1417
                } // library marker kkossev.commonLib, line 1418
                else if (it.attrId in ["FFFE", "FFDF"]) { // library marker kkossev.commonLib, line 1419
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1420
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1421
                } // library marker kkossev.commonLib, line 1422
                else { // library marker kkossev.commonLib, line 1423
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1424
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1425
                } // library marker kkossev.commonLib, line 1426
                break // library marker kkossev.commonLib, line 1427
            default : // library marker kkossev.commonLib, line 1428
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1429
                break // library marker kkossev.commonLib, line 1430
        } // switch // library marker kkossev.commonLib, line 1431
    } // for each attribute // library marker kkossev.commonLib, line 1432
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1433
} // library marker kkossev.commonLib, line 1434

private boolean isCircuitBreaker()      { device.getDataValue("manufacturer") in ["_TZ3000_ky0fq4ho"] } // library marker kkossev.commonLib, line 1436
private boolean isRTXCircuitBreaker()   { device.getDataValue("manufacturer") in ["_TZE200_abatw3kj"] } // library marker kkossev.commonLib, line 1437

def parseOnOffAttributes( it ) { // library marker kkossev.commonLib, line 1439
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1440
    def mode // library marker kkossev.commonLib, line 1441
    def attrName // library marker kkossev.commonLib, line 1442
    if (it.value == null) { // library marker kkossev.commonLib, line 1443
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1444
        return // library marker kkossev.commonLib, line 1445
    } // library marker kkossev.commonLib, line 1446
    def value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1447
    switch (it.attrId) { // library marker kkossev.commonLib, line 1448
        case "4000" :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1449
            attrName = "Global Scene Control" // library marker kkossev.commonLib, line 1450
            mode = value == 0 ? "off" : value == 1 ? "on" : null // library marker kkossev.commonLib, line 1451
            break // library marker kkossev.commonLib, line 1452
        case "4001" :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1453
            attrName = "On Time" // library marker kkossev.commonLib, line 1454
            mode = value // library marker kkossev.commonLib, line 1455
            break // library marker kkossev.commonLib, line 1456
        case "4002" :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1457
            attrName = "Off Wait Time" // library marker kkossev.commonLib, line 1458
            mode = value // library marker kkossev.commonLib, line 1459
            break // library marker kkossev.commonLib, line 1460
        case "4003" :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1  // library marker kkossev.commonLib, line 1461
            attrName = "Power On State" // library marker kkossev.commonLib, line 1462
            mode = value == 0 ? "off" : value == 1 ? "on" : value == 2 ?  "Last state" : "UNKNOWN" // library marker kkossev.commonLib, line 1463
            break // library marker kkossev.commonLib, line 1464
        case "8000" :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1465
            attrName = "Child Lock" // library marker kkossev.commonLib, line 1466
            mode = value == 0 ? "off" : "on" // library marker kkossev.commonLib, line 1467
            break // library marker kkossev.commonLib, line 1468
        case "8001" :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1469
            attrName = "LED mode" // library marker kkossev.commonLib, line 1470
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1471
                mode = value == 0 ? "Always Green" : value == 1 ? "Red when On; Green when Off" : value == 2 ? "Green when On; Red when Off" : value == 3 ? "Always Red" : null // library marker kkossev.commonLib, line 1472
            } // library marker kkossev.commonLib, line 1473
            else { // library marker kkossev.commonLib, line 1474
                mode = value == 0 ? "Disabled"  : value == 1 ? "Lit when On" : value == 2 ? "Lit when Off" : value == 3 ? "Freeze": null // library marker kkossev.commonLib, line 1475
            } // library marker kkossev.commonLib, line 1476
            break // library marker kkossev.commonLib, line 1477
        case "8002" :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1478
            attrName = "Power On State" // library marker kkossev.commonLib, line 1479
            mode = value == 0 ? "off" : value == 1 ? "on" : value == 2 ?  "Last state" : null // library marker kkossev.commonLib, line 1480
            break // library marker kkossev.commonLib, line 1481
        case "8003" : //  Over current alarm // library marker kkossev.commonLib, line 1482
            attrName = "Over current alarm" // library marker kkossev.commonLib, line 1483
            mode = value == 0 ? "Over Current OK" : value == 1 ? "Over Current Alarm" : null // library marker kkossev.commonLib, line 1484
            break // library marker kkossev.commonLib, line 1485
        default : // library marker kkossev.commonLib, line 1486
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1487
            return // library marker kkossev.commonLib, line 1488
    } // library marker kkossev.commonLib, line 1489
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1490
} // library marker kkossev.commonLib, line 1491

def sendButtonEvent(buttonNumber, buttonState, isDigital=false) { // library marker kkossev.commonLib, line 1493
    def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true, type: isDigital==true ? 'digital' : 'physical'] // library marker kkossev.commonLib, line 1494
    if (txtEnable) {log.info "${device.displayName} $event.descriptionText"} // library marker kkossev.commonLib, line 1495
    sendEvent(event) // library marker kkossev.commonLib, line 1496
} // library marker kkossev.commonLib, line 1497

def push() {                // Momentary capability // library marker kkossev.commonLib, line 1499
    logDebug "push momentary" // library marker kkossev.commonLib, line 1500
    if (DEVICE_TYPE in ["Fingerbot"])     { pushFingerbot(); return }     // library marker kkossev.commonLib, line 1501
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.commonLib, line 1502
} // library marker kkossev.commonLib, line 1503

def push(buttonNumber) {    //pushableButton capability // library marker kkossev.commonLib, line 1505
    if (DEVICE_TYPE in ["Fingerbot"])     { pushFingerbot(buttonNumber); return }     // library marker kkossev.commonLib, line 1506
    sendButtonEvent(buttonNumber, "pushed", isDigital=true) // library marker kkossev.commonLib, line 1507
} // library marker kkossev.commonLib, line 1508

def doubleTap(buttonNumber) { // library marker kkossev.commonLib, line 1510
    sendButtonEvent(buttonNumber, "doubleTapped", isDigital=true) // library marker kkossev.commonLib, line 1511
} // library marker kkossev.commonLib, line 1512

def hold(buttonNumber) { // library marker kkossev.commonLib, line 1514
    sendButtonEvent(buttonNumber, "held", isDigital=true) // library marker kkossev.commonLib, line 1515
} // library marker kkossev.commonLib, line 1516

def release(buttonNumber) { // library marker kkossev.commonLib, line 1518
    sendButtonEvent(buttonNumber, "released", isDigital=true) // library marker kkossev.commonLib, line 1519
} // library marker kkossev.commonLib, line 1520

void sendNumberOfButtonsEvent(numberOfButtons) { // library marker kkossev.commonLib, line 1522
    sendEvent(name: "numberOfButtons", value: numberOfButtons, isStateChange: true, type: "digital") // library marker kkossev.commonLib, line 1523
} // library marker kkossev.commonLib, line 1524

void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.commonLib, line 1526
    sendEvent(name: "supportedButtonValues", value: JsonOutput.toJson(supportedValues), isStateChange: true, type: "digital") // library marker kkossev.commonLib, line 1527
} // library marker kkossev.commonLib, line 1528


/* // library marker kkossev.commonLib, line 1531
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1532
 * Level Control Cluster            0x0008 // library marker kkossev.commonLib, line 1533
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1534
*/ // library marker kkossev.commonLib, line 1535
void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1536
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1537
    if (DEVICE_TYPE in ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 1538
        parseLevelControlClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1539
    } // library marker kkossev.commonLib, line 1540
    else if (DEVICE_TYPE in ["Bulb"]) { // library marker kkossev.commonLib, line 1541
        parseLevelControlClusterBulb(descMap) // library marker kkossev.commonLib, line 1542
    } // library marker kkossev.commonLib, line 1543
    else if (descMap.attrId == "0000") { // library marker kkossev.commonLib, line 1544
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1545
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1546
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1547
    } // library marker kkossev.commonLib, line 1548
    else { // library marker kkossev.commonLib, line 1549
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1550
    } // library marker kkossev.commonLib, line 1551
} // library marker kkossev.commonLib, line 1552


def sendLevelControlEvent( rawValue ) { // library marker kkossev.commonLib, line 1555
    def value = rawValue as int // library marker kkossev.commonLib, line 1556
    if (value <0) value = 0 // library marker kkossev.commonLib, line 1557
    if (value >100) value = 100 // library marker kkossev.commonLib, line 1558
    def map = [:]  // library marker kkossev.commonLib, line 1559

    def isDigital = state.states["isDigital"] // library marker kkossev.commonLib, line 1561
    map.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1562

    map.name = "level" // library marker kkossev.commonLib, line 1564
    map.value = value // library marker kkossev.commonLib, line 1565
    boolean isRefresh = state.states["isRefresh"] ?: false // library marker kkossev.commonLib, line 1566
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1567
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1568
        map.isStateChange = true // library marker kkossev.commonLib, line 1569
    } // library marker kkossev.commonLib, line 1570
    else { // library marker kkossev.commonLib, line 1571
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.commonLib, line 1572
    } // library marker kkossev.commonLib, line 1573
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1574
    sendEvent(map) // library marker kkossev.commonLib, line 1575
    clearIsDigital() // library marker kkossev.commonLib, line 1576
} // library marker kkossev.commonLib, line 1577

/** // library marker kkossev.commonLib, line 1579
 * Get the level transition rate // library marker kkossev.commonLib, line 1580
 * @param level desired target level (0-100) // library marker kkossev.commonLib, line 1581
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.commonLib, line 1582
 * @return transition rate in 1/10ths of a second // library marker kkossev.commonLib, line 1583
 */ // library marker kkossev.commonLib, line 1584
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.commonLib, line 1585
    int rate = 0 // library marker kkossev.commonLib, line 1586
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.commonLib, line 1587
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.commonLib, line 1588
    if (!isOn) { // library marker kkossev.commonLib, line 1589
        currentLevel = 0 // library marker kkossev.commonLib, line 1590
    } // library marker kkossev.commonLib, line 1591
    // Check if 'transitionTime' has a value // library marker kkossev.commonLib, line 1592
    if (transitionTime > 0) { // library marker kkossev.commonLib, line 1593
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.commonLib, line 1594
        rate = transitionTime * 10 // library marker kkossev.commonLib, line 1595
    } else { // library marker kkossev.commonLib, line 1596
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.commonLib, line 1597
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.commonLib, line 1598
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.commonLib, line 1599
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.commonLib, line 1600
        } // library marker kkossev.commonLib, line 1601
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.commonLib, line 1602
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.commonLib, line 1603
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.commonLib, line 1604
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.commonLib, line 1605
        } // library marker kkossev.commonLib, line 1606
    } // library marker kkossev.commonLib, line 1607
    logDebug "using level transition rate ${rate}" // library marker kkossev.commonLib, line 1608
    return rate // library marker kkossev.commonLib, line 1609
} // library marker kkossev.commonLib, line 1610

// Command option that enable changes when off // library marker kkossev.commonLib, line 1612
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.commonLib, line 1613

/** // library marker kkossev.commonLib, line 1615
 * Constrain a value to a range // library marker kkossev.commonLib, line 1616
 * @param value value to constrain // library marker kkossev.commonLib, line 1617
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1618
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1619
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1620
 */ // library marker kkossev.commonLib, line 1621
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.commonLib, line 1622
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1623
        return value // library marker kkossev.commonLib, line 1624
    } // library marker kkossev.commonLib, line 1625
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.commonLib, line 1626
} // library marker kkossev.commonLib, line 1627

/** // library marker kkossev.commonLib, line 1629
 * Constrain a value to a range // library marker kkossev.commonLib, line 1630
 * @param value value to constrain // library marker kkossev.commonLib, line 1631
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1632
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1633
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1634
 */ // library marker kkossev.commonLib, line 1635
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.commonLib, line 1636
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1637
        return value as Integer // library marker kkossev.commonLib, line 1638
    } // library marker kkossev.commonLib, line 1639
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.commonLib, line 1640
} // library marker kkossev.commonLib, line 1641

// Delay before reading attribute (when using polling) // library marker kkossev.commonLib, line 1643
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.commonLib, line 1644

/** // library marker kkossev.commonLib, line 1646
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.commonLib, line 1647
 * @param delayMs delay in milliseconds // library marker kkossev.commonLib, line 1648
 * @param commands commands to execute // library marker kkossev.commonLib, line 1649
 * @return list of commands to be sent to the device // library marker kkossev.commonLib, line 1650
 */ // library marker kkossev.commonLib, line 1651
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.commonLib, line 1652
    if (state.reportingEnabled == false) { // library marker kkossev.commonLib, line 1653
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.commonLib, line 1654
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.commonLib, line 1655
    } // library marker kkossev.commonLib, line 1656
    return [] // library marker kkossev.commonLib, line 1657
} // library marker kkossev.commonLib, line 1658

def intTo16bitUnsignedHex(value) { // library marker kkossev.commonLib, line 1660
    def hexStr = zigbee.convertToHexString(value.toInteger(),4) // library marker kkossev.commonLib, line 1661
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1662
} // library marker kkossev.commonLib, line 1663

def intTo8bitUnsignedHex(value) { // library marker kkossev.commonLib, line 1665
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1666
} // library marker kkossev.commonLib, line 1667

/** // library marker kkossev.commonLib, line 1669
 * Send 'switchLevel' attribute event // library marker kkossev.commonLib, line 1670
 * @param isOn true if light is on, false otherwise // library marker kkossev.commonLib, line 1671
 * @param level brightness level (0-254) // library marker kkossev.commonLib, line 1672
 */ // library marker kkossev.commonLib, line 1673
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) { // library marker kkossev.commonLib, line 1674
    List<String> cmds = [] // library marker kkossev.commonLib, line 1675
    final Integer level = constrain(value) // library marker kkossev.commonLib, line 1676
    final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.commonLib, line 1677
    final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.commonLib, line 1678
    final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.commonLib, line 1679
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.commonLib, line 1680
        // If light is off, first go to level 0 then to desired level // library marker kkossev.commonLib, line 1681
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.commonLib, line 1682
    } // library marker kkossev.commonLib, line 1683
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.commonLib, line 1684
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.commonLib, line 1685
    /* // library marker kkossev.commonLib, line 1686
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.commonLib, line 1687
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.commonLib, line 1688
    */ // library marker kkossev.commonLib, line 1689
    int duration = 10            // TODO !!! // library marker kkossev.commonLib, line 1690
    String endpointId = "01"     // TODO !!! // library marker kkossev.commonLib, line 1691
     cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.commonLib, line 1692

    return cmds // library marker kkossev.commonLib, line 1694
} // library marker kkossev.commonLib, line 1695


/** // library marker kkossev.commonLib, line 1698
 * Set Level Command // library marker kkossev.commonLib, line 1699
 * @param value level percent (0-100) // library marker kkossev.commonLib, line 1700
 * @param transitionTime transition time in seconds // library marker kkossev.commonLib, line 1701
 * @return List of zigbee commands // library marker kkossev.commonLib, line 1702
 */ // library marker kkossev.commonLib, line 1703
void /*List<String>*/ setLevel(final Object value, final Object transitionTime = null) { // library marker kkossev.commonLib, line 1704
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.commonLib, line 1705
    if (DEVICE_TYPE in  ["ButtonDimmer"]) { setLevelButtonDimmer(value, transitionTime); return } // library marker kkossev.commonLib, line 1706
    if (DEVICE_TYPE in  ["Bulb"]) { setLevelBulb(value, transitionTime); return } // library marker kkossev.commonLib, line 1707
    else { // library marker kkossev.commonLib, line 1708
        final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer) // library marker kkossev.commonLib, line 1709
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1710
        /*return*/ sendZigbeeCommands ( setLevelPrivate(value, rate)) // library marker kkossev.commonLib, line 1711
    } // library marker kkossev.commonLib, line 1712
} // library marker kkossev.commonLib, line 1713

/* // library marker kkossev.commonLib, line 1715
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1716
 * Color Control Cluster            0x0300 // library marker kkossev.commonLib, line 1717
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1718
*/ // library marker kkossev.commonLib, line 1719
void parseColorControlCluster(final Map descMap, description) { // library marker kkossev.commonLib, line 1720
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1721
    if (DEVICE_TYPE in ["Bulb"]) { // library marker kkossev.commonLib, line 1722
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1723
    } // library marker kkossev.commonLib, line 1724
    else if (descMap.attrId == "0000") { // library marker kkossev.commonLib, line 1725
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1726
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1727
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1728
    } // library marker kkossev.commonLib, line 1729
    else { // library marker kkossev.commonLib, line 1730
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1731
    } // library marker kkossev.commonLib, line 1732
} // library marker kkossev.commonLib, line 1733

/* // library marker kkossev.commonLib, line 1735
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1736
 * Illuminance    cluster 0x0400 // library marker kkossev.commonLib, line 1737
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1738
*/ // library marker kkossev.commonLib, line 1739
void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1740
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1741
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1742
    final long value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1743
    def lux = value > 0 ? Math.round(Math.pow(10,(value/10000))) : 0 // library marker kkossev.commonLib, line 1744
    handleIlluminanceEvent(lux) // library marker kkossev.commonLib, line 1745
} // library marker kkossev.commonLib, line 1746

void handleIlluminanceEvent( illuminance, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1748
    def eventMap = [:] // library marker kkossev.commonLib, line 1749
    if (state.stats != null) state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1750
    eventMap.name = "illuminance" // library marker kkossev.commonLib, line 1751
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.commonLib, line 1752
    eventMap.value  = illumCorrected // library marker kkossev.commonLib, line 1753
    eventMap.type = isDigital ? "digital" : "physical" // library marker kkossev.commonLib, line 1754
    eventMap.unit = "lx" // library marker kkossev.commonLib, line 1755
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1756
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now()))/1000) // library marker kkossev.commonLib, line 1757
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1758
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1759
    Integer lastIllum = device.currentValue("illuminance") ?: 0 // library marker kkossev.commonLib, line 1760
    Integer delta = Math.abs(lastIllum- illumCorrected) // library marker kkossev.commonLib, line 1761
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.commonLib, line 1762
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.commonLib, line 1763
        return // library marker kkossev.commonLib, line 1764
    } // library marker kkossev.commonLib, line 1765
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1766
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1767
        unschedule("sendDelayedIllumEvent")        //get rid of stale queued reports // library marker kkossev.commonLib, line 1768
        state.lastRx['illumTime'] = now() // library marker kkossev.commonLib, line 1769
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1770
    }         // library marker kkossev.commonLib, line 1771
    else {         // queue the event // library marker kkossev.commonLib, line 1772
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1773
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.commonLib, line 1774
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1775
    } // library marker kkossev.commonLib, line 1776
} // library marker kkossev.commonLib, line 1777

private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.commonLib, line 1779
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1780
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1781
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1782
} // library marker kkossev.commonLib, line 1783

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.commonLib, line 1785


/* // library marker kkossev.commonLib, line 1788
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1789
 * temperature // library marker kkossev.commonLib, line 1790
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1791
*/ // library marker kkossev.commonLib, line 1792
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1793
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1794
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1795
    final long value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1796
    handleTemperatureEvent(value/100.0F as Float) // library marker kkossev.commonLib, line 1797
} // library marker kkossev.commonLib, line 1798

void handleTemperatureEvent( Float temperature, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1800
    def eventMap = [:] // library marker kkossev.commonLib, line 1801
    if (state.stats != null) state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1802
    eventMap.name = "temperature" // library marker kkossev.commonLib, line 1803
    def Scale = location.temperatureScale // library marker kkossev.commonLib, line 1804
    if (Scale == "F") { // library marker kkossev.commonLib, line 1805
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1806
        eventMap.unit = "\u00B0"+"F" // library marker kkossev.commonLib, line 1807
    } // library marker kkossev.commonLib, line 1808
    else { // library marker kkossev.commonLib, line 1809
        eventMap.unit = "\u00B0"+"C" // library marker kkossev.commonLib, line 1810
    } // library marker kkossev.commonLib, line 1811
    def tempCorrected = (temperature + safeToDouble(settings?.temperatureOffset ?: 0)) as Float // library marker kkossev.commonLib, line 1812
    eventMap.value  =  (Math.round(tempCorrected * 10) / 10.0) as Float // library marker kkossev.commonLib, line 1813
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1814
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1815
    if (state.states["isRefresh"] == true) { // library marker kkossev.commonLib, line 1816
        eventMap.descriptionText += " [refresh]" // library marker kkossev.commonLib, line 1817
        eventMap.isStateChange = true // library marker kkossev.commonLib, line 1818
    }    // library marker kkossev.commonLib, line 1819
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now()))/1000) // library marker kkossev.commonLib, line 1820
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1821
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1822
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1823
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1824
        unschedule("sendDelayedTempEvent")        //get rid of stale queued reports // library marker kkossev.commonLib, line 1825
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1826
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1827
    }         // library marker kkossev.commonLib, line 1828
    else {         // queue the event // library marker kkossev.commonLib, line 1829
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1830
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1831
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1832
    } // library marker kkossev.commonLib, line 1833
} // library marker kkossev.commonLib, line 1834

private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.commonLib, line 1836
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1837
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1838
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1839
} // library marker kkossev.commonLib, line 1840

/* // library marker kkossev.commonLib, line 1842
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1843
 * humidity // library marker kkossev.commonLib, line 1844
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1845
*/ // library marker kkossev.commonLib, line 1846
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1847
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1848
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1849
    final long value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1850
    handleHumidityEvent(value/100.0F as Float) // library marker kkossev.commonLib, line 1851
} // library marker kkossev.commonLib, line 1852

void handleHumidityEvent( Float humidity, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1854
    def eventMap = [:] // library marker kkossev.commonLib, line 1855
    if (state.stats != null) state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1856
    double humidityAsDouble = safeToDouble(humidity) + safeToDouble(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1857
    if (humidityAsDouble <= 0.0 || humidityAsDouble > 100.0) { // library marker kkossev.commonLib, line 1858
        logWarn "ignored invalid humidity ${humidity} (${humidityAsDouble})" // library marker kkossev.commonLib, line 1859
        return // library marker kkossev.commonLib, line 1860
    } // library marker kkossev.commonLib, line 1861
    eventMap.value = Math.round(humidityAsDouble) // library marker kkossev.commonLib, line 1862
    eventMap.name = "humidity" // library marker kkossev.commonLib, line 1863
    eventMap.unit = "% RH" // library marker kkossev.commonLib, line 1864
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1865
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1866
    eventMap.descriptionText = "${eventMap.name} is ${humidityAsDouble.round(1)} ${eventMap.unit}" // library marker kkossev.commonLib, line 1867
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now()))/1000) // library marker kkossev.commonLib, line 1868
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1869
    Integer timeRamaining = (minTime - timeElapsed) as Integer     // library marker kkossev.commonLib, line 1870
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1871
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1872
        unschedule("sendDelayedHumidityEvent") // library marker kkossev.commonLib, line 1873
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1874
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1875
    } // library marker kkossev.commonLib, line 1876
    else { // library marker kkossev.commonLib, line 1877
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1878
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1879
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1880
    } // library marker kkossev.commonLib, line 1881
} // library marker kkossev.commonLib, line 1882

private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1884
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1885
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1886
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1887
} // library marker kkossev.commonLib, line 1888

/* // library marker kkossev.commonLib, line 1890
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1891
 * Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1892
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1893
*/ // library marker kkossev.commonLib, line 1894

void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1896
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1897
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1898
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1899
    if (DEVICE_TYPE in  ["Switch"]) { // library marker kkossev.commonLib, line 1900
        parseElectricalMeasureClusterSwitch(descMap) // library marker kkossev.commonLib, line 1901
    } // library marker kkossev.commonLib, line 1902
    else { // library marker kkossev.commonLib, line 1903
        logWarn "parseElectricalMeasureCluster is NOT implemented1" // library marker kkossev.commonLib, line 1904
    } // library marker kkossev.commonLib, line 1905
} // library marker kkossev.commonLib, line 1906

/* // library marker kkossev.commonLib, line 1908
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1909
 * Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1910
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1911
*/ // library marker kkossev.commonLib, line 1912

void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1914
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1915
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1916
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1917
    if (DEVICE_TYPE in  ["Switch"]) { // library marker kkossev.commonLib, line 1918
        parseMeteringClusterSwitch(descMap) // library marker kkossev.commonLib, line 1919
    } // library marker kkossev.commonLib, line 1920
    else { // library marker kkossev.commonLib, line 1921
        logWarn "parseMeteringCluster is NOT implemented1" // library marker kkossev.commonLib, line 1922
    } // library marker kkossev.commonLib, line 1923
} // library marker kkossev.commonLib, line 1924


/* // library marker kkossev.commonLib, line 1927
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1928
 * pm2.5 // library marker kkossev.commonLib, line 1929
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1930
*/ // library marker kkossev.commonLib, line 1931
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1932
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1933
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1934
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1935
    Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1936
    //logDebug "pm25 float value = ${floatValue}" // library marker kkossev.commonLib, line 1937
    handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1938
} // library marker kkossev.commonLib, line 1939

void handlePm25Event( Integer pm25, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1941
    def eventMap = [:] // library marker kkossev.commonLib, line 1942
    if (state.stats != null) state.stats['pm25Ctr'] = (state.stats['pm25Ctr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1943
    double pm25AsDouble = safeToDouble(pm25) + safeToDouble(settings?.pm25Offset ?: 0) // library marker kkossev.commonLib, line 1944
    if (pm25AsDouble <= 0.0 || pm25AsDouble > 999.0) { // library marker kkossev.commonLib, line 1945
        logWarn "ignored invalid pm25 ${pm25} (${pm25AsDouble})" // library marker kkossev.commonLib, line 1946
        return // library marker kkossev.commonLib, line 1947
    } // library marker kkossev.commonLib, line 1948
    eventMap.value = Math.round(pm25AsDouble) // library marker kkossev.commonLib, line 1949
    eventMap.name = "pm25" // library marker kkossev.commonLib, line 1950
    eventMap.unit = "\u03BCg/m3"    //"mg/m3" // library marker kkossev.commonLib, line 1951
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1952
    eventMap.isStateChange = true // library marker kkossev.commonLib, line 1953
    eventMap.descriptionText = "${eventMap.name} is ${pm25AsDouble.round()} ${eventMap.unit}" // library marker kkossev.commonLib, line 1954
    Integer timeElapsed = Math.round((now() - (state.lastRx['pm25Time'] ?: now()))/1000) // library marker kkossev.commonLib, line 1955
    Integer minTime = settings?.minReportingTimePm25 ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1956
    Integer timeRamaining = (minTime - timeElapsed) as Integer     // library marker kkossev.commonLib, line 1957
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1958
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1959
        unschedule("sendDelayedPm25Event") // library marker kkossev.commonLib, line 1960
        state.lastRx['pm25Time'] = now() // library marker kkossev.commonLib, line 1961
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1962
    } // library marker kkossev.commonLib, line 1963
    else { // library marker kkossev.commonLib, line 1964
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1965
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1966
        runIn(timeRamaining, 'sendDelayedPm25Event',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1967
    } // library marker kkossev.commonLib, line 1968
} // library marker kkossev.commonLib, line 1969

private void sendDelayedPm25Event(Map eventMap) { // library marker kkossev.commonLib, line 1971
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1972
    state.lastRx['pm25Time'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1973
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1974
} // library marker kkossev.commonLib, line 1975

/* // library marker kkossev.commonLib, line 1977
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1978
 * Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1979
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1980
*/ // library marker kkossev.commonLib, line 1981
void parseAnalogInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1982
    if (DEVICE_TYPE in ["AirQuality"]) { // library marker kkossev.commonLib, line 1983
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1984
    } // library marker kkossev.commonLib, line 1985
    else if (DEVICE_TYPE in ["AqaraCube"]) { // library marker kkossev.commonLib, line 1986
        parseAqaraCubeAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1987
    } // library marker kkossev.commonLib, line 1988
    else { // library marker kkossev.commonLib, line 1989
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1990
    } // library marker kkossev.commonLib, line 1991
} // library marker kkossev.commonLib, line 1992


/* // library marker kkossev.commonLib, line 1995
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1996
 * Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1997
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1998
*/ // library marker kkossev.commonLib, line 1999

void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 2001
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2002
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 2003
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 2004
    Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 2005
    if (DEVICE_TYPE in  ["AqaraCube"]) { // library marker kkossev.commonLib, line 2006
        parseMultistateInputClusterAqaraCube(descMap) // library marker kkossev.commonLib, line 2007
    } // library marker kkossev.commonLib, line 2008
    else { // library marker kkossev.commonLib, line 2009
        handleMultistateInputEvent(value as Integer) // library marker kkossev.commonLib, line 2010
    } // library marker kkossev.commonLib, line 2011
} // library marker kkossev.commonLib, line 2012

void handleMultistateInputEvent( Integer value, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 2014
    def eventMap = [:] // library marker kkossev.commonLib, line 2015
    eventMap.value = value // library marker kkossev.commonLib, line 2016
    eventMap.name = "multistateInput" // library marker kkossev.commonLib, line 2017
    eventMap.unit = "" // library marker kkossev.commonLib, line 2018
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 2019
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 2020
    sendEvent(eventMap) // library marker kkossev.commonLib, line 2021
    logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 2022
} // library marker kkossev.commonLib, line 2023

/* // library marker kkossev.commonLib, line 2025
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2026
 * Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 2027
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2028
*/ // library marker kkossev.commonLib, line 2029

void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 2031
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2032
    if (DEVICE_TYPE in  ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 2033
        parseWindowCoveringClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 2034
    } // library marker kkossev.commonLib, line 2035
    else { // library marker kkossev.commonLib, line 2036
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 2037
    } // library marker kkossev.commonLib, line 2038
} // library marker kkossev.commonLib, line 2039

/* // library marker kkossev.commonLib, line 2041
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2042
 * thermostat cluster 0x0201 // library marker kkossev.commonLib, line 2043
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2044
*/ // library marker kkossev.commonLib, line 2045
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 2046
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2047
    if (DEVICE_TYPE in  ["Thermostat"]) { // library marker kkossev.commonLib, line 2048
        parseThermostatClusterThermostat(descMap) // library marker kkossev.commonLib, line 2049
    } // library marker kkossev.commonLib, line 2050
    else { // library marker kkossev.commonLib, line 2051
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 2052
    } // library marker kkossev.commonLib, line 2053
} // library marker kkossev.commonLib, line 2054

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2056

def parseFC11Cluster( descMap ) { // library marker kkossev.commonLib, line 2058
    if (DEVICE_TYPE in ["Thermostat"])     { parseFC11ClusterThermostat(descMap) }     // library marker kkossev.commonLib, line 2059
    else { // library marker kkossev.commonLib, line 2060
        logWarn "Unprocessed cluster 0xFC11 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" // library marker kkossev.commonLib, line 2061
    } // library marker kkossev.commonLib, line 2062
} // library marker kkossev.commonLib, line 2063

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2065

def parseE002Cluster( descMap ) { // library marker kkossev.commonLib, line 2067
    if (DEVICE_TYPE in ["Radar"])     { parseE002ClusterRadar(descMap) }     // library marker kkossev.commonLib, line 2068
    else { // library marker kkossev.commonLib, line 2069
        logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" // library marker kkossev.commonLib, line 2070
    } // library marker kkossev.commonLib, line 2071
} // library marker kkossev.commonLib, line 2072


/* // library marker kkossev.commonLib, line 2075
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2076
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 2077
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2078
*/ // library marker kkossev.commonLib, line 2079
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 2080
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 2081
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 2082

// Tuya Commands // library marker kkossev.commonLib, line 2084
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 2085
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 2086
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 2087
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 2088
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 2089

// tuya DP type // library marker kkossev.commonLib, line 2091
private static getDP_TYPE_RAW()        { "01" }    // [ bytes ] // library marker kkossev.commonLib, line 2092
private static getDP_TYPE_BOOL()       { "01" }    // [ 0/1 ] // library marker kkossev.commonLib, line 2093
private static getDP_TYPE_VALUE()      { "02" }    // [ 4 byte value ] // library marker kkossev.commonLib, line 2094
private static getDP_TYPE_STRING()     { "03" }    // [ N byte string ] // library marker kkossev.commonLib, line 2095
private static getDP_TYPE_ENUM()       { "04" }    // [ 0-255 ] // library marker kkossev.commonLib, line 2096
private static getDP_TYPE_BITMAP()     { "05" }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 2097


void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 2100
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME // library marker kkossev.commonLib, line 2101
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 2102
        def offset = 0 // library marker kkossev.commonLib, line 2103
        try { // library marker kkossev.commonLib, line 2104
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 2105
            //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}" // library marker kkossev.commonLib, line 2106
        } // library marker kkossev.commonLib, line 2107
        catch(e) { // library marker kkossev.commonLib, line 2108
            logWarn "cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 2109
        } // library marker kkossev.commonLib, line 2110
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8)) // library marker kkossev.commonLib, line 2111
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 2112
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 2113
        //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 2114
    } // library marker kkossev.commonLib, line 2115
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response // library marker kkossev.commonLib, line 2116
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 2117
        def status = descMap?.data[1]             // library marker kkossev.commonLib, line 2118
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 2119
        if (status != "00") { // library marker kkossev.commonLib, line 2120
            logWarn "ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                 // library marker kkossev.commonLib, line 2121
        } // library marker kkossev.commonLib, line 2122
    }  // library marker kkossev.commonLib, line 2123
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02" || descMap?.command == "05" || descMap?.command == "06")) // library marker kkossev.commonLib, line 2124
    { // library marker kkossev.commonLib, line 2125
        def dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 2126
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 2127
        def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 2128
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 2129
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 2130
            return // library marker kkossev.commonLib, line 2131
        } // library marker kkossev.commonLib, line 2132
        for (int i = 0; i < (dataLen-4); ) { // library marker kkossev.commonLib, line 2133
            def dp = zigbee.convertHexToInt(descMap?.data[2+i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 2134
            def dp_id = zigbee.convertHexToInt(descMap?.data[3+i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 2135
            def fncmd_len = zigbee.convertHexToInt(descMap?.data[5+i])  // library marker kkossev.commonLib, line 2136
            def fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 2137
            logDebug "dp_id=${dp_id} dp=${dp} fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 2138
            processTuyaDP( descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 2139
            i = i + fncmd_len + 4; // library marker kkossev.commonLib, line 2140
        } // library marker kkossev.commonLib, line 2141
    } // library marker kkossev.commonLib, line 2142
    else { // library marker kkossev.commonLib, line 2143
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 2144
    } // library marker kkossev.commonLib, line 2145
} // library marker kkossev.commonLib, line 2146

void processTuyaDP(descMap, dp, dp_id, fncmd, dp_len=0) { // library marker kkossev.commonLib, line 2148
    if (DEVICE_TYPE in ["Radar"])         { processTuyaDpRadar(descMap, dp, dp_id, fncmd); return }     // library marker kkossev.commonLib, line 2149
    if (DEVICE_TYPE in ["Fingerbot"])     { processTuyaDpFingerbot(descMap, dp, dp_id, fncmd); return }     // library marker kkossev.commonLib, line 2150
    // check if the method  method exists // library marker kkossev.commonLib, line 2151
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 2152
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0  // library marker kkossev.commonLib, line 2153
            return // library marker kkossev.commonLib, line 2154
        }     // library marker kkossev.commonLib, line 2155
    } // library marker kkossev.commonLib, line 2156
    switch (dp) { // library marker kkossev.commonLib, line 2157
        case 0x01 : // on/off // library marker kkossev.commonLib, line 2158
            if (DEVICE_TYPE in  ["LightSensor"]) { // library marker kkossev.commonLib, line 2159
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.commonLib, line 2160
            } // library marker kkossev.commonLib, line 2161
            else { // library marker kkossev.commonLib, line 2162
                sendSwitchEvent(fncmd) // library marker kkossev.commonLib, line 2163
            } // library marker kkossev.commonLib, line 2164
            break // library marker kkossev.commonLib, line 2165
        case 0x02 : // library marker kkossev.commonLib, line 2166
            if (DEVICE_TYPE in  ["LightSensor"]) { // library marker kkossev.commonLib, line 2167
                handleIlluminanceEvent(fncmd) // library marker kkossev.commonLib, line 2168
            } // library marker kkossev.commonLib, line 2169
            else { // library marker kkossev.commonLib, line 2170
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"  // library marker kkossev.commonLib, line 2171
            } // library marker kkossev.commonLib, line 2172
            break // library marker kkossev.commonLib, line 2173
        case 0x04 : // battery // library marker kkossev.commonLib, line 2174
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.commonLib, line 2175
            break // library marker kkossev.commonLib, line 2176
        default : // library marker kkossev.commonLib, line 2177
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"  // library marker kkossev.commonLib, line 2178
            break             // library marker kkossev.commonLib, line 2179
    } // library marker kkossev.commonLib, line 2180
} // library marker kkossev.commonLib, line 2181

private int getTuyaAttributeValue(ArrayList _data, index) { // library marker kkossev.commonLib, line 2183
    int retValue = 0 // library marker kkossev.commonLib, line 2184

    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 2186
        int dataLength = _data[5+index] as Integer // library marker kkossev.commonLib, line 2187
        int power = 1; // library marker kkossev.commonLib, line 2188
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 2189
            retValue = retValue + power * zigbee.convertHexToInt(_data[index+i+5]) // library marker kkossev.commonLib, line 2190
            power = power * 256 // library marker kkossev.commonLib, line 2191
        } // library marker kkossev.commonLib, line 2192
    } // library marker kkossev.commonLib, line 2193
    return retValue // library marker kkossev.commonLib, line 2194
} // library marker kkossev.commonLib, line 2195


private sendTuyaCommand(dp, dp_type, fncmd) { // library marker kkossev.commonLib, line 2198
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2199
    def ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 2200
    if (ep==null || ep==0) ep = 1 // library marker kkossev.commonLib, line 2201
    def tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 2202

    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd ) // library marker kkossev.commonLib, line 2204
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 2205
    return cmds // library marker kkossev.commonLib, line 2206
} // library marker kkossev.commonLib, line 2207

private getPACKET_ID() { // library marker kkossev.commonLib, line 2209
    return zigbee.convertToHexString(new Random().nextInt(65536), 4)  // library marker kkossev.commonLib, line 2210
} // library marker kkossev.commonLib, line 2211

def tuyaTest( dpCommand, dpValue, dpTypeString ) { // library marker kkossev.commonLib, line 2213
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2214
    def dpType   = dpTypeString=="DP_TYPE_VALUE" ? DP_TYPE_VALUE : dpTypeString=="DP_TYPE_BOOL" ? DP_TYPE_BOOL : dpTypeString=="DP_TYPE_ENUM" ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 2215
    def dpValHex = dpTypeString=="DP_TYPE_VALUE" ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 2216

    if (settings?.logEnable) log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" // library marker kkossev.commonLib, line 2218

    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 2220
} // library marker kkossev.commonLib, line 2221

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 2223
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 2224

def tuyaBlackMagic() { // library marker kkossev.commonLib, line 2226
    def ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 2227
    if (ep==null || ep==0) ep = 1 // library marker kkossev.commonLib, line 2228
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay=200) // library marker kkossev.commonLib, line 2229
} // library marker kkossev.commonLib, line 2230

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 2232
    List<String> cmds = [] // library marker kkossev.commonLib, line 2233
    if (isAqaraTVOC() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2234
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 200",] // library marker kkossev.commonLib, line 2235
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2236
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2237
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 2238
        if (isAqaraTVOC()) { // library marker kkossev.commonLib, line 2239
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay=200)    // TVOC only // library marker kkossev.commonLib, line 2240
        } // library marker kkossev.commonLib, line 2241
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 2242
        logDebug "sent aqaraBlackMagic()" // library marker kkossev.commonLib, line 2243
    } // library marker kkossev.commonLib, line 2244
    else { // library marker kkossev.commonLib, line 2245
        logDebug "aqaraBlackMagic() was SKIPPED" // library marker kkossev.commonLib, line 2246
    } // library marker kkossev.commonLib, line 2247
} // library marker kkossev.commonLib, line 2248


/** // library marker kkossev.commonLib, line 2251
 * initializes the device // library marker kkossev.commonLib, line 2252
 * Invoked from configure() // library marker kkossev.commonLib, line 2253
 * @return zigbee commands // library marker kkossev.commonLib, line 2254
 */ // library marker kkossev.commonLib, line 2255
def initializeDevice() { // library marker kkossev.commonLib, line 2256
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2257
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 2258

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 2260
    if (DEVICE_TYPE in  ["AirQuality"])          { return initializeDeviceAirQuality() } // library marker kkossev.commonLib, line 2261
    else if (DEVICE_TYPE in  ["IRBlaster"])      { return initializeDeviceIrBlaster() } // library marker kkossev.commonLib, line 2262
    else if (DEVICE_TYPE in  ["Radar"])          { return initializeDeviceRadar() } // library marker kkossev.commonLib, line 2263
    else if (DEVICE_TYPE in  ["ButtonDimmer"])   { return initializeDeviceButtonDimmer() } // library marker kkossev.commonLib, line 2264


    // not specific device type - do some generic initializations // library marker kkossev.commonLib, line 2267
    if (DEVICE_TYPE in  ["THSensor"]) { // library marker kkossev.commonLib, line 2268
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.commonLib, line 2269
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.commonLib, line 2270
    } // library marker kkossev.commonLib, line 2271
    // // library marker kkossev.commonLib, line 2272
    if (cmds == []) { // library marker kkossev.commonLib, line 2273
        cmds = ["delay 299"] // library marker kkossev.commonLib, line 2274
    } // library marker kkossev.commonLib, line 2275
    return cmds // library marker kkossev.commonLib, line 2276
} // library marker kkossev.commonLib, line 2277


/** // library marker kkossev.commonLib, line 2280
 * configures the device // library marker kkossev.commonLib, line 2281
 * Invoked from updated() // library marker kkossev.commonLib, line 2282
 * @return zigbee commands // library marker kkossev.commonLib, line 2283
 */ // library marker kkossev.commonLib, line 2284
def configureDevice() { // library marker kkossev.commonLib, line 2285
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2286
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 2287

    if (DEVICE_TYPE in  ["AirQuality"]) { cmds += configureDeviceAirQuality() } // library marker kkossev.commonLib, line 2289
    else if (DEVICE_TYPE in  ["Fingerbot"])  { cmds += configureDeviceFingerbot() } // library marker kkossev.commonLib, line 2290
    else if (DEVICE_TYPE in  ["AqaraCube"])  { cmds += configureDeviceAqaraCube() } // library marker kkossev.commonLib, line 2291
    else if (DEVICE_TYPE in  ["Switch"])     { cmds += configureDeviceSwitch() } // library marker kkossev.commonLib, line 2292
    else if (DEVICE_TYPE in  ["IRBlaster"])  { cmds += configureDeviceIrBlaster() } // library marker kkossev.commonLib, line 2293
    else if (DEVICE_TYPE in  ["Radar"])      { cmds += configureDeviceRadar() } // library marker kkossev.commonLib, line 2294
    else if (DEVICE_TYPE in  ["ButtonDimmer"]) { cmds += configureDeviceButtonDimmer() } // library marker kkossev.commonLib, line 2295
    else if (DEVICE_TYPE in  ["Bulb"])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 2296
    if (cmds == []) {  // library marker kkossev.commonLib, line 2297
        cmds = ["delay 277",] // library marker kkossev.commonLib, line 2298
    } // library marker kkossev.commonLib, line 2299
    sendZigbeeCommands(cmds)   // library marker kkossev.commonLib, line 2300
} // library marker kkossev.commonLib, line 2301

/* // library marker kkossev.commonLib, line 2303
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2304
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 2305
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2306
*/ // library marker kkossev.commonLib, line 2307

def refresh() { // library marker kkossev.commonLib, line 2309
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2310
    checkDriverVersion() // library marker kkossev.commonLib, line 2311
    List<String> cmds = [] // library marker kkossev.commonLib, line 2312
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 2313

    // device type specific refresh handlers // library marker kkossev.commonLib, line 2315
    if (DEVICE_TYPE in  ["AqaraCube"])       { cmds += refreshAqaraCube() } // library marker kkossev.commonLib, line 2316
    else if (DEVICE_TYPE in  ["Fingerbot"])  { cmds += refreshFingerbot() } // library marker kkossev.commonLib, line 2317
    else if (DEVICE_TYPE in  ["AirQuality"]) { cmds += refreshAirQuality() } // library marker kkossev.commonLib, line 2318
    else if (DEVICE_TYPE in  ["Switch"])     { cmds += refreshSwitch() } // library marker kkossev.commonLib, line 2319
    else if (DEVICE_TYPE in  ["IRBlaster"])  { cmds += refreshIrBlaster() } // library marker kkossev.commonLib, line 2320
    else if (DEVICE_TYPE in  ["Radar"])      { cmds += refreshRadar() } // library marker kkossev.commonLib, line 2321
    else if (DEVICE_TYPE in  ["Thermostat"]) { cmds += refreshThermostat() } // library marker kkossev.commonLib, line 2322
    else if (DEVICE_TYPE in  ["Bulb"])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 2323
    else { // library marker kkossev.commonLib, line 2324
        // generic refresh handling, based on teh device capabilities  // library marker kkossev.commonLib, line 2325
        if (device.hasCapability("Battery")) { // library marker kkossev.commonLib, line 2326
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)         // battery voltage // library marker kkossev.commonLib, line 2327
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay=200)         // battery percentage  // library marker kkossev.commonLib, line 2328
        } // library marker kkossev.commonLib, line 2329
        if (DEVICE_TYPE in  ["Plug", "Dimmer"]) { // library marker kkossev.commonLib, line 2330
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay=200) // library marker kkossev.commonLib, line 2331
            cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership // library marker kkossev.commonLib, line 2332
        } // library marker kkossev.commonLib, line 2333
        if (DEVICE_TYPE in  ["Dimmer"]) { // library marker kkossev.commonLib, line 2334
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay=200)         // library marker kkossev.commonLib, line 2335
        } // library marker kkossev.commonLib, line 2336
        if (DEVICE_TYPE in  ["THSensor", "AirQuality"]) { // library marker kkossev.commonLib, line 2337
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay=200)         // library marker kkossev.commonLib, line 2338
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay=200)         // library marker kkossev.commonLib, line 2339
        } // library marker kkossev.commonLib, line 2340
    } // library marker kkossev.commonLib, line 2341

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2343
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2344
    } // library marker kkossev.commonLib, line 2345
    else { // library marker kkossev.commonLib, line 2346
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2347
    } // library marker kkossev.commonLib, line 2348
} // library marker kkossev.commonLib, line 2349

def setRefreshRequest()   { if (state.states == null) {state.states = [:]};   state.states["isRefresh"] = true; runInMillis( REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) }                 // 3 seconds // library marker kkossev.commonLib, line 2351
def clearRefreshRequest() { if (state.states == null) {state.states = [:] }; state.states["isRefresh"] = false } // library marker kkossev.commonLib, line 2352

void clearInfoEvent() { // library marker kkossev.commonLib, line 2354
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 2355
} // library marker kkossev.commonLib, line 2356

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 2358
    if (info == null || info == "clear") { // library marker kkossev.commonLib, line 2359
        logDebug "clearing the Status event" // library marker kkossev.commonLib, line 2360
        sendEvent(name: "Status", value: "clear", isDigital: true) // library marker kkossev.commonLib, line 2361
    } // library marker kkossev.commonLib, line 2362
    else { // library marker kkossev.commonLib, line 2363
        logInfo "${info}" // library marker kkossev.commonLib, line 2364
        sendEvent(name: "Status", value: info, isDigital: true) // library marker kkossev.commonLib, line 2365
        runIn(INFO_AUTO_CLEAR_PERIOD, "clearInfoEvent")            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 2366
    } // library marker kkossev.commonLib, line 2367
} // library marker kkossev.commonLib, line 2368

def ping() { // library marker kkossev.commonLib, line 2370
    if (!(isAqaraTVOC())) { // library marker kkossev.commonLib, line 2371
        if (state.lastTx == nill ) state.lastTx = [:]  // library marker kkossev.commonLib, line 2372
        state.lastTx["pingTime"] = new Date().getTime() // library marker kkossev.commonLib, line 2373
        if (state.states == nill ) state.states = [:]  // library marker kkossev.commonLib, line 2374
        state.states["isPing"] = true // library marker kkossev.commonLib, line 2375
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 2376
        sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 2377
        logDebug 'ping...' // library marker kkossev.commonLib, line 2378
    } // library marker kkossev.commonLib, line 2379
    else { // library marker kkossev.commonLib, line 2380
        // Aqara TVOC is sleepy or does not respond to the ping. // library marker kkossev.commonLib, line 2381
        logInfo "ping() command is not available for this sleepy device." // library marker kkossev.commonLib, line 2382
        sendRttEvent("n/a") // library marker kkossev.commonLib, line 2383
    } // library marker kkossev.commonLib, line 2384
} // library marker kkossev.commonLib, line 2385

/** // library marker kkossev.commonLib, line 2387
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 2388
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 2389
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 2390
 * @return none // library marker kkossev.commonLib, line 2391
 */ // library marker kkossev.commonLib, line 2392
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 2393
    def now = new Date().getTime() // library marker kkossev.commonLib, line 2394
    if (state.lastTx == null ) state.lastTx = [:] // library marker kkossev.commonLib, line 2395
    def timeRunning = now.toInteger() - (state.lastTx["pingTime"] ?: now).toInteger() // library marker kkossev.commonLib, line 2396
    def descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats["pingsMin"]} max=${state.stats["pingsMax"]} average=${state.stats["pingsAvg"]})" // library marker kkossev.commonLib, line 2397
    if (value == null) { // library marker kkossev.commonLib, line 2398
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2399
        sendEvent(name: "rtt", value: timeRunning, descriptionText: descriptionText, unit: "ms", isDigital: true)     // library marker kkossev.commonLib, line 2400
    } // library marker kkossev.commonLib, line 2401
    else { // library marker kkossev.commonLib, line 2402
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 2403
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2404
        sendEvent(name: "rtt", value: value, descriptionText: descriptionText, isDigital: true)     // library marker kkossev.commonLib, line 2405
    } // library marker kkossev.commonLib, line 2406
} // library marker kkossev.commonLib, line 2407

/** // library marker kkossev.commonLib, line 2409
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 2410
 * @param cluster cluster ID // library marker kkossev.commonLib, line 2411
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 2412
 */ // library marker kkossev.commonLib, line 2413
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 2414
    if (cluster != null) { // library marker kkossev.commonLib, line 2415
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 2416
    } // library marker kkossev.commonLib, line 2417
    else { // library marker kkossev.commonLib, line 2418
        logWarn "cluster is NULL!" // library marker kkossev.commonLib, line 2419
        return "NULL" // library marker kkossev.commonLib, line 2420
    } // library marker kkossev.commonLib, line 2421
} // library marker kkossev.commonLib, line 2422

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 2424
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 2425
} // library marker kkossev.commonLib, line 2426

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 2428
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 2429
    sendRttEvent("timeout") // library marker kkossev.commonLib, line 2430
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 2431
} // library marker kkossev.commonLib, line 2432

/** // library marker kkossev.commonLib, line 2434
 * Schedule a device health check // library marker kkossev.commonLib, line 2435
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 2436
 */ // library marker kkossev.commonLib, line 2437
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 2438
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 2439
        String cron = getCron( intervalMins*60 ) // library marker kkossev.commonLib, line 2440
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 2441
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 2442
    } // library marker kkossev.commonLib, line 2443
    else { // library marker kkossev.commonLib, line 2444
        logWarn "deviceHealthCheck is not scheduled!" // library marker kkossev.commonLib, line 2445
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2446
    } // library marker kkossev.commonLib, line 2447
} // library marker kkossev.commonLib, line 2448

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 2450
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2451
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 2452
    logWarn "device health check is disabled!" // library marker kkossev.commonLib, line 2453

} // library marker kkossev.commonLib, line 2455

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 2457
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 2458
    if(state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2459
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 2460
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) {    // library marker kkossev.commonLib, line 2461
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 2462
        logInfo "is now online!" // library marker kkossev.commonLib, line 2463
    } // library marker kkossev.commonLib, line 2464
} // library marker kkossev.commonLib, line 2465


def deviceHealthCheck() { // library marker kkossev.commonLib, line 2468
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2469
    def ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 2470
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 2471
        if ((device.currentValue("healthStatus") ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 2472
            logWarn "not present!" // library marker kkossev.commonLib, line 2473
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 2474
        } // library marker kkossev.commonLib, line 2475
    } // library marker kkossev.commonLib, line 2476
    else { // library marker kkossev.commonLib, line 2477
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 2478
    } // library marker kkossev.commonLib, line 2479
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 2480
} // library marker kkossev.commonLib, line 2481

void sendHealthStatusEvent(value) { // library marker kkossev.commonLib, line 2483
    def descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 2484
    sendEvent(name: "healthStatus", value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 2485
    if (value == 'online') { // library marker kkossev.commonLib, line 2486
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2487
    } // library marker kkossev.commonLib, line 2488
    else { // library marker kkossev.commonLib, line 2489
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 2490
    } // library marker kkossev.commonLib, line 2491
} // library marker kkossev.commonLib, line 2492



/** // library marker kkossev.commonLib, line 2496
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 2497
 */ // library marker kkossev.commonLib, line 2498
void autoPoll() { // library marker kkossev.commonLib, line 2499
    logDebug "autoPoll()..." // library marker kkossev.commonLib, line 2500
    checkDriverVersion() // library marker kkossev.commonLib, line 2501
    List<String> cmds = [] // library marker kkossev.commonLib, line 2502
    if (state.states == null) state.states = [:] // library marker kkossev.commonLib, line 2503
    //state.states["isRefresh"] = true // library marker kkossev.commonLib, line 2504

    if (DEVICE_TYPE in  ["AirQuality"]) { // library marker kkossev.commonLib, line 2506
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay=200)      // tVOC   !! mfcode="0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 2507
    } // library marker kkossev.commonLib, line 2508

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2510
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2511
    }     // library marker kkossev.commonLib, line 2512
} // library marker kkossev.commonLib, line 2513


/** // library marker kkossev.commonLib, line 2516
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 2517
 */ // library marker kkossev.commonLib, line 2518
void updated() { // library marker kkossev.commonLib, line 2519
    logInfo 'updated...' // library marker kkossev.commonLib, line 2520
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2521
    unschedule() // library marker kkossev.commonLib, line 2522

    if (settings.logEnable) { // library marker kkossev.commonLib, line 2524
        logTrace settings // library marker kkossev.commonLib, line 2525
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 2526
    } // library marker kkossev.commonLib, line 2527
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2528
        logTrace settings // library marker kkossev.commonLib, line 2529
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 2530
    }     // library marker kkossev.commonLib, line 2531

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 2533
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 2534
        // schedule the periodic timer // library marker kkossev.commonLib, line 2535
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 2536
        if (interval > 0) { // library marker kkossev.commonLib, line 2537
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 2538
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 2539
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 2540
        } // library marker kkossev.commonLib, line 2541
    } // library marker kkossev.commonLib, line 2542
    else { // library marker kkossev.commonLib, line 2543
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 2544
        log.info "Health Check is disabled!" // library marker kkossev.commonLib, line 2545
    } // library marker kkossev.commonLib, line 2546

    if (DEVICE_TYPE in ["AirQuality"])  { updatedAirQuality() } // library marker kkossev.commonLib, line 2548
    if (DEVICE_TYPE in ["IRBlaster"])   { updatedIrBlaster() } // library marker kkossev.commonLib, line 2549
    if (DEVICE_TYPE in ["Thermostat"])  { updatedThermostat() } // library marker kkossev.commonLib, line 2550

    //configureDevice()    // sends Zigbee commands  // commented out 11/18/2023 // library marker kkossev.commonLib, line 2552

    sendInfoEvent("updated") // library marker kkossev.commonLib, line 2554
} // library marker kkossev.commonLib, line 2555

/** // library marker kkossev.commonLib, line 2557
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 2558
 */ // library marker kkossev.commonLib, line 2559
void logsOff() { // library marker kkossev.commonLib, line 2560
    logInfo "debug logging disabled..." // library marker kkossev.commonLib, line 2561
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2562
} // library marker kkossev.commonLib, line 2563
void traceOff() { // library marker kkossev.commonLib, line 2564
    logInfo "trace logging disabled..." // library marker kkossev.commonLib, line 2565
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2566
} // library marker kkossev.commonLib, line 2567

def configure(command) { // library marker kkossev.commonLib, line 2569
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2570
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 2571

    Boolean validated = false // library marker kkossev.commonLib, line 2573
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 2574
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 2575
        return // library marker kkossev.commonLib, line 2576
    } // library marker kkossev.commonLib, line 2577
    // // library marker kkossev.commonLib, line 2578
    def func // library marker kkossev.commonLib, line 2579
   // try { // library marker kkossev.commonLib, line 2580
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 2581
        cmds = "$func"() // library marker kkossev.commonLib, line 2582
 //   } // library marker kkossev.commonLib, line 2583
//    catch (e) { // library marker kkossev.commonLib, line 2584
//        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 2585
//        return // library marker kkossev.commonLib, line 2586
//    } // library marker kkossev.commonLib, line 2587

    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 2589
} // library marker kkossev.commonLib, line 2590

def configureHelp( val ) { // library marker kkossev.commonLib, line 2592
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 2593
} // library marker kkossev.commonLib, line 2594

def loadAllDefaults() { // library marker kkossev.commonLib, line 2596
    logWarn "loadAllDefaults() !!!" // library marker kkossev.commonLib, line 2597
    deleteAllSettings() // library marker kkossev.commonLib, line 2598
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 2599
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 2600
    deleteAllStates() // library marker kkossev.commonLib, line 2601
    deleteAllChildDevices() // library marker kkossev.commonLib, line 2602
    initialize() // library marker kkossev.commonLib, line 2603
    configure() // library marker kkossev.commonLib, line 2604
    updated() // calls  also   configureDevice() // library marker kkossev.commonLib, line 2605
    sendInfoEvent("All Defaults Loaded! F5 to refresh") // library marker kkossev.commonLib, line 2606
} // library marker kkossev.commonLib, line 2607

/** // library marker kkossev.commonLib, line 2609
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 2610
 * Invoked when device is first installed and when the user updates the configuration // library marker kkossev.commonLib, line 2611
 * @return sends zigbee commands // library marker kkossev.commonLib, line 2612
 */ // library marker kkossev.commonLib, line 2613
def configure() { // library marker kkossev.commonLib, line 2614
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2615
    logInfo 'configure...' // library marker kkossev.commonLib, line 2616
    logDebug settings // library marker kkossev.commonLib, line 2617
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 2618
    if (isAqaraTVOC() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2619
        aqaraBlackMagic() // library marker kkossev.commonLib, line 2620
    } // library marker kkossev.commonLib, line 2621
    cmds += initializeDevice() // library marker kkossev.commonLib, line 2622
    cmds += configureDevice() // library marker kkossev.commonLib, line 2623
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2624
    sendInfoEvent("sent device configuration") // library marker kkossev.commonLib, line 2625
} // library marker kkossev.commonLib, line 2626

/** // library marker kkossev.commonLib, line 2628
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 2629
 */ // library marker kkossev.commonLib, line 2630
void installed() { // library marker kkossev.commonLib, line 2631
    logInfo 'installed...' // library marker kkossev.commonLib, line 2632
    // populate some default values for attributes // library marker kkossev.commonLib, line 2633
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 2634
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 2635
    sendInfoEvent("installed") // library marker kkossev.commonLib, line 2636
    runIn(3, 'updated') // library marker kkossev.commonLib, line 2637
} // library marker kkossev.commonLib, line 2638

/** // library marker kkossev.commonLib, line 2640
 * Invoked when initialize button is clicked // library marker kkossev.commonLib, line 2641
 */ // library marker kkossev.commonLib, line 2642
void initialize() { // library marker kkossev.commonLib, line 2643
    logInfo 'initialize...' // library marker kkossev.commonLib, line 2644
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 2645
    updateTuyaVersion() // library marker kkossev.commonLib, line 2646
    updateAqaraVersion() // library marker kkossev.commonLib, line 2647
} // library marker kkossev.commonLib, line 2648


/* // library marker kkossev.commonLib, line 2651
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2652
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 2653
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2654
*/ // library marker kkossev.commonLib, line 2655

static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 2657
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 2658
} // library marker kkossev.commonLib, line 2659

static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 2661
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 2662
} // library marker kkossev.commonLib, line 2663

void sendZigbeeCommands(ArrayList<String> cmd) { // library marker kkossev.commonLib, line 2665
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 2666
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 2667
    cmd.each { // library marker kkossev.commonLib, line 2668
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 2669
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats=[:] } // library marker kkossev.commonLib, line 2670
    } // library marker kkossev.commonLib, line 2671
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 2672
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 2673
} // library marker kkossev.commonLib, line 2674

def driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? " (debug version!) " : " ") + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString}) "} // library marker kkossev.commonLib, line 2676

def getDeviceInfo() { // library marker kkossev.commonLib, line 2678
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 2679
} // library marker kkossev.commonLib, line 2680

def getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 2682
    return state.destinationEP ?: device.endpointId ?: "01" // library marker kkossev.commonLib, line 2683
} // library marker kkossev.commonLib, line 2684

def checkDriverVersion() { // library marker kkossev.commonLib, line 2686
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 2687
        logDebug "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2688
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 2689
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2690
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 2691
        updateTuyaVersion() // library marker kkossev.commonLib, line 2692
        updateAqaraVersion() // library marker kkossev.commonLib, line 2693
    } // library marker kkossev.commonLib, line 2694
    else { // library marker kkossev.commonLib, line 2695
        // no driver version change // library marker kkossev.commonLib, line 2696
    } // library marker kkossev.commonLib, line 2697
} // library marker kkossev.commonLib, line 2698

// credits @thebearmay // library marker kkossev.commonLib, line 2700
String getModel(){ // library marker kkossev.commonLib, line 2701
    try{ // library marker kkossev.commonLib, line 2702
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2703
    } catch (ignore){ // library marker kkossev.commonLib, line 2704
        try{ // library marker kkossev.commonLib, line 2705
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2706
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2707
            return model // library marker kkossev.commonLib, line 2708
            }         // library marker kkossev.commonLib, line 2709
        } catch(ignore_again) { // library marker kkossev.commonLib, line 2710
            return "" // library marker kkossev.commonLib, line 2711
        } // library marker kkossev.commonLib, line 2712
    } // library marker kkossev.commonLib, line 2713
} // library marker kkossev.commonLib, line 2714

// credits @thebearmay // library marker kkossev.commonLib, line 2716
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2717
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2718
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2719
    String revision = tokens.last() // library marker kkossev.commonLib, line 2720
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2721
} // library marker kkossev.commonLib, line 2722

/** // library marker kkossev.commonLib, line 2724
 * called from TODO // library marker kkossev.commonLib, line 2725
 *  // library marker kkossev.commonLib, line 2726
 */ // library marker kkossev.commonLib, line 2727

def deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2729
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2730
    unschedule() // library marker kkossev.commonLib, line 2731
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2732
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2733

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2735
} // library marker kkossev.commonLib, line 2736


def resetStatistics() { // library marker kkossev.commonLib, line 2739
    runIn(1, "resetStats") // library marker kkossev.commonLib, line 2740
    sendInfoEvent("Statistics are reset. Refresh the web page") // library marker kkossev.commonLib, line 2741
} // library marker kkossev.commonLib, line 2742

/** // library marker kkossev.commonLib, line 2744
 * called from TODO // library marker kkossev.commonLib, line 2745
 *  // library marker kkossev.commonLib, line 2746
 */ // library marker kkossev.commonLib, line 2747
def resetStats() { // library marker kkossev.commonLib, line 2748
    logDebug "resetStats..." // library marker kkossev.commonLib, line 2749
    state.stats = [:] // library marker kkossev.commonLib, line 2750
    state.states = [:] // library marker kkossev.commonLib, line 2751
    state.lastRx = [:] // library marker kkossev.commonLib, line 2752
    state.lastTx = [:] // library marker kkossev.commonLib, line 2753
    state.health = [:] // library marker kkossev.commonLib, line 2754
    state.zigbeeGroups = [:]  // library marker kkossev.commonLib, line 2755
    state.stats["rxCtr"] = 0 // library marker kkossev.commonLib, line 2756
    state.stats["txCtr"] = 0 // library marker kkossev.commonLib, line 2757
    state.states["isDigital"] = false // library marker kkossev.commonLib, line 2758
    state.states["isRefresh"] = false // library marker kkossev.commonLib, line 2759
    state.health["offlineCtr"] = 0 // library marker kkossev.commonLib, line 2760
    state.health["checkCtr3"] = 0 // library marker kkossev.commonLib, line 2761
} // library marker kkossev.commonLib, line 2762

/** // library marker kkossev.commonLib, line 2764
 * called from TODO // library marker kkossev.commonLib, line 2765
 *  // library marker kkossev.commonLib, line 2766
 */ // library marker kkossev.commonLib, line 2767
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2768
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2769
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2770
        state.clear() // library marker kkossev.commonLib, line 2771
        unschedule() // library marker kkossev.commonLib, line 2772
        resetStats() // library marker kkossev.commonLib, line 2773
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2774
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2775
        logInfo "all states and scheduled jobs cleared!" // library marker kkossev.commonLib, line 2776
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2777
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2778
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2779
        sendInfoEvent("Initialized") // library marker kkossev.commonLib, line 2780
    } // library marker kkossev.commonLib, line 2781

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2783
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2784
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2785
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2786
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2787
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 2788

    if (fullInit || settings?.txtEnable == null) device.updateSetting("txtEnable", true) // library marker kkossev.commonLib, line 2790
    if (fullInit || settings?.logEnable == null) device.updateSetting("logEnable", false) // library marker kkossev.commonLib, line 2791
    if (fullInit || settings?.traceEnable == null) device.updateSetting("traceEnable", false) // library marker kkossev.commonLib, line 2792
    if (fullInit || settings?.alwaysOn == null) device.updateSetting("alwaysOn", false) // library marker kkossev.commonLib, line 2793
    if (fullInit || settings?.advancedOptions == null) device.updateSetting("advancedOptions", [value:false, type:"bool"]) // library marker kkossev.commonLib, line 2794
    if (fullInit || settings?.healthCheckMethod == null) device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) // library marker kkossev.commonLib, line 2795
    if (fullInit || settings?.healthCheckInterval == null) device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) // library marker kkossev.commonLib, line 2796
    if (device.currentValue('healthStatus') == null) sendHealthStatusEvent('unknown') // library marker kkossev.commonLib, line 2797
    if (fullInit || settings?.voltageToPercent == null) device.updateSetting("voltageToPercent", false) // library marker kkossev.commonLib, line 2798
    if (device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 2799
        if (fullInit || settings?.minReportingTime == null) device.updateSetting("minReportingTime", [value:DEFAULT_MIN_REPORTING_TIME, type:"number"]) // library marker kkossev.commonLib, line 2800
        if (fullInit || settings?.maxReportingTime == null) device.updateSetting("maxReportingTime", [value:DEFAULT_MAX_REPORTING_TIME, type:"number"]) // library marker kkossev.commonLib, line 2801
    } // library marker kkossev.commonLib, line 2802
    if (device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 2803
        if (fullInit || settings?.illuminanceThreshold == null) device.updateSetting("illuminanceThreshold", [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:"number"]) // library marker kkossev.commonLib, line 2804
        if (fullInit || settings?.illuminanceCoeff == null) device.updateSetting("illuminanceCoeff", [value:1.00, type:"decimal"]) // library marker kkossev.commonLib, line 2805
    } // library marker kkossev.commonLib, line 2806
    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2807
    if (DEVICE_TYPE in ["AirQuality"]) { initVarsAirQuality(fullInit) } // library marker kkossev.commonLib, line 2808
    if (DEVICE_TYPE in ["Fingerbot"])  { initVarsFingerbot(fullInit); initEventsFingerbot(fullInit) } // library marker kkossev.commonLib, line 2809
    if (DEVICE_TYPE in ["AqaraCube"])  { initVarsAqaraCube(fullInit); initEventsAqaraCube(fullInit) } // library marker kkossev.commonLib, line 2810
    if (DEVICE_TYPE in ["Switch"])     { initVarsSwitch(fullInit);    initEventsSwitch(fullInit) }         // threeStateEnable, ignoreDuplicated // library marker kkossev.commonLib, line 2811
    if (DEVICE_TYPE in ["IRBlaster"])  { initVarsIrBlaster(fullInit); initEventsIrBlaster(fullInit) }      // none // library marker kkossev.commonLib, line 2812
    if (DEVICE_TYPE in ["Radar"])      { initVarsRadar(fullInit);     initEventsRadar(fullInit) }          // none // library marker kkossev.commonLib, line 2813
    if (DEVICE_TYPE in ["ButtonDimmer"]) { initVarsButtonDimmer(fullInit);     initEventsButtonDimmer(fullInit) } // library marker kkossev.commonLib, line 2814
    if (DEVICE_TYPE in ["Thermostat"]) { initVarsThermostat(fullInit);     initEventsThermostat(fullInit) } // library marker kkossev.commonLib, line 2815
    if (DEVICE_TYPE in ["Bulb"])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2816

    def mm = device.getDataValue("model") // library marker kkossev.commonLib, line 2818
    if ( mm != null) { // library marker kkossev.commonLib, line 2819
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 2820
    } // library marker kkossev.commonLib, line 2821
    else { // library marker kkossev.commonLib, line 2822
        logWarn " Model not found, please re-pair the device!" // library marker kkossev.commonLib, line 2823
    } // library marker kkossev.commonLib, line 2824
    def ep = device.getEndpointId() // library marker kkossev.commonLib, line 2825
    if ( ep  != null) { // library marker kkossev.commonLib, line 2826
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2827
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2828
    } // library marker kkossev.commonLib, line 2829
    else { // library marker kkossev.commonLib, line 2830
        logWarn " Destination End Point not found, please re-pair the device!" // library marker kkossev.commonLib, line 2831
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2832
    }     // library marker kkossev.commonLib, line 2833
} // library marker kkossev.commonLib, line 2834


/** // library marker kkossev.commonLib, line 2837
 * called from TODO // library marker kkossev.commonLib, line 2838
 *  // library marker kkossev.commonLib, line 2839
 */ // library marker kkossev.commonLib, line 2840
def setDestinationEP() { // library marker kkossev.commonLib, line 2841
    def ep = device.getEndpointId() // library marker kkossev.commonLib, line 2842
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2843
        state.destinationEP = ep // library marker kkossev.commonLib, line 2844
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2845
    } // library marker kkossev.commonLib, line 2846
    else { // library marker kkossev.commonLib, line 2847
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2848
        state.destinationEP = "01"    // fallback EP // library marker kkossev.commonLib, line 2849
    }       // library marker kkossev.commonLib, line 2850
} // library marker kkossev.commonLib, line 2851


def logDebug(msg) { // library marker kkossev.commonLib, line 2854
    if (settings.logEnable) { // library marker kkossev.commonLib, line 2855
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2856
    } // library marker kkossev.commonLib, line 2857
} // library marker kkossev.commonLib, line 2858

def logInfo(msg) { // library marker kkossev.commonLib, line 2860
    if (settings.txtEnable) { // library marker kkossev.commonLib, line 2861
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2862
    } // library marker kkossev.commonLib, line 2863
} // library marker kkossev.commonLib, line 2864

def logWarn(msg) { // library marker kkossev.commonLib, line 2866
    if (settings.logEnable) { // library marker kkossev.commonLib, line 2867
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2868
    } // library marker kkossev.commonLib, line 2869
} // library marker kkossev.commonLib, line 2870

def logTrace(msg) { // library marker kkossev.commonLib, line 2872
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2873
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2874
    } // library marker kkossev.commonLib, line 2875
} // library marker kkossev.commonLib, line 2876



// _DEBUG mode only // library marker kkossev.commonLib, line 2880
void getAllProperties() { // library marker kkossev.commonLib, line 2881
    log.trace 'Properties:'     // library marker kkossev.commonLib, line 2882
    device.properties.each { it-> // library marker kkossev.commonLib, line 2883
        log.debug it // library marker kkossev.commonLib, line 2884
    } // library marker kkossev.commonLib, line 2885
    log.trace 'Settings:'     // library marker kkossev.commonLib, line 2886
    settings.each { it-> // library marker kkossev.commonLib, line 2887
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2888
    }     // library marker kkossev.commonLib, line 2889
    log.trace 'Done'     // library marker kkossev.commonLib, line 2890
} // library marker kkossev.commonLib, line 2891

// delete all Preferences // library marker kkossev.commonLib, line 2893
void deleteAllSettings() { // library marker kkossev.commonLib, line 2894
    settings.each { it-> // library marker kkossev.commonLib, line 2895
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2896
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2897
    } // library marker kkossev.commonLib, line 2898
    logInfo  "All settings (preferences) DELETED" // library marker kkossev.commonLib, line 2899
} // library marker kkossev.commonLib, line 2900

// delete all attributes // library marker kkossev.commonLib, line 2902
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2903
    device.properties.supportedAttributes.each { it-> // library marker kkossev.commonLib, line 2904
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2905
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2906
    } // library marker kkossev.commonLib, line 2907
    logInfo "All current states (attributes) DELETED" // library marker kkossev.commonLib, line 2908
} // library marker kkossev.commonLib, line 2909

// delete all State Variables // library marker kkossev.commonLib, line 2911
void deleteAllStates() { // library marker kkossev.commonLib, line 2912
    state.each { it-> // library marker kkossev.commonLib, line 2913
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2914
    } // library marker kkossev.commonLib, line 2915
    state.clear() // library marker kkossev.commonLib, line 2916
    logInfo "All States DELETED" // library marker kkossev.commonLib, line 2917
} // library marker kkossev.commonLib, line 2918

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2920
    unschedule() // library marker kkossev.commonLib, line 2921
    logInfo "All scheduled jobs DELETED" // library marker kkossev.commonLib, line 2922
} // library marker kkossev.commonLib, line 2923

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2925
    logDebug "deleteAllChildDevices : not implemented!" // library marker kkossev.commonLib, line 2926
} // library marker kkossev.commonLib, line 2927

def parseTest(par) { // library marker kkossev.commonLib, line 2929
//read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2930
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2931
    parse(par) // library marker kkossev.commonLib, line 2932
} // library marker kkossev.commonLib, line 2933

def testJob() { // library marker kkossev.commonLib, line 2935
    log.warn "test job executed" // library marker kkossev.commonLib, line 2936
} // library marker kkossev.commonLib, line 2937

/** // library marker kkossev.commonLib, line 2939
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2940
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2941
 */ // library marker kkossev.commonLib, line 2942
def getCron( timeInSeconds ) { // library marker kkossev.commonLib, line 2943
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2944
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2945
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2946
    def minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2947
    def hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2948
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2949
    String cron // library marker kkossev.commonLib, line 2950
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2951
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2952
    } // library marker kkossev.commonLib, line 2953
    else { // library marker kkossev.commonLib, line 2954
        if (minutes < 60) { // library marker kkossev.commonLib, line 2955
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *"   // library marker kkossev.commonLib, line 2956
        } // library marker kkossev.commonLib, line 2957
        else { // library marker kkossev.commonLib, line 2958
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"                    // library marker kkossev.commonLib, line 2959
        } // library marker kkossev.commonLib, line 2960
    } // library marker kkossev.commonLib, line 2961
    return cron // library marker kkossev.commonLib, line 2962
} // library marker kkossev.commonLib, line 2963

boolean isTuya() { // library marker kkossev.commonLib, line 2965
    def model = device.getDataValue("model")  // library marker kkossev.commonLib, line 2966
    def manufacturer = device.getDataValue("manufacturer")  // library marker kkossev.commonLib, line 2967
    if (model?.startsWith("TS") && manufacturer?.startsWith("_TZ")) { // library marker kkossev.commonLib, line 2968
        return true // library marker kkossev.commonLib, line 2969
    } // library marker kkossev.commonLib, line 2970
    return false // library marker kkossev.commonLib, line 2971
} // library marker kkossev.commonLib, line 2972

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2974
    if (!isTuya()) { // library marker kkossev.commonLib, line 2975
        logTrace "not Tuya" // library marker kkossev.commonLib, line 2976
        return // library marker kkossev.commonLib, line 2977
    } // library marker kkossev.commonLib, line 2978
    def application = device.getDataValue("application")  // library marker kkossev.commonLib, line 2979
    if (application != null) { // library marker kkossev.commonLib, line 2980
        Integer ver // library marker kkossev.commonLib, line 2981
        try { // library marker kkossev.commonLib, line 2982
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2983
        } // library marker kkossev.commonLib, line 2984
        catch (e) { // library marker kkossev.commonLib, line 2985
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2986
            return // library marker kkossev.commonLib, line 2987
        } // library marker kkossev.commonLib, line 2988
        def str = ((ver&0xC0)>>6).toString() + "." + ((ver&0x30)>>4).toString() + "." + (ver&0x0F).toString() // library marker kkossev.commonLib, line 2989
        if (device.getDataValue("tuyaVersion") != str) { // library marker kkossev.commonLib, line 2990
            device.updateDataValue("tuyaVersion", str) // library marker kkossev.commonLib, line 2991
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2992
        } // library marker kkossev.commonLib, line 2993
    } // library marker kkossev.commonLib, line 2994
} // library marker kkossev.commonLib, line 2995

boolean isAqara() { // library marker kkossev.commonLib, line 2997
    def model = device.getDataValue("model")  // library marker kkossev.commonLib, line 2998
    def manufacturer = device.getDataValue("manufacturer")  // library marker kkossev.commonLib, line 2999
    if (model?.startsWith("lumi")) { // library marker kkossev.commonLib, line 3000
        return true // library marker kkossev.commonLib, line 3001
    } // library marker kkossev.commonLib, line 3002
    return false // library marker kkossev.commonLib, line 3003
} // library marker kkossev.commonLib, line 3004

def updateAqaraVersion() { // library marker kkossev.commonLib, line 3006
    if (!isAqara()) { // library marker kkossev.commonLib, line 3007
        logTrace "not Aqara" // library marker kkossev.commonLib, line 3008
        return // library marker kkossev.commonLib, line 3009
    }     // library marker kkossev.commonLib, line 3010
    def application = device.getDataValue("application")  // library marker kkossev.commonLib, line 3011
    if (application != null) { // library marker kkossev.commonLib, line 3012
        def str = "0.0.0_" + String.format("%04d", zigbee.convertHexToInt(application.substring(0, Math.min(application.length(), 2)))); // library marker kkossev.commonLib, line 3013
        if (device.getDataValue("aqaraVersion") != str) { // library marker kkossev.commonLib, line 3014
            device.updateDataValue("aqaraVersion", str) // library marker kkossev.commonLib, line 3015
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 3016
        } // library marker kkossev.commonLib, line 3017
    } // library marker kkossev.commonLib, line 3018
    else { // library marker kkossev.commonLib, line 3019
        return null // library marker kkossev.commonLib, line 3020
    } // library marker kkossev.commonLib, line 3021
} // library marker kkossev.commonLib, line 3022

def test(par) { // library marker kkossev.commonLib, line 3024
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 3025
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 3026

    parse(par) // library marker kkossev.commonLib, line 3028

   // sendZigbeeCommands(cmds)     // library marker kkossev.commonLib, line 3030
} // library marker kkossev.commonLib, line 3031

// /////////////////////////////////////////////////////////////////// Libraries ////////////////////////////////////////////////////////////////////// // library marker kkossev.commonLib, line 3033



// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (141) kkossev.xiaomiLib ~~~~~
library ( // library marker kkossev.xiaomiLib, line 1
    base: "driver", // library marker kkossev.xiaomiLib, line 2
    author: "Krassimir Kossev", // library marker kkossev.xiaomiLib, line 3
    category: "zigbee", // library marker kkossev.xiaomiLib, line 4
    description: "Xiaomi Library", // library marker kkossev.xiaomiLib, line 5
    name: "xiaomiLib", // library marker kkossev.xiaomiLib, line 6
    namespace: "kkossev", // library marker kkossev.xiaomiLib, line 7
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/xiaomiLib.groovy", // library marker kkossev.xiaomiLib, line 8
    version: "1.0.1", // library marker kkossev.xiaomiLib, line 9
    documentationLink: "" // library marker kkossev.xiaomiLib, line 10
) // library marker kkossev.xiaomiLib, line 11
/* // library marker kkossev.xiaomiLib, line 12
 *  Xiaomi Library // library marker kkossev.xiaomiLib, line 13
 * // library marker kkossev.xiaomiLib, line 14
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.xiaomiLib, line 15
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.xiaomiLib, line 16
 * // library marker kkossev.xiaomiLib, line 17
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.xiaomiLib, line 18
 * // library marker kkossev.xiaomiLib, line 19
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.xiaomiLib, line 20
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.xiaomiLib, line 21
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.xiaomiLib, line 22
 * // library marker kkossev.xiaomiLib, line 23
 * ver. 1.0.0  2023-09-09 kkossev  - added xiaomiLib // library marker kkossev.xiaomiLib, line 24
 * ver. 1.0.1  2023-11-07 kkossev  - (dev. branch) // library marker kkossev.xiaomiLib, line 25
 * // library marker kkossev.xiaomiLib, line 26
 *                                   TODO:  // library marker kkossev.xiaomiLib, line 27
*/ // library marker kkossev.xiaomiLib, line 28


def xiaomiLibVersion()   {"1.0.1"} // library marker kkossev.xiaomiLib, line 31
def xiaomiLibStamp() {"2023/11/07 5:23 PM"} // library marker kkossev.xiaomiLib, line 32

// no metadata for this library! // library marker kkossev.xiaomiLib, line 34

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.xiaomiLib, line 36

// Zigbee Attributes // library marker kkossev.xiaomiLib, line 38
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.xiaomiLib, line 39
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.xiaomiLib, line 40
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.xiaomiLib, line 41
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.xiaomiLib, line 42
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.xiaomiLib, line 43
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.xiaomiLib, line 44
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.xiaomiLib, line 45
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.xiaomiLib, line 46
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.xiaomiLib, line 47
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.xiaomiLib, line 48
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.xiaomiLib, line 49
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.xiaomiLib, line 50
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.xiaomiLib, line 51
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.xiaomiLib, line 52
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.xiaomiLib, line 53

// Xiaomi Tags // library marker kkossev.xiaomiLib, line 55
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.xiaomiLib, line 56
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 57
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.xiaomiLib, line 58
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.xiaomiLib, line 59
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 60
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.xiaomiLib, line 61

// called from parseXiaomiCluster() in the main code ... // library marker kkossev.xiaomiLib, line 63
// // library marker kkossev.xiaomiLib, line 64
void parseXiaomiClusterLib(final Map descMap) { // library marker kkossev.xiaomiLib, line 65
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 66
        //log.trace "zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 67
    } // library marker kkossev.xiaomiLib, line 68
    if (DEVICE_TYPE in  ["Thermostat"]) { // library marker kkossev.xiaomiLib, line 69
        parseXiaomiClusterThermostatLib(descMap) // library marker kkossev.xiaomiLib, line 70
        return // library marker kkossev.xiaomiLib, line 71
    } // library marker kkossev.xiaomiLib, line 72
    if (DEVICE_TYPE in  ["Bulb"]) { // library marker kkossev.xiaomiLib, line 73
        parseXiaomiClusterRgbLib(descMap) // library marker kkossev.xiaomiLib, line 74
        return // library marker kkossev.xiaomiLib, line 75
    } // library marker kkossev.xiaomiLib, line 76
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 77
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 78
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 79
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 80
            if (DEVICE_TYPE in  ["AqaraCube"]) { logDebug "AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 81
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 82
            break // library marker kkossev.xiaomiLib, line 83
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 84
            log.info "unknown attribute - resetting?" // library marker kkossev.xiaomiLib, line 85
            break // library marker kkossev.xiaomiLib, line 86
        case PRESENCE_ATTR_ID:            // 0x0142 FP1 // library marker kkossev.xiaomiLib, line 87
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 88
            parseXiaomiClusterPresence(value) // library marker kkossev.xiaomiLib, line 89
            break // library marker kkossev.xiaomiLib, line 90
        case PRESENCE_ACTIONS_ATTR_ID:    // 0x0143 FP1 // library marker kkossev.xiaomiLib, line 91
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 92
            parseXiaomiClusterPresenceAction(value) // library marker kkossev.xiaomiLib, line 93
            break // library marker kkossev.xiaomiLib, line 94
        case REGION_EVENT_ATTR_ID:        // 0x0151 FP1 // library marker kkossev.xiaomiLib, line 95
            // Region events can be sent fast and furious so buffer them // library marker kkossev.xiaomiLib, line 96
            final Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1]) // library marker kkossev.xiaomiLib, line 97
            final Integer value = HexUtils.hexStringToInt(descMap.value[2..3]) // library marker kkossev.xiaomiLib, line 98
            if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 99
                log.debug "xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 100
            } // library marker kkossev.xiaomiLib, line 101
            if (device.currentValue("region${regionId}") != null) { // library marker kkossev.xiaomiLib, line 102
                RegionUpdateBuffer.get(device.id).put(regionId, value) // library marker kkossev.xiaomiLib, line 103
                runInMillis(REGION_UPDATE_DELAY_MS, 'updateRegions') // library marker kkossev.xiaomiLib, line 104
            } // library marker kkossev.xiaomiLib, line 105
            break // library marker kkossev.xiaomiLib, line 106
        case SENSITIVITY_LEVEL_ATTR_ID:   // 0x010C FP1 // library marker kkossev.xiaomiLib, line 107
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 108
            log.info "sensitivity level is '${SensitivityLevelOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 109
            device.updateSetting('sensitivityLevel', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 110
            break // library marker kkossev.xiaomiLib, line 111
        case TRIGGER_DISTANCE_ATTR_ID:    // 0x0146 FP1 // library marker kkossev.xiaomiLib, line 112
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 113
            log.info "approach distance is '${ApproachDistanceOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 114
            device.updateSetting('approachDistance', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 115
            break // library marker kkossev.xiaomiLib, line 116
        case DIRECTION_MODE_ATTR_ID:     // 0x0144 FP1 // library marker kkossev.xiaomiLib, line 117
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 118
            log.info "monitoring direction mode is '${DirectionModeOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 119
            device.updateSetting('directionMode', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 120
            break // library marker kkossev.xiaomiLib, line 121
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode // library marker kkossev.xiaomiLib, line 122
            if (DEVICE_TYPE in  ["AqaraCube"]) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 123
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 124
            break // library marker kkossev.xiaomiLib, line 125
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 126
            if (DEVICE_TYPE in  ["AqaraCube"]) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 127
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 128
            break // library marker kkossev.xiaomiLib, line 129
        case XIAOMI_SPECIAL_REPORT_ID:   // 0x00F7 sent every 55 minutes // library marker kkossev.xiaomiLib, line 130
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value) // library marker kkossev.xiaomiLib, line 131
            parseXiaomiClusterTags(tags) // library marker kkossev.xiaomiLib, line 132
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 133
                sendZigbeeCommands(refreshAqaraCube()) // library marker kkossev.xiaomiLib, line 134
            } // library marker kkossev.xiaomiLib, line 135
            break // library marker kkossev.xiaomiLib, line 136
        case XIAOMI_RAW_ATTR_ID:        // 0xFFF2 FP1  // library marker kkossev.xiaomiLib, line 137
            final byte[] rawData = HexUtils.hexStringToByteArray(descMap.value) // library marker kkossev.xiaomiLib, line 138
            if (rawData.size() == 24 && settings.enableDistanceDirection) { // library marker kkossev.xiaomiLib, line 139
                final int degrees = rawData[19] // library marker kkossev.xiaomiLib, line 140
                final int distanceCm = (rawData[17] << 8) | (rawData[18] & 0x00ff) // library marker kkossev.xiaomiLib, line 141
                if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 142
                    log.debug "location ${degrees}&deg;, ${distanceCm}cm" // library marker kkossev.xiaomiLib, line 143
                } // library marker kkossev.xiaomiLib, line 144
                runIn(1, 'updateLocation', [ data: [ degrees: degrees, distanceCm: distanceCm ] ]) // library marker kkossev.xiaomiLib, line 145
            } // library marker kkossev.xiaomiLib, line 146
            break // library marker kkossev.xiaomiLib, line 147
        default: // library marker kkossev.xiaomiLib, line 148
            log.warn "zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 149
            break // library marker kkossev.xiaomiLib, line 150
    } // library marker kkossev.xiaomiLib, line 151
} // library marker kkossev.xiaomiLib, line 152

void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 154
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 155
        switch (tag) { // library marker kkossev.xiaomiLib, line 156
            case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 157
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value/1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 158
                break // library marker kkossev.xiaomiLib, line 159
            case 0x03: // library marker kkossev.xiaomiLib, line 160
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 161
                break // library marker kkossev.xiaomiLib, line 162
            case 0x05: // library marker kkossev.xiaomiLib, line 163
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 164
                break // library marker kkossev.xiaomiLib, line 165
            case 0x06: // library marker kkossev.xiaomiLib, line 166
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 167
                break // library marker kkossev.xiaomiLib, line 168
            case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 169
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 170
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 171
                device.updateDataValue("aqaraVersion", swBuild) // library marker kkossev.xiaomiLib, line 172
                break // library marker kkossev.xiaomiLib, line 173
            case 0x0a: // library marker kkossev.xiaomiLib, line 174
                String nwk = intToHexStr(value as Integer,2) // library marker kkossev.xiaomiLib, line 175
                if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 176
                String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 177
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 178
                if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 179
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 180
                    state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 181
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 182
                } // library marker kkossev.xiaomiLib, line 183
                break // library marker kkossev.xiaomiLib, line 184
            case 0x0b: // library marker kkossev.xiaomiLib, line 185
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 186
                break // library marker kkossev.xiaomiLib, line 187
            case 0x64: // library marker kkossev.xiaomiLib, line 188
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value/100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 189
                // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 190
                break // library marker kkossev.xiaomiLib, line 191
            case 0x65: // library marker kkossev.xiaomiLib, line 192
                if (isAqaraFP1()) { logDebug "xiaomi decode PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 193
                else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value/100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 194
                break // library marker kkossev.xiaomiLib, line 195
            case 0x66: // library marker kkossev.xiaomiLib, line 196
                if (isAqaraFP1()) { logDebug "xiaomi decode SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 197
                else if (isAqaraTVOC()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 198
                else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" }  // library marker kkossev.xiaomiLib, line 199
                break // library marker kkossev.xiaomiLib, line 200
            case 0x67: // library marker kkossev.xiaomiLib, line 201
                if (isAqaraFP1()) { logDebug "xiaomi decode DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" }     // library marker kkossev.xiaomiLib, line 202
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC:  // library marker kkossev.xiaomiLib, line 203
                // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 204
                break // library marker kkossev.xiaomiLib, line 205
            case 0x69: // library marker kkossev.xiaomiLib, line 206
                if (isAqaraFP1()) { logDebug "xiaomi decode TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 207
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 208
                break // library marker kkossev.xiaomiLib, line 209
            case 0x6a: // library marker kkossev.xiaomiLib, line 210
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 211
                else              { logDebug "xiaomi decode MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 212
                break // library marker kkossev.xiaomiLib, line 213
            case 0x6b: // library marker kkossev.xiaomiLib, line 214
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 215
                else              { logDebug "xiaomi decode MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 216
                break // library marker kkossev.xiaomiLib, line 217
            case 0x95: // library marker kkossev.xiaomiLib, line 218
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 219
                break // library marker kkossev.xiaomiLib, line 220
            case 0x96: // library marker kkossev.xiaomiLib, line 221
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 222
                break // library marker kkossev.xiaomiLib, line 223
            case 0x97: // library marker kkossev.xiaomiLib, line 224
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 225
                break // library marker kkossev.xiaomiLib, line 226
            case 0x98: // library marker kkossev.xiaomiLib, line 227
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 228
                break // library marker kkossev.xiaomiLib, line 229
            case 0x9b: // library marker kkossev.xiaomiLib, line 230
                if (isAqaraCube()) {  // library marker kkossev.xiaomiLib, line 231
                    logDebug "Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})"  // library marker kkossev.xiaomiLib, line 232
                    sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 233
                } // library marker kkossev.xiaomiLib, line 234
                else { logDebug "xiaomi decode CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 235
                break // library marker kkossev.xiaomiLib, line 236
            default: // library marker kkossev.xiaomiLib, line 237
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 238
        } // library marker kkossev.xiaomiLib, line 239
    } // library marker kkossev.xiaomiLib, line 240
} // library marker kkossev.xiaomiLib, line 241


/** // library marker kkossev.xiaomiLib, line 244
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 245
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 246
 */ // library marker kkossev.xiaomiLib, line 247
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 248
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 249
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 250
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 251
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 252
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 253
    } // library marker kkossev.xiaomiLib, line 254
    return bigInt // library marker kkossev.xiaomiLib, line 255
} // library marker kkossev.xiaomiLib, line 256

/** // library marker kkossev.xiaomiLib, line 258
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 259
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 260
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 261
 */ // library marker kkossev.xiaomiLib, line 262
private static Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 263
    final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 264
    final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 265
    new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 266
        while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 267
            int tag = stream.read() // library marker kkossev.xiaomiLib, line 268
            int dataType = stream.read() // library marker kkossev.xiaomiLib, line 269
            Object value // library marker kkossev.xiaomiLib, line 270
            if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 271
                int length = stream.read() // library marker kkossev.xiaomiLib, line 272
                byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 273
                stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 274
                value = new String(byteArr) // library marker kkossev.xiaomiLib, line 275
            } else { // library marker kkossev.xiaomiLib, line 276
                int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 277
                value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 278
            } // library marker kkossev.xiaomiLib, line 279
            results[tag] = value // library marker kkossev.xiaomiLib, line 280
        } // library marker kkossev.xiaomiLib, line 281
    } // library marker kkossev.xiaomiLib, line 282
    return results // library marker kkossev.xiaomiLib, line 283
} // library marker kkossev.xiaomiLib, line 284


def refreshXiaomi() { // library marker kkossev.xiaomiLib, line 287
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 288
    if (cmds == []) { cmds = ["delay 299"] } // library marker kkossev.xiaomiLib, line 289
    return cmds // library marker kkossev.xiaomiLib, line 290
} // library marker kkossev.xiaomiLib, line 291

def configureXiaomi() { // library marker kkossev.xiaomiLib, line 293
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 294
    logDebug "configureThermostat() : ${cmds}" // library marker kkossev.xiaomiLib, line 295
    if (cmds == []) { cmds = ["delay 299"] }    // no ,  // library marker kkossev.xiaomiLib, line 296
    return cmds     // library marker kkossev.xiaomiLib, line 297
} // library marker kkossev.xiaomiLib, line 298

def initializeXiaomi() // library marker kkossev.xiaomiLib, line 300
{ // library marker kkossev.xiaomiLib, line 301
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 302
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 303
    if (cmds == []) { cmds = ["delay 299",] } // library marker kkossev.xiaomiLib, line 304
    return cmds         // library marker kkossev.xiaomiLib, line 305
} // library marker kkossev.xiaomiLib, line 306

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 308
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 309
} // library marker kkossev.xiaomiLib, line 310

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 312
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 313
} // library marker kkossev.xiaomiLib, line 314


// ~~~~~ end include (141) kkossev.xiaomiLib ~~~~~

// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
library ( // library marker kkossev.deviceProfileLib, line 1
    base: "driver", // library marker kkossev.deviceProfileLib, line 2
    author: "Krassimir Kossev", // library marker kkossev.deviceProfileLib, line 3
    category: "zigbee", // library marker kkossev.deviceProfileLib, line 4
    description: "Device Profile Library", // library marker kkossev.deviceProfileLib, line 5
    name: "deviceProfileLib", // library marker kkossev.deviceProfileLib, line 6
    namespace: "kkossev", // library marker kkossev.deviceProfileLib, line 7
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy", // library marker kkossev.deviceProfileLib, line 8
    version: "3.0.0", // library marker kkossev.deviceProfileLib, line 9
    documentationLink: "" // library marker kkossev.deviceProfileLib, line 10
) // library marker kkossev.deviceProfileLib, line 11
/* // library marker kkossev.deviceProfileLib, line 12
 *  Device Profile Library // library marker kkossev.deviceProfileLib, line 13
 * // library marker kkossev.deviceProfileLib, line 14
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.deviceProfileLib, line 15
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.deviceProfileLib, line 16
 * // library marker kkossev.deviceProfileLib, line 17
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.deviceProfileLib, line 18
 * // library marker kkossev.deviceProfileLib, line 19
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.deviceProfileLib, line 20
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.deviceProfileLib, line 21
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.deviceProfileLib, line 22
 * // library marker kkossev.deviceProfileLib, line 23
 * ver. 1.0.0  2023-11-04 kkossev  - added deviceProfileLib (based on Tuya 4 In 1 driver) // library marker kkossev.deviceProfileLib, line 24
 * ver. 3.0.0  2023-11-27 kkossev  - (dev. branch) fixes for use with commonLib; added processClusterAttributeFromDeviceProfile() method; added validateAndFixPreferences() method;  inputIt bug fix; signedInt Preproc method;  // library marker kkossev.deviceProfileLib, line 25
 * ver. 3.0.1  2023-12-02 kkossev  - (dev. branch) release candidate // library marker kkossev.deviceProfileLib, line 26
 * // library marker kkossev.deviceProfileLib, line 27
 *                                   TODO: refactor sendAttribute ! // library marker kkossev.deviceProfileLib, line 28
*/ // library marker kkossev.deviceProfileLib, line 29

def deviceProfileLibVersion()   {"3.0.1"} // library marker kkossev.deviceProfileLib, line 31
def deviceProfileLibtamp() {"2023/12/02 10:48 AM"} // library marker kkossev.deviceProfileLib, line 32

metadata { // library marker kkossev.deviceProfileLib, line 34
    // no capabilities // library marker kkossev.deviceProfileLib, line 35
    // no attributes // library marker kkossev.deviceProfileLib, line 36
    command "sendCommand", [ // library marker kkossev.deviceProfileLib, line 37
        [name:"command", type: "STRING", description: "command name", constraints: ["STRING"]], // library marker kkossev.deviceProfileLib, line 38
        [name:"val",     type: "STRING", description: "command parameter value", constraints: ["STRING"]] // library marker kkossev.deviceProfileLib, line 39
    ] // library marker kkossev.deviceProfileLib, line 40
    command "setPar", [ // library marker kkossev.deviceProfileLib, line 41
            [name:"par", type: "STRING", description: "preference parameter name", constraints: ["STRING"]], // library marker kkossev.deviceProfileLib, line 42
            [name:"val", type: "STRING", description: "preference parameter value", constraints: ["STRING"]] // library marker kkossev.deviceProfileLib, line 43
    ]     // library marker kkossev.deviceProfileLib, line 44
    // // library marker kkossev.deviceProfileLib, line 45
    // itterate over DEVICE.preferences map and inputIt all! // library marker kkossev.deviceProfileLib, line 46
    if (DEVICE != null && DEVICE.preferences != null && DEVICE.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 47
        (DEVICE.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 48
            if (inputIt(key) != null) { // library marker kkossev.deviceProfileLib, line 49
                input inputIt(key) // library marker kkossev.deviceProfileLib, line 50
            } // library marker kkossev.deviceProfileLib, line 51
        }     // library marker kkossev.deviceProfileLib, line 52
    } // library marker kkossev.deviceProfileLib, line 53
    preferences { // library marker kkossev.deviceProfileLib, line 54
        if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 55
            input (name: "forcedProfile", type: "enum", title: "<b>Device Profile</b>", description: "<i>Forcely change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!</i>",  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 56
        } // library marker kkossev.deviceProfileLib, line 57
    } // library marker kkossev.deviceProfileLib, line 58
} // library marker kkossev.deviceProfileLib, line 59

def getDeviceGroup()     { state.deviceProfile ?: "UNKNOWN" } // library marker kkossev.deviceProfileLib, line 61
def getDEVICE()          { deviceProfilesV2[getDeviceGroup()] } // library marker kkossev.deviceProfileLib, line 62
def getDeviceProfiles()      { deviceProfilesV2.keySet() } // library marker kkossev.deviceProfileLib, line 63
def getDeviceProfilesMap()   {deviceProfilesV2.values().description as List<String>} // library marker kkossev.deviceProfileLib, line 64


/** // library marker kkossev.deviceProfileLib, line 67
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 68
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 69
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 70
 */ // library marker kkossev.deviceProfileLib, line 71
def getProfileKey(String valueStr) { // library marker kkossev.deviceProfileLib, line 72
    def key = null // library marker kkossev.deviceProfileLib, line 73
    deviceProfilesV2.each {  profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 74
        if (profileMap.description.equals(valueStr)) { // library marker kkossev.deviceProfileLib, line 75
            key = profileName // library marker kkossev.deviceProfileLib, line 76
        } // library marker kkossev.deviceProfileLib, line 77
    } // library marker kkossev.deviceProfileLib, line 78
    return key // library marker kkossev.deviceProfileLib, line 79
} // library marker kkossev.deviceProfileLib, line 80

/** // library marker kkossev.deviceProfileLib, line 82
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 83
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 84
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 85
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 86
 * @return null if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 87
 */ // library marker kkossev.deviceProfileLib, line 88
def getPreferencesMapByName( String param, boolean debug=false ) { // library marker kkossev.deviceProfileLib, line 89
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 90
    if (!(param in DEVICE.preferences)) { // library marker kkossev.deviceProfileLib, line 91
        if (debug) { logWarn "getPreferencesMapByName: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 92
        return null // library marker kkossev.deviceProfileLib, line 93
    } // library marker kkossev.deviceProfileLib, line 94
    def preference  // library marker kkossev.deviceProfileLib, line 95
    try { // library marker kkossev.deviceProfileLib, line 96
        preference = DEVICE.preferences["$param"] // library marker kkossev.deviceProfileLib, line 97
        if (debug) log.debug "getPreferencesMapByName: param ${param} found. preference is ${preference}" // library marker kkossev.deviceProfileLib, line 98
        if (preference in [true, false]) {      // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 99
            if (debug) { logDebug "getPreferencesMapByName: preference ${param} is boolean" } // library marker kkossev.deviceProfileLib, line 100
            return null     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 101
        } // library marker kkossev.deviceProfileLib, line 102
        if (safeToInt(preference, -1) >0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 103
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 104
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 105
            foundMap = DEVICE.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 106
        } // library marker kkossev.deviceProfileLib, line 107
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 108
            //if (debug) log.trace "getPreferencesMapByName:  ${DEVICE.attributes}" // library marker kkossev.deviceProfileLib, line 109
            def dpMaps   =  DEVICE.tuyaDPs  // library marker kkossev.deviceProfileLib, line 110
            foundMap = DEVICE.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 111
        } // library marker kkossev.deviceProfileLib, line 112
        // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 113
    } catch (Exception e) { // library marker kkossev.deviceProfileLib, line 114
        if (debug) log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" // library marker kkossev.deviceProfileLib, line 115
        return null // library marker kkossev.deviceProfileLib, line 116
    } // library marker kkossev.deviceProfileLib, line 117
    if (debug) { logDebug "getPreferencesMapByName: param=${param} foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 118
    return foundMap      // library marker kkossev.deviceProfileLib, line 119
} // library marker kkossev.deviceProfileLib, line 120

def getAttributesMap( String attribName, boolean debug=false ) { // library marker kkossev.deviceProfileLib, line 122
    Map foundMap = null // library marker kkossev.deviceProfileLib, line 123
    def searchMap // library marker kkossev.deviceProfileLib, line 124
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 125
    if (DEVICE.tuyaDPs != null) { // library marker kkossev.deviceProfileLib, line 126
        searchMap =  DEVICE.tuyaDPs  // library marker kkossev.deviceProfileLib, line 127
        foundMap = searchMap.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 128
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 129
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 130
            return foundMap // library marker kkossev.deviceProfileLib, line 131
        } // library marker kkossev.deviceProfileLib, line 132
    } // library marker kkossev.deviceProfileLib, line 133
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 134
    if (DEVICE.attributes != null) { // library marker kkossev.deviceProfileLib, line 135
        searchMap  =  DEVICE.attributes  // library marker kkossev.deviceProfileLib, line 136
        foundMap = searchMap.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 137
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 138
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 139
            return foundMap // library marker kkossev.deviceProfileLib, line 140
        } // library marker kkossev.deviceProfileLib, line 141
    } // library marker kkossev.deviceProfileLib, line 142
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 143
    return null // library marker kkossev.deviceProfileLib, line 144
} // library marker kkossev.deviceProfileLib, line 145


/** // library marker kkossev.deviceProfileLib, line 148
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 149
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 150
 */ // library marker kkossev.deviceProfileLib, line 151
def resetPreferencesToDefaults(/*boolean*/ debug=false ) { // library marker kkossev.deviceProfileLib, line 152
    logDebug "resetPreferencesToDefaults...(debug=${debug})" // library marker kkossev.deviceProfileLib, line 153
    if (DEVICE == null) { // library marker kkossev.deviceProfileLib, line 154
        if (debug) { logWarn "DEVICE not found!" } // library marker kkossev.deviceProfileLib, line 155
        return // library marker kkossev.deviceProfileLib, line 156
    } // library marker kkossev.deviceProfileLib, line 157
    def preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 158
    logTrace "preferences = ${preferences}"     // library marker kkossev.deviceProfileLib, line 159
    if (preferences == null) { // library marker kkossev.deviceProfileLib, line 160
        if (debug) { logWarn "Preferences not found!" } // library marker kkossev.deviceProfileLib, line 161
        return // library marker kkossev.deviceProfileLib, line 162
    } // library marker kkossev.deviceProfileLib, line 163
    def parMap = [:] // library marker kkossev.deviceProfileLib, line 164
    preferences.each{ parName, mapValue ->  // library marker kkossev.deviceProfileLib, line 165
        if (debug) log.trace "$parName $mapValue" // library marker kkossev.deviceProfileLib, line 166
        // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 167
        if (mapValue in [true, false]) { // library marker kkossev.deviceProfileLib, line 168
            if (debug) { logDebug "Preference ${parName} is predefined -> (${mapValue})" } // library marker kkossev.deviceProfileLib, line 169
            // TODO - set the predefined value // library marker kkossev.deviceProfileLib, line 170
            /* // library marker kkossev.deviceProfileLib, line 171
            if (debug) log.info "par ${parName} defaultValue = ${parMap.defaultValue}" // library marker kkossev.deviceProfileLib, line 172
            device.updateSetting("${parMap.name}",[value:parMap.defaultValue, type:parMap.type])      // library marker kkossev.deviceProfileLib, line 173
            */        // library marker kkossev.deviceProfileLib, line 174
            return // continue // library marker kkossev.deviceProfileLib, line 175
        } // library marker kkossev.deviceProfileLib, line 176
        // find the individual preference map // library marker kkossev.deviceProfileLib, line 177
        parMap = getPreferencesMapByName(parName, false) // library marker kkossev.deviceProfileLib, line 178
        if (parMap == null) { // library marker kkossev.deviceProfileLib, line 179
            if (debug) { logWarn "Preference ${parName} not found in tuyaDPs or attributes map!" } // library marker kkossev.deviceProfileLib, line 180
            return // continue // library marker kkossev.deviceProfileLib, line 181
        }    // library marker kkossev.deviceProfileLib, line 182
        // parMap = [at:0xE002:0xE005, name:staticDetectionSensitivity, type:number, dt:UINT8, rw:rw, min:0, max:5, step:1, scale:1, unit:x, title:Static Detection Sensitivity, description:Static detection sensitivity] // library marker kkossev.deviceProfileLib, line 183
        if (parMap.defaultValue == null) { // library marker kkossev.deviceProfileLib, line 184
            if (debug) { logWarn "no default value for preference ${parName} !" } // library marker kkossev.deviceProfileLib, line 185
            return // continue // library marker kkossev.deviceProfileLib, line 186
        } // library marker kkossev.deviceProfileLib, line 187
        if (debug) log.info "par ${parName} defaultValue = ${parMap.defaultValue}" // library marker kkossev.deviceProfileLib, line 188
        if (debug) log.trace "parMap.name ${parMap.name} parMap.defaultValue = ${parMap.defaultValue} type=${parMap.type}" // library marker kkossev.deviceProfileLib, line 189
        device.updateSetting("${parMap.name}",[value:parMap.defaultValue, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 190
    } // library marker kkossev.deviceProfileLib, line 191
    logInfo "Preferences reset to default values" // library marker kkossev.deviceProfileLib, line 192
} // library marker kkossev.deviceProfileLib, line 193




/** // library marker kkossev.deviceProfileLib, line 198
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 199
 * // library marker kkossev.deviceProfileLib, line 200
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 201
 */ // library marker kkossev.deviceProfileLib, line 202
def getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 203
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 204
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 205
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 206
        validPars = DEVICE.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 207
    } // library marker kkossev.deviceProfileLib, line 208
    return validPars // library marker kkossev.deviceProfileLib, line 209
} // library marker kkossev.deviceProfileLib, line 210


/** // library marker kkossev.deviceProfileLib, line 213
 * Returns the scaled value of a preference based on its type and scale. // library marker kkossev.deviceProfileLib, line 214
 * @param preference The name of the preference to retrieve. // library marker kkossev.deviceProfileLib, line 215
 * @param dpMap A map containing the type and scale of the preference. // library marker kkossev.deviceProfileLib, line 216
 * @return The scaled value of the preference, or null if the preference is not found or has an unsupported type. // library marker kkossev.deviceProfileLib, line 217
 */ // library marker kkossev.deviceProfileLib, line 218
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 219
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 220
    def scaledValue // library marker kkossev.deviceProfileLib, line 221
    if (value == null) { // library marker kkossev.deviceProfileLib, line 222
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 223
        return null // library marker kkossev.deviceProfileLib, line 224
    } // library marker kkossev.deviceProfileLib, line 225
    switch(dpMap.type) { // library marker kkossev.deviceProfileLib, line 226
        case "number" : // library marker kkossev.deviceProfileLib, line 227
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 228
            break // library marker kkossev.deviceProfileLib, line 229
        case "decimal" : // library marker kkossev.deviceProfileLib, line 230
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 231
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 232
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 233
            } // library marker kkossev.deviceProfileLib, line 234
            break // library marker kkossev.deviceProfileLib, line 235
        case "bool" : // library marker kkossev.deviceProfileLib, line 236
            scaledValue = value == "true" ? 1 : 0 // library marker kkossev.deviceProfileLib, line 237
            break // library marker kkossev.deviceProfileLib, line 238
        case "enum" : // library marker kkossev.deviceProfileLib, line 239
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 240
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 241
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 242
                return null // library marker kkossev.deviceProfileLib, line 243
            } // library marker kkossev.deviceProfileLib, line 244
            scaledValue = value  // library marker kkossev.deviceProfileLib, line 245
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 246
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 247
            }             // library marker kkossev.deviceProfileLib, line 248
            break // library marker kkossev.deviceProfileLib, line 249
        default : // library marker kkossev.deviceProfileLib, line 250
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 251
            return null // library marker kkossev.deviceProfileLib, line 252
    } // library marker kkossev.deviceProfileLib, line 253
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})"  // library marker kkossev.deviceProfileLib, line 254
    return scaledValue // library marker kkossev.deviceProfileLib, line 255
} // library marker kkossev.deviceProfileLib, line 256

// called from updated() method // library marker kkossev.deviceProfileLib, line 258
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 259
void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 260
    logDebug "updateAllPreferences: preferences=${DEVICE.preferences}" // library marker kkossev.deviceProfileLib, line 261
    if (DEVICE.preferences == null || DEVICE.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 262
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceGroup()}" // library marker kkossev.deviceProfileLib, line 263
        return // library marker kkossev.deviceProfileLib, line 264
    } // library marker kkossev.deviceProfileLib, line 265
    Integer dpInt = 0 // library marker kkossev.deviceProfileLib, line 266
    def scaledValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 267
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 268
    (DEVICE.preferences).each { name, dp ->  // library marker kkossev.deviceProfileLib, line 269
        /* // library marker kkossev.deviceProfileLib, line 270
        dpInt = safeToInt(dp, -1) // library marker kkossev.deviceProfileLib, line 271
        if (dpInt <= 0) { // library marker kkossev.deviceProfileLib, line 272
            // this is the IAS and other non-Tuya DPs preferences ....  // library marker kkossev.deviceProfileLib, line 273
            logDebug "updateAllPreferences: preference ${name} has invalid Tuya dp value ${dp}" // library marker kkossev.deviceProfileLib, line 274
            return  // library marker kkossev.deviceProfileLib, line 275
        } // library marker kkossev.deviceProfileLib, line 276
        def dpMaps   =  DEVICE.tuyaDPs  // library marker kkossev.deviceProfileLib, line 277
        */ // library marker kkossev.deviceProfileLib, line 278
        Map foundMap // library marker kkossev.deviceProfileLib, line 279
        foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 280
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 281

        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 283
            // scaledValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 284
            scaledValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 285
            logTrace"scaledValue = ${scaledValue}"                                          // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!  // library marker kkossev.deviceProfileLib, line 286
            if (scaledValue != null) { // library marker kkossev.deviceProfileLib, line 287
                setPar(name, scaledValue) // library marker kkossev.deviceProfileLib, line 288
            } // library marker kkossev.deviceProfileLib, line 289
            else { // library marker kkossev.deviceProfileLib, line 290
                logDebug "updateAllPreferences: preference ${name} is not set (scaledValue was null)" // library marker kkossev.deviceProfileLib, line 291
                return  // library marker kkossev.deviceProfileLib, line 292
            } // library marker kkossev.deviceProfileLib, line 293
        } // library marker kkossev.deviceProfileLib, line 294
        else { // library marker kkossev.deviceProfileLib, line 295
            logDebug "warning: couldn't find map for preference ${name}" // library marker kkossev.deviceProfileLib, line 296
            return  // library marker kkossev.deviceProfileLib, line 297
        } // library marker kkossev.deviceProfileLib, line 298
    }     // library marker kkossev.deviceProfileLib, line 299
    return // library marker kkossev.deviceProfileLib, line 300
} // library marker kkossev.deviceProfileLib, line 301

def divideBy100( val ) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 303
def multiplyBy100( val ) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 304
def divideBy10( val ) {  // library marker kkossev.deviceProfileLib, line 305
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 306
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 307
} // library marker kkossev.deviceProfileLib, line 308
def multiplyBy10( val ) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 309
def divideBy1( val ) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 310
def signedInt( val ) { // library marker kkossev.deviceProfileLib, line 311
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 312
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 313

} // library marker kkossev.deviceProfileLib, line 315

/** // library marker kkossev.deviceProfileLib, line 317
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 318
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 319
 *  // library marker kkossev.deviceProfileLib, line 320
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 321
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 322
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 323
 */ // library marker kkossev.deviceProfileLib, line 324
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 325
    def value = null    // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 326
    def scaledValue = null // library marker kkossev.deviceProfileLib, line 327
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 328
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 329
        case "number" : // library marker kkossev.deviceProfileLib, line 330
            value = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 331
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 332
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 333
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 334
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 335
            } // library marker kkossev.deviceProfileLib, line 336
            else { // library marker kkossev.deviceProfileLib, line 337
                scaledValue = value // library marker kkossev.deviceProfileLib, line 338
            } // library marker kkossev.deviceProfileLib, line 339
            break // library marker kkossev.deviceProfileLib, line 340

        case "decimal" : // library marker kkossev.deviceProfileLib, line 342
            value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 343
            // scale the value // library marker kkossev.deviceProfileLib, line 344
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 345
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 346
            } // library marker kkossev.deviceProfileLib, line 347
            else { // library marker kkossev.deviceProfileLib, line 348
                scaledValue = value // library marker kkossev.deviceProfileLib, line 349
            } // library marker kkossev.deviceProfileLib, line 350
            break // library marker kkossev.deviceProfileLib, line 351

        case "bool" : // library marker kkossev.deviceProfileLib, line 353
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 354
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 355
            else { // library marker kkossev.deviceProfileLib, line 356
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 357
                return null // library marker kkossev.deviceProfileLib, line 358
            } // library marker kkossev.deviceProfileLib, line 359
            break // library marker kkossev.deviceProfileLib, line 360
        case "enum" : // library marker kkossev.deviceProfileLib, line 361
            // val could be both integer or float value ... check if the scaling is different than 1 in dpMap  // library marker kkossev.deviceProfileLib, line 362
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 363
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) {   // TODO - check this !!! // library marker kkossev.deviceProfileLib, line 364
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 365
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 366
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 367
            } // library marker kkossev.deviceProfileLib, line 368
            else { // library marker kkossev.deviceProfileLib, line 369
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 370
            } // library marker kkossev.deviceProfileLib, line 371
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 372
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 373
                List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 374
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 375
                // find the key for the value // library marker kkossev.deviceProfileLib, line 376
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 377
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 378
                if (key == null) { // library marker kkossev.deviceProfileLib, line 379
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 380
                    return null // library marker kkossev.deviceProfileLib, line 381
                } // library marker kkossev.deviceProfileLib, line 382
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 383
                //return null // library marker kkossev.deviceProfileLib, line 384
            } // library marker kkossev.deviceProfileLib, line 385
            break // library marker kkossev.deviceProfileLib, line 386
        default : // library marker kkossev.deviceProfileLib, line 387
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 388
            return null // library marker kkossev.deviceProfileLib, line 389
    } // library marker kkossev.deviceProfileLib, line 390
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 391
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 392
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 393
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 394
        return null // library marker kkossev.deviceProfileLib, line 395
    } // library marker kkossev.deviceProfileLib, line 396
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 397
    return scaledValue // library marker kkossev.deviceProfileLib, line 398
} // library marker kkossev.deviceProfileLib, line 399

/** // library marker kkossev.deviceProfileLib, line 401
 * Sets the parameter value for the device. // library marker kkossev.deviceProfileLib, line 402
 * @param par The name of the parameter to set. // library marker kkossev.deviceProfileLib, line 403
 * @param val The value to set the parameter to. // library marker kkossev.deviceProfileLib, line 404
 * @return Nothing. // library marker kkossev.deviceProfileLib, line 405
 * // library marker kkossev.deviceProfileLib, line 406
 * TODO: refactor it !!! // library marker kkossev.deviceProfileLib, line 407
 */ // library marker kkossev.deviceProfileLib, line 408
def setPar( par=null, val=null ) // library marker kkossev.deviceProfileLib, line 409
{ // library marker kkossev.deviceProfileLib, line 410
    ArrayList<String> cmds = [] // library marker kkossev.deviceProfileLib, line 411
    Boolean validated = false // library marker kkossev.deviceProfileLib, line 412
    logDebug "setPar(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 413
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return } // library marker kkossev.deviceProfileLib, line 414
    if (par == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return }         // library marker kkossev.deviceProfileLib, line 415
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 416
    if ( dpMap == null ) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return } // library marker kkossev.deviceProfileLib, line 417
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return } // library marker kkossev.deviceProfileLib, line 418
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 419
    if (scaledValue == null) { logInfo "setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return } // library marker kkossev.deviceProfileLib, line 420
    /* // library marker kkossev.deviceProfileLib, line 421
    // update the device setting // TODO: decide whether the setting must be updated here, or after it is echeod back from the device // library marker kkossev.deviceProfileLib, line 422
    try { // library marker kkossev.deviceProfileLib, line 423
        device.updateSetting("$par", [value:val, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 424
    } // library marker kkossev.deviceProfileLib, line 425
    catch (e) { // library marker kkossev.deviceProfileLib, line 426
        logWarn "setPar: Exception '${e}'caught while updateSetting <b>$par</b>(<b>$val</b>) type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 427
        return // library marker kkossev.deviceProfileLib, line 428
    } // library marker kkossev.deviceProfileLib, line 429
    */ // library marker kkossev.deviceProfileLib, line 430
    //logDebug "setPar: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 431
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 432
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 433
    String setFunction = "set${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 434
    if (this.respondsTo(setFunction)) { // library marker kkossev.deviceProfileLib, line 435
        logDebug "setPar: found setFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 436
        // execute the setFunction // library marker kkossev.deviceProfileLib, line 437
        try { // library marker kkossev.deviceProfileLib, line 438
            cmds = "$setFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 439
        } // library marker kkossev.deviceProfileLib, line 440
        catch (e) { // library marker kkossev.deviceProfileLib, line 441
            logWarn "setPar: Exception '${e}'caught while processing <b>$setFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 442
            return // library marker kkossev.deviceProfileLib, line 443
        } // library marker kkossev.deviceProfileLib, line 444
        logDebug "setFunction result is ${cmds}"        // library marker kkossev.deviceProfileLib, line 445
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 446
            logInfo "setPar: (1) successfluly executed setPar <b>$setFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 447
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 448
            return // library marker kkossev.deviceProfileLib, line 449
        }             // library marker kkossev.deviceProfileLib, line 450
        else { // library marker kkossev.deviceProfileLib, line 451
            logWarn "setPar: setFunction <b>$setFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 452
            // continue with the default processing // library marker kkossev.deviceProfileLib, line 453
        } // library marker kkossev.deviceProfileLib, line 454
    } // library marker kkossev.deviceProfileLib, line 455
    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 456
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 457

    try { // library marker kkossev.deviceProfileLib, line 459
        // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 460
        isTuyaDP = dpMap.dp instanceof Number // library marker kkossev.deviceProfileLib, line 461
    } // library marker kkossev.deviceProfileLib, line 462
    catch (e) { // library marker kkossev.deviceProfileLib, line 463
        logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" // library marker kkossev.deviceProfileLib, line 464
        isTuyaDP = false // library marker kkossev.deviceProfileLib, line 465
        //return null // library marker kkossev.deviceProfileLib, line 466
    }      // library marker kkossev.deviceProfileLib, line 467
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 468
        // Tuya DP // library marker kkossev.deviceProfileLib, line 469
        cmds = sendTuyaParameter(dpMap,  par, scaledValue)  // library marker kkossev.deviceProfileLib, line 470
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 471
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 472
            return // library marker kkossev.deviceProfileLib, line 473
        } // library marker kkossev.deviceProfileLib, line 474
        else { // library marker kkossev.deviceProfileLib, line 475
            logInfo "setPar: (2) successfluly executed setPar <b>$setFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 476
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 477
            return // library marker kkossev.deviceProfileLib, line 478
        } // library marker kkossev.deviceProfileLib, line 479
    } // library marker kkossev.deviceProfileLib, line 480
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 481
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 482
        int cluster // library marker kkossev.deviceProfileLib, line 483
        int attribute // library marker kkossev.deviceProfileLib, line 484
        int dt // library marker kkossev.deviceProfileLib, line 485
        def mfgCode // library marker kkossev.deviceProfileLib, line 486
        try { // library marker kkossev.deviceProfileLib, line 487
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(":")[0]) // library marker kkossev.deviceProfileLib, line 488
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 489
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(":")[1]) // library marker kkossev.deviceProfileLib, line 490
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 491
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 492
            //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 493
            mfgCode = dpMap.mfgCode // library marker kkossev.deviceProfileLib, line 494
            //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 495
        } // library marker kkossev.deviceProfileLib, line 496
        catch (e) { // library marker kkossev.deviceProfileLib, line 497
            logWarn "setPar: Exception '${e}' caught while splitting cluser and attribute <b>$setFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 498
            return // library marker kkossev.deviceProfileLib, line 499
        } // library marker kkossev.deviceProfileLib, line 500
        Map mapMfCode = ["mfgCode":mfgCode] // library marker kkossev.deviceProfileLib, line 501
        logDebug "setPar: found cluster=${cluster} attribute=${attribute} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 502
        if (mfgCode != null) { // library marker kkossev.deviceProfileLib, line 503
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay=200) // library marker kkossev.deviceProfileLib, line 504
        } // library marker kkossev.deviceProfileLib, line 505
        else { // library marker kkossev.deviceProfileLib, line 506
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay=200) // library marker kkossev.deviceProfileLib, line 507
        } // library marker kkossev.deviceProfileLib, line 508
    } // library marker kkossev.deviceProfileLib, line 509
    else { // library marker kkossev.deviceProfileLib, line 510
        logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 511
        return // library marker kkossev.deviceProfileLib, line 512
    } // library marker kkossev.deviceProfileLib, line 513
    logInfo "setPar: (3) successfluly executed setPar <b>$setFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 514
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 515
    return // library marker kkossev.deviceProfileLib, line 516
} // library marker kkossev.deviceProfileLib, line 517

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 519
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 520
def sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 521
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 522
    ArrayList<String> cmds = [] // library marker kkossev.deviceProfileLib, line 523
    if (dpMap == null) { // library marker kkossev.deviceProfileLib, line 524
        logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 525
        return null // library marker kkossev.deviceProfileLib, line 526
    } // library marker kkossev.deviceProfileLib, line 527
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 528
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 529
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 530
        return null  // library marker kkossev.deviceProfileLib, line 531
    } // library marker kkossev.deviceProfileLib, line 532
    String dpType // library marker kkossev.deviceProfileLib, line 533
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 534
        dpType = dpMap.type == "bool" ? DP_TYPE_BOOL : dpMap.type == "enum" ? DP_TYPE_ENUM : (dpMap.type in ["value", "number", "decimal"]) ? DP_TYPE_VALUE: null // library marker kkossev.deviceProfileLib, line 535
    } // library marker kkossev.deviceProfileLib, line 536
    else { // library marker kkossev.deviceProfileLib, line 537
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 538
    } // library marker kkossev.deviceProfileLib, line 539
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 540
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 541
        return null  // library marker kkossev.deviceProfileLib, line 542
    } // library marker kkossev.deviceProfileLib, line 543
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 544
    def dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2)  // library marker kkossev.deviceProfileLib, line 545
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 546
    cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 547
    return cmds // library marker kkossev.deviceProfileLib, line 548
} // library marker kkossev.deviceProfileLib, line 549

def sendAttribute( par=null, val=null ) // library marker kkossev.deviceProfileLib, line 551
{ // library marker kkossev.deviceProfileLib, line 552
    ArrayList<String> cmds = [] // library marker kkossev.deviceProfileLib, line 553
    Boolean validated = false // library marker kkossev.deviceProfileLib, line 554
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 555
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false} // library marker kkossev.deviceProfileLib, line 556

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 558
    if ( dpMap == null ) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 559
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 560
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 561
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 562
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 563
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 564
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 565
    String setFunction = "set${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 566
    if (this.respondsTo(setFunction) && !(setFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])) { // library marker kkossev.deviceProfileLib, line 567
        logDebug "sendAttribute: found setFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 568
        // execute the setFunction // library marker kkossev.deviceProfileLib, line 569
        try { // library marker kkossev.deviceProfileLib, line 570
            cmds = "$setFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 571
        } // library marker kkossev.deviceProfileLib, line 572
        catch (e) { // library marker kkossev.deviceProfileLib, line 573
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$setFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 574
            return false // library marker kkossev.deviceProfileLib, line 575
        } // library marker kkossev.deviceProfileLib, line 576
        logDebug "setFunction result is ${cmds}"        // library marker kkossev.deviceProfileLib, line 577
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 578
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$setFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 579
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 580
            return true // library marker kkossev.deviceProfileLib, line 581
        }             // library marker kkossev.deviceProfileLib, line 582
        else { // library marker kkossev.deviceProfileLib, line 583
            logWarn "sendAttribute: setFunction <b>$setFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 584
            // continue with the default processing // library marker kkossev.deviceProfileLib, line 585
        } // library marker kkossev.deviceProfileLib, line 586
    } // library marker kkossev.deviceProfileLib, line 587
    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 588
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 589
    def preference = dpMap.dp // library marker kkossev.deviceProfileLib, line 590
    try { // library marker kkossev.deviceProfileLib, line 591
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 592
    } // library marker kkossev.deviceProfileLib, line 593
    catch (e) { // library marker kkossev.deviceProfileLib, line 594
        if (debug) log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" // library marker kkossev.deviceProfileLib, line 595
        return false // library marker kkossev.deviceProfileLib, line 596
    }      // library marker kkossev.deviceProfileLib, line 597
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 598
        // Tuya DP // library marker kkossev.deviceProfileLib, line 599
        cmds = sendTuyaParameter(dpMap,  par, scaledValue)  // library marker kkossev.deviceProfileLib, line 600
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 601
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 602
            return false // library marker kkossev.deviceProfileLib, line 603
        } // library marker kkossev.deviceProfileLib, line 604
        else { // library marker kkossev.deviceProfileLib, line 605
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$setFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 606
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 607
            return true // library marker kkossev.deviceProfileLib, line 608
        } // library marker kkossev.deviceProfileLib, line 609
    } // library marker kkossev.deviceProfileLib, line 610
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 611
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 612
        int cluster // library marker kkossev.deviceProfileLib, line 613
        int attribute // library marker kkossev.deviceProfileLib, line 614
        int dt // library marker kkossev.deviceProfileLib, line 615
       // int mfgCode // library marker kkossev.deviceProfileLib, line 616
   //     try { // library marker kkossev.deviceProfileLib, line 617
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(":")[0]) // library marker kkossev.deviceProfileLib, line 618
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 619
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(":")[1]) // library marker kkossev.deviceProfileLib, line 620
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 621
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 622
            //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 623
            //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 624
          //  mfgCode = dpMap.mfgCode != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.mfgCode) : null // library marker kkossev.deviceProfileLib, line 625
          //  log.trace "mfgCode = ${mfgCode}" // library marker kkossev.deviceProfileLib, line 626
  //      } // library marker kkossev.deviceProfileLib, line 627
  /*      catch (e) { // library marker kkossev.deviceProfileLib, line 628
            logWarn "sendAttribute: Exception '${e}'caught while splitting cluster and attribute <b>$setFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 629
            return false // library marker kkossev.deviceProfileLib, line 630
        }*/ // library marker kkossev.deviceProfileLib, line 631

        logDebug "sendAttribute: found cluster=${cluster} attribute=${attribute} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 633
        if (dpMap.mfgCode != null) { // library marker kkossev.deviceProfileLib, line 634
            Map mapMfCode = ["mfgCode":dpMap.mfgCode] // library marker kkossev.deviceProfileLib, line 635
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay=200) // library marker kkossev.deviceProfileLib, line 636
        } // library marker kkossev.deviceProfileLib, line 637
        else { // library marker kkossev.deviceProfileLib, line 638
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay=200) // library marker kkossev.deviceProfileLib, line 639
        } // library marker kkossev.deviceProfileLib, line 640
    } // library marker kkossev.deviceProfileLib, line 641
    else { // library marker kkossev.deviceProfileLib, line 642
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 643
        return false // library marker kkossev.deviceProfileLib, line 644
    } // library marker kkossev.deviceProfileLib, line 645
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$setFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 646
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 647
    return true // library marker kkossev.deviceProfileLib, line 648
} // library marker kkossev.deviceProfileLib, line 649


/** // library marker kkossev.deviceProfileLib, line 652
 * Sends a command to the device. // library marker kkossev.deviceProfileLib, line 653
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 654
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 655
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 656
 */ // library marker kkossev.deviceProfileLib, line 657
def sendCommand( command=null, val=null) // library marker kkossev.deviceProfileLib, line 658
{ // library marker kkossev.deviceProfileLib, line 659
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 660
    ArrayList<String> cmds = [] // library marker kkossev.deviceProfileLib, line 661
    def supportedCommandsMap = DEVICE.commands  // library marker kkossev.deviceProfileLib, line 662
    if (supportedCommandsMap == null || supportedCommandsMap == []) { // library marker kkossev.deviceProfileLib, line 663
        logInfo "sendCommand: no commands defined for device profile ${getDeviceGroup()} !" // library marker kkossev.deviceProfileLib, line 664
        return false // library marker kkossev.deviceProfileLib, line 665
    } // library marker kkossev.deviceProfileLib, line 666
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 667
    def supportedCommandsList =  DEVICE.commands.keySet() as List  // library marker kkossev.deviceProfileLib, line 668
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 669
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 670
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 671
        return false // library marker kkossev.deviceProfileLib, line 672
    } // library marker kkossev.deviceProfileLib, line 673
    def func // library marker kkossev.deviceProfileLib, line 674
    try { // library marker kkossev.deviceProfileLib, line 675
        func = DEVICE.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 676
        if (val != null) { // library marker kkossev.deviceProfileLib, line 677
            cmds = "${func}"(val) // library marker kkossev.deviceProfileLib, line 678
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 679
        } // library marker kkossev.deviceProfileLib, line 680
        else { // library marker kkossev.deviceProfileLib, line 681
            cmds = "${func}"() // library marker kkossev.deviceProfileLib, line 682
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 683
        } // library marker kkossev.deviceProfileLib, line 684
    } // library marker kkossev.deviceProfileLib, line 685
    catch (e) { // library marker kkossev.deviceProfileLib, line 686
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 687
        return false // library marker kkossev.deviceProfileLib, line 688
    } // library marker kkossev.deviceProfileLib, line 689
    if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 690
        sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 691
    } // library marker kkossev.deviceProfileLib, line 692
    return true // library marker kkossev.deviceProfileLib, line 693
} // library marker kkossev.deviceProfileLib, line 694

/** // library marker kkossev.deviceProfileLib, line 696
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 697
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 698
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 699
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 700
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 701
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 702
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 703
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 704
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 705
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 706
 */ // library marker kkossev.deviceProfileLib, line 707
def inputIt( String param, boolean debug=false ) { // library marker kkossev.deviceProfileLib, line 708
    Map input = [:] // library marker kkossev.deviceProfileLib, line 709
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 710
    if (!(param in DEVICE.preferences)) { // library marker kkossev.deviceProfileLib, line 711
        if (debug) logWarn "inputIt: preference ${param} not defined for this device!" // library marker kkossev.deviceProfileLib, line 712
        return null // library marker kkossev.deviceProfileLib, line 713
    } // library marker kkossev.deviceProfileLib, line 714
    def preference // library marker kkossev.deviceProfileLib, line 715
    boolean isTuyaDP  // library marker kkossev.deviceProfileLib, line 716
    try { // library marker kkossev.deviceProfileLib, line 717
        preference = DEVICE.preferences["$param"] // library marker kkossev.deviceProfileLib, line 718
    } // library marker kkossev.deviceProfileLib, line 719
    catch (e) { // library marker kkossev.deviceProfileLib, line 720
        if (debug) logWarn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" // library marker kkossev.deviceProfileLib, line 721
        return null // library marker kkossev.deviceProfileLib, line 722
    }    // library marker kkossev.deviceProfileLib, line 723
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 724
    try { // library marker kkossev.deviceProfileLib, line 725
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 726
            if (debug) logWarn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" // library marker kkossev.deviceProfileLib, line 727
            return null // library marker kkossev.deviceProfileLib, line 728
        } // library marker kkossev.deviceProfileLib, line 729
    } // library marker kkossev.deviceProfileLib, line 730
    catch (e) { // library marker kkossev.deviceProfileLib, line 731
        if (debug) logWarn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" // library marker kkossev.deviceProfileLib, line 732
        return null // library marker kkossev.deviceProfileLib, line 733
    }  // library marker kkossev.deviceProfileLib, line 734

    try { // library marker kkossev.deviceProfileLib, line 736
        isTuyaDP = preference instanceof Number // library marker kkossev.deviceProfileLib, line 737
    } // library marker kkossev.deviceProfileLib, line 738
    catch (e) { // library marker kkossev.deviceProfileLib, line 739
        if (debug) logWarn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" // library marker kkossev.deviceProfileLib, line 740
        return null // library marker kkossev.deviceProfileLib, line 741
    }  // library marker kkossev.deviceProfileLib, line 742

    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 744
    if (foundMap == null) { // library marker kkossev.deviceProfileLib, line 745
        if (debug) logWarn "inputIt: map not found for param '${param}'!" // library marker kkossev.deviceProfileLib, line 746
        return null // library marker kkossev.deviceProfileLib, line 747
    } // library marker kkossev.deviceProfileLib, line 748
    if (foundMap.rw != "rw") { // library marker kkossev.deviceProfileLib, line 749
        if (debug) logWarn "inputIt: param '${param}' is read only!" // library marker kkossev.deviceProfileLib, line 750
        return null // library marker kkossev.deviceProfileLib, line 751
    }         // library marker kkossev.deviceProfileLib, line 752
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 753
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 754
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 755
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 756
    if (input.type in ["number", "decimal"]) { // library marker kkossev.deviceProfileLib, line 757
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 758
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 759
        } // library marker kkossev.deviceProfileLib, line 760
        if (input.range != null && input.description !=null) { // library marker kkossev.deviceProfileLib, line 761
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 762
            if (foundMap.unit != null && foundMap.unit != "") { // library marker kkossev.deviceProfileLib, line 763
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 764
            } // library marker kkossev.deviceProfileLib, line 765
        } // library marker kkossev.deviceProfileLib, line 766
    } // library marker kkossev.deviceProfileLib, line 767
    else if (input.type == "enum") { // library marker kkossev.deviceProfileLib, line 768
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 769
    }/* // library marker kkossev.deviceProfileLib, line 770
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 771
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 772
    }*/ // library marker kkossev.deviceProfileLib, line 773
    else { // library marker kkossev.deviceProfileLib, line 774
        if (debug) logWarn "inputIt: unsupported type ${input.type} for param '${param}'!" // library marker kkossev.deviceProfileLib, line 775
        return null // library marker kkossev.deviceProfileLib, line 776
    }    // library marker kkossev.deviceProfileLib, line 777
    if (input.defaultValue != null) { // library marker kkossev.deviceProfileLib, line 778
        input.defaultValue = foundMap.defaultValue // library marker kkossev.deviceProfileLib, line 779
    } // library marker kkossev.deviceProfileLib, line 780
    return input // library marker kkossev.deviceProfileLib, line 781
} // library marker kkossev.deviceProfileLib, line 782


/** // library marker kkossev.deviceProfileLib, line 785
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 786
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 787
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 788
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 789
 */ // library marker kkossev.deviceProfileLib, line 790
def getDeviceNameAndProfile( model=null, manufacturer=null) { // library marker kkossev.deviceProfileLib, line 791
    def deviceName         = UNKNOWN // library marker kkossev.deviceProfileLib, line 792
    def deviceProfile      = UNKNOWN // library marker kkossev.deviceProfileLib, line 793
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 794
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 795
    deviceProfilesV2.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 796
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 797
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 798
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 799
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV2[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 800
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 801
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 802
            } // library marker kkossev.deviceProfileLib, line 803
        } // library marker kkossev.deviceProfileLib, line 804
    } // library marker kkossev.deviceProfileLib, line 805
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 806
        logWarn "<b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 807
    } // library marker kkossev.deviceProfileLib, line 808
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 809
} // library marker kkossev.deviceProfileLib, line 810

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 812
def setDeviceNameAndProfile( model=null, manufacturer=null) { // library marker kkossev.deviceProfileLib, line 813
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 814
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 815
        logWarn "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 816
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 817
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 818
    } // library marker kkossev.deviceProfileLib, line 819
    def dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 820
    def dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 821
    if (deviceName != NULL && deviceName != UNKNOWN  ) { // library marker kkossev.deviceProfileLib, line 822
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 823
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 824
        device.updateSetting("forcedProfile", [value:deviceProfilesV2[deviceProfile].description, type:"enum"]) // library marker kkossev.deviceProfileLib, line 825
        //logDebug "after : forcedProfile = ${settings.forcedProfile}" // library marker kkossev.deviceProfileLib, line 826
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 827
    } else { // library marker kkossev.deviceProfileLib, line 828
        logWarn "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 829
    }     // library marker kkossev.deviceProfileLib, line 830
} // library marker kkossev.deviceProfileLib, line 831

def refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 833
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 834
    if (cmds == []) { cmds = ["delay 299"] } // library marker kkossev.deviceProfileLib, line 835
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 836
    return cmds // library marker kkossev.deviceProfileLib, line 837
} // library marker kkossev.deviceProfileLib, line 838

def configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 840
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 841
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 842
    if (cmds == []) { cmds = ["delay 299"] }    // no ,  // library marker kkossev.deviceProfileLib, line 843
    return cmds     // library marker kkossev.deviceProfileLib, line 844
} // library marker kkossev.deviceProfileLib, line 845

def initializeDeviceProfile() // library marker kkossev.deviceProfileLib, line 847
{ // library marker kkossev.deviceProfileLib, line 848
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 849
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 850
    if (cmds == []) { cmds = ["delay 299",] } // library marker kkossev.deviceProfileLib, line 851
    return cmds         // library marker kkossev.deviceProfileLib, line 852
} // library marker kkossev.deviceProfileLib, line 853

void initVarsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 855
    logDebug "initVarsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 856
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 857
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 858
    }     // library marker kkossev.deviceProfileLib, line 859
} // library marker kkossev.deviceProfileLib, line 860

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 862
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 863
} // library marker kkossev.deviceProfileLib, line 864

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 866


// // library marker kkossev.deviceProfileLib, line 869
// called from parse() // library marker kkossev.deviceProfileLib, line 870
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 871
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 872
// // library marker kkossev.deviceProfileLib, line 873
boolean isSpammyDPsToIgnore(descMap) { // library marker kkossev.deviceProfileLib, line 874
    if (!(descMap?.clusterId == "EF00" && (descMap?.command in ["01", "02"]))) { return false } // library marker kkossev.deviceProfileLib, line 875
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 876
    Integer dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 877
    def spammyList = deviceProfilesV2[getDeviceGroup()].spammyDPsToIgnore // library marker kkossev.deviceProfileLib, line 878
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 879
} // library marker kkossev.deviceProfileLib, line 880

// // library marker kkossev.deviceProfileLib, line 882
// called from processTuyaDP(), processTuyaDPfromDeviceProfile() // library marker kkossev.deviceProfileLib, line 883
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 884
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 885
// // library marker kkossev.deviceProfileLib, line 886
boolean isSpammyDPsToNotTrace(descMap) { // library marker kkossev.deviceProfileLib, line 887
    if (!(descMap?.clusterId == "EF00" && (descMap?.command in ["01", "02"]))) { return false } // library marker kkossev.deviceProfileLib, line 888
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 889
    Integer dp = zigbee.convertHexToInt(descMap.data[2])  // library marker kkossev.deviceProfileLib, line 890
    def spammyList = deviceProfilesV2[getDeviceGroup()].spammyDPsToNotTrace // library marker kkossev.deviceProfileLib, line 891
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 892
} // library marker kkossev.deviceProfileLib, line 893

def compareAndConvertStrings(foundItem, tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 895
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 896
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 897
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 898
} // library marker kkossev.deviceProfileLib, line 899

def compareAndConvertNumbers(foundItem, tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 901
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 902
    if (foundItem.scale == null || foundItem.scale == 0 || foundItem.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 903
        convertedValue = tuyaValue as int                 // library marker kkossev.deviceProfileLib, line 904
    } // library marker kkossev.deviceProfileLib, line 905
    else { // library marker kkossev.deviceProfileLib, line 906
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 907
    } // library marker kkossev.deviceProfileLib, line 908
    boolean isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 909
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 910
} // library marker kkossev.deviceProfileLib, line 911

def compareAndConvertDecimals(foundItem, tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 913
    Double convertedValue // library marker kkossev.deviceProfileLib, line 914
    if (foundItem.scale == null || foundItem.scale == 0 || foundItem.scale == 1) { // library marker kkossev.deviceProfileLib, line 915
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 916
    } // library marker kkossev.deviceProfileLib, line 917
    else { // library marker kkossev.deviceProfileLib, line 918
        convertedValue = (tuyaValue as double) / (foundItem.scale as double)  // library marker kkossev.deviceProfileLib, line 919
    } // library marker kkossev.deviceProfileLib, line 920
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001  // library marker kkossev.deviceProfileLib, line 921
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 922
} // library marker kkossev.deviceProfileLib, line 923


def compareAndConvertTuyaToHubitatPreferenceValue(foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 926
    if (foundItem == null || fncmd == null || preference == null) { return [true, "none"] } // library marker kkossev.deviceProfileLib, line 927
    if (foundItem.type == null) { return [true, "none"] } // library marker kkossev.deviceProfileLib, line 928
    boolean isEqual // library marker kkossev.deviceProfileLib, line 929
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 930
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 931
        case "bool" :       // [0:"OFF", 1:"ON"]  // library marker kkossev.deviceProfileLib, line 932
        case "enum" :       // [0:"inactive", 1:"active"] // library marker kkossev.deviceProfileLib, line 933
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 934
            //logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 935
            break // library marker kkossev.deviceProfileLib, line 936
        case "value" :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 937
        case "number" : // library marker kkossev.deviceProfileLib, line 938
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 939
            //logWarn "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 940
            break  // library marker kkossev.deviceProfileLib, line 941
       case "decimal" : // library marker kkossev.deviceProfileLib, line 942
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference))  // library marker kkossev.deviceProfileLib, line 943
            //logDebug "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 944
            break // library marker kkossev.deviceProfileLib, line 945
        default : // library marker kkossev.deviceProfileLib, line 946
            logDebug "compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}" // library marker kkossev.deviceProfileLib, line 947
            return [true, "none"]   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 948
    } // library marker kkossev.deviceProfileLib, line 949
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 950
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 951
    } // library marker kkossev.deviceProfileLib, line 952
    // // library marker kkossev.deviceProfileLib, line 953
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 954
} // library marker kkossev.deviceProfileLib, line 955

// // library marker kkossev.deviceProfileLib, line 957
// called from processTuyaDPfromDeviceProfile() // library marker kkossev.deviceProfileLib, line 958
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 959
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 960
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 961
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 962
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 963
//  // library marker kkossev.deviceProfileLib, line 964
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 965
// // library marker kkossev.deviceProfileLib, line 966
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 967
// // library marker kkossev.deviceProfileLib, line 968
def compareAndConvertTuyaToHubitatEventValue(foundItem, fncmd, doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 969
    if (foundItem == null) { return [true, "none"] } // library marker kkossev.deviceProfileLib, line 970
    if (foundItem.type == null) { return [true, "none"] } // library marker kkossev.deviceProfileLib, line 971
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 972
    boolean isEqual // library marker kkossev.deviceProfileLib, line 973
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 974
        case "bool" :       // [0:"OFF", 1:"ON"]  // library marker kkossev.deviceProfileLib, line 975
        case "enum" :       // [0:"inactive", 1:"active"] // library marker kkossev.deviceProfileLib, line 976
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: "unknown", device.currentValue(foundItem.name) ?: "unknown") // library marker kkossev.deviceProfileLib, line 977
            break // library marker kkossev.deviceProfileLib, line 978
        case "value" :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 979
        case "number" : // library marker kkossev.deviceProfileLib, line 980
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 981
            break         // library marker kkossev.deviceProfileLib, line 982
        case "decimal" : // library marker kkossev.deviceProfileLib, line 983
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name)))             // library marker kkossev.deviceProfileLib, line 984
            break // library marker kkossev.deviceProfileLib, line 985
        default : // library marker kkossev.deviceProfileLib, line 986
            logDebug "compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}" // library marker kkossev.deviceProfileLib, line 987
            return [true, "none"]   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 988
    } // library marker kkossev.deviceProfileLib, line 989
    //if (!doNotTrace)  logTrace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 990
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 991
} // library marker kkossev.deviceProfileLib, line 992


def preProc(foundItem, fncmd_orig) { // library marker kkossev.deviceProfileLib, line 995
    def fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 996
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 997
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 998
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 999
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1000
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1001
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1002
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1003
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1004
    } // library marker kkossev.deviceProfileLib, line 1005
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1006
    try { // library marker kkossev.deviceProfileLib, line 1007
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1008
    } // library marker kkossev.deviceProfileLib, line 1009
    catch (e) { // library marker kkossev.deviceProfileLib, line 1010
        logWarn "preProc: Exception '${e}'caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1011
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1012
    } // library marker kkossev.deviceProfileLib, line 1013
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1014
    return fncmd // library marker kkossev.deviceProfileLib, line 1015
} // library marker kkossev.deviceProfileLib, line 1016


/** // library marker kkossev.deviceProfileLib, line 1019
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1020
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1021
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1022
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1023
 *  // library marker kkossev.deviceProfileLib, line 1024
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1025
 */ // library marker kkossev.deviceProfileLib, line 1026
boolean processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd_orig, dp_len=0) { // library marker kkossev.deviceProfileLib, line 1027
    def fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1028
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1029
    //if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status)  // library marker kkossev.deviceProfileLib, line 1030

    def tuyaDPsMap = deviceProfilesV2[state.deviceProfile].tuyaDPs // library marker kkossev.deviceProfileLib, line 1032
    if (tuyaDPsMap == null || tuyaDPsMap == []) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1033

    def foundItem = null // library marker kkossev.deviceProfileLib, line 1035
    tuyaDPsMap.each { item -> // library marker kkossev.deviceProfileLib, line 1036
         if (item['dp'] == (dp as int)) { // library marker kkossev.deviceProfileLib, line 1037
            foundItem = item // library marker kkossev.deviceProfileLib, line 1038
            return // library marker kkossev.deviceProfileLib, line 1039
        } // library marker kkossev.deviceProfileLib, line 1040
    } // library marker kkossev.deviceProfileLib, line 1041
    if (foundItem == null) {  // library marker kkossev.deviceProfileLib, line 1042
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1043
        //updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len) // library marker kkossev.deviceProfileLib, line 1044
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1045
        return false  // library marker kkossev.deviceProfileLib, line 1046
    } // library marker kkossev.deviceProfileLib, line 1047

    return processFoundItem(foundItem, fncmd_orig)      // library marker kkossev.deviceProfileLib, line 1049
} // library marker kkossev.deviceProfileLib, line 1050


boolean processClusterAttributeFromDeviceProfile(descMap) { // library marker kkossev.deviceProfileLib, line 1053
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1054
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1055

    def attribMap = deviceProfilesV2[state.deviceProfile].attributes // library marker kkossev.deviceProfileLib, line 1057
    if (attribMap == null || attribMap == []) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1058

    def foundItem = null // library marker kkossev.deviceProfileLib, line 1060
    def clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1061
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1062
    //logTrace "clusterAttribute = ${clusterAttribute}" // library marker kkossev.deviceProfileLib, line 1063
    attribMap.each { item -> // library marker kkossev.deviceProfileLib, line 1064
         if (item['at'] == clusterAttribute) { // library marker kkossev.deviceProfileLib, line 1065
            foundItem = item // library marker kkossev.deviceProfileLib, line 1066
            return // library marker kkossev.deviceProfileLib, line 1067
        } // library marker kkossev.deviceProfileLib, line 1068
    } // library marker kkossev.deviceProfileLib, line 1069
    if (foundItem == null) {  // library marker kkossev.deviceProfileLib, line 1070
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1071
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1072
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1073
        return false  // library marker kkossev.deviceProfileLib, line 1074
    } // library marker kkossev.deviceProfileLib, line 1075

    return processFoundItem(foundItem, value)  // library marker kkossev.deviceProfileLib, line 1077
} // library marker kkossev.deviceProfileLib, line 1078



def processFoundItem (foundItem, value) { // library marker kkossev.deviceProfileLib, line 1082
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1083
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1084
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1085
        value = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1086
        logDebug "<b>preProc</b> changed ${foundItem.name} value to ${value}" // library marker kkossev.deviceProfileLib, line 1087
    } // library marker kkossev.deviceProfileLib, line 1088
    else { // library marker kkossev.deviceProfileLib, line 1089
        logTrace "no preProc for ${foundItem.name} : ${foundItem}" // library marker kkossev.deviceProfileLib, line 1090
    } // library marker kkossev.deviceProfileLib, line 1091

    def name = foundItem.name                                    // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1093
    def existingPrefValue = settings[name]                        // preference name as in Hubitat settings (preferences), if already created. // library marker kkossev.deviceProfileLib, line 1094
    def perfValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1095
    boolean preferenceExists = existingPrefValue != null          // check if there is an existing preference for this clusterAttribute   // library marker kkossev.deviceProfileLib, line 1096
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1097
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1098
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1099
    boolean doNotTrace = false  // isSpammyDPsToNotTrace(descMap)          // do not log/trace the spammy clusterAttribute's TODO! // library marker kkossev.deviceProfileLib, line 1100
    if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1101
        logTrace "processClusterAttributeFromDeviceProfile clusterAttribute=${clusterAttribute} ${foundItem.name} (type ${foundItem.type}, rw=${foundItem.rw} isAttribute=${isAttribute}, preferenceExists=${preferenceExists}) value is ${value} - ${foundItem.description}" // library marker kkossev.deviceProfileLib, line 1102
    } // library marker kkossev.deviceProfileLib, line 1103
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1104
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1105
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : "" // library marker kkossev.deviceProfileLib, line 1106
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1107
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1108

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1110
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1111
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1112
            (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1113
            descText  = "${name} is ${valueScaled} ${unitText}"         // library marker kkossev.deviceProfileLib, line 1114
            if (settings.logEnable) { logInfo "${descText}"} // library marker kkossev.deviceProfileLib, line 1115
        } // library marker kkossev.deviceProfileLib, line 1116
        // no more processing is needed, as this clusterAttribute is not a preference and not an attribute // library marker kkossev.deviceProfileLib, line 1117
        return true // library marker kkossev.deviceProfileLib, line 1118
    } // library marker kkossev.deviceProfileLib, line 1119

    // first, check if there is a preference defined to be updated // library marker kkossev.deviceProfileLib, line 1121
    if (preferenceExists) { // library marker kkossev.deviceProfileLib, line 1122
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1123
        def oldPerfValue = device.getSetting(name) // library marker kkossev.deviceProfileLib, line 1124
        (isEqual, perfValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue)     // library marker kkossev.deviceProfileLib, line 1125
        if (isEqual == true) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1126
            logDebug "no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${perfValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1127
        } // library marker kkossev.deviceProfileLib, line 1128
        else { // library marker kkossev.deviceProfileLib, line 1129
            logDebug "preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${perfValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1130
            if (debug) logInfo "updating par ${name} from ${existingPrefValue} to ${perfValue} type ${foundItem.type}"  // library marker kkossev.deviceProfileLib, line 1131
            try { // library marker kkossev.deviceProfileLib, line 1132
                device.updateSetting("${name}",[value:perfValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1133
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1134
            } // library marker kkossev.deviceProfileLib, line 1135
            catch (e) { // library marker kkossev.deviceProfileLib, line 1136
                logWarn "exception ${e} caught while updating preference ${name} to ${value}, type ${foundItem.type}"  // library marker kkossev.deviceProfileLib, line 1137
            } // library marker kkossev.deviceProfileLib, line 1138
        } // library marker kkossev.deviceProfileLib, line 1139
    } // library marker kkossev.deviceProfileLib, line 1140
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1141
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1142
        unitText = foundItem.unit != null ? "$foundItem.unit" : "" // library marker kkossev.deviceProfileLib, line 1143
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1144
    }     // library marker kkossev.deviceProfileLib, line 1145

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1147
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1148
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1149
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1150
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1151

        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1153
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1154
                if (settings.logEnable) { logInfo "${descText} (no change)"} // library marker kkossev.deviceProfileLib, line 1155
            } // library marker kkossev.deviceProfileLib, line 1156
            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1157
            if (name == "motion" && is2in1()) { // library marker kkossev.deviceProfileLib, line 1158
                logDebug "patch for inverted motion sensor 2-in-1" // library marker kkossev.deviceProfileLib, line 1159
                // continue ...  // library marker kkossev.deviceProfileLib, line 1160
            } // library marker kkossev.deviceProfileLib, line 1161
            else { // library marker kkossev.deviceProfileLib, line 1162
                if (state.states != null && state.states["isRefresh"] == true) { // library marker kkossev.deviceProfileLib, line 1163
                    logTrace "isRefresh = true - continue and send an event, although there was no change..." // library marker kkossev.deviceProfileLib, line 1164
                } // library marker kkossev.deviceProfileLib, line 1165
                else { // library marker kkossev.deviceProfileLib, line 1166
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1167
                } // library marker kkossev.deviceProfileLib, line 1168
            } // library marker kkossev.deviceProfileLib, line 1169
        } // library marker kkossev.deviceProfileLib, line 1170

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an event! // library marker kkossev.deviceProfileLib, line 1172

        def divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1174
        def valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1175
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1176
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1177
        if (DEVICE_TYPE in ["Thermostat"])  { processDeviceEventThermostat(name, valueScaled, unitText, descText) } // library marker kkossev.deviceProfileLib, line 1178
        else { // library marker kkossev.deviceProfileLib, line 1179
            switch (name) { // library marker kkossev.deviceProfileLib, line 1180
                case "motion" : // library marker kkossev.deviceProfileLib, line 1181
                    handleMotion(motionActive = value)  // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1182
                    break // library marker kkossev.deviceProfileLib, line 1183
                case "temperature" : // library marker kkossev.deviceProfileLib, line 1184
                    //temperatureEvent(value / getTemperatureDiv()) // library marker kkossev.deviceProfileLib, line 1185
                    handleTemperatureEvent(valueScaled as Float) // library marker kkossev.deviceProfileLib, line 1186
                    break // library marker kkossev.deviceProfileLib, line 1187
                case "humidity" : // library marker kkossev.deviceProfileLib, line 1188
                    handleHumidityEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1189
                    break // library marker kkossev.deviceProfileLib, line 1190
                case "illuminance" : // library marker kkossev.deviceProfileLib, line 1191
                case "illuminance_lux" : // library marker kkossev.deviceProfileLib, line 1192
                    handleIlluminanceEvent(valueCorrected)        // library marker kkossev.deviceProfileLib, line 1193
                    break // library marker kkossev.deviceProfileLib, line 1194
                case "pushed" : // library marker kkossev.deviceProfileLib, line 1195
                    logDebug "button event received value=${value} valueScaled=${valueScaled} valueCorrected=${valueCorrected}" // library marker kkossev.deviceProfileLib, line 1196
                    buttonEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1197
                    break // library marker kkossev.deviceProfileLib, line 1198
                default : // library marker kkossev.deviceProfileLib, line 1199
                    sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: "physical", isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1200
                    if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1201
                        logDebug "event ${name} sent w/ value ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1202
                        logInfo "${descText}"                                 // send an Info log also (because value changed )  // TODO - check whether Info log will be sent also for spammy clusterAttribute ?                                // library marker kkossev.deviceProfileLib, line 1203
                    } // library marker kkossev.deviceProfileLib, line 1204
                    break // library marker kkossev.deviceProfileLib, line 1205
            } // library marker kkossev.deviceProfileLib, line 1206
            //logTrace "attrValue=${attrValue} valueScaled=${valueScaled} equal=${isEqual}" // library marker kkossev.deviceProfileLib, line 1207
        } // library marker kkossev.deviceProfileLib, line 1208
    } // library marker kkossev.deviceProfileLib, line 1209
    // all processing was done here! // library marker kkossev.deviceProfileLib, line 1210
    return true     // library marker kkossev.deviceProfileLib, line 1211
} // library marker kkossev.deviceProfileLib, line 1212




def validateAndFixPreferences(debug=false) { // library marker kkossev.deviceProfileLib, line 1217
    if (debug) logTrace "validateAndFixPreferences: preferences=${DEVICE.preferences}" // library marker kkossev.deviceProfileLib, line 1218
    if (DEVICE.preferences == null || DEVICE.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 1219
        logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceGroup()}" // library marker kkossev.deviceProfileLib, line 1220
        return null // library marker kkossev.deviceProfileLib, line 1221
    } // library marker kkossev.deviceProfileLib, line 1222
    def validationFailures = 0 // library marker kkossev.deviceProfileLib, line 1223
    def validationFixes = 0 // library marker kkossev.deviceProfileLib, line 1224
    def total = 0 // library marker kkossev.deviceProfileLib, line 1225
    def oldSettingValue  // library marker kkossev.deviceProfileLib, line 1226
    def newValue // library marker kkossev.deviceProfileLib, line 1227
    String settingType  // library marker kkossev.deviceProfileLib, line 1228
    DEVICE.preferences.each { // library marker kkossev.deviceProfileLib, line 1229
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1230
        if (foundMap == null) { // library marker kkossev.deviceProfileLib, line 1231
            logDebug "validateAndFixPreferences: map not found for preference ${it.key}"    // 10/21/2023 - sevirity lowered to debug // library marker kkossev.deviceProfileLib, line 1232
            return null // library marker kkossev.deviceProfileLib, line 1233
        } // library marker kkossev.deviceProfileLib, line 1234
        settingType = device.getSettingType(it.key) // library marker kkossev.deviceProfileLib, line 1235
        oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1236
        if (settingType == null) { // library marker kkossev.deviceProfileLib, line 1237
            logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" // library marker kkossev.deviceProfileLib, line 1238
            return null // library marker kkossev.deviceProfileLib, line 1239
        } // library marker kkossev.deviceProfileLib, line 1240
        if (debug) logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" // library marker kkossev.deviceProfileLib, line 1241
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1242
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1243
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1244
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1245
            try { // library marker kkossev.deviceProfileLib, line 1246
                device.removeSetting(it.key) // library marker kkossev.deviceProfileLib, line 1247
                logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1248
            } // library marker kkossev.deviceProfileLib, line 1249
            catch (e) { // library marker kkossev.deviceProfileLib, line 1250
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1251
                return null // library marker kkossev.deviceProfileLib, line 1252
            } // library marker kkossev.deviceProfileLib, line 1253
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1254
            try { // library marker kkossev.deviceProfileLib, line 1255
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1256
                if (foundMap.type == "decimal")     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1257
                else if (foundMap.type == "number") { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1258
                else if (foundMap.type == "bool")   { newValue = oldSettingValue == "true" ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1259
                else if (foundMap.type == "enum") { // library marker kkossev.deviceProfileLib, line 1260
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1261
                    if (oldSettingValue == "true" || oldSettingValue == "false" || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1262
                        newValue = (oldSettingValue == "true" || oldSettingValue == true) ? "1" : "0" // library marker kkossev.deviceProfileLib, line 1263
                    } // library marker kkossev.deviceProfileLib, line 1264
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1265
                    else if (foundMap.map.keySet().toString().any { it.contains(".") }) { // library marker kkossev.deviceProfileLib, line 1266
                        newValue = String.format("%.2f", oldSettingValue) // library marker kkossev.deviceProfileLib, line 1267
                    } // library marker kkossev.deviceProfileLib, line 1268
                    else { // library marker kkossev.deviceProfileLib, line 1269
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1270
                        newValue = String.format("%d", oldSettingValue) // library marker kkossev.deviceProfileLib, line 1271
                    } // library marker kkossev.deviceProfileLib, line 1272
                } // library marker kkossev.deviceProfileLib, line 1273
                // // library marker kkossev.deviceProfileLib, line 1274
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1275
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1276
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1277
            } // library marker kkossev.deviceProfileLib, line 1278
            catch (e) { // library marker kkossev.deviceProfileLib, line 1279
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1280
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1281
                try { // library marker kkossev.deviceProfileLib, line 1282
                    settingValue = foundMap.defaultValue // library marker kkossev.deviceProfileLib, line 1283
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1284
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1285
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1286
                } // library marker kkossev.deviceProfileLib, line 1287
                catch (e2) { // library marker kkossev.deviceProfileLib, line 1288
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" // library marker kkossev.deviceProfileLib, line 1289
                    return null // library marker kkossev.deviceProfileLib, line 1290
                }             // library marker kkossev.deviceProfileLib, line 1291
            } // library marker kkossev.deviceProfileLib, line 1292
        } // library marker kkossev.deviceProfileLib, line 1293
        total ++ // library marker kkossev.deviceProfileLib, line 1294
    } // library marker kkossev.deviceProfileLib, line 1295
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1296
} // library marker kkossev.deviceProfileLib, line 1297



// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~
