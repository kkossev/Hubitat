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
 * ver. 3.3.0  2024-06-25 kkossev  - (dev. branch) new driver for Zigbee Shade Controller : TUYA_TS130F_MODULE
 * ver. 3.3.1  2024-06-29 kkossev  - (dev. branch) 
 *
 *                                   TODO: Add Advanced parametres - openCode, closeCode, stopCode, continueCode
 *                                   TODO: make different preferences  - softwareInvertDirection, hardwareInvertDirection
 */

static String version() { '3.3.1' }
static String timeStamp() { '2024/06/30 5:08 PM' }

@Field static final Boolean _DEBUG = true
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true

import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

#include kkossev.commonLib
#include kkossev.batteryLib
#include kkossev.deviceProfileLib

deviceType = 'Curtain'
@Field static final String DEVICE_TYPE = 'Curtain'

metadata {
    definition(
        name: 'Zigbee Window Shade',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee%20Window%20Shade/Zigbee_Window_Shade_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        capability 'WindowShade'    // Attributes: position - NUMBER, unit:% windowShade - ENUM ["opening", "partially open", "closed", "open", "closing", "unknown"]
                                    // Commands: close(); open(); setPosition(position) position required (NUMBER) - Shade position (0 to 100);
                                    //           startPositionChange(direction): direction required (ENUM) - Direction for position change request ["open", "close"]
                                    //           stopPositionChange()

        attribute 'targetPosition', 'number'            // ZemiSmart M1 is updating this attribute, not the position :(
        attribute 'operationalStatus', 'number'         // 'enum', ['unknown', 'open', 'closed', 'opening', 'closing', 'partially open']

        attribute 'positionState', 'enum', ['up/open', 'stop', 'down/close']    // TUYA_TS130F_MODULE
        attribute 'upDownConfirm', 'enum', ['false', 'true']                // TUYA_TS130F_MODULE
        attribute 'controlBack', 'enum', ['false', 'true']                  // TUYA_TS130F_MODULE
        attribute 'scheduleTime', 'number'                                  // TUYA_TS130F_MODULE
        attribute 'clickControl', 'enum', ['up', 'down']                    // Zemismart ZM85EL_1x
        attribute 'bestPosition', 'number'                                  // Zemismart ZM85EL_1x
        attribute 'workState', 'enum', ['moving', 'idle']                  // Zemismart ZM85EL_1x
        attribute 'mode', 'enum', ['morning', 'night']                      // Zemismart ZM85EL_1x
        attribute 'motorDirection', 'enum', ['forward', 'backward', 'left', 'right']         // Zemismart ZM85EL_1x, TUYA_SLIDING_WINDOW_PUSHER
        attribute 'situationSet', 'enum', ['fully_open', 'fully_close']     // Zemismart ZM85EL_1x
        attribute 'fault', 'enum', ['clear', 'motor_fault']                 // Zemismart ZM85EL_1x, TUYA_SLIDING_WINDOW_PUSHER
        

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
            input name: "helpInfo", type: "hidden", title: fmtHelpInfo("Community Link")
      //  }
        // the rest of the preferences are inputed from the deviceProfile maps in the deviceProfileLib
        //section {
            input name: 'maxTravelTime', type: 'number', title: '<b>Maximum travel time</b>', description: 'The maximum time to fully open or close (Seconds).', required: false, defaultValue: DEFAULT_MAX_TRAVEL_TIME
            input name: 'deltaPosition', type: 'number', title: '<b>Position delta</b>', description: 'The maximum error step reaching the target position.', required: false, defaultValue: DEFAULT_POSITION_DELTA
            if (settings?.advancedOptions == true) {
                input name: 'commandOpenCode', type: 'number', title: '<b>Open Command Code</b>', description: 'The standard Open command code is 0.<br>Don\'t change these codes, except you are sure what you are doing.', required: true, defaultValue: COMMAND_OPEN
                input name: 'commandCloseCode', type: 'number', title: '<b>Close Command Code</b>', description: 'The standard Close command code is 1.<br>Zemismart OEMs are making fun by changing these numbers.', required: true, defaultValue: COMMAND_CLOSE
                input name: 'commandStopCode', type: 'number', title: '<b>Stop Command Code</b>', description: 'The standard Stop (Pause) command code is 2.<br>Don\'t do like Zemismart OEM\'s!', required: true, defaultValue: COMMAND_PAUSE
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

/*
String getModelX()  { return settings?.forcedTS130F == true ? 'TS130F' : device.getDataValue('model') }
boolean isTS130F() { return getModelX() == 'TS130F' }
boolean isZM85EL() { return device.getDataValue('manufacturer') in ['_TZE200_cf1sl3tj'] }
boolean isAM43()   { return device.getDataValue('manufacturer') in ['_TZE200_zah67ekd'] }
boolean isAM02()   { return device.getDataValue('manufacturer') in ['_TZE200_iossyxra', '_TZE200_cxu0jkjk'] }
*/

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
                [dp:4,   name:'mode',                type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'morning', 1:'night'] ,  title:'<bMode</b>',description:'mode'],
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

    'TUYA_SLIDING_WINDOW_PUSHER'   : [
            description   : 'Tuya Sliding Window Pusher',   //
            device        : [type: 'COVERING', powerSource: 'battery', isSleepy:false, isTuyaEF00:true],
            capabilities  : ['Battery': true],
            preferences   : ['motorDirection':'109'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500,EF00", outClusters:"000A,0019", model:"TS0601", manufacturer:"_TZ3210_5rta89nj", controllerType: "ZGB"]
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh'],
            tuyaDPs:        [
                [dp:4,   name:'battery',             type:'number',  rw: 'ro', min:0,   max:100,   defVal:100,  scale:1,   unit:'%',  description:'Battery percentage'],
                [dp:102, name:'control',             type:'enum',    rw: 'rw', min:0,   max:2 ,    defVal:'0',  scale:1,   map:[0:'open', 1:'close', 2:'stop'] , description:'Shade control'],        // control
                [dp:103, name:'alarmMode',           type:'enum',    rw: 'ro', map:[0:'true', 1:'false'] ,      description:'Alarm Mode'],                          // alarm_mode security_mode
                [dp:104, name:'position',            type:'number',  rw: 'ro', min:0,   max:100,   defVal:0,    description:'Curtain current position'],            // coverPosition  ratio of opening
                [dp:105, name:'chargeState',         type:'enum',    rw: 'ro', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'true', 1:'false'],  description:'Charge state'],
                [dp:106, name:'manualMode',          type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'true', 1:'false'],  description:'Manual mode'],
                [dp:107, name:'fault',               type:'enum',    rw: 'ro', map:[0:'motor_fault', 1:'clear'] ,  description:'Fault code'],                       // alarm_mode
                [dp:108, name:'calibration',         type:'number',  rw: 'ro', min:0,   max:100,   defVal:0,    scale:1,   unit:'%',  description:'Calibration'],   // motor_calibration
                [dp:109, name:'motorDirection', dt:'04',  type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'left', 1:'right'] , title:'<b>Motor Direction</b>',  description:'Motor direction'],  //control_back_mode installation_type
                [dp:110, name:'slowStop',            type:'enum',    rw: 'ro', map:[0:'true', 1:'false'] ,      description:'Slow stop'],                           // slow_stop
                [dp:111, name:'solarEnergyCurrent',  type:'number',  rw: 'ro', min:0,   max:99999, defVal:0,    scale:1,   unit:'%',  description:'Solar Energy Current'],   // solar_energy_current
                [dp:112, name:'fixedWinodw',         type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'true', 1:'false'],  description:'Window detection'], // fixed_window_sash
                [dp:113, name:'countdown',           type:'number',  rw: 'ro', min:0,   max:99999, defVal:0,    description:'Countdown'],                           // motor_timeout 
                [dp:114, name:'windowDetection',     type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'true', 1:'false'],  description:'Window detection'],
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
    //logDebug "customInitializeVars(${fullInit})"
    if (state.deviceProfile == null) { setDeviceNameAndProfile() }               // in deviceProfileiLib.groovy
    if (fullInit == true) { resetPreferencesToDefaults() }                      // in deviceProfileiLib.groovy
    if (fullInit || settings?.maxTravelTime == null) { device.updateSetting('maxTravelTime',[value:DEFAULT_MAX_TRAVEL_TIME, type:'number'])  }
    if (fullInit || settings?.deltaPosition == null) { device.updateSetting('deltaPosition',[value:DEFAULT_POSITION_DELTA, type:'number'])  }
    if (fullInit || settings?.commandOpenCode == null) { device.updateSetting('commandOpenCode',[value:COMMAND_OPEN, type:'number'])  }
    if (fullInit || settings?.commandCloseCode == null) { device.updateSetting('commandCloseCode',[value:COMMAND_CLOSE, type:'number'])  }
    if (fullInit || settings?.commandStopCode == null) { device.updateSetting('commandStopCode',[value:COMMAND_PAUSE, type:'number'])  }
    if (fullInit || settings?.substituteOpenClose == null) { device.updateSetting('substituteOpenClose',[value:false, type:'bool'])  }
    if (fullInit || settings?.invertPosition == null) { device.updateSetting('invertPosition',[value:false, type:'bool'])  }
    if (fullInit || settings?.targetAsCurrentPosition == null) { device.updateSetting('targetAsCurrentPosition',[value:false, type:'bool'])  }
    
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




// parse commands from parent
void parseShade(List<Map> description) {
    if (logEnable) { log.debug "parse: ${description}" }
    description.each { d ->
        if (d?.name == 'position') {
            processCurrentPositionBridgeEvent(d)
        }
        else if (d?.name == 'targetPosition') {
            processTargetPositionBridgeEvent(d)
        }
        else if (d?.name == 'operationalStatus') {
            processOperationalStatusBridgeEvent(d)
        }
        else {
            if (d?.descriptionText && txtEnable) { log.info "${d.descriptionText}" }
            log.trace "parse: ${d}"
            sendEvent(d)
        }
    }
}

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



void sendSetPosition(Object device, final BigDecimal positionParam) {
    int position = positionParam as Integer
    if (position == null || position < 0 || position > 100) {
        throw new Exception("Invalid position ${position}. Position must be between 0 and 100 inclusive.")
    }
    logDebug("setPosition: target is ${position}, currentPosition=${device.currentValue('position')}")
    if (settings?.invertPosition == true) {
        position = 100 - position
    }
    if (isTS130F()) {
        sendZigbeeCommands(zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_GOTO_LIFT_PERCENTAGE, zigbee.convertToHexString(position, 2)))
    }
    else {
        sendTuyaCommand(DP_ID_TARGET_POSITION, DP_TYPE_VALUE, position.intValue(), 8)
    }
}

// Component command to set position of device
void setPosition(BigDecimal targetPosition) {
    if (txtEnable) { log.info "${device.displayName} setting target position ${targetPosition}% (current position is ${device.currentValue('position')})" }
    sendEvent(name: 'targetPosition', value: targetPosition as Integer, descriptionText: "targetPosition set to ${targetPosition}", type: 'digital')
    updateWindowShadeStatus(device?.currentValue('position') as Integer, targetPosition as Integer, isFinal = false, isDigital = true)
    BigDecimal componentTargetPosition = invertPositionIfNeeded(targetPosition as Integer)
    if (logEnable) { log.debug "inverted componentTargetPosition: ${componentTargetPosition}" }
    sendSetPosition(device, componentTargetPosition)
    startOperationTimeoutTimer()
}

// Component command to start position change of device
void startPositionChange(String change) {
    if (logEnable) { log.debug "${device.displayName} startPositionChange ${change}" }
    if (change == 'open') {
        open()
    }
    else {
        close()
    }
}

void sendStopPositionChange(Object device) {
    int dpCommandStop = getDpCommandStop()
    logDebug "sending command stopPositionChange (${dpCommandStop})"
    if (isTS130F()) {
        sendZigbeeCommands(zigbee.command(0x0102, dpCommandStop as int, [:], delay = 200))
    }
    else {
        sendTuyaCommand('01', DP_TYPE_ENUM, dpCommandStop, 2)
    }
}

// Component command to start position change of device
void stopPositionChange() {
    logDebug "stopPositionChange" 
    sendStopPositionChange(device)
    startOperationTimeoutTimer()
}


// Component command to refresh the device
// TODO !
void refreshMatter() {
    if (txtEnable) { log.info "${device.displayName} refreshing ..." }
    state.standardOpenClose = 'DEFAULT_OPEN = 0% DEFAULT_CLOSED = 100%'
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


/*
void parseTest(description) {
    log.warn "parseTest: ${description}"
    //String str = "name:position, value:0, descriptionText:Bridge#4266 Device#32 (tuya CURTAIN) position is is reported as 0 (to be re-processed in the child driver!) [refresh], unit:null, type:physical, isStateChange:true, isRefresh:true"
    String str = description
    // Split the string into key-value pairs
    List<String> pairs = str.split(', ')
    Map map = [:]
    pairs.each { pair ->
        // Split each pair into a key and a value
        List<String> keyValue = pair.split(':')
        String key = keyValue[0]
        String value = keyValue[1..-1].join(':') // Join the rest of the elements in case the value contains colons
        // Try to convert the value to a boolean or integer if possible
        if (value == 'true' || value == 'false' || value == true || value == false) {
            value = Boolean.parseBoolean(value)
        } else if (value.isInteger()) {
            value = Integer.parseInt(value)
        } else if (value == 'null') {
            value = null
        }
        // Add the key-value pair to the map
        map[key] = value
    }
    log.debug map
    parse([map])
}
*/

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

private getCOMMAND_OPEN()                   { 0x00 }
private getCOMMAND_CLOSE()                  { 0x01 }
private getCOMMAND_PAUSE()                  { 0x02 }
private getCOMMAND_MOVE_LEVEL_ONOFF()       { 0x04 }      // Go To Lift Value
private getCOMMAND_GOTO_LIFT_PERCENTAGE()   { 0x05 }      // Go to Lift Percentage
private getCOMMAND_GOTO_TILT_VALUE()        { 0x07  }     // Go To Tilt Value
private getCOMMAND_GOTO_TILT_PERCENTAGE()   { 0x08 }      // Go to Tilt Percentage

private getATTRIBUTE_POSITION_LIFT()        { 0x0008 }
private getATTRIBUTE_CURRENT_LEVEL()        { 0x0000 }

@Field static final Integer DEFAULT_OPEN   = 0      // this is the standard!  Hubitat is inverted?
@Field static final Integer DEFAULT_CLOSED = 100    // this is the standard!  Hubitat is inverted?

@Field static final Integer DEFAULT_POSITION_DELTA = 2          //settings.deltaPosition , percentage
@Field static final Integer DEFAULT_MAX_TRAVEL_TIME = 30        //settings.maxTravelTime , seconds

int getDelta() { return settings?.deltaPosition != null ? settings?.deltaPosition as int : DEFAULT_POSITION_DELTA }

//int getFullyOpen()   { return settings?.invertOpenClose ? DEFAULT_CLOSED : DEFAULT_OPEN }
//int getFullyClosed() { return settings?.invertOpenClose ? DEFAULT_OPEN : DEFAULT_CLOSED }
//int getFullyOpen()   { return settings?.invertPosition ? DEFAULT_CLOSED : DEFAULT_OPEN }
//int getFullyClosed() { return settings?.invertPosition ? DEFAULT_OPEN : DEFAULT_CLOSED }

int getFullyOpen()   { return  DEFAULT_OPEN }
int getFullyClosed() { return DEFAULT_CLOSED }

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

void sendOpen() { // open is 100 %
    logDebug "sendOpen() dpCommandOpen = ${settings?.commandOpenCode}"
    Integer dpCommandOpen = settings?.commandOpenCode
    String sDirection = device.currentValue('motorDirection') ?: 'unknown'
    logDebug "sendOpen: sending command open (${dpCommandOpen}), sDirection = ${sDirection}"
    List<String> cmds = []

    if (DEVICE?.device?.isTuyaEF00 == true) {
        cmds = getTuyaCommand('01', DP_TYPE_ENUM, zigbee.convertToHexString(dpCommandOpen as int, 2))
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
        cmds = getTuyaCommand('01', DP_TYPE_ENUM, zigbee.convertToHexString(dpCommandClose as int, 2))
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


// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
