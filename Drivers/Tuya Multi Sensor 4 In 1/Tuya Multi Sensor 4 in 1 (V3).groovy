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
 * ver. 3.2.0  2024-05-26 kkossev  - (dev.branch) first version, based on the mmWave radar driver code : depricated Linptech; added TS0202 add _TYZB01_vwqnz1sn; 
 * ver. 3.2.1  2024-05-29 kkossev  - (dev.branch) tested 2In1 _TZE200_3towulqd ; new device profile group 'RH3040_TUYATEC'
 *                                   
 *                                   TODO: test TUYATEC-53o41joc IAS - add refresh commands (battery not reported when paired!);
 *                                   TODO: temperature and humidity thresholds SIHAS exception : List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1118
 *                                   TODO: battery events delay
 *                                   TODO: add powerSource attribute
 *                                   TODO: 'all' status preference and attribute
 *                                   TODO: temperature and humidity calibration (offsets)
 *                                   TODO: for 4IN1 (Fantem) - add in refresh() : cmds += zigbee.command(0xEF00, 0x07, '00')    // Fantem Tuya Magic
 *                                   TODO: for Tuya- add in refresh() : cmds += zigbee.command(0xEF00, 0x03)
 *                                   TODO: battery level for TS0202 and TS0601 2in1 ; battery1 for Fantem 4-in-1 (100% or 0% ) Battery level for _TZE200_3towulqd (2in1)
 *                                   TODO: https://community.hubitat.com/t/moes-tuya-motion-sensor-distance-issue-ts0202-have-to-be-ridiculously-close-to-detect-movement/109917/8?u=kkossev 
 *                                   TODO: make new GitHub WiKi 
 *                                   TODO: add the state tuyaDps as in the 4-in-1 driver!
 *                                   TODO: cleanup the 4-in-1 state variables.
 *                                   TODO: illumState default value is 0 - should be 'unknown' ?
 *                                   TODO: Motion reset to inactive after 43648s - convert to H:M:S
*/

static String version() { "3.2.1" }
static String timeStamp() {"2024/05/30 10:36 AM"}

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

#include kkossev.deviceProfileLib
#include kkossev.commonLib
#include kkossev.batteryLib
#include kkossev.temperatureLib
#include kkossev.humidityLib
#include kkossev.illuminanceLib
#include kkossev.iasLib

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
            if (('DistanceMeasurement' in DEVICE?.capabilities)) {  // keep it because of the depricated mmWave sensors
                input(name: 'ignoreDistance', type: 'bool', title: '<b>Ignore distance reports</b>', description: 'If not used, ignore the distance reports received every 1 second!', defaultValue: true)
            }
            if (advancedOptions == true) {
                if ('invertMotion' in DEVICE?.preferences) {
                    input(name: 'invertMotion', type: 'bool', title: '<b>Invert Motion Active/Not Active</b>', description: '<i>Some Tuya motion sensors may report the motion active/inactive inverted...</i>', defaultValue: false)
                }
            }
        }

        // the rest of the preferences are inputIt from the deviceProfileLib 
    }
}

@Field static String ttStyleStr = '<style>.tTip {display:inline-block;border-bottom: 1px dotted black;}.tTip .tTipText {display:none;border-radius: 6px;padding: 5px 0;position: absolute;z-index: 1;}.tTip:hover .tTipText {display:inline-block;background-color:red;color:red;}</style>'

boolean is4in1() { return getDeviceProfile().contains('TS0202_4IN1') }
//boolean is2in1() { return getDeviceProfile().contains('TS0601_2IN1') } defined in the deviceProfileLib, because of a patch!


// based on 'Tuya Multi Sensor 4 In 1' version '1.9.0' '2024/05/06 10:39 AM' 
@Field static final Map deviceProfilesV3 = [
    // is4in1() // tested _TZ3210_zmy9hjay - OK
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
                //            ^^^TODO^^^ (preProc 0..3 -> 1..100)
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

    // is2in1() // tested  _TZE200_3towulqd - OK (w/ patch!)
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
                [dp:1,   name:'motion', /*preProc:'invert',*/ type:'enum',   rw: 'ro', min:0, max:1 ,   defVal:'0',  scale:1,  map:[0:'inactive', 1:'active'] ,   unit:'',  description:'<i>Motion</i>'],
                [dp:4,   name:'battery',                  type:'number', rw: 'ro', min:0, max:100,  defVal:100,  scale:1,  unit:'%',          title:'<b>Battery level</b>',              description:'<i>Battery level</i>'],
                [dp:9,   name:'sensitivity',              type:'enum',   rw: 'rw', min:0, max:2,    defVal:'2',  unit:'',           map:[0:'0 - low', 1:'1 - medium', 2:'2 - high'], title:'<b>Sensitivity</b>',   description:'<i>PIR sensor sensitivity (update at the time motion is activated)</i>'],
                [dp:10,  name:'keepTime',                 type:'enum',   rw: 'rw', min:0, max:3,    defVal:'0',  unit:'seconds',    map:[0:'10 seconds', 1:'30 seconds', 2:'60 seconds', 3:'120 seconds'], title:'<b>Keep Time</b>',   description:'<i>PIR keep time in seconds (update at the time motion is activated)</i>'],
                [dp:12,  name:'illuminance',              type:'number', rw: 'ro', min:0, max:1000, defVal:0,    scale:1,  unit:'lx',       title:'<b>illuminance</b>',     description:'<i>illuminance</i>'],
                [dp:102, name:'illuminance_interval',     type:'number', rw: 'rw', min:1, max:720,  defVal:1,    scale:1,  unit:'minutes',  title:'<b>Illuminance Interval</b>',     description:'<i>Brightness acquisition interval (update at the time motion is activated)</i>'],

            ],
            deviceJoinName: 'Tuya Multi Sensor 2 In 1',
            configuration : ['battery': false]
    ],

    'RH3040_TUYATEC'   : [ // testing TUYATEC-53o41joc   // non-configurable
            description   : 'TuyaTec RH3040 Motion sensor (IAS)',
            models        : ['RH3040'],
            device        : [type: 'PIR', isIAS:true, powerSource: 'battery', isSleepy:true],
            capabilities  : ['MotionSensor': true, 'Battery': true],
            preferences   : ['motionReset':false, 'keepTime':false, 'sensitivity':false],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-53o41joc', deviceJoinName: 'TUYATEC RH3040 Motion Sensor'],                                            // KK - 60 seconds reset period
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-b5g40alm', deviceJoinName: 'TUYATEC RH3040 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-deetibst', deviceJoinName: 'TUYATEC RH3040 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-bd5faf9p', deviceJoinName: 'Nedis/Samotech RH3040 Motion Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-zn9wyqtr', deviceJoinName: 'Samotech RH3040 Motion Sensor'],                                           // vendor: 'Samotech', model: 'SM301Z'
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-b3ov3nor', deviceJoinName: 'Zemismart RH3040 Motion Sensor'],                                          // vendor: 'Nedis', model: 'ZBSM10WT'
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500', model:'RH3040', manufacturer:'TUYATEC-2gn2zf9e', deviceJoinName: 'TUYATEC RH3040 Motion Sensor']
            ],
            deviceJoinName: 'TuyaTec RH3040 Motion sensor (IAS)',
            configuration : ['battery': false]
    ],

    'TS0202_MOTION_IAS'   : [ // non-configurable
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
    
    if (settings.invertMotion == true) {    // patch!! fix it!
        motionActiveCopy = !motionActiveCopy
    }
    
    //log.trace "handleMotion: motionActive=${motionActiveCopy}, isDigital=${isDigital}"
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

// battery level for TS0202 and TS0601 2in1 ; battery1 for Fantem 4-in-1 (100% or 0% ) Battery level for _TZE200_3towulqd (2in1)
void handleTuyaBatteryLevel(int fncmd) {
    int rawValue = fncmd
    switch (fncmd) {
        case 0: rawValue = 100; break // Battery Full
        case 1: rawValue = 75;  break // Battery High
        case 2: rawValue = 50;  break // Battery Medium
        case 3: rawValue = 25;  break // Battery Low
        case 4: rawValue = 100; break // Tuya 3 in 1 -> USB powered
    }
    getBatteryPercentageResult(rawValue * 2)
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
void customProcessDeviceProfileEvent(final Map descMap, final String name, valueScaled, final String unitText, final String descText) {
    logTrace "customProcessDeviceProfileEvent(${name}, ${valueScaled}) called"
    boolean doNotTrace = isSpammyDPsToNotTrace(descMap)
    Map eventMap = [name: name, value: valueScaled, unit: unitText, descriptionText: descText, type: 'physical', isStateChange: true]
    switch (name) {
        case 'motion' :
            logTrace "customProcessDeviceProfileEvent: motion event received deviceProfile is ${getDeviceProfile()} valueScaled=${valueScaled} as boolean=${(valueScaled as boolean) }"
            handleMotion(valueScaled == 'active' ? true : false)  // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
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
    if (fullInit == true || settings.invertMotion == null) device.updateSetting('invertMotion', is2in1() ? true : false)
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

