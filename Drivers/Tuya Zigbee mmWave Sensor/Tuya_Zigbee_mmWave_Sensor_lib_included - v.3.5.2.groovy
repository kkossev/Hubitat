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
 * ver. 3.2.3  2024-06-21 kkossev  - added _TZE204_nbkshs6k and _TZE204_dapwryy7 @CheesyPotato 
 * ver. 3.2.4  2024-07-31 kkossev  - using motionLib.groovy; added batteryLib; added _TZE200_jkbljri7; TS0601 _TZE204_dapwryy7 all DPs defined; added Wenzhi TS0601 _TZE204_laokfqwu
 * ver. 3.3.0  2024-09-15 kkossev  - deviceProfileLib 3.3.3 ; added _TZE204_ex3rcdha; added almost all DPs of the most spammy ZY-M100 radars into spammyDPsToNotTrace filter; fixed powerSource for _TZE200_2aaelwxk (battery); added queryAllTuyaDP for refresh; 
 * ver. 3.3.1  2024-09-28 kkossev  - added TS0601 _TZE204_ya4ft0w4 (Wenzhi); motionOrNot bug fix; 'Disable Distance Reports' preference (for this device only!)
 * ver. 3.3.2  2024-10-07 kkossev  - TS0225 _TZE200_hl0ss9oa new fingerprint; added switch to disable the spammy distanceReporting for _TZE204_iaeejhvf _TZE200_dtzziy1e _TZE204_dtzziy1e _TZE200_clrdrnya _TZE204_clrdrnya (LeapMMW/Wenzhi)
 * ver. 3.3.3  2024-10-19 kkossev  - humanMotionState 'small_move' and 'large_move' replaced by 'small' and 'large'; the soft 'ignoreDistance' preference is shown only for these old devices that don't have the true distance reporting disabling switch.
 * ver. 3.3.4  2024-11-17 kkossev  - TS0225 _TZE200_2aaelwxk power source changed to 'dc'; bug fixed for 'humanMotionState' attribite - 'presence' is now changed to 'present'.
 * ver. 3.3.5  2024-11-30 kkossev  - added TS0601 _TZ6210_duv6fhwt (Heiman presence sesnor); added TS0601 _TZE204_uxllnywp @Televisi
 * ver. 3.3.6  2025-01-04 kkossev  - changed TS0601 _TZE204_ya4ft0w4 dp102 scale to 10 - tnx @Jon7sky 
 * ver. 3.4.0  2025-02-02 kkossev  - deviceProfilesV3 optimizations; adding add TS0225 _TZ321C_fkzihax8 into LEAPMMW new device profile @Wilson; changed TS0601 _TZE204_ya4ft0w4 dp102 scale back to 1  
 * ver. 3.4.1  2025-02-09 kkossev  - TS0601 _TZE200_kb5noeto added motionDetectionMode; 
 * ver. 3.4.2  2025-03-24 kkossev  - healthCheck by pinging the device; updateRxStats() replaced with inline code; deviceProfilesV3 optimizations; 
 * ver. 3.5.0  2025-04-08 kkossev  - urgent fix for java.lang.CloneNotSupportedException in common.lib for HE platform version 2.4.1.155
 * ver. 3.5.1  2025-04-25 kkossev  - HE platfrom version 2.4.1.x decimal preferences range patch/workaround.
 * ver. 3.5.2  2025-06-15 kkossev  - added _TZE284_4qznlkbu. (_TZE284_iadro9bf is NOT supported by this driver!);
 *                                   
 *                                   TODO: check why ignoreDistance prefrence is not shown when forcebly changing the deviceProfile
 *                                   TODO: Optimize the deviceProfilesV3 !! (reached max size ... :( )  
 *                                   TODO: add https://www.leapmmw.com/ mmWave radars : https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/blob/main/mtd085_convertor_240628.js https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/blob/main/mtd085_z2m1.4.0.js 
 *                                   TODO: update the top post in the forum with the new models mmWave radars
 *                                   TODO: add the state tuyaDps as in the 4-in-1 driver!
 *                                   TODO: enable the OWON radar configuration : ['0x0406':'bind']
 *                                   TODO: Motion reset to inactive after 43648s - convert to H:M:S
 *                                   TODO: Black Square Radar validateAndFixPreferences: map not found for preference indicatorLight
 *                                   TODO: command for black radar LED
 *                                   TODO: TS0225_2AAELWXK_RADAR  dont see an attribute as mentioned that shows the distance at which the motion was detected. - https://community.hubitat.com/t/the-new-tuya-human-presence-sensors-ts0225-tze200-hl0ss9oa-tze200-2aaelwxk-have-actually-5-8ghz-modules-inside/122283/294?u=kkossev
 *                                   TODO: TS0225_2AAELWXK_RADAR led setting not working - https://community.hubitat.com/t/the-new-tuya-human-presence-sensors-ts0225-tze200-hl0ss9oa-tze200-2aaelwxk-have-actually-5-8ghz-modules-inside/122283/294?u=kkossev
 *                                   TODO: TS0225_HL0SS9OA_RADAR - add presets
 *                                   TODO: humanMotionState - add preference: enum "disabled", "enabled", "enabled w/ timing" ...; add delayed event
*/

static String version() { "3.5.2" }
static String timeStamp() {"2025/06/15 5:07 PM"}

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
        name: 'Tuya Zigbee mmWave Sensor ver 3.5.2',
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
        attribute 'distance', 'number'                          // Tuya Radar
        attribute 'unacknowledgedTime', 'number'                // AIR models
        attribute 'occupiedTime', 'number'                      // BlackSquareRadar & LINPTECH // was existance_time
        attribute 'absenceTime', 'number'                       // BlackSquareRadar only
        attribute 'keepTime', 'enum', ['10 seconds', '30 seconds', '60 seconds', '120 seconds']
        attribute 'motionDetectionSensitivity', 'number'
        attribute 'motionDetectionDistance', 'decimal'          // changed 05/11/2024 - was 'number'
        attribute 'motionDetectionMode', 'enum', ['0 - onlyPIR', '1 - PIRandRadar', '2 - onlyRadar']    // added 07/24/2024

        attribute 'radarSensitivity', 'number'
        attribute 'staticDetectionSensitivity', 'number'        // added 10/29/2023
        attribute 'staticDetectionDistance', 'decimal'          // added 05/1/2024
        attribute 'smallMotionDetectionSensitivity', 'number'   // added 04/25/2024
        attribute 'detectionDelay', 'decimal'
        attribute 'fadingTime', 'decimal'
        attribute 'minimumDistance', 'decimal'
        attribute 'maximumDistance', 'decimal'
        attribute 'radarStatus', 'enum', ['checking', 'check_success', 'check_failure', 'others', 'comm_fault', 'radar_fault']
        attribute 'humanMotionState', 'enum', ['none', 'moving', 'small', 'stationary', 'static', 'present', 'peaceful', 'large']
        attribute 'radarAlarmMode', 'enum',   ['0 - arm', '1 - off', '2 - alarm', '3 - doorbell']
        attribute 'radarAlarmVolume', 'enum', ['0 - low', '1 - medium', '2 - high', '3 - mute']
        attribute 'illumState', 'enum', ['dark', 'light', 'unknown']
        attribute 'ledIndicator', 'number'
        attribute 'WARNING', 'string'
        attribute 'tamper', 'enum', ['clear', 'detected']

        command 'sendCommand', [
            [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']],
            [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']]
        ]
        command 'setPar', [
                [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']],
                [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']]
        ]
        if (_DEBUG) {
            command 'test', [[name: "test", type: "STRING", description: "test", defaultValue : ""]] 
            // testParse is defined in the common library
            // tuyaTest is defined in the common library
        }
        // itterate through all the figerprints and add them on the fly
        deviceProfilesV3.each { profileName, profileMap ->
            if (profileMap.fingerprints != null) {
                profileMap.fingerprints.each {
                    fingerprintIt(profileMap, it) // changed 01/25/2025
                }
            }
        }        
    }

    preferences {
        input(name: 'info',    type: 'hidden', title: "<a href='https://github.com/kkossev/Hubitat/wiki/Tuya-Zigbee-mmWave-Sensor' target='_blank'><i>For more info, click on this link to visit the WiKi page</i></a>")
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_DEBUG_LOGGING, description: '<i>Turns on debug logging for 24 hours.</i>'
        // 10/19/2024 - luxThreshold and illuminanceCoeff are defined in illuminanceLib.groovy
        if (('DistanceMeasurement' in DEVICE?.capabilities) && settings?.distanceReporting == null) {   // 10/19/2024 - show the soft 'ignoreDistance' switch only for these old devices that don't have the true distance reporting disabling switch!
            input(name: 'ignoreDistance', type: 'bool', title: '<b>Ignore distance reports</b>', description: 'If not used, ignore the distance reports received every 1 second!', defaultValue: true)
        }
    }
}


@Field static final Map deviceProfilesV3 = [
    'TS0601_TUYA_RADAR'   : [        // isZY_M100Radar()        // very spammy devices!
            description   : 'Tuya Human Presence mmWave Radar ZY-M100',
            device        : [powerSource: 'dc', /*isSpammy:true, */ignoreIAS:true], // sends all DPs periodically!
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'2', 'detectionDelay':'101', 'fadingTime':'102', 'minimumDistance':'3', 'maximumDistance':'4'],
            commands      : [resetStats:''],
           
            defaultFingerprint: [
                 profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ztc6ggyl', deviceJoinName: 'Tuya ZigBee Breath Presence Sensor ZY-M100'
            ],
            fingerprints  : [
                [model:'TS0601', manufacturer:'_TZE200_ztc6ggyl', deviceJoinName: 'Tuya ZigBee Breath Presence Sensor ZY-M100'],       // KK
                [model:'TS0601', manufacturer:'_TZE204_ztc6ggyl'],                     // KK
                [model:'TS0601', manufacturer:'_TZE200_ikvncluo', deviceJoinName: 'Moes TuyaHuman Presence Detector Radar 2 in 1'],    // jw970065; very spammy1
                [model:'TS0601', manufacturer:'_TZE200_lyetpprm'],
                [model:'TS0601', manufacturer:'_TZE200_wukb7rhc', deviceJoinName: 'Moes Smart Human Presence Detector'],               // https://www.moeshouse.com/collections/smart-sensor-security/products/smart-zigbee-human-presence-detector-pir-mmwave-radar-detection-sensor-ceiling-mount
                [model:'TS0601', manufacturer:'_TZE200_jva8ink8', deviceJoinName: 'AUBESS Human Presence Detector'],                   // https://www.aliexpress.com/item/1005004262109070.html
                [model:'TS0601', manufacturer:'_TZE200_mrf6vtua'],                     // not tested
                [model:'TS0601', manufacturer:'_TZE200_ar0slwnd'],                     // not tested
                [model:'TS0601', manufacturer:'_TZE200_sfiy5tfs'],                     // not tested
                [model:'TS0601', manufacturer:'_TZE200_holel4dk'],                     // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars/92441/280?u=kkossev
                [model:'TS0601', manufacturer:'_TZE200_xpq2rzhq'],                     // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/432?u=kkossev
                [model:'TS0601', manufacturer:'_TZE204_qasjif9e'],                     //
                [model:'TS0601', manufacturer:'_TZE204_xsm7l9xa'],                     //
                [model:'TS0601', manufacturer:'_TZE204_ztqnh5cg'],                     // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/1054?u=kkossev
                [model:'TS0601', manufacturer:'_TZE204_fwondbzy', deviceJoinName: 'Moes Smart Human Presence Detector']                // https://www.aliexpress.us/item/3256803962192457.html https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/1054?u=kkossev
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
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [9],
            spammyDPsToNotTrace : [2, 3, 4, 6, 9, 101, 102, 103, 104], // added the illuminance as a spammyDP - 05/30/10114
    ],
    
    'TS0601_KAPVNNLK_RADAR'   : [        // 24GHz spammy radar w/ battery backup - no illuminance!
            description   : 'Tuya TS0601_KAPVNNLK 24GHz Radar',                     // https://www.amazon.com/dp/B0CDRBX1CQ?psc=1&ref=ppx_yo2ov_dt_b_product_details  // https://www.aliexpress.com/item/1005005834366702.html  // https://github.com/Koenkk/zigbee2mqtt/issues/18632
            device        : [powerSource: 'dc'],     // https://www.aliexpress.com/item/1005005858609756.html     // https://www.aliexpress.com/item/1005005946786561.html    // https://www.aliexpress.com/item/1005005946931559.html
            capabilities  : ['MotionSensor': true, 'DistanceMeasurement':true, 'HumanMotionState':true],
            preferences   : ['radarSensitivity':'15',  'maximumDistance':'13', 'smallMotionDetectionSensitivity':'16', 'fadingTime':'12',],
            commands      : [resetStats:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_kapvnnlk', deviceJoinName: 'Tuya 24 GHz Human Presence Detector NEW'],           // https://community.hubitat.com/t/tuya-smart-human-presence-sensor-micromotion-detect-human-motion-detector-zigbee-ts0601-tze204-sxm7l9xa/111612/71?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_kyhbrfyl', deviceJoinName: 'Tuya 24 GHz Human Presence Detector NEW']           // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/1042?u=kkossev
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',              type:'enum',    rw: 'ro', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:11,  name:'humanMotionState',    type:'enum',    rw: 'ro', min:0,   max:2,     defVal:'0', map:[0:'none', 1:'small', 2:'large'],  description:'Human motion state'],        // "none", "small_move", "large_move"]
                [dp:12,  name:'fadingTime',          type:'number',  rw: 'rw', min:3,   max:600,   defVal:60,   scale:1,   unit:'seconds',    title:'<b>Fading time</b>',                description:'<i>Presence inactivity delay timer</i>'],                                  // aka 'nobody time'
                [dp:13,  name:'maximumDistance',     type:'decimal',/* dt: '03',*/ rw: 'rw', min:1.5, max:6.0,   defVal:4.0, step:75, scale:100, unit:'meters',     title:'<b>Maximum detection distance</b>', description:'<i>Maximum (far) detection distance</i>'],  // aka 'Large motion detection distance'
                [dp:15,  name:'radarSensitivity',    type:'number',  rw: 'rw', min:0,   max:7 ,    defVal:5,    scale:1,   unit:'',          title:'<b>Radar sensitivity</b>',          description:'<i>Large motion detection sensitivity of the radar</i>'],
                [dp:16 , name:'smallMotionDetectionSensitivity', type:'number',  rw: 'rw', min:0,   max:7,  defVal:5,     scale:1,   unit:'', title:'<b>Small motion sensitivity</b>',   description:'<i>Small motion detection sensitivity</i>'],
                [dp:19,  name:'distance',            type:'decimal', rw: 'ro', min:0.0, max:10.0,  defVal:0.0,  scale:100, unit:'meters',     title:'<b>Distance</b>',                   description:'<i>detected distance</i>'],
                [dp:101, name:'battery',             type:'number',  rw: 'ro', min:0,   max:100,   defVal:100,  scale:1,   unit:'%',          title:'<b>Battery level</b>',              description:'<i>Battery level</i>']
            ],
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [19], spammyDPsToNotTrace : [19],
    ],
    
    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/f277bef2f84d50aea70c25261db0c2ded84b7396/src/devices/tuya.ts#L4164
    'TS0601_RADAR_MIR-HE200-TY'   : [        // Human presence sensor radar 'MIR-HE200-TY' - illuminance, presence, occupancy, motion_speed, motion_direction, radar_sensitivity, radar_scene ('default', 'area', 'toilet', 'bedroom', 'parlour', 'office', 'hotel')
            description   : 'Tuya Human Presence Sensor MIR-HE200-TY',
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true],
            preferences   : ['radarSensitivity':'2', 'tumbleSwitch':'105', 'tumbleAlarmTime':'106', 'fallSensitivity':'118'],
            commands      : [resetStats:''],
            defaultFingerprint : [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_lu01t0zl', deviceJoinName: 'Tuya Human presence sensor with fall function'],
            fingerprints  : [
                [model:'TS0601', manufacturer:'_TZE200_vrfecyku', deviceJoinName: 'Tuya Human presence sensor MIR-HE200-TY'],
                [model:'TS0601', manufacturer:'_TZE200_lu01t0zl'],
                [model:'TS0601', manufacturer:'_TZE200_ypprdwsl'],
                [model:'TS0601', manufacturer:'_TZE200_jkbljri7']
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
            refresh: ['queryAllTuyaDP'],
    ],
    
    'TS0601_BLACK_SQUARE_RADAR'   : [        // // 24GHz Big Black Square Radar w/ annoying LED    // EXTREMLY SPAMMY !!!
            description   : 'Tuya Black Square Radar',
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor':true],
            preferences   : ['indicatorLight':'103'],
            commands      : [resetStats:''],
            defaultFingerprint : [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_0u3bj3rc', deviceJoinName: '24GHz Black Square Human Presence Radar w/ LED'],
            fingerprints  : [
                [model:'TS0601', manufacturer:'_TZE200_0u3bj3rc'],
                [model:'TS0601', manufacturer:'_TZE200_v6ossqfy'],
                [model:'TS0601', manufacturer:'_TZE200_mx6u6l4y']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',         type:'enum',   rw: 'ro', min:0, max:1,    defVal: '0', map:[0:'inactive', 1:'active'],     description:'Presence'],
                [dp:101, name:'occupiedTime', type:'number', rw: 'ro', min:0, max:9999, scale:1,   unit:'minutes',    description:'Shows the presence duration in minutes'],
                [dp:102, name:'absenceTime',     type:'number', rw: 'ro', min:0, max:9999, scale:1,   unit:'minutes',    description:'Shows the duration of the absence in minutes'],
                [dp:103, name:'indicatorLight', type:'enum',   rw: 'rw', min:0, max:1,    defVal: '0', map:[0:'OFF', 1:'ON'],  title:'<b>Indicator Light</b>', description:'<i>Turns the onboard LED on or off</i>']
            ],
            spammyDPsToIgnore : [103, 102, 101],            // we don't need to know the LED status every 4 seconds! Skip also all other spammy DPs except motion
            spammyDPsToNotTrace : [1, 101, 102, 103],     // very spammy device - 4 packates are sent every 4 seconds!
    ],
    
    'TS0601_YXZBRB58_RADAR'   : [        // Seller: shenzhenshixiangchuangyeshiyey Manufacturer: Shenzhen Eysltime Intelligent LTD    Item model number: YXZBRB58  isYXZBRB58radar()
            description   : 'Tuya YXZBRB58 Radar',
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],    // https://github.com/Koenkk/zigbee2mqtt/issues/18318
            preferences   : ['radarSensitivity':'2', 'detectionDelay':'103', 'fadingTime':'102', 'minimumDistance':'3', 'maximumDistance':'4'],
            commands      : [resetStats:''],
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
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [105], spammyDPsToNotTrace : [105],    // https://www.aliexpress.com/item/1005005764168560.html
    ],
    
    // isSXM7L9XAradar()                                                // https://github.com/dresden-elektronik/deconz-rest-plugin/issues/6998#issuecomment-1612113340
    'TS0601_SXM7L9XA_RADAR'   : [                                       // https://gist.github.com/Koenkk/9295fc8afcc65f36027f9ab4d319ce64
            description   : 'Tuya Human Presence Detector SXM7L9XA',    // https://github.com/zigpy/zha-device-handlers/issues/2378#issuecomment-1558777494  // https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/tree/main
            device        : [powerSource: 'dc'],                        // https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/blob/main/wenzhi_tuya_M100-230908.js
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'106', 'detectionDelay':'111', 'fadingTime':'110', 'minimumDistance':'108', 'maximumDistance':'107'],
            commands      : [resetStats:''],
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
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [109], spammyDPsToNotTrace : [109],
    ],
    
    
    // isIJXVKHD0radar()  '24G MmWave radar human presence motion sensor'
    'TS0601_IJXVKHD0_RADAR'   : [
            description   : 'Tuya Human Presence Detector IJXVKHD0',    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/5acadaf16b0e85c1a8401223ddcae3d31ce970eb/src/devices/tuya.ts#L5747
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'106', 'staticDetectionSensitivity':'111', 'fadingTime':'110', 'maximumDistance':'107'],
            commands      : [resetStats:''],
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
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [109, 9], // dp 9 test
            spammyDPsToNotTrace : [109, 104],   // illuminance reporting is extremly spammy !
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
            device        : [powerSource: 'dc'],                        // https://www.aliexpress.com/item/1005005677110270.html
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'101', 'presence_time':'12', 'detectionDelay':'102', 'fadingTime':'116', 'minimumDistance': '111', 'maximumDistance':'112'],
            commands      : [resetStats:''],
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
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [19], spammyDPsToNotTrace : [19],
    ],
    
    
    // the new 5.8 GHz radar w/ humanMotionState and a lot of configuration options, 'not-so-spammy' !   - pedestal mount form-factor
    'TS0225_HL0SS9OA_RADAR'   : [
            description   : 'Tuya TS0225_HL0SS9OA Radar',           // https://www.aliexpress.com/item/1005005761971083.html
            device        : [powerSource: 'dc', ignoreIAS: true],   // ignore the illuminance reports from the IAS cluster
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true],
            preferences   : ['presenceKeepTime':'12', 'ledIndicator':'24', 'radarAlarmMode':'105', 'radarAlarmVolume':'102', 'radarAlarmTime':'101', \
                             'motionFalseDetection':'112', 'motionDetectionSensitivity':'15', 'motionMinimumDistance':'106', 'motionDetectionDistance':'13', \
                             'smallMotionDetectionSensitivity':'16', 'smallMotionMinimumDistance':'107', 'smallMotionDetectionDistance':'14', \
                             'breatheFalseDetection':'115', 'staticDetectionSensitivity':'104', 'staticDetectionMinimumDistance':'108', 'staticDetectionDistance':'103' \
                            ],
            commands      : [resetSettings:'', moveSelfTest:'', smallMoveSelfTest:'', breatheSelfTest:'', resetStats:'', initialize:'', updateAllPreferences: '', printFingerprints:'', printPreferences:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,E002,EF00', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZE200_hl0ss9oa', deviceJoinName: 'Tuya TS0225_HL0SS9OA Human Presence Detector'],           // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,E002,EF00,0400', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZE200_hl0ss9oa', deviceJoinName: 'Tuya TS0225_HL0SS9OA Human Presence Detector']       // https://community.hubitat.com/t/release-tuya-zigbee-mmwave-sensors-code-moved-from-the-tuya-4-in-1-driver/137410/195?u=kkossev
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
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [], spammyDPsToNotTrace : [11],
    ],
    
    
    // the new 5.8GHz radar w/ humanMotionState and a lot of configuration options, 'not-so-spammy' !   - wall mount form-factor    is2AAELWXKradar()
    'TS0225_2AAELWXK_RADAR'   : [                                     // https://github.com/Koenkk/zigbee2mqtt/issues/18612 // ZG-205Z   https://github.com/Koenkk/zigbee-herdsman-converters/blob/38bf79304292c380dc8366966aaefb71ca0b03da/src/devices/tuya.ts#L4793
            description   : 'Tuya TS0225_2AAELWXK 5.8 GHz Radar',     // https://community.hubitat.com/t/the-new-tuya-24ghz-human-presence-sensor-ts0225-tze200-hl0ss9oa-finally-a-good-one/122283/72?u=kkossev
            device        : [powerSource: 'dc', ignoreIAS: true],     // ignore the illuminance reports from the IAS cluster
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true],
            preferences   : ['presenceKeepTime':'102', 'ledIndicator':'107', 'radarAlarmMode':'117', 'radarAlarmVolume':'116', 'radarAlarmTime':'115', \
                             'motionFalseDetection':'103', 'motionDetectionSensitivity':'2', 'motionMinimumDistance':'3', 'motionDetectionDistance':'4', \
                             'smallMotionDetectionSensitivity':'105', 'smallMotionMinimumDistance':'110', 'smallMotionDetectionDistance':'104', \
                             'breatheFalseDetection':'113', 'staticDetectionSensitivity':'109', 'staticDetectionDistance':'108' \
                            ],
            commands      : [resetSettings:'', resetStats:''],
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
            refresh: ['queryAllTuyaDP'],
    ],

    // leapMMW radars  https://amzn.to/4jo0Bsa 
    'TS0225_LEAPMMW_RADAR'   : [
            description   : 'Tuya TS0225 leapMMW radar',                // ? https://github.com/falkenbt/zigbee-herdsman-converters/blob/0a5eddc2ea74e95e0401be67703be40103362a65/src/devices/tuya.ts#L13470-L13545 
            device        : [powerSource: 'dc', ignoreIAS: false],      // occupancy (motion) is reported from the IAS cluster ?
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true],
            preferences   : ['fadingTime':'103', 'ledIndicator':'114', 'minimumDistance':'116', 'maximumDistance':'117',  \

                            ],
            commands      : [resetSettings:'', resetStats:'', printFingerprints:'', printPreferences:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,0500', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZ321C_fkzihax8', deviceJoinName: 'WenzhiIoT Smart Motion Sensor'],       // https://amzn.to/4jo0Bsa
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,0500', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZ321C_4slreunp', deviceJoinName: 'LeapMMW MTD095-ZB Human presence sensor'],       // https://amzn.to/4jo0Bsa
            ],
            tuyaDPs:        [
                //[dp:1,   name:'motion',                          type:'enum',    rw: 'ro', min:0,    max:1,    defVal:'0',   scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],

                [dp:103, name:'fadingTime',             type:'decimal', rw: 'rw', min:5.0, max:7200.0, defVal:30.0, scale:1,   unit:'seconds',  title:'<b>Fading time</b>', description:'<i>Presence inactivity delay timer</i>'],
                [dp:107, name:'illuminance',            type:'number',  rw: 'ro', scale:10,  unit:'lx', description:'Illuminance'],

                [dp:114, name:'ledIndicator',           type:'enum',    rw: 'rw', min:0,   max:1,     defVal:'0', map:[0:'0 - OFF', 1:'1 - ON'], title:'<b>LED indicator</b>', description:'<i>LED indicator mode</i>'],
                [dp:115, name:'radarSensitivity',       type:'number',  rw: 'rw', min:10,  max:100,   defVal:50,  scale:1,   unit:'%',  title:'<b>Radar sensitivity</b>',       description:'<i>Radar sensitivity</i>'],
                [dp:116, name:'minimumDistance',        type:'decimal', rw: 'rw', min:0.0, max:8.0,   defVal:0.5, scale:100,  unit:'meters',   title:'<b>Minimum distance</b>',   description:'Min detection distance'],
                [dp:117, name:'maximumDistance',        type:'decimal', rw: 'rw', min:0.0, max:8.0,   defVal:6.0, scale:100,  unit:'meters',   title:'<b>Maximum distance</b>',   description:'<i>Max detection distance</i>'],
                [dp:119, name:'distance',               type:'decimal', rw: 'ro', min:0.0, max:10.0,  scale:100,  unit:'meters',    description:'Distance'],


/*
                [dp:2,   name:'motionDetectionSensitivity',      type:'number',  rw: 'rw', min:0,    max:10,   defVal:7,     scale:1,   unit:'',         title:'<b>Motion Detection Sensitivity</b>',       description:'<i>Large motion detection sensitivity</i>'],           // dt: "UINT8" aka Motionless Detection Sensitivity
                [dp:3,   name:'motionMinimumDistance',           type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:0.5,   scale:100, unit:'meters',    title:'<b>Motion minimum distance</b>',            description:'<i>Motion minimum distance</i>'],
                [dp:4,   name:'motionDetectionDistance',         type:'decimal', rw: 'rw', min:0.0,  max:10.0, defVal:6.0,   scale:100, unit:'meters',    title:'<b>Motion Detection Distance</b>',          description:'<i>Large motion detection distance, meters</i>'], //dt: "UINT16"
                [dp:101, name:'humanMotionState',                type:'enum',    rw: 'ro', min:0,    max:3,    defVal:'0',  map:[0:'none', 1:'large', 2:'small', 3:'static'],       description:'Human motion state'],
                [dp:102, name:'presenceKeepTime',                type:'number',  rw: 'rw', min:5,    max:3600, defVal:30,    scale:1,   unit:'seconds',   title:'<b>Presence keep time</b>',                 description:'<i>Presence keep time</i>'],
                [dp:103, name:'motionFalseDetection',            type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'],     title:'<b>Motion false detection</b>',     description:'<i>Disable/enable Motion false detection</i>'],
                [dp:104, name:'smallMotionDetectionDistance',    type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:5.0,   scale:100, unit:'meters',    title:'<b>Small motion detection distance</b>',    description:'<i>Small motion detection distance</i>'],
                [dp:105, name:'smallMotionDetectionSensitivity', type:'number',  rw: 'rw', min:0,    max:10 ,  defVal:7,     scale:1,   unit:'',         title:'<b>Small motion detection sensitivity</b>', description:'<i>Small motion detection sensitivity</i>'],
                [dp:108, name:'staticDetectionDistance',         type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:4.0,   scale:100, unit:'meters',    title:'<b>Static detection distance</b>',          description:'<i>Static detection distance</i>'],
                [dp:109, name:'staticDetectionSensitivity',      type:'number',  rw: 'rw', min:0,    max:10,   defVal:7,     scale:1,   unit:'',         title:'<b>Static Detection Sensitivity</b>',       description:'<i>Static detection sensitivity</i>'],                 //  dt: "UINT8", aka Motionless Detection Sensitivity
                [dp:110, name:'smallMotionMinimumDistance',      type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:0.5,   scale:100, unit:'meters',    title:'<b>Small Motion Minimum Distance</b>',      description:'<i>Small Motion Minimum Distance</i>'],
                //[dp:111, name:'staticDetectionMinimumDistance',  type:"decimal", rw: "rw", min:0.0,  max:6.0,   defVal:0.5,  scale:100, unit:"meters",    title:'<b>Static detection minimum distance</b>',  description:'<i>Static detection minimum distance</i>'],
*/                
            ],
            spammyDPsToIgnore : [119], spammyDPsToNotTrace : [119],
            refresh: ['queryAllTuyaDP'],
    ],
    
    // Battery powered ! 24 GHz + PIR Radar
    'TS0601_24GHZ_PIR_RADAR'   : [  //https://github.com/Koenkk/zigbee-herdsman-converters/blob/3a8832a8a3586356e7ba76bcd92ce3177f6b934e/src/devices/tuya.ts#L5730-L5762
            description   : 'Tuya TS0601_2AAELWXK 24 GHz + PIR Radar',
            device        : [powerSource: 'battery', ignoreIAS: true],     // ignore the illuminance reports from the IAS cluster
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true, 'Battery':true],
            preferences   : ['radarSensitivity':'123', 'staticDetectionSensitivity':'2',  'staticDetectionDistance':'4', 'fadingTime':'102', 'ledIndicator':'107', 'motionDetectionMode':'122'],
            commands      : [resetSettings:'', resetStats:''],
            fingerprints  : [                                          // reports illuminance and motion using clusters 0x400 and 0x500 !
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,0001,0400', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_2aaelwxk', deviceJoinName: 'Tuya 2AAELWXK 24 GHz + PIR Radar'], 
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,0001,0400', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_kb5noeto', deviceJoinName: 'Tuya KB5NOETO 24 GHz + PIR Radar']        // https://community.hubitat.com/t/beta-tuya-zigbee-mmwave-sensors-code-moved-from-the-tuya-4-in-1-driver/137410/41?u=kkossev 
                // https://www.aliexpress.us/item/3256806664768243.html ^^^ https://gist.github.com/vinzent/2cd645b848fd3b6a0c3e5762956ec89f ^^  https://doc.szalarm.com/zg-205Z/doc/ZG-204ZM.pdf 
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',                          type:'enum',    rw: 'ro', min:0,    max:1,     defVal:'0',   scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:2,   name:'staticDetectionSensitivity',      type:'number',  rw: 'rw', min:0,    max:10,    defVal:7,     scale:1,   unit:'',         title:'<b>Static Detection Sensitivity</b>',       description:'<i>Static detection sensitivity</i>'],                 //  dt: "UINT8", aka Motionless Detection Sensitivity
                // # "3":"Minimum detection distance", (near_detection, Integer, 0-1000, unit=cm, step=1) (NOT AVAILABLE IN TUYA SMART LIFE APP)
                [dp:4,   name:'staticDetectionDistance',         type:'decimal', rw: 'rw', min:0.0,  max:10.0,  defVal:5.0,   scale:100, unit:'meters',    title:'<b>Static detection distance</b>',          description:'<i>Static detection distance</i>'],
                [dp:101, name:'humanMotionState',                type:'enum',    rw: 'ro', min:0,    max:3,     defVal:'0',  map:[0:'none', 1:'moving', 2:'small', 3:'static'],       description:'Human motion state'],
                [dp:102, name:'fadingTime',                      type:'number',  rw: 'rw', min:0,    max:28800, defVal:30,    scale:1,   unit:'seconds',   title:'<b>Presence keep time</b>',                 description:'<i>Presence keep time</i>'],
                [dp:106, name:'illuminance',                     type:'number',  rw: 'ro', scale:10,  unit:'lx',        description:'Illuminance'],
                [dp:107, name:'ledIndicator',                    type:'enum',    rw: 'rw', min:0,    max:1,     defVal:'0',  map:[0:'0 - OFF', 1:'1 - ON'],               title:'<b>LED indicator</b>',              description:'<i>LED indicator mode</i>'],
                // # "112":"Reset setting", (reset_setting, Boolean)
                [dp:121, name:'battery',                         type:'number',  rw: 'ro', min:0,    max:100,   defVal:100,  scale:1,   unit:'%',          title:'<b>Battery level</b>',              description:'<i>Battery level</i>'],
                [dp:122, name:'motionDetectionMode',             type:'enum',    rw: 'rw', min:0,    max:2,     defVal:'1',  map:[0:'0 - onlyPIR', 1:'1 - PIRandRadar', 2:'2 - onlyRadar'],     title:'<b>Motion detection mode</b>',       description:'<i>Motion detection mode</i>'],
                [dp:123, name:'radarSensitivity',                type:'number',  rw: 'rw', min:1,   max:9,     defVal:5,     scale:1,    unit:'',        title:'<b>Motion Detection sensitivity</b>',     description:'<i>Motion detection sensitivity</i>'],  // motion_detection_sensitivity
                // # "124":"ver" (ver, Integer, 0-100, step=1) (NOT AVAILABLE IN TUYA SMART LIFE APP)
            ],
            refresh: ['queryAllTuyaDP'],
/*
 * TS0601 ZG-204ZM
 * _TZE200_kb5noeto
 * Works with HA 2024.11 - updated by @txip (Update 2) 
 * https://de.aliexpress.com/item/1005006174074799.html ("Color": Mmwave PIR)
 * https://github.com/13717033460/zigbee-herdsman-converters/blob/6c9cf1b0de836ec2172d569568d3c7fe75268958/src/devices/tuya.ts#L5730-L5762
 * https://www.zigbee2mqtt.io/devices/ZG-204ZM.html
 * https://smarthomescene.com/reviews/zigbee-battery-powered-presence-sensor-zg-204zm-review/
 * https://doc.szalarm.com/zg-205ZL/cntop_zigbee_sensor.js
 * https://github.com/Koenkk/zigbee2mqtt/issues/21919
*/

    ],

    //24 GHz Radar https://s.click.aliexpress.com/e/_DmlO3GH  (SZKOSTON) TS0601 _TZE204_uxllnywp model: 'RTC ZCZ03Z'
    'TS0601_24GHZ_UXLLNYWP_RADAR'   : [    // https://github.com/Koenkk/zigbee2mqtt/issues/22906#issuecomment-2194557546      https://github.com/krikkoo/zigbee-herdsman-converters/blob/c1e50113ff2e36a8504b313c6c9064b8956da011/src/devices/tuya.ts#L10368
            description   : 'Tuya 24GHz UXLLNYWP Radar',
            device        : [powerSource: 'dc', ignoreIAS: true],    // ignore the illuminance reports from the IAS cluster
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'111', 'minimumDistance':'108',  'maximumDistance':'107', 'fadingTime':'103', 'ledIndicator':'104'],
            commands      : [resetSettings:'', resetStats:'', initialize:'', printFingerprints:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_uxllnywp', deviceJoinName: 'Tuya 24GHz UXLLNYWP Radar'], 
            ],
            tuyaDPs:        [
                //[dp:1,   name:'motion',                          type:'enum',    rw: 'ro', min:0,    max:1,     defVal:'0',   scale:1,   map:[0:'inactive', 1:'active'], description:'<i>Presence state</i>'],
                // humanMotionState : ['none', 'moving', 'small', 'stationary', 'static', 'present', 'peaceful', 'large']
                [dp:1,   name:'humanMotionState',   preProc:'motionOrNotUXLLNYWP', type:'enum',    rw: 'ro', map:[0:'none', 1:'static', 2:'small', 3:'large', 4:'moving'], description:'Presence state'],
                [dp:101, name:'distance',                        type:'decimal', rw: 'ro', min:0.0,  max:10.0,  defVal:0.0, scale:100, unit:'meters',  description:'Distance'],
                [dp:102, name:'illuminance',                     type:'number',  rw: 'ro', scale:10, unit:'lx', description:'Illuminance'],  // "Light intensity"
                [dp:103, name:'fadingTime',                      type:'number',  rw: 'rw', min:1,    max:59,    defVal:30,  scale:1,   unit:'seconds', title:'<b>Presence keep time</b>', description:'<i>Presence keep time</i>'],         // "Hold delay"
                [dp:104, name:'ledIndicator',                    type:'enum',    rw: 'rw', min:0,    max:1,     defVal:'0', map:[0:'0 - OFF', 1:'1 - ON'],               title:'<b>LED indicator</b>',              description:'<i>LED indicator mode</i>'],  // "Indicator led"
                [dp:105, name:'noneDelayTimeMin',                type:'number',  rw: 'ro', scale:1,  unit:'minutes', description:'None Delay Time (min)'],
                [dp:106, name:'noneDelayTimeSec',                type:'number',  rw: 'ro', scale:1,  unit:'seconds', description:'None Delay Time (sec)'],
                [dp:107, name:'maximumDistance',                 type:'decimal', rw: 'rw', min:0.0,  max:8.40,  defVal:7.0, scale:100, unit:'meters',  title:'<b>Maximum distance</b>', description:'<i>Breath detection maximum distance</i>'],
                [dp:108, name:'minimumDistance',                 type:'decimal', rw: 'rw', min:0.0,  max:8.40,  defVal:0.1, scale:100, unit:'meters',  title:'<b>Minimum distance</b>', description:'<i>Breath detection minimum distance</i>'],
                [dp:111, name:'radarSensitivity',                type:'number',  rw: 'rw', min:1,    max:10,    defVal:5,   scale:1,   unit:'',         title:'<b>Motion Detection sensitivity</b>', description:'<i>Motion detection sensitivity</i>'],  // motion_detection_sensitivity
                [dp:112, name:'staticDetectionSensitivity',      type:'number',  rw: 'ro', min:1,    max:10,    defVal:5,   scale:1,   unit:'',         title:'<b>Static Detection sensitivity</b>', description:'<i>Static detection sensitivity</i>'],  // "Hold sensitivity"
                [dp:114, name:'factoryParameters',               type:'number',  rw: 'ro', scale:1,  description:'Factory Reset'],
                [dp:120, name:'debugCLI',                        type:'number',  rw: 'ro', min:0,    max:99999, defVal:0,   scale:1,   description:'<i>debug CLI</i>'],
            ],
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [101], spammyDPsToNotTrace : [101],
    ],

    // isSBYX0LM6radar()                                               // https://github.com/Koenkk/zigbee-herdsman-converters/issues/5930#issuecomment-1662456347
    'TS0601_SBYX0LM6_RADAR'   : [                                      // _TZE204_sbyx0lm6    TS0601   model: 'MTG075-ZB-RL', '5.8G Human presence sensor with relay',
            description   : 'Tuya Human Presence Detector SBYX0LM6',   // https://github.com/vit-um/hass/blob/main/zigbee2mqtt/tuya_h_pr.js  // https://github.com/Koenkk/zigbee-herdsman-converters/issues/5930      https://github.com/Koenkk/zigbee-herdsman-converters/issues/5930#issuecomment-1651270524
            device        : [powerSource: 'dc'],                       // https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/blob/main/ts0601_radar_X75-X25-230705.js
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'2', 'minimumDistance':'3', 'maximumDistance':'4', 'detectionDelay':'101', 'fadingTime':'102', 'entrySensitivity':'105', 'entryDistanceIndentation':'106', 'breakerMode':'107', \
                             'breakerStatus':'108', 'statusIndication':'109', 'illuminThreshold':'110', 'breakerPolarity':'111', 'blockTime':'112', 'distanceReporting':'116'
                            ],
            commands      : [resetSettings:''],
            defaultFingerprint : [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_sbyx0lm6', deviceJoinName: 'Tuya Human Presence Detector MTGxxx-ZB-xx'],
            fingerprints  : [
                [model:'TS0601', manufacturer:'_TZE204_sbyx0lm6', deviceJoinName: 'Tuya 5.8GHz Human Presence Detector MTG075-ZB-RL'],    // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
                [model:'TS0601', manufacturer:'_TZE200_sbyx0lm6', deviceJoinName: 'Tuya 5.8GHz Human Presence Detector MTG075-ZB-RL'],
                [model:'TS0601', manufacturer:'_TZE204_dtzziy1e', deviceJoinName: 'Tuya 24GHz Human Presence Detector MTG275-ZB-RL'],     // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
                [model:'TS0601', manufacturer:'_TZE200_dtzziy1e', deviceJoinName: 'Tuya 24GHz Human Presence Detector MTG275-ZB-RL'],     // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
                [model:'TS0601', manufacturer:'_TZE204_clrdrnya', deviceJoinName: 'Tuya Human Presence Detector MTG235-ZB-RL'],           // https://www.aliexpress.com/item/1005005865536713.html                  // https://github.com/Koenkk/zigbee2mqtt/issues/18677?notification_referrer_id=NT_kwDOAF5zfrI3NDQ1Mzc2NTAxOjYxODk5NTA
                [model:'TS0601', manufacturer:'_TZE204_iaeejhvf', deviceJoinName: 'Tuya Human Presence Detector MTG075-ZB2-RL'],
                [model:'TS0601', manufacturer:'_TZE204_mtoaryre', deviceJoinName: 'Tuya Human Presence Detector MTG035-ZB2-RL'],
                [model:'TS0601', manufacturer:'_TZE200_mp902om5', deviceJoinName: 'Tuya Human Presence Detector MTG075-ZB'],
                [model:'TS0601', manufacturer:'_TZE204_pfayrzcw', deviceJoinName: 'Tuya Human Presence Detector MTG035-ZB-RL'],
                [model:'TS0601', manufacturer:'_TZE284_4qznlkbu', deviceJoinName: 'Tuya 24Ghz Human Presence Detector QASZ24R'],
/*                
                [model:'TS0601', manufacturer:'_TZE200_clrdrnya', deviceJoinName: 'Tuya Human Presence Detector MTG235-ZB-RL'],
                [model:'TS0601', manufacturer:'_TZE204_cfcznfbz', deviceJoinName: 'Tuya Human Presence Detector MTG075-ZB2'],
                [model:'TS0601', manufacturer:'_TZE204_8s6jtscb', deviceJoinName: 'Tuya Human Presence Detector MTG035-ZB2'],
                [model:'TS0601', manufacturer:'_TZE204_rktkuel1', deviceJoinName: 'Tuya Human Presence Detector MTD065-ZB2'],
                [model:'TS0601', manufacturer:'_TZE204_mp902om5', deviceJoinName: 'Tuya Human Presence Detector MTG075-ZB'],
                [model:'TS0601', manufacturer:'_TZE200_w5y5slkq', deviceJoinName: 'Tuya Human Presence Detector MTG275-ZB'],
                [model:'TS0601', manufacturer:'_TZE204_w5y5slkq', deviceJoinName: 'Tuya Human Presence Detector MTG275-ZB'],
                [model:'TS0601', manufacturer:'_TZE200_xnaqu2pc', deviceJoinName: 'Tuya Human Presence Detector MTD065-ZB'],
                [model:'TS0601', manufacturer:'_TZE204_xnaqu2pc', deviceJoinName: 'Tuya Human Presence Detector MTD065-ZB'],
                [model:'TS0601', manufacturer:'_TZE200_wk7seszg', deviceJoinName: 'Tuya Human Presence Detector MTG235-ZB'],
                [model:'TS0601', manufacturer:'_TZE204_wk7seszg', deviceJoinName: 'Tuya Human Presence Detector MTG235-ZB'],
                [model:'TS0601', manufacturer:'_TZE200_0wfzahlw', deviceJoinName: 'Tuya Human Presence Detector MTD021-ZB'],
                [model:'TS0601', manufacturer:'_TZE204_0wfzahlw', deviceJoinName: 'Tuya Human Presence Detector MTD021-ZB'],
                [model:'TS0601', manufacturer:'_TZE200_pfayrzcw', deviceJoinName: 'Tuya Human Presence Detector MTG035-ZB-RL'],
                [model:'TS0601', manufacturer:'_TZE200_z4tzr0rg', deviceJoinName: 'Tuya Human Presence Detector MTG035-ZB'],
                [model:'TS0601', manufacturer:'_TZE204_z4tzr0rg', deviceJoinName: 'Tuya Human Presence Detector MTG035-ZB']
*/                
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
                [dp:116, name:'distanceReporting',  type:'enum',    rw: 'rw', min:0,    max:1,       defVal:'0',  map:[0:'disabled', 1:'enabled'], title:'<b>Distance Reports</b>', description:'Effectively disable the spammy distance reporting!<br>The recommended default value is <b>disabled</b>'],

            ],
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [9], spammyDPsToNotTrace : [9],
    ],

    // 
    'TS0601_7GCLUKJS_RADAR'   : [           // https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/blob/main/ZM10224gNEW2.2.js       // https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/blob/main/zmzn24g.NEW.js
            description   : 'Tuya Human Presence Detector 7GCLUKJS',        // https://github.com/sprut/Hub/issues/3062 (default values)
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true, 'HumanMotionState':true],
            preferences   : ['radarSensitivity':'2', 'staticDetectionSensitivity':'102', 'fadingTime':'105', 'minimumDistance':'3', 'maximumDistance':'4'],
            commands      : [resetStats:''],
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
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [9],
            spammyDPsToNotTrace : [9],
    ],
        
    'TS0601_YA4FT0W4_RADAR'   : [        //https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/blob/68468dc630f19fdbea826538eddfaeafd964a1be/M100-ya4ft0-V3-20240907.js#L14
            description   : 'Tuya Human Presence Detector YA4FT0W4',
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true, 'HumanMotionState':true],
            preferences   : ['radarSensitivity':'2', 'staticDetectionSensitivity':'102', 'fadingTime':'105', 'minimumDistance':'3', 'maximumDistance':'4', 'distanceReporting':'101'],
            commands      : [resetStats:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_ya4ft0w4', deviceJoinName: 'Tuya Human Presence Detector YA4FT0W4 ZY-M100-24GV372']
            ],
            tuyaDPs:        [   
                [dp:1,   name:'humanMotionState',   preProc:'motionOrNotYA4FT0W4', type:'enum',    rw: 'ro', map:[0:'none', 1:'present', 2:'moving', 3:'none'], description:'Presence state'],
                [dp:2,   name:'radarSensitivity',   type:'number',  rw: 'rw', min:1,    max:10,   defVal:5, title:'<b>Motion sensitivity</b>', description:'Radar motion sensitivity'],
                [dp:3,   name:'minimumDistance',    type:'decimal', rw: 'rw', min:0.0,  max:8.25, defVal:0.75, step:75, scale:100,  unit:'meters',   title:'<b>Minimum distance</b>',      description:'Shield range of the radar'],         // was shieldRange
                [dp:4,   name:'maximumDistance',    type:'decimal', rw: 'rw', min:0.75, max:9.00, defVal:6.00, step:75, scale:100,  unit:'meters',   title:'<b>Maximum distance</b>',      description:'Detection range of the radar'],      // was detectionRange
                [dp:9,   name:'distance',           type:'decimal', rw: 'ro', min:0.0,  max:10.0, scale:10, unit:'meters', description:'Target distance'],
                [dp:101, name:'distanceReporting',  type:'enum',    rw: 'rw', min:0,    max:1,       defVal:'0',  map:[0:'disabled', 1:'enabled'], title:'<b>Distance Reports</b>', description:'Effectively disable the spammy distance reporting!<br>The recommended default value is <b>disabled</b>'],
//                [dp:102, name:'staticDetectionSensitivity',   type:'number',  rw: 'rw', min:1, max:10, defVal:5, title:'<b>Static detection sensitivity</b>', description:'Presence sensitivity'],
                [dp:102, name:'staticDetectionSensitivity',   type:'decimal',  rw: 'rw', min:0.0, max:10.0, defVal:5.0, scale:1, title:'<b>Static detection sensitivity</b>', description:'Presence sensitivity'],
                [dp:103, name:'illuminance',        type:'number',  rw: 'ro', unit:'lx', description:'illuminance'],
                //[dp:104, name:'motion',             type:'enum',    rw: 'ro', map:[0:'inactive', 1:'active'], description:'Presence state'],
                // DP:104 is still sent by the device.... and is processed in this driver.
                [dp:105, name:'fadingTime',         type:'decimal', rw: 'rw', min:5,    max:15000, , defVal:15, unit:'seconds', title:'<b>Delay time</b>', description:'Delay (fading) time'],
                [dp:255, name:'unknownDp255',       type:'enum',  rw: 'ro', description:'unknownDp255'] // 0x00 | 0xFF  boolean?  target_dis_closest (dis_key)
            ],
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [9], spammyDPsToNotTrace : [9],
    ],
        
    'TS0601_LAOKFQWU_RADAR'   : [           // https://github.com/wzwenzhi/Wenzhi-ZigBee2mqtt/blob/d0e62c42726dca0c1a881d892129f3087c7d8bc7/wenzhi_tuya_M100_240704.js#L20
            description   : 'Tuya/Wenzhi Human Presence Detector LAOKFQWU WZ-M100',
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true, 'HumanMotionState':true],
            preferences   : ['radarSensitivity':'2', 'minimumDistance':'3', 'maximumDistance':'4', 'fadingTime':'106', 'detectionDelay':'105'/*, 'intervalTime':'104'*/],
            commands      : [resetStats:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_laokfqwu', deviceJoinName: 'Tuya/Wenzhi Human Presence Detector LAOKFQWU WZ-M100']
            ],
            tuyaDPs:        [   
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', defVal:'0', map:[0:'inactive', 1:'active'], description:'Presence state'],
                [dp:2,   name:'radarSensitivity',   type:'number',  rw: 'rw', min:1,    max:10,    defVal:5, title:'<b>Motion sensitivity</b>', description:'Radar motion sensitivity'],
                [dp:3,   name:'minimumDistance',    type:'decimal', rw: 'rw', min:0.0,  max:9.5,   defVal:0.1,  step:10, scale:100,  unit:'meters', title:'<b>Minimum distance</b>', description:'Minimum detection range'],
                [dp:4,   name:'maximumDistance',    type:'decimal', rw: 'rw', min:0.0,  max:9.5,   defVal:6.0,  step:10, scale:100,  unit:'meters', title:'<b>Maximum distance</b>', description:'Maximum detection range'],
                [dp:9,   name:'distance',           type:'decimal', rw: 'ro', defVal:0.0,  scale:100, unit:'meters', description:'Target distance'],
                [dp:103, name:'illuminance',        type:'number',  rw: 'ro', unit:'lx',  description:'illuminance'],
                [dp:104, name:'intervalTime',       type:'number',  rw: 'rw', min:1,    max:3600,  unit:'seconds', title:'<b<Interval time</b>', description:'Interval time'],
                [dp:105, name:'detectionDelay',     type:'decimal', rw: 'rw', min:0.0,  max:10.0,  defVal:0.5,  scale:10, unit:'seconds',  title:'<b>Detection Delay</b>',   description:'Presence detection delay time'],
                [dp:106, name:'fadingTime',         type:'decimal', rw: 'rw', min:0.5,  max:150.0, defVal:30.0, scale:10, step:5, unit:'seconds', title:'<b<Delay time</b>', description:'Presence timeout']
            ],
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [9], spammyDPsToNotTrace : [9],
    ],
        
    
    // isLINPTECHradar()
    'TS0225_LINPTECH_RADAR'   : [                                      // https://github.com/Koenkk/zigbee2mqtt/issues/18637
            description   : 'Tuya TS0225_LINPTECH 24GHz Radar',        // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/646?u=kkossev
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['fadingTime':'101', 'motionDetectionDistance':'0xE002:0xE00B', 'motionDetectionSensitivity':'0xE002:0xE004', 'staticDetectionSensitivity':'0xE002:0xE005', 'ledIndicator':'0xE002:0xE009'],
            fingerprints  : [                                          // https://www.amazon.com/dp/B0C7C6L66J?ref=ppx_yo2ov_dt_b_product_details&th=1
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,E002,4000,EF00,0500', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZ3218_awarhusb', deviceJoinName: 'Tuya TS0225_LINPTECH 24Ghz Human Presence Detector'],       // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,E002,4000,EF00,0500', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZ3218_t9ynfz4x', deviceJoinName: 'Tuya TS0225_LINPTECH 24Ghz Human Presence Detector']
            ],
            commands      : [resetStats:'', refresh:'', initialize:'', updateAllPreferences: '',resetPreferencesToDefaults:'', validateAndFixPreferences:''],
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
            refresh: ['queryAllTuyaDP'],
            configuration : [:]
    ],
    
/*    
    //  no-name 240V AC ceiling radar presence sensor
    'TS0225_EGNGMRZH_RADAR'   : [                                    // https://github.com/sprut/Hub/issues/2489
            description   : 'Tuya TS0225_EGNGMRZH 24GHz Radar',      // isEGNGMRZHradar()
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'101', 'presence_time':'12', 'detectionDelay':'102', 'fadingTime':'116', 'minimumDistance': '111', 'maximumDistance':'112'],
            commands      : [resetStats:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,,0500,1000,EF00,0003,0004,0008', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZFED8_egngmrzh', deviceJoinName: 'Tuya TS0225_EGNGMRZH 24Ghz Human Presence Detector']       // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
            ],
            // uses IAS for occupancy!
            tuyaDPs:        [
                [dp:101, name:'illuminance',        type:'number',  rw: 'ro', min:0,  max:10000, scale:1,   unit:'lx'],        // https://github.com/Koenkk/zigbee-herdsman-converters/issues/6001
                [dp:103, name:'distance',           type:'decimal', rw: 'ro', min:0.0,  max:10.0,  defVal:0.0, scale:10,  unit:'meters']
                // 09/15/2024 - no additional information can be found for this device ... :( 
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
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [103], spammyDPsToNotTrace : [103],
            configuration : ['battery': false]
    ],
*/    
    
    'TS0225_O7OE4N9A_RADAR'   : [                                       // Aubess Zigbee-Human Presence Detector, Smart PIR Human Body Sensor, Wifi Radar, Microwave Motion Sensors, Tuya, 1/24/5G
            description   : 'Tuya Human Presence Detector YENSYA2C',    // https://github.com/Koenkk/zigbee2mqtt/issues/20082#issuecomment-1856204828   // https://fr.aliexpress.com/item/1005006016522811.html
            device        : [powerSource: 'dc'],                        // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/926?u=kkossev
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'110', 'motionSensitivity':'114', 'stateLockDuration':'101', 'fadingTime':'116'],
            commands      : [resetStats:''],
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
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [182], spammyDPsToNotTrace : [182],
    ],
    
    
    'OWON_OCP305_RADAR'   : [
            description   : 'OWON OCP305 Radar',
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true, 'Battery': true],
            preferences   : [:],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0406', outClusters:'0003', model:'OCP305', manufacturer:'OWON']
            ],
            configuration : ['0x0406':'bind']
    ],
    
    // isSONOFF()
    'SONOFF_SNZB-06P_RADAR' : [
            description   : 'SONOFF SNZB-06P RADAR',
            device        : [powerSource: 'dc', isIAS:false, isSleepy:false],   // TODO: check if IAS is used and aooky ignoreIAS:true ?
            capabilities  : ['MotionSensor': true],
            preferences   : ['fadingTime':'0x0406:0x0020', 'radarSensitivity':'0x0406:0x0022', 'detectionDelay':'0x0406:0x0021'],
            commands      : [printFingerprints:'',resetStats:'', refresh:'', initialize:'', updateAllPreferences: '',resetPreferencesToDefaults:'', validateAndFixPreferences:''],
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
            configuration : ['0x0406':'bind', '0x0FC57':'bind'/*, "0xFC11":"bind"*/]
    ],

    'TS0601_MUVJRJR5_RADAR'   : [                                       // Zigbee side mounted human presence sensor 24Ghz      // ZN494622_01  // no illuminance
            description   : 'Tuya Human Presence Detector MUVJRJR5',    // https://s.click.aliexpress.com/e/_DDkMp7Z 
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': false, 'DistanceMeasurement':true],
            preferences   : ['radarSensitivity':'16', 'fadingTime':'103', 'maximumDistance':'13', 'ledIndicator':'101', 'powerSwitch':'102'],
            commands      : [resetStats:''],
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
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [19], spammyDPsToNotTrace : [19],
    ],

    'TS0601_NBKSHS6K_RADAR'   : [        //5GHz Tuya Thick White Square with Sqr Button model: 'ZY-M100-S_2'
            description   : '5GHz Tuya Generic White Square Basic',     // https://github.com/Koenkk/zigbee2mqtt/issues/23183
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true],
            preferences   : ["unknownDP12":"12"],
            commands      : [resetStats:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_nbkshs6k', deviceJoinName: '5GHz Tuya Generic White Square Basic']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',         type:'enum',   rw: 'ro', min:0, max:1,    defVal: '0', map:[0:'active', 1:'inactive'],     description:'Presence'],
                //[dp:12,   name:'unknownDP12',         type:'number',   rw: 'rw', min:0, max:9999,    defVal: 1,      description:'UnknownDP12'],
                // TODO
            ],
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [], spammyDPsToNotTrace : [],    
        ],

'TS0601_DAPWRYY7_RADAR'   : [        //5GHz Tuya Thick White Square with Sqr Button
            description   : '5GHz Tuya Thick White Square with Sqr Button',
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true, 'DistanceMeasurement':true, 'IlluminanceMeasurement': true],
            preferences   : ['fadingTime':'103', 'radarSensitivity':'116', 'minimumDistance':'108', 'maximumDistance':'107', 'ledIndicator':'104', staticDetectionDistance:'109', staticDetectionMinDistance:'110', smallMotionDetectionDistance:'114', smallMotionDetectionMinDistance:'115', smallMotionDetectionSensitivity:'117', staticDetectionSensitivity:'118'],
            commands      : [resetStats:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_dapwryy7', deviceJoinName: '5GHz Tuya Thick White Square with Sqr Button']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',          type:'enum',   rw: 'ro', min:0, max:4,    defVal: '0', map:[0:'inactive', 1:'presence', 2:'peaceful', 3:'smallMovement', 4:'active' /*4:'largeMovement'*/],     description:'Presence'],
                // TODO - the above is actually the humanState !    // https://github.com/Burki24/zigbee-herdsman-converters/blob/37495d997f4ae50811491a2b9dc7d578afb52229/src/devices/tuya.ts#L10816
                [dp:101, name:'distance',        type:'decimal', rw: 'ro', min:0.0, max:9999.0, scale:100,   unit:'m',    description:'Distance'],
                [dp:102, name:'illuminance',     type:'number',  rw: 'ro', scale:1,    unit:'lx',       description:'Illuminance'], // BUG? - see above preferences
                [dp:103, name:'fadingTime',      type:'number',  rw: 'rw', min:0,   max:28800, defVal:30,  scale:1,   unit:'seconds',  title:'<b>Fading time</b>', description:'<i>Presence (fading) delay time</i>'],
                [dp:104, name:'ledIndicator',    type:'enum',    rw: 'rw', min:0,   max:1,    defVal:'0', map:[0:'0 - OFF', 1:'1 - ON'], title:'<b>LED indicator mode</b>', description:'<i>LED indicator mode</i>'],
                [dp:107, name:'maximumDistance', type:'decimal', rw: 'rw', min:0.0, max:10.0,  defVal:10.0,  scale:100,  unit:'meters',   title:'<b>Maximum distance</b>',     description:'<i>Maximum detection distance</i>'],
                [dp:108, name:'minimumDistance', type:'decimal', rw: 'rw', min:0.0, max:10.0,  defVal:10.0,  scale:100,  unit:'meters',   title:'<b>Minimum distance</b>',     description:'<i>Minimum detection distance</i>'],
                [dp:109, name:'staticDetectionDistance',         type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:4.0,   scale:100, unit:'meters',    title:'<b>Static detection Max distance</b>',          description:'<i>Static detection Max distance</i>'],
                [dp:110, name:'staticDetectionMinDistance',      type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:4.0,   scale:100, unit:'meters',    title:'<b>Static detection Min distance</b>',          description:'<i>Static detection Min distance</i>'],
                [dp:114, name:'smallMotionDetectionDistance',    type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:5.0,   scale:100, unit:'meters',    title:'<b>Small motion detection Max distance</b>',    description:'<i>Small motion detection Max distance</i>'],
                [dp:115, name:'smallMotionDetectionMinDistance', type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:5.0,   scale:100, unit:'meters',    title:'<b>Small motion detection Min distance</b>',    description:'<i>Small motion detection Min distance</i>'],
                [dp:116, name:'radarSensitivity',                type:'number',  rw: 'rw', min:0,    max:10 ,  defVal:5,     scale:1,   unit:'',          title:'<b>Radar Sensitivity</b>',    description:'<i>Sensitivity of the radar</i>'],
                [dp:117, name:'smallMotionDetectionSensitivity', type:'number',  rw: 'rw', min:0,    max:10 ,  defVal:7,     scale:1,   unit:'',          title:'<b>Small motion detection sensitivity</b>', description:'<i>Small motion detection sensitivity</i>'],
                [dp:118, name:'staticDetectionSensitivity',      type:'number',  rw: 'rw', min:0,    max:10,   defVal:7,     scale:1,   unit:'',          title:'<b>Static Detection Sensitivity</b>',       description:'<i>Static detection sensitivity</i>'],                 //  dt: "UINT8", aka Motionless Detection Sensitivity
            ], 
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [101], spammyDPsToNotTrace : [101],    
    ],

'TS0601_EX3RCDHA_RADAR'   : [        // white box human presence detector _TZE204_ex3rcdha ZHPS01
            description   : 'Tuya mmWave Radar ZHPS01',
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true, 'DistanceMeasurement':false, 'IlluminanceMeasurement': true],
            preferences   : [fadingTime:'104', radarSensitivity:'105', maximumDistance:'109', minimumDistance:'110', staticDetectionDistance:'111', staticDetectionMinDistance:'112', staticDetectionSensitivity:'107'],
            commands      : [resetStats:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_ex3rcdha', deviceJoinName: 'Tuya mmWave Radar ZHPS01']
            ],
            tuyaDPs:        [
                [dp:12,  name:'illuminance',       type:'number',  rw: 'ro', scale:1,    unit:'lx',       description:'Illuminance'],
                [dp:101, name:'motion',            type:'enum',    rw: 'ro', min:0, max:1,    defVal: '0', map:[0:'active', 1:'inactive'],     description:'Presence'],
                [dp:104, name:'fadingTime',        type:'number',  rw: 'rw', min:0, max:180,  defVal:30,  unit:'seconds',  title:'<b>Fading time</b>', description:'<i>Presence (fading) delay time</i>'],
                [dp:105, name:'radarSensitivity',  type:'number',  rw: 'rw', min:0, max:10 ,  defVal:5,   title:'<b>Radar Sensitivity</b>',    description:'<i>Sensitivity of the radar</i>'],  // move_sensitivity
                [dp:107, name:'staticDetectionSensitivity',      type:'number',  rw: 'rw', min:0,    max:10,   defVal:7,   title:'<b>Static Detection Sensitivity</b>',       description:'<i>Static detection sensitivity</i>'],                 //  dt: "UINT8", aka Motionless Detection Sensitivity
                [dp:109, name:'maximumDistance',   type:'decimal', rw: 'rw', min:0.0, max:6.0,  defVal:6.0,  scale:100, step:10, unit:'meters',   title:'<b>Maximum distance</b>',     description:'<i>Maximum detection distance</i>'],
                [dp:110, name:'minimumDistance',   type:'decimal', rw: 'rw', min:0.0, max:6.0,  defVal:1.0,  scale:100, step:10, unit:'meters',   title:'<b>Minimum distance</b>',     description:'<i>Minimum detection distance</i>'],
                [dp:111, name:'staticDetectionDistance',         type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:6.0,   scale:100, step:10, unit:'meters',    title:'<b>Static detection Max distance</b>',          description:'<i>Static detection Max distance</i>'],
                [dp:112, name:'staticDetectionMinDistance',      type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:4.0,   scale:100, step:10, unit:'meters',    title:'<b>Static detection Min distance</b>',          description:'<i>Static detection Min distance</i>'],
            ], 
            refresh: ['queryAllTuyaDP'],
            spammyDPsToNotTrace : [12],    
    ],

    'TS0601_HEIMAN_RADAR'   : [     // https://community.hubitat.com/t/release-tuya-zigbee-mmwave-sensors-code-moved-from-the-tuya-4-in-1-driver/137410/254?u=kkossev
            description   : 'Heiman mmWave Presence Sensor HS8OS',  // https://github.com/Koenkk/zigbee-herdsman-converters/pull/7423#issuecomment-2493581611
            device        : [powerSource: 'dc'],
            capabilities  : ['MotionSensor': true, 'DistanceMeasurement':false, 'IlluminanceMeasurement': true],
            preferences   : ['radarSensitivity':'104', 'ledIndicator':'102'],
            commands      : [resetStats:'', resetPreferencesToDefaults:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0B05,EF00', outClusters:'0003,0019,EF00', model:'TS0601', manufacturer:'_TZ6210_duv6fhwt', deviceJoinName: 'Heiman mmWave Presence Sensor HS8OS']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',            type:'enum',    rw: 'ro', defVal: '0', map:[0:'inactive', 1:'active'],     description:'Presence'],
                [dp:101, name:'illuminance',       type:'number',  rw: 'ro', scale:1,    unit:'lx',       description:'Illuminance'],
                [dp:102, name:'ledIndicator',      type:'enum',    rw: 'rw', defVal:'0',  map:[0:'0 - OFF', 1:'1 - ON'],  title:'<b>LED indicator</b>', description:'<i>LED indicator mode</i>'],
                [dp:103, name:'tamper',            type:'enum',    rw: 'ro', defVal:'0',  map:[0:'clear', 1:'detected'],  description:'Tamper state'],
                [dp:104, name:'radarSensitivity',  type:'number',  rw: 'rw', min:0, max:100 , defVal:50, scale:1,   unit:'', title:'<b>Radar Sensitivity</b>', description:'<i>Sensitivity of the radar</i>'],
                [dp:105, name:'occupiedTime',      type:'number',  rw: 'ro', min:0, max:9999, scale:1,   unit:'minutes',    description:'Presence duration in minutes'],
            ], 
            refresh: ['queryAllTuyaDP'],    // works OK!
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

// called from processFoundItem() for TS0601_YA4FT0W4_RADAR radar
Integer motionOrNotYA4FT0W4(int val) {
    // [dp:1,   name:'humanMotionState',   preProc:'motionOrNotYA4FT0W4', type:'enum',    rw: 'ro', min:0,    max:3,       defVal:'0',  map:[0:'none', 1:'present', 2:'moving', 3:'none'], description:'Presence state'],
    if (val in [1, 2]) {
        handleMotion(true)
    }
    else {
        handleMotion(false)
    }
    return val
}

Integer motionOrNotUXLLNYWP(int val) {
    // [dp:1,   name:'humanMotionState',   preProc:'motionOrNotUXLLNYWP', type:'enum',    rw: 'ro', min:0,    max:3,       defVal:'0',  map:[0:'none', 1:'static', 2:'small', 3:'large', 4:'moving'], description:'Presence state'],
    if (val in [4]) {
        handleMotion(true)
    }
    else if (val in [0]) {
        handleMotion(false)
    }
    return val
}

void customParseIasMessage(final String description) {
    // https://developer.tuya.com/en/docs/iot-device-dev/tuya-zigbee-water-sensor-access-standard?id=K9ik6zvon7orn
    Map zs = zigbee.parseZoneStatusChange(description)
    if (zs.alarm1Set == true) {
        handleMotion(true)
    }
    else {
        handleMotion(false)
    }
}

/*
// called from standardProcessTuyaDP in the commonLib for each Tuya dp report in a Zigbee message
// should always return true, as we are not processing the dp reports here. Actually - not needed to be defined at all!
boolean customProcessTuyaDp(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) {
    return false
}
*/

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
            logDebug "customUpdated: ignoreDistance is true ->deleting the distance state"
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

    // Itterates through all settings and calls setPar() for each setting
    updateAllPreferences()

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

// ~~~~~ start include (168) kkossev.illuminanceLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.illuminanceLib, line 1
library( // library marker kkossev.illuminanceLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Illuminance Library', name: 'illuminanceLib', namespace: 'kkossev', // library marker kkossev.illuminanceLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/illuminanceLib.groovy', documentationLink: '', // library marker kkossev.illuminanceLib, line 4
    version: '3.2.1' // library marker kkossev.illuminanceLib, line 5
) // library marker kkossev.illuminanceLib, line 6
/* // library marker kkossev.illuminanceLib, line 7
 *  Zigbee Illuminance Library // library marker kkossev.illuminanceLib, line 8
 * // library marker kkossev.illuminanceLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.illuminanceLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.illuminanceLib, line 11
 * // library marker kkossev.illuminanceLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.illuminanceLib, line 13
 * // library marker kkossev.illuminanceLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.illuminanceLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.illuminanceLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.illuminanceLib, line 17
 * // library marker kkossev.illuminanceLib, line 18
 * ver. 3.0.0  2024-04-06 kkossev  - added illuminanceLib.groovy // library marker kkossev.illuminanceLib, line 19
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0 allignment; added capability 'IlluminanceMeasurement'; added illuminanceRefresh() // library marker kkossev.illuminanceLib, line 20
 * ver. 3.2.1  2024-07-06 kkossev  - added illuminanceCoeff; added luxThreshold and illuminanceCoeff to preferences (if applicable) // library marker kkossev.illuminanceLib, line 21
 * // library marker kkossev.illuminanceLib, line 22
 *                                   TODO: illum threshold not working! // library marker kkossev.illuminanceLib, line 23
 *                                   TODO: check illuminanceInitializeVars() and illuminanceProcessTuyaDP() usage // library marker kkossev.illuminanceLib, line 24
*/ // library marker kkossev.illuminanceLib, line 25

static String illuminanceLibVersion()   { '3.2.1' } // library marker kkossev.illuminanceLib, line 27
static String illuminanceLibStamp() { '2024/07/06 1:34 PM' } // library marker kkossev.illuminanceLib, line 28

metadata { // library marker kkossev.illuminanceLib, line 30
    capability 'IlluminanceMeasurement' // library marker kkossev.illuminanceLib, line 31
    // no attributes // library marker kkossev.illuminanceLib, line 32
    // no commands // library marker kkossev.illuminanceLib, line 33
    preferences { // library marker kkossev.illuminanceLib, line 34
        if (device) { // library marker kkossev.illuminanceLib, line 35
            if ((DEVICE?.capabilities?.IlluminanceMeasurement == true) && (DEVICE?.preferences.illuminanceThreshold != false) && !(DEVICE?.device?.isDepricated == true)) { // library marker kkossev.illuminanceLib, line 36
                input('illuminanceThreshold', 'number', title: '<b>Lux threshold</b>', description: 'Minimum change in the lux which will trigger an event', range: '0..999', defaultValue: 5) // library marker kkossev.illuminanceLib, line 37
                if (advancedOptions) { // library marker kkossev.illuminanceLib, line 38
                    input('illuminanceCoeff', 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: 'Illuminance correction coefficient, range (0.10..10.00)', range: '0.10..10.00', defaultValue: 1.00) // library marker kkossev.illuminanceLib, line 39
                } // library marker kkossev.illuminanceLib, line 40
            } // library marker kkossev.illuminanceLib, line 41
            /* // library marker kkossev.illuminanceLib, line 42
            if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 43
                input 'minReportingTime', 'number', title: 'Minimum Reporting Time (sec)', description: 'Minimum time between illuminance reports', defaultValue: 60, required: false // library marker kkossev.illuminanceLib, line 44
                input 'maxReportingTime', 'number', title: 'Maximum Reporting Time (sec)', description: 'Maximum time between illuminance reports', defaultValue: 3600, required: false // library marker kkossev.illuminanceLib, line 45
            } // library marker kkossev.illuminanceLib, line 46
            */ // library marker kkossev.illuminanceLib, line 47
        } // library marker kkossev.illuminanceLib, line 48
    } // library marker kkossev.illuminanceLib, line 49
} // library marker kkossev.illuminanceLib, line 50

@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 10 // library marker kkossev.illuminanceLib, line 52

void standardParseIlluminanceCluster(final Map descMap) { // library marker kkossev.illuminanceLib, line 54
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.illuminanceLib, line 55
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.illuminanceLib, line 56
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.illuminanceLib, line 57
    handleIlluminanceEvent(lux) // library marker kkossev.illuminanceLib, line 58
} // library marker kkossev.illuminanceLib, line 59

void handleIlluminanceEvent(int illuminance, boolean isDigital=false) { // library marker kkossev.illuminanceLib, line 61
    Map eventMap = [:] // library marker kkossev.illuminanceLib, line 62
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.illuminanceLib, line 63
    eventMap.name = 'illuminance' // library marker kkossev.illuminanceLib, line 64
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.illuminanceLib, line 65
    eventMap.value  = illumCorrected // library marker kkossev.illuminanceLib, line 66
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.illuminanceLib, line 67
    eventMap.unit = 'lx' // library marker kkossev.illuminanceLib, line 68
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.illuminanceLib, line 69
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.illuminanceLib, line 70
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME  // defined in commonLib // library marker kkossev.illuminanceLib, line 71
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.illuminanceLib, line 72
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.illuminanceLib, line 73
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.illuminanceLib, line 74
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.illuminanceLib, line 75
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.illuminanceLib, line 76
        return // library marker kkossev.illuminanceLib, line 77
    } // library marker kkossev.illuminanceLib, line 78
    if (timeElapsed >= minTime) { // library marker kkossev.illuminanceLib, line 79
        logInfo "${eventMap.descriptionText}" // library marker kkossev.illuminanceLib, line 80
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.illuminanceLib, line 81
        state.lastRx['illumTime'] = now() // library marker kkossev.illuminanceLib, line 82
        sendEvent(eventMap) // library marker kkossev.illuminanceLib, line 83
    } // library marker kkossev.illuminanceLib, line 84
    else {         // queue the event // library marker kkossev.illuminanceLib, line 85
        eventMap.type = 'delayed' // library marker kkossev.illuminanceLib, line 86
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.illuminanceLib, line 87
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.illuminanceLib, line 88
    } // library marker kkossev.illuminanceLib, line 89
} // library marker kkossev.illuminanceLib, line 90

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.illuminanceLib, line 92
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.illuminanceLib, line 93
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.illuminanceLib, line 94
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.illuminanceLib, line 95
    sendEvent(eventMap) // library marker kkossev.illuminanceLib, line 96
} // library marker kkossev.illuminanceLib, line 97

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.illuminanceLib, line 99

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.illuminanceLib, line 101
void illuminanceProcessTuyaDP(final Map descMap, int dp, int dp_id, int fncmd) { // library marker kkossev.illuminanceLib, line 102
    switch (dp) { // library marker kkossev.illuminanceLib, line 103
        case 0x01 : // on/off // library marker kkossev.illuminanceLib, line 104
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.illuminanceLib, line 105
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.illuminanceLib, line 106
            } // library marker kkossev.illuminanceLib, line 107
            else { // library marker kkossev.illuminanceLib, line 108
                sendSwitchEvent(fncmd) // library marker kkossev.illuminanceLib, line 109
            } // library marker kkossev.illuminanceLib, line 110
            break // library marker kkossev.illuminanceLib, line 111
        case 0x02 : // library marker kkossev.illuminanceLib, line 112
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.illuminanceLib, line 113
                handleIlluminanceEvent(fncmd) // library marker kkossev.illuminanceLib, line 114
            } // library marker kkossev.illuminanceLib, line 115
            else { // library marker kkossev.illuminanceLib, line 116
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.illuminanceLib, line 117
            } // library marker kkossev.illuminanceLib, line 118
            break // library marker kkossev.illuminanceLib, line 119
        case 0x04 : // battery // library marker kkossev.illuminanceLib, line 120
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.illuminanceLib, line 121
            break // library marker kkossev.illuminanceLib, line 122
        default : // library marker kkossev.illuminanceLib, line 123
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.illuminanceLib, line 124
            break // library marker kkossev.illuminanceLib, line 125
    } // library marker kkossev.illuminanceLib, line 126
} // library marker kkossev.illuminanceLib, line 127

void illuminanceInitializeVars( boolean fullInit = false ) { // library marker kkossev.illuminanceLib, line 129
    logDebug "customInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.illuminanceLib, line 130
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 131
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // defined in commonLib // library marker kkossev.illuminanceLib, line 132
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.illuminanceLib, line 133
    } // library marker kkossev.illuminanceLib, line 134
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 135
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.illuminanceLib, line 136
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.illuminanceLib, line 137
    } // library marker kkossev.illuminanceLib, line 138
} // library marker kkossev.illuminanceLib, line 139

List<String> illuminanceRefresh() { // library marker kkossev.illuminanceLib, line 141
    List<String> cmds = [] // library marker kkossev.illuminanceLib, line 142
    cmds = zigbee.readAttribute(0x0400, 0x0000, [:], delay = 200) // illuminance // library marker kkossev.illuminanceLib, line 143
    return cmds // library marker kkossev.illuminanceLib, line 144
} // library marker kkossev.illuminanceLib, line 145

// ~~~~~ end include (168) kkossev.illuminanceLib ~~~~~

// ~~~~~ start include (180) kkossev.motionLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.motionLib, line 1
library( // library marker kkossev.motionLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Motion Library', name: 'motionLib', namespace: 'kkossev', // library marker kkossev.motionLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/motionLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-motionLib', // library marker kkossev.motionLib, line 4
    version: '3.2.1' // library marker kkossev.motionLib, line 5
) // library marker kkossev.motionLib, line 6
/*  Zigbee Motion Library // library marker kkossev.motionLib, line 7
 * // library marker kkossev.motionLib, line 8
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.motionLib, line 9
 * // library marker kkossev.motionLib, line 10
 * ver. 3.2.0  2024-07-06 kkossev  - added motionLib.groovy; added [digital] [physical] to the descriptionText // library marker kkossev.motionLib, line 11
 * ver. 3.2.1  2025-03-24 kkossev  - (dev.branch) documentation // library marker kkossev.motionLib, line 12
 * // library marker kkossev.motionLib, line 13
 *                                   TODO: // library marker kkossev.motionLib, line 14
*/ // library marker kkossev.motionLib, line 15

static String motionLibVersion()   { '3.2.1' } // library marker kkossev.motionLib, line 17
static String motionLibStamp() { '2025/03/06 12:52 PM' } // library marker kkossev.motionLib, line 18

metadata { // library marker kkossev.motionLib, line 20
    capability 'MotionSensor' // library marker kkossev.motionLib, line 21
    // no custom attributes // library marker kkossev.motionLib, line 22
    command 'setMotion', [[name: 'setMotion', type: 'ENUM', constraints: ['No selection', 'active', 'inactive'], description: 'Force motion active/inactive (for tests)']] // library marker kkossev.motionLib, line 23
    preferences { // library marker kkossev.motionLib, line 24
        if (device) { // library marker kkossev.motionLib, line 25
            if (('motionReset' in DEVICE?.preferences) && (DEVICE?.preferences.motionReset == true)) { // library marker kkossev.motionLib, line 26
                input(name: 'motionReset', type: 'bool', title: '<b>Reset Motion to Inactive</b>', description: 'Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b>', defaultValue: false) // library marker kkossev.motionLib, line 27
                if (settings?.motionReset?.value == true) { // library marker kkossev.motionLib, line 28
                    input('motionResetTimer', 'number', title: '<b>Motion Reset Timer</b>', description: 'After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds', range: '0..7200', defaultValue: 60) // library marker kkossev.motionLib, line 29
                } // library marker kkossev.motionLib, line 30
            } // library marker kkossev.motionLib, line 31
            if (advancedOptions == true) { // library marker kkossev.motionLib, line 32
                if ('invertMotion' in DEVICE?.preferences) { // library marker kkossev.motionLib, line 33
                    input(name: 'invertMotion', type: 'bool', title: '<b>Invert Motion Active/Not Active</b>', description: 'Some Tuya motion sensors may report the motion active/inactive inverted...', defaultValue: false) // library marker kkossev.motionLib, line 34
                } // library marker kkossev.motionLib, line 35
            } // library marker kkossev.motionLib, line 36
        } // library marker kkossev.motionLib, line 37
    } // library marker kkossev.motionLib, line 38
} // library marker kkossev.motionLib, line 39

public void handleMotion(final boolean motionActive, final boolean isDigital=false) { // library marker kkossev.motionLib, line 41
    boolean motionActiveCopy = motionActive // library marker kkossev.motionLib, line 42

    if (settings.invertMotion == true) {    // patch!! fix it! // library marker kkossev.motionLib, line 44
        motionActiveCopy = !motionActiveCopy // library marker kkossev.motionLib, line 45
    } // library marker kkossev.motionLib, line 46

    //log.trace "handleMotion: motionActive=${motionActiveCopy}, isDigital=${isDigital}" // library marker kkossev.motionLib, line 48
    if (motionActiveCopy) { // library marker kkossev.motionLib, line 49
        int timeout = settings?.motionResetTimer ?: 0 // library marker kkossev.motionLib, line 50
        // If the sensor only sends a motion detected message, the reset to motion inactive must be  performed in code // library marker kkossev.motionLib, line 51
        if (settings?.motionReset == true && timeout != 0) { // library marker kkossev.motionLib, line 52
            runIn(timeout, 'resetToMotionInactive', [overwrite: true]) // library marker kkossev.motionLib, line 53
        } // library marker kkossev.motionLib, line 54
        if (device.currentState('motion')?.value != 'active') { // library marker kkossev.motionLib, line 55
            state.motionStarted = unix2formattedDate(now()) // library marker kkossev.motionLib, line 56
        } // library marker kkossev.motionLib, line 57
    } // library marker kkossev.motionLib, line 58
    else { // library marker kkossev.motionLib, line 59
        if (device.currentState('motion')?.value == 'inactive') { // library marker kkossev.motionLib, line 60
            logDebug "ignored motion inactive event after ${getSecondsInactive()}s" // library marker kkossev.motionLib, line 61
            return      // do not process a second motion inactive event! // library marker kkossev.motionLib, line 62
        } // library marker kkossev.motionLib, line 63
    } // library marker kkossev.motionLib, line 64
    sendMotionEvent(motionActiveCopy, isDigital) // library marker kkossev.motionLib, line 65
} // library marker kkossev.motionLib, line 66

public void sendMotionEvent(final boolean motionActive, boolean isDigital=false) { // library marker kkossev.motionLib, line 68
    String descriptionText = 'Detected motion' // library marker kkossev.motionLib, line 69
    if (motionActive) { // library marker kkossev.motionLib, line 70
        descriptionText = device.currentValue('motion') == 'active' ? "Motion is active ${getSecondsInactive()}s" : 'Detected motion' // library marker kkossev.motionLib, line 71
    } // library marker kkossev.motionLib, line 72
    else { // library marker kkossev.motionLib, line 73
        descriptionText = "Motion reset to inactive after ${getSecondsInactive()}s" // library marker kkossev.motionLib, line 74
    } // library marker kkossev.motionLib, line 75
    if (isDigital) { descriptionText += ' [digital]' } // library marker kkossev.motionLib, line 76
    logInfo "${descriptionText}" // library marker kkossev.motionLib, line 77
    sendEvent( // library marker kkossev.motionLib, line 78
            name            : 'motion', // library marker kkossev.motionLib, line 79
            value            : motionActive ? 'active' : 'inactive', // library marker kkossev.motionLib, line 80
            type            : isDigital == true ? 'digital' : 'physical', // library marker kkossev.motionLib, line 81
            descriptionText : descriptionText // library marker kkossev.motionLib, line 82
    ) // library marker kkossev.motionLib, line 83
    //runIn(1, formatAttrib, [overwrite: true]) // library marker kkossev.motionLib, line 84
} // library marker kkossev.motionLib, line 85

public void resetToMotionInactive() { // library marker kkossev.motionLib, line 87
    if (device.currentState('motion')?.value == 'active') { // library marker kkossev.motionLib, line 88
        String descText = "Motion reset to inactive after ${getSecondsInactive()}s (software timeout)" // library marker kkossev.motionLib, line 89
        sendEvent( // library marker kkossev.motionLib, line 90
            name : 'motion', // library marker kkossev.motionLib, line 91
            value : 'inactive', // library marker kkossev.motionLib, line 92
            isStateChange : true, // library marker kkossev.motionLib, line 93
            type:  'digital', // library marker kkossev.motionLib, line 94
            descriptionText : descText // library marker kkossev.motionLib, line 95
        ) // library marker kkossev.motionLib, line 96
        logInfo "${descText}" // library marker kkossev.motionLib, line 97
    } // library marker kkossev.motionLib, line 98
    else { // library marker kkossev.motionLib, line 99
        logDebug "ignored resetToMotionInactive (software timeout) after ${getSecondsInactive()}s" // library marker kkossev.motionLib, line 100
    } // library marker kkossev.motionLib, line 101
} // library marker kkossev.motionLib, line 102

public void setMotion(String mode) { // library marker kkossev.motionLib, line 104
    if (mode == 'active') { // library marker kkossev.motionLib, line 105
        handleMotion(motionActive = true, isDigital = true) // library marker kkossev.motionLib, line 106
    } else if (mode == 'inactive') { // library marker kkossev.motionLib, line 107
        handleMotion(motionActive = false, isDigital = true) // library marker kkossev.motionLib, line 108
    } else { // library marker kkossev.motionLib, line 109
        if (settings?.txtEnable) { // library marker kkossev.motionLib, line 110
            log.warn "${device.displayName} please select motion action" // library marker kkossev.motionLib, line 111
        } // library marker kkossev.motionLib, line 112
    } // library marker kkossev.motionLib, line 113
} // library marker kkossev.motionLib, line 114

public int getSecondsInactive() { // library marker kkossev.motionLib, line 116
    Long unixTime = 0 // library marker kkossev.motionLib, line 117
    try { unixTime = formattedDate2unix(state.motionStarted) } catch (Exception e) { logWarn "getSecondsInactive: ${e}" } // library marker kkossev.motionLib, line 118
    if (unixTime) { return Math.round((now() - unixTime) / 1000) as int } // library marker kkossev.motionLib, line 119
    return settings?.motionResetTimer ?: 0 // library marker kkossev.motionLib, line 120
} // library marker kkossev.motionLib, line 121

public List<String> refreshAllMotion() { // library marker kkossev.motionLib, line 123
    logDebug 'refreshAllMotion()' // library marker kkossev.motionLib, line 124
    List<String> cmds = [] // library marker kkossev.motionLib, line 125
    return cmds // library marker kkossev.motionLib, line 126
} // library marker kkossev.motionLib, line 127

public void motionInitializeVars( boolean fullInit = false ) { // library marker kkossev.motionLib, line 129
    logDebug "motionInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.motionLib, line 130
    if (device.hasCapability('MotionSensor')) { // library marker kkossev.motionLib, line 131
        if (fullInit == true || settings.motionReset == null) { device.updateSetting('motionReset', false) } // library marker kkossev.motionLib, line 132
        if (fullInit == true || settings.invertMotion == null) { device.updateSetting('invertMotion', false) } // library marker kkossev.motionLib, line 133
        if (fullInit == true || settings.motionResetTimer == null) { device.updateSetting('motionResetTimer', 60) } // library marker kkossev.motionLib, line 134
    } // library marker kkossev.motionLib, line 135
} // library marker kkossev.motionLib, line 136

// ~~~~~ end include (180) kkossev.motionLib ~~~~~

// ~~~~~ start include (171) kkossev.batteryLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoJavaUtilDate, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
library( // library marker kkossev.batteryLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Battery Library', name: 'batteryLib', namespace: 'kkossev', // library marker kkossev.batteryLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/batteryLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-batteryLib', // library marker kkossev.batteryLib, line 4
    version: '3.2.3' // library marker kkossev.batteryLib, line 5
) // library marker kkossev.batteryLib, line 6
/* // library marker kkossev.batteryLib, line 7
 *  Zigbee Battery Library // library marker kkossev.batteryLib, line 8
 * // library marker kkossev.batteryLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.batteryLib, line 10
 * // library marker kkossev.batteryLib, line 11
 * ver. 3.0.0  2024-04-06 kkossev  - added batteryLib.groovy // library marker kkossev.batteryLib, line 12
 * ver. 3.0.1  2024-04-06 kkossev  - customParsePowerCluster bug fix // library marker kkossev.batteryLib, line 13
 * ver. 3.0.2  2024-04-14 kkossev  - batteryPercentage bug fix (was x2); added bVoltCtr; added battertRefresh // library marker kkossev.batteryLib, line 14
 * ver. 3.2.0  2024-05-21 kkossev  - commonLib 3.2.0 allignment; added lastBattery; added handleTuyaBatteryLevel // library marker kkossev.batteryLib, line 15
 * ver. 3.2.1  2024-07-06 kkossev  - added tuyaToBatteryLevel and handleTuyaBatteryLevel; added batteryInitializeVars // library marker kkossev.batteryLib, line 16
 * ver. 3.2.2  2024-07-18 kkossev  - added BatteryVoltage and BatteryDelay device capability checks // library marker kkossev.batteryLib, line 17
 * ver. 3.2.3  2025-07-13 kkossev  - bug fix: corrected runIn method name from 'sendDelayedBatteryEvent' to 'sendDelayedBatteryPercentageEvent' // library marker kkossev.batteryLib, line 18
 * // library marker kkossev.batteryLib, line 19
 *                                   TODO: add an Advanced Option resetBatteryToZeroWhenOffline // library marker kkossev.batteryLib, line 20
 *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.batteryLib, line 21
*/ // library marker kkossev.batteryLib, line 22

static String batteryLibVersion()   { '3.2.3' } // library marker kkossev.batteryLib, line 24
static String batteryLibStamp() { '2025/07/13 7:45 PM' } // library marker kkossev.batteryLib, line 25

metadata { // library marker kkossev.batteryLib, line 27
    capability 'Battery' // library marker kkossev.batteryLib, line 28
    attribute  'batteryVoltage', 'number' // library marker kkossev.batteryLib, line 29
    attribute  'lastBattery', 'date'         // last battery event time - added in 3.2.0 05/21/2024 // library marker kkossev.batteryLib, line 30
    // no commands // library marker kkossev.batteryLib, line 31
    preferences { // library marker kkossev.batteryLib, line 32
        if (device && advancedOptions == true) { // library marker kkossev.batteryLib, line 33
            if ('BatteryVoltage' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 34
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: 'Convert battery voltage to battery Percentage remaining.' // library marker kkossev.batteryLib, line 35
            } // library marker kkossev.batteryLib, line 36
            if ('BatteryDelay' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 37
                input(name: 'batteryDelay', type: 'enum', title: '<b>Battery Events Delay</b>', description:'Select the Battery Events Delay<br>(default is <b>no delay</b>)', options: DelayBatteryOpts.options, defaultValue: DelayBatteryOpts.defaultValue) // library marker kkossev.batteryLib, line 38
            } // library marker kkossev.batteryLib, line 39
        } // library marker kkossev.batteryLib, line 40
    } // library marker kkossev.batteryLib, line 41
} // library marker kkossev.batteryLib, line 42

@Field static final Map DelayBatteryOpts = [ defaultValue: 0, options: [0: 'No delay', 30: '30 seconds', 3600: '1 hour', 14400: '4 hours', 28800: '8 hours', 43200: '12 hours']] // library marker kkossev.batteryLib, line 44

public void standardParsePowerCluster(final Map descMap) { // library marker kkossev.batteryLib, line 46
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.batteryLib, line 47
    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.batteryLib, line 48
    if (descMap.attrId == '0020') { // battery voltage // library marker kkossev.batteryLib, line 49
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 50
        state.stats['bVoltCtr'] = (state.stats['bVoltCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 51
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.batteryLib, line 52
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.batteryLib, line 53
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.batteryLib, line 54
        } // library marker kkossev.batteryLib, line 55
    } // library marker kkossev.batteryLib, line 56
    else if (descMap.attrId == '0021') { // battery percentage // library marker kkossev.batteryLib, line 57
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 58
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 59
        if (isTuya()) { // library marker kkossev.batteryLib, line 60
            sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 61
        } // library marker kkossev.batteryLib, line 62
        else { // library marker kkossev.batteryLib, line 63
            sendBatteryPercentageEvent((rawValue / 2) as int) // library marker kkossev.batteryLib, line 64
        } // library marker kkossev.batteryLib, line 65
    } // library marker kkossev.batteryLib, line 66
    else { // library marker kkossev.batteryLib, line 67
        logWarn "customParsePowerCluster: zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.batteryLib, line 68
    } // library marker kkossev.batteryLib, line 69
} // library marker kkossev.batteryLib, line 70

public void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.batteryLib, line 72
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.batteryLib, line 73
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 74
    Map result = [:] // library marker kkossev.batteryLib, line 75
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.batteryLib, line 76
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.batteryLib, line 77
        BigDecimal minVolts = 2.2 // library marker kkossev.batteryLib, line 78
        BigDecimal maxVolts = 3.2 // library marker kkossev.batteryLib, line 79
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.batteryLib, line 80
        int roundedPct = Math.round(pct * 100) // library marker kkossev.batteryLib, line 81
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.batteryLib, line 82
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.batteryLib, line 83
        if (convertToPercent == true) { // library marker kkossev.batteryLib, line 84
            result.value = Math.min(100, roundedPct) // library marker kkossev.batteryLib, line 85
            result.name = 'battery' // library marker kkossev.batteryLib, line 86
            result.unit  = '%' // library marker kkossev.batteryLib, line 87
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.batteryLib, line 88
        } // library marker kkossev.batteryLib, line 89
        else { // library marker kkossev.batteryLib, line 90
            result.value = volts // library marker kkossev.batteryLib, line 91
            result.name = 'batteryVoltage' // library marker kkossev.batteryLib, line 92
            result.unit  = 'V' // library marker kkossev.batteryLib, line 93
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.batteryLib, line 94
        } // library marker kkossev.batteryLib, line 95
        result.type = 'physical' // library marker kkossev.batteryLib, line 96
        result.isStateChange = true // library marker kkossev.batteryLib, line 97
        logInfo "${result.descriptionText}" // library marker kkossev.batteryLib, line 98
        sendEvent(result) // library marker kkossev.batteryLib, line 99
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 100
    } // library marker kkossev.batteryLib, line 101
    else { // library marker kkossev.batteryLib, line 102
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.batteryLib, line 103
    } // library marker kkossev.batteryLib, line 104
} // library marker kkossev.batteryLib, line 105

public void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.batteryLib, line 107
    if ((batteryPercent as int) == 255) { // library marker kkossev.batteryLib, line 108
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.batteryLib, line 109
        return // library marker kkossev.batteryLib, line 110
    } // library marker kkossev.batteryLib, line 111
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 112
    Map map = [:] // library marker kkossev.batteryLib, line 113
    map.name = 'battery' // library marker kkossev.batteryLib, line 114
    map.timeStamp = now() // library marker kkossev.batteryLib, line 115
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.batteryLib, line 116
    map.unit  = '%' // library marker kkossev.batteryLib, line 117
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.batteryLib, line 118
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.batteryLib, line 119
    map.isStateChange = true // library marker kkossev.batteryLib, line 120
    // // library marker kkossev.batteryLib, line 121
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.batteryLib, line 122
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.batteryLib, line 123
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.batteryLib, line 124
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.batteryLib, line 125
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.batteryLib, line 126
        // send it now! // library marker kkossev.batteryLib, line 127
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.batteryLib, line 128
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 129
    } // library marker kkossev.batteryLib, line 130
    else { // library marker kkossev.batteryLib, line 131
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.batteryLib, line 132
        map.delayed = delayedTime // library marker kkossev.batteryLib, line 133
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.batteryLib, line 134
        map.lastBattery = lastBattery // library marker kkossev.batteryLib, line 135
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.batteryLib, line 136
        runIn(delayedTime, 'sendDelayedBatteryPercentageEvent', [overwrite: true, data: map]) // library marker kkossev.batteryLib, line 137
    } // library marker kkossev.batteryLib, line 138
} // library marker kkossev.batteryLib, line 139

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.batteryLib, line 141
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 142
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 143
    sendEvent(map) // library marker kkossev.batteryLib, line 144
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 145
} // library marker kkossev.batteryLib, line 146

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.batteryLib, line 148
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.batteryLib, line 149
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 150
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 151
    sendEvent(map) // library marker kkossev.batteryLib, line 152
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 153
} // library marker kkossev.batteryLib, line 154

public int tuyaToBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 156
    int rawValue = fncmd // library marker kkossev.batteryLib, line 157
    switch (fncmd) { // library marker kkossev.batteryLib, line 158
        case 0: rawValue = 100; break // Battery Full // library marker kkossev.batteryLib, line 159
        case 1: rawValue = 75;  break // Battery High // library marker kkossev.batteryLib, line 160
        case 2: rawValue = 50;  break // Battery Medium // library marker kkossev.batteryLib, line 161
        case 3: rawValue = 25;  break // Battery Low // library marker kkossev.batteryLib, line 162
        case 4: rawValue = 100; break // Tuya 3 in 1 -> USB powered // library marker kkossev.batteryLib, line 163
        // for all other values >4 we will use the raw value, expected to be the real battery level 4..100% // library marker kkossev.batteryLib, line 164
    } // library marker kkossev.batteryLib, line 165
    return rawValue // library marker kkossev.batteryLib, line 166
} // library marker kkossev.batteryLib, line 167

public void handleTuyaBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 169
    int rawValue = tuyaToBatteryLevel(fncmd) // library marker kkossev.batteryLib, line 170
    sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 171
} // library marker kkossev.batteryLib, line 172

public void batteryInitializeVars( boolean fullInit = false ) { // library marker kkossev.batteryLib, line 174
    logDebug "batteryInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.batteryLib, line 175
    if (device.hasCapability('Battery')) { // library marker kkossev.batteryLib, line 176
        if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.batteryLib, line 177
        if (fullInit || settings?.batteryDelay == null) { device.updateSetting('batteryDelay', [value: DelayBatteryOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.batteryLib, line 178
    } // library marker kkossev.batteryLib, line 179
} // library marker kkossev.batteryLib, line 180

public List<String> batteryRefresh() { // library marker kkossev.batteryLib, line 182
    List<String> cmds = [] // library marker kkossev.batteryLib, line 183
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 100)         // battery voltage // library marker kkossev.batteryLib, line 184
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 100)         // battery percentage // library marker kkossev.batteryLib, line 185
    return cmds // library marker kkossev.batteryLib, line 186
} // library marker kkossev.batteryLib, line 187

// ~~~~~ end include (171) kkossev.batteryLib ~~~~~

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
 * ver. 3.5.0  2025-08-14 kkossev  - zclWriteAttribute() support for forced destinationEndpoint in the attributes map // library marker kkossev.deviceProfileLib, line 39
 * // library marker kkossev.deviceProfileLib, line 40
 *                                   TODO - remove the 2-in-1 patch ! // library marker kkossev.deviceProfileLib, line 41
 *                                   TODO - add updateStateUnknownDPs (from the 4-in-1 driver) // library marker kkossev.deviceProfileLib, line 42
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 43
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 44
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off) // library marker kkossev.deviceProfileLib, line 45
 *                                   TODO: allow NULL parameters default values in the device profiles // library marker kkossev.deviceProfileLib, line 46
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 47
 * // library marker kkossev.deviceProfileLib, line 48
*/ // library marker kkossev.deviceProfileLib, line 49

static String deviceProfileLibVersion()   { '3.5.0' } // library marker kkossev.deviceProfileLib, line 51
static String deviceProfileLibStamp() { '2025/08/14 11:17 PM' } // library marker kkossev.deviceProfileLib, line 52
import groovy.json.* // library marker kkossev.deviceProfileLib, line 53
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 54
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 55
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 56
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 57

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 59

metadata { // library marker kkossev.deviceProfileLib, line 61
    // no capabilities // library marker kkossev.deviceProfileLib, line 62
    // no attributes // library marker kkossev.deviceProfileLib, line 63
    /* // library marker kkossev.deviceProfileLib, line 64
    // copy the following commands to the main driver, if needed // library marker kkossev.deviceProfileLib, line 65
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 66
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 67
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 68
    ] // library marker kkossev.deviceProfileLib, line 69
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 70
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 71
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 72
    ] // library marker kkossev.deviceProfileLib, line 73
    */ // library marker kkossev.deviceProfileLib, line 74
    preferences { // library marker kkossev.deviceProfileLib, line 75
        if (device) { // library marker kkossev.deviceProfileLib, line 76
            input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 77
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 78
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 79
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 80
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 81
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 82
                        input inputMap // library marker kkossev.deviceProfileLib, line 83
                    } // library marker kkossev.deviceProfileLib, line 84
                } // library marker kkossev.deviceProfileLib, line 85
            } // library marker kkossev.deviceProfileLib, line 86
        } // library marker kkossev.deviceProfileLib, line 87
    } // library marker kkossev.deviceProfileLib, line 88
} // library marker kkossev.deviceProfileLib, line 89

private boolean is2in1() { return getDeviceProfile().startsWith('TS0601_2IN1')  }   // patch! // library marker kkossev.deviceProfileLib, line 91

public String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 93
public Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 94
public Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 95

public List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLib, line 97
    if (deviceProfilesV3 == null) { // library marker kkossev.deviceProfileLib, line 98
        if (deviceProfilesV2 == null) { return [] } // library marker kkossev.deviceProfileLib, line 99
        return deviceProfilesV2.values().description as List<String> // library marker kkossev.deviceProfileLib, line 100
    } // library marker kkossev.deviceProfileLib, line 101
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLib, line 102
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 103
        if ((profileMap.device?.isDepricated ?: false) != true) { // library marker kkossev.deviceProfileLib, line 104
            activeProfiles.add(profileMap.description ?: '---') // library marker kkossev.deviceProfileLib, line 105
        } // library marker kkossev.deviceProfileLib, line 106
    } // library marker kkossev.deviceProfileLib, line 107
    return activeProfiles // library marker kkossev.deviceProfileLib, line 108
} // library marker kkossev.deviceProfileLib, line 109

// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 111

/** // library marker kkossev.deviceProfileLib, line 113
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 114
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 115
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 116
 */ // library marker kkossev.deviceProfileLib, line 117
public String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 118
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 119
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 120
    else { return null } // library marker kkossev.deviceProfileLib, line 121
} // library marker kkossev.deviceProfileLib, line 122

/** // library marker kkossev.deviceProfileLib, line 124
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 125
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 126
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 127
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 128
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 129
 */ // library marker kkossev.deviceProfileLib, line 130
private Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 131
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 132
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 133
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 134
    def preference // library marker kkossev.deviceProfileLib, line 135
    try { // library marker kkossev.deviceProfileLib, line 136
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 137
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 138
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 139
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 140
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 141
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 142
        } // library marker kkossev.deviceProfileLib, line 143
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 144
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 145
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 146
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 147
        } // library marker kkossev.deviceProfileLib, line 148
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 149
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 150
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 151
        } // library marker kkossev.deviceProfileLib, line 152
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 153
    } catch (e) { // library marker kkossev.deviceProfileLib, line 154
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 155
        return [:] // library marker kkossev.deviceProfileLib, line 156
    } // library marker kkossev.deviceProfileLib, line 157
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 158
    return foundMap // library marker kkossev.deviceProfileLib, line 159
} // library marker kkossev.deviceProfileLib, line 160

public Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 162
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 163
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 164
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 165
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 166
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 167
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 168
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 169
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 170
            return foundMap // library marker kkossev.deviceProfileLib, line 171
        } // library marker kkossev.deviceProfileLib, line 172
    } // library marker kkossev.deviceProfileLib, line 173
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 174
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 175
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 176
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 177
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 178
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 179
            return foundMap // library marker kkossev.deviceProfileLib, line 180
        } // library marker kkossev.deviceProfileLib, line 181
    } // library marker kkossev.deviceProfileLib, line 182
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 183
    return [:] // library marker kkossev.deviceProfileLib, line 184
} // library marker kkossev.deviceProfileLib, line 185

/** // library marker kkossev.deviceProfileLib, line 187
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 188
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 189
 */ // library marker kkossev.deviceProfileLib, line 190
public void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 191
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 192
    if (DEVICE == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 193
    Map preferences = DEVICE?.preferences ?: [:] // library marker kkossev.deviceProfileLib, line 194
    if (preferences == null || preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 195
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 196
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 197
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 198
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 199
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 200
            return // continue // library marker kkossev.deviceProfileLib, line 201
        } // library marker kkossev.deviceProfileLib, line 202
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 203
        if (parMap == null || parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 204
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 205
        if (parMap?.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 206
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 207
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 208
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 209
    } // library marker kkossev.deviceProfileLib, line 210
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 211
} // library marker kkossev.deviceProfileLib, line 212

/** // library marker kkossev.deviceProfileLib, line 214
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 215
 * // library marker kkossev.deviceProfileLib, line 216
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 217
 */ // library marker kkossev.deviceProfileLib, line 218
private List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 219
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 220
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 221
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 222
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 223
    } // library marker kkossev.deviceProfileLib, line 224
    return validPars // library marker kkossev.deviceProfileLib, line 225
} // library marker kkossev.deviceProfileLib, line 226

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 228
private def getScaledPreferenceValue(String preference, Map dpMap) {        // TODO - not used ??? // library marker kkossev.deviceProfileLib, line 229
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 230
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 231
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 232
    def scaledValue // library marker kkossev.deviceProfileLib, line 233
    if (value == null) { // library marker kkossev.deviceProfileLib, line 234
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 235
        return null // library marker kkossev.deviceProfileLib, line 236
    } // library marker kkossev.deviceProfileLib, line 237
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 238
        case 'number' : // library marker kkossev.deviceProfileLib, line 239
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 240
            break // library marker kkossev.deviceProfileLib, line 241
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 242
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 243
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 244
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 245
            } // library marker kkossev.deviceProfileLib, line 246
            break // library marker kkossev.deviceProfileLib, line 247
        case 'bool' : // library marker kkossev.deviceProfileLib, line 248
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 249
            break // library marker kkossev.deviceProfileLib, line 250
        case 'enum' : // library marker kkossev.deviceProfileLib, line 251
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 252
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 253
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 254
                return null // library marker kkossev.deviceProfileLib, line 255
            } // library marker kkossev.deviceProfileLib, line 256
            scaledValue = value // library marker kkossev.deviceProfileLib, line 257
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 258
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 259
            } // library marker kkossev.deviceProfileLib, line 260
            break // library marker kkossev.deviceProfileLib, line 261
        default : // library marker kkossev.deviceProfileLib, line 262
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 263
            return null // library marker kkossev.deviceProfileLib, line 264
    } // library marker kkossev.deviceProfileLib, line 265
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 266
    return scaledValue // library marker kkossev.deviceProfileLib, line 267
} // library marker kkossev.deviceProfileLib, line 268

// called from customUpdated() method in the custom driver // library marker kkossev.deviceProfileLib, line 270
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 271
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 272
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 273
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 274
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 275
        return // library marker kkossev.deviceProfileLib, line 276
    } // library marker kkossev.deviceProfileLib, line 277
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 278
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 279
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 280
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 281
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 282
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 283
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 284
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 285
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 286
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 287
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 288
                // scale the value // library marker kkossev.deviceProfileLib, line 289
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 290
            } // library marker kkossev.deviceProfileLib, line 291
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLib, line 292
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLib, line 293
            } // library marker kkossev.deviceProfileLib, line 294
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLib, line 295
        } // library marker kkossev.deviceProfileLib, line 296
        else { logDebug "warning: couldn't find map for preference ${name}" ; return }  // TODO - supress the warning if the preference was boolean true/false // library marker kkossev.deviceProfileLib, line 297
    } // library marker kkossev.deviceProfileLib, line 298
    return // library marker kkossev.deviceProfileLib, line 299
} // library marker kkossev.deviceProfileLib, line 300

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 302
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 303
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 304
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 305
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 306
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 307
} // library marker kkossev.deviceProfileLib, line 308
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 309
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 310
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 311
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 312
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 313
} // library marker kkossev.deviceProfileLib, line 314
int invert(int val) { // library marker kkossev.deviceProfileLib, line 315
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 316
    else { return val } // library marker kkossev.deviceProfileLib, line 317
} // library marker kkossev.deviceProfileLib, line 318

// called from setPar and sendAttribite methods for non-Tuya DPs // library marker kkossev.deviceProfileLib, line 320
private List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 321
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 322
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 323
    Map map = [:] // library marker kkossev.deviceProfileLib, line 324
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 325
    try { // library marker kkossev.deviceProfileLib, line 326
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 327
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 328
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 329
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 330
        map['ep'] = (attributesMap.ep != null && attributesMap.ep != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.ep) as Integer : null // library marker kkossev.deviceProfileLib, line 331
    } // library marker kkossev.deviceProfileLib, line 332
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLib, line 333
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 334
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 335
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLib, line 336
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 337
    } // library marker kkossev.deviceProfileLib, line 338
    if ((map.mfgCode != null && map.mfgCode != '') || (map.ep != null && map.ep != '')) { // library marker kkossev.deviceProfileLib, line 339
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 340
        Map ep = map.ep != null ? ['destEndpoint':map.ep] : [:] // library marker kkossev.deviceProfileLib, line 341
        Map mapOptions = [:] // library marker kkossev.deviceProfileLib, line 342
        if (mfgCode) mapOptions.putAll(mfgCode) // library marker kkossev.deviceProfileLib, line 343
        if (ep) mapOptions.putAll(ep) // library marker kkossev.deviceProfileLib, line 344
        //log.trace "$mapOptions" // library marker kkossev.deviceProfileLib, line 345
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mapOptions, delay = 50) // library marker kkossev.deviceProfileLib, line 346
    } // library marker kkossev.deviceProfileLib, line 347
    else { // library marker kkossev.deviceProfileLib, line 348
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 50) // library marker kkossev.deviceProfileLib, line 349
    } // library marker kkossev.deviceProfileLib, line 350
    return cmds // library marker kkossev.deviceProfileLib, line 351
} // library marker kkossev.deviceProfileLib, line 352

/** // library marker kkossev.deviceProfileLib, line 354
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 355
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 356
 * // library marker kkossev.deviceProfileLib, line 357
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 358
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 359
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 360
 */ // library marker kkossev.deviceProfileLib, line 361
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 362
private def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 363
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 364
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 365
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 366
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 367
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 368
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 369
        case 'number' : // library marker kkossev.deviceProfileLib, line 370
            // TODO - negative values ! // library marker kkossev.deviceProfileLib, line 371
            // TODO - better conversion to integer! // library marker kkossev.deviceProfileLib, line 372
            value = safeToInt(val, 0) // library marker kkossev.deviceProfileLib, line 373
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 374
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 375
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 376
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 377
            } // library marker kkossev.deviceProfileLib, line 378
            else { // library marker kkossev.deviceProfileLib, line 379
                scaledValue = value // library marker kkossev.deviceProfileLib, line 380
            } // library marker kkossev.deviceProfileLib, line 381
            break // library marker kkossev.deviceProfileLib, line 382

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 384
            value = safeToDouble(val, 0.0) // library marker kkossev.deviceProfileLib, line 385
            // scale the value // library marker kkossev.deviceProfileLib, line 386
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 387
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 388
            } // library marker kkossev.deviceProfileLib, line 389
            else { // library marker kkossev.deviceProfileLib, line 390
                scaledValue = value // library marker kkossev.deviceProfileLib, line 391
            } // library marker kkossev.deviceProfileLib, line 392
            break // library marker kkossev.deviceProfileLib, line 393

        case 'bool' : // library marker kkossev.deviceProfileLib, line 395
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 396
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 397
            else { // library marker kkossev.deviceProfileLib, line 398
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 399
                return null // library marker kkossev.deviceProfileLib, line 400
            } // library marker kkossev.deviceProfileLib, line 401
            break // library marker kkossev.deviceProfileLib, line 402
        case 'enum' : // library marker kkossev.deviceProfileLib, line 403
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 404
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 405
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 406
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 407
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 408
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 409
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 410
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 411
            } // library marker kkossev.deviceProfileLib, line 412
            else { // library marker kkossev.deviceProfileLib, line 413
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 414
            } // library marker kkossev.deviceProfileLib, line 415
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 416
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 417
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 418
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 419
                // find the key for the value // library marker kkossev.deviceProfileLib, line 420
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 421
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 422
                if (key == null) { // library marker kkossev.deviceProfileLib, line 423
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 424
                    return null // library marker kkossev.deviceProfileLib, line 425
                } // library marker kkossev.deviceProfileLib, line 426
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 427
            //return null // library marker kkossev.deviceProfileLib, line 428
            } // library marker kkossev.deviceProfileLib, line 429
            break // library marker kkossev.deviceProfileLib, line 430
        default : // library marker kkossev.deviceProfileLib, line 431
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 432
            return null // library marker kkossev.deviceProfileLib, line 433
    } // library marker kkossev.deviceProfileLib, line 434
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 435
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 436
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 437
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 438
        return null // library marker kkossev.deviceProfileLib, line 439
    } // library marker kkossev.deviceProfileLib, line 440
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 441
    return scaledValue // library marker kkossev.deviceProfileLib, line 442
} // library marker kkossev.deviceProfileLib, line 443

/** // library marker kkossev.deviceProfileLib, line 445
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 446
 * // library marker kkossev.deviceProfileLib, line 447
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 448
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 449
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 450
 */ // library marker kkossev.deviceProfileLib, line 451
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 452
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 453
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 454
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 455
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 456
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 457
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 458
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 459
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 460
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 461
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 462
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 463
    if (scaledValue == null) { // library marker kkossev.deviceProfileLib, line 464
        log.trace "$dpMap  ${dpMap.map}" // library marker kkossev.deviceProfileLib, line 465
        String helpTxt = "setPar: invalid parameter ${par} value <b>${val}</b>." // library marker kkossev.deviceProfileLib, line 466
        if (dpMap.min != null && dpMap.max != null) { helpTxt += " Must be in the range ${dpMap.min} to ${dpMap.max}" } // library marker kkossev.deviceProfileLib, line 467
        if (dpMap.map != null) { helpTxt += " Must be one of ${dpMap.map}" } // library marker kkossev.deviceProfileLib, line 468
        logInfo helpTxt // library marker kkossev.deviceProfileLib, line 469
        return false // library marker kkossev.deviceProfileLib, line 470
    } // library marker kkossev.deviceProfileLib, line 471

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 473
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 474
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 475
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 476
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 477
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 478
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 479
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 480
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 481
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 482
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 483
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 484
            return true // library marker kkossev.deviceProfileLib, line 485
        } // library marker kkossev.deviceProfileLib, line 486
        else { // library marker kkossev.deviceProfileLib, line 487
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 488
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 489
        } // library marker kkossev.deviceProfileLib, line 490
    } // library marker kkossev.deviceProfileLib, line 491
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 492
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 493
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 494
        def valMiscType // library marker kkossev.deviceProfileLib, line 495
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 496
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 497
            // find the key for the value // library marker kkossev.deviceProfileLib, line 498
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 499
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 500
            if (key == null) { // library marker kkossev.deviceProfileLib, line 501
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 502
                return false // library marker kkossev.deviceProfileLib, line 503
            } // library marker kkossev.deviceProfileLib, line 504
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 505
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 506
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 507
        } // library marker kkossev.deviceProfileLib, line 508
        else { // library marker kkossev.deviceProfileLib, line 509
            valMiscType = val // library marker kkossev.deviceProfileLib, line 510
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 511
        } // library marker kkossev.deviceProfileLib, line 512
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 513
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 514
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 515
        return true // library marker kkossev.deviceProfileLib, line 516
    } // library marker kkossev.deviceProfileLib, line 517

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 519
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 520

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 522
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 523
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 524
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 525
        // Tuya DP // library marker kkossev.deviceProfileLib, line 526
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 527
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 528
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 529
            return false // library marker kkossev.deviceProfileLib, line 530
        } // library marker kkossev.deviceProfileLib, line 531
        else { // library marker kkossev.deviceProfileLib, line 532
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 533
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 534
            return false // library marker kkossev.deviceProfileLib, line 535
        } // library marker kkossev.deviceProfileLib, line 536
    } // library marker kkossev.deviceProfileLib, line 537
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 538
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 539
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 540
        int signedIntScaled = convertSignedInts(scaledValue, dpMap) // library marker kkossev.deviceProfileLib, line 541
        cmds = zclWriteAttribute(dpMap, signedIntScaled) // library marker kkossev.deviceProfileLib, line 542
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 543
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 544
            return false // library marker kkossev.deviceProfileLib, line 545
        } // library marker kkossev.deviceProfileLib, line 546
    } // library marker kkossev.deviceProfileLib, line 547
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 548
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 549
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 550
    return true // library marker kkossev.deviceProfileLib, line 551
} // library marker kkossev.deviceProfileLib, line 552

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 554
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 555
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 556
public List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 557
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 558
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 559
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 560
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 561
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 562
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 563
        return [] // library marker kkossev.deviceProfileLib, line 564
    } // library marker kkossev.deviceProfileLib, line 565
    String dpType // library marker kkossev.deviceProfileLib, line 566
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 567
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 568
    } // library marker kkossev.deviceProfileLib, line 569
    else { // library marker kkossev.deviceProfileLib, line 570
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 571
    } // library marker kkossev.deviceProfileLib, line 572
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 573
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 574
        return [] // library marker kkossev.deviceProfileLib, line 575
    } // library marker kkossev.deviceProfileLib, line 576
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 577
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 578
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 579
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 580
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 581
    } // library marker kkossev.deviceProfileLib, line 582
    else { // library marker kkossev.deviceProfileLib, line 583
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 584
    } // library marker kkossev.deviceProfileLib, line 585
    return cmds // library marker kkossev.deviceProfileLib, line 586
} // library marker kkossev.deviceProfileLib, line 587

private int convertSignedInts(int val, Map dpMap) { // library marker kkossev.deviceProfileLib, line 589
    if (dpMap.dt == '0x28') { // library marker kkossev.deviceProfileLib, line 590
        if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 591
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 592
    } // library marker kkossev.deviceProfileLib, line 593
    else if (dpMap.dt == '0x29') { // library marker kkossev.deviceProfileLib, line 594
        if (val > 32767) { return (val as int) - 65536 } // library marker kkossev.deviceProfileLib, line 595
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 596
    } // library marker kkossev.deviceProfileLib, line 597
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 598
} // library marker kkossev.deviceProfileLib, line 599

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 601
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 602
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 603
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 604
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 605
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'DEVICE.preferences is empty!' ; return false } // library marker kkossev.deviceProfileLib, line 606

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 608
    l//log.trace "sendAttribute: dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 609
    if (dpMap == null || dpMap?.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 610
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 611
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 612
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 613
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 614
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 615
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 616
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 617
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 618
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 619
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 620
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 621
        try { // library marker kkossev.deviceProfileLib, line 622
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 623
        } // library marker kkossev.deviceProfileLib, line 624
        catch (e) { // library marker kkossev.deviceProfileLib, line 625
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 626
            return false // library marker kkossev.deviceProfileLib, line 627
        } // library marker kkossev.deviceProfileLib, line 628
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 629
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 630
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 631
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 632
            return true // library marker kkossev.deviceProfileLib, line 633
        } // library marker kkossev.deviceProfileLib, line 634
        else { // library marker kkossev.deviceProfileLib, line 635
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 636
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 637
        } // library marker kkossev.deviceProfileLib, line 638
    } // library marker kkossev.deviceProfileLib, line 639
    else { // library marker kkossev.deviceProfileLib, line 640
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 641
    } // library marker kkossev.deviceProfileLib, line 642
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 643
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 644
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 645
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 646
        // patch !! // library marker kkossev.deviceProfileLib, line 647
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 648
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 649
        } // library marker kkossev.deviceProfileLib, line 650
        else { // library marker kkossev.deviceProfileLib, line 651
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 652
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 653
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 654
        } // library marker kkossev.deviceProfileLib, line 655
        return true // library marker kkossev.deviceProfileLib, line 656
    } // library marker kkossev.deviceProfileLib, line 657
    else { // library marker kkossev.deviceProfileLib, line 658
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 659
    } // library marker kkossev.deviceProfileLib, line 660
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 661
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 662
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 663
    try { // library marker kkossev.deviceProfileLib, line 664
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 665
    } // library marker kkossev.deviceProfileLib, line 666
    catch (e) { // library marker kkossev.deviceProfileLib, line 667
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 668
        return false // library marker kkossev.deviceProfileLib, line 669
    } // library marker kkossev.deviceProfileLib, line 670
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 671
        // Tuya DP // library marker kkossev.deviceProfileLib, line 672
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 673
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 674
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 675
            return false // library marker kkossev.deviceProfileLib, line 676
        } // library marker kkossev.deviceProfileLib, line 677
        else { // library marker kkossev.deviceProfileLib, line 678
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 679
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 680
            return true // library marker kkossev.deviceProfileLib, line 681
        } // library marker kkossev.deviceProfileLib, line 682
    } // library marker kkossev.deviceProfileLib, line 683
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 684
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 685
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 686
    } // library marker kkossev.deviceProfileLib, line 687
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 688
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 689
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 690
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 691
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 692
            return false // library marker kkossev.deviceProfileLib, line 693
        } // library marker kkossev.deviceProfileLib, line 694
    } // library marker kkossev.deviceProfileLib, line 695
    else { // library marker kkossev.deviceProfileLib, line 696
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 697
        return false // library marker kkossev.deviceProfileLib, line 698
    } // library marker kkossev.deviceProfileLib, line 699
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 700
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 701
    return true // library marker kkossev.deviceProfileLib, line 702
} // library marker kkossev.deviceProfileLib, line 703

/** // library marker kkossev.deviceProfileLib, line 705
 * SENDS a list of Zigbee commands to be sent to the device. // library marker kkossev.deviceProfileLib, line 706
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 707
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 708
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 709
 */ // library marker kkossev.deviceProfileLib, line 710
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 711
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 712
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 713
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 714
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 715
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 716
    if (supportedCommandsMap == null || supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 717
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 718
        return false // library marker kkossev.deviceProfileLib, line 719
    } // library marker kkossev.deviceProfileLib, line 720
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 721
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 722
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 723
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 724
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 725
        return false // library marker kkossev.deviceProfileLib, line 726
    } // library marker kkossev.deviceProfileLib, line 727
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 728
    def func, funcResult // library marker kkossev.deviceProfileLib, line 729
    try { // library marker kkossev.deviceProfileLib, line 730
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 731
        // added 01/25/2025 : the commands now can be shorted : instead of a map kay and value 'printFingerprints':'printFingerprints' we can skip the value when it is the same:  'printFingerprints:'  - the value is the same as the key // library marker kkossev.deviceProfileLib, line 732
        if (func == null || func == '') { // library marker kkossev.deviceProfileLib, line 733
            func = command // library marker kkossev.deviceProfileLib, line 734
        } // library marker kkossev.deviceProfileLib, line 735
        if (val != null && val != '') { // library marker kkossev.deviceProfileLib, line 736
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 737
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 738
        } // library marker kkossev.deviceProfileLib, line 739
        else { // library marker kkossev.deviceProfileLib, line 740
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 741
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 742
        } // library marker kkossev.deviceProfileLib, line 743
    } // library marker kkossev.deviceProfileLib, line 744
    catch (e) { // library marker kkossev.deviceProfileLib, line 745
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 746
        return false // library marker kkossev.deviceProfileLib, line 747
    } // library marker kkossev.deviceProfileLib, line 748
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 749
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 750
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 751
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 752
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 753
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 754
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 755
        } // library marker kkossev.deviceProfileLib, line 756
    } // library marker kkossev.deviceProfileLib, line 757
    else if (funcResult == null) { // library marker kkossev.deviceProfileLib, line 758
        return false // library marker kkossev.deviceProfileLib, line 759
    } // library marker kkossev.deviceProfileLib, line 760
     else { // library marker kkossev.deviceProfileLib, line 761
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 762
        return false // library marker kkossev.deviceProfileLib, line 763
    } // library marker kkossev.deviceProfileLib, line 764
    return true // library marker kkossev.deviceProfileLib, line 765
} // library marker kkossev.deviceProfileLib, line 766

/** // library marker kkossev.deviceProfileLib, line 768
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 769
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 770
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 771
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 772
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 773
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 774
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 775
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 776
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 777
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 778
 */ // library marker kkossev.deviceProfileLib, line 779
public Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 780
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 781
    Map input = [:] // library marker kkossev.deviceProfileLib, line 782
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 783
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 784
    Object preference // library marker kkossev.deviceProfileLib, line 785
    try { preference = DEVICE?.preferences["$param"] } // library marker kkossev.deviceProfileLib, line 786
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 787
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 788
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } } // library marker kkossev.deviceProfileLib, line 789
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 790
    /* // library marker kkossev.deviceProfileLib, line 791
    // TODO - check if this is neccessary? isTuyaDP is not defined! // library marker kkossev.deviceProfileLib, line 792
    try { isTuyaDP = preference.isNumber() } // library marker kkossev.deviceProfileLib, line 793
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 794
    */ // library marker kkossev.deviceProfileLib, line 795
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 796
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 797
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 798
    if (foundMap == null || foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 799
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 800
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) { // library marker kkossev.deviceProfileLib, line 801
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" } // library marker kkossev.deviceProfileLib, line 802
        return [:] // library marker kkossev.deviceProfileLib, line 803
    } // library marker kkossev.deviceProfileLib, line 804
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 805
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 806
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 807
    //input.description = (foundMap.description ?: foundMap.title)?.replaceAll(/<\/?b>/, '')  // if description is not defined, use the title // library marker kkossev.deviceProfileLib, line 808
    input.description = foundMap.description ?: ''   // if description is not defined, skip it // library marker kkossev.deviceProfileLib, line 809
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 810
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 811
            //input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 812
            input.range = "${Math.ceil(foundMap.min) as int}..${Math.ceil(foundMap.max) as int}" // library marker kkossev.deviceProfileLib, line 813
        } // library marker kkossev.deviceProfileLib, line 814
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 815
            if (input.description != '') { input.description += '<br>' } // library marker kkossev.deviceProfileLib, line 816
            input.description += "<i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 817
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 818
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 819
            } // library marker kkossev.deviceProfileLib, line 820
        } // library marker kkossev.deviceProfileLib, line 821
    } // library marker kkossev.deviceProfileLib, line 822
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 823
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 824
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 825
    }/* // library marker kkossev.deviceProfileLib, line 826
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 827
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 828
    }*/ // library marker kkossev.deviceProfileLib, line 829
    else { // library marker kkossev.deviceProfileLib, line 830
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 831
        return [:] // library marker kkossev.deviceProfileLib, line 832
    } // library marker kkossev.deviceProfileLib, line 833
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 834
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 835
    } // library marker kkossev.deviceProfileLib, line 836
    return input // library marker kkossev.deviceProfileLib, line 837
} // library marker kkossev.deviceProfileLib, line 838

/** // library marker kkossev.deviceProfileLib, line 840
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 841
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 842
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 843
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 844
 */ // library marker kkossev.deviceProfileLib, line 845
public List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 846
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 847
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 848
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 849
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 850
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 851
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 852
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 853
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].description ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 854
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 855
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 856
            } // library marker kkossev.deviceProfileLib, line 857
        } // library marker kkossev.deviceProfileLib, line 858
    } // library marker kkossev.deviceProfileLib, line 859
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 860
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 861
    } // library marker kkossev.deviceProfileLib, line 862
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 863
} // library marker kkossev.deviceProfileLib, line 864

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 866
public void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 867
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 868
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 869
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 870
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 871
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 872
    } // library marker kkossev.deviceProfileLib, line 873
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 874
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 875
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 876
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 877
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 878
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 879
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 880
    } else { // library marker kkossev.deviceProfileLib, line 881
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 882
    } // library marker kkossev.deviceProfileLib, line 883
} // library marker kkossev.deviceProfileLib, line 884

public List<String> refreshFromConfigureReadList(List<String> refreshList) { // library marker kkossev.deviceProfileLib, line 886
    logDebug "refreshFromConfigureReadList(${refreshList})" // library marker kkossev.deviceProfileLib, line 887
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 888
    if (refreshList != null && !refreshList.isEmpty()) { // library marker kkossev.deviceProfileLib, line 889
        //List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 890
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 891
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 892
            if (k != null) { // library marker kkossev.deviceProfileLib, line 893
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 894
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 895
                if (map != null) { // library marker kkossev.deviceProfileLib, line 896
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 897
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 898
                } // library marker kkossev.deviceProfileLib, line 899
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 900
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 901
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 902
                } // library marker kkossev.deviceProfileLib, line 903
            } // library marker kkossev.deviceProfileLib, line 904
        } // library marker kkossev.deviceProfileLib, line 905
    } // library marker kkossev.deviceProfileLib, line 906
    return cmds // library marker kkossev.deviceProfileLib, line 907
} // library marker kkossev.deviceProfileLib, line 908

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLib, line 910
public List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLib, line 911
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLib, line 912
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 913
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLib, line 914
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 915
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 916
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 917
            if (k != null) { // library marker kkossev.deviceProfileLib, line 918
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 919
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 920
                if (map != null) { // library marker kkossev.deviceProfileLib, line 921
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 922
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 923
                } // library marker kkossev.deviceProfileLib, line 924
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 925
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 926
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 927
                } // library marker kkossev.deviceProfileLib, line 928
            } // library marker kkossev.deviceProfileLib, line 929
        } // library marker kkossev.deviceProfileLib, line 930
    } // library marker kkossev.deviceProfileLib, line 931
    return cmds // library marker kkossev.deviceProfileLib, line 932
} // library marker kkossev.deviceProfileLib, line 933

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 935
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 936
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 937
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 938
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 939
    return cmds // library marker kkossev.deviceProfileLib, line 940
} // library marker kkossev.deviceProfileLib, line 941

// TODO ! - remove? // library marker kkossev.deviceProfileLib, line 943
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 944
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 945
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 946
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 947
    return cmds // library marker kkossev.deviceProfileLib, line 948
} // library marker kkossev.deviceProfileLib, line 949

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 951
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 952
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 953
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 954
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 955
    return cmds // library marker kkossev.deviceProfileLib, line 956
} // library marker kkossev.deviceProfileLib, line 957

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 959
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 960
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 961
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 962
    } // library marker kkossev.deviceProfileLib, line 963
} // library marker kkossev.deviceProfileLib, line 964

public void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 966
    String ps = DEVICE?.device?.powerSource // library marker kkossev.deviceProfileLib, line 967
    logDebug "initEventsDeviceProfile(${fullInit}) for deviceProfile=${state.deviceProfile} DEVICE?.device?.powerSource=${ps} ps.isEmpty()=${ps?.isEmpty()}" // library marker kkossev.deviceProfileLib, line 968
    if (ps != null && !ps.isEmpty()) { // library marker kkossev.deviceProfileLib, line 969
        sendEvent(name: 'powerSource', value: ps, descriptionText: "Power Source set to '${ps}'", type: 'digital') // library marker kkossev.deviceProfileLib, line 970
    } // library marker kkossev.deviceProfileLib, line 971
} // library marker kkossev.deviceProfileLib, line 972

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 974

// // library marker kkossev.deviceProfileLib, line 976
// called from parse() // library marker kkossev.deviceProfileLib, line 977
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profile // library marker kkossev.deviceProfileLib, line 978
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 979
// // library marker kkossev.deviceProfileLib, line 980
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 981
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 982
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 983
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 984
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 985
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 986
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 987
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 988
} // library marker kkossev.deviceProfileLib, line 989

// // library marker kkossev.deviceProfileLib, line 991
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 992
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profile // library marker kkossev.deviceProfileLib, line 993
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 994
// // library marker kkossev.deviceProfileLib, line 995
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 996
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 997
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 998
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 999
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 1000
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 1001
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 1002
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 1003
} // library marker kkossev.deviceProfileLib, line 1004

// all DPs are spammy - sent periodically! (this function is not used?) // library marker kkossev.deviceProfileLib, line 1006
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 1007
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 1008
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 1009
    return isSpammy // library marker kkossev.deviceProfileLib, line 1010
} // library marker kkossev.deviceProfileLib, line 1011

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1013
private List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 1014
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 1015
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 1016
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 1017
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1018
    } // library marker kkossev.deviceProfileLib, line 1019
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1020
} // library marker kkossev.deviceProfileLib, line 1021

private List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 1023
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 1024
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1025
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 1026
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1027
    } // library marker kkossev.deviceProfileLib, line 1028
    else { // library marker kkossev.deviceProfileLib, line 1029
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 1030
    } // library marker kkossev.deviceProfileLib, line 1031
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 1032
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1033
} // library marker kkossev.deviceProfileLib, line 1034

private List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 1036
    Double convertedValue // library marker kkossev.deviceProfileLib, line 1037
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1038
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 1039
    } // library marker kkossev.deviceProfileLib, line 1040
    else { // library marker kkossev.deviceProfileLib, line 1041
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1042
    } // library marker kkossev.deviceProfileLib, line 1043
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1044
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1045
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1046
} // library marker kkossev.deviceProfileLib, line 1047

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1049
private List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1050
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1051
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1052
    def convertedValue // library marker kkossev.deviceProfileLib, line 1053
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1054
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1055
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1056
    } // library marker kkossev.deviceProfileLib, line 1057
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1058
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1059
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1060
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1061
            isEqual = false // library marker kkossev.deviceProfileLib, line 1062
        } // library marker kkossev.deviceProfileLib, line 1063
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1064
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1065
        } // library marker kkossev.deviceProfileLib, line 1066
    } // library marker kkossev.deviceProfileLib, line 1067
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1068
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1069
} // library marker kkossev.deviceProfileLib, line 1070

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1072
private List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1073
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1074
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1075
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1076
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1077
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1078
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1079
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1080
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1081
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1082
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1083
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1084
            break // library marker kkossev.deviceProfileLib, line 1085
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1086
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1087
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1088
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1089
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1090
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1091
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1092
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1093
            } // library marker kkossev.deviceProfileLib, line 1094
            else { // library marker kkossev.deviceProfileLib, line 1095
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1096
            } // library marker kkossev.deviceProfileLib, line 1097
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1098
            break // library marker kkossev.deviceProfileLib, line 1099
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1100
        case 'number' : // library marker kkossev.deviceProfileLib, line 1101
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1102
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1103
            break // library marker kkossev.deviceProfileLib, line 1104
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1105
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1106
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1107
            break // library marker kkossev.deviceProfileLib, line 1108
        default : // library marker kkossev.deviceProfileLib, line 1109
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1110
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1111
    } // library marker kkossev.deviceProfileLib, line 1112
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1113
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1114
    } // library marker kkossev.deviceProfileLib, line 1115
    // // library marker kkossev.deviceProfileLib, line 1116
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1117
} // library marker kkossev.deviceProfileLib, line 1118

// // library marker kkossev.deviceProfileLib, line 1120
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1121
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1122
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1123
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1124
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1125
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1126
// // library marker kkossev.deviceProfileLib, line 1127
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1128
// // library marker kkossev.deviceProfileLib, line 1129
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1130
// // library marker kkossev.deviceProfileLib, line 1131
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1132
private List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1133
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1134
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1135
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1136
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1137
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1138
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1139
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1140
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1141
            break // library marker kkossev.deviceProfileLib, line 1142
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1143
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1144
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1145
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1146
            logTrace "latestEvent is ${latestEvent} dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1147
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1148
            if (dataType == null || dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1149
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1150
            } // library marker kkossev.deviceProfileLib, line 1151
            else { // library marker kkossev.deviceProfileLib, line 1152
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1153
            } // library marker kkossev.deviceProfileLib, line 1154
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1155
            break // library marker kkossev.deviceProfileLib, line 1156
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1157
        case 'number' : // library marker kkossev.deviceProfileLib, line 1158
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1159
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1160
            break // library marker kkossev.deviceProfileLib, line 1161
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1162
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1163
            break // library marker kkossev.deviceProfileLib, line 1164
        default : // library marker kkossev.deviceProfileLib, line 1165
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1166
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1167
    } // library marker kkossev.deviceProfileLib, line 1168
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1169
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1170
} // library marker kkossev.deviceProfileLib, line 1171

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1173
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1174
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1175
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1176
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1177
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1178
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1179
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1180
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1181
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1182
    } // library marker kkossev.deviceProfileLib, line 1183
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1184
    try { // library marker kkossev.deviceProfileLib, line 1185
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1186
    } // library marker kkossev.deviceProfileLib, line 1187
    catch (e) { // library marker kkossev.deviceProfileLib, line 1188
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1189
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1190
    } // library marker kkossev.deviceProfileLib, line 1191
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1192
    return fncmd // library marker kkossev.deviceProfileLib, line 1193
} // library marker kkossev.deviceProfileLib, line 1194

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1196
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1197
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1198
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1199
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1200
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1201
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1202

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1204
    if (attribMap == null || attribMap?.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1205

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1207
    int value // library marker kkossev.deviceProfileLib, line 1208
    try { // library marker kkossev.deviceProfileLib, line 1209
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1210
    } // library marker kkossev.deviceProfileLib, line 1211
    catch (e) { // library marker kkossev.deviceProfileLib, line 1212
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1213
        return false // library marker kkossev.deviceProfileLib, line 1214
    } // library marker kkossev.deviceProfileLib, line 1215
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1216
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1217
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1218
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1219
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1220
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1221
        return false // library marker kkossev.deviceProfileLib, line 1222
    } // library marker kkossev.deviceProfileLib, line 1223
    value = convertSignedInts(value, foundItem) // library marker kkossev.deviceProfileLib, line 1224
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1225
} // library marker kkossev.deviceProfileLib, line 1226

/** // library marker kkossev.deviceProfileLib, line 1228
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1229
 * // library marker kkossev.deviceProfileLib, line 1230
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1231
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1232
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1233
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1234
 * // library marker kkossev.deviceProfileLib, line 1235
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1236
 */ // library marker kkossev.deviceProfileLib, line 1237
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1238
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1239
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1240
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1241
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1242

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1244
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1245

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1247
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1248
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1249
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1250
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1251
        return false // library marker kkossev.deviceProfileLib, line 1252
    } // library marker kkossev.deviceProfileLib, line 1253
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1254
} // library marker kkossev.deviceProfileLib, line 1255

/* // library marker kkossev.deviceProfileLib, line 1257
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1258
 */ // library marker kkossev.deviceProfileLib, line 1259
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1260
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1261
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1262
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1263
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1264
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1265
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1266
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1267
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1268
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1269
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1270
        } // library marker kkossev.deviceProfileLib, line 1271
    } // library marker kkossev.deviceProfileLib, line 1272
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1273

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1275
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1276
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1277
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1278
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1279
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences?.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1280
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1281
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1282
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1283
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1284
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1285
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1286
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1287
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1288
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1289
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1290
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1291

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1293
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1294
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1295
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1296
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1297
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1298
            if (settings.logEnable) { logInfo "${descText} (Debug logging is enabled)" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1299
        } // library marker kkossev.deviceProfileLib, line 1300
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1301
    } // library marker kkossev.deviceProfileLib, line 1302

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1304
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1305
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1306
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1307
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1308
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1309
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1310
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1311
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1312
            } // library marker kkossev.deviceProfileLib, line 1313
        } // library marker kkossev.deviceProfileLib, line 1314
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1315
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1316
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1317
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1318
            } // library marker kkossev.deviceProfileLib, line 1319
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1320
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1321
            try { // library marker kkossev.deviceProfileLib, line 1322
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1323
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1324
            } // library marker kkossev.deviceProfileLib, line 1325
            catch (e) { // library marker kkossev.deviceProfileLib, line 1326
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1327
            } // library marker kkossev.deviceProfileLib, line 1328
        } // library marker kkossev.deviceProfileLib, line 1329
    } // library marker kkossev.deviceProfileLib, line 1330
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1331
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1332
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1333
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1334
    } // library marker kkossev.deviceProfileLib, line 1335

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1337
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1338
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1339
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1340
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1341
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1342
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1343
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1344
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1345
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1346
            } // library marker kkossev.deviceProfileLib, line 1347
            if (foundItem.processDuplicated == true) { // library marker kkossev.deviceProfileLib, line 1348
                logDebug 'processDuplicated=true -> continue' // library marker kkossev.deviceProfileLib, line 1349
            } // library marker kkossev.deviceProfileLib, line 1350

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1352
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1353
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1354
            // continue ... // library marker kkossev.deviceProfileLib, line 1355
            } // library marker kkossev.deviceProfileLib, line 1356

            else { // library marker kkossev.deviceProfileLib, line 1358
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1359
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1360
                } // library marker kkossev.deviceProfileLib, line 1361
                else { // library marker kkossev.deviceProfileLib, line 1362
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLib, line 1363
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1364
                } // library marker kkossev.deviceProfileLib, line 1365
            } // library marker kkossev.deviceProfileLib, line 1366
        } // library marker kkossev.deviceProfileLib, line 1367

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1369
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1370
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1371
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1372
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1373
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1374
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1375
        } // library marker kkossev.deviceProfileLib, line 1376
        else { // library marker kkossev.deviceProfileLib, line 1377
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1378
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1379
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1380
                logTrace "event ${name} sent w/ valueScaled ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1381
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1382
            } // library marker kkossev.deviceProfileLib, line 1383
        } // library marker kkossev.deviceProfileLib, line 1384
    } // library marker kkossev.deviceProfileLib, line 1385
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1386
} // library marker kkossev.deviceProfileLib, line 1387

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1389
public boolean validateAndFixPreferences(String debugStr) { return validateAndFixPreferences(debugStr.toBoolean() as boolean) } // library marker kkossev.deviceProfileLib, line 1390
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1391
    //debug = true // library marker kkossev.deviceProfileLib, line 1392
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1393
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLib, line 1394
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLib, line 1395
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1396
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLib, line 1397
    String settingType = '' // library marker kkossev.deviceProfileLib, line 1398
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1399
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1400
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1401
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1402
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1403
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1404
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1405
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1406
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1407
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1408
            try { // library marker kkossev.deviceProfileLib, line 1409
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1410
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1411
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLib, line 1412
            } // library marker kkossev.deviceProfileLib, line 1413
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1414
            try { // library marker kkossev.deviceProfileLib, line 1415
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1416
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1417
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1418
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1419
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1420
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1421
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1422
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1423
                    } // library marker kkossev.deviceProfileLib, line 1424
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1425
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1426
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1427
                    } else { // library marker kkossev.deviceProfileLib, line 1428
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1429
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1430
                    } // library marker kkossev.deviceProfileLib, line 1431
                } // library marker kkossev.deviceProfileLib, line 1432
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1433
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1434
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1435
            } // library marker kkossev.deviceProfileLib, line 1436
            catch (e) { // library marker kkossev.deviceProfileLib, line 1437
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1438
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1439
                try { // library marker kkossev.deviceProfileLib, line 1440
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1441
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1442
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1443
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1444
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1445
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLib, line 1446
                } // library marker kkossev.deviceProfileLib, line 1447
            } // library marker kkossev.deviceProfileLib, line 1448
        } // library marker kkossev.deviceProfileLib, line 1449
        total ++ // library marker kkossev.deviceProfileLib, line 1450
    } // library marker kkossev.deviceProfileLib, line 1451
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1452
    return true // library marker kkossev.deviceProfileLib, line 1453
} // library marker kkossev.deviceProfileLib, line 1454

public String fingerprintIt(Map profileMap, Map fingerprint) { // library marker kkossev.deviceProfileLib, line 1456
    if (profileMap == null) { return 'profileMap is null' } // library marker kkossev.deviceProfileLib, line 1457
    if (fingerprint == null) { return 'fingerprint is null' } // library marker kkossev.deviceProfileLib, line 1458
    Map defaultFingerprint = profileMap.defaultFingerprint ?: [:] // library marker kkossev.deviceProfileLib, line 1459
    // if there is no defaultFingerprint, use the fingerprint as is // library marker kkossev.deviceProfileLib, line 1460
    if (defaultFingerprint == [:]) { // library marker kkossev.deviceProfileLib, line 1461
        return fingerprint.toString() // library marker kkossev.deviceProfileLib, line 1462
    } // library marker kkossev.deviceProfileLib, line 1463
    // for the missing keys, use the default values // library marker kkossev.deviceProfileLib, line 1464
    String fingerprintStr = '' // library marker kkossev.deviceProfileLib, line 1465
    defaultFingerprint.each { key, value -> // library marker kkossev.deviceProfileLib, line 1466
        String keyValue = fingerprint[key] ?: value // library marker kkossev.deviceProfileLib, line 1467
        fingerprintStr += "${key}:'${keyValue}', " // library marker kkossev.deviceProfileLib, line 1468
    } // library marker kkossev.deviceProfileLib, line 1469
    // remove the last comma and space // library marker kkossev.deviceProfileLib, line 1470
    fingerprintStr = fingerprintStr[0..-3] // library marker kkossev.deviceProfileLib, line 1471
    return fingerprintStr // library marker kkossev.deviceProfileLib, line 1472
} // library marker kkossev.deviceProfileLib, line 1473

public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1475
    int count = 0 // library marker kkossev.deviceProfileLib, line 1476
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1477
        logInfo "Device Profile: ${profileName}" // library marker kkossev.deviceProfileLib, line 1478
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1479
            log.info "${fingerprintIt(profileMap, fingerprint)}" // library marker kkossev.deviceProfileLib, line 1480
            count++ // library marker kkossev.deviceProfileLib, line 1481
        } // library marker kkossev.deviceProfileLib, line 1482
    } // library marker kkossev.deviceProfileLib, line 1483
    logInfo "Total fingerprints: ${count}" // library marker kkossev.deviceProfileLib, line 1484
} // library marker kkossev.deviceProfileLib, line 1485

public void printPreferences() { // library marker kkossev.deviceProfileLib, line 1487
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 1488
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 1489
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 1490
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLib, line 1491
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 1492
                log.info inputMap // library marker kkossev.deviceProfileLib, line 1493
            } // library marker kkossev.deviceProfileLib, line 1494
        } // library marker kkossev.deviceProfileLib, line 1495
    } // library marker kkossev.deviceProfileLib, line 1496
} // library marker kkossev.deviceProfileLib, line 1497

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDouble, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/commonLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-commonLib', // library marker kkossev.commonLib, line 4
    version: '3.5.1' // library marker kkossev.commonLib, line 5
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
  * ver. 3.5.1  2025-07-23 kkossev  - Aqara W100 destEndpoint: 0x01 patch // library marker kkossev.commonLib, line 47
  * ver. 3.5.2  2025-08-13 kkossev  - (dev.branch) Status attribute renamed to _status_ // library marker kkossev.commonLib, line 48
  * // library marker kkossev.commonLib, line 49
  *                                   TODO: add GetInfo (endpoints list) command (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 50
  *                                   TODO: make the configure() without parameter smart - analyze the State variables and call delete states.... call ActiveAndpoints() or/amd initialize() or/and configure() // library marker kkossev.commonLib, line 51
  *                                   TODO: check - offlineCtr is not increasing? (ZBMicro); // library marker kkossev.commonLib, line 52
  *                                   TODO: check deviceCommandTimeout() // library marker kkossev.commonLib, line 53
  *                                   TODO: when device rejoins the network, read the battery percentage again (probably in custom handler, not for all devices) // library marker kkossev.commonLib, line 54
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 55
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 56
  *                                   TODO: MOVE ZDO counters to health state? // library marker kkossev.commonLib, line 57
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 58
  *                                   TODO: Versions of the main module + included libraries (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 59
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 60
  * // library marker kkossev.commonLib, line 61
*/ // library marker kkossev.commonLib, line 62

String commonLibVersion() { '3.5.2' } // library marker kkossev.commonLib, line 64
String commonLibStamp() { '2025/08/13 8:18 PM' } // library marker kkossev.commonLib, line 65

import groovy.transform.Field // library marker kkossev.commonLib, line 67
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 68
import hubitat.device.Protocol // library marker kkossev.commonLib, line 69
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 70
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 71
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 72
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 73
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 74
import java.math.BigDecimal // library marker kkossev.commonLib, line 75

metadata { // library marker kkossev.commonLib, line 77
        if (_DEBUG) { // library marker kkossev.commonLib, line 78
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 79
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 80
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 81
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 82
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 83
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 84
            ] // library marker kkossev.commonLib, line 85
        } // library marker kkossev.commonLib, line 86

        // common capabilities for all device types // library marker kkossev.commonLib, line 88
        capability 'Configuration' // library marker kkossev.commonLib, line 89
        capability 'Refresh' // library marker kkossev.commonLib, line 90
        capability 'HealthCheck' // library marker kkossev.commonLib, line 91
        capability 'PowerSource'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 92

        // common attributes for all device types // library marker kkossev.commonLib, line 94
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 95
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 96
        attribute '_status_', 'string' // library marker kkossev.commonLib, line 97

        // common commands for all device types // library marker kkossev.commonLib, line 99
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM', constraints: ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 100

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 102
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 103

    preferences { // library marker kkossev.commonLib, line 105
        // txtEnable and logEnable moved to the custom driver settings - copy& paste there ... // library marker kkossev.commonLib, line 106
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 107
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 108

        if (device) { // library marker kkossev.commonLib, line 110
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'The advanced options should be already automatically set in an optimal way for your device...Click on the "Save and Close" button when toggling this option!', defaultValue: false // library marker kkossev.commonLib, line 111
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 112
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 113
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 114
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 115
            } // library marker kkossev.commonLib, line 116
        } // library marker kkossev.commonLib, line 117
    } // library marker kkossev.commonLib, line 118
} // library marker kkossev.commonLib, line 119

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 121
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 122
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 123
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 124
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 125
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 126
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 127
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 128
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 129
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 130
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 131

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 133
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 134
] // library marker kkossev.commonLib, line 135
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 136
    defaultValue: 240, options: [2: 'Every 2 Mins', 10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 137
] // library marker kkossev.commonLib, line 138

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 140
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'], // library marker kkossev.commonLib, line 141
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 142
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 143
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 144
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 145
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 146
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 147
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 148
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 149
    '           -             '  : [key:1, function: 'configureHelp'] // library marker kkossev.commonLib, line 150
] // library marker kkossev.commonLib, line 151

public boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 153

/** // library marker kkossev.commonLib, line 155
 * Parse Zigbee message // library marker kkossev.commonLib, line 156
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 157
 */ // library marker kkossev.commonLib, line 158
public void parse(final String description) { // library marker kkossev.commonLib, line 159
    Map stateCopy = state            // .clone() throws java.lang.CloneNotSupportedException in HE platform version 2.4.1.155 ! // library marker kkossev.commonLib, line 160
    checkDriverVersion(stateCopy)    // +1 ms // library marker kkossev.commonLib, line 161
    if (state.stats != null) { state.stats?.rxCtr= (state.stats?.rxCtr ?: 0) + 1 } else { state.stats = [:] }  // updateRxStats(state) // +1 ms // library marker kkossev.commonLib, line 162
    if (state.lastRx != null) { state.lastRx?.timeStamp = unix2formattedDate(now()) } else { state.lastRx = [:] } // library marker kkossev.commonLib, line 163
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 164
    setHealthStatusOnline(state)    // +2 ms // library marker kkossev.commonLib, line 165

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 167
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 168
        if (this.respondsTo('customParseIasMessage')) { customParseIasMessage(description) } // library marker kkossev.commonLib, line 169
        else if (this.respondsTo('standardParseIasMessage')) { standardParseIasMessage(description) } // library marker kkossev.commonLib, line 170
        else if (this.respondsTo('parseIasMessage')) { parseIasMessage(description) } // library marker kkossev.commonLib, line 171
        else { logDebug "ignored IAS zone status (no IAS parser) description: $description" } // library marker kkossev.commonLib, line 172
        return // library marker kkossev.commonLib, line 173
    } // library marker kkossev.commonLib, line 174
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 175
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 176
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 177
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 178
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 179
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 180
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 181
        return // library marker kkossev.commonLib, line 182
    } // library marker kkossev.commonLib, line 183

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 185
        return // library marker kkossev.commonLib, line 186
    } // library marker kkossev.commonLib, line 187
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 188

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 190
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 191

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 193
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 194
        return // library marker kkossev.commonLib, line 195
    } // library marker kkossev.commonLib, line 196
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 197
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 198
        return // library marker kkossev.commonLib, line 199
    } // library marker kkossev.commonLib, line 200
    // // library marker kkossev.commonLib, line 201
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 202
    // // library marker kkossev.commonLib, line 203
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 204
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 205
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 206
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 207
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 208
            } // library marker kkossev.commonLib, line 209
            break // library marker kkossev.commonLib, line 210
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 211
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 212
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 213
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 214
            } // library marker kkossev.commonLib, line 215
            break // library marker kkossev.commonLib, line 216
        default: // library marker kkossev.commonLib, line 217
            if (settings.logEnable) { // library marker kkossev.commonLib, line 218
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 219
            } // library marker kkossev.commonLib, line 220
            break // library marker kkossev.commonLib, line 221
    } // library marker kkossev.commonLib, line 222
} // library marker kkossev.commonLib, line 223

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 225
    0x0000: 'Basic',             0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x0006: 'OnOff',           0x0007:'onOffConfiguration',      0x0008: 'LevelControl',  // library marker kkossev.commonLib, line 226
    0x000C: 'AnalogInput',       0x0012: 'MultistateInput',  0x0020: 'PollControl',      0x0102: 'WindowCovering',   0x0201: 'Thermostat',  0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 227
    0x0400: 'Illuminance',       0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 228
    0x0B04: 'ElectricalMeasure', 0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC03: 'FC03',            0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 229
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 230
] // library marker kkossev.commonLib, line 231

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 233
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 234
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 235
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 236
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 237
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 238
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 239
        return false // library marker kkossev.commonLib, line 240
    } // library marker kkossev.commonLib, line 241
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 242
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 243
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 244
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 245
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 246
        return true // library marker kkossev.commonLib, line 247
    } // library marker kkossev.commonLib, line 248
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 249
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 250
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 251
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 252
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 253
        return true // library marker kkossev.commonLib, line 254
    } // library marker kkossev.commonLib, line 255
    if (device?.getDataValue('model') != 'ZigUSB' && descMap.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 256
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 257
    } // library marker kkossev.commonLib, line 258
    return false // library marker kkossev.commonLib, line 259
} // library marker kkossev.commonLib, line 260

// not used - throws exception :  error groovy.lang.MissingPropertyException: No such property: rxCtr for class: java.lang.String on line 1568 (method parse) // library marker kkossev.commonLib, line 262
private static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 263
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 264
} // library marker kkossev.commonLib, line 265

public boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 267
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 268
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 269
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 270
    } // library marker kkossev.commonLib, line 271
    return false // library marker kkossev.commonLib, line 272
} // library marker kkossev.commonLib, line 273

public boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 275
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 276
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 277
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 278
    } // library marker kkossev.commonLib, line 279
    return false // library marker kkossev.commonLib, line 280
} // library marker kkossev.commonLib, line 281

// not used? // library marker kkossev.commonLib, line 283
public boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 284
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 285
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 286
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 287
    } // library marker kkossev.commonLib, line 288
    return false // library marker kkossev.commonLib, line 289
} // library marker kkossev.commonLib, line 290

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 292
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 293
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 294
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 295
] // library marker kkossev.commonLib, line 296

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 298
private void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 299
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 300
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 301
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 302
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 303
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 304
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 305
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 306
    List<String> cmds = [] // library marker kkossev.commonLib, line 307
    switch (clusterId) { // library marker kkossev.commonLib, line 308
        case 0x0005 : // library marker kkossev.commonLib, line 309
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 310
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 311
            // send the active endpoint response // library marker kkossev.commonLib, line 312
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 313
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 314
            break // library marker kkossev.commonLib, line 315
        case 0x0006 : // library marker kkossev.commonLib, line 316
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 317
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 318
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 319
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 320
            break // library marker kkossev.commonLib, line 321
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 322
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 323
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 324
            break // library marker kkossev.commonLib, line 325
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 326
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 327
            if (this.respondsTo('parseSimpleDescriptorResponse')) { parseSimpleDescriptorResponse(descMap) } // library marker kkossev.commonLib, line 328
            break // library marker kkossev.commonLib, line 329
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 330
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 331
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 332
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 333
            break // library marker kkossev.commonLib, line 334
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 335
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 336
            break // library marker kkossev.commonLib, line 337
        case 0x0002 : // Node Descriptor Request // library marker kkossev.commonLib, line 338
        case 0x0036 : // Permit Joining Request // library marker kkossev.commonLib, line 339
        case 0x8022 : // unbind request // library marker kkossev.commonLib, line 340
        case 0x8034 : // leave response // library marker kkossev.commonLib, line 341
            if (settings?.logEnable) { log.debug "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 342
            break // library marker kkossev.commonLib, line 343
        default : // library marker kkossev.commonLib, line 344
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 345
            break // library marker kkossev.commonLib, line 346
    } // library marker kkossev.commonLib, line 347
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 348
} // library marker kkossev.commonLib, line 349

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 351
private void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 352
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 353
    switch (commandId) { // library marker kkossev.commonLib, line 354
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 355
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 356
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 357
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 358
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 359
        default: // library marker kkossev.commonLib, line 360
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 361
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 362
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 363
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 364
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 365
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 366
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 367
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 368
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 369
            } // library marker kkossev.commonLib, line 370
            break // library marker kkossev.commonLib, line 371
    } // library marker kkossev.commonLib, line 372
} // library marker kkossev.commonLib, line 373

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 375
private void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 376
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 377
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 378
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 379
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 380
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 381
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 382
    } // library marker kkossev.commonLib, line 383
    else { // library marker kkossev.commonLib, line 384
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 385
    } // library marker kkossev.commonLib, line 386
} // library marker kkossev.commonLib, line 387

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 389
private void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 390
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 391
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 392
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 393
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 394
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 395
    } // library marker kkossev.commonLib, line 396
    else { // library marker kkossev.commonLib, line 397
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 398
    } // library marker kkossev.commonLib, line 399
} // library marker kkossev.commonLib, line 400

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 402
private void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 403
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 404
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 405
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 406
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 407
        state.reportingEnabled = true // library marker kkossev.commonLib, line 408
    } // library marker kkossev.commonLib, line 409
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 410
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 411
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 412
    } else { // library marker kkossev.commonLib, line 413
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 414
    } // library marker kkossev.commonLib, line 415
} // library marker kkossev.commonLib, line 416

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 418
private void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 419
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 420
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 421
    if (status == 0) { // library marker kkossev.commonLib, line 422
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 423
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 424
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 425
        int delta = 0 // library marker kkossev.commonLib, line 426
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 427
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 428
        } // library marker kkossev.commonLib, line 429
        else { // library marker kkossev.commonLib, line 430
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 431
        } // library marker kkossev.commonLib, line 432
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 433
    } // library marker kkossev.commonLib, line 434
    else { // library marker kkossev.commonLib, line 435
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 436
    } // library marker kkossev.commonLib, line 437
} // library marker kkossev.commonLib, line 438

private Boolean executeCustomHandler(String handlerName, Object handlerArgs) { // library marker kkossev.commonLib, line 440
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 441
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 442
        return false // library marker kkossev.commonLib, line 443
    } // library marker kkossev.commonLib, line 444
    // execute the customHandler function // library marker kkossev.commonLib, line 445
    Boolean result = false // library marker kkossev.commonLib, line 446
    try { // library marker kkossev.commonLib, line 447
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 448
    } // library marker kkossev.commonLib, line 449
    catch (e) { // library marker kkossev.commonLib, line 450
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 451
        return false // library marker kkossev.commonLib, line 452
    } // library marker kkossev.commonLib, line 453
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 454
    return result // library marker kkossev.commonLib, line 455
} // library marker kkossev.commonLib, line 456

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 458
private void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 459
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 460
    final String commandId = data[0] // library marker kkossev.commonLib, line 461
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 462
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 463
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 464
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 465
    } else { // library marker kkossev.commonLib, line 466
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 467
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 468
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 469
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 470
        } // library marker kkossev.commonLib, line 471
    } // library marker kkossev.commonLib, line 472
} // library marker kkossev.commonLib, line 473

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 475
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 476
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 477
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 478

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 480
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 481
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 482
] // library marker kkossev.commonLib, line 483

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 485
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 486
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 487
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 488
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 489
] // library marker kkossev.commonLib, line 490

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 492
private BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 493
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 494
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 495
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 496
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 497
    return avg // library marker kkossev.commonLib, line 498
} // library marker kkossev.commonLib, line 499

private void handlePingResponse() { // library marker kkossev.commonLib, line 501
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 502
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 503
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 504

    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 506
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 507
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 508
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 509
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 510
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 511
        sendRttEvent() // library marker kkossev.commonLib, line 512
    } // library marker kkossev.commonLib, line 513
    else { // library marker kkossev.commonLib, line 514
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 515
    } // library marker kkossev.commonLib, line 516
    state.states['isPing'] = false // library marker kkossev.commonLib, line 517
} // library marker kkossev.commonLib, line 518

/* // library marker kkossev.commonLib, line 520
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 521
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 522
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 523
*/ // library marker kkossev.commonLib, line 524
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 525

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 527
private void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 528
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 529
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 530
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 531
    boolean isPing = state.states?.isPing ?: false // library marker kkossev.commonLib, line 532
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 533
        case 0x0000: // library marker kkossev.commonLib, line 534
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 535
            break // library marker kkossev.commonLib, line 536
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 537
            if (isPing) { // library marker kkossev.commonLib, line 538
                handlePingResponse() // library marker kkossev.commonLib, line 539
            } // library marker kkossev.commonLib, line 540
            else { // library marker kkossev.commonLib, line 541
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 542
            } // library marker kkossev.commonLib, line 543
            break // library marker kkossev.commonLib, line 544
        case 0x0004: // library marker kkossev.commonLib, line 545
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 546
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 547
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 548
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 549
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 550
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 551
            } // library marker kkossev.commonLib, line 552
            break // library marker kkossev.commonLib, line 553
        case 0x0005: // library marker kkossev.commonLib, line 554
            if (isPing) { // library marker kkossev.commonLib, line 555
                handlePingResponse() // library marker kkossev.commonLib, line 556
            } // library marker kkossev.commonLib, line 557
            else { // library marker kkossev.commonLib, line 558
                logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 559
                // received device model Remote Control N2 // library marker kkossev.commonLib, line 560
                String model = device.getDataValue('model') // library marker kkossev.commonLib, line 561
                if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 562
                    logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 563
                    device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 564
                } // library marker kkossev.commonLib, line 565
            } // library marker kkossev.commonLib, line 566
            break // library marker kkossev.commonLib, line 567
        case 0x0007: // library marker kkossev.commonLib, line 568
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 569
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 570
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 571
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 572
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 573
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 574
            } // library marker kkossev.commonLib, line 575
            break // library marker kkossev.commonLib, line 576
        case 0xFFDF: // library marker kkossev.commonLib, line 577
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 578
            break // library marker kkossev.commonLib, line 579
        case 0xFFE2: // library marker kkossev.commonLib, line 580
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 581
            break // library marker kkossev.commonLib, line 582
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 583
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 584
            break // library marker kkossev.commonLib, line 585
        case 0xFFFE: // library marker kkossev.commonLib, line 586
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 587
            break // library marker kkossev.commonLib, line 588
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 589
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 590
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 591
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 592
            break // library marker kkossev.commonLib, line 593
        default: // library marker kkossev.commonLib, line 594
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 595
            break // library marker kkossev.commonLib, line 596
    } // library marker kkossev.commonLib, line 597
} // library marker kkossev.commonLib, line 598

private void standardParsePollControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 600
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 601
        case 0x0000: logDebug "PollControl cluster: CheckInInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 602
        case 0x0001: logDebug "PollControl cluster: LongPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 603
        case 0x0002: logDebug "PollControl cluster: ShortPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 604
        case 0x0003: logDebug "PollControl cluster: FastPollTimeout = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 605
        case 0x0004: logDebug "PollControl cluster: CheckInIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 606
        case 0x0005: logDebug "PollControl cluster: LongPollIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 607
        case 0x0006: logDebug "PollControl cluster: FastPollTimeoutMax = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 608
        default: logWarn "zigbee received unknown PollControl cluster attribute 0x${descMap.attrId} (value ${descMap.value})" ; break // library marker kkossev.commonLib, line 609
    } // library marker kkossev.commonLib, line 610
} // library marker kkossev.commonLib, line 611

public void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 613
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 614
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 615

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 617
    Map descMap = [:] // library marker kkossev.commonLib, line 618
    try { // library marker kkossev.commonLib, line 619
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 620
    } // library marker kkossev.commonLib, line 621
    catch (e1) { // library marker kkossev.commonLib, line 622
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 623
        // try alternative custom parsing // library marker kkossev.commonLib, line 624
        descMap = [:] // library marker kkossev.commonLib, line 625
        try { // library marker kkossev.commonLib, line 626
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 627
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 628
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 629
            } // library marker kkossev.commonLib, line 630
        } // library marker kkossev.commonLib, line 631
        catch (e2) { // library marker kkossev.commonLib, line 632
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 633
            return [:] // library marker kkossev.commonLib, line 634
        } // library marker kkossev.commonLib, line 635
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 636
    } // library marker kkossev.commonLib, line 637
    return descMap // library marker kkossev.commonLib, line 638
} // library marker kkossev.commonLib, line 639

// return true if the messages is processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 641
// return false if the cluster is not a Tuya cluster // library marker kkossev.commonLib, line 642
private boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 643
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 644
        return false // library marker kkossev.commonLib, line 645
    } // library marker kkossev.commonLib, line 646
    // try to parse ... // library marker kkossev.commonLib, line 647
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 648
    Map descMap = [:] // library marker kkossev.commonLib, line 649
    try { // library marker kkossev.commonLib, line 650
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 651
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 652
    } // library marker kkossev.commonLib, line 653
    catch (e) { // library marker kkossev.commonLib, line 654
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 655
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 656
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 657
        return true // library marker kkossev.commonLib, line 658
    } // library marker kkossev.commonLib, line 659

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 661
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 662
    } // library marker kkossev.commonLib, line 663
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 664
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 665
    } // library marker kkossev.commonLib, line 666
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 667
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 668
    } // library marker kkossev.commonLib, line 669
    else { // library marker kkossev.commonLib, line 670
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 671
        return false // library marker kkossev.commonLib, line 672
    } // library marker kkossev.commonLib, line 673
    return true    // processed // library marker kkossev.commonLib, line 674
} // library marker kkossev.commonLib, line 675

// return true if processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 677
private boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 678
  /* // library marker kkossev.commonLib, line 679
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 680
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 681
        return true // library marker kkossev.commonLib, line 682
    } // library marker kkossev.commonLib, line 683
*/ // library marker kkossev.commonLib, line 684
    Map descMap = [:] // library marker kkossev.commonLib, line 685
    try { // library marker kkossev.commonLib, line 686
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 687
    } // library marker kkossev.commonLib, line 688
    catch (e1) { // library marker kkossev.commonLib, line 689
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 690
        // try alternative custom parsing // library marker kkossev.commonLib, line 691
        descMap = [:] // library marker kkossev.commonLib, line 692
        try { // library marker kkossev.commonLib, line 693
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 694
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 695
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 696
            } // library marker kkossev.commonLib, line 697
        } // library marker kkossev.commonLib, line 698
        catch (e2) { // library marker kkossev.commonLib, line 699
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 700
            return true // library marker kkossev.commonLib, line 701
        } // library marker kkossev.commonLib, line 702
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 703
    } // library marker kkossev.commonLib, line 704
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 705
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 706
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 707
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 708
        return false // library marker kkossev.commonLib, line 709
    } // library marker kkossev.commonLib, line 710
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 711
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 712
    // attribute report received // library marker kkossev.commonLib, line 713
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 714
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 715
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 716
    } // library marker kkossev.commonLib, line 717
    attrData.each { // library marker kkossev.commonLib, line 718
        if (it.status == '86') { // library marker kkossev.commonLib, line 719
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 720
        // TODO - skip parsing? // library marker kkossev.commonLib, line 721
        } // library marker kkossev.commonLib, line 722
        switch (it.cluster) { // library marker kkossev.commonLib, line 723
            case '0000' : // library marker kkossev.commonLib, line 724
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 725
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 726
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 727
                } // library marker kkossev.commonLib, line 728
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 729
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 730
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 731
                } // library marker kkossev.commonLib, line 732
                else { // library marker kkossev.commonLib, line 733
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 734
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 735
                } // library marker kkossev.commonLib, line 736
                break // library marker kkossev.commonLib, line 737
            default : // library marker kkossev.commonLib, line 738
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 739
                break // library marker kkossev.commonLib, line 740
        } // switch // library marker kkossev.commonLib, line 741
    } // for each attribute // library marker kkossev.commonLib, line 742
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 743
} // library marker kkossev.commonLib, line 744

public String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 746
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 747
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 748
} // library marker kkossev.commonLib, line 749

public String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 751
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 752
} // library marker kkossev.commonLib, line 753

/* // library marker kkossev.commonLib, line 755
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 756
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 757
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 758
*/ // library marker kkossev.commonLib, line 759
private static int getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 760
private static int getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 761
private static int getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 762

// Tuya Commands // library marker kkossev.commonLib, line 764
private static int getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 765
private static int getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 766
private static int getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 767
private static int getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 768
private static int getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 769

// tuya DP type // library marker kkossev.commonLib, line 771
private static String getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 772
private static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 773
private static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 774
private static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 775
private static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 776
private static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 777

private void syncTuyaDateTime() { // library marker kkossev.commonLib, line 779
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 780
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 781
    long offset = 0 // library marker kkossev.commonLib, line 782
    int offsetHours = 0 // library marker kkossev.commonLib, line 783
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 784
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 785
    try { // library marker kkossev.commonLib, line 786
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 787
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 788
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 789
    } catch (e) { // library marker kkossev.commonLib, line 790
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 791
    } // library marker kkossev.commonLib, line 792
    // // library marker kkossev.commonLib, line 793
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 794
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 795
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 796
} // library marker kkossev.commonLib, line 797

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 799
public void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 800
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 801
        syncTuyaDateTime() // library marker kkossev.commonLib, line 802
    } // library marker kkossev.commonLib, line 803
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 804
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 805
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 806
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 807
        if (status != '00') { // library marker kkossev.commonLib, line 808
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 809
        } // library marker kkossev.commonLib, line 810
    } // library marker kkossev.commonLib, line 811
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 812
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 813
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 814
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 815
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 816
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 817
            return // library marker kkossev.commonLib, line 818
        } // library marker kkossev.commonLib, line 819
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 820
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 821
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 822
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 823
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 824
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 825
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 826
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 827
            } // library marker kkossev.commonLib, line 828
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 829
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 830
        } // library marker kkossev.commonLib, line 831
    } // library marker kkossev.commonLib, line 832
    else { // library marker kkossev.commonLib, line 833
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 834
    } // library marker kkossev.commonLib, line 835
} // library marker kkossev.commonLib, line 836

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 838
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 839
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 840
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 841
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 842
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 843
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 844
        } // library marker kkossev.commonLib, line 845
    } // library marker kkossev.commonLib, line 846
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 847
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 848
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 849
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 850
        } // library marker kkossev.commonLib, line 851
    } // library marker kkossev.commonLib, line 852
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 853
} // library marker kkossev.commonLib, line 854

public int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 856
    int retValue = 0 // library marker kkossev.commonLib, line 857
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 858
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 859
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 860
        int power = 1 // library marker kkossev.commonLib, line 861
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 862
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 863
            power = power * 256 // library marker kkossev.commonLib, line 864
        } // library marker kkossev.commonLib, line 865
    } // library marker kkossev.commonLib, line 866
    return retValue // library marker kkossev.commonLib, line 867
} // library marker kkossev.commonLib, line 868

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 870

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 872
    List<String> cmds = [] // library marker kkossev.commonLib, line 873
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 874
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 875
    int tuyaCmd // library marker kkossev.commonLib, line 876
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified! // library marker kkossev.commonLib, line 877
    if (this.respondsTo('getDEVICE') && DEVICE?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 878
        tuyaCmd = DEVICE?.device?.tuyaCmd // library marker kkossev.commonLib, line 879
    } // library marker kkossev.commonLib, line 880
    else { // library marker kkossev.commonLib, line 881
        tuyaCmd = tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 882
    } // library marker kkossev.commonLib, line 883
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 884
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 885
    return cmds // library marker kkossev.commonLib, line 886
} // library marker kkossev.commonLib, line 887

private String getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 889

public void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 891
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 892
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 893
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 894
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 895
} // library marker kkossev.commonLib, line 896


public List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 899
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 900
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 901
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 902
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 903
} // library marker kkossev.commonLib, line 904

public List<String> queryAllTuyaDP() { // library marker kkossev.commonLib, line 906
    logTrace 'queryAllTuyaDP()' // library marker kkossev.commonLib, line 907
    List<String> cmds = zigbee.command(0xEF00, 0x03) // library marker kkossev.commonLib, line 908
    return cmds // library marker kkossev.commonLib, line 909
} // library marker kkossev.commonLib, line 910

public void aqaraBlackMagic() { // library marker kkossev.commonLib, line 912
    List<String> cmds = [] // library marker kkossev.commonLib, line 913
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 914
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 915
    } // library marker kkossev.commonLib, line 916
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 917
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 918
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 919
        return // library marker kkossev.commonLib, line 920
    } // library marker kkossev.commonLib, line 921
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 922
} // library marker kkossev.commonLib, line 923

// Invoked from configure() // library marker kkossev.commonLib, line 925
public List<String> initializeDevice() { // library marker kkossev.commonLib, line 926
    List<String> cmds = [] // library marker kkossev.commonLib, line 927
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 928
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 929
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 930
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 931
    } // library marker kkossev.commonLib, line 932
    else { logDebug 'no customInitializeDevice method defined' } // library marker kkossev.commonLib, line 933
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 934
    return cmds // library marker kkossev.commonLib, line 935
} // library marker kkossev.commonLib, line 936

// Invoked from configure() // library marker kkossev.commonLib, line 938
public List<String> configureDevice() { // library marker kkossev.commonLib, line 939
    List<String> cmds = [] // library marker kkossev.commonLib, line 940
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 941
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 942
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 943
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 944
    } // library marker kkossev.commonLib, line 945
    else { logDebug 'no customConfigureDevice method defined' } // library marker kkossev.commonLib, line 946
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 947
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 948
    return cmds // library marker kkossev.commonLib, line 949
} // library marker kkossev.commonLib, line 950

/* // library marker kkossev.commonLib, line 952
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 953
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 954
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 955
*/ // library marker kkossev.commonLib, line 956

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 958
    List<String> cmds = [] // library marker kkossev.commonLib, line 959
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 960
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 961
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 962
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 963
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 964
            } // library marker kkossev.commonLib, line 965
        } // library marker kkossev.commonLib, line 966
    } // library marker kkossev.commonLib, line 967
    return cmds // library marker kkossev.commonLib, line 968
} // library marker kkossev.commonLib, line 969

public void refresh() { // library marker kkossev.commonLib, line 971
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 972
    checkDriverVersion(state) // library marker kkossev.commonLib, line 973
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 974
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 975
        customCmds = customRefresh() // library marker kkossev.commonLib, line 976
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 977
    } // library marker kkossev.commonLib, line 978
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 979
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 980
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 981
    } // library marker kkossev.commonLib, line 982
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 983
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 984
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 985
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 986
    } // library marker kkossev.commonLib, line 987
    else { // library marker kkossev.commonLib, line 988
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 989
    } // library marker kkossev.commonLib, line 990
} // library marker kkossev.commonLib, line 991

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, 'clearRefreshRequest', [overwrite: true]) } // library marker kkossev.commonLib, line 993
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 994
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 995

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 997
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 998
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 999
        sendEvent(name: '_status_', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 1000
    } // library marker kkossev.commonLib, line 1001
    else { // library marker kkossev.commonLib, line 1002
        logInfo "${info}" // library marker kkossev.commonLib, line 1003
        sendEvent(name: '_status_', value: info, type: 'digital') // library marker kkossev.commonLib, line 1004
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1005
    } // library marker kkossev.commonLib, line 1006
} // library marker kkossev.commonLib, line 1007

public void ping() { // library marker kkossev.commonLib, line 1009
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1010
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true // library marker kkossev.commonLib, line 1011
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1012
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID // library marker kkossev.commonLib, line 1013
    if (isVirtual()) { runInMillis(10, 'virtualPong') } // library marker kkossev.commonLib, line 1014
    else if (device.getDataValue('manufacturer') == 'Aqara') { // library marker kkossev.commonLib, line 1015
        logDebug 'Aqara device ping...' // library marker kkossev.commonLib, line 1016
        sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [destEndpoint: 0x01], 0) ) // library marker kkossev.commonLib, line 1017
    } // library marker kkossev.commonLib, line 1018
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) } // library marker kkossev.commonLib, line 1019
    logDebug 'ping...' // library marker kkossev.commonLib, line 1020
} // library marker kkossev.commonLib, line 1021

private void virtualPong() { // library marker kkossev.commonLib, line 1023
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1024
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1025
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1026
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1027
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1028
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '9999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1029
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1030
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1031
        sendRttEvent() // library marker kkossev.commonLib, line 1032
    } // library marker kkossev.commonLib, line 1033
    else { // library marker kkossev.commonLib, line 1034
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1035
    } // library marker kkossev.commonLib, line 1036
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1037
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1038
} // library marker kkossev.commonLib, line 1039

public void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1041
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1042
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1043
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1044
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1045
    if (value == null) { // library marker kkossev.commonLib, line 1046
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1047
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1048
    } // library marker kkossev.commonLib, line 1049
    else { // library marker kkossev.commonLib, line 1050
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1051
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1052
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1053
    } // library marker kkossev.commonLib, line 1054
} // library marker kkossev.commonLib, line 1055

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1057
    if (cluster != null) { // library marker kkossev.commonLib, line 1058
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1059
    } // library marker kkossev.commonLib, line 1060
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1061
    return 'NULL' // library marker kkossev.commonLib, line 1062
} // library marker kkossev.commonLib, line 1063

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1065
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1066
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1067
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1068
} // library marker kkossev.commonLib, line 1069

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1071
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1072
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1073
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1074
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1075
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1076
    } // library marker kkossev.commonLib, line 1077
} // library marker kkossev.commonLib, line 1078

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1080
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1081
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1082
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1083
    if (state.health?.isHealthCheck == true) { // library marker kkossev.commonLib, line 1084
        logWarn 'device health check failed!' // library marker kkossev.commonLib, line 1085
        state.health?.checkCtr3 = (state.health?.checkCtr3 ?: 0 ) + 1 // library marker kkossev.commonLib, line 1086
        if (state.health?.checkCtr3 >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1087
            if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1088
                sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1089
            } // library marker kkossev.commonLib, line 1090
        } // library marker kkossev.commonLib, line 1091
        state.health['isHealthCheck'] = false // library marker kkossev.commonLib, line 1092
    } // library marker kkossev.commonLib, line 1093
} // library marker kkossev.commonLib, line 1094

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1096
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1097
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1098
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1099
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1100
    } // library marker kkossev.commonLib, line 1101
    else { // library marker kkossev.commonLib, line 1102
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1103
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1104
    } // library marker kkossev.commonLib, line 1105
} // library marker kkossev.commonLib, line 1106

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1108
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1109
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1110
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1111
} // library marker kkossev.commonLib, line 1112

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1114
private void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1115
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1116
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1117
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1118
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1119
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1120
    } // library marker kkossev.commonLib, line 1121
} // library marker kkossev.commonLib, line 1122

private void deviceHealthCheck() { // library marker kkossev.commonLib, line 1124
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1125
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1126
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1127
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1128
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1129
            logWarn 'not present!' // library marker kkossev.commonLib, line 1130
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1131
        } // library marker kkossev.commonLib, line 1132
    } // library marker kkossev.commonLib, line 1133
    else { // library marker kkossev.commonLib, line 1134
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})" // library marker kkossev.commonLib, line 1135
    } // library marker kkossev.commonLib, line 1136
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1137
    // added 03/06/2025 // library marker kkossev.commonLib, line 1138
    if (settings?.healthCheckMethod as int == 2) { // library marker kkossev.commonLib, line 1139
        state.health['isHealthCheck'] = true // library marker kkossev.commonLib, line 1140
        ping()  // proactively ping the device... // library marker kkossev.commonLib, line 1141
    } // library marker kkossev.commonLib, line 1142
} // library marker kkossev.commonLib, line 1143

private void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1145
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1146
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1147
    if (value == 'online') { // library marker kkossev.commonLib, line 1148
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1149
    } // library marker kkossev.commonLib, line 1150
    else { // library marker kkossev.commonLib, line 1151
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1152
    } // library marker kkossev.commonLib, line 1153
} // library marker kkossev.commonLib, line 1154

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1156
void updated() { // library marker kkossev.commonLib, line 1157
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1158
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1159
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1160
    unschedule() // library marker kkossev.commonLib, line 1161

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1163
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1164
        runIn(86400, 'logsOff') // library marker kkossev.commonLib, line 1165
    } // library marker kkossev.commonLib, line 1166
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1167
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1168
        runIn(1800, 'traceOff') // library marker kkossev.commonLib, line 1169
    } // library marker kkossev.commonLib, line 1170

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1172
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1173
        // schedule the periodic timer // library marker kkossev.commonLib, line 1174
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1175
        if (interval > 0) { // library marker kkossev.commonLib, line 1176
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1177
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1178
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1179
        } // library marker kkossev.commonLib, line 1180
    } // library marker kkossev.commonLib, line 1181
    else { // library marker kkossev.commonLib, line 1182
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1183
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1184
    } // library marker kkossev.commonLib, line 1185
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1186
        customUpdated() // library marker kkossev.commonLib, line 1187
    } // library marker kkossev.commonLib, line 1188

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1190
} // library marker kkossev.commonLib, line 1191

private void logsOff() { // library marker kkossev.commonLib, line 1193
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1194
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1195
} // library marker kkossev.commonLib, line 1196
private void traceOff() { // library marker kkossev.commonLib, line 1197
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1198
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1199
} // library marker kkossev.commonLib, line 1200

public void configure(String command) { // library marker kkossev.commonLib, line 1202
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1203
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1204
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1205
        return // library marker kkossev.commonLib, line 1206
    } // library marker kkossev.commonLib, line 1207
    // // library marker kkossev.commonLib, line 1208
    String func // library marker kkossev.commonLib, line 1209
    try { // library marker kkossev.commonLib, line 1210
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1211
        "$func"() // library marker kkossev.commonLib, line 1212
    } // library marker kkossev.commonLib, line 1213
    catch (e) { // library marker kkossev.commonLib, line 1214
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1215
        return // library marker kkossev.commonLib, line 1216
    } // library marker kkossev.commonLib, line 1217
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1218
} // library marker kkossev.commonLib, line 1219

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1221
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1222
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1223
} // library marker kkossev.commonLib, line 1224

public void loadAllDefaults() { // library marker kkossev.commonLib, line 1226
    logDebug 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1227
    deleteAllSettings() // library marker kkossev.commonLib, line 1228
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1229
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1230
    deleteAllStates() // library marker kkossev.commonLib, line 1231
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1232

    initialize() // library marker kkossev.commonLib, line 1234
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1235
    updated() // library marker kkossev.commonLib, line 1236
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1237
} // library marker kkossev.commonLib, line 1238

private void configureNow() { // library marker kkossev.commonLib, line 1240
    configure() // library marker kkossev.commonLib, line 1241
} // library marker kkossev.commonLib, line 1242

/** // library marker kkossev.commonLib, line 1244
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1245
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1246
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1247
 */ // library marker kkossev.commonLib, line 1248
void configure() { // library marker kkossev.commonLib, line 1249
    List<String> cmds = [] // library marker kkossev.commonLib, line 1250
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1251
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1252
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1253
    if (isTuya()) { // library marker kkossev.commonLib, line 1254
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1255
    } // library marker kkossev.commonLib, line 1256
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1257
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1258
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1259
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1260
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1261
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1262
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1263
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1264
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1265
    } // library marker kkossev.commonLib, line 1266
    else { // library marker kkossev.commonLib, line 1267
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1268
    } // library marker kkossev.commonLib, line 1269
} // library marker kkossev.commonLib, line 1270

 // Invoked when the device is installed with this driver automatically selected. // library marker kkossev.commonLib, line 1272
void installed() { // library marker kkossev.commonLib, line 1273
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1274
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1275
    // populate some default values for attributes // library marker kkossev.commonLib, line 1276
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1277
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1278
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1279
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1280
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1281
} // library marker kkossev.commonLib, line 1282

private void queryPowerSource() { // library marker kkossev.commonLib, line 1284
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1285
} // library marker kkossev.commonLib, line 1286

 // Invoked from 'LoadAllDefaults' // library marker kkossev.commonLib, line 1288
private void initialize() { // library marker kkossev.commonLib, line 1289
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1290
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1291
    if (device.getDataValue('powerSource') == null) { // library marker kkossev.commonLib, line 1292
        logInfo "initializing device powerSource 'unknown'" // library marker kkossev.commonLib, line 1293
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1294
    } // library marker kkossev.commonLib, line 1295
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1296
    updateTuyaVersion() // library marker kkossev.commonLib, line 1297
    updateAqaraVersion() // library marker kkossev.commonLib, line 1298
} // library marker kkossev.commonLib, line 1299

/* // library marker kkossev.commonLib, line 1301
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1302
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1303
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1304
*/ // library marker kkossev.commonLib, line 1305

static Integer safeToInt(Object val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1307
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1308
} // library marker kkossev.commonLib, line 1309

static Double safeToDouble(Object val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1311
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1312
} // library marker kkossev.commonLib, line 1313

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1315
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1316
} // library marker kkossev.commonLib, line 1317

public void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1319
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1320
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1321
        return // library marker kkossev.commonLib, line 1322
    } // library marker kkossev.commonLib, line 1323
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1324
    cmd.each { // library marker kkossev.commonLib, line 1325
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1326
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1327
            return // library marker kkossev.commonLib, line 1328
        } // library marker kkossev.commonLib, line 1329
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1330
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1331
    } // library marker kkossev.commonLib, line 1332
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1333
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1334
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1335
} // library marker kkossev.commonLib, line 1336

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1338

private String getDeviceInfo() { // library marker kkossev.commonLib, line 1340
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1341
} // library marker kkossev.commonLib, line 1342

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1344
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1345
} // library marker kkossev.commonLib, line 1346

//@CompileStatic // library marker kkossev.commonLib, line 1348
public void checkDriverVersion(final Map stateCopy) { // library marker kkossev.commonLib, line 1349
    if (stateCopy.driverVersion == null || driverVersionAndTimeStamp() != stateCopy.driverVersion) { // library marker kkossev.commonLib, line 1350
        logDebug "checkDriverVersion: updating the settings from the current driver version ${stateCopy.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1351
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1352
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1353
        initializeVars(false) // library marker kkossev.commonLib, line 1354
        updateTuyaVersion() // library marker kkossev.commonLib, line 1355
        updateAqaraVersion() // library marker kkossev.commonLib, line 1356
    } // library marker kkossev.commonLib, line 1357
    if (state.states == null) { state.states = [:] } ; if (state.lastRx == null) { state.lastRx = [:] } ; if (state.lastTx == null) { state.lastTx = [:] } ; if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1358
} // library marker kkossev.commonLib, line 1359

// credits @thebearmay // library marker kkossev.commonLib, line 1361
String getModel() { // library marker kkossev.commonLib, line 1362
    try { // library marker kkossev.commonLib, line 1363
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1364
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1365
    } catch (ignore) { // library marker kkossev.commonLib, line 1366
        try { // library marker kkossev.commonLib, line 1367
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1368
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1369
                return model // library marker kkossev.commonLib, line 1370
            } // library marker kkossev.commonLib, line 1371
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1372
            return '' // library marker kkossev.commonLib, line 1373
        } // library marker kkossev.commonLib, line 1374
    } // library marker kkossev.commonLib, line 1375
} // library marker kkossev.commonLib, line 1376

// credits @thebearmay // library marker kkossev.commonLib, line 1378
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1379
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1380
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1381
    String revision = tokens.last() // library marker kkossev.commonLib, line 1382
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1383
} // library marker kkossev.commonLib, line 1384

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1386
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1387
    unschedule() // library marker kkossev.commonLib, line 1388
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1389
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1390

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1392
} // library marker kkossev.commonLib, line 1393

void resetStatistics() { // library marker kkossev.commonLib, line 1395
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1396
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1397
} // library marker kkossev.commonLib, line 1398

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1400
void resetStats() { // library marker kkossev.commonLib, line 1401
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1402
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1403
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1404
    state.stats.rxCtr = 0 ; state.stats.txCtr = 0 // library marker kkossev.commonLib, line 1405
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1406
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1407
} // library marker kkossev.commonLib, line 1408

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1410
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1411
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1412
        state.clear() // library marker kkossev.commonLib, line 1413
        unschedule() // library marker kkossev.commonLib, line 1414
        resetStats() // library marker kkossev.commonLib, line 1415
        if (deviceProfilesV3 != null && this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1416
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1417
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1418
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1419
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1420
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1421
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1422
    } // library marker kkossev.commonLib, line 1423

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1425
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1426
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1427
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1428
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1429

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1431
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1432
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1433
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1434
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1435
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1436
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1437

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1439

    // common libraries initialization // library marker kkossev.commonLib, line 1441
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024 // library marker kkossev.commonLib, line 1442
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024 // library marker kkossev.commonLib, line 1443
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1444
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1445
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1446
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1447
    // // library marker kkossev.commonLib, line 1448
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers! // library marker kkossev.commonLib, line 1449
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024 // library marker kkossev.commonLib, line 1450
    // // library marker kkossev.commonLib, line 1451
    // custom device driver specific initialization should be at the end // library marker kkossev.commonLib, line 1452
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1453
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1454
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1455

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1457
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1458
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1459
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1460
    if ( ep  != null) { // library marker kkossev.commonLib, line 1461
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1462
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1463
    } // library marker kkossev.commonLib, line 1464
    else { // library marker kkossev.commonLib, line 1465
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1466
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1467
    } // library marker kkossev.commonLib, line 1468
} // library marker kkossev.commonLib, line 1469

// not used!? // library marker kkossev.commonLib, line 1471
void setDestinationEP() { // library marker kkossev.commonLib, line 1472
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1473
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1474
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1475
} // library marker kkossev.commonLib, line 1476

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1478
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1479
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1480
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1481

// _DEBUG mode only // library marker kkossev.commonLib, line 1483
void getAllProperties() { // library marker kkossev.commonLib, line 1484
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1485
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1486
} // library marker kkossev.commonLib, line 1487

// delete all Preferences // library marker kkossev.commonLib, line 1489
void deleteAllSettings() { // library marker kkossev.commonLib, line 1490
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1491
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1492
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1493
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1494
} // library marker kkossev.commonLib, line 1495

// delete all attributes // library marker kkossev.commonLib, line 1497
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1498
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1499
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1500
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1501
} // library marker kkossev.commonLib, line 1502

// delete all State Variables // library marker kkossev.commonLib, line 1504
void deleteAllStates() { // library marker kkossev.commonLib, line 1505
    String stateDeleted = '' // library marker kkossev.commonLib, line 1506
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1507
    state.clear() // library marker kkossev.commonLib, line 1508
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1509
} // library marker kkossev.commonLib, line 1510

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1512
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1513
} // library marker kkossev.commonLib, line 1514

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1516
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1517
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1518
} // library marker kkossev.commonLib, line 1519

void testParse(String par) { // library marker kkossev.commonLib, line 1521
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1522
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1523
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1524
    parse(par) // library marker kkossev.commonLib, line 1525
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1526
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1527
} // library marker kkossev.commonLib, line 1528

Object testJob() { // library marker kkossev.commonLib, line 1530
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1531
} // library marker kkossev.commonLib, line 1532

/** // library marker kkossev.commonLib, line 1534
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1535
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1536
 */ // library marker kkossev.commonLib, line 1537
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1538
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1539
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1540
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1541
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1542
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1543
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1544
    String cron // library marker kkossev.commonLib, line 1545
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1546
    else { // library marker kkossev.commonLib, line 1547
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1548
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1549
    } // library marker kkossev.commonLib, line 1550
    return cron // library marker kkossev.commonLib, line 1551
} // library marker kkossev.commonLib, line 1552

// credits @thebearmay // library marker kkossev.commonLib, line 1554
String formatUptime() { // library marker kkossev.commonLib, line 1555
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1556
} // library marker kkossev.commonLib, line 1557

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1559
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1560
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1561
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1562
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1563
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1564
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1565
} // library marker kkossev.commonLib, line 1566

boolean isTuya() { // library marker kkossev.commonLib, line 1568
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1569
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1570
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1571
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1572
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1573
} // library marker kkossev.commonLib, line 1574

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1576
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1577
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1578
    if (application != null) { // library marker kkossev.commonLib, line 1579
        Integer ver // library marker kkossev.commonLib, line 1580
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1581
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1582
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1583
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1584
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1585
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1586
        } // library marker kkossev.commonLib, line 1587
    } // library marker kkossev.commonLib, line 1588
} // library marker kkossev.commonLib, line 1589

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1591

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1593
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1594
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1595
    if (application != null) { // library marker kkossev.commonLib, line 1596
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1597
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1598
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1599
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1600
        } // library marker kkossev.commonLib, line 1601
    } // library marker kkossev.commonLib, line 1602
} // library marker kkossev.commonLib, line 1603

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1605
    try { // library marker kkossev.commonLib, line 1606
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1607
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1608
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1609
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1610
    } catch (e) { // library marker kkossev.commonLib, line 1611
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1612
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1613
    } // library marker kkossev.commonLib, line 1614
} // library marker kkossev.commonLib, line 1615

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1617
    try { // library marker kkossev.commonLib, line 1618
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1619
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1620
        return date.getTime() // library marker kkossev.commonLib, line 1621
    } catch (e) { // library marker kkossev.commonLib, line 1622
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1623
        return now() // library marker kkossev.commonLib, line 1624
    } // library marker kkossev.commonLib, line 1625
} // library marker kkossev.commonLib, line 1626

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1628
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1629
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1630
    int seconds = time % 60 // library marker kkossev.commonLib, line 1631
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1632
} // library marker kkossev.commonLib, line 1633

// ~~~~~ end include (144) kkossev.commonLib ~~~~~
