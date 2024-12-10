/* groovylint-disable DuplicateMapLiteral, DuplicateStringLiteral, ImplicitClosureParameter, InsecureRandom, LineLength, NoDouble, ParameterName, StaticMethodsBeforeInstanceMethods */
/**
 *  Tuya Zigbee Valve driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/alpha-tuya-zigbee-valve-driver/92788
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  ver. 1.0.0 2022-04-22 kkossev - inital version
 *  ver. 1.0.1 2022-04-23 kkossev - added Refresh command; [overwrite: true] explicit option for runIn calls; capability PowerSource
 *  ver. 1.0.2 2022-08-14 kkossev - added _TZE200_sh1btabb WaterIrrigationValve (On/Off only); fingerprint inClusters correction; battery capability; open/close commands changes
 *  ver. 1.0.3 2022-08-19 kkossev - decreased delay betwen Tuya commands to 200 milliseconds; irrigation valve open/close commands are sent 2 times; digital/physicla timer changed to 3 seconds;
 *  ver. 1.0.4 2022-11-28 kkossev - added Power-On Behaviour preference setting
 *  ver. 1.0.5 2023-01-21 kkossev - added _TZE200_81isopgh (SASWELL) battery, timer_state, timer_time_left, last_valve_open_duration, weather_delay; added _TZE200_2wg5qrjy _TZE200_htnnfasr (LIDL);
 *  ver. 1.1.0 2023-01-29 kkossev - added healthStatus
 *  ver. 1.2.0 2023-02-28 kkossev - added deviceProfiles; stats; Advanced Option to manually select device profile; dynamically generated fingerptints; added autOffTimer;
 *                                  added irrigationStartTime, irrigationEndTime, lastIrrigationDuration, waterConsumed; removed the doubled open/close commands for _TZE200_sh1btabb;
 *                                  renamed timer_time_left to timerTimeLeft, renamed last_valve_open_duration to lastValveOpenDuration; autoOffTimer value is sent as an attribute;
 *                                  added new _TZE200_a7sghmms GiEX manufacturer; sending the timeout 5 seconds both after the start and after the stop commands are received (both SASWELL and GiEX)
 *                                  added setIrrigationCapacity, setIrrigationMode; irrigationCapacity; irrigationDuration;
 *                                  added extraTuyaMagic for Lidl TS0601 _TZE200_htnnfasr 'Parkside smart watering timer'
 *  ver. 1.2.1 2023-03-12 kkossev - bugfix: debug/info logs were enabled after each version update; autoSendTimer is made optional (default:enabled for GiEX, disabled for SASWELL); added tuyaVersion; added _TZ3000_5ucujjts + fingerprint bug fix;
 *  ver. 1.2.2 2023-03-12 kkossev - _TZ3000_5ucujjts fingerprint model bug fix; parse exception logs everity changed from warning to debug; refresh() is called w/ 3 seconds delay on configure(); sendIrrigationDuration() exception bug fixed; aded rejoinCtr
 *  ver. 1.2.3 2023-03-26 kkossev - TS0601_VALVE_ONOFF powerSource changed to 'dc'; added _TZE200_yxcgyjf1; added EF01,EF02,EF03,EF04 logs; added _TZE200_d0ypnbvn; fixed TS0601, GiEX and Lidl switch on/off reporting bug
 *  ver. 1.2.4 2023-04-09 kkossev - _TZ3000_5ucujjts deviceProfile bug fix; added rtt measurement in ping(); handle known E00X clusters
 *  ver. 1.2.5 2023-05-22 kkossev - handle exception when processing application version; Saswell _TZE200_81isopgh fingerptint correction; fixed Lidl/Parkside _TZE200_htnnfasr group; lables changed : timer is in seconds (Saswell) or in minutes (GiEX)
 *  ver. 1.2.6 2023-07-28 kkossev - fixed exceptions in configure(), ping() and rtt commands; scheduleDeviceHealthCheck() was not scheduled on initialize() and updated(); UNKNOWN deviceProfile fixed; set deviceProfile preference to match the automatically selected one; fake deviceCommandTimeout fix;
 *  ver. 1.2.7 2023-12-18 kkossev - code linting
 *  ver. 1.3.0 2024-03-17 kkossev - more code linting; added TS0049 _TZ3210_0jxeoadc; added three-states (opening, closing)
 *  ver. 1.3.1 2024-04-30 kkossev - getPowerSource bug fix; TS0049 command '06' processing; TS0049 battery% fix; TS0049 open/close fix; TS0049 command '05' processing;
 *  ver. 1.3.2 2024-07-31 kkossev - added SONOFF SWV (+onWithTimedOff)
 *  ver. 1.3.3 2024-08-02 kkossev - added FrankEver FK_V02 _TZE200_1n2zev06 Valve Open Percentage and timeout timer; separated valveOpenThreshold and valveOpenPercentage
 *  ver. 1.3.4 2024-08-02 dstutz  - added Giex _TZE204_7ytb3h8u 
 *  ver. 1.3.5 2024-09-22 kkossev - removed tuyaVersion for non-Tuya devices; combined on() + timedOff() command for opening the Sonoff valve;
 *  ver. 1.3.6 2024-09-23 kkossev - Sonoff valve: irrigationDuration 0 will disable the valve auto-off; default auto-off timer changed to 0 (was 60 seconds); invalid 'digital' type of autoClose fixed; added workState attribute; logging improvements;
 *  ver. 1.4.0 2024-11-22 kkossev - supressed 'Sonoff SWV sendIrrigationDuration is not avaiable!' warning; added NovaDigital TS0601 _TZE200_fphxkxue @Rafael as TS0601_SASWELL_VALVE (working partially!); added queryAllTuyaDP for TS0601 devices;
 *  ver. 1.5.0 2024-12-10 kkossev - (dev.branch) adding TS0601 _TZE284_8zizsafo _TZE284_eaet5qt5 in 'TS0601_TZE284_VALVE' group 
 *
 *                                  TODO: @rgr - add a timer to the driver that shows how much time is left before the valve closes
 *                                  TODO: document the attributes (per valve model) in GitHub; add links to the HE forum and GitHub pages; 
 *                                  TODO: set the device name from fingerprint (deviceProfilesV2 as in 4-in-1 driver)
 *                                  TODO: clear the old states on update; add rejoinCtr;
 */
import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

static String version() { '1.5.0' }
static String timeStamp() { '2024/12/10 10:34 PM' }

@Field static final Boolean _DEBUG = false
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true                // disable it for the production release !
@Field static final String SIMULATED_PROFILE = 'TS0601_TZE284_VALVE'    // in _DEBUG mode only

metadata {
    definition(name: 'Tuya Zigbee Valve', namespace: 'kkossev', author: 'Krassimir Kossev', importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Valve/Tuya%20Zigbee%20Valve.groovy', singleThreaded: true ) {
        capability 'Actuator'
        capability 'Valve'
        capability 'Refresh'
        capability 'Configuration'
        capability 'PowerSource'
        capability 'HealthCheck'
        capability 'Battery'
        capability 'SignalStrength'         // Sonoff SWV
        capability 'LiquidFlowRate'         // Sonoff SWV (attribute 'rate')

        attribute 'batteryVoltage', 'number'
        attribute 'healthStatus', 'enum', ['offline', 'online']
        attribute 'rtt', 'number'
        attribute 'timerState', 'enum', ['disabled', 'active (on)', 'enabled (off)']
        attribute 'timerTimeLeft', 'number'
        attribute 'timerTimeLeft2', 'number'             // isTZE284()
        attribute 'lastValveOpenDuration', 'number'
        attribute 'weatherDelay', 'enum', ['disabled', '24h', '48h', '72h']
        attribute 'irrigationStartTime', 'string'
        attribute 'irrigationEndTime', 'string'
        attribute 'lastIrrigationDuration', 'string'    // Ex: "00:01:10,0"
        attribute 'waterConsumed', 'number'
        attribute 'irrigationVolume', 'number'
        attribute 'irrigationDuration', 'number'        // Sonoff SWV, frankEver FK_V02
        attribute 'irrigationCapacity', 'number'
        attribute 'valveStatus',  'enum', ['normal', 'shortage', 'leakage', 'shortageAndLeakage', 'clear', 'manual', 'auto', 'idle']    // SONOFF {ID: 0x500c, type: 0x20},
        attribute 'valveStatus2', 'enum', ['normal', 'shortage', 'leakage', 'shortageAndLeakage', 'clear', 'manual', 'auto', 'idle']    // isTZE284()
        attribute 'valveOpenThreshold', 'number'        // FrankEver FK_V02 - the set threshold for valve open 
        attribute 'valveOpenPercentage', 'number'       // FrankEver FK_V02 - the current valve open percentage reported by the device
        attribute 'workState', 'enum', ['manual control', 'Cycle timing / quantity control', 'Schedule control']
        //attribute 'workState', 'enum', SonoffWorkStateOptions.values() as List<String>
        attribute 'valve2', 'enum', ['open', 'closed']  // isTZE284()

        command 'initialize', [[name: 'Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****']]
        command 'setIrrigationTimer', [[name:'auto-off timer (irrigationDuration ), in seconds or minutes (depending on the model)', type: 'NUMBER', description: 'Set the irrigation duration timer<br>, in seconds or minutes (depending on the model). Zero value disables the auto-off!', constraints: ['0..86400']]]
        command 'setIrrigationCapacity', [[name:'capacity, liters (Saswell and GiEX)', type: 'NUMBER', description: 'Set Irrigation Capacity, litres', constraints: ['0..9999']]]
        command 'setIrrigationMode', [[name:'select the mode (Saswell and GiEX)', type: 'ENUM', description: 'Set Irrigation Mode', constraints: ['duration', 'capacity']]]
        command 'setValveOpenThreshold', [[name:'Valve Open Threshold, % (FrankEver FK_V02)', type: 'NUMBER', description: 'Valve Open Threshold, % (FrankEver FK_V02)', constraints: ['0..100']]]
        command 'setValve2', [[name:'select state (TZE284)', type: 'ENUM', description: 'Set TZE284 second valve Mode', constraints: ['open', 'closed']]]

        if (_DEBUG == true) {
            command 'testTuyaCmd', [
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']],
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']],
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type']
            ]
            command 'test', [[name:'description', type: 'STRING', description: 'description', constraints: ['STRING']]]
            command 'testX'
        }

        deviceProfilesV2.each { profileName, profileMap ->
            if (profileMap.fingerprints != null) {
                profileMap.fingerprints.each {
                    fingerprint it
                }
            }
        }
    }

    preferences {
        input(name: 'txtEnable', type: 'bool', title: '<b>Description text logging</b>', description: 'Display measured values in HE log page. Recommended value is <b>true</b>', defaultValue: true)
        input(name: 'logEnable', type: 'bool', title: '<b>Debug logging</b>', description: 'Debug information, useful for troubleshooting. Recommended value is <b>false</b>', defaultValue: DEFAULT_DEBUG_LOGGING)
        if (device) {
            if (deviceProfilesV2[getModelGroup()]?.capabilities?.powerOnBehaviour != false) {
                input(name: 'powerOnBehaviour', type: 'enum', title: '<b>Power-On Behaviour</b>', description:'Select Power-On Behaviour', defaultValue: '2', options: powerOnBehaviourOptions)
            }
            if (isSASWELL() || isGIEX() || isSonoff() || isFankEver() || isTZE284()) {
                input(name: 'autoOffTimer', type: 'number', title: '<b>Auto off timer (Irrigation Duration)</b> ', description: 'Automatically turn off after how many seconds or minutes<br>(depending on the model).<br>Zero value disables the auto-off!', defaultValue: DEFAULT_AUTOOFF_TIMER, required: false)
            }
            if (isSASWELL() || isGIEX()) {
                input(name: 'irrigationCapacity', type: 'number', title: '<b>Irrigation Capacity</b>', description: 'Automatically turn off agter how many liters?', defaultValue: DEFAULT_CAPACITY, required: false)
            }
            if (isFankEver()) {
                input(name: 'valveOpenThreshold', type: 'number', title: '<b>Valve Open Thrfeshold, %</b>', description: 'Valve Open Threshold, %<br>(FrankEver only)', range: "0..100", step: 10, defaultValue: 100, required: false)
                if (valveOpenThreshold != null ) {
                    int roundedValue = Math.floor((valveOpenThreshold + 5) / 10) * 10
                    if ((valveOpenThreshold % 10) != 0) {
                        device.updateSetting('valveOpenThreshold', [value: roundedValue, type: 'number'])
                    }
                    if ((device.currentValue('valveOpenThreshold') ?: UNKNOWN) != valveOpenThreshold) {
                        sendEvent(name: 'valveOpenThreshold', value: roundedValue, type: 'digital')
                    }
                }
            }
            input(name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These options should have been set automatically by the driver<br>Manually changes may not always work!', defaultValue: false)
            if (advancedOptions == true) {
                input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Forcely change the Device Profile, if the valve model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfiles())
                input(name: 'autoSendTimer', type: 'bool', title: '<b>Send the timeout timer automatically</b>', description: 'Send the configured timeout value on every open and close command <b>(GiEX)</b>', defaultValue: true)
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: 'Experimental multi-state switch events', defaultValue: false
            }
        }
    }
}

String getModelGroup()          { return state.deviceProfile ?: 'UNKNOWN' }
Set<String> getDeviceProfiles()      { deviceProfilesV2.keySet() }
boolean isConfigurable(String model)    { return (deviceProfilesV2["$model"]?.preferences != null && deviceProfilesV2["$model"]?.preferences != []) }
String getPowerSource(String profile = null) { String ps = deviceProfilesV2["${profile ?: getModelGroup()}"]?.attributes?.powerSource; return ps != null && !ps.isEmpty() ? ps : 'unknown' }
boolean isConfigurable()         { return isConfigurable(getModelGroup()) }
boolean isGIEX()                 { return getModelGroup().contains('GIEX') }    // GiEX valve device
boolean isSASWELL()              { return getModelGroup().contains('SASWELL') }
boolean isLIDL()                 { return getModelGroup().contains('LIDL') }
boolean isTS0001()               { return getModelGroup().contains('TS0001') }
boolean isTS0011()               { return getModelGroup().contains('TS0011') }
boolean isTS0049()               { return getModelGroup().contains('TS0049') }
boolean isBatteryPowered()       { return isGIEX() || isSASWELL() || isTS0049() }
boolean isFankEver()             { return getModelGroup().contains('FRANKEVER') }
boolean isSonoff()               { return getModelGroup().contains('SONOFF') }
boolean isTZE284()               { return getModelGroup().contains('TZE284') || _DEBUG == true }

// Constants
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3
@Field static final Integer DEFAULT_POLLING_INTERVAL = 15
@Field static final Integer DEFAULT_AUTOOFF_TIMER = 0   // 0 means no auto-off - changed 2024-09-23
@Field static final Integer MAX_AUTOOFF_TIMER = 86400
@Field static final Integer DEFAULT_CAPACITY = 99
@Field static final Integer MAX_CAPACITY = 999
@Field static final Integer DEBOUNCING_TIMER = 300
@Field static final Integer DIGITAL_TIMER = 3000
@Field static final Integer REFRESH_TIMER = 3000
@Field static final Integer COMMAND_TIMEOUT = 6
@Field static final Integer MAX_PING_MILISECONDS = 10000    // rtt more than 10 seconds will be ignored
@Field static String UNKNOWN = 'UNKNOWN'
@Field static final String  DATE_TIME_UNKNOWN = '****-**-** **:**:**'
@Field static final String  NUMBER_UNKNOWN = '***'

// WaterMode  for _TZE200_sh1btabb : duration=0 / capacity=1

@Field static final Map waterModeOptions = [
    '0': 'duration',
    '1': 'capacity'
]

@Field static final Map powerOnBehaviourOptions = [
    '0': 'closed',
    '1': 'open',
    '2': 'last state'
]

@Field static final Map switchTypeOptions = [
    '0': 'toggle',
    '1': 'state',
    '2': 'momentary'
]

@Field static final Map timerStateOptions = [
    '0': 'disabled',
    '1': 'active (on)',
    '2': 'enabled (off)'
]

@Field static final Map weatherDelayOptions = [
    '0': 'disabled',
    '1': '24h',
    '2': '48h',
    '3': '72h'
]

@Field static final Map batteryStateOptions = [
    '0': 'low',
    '1': 'middle',
    '2': 'high'
]

@Field static final Map smartWeatherOptions = [
    '0': 'sunny',
    '1': 'clear',
    '2': 'cloud',
    '3': 'cloudy',
    '4': 'rainy',
    '5': 'snow',
    '6': 'fog'
]

@Field static final Map tze284StateOptions = [
    '0': 'manual',
    '1': 'auto',
    '2': 'idle'
]

// // Valve Work State (Valve working status)  // 0 - 'manual control'; 1 - 'Cycle timing / quantity control''; 2 - 'Schedule control'
@Field static final Map SonoffWorkStateOptions = [
    '0': 'manual control',
    '1': 'Cycle timing / quantity control',
    '2': 'Schedule control'
]

// TODO : change 'model' to 'models' list; combine TS0001_VALVE_ONOFF TS0011_VALVE_ONOFF TS011F_VALVE_ONOFF in one profile;
@Field static final Map deviceProfilesV2 = [
    'TS0001_VALVE_ONOFF'  : [
            model         : 'TS0001',
            manufacturers : ['_TZ3000_iedbgyxt', '_TZ3000_o4cjetlm', '_TYZB01_4tlksk8a', '_TZ3000_h3noz0a5', '_TZ3000_5ucujjts'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0003,0004,0005,0006,E000,E001,0000', outClusters:'0019,000A',     model:'TS0001', manufacturer:'_TZ3000_iedbgyxt'],    // https://community.hubitat.com/t/generic-zigbee-3-0-valve-not-getting-fingerprint/92614
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,E000,E001', outClusters:'0019,000A',     model:'TS0001', manufacturer:'_TZ3000_o4cjetlm'],    // https://community.hubitat.com/t/water-shutoff-valve-that-works-with-hubitat/32454/59?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0000, 0003, 0006', outClusters:'0003, 0006, 0004', model:'TS0001', manufacturer:'_TYZB01_4tlksk8a'], // clusters verified
                [profileId:'0104', endpointId:'01', inClusters:'0003,0004,0005,0006,E000,E001,0000', outClusters:'0019,000A',     model:'TS0001', manufacturer:'_TZ3000_h3noz0a5'],    // clusters verified
                [profileId:'0104', endpointId:'01', inClusters:'0000,0006,0003,0004,0005,E001',      outClusters:'0019',          model:'TS0001', manufacturer:'_TZ3000_5ucujjts']     // https://community.hubitat.com/t/release-tuya-zigbee-valve-driver-w-healthstatus/92788/85?u=kkossev
            ],
            deviceJoinName: 'Tuya Zigbee Valve TS0001',
            capabilities  : ['valve': true, 'battery': false],
            attributes    : ['valve': '', 'healthStatus': 'unknown', 'powerSource': 'dc'],
            configuration : ['battery': false],
            preferences   : [
                'powerOnBehaviour' : [ name: 'powerOnBehaviour', type: 'enum', title: '<b>Power-On Behaviour</b>', description:'Select Power-On Behaviour', defaultValue: '2', options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],

    'TS0011_VALVE_ONOFF'  : [
            model         : 'TS0011',
            manufacturers : ['_TYZB01_rifa0wlb',  '_TYZB01_ymcdbl3u'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,0006',                outClusters:'0019',          model:'TS0011', manufacturer:'_TYZB01_rifa0wlb'],     // https://community.hubitat.com/t/tuya-zigbee-water-gas-valve/78412
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,0702,0B04', outClusters:'0019',          model:'TS0011', manufacturer:'_TYZB01_ymcdbl3u']      // clusters verified
            ],
            deviceJoinName: 'Tuya Zigbee Valve TS0011',
            capabilities  : ['valve': true, 'battery': false],
            attributes    : ['healthStatus': 'unknown', 'powerSource': 'dc'],
            configuration : ['battery': false],
            preferences   : [
                'powerOnBehaviour' : [ name: 'powerOnBehaviour', type: 'enum', title: '<b>Power-On Behaviour</b>', description:'Select Power-On Behaviour', defaultValue: '2', options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],

    'TS011F_VALVE_ONOFF'  : [
            model         : 'TS0011',
            manufacturers : ['_TZ3000_rk2yzt0u'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,E000,E001', outClusters:'0019,000A',     model:'TS011F', manufacturer:'_TZ3000_rk2yzt0u']     // clusters verified! model: 'ZN231392'
            ],
            deviceJoinName: 'Tuya Zigbee Valve TS011F',
            capabilities  : ['valve': true, 'battery': false],
            configuration : ['battery': false],
            attributes    : ['healthStatus': 'unknown', 'powerSource': 'dc'],
            preferences   : [
                'powerOnBehaviour' : [ name: 'powerOnBehaviour', type: 'enum', title: '<b>Power-On Behaviour</b>', description:'Select Power-On Behaviour', defaultValue: '2', options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],

    'TS0601_VALVE_ONOFF'  : [            // model 'PM02D-TYZ' model: 'PF-PM02D-TYZ', vendor: 'IOTPerfect', IOTPerfect PF-PM02D-TYZ   https://www.aliexpress.com/item/1005002822008845.html
            model         : 'TS0601',
            manufacturers : ['_TZE200_vrjkcam9', '_TZE200_yxcgyjf1', '_TZE200_d0ypnbvn'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_vrjkcam9'],     // https://community.hubitat.com/t/tuya-zigbee-water-gas-valve/78412?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_yxcgyjf1'],     // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_d0ypnbvn']      // Model: PF-PM02D-TYZ https://community.hubitat.com/t/release-tuya-zigbee-valve-driver-w-healthstatus/92788/113?u=kkossev
            ],
            deviceJoinName: 'Tuya Zigbee Valve TS0601',
            capabilities  : ['valve': true, 'battery': false],
            configuration : ['battery': false],
            attributes    : ['healthStatus': 'unknown', 'powerSource': 'dc'],
            preferences   : [
                'powerOnBehaviour' : [ name: 'powerOnBehaviour', type: 'enum', title: '<b>Power-On Behaviour</b>', description:'Select Power-On Behaviour', defaultValue: '2', options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],

    'TS0601_GIEX_VALVE'   : [         // https://www.aliexpress.com/item/1005004222098040.html    // GiEX valve device
            model         : 'TS0601',        // https://github.com/Koenkk/zigbee-herdsman-converters/blob/21a66c05aa533de356a51c8417073f28092c6e9d/devices/giex.js
            manufacturers : ['_TZE200_sh1btabb', '_TZE200_a7sghmms', '_TZE204_7ytb3h8u'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000',                outClusters:'0019,000A',     model:'TS0601', manufacturer:'_TZE200_sh1btabb'],    // WaterIrrigationValve
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000',                outClusters:'0019,000A',     model:'TS0601', manufacturer:'_TZE200_a7sghmms'],    // WaterIrrigationValve
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000',                outClusters:'0019,000A',     model:'TS0601', manufacturer:'_TZE204_7ytb3h8u']     // https://www.amazon.com/dp/B0D3BXVZKY
            ],
            deviceJoinName: 'Tuya Zigbee Irrigation Valve',
            capabilities  : ['valve': true, 'battery': true],        // no consumption reporting ?
            configuration : ['battery': false],
            attributes    : ['healthStatus': 'unknown', 'powerSource': 'battery'],
            preferences   : [
                'powerOnBehaviour' : [ name: 'powerOnBehaviour', type: 'enum', title: '<b>Power-On Behaviour</b>', description:'Select Power-On Behaviour', defaultValue: '2', options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],

    'TS0601_SASWELL_VALVE'    : [
            model         : 'TS0601',
            manufacturers : ['_TZE200_akjefhj5', '_TZE200_81isopgh', '_TZE200_2wg5qrjy', '_TZE200_fphxkxue'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,0702,EF00', outClusters:'0019',      model:'TS0601', manufacturer:'_TZE200_akjefhj5'],     // SASWELL SAS980SWT-7-Z01 (RTX ZVG1 ) (_TZE200_akjefhj5, TS0601) https://github.com/zigpy/zha-device-handlers/discussions/1660
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00',                outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_81isopgh'],     // "SAS980SWT-7-Z01(EU)" // https://community.hubitat.com/t/release-tuya-zigbee-valve-driver-w-healthstatus/92788/184?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,0702,EF00', outClusters:'0019',      model:'TS0601', manufacturer:'_TZE200_2wg5qrjy'],     // not tested //
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00',                outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_fphxkxue']      // NovaDigital
            ],
            deviceJoinName: 'Saswell Zigbee Irrigation Valve',
            instructions  : 'https://fccid.io/2AOIFSAS980SWT/User-Manual/User-Manual-5361734.pdf',
            capabilities  : ['valve': true, 'battery': true],
            configuration : ['battery': false],
            attributes    : ['healthStatus': 'unknown', 'powerSource': 'battery', 'battery': '---', 'timerTimeLeft': '---', 'lastValveOpenDuration': '---'],
            tuyaCommands  : ['timerState': '0x02', 'timerTimeLeft': '0x0B'],
            preferences   : [
                'powerOnBehaviour' : [ name: 'powerOnBehaviour', type: 'enum', title: '<b>Power-On Behaviour</b>', description:'Select Power-On Behaviour', defaultValue: '2', options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],

    'TS0601_LIDL_VALVE'   : [
            model         : 'TS0601',                                    // TS0601 _TZE200_c88teujp model: 'PSBZS A1'   PARKSIDE? Smart Irrigation Computer     Lidl https://www.lidl.de/p/parkside-smarter-bewaesserungscomputer-zigbee-smart-home/p100325201
            manufacturers : ['_TZE200_htnnfasr', '_TZE200_c88teujp'],    // TS0601 _TZE200_htnnfasr 'Parkside smart watering timer' -  only DP1 and 5 (timer) !!!  'PSBZS A1',    // https://github.com/mgrom/zigbee-herdsman-converters/blob/ce171e86f9bde6004046b9f4a3701b8024569a2a/devices/lidl.js
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,EF00', outClusters:'000A,0019', model:'TS0601', manufacturer:'_TZE200_htnnfasr'],     // not tested // LIDL // PARKSIDE? Smart Irrigation Computer //https://www.lidl.de/p/parkside-smarter-bewaesserungscomputer-zigbee-smart-home/p100325201
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,EF00', outClusters:'000A,0019', model:'TS0601', manufacturer:'_TZE200_htnnfasr']      // not tested // LIDL // PARKSIDE? Smart Irrigation Computer //https://www.lidl.de/p/parkside-smarter-bewaesserungscomputer-zigbee-smart-home/p100325201
            ],
            deviceJoinName: 'LIDL Parkside smart watering timer',        // also https://gist.github.com/zinserjan/e0486af73d0aa8c6aeed31762e831022
            capabilities  : ['valve': true, 'battery': true],            // Lidl commands set : https://github.com/Koenkk/zigbee2mqtt/issues/7695#issuecomment-1084932081
            configuration : ['battery': false],
            attributes    : ['healthStatus': 'unknown', 'powerSource': 'battery', 'battery': '---'],
            tuyaCommands  : ['switch': '0x01', 'timeSchedule': '0x6B', 'frostReset': '0x6D'],
            preferences   : [
            //                "powerOnBehaviour" : [ name: "powerOnBehaviour", type: "enum", title: "<b>Power-On Behaviour</b>", description:"Select Power-On Behaviour", defaultValue: "2", options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],

    'TS0601_FRANKEVER_FK_V02'    : [    // isFankEver()  // https://www.zigbee2mqtt.io/devices/FK_V02.html
            model         : 'TS0601',                    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/a4a2ac2e382508fc911d721edbb9d4fa308a68bb/converters/fromZigbee.js#L5013
            manufacturers : ['_TZE200_wt9agwf3', '_TZE200_5uodvhgc', '_TZE200_1n2zev06'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_1n2zev06'],     //https://community.hubitat.com/t/frankever-zigbee-1-water-valve/140694?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_5uodvhgc'],    // https://www.youtube.com/watch?v=lpL6xAYuBHk
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_wt9agwf3'],
            ],
            deviceJoinName: 'FrankEver Zigbee Smart Water Valve FK_V02',
            capabilities  : ['valve': true, 'battery': true],
            configuration : ['battery': false],
            attributes    : ['healthStatus': 'unknown', 'powerSource': 'battery', 'battery': '---', 'timerTimeLeft': '---', 'lastValveOpenDuration': '---'],
            tuyaCommands  : ['switch': '0x01', 'valveOpenThreshold': '0x65', 'timer': '0x09'],
            // https://github.com/Koenkk/zigbee-herdsman-converters/blob/f2704346e27431ae3f77c398e4f434c88adec149/src/devices/frankever.ts#L10
            // https://github.com/u236/homed-service-zigbee/blob/66c507b47d720908bfb3ec2a0e8c6d9a79039d94/deploy/data/usr/share/homed-zigbee/tuya.json#L408
            // state: 1,    status": {"enum": ["off", "on"]},
            // frankEverTimer: 9,   // frankEverTimer: {timer: value / 60} 0 ..600 (minutes) 'Countdown timer in minutes'       "timer": {"min": 0, "max": 600},
            //                 9 Switch 1 timer value range 0-86400, pitch 1, unit sec
            // frankEverTreshold: 101   // threshold 0..100 'Valve open percentage (multiple of 10)'        "threshold": {"min": 0, "max": 100, "step": 10, "unit": "%", "control": true, "icon": "mdi:percent"},
            // "lock": "valve"
            preferences   : [
                //'powerOnBehaviour' : [ name: 'powerOnBehaviour', type: 'enum', title: '<b>Power-On Behaviour</b>', description:'Select Power-On Behaviour', defaultValue: '2', options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],

    'TS0049_IRRIGATION_VALVE'    : [    // isTS0049()
            model         : 'TS0049',     // https://github.com/Koenkk/zigbee2mqtt/issues/15124#issuecomment-1435490104
            manufacturers : ['_TZ3210_0jxeoadc', '_TZ3000_hwnphliv','_TZ3000_srldgdxz'],   // https://github.com/Koenkk/zigbee2mqtt/issues/15124
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'EF00,0000', outClusters:'0019,000A', model:'TS0049', manufacturer:'_TZ3210_0jxeoadc'],     // https://www.amazon.com.au/dp/B0BX47V4YB
                [profileId:'0104', endpointId:'01', inClusters:'EF00,0000', outClusters:'0019,000A', model:'TS0049', manufacturer:'_TZ3000_hwnphliv'],     // not tested // (FrankEver model FK-WT03W)
                [profileId:'0104', endpointId:'01', inClusters:'EF00,0000', outClusters:'0019,000A', model:'TS0049', manufacturer:'_TZ3000_srldgdxz']      //
            ],
            deviceJoinName: 'TS0049 Zigbee Irrigation Valve',
            capabilities  : ['valve': true, 'battery': true],
            configuration : ['battery': false],
            attributes    : ['healthStatus': 'unknown', 'powerSource': 'battery', 'battery': '---', 'timerTimeLeft': '---', 'lastValveOpenDuration': '---'],
            // https://github.com/Koenkk/zigbee2mqtt/issues/15124#issuecomment-1345161859
            // 00 - ??; 26 - "error_status"; 101(0x65) - "on_off"(bool); 0x66-???(bool); 0x67-??(bool); 0x69-??;0x6A-??; 0x6D-??(8bit) 110(0x6E) - ??; 111(0x6F) - "irrigation_time" or countdown(32bit); 115(0x73) - battery state: Low = 0x00 Medium = 0x01 High = 0x02;
            tuyaCommands  : ['switch': '0x65'],
            preferences   : [
                'powerOnBehaviour' : [ name: 'powerOnBehaviour', type: 'enum', title: '<b>Power-On Behaviour</b>', description:'Select Power-On Behaviour', defaultValue: '2', options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],


    'SONOFF_SWV_VALVE'    : [           // isSonoff()
            model         : 'SWV',      //https://github.com/Koenkk/zigbee-herdsman-converters/blob/97f8236ec184a3b5df09adca1168868deceaaa91/src/devices/sonoff.ts#L1104-L1138
            manufacturers : ['SONOFF'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0006,0020,0404,0B05,FC57,FC11', outClusters:'000A,0019', model:'SWV', manufacturer:'SONOFF'],
            ],
            deviceJoinName: 'Sonoff Zigbee smart water valve SWV',
            capabilities  : ['valve': true, 'battery': true, 'powerOnBehaviour': false],
            configuration : ['battery': false],
            attributes    : ['healthStatus': 'unknown', 'powerSource': 'battery', 'battery': '---', 'timerTimeLeft': '---', 'lastValveOpenDuration': '---'],
            preferences   : [
                'powerOnBehaviour' : [ name: 'powerOnBehaviour', type: 'enum', title: '<b>Power-On Behaviour</b>', description:'Select Power-On Behaviour', defaultValue: '2', options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],

    'TS0601_TZE284_VALVE'   : [              // https://de.aliexpress.com/item/1005007836145637.html
            model         : 'TS0601',        // https://github.com/zigpy/zha-device-handlers/blob/a1f6378fba3a727b5f9432d711ef3d5320e45827/zhaquirks/tuya/ts0601_valve.py#L489 
            manufacturers : ['_TZE284_8zizsafo'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00,0000,ED00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE284_8zizsafo'],    // GX-03ZG
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00,0000,ED00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE284_eaet5qt5'],    // Insoma SGW08W https://www.aliexpress.us/item/3256807355418184.html
            ],
            deviceJoinName: 'GiEX Zigbee TZE284 Double Valve',
            capabilities  : ['valve': true, 'battery': true],
            configuration : ['battery': false],
            attributes    : ['healthStatus': 'unknown', 'powerSource': 'battery'],
            preferences   : [
                'powerOnBehaviour' : [ name: 'powerOnBehaviour', type: 'enum', title: '<b>Power-On Behaviour</b>', description:'Select Power-On Behaviour', defaultValue: '2', options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],

    'UNKNOWN'      : [                // TODO: _TZE200_5uodvhgc https://github.com/sprut/Hub/issues/1316 https://www.youtube.com/watch?v=lpL6xAYuBHk
        model         : 'UNKNOWN',
        manufacturers : [],
        deviceJoinName: 'Unknown device',
        capabilities  : ['valve': true],
        configuration : ['battery': true],
        attributes    : [],
        batteries     : 'unknown'
    ]
]

void parse(String description) {
    checkDriverVersion()
    if (state.stats == null) { state.stats = [:] }
    if (state.lastRx == null) { state.lastRx = [:] }
    state.stats['RxCtr'] = (state.stats['RxCtr'] ?: 0) + 1
    state.lastRx['parseTime'] = new Date().getTime()
    setHealthStatusOnline()
    unschedule('deviceCommandTimeout')
    logDebug "parse: description is $description"

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {
        return null
    }
    Map event = [:]
    try {
        event = zigbee.getEvent(description)
    }
    catch (e) {
        logDebug "exception ${e} caught while parsing event: ${description}"
        return
    }
    if (event) {
        if (event.name ==  'switch') {
            if (logEnable == true) { log.debug "${device.displayName} event ${event}" }
            sendSwitchEvent(event.value)
        }
        else if (event.name == 'battery') {
            if (logEnable == true) { log.debug "${device.displayName} event ${event}" }
            sendBatteryEvent(event.value.toInteger())
        }
        else if (event.name == 'batteryVoltage') {
            String descriptionText = "Battery voltage is ${event.value} V"
            if (txtEnable == true) { log.info "${device.displayName} ${descriptionText}" }
            sendEvent(name: 'batteryVoltage', value: event.value, descriptionText: descriptionText, unit: 'V', type: 'physical')
        }
        else {
            if (txtEnable) { log.warn "${device.displayName } received <b>unhandled event</b> ${event.name } = $event.value" }
        }
    }
    else {
        Map descMap = [:]
        try {
            descMap = zigbee.parseDescriptionAsMap(description)
        }
        catch (e) {
            logDebug "exception ${e} caught while parsing description: ${descMap}"
            return
        }
        if (logEnable == true) { log.debug "${device.displayName } Desc Map: $descMap" }
        if (descMap.attrId != null) {
            // attribute report received
            List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]]
            descMap.additionalAttrs.each {
                attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status]
            }
            attrData.each {
                // Removed unused variable
                // def map = [:]
                if (it.status == '86') {
                    if (logEnable == true) { log.warn "${device.displayName} Read attribute response: unsupported Attributte ${it.attrId} cluster ${descMap.cluster}" }
                }
                else if (it.cluster == '0000' && it.attrId in ['0001', 'FFE0', 'FFE1', 'FFE2', 'FFE4', 'FFFE', 'FFDF']) {
                    if (it.attrId == '0001') {
                        if (logEnable) { log.debug "${device.displayName} Tuya check-in message (attribute ${it.attrId} reported: ${it.value})" }
                        Long now = new Date().getTime()
                        if (state.lastTx == null) { state.lastTx = [:] }
                        int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger()
                        if (timeRunning < MAX_PING_MILISECONDS) {
                            sendRttEvent()
                        }
                    }
                    else {
                        if (logEnable) { log.debug "${device.displayName} Tuya specific attribute ${it.attrId} reported: ${it.value}" }    // not tested
                    }
                }
                else if (it.cluster == '0000') {
                    if (it.attrId == '0000') {
                        if (logEnable) { log.debug "${device.displayName} zclVersion is :  ${it.value}" }
                    }
                    else if (it.attrId == '0004') {
                        if (logEnable) { log.debug "${device.displayName} Manufacturer is :  ${it.value}" }
                    }
                    else if (it.attrId == '0005') {
                        if (logEnable) { log.debug "${device.displayName} Model is :  ${it.value}" }
                    }
                    else {
                        if (logEnable) { log.debug "${device.displayName} Cluster 0000 attribute ${it.attrId} reported: ${it.value}" }
                    }
                }
                else if (it.cluster == '0006') {
                    if (it.attrId == '4001') {
                        logDebug "cluster ${it.cluster} attribute ${it.attrId} OnTime is ${it.value}"
                    }
                    else if (it.attrId == '4002') {
                        logDebug "cluster ${it.cluster} attribute ${it.attrId} OffWaitTime is ${it.value}"
                    }
                    else if (it.attrId == '8001') {
                        logDebug "cluster ${it.cluster} attribute ${it.attrId} IndicatorMode is ${it.value}"
                    }
                    else if (it.attrId == '8002') {
                        logDebug "cluster ${it.cluster} attribute ${it.attrId} RestartStatus is ${it.value}"
                    }
                    else {
                        logDebug "cluster 0006 attribute ${it.attrId} reported: ${it.value}"
                    }
                }
                else if (it.cluster == '0404') {
                    parseFlowMeasurementCluster(it)
                }
                else if (it.cluster == 'FC11') {
                    parseSonoffCluster(it, description)
                }
                else if (it.cluster == '0B05') {
                    parseDiagnosticCluster(it)
                }
                else {
                    if (logEnable == true) { log.warn "${device.displayName} Unprocessed attribute report: cluster=${it.cluster} attrId=${it.attrId} value=${it.value} status=${it.status} data=${descMap.data}" }
                }
            } // for each attribute
        } // if attribute report
        else if (descMap.profileId == '0000') { //zdo
            parseZDOcommand(descMap)
        }
        else if (descMap.clusterId != null && descMap.profileId == '0104') { // ZHA global command
            parseZHAcommand(descMap)
        }
        else {
            if (logEnable == true)  { log.warn "${device.displayName} Unprocesed unknown command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" }
        }
    } // descMap
}

void sendSwitchEvent(final String switchValue) {
    Map map = [:]
    String value = (switchValue == null) ? 'unknown' : (switchValue == 'on') ? 'open' : (switchValue == 'off') ? 'closed' : 'unknown'
    // Removed unused variable
    // def bWasChange = false
    //boolean bWasChange = false
    boolean debounce   = state.states['debounce'] ?: false
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown'
    if (debounce == true && value == lastSwitch) {    // some devices send only catchall events, some only readattr reports, but some will fire both...
        if (logEnable) { log.debug "${device.displayName } Ignored duplicated switch event for model ${getModelGroup() }" }
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])
        return
    }
    logDebug "sendSwitchEvent: value=${value}  lastSwitch=${state.states['lastSwitch']}"
    boolean isDigital = state.states['isDigital'] ?: false  // bug fixed 2024-09-23
    map.type = isDigital == true ? 'digital' : 'physical'
    if (lastSwitch != value) {
        //bWasChange = true
        if (logEnable) { log.debug "${device.displayName } Valve state changed from <b>${lastSwitch }</b> to <b>${value }</b>" }
        state.states['debounce']   = true
        state.states['lastSwitch'] = value
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])
    }
    else {
        state.states['debounce'] = true
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])
    }

    map.name = 'valve'
    map.value = value
    boolean isRefresh = state.states['isRefresh'] ?: false
    if (isRefresh == true) {
        map.descriptionText = "${device.displayName} is ${value} (Refresh)"
    }
    else {
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]"
    }
    //if ( bWasChange==true )
    //{
    if (txtEnable) { log.info "${device.displayName } ${map.descriptionText }" }
    sendEvent(map)
    //}
    clearIsDigital()
}

void parseZDOcommand(Map descMap) {
    switch (descMap.clusterId) {
        case '0006' :
            if (logEnable) { log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" }
            break
        case '0013' : // device announcement
            logInfo "Received device announcement, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})"
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1
            break
        case '8001' :  // Device and Service Discovery - IEEE_addr_rsp
            if (logEnable) { log.info "${device.displayName} Received Device and Service Discovery - IEEE_addr_rsp, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" }
            break
            break
        case '8004' : // simple descriptor response
            if (logEnable) { log.info "${device.displayName} Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" }
            parseSimpleDescriptorResponse(descMap)
            break
        case '8005' : // endpoint response
            if (logEnable) { log.info "${device.displayName} Received endpoint response: cluster: ${descMap.clusterId} (endpoint response) endpointCount = ${ descMap.data[4]}  endpointList = ${descMap.data[5]}" }
            break
        case '8021' : // bind response
            if (logEnable) { log.info "${device.displayName} Received bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" }
            break
        case '8038' : // Management Network Update Notify
            if (logEnable) { log.info "${device.displayName} Received Management Network Update Notify, data=${descMap.data}" }
            break
        default :
            if (logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" }
    }
}

void parseSimpleDescriptorResponse(Map descMap) {
    logDebug "Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}"
    if (logEnable == true) { log.info "${device.displayName} Endpoint: ${descMap.data[5]} Application Device:${descMap.data[9]}${descMap.data[8]}, Application Version:${descMap.data[10]}" }
    int inputClusterCount = hubitat.helper.HexUtils.hexStringToInt(descMap.data[11])
    String inputClusterList = ''
    if (inputClusterCount != 0) {
        for (int i in 1..inputClusterCount) {
            inputClusterList += descMap.data[13 + (i - 1) * 2] + descMap.data[12 + (i - 1) * 2] + ','
        }
        inputClusterList = inputClusterList.substring(0, inputClusterList.length() - 1)
        if (logEnable == true) { log.info "${device.displayName} endpoint ${descMap.data[5]} Input Cluster Count: ${inputClusterCount} Input Cluster List : ${inputClusterList}" }
        if (descMap.data[5] == device.endpointId) {
            if (getDataValue('inClusters') != inputClusterList)  {
                if (logEnable == true) { log.warn "${device.displayName} inClusters=${getDataValue('inClusters')} differs from inputClusterList:${inputClusterList} - will be updated!" }
                updateDataValue('inClusters', inputClusterList)
            }
        }
    }

    int outputClusterCount = hubitat.helper.HexUtils.hexStringToInt(descMap.data[12 + inputClusterCount * 2])
    String outputClusterList = ''
    if (outputClusterCount != 0) {
        for (int i in 1..outputClusterCount) {
            outputClusterList += descMap.data[14 + inputClusterCount * 2 + (i - 1) * 2] + descMap.data[13 + inputClusterCount * 2 + (i - 1) * 2] + ','
        }
        outputClusterList = outputClusterList.substring(0, outputClusterList.length() - 1)
        if (logEnable == true) { log.info "${device.displayName} endpoint ${descMap.data[5]} Output Cluster Count: ${outputClusterCount} Output Cluster List : ${outputClusterList}" }
        if (descMap.data[5] == device.endpointId) {
            if (getDataValue('outClusters') != outputClusterList)  {
                if (logEnable == true) { log.warn "${device.displayName} outClusters=${getDataValue('outClusters')} differs from outputClusterList:${outputClusterList} -  will be updated!" }
                updateDataValue('outClusters', outputClusterList)
            }
            else { log.warn "device.outClusters = ${device.outClusters } outputClusterList = ${outputClusterList }" }
        }
    }
}

void parseZHAcommand(Map descMap) {
    switch (descMap.command) {
        case '01' : //read attribute response. If there was no error, the successful attribute reading would be processed in the main parse() method.
        case '02' : // version 1.0.2
        case '05' : // version 1.3.1 04/30/2024 TS0049
        case '06' : // version 1.3.1 04/28/2024 TS0049
            String status = descMap.data[2]
            String attrId = descMap.data[1] + descMap.data[0]
            if (status == '86') {
                if (logEnable == true) { log.warn "${device.displayName} Read attribute response: unsupported Attributte ${attrId} cluster ${descMap.clusterId}  descMap = ${descMap}" }
            }
            else {
                switch (descMap.clusterId) {
                    case 'EF00' :
                        //if (logEnable==true) log.debug "${device.displayName} Tuya cluster read attribute response: code ${status} Attributte ${attrId} cluster ${descMap.clusterId} data ${descMap.data}"
                        String cmd = descMap.data[2]
                        int value = getAttributeValue(descMap.data)
                        if (logEnable == true) { log.trace "${device.displayName} Tuya cluster cmd=${cmd} value=${value} ()" }
                        switch (cmd) {
                            case '01' :   // WaterMode  for GiEX : duration=0 / capacity=1
                                if (isGIEX()) {
                                    String str = waterModeOptions[safeToInt(value).toString()]
                                    logInfo "Water Valve Mode (dp=${cmd}) is: ${str} (${value})"  // 0 - 'duration'; 1 - 'capacity'     // TODO - Send to device ?
                                    sendEvent(name: 'waterMode', value: str, type: 'physical')
                                }
                                else if (isGIEX() || isLIDL()) { // switch
                                    sendSwitchEvent(value == 0 ? 'off' : 'on')    // also SASWELL and LIDL
                                    if (settings?.autoSendTimer == true) {
                                        // There is no way to disable the "Auto off" timer for when the valve is turned on manually
                                        // https://github.com/Koenkk/zigbee2mqtt/issues/13199#issuecomment-1239914073
                                        logDebug "scheduled again to set the SASWELL autoOff (irrigation duration) timer to ${settings?.autoOffTimer} after 5 seconds"
                                        runIn(5, 'sendIrrigationDuration')
                                    }
                                }
                                else {
                                    sendSwitchEvent(value == 0 ? 'off' : 'on')    // TS0601, GiEX TZE284
                                }
                                break
                            case '02' : 
                                if (isTZE284()) {   // second valve
                                    String status2 = value == 0 ? 'closed' : 'open' 
                                    logInfo "valve2 is ${status2} (${value})"
                                    sendEvent(name: 'valve2', value: status2, type: 'physical')
                                }
                                else {  // isGIEX() - WaterValveState   1=on 0 = 0ff        // _TZE200_sh1btabb WaterState # off=0 / on=1
                                    String timerState = timerStateOptions[value.toString()]
                                    logInfo "Water Valve State (dp=${cmd}) is ${timerState} (${value})"
                                    sendSwitchEvent(value == 0 ? 'off' : 'on')
                                    sendEvent(name: 'timerState', value: timerState, type: 'physical')
                                    if (settings?.autoSendTimer == true) {
                                        logDebug "scheduled again to set the GiEX autoOff (irrigation duration) timer to ${settings?.autoOffTimer} after 5 seconds"
                                        runIn(5, 'sendIrrigationDuration')
                                    }
                                }
                                break
                            case '03' : // flow_state or percent_state?  (0..100%) SASWELL ?
                                logInfo "flow_state (${cmd}) is: ${value} %"
                                break
                            case '04' : // failure_to_report
                                logInfo "failure_to_report (${cmd}) is: ${value}"
                                break
                            case '05' : // isSASWELL() - measuredValue ( water_once, or last irrigation volume ) ( 0..1000, divisor:10, unit: 'L')
                                // for GiEX - assuming value is reported in fl. oz. ? => { water_consumed: (value / 33.8140226).toFixed(2) }
                                if (isSASWELL()) {
                                    logInfo "SASWELL measuredValue (dp=${cmd}) is: ${value} (data=${descMap.data})"
                                }
                                else if (isGIEX()) {
                                    logInfo "GiEX measuredValue (dp=${cmd}) is: ${(value / 33.8140226).toFixed(2)} (data=${descMap.data})"    // or the reported value is in litres - keep it as it is?
                                }
                                else {
                                    logInfo "measuredValue (dp=${cmd}) is: ${value} (data=${descMap.data})"
                                }
                                break
                            case '06' : // unknown ; LIDL - TODO !!!!
                                logDebug "SASWELL unknown cmd (${cmd}) value is: ${value}"
                                break
                            case '07' : // Battery for SASWELL (0..100%), Countdown for the others?
                                if (isSASWELL()) {
                                    logInfo "battery (${cmd}) is: ${value} %"
                                    sendBatteryEvent(value)
                                }
                                else {
                                    logInfo "Countdown (${cmd}) is: ${value}"
                                }
                                break
                            case '08' : // battery_state batteryStateOptions
                                String valueString = batteryStateOptions[safeToInt(value).toString()]
                                logInfo "battery_state (${cmd}) is: ${valueString} (${value})"
                                break
                            case '09' : // accumulated_usage_time (0..2592000, seconds)
                                if (isFankEver()) {
                                    String descText = "received FrankEver irrigation timer ${value} seconds"
                                    //device.updateSetting('autoOffTimer', [value: value, type: 'number'])
                                    sendEvent(name: 'irrigationDuration', value: value, descriptionText: descText, unit: 's',type: 'physical')
                                    logInfo descText
                                }
                                else {
                                    logInfo "accumulated_usage_time (${cmd}) is: ${value} seconds"
                                }
                                break
                            case '0A' : // (10) weather_delay //   0 -> disabled; 1 -> "24h"; 2 -> "48h";  3 -> "72h"
                                String valueString = weatherDelayOptions[safeToInt(value).toString()]
                                logInfo "weatherDelay (${cmd}) is: ${valueString} (${value})"
                                sendEvent(name: 'weatherDelay', value: valueString, type: 'physical')
                                break
                            case '0B' : // (11) SASWELL countdown timeLeft in seconds timer_time_left "irrigation_time" (0..86400, seconds)
                                if (isLIDL()) {
                                    logInfo "LIDL battery (${cmd}) is: ${value} %"
                                    sendBatteryEvent(value)
                                }
                                else {
                                    logInfo "timer time left (${cmd}) is: ${value} seconds"
                                    sendEvent(name: 'timerTimeLeft', value: value, type: 'physical')
                                }
                                break
                            case '0C' : // (12) SASWELL ("work_state") state 0-disabled 1-active on (open) 2-enabled off (closed) ? or auto/manual/idle ?
                                String valueString = timerStateOptions[safeToInt(value).toString()]
                                logInfo "timer_state (work state) (${cmd}) is: ${valueString} (${value})"
                                sendEvent(name: 'timerState', value: valueString, type: 'physical')
                                break
                            case '0D' : // (13) "smart_weather" for SASWELL or relay status for others?
                                if (isSASWELL()) {
                                    String valueString = smartWeatherOptions[safeToInt(value).toString()]
                                    logInfo "smart_weather (${cmd}) is: ${valueString} (${value})"
                                }
                                else if (isTZE284()) {
                                    logInfo "TZE284 valve1 countdown timer is: ${value} minutes"
                                    sendEvent(name: 'timerTimeLeft', value: value, type: 'physical')
                                }
                                else {
                                    logInfo "relay status (${cmd}) is: ${value}"
                                }
                                break
                            case '0E' : // (14)
                                if (isTZE284()) {
                                    logInfo "TZE284 valve2 countdown timer is: ${value} minutes"
                                    sendEvent(name: 'timerTimeLeft2', value: value, type: 'physical')
                                }
                                else {  // SASWELL "smart_weather_switch"
                                    logInfo "smart_weather_switch (${cmd}) is: ${value}"
                                }
                                break
                            case '0F' : // (15) SASWELL lastValveOpenDuration in seconds last_valve_open_duration (once_using_time, last irrigation duration) (0..86400, seconds)
                                logInfo "last valve open duration is: ${value} seconds"    // also TZE284
                                sendEvent(name: 'lastValveOpenDuration', value: value, type: 'physical')
                                break
                            case '10' : // (16)
                                // (16) SASWELL RawToCycleTimer1 ?     ("cycle_irrigation")
                                // https://github.com/Koenkk/zigbee2mqtt/issues/13199#issuecomment-1205015123
                                logInfo "SASWELL RawToCycleTimer1 (${cmd}) is: ${value}"
                                break
                            case '11' : // (17) SASWELL RawToCycleTimer2 ?     ("normal_timer")
                                logInfo "SASWELL RawToCycleTimer2 (${cmd}) is: ${value}"
                                break
                            case '13' : // (19) inching switch ( once enabled, each time the device is turned on, it will automatically turn off after a period time as preset
                                logInfo "inching switch(!?!) is: ${value}"
                                break
                            case '1A' : // (26) TS049
                                if (isTZE284()) {
                                    logInfo "last valve2 open duration is: ${value} seconds"
                                    sendEvent(name: 'lastValveOpenDuration2', value: value, type: 'physical')
                                }
                                else {
                                    logInfo "TS049 fault (${cmd}) is: ${value}"
                                }
                                break
                            case '3B' : // (59) isTZE284()
                                logInfo "battery (${cmd}) is: ${value} %"
                                sendBatteryEvent(value)
                                break
                            case '65' : // (101) WaterValveIrrigationStartTime for GiEX and LIDL?   // IrrigationStartTime       # (string) ex: "08:12:26"
                                if (isTS0049()) {   // TS0049 valve on/off
                                    String switchValue = value == 0 ? 'off' : 'on'
                                    logInfo "TS0049 Valve (dp=${cmd}) switch is ${switchValue} ${value})"
                                    sendSwitchEvent(switchValue)        
                                }
                                else if (isFankEver()) {
                                    logInfo "reported valve open threshold is ${value} %"
                                    sendEvent(name: 'valveOpenThreshold', value: value, type: 'physical') 
                               }
                                else {
                                    String str = getAttributeString(descMap.data)
                                    logInfo "IrrigationStartTime (${cmd}) is: ${str}"
                                    sendEvent(name: 'irrigationStartTime', value: str, type: 'physical')
                                }
                                break
                            case '66' : // (102) WaterValveIrrigationEndTime  for GiEX    // IrrigationStopTime        # (string) ex: "08:13:36"
                                if (isTS0049()) {
                                    String str = waterModeOptions[safeToInt(value).toString()]
                                    logInfo "Water Valve Mode (dp=${cmd}) is: ${str} (${value})"  // 0 - 'duration'; 1 - 'capacity'
                                    sendEvent(name: 'waterMode', value: str, type: 'physical')
                                }
                                else if (isFankEver()) {
                                    logInfo "reported valve open percentage is ${value} %"
                                    sendEvent(name: 'valveOpenPercentage', value: value, type: 'physical') 
                               }
                                else {
                                    String str = getAttributeString(descMap.data)
                                    logInfo "IrrigationEndTime (${cmd}) is: ${str}"
                                    sendEvent(name: 'irrigationEndTime', value: str, type: 'physical')
                                }
                                break
                            case '67' : // (103) WaterValveCycleIrrigationNumTimes  for GiEX        // CycleIrrigationNumTimes   # number of cycle irrigation times, set to 0 for single cycle        // TODO - Send to device cycle_irrigation_num_times ?
                                if (isTS0049()) {
                                    String switchValue = value == 0 ? 'off' : 'on'
                                    logInfo "TS0049 Valve (dp=${cmd}) rain sensor is ${switchValue} ${value})"
                                }
                                else {
                                    if (txtEnable == true) { log.info "${device.displayName} CycleIrrigationNumTimes (${cmd}) is: ${value}" }
                                }
                                break
                            case '68' : // (104) WaterValveIrrigationTarget for GiEX        // IrrigationTarget   for _TZE200_sh1btabb       # duration in minutes or capacity in Liters (depending on mode)
                                if (isTS0049()) {
                                    logInfo "Automatic Execution Status (dp=${cmd}) is: (${value})"
                                }
                                else if (isTZE284()) {
                                    String valveStatus = tze284StateOptions[value.toString()]
                                    descText = "valveStatus is ${valveStatus}"
                                    logInfo "${descText} (${value})"
                                    sendEvent(name: 'valveStatus', value: valveStatus, descriptionText:descText, type: 'physical')
                                }
                                else {
                                    logInfo "IrrigationTarget (${cmd}) is: ${value}"            // TODO - Send to device irrigation_target?
                                }
                                break
                            case '69' : // (105) WaterValveCycleIrrigationInterval for GiEX      // CycleIrrigationInterval   # cycle irrigation interval (minutes, max 1440)                        // TODO - Send to device cycle_irrigation_interval ?
                                if (isTS0049()) {   // count down timer
                                    logInfo "TS049 timer time left (${cmd}) is: ${value}"   // seconds or minutes?
                                    sendEvent(name: 'timerTimeLeft', value: value, type: 'physical')
                                }
                                else if (isTZE284()) {
                                    String valveStatus = tze284StateOptions[value.toString()]
                                    descText = "valveStatus2 is ${valveStatus}"
                                    logInfo "${descText} (${value})"
                                    sendEvent(name: 'valveStatus2', value: valveStatus, descriptionText:descText, type: 'physical')
                                }
                                else {
                                    if (txtEnable == true) { log.info "${device.displayName} CycleIrrigationInterval (${cmd}) is: ${value}" }
                                }
                                break
                            case '6A' : // (106) WaterValveCurrentTempurature            // CurrentTemperature        # (value ignored because isn't a valid tempurature reading.  Misdocumented and usage unclear)
                                if (isTS0049()) {
                                    logInfo "TS0049 Valve (dp=${cmd}) loop timing is ${value}"
                                }
                                else {
                                    if (txtEnable == true) { log.info "${device.displayName} ?CurrentTempurature? (${cmd}) is: ${value}" }       // ignore!
                                }
                                break
                            case '6B' : // (107) - LIDL time schedile                    // https://github.com/Koenkk/zigbee2mqtt/issues/7695#issuecomment-868509538
                                if (isTS0049()) {
                                    logInfo "TS0049 Automatic Mode Distinction (dp=${cmd}) is ${value}"
                                }
                                else {
                                    logInfo "${device.displayName} LIDL time schedile (${cmd}) is: ${value}"
                                }
                                break
                            case '6C' : // (108) WaterValveBattery for GiEX    // 0001/0021,mul:2           # match to BatteryPercentage
                                if (isGIEX()) {
                                    logInfo "GiEX Battery (${cmd}) is: ${value} %"
                                    sendBatteryEvent(value)
                                }
                                else if (isTS0049()) {
                                    logInfo "TS0049 Effective Time Period (dp=${cmd}) is ${value}"
                                }
                                else {    // Lidl
                                    logInfo "LIDL battery (${cmd}) is: ${value} %"
                                    sendBatteryEvent(value)
                                }
                                break
                            case '6D' : // (109) LIDL frost reset
                                if (isTS0049()) {
                                    logInfo "TS0049 Valve (dp=${cmd}) model is ${value}"
                                }
                                else {
                                    logInfo "LIDL reset frost alarmcommand (${cmd}) is: ${value}"    // to be sent to the device! TODO: reset frost alarm : https://github.com/Koenkk/zigbee2mqtt/issues/7695#issuecomment-1084774734 - command 0x6D ?TYPE_ENUM value 01
                                }
                                break
                            case '6E' : // (110) TS0049
                                logInfo "TS0049 Log Report (dp=${cmd}) is ${value}"
                                break
                            case '6F' : // (111) WaterValveWaterConsumed for GiEX       // WaterConsumed             # water consumed (Litres)
                                if (isTS0049()) {   // TS0049 irrigation time
                                    logInfo "TS0049 irrigation time (${cmd}) is: ${value} minutes"
                                    sendEvent(name: 'timerTimeLeft', value: value, type: 'physical')
                                }
                                else {
                                    logInfo "WaterConsumed (${cmd}) is: ${value} (Litres)"
                                    sendEvent(name: 'waterConsumed', value: value, type: 'physical')
                                }
                                break
                            case '70' : // (112)
                                logInfo "TS0049 Flow Reset (dp=${cmd}) is ${value}"
                                break
                            case '71' : // (113)
                                logInfo "TS0049 Temp Current (dp=${cmd}) is ${value}"
                                break
                            case '72' : // (114) WaterValveLastIrrigationDuration for GiEX   LastIrrigationDuration    # (string) Ex: "00:01:10,0"
                                if (isTS0049()) {   // TS0049 battery
                                    logInfo "TS0049 Humidity Value (dp=${cmd}) is ${value}"
                                }
                                else {    // GiEX (or LIDL
                                    String str = getAttributeString(descMap.data)
                                    if (txtEnable == true) { log.info "${device.displayName} LastIrrigationDuration (${cmd}) is: ${value}" }
                                    sendEvent(name: 'lastIrrigationDuration', value: str, type: 'physical')
                                }
                                break
                            case '73' : // (115) TS0049 battery
                                String valueString = batteryStateOptions[safeToInt(value).toString()]
                                logInfo "TS0049 battery_state (${cmd}) is: ${valueString} (${value})"
                                sendBatteryEvent(value == 0 ? 33 : value == 1 ? 66 : value == 2 ? 100 : 0)
                                break
                            // case '74' : // (116) TS0049 - MaxTemp Set
                            // case '75' : // (117) TS0049 - MinTemp Set
                            // case '76' : // (118) TS0049 - MaxHum  Set
                            // case '77' : // (119) TS0049 - MinHum  Set
                            // case '78' : // (120) TS0049 - Charge State
                            // case '79' : // (121) TS0049 - Water Once
                            // case '7A' : // (122) TS0049 - Flowrate Total
                            // case '7B' : // (123) TS0049 - Water Supply Pressure
                            // case '7C' : // (124) TS0049 - Flow Rate Instant Value
                            // case '7D' : // (125) TS0049 - Flow Calibration
                            case 'D1' : // cycle timer
                                if (txtEnable == true) { log.info "${device.displayName} cycle timer (${cmd}) is: ${value}" }
                                break
                            case 'D2' : // random timer
                                if (txtEnable == true) { log.info "${device.displayName} cycle timer (${cmd}) is: ${value}" }
                                break
                            default :
                                if (logEnable == true) { log.warn "Tuya unknown attribute: ${descMap.data[0]}${descMap.data[1]}=${descMap.data[2]}=${descMap.data[3]}${descMap.data[4]} data.size() = ${descMap.data.size()} value: ${value}}" }
                                if (logEnable == true) { log.warn "map= ${descMap}" }
                                break
                        } // EF00 command swotch
                        break

                    case 'EF01' :
                        logInfo "EF01 timer time left /* (${cmd}) is: ${value} seconds */"
                        //sendEvent(name: 'timerTimeLeft', value: value, type: "physical")
                        break
                    case 'EF02' :
                        logInfo "EF02 timer_state (work state) /* (${cmd}) is: ${valueString} (${value}) */"
                        //sendEvent(name: 'timerState', value: valueString, type: "physical")
                        break
                    case 'EF03' :
                        logInfo "EF03 last valve open duration /* (${cmd}) is: ${value} seconds */"
                        //sendEvent(name: 'lastValveOpenDuration', value: value, type: "physical")
                        break
                    case 'EF04' :
                        logInfo "EF04 unknown (dp4?) /*(${cmd}) is: ${value} seconds*/"
                        break

                    default :
                        if (logEnable == true) { log.warn "${device.displayName} Read attribute response: unknown status code ${status} Attributte ${attrId} cluster ${descMap.clusterId}" }
                        break
                } // switch (descMap.clusterId)
            }  //command is read attribute response 01 or 02 (supported)
            break

        case '04' : //write attribute response
            logDebug "parseZHAcommand writeAttributeResponse cluster: ${descMap.clusterId} status:${descMap.data[0]}"
            break
        case '07' : // Configure Reporting Response
            logDebug "Received Configure Reporting Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})"
            // Status: Unreportable Attribute (0x8c)
            break
        case '0B' : // ZCL Default Response
            String status = descMap.data[1]
            if (status != '00') {
                switch (descMap.clusterId) {
                    case '0006' : // Switch state
                        if (logEnable == true) { log.warn "${device.displayName} standard ZCL Switch state is not supported." }
                        break
                    default :
                        if (logEnable == true) { log.info "${device.displayName} Received ZCL Default Response to Command ${descMap.data[0]} for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" }
                        break
                }
            }
            break
        case '11' :    // Tuya specific
            if (logEnable == true) { log.info "${device.displayName} Tuya specific command: cluster=${descMap.clusterId} command=${descMap.command} data=${descMap.data}" }
            break
        case '24' :    // Tuya time sync
            //log.trace "Tuya time sync"
            if (descMap?.clusterInt == 0xEF00 && descMap?.command == '24') {        //getSETTIME
                if (settings?.logEnable) { log.debug "${device.displayName} time synchronization request from device, descMap = ${descMap}" }
                def offset = 0
                try {
                    offset = location.getTimeZone().getOffset(new Date().getTime())
                //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}"
                }
                catch (e) {
                    if (settings?.logEnable) { log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" }
                }
                def cmds = zigbee.command(0xEF00, 0x24, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8))
                if (settings?.logEnable) { log.trace "${device.displayName} now is: ${now()}" } // KK TODO - convert to Date/Time string!
                if (settings?.logEnable) { log.debug "${device.displayName} sending time data : ${cmds}" }
                cmds.each {
                    sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
                }
                return
            }
            break
        default :
            if (logEnable == true) { log.warn "${device.displayName} Unprocessed global command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" }
    }
}

@Field static final Map valveStatusOptions = [
    '0': 'normal',
    '1': 'shortage',
    '2': 'leakage',
    '3': 'shortage and leakage'
]

import java.text.SimpleDateFormat

void parseSonoffCluster(Map it, String description) {
    logDebug "parseSonoffCluster: ${it}"
    // Define a date format
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    Integer intValue = 0
    String descText = ''
    try {
        intValue = zigbee.convertHexToInt(it.value)
    }
    catch (e) {
        logDebug "parseSonoffCluster: it.value ${it.value} is not a number"
    }
    switch (it.attrId) {
        case '5006' :   // Real-time irrigation duration (0..86400, seconds)
            logDebug "Sonoff cluster 0x${it.cluster} attribute ${it.attrId} value is ${intValue} (raw: ${it.value})"
            descText = "lastValveOpenDuration is ${intValue} seconds"
            sendEvent(name: 'lastValveOpenDuration', value: intValue, descriptionText: descText, type: 'physical')
            logInfo "${descText}"
            break
        case '5007' :   // Real-time Irrigation Volume (0..1000, divisor:10, unit: 'L')
            logDebug "Sonoff cluster 0x${it.cluster} attribute ${it.attrId} value is ${intValue} (raw: ${it.value})"
            descText = "irrigationVolume is ${intValue} L"
            sendEvent(name: 'irrigationVolume', value: intValue, descriptionText: descText, type: 'physical')
            logInfo "${descText}"
            break
        case '5008' :   // Cyclic Timed Irrigation // data type: Char string      0x0A 01 01 00 00 04 B0 00 00 0E 10
            //                                                      raw: E9D701FC11 0A 08 50 42 00,
            logDebug "Sonoff cluster 0x${it.cluster} attribute ${it.attrId} Cyclic Timed Irrigation : value is ${intValue} (raw: ${it.value})"
            break
        case '5009' :   // Cyclic Volume Irrigation // data type: Char string     0x0A 01 01 00 00 00 1E 00 00 0E 10
            //                                                           E9D701FC11 0A 09 50 42 00
            logDebug "Sonoff cluster 0x${it.cluster} attribute ${it.attrId} Cyclic Volume Irrigation : value is ${intValue} (raw: ${it.value})"
            break
        case '500A' :   // Weekly Schedule  // data type: Char string
            logDebug "Sonoff cluster 0x${it.cluster} attribute ${it.attrId} Weekly Schedule : value is ${intValue} (raw: ${it.value})"
            break
        case '500B' :   // Schedule Skip
            logDebug "Sonoff cluster 0x${it.cluster} attribute ${it.attrId} Schedule Skip : value is ${intValue} (raw: ${it.value})"
            break
        case '500C' :	// Valve Abnormal State // valveStatus :  0 - 'normal'; 1 - 'shortage'; 2 - 'leakage'; 3 - 'shortage and leakage'
            logDebug "Sonoff cluster 0x${it.cluster} attribute ${it.attrId} Valve Abnormal State value is ${intValue} (raw: ${it.value})"
            String valveStatus = valveStatusOptions[intValue.toString()]
            descText = "valveStatus is ${valveStatus}"
            logInfo "${descText} (${intValue})"
            sendEvent(name: 'valveStatus', value: valveStatus, descriptionText:descText, type: 'physical')
            break
        case '500D' :   // Irrigation Start Time // uint32  // Local time (since 1970)
            logDebug "Sonoff cluster 0x${it.cluster} attribute ${it.attrId} Irrigation Start Time : value is ${intValue} (raw: ${it.value})"
            Date startDate = new Date((intValue + 946684800L)  * 1000L)
            String startDateString = dateFormat.format(startDate)
            descText = "Irrigation Start Time is ${startDateString}" ; if (settings?.logEnable) { descText += " (raw: ${it.value})" }
            logInfo "${descText}"
            sendEvent(name: 'irrigationStartTime', value: startDateString, descriptionText:descText, type: 'physical')
            break
        case '500E' :   // Irrigation End Time //  uint32  // Local time (since 1970)
            Date endDate = new Date((intValue + 946684800L)  * 1000L)
            String endDateString = dateFormat.format(endDate)
            descText = "Irrigation End Time is ${endDateString}"  ; if (settings?.logEnable) { descText += " (raw: ${it.value})" }
            logInfo "${descText}"
            sendEvent(name: 'irrigationEndTime', value: endDateString, descriptionText:descText, type: 'physical')
            break
        case '500F' :   // Daily Irrigation Volume (Irrigation water volume for the day)    // uint32 (Liter)
            logDebug "Sonoff cluster 0x${it.cluster} attribute ${it.attrId} Daily Irrigation Volume : value is ${intValue} (raw: ${it.value})"
            break
        case '5010' :   // Valve Work State (Valve working status)  // 0 - 'manual control'; 1 - 'Cycle timing / quantity control''; 2 - 'Schedule control'
            String workState = SonoffWorkStateOptions[intValue.toString()]
            descText = "Valve Work State is ${workState}" ; if (settings?.logEnable) { descText += " (raw: ${it.value})" }
            logInfo "${descText}"
            sendEvent(name: 'workState', value: workState, descriptionText:descText, type: 'physical')
            break
        default :
            logDebug "Sonoff cluster 0x${it.cluster} <b>unknown attribute ${it.attrId}</b> value is ${intValue} (raw: ${it.value})"
            break
    }
}

void parseFlowMeasurementCluster(Map it) {
    if (it.attrId == '0000') {
        logDebug "parseFlowMeasurementCluster: ${it}"
        int flowRate = zigbee.convertHexToInt(it.value)
        logInfo "Flow Measurement cluster 0x${it.cluster} attribute ${it.attrId} value is ${flowRate} 0m³/h (raw: ${it.value})"
        sendEvent(name: 'rate', value: flowRate, unit: '0m³/h', type: 'physical')
    }
    else {
        logDebug "Flow Measurement cluster 0x${it.cluster} unknown attribute ${it.attrId} value is ${zigbee.convertHexToInt(it.value)} (raw: ${it.value})"
    }
}

// Read Attribute Response, status=SUCCESS, endpoint=0x01, cluster=0x0B05 (Diagnostics Cluster), attribute=0x011B (Average MAC Retry Per APS Message Sent), value=0003
// Read Attribute Response, status=SUCCESS, endpoint=0x01, cluster=0x0B05 (Diagnostics Cluster), attribute=0x011C (Last Message LQI), value=FC 
// Read Attribute Response, status=SUCCESS, endpoint=0x01, cluster=0x0B05 (Diagnostics Cluster), attribute=0x011D (Last Message RSSI), value=DB
void parseDiagnosticCluster(Map it) {
    logDebug "parseDiagnosticCluster: ${it}"
    String descText = ''
    switch (it.attrId) {
        case '011B' :
            descText = "Average MAC Retry Per APS Message Sent is ${zigbee.convertHexToInt(it.value)} (raw: ${it.value})"
            logInfo "Diagnostics cluster 0x${it.cluster} attribute ${it.attrId} ${descText}"
            break
        case '011C' :
            descText = "Last Message LQI is ${zigbee.convertHexToInt(it.value)} (raw: ${it.value})"
            sendEvent(name: 'lqi', value: zigbee.convertHexToInt(it.value), descriptionText: descText, type: 'physical')
            logInfo "Diagnostics cluster 0x${it.cluster} attribute ${it.attrId} ${descText}"
            break
        case '011D' :
            descText = "Last Message RSSI is ${zigbee.convertHexToInt(it.value)} (raw: ${it.value})"
            sendEvent(name: 'rssi', value: zigbee.convertHexToInt(it.value), descriptionText: descText, type: 'physical')
            logInfo "Diagnostics cluster 0x${it.cluster} attribute ${it.attrId} ${descText}"
            break
        default :
            logDebug "Diagnostics cluster 0x${it.cluster} unknown attribute ${it.attrId} value is ${zigbee.convertHexToInt(it.value)} (raw: ${it.value})"
            break
    }
}

void decodeSonoffCiclicTimedIrrigationAtt005008(Map it) {
    logDebug "decodeSonoffCiclicTimedIrrigationAtt: ${it} it.data.size() = ${it.data.size()}"
    int currentCount, totalNumber, irrigationDuration, irrigationInterval
    if (it.data.size() >= 10) {
        currentCount = zigbee.convertHexToInt(it.data[0])
        totalNumber = zigbee.convertHexToInt(it.data[1])        // 0 - 100 'Total times of circulating irrigation'
        irrigationDuration = (zigbee.convertHexToInt(it.data[2]) << 24) + (zigbee.convertHexToInt(it.data[3]) << 16) + (zigbee.convertHexToInt(it.data[4]) << 8) + zigbee.convertHexToInt(it.data[5])       // 0..86400 'Single irrigation duration', seconds
        irrigationInterval = (zigbee.convertHexToInt(it.data[6]) << 24) + (zigbee.convertHexToInt(it.data[7]) << 16) + (zigbee.convertHexToInt(it.data[8]) << 8) + zigbee.convertHexToInt(it.data[9])       // 0..86400 'Time interval between two adjacent irrigations', seconds
        logDebug "Sonoff cluster 0x${it.cluster} attribute ${it.attrId} value is ${it.value} currentCount=${currentCount} totalNumber=${totalNumber} irrigationDuration=${irrigationDuration} irrigationInterval=${irrigationInterval}"
    }
    else {
        logWarn "Sonoff cluster 0x${it.cluster} attribute ${it.attrId} value is ${it.value} data.size() = ${it.data.size()}"
    }

}

int getAttributeValue(ArrayList _data) {
    int retValue = 0
    try {
        if (_data.size() >= 6) {
            int dataLength = zigbee.convertHexToInt(_data[5]) as Integer
            int power = 1
            for (i in dataLength..1) {
                retValue = retValue + power * zigbee.convertHexToInt(_data[i + 5])
                power = power * 256
            }
        }
    }
    catch (e) {
        log.error "${device.displayName} Exception caught : data = ${_data}"
    }
    return retValue
}

String getAttributeString(ArrayList _data) {
    String retValue = ''
    try {
        if (_data.size() >= 6) {
            for (int i = 6; i < _data.size(); i++) {
                retValue = retValue + (zigbee.convertHexToInt(_data[i]) as char)
            }
        }
    }
    catch (e) {
        log.error "${device.displayName} Exception caught : data = ${_data}"
    }
    return retValue
}

void close() {
    if (state.states == null) { state.states = [:] }
    state.states['isDigital'] = true
    if (settings?.threeStateEnable == true) {
        sendEvent(name: 'valve', value: 'closing', descriptionText: 'sent a command to close the valve', type: 'digital')
        logInfo 'closing ...'
    }
    scheduleCommandTimeoutCheck()
    List<String> cmds = []
    if (isGIEX()) {
        Short paramVal = 0
        String dpValHex = zigbee.convertToHexString(paramVal as int, 2)
        cmds = sendTuyaCommand('02', DP_TYPE_BOOL, dpValHex)
        if (logEnable) { log.debug "${device.displayName} closing WaterIrrigationValve cmds = ${cmds}" }
    }
    else if (getModelGroup().contains('TS0601')) {
        cmds = sendTuyaCommand('01', DP_TYPE_BOOL, '00')
    }
    else if (getModelGroup().contains('TS0049')) {
        cmds = sendTuyaCommand('65', DP_TYPE_BOOL, '00')
    }
    else {
        cmds = zigbee.off()    // for all models that support the standard Zigbee OnOff cluster
    }
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true])
    logDebug "close()... sent cmds=${cmds}"
    sendZigbeeCommands(cmds)
}

void open() {
    if (state.states == null) { state.states = [:] }
    state.states['isDigital'] = true
    if (settings?.threeStateEnable == true) {
        sendEvent(name: 'valve', value: 'opening', descriptionText: 'sent a command to open the valve', type: 'digital')
        logInfo 'opening ...'
    }
    if (isSonoff()) {
        String lastValveStatus = device.currentValue('valveStatus')
        if (lastValveStatus != null && !(lastValveStatus in ['normal', 'clear'])) {
            sendEvent(name: 'valveStatus', value: 'clear', type: 'digital')
        }
        sendEvent(name: 'irrigationStartTime', value: DATE_TIME_UNKNOWN, type: 'digital')
        sendEvent(name: 'irrigationEndTime', value: DATE_TIME_UNKNOWN, type: 'digital')
        sendEvent(name: 'lastValveOpenDuration', value: NUMBER_UNKNOWN, type: 'digital')
    }
    scheduleCommandTimeoutCheck()
    ArrayList<String> cmds = []
    if (isGIEX()) {
        Short paramVal = 1
        String dpValHex = zigbee.convertToHexString(paramVal as int, 2)
        cmds = sendTuyaCommand('02', DP_TYPE_BOOL, dpValHex)
        if (logEnable) { log.debug "${device.displayName} opening WaterIrrigationValve cmds = ${cmds}" }
    }
    else if (getModelGroup().contains('TS0601')) {
        cmds = sendTuyaCommand('01', DP_TYPE_BOOL, '01')
    }
    else if (getModelGroup().contains('TS0049')) {
        cmds = sendTuyaCommand('65', DP_TYPE_BOOL, '01')
    }
    else if (getModelGroup().contains('SONOFF')) {
        cmds = zigbee.on(200)
        int delay = settings?.autoOffTimer == null ? MAX_AUTOOFF_TIMER : settings?.autoOffTimer as int
        if (delay != 0) {
            String delayHex = zigbee.convertToHexString(delay as int, 4)
            String payload = zigbee.swapOctets(delayHex)
            logDebug "SONOFF delay: ${delay} delayHex: ${delayHex} payload: ${payload}"
            cmds += zigbee.command(0x0006, 0x42, [:],  delay=300, "00 ${payload} 0000")
        }
        else {
            logDebug "SONOFF - no autoOffTimer!"
        }
        logDebug "SONOFF cmds: ${cmds}"
    }
    else {
        cmds =  zigbee.on()
    }
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true])
    if (isSASWELL() || isGIEX()) {
        logDebug "scheduled to set the autoOff Iirrigation duration) timer to ${settings?.autoOffTimer} after 5 seconds"
        runIn(5, 'sendIrrigationDuration')
    }
    logDebug "open()... sent cmds=${cmds}"
    sendZigbeeCommands(cmds)
}

void sendBatteryEvent(int roundedPct, boolean isDigital=false) {
    sendEvent(name: 'battery', value: roundedPct, unit: '%', type:  isDigital == true ? 'digital' : 'physical', isStateChange: true)
    logInfo "battery is: ${roundedPct}%"
    if (isDigital == false) {
        if (state.states == null) { state.states = [:] }
        state.states['lastBattery'] = roundedPct.toString()
    }
}

void clearIsDigital()        { if (state.states == null) { state.states = [:] } ; state.states['isDigital'] = false }
void switchDebouncingClear() { if (state.states == null) { state.states = [:] } ; state.states['debounce']  = false }
void isRefreshRequestClear() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false }

void ping() {
    logInfo 'ping...'
    scheduleCommandTimeoutCheck()
    if (state.lastTx == null) { state.lastTx = [:] }
    state.lastTx['pingTime'] = new Date().getTime()
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0))
}

void sendRttEvent(String value=null) {
    Long now = new Date().getTime()
    if (state.lastTx == null) { state.lastTx = [:] }
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger()
    String descriptionText = "Round-trip time is ${timeRunning} ms"
    if (value == null) {
        logInfo "${descriptionText}"
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'digital')
    }
    else {
        descriptionText = "Round-trip time : ${value}"
        logInfo "${descriptionText}"
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'digital')
    }
}

void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) {
    runIn(delay, 'deviceCommandTimeout')
}

void deviceCommandTimeout() {
    logWarn 'no response received (sleepy device or offline?)'
    sendRttEvent('timeout')
    if ((device.currentValue('valve') ?: 'unknown') in ['opening', 'closing']) {
        sendEvent(name: 'valve', value: 'unknown', type: 'digital')
    }
}

// refresh()
void refresh() {
    logDebug 'refresh()...'
    checkDriverVersion()
    scheduleCommandTimeoutCheck()
    List<String> cmds = []
    if (state.states == null) { state.states = [:] }
    state.states['isRefresh'] = true
    if (device.getDataValue('model') == 'TS0601') {
        cmds += zigbee.command(0xEF00, 0x03)    // queryAllTuyaDP - added 11/21/2024
    }
    else  {
        cmds = zigbee.onOffRefresh()
    }
    if (deviceProfilesV2[getModelGroup()]?.capabilities?.battery?.value == true) {
        cmds += zigbee.readAttribute(0x001, [0x0021, 0x0020], [:], delay = 100)
        //cmds += zigbee.readAttribute(0x001, 0x0021, [:], delay = 200)
    }
    //
    if (isSASWELL() || isGIEX()) {
        cmds += zigbee.command(0xEF00, 0x0, '00020100')
    }
    else if (isTS0001() || isTS0011()) {
        cmds += zigbee.readAttribute(0xE000, 0xD001, [:], delay = 200)    // encoding:42, value:AAAA; attrId: D001, encoding: 48, value: 020006
        cmds += zigbee.readAttribute(0xE000, 0xD002, [:], delay = 200)    // encoding: 48, value: 02000A
        cmds += zigbee.readAttribute(0xE000, 0xD003, [:], delay = 200)
        cmds += zigbee.readAttribute(0xE001, 0xD010, [:], delay = 200)    // powerOnBehavior: {ID: 0xD010, type: DataType.enum8},
        cmds += zigbee.readAttribute(0xE001, 0xD030, [:], delay = 200)    // switchType: {ID: 0xD030, type: DataType.enum8},
        cmds += zigbee.readAttribute(0x0006, 0x4001, [:], delay = 200)    // OnTime
        cmds += zigbee.readAttribute(0x0006, 0x4002, [:], delay = 200)    // OffWaitTime
        cmds += zigbee.readAttribute(0x0006, 0x8001, [:], delay = 200)    // IndicatorMode: 1
        cmds += zigbee.readAttribute(0x0006, 0x8002, [:], delay = 200)    // RestartStatus: 2
    }
    else if (isSonoff()) {
        //  cluster=0x0001 (Power Configuration Cluster), attribute=0x0020 (Battery Voltage), value=36
        //  cluster=0x0001 (Power Configuration Cluster), attribute=0x0021 (Battery Percentage Remaining), value=90
        //  cluster=0x0404 (Flow Measurement Cluster), attribute=0x0000 (Measured Value), value=0000
        cmds += zigbee.readAttribute(0x0404, 0x0000, [:], delay = 199)
        // Read Attribute Response, status=SUCCESS, endpoint=0x01, cluster=0x0B05 (Diagnostics Cluster), attribute=0x011B (Average MAC Retry Per APS Message Sent), value=0003
        // Read Attribute Response, status=SUCCESS, endpoint=0x01, cluster=0x0B05 (Diagnostics Cluster), attribute=0x011C (Last Message LQI), value=FC 
        // Read Attribute Response, status=SUCCESS, endpoint=0x01, cluster=0x0B05 (Diagnostics Cluster), attribute=0x011D (Last Message RSSI), value=DB
        cmds += zigbee.readAttribute(0x0B05, [0x011B, 0x011C, 0x011D], [:], delay = 200)
        // cluster=0xFC11 (Unknown), attribute=0x5006 (Unknown), value=00000009
        //  cluster=0xFC11 (Unknown), attribute=0x5007 (Unknown), value=00000000
        //  cluster=0xFC11 (Unknown), attribute=0x5008 (Unknown), value=
        // cluster=0xFC11 (Unknown), attribute=0x5009 (Unknown), value=
        // cluster=0xFC11 (Unknown), attribute=0x500C (Unknown), value=01 
        // cluster=0xFC11 (Unknown), attribute=0x500D (Unknown), value=2E37E249 
        // cluster=0xFC11 (Unknown), attribute=0x500E (Unknown), value=2E37E24E
        // cluster=0xFC11 (Unknown), attribute=0x500F (Unknown), value=00000000
        //  cluster=0xFC11 (Unknown), attribute=0x5010 (Unknown), value=00      Valve Work State 0: Manual control; 1: Cycle timing / quantity control; 2: Schedule control
        cmds += zigbee.readAttribute(0xFC11, [0x5006, 0x5007,0x500C, 0x500D, 0x500E, 0x500F, 0x5010], [:], delay = 201)
        cmds += zigbee.readAttribute(0xFC11, 0x5008, [:], delay = 202)
        cmds += zigbee.readAttribute(0xFC11, 0x5009, [:], delay = 203)

        // reportging
        // Read Reporting Configuration Response, status=SUCCESS, endpoint=0x01, cluster=0x0001, attribute=0x0021, minPeriod=1, maxPeriod=7200      , data:[00, 00, 21, 00, 20, 01, 00, 20, 1C, 02],
        // Check attribute reporting (endpoint=0x01, cluster=0x0001, attribute=0x0020, manufacturer=)  => Read Reporting Configuration Response, status=NOT_FOUND, data=[8B, 00, 20, 00]
        // Read Reporting Configuration Response, status=SUCCESS, endpoint=0x01, cluster=0x0404, attribute=0x0000, minPeriod=1, maxPeriod=7200  data:[00, 00, 00, 00, 21, 01, 00, 20, 1C, 01, 00]
        // 5006, 
    }
    runInMillis(REFRESH_TIMER, isRefreshRequestClear, [overwrite: true])           // 3 seconds
    if (cmds != null && cmds != []) {
        sendZigbeeCommands(cmds)
    }
}

List<String> tuyaBlackMagic() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0000, [0x0004, 0x0000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay = 150) // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, attributeReportingStatus
    cmds += zigbee.writeAttribute(0x0000, 0xffde, 0x20, 0x0d, [:], delay = 50)
    return cmds
}

/*
    configure() method is called:
       *  unconditionally during the initial pairing, immediately after Installed() method
       *  when Initialize button is pressed
       *  from updated() when preferencies are saved
*/
void configure() {
    if (txtEnable == true) { log.info "${device.displayName} configure().." }
    List<String> cmds = []
    cmds += tuyaBlackMagic()
    //
    if (settings?.autoOffTimer != null /*&& settings?.autoSendTimer != false*/) {
        String timerText = settings?.autoOffTimer == 0 ? 'disabled' : (settings?.autoOffTimer).toString()
        sendEvent(name: 'irrigationDuration', value: timerText, type: 'digital')
    }

    if (settings?.forcedProfile != null) {
        if (settings?.forcedProfile != state.deviceProfile) {
            logWarn "changing the device profile from ${state.deviceProfile} to ${settings?.forcedProfile}"
            state.deviceProfile = settings?.forcedProfile
            logInfo 'press F5 to refresh the page'
        }
    }
    /* Throws an exception !!
    if (getPowerSource() != (device.currentValue('powerSource') ?: 'unknown')) {
        sendEvent(name: 'powerSource', value: getPowerSource(), type: 'digital')
    }
    */

    if (settings?.powerOnBehaviour != null) {
        Map.Entry modeName =  powerOnBehaviourOptions.find { it.key == settings?.powerOnBehaviour }
        if (modeName != null) {
            // TODO - skip it for the battery powered irrigation timers? (Response cluster: E001 status:86)
            logDebug "setting powerOnBehaviour to ${modeName.value} (${settings?.powerOnBehaviour})"
            cmds += zigbee.writeAttribute(0xE001, 0xD010, DataType.ENUM8, (byte) safeToInt(settings?.powerOnBehaviour), [:], delay = 251)
        }
    }

    if (deviceProfilesV2[getModelGroup()]?.configuration?.battery?.value == true) {
        // TODO - configure battery reporting
        logDebug "settings.batteryReporting = ${settings?.batteryReporting}"
    }
    //
    runIn(3, 'refresh')    // ver. 1.2.2
    //
    sendZigbeeCommands(cmds)
}

// called from  initializeVars( fullInit = true)
def setDeviceNameAndProfile(String model=null, String manufacturer=null) {
    String deviceName
    def currentModelMap = null
    String deviceModel        = model != null ? model : device.getDataValue('model')
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer')
    deviceProfilesV2.each { profileName, profileMap ->
        if (profileMap.model == deviceModel) {
            if (profileMap.manufacturers.contains(deviceManufacturer)) {
                currentModelMap = profileName
                state.deviceProfile = currentModelMap
                deviceName = deviceProfilesV2[currentModelMap].deviceJoinName
                logDebug "FOUND exact match!  deviceName =${deviceName} profileName=${currentModelMap} for model ${deviceModel} manufacturer ${deviceManufacturer}"
            }
        }
    }

    if (currentModelMap == null) {
        logWarn "unknown model ${deviceModel} manufacturer ${deviceManufacturer}"
        // don't change the device name when unknown
        state.deviceProfile = UNKNOWN
    }
    if (deviceName != NULL) {
        device.setName(deviceName)
        logInfo "device model ${deviceModel} manufacturer ${deviceManufacturer} deviceName was set to ${deviceName}"
    } else {
        logWarn "device model ${deviceModel} manufacturer ${deviceManufacturer} was not found!"
    }
    // TODO !! patch !
    if (currentModelMap != null) {
        state.deviceProfile = currentModelMap
        logInfo "deviceProfile was set to ${currentModelMap}"
        device.updateSetting('forcedProfile', [value:currentModelMap, type:'enum'])
    }
    else if (_DEBUG == true) {
        logWarn "##### Setting ${SIMULATED_PROFILE}"
        state.deviceProfile = SIMULATED_PROFILE
    }
    //
    return [deviceName, currentModelMap]
}

// This method is called when the preferences of a device are updated.
void updated() {
    checkDriverVersion()
    logInfo "Updating ${(device.getLabel() ?: '[no lablel]')} (${device.getName()}) device model ${deviceModel} manufacturer ${deviceManufacturer} deviceProfile ${getModelGroup()} (driver version ${driverVersionAndTimeStamp()}) "
    logInfo "Debug logging is <b>${logEnable}</b> Description text logging is  <b>${txtEnable}</b>"
    if (logEnable == true) {
        runIn(86400, logsOff, [overwrite: true])    // turn off debug logging after 24 hours
        logInfo 'Debug logging will be automatically switched off after 24 hours'
    }
    else {
        unschedule(logsOff)
    }
    scheduleDeviceHealthCheck()
    configure()
}

void resetStats() {
    state.stats = [:]
    state.states = [:]
    state.lastRx = [:]
    state.lastTx = [:]
    state.stats['RxCtr'] = 0
    state.stats['TxCtr'] = 0
    state.states['isDigital'] = false
    state.states['isRefresh'] = false
    state.states['debounce'] = false
    state.states['lastSwitch'] = 'unknown'
    if (isBatteryPowered()) { state.states['lastBattery'] = '100' }
    state.states['notPresentCtr'] = 0
    state.lastTx['pingTime'] = new Date().getTime()
}

void deleteAll() {
    String deleted = ''
    settings.each { it -> deleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") }
    logDebug "Deleted settings: ${deleted}" ; deleted = ''
    device.properties.supportedAttributes.each { it -> deleted += "${it}, " ; device.deleteCurrentState("$it") }
    logDebug "Deleted attributes: ${deleted}" ; deleted = ''
    state.each { it -> deleted += "${it.key}, " }
    state.clear()
    logDebug "Deleted states: ${stateDeleted}" ; deleted = ''
    unschedule() ; logDebug 'All scheduled jobs DELETED'
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) }
    logDebug 'All child devices DELETED'
}

void initializeVars(boolean fullInit = true) {
    logInfo "InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true) {
        state.clear()
        unschedule()
        deleteAll() // added 12/10/2024
        resetStats()
        logInfo 'all states and scheduled jobs cleared!'
        setDeviceNameAndProfile()
        state.comment = 'Works with Tuya TS0001 TS0011 TS011F TS0601 shutoff valves; Tuya TS0049, GiEX, Saswell, Lidl, FrankEver irrigation timers'
        state.driverVersion = driverVersionAndTimeStamp()
    }

    if (state.stats == null)  { state.stats  = [:] }
    if (state.states == null) { state.states = [:] }
    if (state.lastRx == null) { state.lastRx = [:] }
    if (state.lastTx == null) { state.lastTx = [:] }

    if (fullInit == true || state.states['lastSwitch'] == null) { state.states['lastSwitch'] = 'unknown' }
    if (fullInit == true || state.states['notPresentCtr'] == null)  { state.states['notPresentCtr']  = 0 }
    if (fullInit == true || settings?.logEnable == null)  { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING) }
    if (fullInit == true || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) }
    if (fullInit == true || settings?.powerOnBehaviour == null) { device.updateSetting('powerOnBehaviour', [value:'2', type:'enum']) }   // last state
    if (fullInit == true || settings?.switchType == null) { device.updateSetting('switchType', [value:'0', type:'enum']) }               // toggle
    if (fullInit == true || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) }               // toggle
    if (isSASWELL() || isGIEX() || isSonoff() || isFankEver()) {
        if (fullInit == true || settings?.autoOffTimer == null) { device.updateSetting('autoOffTimer', [value: DEFAULT_AUTOOFF_TIMER, type: 'number']) }
    }
    if (fullInit == true || settings?.autoSendTimer == null) { device.updateSetting('autoSendTimer', (isGIEX() ? true : false)) }
    if (fullInit == true || settings?.threeStateEnable == null) { device.updateSetting('threeStateEnable', false) }

    if (isBatteryPowered()) {
        if (state.states['lastBattery'] == null) { state.states['lastBattery'] = '100' }
    }
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') }

    updateTuyaVersion()

    String mm = device.getDataValue('model')
    if (mm != null) {
        if (logEnable == true) { log.trace " model = ${mm}" }
    }
    else {
        if (txtEnable == true) { log.warn ' Model not found, please re-pair the device!' }
    }
    String ep = device.getEndpointId()
    if (ep  != null) {
        //state.destinationEP = ep
        if (logEnable == true) { log.trace " destinationEP = ${ep}" }
    }
    else {
        if (txtEnable == true) { log.warn ' Destination End Point not found, please re-pair the device!' }
    //state.destinationEP = "01"    // fallback
    }
}

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() }

void checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logInfo "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        initializeVars(fullInit = false)
        scheduleDeviceHealthCheck()
        if (state.deviceProfile == UNKNOWN) {
            setDeviceNameAndProfile()
        }
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

void logInitializeRezults() {
    if (logEnable == true) { log.info "${device.displayName} Initialization finished" }
}

// NOT called when the driver is initialized as a new device, because the Initialize capability is NOT declared!
void initialize() {
    log.info "${device.displayName} Initialize()..."
    unschedule()
    initializeVars(true)
    updated()            // calls also configure()
    scheduleDeviceHealthCheck()
    runIn(3, logInitializeRezults, [overwrite: true])
}

// This method is called when the device is first created.
void installed() {
    log.info "${device.displayName} installed() model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} driver version ${driverVersionAndTimeStamp()}"
    initializeVars()
    runIn(5, initialize, [overwrite: true])
    if (logEnable == true) { log.debug 'calling initialize() after 5 seconds...' }
// HE will autoomaticall call configure() method here
}

void uninstalled() {
    if (logEnable == true) { log.info "${device.displayName} Uninstalled()..." }
    unschedule()     //Unschedule any existing schedules
}

void scheduleDeviceHealthCheck() {
    logDebug 'scheduleDeviceHealthCheck()...'
    Random rnd = new Random()
    //schedule("1 * * * * ? *", 'deviceHealthCheck') // for quick test
    schedule("${rnd.nextInt(59)} ${rnd.nextInt(59)} 1/3 * * ? *", 'deviceHealthCheck')
}

// called when any event was received from the Zigbee device in parse() method..
void setHealthStatusOnline() {
    if (state.states == null) { state.states = [:] }
    state.states['notPresentCtr']  = 0
    if (!((device.currentValue('healthStatus', true) ?: 'unknown') in ['online'])) {
        sendHealthStatusEvent('online')
        sendEvent(name: 'powerSource', value: getPowerSource(), type: 'digital')
        logInfo 'is online'
    }
}

void deviceHealthCheck() {
    if (state.states == null) { state.states = [:] }
    int ctr = state.states['notPresentCtr'] ?: 0
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) {
        if ((device.currentValue('healthStatus', true) ?: 'unknown') != 'offline') {
            logWarn 'not present!'
            sendHealthStatusEvent('offline')
            sendEvent(name: 'powerSource', value: 'unknown', type: 'digital')
            if (isBatteryPowered()) {
                if (safeToInt(device.currentValue('battery', true)) != 0) {
                    logWarn "${device.displayName} forced battery to '<b>0 %</b>"
                    sendBatteryEvent(0, isDigital = true)
                }
            }
        }
    }
    else {
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})"
    }
    state.states['notPresentCtr'] = ctr + 1
}

void sendHealthStatusEvent(String value) {
    sendEvent(name: 'healthStatus', value: value, descriptionText: "${device.displayName} healthStatus set to $value", type: 'digital')
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (settings?.logEnable) { log.debug "${device.displayName } <b>sendZigbeeCommands</b> (cmd=$cmd)" }
    if (state.stats == null) { state.stats = [:] }
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
            state.stats['TxCtr'] = state.stats['TxCtr'] != null ? state.stats['TxCtr'] + 1 : 1
    }
    sendHubCommand(allActions)
}

void logsOff() {
    log.warn 'debug logging disabled...'
    device.updateSetting('logEnable', [value:'false', type:'bool'])
}

boolean isTuyaE00xCluster( String description ) {
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) {
        return false
    }
    // try to parse ...
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..."
    Map descMap = [:]
    try {
        descMap = zigbee.parseDescriptionAsMap(description)
        logDebug "TuyaE00xCluster Desc Map: ${descMap}"
    }
    catch ( e ) {
        logDebug "<b>exception</b> caught while parsing description:  ${description}"
        logDebug "TuyaE00xCluster Desc Map: ${descMap}"
        // cluster E001 is the one that is generating exceptions...
        return true
    }

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) {
        logInfo "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}"
    }
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') {
        logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})"
    }
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') {
        logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})"
    }
    else {
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap"
        return false
    }
    return true    // processed
}

/* groovylint-disable-next-line UnusedMethodParameter */
boolean otherTuyaOddities( String description ) {
    return false    // !!!!!!!!!!!
/* groovylint-disable-next-line DeadCode */
/*
    if (description.indexOf('cluster: 0000') >= 0 || description.indexOf('attrId: 0004') >= 0) {
        if (logEnable) log.debug " other Tuya oddities - don't know how to handle it, skipping it for now..."
        return true
    }
    else
        return false
*/
}

Integer safeToInt(val, Integer defaultVal=0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

Double safeToDouble(val, Double defaultVal=0.0) {
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

void logDebug(final String msg) {
    if (settings?.logEnable) {
        log.debug "${device.displayName} " + msg
    }
}

void logInfo(final String msg) {
    if (settings?.txtEnable) {
        log.info "${device.displayName} " + msg
    }
}

void logWarn(final String msg) {
    if (settings?.logEnable) {
        log.warn "${device.displayName} " + msg
    }
}

/*
    https://github.com/zigpy/zha-device-handlers/issues/1571#issuecomment-1132516457

    attributes = TuyaMCUCluster.attributes.copy()
    attributes.update(
        {
            0xEF01: ("time_left", t.uint32_t, True),
            0xEF02: ("state", t.enum8, True),
            0xEF03: ("last_valve_open_duration", t.uint32_t, True),
            0xEF04: ("dp_6", t.uint32_t, True),
        }
    )

*/

/*
https://github.com/zigpy/zha-device-handlers/issues/1556#issuecomment-1127443288

            0xef01: ("timer", t.uint32_t, True),
            0xef02: ("timer_time_left", t.uint32_t, True),
            0xef03: ("frost_lock", t.Bool, True),
            0xef04: ("frost_lock_reset", t.Bool, True),  # 0 resets frost lock
?????????????????????????????????????????

*/

/*
https://github.com/simonbaudart/zha-device-handlers/blob/6cb86ce2980abbe8eb0a7670e440282a2ab5b022/zhaquirks/tuya/ts0601_garden.py

    cluster_id = 0x043E
    name = "Timer"
    ep_attribute = "timer"

    attributes = {
        0x000C: ("state", t.uint16_t),
        0x000B: ("time_left", t.uint16_t),
        0x000F: ("last_valve_open_duration", t.uint16_t),
    }

https://github.com/simonbaudart/zha-device-handlers/blob/cae7400682fc2a1ffcb697e96684f607566cf123/zhaquirks/tuya/ts0601_valve.py

*/

void setIrrigationTimer(BigDecimal timer) {
    if (!(isGIEX() || isSASWELL() || isTS0049() || isFankEver() || isSonoff() || isTZE284())) {
        logWarn 'setIrrigationTimer is avaiable for GiEX, SASWELL, TS0049, FankEver and Sonoff valves only!'
        return
    }
    int timerSec = safeToInt(timer, -1)
    if (timerSec < 0 || timerSec > MAX_AUTOOFF_TIMER) {
        logWarn "timer must be withing 0 and ${MAX_AUTOOFF_TIMER} seconds"
        return
    }
    logInfo "setting the irrigation timer to ${timerSec} seconds"
    device.updateSetting('autoOffTimer', [value: timerSec, type: 'number'])
    String timerText = timerSec == 0 ? 'disabled' : timerSec.toString()
    sendEvent(name: 'irrigationDuration', value: timerText, type: 'digital')
    runIn( 1, 'sendIrrigationDuration')
}

void sendIrrigationDuration() {
    List<String> cmds = []
    String dpValHex = zigbee.convertToHexString((settings?.autoOffTimer ?: DEFAULT_AUTOOFF_TIMER) as Integer, 8)
    if (isSASWELL()) {
        String autoOffTime = '00010B020004' + dpValHex
        cmds = zigbee.command(0xEF00, 0x0, autoOffTime)
    } else if (isGIEX()) {
        cmds = sendTuyaCommand('68', DP_TYPE_VALUE, dpValHex)
    }
    else if (isTS0049()) {
        cmds = sendTuyaCommand('6F', DP_TYPE_VALUE, dpValHex)
    }
    else if (isFankEver()) {
        cmds = sendTuyaCommand('09', DP_TYPE_VALUE, dpValHex)
    }
    else if (isTZE284()) {
        cmds = sendTuyaCommand('19', DP_TYPE_VALUE, dpValHex) + sendTuyaCommand('1A', DP_TYPE_VALUE, dpValHex)
    }
    else if (isSonoff()) {
        logDebug "Sonoff irrigation timer is ${settings?.autoOffTimer ?: DEFAULT_AUTOOFF_TIMER}"
        // nothing to send for Sonoff
        return
    }
    else {
        logWarn 'sendIrrigationDuration is not avaiable!'
        return
    }
    logDebug "sendIrrigationDuration = ${settings?.autoOffTimer ?: DEFAULT_AUTOOFF_TIMER} : ${cmds}"
    sendZigbeeCommands(cmds)
}

void setIrrigationCapacity(BigDecimal litres) {
    if (!(isGIEX() || isSASWELL())) {
        logWarn 'setIrrigationCapacity is avaiable for GiEX/SASWELL valves only!'
        return
    }
    int value = safeToInt(litres, -1)
    if (value < 0 || value > MAX_CAPACITY) {
        logWarn "irrigation capacity must be withing 0 and ${MAX_CAPACITY} litres"
        return
    }
    logDebug "setting the irrigation capacity to ${value} litres"
    device.updateSetting('irrigationCapacity', [value: value, type: 'number'])
    sendEvent(name: 'irrigationCapacity', value: value, type: 'digital')
    runIn( 1, 'sendIrrigationCapacity')
}

void sendIrrigationCapacity() {
    List<String> cmds = []
    if (isGIEX() || isSASWELL()) {
        String dpValHex = zigbee.convertToHexString(settings?.irrigationCapacity as int, 8)
        cmds = sendTuyaCommand('68', DP_TYPE_VALUE, dpValHex)
        logDebug "sendIrrigationCapacity= ${settings?.irrigationCapacity} : ${cmds}"
        sendZigbeeCommands( cmds )
    } else {
        logWarn 'sendIrrigationCapacity is avaiable for GiEX valves only!'
    }
}

void setIrrigationMode(String mode) {
    if (!(isGIEX() || isSASWELL())) {
        logWarn 'setIrrigationMode is avaiable for GiEX and SASWELL valves only!'
        return
    }
    List<String> cmds = []
    String dpValHex
    switch (mode)  {
        case 'duration':
            dpValHex = '00'
            break
        case 'capacity':
            dpValHex = '01'
            break
        default :
            logWarn "incorrect irrigationMode ${ mode }, must be ${ (waterModeOptions.each { it })}"
            return
    }
    cmds = sendTuyaCommand('01', DP_TYPE_ENUM, dpValHex)
    logDebug "setIrrigationMode= ${mode} : ${cmds}"
    sendZigbeeCommands( cmds )
}


void setValve2(String mode) {
    if (!isTZE284()) {
        logWarn 'setValve2 command is available for GiEX double valves  only!'
        return
    }
    List<String> cmds = []
    String dpValHex
    switch (mode)  {
        case 'open':
            dpValHex = '01'
            break
        case 'closed':
            dpValHex = '00'
            break
        default :
            logWarn "incorrect setValve2 ${ mode }, must be 'open' or 'closed'"
            return
    }
    cmds = sendTuyaCommand('02', DP_TYPE_ENUM, dpValHex)
    logDebug "setValve2= ${mode} : ${cmds}"
    sendZigbeeCommands( cmds )
}

void setValveOpenThreshold(Number percentage) {
    if (!isFankEver()) {
        logWarn 'setValveOpenThreshold is avaiable only for FankEver valves'
        return
    }
    List<String> cmds = []
    int value = safeToInt(percentage, -1)
    if (value < 0 || value > 100) {
        logWarn "valve open threshold must be withing 0 and 100"
        return
    }
    // round to the nearest 10
    int roundedValue = Math.floor((value + 5) / 10) * 10 
    logDebug "setting the valve open threshold to ${roundedValue} % (was ${value})"
    device.updateSetting('valveOpenThreshold', [value: roundedValue, type: 'number'])
    sendEvent(name: 'valveOpenThreshold', value: roundedValue, type: 'digital')
    sendTuyaCommand('65', DP_TYPE_VALUE, zigbee.convertToHexString(roundedValue, 8))
}

void testTuyaCmd(String dpCommand, String dpValue, String dpTypeString) {
    //ArrayList<String> cmds = []
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue
    log.warn " sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}"
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}

void updateTuyaVersion() {
    if (!isTuya()) {
        logDebug 'updateTuyaVersion() - not a Tuya device'
        if (device?.getDataValue('tuyaVersion') != null)  {
            device.removeDataValue('tuyaVersion')
            logInfo 'tuyaVersion cleared'
        }
        return
    }
    def application = device.getDataValue('application')
    Integer ver
    if (application != null) {
        try {
            ver = zigbee.convertHexToInt(application)
        }
        catch (e) {
            logWarn "exception caught while converting application version ${application} to tuyaVersion"
            return
        }
        String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString()
        if (device.getDataValue('tuyaVersion') != str) {
            device.updateDataValue('tuyaVersion', str)
            logInfo "tuyaVersion set to $str"
        }
    }
}

boolean isTuya() {
    if (!device) { return true }    // fallback - added 04/03/2024
    String model = device.getDataValue('model')
    String manufacturer = device.getDataValue('manufacturer')
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false
}

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */
void test(String description) {
    //    catchall: 0104 EF00 01 01 0040 00 533D 01 00 0000 01 01 00550101000100
    log.warn "test parsing: <b>${description}</b>"
    parse(description)
//log.trace "getPowerSource()=${getPowerSource()}"
// 
}

void testX() {
    /*
    logWarn 'sending Active Endpoints and Simple Descriptor Requests'
    List<String> cmds = []
    String endpointIdTemp
    cmds += ["he raw ${device.deviceNetworkId} 0 0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"] // ZDO(x0000) Active Endpoints Request (cluster 0x0005)
    endpointIdTemp = '01'
    cmds += ["he raw ${device.deviceNetworkId} 0 0 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} $endpointIdTemp} {0x0000}"]
    endpointIdTemp = 'F2'
    cmds += ["he raw ${device.deviceNetworkId} 0 0 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} $endpointIdTemp} {0x0000}"]
    sendZigbeeCommands(cmds)
    */
    setDeviceNameAndProfile()
}

// private methods

static int getCLUSTER_TUYA()       { 0xEF00 }
static int getTUYA_ELECTRICIAN_PRIVATE_CLUSTER() { 0xE001 }
static int getSETDATA()            { 0x00 }
static int getSETTIME()            { 0x24 }

// tuya DP type
static String getDP_TYPE_RAW()        { '01' }    // [ bytes ]
static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ]
static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ]
static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ]
static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ]
static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits

String getPACKET_ID() {
    return zigbee.convertToHexString(new Random().nextInt(65536), 4)
}

List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) {
    List<String> cmds = []
    int tuyaCmd = isTS0049() ? 0x04 : SETDATA
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [:], delay = 200, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd)
    if (settings?.logEnable) { log.trace "${device.displayName} sendTuyaCommand = ${cmds}" }
    if (state.stats == null) { state.stats = [:] }
    state.stats['TxCtr'] = state.stats['TxCtr'] != null ? state.stats['TxCtr'] + 1 : 1
    return cmds
}
