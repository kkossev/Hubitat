/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateStringLiteral, ImplicitClosureParameter, LineLength, MethodCount, MethodSize, NglParseError, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessarySetter, UnusedImport */
/**
 *  Zigbee Shade Controller - Device Driver for Hubitat Elevation
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
 * ver. 3.3.0  2024-06-25 kkossev  - new driver for Zigbee Shade Controller : TUYA_TS130F_MODULE DEFAULT_ZCL_WINDOW_COVERING
 * ver. 3.3.1  2024-06-30 kkossev  - added TUYA_SLIDING_WINDOW_PUSHER for tests; added ZEMISMART_ZM85EL_1X ZEMISMART_M515EGB
 * ver. 3.3.2  2024-07-01 kkossev  - added preferences 'slowStop', 'manualMode'; added attributes 'control', 'chargeState', 'motorTimeout', 'windowDetection'
 * ver. 3.3.3  2024-07-01 kkossev  - added tuyaCmd=04 in the deviceProfile map and commonLib
 * ver. 3.3.4  2024-07-02 kkossev  - (dev. branch) OPEN = 100%  CLOSED = 0%
 *
 *                                   TODO: make the invert option different preferences  - softwareInvertDirection, hardwareInvertDirection;
 *                                   TODO: hide Battery Voltage to Percentage preference
 */

static String version() { '3.3.4' }
static String timeStamp() { '2024/07/02 8:03 AM' }

@Field static final Boolean _DEBUG = false
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true

import groovy.transform.Field
import hubitat.zigbee.zcl.DataType





deviceType = 'Curtain'
@Field static final String DEVICE_TYPE = 'Curtain'

metadata {
    definition(
        name: 'Zigbee Window Shade',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/ZigBee%20Window%20Shade/Zigbee_Window_Shade_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        capability 'WindowShade'

        attribute 'targetPosition', 'number'            // ZemiSmart M1 is updating this attribute, not the position :(
        attribute 'operationalStatus', 'number'         // 'enum', ['unknown', 'open', 'closed', 'opening', 'closing', 'partially open']

        attribute 'positionState', 'enum', ['up/open', 'stop', 'down/close']    // TUYA_TS130F_MODULE
        attribute 'upDownConfirm', 'enum', ['false', 'true']                // TUYA_TS130F_MODULE
        attribute 'controlBack', 'enum', ['false', 'true']                  // TUYA_TS130F_MODULE
        attribute 'scheduleTime', 'number'                                  // TUYA_TS130F_MODULE
        attribute 'clickControl', 'enum', ['up', 'down']                    // Zemismart ZM85EL_1x
        attribute 'bestPosition', 'number'                                  // Zemismart ZM85EL_1x
        attribute 'workState', 'enum', ['moving', 'idle']                   // Zemismart ZM85EL_1x
        attribute 'mode', 'enum', ['morning', 'night']                      // Zemismart ZM85EL_1x
        attribute 'motorDirection', 'enum', ['forward', 'backward', 'left', 'right']         // Zemismart ZM85EL_1x, TUYA_SLIDING_WINDOW_PUSHER
        attribute 'situationSet', 'enum', ['fully_open', 'fully_close']     // Zemismart ZM85EL_1x
        attribute 'fault', 'enum', ['clear', 'motor_fault']                 // Zemismart ZM85EL_1x, TUYA_SLIDING_WINDOW_PUSHER
        attribute 'slowStop', 'enum', ['enabled', 'disabled']               // TUYA_SLIDING_WINDOW_PUSHER
        attribute 'manualMode', 'enum', ['enabled', 'disabled']             // TUYA_SLIDING_WINDOW_PUSHER
        attribute 'chargeState', 'enum', ['charging', 'discharging']        // TUYA_SLIDING_WINDOW_PUSHER
        attribute 'motorTimeout', 'number'                                  // TUYA_SLIDING_WINDOW_PUSHER
        attribute 'windowDetection', 'enum', ['true', 'false']              // TUYA_SLIDING_WINDOW_PUSHER
        attribute 'control', 'enum', ['open', 'close', 'stop']              // TUYA_SLIDING_WINDOW_PUSHER

        command 'refreshAll'
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
       // section {
            input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: 'Enables command logging.'
            input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_DEBUG_LOGGING, description: 'Turns on debug logging for 24 hours.'
            //input name: "helpInfo", type: "hidden", title: fmtHelpInfo("Community Link")
      //  }
        // the rest of the preferences are inputed from the deviceProfile maps in the deviceProfileLib
        //section {
            input name: 'maxTravelTime', type: 'number', title: '<b>Maximum travel time</b>', description: 'The maximum time to fully open or close (Seconds).', required: false, defaultValue: DEFAULT_MAX_TRAVEL_TIME
            input name: 'deltaPosition', type: 'number', title: '<b>Position delta</b>', description: 'The maximum error step reaching the target position.', required: false, defaultValue: DEFAULT_POSITION_DELTA
            if (settings?.advancedOptions == true) {
                input name: 'commandOpenCode', type: 'number', title: '<b>Open Command Code</b>', description: 'The standard Open command code is 0.<br>Don\'t change these codes, except you are sure what you are doing.', required: true, defaultValue: DEFAULT_COMMAND_OPEN
                input name: 'commandCloseCode', type: 'number', title: '<b>Close Command Code</b>', description: 'The standard Close command code is 1.<br>Zemismart OEMs are making fun by changing these numbers.', required: true, defaultValue: DEFAULT_COMMAND_CLOSE
                input name: 'commandStopCode', type: 'number', title: '<b>Stop Command Code</b>', description: 'The standard Stop (Pause) command code is 2.<br>Don\'t do like Zemismart OEM\'s!', required: true, defaultValue: DEFAULT_COMMAND_PAUSE
                input name: 'substituteOpenClose', type: 'bool', title: '<b>Substitute Open/Close w/ setPosition</b>', description: 'Some Zemismart OEMs do not accept the Open/Close commands... :( ', required: true, defaultValue: false
                input name: 'invertPosition', type: 'bool', title: '<b>Reverse Position Reports</b>', description: 'Some Zemismart OEMs don\'t know what is up and what is down...', required: true, defaultValue: false
                input name: 'targetAsCurrentPosition', type: 'bool', title: '<b>Reverse Target and Current Position</b>', description: 'Non-standard Zemismart motors<br>Hopefully we can get rid of this option.', required: false, defaultValue: false
            }
        //}

    }
}

@Field static final String DRIVER = 'Zigbee Window Shade'
@Field static final String WIKI   = 'Get help on GitHub Wiki page:'
@Field static final String COMM_LINK =   "https://community.hubitat.com/t/moes-tuya-zigbee-smart-sliding-window-pusher/139006/7?u=kkossev"
@Field static final String GITHUB_LINK = "https://github.com/kkossev/Hubitat/wiki/Zigbee-Window-Shade"

// credits @jtp10181
String fmtHelpInfo(String str) {
	String info = "${DRIVER} v${version()}"
	String prefLink = "<a href='${GITHUB_LINK}' target='_blank'>${WIKI}<br><div style='font-size: 70%;'>${info}</div></a>"
    String topStyle = "style='font-size: 18px; padding: 1px 12px; border: 2px solid green; border-radius: 6px; color: green;'"
    String topLink = "<a ${topStyle} href='${COMM_LINK}' target='_blank'>${str}<br><div style='font-size: 14px;'>${info}</div></a>"

	return "<div style='font-size: 160%; font-style: bold; padding: 2px 0px; text-align: center;'>${prefLink}</div>" +
		"<div style='text-align: center; position: absolute; top: 46px; right: 60px; padding: 0px;'><ul class='nav'><li>${topLink}</ul></li></div>"
}


@Field static final Map deviceProfilesV3 = [
    //
    'ZEMISMART_ZM85EL_1X'   : [
            description   : 'Zemismart ZM85EL_1x',   //
            device        : [type: 'COVERING', powerSource: 'battery', isSleepy:false, isTuyaEF00:true],
            capabilities  : ['Battery': true],
            preferences   : ['motorDirection':'5', 'bestPosition':'19'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_68nvbio9", controllerType: "ZGB"]
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs:        [
                [dp:1,   name:'control',             type:'enum',    rw: 'rw', min:0,   max:3 ,    defVal:'0',  scale:1,   map:[0:'open', 1:'stop', 2:'close', 3:'continue'] , description:'Shade control'],        // control
                [dp:2,   name:'positionSetting',     type:'number',  rw: 'ro', min:0,   max:100,   defVal:0,    description:'curtain position setting'],            // percent_control
                [dp:3,   name:'position',            type:'number',  rw: 'ro', min:0,   max:100,   defVal:0,    description:'curtain current position'],            // current_position
                [dp:4,   name:'mode',                type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'morning', 1:'night'] ,  title:'<bMode</b>', description:'mode'],
                [dp:5,   name:'motorDirection', dt:'04',     type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'forward', 1:'backward'] , title:'<b>Motor Direction</b>',  description:'Motor direction'],  //control_back_mode
                [dp:7,   name:'workState',           type:'enum',    rw: 'ro', processDuplicated:true, map:[0:'moving', 1:'idle'] , description:'work state'],      // work_state - was 'opening' and 'closing'
                [dp:11,  name:'situationSet',        type:'number',  rw: 'ro', map:[0:'fully_open', 1:'fully_close'], description:'situation set'],                 // situation_set
                [dp:12,  name:'fault',               type:'enum',    rw: 'ro', map:[0:'clear', 1:'motor_fault'] ,     description:'fault code'],                    // fault
                [dp:13,  name:'battery',             type:'number',  rw: 'ro', min:0,   max:100,   defVal:100,  scale:1,   unit:'%',  description:'battery percentage'],
                [dp:16,  name:'limits',              type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'up', 1:'down', 2:'up_delete', 3:'down_delete', 4:'remove_top_bottom'], title:'<Limits</b>', description:'Limits setting'],      // border
                [dp:19,  name:'bestPosition',  dt:'02',      type:'number',  rw: 'rw', min:0,   max:100,   defVal:50,   scale:1,   unit:'%', title:'<b>Best Position</b>', description:'best position'],            // best_position    
                [dp:20,  name:'clickControl',        type:'enum',    rw: 'rw', min:0,   max:2 ,    defVal:'0',  scale:1,   map:[0:'up', 1:'down'] , title:'<b>Click Control</b>' , description:'Shade control'],
            ],
            //refresh: ['refreshTS130F'],
            //refresh: ['position', 'positionState', 'upDownConfirm', 'controlBack', 'scheduleTime', '0xE001:0x0000'],
            deviceJoinName: 'Zemismart ZM85EL_1x',
            configuration : [:]
    ],

    'ZEMISMART_M515EGB'   : [   // not fully working yet!
            description   : 'Zemismart M515EGB',   //
            device        : [type: 'COVERING', powerSource: 'battery', isSleepy:false, isTuyaEF00:true],
            capabilities  : ['Battery': false],
            preferences   : ['motorDirection':'5'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_xuzcvlku", controllerType: "ZGB"], // inverted
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_wmcdj3aq", controllerType: "ZGB"],  // inverted
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TYST11_wmcdj3aq", controllerType: "ZGB"],  // inverted
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TYST11_xu1rkty3", controllerType: "ZGB"],  // inverted
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_xaabybja", controllerType: "ZGB"], // inverted
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_nogaemzt", controllerType: "ZGB"], // inverted

                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_gubdgai2", controllerType: "ZGB"],
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs:        [
                [dp:1,   name:'control',             type:'enum',    rw: 'rw', min:0,   max:2 ,    defVal:'0',  scale:1,   map:[0:'close', 1:'stop', 2:'open'] , description:'Shade control'],        // control
                [dp:2,   name:'position',            type:'number',  rw: 'ro', min:0,   max:100,   defVal:0,    description:'curtain position setting'],            // percent_control
                [dp:3,   name:'currentPosition',     type:'number',  rw: 'ro', min:0,   max:100,   defVal:0,    description:'curtain current position'],            // current_position
                //[dp:4,   name:'mode',                type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'morning', 1:'night'] ,  title:'<bMode</b>',description:'mode'],
                [dp:5,   name:'motorDirection', dt:'04',     type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'forward', 1:'backward'] , title:'<b>Motor Direction</b>',  description:'Motor direction'],  //control_back_mode
                [dp:7,   name:'workState',           type:'enum',    rw: 'ro', processDuplicated:true, map:[0:'moving', 1:'idle'] , description:'work state'],      // work_state - was 'opening' and 'closing'
                //[dp:11,  name:'situationSet',        type:'number',  rw: 'ro', map:[0:'fully_open', 1:'fully_close'], description:'situation set'],                 // situation_set
                [dp:12,  name:'fault',               type:'enum',    rw: 'ro', map:[0:'clear', 1:'motor_fault'] ,     description:'fault code'],                    // fault
                //[dp:13,  name:'battery',             type:'number',  rw: 'ro', min:0,   max:100,   defVal:100,  scale:1,   unit:'%',  description:'battery percentage'],
                //[dp:16,  name:'limits',              type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'up', 1:'down', 2:'up_delete', 3:'down_delete', 4:'remove_top_bottom'], title:'<Limits</b>', description:'Limits setting'],      // border
                //[dp:19,  name:'bestPosition',  dt:'02',      type:'number',  rw: 'rw', min:0,   max:100,   defVal:50,   scale:1,   unit:'%', title:'<b>Best Position</b>', description:'best position'],            // best_position    
                //[dp:20,  name:'clickControl',        type:'enum',    rw: 'rw', min:0,   max:2 ,    defVal:'0',  scale:1,   map:[0:'up', 1:'down'] , title:'<b>Click Control</b>' , description:'Shade control'],
            ],
            //refresh: ['refreshTS130F'],
            //refresh: ['position', 'positionState', 'upDownConfirm', 'controlBack', 'scheduleTime', '0xE001:0x0000'],
            deviceJoinName: 'Zemismart M515EGB',
            configuration : [:]
    ],

    'TUYA_SLIDING_WINDOW_PUSHER'   : [
            description   : 'Tuya Sliding Window Pusher',   //
            device        : [type: 'COVERING', powerSource: 'battery', isSleepy:false, isTuyaEF00:true, tuyaCmd:04],
            capabilities  : ['Battery': true],
            preferences   : ['motorDirection':'109', 'slowStop':'110', 'manualMode':'106'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500,EF00", outClusters:"000A,0019", model:"TS0601", manufacturer:"_TZ3210_5rta89nj", controllerType: "ZGB"]
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh'],
            tuyaDPs:        [
                [dp:4,   name:'battery',             type:'number',  rw: 'ro', min:0,   max:100,   defVal:100,  scale:1,   unit:'%',  description:'Battery percentage'],
                [dp:102, name:'control',             type:'enum',    rw: 'rw', min:0,   max:2 ,    defVal:'0',  scale:1,   map:[0:'open', 1:'close', 2:'stop'] , description:'Window control'],        // control
                [dp:103, name:'alarmMode',           type:'enum',    rw: 'ro', map:[0:'enabled', 1:'disabled'] ,      description:'Alarm Mode'],                          // alarm_mode security_mode
                [dp:104, name:'position',            type:'number',  rw: 'ro', min:0,   max:100,   defVal:0,    description:'Curtain current position'],            // coverPosition  ratio of opening
                [dp:105, name:'chargeState',         type:'enum',    rw: 'ro', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'charging', 1:'discharging'],  description:'Charge state'],
                [dp:106, name:'manualMode',          type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'enabled', 1:'disabled'], title:'<b>Manual Mode</b>', description:'Pushing the window causes the motor to run in the push direction'],
                [dp:107, name:'fault',               type:'enum',    rw: 'ro', map:[0:'motor_fault', 1:'clear'] ,  description:'Fault code'],                       // alarm_mode
                [dp:108, name:'calibration',         type:'number',  rw: 'rw', min:10,   max:90,   defVal:15,    scale:1,   unit:'seconds',  title:'<b>Motor Calibration</b>', description:'Motor calibration'],   // motor_calibration
                [dp:109, name:'motorDirection', dt:'04',  type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'left', 1:'right'] , title:'<b>Motor Direction</b>',  description:'Motor direction install side'],  //control_back_mode installation_type
                [dp:110, name:'slowStop',            type:'enum',    rw: 'rw', map:[0:'enabled', 1:'disabled'],  title:'<b>Slow Stop</b>',    description:'Enable/disable the slow stop function'],                           // slow_stop
                [dp:111, name:'solarEnergyCurrent',  type:'number',  rw: 'ro', min:0,   max:99999, defVal:0,    scale:1,   description:'Solar Energy Current'],   // solar_energy_current
                [dp:112, name:'fixedWindowSash',     type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'true', 1:'false'],  description:'Window detection'], // fixed_window_sash
                [dp:113, name:'motorTimeout',        type:'number',  rw: 'rw', min:10,  max:90,    defVal:30,   title:'<b>Motor Timeout</b>', description:'Motor timeout'],                           // motor_timeout 
                [dp:114, name:'windowDetection',     type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'true', 1:'false'],  title:'<b>Window Detection</b>', description:'Window detection'],
            ],
            //refresh: ['refreshTS130F'],
            //refresh: ['position', 'positionState', 'upDownConfirm', 'controlBack', 'scheduleTime', '0xE001:0x0000'],
            deviceJoinName: 'Tuya Sliding Window Pusher',
            configuration : [:]
    ],
    //
    'TUYA_TS130F_MODULE'   : [
            description   : 'Tuya TS130F Module',   //
            device        : [type: 'COVERING', powerSource: 'ac', isSleepy:false],
            capabilities  : ['Battery': false],
            //preferences   : ['invertPosition':'invertPosition', 'custom1':'0xFCC0:0x014B'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,0006,0102,E001,0000", outClusters:"0019,000A", model:"TS130F", manufacturer:"_TZ3000_e3vhyirx", controllerType: "ZGB"]
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            attributes    : [
                // [at:'0x0102:0x0000',  name:'currentLevel',          type:'number',  dt:'0x23', rw: 'ro',            description:'currentLevel (0x0102:0x0000)'],   // uint8
                [at:'0x0102:0x0008',  name:'position',              type:'number',  dt:'0x23', rw: 'rw', unit:'%',  description:'Current Position Lift Percentage'],
                [at:'0x0102:0x8000',  name:'0x0102:0x8000',         type:'enum',    dt:'0x20', rw: 'rw', map:[0: 'disabled', 1: 'enabled'], title: '<b>0x0102:0x8000</b>',   description:'0x0102:0x8000'], // enum8
                [at:'0x0102:0xF000',  name:'positionState',         type:'enum',    dt:'0x20', rw: 'rw', map:[0: 'up/open', 1: 'stop', 2: 'down/close' ], title: '<b>Position State</b>',   description:'position state (0x0102:0xF000)'],
                [at:'0x0102:0xF001',  name:'upDownConfirm',         type:'enum',    dt:'0x20', rw: 'rw', map:[0: 'false', 1: 'true'], title: '<b>upDownConfirm</b>',   description:'upDownConfirm (0x0102:0xF001)'],
                [at:'0x0102:0xF002',  name:'controlBack',           type:'enum',    dt:'0x20', rw: 'rw', map:[0: 'false', 1: 'true'], title: '<b>controlBack</b>',   description:'controlBack (0x0102:0xF002)'],
                [at:'0x0102:0xF003',  name:'scheduleTime ',         type:'number',  dt:'0x29', rw: 'rw', title: '<b>ScheduleTime</b>',   description:'ScheduleTime (0x0102:0xF003)'],  // uint16 

                [at:'0xE001:0x0000',  name:'0xE001:0x0000',         type:'number',  dt:'0x23', rw: 'rw', unit:'?', title: '<b>0xE001:0x0000</b>', description:'0xE001:0x0000'],    // array
            ],
            //refresh: ['refreshTS130F'],
            refresh: ['position', 'positionState', 'upDownConfirm', 'controlBack', 'scheduleTime', '0xE001:0x0000'],
            deviceJoinName: 'Tuya TS130F Module',
            configuration : [:]
    ],

    'DEFAULT_ZCL_WINDOW_COVERING'   : [
            description   : 'Default ZCL Window Covering',
            device        : [type: 'COVERING', powerSource: 'ac', isSleepy:false],
            capabilities  : ['Battery': false],
            //preferences   : ['invertPosition':'invertPosition', 'custom1':'0xFCC0:0x014B'],
            fingerprints  : [
                [profileId: "0104", inClusters: "0000,0003,0004,0102", outClusters: "0019", model: "E2B0-KR000Z0-HA", deviceJoinName: "eZEX Window Treatment"],                                                       // SY-IoT201-BD //SOMFY Blind Controller/eZEX
                [profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0008,0102", outClusters: "000A", manufacturer: "Feibit Co.Ltd", model: "FTB56-ZT218AK1.6", deviceJoinName: "Wistar Window Treatment"],   //Wistar Curtain Motor(CMJ)
                [profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0008,0102", outClusters: "000A", manufacturer: "Feibit Co.Ltd", model: "FTB56-ZT218AK1.8", deviceJoinName: "Wistar Window Treatment"],   //Wistar Curtain Motor(CMJ)
                [profileId: "0104", inClusters: "0000,0003,0004,0005,0102", outClusters: "0003", manufacturer: "REXENSE", model: "KG0001", deviceJoinName: "Window Treatment"],                                      //Smart Curtain Motor(BCM300D)
                [profileId: "0104", inClusters: "0000,0003,0004,0005,0102", outClusters: "0003", manufacturer: "REXENSE", model: "DY0010", deviceJoinName: "Window Treatment"],                                      //Smart Curtain Motor(DT82TV)
                [profileId: "0104", inClusters: "0000,0003,0004,0005,0102", outClusters: "0003", manufacturer: "SOMFY", model: "Glydea Ultra Curtain", deviceJoinName: "Somfy Window Treatment"],                    //Somfy Glydea Ultra
                [profileId: "0104", inClusters: "0000,0003,0004,0005,0020,0102", outClusters: "0003", manufacturer: "SOMFY", model: "Sonesse 30 WF Roller", deviceJoinName: "Somfy Window Treatment"],              // Somfy Sonesse 30 Zigbee LI-ION Pack
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            attributes    : [
                [at:'0x0102:0x0000',  name:'windowCoveringType',    type:'enum',    dt:'0x20', rw: 'ro',            description:'windowCoveringType (0x0102:0x0000)'],   // enum8
                [at:'0x0102:0x0007',  name:'configStatus',          type:'enum',    dt:'0x20', rw: 'ro',            description:'windowCoveringType (0x0102:0x0007)'],   // map8 0b0xxx xxxx
                [at:'0x0102:0x0008',  name:'position',              type:'number',  dt:'0x23', rw: 'rw', unit:'%',  description:'Current Position Lift Percentage'],    // uint8   0-0x64 
                [at:'0x0102:0x0008',  name:'tilt',                  type:'number',  dt:'0x23', rw: 'rw', unit:'%',  description:'Current Position Tilt Percentage'],     // uint8   0-0x64 
            ],
            refresh: ['position'],
            deviceJoinName: 'Default ZCL Window Covering',
            configuration : [:]
    ]

]



/*
 * -----------------------------------------------------------------------------
 * WindowCovering cluster 0x0102
 * called from parseWindowCovering() in the main code ...
 * -----------------------------------------------------------------------------
*/
void customParseWindowCoveringCluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseWindowCoveringCluster: zigbee received WindowCovering cluster (0x0102) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return }
    boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "customParseWindowCoveringCluster: received unknown WindowCovering cluster (0x0102) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

void customParseE0001Cluster(final Map descMap) {
    logDebug "customParseE0001Cluster: ${descMap}"
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return }
    boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "customParseE0001Cluster: received unknown cluster (0xE001) attribute 0x${descMap.attrId} (value ${descMap.value})"
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

    // Itterates through all settings
    logDebug 'customUpdated: updateAllPreferences()...'
    updateAllPreferences()
}

//
List<String> refreshTS130F() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT, [:], delay = 200)
    cmds += zigbee.readAttribute(zigbee.LEVEL_CONTROL_CLUSTER, ATTRIBUTE_CURRENT_LEVEL, [:], delay = 200)
    return cmds
}

// called on refresh() command from the commonLib. Thus supresses calling the standard XXXrefresh() commands from the included libraries!
List<String> customRefresh() {
    List<String> cmds = []
    cmds += refreshTS130F()
    cmds += batteryRefresh()
    logDebug "customRefresh: ${cmds} "
    return cmds
}

List<String> refreshAll() {
    logDebug 'refreshAll()'
    List<String> cmds = []
    cmds += customRefresh()         // all deviceProfile attributes + battery
    cmds += refreshFromDeviceProfileList()
    sendZigbeeCommands(cmds)
}

List<String> customConfigure() {
    List<String> cmds = []
    logDebug "customConfigure() : ${cmds} (not implemented!)"
    return cmds
}

List<String> initializeTS130F() {
    List<String> cmds = []
    logDebug 'configuring TS130F ...'
    //cmds =  ["zdo bind 0x${device.deviceNetworkId} 1 1 0x0500 {${device.zigbeeId}} {}", "delay 200" ]
    //cmds += zigbee.configureReporting(0x0500, 0x0002, 0x19, 0, 3600, 0x00, [:], delay=201)
    return cmds
}

// called from initializeDevice in the commonLib code
List<String> customInitializeDevice() {
    List<String> cmds = []
    cmds = initializeTS130F()
    logDebug "customInitializeDevice() : ${cmds}"
    return cmds
}

void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (state.deviceProfile == null) { setDeviceNameAndProfile() }               // in deviceProfileiLib.groovy
    if (fullInit == true) { resetPreferencesToDefaults() }                      // in deviceProfileiLib.groovy
    if (fullInit || settings?.maxTravelTime == null) { device.updateSetting('maxTravelTime',[value:DEFAULT_MAX_TRAVEL_TIME, type:'number'])  }
    if (fullInit || settings?.deltaPosition == null) { device.updateSetting('deltaPosition',[value:DEFAULT_POSITION_DELTA, type:'number'])  }
    if (fullInit || settings?.commandOpenCode == null) { device.updateSetting('commandOpenCode',[value:DEFAULT_COMMAND_OPEN, type:'number'])  }
    if (fullInit || settings?.commandCloseCode == null) { device.updateSetting('commandCloseCode',[value:DEFAULT_COMMAND_CLOSE, type:'number'])  }
    if (fullInit || settings?.commandStopCode == null) { device.updateSetting('commandStopCode',[value:DEFAULT_COMMAND_PAUSE, type:'number'])  }
    if (fullInit || settings?.substituteOpenClose == null) { device.updateSetting('substituteOpenClose',[value:false, type:'bool'])  }
    if (fullInit || settings?.invertPosition == null) { device.updateSetting('invertPosition',[value:false, type:'bool'])  }
    if (fullInit || settings?.targetAsCurrentPosition == null) { device.updateSetting('targetAsCurrentPosition',[value:false, type:'bool'])  }
    // TUYA pusher specific
    //
    if (fullInit) {
        Map mapAttr = DEVICE?.tuyaDPs?.find { it.name == 'control' }
        if (mapAttr == null) { mapAttr = DEVICE?.attributes?.find { it.name == 'control' } }
        //logDebug "customInitializeVars: control attribute mapAttr: ${mapAttr}"
        if (mapAttr != null) {
            Map map = mapAttr.map
            //logDebug "customInitializeVars: map: ${map}"
            if (map != null) {
                Integer commandValue = (map.find { entry -> entry.value == 'open' }?.key) ; if (commandValue == null) { commandValue = DEFAULT_COMMAND_OPEN }
                device.updateSetting('commandOpenCode',[value: commandValue, type:'number']) 
                commandValue = (map.find { entry -> entry.value == 'close' }?.key) ; if (commandValue == null) { commandValue = DEFAULT_COMMAND_CLOSE }
                device.updateSetting('commandCloseCode',[value: commandValue, type:'number']) 
                commandValue = (map.find { entry -> entry.value == 'stop' }?.key) ; if (commandValue == null) { commandValue = DEFAULT_COMMAND_PAUSE }
                device.updateSetting('commandStopCode',[value: commandValue, type:'number']) 
            }
        }
    }
    
    logDebug "customInitializeVars: (${fullInit}) settings = ${settings}"
}

// called from initializeVars() in the main code ...
void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents(${fullInit})"
    if (fullInit || device.currentState('windowShade') == null) { sendEvent(name: 'windowShade', value: 'unknown', descriptionText: 'initializing...', type: 'digital') }
    if (fullInit || device.currentState('position') == null) { sendEvent(name: 'position', value: 0, unit: '%', descriptionText: 'initializing...', type: 'digital') }
    if (fullInit || device.currentState('targetPosition') == null) { sendEvent(name: 'targetPosition', value: 0, unit: '%', descriptionText: 'initializing...', type: 'digital') }
}

// called from processFoundItem  (processTuyaDPfromDeviceProfile and ) processClusterAttributeFromDeviceProfile in deviceProfileLib when a Zigbee message was found defined in the device profile map
//
//
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
void customProcessDeviceProfileEvent(final Map descMap, final String name, final valueScaled, final String unitText, final String descText) {
    logDebug "customProcessDeviceProfileEvent(${name}, ${valueScaled}) called"
    Map eventMap = [name: name, value: valueScaled, unit: unitText, descriptionText: descText, type: 'physical', isStateChange: true]
    switch (name) {
        case 'battery' :
            sendBatteryPercentageEvent(valueScaled as Integer)
            break
        case 'position' :
            processCurrentPosition(eventMap)
            if (getDeviceProfile() == 'ZEMISMART_ZM85EL_1X') {  // position report is sent by the device when the targetPosition is reached?
                sendEvent(name: 'workState', value:'idle', type: 'digital', isStateChange: true)
            }
            break
        case 'workState' :  // ZEMISMART_ZM85EL_1X
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)
            logDebug "event ${name} sent w/ value ${valueScaled}"
            logInfo "${descText}"     
            //
            log.trace "workState = ${valueScaled}"
            sendWindowShadeEvent('moving', 'moving')
            break
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
void alarmSelfTest(Number par) {
    logDebug "alarmSelfTest(${par})"
    ping()  // make the device awake
    List<String> cmds = []
    cmds += zigbee.writeAttribute(0xFCC0, 0x0127, 0x10, 1, [mfgCode:0x115f], delay=200)
    sendZigbeeCommands(cmds)
}
*/


//////////////////////////////////// Matter Generic Component Window Shade'//////////////////////////////////////



int invertPositionIfNeeded(int position) {
    int value =  (settings?.invertPosition ?: false) ? (100 - position) as Integer : position
    if (value < 0)   { value = 0 }
    if (value > 100) { value = 100 }
    return value
}

void processCurrentPositionBridgeEvent(final Map d) {
    Map map = new HashMap(d)
    //stopOperationTimeoutTimer()
    if (settings?.targetAsCurrentPosition == true) {
        map.name = 'targetPosition'
        if (logEnable) { log.debug "${device.displayName} processCurrentPositionBridgeEvent: targetAsCurrentPosition is true -> <b>processing as targetPosition ${map.value} !</b>" }
        processTargetPosition(map)
    }
    else {
        if (logEnable) { log.debug "${device.displayName} processCurrentPositionBridgeEvent: currentPosition reported is ${map.value}" }
        processCurrentPosition(map)
    }
}

void processCurrentPosition(final Map d) {
    Map map = new HashMap(d)
    stopOperationTimeoutTimer()
    // we may have the currentPosition reported inverted !
    map.value = invertPositionIfNeeded(d.value as int)
    if (logEnable) { log.debug "${device.displayName} processCurrentPosition: ${map.value} (was ${d.value})" }
    map.name = 'position'
    map.unit = '%'
    map.descriptionText = "${device.displayName} position is ${map.value}%"
    if (map.isRefresh) {
        map.descriptionText += ' [refresh]'
    }
    if (txtEnable) { log.info "${map.descriptionText}" }
    sendEvent(map)
    updateWindowShadeStatus(map.value as Integer, device.currentValue('targetPosition') as Integer, /*isFinal =*/ true, /*isDigital =*/ false)
}

void updateWindowShadeStatus(Integer currentPositionPar, Integer targetPositionPar, Boolean isFinal, Boolean isDigital) {
    String value = 'unknown'
    String descriptionText = 'unknown'
    String type = isDigital ? 'digital' : 'physical'
    //log.trace "updateWindowShadeStatus: currentPositionPar = ${currentPositionPar}, targetPositionPar = ${targetPositionPar}"
    Integer currentPosition = safeToInt(currentPositionPar)
    Integer targetPosition = safeToInt(targetPositionPar)

    if (isFinal == true) {
        if (isFullyClosed(currentPosition)) {
            value = 'closed'
        }
        else if (isFullyOpen(currentPosition)) {
            value = 'open'
        }
        else {
            value = 'partially open'
        }
    }
    else {
        if (targetPosition < currentPosition) {
            value =  'opening'
        }
        else if (targetPosition > currentPosition) {
            value = 'closing'
        }
        else {
            //value = 'stopping'
            if (isFullyClosed(currentPosition)) {
                value = 'closed'
            }
            else if (isFullyOpen(currentPosition)) {
                value = 'open'
            }            
        }
    }
    descriptionText = "${device.displayName} windowShade is ${value} [${type}]"
    sendEvent(name: 'windowShade', value: value, descriptionText: descriptionText, type: type)
    if (logEnable) { log.debug "${device.displayName} updateWindowShadeStatus: isFinal: ${isFinal}, substituteOpenClose: ${settings?.substituteOpenClose}, targetPosition: ${targetPosition}, currentPosition: ${currentPosition}, windowShade: ${device.currentValue('windowShade')}" }
    if (txtEnable) { log.info "${descriptionText}" }
}

void sendWindowShadeEvent(String value, String descriptionText) {
    sendEvent(name: 'windowShade', value: value, descriptionText: descriptionText)
    if (txtEnable) { log.info "${device.displayName} windowShade is ${value}" }
}

void processTargetPositionBridgeEvent(final Map d) {
    Map map = new HashMap(d)
    stopOperationTimeoutTimer()
    if (logEnable) { log.debug "${device.displayName} processTargetPositionBridgeEvent: ${d}" }
    if (settings?.targetAsCurrentPosition) {
        if (logEnable) { log.debug "${device.displayName} processTargetPositionBridgeEvent: targetAsCurrentPosition is true" }
        map.name = 'position'
        processCurrentPosition(map)
        return
    }
    processTargetPosition(map)
}

void processTargetPosition(final Map d) {
    //log.trace "processTargetPosition: value: ${d.value}"
    Map map = new HashMap(d)
    map.value = invertPositionIfNeeded(safeToInt(d.value))
    map.descriptionText = "${device.displayName} targetPosition is ${map.value}%"
    if (map.isRefresh) {
        map.descriptionText += ' [refresh]'
    }
    map.name = 'targetPosition'
    map.unit = '%'
    if (logEnable) { log.debug "${device.displayName} processTargetPosition: ${map.value} (was ${d.value})" }
    if (txtEnable) { log.info "${map.descriptionText}" }
    //
    //stopOperationTimeoutTimer()
    sendEvent(map)
    if (!map.isRefresh) {
        // skip upddating the windowShade status on targetPosition refresh
        updateWindowShadeStatus(device.currentValue('position') as int, map.value as int, /*isFinal =*/ false, /*isDigital =*/ false)
    }
}

void processOperationalStatusBridgeEvent(Map d) {
    stopOperationTimeoutTimer()
    if (logEnable) { log.debug "${device.displayName} processOperationalStatusBridgeEvent: ${d}" }
    if (d.descriptionText && txtEnable) { log.info "${device.displayName} ${d.descriptionText}" }
    sendEvent(d)
}




// Component command to refresh the device
// TODO !
void refreshMatter() {
    if (txtEnable) { log.info "${device.displayName} refreshing ..." }
    state.standardOpenClose = 'DEFAULT_OPEN_PERCENT = 0% DEFAULT_CLOSED_PERCENT = 100%'
    state.driverVersion = matterComponentWindowShadeVersion + ' (' + matterComponentWindowShadeStamp + ')'
    parent?.componentRefresh(device)
}


// Called when the settings are updated
// TODO !
void updatedMatter() {
    if (txtEnable) { log.info "${device.displayName} driver configuration updated" }
    if (logEnable) {
        log.debug settings
        runIn(86400, 'logsOff')
    }
    if ((state.substituteOpenClose ?: false) != settings?.substituteOpenClose) {
        state.substituteOpenClose = settings?.substituteOpenClose
        if (logEnable) { log.debug "${device.displayName} substituteOpenClose: ${settings?.substituteOpenClose}" }
        /*
        String currentOpenClose = device.currentWindowShade
        String newOpenClose = currentOpenClose == 'open' ? 'closed' : currentOpenClose == 'closed' ? 'open' : currentOpenClose
        if (currentOpenClose != newOpenClose) {
            sendEvent([name:'windowShade', value: newOpenClose, type: 'digital', descriptionText: "windowShade state inverted to ${newOpenClose}", isStateChange:true])
        }
        */
    }
    else {
        if (logEnable) { log.debug "${device.displayName} invertMotion: no change" }
    }
    //
    if ((state.invertPosition ?: false) != settings?.invertPosition) {
        state.invertPosition = settings?.invertPosition
        if (logEnable) { log.debug "${device.displayName} invertPosition: ${settings?.invertPosition}" }
    }
    else {
        if (logEnable) { log.debug "${device.displayName} invertPosition: no change" }
    }
}



void test(String par) {
    List<String> cmds = []
    //cmds += zigbee.configureReporting(0xFCC0, 0x013A, 0x20, 0, 3600, 0x00, [mfgCode:0x115f], delay=203)
    //cmds += zigbee.configureReporting(0xFCC0, 0x013B, 0x23, 0, 3600, 0x00, [mfgCode:0x115f], delay=204)
    cmds += zigbee.configureReporting(0xFCC0, 0x013C, 0x23, 0, 3600, 0x00, [:], delay=204)

    //sendZigbeeCommands(cmds)

    queryPowerSource()
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


//////////////////////////////////////////////////////////////// new library - TODO /////////////////////////////////////////////////////////////////

@Field static final String libWindowShadeVersion = '1.0.0'
@Field static final String libWindowShadeStamp   = '2024/03/16 9:29 AM'

private getCLUSTER_WINDOW_COVERING()        { 0x0102 }

private getDEFAULT_COMMAND_OPEN()                   { 0x00 }
private getDEFAULT_COMMAND_CLOSE()                  { 0x01 }
private getDEFAULT_COMMAND_PAUSE()                  { 0x02 }

private getCOMMAND_MOVE_LEVEL_ONOFF()       { 0x04 }      // Go To Lift Value
private getCOMMAND_GOTO_LIFT_PERCENTAGE()   { 0x05 }      // Go to Lift Percentage
private getCOMMAND_GOTO_TILT_VALUE()        { 0x07 }     // Go To Tilt Value
private getCOMMAND_GOTO_TILT_PERCENTAGE()   { 0x08 }      // Go to Tilt Percentage

private getATTRIBUTE_POSITION_LIFT()        { 0x0008 }
private getATTRIBUTE_CURRENT_LEVEL()        { 0x0000 }

@Field static final Integer DEFAULT_OPEN_PERCENT   = 100  // this is Hubitat standard!
@Field static final Integer DEFAULT_CLOSED_PERCENT = 0    // this is Hubitat standard!

@Field static final Integer DEFAULT_POSITION_DELTA = 2          //settings.deltaPosition , percentage
@Field static final Integer DEFAULT_MAX_TRAVEL_TIME = 30        //settings.maxTravelTime , seconds

int getDelta() { return settings?.deltaPosition != null ? settings?.deltaPosition as int : DEFAULT_POSITION_DELTA }

//int getFullyOpen()   { return settings?.invertOpenClose ? DEFAULT_CLOSED_PERCENT : DEFAULT_OPEN_PERCENT }
//int getFullyClosed() { return settings?.invertOpenClose ? DEFAULT_OPEN_PERCENT : DEFAULT_CLOSED_PERCENT }
//int getFullyOpen()   { return settings?.invertPosition ? DEFAULT_CLOSED_PERCENT : DEFAULT_OPEN_PERCENT }
//int getFullyClosed() { return settings?.invertPosition ? DEFAULT_OPEN_PERCENT : DEFAULT_CLOSED_PERCENT }

int getFullyOpen()   { return DEFAULT_OPEN_PERCENT }
int getFullyClosed() { return DEFAULT_CLOSED_PERCENT }

boolean isFullyOpen(int position)   { return Math.abs(position - getFullyOpen()) < getDelta() }
boolean isFullyClosed(int position) { return Math.abs(position - getFullyClosed()) < getDelta() }

// Tuya DPs

@Field final int DP_ID_COMMAND = 0x01
@Field final int DP_ID_TARGET_POSITION = 0x02
@Field final int DP_ID_CURRENT_POSITION = 0x03
@Field final int DP_ID_DIRECTION = 0x05
@Field final int DP_ID_COMMAND_REMOTE = 0x07
@Field final int DP_ID_MODE = 0x65
@Field final int DP_ID_SPEED = 0x69
@Field final int DP_ID_BATTERY = 0x0D

@Field final int DP_COMMAND_OPEN = 0x00
@Field final int DP_COMMAND_STOP = 0x01
@Field final int DP_COMMAND_CLOSE = 0x02
@Field final int DP_COMMAND_CONTINUE = 0x03
@Field final int DP_COMMAND_LIFTPERCENT = 0x05
@Field final int DP_COMMAND_CUSTOM = 0x06


// ------------ helper functions ------------

// TODO - move to the commonLib
public BigDecimal scale(int value, int fromLow, int fromHigh, int toLow, int toHigh) {
    return  BigDecimal.valueOf(toHigh - toLow) *  BigDecimal.valueOf(value - fromLow) /  BigDecimal.valueOf(fromHigh - fromLow) + toLow
}

// called from open() close() setPosition() stopPositionChange()
public void startOperationTimeoutTimer() {
    int travelTime = Math.abs(device.currentValue('position') ?: 0 - device.currentValue('targetPosition') ?: 0)
    Integer scaledTimerValue = scale(travelTime, 0, 100, 1, (settings?.maxTravelTime as Integer) ?: 0) + 1.5
    logDebug "startOperationTimeoutTimer: ${scaledTimerValue} seconds"
    runIn(scaledTimerValue, 'operationTimeoutTimer', [overwrite: true])
}

public void stopOperationTimeoutTimer() {
    logDebug "stopOperationTimeoutTimer" 
    unschedule('operationTimeoutTimer')
}

public void operationTimeoutTimer() {
    logWarn "operationTimeout!" 
    updateWindowShadeStatus(device.currentValue('position') as Integer, device.currentValue('targetPosition') as Integer, /*isFinal =*/ true, /*isDigital =*/ true)
}

// ------------ Window Shade standard Commands ------------
                                    // Commands: close(); open(); setPosition(position) position required (NUMBER) - Shade position (0 to 100);
                                    //           startPositionChange(direction): direction required (ENUM) - Direction for position change request ["open", "close"]
                                    //           stopPositionChange()

String getTuyaDPbyCommandName(String commandName) {
    Map mapAttr = DEVICE?.tuyaDPs?.find { it.name == commandName }
    if (mapAttr == null) { mapAttr = DEVICE?.attributes?.find { it.name == commandName } }
    logDebug "getTuyaDPbyCommandName: mapAttr: ${mapAttr}"
    if (mapAttr != null) {
        Integer dp = mapAttr.dp
        if (dp != null) {
            logDebug "getTuyaDPbyCommandName: dp: ${zigbee.convertToHexString(dp, 2)}"
            return zigbee.convertToHexString(dp, 2)
        }
    }
    return null
}

void sendOpen() { // open is 100 %
    logDebug "sendOpen() dpCommandOpen = ${settings?.commandOpenCode}"
    Integer dpCommandOpen = settings?.commandOpenCode
    String sDirection = device.currentValue('motorDirection') ?: 'unknown'
    logDebug "sendOpen: sending command open (${dpCommandOpen}), sDirection = ${sDirection}"
    List<String> cmds = []

    if (DEVICE?.device?.isTuyaEF00 == true) {
        cmds = getTuyaCommand(getTuyaDPbyCommandName('control'), DP_TYPE_ENUM, zigbee.convertToHexString(dpCommandOpen as int, 2))
    }
    else {
        cmds = zigbee.command(0x0102, dpCommandOpen as int, [:], delay = 200)
    }

    if (cmds != null && cmds != []) {
        sendZigbeeCommands(cmds)
    }
    else {
        logWarn "sendOpen: no cmds!"
    }
}

// command to open device
void open() {
    if (txtEnable) { log.info "${device.displayName} opening" }
    sendEvent(name: 'targetPosition', value: getFullyOpen(), descriptionText: "targetPosition set to ${getFullyOpen()}", type: 'digital')
    if (settings?.substituteOpenClose == false) {
        sendOpen()
    }
    else {
        setPosition(getFullyOpen())
    }
    startOperationTimeoutTimer()
    sendWindowShadeEvent('opening', "${device.displayName} windowShade is opening")
}

void sendClose() {     // close is 0 %
    logDebug "sendClose() dpCommandClose = ${settings?.commandCloseCode}"
    Integer dpCommandClose = settings?.commandCloseCode
    String sDirection = device.currentValue('motorDirection') ?: 'unknown'
    logDebug "sendClose: sending command close (${dpCommandClose}), sDirection = ${sDirection}"
    List<String> cmds = []

    if (DEVICE?.device?.isTuyaEF00 == true) {
        cmds = getTuyaCommand(getTuyaDPbyCommandName('control'), DP_TYPE_ENUM, zigbee.convertToHexString(dpCommandClose as int, 2))
    }
    else {
        cmds = zigbee.command(0x0102, dpCommandClose as int, [:], delay = 200)
    }

    if (cmds != null && cmds != []) {
        sendZigbeeCommands(cmds)
    }
    else {
        logWarn "sendClose: no cmds!"
    }
}

// standard capability 'WindowShade' command to close the device
void close() {
    logDebug "close: [digital]" 
    sendEvent(name: 'targetPosition', value: getFullyClosed(), descriptionText: "targetPosition set to ${getFullyClosed()}", type: 'digital')
    if (settings?.substituteOpenClose == false) {
        logDebug "close: sending sendClose() command"
        sendClose()
    }
    else {
        logDebug "close: sending sendSetPosition(${getFullyClosed()}) command" 
        setPosition(getFullyClosed())
    }
    startOperationTimeoutTimer()
    sendWindowShadeEvent('closing', "${device.displayName} windowShade is closing [digital]")
}

void sendStopPositionChange() {
    int dpCommandStop = settings?.commandStopCode
    logDebug "sendStopPositionChange: sending command (${dpCommandStop})"
    List<String> cmds = []
    if (DEVICE?.device?.isTuyaEF00 == true) {
        cmds = getTuyaCommand(getTuyaDPbyCommandName('control'), DP_TYPE_ENUM, zigbee.convertToHexString(dpCommandStop as int, 2))
    }
    else {
        cmds = zigbee.command(0x0102, dpCommandStop as int, [:], delay = 200)
    }

    if (cmds != null && cmds != []) {
        sendZigbeeCommands(cmds)
    }
    else {
        logWarn "sendStopPositionChange: no cmds!"
    }
}

// Component command to start position change of device
void stopPositionChange() {
    logDebug "stopPositionChange:" 
    sendStopPositionChange()
    startOperationTimeoutTimer()
}


void sendSetPosition(final BigDecimal positionParam) {
    int position = positionParam as Integer
    if (position == null || position < 0 || position > 100) {
        throw new Exception("Invalid position ${position}. Position must be between 0 and 100 inclusive.")
    }
    logDebug "setPosition: target is ${position}, currentPosition=${device.currentValue('position')}"
    if (settings?.invertPosition == true) {
        position = 100 - position
    }
    List<String> cmds = []
    if (DEVICE?.device?.isTuyaEF00 == true) {
        cmds = getTuyaCommand(getTuyaDPbyCommandName('position'), DP_TYPE_VALUE, zigbee.convertToHexString(position as int, 8))
    }
    else {
        cmds = zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_GOTO_LIFT_PERCENTAGE, zigbee.convertToHexString(position, 2))
    }
    if (cmds != null && cmds != []) {
        sendZigbeeCommands(cmds)
    }
    else {
        logWarn "sendStopPositionChange: no cmds!"
    }
}

// Standard command to set position of device
void setPosition(BigDecimal targetPosition) {
    logInfo "setting target position ${targetPosition}% (current position is ${device.currentValue('position')})"
    sendEvent(name: 'targetPosition', value: targetPosition as Integer, descriptionText: "targetPosition set to ${targetPosition}", type: 'digital')
    updateWindowShadeStatus(device?.currentValue('position') as Integer, targetPosition as Integer, isFinal = false, isDigital = true)
    BigDecimal componentTargetPosition = invertPositionIfNeeded(targetPosition as Integer)
    logDebug "setPosition: inverted componentTargetPosition: ${componentTargetPosition}"
    sendSetPosition(componentTargetPosition)
    startOperationTimeoutTimer()
}

// Standard command to start position change of device
void startPositionChange(String change) {
    logDebug "startPositionChange: ${change}"
    if (change == 'open') { open() }
    else { close() }
}



// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', documentationLink: '', // library marker kkossev.commonLib, line 4
    version: '3.3.1' // library marker kkossev.commonLib, line 5
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
  * ver. 3.3.1  2024-07-01 kkossev  - (dev.branch) remove isFingerbot() dependancy  // library marker kkossev.commonLib, line 40
  * // library marker kkossev.commonLib, line 41
  *                                   TODO: offlineCtr is not increasing! (ZBMicro) // library marker kkossev.commonLib, line 42
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 43
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 44
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 45
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 46
  *                                   TODO: Versions of the main module + included libraries // library marker kkossev.commonLib, line 47
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 48
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 49
  * // library marker kkossev.commonLib, line 50
*/ // library marker kkossev.commonLib, line 51

String commonLibVersion() { '3.3.1' } // library marker kkossev.commonLib, line 53
String commonLibStamp() { '2024/07/01 11:40 AM' } // library marker kkossev.commonLib, line 54

import groovy.transform.Field // library marker kkossev.commonLib, line 56
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 57
import hubitat.device.Protocol // library marker kkossev.commonLib, line 58
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 59
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 60
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 61
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 62
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 63
import java.math.BigDecimal // library marker kkossev.commonLib, line 64

metadata { // library marker kkossev.commonLib, line 66
        if (_DEBUG) { // library marker kkossev.commonLib, line 67
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 68
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 69
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
        capability 'Power Source'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 81

        // common attributes for all device types // library marker kkossev.commonLib, line 83
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 84
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 85
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 86

        // common commands for all device types // library marker kkossev.commonLib, line 88
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 89

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 91
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 92

    preferences { // library marker kkossev.commonLib, line 94
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 95
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 96
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 97

        if (device) { // library marker kkossev.commonLib, line 99
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device...', defaultValue: false // library marker kkossev.commonLib, line 100
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 101
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 102
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 103
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 104
            } // library marker kkossev.commonLib, line 105
        } // library marker kkossev.commonLib, line 106
    } // library marker kkossev.commonLib, line 107
} // library marker kkossev.commonLib, line 108

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 110
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 111
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 112
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 113
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 114
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 115
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 116
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 117
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 118
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 119
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 120

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 122
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 123
] // library marker kkossev.commonLib, line 124
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 125
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 126
] // library marker kkossev.commonLib, line 127

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 129
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 130
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 131
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 132
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 133
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 134
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 135
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 136
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 137
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 138
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 139
] // library marker kkossev.commonLib, line 140

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 142
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 143
//boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 144
//boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 145
//boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 146
//boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 147

/** // library marker kkossev.commonLib, line 149
 * Parse Zigbee message // library marker kkossev.commonLib, line 150
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 151
 */ // library marker kkossev.commonLib, line 152
void parse(final String description) { // library marker kkossev.commonLib, line 153
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 154
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 155
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 156
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 157

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 159
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 160
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 161
            parseIasMessage(description) // library marker kkossev.commonLib, line 162
        } // library marker kkossev.commonLib, line 163
        else { // library marker kkossev.commonLib, line 164
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 165
        } // library marker kkossev.commonLib, line 166
        return // library marker kkossev.commonLib, line 167
    } // library marker kkossev.commonLib, line 168
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 169
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 170
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 171
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 172
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 173
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 174
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 175
        return // library marker kkossev.commonLib, line 176
    } // library marker kkossev.commonLib, line 177

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 179
        return // library marker kkossev.commonLib, line 180
    } // library marker kkossev.commonLib, line 181
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 182

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 184
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 185

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 187
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 188
        return // library marker kkossev.commonLib, line 189
    } // library marker kkossev.commonLib, line 190
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 191
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 192
        return // library marker kkossev.commonLib, line 193
    } // library marker kkossev.commonLib, line 194
    // // library marker kkossev.commonLib, line 195
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 196
    // // library marker kkossev.commonLib, line 197
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 198
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 199
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 200
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 201
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 202
            } // library marker kkossev.commonLib, line 203
            break // library marker kkossev.commonLib, line 204
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 205
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 206
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 207
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 208
            } // library marker kkossev.commonLib, line 209
            break // library marker kkossev.commonLib, line 210
        default: // library marker kkossev.commonLib, line 211
            if (settings.logEnable) { // library marker kkossev.commonLib, line 212
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 213
            } // library marker kkossev.commonLib, line 214
            break // library marker kkossev.commonLib, line 215
    } // library marker kkossev.commonLib, line 216
} // library marker kkossev.commonLib, line 217

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 219
    0x0000: 'Basic',                0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x000C: 'AnalogInput', // library marker kkossev.commonLib, line 220
    0x0006: 'OnOff',                0x0008: 'LevelControl',     0x0012: 'MultistateInput',  0x0102: 'WindowCovering',   0x0201: 'Thermostat',   0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 221
    0x0400: 'Illuminance',          0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 222
    0x0B04: 'ElectricalMeasure',    0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 223
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 224
] // library marker kkossev.commonLib, line 225

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 227
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 228
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 229
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 230
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 231
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 232
        return false // library marker kkossev.commonLib, line 233
    } // library marker kkossev.commonLib, line 234
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 235
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 236
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 237
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 238
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 239
        return true // library marker kkossev.commonLib, line 240
    } // library marker kkossev.commonLib, line 241
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 242
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 243
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 244
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 245
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 246
        return true // library marker kkossev.commonLib, line 247
    } // library marker kkossev.commonLib, line 248
    if (device?.getDataValue('model') != 'ZigUSB' && descMap.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 249
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 250
    } // library marker kkossev.commonLib, line 251
    return false // library marker kkossev.commonLib, line 252
} // library marker kkossev.commonLib, line 253

static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 255
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 256
} // library marker kkossev.commonLib, line 257

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 259
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 260
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 261
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 262
    } // library marker kkossev.commonLib, line 263
    return false // library marker kkossev.commonLib, line 264
} // library marker kkossev.commonLib, line 265

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 267
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 268
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 269
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 270
    } // library marker kkossev.commonLib, line 271
    return false // library marker kkossev.commonLib, line 272
} // library marker kkossev.commonLib, line 273

boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 275
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 276
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 277
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 278
    } // library marker kkossev.commonLib, line 279
    return false // library marker kkossev.commonLib, line 280
} // library marker kkossev.commonLib, line 281

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 283
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 284
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 285
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 286
] // library marker kkossev.commonLib, line 287

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 289
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 290
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 291
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 292
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 293
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 294
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 295
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 296
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 297
    List<String> cmds = [] // library marker kkossev.commonLib, line 298
    switch (clusterId) { // library marker kkossev.commonLib, line 299
        case 0x0005 : // library marker kkossev.commonLib, line 300
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 301
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 302
            // send the active endpoint response // library marker kkossev.commonLib, line 303
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 304
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 305
            break // library marker kkossev.commonLib, line 306
        case 0x0006 : // library marker kkossev.commonLib, line 307
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 308
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 309
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 310
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 311
            break // library marker kkossev.commonLib, line 312
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 313
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 314
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 315
            break // library marker kkossev.commonLib, line 316
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 317
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 318
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 319
            break // library marker kkossev.commonLib, line 320
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 321
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 322
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 323
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 324
            break // library marker kkossev.commonLib, line 325
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 326
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 327
            break // library marker kkossev.commonLib, line 328
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 329
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 330
            if (settings?.logEnable) { log.debug "${clusterInfo}" } // library marker kkossev.commonLib, line 331
            break // library marker kkossev.commonLib, line 332
        default : // library marker kkossev.commonLib, line 333
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 334
            break // library marker kkossev.commonLib, line 335
    } // library marker kkossev.commonLib, line 336
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 337
} // library marker kkossev.commonLib, line 338

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 340
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 341
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 342
    switch (commandId) { // library marker kkossev.commonLib, line 343
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 344
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 345
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 346
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 347
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 348
        default: // library marker kkossev.commonLib, line 349
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 350
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 351
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 352
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 353
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 354
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 355
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 356
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 357
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 358
            } // library marker kkossev.commonLib, line 359
            break // library marker kkossev.commonLib, line 360
    } // library marker kkossev.commonLib, line 361
} // library marker kkossev.commonLib, line 362

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 364
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 365
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 366
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 367
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 368
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 369
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 370
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 371
    } // library marker kkossev.commonLib, line 372
    else { // library marker kkossev.commonLib, line 373
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 374
    } // library marker kkossev.commonLib, line 375
} // library marker kkossev.commonLib, line 376

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 378
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 379
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 380
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 381
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 382
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 383
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 384
    } // library marker kkossev.commonLib, line 385
    else { // library marker kkossev.commonLib, line 386
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 387
    } // library marker kkossev.commonLib, line 388
} // library marker kkossev.commonLib, line 389

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 391
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 392
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 393
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 394
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 395
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 396
        state.reportingEnabled = true // library marker kkossev.commonLib, line 397
    } // library marker kkossev.commonLib, line 398
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 399
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 400
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 401
    } else { // library marker kkossev.commonLib, line 402
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 403
    } // library marker kkossev.commonLib, line 404
} // library marker kkossev.commonLib, line 405

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 407
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 408
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 409
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 410
    if (status == 0) { // library marker kkossev.commonLib, line 411
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 412
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 413
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 414
        int delta = 0 // library marker kkossev.commonLib, line 415
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 416
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 417
        } // library marker kkossev.commonLib, line 418
        else { // library marker kkossev.commonLib, line 419
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 420
        } // library marker kkossev.commonLib, line 421
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 422
    } // library marker kkossev.commonLib, line 423
    else { // library marker kkossev.commonLib, line 424
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 425
    } // library marker kkossev.commonLib, line 426
} // library marker kkossev.commonLib, line 427

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 429
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 430
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 431
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 432
        return false // library marker kkossev.commonLib, line 433
    } // library marker kkossev.commonLib, line 434
    // execute the customHandler function // library marker kkossev.commonLib, line 435
    boolean result = false // library marker kkossev.commonLib, line 436
    try { // library marker kkossev.commonLib, line 437
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 438
    } // library marker kkossev.commonLib, line 439
    catch (e) { // library marker kkossev.commonLib, line 440
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 441
        return false // library marker kkossev.commonLib, line 442
    } // library marker kkossev.commonLib, line 443
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 444
    return result // library marker kkossev.commonLib, line 445
} // library marker kkossev.commonLib, line 446

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 448
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 449
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 450
    final String commandId = data[0] // library marker kkossev.commonLib, line 451
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 452
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 453
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 454
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 455
    } else { // library marker kkossev.commonLib, line 456
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 457
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 458
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 459
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 460
        } // library marker kkossev.commonLib, line 461
    } // library marker kkossev.commonLib, line 462
} // library marker kkossev.commonLib, line 463

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 465
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 466
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 467
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 468

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 470
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 471
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 472
] // library marker kkossev.commonLib, line 473

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 475
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 476
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 477
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 478
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 479
] // library marker kkossev.commonLib, line 480

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 482
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 483
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 484
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 485
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 486
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 487
    return avg // library marker kkossev.commonLib, line 488
} // library marker kkossev.commonLib, line 489

/* // library marker kkossev.commonLib, line 491
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 492
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 493
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 494
*/ // library marker kkossev.commonLib, line 495
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 496

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 498
void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 499
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 500
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 501
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 502
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 503
        case 0x0000: // library marker kkossev.commonLib, line 504
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 505
            break // library marker kkossev.commonLib, line 506
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 507
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 508
            if (isPing) { // library marker kkossev.commonLib, line 509
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 510
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 511
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 512
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 513
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 514
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 515
                    sendRttEvent() // library marker kkossev.commonLib, line 516
                } // library marker kkossev.commonLib, line 517
                else { // library marker kkossev.commonLib, line 518
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 519
                } // library marker kkossev.commonLib, line 520
                state.states['isPing'] = false // library marker kkossev.commonLib, line 521
            } // library marker kkossev.commonLib, line 522
            else { // library marker kkossev.commonLib, line 523
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 524
            } // library marker kkossev.commonLib, line 525
            break // library marker kkossev.commonLib, line 526
        case 0x0004: // library marker kkossev.commonLib, line 527
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 528
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 529
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 530
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 531
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 532
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 533
            } // library marker kkossev.commonLib, line 534
            break // library marker kkossev.commonLib, line 535
        case 0x0005: // library marker kkossev.commonLib, line 536
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 537
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 538
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 539
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 540
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 541
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 542
            } // library marker kkossev.commonLib, line 543
            break // library marker kkossev.commonLib, line 544
        case 0x0007: // library marker kkossev.commonLib, line 545
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 546
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 547
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 548
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 549
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 550
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 551
            } // library marker kkossev.commonLib, line 552
            break // library marker kkossev.commonLib, line 553
        case 0xFFDF: // library marker kkossev.commonLib, line 554
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 555
            break // library marker kkossev.commonLib, line 556
        case 0xFFE2: // library marker kkossev.commonLib, line 557
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 558
            break // library marker kkossev.commonLib, line 559
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 560
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 561
            break // library marker kkossev.commonLib, line 562
        case 0xFFFE: // library marker kkossev.commonLib, line 563
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 564
            break // library marker kkossev.commonLib, line 565
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 566
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 567
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 568
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 569
            break // library marker kkossev.commonLib, line 570
        default: // library marker kkossev.commonLib, line 571
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 572
            break // library marker kkossev.commonLib, line 573
    } // library marker kkossev.commonLib, line 574
} // library marker kkossev.commonLib, line 575

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 577
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 578
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 579

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 581
    Map descMap = [:] // library marker kkossev.commonLib, line 582
    try { // library marker kkossev.commonLib, line 583
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 584
    } // library marker kkossev.commonLib, line 585
    catch (e1) { // library marker kkossev.commonLib, line 586
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 587
        // try alternative custom parsing // library marker kkossev.commonLib, line 588
        descMap = [:] // library marker kkossev.commonLib, line 589
        try { // library marker kkossev.commonLib, line 590
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 591
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 592
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 593
            } // library marker kkossev.commonLib, line 594
        } // library marker kkossev.commonLib, line 595
        catch (e2) { // library marker kkossev.commonLib, line 596
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 597
            return [:] // library marker kkossev.commonLib, line 598
        } // library marker kkossev.commonLib, line 599
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 600
    } // library marker kkossev.commonLib, line 601
    return descMap // library marker kkossev.commonLib, line 602
} // library marker kkossev.commonLib, line 603

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 605
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 606
        return false // library marker kkossev.commonLib, line 607
    } // library marker kkossev.commonLib, line 608
    // try to parse ... // library marker kkossev.commonLib, line 609
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 610
    Map descMap = [:] // library marker kkossev.commonLib, line 611
    try { // library marker kkossev.commonLib, line 612
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 613
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 614
    } // library marker kkossev.commonLib, line 615
    catch (e) { // library marker kkossev.commonLib, line 616
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 617
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 618
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 619
        return true // library marker kkossev.commonLib, line 620
    } // library marker kkossev.commonLib, line 621

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 623
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 624
    } // library marker kkossev.commonLib, line 625
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 626
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 627
    } // library marker kkossev.commonLib, line 628
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 629
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 630
    } // library marker kkossev.commonLib, line 631
    else { // library marker kkossev.commonLib, line 632
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 633
        return false // library marker kkossev.commonLib, line 634
    } // library marker kkossev.commonLib, line 635
    return true    // processed // library marker kkossev.commonLib, line 636
} // library marker kkossev.commonLib, line 637

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 639
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 640
  /* // library marker kkossev.commonLib, line 641
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 642
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 643
        return true // library marker kkossev.commonLib, line 644
    } // library marker kkossev.commonLib, line 645
*/ // library marker kkossev.commonLib, line 646
    Map descMap = [:] // library marker kkossev.commonLib, line 647
    try { // library marker kkossev.commonLib, line 648
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 649
    } // library marker kkossev.commonLib, line 650
    catch (e1) { // library marker kkossev.commonLib, line 651
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 652
        // try alternative custom parsing // library marker kkossev.commonLib, line 653
        descMap = [:] // library marker kkossev.commonLib, line 654
        try { // library marker kkossev.commonLib, line 655
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 656
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 657
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 658
            } // library marker kkossev.commonLib, line 659
        } // library marker kkossev.commonLib, line 660
        catch (e2) { // library marker kkossev.commonLib, line 661
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 662
            return true // library marker kkossev.commonLib, line 663
        } // library marker kkossev.commonLib, line 664
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 665
    } // library marker kkossev.commonLib, line 666
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 667
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 668
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 669
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 670
        return false // library marker kkossev.commonLib, line 671
    } // library marker kkossev.commonLib, line 672
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 673
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 674
    // attribute report received // library marker kkossev.commonLib, line 675
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 676
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 677
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 678
    } // library marker kkossev.commonLib, line 679
    attrData.each { // library marker kkossev.commonLib, line 680
        if (it.status == '86') { // library marker kkossev.commonLib, line 681
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 682
        // TODO - skip parsing? // library marker kkossev.commonLib, line 683
        } // library marker kkossev.commonLib, line 684
        switch (it.cluster) { // library marker kkossev.commonLib, line 685
            case '0000' : // library marker kkossev.commonLib, line 686
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 687
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 688
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 689
                } // library marker kkossev.commonLib, line 690
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 691
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 692
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 693
                } // library marker kkossev.commonLib, line 694
                else { // library marker kkossev.commonLib, line 695
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 696
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 697
                } // library marker kkossev.commonLib, line 698
                break // library marker kkossev.commonLib, line 699
            default : // library marker kkossev.commonLib, line 700
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 701
                break // library marker kkossev.commonLib, line 702
        } // switch // library marker kkossev.commonLib, line 703
    } // for each attribute // library marker kkossev.commonLib, line 704
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 705
} // library marker kkossev.commonLib, line 706

String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 708
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 709
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 710
} // library marker kkossev.commonLib, line 711

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 713
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 714
} // library marker kkossev.commonLib, line 715

/* // library marker kkossev.commonLib, line 717
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 718
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 719
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 720
*/ // library marker kkossev.commonLib, line 721
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 722
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 723
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 724

// Tuya Commands // library marker kkossev.commonLib, line 726
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 727
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 728
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 729
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 730
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 731

// tuya DP type // library marker kkossev.commonLib, line 733
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 734
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 735
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 736
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 737
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 738
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 739

void syncTuyaDateTime() { // library marker kkossev.commonLib, line 741
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 742
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 743
    long offset = 0 // library marker kkossev.commonLib, line 744
    int offsetHours = 0 // library marker kkossev.commonLib, line 745
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 746
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 747
    try { // library marker kkossev.commonLib, line 748
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 749
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 750
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 751
    } catch (e) { // library marker kkossev.commonLib, line 752
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 753
    } // library marker kkossev.commonLib, line 754
    // // library marker kkossev.commonLib, line 755
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 756
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 757
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 758
} // library marker kkossev.commonLib, line 759

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 761
void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 762
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 763
        syncTuyaDateTime() // library marker kkossev.commonLib, line 764
    } // library marker kkossev.commonLib, line 765
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 766
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 767
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 768
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 769
        if (status != '00') { // library marker kkossev.commonLib, line 770
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 771
        } // library marker kkossev.commonLib, line 772
    } // library marker kkossev.commonLib, line 773
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 774
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 775
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 776
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 777
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 778
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 779
            return // library marker kkossev.commonLib, line 780
        } // library marker kkossev.commonLib, line 781
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 782
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 783
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 784
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 785
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 786
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 787
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 788
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 789
            } // library marker kkossev.commonLib, line 790
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 791
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 792
        } // library marker kkossev.commonLib, line 793
    } // library marker kkossev.commonLib, line 794
    else { // library marker kkossev.commonLib, line 795
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 796
    } // library marker kkossev.commonLib, line 797
} // library marker kkossev.commonLib, line 798

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 800
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 801
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 802
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 803
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 804
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 805
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 806
        } // library marker kkossev.commonLib, line 807
    } // library marker kkossev.commonLib, line 808
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 809
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 810
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 811
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 812
        } // library marker kkossev.commonLib, line 813
    } // library marker kkossev.commonLib, line 814
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 815
} // library marker kkossev.commonLib, line 816

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 818
    int retValue = 0 // library marker kkossev.commonLib, line 819
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 820
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 821
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 822
        int power = 1 // library marker kkossev.commonLib, line 823
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 824
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 825
            power = power * 256 // library marker kkossev.commonLib, line 826
        } // library marker kkossev.commonLib, line 827
    } // library marker kkossev.commonLib, line 828
    return retValue // library marker kkossev.commonLib, line 829
} // library marker kkossev.commonLib, line 830

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 832

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 834
    List<String> cmds = [] // library marker kkossev.commonLib, line 835
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 836
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 837
    //int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 838
    int tuyaCmd // library marker kkossev.commonLib, line 839
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified!\ // library marker kkossev.commonLib, line 840
    if (this.respondsTo('getDEVICE') && DEVICE?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 841
        tuyaCmd = DEVICE?.device?.tuyaCmd // library marker kkossev.commonLib, line 842
    } // library marker kkossev.commonLib, line 843
    else { // library marker kkossev.commonLib, line 844
        tuyaCmd = /*isFingerbot() ? 0x04 : */ tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 845
    } // library marker kkossev.commonLib, line 846
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 847
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 848
    return cmds // library marker kkossev.commonLib, line 849
} // library marker kkossev.commonLib, line 850

private getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 852

void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 854
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 855
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 856
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 857
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 858
} // library marker kkossev.commonLib, line 859

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 861
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 862

List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 864
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 865
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 866
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 867
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 868
} // library marker kkossev.commonLib, line 869

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 871
    List<String> cmds = [] // library marker kkossev.commonLib, line 872
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 873
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 874
    } // library marker kkossev.commonLib, line 875
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 876
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 877
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 878
        return // library marker kkossev.commonLib, line 879
    } // library marker kkossev.commonLib, line 880
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 881
} // library marker kkossev.commonLib, line 882

// Invoked from configure() // library marker kkossev.commonLib, line 884
List<String> initializeDevice() { // library marker kkossev.commonLib, line 885
    List<String> cmds = [] // library marker kkossev.commonLib, line 886
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 887
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 888
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 889
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 890
    } // library marker kkossev.commonLib, line 891
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 892
    return cmds // library marker kkossev.commonLib, line 893
} // library marker kkossev.commonLib, line 894

// Invoked from configure() // library marker kkossev.commonLib, line 896
List<String> configureDevice() { // library marker kkossev.commonLib, line 897
    List<String> cmds = [] // library marker kkossev.commonLib, line 898
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 899
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 900
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 901
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 902
    } // library marker kkossev.commonLib, line 903
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 904
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 905
    return cmds // library marker kkossev.commonLib, line 906
} // library marker kkossev.commonLib, line 907

/* // library marker kkossev.commonLib, line 909
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 910
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 911
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 912
*/ // library marker kkossev.commonLib, line 913

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 915
    List<String> cmds = [] // library marker kkossev.commonLib, line 916
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 917
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 918
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 919
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 920
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 921
            } // library marker kkossev.commonLib, line 922
        } // library marker kkossev.commonLib, line 923
    } // library marker kkossev.commonLib, line 924
    return cmds // library marker kkossev.commonLib, line 925
} // library marker kkossev.commonLib, line 926

void refresh() { // library marker kkossev.commonLib, line 928
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 929
    checkDriverVersion(state) // library marker kkossev.commonLib, line 930
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 931
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 932
        customCmds = customRefresh() // library marker kkossev.commonLib, line 933
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 934
    } // library marker kkossev.commonLib, line 935
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 936
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 937
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 938
    } // library marker kkossev.commonLib, line 939
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 940
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 941
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 942
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 943
    } // library marker kkossev.commonLib, line 944
    else { // library marker kkossev.commonLib, line 945
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 946
    } // library marker kkossev.commonLib, line 947
} // library marker kkossev.commonLib, line 948

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 950
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 951
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 952

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 954
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 955
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 956
        sendEvent(name: 'Status', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 957
    } // library marker kkossev.commonLib, line 958
    else { // library marker kkossev.commonLib, line 959
        logInfo "${info}" // library marker kkossev.commonLib, line 960
        sendEvent(name: 'Status', value: info, type: 'digital') // library marker kkossev.commonLib, line 961
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 962
    } // library marker kkossev.commonLib, line 963
} // library marker kkossev.commonLib, line 964

public void ping() { // library marker kkossev.commonLib, line 966
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 967
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 968
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 969
    if (isVirtual()) { runInMillis(10, virtualPong) } // library marker kkossev.commonLib, line 970
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) } // library marker kkossev.commonLib, line 971
    logDebug 'ping...' // library marker kkossev.commonLib, line 972
} // library marker kkossev.commonLib, line 973

def virtualPong() { // library marker kkossev.commonLib, line 975
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 976
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 977
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 978
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 979
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 980
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 981
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 982
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 983
        sendRttEvent() // library marker kkossev.commonLib, line 984
    } // library marker kkossev.commonLib, line 985
    else { // library marker kkossev.commonLib, line 986
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 987
    } // library marker kkossev.commonLib, line 988
    state.states['isPing'] = false // library marker kkossev.commonLib, line 989
    //unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 990
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 991
} // library marker kkossev.commonLib, line 992

void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 994
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 995
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 996
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 997
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 998
    if (value == null) { // library marker kkossev.commonLib, line 999
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1000
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1001
    } // library marker kkossev.commonLib, line 1002
    else { // library marker kkossev.commonLib, line 1003
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1004
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1005
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1006
    } // library marker kkossev.commonLib, line 1007
} // library marker kkossev.commonLib, line 1008

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1010
    if (cluster != null) { // library marker kkossev.commonLib, line 1011
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1012
    } // library marker kkossev.commonLib, line 1013
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1014
    return 'NULL' // library marker kkossev.commonLib, line 1015
} // library marker kkossev.commonLib, line 1016

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1018
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1019
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1020
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1021
} // library marker kkossev.commonLib, line 1022

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1024
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1025
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1026
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1027
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1028
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1029
    } // library marker kkossev.commonLib, line 1030
} // library marker kkossev.commonLib, line 1031

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1033
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1034
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1035
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1036
} // library marker kkossev.commonLib, line 1037

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1039
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1040
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1041
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1042
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1043
    } // library marker kkossev.commonLib, line 1044
    else { // library marker kkossev.commonLib, line 1045
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1046
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1047
    } // library marker kkossev.commonLib, line 1048
} // library marker kkossev.commonLib, line 1049

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1051
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1052
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1053
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1054
} // library marker kkossev.commonLib, line 1055

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1057
void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1058
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1059
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1060
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1061
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1062
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1063
    } // library marker kkossev.commonLib, line 1064
} // library marker kkossev.commonLib, line 1065

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1067
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1068
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1069
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1070
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1071
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1072
            logWarn 'not present!' // library marker kkossev.commonLib, line 1073
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1074
        } // library marker kkossev.commonLib, line 1075
    } // library marker kkossev.commonLib, line 1076
    else { // library marker kkossev.commonLib, line 1077
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1078
    } // library marker kkossev.commonLib, line 1079
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1080
} // library marker kkossev.commonLib, line 1081

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1083
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1084
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1085
    if (value == 'online') { // library marker kkossev.commonLib, line 1086
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1087
    } // library marker kkossev.commonLib, line 1088
    else { // library marker kkossev.commonLib, line 1089
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1090
    } // library marker kkossev.commonLib, line 1091
} // library marker kkossev.commonLib, line 1092

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1094
void updated() { // library marker kkossev.commonLib, line 1095
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1096
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1097
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1098
    unschedule() // library marker kkossev.commonLib, line 1099

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1101
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1102
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1103
    } // library marker kkossev.commonLib, line 1104
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1105
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1106
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1107
    } // library marker kkossev.commonLib, line 1108

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1110
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1111
        // schedule the periodic timer // library marker kkossev.commonLib, line 1112
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1113
        if (interval > 0) { // library marker kkossev.commonLib, line 1114
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1115
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1116
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1117
        } // library marker kkossev.commonLib, line 1118
    } // library marker kkossev.commonLib, line 1119
    else { // library marker kkossev.commonLib, line 1120
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1121
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1122
    } // library marker kkossev.commonLib, line 1123
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1124
        customUpdated() // library marker kkossev.commonLib, line 1125
    } // library marker kkossev.commonLib, line 1126

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1128
} // library marker kkossev.commonLib, line 1129

void logsOff() { // library marker kkossev.commonLib, line 1131
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1132
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1133
} // library marker kkossev.commonLib, line 1134
void traceOff() { // library marker kkossev.commonLib, line 1135
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1136
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1137
} // library marker kkossev.commonLib, line 1138

void configure(String command) { // library marker kkossev.commonLib, line 1140
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1141
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1142
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1143
        return // library marker kkossev.commonLib, line 1144
    } // library marker kkossev.commonLib, line 1145
    // // library marker kkossev.commonLib, line 1146
    String func // library marker kkossev.commonLib, line 1147
    try { // library marker kkossev.commonLib, line 1148
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1149
        "$func"() // library marker kkossev.commonLib, line 1150
    } // library marker kkossev.commonLib, line 1151
    catch (e) { // library marker kkossev.commonLib, line 1152
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1153
        return // library marker kkossev.commonLib, line 1154
    } // library marker kkossev.commonLib, line 1155
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1156
} // library marker kkossev.commonLib, line 1157

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1159
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1160
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1161
} // library marker kkossev.commonLib, line 1162

void loadAllDefaults() { // library marker kkossev.commonLib, line 1164
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1165
    deleteAllSettings() // library marker kkossev.commonLib, line 1166
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1167
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1168
    deleteAllStates() // library marker kkossev.commonLib, line 1169
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1170

    initialize() // library marker kkossev.commonLib, line 1172
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1173
    updated() // library marker kkossev.commonLib, line 1174
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1175
} // library marker kkossev.commonLib, line 1176

void configureNow() { // library marker kkossev.commonLib, line 1178
    configure() // library marker kkossev.commonLib, line 1179
} // library marker kkossev.commonLib, line 1180

/** // library marker kkossev.commonLib, line 1182
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1183
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1184
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1185
 */ // library marker kkossev.commonLib, line 1186
void configure() { // library marker kkossev.commonLib, line 1187
    List<String> cmds = [] // library marker kkossev.commonLib, line 1188
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1189
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1190
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1191
    if (isTuya()) { // library marker kkossev.commonLib, line 1192
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1193
    } // library marker kkossev.commonLib, line 1194
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1195
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1196
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1197
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1198
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1199
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1200
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1201
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1202
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1203
    } // library marker kkossev.commonLib, line 1204
    else { // library marker kkossev.commonLib, line 1205
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1206
    } // library marker kkossev.commonLib, line 1207
} // library marker kkossev.commonLib, line 1208

 // Invoked when the device is installed or when driver is installed ? // library marker kkossev.commonLib, line 1210
void installed() { // library marker kkossev.commonLib, line 1211
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1212
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1213
    // populate some default values for attributes // library marker kkossev.commonLib, line 1214
    sendEvent(name: 'healthStatus', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1215
    sendEvent(name: 'powerSource',  value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1216
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1217
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1218
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1219
} // library marker kkossev.commonLib, line 1220

void queryPowerSource() { // library marker kkossev.commonLib, line 1222
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1223
} // library marker kkossev.commonLib, line 1224

 // Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1226
void initialize() { // library marker kkossev.commonLib, line 1227
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1228
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1229
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1230
    updateTuyaVersion() // library marker kkossev.commonLib, line 1231
    updateAqaraVersion() // library marker kkossev.commonLib, line 1232
} // library marker kkossev.commonLib, line 1233

/* // library marker kkossev.commonLib, line 1235
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1236
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1237
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1238
*/ // library marker kkossev.commonLib, line 1239

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1241
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1242
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1243
} // library marker kkossev.commonLib, line 1244

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1246
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1247
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1248
} // library marker kkossev.commonLib, line 1249

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1251
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1252
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1253
} // library marker kkossev.commonLib, line 1254

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1256
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1257
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1258
        return // library marker kkossev.commonLib, line 1259
    } // library marker kkossev.commonLib, line 1260
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1261
    cmd.each { // library marker kkossev.commonLib, line 1262
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1263
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1264
            return // library marker kkossev.commonLib, line 1265
        } // library marker kkossev.commonLib, line 1266
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1267
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1268
    } // library marker kkossev.commonLib, line 1269
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1270
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1271
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1272
} // library marker kkossev.commonLib, line 1273

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1275

String getDeviceInfo() { // library marker kkossev.commonLib, line 1277
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1278
} // library marker kkossev.commonLib, line 1279

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1281
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1282
} // library marker kkossev.commonLib, line 1283

@CompileStatic // library marker kkossev.commonLib, line 1285
void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1286
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1287
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1288
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1289
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1290
        initializeVars(false) // library marker kkossev.commonLib, line 1291
        updateTuyaVersion() // library marker kkossev.commonLib, line 1292
        updateAqaraVersion() // library marker kkossev.commonLib, line 1293
    } // library marker kkossev.commonLib, line 1294
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1295
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1296
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1297
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1298
} // library marker kkossev.commonLib, line 1299

// credits @thebearmay // library marker kkossev.commonLib, line 1301
String getModel() { // library marker kkossev.commonLib, line 1302
    try { // library marker kkossev.commonLib, line 1303
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1304
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1305
    } catch (ignore) { // library marker kkossev.commonLib, line 1306
        try { // library marker kkossev.commonLib, line 1307
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1308
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1309
                return model // library marker kkossev.commonLib, line 1310
            } // library marker kkossev.commonLib, line 1311
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1312
            return '' // library marker kkossev.commonLib, line 1313
        } // library marker kkossev.commonLib, line 1314
    } // library marker kkossev.commonLib, line 1315
} // library marker kkossev.commonLib, line 1316

// credits @thebearmay // library marker kkossev.commonLib, line 1318
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1319
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1320
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1321
    String revision = tokens.last() // library marker kkossev.commonLib, line 1322
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1323
} // library marker kkossev.commonLib, line 1324

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1326
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1327
    unschedule() // library marker kkossev.commonLib, line 1328
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1329
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1330

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1332
} // library marker kkossev.commonLib, line 1333

void resetStatistics() { // library marker kkossev.commonLib, line 1335
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1336
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1337
} // library marker kkossev.commonLib, line 1338

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1340
void resetStats() { // library marker kkossev.commonLib, line 1341
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1342
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1343
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1344
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1345
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1346
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1347
} // library marker kkossev.commonLib, line 1348

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1350
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1351
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1352
        state.clear() // library marker kkossev.commonLib, line 1353
        unschedule() // library marker kkossev.commonLib, line 1354
        resetStats() // library marker kkossev.commonLib, line 1355
        if (deviceProfilesV3 != null && this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1356
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1357
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1358
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1359
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1360
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1361
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1362
    } // library marker kkossev.commonLib, line 1363

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1365
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1366
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1367
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1368
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1369

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1371
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1372
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1373
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1374
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1375
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1376
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1377

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1379

    // common libraries initialization // library marker kkossev.commonLib, line 1381
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1382
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1383
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1384
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1385
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1386

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1388
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1389
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1390
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1391

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1393
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1394
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1395
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1396
    if ( ep  != null) {  // library marker kkossev.commonLib, line 1397
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1398
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1399
    } // library marker kkossev.commonLib, line 1400
    else { // library marker kkossev.commonLib, line 1401
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1402
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1403
    } // library marker kkossev.commonLib, line 1404
} // library marker kkossev.commonLib, line 1405

// not used!? // library marker kkossev.commonLib, line 1407
void setDestinationEP() { // library marker kkossev.commonLib, line 1408
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1409
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1410
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1411
} // library marker kkossev.commonLib, line 1412

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1414
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1415
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1416
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1417

// _DEBUG mode only // library marker kkossev.commonLib, line 1419
void getAllProperties() { // library marker kkossev.commonLib, line 1420
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1421
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1422
} // library marker kkossev.commonLib, line 1423

// delete all Preferences // library marker kkossev.commonLib, line 1425
void deleteAllSettings() { // library marker kkossev.commonLib, line 1426
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1427
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1428
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1429
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1430
} // library marker kkossev.commonLib, line 1431

// delete all attributes // library marker kkossev.commonLib, line 1433
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1434
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1435
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1436
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1437
} // library marker kkossev.commonLib, line 1438

// delete all State Variables // library marker kkossev.commonLib, line 1440
void deleteAllStates() { // library marker kkossev.commonLib, line 1441
    String stateDeleted = '' // library marker kkossev.commonLib, line 1442
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1443
    state.clear() // library marker kkossev.commonLib, line 1444
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1445
} // library marker kkossev.commonLib, line 1446

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1448
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1449
} // library marker kkossev.commonLib, line 1450

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1452
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1453
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1454
} // library marker kkossev.commonLib, line 1455

void testParse(String par) { // library marker kkossev.commonLib, line 1457
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1458
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1459
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1460
    parse(par) // library marker kkossev.commonLib, line 1461
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1462
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1463
} // library marker kkossev.commonLib, line 1464

def testJob() { // library marker kkossev.commonLib, line 1466
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1467
} // library marker kkossev.commonLib, line 1468

/** // library marker kkossev.commonLib, line 1470
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1471
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1472
 */ // library marker kkossev.commonLib, line 1473
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1474
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1475
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1476
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1477
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1478
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1479
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1480
    String cron // library marker kkossev.commonLib, line 1481
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1482
    else { // library marker kkossev.commonLib, line 1483
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1484
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1485
    } // library marker kkossev.commonLib, line 1486
    return cron // library marker kkossev.commonLib, line 1487
} // library marker kkossev.commonLib, line 1488

// credits @thebearmay // library marker kkossev.commonLib, line 1490
String formatUptime() { // library marker kkossev.commonLib, line 1491
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1492
} // library marker kkossev.commonLib, line 1493

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1495
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1496
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1497
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1498
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1499
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1500
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1501
} // library marker kkossev.commonLib, line 1502

boolean isTuya() { // library marker kkossev.commonLib, line 1504
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1505
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1506
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1507
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1508
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1509
} // library marker kkossev.commonLib, line 1510

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1512
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1513
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1514
    if (application != null) { // library marker kkossev.commonLib, line 1515
        Integer ver // library marker kkossev.commonLib, line 1516
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1517
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1518
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1519
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1520
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1521
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1522
        } // library marker kkossev.commonLib, line 1523
    } // library marker kkossev.commonLib, line 1524
} // library marker kkossev.commonLib, line 1525

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1527

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1529
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1530
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1531
    if (application != null) { // library marker kkossev.commonLib, line 1532
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1533
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1534
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1535
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1536
        } // library marker kkossev.commonLib, line 1537
    } // library marker kkossev.commonLib, line 1538
} // library marker kkossev.commonLib, line 1539

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1541
    try { // library marker kkossev.commonLib, line 1542
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1543
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1544
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1545
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1546
    } catch (e) { // library marker kkossev.commonLib, line 1547
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1548
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1549
    } // library marker kkossev.commonLib, line 1550
} // library marker kkossev.commonLib, line 1551

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1553
    try { // library marker kkossev.commonLib, line 1554
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1555
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1556
        return date.getTime() // library marker kkossev.commonLib, line 1557
    } catch (e) { // library marker kkossev.commonLib, line 1558
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1559
        return now() // library marker kkossev.commonLib, line 1560
    } // library marker kkossev.commonLib, line 1561
} // library marker kkossev.commonLib, line 1562

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1564
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1565
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1566
    int seconds = time % 60 // library marker kkossev.commonLib, line 1567
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1568
} // library marker kkossev.commonLib, line 1569

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

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

// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NestedBlockDepth, NoDouble, NoFloat, NoWildcardImports, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.deviceProfileLib, line 1
library( // library marker kkossev.deviceProfileLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLib', namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy', documentationLink: '', // library marker kkossev.deviceProfileLib, line 4
    version: '3.3.0' // library marker kkossev.deviceProfileLib, line 5
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
 * ver. 3.3.0  2024-06-29 kkossev  - (dev. branch) empty preferences bug fix; zclWriteAttribute delay 50 ms; added advanced check in inputIt(); fixed 'Cannot get property 'rw' on null object' bug; fixed enum attributes first event numeric value bug; // library marker kkossev.deviceProfileLib, line 30
 * // library marker kkossev.deviceProfileLib, line 31
 *                                   TODO - remove the 2-in-1 patch ! // library marker kkossev.deviceProfileLib, line 32
 *                                   TODO - add defaults for profileId:'0104', endpointId:'01', inClusters, outClusters, in the deviceProfilesV3 map // library marker kkossev.deviceProfileLib, line 33
 *                                   TODO - add updateStateUnknownDPs (from the 4-in-1 driver) // library marker kkossev.deviceProfileLib, line 34
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 35
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 36
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off) // library marker kkossev.deviceProfileLib, line 37
 *                                   TODO: allow NULL parameters default values in the device profiles // library marker kkossev.deviceProfileLib, line 38
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 39
 * // library marker kkossev.deviceProfileLib, line 40
*/ // library marker kkossev.deviceProfileLib, line 41

static String deviceProfileLibVersion()   { '3.3.0' } // library marker kkossev.deviceProfileLib, line 43
static String deviceProfileLibStamp() { '2024/06/29 3:01 PM' } // library marker kkossev.deviceProfileLib, line 44
import groovy.json.* // library marker kkossev.deviceProfileLib, line 45
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 46
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 47
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 48
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 49

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 51

metadata { // library marker kkossev.deviceProfileLib, line 53
    // no capabilities // library marker kkossev.deviceProfileLib, line 54
    // no attributes // library marker kkossev.deviceProfileLib, line 55
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 56
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 57
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 58
    ] // library marker kkossev.deviceProfileLib, line 59
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 60
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 61
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 62
    ] // library marker kkossev.deviceProfileLib, line 63

    preferences { // library marker kkossev.deviceProfileLib, line 65
        if (device) { // library marker kkossev.deviceProfileLib, line 66
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 67
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 68
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 69
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 70
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 71
                        input inputMap // library marker kkossev.deviceProfileLib, line 72
                    } // library marker kkossev.deviceProfileLib, line 73
                } // library marker kkossev.deviceProfileLib, line 74
            } // library marker kkossev.deviceProfileLib, line 75
            //if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 76
                input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 77
            //} // library marker kkossev.deviceProfileLib, line 78
        } // library marker kkossev.deviceProfileLib, line 79
    } // library marker kkossev.deviceProfileLib, line 80
} // library marker kkossev.deviceProfileLib, line 81

private boolean is2in1() { return getDeviceProfile().contains('TS0601_2IN1') }    // patch removed 05/29/2024 // library marker kkossev.deviceProfileLib, line 83

public String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 85
public Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 86
public Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 87
//List<String> getDeviceProfilesMap()   { deviceProfilesV3 != null ? deviceProfilesV3.values().description as List<String> : deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 88

public List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLib, line 90
    if (deviceProfilesV3 == null) { // library marker kkossev.deviceProfileLib, line 91
        if (deviceProfilesV2 == null) { return [] } // library marker kkossev.deviceProfileLib, line 92
        return deviceProfilesV2.values().description as List<String> // library marker kkossev.deviceProfileLib, line 93
    } // library marker kkossev.deviceProfileLib, line 94
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLib, line 95
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 96
        if ((profileMap.device?.isDepricated ?: false) != true) { // library marker kkossev.deviceProfileLib, line 97
            activeProfiles.add(profileMap.description ?: '---') // library marker kkossev.deviceProfileLib, line 98
        } // library marker kkossev.deviceProfileLib, line 99
    } // library marker kkossev.deviceProfileLib, line 100
    return activeProfiles // library marker kkossev.deviceProfileLib, line 101
} // library marker kkossev.deviceProfileLib, line 102


// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 105

/** // library marker kkossev.deviceProfileLib, line 107
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 108
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 109
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 110
 */ // library marker kkossev.deviceProfileLib, line 111
String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 112
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 113
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 114
    else { return null } // library marker kkossev.deviceProfileLib, line 115
} // library marker kkossev.deviceProfileLib, line 116

/** // library marker kkossev.deviceProfileLib, line 118
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 119
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 120
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 121
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 122
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 123
 */ // library marker kkossev.deviceProfileLib, line 124
Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 125
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 126
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 127
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 128
    def preference // library marker kkossev.deviceProfileLib, line 129
    try { // library marker kkossev.deviceProfileLib, line 130
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 131
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 132
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 133
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 134
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 135
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 136
        } // library marker kkossev.deviceProfileLib, line 137
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 138
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 139
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 140
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 141
        } // library marker kkossev.deviceProfileLib, line 142
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 143
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 144
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 145
        } // library marker kkossev.deviceProfileLib, line 146
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 147
    } catch (e) { // library marker kkossev.deviceProfileLib, line 148
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 149
        return [:] // library marker kkossev.deviceProfileLib, line 150
    } // library marker kkossev.deviceProfileLib, line 151
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 152
    return foundMap // library marker kkossev.deviceProfileLib, line 153
} // library marker kkossev.deviceProfileLib, line 154

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 156
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 157
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 158
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 159
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 160
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 161
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 162
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 163
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 164
            return foundMap // library marker kkossev.deviceProfileLib, line 165
        } // library marker kkossev.deviceProfileLib, line 166
    } // library marker kkossev.deviceProfileLib, line 167
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 168
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 169
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 170
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 171
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 172
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 173
            return foundMap // library marker kkossev.deviceProfileLib, line 174
        } // library marker kkossev.deviceProfileLib, line 175
    } // library marker kkossev.deviceProfileLib, line 176
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 177
    return [:] // library marker kkossev.deviceProfileLib, line 178
} // library marker kkossev.deviceProfileLib, line 179

/** // library marker kkossev.deviceProfileLib, line 181
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 182
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 183
 */ // library marker kkossev.deviceProfileLib, line 184
void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 185
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 186
    Map preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 187
    if (preferences == null || preferences?.isEmpty()) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 188
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 189
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 190
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 191
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 192
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 193
            return // continue // library marker kkossev.deviceProfileLib, line 194
        } // library marker kkossev.deviceProfileLib, line 195
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 196
        if (parMap == null || parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 197
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 198
        if (parMap?.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 199
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 200
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 201
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 202
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

// called from customUpdated() method in the custom driver // library marker kkossev.deviceProfileLib, line 263
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 264
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 265
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 266
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 267
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 268
        return // library marker kkossev.deviceProfileLib, line 269
    } // library marker kkossev.deviceProfileLib, line 270
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 271
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 272
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 273
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 274
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 275
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 276
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 277
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 278
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 279
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 280
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 281
                // scale the value // library marker kkossev.deviceProfileLib, line 282
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 283
            } // library marker kkossev.deviceProfileLib, line 284
            if (preferenceValue != null) {  // library marker kkossev.deviceProfileLib, line 285
                setPar(name, preferenceValue.toString())  // library marker kkossev.deviceProfileLib, line 286
            } // library marker kkossev.deviceProfileLib, line 287
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLib, line 288
        } // library marker kkossev.deviceProfileLib, line 289
        else { logDebug "warning: couldn't find map for preference ${name}" ; return } // library marker kkossev.deviceProfileLib, line 290
    } // library marker kkossev.deviceProfileLib, line 291
    return // library marker kkossev.deviceProfileLib, line 292
} // library marker kkossev.deviceProfileLib, line 293

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 295
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 296
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 297
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 298
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 299
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 300
} // library marker kkossev.deviceProfileLib, line 301
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 302
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 303
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 304
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 305
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 306
} // library marker kkossev.deviceProfileLib, line 307
int invert(int val) { // library marker kkossev.deviceProfileLib, line 308
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 309
    else { return val } // library marker kkossev.deviceProfileLib, line 310
} // library marker kkossev.deviceProfileLib, line 311

// called from setPar and sendAttribite methods for non-Tuya DPs // library marker kkossev.deviceProfileLib, line 313
List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 314
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 315
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 316
    Map map = [:] // library marker kkossev.deviceProfileLib, line 317
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 318
    try { // library marker kkossev.deviceProfileLib, line 319
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 320
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 321
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 322
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 323
    } // library marker kkossev.deviceProfileLib, line 324
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLib, line 325
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 326
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 327
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLib, line 328
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 329
    } // library marker kkossev.deviceProfileLib, line 330
    if (map.mfgCode != null && map.mfgCode != '') { // library marker kkossev.deviceProfileLib, line 331
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 332
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mfgCode, delay = 50) // library marker kkossev.deviceProfileLib, line 333
    } // library marker kkossev.deviceProfileLib, line 334
    else { // library marker kkossev.deviceProfileLib, line 335
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 50) // library marker kkossev.deviceProfileLib, line 336
    } // library marker kkossev.deviceProfileLib, line 337
    return cmds // library marker kkossev.deviceProfileLib, line 338
} // library marker kkossev.deviceProfileLib, line 339

/** // library marker kkossev.deviceProfileLib, line 341
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 342
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 343
 * // library marker kkossev.deviceProfileLib, line 344
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 345
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 346
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 347
 */ // library marker kkossev.deviceProfileLib, line 348
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 349
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 350
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 351
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 352
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 353
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 354
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 355
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 356
        case 'number' : // library marker kkossev.deviceProfileLib, line 357
            // TODO - negative values ! // library marker kkossev.deviceProfileLib, line 358
            // TODO - better conversion to integer! // library marker kkossev.deviceProfileLib, line 359
            value = safeToInt(val, 0) // library marker kkossev.deviceProfileLib, line 360
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 361
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 362
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 363
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 364
            } // library marker kkossev.deviceProfileLib, line 365
            else { // library marker kkossev.deviceProfileLib, line 366
                scaledValue = value // library marker kkossev.deviceProfileLib, line 367
            } // library marker kkossev.deviceProfileLib, line 368
            break // library marker kkossev.deviceProfileLib, line 369

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 371
            value = safeToDouble(val, 0.0) // library marker kkossev.deviceProfileLib, line 372
            // scale the value // library marker kkossev.deviceProfileLib, line 373
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 374
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 375
            } // library marker kkossev.deviceProfileLib, line 376
            else { // library marker kkossev.deviceProfileLib, line 377
                scaledValue = value // library marker kkossev.deviceProfileLib, line 378
            } // library marker kkossev.deviceProfileLib, line 379
            break // library marker kkossev.deviceProfileLib, line 380

        case 'bool' : // library marker kkossev.deviceProfileLib, line 382
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 383
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 384
            else { // library marker kkossev.deviceProfileLib, line 385
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 386
                return null // library marker kkossev.deviceProfileLib, line 387
            } // library marker kkossev.deviceProfileLib, line 388
            break // library marker kkossev.deviceProfileLib, line 389
        case 'enum' : // library marker kkossev.deviceProfileLib, line 390
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 391
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 392
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 393
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 394
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 395
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 396
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 397
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 398
            } // library marker kkossev.deviceProfileLib, line 399
            else { // library marker kkossev.deviceProfileLib, line 400
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 401
            } // library marker kkossev.deviceProfileLib, line 402
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 403
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 404
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 405
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 406
                // find the key for the value // library marker kkossev.deviceProfileLib, line 407
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 408
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 409
                if (key == null) { // library marker kkossev.deviceProfileLib, line 410
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 411
                    return null // library marker kkossev.deviceProfileLib, line 412
                } // library marker kkossev.deviceProfileLib, line 413
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 414
            //return null // library marker kkossev.deviceProfileLib, line 415
            } // library marker kkossev.deviceProfileLib, line 416
            break // library marker kkossev.deviceProfileLib, line 417
        default : // library marker kkossev.deviceProfileLib, line 418
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 419
            return null // library marker kkossev.deviceProfileLib, line 420
    } // library marker kkossev.deviceProfileLib, line 421
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 422
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 423
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 424
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 425
        return null // library marker kkossev.deviceProfileLib, line 426
    } // library marker kkossev.deviceProfileLib, line 427
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 428
    return scaledValue // library marker kkossev.deviceProfileLib, line 429
} // library marker kkossev.deviceProfileLib, line 430

/** // library marker kkossev.deviceProfileLib, line 432
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 433
 * // library marker kkossev.deviceProfileLib, line 434
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 435
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 436
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 437
 */ // library marker kkossev.deviceProfileLib, line 438
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 439
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 440
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 441
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 442
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 443
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 444
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 445
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 446
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 447
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 448
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 449
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 450
    if (scaledValue == null) { logInfo "setPar: invalid parameter ${par} value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 451

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 453
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 454
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 455
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 456
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 457
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 458
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 459
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 460
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 461
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 462
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 463
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 464
            return true // library marker kkossev.deviceProfileLib, line 465
        } // library marker kkossev.deviceProfileLib, line 466
        else { // library marker kkossev.deviceProfileLib, line 467
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 468
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 469
        } // library marker kkossev.deviceProfileLib, line 470
    } // library marker kkossev.deviceProfileLib, line 471
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 472
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 473
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 474
        def valMiscType // library marker kkossev.deviceProfileLib, line 475
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 476
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 477
            // find the key for the value // library marker kkossev.deviceProfileLib, line 478
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 479
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 480
            if (key == null) { // library marker kkossev.deviceProfileLib, line 481
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 482
                return false // library marker kkossev.deviceProfileLib, line 483
            } // library marker kkossev.deviceProfileLib, line 484
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 485
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 486
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 487
        } // library marker kkossev.deviceProfileLib, line 488
        else { // library marker kkossev.deviceProfileLib, line 489
            valMiscType = val // library marker kkossev.deviceProfileLib, line 490
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 491
        } // library marker kkossev.deviceProfileLib, line 492
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 493
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 494
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 495
        return true // library marker kkossev.deviceProfileLib, line 496
    } // library marker kkossev.deviceProfileLib, line 497

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 499
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 500

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 502
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 503
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 504
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 505
        // Tuya DP // library marker kkossev.deviceProfileLib, line 506
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 507
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 508
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 509
            return false // library marker kkossev.deviceProfileLib, line 510
        } // library marker kkossev.deviceProfileLib, line 511
        else { // library marker kkossev.deviceProfileLib, line 512
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 513
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 514
            return false // library marker kkossev.deviceProfileLib, line 515
        } // library marker kkossev.deviceProfileLib, line 516
    } // library marker kkossev.deviceProfileLib, line 517
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 518
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 519
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 520
        int signedIntScaled = convertSignedInts(scaledValue, dpMap) // library marker kkossev.deviceProfileLib, line 521
        cmds = zclWriteAttribute(dpMap, signedIntScaled) // library marker kkossev.deviceProfileLib, line 522
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 523
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 524
            return false // library marker kkossev.deviceProfileLib, line 525
        } // library marker kkossev.deviceProfileLib, line 526
    } // library marker kkossev.deviceProfileLib, line 527
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 528
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 529
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 530
    return true // library marker kkossev.deviceProfileLib, line 531
} // library marker kkossev.deviceProfileLib, line 532

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 534
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 535
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 536
public List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 537
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 538
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 539
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 540
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 541
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 542
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 543
        return [] // library marker kkossev.deviceProfileLib, line 544
    } // library marker kkossev.deviceProfileLib, line 545
    String dpType // library marker kkossev.deviceProfileLib, line 546
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 547
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 548
    } // library marker kkossev.deviceProfileLib, line 549
    else { // library marker kkossev.deviceProfileLib, line 550
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 551
    } // library marker kkossev.deviceProfileLib, line 552
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 553
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 554
        return [] // library marker kkossev.deviceProfileLib, line 555
    } // library marker kkossev.deviceProfileLib, line 556
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 557
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 558
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 559
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 560
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 561
    } // library marker kkossev.deviceProfileLib, line 562
    else { // library marker kkossev.deviceProfileLib, line 563
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 564
    } // library marker kkossev.deviceProfileLib, line 565
    return cmds // library marker kkossev.deviceProfileLib, line 566
} // library marker kkossev.deviceProfileLib, line 567

int convertSignedInts(int val, Map dpMap) { // library marker kkossev.deviceProfileLib, line 569
    if (dpMap.dt == '0x28') { // library marker kkossev.deviceProfileLib, line 570
        if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 571
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 572
    } // library marker kkossev.deviceProfileLib, line 573
    else if (dpMap.dt == '0x29') { // library marker kkossev.deviceProfileLib, line 574
        if (val > 32767) { return (val as int) - 65536 } // library marker kkossev.deviceProfileLib, line 575
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 576
    } // library marker kkossev.deviceProfileLib, line 577
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 578
} // library marker kkossev.deviceProfileLib, line 579

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 581
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 582
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 583
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 584
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 585
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "DEVICE.preferences is empty!" ; return false } // library marker kkossev.deviceProfileLib, line 586

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 588
    l//log.trace "sendAttribute: dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 589
    if (dpMap == null || dpMap?.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 590
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 591
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 592
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 593
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 594
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 595
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 596
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 597
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 598
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 599
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 600
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 601
        try { // library marker kkossev.deviceProfileLib, line 602
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 603
        } // library marker kkossev.deviceProfileLib, line 604
        catch (e) { // library marker kkossev.deviceProfileLib, line 605
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 606
            return false // library marker kkossev.deviceProfileLib, line 607
        } // library marker kkossev.deviceProfileLib, line 608
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 609
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 610
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 611
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 612
            return true // library marker kkossev.deviceProfileLib, line 613
        } // library marker kkossev.deviceProfileLib, line 614
        else { // library marker kkossev.deviceProfileLib, line 615
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 616
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 617
        } // library marker kkossev.deviceProfileLib, line 618
    } // library marker kkossev.deviceProfileLib, line 619
    else { // library marker kkossev.deviceProfileLib, line 620
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 621
    } // library marker kkossev.deviceProfileLib, line 622
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 623
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 624
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 625
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 626
        // patch !! // library marker kkossev.deviceProfileLib, line 627
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 628
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 629
        } // library marker kkossev.deviceProfileLib, line 630
        else { // library marker kkossev.deviceProfileLib, line 631
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 632
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 633
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 634
        } // library marker kkossev.deviceProfileLib, line 635
        return true // library marker kkossev.deviceProfileLib, line 636
    } // library marker kkossev.deviceProfileLib, line 637
    else { // library marker kkossev.deviceProfileLib, line 638
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 639
    } // library marker kkossev.deviceProfileLib, line 640
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 641
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 642
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 643
    try { // library marker kkossev.deviceProfileLib, line 644
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 645
    } // library marker kkossev.deviceProfileLib, line 646
    catch (e) { // library marker kkossev.deviceProfileLib, line 647
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 648
        return false // library marker kkossev.deviceProfileLib, line 649
    } // library marker kkossev.deviceProfileLib, line 650
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 651
        // Tuya DP // library marker kkossev.deviceProfileLib, line 652
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 653
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 654
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 655
            return false // library marker kkossev.deviceProfileLib, line 656
        } // library marker kkossev.deviceProfileLib, line 657
        else { // library marker kkossev.deviceProfileLib, line 658
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 659
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 660
            return true // library marker kkossev.deviceProfileLib, line 661
        } // library marker kkossev.deviceProfileLib, line 662
    } // library marker kkossev.deviceProfileLib, line 663
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 664
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 665
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 666
    } // library marker kkossev.deviceProfileLib, line 667
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 668
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 669
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 670
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 671
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 672
            return false // library marker kkossev.deviceProfileLib, line 673
        } // library marker kkossev.deviceProfileLib, line 674
    } // library marker kkossev.deviceProfileLib, line 675
    else { // library marker kkossev.deviceProfileLib, line 676
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 677
        return false // library marker kkossev.deviceProfileLib, line 678
    } // library marker kkossev.deviceProfileLib, line 679
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 680
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 681
    return true // library marker kkossev.deviceProfileLib, line 682
} // library marker kkossev.deviceProfileLib, line 683

/** // library marker kkossev.deviceProfileLib, line 685
 * SENDS a list of Zigbee commands to be sent to the device. // library marker kkossev.deviceProfileLib, line 686
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 687
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 688
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 689
 */ // library marker kkossev.deviceProfileLib, line 690
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 691
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 692
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 693
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 694
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 695
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 696
    if (supportedCommandsMap == null || supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 697
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 698
        return false // library marker kkossev.deviceProfileLib, line 699
    } // library marker kkossev.deviceProfileLib, line 700
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 701
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 702
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 703
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 704
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 705
        return false // library marker kkossev.deviceProfileLib, line 706
    } // library marker kkossev.deviceProfileLib, line 707
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 708
    def func, funcResult // library marker kkossev.deviceProfileLib, line 709
    try { // library marker kkossev.deviceProfileLib, line 710
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 711
        if (val != null && val != '') { // library marker kkossev.deviceProfileLib, line 712
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 713
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 714
        } // library marker kkossev.deviceProfileLib, line 715
        else { // library marker kkossev.deviceProfileLib, line 716
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 717
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 718
        } // library marker kkossev.deviceProfileLib, line 719
    }  // library marker kkossev.deviceProfileLib, line 720
    catch (e) { // library marker kkossev.deviceProfileLib, line 721
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 722
        return false // library marker kkossev.deviceProfileLib, line 723
    }  // library marker kkossev.deviceProfileLib, line 724
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 725
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 726
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 727
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 728
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 729
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 730
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 731
        } // library marker kkossev.deviceProfileLib, line 732
    } // library marker kkossev.deviceProfileLib, line 733
    else if (funcResult == null) { // library marker kkossev.deviceProfileLib, line 734
        return false // library marker kkossev.deviceProfileLib, line 735
    } // library marker kkossev.deviceProfileLib, line 736
     else { // library marker kkossev.deviceProfileLib, line 737
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 738
        return false // library marker kkossev.deviceProfileLib, line 739
    } // library marker kkossev.deviceProfileLib, line 740
    return true // library marker kkossev.deviceProfileLib, line 741
} // library marker kkossev.deviceProfileLib, line 742

/** // library marker kkossev.deviceProfileLib, line 744
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 745
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 746
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 747
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 748
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 749
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 750
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 751
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 752
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 753
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 754
 */ // library marker kkossev.deviceProfileLib, line 755
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 756
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 757
    Map input = [:] // library marker kkossev.deviceProfileLib, line 758
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 759
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 760
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 761
    def preference // library marker kkossev.deviceProfileLib, line 762
    try { preference = DEVICE?.preferences["$param"] } // library marker kkossev.deviceProfileLib, line 763
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 764
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 765
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } } // library marker kkossev.deviceProfileLib, line 766
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 767
    // TODO - check if this is neccessary? isTuyaDP is not defined! // library marker kkossev.deviceProfileLib, line 768
    try { isTuyaDP = preference.isNumber() } // library marker kkossev.deviceProfileLib, line 769
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 770
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 771
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 772
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 773
    if (foundMap == null || foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 774
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 775
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) { // library marker kkossev.deviceProfileLib, line 776
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" } // library marker kkossev.deviceProfileLib, line 777
        return [:] // library marker kkossev.deviceProfileLib, line 778
    } // library marker kkossev.deviceProfileLib, line 779
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 780
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 781
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 782
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 783
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 784
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 785
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 786
        } // library marker kkossev.deviceProfileLib, line 787
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 788
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 789
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 790
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 791
            } // library marker kkossev.deviceProfileLib, line 792
        } // library marker kkossev.deviceProfileLib, line 793
    } // library marker kkossev.deviceProfileLib, line 794
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 795
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 796
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 797
    }/* // library marker kkossev.deviceProfileLib, line 798
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 799
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 800
    }*/ // library marker kkossev.deviceProfileLib, line 801
    else { // library marker kkossev.deviceProfileLib, line 802
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 803
        return [:] // library marker kkossev.deviceProfileLib, line 804
    } // library marker kkossev.deviceProfileLib, line 805
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 806
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 807
    } // library marker kkossev.deviceProfileLib, line 808
    return input // library marker kkossev.deviceProfileLib, line 809
} // library marker kkossev.deviceProfileLib, line 810

/** // library marker kkossev.deviceProfileLib, line 812
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 813
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 814
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 815
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 816
 */ // library marker kkossev.deviceProfileLib, line 817
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 818
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 819
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 820
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 821
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 822
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 823
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 824
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 825
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 826
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 827
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 828
            } // library marker kkossev.deviceProfileLib, line 829
        } // library marker kkossev.deviceProfileLib, line 830
    } // library marker kkossev.deviceProfileLib, line 831
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 832
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 833
    } // library marker kkossev.deviceProfileLib, line 834
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 835
} // library marker kkossev.deviceProfileLib, line 836

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 838
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 839
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 840
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 841
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 842
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 843
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 844
    } // library marker kkossev.deviceProfileLib, line 845
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 846
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 847
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 848
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 849
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 850
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 851
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 852
    } else { // library marker kkossev.deviceProfileLib, line 853
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 854
    } // library marker kkossev.deviceProfileLib, line 855
} // library marker kkossev.deviceProfileLib, line 856

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLib, line 858
List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLib, line 859
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLib, line 860
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 861
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLib, line 862
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 863
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 864
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 865
            if (k != null) { // library marker kkossev.deviceProfileLib, line 866
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 867
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 868
                if (map != null) { // library marker kkossev.deviceProfileLib, line 869
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 870
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 871
                } // library marker kkossev.deviceProfileLib, line 872
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 873
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 874
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 875
                } // library marker kkossev.deviceProfileLib, line 876
            } // library marker kkossev.deviceProfileLib, line 877
        } // library marker kkossev.deviceProfileLib, line 878
    } // library marker kkossev.deviceProfileLib, line 879
    return cmds // library marker kkossev.deviceProfileLib, line 880
} // library marker kkossev.deviceProfileLib, line 881

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 883
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 884
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 885
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 886
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 887
    return cmds // library marker kkossev.deviceProfileLib, line 888
} // library marker kkossev.deviceProfileLib, line 889

// TODO ! // library marker kkossev.deviceProfileLib, line 891
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 892
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 893
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 894
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 895
    return cmds // library marker kkossev.deviceProfileLib, line 896
} // library marker kkossev.deviceProfileLib, line 897

// TODO // library marker kkossev.deviceProfileLib, line 899
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 900
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 901
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 902
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 903
    return cmds // library marker kkossev.deviceProfileLib, line 904
} // library marker kkossev.deviceProfileLib, line 905

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 907
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 908
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 909
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 910
    } // library marker kkossev.deviceProfileLib, line 911
} // library marker kkossev.deviceProfileLib, line 912

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 914
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 915
} // library marker kkossev.deviceProfileLib, line 916

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 918

// // library marker kkossev.deviceProfileLib, line 920
// called from parse() // library marker kkossev.deviceProfileLib, line 921
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 922
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 923
// // library marker kkossev.deviceProfileLib, line 924
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 925
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 926
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 927
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 928
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 929
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 930
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 931
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 932
} // library marker kkossev.deviceProfileLib, line 933

// // library marker kkossev.deviceProfileLib, line 935
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 936
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 937
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 938
// // library marker kkossev.deviceProfileLib, line 939
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 940
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 941
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 942
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 943
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 944
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 945
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 946
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 947
} // library marker kkossev.deviceProfileLib, line 948

// all DPs are spammy - sent periodically! // library marker kkossev.deviceProfileLib, line 950
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 951
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 952
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 953
    return isSpammy // library marker kkossev.deviceProfileLib, line 954
} // library marker kkossev.deviceProfileLib, line 955

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 957
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 958
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 959
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 960
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 961
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 962
    } // library marker kkossev.deviceProfileLib, line 963
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 964
} // library marker kkossev.deviceProfileLib, line 965

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 967
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 968
    boolean isEqual // library marker kkossev.deviceProfileLib, line 969
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 970
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 971
    } // library marker kkossev.deviceProfileLib, line 972
    else { // library marker kkossev.deviceProfileLib, line 973
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 974
    } // library marker kkossev.deviceProfileLib, line 975
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 976
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 977
} // library marker kkossev.deviceProfileLib, line 978

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 980
    Double convertedValue // library marker kkossev.deviceProfileLib, line 981
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 982
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 983
    } // library marker kkossev.deviceProfileLib, line 984
    else { // library marker kkossev.deviceProfileLib, line 985
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 986
    } // library marker kkossev.deviceProfileLib, line 987
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 988
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 989
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 990
} // library marker kkossev.deviceProfileLib, line 991

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 993
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 994
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 995
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 996
    def convertedValue // library marker kkossev.deviceProfileLib, line 997
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 998
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 999
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1000
    } // library marker kkossev.deviceProfileLib, line 1001
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1002
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1003
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1004
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1005
            isEqual = false // library marker kkossev.deviceProfileLib, line 1006
        } // library marker kkossev.deviceProfileLib, line 1007
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1008
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1009
        } // library marker kkossev.deviceProfileLib, line 1010
    } // library marker kkossev.deviceProfileLib, line 1011
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1012
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1013
} // library marker kkossev.deviceProfileLib, line 1014

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1016
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1017
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1018
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1019
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1020
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1021
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1022
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1023
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1024
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1025
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1026
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1027
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1028
            break // library marker kkossev.deviceProfileLib, line 1029
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1030
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1031
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1032
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1033
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1034
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1035
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1036
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1037
            } // library marker kkossev.deviceProfileLib, line 1038
            else { // library marker kkossev.deviceProfileLib, line 1039
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1040
            } // library marker kkossev.deviceProfileLib, line 1041
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1042
            break // library marker kkossev.deviceProfileLib, line 1043
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1044
        case 'number' : // library marker kkossev.deviceProfileLib, line 1045
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1046
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1047
            break // library marker kkossev.deviceProfileLib, line 1048
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1049
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1050
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1051
            break // library marker kkossev.deviceProfileLib, line 1052
        default : // library marker kkossev.deviceProfileLib, line 1053
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1054
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1055
    } // library marker kkossev.deviceProfileLib, line 1056
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1057
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1058
    } // library marker kkossev.deviceProfileLib, line 1059
    // // library marker kkossev.deviceProfileLib, line 1060
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1061
} // library marker kkossev.deviceProfileLib, line 1062

// // library marker kkossev.deviceProfileLib, line 1064
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1065
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1066
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1067
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1068
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1069
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1070
// // library marker kkossev.deviceProfileLib, line 1071
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1072
// // library marker kkossev.deviceProfileLib, line 1073
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1074
// // library marker kkossev.deviceProfileLib, line 1075
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1076
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1077
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1078
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1079
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1080
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1081
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1082
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1083
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1084
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1085
            break // library marker kkossev.deviceProfileLib, line 1086
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1087
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1088
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1089
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1090
            logTrace "latestEvent is ${latestEvent} dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1091
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1092
            if (dataType == null || dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1093
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1094
            } // library marker kkossev.deviceProfileLib, line 1095
            else { // library marker kkossev.deviceProfileLib, line 1096
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1097
            } // library marker kkossev.deviceProfileLib, line 1098
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1099
            break // library marker kkossev.deviceProfileLib, line 1100
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1101
        case 'number' : // library marker kkossev.deviceProfileLib, line 1102
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1103
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1104
            break // library marker kkossev.deviceProfileLib, line 1105
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1106
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1107
            break // library marker kkossev.deviceProfileLib, line 1108
        default : // library marker kkossev.deviceProfileLib, line 1109
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1110
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1111
    } // library marker kkossev.deviceProfileLib, line 1112
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1113
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1114
} // library marker kkossev.deviceProfileLib, line 1115

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1117
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1118
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1119
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1120
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1121
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1122
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1123
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1124
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1125
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1126
    } // library marker kkossev.deviceProfileLib, line 1127
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1128
    try { // library marker kkossev.deviceProfileLib, line 1129
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1130
    } // library marker kkossev.deviceProfileLib, line 1131
    catch (e) { // library marker kkossev.deviceProfileLib, line 1132
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1133
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1134
    } // library marker kkossev.deviceProfileLib, line 1135
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1136
    return fncmd // library marker kkossev.deviceProfileLib, line 1137
} // library marker kkossev.deviceProfileLib, line 1138

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1140
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1141
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1142
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1143
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1144
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1145
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1146

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1148
    if (attribMap == null || attribMap?.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1149

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1151
    int value // library marker kkossev.deviceProfileLib, line 1152
    try { // library marker kkossev.deviceProfileLib, line 1153
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1154
    } // library marker kkossev.deviceProfileLib, line 1155
    catch (e) { // library marker kkossev.deviceProfileLib, line 1156
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1157
        return false // library marker kkossev.deviceProfileLib, line 1158
    } // library marker kkossev.deviceProfileLib, line 1159
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1160
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1161
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1162
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1163
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1164
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1165
        return false // library marker kkossev.deviceProfileLib, line 1166
    } // library marker kkossev.deviceProfileLib, line 1167
    value = convertSignedInts(value, foundItem) // library marker kkossev.deviceProfileLib, line 1168
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1169
} // library marker kkossev.deviceProfileLib, line 1170

/** // library marker kkossev.deviceProfileLib, line 1172
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1173
 * // library marker kkossev.deviceProfileLib, line 1174
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1175
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1176
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1177
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1178
 * // library marker kkossev.deviceProfileLib, line 1179
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1180
 */ // library marker kkossev.deviceProfileLib, line 1181
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1182
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1183
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1184
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1185
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1186

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1188
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1189

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1191
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1192
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1193
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1194
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1195
        return false // library marker kkossev.deviceProfileLib, line 1196
    } // library marker kkossev.deviceProfileLib, line 1197
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1198
} // library marker kkossev.deviceProfileLib, line 1199

/* // library marker kkossev.deviceProfileLib, line 1201
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1202
 */ // library marker kkossev.deviceProfileLib, line 1203
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1204
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1205
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1206
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1207
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1208
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1209
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1210
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1211
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1212
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1213
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1214
        } // library marker kkossev.deviceProfileLib, line 1215
    } // library marker kkossev.deviceProfileLib, line 1216
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1217

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1219
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1220
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1221
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1222
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1223
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences?.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1224
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1225
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1226
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1227
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1228
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1229
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1230
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1231
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1232
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1233
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1234
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1235

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1237
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1238
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1239
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1240
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1241
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1242
            if (settings.logEnable) { logInfo "${descText }" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1243
        } // library marker kkossev.deviceProfileLib, line 1244
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1245
    } // library marker kkossev.deviceProfileLib, line 1246

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1248
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1249
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1250
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1251
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1252
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1253
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1254
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1255
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1256
            } // library marker kkossev.deviceProfileLib, line 1257
        } // library marker kkossev.deviceProfileLib, line 1258
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1259
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1260
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1261
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1262
            } // library marker kkossev.deviceProfileLib, line 1263
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1264
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1265
            try { // library marker kkossev.deviceProfileLib, line 1266
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1267
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1268
            } // library marker kkossev.deviceProfileLib, line 1269
            catch (e) { // library marker kkossev.deviceProfileLib, line 1270
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1271
            } // library marker kkossev.deviceProfileLib, line 1272
        } // library marker kkossev.deviceProfileLib, line 1273
    } // library marker kkossev.deviceProfileLib, line 1274
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1275
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1276
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1277
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1278
    } // library marker kkossev.deviceProfileLib, line 1279

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1281
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1282
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1283
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1284
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1285
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1286
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1287
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1288
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1289
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1290
            } // library marker kkossev.deviceProfileLib, line 1291
            if (foundItem.processDuplicated == true) { // library marker kkossev.deviceProfileLib, line 1292
                logDebug 'processDuplicated=true -> continue' // library marker kkossev.deviceProfileLib, line 1293
            } // library marker kkossev.deviceProfileLib, line 1294

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1296
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1297
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1298
            // continue ... // library marker kkossev.deviceProfileLib, line 1299
            } // library marker kkossev.deviceProfileLib, line 1300

            else { // library marker kkossev.deviceProfileLib, line 1302
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1303
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1304
                } // library marker kkossev.deviceProfileLib, line 1305
                else { // library marker kkossev.deviceProfileLib, line 1306
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLib, line 1307
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1308
                } // library marker kkossev.deviceProfileLib, line 1309
            } // library marker kkossev.deviceProfileLib, line 1310
        } // library marker kkossev.deviceProfileLib, line 1311

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1313
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1314
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1315
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1316
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1317
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1318
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1319
        } // library marker kkossev.deviceProfileLib, line 1320
        else { // library marker kkossev.deviceProfileLib, line 1321
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1322
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1323
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1324
                logTrace "event ${name} sent w/ valueScaled ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1325
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1326
            } // library marker kkossev.deviceProfileLib, line 1327
        } // library marker kkossev.deviceProfileLib, line 1328
    } // library marker kkossev.deviceProfileLib, line 1329
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1330
} // library marker kkossev.deviceProfileLib, line 1331

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1333
public boolean validateAndFixPreferences(String debugStr) { return validateAndFixPreferences(debugStr.toBoolean() as boolean) } // library marker kkossev.deviceProfileLib, line 1334
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
