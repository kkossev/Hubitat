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
 * ver. 3.1.0  2024-04-28 kkossev  - commonLib 3.1.0 speed optimization; added TS0601_KAPVNNLK_RADAR, TS0225_HL0SS9OA_RADAR
 * ver. 3.1.1  2024-05-04 kkossev  - enabled all radars; add TS0601 _TZE204_muvkrjr5 @iEnam; added the code for forcedProfile change; added 'switch' for the TuYa SZR07U; Linptech: added ledIndicator; radar attributes types changed to number (was enum)
 * ver. 3.1.2  2024-05-08 kkossev  - added _TZ3218_t9ynfz4x as a new Linptech manufacturer; fixed HL0SS9OA and 2AAELWXK wrong IAS illuminance reprots; existance_time reanmed to occupiedTime
 * ver. 3.1.3  2024-05-11 kkossev  - added TS0601 _TZE204_7gclukjs; fixed debug trace logging;
 * ver. 3.1.4  2024-05-14 kkossev  - added TS0601_24GHZ_PIR_RADAR profile TS0601 _TZE200_2aaelwxk and TS0601 _TZE200_kb5noeto for tests; added TS0601 _TZE204_fwondbzy; 
 * ver. 3.2.0  2024-05-24 kkossev  - (dev.branch) commonLib 2.0 allignment
 *                                   
 *                                   TODO: add the state tuyaDps as in the 4-in-1 driver!
 *                                   TODO: cleanup the 4-in-1 state variables.
 *                                   TODO: enable the OWON radar configuration : ['0x0406':'bind']
 *                                   TODO: add response to ZDO Match Descriptor Request (Sonoff SNZB-06)
 *                                   TODO: illumState default value is 0 - should be 'unknown' ?
 *                                   TODO: Motion reset to inactive after 43648s - convert to H:M:S
 *                                   TODO: Black Square Radar validateAndFixPreferences: map not found for preference indicatorLight
 *                                   TODO: command for black radar LED
 *                                   TODO: TS0225_2AAELWXK_RADAR  dont see an attribute as mentioned that shows the distance at which the motion was detected. - https://community.hubitat.com/t/the-new-tuya-human-presence-sensors-ts0225-tze200-hl0ss9oa-tze200-2aaelwxk-have-actually-5-8ghz-modules-inside/122283/294?u=kkossev
 *                                   TODO: TS0225_2AAELWXK_RADAR led setting not working - https://community.hubitat.com/t/the-new-tuya-human-presence-sensors-ts0225-tze200-hl0ss9oa-tze200-2aaelwxk-have-actually-5-8ghz-modules-inside/122283/294?u=kkossev
 *                                   TODO: radars - ignore the change of the presence/motion being turned off when changing parameters for a period of 10 seconds ?
 *                                   TODO: TS0225_HL0SS9OA_RADAR - add presets
 *                                   TODO: humanMotionState - add preference: enum "disabled", "enabled", "enabled w/ timing" ...; add delayed event
*/

static String version() { "3.2.0" }
static String timeStamp() {"2024/05/24 6:07 PM"}

@Field static final Boolean _DEBUG = false
@Field static final Boolean _TRACE_ALL = false      // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = false 


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
        attribute 'occupiedTime', 'number'          // BlackSquareRadar & LINPTECH // was existance_time
        attribute 'absenceTime', 'number'            // BlackSquareRadar only
        attribute 'keepTime', 'enum', ['10 seconds', '30 seconds', '60 seconds', '120 seconds']
        attribute 'motionDetectionSensitivity', 'number'
        attribute 'motionDetectionDistance', 'decimal'  // changed 05/11/2024 - was 'number'

        attribute 'radarSensitivity', 'number'
        attribute 'staticDetectionSensitivity', 'number'    // added 10/29/2023
        attribute 'staticDetectionDistance', 'decimal'      // added 05/1/2024
        attribute 'smallMotionDetectionSensitivity', 'number'   // added 04/25/2024
        attribute 'detectionDelay', 'decimal'
        attribute 'fadingTime', 'decimal'
        attribute 'minimumDistance', 'decimal'
        attribute 'maximumDistance', 'decimal'
        attribute 'radarStatus', 'enum', ['checking', 'check_success', 'check_failure', 'others', 'comm_fault', 'radar_fault']
        attribute 'humanMotionState', 'enum', ['none', 'moving', 'small_move', 'stationary', 'static', 'presence', 'peaceful', 'large_move']
        attribute 'radarAlarmMode', 'enum',   ['0 - arm', '1 - off', '2 - alarm', '3 - doorbell']
        attribute 'radarAlarmVolume', 'enum', ['0 - low', '1 - medium', '2 - high', '3 - mute']
        attribute 'illumState', 'enum', ['dark', 'light', 'unknown']
        attribute 'ledIndicator', 'number'
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
        input(name: 'info',    type: 'hidden', title: "<a href='https://github.com/kkossev/Hubitat/wiki/Tuya-Zigbee-mmWave-Sensor' target='_blank'><i>For more info, click on this link to visit the WiKi page</i></a>")
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
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false, isSpammy:true, ignoreIAS:true], // sends all DPs periodically!
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
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_ztqnh5cg', deviceJoinName: 'Tuya Human Presence Detector ZY-M100'],             // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/1054?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_fwondbzy', deviceJoinName: 'Moes Smart Human Presence Detector']                // https://www.aliexpress.us/item/3256803962192457.html https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/1054?u=kkossev

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
            deviceJoinName: 'Tuya Human Presence Detector ZY-M100'
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
                [dp:101, name:'battery',             type:'number',  rw: 'ro', min:0,   max:100,   defVal:100,  scale:1,   unit:'%',          title:'<b>Battery level</b>',              description:'<i>Battery level</i>']
            ],
            spammyDPsToIgnore : [19], spammyDPsToNotTrace : [19],
            deviceJoinName: 'Tuya 24 GHz Human Presence Detector NEW'
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
            deviceJoinName: 'Tuya Human Presence Sensor MIR-HE200-TY'
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
                [dp:101, name:'occupiedTime', type:'number', rw: 'ro', min:0, max:9999, scale:1,   unit:'minutes',    description:'Shows the presence duration in minutes'],
                [dp:102, name:'absenceTime',     type:'number', rw: 'ro', min:0, max:9999, scale:1,   unit:'minutes',    description:'Shows the duration of the absence in minutes'],
                [dp:103, name:'indicatorLight', type:'enum',   rw: 'rw', min:0, max:1,    defVal: '0', map:[0:'OFF', 1:'ON'],  title:'<b>Indicator Light</b>', description:'<i>Turns the onboard LED on or off</i>']
            ],
            spammyDPsToIgnore : [103, 102, 101],            // we don't need to know the LED status every 4 seconds! Skip also all other spammy DPs except motion
            spammyDPsToNotTrace : [1, 101, 102, 103],     // very spammy device - 4 packates are sent every 4 seconds!
            deviceJoinName: '24GHz Black Square Human Presence Radar w/ LED'
    ],
    
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
            spammyDPsToIgnore : [105], spammyDPsToNotTrace : [105],
            deviceJoinName: 'Tuya Human Presence Detector YXZBRB58'    // https://www.aliexpress.com/item/1005005764168560.html
    ],
    
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
            spammyDPsToIgnore : [109], spammyDPsToNotTrace : [109],
            deviceJoinName: 'Tuya Human Presence Detector SXM7L9XA'
    ],
    
    
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
            deviceJoinName: 'Tuya Human Presence Detector ZY-M100-24G'
    ],
    
/*
SmartLife   radarSensitivity staticDetectionSensitivity
    L1          7                   9
    L2          6                   7
    L3          4                   6
    L4          2                   4
    L5          2                   3
*/
    
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
            spammyDPsToIgnore : [19], spammyDPsToNotTrace : [19],
            deviceJoinName: 'Tuya Human Presence Detector YENSYA2C'
    ],
    
    
    // the new 5.8 GHz radar w/ humanMotionState and a lot of configuration options, 'not-so-spammy' !   - pedestal mount form-factor
    'TS0225_HL0SS9OA_RADAR'   : [
            description   : 'Tuya TS0225_HL0SS9OA Radar',        // https://www.aliexpress.com/item/1005005761971083.html
            models        : ['TS0225'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false, ignoreIAS: true],    // ignore the illuminance reports from the IAS cluster
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
            tuyaDPs:        [        // W.I.P - use already defined DPs and preferences !!  TODO - verify the default values !
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
                [dp:116, name:'occupiedTime',                  type:'number',  rw: 'ro', min:0,    max:60 ,   scale:1,   unit:'seconds',   description:'Radar presence duration'],    // not received
                [dp:117, name:'absenceTime',                      type:'number',  rw: 'ro', min:0,    max:60 ,   scale:1,   unit:'seconds',   description:'Radar absence duration'],     // not received
                [dp:118, name:'radarDurationStatus',             type:'number',  rw: 'ro', min:0,    max:60 ,   scale:1,   unit:'seconds',   description:'Radar duration status']       // not received
            ],
            spammyDPsToIgnore : [],
            spammyDPsToNotTrace : [11],
            deviceJoinName: 'Tuya TS0225_HL0SS9OA Human Presence Detector'
    ],
    
    
    // the new 5.8GHz radar w/ humanMotionState and a lot of configuration options, 'not-so-spammy' !   - wall mount form-factor    is2AAELWXKradar()
    'TS0225_2AAELWXK_RADAR'   : [                                     // https://github.com/Koenkk/zigbee2mqtt/issues/18612
            description   : 'Tuya TS0225_2AAELWXK 5.8 GHz Radar',        // https://community.hubitat.com/t/the-new-tuya-24ghz-human-presence-sensor-ts0225-tze200-hl0ss9oa-finally-a-good-one/122283/72?u=kkossev
            models        : ['TS0225'],                                // ZG-205Z   https://github.com/Koenkk/zigbee-herdsman-converters/blob/38bf79304292c380dc8366966aaefb71ca0b03da/src/devices/tuya.ts#L4793
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false, ignoreIAS: true],    // ignore the illuminance reports from the IAS cluster
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true],
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
                //[dp:116, name:'occupiedTime',                  type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar presence duration'],    // not received
                //[dp:117, name:'absenceTime',                      type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar absence duration'],     // not received
                //[dp:118, name:'radarDurationStatus',             type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar duration status']       // not received
            ],
            deviceJoinName: 'Tuya TS0225_2AAELWXK 5.8 Ghz Human Presence Detector'
    ],
    
    'TS0601_24GHZ_PIR_RADAR'   : [  //https://github.com/Koenkk/zigbee-herdsman-converters/blob/3a8832a8a3586356e7ba76bcd92ce3177f6b934e/src/devices/tuya.ts#L5730-L5762
            description   : 'Tuya TS0601_2AAELWXK 24 GHz + PIR Radar',
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false, ignoreIAS: true],    // ignore the illuminance reports from the IAS cluster
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true],
            preferences   : ['staticDetectionSensitivity':'2',  'staticDetectionDistance':'4', 'fadingTime':'102', 'ledIndicator':'107'],
            commands      : ['resetSettings':'resetSettings', 'moveSelfTest':'moveSelfTest', 'smallMoveSelfTest':'smallMoveSelfTest', 'breatheSelfTest':'breatheSelfTest',  \
                             'resetStats':'resetStats', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences' \
            ],
            fingerprints  : [                                          // reports illuminance and motion using clusters 0x400 and 0x500 !
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,0001,0400', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_2aaelwxk', deviceJoinName: 'Tuya 2AAELWXK 24 GHz + PIR Radar'], 
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,0001,0400', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_kb5noeto', deviceJoinName: 'Tuya KB5NOETO 24 GHz + PIR Radar']        // https://community.hubitat.com/t/beta-tuya-zigbee-mmwave-sensors-code-moved-from-the-tuya-4-in-1-driver/137410/41?u=kkossev 
                // https://www.aliexpress.us/item/3256806664768243.html
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',                          type:'enum',    rw: 'ro', min:0,    max:1,     defVal:'0',   scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:2,   name:'staticDetectionSensitivity',      type:'number',  rw: 'rw', min:0,    max:10,    defVal:7,     scale:1,   unit:'',         title:'<b>Static Detection Sensitivity</b>',       description:'<i>Static detection sensitivity</i>'],                 //  dt: "UINT8", aka Motionless Detection Sensitivity
                [dp:4,   name:'staticDetectionDistance',         type:'decimal', rw: 'rw', min:0.0,  max:10.0,  defVal:5.0,   scale:100, unit:'meters',    title:'<b>Static detection distance</b>',          description:'<i>Static detection distance</i>'],
                [dp:106, name:'illuminance',                     type:'number',  rw: 'ro', scale:10,  unit:'lx',        description:'Illuminance'],
                [dp:101, name:'humanMotionState',                type:'enum',    rw: 'ro', min:0,    max:3,     defVal:'0',  map:[0:'none', 1:'moving', 2:'small', 3:'static'],       description:'Human motion state'],
                [dp:102, name:'fadingTime',                      type:'number',  rw: 'rw', min:0,    max:28800, defVal:30,    scale:1,   unit:'seconds',   title:'<b>Presence keep time</b>',                 description:'<i>Presence keep time</i>'],
                [dp:107, name:'ledIndicator',                    type:'enum',    rw: 'rw', min:0,    max:1,     defVal:'0',  map:[0:'0 - OFF', 1:'1 - ON'],               title:'<b>LED indicator</b>',              description:'<i>LED indicator mode</i>'],
                [dp:121, name:'battery',                         type:'number',  rw: 'ro', min:0,    max:100,   defVal:100,  scale:1,   unit:'%',          title:'<b>Battery level</b>',              description:'<i>Battery level</i>']
            ],
            deviceJoinName: 'Tuya TS0601 24 GHz + PIR Radar'
    ],
    
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
            spammyDPsToIgnore : [9], spammyDPsToNotTrace : [9],
            deviceJoinName: 'Tuya Human Presence Detector SBYX0LM6'
    ],

    // 
    'TS0601_7GCLUKJS_RADAR'   : [           // https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/blob/main/ZM10224gNEW2.2.js       // https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/blob/main/zmzn24g.NEW.js
            description   : 'Tuya Human Presence Detector 7GCLUKJS',
            models        : ['TS0601'],    // https://github.com/sprut/Hub/issues/3062 (default values)
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true, 'HumanMotionState':true],
            preferences   : ['radarSensitivity':'2', 'staticDetectionSensitivity':'102', 'fadingTime':'105', 'minimumDistance':'3', 'maximumDistance':'4'],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_7gclukjs', deviceJoinName: 'Tuya Human Presence Detector ZY-M100 24G']
            ],
            tuyaDPs:        [   
                [dp:1,   name:'humanMotionState',   type:'enum',    rw: 'ro', min:0,    max:2,       defVal:'0',  map:[0:'none', 1:'present', 2:'moving'], description:'Presence state'],
                [dp:2,   name:'radarSensitivity',   type:'number',  rw: 'rw', min:1,    max:10,      defVal:2 ,   scale:1,   unit:'',           title:'<b>Motion sensitivity</b>',          description:'<i>Radar motion sensitivity</i>'],
                [dp:3,   name:'minimumDistance',    type:'decimal', rw: 'rw', min:0.0,  max:8.25,    defVal:0.75, step:75, scale:100,  unit:'meters',   title:'<b>Minimum distance</b>',      description:'<i>Shield range of the radar</i>'],         // was shieldRange
                [dp:4,   name:'maximumDistance',    type:'decimal', rw: 'rw', min:0.75, max:8.25,    defVal:4.5,  step:75, scale:100,  unit:'meters',   title:'<b>Maximum distance</b>',      description:'<i>Detection range of the radar</i>'],      // was detectionRange
                [dp:9,   name:'distance',           type:'decimal', rw: 'ro', min:0.0,  max:10.0,    defVal:0.0,  scale:100, unit:'meters',             description:'Target distance'],
                [dp:102, name:'staticDetectionSensitivity', type:'number',  rw: 'rw', min:0, max:10, defVal:3,    scale:1,   unit:'',      title:'<b>Static detection sensitivity</b>', description:'<i>Presence sensitivity</i>'],
                [dp:103, name:'illuminance',        type:'number',  rw: 'ro',                                     scale:1, unit:'lx',                  description:'illuminance'],
                [dp:104, name:'motion',             type:'enum',    rw: 'ro', min:0,    max:1,       defVal:'0',  scale:1,    map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:105, name:'fadingTime',         type:'decimal', rw: 'rw', min:0,    max:600,     defVal:5,   scale:1,   unit:'seconds',   title:'<b<Delay time</b>',         description:'<i>Delay (fading) time</i>']
            ],
            spammyDPsToIgnore : [9],
            spammyDPsToNotTrace : [9],
            deviceJoinName: 'Tuya Human Presence Detector ZY-M100-24G'
    ],
        
    
    // isLINPTECHradar()
    'TS0225_LINPTECH_RADAR'   : [                                      // https://github.com/Koenkk/zigbee2mqtt/issues/18637
            description   : 'Tuya TS0225_LINPTECH 24GHz Radar',        // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/646?u=kkossev
            models        : ['TS0225'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['fadingTime':'101', 'motionDetectionDistance':'0xE002:0xE00B', 'motionDetectionSensitivity':'0xE002:0xE004', 'staticDetectionSensitivity':'0xE002:0xE005', 'ledIndicator':'0xE002:0xE009'],
            fingerprints  : [                                          // https://www.amazon.com/dp/B0C7C6L66J?ref=ppx_yo2ov_dt_b_product_details&th=1
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,E002,4000,EF00,0500', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZ3218_awarhusb', deviceJoinName: 'Tuya TS0225_LINPTECH 24Ghz Human Presence Detector'],       // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,E002,4000,EF00,0500', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZ3218_t9ynfz4x', deviceJoinName: 'Tuya TS0225_LINPTECH 24Ghz Human Presence Detector']
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs:       [                                           // the tuyaDPs revealed from iot.tuya.com are actually not used by the device! The only exception is dp:101
                [dp:101,             name:'fadingTime',                 type:'number',             rw: 'rw', min:1,    max:9999,   defVal:10,  scale:1,   unit:'seconds', title: '<b>Fading time</b>', description:'<i>Presence inactivity timer, seconds</i>']                                  // aka 'nobody time'
            ],
            attributes:       [                                        // LINPTECH / MOES are using a custom cluster 0xE002 for the settings (except for the fadingTime), ZCL cluster 0x0400 for illuminance (malformed reports!) and the IAS cluster 0x0500 for motion detection
                [at:'0xE002:0xE001', name:'occupiedTime',               type:'number', dt: '0x21', rw: 'ro', min:0,    max:65535,  scale:1,    unit:'minutes', title: '<b>Existance time/b>', description:'<i>existance (presence) time, recommended value is > 10 seconds!</i>'],                    // aka Presence Time
                [at:'0xE002:0xE004', name:'motionDetectionSensitivity', type:'enum',   dt: '0x20', rw: 'rw', min:1,    max:5,      defVal:'4', scale:1,   map:[1: '1 - low', 2: '2 - medium low', 3: '3 - medium', 4: '4 - medium high', 5: '5 - high'], unit:'', title: '<b>Motion Detection Sensitivity</b>',  description:'<i>Large motion detection sensitivity</i>'],
                [at:'0xE002:0xE005', name:'staticDetectionSensitivity', type:'enum',   dt: '0x20', rw: 'rw', min:1,    max:5,      defVal:'3', scale:1,   map:[1: '1 - low', 2: '2 - medium low', 3: '3 - medium', 4: '4 - medium high', 5: '5 - high'], unit:'', title: '<b>Static Detection Sensitivity</b>',  description:'<i>Static detection sensitivity</i>'],                 // aka Motionless Detection Sensitivity
                [at:'0xE002:0xE009', name:'ledIndicator',               type:'enum',   dt: '0x10', rw: 'rw', min:0,    max:1,      defVal:'0', map:[0:'0 - OFF', 1:'1 - ON'],       title:'<b>LED indicator mode</b>', description:'<i>LED indicator mode<br>Requires firmware version 1.0.6 (application:46)!</i>'],
                [at:'0xE002:0xE00A', name:'distance',  preProc:'skipIfDisabled', type:'decimal', dt: '0x21', rw: 'ro', min:0.0,    max:6.0,    defVal:0.0, scale:100, unit:'meters', title: '<b>Distance</b>', description:'<i>Measured distance</i>'],                            // aka Current Distance
                [at:'0xE002:0xE00B', name:'motionDetectionDistance',    type:'enum',   dt: '0x21', rw: 'rw', min:0.75, max:6.00,   defVal:'450', step:75, scale:100, map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters', '300': '3.00 meters', '375': '3.75 meters', '450': '4.50 meters', '525': '5.25 meters', '600' : '6.00 meters'], unit:'meters', title: '<b>Motion Detection Distance</b>', description:'<i>Large motion detection distance, meters</i>']               // aka Far Detection
            ],
            // returns zeroes !!!refresh: ['motion', 'occupiedTime', 'motionDetectionSensitivity', 'staticDetectionSensitivity', 'ledIndicator', 'motionDetectionDistance'],
            deviceJoinName: 'Tuya TS0225_LINPTECH 24Ghz Human Presence Detector',
            configuration : [:]
    ],
    
    
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
            spammyDPsToIgnore : [103], spammyDPsToNotTrace : [103],
            deviceJoinName: 'Tuya TS0225_AWARHUSB 24Ghz Human Presence Detector',
            configuration : ['battery': false]
    ],
    
    
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
            spammyDPsToIgnore : [182], spammyDPsToNotTrace : [182],
            deviceJoinName: 'Aubess Human Presence Detector O7OE4N9A'
    ],
    
    
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
    ],

    'TS0601_MUVJRJR5_RADAR'   : [                                       // Zigbee side mounted human presence sensor 24Ghz      // ZN494622_01  // no illuminance
            description   : 'Tuya Human Presence Detector MUVJRJR5',    // https://s.click.aliexpress.com/e/_DDkMp7Z 
            models        : ['TS0601'],                                 
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': false, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'16', 'fadingTime':'103', 'maximumDistance':'13', 'ledIndicator':'101', 'powerSwitch':'102'],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_muvkrjr5', deviceJoinName: 'Tuya Human Presence Detector MUVJRJR5'],       //
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', min:0,   max:1,    defVal:'0', scale:1,   map:[0:'inactive', 1:'active'] , unit:'', title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:13,  name:'maximumDistance',    type:'decimal', rw: 'rw', min:1.5, max:6.0,  defVal:5.0, scale:100, unit:'meters',  title:'<b>Maximum distance</b>', description:'<i>Breath detection maximum distance</i>'],
                [dp:16,  name:'radarSensitivity',   type:'number',  rw: 'rw', min:68,  max:90,   defVal:80,  scale:1,   unit:'',  title:'<b>Radar sensitivity</b>', description:'<i>Radar sensitivity</i>'],
                [dp:19,  name:'distance',           type:'decimal', rw: 'ro', min:0.0, max:10.0, defVal:0.0, scale:100, unit:'meters',  description:'Distance'],
                [dp:101, name:'ledIndicator',       type:'enum',    rw: 'rw', min:0,   max:1,    defVal:'0', map:[0:'0 - OFF', 1:'1 - ON'], title:'<b>LED indicator mode</b>', description:'<i>LED indicator mode</i>'],
                [dp:102, name:'powerSwitch',        type:'enum',    rw: 'rw', min:0,   max:1,    defVal:'1', map:[0:'OFF', 1:'ON'], title:'<b>powerSwitch</b>', description:'<i>Switch</i>'],
                [dp:103, name:'fadingTime',         type:'number',  rw: 'rw', min:3,   max:1799, defVal:30,  scale:1,   unit:'seconds',  title:'<b>Fading time</b>', description:'<i>Presence (fading) delay time</i>']
            ],
            spammyDPsToIgnore : [19], spammyDPsToNotTrace : [19],
            deviceJoinName: 'Tuya Human Presence Detector MUVJRJR5'
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
            state.motionStarted = unix2formattedDate(now())
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
    Long unixTime = 0
    try { unixTime = formattedDate2unix(state.motionStarted) } catch (Exception e) { logWarn "getSecondsInactive: ${e}" }
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

    if (settings?.forcedProfile != null) {
        logDebug "current state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${getProfileKey(settings?.forcedProfile)}"
        if (getProfileKey(settings?.forcedProfile) != state.deviceProfile) {
            logInfo "changing the device profile from ${state.deviceProfile} to ${getProfileKey(settings?.forcedProfile)}"
            state.deviceProfile = getProfileKey(settings?.forcedProfile)
            initializeVars(fullInit = false)
            resetPreferencesToDefaults(debug = true)
            logInfo 'press F5 to refresh the page'
        }
    }
    /* groovylint-disable-next-line EmptyElseBlock */
    else {
        logDebug "forcedProfile is not set"
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
    if (fullInit == true || state.motionStarted == null) { state.motionStarted = unix2formattedDate(now()) }

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
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '11') {}
        if (getDeviceProfile() == 'TS0601_BLACK_SQUARE_RADAR') {
        updateInidicatorLight()
    }
    else {
        logDebug "customParseTuyaCluster: received Tuya cluster <b>command ${descMap.command}</b>"
    }
}

void customParseIlluminanceCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    if (DEVICE?.device?.ignoreIAS == true) { 
        logDebug "customCustomParseIlluminanceCluster: ignoring IAS reporting device"
        return 
    }    // ignore IAS devices
    standardParseIlluminanceCluster(descMap)  // illuminance.lib
}

void formatAttrib() {
    logDebug "trapped formatAttrib() from the 4-in-1 driver..."
}

// ------------------------- sbruke781 tooltips methods -------------------------

String getZindexToggle(String setting, int low = 10, int high = 50) {
    return "<style> div:has(label[for^='settings[${setting}]']) { z-index: ${low}; } div:has(label):has(div):has(span):hover { z-index: ${high}; } </style>";
}

String getTooltipHTML(String heading, String tooltipText, String hrefURL, String hrefLabel='View Documentation'){
    return "<span class='help-tip'> <p> <span class='help-tip-header'>${heading}</span> <br/>${tooltipText}<br/> <a href='${hrefURL}' target='_blank'>${hrefLabel}</a> </p> </span>";
}

def pageDeviceConfiguration(params) {
    String tooltipStyle = "<style> /* The icon */ .help-tip{     /* HE styling overrides */ 	box-sizing: content-box; 	white-space: collapse; 	 	display: inline-block; 	margin: auto; 	vertical-align: text-top; 	text-align: center; 	border: 2px solid white; 	border-radius: 50%; 	width: 16px; 	height: 16px; 	font-size: 12px; 	 	cursor: default; 	color: white; 	background-color: #2f4a9c; } /* Add the icon text, e.g. question mark */ .help-tip:before{     white-space: collapse; 	content:'?';     font-family: sans-serif;     font-weight: normal;     color: white; 	z-index: 10; } /* When hovering over the icon, display the tooltip */ .help-tip:hover p{     display:block;     transform-origin: 100% 0%;     -webkit-animation: fadeIn 0.5s ease;     animation: fadeIn 0.5s ease; } /* The tooltip */ .help-tip p {     /* HE styling overrides */ 	box-sizing: content-box; 	 	/* initially hidden */ 	display: none; 	 	position: relative; 	float: right; 	width: 178px; 	height: auto; 	left: 50%; 	transform: translate(204px, -90px); 	border-radius: 3px; 	box-shadow: 0 0px 20px 0 rgba(0,0,0,0.1);	 	background-color: #FFFFFF; 	padding: 12px 16px; 	z-index: 999; 	 	color: #37393D; 	 	text-align: center; 	line-height: 18px; 	font-family: sans-serif; 	font-size: 12px; 	text-rendering: optimizeLegibility; 	-webkit-font-smoothing: antialiased; 	 } .help-tip p a { 	color: #067df7; 	text-decoration: none; 	z-index: 100; } .help-tip p a:hover { 	text-decoration: underline; } .help-tip-header {     font-weight: bold; 	color: #6482de; } /* CSS animation */ @-webkit-keyframes fadeIn {     0% { opacity:0; }     100% { opacity:100%; } } @keyframes fadeIn {     0% { opacity:0; }     100% { opacity:100%; } } </style>";
    dynamicPage (name: "pageDeviceConfiguration", title: "Mobile Device Configuration", nextPage: "pageApplyConfiguration", install: false, uninstall: false) {
        section("") {
            paragraph "Select the permissions you want to grant for Mobile Controller on your mobile device: ${tooltipStyle}"
            input ("btMonitor", "bool", title: "Monitor Bluetooth Connections? ${getZindexToggle('btMonitor')} ${getTooltipHTML('Bluetooth Monitoring', 'Allow Mobile Controller to detect devices paired to the mobile device.  Child devices will be created in HE to capture the connection status for each bluetooth device.', 'https://github.com/sburke781/MobileController/blob/master/Settings.md#bluetooth-monitoring')}",     required: true, submitOnChange: true, defaultValue: false)
            input ("wifiMonitor", "bool", title: "Monitor Wi-Fi Connections? ${getZindexToggle('wifiMonitor')} ${getTooltipHTML('Wi-Fi Monitoring','Allow Mobile Controller to detect connections to a specified list of Wi-Fi networks, allowing easy switching between local and cloud communications and contributing to presence detection.', 'https://github.com/sburke781/MobileController/blob/master/Settings.md#wi-fi-monitoring')}",     required: true, submitOnChange: true, defaultValue: false)
            input ("callMonitor", "bool", title: "Monitor Calls? ${getZindexToggle('callMonitor')} ${getTooltipHTML('Call Monitoring', 'Allow Mobile Controller to detect incoming, ongoing and missed calls, reporting the call status to HE.', 'https://github.com/sburke781/MobileController/blob/master/Settings.md#call-monitoring')}",     required: true, submitOnChange: true, defaultValue: false)
            input ("msgMonitor", "bool", title: "Monitor Messages? ${getZindexToggle('msgMonitor')} ${getTooltipHTML('Message Monitoring', 'Allow Mobile Controller to detect new or unread SMS/MMS messages on the mobile device, reporting the status back to HE.', 'https://github.com/sburke781/MobileController/blob/master/Settings.md#message-monitoring')}",     required: true, submitOnChange: true, defaultValue: false)
            input ("syncModes", "bool", title: "Synchronize HE Modes? ${getZindexToggle('syncModes')} ${getTooltipHTML('Synchronizing HE Modes', 'Changes to the HE mode will be communicated to and stored on the mobile device, allowing use of the mode in custom automations on the mobile device.', 'https://github.com/sburke781/MobileController/blob/master/Settings.md#synchronizing-he-modes')}",     required: true, submitOnChange: true, defaultValue: false)
            input ("controlModes", "bool", title: "Allow Control of HE Mode From The Device? ${getZindexToggle('controlModes')} ${getTooltipHTML('Control HE Modes', 'Allows changes to the HE mode to be initiated on the mobile device through elements such as a home-screen widget.', 'https://github.com/sburke781/MobileController/blob/master/Settings.md#control-he-modes')}",     required: true, submitOnChange: true, defaultValue: false)
            input ("cloudComms", "bool", title: "Allow Cloud Communication? ${getZindexToggle('cloudComms')} ${getTooltipHTML('Cloud Communication', 'Allows the mobile controller to send status updates and other commands from the mobile device when not connected to the HE hub over a local Wi-Fi or VPN connection.', 'https://github.com/sburke781/MobileController/blob/master/Settings.md#cloud-communication')}",     required: true, submitOnChange: true, defaultValue: false)
            input ("useVPN", "bool", title: "Communicate using VPN Connection When Available? ${getZindexToggle('useVPN')} ${getTooltipHTML('VPN Connection', 'If a VPN connection is available (connected) on the mobile device, this will be used when communicating from HE to the mobile device.', 'https://github.com/sburke781/MobileController/blob/master/Settings.md#vpn-connection')}",     required: true, submitOnChange: true, defaultValue: false)
            input ("vpnIP", "string", title: "Mobile Device VPN IP Address",     required: false, submitOnChange: true, defaultValue: '')
            paragraph "Click Next to apply the configuration settings"
        }
    }
}

// ------------------------- end of Simon's tooltips methods -------------------------

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
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', documentationLink: '', // library marker kkossev.commonLib, line 4
    version: '3.2.0' // library marker kkossev.commonLib, line 5
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
  * ver. 3.2.0  2024-05-23 kkossev  - (dev.branch) W.I.P - standardParse____Cluster and customParse___Cluster methods; // library marker kkossev.commonLib, line 36
  * // library marker kkossev.commonLib, line 37
  *                                   TODO: move onOff methods to a new library // library marker kkossev.commonLib, line 38
  *                                   TODO: rename all custom handlers in the libs to statdndardParseXXX !! W.I.P. // library marker kkossev.commonLib, line 39
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 40
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 41
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib // library marker kkossev.commonLib, line 42
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 43
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 44
  * // library marker kkossev.commonLib, line 45
*/ // library marker kkossev.commonLib, line 46

String commonLibVersion() { '3.2.0' } // library marker kkossev.commonLib, line 48
String commonLibStamp() { '2024/05/23 11:00 PM' } // library marker kkossev.commonLib, line 49

import groovy.transform.Field // library marker kkossev.commonLib, line 51
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 52
import hubitat.device.Protocol // library marker kkossev.commonLib, line 53
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 54
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 55
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 56
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 57
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 58
import java.math.BigDecimal // library marker kkossev.commonLib, line 59

metadata { // library marker kkossev.commonLib, line 61
        if (_DEBUG) { // library marker kkossev.commonLib, line 62
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 63
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 64
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 65
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 66
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 67
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 68
            ] // library marker kkossev.commonLib, line 69
        } // library marker kkossev.commonLib, line 70

        // common capabilities for all device types // library marker kkossev.commonLib, line 72
        capability 'Configuration' // library marker kkossev.commonLib, line 73
        capability 'Refresh' // library marker kkossev.commonLib, line 74
        capability 'Health Check' // library marker kkossev.commonLib, line 75

        // common attributes for all device types // library marker kkossev.commonLib, line 77
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 78
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 79
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 80

        // common commands for all device types // library marker kkossev.commonLib, line 82
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 83

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 85
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 86

    preferences { // library marker kkossev.commonLib, line 88
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 89
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 90
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 91

        if (device) { // library marker kkossev.commonLib, line 93
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 94
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 95
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 96
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 97
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 98
            } // library marker kkossev.commonLib, line 99
        } // library marker kkossev.commonLib, line 100
    } // library marker kkossev.commonLib, line 101
} // library marker kkossev.commonLib, line 102

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 104
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 105
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 106
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 107
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 108
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 109
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 110
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 111
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 112
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 113
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 114

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 116
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 117
] // library marker kkossev.commonLib, line 118
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 119
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 120
] // library marker kkossev.commonLib, line 121

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 123
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 124
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 125
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 126
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 127
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 128
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 129
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 130
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 131
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 132
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 133
] // library marker kkossev.commonLib, line 134

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 136
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 137
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 138
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 139
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 140
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 141

/** // library marker kkossev.commonLib, line 143
 * Parse Zigbee message // library marker kkossev.commonLib, line 144
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 145
 */ // library marker kkossev.commonLib, line 146
void parse(final String description) { // library marker kkossev.commonLib, line 147
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 148
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 149
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 150
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 151

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 153
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 154
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 155
            parseIasMessage(description) // library marker kkossev.commonLib, line 156
        } // library marker kkossev.commonLib, line 157
        else { // library marker kkossev.commonLib, line 158
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 159
        } // library marker kkossev.commonLib, line 160
        return // library marker kkossev.commonLib, line 161
    } // library marker kkossev.commonLib, line 162
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 163
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 164
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 165
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 166
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 167
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 168
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 169
        return // library marker kkossev.commonLib, line 170
    } // library marker kkossev.commonLib, line 171

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 173
        return // library marker kkossev.commonLib, line 174
    } // library marker kkossev.commonLib, line 175
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 176

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 178
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 179

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 181
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 182
        return // library marker kkossev.commonLib, line 183
    } // library marker kkossev.commonLib, line 184
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 185
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 186
        return // library marker kkossev.commonLib, line 187
    } // library marker kkossev.commonLib, line 188
    // // library marker kkossev.commonLib, line 189
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 190
    // // library marker kkossev.commonLib, line 191
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 192
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 193
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 194
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 195
            break // library marker kkossev.commonLib, line 196
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 197
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 198
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 199
            break // library marker kkossev.commonLib, line 200
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 201
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 202
            break // library marker kkossev.commonLib, line 203
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 204
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 205
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 206
            break // library marker kkossev.commonLib, line 207
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 208
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 209
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 210
            break // library marker kkossev.commonLib, line 211
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 212
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 213
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 214
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 215
            } // library marker kkossev.commonLib, line 216
            break // library marker kkossev.commonLib, line 217
        default: // library marker kkossev.commonLib, line 218
            if (settings.logEnable) { // library marker kkossev.commonLib, line 219
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 220
            } // library marker kkossev.commonLib, line 221
            break // library marker kkossev.commonLib, line 222
    } // library marker kkossev.commonLib, line 223
} // library marker kkossev.commonLib, line 224

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 226
    0x0000: 'Basic', // library marker kkossev.commonLib, line 227
    0x0001: 'Power', // library marker kkossev.commonLib, line 228
    0x0003: 'Identify', // library marker kkossev.commonLib, line 229
    0x000C: 'AnalogInput', // library marker kkossev.commonLib, line 230
    0x0006: 'OnOff', // library marker kkossev.commonLib, line 231
    0x0008: 'LevelControl', // library marker kkossev.commonLib, line 232
    0x0012: 'MultistateInput', // library marker kkossev.commonLib, line 233
    0x0201: 'Thermostat', // library marker kkossev.commonLib, line 234
    0x0300: 'ColorControl', // library marker kkossev.commonLib, line 235
    0x0400: 'Illuminance', // library marker kkossev.commonLib, line 236
    0x0402: 'Temperature', // library marker kkossev.commonLib, line 237
    0x0405: 'Humidity', // library marker kkossev.commonLib, line 238
    0x0406: 'Occupancy', // library marker kkossev.commonLib, line 239
    0x042A: 'Pm25', // library marker kkossev.commonLib, line 240
    0xE002: 'E002', // library marker kkossev.commonLib, line 241
    0xEC03: 'EC03', // library marker kkossev.commonLib, line 242
    0xEF00: 'Tuya', // library marker kkossev.commonLib, line 243
    0xFC11: 'FC11', // library marker kkossev.commonLib, line 244
    0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 245
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 246
] // library marker kkossev.commonLib, line 247

boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 249
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 250
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 251
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 252
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 253
        return false // library marker kkossev.commonLib, line 254
    } // library marker kkossev.commonLib, line 255
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 256
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 257
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 258
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 259
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 260
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 261
        return true // library marker kkossev.commonLib, line 262
    } // library marker kkossev.commonLib, line 263
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 264
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 265
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 266
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 267
        return true // library marker kkossev.commonLib, line 268
    } // library marker kkossev.commonLib, line 269
    if (device?.getDataValue('model') != 'ZigUSB') {    // patch! // library marker kkossev.commonLib, line 270
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 271
    } // library marker kkossev.commonLib, line 272
    return false // library marker kkossev.commonLib, line 273
} // library marker kkossev.commonLib, line 274

static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 276
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 277
} // library marker kkossev.commonLib, line 278

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 280
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 281
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 282
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 283
    } // library marker kkossev.commonLib, line 284
    return false // library marker kkossev.commonLib, line 285
} // library marker kkossev.commonLib, line 286

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 288
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 289
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 290
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 291
    } // library marker kkossev.commonLib, line 292
    return false // library marker kkossev.commonLib, line 293
} // library marker kkossev.commonLib, line 294

boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 296
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 297
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 298
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 299
    } // library marker kkossev.commonLib, line 300
    return false // library marker kkossev.commonLib, line 301
} // library marker kkossev.commonLib, line 302

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 304
    0x0002: 'Node Descriptor Request', 0x0005: 'Active Endpoints Request', 0x0006: 'Match Descriptor Request', 0x0022: 'Unbind Request', 0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 305
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 306
    0x8021: 'Bind Response', 0x8022: 'Unbind Response', 0x8023: 'Bind Register Response', 0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 307
] // library marker kkossev.commonLib, line 308

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 310
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 311
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 312
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 313
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 314
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 315
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 316
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 317
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 318
    List<String> cmds = [] // library marker kkossev.commonLib, line 319
    switch (clusterId) { // library marker kkossev.commonLib, line 320
        case 0x0005 : // library marker kkossev.commonLib, line 321
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 322
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 323
            // send the active endpoint response // library marker kkossev.commonLib, line 324
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 325
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 326
            break // library marker kkossev.commonLib, line 327
        case 0x0006 : // library marker kkossev.commonLib, line 328
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 329
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 330
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 331
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 332
            break // library marker kkossev.commonLib, line 333
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 334
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 335
            if (settings?.logEnable) { log.info "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 336
            break // library marker kkossev.commonLib, line 337
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 338
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 339
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 340
            break // library marker kkossev.commonLib, line 341
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 342
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 343
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 344
            if (settings?.logEnable) { log.info "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 345
            break // library marker kkossev.commonLib, line 346
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 347
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 348
            break // library marker kkossev.commonLib, line 349
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 350
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 351
            if (settings?.logEnable) { log.info "${clusterInfo}" } // library marker kkossev.commonLib, line 352
            break // library marker kkossev.commonLib, line 353
        default : // library marker kkossev.commonLib, line 354
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 355
            break // library marker kkossev.commonLib, line 356
    } // library marker kkossev.commonLib, line 357
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 358
} // library marker kkossev.commonLib, line 359

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 361
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 362
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 363
    switch (commandId) { // library marker kkossev.commonLib, line 364
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 365
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 366
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 367
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 368
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 369
        default: // library marker kkossev.commonLib, line 370
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 371
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 372
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 373
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 374
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 375
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 376
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 377
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 378
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 379
            } // library marker kkossev.commonLib, line 380
            break // library marker kkossev.commonLib, line 381
    } // library marker kkossev.commonLib, line 382
} // library marker kkossev.commonLib, line 383

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 385
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 386
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 387
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 388
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 389
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 390
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 391
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 392
    } // library marker kkossev.commonLib, line 393
    else { // library marker kkossev.commonLib, line 394
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 395
    } // library marker kkossev.commonLib, line 396
} // library marker kkossev.commonLib, line 397

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 399
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 400
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 401
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 402
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 403
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 404
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 405
    } // library marker kkossev.commonLib, line 406
    else { // library marker kkossev.commonLib, line 407
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 408
    } // library marker kkossev.commonLib, line 409
} // library marker kkossev.commonLib, line 410

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 412
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 413
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 414
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 415
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 416
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 417
        state.reportingEnabled = true // library marker kkossev.commonLib, line 418
    } // library marker kkossev.commonLib, line 419
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 420
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 421
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 422
    } else { // library marker kkossev.commonLib, line 423
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 424
    } // library marker kkossev.commonLib, line 425
} // library marker kkossev.commonLib, line 426

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 428
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 429
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 430
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 431
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 432
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 433
    if (status == 0) { // library marker kkossev.commonLib, line 434
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 435
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 436
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 437
        int delta = 0 // library marker kkossev.commonLib, line 438
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 439
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 440
        } // library marker kkossev.commonLib, line 441
        else { // library marker kkossev.commonLib, line 442
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 443
        } // library marker kkossev.commonLib, line 444
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 445
    } // library marker kkossev.commonLib, line 446
    else { // library marker kkossev.commonLib, line 447
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 448
    } // library marker kkossev.commonLib, line 449
} // library marker kkossev.commonLib, line 450

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 452
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 453
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 454
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 455
        return false // library marker kkossev.commonLib, line 456
    } // library marker kkossev.commonLib, line 457
    // execute the customHandler function // library marker kkossev.commonLib, line 458
    boolean result = false // library marker kkossev.commonLib, line 459
    try { // library marker kkossev.commonLib, line 460
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 461
    } // library marker kkossev.commonLib, line 462
    catch (e) { // library marker kkossev.commonLib, line 463
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 464
        return false // library marker kkossev.commonLib, line 465
    } // library marker kkossev.commonLib, line 466
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 467
    return result // library marker kkossev.commonLib, line 468
} // library marker kkossev.commonLib, line 469

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 471
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 472
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 473
    final String commandId = data[0] // library marker kkossev.commonLib, line 474
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 475
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 476
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 477
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 478
    } else { // library marker kkossev.commonLib, line 479
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 480
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 481
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 482
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 483
        } // library marker kkossev.commonLib, line 484
    } // library marker kkossev.commonLib, line 485
} // library marker kkossev.commonLib, line 486

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 488
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 489
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 490
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 491

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 493
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 494
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 495
] // library marker kkossev.commonLib, line 496

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 498
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 499
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 500
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 501
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 502
] // library marker kkossev.commonLib, line 503

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 505
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 506
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 507
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 508
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 509
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 510
    return avg // library marker kkossev.commonLib, line 511
} // library marker kkossev.commonLib, line 512

/* // library marker kkossev.commonLib, line 514
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 515
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 516
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 517
*/ // library marker kkossev.commonLib, line 518
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 519

// Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 521
void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 522
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 523
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 524
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 525
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 526
        case 0x0000: // library marker kkossev.commonLib, line 527
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 528
            break // library marker kkossev.commonLib, line 529
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 530
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 531
            if (isPing) { // library marker kkossev.commonLib, line 532
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 533
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 534
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 535
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 536
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 537
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 538
                    sendRttEvent() // library marker kkossev.commonLib, line 539
                } // library marker kkossev.commonLib, line 540
                else { // library marker kkossev.commonLib, line 541
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 542
                } // library marker kkossev.commonLib, line 543
                state.states['isPing'] = false // library marker kkossev.commonLib, line 544
            } // library marker kkossev.commonLib, line 545
            else { // library marker kkossev.commonLib, line 546
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 547
            } // library marker kkossev.commonLib, line 548
            break // library marker kkossev.commonLib, line 549
        case 0x0004: // library marker kkossev.commonLib, line 550
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 551
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 552
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 553
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 554
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 555
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 556
            } // library marker kkossev.commonLib, line 557
            break // library marker kkossev.commonLib, line 558
        case 0x0005: // library marker kkossev.commonLib, line 559
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 560
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 561
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 562
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 563
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 564
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 565
            } // library marker kkossev.commonLib, line 566
            break // library marker kkossev.commonLib, line 567
        case 0x0007: // library marker kkossev.commonLib, line 568
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 569
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 570
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 571
            break // library marker kkossev.commonLib, line 572
        case 0xFFDF: // library marker kkossev.commonLib, line 573
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 574
            break // library marker kkossev.commonLib, line 575
        case 0xFFE2: // library marker kkossev.commonLib, line 576
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 577
            break // library marker kkossev.commonLib, line 578
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 579
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 580
            break // library marker kkossev.commonLib, line 581
        case 0xFFFE: // library marker kkossev.commonLib, line 582
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 583
            break // library marker kkossev.commonLib, line 584
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 585
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 586
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 587
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 588
            break // library marker kkossev.commonLib, line 589
        default: // library marker kkossev.commonLib, line 590
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 591
            break // library marker kkossev.commonLib, line 592
    } // library marker kkossev.commonLib, line 593
} // library marker kkossev.commonLib, line 594

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 596
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 597
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 598

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 600
    Map descMap = [:] // library marker kkossev.commonLib, line 601
    try { // library marker kkossev.commonLib, line 602
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 603
    } // library marker kkossev.commonLib, line 604
    catch (e1) { // library marker kkossev.commonLib, line 605
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 606
        // try alternative custom parsing // library marker kkossev.commonLib, line 607
        descMap = [:] // library marker kkossev.commonLib, line 608
        try { // library marker kkossev.commonLib, line 609
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 610
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 611
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 612
            } // library marker kkossev.commonLib, line 613
        } // library marker kkossev.commonLib, line 614
        catch (e2) { // library marker kkossev.commonLib, line 615
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 616
            return [:] // library marker kkossev.commonLib, line 617
        } // library marker kkossev.commonLib, line 618
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 619
    } // library marker kkossev.commonLib, line 620
    return descMap // library marker kkossev.commonLib, line 621
} // library marker kkossev.commonLib, line 622

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 624
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 625
        return false // library marker kkossev.commonLib, line 626
    } // library marker kkossev.commonLib, line 627
    // try to parse ... // library marker kkossev.commonLib, line 628
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 629
    Map descMap = [:] // library marker kkossev.commonLib, line 630
    try { // library marker kkossev.commonLib, line 631
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 632
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 633
    } // library marker kkossev.commonLib, line 634
    catch (e) { // library marker kkossev.commonLib, line 635
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 636
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 637
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 638
        return true // library marker kkossev.commonLib, line 639
    } // library marker kkossev.commonLib, line 640

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 642
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 643
    } // library marker kkossev.commonLib, line 644
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 645
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 646
    } // library marker kkossev.commonLib, line 647
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 648
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 649
    } // library marker kkossev.commonLib, line 650
    else { // library marker kkossev.commonLib, line 651
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 652
        return false // library marker kkossev.commonLib, line 653
    } // library marker kkossev.commonLib, line 654
    return true    // processed // library marker kkossev.commonLib, line 655
} // library marker kkossev.commonLib, line 656

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 658
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 659
  /* // library marker kkossev.commonLib, line 660
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 661
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 662
        return true // library marker kkossev.commonLib, line 663
    } // library marker kkossev.commonLib, line 664
*/ // library marker kkossev.commonLib, line 665
    Map descMap = [:] // library marker kkossev.commonLib, line 666
    try { // library marker kkossev.commonLib, line 667
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 668
    } // library marker kkossev.commonLib, line 669
    catch (e1) { // library marker kkossev.commonLib, line 670
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 671
        // try alternative custom parsing // library marker kkossev.commonLib, line 672
        descMap = [:] // library marker kkossev.commonLib, line 673
        try { // library marker kkossev.commonLib, line 674
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 675
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 676
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 677
            } // library marker kkossev.commonLib, line 678
        } // library marker kkossev.commonLib, line 679
        catch (e2) { // library marker kkossev.commonLib, line 680
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 681
            return true // library marker kkossev.commonLib, line 682
        } // library marker kkossev.commonLib, line 683
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 684
    } // library marker kkossev.commonLib, line 685
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 686
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 687
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 688
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 689
        return false // library marker kkossev.commonLib, line 690
    } // library marker kkossev.commonLib, line 691
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 692
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 693
    // attribute report received // library marker kkossev.commonLib, line 694
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 695
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 696
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 697
    } // library marker kkossev.commonLib, line 698
    attrData.each { // library marker kkossev.commonLib, line 699
        if (it.status == '86') { // library marker kkossev.commonLib, line 700
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 701
        // TODO - skip parsing? // library marker kkossev.commonLib, line 702
        } // library marker kkossev.commonLib, line 703
        switch (it.cluster) { // library marker kkossev.commonLib, line 704
            case '0000' : // library marker kkossev.commonLib, line 705
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 706
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 707
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 708
                } // library marker kkossev.commonLib, line 709
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 710
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 711
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 712
                } // library marker kkossev.commonLib, line 713
                else { // library marker kkossev.commonLib, line 714
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 715
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 716
                } // library marker kkossev.commonLib, line 717
                break // library marker kkossev.commonLib, line 718
            default : // library marker kkossev.commonLib, line 719
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 720
                break // library marker kkossev.commonLib, line 721
        } // switch // library marker kkossev.commonLib, line 722
    } // for each attribute // library marker kkossev.commonLib, line 723
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 724
} // library marker kkossev.commonLib, line 725


String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 728
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 729
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 730
} // library marker kkossev.commonLib, line 731

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 733
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 734
} // library marker kkossev.commonLib, line 735

// Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 737
void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 738
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { logWarn 'parseElectricalMeasureCluster is NOT implemented1' } // library marker kkossev.commonLib, line 739
} // library marker kkossev.commonLib, line 740

// Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 742
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 743
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { logWarn 'parseMeteringCluster is NOT implemented1' } // library marker kkossev.commonLib, line 744
} // library marker kkossev.commonLib, line 745

// Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 747
void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 748
    if (this.respondsTo('customParseWindowCoveringCluster')) { customParseWindowCoveringCluster(descMap) } else { logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 749
} // library marker kkossev.commonLib, line 750

/* // library marker kkossev.commonLib, line 752
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 753
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 754
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 755
*/ // library marker kkossev.commonLib, line 756
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 757
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 758
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 759

// Tuya Commands // library marker kkossev.commonLib, line 761
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 762
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 763
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 764
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 765
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 766

// tuya DP type // library marker kkossev.commonLib, line 768
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 769
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 770
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 771
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 772
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 773
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 774

void syncTuyaDateTime() { // library marker kkossev.commonLib, line 776
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 777
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 778
    long offset = 0 // library marker kkossev.commonLib, line 779
    int offsetHours = 0 // library marker kkossev.commonLib, line 780
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 781
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 782
    try { // library marker kkossev.commonLib, line 783
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 784
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 785
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 786
    } catch (e) { // library marker kkossev.commonLib, line 787
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 788
    } // library marker kkossev.commonLib, line 789
    // // library marker kkossev.commonLib, line 790
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 791
    String dateTimeNow = unix2formattedDate(now()) // library marker kkossev.commonLib, line 792
    logDebug "sending time data : ${dateTimeNow} (${cmds})" // library marker kkossev.commonLib, line 793
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 794
    logInfo "Tuya device time synchronized to ${dateTimeNow}" // library marker kkossev.commonLib, line 795
} // library marker kkossev.commonLib, line 796

void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 798
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
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 827
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 828
        } // library marker kkossev.commonLib, line 829
    } // library marker kkossev.commonLib, line 830
    else { // library marker kkossev.commonLib, line 831
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 832
    } // library marker kkossev.commonLib, line 833
} // library marker kkossev.commonLib, line 834

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 836
    logTrace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 837
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 838
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 839
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 840
            return // library marker kkossev.commonLib, line 841
        } // library marker kkossev.commonLib, line 842
    } // library marker kkossev.commonLib, line 843
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) {  // check if the method  method exists // library marker kkossev.commonLib, line 844
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 845
            return // library marker kkossev.commonLib, line 846
        } // library marker kkossev.commonLib, line 847
    } // library marker kkossev.commonLib, line 848
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 849
} // library marker kkossev.commonLib, line 850

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 852
    int retValue = 0 // library marker kkossev.commonLib, line 853
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 854
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 855
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 856
        int power = 1 // library marker kkossev.commonLib, line 857
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 858
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 859
            power = power * 256 // library marker kkossev.commonLib, line 860
        } // library marker kkossev.commonLib, line 861
    } // library marker kkossev.commonLib, line 862
    return retValue // library marker kkossev.commonLib, line 863
} // library marker kkossev.commonLib, line 864

private List<String> getTuyaCommand(String dp, String dp_type, String fncmd) { return sendTuyaCommand(dp, dp_type, fncmd) } // library marker kkossev.commonLib, line 866

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 868
    List<String> cmds = [] // library marker kkossev.commonLib, line 869
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 870
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 871
    int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 872
    //tuyaCmd = 0x04  // !!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.commonLib, line 873
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 874
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 875
    return cmds // library marker kkossev.commonLib, line 876
} // library marker kkossev.commonLib, line 877

private getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 879

void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 881
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 882
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 883
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 884
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 885
} // library marker kkossev.commonLib, line 886

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 888
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 889

List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 891
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 892
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 893
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 894
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 895
} // library marker kkossev.commonLib, line 896

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 898
    List<String> cmds = [] // library marker kkossev.commonLib, line 899
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 900
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 901
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 902
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 903
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 904
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 905
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 906
        } // library marker kkossev.commonLib, line 907
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 908
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 909
    } // library marker kkossev.commonLib, line 910
    else { // library marker kkossev.commonLib, line 911
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 912
    } // library marker kkossev.commonLib, line 913
} // library marker kkossev.commonLib, line 914

// Invoked from configure() // library marker kkossev.commonLib, line 916
List<String> initializeDevice() { // library marker kkossev.commonLib, line 917
    List<String> cmds = [] // library marker kkossev.commonLib, line 918
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 919
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 920
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 921
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 922
    } // library marker kkossev.commonLib, line 923
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 924
    return cmds // library marker kkossev.commonLib, line 925
} // library marker kkossev.commonLib, line 926

// Invoked from configure() // library marker kkossev.commonLib, line 928
List<String> configureDevice() { // library marker kkossev.commonLib, line 929
    List<String> cmds = [] // library marker kkossev.commonLib, line 930
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 931
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 932
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 933
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 934
    } // library marker kkossev.commonLib, line 935
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 936
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 937
    return cmds // library marker kkossev.commonLib, line 938
} // library marker kkossev.commonLib, line 939

/* // library marker kkossev.commonLib, line 941
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 942
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 943
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 944
*/ // library marker kkossev.commonLib, line 945

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 947
    List<String> cmds = [] // library marker kkossev.commonLib, line 948
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 949
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 950
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 951
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 952
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 953
            } // library marker kkossev.commonLib, line 954
        } // library marker kkossev.commonLib, line 955
    } // library marker kkossev.commonLib, line 956
    return cmds // library marker kkossev.commonLib, line 957
} // library marker kkossev.commonLib, line 958

void refresh() { // library marker kkossev.commonLib, line 960
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 961
    checkDriverVersion(state) // library marker kkossev.commonLib, line 962
    List<String> cmds = [] // library marker kkossev.commonLib, line 963
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 964
    List<String> customCmds = customHandlers(['batteryRefresh', 'groupsRefresh', 'onOffRefresh', 'customRefresh']) // library marker kkossev.commonLib, line 965
    if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customHandlers refresh() defined' } // library marker kkossev.commonLib, line 966
    if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 967
        cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 968
        cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 969
    } // library marker kkossev.commonLib, line 970
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 971
        cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 972
        cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 973
    } // library marker kkossev.commonLib, line 974
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 975
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 976
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 977
    } // library marker kkossev.commonLib, line 978
    else { // library marker kkossev.commonLib, line 979
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 980
    } // library marker kkossev.commonLib, line 981
} // library marker kkossev.commonLib, line 982

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 984
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 985
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 986

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 988
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 989
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 990
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 991
    } // library marker kkossev.commonLib, line 992
    else { // library marker kkossev.commonLib, line 993
        logInfo "${info}" // library marker kkossev.commonLib, line 994
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 995
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 996
    } // library marker kkossev.commonLib, line 997
} // library marker kkossev.commonLib, line 998

public void ping() { // library marker kkossev.commonLib, line 1000
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1001
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 1002
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1003
    if (isVirtual()) { runInMillis(10, virtualPong) } // library marker kkossev.commonLib, line 1004
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) } // library marker kkossev.commonLib, line 1005
    logDebug 'ping...' // library marker kkossev.commonLib, line 1006
} // library marker kkossev.commonLib, line 1007

def virtualPong() { // library marker kkossev.commonLib, line 1009
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1010
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1011
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1012
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1013
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1014
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1015
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1016
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1017
        sendRttEvent() // library marker kkossev.commonLib, line 1018
    } // library marker kkossev.commonLib, line 1019
    else { // library marker kkossev.commonLib, line 1020
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1021
    } // library marker kkossev.commonLib, line 1022
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1023
    //unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1024
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1025
} // library marker kkossev.commonLib, line 1026

void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1028
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1029
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1030
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1031
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1032
    if (value == null) { // library marker kkossev.commonLib, line 1033
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1034
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 1035
    } // library marker kkossev.commonLib, line 1036
    else { // library marker kkossev.commonLib, line 1037
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1038
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1039
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 1040
    } // library marker kkossev.commonLib, line 1041
} // library marker kkossev.commonLib, line 1042

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1044
    if (cluster != null) { // library marker kkossev.commonLib, line 1045
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1046
    } // library marker kkossev.commonLib, line 1047
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1048
    return 'NULL' // library marker kkossev.commonLib, line 1049
} // library marker kkossev.commonLib, line 1050

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1052
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1053
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1054
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1055
} // library marker kkossev.commonLib, line 1056

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1058
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1059
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1060
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1061
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1062
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1063
    } // library marker kkossev.commonLib, line 1064
} // library marker kkossev.commonLib, line 1065

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1067
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1068
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1069
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1070
} // library marker kkossev.commonLib, line 1071

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1073
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1074
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1075
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1076
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1077
    } // library marker kkossev.commonLib, line 1078
    else { // library marker kkossev.commonLib, line 1079
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1080
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1081
    } // library marker kkossev.commonLib, line 1082
} // library marker kkossev.commonLib, line 1083

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1085
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1086
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1087
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1088
} // library marker kkossev.commonLib, line 1089

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1091
void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1092
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1093
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1094
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1095
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1096
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1097
    } // library marker kkossev.commonLib, line 1098
} // library marker kkossev.commonLib, line 1099

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1101
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1102
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1103
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1104
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1105
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1106
            logWarn 'not present!' // library marker kkossev.commonLib, line 1107
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1108
        } // library marker kkossev.commonLib, line 1109
    } // library marker kkossev.commonLib, line 1110
    else { // library marker kkossev.commonLib, line 1111
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1112
    } // library marker kkossev.commonLib, line 1113
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1114
} // library marker kkossev.commonLib, line 1115

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1117
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1118
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 1119
    if (value == 'online') { // library marker kkossev.commonLib, line 1120
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1121
    } // library marker kkossev.commonLib, line 1122
    else { // library marker kkossev.commonLib, line 1123
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1124
    } // library marker kkossev.commonLib, line 1125
} // library marker kkossev.commonLib, line 1126

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1128
void updated() { // library marker kkossev.commonLib, line 1129
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1130
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1131
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1132
    unschedule() // library marker kkossev.commonLib, line 1133

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1135
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1136
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1137
    } // library marker kkossev.commonLib, line 1138
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1139
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1140
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1141
    } // library marker kkossev.commonLib, line 1142

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1144
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1145
        // schedule the periodic timer // library marker kkossev.commonLib, line 1146
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1147
        if (interval > 0) { // library marker kkossev.commonLib, line 1148
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1149
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1150
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1151
        } // library marker kkossev.commonLib, line 1152
    } // library marker kkossev.commonLib, line 1153
    else { // library marker kkossev.commonLib, line 1154
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1155
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1156
    } // library marker kkossev.commonLib, line 1157
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1158
        customUpdated() // library marker kkossev.commonLib, line 1159
    } // library marker kkossev.commonLib, line 1160

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1162
} // library marker kkossev.commonLib, line 1163

void logsOff() { // library marker kkossev.commonLib, line 1165
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1166
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1167
} // library marker kkossev.commonLib, line 1168
void traceOff() { // library marker kkossev.commonLib, line 1169
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1170
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1171
} // library marker kkossev.commonLib, line 1172

void configure(String command) { // library marker kkossev.commonLib, line 1174
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1175
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1176
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1177
        return // library marker kkossev.commonLib, line 1178
    } // library marker kkossev.commonLib, line 1179
    // // library marker kkossev.commonLib, line 1180
    String func // library marker kkossev.commonLib, line 1181
    try { // library marker kkossev.commonLib, line 1182
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1183
        "$func"() // library marker kkossev.commonLib, line 1184
    } // library marker kkossev.commonLib, line 1185
    catch (e) { // library marker kkossev.commonLib, line 1186
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1187
        return // library marker kkossev.commonLib, line 1188
    } // library marker kkossev.commonLib, line 1189
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1190
} // library marker kkossev.commonLib, line 1191

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1193
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1194
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1195
} // library marker kkossev.commonLib, line 1196

void loadAllDefaults() { // library marker kkossev.commonLib, line 1198
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1199
    deleteAllSettings() // library marker kkossev.commonLib, line 1200
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1201
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1202
    deleteAllStates() // library marker kkossev.commonLib, line 1203
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1204
    initialize() // library marker kkossev.commonLib, line 1205
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1206
    updated() // library marker kkossev.commonLib, line 1207
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1208
} // library marker kkossev.commonLib, line 1209

void configureNow() { // library marker kkossev.commonLib, line 1211
    configure() // library marker kkossev.commonLib, line 1212
} // library marker kkossev.commonLib, line 1213

/** // library marker kkossev.commonLib, line 1215
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1216
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1217
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1218
 */ // library marker kkossev.commonLib, line 1219
void configure() { // library marker kkossev.commonLib, line 1220
    List<String> cmds = [] // library marker kkossev.commonLib, line 1221
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1222
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1223
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1224
    if (isTuya()) { // library marker kkossev.commonLib, line 1225
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1226
    } // library marker kkossev.commonLib, line 1227
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1228
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1229
    } // library marker kkossev.commonLib, line 1230
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1231
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1232
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1233
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1234
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1235
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1236
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1237
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1238
    } // library marker kkossev.commonLib, line 1239
    else { // library marker kkossev.commonLib, line 1240
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1241
    } // library marker kkossev.commonLib, line 1242
} // library marker kkossev.commonLib, line 1243

 // Invoked when the device is installed or when driver is installed ? // library marker kkossev.commonLib, line 1245
void installed() { // library marker kkossev.commonLib, line 1246
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1247
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1248
    // populate some default values for attributes // library marker kkossev.commonLib, line 1249
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 1250
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 1251
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1252
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1253
} // library marker kkossev.commonLib, line 1254

 // Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1256
void initialize() { // library marker kkossev.commonLib, line 1257
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1258
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1259
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1260
    updateTuyaVersion() // library marker kkossev.commonLib, line 1261
    updateAqaraVersion() // library marker kkossev.commonLib, line 1262
} // library marker kkossev.commonLib, line 1263

/* // library marker kkossev.commonLib, line 1265
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1266
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1267
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1268
*/ // library marker kkossev.commonLib, line 1269

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1271
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1272
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1273
} // library marker kkossev.commonLib, line 1274

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1276
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1277
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1278
} // library marker kkossev.commonLib, line 1279

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1281
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1282
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1283
} // library marker kkossev.commonLib, line 1284

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1286
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1287
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1288
        return // library marker kkossev.commonLib, line 1289
    } // library marker kkossev.commonLib, line 1290
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1291
    cmd.each { // library marker kkossev.commonLib, line 1292
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1293
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1294
            return // library marker kkossev.commonLib, line 1295
        } // library marker kkossev.commonLib, line 1296
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1297
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1298
    } // library marker kkossev.commonLib, line 1299
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1300
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1301
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1302
} // library marker kkossev.commonLib, line 1303

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1305

String getDeviceInfo() { // library marker kkossev.commonLib, line 1307
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1308
} // library marker kkossev.commonLib, line 1309

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1311
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1312
} // library marker kkossev.commonLib, line 1313

@CompileStatic // library marker kkossev.commonLib, line 1315
void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1316
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1317
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1318
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1319
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1320
        initializeVars(false) // library marker kkossev.commonLib, line 1321
        updateTuyaVersion() // library marker kkossev.commonLib, line 1322
        updateAqaraVersion() // library marker kkossev.commonLib, line 1323
    } // library marker kkossev.commonLib, line 1324
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1325
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1326
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1327
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1328
} // library marker kkossev.commonLib, line 1329

// credits @thebearmay // library marker kkossev.commonLib, line 1331
String getModel() { // library marker kkossev.commonLib, line 1332
    try { // library marker kkossev.commonLib, line 1333
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1334
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1335
    } catch (ignore) { // library marker kkossev.commonLib, line 1336
        try { // library marker kkossev.commonLib, line 1337
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1338
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1339
                return model // library marker kkossev.commonLib, line 1340
            } // library marker kkossev.commonLib, line 1341
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1342
            return '' // library marker kkossev.commonLib, line 1343
        } // library marker kkossev.commonLib, line 1344
    } // library marker kkossev.commonLib, line 1345
} // library marker kkossev.commonLib, line 1346

// credits @thebearmay // library marker kkossev.commonLib, line 1348
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1349
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1350
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1351
    String revision = tokens.last() // library marker kkossev.commonLib, line 1352
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1353
} // library marker kkossev.commonLib, line 1354

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1356
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1357
    unschedule() // library marker kkossev.commonLib, line 1358
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1359
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1360

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1362
} // library marker kkossev.commonLib, line 1363

void resetStatistics() { // library marker kkossev.commonLib, line 1365
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1366
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1367
} // library marker kkossev.commonLib, line 1368

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1370
void resetStats() { // library marker kkossev.commonLib, line 1371
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1372
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1373
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1374
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1375
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1376
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1377
} // library marker kkossev.commonLib, line 1378

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1380
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1381
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1382
        state.clear() // library marker kkossev.commonLib, line 1383
        unschedule() // library marker kkossev.commonLib, line 1384
        resetStats() // library marker kkossev.commonLib, line 1385
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 1386
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1387
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1388
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1389
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1390
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1391
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1392
    } // library marker kkossev.commonLib, line 1393

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1395
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1396
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1397
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1398
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1399

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1401
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1402
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1403
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1404
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1405
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1406
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1407

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1409

    // common libraries initialization // library marker kkossev.commonLib, line 1411
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1412
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1413
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1414
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1415

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1417
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1418
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1419
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1420

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1422
    if ( mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1423
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1424
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1425
    if ( ep  != null) { // library marker kkossev.commonLib, line 1426
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1427
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1428
    } // library marker kkossev.commonLib, line 1429
    else { // library marker kkossev.commonLib, line 1430
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1431
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1432
    } // library marker kkossev.commonLib, line 1433
} // library marker kkossev.commonLib, line 1434

void setDestinationEP() { // library marker kkossev.commonLib, line 1436
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1437
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1438
        state.destinationEP = ep // library marker kkossev.commonLib, line 1439
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1440
    } // library marker kkossev.commonLib, line 1441
    else { // library marker kkossev.commonLib, line 1442
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1443
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 1444
    } // library marker kkossev.commonLib, line 1445
} // library marker kkossev.commonLib, line 1446

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1448
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1449
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1450
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1451

// _DEBUG mode only // library marker kkossev.commonLib, line 1453
void getAllProperties() { // library marker kkossev.commonLib, line 1454
    log.trace 'Properties:' // library marker kkossev.commonLib, line 1455
    device.properties.each { it -> // library marker kkossev.commonLib, line 1456
        log.debug it // library marker kkossev.commonLib, line 1457
    } // library marker kkossev.commonLib, line 1458
    log.trace 'Settings:' // library marker kkossev.commonLib, line 1459
    settings.each { it -> // library marker kkossev.commonLib, line 1460
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1461
    } // library marker kkossev.commonLib, line 1462
    log.trace 'Done' // library marker kkossev.commonLib, line 1463
} // library marker kkossev.commonLib, line 1464

// delete all Preferences // library marker kkossev.commonLib, line 1466
void deleteAllSettings() { // library marker kkossev.commonLib, line 1467
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1468
    settings.each { it -> // library marker kkossev.commonLib, line 1469
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 1470
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 1471
    } // library marker kkossev.commonLib, line 1472
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1473
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1474
} // library marker kkossev.commonLib, line 1475

// delete all attributes // library marker kkossev.commonLib, line 1477
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1478
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1479
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1480
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1481
} // library marker kkossev.commonLib, line 1482

// delete all State Variables // library marker kkossev.commonLib, line 1484
void deleteAllStates() { // library marker kkossev.commonLib, line 1485
    String stateDeleted = '' // library marker kkossev.commonLib, line 1486
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1487
    state.clear() // library marker kkossev.commonLib, line 1488
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1489
} // library marker kkossev.commonLib, line 1490

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1492
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1493
} // library marker kkossev.commonLib, line 1494

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1496
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 1497
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 1498
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 1499
    } // library marker kkossev.commonLib, line 1500
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1501
} // library marker kkossev.commonLib, line 1502

void testParse(String par) { // library marker kkossev.commonLib, line 1504
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1505
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1506
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1507
    parse(par) // library marker kkossev.commonLib, line 1508
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1509
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1510
} // library marker kkossev.commonLib, line 1511

def testJob() { // library marker kkossev.commonLib, line 1513
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1514
} // library marker kkossev.commonLib, line 1515

/** // library marker kkossev.commonLib, line 1517
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1518
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1519
 */ // library marker kkossev.commonLib, line 1520
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1521
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1522
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1523
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1524
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1525
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1526
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1527
    String cron // library marker kkossev.commonLib, line 1528
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1529
    else { // library marker kkossev.commonLib, line 1530
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1531
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1532
    } // library marker kkossev.commonLib, line 1533
    return cron // library marker kkossev.commonLib, line 1534
} // library marker kkossev.commonLib, line 1535

// credits @thebearmay // library marker kkossev.commonLib, line 1537
String formatUptime() { // library marker kkossev.commonLib, line 1538
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1539
} // library marker kkossev.commonLib, line 1540

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1542
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1543
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1544
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1545
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1546
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1547
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1548
} // library marker kkossev.commonLib, line 1549

boolean isTuya() { // library marker kkossev.commonLib, line 1551
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1552
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1553
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1554
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1555
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1556
} // library marker kkossev.commonLib, line 1557

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1559
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1560
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1561
    if (application != null) { // library marker kkossev.commonLib, line 1562
        Integer ver // library marker kkossev.commonLib, line 1563
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1564
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1565
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1566
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1567
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1568
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1569
        } // library marker kkossev.commonLib, line 1570
    } // library marker kkossev.commonLib, line 1571
} // library marker kkossev.commonLib, line 1572

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1574

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1576
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1577
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1578
    if (application != null) { // library marker kkossev.commonLib, line 1579
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1580
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1581
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1582
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1583
        } // library marker kkossev.commonLib, line 1584
    } // library marker kkossev.commonLib, line 1585
} // library marker kkossev.commonLib, line 1586

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1588
    try { // library marker kkossev.commonLib, line 1589
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1590
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1591
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1592
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1593
    } catch (e) { // library marker kkossev.commonLib, line 1594
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1595
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1596
    } // library marker kkossev.commonLib, line 1597
} // library marker kkossev.commonLib, line 1598

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1600
    try { // library marker kkossev.commonLib, line 1601
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1602
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1603
        return date.getTime() // library marker kkossev.commonLib, line 1604
    } catch (e) { // library marker kkossev.commonLib, line 1605
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1606
        return now() // library marker kkossev.commonLib, line 1607
    } // library marker kkossev.commonLib, line 1608
} // library marker kkossev.commonLib, line 1609

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
    version: '3.1.3', // library marker kkossev.deviceProfileLib, line 10
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
 * ver. 3.1.2  2024-05-05 kkossev  - (dev. branch) added isSpammyDeviceProfile() // library marker kkossev.deviceProfileLib, line 32
 * ver. 3.1.3  2024-05-21 kkossev  - (dev. branch) skip processClusterAttributeFromDeviceProfile if cluster or attribute or value is missing // library marker kkossev.deviceProfileLib, line 33
 * // library marker kkossev.deviceProfileLib, line 34
 *                                   TODO - updateStateUnknownDPs !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 35
 *                                   TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 36
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 37
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 38
 * // library marker kkossev.deviceProfileLib, line 39
*/ // library marker kkossev.deviceProfileLib, line 40

static String deviceProfileLibVersion()   { '3.1.3' } // library marker kkossev.deviceProfileLib, line 42
static String deviceProfileLibStamp() { '2024/05/21 10:53 AM' } // library marker kkossev.deviceProfileLib, line 43
import groovy.json.* // library marker kkossev.deviceProfileLib, line 44
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 45
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 46
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 47
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 48

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 50

metadata { // library marker kkossev.deviceProfileLib, line 52
    // no capabilities // library marker kkossev.deviceProfileLib, line 53
    // no attributes // library marker kkossev.deviceProfileLib, line 54
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 55
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 56
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 57
    ] // library marker kkossev.deviceProfileLib, line 58
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 59
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 60
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 61
    ] // library marker kkossev.deviceProfileLib, line 62

    preferences { // library marker kkossev.deviceProfileLib, line 64
        // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 65
        if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 66
            (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 67
                if (inputIt(key) != null) { // library marker kkossev.deviceProfileLib, line 68
                    input inputIt(key) // library marker kkossev.deviceProfileLib, line 69
                } // library marker kkossev.deviceProfileLib, line 70
            } // library marker kkossev.deviceProfileLib, line 71
            if (('motionReset' in DEVICE?.preferences) && (DEVICE?.preferences.motionReset == true)) { // library marker kkossev.deviceProfileLib, line 72
                input(name: 'motionReset', type: 'bool', title: '<b>Reset Motion to Inactive</b>', description: '<i>Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b></i>', defaultValue: false) // library marker kkossev.deviceProfileLib, line 73
                if (motionReset.value == true) { // library marker kkossev.deviceProfileLib, line 74
                    input('motionResetTimer', 'number', title: '<b>Motion Reset Timer</b>', description: '<i>After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds</i>', range: '0..7200', defaultValue: 60) // library marker kkossev.deviceProfileLib, line 75
                } // library marker kkossev.deviceProfileLib, line 76
            } // library marker kkossev.deviceProfileLib, line 77
        } // library marker kkossev.deviceProfileLib, line 78
        if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 79
            input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: '<i>Forcely change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!</i>',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 80
        } // library marker kkossev.deviceProfileLib, line 81
    } // library marker kkossev.deviceProfileLib, line 82
} // library marker kkossev.deviceProfileLib, line 83

boolean is2in1() { return getDeviceProfile().contains('TS0601_2IN1') } // library marker kkossev.deviceProfileLib, line 85

String  getDeviceProfile()       { state.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 87
Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2[getDeviceProfile()] } // library marker kkossev.deviceProfileLib, line 88
Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2?.keySet() } // library marker kkossev.deviceProfileLib, line 89
List<String> getDeviceProfilesMap()   { deviceProfilesV3 != null ? deviceProfilesV3.values().description as List<String> : deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 90

// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 92

/** // library marker kkossev.deviceProfileLib, line 94
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 95
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 96
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 97
 */ // library marker kkossev.deviceProfileLib, line 98
String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 99
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 100
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 101
    else { return null } // library marker kkossev.deviceProfileLib, line 102
} // library marker kkossev.deviceProfileLib, line 103

/** // library marker kkossev.deviceProfileLib, line 105
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 106
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 107
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 108
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 109
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 110
 */ // library marker kkossev.deviceProfileLib, line 111
Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 112
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 113
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 114
        if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 115
        return [:] // library marker kkossev.deviceProfileLib, line 116
    } // library marker kkossev.deviceProfileLib, line 117
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 118
    def preference // library marker kkossev.deviceProfileLib, line 119
    try { // library marker kkossev.deviceProfileLib, line 120
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 121
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 122
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 123
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 124
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 125
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 126
        } // library marker kkossev.deviceProfileLib, line 127
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 128
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 129
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 130
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 131
        } // library marker kkossev.deviceProfileLib, line 132
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 133
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 134
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 135
        } // library marker kkossev.deviceProfileLib, line 136
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 137
    } catch (e) { // library marker kkossev.deviceProfileLib, line 138
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 139
        return [:] // library marker kkossev.deviceProfileLib, line 140
    } // library marker kkossev.deviceProfileLib, line 141
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 142
    return foundMap // library marker kkossev.deviceProfileLib, line 143
} // library marker kkossev.deviceProfileLib, line 144

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 146
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 147
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 148
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 149
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 150
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 151
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 152
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 153
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 154
            return foundMap // library marker kkossev.deviceProfileLib, line 155
        } // library marker kkossev.deviceProfileLib, line 156
    } // library marker kkossev.deviceProfileLib, line 157
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 158
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 159
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 160
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 161
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 162
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 163
            return foundMap // library marker kkossev.deviceProfileLib, line 164
        } // library marker kkossev.deviceProfileLib, line 165
    } // library marker kkossev.deviceProfileLib, line 166
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 167
    return [:] // library marker kkossev.deviceProfileLib, line 168
} // library marker kkossev.deviceProfileLib, line 169

/** // library marker kkossev.deviceProfileLib, line 171
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 172
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 173
 */ // library marker kkossev.deviceProfileLib, line 174
void resetPreferencesToDefaults(boolean debug=true) { // library marker kkossev.deviceProfileLib, line 175
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 176
    Map preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 177
    if (preferences == null || preferences.isEmpty()) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 178
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 179
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 180
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 181
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 182
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 183
            return // continue // library marker kkossev.deviceProfileLib, line 184
        } // library marker kkossev.deviceProfileLib, line 185
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 186
        if (parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 187
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'<i>Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 188
        if (parMap.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 189
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 190
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 191
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 192
    } // library marker kkossev.deviceProfileLib, line 193
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 194
} // library marker kkossev.deviceProfileLib, line 195

/** // library marker kkossev.deviceProfileLib, line 197
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 198
 * // library marker kkossev.deviceProfileLib, line 199
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 200
 */ // library marker kkossev.deviceProfileLib, line 201
List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 202
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 203
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 204
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 205
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 206
    } // library marker kkossev.deviceProfileLib, line 207
    return validPars // library marker kkossev.deviceProfileLib, line 208
} // library marker kkossev.deviceProfileLib, line 209

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 211
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 212
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 213
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 214
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 215
    def scaledValue // library marker kkossev.deviceProfileLib, line 216
    if (value == null) { // library marker kkossev.deviceProfileLib, line 217
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 218
        return null // library marker kkossev.deviceProfileLib, line 219
    } // library marker kkossev.deviceProfileLib, line 220
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 221
        case 'number' : // library marker kkossev.deviceProfileLib, line 222
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 223
            break // library marker kkossev.deviceProfileLib, line 224
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 225
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 226
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 227
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 228
            } // library marker kkossev.deviceProfileLib, line 229
            break // library marker kkossev.deviceProfileLib, line 230
        case 'bool' : // library marker kkossev.deviceProfileLib, line 231
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 232
            break // library marker kkossev.deviceProfileLib, line 233
        case 'enum' : // library marker kkossev.deviceProfileLib, line 234
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 235
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 236
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 237
                return null // library marker kkossev.deviceProfileLib, line 238
            } // library marker kkossev.deviceProfileLib, line 239
            scaledValue = value // library marker kkossev.deviceProfileLib, line 240
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 241
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 242
            } // library marker kkossev.deviceProfileLib, line 243
            break // library marker kkossev.deviceProfileLib, line 244
        default : // library marker kkossev.deviceProfileLib, line 245
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 246
            return null // library marker kkossev.deviceProfileLib, line 247
    } // library marker kkossev.deviceProfileLib, line 248
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 249
    return scaledValue // library marker kkossev.deviceProfileLib, line 250
} // library marker kkossev.deviceProfileLib, line 251

// called from updated() method // library marker kkossev.deviceProfileLib, line 253
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 254
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 255
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 256
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 257
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 258
        return // library marker kkossev.deviceProfileLib, line 259
    } // library marker kkossev.deviceProfileLib, line 260
    //Integer dpInt = 0 // library marker kkossev.deviceProfileLib, line 261
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 262
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 263
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 264
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 265
        Map foundMap // library marker kkossev.deviceProfileLib, line 266
        foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 267
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 268

        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 270
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 271
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 272
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 273
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 274
                // scale the value // library marker kkossev.deviceProfileLib, line 275
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 276
            } // library marker kkossev.deviceProfileLib, line 277
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLib, line 278
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLib, line 279
            } // library marker kkossev.deviceProfileLib, line 280
            else { // library marker kkossev.deviceProfileLib, line 281
                logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" // library marker kkossev.deviceProfileLib, line 282
                return // library marker kkossev.deviceProfileLib, line 283
            } // library marker kkossev.deviceProfileLib, line 284
        } // library marker kkossev.deviceProfileLib, line 285
        else { // library marker kkossev.deviceProfileLib, line 286
            logDebug "warning: couldn't find map for preference ${name}" // library marker kkossev.deviceProfileLib, line 287
            return // library marker kkossev.deviceProfileLib, line 288
        } // library marker kkossev.deviceProfileLib, line 289
    } // library marker kkossev.deviceProfileLib, line 290
    return // library marker kkossev.deviceProfileLib, line 291
} // library marker kkossev.deviceProfileLib, line 292

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 294
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 295
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 296
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 297
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 298
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 299
} // library marker kkossev.deviceProfileLib, line 300
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 301
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 302
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 303
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 304
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 305
} // library marker kkossev.deviceProfileLib, line 306

/** // library marker kkossev.deviceProfileLib, line 308
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 309
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 310
 * // library marker kkossev.deviceProfileLib, line 311
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 312
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 313
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 314
 */ // library marker kkossev.deviceProfileLib, line 315
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 316
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 317
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 318
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 319
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 320
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 321
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 322
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 323
        case 'number' : // library marker kkossev.deviceProfileLib, line 324
            value = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 325
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 326
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 327
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 328
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 329
            } // library marker kkossev.deviceProfileLib, line 330
            else { // library marker kkossev.deviceProfileLib, line 331
                scaledValue = value // library marker kkossev.deviceProfileLib, line 332
            } // library marker kkossev.deviceProfileLib, line 333
            break // library marker kkossev.deviceProfileLib, line 334

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 336
            value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 337
            // scale the value // library marker kkossev.deviceProfileLib, line 338
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 339
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 340
            } // library marker kkossev.deviceProfileLib, line 341
            else { // library marker kkossev.deviceProfileLib, line 342
                scaledValue = value // library marker kkossev.deviceProfileLib, line 343
            } // library marker kkossev.deviceProfileLib, line 344
            break // library marker kkossev.deviceProfileLib, line 345

        case 'bool' : // library marker kkossev.deviceProfileLib, line 347
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 348
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 349
            else { // library marker kkossev.deviceProfileLib, line 350
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 351
                return null // library marker kkossev.deviceProfileLib, line 352
            } // library marker kkossev.deviceProfileLib, line 353
            break // library marker kkossev.deviceProfileLib, line 354
        case 'enum' : // library marker kkossev.deviceProfileLib, line 355
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 356
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 357
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 358
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 359
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 360
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 361
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 362
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 363
            } // library marker kkossev.deviceProfileLib, line 364
            else { // library marker kkossev.deviceProfileLib, line 365
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 366
            } // library marker kkossev.deviceProfileLib, line 367
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 368
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 369
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 370
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 371
                // find the key for the value // library marker kkossev.deviceProfileLib, line 372
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 373
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 374
                if (key == null) { // library marker kkossev.deviceProfileLib, line 375
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 376
                    return null // library marker kkossev.deviceProfileLib, line 377
                } // library marker kkossev.deviceProfileLib, line 378
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 379
            //return null // library marker kkossev.deviceProfileLib, line 380
            } // library marker kkossev.deviceProfileLib, line 381
            break // library marker kkossev.deviceProfileLib, line 382
        default : // library marker kkossev.deviceProfileLib, line 383
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 384
            return null // library marker kkossev.deviceProfileLib, line 385
    } // library marker kkossev.deviceProfileLib, line 386
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 387
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 388
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 389
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 390
        return null // library marker kkossev.deviceProfileLib, line 391
    } // library marker kkossev.deviceProfileLib, line 392
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 393
    return scaledValue // library marker kkossev.deviceProfileLib, line 394
} // library marker kkossev.deviceProfileLib, line 395

/** // library marker kkossev.deviceProfileLib, line 397
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 398
 * // library marker kkossev.deviceProfileLib, line 399
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 400
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 401
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 402
 */ // library marker kkossev.deviceProfileLib, line 403
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 404
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 405
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 406
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 407
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 408
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 409
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 410
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 411
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 412
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 413
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 414
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 415
    if (scaledValue == null) { logInfo "setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 416
    /* // library marker kkossev.deviceProfileLib, line 417
    // update the device setting // TODO: decide whether the setting must be updated here, or after it is echeod back from the device // library marker kkossev.deviceProfileLib, line 418
    try { // library marker kkossev.deviceProfileLib, line 419
        device.updateSetting("$par", [value:val, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 420
    } // library marker kkossev.deviceProfileLib, line 421
    catch (e) { // library marker kkossev.deviceProfileLib, line 422
        logWarn "setPar: Exception '${e}'caught while updateSetting <b>$par</b>(<b>$val</b>) type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 423
        return false // library marker kkossev.deviceProfileLib, line 424
    } // library marker kkossev.deviceProfileLib, line 425
    */ // library marker kkossev.deviceProfileLib, line 426
    //logDebug "setPar: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 427
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 428
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 429
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 430
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 431
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 432
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 433
        try { // library marker kkossev.deviceProfileLib, line 434
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 435
        } // library marker kkossev.deviceProfileLib, line 436
        catch (e) { // library marker kkossev.deviceProfileLib, line 437
            logWarn "setPar: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 438
            return false // library marker kkossev.deviceProfileLib, line 439
        } // library marker kkossev.deviceProfileLib, line 440
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 441
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 442
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 443
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 444
            return false // library marker kkossev.deviceProfileLib, line 445
        } // library marker kkossev.deviceProfileLib, line 446
        else { // library marker kkossev.deviceProfileLib, line 447
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 448
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 449
        } // library marker kkossev.deviceProfileLib, line 450
    } // library marker kkossev.deviceProfileLib, line 451
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 452
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 453
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 454
        def valMiscType // library marker kkossev.deviceProfileLib, line 455
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 456
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 457
            // find the key for the value // library marker kkossev.deviceProfileLib, line 458
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 459
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 460
            if (key == null) { // library marker kkossev.deviceProfileLib, line 461
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 462
                return false // library marker kkossev.deviceProfileLib, line 463
            } // library marker kkossev.deviceProfileLib, line 464
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 465
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 466
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 467
        } // library marker kkossev.deviceProfileLib, line 468
        else { // library marker kkossev.deviceProfileLib, line 469
            valMiscType = val // library marker kkossev.deviceProfileLib, line 470
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 471
        } // library marker kkossev.deviceProfileLib, line 472
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 473
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 474
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 475
        return true // library marker kkossev.deviceProfileLib, line 476
    } // library marker kkossev.deviceProfileLib, line 477

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 479
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 480

    try { // library marker kkossev.deviceProfileLib, line 482
        // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 483
        /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 484
        isTuyaDP = dpMap.dp instanceof Number // library marker kkossev.deviceProfileLib, line 485
    } // library marker kkossev.deviceProfileLib, line 486
    catch (e) { // library marker kkossev.deviceProfileLib, line 487
        logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" // library marker kkossev.deviceProfileLib, line 488
        isTuyaDP = false // library marker kkossev.deviceProfileLib, line 489
    //return false // library marker kkossev.deviceProfileLib, line 490
    } // library marker kkossev.deviceProfileLib, line 491
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 492
        // Tuya DP // library marker kkossev.deviceProfileLib, line 493
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 494
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 495
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 496
            return false // library marker kkossev.deviceProfileLib, line 497
        } // library marker kkossev.deviceProfileLib, line 498
        else { // library marker kkossev.deviceProfileLib, line 499
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 500
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 501
            return false // library marker kkossev.deviceProfileLib, line 502
        } // library marker kkossev.deviceProfileLib, line 503
    } // library marker kkossev.deviceProfileLib, line 504
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 505
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 506
        int cluster // library marker kkossev.deviceProfileLib, line 507
        int attribute // library marker kkossev.deviceProfileLib, line 508
        int dt // library marker kkossev.deviceProfileLib, line 509
        String mfgCodeString = '' // library marker kkossev.deviceProfileLib, line 510
        try { // library marker kkossev.deviceProfileLib, line 511
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[0]) // library marker kkossev.deviceProfileLib, line 512
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 513
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[1]) // library marker kkossev.deviceProfileLib, line 514
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 515
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 516
            //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 517
            mfgCodeString = dpMap.mfgCode // library marker kkossev.deviceProfileLib, line 518
        //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 519
        } // library marker kkossev.deviceProfileLib, line 520
        catch (e) { // library marker kkossev.deviceProfileLib, line 521
            logWarn "setPar: Exception '${e}' caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 522
            return false // library marker kkossev.deviceProfileLib, line 523
        } // library marker kkossev.deviceProfileLib, line 524
        Map mapMfCode = ['mfgCode':mfgCodeString] // library marker kkossev.deviceProfileLib, line 525
        logDebug "setPar: found cluster=0x${zigbee.convertToHexString(cluster, 2)} attribute=0x${zigbee.convertToHexString(attribute, 2)} dt=${dpMap.dt} mfgCodeString=${mfgCodeString} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 526
        if (mfgCodeString != null && mfgCodeString != '') { // library marker kkossev.deviceProfileLib, line 527
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay = 200) // library marker kkossev.deviceProfileLib, line 528
        } // library marker kkossev.deviceProfileLib, line 529
        else { // library marker kkossev.deviceProfileLib, line 530
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 531
        } // library marker kkossev.deviceProfileLib, line 532
    } // library marker kkossev.deviceProfileLib, line 533
    else { // library marker kkossev.deviceProfileLib, line 534
        logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 535
        return false // library marker kkossev.deviceProfileLib, line 536
    } // library marker kkossev.deviceProfileLib, line 537
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 538
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 539
    return true // library marker kkossev.deviceProfileLib, line 540
} // library marker kkossev.deviceProfileLib, line 541

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 543
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 544
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 545
List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 546
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 547
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 548
    if (dpMap == null) { // library marker kkossev.deviceProfileLib, line 549
        logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 550
        return [] // library marker kkossev.deviceProfileLib, line 551
    } // library marker kkossev.deviceProfileLib, line 552
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 553
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 554
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 555
        return [] // library marker kkossev.deviceProfileLib, line 556
    } // library marker kkossev.deviceProfileLib, line 557
    String dpType // library marker kkossev.deviceProfileLib, line 558
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 559
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 560
    } // library marker kkossev.deviceProfileLib, line 561
    else { // library marker kkossev.deviceProfileLib, line 562
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 563
    } // library marker kkossev.deviceProfileLib, line 564
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 565
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 566
        return [] // library marker kkossev.deviceProfileLib, line 567
    } // library marker kkossev.deviceProfileLib, line 568
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 569
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 570
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 571
    cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 572
    return cmds // library marker kkossev.deviceProfileLib, line 573
} // library marker kkossev.deviceProfileLib, line 574

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 576
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 577
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 578
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 579
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 580
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 581

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 583
    if (dpMap == null || dpMap.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 584
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 585
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 586
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 587
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 588
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 589
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 590
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 591
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 592
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 593
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 594
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 595
        try { // library marker kkossev.deviceProfileLib, line 596
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 597
        } // library marker kkossev.deviceProfileLib, line 598
        catch (e) { // library marker kkossev.deviceProfileLib, line 599
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 600
            return false // library marker kkossev.deviceProfileLib, line 601
        } // library marker kkossev.deviceProfileLib, line 602
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 603
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 604
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 605
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 606
            return true // library marker kkossev.deviceProfileLib, line 607
        } // library marker kkossev.deviceProfileLib, line 608
        else { // library marker kkossev.deviceProfileLib, line 609
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 610
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 611
        } // library marker kkossev.deviceProfileLib, line 612
    } // library marker kkossev.deviceProfileLib, line 613
    else { // library marker kkossev.deviceProfileLib, line 614
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 615
    } // library marker kkossev.deviceProfileLib, line 616
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 617
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 618
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 619
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 620
        // patch !! // library marker kkossev.deviceProfileLib, line 621
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 622
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 623
        } // library marker kkossev.deviceProfileLib, line 624
        else { // library marker kkossev.deviceProfileLib, line 625
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 626
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 627
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 628
        } // library marker kkossev.deviceProfileLib, line 629
        return true // library marker kkossev.deviceProfileLib, line 630
    } // library marker kkossev.deviceProfileLib, line 631
    else { // library marker kkossev.deviceProfileLib, line 632
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 633
    } // library marker kkossev.deviceProfileLib, line 634
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 635
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 636
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 637
    try { // library marker kkossev.deviceProfileLib, line 638
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 639
    } // library marker kkossev.deviceProfileLib, line 640
    catch (e) { // library marker kkossev.deviceProfileLib, line 641
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 642
        return false // library marker kkossev.deviceProfileLib, line 643
    } // library marker kkossev.deviceProfileLib, line 644
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 645
        // Tuya DP // library marker kkossev.deviceProfileLib, line 646
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 647
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 648
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 649
            return false // library marker kkossev.deviceProfileLib, line 650
        } // library marker kkossev.deviceProfileLib, line 651
        else { // library marker kkossev.deviceProfileLib, line 652
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 653
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 654
            return true // library marker kkossev.deviceProfileLib, line 655
        } // library marker kkossev.deviceProfileLib, line 656
    } // library marker kkossev.deviceProfileLib, line 657
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 658
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 659
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 660
    } // library marker kkossev.deviceProfileLib, line 661
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 662
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 663
        int cluster // library marker kkossev.deviceProfileLib, line 664
        int attribute // library marker kkossev.deviceProfileLib, line 665
        int dt // library marker kkossev.deviceProfileLib, line 666
        // int mfgCode // library marker kkossev.deviceProfileLib, line 667
        try { // library marker kkossev.deviceProfileLib, line 668
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[0]) // library marker kkossev.deviceProfileLib, line 669
            //log.trace "cluster = ${cluster}" // library marker kkossev.deviceProfileLib, line 670
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(':')[1]) // library marker kkossev.deviceProfileLib, line 671
            //log.trace "attribute = ${attribute}" // library marker kkossev.deviceProfileLib, line 672
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null // library marker kkossev.deviceProfileLib, line 673
        //log.trace "dt = ${dt}" // library marker kkossev.deviceProfileLib, line 674
        //log.trace "mfgCode = ${dpMap.mfgCode}" // library marker kkossev.deviceProfileLib, line 675
        //  mfgCode = dpMap.mfgCode != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.mfgCode) : null // library marker kkossev.deviceProfileLib, line 676
        //  log.trace "mfgCode = ${mfgCode}" // library marker kkossev.deviceProfileLib, line 677
        } // library marker kkossev.deviceProfileLib, line 678
        catch (e) { // library marker kkossev.deviceProfileLib, line 679
            logWarn "sendAttribute: Exception '${e}'caught while splitting cluster and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 680
            return false // library marker kkossev.deviceProfileLib, line 681
        } // library marker kkossev.deviceProfileLib, line 682

        logDebug "sendAttribute: found cluster=${cluster} attribute=${attribute} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 684
        if (dpMap.mfgCode != null) { // library marker kkossev.deviceProfileLib, line 685
            Map mapMfCode = ['mfgCode':dpMap.mfgCode] // library marker kkossev.deviceProfileLib, line 686
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay = 200) // library marker kkossev.deviceProfileLib, line 687
        } // library marker kkossev.deviceProfileLib, line 688
        else { // library marker kkossev.deviceProfileLib, line 689
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 690
        } // library marker kkossev.deviceProfileLib, line 691
    } // library marker kkossev.deviceProfileLib, line 692
    else { // library marker kkossev.deviceProfileLib, line 693
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 694
        return false // library marker kkossev.deviceProfileLib, line 695
    } // library marker kkossev.deviceProfileLib, line 696
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 697
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 698
    return true // library marker kkossev.deviceProfileLib, line 699
} // library marker kkossev.deviceProfileLib, line 700

/** // library marker kkossev.deviceProfileLib, line 702
 * Sends a command to the device. // library marker kkossev.deviceProfileLib, line 703
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 704
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 705
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 706
 */ // library marker kkossev.deviceProfileLib, line 707
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 708
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 709
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 710
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 711
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 712
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 713
    if (supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 714
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 715
        return false // library marker kkossev.deviceProfileLib, line 716
    } // library marker kkossev.deviceProfileLib, line 717
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 718
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 719
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 720
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 721
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 722
        return false // library marker kkossev.deviceProfileLib, line 723
    } // library marker kkossev.deviceProfileLib, line 724
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 725
    def func // library marker kkossev.deviceProfileLib, line 726
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 727
    def funcResult // library marker kkossev.deviceProfileLib, line 728
    try { // library marker kkossev.deviceProfileLib, line 729
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 730
        if (val != null) { // library marker kkossev.deviceProfileLib, line 731
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 732
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 733
        } // library marker kkossev.deviceProfileLib, line 734
        else { // library marker kkossev.deviceProfileLib, line 735
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 736
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 737
        } // library marker kkossev.deviceProfileLib, line 738
    } // library marker kkossev.deviceProfileLib, line 739
    catch (e) { // library marker kkossev.deviceProfileLib, line 740
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 741
        return false // library marker kkossev.deviceProfileLib, line 742
    } // library marker kkossev.deviceProfileLib, line 743
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 744
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 745
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 746
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 747
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 748
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 749
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 750
        } // library marker kkossev.deviceProfileLib, line 751
    } else { // library marker kkossev.deviceProfileLib, line 752
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 753
        return false // library marker kkossev.deviceProfileLib, line 754
    } // library marker kkossev.deviceProfileLib, line 755
    cmds = funcResult // library marker kkossev.deviceProfileLib, line 756
    if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 757
        sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 758
    } // library marker kkossev.deviceProfileLib, line 759
    return true // library marker kkossev.deviceProfileLib, line 760
} // library marker kkossev.deviceProfileLib, line 761

/** // library marker kkossev.deviceProfileLib, line 763
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 764
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 765
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 766
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 767
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 768
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 769
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 770
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 771
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 772
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 773
 */ // library marker kkossev.deviceProfileLib, line 774
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 775
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 776
    Map input = [:] // library marker kkossev.deviceProfileLib, line 777
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 778
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 779
        if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 780
        return [:] // library marker kkossev.deviceProfileLib, line 781
    } // library marker kkossev.deviceProfileLib, line 782
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 783
    def preference // library marker kkossev.deviceProfileLib, line 784
    try { // library marker kkossev.deviceProfileLib, line 785
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 786
    } // library marker kkossev.deviceProfileLib, line 787
    catch (e) { // library marker kkossev.deviceProfileLib, line 788
        if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 789
        return [:] // library marker kkossev.deviceProfileLib, line 790
    } // library marker kkossev.deviceProfileLib, line 791
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 792
    try { // library marker kkossev.deviceProfileLib, line 793
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 794
            if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } // library marker kkossev.deviceProfileLib, line 795
            return [:] // library marker kkossev.deviceProfileLib, line 796
        } // library marker kkossev.deviceProfileLib, line 797
    } // library marker kkossev.deviceProfileLib, line 798
    catch (e) { // library marker kkossev.deviceProfileLib, line 799
        if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 800
        return [:] // library marker kkossev.deviceProfileLib, line 801
    } // library marker kkossev.deviceProfileLib, line 802

    try { // library marker kkossev.deviceProfileLib, line 804
        isTuyaDP = preference.isNumber() // library marker kkossev.deviceProfileLib, line 805
    } // library marker kkossev.deviceProfileLib, line 806
    catch (e) { // library marker kkossev.deviceProfileLib, line 807
        if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 808
        return [:] // library marker kkossev.deviceProfileLib, line 809
    } // library marker kkossev.deviceProfileLib, line 810

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 812
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 813
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 814
    if (foundMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 815
        if (debug) { log.warn "inputIt: map not found for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 816
        return [:] // library marker kkossev.deviceProfileLib, line 817
    } // library marker kkossev.deviceProfileLib, line 818
    if (foundMap.rw != 'rw') { // library marker kkossev.deviceProfileLib, line 819
        if (debug) { log.warn "inputIt: param '${param}' is read only!" } // library marker kkossev.deviceProfileLib, line 820
        return [:] // library marker kkossev.deviceProfileLib, line 821
    } // library marker kkossev.deviceProfileLib, line 822
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 823
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 824
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 825
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 826
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 827
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 828
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 829
        } // library marker kkossev.deviceProfileLib, line 830
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 831
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 832
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 833
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 834
            } // library marker kkossev.deviceProfileLib, line 835
        } // library marker kkossev.deviceProfileLib, line 836
    } // library marker kkossev.deviceProfileLib, line 837
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 838
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 839
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 840
    }/* // library marker kkossev.deviceProfileLib, line 841
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 842
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 843
    }*/ // library marker kkossev.deviceProfileLib, line 844
    else { // library marker kkossev.deviceProfileLib, line 845
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 846
        return [:] // library marker kkossev.deviceProfileLib, line 847
    } // library marker kkossev.deviceProfileLib, line 848
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 849
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 850
    } // library marker kkossev.deviceProfileLib, line 851
    return input // library marker kkossev.deviceProfileLib, line 852
} // library marker kkossev.deviceProfileLib, line 853

/** // library marker kkossev.deviceProfileLib, line 855
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 856
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 857
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 858
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 859
 */ // library marker kkossev.deviceProfileLib, line 860
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 861
    String deviceName         = UNKNOWN // library marker kkossev.deviceProfileLib, line 862
    String deviceProfile      = UNKNOWN // library marker kkossev.deviceProfileLib, line 863
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 864
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 865
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 866
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 867
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 868
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 869
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 870
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 871
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 872
            } // library marker kkossev.deviceProfileLib, line 873
        } // library marker kkossev.deviceProfileLib, line 874
    } // library marker kkossev.deviceProfileLib, line 875
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 876
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 877
    } // library marker kkossev.deviceProfileLib, line 878
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 879
} // library marker kkossev.deviceProfileLib, line 880

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 882
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 883
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 884
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 885
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 886
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 887
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 888
    } // library marker kkossev.deviceProfileLib, line 889
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 890
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 891
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 892
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 893
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 894
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 895
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 896
    } else { // library marker kkossev.deviceProfileLib, line 897
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 898
    } // library marker kkossev.deviceProfileLib, line 899
} // library marker kkossev.deviceProfileLib, line 900

// TODO! // library marker kkossev.deviceProfileLib, line 902
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 903
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 904
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 905
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 906
    return cmds // library marker kkossev.deviceProfileLib, line 907
} // library marker kkossev.deviceProfileLib, line 908

// TODO ! // library marker kkossev.deviceProfileLib, line 910
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 911
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 912
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 913
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.deviceProfileLib, line 914
    return cmds // library marker kkossev.deviceProfileLib, line 915
} // library marker kkossev.deviceProfileLib, line 916

// TODO // library marker kkossev.deviceProfileLib, line 918
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 919
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 920
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 921
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 922
    return cmds // library marker kkossev.deviceProfileLib, line 923
} // library marker kkossev.deviceProfileLib, line 924

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 926
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 927
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 928
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 929
    } // library marker kkossev.deviceProfileLib, line 930
} // library marker kkossev.deviceProfileLib, line 931

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 933
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 934
} // library marker kkossev.deviceProfileLib, line 935

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 937

// // library marker kkossev.deviceProfileLib, line 939
// called from parse() // library marker kkossev.deviceProfileLib, line 940
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 941
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 942
// // library marker kkossev.deviceProfileLib, line 943
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 944
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 945
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 946
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 947
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 948
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 949
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 950
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 951
} // library marker kkossev.deviceProfileLib, line 952

// // library marker kkossev.deviceProfileLib, line 954
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 955
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 956
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 957
// // library marker kkossev.deviceProfileLib, line 958
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 959
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 960
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 961
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 962
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 963
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 964
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 965
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 966
} // library marker kkossev.deviceProfileLib, line 967

// all DPs are spammy - sent periodically! // library marker kkossev.deviceProfileLib, line 969
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 970
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 971
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 972
    return isSpammy // library marker kkossev.deviceProfileLib, line 973
} // library marker kkossev.deviceProfileLib, line 974

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 976
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 977
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 978
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 979
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 980
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 981
    } // library marker kkossev.deviceProfileLib, line 982
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 983
} // library marker kkossev.deviceProfileLib, line 984

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 986
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 987
    boolean isEqual // library marker kkossev.deviceProfileLib, line 988
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 989
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 990
    } // library marker kkossev.deviceProfileLib, line 991
    else { // library marker kkossev.deviceProfileLib, line 992
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 993
    } // library marker kkossev.deviceProfileLib, line 994
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 995
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 996
} // library marker kkossev.deviceProfileLib, line 997

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 999
    Double convertedValue // library marker kkossev.deviceProfileLib, line 1000
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1001
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 1002
    } // library marker kkossev.deviceProfileLib, line 1003
    else { // library marker kkossev.deviceProfileLib, line 1004
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1005
    } // library marker kkossev.deviceProfileLib, line 1006
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1007
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1008
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1009
} // library marker kkossev.deviceProfileLib, line 1010

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1012
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1013
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1014
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1015
    def convertedValue // library marker kkossev.deviceProfileLib, line 1016
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1017
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1018
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1019
    } // library marker kkossev.deviceProfileLib, line 1020
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1021
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1022
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1023
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1024
            isEqual = false // library marker kkossev.deviceProfileLib, line 1025
        } // library marker kkossev.deviceProfileLib, line 1026
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1027
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1028
        } // library marker kkossev.deviceProfileLib, line 1029
    } // library marker kkossev.deviceProfileLib, line 1030
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1031
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1032
} // library marker kkossev.deviceProfileLib, line 1033

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1035
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1036
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1037
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1038
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1039
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1040
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1041
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1042
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1043
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1044
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1045
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1046
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1047
            break // library marker kkossev.deviceProfileLib, line 1048
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1049
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1050
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1051
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1052
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1053
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1054
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1055
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1056
            } // library marker kkossev.deviceProfileLib, line 1057
            else { // library marker kkossev.deviceProfileLib, line 1058
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1059
            } // library marker kkossev.deviceProfileLib, line 1060
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1061
            break // library marker kkossev.deviceProfileLib, line 1062
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1063
        case 'number' : // library marker kkossev.deviceProfileLib, line 1064
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1065
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1066
            break // library marker kkossev.deviceProfileLib, line 1067
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1068
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1069
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1070
            break // library marker kkossev.deviceProfileLib, line 1071
        default : // library marker kkossev.deviceProfileLib, line 1072
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1073
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1074
    } // library marker kkossev.deviceProfileLib, line 1075
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1076
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1077
    } // library marker kkossev.deviceProfileLib, line 1078
    // // library marker kkossev.deviceProfileLib, line 1079
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1080
} // library marker kkossev.deviceProfileLib, line 1081

// // library marker kkossev.deviceProfileLib, line 1083
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1084
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1085
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1086
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1087
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1088
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1089
// // library marker kkossev.deviceProfileLib, line 1090
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1091
// // library marker kkossev.deviceProfileLib, line 1092
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1093
// // library marker kkossev.deviceProfileLib, line 1094
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1095
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1096
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1097
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1098
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1099
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1100
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1101
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1102
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1103
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1104
            break // library marker kkossev.deviceProfileLib, line 1105
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1106
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1107
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1108
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1109
            logTrace "latestEvent is dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1110
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1111
            if (dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1112
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1113
            } // library marker kkossev.deviceProfileLib, line 1114
            else { // library marker kkossev.deviceProfileLib, line 1115
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1116
            } // library marker kkossev.deviceProfileLib, line 1117
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1118
            break // library marker kkossev.deviceProfileLib, line 1119
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1120
        case 'number' : // library marker kkossev.deviceProfileLib, line 1121
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1122
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1123
            break // library marker kkossev.deviceProfileLib, line 1124
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1125
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1126
            break // library marker kkossev.deviceProfileLib, line 1127
        default : // library marker kkossev.deviceProfileLib, line 1128
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1129
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1130
    } // library marker kkossev.deviceProfileLib, line 1131
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1132
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1133
} // library marker kkossev.deviceProfileLib, line 1134

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1136
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1137
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1138
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1139
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1140
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1141
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1142
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1143
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1144
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1145
    } // library marker kkossev.deviceProfileLib, line 1146
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1147
    try { // library marker kkossev.deviceProfileLib, line 1148
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1149
    } // library marker kkossev.deviceProfileLib, line 1150
    catch (e) { // library marker kkossev.deviceProfileLib, line 1151
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1152
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1153
    } // library marker kkossev.deviceProfileLib, line 1154
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1155
    return fncmd // library marker kkossev.deviceProfileLib, line 1156
} // library marker kkossev.deviceProfileLib, line 1157

/** // library marker kkossev.deviceProfileLib, line 1159
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1160
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1161
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1162
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1163
 * // library marker kkossev.deviceProfileLib, line 1164
 * @param descMap The description map of the received DP. // library marker kkossev.deviceProfileLib, line 1165
 * @param dp The value of the received DP. // library marker kkossev.deviceProfileLib, line 1166
 * @param dp_id The ID of the received DP. // library marker kkossev.deviceProfileLib, line 1167
 * @param fncmd The command of the received DP. // library marker kkossev.deviceProfileLib, line 1168
 * @param dp_len The length of the received DP. // library marker kkossev.deviceProfileLib, line 1169
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1170
 */ // library marker kkossev.deviceProfileLib, line 1171
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1172
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1173
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1174
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1175
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1176

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1178
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1179

    Map foundItem = null // library marker kkossev.deviceProfileLib, line 1181
    tuyaDPsMap.each { item -> // library marker kkossev.deviceProfileLib, line 1182
        if (item['dp'] == (dp as int)) { // library marker kkossev.deviceProfileLib, line 1183
            foundItem = item // library marker kkossev.deviceProfileLib, line 1184
            return // library marker kkossev.deviceProfileLib, line 1185
        } // library marker kkossev.deviceProfileLib, line 1186
    } // library marker kkossev.deviceProfileLib, line 1187
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1188
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1189
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1190
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1191
        return false // library marker kkossev.deviceProfileLib, line 1192
    } // library marker kkossev.deviceProfileLib, line 1193
    return processFoundItem(foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1194
} // library marker kkossev.deviceProfileLib, line 1195

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1197
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1198
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1199
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1200
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1201

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1203
    if (attribMap == null || attribMap.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1204

    Map foundItem = null // library marker kkossev.deviceProfileLib, line 1206
    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1207
    int value // library marker kkossev.deviceProfileLib, line 1208
    try { // library marker kkossev.deviceProfileLib, line 1209
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1210
    } // library marker kkossev.deviceProfileLib, line 1211
    catch (e) { // library marker kkossev.deviceProfileLib, line 1212
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1213
        return false // library marker kkossev.deviceProfileLib, line 1214
    } // library marker kkossev.deviceProfileLib, line 1215
    //logTrace "clusterAttribute = ${clusterAttribute}" // library marker kkossev.deviceProfileLib, line 1216
    attribMap.each { item -> // library marker kkossev.deviceProfileLib, line 1217
        if (item['at'] == clusterAttribute) { // library marker kkossev.deviceProfileLib, line 1218
            foundItem = item // library marker kkossev.deviceProfileLib, line 1219
            return // library marker kkossev.deviceProfileLib, line 1220
        } // library marker kkossev.deviceProfileLib, line 1221
    } // library marker kkossev.deviceProfileLib, line 1222
    if (foundItem == null) { // library marker kkossev.deviceProfileLib, line 1223
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1224
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1225
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1226
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1227
        return false // library marker kkossev.deviceProfileLib, line 1228
    } // library marker kkossev.deviceProfileLib, line 1229
    return processFoundItem(foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1230
} // library marker kkossev.deviceProfileLib, line 1231

// modifies the value of the foundItem if needed !!! // library marker kkossev.deviceProfileLib, line 1233
/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.deviceProfileLib, line 1234
boolean processFoundItem(final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1235
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1236
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1237
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1238
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1239
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1240
        if (preProcValue == null) { // library marker kkossev.deviceProfileLib, line 1241
            logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" // library marker kkossev.deviceProfileLib, line 1242
            return true // library marker kkossev.deviceProfileLib, line 1243
        } // library marker kkossev.deviceProfileLib, line 1244
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1245
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1246
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1247
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1248
        } // library marker kkossev.deviceProfileLib, line 1249
    } // library marker kkossev.deviceProfileLib, line 1250
    else { // library marker kkossev.deviceProfileLib, line 1251
        logTrace "processFoundItem: no preProc for ${foundItem.name}" // library marker kkossev.deviceProfileLib, line 1252
    } // library marker kkossev.deviceProfileLib, line 1253

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1255
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1256
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1257
    //existingPrefValue = existingPrefValue?.replace("[", "").replace("]", "")               // preference name as in Hubitat settings (preferences), if already created. // library marker kkossev.deviceProfileLib, line 1258
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1259
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1260
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1261
    //boolean preferenceExists = settings.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1262
    boolean preferenceExists = DEVICE?.preferences?.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1263
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1264
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1265
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1266
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1267
    if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1268
        logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" // library marker kkossev.deviceProfileLib, line 1269
    } // library marker kkossev.deviceProfileLib, line 1270
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1271
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1272
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1273
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1274
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1275
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1276

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1278
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1279
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1280
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1281
            // TODO - scaledValue ????? // library marker kkossev.deviceProfileLib, line 1282
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1283
            if (settings.logEnable) { logInfo "${descText }" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1284
        } // library marker kkossev.deviceProfileLib, line 1285
        // no more processing is needed, as this clusterAttribute is not a preference and not an attribute // library marker kkossev.deviceProfileLib, line 1286
        return true // library marker kkossev.deviceProfileLib, line 1287
    } // library marker kkossev.deviceProfileLib, line 1288

    // first, check if there is a preference defined to be updated // library marker kkossev.deviceProfileLib, line 1290
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1291
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1292
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1293
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1294
        if (isEqual == true) { // library marker kkossev.deviceProfileLib, line 1295
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1296
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1297
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1298
            } // library marker kkossev.deviceProfileLib, line 1299
        } // library marker kkossev.deviceProfileLib, line 1300
        else { // library marker kkossev.deviceProfileLib, line 1301
            String scaledPreferenceValue = preferenceValue      //.toString() is not neccessary // library marker kkossev.deviceProfileLib, line 1302
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1303
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1304
            } // library marker kkossev.deviceProfileLib, line 1305
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1306
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1307
            try { // library marker kkossev.deviceProfileLib, line 1308
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1309
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1310
            } // library marker kkossev.deviceProfileLib, line 1311
            catch (e) { // library marker kkossev.deviceProfileLib, line 1312
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1313
            } // library marker kkossev.deviceProfileLib, line 1314
        } // library marker kkossev.deviceProfileLib, line 1315
    } // library marker kkossev.deviceProfileLib, line 1316
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1317
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1318
        unitText = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1319
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1320
    } // library marker kkossev.deviceProfileLib, line 1321

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1323
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1324
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1325
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1326
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1327
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1328
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1329
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1330
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1331
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1332
            } // library marker kkossev.deviceProfileLib, line 1333
            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1334
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1335
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1336
            // continue ... // library marker kkossev.deviceProfileLib, line 1337
            } // library marker kkossev.deviceProfileLib, line 1338
            else { // library marker kkossev.deviceProfileLib, line 1339
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1340
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1341
                } // library marker kkossev.deviceProfileLib, line 1342
                else { // library marker kkossev.deviceProfileLib, line 1343
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1344
                } // library marker kkossev.deviceProfileLib, line 1345
            } // library marker kkossev.deviceProfileLib, line 1346
        } // library marker kkossev.deviceProfileLib, line 1347

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an event! // library marker kkossev.deviceProfileLib, line 1349
        //log.trace 'sending event' // library marker kkossev.deviceProfileLib, line 1350
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1351
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1352
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1353
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1354
        if (DEVICE_TYPE in ['Thermostat'])  { processDeviceEventThermostat(name, valueScaled, unitText, descText) } // library marker kkossev.deviceProfileLib, line 1355
        else { // library marker kkossev.deviceProfileLib, line 1356
            switch (name) { // library marker kkossev.deviceProfileLib, line 1357
                case 'motion' : // library marker kkossev.deviceProfileLib, line 1358
                    handleMotion(value as boolean)  // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1359
                    break // library marker kkossev.deviceProfileLib, line 1360
                case 'temperature' : // library marker kkossev.deviceProfileLib, line 1361
                    //temperatureEvent(value / getTemperatureDiv()) // library marker kkossev.deviceProfileLib, line 1362
                    handleTemperatureEvent(valueScaled as Float) // library marker kkossev.deviceProfileLib, line 1363
                    break // library marker kkossev.deviceProfileLib, line 1364
                case 'humidity' : // library marker kkossev.deviceProfileLib, line 1365
                    handleHumidityEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1366
                    break // library marker kkossev.deviceProfileLib, line 1367
                case 'illuminance' : // library marker kkossev.deviceProfileLib, line 1368
                case 'illuminance_lux' :    // ignore the IAS Zone illuminance reports for HL0SS9OA and 2AAELWXK // library marker kkossev.deviceProfileLib, line 1369
                    //log.trace "illuminance event received deviceProfile is ${getDeviceProfile()} value=${value} valueScaled=${valueScaled} valueCorrected=${valueCorrected}" // library marker kkossev.deviceProfileLib, line 1370
                    handleIlluminanceEvent(valueCorrected as int) // library marker kkossev.deviceProfileLib, line 1371
                    break // library marker kkossev.deviceProfileLib, line 1372
                case 'pushed' : // library marker kkossev.deviceProfileLib, line 1373
                    logDebug "button event received value=${value} valueScaled=${valueScaled} valueCorrected=${valueCorrected}" // library marker kkossev.deviceProfileLib, line 1374
                    buttonEvent(valueScaled) // library marker kkossev.deviceProfileLib, line 1375
                    break // library marker kkossev.deviceProfileLib, line 1376
                default : // library marker kkossev.deviceProfileLib, line 1377
                    sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1378
                    if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1379
                        logTrace "event ${name} sent w/ value ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1380
                        logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1381
                    } // library marker kkossev.deviceProfileLib, line 1382
                    break // library marker kkossev.deviceProfileLib, line 1383
            } // library marker kkossev.deviceProfileLib, line 1384
        //logTrace "attrValue=${attrValue} valueScaled=${valueScaled} equal=${isEqual}" // library marker kkossev.deviceProfileLib, line 1385
        } // library marker kkossev.deviceProfileLib, line 1386
    } // library marker kkossev.deviceProfileLib, line 1387
    // all processing was done here! // library marker kkossev.deviceProfileLib, line 1388
    return true // library marker kkossev.deviceProfileLib, line 1389
} // library marker kkossev.deviceProfileLib, line 1390

public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1392
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1393
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 1394
        logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1395
        return false // library marker kkossev.deviceProfileLib, line 1396
    } // library marker kkossev.deviceProfileLib, line 1397
    int validationFailures = 0 // library marker kkossev.deviceProfileLib, line 1398
    int validationFixes = 0 // library marker kkossev.deviceProfileLib, line 1399
    int total = 0 // library marker kkossev.deviceProfileLib, line 1400
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1401
    def oldSettingValue // library marker kkossev.deviceProfileLib, line 1402
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1403
    def newValue // library marker kkossev.deviceProfileLib, line 1404
    String settingType // library marker kkossev.deviceProfileLib, line 1405
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1406
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1407
        if (foundMap == null || foundMap == [:]) { // library marker kkossev.deviceProfileLib, line 1408
            logDebug "validateAndFixPreferences: map not found for preference ${it.key}"    // 10/21/2023 - sevirity lowered to debug // library marker kkossev.deviceProfileLib, line 1409
            return false // library marker kkossev.deviceProfileLib, line 1410
        } // library marker kkossev.deviceProfileLib, line 1411
        settingType = device.getSettingType(it.key) // library marker kkossev.deviceProfileLib, line 1412
        oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1413
        if (settingType == null) { // library marker kkossev.deviceProfileLib, line 1414
            logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" // library marker kkossev.deviceProfileLib, line 1415
            return false // library marker kkossev.deviceProfileLib, line 1416
        } // library marker kkossev.deviceProfileLib, line 1417
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1418
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1419
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1420
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1421
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1422
            try { // library marker kkossev.deviceProfileLib, line 1423
                device.removeSetting(it.key) // library marker kkossev.deviceProfileLib, line 1424
                logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1425
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1426
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1427
                return false // library marker kkossev.deviceProfileLib, line 1428
            } // library marker kkossev.deviceProfileLib, line 1429
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1430
            try { // library marker kkossev.deviceProfileLib, line 1431
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1432
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1433
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1434
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1435
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1436
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1437
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1438
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1439
                    } // library marker kkossev.deviceProfileLib, line 1440
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1441
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1442
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1443
                    } else { // library marker kkossev.deviceProfileLib, line 1444
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1445
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1446
                    } // library marker kkossev.deviceProfileLib, line 1447
                } // library marker kkossev.deviceProfileLib, line 1448
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1449
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1450
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1451
            } // library marker kkossev.deviceProfileLib, line 1452
            catch (e) { // library marker kkossev.deviceProfileLib, line 1453
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1454
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1455
                try { // library marker kkossev.deviceProfileLib, line 1456
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1457
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1458
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1459
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1460
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1461
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" // library marker kkossev.deviceProfileLib, line 1462
                    return false // library marker kkossev.deviceProfileLib, line 1463
                } // library marker kkossev.deviceProfileLib, line 1464
            } // library marker kkossev.deviceProfileLib, line 1465
        } // library marker kkossev.deviceProfileLib, line 1466
        total ++ // library marker kkossev.deviceProfileLib, line 1467
    } // library marker kkossev.deviceProfileLib, line 1468
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1469
    return true // library marker kkossev.deviceProfileLib, line 1470
} // library marker kkossev.deviceProfileLib, line 1471

public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1473
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1474
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1475
            logInfo "${fingerprint}" // library marker kkossev.deviceProfileLib, line 1476
        } // library marker kkossev.deviceProfileLib, line 1477
    } // library marker kkossev.deviceProfileLib, line 1478
} // library marker kkossev.deviceProfileLib, line 1479

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~

// ~~~~~ start include (168) kkossev.illuminanceLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.illuminanceLib, line 1
library( // library marker kkossev.illuminanceLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Illuminance Library', name: 'illuminanceLib', namespace: 'kkossev', // library marker kkossev.illuminanceLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/illuminanceLib.groovy', documentationLink: '', // library marker kkossev.illuminanceLib, line 4
    version: '3.2.0' // library marker kkossev.illuminanceLib, line 5

) // library marker kkossev.illuminanceLib, line 7
/* // library marker kkossev.illuminanceLib, line 8
 *  Zigbee Illuminance Library // library marker kkossev.illuminanceLib, line 9
 * // library marker kkossev.illuminanceLib, line 10
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.illuminanceLib, line 11
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.illuminanceLib, line 12
 * // library marker kkossev.illuminanceLib, line 13
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.illuminanceLib, line 14
 * // library marker kkossev.illuminanceLib, line 15
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.illuminanceLib, line 16
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.illuminanceLib, line 17
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.illuminanceLib, line 18
 * // library marker kkossev.illuminanceLib, line 19
 * ver. 3.0.0  2024-04-06 kkossev  - added illuminanceLib.groovy // library marker kkossev.illuminanceLib, line 20
 * ver. 3.2.0  2024-05-21 kkossev  - commonLib 3.2.0 allignment; // library marker kkossev.illuminanceLib, line 21
 * // library marker kkossev.illuminanceLib, line 22
 *                                   TODO: illum threshold not working! // library marker kkossev.illuminanceLib, line 23
 *                                   TODO: check illuminanceInitializeVars() and illuminanceProcessTuyaDP() usage // library marker kkossev.illuminanceLib, line 24
*/ // library marker kkossev.illuminanceLib, line 25

static String illuminanceLibVersion()   { '3.2.0' } // library marker kkossev.illuminanceLib, line 27
static String illuminanceLibStamp() { '2024/05/21 9:03 PM' } // library marker kkossev.illuminanceLib, line 28

metadata { // library marker kkossev.illuminanceLib, line 30
    // no capabilities // library marker kkossev.illuminanceLib, line 31
    // no attributes // library marker kkossev.illuminanceLib, line 32
    // no commands // library marker kkossev.illuminanceLib, line 33
    preferences { // library marker kkossev.illuminanceLib, line 34
        // no prefrences // library marker kkossev.illuminanceLib, line 35
    } // library marker kkossev.illuminanceLib, line 36
} // library marker kkossev.illuminanceLib, line 37

@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 10 // library marker kkossev.illuminanceLib, line 39

void standardParseIlluminanceCluster(final Map descMap) { // library marker kkossev.illuminanceLib, line 41
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.illuminanceLib, line 42
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.illuminanceLib, line 43
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.illuminanceLib, line 44
    handleIlluminanceEvent(lux) // library marker kkossev.illuminanceLib, line 45
} // library marker kkossev.illuminanceLib, line 46

void handleIlluminanceEvent(int illuminance, boolean isDigital=false) { // library marker kkossev.illuminanceLib, line 48
    Map eventMap = [:] // library marker kkossev.illuminanceLib, line 49
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.illuminanceLib, line 50
    eventMap.name = 'illuminance' // library marker kkossev.illuminanceLib, line 51
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.illuminanceLib, line 52
    eventMap.value  = illumCorrected // library marker kkossev.illuminanceLib, line 53
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.illuminanceLib, line 54
    eventMap.unit = 'lx' // library marker kkossev.illuminanceLib, line 55
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.illuminanceLib, line 56
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.illuminanceLib, line 57
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.illuminanceLib, line 58
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.illuminanceLib, line 59
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.illuminanceLib, line 60
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.illuminanceLib, line 61
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.illuminanceLib, line 62
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.illuminanceLib, line 63
        return // library marker kkossev.illuminanceLib, line 64
    } // library marker kkossev.illuminanceLib, line 65
    if (timeElapsed >= minTime) { // library marker kkossev.illuminanceLib, line 66
        logInfo "${eventMap.descriptionText}" // library marker kkossev.illuminanceLib, line 67
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.illuminanceLib, line 68
        state.lastRx['illumTime'] = now() // library marker kkossev.illuminanceLib, line 69
        sendEvent(eventMap) // library marker kkossev.illuminanceLib, line 70
    } // library marker kkossev.illuminanceLib, line 71
    else {         // queue the event // library marker kkossev.illuminanceLib, line 72
        eventMap.type = 'delayed' // library marker kkossev.illuminanceLib, line 73
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.illuminanceLib, line 74
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.illuminanceLib, line 75
    } // library marker kkossev.illuminanceLib, line 76
} // library marker kkossev.illuminanceLib, line 77

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.illuminanceLib, line 79
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.illuminanceLib, line 80
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.illuminanceLib, line 81
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.illuminanceLib, line 82
    sendEvent(eventMap) // library marker kkossev.illuminanceLib, line 83
} // library marker kkossev.illuminanceLib, line 84

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.illuminanceLib, line 86

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.illuminanceLib, line 88
void illuminanceProcessTuyaDP(final Map descMap, int dp, int dp_id, int fncmd) { // library marker kkossev.illuminanceLib, line 89
    switch (dp) { // library marker kkossev.illuminanceLib, line 90
        case 0x01 : // on/off // library marker kkossev.illuminanceLib, line 91
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.illuminanceLib, line 92
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.illuminanceLib, line 93
            } // library marker kkossev.illuminanceLib, line 94
            else { // library marker kkossev.illuminanceLib, line 95
                sendSwitchEvent(fncmd) // library marker kkossev.illuminanceLib, line 96
            } // library marker kkossev.illuminanceLib, line 97
            break // library marker kkossev.illuminanceLib, line 98
        case 0x02 : // library marker kkossev.illuminanceLib, line 99
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.illuminanceLib, line 100
                handleIlluminanceEvent(fncmd) // library marker kkossev.illuminanceLib, line 101
            } // library marker kkossev.illuminanceLib, line 102
            else { // library marker kkossev.illuminanceLib, line 103
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.illuminanceLib, line 104
            } // library marker kkossev.illuminanceLib, line 105
            break // library marker kkossev.illuminanceLib, line 106
        case 0x04 : // battery // library marker kkossev.illuminanceLib, line 107
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.illuminanceLib, line 108
            break // library marker kkossev.illuminanceLib, line 109
        default : // library marker kkossev.illuminanceLib, line 110
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.illuminanceLib, line 111
            break // library marker kkossev.illuminanceLib, line 112
    } // library marker kkossev.illuminanceLib, line 113
} // library marker kkossev.illuminanceLib, line 114

void illuminanceInitializeVars( boolean fullInit = false ) { // library marker kkossev.illuminanceLib, line 116
    logDebug "customInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.illuminanceLib, line 117
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 118
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // library marker kkossev.illuminanceLib, line 119
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.illuminanceLib, line 120
    } // library marker kkossev.illuminanceLib, line 121
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 122
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.illuminanceLib, line 123
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.illuminanceLib, line 124
    } // library marker kkossev.illuminanceLib, line 125
} // library marker kkossev.illuminanceLib, line 126

// ~~~~~ end include (168) kkossev.illuminanceLib ~~~~~
