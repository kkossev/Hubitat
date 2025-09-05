/* groovylint-disable NglParseError, ImplicitReturnStatement, InsecureRandom, MethodReturnTypeRequired, MethodSize, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnusedImport, VariableName */ 
 /*
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
 * ..............................
 * ver. 4.0.0  2025-09-04 kkossev  - deviceProfileV4 BRANCH created
 *                                   
 *                                   TODO:
*/

static String version() { "4.0.0" }
static String timeStamp() {"2025/09/05 1:29 PM"}

@Field static final Boolean _DEBUG = true           // debug logging
@Field static final Boolean _TRACE_ALL = false      // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true 

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput

#include kkossev.illuminanceLib
#include kkossev.motionLib
#include kkossev.batteryLib
#include kkossev.deviceProfileLibV4
#include kkossev.commonLib

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
            command 'cacheTest', [[name: "action", type: "ENUM", description: "Cache action", constraints: ["Info", "Initialize", "ReconstructedFingerprints", "Clear"], defaultValue: "Info"]]
        }
        
        // Generate fingerprints from optimized deviceFingerprintsV4 map (fast access!)
        // Uses pre-loaded fingerprint data instead of processing deviceProfilesV3
        if (deviceFingerprintsV4 && !deviceFingerprintsV4.isEmpty()) {
            deviceFingerprintsV4.each { profileName, fingerprintData ->
                fingerprintData.fingerprints?.each { fingerprintMap ->
                    fingerprint fingerprintMap
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




/*
@Field static final Map deviceProfilesV33 = [
    'TS0601_TUYA_RADAR'   : [        // isZY_M100Radar()        // very spammy devices!
            description   : 'Tuya Human Presence mmWave Radar ZY-M100',
            device        : [powerSource: 'dc', ignoreIAS:true], // sends all DPs periodically!
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
                [dp:104, name:'illuminance',        type:'number',  rw: 'ro', min:0,   max:2000,  defVal:0,    scale:1,    unit:'lx',       title:'<b>illuminance</b>',                description:'<i>illuminance</i>',

            ],
            refresh: ['queryAllTuyaDP'],
            spammyDPsToIgnore : [9],
            spammyDPsToNotTrace : [2, 3, 4, 6, 9, 101, 102, 103, 104], // added the illuminance as a spammyDP - 05/30/10114
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
                [dp:115, name:'radarAlarmTime',                  type:'number',  rw: 'rw', min:0,    max:60 ,  defVal:1,     scale:1,   unit:'seconds',   title:'<b>Alarm time</b>',                         description:'<i>Alarm time</i>'},
                [dp:116, name:'radarAlarmVolume',                type:'enum',    rw: 'rw', min:0,    max:3,    defVal:'3',  map:[0:'0 - low', 1:'1 - medium', 2:'2 - high', 3:'3 - mute'],    title:'<b>Alarm volume</b>',          description:'<i>Alarm volume</i>'},
                [dp:117, name:'radarAlarmMode',                  type:'enum',    rw: 'rw', min:0,    max:3,    defVal:'1',  map:[0:'0 - arm', 1:'1 - off', 2:'2 - alarm', 3:'3 - doorbell'],  title:'<b>Alarm mode</b>',            description:'<i>Alarm mode</i>'},
                [dp:118, name:'radarStatus',                     type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], description:'Radar small move self-test'],
                [dp:119, name:'radarStatus',                     type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], description:'Radar breathe self-test'],
                [dp:120, name:'radarStatus',                     type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], description:'Radar move self-test']
                //[dp:116, name:'occupiedTime',                  type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar presence duration'],    // not received
                //[dp:117, name:'absenceTime',                      type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar absence duration'],     // not received
                //[dp:118, name:'radarDurationStatus',             type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar duration status']       // not received
            ],
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
                [dp:102, name:'fadingTime',                      type:'number',  rw: 'rw', min:0,    max:28800, defVal:30,    scale:1,   unit:'seconds',   title:'<b>Presence keep time</b>',                 description:'<i>Presence keep time</i>' },
                [dp:103, name:'motionFalseDetection',            type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'],     title:'<b>Motion false detection</b>',     description:'<i>Disable/enable Motion false detection</i>'],
                [dp:104, name:'smallMotionDetectionDistance',    type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:5.0,   scale:100, unit:'meters',    title:'<b>Small motion detection distance</b>',    description:'<i>Small motion detection distance</i>'},
                [dp:105, name:'smallMotionDetectionSensitivity', type:'number',  rw: 'rw', min:0,    max:10 ,  defVal:7,     scale:1,   unit:'',         title:'<b>Small motion detection sensitivity</b>', description:'<i>Small motion detection sensitivity</i>'},
                [dp:106, name:'illuminance',                     type:'number',  rw: 'ro', scale:10,  unit:'lx',        description:'Illuminance'],
                [dp:107, name:'ledIndicator',                    type:'enum',    rw: 'rw', min:0,    max:1,     defVal:'0',  map:[0:'0 - OFF', 1:'1 - ON'],               title:'<b>LED indicator</b>',              description:'<i>LED indicator mode</i>'},
                [dp:108, name:'staticDetectionDistance',         type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:4.0,   scale:100, unit:'meters',    title:'<b>Static detection distance</b>,          description:'<i>Static detection distance</i>'],
                [dp:109, name:'staticDetectionSensitivity',      type:'number',  rw: 'rw', min:0,    max:10,   defVal:7,     scale:1,   unit:'',         title:'<b>Static Detection Sensitivity</b>',       description:'<i>Static detection sensitivity</i>'],                 //  dt: "UINT8", aka Motionless Detection Sensitivity
                [dp:110, name:'smallMotionMinimumDistance',      type:'decimal', rw: 'rw', min:0.0,  max:6.0,  defVal:0.5,   scale:100, unit:'meters',    title:'<b>Small Motion Minimum Distance</b>',      description:'<i>Small Motion Minimum Distance</i>'],
                //[dp:111, name:'staticDetectionMinimumDistance',  type:"decimal", rw: "rw", min:0.0,  max:6.0,   defVal:0.5,  scale:100, unit:"meters",    title:'<b>Static detection minimum distance</b>',  description:'<i>Static detection minimum distance</i>'],
                [dp:112, name:'radarReset',                      type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'],     description:'Radar reset'],
                [dp:113, name:'breatheFalseDetection',           type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'],     title:'<b>Breathe false detection</b>',    description:'<i>Disable/enable Breathe false detection</i>'],
                [dp:114, name:'checkingTime',                    type:'decimal', rw: 'ro',                     scale:10,  unit:'seconds',   description:'Checking time'],
                [dp:115, name:'radarAlarmTime',                  type:'number',  rw: 'rw', min:0,    max:60 ,  defVal:1,     scale:1,   unit:'seconds',   title:'<b>Alarm time</b>',                         description:'<i>Alarm time</i>'},
                [dp:116, name:'radarAlarmVolume',                type:'enum',    rw: 'rw', min:0,    max:3,    defVal:'3',  map:[0:'0 - low', 1:'1 - medium', 2:'2 - high', 3:'3 - mute'],    title:'<b>Alarm volume</b>',          description:'<i>Alarm volume</i>'},
                [dp:117, name:'radarAlarmMode',                  type:'enum',    rw: 'rw', min:0,    max:3,    defVal:'1',  map:[0:'0 - arm', 1:'1 - off', 2:'2 - alarm', 3:'3 - doorbell'],  title:'<b>Alarm mode</b>',            description:'<i>Alarm mode</i>'},
                [dp:118, name:'radarStatus',                     type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], description:'Radar small move self-test'],
                [dp:119, name:'radarStatus',                     type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], description:'Radar breathe self-test'],
                [dp:120, name:'radarStatus',                     type:'enum',    rw: 'rw', min:0,    max:1,    defVal:'0',  map:[0:'0 - disabled', 1:'1 - enabled'], description:'Radar move self-test']
                //[dp:116, name:'occupiedTime',                  type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar presence duration'],    // not received
                //[dp:117, name:'absenceTime',                      type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar absence duration'],     // not received
                //[dp:118, name:'radarDurationStatus',             type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar duration status']       // not received
            ],
            refresh: ['queryAllTuyaDP'],
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
            refresh: ['queryAllTuyaDP'],
            configuration : [:]
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
            configuration : ['0x0406':'bind', '0x0FC57':'bind']
    ]
]
*/


@Field static final String testJSON = '''{
  "deviceProfiles": {
    "TS0601_TUYA_RADAR": {
      "description": "Tuya Human Presence mmWave Radar ZY-M100",
      "device": { "powerSource": "dc", "ignoreIAS": true },
      "capabilities": { "MotionSensor": true, "IlluminanceMeasurement": true, "DistanceMeasurement": true },
      "preferences": { "radarSensitivity": "2", "detectionDelay": "101", "fadingTime": "102", "minimumDistance": "3", "maximumDistance": "4" },
      "commands": { "resetStats": "" },
      "defaultFingerprint": { 
        "profileId": "0104", "endpointId": "01", "inClusters": "0000,0004,0005,EF00", "outClusters": "0019,000A", 
        "model": "TS0601", "manufacturer": "_TZE200_ztc6ggyl", "deviceJoinName": "Tuya ZigBee Breath Presence Sensor ZY-M100"
      },
      "fingerprints": [
        { "model": "TS0601", "manufacturer": "_TZE200_ztc6ggyl", "deviceJoinName": "Tuya ZigBee Breath Presence Sensor ZY-M100" },
        { "model": "TS0601", "manufacturer": "_TZE204_ztc6ggyl" },
        { "model": "TS0601", "manufacturer": "_TZE200_ikvncluo", "deviceJoinName": "Moes TuyaHuman Presence Detector Radar 2 in 1" },
        { "model": "TS0601", "manufacturer": "_TZE200_lyetpprm" },
        { "model": "TS0601", "manufacturer": "_TZE200_wukb7rhc", "deviceJoinName": "Moes Smart Human Presence Detector" },
        { "model": "TS0601", "manufacturer": "_TZE200_jva8ink8", "deviceJoinName": "AUBESS Human Presence Detector" },
        { "model": "TS0601", "manufacturer": "_TZE200_mrf6vtua" },
        { "model": "TS0601", "manufacturer": "_TZE200_ar0slwnd" },
        { "model": "TS0601", "manufacturer": "_TZE200_sfiy5tfs" },
        { "model": "TS0601", "manufacturer": "_TZE200_holel4dk" },
        { "model": "TS0601", "manufacturer": "_TZE200_xpq2rzhq" },
        { "model": "TS0601", "manufacturer": "_TZE204_qasjif9e" },
        { "model": "TS0601", "manufacturer": "_TZE204_xsm7l9xa" },
        { "model": "TS0601", "manufacturer": "_TZE204_ztqnh5cg" },
        { "model": "TS0601", "manufacturer": "_TZE204_fwondbzy", "deviceJoinName": "Moes Smart Human Presence Detector" }
      ],
      "tuyaDPs": [
        { "dp": 1, "name": "motion", "type": "enum", "rw": "ro", "min": 0, "max": 1, "defVal": "0", "scale": 1, "map": { "0": "inactive", "1": "active" }, "unit": "", "title": "<b>Presence state</b>", "description": "<i>Presence state</i>" },
        { "dp": 2, "name": "radarSensitivity", "type": "number", "rw": "rw", "min": 0, "max": 9, "defVal": 7, "scale": 1, "unit": "", "title": "<b>Radar sensitivity</b>", "description": "<i>Sensitivity of the radar</i>" },
        { "dp": 3, "name": "minimumDistance", "type": "decimal", "rw": "rw", "min": 0.0, "max": 10.0, "defVal": 0.1, "scale": 100, "unit": "meters", "title": "<b>Minimim detection distance</b>", "description": "<i>Minimim (near) detection distance</i>" },
        { "dp": 4, "name": "maximumDistance", "type": "decimal", "rw": "rw", "min": 0.0, "max": 10.0, "defVal": 6.0, "scale": 100, "unit": "meters", "title": "<b>Maximum detection distance</b>", "description": "<i>Maximum (far) detection distance</i>" },
        { "dp": 6, "name": "radarStatus", "type": "enum", "rw": "ro", "min": 0, "max": 5, "defVal": "1", "scale": 1, "map": { "0": "checking", "1": "check_success", "2": "check_failure", "3": "others", "4": "comm_fault", "5": "radar_fault" }, "unit": "TODO", "title": "<b>Radar self checking status</b>", "description": "<i>Radar self checking status</i>" },
        { "dp": 9, "name": "distance", "type": "decimal", "rw": "ro", "min": 0.0, "max": 10.0, "defVal": 0.0, "scale": 100, "unit": "meters", "title": "<b>Distance</b>", "description": "<i>detected distance</i>" },
        { "dp": 101, "name": "detectionDelay", "type": "decimal", "rw": "rw", "min": 0.0, "max": 10.0, "defVal": 0.2, "scale": 10, "unit": "seconds", "title": "<b>Detection delay</b>", "description": "<i>Presence detection delay timer</i>" },
        { "dp": 102, "name": "fadingTime", "type": "decimal", "rw": "rw", "min": 0.5, "max": 500.0, "defVal": 60.0, "scale": 10, "unit": "seconds", "title": "<b>Fading time</b>", "description": "<i>Presence inactivity delay timer</i>" },
        { "dp": 103, "name": "debugCLI", "type": "number", "rw": "ro", "min": 0, "max": 99999, "defVal": 0, "scale": 1, "unit": "?", "title": "<b>debugCLI</b>", "description": "<i>debug CLI</i>" },
        { "dp": 104, "name": "illuminance", "type": "number", "rw": "ro", "min": 0, "max": 2000, "defVal": 0, "scale": 1, "unit": "lx", "title": "<b>illuminance</b>", "description": "<i>illuminance</i>" }
      ],
      "refresh": ["queryAllTuyaDP"],
      "spammyDPsToIgnore": [9],
      "spammyDPsToNotTrace": [2, 3, 4, 6, 9, 101, 102, 103, 104]
    },
    "TS0601_BLACK_SQUARE_RADAR": {
      "description": "24GHz Black Square Human Presence Radar w/ LED",
      "device": { "powerSource": "dc" },
      "capabilities": { "MotionSensor": true, "IlluminanceMeasurement": true, "DistanceMeasurement": true },
      "preferences": { "radarSensitivity": "102", "fadingTime": "104" },
      "defaultFingerprint": { 
        "profileId": "0104", "endpointId": "01", "inClusters": "0004,0005,EF00,0000", "outClusters": "0019,000A", 
        "model": "TS0601", "manufacturer": "_TZE200_0u3bj3rc", "deviceJoinName": "24GHz Black Square Human Presence Radar w/ LED"
      },
      "fingerprints": [
        { "model": "TS0601", "manufacturer": "_TZE200_0u3bj3rc" },
        { "model": "TS0601", "manufacturer": "_TZE200_v6ossqfy" },
        { "model": "TS0601", "manufacturer": "_TZE200_mx6u6l4y" }
      ],
      "tuyaDPs": [
        { "dp": 1, "name": "motion", "type": "enum", "rw": "ro", "min": 0, "max": 1, "defVal": "0", "map": { "0": "inactive", "1": "active" }, "title": "<b>Presence state</b>", "description": "<i>Presence state</i>" },
        { "dp": 2, "name": "distance", "type": "decimal", "rw": "ro", "min": 0.0, "max": 10.0, "defVal": 0.0, "scale": 100, "unit": "meters", "title": "<b>Distance</b>", "description": "<i>Detected distance</i>" },
        { "dp": 4, "name": "illuminance", "type": "number", "rw": "ro", "min": 0, "max": 2000, "defVal": 0, "scale": 1, "unit": "lx", "title": "<b>Illuminance</b>", "description": "<i>Illuminance</i>" },
        { "dp": 102, "name": "radarSensitivity", "type": "number", "rw": "rw", "min": 1, "max": 5, "defVal": 3, "scale": 1, "unit": "", "title": "<b>Radar sensitivity</b>", "description": "<i>Sensitivity of the radar</i>" },
        { "dp": 104, "name": "fadingTime", "type": "number", "rw": "rw", "min": 5, "max": 1500, "defVal": 60, "scale": 1, "unit": "seconds", "title": "<b>Fading time</b>", "description": "<i>Presence inactivity timer, seconds</i>" },
        { "dp": 103, "name": "detectionDelay", "type": "decimal", "rw": "rw", "min": 0.0, "max": 10.0, "defVal": 1.0, "scale": 10, "unit": "seconds", "title": "<b>Detection delay</b>", "description": "<i>Detection delay</i>" },
        { "dp": 104, "name": "radar_scene", "type": "enum", "rw": "rw", "min": 0, "max": 4, "defVal": "0", "map": { "0": "default", "1": "bathroom", "2": "bedroom", "3": "sleeping" }, "description": "Presets for sensitivity for presence and movement" },
        { "dp": 105, "name": "distance", "type": "decimal", "rw": "ro", "min": 0.0, "max": 10.0, "scale": 100, "unit": "meters", "description": "Distance" }
      ],
      "refresh": ["queryAllTuyaDP"],
      "spammyDPsToIgnore": [105],
      "spammyDPsToNotTrace": [105]
    },
    "TS0601_24GHZ_PIR_RADAR": {
      "description": "Tuya TS0601_2AAELWXK 24 GHz + PIR Radar",
      "device": { "powerSource": "battery", "ignoreIAS": true },
      "capabilities": { "MotionSensor": true, "IlluminanceMeasurement": true, "HumanMotionState": true, "Battery": true },
      "preferences": { "radarSensitivity": "123", "staticDetectionSensitivity": "2", "staticDetectionDistance": "4", "fadingTime": "102", "ledIndicator": "107", "motionDetectionMode": "122" },
      "commands": { "resetSettings": "", "resetStats": "" },
      "fingerprints": [
        { "profileId": "0104", "endpointId": "01", "inClusters": "0000,0003,0500,0001,0400", "outClusters": "0019,000A", "model": "TS0601", "manufacturer": "_TZE200_2aaelwxk", "deviceJoinName": "Tuya 2AAELWXK 24 GHz + PIR Radar" },
        { "profileId": "0104", "endpointId": "01", "inClusters": "0000,0003,0500,0001,0400", "outClusters": "0019,000A", "model": "TS0601", "manufacturer": "_TZE200_kb5noeto", "deviceJoinName": "Tuya KB5NOETO 24 GHz + PIR Radar" }
      ],
      "tuyaDPs": [
        { "dp": 1, "name": "motion", "type": "enum", "rw": "ro", "min": 0, "max": 1, "defVal": "0", "scale": 1, "map": { "0": "inactive", "1": "active" }, "unit": "", "title": "<b>Presence state</b>", "description": "<i>Presence state</i>" },
        { "dp": 2, "name": "staticDetectionSensitivity", "type": "number", "rw": "rw", "min": 0, "max": 10, "defVal": 7, "scale": 1, "unit": "", "title": "<b>Static Detection Sensitivity</b>", "description": "<i>Static detection sensitivity</i>" },
        { "dp": 4, "name": "staticDetectionDistance", "type": "decimal", "rw": "rw", "min": 0.0, "max": 10.0, "defVal": 5.0, "scale": 100, "unit": "meters", "title": "<b>Static detection distance</b>", "description": "<i>Static detection distance</i>" },
        { "dp": 101, "name": "humanMotionState", "type": "enum", "rw": "ro", "min": 0, "max": 3, "defVal": "0", "map": { "0": "none", "1": "moving", "2": "small", "3": "static" }, "description": "Human motion state" },
        { "dp": 102, "name": "fadingTime", "type": "number", "rw": "rw", "min": 0, "max": 28800, "defVal": 30, "scale": 1, "unit": "seconds", "title": "<b>Presence keep time</b>", "description": "<i>Presence keep time</i>" },
        { "dp": 103, "name": "motionFalseDetection", "type": "enum", "rw": "rw", "min": 0, "max": 1, "defVal": "0", "map": { "0": "0 - disabled", "1": "1 - enabled" }, "title": "<b>Motion false detection</b>", "description": "<i>Disable/enable Motion false detection</i>" },
        { "dp": 104, "name": "smallMotionDetectionDistance", "type": "decimal", "rw": "rw", "min": 0.0, "max": 6.0, "defVal": 5.0, "scale": 100, "unit": "meters", "title": "<b>Small motion detection distance</b>", "description": "<i>Small motion detection distance</i>" },
        { "dp": 105, "name": "smallMotionDetectionSensitivity", "type": "number", "rw": "rw", "min": 0, "max": 10, "defVal": 7, "scale": 1, "unit": "", "title": "<b>Small motion detection sensitivity</b>", "description": "<i>Small motion detection sensitivity</i>" },
        { "dp": 106, "name": "illuminance", "type": "number", "rw": "ro", "scale": 10, "unit": "lx", "description": "Illuminance" },
        { "dp": 107, "name": "ledIndicator", "type": "enum", "rw": "rw", "min": 0, "max": 1, "defVal": "0", "map": { "0": "0 - OFF", "1": "1 - ON" }, "title": "<b>LED indicator</b>", "description": "<i>LED indicator mode</i>" },
        { "dp": 121, "name": "battery", "type": "number", "rw": "ro", "min": 0, "max": 100, "defVal": 100, "scale": 1, "unit": "%", "title": "<b>Battery level</b>", "description": "<i>Battery level</i>" },
        { "dp": 122, "name": "motionDetectionMode", "type": "enum", "rw": "rw", "min": 0, "max": 2, "defVal": "1", "map": { "0": "0 - onlyPIR", "1": "1 - PIRandRadar", "2": "2 - onlyRadar" }, "title": "<b>Motion detection mode</b>", "description": "<i>Motion detection mode</i>" },
        { "dp": 123, "name": "radarSensitivity", "type": "number", "rw": "rw", "min": 1, "max": 9, "defVal": 5, "scale": 1, "unit": "", "title": "<b>Motion Detection sensitivity</b>", "description": "<i>Motion detection sensitivity</i>" }
      ],
      "refresh": ["queryAllTuyaDP"]
    },
    "TS0225_LINPTECH_RADAR": {
      "description": "Tuya TS0225_LINPTECH 24GHz Radar",
      "device": { "powerSource": "dc" },
      "capabilities": { "MotionSensor": true, "IlluminanceMeasurement": true, "DistanceMeasurement": true },
      "preferences": { "fadingTime": "101", "motionDetectionDistance": "0xE002:0xE00B", "motionDetectionSensitivity": "0xE002:0xE004", "staticDetectionSensitivity": "0xE002:0xE005", "ledIndicator": "0xE002:0xE009" },
      "commands": { "resetStats": "", "refresh": "", "initialize": "", "updateAllPreferences": "", "resetPreferencesToDefaults": "", "validateAndFixPreferences": "" },
      "fingerprints": [
        { "profileId": "0104", "endpointId": "01", "inClusters": "0000,0003,0004,0005,E002,4000,EF00,0500", "outClusters": "0019,000A", "model": "TS0225", "manufacturer": "_TZ3218_awarhusb", "deviceJoinName": "Tuya TS0225_LINPTECH 24Ghz Human Presence Detector" },
        { "profileId": "0104", "endpointId": "01", "inClusters": "0000,0003,0004,0005,E002,4000,EF00,0500", "outClusters": "0019,000A", "model": "TS0225", "manufacturer": "_TZ3218_t9ynfz4x", "deviceJoinName": "Tuya TS0225_LINPTECH 24Ghz Human Presence Detector" }
      ],
      "tuyaDPs": [
        { "dp": 101, "name": "fadingTime", "type": "number", "rw": "rw", "min": 1, "max": 9999, "defVal": 10, "scale": 1, "unit": "seconds", "title": "<b>Fading time</b>", "description": "<i>Presence inactivity timer, seconds</i>" }
      ],
      "attributes": [
        { "at": "0xE002:0xE001", "name": "occupiedTime", "type": "number", "dt": "0x21", "rw": "ro", "min": 0, "max": 65535, "scale": 1, "unit": "minutes", "title": "<b>Existence time</b>", "description": "<i>Existence (presence) time, recommended value is > 10 seconds!</i>" },
        { "at": "0xE002:0xE004", "name": "motionDetectionSensitivity", "type": "enum", "dt": "0x20", "rw": "rw", "min": 1, "max": 5, "defVal": "4", "scale": 1, "map": { "1": "1 - low", "2": "2 - medium low", "3": "3 - medium", "4": "4 - medium high", "5": "5 - high" }, "unit": "", "title": "<b>Motion Detection Sensitivity</b>", "description": "<i>Large motion detection sensitivity</i>" },
        { "at": "0xE002:0xE005", "name": "staticDetectionSensitivity", "type": "enum", "dt": "0x20", "rw": "rw", "min": 1, "max": 5, "defVal": "3", "scale": 1, "map": { "1": "1 - low", "2": "2 - medium low", "3": "3 - medium", "4": "4 - medium high", "5": "5 - high" }, "unit": "", "title": "<b>Static Detection Sensitivity</b>", "description": "<i>Static detection sensitivity</i>" },
        { "at": "0xE002:0xE009", "name": "ledIndicator", "type": "enum", "dt": "0x10", "rw": "rw", "min": 0, "max": 1, "defVal": "0", "map": { "0": "0 - OFF", "1": "1 - ON" }, "title": "<b>LED indicator mode</b>", "description": "<i>LED indicator mode<br>Requires firmware version 1.0.6 (application:46)!</i>" },
        { "at": "0xE002:0xE00A", "name": "distance", "type": "decimal", "dt": "0x21", "rw": "ro", "min": 0.0, "max": 6.0, "defVal:": 0.0, "scale": 100, "unit": "meters", "title": "<b>Distance</b>", "description": "<i>Measured distance</i>" },
        { "at": "0xE002:0xE00B", "name": "motionDetectionDistance", "type": "enum", "dt": "0x21", "rw": "rw", "min": 0.75, "max": 6.00, "defVal": "450", "step": 75, "scale": 100, "map": { "75": "0.75 meters", "150": "1.50 meters", "225": "2.25 meters", "300": "3.00 meters", "375": "3.75 meters", "450": "4.50 meters", "525": "5.25 meters", "600": "6.00 meters" }, "unit": "meters", "title": "<b>Motion Detection Distance</b>", "description": "<i>Large motion detection distance, meters</i>" }
      ],
      "refresh": ["queryAllTuyaDP"],
      "configuration": {},
      "comments": ["https://github.com/Koenkk/zigbee2mqtt/issues/18637"]
    }
}
''' 

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
        logWarn "customInitEvents: ${device.displayName} is a known spammy device!"
        logInfo 'This device bombards the hub every 4 seconds!'
    }
    if (!state.deviceProfile || state.deviceProfile == UNKNOWN) {
        String unknown = "<b>UNKNOWN</b> mmWave model/manufacturer ${device.getDataValue('model')}/${device.getDataValue('manufacturer')}"
        sendEvent(name: 'WARNING', value: unknown, descriptionText: 'Device profile is not set')
        logInfo unknown
        logWarn unknown
    }
}

void updateIndicatorLight() {
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


void test(String par) {
    long startTime = now()
    logWarn "test() started at ${startTime}"

    def xx = getDeviceProfilesMap()
    logDebug "test() getDeviceProfilesMap() returned ${xx?.size() ?: 0} profiles"
    /*

    boolean loaded = ensureProfilesLoaded()
    if (!loaded) {
        logWarn "test(): profiles not loaded, aborting test()"
        return
    }
    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes
    logDebug "test() attribMap: ${attribMap}"
    */

    /*
    //parse('catchall: 0104 EF00 01 01 0040 00 E03B 01 00 0000 02 01 00556701000100')
    def parpar = 'catchall: 0104 EF00 01 01 0040 00 E03B 01 00 0000 02 01 00556701000100'
    catchall: 0104 EF00 01 01 0040 00 E03B 01 00 0000 02 01 00EB0104000100

    for (int i=0; i<100; i++) { 
        testFunc(parpar) 
    }
*/
    long endTime = now()
    logWarn "test() ended at ${endTime} (duration ${endTime - startTime}ms)"
}


// cacheTest command - manage and inspect cached data structures (currently deviceProfilesV3)
void cacheTest(String action) {
    String act = (action ?: 'Info').trim()
    switch(act) {
        case 'Info':
            int size = deviceProfilesV3?.size() ?: 0
            int fingerprintSize = deviceFingerprintsV4?.size() ?: 0
            List keys = deviceProfilesV3 ? new ArrayList(deviceProfilesV3.keySet()) : []
            List fingerprintKeys = deviceFingerprintsV4 ? new ArrayList(deviceFingerprintsV4.keySet()) : []
            
            // Count computed fingerprints
            int totalComputedFingerprints = 0
            deviceFingerprintsV4.each { key, value ->
                totalComputedFingerprints += value.computedFingerprints?.size() ?: 0
            }
            
            logInfo "cacheTest Info: deviceProfilesV3 size=${size} keys=${keys}"
            logInfo "cacheTest Info: deviceFingerprintsV4 size=${fingerprintSize} keys=${fingerprintKeys}"
            logInfo "cacheTest Info: total computed fingerprint strings=${totalComputedFingerprints}"
            break
        case 'Initialize':
            boolean ok = ensureProfilesLoaded()
            logInfo "cacheTest Initialize: ensureProfilesLoaded() -> ${ok}; size now ${deviceProfilesV3.size()}"
            break
        case 'ReconstructedFingerprints':
            if (deviceFingerprintsV4.isEmpty()) {
                logInfo "cacheTest ReconstructedFingerprints: no fingerprints loaded - run Initialize first"
            } else {
                deviceFingerprintsV4.each { profileName, fingerprintData ->
                    int fpCount = fingerprintData.computedFingerprints?.size() ?: 0
                    if (fpCount > 0) {
                        StringBuilder allFingerprints = new StringBuilder()
                        allFingerprints.append("Profile ${profileName} has ${fpCount} computed fingerprints:<br>")
                        fingerprintData.computedFingerprints.eachWithIndex { fpString, index ->
                            allFingerprints.append(" [${index + 1}] ${fpString}")
                            if (index < fpCount - 1) allFingerprints.append(" <br>")  // add line break except after last
                        }
                        logInfo "cacheTest ReconstructedFingerprints: ${allFingerprints.toString()}"
                    } else {
                        logInfo "cacheTest ReconstructedFingerprints: Profile ${profileName} has no computed fingerprints"
                    }
                }
                logInfo "cacheTest ReconstructedFingerprints: completed"
            }
            break
        case 'Clear':
            int before = deviceProfilesV3.size()
            int beforeFingerprints = deviceFingerprintsV4.size()
            deviceProfilesV3.clear()
            deviceFingerprintsV4.clear()
            profilesLoaded = false
            profilesLoading = false
            logInfo "cacheTest Clear: cleared ${before} profiles and ${beforeFingerprints} fingerprint entries"
            break
        default:
            logWarn "cacheTest: unknown action '${action}'"
    }
}


@Field static  Map deviceProfilesV3 = [:]
@Field static  boolean profilesLoading = false
@Field static  boolean profilesLoaded = false
@Field static  Map deviceFingerprintsV4 = [:]

// -------------- new test functions - add here !!! -------------------------

/**
 * Reconstructs a complete fingerprint Map by merging original fingerprint with defaultFingerprint values
 * This is similar to fingerprintIt() but returns a Map instead of a formatted String
 */
private Map reconstructFingerprint(Map profileMap, Map fingerprint) {
    if (profileMap == null || fingerprint == null) { 
        return fingerprint ?: [:] 
    }
    
    Map defaultFingerprint = profileMap.defaultFingerprint ?: [:]
    // if there is no defaultFingerprint, use the fingerprint as is
    if (defaultFingerprint == [:]) {
        return fingerprint
    }
    
    // Create a new Map with default values, then overlay with actual fingerprint values
    Map reconstructed = [:]
    defaultFingerprint.each { key, defaultValue ->
        reconstructed[key] = fingerprint[key] ?: defaultValue
    }
    
    // Add any additional keys that exist in fingerprint but not in defaultFingerprint
    fingerprint.each { key, value ->
        if (!reconstructed.containsKey(key)) {
            reconstructed[key] = value
        }
    }
    
    return reconstructed
}

boolean loadProfilesFromJSON() {
    long startTime = now()
    
    // idempotent : don't re-parse if already populated
    if (!deviceProfilesV3.isEmpty()) {
        logDebug "loadProfilesFromJSON: already loaded (${deviceProfilesV3.size()} profiles)"
        return true
    }
    try {
        logDebug "loadProfilesFromJSON: start loading device profiles from JSON..."
        if (!testJSON) {
            logWarn "loadProfilesFromJSON: testJSON is empty/null"
            return false
        }
        def parsed = new groovy.json.JsonSlurper().parseText(testJSON.trim())
        def dp = parsed?.deviceProfiles
        if (!(dp instanceof Map) || dp.isEmpty()) {
            logWarn "loadProfilesFromJSON: parsed deviceProfiles missing or empty"
            return false
        }
        // !!!!!!!!!!!!!!!!!!!!!!!
        // Populate deviceProfilesV3
        deviceProfilesV3.putAll(dp as Map)
        logDebug "loadProfilesFromJSON: deviceProfilesV3 populated with ${deviceProfilesV3.size()} profiles"

        // Populate deviceFingerprintsV4 using bulk assignment for better performance
        // Use fingerprintIt() logic to reconstruct complete fingerprint data
        Map localFingerprints = [:]
        
        deviceProfilesV3.each { profileKey, profileMap ->
            // Reconstruct complete fingerprint Maps and pre-compute strings
            List<Map> reconstructedFingerprints = []
            List<String> computedFingerprintStrings = []
            
            if (profileMap.fingerprints != null) {
                profileMap.fingerprints.each { fingerprint ->
                    // Reconstruct complete fingerprint using fingerprintIt logic
                    Map reconstructedFingerprint = reconstructFingerprint(profileMap, fingerprint)
                    reconstructedFingerprints.add(reconstructedFingerprint)
                    
                    // Also create formatted string for debugging
                    String fpString = fingerprintIt(profileMap, fingerprint)
                    if (fpString && fpString != 'profileMap is null' && fpString != 'fingerprint is null') {
                        computedFingerprintStrings.add(fpString)
                    }
                }
            }
            
            localFingerprints[profileKey] = [
                description: profileMap.description ?: '',
                fingerprints: reconstructedFingerprints, // Use reconstructed complete fingerprints
                computedFingerprints: computedFingerprintStrings
            ]
        }
        
        deviceFingerprintsV4.clear()
        deviceFingerprintsV4.putAll(localFingerprints)

        // Count total computed fingerprint strings
        int totalComputedFingerprints = 0
        localFingerprints.each { key, value ->
            totalComputedFingerprints += value.computedFingerprints?.size() ?: 0
        }

        // NOTE: profilesLoaded flag is managed by ensureProfilesLoaded(), not here
        // This keeps loadProfilesFromJSON() as a pure function
        long endTime = now()
        long executionTime = endTime - startTime
        
        logDebug "loadProfilesFromJSON: loaded ${deviceProfilesV3.size()} profiles: ${deviceProfilesV3.keySet()}"
        logDebug "loadProfilesFromJSON: populated ${deviceFingerprintsV4.size()} fingerprint entries"
        logDebug "loadProfilesFromJSON: pre-computed ${totalComputedFingerprints} fingerprint strings"
        logDebug "loadProfilesFromJSON: execution time: ${executionTime}ms"
        return true
    } catch (Exception e) {
        long endTime = now()
        long executionTime = endTime - startTime
        logError "loadProfilesFromJSON: error converting JSON: ${e.message} (execution time: ${executionTime}ms)"
        return false
    }
}

/**
 * Ensures that device profiles are loaded with thread-safe lazy loading
 * This is the main function that should be called before accessing deviceProfilesV3
 * @return true if profiles are loaded successfully, false otherwise
 */
private boolean ensureProfilesLoaded() {
    // Fast path: already loaded
    if (!deviceProfilesV3.isEmpty() && profilesLoaded) {
        return true
    }
    
    // Check if another thread is already loading
    if (profilesLoading) {
        // Wait briefly for other thread to finish
        for (int i = 0; i < 10; i++) {
            logDebug "ensureProfilesLoaded: waiting <b>100ms</b> for other thread to finish loading..."
            pauseExecution(100)
            if (profilesLoaded && !deviceProfilesV3.isEmpty()) {
                logDebug "ensureProfilesLoaded: other thread finished loading"
                return true
            }
        }
        // If still loading after wait, return false - don't interfere with other thread
        logWarn "ensureProfilesLoaded: timeout waiting for other thread, returning false"
        return false
    }
    
    // Acquire loading lock
    profilesLoading = true
    try {
        // Double-check after acquiring lock
        if (deviceProfilesV3.isEmpty() || !profilesLoaded) {
            logWarn "ensureProfilesLoaded: loading device profiles...(deviceProfilesV3.isEmpty()=${deviceProfilesV3.isEmpty()}, profilesLoaded=${profilesLoaded})"
            boolean result = loadProfilesFromJSON()
            if (result) {
                profilesLoaded = true
                logInfo "ensureProfilesLoaded: successfully loaded ${deviceProfilesV3.size()} deviceProfilesV3 profiles"
            } else {
                logWarn "ensureProfilesLoaded: failed to load device profiles"
            }
            profilesLoading = false
            return result
        }
        return true
    } finally {
        profilesLoading = false
    }
}




// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
