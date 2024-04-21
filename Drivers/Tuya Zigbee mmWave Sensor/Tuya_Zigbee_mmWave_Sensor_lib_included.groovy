/* groovylint-disable NglParseError, ImplicitReturnStatement, InsecureRandom, MethodReturnTypeRequired, MethodSize, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnusedImport, VariableName *//**
 *  Tuya Zigbee Button Dimmer - driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *
 * 	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * 	in compliance with the License. You may obtain a copy of the License at:
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * 	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * 	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * 	for the specific language governing permissions and limitations under the License.
 *
 * This driver is inspired by @w35l3y work on Tuya device driver (Edge project).
 * For a big portions of code all credits go to Jonathan Bradshaw.
 *
 * ver. 3.0.6  2024-04-06 kkossev  - (dev. branch) first version
 * ver. 3.0.7  2024-04-21 kkossev  - (dev. branch) deviceProfilesV3; SNZB-06 data type fix; OccupancyCluster processing; added illumState dark/light;
 *
 *                                   TODO: illumState default value is 0 - should be 'unknown' ?
 *                                   TODO: Motion reset to inactive after 43648s - convert to H:M:S
 *                                   TODO: refactor the refresh() method? or postProcess method?
 *                                   TODO: Black Square Radar validateAndFixPreferences: map not found for preference indicatorLight
 *                                   TODO: Linptech spammyDPsToIgnore[] !
 *                                   TODO: command for black radar LED
 *                                   TODO: TS0225_2AAELWXK_RADAR  dont see an attribute as mentioned that shows the distance at which the motion was detected. - https://community.hubitat.com/t/the-new-tuya-human-presence-sensors-ts0225-tze200-hl0ss9oa-tze200-2aaelwxk-have-actually-5-8ghz-modules-inside/122283/294?u=kkossev
 *                                   TODO: TS0225_2AAELWXK_RADAR led setting not working - https://community.hubitat.com/t/the-new-tuya-human-presence-sensors-ts0225-tze200-hl0ss9oa-tze200-2aaelwxk-have-actually-5-8ghz-modules-inside/122283/294?u=kkossev
 *                                   TODO: radars - ignore the change of the presence/motion being turned off when changing parameters for a period of 10 seconds ?
 *                                   TODO: TS0225_HL0SS9OA_RADAR - add presets
 *                                   TODO: humanMotionState - add preference: enum "disabled", "enabled", "enabled w/ timing" ...; add delayed event
*/

static String version() { "3.0.7" }
static String timeStamp() {"2024/04/21 10:43 AM"}

@Field static final Boolean _DEBUG = false
@Field static final Boolean _TRACE_ALL = false      // trace all messages, including the spammy ones

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput





deviceType = "mmWaveSensor"
@Field static final String DEVICE_TYPE = "mmWaveSensor"

metadata {
    definition (
        name: 'Tuya Zigbee mmWave Sensor',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20mmWave%20Sensor/Tuya_Zigbee_mmWave_Sensor_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {

        capability 'MotionSensor'
        capability 'IlluminanceMeasurement'
        capability 'Configuration'
        capability 'Refresh'
        capability 'Health Check'
        
        attribute 'batteryVoltage', 'number'
        attribute 'healthStatus', 'enum', ['offline', 'online']
        attribute 'distance', 'number'              // Tuya Radar
        attribute 'unacknowledgedTime', 'number'    // AIR models
        attribute 'existance_time', 'number'        // BlackSquareRadar & LINPTECH
        attribute 'leave_time', 'number'            // BlackSquareRadar only
        attribute 'keepTime', 'enum', ['10 seconds', '30 seconds', '60 seconds', '120 seconds']
        attribute 'sensitivity', 'enum', ['low', 'medium', 'high']
        attribute 'motionDetectionSensitivity', 'enum', ['1 - low', '2 - medium low', '3 - medium', '4 - medium high', '5 - high']
        attribute 'staticDetectionSensitivity', 'enum', ['1 - low', '2 - medium low', '3 - medium', '4 - medium high', '5 - high']
        attribute 'motionDetectionDistance', 'enum', ['0.75 meters', '1.50 meters', '2.25 meters', '3.00 meters', '3.75 meters', '4.50 meters', '5.25 meters', '6.00 meters']

        attribute 'radarSensitivity', 'number'
        attribute 'staticDetectionSensitivity', 'number'    // added 10/29/2023
        attribute 'detectionDelay', 'decimal'
        attribute 'fadingTime', 'decimal'
        attribute 'minimumDistance', 'decimal'
        attribute 'maximumDistance', 'decimal'
        attribute 'radarStatus', 'enum', ['checking', 'check_success', 'check_failure', 'others', 'comm_fault', 'radar_fault']
        attribute 'humanMotionState', 'enum', ['none', 'moving', 'small_move', 'stationary', 'presence', 'peaceful', 'large_move']
        attribute 'radarAlarmMode', 'enum',   ['0 - arm', '1 - off', '2 - alarm', '3 - doorbell']
        attribute 'radarAlarmVolume', 'enum', ['0 - low', '1 - medium', '2 - high', '3 - mute']
        attribute 'illumState', 'enum', ['dark', 'light', 'unknown']

        command 'setMotion', [[name: 'setMotion', type: 'ENUM', constraints: ['No selection', 'active', 'inactive'], description: 'Force motion active/inactive (for tests)']]

        if (_DEBUG) {
            command 'test', [[name: "test", type: "STRING", description: "test", defaultValue : ""]] 
            command 'parseTest', [[name: "parseTest", type: "STRING", description: "parseTest", defaultValue : ""]]
            command "tuyaTest", [
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"]
            ]
        }
        
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
        if (device) {
            if ((DEVICE?.capabilities?.IlluminanceMeasurement == true) && (DEVICE?.preferences.luxThreshold != false)) {
                input('luxThreshold', 'number', title: '<b>Lux threshold</b>', description: 'Minimum change in the lux which will trigger an event', range: '0..999', defaultValue: 5)
                input name: 'illuminanceCoeff', type: 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: '<i>Illuminance correction coefficient, range (0.10..10.00)</i>', range: '0.10..10.00', defaultValue: 1.00
            }
        }
        if (('DistanceMeasurement' in DEVICE?.capabilities)) {
            input(name: 'ignoreDistance', type: 'bool', title: '<b>Ignore distance reports</b>', description: 'If not used, ignore the distance reports received every 1 second!', defaultValue: true)
        }
    }
}


@Field static final Map deviceProfilesV3 = [
    /*
    'TS0601_TUYA_RADAR'   : [        // isZY_M100Radar()        // spammy devices!
            description   : 'Tuya Human Presence mmWave Radar ZY-M100',
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'2', 'detectionDelay':'101', 'fadingTime':'102', 'minimumDistance':'3', 'maximumDistance':'4'],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ztc6ggyl', deviceJoinName: 'Tuya ZigBee Breath Presence Sensor ZY-M100'],       // KK
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_ztc6ggyl', deviceJoinName: 'Tuya ZigBee Breath Presence Sensor ZY-M100'],       // KK
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ikvncluo', deviceJoinName: 'Moes TuyaHuman Presence Detector Radar 2 in 1'],    // jw970065
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_lyetpprm', deviceJoinName: 'Tuya ZigBee Breath Presence Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_wukb7rhc', deviceJoinName: 'Moes Smart Human Presence Detector'],               // https://www.moeshouse.com/collections/smart-sensor-security/products/smart-zigbee-human-presence-detector-pir-mmwave-radar-detection-sensor-ceiling-mount
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_jva8ink8', deviceJoinName: 'AUBESS Human Presence Detector'],                   // https://www.aliexpress.com/item/1005004262109070.html
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_mrf6vtua', deviceJoinName: 'Tuya Human Presence Detector'],                     // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ar0slwnd', deviceJoinName: 'Tuya Human Presence Detector'],                     // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_sfiy5tfs', deviceJoinName: 'Tuya Human Presence Detector'],                     // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_holel4dk', deviceJoinName: 'Tuya Human Presence Detector'],                     // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars/92441/280?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_xpq2rzhq', deviceJoinName: 'Tuya Human Presence Detector'],                     // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/432?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_qasjif9e', deviceJoinName: 'Tuya Human Presence Detector'],                     //
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_xsm7l9xa', deviceJoinName: 'Tuya Human Presence Detector']                      //

            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', min:0,   max:1 ,    defVal:'0',  scale:1,    map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:2,   name:'radarSensitivity',   type:'number',  rw: 'rw', min:0,   max:9 ,    defVal:7,    scale:1,    unit:'',        title:'<b>Radar sensitivity</b>',          description:'<i>Sensitivity of the radar</i>'],
                [dp:3,   name:'minimumDistance',    type:'decimal', rw: 'rw', min:0.0, max:10.0,  defVal:0.1,  scale:100,  unit:'meters',   title:'<b>Minimim detection distance</b>', description:'<i>Minimim (near) detection distance</i>'],
                [dp:4,   name:'maximumDistance',    type:'decimal', rw: 'rw', min:0.0, max:10.0,  defVal:6.0,  scale:100,  unit:'meters',   title:'<b>Maximum detection distance</b>', description:'<i>Maximum (far) detection distance</i>'],
                [dp:6,   name:'radarStatus',        type:'enum',    rw: 'ro', min:0,   max:5 ,    defVal:'1',  scale:1,    map:[ 0:'checking', 1:'check_success', 2:'check_failure', 3:'others', 4:'comm_fault', 5:'radar_fault'] ,   unit:'TODO',     title:'<b>Radar self checking status</b>', description:'<i>Radar self checking status</i>'],            // radarSeradarSelfCheckingStatus[fncmd.toString()]
                [dp:9,   name:'distance',           type:'decimal', rw: 'ro', min:0.0, max:10.0 , defVal:0.0,  scale:100,  unit:'meters',   title:'<b>Distance</b>',                   description:'<i>detected distance</i>'],
                [dp:101, name:'detectionDelay',     type:'decimal', rw: 'rw', min:0.0, max:10.0,  defVal:0.2,  scale:10,   unit:'seconds',  title:'<b>Detection delay</b>',            description:'<i>Presence detection delay timer</i>'],
                [dp:102, name:'fadingTime',         type:'decimal', rw: 'rw', min:0.5, max:500.0, defVal:60.0, scale:10,   unit:'seconds',  title:'<b>Fading time</b>',                description:'<i>Presence inactivity delay timer</i>'],                                  // aka 'nobody time'
                [dp:103, name:'debugCLI',           type:'number',  rw: 'ro', min:0,   max:99999, defVal:0,    scale:1,    unit:'?',        title:'<b>debugCLI</b>',                   description:'<i>debug CLI</i>'],
                [dp:104, name:'illuminance',        type:'number',  rw: 'ro', min:0,   max:2000,  defVal:0,    scale:1,    unit:'lx',       title:'<b>illuminance</b>',                description:'<i>illuminance</i>'],

            ],
            spammyDPsToIgnore : [9],
            spammyDPsToNotTrace : [9, 103],
            deviceJoinName: 'Tuya Human Presence Detector ZY-M100',
            configuration : [:]
    // status: completed
    ],
    */
    /*
    'TS0601_KAPVNNLK_RADAR'   : [        // 24GHz spammy radar w/ battery backup - no illuminance!
            description   : 'Tuya TS0601_KAPVNNLK 24GHz Radar',        // https://www.amazon.com/dp/B0CDRBX1CQ?psc=1&ref=ppx_yo2ov_dt_b_product_details  // https://www.aliexpress.com/item/1005005834366702.html  // https://github.com/Koenkk/zigbee2mqtt/issues/18632
            models        : ['TS0601'],                                // https://www.aliexpress.com/item/1005005858609756.html     // https://www.aliexpress.com/item/1005005946786561.html    // https://www.aliexpress.com/item/1005005946931559.html
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'DistanceMeasurement':true, 'HumanMotionState':true],
            preferences   : ['radarSensitivity':'15', 'fadingTime':'12', 'maximumDistance':'13', 'smallMotionDetectionSensitivity':'16'],
            commands      : ['resetStats':'resetStats', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_kapvnnlk', deviceJoinName: 'Tuya 24 GHz Human Presence Detector NEW']           // https://community.hubitat.com/t/tuya-smart-human-presence-sensor-micromotion-detect-human-motion-detector-zigbee-ts0601-tze204-sxm7l9xa/111612/71?u=kkossev
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',              type:'enum',    rw: 'ro', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:11,  name:'humanMotionState',    type:'enum',    rw: 'ro', min:0,   max:2,     defVal:'0', map:[0:'none', 1:'small_move', 2:'large_move'],  description:'Human motion state'],        // "none", "small_move", "large_move"]
                [dp:12,  name:'fadingTime',          type:'number',  rw: 'rw', min:3,   max:600,   defVal:60,   scale:1,   unit:'seconds',    title:'<b>Fading time</b>',                description:'<i>Presence inactivity delay timer</i>'],                                  // aka 'nobody time'
                [dp:13,  name:'maximumDistance',     type:'decimal', rw: 'rw', min:1.5, max:6.0,   defVal:4.0, step:75, scale:100, unit:'meters',     title:'<b>Maximum detection distance</b>', description:'<i>Maximum (far) detection distance</i>'],  // aka 'Large motion detection distance'
                [dp:15,  name:'radarSensitivity',    type:'number',  rw: 'rw', min:0,   max:7 ,    defVal:5,    scale:1,   unit:'',          title:'<b>Radar sensitivity</b>',          description:'<i>Large motion detection sensitivity of the radar</i>'],
                [dp:16 , name:'smallMotionDetectionSensitivity', type:'number',  rw: 'rw', min:0,   max:7,  defVal:5,     scale:1,   unit:'', title:'<b>Small motion sensitivity</b>',   description:'<i>Small motion detection sensitivity</i>'],
                [dp:19,  name:'distance',            type:'decimal', rw: 'ro', min:0.0, max:10.0,  defVal:0.0,  scale:100, unit:'meters',     title:'<b>Distance</b>',                   description:'<i>detected distance</i>'],
                [dp:101, name:'batteryLevel',        type:'number',  rw: 'ro', min:0,   max:100,   defVal:100,  scale:1,   unit:'%',          title:'<b>Battery level</b>',              description:'<i>Battery level</i>']
            ],
            spammyDPsToIgnore : [19],
            spammyDPsToNotTrace : [19],
            deviceJoinName: 'Tuya 24 GHz Human Presence Detector NEW',
            configuration : [:]
    ],
    */
    /*
    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/f277bef2f84d50aea70c25261db0c2ded84b7396/src/devices/tuya.ts#L4164
    'TS0601_RADAR_MIR-HE200-TY'   : [        // Human presence sensor radar 'MIR-HE200-TY' - illuminance, presence, occupancy, motion_speed, motion_direction, radar_sensitivity, radar_scene ('default', 'area', 'toilet', 'bedroom', 'parlour', 'office', 'hotel')
            description   : 'Tuya Human Presence Sensor MIR-HE200-TY',
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true],
            preferences   : ['radarSensitivity':'2', 'tumbleSwitch':'105', 'tumbleAlarmTime':'106', 'fallSensitivity':'118'],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_vrfecyku', deviceJoinName: 'Tuya Human presence sensor MIR-HE200-TY'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_lu01t0zl', deviceJoinName: 'Tuya Human presence sensor with fall function'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ypprdwsl', deviceJoinName: 'Tuya Human presence sensor with fall function']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0', scale:1,    map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:2,   name:'radarSensitivity',   type:'number',  rw: 'rw', min:0,   max:10,    defVal:7,   scale:1,    unit:'',        title:'<b>Radar sensitivity</b>',          description:'<i>Sensitivity of the radar</i>'],
                [dp:102, name:'motionState',        type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0', scale:1,    map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Motion state</b>', description:'<i>Motion state (occupancy)</i>'],
                [dp:103, name:'illuminance',        type:'number',  rw: 'ro', min:0,   max:2000,  defVal:0,   scale:1,    unit:'lx',       title:'<b>illuminance</b>',                description:'<i>illuminance</i>'],
                [dp:105, name:'tumbleSwitch',       type:'enum',    rw: 'rw', min:0,   max:1,     defVal:'0', scale:1,    map:[0:'OFF', 1:'ON'] ,   unit:'',     title:'<b>Tumble status switch</b>', description:'<i>Tumble status switch</i>'],
                [dp:106, name:'tumbleAlarmTime',    type:'number',  rw: 'rw', min:1,   max:5,     defVal:1,   scale:1,    unit:'minutes',        title:'<b>Tumble alarm time</b>',                   description:'<i>Tumble alarm time</i>'],
                [dp:112, name:'radarScene',         type:'enum',    rw: 'rw', min:0,   max:6,     defVal:'0', scale:1,    map:[ 0:'default', 1:'area', 2:'toilet', 3:'bedroom', 4:'parlour', 5:'office', 6:'hotel'] ,   unit:'-',     title:'<b>Radar Presets</b>', description:'<i>Presets for sensitivity for presence and movement</i>'],
                [dp:114, name:'motionDirection',    type:'enum',    rw: 'ro', min:0,   max:2,     defVal:'0', scale:1,    map:[ 0:'standing_still', 1:'moving_forward', 2:'moving_backward'] ,   unit:'-',     title:'<b>Movement direction</b>', description:'<i>direction of movement from the point of view of the radar</i>'],
                [dp:115, name:'motionSpeed',        type:'number',  rw: 'ro', min:0,   max:9999,  defVal:0,   scale:1,    unit:'-',        title:'<b>Movement speed</b>',                   description:'<i>Speed of movement</i>'],
                [dp:116, name:'fallDownStatus',     type:'enum',    rw: 'ro', min:0,   max:2,     defVal:'0', scale:1,    map:[ 0:'none', 1:'maybe_fall', 2:'fall'] ,   unit:'-',     title:'<b>Fall down status</b>', description:'<i>Fall down status</i>'],
                //[dp:117, name:'staticDwellAalarm',  type:"text",    rw: "ro", min:0,   max:9999,  defVal:0, scale:1,    unit:"-",        title:"<b>Static dwell alarm</b>",                   description:'<i>Static dwell alarm</i>'],
                [dp:118, name:'fallSensitivity',    type:'number',  rw: 'rw', min:1,   max:10,    defVal:7,   scale:1,    unit:'',        title:'<b>Fall sensitivity</b>',          description:'<i>Fall sensitivity of the radar</i>'],
            ],
            deviceJoinName: 'Tuya Human Presence Sensor MIR-HE200-TY',
            configuration : [:]
    ],
    */
    /*
    'TS0601_BLACK_SQUARE_RADAR'   : [        // // 24GHz Big Black Square Radar w/ annoying LED    // isBlackSquareRadar()
            description   : 'Tuya Black Square Radar',
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor':true],
            preferences   : ['indicatorLight':103],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_0u3bj3rc', deviceJoinName: '24GHz Black Square Human Presence Radar w/ LED'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_v6ossqfy', deviceJoinName: '24GHz Black Square Human Presence Radar w/ LED'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_mx6u6l4y', deviceJoinName: '24GHz Black Square Human Presence Radar w/ LED']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',         type:'enum',   rw: 'ro', min:0, max:1,    defVal: '0', map:[0:'inactive', 1:'active'],     description:'Presence'],
                [dp:101, name:'existance_time', type:'number', rw: 'ro', min:0, max:9999, scale:1,   unit:'minutes',    description:'Shows the presence duration in minutes'],
                [dp:102, name:'leave_time',     type:'number', rw: 'ro', min:0, max:9999, scale:1,   unit:'minutes',    description:'Shows the duration of the absence in minutes'],
                [dp:103, name:'indicatorLight', type:'enum',   rw: 'rw', min:0, max:1,    defVal: '0', map:[0:'OFF', 1:'ON'],  title:'<b>Indicator Light</b>', description:'<i>Turns the onboard LED on or off</i>']
            ],
            spammyDPsToIgnore : [103],                    // we don't need to know the LED status every 4 seconds!
            spammyDPsToNotTrace : [1, 101, 102, 103],     // very spammy device - 4 packates are sent every 4 seconds!
            deviceJoinName: '24GHz Black Square Human Presence Radar w/ LED',
    ],
    */
    /*
    'TS0601_YXZBRB58_RADAR'   : [        // Seller: shenzhenshixiangchuangyeshiyey Manufacturer: Shenzhen Eysltime Intelligent LTD    Item model number: YXZBRB58  isYXZBRB58radar()
            description   : 'Tuya YXZBRB58 Radar',
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],    // https://github.com/Koenkk/zigbee2mqtt/issues/18318
            preferences   : ['radarSensitivity':'2', 'detectionDelay':'103', 'fadingTime':'102', 'minimumDistance':'3', 'maximumDistance':'4'],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_sooucan5', deviceJoinName: 'Tuya Human Presence Detector YXZBRB58']                      // https://www.amazon.com/dp/B0BYDCY4YN
            ],
            tuyaDPs:        [        // TODO - use already defined DPs and preferences !!
                [dp:1,   name:'motion',                 type:'enum',    rw: 'ro', min:0,   max:2,     defVal:'0',  map:[0:'inactive', 1:'active'],  description:'Presence state'],
                [dp:2,   name:'radarSensitivity',       type:'number',  rw: 'rw', min:0,   max:9 ,    defVal:7,    scale:1,    unit:'',        title:'<b>Radar Sensitivity</b>',    description:'<i>Sensitivity of the radar</i>'],
                [dp:3,   name:'minimumDistance',        type:'decimal', rw: 'rw', min:0.0, max:10.0,  defVal:0.5,  scale:100,  unit:'meters',   title:'<b>Minimum distance</b>',     description:'<i>Minimum detection distance</i>'],
                [dp:4,   name:'maximumDistance',        type:'decimal', rw: 'rw', min:0.0, max:10.0,  defVal:6.0,  scale:100,  unit:'meters',   title:'<b>Maximum distance</b>',     description:'<i>Maximum detection distance</i>'],
                [dp:101, name:'illuminance',            type:'number',  rw: 'ro',                     scale:1,    unit:'lx',       description:'Illuminance'],
                [dp:102, name:'fadingTime',             type:'number',  rw: 'rw', min:5,   max:1500,  defVal:60,     scale:1,   unit:'seconds',  title:'<b>Fading time</b>', description:'<i>Presence inactivity timer, seconds</i>'],
                [dp:103, name:'detectionDelay',         type:'decimal', rw: 'rw', min:0.0, max:10.0,  defVal:1.0,  scale:10,   unit:'seconds',   title:'<b>Detection delay</b>', description:'<i>Detection delay</i>'],
                [dp:104, name:'radar_scene',            type:'enum',    rw: 'rw', min:0,   max:4,     defVal:'0',  map:[0:'default', 1:'bathroom', 2:'bedroom', 3:'sleeping'],  description:'Presets for sensitivity for presence and movement'],    // https://github.com/kirovilya/zigbee-herdsman-converters/blob/b9bb6695fdf5d26ab4195cca9fcb1f2bd73afa71/src/devices/tuya.ts
                [dp:105, name:'distance',               type:'decimal', rw: 'ro', min:0.0, max:10.0,  scale:100,  unit:'meters',   description:'Distance']
            ],                    // https://github.com/zigpy/zha-device-handlers/issues/2429
            spammyDPsToIgnore : [105],
            spammyDPsToNotTrace : [105],
            deviceJoinName: 'Tuya Human Presence Detector YXZBRB58',    // https://www.aliexpress.com/item/1005005764168560.html
            configuration : [:]
    ],
    */
    /*
    // isSXM7L9XAradar()                                                // https://github.com/dresden-elektronik/deconz-rest-plugin/issues/6998#issuecomment-1612113340
    'TS0601_SXM7L9XA_RADAR'   : [                                       // https://gist.github.com/Koenkk/9295fc8afcc65f36027f9ab4d319ce64
            description   : 'Tuya Human Presence Detector SXM7L9XA',    // https://github.com/zigpy/zha-device-handlers/issues/2378#issuecomment-1558777494
            models        : ['TS0601'],                                 // https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/tree/main
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],     // https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/blob/main/wenzhi_tuya_M100-230908.js
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'106', 'detectionDelay':'111', 'fadingTime':'110', 'minimumDistance':'108', 'maximumDistance':'107'],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_sxm7l9xa', deviceJoinName: 'Tuya Human Presence Detector SXM7L9XA'],      // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_e5m9c5hl', deviceJoinName: 'Tuya Human Presence Detector WZ-M100']       // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/745?u=kkossev
            ],
            tuyaDPs:        [
                [dp:104, name:'illuminance',            type:'number',  rw: 'ro',                     scale:1, unit:'lx',          description:'illuminance'],
                [dp:105, name:'motion',                 type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0', map:[0:'inactive', 1:'active'],  description:'Presence state'],
                [dp:106, name:'radarSensitivity',       type:'number',  rw: 'rw', min:1,   max:10 ,   defVal:7,   scale:1,    unit:'',        title:'<b>Motion sensitivity</b>', description:'<i>Motion sensitivity</i>'],
                [dp:107, name:'maximumDistance',        type:'decimal', rw: 'rw', min:0.0, max:9.5,   defVal:6.0, scale:100,  unit:'meters',   title:'<b>Maximum distance</b>',   description:'<i>Max detection distance</i>'],
                [dp:108, name:'minimumDistance',        type:'decimal', rw: 'rw', min:0.0, max:9.5,   defVal:0.5, scale:100,  unit:'meters',   title:'<b>Minimum distance</b>',   description:'Min detection distance'],       // TODO - check DP!
                [dp:109, name:'distance',               type:'decimal', rw: 'ro', min:0.0, max:10.0,  scale:100,  unit:'meters',    description:'Distance'],
                [dp:110, name:'fadingTime',             type:'decimal', rw: 'rw', min:0.5, max:150.0, defVal:60.0, step:5,    scale:10,   unit:'seconds',  title: '<b>Fading time</b>', description:'<i>Presence inactivity timer</i>'],
                [dp:111, name:'detectionDelay',         type:'decimal', rw: 'rw', min:0.0,  max:10.0, defVal:0.5, scale:10,   unit:'seconds',              title: '<b>Detection delay</b>', description:'<i>Detection delay</i>']
            ],
            spammyDPsToIgnore : [109],
            spammyDPsToNotTrace : [109],
            deviceJoinName: 'Tuya Human Presence Detector SXM7L9XA',
            configuration : [:],
    ],
    */
    /*
    // isIJXVKHD0radar()  '24G MmWave radar human presence motion sensor'
    'TS0601_IJXVKHD0_RADAR'   : [
            description   : 'Tuya Human Presence Detector IJXVKHD0',    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/5acadaf16b0e85c1a8401223ddcae3d31ce970eb/src/devices/tuya.ts#L5747
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'106', 'staticDetectionSensitivity':'111', 'fadingTime':'110', 'maximumDistance':'107'],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_ijxvkhd0', deviceJoinName: 'Tuya Human Presence Detector ZY-M100-24G']       //
            ],
            tuyaDPs:        [                           // https://github.com/Koenkk/zigbee2mqtt/issues/18237
                [dp:1, name:'unknownDp1',               type:'enum',    rw: 'ro', min:0,   max:1,    defVal:'0', map:[0:'inactive', 1:'active'],          description:'unknown state dp1'],
                [dp:2, name:'unknownDp2',               type:'enum',    rw: 'ro', min:0,   max:1,    defVal:'0', map:[0:'inactive', 1:'active'],          description:'unknown state dp2'],
                [dp:104, name:'illuminance',            type:'number',  rw: 'ro',                    scale:1, unit:'lx',                  description:'illuminance'],
                [dp:105, name:'humanMotionState',       type:'enum',    rw: 'ro', min:0,   max:2,    defVal:'0', map:[0:'none', 1:'present', 2:'moving'], description:'Presence state'],
                [dp:106, name:'radarSensitivity', preProc:'divideBy10',      type:'number',  rw: 'rw', min:1,   max:9,    defVal:2 ,  scale:1,   unit:'',           title:'<b>Motion sensitivity</b>',          description:'<i>Radar motion sensitivity<br>1 is highest, 9 is lowest!</i>'],
                [dp:107, name:'maximumDistance',        type:'decimal', rw: 'rw', min:1.5, max:5.5,  defVal:5.5, scale:100, unit:'meters',      title:'<b>Maximum distance</b>',          description:'<i>Max detection distance</i>'],
                [dp:109, name:'distance',               type:'decimal', rw: 'ro', min:0.0, max:10.0, defVal:0.0, scale:100, unit:'meters',             description:'Target distance'],
                [dp:110, name:'fadingTime',             type:'number',  rw: 'rw', min:1,   max:1500, defVal:5,   scale:1,   unit:'seconds',   title:'<b<Delay time</b>',         description:'<i>Delay (fading) time</i>'],
                [dp:111, name:'staticDetectionSensitivity', preProc:'divideBy10', type:'number',  rw: 'rw', min:1, max:9,  defVal:3,   scale:1,   unit:'',      title:'<b>Static detection sensitivity</b>', description:'<i>Presence sensitivity<br>1 is highest, 9 is lowest!</i>'],
                [dp:112, name:'motion',                 type:'enum',    rw: 'ro', min:0,   max:1,    defVal:'0',       scale:1,    map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:123, name:'presence',               type:'enum',    rw: 'ro', min:0,   max:1,    defVal:'0', map:[0:'none', 1:'presence'],            description:'Presence']    // TODO -- check if used?
            ],
            spammyDPsToIgnore : [109, 9], // dp 9 test
            spammyDPsToNotTrace : [109, 104],   // illuminance reporting is extremly spammy !
            deviceJoinName: 'Tuya Human Presence Detector ZY-M100-24G',
            configuration : [:]
    ],
    */
/*
SmartLife   radarSensitivity staticDetectionSensitivity
    L1          7                   9
    L2          6                   7
    L3          4                   6
    L4          2                   4
    L5          2                   3
*/
    /*
    'TS0601_YENSYA2C_RADAR'   : [                                       // Loginovo Zigbee Mmwave Human Presence Sensor (rectangular)    // TODO: update thread first post
            description   : 'Tuya Human Presence Detector YENSYA2C',    // https://github.com/Koenkk/zigbee2mqtt/issues/18646
            models        : ['TS0601'],                                 // https://www.aliexpress.com/item/1005005677110270.html
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'101', 'presence_time':'12', 'detectionDelay':'102', 'fadingTime':'116', 'minimumDistance': '111', 'maximumDistance':'112'],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_yensya2c', deviceJoinName: 'Tuya Human Presence Detector YENSYA2C'],       //
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_mhxn2jso', deviceJoinName: 'Tuya Human Presence Detector New']       //  https://github.com/Koenkk/zigbee2mqtt/issues/18623
            //        ^^^similar DPs, but not a full match? ^^^ TODO - make a new profile ?   // https://raw.githubusercontent.com/kvazis/training/master/z2m_converters/converters/_TZE204_mhxn2jso.js
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0', scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:12,  name:'presence_time',      type:'decimal', rw: 'rw', min:0.1, max:360.0, defVal:0.5, scale:10,  unit:'seconds', title:'<b>Presence time</b>', description:'<i>Presence time</i>'],    // for _TZE204_mhxn2jso
                [dp:19,  name:'distance',           type:'decimal', rw: 'ro', min:0.0, max:10.0,  defVal:0.0, scale:100, unit:'meters',  description:'Distance'],
                [dp:20,  name:'illuminance',        type:'number',  rw: 'ro', min:0,   max:10000, scale:1,   unit:'lx',        description:'illuminance'],
                [dp:101, name:'radarSensitivity',   type:'number',  rw: 'rw', min:0,   max:10,    defVal:7,   scale:1,   unit:'',  title:'<b>Radar sensitivity</b>',       description:'<i>Radar sensitivity</i>'],
                [dp:102, name:'detectionDelay',     type:'decimal', rw: 'rw', min:0.5, max:360.0, defVal:1.0, scale:10,  unit:'seconds',  title:'<b>Delay time</b>',   description:'<i>Presence detection delay time</i>'],
                [dp:111, name:'minimumDistance',    type:'decimal', rw: 'rw', min:0.0, max:10.0,  defVal:0.1, scale:100, unit:'meters',  title:'<b>Minimum distance</b>', description:'<i>Breath detection minimum distance</i>'],
                [dp:112, name:'maximumDistance',    type:'decimal', rw: 'rw', min:0.5, max:10.0,  defVal:7.0, scale:100, unit:'meters',  title:'<b>Maximum distance</b>', description:'<i>Breath detection maximum distance</i>'],
                [dp:113, name:'breathe_flag',       type:'enum',  rw: 'rw'],
                [dp:114, name:'small_flag',         type:'enum',  rw: 'rw'],
                [dp:115, name:'large_flag',         type:'enum',  rw: 'rw'],
                [dp:116, name:'fadingTime',         type:'number', rw: 'rw', min:0,  max:3600,  defVal:30, scale:1,   unit:'seconds',  title:'<b>Fading time</b>', description:'<i>Presence (fading) delay time</i>']
            ],
            spammyDPsToIgnore : [19],
            spammyDPsToNotTrace : [19],
            deviceJoinName: 'Tuya Human Presence Detector YENSYA2C',
            configuration : [:]
    ],
    */
    /*
    // the new 5.8 GHz radar w/ humanMotionState and a lot of configuration options, 'not-so-spammy' !   - pedestal mount form-factor
    'TS0225_HL0SS9OA_RADAR'   : [
            description   : 'Tuya TS0225_HL0SS9OA Radar',        // https://www.aliexpress.com/item/1005005761971083.html
            models        : ['TS0225'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true],
            preferences   : ['presenceKeepTime':'12', 'ledIndicator':'24', 'radarAlarmMode':'105', 'radarAlarmVolume':'102', 'radarAlarmTime':'101', \
                             'motionFalseDetection':'112', 'motionDetectionSensitivity':'15', 'motionMinimumDistance':'106', 'motionDetectionDistance':'13', \
                             'smallMotionDetectionSensitivity':'16', 'smallMotionMinimumDistance':'107', 'smallMotionDetectionDistance':'14', \
                             'breatheFalseDetection':'115', 'staticDetectionSensitivity':'104', 'staticDetectionMinimumDistance':'108', 'staticDetectionDistance':'103' \
                            ],
            commands      : ['resetSettings':'resetSettings', 'moveSelfTest':'moveSelfTest', 'smallMoveSelfTest':'smallMoveSelfTest', 'breatheSelfTest':'breatheSelfTest',  \
                             'resetStats':'resetStats', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences'
            ],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,E002,EF00', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZE200_hl0ss9oa', deviceJoinName: 'Tuya TS0225_HL0SS9OA Human Presence Detector']       // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
            ],
            tuyaDPs:        [        // W.I.P - use already defined DPs and preferences !!  TODO - verify teh default values !
                [dp:1,   name:'motion',                          type:'enum',    rw: 'ro', min:0,    max:1,    defVal:'0',   scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:11,  name:'humanMotionState',                type:'enum',    rw: 'ro', min:0,    max:3,    defVal:'0',  map:[0:'none', 1:'large', 2:'small', 3:'static'],       description:'Human motion state'],
                [dp:12,  name:'presenceKeepTime',                type:'number',  rw: 'rw', min:5,    max:3600, defVal:30,    scale:1,   unit:'seconds',   title:'<b>Presence keep time</b>',                 description:'<i>Presence keep time</i>'],
                [dp:13,  name:'motionDetectionDistance',         type:'decimal', rw: 'rw', min:0.0,  max:10.0, defVal:6.0,   scale:100, unit:'meters',    title:'<b>Motion Detection Distance</b>',          description:'<i>Large motion detection distance, meters</i>'], //dt: "UINT16"
                [dp:14,  name:'smallMotionDetectionDistance',    type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:5.0,   scale:100, unit:'meters',    title:'<b>Small motion detection distance</b>',    description:'<i>Small motion detection distance</i>'],
                [dp:15,  name:'motionDetectionSensitivity',      type:'number',  rw: 'rw', min:0,    max:10,   defVal:7,     scale:1,   unit:'',         title:'<b>Motion Detection Sensitivity</b>',       description:'<i>Large motion detection sensitivity</i>'],           // dt: "UINT8" aka Motionless Detection Sensitivity
                [dp:16,  name:'smallMotionDetectionSensitivity', type:'number',  rw: 'rw', min:0,    max:10 ,  defVal:7,     scale:1,   unit:'',         title:'<b>Small motion detection sensitivity</b>', description:'<i>Small motion detection sensitivity</i>'],
                [dp:20,  name:'illuminance',                     type:'number',  rw: 'ro',                                                scale:10,  unit:'lx',        description:'Illuminance'],
                [dp:24,  name:'ledIndicator',                    type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - OFF', 1:'1 - ON'],       title:'<b>LED indicator mode</b>',                 description:'<i>LED indicator mode</i>'],
                [dp:101, name:'radarAlarmTime',                  type:'number',  rw: 'rw', min:0,    max:60 ,  defVal:1,     scale:1,   unit:'seconds',   title:'<b>Alarm time</b>',                         description:'<i>Alarm time</i>'],
                [dp:102, name:'radarAlarmVolume',                type:'enum',    rw: 'rw', min:0,    max:3,    defVal:'3',  map:[0:'0 - low', 1:'1 - medium', 2:'2 - high', 3:'3 - mute'],  title:'<b>Alarm volume</b>',            description:'<i>Alarm volume</i>'],
                [dp:103, name:'staticDetectionDistance',         type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:4.0,   scale:100, unit:'meters',    title:'<b>Static detection distance</b>',          description:'<i>Static detection distance</i>'],
                [dp:104, name:'staticDetectionSensitivity',      type:'number',  rw: 'rw', min:0,    max:10,   defVal:7,     scale:1,   unit:'',         title: '<b>Static Detection Sensitivity</b>',      description:'<i>Static detection sensitivity</i>'],                 //  dt: "UINT8", aka Motionless Detection Sensitivity
                [dp:105, name:'radarAlarmMode',                  type:'enum',    rw: 'rw', min:0,    max:3,    defVal:'1',  map:[0:'0 - arm', 1:'1 - off', 2:'2 - alarm', 3:'3 - doorbell'],  title:'<b>Alarm mode</b>',            description:'<i>Alarm mode</i>'],
                [dp:106, name:'motionMinimumDistance',           type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:0.5,   scale:100, unit:'meters',    title:'<b>Motion minimum distance</b>',            description:'<i>Motion minimum distance</i>'],
                [dp:107, name:'smallMotionMinimumDistance',      type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:0.5,   scale:100, unit:'meters',    title:'<b>Small Motion Minimum Distance</b>',      description:'<i>Small Motion Minimum Distance</i>'],
                [dp:108, name:'staticDetectionMinimumDistance',  type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:0.5,   scale:100, unit:'meters',    title:'<b>Static detection minimum distance</b>',  description:'<i>Static detection minimum distance</i>'],
                [dp:109, name:'checkingTime',                    type:'decimal', rw: 'ro',                   scale:10,  unit:'seconds',   description:'Checking time'],
                [dp:110, name:'radarStatus',                     type:'enum',    rw: 'ro', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], description:'Radar small move self-test'],
                [dp:111, name:'radarStatus',                     type:'enum',    rw: 'ro', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], description:'Radar breathe self-test'],
                [dp:112, name:'motionFalseDetection',            type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], title:'<b>Motion false detection</b>', description:'<i>Motion false detection</i>'],
                [dp:113, name:'radarReset',                      type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], description:'Radar reset'],
                [dp:114, name:'radarStatus',                     type:'enum',    rw: 'ro', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], description:'Radar move self-test'],
                [dp:115, name:'breatheFalseDetection',           type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], title:'<b>Breathe false detection</b>', description:'<i>Breathe false detection</i>'],
                [dp:116, name:'existance_time',                  type:'number',  rw: 'ro', min:0,    max:60 ,   scale:1,   unit:'seconds',   description:'Radar presence duration'],    // not received
                [dp:117, name:'leave_time',                      type:'number',  rw: 'ro', min:0,    max:60 ,   scale:1,   unit:'seconds',   description:'Radar absence duration'],     // not received
                [dp:118, name:'radarDurationStatus',             type:'number',  rw: 'ro', min:0,    max:60 ,   scale:1,   unit:'seconds',   description:'Radar duration status']       // not received
            ],
            spammyDPsToIgnore : [],
            spammyDPsToNotTrace : [11],
            deviceJoinName: 'Tuya TS0225_HL0SS9OA Human Presence Detector',
            configuration : [:]
    ],
    */
    /*
    // the new 5.8GHz radar w/ humanMotionState and a lot of configuration options, 'not-so-spammy' !   - wall mount form-factor    is2AAELWXKradar()
    'TS0225_2AAELWXK_RADAR'   : [                                     // https://github.com/Koenkk/zigbee2mqtt/issues/18612
            description   : 'Tuya TS0225_2AAELWXK 5.8 GHz Radar',        // https://community.hubitat.com/t/the-new-tuya-24ghz-human-presence-sensor-ts0225-tze200-hl0ss9oa-finally-a-good-one/122283/72?u=kkossev
            models        : ['TS0225'],                                // ZG-205Z   https://github.com/Koenkk/zigbee-herdsman-converters/blob/38bf79304292c380dc8366966aaefb71ca0b03da/src/devices/tuya.ts#L4793
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true],
            // TODO - preferences and DPs !!!!!!!!!!!!!!!!!!!!
            preferences   : ['presenceKeepTime':'102', 'ledIndicator':'107', 'radarAlarmMode':'117', 'radarAlarmVolume':'116', 'radarAlarmTime':'115', \
                             'motionFalseDetection':'103', 'motionDetectionSensitivity':'2', 'motionMinimumDistance':'3', 'motionDetectionDistance':'4', \
                             'smallMotionDetectionSensitivity':'105', 'smallMotionMinimumDistance':'110', 'smallMotionDetectionDistance':'104', \
                             'breatheFalseDetection':'113', 'staticDetectionSensitivity':'109', 'staticDetectionDistance':'108' \
                            ],
            commands      : ['resetSettings':'resetSettings', 'moveSelfTest':'moveSelfTest', 'smallMoveSelfTest':'smallMoveSelfTest', 'breatheSelfTest':'breatheSelfTest',  \
                             'resetStats':'resetStats', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences' \
            ],
            fingerprints  : [                                          // reports illuminance and motion using clusters 0x400 and 0x500 !
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,E002,EF00,EE00,E000,0400', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZE200_2aaelwxk', deviceJoinName: 'Tuya TS0225_2AAELWXK 24Ghz Human Presence Detector']       // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
            ],
            tuyaDPs:        [        // W.I.P. - use already defined DPs and preferences !!
                [dp:1,   name:'motion',                          type:'enum',    rw: 'ro', min:0,    max:1,    defVal:'0',   scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:2,   name:'motionDetectionSensitivity',      type:'number',  rw: 'rw', min:0,    max:10,   defVal:7,     scale:1,   unit:'',         title:'<b>Motion Detection Sensitivity</b>',       description:'<i>Large motion detection sensitivity</i>'],           // dt: "UINT8" aka Motionless Detection Sensitivity
                [dp:3,   name:'motionMinimumDistance',           type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:0.5,   scale:100, unit:'meters',    title:'<b>Motion minimum distance</b>',            description:'<i>Motion minimum distance</i>'],
                [dp:4,   name:'motionDetectionDistance',         type:'decimal', rw: 'rw', min:0.0,  max:10.0, defVal:6.0,   scale:100, unit:'meters',    title:'<b>Motion Detection Distance</b>',          description:'<i>Large motion detection distance, meters</i>'], //dt: "UINT16"
                [dp:101, name:'humanMotionState',                type:'enum',    rw: 'ro', min:0,    max:3,    defVal:'0',  map:[0:'none', 1:'large', 2:'small', 3:'static'],       description:'Human motion state'],
                [dp:102, name:'presenceKeepTime',                type:'number',  rw: 'rw', min:5,    max:3600, defVal:30,    scale:1,   unit:'seconds',   title:'<b>Presence keep time</b>',                 description:'<i>Presence keep time</i>'],
                [dp:103, name:'motionFalseDetection',            type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'],     title:'<b>Motion false detection</b>',     description:'<i>Disable/enable Motion false detection</i>'],
                [dp:104, name:'smallMotionDetectionDistance',    type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:5.0,   scale:100, unit:'meters',    title:'<b>Small motion detection distance</b>',    description:'<i>Small motion detection distance</i>'],
                [dp:105, name:'smallMotionDetectionSensitivity', type:'number',  rw: 'rw', min:0,    max:10 ,  defVal:7,     scale:1,   unit:'',         title:'<b>Small motion detection sensitivity</b>', description:'<i>Small motion detection sensitivity</i>'],
                [dp:106, name:'illuminance',                     type:'number',  rw: 'ro',                                                scale:10,  unit:'lx',        description:'Illuminance'],
                [dp:107, name:'ledIndicator',                    type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - OFF', 1:'1 - ON'],               title:'<b>LED indicator</b>',              description:'<i>LED indicator mode</i>'],
                [dp:108, name:'staticDetectionDistance',         type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:4.0,   scale:100, unit:'meters',    title:'<b>Static detection distance</b>',          description:'<i>Static detection distance</i>'],
                [dp:109, name:'staticDetectionSensitivity',      type:'number',  rw: 'rw', min:0,    max:10,   defVal:7,     scale:1,   unit:'',         title:'<b>Static Detection Sensitivity</b>',       description:'<i>Static detection sensitivity</i>'],                 //  dt: "UINT8", aka Motionless Detection Sensitivity
                [dp:110, name:'smallMotionMinimumDistance',      type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:0.5,   scale:100, unit:'meters',    title:'<b>Small Motion Minimum Distance</b>',      description:'<i>Small Motion Minimum Distance</i>'],
                //[dp:111, name:'staticDetectionMinimumDistance',  type:"decimal", rw: "rw", min:0.0,  max:6.0,   defVal:0.5,  scale:100, unit:"meters",    title:'<b>Static detection minimum distance</b>',  description:'<i>Static detection minimum distance</i>'],
                [dp:112, name:'radarReset',                      type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'],     description:'Radar reset'],
                [dp:113, name:'breatheFalseDetection',           type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'],     title:'<b>Breathe false detection</b>',    description:'<i>Disable/enable Breathe false detection</i>'],
                [dp:114, name:'checkingTime',                    type:'decimal', rw: 'ro',                     scale:10,  unit:'seconds',   description:'Checking time'],
                [dp:115, name:'radarAlarmTime',                  type:'number',  rw: 'rw', min:0,    max:60 ,  defVal:1,     scale:1,   unit:'seconds',   title:'<b>Alarm time</b>',                         description:'<i>Alarm time</i>'],
                [dp:116, name:'radarAlarmVolume',                type:'enum',    rw: 'rw', min:0,    max:3,    defVal:'3',  map:[0:'0 - low', 1:'1 - medium', 2:'2 - high', 3:'3 - mute'],    title:'<b>Alarm volume</b>',          description:'<i>Alarm volume</i>'],
                [dp:117, name:'radarAlarmMode',                  type:'enum',    rw: 'rw', min:0,    max:3,    defVal:'1',  map:[0:'0 - arm', 1:'1 - off', 2:'2 - alarm', 3:'3 - doorbell'],  title:'<b>Alarm mode</b>',            description:'<i>Alarm mode</i>'],
                [dp:118, name:'radarStatus',                     type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], description:'Radar small move self-test'],
                [dp:119, name:'radarStatus',                     type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], description:'Radar breathe self-test'],
                [dp:120, name:'radarStatus',                     type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], description:'Radar move self-test']
                //[dp:116, name:'existance_time',                  type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar presence duration'],    // not received
                //[dp:117, name:'leave_time',                      type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar absence duration'],     // not received
                //[dp:118, name:'radarDurationStatus',             type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar duration status']       // not received
            ],
            deviceJoinName: 'Tuya TS0225_2AAELWXK 5.8 Ghz Human Presence Detector',
            configuration : [:]
    ],
    */
    /*
    // isSBYX0LM6radar()                                               // https://github.com/Koenkk/zigbee-herdsman-converters/issues/5930#issuecomment-1662456347
    'TS0601_SBYX0LM6_RADAR'   : [                                      // _TZE204_sbyx0lm6    TS0601   model: 'MTG075-ZB-RL', '5.8G Human presence sensor with relay',
            description   : 'Tuya Human Presence Detector SBYX0LM6',   // https://github.com/vit-um/hass/blob/main/zigbee2mqtt/tuya_h_pr.js
            models        : ['TS0601'],                                // https://github.com/Koenkk/zigbee-herdsman-converters/issues/5930      https://github.com/Koenkk/zigbee-herdsman-converters/issues/5930#issuecomment-1651270524
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],     // https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/blob/main/ts0601_radar_X75-X25-230705.js
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'2', 'minimumDistance':'3', 'maximumDistance':'4', 'detectionDelay':'101', 'fadingTime':'102', 'entrySensitivity':'105', 'entryDistanceIndentation':'106', 'breakerMode':'107', \
                             'breakerStatus':'108', 'statusIndication':'109', 'illuminThreshold':'110', 'breakerPolarity':'111', 'blockTime':'112'
                            ],
            commands      : ['resetSettings':'resetSettings'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_sbyx0lm6', deviceJoinName: 'Tuya 5.8GHz Human Presence Detector MTG075-ZB-RL'],    // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_sbyx0lm6', deviceJoinName: 'Tuya 5.8GHz Human Presence Detector MTG075-ZB-RL'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_dtzziy1e', deviceJoinName: 'Tuya 24GHz Human Presence Detector MTG275-ZB-RL'],     // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_dtzziy1e', deviceJoinName: 'Tuya 24GHz Human Presence Detector MTG275-ZB-RL'],     // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_clrdrnya', deviceJoinName: 'Tuya Human Presence Detector MTG235-ZB-RL'],               // https://www.aliexpress.com/item/1005005865536713.html                  // https://github.com/Koenkk/zigbee2mqtt/issues/18677?notification_referrer_id=NT_kwDOAF5zfrI3NDQ1Mzc2NTAxOjYxODk5NTA
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_clrdrnya', deviceJoinName: 'Tuya Human Presence Detector MTG235-ZB-RL'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_cfcznfbz', deviceJoinName: 'Tuya Human Presence Detector MTG075-ZB2'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_iaeejhvf', deviceJoinName: 'Tuya Human Presence Detector MTG075-ZB2-RL'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_mtoaryre', deviceJoinName: 'Tuya Human Presence Detector MTG035-ZB2-RL'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_8s6jtscb', deviceJoinName: 'Tuya Human Presence Detector MTG035-ZB2'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_rktkuel1', deviceJoinName: 'Tuya Human Presence Detector MTD065-ZB2'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_mp902om5', deviceJoinName: 'Tuya Human Presence Detector MTG075-ZB'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_mp902om5', deviceJoinName: 'Tuya Human Presence Detector MTG075-ZB'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_w5y5slkq', deviceJoinName: 'Tuya Human Presence Detector MTG275-ZB'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_w5y5slkq', deviceJoinName: 'Tuya Human Presence Detector MTG275-ZB'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_xnaqu2pc', deviceJoinName: 'Tuya Human Presence Detector MTD065-ZB'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_xnaqu2pc', deviceJoinName: 'Tuya Human Presence Detector MTD065-ZB'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_wk7seszg', deviceJoinName: 'Tuya Human Presence Detector MTG235-ZB'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_wk7seszg', deviceJoinName: 'Tuya Human Presence Detector MTG235-ZB'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_0wfzahlw', deviceJoinName: 'Tuya Human Presence Detector MTD021-ZB'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_0wfzahlw', deviceJoinName: 'Tuya Human Presence Detector MTD021-ZB'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_pfayrzcw', deviceJoinName: 'Tuya Human Presence Detector MTG035-ZB-RL'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_pfayrzcw', deviceJoinName: 'Tuya Human Presence Detector MTG035-ZB-RL'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_z4tzr0rg', deviceJoinName: 'Tuya Human Presence Detector MTG035-ZB'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_z4tzr0rg', deviceJoinName: 'Tuya Human Presence Detector MTG035-ZB']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0',   scale:1,    map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:2,   name:'radarSensitivity',   type:'number',  rw: 'rw', min:1,   max:9,     defVal:5,     scale:1,    unit:'',        title:'<b>Radar sensitivity</b>',     description:'<i>Sensitivity of the radar</i>'],
                [dp:3,   name:'minimumDistance',    type:'decimal', rw: 'rw', min:0.0, max:10.0,  defVal:0.1,   step:10, scale:100,  unit:'meters',   title:'<b>Minimum distance</b>',      description:'<i>Shield range of the radar</i>'],         // was shieldRange
                [dp:4,   name:'maximumDistance',    type:'decimal', rw: 'rw', min:1.5, max:10.0,  defVal:7.0,   step:10, scale:100,  unit:'meters',   title:'<b>Maximum distance</b>',      description:'<i>Detection range of the radar</i>'],      // was detectionRange
                [dp:6,   name:'radarStatus',        type:'enum',    rw: 'ro', min:0,   max:5 ,    defVal:'1',   scale:1,    map:[ 0:'checking', 1:'check_success', 2:'check_failure', 3:'others', 4:'comm_fault', 5:'radar_fault'] ,   unit:'',     title:'<b>Radar self checking status</b>', description:'<i>Radar self checking status</i>'],
                [dp:9,   name:'distance',           type:'decimal', rw: 'ro', min:0.0, max:10.0,  defVal:0.0,   scale:100,  unit:'meters',   description:'<i>detected distance</i>'],
                [dp:101, name:'detectionDelay',     type:'decimal', rw: 'rw', min:0.0, max:1.0,   defVal:0.2,   scale:10,   unit:'seconds',  title:'<b>Detection delay</b>',       description:'<i>Entry filter time</i>'],
                [dp:102, name:'fadingTime',         type:'decimal', rw: 'rw', min:0.5, max:150.0, defVal:30.0,  scale:10,   unit:'seconds',  title:'<b>Fading time</b>',                description:'<i>Presence inactivity delay timer</i>'],                                  // aka 'nobody time'
                [dp:103, name:'debugCLI',           type:'number',  rw: 'ro', min:0,   max:99999, defVal:0,     scale:1,    unit:'?',        title:'<b>debugCLI</b>',                   description:'<i>debug CLI</i>'],
                [dp:104, name:'illuminance',        type:'number',  rw: 'ro', min:0,   max:2000,  defVal:0,     scale:10,   unit:'lx',       title:'<b>illuminance</b>',                description:'<i>illuminance</i>'],   // divideBy10 !
                [dp:105, name:'entrySensitivity',   type:'number',  rw: 'rw', min:1,   max:9,     defVal:5,     scale:1,    unit:'',        title:'<b>Entry sensitivity</b>',          description:'<i>Radar entry sensitivity</i>'],
                [dp:106, name:'entryDistanceIndentation',    type:'decimal', rw: 'rw', min:0.0, max:10.0,  defVal:6.0,   step:10,  scale:100,   unit:'meters',  title:'<b>Entry distance indentation</b>',          description:'<i>Entry distance indentation</i>'],     // aka 'Detection range reduce when unoccupied'
                [dp:107, name:'breakerMode',        type:'enum',    rw: 'rw', min:0,   max:3,     defVal:'0',   map:[0:'standalone', 1:'local', 2:'manual', 3:'unavailable'],       title:'<b>Breaker mode</b>',    description:'<i>Status Breaker mode: standalone is external, local is auto</i>'],
                [dp:108, name:'breakerStatus',      type:'enum',    rw: 'rw', min:0,   max:1,     defVal:'0',   map:[0:'OFF', 1:'ON'],       title:'<b>Breaker status</b>',                         description:'<i>on/off state of the switch</i>'],
                [dp:109, name:'statusIndication',   type:'enum',    rw: 'rw', min:0,   max:1,     defVal:'0',   map:[0:'OFF', 1:'ON'],       title:'<b>Status indication</b>',                      description:'<i>Led backlight when triggered</i>'],
                [dp:110, name:'illuminThreshold',   type:'decimal', rw: 'rw', min:0.0, max:420.0, defVal:100.0, scale:10,   unit:'lx',  title:'<b>Illuminance Threshold</b>',          description:'<i>Illumination threshold for switching on</i>'],
                [dp:111, name:'breakerPolarity',    type:'enum',    rw: 'rw', min:0,   max:1,     defVal:'0',   map:[0:'NC', 1:'NO'],       title:'<b>Breaker polarity</b>',                      description:'<i>Normally open / normally closed factory setting</i>'],
                [dp:112, name:'blockTime',          type:'number',  rw: 'rw', min:0,   max:100,   defVal:30,    scale:1,    unit:'seconds',  title:"<b>Block time'</b>",                description:'<i>Sensor inhibition time after presence or relay state changed</i>'],                                  // aka 'nobody time'
                [dp:113, name:'parameterSettingResult',    type:'enum',    rw: 'ro', min:0,   max:6 ,    defVal:'1',     scale:1,    map:[ 0:'none', 1:'invalid detection range reduce', 2:'invalid minimum detection range', 3:'invalid maximum detection range', 4:'switch unavailable', 5:'invalid inhibition time', 6:'switch polarity unsupported'] ,   unit:'',   description:'<i>Config error</i>'],
                [dp:114, name:'factoryParameters',  type:'number',  rw: 'ro',                                                  scale:1,    unit:'-',        description:'Factory Reset'],
                [dp:115, name:'sensor',             type:'enum',    rw: 'ro', min:0,   max:2,     defVal:'0',   scale:1,    map:[0:'on', 1:'off', 2:'report occupy', 3:'report unoccupy'] ,   unit:'',    description:'<i>Sensor state</i>'],
            ],
            spammyDPsToIgnore : [9],
            spammyDPsToNotTrace : [9],
            deviceJoinName: 'Tuya Human Presence Detector SBYX0LM6',
            configuration : [:]
    ],
    */
    /*
    // isLINPTECHradar()
    'TS0225_LINPTECH_RADAR'   : [                                      // https://github.com/Koenkk/zigbee2mqtt/issues/18637
            description   : 'Tuya TS0225_LINPTECH 24GHz Radar',        // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/646?u=kkossev
            models        : ['TS0225'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['fadingTime':'101', 'motionDetectionDistance':'0xE002:0xE00B', 'motionDetectionSensitivity':'0xE002:0xE004', 'staticDetectionSensitivity':'0xE002:0xE005'],
            fingerprints  : [                                          // https://www.amazon.com/dp/B0C7C6L66J?ref=ppx_yo2ov_dt_b_product_details&th=1
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,E002,4000,EF00,0500', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZ3218_awarhusb', deviceJoinName: 'Tuya TS0225_LINPTECH 24Ghz Human Presence Detector']       // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs:       [                                           // the tuyaDPs revealed from iot.tuya.com are actually not used by the device! The only exception is dp:101
                [dp:101,              name:'fadingTime',                      type:'number',                rw: 'rw', min:1,    max:9999, defVal:10,    scale:1,   unit:'seconds', title: '<b>Fading time</b>', description:'<i>Presence inactivity timer, seconds</i>']                                  // aka 'nobody time'
            ],
            attributes:       [                                        // LINPTECH / MOES are using a custom cluster 0xE002 for the settings (except for the fadingTime), ZCL cluster 0x0400 for illuminance (malformed reports!) and the IAS cluster 0x0500 for motion detection
                [at:'0xE002:0xE001',  name:'existance_time',                  type:'number', dt: '0x21', rw: 'ro', min:0,    max:65535,  scale:1,    unit:'minutes',   title: '<b>Existance time/b>',                 description:'<i>existance (presence) time, recommended value is > 10 seconds!</i>'],                    // aka Presence Time
                [at:'0xE002:0xE004',  name:'motionDetectionSensitivity',      type:'enum',   dt: '0x20', rw: 'rw', min:1,    max:5,      defVal:'4',    scale:1,   map:[1: '1 - low', 2: '2 - medium low', 3: '3 - medium', 4: '4 - medium high', 5: '5 - high'], unit:'',         title: '<b>Motion Detection Sensitivity</b>',  description:'<i>Large motion detection sensitivity</i>'],           // aka Motionless Detection Sensitivity
                [at:'0xE002:0xE005',  name:'staticDetectionSensitivity',      type:'enum',   dt: '0x20', rw: 'rw', min:1,    max:5,      defVal:'3',    scale:1,   map:[1: '1 - low', 2: '2 - medium low', 3: '3 - medium', 4: '4 - medium high', 5: '5 - high'], unit:'',         title: '<b>Static Detection Sensitivity</b>',  description:'<i>Static detection sensitivity</i>'],                 // aka Motionless Detection Sensitivity
                [at:'0xE002:0xE00A',  name:'distance',  preProc:'skipIfDisabled', type:'decimal', dt: '0x21', rw: 'ro', min:0.0,  max:6.0,    defVal:0.0,    scale:100,  unit:'meters',            title: '<b>Distance</b>',                      description:'<i>Measured distance</i>'],                            // aka Current Distance
                [at:'0xE002:0xE00B',  name:'motionDetectionDistance',         type:'enum',   dt: '0x21', rw: 'rw', min:0.75, max:6.00, defVal:'450', step:75, scale:100, map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters', '300': '3.00 meters', '375': '3.75 meters', '450': '4.50 meters', '525': '5.25 meters', '600' : '6.00 meters'], unit:'meters', title: '<b>Motion Detection Distance</b>', description:'<i>Large motion detection distance, meters</i>']               // aka Far Detection
            ],
            spammyDPsToIgnore : [19],       // TODO
            spammyDPsToNotTrace : [19],     // TODO
            deviceJoinName: 'Tuya TS0225_LINPTECH 24Ghz Human Presence Detector',
            configuration : [:]
    ],
    */
    /*
    //  no-name 240V AC ceiling radar presence sensor
    'TS0225_EGNGMRZH_RADAR'   : [                                    // https://github.com/sprut/Hub/issues/2489
            description   : 'Tuya TS0225_EGNGMRZH 24GHz Radar',      // isEGNGMRZHradar()
            models        : ['TS0225'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'101', 'presence_time':'12', 'detectionDelay':'102', 'fadingTime':'116', 'minimumDistance': '111', 'maximumDistance':'112'],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,,0500,1000,EF00,0003,0004,0008', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZFED8_egngmrzh', deviceJoinName: 'Tuya TS0225_EGNGMRZH 24Ghz Human Presence Detector']       // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
            ],
            // uses IAS for occupancy!
            tuyaDPs:        [
                [dp:101, name:'illuminance',        type:'number',  rw: 'ro', min:0,  max:10000, scale:1,   unit:'lx'],        // https://github.com/Koenkk/zigbee-herdsman-converters/issues/6001
                [dp:103, name:'distance',           type:'decimal', rw: 'ro', min:0.0,  max:10.0,  defVal:0.0, scale:10,  unit:'meters']
                
                //[dp:104, name:'unknown 104 0x68',            type:'number',  rw: 'ro'],    //68
                //[dp:105, name:'unknown 105 0x69',            type:'number',  rw: 'ro'],    //69
                //[dp:109, name:'unknown 109 0x6D',            type:'number',  rw: 'ro'],    //6D
                //[dp:110, name:'unknown 110 0x6E',            type:'number',  rw: 'ro'],    //6E
                //[dp:111, name:'unknown 111 0x6F',            type:'number',  rw: 'ro'],    //6F
                //[dp:114, name:'unknown 114 0x72',            type:'number',  rw: 'ro'],    //72
                //[dp:115, name:'unknown 115 0x73',            type:'number',  rw: 'ro'],    //73
                //[dp:116, name:'unknown 116 0x74',            type:'number',  rw: 'ro'],    //74
                //[dp:118, name:'unknown 118 0x76',            type:'number',  rw: 'ro'],    //76
                //[dp:119, name:'unknown 119 0x77',            type:'number',  rw: 'ro']     //77
                
            ],
            spammyDPsToIgnore : [103],
            spammyDPsToNotTrace : [103],
            deviceJoinName: 'Tuya TS0225_AWARHUSB 24Ghz Human Presence Detector',
            configuration : ['battery': false]
    ],
    */
    /*
    'TS0225_O7OE4N9A_RADAR'   : [                                       // Aubess Zigbee-Human Presence Detector, Smart PIR Human Body Sensor, Wifi Radar, Microwave Motion Sensors, Tuya, 1/24/5G
            description   : 'Tuya Human Presence Detector YENSYA2C',    // https://github.com/Koenkk/zigbee2mqtt/issues/20082#issuecomment-1856204828
            models        : ['TS0225'],                                 // https://fr.aliexpress.com/item/1005006016522811.html
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false], // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/926?u=kkossev
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'110', 'motionSensitivity':'114', 'stateLockDuration':'101', 'fadingTime':'116'],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0500,0008,1000', outClusters:'000A,0019', model:'TS0225', manufacturer:'_TZFED8_o7oe4n9a', deviceJoinName: 'Aubess Human Presence Detector '],       //
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',                 type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0', scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',  title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:101, name:'stateLockDuration',      type:'number',  rw: 'rw', min:1,   max:5,     defVal:1,   scale:1,   unit:'seconds',  title:'<b>State Lock Duration</b>', description:'<i>After a change in manned or unmanned status, it will not change for the specified period of time</i>'],    // to be checked/clarified
                [dp:102, name:'fadingTime',             type:'number',  rw: 'rw', min:0,   max:3600,  defVal:30,  scale:1,   unit:'seconds',  title:'<b>Fading time</b>', description:'<i>How many seconds does it take to become unmanned when no one is detected</i>'],
                [dp:105, name:'distanceIntervalSwitch', type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0', scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',  title:'<b>Distance Interval Switch</b>', description:'<i>Distance interval switch</i>'], // to be checked/clarified
                [dp:110, name:'radarSensitivity',       type:'number',  rw: 'rw', min:1,   max:10,    defVal:1,   scale:1,   unit:'',  title:'<b>Radar sensitivity</b>',  description:'<i>Occupancy Sensitivity<br>1 = Highest 10 = Lowest</i>'],
                [dp:114, name:'motionSensitivity',      type:'number',  rw: 'rw', min:1,   max:20,    defVal:7,   scale:1,   unit:'',  title:'<b>Motion sensitivity</b>',  description:'<i>Motion Sensitivity<br>1 = Highest 20 = Lowest</i>'],
                [dp:126, name:'requestToSendSomeone',   type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0', scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',  title:'<b>Request to send someone</b>', description:'<i>Request to send someone</i>'], // to be checked/clarified
                [dp:176, name:'patternChanges',         type:'number',  rw: 'ro', min:1,   max:5,     defVal:1,   scale:1,   unit:'',  title:'<b>Pattern Changes</b>', description:'<i>Pattern changes</i>'],    // to be checked/clarified
                [dp:181, name:'illuminance',            type:'number',  rw: 'ro', min:0,   max:10000, scale:1,    unit:'lx', description:'illuminance'],
                [dp:182, name:'distance',               type:'decimal', rw: 'ro', min:0.0, max:10.0,  defVal:0.0, scale:100, unit:'meters',  description:'Distance to target'],
                [dp:183, name:'distanceIntervalData',   type:'number',  rw: 'ro', min:0,   max:10000, scale:1,    unit:'',   description:'Distance interval data'],     // to be checked/clarified
            ],
            //spammyDPsToIgnore : [182],
            //spammyDPsToNotTrace : [182],
            deviceJoinName: 'Aubess Human Presence Detector O7OE4N9A',
            configuration : [:]
    ],
    */
    /*
    'OWON_OCP305_RADAR'   : [
            description   : 'OWON OCP305 Radar',
            models        : ['OCP305'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'Battery': true],
            preferences   : [:],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0406', outClusters:'0003', model:'OCP305', manufacturer:'OWON']
            ],
            deviceJoinName: 'OWON OCP305 Radar',
            configuration : ['0x0406':'bind']
    ],
    */
    // isSONOFF()
    'SONOFF_SNZB-06P_RADAR' : [
            description   : 'SONOFF SNZB-06P RADAR',
            models        : ['SONOFF'],
            device        : [type: 'radar', powerSource: 'dc', isIAS:false, isSleepy:false],
            capabilities  : ['MotionSensor': true],
            preferences   : ['fadingTime':'0x0406:0x0020', 'radarSensitivity':'0x0406:0x0022'],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0406,0500,FC57,FC11', outClusters:'0003,0019', model:'SNZB-06P', manufacturer:'SONOFF', deviceJoinName: 'SONOFF SNZB-06P RADAR']      // https://community.hubitat.com/t/sonoff-zigbee-human-presence-sensor-snzb-06p/126128/14?u=kkossev
            ],
            attributes:       [
                [at:'0x0406:0x0000', name:'motion',           type:'enum',             rw: 'ro', min:0,  max:1,   defVal:'0',  scale:1,         map:[0:'inactive', 1:'active'] ,   unit:'',  title:'<b>Occupancy state</b>', description:'<i>Occupancy state</i>'],
                [at:'0x0406:0x0022', name:'radarSensitivity', type:'enum', dt: '0x20', rw: 'rw', min:1,  max:3,   defVal:'2',  scale:1, unit:'',        map:[1:'1 - low', 2:'2 - medium', 3:'3 - high'], title:'<b>Radar Sensitivity</b>',   description:'<i>Radar Sensitivity</i>'],
                [at:'0x0406:0x0020', name:'fadingTime',       type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'60', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'<i>Radar fading time in seconds</i>'],
                [at:'0xFC11:0x2001', name:'illumState',       type:'enum', dt: '0x20', mfgCode: '0x1286', rw: 'ro', min:0,  max:2,   defVal:2, scale:1,  unit:'',   map:[0:'dark', 1:'light', 2:'unknown'], title:'<b>Illuminance State</b>',   description:'<i>Illuminance State</i>']
            ],
            refresh: ['refreshSonoff'],
            deviceJoinName: 'SONOFF SNZB-06P RADAR',
            configuration : ['0x0406':'bind', '0x0FC57':'bind'/*, "0xFC11":"bind"*/]
    ]
]

// called from processFoundItem() for Linptech radar
Integer skipIfDisabled(int val) {
    if (settings.ignoreDistance == true) {
        logTrace "skipIfDisabled: ignoring distance attribute"
        return null
    }
    return val
}


void parseIasMessage(final String description) {
    // https://developer.tuya.com/en/docs/iot-device-dev/tuya-zigbee-water-sensor-access-standard?id=K9ik6zvon7orn
    Map zs = zigbee.parseZoneStatusChange(description)
    if (zs.alarm1Set == true) {
        handleMotion(true)
    }
    else {
        handleMotion(false)
    }
}

void handleMotion(final boolean motionActive, final boolean isDigital=false) {
    boolean motionActiveCopy = motionActive
    if (settings.invertMotion == true) {
        motionActiveCopy = !motionActiveCopy
    }
    if (motionActiveCopy) {
        int timeout = motionResetTimer ?: 0
        // If the sensor only sends a motion detected message, the reset to motion inactive must be  performed in code
        if (settings.motionReset == true && timeout != 0) {
            runIn(timeout, resetToMotionInactive, [overwrite: true])
        }
        if (device.currentState('motion')?.value != 'active') {
            state.motionStarted = unix2formattedDate(now()/*.toString()*/)
        }
    }
    else {
        if (device.currentState('motion')?.value == 'inactive') {
            logDebug "ignored motion inactive event after ${getSecondsInactive()}s"
            return      // do not process a second motion inactive event!
        }
    }
    sendMotionEvent(motionActiveCopy, isDigital)
}

void sendMotionEvent(final boolean motionActive, boolean isDigital=false) {
    String descriptionText = 'Detected motion'
    if (motionActive) {
        descriptionText = device.currentValue('motion') == 'active' ? "Motion is active ${getSecondsInactive()}s" : 'Detected motion'
    }
    else {
        descriptionText = "Motion reset to inactive after ${getSecondsInactive()}s"
    }
    /*
    if (isBlackSquareRadar() && device.currentValue("motion", true) == "active" && (motionActive as boolean) == true) {    // TODO - obsolete
        return    // the black square radar sends 'motion active' every 4 seconds!
    }
    */
    if (txtEnable) log.info "${device.displayName} ${descriptionText}"
    sendEvent(
            name            : 'motion',
            value            : motionActive ? 'active' : 'inactive',
            type            : isDigital == true ? 'digital' : 'physical',
            descriptionText : descriptionText
    )
    //runIn(1, formatAttrib, [overwrite: true])
}

void resetToMotionInactive() {
    if (device.currentState('motion')?.value == 'active') {
        String descText = "Motion reset to inactive after ${getSecondsInactive()}s (software timeout)"
        sendEvent(
            name : 'motion',
            value : 'inactive',
            isStateChange : true,
            type:  'digital',
            descriptionText : descText
        )
        if (txtEnable) log.info "${device.displayName} ${descText}"
    }
    else {
        if (txtEnable) log.debug "${device.displayName} ignored resetToMotionInactive (software timeout) after ${getSecondsInactive()}s"
    }
}

int getSecondsInactive() {
    Long unixTime = formattedDate2unix(state.motionStarted)
    if (unixTime) { return Math.round((now() - unixTime) / 1000) }
    return settings?.motionResetTimer ?: 0
}

boolean customProcessTuyaDp(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) {
    return false
}

void customParseE002Cluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseE002Cluster: zigbee received 0xE002 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logWarn "customParseE002Cluster: received unknown 0xE002 attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

void customParseFC11Cluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseFC11Cluster: zigbee received 0xFC11 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logWarn "customParseFC11Cluster: received unknown 0xFC11 attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}
void customParseOccupancyCluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseOccupancyCluster: zigbee received cluster 0x0406 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logWarn "customParseOccupancyCluster: received unknown 0x0406 attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

void customParseEC03Cluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseEC03Cluster: zigbee received unknown cluster 0xEC03 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
}

List<String> refreshSonoff() {
    logDebug "refreshSonoff()"
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0406, 0x0022, [:], delay = 100)    // radarSensitivity
    cmds += zigbee.readAttribute(0x0406, 0x0020, [:], delay = 100)    // fadingTime
    cmds += zigbee.readAttribute(0xFC11, 0x2001, [mfgCode: 0x1286], delay = 100)    // dark/light   mfgCode:'0x1286',
    return cmds
}

List<String> customRefresh() {
    logDebug "customRefresh()"
    List<String> cmds = []
    if (getDeviceProfile() == 'SONOFF_SNZB-06P_RADAR') {
        cmds += refreshSonoff()
    }
    return cmds
}

void customUpdated() {
    logDebug "customUpdated()"
    List<String> cmds = []
    if ('DistanceMeasurement' in DEVICE?.capabilities) {
        if (settings?.ignoreDistance == true) {
            device.deleteCurrentState('distance')
            logDebug "customUpdated: deleted distance state"
        }
        else {
            logDebug "customUpdated: ignoreDistance is ${settings?.ignoreDistance}"
        }
    }
    // Itterates through all settings
    cmds += updateAllPreferences()
    sendZigbeeCommands(cmds)
    if (getDeviceProfile() == 'SONOFF_SNZB-06P_RADAR') {
        setRefreshRequest() 
        runIn(2, refreshSonoff, [overwrite: true])
    }
}

void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (state.deviceProfile == null) {
        setDeviceNameAndProfile()               // in deviceProfileiLib.groovy
    }
    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
    if (fullInit == true || settings?.ignoreDistance == null) { device.updateSetting('ignoreDistance', true) }

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
 * ver. 3.1.1  2024-04-19 kkossev  - (dev. branch) deviceProfilesV3 bug fix; tuyaDPs list of maps bug fix; // library marker kkossev.deviceProfileLib, line 31
 * // library marker kkossev.deviceProfileLib, line 32
 *                                   TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 33
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 34
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 35
 * // library marker kkossev.deviceProfileLib, line 36
*/ // library marker kkossev.deviceProfileLib, line 37

static String deviceProfileLibVersion()   { '3.1.1' } // library marker kkossev.deviceProfileLib, line 39
static String deviceProfileLibStamp() { '2024/04/21 10:21 AM' } // library marker kkossev.deviceProfileLib, line 40
import groovy.json.* // library marker kkossev.deviceProfileLib, line 41
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 42
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 43
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 44
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 45

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 47

metadata { // library marker kkossev.deviceProfileLib, line 49
    // no capabilities // library marker kkossev.deviceProfileLib, line 50
    // no attributes // library marker kkossev.deviceProfileLib, line 51
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 52
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 53
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 54
    ] // library marker kkossev.deviceProfileLib, line 55
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 56
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 57
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 58
    ] // library marker kkossev.deviceProfileLib, line 59

    preferences { // library marker kkossev.deviceProfileLib, line 61
        // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 62
        if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 63
            (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 64
                if (inputIt(key) != null) { // library marker kkossev.deviceProfileLib, line 65
                    input inputIt(key) // library marker kkossev.deviceProfileLib, line 66
                } // library marker kkossev.deviceProfileLib, line 67
            } // library marker kkossev.deviceProfileLib, line 68
            if (('motionReset' in DEVICE?.preferences) && (DEVICE?.preferences.motionReset == true)) { // library marker kkossev.deviceProfileLib, line 69
                input(name: 'motionReset', type: 'bool', title: '<b>Reset Motion to Inactive</b>', description: '<i>Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b></i>', defaultValue: false) // library marker kkossev.deviceProfileLib, line 70
                if (motionReset.value == true) { // library marker kkossev.deviceProfileLib, line 71
                    input('motionResetTimer', 'number', title: '<b>Motion Reset Timer</b>', description: '<i>After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds</i>', range: '0..7200', defaultValue: 60) // library marker kkossev.deviceProfileLib, line 72
                } // library marker kkossev.deviceProfileLib, line 73
            } // library marker kkossev.deviceProfileLib, line 74
        } // library marker kkossev.deviceProfileLib, line 75
        if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 76
            input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: '<i>Forcely change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!</i>',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 77
        } // library marker kkossev.deviceProfileLib, line 78
    } // library marker kkossev.deviceProfileLib, line 79
} // library marker kkossev.deviceProfileLib, line 80

boolean is2in1() { return getDeviceProfile().contains('TS0601_2IN1') } // library marker kkossev.deviceProfileLib, line 82

String  getDeviceProfile()       { state.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 84
Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2[getDeviceProfile()] } // library marker kkossev.deviceProfileLib, line 85
Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2?.keySet() } // library marker kkossev.deviceProfileLib, line 86
List<String> getDeviceProfilesMap()   { deviceProfilesV3 != null ? deviceProfilesV3.values().description as List<String> : deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 87

// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 89

/** // library marker kkossev.deviceProfileLib, line 91
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 92
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 93
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 94
 */ // library marker kkossev.deviceProfileLib, line 95
String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 96
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 97
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 98
    else { return null } // library marker kkossev.deviceProfileLib, line 99
} // library marker kkossev.deviceProfileLib, line 100

/** // library marker kkossev.deviceProfileLib, line 102
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 103
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 104
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 105
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 106
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 107
 */ // library marker kkossev.deviceProfileLib, line 108
Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 109
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 110
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 111
        if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 112
        return [:] // library marker kkossev.deviceProfileLib, line 113
    } // library marker kkossev.deviceProfileLib, line 114
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 115
    def preference // library marker kkossev.deviceProfileLib, line 116
    try { // library marker kkossev.deviceProfileLib, line 117
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 118
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 119
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 120
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 121
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 122
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 123
        } // library marker kkossev.deviceProfileLib, line 124
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 125
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 126
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 127
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 128
        } // library marker kkossev.deviceProfileLib, line 129
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 130
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 131
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 132
        } // library marker kkossev.deviceProfileLib, line 133
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 134
    } catch (e) { // library marker kkossev.deviceProfileLib, line 135
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 136
        return [:] // library marker kkossev.deviceProfileLib, line 137
    } // library marker kkossev.deviceProfileLib, line 138
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 139
    return foundMap // library marker kkossev.deviceProfileLib, line 140
} // library marker kkossev.deviceProfileLib, line 141

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 143
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 144
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 145
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 146
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 147
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 148
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 149
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 150
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 151
            return foundMap // library marker kkossev.deviceProfileLib, line 152
        } // library marker kkossev.deviceProfileLib, line 153
    } // library marker kkossev.deviceProfileLib, line 154
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 155
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 156
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 157
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 158
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 159
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 160
            return foundMap // library marker kkossev.deviceProfileLib, line 161
        } // library marker kkossev.deviceProfileLib, line 162
    } // library marker kkossev.deviceProfileLib, line 163
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 164
    return [:] // library marker kkossev.deviceProfileLib, line 165
} // library marker kkossev.deviceProfileLib, line 166

/** // library marker kkossev.deviceProfileLib, line 168
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 169
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 170
 */ // library marker kkossev.deviceProfileLib, line 171
void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 172
    logTrace "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 173
    Map preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 174
    if (preferences == null || preferences.isEmpty()) { // library marker kkossev.deviceProfileLib, line 175
        logDebug 'Preferences not found!' // library marker kkossev.deviceProfileLib, line 176
        return // library marker kkossev.deviceProfileLib, line 177
    } // library marker kkossev.deviceProfileLib, line 178
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 179
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 180
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 181
        // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 182
        if (mapValue in [true, false]) { // library marker kkossev.deviceProfileLib, line 183
            logDebug "Preference ${parName} is predefined -> (${mapValue})" // library marker kkossev.deviceProfileLib, line 184
            // TODO - set the predefined value // library marker kkossev.deviceProfileLib, line 185
            /* // library marker kkossev.deviceProfileLib, line 186
            if (debug) log.info "par ${parName} defVal = ${parMap.defVal}" // library marker kkossev.deviceProfileLib, line 187
            device.updateSetting("${parMap.name}",[value:parMap.defVal, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 188
            */ // library marker kkossev.deviceProfileLib, line 189
            return // continue // library marker kkossev.deviceProfileLib, line 190
        } // library marker kkossev.deviceProfileLib, line 191
        // find the individual preference map // library marker kkossev.deviceProfileLib, line 192
        parMap = getPreferencesMapByName(parName, false) // library marker kkossev.deviceProfileLib, line 193
        if (parMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 194
            logDebug "Preference ${parName} not found in tuyaDPs or attributes map!" // library marker kkossev.deviceProfileLib, line 195
            return // continue // library marker kkossev.deviceProfileLib, line 196
        } // library marker kkossev.deviceProfileLib, line 197
        // parMap = [at:0xE002:0xE005, name:staticDetectionSensitivity, type:number, dt:UINT8, rw:rw, min:0, max:5, scale:1, unit:x, title:Static Detection Sensitivity, description:Static detection sensitivity] // library marker kkossev.deviceProfileLib, line 198
        if (parMap.defVal == null) { // library marker kkossev.deviceProfileLib, line 199
            logDebug "no default value for preference ${parName} !" // library marker kkossev.deviceProfileLib, line 200
            return // continue // library marker kkossev.deviceProfileLib, line 201
        } // library marker kkossev.deviceProfileLib, line 202
        if (debug) { log.info "par ${parName} defVal = ${parMap.defVal}" } // library marker kkossev.deviceProfileLib, line 203
        device.updateSetting("${parMap.name}", [value:parMap.defVal, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 204
    } // library marker kkossev.deviceProfileLib, line 205
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 206
} // library marker kkossev.deviceProfileLib, line 207

/** // library marker kkossev.deviceProfileLib, line 209
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 210
 * // library marker kkossev.deviceProfileLib, line 211
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 212
 */ // library marker kkossev.deviceProfileLib, line 213
List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 214
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 215
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 216
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 217
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 218
    } // library marker kkossev.deviceProfileLib, line 219
    return validPars // library marker kkossev.deviceProfileLib, line 220
} // library marker kkossev.deviceProfileLib, line 221

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 223
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 224
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 225
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 226
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 227
    def scaledValue // library marker kkossev.deviceProfileLib, line 228
    if (value == null) { // library marker kkossev.deviceProfileLib, line 229
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 230
        return null // library marker kkossev.deviceProfileLib, line 231
    } // library marker kkossev.deviceProfileLib, line 232
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 233
        case 'number' : // library marker kkossev.deviceProfileLib, line 234
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 235
            break // library marker kkossev.deviceProfileLib, line 236
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 237
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 238
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 239
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 240
            } // library marker kkossev.deviceProfileLib, line 241
            break // library marker kkossev.deviceProfileLib, line 242
        case 'bool' : // library marker kkossev.deviceProfileLib, line 243
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 244
            break // library marker kkossev.deviceProfileLib, line 245
        case 'enum' : // library marker kkossev.deviceProfileLib, line 246
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 247
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 248
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 249
                return null // library marker kkossev.deviceProfileLib, line 250
            } // library marker kkossev.deviceProfileLib, line 251
            scaledValue = value // library marker kkossev.deviceProfileLib, line 252
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 253
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 254
            } // library marker kkossev.deviceProfileLib, line 255
            break // library marker kkossev.deviceProfileLib, line 256
        default : // library marker kkossev.deviceProfileLib, line 257
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 258
            return null // library marker kkossev.deviceProfileLib, line 259
    } // library marker kkossev.deviceProfileLib, line 260
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 261
    return scaledValue // library marker kkossev.deviceProfileLib, line 262
} // library marker kkossev.deviceProfileLib, line 263

// called from updated() method // library marker kkossev.deviceProfileLib, line 265
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 266
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 267
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 268
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 269
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 270
        return // library marker kkossev.deviceProfileLib, line 271
    } // library marker kkossev.deviceProfileLib, line 272
    //Integer dpInt = 0 // library marker kkossev.deviceProfileLib, line 273
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 274
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 275
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 276
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 277
        Map foundMap // library marker kkossev.deviceProfileLib, line 278
        foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 279
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 280

        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 282
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 283
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 284
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 285
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 286
                // scale the value // library marker kkossev.deviceProfileLib, line 287
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 288
            } // library marker kkossev.deviceProfileLib, line 289
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLib, line 290
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLib, line 291
            } // library marker kkossev.deviceProfileLib, line 292
            else { // library marker kkossev.deviceProfileLib, line 293
                logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" // library marker kkossev.deviceProfileLib, line 294
                return // library marker kkossev.deviceProfileLib, line 295
            } // library marker kkossev.deviceProfileLib, line 296
        } // library marker kkossev.deviceProfileLib, line 297
        else { // library marker kkossev.deviceProfileLib, line 298
            logDebug "warning: couldn't find map for preference ${name}" // library marker kkossev.deviceProfileLib, line 299
            return // library marker kkossev.deviceProfileLib, line 300
        } // library marker kkossev.deviceProfileLib, line 301
    } // library marker kkossev.deviceProfileLib, line 302
    return // library marker kkossev.deviceProfileLib, line 303
} // library marker kkossev.deviceProfileLib, line 304

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 306
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 307
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 308
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 309
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 310
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 311
} // library marker kkossev.deviceProfileLib, line 312
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 313
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 314
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 315
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 316
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 317
} // library marker kkossev.deviceProfileLib, line 318

/** // library marker kkossev.deviceProfileLib, line 320
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 321
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 322
 * // library marker kkossev.deviceProfileLib, line 323
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 324
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 325
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 326
 */ // library marker kkossev.deviceProfileLib, line 327
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 328
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 329
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 330
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 331
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 332
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 333
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 334
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 335
        case 'number' : // library marker kkossev.deviceProfileLib, line 336
            value = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 337
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 338
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 339
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 340
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 341
            } // library marker kkossev.deviceProfileLib, line 342
            else { // library marker kkossev.deviceProfileLib, line 343
                scaledValue = value // library marker kkossev.deviceProfileLib, line 344
            } // library marker kkossev.deviceProfileLib, line 345
            break // library marker kkossev.deviceProfileLib, line 346

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 348
            value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 349
            // scale the value // library marker kkossev.deviceProfileLib, line 350
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 351
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 352
            } // library marker kkossev.deviceProfileLib, line 353
            else { // library marker kkossev.deviceProfileLib, line 354
                scaledValue = value // library marker kkossev.deviceProfileLib, line 355
            } // library marker kkossev.deviceProfileLib, line 356
            break // library marker kkossev.deviceProfileLib, line 357

        case 'bool' : // library marker kkossev.deviceProfileLib, line 359
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 360
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 361
            else { // library marker kkossev.deviceProfileLib, line 362
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 363
                return null // library marker kkossev.deviceProfileLib, line 364
            } // library marker kkossev.deviceProfileLib, line 365
            break // library marker kkossev.deviceProfileLib, line 366
        case 'enum' : // library marker kkossev.deviceProfileLib, line 367
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 368
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 369
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 370
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 371
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 372
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 373
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 374
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 375
            } // library marker kkossev.deviceProfileLib, line 376
            else { // library marker kkossev.deviceProfileLib, line 377
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 378
            } // library marker kkossev.deviceProfileLib, line 379
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 380
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 381
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 382
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 383
                // find the key for the value // library marker kkossev.deviceProfileLib, line 384
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 385
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 386
                if (key == null) { // library marker kkossev.deviceProfileLib, line 387
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 388
                    return null // library marker kkossev.deviceProfileLib, line 389
                } // library marker kkossev.deviceProfileLib, line 390
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 391
            //return null // library marker kkossev.deviceProfileLib, line 392
            } // library marker kkossev.deviceProfileLib, line 393
            break // library marker kkossev.deviceProfileLib, line 394
        default : // library marker kkossev.deviceProfileLib, line 395
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 396
            return null // library marker kkossev.deviceProfileLib, line 397
    } // library marker kkossev.deviceProfileLib, line 398
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 399
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 400
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 401
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 402
        return null // library marker kkossev.deviceProfileLib, line 403
    } // library marker kkossev.deviceProfileLib, line 404
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 405
    return scaledValue // library marker kkossev.deviceProfileLib, line 406
} // library marker kkossev.deviceProfileLib, line 407

/** // library marker kkossev.deviceProfileLib, line 409
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 410
 * // library marker kkossev.deviceProfileLib, line 411
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 412
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 413
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 414
 */ // library marker kkossev.deviceProfileLib, line 415
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 416
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 417
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 418
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 419
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 420
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 421
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 422
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 423
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 424
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 425
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 426
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 427
    if (scaledValue == null) { logInfo "setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 428
    /* // library marker kkossev.deviceProfileLib, line 429
    // update the device setting // TODO: decide whether the setting must be updated here, or after it is echeod back from the device // library marker kkossev.deviceProfileLib, line 430
    try { // library marker kkossev.deviceProfileLib, line 431
        device.updateSetting("$par", [value:val, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 432
    } // library marker kkossev.deviceProfileLib, line 433
    catch (e) { // library marker kkossev.deviceProfileLib, line 434
        logWarn "setPar: Exception '${e}'caught while updateSetting <b>$par</b>(<b>$val</b>) type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 435
        return false // library marker kkossev.deviceProfileLib, line 436
    } // library marker kkossev.deviceProfileLib, line 437
    */ // library marker kkossev.deviceProfileLib, line 438
    //logDebug "setPar: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 439
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 440
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 441
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 442
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 443
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 444
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 445
        try { // library marker kkossev.deviceProfileLib, line 446
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 447
        } // library marker kkossev.deviceProfileLib, line 448
        catch (e) { // library marker kkossev.deviceProfileLib, line 449
            logWarn "setPar: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 450
            return false // library marker kkossev.deviceProfileLib, line 451
        } // library marker kkossev.deviceProfileLib, line 452
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 453
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 454
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 455
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 456
            return false // library marker kkossev.deviceProfileLib, line 457
        } // library marker kkossev.deviceProfileLib, line 458
        else { // library marker kkossev.deviceProfileLib, line 459
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 460
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 461
        } // library marker kkossev.deviceProfileLib, line 462
    } // library marker kkossev.deviceProfileLib, line 463
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 464
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 465
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 466
        def valMiscType // library marker kkossev.deviceProfileLib, line 467
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 468
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 469
            // find the key for the value // library marker kkossev.deviceProfileLib, line 470
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 471
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 472
            if (key == null) { // library marker kkossev.deviceProfileLib, line 473
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 474
                return false // library marker kkossev.deviceProfileLib, line 475
            } // library marker kkossev.deviceProfileLib, line 476
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 477
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 478
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 479
        } // library marker kkossev.deviceProfileLib, line 480
        else { // library marker kkossev.deviceProfileLib, line 481
            valMiscType = val // library marker kkossev.deviceProfileLib, line 482
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 483
        } // library marker kkossev.deviceProfileLib, line 484
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 485
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 486
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 487
        return true // library marker kkossev.deviceProfileLib, line 488
    } // library marker kkossev.deviceProfileLib, line 489

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 491
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 492

    try { // library marker kkossev.deviceProfileLib, line 494
        // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 495
        /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 496
        isTuyaDP = dpMap.dp instanceof Number // library marker kkossev.deviceProfileLib, line 497
    } // library marker kkossev.deviceProfileLib, line 498
    catch (e) { // library marker kkossev.deviceProfileLib, line 499
        logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" // library marker kkossev.deviceProfileLib, line 500
        isTuyaDP = false // library marker kkossev.deviceProfileLib, line 501
    //return false // library marker kkossev.deviceProfileLib, line 502
    } // library marker kkossev.deviceProfileLib, line 503
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 504
        // Tuya DP // library marker kkossev.deviceProfileLib, line 505
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 506
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 507
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 508
            return false // library marker kkossev.deviceProfileLib, line 509
        } // library marker kkossev.deviceProfileLib, line 510
        else { // library marker kkossev.deviceProfileLib, line 511
            logInfo "setPar: (2) successfluly executed setPar <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 512
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 513
            return false // library marker kkossev.deviceProfileLib, line 514
        } // library marker kkossev.deviceProfileLib, line 515
    } // library marker kkossev.deviceProfileLib, line 516
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 517
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 518
        int cluster // library marker kkossev.deviceProfileLib, line 519
        int attribute // library marker kkossev.deviceProfileLib, line 520
        int dt // library marker kkossev.deviceProfileLib, line 521
        String mfgCode // library marker kkossev.deviceProfileLib, line 522
        try { // library marker kkossev.deviceProfileLib, line 523
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[0]) // library marker kkossev.deviceProfileLib, line 524
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 525
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[1]) // library marker kkossev.deviceProfileLib, line 526
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 527
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 528
            //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 529
            mfgCode = dpMap.mfgCode // library marker kkossev.deviceProfileLib, line 530
        //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 531
        } // library marker kkossev.deviceProfileLib, line 532
        catch (e) { // library marker kkossev.deviceProfileLib, line 533
            logWarn "setPar: Exception '${e}' caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 534
            return false // library marker kkossev.deviceProfileLib, line 535
        } // library marker kkossev.deviceProfileLib, line 536
        Map mapMfCode = ['mfgCode':mfgCode] // library marker kkossev.deviceProfileLib, line 537
        logDebug "setPar: found cluster=0x${zigbee.convertToHexString(cluster, 2)} attribute=0x${zigbee.convertToHexString(attribute, 2)} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 538
        if (mfgCode != null) { // library marker kkossev.deviceProfileLib, line 539
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay = 200) // library marker kkossev.deviceProfileLib, line 540
        } // library marker kkossev.deviceProfileLib, line 541
        else { // library marker kkossev.deviceProfileLib, line 542
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 543
        } // library marker kkossev.deviceProfileLib, line 544
    } // library marker kkossev.deviceProfileLib, line 545
    else { // library marker kkossev.deviceProfileLib, line 546
        logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 547
        return false // library marker kkossev.deviceProfileLib, line 548
    } // library marker kkossev.deviceProfileLib, line 549
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 550
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 551
    return true // library marker kkossev.deviceProfileLib, line 552
} // library marker kkossev.deviceProfileLib, line 553

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 555
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 556
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 557
List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 558
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 559
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 560
    if (dpMap == null) { // library marker kkossev.deviceProfileLib, line 561
        logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 562
        return [] // library marker kkossev.deviceProfileLib, line 563
    } // library marker kkossev.deviceProfileLib, line 564
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 565
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 566
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 567
        return [] // library marker kkossev.deviceProfileLib, line 568
    } // library marker kkossev.deviceProfileLib, line 569
    String dpType // library marker kkossev.deviceProfileLib, line 570
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 571
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 572
    } // library marker kkossev.deviceProfileLib, line 573
    else { // library marker kkossev.deviceProfileLib, line 574
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 575
    } // library marker kkossev.deviceProfileLib, line 576
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 577
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 578
        return [] // library marker kkossev.deviceProfileLib, line 579
    } // library marker kkossev.deviceProfileLib, line 580
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 581
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 582
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 583
    cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 584
    return cmds // library marker kkossev.deviceProfileLib, line 585
} // library marker kkossev.deviceProfileLib, line 586

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 588
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 589
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 590
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 591
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 592
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 593

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 595
    if (dpMap == null || dpMap.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 596
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 597
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 598
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 599
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 600
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 601
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 602
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 603
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 604
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 605
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 606
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 607
        try { // library marker kkossev.deviceProfileLib, line 608
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 609
        } // library marker kkossev.deviceProfileLib, line 610
        catch (e) { // library marker kkossev.deviceProfileLib, line 611
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 612
            return false // library marker kkossev.deviceProfileLib, line 613
        } // library marker kkossev.deviceProfileLib, line 614
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 615
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 616
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 617
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 618
            return true // library marker kkossev.deviceProfileLib, line 619
        } // library marker kkossev.deviceProfileLib, line 620
        else { // library marker kkossev.deviceProfileLib, line 621
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 622
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 623
        } // library marker kkossev.deviceProfileLib, line 624
    } // library marker kkossev.deviceProfileLib, line 625
    else { // library marker kkossev.deviceProfileLib, line 626
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 627
    } // library marker kkossev.deviceProfileLib, line 628
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 629
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 630
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 631
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 632
        // patch !! // library marker kkossev.deviceProfileLib, line 633
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 634
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 635
        } // library marker kkossev.deviceProfileLib, line 636
        else { // library marker kkossev.deviceProfileLib, line 637
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 638
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 639
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 640
        } // library marker kkossev.deviceProfileLib, line 641
        return true // library marker kkossev.deviceProfileLib, line 642
    } // library marker kkossev.deviceProfileLib, line 643
    else { // library marker kkossev.deviceProfileLib, line 644
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 645
    } // library marker kkossev.deviceProfileLib, line 646
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 647
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 648
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 649
    try { // library marker kkossev.deviceProfileLib, line 650
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 651
    } // library marker kkossev.deviceProfileLib, line 652
    catch (e) { // library marker kkossev.deviceProfileLib, line 653
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 654
        return false // library marker kkossev.deviceProfileLib, line 655
    } // library marker kkossev.deviceProfileLib, line 656
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 657
        // Tuya DP // library marker kkossev.deviceProfileLib, line 658
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 659
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 660
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 661
            return false // library marker kkossev.deviceProfileLib, line 662
        } // library marker kkossev.deviceProfileLib, line 663
        else { // library marker kkossev.deviceProfileLib, line 664
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 665
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 666
            return true // library marker kkossev.deviceProfileLib, line 667
        } // library marker kkossev.deviceProfileLib, line 668
    } // library marker kkossev.deviceProfileLib, line 669
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 670
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 671
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 672
    } // library marker kkossev.deviceProfileLib, line 673
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 674
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 675
        int cluster // library marker kkossev.deviceProfileLib, line 676
        int attribute // library marker kkossev.deviceProfileLib, line 677
        int dt // library marker kkossev.deviceProfileLib, line 678
        // int mfgCode // library marker kkossev.deviceProfileLib, line 679
        try { // library marker kkossev.deviceProfileLib, line 680
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[0]) // library marker kkossev.deviceProfileLib, line 681
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 682
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[1]) // library marker kkossev.deviceProfileLib, line 683
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 684
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 685
        //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 686
        //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 687
        //  mfgCode = dpMap.mfgCode != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.mfgCode) : null // library marker kkossev.deviceProfileLib, line 688
        //  log.trace "mfgCode = ${mfgCode}" // library marker kkossev.deviceProfileLib, line 689
        } // library marker kkossev.deviceProfileLib, line 690
        catch (e) { // library marker kkossev.deviceProfileLib, line 691
            logWarn "sendAttribute: Exception '${e}'caught while splitting cluster and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 692
            return false // library marker kkossev.deviceProfileLib, line 693
        } // library marker kkossev.deviceProfileLib, line 694

        logDebug "sendAttribute: found cluster=${cluster} attribute=${attribute} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 696
        if (dpMap.mfgCode != null) { // library marker kkossev.deviceProfileLib, line 697
            Map mapMfCode = ['mfgCode':dpMap.mfgCode] // library marker kkossev.deviceProfileLib, line 698
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay = 200) // library marker kkossev.deviceProfileLib, line 699
        } // library marker kkossev.deviceProfileLib, line 700
        else { // library marker kkossev.deviceProfileLib, line 701
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 702
        } // library marker kkossev.deviceProfileLib, line 703
    } // library marker kkossev.deviceProfileLib, line 704
    else { // library marker kkossev.deviceProfileLib, line 705
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 706
        return false // library marker kkossev.deviceProfileLib, line 707
    } // library marker kkossev.deviceProfileLib, line 708
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 709
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 710
    return true // library marker kkossev.deviceProfileLib, line 711
} // library marker kkossev.deviceProfileLib, line 712

/** // library marker kkossev.deviceProfileLib, line 714
 * Sends a command to the device. // library marker kkossev.deviceProfileLib, line 715
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 716
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 717
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 718
 */ // library marker kkossev.deviceProfileLib, line 719
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 720
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 721
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 722
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 723
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 724
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 725
    if (supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 726
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 727
        return false // library marker kkossev.deviceProfileLib, line 728
    } // library marker kkossev.deviceProfileLib, line 729
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 730
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 731
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 732
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 733
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 734
        return false // library marker kkossev.deviceProfileLib, line 735
    } // library marker kkossev.deviceProfileLib, line 736
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 737
    def func // library marker kkossev.deviceProfileLib, line 738
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 739
    def funcResult // library marker kkossev.deviceProfileLib, line 740
    try { // library marker kkossev.deviceProfileLib, line 741
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 742
        if (val != null) { // library marker kkossev.deviceProfileLib, line 743
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 744
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 745
        } // library marker kkossev.deviceProfileLib, line 746
        else { // library marker kkossev.deviceProfileLib, line 747
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 748
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 749
        } // library marker kkossev.deviceProfileLib, line 750
    } // library marker kkossev.deviceProfileLib, line 751
    catch (e) { // library marker kkossev.deviceProfileLib, line 752
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 753
        return false // library marker kkossev.deviceProfileLib, line 754
    } // library marker kkossev.deviceProfileLib, line 755
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 756
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 757
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 758
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 759
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 760
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 761
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 762
        } // library marker kkossev.deviceProfileLib, line 763
    } else { // library marker kkossev.deviceProfileLib, line 764
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 765
        return false // library marker kkossev.deviceProfileLib, line 766
    } // library marker kkossev.deviceProfileLib, line 767
    cmds = funcResult // library marker kkossev.deviceProfileLib, line 768
    if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 769
        sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 770
    } // library marker kkossev.deviceProfileLib, line 771
    return true // library marker kkossev.deviceProfileLib, line 772
} // library marker kkossev.deviceProfileLib, line 773

/** // library marker kkossev.deviceProfileLib, line 775
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 776
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 777
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 778
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 779
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 780
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 781
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 782
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 783
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 784
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 785
 */ // library marker kkossev.deviceProfileLib, line 786
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 787
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 788
    Map input = [:] // library marker kkossev.deviceProfileLib, line 789
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 790
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 791
        if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 792
        return [:] // library marker kkossev.deviceProfileLib, line 793
    } // library marker kkossev.deviceProfileLib, line 794
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 795
    def preference // library marker kkossev.deviceProfileLib, line 796
    try { // library marker kkossev.deviceProfileLib, line 797
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 798
    } // library marker kkossev.deviceProfileLib, line 799
    catch (e) { // library marker kkossev.deviceProfileLib, line 800
        if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 801
        return [:] // library marker kkossev.deviceProfileLib, line 802
    } // library marker kkossev.deviceProfileLib, line 803
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 804
    try { // library marker kkossev.deviceProfileLib, line 805
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 806
            if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } // library marker kkossev.deviceProfileLib, line 807
            return [:] // library marker kkossev.deviceProfileLib, line 808
        } // library marker kkossev.deviceProfileLib, line 809
    } // library marker kkossev.deviceProfileLib, line 810
    catch (e) { // library marker kkossev.deviceProfileLib, line 811
        if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 812
        return [:] // library marker kkossev.deviceProfileLib, line 813
    } // library marker kkossev.deviceProfileLib, line 814

    try { // library marker kkossev.deviceProfileLib, line 816
        isTuyaDP = preference.isNumber() // library marker kkossev.deviceProfileLib, line 817
    } // library marker kkossev.deviceProfileLib, line 818
    catch (e) { // library marker kkossev.deviceProfileLib, line 819
        if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 820
        return [:] // library marker kkossev.deviceProfileLib, line 821
    } // library marker kkossev.deviceProfileLib, line 822

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 824
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 825
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 826
    if (foundMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 827
        if (debug) { log.warn "inputIt: map not found for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 828
        return [:] // library marker kkossev.deviceProfileLib, line 829
    } // library marker kkossev.deviceProfileLib, line 830
    if (foundMap.rw != 'rw') { // library marker kkossev.deviceProfileLib, line 831
        if (debug) { log.warn "inputIt: param '${param}' is read only!" } // library marker kkossev.deviceProfileLib, line 832
        return [:] // library marker kkossev.deviceProfileLib, line 833
    } // library marker kkossev.deviceProfileLib, line 834
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 835
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 836
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 837
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 838
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 839
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 840
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 841
        } // library marker kkossev.deviceProfileLib, line 842
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 843
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 844
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 845
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 846
            } // library marker kkossev.deviceProfileLib, line 847
        } // library marker kkossev.deviceProfileLib, line 848
    } // library marker kkossev.deviceProfileLib, line 849
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 850
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 851
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 852
    }/* // library marker kkossev.deviceProfileLib, line 853
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 854
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 855
    }*/ // library marker kkossev.deviceProfileLib, line 856
    else { // library marker kkossev.deviceProfileLib, line 857
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 858
        return [:] // library marker kkossev.deviceProfileLib, line 859
    } // library marker kkossev.deviceProfileLib, line 860
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 861
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 862
    } // library marker kkossev.deviceProfileLib, line 863
    return input // library marker kkossev.deviceProfileLib, line 864
} // library marker kkossev.deviceProfileLib, line 865

/** // library marker kkossev.deviceProfileLib, line 867
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 868
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 869
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 870
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 871
 */ // library marker kkossev.deviceProfileLib, line 872
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 873
    String deviceName         = UNKNOWN // library marker kkossev.deviceProfileLib, line 874
    String deviceProfile      = UNKNOWN // library marker kkossev.deviceProfileLib, line 875
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 876
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 877
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 878
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 879
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 880
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 881
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 882
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 883
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 884
            } // library marker kkossev.deviceProfileLib, line 885
        } // library marker kkossev.deviceProfileLib, line 886
    } // library marker kkossev.deviceProfileLib, line 887
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 888
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 889
    } // library marker kkossev.deviceProfileLib, line 890
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 891
} // library marker kkossev.deviceProfileLib, line 892

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 894
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 895
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 896
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 897
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 898
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 899
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 900
    } // library marker kkossev.deviceProfileLib, line 901
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 902
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 903
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 904
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 905
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 906
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 907
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 908
    } else { // library marker kkossev.deviceProfileLib, line 909
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 910
    } // library marker kkossev.deviceProfileLib, line 911
} // library marker kkossev.deviceProfileLib, line 912

// TODO! // library marker kkossev.deviceProfileLib, line 914
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 915
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 916
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 917
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 918
    return cmds // library marker kkossev.deviceProfileLib, line 919
} // library marker kkossev.deviceProfileLib, line 920

// TODO ! // library marker kkossev.deviceProfileLib, line 922
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 923
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 924
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 925
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.deviceProfileLib, line 926
    return cmds // library marker kkossev.deviceProfileLib, line 927
} // library marker kkossev.deviceProfileLib, line 928

// TODO // library marker kkossev.deviceProfileLib, line 930
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 931
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 932
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 933
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 934
    return cmds // library marker kkossev.deviceProfileLib, line 935
} // library marker kkossev.deviceProfileLib, line 936

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 938
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 939
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 940
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 941
    } // library marker kkossev.deviceProfileLib, line 942
} // library marker kkossev.deviceProfileLib, line 943

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 945
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 946
} // library marker kkossev.deviceProfileLib, line 947

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 949

// // library marker kkossev.deviceProfileLib, line 951
// called from parse() // library marker kkossev.deviceProfileLib, line 952
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 953
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 954
// // library marker kkossev.deviceProfileLib, line 955
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 956
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 957
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 958
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 959
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 960
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 961
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 962
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 963
} // library marker kkossev.deviceProfileLib, line 964

// // library marker kkossev.deviceProfileLib, line 966
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 967
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 968
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 969
// // library marker kkossev.deviceProfileLib, line 970
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 971
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 972
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 973
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 974
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 975
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 976
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 977
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 978
} // library marker kkossev.deviceProfileLib, line 979

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 981
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 982
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 983
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 984
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 985
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 986
    } // library marker kkossev.deviceProfileLib, line 987
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 988
} // library marker kkossev.deviceProfileLib, line 989

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 991
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 992
    boolean isEqual // library marker kkossev.deviceProfileLib, line 993
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 994
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 995
    } // library marker kkossev.deviceProfileLib, line 996
    else { // library marker kkossev.deviceProfileLib, line 997
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 998
    } // library marker kkossev.deviceProfileLib, line 999
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 1000
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1001
} // library marker kkossev.deviceProfileLib, line 1002

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 1004
    Double convertedValue // library marker kkossev.deviceProfileLib, line 1005
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1006
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 1007
    } // library marker kkossev.deviceProfileLib, line 1008
    else { // library marker kkossev.deviceProfileLib, line 1009
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1010
    } // library marker kkossev.deviceProfileLib, line 1011
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1012
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1013
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1014
} // library marker kkossev.deviceProfileLib, line 1015

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1017
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1018
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1019
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1020
    def convertedValue // library marker kkossev.deviceProfileLib, line 1021
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1022
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1023
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1024
    } // library marker kkossev.deviceProfileLib, line 1025
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1026
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1027
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1028
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1029
            isEqual = false // library marker kkossev.deviceProfileLib, line 1030
        } // library marker kkossev.deviceProfileLib, line 1031
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1032
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1033
        } // library marker kkossev.deviceProfileLib, line 1034
    } // library marker kkossev.deviceProfileLib, line 1035
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1036
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1037
} // library marker kkossev.deviceProfileLib, line 1038

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1040
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1041
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1042
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1043
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1044
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1045
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1046
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1047
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1048
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1049
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1050
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1051
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1052
            break // library marker kkossev.deviceProfileLib, line 1053
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1054
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1055
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1056
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1057
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1058
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1059
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1060
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1061
            } // library marker kkossev.deviceProfileLib, line 1062
            else { // library marker kkossev.deviceProfileLib, line 1063
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1064
            } // library marker kkossev.deviceProfileLib, line 1065
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1066
            break // library marker kkossev.deviceProfileLib, line 1067
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1068
        case 'number' : // library marker kkossev.deviceProfileLib, line 1069
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1070
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1071
            break // library marker kkossev.deviceProfileLib, line 1072
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1073
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1074
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1075
            break // library marker kkossev.deviceProfileLib, line 1076
        default : // library marker kkossev.deviceProfileLib, line 1077
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1078
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1079
    } // library marker kkossev.deviceProfileLib, line 1080
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1081
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1082
    } // library marker kkossev.deviceProfileLib, line 1083
    // // library marker kkossev.deviceProfileLib, line 1084
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1085
} // library marker kkossev.deviceProfileLib, line 1086

// // library marker kkossev.deviceProfileLib, line 1088
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1089
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1090
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1091
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1092
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1093
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1094
// // library marker kkossev.deviceProfileLib, line 1095
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1096
// // library marker kkossev.deviceProfileLib, line 1097
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1098
// // library marker kkossev.deviceProfileLib, line 1099
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1100
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1101
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1102
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1103
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1104
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1105
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1106
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1107
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1108
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1109
            break // library marker kkossev.deviceProfileLib, line 1110
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1111
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1112
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1113
            String dataType = latestEvent?.dataType  // library marker kkossev.deviceProfileLib, line 1114
            logTrace "latestEvent is dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1115
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1116
            if (dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1117
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1118
            } // library marker kkossev.deviceProfileLib, line 1119
            else { // library marker kkossev.deviceProfileLib, line 1120
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1121
            } // library marker kkossev.deviceProfileLib, line 1122
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1123
            break // library marker kkossev.deviceProfileLib, line 1124
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1125
        case 'number' : // library marker kkossev.deviceProfileLib, line 1126
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1127
            break // library marker kkossev.deviceProfileLib, line 1128
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1129
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1130
            break // library marker kkossev.deviceProfileLib, line 1131
        default : // library marker kkossev.deviceProfileLib, line 1132
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1133
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1134
    } // library marker kkossev.deviceProfileLib, line 1135
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1136
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1137
} // library marker kkossev.deviceProfileLib, line 1138

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1140
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1141
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1142
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1143
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1144
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1145
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1146
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1147
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1148
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1149
    } // library marker kkossev.deviceProfileLib, line 1150
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1151
    try { // library marker kkossev.deviceProfileLib, line 1152
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1153
    } // library marker kkossev.deviceProfileLib, line 1154
    catch (e) { // library marker kkossev.deviceProfileLib, line 1155
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1156
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1157
    } // library marker kkossev.deviceProfileLib, line 1158
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1159
    return fncmd // library marker kkossev.deviceProfileLib, line 1160
} // library marker kkossev.deviceProfileLib, line 1161

/** // library marker kkossev.deviceProfileLib, line 1163
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1164
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1165
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1166
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1167
 * // library marker kkossev.deviceProfileLib, line 1168
 * @param descMap The description map of the received DP. // library marker kkossev.deviceProfileLib, line 1169
 * @param dp The value of the received DP. // library marker kkossev.deviceProfileLib, line 1170
 * @param dp_id The ID of the received DP. // library marker kkossev.deviceProfileLib, line 1171
 * @param fncmd The command of the received DP. // library marker kkossev.deviceProfileLib, line 1172
 * @param dp_len The length of the received DP. // library marker kkossev.deviceProfileLib, line 1173
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1174
 */ // library marker kkossev.deviceProfileLib, line 1175
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1176
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1177
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1178
    //if (!(DEVICE?.device?.type == "radar"))      { return false }   // enabled for all devices - 10/22/2023 !!!    // only these models are handled here for now ... // library marker kkossev.deviceProfileLib, line 1179
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1180

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1182
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1183

    Map foundItem = null // library marker kkossev.deviceProfileLib, line 1185
    tuyaDPsMap.each { item -> // library marker kkossev.deviceProfileLib, line 1186
        if (item['dp'] == (dp as int)) { // library marker kkossev.deviceProfileLib, line 1187
            foundItem = item // library marker kkossev.deviceProfileLib, line 1188
            return // library marker kkossev.deviceProfileLib, line 1189
        } // library marker kkossev.deviceProfileLib, line 1190
    } // library marker kkossev.deviceProfileLib, line 1191
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1192
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1193
        updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len) // library marker kkossev.deviceProfileLib, line 1194
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1195
        return false // library marker kkossev.deviceProfileLib, line 1196
    } // library marker kkossev.deviceProfileLib, line 1197

    return processFoundItem(foundItem, fncmd_orig) // library marker kkossev.deviceProfileLib, line 1199
} // library marker kkossev.deviceProfileLib, line 1200

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1202
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1203
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1204
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1205

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1207
    if (attribMap == null || attribMap.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1208

    Map foundItem = null // library marker kkossev.deviceProfileLib, line 1210
    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1211
    int value // library marker kkossev.deviceProfileLib, line 1212
    try { // library marker kkossev.deviceProfileLib, line 1213
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1214
    } // library marker kkossev.deviceProfileLib, line 1215
    catch (e) { // library marker kkossev.deviceProfileLib, line 1216
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1217
        return false // library marker kkossev.deviceProfileLib, line 1218
    } // library marker kkossev.deviceProfileLib, line 1219
    //logTrace "clusterAttribute = ${clusterAttribute}" // library marker kkossev.deviceProfileLib, line 1220
    attribMap.each { item -> // library marker kkossev.deviceProfileLib, line 1221
        if (item['at'] == clusterAttribute) { // library marker kkossev.deviceProfileLib, line 1222
            foundItem = item // library marker kkossev.deviceProfileLib, line 1223
            return // library marker kkossev.deviceProfileLib, line 1224
        } // library marker kkossev.deviceProfileLib, line 1225
    } // library marker kkossev.deviceProfileLib, line 1226
    if (foundItem == null) { // library marker kkossev.deviceProfileLib, line 1227
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1228
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1229
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1230
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1231
        return false // library marker kkossev.deviceProfileLib, line 1232
    } // library marker kkossev.deviceProfileLib, line 1233
    return processFoundItem(foundItem, value) // library marker kkossev.deviceProfileLib, line 1234
} // library marker kkossev.deviceProfileLib, line 1235

// modifies the value of the foundItem if needed !!! // library marker kkossev.deviceProfileLib, line 1237
/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.deviceProfileLib, line 1238
boolean processFoundItem(final Map foundItem, int value) { // library marker kkossev.deviceProfileLib, line 1239
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1240
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1241
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1242
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1243
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1244
        if (preProcValue == null) { // library marker kkossev.deviceProfileLib, line 1245
            logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" // library marker kkossev.deviceProfileLib, line 1246
            return true // library marker kkossev.deviceProfileLib, line 1247
        } // library marker kkossev.deviceProfileLib, line 1248
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1249
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1250
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1251
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1252
        } // library marker kkossev.deviceProfileLib, line 1253
    } // library marker kkossev.deviceProfileLib, line 1254
    else { // library marker kkossev.deviceProfileLib, line 1255
        logTrace "processFoundItem: no preProc for ${foundItem.name}" // library marker kkossev.deviceProfileLib, line 1256
    } // library marker kkossev.deviceProfileLib, line 1257

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1259
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1260
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1261
    //existingPrefValue = existingPrefValue?.replace("[", "").replace("]", "")               // preference name as in Hubitat settings (preferences), if already created. // library marker kkossev.deviceProfileLib, line 1262
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1263
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1264
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1265
    //boolean preferenceExists = settings.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1266
    boolean preferenceExists = DEVICE?.preferences?.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1267
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1268
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1269
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1270
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1271
    boolean doNotTrace = false  // isSpammyDPsToNotTrace(descMap)          // do not log/trace the spammy clusterAttribute's TODO! // library marker kkossev.deviceProfileLib, line 1272
    if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1273
        logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" // library marker kkossev.deviceProfileLib, line 1274
    } // library marker kkossev.deviceProfileLib, line 1275
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1276
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1277
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1278
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1279
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1280
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1281

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1283
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1284
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1285
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1286
            // TODO - scaledValue ????? // library marker kkossev.deviceProfileLib, line 1287
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1288
            if (settings.logEnable) { logInfo "${descText }" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1289
        } // library marker kkossev.deviceProfileLib, line 1290
        // no more processing is needed, as this clusterAttribute is not a preference and not an attribute // library marker kkossev.deviceProfileLib, line 1291
        return true // library marker kkossev.deviceProfileLib, line 1292
    } // library marker kkossev.deviceProfileLib, line 1293

    // first, check if there is a preference defined to be updated // library marker kkossev.deviceProfileLib, line 1295
    if (preferenceExists) { // library marker kkossev.deviceProfileLib, line 1296
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1297
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1298
        //log.trace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1299
        if (isEqual == true) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1300
            logDebug "no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1301
        } // library marker kkossev.deviceProfileLib, line 1302
        else { // library marker kkossev.deviceProfileLib, line 1303
            String scaledPreferenceValue = preferenceValue      //.toString() is not neccessary // library marker kkossev.deviceProfileLib, line 1304
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1305
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1306
            } // library marker kkossev.deviceProfileLib, line 1307
            logDebug "preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1308
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1309
            try { // library marker kkossev.deviceProfileLib, line 1310
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1311
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1312
            } // library marker kkossev.deviceProfileLib, line 1313
            catch (e) { // library marker kkossev.deviceProfileLib, line 1314
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1315
            } // library marker kkossev.deviceProfileLib, line 1316
        } // library marker kkossev.deviceProfileLib, line 1317
    } // library marker kkossev.deviceProfileLib, line 1318
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1319
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1320
        unitText = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1321
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1322
    } // library marker kkossev.deviceProfileLib, line 1323

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1325
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1326
        logTrace "attribute '${name}' exists (type ${foundItem.type})" // library marker kkossev.deviceProfileLib, line 1327
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1328
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1329
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1330
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1331
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1332
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1333
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1334
            } // library marker kkossev.deviceProfileLib, line 1335
            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1336
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1337
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1338
            // continue ... // library marker kkossev.deviceProfileLib, line 1339
            } // library marker kkossev.deviceProfileLib, line 1340
            else { // library marker kkossev.deviceProfileLib, line 1341
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1342
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1343
                } // library marker kkossev.deviceProfileLib, line 1344
                else { // library marker kkossev.deviceProfileLib, line 1345
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1346
                } // library marker kkossev.deviceProfileLib, line 1347
            } // library marker kkossev.deviceProfileLib, line 1348
        } // library marker kkossev.deviceProfileLib, line 1349

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an event! // library marker kkossev.deviceProfileLib, line 1351

        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1353
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1354
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1355
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1356
        if (DEVICE_TYPE in ['Thermostat'])  { processDeviceEventThermostat(name, valueScaled, unitText, descText) } // library marker kkossev.deviceProfileLib, line 1357
        else { // library marker kkossev.deviceProfileLib, line 1358
            switch (name) { // library marker kkossev.deviceProfileLib, line 1359
                case 'motion' : // library marker kkossev.deviceProfileLib, line 1360
                    handleMotion(value as boolean)  // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1361
                    break // library marker kkossev.deviceProfileLib, line 1362
                case 'temperature' : // library marker kkossev.deviceProfileLib, line 1363
                    //temperatureEvent(value / getTemperatureDiv()) // library marker kkossev.deviceProfileLib, line 1364
                    handleTemperatureEvent(valueScaled as Float) // library marker kkossev.deviceProfileLib, line 1365
                    break // library marker kkossev.deviceProfileLib, line 1366
                case 'humidity' : // library marker kkossev.deviceProfileLib, line 1367
                    handleHumidityEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1368
                    break // library marker kkossev.deviceProfileLib, line 1369
                case 'illuminance' : // library marker kkossev.deviceProfileLib, line 1370
                case 'illuminance_lux' : // library marker kkossev.deviceProfileLib, line 1371
                    handleIlluminanceEvent(valueCorrected.toInteger()) // library marker kkossev.deviceProfileLib, line 1372
                    break // library marker kkossev.deviceProfileLib, line 1373
                case 'pushed' : // library marker kkossev.deviceProfileLib, line 1374
                    logDebug "button event received value=${value} valueScaled=${valueScaled} valueCorrected=${valueCorrected}" // library marker kkossev.deviceProfileLib, line 1375
                    buttonEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1376
                    break // library marker kkossev.deviceProfileLib, line 1377
                default : // library marker kkossev.deviceProfileLib, line 1378
                    sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1379
                    if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1380
                        logDebug "event ${name} sent w/ value ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1381
                        logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1382
                    } // library marker kkossev.deviceProfileLib, line 1383
                    break // library marker kkossev.deviceProfileLib, line 1384
            } // library marker kkossev.deviceProfileLib, line 1385
        //logTrace "attrValue=${attrValue} valueScaled=${valueScaled} equal=${isEqual}" // library marker kkossev.deviceProfileLib, line 1386
        } // library marker kkossev.deviceProfileLib, line 1387
    } // library marker kkossev.deviceProfileLib, line 1388
    // all processing was done here! // library marker kkossev.deviceProfileLib, line 1389
    return true // library marker kkossev.deviceProfileLib, line 1390
} // library marker kkossev.deviceProfileLib, line 1391

public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1393
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1394
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 1395
        logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1396
        return false // library marker kkossev.deviceProfileLib, line 1397
    } // library marker kkossev.deviceProfileLib, line 1398
    int validationFailures = 0 // library marker kkossev.deviceProfileLib, line 1399
    int validationFixes = 0 // library marker kkossev.deviceProfileLib, line 1400
    int total = 0 // library marker kkossev.deviceProfileLib, line 1401
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1402
    def oldSettingValue // library marker kkossev.deviceProfileLib, line 1403
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1404
    def newValue // library marker kkossev.deviceProfileLib, line 1405
    String settingType // library marker kkossev.deviceProfileLib, line 1406
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1407
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1408
        if (foundMap == null || foundMap == [:]) { // library marker kkossev.deviceProfileLib, line 1409
            logDebug "validateAndFixPreferences: map not found for preference ${it.key}"    // 10/21/2023 - sevirity lowered to debug // library marker kkossev.deviceProfileLib, line 1410
            return false // library marker kkossev.deviceProfileLib, line 1411
        } // library marker kkossev.deviceProfileLib, line 1412
        settingType = device.getSettingType(it.key) // library marker kkossev.deviceProfileLib, line 1413
        oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1414
        if (settingType == null) { // library marker kkossev.deviceProfileLib, line 1415
            logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" // library marker kkossev.deviceProfileLib, line 1416
            return false // library marker kkossev.deviceProfileLib, line 1417
        } // library marker kkossev.deviceProfileLib, line 1418
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1419
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1420
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1421
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1422
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1423
            try { // library marker kkossev.deviceProfileLib, line 1424
                device.removeSetting(it.key) // library marker kkossev.deviceProfileLib, line 1425
                logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1426
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1427
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1428
                return false // library marker kkossev.deviceProfileLib, line 1429
            } // library marker kkossev.deviceProfileLib, line 1430
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1431
            try { // library marker kkossev.deviceProfileLib, line 1432
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1433
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1434
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1435
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1436
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1437
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1438
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1439
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1440
                    } // library marker kkossev.deviceProfileLib, line 1441
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1442
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1443
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1444
                    } else { // library marker kkossev.deviceProfileLib, line 1445
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1446
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1447
                    } // library marker kkossev.deviceProfileLib, line 1448
                } // library marker kkossev.deviceProfileLib, line 1449
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1450
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1451
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1452
            } // library marker kkossev.deviceProfileLib, line 1453
            catch (e) { // library marker kkossev.deviceProfileLib, line 1454
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1455
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1456
                try { // library marker kkossev.deviceProfileLib, line 1457
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1458
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1459
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1460
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1461
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1462
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" // library marker kkossev.deviceProfileLib, line 1463
                    return false // library marker kkossev.deviceProfileLib, line 1464
                } // library marker kkossev.deviceProfileLib, line 1465
            } // library marker kkossev.deviceProfileLib, line 1466
        } // library marker kkossev.deviceProfileLib, line 1467
        total ++ // library marker kkossev.deviceProfileLib, line 1468
    } // library marker kkossev.deviceProfileLib, line 1469
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1470
    return true // library marker kkossev.deviceProfileLib, line 1471
} // library marker kkossev.deviceProfileLib, line 1472

void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1474
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1475
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1476
            logInfo fingerprint // library marker kkossev.deviceProfileLib, line 1477
        } // library marker kkossev.deviceProfileLib, line 1478
    } // library marker kkossev.deviceProfileLib, line 1479
} // library marker kkossev.deviceProfileLib, line 1480

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~

// ~~~~~ start include (168) kkossev.illuminanceLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.illuminanceLib, line 1
library( // library marker kkossev.illuminanceLib, line 2
    base: 'driver', // library marker kkossev.illuminanceLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.illuminanceLib, line 4
    category: 'zigbee', // library marker kkossev.illuminanceLib, line 5
    description: 'Zigbee Illuminance Library', // library marker kkossev.illuminanceLib, line 6
    name: 'illuminanceLib', // library marker kkossev.illuminanceLib, line 7
    namespace: 'kkossev', // library marker kkossev.illuminanceLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/illuminanceLib.groovy', // library marker kkossev.illuminanceLib, line 9
    version: '3.0.0', // library marker kkossev.illuminanceLib, line 10
    documentationLink: '' // library marker kkossev.illuminanceLib, line 11
) // library marker kkossev.illuminanceLib, line 12
/* // library marker kkossev.illuminanceLib, line 13
 *  Zigbee Illuminance Library // library marker kkossev.illuminanceLib, line 14
 * // library marker kkossev.illuminanceLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.illuminanceLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.illuminanceLib, line 17
 * // library marker kkossev.illuminanceLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.illuminanceLib, line 19
 * // library marker kkossev.illuminanceLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.illuminanceLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.illuminanceLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.illuminanceLib, line 23
 * // library marker kkossev.illuminanceLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added illuminanceLib.groovy // library marker kkossev.illuminanceLib, line 25
 * // library marker kkossev.illuminanceLib, line 26
 *                                   TODO: // library marker kkossev.illuminanceLib, line 27
*/ // library marker kkossev.illuminanceLib, line 28

static String illuminanceLibVersion()   { '3.0.0' } // library marker kkossev.illuminanceLib, line 30
static String illuminanceLibStamp() { '2024/04/06 2:40 PM' } // library marker kkossev.illuminanceLib, line 31

metadata { // library marker kkossev.illuminanceLib, line 33
    // no capabilities // library marker kkossev.illuminanceLib, line 34
    // no attributes // library marker kkossev.illuminanceLib, line 35
    // no commands // library marker kkossev.illuminanceLib, line 36
    preferences { // library marker kkossev.illuminanceLib, line 37
        // no prefrences // library marker kkossev.illuminanceLib, line 38
    } // library marker kkossev.illuminanceLib, line 39
} // library marker kkossev.illuminanceLib, line 40

// ~~~~~ end include (168) kkossev.illuminanceLib ~~~~~
