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
 * ver. 3.2.0  2024-05-24 kkossev  - commonLib 3.2.0 allignment
 * ver. 3.2.1  2024-05-25 kkossev  - Tuya radars bug fix
 * ver. 3.2.2  2024-06-04 kkossev  - commonLib 3.2.1 allignment; deviceProfile preference bug fix.
 * ver. 3.2.3  2024-06-21 kkossev  - (dev. branch) added _TZE204_nbkshs6k and _TZE204_dapwryy7 @CheesyPotato 
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

static String version() { "3.2.3" }
static String timeStamp() {"2024/06/21 7:46 AM"}

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
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false, /*isSpammy:true, */ignoreIAS:true], // sends all DPs periodically!
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'2', 'detectionDelay':'101', 'fadingTime':'102', 'minimumDistance':'3', 'maximumDistance':'4'],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ztc6ggyl', deviceJoinName: 'Tuya ZigBee Breath Presence Sensor ZY-M100'],       // KK
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_ztc6ggyl', deviceJoinName: 'Tuya ZigBee Breath Presence Sensor ZY-M100'],       // KK
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ikvncluo', deviceJoinName: 'Moes TuyaHuman Presence Detector Radar 2 in 1'],    // jw970065; very spammy1
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
            spammyDPsToNotTrace : [9, 103, 104], // added the illuminance as a spammyDP - 05/30/10114
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
    ],

    'TS0601_NBKSHS6K_RADAR'   : [        //5GHz Tuya Thick White Square with Sqr Button
                description   : '5GHz Tuya Generic White Square Basic',
                models        : ['TS0601'],
                device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
                capabilities  : ['MotionSensor': true],
                preferences   : ["unknownDP12":"12"],
                commands      : ['resetStats':'resetStats'],
                fingerprints  : [
                    [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_nbkshs6k', deviceJoinName: '5GHz Tuya Generic White Square Basic']
                ],
                tuyaDPs:        [
                    [dp:1,   name:'motion',         type:'enum',   rw: 'ro', min:0, max:1,    defVal: '0', map:[0:'active', 1:'inactive'],     description:'Presence'],
                    //[dp:12,   name:'unknownDP12',         type:'number',   rw: 'rw', min:0, max:9999,    defVal: 1,      description:'UnknownDP12'],

                ],
                spammyDPsToIgnore : [],           
                spammyDPsToNotTrace : [],    
                deviceJoinName: '5GHz Tuya Generic White Square Basic'
        ],

'TS0601_DAPWRYY7_RADAR'   : [        //5GHz Tuya Thick White Square with Sqr Button
            description   : '5GHz Tuya Thick White Square with Sqr Button',
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'DistanceMeasurement':true, 'IlluminanceMeasurement': true],
            preferences   : ['fadingTime':'103', 'radarSensitivity':'105', 'maximumDistance':'107'], //BUG? - the dp103 is tested to set the fading time or motion reset with outbound commands BUT it is also the inbound illuminance dp
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_dapwryy7', deviceJoinName: '5GHz Tuya Thick White Square with Sqr Button']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',         type:'enum',   rw: 'ro', min:0, max:4,    defVal: '0', map:[0:'inactive', 4:'active'],     description:'Presence'],
                [dp:101, name:'distance', type:'decimal', rw: 'ro', min:0.0, max:9999.0, scale:100,   unit:'m',    description:'Distance'],
                [dp:103, name:'illuminance',            type:'number',  rw: 'ro',                     scale:1,    unit:'lx',       description:'Illuminance'], // BUG? - see above preferences

                //[dp:102, name:'unknownDP102', type:'number', rw: 'ro', min:0, max:9999, scale:1, unit:'', description:''],
                //[dp:104, name:'unknownDP104', type:'number', rw: 'ro', min:0, max:9999, scale:1, unit:'', description:''],

                [dp:105,   name:'radarSensitivity',       type:'number',  rw: 'rw', min:1,   max:9 ,    defVal:5,    scale:1,    unit:'',        title:'<b>Radar Sensitivity</b>',    description:'<i>Sensitivity of the radar</i>'],
                //[dp:106,   name:'minimumDistance',        type:'decimal', rw: 'ro', min:0.0, max:10.0,  defVal:0.1,  scale:100,  unit:'meters',   title:'<b>Minimum distance</b>',     description:'<i>Minimum detection distance</i>'],
                [dp:107,   name:'maximumDistance',        type:'decimal', rw: 'rw', min:0.0, max:10.0,  defVal:10.0,  scale:100,  unit:'meters',   title:'<b>Maximum distance</b>',     description:'<i>Maximum detection distance</i>'],
                
                //[dp:108, name:'unknownDP108', type:'number', rw: 'ro', min:0, max:9999, scale:1, unit:'', description:'Unkown DP108'],
                //[dp:109, name:'unknownDP109', type:'number', rw: 'ro', min:0, max:9999, scale:1, unit:'', description:'Unkown DP109'],
                //[dp:110, name:'unknownDP110', type:'number', rw: 'ro', min:0, max:9999, scale:1, unit:'', description:'Unkown DP110'],
                
                //[dp:114, name:'unknownDP114', type:'number', rw: 'rw', min:0, max:9999, scale:1, unit:'', description:'Unkown DP114'],
                //[dp:115, name:'unknownDP115', type:'number', rw: 'ro', min:0, max:9999, scale:1, unit:'', description:'Unkown DP115'],
                //[dp:116, name:'unknownDP116', type:'number', rw: 'ro', min:0, max:9999, scale:1, unit:'', description:'Unkown DP116'],
                //[dp:117, name:'unknownDP117', type:'number', rw: 'ro', min:0, max:9999, scale:1, unit:'', description:'Unkown DP117'],
                //[dp:118, name:'unknownDP118', type:'number', rw: 'ro', min:0, max:9999, scale:1, unit:'', description:'Unkown DP118'],
                //[dp:119, name:'unknownDP119', type:'number', rw: 'ro', min:0, max:9999, scale:1, unit:'', description:'Unkown DP119'],

            ],
            spammyDPsToIgnore : [],           
            spammyDPsToNotTrace : [],    
            deviceJoinName: '5GHz Tuya Thick White Square with Sqr Button'
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

// called from processFoundItem in deviceProfileLib 
void customProcessDeviceProfileEvent(final Map descMap, final String name, final valueScaled, final String unitText, final String descText) {
    logTrace "customProcessDeviceProfileEvent(${name}, ${valueScaled}) called"
    boolean doNotTrace = isSpammyDPsToNotTrace(descMap)
    Map eventMap = [name: name, value: valueScaled, unit: unitText, descriptionText: descText, type: 'physical', isStateChange: true]
    switch (name) {
        case 'motion' :
            handleMotion(valueScaled == 'active' ? true : false)  // bug fixed 05/30/2024
            break
        case 'illuminance' :
        case 'illuminance_lux' :    // ignore the IAS Zone illuminance reports for HL0SS9OA and 2AAELWXK
            //log.trace "illuminance event received deviceProfile is ${getDeviceProfile()} value=${value} valueScaled=${valueScaled} valueCorrected=${valueCorrected}"
            handleIlluminanceEvent(valueScaled as int)  // TODO : was valueCorrected !!!!! ?? check! TODO !
            break
        default :
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event !
            if (!doNotTrace) {
                logTrace "event ${name} sent w/ value ${valueScaled}"
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ?
            }
            break
    }    
}


/*  // moved to deviceProfileLib
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

*/

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
    standardParseTuyaCluster(descMap)  // commonLib
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
    version: '3.2.2' // library marker kkossev.commonLib, line 5
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
  * ver. 3.2.2  2024-06-12 kkossev  - (dev. branch) removed isAqaraTRV_OLD() and isAqaraTVOC_OLD() dependencies from the lib; added timeToHMS(); metering and electricalMeasure clusters swapped bug fix; added cluster 0x0204; // library marker kkossev.commonLib, line 38
  * // library marker kkossev.commonLib, line 39
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 40
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 41
  *                                   TODO: Versions of the main module + included libraries // library marker kkossev.commonLib, line 42
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 43
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 44
  * // library marker kkossev.commonLib, line 45
*/ // library marker kkossev.commonLib, line 46

String commonLibVersion() { '3.2.2' } // library marker kkossev.commonLib, line 48
String commonLibStamp() { '2024/06/12 4:32 PM' } // library marker kkossev.commonLib, line 49

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
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 90
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 91

        if (device) { // library marker kkossev.commonLib, line 93
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device...', defaultValue: false // library marker kkossev.commonLib, line 94
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 95
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 96
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 97
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 98
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
//boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 138
//boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 139
//boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 140
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
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 193
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 194
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 195
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 196
            } // library marker kkossev.commonLib, line 197
            break // library marker kkossev.commonLib, line 198
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 199
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 200
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 201
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 202
            } // library marker kkossev.commonLib, line 203
            break // library marker kkossev.commonLib, line 204
        default: // library marker kkossev.commonLib, line 205
            if (settings.logEnable) { // library marker kkossev.commonLib, line 206
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 207
            } // library marker kkossev.commonLib, line 208
            break // library marker kkossev.commonLib, line 209
    } // library marker kkossev.commonLib, line 210
} // library marker kkossev.commonLib, line 211

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 213
    0x0000: 'Basic',                0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x000C: 'AnalogInput', // library marker kkossev.commonLib, line 214
    0x0006: 'OnOff',                0x0008: 'LevelControl',     0x0012: 'MultistateInput',  0x0102: 'WindowCovering',   0x0201: 'Thermostat',   0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 215
    0x0400: 'Illuminance',          0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 216
    0x0B04: 'ElectricalMeasure',             0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',             0xFC11: 'FC11',         0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 217
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 218
] // library marker kkossev.commonLib, line 219

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 221
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 222
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 223
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 224
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 225
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 226
        return false // library marker kkossev.commonLib, line 227
    } // library marker kkossev.commonLib, line 228
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 229
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 230
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 231
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 232
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 233
        return true // library marker kkossev.commonLib, line 234
    } // library marker kkossev.commonLib, line 235
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 236
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 237
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 238
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 239
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 240
        return true // library marker kkossev.commonLib, line 241
    } // library marker kkossev.commonLib, line 242
    if (device?.getDataValue('model') != 'ZigUSB' && description.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 243
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 244
    } // library marker kkossev.commonLib, line 245
    return false // library marker kkossev.commonLib, line 246
} // library marker kkossev.commonLib, line 247

static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 249
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 250
} // library marker kkossev.commonLib, line 251

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 253
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 254
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 255
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 256
    } // library marker kkossev.commonLib, line 257
    return false // library marker kkossev.commonLib, line 258
} // library marker kkossev.commonLib, line 259

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 261
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 262
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 263
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 264
    } // library marker kkossev.commonLib, line 265
    return false // library marker kkossev.commonLib, line 266
} // library marker kkossev.commonLib, line 267

boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 269
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 270
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 271
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 272
    } // library marker kkossev.commonLib, line 273
    return false // library marker kkossev.commonLib, line 274
} // library marker kkossev.commonLib, line 275

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 277
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 278
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 279
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 280
] // library marker kkossev.commonLib, line 281

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 283
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 284
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 285
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 286
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 287
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 288
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 289
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 290
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 291
    List<String> cmds = [] // library marker kkossev.commonLib, line 292
    switch (clusterId) { // library marker kkossev.commonLib, line 293
        case 0x0005 : // library marker kkossev.commonLib, line 294
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 295
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 296
            // send the active endpoint response // library marker kkossev.commonLib, line 297
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 298
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 299
            break // library marker kkossev.commonLib, line 300
        case 0x0006 : // library marker kkossev.commonLib, line 301
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 302
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 303
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 304
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 305
            break // library marker kkossev.commonLib, line 306
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 307
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 308
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 309
            break // library marker kkossev.commonLib, line 310
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 311
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 312
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 313
            break // library marker kkossev.commonLib, line 314
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 315
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 316
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 317
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 318
            break // library marker kkossev.commonLib, line 319
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 320
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 321
            break // library marker kkossev.commonLib, line 322
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 323
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 324
            if (settings?.logEnable) { log.debug "${clusterInfo}" } // library marker kkossev.commonLib, line 325
            break // library marker kkossev.commonLib, line 326
        default : // library marker kkossev.commonLib, line 327
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 328
            break // library marker kkossev.commonLib, line 329
    } // library marker kkossev.commonLib, line 330
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 331
} // library marker kkossev.commonLib, line 332

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 334
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 335
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 336
    switch (commandId) { // library marker kkossev.commonLib, line 337
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 338
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 339
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 340
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 341
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 342
        default: // library marker kkossev.commonLib, line 343
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 344
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 345
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 346
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 347
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 348
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 349
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 350
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 351
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 352
            } // library marker kkossev.commonLib, line 353
            break // library marker kkossev.commonLib, line 354
    } // library marker kkossev.commonLib, line 355
} // library marker kkossev.commonLib, line 356

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 358
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 359
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 360
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 361
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 362
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 363
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 364
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 365
    } // library marker kkossev.commonLib, line 366
    else { // library marker kkossev.commonLib, line 367
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 368
    } // library marker kkossev.commonLib, line 369
} // library marker kkossev.commonLib, line 370

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 372
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 373
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 374
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 375
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 376
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 377
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 378
    } // library marker kkossev.commonLib, line 379
    else { // library marker kkossev.commonLib, line 380
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 381
    } // library marker kkossev.commonLib, line 382
} // library marker kkossev.commonLib, line 383

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 385
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 386
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 387
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 388
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 389
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 390
        state.reportingEnabled = true // library marker kkossev.commonLib, line 391
    } // library marker kkossev.commonLib, line 392
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 393
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 394
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 395
    } else { // library marker kkossev.commonLib, line 396
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 397
    } // library marker kkossev.commonLib, line 398
} // library marker kkossev.commonLib, line 399

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 401
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 402
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 403
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 404
    if (status == 0) { // library marker kkossev.commonLib, line 405
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 406
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 407
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 408
        int delta = 0 // library marker kkossev.commonLib, line 409
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 410
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 411
        } // library marker kkossev.commonLib, line 412
        else { // library marker kkossev.commonLib, line 413
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 414
        } // library marker kkossev.commonLib, line 415
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 416
    } // library marker kkossev.commonLib, line 417
    else { // library marker kkossev.commonLib, line 418
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 419
    } // library marker kkossev.commonLib, line 420
} // library marker kkossev.commonLib, line 421

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 423
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 424
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 425
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 426
        return false // library marker kkossev.commonLib, line 427
    } // library marker kkossev.commonLib, line 428
    // execute the customHandler function // library marker kkossev.commonLib, line 429
    boolean result = false // library marker kkossev.commonLib, line 430
    try { // library marker kkossev.commonLib, line 431
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 432
    } // library marker kkossev.commonLib, line 433
    catch (e) { // library marker kkossev.commonLib, line 434
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 435
        return false // library marker kkossev.commonLib, line 436
    } // library marker kkossev.commonLib, line 437
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 438
    return result // library marker kkossev.commonLib, line 439
} // library marker kkossev.commonLib, line 440

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 442
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 443
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 444
    final String commandId = data[0] // library marker kkossev.commonLib, line 445
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 446
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 447
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 448
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 449
    } else { // library marker kkossev.commonLib, line 450
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 451
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 452
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 453
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 454
        } // library marker kkossev.commonLib, line 455
    } // library marker kkossev.commonLib, line 456
} // library marker kkossev.commonLib, line 457

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 459
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 460
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 461
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 462

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 464
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 465
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 466
] // library marker kkossev.commonLib, line 467

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 469
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 470
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 471
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 472
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 473
] // library marker kkossev.commonLib, line 474

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 476
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 477
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 478
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 479
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 480
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 481
    return avg // library marker kkossev.commonLib, line 482
} // library marker kkossev.commonLib, line 483

/* // library marker kkossev.commonLib, line 485
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 486
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 487
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 488
*/ // library marker kkossev.commonLib, line 489
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 490

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 492
void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 493
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 494
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 495
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 496
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 497
        case 0x0000: // library marker kkossev.commonLib, line 498
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 499
            break // library marker kkossev.commonLib, line 500
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 501
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 502
            if (isPing) { // library marker kkossev.commonLib, line 503
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
            else { // library marker kkossev.commonLib, line 517
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 518
            } // library marker kkossev.commonLib, line 519
            break // library marker kkossev.commonLib, line 520
        case 0x0004: // library marker kkossev.commonLib, line 521
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 522
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 523
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 524
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 525
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 526
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 527
            } // library marker kkossev.commonLib, line 528
            break // library marker kkossev.commonLib, line 529
        case 0x0005: // library marker kkossev.commonLib, line 530
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 531
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 532
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 533
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 534
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 535
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 536
            } // library marker kkossev.commonLib, line 537
            break // library marker kkossev.commonLib, line 538
        case 0x0007: // library marker kkossev.commonLib, line 539
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 540
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 541
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 542
            break // library marker kkossev.commonLib, line 543
        case 0xFFDF: // library marker kkossev.commonLib, line 544
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 545
            break // library marker kkossev.commonLib, line 546
        case 0xFFE2: // library marker kkossev.commonLib, line 547
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 548
            break // library marker kkossev.commonLib, line 549
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 550
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 551
            break // library marker kkossev.commonLib, line 552
        case 0xFFFE: // library marker kkossev.commonLib, line 553
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 554
            break // library marker kkossev.commonLib, line 555
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 556
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 557
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 558
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 559
            break // library marker kkossev.commonLib, line 560
        default: // library marker kkossev.commonLib, line 561
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 562
            break // library marker kkossev.commonLib, line 563
    } // library marker kkossev.commonLib, line 564
} // library marker kkossev.commonLib, line 565

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 567
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 568
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 569

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 571
    Map descMap = [:] // library marker kkossev.commonLib, line 572
    try { // library marker kkossev.commonLib, line 573
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 574
    } // library marker kkossev.commonLib, line 575
    catch (e1) { // library marker kkossev.commonLib, line 576
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 577
        // try alternative custom parsing // library marker kkossev.commonLib, line 578
        descMap = [:] // library marker kkossev.commonLib, line 579
        try { // library marker kkossev.commonLib, line 580
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 581
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 582
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 583
            } // library marker kkossev.commonLib, line 584
        } // library marker kkossev.commonLib, line 585
        catch (e2) { // library marker kkossev.commonLib, line 586
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 587
            return [:] // library marker kkossev.commonLib, line 588
        } // library marker kkossev.commonLib, line 589
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 590
    } // library marker kkossev.commonLib, line 591
    return descMap // library marker kkossev.commonLib, line 592
} // library marker kkossev.commonLib, line 593

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 595
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 596
        return false // library marker kkossev.commonLib, line 597
    } // library marker kkossev.commonLib, line 598
    // try to parse ... // library marker kkossev.commonLib, line 599
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 600
    Map descMap = [:] // library marker kkossev.commonLib, line 601
    try { // library marker kkossev.commonLib, line 602
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 603
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 604
    } // library marker kkossev.commonLib, line 605
    catch (e) { // library marker kkossev.commonLib, line 606
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 607
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 608
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 609
        return true // library marker kkossev.commonLib, line 610
    } // library marker kkossev.commonLib, line 611

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 613
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 614
    } // library marker kkossev.commonLib, line 615
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 616
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 617
    } // library marker kkossev.commonLib, line 618
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 619
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 620
    } // library marker kkossev.commonLib, line 621
    else { // library marker kkossev.commonLib, line 622
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 623
        return false // library marker kkossev.commonLib, line 624
    } // library marker kkossev.commonLib, line 625
    return true    // processed // library marker kkossev.commonLib, line 626
} // library marker kkossev.commonLib, line 627

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 629
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 630
  /* // library marker kkossev.commonLib, line 631
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 632
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 633
        return true // library marker kkossev.commonLib, line 634
    } // library marker kkossev.commonLib, line 635
*/ // library marker kkossev.commonLib, line 636
    Map descMap = [:] // library marker kkossev.commonLib, line 637
    try { // library marker kkossev.commonLib, line 638
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 639
    } // library marker kkossev.commonLib, line 640
    catch (e1) { // library marker kkossev.commonLib, line 641
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 642
        // try alternative custom parsing // library marker kkossev.commonLib, line 643
        descMap = [:] // library marker kkossev.commonLib, line 644
        try { // library marker kkossev.commonLib, line 645
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 646
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 647
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 648
            } // library marker kkossev.commonLib, line 649
        } // library marker kkossev.commonLib, line 650
        catch (e2) { // library marker kkossev.commonLib, line 651
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 652
            return true // library marker kkossev.commonLib, line 653
        } // library marker kkossev.commonLib, line 654
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 655
    } // library marker kkossev.commonLib, line 656
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 657
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 658
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 659
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 660
        return false // library marker kkossev.commonLib, line 661
    } // library marker kkossev.commonLib, line 662
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 663
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 664
    // attribute report received // library marker kkossev.commonLib, line 665
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 666
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 667
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 668
    } // library marker kkossev.commonLib, line 669
    attrData.each { // library marker kkossev.commonLib, line 670
        if (it.status == '86') { // library marker kkossev.commonLib, line 671
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 672
        // TODO - skip parsing? // library marker kkossev.commonLib, line 673
        } // library marker kkossev.commonLib, line 674
        switch (it.cluster) { // library marker kkossev.commonLib, line 675
            case '0000' : // library marker kkossev.commonLib, line 676
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 677
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 678
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 679
                } // library marker kkossev.commonLib, line 680
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 681
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 682
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 683
                } // library marker kkossev.commonLib, line 684
                else { // library marker kkossev.commonLib, line 685
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 686
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 687
                } // library marker kkossev.commonLib, line 688
                break // library marker kkossev.commonLib, line 689
            default : // library marker kkossev.commonLib, line 690
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 691
                break // library marker kkossev.commonLib, line 692
        } // switch // library marker kkossev.commonLib, line 693
    } // for each attribute // library marker kkossev.commonLib, line 694
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 695
} // library marker kkossev.commonLib, line 696

String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 698
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 699
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 700
} // library marker kkossev.commonLib, line 701

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 703
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 704
} // library marker kkossev.commonLib, line 705

/* // library marker kkossev.commonLib, line 707
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 708
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 709
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 710
*/ // library marker kkossev.commonLib, line 711
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 712
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 713
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 714

// Tuya Commands // library marker kkossev.commonLib, line 716
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 717
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 718
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 719
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 720
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 721

// tuya DP type // library marker kkossev.commonLib, line 723
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 724
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 725
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 726
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 727
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 728
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 729

void syncTuyaDateTime() { // library marker kkossev.commonLib, line 731
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 732
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 733
    long offset = 0 // library marker kkossev.commonLib, line 734
    int offsetHours = 0 // library marker kkossev.commonLib, line 735
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 736
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 737
    try { // library marker kkossev.commonLib, line 738
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 739
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 740
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 741
    } catch (e) { // library marker kkossev.commonLib, line 742
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 743
    } // library marker kkossev.commonLib, line 744
    // // library marker kkossev.commonLib, line 745
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 746
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 747
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 748
} // library marker kkossev.commonLib, line 749

// called from the main parse method when the cluster is 0xEF00 // library marker kkossev.commonLib, line 751
void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 752
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 753
        syncTuyaDateTime() // library marker kkossev.commonLib, line 754
    } // library marker kkossev.commonLib, line 755
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 756
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 757
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 758
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 759
        if (status != '00') { // library marker kkossev.commonLib, line 760
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 761
        } // library marker kkossev.commonLib, line 762
    } // library marker kkossev.commonLib, line 763
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 764
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 765
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 766
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 767
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 768
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 769
            return // library marker kkossev.commonLib, line 770
        } // library marker kkossev.commonLib, line 771
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 772
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 773
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 774
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 775
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 776
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 777
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 778
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 779
            } // library marker kkossev.commonLib, line 780
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 781
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 782
        } // library marker kkossev.commonLib, line 783
    } // library marker kkossev.commonLib, line 784
    else { // library marker kkossev.commonLib, line 785
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 786
    } // library marker kkossev.commonLib, line 787
} // library marker kkossev.commonLib, line 788

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 790
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 791
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 792
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 793
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 794
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 795
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 796
        } // library marker kkossev.commonLib, line 797
    } // library marker kkossev.commonLib, line 798
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 799
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 800
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 801
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 802
        } // library marker kkossev.commonLib, line 803
    } // library marker kkossev.commonLib, line 804
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 805
} // library marker kkossev.commonLib, line 806

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 808
    int retValue = 0 // library marker kkossev.commonLib, line 809
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 810
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 811
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 812
        int power = 1 // library marker kkossev.commonLib, line 813
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 814
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 815
            power = power * 256 // library marker kkossev.commonLib, line 816
        } // library marker kkossev.commonLib, line 817
    } // library marker kkossev.commonLib, line 818
    return retValue // library marker kkossev.commonLib, line 819
} // library marker kkossev.commonLib, line 820

private List<String> getTuyaCommand(String dp, String dp_type, String fncmd) { return sendTuyaCommand(dp, dp_type, fncmd) } // library marker kkossev.commonLib, line 822

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 824
    List<String> cmds = [] // library marker kkossev.commonLib, line 825
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 826
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 827
    //int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 828
    int tuyaCmd = isFingerbot() ? 0x04 : tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 829
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 830
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 831
    return cmds // library marker kkossev.commonLib, line 832
} // library marker kkossev.commonLib, line 833

private getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 835

void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 837
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 838
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 839
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 840
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 841
} // library marker kkossev.commonLib, line 842

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 844
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 845

List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 847
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 848
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 849
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 850
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 851
} // library marker kkossev.commonLib, line 852

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 854
    List<String> cmds = [] // library marker kkossev.commonLib, line 855
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 856
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 857
    } // library marker kkossev.commonLib, line 858
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 859
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 860
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 861
        return // library marker kkossev.commonLib, line 862
    } // library marker kkossev.commonLib, line 863
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 864
} // library marker kkossev.commonLib, line 865

// Invoked from configure() // library marker kkossev.commonLib, line 867
List<String> initializeDevice() { // library marker kkossev.commonLib, line 868
    List<String> cmds = [] // library marker kkossev.commonLib, line 869
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 870
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 871
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 872
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 873
    } // library marker kkossev.commonLib, line 874
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 875
    return cmds // library marker kkossev.commonLib, line 876
} // library marker kkossev.commonLib, line 877

// Invoked from configure() // library marker kkossev.commonLib, line 879
List<String> configureDevice() { // library marker kkossev.commonLib, line 880
    List<String> cmds = [] // library marker kkossev.commonLib, line 881
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 882
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 883
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 884
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 885
    } // library marker kkossev.commonLib, line 886
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 887
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 888
    return cmds // library marker kkossev.commonLib, line 889
} // library marker kkossev.commonLib, line 890

/* // library marker kkossev.commonLib, line 892
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 893
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 894
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 895
*/ // library marker kkossev.commonLib, line 896

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 898
    List<String> cmds = [] // library marker kkossev.commonLib, line 899
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 900
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 901
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 902
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 903
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 904
            } // library marker kkossev.commonLib, line 905
        } // library marker kkossev.commonLib, line 906
    } // library marker kkossev.commonLib, line 907
    return cmds // library marker kkossev.commonLib, line 908
} // library marker kkossev.commonLib, line 909

void refresh() { // library marker kkossev.commonLib, line 911
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 912
    checkDriverVersion(state) // library marker kkossev.commonLib, line 913
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 914
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 915
        customCmds = customRefresh() // library marker kkossev.commonLib, line 916
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 917
    } // library marker kkossev.commonLib, line 918
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 919
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 920
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 921
    } // library marker kkossev.commonLib, line 922
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 923
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 924
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 925
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 926
    } // library marker kkossev.commonLib, line 927
    else { // library marker kkossev.commonLib, line 928
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 929
    } // library marker kkossev.commonLib, line 930
} // library marker kkossev.commonLib, line 931

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 933
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 934
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 935

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 937
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 938
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 939
        sendEvent(name: 'Status', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 940
    } // library marker kkossev.commonLib, line 941
    else { // library marker kkossev.commonLib, line 942
        logInfo "${info}" // library marker kkossev.commonLib, line 943
        sendEvent(name: 'Status', value: info, type: 'digital') // library marker kkossev.commonLib, line 944
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 945
    } // library marker kkossev.commonLib, line 946
} // library marker kkossev.commonLib, line 947

public void ping() { // library marker kkossev.commonLib, line 949
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 950
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 951
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 952
    if (isVirtual()) { runInMillis(10, virtualPong) } // library marker kkossev.commonLib, line 953
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) } // library marker kkossev.commonLib, line 954
    logDebug 'ping...' // library marker kkossev.commonLib, line 955
} // library marker kkossev.commonLib, line 956

def virtualPong() { // library marker kkossev.commonLib, line 958
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 959
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 960
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 961
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 962
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 963
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 964
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 965
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 966
        sendRttEvent() // library marker kkossev.commonLib, line 967
    } // library marker kkossev.commonLib, line 968
    else { // library marker kkossev.commonLib, line 969
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 970
    } // library marker kkossev.commonLib, line 971
    state.states['isPing'] = false // library marker kkossev.commonLib, line 972
    //unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 973
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 974
} // library marker kkossev.commonLib, line 975

void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 977
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 978
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 979
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 980
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 981
    if (value == null) { // library marker kkossev.commonLib, line 982
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 983
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 984
    } // library marker kkossev.commonLib, line 985
    else { // library marker kkossev.commonLib, line 986
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 987
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 988
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 989
    } // library marker kkossev.commonLib, line 990
} // library marker kkossev.commonLib, line 991

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 993
    if (cluster != null) { // library marker kkossev.commonLib, line 994
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 995
    } // library marker kkossev.commonLib, line 996
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 997
    return 'NULL' // library marker kkossev.commonLib, line 998
} // library marker kkossev.commonLib, line 999

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1001
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1002
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1003
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1004
} // library marker kkossev.commonLib, line 1005

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1007
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1008
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1009
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1010
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1011
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1012
    } // library marker kkossev.commonLib, line 1013
} // library marker kkossev.commonLib, line 1014

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1016
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1017
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1018
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1019
} // library marker kkossev.commonLib, line 1020

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1022
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1023
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1024
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1025
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1026
    } // library marker kkossev.commonLib, line 1027
    else { // library marker kkossev.commonLib, line 1028
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1029
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1030
    } // library marker kkossev.commonLib, line 1031
} // library marker kkossev.commonLib, line 1032

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1034
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1035
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1036
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1037
} // library marker kkossev.commonLib, line 1038

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1040
void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1041
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1042
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1043
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1044
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1045
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1046
    } // library marker kkossev.commonLib, line 1047
} // library marker kkossev.commonLib, line 1048

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1050
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1051
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1052
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1053
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1054
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1055
            logWarn 'not present!' // library marker kkossev.commonLib, line 1056
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1057
        } // library marker kkossev.commonLib, line 1058
    } // library marker kkossev.commonLib, line 1059
    else { // library marker kkossev.commonLib, line 1060
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1061
    } // library marker kkossev.commonLib, line 1062
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1063
} // library marker kkossev.commonLib, line 1064

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1066
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1067
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1068
    if (value == 'online') { // library marker kkossev.commonLib, line 1069
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1070
    } // library marker kkossev.commonLib, line 1071
    else { // library marker kkossev.commonLib, line 1072
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1073
    } // library marker kkossev.commonLib, line 1074
} // library marker kkossev.commonLib, line 1075

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1077
void updated() { // library marker kkossev.commonLib, line 1078
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1079
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1080
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1081
    unschedule() // library marker kkossev.commonLib, line 1082

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1084
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1085
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1086
    } // library marker kkossev.commonLib, line 1087
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1088
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1089
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1090
    } // library marker kkossev.commonLib, line 1091

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1093
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1094
        // schedule the periodic timer // library marker kkossev.commonLib, line 1095
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1096
        if (interval > 0) { // library marker kkossev.commonLib, line 1097
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1098
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1099
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1100
        } // library marker kkossev.commonLib, line 1101
    } // library marker kkossev.commonLib, line 1102
    else { // library marker kkossev.commonLib, line 1103
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1104
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1105
    } // library marker kkossev.commonLib, line 1106
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1107
        customUpdated() // library marker kkossev.commonLib, line 1108
    } // library marker kkossev.commonLib, line 1109

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1111
} // library marker kkossev.commonLib, line 1112

void logsOff() { // library marker kkossev.commonLib, line 1114
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1115
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1116
} // library marker kkossev.commonLib, line 1117
void traceOff() { // library marker kkossev.commonLib, line 1118
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1119
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1120
} // library marker kkossev.commonLib, line 1121

void configure(String command) { // library marker kkossev.commonLib, line 1123
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1124
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1125
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1126
        return // library marker kkossev.commonLib, line 1127
    } // library marker kkossev.commonLib, line 1128
    // // library marker kkossev.commonLib, line 1129
    String func // library marker kkossev.commonLib, line 1130
    try { // library marker kkossev.commonLib, line 1131
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1132
        "$func"() // library marker kkossev.commonLib, line 1133
    } // library marker kkossev.commonLib, line 1134
    catch (e) { // library marker kkossev.commonLib, line 1135
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1136
        return // library marker kkossev.commonLib, line 1137
    } // library marker kkossev.commonLib, line 1138
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1139
} // library marker kkossev.commonLib, line 1140

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1142
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1143
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1144
} // library marker kkossev.commonLib, line 1145

void loadAllDefaults() { // library marker kkossev.commonLib, line 1147
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1148
    deleteAllSettings() // library marker kkossev.commonLib, line 1149
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1150
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1151
    deleteAllStates() // library marker kkossev.commonLib, line 1152
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1153

    initialize() // library marker kkossev.commonLib, line 1155
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1156
    updated() // library marker kkossev.commonLib, line 1157
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1158
} // library marker kkossev.commonLib, line 1159

void configureNow() { // library marker kkossev.commonLib, line 1161
    configure() // library marker kkossev.commonLib, line 1162
} // library marker kkossev.commonLib, line 1163

/** // library marker kkossev.commonLib, line 1165
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1166
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1167
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1168
 */ // library marker kkossev.commonLib, line 1169
void configure() { // library marker kkossev.commonLib, line 1170
    List<String> cmds = [] // library marker kkossev.commonLib, line 1171
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1172
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1173
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1174
    if (isTuya()) { // library marker kkossev.commonLib, line 1175
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1176
    } // library marker kkossev.commonLib, line 1177
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1178
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1179
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1180
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1181
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1182
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1183
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1184
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1185
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1186
    } // library marker kkossev.commonLib, line 1187
    else { // library marker kkossev.commonLib, line 1188
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1189
    } // library marker kkossev.commonLib, line 1190
} // library marker kkossev.commonLib, line 1191

 // Invoked when the device is installed or when driver is installed ? // library marker kkossev.commonLib, line 1193
void installed() { // library marker kkossev.commonLib, line 1194
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1195
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1196
    // populate some default values for attributes // library marker kkossev.commonLib, line 1197
    sendEvent(name: 'healthStatus', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1198
    sendEvent(name: 'powerSource',  value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1199
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1200
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1201
} // library marker kkossev.commonLib, line 1202

 // Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1204
void initialize() { // library marker kkossev.commonLib, line 1205
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1206
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1207
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1208
    updateTuyaVersion() // library marker kkossev.commonLib, line 1209
    updateAqaraVersion() // library marker kkossev.commonLib, line 1210
} // library marker kkossev.commonLib, line 1211

/* // library marker kkossev.commonLib, line 1213
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1214
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1215
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1216
*/ // library marker kkossev.commonLib, line 1217

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1219
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1220
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1221
} // library marker kkossev.commonLib, line 1222

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1224
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1225
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1226
} // library marker kkossev.commonLib, line 1227

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1229
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1230
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1231
} // library marker kkossev.commonLib, line 1232

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1234
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1235
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1236
        return // library marker kkossev.commonLib, line 1237
    } // library marker kkossev.commonLib, line 1238
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1239
    cmd.each { // library marker kkossev.commonLib, line 1240
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1241
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1242
            return // library marker kkossev.commonLib, line 1243
        } // library marker kkossev.commonLib, line 1244
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1245
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1246
    } // library marker kkossev.commonLib, line 1247
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1248
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1249
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1250
} // library marker kkossev.commonLib, line 1251

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1253

String getDeviceInfo() { // library marker kkossev.commonLib, line 1255
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1256
} // library marker kkossev.commonLib, line 1257

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1259
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1260
} // library marker kkossev.commonLib, line 1261

@CompileStatic // library marker kkossev.commonLib, line 1263
void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1264
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1265
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1266
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1267
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1268
        initializeVars(false) // library marker kkossev.commonLib, line 1269
        updateTuyaVersion() // library marker kkossev.commonLib, line 1270
        updateAqaraVersion() // library marker kkossev.commonLib, line 1271
    } // library marker kkossev.commonLib, line 1272
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1273
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1274
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1275
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1276
} // library marker kkossev.commonLib, line 1277

// credits @thebearmay // library marker kkossev.commonLib, line 1279
String getModel() { // library marker kkossev.commonLib, line 1280
    try { // library marker kkossev.commonLib, line 1281
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1282
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1283
    } catch (ignore) { // library marker kkossev.commonLib, line 1284
        try { // library marker kkossev.commonLib, line 1285
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1286
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1287
                return model // library marker kkossev.commonLib, line 1288
            } // library marker kkossev.commonLib, line 1289
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1290
            return '' // library marker kkossev.commonLib, line 1291
        } // library marker kkossev.commonLib, line 1292
    } // library marker kkossev.commonLib, line 1293
} // library marker kkossev.commonLib, line 1294

// credits @thebearmay // library marker kkossev.commonLib, line 1296
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1297
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1298
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1299
    String revision = tokens.last() // library marker kkossev.commonLib, line 1300
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1301
} // library marker kkossev.commonLib, line 1302

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1304
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1305
    unschedule() // library marker kkossev.commonLib, line 1306
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1307
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1308

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1310
} // library marker kkossev.commonLib, line 1311

void resetStatistics() { // library marker kkossev.commonLib, line 1313
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1314
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1315
} // library marker kkossev.commonLib, line 1316

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1318
void resetStats() { // library marker kkossev.commonLib, line 1319
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1320
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1321
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1322
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1323
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1324
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1325
} // library marker kkossev.commonLib, line 1326

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1328
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1329
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1330
        state.clear() // library marker kkossev.commonLib, line 1331
        unschedule() // library marker kkossev.commonLib, line 1332
        resetStats() // library marker kkossev.commonLib, line 1333
        if (deviceProfilesV3 != null && this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1334
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1335
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1336
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1337
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1338
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1339
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1340
    } // library marker kkossev.commonLib, line 1341

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1343
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1344
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1345
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1346
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1347

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1349
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1350
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1351
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1352
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1353
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1354
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1355

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1357

    // common libraries initialization // library marker kkossev.commonLib, line 1359
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1360
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1361
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1362
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1363
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1364

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1366
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1367
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1368
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1369

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1371
    if ( mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1372
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1373
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1374
    if ( ep  != null) { // library marker kkossev.commonLib, line 1375
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1376
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1377
    } // library marker kkossev.commonLib, line 1378
    else { // library marker kkossev.commonLib, line 1379
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1380
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1381
    } // library marker kkossev.commonLib, line 1382
} // library marker kkossev.commonLib, line 1383

void setDestinationEP() { // library marker kkossev.commonLib, line 1385
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1386
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1387
        state.destinationEP = ep // library marker kkossev.commonLib, line 1388
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1389
    } // library marker kkossev.commonLib, line 1390
    else { // library marker kkossev.commonLib, line 1391
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1392
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 1393
    } // library marker kkossev.commonLib, line 1394
} // library marker kkossev.commonLib, line 1395

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1397
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1398
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1399
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1400

// _DEBUG mode only // library marker kkossev.commonLib, line 1402
void getAllProperties() { // library marker kkossev.commonLib, line 1403
    log.trace 'Properties:' // library marker kkossev.commonLib, line 1404
    device.properties.each { it -> // library marker kkossev.commonLib, line 1405
        log.debug it // library marker kkossev.commonLib, line 1406
    } // library marker kkossev.commonLib, line 1407
    log.trace 'Settings:' // library marker kkossev.commonLib, line 1408
    settings.each { it -> // library marker kkossev.commonLib, line 1409
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1410
    } // library marker kkossev.commonLib, line 1411
    log.trace 'Done' // library marker kkossev.commonLib, line 1412
} // library marker kkossev.commonLib, line 1413

// delete all Preferences // library marker kkossev.commonLib, line 1415
void deleteAllSettings() { // library marker kkossev.commonLib, line 1416
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1417
    settings.each { it -> // library marker kkossev.commonLib, line 1418
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 1419
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 1420
    } // library marker kkossev.commonLib, line 1421
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1422
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1423
} // library marker kkossev.commonLib, line 1424

// delete all attributes // library marker kkossev.commonLib, line 1426
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1427
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1428
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1429
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1430
} // library marker kkossev.commonLib, line 1431

// delete all State Variables // library marker kkossev.commonLib, line 1433
void deleteAllStates() { // library marker kkossev.commonLib, line 1434
    String stateDeleted = '' // library marker kkossev.commonLib, line 1435
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1436
    state.clear() // library marker kkossev.commonLib, line 1437
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1438
} // library marker kkossev.commonLib, line 1439

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1441
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1442
} // library marker kkossev.commonLib, line 1443

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1445
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 1446
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 1447
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 1448
    } // library marker kkossev.commonLib, line 1449
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1450
} // library marker kkossev.commonLib, line 1451

void testParse(String par) { // library marker kkossev.commonLib, line 1453
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1454
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1455
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1456
    parse(par) // library marker kkossev.commonLib, line 1457
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1458
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1459
} // library marker kkossev.commonLib, line 1460

def testJob() { // library marker kkossev.commonLib, line 1462
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1463
} // library marker kkossev.commonLib, line 1464

/** // library marker kkossev.commonLib, line 1466
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1467
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1468
 */ // library marker kkossev.commonLib, line 1469
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1470
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1471
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1472
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1473
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1474
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1475
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1476
    String cron // library marker kkossev.commonLib, line 1477
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1478
    else { // library marker kkossev.commonLib, line 1479
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1480
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1481
    } // library marker kkossev.commonLib, line 1482
    return cron // library marker kkossev.commonLib, line 1483
} // library marker kkossev.commonLib, line 1484

// credits @thebearmay // library marker kkossev.commonLib, line 1486
String formatUptime() { // library marker kkossev.commonLib, line 1487
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1488
} // library marker kkossev.commonLib, line 1489

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1491
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1492
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1493
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1494
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1495
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1496
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1497
} // library marker kkossev.commonLib, line 1498

boolean isTuya() { // library marker kkossev.commonLib, line 1500
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1501
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1502
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1503
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1504
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1505
} // library marker kkossev.commonLib, line 1506

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1508
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1509
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1510
    if (application != null) { // library marker kkossev.commonLib, line 1511
        Integer ver // library marker kkossev.commonLib, line 1512
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1513
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1514
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1515
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1516
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1517
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1518
        } // library marker kkossev.commonLib, line 1519
    } // library marker kkossev.commonLib, line 1520
} // library marker kkossev.commonLib, line 1521

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1523

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1525
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1526
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1527
    if (application != null) { // library marker kkossev.commonLib, line 1528
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1529
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1530
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1531
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1532
        } // library marker kkossev.commonLib, line 1533
    } // library marker kkossev.commonLib, line 1534
} // library marker kkossev.commonLib, line 1535

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1537
    try { // library marker kkossev.commonLib, line 1538
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1539
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1540
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1541
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1542
    } catch (e) { // library marker kkossev.commonLib, line 1543
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1544
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1545
    } // library marker kkossev.commonLib, line 1546
} // library marker kkossev.commonLib, line 1547

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1549
    try { // library marker kkossev.commonLib, line 1550
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1551
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1552
        return date.getTime() // library marker kkossev.commonLib, line 1553
    } catch (e) { // library marker kkossev.commonLib, line 1554
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1555
        return now() // library marker kkossev.commonLib, line 1556
    } // library marker kkossev.commonLib, line 1557
} // library marker kkossev.commonLib, line 1558

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1560
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1561
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1562
    int seconds = time % 60 // library marker kkossev.commonLib, line 1563
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1564
} // library marker kkossev.commonLib, line 1565

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

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
 * ver. 3.0.0  2023-11-27 kkossev  - (dev. branch) fixes for use with commonLib; added processClusterAttributeFromDeviceProfile() method; added validateAndFixPreferences() method;  inputIt bug fix; signedInt Preproc method; // library marker kkossev.deviceProfileLib, line 20
 * ver. 3.0.1  2023-12-02 kkossev  - (dev. branch) release candidate // library marker kkossev.deviceProfileLib, line 21
 * ver. 3.0.2  2023-12-17 kkossev  - (dev. branch) inputIt moved to the preferences section; setfunction replaced by customSetFunction; Groovy Linting; // library marker kkossev.deviceProfileLib, line 22
 * ver. 3.0.4  2024-03-30 kkossev  - (dev. branch) more Groovy Linting; processClusterAttributeFromDeviceProfile exception fix; // library marker kkossev.deviceProfileLib, line 23
 * ver. 3.1.0  2024-04-03 kkossev  - (dev. branch) more Groovy Linting; deviceProfilesV3, enum pars bug fix; // library marker kkossev.deviceProfileLib, line 24
 * ver. 3.1.1  2024-04-21 kkossev  - (dev. branch) deviceProfilesV3 bug fix; tuyaDPs list of maps bug fix; resetPreferencesToDefaults bug fix; // library marker kkossev.deviceProfileLib, line 25
 * ver. 3.1.2  2024-05-05 kkossev  - (dev. branch) added isSpammyDeviceProfile() // library marker kkossev.deviceProfileLib, line 26
 * ver. 3.1.3  2024-05-21 kkossev  - skip processClusterAttributeFromDeviceProfile if cluster or attribute or value is missing // library marker kkossev.deviceProfileLib, line 27
 * ver. 3.2.0  2024-05-25 kkossev  - commonLib 3.2.0 allignment; // library marker kkossev.deviceProfileLib, line 28
 * ver. 3.2.1  2024-06-06 kkossev  - Tuya Multi Sensor 4 In 1 (V3) driver allignment (customProcessDeviceProfileEvent); getDeviceProfilesMap bug fix; forcedProfile is always shown in preferences; // library marker kkossev.deviceProfileLib, line 29
 * ver. 3.3.0  2024-06-16 kkossev  - (dev. branch) empty preferences bug fix; zclWriteAttribute delay 50 ms; added advanced check in inputIt() // library marker kkossev.deviceProfileLib, line 30
 * // library marker kkossev.deviceProfileLib, line 31
 *                                   TODO - remove the 2-in-1 patch ! // library marker kkossev.deviceProfileLib, line 32
 *                                   TODO - add defaults for profileId:'0104', endpointId:'01', inClusters, outClusters, in the deviceProfilesV3 map // library marker kkossev.deviceProfileLib, line 33
 *                                   TODO - updateStateUnknownDPs !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 34
 *                                   TODO - check why the forcedProfile preference is not initialized? // library marker kkossev.deviceProfileLib, line 35
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 36
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 37
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off) // library marker kkossev.deviceProfileLib, line 38
 *                                   TODO: allow NULL parameters default values in the device profiles // library marker kkossev.deviceProfileLib, line 39
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 40
 * // library marker kkossev.deviceProfileLib, line 41
*/ // library marker kkossev.deviceProfileLib, line 42

static String deviceProfileLibVersion()   { '3.3.0' } // library marker kkossev.deviceProfileLib, line 44
static String deviceProfileLibStamp() { '2024/06/16 8:10 AM' } // library marker kkossev.deviceProfileLib, line 45
import groovy.json.* // library marker kkossev.deviceProfileLib, line 46
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 47
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 48
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 49
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 50

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 52

metadata { // library marker kkossev.deviceProfileLib, line 54
    // no capabilities // library marker kkossev.deviceProfileLib, line 55
    // no attributes // library marker kkossev.deviceProfileLib, line 56
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 57
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 58
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 59
    ] // library marker kkossev.deviceProfileLib, line 60
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 61
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 62
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 63
    ] // library marker kkossev.deviceProfileLib, line 64

    preferences { // library marker kkossev.deviceProfileLib, line 66
        if (device) { // library marker kkossev.deviceProfileLib, line 67
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 68
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 69
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 70
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 71
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 72
                        input inputMap // library marker kkossev.deviceProfileLib, line 73
                    } // library marker kkossev.deviceProfileLib, line 74
                } // library marker kkossev.deviceProfileLib, line 75
            } // library marker kkossev.deviceProfileLib, line 76
            //if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 77
                input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 78
            //} // library marker kkossev.deviceProfileLib, line 79
        } // library marker kkossev.deviceProfileLib, line 80
    } // library marker kkossev.deviceProfileLib, line 81
} // library marker kkossev.deviceProfileLib, line 82

boolean is2in1() { return getDeviceProfile().contains('TS0601_2IN1') }    // patch removed 05/29/2024 // library marker kkossev.deviceProfileLib, line 84

String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 86
Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 87
Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 88
//List<String> getDeviceProfilesMap()   { deviceProfilesV3 != null ? deviceProfilesV3.values().description as List<String> : deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 89

List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLib, line 91
    if (deviceProfilesV3 == null) { // library marker kkossev.deviceProfileLib, line 92
        if (deviceProfilesV2 == null) { return [] } // library marker kkossev.deviceProfileLib, line 93
        return deviceProfilesV2.values().description as List<String> // library marker kkossev.deviceProfileLib, line 94
    } // library marker kkossev.deviceProfileLib, line 95
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLib, line 96
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 97
        if ((profileMap.device?.isDepricated ?: false) != true) { // library marker kkossev.deviceProfileLib, line 98
            activeProfiles.add(profileMap.description ?: '---') // library marker kkossev.deviceProfileLib, line 99
        } // library marker kkossev.deviceProfileLib, line 100
    } // library marker kkossev.deviceProfileLib, line 101
    return activeProfiles // library marker kkossev.deviceProfileLib, line 102
} // library marker kkossev.deviceProfileLib, line 103


// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 106

/** // library marker kkossev.deviceProfileLib, line 108
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 109
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 110
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 111
 */ // library marker kkossev.deviceProfileLib, line 112
String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 113
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 114
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 115
    else { return null } // library marker kkossev.deviceProfileLib, line 116
} // library marker kkossev.deviceProfileLib, line 117

/** // library marker kkossev.deviceProfileLib, line 119
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 120
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 121
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 122
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 123
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 124
 */ // library marker kkossev.deviceProfileLib, line 125
Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 126
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 127
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 128
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 129
    def preference // library marker kkossev.deviceProfileLib, line 130
    try { // library marker kkossev.deviceProfileLib, line 131
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 132
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 133
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 134
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 135
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 136
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 137
        } // library marker kkossev.deviceProfileLib, line 138
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 139
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 140
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 141
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 142
        } // library marker kkossev.deviceProfileLib, line 143
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 144
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 145
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 146
        } // library marker kkossev.deviceProfileLib, line 147
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 148
    } catch (e) { // library marker kkossev.deviceProfileLib, line 149
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 150
        return [:] // library marker kkossev.deviceProfileLib, line 151
    } // library marker kkossev.deviceProfileLib, line 152
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 153
    return foundMap // library marker kkossev.deviceProfileLib, line 154
} // library marker kkossev.deviceProfileLib, line 155

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 157
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 158
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 159
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 160
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 161
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 162
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 163
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 164
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 165
            return foundMap // library marker kkossev.deviceProfileLib, line 166
        } // library marker kkossev.deviceProfileLib, line 167
    } // library marker kkossev.deviceProfileLib, line 168
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 169
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 170
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 171
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 172
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 173
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 174
            return foundMap // library marker kkossev.deviceProfileLib, line 175
        } // library marker kkossev.deviceProfileLib, line 176
    } // library marker kkossev.deviceProfileLib, line 177
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 178
    return [:] // library marker kkossev.deviceProfileLib, line 179
} // library marker kkossev.deviceProfileLib, line 180

/** // library marker kkossev.deviceProfileLib, line 182
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 183
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 184
 */ // library marker kkossev.deviceProfileLib, line 185
void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 186
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 187
    Map preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 188
    if (preferences == null || preferences.isEmpty()) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 189
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 190
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 191
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 192
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 193
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 194
            return // continue // library marker kkossev.deviceProfileLib, line 195
        } // library marker kkossev.deviceProfileLib, line 196
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 197
        if (parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 198
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 199
        if (parMap.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 200
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 201
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 202
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 203
    } // library marker kkossev.deviceProfileLib, line 204
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 205
} // library marker kkossev.deviceProfileLib, line 206

/** // library marker kkossev.deviceProfileLib, line 208
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 209
 * // library marker kkossev.deviceProfileLib, line 210
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 211
 */ // library marker kkossev.deviceProfileLib, line 212
List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 213
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 214
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 215
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 216
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 217
    } // library marker kkossev.deviceProfileLib, line 218
    return validPars // library marker kkossev.deviceProfileLib, line 219
} // library marker kkossev.deviceProfileLib, line 220

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 222
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 223
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 224
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 225
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 226
    def scaledValue // library marker kkossev.deviceProfileLib, line 227
    if (value == null) { // library marker kkossev.deviceProfileLib, line 228
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 229
        return null // library marker kkossev.deviceProfileLib, line 230
    } // library marker kkossev.deviceProfileLib, line 231
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 232
        case 'number' : // library marker kkossev.deviceProfileLib, line 233
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 234
            break // library marker kkossev.deviceProfileLib, line 235
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 236
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 237
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 238
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 239
            } // library marker kkossev.deviceProfileLib, line 240
            break // library marker kkossev.deviceProfileLib, line 241
        case 'bool' : // library marker kkossev.deviceProfileLib, line 242
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 243
            break // library marker kkossev.deviceProfileLib, line 244
        case 'enum' : // library marker kkossev.deviceProfileLib, line 245
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 246
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 247
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 248
                return null // library marker kkossev.deviceProfileLib, line 249
            } // library marker kkossev.deviceProfileLib, line 250
            scaledValue = value // library marker kkossev.deviceProfileLib, line 251
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 252
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 253
            } // library marker kkossev.deviceProfileLib, line 254
            break // library marker kkossev.deviceProfileLib, line 255
        default : // library marker kkossev.deviceProfileLib, line 256
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 257
            return null // library marker kkossev.deviceProfileLib, line 258
    } // library marker kkossev.deviceProfileLib, line 259
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 260
    return scaledValue // library marker kkossev.deviceProfileLib, line 261
} // library marker kkossev.deviceProfileLib, line 262

// called from customUpdated() method in the custom driver // library marker kkossev.deviceProfileLib, line 264
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 265
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 266
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 267
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 268
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 269
        return // library marker kkossev.deviceProfileLib, line 270
    } // library marker kkossev.deviceProfileLib, line 271
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 272
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 273
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 274
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 275
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 276
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 277
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 278
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 279
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 280
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 281
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 282
                // scale the value // library marker kkossev.deviceProfileLib, line 283
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 284
            } // library marker kkossev.deviceProfileLib, line 285
            if (preferenceValue != null) {  // library marker kkossev.deviceProfileLib, line 286
                setPar(name, preferenceValue.toString())  // library marker kkossev.deviceProfileLib, line 287
            } // library marker kkossev.deviceProfileLib, line 288
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLib, line 289
        } // library marker kkossev.deviceProfileLib, line 290
        else { logDebug "warning: couldn't find map for preference ${name}" ; return } // library marker kkossev.deviceProfileLib, line 291
    } // library marker kkossev.deviceProfileLib, line 292
    return // library marker kkossev.deviceProfileLib, line 293
} // library marker kkossev.deviceProfileLib, line 294

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 296
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 297
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 298
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 299
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 300
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 301
} // library marker kkossev.deviceProfileLib, line 302
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 303
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 304
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 305
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 306
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 307
} // library marker kkossev.deviceProfileLib, line 308
int invert(int val) { // library marker kkossev.deviceProfileLib, line 309
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 310
    else { return val } // library marker kkossev.deviceProfileLib, line 311
} // library marker kkossev.deviceProfileLib, line 312

// called from setPar and sendAttribite methods for non-Tuya DPs // library marker kkossev.deviceProfileLib, line 314
List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 315
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 316
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 317
    Map map = [:] // library marker kkossev.deviceProfileLib, line 318
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 319
    try { // library marker kkossev.deviceProfileLib, line 320
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 321
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 322
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 323
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 324
    } // library marker kkossev.deviceProfileLib, line 325
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLib, line 326
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 327
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 328
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLib, line 329
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 330
    } // library marker kkossev.deviceProfileLib, line 331
    if (map.mfgCode != null && map.mfgCode != '') { // library marker kkossev.deviceProfileLib, line 332
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 333
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mfgCode, delay = 50) // library marker kkossev.deviceProfileLib, line 334
    } // library marker kkossev.deviceProfileLib, line 335
    else { // library marker kkossev.deviceProfileLib, line 336
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 50) // library marker kkossev.deviceProfileLib, line 337
    } // library marker kkossev.deviceProfileLib, line 338
    return cmds // library marker kkossev.deviceProfileLib, line 339
} // library marker kkossev.deviceProfileLib, line 340

/** // library marker kkossev.deviceProfileLib, line 342
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 343
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 344
 * // library marker kkossev.deviceProfileLib, line 345
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 346
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 347
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 348
 */ // library marker kkossev.deviceProfileLib, line 349
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 350
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 351
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 352
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 353
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 354
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 355
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 356
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 357
        case 'number' : // library marker kkossev.deviceProfileLib, line 358
            // TODO - negative values ! // library marker kkossev.deviceProfileLib, line 359
            // TODO - better conversion to integer! // library marker kkossev.deviceProfileLib, line 360
            value = safeToInt(val, 0) // library marker kkossev.deviceProfileLib, line 361
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 362
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 363
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 364
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 365
            } // library marker kkossev.deviceProfileLib, line 366
            else { // library marker kkossev.deviceProfileLib, line 367
                scaledValue = value // library marker kkossev.deviceProfileLib, line 368
            } // library marker kkossev.deviceProfileLib, line 369
            break // library marker kkossev.deviceProfileLib, line 370

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 372
            value = safeToDouble(val, 0.0) // library marker kkossev.deviceProfileLib, line 373
            // scale the value // library marker kkossev.deviceProfileLib, line 374
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 375
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 376
            } // library marker kkossev.deviceProfileLib, line 377
            else { // library marker kkossev.deviceProfileLib, line 378
                scaledValue = value // library marker kkossev.deviceProfileLib, line 379
            } // library marker kkossev.deviceProfileLib, line 380
            break // library marker kkossev.deviceProfileLib, line 381

        case 'bool' : // library marker kkossev.deviceProfileLib, line 383
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 384
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 385
            else { // library marker kkossev.deviceProfileLib, line 386
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 387
                return null // library marker kkossev.deviceProfileLib, line 388
            } // library marker kkossev.deviceProfileLib, line 389
            break // library marker kkossev.deviceProfileLib, line 390
        case 'enum' : // library marker kkossev.deviceProfileLib, line 391
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 392
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 393
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 394
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 395
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 396
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 397
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 398
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 399
            } // library marker kkossev.deviceProfileLib, line 400
            else { // library marker kkossev.deviceProfileLib, line 401
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 402
            } // library marker kkossev.deviceProfileLib, line 403
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 404
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 405
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 406
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 407
                // find the key for the value // library marker kkossev.deviceProfileLib, line 408
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 409
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 410
                if (key == null) { // library marker kkossev.deviceProfileLib, line 411
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 412
                    return null // library marker kkossev.deviceProfileLib, line 413
                } // library marker kkossev.deviceProfileLib, line 414
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 415
            //return null // library marker kkossev.deviceProfileLib, line 416
            } // library marker kkossev.deviceProfileLib, line 417
            break // library marker kkossev.deviceProfileLib, line 418
        default : // library marker kkossev.deviceProfileLib, line 419
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 420
            return null // library marker kkossev.deviceProfileLib, line 421
    } // library marker kkossev.deviceProfileLib, line 422
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 423
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 424
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 425
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 426
        return null // library marker kkossev.deviceProfileLib, line 427
    } // library marker kkossev.deviceProfileLib, line 428
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 429
    return scaledValue // library marker kkossev.deviceProfileLib, line 430
} // library marker kkossev.deviceProfileLib, line 431

/** // library marker kkossev.deviceProfileLib, line 433
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 434
 * // library marker kkossev.deviceProfileLib, line 435
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 436
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 437
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 438
 */ // library marker kkossev.deviceProfileLib, line 439
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 440
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 441
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 442
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 443
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 444
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 445
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 446
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 447
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 448
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 449
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 450
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 451
    if (scaledValue == null) { logInfo "setPar: invalid parameter ${par} value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 452

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 454
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 455
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 456
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 457
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 458
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 459
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 460
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 461
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 462
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 463
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 464
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 465
            return true // library marker kkossev.deviceProfileLib, line 466
        } // library marker kkossev.deviceProfileLib, line 467
        else { // library marker kkossev.deviceProfileLib, line 468
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 469
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 470
        } // library marker kkossev.deviceProfileLib, line 471
    } // library marker kkossev.deviceProfileLib, line 472
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 473
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 474
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 475
        def valMiscType // library marker kkossev.deviceProfileLib, line 476
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 477
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 478
            // find the key for the value // library marker kkossev.deviceProfileLib, line 479
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 480
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 481
            if (key == null) { // library marker kkossev.deviceProfileLib, line 482
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 483
                return false // library marker kkossev.deviceProfileLib, line 484
            } // library marker kkossev.deviceProfileLib, line 485
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 486
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 487
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 488
        } // library marker kkossev.deviceProfileLib, line 489
        else { // library marker kkossev.deviceProfileLib, line 490
            valMiscType = val // library marker kkossev.deviceProfileLib, line 491
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 492
        } // library marker kkossev.deviceProfileLib, line 493
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 494
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 495
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 496
        return true // library marker kkossev.deviceProfileLib, line 497
    } // library marker kkossev.deviceProfileLib, line 498

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 500
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 501

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 503
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 504
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 505
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 506
        // Tuya DP // library marker kkossev.deviceProfileLib, line 507
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 508
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 509
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 510
            return false // library marker kkossev.deviceProfileLib, line 511
        } // library marker kkossev.deviceProfileLib, line 512
        else { // library marker kkossev.deviceProfileLib, line 513
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 514
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 515
            return false // library marker kkossev.deviceProfileLib, line 516
        } // library marker kkossev.deviceProfileLib, line 517
    } // library marker kkossev.deviceProfileLib, line 518
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 519
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 520
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 521
        int signedIntScaled = convertSignedInts(scaledValue, dpMap) // library marker kkossev.deviceProfileLib, line 522
        cmds = zclWriteAttribute(dpMap, signedIntScaled) // library marker kkossev.deviceProfileLib, line 523
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 524
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 525
            return false // library marker kkossev.deviceProfileLib, line 526
        } // library marker kkossev.deviceProfileLib, line 527
    } // library marker kkossev.deviceProfileLib, line 528
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 529
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 530
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 531
    return true // library marker kkossev.deviceProfileLib, line 532
} // library marker kkossev.deviceProfileLib, line 533

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 535
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 536
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 537
List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 538
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 539
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 540
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 541
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 542
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 543
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 544
        return [] // library marker kkossev.deviceProfileLib, line 545
    } // library marker kkossev.deviceProfileLib, line 546
    String dpType // library marker kkossev.deviceProfileLib, line 547
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 548
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 549
    } // library marker kkossev.deviceProfileLib, line 550
    else { // library marker kkossev.deviceProfileLib, line 551
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 552
    } // library marker kkossev.deviceProfileLib, line 553
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 554
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 555
        return [] // library marker kkossev.deviceProfileLib, line 556
    } // library marker kkossev.deviceProfileLib, line 557
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 558
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 559
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 560
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 561
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 562
    } // library marker kkossev.deviceProfileLib, line 563
    else { // library marker kkossev.deviceProfileLib, line 564
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 565
    } // library marker kkossev.deviceProfileLib, line 566
    return cmds // library marker kkossev.deviceProfileLib, line 567
} // library marker kkossev.deviceProfileLib, line 568

int convertSignedInts(int val, Map dpMap) { // library marker kkossev.deviceProfileLib, line 570
    if (dpMap.dt == '0x28') { // library marker kkossev.deviceProfileLib, line 571
        if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 572
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 573
    } // library marker kkossev.deviceProfileLib, line 574
    else if (dpMap.dt == '0x29') { // library marker kkossev.deviceProfileLib, line 575
        if (val > 32767) { return (val as int) - 65536 } // library marker kkossev.deviceProfileLib, line 576
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 577
    } // library marker kkossev.deviceProfileLib, line 578
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 579
} // library marker kkossev.deviceProfileLib, line 580

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 582
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 583
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 584
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 585
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 586
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "DEVICE.preferences is empty!" ; return false } // library marker kkossev.deviceProfileLib, line 587

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 589
    l//log.trace "sendAttribute: dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 590
    if (dpMap == null || dpMap.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 591
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 592
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 593
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 594
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 595
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 596
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 597
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 598
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 599
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 600
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 601
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 602
        try { // library marker kkossev.deviceProfileLib, line 603
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 604
        } // library marker kkossev.deviceProfileLib, line 605
        catch (e) { // library marker kkossev.deviceProfileLib, line 606
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 607
            return false // library marker kkossev.deviceProfileLib, line 608
        } // library marker kkossev.deviceProfileLib, line 609
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 610
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 611
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 612
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 613
            return true // library marker kkossev.deviceProfileLib, line 614
        } // library marker kkossev.deviceProfileLib, line 615
        else { // library marker kkossev.deviceProfileLib, line 616
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 617
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 618
        } // library marker kkossev.deviceProfileLib, line 619
    } // library marker kkossev.deviceProfileLib, line 620
    else { // library marker kkossev.deviceProfileLib, line 621
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 622
    } // library marker kkossev.deviceProfileLib, line 623
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 624
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 625
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 626
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 627
        // patch !! // library marker kkossev.deviceProfileLib, line 628
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 629
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 630
        } // library marker kkossev.deviceProfileLib, line 631
        else { // library marker kkossev.deviceProfileLib, line 632
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 633
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 634
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 635
        } // library marker kkossev.deviceProfileLib, line 636
        return true // library marker kkossev.deviceProfileLib, line 637
    } // library marker kkossev.deviceProfileLib, line 638
    else { // library marker kkossev.deviceProfileLib, line 639
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 640
    } // library marker kkossev.deviceProfileLib, line 641
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 642
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 643
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 644
    try { // library marker kkossev.deviceProfileLib, line 645
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 646
    } // library marker kkossev.deviceProfileLib, line 647
    catch (e) { // library marker kkossev.deviceProfileLib, line 648
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 649
        return false // library marker kkossev.deviceProfileLib, line 650
    } // library marker kkossev.deviceProfileLib, line 651
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 652
        // Tuya DP // library marker kkossev.deviceProfileLib, line 653
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 654
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 655
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 656
            return false // library marker kkossev.deviceProfileLib, line 657
        } // library marker kkossev.deviceProfileLib, line 658
        else { // library marker kkossev.deviceProfileLib, line 659
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 660
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 661
            return true // library marker kkossev.deviceProfileLib, line 662
        } // library marker kkossev.deviceProfileLib, line 663
    } // library marker kkossev.deviceProfileLib, line 664
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 665
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 666
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 667
    } // library marker kkossev.deviceProfileLib, line 668
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 669
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 670
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 671
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 672
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 673
            return false // library marker kkossev.deviceProfileLib, line 674
        } // library marker kkossev.deviceProfileLib, line 675
    } // library marker kkossev.deviceProfileLib, line 676
    else { // library marker kkossev.deviceProfileLib, line 677
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 678
        return false // library marker kkossev.deviceProfileLib, line 679
    } // library marker kkossev.deviceProfileLib, line 680
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 681
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 682
    return true // library marker kkossev.deviceProfileLib, line 683
} // library marker kkossev.deviceProfileLib, line 684

/** // library marker kkossev.deviceProfileLib, line 686
 * Sends a command to the device. // library marker kkossev.deviceProfileLib, line 687
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 688
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 689
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 690
 */ // library marker kkossev.deviceProfileLib, line 691
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 692
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 693
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 694
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 695
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 696
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 697
    if (supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 698
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 699
        return false // library marker kkossev.deviceProfileLib, line 700
    } // library marker kkossev.deviceProfileLib, line 701
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 702
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 703
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 704
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 705
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 706
        return false // library marker kkossev.deviceProfileLib, line 707
    } // library marker kkossev.deviceProfileLib, line 708
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 709
    def func, funcResult // library marker kkossev.deviceProfileLib, line 710
    try { // library marker kkossev.deviceProfileLib, line 711
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 712
        if (val != null && val != '') { // library marker kkossev.deviceProfileLib, line 713
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 714
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 715
        } // library marker kkossev.deviceProfileLib, line 716
        else { // library marker kkossev.deviceProfileLib, line 717
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 718
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 719
        } // library marker kkossev.deviceProfileLib, line 720
    }  // library marker kkossev.deviceProfileLib, line 721
    catch (e) { // library marker kkossev.deviceProfileLib, line 722
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 723
        return false // library marker kkossev.deviceProfileLib, line 724
    }  // library marker kkossev.deviceProfileLib, line 725
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 726
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 727
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 728
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 729
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 730
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 731
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 732
        } // library marker kkossev.deviceProfileLib, line 733
    } else { // library marker kkossev.deviceProfileLib, line 734
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 735
        return false // library marker kkossev.deviceProfileLib, line 736
    } // library marker kkossev.deviceProfileLib, line 737
    /* // library marker kkossev.deviceProfileLib, line 738
    cmds = funcResult // library marker kkossev.deviceProfileLib, line 739
    if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 740
        sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 741
    } // library marker kkossev.deviceProfileLib, line 742
    */ // library marker kkossev.deviceProfileLib, line 743
    return true // library marker kkossev.deviceProfileLib, line 744
} // library marker kkossev.deviceProfileLib, line 745

/** // library marker kkossev.deviceProfileLib, line 747
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 748
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 749
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 750
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 751
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 752
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 753
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 754
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 755
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 756
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 757
 */ // library marker kkossev.deviceProfileLib, line 758
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 759
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 760
    Map input = [:] // library marker kkossev.deviceProfileLib, line 761
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 762
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 763
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 764
    def preference // library marker kkossev.deviceProfileLib, line 765
    try { preference = DEVICE?.preferences["$param"] } // library marker kkossev.deviceProfileLib, line 766
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 767
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 768
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } } // library marker kkossev.deviceProfileLib, line 769
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 770
    // TODO - check if this is neccessary? isTuyaDP is not defined! // library marker kkossev.deviceProfileLib, line 771
    try { isTuyaDP = preference.isNumber() } // library marker kkossev.deviceProfileLib, line 772
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 773
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 774
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 775
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 776
    if (foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 777
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 778
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) { // library marker kkossev.deviceProfileLib, line 779
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" } // library marker kkossev.deviceProfileLib, line 780
        return [:] // library marker kkossev.deviceProfileLib, line 781
    } // library marker kkossev.deviceProfileLib, line 782
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 783
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 784
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 785
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 786
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 787
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 788
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 789
        } // library marker kkossev.deviceProfileLib, line 790
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 791
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 792
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 793
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 794
            } // library marker kkossev.deviceProfileLib, line 795
        } // library marker kkossev.deviceProfileLib, line 796
    } // library marker kkossev.deviceProfileLib, line 797
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 798
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 799
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 800
    }/* // library marker kkossev.deviceProfileLib, line 801
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 802
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 803
    }*/ // library marker kkossev.deviceProfileLib, line 804
    else { // library marker kkossev.deviceProfileLib, line 805
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 806
        return [:] // library marker kkossev.deviceProfileLib, line 807
    } // library marker kkossev.deviceProfileLib, line 808
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 809
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 810
    } // library marker kkossev.deviceProfileLib, line 811
    return input // library marker kkossev.deviceProfileLib, line 812
} // library marker kkossev.deviceProfileLib, line 813

/** // library marker kkossev.deviceProfileLib, line 815
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 816
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 817
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 818
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 819
 */ // library marker kkossev.deviceProfileLib, line 820
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 821
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 822
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 823
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 824
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 825
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 826
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 827
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 828
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 829
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 830
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 831
            } // library marker kkossev.deviceProfileLib, line 832
        } // library marker kkossev.deviceProfileLib, line 833
    } // library marker kkossev.deviceProfileLib, line 834
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 835
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 836
    } // library marker kkossev.deviceProfileLib, line 837
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 838
} // library marker kkossev.deviceProfileLib, line 839

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 841
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 842
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 843
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 844
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 845
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 846
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 847
    } // library marker kkossev.deviceProfileLib, line 848
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 849
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 850
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 851
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 852
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 853
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 854
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 855
    } else { // library marker kkossev.deviceProfileLib, line 856
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 857
    } // library marker kkossev.deviceProfileLib, line 858
} // library marker kkossev.deviceProfileLib, line 859

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLib, line 861
List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLib, line 862
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLib, line 863
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 864
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLib, line 865
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 866
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 867
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 868
            if (k != null) { // library marker kkossev.deviceProfileLib, line 869
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 870
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 871
                if (map != null) { // library marker kkossev.deviceProfileLib, line 872
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 873
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 874
                } // library marker kkossev.deviceProfileLib, line 875
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 876
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 877
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 878
                } // library marker kkossev.deviceProfileLib, line 879
            } // library marker kkossev.deviceProfileLib, line 880
        } // library marker kkossev.deviceProfileLib, line 881
    } // library marker kkossev.deviceProfileLib, line 882
    return cmds // library marker kkossev.deviceProfileLib, line 883
} // library marker kkossev.deviceProfileLib, line 884

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 886
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 887
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 888
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 889
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 890
    return cmds // library marker kkossev.deviceProfileLib, line 891
} // library marker kkossev.deviceProfileLib, line 892

// TODO ! // library marker kkossev.deviceProfileLib, line 894
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 895
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 896
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 897
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 898
    return cmds // library marker kkossev.deviceProfileLib, line 899
} // library marker kkossev.deviceProfileLib, line 900

// TODO // library marker kkossev.deviceProfileLib, line 902
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 903
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 904
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 905
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 906
    return cmds // library marker kkossev.deviceProfileLib, line 907
} // library marker kkossev.deviceProfileLib, line 908

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 910
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 911
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 912
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 913
    } // library marker kkossev.deviceProfileLib, line 914
} // library marker kkossev.deviceProfileLib, line 915

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 917
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 918
} // library marker kkossev.deviceProfileLib, line 919

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 921

// // library marker kkossev.deviceProfileLib, line 923
// called from parse() // library marker kkossev.deviceProfileLib, line 924
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 925
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 926
// // library marker kkossev.deviceProfileLib, line 927
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 928
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 929
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 930
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 931
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 932
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 933
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 934
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 935
} // library marker kkossev.deviceProfileLib, line 936

// // library marker kkossev.deviceProfileLib, line 938
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 939
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 940
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 941
// // library marker kkossev.deviceProfileLib, line 942
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 943
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 944
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 945
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 946
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 947
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 948
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 949
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 950
} // library marker kkossev.deviceProfileLib, line 951

// all DPs are spammy - sent periodically! // library marker kkossev.deviceProfileLib, line 953
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 954
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 955
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 956
    return isSpammy // library marker kkossev.deviceProfileLib, line 957
} // library marker kkossev.deviceProfileLib, line 958

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 960
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 961
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 962
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 963
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 964
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 965
    } // library marker kkossev.deviceProfileLib, line 966
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 967
} // library marker kkossev.deviceProfileLib, line 968

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 970
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 971
    boolean isEqual // library marker kkossev.deviceProfileLib, line 972
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 973
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 974
    } // library marker kkossev.deviceProfileLib, line 975
    else { // library marker kkossev.deviceProfileLib, line 976
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 977
    } // library marker kkossev.deviceProfileLib, line 978
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 979
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 980
} // library marker kkossev.deviceProfileLib, line 981

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 983
    Double convertedValue // library marker kkossev.deviceProfileLib, line 984
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 985
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 986
    } // library marker kkossev.deviceProfileLib, line 987
    else { // library marker kkossev.deviceProfileLib, line 988
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 989
    } // library marker kkossev.deviceProfileLib, line 990
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 991
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 992
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 993
} // library marker kkossev.deviceProfileLib, line 994

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 996
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 997
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 998
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 999
    def convertedValue // library marker kkossev.deviceProfileLib, line 1000
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1001
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1002
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1003
    } // library marker kkossev.deviceProfileLib, line 1004
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1005
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1006
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1007
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1008
            isEqual = false // library marker kkossev.deviceProfileLib, line 1009
        } // library marker kkossev.deviceProfileLib, line 1010
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1011
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1012
        } // library marker kkossev.deviceProfileLib, line 1013
    } // library marker kkossev.deviceProfileLib, line 1014
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1015
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1016
} // library marker kkossev.deviceProfileLib, line 1017

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1019
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1020
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1021
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1022
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1023
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1024
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1025
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1026
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1027
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1028
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1029
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1030
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1031
            break // library marker kkossev.deviceProfileLib, line 1032
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1033
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1034
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1035
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1036
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1037
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1038
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1039
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1040
            } // library marker kkossev.deviceProfileLib, line 1041
            else { // library marker kkossev.deviceProfileLib, line 1042
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1043
            } // library marker kkossev.deviceProfileLib, line 1044
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1045
            break // library marker kkossev.deviceProfileLib, line 1046
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1047
        case 'number' : // library marker kkossev.deviceProfileLib, line 1048
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1049
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1050
            break // library marker kkossev.deviceProfileLib, line 1051
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1052
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1053
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1054
            break // library marker kkossev.deviceProfileLib, line 1055
        default : // library marker kkossev.deviceProfileLib, line 1056
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1057
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1058
    } // library marker kkossev.deviceProfileLib, line 1059
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1060
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1061
    } // library marker kkossev.deviceProfileLib, line 1062
    // // library marker kkossev.deviceProfileLib, line 1063
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1064
} // library marker kkossev.deviceProfileLib, line 1065

// // library marker kkossev.deviceProfileLib, line 1067
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1068
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1069
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1070
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1071
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1072
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1073
// // library marker kkossev.deviceProfileLib, line 1074
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1075
// // library marker kkossev.deviceProfileLib, line 1076
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1077
// // library marker kkossev.deviceProfileLib, line 1078
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1079
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1080
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1081
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1082
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1083
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1084
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1085
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1086
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1087
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1088
            break // library marker kkossev.deviceProfileLib, line 1089
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1090
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1091
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1092
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1093
            logTrace "latestEvent is dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1094
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1095
            if (dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1096
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1097
            } // library marker kkossev.deviceProfileLib, line 1098
            else { // library marker kkossev.deviceProfileLib, line 1099
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1100
            } // library marker kkossev.deviceProfileLib, line 1101
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1102
            break // library marker kkossev.deviceProfileLib, line 1103
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1104
        case 'number' : // library marker kkossev.deviceProfileLib, line 1105
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1106
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1107
            break // library marker kkossev.deviceProfileLib, line 1108
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1109
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1110
            break // library marker kkossev.deviceProfileLib, line 1111
        default : // library marker kkossev.deviceProfileLib, line 1112
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1113
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1114
    } // library marker kkossev.deviceProfileLib, line 1115
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1116
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1117
} // library marker kkossev.deviceProfileLib, line 1118

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1120
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1121
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1122
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1123
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1124
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1125
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1126
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1127
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1128
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1129
    } // library marker kkossev.deviceProfileLib, line 1130
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1131
    try { // library marker kkossev.deviceProfileLib, line 1132
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1133
    } // library marker kkossev.deviceProfileLib, line 1134
    catch (e) { // library marker kkossev.deviceProfileLib, line 1135
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1136
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1137
    } // library marker kkossev.deviceProfileLib, line 1138
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1139
    return fncmd // library marker kkossev.deviceProfileLib, line 1140
} // library marker kkossev.deviceProfileLib, line 1141

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1143
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1144
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1145
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1146
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1147
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1148
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1149

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1151
    if (attribMap == null || attribMap.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1152

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1154
    int value // library marker kkossev.deviceProfileLib, line 1155
    try { // library marker kkossev.deviceProfileLib, line 1156
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1157
    } // library marker kkossev.deviceProfileLib, line 1158
    catch (e) { // library marker kkossev.deviceProfileLib, line 1159
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1160
        return false // library marker kkossev.deviceProfileLib, line 1161
    } // library marker kkossev.deviceProfileLib, line 1162
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1163
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1164
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1165
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1166
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1167
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1168
        return false // library marker kkossev.deviceProfileLib, line 1169
    } // library marker kkossev.deviceProfileLib, line 1170
    value = convertSignedInts(value, foundItem) // library marker kkossev.deviceProfileLib, line 1171
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1172
} // library marker kkossev.deviceProfileLib, line 1173

/** // library marker kkossev.deviceProfileLib, line 1175
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1176
 * // library marker kkossev.deviceProfileLib, line 1177
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1178
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1179
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1180
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1181
 * // library marker kkossev.deviceProfileLib, line 1182
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1183
 */ // library marker kkossev.deviceProfileLib, line 1184
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1185
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1186
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1187
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1188
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1189

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1191
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1192

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1194
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1195
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1196
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1197
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1198
        return false // library marker kkossev.deviceProfileLib, line 1199
    } // library marker kkossev.deviceProfileLib, line 1200
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1201
} // library marker kkossev.deviceProfileLib, line 1202

/* // library marker kkossev.deviceProfileLib, line 1204
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1205
 */ // library marker kkossev.deviceProfileLib, line 1206
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1207
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1208
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1209
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1210
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1211
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1212
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1213
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1214
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1215
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1216
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1217
        } // library marker kkossev.deviceProfileLib, line 1218
    } // library marker kkossev.deviceProfileLib, line 1219
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1220

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1222
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1223
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1224
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1225
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1226
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1227
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1228
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1229
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1230
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1231
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1232
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1233
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1234
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1235
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1236
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1237
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1238

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1240
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1241
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1242
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1243
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1244
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1245
            if (settings.logEnable) { logInfo "${descText }" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1246
        } // library marker kkossev.deviceProfileLib, line 1247
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1248
    } // library marker kkossev.deviceProfileLib, line 1249

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1251
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1252
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1253
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1254
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1255
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1256
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1257
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1258
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1259
            } // library marker kkossev.deviceProfileLib, line 1260
        } // library marker kkossev.deviceProfileLib, line 1261
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1262
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1263
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1264
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1265
            } // library marker kkossev.deviceProfileLib, line 1266
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1267
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1268
            try { // library marker kkossev.deviceProfileLib, line 1269
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1270
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1271
            } // library marker kkossev.deviceProfileLib, line 1272
            catch (e) { // library marker kkossev.deviceProfileLib, line 1273
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1274
            } // library marker kkossev.deviceProfileLib, line 1275
        } // library marker kkossev.deviceProfileLib, line 1276
    } // library marker kkossev.deviceProfileLib, line 1277
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1278
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1279
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1280
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1281
    } // library marker kkossev.deviceProfileLib, line 1282

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1284
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1285
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1286
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1287
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1288
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1289
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1290
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1291
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1292
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1293
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
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0 allignment; added capability 'IlluminanceMeasurement'; added illuminanceRefresh() // library marker kkossev.illuminanceLib, line 21
 * // library marker kkossev.illuminanceLib, line 22
 *                                   TODO: illum threshold not working! // library marker kkossev.illuminanceLib, line 23
 *                                   TODO: check illuminanceInitializeVars() and illuminanceProcessTuyaDP() usage // library marker kkossev.illuminanceLib, line 24
*/ // library marker kkossev.illuminanceLib, line 25

static String illuminanceLibVersion()   { '3.2.0' } // library marker kkossev.illuminanceLib, line 27
static String illuminanceLibStamp() { '2024/05/28 1:33 PM' } // library marker kkossev.illuminanceLib, line 28

metadata { // library marker kkossev.illuminanceLib, line 30
    capability 'IlluminanceMeasurement' // library marker kkossev.illuminanceLib, line 31
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

List<String> illuminanceRefresh() { // library marker kkossev.illuminanceLib, line 128
    List<String> cmds = [] // library marker kkossev.illuminanceLib, line 129
    cmds = zigbee.readAttribute(0x0400, 0x0000, [:], delay = 200) // illuminance // library marker kkossev.illuminanceLib, line 130
    return cmds // library marker kkossev.illuminanceLib, line 131
} // library marker kkossev.illuminanceLib, line 132

// ~~~~~ end include (168) kkossev.illuminanceLib ~~~~~
