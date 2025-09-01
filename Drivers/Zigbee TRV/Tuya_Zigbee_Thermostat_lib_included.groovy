/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateStringLiteral, ImplicitClosureParameter, MethodCount, MethodSize, NglParseError, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessarySetter, UnusedImport */
/**
 *  Tuya Zigbee TRVs and Thermostats - Device Driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/release-tuya-zigbee-thermostats-and-trvs-driver/128916
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
 * ver. 3.3.0  2024-06-07 kkossev  - separate new 'Tuya Zigbee Thermostat' driver for Tuya Thermostats and TRVs.
 * ver. 3.3.1  2024-07-09 kkossev  - driver renamed to 'Tuya Zigbee TRVs and Thermostats'
 * ver. 3.3.2  2024-07-18 kkossev  - added AVATTO_TRV06_TRV16_ME167_ME168_TRV profile; added '_TZE200_bvrlmajk' as 'AVATTO TRV-07'; added 'IMMAX_Neo Lite TRV 07732L' profile; temperaturePollingInterval disabled for Tuya TRVs
 *                                   'Battery Voltage to Percentage' and 'Minimum time between reports' are hidden; 
 * ver. 3.3.3  2024-08-01 kkossev  - added _TZE200_g9a3awaj 'AVATTO_ZWT07_BATTERY_THERMOSTAT'
 * ver. 3.4.0  2024-10-05 kkossev  - added to HPM
 * ver. 3.4.1  2024-10-26 kkossev  - fixed exception in sendDigitalEventIfNeeded when the attribute is not found (level); added faultAlarm attribute; added TRV602 profile (TS0601  _TZE200_rtrmfadk)
 *                                   added TRV602Z profile (TS0601 _TZE204_ltwbm23f); queryAllTuyaDP() when refreshing TRV602 and TRV602Z;
 * ver. 3.4.2  2024-11-12 kkossev  - added TS0601 _TZE204_xnbkhhdr (_TZE200_viy9ihs7 _TZE200_viy9ihs7) thermostat profile 'AVATTO_ZWT198_ZWT100-BH_THERMOSTAT'
 * ver. 3.5.0  2025-02-16 kkossev  - restored sendCommand and setPar; added brightness attribute; added checkIfIsDuplicated; added Resend failed commands : setThermostatMode and heatingSetpoint retries; added forceManual prefrence
 * ver. 3.5.1  2025-04-08 kkossev  - urgent fix for java.lang.CloneNotSupportedException
 * ver. 3.5.2  2025-05-25 kkossev  - HE platfrom version 2.4.1.x decimal preferences patch/workaround.
 *
 *                                   TODO:
 */

static String version() { '3.5.2' }
static String timeStamp() { '2025/05/25 8:54 AM' }

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
        name: 'Tuya Zigbee TRVs and Thermostats',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee%20TRV/Tuya_Zigbee_Thermostat_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {

        // TODO - add all other models attributes possible values
        attribute 'antiFreeze', 'enum', ['off', 'on']               // TUYA_SASWELL_TRV, AVATTO_TRV06_TRV16_ME167_ME168_TRV, 
        attribute 'batteryVoltage', 'number'
        attribute 'boostTime', 'number'                             // BRT-100
        attribute 'calibrated', 'enum', ['false', 'true']           // Aqara E1
        attribute 'brightness', 'enum', ['off', 'low', 'medium', 'high']    // BEOK/AVATTO
        attribute 'calibrationTemp', 'number'                       // BRT-100, Sonoff
        attribute 'childLock', 'enum', ['off', 'on']                // BRT-100, Aqara E1, Sonoff, AVATTO
        attribute 'comfortTemp', 'number'                           // TRV602Z, IMMAX_Neo Lite TRV 07732L
        attribute 'controlMode', 'enum', ['PID', 'OnOff']           // TRV602Z
        attribute 'ecoMode', 'enum', ['off', 'on']                  // BRT-100
        attribute 'ecoTemp', 'number'                               // BRT-100, TRV602Z
        attribute 'emergencyHeating', 'enum', ['off', 'on']         // BRT-100
        attribute 'emergencyHeatingTime', 'number'                  // BRT-100
        attribute 'floorTemperature', 'number'                      // AVATTO/MOES floor thermostats
        attribute 'frostProtectionTemperature', 'number'            // Sonoff
        attribute 'hysteresis', 'number'                            // AVATTO, Virtual thermostat
        attribute 'level', 'number'                                 // BRT-100
        attribute 'maxHeatingSetpoint', 'number'                    // BRT-100, Sonoff, AVATTO
        attribute 'minHeatingSetpoint', 'number'                    // BRT-100, Sonoff, AVATTO
        attribute 'sensor', 'enum', ['internal', 'external', 'both']         // Aqara E1, AVATTO
        attribute 'systemMode', 'enum', ['off', 'on']               // Aqara E1, AVATTO
        attribute 'valveAlarm', 'enum',  ['false', 'true']          // Aqara E1
        attribute 'valveDetection', 'enum', ['off', 'on']           // Aqara E1
        attribute 'weeklyProgram', 'number'                         // BRT-100
        attribute 'windowOpenDetection', 'enum', ['off', 'on']      // BRT-100, Aqara E1, Sonoff
        attribute 'windowsState', 'enum', ['open', 'closed']        // BRT-100, Aqara E1
        attribute 'batteryLowAlarm', 'enum', ['batteryOK', 'batteryLow']        // TUYA_SASWELL
        //attribute 'workingState', "enum", ["open", "closed"]        // BRT-100
        attribute 'faultAlarm', 'enum', ['clear', 'faultSensor', 'faultMotor', 'faultLowBatt', 'faultUgLowBatt', 'error']        // TUYA
        attribute 'motorThrust', 'enum', ['strong', 'middle', 'weak']   // TRV602Z

        // Aqaura E1 attributes     TODO - consolidate a common set of attributes
        attribute 'preset', 'enum', ['manual', 'auto', 'away']      // TODO - remove?
        attribute 'awayPresetTemperature', 'number'

        command 'sendCommand', [
            [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']],
            [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']]
        ]
        command 'setPar', [
                [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']],
                [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']]
        ]
    
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
        if (advancedOptions == true) {
            input (name: 'resendFailed', type: 'bool', title: '<b>Resend failed commands</b>', description: '<i>If the thermostat does not change the Setpoint or Mode as expected, then commands will be resent automatically</i>', defaultValue: false)
            input (name: 'forceManual',  type: 'bool', title: '<b>Force Manual Mode</b>', description: '<i>If the thermostat changes into schedule mode, then it automatically reverts back to manual mode</i>', defaultValue: false)
        }

    }
}

@Field static final Integer MaxRetries = 5
@Field static final Integer NOT_SET = -1

@Field static final Map deviceProfilesV3 = [    // https://github.com/jacekk015/zha_quirks
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
    'TUYA_SASWELL_TRV'    : [
        description   : 'Tuya Saswell TRV (not tested!)',
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

    // https://github.com/Koenkk/zigbee2mqtt/issues/18872#issuecomment-1869439394 Few words to note that _TZE200_rxntag7i TS0601 / Tuya - Avatto TRV16 does not fit to ME168 TRV model.
    'AVATTO_TRV06_TRV16_ME167_ME168_TRV'   : [
        description   : 'AVATTO TRV6/TRV16/ME167/ME168 TRV',   // AVATTO TRV06
        // _TZE200_bvu2wnxz
        device        : [models: ['TS0601'], type: 'TRV', powerSource: 'battery', isSleepy:false],
        capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
        preferences   : ['childLock':'7', 'antiFreeze':'36', 'limescaleProtect':'39', 'calibrationTemp':'47'],
        fingerprints  : [
            [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_p3dbf6qs', deviceJoinName: 'Avatto ME168 TRV06'],        // https://community.hubitat.com/t/release-tuya-wall-mount-thermostat-water-electric-floor-heating-zigbee-driver/87050/249?u=kkossev
            [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_rxntag7i', deviceJoinName: 'Avatto ME168 TRV16'],        // https://community.hubitat.com/t/beta-tuya-zigbee-thermostats-and-trvs-driver/128916/39?u=kkossev
            [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_6rdj8dzm', deviceJoinName: 'Avatto ME167 TRV'],
            [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_bvu2wnxz', deviceJoinName: 'Avatto ME167 TRV'],
        ],
        commands      : ["pollTuya":"pollTuya","autoPollThermostat":"autoPollThermostat", 'sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
        tuyaDPs       : [
            [dp:2,   name:'thermostatMode',     type:'enum',            rw: 'rw', min:0,     max:2,    defVal:'1',  step:1,   scale:1,  map:[0: 'auto', 1: 'heat', 2: 'off'] , title:'<b>Thermostat Mode</b>',  description:'Thermostat mode'],     // 'system_mode'
            [dp:3,   name:'thermostatOperatingState',  type:'enum',     rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'heating', 1:'idle'] ,  unit:'', description:'Thermostat Operating State(working state)'],           // running_state
            [dp:4,   name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,   max:35.0, defVal:20.0, step:1.0, scale:10, unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
            [dp:5,   name:'temperature',        type:'decimal',         rw: 'ro', min:0.0,   max:50.0, defVal:20.0, step:1.0, scale:10, unit:'°C',  description:'Temperature'],     // current_heating_setpoint
            [dp:7,   name:'childLock',          type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Child Lock</b>',  description:'Child lock'],
            [dp:28,  name:'scheduleMonday',     type:'number',          rw: 'ro',  description:'Schedule Monday'],
            [dp:29,  name:'scheduleTuesday',    type:'number',          rw: 'ro',  description:'Schedule Tueasday'],
            [dp:30,  name:'scheduleWednesday',  type:'number',          rw: 'ro',  description:'Schedule Wednesday'],
            [dp:31,  name:'scheduleThursday',   type:'number',          rw: 'ro',  description:'Schedule Thursday'],
            [dp:32,  name:'scheduleFriday',     type:'number',          rw: 'ro',  description:'Schedule Friday'],
            [dp:33,  name:'scheduleSaturday',   type:'number',          rw: 'ro',  description:'Schedule Saturday'],
            [dp:34,  name:'scheduleSunday',     type:'number',          rw: 'ro',  description:'Schedule Sunday'],
            [dp:35,  name:'batteryLowAlarm',    type:'enum',  dt: '01', rw: 'ro', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'lowBattery', 1:'sensorFault'], description:'Alarm'],                                            // fault_alarm tuya.valueConverter.errorOrBatteryLow]
            [dp:36,  name:'antiFreeze',         type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'], title:'<b>Anti-Freeze</b>',  description:'Anti-Freeze support'],                     // frost_protection'
            [dp:39,  name:'limescaleProtect',   type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'], title:'<b>Limescale Protect</b>',  description:'Limescale Protection support'],      // scale_protection
            [dp:47,  name:'calibrationTemp',    type:'decimal',         rw: 'rw', min:-3.0,  max:3.0,  defVal:0.0,  step:1,   scale:1,  unit:'°C',  title:'<b>Calibration Temperature</b>', description:'Calibration Temperature'],                  // local_temperature_calibration
        ],
        supportedThermostatModes: ['off', 'heat', 'auto'],
        refresh: ['pollTuya'],
        deviceJoinName: 'AVATTO TRV6/TRV16/ME167/ME168 TRV',
        configuration : [:]
    ],

    'AVATTO-TRV07'   : [        // https://github.com/eteodun/Avatto-TRV07-TS0601-_TZE200_bvrlmajk/blob/main/tuyats601.js
        description   : 'AVATTO TRV-07 (not tested!)',
        device        : [models: ['TS0601'], type: 'TRV', powerSource: 'battery', isSleepy:false],
        capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
        preferences   : ['windowOpenDetection':'8', 'childLock':'12', 'calibrationTemp':'101', 'minHeatingSetpoint':'15', 'maxHeatingSetpoint':'16', 'brightness':'111', 'orientation':'113', 'ecoMode':'114'],
        fingerprints  : [
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_bvrlmajk', deviceJoinName: 'AVATTO TRV-07']
        ],
        commands      : ['sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
        tuyaDPs       : [
            [dp:1,   name:'thermostatMode',     type:'enum',            rw: 'rw', min:0,     max:3,    defVal:'1',  step:1,   scale:1,  map:[0: 'auto', 1: 'heat', 2: 'off', 3: 'emergency heat'] ,   unit:'', title:'<b>Thermostat Mode</b>',  description:'Thermostat mode'],
            [dp:2,   name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,   max:35.0, defVal:20.0, step:0.5, scale:10,  unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
            [dp:3,   name:'temperature',        type:'decimal',         rw: 'ro', min:-10.0, max:50.0, defVal:20.0, step:0.5, scale:10, unit:'°C',  description:'Temperature'],
            [dp:6,   name:'thermostatOperatingState',  type:'enum',     rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'heating', 1:'idle'],  unit:'', description:'Thermostat Operating State(working state)'],
            [dp:7,   name:'windowsState',       type:'enum',            rw: 'ro', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'open', 1:'closed'] ,   unit:'', title:'<bWindow State</b>',  description:'Window state'],
            [dp:8,   name:'windowOpenDetection', type:'enum', dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Window Detection</b>',  description:'Window detection'],
            [dp:12,  name:'childLock',          type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Child Lock</b>',  description:'Child lock'],
            [dp:13,  name:'battery',            type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:1,   scale:1,  unit:'%',          description:'Battery level'],
            [dp:14,  name:'faultAlarm',         type:'enum',            rw: 'ro', defVal:'0', map:[0:'clear', 1:'faultSensor', 2:'faultMotor', 4:'faultLowBatt', 8:'faultUgLowBatt'],  description:'Fault alarm'],
            [dp:15,  name:'minHeatingSetpoint', type:'decimal',         rw: 'rw', min:5.0,   max:15.0, defVal:10.0, step:1.0, scale:10,  unit:'°C',  title: '<b>Minimum Temperature</b>',      description:'Minimum temperature'],
            [dp:16,  name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:15.0,  max:45.0, defVal:35.0, step:1.0, scale:10,  unit:'°C',  title: '<b>Maximum Temperature</b>',      description:'Maximum temperature'],
            [dp:17,  name:'scheduleMonday',     type:'number',          rw: 'ro', description:'Schedule Monday'],
            [dp:18,  name:'scheduleTuesday',    type:'number',          rw: 'ro', description:'Schedule Tueasday'],
            [dp:19,  name:'scheduleWednesday',  type:'number',          rw: 'ro', description:'Schedule Wednesday'],
            [dp:20,  name:'scheduleThursday',   type:'number',          rw: 'ro', description:'Schedule Thursday'],
            [dp:21,  name:'scheduleFriday',     type:'number',          rw: 'ro', description:'Schedule Friday'],
            [dp:22,  name:'scheduleSaturday',   type:'number',          rw: 'ro', description:'Schedule Saturday'],
            [dp:23,  name:'scheduleSunday',     type:'number',          rw: 'ro', description:'Schedule Sunday'],
            [dp:101, name:'calibrationTemp',    type:'decimal',         rw: 'rw', min:-10.0,  max:19.0,  defVal:0.0, step:0.1,   scale:10,  unit:'°C',  title:'<b>Calibration Temperature</b>', description:'Calibration Temperature'],
            [dp:108, name:'level',              type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:10,   scale:10,  unit:'%', description:'Valve level'],      // valve open degree
            [dp:109, name:'model',              type:'number',          rw: 'ro', description:'Manufacturer model'],      // STRING!
            [dp:110, name:'motorThrust',        type:'enum',            rw: 'ro', defVal:'1', map:[0:'strong', 1:'middle', 2:'weak'], description:'Motor thrust'],
            [dp:111, name:'brightness',         type:'enum',            rw: 'rw', defVal:'1', map:[0:'high', 1:'medium', 2:'low'], title:'<b>Display Brightness</b>',  description:'Display brightness'],
            [dp:112, name:'softVersion',        type:'number',          rw: 'ro', description:'Software version'],
            [dp:113, name:'orientation',        type:'enum',            rw: 'rw', defVal:'1', map:[0:'up', 1:'right', 2:'down', 3:'left'], title:'<b>Screen Orientation</b>',  description:'Screen orientation'],
            [dp:114, name:'ecoMode',            type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Eco mode</b>',  description:'Eco mode'],
            [dp:115, name:'switchDeviation',    type:'decimal',         rw: 'ro', min:0.5,   max:5.0,  defVal:1.0,  step:10,   scale:10,  unit:'°C', description:'Valve level'],      // ECO mode only?
            // ^^^^ "system_mode": { "type": "enum", "range": [ "comfort_mode", "Eco_mode"    ]      },
            //{'comfort': tuya.enum(0), 'eco': tuya.enum(1)}
        ],
        supportedThermostatModes: ['off', 'heat', 'auto', 'emergency heat', 'eco'],
        refresh: ['pollTuya'],
        deviceJoinName: 'AVATTO TRV-07',
        configuration : [:]
    ],

    'Tuya_TRV602'   : [        // https://github.com/Koenkk/zigbee-herdsman-converters/blob/f7cb1f160f07fcca7a354429cad9d63c7272ea85/src/devices/tuya.ts#L3285-L3348
        description   : 'Tuya TRV602 TRV',
        device        : [models: ['TS0601'], type: 'TRV', powerSource: 'battery', isSleepy:false],
        capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
        preferences   : ['windowOpenDetection':'8', 'childLock':'12', /*'calibrationTemp':'101',*/ 'minHeatingSetpoint':'15', 'maxHeatingSetpoint':'16', 'brightness':'111', 'orientation':'113', 'ecoMode':'114'],
        fingerprints  : [
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_rtrmfadk', deviceJoinName: 'Tuya TRV602'],
        ],
        commands      : ['sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
        tuyaDPs       : [
            [dp:1,   name:'thermostatMode',     type:'enum',            rw: 'rw', min:0,     max:3,    defVal:'1',  step:1,   scale:1,  map:[0: 'auto', 1: 'heat', 2: 'off', 3: 'emergency heat'] ,   unit:'', title:'<b>Thermostat Mode</b>',  description:'Thermostat mode'],    // 'system_mode'
            [dp:2,   name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,   max:35.0, defVal:20.0, step:0.5, scale:10,  unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
            [dp:3,   name:'temperature',        type:'decimal',         rw: 'ro', min:-10.0, max:50.0, defVal:20.0, step:0.5, scale:10, unit:'°C',  description:'Temperature'],
            [dp:6,   name:'thermostatOperatingState',  type:'enum',     rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'idle', 1:'heating'], description:'Thermostat Operating State(working state)'],
            [dp:7,   name:'windowsState',       type:'enum',            rw: 'ro', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'closed', 1:'open'],  title:'<bWindow State</b>',  description:'Window state'],
            [dp:8,   name:'windowOpenDetection', type:'enum', dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Window Detection</b>',  description:'Window detection'],
            [dp:12,  name:'childLock',          type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Child Lock</b>',  description:'Child lock'],
            [dp:13,  name:'battery',            type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:1,   scale:1,  unit:'%',          description:'Battery level'],
            [dp:14,  name:'faultAlarm',         type:'enum',            rw: 'ro', defVal:'0', map:[0:'clear', 1:'error'],  description:'Fault alarm'],
            [dp:15,  name:'minHeatingSetpoint', type:'decimal',         rw: 'rw', min:5.0,   max:15.0, defVal:10.0, step:1.0, scale:10,  unit:'°C',  title: '<b>Minimum Temperature</b>',      description:'Minimum temperature'],
            [dp:16,  name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:15.0,  max:45.0, defVal:35.0, step:1.0, scale:10,  unit:'°C',  title: '<b>Maximum Temperature</b>',      description:'Maximum temperature'],
            [dp:17,  name:'scheduleMonday',     type:'number',          rw: 'ro', description:'Schedule Monday'],
            [dp:18,  name:'scheduleTuesday',    type:'number',          rw: 'ro', description:'Schedule Tueasday'],
            [dp:19,  name:'scheduleWednesday',  type:'number',          rw: 'ro', description:'Schedule Wednesday'],
            [dp:20,  name:'scheduleThursday',   type:'number',          rw: 'ro', description:'Schedule Thursday'],
            [dp:21,  name:'scheduleFriday',     type:'number',          rw: 'ro', description:'Schedule Friday'],
            [dp:22,  name:'scheduleSaturday',   type:'number',          rw: 'ro', description:'Schedule Saturday'],
            [dp:23,  name:'scheduleSunday',     type:'number',          rw: 'ro', description:'Schedule Sunday'],
            [dp:101, name:'calibrationTemp',    type:'decimal',         rw: 'ro', min:-10.0,  max:19.0,  defVal:0.0, step:0.1,   scale:1,  unit:'°C',  title:'<b>Calibration Temperature</b>', description:'Calibration Temperature'],
            // ^^^^^ calibrationTemp is temporary made read-only !!  https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/lib/tuya.ts#L845
            [dp:108, name:'level',              type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:10,   scale:10,  unit:'%', description:'Valve level'],      // valve open degree
            [dp:111, name:'brightness',         type:'enum',            rw: 'rw', defVal:'1', map:[0:'high', 1:'medium', 2:'low'], title:'<b>Display Brightness</b>',  description:'Display brightness'],
            [dp:113, name:'orientation',        type:'enum',            rw: 'rw', defVal:'1', map:[0:'up', 1:'right', 2:'down', 3:'left'], title:'<b>Screen Orientation</b>',  description:'Screen orientation'],
            [dp:114, name:'ecoMode',            type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Eco mode</b>',  description:'Eco mode'],       // {'comfort': tuya.enum(0), 'eco': tuya.enum(1)}
        ],
        supportedThermostatModes: ['off', 'heat', 'auto', 'emergency heat', 'eco'],
        refresh: ['queryAllTuyaDP'],
        deviceJoinName: 'Tuya TRV602 TRV',
        configuration : [:]
    ],

    'Tuya_TRV602Z'   : [        // https://github.com/Koenkk/zigbee-herdsman-converters/blob/af278d3a520a3fb37e7417f41e613cc39c485ea9/src/devices/tuya.ts#L5117C62-L5241
        description   : 'Tuya TRV602Z TRV',
        device        : [models: ['TS0601'], type: 'TRV', powerSource: 'battery', isSleepy:false],
        capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
        preferences   : ['controlMode':'127', 'childLock':'7', 'minHeatingSetpoint':'10', 'maxHeatingSetpoint':'9', 'windowOpenDetection':'14',  'calibrationTemp':'47', 'brightness':'111','orientation':'113', 'comfortTemp':'119', 'ecoTemp':'120'],
        fingerprints  : [
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_ltwbm23f', deviceJoinName: 'Tuya TRV602Z TRV']      // https://s.click.aliexpress.com/e/_DDZf6Md
        ],
        commands      : ["pollTuya":"pollTuya","autoPollThermostat":"autoPollThermostat", 'sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
        tuyaDPs       : [
            [dp:2,   name:'thermostatMode',     type:'enum',            rw: 'rw', min:0,     max:5,    defVal:'3',  step:1,   scale:1,  map:[
                    0: 'off',       // standby: tuya.enum(0),
                    1: 'antifrost', // antifrost: tuya.enum(1),
                    2: 'eco',       // eco: tuya.enum(2),
                    3: 'heat',      // comfort: tuya.enum(3),
                    4: 'auto',      // auto: tuya.enum(4),
                    5: 'emergency heat',        // on: tuya.enum(5),
                ] , title:'<b>Thermostat Mode</b>',  description:'Thermostat mode'],     // 'system_mode'   [0: 'auto', 1: 'heat', 2: 'off', 3: 'emergency heat'] 
            //[dp:3,   name:'thermostatOperatingState',  type:'enum',     rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'heating', 1:'idle'] ,  unit:'', description:'Thermostat Operating State(working state)'],           // running_state
            [dp:4,   name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,   max:35.0, defVal:20.0, step:0.5, scale:10, unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
            [dp:5,   name:'temperature',        type:'decimal',         rw: 'ro', min:0.0,   max:50.0, defVal:20.0, step:0.5, scale:10, unit:'°C',  description:'Temperature'],     // current_heating_setpoint
            [dp:6,   name:'battery',            type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:1,   scale:1,  unit:'%',   description:'Battery level'],
            [dp:7,   name:'childLock',          type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'], title:'<b>Child Lock</b>',  description:'Child lock'],
            [dp:9,   name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:15.0,  max:45.0, defVal:35.0, step:1.0, scale:10, unit:'°C',  title: '<b>Maximum Temperature</b>',      description:'Maximum temperature'],
            [dp:10,  name:'minHeatingSetpoint', type:'decimal',         rw: 'rw', min:5.0,   max:15.0, defVal:10.0, step:1.0, scale:10, unit:'°C',  title: '<b>Minimum Temperature</b>',      description:'Minimum temperature'],
            [dp:14,  name:'windowOpenDetection', type:'enum', dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Window Detection</b>',  description:'Window detection'],
            [dp:15,  name:'windowsState',       type:'enum',            rw: 'ro', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'closed', 1:'open'],  title:'<bWindow State</b>',  description:'Window state'],
            [dp:47,  name:'calibrationTemp',    type:'decimal',         rw: 'rw', min:-3.0,  max:3.0,  defVal:0.0,  step:1,   scale:1,  unit:'°C',  title:'<b>Calibration Temperature</b>', description:'Calibration Temperature'],                  // local_temperature_calibration
            // ^^ check! ^^^ tuya.valueConverter.localTempCalibration1
            [dp:102, name:'scheduleMonday',     type:'number',          rw: 'ro',  description:'Schedule Monday'],
            [dp:103, name:'scheduleTuesday',    type:'number',          rw: 'ro',  description:'Schedule Tueasday'],
            [dp:104, name:'scheduleWednesday',  type:'number',          rw: 'ro',  description:'Schedule Wednesday'],
            [dp:105, name:'scheduleThursday',   type:'number',          rw: 'ro',  description:'Schedule Thursday'],
            [dp:106, name:'scheduleFriday',     type:'number',          rw: 'ro',  description:'Schedule Friday'],
            [dp:107, name:'scheduleSaturday',   type:'number',          rw: 'ro',  description:'Schedule Saturday'],
            [dp:108, name:'scheduleSunday',     type:'number',          rw: 'ro',  description:'Schedule Sunday'],
            [dp:110, name:'motorThrust',        type:'enum',            rw: 'ro', defVal:'1', map:[0:'strong', 1:'middle', 2:'weak'], description:'Motor thrust'],
            [dp:111, name:'brightness',         type:'enum',            rw: 'rw', defVal:'1', map:[0:'high', 1:'medium', 2:'low'], title:'<b>Display Brightness</b>',  description:'Display brightness'],
            [dp:113, name:'orientation',        type:'enum',            rw: 'rw', defVal:'1', map:[0:'up', 1:'right', 2:'down', 3:'left'], title:'<b>Screen Orientation</b>',  description:'Screen orientation'],
            [dp:114, name:'level',              type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:10,   scale:10,  unit:'%', description:'Valve level'],      // valve open degree (position)
            [dp:119, name:'comfortTemp',        type:'decimal',         rw: 'rw', min:5.0,   max:30.0, defVal:20.0, step:1.0, scale:10,  unit:'°C', title: '<b>Comfort Temperature</b>',      description:'Comfort temperature'],
            [dp:120, name:'ecoTemp',            type:'decimal',         rw: 'rw', min:5.0,   max:30.0, defVal:7.0,  step:1.0, scale:10,  unit:'°C',  title: '<b>Eco Temperature</b>',      description:'Eco temperature'],
            [dp:127, name:'controlMode',        type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'PID', 1:'onOff'] ,   unit:'', title:'<b>Control Mode</b>',  description:'PID - valve smooth control from 0% to 100%<br>onOff - 0.5 degrees above or below, valve either 0% or 100%'],   // 'system_mode', { comfort: 0, eco:(1) }
        ],
        supportedThermostatModes: ['off', 'heat', 'auto', 'emergency heat', 'eco'],
        refresh: ['queryAllTuyaDP'],
        deviceJoinName: 'Tuya TRV602Z TRV',
        configuration : [:]
    ],


    'IMMAX_Neo Lite TRV 07732L'   : [
        description   : 'IMMAX_Neo Lite TRV 07732L (not tested!)',
        device        : [models: ['TS0601'], type: 'TRV', powerSource: 'battery', isSleepy:false],
        capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
        preferences   : ['windowOpenDetection':'8', 'childLock':'13', 'boostTime':'103', 'calibrationTemp':'105', 'ecoTemp':'107', 'minHeatingSetpoint':'109', 'maxHeatingSetpoint':'108'],
        fingerprints  : [
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_rufdtfyv', deviceJoinName: 'Immax 07732L TRV'],            // https://community.hubitat.com/t/release-tuya-wall-mount-thermostat-water-electric-floor-heating-zigbee-driver/87050/232?u=kkossev
        ],
        commands      : ['sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
        tuyaDPs       : [
            [dp:2,   name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:1.0,   max:70.0, defVal:20.0, step:0.5, scale:1,  unit:'°C', title: '<b>Current Heating Setpoint</b>', description:'Current heating setpoint'],
            [dp:3,   name:'temperature',        type:'decimal',         rw: 'ro', min:0.0,   max:70.0, defVal:20.0, step:0.5, scale:10, unit:'°C', description:'Temperature'],
            [dp:4,   name:'thermostatMode',     type:'enum',            rw: 'rw', min:0,     max:7,    defVal:'2',  step:1,   scale:1,  map:[0: 'holiday', 1: 'auto', 2: 'manual', 3: 'comfort', 4: 'eco', 5: 'BOOST', 6: 'temp_auto', 7: 'Valve'] ,   unit:'', title:'<b>Auto Mode Type</b>',  description:'Auto mode type'], // mode 
            [dp:7,   name:'childLock',          type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'], title:'<b>Child Lock</b>',  description:'Child lock'],
            [dp:13,  name:'faultAlarm',         type:'enum',            rw: 'ro', defVal:'0', map:[0:'clear', 1:'err1', 2:'err2', 3:'err3', 4:'err4', 5:'err5'],  description:'Fault alarm'],
            [dp:44,  name:'calibrationTemp',    type:'decimal',         rw: 'rw', min:-9.0,  max:9.0,  defVal:0.0,  step:1,   scale:10, unit:'°C', title:'<b>Calibration Temperature</b>', description:'Calibration Temperature'],
            [dp:102, name:'minHeatingSetpoint', type:'decimal',         rw: 'rw', min:1.0,   max:15.0, defVal:10.0, step:1.0, scale:1,  unit:'°C', title: '<b>Minimum Temperature</b>',      description:'Minimum temperature'],
            [dp:103, name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:16.0,  max:70.0, defVal:35.0, step:1.0, scale:1,  unit:'°C', title: '<b>Maximum Temperature</b>',      description:'Maximum temperature'],
            [dp:104, name:'windowOpenDetection', type:'enum', dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'], title:'<b>Window Detection</b>',  description:'Window detection'],   // Window Parameter
            [dp:105, name:'boostTime',          type:'number',          rw: 'rw', min:100,   max:900 , defVal:100,  step:1,   scale:1,  unit:'seconds', title:'<b>Boost Timer</b>',  description:'Boost timer'],
            [dp:106, name:'valveSet',           type:'enum', dt: '01',  rw: 'rw', min:0,     max:2 ,   defVal:'0',  step:1,   scale:1,  map:[0:'normal', 1:'forceOpen', 2:'forceClose'], title:'<b>Valve Set</b>',  description:'Valve set'],
            [dp:107, name:'comfortTemp',        type:'decimal',         rw: 'rw', min:1.0,   max:70.0, defVal:20.0, step:1.0, scale:1,  unit:'°C', title: '<b>Comfort Temperature</b>',      description:'Comfort temperature'],
            [dp:108, name:'ecoTemp',            type:'decimal',         rw: 'rw', min:1.0,   max:70.0, defVal:18.0, step:1.0, scale:1,  unit:'°C', title: '<b>Eco Temperature</b>',      description:'Eco temperature'],
            [dp:109, name:'valveStatus',        type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:1,   scale:1,  unit:'%',  description:'Valve status'],  // Valve Status ?
            [dp:110, name:'battery',            type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:1,   scale:1,  unit:'%',  description:'Battery level'],
            [dp:111, name:'autoModeType',       type:'enum',            rw: 'rw', min:0,     max:7,    defVal:'2',  step:1,   scale:1,  map:[0: 'holiday', 1: 'auto', 2: 'manual', 3: 'comfort', 4: 'eco', 5: 'BOOST', 6: 'temp_auto', 7: 'Valve'] ,   unit:'', title:'<b>Auto Mode Type</b>',  description:'Auto mode type'], // mode 
            [dp:112, name:'workdaySet',         type:'number',          rw: 'ro', description:'Workday Set'],
            [dp:113, name:'restdaySet',         type:'number',          rw: 'ro', description:'Restday Set'],
            [dp:114, name:'holidayTemp',        type:'decimal',         rw: 'rw', min:1.0,   max:70.0, defVal:20.0, step:1.0, scale:1,  unit:'°C', title: '<b>Holiday Temperature</b>', description:'Holiday temperature'],
            [dp:115, name:'windowsState',       type:'enum',            rw: 'ro', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'open', 1:'closed'] ,   unit:'', title:'<bWindow State</b>',  description:'Window state'],
            [dp:116, name:'autoLock',            type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,  scale:1,  map:[0:'off', 1:'on'], title:'<b>Auto Lock</b>',  description:'Auto lock'],
            [dp:117, name:'holidayDays',        type:'number',          rw: 'ro', description:'Holiday Days'],  /// 1..30
            [dp:118, name:'level',              type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:10,  scale:1,  unit:'%',  description:'Valve level'],  // Valve Opening 
        ],
        supportedThermostatModes: ['off', 'heat', 'auto', 'emergency heat', 'eco'],
        refresh: ['pollTuya'],
        deviceJoinName: 'IMMAX_Neo Lite TRV 07732L',
        configuration : [:]
    ],


    'TUYA_HY367_HY368_HY369_TRV'   : [        // https://github.com/Koenkk/zigbee-herdsman-converters/blob/a615c8077123197a6d30aac334160e5dd4cf1058/src/devices/tuya.ts#L3018
        description   : 'TUYA_HY367_HY368_HY369_TRV (not tested!)',
        device        : [models: ['TS0601'], type: 'TRV', powerSource: 'battery', isSleepy:false],
        capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
        preferences   : ['windowOpenDetection':'8', 'childLock':'12', 'boostTime':'103', 'calibrationTemp':'101', 'minHeatingSetpoint':'15', 'maxHeatingSetpoint':'16', 'brightness':'111', 'orientation':'113', 'ecoMode':'114'],
        fingerprints  : [
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ckud7u2l', deviceJoinName: 'TUYA_HY367_HY368_HY369_TRV'],
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ywdxldoj', deviceJoinName: 'TUYA_HY367_HY368_HY369_TRV'],
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_do5qy8zo', deviceJoinName: 'TUYA_HY367_HY368_HY369_TRV'],
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_cwnjrr72', deviceJoinName: 'TUYA_HY367_HY368_HY369_TRV'],
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_pvvbommb', deviceJoinName: 'TUYA_HY367_HY368_HY369_TRV'],
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_9sfg7gm0', deviceJoinName: 'TUYA HomeCloud TRV'],
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_2atgpdho', deviceJoinName: 'TUYA HY367 TRV'],
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_cpmgn2cf', deviceJoinName: 'TUYA_HY367_HY368_HY369_TRV'],  // https://community.hubitat.com/t/release-tuya-wall-mount-thermostat-water-electric-floor-heating-zigbee-driver/87050/253?u=kkossev
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_znlqjmih', deviceJoinName: 'TUYA_HY367_HY368_HY369_TRV'],
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_8thwkzxl', deviceJoinName: 'TUYA Tervix eva2 TRV'],
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_4eeyebrt', deviceJoinName: 'TUYA_HY367_HY368_HY369_TRV'],
            [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_lpwgshtl', deviceJoinName: 'TUYA_HY367_HY368_HY369_TRV'],
        ],
        commands      : ['sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
        tuyaDPs       : [
            [dp:1,   name:'thermostatMode',     type:'enum',            rw: 'rw', min:0,     max:3,    defVal:'1',  step:1,   scale:1,  map:[0: 'auto', 1: 'heat', 2: 'off', 3: 'emergency heat'] ,   unit:'', title:'<b>Thermostat Mode</b>',  description:'Thermostat mode'],
            [dp:2,   name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,   max:35.0, defVal:20.0, step:0.5, scale:10,  unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
            [dp:3,   name:'temperature',        type:'decimal',         rw: 'ro', min:-10.0, max:50.0, defVal:20.0, step:0.5, scale:10, unit:'°C',  description:'Temperature'],
            [dp:6,   name:'thermostatOperatingState',  type:'enum',     rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'heating', 1:'idle'],  unit:'', description:'Thermostat Operating State(working state)'],
            [dp:7,   name:'windowsState',       type:'enum',            rw: 'ro', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'open', 1:'closed'] ,   unit:'', title:'<bWindow State</b>',  description:'Window state'],
            [dp:8,   name:'windowOpenDetection', type:'enum', dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Window Detection</b>',  description:'Window detection'],
            [dp:12,  name:'childLock',          type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Child Lock</b>',  description:'Child lock'],
            [dp:13,  name:'battery',            type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:1,   scale:1,  unit:'%',          description:'Battery level'],
            [dp:14,  name:'faultAlarm',         type:'enum',            rw: 'ro', defVal:'0', map:[0:'active', 1:'clear'],  description:'Fault alarm'],
            [dp:15,  name:'minHeatingSetpoint', type:'decimal',         rw: 'rw', min:5.0,   max:15.0, defVal:10.0, step:1.0, scale:10,  unit:'°C',  title: '<b>Minimum Temperature</b>',      description:'Minimum temperature'],
            [dp:16,  name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:15.0,  max:45.0, defVal:35.0, step:1.0, scale:10,  unit:'°C',  title: '<b>Maximum Temperature</b>',      description:'Maximum temperature'],
            [dp:17,  name:'scheduleMonday',     type:'number',          rw: 'ro', description:'Schedule Monday'],
            [dp:18,  name:'scheduleTuesday',    type:'number',          rw: 'ro', description:'Schedule Tueasday'],
            [dp:19,  name:'scheduleWednesday',  type:'number',          rw: 'ro', description:'Schedule Wednesday'],
            [dp:20,  name:'scheduleThursday',   type:'number',          rw: 'ro', description:'Schedule Thursday'],
            [dp:21,  name:'scheduleFriday',     type:'number',          rw: 'ro', description:'Schedule Friday'],
            [dp:22,  name:'scheduleSaturday',   type:'number',          rw: 'ro', description:'Schedule Saturday'],
            [dp:23,  name:'scheduleSunday',     type:'number',          rw: 'ro', description:'Schedule Sunday'],
            [dp:101, name:'calibrationTemp',    type:'decimal',         rw: 'rw', min:-3.0,  max:3.0,  defVal:00.0, step:0.1,   scale:10,  unit:'°C',  title:'<b>Calibration Temperature</b>', description:'Calibration Temperature'],
            [dp:108, name:'level',              type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:1,   scale:1,  unit:'%',          description:'Valve level'],      // position
            [dp:111, name:'brightness',         type:'enum',            rw: 'rw', defVal:'1', map:[0:'high', 1:'medium', 2:'low'], title:'<b>Display Brightness</b>',  description:'Display brightness'],
            [dp:113, name:'orientation',        type:'enum',            rw: 'rw', defVal:'1', map:[0:'up', 1:'right', 2:'down', 3:'left'], title:'<b>Screen Orientation</b>',  description:'Screen orientation'],
            [dp:114, name:'ecoMode',            type:'enum',  dt: '01', rw: 'rw', min:0,     max:1 ,   defVal:'0',  step:1,   scale:1,  map:[0:'off', 1:'on'] ,   unit:'', title:'<b>Eco mode</b>',  description:'Eco mode'],
            // or Hysteresis  ?
            //{'comfort': tuya.enum(0), 'eco': tuya.enum(1)}
        ],
        supportedThermostatModes: ['off', 'heat', 'auto', 'emergency heat', 'eco'],
        refresh: ['pollTuya'],
        deviceJoinName: 'TUYA_HY367_HY368_HY369_TRV',
        configuration : [:]
    ],


// ---------------------------------------------------- thermostats ----------------------------------------------------

    'TUYA/AVATTO_ME81H_THERMOSTAT'   : [       // https://github.com/Koenkk/zigbee-herdsman-converters/blob/3ec951e4c16310be29cec0473030827fb9a5bc23/src/devices/moes.ts#L97-L165 
            description   : 'Tuya/Avatto/Moes ME81H Thermostat',
            device        : [models: ['TS0601'], type: 'Thermostat', powerSource: 'ac', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [childLock:'40', minHeatingSetpoint:'26', maxHeatingSetpoint:'19', sensor:'43', antiFreeze:'103', hysteresis:'106', temperatureCalibration:'105'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ye5jkfsb", controllerType: "ZGB", deviceJoinName: 'AVATTO ME81H Thermostat'],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ztvwu4nk", deviceJoinName: 'Tuya Thermostat'],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_5toc8efa", deviceJoinName: 'Tuya Thermostat'],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_5toc8efa", deviceJoinName: 'Tuya Thermostat'],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_aoclfnxz", deviceJoinName: 'Tuya Thermostat'],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_u9bfwha0", deviceJoinName: 'Tuya Thermostat'],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_u9bfwha0", deviceJoinName: 'Tuya Thermostat']
            ],
            commands      : [/*"pollTuya":"pollTuya","autoPollThermostat":"autoPollThermostat",*/ 'sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [   // https://github.com/Koenkk/zigbee-herdsman-converters/blob/a4a2ac2e382508fc911d721edbb9d4fa308a68bb/lib/tuya.js#L326-L339
                [dp:1,   name:'systemMode',         type:'enum',            rw: 'rw', defVal:'1', map:[0: 'off', 1: 'on'],  title:'<b>Thermostat Switch</b>',  description:'Thermostat Switch (system mode)'],
                [dp:2,   name:'thermostatMode',     type:'enum',            rw: 'rw', defVal:'0', map:[0:'heat', 1:'auto'], description:'Thermostat Working Mode'],                             // moesHold: 2,
                [dp:16,  name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,    max:99.0, defVal:20.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],    // moesHeatingSetpoint: 16,
                [dp:19,  name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:30.0,   max:95.0, defVal:40.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Maximum Heating Setpoint</b>',      description:'Maximum heating setpoint'],    // moesMaxTemp: 18, 
                [dp:23,  name:'temperatureScale',   type:'enum',            rw: 'rw', defVal:'0', map:[0:'Celsius', 1:'Fahrenheit'] ,  unit:'', description:'Thermostat Operating State(working state)'], // temperature scale              temp_unit_convert	Enum	{  "range": [    "c",    "f"  ] }
                [dp:24,  name:'temperature',        type:'decimal',         rw: 'ro', defVal:20.0, scale:1 , unit:'°C',  description:'Temperature'],                                                                // moesLocalTemp: 24,
                [dp:26,  name:'minHeatingSetpoint', type:'decimal',         rw: 'rw', min:5.0,    max:20.0, defVal:10.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Minimum Heating Setpoint</b>',      description:'Minimum heating setpoint'],
                [dp:27,  name:'temperatureCorrection',     type:'decimal',  rw: 'rw', min:-9.0,   max:9.0,  defVal:0.0,  step:1,   scale:1,  unit:'°C',  title:'<b>Temperature Correction</b>',  description:'Temperature correction'],            // moesTempCalibration: 27,
                [dp:36,  name:'thermostatOperatingState',  type:'enum',     rw: 'rw', defVal:'0', map:[0:'heating', 1:'idle'] ,  unit:'', description:'Thermostat Operating State(working state)'],      // state of the valve     valve_state	Enum	{  "range": [    "open",    "close"  ] }
                [dp:39,  name:'reset',              type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Reset</b>',  description:'Reset'],
                [dp:40,  name:'childLock',          type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Child Lock</b>',  description:'Child lock'],              // moesChildLock: 40,
                [dp:43,  name:'sensor',             type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'internal', 1:'external', 2:'both'], title:'<b>Sensor Selection</b>',  description:'Sensor Selection'],              // sensor_choose	Enum	{  "range": [    "in",    "out"  ] }
                [dp:45,  name:'faultAlarm',         type:'enum',            rw: 'ro', defVal:'0', map:[0:'e1', 1:'e2', 2:'e3'], title:'<b>Fault Alarm Selection</b>',  description:'Fault alarm'],              // fault alarm  Bitmap	{  "label": [    "e1",    "e2",    "e3"  ] } // etopErrorStatus: 13,
                [dp:101, name:'floorTemperature',   type:'decimal',         rw: 'ro', min:0.0,    max:99.0, defVal:20.0, step:0.5, scale:1 , unit:'°C',  description:'Temperature'],                                                                // moesLocalTemp: 24,
                [dp:103, name:'antiFreeze',         type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Anti-Freeze</b>',  description:'Anti-Freeze support'],  
                [dp:104, name:'programmingMode',    type:'enum',            rw: 'ro', defVal:'0', map:[0:'off', 1:'two weekends', 2:'one weeked', 3:'no weekends'], title:'<b>Programming Mode</b>',  description:'Programming Mode'], 
                [dp:105, name:'temperatureCalibration',  type:'decimal',    rw: 'rw', min:-9.0,   max:9.0,  defVal:-2.0, step:1,   scale:1,  unit:'°C',  title:'<b>Temperature Calibration</b>',  description:'Temperature calibration'],
                [dp:106, name:'hysteresis',         type:'decimal',         rw: 'rw', min:1.0,    max:5.0,  defVal:1.0,  step:1.0, scale:1,  title: '<b>Hysteresis</b>', description:'hysteresis']                                       // moesDeadZoneTemp: 20,
            ],
            supportedThermostatModes: ['off', 'heat', 'auto'],
            refresh: ['pollTuya'],
            deviceJoinName: 'Tuya/Avatto/Moes ME81H Thermostat',
            configuration : [:]
    ],

    // TODO - AVATTO (ZWT100) WT198/ZWT100-BH - https://github.com/Koenkk/zigbee-herdsman-converters/blob/34de76fe3d1ffb2aa6dbcd712ceb5178f38712f9/src/devices/tuya.ts#L4338C51-L4338C67    https://www.aliexpress.com/i/3256802774365245.html 
    'TUYA/MOES_BHT-002_THERMOSTAT'   : [        // probably also BSEED ? TODO - check!
            description   : 'Tuya/Moes BHT-002-GCLZB Thermostat',
            device        : [models: ['TS0601'], type: 'Thermostat', powerSource: 'ac', isSleepy:false],    //  model: 'BHT-002-GCLZB' 
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [childLock:'40', minHeatingSetpoint:'26', maxHeatingSetpoint:'19', sensor:'43', antiFreeze:'103', hysteresis:'106', temperatureCalibration:'105'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_aoclfnxz", controllerType: "ZGB", deviceJoinName: 'Moes BHT series Thermostat'],
            ],
            commands      : ['sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [
                [dp:1,   name:'systemMode',         type:'enum',            rw: 'rw', defVal:'1', map:[0: 'off', 1: 'on'],  title:'<b>Thermostat Switch</b>',  description:'Thermostat Switch (system mode)'],
                [dp:2,   name:'thermostatMode',     type:'enum',            rw: 'rw', defVal:'0', map:[0:'heat', 1:'auto', 2:'auto+manual'], description:'Thermostat Working Mode'],                             // 2:auto w/ temporary changed setpoint
                // check for dp:3 !!!! TODO
                // check for dp:13 !!!! TODO
                [dp:16,  name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,    max:99.0, defVal:20.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
                [dp:19,  name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:30.0,   max:95.0, defVal:40.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Maximum Heating Setpoint</b>',      description:'Maximum heating setpoint'],
                // check for DP:20 !!!! TODO
                [dp:23,  name:'temperatureScale',   type:'enum',            rw: 'rw', defVal:'0', map:[0:'Celsius', 1:'Fahrenheit'] ,  unit:'', description:'Thermostat Operating State(working state)'],
                [dp:24,  name:'temperature',        type:'decimal',         rw: 'ro', defVal:20.0, scale:10 , unit:'°C',  description:'Temperature'],   // scale 10 !
                [dp:26,  name:'minHeatingSetpoint', type:'decimal',         rw: 'rw', min:5.0,    max:20.0, defVal:10.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Minimum Heating Setpoint</b>',      description:'Minimum heating setpoint'],
                [dp:27,  name:'temperatureCorrection',     type:'decimal',  rw: 'rw', min:-9.0,   max:9.0,  defVal:0.0,  step:1,   scale:1,  unit:'°C',  title:'<b>Temperature Correction</b>',  description:'Temperature correction'],
                // check for DP:30 !!!! TODO
                [dp:36,  name:'thermostatOperatingState',  type:'enum',     rw: 'rw', defVal:'0', map:[0:'heating', 1:'idle'] ,  unit:'', description:'Thermostat Operating State(working state)'],
                [dp:39,  name:'reset',              type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Reset</b>',  description:'Reset'],
                [dp:40,  name:'childLock',          type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Child Lock</b>',  description:'Child lock'],
                [dp:43,  name:'sensor',             type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'internal', 1:'external', 2:'both'], title:'<b>Sensor Selection</b>',  description:'Sensor Selection'],
                [dp:45,  name:'faultAlarm',         type:'enum',            rw: 'ro', defVal:'0', map:[0:'e1', 1:'e2', 2:'e3'], title:'<b>Fault Alarm Selection</b>',  description:'Fault alarm'],
                [dp:101, name:'floorTemperature',   type:'decimal',         rw: 'ro', min:0.0,    max:99.0, defVal:20.0, step:0.5, scale:1 , unit:'°C',  description:'Temperature'],
                // check for DP:102 !!!! TODO
                [dp:103, name:'antiFreeze',         type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Anti-Freeze</b>',  description:'Anti-Freeze support'],  
                [dp:104, name:'programmingMode',    type:'enum',            rw: 'ro', defVal:'0', map:[0:'off', 1:'two weekends', 2:'one weeked', 3:'no weekends'], title:'<b>Programming Mode</b>',  description:'Programming Mode'], 
                [dp:105, name:'temperatureCalibration',  type:'decimal',    rw: 'rw', min:-9.0,   max:9.0,  defVal:-2.0, step:1,   scale:1,  unit:'°C',  title:'<b>Temperature Calibration</b>',  description:'Temperature calibration'],
                [dp:106, name:'hysteresis',         type:'decimal',         rw: 'rw', min:1.0,    max:5.0,  defVal:1.0,  step:1.0, scale:1,  title: '<b>Hysteresis</b>', description:'hysteresis']
            ],
            supportedThermostatModes: ['off', 'heat', 'auto'],
            refresh: ['pollTuya'],
            deviceJoinName: 'Moes Thermostat',
            configuration : [:]
    ],

    'TUYA/BEOK_THERMOSTAT'   : [
            description   : 'Tuya/Beok X5H-GB-B Thermostat',
            device        : [models: ['TS0601'], type: 'Thermostat', powerSource: 'ac', isSleepy:false],    //  model: 'BHT-002-GCLZB' 
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [sound:'7', childLock:'40', /*maxHeatingSetpoint:'19',*/ sensor:'43', antiFreeze:'10', hysteresis:'101', temperatureCalibration:'27', brightness:'104', outputReverse:'103', protectionTemperature:'102'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_2ekuz3dz", controllerType: "ZGB", deviceJoinName: 'Beok X5H-GB-B Thermostat'],
            ],
            commands      : ['sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [
                [dp:1,   name:'systemMode',         type:'enum',  dt: '01',  rw: 'rw', defVal:'1', map:[0: 'off', 1: 'on'],  title:'<b>Thermostat Switch</b>',  description:'Thermostat Switch (system mode)'],
               // [dp:1,   name:'systemMode',         type:'enum',            rw: 'rw', defVal:'1', map:[0: 'on', 1: 'off'],  title:'<b>Thermostat Switch</b>',  description:'Thermostat Switch (system mode)'],
                [dp:2,   name:'thermostatMode',     type:'enum',            rw: 'rw', defVal:'0', map:[0:'heat', 1:'auto', 2:'auto+manual'], description:'Thermostat Working Mode'],                             // 2:auto w/ temporary changed setpoint
                [dp:3,   name:'thermostatOperatingState',  type:'enum',     rw: 'rw', defVal:'0', map:[0:'idle', 1:'heating'] ,  unit:'', description:'Thermostat Operating State(working state)'],
                [dp:7,   name:'sound',   type:'enum',            rw: 'rw', defVal:'0', map:[0:'off', 1:'on'] ,  unit:'', title: '<b>Sound</b>', description:'Sound on/off'],
                [dp:10,  name:'antiFreeze',         type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Anti-Freeze</b>',  description:'Anti-Freeze support'],  
                [dp:16,  name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:0.5,    max:60.0, defVal:20.0, step:0.5, scale:10,  unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],   // scale : x 10 !!
                [dp:19,  name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:35.0,   max:95.0, defVal:40.0, step:0.5, scale:10,  unit:'°C',  title: '<b>Maximum Heating Setpoint</b>',      description:'Maximum heating setpoint'],
                // ^^^^^^^^^^^^^^^^^^ sets the heatingSetpoint !!!!!!!!!!!! ^^^^^^^^^ TODO
                [dp:24,  name:'temperature',        type:'decimal',         rw: 'ro', defVal:20.0, scale:10 , unit:'°C',  description:'Temperature'],   // scale 10 !
                [dp:27,  name:'temperatureCalibration',     type:'decimal',  rw: 'rw', min:-9.9,   max:9.9,  defVal:-2.0,  step:1,   scale:10,  unit:'°C',  title:'<b>Temperature Correction</b>',  description:'Temperature correction'],
                [dp:30,  name:'weeklyProgram',      type:'number',          rw: 'ro',  description:'weeklyProgram'],
                [dp:31,  name:'programmingMode',    type:'enum',            rw: 'ro', defVal:'0', map:[0:'off', 1:'5_2 (two weekends)', 2:'6_1 (one weeked)', 3:'7 (no weekends)'], title:'<b>Programming Mode</b>',  description:'Programming Mode'], 
                [dp:39,  name:'reset',              type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Reset</b>',  description:'Reset'],
                [dp:40,  name:'childLock',          type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Child Lock</b>',  description:'Child lock'],
                [dp:43,  name:'sensor',             type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'internal', 1:'external', 2:'both'], title:'<b>Sensor Selection</b>',  description:'Sensor Selection'],  // only in/out for BEOK
                [dp:45,  name:'faultAlarm',         type:'enum',            rw: 'ro', defVal:'0', map:[0:'e1', 1:'e2', 2:'e3'], title:'<b>Fault Alarm Selection</b>',  description:'Fault alarm'],
                [dp:101, name:'hysteresis',         type:'decimal',         rw: 'rw', min:1.0,    max:9.5,  defVal:1.0,  step:0.5, scale:10,  title: '<b>Hysteresis</b>', description:'hysteresis'],
                [dp:102, name:'protectionTemperature', type:'decimal',      rw: 'rw', min:20.0,   max:80.0, defVal:70.0, step:1.0, scale:1,  unit:'°C',  title: '<b>Protection Temperature Limit</b>', description:'Protection Temperature Limit'],
                [dp:103, name:'outputReverse',      type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Output Reverse</b>',  description:'Output reverse'],  
                [dp:104, name:'brightness',         type:'enum',            rw: 'rw', defVal:'2', min:0, max:3, map:[0:'off', 1:'low', 2:'medium', 3:'high'], title:'<b>LCD Brightness</b>',  description:'LCD brightness']
            ],
            supportedThermostatModes: ['off', 'heat', 'auto'],
            refresh: ['pollTuya'],
            deviceJoinName: 'Beok X5H-GB-B Thermostat',     // pairing - hold the Up button for 5 seconds while the thermostat is off.
            configuration : [:]
    ],

    'AVATTO_ZWT07_BATTERY_THERMOSTAT'   : [       // https://github.com/Koenkk/zigbee-herdsman-converters/blob/c70ee5c41c0c1a4d9e36cf5527d8079e502edf98/src/devices/tuya.ts#L4231-L4274
            description   : 'Avatto ZWT07 Battery Thermostat',
            device        : [models: ['TS0601'], type: 'Thermostat', powerSource: 'ac', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [childLock:'40', minHeatingSetpoint:'18', maxHeatingSetpoint:'19', sensor:'43', antiFreeze:'10', hysteresis:'106', temperatureCalibration:'109'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_g9a3awaj", controllerType: "ZGB", deviceJoinName: 'Avatto ZWT07 Battery Thermostat'],
            ],
            commands      : ['sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [
                [dp:1,   name:'systemMode',         type:'enum',  dt: '01', rw: 'rw', defVal:'1', map:[0: 'off', 1: 'on'],  title:'<b>Thermostat Switch</b>',  description:'Thermostat Switch (system mode)'],  //'system_mode', 'switch'
                [dp:2,   name:'thermostatMode',     type:'enum',            rw: 'rw', defVal:'0', map:[1:'heat', 0:'auto'], description:'Thermostat Working Mode'],                             // 'preset', 'mode'
                [dp:9,   name:'battery',            type:'number',          rw: 'ro', min:0,     max:100,  defVal:100,  step:1,   scale:1,  unit:'%',          description:'Battery level'],
                [dp:10,  name:'antiFreeze',         type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Anti-Freeze</b>',  description:'Anti-Freeze / Frost Protection'],     // 'frost_protection'
                [dp:16,  name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,    max:95.0, defVal:20.0, step:0.5, scale:10,  unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],    // moesHeatingSetpoint: 16,
                [dp:18,  name:'minHeatingSetpoint', type:'decimal',         rw: 'rw', min:5.0,    max:20.0, defVal:5.0,  step:1.0, scale:10,  unit:'°C',  title: '<b>Minimum Heating Setpoint</b>',      description:'Minimum heating setpoint'],
                [dp:19,  name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:21.0,   max:95.0, defVal:50.0, step:1.0, scale:10,  unit:'°C',  title: '<b>Maximum Heating Setpoint</b>',      description:'Maximum heating setpoint'],    // moesMaxTemp: 18, 
                [dp:24,  name:'temperature',        type:'decimal',         rw: 'ro', defVal:20.0, scale:10 , unit:'°C',  description:'Temperature'],                                                    // 'local_temperature'
                [dp:31,  name:'programmingMode',    type:'enum',            rw: 'ro', defVal:'0', map:[0:'5/2', 1:'6/1', 2:'7/0'], title:'<b>Programming Mode</b>',  description:'Programming Mode'],    // workday settings
                [dp:36,  name:'thermostatOperatingState',  type:'enum',     rw: 'rw', defVal:'0', map:[1:'heating', 0:'idle'] ,  unit:'', description:'Thermostat Operating State(working state)'],      //  'running_state' valve state?
                [dp:40,  name:'childLock',          type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Child Lock</b>',  description:'Child lock'],              // moesChildLock: 40,
                [dp:45,  name:'faultAlarm',         type:'enum',            rw: 'ro', defVal:'0', map:[0:'e1', 1:'e2', 2:'e3'], title:'<b>Fault Alarm Selection</b>',  description:'Fault alarm'],              // fault alarm  Bitmap	{  "label": [    "e1",    "e2",    "e3"  ] } // etopErrorStatus: 13,
                [dp:67,  name:'heatingSchedule',    type:'number',          rw: 'ro', description:'Heating Schedule'],
                [dp:105, name:'model',              type:'number',          rw: 'ro', description:'Model'],     // mfg_model	String	
                [dp:109, name:'temperatureCalibration',  type:'decimal',    rw: 'rw', min:-9.9,   max:9.9,  defVal:0.0, step:1,   scale:10,  unit:'°C',  title:'<b>Temperature Calibration</b>',  description:'Temperature calibration'],
            ],
            supportedThermostatModes: ['off', 'heat', 'auto'],
            refresh: ['pollTuya'],
            deviceJoinName: 'Avatto ZWT07 Battery Thermostat',
            configuration : [:]
    ],

    'AVATTO_ZWT198_ZWT100-BH_THERMOSTAT'   : [       // https://github.com/Koenkk/zigbee-herdsman-converters/blob/8705f77d83dcfa42e9ccc3ad284070abcf7cff00/src/devices/tuya.ts#L6331
            description   : 'Avatto ZWT198/ZWT100-BH Thermostat',
            device        : [models: ['TS0601'], type: 'Thermostat', powerSource: 'ac', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : [childLock:'9', minHeatingSetpoint:'16', maxHeatingSetpoint:'15', sensor:'106', antiFreeze:'102', hysteresis:'107', temperatureCalibration:'19', brightness:'110'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_xnbkhhdr", controllerType: "ZGB", deviceJoinName: 'Avatto ZWT198/ZWT100-BH Thermostat'],   // https://community.hubitat.com/t/avatto-thermostat-driver-note-working-cirrectly/145261?u=kkossev
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_lzriup1j", controllerType: "ZGB", deviceJoinName: 'Avatto ZWT198/ZWT100-BH Thermostat'],   // https://community.hubitat.com/t/avatto-thermostat-driver-note-working-cirrectly/145261?u=kkossev
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_viy9ihs7", controllerType: "ZGB", deviceJoinName: 'Avatto ZWT198/ZWT100-BH Thermostat'],   // https://community.hubitat.com/t/avatto-thermostat-driver-note-working-cirrectly/145261?u=kkossev
            ],
            commands      : ['sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [
                [dp:1,   name:'systemMode',         type:'enum',  dt: '01', rw: 'rw', defVal:'1', map:[0: 'off', 1: 'on'],  title:'<b>Thermostat Switch</b>',  description:'Thermostat Switch (system mode)'],  //'system_mode', 'switch'
                [dp:2,   name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,    max:35.0, defVal:20.0, step:0.5, scale:10,  unit:'°C',  title: '<b>Current Heating Setpoint</b>',      description:'Current heating setpoint'],
                [dp:3,   name:'temperature',        type:'decimal',         rw: 'ro', defVal:20.0, scale:10 , unit:'°C',  description:'Temperature'],                                                    // 'local_temperature'
                [dp:4,   name:'thermostatMode',     type:'enum',            rw: 'rw', defVal:'0', map:[0:'heat', 1:'auto', 2:'auto+manual'], description:'Thermostat Working Mode'],                     // 'preset', 'mode'
                // TODO - preProc for  _TZE200_viy9ihs7 // https://github.com/Koenkk/zigbee2mqtt/issues/21353#issuecomment-1938328429
                [dp:9,   name:'childLock',          type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Child Lock</b>',  description:'Child lock'],
                [dp:11,  name:'faultAlarm',         type:'enum',            rw: 'ro', defVal:'0', map:[0:'e1', 1:'e2', 2:'e3'], title:'<b>Fault Alarm Selection</b>',  description:'Fault alarm'],
                [dp:15,  name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:15.0,   max:90.0, defVal:60.0, step:0.5, scale:10,  unit:'°C',  title:'<b>Maximum Heating Setpoint</b>',      description:'Maximum heating setpoint'],
                [dp:16,  name:'minHeatingSetpoint', type:'decimal',         rw: 'rw', min:5.0,    max:15.0, defVal:10.0, step:0.5, scale:10,  unit:'°C',  title:'<b>Minimum Heating Setpoint</b>',      description:'Minimum heating setpoint'],    // 'dp16 is listed in Tuya, but no communication from device'
                [dp:19,  name:'temperatureCalibration',  type:'decimal',    rw: 'rw', min:-9.9,   max:9.9,  defVal:0.0, step:1,    scale:10,  unit:'°C',  title:'<b>Temperature Calibration</b>',  description:'Temperature calibration'],
                [dp:101, name:'thermostatOperatingState',  type:'enum',     rw: 'rw', defVal:'0', map:[1:'heating', 0:'idle'] ,  unit:'', description:'Thermostat Operating State(working state)'],      //  'running_state' valve state?
                [dp:102, name:'antiFreeze',         type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Anti-Freeze</b>',  description:'Anti-Freeze / Frost Protection'],     // 'frost_protection'
                [dp:103, name:'reset',              type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Factory Reset</b>',  description:'Full factory reset, use with caution!'],
                [dp:104, name:'workdaySet',         type:'enum',            rw: 'ro',             map:[0:'disabled', 1:'6-1', 2:'5-2', 3:'7'], description:'Workday Set'],
                [dp:105, name:'unknown105',         type:'enum',  dt: '01', rw: 'ro', defVal:'0', map:[0:'off', 1:'on'], description:'Unknown 105'],
                [dp:106, name:'sensor',             type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'internal', 1:'external', 2:'both'], title:'<b>Sensor Selection</b>',  description:'Sensor Selection'],  // only in/out for BEOK
                [dp:107, name:'hysteresis',         type:'decimal',         rw: 'rw', min:1.0,    max:9.5,  defVal:1.0,  step:0.5, scale:10,  title: '<b>Hysteresis</b>', description:'hysteresis'],
                [dp:109, name:'heatingSchedule',    type:'number',          rw: 'ro', description:'Heating Schedule'],
                [dp:110, name:'brightness',         type:'enum',            rw: 'rw', defVal:'2', map:[0:'off', 1:'low', 2:'medium', 3:'high'], title:'<b>LCD Brightness</b>',  description:'LCD brightness'],
                [dp:111, name:'unknown111',         type:'enum',  dt: '01', rw: 'ro', defVal:'0', map:[0:'off', 1:'on'], description:'Unknown 111'],
            ],
            supportedThermostatModes: ['off', 'heat', 'auto'],
            refresh: ['pollTuya'],
            deviceJoinName: 'Avatto ZWT198/ZWT100-BH Thermostat',
            configuration : [:]
    ],
]

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

//
// called from updated() in the main code
void customUpdated() {
    //ArrayList<String> cmds = []
    logDebug 'customUpdated: ...'
    //if (setting?.advancedOptions == true) {
        logInfo "Force manual is <b>${settings?.forceManual}</b>; Resend failed is <b>${settings?.resendFailed}</b>"
        if (settings?.resendFailed == true) {
            logInfo 'resendFailed is enabled!'
            runEvery1Minute('receiveCheck')
        }
        else {
            logInfo 'resendFailed is disabled'
            unschedule('receiveCheck')
        }
    //}
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

List<String> customConfigure() {
    List<String> cmds = []
    logDebug "customConfigure() : ${cmds} (not implemented!)"
    return cmds
}


// called from initializeDevice in the commonLib code
List<String> customInitializeDevice() {
    List<String> cmds = []
    logDebug "customInitializeDevice: nothing to initialize for device group ${getDeviceProfile()}"
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
    // for Tuya TRVs and thermostats - change the default polling method to 0 'disabled'
    if (fullInit || settings?.temperaturePollingInterval == null) { device.updateSetting('temperaturePollingInterval', [value: '0', type: 'enum']) }
    if (fullInit || settings?.resendFailed == null) { device.updateSetting('resendFailed', [value: 'false', type: 'bool']) }
    if (fullInit || settings?.forceManual == null)  { device.updateSetting('forceManual',  [value: 'false', type: 'bool']) }
}

// called from initializeVars() in the main code ...
void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents(${fullInit})"
    thermostatInitEvents(fullInit)
    if (settings?.resendFailed == true) {
        logInfo 'resendFailed is enabled!'
        runEvery1Minute('receiveCheck')    // KK: check
    }
    else {
        logInfo 'resendFailed is disabled'
        unschedule('receiveCheck')
    }
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
            // added 02/16/2025
            if (settings?.forceManual == true) {
                if (valueScaled == 'auto') {
                    logWarn "customProcessDeviceProfileEvent: forceManual is <b>enabled</b>! Changing the thermostatMode from auto to heat"
                    runIn(1, 'heat')
                }
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

void customParseTuyaCluster(final Map descMap) {
    boolean isDuplicated = false
/*    
    int dp = zigbee.convertHexToInt(descMap?.data[2])
    int fncmd = getTuyaAttributeValue(descMap?.data, 0) // PATCH! zero-based index
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response
        setLastRx( NOT_SET, NOT_SET)    // -1
    }
    else if (descMap?.clusterInt == CLUSTER_TUYA && dp != 24) {    // Tuya set time command
        if (checkIfIsDuplicated( dp, fncmd )) {
            logDebug "<b>(duplicate)</b>  <b>dp=${dp}</b> fncmd=${fncmd} command=${descMap?.command} data = ${descMap?.data}"
            state.stats['dupeCtr'] = (state.stats['dupeCtr'] ?: 0) + 1
            isDuplicated = true
        }
        else {
            logDebug "${device.displayName} dp_id=${dp_id} <b>dp=${dp}</b> fncmd=${fncmd}" 
            setLastRx( dp, fncmd)
        }

    }
    */
    if (isDuplicated == false) {
        standardParseTuyaCluster(descMap)   // defined in commonLib
    }
}


//  receiveCheck() is unconditionally scheduled Every1Minute from installed() ..
void receiveCheck() {
    logDebug "receiveCheck() called"
    modeReceiveCheck()
    setpointReceiveCheck()
    setBrightnessReceiveCheck()
}


// NOT ACTIVATED !!! Needs a dedicated setBrightness() command ?
// Brightness checking is also called every 1 minute from receiveCheck()
void setBrightnessReceiveCheck() {
    if (settings?.resendFailed == false ) { return }
    if (state.lastTx.isSetBrightnessReq == false) { return }

    if (state.lastTx.setBrightness != NOT_SET && ((state.lastTx.setBrightness as String) != (state.lastRx.setBrightness as String))) {
        state.lastTx.setBrightnessRetries = (state.lastTx.setBrightnessRetries ?: 0) + 1
        if (state.lastTx.setBrightnessRetries < MaxRetries) {
            logWarn "setBrightnessReceiveCheck(${state.lastTx.setBrightness}) <b>failed<b/> (last received is still ${state.lastRx.setBrightness})"
            logDebug "resending setBrightness command : ${state.lastTx.setBrightness} (retry# ${state.lastTx.setBrightnessRetries}) of ${MaxRetries}"
            state.stats.txFailCtr = (state.stats.txFailCtr ?: 0) + 1
            //setBrightness(lastTxMap.setBrightness)
            setPar('brightness', state.lastTx.setBrightness)
        }
        else {
            logWarn "setBrightnessReceiveCheck(${state.lastTx.setBrightness}) <b>giving up retrying<b/>"
            state.lastTx.isSetBrightnessReq = false
            state.lastTx.setBrightnessRetries = 0
        }
    }
    else {
        logDebug "setBrightnessReceiveCheck brightness was changed successfuly to (${state.lastTx.setBrightness}). No need for further checks."
        state.lastTx.setBrightness = NOT_SET
        state.lastTx.isSetBrightnessReq = false
    }
}


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
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDouble, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/commonLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-commonLib', // library marker kkossev.commonLib, line 4
    version: '3.4.0' // library marker kkossev.commonLib, line 5
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
  * ver. 3.3.1  2024-07-06 kkossev  - removed isFingerbot() dependancy; added FC03 cluster (Frient); removed noDef from the linter; added customParseIasMessage and standardParseIasMessage; powerSource set to unknown on initialize(); add 0x0007 onOffConfiguration cluster'; // library marker kkossev.commonLib, line 40
  * ver. 3.3.2  2024-07-12 kkossev  - added PollControl (0x0020) cluster; ping for SONOFF // library marker kkossev.commonLib, line 41
  * ver. 3.3.3  2024-09-15 kkossev  - added queryAllTuyaDP(); 2 minutes healthCheck option; // library marker kkossev.commonLib, line 42
  * ver. 3.3.4  2025-01-29 kkossev  - 'LOAD ALL DEFAULTS' is the default Configure command. // library marker kkossev.commonLib, line 43
  * ver. 3.3.5  2025-03-05 kkossev  - getTuyaAttributeValue made public; fixed checkDriverVersion bug on hub reboot. // library marker kkossev.commonLib, line 44
  * ver. 3.4.0  2025-03-23 kkossev  - healthCheck by pinging the device; updateRxStats() replaced with inline code; added state.lastRx.timeStamp; added activeEndpoints() handler call; documentation improvements // library marker kkossev.commonLib, line 45
  * ver. 3.5.0  2025-04-08 kkossev  - urgent fix for java.lang.CloneNotSupportedException // library marker kkossev.commonLib, line 46
  * // library marker kkossev.commonLib, line 47
  *                                   TODO: add GetInfo (endpoints list) command (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 48
  *                                   TODO: make the configure() without parameter smart - analyze the State variables and call delete states.... call ActiveAndpoints() or/amd initialize() or/and configure() // library marker kkossev.commonLib, line 49
  *                                   TODO: check - offlineCtr is not increasing? (ZBMicro); // library marker kkossev.commonLib, line 50
  *                                   TODO: check deviceCommandTimeout() // library marker kkossev.commonLib, line 51
  *                                   TODO: when device rejoins the network, read the battery percentage again (probably in custom handler, not for all devices) // library marker kkossev.commonLib, line 52
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 53
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 54
  *                                   TODO: MOVE ZDO counters to health state? // library marker kkossev.commonLib, line 55
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 56
  *                                   TODO: Versions of the main module + included libraries (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 57
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 58
  * // library marker kkossev.commonLib, line 59
*/ // library marker kkossev.commonLib, line 60

String commonLibVersion() { '3.5.0' } // library marker kkossev.commonLib, line 62
String commonLibStamp() { '2025/04/08 8:36 PM' } // library marker kkossev.commonLib, line 63

import groovy.transform.Field // library marker kkossev.commonLib, line 65
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 66
import hubitat.device.Protocol // library marker kkossev.commonLib, line 67
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 68
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 69
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 70
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 71
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 72
import java.math.BigDecimal // library marker kkossev.commonLib, line 73

metadata { // library marker kkossev.commonLib, line 75
        if (_DEBUG) { // library marker kkossev.commonLib, line 76
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 77
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 78
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 79
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 80
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 81
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 82
            ] // library marker kkossev.commonLib, line 83
        } // library marker kkossev.commonLib, line 84

        // common capabilities for all device types // library marker kkossev.commonLib, line 86
        capability 'Configuration' // library marker kkossev.commonLib, line 87
        capability 'Refresh' // library marker kkossev.commonLib, line 88
        capability 'HealthCheck' // library marker kkossev.commonLib, line 89
        capability 'PowerSource'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 90

        // common attributes for all device types // library marker kkossev.commonLib, line 92
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 93
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 94
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 95

        // common commands for all device types // library marker kkossev.commonLib, line 97
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM', constraints: ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 98

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 100
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 101

    preferences { // library marker kkossev.commonLib, line 103
        // txtEnable and logEnable moved to the custom driver settings - copy& paste there ... // library marker kkossev.commonLib, line 104
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 105
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 106

        if (device) { // library marker kkossev.commonLib, line 108
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device...', defaultValue: false // library marker kkossev.commonLib, line 109
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 110
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 111
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 112
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 113
            } // library marker kkossev.commonLib, line 114
        } // library marker kkossev.commonLib, line 115
    } // library marker kkossev.commonLib, line 116
} // library marker kkossev.commonLib, line 117

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 119
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 120
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 121
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 122
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 123
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 124
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 125
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 126
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 127
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 128
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 129

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 131
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 132
] // library marker kkossev.commonLib, line 133
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 134
    defaultValue: 240, options: [2: 'Every 2 Mins', 10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 135
] // library marker kkossev.commonLib, line 136

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 138
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'], // library marker kkossev.commonLib, line 139
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 140
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 141
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 142
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 143
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 144
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 145
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 146
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 147
    '           -             '  : [key:1, function: 'configureHelp'] // library marker kkossev.commonLib, line 148
] // library marker kkossev.commonLib, line 149

public boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 151

/** // library marker kkossev.commonLib, line 153
 * Parse Zigbee message // library marker kkossev.commonLib, line 154
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 155
 */ // library marker kkossev.commonLib, line 156
public void parse(final String description) { // library marker kkossev.commonLib, line 157
    Map stateCopy = state            // .clone() throws java.lang.CloneNotSupportedException in HE platform version 2.4.1.155 ! // library marker kkossev.commonLib, line 158
    checkDriverVersion(stateCopy)    // +1 ms // library marker kkossev.commonLib, line 159
    if (state.stats != null) { state.stats?.rxCtr= (state.stats?.rxCtr ?: 0) + 1 } else { state.stats = [:] }  // updateRxStats(state) // +1 ms // library marker kkossev.commonLib, line 160
    if (state.lastRx != null) { state.lastRx?.timeStamp = unix2formattedDate(now()) } else { state.lastRx = [:] } // library marker kkossev.commonLib, line 161
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 162
    setHealthStatusOnline(state)    // +2 ms // library marker kkossev.commonLib, line 163

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 165
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 166
        if (this.respondsTo('customParseIasMessage')) { customParseIasMessage(description) } // library marker kkossev.commonLib, line 167
        else if (this.respondsTo('standardParseIasMessage')) { standardParseIasMessage(description) } // library marker kkossev.commonLib, line 168
        else if (this.respondsTo('parseIasMessage')) { parseIasMessage(description) } // library marker kkossev.commonLib, line 169
        else { logDebug "ignored IAS zone status (no IAS parser) description: $description" } // library marker kkossev.commonLib, line 170
        return // library marker kkossev.commonLib, line 171
    } // library marker kkossev.commonLib, line 172
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 173
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 174
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 175
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 176
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 177
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 178
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 179
        return // library marker kkossev.commonLib, line 180
    } // library marker kkossev.commonLib, line 181

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 183
        return // library marker kkossev.commonLib, line 184
    } // library marker kkossev.commonLib, line 185
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 186

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 188
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 189

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 191
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 192
        return // library marker kkossev.commonLib, line 193
    } // library marker kkossev.commonLib, line 194
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 195
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 196
        return // library marker kkossev.commonLib, line 197
    } // library marker kkossev.commonLib, line 198
    // // library marker kkossev.commonLib, line 199
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 200
    // // library marker kkossev.commonLib, line 201
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 202
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 203
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 204
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 205
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 206
            } // library marker kkossev.commonLib, line 207
            break // library marker kkossev.commonLib, line 208
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 209
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 210
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 211
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 212
            } // library marker kkossev.commonLib, line 213
            break // library marker kkossev.commonLib, line 214
        default: // library marker kkossev.commonLib, line 215
            if (settings.logEnable) { // library marker kkossev.commonLib, line 216
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 217
            } // library marker kkossev.commonLib, line 218
            break // library marker kkossev.commonLib, line 219
    } // library marker kkossev.commonLib, line 220
} // library marker kkossev.commonLib, line 221

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 223
    0x0000: 'Basic',             0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x0006: 'OnOff',           0x0007:'onOffConfiguration',      0x0008: 'LevelControl',  // library marker kkossev.commonLib, line 224
    0x000C: 'AnalogInput',       0x0012: 'MultistateInput',  0x0020: 'PollControl',      0x0102: 'WindowCovering',   0x0201: 'Thermostat',  0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 225
    0x0400: 'Illuminance',       0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 226
    0x0B04: 'ElectricalMeasure', 0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC03: 'FC03',            0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 227
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 228
] // library marker kkossev.commonLib, line 229

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 231
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 232
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 233
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 234
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 235
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 236
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 237
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
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 255
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

// not used? // library marker kkossev.commonLib, line 281
public boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 282
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 283
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 284
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 285
    } // library marker kkossev.commonLib, line 286
    return false // library marker kkossev.commonLib, line 287
} // library marker kkossev.commonLib, line 288

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 290
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 291
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 292
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 293
] // library marker kkossev.commonLib, line 294

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 296
private void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 297
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 298
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 299
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 300
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 301
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 302
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 303
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 304
    List<String> cmds = [] // library marker kkossev.commonLib, line 305
    switch (clusterId) { // library marker kkossev.commonLib, line 306
        case 0x0005 : // library marker kkossev.commonLib, line 307
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 308
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 309
            // send the active endpoint response // library marker kkossev.commonLib, line 310
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 311
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 312
            break // library marker kkossev.commonLib, line 313
        case 0x0006 : // library marker kkossev.commonLib, line 314
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 315
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 316
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 317
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 318
            break // library marker kkossev.commonLib, line 319
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 320
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 321
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 322
            break // library marker kkossev.commonLib, line 323
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 324
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 325
            if (this.respondsTo('parseSimpleDescriptorResponse')) { parseSimpleDescriptorResponse(descMap) } // library marker kkossev.commonLib, line 326
            break // library marker kkossev.commonLib, line 327
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 328
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 329
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 330
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 331
            break // library marker kkossev.commonLib, line 332
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 333
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 334
            break // library marker kkossev.commonLib, line 335
        case 0x0002 : // Node Descriptor Request // library marker kkossev.commonLib, line 336
        case 0x0036 : // Permit Joining Request // library marker kkossev.commonLib, line 337
        case 0x8022 : // unbind request // library marker kkossev.commonLib, line 338
        case 0x8034 : // leave response // library marker kkossev.commonLib, line 339
            if (settings?.logEnable) { log.debug "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 340
            break // library marker kkossev.commonLib, line 341
        default : // library marker kkossev.commonLib, line 342
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 343
            break // library marker kkossev.commonLib, line 344
    } // library marker kkossev.commonLib, line 345
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 346
} // library marker kkossev.commonLib, line 347

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 349
private void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 350
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 351
    switch (commandId) { // library marker kkossev.commonLib, line 352
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 353
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 354
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 355
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 356
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 357
        default: // library marker kkossev.commonLib, line 358
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 359
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 360
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 361
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 362
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 363
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 364
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 365
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 366
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 367
            } // library marker kkossev.commonLib, line 368
            break // library marker kkossev.commonLib, line 369
    } // library marker kkossev.commonLib, line 370
} // library marker kkossev.commonLib, line 371

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 373
private void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 374
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 375
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 376
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 377
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 378
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 379
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 380
    } // library marker kkossev.commonLib, line 381
    else { // library marker kkossev.commonLib, line 382
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 383
    } // library marker kkossev.commonLib, line 384
} // library marker kkossev.commonLib, line 385

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 387
private void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 388
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 389
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 390
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 391
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 392
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 393
    } // library marker kkossev.commonLib, line 394
    else { // library marker kkossev.commonLib, line 395
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 396
    } // library marker kkossev.commonLib, line 397
} // library marker kkossev.commonLib, line 398

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 400
private void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 401
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 402
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 403
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 404
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 405
        state.reportingEnabled = true // library marker kkossev.commonLib, line 406
    } // library marker kkossev.commonLib, line 407
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 408
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 409
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 410
    } else { // library marker kkossev.commonLib, line 411
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 412
    } // library marker kkossev.commonLib, line 413
} // library marker kkossev.commonLib, line 414

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 416
private void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 417
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 418
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 419
    if (status == 0) { // library marker kkossev.commonLib, line 420
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 421
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 422
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 423
        int delta = 0 // library marker kkossev.commonLib, line 424
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 425
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 426
        } // library marker kkossev.commonLib, line 427
        else { // library marker kkossev.commonLib, line 428
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 429
        } // library marker kkossev.commonLib, line 430
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 431
    } // library marker kkossev.commonLib, line 432
    else { // library marker kkossev.commonLib, line 433
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 434
    } // library marker kkossev.commonLib, line 435
} // library marker kkossev.commonLib, line 436

private Boolean executeCustomHandler(String handlerName, Object handlerArgs) { // library marker kkossev.commonLib, line 438
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 439
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 440
        return false // library marker kkossev.commonLib, line 441
    } // library marker kkossev.commonLib, line 442
    // execute the customHandler function // library marker kkossev.commonLib, line 443
    Boolean result = false // library marker kkossev.commonLib, line 444
    try { // library marker kkossev.commonLib, line 445
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 446
    } // library marker kkossev.commonLib, line 447
    catch (e) { // library marker kkossev.commonLib, line 448
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 449
        return false // library marker kkossev.commonLib, line 450
    } // library marker kkossev.commonLib, line 451
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 452
    return result // library marker kkossev.commonLib, line 453
} // library marker kkossev.commonLib, line 454

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 456
private void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 457
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 458
    final String commandId = data[0] // library marker kkossev.commonLib, line 459
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 460
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 461
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 462
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 463
    } else { // library marker kkossev.commonLib, line 464
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 465
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 466
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 467
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 468
        } // library marker kkossev.commonLib, line 469
    } // library marker kkossev.commonLib, line 470
} // library marker kkossev.commonLib, line 471

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 473
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 474
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 475
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 476

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 478
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 479
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 480
] // library marker kkossev.commonLib, line 481

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 483
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 484
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 485
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 486
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 487
] // library marker kkossev.commonLib, line 488

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 490
private BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 491
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 492
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 493
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 494
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 495
    return avg // library marker kkossev.commonLib, line 496
} // library marker kkossev.commonLib, line 497

private void handlePingResponse() { // library marker kkossev.commonLib, line 499
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 500
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 501
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 502

    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 504
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 505
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 506
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 507
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 508
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 509
        sendRttEvent() // library marker kkossev.commonLib, line 510
    } // library marker kkossev.commonLib, line 511
    else { // library marker kkossev.commonLib, line 512
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 513
    } // library marker kkossev.commonLib, line 514
    state.states['isPing'] = false // library marker kkossev.commonLib, line 515
} // library marker kkossev.commonLib, line 516

/* // library marker kkossev.commonLib, line 518
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 519
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 520
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 521
*/ // library marker kkossev.commonLib, line 522
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 523

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 525
private void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 526
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 527
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 528
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 529
    boolean isPing = state.states?.isPing ?: false // library marker kkossev.commonLib, line 530
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 531
        case 0x0000: // library marker kkossev.commonLib, line 532
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 533
            break // library marker kkossev.commonLib, line 534
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 535
            if (isPing) { // library marker kkossev.commonLib, line 536
                handlePingResponse() // library marker kkossev.commonLib, line 537
            } // library marker kkossev.commonLib, line 538
            else { // library marker kkossev.commonLib, line 539
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 540
            } // library marker kkossev.commonLib, line 541
            break // library marker kkossev.commonLib, line 542
        case 0x0004: // library marker kkossev.commonLib, line 543
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 544
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 545
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 546
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 547
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 548
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 549
            } // library marker kkossev.commonLib, line 550
            break // library marker kkossev.commonLib, line 551
        case 0x0005: // library marker kkossev.commonLib, line 552
            if (isPing) { // library marker kkossev.commonLib, line 553
                handlePingResponse() // library marker kkossev.commonLib, line 554
            } // library marker kkossev.commonLib, line 555
            else { // library marker kkossev.commonLib, line 556
                logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 557
                // received device model Remote Control N2 // library marker kkossev.commonLib, line 558
                String model = device.getDataValue('model') // library marker kkossev.commonLib, line 559
                if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 560
                    logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 561
                    device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 562
                } // library marker kkossev.commonLib, line 563
            } // library marker kkossev.commonLib, line 564
            break // library marker kkossev.commonLib, line 565
        case 0x0007: // library marker kkossev.commonLib, line 566
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 567
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 568
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 569
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 570
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 571
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 572
            } // library marker kkossev.commonLib, line 573
            break // library marker kkossev.commonLib, line 574
        case 0xFFDF: // library marker kkossev.commonLib, line 575
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 576
            break // library marker kkossev.commonLib, line 577
        case 0xFFE2: // library marker kkossev.commonLib, line 578
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 579
            break // library marker kkossev.commonLib, line 580
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 581
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 582
            break // library marker kkossev.commonLib, line 583
        case 0xFFFE: // library marker kkossev.commonLib, line 584
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 585
            break // library marker kkossev.commonLib, line 586
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 587
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 588
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 589
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 590
            break // library marker kkossev.commonLib, line 591
        default: // library marker kkossev.commonLib, line 592
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 593
            break // library marker kkossev.commonLib, line 594
    } // library marker kkossev.commonLib, line 595
} // library marker kkossev.commonLib, line 596

private void standardParsePollControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 598
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 599
        case 0x0000: logDebug "PollControl cluster: CheckInInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 600
        case 0x0001: logDebug "PollControl cluster: LongPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 601
        case 0x0002: logDebug "PollControl cluster: ShortPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 602
        case 0x0003: logDebug "PollControl cluster: FastPollTimeout = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 603
        case 0x0004: logDebug "PollControl cluster: CheckInIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 604
        case 0x0005: logDebug "PollControl cluster: LongPollIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 605
        case 0x0006: logDebug "PollControl cluster: FastPollTimeoutMax = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 606
        default: logWarn "zigbee received unknown PollControl cluster attribute 0x${descMap.attrId} (value ${descMap.value})" ; break // library marker kkossev.commonLib, line 607
    } // library marker kkossev.commonLib, line 608
} // library marker kkossev.commonLib, line 609

public void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 611
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 612
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 613

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 615
    Map descMap = [:] // library marker kkossev.commonLib, line 616
    try { // library marker kkossev.commonLib, line 617
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 618
    } // library marker kkossev.commonLib, line 619
    catch (e1) { // library marker kkossev.commonLib, line 620
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 621
        // try alternative custom parsing // library marker kkossev.commonLib, line 622
        descMap = [:] // library marker kkossev.commonLib, line 623
        try { // library marker kkossev.commonLib, line 624
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 625
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 626
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 627
            } // library marker kkossev.commonLib, line 628
        } // library marker kkossev.commonLib, line 629
        catch (e2) { // library marker kkossev.commonLib, line 630
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 631
            return [:] // library marker kkossev.commonLib, line 632
        } // library marker kkossev.commonLib, line 633
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 634
    } // library marker kkossev.commonLib, line 635
    return descMap // library marker kkossev.commonLib, line 636
} // library marker kkossev.commonLib, line 637

// return true if the messages is processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 639
// return false if the cluster is not a Tuya cluster // library marker kkossev.commonLib, line 640
private boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 641
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 642
        return false // library marker kkossev.commonLib, line 643
    } // library marker kkossev.commonLib, line 644
    // try to parse ... // library marker kkossev.commonLib, line 645
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 646
    Map descMap = [:] // library marker kkossev.commonLib, line 647
    try { // library marker kkossev.commonLib, line 648
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 649
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 650
    } // library marker kkossev.commonLib, line 651
    catch (e) { // library marker kkossev.commonLib, line 652
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 653
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 654
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 655
        return true // library marker kkossev.commonLib, line 656
    } // library marker kkossev.commonLib, line 657

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 659
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 660
    } // library marker kkossev.commonLib, line 661
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 662
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 663
    } // library marker kkossev.commonLib, line 664
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 665
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 666
    } // library marker kkossev.commonLib, line 667
    else { // library marker kkossev.commonLib, line 668
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 669
        return false // library marker kkossev.commonLib, line 670
    } // library marker kkossev.commonLib, line 671
    return true    // processed // library marker kkossev.commonLib, line 672
} // library marker kkossev.commonLib, line 673

// return true if processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 675
private boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 676
  /* // library marker kkossev.commonLib, line 677
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 678
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 679
        return true // library marker kkossev.commonLib, line 680
    } // library marker kkossev.commonLib, line 681
*/ // library marker kkossev.commonLib, line 682
    Map descMap = [:] // library marker kkossev.commonLib, line 683
    try { // library marker kkossev.commonLib, line 684
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 685
    } // library marker kkossev.commonLib, line 686
    catch (e1) { // library marker kkossev.commonLib, line 687
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 688
        // try alternative custom parsing // library marker kkossev.commonLib, line 689
        descMap = [:] // library marker kkossev.commonLib, line 690
        try { // library marker kkossev.commonLib, line 691
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 692
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 693
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 694
            } // library marker kkossev.commonLib, line 695
        } // library marker kkossev.commonLib, line 696
        catch (e2) { // library marker kkossev.commonLib, line 697
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 698
            return true // library marker kkossev.commonLib, line 699
        } // library marker kkossev.commonLib, line 700
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 701
    } // library marker kkossev.commonLib, line 702
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 703
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 704
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 705
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 706
        return false // library marker kkossev.commonLib, line 707
    } // library marker kkossev.commonLib, line 708
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 709
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 710
    // attribute report received // library marker kkossev.commonLib, line 711
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 712
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 713
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 714
    } // library marker kkossev.commonLib, line 715
    attrData.each { // library marker kkossev.commonLib, line 716
        if (it.status == '86') { // library marker kkossev.commonLib, line 717
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 718
        // TODO - skip parsing? // library marker kkossev.commonLib, line 719
        } // library marker kkossev.commonLib, line 720
        switch (it.cluster) { // library marker kkossev.commonLib, line 721
            case '0000' : // library marker kkossev.commonLib, line 722
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 723
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 724
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 725
                } // library marker kkossev.commonLib, line 726
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 727
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 728
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 729
                } // library marker kkossev.commonLib, line 730
                else { // library marker kkossev.commonLib, line 731
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 732
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 733
                } // library marker kkossev.commonLib, line 734
                break // library marker kkossev.commonLib, line 735
            default : // library marker kkossev.commonLib, line 736
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 737
                break // library marker kkossev.commonLib, line 738
        } // switch // library marker kkossev.commonLib, line 739
    } // for each attribute // library marker kkossev.commonLib, line 740
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 741
} // library marker kkossev.commonLib, line 742

public String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 744
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 745
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 746
} // library marker kkossev.commonLib, line 747

public String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 749
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 750
} // library marker kkossev.commonLib, line 751

/* // library marker kkossev.commonLib, line 753
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 754
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 755
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 756
*/ // library marker kkossev.commonLib, line 757
private static int getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 758
private static int getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 759
private static int getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 760

// Tuya Commands // library marker kkossev.commonLib, line 762
private static int getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 763
private static int getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 764
private static int getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 765
private static int getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 766
private static int getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 767

// tuya DP type // library marker kkossev.commonLib, line 769
private static String getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 770
private static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 771
private static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 772
private static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 773
private static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 774
private static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 775

private void syncTuyaDateTime() { // library marker kkossev.commonLib, line 777
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 778
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 779
    long offset = 0 // library marker kkossev.commonLib, line 780
    int offsetHours = 0 // library marker kkossev.commonLib, line 781
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 782
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 783
    try { // library marker kkossev.commonLib, line 784
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 785
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 786
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 787
    } catch (e) { // library marker kkossev.commonLib, line 788
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 789
    } // library marker kkossev.commonLib, line 790
    // // library marker kkossev.commonLib, line 791
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 792
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 793
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 794
} // library marker kkossev.commonLib, line 795

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 797
public void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 798
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 799
        syncTuyaDateTime() // library marker kkossev.commonLib, line 800
    } // library marker kkossev.commonLib, line 801
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 802
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 803
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 804
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 805
        if (status != '00') { // library marker kkossev.commonLib, line 806
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 807
        } // library marker kkossev.commonLib, line 808
    } // library marker kkossev.commonLib, line 809
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 810
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 811
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 812
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 813
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 814
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 815
            return // library marker kkossev.commonLib, line 816
        } // library marker kkossev.commonLib, line 817
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 818
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 819
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 820
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 821
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 822
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 823
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 824
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 825
            } // library marker kkossev.commonLib, line 826
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 827
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 828
        } // library marker kkossev.commonLib, line 829
    } // library marker kkossev.commonLib, line 830
    else { // library marker kkossev.commonLib, line 831
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 832
    } // library marker kkossev.commonLib, line 833
} // library marker kkossev.commonLib, line 834

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 836
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 837
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 838
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 839
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 840
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 841
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 842
        } // library marker kkossev.commonLib, line 843
    } // library marker kkossev.commonLib, line 844
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 845
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 846
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 847
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 848
        } // library marker kkossev.commonLib, line 849
    } // library marker kkossev.commonLib, line 850
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 851
} // library marker kkossev.commonLib, line 852

public int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 854
    int retValue = 0 // library marker kkossev.commonLib, line 855
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 856
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 857
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 858
        int power = 1 // library marker kkossev.commonLib, line 859
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 860
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 861
            power = power * 256 // library marker kkossev.commonLib, line 862
        } // library marker kkossev.commonLib, line 863
    } // library marker kkossev.commonLib, line 864
    return retValue // library marker kkossev.commonLib, line 865
} // library marker kkossev.commonLib, line 866

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 868

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 870
    List<String> cmds = [] // library marker kkossev.commonLib, line 871
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 872
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 873
    int tuyaCmd // library marker kkossev.commonLib, line 874
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified! // library marker kkossev.commonLib, line 875
    if (this.respondsTo('getDEVICE') && DEVICE?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 876
        tuyaCmd = DEVICE?.device?.tuyaCmd // library marker kkossev.commonLib, line 877
    } // library marker kkossev.commonLib, line 878
    else { // library marker kkossev.commonLib, line 879
        tuyaCmd = tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 880
    } // library marker kkossev.commonLib, line 881
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 882
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 883
    return cmds // library marker kkossev.commonLib, line 884
} // library marker kkossev.commonLib, line 885

private String getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 887

public void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 889
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 890
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 891
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 892
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 893
} // library marker kkossev.commonLib, line 894


public List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 897
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 898
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 899
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 900
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 901
} // library marker kkossev.commonLib, line 902

public List<String> queryAllTuyaDP() { // library marker kkossev.commonLib, line 904
    logTrace 'queryAllTuyaDP()' // library marker kkossev.commonLib, line 905
    List<String> cmds = zigbee.command(0xEF00, 0x03) // library marker kkossev.commonLib, line 906
    return cmds // library marker kkossev.commonLib, line 907
} // library marker kkossev.commonLib, line 908

public void aqaraBlackMagic() { // library marker kkossev.commonLib, line 910
    List<String> cmds = [] // library marker kkossev.commonLib, line 911
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 912
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 913
    } // library marker kkossev.commonLib, line 914
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 915
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 916
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 917
        return // library marker kkossev.commonLib, line 918
    } // library marker kkossev.commonLib, line 919
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 920
} // library marker kkossev.commonLib, line 921

// Invoked from configure() // library marker kkossev.commonLib, line 923
public List<String> initializeDevice() { // library marker kkossev.commonLib, line 924
    List<String> cmds = [] // library marker kkossev.commonLib, line 925
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 926
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 927
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 928
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 929
    } // library marker kkossev.commonLib, line 930
    else { logDebug 'no customInitializeDevice method defined' } // library marker kkossev.commonLib, line 931
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 932
    return cmds // library marker kkossev.commonLib, line 933
} // library marker kkossev.commonLib, line 934

// Invoked from configure() // library marker kkossev.commonLib, line 936
public List<String> configureDevice() { // library marker kkossev.commonLib, line 937
    List<String> cmds = [] // library marker kkossev.commonLib, line 938
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 939
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 940
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 941
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 942
    } // library marker kkossev.commonLib, line 943
    else { logDebug 'no customConfigureDevice method defined' } // library marker kkossev.commonLib, line 944
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 945
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 946
    return cmds // library marker kkossev.commonLib, line 947
} // library marker kkossev.commonLib, line 948

/* // library marker kkossev.commonLib, line 950
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 951
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 952
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 953
*/ // library marker kkossev.commonLib, line 954

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 956
    List<String> cmds = [] // library marker kkossev.commonLib, line 957
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 958
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 959
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 960
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 961
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 962
            } // library marker kkossev.commonLib, line 963
        } // library marker kkossev.commonLib, line 964
    } // library marker kkossev.commonLib, line 965
    return cmds // library marker kkossev.commonLib, line 966
} // library marker kkossev.commonLib, line 967

public void refresh() { // library marker kkossev.commonLib, line 969
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 970
    checkDriverVersion(state) // library marker kkossev.commonLib, line 971
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 972
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 973
        customCmds = customRefresh() // library marker kkossev.commonLib, line 974
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 975
    } // library marker kkossev.commonLib, line 976
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 977
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 978
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 979
    } // library marker kkossev.commonLib, line 980
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 981
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 982
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 983
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 984
    } // library marker kkossev.commonLib, line 985
    else { // library marker kkossev.commonLib, line 986
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 987
    } // library marker kkossev.commonLib, line 988
} // library marker kkossev.commonLib, line 989

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, 'clearRefreshRequest', [overwrite: true]) } // library marker kkossev.commonLib, line 991
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 992
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 993

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 995
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 996
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 997
        sendEvent(name: 'Status', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 998
    } // library marker kkossev.commonLib, line 999
    else { // library marker kkossev.commonLib, line 1000
        logInfo "${info}" // library marker kkossev.commonLib, line 1001
        sendEvent(name: 'Status', value: info, type: 'digital') // library marker kkossev.commonLib, line 1002
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1003
    } // library marker kkossev.commonLib, line 1004
} // library marker kkossev.commonLib, line 1005

public void ping() { // library marker kkossev.commonLib, line 1007
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1008
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true // library marker kkossev.commonLib, line 1009
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1010
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID // library marker kkossev.commonLib, line 1011
    if (isVirtual()) { runInMillis(10, 'virtualPong') } // library marker kkossev.commonLib, line 1012
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) } // library marker kkossev.commonLib, line 1013
    logDebug 'ping...' // library marker kkossev.commonLib, line 1014
} // library marker kkossev.commonLib, line 1015

private void virtualPong() { // library marker kkossev.commonLib, line 1017
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1018
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1019
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1020
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1021
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1022
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '9999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1023
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1024
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1025
        sendRttEvent() // library marker kkossev.commonLib, line 1026
    } // library marker kkossev.commonLib, line 1027
    else { // library marker kkossev.commonLib, line 1028
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1029
    } // library marker kkossev.commonLib, line 1030
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1031
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1032
} // library marker kkossev.commonLib, line 1033

public void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1035
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1036
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1037
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1038
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1039
    if (value == null) { // library marker kkossev.commonLib, line 1040
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1041
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1042
    } // library marker kkossev.commonLib, line 1043
    else { // library marker kkossev.commonLib, line 1044
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1045
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1046
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1047
    } // library marker kkossev.commonLib, line 1048
} // library marker kkossev.commonLib, line 1049

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1051
    if (cluster != null) { // library marker kkossev.commonLib, line 1052
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1053
    } // library marker kkossev.commonLib, line 1054
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1055
    return 'NULL' // library marker kkossev.commonLib, line 1056
} // library marker kkossev.commonLib, line 1057

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1059
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1060
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1061
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1062
} // library marker kkossev.commonLib, line 1063

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1065
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1066
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1067
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1068
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1069
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1070
    } // library marker kkossev.commonLib, line 1071
} // library marker kkossev.commonLib, line 1072

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1074
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1075
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1076
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1077
    if (state.health?.isHealthCheck == true) { // library marker kkossev.commonLib, line 1078
        logWarn 'device health check failed!' // library marker kkossev.commonLib, line 1079
        state.health?.checkCtr3 = (state.health?.checkCtr3 ?: 0 ) + 1 // library marker kkossev.commonLib, line 1080
        if (state.health?.checkCtr3 >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1081
            if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1082
                sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1083
            } // library marker kkossev.commonLib, line 1084
        } // library marker kkossev.commonLib, line 1085
        state.health['isHealthCheck'] = false // library marker kkossev.commonLib, line 1086
    } // library marker kkossev.commonLib, line 1087
} // library marker kkossev.commonLib, line 1088

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1090
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1091
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1092
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1093
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1094
    } // library marker kkossev.commonLib, line 1095
    else { // library marker kkossev.commonLib, line 1096
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1097
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1098
    } // library marker kkossev.commonLib, line 1099
} // library marker kkossev.commonLib, line 1100

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1102
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1103
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1104
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1105
} // library marker kkossev.commonLib, line 1106

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1108
private void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1109
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1110
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1111
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1112
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1113
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1114
    } // library marker kkossev.commonLib, line 1115
} // library marker kkossev.commonLib, line 1116

private void deviceHealthCheck() { // library marker kkossev.commonLib, line 1118
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1119
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1120
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1121
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1122
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1123
            logWarn 'not present!' // library marker kkossev.commonLib, line 1124
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1125
        } // library marker kkossev.commonLib, line 1126
    } // library marker kkossev.commonLib, line 1127
    else { // library marker kkossev.commonLib, line 1128
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})" // library marker kkossev.commonLib, line 1129
    } // library marker kkossev.commonLib, line 1130
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1131
    // added 03/06/2025 // library marker kkossev.commonLib, line 1132
    if (settings?.healthCheckMethod as int == 2) { // library marker kkossev.commonLib, line 1133
        state.health['isHealthCheck'] = true // library marker kkossev.commonLib, line 1134
        ping()  // proactively ping the device... // library marker kkossev.commonLib, line 1135
    } // library marker kkossev.commonLib, line 1136
} // library marker kkossev.commonLib, line 1137

private void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1139
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1140
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1141
    if (value == 'online') { // library marker kkossev.commonLib, line 1142
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1143
    } // library marker kkossev.commonLib, line 1144
    else { // library marker kkossev.commonLib, line 1145
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1146
    } // library marker kkossev.commonLib, line 1147
} // library marker kkossev.commonLib, line 1148

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1150
void updated() { // library marker kkossev.commonLib, line 1151
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1152
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1153
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1154
    unschedule() // library marker kkossev.commonLib, line 1155

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1157
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1158
        runIn(86400, 'logsOff') // library marker kkossev.commonLib, line 1159
    } // library marker kkossev.commonLib, line 1160
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1161
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1162
        runIn(1800, 'traceOff') // library marker kkossev.commonLib, line 1163
    } // library marker kkossev.commonLib, line 1164

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1166
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1167
        // schedule the periodic timer // library marker kkossev.commonLib, line 1168
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1169
        if (interval > 0) { // library marker kkossev.commonLib, line 1170
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1171
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1172
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1173
        } // library marker kkossev.commonLib, line 1174
    } // library marker kkossev.commonLib, line 1175
    else { // library marker kkossev.commonLib, line 1176
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1177
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1178
    } // library marker kkossev.commonLib, line 1179
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1180
        customUpdated() // library marker kkossev.commonLib, line 1181
    } // library marker kkossev.commonLib, line 1182

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1184
} // library marker kkossev.commonLib, line 1185

private void logsOff() { // library marker kkossev.commonLib, line 1187
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1188
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1189
} // library marker kkossev.commonLib, line 1190
private void traceOff() { // library marker kkossev.commonLib, line 1191
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1192
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1193
} // library marker kkossev.commonLib, line 1194

public void configure(String command) { // library marker kkossev.commonLib, line 1196
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1197
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1198
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1199
        return // library marker kkossev.commonLib, line 1200
    } // library marker kkossev.commonLib, line 1201
    // // library marker kkossev.commonLib, line 1202
    String func // library marker kkossev.commonLib, line 1203
    try { // library marker kkossev.commonLib, line 1204
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1205
        "$func"() // library marker kkossev.commonLib, line 1206
    } // library marker kkossev.commonLib, line 1207
    catch (e) { // library marker kkossev.commonLib, line 1208
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1209
        return // library marker kkossev.commonLib, line 1210
    } // library marker kkossev.commonLib, line 1211
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1212
} // library marker kkossev.commonLib, line 1213

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1215
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1216
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1217
} // library marker kkossev.commonLib, line 1218

public void loadAllDefaults() { // library marker kkossev.commonLib, line 1220
    logDebug 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1221
    deleteAllSettings() // library marker kkossev.commonLib, line 1222
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1223
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1224
    deleteAllStates() // library marker kkossev.commonLib, line 1225
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1226

    initialize() // library marker kkossev.commonLib, line 1228
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1229
    updated() // library marker kkossev.commonLib, line 1230
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1231
} // library marker kkossev.commonLib, line 1232

private void configureNow() { // library marker kkossev.commonLib, line 1234
    configure() // library marker kkossev.commonLib, line 1235
} // library marker kkossev.commonLib, line 1236

/** // library marker kkossev.commonLib, line 1238
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1239
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1240
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1241
 */ // library marker kkossev.commonLib, line 1242
void configure() { // library marker kkossev.commonLib, line 1243
    List<String> cmds = [] // library marker kkossev.commonLib, line 1244
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1245
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1246
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1247
    if (isTuya()) { // library marker kkossev.commonLib, line 1248
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1249
    } // library marker kkossev.commonLib, line 1250
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1251
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1252
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1253
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1254
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1255
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1256
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1257
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1258
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1259
    } // library marker kkossev.commonLib, line 1260
    else { // library marker kkossev.commonLib, line 1261
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1262
    } // library marker kkossev.commonLib, line 1263
} // library marker kkossev.commonLib, line 1264

 // Invoked when the device is installed with this driver automatically selected. // library marker kkossev.commonLib, line 1266
void installed() { // library marker kkossev.commonLib, line 1267
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1268
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1269
    // populate some default values for attributes // library marker kkossev.commonLib, line 1270
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1271
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1272
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1273
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1274
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1275
} // library marker kkossev.commonLib, line 1276

private void queryPowerSource() { // library marker kkossev.commonLib, line 1278
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1279
} // library marker kkossev.commonLib, line 1280

 // Invoked from 'LoadAllDefaults' // library marker kkossev.commonLib, line 1282
private void initialize() { // library marker kkossev.commonLib, line 1283
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1284
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1285
    if (device.getDataValue('powerSource') == null) { // library marker kkossev.commonLib, line 1286
        logInfo "initializing device powerSource 'unknown'" // library marker kkossev.commonLib, line 1287
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1288
    } // library marker kkossev.commonLib, line 1289
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1290
    updateTuyaVersion() // library marker kkossev.commonLib, line 1291
    updateAqaraVersion() // library marker kkossev.commonLib, line 1292
} // library marker kkossev.commonLib, line 1293

/* // library marker kkossev.commonLib, line 1295
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1296
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1297
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1298
*/ // library marker kkossev.commonLib, line 1299

static Integer safeToInt(Object val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1301
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1302
} // library marker kkossev.commonLib, line 1303

static Double safeToDouble(Object val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1305
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1306
} // library marker kkossev.commonLib, line 1307

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1309
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1310
} // library marker kkossev.commonLib, line 1311

public void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1313
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1314
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1315
        return // library marker kkossev.commonLib, line 1316
    } // library marker kkossev.commonLib, line 1317
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1318
    cmd.each { // library marker kkossev.commonLib, line 1319
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1320
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1321
            return // library marker kkossev.commonLib, line 1322
        } // library marker kkossev.commonLib, line 1323
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1324
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1325
    } // library marker kkossev.commonLib, line 1326
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1327
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1328
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1329
} // library marker kkossev.commonLib, line 1330

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1332

private String getDeviceInfo() { // library marker kkossev.commonLib, line 1334
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1335
} // library marker kkossev.commonLib, line 1336

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1338
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1339
} // library marker kkossev.commonLib, line 1340

//@CompileStatic // library marker kkossev.commonLib, line 1342
public void checkDriverVersion(final Map stateCopy) { // library marker kkossev.commonLib, line 1343
    if (stateCopy.driverVersion == null || driverVersionAndTimeStamp() != stateCopy.driverVersion) { // library marker kkossev.commonLib, line 1344
        logDebug "checkDriverVersion: updating the settings from the current driver version ${stateCopy.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1345
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1346
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1347
        initializeVars(false) // library marker kkossev.commonLib, line 1348
        updateTuyaVersion() // library marker kkossev.commonLib, line 1349
        updateAqaraVersion() // library marker kkossev.commonLib, line 1350
    } // library marker kkossev.commonLib, line 1351
    if (state.states == null) { state.states = [:] } ; if (state.lastRx == null) { state.lastRx = [:] } ; if (state.lastTx == null) { state.lastTx = [:] } ; if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1352
} // library marker kkossev.commonLib, line 1353

// credits @thebearmay // library marker kkossev.commonLib, line 1355
String getModel() { // library marker kkossev.commonLib, line 1356
    try { // library marker kkossev.commonLib, line 1357
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1358
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1359
    } catch (ignore) { // library marker kkossev.commonLib, line 1360
        try { // library marker kkossev.commonLib, line 1361
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1362
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1363
                return model // library marker kkossev.commonLib, line 1364
            } // library marker kkossev.commonLib, line 1365
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1366
            return '' // library marker kkossev.commonLib, line 1367
        } // library marker kkossev.commonLib, line 1368
    } // library marker kkossev.commonLib, line 1369
} // library marker kkossev.commonLib, line 1370

// credits @thebearmay // library marker kkossev.commonLib, line 1372
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1373
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1374
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1375
    String revision = tokens.last() // library marker kkossev.commonLib, line 1376
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1377
} // library marker kkossev.commonLib, line 1378

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1380
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1381
    unschedule() // library marker kkossev.commonLib, line 1382
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1383
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1384

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1386
} // library marker kkossev.commonLib, line 1387

void resetStatistics() { // library marker kkossev.commonLib, line 1389
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1390
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1391
} // library marker kkossev.commonLib, line 1392

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1394
void resetStats() { // library marker kkossev.commonLib, line 1395
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1396
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1397
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1398
    state.stats.rxCtr = 0 ; state.stats.txCtr = 0 // library marker kkossev.commonLib, line 1399
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1400
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1401
} // library marker kkossev.commonLib, line 1402

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1404
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1405
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1406
        state.clear() // library marker kkossev.commonLib, line 1407
        unschedule() // library marker kkossev.commonLib, line 1408
        resetStats() // library marker kkossev.commonLib, line 1409
        if (deviceProfilesV3 != null && this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1410
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1411
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1412
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1413
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1414
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1415
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1416
    } // library marker kkossev.commonLib, line 1417

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1419
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1420
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1421
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1422
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1423

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1425
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1426
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1427
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1428
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1429
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1430
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1431

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1433

    // common libraries initialization // library marker kkossev.commonLib, line 1435
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024 // library marker kkossev.commonLib, line 1436
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024 // library marker kkossev.commonLib, line 1437
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1438
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1439
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1440
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1441
    // // library marker kkossev.commonLib, line 1442
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers! // library marker kkossev.commonLib, line 1443
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024 // library marker kkossev.commonLib, line 1444
    // // library marker kkossev.commonLib, line 1445
    // custom device driver specific initialization should be at the end // library marker kkossev.commonLib, line 1446
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1447
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1448
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1449

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1451
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1452
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1453
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1454
    if ( ep  != null) { // library marker kkossev.commonLib, line 1455
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1456
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1457
    } // library marker kkossev.commonLib, line 1458
    else { // library marker kkossev.commonLib, line 1459
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1460
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1461
    } // library marker kkossev.commonLib, line 1462
} // library marker kkossev.commonLib, line 1463

// not used!? // library marker kkossev.commonLib, line 1465
void setDestinationEP() { // library marker kkossev.commonLib, line 1466
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1467
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1468
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1469
} // library marker kkossev.commonLib, line 1470

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1472
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1473
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1474
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1475

// _DEBUG mode only // library marker kkossev.commonLib, line 1477
void getAllProperties() { // library marker kkossev.commonLib, line 1478
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1479
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1480
} // library marker kkossev.commonLib, line 1481

// delete all Preferences // library marker kkossev.commonLib, line 1483
void deleteAllSettings() { // library marker kkossev.commonLib, line 1484
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1485
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1486
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1487
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1488
} // library marker kkossev.commonLib, line 1489

// delete all attributes // library marker kkossev.commonLib, line 1491
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1492
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1493
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1494
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1495
} // library marker kkossev.commonLib, line 1496

// delete all State Variables // library marker kkossev.commonLib, line 1498
void deleteAllStates() { // library marker kkossev.commonLib, line 1499
    String stateDeleted = '' // library marker kkossev.commonLib, line 1500
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1501
    state.clear() // library marker kkossev.commonLib, line 1502
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1503
} // library marker kkossev.commonLib, line 1504

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1506
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1507
} // library marker kkossev.commonLib, line 1508

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1510
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1511
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1512
} // library marker kkossev.commonLib, line 1513

void testParse(String par) { // library marker kkossev.commonLib, line 1515
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1516
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1517
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1518
    parse(par) // library marker kkossev.commonLib, line 1519
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1520
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1521
} // library marker kkossev.commonLib, line 1522

Object testJob() { // library marker kkossev.commonLib, line 1524
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1525
} // library marker kkossev.commonLib, line 1526

/** // library marker kkossev.commonLib, line 1528
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1529
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1530
 */ // library marker kkossev.commonLib, line 1531
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1532
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1533
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1534
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1535
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1536
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1537
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1538
    String cron // library marker kkossev.commonLib, line 1539
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1540
    else { // library marker kkossev.commonLib, line 1541
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1542
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1543
    } // library marker kkossev.commonLib, line 1544
    return cron // library marker kkossev.commonLib, line 1545
} // library marker kkossev.commonLib, line 1546

// credits @thebearmay // library marker kkossev.commonLib, line 1548
String formatUptime() { // library marker kkossev.commonLib, line 1549
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1550
} // library marker kkossev.commonLib, line 1551

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1553
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1554
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1555
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1556
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1557
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1558
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1559
} // library marker kkossev.commonLib, line 1560

boolean isTuya() { // library marker kkossev.commonLib, line 1562
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1563
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1564
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1565
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1566
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1567
} // library marker kkossev.commonLib, line 1568

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1570
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1571
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1572
    if (application != null) { // library marker kkossev.commonLib, line 1573
        Integer ver // library marker kkossev.commonLib, line 1574
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1575
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1576
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1577
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1578
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1579
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1580
        } // library marker kkossev.commonLib, line 1581
    } // library marker kkossev.commonLib, line 1582
} // library marker kkossev.commonLib, line 1583

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1585

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1587
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1588
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1589
    if (application != null) { // library marker kkossev.commonLib, line 1590
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1591
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1592
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1593
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1594
        } // library marker kkossev.commonLib, line 1595
    } // library marker kkossev.commonLib, line 1596
} // library marker kkossev.commonLib, line 1597

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1599
    try { // library marker kkossev.commonLib, line 1600
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1601
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1602
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1603
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1604
    } catch (e) { // library marker kkossev.commonLib, line 1605
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1606
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1607
    } // library marker kkossev.commonLib, line 1608
} // library marker kkossev.commonLib, line 1609

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1611
    try { // library marker kkossev.commonLib, line 1612
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1613
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1614
        return date.getTime() // library marker kkossev.commonLib, line 1615
    } catch (e) { // library marker kkossev.commonLib, line 1616
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1617
        return now() // library marker kkossev.commonLib, line 1618
    } // library marker kkossev.commonLib, line 1619
} // library marker kkossev.commonLib, line 1620

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1622
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1623
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1624
    int seconds = time % 60 // library marker kkossev.commonLib, line 1625
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1626
} // library marker kkossev.commonLib, line 1627

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (176) kkossev.onOffLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.onOffLib, line 1
library( // library marker kkossev.onOffLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee OnOff Cluster Library', name: 'onOffLib', namespace: 'kkossev', // library marker kkossev.onOffLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/onOffLib.groovy', documentationLink: '', // library marker kkossev.onOffLib, line 4
    version: '3.2.2' // library marker kkossev.onOffLib, line 5
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
 * // library marker kkossev.onOffLib, line 22
 *                                   TODO: // library marker kkossev.onOffLib, line 23
*/ // library marker kkossev.onOffLib, line 24

static String onOffLibVersion()   { '3.2.2' } // library marker kkossev.onOffLib, line 26
static String onOffLibStamp() { '2024/06/29 12:27 PM' } // library marker kkossev.onOffLib, line 27

@Field static final Boolean _THREE_STATE = true // library marker kkossev.onOffLib, line 29

metadata { // library marker kkossev.onOffLib, line 31
    capability 'Actuator' // library marker kkossev.onOffLib, line 32
    capability 'Switch' // library marker kkossev.onOffLib, line 33
    if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 34
        attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.onOffLib, line 35
    } // library marker kkossev.onOffLib, line 36
    // no commands // library marker kkossev.onOffLib, line 37
    preferences { // library marker kkossev.onOffLib, line 38
        if (settings?.advancedOptions == true && device != null && !(DEVICE_TYPE in ['Device', 'Thermostat'])) { // library marker kkossev.onOffLib, line 39
            input(name: 'ignoreDuplicated', type: 'bool', title: '<b>Ignore Duplicated Switch Events</b>', description: 'Some switches and plugs send periodically the switch status as a heart-beet ', defaultValue: true) // library marker kkossev.onOffLib, line 40
            input(name: 'alwaysOn', type: 'bool', title: '<b>Always On</b>', description: 'Disable switching off plugs and switches that must stay always On', defaultValue: false) // library marker kkossev.onOffLib, line 41
            if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 42
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: 'Experimental multi-state switch events', defaultValue: false // library marker kkossev.onOffLib, line 43
            } // library marker kkossev.onOffLib, line 44
        } // library marker kkossev.onOffLib, line 45
    } // library marker kkossev.onOffLib, line 46
} // library marker kkossev.onOffLib, line 47

@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.onOffLib, line 49
    defaultValue: 0, options: [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.onOffLib, line 50
] // library marker kkossev.onOffLib, line 51

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.onOffLib, line 53
    '0': 'switch off', '1': 'switch on', '2': 'switch last state' // library marker kkossev.onOffLib, line 54
] // library marker kkossev.onOffLib, line 55

@Field static final Map switchTypeOptions = [ // library marker kkossev.onOffLib, line 57
    '0': 'toggle', '1': 'state', '2': 'momentary' // library marker kkossev.onOffLib, line 58
] // library marker kkossev.onOffLib, line 59

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.onOffLib, line 61

/* // library marker kkossev.onOffLib, line 63
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 64
 * on/off cluster            0x0006     TODO - move to a library !!!!!!!!!!!!!!! // library marker kkossev.onOffLib, line 65
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 66
*/ // library marker kkossev.onOffLib, line 67
void standardParseOnOffCluster(final Map descMap) { // library marker kkossev.onOffLib, line 68
    /* // library marker kkossev.onOffLib, line 69
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.onOffLib, line 70
        customParseOnOffCluster(descMap) // library marker kkossev.onOffLib, line 71
    } // library marker kkossev.onOffLib, line 72
    else */ // library marker kkossev.onOffLib, line 73
    if (descMap.attrId == '0000') { // library marker kkossev.onOffLib, line 74
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.onOffLib, line 75
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.onOffLib, line 76
        sendSwitchEvent(rawValue) // library marker kkossev.onOffLib, line 77
    } // library marker kkossev.onOffLib, line 78
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.onOffLib, line 79
        parseOnOffAttributes(descMap) // library marker kkossev.onOffLib, line 80
    } // library marker kkossev.onOffLib, line 81
    else { // library marker kkossev.onOffLib, line 82
        if (descMap.attrId != null) { logWarn "standardParseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.onOffLib, line 83
        else { logDebug "standardParseOnOffCluster: skipped processing OnOff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.onOffLib, line 84
    } // library marker kkossev.onOffLib, line 85
} // library marker kkossev.onOffLib, line 86

void toggleX() { // library marker kkossev.onOffLib, line 88
    String descriptionText = 'central button switch is ' // library marker kkossev.onOffLib, line 89
    String state = '' // library marker kkossev.onOffLib, line 90
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.onOffLib, line 91
        state = 'on' // library marker kkossev.onOffLib, line 92
    } // library marker kkossev.onOffLib, line 93
    else { // library marker kkossev.onOffLib, line 94
        state = 'off' // library marker kkossev.onOffLib, line 95
    } // library marker kkossev.onOffLib, line 96
    descriptionText += state // library marker kkossev.onOffLib, line 97
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.onOffLib, line 98
    logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 99
} // library marker kkossev.onOffLib, line 100

void off() { // library marker kkossev.onOffLib, line 102
    if (this.respondsTo('customOff')) { customOff() ; return  } // library marker kkossev.onOffLib, line 103
    if ((settings?.alwaysOn ?: false) == true) { logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" ; return } // library marker kkossev.onOffLib, line 104
    List<String> cmds = [] // library marker kkossev.onOffLib, line 105
    // added 06/29/2024 - control Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 106
    if (this.respondsTo(getDEVICE)) {   // defined in deviceProfileLib // library marker kkossev.onOffLib, line 107
        Map switchMap = getAttributesMap('switch') // library marker kkossev.onOffLib, line 108
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  0  : 1 // library marker kkossev.onOffLib, line 109
        if (switchMap != null && switchMap != [:]) { // library marker kkossev.onOffLib, line 110
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue) // library marker kkossev.onOffLib, line 111
            logTrace "off() Tuya cmds=${cmds}" // library marker kkossev.onOffLib, line 112
        } // library marker kkossev.onOffLib, line 113
    } // library marker kkossev.onOffLib, line 114
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 115
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.onOffLib, line 116
    } // library marker kkossev.onOffLib, line 117

    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 119
    logDebug "off() currentState=${currentState}" // library marker kkossev.onOffLib, line 120
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 121
        if (currentState == 'off') { // library marker kkossev.onOffLib, line 122
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 123
        } // library marker kkossev.onOffLib, line 124
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.onOffLib, line 125
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 126
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 127
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 128
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 129
    } // library marker kkossev.onOffLib, line 130
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 131
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 132
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 133
} // library marker kkossev.onOffLib, line 134

void on() { // library marker kkossev.onOffLib, line 136
    if (this.respondsTo('customOn')) { customOn() ; return } // library marker kkossev.onOffLib, line 137
    List<String> cmds = [] // library marker kkossev.onOffLib, line 138
    // added 06/29/2024 - control Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 139
    if (this.respondsTo(getDEVICE)) {   // defined in deviceProfileLib // library marker kkossev.onOffLib, line 140
        Map switchMap = getAttributesMap('switch') // library marker kkossev.onOffLib, line 141
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  1  : 0 // library marker kkossev.onOffLib, line 142
        if (switchMap != null && switchMap != [:]) { // library marker kkossev.onOffLib, line 143
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue) // library marker kkossev.onOffLib, line 144
            logTrace "on() Tuya cmds=${cmds}" // library marker kkossev.onOffLib, line 145
        } // library marker kkossev.onOffLib, line 146
    } // library marker kkossev.onOffLib, line 147
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 148
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.onOffLib, line 149
    } // library marker kkossev.onOffLib, line 150
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 151
    logDebug "on() currentState=${currentState}" // library marker kkossev.onOffLib, line 152
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 153
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.onOffLib, line 154
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 155
        } // library marker kkossev.onOffLib, line 156
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.onOffLib, line 157
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 158
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 159
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 160
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 161
    } // library marker kkossev.onOffLib, line 162
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 163
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 164
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 165
} // library marker kkossev.onOffLib, line 166

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.onOffLib, line 168
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.onOffLib, line 169
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.onOffLib, line 170
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.onOffLib, line 171
    } // library marker kkossev.onOffLib, line 172
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.onOffLib, line 173
    Map map = [:] // library marker kkossev.onOffLib, line 174
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.onOffLib, line 175
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.onOffLib, line 176
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.onOffLib, line 177
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false)) && !isRefresh) { // library marker kkossev.onOffLib, line 178
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.onOffLib, line 179
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 180
        return // library marker kkossev.onOffLib, line 181
    } // library marker kkossev.onOffLib, line 182
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.onOffLib, line 183
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.onOffLib, line 184
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.onOffLib, line 185
    if (lastSwitch != value) { // library marker kkossev.onOffLib, line 186
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.onOffLib, line 187
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 188
        state.states['lastSwitch'] = value // library marker kkossev.onOffLib, line 189
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 190
    } else { // library marker kkossev.onOffLib, line 191
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 192
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 193
    } // library marker kkossev.onOffLib, line 194
    map.name = 'switch' // library marker kkossev.onOffLib, line 195
    map.value = value // library marker kkossev.onOffLib, line 196
    if (isRefresh) { // library marker kkossev.onOffLib, line 197
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.onOffLib, line 198
        map.isStateChange = true // library marker kkossev.onOffLib, line 199
    } else { // library marker kkossev.onOffLib, line 200
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.onOffLib, line 201
    } // library marker kkossev.onOffLib, line 202
    logInfo "${map.descriptionText}" // library marker kkossev.onOffLib, line 203
    sendEvent(map) // library marker kkossev.onOffLib, line 204
    clearIsDigital() // library marker kkossev.onOffLib, line 205
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.onOffLib, line 206
        customSwitchEventPostProcesing(map) // library marker kkossev.onOffLib, line 207
    } // library marker kkossev.onOffLib, line 208
} // library marker kkossev.onOffLib, line 209

void parseOnOffAttributes(final Map it) { // library marker kkossev.onOffLib, line 211
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 212
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.onOffLib, line 213
    String mode // library marker kkossev.onOffLib, line 214
    String attrName // library marker kkossev.onOffLib, line 215
    if (it.value == null) { // library marker kkossev.onOffLib, line 216
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.onOffLib, line 217
        return // library marker kkossev.onOffLib, line 218
    } // library marker kkossev.onOffLib, line 219
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.onOffLib, line 220
    switch (it.attrId) { // library marker kkossev.onOffLib, line 221
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.onOffLib, line 222
            attrName = 'Global Scene Control' // library marker kkossev.onOffLib, line 223
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.onOffLib, line 224
            break // library marker kkossev.onOffLib, line 225
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.onOffLib, line 226
            attrName = 'On Time' // library marker kkossev.onOffLib, line 227
            mode = value // library marker kkossev.onOffLib, line 228
            break // library marker kkossev.onOffLib, line 229
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.onOffLib, line 230
            attrName = 'Off Wait Time' // library marker kkossev.onOffLib, line 231
            mode = value // library marker kkossev.onOffLib, line 232
            break // library marker kkossev.onOffLib, line 233
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.onOffLib, line 234
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 235
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.onOffLib, line 236
            break // library marker kkossev.onOffLib, line 237
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.onOffLib, line 238
            attrName = 'Child Lock' // library marker kkossev.onOffLib, line 239
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.onOffLib, line 240
            break // library marker kkossev.onOffLib, line 241
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.onOffLib, line 242
            attrName = 'LED mode' // library marker kkossev.onOffLib, line 243
            if (isCircuitBreaker()) { // library marker kkossev.onOffLib, line 244
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.onOffLib, line 245
            } // library marker kkossev.onOffLib, line 246
            else { // library marker kkossev.onOffLib, line 247
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.onOffLib, line 248
            } // library marker kkossev.onOffLib, line 249
            break // library marker kkossev.onOffLib, line 250
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.onOffLib, line 251
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 252
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.onOffLib, line 253
            break // library marker kkossev.onOffLib, line 254
        case '8003' : //  Over current alarm // library marker kkossev.onOffLib, line 255
            attrName = 'Over current alarm' // library marker kkossev.onOffLib, line 256
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.onOffLib, line 257
            break // library marker kkossev.onOffLib, line 258
        default : // library marker kkossev.onOffLib, line 259
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 260
            return // library marker kkossev.onOffLib, line 261
    } // library marker kkossev.onOffLib, line 262
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.onOffLib, line 263
} // library marker kkossev.onOffLib, line 264

List<String> onOffRefresh() { // library marker kkossev.onOffLib, line 266
    logDebug 'onOffRefresh()' // library marker kkossev.onOffLib, line 267
    List<String> cmds = zigbee.readAttribute(0x0006, 0x0000, [:], delay = 100) // library marker kkossev.onOffLib, line 268
    return cmds // library marker kkossev.onOffLib, line 269
} // library marker kkossev.onOffLib, line 270

void onOfInitializeVars( boolean fullInit = false ) { // library marker kkossev.onOffLib, line 272
    logDebug "onOfInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.onOffLib, line 273
    if (fullInit || settings?.ignoreDuplicated == null) { device.updateSetting('ignoreDuplicated', true) } // library marker kkossev.onOffLib, line 274
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.onOffLib, line 275
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.onOffLib, line 276
} // library marker kkossev.onOffLib, line 277

// ~~~~~ end include (176) kkossev.onOffLib ~~~~~

// ~~~~~ start include (171) kkossev.batteryLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoJavaUtilDate, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
library( // library marker kkossev.batteryLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Battery Library', name: 'batteryLib', namespace: 'kkossev', // library marker kkossev.batteryLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/batteryLib.groovy', documentationLink: '', // library marker kkossev.batteryLib, line 4
    version: '3.2.2' // library marker kkossev.batteryLib, line 5
) // library marker kkossev.batteryLib, line 6
/* // library marker kkossev.batteryLib, line 7
 *  Zigbee Level Library // library marker kkossev.batteryLib, line 8
 * // library marker kkossev.batteryLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.batteryLib, line 10
 * // library marker kkossev.batteryLib, line 11
 * ver. 3.0.0  2024-04-06 kkossev  - added batteryLib.groovy // library marker kkossev.batteryLib, line 12
 * ver. 3.0.1  2024-04-06 kkossev  - customParsePowerCluster bug fix // library marker kkossev.batteryLib, line 13
 * ver. 3.0.2  2024-04-14 kkossev  - batteryPercentage bug fix (was x2); added bVoltCtr; added battertRefresh // library marker kkossev.batteryLib, line 14
 * ver. 3.2.0  2024-05-21 kkossev  - commonLib 3.2.0 allignment; added lastBattery; added handleTuyaBatteryLevel // library marker kkossev.batteryLib, line 15
 * ver. 3.2.1  2024-07-06 kkossev  - added tuyaToBatteryLevel and handleTuyaBatteryLevel; added batteryInitializeVars // library marker kkossev.batteryLib, line 16
 * ver. 3.2.2  2024-07-18 kkossev  - added BatteryVoltage and BatteryDelay device capability checks // library marker kkossev.batteryLib, line 17
 * // library marker kkossev.batteryLib, line 18
 *                                   TODO:  // library marker kkossev.batteryLib, line 19
 *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.batteryLib, line 20
*/ // library marker kkossev.batteryLib, line 21

static String batteryLibVersion()   { '3.2.2' } // library marker kkossev.batteryLib, line 23
static String batteryLibStamp() { '2024/07/18 2:34 PM' } // library marker kkossev.batteryLib, line 24

metadata { // library marker kkossev.batteryLib, line 26
    capability 'Battery' // library marker kkossev.batteryLib, line 27
    attribute  'batteryVoltage', 'number' // library marker kkossev.batteryLib, line 28
    attribute  'lastBattery', 'date'         // last battery event time - added in 3.2.0 05/21/2024 // library marker kkossev.batteryLib, line 29
    // no commands // library marker kkossev.batteryLib, line 30
    preferences { // library marker kkossev.batteryLib, line 31
        if (device && advancedOptions == true) { // library marker kkossev.batteryLib, line 32
            if ('BatteryVoltage' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 33
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: 'Convert battery voltage to battery Percentage remaining.' // library marker kkossev.batteryLib, line 34
            } // library marker kkossev.batteryLib, line 35
            if ('BatteryDelay' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 36
                input(name: 'batteryDelay', type: 'enum', title: '<b>Battery Events Delay</b>', description:'Select the Battery Events Delay<br>(default is <b>no delay</b>)', options: DelayBatteryOpts.options, defaultValue: DelayBatteryOpts.defaultValue) // library marker kkossev.batteryLib, line 37
            } // library marker kkossev.batteryLib, line 38
        } // library marker kkossev.batteryLib, line 39
    } // library marker kkossev.batteryLib, line 40
} // library marker kkossev.batteryLib, line 41

@Field static final Map DelayBatteryOpts = [ defaultValue: 0, options: [0: 'No delay', 30: '30 seconds', 3600: '1 hour', 14400: '4 hours', 28800: '8 hours', 43200: '12 hours']] // library marker kkossev.batteryLib, line 43

public void standardParsePowerCluster(final Map descMap) { // library marker kkossev.batteryLib, line 45
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.batteryLib, line 46
    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.batteryLib, line 47
    if (descMap.attrId == '0020') { // battery voltage // library marker kkossev.batteryLib, line 48
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 49
        state.stats['bVoltCtr'] = (state.stats['bVoltCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 50
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.batteryLib, line 51
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.batteryLib, line 52
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.batteryLib, line 53
        } // library marker kkossev.batteryLib, line 54
    } // library marker kkossev.batteryLib, line 55
    else if (descMap.attrId == '0021') { // battery percentage // library marker kkossev.batteryLib, line 56
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 57
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 58
        if (isTuya()) { // library marker kkossev.batteryLib, line 59
            sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 60
        } // library marker kkossev.batteryLib, line 61
        else { // library marker kkossev.batteryLib, line 62
            sendBatteryPercentageEvent((rawValue / 2) as int) // library marker kkossev.batteryLib, line 63
        } // library marker kkossev.batteryLib, line 64
    } // library marker kkossev.batteryLib, line 65
    else { // library marker kkossev.batteryLib, line 66
        logWarn "customParsePowerCluster: zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.batteryLib, line 67
    } // library marker kkossev.batteryLib, line 68
} // library marker kkossev.batteryLib, line 69

public void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.batteryLib, line 71
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.batteryLib, line 72
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 73
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
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 99
    } // library marker kkossev.batteryLib, line 100
    else { // library marker kkossev.batteryLib, line 101
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.batteryLib, line 102
    } // library marker kkossev.batteryLib, line 103
} // library marker kkossev.batteryLib, line 104

public void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.batteryLib, line 106
    if ((batteryPercent as int) == 255) { // library marker kkossev.batteryLib, line 107
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.batteryLib, line 108
        return // library marker kkossev.batteryLib, line 109
    } // library marker kkossev.batteryLib, line 110
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 111
    Map map = [:] // library marker kkossev.batteryLib, line 112
    map.name = 'battery' // library marker kkossev.batteryLib, line 113
    map.timeStamp = now() // library marker kkossev.batteryLib, line 114
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.batteryLib, line 115
    map.unit  = '%' // library marker kkossev.batteryLib, line 116
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.batteryLib, line 117
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.batteryLib, line 118
    map.isStateChange = true // library marker kkossev.batteryLib, line 119
    // // library marker kkossev.batteryLib, line 120
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.batteryLib, line 121
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.batteryLib, line 122
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.batteryLib, line 123
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.batteryLib, line 124
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.batteryLib, line 125
        // send it now! // library marker kkossev.batteryLib, line 126
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.batteryLib, line 127
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 128
    } // library marker kkossev.batteryLib, line 129
    else { // library marker kkossev.batteryLib, line 130
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.batteryLib, line 131
        map.delayed = delayedTime // library marker kkossev.batteryLib, line 132
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.batteryLib, line 133
        map.lastBattery = lastBattery // library marker kkossev.batteryLib, line 134
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.batteryLib, line 135
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.batteryLib, line 136
    } // library marker kkossev.batteryLib, line 137
} // library marker kkossev.batteryLib, line 138

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.batteryLib, line 140
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 141
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 142
    sendEvent(map) // library marker kkossev.batteryLib, line 143
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 144
} // library marker kkossev.batteryLib, line 145

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.batteryLib, line 147
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.batteryLib, line 148
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 149
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 150
    sendEvent(map) // library marker kkossev.batteryLib, line 151
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 152
} // library marker kkossev.batteryLib, line 153

public int tuyaToBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 155
    int rawValue = fncmd // library marker kkossev.batteryLib, line 156
    switch (fncmd) { // library marker kkossev.batteryLib, line 157
        case 0: rawValue = 100; break // Battery Full // library marker kkossev.batteryLib, line 158
        case 1: rawValue = 75;  break // Battery High // library marker kkossev.batteryLib, line 159
        case 2: rawValue = 50;  break // Battery Medium // library marker kkossev.batteryLib, line 160
        case 3: rawValue = 25;  break // Battery Low // library marker kkossev.batteryLib, line 161
        case 4: rawValue = 100; break // Tuya 3 in 1 -> USB powered // library marker kkossev.batteryLib, line 162
        // for all other values >4 we will use the raw value, expected to be the real battery level 4..100% // library marker kkossev.batteryLib, line 163
    } // library marker kkossev.batteryLib, line 164
    return rawValue // library marker kkossev.batteryLib, line 165
} // library marker kkossev.batteryLib, line 166

public void handleTuyaBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 168
    int rawValue = tuyaToBatteryLevel(fncmd) // library marker kkossev.batteryLib, line 169
    sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 170
} // library marker kkossev.batteryLib, line 171

public void batteryInitializeVars( boolean fullInit = false ) { // library marker kkossev.batteryLib, line 173
    logDebug "batteryInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.batteryLib, line 174
    if (device.hasCapability('Battery')) { // library marker kkossev.batteryLib, line 175
        if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.batteryLib, line 176
        if (fullInit || settings?.batteryDelay == null) { device.updateSetting('batteryDelay', [value: DelayBatteryOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.batteryLib, line 177
    } // library marker kkossev.batteryLib, line 178
} // library marker kkossev.batteryLib, line 179

public List<String> batteryRefresh() { // library marker kkossev.batteryLib, line 181
    List<String> cmds = [] // library marker kkossev.batteryLib, line 182
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 100)         // battery voltage // library marker kkossev.batteryLib, line 183
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 100)         // battery percentage // library marker kkossev.batteryLib, line 184
    return cmds // library marker kkossev.batteryLib, line 185
} // library marker kkossev.batteryLib, line 186

// ~~~~~ end include (171) kkossev.batteryLib ~~~~~

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
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/deviceProfileLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-deviceProfileLib', // library marker kkossev.deviceProfileLib, line 4
    version: '3.4.2' // library marker kkossev.deviceProfileLib, line 5
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
 * ver. 3.3.4  2024-09-28 kkossev  - fixed exceptions in resetPreferencesToDefaults() and initEventsDeviceProfile() // library marker kkossev.deviceProfileLib, line 34
 * ver. 3.4.0  2025-02-02 kkossev  - deviceProfilesV3 optimizations (defaultFingerprint); is2in1() mod // library marker kkossev.deviceProfileLib, line 35
 * ver. 3.4.1  2025-02-02 kkossev  - setPar help improvements; // library marker kkossev.deviceProfileLib, line 36
 * ver. 3.4.2  2025-03-24 kkossev  - added refreshFromConfigureReadList() method; documentation update; getDeviceNameAndProfile uses DEVICE.description instead of deviceJoinName // library marker kkossev.deviceProfileLib, line 37
 * ver. 3.4.3  2025-04-25 kkossev  - HE platfrom version 2.4.1.x decimal preferences patch/workaround. // library marker kkossev.deviceProfileLib, line 38
 * // library marker kkossev.deviceProfileLib, line 39
 *                                   TODO - remove the 2-in-1 patch ! // library marker kkossev.deviceProfileLib, line 40
 *                                   TODO - add updateStateUnknownDPs (from the 4-in-1 driver) // library marker kkossev.deviceProfileLib, line 41
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 42
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 43
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off) // library marker kkossev.deviceProfileLib, line 44
 *                                   TODO: allow NULL parameters default values in the device profiles // library marker kkossev.deviceProfileLib, line 45
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 46
 * // library marker kkossev.deviceProfileLib, line 47
*/ // library marker kkossev.deviceProfileLib, line 48

static String deviceProfileLibVersion()   { '3.4.3' } // library marker kkossev.deviceProfileLib, line 50
static String deviceProfileLibStamp() { '2025/04/25 12:43 PM' } // library marker kkossev.deviceProfileLib, line 51
import groovy.json.* // library marker kkossev.deviceProfileLib, line 52
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 53
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 54
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 55
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 56

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 58

metadata { // library marker kkossev.deviceProfileLib, line 60
    // no capabilities // library marker kkossev.deviceProfileLib, line 61
    // no attributes // library marker kkossev.deviceProfileLib, line 62
    /* // library marker kkossev.deviceProfileLib, line 63
    // copy the following commands to the main driver, if needed // library marker kkossev.deviceProfileLib, line 64
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 65
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 66
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 67
    ] // library marker kkossev.deviceProfileLib, line 68
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 69
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 70
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 71
    ] // library marker kkossev.deviceProfileLib, line 72
    */ // library marker kkossev.deviceProfileLib, line 73
    preferences { // library marker kkossev.deviceProfileLib, line 74
        if (device) { // library marker kkossev.deviceProfileLib, line 75
            input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 76
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 77
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 78
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 79
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 80
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 81
                        input inputMap // library marker kkossev.deviceProfileLib, line 82
                    } // library marker kkossev.deviceProfileLib, line 83
                } // library marker kkossev.deviceProfileLib, line 84
            } // library marker kkossev.deviceProfileLib, line 85
        } // library marker kkossev.deviceProfileLib, line 86
    } // library marker kkossev.deviceProfileLib, line 87
} // library marker kkossev.deviceProfileLib, line 88

private boolean is2in1() { return getDeviceProfile().startsWith('TS0601_2IN1')  }   // patch! // library marker kkossev.deviceProfileLib, line 90

public String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 92
public Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 93
public Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 94

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
public String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 117
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
private Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 130
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

public Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 161
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
public void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 190
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
private List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 218
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 219
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 220
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 221
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 222
    } // library marker kkossev.deviceProfileLib, line 223
    return validPars // library marker kkossev.deviceProfileLib, line 224
} // library marker kkossev.deviceProfileLib, line 225

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 227
private def getScaledPreferenceValue(String preference, Map dpMap) {        // TODO - not used ??? // library marker kkossev.deviceProfileLib, line 228
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
        else { logDebug "warning: couldn't find map for preference ${name}" ; return }  // TODO - supress the warning if the preference was boolean true/false // library marker kkossev.deviceProfileLib, line 296
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
private List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 320
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
private def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 356
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
    if (scaledValue == null) { // library marker kkossev.deviceProfileLib, line 457
        log.trace "$dpMap  ${dpMap.map}" // library marker kkossev.deviceProfileLib, line 458
        String helpTxt = "setPar: invalid parameter ${par} value <b>${val}</b>." // library marker kkossev.deviceProfileLib, line 459
        if (dpMap.min != null && dpMap.max != null) { helpTxt += " Must be in the range ${dpMap.min} to ${dpMap.max}" } // library marker kkossev.deviceProfileLib, line 460
        if (dpMap.map != null) { helpTxt += " Must be one of ${dpMap.map}" } // library marker kkossev.deviceProfileLib, line 461
        logInfo helpTxt // library marker kkossev.deviceProfileLib, line 462
        return false // library marker kkossev.deviceProfileLib, line 463
    } // library marker kkossev.deviceProfileLib, line 464

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 466
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 467
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 468
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 469
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 470
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 471
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 472
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 473
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 474
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 475
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 476
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 477
            return true // library marker kkossev.deviceProfileLib, line 478
        } // library marker kkossev.deviceProfileLib, line 479
        else { // library marker kkossev.deviceProfileLib, line 480
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 481
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 482
        } // library marker kkossev.deviceProfileLib, line 483
    } // library marker kkossev.deviceProfileLib, line 484
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 485
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 486
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 487
        def valMiscType // library marker kkossev.deviceProfileLib, line 488
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 489
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 490
            // find the key for the value // library marker kkossev.deviceProfileLib, line 491
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 492
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 493
            if (key == null) { // library marker kkossev.deviceProfileLib, line 494
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 495
                return false // library marker kkossev.deviceProfileLib, line 496
            } // library marker kkossev.deviceProfileLib, line 497
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 498
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 499
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 500
        } // library marker kkossev.deviceProfileLib, line 501
        else { // library marker kkossev.deviceProfileLib, line 502
            valMiscType = val // library marker kkossev.deviceProfileLib, line 503
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 504
        } // library marker kkossev.deviceProfileLib, line 505
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 506
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 507
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 508
        return true // library marker kkossev.deviceProfileLib, line 509
    } // library marker kkossev.deviceProfileLib, line 510

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 512
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 513

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 515
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 516
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 517
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 518
        // Tuya DP // library marker kkossev.deviceProfileLib, line 519
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 520
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 521
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 522
            return false // library marker kkossev.deviceProfileLib, line 523
        } // library marker kkossev.deviceProfileLib, line 524
        else { // library marker kkossev.deviceProfileLib, line 525
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 526
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 527
            return false // library marker kkossev.deviceProfileLib, line 528
        } // library marker kkossev.deviceProfileLib, line 529
    } // library marker kkossev.deviceProfileLib, line 530
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 531
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 532
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 533
        int signedIntScaled = convertSignedInts(scaledValue, dpMap) // library marker kkossev.deviceProfileLib, line 534
        cmds = zclWriteAttribute(dpMap, signedIntScaled) // library marker kkossev.deviceProfileLib, line 535
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 536
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 537
            return false // library marker kkossev.deviceProfileLib, line 538
        } // library marker kkossev.deviceProfileLib, line 539
    } // library marker kkossev.deviceProfileLib, line 540
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 541
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 542
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 543
    return true // library marker kkossev.deviceProfileLib, line 544
} // library marker kkossev.deviceProfileLib, line 545

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 547
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 548
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 549
public List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 550
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 551
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 552
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 553
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 554
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 555
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 556
        return [] // library marker kkossev.deviceProfileLib, line 557
    } // library marker kkossev.deviceProfileLib, line 558
    String dpType // library marker kkossev.deviceProfileLib, line 559
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 560
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 561
    } // library marker kkossev.deviceProfileLib, line 562
    else { // library marker kkossev.deviceProfileLib, line 563
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 564
    } // library marker kkossev.deviceProfileLib, line 565
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 566
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 567
        return [] // library marker kkossev.deviceProfileLib, line 568
    } // library marker kkossev.deviceProfileLib, line 569
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 570
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 571
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 572
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 573
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 574
    } // library marker kkossev.deviceProfileLib, line 575
    else { // library marker kkossev.deviceProfileLib, line 576
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 577
    } // library marker kkossev.deviceProfileLib, line 578
    return cmds // library marker kkossev.deviceProfileLib, line 579
} // library marker kkossev.deviceProfileLib, line 580

private int convertSignedInts(int val, Map dpMap) { // library marker kkossev.deviceProfileLib, line 582
    if (dpMap.dt == '0x28') { // library marker kkossev.deviceProfileLib, line 583
        if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 584
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 585
    } // library marker kkossev.deviceProfileLib, line 586
    else if (dpMap.dt == '0x29') { // library marker kkossev.deviceProfileLib, line 587
        if (val > 32767) { return (val as int) - 65536 } // library marker kkossev.deviceProfileLib, line 588
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 589
    } // library marker kkossev.deviceProfileLib, line 590
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 591
} // library marker kkossev.deviceProfileLib, line 592

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 594
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 595
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 596
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 597
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 598
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'DEVICE.preferences is empty!' ; return false } // library marker kkossev.deviceProfileLib, line 599

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 601
    l//log.trace "sendAttribute: dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 602
    if (dpMap == null || dpMap?.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 603
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 604
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 605
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 606
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 607
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 608
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 609
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 610
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 611
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 612
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 613
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 614
        try { // library marker kkossev.deviceProfileLib, line 615
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 616
        } // library marker kkossev.deviceProfileLib, line 617
        catch (e) { // library marker kkossev.deviceProfileLib, line 618
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 619
            return false // library marker kkossev.deviceProfileLib, line 620
        } // library marker kkossev.deviceProfileLib, line 621
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 622
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 623
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 624
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 625
            return true // library marker kkossev.deviceProfileLib, line 626
        } // library marker kkossev.deviceProfileLib, line 627
        else { // library marker kkossev.deviceProfileLib, line 628
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 629
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 630
        } // library marker kkossev.deviceProfileLib, line 631
    } // library marker kkossev.deviceProfileLib, line 632
    else { // library marker kkossev.deviceProfileLib, line 633
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 634
    } // library marker kkossev.deviceProfileLib, line 635
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 636
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 637
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 638
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 639
        // patch !! // library marker kkossev.deviceProfileLib, line 640
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 641
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 642
        } // library marker kkossev.deviceProfileLib, line 643
        else { // library marker kkossev.deviceProfileLib, line 644
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 645
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 646
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 647
        } // library marker kkossev.deviceProfileLib, line 648
        return true // library marker kkossev.deviceProfileLib, line 649
    } // library marker kkossev.deviceProfileLib, line 650
    else { // library marker kkossev.deviceProfileLib, line 651
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 652
    } // library marker kkossev.deviceProfileLib, line 653
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 654
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 655
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 656
    try { // library marker kkossev.deviceProfileLib, line 657
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 658
    } // library marker kkossev.deviceProfileLib, line 659
    catch (e) { // library marker kkossev.deviceProfileLib, line 660
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 661
        return false // library marker kkossev.deviceProfileLib, line 662
    } // library marker kkossev.deviceProfileLib, line 663
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 664
        // Tuya DP // library marker kkossev.deviceProfileLib, line 665
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 666
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 667
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 668
            return false // library marker kkossev.deviceProfileLib, line 669
        } // library marker kkossev.deviceProfileLib, line 670
        else { // library marker kkossev.deviceProfileLib, line 671
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 672
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 673
            return true // library marker kkossev.deviceProfileLib, line 674
        } // library marker kkossev.deviceProfileLib, line 675
    } // library marker kkossev.deviceProfileLib, line 676
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 677
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 678
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 679
    } // library marker kkossev.deviceProfileLib, line 680
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 681
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 682
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 683
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 684
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 685
            return false // library marker kkossev.deviceProfileLib, line 686
        } // library marker kkossev.deviceProfileLib, line 687
    } // library marker kkossev.deviceProfileLib, line 688
    else { // library marker kkossev.deviceProfileLib, line 689
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 690
        return false // library marker kkossev.deviceProfileLib, line 691
    } // library marker kkossev.deviceProfileLib, line 692
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 693
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 694
    return true // library marker kkossev.deviceProfileLib, line 695
} // library marker kkossev.deviceProfileLib, line 696

/** // library marker kkossev.deviceProfileLib, line 698
 * SENDS a list of Zigbee commands to be sent to the device. // library marker kkossev.deviceProfileLib, line 699
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 700
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 701
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 702
 */ // library marker kkossev.deviceProfileLib, line 703
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 704
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 705
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 706
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 707
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 708
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 709
    if (supportedCommandsMap == null || supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 710
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 711
        return false // library marker kkossev.deviceProfileLib, line 712
    } // library marker kkossev.deviceProfileLib, line 713
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 714
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 715
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 716
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 717
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 718
        return false // library marker kkossev.deviceProfileLib, line 719
    } // library marker kkossev.deviceProfileLib, line 720
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 721
    def func, funcResult // library marker kkossev.deviceProfileLib, line 722
    try { // library marker kkossev.deviceProfileLib, line 723
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 724
        // added 01/25/2025 : the commands now can be shorted : instead of a map kay and value 'printFingerprints':'printFingerprints' we can skip the value when it is the same:  'printFingerprints:'  - the value is the same as the key // library marker kkossev.deviceProfileLib, line 725
        if (func == null || func == '') { // library marker kkossev.deviceProfileLib, line 726
            func = command // library marker kkossev.deviceProfileLib, line 727
        } // library marker kkossev.deviceProfileLib, line 728
        if (val != null && val != '') { // library marker kkossev.deviceProfileLib, line 729
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 730
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 731
        } // library marker kkossev.deviceProfileLib, line 732
        else { // library marker kkossev.deviceProfileLib, line 733
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 734
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 735
        } // library marker kkossev.deviceProfileLib, line 736
    } // library marker kkossev.deviceProfileLib, line 737
    catch (e) { // library marker kkossev.deviceProfileLib, line 738
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 739
        return false // library marker kkossev.deviceProfileLib, line 740
    } // library marker kkossev.deviceProfileLib, line 741
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 742
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 743
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 744
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 745
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 746
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 747
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 748
        } // library marker kkossev.deviceProfileLib, line 749
    } // library marker kkossev.deviceProfileLib, line 750
    else if (funcResult == null) { // library marker kkossev.deviceProfileLib, line 751
        return false // library marker kkossev.deviceProfileLib, line 752
    } // library marker kkossev.deviceProfileLib, line 753
     else { // library marker kkossev.deviceProfileLib, line 754
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 755
        return false // library marker kkossev.deviceProfileLib, line 756
    } // library marker kkossev.deviceProfileLib, line 757
    return true // library marker kkossev.deviceProfileLib, line 758
} // library marker kkossev.deviceProfileLib, line 759

/** // library marker kkossev.deviceProfileLib, line 761
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 762
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 763
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 764
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 765
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 766
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 767
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 768
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 769
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 770
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 771
 */ // library marker kkossev.deviceProfileLib, line 772
public Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 773
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 774
    Map input = [:] // library marker kkossev.deviceProfileLib, line 775
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 776
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 777
    Object preference // library marker kkossev.deviceProfileLib, line 778
    try { preference = DEVICE?.preferences["$param"] } // library marker kkossev.deviceProfileLib, line 779
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 780
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 781
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } } // library marker kkossev.deviceProfileLib, line 782
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 783
    /* // library marker kkossev.deviceProfileLib, line 784
    // TODO - check if this is neccessary? isTuyaDP is not defined! // library marker kkossev.deviceProfileLib, line 785
    try { isTuyaDP = preference.isNumber() } // library marker kkossev.deviceProfileLib, line 786
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 787
    */ // library marker kkossev.deviceProfileLib, line 788
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 789
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 790
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 791
    if (foundMap == null || foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 792
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 793
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) { // library marker kkossev.deviceProfileLib, line 794
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" } // library marker kkossev.deviceProfileLib, line 795
        return [:] // library marker kkossev.deviceProfileLib, line 796
    } // library marker kkossev.deviceProfileLib, line 797
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 798
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 799
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 800
    //input.description = (foundMap.description ?: foundMap.title)?.replaceAll(/<\/?b>/, '')  // if description is not defined, use the title // library marker kkossev.deviceProfileLib, line 801
    input.description = foundMap.description ?: ''   // if description is not defined, skip it // library marker kkossev.deviceProfileLib, line 802
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 803
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 804
            //input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 805
            input.range = "${Math.ceil(foundMap.min) as int}..${Math.ceil(foundMap.max) as int}" // library marker kkossev.deviceProfileLib, line 806
        } // library marker kkossev.deviceProfileLib, line 807
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 808
            if (input.description != '') { input.description += '<br>' } // library marker kkossev.deviceProfileLib, line 809
            input.description += "<i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 810
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 811
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 812
            } // library marker kkossev.deviceProfileLib, line 813
        } // library marker kkossev.deviceProfileLib, line 814
    } // library marker kkossev.deviceProfileLib, line 815
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 816
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 817
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 818
    }/* // library marker kkossev.deviceProfileLib, line 819
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 820
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 821
    }*/ // library marker kkossev.deviceProfileLib, line 822
    else { // library marker kkossev.deviceProfileLib, line 823
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 824
        return [:] // library marker kkossev.deviceProfileLib, line 825
    } // library marker kkossev.deviceProfileLib, line 826
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 827
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 828
    } // library marker kkossev.deviceProfileLib, line 829
    return input // library marker kkossev.deviceProfileLib, line 830
} // library marker kkossev.deviceProfileLib, line 831

/** // library marker kkossev.deviceProfileLib, line 833
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 834
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 835
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 836
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 837
 */ // library marker kkossev.deviceProfileLib, line 838
public List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 839
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 840
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 841
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 842
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 843
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 844
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 845
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 846
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].description ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 847
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 848
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 849
            } // library marker kkossev.deviceProfileLib, line 850
        } // library marker kkossev.deviceProfileLib, line 851
    } // library marker kkossev.deviceProfileLib, line 852
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 853
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 854
    } // library marker kkossev.deviceProfileLib, line 855
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 856
} // library marker kkossev.deviceProfileLib, line 857

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 859
public void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 860
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 861
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 862
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 863
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 864
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 865
    } // library marker kkossev.deviceProfileLib, line 866
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 867
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 868
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 869
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 870
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 871
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 872
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 873
    } else { // library marker kkossev.deviceProfileLib, line 874
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 875
    } // library marker kkossev.deviceProfileLib, line 876
} // library marker kkossev.deviceProfileLib, line 877

public List<String> refreshFromConfigureReadList(List<String> refreshList) { // library marker kkossev.deviceProfileLib, line 879
    logDebug "refreshFromConfigureReadList(${refreshList})" // library marker kkossev.deviceProfileLib, line 880
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 881
    if (refreshList != null && !refreshList.isEmpty()) { // library marker kkossev.deviceProfileLib, line 882
        //List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 883
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 884
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 885
            if (k != null) { // library marker kkossev.deviceProfileLib, line 886
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 887
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 888
                if (map != null) { // library marker kkossev.deviceProfileLib, line 889
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 890
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 891
                } // library marker kkossev.deviceProfileLib, line 892
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 893
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 894
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 895
                } // library marker kkossev.deviceProfileLib, line 896
            } // library marker kkossev.deviceProfileLib, line 897
        } // library marker kkossev.deviceProfileLib, line 898
    } // library marker kkossev.deviceProfileLib, line 899
    return cmds // library marker kkossev.deviceProfileLib, line 900
} // library marker kkossev.deviceProfileLib, line 901

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLib, line 903
public List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLib, line 904
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLib, line 905
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 906
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLib, line 907
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 908
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 909
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 910
            if (k != null) { // library marker kkossev.deviceProfileLib, line 911
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 912
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 913
                if (map != null) { // library marker kkossev.deviceProfileLib, line 914
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 915
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 916
                } // library marker kkossev.deviceProfileLib, line 917
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 918
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 919
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 920
                } // library marker kkossev.deviceProfileLib, line 921
            } // library marker kkossev.deviceProfileLib, line 922
        } // library marker kkossev.deviceProfileLib, line 923
    } // library marker kkossev.deviceProfileLib, line 924
    return cmds // library marker kkossev.deviceProfileLib, line 925
} // library marker kkossev.deviceProfileLib, line 926

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 928
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 929
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 930
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 931
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 932
    return cmds // library marker kkossev.deviceProfileLib, line 933
} // library marker kkossev.deviceProfileLib, line 934

// TODO ! - remove? // library marker kkossev.deviceProfileLib, line 936
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 937
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 938
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 939
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 940
    return cmds // library marker kkossev.deviceProfileLib, line 941
} // library marker kkossev.deviceProfileLib, line 942

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 944
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 945
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 946
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 947
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 948
    return cmds // library marker kkossev.deviceProfileLib, line 949
} // library marker kkossev.deviceProfileLib, line 950

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 952
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 953
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 954
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 955
    } // library marker kkossev.deviceProfileLib, line 956
} // library marker kkossev.deviceProfileLib, line 957

public void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 959
    String ps = DEVICE?.device?.powerSource // library marker kkossev.deviceProfileLib, line 960
    logDebug "initEventsDeviceProfile(${fullInit}) for deviceProfile=${state.deviceProfile} DEVICE?.device?.powerSource=${ps} ps.isEmpty()=${ps?.isEmpty()}" // library marker kkossev.deviceProfileLib, line 961
    if (ps != null && !ps.isEmpty()) { // library marker kkossev.deviceProfileLib, line 962
        sendEvent(name: 'powerSource', value: ps, descriptionText: "Power Source set to '${ps}'", type: 'digital') // library marker kkossev.deviceProfileLib, line 963
    } // library marker kkossev.deviceProfileLib, line 964
} // library marker kkossev.deviceProfileLib, line 965

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 967

// // library marker kkossev.deviceProfileLib, line 969
// called from parse() // library marker kkossev.deviceProfileLib, line 970
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profile // library marker kkossev.deviceProfileLib, line 971
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 972
// // library marker kkossev.deviceProfileLib, line 973
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 974
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 975
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 976
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 977
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 978
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 979
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 980
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 981
} // library marker kkossev.deviceProfileLib, line 982

// // library marker kkossev.deviceProfileLib, line 984
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 985
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profile // library marker kkossev.deviceProfileLib, line 986
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 987
// // library marker kkossev.deviceProfileLib, line 988
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 989
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 990
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 991
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 992
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 993
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 994
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 995
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 996
} // library marker kkossev.deviceProfileLib, line 997

// all DPs are spammy - sent periodically! (this function is not used?) // library marker kkossev.deviceProfileLib, line 999
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 1000
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 1001
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 1002
    return isSpammy // library marker kkossev.deviceProfileLib, line 1003
} // library marker kkossev.deviceProfileLib, line 1004

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1006
private List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 1007
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 1008
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 1009
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 1010
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1011
    } // library marker kkossev.deviceProfileLib, line 1012
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1013
} // library marker kkossev.deviceProfileLib, line 1014

private List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 1016
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 1017
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1018
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 1019
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1020
    } // library marker kkossev.deviceProfileLib, line 1021
    else { // library marker kkossev.deviceProfileLib, line 1022
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 1023
    } // library marker kkossev.deviceProfileLib, line 1024
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 1025
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1026
} // library marker kkossev.deviceProfileLib, line 1027

private List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 1029
    Double convertedValue // library marker kkossev.deviceProfileLib, line 1030
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1031
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 1032
    } // library marker kkossev.deviceProfileLib, line 1033
    else { // library marker kkossev.deviceProfileLib, line 1034
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1035
    } // library marker kkossev.deviceProfileLib, line 1036
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1037
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1038
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1039
} // library marker kkossev.deviceProfileLib, line 1040

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1042
private List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1043
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1044
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1045
    def convertedValue // library marker kkossev.deviceProfileLib, line 1046
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1047
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1048
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1049
    } // library marker kkossev.deviceProfileLib, line 1050
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1051
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1052
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1053
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1054
            isEqual = false // library marker kkossev.deviceProfileLib, line 1055
        } // library marker kkossev.deviceProfileLib, line 1056
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1057
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1058
        } // library marker kkossev.deviceProfileLib, line 1059
    } // library marker kkossev.deviceProfileLib, line 1060
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1061
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1062
} // library marker kkossev.deviceProfileLib, line 1063

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1065
private List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1066
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1067
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1068
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1069
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1070
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1071
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1072
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1073
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1074
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1075
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1076
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1077
            break // library marker kkossev.deviceProfileLib, line 1078
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1079
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1080
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1081
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1082
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1083
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1084
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1085
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1086
            } // library marker kkossev.deviceProfileLib, line 1087
            else { // library marker kkossev.deviceProfileLib, line 1088
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1089
            } // library marker kkossev.deviceProfileLib, line 1090
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1091
            break // library marker kkossev.deviceProfileLib, line 1092
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1093
        case 'number' : // library marker kkossev.deviceProfileLib, line 1094
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1095
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1096
            break // library marker kkossev.deviceProfileLib, line 1097
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1098
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1099
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1100
            break // library marker kkossev.deviceProfileLib, line 1101
        default : // library marker kkossev.deviceProfileLib, line 1102
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1103
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1104
    } // library marker kkossev.deviceProfileLib, line 1105
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1106
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1107
    } // library marker kkossev.deviceProfileLib, line 1108
    // // library marker kkossev.deviceProfileLib, line 1109
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1110
} // library marker kkossev.deviceProfileLib, line 1111

// // library marker kkossev.deviceProfileLib, line 1113
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1114
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1115
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1116
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1117
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1118
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1119
// // library marker kkossev.deviceProfileLib, line 1120
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1121
// // library marker kkossev.deviceProfileLib, line 1122
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1123
// // library marker kkossev.deviceProfileLib, line 1124
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1125
private List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1126
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1127
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1128
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1129
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1130
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1131
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1132
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1133
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1134
            break // library marker kkossev.deviceProfileLib, line 1135
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1136
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1137
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1138
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1139
            logTrace "latestEvent is ${latestEvent} dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1140
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1141
            if (dataType == null || dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1142
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1143
            } // library marker kkossev.deviceProfileLib, line 1144
            else { // library marker kkossev.deviceProfileLib, line 1145
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1146
            } // library marker kkossev.deviceProfileLib, line 1147
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1148
            break // library marker kkossev.deviceProfileLib, line 1149
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1150
        case 'number' : // library marker kkossev.deviceProfileLib, line 1151
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1152
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1153
            break // library marker kkossev.deviceProfileLib, line 1154
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1155
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1156
            break // library marker kkossev.deviceProfileLib, line 1157
        default : // library marker kkossev.deviceProfileLib, line 1158
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1159
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1160
    } // library marker kkossev.deviceProfileLib, line 1161
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1162
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1163
} // library marker kkossev.deviceProfileLib, line 1164

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1166
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1167
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1168
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1169
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1170
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1171
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1172
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1173
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1174
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1175
    } // library marker kkossev.deviceProfileLib, line 1176
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1177
    try { // library marker kkossev.deviceProfileLib, line 1178
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1179
    } // library marker kkossev.deviceProfileLib, line 1180
    catch (e) { // library marker kkossev.deviceProfileLib, line 1181
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1182
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1183
    } // library marker kkossev.deviceProfileLib, line 1184
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1185
    return fncmd // library marker kkossev.deviceProfileLib, line 1186
} // library marker kkossev.deviceProfileLib, line 1187

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1189
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1190
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1191
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1192
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1193
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1194
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1195

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1197
    if (attribMap == null || attribMap?.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1198

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1200
    int value // library marker kkossev.deviceProfileLib, line 1201
    try { // library marker kkossev.deviceProfileLib, line 1202
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1203
    } // library marker kkossev.deviceProfileLib, line 1204
    catch (e) { // library marker kkossev.deviceProfileLib, line 1205
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1206
        return false // library marker kkossev.deviceProfileLib, line 1207
    } // library marker kkossev.deviceProfileLib, line 1208
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1209
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1210
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1211
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1212
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1213
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1214
        return false // library marker kkossev.deviceProfileLib, line 1215
    } // library marker kkossev.deviceProfileLib, line 1216
    value = convertSignedInts(value, foundItem) // library marker kkossev.deviceProfileLib, line 1217
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1218
} // library marker kkossev.deviceProfileLib, line 1219

/** // library marker kkossev.deviceProfileLib, line 1221
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1222
 * // library marker kkossev.deviceProfileLib, line 1223
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1224
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1225
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1226
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1227
 * // library marker kkossev.deviceProfileLib, line 1228
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1229
 */ // library marker kkossev.deviceProfileLib, line 1230
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1231
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1232
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1233
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1234
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1235

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1237
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1238

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1240
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1241
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1242
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1243
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1244
        return false // library marker kkossev.deviceProfileLib, line 1245
    } // library marker kkossev.deviceProfileLib, line 1246
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1247
} // library marker kkossev.deviceProfileLib, line 1248

/* // library marker kkossev.deviceProfileLib, line 1250
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1251
 */ // library marker kkossev.deviceProfileLib, line 1252
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1253
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1254
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1255
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1256
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1257
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1258
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1259
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1260
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1261
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1262
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1263
        } // library marker kkossev.deviceProfileLib, line 1264
    } // library marker kkossev.deviceProfileLib, line 1265
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1266

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1268
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1269
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1270
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1271
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1272
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences?.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1273
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1274
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1275
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1276
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1277
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1278
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1279
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1280
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1281
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1282
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1283
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1284

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1286
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1287
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1288
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1289
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1290
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1291
            if (settings.logEnable) { logInfo "${descText} (Debug logging is enabled)" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1292
        } // library marker kkossev.deviceProfileLib, line 1293
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1294
    } // library marker kkossev.deviceProfileLib, line 1295

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1297
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1298
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1299
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1300
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1301
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1302
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1303
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1304
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1305
            } // library marker kkossev.deviceProfileLib, line 1306
        } // library marker kkossev.deviceProfileLib, line 1307
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1308
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1309
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1310
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1311
            } // library marker kkossev.deviceProfileLib, line 1312
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1313
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1314
            try { // library marker kkossev.deviceProfileLib, line 1315
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1316
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1317
            } // library marker kkossev.deviceProfileLib, line 1318
            catch (e) { // library marker kkossev.deviceProfileLib, line 1319
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1320
            } // library marker kkossev.deviceProfileLib, line 1321
        } // library marker kkossev.deviceProfileLib, line 1322
    } // library marker kkossev.deviceProfileLib, line 1323
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1324
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1325
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1326
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1327
    } // library marker kkossev.deviceProfileLib, line 1328

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1330
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1331
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1332
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1333
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1334
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1335
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1336
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1337
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1338
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1339
            } // library marker kkossev.deviceProfileLib, line 1340
            if (foundItem.processDuplicated == true) { // library marker kkossev.deviceProfileLib, line 1341
                logDebug 'processDuplicated=true -> continue' // library marker kkossev.deviceProfileLib, line 1342
            } // library marker kkossev.deviceProfileLib, line 1343

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1345
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1346
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1347
            // continue ... // library marker kkossev.deviceProfileLib, line 1348
            } // library marker kkossev.deviceProfileLib, line 1349

            else { // library marker kkossev.deviceProfileLib, line 1351
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1352
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1353
                } // library marker kkossev.deviceProfileLib, line 1354
                else { // library marker kkossev.deviceProfileLib, line 1355
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLib, line 1356
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1357
                } // library marker kkossev.deviceProfileLib, line 1358
            } // library marker kkossev.deviceProfileLib, line 1359
        } // library marker kkossev.deviceProfileLib, line 1360

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1362
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1363
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1364
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1365
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1366
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1367
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1368
        } // library marker kkossev.deviceProfileLib, line 1369
        else { // library marker kkossev.deviceProfileLib, line 1370
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1371
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1372
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1373
                logTrace "event ${name} sent w/ valueScaled ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1374
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1375
            } // library marker kkossev.deviceProfileLib, line 1376
        } // library marker kkossev.deviceProfileLib, line 1377
    } // library marker kkossev.deviceProfileLib, line 1378
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1379
} // library marker kkossev.deviceProfileLib, line 1380

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1382
public boolean validateAndFixPreferences(String debugStr) { return validateAndFixPreferences(debugStr.toBoolean() as boolean) } // library marker kkossev.deviceProfileLib, line 1383
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1384
    //debug = true // library marker kkossev.deviceProfileLib, line 1385
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1386
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLib, line 1387
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLib, line 1388
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1389
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLib, line 1390
    String settingType = '' // library marker kkossev.deviceProfileLib, line 1391
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1392
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1393
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1394
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1395
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1396
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1397
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1398
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1399
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1400
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1401
            try { // library marker kkossev.deviceProfileLib, line 1402
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1403
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1404
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLib, line 1405
            } // library marker kkossev.deviceProfileLib, line 1406
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1407
            try { // library marker kkossev.deviceProfileLib, line 1408
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1409
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1410
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1411
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1412
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1413
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1414
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1415
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1416
                    } // library marker kkossev.deviceProfileLib, line 1417
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1418
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1419
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1420
                    } else { // library marker kkossev.deviceProfileLib, line 1421
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1422
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1423
                    } // library marker kkossev.deviceProfileLib, line 1424
                } // library marker kkossev.deviceProfileLib, line 1425
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1426
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1427
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1428
            } // library marker kkossev.deviceProfileLib, line 1429
            catch (e) { // library marker kkossev.deviceProfileLib, line 1430
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1431
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1432
                try { // library marker kkossev.deviceProfileLib, line 1433
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1434
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1435
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1436
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1437
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1438
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLib, line 1439
                } // library marker kkossev.deviceProfileLib, line 1440
            } // library marker kkossev.deviceProfileLib, line 1441
        } // library marker kkossev.deviceProfileLib, line 1442
        total ++ // library marker kkossev.deviceProfileLib, line 1443
    } // library marker kkossev.deviceProfileLib, line 1444
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1445
    return true // library marker kkossev.deviceProfileLib, line 1446
} // library marker kkossev.deviceProfileLib, line 1447

public String fingerprintIt(Map profileMap, Map fingerprint) { // library marker kkossev.deviceProfileLib, line 1449
    if (profileMap == null) { return 'profileMap is null' } // library marker kkossev.deviceProfileLib, line 1450
    if (fingerprint == null) { return 'fingerprint is null' } // library marker kkossev.deviceProfileLib, line 1451
    Map defaultFingerprint = profileMap.defaultFingerprint ?: [:] // library marker kkossev.deviceProfileLib, line 1452
    // if there is no defaultFingerprint, use the fingerprint as is // library marker kkossev.deviceProfileLib, line 1453
    if (defaultFingerprint == [:]) { // library marker kkossev.deviceProfileLib, line 1454
        return fingerprint.toString() // library marker kkossev.deviceProfileLib, line 1455
    } // library marker kkossev.deviceProfileLib, line 1456
    // for the missing keys, use the default values // library marker kkossev.deviceProfileLib, line 1457
    String fingerprintStr = '' // library marker kkossev.deviceProfileLib, line 1458
    defaultFingerprint.each { key, value -> // library marker kkossev.deviceProfileLib, line 1459
        String keyValue = fingerprint[key] ?: value // library marker kkossev.deviceProfileLib, line 1460
        fingerprintStr += "${key}:'${keyValue}', " // library marker kkossev.deviceProfileLib, line 1461
    } // library marker kkossev.deviceProfileLib, line 1462
    // remove the last comma and space // library marker kkossev.deviceProfileLib, line 1463
    fingerprintStr = fingerprintStr[0..-3] // library marker kkossev.deviceProfileLib, line 1464
    return fingerprintStr // library marker kkossev.deviceProfileLib, line 1465
} // library marker kkossev.deviceProfileLib, line 1466

public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1468
    int count = 0 // library marker kkossev.deviceProfileLib, line 1469
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1470
        logInfo "Device Profile: ${profileName}" // library marker kkossev.deviceProfileLib, line 1471
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1472
            log.info "${fingerprintIt(profileMap, fingerprint)}" // library marker kkossev.deviceProfileLib, line 1473
            count++ // library marker kkossev.deviceProfileLib, line 1474
        } // library marker kkossev.deviceProfileLib, line 1475
    } // library marker kkossev.deviceProfileLib, line 1476
    logInfo "Total fingerprints: ${count}" // library marker kkossev.deviceProfileLib, line 1477
} // library marker kkossev.deviceProfileLib, line 1478

public void printPreferences() { // library marker kkossev.deviceProfileLib, line 1480
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 1481
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 1482
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 1483
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLib, line 1484
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 1485
                log.info inputMap // library marker kkossev.deviceProfileLib, line 1486
            } // library marker kkossev.deviceProfileLib, line 1487
        } // library marker kkossev.deviceProfileLib, line 1488
    } // library marker kkossev.deviceProfileLib, line 1489
} // library marker kkossev.deviceProfileLib, line 1490

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~

// ~~~~~ start include (179) kkossev.thermostatLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.thermostatLib, line 1
library( // library marker kkossev.thermostatLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Thermostat Library', name: 'thermostatLib', namespace: 'kkossev', // library marker kkossev.thermostatLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/thermostatLib.groovy', documentationLink: '', // library marker kkossev.thermostatLib, line 4
    version: '3.5.1') // library marker kkossev.thermostatLib, line 5
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
 * ver. 3.5.1  2025-03-04 kkossev  - (dev. branch) == false bug fix; disabled switching to 'cool' mode. // library marker kkossev.thermostatLib, line 23
 * // library marker kkossev.thermostatLib, line 24
 *                                   TODO: add eco() method // library marker kkossev.thermostatLib, line 25
 *                                   TODO: refactor sendHeatingSetpointEvent // library marker kkossev.thermostatLib, line 26
*/ // library marker kkossev.thermostatLib, line 27

public static String thermostatLibVersion()   { '3.5.1' } // library marker kkossev.thermostatLib, line 29
public static String thermostatLibStamp() { '2025/03/04 9:11 PM' } // library marker kkossev.thermostatLib, line 30

metadata { // library marker kkossev.thermostatLib, line 32
    capability 'Actuator'           // also in onOffLib // library marker kkossev.thermostatLib, line 33
    capability 'Sensor' // library marker kkossev.thermostatLib, line 34
    capability 'Thermostat'                 // needed for HomeKit // library marker kkossev.thermostatLib, line 35
                // coolingSetpoint - NUMBER; heatingSetpoint - NUMBER; supportedThermostatFanModes - JSON_OBJECT; supportedThermostatModes - JSON_OBJECT; temperature - NUMBER, unit:°F || °C; thermostatFanMode - ENUM ["on", "circulate", "auto"] // library marker kkossev.thermostatLib, line 36
                // thermostatMode - ENUM ["auto", "off", "heat", "emergency heat", "cool"]; thermostatOperatingState - ENUM ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]; thermostatSetpoint - NUMBER, unit:°F || °C // library marker kkossev.thermostatLib, line 37
    capability 'ThermostatHeatingSetpoint' // library marker kkossev.thermostatLib, line 38
    capability 'ThermostatCoolingSetpoint' // library marker kkossev.thermostatLib, line 39
    capability 'ThermostatOperatingState'   // thermostatOperatingState - ENUM ["vent economizer", "pending cool", "cooling", "heating", "pending heat", "fan only", "idle"] // library marker kkossev.thermostatLib, line 40
    capability 'ThermostatSetpoint' // library marker kkossev.thermostatLib, line 41
    capability 'ThermostatMode' // library marker kkossev.thermostatLib, line 42
    capability 'ThermostatFanMode' // library marker kkossev.thermostatLib, line 43
    // no attributes // library marker kkossev.thermostatLib, line 44

    command 'setThermostatMode', [[name: 'thermostat mode (not all are available!)', type: 'ENUM', constraints: ['--- select ---'] + AllPossibleThermostatModesOpts.options.values() as List<String>]] // library marker kkossev.thermostatLib, line 46
    //    command 'setTemperature', ['NUMBER']                        // Virtual thermostat  TODO - decide if it is needed // library marker kkossev.thermostatLib, line 47

    preferences { // library marker kkossev.thermostatLib, line 49
        if (device) { // TODO -  move it to the deviceProfile preferences // library marker kkossev.thermostatLib, line 50
            input name: 'temperaturePollingInterval', type: 'enum', title: '<b>Temperature polling interval</b>', options: TrvTemperaturePollingIntervalOpts.options, defaultValue: TrvTemperaturePollingIntervalOpts.defaultValue, required: true, description: 'Changes how often the hub will poll the TRV for faster temperature reading updates and nice looking graphs.' // library marker kkossev.thermostatLib, line 51
        } // library marker kkossev.thermostatLib, line 52
    } // library marker kkossev.thermostatLib, line 53
} // library marker kkossev.thermostatLib, line 54

@Field static final Map TrvTemperaturePollingIntervalOpts = [ // library marker kkossev.thermostatLib, line 56
    defaultValue: 600, // library marker kkossev.thermostatLib, line 57
    options     : [0: 'Disabled', 60: 'Every minute (not recommended)', 120: 'Every 2 minutes', 300: 'Every 5 minutes', 600: 'Every 10 minutes', 900: 'Every 15 minutes', 1800: 'Every 30 minutes', 3600: 'Every 1 hour'] // library marker kkossev.thermostatLib, line 58
] // library marker kkossev.thermostatLib, line 59

@Field static final Map AllPossibleThermostatModesOpts = [ // library marker kkossev.thermostatLib, line 61
    defaultValue: 1, // library marker kkossev.thermostatLib, line 62
    options     : [0: 'off', 1: 'heat', 2: 'cool', 3: 'auto', 4: 'emergency heat', 5: 'eco'] // library marker kkossev.thermostatLib, line 63
] // library marker kkossev.thermostatLib, line 64

public void heat() { setThermostatMode('heat') } // library marker kkossev.thermostatLib, line 66
public void auto() { setThermostatMode('auto') } // library marker kkossev.thermostatLib, line 67
public void cool() { setThermostatMode('cool') } // library marker kkossev.thermostatLib, line 68
public void emergencyHeat() { setThermostatMode('emergency heat') } // library marker kkossev.thermostatLib, line 69
public void eco() { setThermostatMode('eco') } // library marker kkossev.thermostatLib, line 70

public void setThermostatFanMode(final String fanMode) { sendEvent(name: 'thermostatFanMode', value: "${fanMode}", descriptionText: getDescriptionText("thermostatFanMode is ${fanMode}")) } // library marker kkossev.thermostatLib, line 72
public void fanAuto() { setThermostatFanMode('auto') } // library marker kkossev.thermostatLib, line 73
public void fanCirculate() { setThermostatFanMode('circulate') } // library marker kkossev.thermostatLib, line 74
public void fanOn() { setThermostatFanMode('on') } // library marker kkossev.thermostatLib, line 75

public void customOff() { setThermostatMode('off') }    // invoked from the common library // library marker kkossev.thermostatLib, line 77
public void customOn()  { setThermostatMode('heat') }   // invoked from the common library // library marker kkossev.thermostatLib, line 78

/* // library marker kkossev.thermostatLib, line 80
 * ----------------------------------------------------------------------------- // library marker kkossev.thermostatLib, line 81
 * thermostat cluster 0x0201 // library marker kkossev.thermostatLib, line 82
 * ----------------------------------------------------------------------------- // library marker kkossev.thermostatLib, line 83
*/ // library marker kkossev.thermostatLib, line 84
// * should be implemented in the custom driver code ... // library marker kkossev.thermostatLib, line 85
public void standardParseThermostatCluster(final Map descMap) { // library marker kkossev.thermostatLib, line 86
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value)) // library marker kkossev.thermostatLib, line 87
    logTrace "standardParseThermostatCluster: zigbee received Thermostat cluster (0x0201) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})" // library marker kkossev.thermostatLib, line 88
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return } // library marker kkossev.thermostatLib, line 89
    if (deviceProfilesV3 != null) { // library marker kkossev.thermostatLib, line 90
        boolean result = processClusterAttributeFromDeviceProfile(descMap) // library marker kkossev.thermostatLib, line 91
        if ( result == false ) { // library marker kkossev.thermostatLib, line 92
            logWarn "standardParseThermostatCluster: received unknown Thermostat cluster (0x0201) attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.thermostatLib, line 93
        } // library marker kkossev.thermostatLib, line 94
    } // library marker kkossev.thermostatLib, line 95
    // try to process the attribute value // library marker kkossev.thermostatLib, line 96
    standardHandleThermostatEvent(value) // library marker kkossev.thermostatLib, line 97
} // library marker kkossev.thermostatLib, line 98

//  setHeatingSetpoint thermostat capability standard command // library marker kkossev.thermostatLib, line 100
//  1°C steps. (0.5°C setting on the TRV itself, rounded for zigbee interface) // library marker kkossev.thermostatLib, line 101
// // library marker kkossev.thermostatLib, line 102
void setHeatingSetpoint(final Number temperaturePar ) { // library marker kkossev.thermostatLib, line 103
    BigDecimal temperature = temperaturePar.toBigDecimal() // library marker kkossev.thermostatLib, line 104
    logTrace "setHeatingSetpoint(${temperature}) called!" // library marker kkossev.thermostatLib, line 105
    BigDecimal previousSetpoint = (device.currentState('heatingSetpoint')?.value ?: 0.0G).toBigDecimal() // library marker kkossev.thermostatLib, line 106
    BigDecimal tempDouble = temperature // library marker kkossev.thermostatLib, line 107
    //logDebug "setHeatingSetpoint temperature = ${temperature}  as int = ${temperature as int} (previousSetpointt = ${previousSetpoint})" // library marker kkossev.thermostatLib, line 108
    /* groovylint-disable-next-line ConstantIfExpression */ // library marker kkossev.thermostatLib, line 109
    if (true) { // library marker kkossev.thermostatLib, line 110
        //logDebug "0.5 C correction of the heating setpoint${temperature}" // library marker kkossev.thermostatLib, line 111
        //log.trace "tempDouble = ${tempDouble}" // library marker kkossev.thermostatLib, line 112
        tempDouble = (tempDouble * 2).setScale(0, RoundingMode.HALF_UP) / 2 // library marker kkossev.thermostatLib, line 113
    } // library marker kkossev.thermostatLib, line 114
    else { // library marker kkossev.thermostatLib, line 115
        if (temperature != (temperature as int)) { // library marker kkossev.thermostatLib, line 116
            if ((temperature as double) > (previousSetpoint as double)) { // library marker kkossev.thermostatLib, line 117
                temperature = (temperature + 0.5 ) as int // library marker kkossev.thermostatLib, line 118
            } // library marker kkossev.thermostatLib, line 119
            else { // library marker kkossev.thermostatLib, line 120
                temperature = temperature as int // library marker kkossev.thermostatLib, line 121
            } // library marker kkossev.thermostatLib, line 122
            logDebug "corrected heating setpoint ${temperature}" // library marker kkossev.thermostatLib, line 123
        } // library marker kkossev.thermostatLib, line 124
        tempDouble = temperature // library marker kkossev.thermostatLib, line 125
    } // library marker kkossev.thermostatLib, line 126
    BigDecimal maxTemp = settings?.maxHeatingSetpoint ? new BigDecimal(settings.maxHeatingSetpoint) : new BigDecimal(50) // library marker kkossev.thermostatLib, line 127
    BigDecimal minTemp = settings?.minHeatingSetpoint ? new BigDecimal(settings.minHeatingSetpoint) : new BigDecimal(5) // library marker kkossev.thermostatLib, line 128
    tempBigDecimal = new BigDecimal(tempDouble) // library marker kkossev.thermostatLib, line 129
    tempBigDecimal = tempDouble.min(maxTemp).max(minTemp).setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.thermostatLib, line 130

    logDebug "setHeatingSetpoint: calling sendAttribute heatingSetpoint ${tempBigDecimal}" // library marker kkossev.thermostatLib, line 132
    sendAttribute('heatingSetpoint', tempBigDecimal as double) // library marker kkossev.thermostatLib, line 133

    // added 02/16/2025 // library marker kkossev.thermostatLib, line 135
    state.lastTx.isSetPointReq = true // library marker kkossev.thermostatLib, line 136
    state.lastTx.setPoint = tempBigDecimal    // BEOK - float value! // library marker kkossev.thermostatLib, line 137
    runIn(3, setpointReceiveCheck) // library marker kkossev.thermostatLib, line 138

} // library marker kkossev.thermostatLib, line 140

// TODO - use sendThermostatEvent instead! // library marker kkossev.thermostatLib, line 142
void sendHeatingSetpointEvent(Number temperature) { // library marker kkossev.thermostatLib, line 143
    tempDouble = safeToDouble(temperature) // library marker kkossev.thermostatLib, line 144
    Map eventMap = [name: 'heatingSetpoint',  value: tempDouble, unit: '\u00B0C', type: 'physical'] // library marker kkossev.thermostatLib, line 145
    eventMap.descriptionText = "heatingSetpoint is ${tempDouble}" // library marker kkossev.thermostatLib, line 146
    if (state.states['isRefresh'] == true) { // library marker kkossev.thermostatLib, line 147
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.thermostatLib, line 148
        eventMap.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 149
    } // library marker kkossev.thermostatLib, line 150
    sendEvent(eventMap) // library marker kkossev.thermostatLib, line 151
    if (eventMap.descriptionText != null) { logInfo "${eventMap.descriptionText}" } // library marker kkossev.thermostatLib, line 152

    eventMap.name = 'thermostatSetpoint' // library marker kkossev.thermostatLib, line 154
    logDebug "sending event ${eventMap}" // library marker kkossev.thermostatLib, line 155
    sendEvent(eventMap) // library marker kkossev.thermostatLib, line 156
    updateDataValue('lastRunningMode', 'heat') // library marker kkossev.thermostatLib, line 157
    // added 02/16/2025 // library marker kkossev.thermostatLib, line 158
    state.lastRx.setPoint = tempDouble // library marker kkossev.thermostatLib, line 159
} // library marker kkossev.thermostatLib, line 160

// thermostat capability standard command // library marker kkossev.thermostatLib, line 162
// do nothing in TRV - just send an event // library marker kkossev.thermostatLib, line 163
void setCoolingSetpoint(Number temperaturePar) { // library marker kkossev.thermostatLib, line 164
    logDebug "setCoolingSetpoint(${temperaturePar}) called!" // library marker kkossev.thermostatLib, line 165
    /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.thermostatLib, line 166
    BigDecimal temperature = Math.round(temperaturePar * 2) / 2 // library marker kkossev.thermostatLib, line 167
    String descText = "coolingSetpoint is set to ${temperature} \u00B0C" // library marker kkossev.thermostatLib, line 168
    sendEvent(name: 'coolingSetpoint', value: temperature, unit: '\u00B0C', descriptionText: descText, type: 'digital') // library marker kkossev.thermostatLib, line 169
    logInfo "${descText}" // library marker kkossev.thermostatLib, line 170
} // library marker kkossev.thermostatLib, line 171

public void sendThermostatEvent(Map eventMap, final boolean isDigital = false) { // library marker kkossev.thermostatLib, line 173
    if (eventMap.descriptionText == null) { eventMap.descriptionText = "${eventName} is ${value}" } // library marker kkossev.thermostatLib, line 174
    if (eventMap.type == null) { eventMap.type = isDigital == true ? 'digital' : 'physical' } // library marker kkossev.thermostatLib, line 175
    if (state.states['isRefresh'] == true) { // library marker kkossev.thermostatLib, line 176
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.thermostatLib, line 177
        eventMap.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 178
    } // library marker kkossev.thermostatLib, line 179
    sendEvent(eventMap) // library marker kkossev.thermostatLib, line 180
    logInfo "${eventMap.descriptionText}" // library marker kkossev.thermostatLib, line 181
} // library marker kkossev.thermostatLib, line 182

private void sendEventMap(final Map event, final boolean isDigital = false) { // library marker kkossev.thermostatLib, line 184
    if (event.descriptionText == null) { // library marker kkossev.thermostatLib, line 185
        event.descriptionText = "${event.name} is ${event.value} ${event.unit ?: ''}" // library marker kkossev.thermostatLib, line 186
    } // library marker kkossev.thermostatLib, line 187
    if (state.states['isRefresh'] == true) { // library marker kkossev.thermostatLib, line 188
        event.descriptionText += ' [refresh]' // library marker kkossev.thermostatLib, line 189
        event.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 190
    } // library marker kkossev.thermostatLib, line 191
    event.type = event.type != null ? event.type : isDigital == true ? 'digital' : 'physical' // library marker kkossev.thermostatLib, line 192
    if (event.type == 'digital') { // library marker kkossev.thermostatLib, line 193
        event.isStateChange = true   // force event to be sent // library marker kkossev.thermostatLib, line 194
        event.descriptionText += ' [digital]' // library marker kkossev.thermostatLib, line 195
    } // library marker kkossev.thermostatLib, line 196
    sendEvent(event) // library marker kkossev.thermostatLib, line 197
    logInfo "${event.descriptionText}" // library marker kkossev.thermostatLib, line 198
} // library marker kkossev.thermostatLib, line 199

private String getDescriptionText(final String msg) { // library marker kkossev.thermostatLib, line 201
    String descriptionText = "${device.displayName} ${msg}" // library marker kkossev.thermostatLib, line 202
    if (settings?.txtEnable) { log.info "${descriptionText}" } // library marker kkossev.thermostatLib, line 203
    return descriptionText // library marker kkossev.thermostatLib, line 204
} // library marker kkossev.thermostatLib, line 205

/** // library marker kkossev.thermostatLib, line 207
 * Sets the thermostat mode based on the requested mode. // library marker kkossev.thermostatLib, line 208
 * // library marker kkossev.thermostatLib, line 209
 * if the requestedMode is supported directly in the thermostatMode attribute, it is set directly. // library marker kkossev.thermostatLib, line 210
 * Otherwise, the thermostatMode is substituted with another command, if supported by the device. // library marker kkossev.thermostatLib, line 211
 * // library marker kkossev.thermostatLib, line 212
 * @param requestedMode The mode to set the thermostat to. // library marker kkossev.thermostatLib, line 213
 */ // library marker kkossev.thermostatLib, line 214
public void setThermostatMode(final String requestedMode) { // library marker kkossev.thermostatLib, line 215
    String mode = requestedMode // library marker kkossev.thermostatLib, line 216
    boolean result = false // library marker kkossev.thermostatLib, line 217
    List nativelySupportedModesList = getAttributesMap('thermostatMode')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 218
    List systemModesList = getAttributesMap('systemMode')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 219
    List ecoModesList = getAttributesMap('ecoMode')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 220
    List emergencyHeatingModesList = getAttributesMap('emergencyHeating')?.map?.values() as List ?: [] // library marker kkossev.thermostatLib, line 221

    logDebug "setThermostatMode: sending setThermostatMode(${mode}). Natively supported: ${nativelySupportedModesList}" // library marker kkossev.thermostatLib, line 223

    // some TRVs require some checks and additional commands to be sent before setting the mode // library marker kkossev.thermostatLib, line 225
    final String currentMode = device.currentValue('thermostatMode') // library marker kkossev.thermostatLib, line 226
    logDebug "setThermostatMode: currentMode = ${currentMode}, switching to ${mode} ..." // library marker kkossev.thermostatLib, line 227

    // added 02/16/2025 // library marker kkossev.thermostatLib, line 229
    setLastTx( mode = requestedMode, isModeSetReq = true) // library marker kkossev.thermostatLib, line 230
    runIn(4, modeReceiveCheck) // library marker kkossev.thermostatLib, line 231

    switch (mode) { // library marker kkossev.thermostatLib, line 233
        case 'heat': // library marker kkossev.thermostatLib, line 234
        case 'auto': // library marker kkossev.thermostatLib, line 235
            if (device.currentValue('ecoMode') == 'on') { // library marker kkossev.thermostatLib, line 236
                logDebug 'setThermostatMode: pre-processing: switching first the eco mode off' // library marker kkossev.thermostatLib, line 237
                sendAttribute('ecoMode', 0) // library marker kkossev.thermostatLib, line 238
            } // library marker kkossev.thermostatLib, line 239
            if (device.currentValue('emergencyHeating') == 'on') { // library marker kkossev.thermostatLib, line 240
                logDebug 'setThermostatMode: pre-processing: switching first the emergencyHeating mode off' // library marker kkossev.thermostatLib, line 241
                sendAttribute('emergencyHeating', 0) // library marker kkossev.thermostatLib, line 242
            } // library marker kkossev.thermostatLib, line 243
            if ((device.currentValue('systemMode') ?: 'off') == 'off') { // library marker kkossev.thermostatLib, line 244
                logDebug 'setThermostatMode: pre-processing: switching first the systemMode on' // library marker kkossev.thermostatLib, line 245
                sendAttribute('systemMode', 'on') // library marker kkossev.thermostatLib, line 246
            } // library marker kkossev.thermostatLib, line 247
            break // library marker kkossev.thermostatLib, line 248
        case 'cool':        // disabled the cool mode 03/04/2025 // library marker kkossev.thermostatLib, line 249
            if (!('cool' in DEVICE.supportedThermostatModes)) { // library marker kkossev.thermostatLib, line 250
                // why shoud we replace 'cool' with 'eco' and 'off' modes ???? // library marker kkossev.thermostatLib, line 251
                /* // library marker kkossev.thermostatLib, line 252
                // replace cool with 'eco' mode, if supported by the device // library marker kkossev.thermostatLib, line 253
                if ('eco' in DEVICE.supportedThermostatModes) { // library marker kkossev.thermostatLib, line 254
                    logDebug 'setThermostatMode: pre-processing: switching to eco mode instead' // library marker kkossev.thermostatLib, line 255
                    mode = 'eco' // library marker kkossev.thermostatLib, line 256
                    break // library marker kkossev.thermostatLib, line 257
                } // library marker kkossev.thermostatLib, line 258
                else if ('off' in DEVICE.supportedThermostatModes) { // library marker kkossev.thermostatLib, line 259
                    logDebug 'setThermostatMode: pre-processing: switching to off mode instead' // library marker kkossev.thermostatLib, line 260
                    mode = 'off' // library marker kkossev.thermostatLib, line 261
                    break // library marker kkossev.thermostatLib, line 262
                } // library marker kkossev.thermostatLib, line 263
                else if (device.currentValue('ecoMode') != null) { // library marker kkossev.thermostatLib, line 264
                    // BRT-100 has a dediceted 'ecoMode' command   // TODO - check how to switch BRT-100 low temp protection mode (5 degrees) ? // library marker kkossev.thermostatLib, line 265
                    logDebug "setThermostatMode: pre-processing: setting eco mode on (${settings.ecoTemp} &degC)" // library marker kkossev.thermostatLib, line 266
                    sendAttribute('ecoMode', 1) // library marker kkossev.thermostatLib, line 267
                } // library marker kkossev.thermostatLib, line 268
                */ // library marker kkossev.thermostatLib, line 269
                //else { // library marker kkossev.thermostatLib, line 270
                    logDebug "setThermostatMode: pre-processing: switching to 'cool' mode is not supported by this device!" // library marker kkossev.thermostatLib, line 271
                    return // library marker kkossev.thermostatLib, line 272
                //} // library marker kkossev.thermostatLib, line 273
            } // library marker kkossev.thermostatLib, line 274
            break // library marker kkossev.thermostatLib, line 275
        case 'emergency heat':     // TODO for Aqara and Sonoff TRVs // library marker kkossev.thermostatLib, line 276
            if ('emergency heat' in nativelySupportedModesList) { // library marker kkossev.thermostatLib, line 277
                break // library marker kkossev.thermostatLib, line 278
            } // library marker kkossev.thermostatLib, line 279
            // look for a dedicated 'emergencyMode' deviceProfile attribute       (BRT-100) // library marker kkossev.thermostatLib, line 280
            if ('on' in emergencyHeatingModesList)  { // library marker kkossev.thermostatLib, line 281
                logInfo "setThermostatMode: pre-processing: switching the emergencyMode mode on for (${settings.emergencyHeatingTime} seconds )" // library marker kkossev.thermostatLib, line 282
                sendAttribute('emergencyHeating', 'on') // library marker kkossev.thermostatLib, line 283
                return // library marker kkossev.thermostatLib, line 284
            } // library marker kkossev.thermostatLib, line 285
            break // library marker kkossev.thermostatLib, line 286
        case 'eco': // library marker kkossev.thermostatLib, line 287
            if ('eco' in nativelySupportedModesList) {   // library marker kkossev.thermostatLib, line 288
                logDebug 'setThermostatMode: pre-processing: switching to natively supported eco mode' // library marker kkossev.thermostatLib, line 289
                break // library marker kkossev.thermostatLib, line 290
            } // library marker kkossev.thermostatLib, line 291
            if (device.hasAttribute('ecoMode')) {   // changed 06/16/2024 : was : (device.currentValue('ecoMode') != null)  { // library marker kkossev.thermostatLib, line 292
                logDebug 'setThermostatMode: pre-processing: switching the eco mode on' // library marker kkossev.thermostatLib, line 293
                sendAttribute('ecoMode', 1) // library marker kkossev.thermostatLib, line 294
                return // library marker kkossev.thermostatLib, line 295
            } // library marker kkossev.thermostatLib, line 296
            else { // library marker kkossev.thermostatLib, line 297
                logWarn "setThermostatMode: pre-processing: switching to 'eco' mode is not supported by this device!" // library marker kkossev.thermostatLib, line 298
                return // library marker kkossev.thermostatLib, line 299
            } // library marker kkossev.thermostatLib, line 300
            break // library marker kkossev.thermostatLib, line 301
        case 'off':     // OK! // library marker kkossev.thermostatLib, line 302
            if ('off' in nativelySupportedModesList) { // library marker kkossev.thermostatLib, line 303
                break // library marker kkossev.thermostatLib, line 304
            } // library marker kkossev.thermostatLib, line 305
            logDebug "setThermostatMode: pre-processing: switching to 'off' mode" // library marker kkossev.thermostatLib, line 306
            // if the 'off' mode is not directly supported, try substituting it with 'eco' mode // library marker kkossev.thermostatLib, line 307
            if ('eco' in nativelySupportedModesList) { // library marker kkossev.thermostatLib, line 308
                logDebug 'setThermostatMode: pre-processing: switching to eco mode instead' // library marker kkossev.thermostatLib, line 309
                mode = 'eco' // library marker kkossev.thermostatLib, line 310
                break // library marker kkossev.thermostatLib, line 311
            } // library marker kkossev.thermostatLib, line 312
            // look for a dedicated 'ecoMode' deviceProfile attribute       (BRT-100) // library marker kkossev.thermostatLib, line 313
            if ('on' in ecoModesList)  { // library marker kkossev.thermostatLib, line 314
                logDebug 'setThermostatMode: pre-processing: switching the eco mode on' // library marker kkossev.thermostatLib, line 315
                sendAttribute('ecoMode', 'on') // library marker kkossev.thermostatLib, line 316
                return // library marker kkossev.thermostatLib, line 317
            } // library marker kkossev.thermostatLib, line 318
            // look for a dedicated 'systemMode' attribute with map 'off' (Aqara E1) // library marker kkossev.thermostatLib, line 319
            if ('off' in systemModesList)  { // library marker kkossev.thermostatLib, line 320
                logDebug 'setThermostatMode: pre-processing: switching the systemMode off' // library marker kkossev.thermostatLib, line 321
                sendAttribute('systemMode', 'off') // library marker kkossev.thermostatLib, line 322
                return // library marker kkossev.thermostatLib, line 323
            } // library marker kkossev.thermostatLib, line 324
            break // library marker kkossev.thermostatLib, line 325
        default: // library marker kkossev.thermostatLib, line 326
            logWarn "setThermostatMode: pre-processing: unknown mode ${mode}" // library marker kkossev.thermostatLib, line 327
            break // library marker kkossev.thermostatLib, line 328
    } // library marker kkossev.thermostatLib, line 329

    // try using the standard thermostat capability to switch to the selected new mode // library marker kkossev.thermostatLib, line 331
    result = sendAttribute('thermostatMode', mode) // library marker kkossev.thermostatLib, line 332
    logTrace "setThermostatMode: sendAttribute returned ${result}" // library marker kkossev.thermostatLib, line 333
    if (result == true) { return } // library marker kkossev.thermostatLib, line 334

    // post-process mode switching for some TRVs // library marker kkossev.thermostatLib, line 336
    switch (mode) { // library marker kkossev.thermostatLib, line 337
        case 'cool' : // library marker kkossev.thermostatLib, line 338
        case 'heat' : // library marker kkossev.thermostatLib, line 339
        case 'auto' : // library marker kkossev.thermostatLib, line 340
        case 'off' : // library marker kkossev.thermostatLib, line 341
        case 'eco' : // library marker kkossev.thermostatLib, line 342
            logTrace "setThermostatMode: post-processing: no post-processing required for mode ${mode}" // library marker kkossev.thermostatLib, line 343
            break // library marker kkossev.thermostatLib, line 344
        case 'emergency heat' : // library marker kkossev.thermostatLib, line 345
            logDebug "setThermostatMode: post-processing: setting emergency heat mode on (${settings.emergencyHeatingTime} minutes)" // library marker kkossev.thermostatLib, line 346
            sendAttribute('emergencyHeating', 1) // library marker kkossev.thermostatLib, line 347
            break // library marker kkossev.thermostatLib, line 348
        default : // library marker kkossev.thermostatLib, line 349
            logWarn "setThermostatMode: post-processing: unsupported thermostat mode '${mode}'" // library marker kkossev.thermostatLib, line 350
            break // library marker kkossev.thermostatLib, line 351
    } // library marker kkossev.thermostatLib, line 352
    return // library marker kkossev.thermostatLib, line 353
} // library marker kkossev.thermostatLib, line 354

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.thermostatLib, line 356
void sendSupportedThermostatModes(boolean debug = false) { // library marker kkossev.thermostatLib, line 357
    List<String> supportedThermostatModes = [] // library marker kkossev.thermostatLib, line 358
    supportedThermostatModes = ['off', 'heat', 'auto', 'emergency heat'] // library marker kkossev.thermostatLib, line 359
    if (DEVICE.supportedThermostatModes != null) { // library marker kkossev.thermostatLib, line 360
        supportedThermostatModes = DEVICE.supportedThermostatModes // library marker kkossev.thermostatLib, line 361
    } // library marker kkossev.thermostatLib, line 362
    else { // library marker kkossev.thermostatLib, line 363
        logWarn 'sendSupportedThermostatModes: DEVICE.supportedThermostatModes is not set!' // library marker kkossev.thermostatLib, line 364
        supportedThermostatModes =  ['off', 'auto', 'heat'] // library marker kkossev.thermostatLib, line 365
    } // library marker kkossev.thermostatLib, line 366
    logInfo "supportedThermostatModes: ${supportedThermostatModes}" // library marker kkossev.thermostatLib, line 367
    sendEvent(name: 'supportedThermostatModes', value:  JsonOutput.toJson(supportedThermostatModes), isStateChange: true, type: 'digital') // library marker kkossev.thermostatLib, line 368
    if (DEVICE.supportedThermostatFanModes != null) { // library marker kkossev.thermostatLib, line 369
        sendEvent(name: 'supportedThermostatFanModes', value: JsonOutput.toJson(DEVICE.supportedThermostatFanModes), isStateChange: true, type: 'digital') // library marker kkossev.thermostatLib, line 370
    } // library marker kkossev.thermostatLib, line 371
    else { // library marker kkossev.thermostatLib, line 372
        sendEvent(name: 'supportedThermostatFanModes', value: JsonOutput.toJson(['auto', 'circulate', 'on']), isStateChange: true, type: 'digital') // library marker kkossev.thermostatLib, line 373
    } // library marker kkossev.thermostatLib, line 374
} // library marker kkossev.thermostatLib, line 375

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.thermostatLib, line 377
void standardHandleThermostatEvent(int value, boolean isDigital=false) { // library marker kkossev.thermostatLib, line 378
    logWarn 'standardHandleThermostatEvent()... NOT IMPLEMENTED!' // library marker kkossev.thermostatLib, line 379
} // library marker kkossev.thermostatLib, line 380

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.thermostatLib, line 382
private void sendDelayedThermostatEvent(Map eventMap) { // library marker kkossev.thermostatLib, line 383
    logWarn "${device.displayName} NOT IMPLEMENTED! <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.thermostatLib, line 384
} // library marker kkossev.thermostatLib, line 385

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.thermostatLib, line 387
void thermostatProcessTuyaDP(final Map descMap, int dp, int dp_id, int fncmd) { // library marker kkossev.thermostatLib, line 388
    logWarn "thermostatProcessTuyaDP()... NOT IMPLEMENTED! dp=${dp} dp_id=${dp_id} fncmd=${fncmd}" // library marker kkossev.thermostatLib, line 389
} // library marker kkossev.thermostatLib, line 390

/** // library marker kkossev.thermostatLib, line 392
 * Schedule thermostat polling // library marker kkossev.thermostatLib, line 393
 * @param intervalMins interval in seconds // library marker kkossev.thermostatLib, line 394
 */ // library marker kkossev.thermostatLib, line 395
public void scheduleThermostatPolling(final int intervalSecs) { // library marker kkossev.thermostatLib, line 396
    String cron = getCron( intervalSecs ) // library marker kkossev.thermostatLib, line 397
    logDebug "cron = ${cron}" // library marker kkossev.thermostatLib, line 398
    schedule(cron, 'autoPollThermostat') // library marker kkossev.thermostatLib, line 399
} // library marker kkossev.thermostatLib, line 400

public void unScheduleThermostatPolling() { // library marker kkossev.thermostatLib, line 402
    unschedule('autoPollThermostat') // library marker kkossev.thermostatLib, line 403
} // library marker kkossev.thermostatLib, line 404

/** // library marker kkossev.thermostatLib, line 406
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.thermostatLib, line 407
 */ // library marker kkossev.thermostatLib, line 408
public void autoPollThermostat() { // library marker kkossev.thermostatLib, line 409
    logDebug 'autoPollThermostat()...' // library marker kkossev.thermostatLib, line 410
    checkDriverVersion(state) // library marker kkossev.thermostatLib, line 411
    List<String> cmds = refreshFromDeviceProfileList() // library marker kkossev.thermostatLib, line 412
    if (cmds != null && cmds != [] ) { // library marker kkossev.thermostatLib, line 413
        sendZigbeeCommands(cmds) // library marker kkossev.thermostatLib, line 414
    } // library marker kkossev.thermostatLib, line 415
} // library marker kkossev.thermostatLib, line 416

private int getElapsedTimeFromEventInSeconds(final String eventName) { // library marker kkossev.thermostatLib, line 418
    /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.thermostatLib, line 419
    final Long now = new Date().time // library marker kkossev.thermostatLib, line 420
    final Object lastEventState = device.currentState(eventName) // library marker kkossev.thermostatLib, line 421
    logDebug "getElapsedTimeFromEventInSeconds: eventName = ${eventName} lastEventState = ${lastEventState}" // library marker kkossev.thermostatLib, line 422
    if (lastEventState == null) { // library marker kkossev.thermostatLib, line 423
        logTrace 'getElapsedTimeFromEventInSeconds: lastEventState is null, returning 0' // library marker kkossev.thermostatLib, line 424
        return 0 // library marker kkossev.thermostatLib, line 425
    } // library marker kkossev.thermostatLib, line 426
    Long lastEventStateTime = lastEventState.date.time // library marker kkossev.thermostatLib, line 427
    //def lastEventStateValue = lastEventState.value // library marker kkossev.thermostatLib, line 428
    int diff = ((now - lastEventStateTime) / 1000) as int // library marker kkossev.thermostatLib, line 429
    // convert diff to minutes and seconds // library marker kkossev.thermostatLib, line 430
    logTrace "getElapsedTimeFromEventInSeconds: lastEventStateTime = ${lastEventStateTime} diff = ${diff} seconds" // library marker kkossev.thermostatLib, line 431
    return diff // library marker kkossev.thermostatLib, line 432
} // library marker kkossev.thermostatLib, line 433

// called from pollTuya() // library marker kkossev.thermostatLib, line 435
public void sendDigitalEventIfNeeded(final String eventName) { // library marker kkossev.thermostatLib, line 436
    final Object lastEventState = device.currentState(eventName) // library marker kkossev.thermostatLib, line 437
    if (lastEventState == null) { // library marker kkossev.thermostatLib, line 438
        logDebug "sendDigitalEventIfNeeded: lastEventState ${eventName} is null, skipping" // library marker kkossev.thermostatLib, line 439
        return // library marker kkossev.thermostatLib, line 440
    } // library marker kkossev.thermostatLib, line 441
    final int diff = getElapsedTimeFromEventInSeconds(eventName) // library marker kkossev.thermostatLib, line 442
    final String diffStr = timeToHMS(diff) // library marker kkossev.thermostatLib, line 443
    if (diff >= (settings.temperaturePollingInterval as int)) { // library marker kkossev.thermostatLib, line 444
        logDebug "pollTuya: ${eventName} was sent more than ${settings.temperaturePollingInterval} seconds ago (${diffStr}), sending digital event" // library marker kkossev.thermostatLib, line 445
        sendEventMap([name: lastEventState.name, value: lastEventState.value, unit: lastEventState.unit, type: 'digital']) // library marker kkossev.thermostatLib, line 446
    } // library marker kkossev.thermostatLib, line 447
    else { // library marker kkossev.thermostatLib, line 448
        logDebug "pollTuya: ${eventName} was sent less than ${settings.temperaturePollingInterval} seconds ago, skipping" // library marker kkossev.thermostatLib, line 449
    } // library marker kkossev.thermostatLib, line 450
} // library marker kkossev.thermostatLib, line 451

public void thermostatInitializeVars( boolean fullInit = false ) { // library marker kkossev.thermostatLib, line 453
    logDebug "thermostatInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.thermostatLib, line 454
    if (fullInit == true || state.lastThermostatMode == null) { state.lastThermostatMode = 'unknown' } // library marker kkossev.thermostatLib, line 455
    if (fullInit == true || state.lastThermostatOperatingState == null) { state.lastThermostatOperatingState = 'unknown' } // library marker kkossev.thermostatLib, line 456
    if (fullInit || settings?.temperaturePollingInterval == null) { device.updateSetting('temperaturePollingInterval', [value: TrvTemperaturePollingIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.thermostatLib, line 457
} // library marker kkossev.thermostatLib, line 458

// called from initializeVars() in the main code ... // library marker kkossev.thermostatLib, line 460
public void thermostatInitEvents(final boolean fullInit=false) { // library marker kkossev.thermostatLib, line 461
    logDebug "thermostatInitEvents()... fullInit = ${fullInit}" // library marker kkossev.thermostatLib, line 462
    if (fullInit == true) { // library marker kkossev.thermostatLib, line 463
        String descText = 'inital attribute setting' // library marker kkossev.thermostatLib, line 464
        sendSupportedThermostatModes() // library marker kkossev.thermostatLib, line 465
        sendEvent(name: 'thermostatMode', value: 'heat', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 466
        state.lastThermostatMode = 'heat' // library marker kkossev.thermostatLib, line 467
        sendEvent(name: 'thermostatFanMode', value: 'auto', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 468
        state.lastThermostatOperatingState = 'idle' // library marker kkossev.thermostatLib, line 469
        sendEvent(name: 'thermostatOperatingState', value: 'idle', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 470
        sendEvent(name: 'thermostatSetpoint', value:  20.0, unit: '\u00B0C', isStateChange: true, description: descText)        // Google Home compatibility // library marker kkossev.thermostatLib, line 471
        sendEvent(name: 'heatingSetpoint', value: 20.0, unit: '\u00B0C', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 472
        state.lastHeatingSetpoint = 20.0 // library marker kkossev.thermostatLib, line 473
        sendEvent(name: 'coolingSetpoint', value: 35.0, unit: '\u00B0C', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 474
        sendEvent(name: 'temperature', value: 18.0, unit: '\u00B0', isStateChange: true, description: descText) // library marker kkossev.thermostatLib, line 475
        updateDataValue('lastRunningMode', 'heat') // library marker kkossev.thermostatLib, line 476
    } // library marker kkossev.thermostatLib, line 477
    else { // library marker kkossev.thermostatLib, line 478
        logDebug "thermostatInitEvents: fullInit = ${fullInit}" // library marker kkossev.thermostatLib, line 479
    } // library marker kkossev.thermostatLib, line 480
} // library marker kkossev.thermostatLib, line 481

/* // library marker kkossev.thermostatLib, line 483
  Reset to Factory Defaults Command (0x00) // library marker kkossev.thermostatLib, line 484
  On receipt of this command, the device resets all the attributes of all its clusters to their factory defaults. // library marker kkossev.thermostatLib, line 485
  Note that networking functionality, bindings, groups, or other persistent data are not affected by this command // library marker kkossev.thermostatLib, line 486
*/ // library marker kkossev.thermostatLib, line 487
public void factoryResetThermostat() { // library marker kkossev.thermostatLib, line 488
    logDebug 'factoryResetThermostat() called!' // library marker kkossev.thermostatLib, line 489
    List<String> cmds  = zigbee.command(0x0000, 0x00) // library marker kkossev.thermostatLib, line 490
    sendZigbeeCommands(cmds) // library marker kkossev.thermostatLib, line 491
    sendInfoEvent 'The thermostat parameters were FACTORY RESET!' // library marker kkossev.thermostatLib, line 492
    if (this.respondsTo('refreshAll')) { // library marker kkossev.thermostatLib, line 493
        runIn(3, 'refreshAll') // library marker kkossev.thermostatLib, line 494
    } // library marker kkossev.thermostatLib, line 495
} // library marker kkossev.thermostatLib, line 496

// ========================================= Virtual thermostat functions  ========================================= // library marker kkossev.thermostatLib, line 498

public void setTemperature(Number temperaturePar) { // library marker kkossev.thermostatLib, line 500
    logDebug "setTemperature(${temperature}) called!" // library marker kkossev.thermostatLib, line 501
    if (isVirtual()) { // library marker kkossev.thermostatLib, line 502
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.thermostatLib, line 503
        double temperature = Math.round(temperaturePar * 2) / 2 // library marker kkossev.thermostatLib, line 504
        String descText = "temperature is set to ${temperature} \u00B0C" // library marker kkossev.thermostatLib, line 505
        sendEvent(name: 'temperature', value: temperature, unit: '\u00B0C', descriptionText: descText, type: 'digital') // library marker kkossev.thermostatLib, line 506
        logInfo "${descText}" // library marker kkossev.thermostatLib, line 507
    } // library marker kkossev.thermostatLib, line 508
    else { // library marker kkossev.thermostatLib, line 509
        logWarn 'setTemperature: not a virtual thermostat!' // library marker kkossev.thermostatLib, line 510
    } // library marker kkossev.thermostatLib, line 511
} // library marker kkossev.thermostatLib, line 512

// TODO - not used? // library marker kkossev.thermostatLib, line 514
List<String> thermostatRefresh() { // library marker kkossev.thermostatLib, line 515
    logDebug 'thermostatRefresh()...' // library marker kkossev.thermostatLib, line 516
    return [] // library marker kkossev.thermostatLib, line 517
} // library marker kkossev.thermostatLib, line 518

// TODO - configure in the deviceProfile refresh: tag // library marker kkossev.thermostatLib, line 520
public List<String> pollThermostatCluster() { // library marker kkossev.thermostatLib, line 521
    return  zigbee.readAttribute(0x0201, [0x0000, 0x0001, /*0x0002,*/ 0x0012, 0x001B, 0x001C, 0x0029], [:], delay = 1500)      // 0x0000 = local temperature, 0x0001 = outdoor temperature, 0x0002 = occupancy, 0x0012 = heating setpoint, 0x001B = controlledSequenceOfOperation, 0x001C = system mode (enum8 ) // library marker kkossev.thermostatLib, line 522
} // library marker kkossev.thermostatLib, line 523

// TODO - configure in the deviceProfile refresh: tag // library marker kkossev.thermostatLib, line 525
public List<String> pollBatteryPercentage() { // library marker kkossev.thermostatLib, line 526
    return zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)                          // battery percentage // library marker kkossev.thermostatLib, line 527
} // library marker kkossev.thermostatLib, line 528

public List<String> pollOccupancy() { // library marker kkossev.thermostatLib, line 530
    return  zigbee.readAttribute(0x0406, 0x0000, [:], delay = 100)      // Bit 0 specifies the sensed occupancy as follows: 1 = occupied, 0 = unoccupied. This flag bit will affect the Occupancy attribute of HVAC cluster, and the operation mode. // library marker kkossev.thermostatLib, line 531
} // library marker kkossev.thermostatLib, line 532

////////////////////////////// added 02/16/2024 ////////////////////////////// // library marker kkossev.thermostatLib, line 534

// scheduled for call from setThermostatMode() 4 seconds after the mode was potentiually changed. // library marker kkossev.thermostatLib, line 536
// also, called every 1 minute from receiveCheck() // library marker kkossev.thermostatLib, line 537
void modeReceiveCheck() { // library marker kkossev.thermostatLib, line 538
    if (settings?.resendFailed != true) { return } // library marker kkossev.thermostatLib, line 539
    if (state.lastTx?.isModeSetReq != true) { return }    // no mode change was requested // library marker kkossev.thermostatLib, line 540

    if (state.lastTx.mode != device.currentState('thermostatMode', true).value) { // library marker kkossev.thermostatLib, line 542
        state.lastTx['setModeRetries'] = (state.lastTx['setModeRetries'] ?: 0) + 1 // library marker kkossev.thermostatLib, line 543
        logWarn "modeReceiveCheck() <b>failed</b> (expected ${state.lastTx['mode']}, current ${device.currentState('thermostatMode', true).value}), retry#${state.lastTx['setModeRetries']} of ${MaxRetries}" // library marker kkossev.thermostatLib, line 544
        if (state.lastTx['setModeRetries'] < MaxRetries) { // library marker kkossev.thermostatLib, line 545
            logDebug "resending mode command : ${state.lastTx['mode']}" // library marker kkossev.thermostatLib, line 546
            state.stats['txFailCtr'] = (state.stats['txFailCtr']  ?: 0) + 1 // library marker kkossev.thermostatLib, line 547
            setThermostatMode( state.lastTx['mode'] ) // library marker kkossev.thermostatLib, line 548
        } // library marker kkossev.thermostatLib, line 549
        else { // library marker kkossev.thermostatLib, line 550
            logWarn "modeReceiveCheck(${state.lastTx['mode'] }}) <b>giving up retrying<b/>" // library marker kkossev.thermostatLib, line 551
            state.lastTx['isModeSetReq'] = false    // giving up // library marker kkossev.thermostatLib, line 552
            state.lastTx['setModeRetries'] = 0 // library marker kkossev.thermostatLib, line 553
        } // library marker kkossev.thermostatLib, line 554
    } // library marker kkossev.thermostatLib, line 555
    else { // library marker kkossev.thermostatLib, line 556
        logDebug "modeReceiveCheck mode was changed OK to (${state.lastTx['mode']}). No need for further checks." // library marker kkossev.thermostatLib, line 557
        state.lastTx.isModeSetReq = false    // setting mode was successfuly confimed, no need for further checks // library marker kkossev.thermostatLib, line 558
        state.lastTx.setModeRetries = 0 // library marker kkossev.thermostatLib, line 559
    } // library marker kkossev.thermostatLib, line 560
} // library marker kkossev.thermostatLib, line 561

// // library marker kkossev.thermostatLib, line 563
//  also, called every 1 minute from receiveCheck() // library marker kkossev.thermostatLib, line 564
public void setpointReceiveCheck() { // library marker kkossev.thermostatLib, line 565
    if (settings?.resendFailed != true) { return } // library marker kkossev.thermostatLib, line 566
    if (state.lastTx.isSetPointReq != true) { return } // library marker kkossev.thermostatLib, line 567

    if (state.lastTx.setPoint != NOT_SET && ((state.lastTx.setPoint as String) != (state.lastRx.setPoint as String))) { // library marker kkossev.thermostatLib, line 569
        state.lastTx.setPointRetries = (state.lastTx.setPointRetries ?: 0) + 1 // library marker kkossev.thermostatLib, line 570
        if (state.lastTx.setPointRetries < MaxRetries) { // library marker kkossev.thermostatLib, line 571
            logWarn "setpointReceiveCheck(${state.lastTx.setPoint}) <b>failed<b/> (last received is still ${state.lastRx.setPoint})" // library marker kkossev.thermostatLib, line 572
            logDebug "resending setpoint command : ${state.lastTx.setPoint} (retry# ${state.lastTx.setPointRetries}) of ${MaxRetries}" // library marker kkossev.thermostatLib, line 573
            state.stats.txFailCtr = (state.stats.txFailCtr ?: 0) + 1 // library marker kkossev.thermostatLib, line 574
            // TODO !! sendTuyaHeatingSetpoint(state.lastTx.setPoint) // library marker kkossev.thermostatLib, line 575
            setHeatingSetpoint(state.lastTx.setPoint as Number) // library marker kkossev.thermostatLib, line 576
        } // library marker kkossev.thermostatLib, line 577
        else { // library marker kkossev.thermostatLib, line 578
            logWarn "setpointReceiveCheck(${state.lastTx.setPoint}) <b>giving up retrying<b/>" // library marker kkossev.thermostatLib, line 579
            state.lastTx.isSetPointReq = false // library marker kkossev.thermostatLib, line 580
            state.lastTx.setPointRetries = 0 // library marker kkossev.thermostatLib, line 581
        } // library marker kkossev.thermostatLib, line 582
    } // library marker kkossev.thermostatLib, line 583
    else { // library marker kkossev.thermostatLib, line 584
        logDebug "setpointReceiveCheck setPoint was changed successfuly to (${state.lastTx.setPoint}). No need for further checks." // library marker kkossev.thermostatLib, line 585
        state.lastTx.setPoint = NOT_SET // library marker kkossev.thermostatLib, line 586
        state.lastTx.isSetPointReq = false // library marker kkossev.thermostatLib, line 587
    } // library marker kkossev.thermostatLib, line 588
} // library marker kkossev.thermostatLib, line 589

public void setLastRx( int dp, int fncmd) { // library marker kkossev.thermostatLib, line 591
    state.lastRx['dp'] = dp // library marker kkossev.thermostatLib, line 592
    state.lastRx['fncmd'] = fncmd // library marker kkossev.thermostatLib, line 593
} // library marker kkossev.thermostatLib, line 594

public void setLastTx( String mode=null, Boolean isModeSetReq=null) { // library marker kkossev.thermostatLib, line 596
    if (mode != null) { // library marker kkossev.thermostatLib, line 597
        state.lastTx['mode'] = mode // library marker kkossev.thermostatLib, line 598
    } // library marker kkossev.thermostatLib, line 599
    if (isModeSetReq != null) { // library marker kkossev.thermostatLib, line 600
        state.lastTx['isModeSetReq'] = isModeSetReq // library marker kkossev.thermostatLib, line 601
    } // library marker kkossev.thermostatLib, line 602
} // library marker kkossev.thermostatLib, line 603

public String getLastMode() { // library marker kkossev.thermostatLib, line 605
    return state.lastTx.mode ?: 'exception' // library marker kkossev.thermostatLib, line 606
} // library marker kkossev.thermostatLib, line 607

public boolean checkIfIsDuplicated( int dp, int fncmd ) { // library marker kkossev.thermostatLib, line 609
    Map oldDpFncmd = state.lastRx // library marker kkossev.thermostatLib, line 610
    return dp == oldDpFncmd.dp && fncmd == oldDpFncmd.fncmd // library marker kkossev.thermostatLib, line 611
} // library marker kkossev.thermostatLib, line 612

// ~~~~~ end include (179) kkossev.thermostatLib ~~~~~
