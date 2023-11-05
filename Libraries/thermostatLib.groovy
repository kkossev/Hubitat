library (
    base: "driver",
    author: "Krassimir Kossev",
    category: "zigbee",
    description: "Thermostat Library",
    name: "thermostatLib",
    namespace: "kkossev",
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/thermostatLib.groovy",
    version: "1.0.2",
    documentationLink: ""
)
/*
 *  Zigbee Button Dimmer -Library
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ver. 1.0.0  2023-09-07 kkossev  - added thermostatLib
 * ver. 1.0.1  2023-09-09 kkossev  - added temperaturePollingInterval
 * ver. 1.0.2  2023-11-03 kkossev  - (dev. branch) - system_mode off/heat; 
 *
 *                                   TODO: temperature event for 20 degrees bug?
 *                                   TODO: debugLogss off not scheduled bug?
 *                                   TODO: thermostat polling scheduled bug?
*/

def thermostatLibVersion()   {"1.0.2"}
def thermostatLibStamp() {"2023/11/04 10:17 PM"}

//import groovy.transform.Field

metadata {
    capability "ThermostatHeatingSetpoint"
    //capability "ThermostatCoolingSetpoint"
    capability "ThermostatOperatingState"
    capability "ThermostatSetpoint"
    capability "ThermostatMode"
    //capability "Thermostat"
    
    /*
        capability "Actuator"
        capability "Refresh"
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Thermostat"
        capability "ThermostatHeatingSetpoint"
        capability "ThermostatCoolingSetpoint"
        capability "ThermostatOperatingState"
        capability "ThermostatSetpoint"
        capability "ThermostatMode"    
    */

    // Aqara E1 thermostat attributes
    attribute "system_mode", 'enum', SystemModeOpts.options.values() as List<String>
    attribute "preset", 'enum', PresetOpts.options.values() as List<String>
    attribute "window_detection", 'enum', WindowDetectionOpts.options.values() as List<String>
    attribute "valve_detection", 'enum', ValveDetectionOpts.options.values() as List<String>
    attribute "valve_alarm", 'enum', ValveAlarmOpts.options.values() as List<String>
    attribute "child_lock", 'enum', ChildLockOpts.options.values() as List<String>
    attribute "away_preset_temperature", 'number'
    attribute "window_open", 'enum', WindowOpenOpts.options.values() as List<String>
    attribute "calibrated", 'enum', CalibratedOpts.options.values() as List<String>
    attribute "sensor", 'enum', SensorOpts.options.values() as List<String>
    attribute "battery", 'number'

    command "preset", [[name:"select preset option", type: "ENUM",   constraints: ["--- select ---"]+PresetOpts.options.values() as List<String>]]

    if (_DEBUG) { command "testT", [[name: "testT", type: "STRING", description: "testT", defaultValue : ""]]  }

    // TODO - add Sonoff TRVZB fingerprint

    //fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,FCC0,000A,0201", outClusters:"0003,FCC0,0201", model:"lumi.airrtc.agl001", manufacturer:"LUMI", deviceJoinName: "Aqara E1 Thermostat"     // model: 'SRTS-A01'
    // fingerprints are inputed from the deviceProfile maps

    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/6339b6034de34f8a633e4f753dc6e506ac9b001c/src/devices/xiaomi.ts#L3197
    // https://github.com/Smanar/deconz-rest-plugin/blob/6efd103c1a43eb300a19bf3bf3745742239e9fee/devices/xiaomi/xiaomi_lumi.airrtc.agl001.json 
    // https://github.com/dresden-elektronik/deconz-rest-plugin/issues/6351
    preferences {
        input name: 'temperaturePollingInterval', type: 'enum', title: '<b>Temperature polling interval</b>', options: TemperaturePollingIntervalOpts.options, defaultValue: TemperaturePollingIntervalOpts.defaultValue, required: true, description: '<i>Changes how often the hub will poll the TRV for faster temperature reading updates.</i>'
    }
}

@Field static final Map TemperaturePollingIntervalOpts = [
    defaultValue: 600,
    options     : [0: 'Disabled', 60: 'Every minute (not recommended)', 120: 'Every 2 minutes', 300: 'Every 5 minutes', 600: 'Every 10 minutes', 900: 'Every 15 minutes', 1800: 'Every 30 minutes', 3600: 'Every 1 hour']
]

@Field static final Map SystemModeOpts = [        //system_mode
    defaultValue: 1,
    options     : [0: 'off', 1: 'heat']
]
@Field static final Map PresetOpts = [            // preset
    defaultValue: 1,
    options     : [0: 'manual', 1: 'auto', 2: 'away']
]
@Field static final Map WindowDetectionOpts = [   // window_detection
    defaultValue: 1,
    options     : [0: 'off', 1: 'on']
]
@Field static final Map ValveDetectionOpts = [    // valve_detection
    defaultValue: 1,
    options     : [0: 'off', 1: 'on']
]
@Field static final Map ValveAlarmOpts = [    // valve_alarm
    defaultValue: 1,
    options     : [0: false, 1: true]
]
@Field static final Map ChildLockOpts = [    // child_lock
    defaultValue: 1,
    options     : [0: 'unlock', 1: 'lock']
]
@Field static final Map WindowOpenOpts = [    // window_open
    defaultValue: 1,
    options     : [0: false, 1: true]
]
@Field static final Map CalibratedOpts = [    // calibrated
    defaultValue: 1,
    options     : [0: false, 1: true]
]
@Field static final Map SensorOpts = [    // child_lock
    defaultValue: 1,
    options     : [0: 'internal', 1: 'external']
]

@Field static final Map deviceProfilesV2 = [
    // isAqaraTRV()
    "AQARA_E1_TRV"   : [
            description   : "Aqara E1`Thermostat model SRTS-A01",
            models        : ["LUMI"],
            device        : [type: "TRV", powerSource: "battery", isSleepy:false],
            capabilities  : ["ThermostatHeatingSetpoint": true, "ThermostatOperatingState": true, "ThermostatSetpoint":true, "ThermostatMode":true],

            preferences   : ["window_detection":"0xFCC0:0x0273", "valve_detection":"0xFCC0:0x0274",, "child_lock":"0xFCC0:0x0277", "away_preset_temperature":"0xFCC0:0x0279", "window_open":"0xFCC0:0x027A", "calibrated":"0xFCC0:0x027B", "sensor":"0xFCC0:0x027E"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,FCC0,000A,0201", outClusters:"0003,FCC0,0201", model:"lumi.airrtc.agl001", manufacturer:"LUMI", deviceJoinName: "Aqara E1 Thermostat"] 
            ],
            commands      : ["resetStats":"resetStats", 'refresh':'refresh', "initialize":"initialize", "updateAllPreferences": "updateAllPreferences", "resetPreferencesToDefaults":"resetPreferencesToDefaults", "validateAndFixPreferences":"validateAndFixPreferences"],
            tuyaDPs       : [:],
            attributes    : [
                [at:"0xFCC0:0x040A",  name:'battery',                       type:"number",  dt: "0x21",   rw: "ro", min:0,    max:100,  step:1,  scale:1,    unit:"%",  description:'<i>Battery percentage remaining</i>'],
                [at:"0xFCC0:0x0271",  name:'system_mode',                   type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>System Mode</b>",                   description:'<i>System Mode</i>'],
                [at:"0xFCC0:0x0272",  name:'preset',                        type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "rw", min:0,    max:2,    step:1,  scale:1,    map:[0: "manual", 1: "auto", 2: "away"], unit:"",         title: "<b>Preset</b>",                        description:'<i>Preset</i>'],
                [at:"0xFCC0:0x0273",  name:'window_detection',              type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "rw", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "off", 1: "on"], unit:"",         title: "<b>Window Detection</b>",              description:'<i>Window detection</i>'],
                [at:"0xFCC0:0x0274",  name:'valve_detection',               type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "rw", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "off", 1: "on"], unit:"",         title: "<b>Valve Detection</b>",               description:'<i>Valve detection</i>'],
                [at:"0xFCC0:0x0275",  name:'valve_alarm',                   type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "false", 1: "true"], unit:"",         title: "<b>Valve Alarm</b>",                   description:'<i>Valve alarm</i>'],
                [at:"0xFCC0:0x0277",  name:'child_lock',                    type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "rw", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "unlock", 1: "lock"], unit:"",         title: "<b>Child Lock</b>",                    description:'<i>Child lock</i>'],
                [at:"0xFCC0:0x0279",  name:'away_preset_temperature',       type:"decimal", dt: "0x23",   mfgCode:"0x115f",  rw: "rw", min:5.0,  max:35.0, defaultValue:5.0,    step:0.5, scale:100,  unit:"°C", title: "<b>Away Preset Temperature</b>",       description:'<i>Away preset temperature</i>'],
                [at:"0xFCC0:0x027A",  name:'window_open',                   type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "false", 1: "true"], unit:"",         title: "<b>Window Open</b>",                   description:'<i>Window open</i>'],
                [at:"0xFCC0:0x027B",  name:'calibrated',                    type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "false", 1: "true"], unit:"",         title: "<b>Calibrated</b>",                    description:'<i>Calibrated</i>'],
                [at:"0xFCC0:0x027E",  name:'sensor',                        type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "internal", 1: "external"], unit:"",         title: "<b>Sensor</b>",                        description:'<i>Sensor</i>'],
                //
                [at:"0x0201:0x0000",  name:'temperature',                   type:"decimal", dt: "0x21",   rw: "ro", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Temperature</b>",                   description:'<i>Measured temperature</i>'],
                [at:"0x0201:0x0011",  name:'coolingSetpoint',               type:"decimal", dt: "0x21",   rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Cooling Setpoint</b>",              description:'<i>cooling setpoint</i>'],
                [at:"0x0201:0x0012",  name:'heatingSetpoint',               type:"decimal", dt: "0x21",   rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Current Heating Setpoint</b>",      description:'<i>Current heating setpoint</i>'],
                [at:"0x0201:0x001C",  name:'mode',                          type:"enum",    dt: "0x20",   rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b> Mode</b>",                   description:'<i>System Mode ?</i>'],
                //                      ^^^^ TODO - check if this is the same as system_mode    
                [at:"0x0201:0x001E",  name:'thermostatRunMode',             type:"enum",    dt: "0x20",   rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatRunMode</b>",                   description:'<i>thermostatRunMode</i>'],
                //                          ^^ TODO  
                [at:"0x0201:0x0020",  name:'battery2',                      type:"number",  dt: "0x20",   rw: "ro", min:0,    max:100,  step:1,  scale:1,    unit:"%",  description:'<i>Battery percentage remaining</i>'],
                //                          ^^ TODO  
                [at:"0x0201:0x0023",  name:'thermostatHoldMode',            type:"enum",    dt: "0x20",   rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatHoldMode</b>",                   description:'<i>thermostatHoldMode</i>'],
                //                          ^^ TODO  
                [at:"0x0201:0x0029",  name:'thermostatOperatingState',      type:"enum",    dt: "0x20",   rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatOperatingState</b>",                   description:'<i>thermostatOperatingState</i>'],
                //                          ^^ TODO  
                [at:"0x0201:0xFFF2",  name:'unknown',                       type:"number",  dt: "0x21",   rw: "ro", min:0,    max:100,  step:1,  scale:1,    unit:"%",  description:'<i>Battery percentage remaining</i>'],
            ],
            deviceJoinName: "Aqara E1 Thermostat",
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
                [at:"0x0201:0x0000",  name:'temperature',                   type:"decimal", dt: "UINT16", rw: "ro", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Temperature</b>",                   description:'<i>Measured temperature</i>'],
                [at:"0x0201:0x0011",  name:'coolingSetpoint',               type:"decimal", dt: "UINT16", rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Cooling Setpoint</b>",              description:'<i>cooling setpoint</i>'],
                [at:"0x0201:0x0012",  name:'heatingSetpoint',               type:"decimal", dt: "UINT16", rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Current Heating Setpoint</b>",      description:'<i>Current heating setpoint</i>'],
                [at:"0x0201:0x001C",  name:'mode',                          type:"enum",    dt: "UINT8",  rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b> Mode</b>",                   description:'<i>System Mode ?</i>'],
                [at:"0x0201:0x001E",  name:'thermostatRunMode',             type:"enum",    dt: "UINT8",  rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatRunMode</b>",                   description:'<i>thermostatRunMode</i>'],
                [at:"0x0201:0x0020",  name:'battery2',                     type:"number",  dt: "UINT16", rw: "ro", min:0,    max:100,  step:1,  scale:1,    unit:"%",  description:'<i>Battery percentage remaining</i>'],
                [at:"0x0201:0x0023",  name:'thermostatHoldMode',           type:"enum",    dt: "UINT8",  rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatHoldMode</b>",                   description:'<i>thermostatHoldMode</i>'],
                [at:"0x0201:0x0029",  name:'thermostatOperatingState',      type:"enum",    dt: "UINT8",  rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatOperatingState</b>",                   description:'<i>thermostatOperatingState</i>'],
            ],
            deviceJoinName: "UNKWNOWN TRV",
            configuration : [:]
    ]

]



void thermostatEvent(eventName, value, raw) {
    sendEvent(name: eventName, value: value, type: "physical")
    logInfo "${eventName} is ${value} (raw ${raw})"
}

// called from parseXiaomiClusterLib in xiaomiLib.groovy (xiaomi cluster 0xFCC0 )
//
void parseXiaomiClusterThermostatLib(final Map descMap) {
    //logWarn "parseXiaomiClusterThermostatLib: received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
    final Integer raw
    final String  value
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
            thermostatEvent("system_mode", value, raw)
            break;
        case 0x0272:    // result['preset'] = {2: 'away', 1: 'auto', 0: 'manual'}[value]; - rw  ['manual', 'auto', 'holiday']
            raw = hexStrToUnsignedInt(descMap.value)
            value = PresetOpts.options[raw as int]
            thermostatEvent("preset", value, raw)
            break;
        case 0x0273:    // result['window_detection'] = {1: 'ON', 0: 'OFF'}[value]; - rw
            raw = hexStrToUnsignedInt(descMap.value)
            value = WindowDetectionOpts.options[raw as int]
            thermostatEvent("window_detection", value, raw)
            break;
        case 0x0274:    // result['valve_detection'] = {1: 'ON', 0: 'OFF'}[value]; -rw 
            raw = hexStrToUnsignedInt(descMap.value)
            value = ValveDetectionOpts.options[raw as int]
            thermostatEvent("valve_detection", value, raw)
            break;
        case 0x0275:    // result['valve_alarm'] = {1: true, 0: false}[value]; - read only!
            raw = hexStrToUnsignedInt(descMap.value)
            value = ValveAlarmOpts.options[raw as int]
            thermostatEvent("valve_alarm", value, raw)
            break;
        case 0x0277:    // result['child_lock'] = {1: 'LOCK', 0: 'UNLOCK'}[value]; - rw
            raw = hexStrToUnsignedInt(descMap.value)
            value = ChildLockOpts.options[raw as int]
            thermostatEvent("child_lock", value, raw)
            break;
        case 0x0279:    // result['away_preset_temperature'] = (value / 100).toFixed(1); - rw
            raw = hexStrToUnsignedInt(descMap.value)
            value = raw / 100
            thermostatEvent("away_preset_temperature", value, raw)
            break;
        case 0x027a:    // result['window_open'] = {1: true, 0: false}[value]; - read only
            raw = hexStrToUnsignedInt(descMap.value)
            value = WindowOpenOpts.options[raw as int]
            thermostatEvent("window_open", value, raw)
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
}

// called from parseXiaomiClusterThermostatLib 
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
    if (settings.logEnable) {
        log.trace "zigbee received Thermostat cluster (0x0201) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    }

    switch (descMap.attrInt as Integer) {
        case 0x000:                      // temperature
            logDebug "temperature = ${value/100.0} (raw ${value})"
            handleTemperatureEvent(value/100.0)
            break
        case 0x0011:                      // cooling setpoint
            logInfo "cooling setpoint = ${value/100.0} (raw ${value})"
            break
        case 0x0012:                      // heating setpoint
            logInfo "heating setpoint = ${value/100.0} (raw ${value})"
            handleHeatingSetpointEvent(value/100.0)
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
}

def handleHeatingSetpointEvent( temperature ) {
    setHeatingSetpoint(temperature)
}

//  ThermostatHeatingSetpoint command
//  sends TuyaCommand and checks after 4 seconds
//  1°C steps. (0.5°C setting on the TRV itself, rounded for zigbee interface)
def setHeatingSetpoint( temperature ) {
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
    def maxTemp = settings?.maxThermostatTemp ?: 50
    def minTemp = settings?.minThermostatTemp ?: 5
    if (tempDouble > maxTemp ) tempDouble = maxTemp
    if (tempDouble < minTemp) tempDouble = minTemp
    tempDouble = tempDouble.round(1)
    Map eventMap = [name: "heatingSetpoint",  value: tempDouble, unit: "\u00B0"+"C"]
    eventMap.descriptionText = "heatingSetpoint is ${tempDouble}"
    sendHeatingSetpointEvent(eventMap)
    eventMap = [name: "thermostatSetpoint", value: tempDouble, unit: "\u00B0"+"C"]
    eventMap.descriptionText = null
    sendHeatingSetpointEvent(eventMap)
    updateDataValue("lastRunningMode", "heat")
    // 
    zigbee.writeAttribute(0x0201, 0x12, 0x29, (tempDouble * 100) as int)        // raw:F6690102010A1200299808, dni:F669, endpoint:01, cluster:0201, size:0A, attrId:0012, encoding:29, command:0A, value:0898, clusterInt:513, attrInt:18
}

private void sendHeatingSetpointEvent(Map eventMap) {
    if (eventMap.descriptionText != null) { logInfo "${eventMap.descriptionText}" }
    sendEvent(eventMap)
}

// TODO - not called 
void processTuyaDpThermostat(descMap, dp, dp_id, fncmd) {

    switch (dp) {
        case 0x01 : // on/off
            sendSwitchEvent(fncmd)
            break
        default :
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            break            
    }
}


def preset( preset ) {
    logDebug "preset(${preset}) called!"
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
        log.warn "setPresetMode NOT IMPLEMENTED"
        return
    }
    if (cmds == []) { cmds = ["delay 299"] }
    sendZigbeeCommands(cmds)

}

def setThermostatMode( mode ) {
    List<String> cmds = []
    logDebug "sending setThermostatMode(${mode})"
    //state.mode = mode
    if (isAqaraTRV()) {
        // TODO - set Aqara E1 thermostat mode
        switch(mode) {
            case "off":
                cmds = zigbee.writeAttribute(0xFCC0, 0x0271, 0x20, 0x00, [mfgCode: 0x115F], delay=200)        // 'off': 0, 'heat': 1
                break
            case "heat":
                cmds = zigbee.writeAttribute(0xFCC0, 0x0271, 0x20, 0x01, [mfgCode: 0x115F], delay=200)        // 'off': 0, 'heat': 1
                break
            default:
                logWarn "setThermostatMode: unknown AqaraTRV mode ${mode}"
                break
        }
    }
    else {
        // TODO - set generic thermostat mode
        log.warn "setThermostatMode NOT IMPLEMENTED"
        return
    }
    if (cmds == []) { cmds = ["delay 299"] }
    sendZigbeeCommands(cmds)
}

def setCoolingSetpoint(temperature){
    logDebug "setCoolingSetpoint(${temperature}) called!"
    if (temperature != (temperature as int)) {
        temperature = (temperature + 0.5 ) as int
        logDebug "corrected temperature: ${temperature}"
    }
    sendEvent(name: "coolingSetpoint", value: temperature, unit: "\u00B0"+"C")
}


def heat(){
    setThermostatMode("heat")
}

def thermostatOff(){
    setThermostatMode("off")
}

def thermostatOn() {
    heat()
}

def setThermostatFanMode(fanMode) { sendEvent(name: "thermostatFanMode", value: "${fanMode}", descriptionText: getDescriptionText("thermostatFanMode is ${fanMode}")) }
def auto() { setThermostatMode("auto") }
def emergencyHeat() { setThermostatMode("emergency heat") }
def cool() { setThermostatMode("cool") }
def fanAuto() { setThermostatFanMode("auto") }
def fanCirculate() { setThermostatFanMode("circulate") }
def fanOn() { setThermostatFanMode("on") }

def sendThermostatOperatingStateEvent( st ) {
    sendEvent(name: "thermostatOperatingState", value: st)
    state.lastThermostatOperatingState = st
}

void sendSupportedThermostatModes() {
    def supportedThermostatModes = []
    supportedThermostatModes = ["off", "heat", "auto"]
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

/**
 * Scheduled job for polling device specific attribute(s)
 */
void autoPollThermostat() {
    logDebug "autoPollThermostat()..."
    checkDriverVersion()
    List<String> cmds = []
    if (state.states == null) state.states = [:]
    //state.states["isRefresh"] = true
    
    cmds += zigbee.readAttribute(0x0201, 0x0000, [:], delay=3500)      // 0x0000=local temperature, 0x0011=cooling setpoint, 0x0012=heating setpoint, 0x001B=controlledSequenceOfOperation, 0x001C=system mode (enum8 )       
    
    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }    
}

//
// called from updated() in the main code ...
void updatedThermostat() {
    logDebug "updatedThermostat()..."
    //
    if (settings?.forcedProfile != null) {
        logDebug "current state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${getProfileKey(settings?.forcedProfile)}"
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
}

def refreshThermostat() {
    List<String> cmds = []
    //cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)                                         // battery voltage (E1 does not send percentage)
    cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0011, 0x0012, 0x001B, 0x001C], [:], delay=3500)       // 0x0000=local temperature, 0x0011=cooling setpoint, 0x0012=heating setpoint, 0x001B=controlledSequenceOfOperation, 0x001C=system mode (enum8 )       

    cmds += zigbee.readAttribute(0xFCC0, [0x0271, 0x0272, 0x0273, 0x0274, 0x0275, 0x0277, 0x0279, 0x027A, 0x027B, 0x027E], [mfgCode: 0x115F], delay=3500)       
    cmds += zigbee.readAttribute(0xFCC0, 0x040a, [mfgCode: 0x115F], delay=500)       
   
    // stock Generic Zigbee Thermostat Refresh answer:
    // raw:F669010201441C0030011E008600000029640A2900861B0000300412000029540B110000299808, dni:F669, endpoint:01, cluster:0201, size:44, attrId:001C, encoding:30, command:01, value:01, clusterInt:513, attrInt:28, additionalAttrs:[[status:86, attrId:001E, attrInt:30], [value:0A64, encoding:29, attrId:0000, consumedBytes:5, attrInt:0], [status:86, attrId:0029, attrInt:41], [value:04, encoding:30, attrId:001B, consumedBytes:4, attrInt:27], [value:0B54, encoding:29, attrId:0012, consumedBytes:5, attrInt:18], [value:0898, encoding:29, attrId:0011, consumedBytes:5, attrInt:17]]
    // conclusion : binding and reporting configuration for this Aqara E1 thermostat does nothing... We need polling mechanism for faster updates of the internal temperature readings.
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


void initEventsThermostat(boolean fullInit=false) {
    sendSupportedThermostatModes()
    sendEvent(name: "supportedThermostatFanModes", value: JsonOutput.toJson(["auto"]), isStateChange: true)    
    sendEvent(name: "thermostatMode", value: "heat", isStateChange: true, description: "inital attribute setting")
    sendEvent(name: "thermostatFanMode", value: "auto", isStateChange: true, description: "inital attribute setting")
    state.lastThermostatMode = "heat"
    sendThermostatOperatingStateEvent( "idle" )
    sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true, description: "inital attribute setting")
    sendEvent(name: "thermostatSetpoint", value:  12.3, unit: "\u00B0"+"C", isStateChange: true, description: "inital attribute setting")        // Google Home compatibility
    sendEvent(name: "heatingSetpoint", value: 12.3, unit: "\u00B0"+"C", isStateChange: true, description: "inital attribute setting")
    sendEvent(name: "coolingSetpoint", value: 34.5, unit: "\u00B0"+"C", isStateChange: true, description: "inital attribute setting")
    sendEvent(name: "temperature", value: 23.4, unit: "\u00B0"+"C", isStateChange: true, description: "inital attribute setting")    
    updateDataValue("lastRunningMode", "heat")    
    
}

private getDescriptionText(msg) {
    def descriptionText = "${device.displayName} ${msg}"
    if (settings?.txtEnable) log.info "${descriptionText}"
    return descriptionText
}

def testT(par) {
    logWarn "testT(${par})"
}

