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
 * ver. 3.5.3  2025-10-03 kkossev  - (dev. branch) added TS0601 _TZE204_lpedvtvr into new 'MOES_RING_THERMOSTAT' profile
 *
 *                                   TODO: 
 */

static String version() { '3.5.3' }
static String timeStamp() { '2025/10/03 9:03 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput
import java.math.RoundingMode


#include kkossev.commonLib
#include kkossev.onOffLib
#include kkossev.batteryLib
#include kkossev.temperatureLib
#include kkossev.deviceProfileLib
#include kkossev.thermostatLib

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

    'MOES_RING_THERMOSTAT'   : [       // https://github.com/Koenkk/zigbee-herdsman-converters/blob/0f787aecb1fc85230d9c1d70d90aa8c04d4490f2/src/devices/moes.ts#L1401-L1501
            description   : 'Moes Ring Thermostat',
            device        : [models: ['TS0601'], type: 'Thermostat', powerSource: 'ac', isSleepy:false],
            capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode':true],
            preferences   : ['childLock':'39', 'minHeatingSetpoint':'18', 'maxHeatingSetpoint':'34', 'sensor':'32', 'hysteresis':'110', 'ecoTemp':'113', 'temperatureCalibration':'101', 'brightness':'48', 'screenTime':'114', 'highProtectTemperature':'111', 'lowProtectTemperature':'112'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_lpedvtvr", deviceJoinName: 'Moes Ring Thermostat']
            ],
            commands      : ['sendSupportedThermostatModes':'sendSupportedThermostatModes', 'setHeatingSetpoint':'setHeatingSetpoint', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [
                [dp:1,   name:'systemMode',         type:'enum',            rw: 'rw', defVal:'1', map:[0: 'off', 1: 'heat'],  title:'<b>Thermostat Switch</b>',  description:'Thermostat switch (system mode)'],
                [dp:2,   name:'preset',             type:'enum',            rw: 'rw', defVal:'0', map:[0:'manual', 1:'temporary manual', 2:'program', 3:'eco'], description:'Preset mode'],
                [dp:16,  name:'temperature',        type:'decimal',         rw: 'ro', min:-20.0, max:60.0, defVal:20.0, step:0.1, scale:10, unit:'°C',  description:'Measured temperature'],
                [dp:18,  name:'minHeatingSetpoint', type:'decimal',         rw: 'rw', min:1.0,   max:15.0, defVal:5.0, step:0.5, scale:10, unit:'°C',  title:'<b>Minimum Heating Setpoint</b>', description:'Minimum comfort temperature limit'],
                [dp:32,  name:'sensor',             type:'enum',            rw: 'rw', defVal:'0', map:[0:'internal', 1:'dual', 2:'external'], title:'<b>Sensor Selection</b>', description:'Temperature sensor mode'],
                [dp:34,  name:'maxHeatingSetpoint', type:'decimal',         rw: 'rw', min:35.0,  max:45.0, defVal:45.0, step:0.5, scale:10, unit:'°C',  title:'<b>Maximum Heating Setpoint</b>', description:'Maximum comfort temperature limit'],
                [dp:39,  name:'childLock',          type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>Child Lock</b>',  description:'Child lock'],
                [dp:47,  name:'thermostatOperatingState', type:'enum',     rw: 'rw', defVal:'0', map:[0:'heating', 1:'idle'], description:'Thermostat operating state'],
                [dp:48,  name:'brightness',         type:'number',          rw: 'rw', min:0,     max:100, defVal:100, step:1,   unit:'%', title:'<b>Backlight Brightness</b>', description:'Display backlight brightness'],
                [dp:50,  name:'heatingSetpoint',    type:'decimal',         rw: 'rw', min:5.0,   max:45.0, defVal:20.0, step:0.5, scale:10, unit:'°C',  title:'<b>Current Heating Setpoint</b>', description:'Target heating setpoint'],
                [dp:101, name:'temperatureCalibration', type:'decimal',    rw: 'rw', min:-10.0, max:10.0, defVal:0.0, step:1.0, scale:1,  unit:'°C',  title:'<b>Temperature Calibration</b>', description:'Temperature calibration offset'],
                [dp:109, name:'floorTemperature',   type:'decimal',         rw: 'ro', min:0.0,   max:70.0, defVal:20.0, step:0.5, scale:10, unit:'°C', description:'Floor temperature'],
                [dp:110, name:'hysteresis',         type:'decimal',         rw: 'rw', min:0.5,   max:5.0,  defVal:1.0, step:0.5, scale:10, title:'<b>Temperature Deadzone</b>', description:'Temperature delta before heating engages'],
                [dp:111, name:'highProtectTemperature', type:'decimal',    rw: 'rw', min:10.0,  max:70.0, defVal:50.0, step:1.0, scale:1,  unit:'°C',  title:'<b>High Protect Temperature</b>', description:'High temperature protection threshold'],
                [dp:112, name:'lowProtectTemperature',  type:'decimal',    rw: 'rw', min:0.0,   max:10.0, defVal:5.0,  step:0.5, scale:10, unit:'°C',  title:'<b>Low Protect Temperature</b>', description:'Low temperature protection threshold'],
                [dp:113, name:'ecoTemp',           type:'decimal',         rw: 'rw', min:10.0,  max:30.0, defVal:18.0, step:0.5, scale:10, unit:'°C',  title:'<b>Eco Temperature</b>', description:'Temperature used in Eco preset'],
                [dp:114, name:'screenTime',        type:'enum',            rw: 'rw', defVal:'2', map:[0:'10 seconds', 1:'20 seconds', 2:'30 seconds', 3:'40 seconds', 4:'50 seconds', 5:'60 seconds'], title:'<b>Screen Timeout</b>', description:'Screen backlight timeout'],
                [dp:115, name:'rgbLight',          type:'enum',  dt: '01', rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'<b>RGB Accent Light</b>', description:'Enable the RGB accent light'],
            ],
            supportedThermostatModes: ['off', 'heat'],
            refresh: ['pollTuya'],
            deviceJoinName: 'Moes Ring Thermostat',
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


