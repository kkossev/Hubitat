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
 * ver. 3.2.0  2024-05-26 kkossev  - (dev.branch) firt version, based on the mmWave radar driver code : depricated Linptech; added TS0202 add _TYZB01_vwqnz1sn; 
 *                                   
 *                                   TODO: temperature and humidity thresholds
 *                                   TODO: temperature and humidity calibration (offsets)
 *                                   TODO: for 4IN1 (Fantem) - add in refresh() : cmds += zigbee.command(0xEF00, 0x07, '00')    // Fantem Tuya Magic
 *                                   TODO: for Tuya- add in refresh() : cmds += zigbee.command(0xEF00, 0x03)
 *                                   TODO: https://community.hubitat.com/t/moes-tuya-motion-sensor-distance-issue-ts0202-have-to-be-ridiculously-close-to-detect-movement/109917/8?u=kkossev 
 *                                   TODO: hide depricated devices
 *                                   TODO: make new GitHub WiKi 
 *                                   TODO: add the state tuyaDps as in the 4-in-1 driver!
 *                                   TODO: cleanup the 4-in-1 state variables.
 *                                   TODO: illumState default value is 0 - should be 'unknown' ?
 *                                   TODO: Motion reset to inactive after 43648s - convert to H:M:S
*/

static String version() { "3.2.0" }
static String timeStamp() {"2024/05/27 11:42 PM"}

@Field static final Boolean _DEBUG = false
@Field static final Boolean _TRACE_ALL = false              // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true    // disable it for production


import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput
import groovy.transform.CompileStatic









deviceType = "MultiSensor4in1"
@Field static final String DEVICE_TYPE = "MultiSensor4in1"

metadata {
    definition (
        name: 'Tuya Multi Sensor 4 In 1 (V3)',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Multi%20Sensor%204%20In%201/Tuya%20Multi%20Sensor%204%20In%201%20(V3).groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {

        capability 'MotionSensor'

        attribute 'distance', 'number'              // Tuya Radar, obsolete
        attribute 'unacknowledgedTime', 'number'    // AIR models
        attribute 'keepTime', 'enum', ['10 seconds', '30 seconds', '60 seconds', '120 seconds']
        attribute 'motionDetectionDistance', 'decimal'  // changed 05/11/2024 - was 'number'

        attribute 'sensitivity', 'number'
        //attribute 'detectionDelay', 'decimal'
        attribute 'fadingTime', 'decimal'
        //attribute 'humanMotionState', 'enum', ['none', 'moving', 'small_move', 'stationary', 'static', 'presence', 'peaceful', 'large_move']
        attribute 'illumState', 'enum', ['dark', 'light', 'unknown']
        attribute 'ledIndicator', 'number'
        attribute 'reportingTime4in1', 'number'
        attribute 'ledEnable', 'enum', ['disabled', 'enabled']
        attribute 'WARNING', 'string'

        command 'setMotion', [[name: 'setMotion', type: 'ENUM', constraints: ['No selection', 'active', 'inactive'], description: 'Force motion active/inactive (for tests)']]

        // itterate through all the figerprints and add them on the fly
        deviceProfilesV3.each { profileName, profileMap ->
            if (profileMap.fingerprints != null) {
                if (profileMap.device?.isDepricated != true) {
                    profileMap.fingerprints.each {
                        fingerprint it
                    }
                }
            }
        }        
    }

    preferences {
        if (device) {
            if (DEVICE?.device?.isDepricated == true) {
                input(name: 'depricated',  type: 'hidden', title: "$ttStyleStr<a href='https://github.com/kkossev/Hubitat/wiki/Tuya-Multi-Sensor-4-In-1' target='_blank'><b>This is the right driver</b><br> for use with <b>${state.deviceProfile}</b> device!<br><br><i>Please change the driver to 'Tuya Zigbee mmWave Sensor' as per the instructions in this link!</i></a>")
            }
        }
        input(name: 'info',    type: 'hidden', title: "<a href='https://github.com/kkossev/Hubitat/wiki/Tuya-Zigbee-mmWave-Sensor' target='_blank'><i>For more info, click on this link to visit the WiKi page</i></a>")
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_DEBUG_LOGGING, description: '<i>Turns on debug logging for 24 hours.</i>'
        if (device) {

            if (('motionReset' in DEVICE?.preferences) && (DEVICE?.preferences.motionReset == true)) {
                input(name: 'motionReset', type: 'bool', title: '<b>Reset Motion to Inactive</b>', description: '<i>Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b></i>', defaultValue: false)
                if (settings?.motionReset?.value == true) {
                    input('motionResetTimer', 'number', title: '<b>Motion Reset Timer</b>', description: '<i>After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds</i>', range: '0..7200', defaultValue: 60)
                }
            }

            if ((DEVICE?.capabilities?.IlluminanceMeasurement == true) && (DEVICE?.preferences.luxThreshold != false) && !(DEVICE?.device?.isDepricated == true)) {
                input('luxThreshold', 'number', title: '<b>Lux threshold</b>', description: 'Minimum change in the lux which will trigger an event', range: '0..999', defaultValue: 5)
                input name: 'illuminanceCoeff', type: 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: '<i>Illuminance correction coefficient, range (0.10..10.00)</i>', range: '0.10..10.00', defaultValue: 1.00
            }
            if (('DistanceMeasurement' in DEVICE?.capabilities)) {
                input(name: 'ignoreDistance', type: 'bool', title: '<b>Ignore distance reports</b>', description: 'If not used, ignore the distance reports received every 1 second!', defaultValue: true)
            }
        }

        // the rest of the preferences are inputIt from the deviceProfileLib 
    }
}

@Field static String ttStyleStr = '<style>.tTip {display:inline-block;border-bottom: 1px dotted black;}.tTip .tTipText {display:none;border-radius: 6px;padding: 5px 0;position: absolute;z-index: 1;}.tTip:hover .tTipText {display:inline-block;background-color:red;color:red;}</style>'
boolean is4in1() { return getDeviceProfile().contains('TS0202_4IN1') }

// based on 'Tuya Multi Sensor 4 In 1' version '1.9.0' '2024/05/06 10:39 AM' 
@Field static final Map deviceProfilesV3 = [
    // is4in1()
    'TS0202_4IN1'  : [
            description   : 'Tuya 4in1 (motion/temp/humi/lux) sensor',
            models        : ['TS0202'],         // model: 'ZB003-X'  vendor: 'Fantem'
            device        : [type: 'PIR', isIAS:true, powerSource: 'dc', isSleepy:false],    // check powerSource
            capabilities  : ['MotionSensor': true, 'TemperatureMeasurement': true, 'RelativeHumidityMeasurement': true, 'IlluminanceMeasurement': true, 'tamper': true, 'Battery': true],
            preferences   : ['motionReset':true, 'reportingTime4in1':'102', 'ledEnable':'111', 'keepTime':'0x0500:0xF001', 'sensitivity':'0x0500:0x0013'],
            commands      : ['reportingTime4in1':'reportingTime4in1', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences', 'printFingerprints':'printFingerprints', 'printPreferences':'printPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,EF00', outClusters:'0019,000A', model:'TS0202',  manufacturer:'_TZ3210_zmy9hjay', deviceJoinName: 'Tuya TS0202 Multi Sensor 4 In 1'],        // pairing: double click!
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,EF00', outClusters:'0019,000A', model:'5j6ifxj', manufacturer:'_TYST11_i5j6ifxj', deviceJoinName: 'Tuya TS0202 Multi Sensor 4 In 1'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,EF00', outClusters:'0019,000A', model:'hfcudw5', manufacturer:'_TYST11_7hfcudw5', deviceJoinName: 'Tuya TS0202 Multi Sensor 4 In 1'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,EF00', outClusters:'0019,000A', model:'TS0202',  manufacturer:'_TZ3210_rxqls8v0', deviceJoinName: 'Tuya TS0202 Multi Sensor 4 In 1'],        // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,EF00', outClusters:'0019,000A', model:'TS0202',  manufacturer:'_TZ3210_wuhzzfqg', deviceJoinName: 'Tuya TS0202 Multi Sensor 4 In 1'],        // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars/92441/282?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,EF00', outClusters:'0019,000A', model:'TS0202',  manufacturer:'_TZ3210_0aqbrnts', deviceJoinName: 'Tuya TS0202 Multi Sensor 4 In 1 is-thpl-zb']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',                                 type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'inactive', 1:'active'] ,   unit:'',  description:'<i>Motion</i>'],
                [dp:5,   name:'tamper',                                 type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'clear', 1:'detected'] ,   unit:'',  description:'<i>Tamper detection</i>'],
                [dp:25,  name:'battery2',                               type:'number',  rw: 'ro', min:0,     max:100,  defVal:100,  scale:1,  unit:'%',          description:'<i>Remaining battery 2 in %</i>'],
                [dp:102, name:'reportingTime4in1', dt:'02', tuyaCmd:04, type:'number',  rw: 'rw', min:0, max:240, defVal:10, step:5, scale:1, unit:'minutes', title:'<b>Reporting Interval</b>', description:'<i>Reporting interval in minutes</i>'],
                [dp:104, name:'tempCalibration',                        type:'decimal', rw: 'ro', min:-2.0,  max:2.0,  defVal:0.0,  scale:10, unit:'deg.',  title:'<b>Temperature Calibration</b>',       description:'<i>Temperature calibration (-2.0...2.0)</i>'],
                [dp:105, name:'humiCalibration',                        type:'number',  rw: 'ro', min:-15,   max:15,   defVal:0,    scale:1,  unit:'%RH',    title:'<b>Huidity Calibration</b>',     description:'<i>Humidity Calibration</i>'],
                [dp:106, name:'illumCalibration',                       type:'number',  rw: 'ro', min:-20, max:20, defVal:0,        scale:1, unit:'Lx', title:'<b>Illuminance Calibration</b>', description:'<i>Illuminance calibration in lux/i>'],
                [dp:107, name:'temperature',                            type:'decimal', rw: 'ro', min:-20.0, max:80.0, defVal:0.0,  scale:10, unit:'deg.',       description:'<i>Temperature</i>'],
                [dp:108, name:'humidity',                               type:'number',  rw: 'ro', min:1,     max:100,  defVal:100,  scale:1,  unit:'%RH',        description:'<i>Humidity</i>'],
                [dp:109, name:'pirSensorEnable',                        type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'1',  scale:1,  map:[0:'disabled', 1:'enabled'] ,   unit:'', title:'<b>MoPIR Sensor Enable</b>',  description:'<i>Enable PIR sensor</i>'],
                [dp:110, name:'battery',                                type:'number',  rw: 'ro', min:0,     max:100,  defVal:100,  scale:1,  unit:'%',          description:'<i>Battery level</i>'],
                [dp:111, name:'ledEnable',       dt:'01', tuyaCmd:04,   type:'enum',    rw: 'rw', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'disabled', 1:'enabled'] ,   unit:'', title:'<b>LED Enable</b>',  description:'<i>Enable LED</i>'],
                [dp:112, name:'reportingEnable',                        type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'disabled', 1:'enabled'] ,   unit:'', title:'<b>Reporting Enable</b>',  description:'<i>Enable reporting</i>'],
            ],
            attributes:       [
                [at:'0x0500:0x0013',  name:'sensitivity', type:'enum',    rw: 'rw', min:0,     max:2,    defVal:'2',  unit:'',           map:[0:'low', 1:'medium', 2:'high'], title:'<b>Sensitivity</b>',   description:'<i>PIR sensor sensitivity (update at the time motion is activated)</i>'],
                [at:'0x0500:0xF001',  name:'keepTime',    type:'enum',    rw: 'rw', min:0,     max:5,    defVal:'0',  unit:'seconds',    map:[0:'0 seconds', 1:'30 seconds', 2:'60 seconds', 3:'120 seconds', 4:'240 seconds', 5:'480 seconds'], title:'<b>Keep Time</b>',   description:'<i>PIR keep time in seconds (update at the time motion is activated)</i>']
            ],

            deviceJoinName: 'Tuya Multi Sensor 4 In 1',
            configuration : ['battery': false]
    ],

    // tested TS0601  _TZE200_7hfcudw5 - OK
    'TS0601_3IN1'  : [                                // https://szneo.com/en/products/show.php?id=239 // https://www.banggood.com/Tuya-Smart-Linkage-ZB-Motion-Sensor-Human-Infrared-Detector-Mobile-Phone-Remote-Monitoring-PIR-Sensor-p-1858413.html?cur_warehouse=CN
            description   : 'Tuya 3in1 (Motion/Temp/Humi) sensor',
            models        : ['TS0601'],
            device        : [type: 'PIR', powerSource: 'dc', isSleepy:false],    //  powerSource changes batt/DC dynamically!
            capabilities  : ['MotionSensor': true, 'TemperatureMeasurement': true, 'RelativeHumidityMeasurement': true, 'tamper': true, 'Battery': true],
            preferences   : ['motionReset':true],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_7hfcudw5', deviceJoinName: 'Tuya NAS-PD07 Multi Sensor 3 In 1'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ppuj1vem', deviceJoinName: 'Tuya NAS-PD07 Multi Sensor 3 In 1']
            ],
            tuyaDPs:        [
                [dp:101, name:'motion',          type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'inactive', 1:'active'] ,   unit:'',  description:'<i>Motion</i>'],
                [dp:102, name:'battery',         type:'number',  rw: 'ro', min:0,     max:100,  defVal:100,  scale:1,  unit:'%',          description:'<i>Battery level</i>'],
                //            ^^^TODO^^^
                [dp:103, name:'tamper',          type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'clear', 1:'detected'] ,   unit:'',  description:'<i>Tamper detection</i>'],
                [dp:104, name:'temperature',     type:'decimal', rw: 'ro', min:-20.0, max:80.0, defVal:0.0,  scale:10, unit:'deg.',       description:'<i>Temperature</i>'],
                [dp:105, name:'humidity',        type:'number',  rw: 'ro', min:1,     max:100,  defVal:100,  scale:1,  unit:'%RH',        description:'<i>Humidity</i>'],
                [dp:106, name:'tempScale',       type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'Celsius', 1:'Fahrenheit'] ,   unit:'',  description:'<i>Temperature scale</i>'],
                [dp:107, name:'minTemp',         type:'number',  rw: 'ro', min:-20,   max:80,   defVal:0,    scale:1,  unit:'deg.',       description:'<i>Minimal temperature</i>'],
                [dp:108, name:'maxTemp',         type:'number',  rw: 'ro', min:-20,   max:80,   defVal:0,    scale:1,  unit:'deg.',       description:'<i>Maximal temperature</i>'],
                [dp:109, name:'minHumidity',     type:'number',  rw: 'ro', min:0,     max:100,  defVal:0,    scale:1,  unit:'%RH',        description:'<i>Minimal humidity</i>'],
                [dp:110, name:'maxHumidity',     type:'number',  rw: 'ro', min:0,     max:100,  defVal:0,    scale:1,  unit:'%RH',        description:'<i>Maximal humidity</i>'],
                [dp:111, name:'tempAlarm',       type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'inactive', 1:'active'] ,   unit:'',  description:'<i>Temperature alarm</i>'],
                [dp:112, name:'humidityAlarm',   type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'inactive', 1:'active'] ,   unit:'',  description:'<i>Humidity alarm</i>'],
                [dp:113, name:'alarmType',       type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'type0', 1:'type1'] ,   unit:'',  description:'<i>Alarm type</i>'],
            ],
            deviceJoinName: 'Tuya Multi Sensor 3 In 1',
            configuration : ['battery': false]
    ],

    // is2in1()
    'TS0601_2IN1'  : [      // https://github.com/Koenkk/zigbee-herdsman-converters/blob/bf32ce2b74689328048b407e56ca936dc7a54a0b/src/devices/tuya.ts#L4568
            description   : 'Tuya 2in1 (Motion and Illuminance) sensor',
            models         : ['TS0601'],
            device        : [type: 'PIR', isIAS:false, powerSource: 'battery', isSleepy:true],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'Battery': true],
            preferences   : ['motionReset':true, 'invertMotion':true, 'keepTime':'10', 'sensitivity':'9'],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_3towulqd', deviceJoinName: 'Tuya 2 in 1 Zigbee Mini PIR Motion Detector + Bright Lux ZG-204ZL'],          // https://www.aliexpress.com/item/1005004095233195.html
                [profileId:'0104', endpointId:'01', inClusters:'0000,0500,0001,0400', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_3towulqd', deviceJoinName: 'Tuya 2 in 1 Zigbee Mini PIR Motion Detector + Bright Lux ZG-204ZL'],     // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/934?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_bh3n6gk8', deviceJoinName: 'Tuya 2 in 1 Zigbee Mini PIR Motion Detector + Bright Lux ZG-204ZL'],          // https://community.hubitat.com/t/tze200-bh3n6gk8-motion-sensor-not-working/123213?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_1ibpyhdc', deviceJoinName: 'Tuya 2 in 1 Zigbee Mini PIR Motion Detector + Bright Lux ZG-204ZL']          //
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',                type:'enum',   rw: 'ro', min:0, max:1 ,   defVal:'0',  scale:1,  map:[0:'inactive', 1:'active'] ,   unit:'',  description:'<i>Motion</i>'],
                [dp:4,   name:'battery',               type:'number', rw: 'ro', min:0, max:100,  defVal:100,  scale:1,  unit:'%',          title:'<b>Battery level</b>',              description:'<i>Battery level</i>'],
                [dp:9,   name:'sensitivity',           type:'enum',   rw: 'rw', min:0, max:2,    defVal:'2',  unit:'',           map:[0:'low', 1:'medium', 2:'high'], title:'<b>Sensitivity</b>',   description:'<i>PIR sensor sensitivity (update at the time motion is activated)</i>'],
                [dp:10,  name:'keepTime',              type:'enum',   rw: 'rw', min:0, max:3,    defVal:'0',  unit:'seconds',    map:[0:'10 seconds', 1:'30 seconds', 2:'60 seconds', 3:'120 seconds'], title:'<b>Keep Time</b>',   description:'<i>PIR keep time in seconds (update at the time motion is activated)</i>'],
                [dp:12,  name:'illuminance',           type:'number', rw: 'ro', min:0, max:1000, defVal:0,    scale:1,  unit:'lx',       title:'<b>illuminance</b>',     description:'<i>illuminance</i>'],
                [dp:102, name:'illuminance_interval',  type:'number', rw: 'rw', min:1, max:720,  defVal:1,    scale:1,  unit:'minutes',  title:'<b>Illuminance Interval</b>',     description:'<i>Brightness acquisition interval (update at the time motion is activated)</i>'],

            ],
            deviceJoinName: 'Tuya Multi Sensor 2 In 1',
            configuration : ['battery': false]
    ],

    'TS0202_MOTION_IAS'   : [
            description   : 'Tuya TS0202 Motion sensor (IAS)',
            models        : ['TS0202', 'RH3040'],
            device        : [type: 'PIR', isIAS:true, powerSource: 'battery', isSleepy:true],
            capabilities  : ['MotionSensor': true, 'Battery': true],
            preferences   : ['motionReset':true, 'keepTime':false, 'sensitivity':false],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0001,0500', outClusters:'0000,0003,0001,0500', model:'TS0202', manufacturer:'_TYZB01_dl7cejts', deviceJoinName: 'Tuya TS0202 Motion Sensor'],             // KK model: 'ZM-RT201'// 5 seconds (!) reset period for testing
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3000_mmtwjmaq', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3000_otvn3lne', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TYZB01_jytabjkb', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TYZB01_ef5xlc9q', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TYZB01_vwqnz1sn', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TYZB01_2b8f6cio', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TYZB01_vwqnz1sn', deviceJoinName: 'Tuya TS0202 Motion Sensor'],            // https://community.hubitat.com/t/moes-tuya-motion-sensor-distance-issue-ts0202-have-to-be-ridiculously-close-to-detect-movement/109917/8?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZE200_bq5c8xfe', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TYZB01_qjqgmqxr', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TYZB01_zwvaj5wy', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3000_bsvqrxru', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TYZB01_tv3wxhcz', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TYZB01_hqbdru35', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3000_tiwq83wk', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3000_ykwcwxmz', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3000_hgu1dlak', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3000_hktqahrq', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3000_jmrgyl7o', deviceJoinName: 'Tuya TS0202 Motion Sensor'],            // not tested! //https://zigbee.blakadder.com/Luminea_ZX-5311.html
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'WHD02',  manufacturer:'_TZ3000_kmh5qpmb', deviceJoinName: 'Tuya TS0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3000_kmh5qpmb', deviceJoinName: 'Tuya TS0202 Motion Sensor'],             // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-w-healthstatus/92441/1059?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3040_usvkzkyn', deviceJoinName: 'Tuya TS0202 Motion Sensor'],            // not tested // https://www.amazon.ae/Rechargeable-Detector-Security-Devices-Required/dp/B0BKKJ48QH
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-53o41joc', deviceJoinName: 'TUYATEC RH3040 Motion Sensor'],                                            // 60 seconds reset period
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-b5g40alm', deviceJoinName: 'TUYATEC RH3040 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-deetibst', deviceJoinName: 'TUYATEC RH3040 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-bd5faf9p', deviceJoinName: 'Nedis/Samotech RH3040 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-zn9wyqtr', deviceJoinName: 'Samotech RH3040 Motion Sensor'],                                           // vendor: 'Samotech', model: 'SM301Z'
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-b3ov3nor', deviceJoinName: 'Zemismart RH3040 Motion Sensor'],                                          // vendor: 'Nedis', model: 'ZBSM10WT'
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-2gn2zf9e', deviceJoinName: 'TUYATEC RH3040 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500,0B05', outClusters:'0019', model:'TY0202', manufacturer:'_TZ1800_fcdjzz3s', deviceJoinName: 'Lidl TY0202 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500,0B05,FCC0', outClusters:'0019,FCC0', model:'TY0202', manufacturer:'_TZ3000_4ggd8ezp', deviceJoinName: 'Bond motion sensor ZX-BS-J11W'],        // https://community.hubitat.com/t/what-driver-to-use-for-this-motion-sensor-zx-bs-j11w-or-ty0202/103953/4
                [profileId:'0104', endpointId:'01', inClusters:'0001,0003,0004,0500,0000', outClusters:'0004,0006,1000,0019,000A', model:'TS0202', manufacturer:'_TZ3040_bb6xaihh', deviceJoinName: 'Tuya TS0202 Motion Sensor'],  // https://github.com/Koenkk/zigbee2mqtt/issues/17364
                [profileId:'0104', endpointId:'01', inClusters:'0001,0003,0004,0500,0000', outClusters:'0004,0006,1000,0019,000A', model:'TS0202', manufacturer:'_TZ3040_wqmtjsyk', deviceJoinName: 'Tuya TS0202 Motion Sensor'],  // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0001,0003,0004,0500,0000', outClusters:'0004,0006,1000,0019,000A', model:'TS0202', manufacturer:'_TZ3000_h4wnrtck', deviceJoinName: 'Tuya TS0202 Motion Sensor']   // not tested

            ],
            deviceJoinName: 'Tuya TS0202 Motion Sensor',
            configuration : ['battery': false]
    ],

    'TS0202_MOTION_IAS_CONFIGURABLE'   : [
            description   : 'Tuya TS0202 Motion sensor (IAS) configurable',
            models        : ['TS0202'],
            device        : [type: 'PIR', isIAS:true, powerSource: 'battery', isSleepy:true],
            capabilities  : ['MotionSensor': true, 'Battery': true],
            preferences   : ['motionReset':true, 'keepTime':'0x0500:0xF001', 'sensitivity':'0x0500:0x0013'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3000_mcxw5ehu', deviceJoinName: 'Tuya TS0202 ZM-35H-Q Motion Sensor'],    // TODO: PIR sensor sensitivity and PIR keep time in seconds ['30', '60', '120']
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3000_msl6wxk9', deviceJoinName: 'Tuya TS0202 ZM-35H-Q Motion Sensor'],    // TODO: fz.ZM35HQ_attr ['30', '60', '120']
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3040_msl6wxk9', deviceJoinName: 'Tuya TS0202 ZM-35H-Q Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3040_fwxuzcf4', deviceJoinName: 'Tuya TS0202 ZM-35H-Q Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3000_6ygjfyll', deviceJoinName: 'Tuya TS0202 Motion Sensor'],            // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars/92441/289?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,0003,0000', outClusters:'1000,0006,0019,000A', model:'TS0202', manufacturer:'_TZ3040_6ygjfyll', deviceJoinName: 'Tuya TS0202 Motion Sensor'],            // https://community.hubitat.com/t/tuya-motion-sensor-driver/72000/54?u=kkossev

            ],
            attributes:       [
                [at:'0x0500:0x0013', name:'sensitivity', type:'enum',   rw: 'rw', min:0, max:2,    defVal:'2',  unit:'',           map:[0:'low', 1:'medium', 2:'high'], title:'<b>Sensitivity</b>',   description:'<i>PIR sensor sensitivity (update at the time motion is activated)</i>'],
                [at:'0x0500:0xF001', name:'keepTime',    type:'enum',   rw: 'rw', min:0, max:2,    defVal:'0',  unit:'seconds',    map:[0:'30 seconds', 1:'60 seconds', 2:'120 seconds'], title:'<b>Keep Time</b>',   description:'<i>PIR keep time in seconds (update at the time motion is activated)</i>'],
            ],
            deviceJoinName: 'Tuya TS0202 Motion Sensor configurable',
            configuration : ['battery': false]
    ],

    // isMotionSwitch()
    'TS0202_MOTION_SWITCH': [
            description   : 'Tuya Motion Sensor and Scene Switch',
            models        : ['TS0202'],
            device        : [type: 'PIR', isIAS:true, powerSource: 'battery', isSleepy:true],
            capabilities  : ['MotionSensor':true, 'IlluminanceMeasurement':true, 'switch':true, 'Battery':true],
            preferences   : ['motionReset':true, 'keepTime':false, 'sensitivity':false, 'luxThreshold':false],    // keepTime is hardcoded 60 seconds, no sensitivity configuration
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0001,0500,EF00,0000', outClusters:'0019,000A', model:'TS0202', manufacturer:'_TZ3210_cwamkvua', deviceJoinName: 'Tuya Motion Sensor and Scene Switch']  // vendor: 'Linkoze', model: 'LKMSZ001'

            ],
            tuyaDPs:        [
                [dp:101, name:'pushed',         type:'enum',   rw: 'ro', min:0, max:2, defVal:'0',   scale:1,    map:[0:'pushed', 1:'doubleTapped', 2:'held'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:102, name:'illuminance',    type:'number', rw: 'ro', min:0, max:1, defVal:0,     scale:1,    unit:'lx',       title:'<b>illuminance</b>',     description:'<i>illuminance</i>'],

            ],
            deviceJoinName: 'Tuya Motion Sensor and Scene Switch',
            configuration : ['battery': false]
    ],

    'TS0601_PIR_PRESENCE'   : [ // isBlackPIRsensor()       // https://github.com/zigpy/zha-device-handlers/issues/1618
            description   : 'Tuya PIR Human Motion Presence Sensor (Black)',
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'Battery': true],
            preferences   : ['fadingTime':'102', 'distance':'105'],
            commands      : ['resetStats':'resetStats', 'resetPreferencesToDefaults':'resetPreferencesToDefaults'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_9qayzqa8', deviceJoinName: 'Smart PIR Human Motion Presence Sensor (Black)']    // https://www.aliexpress.com/item/1005004296422003.html
            ],
            tuyaDPs:        [                                           // TODO - defaults !!
                [dp:102, name:'fadingTime',          type:'number',  rw: 'rw', min:24,  max:300 ,  defVal:24,        scale:1,    unit:'seconds',      title:'<b>Fading time</b>',   description:'<i>Fading(Induction) time</i>'],
                [dp:105, name:'distance',      type:'enum',    rw: 'rw', min:0,   max:9 ,    defVal:'6',       scale:1,    map:[0:'0.5 m', 1:'1.0 m', 2:'1.5 m', 3:'2.0 m', 4:'2.5 m', 5:'3.0 m', 6:'3.5 m', 7:'4.0 m', 8:'4.5 m', 9:'5.0 m'] ,   unit:'meters',     title:'<b>Target Distance</b>', description:'<i>Target Distance</i>'],
                [dp:119, name:'motion',              type:'enum',    rw: 'ro', min:0,   max:1 ,    defVal:'0',       scale:1,    map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:141, name:'humanMotionState',    type:'enum',    rw: 'ro', min:0,   max:4 ,    defVal:'0',       scale:1,    map:[0:'none', 1:'presence', 2:'peaceful', 3:'small_move', 4:'large_move'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
            ],
            deviceJoinName: 'Tuya PIR Human Motion Presence Sensor LQ-CG01-RDR',
            configuration : ['battery': false]
    ],

    'TS0601_PIR_AIR'      : [    // isHumanPresenceSensorAIR()  - Human presence sensor AIR (PIR sensor!) - o_sensitivity, v_sensitivity, led_status, vacancy_delay, light_on_luminance_prefer, light_off_luminance_prefer, mode, luminance_level, reference_luminance, vacant_confirm_time
            description   : 'Tuya PIR Human Motion Presence Sensor AIR',
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'Battery': true],                // TODO - check if battery powered?
            preferences   : ['vacancyDelay':'103', 'ledStatusAIR':'110', 'detectionMode':'104', 'vSensitivity':'101', 'oSensitivity':'102', 'lightOnLuminance':'107', 'lightOffLuminance':'108' ],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_auin8mzr', deviceJoinName: 'Tuya PIR Human Motion Presence Sensor AIR']        // Tuya LY-TAD-K616S-ZB
            ],
            tuyaDPs:        [                                           // TODO - defaults !!
                [dp:101, name:'vSensitivity',        type:'enum',    rw: 'rw', min:0,   max:1,     defVal:'0', scale:1,    map:[0:'Speed Priority', 1:'Standard', 2:'Accuracy Priority'] ,   unit:'-',     title:'<b>vSensitivity options</b>', description:'<i>V-Sensitivity mode</i>'],
                [dp:102, name:'oSensitivity',        type:'enum',    rw: 'rw', min:0,   max:1,     defVal:'0', scale:1,    map:[0:'Sensitive', 1:'Normal', 2:'Cautious'] ,   unit:'',     title:'<b>oSensitivity options</b>', description:'<i>O-Sensitivity mode</i>'],
                [dp:103, name:'vacancyDelay',        type:'number',  rw: 'rw', min:0,   max:1000,  defVal:10,  scale:1,    unit:'seconds',        title:'<b>Vacancy Delay</b>',          description:'<i>Vacancy Delay</i>'],
                [dp:104, name:'detectionMode',       type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0', scale:1,    map:[0:'General Model', 1:'Temporary Stay', 2:'Basic Detecton', 3:'PIR Sensor Test'] ,   unit:'',     title:'<b>Detection Mode</b>', description:'<i>Detection Mode</i>'],
                [dp:105, name:'unacknowledgedTime',  type:'number',  rw: 'ro', min:0,   max:9 ,    defVal:7,   scale:1,    unit:'seconds',         description:'<i>unacknowledgedTime</i>'],
                [dp:106, name:'illuminance',         type:'number',  rw: 'ro', min:0,   max:2000,  defVal:0,   scale:1,    unit:'lx',       title:'<b>illuminance</b>',                description:'<i>illuminance</i>'],
                [dp:107, name:'lightOnLuminance',    type:'number',  rw: 'rw', min:0,   max:2000,  defVal:0,   scale:1,    unit:'lx',       title:'<b>lightOnLuminance</b>',                description:'<i>lightOnLuminance</i>'],        // Ligter, Medium, ... ?// TODO =- check range 0 - 10000 ?
                [dp:108, name:'lightOffLuminance',   type:'number',  rw: 'rw', min:0,   max:2000,  defVal:0,   scale:1,    unit:'lx',       title:'<b>lightOffLuminance</b>',                description:'<i>lightOffLuminance</i>'],
                [dp:109, name:'luminanceLevel',      type:'number',  rw: 'ro', min:0,   max:2000,  defVal:0,   scale:1,    unit:'lx',       title:'<b>luminanceLevel</b>',                description:'<i>luminanceLevel</i>'],            // Ligter, Medium, ... ?
                [dp:110, name:'ledStatusAIR',        type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0', scale:1,    map:[0: 'Switch On', 1:'Switch Off', 2: 'Default'] ,   unit:'',     title:'<b>LED status</b>', description:'<i>Led status switch</i>'],
            ],
            deviceJoinName: 'Tuya PIR Human Motion Presence Sensor AIR',
            configuration : ['battery': false]
    ],

    'SONOFF_MOTION_IAS'   : [
            description   : 'Sonoff/eWeLink Motion sensor',
            models        : ['eWeLink'],
            device        : [type: 'PIR', isIAS:true, powerSource: 'battery', isSleepy:true],   // very sleepy !!
            capabilities  : ['MotionSensor': true, 'Battery': true],
            preferences   : ['motionReset':true, 'keepTime':false, 'sensitivity':false],   // just enable or disable showing the motionReset preference, no link to  tuyaDPs or attributes map!
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,0001', outClusters:'0003', model:'ms01', manufacturer:'eWeLink', deviceJoinName: 'eWeLink Motion Sensor'],        // for testL 60 seconds re-triggering period!
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,0001', outClusters:'0003', model:'msO1', manufacturer:'eWeLink', deviceJoinName: 'eWeLink Motion Sensor'],        // second variant
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,0001', outClusters:'0003', model:'MS01', manufacturer:'eWeLink', deviceJoinName: 'eWeLink Motion Sensor']        // third variant
            ],
            deviceJoinName: 'Sonoff/eWeLink Motion sensor',
            configuration : [
                '0x0001':[['bind':true],  ['reporting':'0x21, 0x20, 3600, 7200, 0x02']],    // TODO - use the reproting values
                '0x0500':[['bind':false], ['sensitivity':false], ['keepTime':false]],       // TODO - use in update function
            ]  // battery percentage, min 3600, max 7200, UINT8, delta 2
    ],

    // isSiHAS()
    'SIHAS_USM-300Z_4_IN_1' : [
            description   : 'SiHAS USM-300Z 4-in-1',
            models        : ['ShinaSystem'],
            device        : [type: 'radar', powerSource: 'battery', isIAS:false, isSleepy:false],
            capabilities  : ['MotionSensor': true, 'TemperatureMeasurement': true, 'RelativeHumidityMeasurement': true, 'IlluminanceMeasurement': true, 'Battery': true],
            preferences   : [:],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0400,0003,0406,0402,0001,0405,0500', outClusters:'0004,0003,0019', model:'USM-300Z', manufacturer:'ShinaSystem', deviceJoinName: 'SiHAS MultiPurpose Sensor']
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [:],
            attributes    : [:],
            deviceJoinName: 'SiHAS USM-300Z 4-in-1',
            //configuration : ["0x0406":"bind"]     // TODO !!
            configuration : [:]
    ],

    'NONTUYA_MOTION_IAS'   : [
            description   : 'Other OEM Motion sensors (IAS)',
            models        : ['MOT003', 'XXX'],
            device        : [type: 'PIR', isIAS:true, powerSource: 'battery', isSleepy:true],
            capabilities  : ['MotionSensor': true, 'Battery': true],
            preferences   : ['motionReset':true, 'keepTime':'0x0500:0xF001', 'sensitivity':'0x0500:0x0013'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,0400,0402,0500', outClusters:'0019', model:'MOT003', manufacturer:'HiveHome.com', deviceJoinName: 'Hive Motion Sensor']         // https://community.hubitat.com/t/hive-motion-sensors-can-we-get-custom-driver-sorted/108177?u=kkossev
            ],
            attributes:       [
                [at:'0x0500:0x0013', name:'sensitivity', type:'enum',   rw: 'rw', min:0, max:2,    defVal:'2',  unit:'',           map:[0:'low', 1:'medium', 2:'high'], title:'<b>Sensitivity</b>',   description:'<i>PIR sensor sensitivity (update at the time motion is activated)</i>'],
                [at:'0x0500:0xF001', name:'keepTime',    type:'enum',   rw: 'rw', min:0, max:2,    defVal:'0',  unit:'seconds',    map:[0:'30 seconds', 1:'60 seconds', 2:'120 seconds'], title:'<b>Keep Time</b>',   description:'<i>PIR keep time in seconds (update at the time motion is activated)</i>'],
            ],
            deviceJoinName: 'Other OEM Motion sensor (IAS)',
            configuration : ['battery': false]
    ],

    '---'   : [
            description   : '--------------------------------------',
            models        : [],
            fingerprints  : [],
    ],

// ------------------------------------------- mmWave Radars ------------------------------------------------//

    'TS0601_TUYA_RADAR'   : [        // isZY_M100Radar()        // spammy devices!
            description   : 'Tuya Human Presence mmWave Radar ZY-M100',
            models        : ['TS0601'],
            device        : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [
                [manufacturer:'_TZE200_ztc6ggyl'], [manufacturer:'_TZE204_ztc6ggyl'], [manufacturer:'_TZE200_ikvncluo'], [manufacturer:'_TZE200_lyetpprm'], [manufacturer:'_TZE200_wukb7rhc'],
                [manufacturer:'_TZE200_jva8ink8'], [manufacturer:'_TZE200_mrf6vtua'], [manufacturer:'_TZE200_ar0slwnd'], [manufacturer:'_TZE200_sfiy5tfs'], [manufacturer:'_TZE200_holel4dk'],
                [manufacturer:'_TZE200_xpq2rzhq'], [manufacturer:'_TZE204_qasjif9e'], [manufacturer:'_TZE204_xsm7l9xa']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', min:0,   max:1 ,    defVal:'0',  scale:1,    map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:9,   name:'distance',           type:'decimal', rw: 'ro', min:0.0, max:10.0 , defVal:0.0,  scale:100,  unit:'meters',   title:'<b>Distance</b>',                   description:'<i>detected distance</i>'],
                [dp:104, name:'illuminance',        type:'number',  rw: 'ro', min:0,   max:2000,  defVal:0,    scale:1,    unit:'lx',       title:'<b>illuminance</b>',                description:'<i>illuminance</i>'],

            ],
            spammyDPsToIgnore : [9], spammyDPsToNotTrace : [9, 103]
    ],

    'TS0601_KAPVNNLK_RADAR'   : [        // 24GHz spammy radar w/ battery backup - depricated
            description   : 'Tuya TS0601_KAPVNNLK 24GHz Radar',
            models        : ['TS0601'],
            device        : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZE204_kapvnnlk'], [manufacturer:'_TZE204_kyhbrfyl']],
            tuyaDPs:        [
                [dp:1, name:'motion', type:'enum', rw: 'ro', min:0, max:1, defVal:'0',  scale:1,   map:[0:'inactive', 1:'active'] , unit:'', title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:19,  name:'distance', type:'decimal', rw: 'ro', min:0.0, max:10.0,  defVal:0.0,  scale:100, unit:'meters', title:'<b>Distance</b>', description:'<i>detected distance</i>']

            ],
            spammyDPsToIgnore : [19], spammyDPsToNotTrace : [19]
    ],

    'TS0601_RADAR_MIR-HE200-TY'   : [
            description   : 'Tuya Human Presence Sensor MIR-HE200-TY',  // deprecated
            models        : ['TS0601'],
            device        : [isDepricated:true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true],
            fingerprints  : [[manufacturer:'_TZE200_vrfecyku'], [manufacturer:'_TZE200_lu01t0zl'], [manufacturer:'_TZE200_ypprdwsl']],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0', scale:1,    map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:102, name:'motionState',        type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0', scale:1,    map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Motion state</b>', description:'<i>Motion state (occupancy)</i>'],
            ]
    ],

    'TS0601_BLACK_SQUARE_RADAR'   : [
            description   : 'Tuya Black Square Radar',
            models        : ['TS0601'],
            device        : [isDepricated:true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor':true],
            fingerprints  : [[manufacturer:'_TZE200_0u3bj3rc'], [manufacturer:'_TZE200_v6ossqfy'], [manufacturer:'_TZE200_mx6u6l4y']],
            tuyaDPs:        [
                [dp:1,   name:'motion',         type:'enum',   rw: 'ro', min:0, max:1,    defVal: '0', map:[0:'inactive', 1:'active'],     description:'Presence'],
            ],
            spammyDPsToIgnore : [103], spammyDPsToNotTrace : [1, 101, 102, 103]
    ],

    'TS0601_YXZBRB58_RADAR'   : [
            description   : 'Tuya YXZBRB58 Radar',
            models        : ['TS0601'],
            device        : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy: false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZE204_sooucan5']],
            tuyaDPs:        [
                [dp:1,   name:'motion',                 type:'enum',    rw: 'ro', min:0,   max:2,     defVal:'0',  map:[0:'inactive', 1:'active'],  description:'Presence state'],
                [dp:101, name:'illuminance',            type:'number',  rw: 'ro',                     scale:1,    unit:'lx',       description:'Illuminance'],
                [dp:105, name:'distance',               type:'decimal', rw: 'ro', min:0.0, max:10.0,  scale:100,  unit:'meters',   description:'Distance']
            ],
            spammyDPsToIgnore : [105], spammyDPsToNotTrace : [105]
    ],

    'TS0601_SXM7L9XA_RADAR'   : [
            description   : 'Tuya Human Presence Detector SXM7L9XA',
            models        : ['TS0601'],
            device        : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [
                [manufacturer:'_TZE204_sxm7l9xa'], [manufacturer:'_TZE204_e5m9c5hl']
            ],
            tuyaDPs:        [
                [dp:104, name:'illuminance',            type:'number',  rw: 'ro',                     scale:1, unit:'lx',          description:'illuminance'],
                [dp:105, name:'motion',                 type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0', map:[0:'inactive', 1:'active'],  description:'Presence state'],
                [dp:109, name:'distance',               type:'decimal', rw: 'ro', min:0.0, max:10.0,  scale:100,  unit:'meters',    description:'Distance']
            ],
            spammyDPsToIgnore : [109], spammyDPsToNotTrace : [109]
    ],

    'TS0601_IJXVKHD0_RADAR'   : [
            description   : 'Tuya Human Presence Detector IJXVKHD0',
            models        : ['TS0601'],
            device        : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZE204_ijxvkhd0']],
            tuyaDPs:        [
                [dp:104, name:'illuminance',            type:'number',  rw: 'ro',                    scale:1, unit:'lx',                  description:'illuminance'],
                [dp:105, name:'humanMotionState',       type:'enum',    rw: 'ro', min:0,   max:2,    defVal:'0', map:[0:'none', 1:'present', 2:'moving'], description:'Presence state'],
                [dp:109, name:'distance',               type:'decimal', rw: 'ro', min:0.0, max:10.0, defVal:0.0, scale:100, unit:'meters',             description:'Target distance'],
                [dp:112, name:'motion',                 type:'enum',    rw: 'ro', min:0,   max:1,    defVal:'0',       scale:1,    map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:123, name:'presence',               type:'enum',    rw: 'ro', min:0,   max:1,    defVal:'0', map:[0:'none', 1:'presence'],            description:'Presence']    // TODO -- check if used?
            ],
            spammyDPsToIgnore : [109, 9], spammyDPsToNotTrace : [109, 104]
    ],

    'TS0601_YENSYA2C_RADAR'   : [
            description   : 'Tuya Human Presence Detector YENSYA2C',
            models        : ['TS0601'],
            device        : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy: false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZE204_yensya2c'], [manufacturer:'_TZE204_mhxn2jso']],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0', scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:19,  name:'distance',           type:'decimal', rw: 'ro', min:0.0, max:10.0,  defVal:0.0, scale:100, unit:'meters',  description:'Distance'],
                [dp:20,  name:'illuminance',        type:'number',  rw: 'ro', min:0,   max:10000, scale:1,   unit:'lx',        description:'illuminance']
            ],
            spammyDPsToIgnore : [19], spammyDPsToNotTrace : [19]
    ],

    'TS0225_HL0SS9OA_RADAR'   : [
            description   : 'Tuya TS0225_HL0SS9OA Radar',
            models        : ['TS0225'],
            device        : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true],
            fingerprints  : [[manufacturer:'_TZE200_hl0ss9oa']],
            tuyaDPs:        [
                [dp:1,   name:'motion',                          type:'enum',    rw: 'ro', min:0,    max:1,    defVal:'0',   scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:11,  name:'humanMotionState',                type:'enum',    rw: 'ro', min:0,    max:3,    defVal:'0',  map:[0:'none', 1:'large', 2:'small', 3:'static'],       description:'Human motion state'],
                [dp:20,  name:'illuminance',                     type:'number',  rw: 'ro', scale:10,  unit:'lx',        description:'Illuminance']
            ],
            spammyDPsToIgnore : [], spammyDPsToNotTrace : [11]
    ],

    'TS0225_2AAELWXK_RADAR'   : [
            description   : 'Tuya TS0225_2AAELWXK 5.8 GHz Radar',
            models        : ['TS0225'],
            device        : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true],
            fingerprints  : [[manufacturer:'_TZE200_2aaelwxk']],
            tuyaDPs:        [
                [dp:1,   name:'motion',                          type:'enum',    rw: 'ro', min:0,    max:1,    defVal:'0',   scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:101, name:'humanMotionState',                type:'enum',    rw: 'ro', min:0,    max:3,    defVal:'0',  map:[0:'none', 1:'large', 2:'small', 3:'static'],       description:'Human motion state'],
                [dp:106, name:'illuminance',                     type:'number',  rw: 'ro', scale:10,  unit:'lx',        description:'Illuminance']
            ]
    ],

    'TS0601_SBYX0LM6_RADAR'   : [
            description   : 'Tuya Human Presence Detector SBYX0LM6',
            models        : ['TS0601'],
            device        : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [
                [manufacturer:'_TZE204_sbyx0lm6'], [manufacturer:'_TZE200_sbyx0lm6'], [manufacturer:'_TZE204_dtzziy1e'], [manufacturer:'_TZE200_dtzziy1e'], [manufacturer:'_TZE204_clrdrnya'], [manufacturer:'_TZE200_clrdrnya'],
                [manufacturer:'_TZE204_cfcznfbz'], [manufacturer:'_TZE204_iaeejhvf'], [manufacturer:'_TZE204_mtoaryre'], [manufacturer:'_TZE204_8s6jtscb'], [manufacturer:'_TZE204_rktkuel1'], [manufacturer:'_TZE204_mp902om5'],
                [manufacturer:'_TZE200_w5y5slkq'], [manufacturer:'_TZE204_w5y5slkq'], [manufacturer:'_TZE200_xnaqu2pc'], [manufacturer:'_TZE204_xnaqu2pc'], [manufacturer:'_TZE200_wk7seszg'], [manufacturer:'_TZE204_wk7seszg'],
                [manufacturer:'_TZE200_0wfzahlw'], [manufacturer:'_TZE204_0wfzahlw'], [manufacturer:'_TZE200_pfayrzcw'], [manufacturer:'_TZE204_pfayrzcw'], [manufacturer:'_TZE200_z4tzr0rg'], [manufacturer:'_TZE204_z4tzr0rg']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0',   scale:1,    map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:9,   name:'distance',           type:'decimal', rw: 'ro', min:0.0, max:10.0,  defVal:0.0,   scale:100,  unit:'meters',   description:'<i>detected distance</i>'],
                [dp:104, name:'illuminance',        type:'number',  rw: 'ro', min:0,   max:2000,  defVal:0,     scale:10,   unit:'lx',       title:'<b>illuminance</b>',                description:'<i>illuminance</i>']
            ],
            spammyDPsToIgnore : [9], spammyDPsToNotTrace : [9]
    ],

    // isLINPTECHradar()
    'TS0225_LINPTECH_RADAR'   : [
            description   : 'Tuya TS0225_LINPTECH 24GHz Radar',
            models        : ['TS0225'],
            device        : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [
                [manufacturer:'_TZ3218_awarhusb']
            ],
            tuyaDPs:       [                                           // the tuyaDPs revealed from iot.tuya.com are actually not used by the device! The only exception is dp:101
                [dp:101,              name:'fadingTime',                      type:'number',                rw: 'rw', min:1,    max:9999, defVal:10,    scale:1,   unit:'seconds', title: '<b>Fading time</b>', description:'<i>Presence inactivity timer, seconds</i>']                                  // aka 'nobody time'
            ],
            deviceJoinName: 'Tuya TS0225_LINPTECH 24Ghz Human Presence Detector'
    ],

    'TS0225_EGNGMRZH_RADAR'   : [
            description   : 'Tuya TS0225_EGNGMRZH 24GHz Radar',
            models        : ['TS0225'],
            device        : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZFED8_egngmrzh']],
            // uses IAS for occupancy!
            tuyaDPs:        [
                [dp:101, name:'illuminance',        type:'number',  rw: 'ro', min:0,  max:10000, scale:1,   unit:'lx'],
                [dp:103, name:'distance',           type:'decimal', rw: 'ro', min:0.0,  max:10.0,  defVal:0.0, scale:10,  unit:'meters']
            ],
            spammyDPsToIgnore : [103], spammyDPsToNotTrace : [103]
    ],

    'TS0225_O7OE4N9A_RADAR'   : [ // Aubess Zigbee-Human Presence Detector, Smart PIR Human Body Sensor, Wifi Radar, Microwave Motion Sensors, Tuya, 1/24/5G
            description   : 'Tuya Human Presence Detector YENSYA2C',
            models        : ['TS0225'],
            device        : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZFED8_o7oe4n9a']],
            tuyaDPs:        [
                [dp:1,   name:'motion',                 type:'enum',    rw: 'ro', min:0,   max:1,     defVal:'0', scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',  title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:181, name:'illuminance',            type:'number',  rw: 'ro', min:0,   max:10000, scale:1,    unit:'lx', description:'illuminance'],
                [dp:182, name:'distance',               type:'decimal', rw: 'ro', min:0.0, max:10.0,  defVal:0.0, scale:100, unit:'meters',  description:'Distance to target']
            ]
            //spammyDPsToIgnore : [182], //spammyDPsToNotTrace : [182],
    ],

    'OWON_OCP305_RADAR'   : [   // depricated
            description   : 'OWON OCP305 Radar',
            models        : ['OCP305'],
            device        : [isDepricated:true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'Battery': true],
            fingerprints  : [
                [manufacturer:'OWON']   // depricated
            ]
    ],

    'SONOFF_SNZB-06P_RADAR' : [ // Depricated
            description   : 'SONOFF SNZB-06P RADAR',
            models        : ['SNZB-06P'],
            device        : [isDepricated:true, type: 'radar', powerSource: 'dc', isIAS:false, isSleepy:false],
            capabilities  : ['MotionSensor': true],
            fingerprints  : [
                [manufacturer:'SONOFF']      // Depricated!
            ]
    ],

    'UNKNOWN'             : [                        // the Device Profile key (shown in the State Variables)
            description   : 'Unknown device',        // the Device Profile description (shown in the Preferences)
            models        : ['UNKNOWN'],             // used to match a Device profile if the individuak fingerprints do not match
            device        : [
                type: 'PIR',         // 'PIR' or 'radar'
                isIAS:true,                          // define it for PIR sensors only!
                powerSource: 'dc',                   // determines the powerSource value - can be 'battery', 'dc', 'mains'
                isSleepy:false                       // determines the update and ping behaviour
            ],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'Battery': true],
            preferences   : ['motionReset':true],
            commands      : ['resetSettings':'resetSettings', 'resetStats':'resetStats', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences' \
            ],
            //fingerprints  : [
            //    [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0406", outClusters:"0003", model:"model", manufacturer:"manufacturer"]
            //],
            tuyaDPs:        [
                [
                    dp:1,
                    name:'motion',
                    type:'enum',
                    rw: 'ro',
                    min:0,
                    max:1,
                    map:[0:'inactive', 1:'active'],
                    description:'Motion state'
                ]
            ],
            deviceJoinName: 'Unknown device',        // used during the inital pairing, if no individual fingerprint deviceJoinName was found
            configuration : ['battery': true],
            batteries     : 'unknown'
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

// TODO - move to iasLib.groovy !!

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

// TODO - move to motionLib.groovy !

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
    if (unixTime) { return Math.round((now() - unixTime) / 1000) as int }
    return settings?.motionResetTimer ?: 0
}

void customParseOccupancyCluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseOccupancyCluster: zigbee received cluster 0x0406 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logWarn "customParseOccupancyCluster: received unknown 0x0406 attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

// called processFoundItem in the deviceProfileLib
void customProcessDeviceProfileEvent(final Map descMap, final String name, final valueScaled, final String unitText, final String descText) {
    logTrace "customProcessDeviceProfileEvent(${name}, ${valueScaled}) called"
    boolean doNotTrace = isSpammyDPsToNotTrace(descMap)
    Map eventMap = [name: name, value: valueScaled, unit: unitText, descriptionText: descText, type: 'physical', isStateChange: true]
    switch (name) {
        case 'motion' :
            handleMotion(value as boolean)  // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            break
        case 'temperature' :
            //temperatureEvent(value / getTemperatureDiv())
            handleTemperatureEvent(valueScaled as Float)
            break
        case 'humidity' :
            handleHumidityEvent(valueScaled)
            break
        case 'illuminance' :
        case 'illuminance_lux' :    // ignore the IAS Zone illuminance reports for HL0SS9OA and 2AAELWXK
            //log.trace "illuminance event received deviceProfile is ${getDeviceProfile()} value=${value} valueScaled=${valueScaled} valueCorrected=${valueCorrected}"
            handleIlluminanceEvent(valueScaled as int)  // check !!!!!!!!!!
            break
        case 'pushed' :     // used in 'TS0202_MOTION_SWITCH'
            logDebug "button event received value=${value} valueScaled=${valueScaled} valueCorrected=${valueCorrected}"
            buttonEvent(valueScaled)
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

// TODO - move to deviceProfileLib !!!!!!!!!!!!!!!!!!!
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
    if (is4in1()) {
        IAS_ATTRIBUTES.each { key, value ->
            cmds += zigbee.readAttribute(0x0500, key, [:], delay = 199)
        }        
        cmds += zigbee.command(0xEF00, 0x07, '00')    // Fantem Tuya Magic
    }
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
        if (this.respondsTo('getProfileKey') == false) {
            logWarn "getProfileKey() is not defined in the driver"
        }
        else {
            logDebug "current state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${getProfileKey(settings?.forcedProfile)}"
            if (getProfileKey(settings?.forcedProfile) != state.deviceProfile) {
                logInfo "changing the device profile from ${state.deviceProfile} to ${getProfileKey(settings?.forcedProfile)}"
                state.deviceProfile = getProfileKey(settings?.forcedProfile)
                initializeVars(fullInit = false)
                resetPreferencesToDefaults(debug = true)
                logInfo 'press F5 to refresh the page'
            }
        }
    }
    /* groovylint-disable-next-line EmptyElseBlock */
    else {
        logDebug "forcedProfile is not set"
    }

    if (DEVICE?.device?.isDepricated == true) {
        logWarn 'The use of this driver with this device is depricated. Please update to the new driver!'
        return
    }


    // Itterates through all settings
    cmds += updateAllPreferences()  // defined in deviceProfileLib
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
    if (fullInit == true || settings.motionReset == null) device.updateSetting('motionReset', false)
    if (fullInit == true || settings.motionResetTimer == null) device.updateSetting('motionResetTimer', 60)


}

void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents()"
    if (getDeviceProfile() == 'TS0601_BLACK_SQUARE_RADAR') {
        sendEvent(name: 'WARNING', value: 'EXTREMLY SPAMMY DEVICE!', descriptionText: 'This device bombards the hub every 4 seconds!')
    }
}
/*
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
*/
/*
void customParseZdoClusters(final Map descMap){
    if (descMap.clusterInt == 0x0013 && getDeviceProfile() == 'TS0601_BLACK_SQUARE_RADAR') {  // device announcement
        updateInidicatorLight()
    }
}
*/

void customParseTuyaCluster(final Map descMap) {
    standardParseTuyaCluster(descMap)  // process it first in commonLib, then come back ... :) 
}

void customParseIlluminanceCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    if (DEVICE?.device?.ignoreIAS == true) { 
        logDebug "customCustomParseIlluminanceCluster: ignoring IAS reporting device"
        return 
    }    // ignore IAS devices
    standardParseIlluminanceCluster(descMap)  // illuminance.lib
}

void customParseIASCluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseIASCluster: zigbee received cluster 0x0500 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logDebug "customParseIASCluster: received unknown 0x0500 attribute 0x${descMap.attrId} (value ${descMap.value})"
        standardParseIASCluster(descMap) 
    }
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

//@CompileStatic
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


// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NestedBlockDepth, NoDouble, NoFloat, NoWildcardImports, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.deviceProfileLib, line 1
library( // library marker kkossev.deviceProfileLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLib', namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy', documentationLink: '', // library marker kkossev.deviceProfileLib, line 4
    version: '3.1.3' // library marker kkossev.deviceProfileLib, line 5
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
 * ver. 3.2.1  2024-05-27 kkossev  - (dev. branch) Tuya Multi Sensor 4 In 1 (V3) driver allignment (customProcessDeviceProfileEvent) // library marker kkossev.deviceProfileLib, line 29
 * // library marker kkossev.deviceProfileLib, line 30
 *                                   TODO - add defaults for profileId:'0104', endpointId:'01', inClusters, outClusters, in the deviceProfilesV3 map // library marker kkossev.deviceProfileLib, line 31
 *                                   TODO - updateStateUnknownDPs !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 32
 *                                   TODO - check why the forcedProfile preference is not initialized? // library marker kkossev.deviceProfileLib, line 33
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 34
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 35
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 36
 * // library marker kkossev.deviceProfileLib, line 37
*/ // library marker kkossev.deviceProfileLib, line 38

static String deviceProfileLibVersion()   { '3.2.1' } // library marker kkossev.deviceProfileLib, line 40
static String deviceProfileLibStamp() { '2024/05/27 10:13 PM' } // library marker kkossev.deviceProfileLib, line 41
import groovy.json.* // library marker kkossev.deviceProfileLib, line 42
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 43
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 44
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 45
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 46

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 48

metadata { // library marker kkossev.deviceProfileLib, line 50
    // no capabilities // library marker kkossev.deviceProfileLib, line 51
    // no attributes // library marker kkossev.deviceProfileLib, line 52
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 53
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 54
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 55
    ] // library marker kkossev.deviceProfileLib, line 56
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 57
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 58
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 59
    ] // library marker kkossev.deviceProfileLib, line 60

    preferences { // library marker kkossev.deviceProfileLib, line 62
        if (device) { // library marker kkossev.deviceProfileLib, line 63
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 64
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 65
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 66
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 67
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 68
                        input inputMap // library marker kkossev.deviceProfileLib, line 69
                    } // library marker kkossev.deviceProfileLib, line 70
                } // library marker kkossev.deviceProfileLib, line 71
            } // library marker kkossev.deviceProfileLib, line 72
            if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 73
                input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: '<i>Forcely change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!</i>',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 74
            } // library marker kkossev.deviceProfileLib, line 75
        } // library marker kkossev.deviceProfileLib, line 76
    } // library marker kkossev.deviceProfileLib, line 77
} // library marker kkossev.deviceProfileLib, line 78

boolean is2in1() { return getDeviceProfile().contains('TS0601_2IN1') } // library marker kkossev.deviceProfileLib, line 80

String  getDeviceProfile()       { state.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 82
Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2[getDeviceProfile()] } // library marker kkossev.deviceProfileLib, line 83
Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2?.keySet() } // library marker kkossev.deviceProfileLib, line 84
//List<String> getDeviceProfilesMap()   { deviceProfilesV3 != null ? deviceProfilesV3.values().description as List<String> : deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 85
List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLib, line 86
    if (deviceProfilesV3 == null) { // library marker kkossev.deviceProfileLib, line 87
        if (deviceProfilesV2 == null) { return [] } // library marker kkossev.deviceProfileLib, line 88
        return deviceProfilesV2.values().description as List<String> // library marker kkossev.deviceProfileLib, line 89
    } // library marker kkossev.deviceProfileLib, line 90
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLib, line 91
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 92
        if (profileMap.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 93
            activeProfiles.add(profileName) // library marker kkossev.deviceProfileLib, line 94
        } // library marker kkossev.deviceProfileLib, line 95
    } // library marker kkossev.deviceProfileLib, line 96
    return activeProfiles // library marker kkossev.deviceProfileLib, line 97
} // library marker kkossev.deviceProfileLib, line 98

// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 100

/** // library marker kkossev.deviceProfileLib, line 102
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 103
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 104
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 105
 */ // library marker kkossev.deviceProfileLib, line 106
String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 107
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 108
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 109
    else { return null } // library marker kkossev.deviceProfileLib, line 110
} // library marker kkossev.deviceProfileLib, line 111

/** // library marker kkossev.deviceProfileLib, line 113
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 114
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 115
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 116
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 117
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 118
 */ // library marker kkossev.deviceProfileLib, line 119
Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 120
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 121
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 122
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 123
    def preference // library marker kkossev.deviceProfileLib, line 124
    try { // library marker kkossev.deviceProfileLib, line 125
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 126
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 127
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 128
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 129
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 130
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 131
        } // library marker kkossev.deviceProfileLib, line 132
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 133
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 134
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 135
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 136
        } // library marker kkossev.deviceProfileLib, line 137
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 138
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 139
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 140
        } // library marker kkossev.deviceProfileLib, line 141
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 142
    } catch (e) { // library marker kkossev.deviceProfileLib, line 143
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 144
        return [:] // library marker kkossev.deviceProfileLib, line 145
    } // library marker kkossev.deviceProfileLib, line 146
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 147
    return foundMap // library marker kkossev.deviceProfileLib, line 148
} // library marker kkossev.deviceProfileLib, line 149

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 151
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 152
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 153
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 154
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 155
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 156
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 157
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 158
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 159
            return foundMap // library marker kkossev.deviceProfileLib, line 160
        } // library marker kkossev.deviceProfileLib, line 161
    } // library marker kkossev.deviceProfileLib, line 162
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 163
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 164
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 165
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 166
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 167
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 168
            return foundMap // library marker kkossev.deviceProfileLib, line 169
        } // library marker kkossev.deviceProfileLib, line 170
    } // library marker kkossev.deviceProfileLib, line 171
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 172
    return [:] // library marker kkossev.deviceProfileLib, line 173
} // library marker kkossev.deviceProfileLib, line 174

/** // library marker kkossev.deviceProfileLib, line 176
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 177
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 178
 */ // library marker kkossev.deviceProfileLib, line 179
void resetPreferencesToDefaults(boolean debug=true) { // library marker kkossev.deviceProfileLib, line 180
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 181
    Map preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 182
    if (preferences == null || preferences.isEmpty()) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 183
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 184
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 185
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 186
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 187
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 188
            return // continue // library marker kkossev.deviceProfileLib, line 189
        } // library marker kkossev.deviceProfileLib, line 190
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 191
        if (parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 192
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'<i>Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 193
        if (parMap.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 194
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 195
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 196
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 197
    } // library marker kkossev.deviceProfileLib, line 198
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 199
} // library marker kkossev.deviceProfileLib, line 200

/** // library marker kkossev.deviceProfileLib, line 202
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 203
 * // library marker kkossev.deviceProfileLib, line 204
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 205
 */ // library marker kkossev.deviceProfileLib, line 206
List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 207
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 208
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 209
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 210
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 211
    } // library marker kkossev.deviceProfileLib, line 212
    return validPars // library marker kkossev.deviceProfileLib, line 213
} // library marker kkossev.deviceProfileLib, line 214

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 216
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 217
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 218
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 219
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 220
    def scaledValue // library marker kkossev.deviceProfileLib, line 221
    if (value == null) { // library marker kkossev.deviceProfileLib, line 222
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 223
        return null // library marker kkossev.deviceProfileLib, line 224
    } // library marker kkossev.deviceProfileLib, line 225
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 226
        case 'number' : // library marker kkossev.deviceProfileLib, line 227
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 228
            break // library marker kkossev.deviceProfileLib, line 229
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 230
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 231
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 232
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 233
            } // library marker kkossev.deviceProfileLib, line 234
            break // library marker kkossev.deviceProfileLib, line 235
        case 'bool' : // library marker kkossev.deviceProfileLib, line 236
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 237
            break // library marker kkossev.deviceProfileLib, line 238
        case 'enum' : // library marker kkossev.deviceProfileLib, line 239
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 240
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 241
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 242
                return null // library marker kkossev.deviceProfileLib, line 243
            } // library marker kkossev.deviceProfileLib, line 244
            scaledValue = value // library marker kkossev.deviceProfileLib, line 245
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 246
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 247
            } // library marker kkossev.deviceProfileLib, line 248
            break // library marker kkossev.deviceProfileLib, line 249
        default : // library marker kkossev.deviceProfileLib, line 250
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 251
            return null // library marker kkossev.deviceProfileLib, line 252
    } // library marker kkossev.deviceProfileLib, line 253
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 254
    return scaledValue // library marker kkossev.deviceProfileLib, line 255
} // library marker kkossev.deviceProfileLib, line 256

// called from updated() method // library marker kkossev.deviceProfileLib, line 258
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 259
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 260
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 261
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 262
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 263
        return // library marker kkossev.deviceProfileLib, line 264
    } // library marker kkossev.deviceProfileLib, line 265
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 266
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 267
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 268
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 269
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 270
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 271
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 272
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 273
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 274
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 275
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 276
                // scale the value // library marker kkossev.deviceProfileLib, line 277
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 278
            } // library marker kkossev.deviceProfileLib, line 279
            if (preferenceValue != null) { setPar(name, preferenceValue.toString()) } // library marker kkossev.deviceProfileLib, line 280
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLib, line 281
        } // library marker kkossev.deviceProfileLib, line 282
        else { logDebug "warning: couldn't find map for preference ${name}" ; return } // library marker kkossev.deviceProfileLib, line 283
    } // library marker kkossev.deviceProfileLib, line 284
    return // library marker kkossev.deviceProfileLib, line 285
} // library marker kkossev.deviceProfileLib, line 286

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 288
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 289
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 290
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 291
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 292
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 293
} // library marker kkossev.deviceProfileLib, line 294
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 295
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 296
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 297
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 298
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 299
} // library marker kkossev.deviceProfileLib, line 300

List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 302
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 303
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 304
    Map map = [:] // library marker kkossev.deviceProfileLib, line 305
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 306
    try { // library marker kkossev.deviceProfileLib, line 307
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 308
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 309
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 310
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 311
    } // library marker kkossev.deviceProfileLib, line 312
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLib, line 313
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 314
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 315
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.UINT8 // library marker kkossev.deviceProfileLib, line 316
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 317
    } // library marker kkossev.deviceProfileLib, line 318
    if (map.mfgCode != null && map.mfgCode != '') { // library marker kkossev.deviceProfileLib, line 319
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, map.mfgCode, delay = 200) // library marker kkossev.deviceProfileLib, line 320
    } // library marker kkossev.deviceProfileLib, line 321
    else { // library marker kkossev.deviceProfileLib, line 322
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 323
    } // library marker kkossev.deviceProfileLib, line 324
    return cmds // library marker kkossev.deviceProfileLib, line 325
} // library marker kkossev.deviceProfileLib, line 326

/** // library marker kkossev.deviceProfileLib, line 328
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 329
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 330
 * // library marker kkossev.deviceProfileLib, line 331
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 332
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 333
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 334
 */ // library marker kkossev.deviceProfileLib, line 335
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 336
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 337
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 338
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 339
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 340
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 341
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 342
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 343
        case 'number' : // library marker kkossev.deviceProfileLib, line 344
            value = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 345
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 346
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 347
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 348
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 349
            } // library marker kkossev.deviceProfileLib, line 350
            else { // library marker kkossev.deviceProfileLib, line 351
                scaledValue = value // library marker kkossev.deviceProfileLib, line 352
            } // library marker kkossev.deviceProfileLib, line 353
            break // library marker kkossev.deviceProfileLib, line 354

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 356
            value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 357
            // scale the value // library marker kkossev.deviceProfileLib, line 358
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 359
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 360
            } // library marker kkossev.deviceProfileLib, line 361
            else { // library marker kkossev.deviceProfileLib, line 362
                scaledValue = value // library marker kkossev.deviceProfileLib, line 363
            } // library marker kkossev.deviceProfileLib, line 364
            break // library marker kkossev.deviceProfileLib, line 365

        case 'bool' : // library marker kkossev.deviceProfileLib, line 367
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 368
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 369
            else { // library marker kkossev.deviceProfileLib, line 370
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 371
                return null // library marker kkossev.deviceProfileLib, line 372
            } // library marker kkossev.deviceProfileLib, line 373
            break // library marker kkossev.deviceProfileLib, line 374
        case 'enum' : // library marker kkossev.deviceProfileLib, line 375
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 376
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 377
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 378
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 379
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 380
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 381
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 382
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 383
            } // library marker kkossev.deviceProfileLib, line 384
            else { // library marker kkossev.deviceProfileLib, line 385
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 386
            } // library marker kkossev.deviceProfileLib, line 387
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 388
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 389
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 390
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 391
                // find the key for the value // library marker kkossev.deviceProfileLib, line 392
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 393
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 394
                if (key == null) { // library marker kkossev.deviceProfileLib, line 395
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 396
                    return null // library marker kkossev.deviceProfileLib, line 397
                } // library marker kkossev.deviceProfileLib, line 398
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 399
            //return null // library marker kkossev.deviceProfileLib, line 400
            } // library marker kkossev.deviceProfileLib, line 401
            break // library marker kkossev.deviceProfileLib, line 402
        default : // library marker kkossev.deviceProfileLib, line 403
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 404
            return null // library marker kkossev.deviceProfileLib, line 405
    } // library marker kkossev.deviceProfileLib, line 406
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 407
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 408
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 409
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 410
        return null // library marker kkossev.deviceProfileLib, line 411
    } // library marker kkossev.deviceProfileLib, line 412
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 413
    return scaledValue // library marker kkossev.deviceProfileLib, line 414
} // library marker kkossev.deviceProfileLib, line 415

/** // library marker kkossev.deviceProfileLib, line 417
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 418
 * // library marker kkossev.deviceProfileLib, line 419
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 420
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 421
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 422
 */ // library marker kkossev.deviceProfileLib, line 423
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 424
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 425
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 426
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 427
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 428
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 429
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 430
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 431
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 432
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 433
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 434
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 435
    if (scaledValue == null) { logInfo "setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 436

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 438
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 439
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 440
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 441
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 442
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 443
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 444
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 445
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 446
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 447
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 448
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 449
            return true // library marker kkossev.deviceProfileLib, line 450
        } // library marker kkossev.deviceProfileLib, line 451
        else { // library marker kkossev.deviceProfileLib, line 452
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 453
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 454
        } // library marker kkossev.deviceProfileLib, line 455
    } // library marker kkossev.deviceProfileLib, line 456
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 457
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 458
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 459
        def valMiscType // library marker kkossev.deviceProfileLib, line 460
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 461
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 462
            // find the key for the value // library marker kkossev.deviceProfileLib, line 463
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 464
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 465
            if (key == null) { // library marker kkossev.deviceProfileLib, line 466
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 467
                return false // library marker kkossev.deviceProfileLib, line 468
            } // library marker kkossev.deviceProfileLib, line 469
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 470
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 471
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 472
        } // library marker kkossev.deviceProfileLib, line 473
        else { // library marker kkossev.deviceProfileLib, line 474
            valMiscType = val // library marker kkossev.deviceProfileLib, line 475
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 476
        } // library marker kkossev.deviceProfileLib, line 477
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 478
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 479
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 480
        return true // library marker kkossev.deviceProfileLib, line 481
    } // library marker kkossev.deviceProfileLib, line 482

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 484
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 485

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 487
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 488
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 489
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 490
        // Tuya DP // library marker kkossev.deviceProfileLib, line 491
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 492
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 493
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 494
            return false // library marker kkossev.deviceProfileLib, line 495
        } // library marker kkossev.deviceProfileLib, line 496
        else { // library marker kkossev.deviceProfileLib, line 497
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 498
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 499
            return false // library marker kkossev.deviceProfileLib, line 500
        } // library marker kkossev.deviceProfileLib, line 501
    } // library marker kkossev.deviceProfileLib, line 502
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 503
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 504
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mapMfCode=${dpMap.mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 505
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 506
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 507
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 508
            return false // library marker kkossev.deviceProfileLib, line 509
        } // library marker kkossev.deviceProfileLib, line 510
    } // library marker kkossev.deviceProfileLib, line 511
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 512
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 513
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 514
    return true // library marker kkossev.deviceProfileLib, line 515
} // library marker kkossev.deviceProfileLib, line 516

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 518
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 519
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 520
List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 521
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 522
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 523
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 524
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 525
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 526
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 527
        return [] // library marker kkossev.deviceProfileLib, line 528
    } // library marker kkossev.deviceProfileLib, line 529
    String dpType // library marker kkossev.deviceProfileLib, line 530
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 531
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 532
    } // library marker kkossev.deviceProfileLib, line 533
    else { // library marker kkossev.deviceProfileLib, line 534
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 535
    } // library marker kkossev.deviceProfileLib, line 536
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 537
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 538
        return [] // library marker kkossev.deviceProfileLib, line 539
    } // library marker kkossev.deviceProfileLib, line 540
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 541
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 542
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 543
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 544
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 545
    } // library marker kkossev.deviceProfileLib, line 546
    else { // library marker kkossev.deviceProfileLib, line 547
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 548
    } // library marker kkossev.deviceProfileLib, line 549
    return cmds // library marker kkossev.deviceProfileLib, line 550
} // library marker kkossev.deviceProfileLib, line 551

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 553
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 554
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 555
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 556
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 557
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 558

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 560
    if (dpMap == null || dpMap.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 561
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 562
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 563
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 564
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 565
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 566
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 567
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 568
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 569
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 570
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 571
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 572
        try { // library marker kkossev.deviceProfileLib, line 573
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 574
        } // library marker kkossev.deviceProfileLib, line 575
        catch (e) { // library marker kkossev.deviceProfileLib, line 576
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 577
            return false // library marker kkossev.deviceProfileLib, line 578
        } // library marker kkossev.deviceProfileLib, line 579
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 580
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 581
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 582
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 583
            return true // library marker kkossev.deviceProfileLib, line 584
        } // library marker kkossev.deviceProfileLib, line 585
        else { // library marker kkossev.deviceProfileLib, line 586
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 587
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 588
        } // library marker kkossev.deviceProfileLib, line 589
    } // library marker kkossev.deviceProfileLib, line 590
    else { // library marker kkossev.deviceProfileLib, line 591
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 592
    } // library marker kkossev.deviceProfileLib, line 593
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 594
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 595
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 596
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 597
        // patch !! // library marker kkossev.deviceProfileLib, line 598
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 599
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 600
        } // library marker kkossev.deviceProfileLib, line 601
        else { // library marker kkossev.deviceProfileLib, line 602
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 603
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 604
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 605
        } // library marker kkossev.deviceProfileLib, line 606
        return true // library marker kkossev.deviceProfileLib, line 607
    } // library marker kkossev.deviceProfileLib, line 608
    else { // library marker kkossev.deviceProfileLib, line 609
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 610
    } // library marker kkossev.deviceProfileLib, line 611
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 612
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 613
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 614
    try { // library marker kkossev.deviceProfileLib, line 615
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 616
    } // library marker kkossev.deviceProfileLib, line 617
    catch (e) { // library marker kkossev.deviceProfileLib, line 618
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 619
        return false // library marker kkossev.deviceProfileLib, line 620
    } // library marker kkossev.deviceProfileLib, line 621
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 622
        // Tuya DP // library marker kkossev.deviceProfileLib, line 623
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 624
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 625
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 626
            return false // library marker kkossev.deviceProfileLib, line 627
        } // library marker kkossev.deviceProfileLib, line 628
        else { // library marker kkossev.deviceProfileLib, line 629
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 630
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 631
            return true // library marker kkossev.deviceProfileLib, line 632
        } // library marker kkossev.deviceProfileLib, line 633
    } // library marker kkossev.deviceProfileLib, line 634
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 635
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 636
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 637
    } // library marker kkossev.deviceProfileLib, line 638
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 639
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 640
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 641
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 642
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 643
            return false // library marker kkossev.deviceProfileLib, line 644
        } // library marker kkossev.deviceProfileLib, line 645
    } // library marker kkossev.deviceProfileLib, line 646
    else { // library marker kkossev.deviceProfileLib, line 647
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 648
        return false // library marker kkossev.deviceProfileLib, line 649
    } // library marker kkossev.deviceProfileLib, line 650
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 651
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 652
    return true // library marker kkossev.deviceProfileLib, line 653
} // library marker kkossev.deviceProfileLib, line 654

/** // library marker kkossev.deviceProfileLib, line 656
 * Sends a command to the device. // library marker kkossev.deviceProfileLib, line 657
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 658
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 659
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 660
 */ // library marker kkossev.deviceProfileLib, line 661
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 662
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 663
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 664
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 665
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 666
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 667
    if (supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 668
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 669
        return false // library marker kkossev.deviceProfileLib, line 670
    } // library marker kkossev.deviceProfileLib, line 671
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 672
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 673
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 674
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 675
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 676
        return false // library marker kkossev.deviceProfileLib, line 677
    } // library marker kkossev.deviceProfileLib, line 678
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 679
    def func, funcResult // library marker kkossev.deviceProfileLib, line 680
    try { // library marker kkossev.deviceProfileLib, line 681
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 682
        if (val != null) { // library marker kkossev.deviceProfileLib, line 683
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 684
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 685
        } // library marker kkossev.deviceProfileLib, line 686
        else { // library marker kkossev.deviceProfileLib, line 687
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 688
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 689
        } // library marker kkossev.deviceProfileLib, line 690
    } // library marker kkossev.deviceProfileLib, line 691
    catch (e) { // library marker kkossev.deviceProfileLib, line 692
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 693
        return false // library marker kkossev.deviceProfileLib, line 694
    } // library marker kkossev.deviceProfileLib, line 695
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 696
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 697
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 698
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 699
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 700
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 701
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 702
        } // library marker kkossev.deviceProfileLib, line 703
    } else { // library marker kkossev.deviceProfileLib, line 704
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 705
        return false // library marker kkossev.deviceProfileLib, line 706
    } // library marker kkossev.deviceProfileLib, line 707
    cmds = funcResult // library marker kkossev.deviceProfileLib, line 708
    if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 709
        sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 710
    } // library marker kkossev.deviceProfileLib, line 711
    return true // library marker kkossev.deviceProfileLib, line 712
} // library marker kkossev.deviceProfileLib, line 713

/** // library marker kkossev.deviceProfileLib, line 715
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 716
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 717
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 718
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 719
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 720
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 721
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 722
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 723
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 724
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 725
 */ // library marker kkossev.deviceProfileLib, line 726
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 727
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 728
    Map input = [:] // library marker kkossev.deviceProfileLib, line 729
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 730
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 731
        if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 732
        return [:] // library marker kkossev.deviceProfileLib, line 733
    } // library marker kkossev.deviceProfileLib, line 734
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 735
    def preference // library marker kkossev.deviceProfileLib, line 736
    try { // library marker kkossev.deviceProfileLib, line 737
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 738
    } // library marker kkossev.deviceProfileLib, line 739
    catch (e) { // library marker kkossev.deviceProfileLib, line 740
        if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 741
        return [:] // library marker kkossev.deviceProfileLib, line 742
    } // library marker kkossev.deviceProfileLib, line 743
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 744
    try { // library marker kkossev.deviceProfileLib, line 745
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 746
            if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } // library marker kkossev.deviceProfileLib, line 747
            return [:] // library marker kkossev.deviceProfileLib, line 748
        } // library marker kkossev.deviceProfileLib, line 749
    } // library marker kkossev.deviceProfileLib, line 750
    catch (e) { // library marker kkossev.deviceProfileLib, line 751
        if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 752
        return [:] // library marker kkossev.deviceProfileLib, line 753
    } // library marker kkossev.deviceProfileLib, line 754

    try { // library marker kkossev.deviceProfileLib, line 756
        isTuyaDP = preference.isNumber() // library marker kkossev.deviceProfileLib, line 757
    } // library marker kkossev.deviceProfileLib, line 758
    catch (e) { // library marker kkossev.deviceProfileLib, line 759
        if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 760
        return [:] // library marker kkossev.deviceProfileLib, line 761
    } // library marker kkossev.deviceProfileLib, line 762

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 764
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 765
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 766
    if (foundMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 767
        if (debug) { log.warn "inputIt: map not found for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 768
        return [:] // library marker kkossev.deviceProfileLib, line 769
    } // library marker kkossev.deviceProfileLib, line 770
    if (foundMap.rw != 'rw') { // library marker kkossev.deviceProfileLib, line 771
        if (debug) { log.warn "inputIt: param '${param}' is read only!" } // library marker kkossev.deviceProfileLib, line 772
        return [:] // library marker kkossev.deviceProfileLib, line 773
    } // library marker kkossev.deviceProfileLib, line 774
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 775
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 776
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 777
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 778
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 779
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 780
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 781
        } // library marker kkossev.deviceProfileLib, line 782
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 783
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 784
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 785
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 786
            } // library marker kkossev.deviceProfileLib, line 787
        } // library marker kkossev.deviceProfileLib, line 788
    } // library marker kkossev.deviceProfileLib, line 789
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 790
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 791
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 792
    }/* // library marker kkossev.deviceProfileLib, line 793
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 794
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 795
    }*/ // library marker kkossev.deviceProfileLib, line 796
    else { // library marker kkossev.deviceProfileLib, line 797
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 798
        return [:] // library marker kkossev.deviceProfileLib, line 799
    } // library marker kkossev.deviceProfileLib, line 800
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 801
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 802
    } // library marker kkossev.deviceProfileLib, line 803
    return input // library marker kkossev.deviceProfileLib, line 804
} // library marker kkossev.deviceProfileLib, line 805

/** // library marker kkossev.deviceProfileLib, line 807
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 808
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 809
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 810
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 811
 */ // library marker kkossev.deviceProfileLib, line 812
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 813
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 814
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 815
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 816
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 817
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 818
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 819
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 820
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 821
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 822
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 823
            } // library marker kkossev.deviceProfileLib, line 824
        } // library marker kkossev.deviceProfileLib, line 825
    } // library marker kkossev.deviceProfileLib, line 826
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 827
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 828
    } // library marker kkossev.deviceProfileLib, line 829
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 830
} // library marker kkossev.deviceProfileLib, line 831

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 833
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 834
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 835
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 836
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 837
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 838
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 839
    } // library marker kkossev.deviceProfileLib, line 840
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 841
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 842
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 843
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 844
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 845
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 846
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 847
    } else { // library marker kkossev.deviceProfileLib, line 848
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 849
    } // library marker kkossev.deviceProfileLib, line 850
} // library marker kkossev.deviceProfileLib, line 851

// TODO! // library marker kkossev.deviceProfileLib, line 853
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 854
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 855
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 856
    logDebug "refreshDeviceProfile() : ${cmds} (TODO!)" // library marker kkossev.deviceProfileLib, line 857
    return cmds // library marker kkossev.deviceProfileLib, line 858
} // library marker kkossev.deviceProfileLib, line 859

// TODO ! // library marker kkossev.deviceProfileLib, line 861
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 862
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 863
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 864
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 865
    return cmds // library marker kkossev.deviceProfileLib, line 866
} // library marker kkossev.deviceProfileLib, line 867

// TODO // library marker kkossev.deviceProfileLib, line 869
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 870
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 871
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 872
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 873
    return cmds // library marker kkossev.deviceProfileLib, line 874
} // library marker kkossev.deviceProfileLib, line 875

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 877
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 878
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 879
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 880
    } // library marker kkossev.deviceProfileLib, line 881
} // library marker kkossev.deviceProfileLib, line 882

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 884
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 885
} // library marker kkossev.deviceProfileLib, line 886

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 888

// // library marker kkossev.deviceProfileLib, line 890
// called from parse() // library marker kkossev.deviceProfileLib, line 891
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 892
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 893
// // library marker kkossev.deviceProfileLib, line 894
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 895
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 896
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 897
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 898
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 899
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 900
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 901
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 902
} // library marker kkossev.deviceProfileLib, line 903

// // library marker kkossev.deviceProfileLib, line 905
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 906
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 907
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 908
// // library marker kkossev.deviceProfileLib, line 909
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 910
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 911
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 912
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 913
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 914
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 915
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 916
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 917
} // library marker kkossev.deviceProfileLib, line 918

// all DPs are spammy - sent periodically! // library marker kkossev.deviceProfileLib, line 920
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 921
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 922
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 923
    return isSpammy // library marker kkossev.deviceProfileLib, line 924
} // library marker kkossev.deviceProfileLib, line 925

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 927
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 928
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 929
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 930
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 931
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 932
    } // library marker kkossev.deviceProfileLib, line 933
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 934
} // library marker kkossev.deviceProfileLib, line 935

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 937
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 938
    boolean isEqual // library marker kkossev.deviceProfileLib, line 939
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 940
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 941
    } // library marker kkossev.deviceProfileLib, line 942
    else { // library marker kkossev.deviceProfileLib, line 943
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 944
    } // library marker kkossev.deviceProfileLib, line 945
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 946
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 947
} // library marker kkossev.deviceProfileLib, line 948

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 950
    Double convertedValue // library marker kkossev.deviceProfileLib, line 951
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 952
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 953
    } // library marker kkossev.deviceProfileLib, line 954
    else { // library marker kkossev.deviceProfileLib, line 955
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 956
    } // library marker kkossev.deviceProfileLib, line 957
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 958
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 959
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 960
} // library marker kkossev.deviceProfileLib, line 961

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 963
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 964
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 965
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 966
    def convertedValue // library marker kkossev.deviceProfileLib, line 967
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 968
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 969
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 970
    } // library marker kkossev.deviceProfileLib, line 971
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 972
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 973
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 974
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 975
            isEqual = false // library marker kkossev.deviceProfileLib, line 976
        } // library marker kkossev.deviceProfileLib, line 977
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 978
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 979
        } // library marker kkossev.deviceProfileLib, line 980
    } // library marker kkossev.deviceProfileLib, line 981
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 982
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 983
} // library marker kkossev.deviceProfileLib, line 984

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 986
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 987
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 988
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 989
    boolean isEqual // library marker kkossev.deviceProfileLib, line 990
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 991
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 992
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 993
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 994
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 995
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 996
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 997
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 998
            break // library marker kkossev.deviceProfileLib, line 999
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1000
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1001
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1002
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1003
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1004
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1005
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1006
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1007
            } // library marker kkossev.deviceProfileLib, line 1008
            else { // library marker kkossev.deviceProfileLib, line 1009
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1010
            } // library marker kkossev.deviceProfileLib, line 1011
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1012
            break // library marker kkossev.deviceProfileLib, line 1013
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1014
        case 'number' : // library marker kkossev.deviceProfileLib, line 1015
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1016
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1017
            break // library marker kkossev.deviceProfileLib, line 1018
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1019
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1020
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1021
            break // library marker kkossev.deviceProfileLib, line 1022
        default : // library marker kkossev.deviceProfileLib, line 1023
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1024
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1025
    } // library marker kkossev.deviceProfileLib, line 1026
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1027
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1028
    } // library marker kkossev.deviceProfileLib, line 1029
    // // library marker kkossev.deviceProfileLib, line 1030
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1031
} // library marker kkossev.deviceProfileLib, line 1032

// // library marker kkossev.deviceProfileLib, line 1034
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1035
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1036
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1037
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1038
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1039
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1040
// // library marker kkossev.deviceProfileLib, line 1041
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1042
// // library marker kkossev.deviceProfileLib, line 1043
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1044
// // library marker kkossev.deviceProfileLib, line 1045
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1046
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1047
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1048
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1049
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1050
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1051
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1052
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1053
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1054
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1055
            break // library marker kkossev.deviceProfileLib, line 1056
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1057
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1058
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1059
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1060
            logTrace "latestEvent is dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1061
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1062
            if (dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1063
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1064
            } // library marker kkossev.deviceProfileLib, line 1065
            else { // library marker kkossev.deviceProfileLib, line 1066
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1067
            } // library marker kkossev.deviceProfileLib, line 1068
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1069
            break // library marker kkossev.deviceProfileLib, line 1070
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1071
        case 'number' : // library marker kkossev.deviceProfileLib, line 1072
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1073
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1074
            break // library marker kkossev.deviceProfileLib, line 1075
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1076
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1077
            break // library marker kkossev.deviceProfileLib, line 1078
        default : // library marker kkossev.deviceProfileLib, line 1079
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1080
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1081
    } // library marker kkossev.deviceProfileLib, line 1082
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1083
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1084
} // library marker kkossev.deviceProfileLib, line 1085

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1087
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1088
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1089
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1090
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1091
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1092
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1093
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1094
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1095
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1096
    } // library marker kkossev.deviceProfileLib, line 1097
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1098
    try { // library marker kkossev.deviceProfileLib, line 1099
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1100
    } // library marker kkossev.deviceProfileLib, line 1101
    catch (e) { // library marker kkossev.deviceProfileLib, line 1102
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1103
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1104
    } // library marker kkossev.deviceProfileLib, line 1105
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1106
    return fncmd // library marker kkossev.deviceProfileLib, line 1107
} // library marker kkossev.deviceProfileLib, line 1108

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1110
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1111
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1112
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1113
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1114
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1115
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1116

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1118
    if (attribMap == null || attribMap.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1119

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1121
    int value // library marker kkossev.deviceProfileLib, line 1122
    try { // library marker kkossev.deviceProfileLib, line 1123
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1124
    } // library marker kkossev.deviceProfileLib, line 1125
    catch (e) { // library marker kkossev.deviceProfileLib, line 1126
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1127
        return false // library marker kkossev.deviceProfileLib, line 1128
    } // library marker kkossev.deviceProfileLib, line 1129
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1130
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1131
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1132
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1133
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1134
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1135
        return false // library marker kkossev.deviceProfileLib, line 1136
    } // library marker kkossev.deviceProfileLib, line 1137
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1138
} // library marker kkossev.deviceProfileLib, line 1139

/** // library marker kkossev.deviceProfileLib, line 1141
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1142
 * // library marker kkossev.deviceProfileLib, line 1143
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1144
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1145
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1146
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1147
 * // library marker kkossev.deviceProfileLib, line 1148
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1149
 */ // library marker kkossev.deviceProfileLib, line 1150
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1151
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1152
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1153
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1154
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1155

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1157
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1158

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1160
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1161
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1162
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1163
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1164
        return false // library marker kkossev.deviceProfileLib, line 1165
    } // library marker kkossev.deviceProfileLib, line 1166
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1167
} // library marker kkossev.deviceProfileLib, line 1168

/* // library marker kkossev.deviceProfileLib, line 1170
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1171
 */ // library marker kkossev.deviceProfileLib, line 1172
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1173
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1174
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1175
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1176
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1177
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1178
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1179
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1180
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1181
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1182
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1183
        } // library marker kkossev.deviceProfileLib, line 1184
    } // library marker kkossev.deviceProfileLib, line 1185
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1186

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1188
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1189
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1190
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1191
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1192
    boolean preferenceExists = DEVICE?.preferences?.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1193
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1194
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1195
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1196
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1197
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1198
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1199
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1200
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1201
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1202
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1203
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1204

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1206
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1207
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1208
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1209
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1210
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1211
            if (settings.logEnable) { logInfo "${descText }" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1212
        } // library marker kkossev.deviceProfileLib, line 1213
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1214
    } // library marker kkossev.deviceProfileLib, line 1215

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1217
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1218
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1219
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1220
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1221
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1222
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1223
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1224
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1225
            } // library marker kkossev.deviceProfileLib, line 1226
        } // library marker kkossev.deviceProfileLib, line 1227
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1228
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1229
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1230
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1231
            } // library marker kkossev.deviceProfileLib, line 1232
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1233
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1234
            try { // library marker kkossev.deviceProfileLib, line 1235
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1236
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1237
            } // library marker kkossev.deviceProfileLib, line 1238
            catch (e) { // library marker kkossev.deviceProfileLib, line 1239
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1240
            } // library marker kkossev.deviceProfileLib, line 1241
        } // library marker kkossev.deviceProfileLib, line 1242
    } // library marker kkossev.deviceProfileLib, line 1243
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1244
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1245
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1246
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1247
    } // library marker kkossev.deviceProfileLib, line 1248

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1250
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1251
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1252
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1253
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1254
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1255
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1256
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1257
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1258
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1259
            } // library marker kkossev.deviceProfileLib, line 1260
            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1261
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1262
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1263
            // continue ... // library marker kkossev.deviceProfileLib, line 1264
            } // library marker kkossev.deviceProfileLib, line 1265
            else { // library marker kkossev.deviceProfileLib, line 1266
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1267
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1268
                } // library marker kkossev.deviceProfileLib, line 1269
                else { // library marker kkossev.deviceProfileLib, line 1270
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1271
                } // library marker kkossev.deviceProfileLib, line 1272
            } // library marker kkossev.deviceProfileLib, line 1273
        } // library marker kkossev.deviceProfileLib, line 1274

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1276
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1277
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1278
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1279
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1280
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1281
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1282
        } // library marker kkossev.deviceProfileLib, line 1283
        else { // library marker kkossev.deviceProfileLib, line 1284
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1285
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1286
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1287
                logTrace "event ${name} sent w/ value ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1288
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1289
            } // library marker kkossev.deviceProfileLib, line 1290
        } // library marker kkossev.deviceProfileLib, line 1291
    } // library marker kkossev.deviceProfileLib, line 1292
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1293
} // library marker kkossev.deviceProfileLib, line 1294

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1296
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1297
    //debug = true // library marker kkossev.deviceProfileLib, line 1298
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1299
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLib, line 1300
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLib, line 1301
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1302
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLib, line 1303
    String settingType = '' // library marker kkossev.deviceProfileLib, line 1304
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1305
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1306
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1307
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1308
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1309
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1310
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1311
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1312
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1313
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1314
            try { // library marker kkossev.deviceProfileLib, line 1315
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1316
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1317
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLib, line 1318
            } // library marker kkossev.deviceProfileLib, line 1319
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1320
            try { // library marker kkossev.deviceProfileLib, line 1321
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1322
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1323
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1324
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1325
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1326
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1327
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1328
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1329
                    } // library marker kkossev.deviceProfileLib, line 1330
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1331
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1332
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1333
                    } else { // library marker kkossev.deviceProfileLib, line 1334
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1335
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1336
                    } // library marker kkossev.deviceProfileLib, line 1337
                } // library marker kkossev.deviceProfileLib, line 1338
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1339
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1340
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1341
            } // library marker kkossev.deviceProfileLib, line 1342
            catch (e) { // library marker kkossev.deviceProfileLib, line 1343
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1344
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1345
                try { // library marker kkossev.deviceProfileLib, line 1346
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1347
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1348
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1349
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1350
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1351
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLib, line 1352
                } // library marker kkossev.deviceProfileLib, line 1353
            } // library marker kkossev.deviceProfileLib, line 1354
        } // library marker kkossev.deviceProfileLib, line 1355
        total ++ // library marker kkossev.deviceProfileLib, line 1356
    } // library marker kkossev.deviceProfileLib, line 1357
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1358
    return true // library marker kkossev.deviceProfileLib, line 1359
} // library marker kkossev.deviceProfileLib, line 1360

// command for debugging // library marker kkossev.deviceProfileLib, line 1362
public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1363
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1364
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1365
            logInfo "${fingerprint}" // library marker kkossev.deviceProfileLib, line 1366
        } // library marker kkossev.deviceProfileLib, line 1367
    } // library marker kkossev.deviceProfileLib, line 1368
} // library marker kkossev.deviceProfileLib, line 1369

// command for debugging // library marker kkossev.deviceProfileLib, line 1371
public void printPreferences() { // library marker kkossev.deviceProfileLib, line 1372
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 1373
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 1374
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 1375
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLib, line 1376
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 1377
                log.trace inputMap // library marker kkossev.deviceProfileLib, line 1378
            } // library marker kkossev.deviceProfileLib, line 1379
        } // library marker kkossev.deviceProfileLib, line 1380
    } // library marker kkossev.deviceProfileLib, line 1381
} // library marker kkossev.deviceProfileLib, line 1382

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~

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
  * ver. 3.2.0  2024-05-23 kkossev  - standardParse____Cluster and customParse___Cluster methods; moved onOff methods to a new library; rename all custom handlers in the libs to statdndardParseXXX // library marker kkossev.commonLib, line 36
  * ver. 3.2.1  2024-05-27 kkossev  - (dev. branch) 4 in 1 V3 compatibility; added IAS cluster; // library marker kkossev.commonLib, line 37
  * // library marker kkossev.commonLib, line 38
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 39
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 40
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib // library marker kkossev.commonLib, line 41
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 42
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 43
  * // library marker kkossev.commonLib, line 44
*/ // library marker kkossev.commonLib, line 45

String commonLibVersion() { '3.2.1' } // library marker kkossev.commonLib, line 47
String commonLibStamp() { '2024/05/27 10:13 PM' } // library marker kkossev.commonLib, line 48

import groovy.transform.Field // library marker kkossev.commonLib, line 50
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 51
import hubitat.device.Protocol // library marker kkossev.commonLib, line 52
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 53
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 54
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 55
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 56
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 57
import java.math.BigDecimal // library marker kkossev.commonLib, line 58

metadata { // library marker kkossev.commonLib, line 60
        if (_DEBUG) { // library marker kkossev.commonLib, line 61
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 62
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 63
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 64
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 65
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 66
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 67
            ] // library marker kkossev.commonLib, line 68
        } // library marker kkossev.commonLib, line 69

        // common capabilities for all device types // library marker kkossev.commonLib, line 71
        capability 'Configuration' // library marker kkossev.commonLib, line 72
        capability 'Refresh' // library marker kkossev.commonLib, line 73
        capability 'Health Check' // library marker kkossev.commonLib, line 74

        // common attributes for all device types // library marker kkossev.commonLib, line 76
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 77
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 78
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 79

        // common commands for all device types // library marker kkossev.commonLib, line 81
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 82

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 84
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 85

    preferences { // library marker kkossev.commonLib, line 87
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 88
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 89
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 90

        if (device) { // library marker kkossev.commonLib, line 92
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 93
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 94
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 95
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 96
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 97
            } // library marker kkossev.commonLib, line 98
        } // library marker kkossev.commonLib, line 99
    } // library marker kkossev.commonLib, line 100
} // library marker kkossev.commonLib, line 101

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 103
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 104
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 105
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 106
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 107
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 108
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 109
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 110
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 111
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 112
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 113

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 115
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 116
] // library marker kkossev.commonLib, line 117
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 118
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 119
] // library marker kkossev.commonLib, line 120

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 122
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 123
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 124
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 125
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 126
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 127
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 128
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 129
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 130
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 131
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 132
] // library marker kkossev.commonLib, line 133

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 135
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 136
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 137
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 138
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 139
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 140

/** // library marker kkossev.commonLib, line 142
 * Parse Zigbee message // library marker kkossev.commonLib, line 143
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 144
 */ // library marker kkossev.commonLib, line 145
void parse(final String description) { // library marker kkossev.commonLib, line 146
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 147
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 148
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 149
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 150

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 152
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 153
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 154
            parseIasMessage(description) // library marker kkossev.commonLib, line 155
        } // library marker kkossev.commonLib, line 156
        else { // library marker kkossev.commonLib, line 157
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 158
        } // library marker kkossev.commonLib, line 159
        return // library marker kkossev.commonLib, line 160
    } // library marker kkossev.commonLib, line 161
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 162
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 163
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 164
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 165
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 166
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 167
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 168
        return // library marker kkossev.commonLib, line 169
    } // library marker kkossev.commonLib, line 170

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 172
        return // library marker kkossev.commonLib, line 173
    } // library marker kkossev.commonLib, line 174
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 175

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 177
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 178

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 180
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 181
        return // library marker kkossev.commonLib, line 182
    } // library marker kkossev.commonLib, line 183
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 184
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 185
        return // library marker kkossev.commonLib, line 186
    } // library marker kkossev.commonLib, line 187
    // // library marker kkossev.commonLib, line 188
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 189
    // // library marker kkossev.commonLib, line 190
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 191
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 192
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 193
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 194
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 195
            } // library marker kkossev.commonLib, line 196
            break // library marker kkossev.commonLib, line 197
        default: // library marker kkossev.commonLib, line 198
            if (settings.logEnable) { // library marker kkossev.commonLib, line 199
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 200
            } // library marker kkossev.commonLib, line 201
            break // library marker kkossev.commonLib, line 202
    } // library marker kkossev.commonLib, line 203
} // library marker kkossev.commonLib, line 204

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 206
    0x0000: 'Basic',                0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x000C: 'AnalogInput', // library marker kkossev.commonLib, line 207
    0x0006: 'OnOff',                0x0008: 'LevelControl',     0x0012: 'MultistateInput',  0x0102: 'WindowCovering',   0x0201: 'Thermostat',   0x0300: 'ColorControl', // library marker kkossev.commonLib, line 208
    0x0400: 'Illuminance',          0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'ElectricalMeasure', // library marker kkossev.commonLib, line 209
    0x0B04: 'Metering',             0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',             0xFC11: 'FC11',         0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 210
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 211
] // library marker kkossev.commonLib, line 212

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 214
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 215
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 216
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 217
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 218
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 219
        return false // library marker kkossev.commonLib, line 220
    } // library marker kkossev.commonLib, line 221
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 222
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 223
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 224
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 225
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 226
        return true // library marker kkossev.commonLib, line 227
    } // library marker kkossev.commonLib, line 228
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 229
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 230
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 231
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 232
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 233
        return true // library marker kkossev.commonLib, line 234
    } // library marker kkossev.commonLib, line 235
    if (device?.getDataValue('model') != 'ZigUSB') {    // patch! // library marker kkossev.commonLib, line 236
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 237
    } // library marker kkossev.commonLib, line 238
    return false // library marker kkossev.commonLib, line 239
} // library marker kkossev.commonLib, line 240

static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 242
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 243
} // library marker kkossev.commonLib, line 244

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 246
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 247
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 248
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 249
    } // library marker kkossev.commonLib, line 250
    return false // library marker kkossev.commonLib, line 251
} // library marker kkossev.commonLib, line 252

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 254
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 255
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 256
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 257
    } // library marker kkossev.commonLib, line 258
    return false // library marker kkossev.commonLib, line 259
} // library marker kkossev.commonLib, line 260

boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 262
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 263
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 264
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 265
    } // library marker kkossev.commonLib, line 266
    return false // library marker kkossev.commonLib, line 267
} // library marker kkossev.commonLib, line 268

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 270
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 271
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 272
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 273
] // library marker kkossev.commonLib, line 274

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 276
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 277
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 278
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 279
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 280
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 281
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 282
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 283
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 284
    List<String> cmds = [] // library marker kkossev.commonLib, line 285
    switch (clusterId) { // library marker kkossev.commonLib, line 286
        case 0x0005 : // library marker kkossev.commonLib, line 287
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 288
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 289
            // send the active endpoint response // library marker kkossev.commonLib, line 290
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 291
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 292
            break // library marker kkossev.commonLib, line 293
        case 0x0006 : // library marker kkossev.commonLib, line 294
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 295
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 296
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 297
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 298
            break // library marker kkossev.commonLib, line 299
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 300
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 301
            if (settings?.logEnable) { log.info "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 302
            break // library marker kkossev.commonLib, line 303
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 304
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 305
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 306
            break // library marker kkossev.commonLib, line 307
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 308
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 309
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 310
            if (settings?.logEnable) { log.info "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 311
            break // library marker kkossev.commonLib, line 312
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 313
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 314
            break // library marker kkossev.commonLib, line 315
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 316
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 317
            if (settings?.logEnable) { log.info "${clusterInfo}" } // library marker kkossev.commonLib, line 318
            break // library marker kkossev.commonLib, line 319
        default : // library marker kkossev.commonLib, line 320
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 321
            break // library marker kkossev.commonLib, line 322
    } // library marker kkossev.commonLib, line 323
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 324
} // library marker kkossev.commonLib, line 325

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 327
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 328
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 329
    switch (commandId) { // library marker kkossev.commonLib, line 330
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 331
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 332
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 333
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 334
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 335
        default: // library marker kkossev.commonLib, line 336
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 337
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 338
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 339
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 340
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 341
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 342
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 343
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 344
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 345
            } // library marker kkossev.commonLib, line 346
            break // library marker kkossev.commonLib, line 347
    } // library marker kkossev.commonLib, line 348
} // library marker kkossev.commonLib, line 349

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 351
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 352
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 353
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 354
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 355
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 356
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 357
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 358
    } // library marker kkossev.commonLib, line 359
    else { // library marker kkossev.commonLib, line 360
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 361
    } // library marker kkossev.commonLib, line 362
} // library marker kkossev.commonLib, line 363

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 365
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 366
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 367
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 368
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 369
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 370
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 371
    } // library marker kkossev.commonLib, line 372
    else { // library marker kkossev.commonLib, line 373
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 374
    } // library marker kkossev.commonLib, line 375
} // library marker kkossev.commonLib, line 376

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 378
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 379
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 380
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 381
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 382
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 383
        state.reportingEnabled = true // library marker kkossev.commonLib, line 384
    } // library marker kkossev.commonLib, line 385
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 386
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 387
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 388
    } else { // library marker kkossev.commonLib, line 389
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 390
    } // library marker kkossev.commonLib, line 391
} // library marker kkossev.commonLib, line 392

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 394
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 395
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 396
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 397
    if (status == 0) { // library marker kkossev.commonLib, line 398
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 399
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 400
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 401
        int delta = 0 // library marker kkossev.commonLib, line 402
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 403
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 404
        } // library marker kkossev.commonLib, line 405
        else { // library marker kkossev.commonLib, line 406
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 407
        } // library marker kkossev.commonLib, line 408
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 409
    } // library marker kkossev.commonLib, line 410
    else { // library marker kkossev.commonLib, line 411
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 412
    } // library marker kkossev.commonLib, line 413
} // library marker kkossev.commonLib, line 414

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 416
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 417
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 418
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 419
        return false // library marker kkossev.commonLib, line 420
    } // library marker kkossev.commonLib, line 421
    // execute the customHandler function // library marker kkossev.commonLib, line 422
    boolean result = false // library marker kkossev.commonLib, line 423
    try { // library marker kkossev.commonLib, line 424
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 425
    } // library marker kkossev.commonLib, line 426
    catch (e) { // library marker kkossev.commonLib, line 427
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 428
        return false // library marker kkossev.commonLib, line 429
    } // library marker kkossev.commonLib, line 430
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 431
    return result // library marker kkossev.commonLib, line 432
} // library marker kkossev.commonLib, line 433

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 435
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 436
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 437
    final String commandId = data[0] // library marker kkossev.commonLib, line 438
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 439
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 440
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 441
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 442
    } else { // library marker kkossev.commonLib, line 443
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 444
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 445
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 446
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 447
        } // library marker kkossev.commonLib, line 448
    } // library marker kkossev.commonLib, line 449
} // library marker kkossev.commonLib, line 450

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 452
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 453
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 454
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 455

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 457
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 458
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 459
] // library marker kkossev.commonLib, line 460

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 462
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 463
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 464
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 465
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 466
] // library marker kkossev.commonLib, line 467

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 469
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 470
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 471
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 472
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 473
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 474
    return avg // library marker kkossev.commonLib, line 475
} // library marker kkossev.commonLib, line 476

/* // library marker kkossev.commonLib, line 478
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 479
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 480
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 481
*/ // library marker kkossev.commonLib, line 482
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 483

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 485
void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 486
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 487
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 488
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 489
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 490
        case 0x0000: // library marker kkossev.commonLib, line 491
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 492
            break // library marker kkossev.commonLib, line 493
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 494
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 495
            if (isPing) { // library marker kkossev.commonLib, line 496
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 497
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 498
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 499
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 500
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 501
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 502
                    sendRttEvent() // library marker kkossev.commonLib, line 503
                } // library marker kkossev.commonLib, line 504
                else { // library marker kkossev.commonLib, line 505
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 506
                } // library marker kkossev.commonLib, line 507
                state.states['isPing'] = false // library marker kkossev.commonLib, line 508
            } // library marker kkossev.commonLib, line 509
            else { // library marker kkossev.commonLib, line 510
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 511
            } // library marker kkossev.commonLib, line 512
            break // library marker kkossev.commonLib, line 513
        case 0x0004: // library marker kkossev.commonLib, line 514
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 515
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 516
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 517
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 518
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 519
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 520
            } // library marker kkossev.commonLib, line 521
            break // library marker kkossev.commonLib, line 522
        case 0x0005: // library marker kkossev.commonLib, line 523
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 524
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 525
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 526
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 527
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 528
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 529
            } // library marker kkossev.commonLib, line 530
            break // library marker kkossev.commonLib, line 531
        case 0x0007: // library marker kkossev.commonLib, line 532
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 533
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 534
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 535
            break // library marker kkossev.commonLib, line 536
        case 0xFFDF: // library marker kkossev.commonLib, line 537
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 538
            break // library marker kkossev.commonLib, line 539
        case 0xFFE2: // library marker kkossev.commonLib, line 540
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 541
            break // library marker kkossev.commonLib, line 542
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 543
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 544
            break // library marker kkossev.commonLib, line 545
        case 0xFFFE: // library marker kkossev.commonLib, line 546
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 547
            break // library marker kkossev.commonLib, line 548
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 549
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 550
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 551
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 552
            break // library marker kkossev.commonLib, line 553
        default: // library marker kkossev.commonLib, line 554
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 555
            break // library marker kkossev.commonLib, line 556
    } // library marker kkossev.commonLib, line 557
} // library marker kkossev.commonLib, line 558

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 560
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 561
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 562

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 564
    Map descMap = [:] // library marker kkossev.commonLib, line 565
    try { // library marker kkossev.commonLib, line 566
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 567
    } // library marker kkossev.commonLib, line 568
    catch (e1) { // library marker kkossev.commonLib, line 569
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 570
        // try alternative custom parsing // library marker kkossev.commonLib, line 571
        descMap = [:] // library marker kkossev.commonLib, line 572
        try { // library marker kkossev.commonLib, line 573
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 574
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 575
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 576
            } // library marker kkossev.commonLib, line 577
        } // library marker kkossev.commonLib, line 578
        catch (e2) { // library marker kkossev.commonLib, line 579
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 580
            return [:] // library marker kkossev.commonLib, line 581
        } // library marker kkossev.commonLib, line 582
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 583
    } // library marker kkossev.commonLib, line 584
    return descMap // library marker kkossev.commonLib, line 585
} // library marker kkossev.commonLib, line 586

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 588
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 589
        return false // library marker kkossev.commonLib, line 590
    } // library marker kkossev.commonLib, line 591
    // try to parse ... // library marker kkossev.commonLib, line 592
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 593
    Map descMap = [:] // library marker kkossev.commonLib, line 594
    try { // library marker kkossev.commonLib, line 595
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 596
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 597
    } // library marker kkossev.commonLib, line 598
    catch (e) { // library marker kkossev.commonLib, line 599
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 600
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 601
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 602
        return true // library marker kkossev.commonLib, line 603
    } // library marker kkossev.commonLib, line 604

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 606
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 607
    } // library marker kkossev.commonLib, line 608
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 609
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 610
    } // library marker kkossev.commonLib, line 611
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 612
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 613
    } // library marker kkossev.commonLib, line 614
    else { // library marker kkossev.commonLib, line 615
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 616
        return false // library marker kkossev.commonLib, line 617
    } // library marker kkossev.commonLib, line 618
    return true    // processed // library marker kkossev.commonLib, line 619
} // library marker kkossev.commonLib, line 620

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 622
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 623
  /* // library marker kkossev.commonLib, line 624
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 625
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 626
        return true // library marker kkossev.commonLib, line 627
    } // library marker kkossev.commonLib, line 628
*/ // library marker kkossev.commonLib, line 629
    Map descMap = [:] // library marker kkossev.commonLib, line 630
    try { // library marker kkossev.commonLib, line 631
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 632
    } // library marker kkossev.commonLib, line 633
    catch (e1) { // library marker kkossev.commonLib, line 634
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 635
        // try alternative custom parsing // library marker kkossev.commonLib, line 636
        descMap = [:] // library marker kkossev.commonLib, line 637
        try { // library marker kkossev.commonLib, line 638
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 639
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 640
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 641
            } // library marker kkossev.commonLib, line 642
        } // library marker kkossev.commonLib, line 643
        catch (e2) { // library marker kkossev.commonLib, line 644
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 645
            return true // library marker kkossev.commonLib, line 646
        } // library marker kkossev.commonLib, line 647
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 648
    } // library marker kkossev.commonLib, line 649
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 650
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 651
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 652
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 653
        return false // library marker kkossev.commonLib, line 654
    } // library marker kkossev.commonLib, line 655
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 656
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 657
    // attribute report received // library marker kkossev.commonLib, line 658
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 659
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 660
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 661
    } // library marker kkossev.commonLib, line 662
    attrData.each { // library marker kkossev.commonLib, line 663
        if (it.status == '86') { // library marker kkossev.commonLib, line 664
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 665
        // TODO - skip parsing? // library marker kkossev.commonLib, line 666
        } // library marker kkossev.commonLib, line 667
        switch (it.cluster) { // library marker kkossev.commonLib, line 668
            case '0000' : // library marker kkossev.commonLib, line 669
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 670
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 671
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 672
                } // library marker kkossev.commonLib, line 673
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 674
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 675
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 676
                } // library marker kkossev.commonLib, line 677
                else { // library marker kkossev.commonLib, line 678
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 679
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 680
                } // library marker kkossev.commonLib, line 681
                break // library marker kkossev.commonLib, line 682
            default : // library marker kkossev.commonLib, line 683
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 684
                break // library marker kkossev.commonLib, line 685
        } // switch // library marker kkossev.commonLib, line 686
    } // for each attribute // library marker kkossev.commonLib, line 687
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 688
} // library marker kkossev.commonLib, line 689


String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 692
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 693
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 694
} // library marker kkossev.commonLib, line 695

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 697
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 698
} // library marker kkossev.commonLib, line 699

/* // library marker kkossev.commonLib, line 701
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 702
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 703
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 704
*/ // library marker kkossev.commonLib, line 705
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 706
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 707
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 708

// Tuya Commands // library marker kkossev.commonLib, line 710
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 711
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 712
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 713
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 714
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 715

// tuya DP type // library marker kkossev.commonLib, line 717
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 718
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 719
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 720
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 721
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 722
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 723

void syncTuyaDateTime() { // library marker kkossev.commonLib, line 725
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 726
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 727
    long offset = 0 // library marker kkossev.commonLib, line 728
    int offsetHours = 0 // library marker kkossev.commonLib, line 729
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 730
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 731
    try { // library marker kkossev.commonLib, line 732
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 733
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 734
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 735
    } catch (e) { // library marker kkossev.commonLib, line 736
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 737
    } // library marker kkossev.commonLib, line 738
    // // library marker kkossev.commonLib, line 739
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 740
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 741
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 742
} // library marker kkossev.commonLib, line 743

// called from the main parse method when the cluster is 0xEF00 // library marker kkossev.commonLib, line 745
void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 746
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 747
        syncTuyaDateTime() // library marker kkossev.commonLib, line 748
    } // library marker kkossev.commonLib, line 749
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 750
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 751
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 752
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 753
        if (status != '00') { // library marker kkossev.commonLib, line 754
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 755
        } // library marker kkossev.commonLib, line 756
    } // library marker kkossev.commonLib, line 757
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 758
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 759
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 760
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 761
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 762
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 763
            return // library marker kkossev.commonLib, line 764
        } // library marker kkossev.commonLib, line 765
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 766
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 767
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 768
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 769
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 770
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 771
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 772
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 773
            } // library marker kkossev.commonLib, line 774
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 775
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 776
        } // library marker kkossev.commonLib, line 777
    } // library marker kkossev.commonLib, line 778
    else { // library marker kkossev.commonLib, line 779
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 780
    } // library marker kkossev.commonLib, line 781
} // library marker kkossev.commonLib, line 782

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 784
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 785
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 786
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 787
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 788
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 789
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 790
        } // library marker kkossev.commonLib, line 791
    } // library marker kkossev.commonLib, line 792
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 793
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) {   // library marker kkossev.commonLib, line 794
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 795
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 796
        } // library marker kkossev.commonLib, line 797
    } // library marker kkossev.commonLib, line 798
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 799
} // library marker kkossev.commonLib, line 800

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 802
    int retValue = 0 // library marker kkossev.commonLib, line 803
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 804
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 805
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 806
        int power = 1 // library marker kkossev.commonLib, line 807
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 808
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 809
            power = power * 256 // library marker kkossev.commonLib, line 810
        } // library marker kkossev.commonLib, line 811
    } // library marker kkossev.commonLib, line 812
    return retValue // library marker kkossev.commonLib, line 813
} // library marker kkossev.commonLib, line 814

private List<String> getTuyaCommand(String dp, String dp_type, String fncmd) { return sendTuyaCommand(dp, dp_type, fncmd) } // library marker kkossev.commonLib, line 816

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 818
    List<String> cmds = [] // library marker kkossev.commonLib, line 819
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 820
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 821
    //int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 822
    int tuyaCmd = isFingerbot() ? 0x04 : tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 823
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 824
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 825
    return cmds // library marker kkossev.commonLib, line 826
} // library marker kkossev.commonLib, line 827

private getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 829

void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 831
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 832
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 833
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 834
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 835
} // library marker kkossev.commonLib, line 836

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 838
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 839

List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 841
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 842
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 843
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 844
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 845
} // library marker kkossev.commonLib, line 846

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 848
    List<String> cmds = [] // library marker kkossev.commonLib, line 849
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 850
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 851
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 852
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 853
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 854
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 855
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 856
        } // library marker kkossev.commonLib, line 857
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 858
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 859
    } // library marker kkossev.commonLib, line 860
    else { // library marker kkossev.commonLib, line 861
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 862
    } // library marker kkossev.commonLib, line 863
} // library marker kkossev.commonLib, line 864

// Invoked from configure() // library marker kkossev.commonLib, line 866
List<String> initializeDevice() { // library marker kkossev.commonLib, line 867
    List<String> cmds = [] // library marker kkossev.commonLib, line 868
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 869
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 870
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 871
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 872
    } // library marker kkossev.commonLib, line 873
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 874
    return cmds // library marker kkossev.commonLib, line 875
} // library marker kkossev.commonLib, line 876

// Invoked from configure() // library marker kkossev.commonLib, line 878
List<String> configureDevice() { // library marker kkossev.commonLib, line 879
    List<String> cmds = [] // library marker kkossev.commonLib, line 880
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 881
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 882
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 883
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 884
    } // library marker kkossev.commonLib, line 885
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 886
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 887
    return cmds // library marker kkossev.commonLib, line 888
} // library marker kkossev.commonLib, line 889

/* // library marker kkossev.commonLib, line 891
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 892
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 893
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 894
*/ // library marker kkossev.commonLib, line 895

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 897
    List<String> cmds = [] // library marker kkossev.commonLib, line 898
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 899
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 900
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 901
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 902
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 903
            } // library marker kkossev.commonLib, line 904
        } // library marker kkossev.commonLib, line 905
    } // library marker kkossev.commonLib, line 906
    return cmds // library marker kkossev.commonLib, line 907
} // library marker kkossev.commonLib, line 908

void refresh() { // library marker kkossev.commonLib, line 910
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 911
    checkDriverVersion(state) // library marker kkossev.commonLib, line 912
    List<String> cmds = [] // library marker kkossev.commonLib, line 913
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 914
    List<String> customCmds = customHandlers(['batteryRefresh', 'groupsRefresh', 'onOffRefresh', 'customRefresh']) // library marker kkossev.commonLib, line 915
    if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customHandlers refresh() defined' } // library marker kkossev.commonLib, line 916
    if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 917
        cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 918
        cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 919
    } // library marker kkossev.commonLib, line 920
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 921
        cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 922
        cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 923
    } // library marker kkossev.commonLib, line 924
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 925
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 926
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 927
    } // library marker kkossev.commonLib, line 928
    else { // library marker kkossev.commonLib, line 929
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 930
    } // library marker kkossev.commonLib, line 931
} // library marker kkossev.commonLib, line 932

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 934
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 935
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 936

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 938
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 939
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 940
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 941
    } // library marker kkossev.commonLib, line 942
    else { // library marker kkossev.commonLib, line 943
        logInfo "${info}" // library marker kkossev.commonLib, line 944
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 945
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 946
    } // library marker kkossev.commonLib, line 947
} // library marker kkossev.commonLib, line 948

public void ping() { // library marker kkossev.commonLib, line 950
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 951
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 952
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 953
    if (isVirtual()) { runInMillis(10, virtualPong) } // library marker kkossev.commonLib, line 954
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) } // library marker kkossev.commonLib, line 955
    logDebug 'ping...' // library marker kkossev.commonLib, line 956
} // library marker kkossev.commonLib, line 957

def virtualPong() { // library marker kkossev.commonLib, line 959
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 960
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 961
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 962
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 963
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 964
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 965
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 966
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 967
        sendRttEvent() // library marker kkossev.commonLib, line 968
    } // library marker kkossev.commonLib, line 969
    else { // library marker kkossev.commonLib, line 970
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 971
    } // library marker kkossev.commonLib, line 972
    state.states['isPing'] = false // library marker kkossev.commonLib, line 973
    //unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 974
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 975
} // library marker kkossev.commonLib, line 976

void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 978
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 979
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 980
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 981
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 982
    if (value == null) { // library marker kkossev.commonLib, line 983
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 984
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 985
    } // library marker kkossev.commonLib, line 986
    else { // library marker kkossev.commonLib, line 987
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 988
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 989
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 990
    } // library marker kkossev.commonLib, line 991
} // library marker kkossev.commonLib, line 992

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 994
    if (cluster != null) { // library marker kkossev.commonLib, line 995
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 996
    } // library marker kkossev.commonLib, line 997
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 998
    return 'NULL' // library marker kkossev.commonLib, line 999
} // library marker kkossev.commonLib, line 1000

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1002
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1003
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1004
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1005
} // library marker kkossev.commonLib, line 1006

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1008
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1009
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1010
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1011
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1012
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1013
    } // library marker kkossev.commonLib, line 1014
} // library marker kkossev.commonLib, line 1015

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1017
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1018
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1019
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1020
} // library marker kkossev.commonLib, line 1021

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1023
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1024
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1025
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1026
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1027
    } // library marker kkossev.commonLib, line 1028
    else { // library marker kkossev.commonLib, line 1029
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1030
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1031
    } // library marker kkossev.commonLib, line 1032
} // library marker kkossev.commonLib, line 1033

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1035
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1036
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1037
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1038
} // library marker kkossev.commonLib, line 1039

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1041
void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1042
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1043
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1044
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1045
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1046
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1047
    } // library marker kkossev.commonLib, line 1048
} // library marker kkossev.commonLib, line 1049

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1051
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1052
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1053
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1054
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1055
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1056
            logWarn 'not present!' // library marker kkossev.commonLib, line 1057
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1058
        } // library marker kkossev.commonLib, line 1059
    } // library marker kkossev.commonLib, line 1060
    else { // library marker kkossev.commonLib, line 1061
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1062
    } // library marker kkossev.commonLib, line 1063
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1064
} // library marker kkossev.commonLib, line 1065

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1067
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1068
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 1069
    if (value == 'online') { // library marker kkossev.commonLib, line 1070
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1071
    } // library marker kkossev.commonLib, line 1072
    else { // library marker kkossev.commonLib, line 1073
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1074
    } // library marker kkossev.commonLib, line 1075
} // library marker kkossev.commonLib, line 1076

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1078
void updated() { // library marker kkossev.commonLib, line 1079
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1080
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1081
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1082
    unschedule() // library marker kkossev.commonLib, line 1083

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1085
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1086
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1087
    } // library marker kkossev.commonLib, line 1088
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1089
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1090
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1091
    } // library marker kkossev.commonLib, line 1092

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1094
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1095
        // schedule the periodic timer // library marker kkossev.commonLib, line 1096
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1097
        if (interval > 0) { // library marker kkossev.commonLib, line 1098
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1099
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1100
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1101
        } // library marker kkossev.commonLib, line 1102
    } // library marker kkossev.commonLib, line 1103
    else { // library marker kkossev.commonLib, line 1104
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1105
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1106
    } // library marker kkossev.commonLib, line 1107
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1108
        customUpdated() // library marker kkossev.commonLib, line 1109
    } // library marker kkossev.commonLib, line 1110

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1112
} // library marker kkossev.commonLib, line 1113

void logsOff() { // library marker kkossev.commonLib, line 1115
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1116
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1117
} // library marker kkossev.commonLib, line 1118
void traceOff() { // library marker kkossev.commonLib, line 1119
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1120
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1121
} // library marker kkossev.commonLib, line 1122

void configure(String command) { // library marker kkossev.commonLib, line 1124
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1125
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1126
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1127
        return // library marker kkossev.commonLib, line 1128
    } // library marker kkossev.commonLib, line 1129
    // // library marker kkossev.commonLib, line 1130
    String func // library marker kkossev.commonLib, line 1131
    try { // library marker kkossev.commonLib, line 1132
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1133
        "$func"() // library marker kkossev.commonLib, line 1134
    } // library marker kkossev.commonLib, line 1135
    catch (e) { // library marker kkossev.commonLib, line 1136
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1137
        return // library marker kkossev.commonLib, line 1138
    } // library marker kkossev.commonLib, line 1139
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1140
} // library marker kkossev.commonLib, line 1141

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1143
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1144
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1145
} // library marker kkossev.commonLib, line 1146

void loadAllDefaults() { // library marker kkossev.commonLib, line 1148
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1149
    deleteAllSettings() // library marker kkossev.commonLib, line 1150
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1151
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1152
    deleteAllStates() // library marker kkossev.commonLib, line 1153
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1154
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
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1178
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1179
    } // library marker kkossev.commonLib, line 1180
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1181
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1182
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1183
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1184
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1185
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1186
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1187
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1188
    } // library marker kkossev.commonLib, line 1189
    else { // library marker kkossev.commonLib, line 1190
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1191
    } // library marker kkossev.commonLib, line 1192
} // library marker kkossev.commonLib, line 1193

 // Invoked when the device is installed or when driver is installed ? // library marker kkossev.commonLib, line 1195
void installed() { // library marker kkossev.commonLib, line 1196
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1197
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1198
    // populate some default values for attributes // library marker kkossev.commonLib, line 1199
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 1200
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 1201
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1202
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1203
} // library marker kkossev.commonLib, line 1204

 // Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1206
void initialize() { // library marker kkossev.commonLib, line 1207
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1208
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1209
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1210
    updateTuyaVersion() // library marker kkossev.commonLib, line 1211
    updateAqaraVersion() // library marker kkossev.commonLib, line 1212
} // library marker kkossev.commonLib, line 1213

/* // library marker kkossev.commonLib, line 1215
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1216
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1217
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1218
*/ // library marker kkossev.commonLib, line 1219

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1221
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1222
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1223
} // library marker kkossev.commonLib, line 1224

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1226
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1227
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1228
} // library marker kkossev.commonLib, line 1229

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1231
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1232
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1233
} // library marker kkossev.commonLib, line 1234

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1236
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1237
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1238
        return // library marker kkossev.commonLib, line 1239
    } // library marker kkossev.commonLib, line 1240
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1241
    cmd.each { // library marker kkossev.commonLib, line 1242
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1243
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1244
            return // library marker kkossev.commonLib, line 1245
        } // library marker kkossev.commonLib, line 1246
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1247
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1248
    } // library marker kkossev.commonLib, line 1249
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1250
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1251
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1252
} // library marker kkossev.commonLib, line 1253

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1255

String getDeviceInfo() { // library marker kkossev.commonLib, line 1257
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1258
} // library marker kkossev.commonLib, line 1259

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1261
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1262
} // library marker kkossev.commonLib, line 1263

@CompileStatic // library marker kkossev.commonLib, line 1265
void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1266
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1267
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1268
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1269
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1270
        initializeVars(false) // library marker kkossev.commonLib, line 1271
        updateTuyaVersion() // library marker kkossev.commonLib, line 1272
        updateAqaraVersion() // library marker kkossev.commonLib, line 1273
    } // library marker kkossev.commonLib, line 1274
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1275
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1276
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1277
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1278
} // library marker kkossev.commonLib, line 1279

// credits @thebearmay // library marker kkossev.commonLib, line 1281
String getModel() { // library marker kkossev.commonLib, line 1282
    try { // library marker kkossev.commonLib, line 1283
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1284
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1285
    } catch (ignore) { // library marker kkossev.commonLib, line 1286
        try { // library marker kkossev.commonLib, line 1287
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1288
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1289
                return model // library marker kkossev.commonLib, line 1290
            } // library marker kkossev.commonLib, line 1291
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1292
            return '' // library marker kkossev.commonLib, line 1293
        } // library marker kkossev.commonLib, line 1294
    } // library marker kkossev.commonLib, line 1295
} // library marker kkossev.commonLib, line 1296

// credits @thebearmay // library marker kkossev.commonLib, line 1298
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1299
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1300
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1301
    String revision = tokens.last() // library marker kkossev.commonLib, line 1302
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1303
} // library marker kkossev.commonLib, line 1304

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1306
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1307
    unschedule() // library marker kkossev.commonLib, line 1308
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1309
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1310

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1312
} // library marker kkossev.commonLib, line 1313

void resetStatistics() { // library marker kkossev.commonLib, line 1315
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1316
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1317
} // library marker kkossev.commonLib, line 1318

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1320
void resetStats() { // library marker kkossev.commonLib, line 1321
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1322
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1323
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1324
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1325
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1326
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1327
} // library marker kkossev.commonLib, line 1328

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1330
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1331
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1332
        state.clear() // library marker kkossev.commonLib, line 1333
        unschedule() // library marker kkossev.commonLib, line 1334
        resetStats() // library marker kkossev.commonLib, line 1335
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 1336
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1337
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1338
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1339
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1340
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1341
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1342
    } // library marker kkossev.commonLib, line 1343

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1345
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1346
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1347
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1348
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1349

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1351
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1352
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1353
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1354
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1355
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1356
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1357

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1359

    // common libraries initialization // library marker kkossev.commonLib, line 1361
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1362
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1363
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1364
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1365

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1367
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1368
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1369
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1370

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1372
    if ( mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1373
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1374
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1375
    if ( ep  != null) { // library marker kkossev.commonLib, line 1376
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1377
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1378
    } // library marker kkossev.commonLib, line 1379
    else { // library marker kkossev.commonLib, line 1380
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1381
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1382
    } // library marker kkossev.commonLib, line 1383
} // library marker kkossev.commonLib, line 1384

void setDestinationEP() { // library marker kkossev.commonLib, line 1386
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1387
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1388
        state.destinationEP = ep // library marker kkossev.commonLib, line 1389
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1390
    } // library marker kkossev.commonLib, line 1391
    else { // library marker kkossev.commonLib, line 1392
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1393
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 1394
    } // library marker kkossev.commonLib, line 1395
} // library marker kkossev.commonLib, line 1396

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1398
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1399
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1400
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1401

// _DEBUG mode only // library marker kkossev.commonLib, line 1403
void getAllProperties() { // library marker kkossev.commonLib, line 1404
    log.trace 'Properties:' // library marker kkossev.commonLib, line 1405
    device.properties.each { it -> // library marker kkossev.commonLib, line 1406
        log.debug it // library marker kkossev.commonLib, line 1407
    } // library marker kkossev.commonLib, line 1408
    log.trace 'Settings:' // library marker kkossev.commonLib, line 1409
    settings.each { it -> // library marker kkossev.commonLib, line 1410
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1411
    } // library marker kkossev.commonLib, line 1412
    log.trace 'Done' // library marker kkossev.commonLib, line 1413
} // library marker kkossev.commonLib, line 1414

// delete all Preferences // library marker kkossev.commonLib, line 1416
void deleteAllSettings() { // library marker kkossev.commonLib, line 1417
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1418
    settings.each { it -> // library marker kkossev.commonLib, line 1419
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 1420
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 1421
    } // library marker kkossev.commonLib, line 1422
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1423
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1424
} // library marker kkossev.commonLib, line 1425

// delete all attributes // library marker kkossev.commonLib, line 1427
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1428
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1429
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1430
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1431
} // library marker kkossev.commonLib, line 1432

// delete all State Variables // library marker kkossev.commonLib, line 1434
void deleteAllStates() { // library marker kkossev.commonLib, line 1435
    String stateDeleted = '' // library marker kkossev.commonLib, line 1436
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1437
    state.clear() // library marker kkossev.commonLib, line 1438
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1439
} // library marker kkossev.commonLib, line 1440

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1442
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1443
} // library marker kkossev.commonLib, line 1444

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1446
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 1447
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 1448
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 1449
    } // library marker kkossev.commonLib, line 1450
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1451
} // library marker kkossev.commonLib, line 1452

void testParse(String par) { // library marker kkossev.commonLib, line 1454
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1455
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1456
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1457
    parse(par) // library marker kkossev.commonLib, line 1458
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1459
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1460
} // library marker kkossev.commonLib, line 1461

def testJob() { // library marker kkossev.commonLib, line 1463
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1464
} // library marker kkossev.commonLib, line 1465

/** // library marker kkossev.commonLib, line 1467
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1468
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1469
 */ // library marker kkossev.commonLib, line 1470
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1471
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1472
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1473
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1474
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1475
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1476
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1477
    String cron // library marker kkossev.commonLib, line 1478
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1479
    else { // library marker kkossev.commonLib, line 1480
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1481
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1482
    } // library marker kkossev.commonLib, line 1483
    return cron // library marker kkossev.commonLib, line 1484
} // library marker kkossev.commonLib, line 1485

// credits @thebearmay // library marker kkossev.commonLib, line 1487
String formatUptime() { // library marker kkossev.commonLib, line 1488
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1489
} // library marker kkossev.commonLib, line 1490

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1492
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1493
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1494
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1495
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1496
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1497
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1498
} // library marker kkossev.commonLib, line 1499

boolean isTuya() { // library marker kkossev.commonLib, line 1501
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1502
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1503
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1504
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1505
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1506
} // library marker kkossev.commonLib, line 1507

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1509
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1510
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1511
    if (application != null) { // library marker kkossev.commonLib, line 1512
        Integer ver // library marker kkossev.commonLib, line 1513
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1514
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1515
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1516
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1517
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1518
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1519
        } // library marker kkossev.commonLib, line 1520
    } // library marker kkossev.commonLib, line 1521
} // library marker kkossev.commonLib, line 1522

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1524

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1526
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1527
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1528
    if (application != null) { // library marker kkossev.commonLib, line 1529
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1530
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1531
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1532
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1533
        } // library marker kkossev.commonLib, line 1534
    } // library marker kkossev.commonLib, line 1535
} // library marker kkossev.commonLib, line 1536

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1538
    try { // library marker kkossev.commonLib, line 1539
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1540
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1541
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1542
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1543
    } catch (e) { // library marker kkossev.commonLib, line 1544
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1545
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1546
    } // library marker kkossev.commonLib, line 1547
} // library marker kkossev.commonLib, line 1548

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1550
    try { // library marker kkossev.commonLib, line 1551
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1552
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1553
        return date.getTime() // library marker kkossev.commonLib, line 1554
    } catch (e) { // library marker kkossev.commonLib, line 1555
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1556
        return now() // library marker kkossev.commonLib, line 1557
    } // library marker kkossev.commonLib, line 1558
} // library marker kkossev.commonLib, line 1559

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (171) kkossev.batteryLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
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
            input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.batteryLib, line 38
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

// ~~~~~ start include (172) kkossev.temperatureLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.temperatureLib, line 1
library( // library marker kkossev.temperatureLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Temperature Library', name: 'temperatureLib', namespace: 'kkossev', // library marker kkossev.temperatureLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/temperatureLib.groovy', documentationLink: '', // library marker kkossev.temperatureLib, line 4
    version: '3.2.0' // library marker kkossev.temperatureLib, line 5
) // library marker kkossev.temperatureLib, line 6
/* // library marker kkossev.temperatureLib, line 7
 *  Zigbee Temperature Library // library marker kkossev.temperatureLib, line 8
 * // library marker kkossev.temperatureLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.temperatureLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.temperatureLib, line 11
 * // library marker kkossev.temperatureLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.temperatureLib, line 13
 * // library marker kkossev.temperatureLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.temperatureLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.temperatureLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.temperatureLib, line 17
 * // library marker kkossev.temperatureLib, line 18
 * ver. 3.0.0  2024-04-06 kkossev  - added temperatureLib.groovy // library marker kkossev.temperatureLib, line 19
 * ver. 3.0.1  2024-04-19 kkossev  - temperature rounding fix // library marker kkossev.temperatureLib, line 20
 * ver. 3.2.0  2024-05-21 kkossev  - commonLib 3.2.0 allignment // library marker kkossev.temperatureLib, line 21
 * // library marker kkossev.temperatureLib, line 22
 *                                   TODO: // library marker kkossev.temperatureLib, line 23
*/ // library marker kkossev.temperatureLib, line 24

static String temperatureLibVersion()   { '3.2.0' } // library marker kkossev.temperatureLib, line 26
static String temperatureLibStamp() { '2024/05/21 5:04 PM' } // library marker kkossev.temperatureLib, line 27

metadata { // library marker kkossev.temperatureLib, line 29
    capability 'TemperatureMeasurement' // library marker kkossev.temperatureLib, line 30
    // no commands // library marker kkossev.temperatureLib, line 31
    preferences { // library marker kkossev.temperatureLib, line 32
        if (device) { // library marker kkossev.temperatureLib, line 33
            if (settings?.minReportingTime == null) { // library marker kkossev.temperatureLib, line 34
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 35
            } // library marker kkossev.temperatureLib, line 36
            if (settings?.minReportingTime == null) { // library marker kkossev.temperatureLib, line 37
                if (deviceType != 'mmWaveSensor') { // library marker kkossev.temperatureLib, line 38
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.temperatureLib, line 39
                } // library marker kkossev.temperatureLib, line 40
            } // library marker kkossev.temperatureLib, line 41
        } // library marker kkossev.temperatureLib, line 42
    } // library marker kkossev.temperatureLib, line 43
} // library marker kkossev.temperatureLib, line 44

void standardParseTemperatureCluster(final Map descMap) { // library marker kkossev.temperatureLib, line 46
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.temperatureLib, line 47
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.temperatureLib, line 48
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.temperatureLib, line 49
} // library marker kkossev.temperatureLib, line 50

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.temperatureLib, line 52
    Map eventMap = [:] // library marker kkossev.temperatureLib, line 53
    BigDecimal temperature = safeToBigDecimal(temperaturePar).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 54
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.temperatureLib, line 55
    eventMap.name = 'temperature' // library marker kkossev.temperatureLib, line 56
    if (location.temperatureScale == 'F') { // library marker kkossev.temperatureLib, line 57
        temperature = ((temperature * 1.8) + 32).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 58
        eventMap.unit = '\u00B0F' // library marker kkossev.temperatureLib, line 59
    } // library marker kkossev.temperatureLib, line 60
    else { // library marker kkossev.temperatureLib, line 61
        eventMap.unit = '\u00B0C' // library marker kkossev.temperatureLib, line 62
    } // library marker kkossev.temperatureLib, line 63
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 64
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 65
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.temperatureLib, line 66
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.temperatureLib, line 67
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.temperatureLib, line 68
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.temperatureLib, line 69
        return // library marker kkossev.temperatureLib, line 70
    } // library marker kkossev.temperatureLib, line 71
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.temperatureLib, line 72
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.temperatureLib, line 73
    if (state.states['isRefresh'] == true) { // library marker kkossev.temperatureLib, line 74
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.temperatureLib, line 75
        eventMap.isStateChange = true // library marker kkossev.temperatureLib, line 76
    } // library marker kkossev.temperatureLib, line 77
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.temperatureLib, line 78
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 79
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.temperatureLib, line 80
    if (timeElapsed >= minTime) { // library marker kkossev.temperatureLib, line 81
        logInfo "${eventMap.descriptionText}" // library marker kkossev.temperatureLib, line 82
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.temperatureLib, line 83
        state.lastRx['tempTime'] = now() // library marker kkossev.temperatureLib, line 84
        sendEvent(eventMap) // library marker kkossev.temperatureLib, line 85
    } // library marker kkossev.temperatureLib, line 86
    else {         // queue the event // library marker kkossev.temperatureLib, line 87
        eventMap.type = 'delayed' // library marker kkossev.temperatureLib, line 88
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.temperatureLib, line 89
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.temperatureLib, line 90
    } // library marker kkossev.temperatureLib, line 91
} // library marker kkossev.temperatureLib, line 92

void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.temperatureLib, line 94
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.temperatureLib, line 95
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.temperatureLib, line 96
    sendEvent(eventMap) // library marker kkossev.temperatureLib, line 97
} // library marker kkossev.temperatureLib, line 98

List<String> temperatureLibInitializeDevice() { // library marker kkossev.temperatureLib, line 100
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 101
    cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.temperatureLib, line 102
    return cmds // library marker kkossev.temperatureLib, line 103
} // library marker kkossev.temperatureLib, line 104

// ~~~~~ end include (172) kkossev.temperatureLib ~~~~~

// ~~~~~ start include (173) kkossev.humidityLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.humidityLib, line 1
library( // library marker kkossev.humidityLib, line 2
    base: 'driver', // library marker kkossev.humidityLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.humidityLib, line 4
    category: 'zigbee', // library marker kkossev.humidityLib, line 5
    description: 'Zigbee Humidity Library', // library marker kkossev.humidityLib, line 6
    name: 'humidityLib', // library marker kkossev.humidityLib, line 7
    namespace: 'kkossev', // library marker kkossev.humidityLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/humidityLib.groovy', // library marker kkossev.humidityLib, line 9
    version: '3.0.0', // library marker kkossev.humidityLib, line 10
    documentationLink: '' // library marker kkossev.humidityLib, line 11
) // library marker kkossev.humidityLib, line 12
/* // library marker kkossev.humidityLib, line 13
 *  Zigbee Humidity Library // library marker kkossev.humidityLib, line 14
 * // library marker kkossev.humidityLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.humidityLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.humidityLib, line 17
 * // library marker kkossev.humidityLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.humidityLib, line 19
 * // library marker kkossev.humidityLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.humidityLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.humidityLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.humidityLib, line 23
 * // library marker kkossev.humidityLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added humidityLib.groovy // library marker kkossev.humidityLib, line 25
 * // library marker kkossev.humidityLib, line 26
 *                                   TODO: // library marker kkossev.humidityLib, line 27
*/ // library marker kkossev.humidityLib, line 28

static String humidityLibVersion()   { '3.0.0' } // library marker kkossev.humidityLib, line 30
static String humidityLibStamp() { '2024/04/06 11:49 PM' } // library marker kkossev.humidityLib, line 31

metadata { // library marker kkossev.humidityLib, line 33
    capability 'RelativeHumidityMeasurement' // library marker kkossev.humidityLib, line 34
    // no commands // library marker kkossev.humidityLib, line 35
    preferences { // library marker kkossev.humidityLib, line 36
        if (device) { // library marker kkossev.humidityLib, line 37
            if (settings?.minReportingTime == null) { // library marker kkossev.humidityLib, line 38
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.humidityLib, line 39
            } // library marker kkossev.humidityLib, line 40
            if (settings?.minReportingTime == null) { // library marker kkossev.humidityLib, line 41
                if (deviceType != 'mmWaveSensor') { // library marker kkossev.humidityLib, line 42
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.humidityLib, line 43
                } // library marker kkossev.humidityLib, line 44
            } // library marker kkossev.humidityLib, line 45
        } // library marker kkossev.humidityLib, line 46
    } // library marker kkossev.humidityLib, line 47
} // library marker kkossev.humidityLib, line 48

void customParseHumidityCluster(final Map descMap) { // library marker kkossev.humidityLib, line 50
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.humidityLib, line 51
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.humidityLib, line 52
    handleHumidityEvent(value / 100.0F as BigDecimal) // library marker kkossev.humidityLib, line 53
} // library marker kkossev.humidityLib, line 54

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) { // library marker kkossev.humidityLib, line 56
    Map eventMap = [:] // library marker kkossev.humidityLib, line 57
    BigDecimal humidity = safeToBigDecimal(humidityPar) // library marker kkossev.humidityLib, line 58
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.humidityLib, line 59
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0) // library marker kkossev.humidityLib, line 60
    if (humidity <= 0.0 || humidity > 100.0) { // library marker kkossev.humidityLib, line 61
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})" // library marker kkossev.humidityLib, line 62
        return // library marker kkossev.humidityLib, line 63
    } // library marker kkossev.humidityLib, line 64
    eventMap.value = humidity.setScale(0, BigDecimal.ROUND_HALF_UP) // library marker kkossev.humidityLib, line 65
    eventMap.name = 'humidity' // library marker kkossev.humidityLib, line 66
    eventMap.unit = '% RH' // library marker kkossev.humidityLib, line 67
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.humidityLib, line 68
    //eventMap.isStateChange = true // library marker kkossev.humidityLib, line 69
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.humidityLib, line 70
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.humidityLib, line 71
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.humidityLib, line 72
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.humidityLib, line 73
    if (timeElapsed >= minTime) { // library marker kkossev.humidityLib, line 74
        logInfo "${eventMap.descriptionText}" // library marker kkossev.humidityLib, line 75
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.humidityLib, line 76
        state.lastRx['humiTime'] = now() // library marker kkossev.humidityLib, line 77
        sendEvent(eventMap) // library marker kkossev.humidityLib, line 78
    } // library marker kkossev.humidityLib, line 79
    else { // library marker kkossev.humidityLib, line 80
        eventMap.type = 'delayed' // library marker kkossev.humidityLib, line 81
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.humidityLib, line 82
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.humidityLib, line 83
    } // library marker kkossev.humidityLib, line 84
} // library marker kkossev.humidityLib, line 85

void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.humidityLib, line 87
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.humidityLib, line 88
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.humidityLib, line 89
    sendEvent(eventMap) // library marker kkossev.humidityLib, line 90
} // library marker kkossev.humidityLib, line 91

List<String> humidityLibInitializeDevice() { // library marker kkossev.humidityLib, line 93
    List<String> cmds = [] // library marker kkossev.humidityLib, line 94
    cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.humidityLib, line 95
    return cmds // library marker kkossev.humidityLib, line 96
} // library marker kkossev.humidityLib, line 97

// ~~~~~ end include (173) kkossev.humidityLib ~~~~~

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
 * ver. 3.2.0  2024-05-21 kkossev  - commonLib 3.2.0 allignment; added capability 'IlluminanceMeasurement' // library marker kkossev.illuminanceLib, line 21
 * // library marker kkossev.illuminanceLib, line 22
 *                                   TODO: illum threshold not working! // library marker kkossev.illuminanceLib, line 23
 *                                   TODO: check illuminanceInitializeVars() and illuminanceProcessTuyaDP() usage // library marker kkossev.illuminanceLib, line 24
*/ // library marker kkossev.illuminanceLib, line 25

static String illuminanceLibVersion()   { '3.2.0' } // library marker kkossev.illuminanceLib, line 27
static String illuminanceLibStamp() { '2024/05/27 11:47 PM' } // library marker kkossev.illuminanceLib, line 28

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

// ~~~~~ end include (168) kkossev.illuminanceLib ~~~~~

// ~~~~~ start include (178) kkossev.iasLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.iasLib, line 1
library( // library marker kkossev.iasLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee IASLibrary', name: 'iasLib', namespace: 'kkossev', // library marker kkossev.iasLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/iasLib.groovy', documentationLink: '', // library marker kkossev.iasLib, line 4
    version: '3.2.0' // library marker kkossev.iasLib, line 5

) // library marker kkossev.iasLib, line 7
/* // library marker kkossev.iasLib, line 8
 *  Zigbee IAS Library // library marker kkossev.iasLib, line 9
 * // library marker kkossev.iasLib, line 10
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.iasLib, line 11
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.iasLib, line 12
 * // library marker kkossev.iasLib, line 13
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.iasLib, line 14
 * // library marker kkossev.iasLib, line 15
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.iasLib, line 16
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.iasLib, line 17
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.iasLib, line 18
 * // library marker kkossev.iasLib, line 19
 * ver. 3.2.0  2024-05-27 kkossev  - added iasLib.groovy // library marker kkossev.iasLib, line 20
 * // library marker kkossev.iasLib, line 21
 *                                   TODO: // library marker kkossev.iasLib, line 22
*/ // library marker kkossev.iasLib, line 23

static String iasLibVersion()   { '3.2.0' } // library marker kkossev.iasLib, line 25
static String iasLibStamp() { '2024/05/27 10:13 PM' } // library marker kkossev.iasLib, line 26

metadata { // library marker kkossev.iasLib, line 28
    // no capabilities // library marker kkossev.iasLib, line 29
    // no attributes // library marker kkossev.iasLib, line 30
    // no commands // library marker kkossev.iasLib, line 31
    preferences { // library marker kkossev.iasLib, line 32
    // no prefrences // library marker kkossev.iasLib, line 33
    } // library marker kkossev.iasLib, line 34
} // library marker kkossev.iasLib, line 35

@Field static final Map<Integer, String> IAS_ATTRIBUTES = [ // library marker kkossev.iasLib, line 37
    //  Zone Information // library marker kkossev.iasLib, line 38
    0x0000: 'zone state', // library marker kkossev.iasLib, line 39
    0x0001: 'zone type', // library marker kkossev.iasLib, line 40
    0x0002: 'zone status', // library marker kkossev.iasLib, line 41
    //  Zone Settings // library marker kkossev.iasLib, line 42
    0x0010: 'CIE addr',    // EUI64 // library marker kkossev.iasLib, line 43
    0x0011: 'Zone Id',     // uint8 // library marker kkossev.iasLib, line 44
    0x0012: 'Num zone sensitivity levels supported',     // uint8 // library marker kkossev.iasLib, line 45
    0x0013: 'Current zone sensitivity level',            // uint8 // library marker kkossev.iasLib, line 46
    0xF001: 'Current zone keep time'                     // uint8 // library marker kkossev.iasLib, line 47
] // library marker kkossev.iasLib, line 48

@Field static final Map<Integer, String> ZONE_TYPE = [ // library marker kkossev.iasLib, line 50
    0x0000: 'Standard CIE', // library marker kkossev.iasLib, line 51
    0x000D: 'Motion Sensor', // library marker kkossev.iasLib, line 52
    0x0015: 'Contact Switch', // library marker kkossev.iasLib, line 53
    0x0028: 'Fire Sensor', // library marker kkossev.iasLib, line 54
    0x002A: 'Water Sensor', // library marker kkossev.iasLib, line 55
    0x002B: 'Carbon Monoxide Sensor', // library marker kkossev.iasLib, line 56
    0x002C: 'Personal Emergency Device', // library marker kkossev.iasLib, line 57
    0x002D: 'Vibration Movement Sensor', // library marker kkossev.iasLib, line 58
    0x010F: 'Remote Control', // library marker kkossev.iasLib, line 59
    0x0115: 'Key Fob', // library marker kkossev.iasLib, line 60
    0x021D: 'Key Pad', // library marker kkossev.iasLib, line 61
    0x0225: 'Standard Warning Device', // library marker kkossev.iasLib, line 62
    0x0226: 'Glass Break Sensor', // library marker kkossev.iasLib, line 63
    0x0229: 'Security Repeater', // library marker kkossev.iasLib, line 64
    0xFFFF: 'Invalid Zone Type' // library marker kkossev.iasLib, line 65
] // library marker kkossev.iasLib, line 66

@Field static final Map<Integer, String> ZONE_STATE = [ // library marker kkossev.iasLib, line 68
    0x00: 'Not Enrolled', // library marker kkossev.iasLib, line 69
    0x01: 'Enrolled' // library marker kkossev.iasLib, line 70
] // library marker kkossev.iasLib, line 71

public void standardParseIASCluster(final Map descMap) { // library marker kkossev.iasLib, line 73
    if (descMap.cluster != '0500') { return } // not IAS cluster // library marker kkossev.iasLib, line 74
    if (descMap.attrInt == null) { return } // missing attribute // library marker kkossev.iasLib, line 75
    String zoneSetting = IAS_ATTRIBUTES[descMap.attrInt] // library marker kkossev.iasLib, line 76
    if ( IAS_ATTRIBUTES[descMap.attrInt] == null ) { // library marker kkossev.iasLib, line 77
        logWarn "standardParseIASCluster: Unknown IAS attribute ${descMap?.attrId} (value:${descMap?.value})" // library marker kkossev.iasLib, line 78
        return  // library marker kkossev.iasLib, line 79
    } // unknown IAS attribute // library marker kkossev.iasLib, line 80
    logDebug "standardParseIASCluster: Don't know how to handle IAS attribute 0x${descMap?.attrId} '${zoneSetting}' (value:${descMap?.value})!" // library marker kkossev.iasLib, line 81
    return // library marker kkossev.iasLib, line 82
/* // library marker kkossev.iasLib, line 83
    String clusterInfo = 'standardParseIASCluster:' // library marker kkossev.iasLib, line 84

    if (descMap?.cluster == '0500' && descMap?.command in ['01', '0A']) {    //IAS read attribute response // library marker kkossev.iasLib, line 86
        logDebug "${standardParseIASCluster} IAS read attribute ${descMap?.attrId} response is ${descMap?.value}" // library marker kkossev.iasLib, line 87
        if (descMap?.attrId == '0000') { // library marker kkossev.iasLib, line 88
            int value = Integer.parseInt(descMap?.value, 16) // library marker kkossev.iasLib, line 89
            logInfo "${clusterInfo} IAS Zone State repot is '${ZONE_STATE[value]}' (${value})" // library marker kkossev.iasLib, line 90
            } else if (descMap?.attrId == '0001') { // library marker kkossev.iasLib, line 91
            int value = Integer.parseInt(descMap?.value, 16) // library marker kkossev.iasLib, line 92
            logInfo "${clusterInfo} IAS Zone Type repot is '${ZONE_TYPE[value]}' (${value})" // library marker kkossev.iasLib, line 93
            } else if (descMap?.attrId == '0002') { // library marker kkossev.iasLib, line 94
            logDebug "${clusterInfo} IAS Zone status repoted: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}" // library marker kkossev.iasLib, line 95
            handleMotion(Integer.parseInt(descMap?.value, 16) ? true : false) // library marker kkossev.iasLib, line 96
            } else if (descMap?.attrId == '0010') { // library marker kkossev.iasLib, line 97
            logDebug "${clusterInfo} IAS Zone Address received (bitmap = ${descMap?.value})" // library marker kkossev.iasLib, line 98
            } else if (descMap?.attrId == '0011') { // library marker kkossev.iasLib, line 99
            logDebug "${clusterInfo} IAS Zone ID: ${descMap.value}" // library marker kkossev.iasLib, line 100
            } else if (descMap?.attrId == '0012') { // library marker kkossev.iasLib, line 101
            logDebug "${clusterInfo} IAS Num zone sensitivity levels supported: ${descMap.value}" // library marker kkossev.iasLib, line 102
            } else if (descMap?.attrId == '0013') { // library marker kkossev.iasLib, line 103
            int value = Integer.parseInt(descMap?.value, 16) // library marker kkossev.iasLib, line 104
            //logInfo "${clusterInfo} IAS Current Zone Sensitivity Level = ${sensitivityOpts.options[value]} (${value})" // library marker kkossev.iasLib, line 105
            logInfo "${clusterInfo} IAS Current Zone Sensitivity Level = (${value})" // library marker kkossev.iasLib, line 106
        // device.updateSetting('settings.sensitivity', [value:value.toString(), type:'enum']) // library marker kkossev.iasLib, line 107
        } // library marker kkossev.iasLib, line 108
            else if (descMap?.attrId == 'F001') {    // [raw:7CC50105000801F02000, dni:7CC5, endpoint:01, cluster:0500, size:08, attrId:F001, encoding:20, command:0A, value:00, clusterInt:1280, attrInt:61441] // library marker kkossev.iasLib, line 109
            int value = Integer.parseInt(descMap?.value, 16) // library marker kkossev.iasLib, line 110
            //String str   = getKeepTimeOpts().options[value] // library marker kkossev.iasLib, line 111
            //logInfo "${clusterInfo} Current IAS Zone Keep-Time =  ${str} (${value})" // library marker kkossev.iasLib, line 112
            logInfo "${clusterInfo} Current IAS Zone Keep-Time =  (${value})" // library marker kkossev.iasLib, line 113
            //device.updateSetting('keepTime', [value: value.toString(), type: 'enum']) // library marker kkossev.iasLib, line 114
            } // library marker kkossev.iasLib, line 115
            else { // library marker kkossev.iasLib, line 116
            logDebug "${clusterInfo} Zone status attribute ${descMap?.attrId}: NOT PROCESSED ${descMap}" // library marker kkossev.iasLib, line 117
            } // library marker kkossev.iasLib, line 118
        } // if IAS read attribute response // library marker kkossev.iasLib, line 119
        else if (descMap?.clusterId == '0500' && descMap?.command == '04') {    //write attribute response (IAS) // library marker kkossev.iasLib, line 120
        logDebug "${clusterInfo} AS write attribute response is ${descMap?.data[0] == '00' ? 'success' : '<b>FAILURE</b>'}" // library marker kkossev.iasLib, line 121
        } // library marker kkossev.iasLib, line 122
        else { // library marker kkossev.iasLib, line 123
        logDebug "${clusterInfo} NOT PROCESSED ${descMap}" // library marker kkossev.iasLib, line 124
        } // library marker kkossev.iasLib, line 125
*/         // library marker kkossev.iasLib, line 126
} // library marker kkossev.iasLib, line 127

// ~~~~~ end include (178) kkossev.iasLib ~~~~~
