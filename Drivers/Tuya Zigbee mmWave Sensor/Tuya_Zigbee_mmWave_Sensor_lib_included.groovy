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
 * ver. 3.0.7  2024-04-21 kkossev  - deviceProfilesV3; SNZB-06 data type fix; OccupancyCluster processing; added illumState dark/light;
 * ver. 3.0.8  2024-04-23 kkossev  - added detectionDelay for SNZB-06; refactored the refresh() method; added TS0601_BLACK_SQUARE_RADAR; TS0601_RADAR_MIR-HE200-TY; 
 * ver. 3.1.0  2024-04-26 kkossev  - (dev. branch) commonLib 3.1.0 speed optimization; added TS0601_KAPVNNLK_RADAR
 *
 *                                   TODO: enable the OWON radar configuration : ['0x0406':'bind']
 *                                   TODO: add response to ZDO Match Descriptor Request (Sonoff SNZB-06)
 *                                   TODO: illumState default value is 0 - should be 'unknown' ?
 *                                   TODO: Motion reset to inactive after 43648s - convert to H:M:S
 *                                   TODO: Black Square Radar validateAndFixPreferences: map not found for preference indicatorLight
 *                                   TODO: Linptech spammyDPsToIgnore[] !
 *                                   TODO: command for black radar LED
 *                                   TODO: TS0225_2AAELWXK_RADAR  dont see an attribute as mentioned that shows the distance at which the motion was detected. - https://community.hubitat.com/t/the-new-tuya-human-presence-sensors-ts0225-tze200-hl0ss9oa-tze200-2aaelwxk-have-actually-5-8ghz-modules-inside/122283/294?u=kkossev
 *                                   TODO: TS0225_2AAELWXK_RADAR led setting not working - https://community.hubitat.com/t/the-new-tuya-human-presence-sensors-ts0225-tze200-hl0ss9oa-tze200-2aaelwxk-have-actually-5-8ghz-modules-inside/122283/294?u=kkossev
 *                                   TODO: radars - ignore the change of the presence/motion being turned off when changing parameters for a period of 10 seconds ?
 *                                   TODO: TS0225_HL0SS9OA_RADAR - add presets
 *                                   TODO: humanMotionState - add preference: enum "disabled", "enabled", "enabled w/ timing" ...; add delayed event
*/

static String version() { "3.1.0" }
static String timeStamp() {"2024/04/26 8:03 PM"}

@Field static final Boolean _DEBUG = false
@Field static final Boolean _TRACE_ALL = false      // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true 


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
        attribute 'smallMotionDetectionSensitivity', 'number'   // added 04/25/2024
        attribute 'detectionDelay', 'decimal'
        attribute 'fadingTime', 'decimal'
        attribute 'minimumDistance', 'decimal'
        attribute 'maximumDistance', 'decimal'
        attribute 'radarStatus', 'enum', ['checking', 'check_success', 'check_failure', 'others', 'comm_fault', 'radar_fault']
        attribute 'humanMotionState', 'enum', ['none', 'moving', 'small_move', 'stationary', 'presence', 'peaceful', 'large_move']
        attribute 'radarAlarmMode', 'enum',   ['0 - arm', '1 - off', '2 - alarm', '3 - doorbell']
        attribute 'radarAlarmVolume', 'enum', ['0 - low', '1 - medium', '2 - high', '3 - mute']
        attribute 'illumState', 'enum', ['dark', 'light', 'unknown']
        attribute 'WARNING', 'string'

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
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_DEBUG_LOGGING, description: '<i>Turns on debug logging for 24 hours.</i>'
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
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_xsm7l9xa', deviceJoinName: 'Tuya Human Presence Detector'],                     //
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_ztqnh5cg', deviceJoinName: 'Tuya Human Presence Detector ZY-M100']                      // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/1054?u=kkossev

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
    ],
    
    
    'TS0601_KAPVNNLK_RADAR'   : [        // 24GHz spammy radar w/ battery backup - no illuminance!
            description   : 'Tuya TS0601_KAPVNNLK 24GHz Radar',        // https://www.amazon.com/dp/B0CDRBX1CQ?psc=1&ref=ppx_yo2ov_dt_b_product_details  // https://www.aliexpress.com/item/1005005834366702.html  // https://github.com/Koenkk/zigbee2mqtt/issues/18632
            models        : ['TS0601'],                                // https://www.aliexpress.com/item/1005005858609756.html     // https://www.aliexpress.com/item/1005005946786561.html    // https://www.aliexpress.com/item/1005005946931559.html
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'DistanceMeasurement':true, 'HumanMotionState':true],
            preferences   : ['radarSensitivity':'15',  'maximumDistance':'13', 'smallMotionDetectionSensitivity':'16', 'fadingTime':'12',],
            commands      : ['resetStats':'resetStats', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_kapvnnlk', deviceJoinName: 'Tuya 24 GHz Human Presence Detector NEW'],           // https://community.hubitat.com/t/tuya-smart-human-presence-sensor-micromotion-detect-human-motion-detector-zigbee-ts0601-tze204-sxm7l9xa/111612/71?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_kyhbrfyl', deviceJoinName: 'Tuya 24 GHz Human Presence Detector NEW']           // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/1042?u=kkossev
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',              type:'enum',    rw: 'ro', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:11,  name:'humanMotionState',    type:'enum',    rw: 'ro', min:0,   max:2,     defVal:'0', map:[0:'none', 1:'small_move', 2:'large_move'],  description:'Human motion state'],        // "none", "small_move", "large_move"]
                [dp:12,  name:'fadingTime',          type:'number',  rw: 'rw', min:3,   max:600,   defVal:60,   scale:1,   unit:'seconds',    title:'<b>Fading time</b>',                description:'<i>Presence inactivity delay timer</i>'],                                  // aka 'nobody time'
                [dp:13,  name:'maximumDistance',     type:'decimal',/* dt: '03',*/ rw: 'rw', min:1.5, max:6.0,   defVal:4.0, step:75, scale:100, unit:'meters',     title:'<b>Maximum detection distance</b>', description:'<i>Maximum (far) detection distance</i>'],  // aka 'Large motion detection distance'
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
    
    
    'TS0601_BLACK_SQUARE_RADAR'   : [        // // 24GHz Big Black Square Radar w/ annoying LED    // EXTREMLY SPAMMY !!!
            description   : 'Tuya Black Square Radar',
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor':true],
            preferences   : ['indicatorLight':'103'],
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
            spammyDPsToIgnore : [103, 102, 101],            // we don't need to know the LED status every 4 seconds! Skip also all other spammy DPs except motion
            spammyDPsToNotTrace : [1, 101, 102, 103],     // very spammy device - 4 packates are sent every 4 seconds!
            deviceJoinName: '24GHz Black Square Human Presence Radar w/ LED',
    ],
    
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
    
    // isSONOFF()
    'SONOFF_SNZB-06P_RADAR' : [
            description   : 'SONOFF SNZB-06P RADAR',
            models        : ['SONOFF'],
            device        : [type: 'radar', powerSource: 'dc', isIAS:false, isSleepy:false],
            capabilities  : ['MotionSensor': true],
            preferences   : ['fadingTime':'0x0406:0x0020', 'radarSensitivity':'0x0406:0x0022', 'detectionDelay':'0x0406:0x0021'],
            commands      : ['printFingerprints':'printFingerprints','resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0406,0500,FC57,FC11', outClusters:'0003,0019', model:'SNZB-06P', manufacturer:'SONOFF', deviceJoinName: 'SONOFF SNZB-06P RADAR'],      // https://community.hubitat.com/t/sonoff-zigbee-human-presence-sensor-snzb-06p/126128/14?u=kkossev
            ],
            attributes:       [
                [at:'0x0406:0x0000', name:'motion',           type:'enum',                rw: 'ro', min:0,   max:1,    defVal:'0',  scale:1,         map:[0:'inactive', 1:'active'] ,   unit:'',  title:'<b>Occupancy state</b>', description:'<i>Occupancy state</i>'],
                [at:'0x0406:0x0022', name:'radarSensitivity', type:'enum',    dt: '0x20', rw: 'rw', min:1,   max:3,    defVal:'1',  scale:1,  unit:'',        map:[1:'1 - low', 2:'2 - medium', 3:'3 - high'], title:'<b>Radar Sensitivity</b>',   description:'<i>Radar Sensitivity</i>'],
                [at:'0x0406:0x0020', name:'fadingTime',       type:'enum',    dt: '0x21', rw: 'rw', min:15,  max:999,  defVal:'30', scale:1,  unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'<i>Radar fading time in seconds</i>'],
                [at:'0x0406:0x0021', name:'detectionDelay',   type:'decimal', dt: '0x21', rw: 'rw', min:0.0, max:10.0, defVal:0.0,  scale:10, unit:'seconds',  title:'<b>Detection delay</b>',            description:'<i>Presence detection delay timer</i>'],
                [at:'0xFC11:0x2001', name:'illumState',       type:'enum',    dt: '0x20', mfgCode: '0x1286', rw: 'ro', min:0,  max:2,   defVal:2, scale:1,  unit:'',   map:[0:'dark', 1:'light', 2:'unknown'], title:'<b>Illuminance State</b>',   description:'<i>Illuminance State</i>']
            ],
            refresh: ['motion', 'radarSensitivity', 'fadingTime', 'detectionDelay'],
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

void setMotion(String mode) {
    if (mode == 'active') {
        handleMotion(motionActive = true, isDigital = true)
    } else if (mode == 'inactive') {
        handleMotion(motionActive = false, isDigital = true)
    } else {
        if (settings?.txtEnable) {
            log.warn "${device.displayName} please select motion action"
        }
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

List<String> refreshFromDeviceProfileList() {
    logDebug "refreshFromDeviceProfileList()"
    List<String> cmds = []
    if (DEVICE?.refresh != null) {
        List<String> refreshList = DEVICE.refresh
        for (String k : refreshList) {
            if (k != null) {
                Map map = DEVICE.attributes.find { it.name == k }
                if (map != null) {
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:]
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100)
                }
            }
        }
    }
    return cmds
}

List<String> customRefresh() {
    logDebug "customRefresh()"
    List<String> cmds = []
    cmds += refreshFromDeviceProfileList()
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
        runIn(2, customRefresh, [overwrite: true])
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

void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents()"
    if (getDeviceProfile() == 'TS0601_BLACK_SQUARE_RADAR') {
        sendEvent(name: 'WARNING', value: 'EXTREMLY SPAMMY DEVICE!', descriptionText: 'This device bombards the hub every 4 seconds!')
    }
}

void updateInidicatorLight() {
    if (settings?.indicatorLight != null && getDeviceProfile() == 'TS0601_BLACK_SQUARE_RADAR') {
        // in the old 4-in-1 driver we used the Tuya command 0x11 to restore the LED on/off configuration
        // dont'know what command "11" means, it is sent by the square black radar when powered on. Will use it to restore the LED on/off configuration :)
        ArrayList<String> cmds = []
        int value = safeToInt(settings.indicatorLight)
        String dpValHex = zigbee.convertToHexString(value as int, 2)
        cmds = sendTuyaCommand('67', DP_TYPE_BOOL, dpValHex)       // TODO - refactor!
        if (settings?.logEnable) log.info "${device.displayName} updating indicator light to : ${(value ? 'ON' : 'OFF')} (${value})"
        sendZigbeeCommands(cmds)
    }
}

void customParseZdoClusters(final Map descMap){
    if (descMap.clusterInt == 0x0013 && getDeviceProfile() == 'TS0601_BLACK_SQUARE_RADAR') {  // device announcement
        updateInidicatorLight()
    }
}

void customParseTuyaCluster(final Map descMap) {
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '11' && getDeviceProfile() == 'TS0601_BLACK_SQUARE_RADAR') {
        updateInidicatorLight()
    }
}

@CompileStatic
void testFunc( par) {
    parse('catchall: 0104 EF00 01 01 0040 00 7770 01 00 0000 02 01 00556701000100') 
}

// catchall: 0104 EF00 01 01 0040 00 7770 01 00 0000 02 01 00556701000100 
void test(String par) {
    long startTime = now()
    logDebug "test() started at ${startTime}"
    //parse('catchall: 0104 EF00 01 01 0040 00 7770 01 00 0000 02 01 00556701000100')
    def parpar = 'catchall: 0104 EF00 01 01 0040 00 7770 01 00 0000 02 01 00556701000100'

    for (int i=0; i<100; i++) { 
        testFunc(parpar) 
    }

    long endTime = now()
    logDebug "test() ended at ${endTime} (duration ${endTime - startTime}ms)"
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
    version: '3.1.0', // library marker kkossev.commonLib, line 10
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
  * ver. 3.0.7  2024-04-23 kkossev  - tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr, activeEpRqCtr; trace ZDO commands; added 0x0406 OccupancyCluster; reduced debug for chatty devices; // library marker kkossev.commonLib, line 39
  * ver. 3.1.0  2024-04-26 kkossev  - (dev. branch) unnecesery unschedule() speed optimization. // library marker kkossev.commonLib, line 40
  * // library marker kkossev.commonLib, line 41
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 42
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 43
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib // library marker kkossev.commonLib, line 44
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 45
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 46
  * // library marker kkossev.commonLib, line 47
*/ // library marker kkossev.commonLib, line 48

String commonLibVersion() { '3.1.0' } // library marker kkossev.commonLib, line 50
String commonLibStamp() { '2024/04/26 5:36 PM' } // library marker kkossev.commonLib, line 51

import groovy.transform.Field // library marker kkossev.commonLib, line 53
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 54
import hubitat.device.Protocol // library marker kkossev.commonLib, line 55
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 56
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 57
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 58
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 59
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 60
import java.math.BigDecimal // library marker kkossev.commonLib, line 61

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 63

metadata { // library marker kkossev.commonLib, line 65
        if (_DEBUG) { // library marker kkossev.commonLib, line 66
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 67
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 68
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 69
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 70
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 71
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 72
            ] // library marker kkossev.commonLib, line 73
        } // library marker kkossev.commonLib, line 74

        // common capabilities for all device types // library marker kkossev.commonLib, line 76
        capability 'Configuration' // library marker kkossev.commonLib, line 77
        capability 'Refresh' // library marker kkossev.commonLib, line 78
        capability 'Health Check' // library marker kkossev.commonLib, line 79

        // common attributes for all device types // library marker kkossev.commonLib, line 81
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 82
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 83
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 84

        // common commands for all device types // library marker kkossev.commonLib, line 86
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 87

        if (deviceType in  ['Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 89
            capability 'Switch' // library marker kkossev.commonLib, line 90
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 91
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 92
            } // library marker kkossev.commonLib, line 93
        } // library marker kkossev.commonLib, line 94

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 96
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 97

    preferences { // library marker kkossev.commonLib, line 99
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 100
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 101
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 102

        if (device) { // library marker kkossev.commonLib, line 104
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 105
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 106
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 107
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 108
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 109
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 110
                } // library marker kkossev.commonLib, line 111
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 112
            } // library marker kkossev.commonLib, line 113
        } // library marker kkossev.commonLib, line 114
    } // library marker kkossev.commonLib, line 115
} // library marker kkossev.commonLib, line 116

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 118
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 119
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 120
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 121
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 122
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 123
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 124
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 125
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 126
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 127
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 128

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 130
    defaultValue: 1, // library marker kkossev.commonLib, line 131
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 132
] // library marker kkossev.commonLib, line 133
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 134
    defaultValue: 240, // library marker kkossev.commonLib, line 135
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 136
] // library marker kkossev.commonLib, line 137
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 138
    defaultValue: 0, // library marker kkossev.commonLib, line 139
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 140
] // library marker kkossev.commonLib, line 141

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 143
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 144
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 145
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 146
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 147
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 148
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 149
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 150
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 151
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 152
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 153
] // library marker kkossev.commonLib, line 154

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 156
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 157
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 158
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 159
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 160
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 161
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 162
//boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 163
//boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 164

/** // library marker kkossev.commonLib, line 166
 * Parse Zigbee message // library marker kkossev.commonLib, line 167
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 168
 */ // library marker kkossev.commonLib, line 169
void parse(final String description) { // library marker kkossev.commonLib, line 170
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 171
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 172
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 173
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 174

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 176
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 177
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 178
            parseIasMessage(description) // library marker kkossev.commonLib, line 179
        } // library marker kkossev.commonLib, line 180
        else { // library marker kkossev.commonLib, line 181
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 182
        } // library marker kkossev.commonLib, line 183
        return // library marker kkossev.commonLib, line 184
    } // library marker kkossev.commonLib, line 185
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 186
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 187
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 188
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 189
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 190
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 191
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 192
        return // library marker kkossev.commonLib, line 193
    } // library marker kkossev.commonLib, line 194

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 196
        return // library marker kkossev.commonLib, line 197
    } // library marker kkossev.commonLib, line 198
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 199

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" }   // library marker kkossev.commonLib, line 201
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 202

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 204
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 205
        return // library marker kkossev.commonLib, line 206
    } // library marker kkossev.commonLib, line 207
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 208
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 209
        return // library marker kkossev.commonLib, line 210
    } // library marker kkossev.commonLib, line 211
    // // library marker kkossev.commonLib, line 212
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 213
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 214
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 215

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 217
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 218
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 219
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 220
            break // library marker kkossev.commonLib, line 221
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 222
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 223
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 224
            break // library marker kkossev.commonLib, line 225
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 226
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 227
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 228
            break // library marker kkossev.commonLib, line 229
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 230
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 231
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 232
            break // library marker kkossev.commonLib, line 233
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 234
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 235
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 236
            break // library marker kkossev.commonLib, line 237
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 238
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 239
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 240
            break // library marker kkossev.commonLib, line 241
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 242
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 243
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 244
            break // library marker kkossev.commonLib, line 245
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 246
            parseAnalogInputCluster(descMap, description) // library marker kkossev.commonLib, line 247
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map, description) } // library marker kkossev.commonLib, line 248
            break // library marker kkossev.commonLib, line 249
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 250
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 251
            break // library marker kkossev.commonLib, line 252
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 253
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 254
            break // library marker kkossev.commonLib, line 255
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 256
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 257
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 258
            break // library marker kkossev.commonLib, line 259
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 260
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 261
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 262
            break // library marker kkossev.commonLib, line 263
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 264
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 265
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 266
            break // library marker kkossev.commonLib, line 267
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 268
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 269
            break // library marker kkossev.commonLib, line 270
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 271
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 272
            break // library marker kkossev.commonLib, line 273
        case 0x0406 : //OCCUPANCY_CLUSTER                   // Sonoff SNZB-06 // library marker kkossev.commonLib, line 274
            parseOccupancyCluster(descMap) // library marker kkossev.commonLib, line 275
            break // library marker kkossev.commonLib, line 276
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 277
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 278
            break // library marker kkossev.commonLib, line 279
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 280
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 281
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 282
            break // library marker kkossev.commonLib, line 283
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 284
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 285
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 286
            break // library marker kkossev.commonLib, line 287
        case 0xE002 : // library marker kkossev.commonLib, line 288
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 289
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 290
            break // library marker kkossev.commonLib, line 291
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 292
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 293
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 294
            break // library marker kkossev.commonLib, line 295
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 296
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 297
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 298
            break // library marker kkossev.commonLib, line 299
        case 0xFC11 :                                       // Sonoff // library marker kkossev.commonLib, line 300
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 301
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 302
            break // library marker kkossev.commonLib, line 303
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 304
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 305
            break // library marker kkossev.commonLib, line 306
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 307
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 308
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 309
            break // library marker kkossev.commonLib, line 310
        default: // library marker kkossev.commonLib, line 311
            if (settings.logEnable) { // library marker kkossev.commonLib, line 312
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 313
            } // library marker kkossev.commonLib, line 314
            break // library marker kkossev.commonLib, line 315
    } // library marker kkossev.commonLib, line 316
} // library marker kkossev.commonLib, line 317

static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 319
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 320
} // library marker kkossev.commonLib, line 321

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 323
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 324
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 325
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 326
    } // library marker kkossev.commonLib, line 327
    return false // library marker kkossev.commonLib, line 328
} // library marker kkossev.commonLib, line 329

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 331
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 332
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 333
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 334
    } // library marker kkossev.commonLib, line 335
    return false // library marker kkossev.commonLib, line 336
} // library marker kkossev.commonLib, line 337

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 339
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 340
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 341
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 342
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 343
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 344
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 345
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 346
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 347
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 348
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 349
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 350
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 351
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 352
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 353
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 354
] // library marker kkossev.commonLib, line 355

/** // library marker kkossev.commonLib, line 357
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 358
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 359
 */ // library marker kkossev.commonLib, line 360
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 361
    if (state.stats == null) { state.stats = [:] }  // library marker kkossev.commonLib, line 362
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 363
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 364
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 365
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 366
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 367
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 368
    switch (clusterId) { // library marker kkossev.commonLib, line 369
        case 0x0005 : // library marker kkossev.commonLib, line 370
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 371
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 372
            break // library marker kkossev.commonLib, line 373
        case 0x0006 : // library marker kkossev.commonLib, line 374
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 375
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 376
            break // library marker kkossev.commonLib, line 377
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 378
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 379
            if (settings?.logEnable) { log.info "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 380
            break // library marker kkossev.commonLib, line 381
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 382
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 383
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 384
            break // library marker kkossev.commonLib, line 385
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 386
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 387
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 388
            if (settings?.logEnable) { log.info "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 389
            break // library marker kkossev.commonLib, line 390
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 391
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 392
            break // library marker kkossev.commonLib, line 393
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 394
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 395
            if (settings?.logEnable) { log.info "${clusterInfo}" } // library marker kkossev.commonLib, line 396
            break // library marker kkossev.commonLib, line 397
        default : // library marker kkossev.commonLib, line 398
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 399
            break // library marker kkossev.commonLib, line 400
    } // library marker kkossev.commonLib, line 401
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 402
} // library marker kkossev.commonLib, line 403

/** // library marker kkossev.commonLib, line 405
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 406
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 407
 */ // library marker kkossev.commonLib, line 408
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 409
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 410
    switch (commandId) { // library marker kkossev.commonLib, line 411
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 412
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 413
            break // library marker kkossev.commonLib, line 414
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 415
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 416
            break // library marker kkossev.commonLib, line 417
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 418
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 419
            break // library marker kkossev.commonLib, line 420
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 421
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 422
            break // library marker kkossev.commonLib, line 423
        case 0x0B: // default command response // library marker kkossev.commonLib, line 424
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 425
            break // library marker kkossev.commonLib, line 426
        default: // library marker kkossev.commonLib, line 427
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 428
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 429
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 430
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 431
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 432
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 433
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 434
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 435
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 436
            } // library marker kkossev.commonLib, line 437
            break // library marker kkossev.commonLib, line 438
    } // library marker kkossev.commonLib, line 439
} // library marker kkossev.commonLib, line 440

/** // library marker kkossev.commonLib, line 442
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 443
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 444
 */ // library marker kkossev.commonLib, line 445
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 446
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 447
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 448
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 449
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 450
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 451
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 452
    } // library marker kkossev.commonLib, line 453
    else { // library marker kkossev.commonLib, line 454
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 455
    } // library marker kkossev.commonLib, line 456
} // library marker kkossev.commonLib, line 457

/** // library marker kkossev.commonLib, line 459
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 460
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 461
 */ // library marker kkossev.commonLib, line 462
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 463
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 464
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 465
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 466
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 467
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 468
    } // library marker kkossev.commonLib, line 469
    else { // library marker kkossev.commonLib, line 470
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 471
    } // library marker kkossev.commonLib, line 472
} // library marker kkossev.commonLib, line 473

/** // library marker kkossev.commonLib, line 475
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 476
 */ // library marker kkossev.commonLib, line 477
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 478
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 479
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 480
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 481
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 482
        state.reportingEnabled = true // library marker kkossev.commonLib, line 483
    } // library marker kkossev.commonLib, line 484
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 485
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 486
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 487
    } else { // library marker kkossev.commonLib, line 488
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 489
    } // library marker kkossev.commonLib, line 490
} // library marker kkossev.commonLib, line 491

/** // library marker kkossev.commonLib, line 493
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 494
 */ // library marker kkossev.commonLib, line 495
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 496
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 497
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 498
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 499
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 500
    if (status == 0) { // library marker kkossev.commonLib, line 501
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 502
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 503
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 504
        int delta = 0 // library marker kkossev.commonLib, line 505
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 506
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 507
        } // library marker kkossev.commonLib, line 508
        else { // library marker kkossev.commonLib, line 509
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 510
        } // library marker kkossev.commonLib, line 511
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 512
    } // library marker kkossev.commonLib, line 513
    else { // library marker kkossev.commonLib, line 514
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 515
    } // library marker kkossev.commonLib, line 516
} // library marker kkossev.commonLib, line 517

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 519
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 520
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 521
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 522
        return false // library marker kkossev.commonLib, line 523
    } // library marker kkossev.commonLib, line 524
    // execute the customHandler function // library marker kkossev.commonLib, line 525
    boolean result = false // library marker kkossev.commonLib, line 526
    try { // library marker kkossev.commonLib, line 527
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 528
    } // library marker kkossev.commonLib, line 529
    catch (e) { // library marker kkossev.commonLib, line 530
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 531
        return false // library marker kkossev.commonLib, line 532
    } // library marker kkossev.commonLib, line 533
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 534
    return result // library marker kkossev.commonLib, line 535
} // library marker kkossev.commonLib, line 536

/** // library marker kkossev.commonLib, line 538
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 539
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 540
 */ // library marker kkossev.commonLib, line 541
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 542
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 543
    final String commandId = data[0] // library marker kkossev.commonLib, line 544
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 545
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 546
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 547
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 548
    } else { // library marker kkossev.commonLib, line 549
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 550
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 551
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 552
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 553
        } // library marker kkossev.commonLib, line 554
    } // library marker kkossev.commonLib, line 555
} // library marker kkossev.commonLib, line 556

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 558
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 559
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 560
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 561

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 563
    0x00: 'Success', // library marker kkossev.commonLib, line 564
    0x01: 'Failure', // library marker kkossev.commonLib, line 565
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 566
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 567
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 568
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 569
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 570
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 571
    0x88: 'Read Only', // library marker kkossev.commonLib, line 572
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 573
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 574
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 575
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 576
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 577
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 578
    0x94: 'Time out', // library marker kkossev.commonLib, line 579
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 580
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 581
] // library marker kkossev.commonLib, line 582

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 584
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 585
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 586
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 587
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 588
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 589
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 590
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 591
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 592
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 593
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 594
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 595
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 596
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 597
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 598
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 599
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 600
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 601
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 602
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 603
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 604
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 605
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 606
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 607
] // library marker kkossev.commonLib, line 608

void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 610
    if (xiaomiLibVersion() != null) { parseXiaomiClusterLib(descMap) } else { logWarn 'Xiaomi cluster 0xFCC0' } // library marker kkossev.commonLib, line 611
} // library marker kkossev.commonLib, line 612

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 614
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 615
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 616
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 617
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 618
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 619
    return avg // library marker kkossev.commonLib, line 620
} // library marker kkossev.commonLib, line 621

/* // library marker kkossev.commonLib, line 623
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 624
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 625
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 626
*/ // library marker kkossev.commonLib, line 627
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 628

/** // library marker kkossev.commonLib, line 630
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 631
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 632
 */ // library marker kkossev.commonLib, line 633
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 634
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 635
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 636
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 637
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 638
        case 0x0000: // library marker kkossev.commonLib, line 639
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 640
            break // library marker kkossev.commonLib, line 641
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 642
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 643
            if (isPing) { // library marker kkossev.commonLib, line 644
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 645
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 646
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 647
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 648
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 649
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 650
                    sendRttEvent() // library marker kkossev.commonLib, line 651
                } // library marker kkossev.commonLib, line 652
                else { // library marker kkossev.commonLib, line 653
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 654
                } // library marker kkossev.commonLib, line 655
                state.states['isPing'] = false // library marker kkossev.commonLib, line 656
            } // library marker kkossev.commonLib, line 657
            else { // library marker kkossev.commonLib, line 658
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 659
            } // library marker kkossev.commonLib, line 660
            break // library marker kkossev.commonLib, line 661
        case 0x0004: // library marker kkossev.commonLib, line 662
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 663
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 664
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 665
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 666
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 667
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 668
            } // library marker kkossev.commonLib, line 669
            break // library marker kkossev.commonLib, line 670
        case 0x0005: // library marker kkossev.commonLib, line 671
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 672
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 673
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 674
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 675
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 676
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 677
            } // library marker kkossev.commonLib, line 678
            break // library marker kkossev.commonLib, line 679
        case 0x0007: // library marker kkossev.commonLib, line 680
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 681
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 682
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 683
            break // library marker kkossev.commonLib, line 684
        case 0xFFDF: // library marker kkossev.commonLib, line 685
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 686
            break // library marker kkossev.commonLib, line 687
        case 0xFFE2: // library marker kkossev.commonLib, line 688
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 689
            break // library marker kkossev.commonLib, line 690
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 691
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 692
            break // library marker kkossev.commonLib, line 693
        case 0xFFFE: // library marker kkossev.commonLib, line 694
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 695
            break // library marker kkossev.commonLib, line 696
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 697
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 698
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 699
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 700
            break // library marker kkossev.commonLib, line 701
        default: // library marker kkossev.commonLib, line 702
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 703
            break // library marker kkossev.commonLib, line 704
    } // library marker kkossev.commonLib, line 705
} // library marker kkossev.commonLib, line 706

// power cluster            0x0001 // library marker kkossev.commonLib, line 708
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 709
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 710
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 711
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 712
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 713
    } // library marker kkossev.commonLib, line 714
    if (this.respondsTo('customParsePowerCluster')) { // library marker kkossev.commonLib, line 715
        customParsePowerCluster(descMap) // library marker kkossev.commonLib, line 716
    } // library marker kkossev.commonLib, line 717
    else { // library marker kkossev.commonLib, line 718
        logDebug "zigbee received Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 719
    } // library marker kkossev.commonLib, line 720
} // library marker kkossev.commonLib, line 721

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 723
void parseIdentityCluster(final Map descMap) { logDebug 'unprocessed parseIdentityCluster' } // library marker kkossev.commonLib, line 724

void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 726
    if (this.respondsTo('customParseScenesCluster')) { customParseScenesCluster(descMap) } else { logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 727
} // library marker kkossev.commonLib, line 728

void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 730
    if (this.respondsTo('customParseGroupsCluster')) { customParseGroupsCluster(descMap) } else { logWarn "unprocessed GroupsCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 731
} // library marker kkossev.commonLib, line 732

/* // library marker kkossev.commonLib, line 734
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 735
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 736
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 737
*/ // library marker kkossev.commonLib, line 738

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 740
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 741
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 742
    } // library marker kkossev.commonLib, line 743
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 744
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 745
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 746
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 747
    } // library marker kkossev.commonLib, line 748
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 749
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 750
    } // library marker kkossev.commonLib, line 751
    else { // library marker kkossev.commonLib, line 752
        if (descMap.attrId != null) { logWarn "parseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.commonLib, line 753
        else { logDebug "parseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 754
    } // library marker kkossev.commonLib, line 755
} // library marker kkossev.commonLib, line 756

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 758
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 759
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 760

void toggle() { // library marker kkossev.commonLib, line 762
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 763
    String state = '' // library marker kkossev.commonLib, line 764
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 765
        state = 'on' // library marker kkossev.commonLib, line 766
    } // library marker kkossev.commonLib, line 767
    else { // library marker kkossev.commonLib, line 768
        state = 'off' // library marker kkossev.commonLib, line 769
    } // library marker kkossev.commonLib, line 770
    descriptionText += state // library marker kkossev.commonLib, line 771
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 772
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 773
} // library marker kkossev.commonLib, line 774

void off() { // library marker kkossev.commonLib, line 776
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 777
        customOff() // library marker kkossev.commonLib, line 778
        return // library marker kkossev.commonLib, line 779
    } // library marker kkossev.commonLib, line 780
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 781
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 782
        return // library marker kkossev.commonLib, line 783
    } // library marker kkossev.commonLib, line 784
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 785
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 786
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 787
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 788
        if (currentState == 'off') { // library marker kkossev.commonLib, line 789
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 790
        } // library marker kkossev.commonLib, line 791
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 792
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 793
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 794
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 795
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 796
    } // library marker kkossev.commonLib, line 797
    /* // library marker kkossev.commonLib, line 798
    else { // library marker kkossev.commonLib, line 799
        if (currentState != 'off') { // library marker kkossev.commonLib, line 800
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 801
        } // library marker kkossev.commonLib, line 802
        else { // library marker kkossev.commonLib, line 803
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 804
            return // library marker kkossev.commonLib, line 805
        } // library marker kkossev.commonLib, line 806
    } // library marker kkossev.commonLib, line 807
    */ // library marker kkossev.commonLib, line 808

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 810
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 811
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 812
} // library marker kkossev.commonLib, line 813

void on() { // library marker kkossev.commonLib, line 815
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 816
        customOn() // library marker kkossev.commonLib, line 817
        return // library marker kkossev.commonLib, line 818
    } // library marker kkossev.commonLib, line 819
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 820
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 821
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 822
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 823
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 824
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 825
        } // library marker kkossev.commonLib, line 826
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 827
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 828
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 829
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 830
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 831
    } // library marker kkossev.commonLib, line 832
    /* // library marker kkossev.commonLib, line 833
    else { // library marker kkossev.commonLib, line 834
        if (currentState != 'on') { // library marker kkossev.commonLib, line 835
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 836
        } // library marker kkossev.commonLib, line 837
        else { // library marker kkossev.commonLib, line 838
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 839
            return // library marker kkossev.commonLib, line 840
        } // library marker kkossev.commonLib, line 841
    } // library marker kkossev.commonLib, line 842
    */ // library marker kkossev.commonLib, line 843
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 844
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 845
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 846
} // library marker kkossev.commonLib, line 847

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 849
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 850
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 851
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 852
    } // library marker kkossev.commonLib, line 853
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 854
    Map map = [:] // library marker kkossev.commonLib, line 855
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 856
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 857
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 858
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 859
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 860
        return // library marker kkossev.commonLib, line 861
    } // library marker kkossev.commonLib, line 862
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 863
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 864
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 865
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 866
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 867
        state.states['debounce'] = true // library marker kkossev.commonLib, line 868
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 869
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 870
    } else { // library marker kkossev.commonLib, line 871
        state.states['debounce'] = true // library marker kkossev.commonLib, line 872
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 873
    } // library marker kkossev.commonLib, line 874
    map.name = 'switch' // library marker kkossev.commonLib, line 875
    map.value = value // library marker kkossev.commonLib, line 876
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 877
    if (isRefresh) { // library marker kkossev.commonLib, line 878
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 879
        map.isStateChange = true // library marker kkossev.commonLib, line 880
    } else { // library marker kkossev.commonLib, line 881
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 882
    } // library marker kkossev.commonLib, line 883
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 884
    sendEvent(map) // library marker kkossev.commonLib, line 885
    clearIsDigital() // library marker kkossev.commonLib, line 886
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 887
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 888
    } // library marker kkossev.commonLib, line 889
} // library marker kkossev.commonLib, line 890

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 892
    '0': 'switch off', // library marker kkossev.commonLib, line 893
    '1': 'switch on', // library marker kkossev.commonLib, line 894
    '2': 'switch last state' // library marker kkossev.commonLib, line 895
] // library marker kkossev.commonLib, line 896

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 898
    '0': 'toggle', // library marker kkossev.commonLib, line 899
    '1': 'state', // library marker kkossev.commonLib, line 900
    '2': 'momentary' // library marker kkossev.commonLib, line 901
] // library marker kkossev.commonLib, line 902

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 904
    Map descMap = [:] // library marker kkossev.commonLib, line 905
    try { // library marker kkossev.commonLib, line 906
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 907
    } // library marker kkossev.commonLib, line 908
    catch (e1) { // library marker kkossev.commonLib, line 909
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 910
        // try alternative custom parsing // library marker kkossev.commonLib, line 911
        descMap = [:] // library marker kkossev.commonLib, line 912
        try { // library marker kkossev.commonLib, line 913
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 914
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 915
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 916
            } // library marker kkossev.commonLib, line 917
        } // library marker kkossev.commonLib, line 918
        catch (e2) { // library marker kkossev.commonLib, line 919
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 920
            return [:] // library marker kkossev.commonLib, line 921
        } // library marker kkossev.commonLib, line 922
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 923
    } // library marker kkossev.commonLib, line 924
    return descMap // library marker kkossev.commonLib, line 925
} // library marker kkossev.commonLib, line 926

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 928
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 929
        return false // library marker kkossev.commonLib, line 930
    } // library marker kkossev.commonLib, line 931
    // try to parse ... // library marker kkossev.commonLib, line 932
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 933
    Map descMap = [:] // library marker kkossev.commonLib, line 934
    try { // library marker kkossev.commonLib, line 935
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 936
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 937
    } // library marker kkossev.commonLib, line 938
    catch (e) { // library marker kkossev.commonLib, line 939
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 940
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 941
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 942
        return true // library marker kkossev.commonLib, line 943
    } // library marker kkossev.commonLib, line 944

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 946
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 947
    } // library marker kkossev.commonLib, line 948
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 949
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 950
    } // library marker kkossev.commonLib, line 951
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 952
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 953
    } // library marker kkossev.commonLib, line 954
    else { // library marker kkossev.commonLib, line 955
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 956
        return false // library marker kkossev.commonLib, line 957
    } // library marker kkossev.commonLib, line 958
    return true    // processed // library marker kkossev.commonLib, line 959
} // library marker kkossev.commonLib, line 960

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 962
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 963
  /* // library marker kkossev.commonLib, line 964
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 965
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 966
        return true // library marker kkossev.commonLib, line 967
    } // library marker kkossev.commonLib, line 968
*/ // library marker kkossev.commonLib, line 969
    Map descMap = [:] // library marker kkossev.commonLib, line 970
    try { // library marker kkossev.commonLib, line 971
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 972
    } // library marker kkossev.commonLib, line 973
    catch (e1) { // library marker kkossev.commonLib, line 974
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 975
        // try alternative custom parsing // library marker kkossev.commonLib, line 976
        descMap = [:] // library marker kkossev.commonLib, line 977
        try { // library marker kkossev.commonLib, line 978
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 979
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 980
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 981
            } // library marker kkossev.commonLib, line 982
        } // library marker kkossev.commonLib, line 983
        catch (e2) { // library marker kkossev.commonLib, line 984
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 985
            return true // library marker kkossev.commonLib, line 986
        } // library marker kkossev.commonLib, line 987
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 988
    } // library marker kkossev.commonLib, line 989
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 990
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 991
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 992
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 993
        return false // library marker kkossev.commonLib, line 994
    } // library marker kkossev.commonLib, line 995
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 996
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 997
    // attribute report received // library marker kkossev.commonLib, line 998
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 999
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1000
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1001
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1002
    } // library marker kkossev.commonLib, line 1003
    attrData.each { // library marker kkossev.commonLib, line 1004
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1005
        //def map = [:] // library marker kkossev.commonLib, line 1006
        if (it.status == '86') { // library marker kkossev.commonLib, line 1007
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1008
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1009
        } // library marker kkossev.commonLib, line 1010
        switch (it.cluster) { // library marker kkossev.commonLib, line 1011
            case '0000' : // library marker kkossev.commonLib, line 1012
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1013
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1014
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1015
                } // library marker kkossev.commonLib, line 1016
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1017
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1018
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1019
                } // library marker kkossev.commonLib, line 1020
                else { // library marker kkossev.commonLib, line 1021
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1022
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1023
                } // library marker kkossev.commonLib, line 1024
                break // library marker kkossev.commonLib, line 1025
            default : // library marker kkossev.commonLib, line 1026
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1027
                break // library marker kkossev.commonLib, line 1028
        } // switch // library marker kkossev.commonLib, line 1029
    } // for each attribute // library marker kkossev.commonLib, line 1030
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1031
} // library marker kkossev.commonLib, line 1032

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1034

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1036
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1037
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1038
    def mode // library marker kkossev.commonLib, line 1039
    String attrName // library marker kkossev.commonLib, line 1040
    if (it.value == null) { // library marker kkossev.commonLib, line 1041
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1042
        return // library marker kkossev.commonLib, line 1043
    } // library marker kkossev.commonLib, line 1044
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1045
    switch (it.attrId) { // library marker kkossev.commonLib, line 1046
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1047
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1048
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1049
            break // library marker kkossev.commonLib, line 1050
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1051
            attrName = 'On Time' // library marker kkossev.commonLib, line 1052
            mode = value // library marker kkossev.commonLib, line 1053
            break // library marker kkossev.commonLib, line 1054
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1055
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1056
            mode = value // library marker kkossev.commonLib, line 1057
            break // library marker kkossev.commonLib, line 1058
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1059
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1060
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1061
            break // library marker kkossev.commonLib, line 1062
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1063
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1064
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1065
            break // library marker kkossev.commonLib, line 1066
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1067
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1068
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1069
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1070
            } // library marker kkossev.commonLib, line 1071
            else { // library marker kkossev.commonLib, line 1072
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1073
            } // library marker kkossev.commonLib, line 1074
            break // library marker kkossev.commonLib, line 1075
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1076
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1077
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1078
            break // library marker kkossev.commonLib, line 1079
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1080
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1081
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1082
            break // library marker kkossev.commonLib, line 1083
        default : // library marker kkossev.commonLib, line 1084
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1085
            return // library marker kkossev.commonLib, line 1086
    } // library marker kkossev.commonLib, line 1087
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1088
} // library marker kkossev.commonLib, line 1089

void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1091
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1092
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1093
    } // library marker kkossev.commonLib, line 1094
    else if (this.respondsTo('levelLibParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1095
        levelLibParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1096
    } // library marker kkossev.commonLib, line 1097
    else { // library marker kkossev.commonLib, line 1098
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1099
    } // library marker kkossev.commonLib, line 1100
} // library marker kkossev.commonLib, line 1101

String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1103
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1104
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1105
} // library marker kkossev.commonLib, line 1106

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1108
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1109
} // library marker kkossev.commonLib, line 1110

void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1112
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1113
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1114
    } // library marker kkossev.commonLib, line 1115
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1116
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1117
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1118
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1119
    } // library marker kkossev.commonLib, line 1120
    else { // library marker kkossev.commonLib, line 1121
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1122
    } // library marker kkossev.commonLib, line 1123
} // library marker kkossev.commonLib, line 1124

void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1126
    if (this.respondsTo('customParseIlluminanceCluster')) { customParseIlluminanceCluster(descMap) } else { logWarn "unprocessed Illuminance attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 1127
} // library marker kkossev.commonLib, line 1128

// Temperature Measurement Cluster 0x0402 // library marker kkossev.commonLib, line 1130
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1131
    if (this.respondsTo('customParseTemperatureCluster')) { // library marker kkossev.commonLib, line 1132
        customParseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 1133
    } // library marker kkossev.commonLib, line 1134
    else { // library marker kkossev.commonLib, line 1135
        logWarn "unprocessed Temperature attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1136
    } // library marker kkossev.commonLib, line 1137
} // library marker kkossev.commonLib, line 1138

// Humidity Measurement Cluster 0x0405 // library marker kkossev.commonLib, line 1140
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1141
    if (this.respondsTo('customParseHumidityCluster')) { // library marker kkossev.commonLib, line 1142
        customParseHumidityCluster(descMap) // library marker kkossev.commonLib, line 1143
    } // library marker kkossev.commonLib, line 1144
    else { // library marker kkossev.commonLib, line 1145
        logWarn "unprocessed Humidity attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1146
    } // library marker kkossev.commonLib, line 1147
} // library marker kkossev.commonLib, line 1148

// Occupancy Sensing Cluster 0x0406 // library marker kkossev.commonLib, line 1150
void parseOccupancyCluster(final Map descMap) { // library marker kkossev.commonLib, line 1151
    if (this.respondsTo('customParseOccupancyCluster')) { // library marker kkossev.commonLib, line 1152
        customParseOccupancyCluster(descMap) // library marker kkossev.commonLib, line 1153
    } // library marker kkossev.commonLib, line 1154
    else { // library marker kkossev.commonLib, line 1155
        logWarn "unprocessed Occupancy attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1156
    } // library marker kkossev.commonLib, line 1157
} // library marker kkossev.commonLib, line 1158

// Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1160
void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1161
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { logWarn 'parseElectricalMeasureCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1162
} // library marker kkossev.commonLib, line 1163

// Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1165
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1166
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { logWarn 'parseMeteringCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1167
} // library marker kkossev.commonLib, line 1168

// pm2.5 // library marker kkossev.commonLib, line 1170
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1171
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1172
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1173
    /* groovylint-disable-next-line NoFloat */ // library marker kkossev.commonLib, line 1174
    float floatValue  = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1175
    if (this.respondsTo('handlePm25Event')) { // library marker kkossev.commonLib, line 1176
        handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1177
    } // library marker kkossev.commonLib, line 1178
    else { // library marker kkossev.commonLib, line 1179
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1180
    } // library marker kkossev.commonLib, line 1181
} // library marker kkossev.commonLib, line 1182

// Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1184
void parseAnalogInputCluster(final Map descMap, String description=null) { // library marker kkossev.commonLib, line 1185
    if (this.respondsTo('customParseAnalogInputCluster')) { // library marker kkossev.commonLib, line 1186
        customParseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1187
    } // library marker kkossev.commonLib, line 1188
    else if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 1189
        customParseAnalogInputClusterDescription(description)                   // ZigUSB // library marker kkossev.commonLib, line 1190
    } // library marker kkossev.commonLib, line 1191
    else if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1192
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1193
    } // library marker kkossev.commonLib, line 1194
    else { // library marker kkossev.commonLib, line 1195
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1196
    } // library marker kkossev.commonLib, line 1197
} // library marker kkossev.commonLib, line 1198

// Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1200
void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1201
    if (this.respondsTo('customParseMultistateInputCluster')) { customParseMultistateInputCluster(descMap) } else { logWarn "parseMultistateInputCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1202
} // library marker kkossev.commonLib, line 1203

// Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1205
void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1206
    if (this.respondsTo('customParseWindowCoveringCluster')) { customParseWindowCoveringCluster(descMap) } else { logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1207
} // library marker kkossev.commonLib, line 1208

// thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1210
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1211
    if (this.respondsTo('customParseThermostatCluster')) { customParseThermostatCluster(descMap) } else { logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1212
} // library marker kkossev.commonLib, line 1213

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1215
    if (this.respondsTo('customParseFC11Cluster')) { customParseFC11Cluster(descMap) } else { logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1216
} // library marker kkossev.commonLib, line 1217

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1219
    if (this.respondsTo('customParseE002Cluster')) { customParseE002Cluster(descMap) } else { logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }    // radars // library marker kkossev.commonLib, line 1220
} // library marker kkossev.commonLib, line 1221

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1223
    if (this.respondsTo('customParseEC03Cluster')) { customParseEC03Cluster(descMap) } else { logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }   // radars // library marker kkossev.commonLib, line 1224
} // library marker kkossev.commonLib, line 1225

/* // library marker kkossev.commonLib, line 1227
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1228
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1229
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1230
*/ // library marker kkossev.commonLib, line 1231
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1232
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1233
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1234

// Tuya Commands // library marker kkossev.commonLib, line 1236
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 1237
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 1238
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 1239
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 1240
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 1241

// tuya DP type // library marker kkossev.commonLib, line 1243
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 1244
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 1245
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 1246
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 1247
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 1248
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 1249

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 1251
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 1252
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 1253
        Long offset = 0 // library marker kkossev.commonLib, line 1254
        try { // library marker kkossev.commonLib, line 1255
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 1256
        } // library marker kkossev.commonLib, line 1257
        catch (e) { // library marker kkossev.commonLib, line 1258
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 1259
        } // library marker kkossev.commonLib, line 1260
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 1261
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 1262
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 1263
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 1264
    } // library marker kkossev.commonLib, line 1265
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 1266
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 1267
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 1268
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 1269
        if (status != '00') { // library marker kkossev.commonLib, line 1270
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 1271
        } // library marker kkossev.commonLib, line 1272
    } // library marker kkossev.commonLib, line 1273
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 1274
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 1275
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 1276
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 1277
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 1278
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 1279
            return // library marker kkossev.commonLib, line 1280
        } // library marker kkossev.commonLib, line 1281
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 1282
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 1283
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 1284
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 1285
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 1286
            if (!isChattyDeviceReport(descMap)) { // library marker kkossev.commonLib, line 1287
                logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 1288
            } // library marker kkossev.commonLib, line 1289
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 1290
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 1291
        } // library marker kkossev.commonLib, line 1292
    } // library marker kkossev.commonLib, line 1293
    else { // library marker kkossev.commonLib, line 1294
        if (this.respondsTo('customParseTuyaCluster')) { // library marker kkossev.commonLib, line 1295
            customParseTuyaCluster(descMap) // library marker kkossev.commonLib, line 1296
        } // library marker kkossev.commonLib, line 1297
        else { // library marker kkossev.commonLib, line 1298
            logWarn "unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 1299
        } // library marker kkossev.commonLib, line 1300
    } // library marker kkossev.commonLib, line 1301
} // library marker kkossev.commonLib, line 1302

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 1304
    logTrace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 1305
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 1306
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 1307
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 1308
            return // library marker kkossev.commonLib, line 1309
        } // library marker kkossev.commonLib, line 1310
    } // library marker kkossev.commonLib, line 1311
    // check if the method  method exists // library marker kkossev.commonLib, line 1312
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 1313
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 1314
            return // library marker kkossev.commonLib, line 1315
        } // library marker kkossev.commonLib, line 1316
    } // library marker kkossev.commonLib, line 1317
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 1318
} // library marker kkossev.commonLib, line 1319

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 1321
    int retValue = 0 // library marker kkossev.commonLib, line 1322
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 1323
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 1324
        int power = 1 // library marker kkossev.commonLib, line 1325
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 1326
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 1327
            power = power * 256 // library marker kkossev.commonLib, line 1328
        } // library marker kkossev.commonLib, line 1329
    } // library marker kkossev.commonLib, line 1330
    return retValue // library marker kkossev.commonLib, line 1331
} // library marker kkossev.commonLib, line 1332

private List<String> getTuyaCommand(String dp, String dp_type, String fncmd) { return sendTuyaCommand(dp, dp_type, fncmd) } // library marker kkossev.commonLib, line 1334

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 1336
    List<String> cmds = [] // library marker kkossev.commonLib, line 1337
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 1338
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1339
    int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 1340
    //tuyaCmd = 0x04  // !!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.commonLib, line 1341
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 1342
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 1343
    return cmds // library marker kkossev.commonLib, line 1344
} // library marker kkossev.commonLib, line 1345

private getPACKET_ID() { // library marker kkossev.commonLib, line 1347
    /* // library marker kkossev.commonLib, line 1348
    int packetId = state.packetId ?: 0 // library marker kkossev.commonLib, line 1349
    state.packetId = packetId + 1 // library marker kkossev.commonLib, line 1350
    return zigbee.convertToHexString(packetId, 4) // library marker kkossev.commonLib, line 1351
    */ // library marker kkossev.commonLib, line 1352
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 1353
} // library marker kkossev.commonLib, line 1354

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1356
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 1357
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 1358
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 1359
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 1360
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 1361
} // library marker kkossev.commonLib, line 1362

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 1364
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 1365

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 1367
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 1368
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1369
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 1370
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 1371
} // library marker kkossev.commonLib, line 1372

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 1374
    List<String> cmds = [] // library marker kkossev.commonLib, line 1375
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1376
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 1377
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1378
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1379
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 1380
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 1381
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 1382
        } // library marker kkossev.commonLib, line 1383
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 1384
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 1385
    } // library marker kkossev.commonLib, line 1386
    else { // library marker kkossev.commonLib, line 1387
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 1388
    } // library marker kkossev.commonLib, line 1389
} // library marker kkossev.commonLib, line 1390

/** // library marker kkossev.commonLib, line 1392
 * initializes the device // library marker kkossev.commonLib, line 1393
 * Invoked from configure() // library marker kkossev.commonLib, line 1394
 * @return zigbee commands // library marker kkossev.commonLib, line 1395
 */ // library marker kkossev.commonLib, line 1396
List<String> initializeDevice() { // library marker kkossev.commonLib, line 1397
    List<String> cmds = [] // library marker kkossev.commonLib, line 1398
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 1399
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 1400
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 1401
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1402
    } // library marker kkossev.commonLib, line 1403
    return cmds // library marker kkossev.commonLib, line 1404
} // library marker kkossev.commonLib, line 1405

/** // library marker kkossev.commonLib, line 1407
 * configures the device // library marker kkossev.commonLib, line 1408
 * Invoked from configure() // library marker kkossev.commonLib, line 1409
 * @return zigbee commands // library marker kkossev.commonLib, line 1410
 */ // library marker kkossev.commonLib, line 1411
List<String> configureDevice() { // library marker kkossev.commonLib, line 1412
    List<String> cmds = [] // library marker kkossev.commonLib, line 1413
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 1414

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 1416
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 1417
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1418
    } // library marker kkossev.commonLib, line 1419
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 1420
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 1421
    return cmds // library marker kkossev.commonLib, line 1422
} // library marker kkossev.commonLib, line 1423

/* // library marker kkossev.commonLib, line 1425
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1426
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 1427
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1428
*/ // library marker kkossev.commonLib, line 1429

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 1431
    List<String> cmds = [] // library marker kkossev.commonLib, line 1432
    if (customHandlersList != null && customHandlersList != []) { // library marker kkossev.commonLib, line 1433
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 1434
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 1435
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 1436
                if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1437
            } // library marker kkossev.commonLib, line 1438
        } // library marker kkossev.commonLib, line 1439
    } // library marker kkossev.commonLib, line 1440
    return cmds // library marker kkossev.commonLib, line 1441
} // library marker kkossev.commonLib, line 1442

void refresh() { // library marker kkossev.commonLib, line 1444
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 1445
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1446
    List<String> cmds = [] // library marker kkossev.commonLib, line 1447
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 1448

    List<String> customCmds = customHandlers(['batteryRefresh', 'groupsRefresh', 'customRefresh']) // library marker kkossev.commonLib, line 1450
    if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1451

    if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 1453
    else { // library marker kkossev.commonLib, line 1454
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 1455
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1456
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1457
        } // library marker kkossev.commonLib, line 1458
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 1459
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1460
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1461
        } // library marker kkossev.commonLib, line 1462
    } // library marker kkossev.commonLib, line 1463

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1465
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1466
    } // library marker kkossev.commonLib, line 1467
    else { // library marker kkossev.commonLib, line 1468
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1469
    } // library marker kkossev.commonLib, line 1470
} // library marker kkossev.commonLib, line 1471

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 1473
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1474

public void clearInfoEvent() { // library marker kkossev.commonLib, line 1476
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 1477
} // library marker kkossev.commonLib, line 1478

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 1480
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 1481
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 1482
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 1483
    } // library marker kkossev.commonLib, line 1484
    else { // library marker kkossev.commonLib, line 1485
        logInfo "${info}" // library marker kkossev.commonLib, line 1486
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 1487
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1488
    } // library marker kkossev.commonLib, line 1489
} // library marker kkossev.commonLib, line 1490

public void ping() { // library marker kkossev.commonLib, line 1492
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1493
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 1494
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1495
    if (isVirtual()) { runInMillis(10, virtualPong) } // library marker kkossev.commonLib, line 1496
    else { sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) } // library marker kkossev.commonLib, line 1497
    logDebug 'ping...' // library marker kkossev.commonLib, line 1498
} // library marker kkossev.commonLib, line 1499

def virtualPong() { // library marker kkossev.commonLib, line 1501
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1502
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1503
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1504
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1505
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1506
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1507
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1508
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1509
        sendRttEvent() // library marker kkossev.commonLib, line 1510
    } // library marker kkossev.commonLib, line 1511
    else { // library marker kkossev.commonLib, line 1512
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1513
    } // library marker kkossev.commonLib, line 1514
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1515
    //unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1516
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1517
} // library marker kkossev.commonLib, line 1518

/** // library marker kkossev.commonLib, line 1520
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 1521
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 1522
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 1523
 * @return none // library marker kkossev.commonLib, line 1524
 */ // library marker kkossev.commonLib, line 1525
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1526
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1527
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1528
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1529
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1530
    if (value == null) { // library marker kkossev.commonLib, line 1531
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1532
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 1533
    } // library marker kkossev.commonLib, line 1534
    else { // library marker kkossev.commonLib, line 1535
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1536
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1537
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 1538
    } // library marker kkossev.commonLib, line 1539
} // library marker kkossev.commonLib, line 1540

/** // library marker kkossev.commonLib, line 1542
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 1543
 * @param cluster cluster ID // library marker kkossev.commonLib, line 1544
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 1545
 */ // library marker kkossev.commonLib, line 1546
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1547
    if (cluster != null) { // library marker kkossev.commonLib, line 1548
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1549
    } // library marker kkossev.commonLib, line 1550
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1551
    return 'NULL' // library marker kkossev.commonLib, line 1552
} // library marker kkossev.commonLib, line 1553

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1555
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1556
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1557
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1558
} // library marker kkossev.commonLib, line 1559

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1561
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :(  // library marker kkossev.commonLib, line 1562
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1563
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1564
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1565
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1566
    } // library marker kkossev.commonLib, line 1567
} // library marker kkossev.commonLib, line 1568

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1570
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1571
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1572
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1573
} // library marker kkossev.commonLib, line 1574

/** // library marker kkossev.commonLib, line 1576
 * Schedule a device health check // library marker kkossev.commonLib, line 1577
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 1578
 */ // library marker kkossev.commonLib, line 1579
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1580
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1581
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1582
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1583
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1584
    } // library marker kkossev.commonLib, line 1585
    else { // library marker kkossev.commonLib, line 1586
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1587
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1588
    } // library marker kkossev.commonLib, line 1589
} // library marker kkossev.commonLib, line 1590

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1592
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1593
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1594
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1595
} // library marker kkossev.commonLib, line 1596

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1598

void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1600
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1601
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1602
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1603
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1604
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1605
    } // library marker kkossev.commonLib, line 1606
} // library marker kkossev.commonLib, line 1607

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1609
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1610
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1611
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1612
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1613
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1614
            logWarn 'not present!' // library marker kkossev.commonLib, line 1615
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1616
        } // library marker kkossev.commonLib, line 1617
    } // library marker kkossev.commonLib, line 1618
    else { // library marker kkossev.commonLib, line 1619
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1620
    } // library marker kkossev.commonLib, line 1621
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1622
} // library marker kkossev.commonLib, line 1623

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1625
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1626
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 1627
    if (value == 'online') { // library marker kkossev.commonLib, line 1628
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1629
    } // library marker kkossev.commonLib, line 1630
    else { // library marker kkossev.commonLib, line 1631
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1632
    } // library marker kkossev.commonLib, line 1633
} // library marker kkossev.commonLib, line 1634

/** // library marker kkossev.commonLib, line 1636
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 1637
 */ // library marker kkossev.commonLib, line 1638
void autoPoll() { // library marker kkossev.commonLib, line 1639
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 1640
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1641
    List<String> cmds = [] // library marker kkossev.commonLib, line 1642
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 1643
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 1644
    } // library marker kkossev.commonLib, line 1645

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1647
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1648
    } // library marker kkossev.commonLib, line 1649
} // library marker kkossev.commonLib, line 1650

/** // library marker kkossev.commonLib, line 1652
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1653
 */ // library marker kkossev.commonLib, line 1654
void updated() { // library marker kkossev.commonLib, line 1655
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1656
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1657
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1658
    unschedule() // library marker kkossev.commonLib, line 1659

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1661
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1662
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1663
    } // library marker kkossev.commonLib, line 1664
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1665
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1666
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1667
    } // library marker kkossev.commonLib, line 1668

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1670
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1671
        // schedule the periodic timer // library marker kkossev.commonLib, line 1672
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1673
        if (interval > 0) { // library marker kkossev.commonLib, line 1674
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1675
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1676
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1677
        } // library marker kkossev.commonLib, line 1678
    } // library marker kkossev.commonLib, line 1679
    else { // library marker kkossev.commonLib, line 1680
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1681
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1682
    } // library marker kkossev.commonLib, line 1683
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1684
        customUpdated() // library marker kkossev.commonLib, line 1685
    } // library marker kkossev.commonLib, line 1686

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1688
} // library marker kkossev.commonLib, line 1689

/** // library marker kkossev.commonLib, line 1691
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 1692
 */ // library marker kkossev.commonLib, line 1693
void logsOff() { // library marker kkossev.commonLib, line 1694
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1695
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1696
} // library marker kkossev.commonLib, line 1697
void traceOff() { // library marker kkossev.commonLib, line 1698
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1699
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1700
} // library marker kkossev.commonLib, line 1701

void configure(String command) { // library marker kkossev.commonLib, line 1703
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1704
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1705
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1706
        return // library marker kkossev.commonLib, line 1707
    } // library marker kkossev.commonLib, line 1708
    // // library marker kkossev.commonLib, line 1709
    String func // library marker kkossev.commonLib, line 1710
    try { // library marker kkossev.commonLib, line 1711
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1712
        "$func"() // library marker kkossev.commonLib, line 1713
    } // library marker kkossev.commonLib, line 1714
    catch (e) { // library marker kkossev.commonLib, line 1715
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1716
        return // library marker kkossev.commonLib, line 1717
    } // library marker kkossev.commonLib, line 1718
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1719
} // library marker kkossev.commonLib, line 1720

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1722
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1723
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1724
} // library marker kkossev.commonLib, line 1725

void loadAllDefaults() { // library marker kkossev.commonLib, line 1727
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1728
    deleteAllSettings() // library marker kkossev.commonLib, line 1729
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1730
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1731
    deleteAllStates() // library marker kkossev.commonLib, line 1732
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1733
    initialize() // library marker kkossev.commonLib, line 1734
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1735
    updated() // library marker kkossev.commonLib, line 1736
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1737
} // library marker kkossev.commonLib, line 1738

void configureNow() { // library marker kkossev.commonLib, line 1740
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 1741
} // library marker kkossev.commonLib, line 1742

/** // library marker kkossev.commonLib, line 1744
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1745
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1746
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1747
 */ // library marker kkossev.commonLib, line 1748
List<String> configure() { // library marker kkossev.commonLib, line 1749
    List<String> cmds = [] // library marker kkossev.commonLib, line 1750
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1751
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1752
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1753
    if (isTuya()) { // library marker kkossev.commonLib, line 1754
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1755
    } // library marker kkossev.commonLib, line 1756
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1757
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1758
    } // library marker kkossev.commonLib, line 1759
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1760
    if (initCmds != null && initCmds != [] ) { cmds += initCmds } // library marker kkossev.commonLib, line 1761
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1762
    if (cfgCmds != null && cfgCmds != [] ) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1763
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1764
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1765
    logDebug "configure(): returning cmds = ${cmds}" // library marker kkossev.commonLib, line 1766
    //return cmds // library marker kkossev.commonLib, line 1767
    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1768
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1769
    } // library marker kkossev.commonLib, line 1770
    else { // library marker kkossev.commonLib, line 1771
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1772
    } // library marker kkossev.commonLib, line 1773
} // library marker kkossev.commonLib, line 1774

/** // library marker kkossev.commonLib, line 1776
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 1777
 */ // library marker kkossev.commonLib, line 1778
void installed() { // library marker kkossev.commonLib, line 1779
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1780
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1781
    // populate some default values for attributes // library marker kkossev.commonLib, line 1782
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 1783
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 1784
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1785
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1786
} // library marker kkossev.commonLib, line 1787

/** // library marker kkossev.commonLib, line 1789
 * Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1790
 */ // library marker kkossev.commonLib, line 1791
void initialize() { // library marker kkossev.commonLib, line 1792
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1793
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1794
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1795
    updateTuyaVersion() // library marker kkossev.commonLib, line 1796
    updateAqaraVersion() // library marker kkossev.commonLib, line 1797
} // library marker kkossev.commonLib, line 1798

/* // library marker kkossev.commonLib, line 1800
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1801
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1802
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1803
*/ // library marker kkossev.commonLib, line 1804

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1806
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1807
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1808
} // library marker kkossev.commonLib, line 1809

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1811
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1812
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1813
} // library marker kkossev.commonLib, line 1814

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1816
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1817
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1818
} // library marker kkossev.commonLib, line 1819

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1821
    if (cmd == null || cmd == 'null' || cmd == [] || cmd == [null]) { // library marker kkossev.commonLib, line 1822
        logWarn "sendZigbeeCommands: no commands to send! cmd=${cmd}" // library marker kkossev.commonLib, line 1823
        return // library marker kkossev.commonLib, line 1824
    } // library marker kkossev.commonLib, line 1825
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 1826
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1827
    cmd.each { // library marker kkossev.commonLib, line 1828
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1829
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1830
    } // library marker kkossev.commonLib, line 1831
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1832
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1833
} // library marker kkossev.commonLib, line 1834

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1836

String getDeviceInfo() { // library marker kkossev.commonLib, line 1838
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1839
} // library marker kkossev.commonLib, line 1840

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1842
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1843
} // library marker kkossev.commonLib, line 1844

@CompileStatic // library marker kkossev.commonLib, line 1846
void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1847
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1848
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1849
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1850
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1851
        initializeVars(false) // library marker kkossev.commonLib, line 1852
        updateTuyaVersion() // library marker kkossev.commonLib, line 1853
        updateAqaraVersion() // library marker kkossev.commonLib, line 1854
    } // library marker kkossev.commonLib, line 1855
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1856
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1857
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1858
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1859
} // library marker kkossev.commonLib, line 1860


// credits @thebearmay // library marker kkossev.commonLib, line 1863
String getModel() { // library marker kkossev.commonLib, line 1864
    try { // library marker kkossev.commonLib, line 1865
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1866
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1867
    } catch (ignore) { // library marker kkossev.commonLib, line 1868
        try { // library marker kkossev.commonLib, line 1869
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1870
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1871
                return model // library marker kkossev.commonLib, line 1872
            } // library marker kkossev.commonLib, line 1873
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1874
            return '' // library marker kkossev.commonLib, line 1875
        } // library marker kkossev.commonLib, line 1876
    } // library marker kkossev.commonLib, line 1877
} // library marker kkossev.commonLib, line 1878

// credits @thebearmay // library marker kkossev.commonLib, line 1880
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1881
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1882
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1883
    String revision = tokens.last() // library marker kkossev.commonLib, line 1884
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1885
} // library marker kkossev.commonLib, line 1886

/** // library marker kkossev.commonLib, line 1888
 * called from TODO // library marker kkossev.commonLib, line 1889
 */ // library marker kkossev.commonLib, line 1890

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1892
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1893
    unschedule() // library marker kkossev.commonLib, line 1894
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1895
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1896

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1898
} // library marker kkossev.commonLib, line 1899

void resetStatistics() { // library marker kkossev.commonLib, line 1901
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1902
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1903
} // library marker kkossev.commonLib, line 1904

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1906
void resetStats() { // library marker kkossev.commonLib, line 1907
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1908
    state.stats = [:] // library marker kkossev.commonLib, line 1909
    state.states = [:] // library marker kkossev.commonLib, line 1910
    state.lastRx = [:] // library marker kkossev.commonLib, line 1911
    state.lastTx = [:] // library marker kkossev.commonLib, line 1912
    state.health = [:] // library marker kkossev.commonLib, line 1913
    if (this.respondsTo('groupsLibVersion')) { // library marker kkossev.commonLib, line 1914
        state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 1915
    } // library marker kkossev.commonLib, line 1916
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 1917
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1918
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 1919
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 1920
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 1921
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1922
} // library marker kkossev.commonLib, line 1923

/** // library marker kkossev.commonLib, line 1925
 * called from TODO // library marker kkossev.commonLib, line 1926
 */ // library marker kkossev.commonLib, line 1927
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1928
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1929
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1930
        state.clear() // library marker kkossev.commonLib, line 1931
        unschedule() // library marker kkossev.commonLib, line 1932
        resetStats() // library marker kkossev.commonLib, line 1933
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 1934
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1935
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1936
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1937
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1938
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1939
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1940
    } // library marker kkossev.commonLib, line 1941

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1943
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1944
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1945
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1946
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1947

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1949
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1950
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1951
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 1952
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1953
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1954
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1955
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1956
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1957
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 1958

    // common libraries initialization - TODO !!!!!!!!!!!!! // library marker kkossev.commonLib, line 1960
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1961
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1962
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1963

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1965
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1966
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1967
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1968
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 1969

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1971
    if ( mm != null) { // library marker kkossev.commonLib, line 1972
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 1973
    } // library marker kkossev.commonLib, line 1974
    else { // library marker kkossev.commonLib, line 1975
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 1976
    } // library marker kkossev.commonLib, line 1977
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1978
    if ( ep  != null) { // library marker kkossev.commonLib, line 1979
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1980
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1981
    } // library marker kkossev.commonLib, line 1982
    else { // library marker kkossev.commonLib, line 1983
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1984
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1985
    } // library marker kkossev.commonLib, line 1986
} // library marker kkossev.commonLib, line 1987

/** // library marker kkossev.commonLib, line 1989
 * called from TODO // library marker kkossev.commonLib, line 1990
 */ // library marker kkossev.commonLib, line 1991
void setDestinationEP() { // library marker kkossev.commonLib, line 1992
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1993
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1994
        state.destinationEP = ep // library marker kkossev.commonLib, line 1995
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1996
    } // library marker kkossev.commonLib, line 1997
    else { // library marker kkossev.commonLib, line 1998
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1999
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 2000
    } // library marker kkossev.commonLib, line 2001
} // library marker kkossev.commonLib, line 2002

void logDebug(final String msg) { // library marker kkossev.commonLib, line 2004
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2005
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2006
    } // library marker kkossev.commonLib, line 2007
} // library marker kkossev.commonLib, line 2008

void logInfo(final String msg) { // library marker kkossev.commonLib, line 2010
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 2011
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2012
    } // library marker kkossev.commonLib, line 2013
} // library marker kkossev.commonLib, line 2014

void logWarn(final String msg) { // library marker kkossev.commonLib, line 2016
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2017
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2018
    } // library marker kkossev.commonLib, line 2019
} // library marker kkossev.commonLib, line 2020

void logTrace(final String msg) { // library marker kkossev.commonLib, line 2022
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 2023
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2024
    } // library marker kkossev.commonLib, line 2025
} // library marker kkossev.commonLib, line 2026

// _DEBUG mode only // library marker kkossev.commonLib, line 2028
void getAllProperties() { // library marker kkossev.commonLib, line 2029
    log.trace 'Properties:' // library marker kkossev.commonLib, line 2030
    device.properties.each { it -> // library marker kkossev.commonLib, line 2031
        log.debug it // library marker kkossev.commonLib, line 2032
    } // library marker kkossev.commonLib, line 2033
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2034
    settings.each { it -> // library marker kkossev.commonLib, line 2035
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2036
    } // library marker kkossev.commonLib, line 2037
    log.trace 'Done' // library marker kkossev.commonLib, line 2038
} // library marker kkossev.commonLib, line 2039

// delete all Preferences // library marker kkossev.commonLib, line 2041
void deleteAllSettings() { // library marker kkossev.commonLib, line 2042
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 2043
    settings.each { it -> // library marker kkossev.commonLib, line 2044
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 2045
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2046
    } // library marker kkossev.commonLib, line 2047
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 2048
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2049
} // library marker kkossev.commonLib, line 2050

// delete all attributes // library marker kkossev.commonLib, line 2052
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2053
    String attributesDeleted = '' // library marker kkossev.commonLib, line 2054
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 2055
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2056
} // library marker kkossev.commonLib, line 2057

// delete all State Variables // library marker kkossev.commonLib, line 2059
void deleteAllStates() { // library marker kkossev.commonLib, line 2060
    String stateDeleted = '' // library marker kkossev.commonLib, line 2061
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 2062
    state.clear() // library marker kkossev.commonLib, line 2063
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2064
} // library marker kkossev.commonLib, line 2065

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2067
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2068
} // library marker kkossev.commonLib, line 2069

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2071
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 2072
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 2073
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 2074
    } // library marker kkossev.commonLib, line 2075
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 2076
} // library marker kkossev.commonLib, line 2077

void parseTest(String par) { // library marker kkossev.commonLib, line 2079
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2080
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2081
    parse(par) // library marker kkossev.commonLib, line 2082
} // library marker kkossev.commonLib, line 2083

def testJob() { // library marker kkossev.commonLib, line 2085
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2086
} // library marker kkossev.commonLib, line 2087

/** // library marker kkossev.commonLib, line 2089
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2090
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2091
 */ // library marker kkossev.commonLib, line 2092
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2093
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2094
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2095
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2096
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2097
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2098
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2099
    String cron // library marker kkossev.commonLib, line 2100
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2101
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2102
    } // library marker kkossev.commonLib, line 2103
    else { // library marker kkossev.commonLib, line 2104
        if (minutes < 60) { // library marker kkossev.commonLib, line 2105
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2106
        } // library marker kkossev.commonLib, line 2107
        else { // library marker kkossev.commonLib, line 2108
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2109
        } // library marker kkossev.commonLib, line 2110
    } // library marker kkossev.commonLib, line 2111
    return cron // library marker kkossev.commonLib, line 2112
} // library marker kkossev.commonLib, line 2113

// credits @thebearmay // library marker kkossev.commonLib, line 2115
String formatUptime() { // library marker kkossev.commonLib, line 2116
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2117
} // library marker kkossev.commonLib, line 2118

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2120
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2121
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2122
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2123
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2124
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2125
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2126
} // library marker kkossev.commonLib, line 2127

boolean isTuya() { // library marker kkossev.commonLib, line 2129
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 2130
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2131
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2132
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2133
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2134
} // library marker kkossev.commonLib, line 2135

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2137
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 2138
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2139
    if (application != null) { // library marker kkossev.commonLib, line 2140
        Integer ver // library marker kkossev.commonLib, line 2141
        try { // library marker kkossev.commonLib, line 2142
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2143
        } // library marker kkossev.commonLib, line 2144
        catch (e) { // library marker kkossev.commonLib, line 2145
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2146
            return // library marker kkossev.commonLib, line 2147
        } // library marker kkossev.commonLib, line 2148
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2149
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2150
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2151
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2152
        } // library marker kkossev.commonLib, line 2153
    } // library marker kkossev.commonLib, line 2154
} // library marker kkossev.commonLib, line 2155

boolean isAqara() { // library marker kkossev.commonLib, line 2157
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2158
} // library marker kkossev.commonLib, line 2159

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2161
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 2162
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2163
    if (application != null) { // library marker kkossev.commonLib, line 2164
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2165
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2166
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2167
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2168
        } // library marker kkossev.commonLib, line 2169
    } // library marker kkossev.commonLib, line 2170
} // library marker kkossev.commonLib, line 2171

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2173
    try { // library marker kkossev.commonLib, line 2174
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2175
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2176
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2177
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2178
    } catch (e) { // library marker kkossev.commonLib, line 2179
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2180
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2181
    } // library marker kkossev.commonLib, line 2182
} // library marker kkossev.commonLib, line 2183

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2185
    try { // library marker kkossev.commonLib, line 2186
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2187
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2188
        return date.getTime() // library marker kkossev.commonLib, line 2189
    } catch (e) { // library marker kkossev.commonLib, line 2190
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2191
        return now() // library marker kkossev.commonLib, line 2192
    } // library marker kkossev.commonLib, line 2193
} // library marker kkossev.commonLib, line 2194
/* // library marker kkossev.commonLib, line 2195
void test(String par) { // library marker kkossev.commonLib, line 2196
    List<String> cmds = [] // library marker kkossev.commonLib, line 2197
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2198

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2200
    //parse(par) // library marker kkossev.commonLib, line 2201

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2203
} // library marker kkossev.commonLib, line 2204
*/ // library marker kkossev.commonLib, line 2205

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
 * ver. 3.1.1  2024-04-21 kkossev  - (dev. branch) deviceProfilesV3 bug fix; tuyaDPs list of maps bug fix; resetPreferencesToDefaults bug fix; // library marker kkossev.deviceProfileLib, line 31
 * // library marker kkossev.deviceProfileLib, line 32
 *                                   TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 33
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 34
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 35
 * // library marker kkossev.deviceProfileLib, line 36
*/ // library marker kkossev.deviceProfileLib, line 37

static String deviceProfileLibVersion()   { '3.1.1' } // library marker kkossev.deviceProfileLib, line 39
static String deviceProfileLibStamp() { '2024/04/21 7:29 PM' } // library marker kkossev.deviceProfileLib, line 40
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
void resetPreferencesToDefaults(boolean debug=true) { // library marker kkossev.deviceProfileLib, line 172
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 173
    Map preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 174
    if (preferences == null || preferences.isEmpty()) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 175
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 176
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 177
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 178
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 179
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 180
            return // continue // library marker kkossev.deviceProfileLib, line 181
        } // library marker kkossev.deviceProfileLib, line 182
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 183
        if (parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 184
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'<i>Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 185
        if (parMap.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 186
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 187
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 188
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 189
    } // library marker kkossev.deviceProfileLib, line 190
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 191
} // library marker kkossev.deviceProfileLib, line 192

/** // library marker kkossev.deviceProfileLib, line 194
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 195
 * // library marker kkossev.deviceProfileLib, line 196
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 197
 */ // library marker kkossev.deviceProfileLib, line 198
List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 199
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 200
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 201
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 202
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 203
    } // library marker kkossev.deviceProfileLib, line 204
    return validPars // library marker kkossev.deviceProfileLib, line 205
} // library marker kkossev.deviceProfileLib, line 206

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 208
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 209
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 210
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 211
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 212
    def scaledValue // library marker kkossev.deviceProfileLib, line 213
    if (value == null) { // library marker kkossev.deviceProfileLib, line 214
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 215
        return null // library marker kkossev.deviceProfileLib, line 216
    } // library marker kkossev.deviceProfileLib, line 217
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 218
        case 'number' : // library marker kkossev.deviceProfileLib, line 219
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 220
            break // library marker kkossev.deviceProfileLib, line 221
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 222
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 223
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 224
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 225
            } // library marker kkossev.deviceProfileLib, line 226
            break // library marker kkossev.deviceProfileLib, line 227
        case 'bool' : // library marker kkossev.deviceProfileLib, line 228
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 229
            break // library marker kkossev.deviceProfileLib, line 230
        case 'enum' : // library marker kkossev.deviceProfileLib, line 231
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 232
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 233
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 234
                return null // library marker kkossev.deviceProfileLib, line 235
            } // library marker kkossev.deviceProfileLib, line 236
            scaledValue = value // library marker kkossev.deviceProfileLib, line 237
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 238
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 239
            } // library marker kkossev.deviceProfileLib, line 240
            break // library marker kkossev.deviceProfileLib, line 241
        default : // library marker kkossev.deviceProfileLib, line 242
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 243
            return null // library marker kkossev.deviceProfileLib, line 244
    } // library marker kkossev.deviceProfileLib, line 245
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 246
    return scaledValue // library marker kkossev.deviceProfileLib, line 247
} // library marker kkossev.deviceProfileLib, line 248

// called from updated() method // library marker kkossev.deviceProfileLib, line 250
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 251
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 252
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 253
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 254
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 255
        return // library marker kkossev.deviceProfileLib, line 256
    } // library marker kkossev.deviceProfileLib, line 257
    //Integer dpInt = 0 // library marker kkossev.deviceProfileLib, line 258
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 259
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 260
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 261
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 262
        Map foundMap // library marker kkossev.deviceProfileLib, line 263
        foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 264
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 265

        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 267
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 268
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 269
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 270
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 271
                // scale the value // library marker kkossev.deviceProfileLib, line 272
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 273
            } // library marker kkossev.deviceProfileLib, line 274
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLib, line 275
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLib, line 276
            } // library marker kkossev.deviceProfileLib, line 277
            else { // library marker kkossev.deviceProfileLib, line 278
                logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" // library marker kkossev.deviceProfileLib, line 279
                return // library marker kkossev.deviceProfileLib, line 280
            } // library marker kkossev.deviceProfileLib, line 281
        } // library marker kkossev.deviceProfileLib, line 282
        else { // library marker kkossev.deviceProfileLib, line 283
            logDebug "warning: couldn't find map for preference ${name}" // library marker kkossev.deviceProfileLib, line 284
            return // library marker kkossev.deviceProfileLib, line 285
        } // library marker kkossev.deviceProfileLib, line 286
    } // library marker kkossev.deviceProfileLib, line 287
    return // library marker kkossev.deviceProfileLib, line 288
} // library marker kkossev.deviceProfileLib, line 289

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 291
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 292
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 293
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 294
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 295
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 296
} // library marker kkossev.deviceProfileLib, line 297
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 298
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 299
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 300
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 301
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 302
} // library marker kkossev.deviceProfileLib, line 303

/** // library marker kkossev.deviceProfileLib, line 305
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 306
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 307
 * // library marker kkossev.deviceProfileLib, line 308
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 309
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 310
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 311
 */ // library marker kkossev.deviceProfileLib, line 312
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 313
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 314
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 315
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 316
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 317
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 318
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 319
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 320
        case 'number' : // library marker kkossev.deviceProfileLib, line 321
            value = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 322
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 323
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 324
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 325
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 326
            } // library marker kkossev.deviceProfileLib, line 327
            else { // library marker kkossev.deviceProfileLib, line 328
                scaledValue = value // library marker kkossev.deviceProfileLib, line 329
            } // library marker kkossev.deviceProfileLib, line 330
            break // library marker kkossev.deviceProfileLib, line 331

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 333
            value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 334
            // scale the value // library marker kkossev.deviceProfileLib, line 335
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 336
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 337
            } // library marker kkossev.deviceProfileLib, line 338
            else { // library marker kkossev.deviceProfileLib, line 339
                scaledValue = value // library marker kkossev.deviceProfileLib, line 340
            } // library marker kkossev.deviceProfileLib, line 341
            break // library marker kkossev.deviceProfileLib, line 342

        case 'bool' : // library marker kkossev.deviceProfileLib, line 344
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 345
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 346
            else { // library marker kkossev.deviceProfileLib, line 347
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 348
                return null // library marker kkossev.deviceProfileLib, line 349
            } // library marker kkossev.deviceProfileLib, line 350
            break // library marker kkossev.deviceProfileLib, line 351
        case 'enum' : // library marker kkossev.deviceProfileLib, line 352
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 353
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 354
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 355
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 356
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 357
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 358
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 359
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 360
            } // library marker kkossev.deviceProfileLib, line 361
            else { // library marker kkossev.deviceProfileLib, line 362
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 363
            } // library marker kkossev.deviceProfileLib, line 364
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 365
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 366
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 367
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 368
                // find the key for the value // library marker kkossev.deviceProfileLib, line 369
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 370
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 371
                if (key == null) { // library marker kkossev.deviceProfileLib, line 372
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 373
                    return null // library marker kkossev.deviceProfileLib, line 374
                } // library marker kkossev.deviceProfileLib, line 375
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 376
            //return null // library marker kkossev.deviceProfileLib, line 377
            } // library marker kkossev.deviceProfileLib, line 378
            break // library marker kkossev.deviceProfileLib, line 379
        default : // library marker kkossev.deviceProfileLib, line 380
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 381
            return null // library marker kkossev.deviceProfileLib, line 382
    } // library marker kkossev.deviceProfileLib, line 383
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 384
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 385
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 386
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 387
        return null // library marker kkossev.deviceProfileLib, line 388
    } // library marker kkossev.deviceProfileLib, line 389
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 390
    return scaledValue // library marker kkossev.deviceProfileLib, line 391
} // library marker kkossev.deviceProfileLib, line 392

/** // library marker kkossev.deviceProfileLib, line 394
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 395
 * // library marker kkossev.deviceProfileLib, line 396
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 397
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 398
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 399
 */ // library marker kkossev.deviceProfileLib, line 400
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 401
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 402
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 403
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 404
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 405
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 406
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 407
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 408
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 409
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 410
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 411
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 412
    if (scaledValue == null) { logInfo "setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 413
    /* // library marker kkossev.deviceProfileLib, line 414
    // update the device setting // TODO: decide whether the setting must be updated here, or after it is echeod back from the device // library marker kkossev.deviceProfileLib, line 415
    try { // library marker kkossev.deviceProfileLib, line 416
        device.updateSetting("$par", [value:val, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 417
    } // library marker kkossev.deviceProfileLib, line 418
    catch (e) { // library marker kkossev.deviceProfileLib, line 419
        logWarn "setPar: Exception '${e}'caught while updateSetting <b>$par</b>(<b>$val</b>) type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 420
        return false // library marker kkossev.deviceProfileLib, line 421
    } // library marker kkossev.deviceProfileLib, line 422
    */ // library marker kkossev.deviceProfileLib, line 423
    //logDebug "setPar: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 424
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 425
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 426
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 427
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 428
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 429
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 430
        try { // library marker kkossev.deviceProfileLib, line 431
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 432
        } // library marker kkossev.deviceProfileLib, line 433
        catch (e) { // library marker kkossev.deviceProfileLib, line 434
            logWarn "setPar: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 435
            return false // library marker kkossev.deviceProfileLib, line 436
        } // library marker kkossev.deviceProfileLib, line 437
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 438
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 439
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 440
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 441
            return false // library marker kkossev.deviceProfileLib, line 442
        } // library marker kkossev.deviceProfileLib, line 443
        else { // library marker kkossev.deviceProfileLib, line 444
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 445
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 446
        } // library marker kkossev.deviceProfileLib, line 447
    } // library marker kkossev.deviceProfileLib, line 448
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 449
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 450
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 451
        def valMiscType // library marker kkossev.deviceProfileLib, line 452
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 453
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 454
            // find the key for the value // library marker kkossev.deviceProfileLib, line 455
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 456
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 457
            if (key == null) { // library marker kkossev.deviceProfileLib, line 458
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 459
                return false // library marker kkossev.deviceProfileLib, line 460
            } // library marker kkossev.deviceProfileLib, line 461
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 462
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 463
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 464
        } // library marker kkossev.deviceProfileLib, line 465
        else { // library marker kkossev.deviceProfileLib, line 466
            valMiscType = val // library marker kkossev.deviceProfileLib, line 467
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 468
        } // library marker kkossev.deviceProfileLib, line 469
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 470
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 471
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 472
        return true // library marker kkossev.deviceProfileLib, line 473
    } // library marker kkossev.deviceProfileLib, line 474

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 476
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 477

    try { // library marker kkossev.deviceProfileLib, line 479
        // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 480
        /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 481
        isTuyaDP = dpMap.dp instanceof Number // library marker kkossev.deviceProfileLib, line 482
    } // library marker kkossev.deviceProfileLib, line 483
    catch (e) { // library marker kkossev.deviceProfileLib, line 484
        logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" // library marker kkossev.deviceProfileLib, line 485
        isTuyaDP = false // library marker kkossev.deviceProfileLib, line 486
    //return false // library marker kkossev.deviceProfileLib, line 487
    } // library marker kkossev.deviceProfileLib, line 488
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 489
        // Tuya DP // library marker kkossev.deviceProfileLib, line 490
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 491
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 492
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 493
            return false // library marker kkossev.deviceProfileLib, line 494
        } // library marker kkossev.deviceProfileLib, line 495
        else { // library marker kkossev.deviceProfileLib, line 496
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 497
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 498
            return false // library marker kkossev.deviceProfileLib, line 499
        } // library marker kkossev.deviceProfileLib, line 500
    } // library marker kkossev.deviceProfileLib, line 501
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 502
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 503
        int cluster // library marker kkossev.deviceProfileLib, line 504
        int attribute // library marker kkossev.deviceProfileLib, line 505
        int dt // library marker kkossev.deviceProfileLib, line 506
        String mfgCode // library marker kkossev.deviceProfileLib, line 507
        try { // library marker kkossev.deviceProfileLib, line 508
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[0]) // library marker kkossev.deviceProfileLib, line 509
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 510
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[1]) // library marker kkossev.deviceProfileLib, line 511
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 512
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 513
            //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 514
            mfgCode = dpMap.mfgCode // library marker kkossev.deviceProfileLib, line 515
        //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 516
        } // library marker kkossev.deviceProfileLib, line 517
        catch (e) { // library marker kkossev.deviceProfileLib, line 518
            logWarn "setPar: Exception '${e}' caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 519
            return false // library marker kkossev.deviceProfileLib, line 520
        } // library marker kkossev.deviceProfileLib, line 521
        Map mapMfCode = ['mfgCode':mfgCode] // library marker kkossev.deviceProfileLib, line 522
        logDebug "setPar: found cluster=0x${zigbee.convertToHexString(cluster, 2)} attribute=0x${zigbee.convertToHexString(attribute, 2)} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 523
        if (mfgCode != null) { // library marker kkossev.deviceProfileLib, line 524
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay = 200) // library marker kkossev.deviceProfileLib, line 525
        } // library marker kkossev.deviceProfileLib, line 526
        else { // library marker kkossev.deviceProfileLib, line 527
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 528
        } // library marker kkossev.deviceProfileLib, line 529
    } // library marker kkossev.deviceProfileLib, line 530
    else { // library marker kkossev.deviceProfileLib, line 531
        logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 532
        return false // library marker kkossev.deviceProfileLib, line 533
    } // library marker kkossev.deviceProfileLib, line 534
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 535
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 536
    return true // library marker kkossev.deviceProfileLib, line 537
} // library marker kkossev.deviceProfileLib, line 538

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 540
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 541
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 542
List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 543
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 544
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 545
    if (dpMap == null) { // library marker kkossev.deviceProfileLib, line 546
        logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 547
        return [] // library marker kkossev.deviceProfileLib, line 548
    } // library marker kkossev.deviceProfileLib, line 549
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 550
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 551
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 552
        return [] // library marker kkossev.deviceProfileLib, line 553
    } // library marker kkossev.deviceProfileLib, line 554
    String dpType // library marker kkossev.deviceProfileLib, line 555
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 556
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 557
    } // library marker kkossev.deviceProfileLib, line 558
    else { // library marker kkossev.deviceProfileLib, line 559
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 560
    } // library marker kkossev.deviceProfileLib, line 561
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 562
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 563
        return [] // library marker kkossev.deviceProfileLib, line 564
    } // library marker kkossev.deviceProfileLib, line 565
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 566
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 567
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 568
    cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 569
    return cmds // library marker kkossev.deviceProfileLib, line 570
} // library marker kkossev.deviceProfileLib, line 571

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 573
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 574
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 575
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 576
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 577
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 578

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 580
    if (dpMap == null || dpMap.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 581
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 582
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 583
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 584
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 585
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 586
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 587
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 588
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 589
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 590
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 591
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 592
        try { // library marker kkossev.deviceProfileLib, line 593
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 594
        } // library marker kkossev.deviceProfileLib, line 595
        catch (e) { // library marker kkossev.deviceProfileLib, line 596
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 597
            return false // library marker kkossev.deviceProfileLib, line 598
        } // library marker kkossev.deviceProfileLib, line 599
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 600
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 601
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 602
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 603
            return true // library marker kkossev.deviceProfileLib, line 604
        } // library marker kkossev.deviceProfileLib, line 605
        else { // library marker kkossev.deviceProfileLib, line 606
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 607
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 608
        } // library marker kkossev.deviceProfileLib, line 609
    } // library marker kkossev.deviceProfileLib, line 610
    else { // library marker kkossev.deviceProfileLib, line 611
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 612
    } // library marker kkossev.deviceProfileLib, line 613
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 614
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 615
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 616
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 617
        // patch !! // library marker kkossev.deviceProfileLib, line 618
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 619
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 620
        } // library marker kkossev.deviceProfileLib, line 621
        else { // library marker kkossev.deviceProfileLib, line 622
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 623
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 624
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 625
        } // library marker kkossev.deviceProfileLib, line 626
        return true // library marker kkossev.deviceProfileLib, line 627
    } // library marker kkossev.deviceProfileLib, line 628
    else { // library marker kkossev.deviceProfileLib, line 629
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 630
    } // library marker kkossev.deviceProfileLib, line 631
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 632
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 633
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 634
    try { // library marker kkossev.deviceProfileLib, line 635
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 636
    } // library marker kkossev.deviceProfileLib, line 637
    catch (e) { // library marker kkossev.deviceProfileLib, line 638
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 639
        return false // library marker kkossev.deviceProfileLib, line 640
    } // library marker kkossev.deviceProfileLib, line 641
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 642
        // Tuya DP // library marker kkossev.deviceProfileLib, line 643
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 644
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 645
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 646
            return false // library marker kkossev.deviceProfileLib, line 647
        } // library marker kkossev.deviceProfileLib, line 648
        else { // library marker kkossev.deviceProfileLib, line 649
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 650
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 651
            return true // library marker kkossev.deviceProfileLib, line 652
        } // library marker kkossev.deviceProfileLib, line 653
    } // library marker kkossev.deviceProfileLib, line 654
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 655
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 656
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 657
    } // library marker kkossev.deviceProfileLib, line 658
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 659
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 660
        int cluster // library marker kkossev.deviceProfileLib, line 661
        int attribute // library marker kkossev.deviceProfileLib, line 662
        int dt // library marker kkossev.deviceProfileLib, line 663
        // int mfgCode // library marker kkossev.deviceProfileLib, line 664
        try { // library marker kkossev.deviceProfileLib, line 665
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[0]) // library marker kkossev.deviceProfileLib, line 666
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 667
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[1]) // library marker kkossev.deviceProfileLib, line 668
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 669
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 670
        //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 671
        //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 672
        //  mfgCode = dpMap.mfgCode != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.mfgCode) : null // library marker kkossev.deviceProfileLib, line 673
        //  log.trace "mfgCode = ${mfgCode}" // library marker kkossev.deviceProfileLib, line 674
        } // library marker kkossev.deviceProfileLib, line 675
        catch (e) { // library marker kkossev.deviceProfileLib, line 676
            logWarn "sendAttribute: Exception '${e}'caught while splitting cluster and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 677
            return false // library marker kkossev.deviceProfileLib, line 678
        } // library marker kkossev.deviceProfileLib, line 679

        logDebug "sendAttribute: found cluster=${cluster} attribute=${attribute} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 681
        if (dpMap.mfgCode != null) { // library marker kkossev.deviceProfileLib, line 682
            Map mapMfCode = ['mfgCode':dpMap.mfgCode] // library marker kkossev.deviceProfileLib, line 683
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay = 200) // library marker kkossev.deviceProfileLib, line 684
        } // library marker kkossev.deviceProfileLib, line 685
        else { // library marker kkossev.deviceProfileLib, line 686
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 687
        } // library marker kkossev.deviceProfileLib, line 688
    } // library marker kkossev.deviceProfileLib, line 689
    else { // library marker kkossev.deviceProfileLib, line 690
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 691
        return false // library marker kkossev.deviceProfileLib, line 692
    } // library marker kkossev.deviceProfileLib, line 693
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 694
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 695
    return true // library marker kkossev.deviceProfileLib, line 696
} // library marker kkossev.deviceProfileLib, line 697

/** // library marker kkossev.deviceProfileLib, line 699
 * Sends a command to the device. // library marker kkossev.deviceProfileLib, line 700
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 701
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 702
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 703
 */ // library marker kkossev.deviceProfileLib, line 704
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 705
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 706
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 707
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 708
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 709
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 710
    if (supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 711
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 712
        return false // library marker kkossev.deviceProfileLib, line 713
    } // library marker kkossev.deviceProfileLib, line 714
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 715
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 716
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 717
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 718
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 719
        return false // library marker kkossev.deviceProfileLib, line 720
    } // library marker kkossev.deviceProfileLib, line 721
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 722
    def func // library marker kkossev.deviceProfileLib, line 723
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 724
    def funcResult // library marker kkossev.deviceProfileLib, line 725
    try { // library marker kkossev.deviceProfileLib, line 726
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 727
        if (val != null) { // library marker kkossev.deviceProfileLib, line 728
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 729
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 730
        } // library marker kkossev.deviceProfileLib, line 731
        else { // library marker kkossev.deviceProfileLib, line 732
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 733
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 734
        } // library marker kkossev.deviceProfileLib, line 735
    } // library marker kkossev.deviceProfileLib, line 736
    catch (e) { // library marker kkossev.deviceProfileLib, line 737
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 738
        return false // library marker kkossev.deviceProfileLib, line 739
    } // library marker kkossev.deviceProfileLib, line 740
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 741
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 742
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 743
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 744
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 745
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 746
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 747
        } // library marker kkossev.deviceProfileLib, line 748
    } else { // library marker kkossev.deviceProfileLib, line 749
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 750
        return false // library marker kkossev.deviceProfileLib, line 751
    } // library marker kkossev.deviceProfileLib, line 752
    cmds = funcResult // library marker kkossev.deviceProfileLib, line 753
    if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 754
        sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 755
    } // library marker kkossev.deviceProfileLib, line 756
    return true // library marker kkossev.deviceProfileLib, line 757
} // library marker kkossev.deviceProfileLib, line 758

/** // library marker kkossev.deviceProfileLib, line 760
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 761
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 762
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 763
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 764
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 765
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 766
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 767
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 768
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 769
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 770
 */ // library marker kkossev.deviceProfileLib, line 771
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 772
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 773
    Map input = [:] // library marker kkossev.deviceProfileLib, line 774
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 775
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 776
        if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 777
        return [:] // library marker kkossev.deviceProfileLib, line 778
    } // library marker kkossev.deviceProfileLib, line 779
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 780
    def preference // library marker kkossev.deviceProfileLib, line 781
    try { // library marker kkossev.deviceProfileLib, line 782
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 783
    } // library marker kkossev.deviceProfileLib, line 784
    catch (e) { // library marker kkossev.deviceProfileLib, line 785
        if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 786
        return [:] // library marker kkossev.deviceProfileLib, line 787
    } // library marker kkossev.deviceProfileLib, line 788
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 789
    try { // library marker kkossev.deviceProfileLib, line 790
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 791
            if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } // library marker kkossev.deviceProfileLib, line 792
            return [:] // library marker kkossev.deviceProfileLib, line 793
        } // library marker kkossev.deviceProfileLib, line 794
    } // library marker kkossev.deviceProfileLib, line 795
    catch (e) { // library marker kkossev.deviceProfileLib, line 796
        if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 797
        return [:] // library marker kkossev.deviceProfileLib, line 798
    } // library marker kkossev.deviceProfileLib, line 799

    try { // library marker kkossev.deviceProfileLib, line 801
        isTuyaDP = preference.isNumber() // library marker kkossev.deviceProfileLib, line 802
    } // library marker kkossev.deviceProfileLib, line 803
    catch (e) { // library marker kkossev.deviceProfileLib, line 804
        if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 805
        return [:] // library marker kkossev.deviceProfileLib, line 806
    } // library marker kkossev.deviceProfileLib, line 807

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 809
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 810
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 811
    if (foundMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 812
        if (debug) { log.warn "inputIt: map not found for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 813
        return [:] // library marker kkossev.deviceProfileLib, line 814
    } // library marker kkossev.deviceProfileLib, line 815
    if (foundMap.rw != 'rw') { // library marker kkossev.deviceProfileLib, line 816
        if (debug) { log.warn "inputIt: param '${param}' is read only!" } // library marker kkossev.deviceProfileLib, line 817
        return [:] // library marker kkossev.deviceProfileLib, line 818
    } // library marker kkossev.deviceProfileLib, line 819
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 820
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 821
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 822
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 823
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 824
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 825
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 826
        } // library marker kkossev.deviceProfileLib, line 827
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 828
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 829
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 830
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 831
            } // library marker kkossev.deviceProfileLib, line 832
        } // library marker kkossev.deviceProfileLib, line 833
    } // library marker kkossev.deviceProfileLib, line 834
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 835
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 836
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 837
    }/* // library marker kkossev.deviceProfileLib, line 838
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 839
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 840
    }*/ // library marker kkossev.deviceProfileLib, line 841
    else { // library marker kkossev.deviceProfileLib, line 842
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 843
        return [:] // library marker kkossev.deviceProfileLib, line 844
    } // library marker kkossev.deviceProfileLib, line 845
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 846
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 847
    } // library marker kkossev.deviceProfileLib, line 848
    return input // library marker kkossev.deviceProfileLib, line 849
} // library marker kkossev.deviceProfileLib, line 850

/** // library marker kkossev.deviceProfileLib, line 852
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 853
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 854
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 855
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 856
 */ // library marker kkossev.deviceProfileLib, line 857
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 858
    String deviceName         = UNKNOWN // library marker kkossev.deviceProfileLib, line 859
    String deviceProfile      = UNKNOWN // library marker kkossev.deviceProfileLib, line 860
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 861
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 862
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 863
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 864
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 865
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 866
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 867
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 868
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 869
            } // library marker kkossev.deviceProfileLib, line 870
        } // library marker kkossev.deviceProfileLib, line 871
    } // library marker kkossev.deviceProfileLib, line 872
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 873
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 874
    } // library marker kkossev.deviceProfileLib, line 875
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 876
} // library marker kkossev.deviceProfileLib, line 877

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 879
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 880
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 881
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 882
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 883
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 884
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 885
    } // library marker kkossev.deviceProfileLib, line 886
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 887
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 888
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 889
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 890
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 891
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 892
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 893
    } else { // library marker kkossev.deviceProfileLib, line 894
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 895
    } // library marker kkossev.deviceProfileLib, line 896
} // library marker kkossev.deviceProfileLib, line 897

// TODO! // library marker kkossev.deviceProfileLib, line 899
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 900
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 901
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 902
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 903
    return cmds // library marker kkossev.deviceProfileLib, line 904
} // library marker kkossev.deviceProfileLib, line 905

// TODO ! // library marker kkossev.deviceProfileLib, line 907
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 908
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 909
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 910
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.deviceProfileLib, line 911
    return cmds // library marker kkossev.deviceProfileLib, line 912
} // library marker kkossev.deviceProfileLib, line 913

// TODO // library marker kkossev.deviceProfileLib, line 915
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 916
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 917
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 918
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 919
    return cmds // library marker kkossev.deviceProfileLib, line 920
} // library marker kkossev.deviceProfileLib, line 921

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 923
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 924
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 925
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 926
    } // library marker kkossev.deviceProfileLib, line 927
} // library marker kkossev.deviceProfileLib, line 928

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 930
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 931
} // library marker kkossev.deviceProfileLib, line 932

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 934

// // library marker kkossev.deviceProfileLib, line 936
// called from parse() // library marker kkossev.deviceProfileLib, line 937
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 938
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 939
// // library marker kkossev.deviceProfileLib, line 940
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 941
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 942
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 943
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 944
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 945
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 946
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 947
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 948
} // library marker kkossev.deviceProfileLib, line 949

// // library marker kkossev.deviceProfileLib, line 951
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 952
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 953
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 954
// // library marker kkossev.deviceProfileLib, line 955
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 956
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 957
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 958
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 959
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 960
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 961
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 962
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 963
} // library marker kkossev.deviceProfileLib, line 964

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 966
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 967
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 968
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 969
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 970
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 971
    } // library marker kkossev.deviceProfileLib, line 972
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 973
} // library marker kkossev.deviceProfileLib, line 974

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 976
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 977
    boolean isEqual // library marker kkossev.deviceProfileLib, line 978
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 979
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 980
    } // library marker kkossev.deviceProfileLib, line 981
    else { // library marker kkossev.deviceProfileLib, line 982
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 983
    } // library marker kkossev.deviceProfileLib, line 984
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 985
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 986
} // library marker kkossev.deviceProfileLib, line 987

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 989
    Double convertedValue // library marker kkossev.deviceProfileLib, line 990
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 991
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 992
    } // library marker kkossev.deviceProfileLib, line 993
    else { // library marker kkossev.deviceProfileLib, line 994
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 995
    } // library marker kkossev.deviceProfileLib, line 996
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 997
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 998
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 999
} // library marker kkossev.deviceProfileLib, line 1000

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1002
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1003
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1004
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1005
    def convertedValue // library marker kkossev.deviceProfileLib, line 1006
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1007
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1008
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1009
    } // library marker kkossev.deviceProfileLib, line 1010
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1011
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1012
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1013
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1014
            isEqual = false // library marker kkossev.deviceProfileLib, line 1015
        } // library marker kkossev.deviceProfileLib, line 1016
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1017
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1018
        } // library marker kkossev.deviceProfileLib, line 1019
    } // library marker kkossev.deviceProfileLib, line 1020
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1021
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1022
} // library marker kkossev.deviceProfileLib, line 1023

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1025
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1026
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1027
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1028
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1029
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1030
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1031
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1032
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1033
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1034
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1035
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1036
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1037
            break // library marker kkossev.deviceProfileLib, line 1038
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1039
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1040
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1041
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1042
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1043
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1044
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1045
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1046
            } // library marker kkossev.deviceProfileLib, line 1047
            else { // library marker kkossev.deviceProfileLib, line 1048
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1049
            } // library marker kkossev.deviceProfileLib, line 1050
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1051
            break // library marker kkossev.deviceProfileLib, line 1052
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1053
        case 'number' : // library marker kkossev.deviceProfileLib, line 1054
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1055
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1056
            break // library marker kkossev.deviceProfileLib, line 1057
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1058
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1059
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1060
            break // library marker kkossev.deviceProfileLib, line 1061
        default : // library marker kkossev.deviceProfileLib, line 1062
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1063
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1064
    } // library marker kkossev.deviceProfileLib, line 1065
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1066
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1067
    } // library marker kkossev.deviceProfileLib, line 1068
    // // library marker kkossev.deviceProfileLib, line 1069
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1070
} // library marker kkossev.deviceProfileLib, line 1071

// // library marker kkossev.deviceProfileLib, line 1073
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1074
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1075
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1076
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1077
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1078
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1079
// // library marker kkossev.deviceProfileLib, line 1080
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1081
// // library marker kkossev.deviceProfileLib, line 1082
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1083
// // library marker kkossev.deviceProfileLib, line 1084
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1085
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1086
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1087
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1088
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1089
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1090
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1091
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1092
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1093
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1094
            break // library marker kkossev.deviceProfileLib, line 1095
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1096
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1097
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1098
            String dataType = latestEvent?.dataType  // library marker kkossev.deviceProfileLib, line 1099
            logTrace "latestEvent is dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1100
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1101
            if (dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1102
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1103
            } // library marker kkossev.deviceProfileLib, line 1104
            else { // library marker kkossev.deviceProfileLib, line 1105
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1106
            } // library marker kkossev.deviceProfileLib, line 1107
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1108
            break // library marker kkossev.deviceProfileLib, line 1109
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1110
        case 'number' : // library marker kkossev.deviceProfileLib, line 1111
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1112
            break // library marker kkossev.deviceProfileLib, line 1113
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1114
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1115
            break // library marker kkossev.deviceProfileLib, line 1116
        default : // library marker kkossev.deviceProfileLib, line 1117
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1118
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1119
    } // library marker kkossev.deviceProfileLib, line 1120
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1121
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1122
} // library marker kkossev.deviceProfileLib, line 1123

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1125
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1126
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1127
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1128
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1129
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1130
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1131
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1132
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1133
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1134
    } // library marker kkossev.deviceProfileLib, line 1135
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1136
    try { // library marker kkossev.deviceProfileLib, line 1137
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1138
    } // library marker kkossev.deviceProfileLib, line 1139
    catch (e) { // library marker kkossev.deviceProfileLib, line 1140
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1141
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1142
    } // library marker kkossev.deviceProfileLib, line 1143
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1144
    return fncmd // library marker kkossev.deviceProfileLib, line 1145
} // library marker kkossev.deviceProfileLib, line 1146

/** // library marker kkossev.deviceProfileLib, line 1148
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1149
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1150
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1151
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1152
 * // library marker kkossev.deviceProfileLib, line 1153
 * @param descMap The description map of the received DP. // library marker kkossev.deviceProfileLib, line 1154
 * @param dp The value of the received DP. // library marker kkossev.deviceProfileLib, line 1155
 * @param dp_id The ID of the received DP. // library marker kkossev.deviceProfileLib, line 1156
 * @param fncmd The command of the received DP. // library marker kkossev.deviceProfileLib, line 1157
 * @param dp_len The length of the received DP. // library marker kkossev.deviceProfileLib, line 1158
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1159
 */ // library marker kkossev.deviceProfileLib, line 1160
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1161
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1162
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1163
    //if (!(DEVICE?.device?.type == "radar"))      { return false }   // enabled for all devices - 10/22/2023 !!!    // only these models are handled here for now ... // library marker kkossev.deviceProfileLib, line 1164
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1165

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1167
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1168

    Map foundItem = null // library marker kkossev.deviceProfileLib, line 1170
    tuyaDPsMap.each { item -> // library marker kkossev.deviceProfileLib, line 1171
        if (item['dp'] == (dp as int)) { // library marker kkossev.deviceProfileLib, line 1172
            foundItem = item // library marker kkossev.deviceProfileLib, line 1173
            return // library marker kkossev.deviceProfileLib, line 1174
        } // library marker kkossev.deviceProfileLib, line 1175
    } // library marker kkossev.deviceProfileLib, line 1176
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1177
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1178
        updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len) // library marker kkossev.deviceProfileLib, line 1179
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1180
        return false // library marker kkossev.deviceProfileLib, line 1181
    } // library marker kkossev.deviceProfileLib, line 1182

    return processFoundItem(foundItem, fncmd_orig, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1184
} // library marker kkossev.deviceProfileLib, line 1185

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1187
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1188
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1189
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1190

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1192
    if (attribMap == null || attribMap.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1193

    Map foundItem = null // library marker kkossev.deviceProfileLib, line 1195
    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1196
    int value // library marker kkossev.deviceProfileLib, line 1197
    try { // library marker kkossev.deviceProfileLib, line 1198
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1199
    } // library marker kkossev.deviceProfileLib, line 1200
    catch (e) { // library marker kkossev.deviceProfileLib, line 1201
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1202
        return false // library marker kkossev.deviceProfileLib, line 1203
    } // library marker kkossev.deviceProfileLib, line 1204
    //logTrace "clusterAttribute = ${clusterAttribute}" // library marker kkossev.deviceProfileLib, line 1205
    attribMap.each { item -> // library marker kkossev.deviceProfileLib, line 1206
        if (item['at'] == clusterAttribute) { // library marker kkossev.deviceProfileLib, line 1207
            foundItem = item // library marker kkossev.deviceProfileLib, line 1208
            return // library marker kkossev.deviceProfileLib, line 1209
        } // library marker kkossev.deviceProfileLib, line 1210
    } // library marker kkossev.deviceProfileLib, line 1211
    if (foundItem == null) { // library marker kkossev.deviceProfileLib, line 1212
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1213
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1214
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1215
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1216
        return false // library marker kkossev.deviceProfileLib, line 1217
    } // library marker kkossev.deviceProfileLib, line 1218
    return processFoundItem(foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1219
} // library marker kkossev.deviceProfileLib, line 1220

// modifies the value of the foundItem if needed !!! // library marker kkossev.deviceProfileLib, line 1222
/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.deviceProfileLib, line 1223
boolean processFoundItem(final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1224
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1225
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1226
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1227
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1228
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1229
        if (preProcValue == null) { // library marker kkossev.deviceProfileLib, line 1230
            logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" // library marker kkossev.deviceProfileLib, line 1231
            return true // library marker kkossev.deviceProfileLib, line 1232
        } // library marker kkossev.deviceProfileLib, line 1233
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1234
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1235
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1236
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1237
        } // library marker kkossev.deviceProfileLib, line 1238
    } // library marker kkossev.deviceProfileLib, line 1239
    else { // library marker kkossev.deviceProfileLib, line 1240
        logTrace "processFoundItem: no preProc for ${foundItem.name}" // library marker kkossev.deviceProfileLib, line 1241
    } // library marker kkossev.deviceProfileLib, line 1242

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1244
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1245
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1246
    //existingPrefValue = existingPrefValue?.replace("[", "").replace("]", "")               // preference name as in Hubitat settings (preferences), if already created. // library marker kkossev.deviceProfileLib, line 1247
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1248
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1249
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1250
    //boolean preferenceExists = settings.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1251
    boolean preferenceExists = DEVICE?.preferences?.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1252
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1253
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1254
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1255
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1256
        if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1257
        logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" // library marker kkossev.deviceProfileLib, line 1258
    } // library marker kkossev.deviceProfileLib, line 1259
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1260
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1261
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1262
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1263
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1264
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1265

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1267
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1268
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1269
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1270
            // TODO - scaledValue ????? // library marker kkossev.deviceProfileLib, line 1271
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1272
            if (settings.logEnable) { logInfo "${descText }" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1273
        } // library marker kkossev.deviceProfileLib, line 1274
        // no more processing is needed, as this clusterAttribute is not a preference and not an attribute // library marker kkossev.deviceProfileLib, line 1275
        return true // library marker kkossev.deviceProfileLib, line 1276
    } // library marker kkossev.deviceProfileLib, line 1277

    // first, check if there is a preference defined to be updated // library marker kkossev.deviceProfileLib, line 1279
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1280
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1281
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1282
        //log.trace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1283
        if (isEqual == true && !doNotTrace) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1284
            logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1285
        } // library marker kkossev.deviceProfileLib, line 1286
        else { // library marker kkossev.deviceProfileLib, line 1287
            String scaledPreferenceValue = preferenceValue      //.toString() is not neccessary // library marker kkossev.deviceProfileLib, line 1288
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1289
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1290
            } // library marker kkossev.deviceProfileLib, line 1291
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1292
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1293
            try { // library marker kkossev.deviceProfileLib, line 1294
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1295
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1296
            } // library marker kkossev.deviceProfileLib, line 1297
            catch (e) { // library marker kkossev.deviceProfileLib, line 1298
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1299
            } // library marker kkossev.deviceProfileLib, line 1300
        } // library marker kkossev.deviceProfileLib, line 1301
    } // library marker kkossev.deviceProfileLib, line 1302
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1303
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1304
        unitText = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1305
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1306
    } // library marker kkossev.deviceProfileLib, line 1307

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1309
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1310
        logTrace "attribute '${name}' exists (type ${foundItem.type})" // library marker kkossev.deviceProfileLib, line 1311
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1312
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1313
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1314
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1315
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1316
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1317
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1318
            } // library marker kkossev.deviceProfileLib, line 1319
            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1320
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1321
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1322
            // continue ... // library marker kkossev.deviceProfileLib, line 1323
            } // library marker kkossev.deviceProfileLib, line 1324
            else { // library marker kkossev.deviceProfileLib, line 1325
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1326
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1327
                } // library marker kkossev.deviceProfileLib, line 1328
                else { // library marker kkossev.deviceProfileLib, line 1329
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1330
                } // library marker kkossev.deviceProfileLib, line 1331
            } // library marker kkossev.deviceProfileLib, line 1332
        } // library marker kkossev.deviceProfileLib, line 1333

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an event! // library marker kkossev.deviceProfileLib, line 1335

        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1337
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1338
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1339
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1340
        if (DEVICE_TYPE in ['Thermostat'])  { processDeviceEventThermostat(name, valueScaled, unitText, descText) } // library marker kkossev.deviceProfileLib, line 1341
        else { // library marker kkossev.deviceProfileLib, line 1342
            switch (name) { // library marker kkossev.deviceProfileLib, line 1343
                case 'motion' : // library marker kkossev.deviceProfileLib, line 1344
                    handleMotion(value as boolean)  // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1345
                    break // library marker kkossev.deviceProfileLib, line 1346
                case 'temperature' : // library marker kkossev.deviceProfileLib, line 1347
                    //temperatureEvent(value / getTemperatureDiv()) // library marker kkossev.deviceProfileLib, line 1348
                    handleTemperatureEvent(valueScaled as Float) // library marker kkossev.deviceProfileLib, line 1349
                    break // library marker kkossev.deviceProfileLib, line 1350
                case 'humidity' : // library marker kkossev.deviceProfileLib, line 1351
                    handleHumidityEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1352
                    break // library marker kkossev.deviceProfileLib, line 1353
                case 'illuminance' : // library marker kkossev.deviceProfileLib, line 1354
                case 'illuminance_lux' : // library marker kkossev.deviceProfileLib, line 1355
                    handleIlluminanceEvent(valueCorrected as int) // library marker kkossev.deviceProfileLib, line 1356
                    break // library marker kkossev.deviceProfileLib, line 1357
                case 'pushed' : // library marker kkossev.deviceProfileLib, line 1358
                    logDebug "button event received value=${value} valueScaled=${valueScaled} valueCorrected=${valueCorrected}" // library marker kkossev.deviceProfileLib, line 1359
                    buttonEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1360
                    break // library marker kkossev.deviceProfileLib, line 1361
                default : // library marker kkossev.deviceProfileLib, line 1362
                    sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1363
                    if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1364
                        logTrace "event ${name} sent w/ value ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1365
                        logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1366
                    } // library marker kkossev.deviceProfileLib, line 1367
                    break // library marker kkossev.deviceProfileLib, line 1368
            } // library marker kkossev.deviceProfileLib, line 1369
        //logTrace "attrValue=${attrValue} valueScaled=${valueScaled} equal=${isEqual}" // library marker kkossev.deviceProfileLib, line 1370
        } // library marker kkossev.deviceProfileLib, line 1371
    } // library marker kkossev.deviceProfileLib, line 1372
    // all processing was done here! // library marker kkossev.deviceProfileLib, line 1373
    return true // library marker kkossev.deviceProfileLib, line 1374
} // library marker kkossev.deviceProfileLib, line 1375

public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1377
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1378
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 1379
        logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1380
        return false // library marker kkossev.deviceProfileLib, line 1381
    } // library marker kkossev.deviceProfileLib, line 1382
    int validationFailures = 0 // library marker kkossev.deviceProfileLib, line 1383
    int validationFixes = 0 // library marker kkossev.deviceProfileLib, line 1384
    int total = 0 // library marker kkossev.deviceProfileLib, line 1385
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1386
    def oldSettingValue // library marker kkossev.deviceProfileLib, line 1387
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1388
    def newValue // library marker kkossev.deviceProfileLib, line 1389
    String settingType // library marker kkossev.deviceProfileLib, line 1390
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1391
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1392
        if (foundMap == null || foundMap == [:]) { // library marker kkossev.deviceProfileLib, line 1393
            logDebug "validateAndFixPreferences: map not found for preference ${it.key}"    // 10/21/2023 - sevirity lowered to debug // library marker kkossev.deviceProfileLib, line 1394
            return false // library marker kkossev.deviceProfileLib, line 1395
        } // library marker kkossev.deviceProfileLib, line 1396
        settingType = device.getSettingType(it.key) // library marker kkossev.deviceProfileLib, line 1397
        oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1398
        if (settingType == null) { // library marker kkossev.deviceProfileLib, line 1399
            logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" // library marker kkossev.deviceProfileLib, line 1400
            return false // library marker kkossev.deviceProfileLib, line 1401
        } // library marker kkossev.deviceProfileLib, line 1402
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1403
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1404
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1405
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1406
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1407
            try { // library marker kkossev.deviceProfileLib, line 1408
                device.removeSetting(it.key) // library marker kkossev.deviceProfileLib, line 1409
                logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1410
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1411
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1412
                return false // library marker kkossev.deviceProfileLib, line 1413
            } // library marker kkossev.deviceProfileLib, line 1414
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1415
            try { // library marker kkossev.deviceProfileLib, line 1416
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1417
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1418
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1419
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1420
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1421
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1422
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1423
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1424
                    } // library marker kkossev.deviceProfileLib, line 1425
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1426
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1427
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1428
                    } else { // library marker kkossev.deviceProfileLib, line 1429
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1430
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1431
                    } // library marker kkossev.deviceProfileLib, line 1432
                } // library marker kkossev.deviceProfileLib, line 1433
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1434
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1435
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1436
            } // library marker kkossev.deviceProfileLib, line 1437
            catch (e) { // library marker kkossev.deviceProfileLib, line 1438
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1439
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1440
                try { // library marker kkossev.deviceProfileLib, line 1441
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1442
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1443
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1444
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1445
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1446
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" // library marker kkossev.deviceProfileLib, line 1447
                    return false // library marker kkossev.deviceProfileLib, line 1448
                } // library marker kkossev.deviceProfileLib, line 1449
            } // library marker kkossev.deviceProfileLib, line 1450
        } // library marker kkossev.deviceProfileLib, line 1451
        total ++ // library marker kkossev.deviceProfileLib, line 1452
    } // library marker kkossev.deviceProfileLib, line 1453
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1454
    return true // library marker kkossev.deviceProfileLib, line 1455
} // library marker kkossev.deviceProfileLib, line 1456

public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1458
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1459
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1460
            logInfo "${fingerprint}" // library marker kkossev.deviceProfileLib, line 1461
        } // library marker kkossev.deviceProfileLib, line 1462
    } // library marker kkossev.deviceProfileLib, line 1463
} // library marker kkossev.deviceProfileLib, line 1464

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~

// ~~~~~ start include (168) kkossev.illuminanceLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.illuminanceLib, line 1
library( // library marker kkossev.illuminanceLib, line 2
    base: 'driver', // library marker kkossev.illuminanceLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.illuminanceLib, line 4
    category: 'zigbee', // library marker kkossev.illuminanceLib, line 5
    description: 'Zigbee Illuminance Library', // library marker kkossev.illuminanceLib, line 6
    name: 'illuminanceLib', // library marker kkossev.illuminanceLib, line 7
    namespace: 'kkossev', // library marker kkossev.illuminanceLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/illuminanceLib.groovy', // library marker kkossev.illuminanceLib, line 9
    version: '3.0.1', // library marker kkossev.illuminanceLib, line 10
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
 *                                   TODO: illum threshold not working! // library marker kkossev.illuminanceLib, line 27
 *                                   TODO: check illuminanceInitializeVars() and illuminanceProcessTuyaDP() usage // library marker kkossev.illuminanceLib, line 28
*/ // library marker kkossev.illuminanceLib, line 29

static String illuminanceLibVersion()   { '3.0.1' } // library marker kkossev.illuminanceLib, line 31
static String illuminanceLibStamp() { '2024/04/26 8:06 AM' } // library marker kkossev.illuminanceLib, line 32

metadata { // library marker kkossev.illuminanceLib, line 34
    // no capabilities // library marker kkossev.illuminanceLib, line 35
    // no attributes // library marker kkossev.illuminanceLib, line 36
    // no commands // library marker kkossev.illuminanceLib, line 37
    preferences { // library marker kkossev.illuminanceLib, line 38
        // no prefrences // library marker kkossev.illuminanceLib, line 39
    } // library marker kkossev.illuminanceLib, line 40
} // library marker kkossev.illuminanceLib, line 41

@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 10 // library marker kkossev.illuminanceLib, line 43

void customParseIlluminanceCluster(final Map descMap) { // library marker kkossev.illuminanceLib, line 45
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.illuminanceLib, line 46
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.illuminanceLib, line 47
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.illuminanceLib, line 48
    handleIlluminanceEvent(lux) // library marker kkossev.illuminanceLib, line 49
} // library marker kkossev.illuminanceLib, line 50

void handleIlluminanceEvent(int illuminance, boolean isDigital=false) { // library marker kkossev.illuminanceLib, line 52
    Map eventMap = [:] // library marker kkossev.illuminanceLib, line 53
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.illuminanceLib, line 54
    eventMap.name = 'illuminance' // library marker kkossev.illuminanceLib, line 55
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.illuminanceLib, line 56
    eventMap.value  = illumCorrected // library marker kkossev.illuminanceLib, line 57
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.illuminanceLib, line 58
    eventMap.unit = 'lx' // library marker kkossev.illuminanceLib, line 59
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.illuminanceLib, line 60
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.illuminanceLib, line 61
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.illuminanceLib, line 62
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.illuminanceLib, line 63
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.illuminanceLib, line 64
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.illuminanceLib, line 65
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.illuminanceLib, line 66
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.illuminanceLib, line 67
        return // library marker kkossev.illuminanceLib, line 68
    } // library marker kkossev.illuminanceLib, line 69
    if (timeElapsed >= minTime) { // library marker kkossev.illuminanceLib, line 70
        logInfo "${eventMap.descriptionText}" // library marker kkossev.illuminanceLib, line 71
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.illuminanceLib, line 72
        state.lastRx['illumTime'] = now() // library marker kkossev.illuminanceLib, line 73
        sendEvent(eventMap) // library marker kkossev.illuminanceLib, line 74
    } // library marker kkossev.illuminanceLib, line 75
    else {         // queue the event // library marker kkossev.illuminanceLib, line 76
        eventMap.type = 'delayed' // library marker kkossev.illuminanceLib, line 77
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.illuminanceLib, line 78
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.illuminanceLib, line 79
    } // library marker kkossev.illuminanceLib, line 80
} // library marker kkossev.illuminanceLib, line 81

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.illuminanceLib, line 83
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.illuminanceLib, line 84
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.illuminanceLib, line 85
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.illuminanceLib, line 86
    sendEvent(eventMap) // library marker kkossev.illuminanceLib, line 87
} // library marker kkossev.illuminanceLib, line 88

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.illuminanceLib, line 90

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.illuminanceLib, line 92
void illuminanceProcessTuyaDP(final Map descMap, int dp, int dp_id, int fncmd) { // library marker kkossev.illuminanceLib, line 93
    switch (dp) { // library marker kkossev.illuminanceLib, line 94
        case 0x01 : // on/off // library marker kkossev.illuminanceLib, line 95
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.illuminanceLib, line 96
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.illuminanceLib, line 97
            } // library marker kkossev.illuminanceLib, line 98
            else { // library marker kkossev.illuminanceLib, line 99
                sendSwitchEvent(fncmd) // library marker kkossev.illuminanceLib, line 100
            } // library marker kkossev.illuminanceLib, line 101
            break // library marker kkossev.illuminanceLib, line 102
        case 0x02 : // library marker kkossev.illuminanceLib, line 103
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.illuminanceLib, line 104
                handleIlluminanceEvent(fncmd) // library marker kkossev.illuminanceLib, line 105
            } // library marker kkossev.illuminanceLib, line 106
            else { // library marker kkossev.illuminanceLib, line 107
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.illuminanceLib, line 108
            } // library marker kkossev.illuminanceLib, line 109
            break // library marker kkossev.illuminanceLib, line 110
        case 0x04 : // battery // library marker kkossev.illuminanceLib, line 111
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.illuminanceLib, line 112
            break // library marker kkossev.illuminanceLib, line 113
        default : // library marker kkossev.illuminanceLib, line 114
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.illuminanceLib, line 115
            break // library marker kkossev.illuminanceLib, line 116
    } // library marker kkossev.illuminanceLib, line 117
} // library marker kkossev.illuminanceLib, line 118

void illuminanceInitializeVars( boolean fullInit = false ) { // library marker kkossev.illuminanceLib, line 120
    logDebug "customInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.illuminanceLib, line 121
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 122
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // library marker kkossev.illuminanceLib, line 123
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.illuminanceLib, line 124
    } // library marker kkossev.illuminanceLib, line 125
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 126
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.illuminanceLib, line 127
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.illuminanceLib, line 128
    } // library marker kkossev.illuminanceLib, line 129
} // library marker kkossev.illuminanceLib, line 130

// ~~~~~ end include (168) kkossev.illuminanceLib ~~~~~
