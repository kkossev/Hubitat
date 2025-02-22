/* groovylint-disable NglParseError, ImplicitReturnStatement, InsecureRandom, MethodReturnTypeRequired, MethodSize, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnusedImport, VariableName */
/**
 *  Tuya Multi Sensor 4 In 1 driver for Hubitat
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *  https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-w-healthstatus/92441
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
 * ver. 3.2.0  2024-05-26 kkossev  - first version, based on the mmWave radar driver code : depricated Linptech; added TS0202 add _TYZB01_vwqnz1sn; 
 * ver. 3.2.1  2024-05-31 kkossev  - commonLib ver 3.2.1 allignment; tested 2In1 _TZE200_3towulqd ; new device profile group 'RH3040_TUYATEC'; SiHAS; 
 * ver. 3.2.2  2024-07-05 kkossev  - created motionLib; restored 'all' attribute
 * ver. 3.2.3  2024-07-27 kkossev  - added Sonoff SNZB-03P
 * ver. 3.3.0  2024-08-30 kkossev  - main branch release.
 * ver. 3.3.1  2024-10-26 kkossev  - added TS0601 _TZE200_f1pvdgoh into a new device profile group 'TS0601_2IN1_MYQ_ZMS03'
 * ver. 3.3.2  2024-11-30 kkossev  - added Azoula Zigbee 4 in 1 Multi Sensor model:'HK-SENSOR-4IN1-A', manufacturer:'Sunricher' into SIHAS group
 * ver. 3.3.3  2025-01-29 kkossev  - TS0601 _TZE200_ppuj1vem moved to 'TS0601_2IN1_MYQ_ZMS03' deviceProfile @ltdonjohnson
 * ver. 3.3.4  2025-02-22 kkossev  - (dev. branch) adding Espressif ZigbeeOccupancyPIRSensor @ilkeraktuna
 *                                   
 *                                   TODO: add TS0601 _TZE200_agumlajc https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-w-healthstatus/92441/1077?u=kkossev
 *                                   TODO: Sensor 3in1 _warning: couldn't find map for preference motionReset
 *                                   TODO: Sensor 3in1 _TZE200_7hfcudw5 - fix battery percentage (shows 4)
 *                                   TODO: test TUYATEC-53o41joc IAS - add refresh commands (battery not reported when paired!);
 *                                   TODO: temperature and humidity thresholds SIHAS exception : List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1118
 *                                   TODO: temperature and humidity calibration (offsets)
 *                                   TODO: for 4IN1 (Fantem) - add in refresh() : cmds += zigbee.command(0xEF00, 0x07, '00')    // Fantem Tuya Magic
 *                                   TODO: TS0601_3IN1 - process Battery/USB powerSource change events! (0..4)
 *                                   TODO: for Tuya- add in refresh() : cmds += zigbee.command(0xEF00, 0x03)
 *                                   TODO: battery level for TS0202 and TS0601 2in1 ; battery1 for Fantem 4-in-1 (100% or 0% ) Battery level for _TZE200_3towulqd (2in1)
 *                                   TODO: https://community.hubitat.com/t/moes-tuya-motion-sensor-distance-issue-ts0202-have-to-be-ridiculously-close-to-detect-movement/109917/8?u=kkossev 
 *                                   TODO: publish examples of SetPar usage : https://community.hubitat.com/t/4-in-1-parameter-for-adjusting-reporting-time/115793/12?u=kkossev
 *                                   TODO: check why only voltage is reported for SONOFF_MOTION_IAS;
 *                                   TODO: hide motionKeepTime and motionSensitivity for SONOFF_MOTION_IAS;
 *                                   TODO: if isSleepy - store in state.cmds and send when the device wakes up!  (on both update() and refresh()
 *                                   TODO: TS0202_MOTION_IAS missing sensitivity and retrigger time settings bug fix;
 *                                   TOOD: Tuya 2in1 illuminance_interval (dp=102) !
 *                                   TODO: use getKeepTimeOpts() for processing dp=0x0A (10) keep time ! ( 2-in-1 time is wrong)
 *                                   TODO: check the bindings commands in configure()
 *                                   TODO: ignore invalid humidity reprots (>100 %)
 *                                   TODO: add the state tuyaDps as in the 4-in-1 driver!
 *                                   TODO: delete all previous preferencies when changing the device profile ?
 *                                   TODO: Motion reset to inactive after 43648s - convert to H:M:S
 *                                   TODO: check temperatureOffset and humidityOffset
*/

static String version() { "3.3.4" }
static String timeStamp() {"2025/02/22 7:15 PM"}

@Field static final Boolean _DEBUG = false
@Field static final Boolean _TRACE_ALL = false              // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true    // disable it for the production release !


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
        name: 'Tuya Multi Sensor 4 In 1',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Multi%20Sensor%204%20In%201/Tuya%20Multi%20Sensor%204%20In%201.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {

        capability 'MotionSensor'
        //capability 'TamperAlert'

        attribute 'all', 'string'                   // all attributes in one string
        attribute 'distance', 'number'              // Tuya Radar, obsolete
        attribute 'unacknowledgedTime', 'number'    // AIR models
        attribute 'keepTime', 'enum', ['10 seconds', '30 seconds', '60 seconds', '120 seconds']
        attribute 'motionDetectionDistance', 'decimal'  // changed 05/11/2024 - was 'number'

        attribute 'sensitivity', 'number'
        attribute 'fadingTime', 'decimal'
        attribute 'humanMotionState', 'enum', ['none', 'moving', 'small_move', 'stationary', 'static', 'presence', 'peaceful', 'large_move']    // in use by the obsolete radars
        attribute 'illumState', 'enum', ['dark', 'light', 'unknown']
        attribute 'ledIndicator', 'number'
        attribute 'reportingTime4in1', 'number'
        attribute 'ledEnable', 'enum', ['disabled', 'enabled']
        attribute 'WARNING', 'string'

        // command 'setMotion' is defined in motionLib
        // version 3.3.0
        command 'sendCommand', [
            [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']],
            [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']]
        ]
        command 'setPar', [
                [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']],
                [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']]
        ]       

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
            else {
                input(name: 'info',    type: 'hidden', title: "<a href='https://github.com/kkossev/Hubitat/wiki/Tuya-Multi-Sensor-4-In-1' target='_blank'><i>For more info, click on this link to visit the WiKi page</i></a>")
            }
        }
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: 'Enables events logging.'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_DEBUG_LOGGING, description: 'Turns on debug logging for 24 hours.'
        if (device) {
            if (('DistanceMeasurement' in DEVICE?.capabilities)) {  // keep it because of the depricated mmWave sensors
                input(name: 'ignoreDistance', type: 'bool', title: '<b>Ignore distance reports</b>', description: 'If not used, ignore the distance reports received every 1 second!', defaultValue: true)
            }
        }
        input(name: 'allStatusTextEnable', type:  'bool', title: "<b>Enable 'all' Status Attribute Creation?</b>",  description: 'Status attribute for Devices/Rooms', defaultValue: false)
        // the rest of the preferences are inputIt from the deviceProfileLib and from the included libraries
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
            preferences   : ['motionReset':true, 'illuminanceThreshold':true, 'reportingTime4in1':'102', 'ledEnable':'111', 'keepTime':'0x0500:0xF001', 'sensitivity':'0x0500:0x0013'],
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
                [dp:1,   name:'motion',                                 type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'inactive', 1:'active'] ,   unit:'',  description:'Motion'],
                [dp:5,   name:'tamper',                                 type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'clear', 1:'detected'] ,   unit:'',  description:'Tamper detection'],
                [dp:25,  name:'battery2',                               type:'number',  rw: 'ro', min:0,     max:100,  defVal:100,  scale:1,  unit:'%',          description:'Remaining battery 2 in %'],
                [dp:102, name:'reportingTime4in1', dt:'02', tuyaCmd:04, type:'number',  rw: 'rw', min:0, max:240, defVal:10, step:5, scale:1, unit:'minutes', title:'<b>Reporting Interval</b>', description:'Reporting interval in minutes'],
                [dp:104, name:'tempCalibration',                        type:'decimal', rw: 'ro', min:-2.0,  max:2.0,  defVal:0.0,  scale:10, unit:'deg.',  title:'<b>Temperature Calibration</b>',       description:'Temperature calibration (-2.0...2.0)'],
                [dp:105, name:'humiCalibration',                        type:'number',  rw: 'ro', min:-15,   max:15,   defVal:0,    scale:1,  unit:'%RH',    title:'<b>Huidity Calibration</b>',     description:'Humidity Calibration'],
                [dp:106, name:'illumCalibration',                       type:'number',  rw: 'ro', min:-20, max:20, defVal:0,        scale:1, unit:'Lx', title:'<b>Illuminance Calibration</b>', description:'Illuminance calibration in lux'],
                [dp:107, name:'temperature',                            type:'decimal', rw: 'ro', min:-20.0, max:80.0, defVal:0.0,  scale:10, unit:'deg.',       description:'Temperature'],
                [dp:108, name:'humidity',                               type:'number',  rw: 'ro', min:1,     max:100,  defVal:100,  scale:1,  unit:'%RH',        description:'Humidity'],
                [dp:109, name:'pirSensorEnable',                        type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'1',  scale:1,  map:[0:'disabled', 1:'enabled'] ,   unit:'', title:'<b>MoPIR Sensor Enable</b>',  description:'Enable PIR sensor'],
                [dp:110, name:'battery',                                type:'number',  rw: 'ro', min:0,     max:100,  defVal:100,  scale:1,  unit:'%',          description:'Battery level'],
                [dp:111, name:'ledEnable',       dt:'01', tuyaCmd:04,   type:'enum',    rw: 'rw', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'disabled', 1:'enabled'] ,   unit:'', title:'<b>LED Enable</b>',  description:'Enable LED'],
                [dp:112, name:'reportingEnable',                        type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'disabled', 1:'enabled'] ,   unit:'', title:'<b>Reporting Enable</b>',  description:'Enable reporting'],
            ],
            attributes:       [
                [at:'0x0500:0x0013',  name:'sensitivity', type:'enum',    rw: 'rw', min:0,     max:2,    defVal:'2',  unit:'',           map:[0:'low', 1:'medium', 2:'high'], title:'<b>Sensitivity</b>',   description:'PIR sensor sensitivity (update at the time motion is activated)'],
                [at:'0x0500:0xF001',  name:'keepTime',    type:'enum',    rw: 'rw', min:0,     max:5,    defVal:'0',  unit:'seconds',    map:[0:'0 seconds', 1:'30 seconds', 2:'60 seconds', 3:'120 seconds', 4:'240 seconds', 5:'480 seconds'], title:'<b>Keep Time</b>',   description:'PIR keep time in seconds (update at the time motion is activated)']
            ],
            refresh:        ['refreshAllIas','sensitivity', 'keepTime', 'refreshFantem'],
            configuration : ['battery': false],
            deviceJoinName: 'Tuya Multi Sensor 4 In 1'
    ],

    // tested TS0601  _TZE200_7hfcudw5 - OK
    'TS0601_3IN1'  : [                                // https://szneo.com/en/products/show.php?id=239 // https://www.banggood.com/Tuya-Smart-Linkage-ZB-Motion-Sensor-Human-Infrared-Detector-Mobile-Phone-Remote-Monitoring-PIR-Sensor-p-1858413.html?cur_warehouse=CN
            description   : 'Tuya 3in1 (Motion/Temp/Humi) sensor',
            models        : ['TS0601'],
            device        : [type: 'PIR', powerSource: 'dc', isSleepy: false],    //  powerSource changes batt/DC dynamically!
            capabilities  : ['MotionSensor': true, 'TemperatureMeasurement': true, 'RelativeHumidityMeasurement': true, 'tamper': true, 'Battery': true],
            preferences   : ['motionReset':true],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_7hfcudw5', deviceJoinName: 'Tuya NAS-PD07 Multi Sensor 3 In 1'],
            ],
            tuyaDPs:        [
                [dp:101, name:'motion',          type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'inactive', 1:'active'], description:'Motion'],
                [dp:102, name:'battery', preProc:'tuyaToBatteryLevel', type:'number',  rw: 'ro', min:0,     max:100,  defVal:100,  scale:1,  unit:'%',          description:'Battery level'],
                [dp:103, name:'tamper',          type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'clear', 1:'detected'] ,   unit:'',  description:'Tamper detection'],
                [dp:104, name:'temperature',     type:'decimal', rw: 'ro', min:-20.0, max:80.0, defVal:0.0,  scale:10, unit:'deg.',       description:'Temperature'],
                [dp:105, name:'humidity',        type:'number',  rw: 'ro', min:1,     max:100,  defVal:100,  scale:1,  unit:'%RH',        description:'Humidity'],
                [dp:106, name:'tempScale',       type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'Celsius', 1:'Fahrenheit'] ,   unit:'',  description:'Temperature scale'],
                [dp:107, name:'minTemp',         type:'number',  rw: 'ro', min:-20,   max:80,   defVal:0,    scale:1,  unit:'deg.',       description:'Minimal temperature'],
                [dp:108, name:'maxTemp',         type:'number',  rw: 'ro', min:-20,   max:80,   defVal:0,    scale:1,  unit:'deg.',       description:'Maximal temperature'],
                [dp:109, name:'minHumidity',     type:'number',  rw: 'ro', min:0,     max:100,  defVal:0,    scale:1,  unit:'%RH',        description:'Minimal humidity'],
                [dp:110, name:'maxHumidity',     type:'number',  rw: 'ro', min:0,     max:100,  defVal:0,    scale:1,  unit:'%RH',        description:'Maximal humidity'],
                [dp:111, name:'tempAlarm',       type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'inactive', 1:'active'] ,   unit:'',  description:'Temperature alarm'],
                [dp:112, name:'humidityAlarm',   type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'inactive', 1:'active'] ,   unit:'',  description:'Humidity alarm'],
                [dp:113, name:'alarmType',       type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'type0', 1:'type1'] ,   unit:'',  description:'Alarm type'],
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
                [dp:1,   name:'motion', /*preProc:'invert',*/ type:'enum',   rw: 'ro', min:0, max:1 ,   defVal:'0',  scale:1,  map:[0:'inactive', 1:'active'] ,   unit:'',  description:'Motion'],
                [dp:4,   name:'battery',                  type:'number', rw: 'ro', min:0, max:100,  defVal:100,  scale:1,  unit:'%',          title:'<b>Battery level</b>',              description:'Battery level'],
                [dp:9,   name:'sensitivity',              type:'enum',   rw: 'rw', min:0, max:2,    defVal:'2',  unit:'',           map:[0:'0 - low', 1:'1 - medium', 2:'2 - high'], title:'<b>Sensitivity</b>',   description:'PIR sensor sensitivity (update at the time motion is activated)'],
                [dp:10,  name:'keepTime',                 type:'enum',   rw: 'rw', min:0, max:3,    defVal:'0',  unit:'seconds',    map:[0:'10 seconds', 1:'30 seconds', 2:'60 seconds', 3:'120 seconds'], title:'<b>Keep Time</b>',   description:'PIR keep time in seconds (update at the time motion is activated)'],
                [dp:12,  name:'illuminance',              type:'number', rw: 'ro', min:0, max:1000, defVal:0,    scale:1,  unit:'lx',       title:'<b>illuminance</b>',     description:'illuminance'],
                [dp:102, name:'illuminance_interval',     type:'number', rw: 'rw', min:1, max:720,  defVal:1,    scale:1,  unit:'minutes',  title:'<b>Illuminance Interval</b>',     description:'Brightness acquisition interval (update at the time motion is activated)'],

            ],
            deviceJoinName: 'Tuya Multi Sensor 2 In 1',
            configuration : ['battery': false]
    ],

    // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-w-healthstatus/92441/1080?u=kkossev
    'TS0601_2IN1_MYQ_ZMS03'  : [      //https://github.com/protyposis/zigbee-herdsman-converters/blob/c9b8f3172cb11ea0ca36440f8956eda582182df7/src/devices/tuya.ts#L4750
            description   : 'Tuya 2in1 (Motion and Illuminance) MYQ_ZMS03 sensor',
            models         : ['TS0601'],
            device        : [type: 'PIR', isIAS:false, powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'Battery': true],
            preferences   : ['motionReset':true, 'invertMotion':true],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_f1pvdgoh', deviceJoinName: 'Tuya MYQ_ZMS03 Multi Sensor 2 in 1'],          // https://s.click.aliexpress.com/e/_DdNVVZx 
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ppuj1vem', deviceJoinName: 'Treatlife human presence sensor 2 in 1']        // https://github.com/benedicttobias/zigbee-herdsman-converters/blob/ddaad14c80cadb2bff8d314ed8128139958ee02a/src/devices/tuya.ts#L12117-L12133
                //  ^^^^  this return the opposite value. presence is 'false' when motion is detected!
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',                   type:'enum',   rw: 'ro', min:0, max:1 ,   defVal:'0',  scale:1,  map:[0:'inactive', 1:'active'] ,   unit:'',  description:'Motion'],
                [dp:4,   name:'battery',                  type:'number', rw: 'ro', min:0, max:100,  defVal:100,  scale:1,  unit:'%',        title:'<b>Battery level</b>',              description:'Battery level'],
                [dp:101, name:'illuminance',              type:'number', rw: 'ro', min:0, max:1000, defVal:0,    scale:1,  unit:'lx',       title:'<b>illuminance</b>',     description:'illuminance'],
            ],
            refresh:        ['queryAllTuyaDP'],
            deviceJoinName: 'Tuya MYQ_ZMS03 Multi Sensor 2 in 1'
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
                [at:'0x0500:0x0013', name:'sensitivity', type:'enum',   rw: 'rw', min:0, max:2,    defVal:'2',  unit:'',           map:[0:'low', 1:'medium', 2:'high'], title:'<b>Sensitivity</b>',   description:'PIR sensor sensitivity (update at the time motion is activated)'],
                [at:'0x0500:0xF001', name:'keepTime',    type:'enum',   rw: 'rw', min:0, max:2,    defVal:'0',  unit:'seconds',    map:[0:'30 seconds', 1:'60 seconds', 2:'120 seconds'], title:'<b>Keep Time</b>',   description:'PIR keep time in seconds (update at the time motion is activated)'],
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
                [dp:101, name:'pushed',         type:'enum',   rw: 'ro', min:0, max:2, defVal:'0',   scale:1,    map:[0:'pushed', 1:'doubleTapped', 2:'held'] ,   unit:'',     title:'<b>Presence state</b>', description:'Presence state'],
                [dp:102, name:'illuminance',    type:'number', rw: 'ro', min:0, max:1, defVal:0,     scale:1,    unit:'lx',       title:'<b>illuminance</b>',     description:'illuminance'],

            ],
            deviceJoinName: 'Tuya Motion Sensor and Scene Switch',
            configuration : ['battery': false]
    ],

    'TS0601_PIR_PRESENCE'   : [ // isBlackPIRsensor()       // https://github.com/zigpy/zha-device-handlers/issues/1618
            description   : 'Tuya PIR Human Motion Sensor (Black)',
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'Battery': true],
            preferences   : ['fadingTime':'102', 'distance':'105'],
            commands      : ['resetStats':'resetStats', 'resetPreferencesToDefaults':'resetPreferencesToDefaults'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_9qayzqa8', deviceJoinName: 'Smart PIR Human Motion Sensor (Black)']    // https://www.aliexpress.com/item/1005004296422003.html
            ],
            tuyaDPs:        [                                           // TODO - defaults !!
                [dp:102, name:'fadingTime',          type:'number',  rw: 'rw', min:24,  max:300 ,  defVal:24,        scale:1,    unit:'seconds',      title:'<b>Fading time</b>',   description:'Fading(Induction) time'],
                [dp:105, name:'distance',      type:'enum',    rw: 'rw', min:0,   max:9 ,    defVal:'6',       scale:1,    map:[0:'0.5 m', 1:'1.0 m', 2:'1.5 m', 3:'2.0 m', 4:'2.5 m', 5:'3.0 m', 6:'3.5 m', 7:'4.0 m', 8:'4.5 m', 9:'5.0 m'] ,   unit:'meters',     title:'<b>Target Distance</b>', description:'Target Distance'],
                [dp:119, name:'motion',              type:'enum',    rw: 'ro', min:0,   max:1 ,    defVal:'0',       scale:1,    map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'Presence state'],
                [dp:141, name:'humanMotionState',    type:'enum',    rw: 'ro', min:0,   max:4 ,    defVal:'0',       scale:1,    map:[0:'none', 1:'presence', 2:'peaceful', 3:'small_move', 4:'large_move'] ,   unit:'',     title:'<b>Presence state</b>', description:'Presence state'],
            ],
            deviceJoinName: 'Tuya PIR Human Motion Sensor LQ-CG01-RDR',
            configuration : ['battery': false]
    ],

    'TS0601_PIR_AIR'      : [    // isHumanPresenceSensorAIR()  - Human presence sensor AIR (PIR sensor!) - o_sensitivity, v_sensitivity, led_status, vacancy_delay, light_on_luminance_prefer, light_off_luminance_prefer, mode, luminance_level, reference_luminance, vacant_confirm_time
            description   : 'Tuya PIR Human Motion Sensor AIR',
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'Battery': true],                // TODO - check if battery powered?
            preferences   : ['vacancyDelay':'103', 'ledStatusAIR':'110', 'detectionMode':'104', 'vSensitivity':'101', 'oSensitivity':'102', 'lightOnLuminance':'107', 'lightOffLuminance':'108' ],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_auin8mzr', deviceJoinName: 'Tuya PIR Human Motion Sensor AIR']        // Tuya LY-TAD-K616S-ZB
            ],
            tuyaDPs:        [                                           // TODO - defaults !!
                [dp:101, name:'vSensitivity',        type:'enum',    rw: 'rw', min:0,   max:1,     defVal:'0', scale:1,    map:[0:'Speed Priority', 1:'Standard', 2:'Accuracy Priority'] ,   unit:'-',     title:'<b>vSensitivity options</b>', description:'V-Sensitivity mode'],
                [dp:102, name:'oSensitivity',        type:'enum',    rw: 'rw', min:0,   max:1,     defVal:'0', scale:1,    map:[0:'Sensitive', 1:'Normal', 2:'Cautious'] ,   unit:'',     title:'<b>oSensitivity options</b>', description:'O-Sensitivity mode'],
                [dp:103, name:'vacancyDelay',        type:'number',  rw: 'rw', min:0,   max:1000,  defVal:10,  scale:1,    unit:'seconds',        title:'<b>Vacancy Delay</b>',          description:'Vacancy Delay'],
                [dp:104, name:'detectionMode',       type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0', scale:1,    map:[0:'General Model', 1:'Temporary Stay', 2:'Basic Detecton', 3:'PIR Sensor Test'] ,   unit:'',     title:'<b>Detection Mode</b>', description:'Detection Mode'],
                [dp:105, name:'unacknowledgedTime',  type:'number',  rw: 'ro', min:0,   max:9 ,    defVal:7,   scale:1,    unit:'seconds',         description:'unacknowledgedTime'],
                [dp:106, name:'illuminance',         type:'number',  rw: 'ro', min:0,   max:2000,  defVal:0,   scale:1,    unit:'lx',       title:'<b>illuminance</b>',                description:'illuminance'],
                [dp:107, name:'lightOnLuminance',    type:'number',  rw: 'rw', min:0,   max:2000,  defVal:0,   scale:1,    unit:'lx',       title:'<b>lightOnLuminance</b>',                description:'lightOnLuminance'],        // Ligter, Medium, ... ?// TODO =- check range 0 - 10000 ?
                [dp:108, name:'lightOffLuminance',   type:'number',  rw: 'rw', min:0,   max:2000,  defVal:0,   scale:1,    unit:'lx',       title:'<b>lightOffLuminance</b>',                description:'lightOffLuminance'],
                [dp:109, name:'luminanceLevel',      type:'number',  rw: 'ro', min:0,   max:2000,  defVal:0,   scale:1,    unit:'lx',       title:'<b>luminanceLevel</b>',                description:'luminanceLevel'],            // Ligter, Medium, ... ?
                [dp:110, name:'ledStatusAIR',        type:'enum',    rw: 'rw', min:0,   max:1 ,    defVal:'0', scale:1,    map:[0: 'Switch On', 1:'Switch Off', 2: 'Default'] ,   unit:'',     title:'<b>LED status</b>', description:'Led status switch'],
            ],
            deviceJoinName: 'Tuya PIR Human Motion Sensor AIR',
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
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,0001', outClusters:'0003', model:'MS01', manufacturer:'eWeLink', deviceJoinName: 'eWeLink Motion Sensor'],        // third variant
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,0406,0500,FC57', outClusters:'0003,0019', model:'SNZB-03P', manufacturer:'eWeLink', deviceJoinName: 'SONOFF SNZB-03P Motion Sensor']         // https://community.hubitat.com/t/new-sonoff-snzb-03p-motion-sensors-not-detecting/141138?u=kkossev
            ],
            deviceJoinName: 'Sonoff/eWeLink Motion sensor',
            configuration : [
                '0x0001':[['bind':true],  ['reporting':'0x21, 0x20, 3600, 7200, 0x02']],    // TODO - use the reproting values
                '0x0500':[['bind':false], ['sensitivity':false], ['keepTime':false]],       // TODO - use in update function
            ]  // battery percentage, min 3600, max 7200, UINT8, delta 2
    ],

    // isSiHAS() and Sunricher
    'SIHAS_USM-300Z_4_IN_1' : [
            description   : 'SiHAS USM-300Z 4-in-1',
            models        : ['ShinaSystem'],
            device        : [type: 'PIR', powerSource: 'battery', isIAS:false, isSleepy:false],
            capabilities  : ['MotionSensor': true, 'TemperatureMeasurement': true, 'RelativeHumidityMeasurement': true, 'IlluminanceMeasurement': true, 'Battery': true],
            preferences   : [:],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0400,0003,0406,0402,0001,0405,0500', outClusters:'0004,0003,0019', model:'USM-300Z', manufacturer:'ShinaSystem', deviceJoinName: 'SiHAS MultiPurpose Sensor'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0009,0400,0402,0405,0406,0500', outClusters:'0019', model:'HK-SENSOR-4IN1-A', manufacturer:'Sunricher', deviceJoinName: 'Azoula Zigbee 4 in 1 Multi Sensor']     // https://community.hubitat.com/t/what-driver-for-this-4-1/145847?u=kkossev
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            //tuyaDPs       : [:],
            attributes    : [
                [at:'0x0406:0x0000', name:'motion',        type:'enum',   rw: 'ro', min:0,   max:1,    defVal:'0',   scale:1,    map:[0:'inactive', 1:'active'], title:'<b>Presence</b>', description:'Presence state']
            ],
            refresh       : [ 'batteryRefresh', 'illuminanceRefresh', 'temperatureRefresh', 'humidityRefresh', 'motion'],
            //configuration : ["0x0406":"bind"]     // TODO !!
            configuration : [:],
            deviceJoinName: 'SiHAS USM-300Z 4-in-1'
    ],

    'ESRESSIF_PIR_TEMP' : [
            description   : 'Espressif motion and temp sensor',
            models        : ['ZigbeeOccupancyPIRSensor'],
            device        : [type: 'PIR', powerSource: 'DC', isIAS:false, isSleepy:false],
            capabilities  : ['MotionSensor': true, 'TemperatureMeasurement': true, 'Battery': false],
            preferences   : [:],
            fingerprints  : [
                [profileId:'0104', endpointId:'0A', inClusters:'0000,0003,0406', outClusters:'0019,000A', model:'ZigbeeOccupancyPIRSensor', manufacturer:'Espressif', deviceJoinName: 'Espressif ZigbeeOccupancyPIRSensor'],
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults'],
            attributes    : [
                [at:'0x0406:0x0000', name:'motion',        type:'enum',   rw: 'ro', min:0,   max:1,    defVal:'0',   scale:1,    map:[0:'inactive', 1:'active'], title:'<b>Motion</b>'],
                // endpoint 0B: inClusters: 0000,0003,0402,000A outClusters: 0003,000A
                [at:'0x0406:0x0000', name:'temperature',     type:'decimal', rw: 'ro', min:-20.0, max:80.0, defVal:0.0,  scale:10, unit:'deg.',       description:'Temperature']
            ],
            refresh       : [ 'motion', 'temperatureRefresh'],
            configuration : [:],
            deviceJoinName: 'Espressif ZigbeeOccupancyPIRSensor'
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
                [at:'0x0500:0x0013', name:'sensitivity', type:'enum',   rw: 'rw', min:0, max:2,    defVal:'2',  unit:'',           map:[0:'low', 1:'medium', 2:'high'], title:'<b>Sensitivity</b>',   description:'PIR sensor sensitivity (update at the time motion is activated)'],
                [at:'0x0500:0xF001', name:'keepTime',    type:'enum',   rw: 'rw', min:0, max:2,    defVal:'0',  unit:'seconds',    map:[0:'30 seconds', 1:'60 seconds', 2:'120 seconds'], title:'<b>Keep Time</b>',   description:'PIR keep time in seconds (update at the time motion is activated)'],
            ],
            deviceJoinName: 'Other OEM Motion sensor (IAS)',
            configuration : ['battery': false]
    ],

    '---'   : [
            description   : '--------------------------------------',
            models        : [],
            fingerprints  : [],
    ],

// ------------------------------------------- mmWave Radars - OBSOLETE ! => use the mmWave driver instead! ------------------------------------------------//

    'TS0601_TUYA_RADAR'   : [ 
            description   : 'Tuya Human Presence mmWave Radar ZY-M100', models : ['TS0601'], device : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false], capabilities : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [
                [manufacturer:'_TZE200_ztc6ggyl'], [manufacturer:'_TZE204_ztc6ggyl'], [manufacturer:'_TZE200_ikvncluo'], [manufacturer:'_TZE200_lyetpprm'], [manufacturer:'_TZE200_wukb7rhc'],
                [manufacturer:'_TZE200_jva8ink8'], [manufacturer:'_TZE200_mrf6vtua'], [manufacturer:'_TZE200_ar0slwnd'], [manufacturer:'_TZE200_sfiy5tfs'], [manufacturer:'_TZE200_holel4dk'],
                [manufacturer:'_TZE200_xpq2rzhq'], [manufacturer:'_TZE204_qasjif9e'], [manufacturer:'_TZE204_xsm7l9xa']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', map:[0:'inactive', 1:'active'], description:'Presence state'],
                [dp:9,   name:'distance',           type:'decimal', rw: 'ro', scale:100,  unit:'meters',  description:'detected distance'],
                [dp:104, name:'illuminance',        type:'number',  rw: 'ro', unit:'lx',  description:'illuminance'],

            ], spammyDPsToIgnore : [9], spammyDPsToNotTrace : [9, 103]
    ],

    'TS0601_KAPVNNLK_RADAR'   : [
            description   : 'Tuya TS0601_KAPVNNLK 24GHz Radar', models : ['TS0601'], device : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false], capabilities : ['MotionSensor': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZE204_kapvnnlk'], [manufacturer:'_TZE204_kyhbrfyl']],
            tuyaDPs:        [
                [dp:1, name:'motion', type:'enum', rw: 'ro', map:[0:'inactive', 1:'active'], description:'Presence state'],
                [dp:19,  name:'distance', type:'decimal', rw: 'ro', scale:100, unit:'meters', description:'detected distance']

            ], spammyDPsToIgnore : [19], spammyDPsToNotTrace : [19]
    ],

    'TS0601_RADAR_MIR-HE200-TY'   : [
            description   : 'Tuya Human Presence Sensor MIR-HE200-TY', models : ['TS0601'], device : [isDepricated:true, type: 'radar', powerSource: 'dc', isSleepy:false], capabilities : ['MotionSensor': true, 'IlluminanceMeasurement': true],
            fingerprints  : [[manufacturer:'_TZE200_vrfecyku'], [manufacturer:'_TZE200_lu01t0zl'], [manufacturer:'_TZE200_ypprdwsl']],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', map:[0:'inactive', 1:'active'], description:'Presence state'],
                [dp:102, name:'motionState',        type:'enum',    rw: 'ro', map:[0:'inactive', 1:'active'], description:'Motion state (occupancy)'],
            ]
    ],

    'TS0601_BLACK_SQUARE_RADAR'   : [
            description   : 'Tuya Black Square Radar', models : ['TS0601'], device : [isDepricated:true, type: 'radar', powerSource: 'dc', isSleepy:false], capabilities : ['MotionSensor':true],
            fingerprints  : [[manufacturer:'_TZE200_0u3bj3rc'], [manufacturer:'_TZE200_v6ossqfy'], [manufacturer:'_TZE200_mx6u6l4y']],
            tuyaDPs:        [[dp:1,   name:'motion',         type:'enum',   rw: 'ro', map:[0:'inactive', 1:'active'],     description:'Presence']], spammyDPsToIgnore : [103], spammyDPsToNotTrace : [1, 101, 102, 103]
    ],

    'TS0601_YXZBRB58_RADAR'   : [
            description   : 'Tuya YXZBRB58 Radar', models : ['TS0601'], device : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy: false], capabilities : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZE204_sooucan5']],
            tuyaDPs:        [
                [dp:1,   name:'motion',                 type:'enum',    rw: 'ro', map:[0:'inactive', 1:'active'],  description:'Presence state'],
                [dp:101, name:'illuminance',            type:'number',  rw: 'ro', unit:'lx', description:'Illuminance'],
                [dp:105, name:'distance',               type:'decimal', rw: 'ro', scale:100,  unit:'meters',   description:'Distance']
            ], spammyDPsToIgnore : [105], spammyDPsToNotTrace : [105]
    ],

    'TS0601_SXM7L9XA_RADAR'   : [
            description   : 'Tuya Human Presence Detector SXM7L9XA', models : ['TS0601'], device : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false], capabilities : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZE204_sxm7l9xa'], [manufacturer:'_TZE204_e5m9c5hl']],
            tuyaDPs:        [
                [dp:104, name:'illuminance',            type:'number',  rw: 'ro', unit:'lx', description:'illuminance'],
                [dp:105, name:'motion',                 type:'enum',    rw: 'ro', map:[0:'inactive', 1:'active'],  description:'Presence state'],
                [dp:109, name:'distance',               type:'decimal', rw: 'ro', scale:100,  unit:'meters',    description:'Distance']
            ], spammyDPsToIgnore : [109], spammyDPsToNotTrace : [109]
    ],

    'TS0601_IJXVKHD0_RADAR'   : [
            description   : 'Tuya Human Presence Detector IJXVKHD0',
            models        : ['TS0601'],
            device        : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZE204_ijxvkhd0']],
            tuyaDPs:        [
                [dp:104, name:'illuminance',            type:'number',  rw: 'ro', unit:'lx', description:'illuminance'],
                [dp:105, name:'humanMotionState',       type:'enum',    rw: 'ro', map:[0:'none', 1:'present', 2:'moving'], description:'Presence state'],
                [dp:109, name:'distance',               type:'decimal', rw: 'ro', unit:'meters', description:'Target distance'],
                [dp:112, name:'motion',                 type:'enum',    rw: 'ro', map:[0:'inactive', 1:'active'], description:'Presence state'],
                [dp:123, name:'presence',               type:'enum',    rw: 'ro', map:[0:'none', 1:'presence'],   description:'Presence']
            ], spammyDPsToIgnore : [109, 9], spammyDPsToNotTrace : [109, 104]
    ],

    'TS0601_YENSYA2C_RADAR'   : [
            description   : 'Tuya Human Presence Detector YENSYA2C', models : ['TS0601'], device : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy: false], capabilities : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZE204_yensya2c'], [manufacturer:'_TZE204_mhxn2jso']],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'Presence state'],
                [dp:19,  name:'distance',           type:'decimal', rw: 'ro', scale:100, unit:'meters',  description:'Distance'],
                [dp:20,  name:'illuminance',        type:'number',  rw: 'ro', unit:'lx', description:'illuminance']
            ], spammyDPsToIgnore : [19], spammyDPsToNotTrace : [19]
    ],

    'TS0225_HL0SS9OA_RADAR'   : [
            description   : 'Tuya TS0225_HL0SS9OA Radar', models : ['TS0225'], device : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false], capabilities : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true],
            fingerprints  : [[manufacturer:'_TZE200_hl0ss9oa']],
            tuyaDPs:        [
                [dp:1,   name:'motion',                          type:'enum',    rw: 'ro', map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'Presence state'],
                [dp:11,  name:'humanMotionState',                type:'enum',    rw: 'ro', map:[0:'none', 1:'large', 2:'small', 3:'static'],       description:'Human motion state'],
                [dp:20,  name:'illuminance',                     type:'number',  rw: 'ro', scale:10,  unit:'lx', description:'Illuminance']
            ], spammyDPsToIgnore : [], spammyDPsToNotTrace : [11]
    ],

    'TS0225_2AAELWXK_RADAR'   : [
            description   : 'Tuya TS0225_2AAELWXK 5.8 GHz Radar', models : ['TS0225'], device : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false], capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true],
            fingerprints  : [[manufacturer:'_TZE200_2aaelwxk']],
            tuyaDPs:        [
                [dp:1,   name:'motion',                          type:'enum',    rw: 'ro', map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'Presence state'],
                [dp:101, name:'humanMotionState',                type:'enum',    rw: 'ro', map:[0:'none', 1:'large', 2:'small', 3:'static'],       description:'Human motion state'],
                [dp:106, name:'illuminance',                     type:'number',  rw: 'ro', scale:10,  unit:'lx', description:'Illuminance']
            ]
    ],

    'TS0601_SBYX0LM6_RADAR'   : [
            description   : 'Tuya Human Presence Detector SBYX0LM6', models : ['TS0601'], device : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false], capabilities : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [
                [manufacturer:'_TZE204_sbyx0lm6'], [manufacturer:'_TZE200_sbyx0lm6'], [manufacturer:'_TZE204_dtzziy1e'], [manufacturer:'_TZE200_dtzziy1e'], [manufacturer:'_TZE204_clrdrnya'], [manufacturer:'_TZE200_clrdrnya'],
                [manufacturer:'_TZE204_cfcznfbz'], [manufacturer:'_TZE204_iaeejhvf'], [manufacturer:'_TZE204_mtoaryre'], [manufacturer:'_TZE204_8s6jtscb'], [manufacturer:'_TZE204_rktkuel1'], [manufacturer:'_TZE204_mp902om5'],
                [manufacturer:'_TZE200_w5y5slkq'], [manufacturer:'_TZE204_w5y5slkq'], [manufacturer:'_TZE200_xnaqu2pc'], [manufacturer:'_TZE204_xnaqu2pc'], [manufacturer:'_TZE200_wk7seszg'], [manufacturer:'_TZE204_wk7seszg'],
                [manufacturer:'_TZE200_0wfzahlw'], [manufacturer:'_TZE204_0wfzahlw'], [manufacturer:'_TZE200_pfayrzcw'], [manufacturer:'_TZE204_pfayrzcw'], [manufacturer:'_TZE200_z4tzr0rg'], [manufacturer:'_TZE204_z4tzr0rg']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:'enum',    rw: 'ro', map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'Presence state'],
                [dp:9,   name:'distance',           type:'decimal', rw: 'ro', scale:100,  unit:'meters',   description:'detected distance'],
                [dp:104, name:'illuminance',        type:'number',  rw: 'ro', scale:10,   unit:'lx',       description:'illuminance']
            ],  spammyDPsToIgnore : [9], spammyDPsToNotTrace : [9]
    ],

    'TS0225_LINPTECH_RADAR'   : [
            description   : 'Tuya TS0225_LINPTECH 24GHz Radar', models : ['TS0225'], device : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false], capabilities : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZ3218_awarhusb']],
            tuyaDPs:       [ [dp:101, name:'fadingTime', type:'number', rw: 'rw', min:1, max:9999, defVal:10, scale:1, unit:'seconds', title: '<b>Fading time</b>', description:'Presence inactivity timer, seconds']]
    ],

    'TS0225_EGNGMRZH_RADAR'   : [
            description   : 'Tuya TS0225_EGNGMRZH 24GHz Radar', models : ['TS0225'], device : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false], capabilities : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZFED8_egngmrzh']],    // uses IAS for occupancy!
            tuyaDPs:        [
                [dp:101, name:'illuminance',        type:'number',  rw: 'ro', unit:'lx'],
                [dp:103, name:'distance',           type:'decimal', rw: 'ro', scale:10,  unit:'meters']
            ], spammyDPsToIgnore : [103], spammyDPsToNotTrace : [103]
    ],

    'TS0225_O7OE4N9A_RADAR'   : [
            description   : 'Tuya Human Presence Detector YENSYA2C', models : ['TS0225'], device : [isDepricated: true, type: 'radar', powerSource: 'dc', isSleepy:false], capabilities : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            fingerprints  : [[manufacturer:'_TZFED8_o7oe4n9a']],
            tuyaDPs:        [
                [dp:1,   name:'motion',                 type:'enum',    rw: 'ro', map:[0:'inactive', 1:'active'], description:'Presence state'],
                [dp:181, name:'illuminance',            type:'number',  rw: 'ro', unit:'lx', description:'illuminance'],
                [dp:182, name:'distance',               type:'decimal', rw: 'ro', unit:'meters',  description:'Distance to target']
            ], spammyDPsToIgnore : [182], spammyDPsToNotTrace : [182]
    ],

    'OWON_OCP305_RADAR'   : [
            description   : 'OWON OCP305 Radar', models : ['OCP305'], device: [isDepricated:true, type: 'radar', powerSource: 'dc', isSleepy:false], capabilities  : ['MotionSensor': true, 'Battery': true],
            fingerprints  : [[manufacturer:'OWON']]
    ],

    'SONOFF_SNZB-06P_RADAR' : [ // Depricated
            description   : 'SONOFF SNZB-06P RADAR', models : ['SNZB-06P'], device : [isDepricated:true, type: 'radar', powerSource: 'dc', isIAS:false, isSleepy:false], capabilities : ['MotionSensor': true],
            fingerprints  : [[manufacturer:'SONOFF']]
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
            configuration : ['battery': true],
            deviceJoinName: 'Unknown device'        // used during the inital pairing, if no individual fingerprint deviceJoinName was found
    ]

]

// this is a motion driver -> IAS events represent motion/occupancy
public void customParseIasMessage(final String description) {
    Map zs = zigbee.parseZoneStatusChange(description)
    if (zs.alarm1Set == true) {
        logDebug "customParseIasMessage: Alarm 1 is set"
        handleMotion(true)      // motionLib.groovy
    }
    else {
        logDebug "customParseIasMessage: Alarm 1 is cleared"
        handleMotion(false)     // motionLib.groovy
    }
}

void customParseOccupancyCluster(final Map descMap) {
    //final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseOccupancyCluster: zigbee received cluster 0x0406 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        if (descMap.attrId == '0000') {
            int raw = Integer.parseInt(descMap.value, 16)
            handleMotion(raw ? true : false)
        }
        // TODO - should be processed in the processClusterAttributeFromDeviceProfile method!
        else if (descMap.attrId == '0020') {    // OWON and SONOFF
            int value = zigbee.convertHexToInt(descMap.value)
            sendEvent('name': 'fadingTime', 'value': value, 'unit': 'seconds', 'type': 'physical', 'descriptionText': "fading time is ${value} seconds")
            logDebug "Cluster ${descMap.cluster} Attribute ${descMap.attrId} (fadingTime) value is ${value} (0x${descMap.value} seconds)"
        }
        else if (descMap.attrId == '0022') {
            int value = zigbee.convertHexToInt(descMap.value)
            sendEvent('name': 'radarSensitivity', 'value': value, 'unit': '', 'type': 'physical', 'descriptionText': "radar sensitivity is ${value}")
            logDebug "Cluster ${descMap.cluster} Attribute ${descMap.attrId} (radarSensitivity) value is ${value} (0x${descMap.value})"
        }
        else {
            logDebug "UNPROCESSED Cluster ${descMap.cluster} Attribute ${descMap.attrId} value is ${descMap.value} (0x${descMap.value})"
        }
    }

}

// called from standardProcessTuyaDP in the commonLib for each Tuya dp report in a Zigbee message
// should always return true, as we are processing all the dp reports here
boolean customProcessTuyaDp(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) {
    logDebug "customProcessTuyaDp: dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len} descMap.data = ${descMap?.data}"
    if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {
        return true      // sucessfuly processed from the deviceProfile 
    }

    logWarn "<b>NOT PROCESSED from deviceProfile</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
    localProcessTuyaDP(descMap, dp, dp_id, fncmd, dp_len)
    return true
}

void localProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len) {
    switch (dp) {
        case 0x01 : // motion state for almost of the Tuya PIR sensors
            logDebug "(DP=0x01) motion event fncmd = ${fncmd}"
            handleMotion(fncmd ? true : false)
            break
        case 0x04 :    // battery level for TS0202 and TS0601 2in1 ; battery1 for Fantem 4-in-1 (100% or 0% ) Battery level for _TZE200_3towulqd (2in1)
            logDebug "(DP=0x04) Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
            handleTuyaBatteryLevel(fncmd)
            break
        case 0x07 : // temperature for 4-in-1 (no data)
            logDebug "(DP=0x07) unexpected 4-in-1 temperature (dp=07) is ${fncmd / 10.0 } ${fncmd}"
            temperatureEvent(fncmd / getTemperatureDiv())
            break
        case 0x08 : // humidity for 4-in-1 (no data)
            logDebug "(DP=0x08) unexpected 4-in-1 humidity (dp=08) is ${fncmd} ${fncmd}"
            humidityEvent(fncmd / getHumidityDiv())
            break
        case 0x09 : // sensitivity for TS0202 4-in-1 and 2in1 _TZE200_3towulqd
            logInfo "(DP=0x09) unexpected received sensitivity : ${sensitivityOpts.options[fncmd]} (${fncmd})"
            //device.updateSetting('sensitivity', [value:fncmd.toString(), type:'enum'])
            break
        case 0x0A : // (10) keep time for TS0202 4-in-1 and 2in1 _TZE200_3towulqd
            logInfo "(DP=0x0A) unexpected Keep Time (dp=0x0A) is ${keepTimeIASOpts.options[fncmd]} (${fncmd})"
            //device.updateSetting('keepTime', [value:fncmd.toString(), type:'enum'])
            break
        case 0x19 : // (25)
            logDebug "(DP=0x19) unexpected battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
            handleTuyaBatteryLevel(fncmd)
            break
        case 0x65 :    // (101)
            //  Tuya 3 in 1 (101) -> motion (ocupancy) + TUYATEC
            if (DEVICE?.device.isDepricated == true) {
                logDebug '(DP=0x65) unexpected : ignored depricated device 0x65 event'
            }
            else {
                logDebug "(DP=0x65) unexpected : motion event 0x65 fncmd = ${fncmd}"
                handleMotion(fncmd ? true : false)
            }
            break
        case 0x68 :     // (104)
            // 4in1  0x68 temperature compensation
            int val = fncmd
            // for negative values produce complimentary hex (equivalent to negative values)
            if (val > 4294967295) { val = val - 4294967295 }
            logInfo "(DP=0x68) unexpected : 4-in-1 temperature calibration is ${val / 10.0}"
            break
        case 0x69 :    // (105)
            // 4in1 0x69 humidity calibration (compensation)
            int val = fncmd
            if (val > 4294967295) val = val - 4294967295
            logInfo "(DP=0x69) unexpected : 4-in-1 humidity calibration is ${val}"
            break
        case 0x6A : // (106)
            // 4in1 0x6a lux calibration (compensation)
            int val = fncmd
            if (val > 4294967295) { val = val - 4294967295 }
            logInfo "(DP=0x69) unexpected : 4-in-1 lux calibration is ${val}"
            break
        default :
                logDebug "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            break
    }
}


// called from processFoundItem in the deviceProfileLib
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

List<String> refreshFantem() {
    List<String>  cmds = zigbee.command(0xEF00, 0x07, '00')    // Fantem Tuya Magic
    return cmds
}

List<String> customRefresh() {
    logDebug "customRefresh()"
    List<String> cmds = []
    List<String> devProfCmds = refreshFromDeviceProfileList()
    if (devProfCmds != null && !devProfCmds.isEmpty()) {
        cmds += devProfCmds
    }
    if (settings.allStatusTextEnable == true) {
        runIn(3, 'formatAttrib', [overwrite: true])
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
    if (settings?.allStatusTextEnable == false) {
        device.deleteCurrentState('all')
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
    if (settings.allStatusTextEnable == true) {
        runIn(3, 'formatAttrib', [overwrite: true])
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
    // overwrite the default value of the invertMotion setting if the device is 2in1
    if (fullInit == true || settings.invertMotion == null) device.updateSetting('invertMotion', is2in1() ? true : false)
    if (fullInit == true || settings.allStatusTextEnable == null) device.updateSetting('allStatusTextEnable', false)
}

void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents()"
    if (getDeviceProfile() == 'TS0601_BLACK_SQUARE_RADAR') {
        sendEvent(name: 'WARNING', value: 'EXTREMLY SPAMMY DEVICE!', descriptionText: 'This device bombards the hub every 4 seconds!')
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

void customParseIASCluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseIASCluster: zigbee received cluster 0x0500 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logDebug "customParseIASCluster: received unknown 0x0500 attribute 0x${descMap.attrId} (value ${descMap.value})"
        standardParseIASCluster(descMap) 
    }
}

// ------------------------- formatAttrib() methods for the 4-in-1 driver -------------------------

void formatAttrib() {
    if (settings.allStatusTextEnable == false) {    // do not send empty html or text attributes
        return
    }
    String attrStr = ''
    attrStr += addToAttr('status', 'healthStatus')
    attrStr += addToAttr('motion', 'motion')
    if (DEVICE?.capabilities?.DistanceMeasurement == true && settings?.ignoreDistance == false) { attrStr += addToAttr('distance', 'distance') }
    if (DEVICE?.capabilities?.Battery == true) { attrStr += addToAttr('battery', 'battery') }
    if (DEVICE?.capabilities?.IlluminanceMeasurement == true) { attrStr += addToAttr('illuminance', 'illuminance') }
    if (DEVICE?.capabilities?.TemperatureMeasurement == true) { attrStr += addToAttr('temperature', 'temperature') }
    if (DEVICE?.capabilities?.RelativeHumidityMeasurement == true) { attrStr += addToAttr('humidity', 'humidity')  }
    attrStr = attrStr.substring(0, attrStr.length() - 3)    // remove the ',  '
    updateAttr('all', attrStr)
    if (attrStr.length() > 64) {
        updateAttr('all', "Max Attribute Size Exceeded: ${attrStr.length()}")
    }
}

/* groovylint-disable-next-line UnusedMethodParameter */
String addToAttr(String name, String key, String convert = 'none') {
    String retResult = ''
    String attrUnit = getUnitFromState(key)
    if (attrUnit == null) { attrUnit = '' }
    /* groovylint-disable-next-line NoDef */
    def curVal = device.currentValue(key, true)
    if (curVal != null) {
        if (convert == 'int') {
            retResult += safeToInt(curVal).toString() + '' + attrUnit
        }
        else if (convert == 'double') {
            retResult += safeToDouble(curVal).toString() + '' + attrUnit
        }
        else {
            retResult += curVal.toString() + '' + attrUnit
        }
    }
    else {
        retResult += 'n/a'
    }
    retResult += ',  '
    return retResult
}

String getUnitFromState(String attrName) {
    return device.currentState(attrName)?.unit
}

void updateAttr(String aKey, String aValue, String aUnit = '') {
    sendEvent(name:aKey, value:aValue, unit:aUnit, type: 'digital')
}

// ------------------------- end of formatAttrib() methods -------------------------

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
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NestedBlockDepth, NoDouble, NoFloat, NoWildcardImports, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.deviceProfileLib, line 1
library( // library marker kkossev.deviceProfileLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLib', namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy', documentationLink: '', // library marker kkossev.deviceProfileLib, line 4
    version: '3.4.1' // library marker kkossev.deviceProfileLib, line 5
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
 * ver. 3.4.1  2025-02-02 kkossev  - (dev. branch) setPar help improvements // library marker kkossev.deviceProfileLib, line 36
 * // library marker kkossev.deviceProfileLib, line 37
 *                                   TODO - remove the 2-in-1 patch ! // library marker kkossev.deviceProfileLib, line 38
 *                                   TODO - add updateStateUnknownDPs (from the 4-in-1 driver) // library marker kkossev.deviceProfileLib, line 39
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 40
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 41
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off) // library marker kkossev.deviceProfileLib, line 42
 *                                   TODO: allow NULL parameters default values in the device profiles // library marker kkossev.deviceProfileLib, line 43
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 44
 * // library marker kkossev.deviceProfileLib, line 45
*/ // library marker kkossev.deviceProfileLib, line 46

static String deviceProfileLibVersion()   { '3.4.1' } // library marker kkossev.deviceProfileLib, line 48
static String deviceProfileLibStamp() { '2025/02/16 7:47 AM' } // library marker kkossev.deviceProfileLib, line 49
import groovy.json.* // library marker kkossev.deviceProfileLib, line 50
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 51
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 52
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 53
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 54

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 56

metadata { // library marker kkossev.deviceProfileLib, line 58
    // no capabilities // library marker kkossev.deviceProfileLib, line 59
    // no attributes // library marker kkossev.deviceProfileLib, line 60
    /* // library marker kkossev.deviceProfileLib, line 61
    // copy the following commands to the main driver, if needed // library marker kkossev.deviceProfileLib, line 62
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 63
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 64
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 65
    ] // library marker kkossev.deviceProfileLib, line 66
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 67
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 68
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 69
    ] // library marker kkossev.deviceProfileLib, line 70
    */ // library marker kkossev.deviceProfileLib, line 71
    preferences { // library marker kkossev.deviceProfileLib, line 72
        if (device) { // library marker kkossev.deviceProfileLib, line 73
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 74
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 75
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 76
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 77
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 78
                        input inputMap // library marker kkossev.deviceProfileLib, line 79
                    } // library marker kkossev.deviceProfileLib, line 80
                } // library marker kkossev.deviceProfileLib, line 81
            } // library marker kkossev.deviceProfileLib, line 82
            //if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 83
                input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 84
            //} // library marker kkossev.deviceProfileLib, line 85
        } // library marker kkossev.deviceProfileLib, line 86
    } // library marker kkossev.deviceProfileLib, line 87
} // library marker kkossev.deviceProfileLib, line 88

private boolean is2in1() { return getDeviceProfile().startsWith('TS0601_2IN1')  }   // patch! // library marker kkossev.deviceProfileLib, line 90

public String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 92
public Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 93
public Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 94
//List<String> getDeviceProfilesMap()   { deviceProfilesV3 != null ? deviceProfilesV3.values().description as List<String> : deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 95

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
String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 118
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
Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 131
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

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 162
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
void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 191
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
List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 219
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 220
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 221
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 222
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 223
    } // library marker kkossev.deviceProfileLib, line 224
    return validPars // library marker kkossev.deviceProfileLib, line 225
} // library marker kkossev.deviceProfileLib, line 226

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 228
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 229
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
        else { logDebug "warning: couldn't find map for preference ${name}" ; return } // library marker kkossev.deviceProfileLib, line 297
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
List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 321
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 322
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 323
    Map map = [:] // library marker kkossev.deviceProfileLib, line 324
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 325
    try { // library marker kkossev.deviceProfileLib, line 326
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 327
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 328
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 329
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 330
    } // library marker kkossev.deviceProfileLib, line 331
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLib, line 332
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 333
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 334
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLib, line 335
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 336
    } // library marker kkossev.deviceProfileLib, line 337
    if (map.mfgCode != null && map.mfgCode != '') { // library marker kkossev.deviceProfileLib, line 338
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 339
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mfgCode, delay = 50) // library marker kkossev.deviceProfileLib, line 340
    } // library marker kkossev.deviceProfileLib, line 341
    else { // library marker kkossev.deviceProfileLib, line 342
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 50) // library marker kkossev.deviceProfileLib, line 343
    } // library marker kkossev.deviceProfileLib, line 344
    return cmds // library marker kkossev.deviceProfileLib, line 345
} // library marker kkossev.deviceProfileLib, line 346

/** // library marker kkossev.deviceProfileLib, line 348
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 349
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 350
 * // library marker kkossev.deviceProfileLib, line 351
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 352
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 353
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 354
 */ // library marker kkossev.deviceProfileLib, line 355
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 356
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 357
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 358
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 359
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 360
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 361
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 362
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 363
        case 'number' : // library marker kkossev.deviceProfileLib, line 364
            // TODO - negative values ! // library marker kkossev.deviceProfileLib, line 365
            // TODO - better conversion to integer! // library marker kkossev.deviceProfileLib, line 366
            value = safeToInt(val, 0) // library marker kkossev.deviceProfileLib, line 367
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 368
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 369
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 370
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 371
            } // library marker kkossev.deviceProfileLib, line 372
            else { // library marker kkossev.deviceProfileLib, line 373
                scaledValue = value // library marker kkossev.deviceProfileLib, line 374
            } // library marker kkossev.deviceProfileLib, line 375
            break // library marker kkossev.deviceProfileLib, line 376

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 378
            value = safeToDouble(val, 0.0) // library marker kkossev.deviceProfileLib, line 379
            // scale the value // library marker kkossev.deviceProfileLib, line 380
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 381
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 382
            } // library marker kkossev.deviceProfileLib, line 383
            else { // library marker kkossev.deviceProfileLib, line 384
                scaledValue = value // library marker kkossev.deviceProfileLib, line 385
            } // library marker kkossev.deviceProfileLib, line 386
            break // library marker kkossev.deviceProfileLib, line 387

        case 'bool' : // library marker kkossev.deviceProfileLib, line 389
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 390
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 391
            else { // library marker kkossev.deviceProfileLib, line 392
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 393
                return null // library marker kkossev.deviceProfileLib, line 394
            } // library marker kkossev.deviceProfileLib, line 395
            break // library marker kkossev.deviceProfileLib, line 396
        case 'enum' : // library marker kkossev.deviceProfileLib, line 397
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 398
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 399
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 400
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 401
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 402
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 403
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 404
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 405
            } // library marker kkossev.deviceProfileLib, line 406
            else { // library marker kkossev.deviceProfileLib, line 407
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 408
            } // library marker kkossev.deviceProfileLib, line 409
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 410
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 411
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 412
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 413
                // find the key for the value // library marker kkossev.deviceProfileLib, line 414
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 415
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 416
                if (key == null) { // library marker kkossev.deviceProfileLib, line 417
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 418
                    return null // library marker kkossev.deviceProfileLib, line 419
                } // library marker kkossev.deviceProfileLib, line 420
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 421
            //return null // library marker kkossev.deviceProfileLib, line 422
            } // library marker kkossev.deviceProfileLib, line 423
            break // library marker kkossev.deviceProfileLib, line 424
        default : // library marker kkossev.deviceProfileLib, line 425
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 426
            return null // library marker kkossev.deviceProfileLib, line 427
    } // library marker kkossev.deviceProfileLib, line 428
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 429
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 430
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 431
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 432
        return null // library marker kkossev.deviceProfileLib, line 433
    } // library marker kkossev.deviceProfileLib, line 434
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 435
    return scaledValue // library marker kkossev.deviceProfileLib, line 436
} // library marker kkossev.deviceProfileLib, line 437

/** // library marker kkossev.deviceProfileLib, line 439
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 440
 * // library marker kkossev.deviceProfileLib, line 441
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 442
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 443
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 444
 */ // library marker kkossev.deviceProfileLib, line 445
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 446
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 447
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 448
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 449
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 450
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 451
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 452
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 453
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 454
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 455
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 456
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 457
    if (scaledValue == null) { // library marker kkossev.deviceProfileLib, line 458
        log.trace "$dpMap  ${dpMap.map}" // library marker kkossev.deviceProfileLib, line 459
        String helpTxt = "setPar: invalid parameter ${par} value <b>${val}</b>." // library marker kkossev.deviceProfileLib, line 460
        if (dpMap.min != null && dpMap.max != null) { helpTxt += " Must be in the range ${dpMap.min} to ${dpMap.max}" } // library marker kkossev.deviceProfileLib, line 461
        if (dpMap.map != null) { helpTxt += " Must be one of ${dpMap.map}" } // library marker kkossev.deviceProfileLib, line 462
        logInfo helpTxt // library marker kkossev.deviceProfileLib, line 463
        return false // library marker kkossev.deviceProfileLib, line 464
    } // library marker kkossev.deviceProfileLib, line 465

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 467
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 468
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 469
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 470
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 471
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 472
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 473
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 474
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 475
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 476
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 477
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 478
            return true // library marker kkossev.deviceProfileLib, line 479
        } // library marker kkossev.deviceProfileLib, line 480
        else { // library marker kkossev.deviceProfileLib, line 481
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 482
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 483
        } // library marker kkossev.deviceProfileLib, line 484
    } // library marker kkossev.deviceProfileLib, line 485
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 486
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 487
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 488
        def valMiscType // library marker kkossev.deviceProfileLib, line 489
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 490
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 491
            // find the key for the value // library marker kkossev.deviceProfileLib, line 492
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 493
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 494
            if (key == null) { // library marker kkossev.deviceProfileLib, line 495
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 496
                return false // library marker kkossev.deviceProfileLib, line 497
            } // library marker kkossev.deviceProfileLib, line 498
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 499
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 500
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 501
        } // library marker kkossev.deviceProfileLib, line 502
        else { // library marker kkossev.deviceProfileLib, line 503
            valMiscType = val // library marker kkossev.deviceProfileLib, line 504
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 505
        } // library marker kkossev.deviceProfileLib, line 506
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 507
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 508
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 509
        return true // library marker kkossev.deviceProfileLib, line 510
    } // library marker kkossev.deviceProfileLib, line 511

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 513
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 514

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 516
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 517
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 518
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 519
        // Tuya DP // library marker kkossev.deviceProfileLib, line 520
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 521
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 522
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 523
            return false // library marker kkossev.deviceProfileLib, line 524
        } // library marker kkossev.deviceProfileLib, line 525
        else { // library marker kkossev.deviceProfileLib, line 526
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 527
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 528
            return false // library marker kkossev.deviceProfileLib, line 529
        } // library marker kkossev.deviceProfileLib, line 530
    } // library marker kkossev.deviceProfileLib, line 531
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 532
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 533
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 534
        int signedIntScaled = convertSignedInts(scaledValue, dpMap) // library marker kkossev.deviceProfileLib, line 535
        cmds = zclWriteAttribute(dpMap, signedIntScaled) // library marker kkossev.deviceProfileLib, line 536
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 537
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 538
            return false // library marker kkossev.deviceProfileLib, line 539
        } // library marker kkossev.deviceProfileLib, line 540
    } // library marker kkossev.deviceProfileLib, line 541
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 542
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 543
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 544
    return true // library marker kkossev.deviceProfileLib, line 545
} // library marker kkossev.deviceProfileLib, line 546

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 548
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 549
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 550
public List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 551
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 552
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 553
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 554
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 555
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 556
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 557
        return [] // library marker kkossev.deviceProfileLib, line 558
    } // library marker kkossev.deviceProfileLib, line 559
    String dpType // library marker kkossev.deviceProfileLib, line 560
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 561
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 562
    } // library marker kkossev.deviceProfileLib, line 563
    else { // library marker kkossev.deviceProfileLib, line 564
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 565
    } // library marker kkossev.deviceProfileLib, line 566
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 567
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 568
        return [] // library marker kkossev.deviceProfileLib, line 569
    } // library marker kkossev.deviceProfileLib, line 570
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 571
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 572
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 573
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 574
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 575
    } // library marker kkossev.deviceProfileLib, line 576
    else { // library marker kkossev.deviceProfileLib, line 577
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 578
    } // library marker kkossev.deviceProfileLib, line 579
    return cmds // library marker kkossev.deviceProfileLib, line 580
} // library marker kkossev.deviceProfileLib, line 581

int convertSignedInts(int val, Map dpMap) { // library marker kkossev.deviceProfileLib, line 583
    if (dpMap.dt == '0x28') { // library marker kkossev.deviceProfileLib, line 584
        if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 585
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 586
    } // library marker kkossev.deviceProfileLib, line 587
    else if (dpMap.dt == '0x29') { // library marker kkossev.deviceProfileLib, line 588
        if (val > 32767) { return (val as int) - 65536 } // library marker kkossev.deviceProfileLib, line 589
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 590
    } // library marker kkossev.deviceProfileLib, line 591
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 592
} // library marker kkossev.deviceProfileLib, line 593

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 595
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 596
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 597
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 598
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 599
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'DEVICE.preferences is empty!' ; return false } // library marker kkossev.deviceProfileLib, line 600

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 602
    l//log.trace "sendAttribute: dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 603
    if (dpMap == null || dpMap?.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 604
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 605
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 606
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 607
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 608
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 609
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 610
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 611
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 612
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 613
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 614
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 615
        try { // library marker kkossev.deviceProfileLib, line 616
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 617
        } // library marker kkossev.deviceProfileLib, line 618
        catch (e) { // library marker kkossev.deviceProfileLib, line 619
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 620
            return false // library marker kkossev.deviceProfileLib, line 621
        } // library marker kkossev.deviceProfileLib, line 622
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 623
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 624
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 625
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 626
            return true // library marker kkossev.deviceProfileLib, line 627
        } // library marker kkossev.deviceProfileLib, line 628
        else { // library marker kkossev.deviceProfileLib, line 629
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 630
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 631
        } // library marker kkossev.deviceProfileLib, line 632
    } // library marker kkossev.deviceProfileLib, line 633
    else { // library marker kkossev.deviceProfileLib, line 634
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 635
    } // library marker kkossev.deviceProfileLib, line 636
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 637
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 638
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 639
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 640
        // patch !! // library marker kkossev.deviceProfileLib, line 641
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 642
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 643
        } // library marker kkossev.deviceProfileLib, line 644
        else { // library marker kkossev.deviceProfileLib, line 645
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 646
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 647
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 648
        } // library marker kkossev.deviceProfileLib, line 649
        return true // library marker kkossev.deviceProfileLib, line 650
    } // library marker kkossev.deviceProfileLib, line 651
    else { // library marker kkossev.deviceProfileLib, line 652
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 653
    } // library marker kkossev.deviceProfileLib, line 654
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 655
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 656
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 657
    try { // library marker kkossev.deviceProfileLib, line 658
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 659
    } // library marker kkossev.deviceProfileLib, line 660
    catch (e) { // library marker kkossev.deviceProfileLib, line 661
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 662
        return false // library marker kkossev.deviceProfileLib, line 663
    } // library marker kkossev.deviceProfileLib, line 664
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 665
        // Tuya DP // library marker kkossev.deviceProfileLib, line 666
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 667
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 668
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 669
            return false // library marker kkossev.deviceProfileLib, line 670
        } // library marker kkossev.deviceProfileLib, line 671
        else { // library marker kkossev.deviceProfileLib, line 672
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 673
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 674
            return true // library marker kkossev.deviceProfileLib, line 675
        } // library marker kkossev.deviceProfileLib, line 676
    } // library marker kkossev.deviceProfileLib, line 677
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 678
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 679
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 680
    } // library marker kkossev.deviceProfileLib, line 681
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 682
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 683
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 684
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 685
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 686
            return false // library marker kkossev.deviceProfileLib, line 687
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
 * SENDS a list of Zigbee commands to be sent to the device. // library marker kkossev.deviceProfileLib, line 700
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
    if (supportedCommandsMap == null || supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 711
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
    def func, funcResult // library marker kkossev.deviceProfileLib, line 723
    try { // library marker kkossev.deviceProfileLib, line 724
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 725
        // added 01/25/2025 : the commands now can be shorted : instead of a map kay and value 'printFingerprints':'printFingerprints' we can skip the value when it is the same:  'printFingerprints:'  - the value is the same as the key // library marker kkossev.deviceProfileLib, line 726
        if (func == null || func == '') { // library marker kkossev.deviceProfileLib, line 727
            func = command // library marker kkossev.deviceProfileLib, line 728
        } // library marker kkossev.deviceProfileLib, line 729
        if (val != null && val != '') { // library marker kkossev.deviceProfileLib, line 730
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 731
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 732
        } // library marker kkossev.deviceProfileLib, line 733
        else { // library marker kkossev.deviceProfileLib, line 734
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 735
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 736
        } // library marker kkossev.deviceProfileLib, line 737
    } // library marker kkossev.deviceProfileLib, line 738
    catch (e) { // library marker kkossev.deviceProfileLib, line 739
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 740
        return false // library marker kkossev.deviceProfileLib, line 741
    } // library marker kkossev.deviceProfileLib, line 742
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 743
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 744
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 745
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 746
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 747
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 748
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 749
        } // library marker kkossev.deviceProfileLib, line 750
    } // library marker kkossev.deviceProfileLib, line 751
    else if (funcResult == null) { // library marker kkossev.deviceProfileLib, line 752
        return false // library marker kkossev.deviceProfileLib, line 753
    } // library marker kkossev.deviceProfileLib, line 754
     else { // library marker kkossev.deviceProfileLib, line 755
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 756
        return false // library marker kkossev.deviceProfileLib, line 757
    } // library marker kkossev.deviceProfileLib, line 758
    return true // library marker kkossev.deviceProfileLib, line 759
} // library marker kkossev.deviceProfileLib, line 760

/** // library marker kkossev.deviceProfileLib, line 762
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 763
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 764
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 765
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 766
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 767
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 768
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 769
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 770
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 771
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 772
 */ // library marker kkossev.deviceProfileLib, line 773
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 774
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 775
    Map input = [:] // library marker kkossev.deviceProfileLib, line 776
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 777
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 778
    Object preference // library marker kkossev.deviceProfileLib, line 779
    try { preference = DEVICE?.preferences["$param"] } // library marker kkossev.deviceProfileLib, line 780
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 781
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 782
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } } // library marker kkossev.deviceProfileLib, line 783
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 784
    /* // library marker kkossev.deviceProfileLib, line 785
    // TODO - check if this is neccessary? isTuyaDP is not defined! // library marker kkossev.deviceProfileLib, line 786
    try { isTuyaDP = preference.isNumber() } // library marker kkossev.deviceProfileLib, line 787
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 788
    */ // library marker kkossev.deviceProfileLib, line 789
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 790
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 791
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 792
    if (foundMap == null || foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 793
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 794
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) { // library marker kkossev.deviceProfileLib, line 795
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" } // library marker kkossev.deviceProfileLib, line 796
        return [:] // library marker kkossev.deviceProfileLib, line 797
    } // library marker kkossev.deviceProfileLib, line 798
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 799
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 800
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 801
    //input.description = (foundMap.description ?: foundMap.title)?.replaceAll(/<\/?b>/, '')  // if description is not defined, use the title // library marker kkossev.deviceProfileLib, line 802
    input.description = foundMap.description ?: ''   // if description is not defined, skip it // library marker kkossev.deviceProfileLib, line 803
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 804
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 805
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 806
        } // library marker kkossev.deviceProfileLib, line 807
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 808
            if (input.description != '') { input.description += '<br>' } // library marker kkossev.deviceProfileLib, line 809
            input.description += "<i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 810
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 811
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 812
            } // library marker kkossev.deviceProfileLib, line 813
        } // library marker kkossev.deviceProfileLib, line 814
    } // library marker kkossev.deviceProfileLib, line 815
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 816
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 817
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 818
    }/* // library marker kkossev.deviceProfileLib, line 819
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 820
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 821
    }*/ // library marker kkossev.deviceProfileLib, line 822
    else { // library marker kkossev.deviceProfileLib, line 823
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 824
        return [:] // library marker kkossev.deviceProfileLib, line 825
    } // library marker kkossev.deviceProfileLib, line 826
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 827
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 828
    } // library marker kkossev.deviceProfileLib, line 829
    return input // library marker kkossev.deviceProfileLib, line 830
} // library marker kkossev.deviceProfileLib, line 831

/** // library marker kkossev.deviceProfileLib, line 833
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 834
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 835
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 836
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 837
 */ // library marker kkossev.deviceProfileLib, line 838
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 839
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 840
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 841
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 842
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 843
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 844
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 845
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 846
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 847
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 848
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 849
            } // library marker kkossev.deviceProfileLib, line 850
        } // library marker kkossev.deviceProfileLib, line 851
    } // library marker kkossev.deviceProfileLib, line 852
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 853
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 854
    } // library marker kkossev.deviceProfileLib, line 855
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 856
} // library marker kkossev.deviceProfileLib, line 857

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 859
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 860
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 861
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 862
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 863
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 864
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 865
    } // library marker kkossev.deviceProfileLib, line 866
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 867
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 868
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 869
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 870
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 871
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 872
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 873
    } else { // library marker kkossev.deviceProfileLib, line 874
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 875
    } // library marker kkossev.deviceProfileLib, line 876
} // library marker kkossev.deviceProfileLib, line 877

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLib, line 879
List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLib, line 880
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLib, line 881
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 882
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLib, line 883
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 884
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 885
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 886
            if (k != null) { // library marker kkossev.deviceProfileLib, line 887
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 888
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 889
                if (map != null) { // library marker kkossev.deviceProfileLib, line 890
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 891
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 892
                } // library marker kkossev.deviceProfileLib, line 893
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 894
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 895
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 896
                } // library marker kkossev.deviceProfileLib, line 897
            } // library marker kkossev.deviceProfileLib, line 898
        } // library marker kkossev.deviceProfileLib, line 899
    } // library marker kkossev.deviceProfileLib, line 900
    return cmds // library marker kkossev.deviceProfileLib, line 901
} // library marker kkossev.deviceProfileLib, line 902

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 904
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 905
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 906
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 907
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 908
    return cmds // library marker kkossev.deviceProfileLib, line 909
} // library marker kkossev.deviceProfileLib, line 910

// TODO ! // library marker kkossev.deviceProfileLib, line 912
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 913
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 914
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 915
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 916
    return cmds // library marker kkossev.deviceProfileLib, line 917
} // library marker kkossev.deviceProfileLib, line 918

// TODO // library marker kkossev.deviceProfileLib, line 920
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 921
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 922
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 923
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 924
    return cmds // library marker kkossev.deviceProfileLib, line 925
} // library marker kkossev.deviceProfileLib, line 926

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 928
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 929
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 930
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 931
    } // library marker kkossev.deviceProfileLib, line 932
} // library marker kkossev.deviceProfileLib, line 933

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 935
    String ps = DEVICE?.device?.powerSource // library marker kkossev.deviceProfileLib, line 936
    logDebug "initEventsDeviceProfile(${fullInit}) for deviceProfile=${state.deviceProfile} DEVICE?.device?.powerSource=${ps} ps.isEmpty()=${ps?.isEmpty()}" // library marker kkossev.deviceProfileLib, line 937
    if (ps != null && !ps.isEmpty()) { // library marker kkossev.deviceProfileLib, line 938
        sendEvent(name: 'powerSource', value: ps, descriptionText: "Power Source set to '${ps}'", type: 'digital') // library marker kkossev.deviceProfileLib, line 939
    } // library marker kkossev.deviceProfileLib, line 940
} // library marker kkossev.deviceProfileLib, line 941

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 943

// // library marker kkossev.deviceProfileLib, line 945
// called from parse() // library marker kkossev.deviceProfileLib, line 946
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 947
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 948
// // library marker kkossev.deviceProfileLib, line 949
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 950
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 951
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 952
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 953
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 954
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 955
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 956
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 957
} // library marker kkossev.deviceProfileLib, line 958

// // library marker kkossev.deviceProfileLib, line 960
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 961
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 962
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 963
// // library marker kkossev.deviceProfileLib, line 964
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 965
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 966
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 967
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 968
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 969
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 970
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 971
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 972
} // library marker kkossev.deviceProfileLib, line 973

// all DPs are spammy - sent periodically! // library marker kkossev.deviceProfileLib, line 975
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 976
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 977
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 978
    return isSpammy // library marker kkossev.deviceProfileLib, line 979
} // library marker kkossev.deviceProfileLib, line 980

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 982
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 983
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 984
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 985
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 986
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 987
    } // library marker kkossev.deviceProfileLib, line 988
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 989
} // library marker kkossev.deviceProfileLib, line 990

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 992
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 993
    boolean isEqual // library marker kkossev.deviceProfileLib, line 994
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 995
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 996
    } // library marker kkossev.deviceProfileLib, line 997
    else { // library marker kkossev.deviceProfileLib, line 998
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 999
    } // library marker kkossev.deviceProfileLib, line 1000
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 1001
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1002
} // library marker kkossev.deviceProfileLib, line 1003

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 1005
    Double convertedValue // library marker kkossev.deviceProfileLib, line 1006
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1007
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 1008
    } // library marker kkossev.deviceProfileLib, line 1009
    else { // library marker kkossev.deviceProfileLib, line 1010
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1011
    } // library marker kkossev.deviceProfileLib, line 1012
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1013
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1014
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1015
} // library marker kkossev.deviceProfileLib, line 1016

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1018
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1019
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1020
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1021
    def convertedValue // library marker kkossev.deviceProfileLib, line 1022
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1023
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1024
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1025
    } // library marker kkossev.deviceProfileLib, line 1026
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1027
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1028
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1029
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1030
            isEqual = false // library marker kkossev.deviceProfileLib, line 1031
        } // library marker kkossev.deviceProfileLib, line 1032
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1033
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1034
        } // library marker kkossev.deviceProfileLib, line 1035
    } // library marker kkossev.deviceProfileLib, line 1036
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1037
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1038
} // library marker kkossev.deviceProfileLib, line 1039

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1041
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1042
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1043
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1044
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1045
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1046
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1047
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1048
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1049
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1050
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1051
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1052
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1053
            break // library marker kkossev.deviceProfileLib, line 1054
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1055
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1056
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1057
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1058
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1059
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1060
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1061
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1062
            } // library marker kkossev.deviceProfileLib, line 1063
            else { // library marker kkossev.deviceProfileLib, line 1064
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1065
            } // library marker kkossev.deviceProfileLib, line 1066
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1067
            break // library marker kkossev.deviceProfileLib, line 1068
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1069
        case 'number' : // library marker kkossev.deviceProfileLib, line 1070
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1071
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1072
            break // library marker kkossev.deviceProfileLib, line 1073
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1074
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1075
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1076
            break // library marker kkossev.deviceProfileLib, line 1077
        default : // library marker kkossev.deviceProfileLib, line 1078
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1079
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1080
    } // library marker kkossev.deviceProfileLib, line 1081
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1082
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1083
    } // library marker kkossev.deviceProfileLib, line 1084
    // // library marker kkossev.deviceProfileLib, line 1085
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1086
} // library marker kkossev.deviceProfileLib, line 1087

// // library marker kkossev.deviceProfileLib, line 1089
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1090
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1091
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1092
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1093
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1094
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1095
// // library marker kkossev.deviceProfileLib, line 1096
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1097
// // library marker kkossev.deviceProfileLib, line 1098
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1099
// // library marker kkossev.deviceProfileLib, line 1100
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1101
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1102
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1103
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1104
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1105
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1106
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1107
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1108
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1109
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1110
            break // library marker kkossev.deviceProfileLib, line 1111
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1112
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1113
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1114
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1115
            logTrace "latestEvent is ${latestEvent} dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1116
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1117
            if (dataType == null || dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1118
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1119
            } // library marker kkossev.deviceProfileLib, line 1120
            else { // library marker kkossev.deviceProfileLib, line 1121
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1122
            } // library marker kkossev.deviceProfileLib, line 1123
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1124
            break // library marker kkossev.deviceProfileLib, line 1125
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1126
        case 'number' : // library marker kkossev.deviceProfileLib, line 1127
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1128
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1129
            break // library marker kkossev.deviceProfileLib, line 1130
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1131
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1132
            break // library marker kkossev.deviceProfileLib, line 1133
        default : // library marker kkossev.deviceProfileLib, line 1134
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1135
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1136
    } // library marker kkossev.deviceProfileLib, line 1137
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1138
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1139
} // library marker kkossev.deviceProfileLib, line 1140

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1142
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1143
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1144
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1145
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1146
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1147
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1148
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1149
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1150
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1151
    } // library marker kkossev.deviceProfileLib, line 1152
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1153
    try { // library marker kkossev.deviceProfileLib, line 1154
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1155
    } // library marker kkossev.deviceProfileLib, line 1156
    catch (e) { // library marker kkossev.deviceProfileLib, line 1157
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1158
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1159
    } // library marker kkossev.deviceProfileLib, line 1160
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1161
    return fncmd // library marker kkossev.deviceProfileLib, line 1162
} // library marker kkossev.deviceProfileLib, line 1163

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1165
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1166
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1167
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1168
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1169
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1170
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1171

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1173
    if (attribMap == null || attribMap?.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1174

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1176
    int value // library marker kkossev.deviceProfileLib, line 1177
    try { // library marker kkossev.deviceProfileLib, line 1178
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1179
    } // library marker kkossev.deviceProfileLib, line 1180
    catch (e) { // library marker kkossev.deviceProfileLib, line 1181
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1182
        return false // library marker kkossev.deviceProfileLib, line 1183
    } // library marker kkossev.deviceProfileLib, line 1184
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1185
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1186
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1187
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1188
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1189
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1190
        return false // library marker kkossev.deviceProfileLib, line 1191
    } // library marker kkossev.deviceProfileLib, line 1192
    value = convertSignedInts(value, foundItem) // library marker kkossev.deviceProfileLib, line 1193
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1194
} // library marker kkossev.deviceProfileLib, line 1195

/** // library marker kkossev.deviceProfileLib, line 1197
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1198
 * // library marker kkossev.deviceProfileLib, line 1199
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1200
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1201
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1202
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1203
 * // library marker kkossev.deviceProfileLib, line 1204
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1205
 */ // library marker kkossev.deviceProfileLib, line 1206
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1207
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1208
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1209
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1210
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1211

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1213
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1214

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1216
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1217
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1218
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1219
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1220
        return false // library marker kkossev.deviceProfileLib, line 1221
    } // library marker kkossev.deviceProfileLib, line 1222
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1223
} // library marker kkossev.deviceProfileLib, line 1224

/* // library marker kkossev.deviceProfileLib, line 1226
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1227
 */ // library marker kkossev.deviceProfileLib, line 1228
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1229
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1230
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1231
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1232
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1233
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1234
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1235
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1236
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1237
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1238
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1239
        } // library marker kkossev.deviceProfileLib, line 1240
    } // library marker kkossev.deviceProfileLib, line 1241
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1242

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1244
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1245
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1246
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1247
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1248
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences?.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1249
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1250
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1251
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1252
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1253
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1254
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1255
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1256
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1257
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1258
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1259
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1260

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1262
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1263
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1264
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1265
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1266
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1267
            if (settings.logEnable) { logInfo "${descText} (Debug logging is enabled)" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1268
        } // library marker kkossev.deviceProfileLib, line 1269
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1270
    } // library marker kkossev.deviceProfileLib, line 1271

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1273
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1274
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1275
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1276
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1277
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1278
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1279
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1280
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1281
            } // library marker kkossev.deviceProfileLib, line 1282
        } // library marker kkossev.deviceProfileLib, line 1283
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1284
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1285
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1286
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1287
            } // library marker kkossev.deviceProfileLib, line 1288
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1289
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1290
            try { // library marker kkossev.deviceProfileLib, line 1291
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1292
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1293
            } // library marker kkossev.deviceProfileLib, line 1294
            catch (e) { // library marker kkossev.deviceProfileLib, line 1295
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1296
            } // library marker kkossev.deviceProfileLib, line 1297
        } // library marker kkossev.deviceProfileLib, line 1298
    } // library marker kkossev.deviceProfileLib, line 1299
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1300
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1301
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1302
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1303
    } // library marker kkossev.deviceProfileLib, line 1304

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1306
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1307
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1308
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1309
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1310
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1311
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1312
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1313
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1314
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1315
            } // library marker kkossev.deviceProfileLib, line 1316
            if (foundItem.processDuplicated == true) { // library marker kkossev.deviceProfileLib, line 1317
                logDebug 'processDuplicated=true -> continue' // library marker kkossev.deviceProfileLib, line 1318
            } // library marker kkossev.deviceProfileLib, line 1319

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1321
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1322
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1323
            // continue ... // library marker kkossev.deviceProfileLib, line 1324
            } // library marker kkossev.deviceProfileLib, line 1325

            else { // library marker kkossev.deviceProfileLib, line 1327
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1328
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1329
                } // library marker kkossev.deviceProfileLib, line 1330
                else { // library marker kkossev.deviceProfileLib, line 1331
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLib, line 1332
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1333
                } // library marker kkossev.deviceProfileLib, line 1334
            } // library marker kkossev.deviceProfileLib, line 1335
        } // library marker kkossev.deviceProfileLib, line 1336

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1338
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1339
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1340
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1341
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1342
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1343
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1344
        } // library marker kkossev.deviceProfileLib, line 1345
        else { // library marker kkossev.deviceProfileLib, line 1346
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1347
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1348
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1349
                logTrace "event ${name} sent w/ valueScaled ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1350
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1351
            } // library marker kkossev.deviceProfileLib, line 1352
        } // library marker kkossev.deviceProfileLib, line 1353
    } // library marker kkossev.deviceProfileLib, line 1354
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1355
} // library marker kkossev.deviceProfileLib, line 1356

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1358
public boolean validateAndFixPreferences(String debugStr) { return validateAndFixPreferences(debugStr.toBoolean() as boolean) } // library marker kkossev.deviceProfileLib, line 1359
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1360
    //debug = true // library marker kkossev.deviceProfileLib, line 1361
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1362
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLib, line 1363
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLib, line 1364
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1365
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLib, line 1366
    String settingType = '' // library marker kkossev.deviceProfileLib, line 1367
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1368
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1369
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1370
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1371
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1372
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1373
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1374
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1375
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1376
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1377
            try { // library marker kkossev.deviceProfileLib, line 1378
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1379
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1380
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLib, line 1381
            } // library marker kkossev.deviceProfileLib, line 1382
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1383
            try { // library marker kkossev.deviceProfileLib, line 1384
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1385
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1386
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1387
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1388
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1389
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1390
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1391
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1392
                    } // library marker kkossev.deviceProfileLib, line 1393
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1394
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1395
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1396
                    } else { // library marker kkossev.deviceProfileLib, line 1397
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1398
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1399
                    } // library marker kkossev.deviceProfileLib, line 1400
                } // library marker kkossev.deviceProfileLib, line 1401
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1402
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1403
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1404
            } // library marker kkossev.deviceProfileLib, line 1405
            catch (e) { // library marker kkossev.deviceProfileLib, line 1406
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1407
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1408
                try { // library marker kkossev.deviceProfileLib, line 1409
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1410
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1411
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1412
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1413
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1414
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLib, line 1415
                } // library marker kkossev.deviceProfileLib, line 1416
            } // library marker kkossev.deviceProfileLib, line 1417
        } // library marker kkossev.deviceProfileLib, line 1418
        total ++ // library marker kkossev.deviceProfileLib, line 1419
    } // library marker kkossev.deviceProfileLib, line 1420
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1421
    return true // library marker kkossev.deviceProfileLib, line 1422
} // library marker kkossev.deviceProfileLib, line 1423

public String fingerprintIt(Map profileMap, Map fingerprint) { // library marker kkossev.deviceProfileLib, line 1425
    if (profileMap == null) { return 'profileMap is null' } // library marker kkossev.deviceProfileLib, line 1426
    if (fingerprint == null) { return 'fingerprint is null' } // library marker kkossev.deviceProfileLib, line 1427
    Map defaultFingerprint = profileMap.defaultFingerprint ?: [:] // library marker kkossev.deviceProfileLib, line 1428
    // if there is no defaultFingerprint, use the fingerprint as is // library marker kkossev.deviceProfileLib, line 1429
    if (defaultFingerprint == [:]) { // library marker kkossev.deviceProfileLib, line 1430
        return fingerprint.toString() // library marker kkossev.deviceProfileLib, line 1431
    } // library marker kkossev.deviceProfileLib, line 1432
    // for the missing keys, use the default values // library marker kkossev.deviceProfileLib, line 1433
    String fingerprintStr = '' // library marker kkossev.deviceProfileLib, line 1434
    defaultFingerprint.each { key, value -> // library marker kkossev.deviceProfileLib, line 1435
        String keyValue = fingerprint[key] ?: value // library marker kkossev.deviceProfileLib, line 1436
        fingerprintStr += "${key}:'${keyValue}', " // library marker kkossev.deviceProfileLib, line 1437
    } // library marker kkossev.deviceProfileLib, line 1438
    // remove the last comma and space // library marker kkossev.deviceProfileLib, line 1439
    fingerprintStr = fingerprintStr[0..-3] // library marker kkossev.deviceProfileLib, line 1440
    return fingerprintStr // library marker kkossev.deviceProfileLib, line 1441
} // library marker kkossev.deviceProfileLib, line 1442

public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1444
    int count = 0 // library marker kkossev.deviceProfileLib, line 1445
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1446
        logInfo "Device Profile: ${profileName}" // library marker kkossev.deviceProfileLib, line 1447
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1448
            log.info "${fingerprintIt(profileMap, fingerprint)}" // library marker kkossev.deviceProfileLib, line 1449
            count++ // library marker kkossev.deviceProfileLib, line 1450
        } // library marker kkossev.deviceProfileLib, line 1451
    } // library marker kkossev.deviceProfileLib, line 1452
    logInfo "Total fingerprints: ${count}" // library marker kkossev.deviceProfileLib, line 1453
} // library marker kkossev.deviceProfileLib, line 1454

public void printPreferences() { // library marker kkossev.deviceProfileLib, line 1456
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 1457
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 1458
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 1459
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLib, line 1460
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 1461
                log.info inputMap // library marker kkossev.deviceProfileLib, line 1462
            } // library marker kkossev.deviceProfileLib, line 1463
        } // library marker kkossev.deviceProfileLib, line 1464
    } // library marker kkossev.deviceProfileLib, line 1465
} // library marker kkossev.deviceProfileLib, line 1466

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDouble, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', documentationLink: '', // library marker kkossev.commonLib, line 4
    version: '3.3.4' // library marker kkossev.commonLib, line 5
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
  * ver. 3.3.1  2024-07-06 kkossev  - removed isFingerbot() dependancy; added FC03 cluster (Frient); removed noDef from the linter; added customParseIasMessage and standardParseIasMessage; powerSource set to unknown on initialize(); // library marker kkossev.commonLib, line 40
  * ver. 3.3.2  2024-07-12 kkossev  - added PollControl (0x0020) cluster; ping for SONOFF // library marker kkossev.commonLib, line 41
  * ver. 3.3.3  2024-09-15 kkossev  - added queryAllTuyaDP(); 2 minutes healthCheck option; // library marker kkossev.commonLib, line 42
  * ver. 3.3.4  2025-01-29 kkossev  - 'LOAD ALL DEFAULTS' is the default Configure command. // library marker kkossev.commonLib, line 43
  * ver. 3.3.5  2025-02-16 kkossev  - (dev.branch) getTuyaAttributeValue made public // library marker kkossev.commonLib, line 44
  * // library marker kkossev.commonLib, line 45
  *                                   TODO: check deviceCommandTimeout() // library marker kkossev.commonLib, line 46
  *                                   TODO: offlineCtr is not increasing! (ZBMicro); // library marker kkossev.commonLib, line 47
  *                                   TODO: when device rejoins the network, read the battery percentage again (probably in custom handler, not for all devices) // library marker kkossev.commonLib, line 48
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 49
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 50
  *                                   TODO: MOVE ZDO counters to health state? // library marker kkossev.commonLib, line 51
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 52
  *                                   TODO: Versions of the main module + included libraries (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 53
  *                                   TODO: add GetInfo (endpoints list) command (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 54
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 55
  * // library marker kkossev.commonLib, line 56
*/ // library marker kkossev.commonLib, line 57

String commonLibVersion() { '3.3.5' } // library marker kkossev.commonLib, line 59
String commonLibStamp() { '2025/02/16 9:46 AM' } // library marker kkossev.commonLib, line 60

import groovy.transform.Field // library marker kkossev.commonLib, line 62
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 63
import hubitat.device.Protocol // library marker kkossev.commonLib, line 64
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 65
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 66
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 67
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 68
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 69
import java.math.BigDecimal // library marker kkossev.commonLib, line 70

metadata { // library marker kkossev.commonLib, line 72
        if (_DEBUG) { // library marker kkossev.commonLib, line 73
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 74
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 75
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 76
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 77
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 78
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 79
            ] // library marker kkossev.commonLib, line 80
        } // library marker kkossev.commonLib, line 81

        // common capabilities for all device types // library marker kkossev.commonLib, line 83
        capability 'Configuration' // library marker kkossev.commonLib, line 84
        capability 'Refresh' // library marker kkossev.commonLib, line 85
        capability 'HealthCheck' // library marker kkossev.commonLib, line 86
        capability 'PowerSource'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 87

        // common attributes for all device types // library marker kkossev.commonLib, line 89
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 90
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 91
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 92

        // common commands for all device types // library marker kkossev.commonLib, line 94
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 95

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 97
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 98

    preferences { // library marker kkossev.commonLib, line 100
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 101
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 102
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 103

        if (device) { // library marker kkossev.commonLib, line 105
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device...', defaultValue: false // library marker kkossev.commonLib, line 106
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 107
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 108
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 109
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 110
            } // library marker kkossev.commonLib, line 111
        } // library marker kkossev.commonLib, line 112
    } // library marker kkossev.commonLib, line 113
} // library marker kkossev.commonLib, line 114

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 116
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 117
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 118
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 119
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 120
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 121
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 122
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 123
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 124
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 125
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 126

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 128
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 129
] // library marker kkossev.commonLib, line 130
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 131
    defaultValue: 240, options: [2: 'Every 2 Mins', 10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 132
] // library marker kkossev.commonLib, line 133

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 135
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'], // library marker kkossev.commonLib, line 136
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 137
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 138
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 139
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 140
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 141
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 142
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 143
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 144
    '           -             '  : [key:1, function: 'configureHelp'] // library marker kkossev.commonLib, line 145
] // library marker kkossev.commonLib, line 146

public boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 148

/** // library marker kkossev.commonLib, line 150
 * Parse Zigbee message // library marker kkossev.commonLib, line 151
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 152
 */ // library marker kkossev.commonLib, line 153
public void parse(final String description) { // library marker kkossev.commonLib, line 154
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 155
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 156
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 157
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 158

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 160
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 161
        if (this.respondsTo('customParseIasMessage')) { customParseIasMessage(description) } // library marker kkossev.commonLib, line 162
        else if (this.respondsTo('standardParseIasMessage')) { standardParseIasMessage(description) } // library marker kkossev.commonLib, line 163
        else if (this.respondsTo('parseIasMessage')) { parseIasMessage(description) } // library marker kkossev.commonLib, line 164
        else { logDebug "ignored IAS zone status (no IAS parser) description: $description" } // library marker kkossev.commonLib, line 165
        return // library marker kkossev.commonLib, line 166
    } // library marker kkossev.commonLib, line 167
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 168
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 169
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 170
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 171
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 172
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 173
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 174
        return // library marker kkossev.commonLib, line 175
    } // library marker kkossev.commonLib, line 176

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 178
        return // library marker kkossev.commonLib, line 179
    } // library marker kkossev.commonLib, line 180
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 181

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 183
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 184

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 186
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 187
        return // library marker kkossev.commonLib, line 188
    } // library marker kkossev.commonLib, line 189
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 190
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 191
        return // library marker kkossev.commonLib, line 192
    } // library marker kkossev.commonLib, line 193
    // // library marker kkossev.commonLib, line 194
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 195
    // // library marker kkossev.commonLib, line 196
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 197
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 198
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 199
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 200
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 201
            } // library marker kkossev.commonLib, line 202
            break // library marker kkossev.commonLib, line 203
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 204
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 205
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 206
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 207
            } // library marker kkossev.commonLib, line 208
            break // library marker kkossev.commonLib, line 209
        default: // library marker kkossev.commonLib, line 210
            if (settings.logEnable) { // library marker kkossev.commonLib, line 211
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 212
            } // library marker kkossev.commonLib, line 213
            break // library marker kkossev.commonLib, line 214
    } // library marker kkossev.commonLib, line 215
} // library marker kkossev.commonLib, line 216

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 218
    0x0000: 'Basic',             0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x0006: 'OnOff',           0x0008: 'LevelControl',  // library marker kkossev.commonLib, line 219
    0x000C: 'AnalogInput',       0x0012: 'MultistateInput',  0x0020: 'PollControl',      0x0102: 'WindowCovering',   0x0201: 'Thermostat',  0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 220
    0x0400: 'Illuminance',       0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 221
    0x0B04: 'ElectricalMeasure', 0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC03: 'FC03',            0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 222
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 223
] // library marker kkossev.commonLib, line 224

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 226
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 227
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

private static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 255
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 256
} // library marker kkossev.commonLib, line 257

public boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 259
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 260
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 261
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 262
    } // library marker kkossev.commonLib, line 263
    return false // library marker kkossev.commonLib, line 264
} // library marker kkossev.commonLib, line 265

public boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 267
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 268
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 269
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 270
    } // library marker kkossev.commonLib, line 271
    return false // library marker kkossev.commonLib, line 272
} // library marker kkossev.commonLib, line 273

public boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 275
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
private void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 290
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
private void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 341
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
private void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 365
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
private void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 379
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
private void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 392
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
private void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 408
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

private Boolean executeCustomHandler(String handlerName, Object handlerArgs) { // library marker kkossev.commonLib, line 429
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 430
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 431
        return false // library marker kkossev.commonLib, line 432
    } // library marker kkossev.commonLib, line 433
    // execute the customHandler function // library marker kkossev.commonLib, line 434
    Boolean result = false // library marker kkossev.commonLib, line 435
    try { // library marker kkossev.commonLib, line 436
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 437
    } // library marker kkossev.commonLib, line 438
    catch (e) { // library marker kkossev.commonLib, line 439
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 440
        return false // library marker kkossev.commonLib, line 441
    } // library marker kkossev.commonLib, line 442
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 443
    return result // library marker kkossev.commonLib, line 444
} // library marker kkossev.commonLib, line 445

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 447
private void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 448
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 449
    final String commandId = data[0] // library marker kkossev.commonLib, line 450
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 451
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 452
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 453
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 454
    } else { // library marker kkossev.commonLib, line 455
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 456
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 457
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 458
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 459
        } // library marker kkossev.commonLib, line 460
    } // library marker kkossev.commonLib, line 461
} // library marker kkossev.commonLib, line 462

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 464
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 465
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 466
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 467

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 469
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 470
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 471
] // library marker kkossev.commonLib, line 472

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 474
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 475
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 476
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 477
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 478
] // library marker kkossev.commonLib, line 479

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 481
private BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 482
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 483
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 484
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 485
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 486
    return avg // library marker kkossev.commonLib, line 487
} // library marker kkossev.commonLib, line 488

void handlePingResponse() { // library marker kkossev.commonLib, line 490
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 491
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 492
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 493

    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 495
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 496
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 497
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 498
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 499
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 500
        sendRttEvent() // library marker kkossev.commonLib, line 501
    } // library marker kkossev.commonLib, line 502
    else { // library marker kkossev.commonLib, line 503
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 504
    } // library marker kkossev.commonLib, line 505
    state.states['isPing'] = false // library marker kkossev.commonLib, line 506
} // library marker kkossev.commonLib, line 507

/* // library marker kkossev.commonLib, line 509
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 510
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 511
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 512
*/ // library marker kkossev.commonLib, line 513
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 514

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 516
private void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 517
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 518
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 519
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 520
    boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 521
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 522
        case 0x0000: // library marker kkossev.commonLib, line 523
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 524
            break // library marker kkossev.commonLib, line 525
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 526
            if (isPing) { // library marker kkossev.commonLib, line 527
                handlePingResponse() // library marker kkossev.commonLib, line 528
            } // library marker kkossev.commonLib, line 529
            else { // library marker kkossev.commonLib, line 530
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 531
            } // library marker kkossev.commonLib, line 532
            break // library marker kkossev.commonLib, line 533
        case 0x0004: // library marker kkossev.commonLib, line 534
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 535
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 536
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 537
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 538
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 539
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 540
            } // library marker kkossev.commonLib, line 541
            break // library marker kkossev.commonLib, line 542
        case 0x0005: // library marker kkossev.commonLib, line 543
            if (isPing) { // library marker kkossev.commonLib, line 544
                handlePingResponse() // library marker kkossev.commonLib, line 545
            } // library marker kkossev.commonLib, line 546
            else { // library marker kkossev.commonLib, line 547
                logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 548
                // received device model Remote Control N2 // library marker kkossev.commonLib, line 549
                String model = device.getDataValue('model') // library marker kkossev.commonLib, line 550
                if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 551
                    logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 552
                    device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 553
                } // library marker kkossev.commonLib, line 554
            } // library marker kkossev.commonLib, line 555
            break // library marker kkossev.commonLib, line 556
        case 0x0007: // library marker kkossev.commonLib, line 557
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 558
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 559
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 560
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 561
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 562
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 563
            } // library marker kkossev.commonLib, line 564
            break // library marker kkossev.commonLib, line 565
        case 0xFFDF: // library marker kkossev.commonLib, line 566
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 567
            break // library marker kkossev.commonLib, line 568
        case 0xFFE2: // library marker kkossev.commonLib, line 569
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 570
            break // library marker kkossev.commonLib, line 571
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 572
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 573
            break // library marker kkossev.commonLib, line 574
        case 0xFFFE: // library marker kkossev.commonLib, line 575
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 576
            break // library marker kkossev.commonLib, line 577
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 578
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 579
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 580
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 581
            break // library marker kkossev.commonLib, line 582
        default: // library marker kkossev.commonLib, line 583
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 584
            break // library marker kkossev.commonLib, line 585
    } // library marker kkossev.commonLib, line 586
} // library marker kkossev.commonLib, line 587

private void standardParsePollControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 589
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 590
        case 0x0000: logDebug "PollControl cluster: CheckInInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 591
        case 0x0001: logDebug "PollControl cluster: LongPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 592
        case 0x0002: logDebug "PollControl cluster: ShortPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 593
        case 0x0003: logDebug "PollControl cluster: FastPollTimeout = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 594
        case 0x0004: logDebug "PollControl cluster: CheckInIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 595
        case 0x0005: logDebug "PollControl cluster: LongPollIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 596
        case 0x0006: logDebug "PollControl cluster: FastPollTimeoutMax = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 597
        default: logWarn "zigbee received unknown PollControl cluster attribute 0x${descMap.attrId} (value ${descMap.value})" ; break // library marker kkossev.commonLib, line 598
    } // library marker kkossev.commonLib, line 599
} // library marker kkossev.commonLib, line 600

public void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 602
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 603
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 604

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 606
    Map descMap = [:] // library marker kkossev.commonLib, line 607
    try { // library marker kkossev.commonLib, line 608
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 609
    } // library marker kkossev.commonLib, line 610
    catch (e1) { // library marker kkossev.commonLib, line 611
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 612
        // try alternative custom parsing // library marker kkossev.commonLib, line 613
        descMap = [:] // library marker kkossev.commonLib, line 614
        try { // library marker kkossev.commonLib, line 615
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 616
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 617
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 618
            } // library marker kkossev.commonLib, line 619
        } // library marker kkossev.commonLib, line 620
        catch (e2) { // library marker kkossev.commonLib, line 621
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 622
            return [:] // library marker kkossev.commonLib, line 623
        } // library marker kkossev.commonLib, line 624
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 625
    } // library marker kkossev.commonLib, line 626
    return descMap // library marker kkossev.commonLib, line 627
} // library marker kkossev.commonLib, line 628

private boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 630
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 631
        return false // library marker kkossev.commonLib, line 632
    } // library marker kkossev.commonLib, line 633
    // try to parse ... // library marker kkossev.commonLib, line 634
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 635
    Map descMap = [:] // library marker kkossev.commonLib, line 636
    try { // library marker kkossev.commonLib, line 637
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 638
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 639
    } // library marker kkossev.commonLib, line 640
    catch (e) { // library marker kkossev.commonLib, line 641
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 642
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 643
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 644
        return true // library marker kkossev.commonLib, line 645
    } // library marker kkossev.commonLib, line 646

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 648
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 649
    } // library marker kkossev.commonLib, line 650
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 651
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 652
    } // library marker kkossev.commonLib, line 653
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 654
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 655
    } // library marker kkossev.commonLib, line 656
    else { // library marker kkossev.commonLib, line 657
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 658
        return false // library marker kkossev.commonLib, line 659
    } // library marker kkossev.commonLib, line 660
    return true    // processed // library marker kkossev.commonLib, line 661
} // library marker kkossev.commonLib, line 662

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 664
private boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 665
  /* // library marker kkossev.commonLib, line 666
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 667
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 668
        return true // library marker kkossev.commonLib, line 669
    } // library marker kkossev.commonLib, line 670
*/ // library marker kkossev.commonLib, line 671
    Map descMap = [:] // library marker kkossev.commonLib, line 672
    try { // library marker kkossev.commonLib, line 673
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 674
    } // library marker kkossev.commonLib, line 675
    catch (e1) { // library marker kkossev.commonLib, line 676
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 677
        // try alternative custom parsing // library marker kkossev.commonLib, line 678
        descMap = [:] // library marker kkossev.commonLib, line 679
        try { // library marker kkossev.commonLib, line 680
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 681
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 682
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 683
            } // library marker kkossev.commonLib, line 684
        } // library marker kkossev.commonLib, line 685
        catch (e2) { // library marker kkossev.commonLib, line 686
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 687
            return true // library marker kkossev.commonLib, line 688
        } // library marker kkossev.commonLib, line 689
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 690
    } // library marker kkossev.commonLib, line 691
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 692
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 693
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 694
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 695
        return false // library marker kkossev.commonLib, line 696
    } // library marker kkossev.commonLib, line 697
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 698
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 699
    // attribute report received // library marker kkossev.commonLib, line 700
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 701
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 702
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 703
    } // library marker kkossev.commonLib, line 704
    attrData.each { // library marker kkossev.commonLib, line 705
        if (it.status == '86') { // library marker kkossev.commonLib, line 706
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 707
        // TODO - skip parsing? // library marker kkossev.commonLib, line 708
        } // library marker kkossev.commonLib, line 709
        switch (it.cluster) { // library marker kkossev.commonLib, line 710
            case '0000' : // library marker kkossev.commonLib, line 711
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 712
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 713
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 714
                } // library marker kkossev.commonLib, line 715
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 716
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 717
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 718
                } // library marker kkossev.commonLib, line 719
                else { // library marker kkossev.commonLib, line 720
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 721
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 722
                } // library marker kkossev.commonLib, line 723
                break // library marker kkossev.commonLib, line 724
            default : // library marker kkossev.commonLib, line 725
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 726
                break // library marker kkossev.commonLib, line 727
        } // switch // library marker kkossev.commonLib, line 728
    } // for each attribute // library marker kkossev.commonLib, line 729
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 730
} // library marker kkossev.commonLib, line 731

public String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 733
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 734
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 735
} // library marker kkossev.commonLib, line 736

public String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 738
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 739
} // library marker kkossev.commonLib, line 740

/* // library marker kkossev.commonLib, line 742
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 743
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 744
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 745
*/ // library marker kkossev.commonLib, line 746
private static int getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 747
private static int getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 748
private static int getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 749

// Tuya Commands // library marker kkossev.commonLib, line 751
private static int getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 752
private static int getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 753
private static int getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 754
private static int getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 755
private static int getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 756

// tuya DP type // library marker kkossev.commonLib, line 758
private static String getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 759
private static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 760
private static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 761
private static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 762
private static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 763
private static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 764

private void syncTuyaDateTime() { // library marker kkossev.commonLib, line 766
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 767
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 768
    long offset = 0 // library marker kkossev.commonLib, line 769
    int offsetHours = 0 // library marker kkossev.commonLib, line 770
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 771
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 772
    try { // library marker kkossev.commonLib, line 773
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 774
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 775
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 776
    } catch (e) { // library marker kkossev.commonLib, line 777
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 778
    } // library marker kkossev.commonLib, line 779
    // // library marker kkossev.commonLib, line 780
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 781
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 782
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 783
} // library marker kkossev.commonLib, line 784

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 786
public void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 787
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 788
        syncTuyaDateTime() // library marker kkossev.commonLib, line 789
    } // library marker kkossev.commonLib, line 790
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 791
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 792
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 793
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 794
        if (status != '00') { // library marker kkossev.commonLib, line 795
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 796
        } // library marker kkossev.commonLib, line 797
    } // library marker kkossev.commonLib, line 798
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 799
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 800
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 801
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 802
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 803
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 804
            return // library marker kkossev.commonLib, line 805
        } // library marker kkossev.commonLib, line 806
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 807
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 808
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 809
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 810
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 811
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 812
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 813
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 814
            } // library marker kkossev.commonLib, line 815
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 816
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 817
        } // library marker kkossev.commonLib, line 818
    } // library marker kkossev.commonLib, line 819
    else { // library marker kkossev.commonLib, line 820
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 821
    } // library marker kkossev.commonLib, line 822
} // library marker kkossev.commonLib, line 823

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 825
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 826
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 827
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 828
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 829
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 830
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 831
        } // library marker kkossev.commonLib, line 832
    } // library marker kkossev.commonLib, line 833
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 834
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 835
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 836
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 837
        } // library marker kkossev.commonLib, line 838
    } // library marker kkossev.commonLib, line 839
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 840
} // library marker kkossev.commonLib, line 841

public int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 843
    int retValue = 0 // library marker kkossev.commonLib, line 844
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 845
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 846
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 847
        int power = 1 // library marker kkossev.commonLib, line 848
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 849
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 850
            power = power * 256 // library marker kkossev.commonLib, line 851
        } // library marker kkossev.commonLib, line 852
    } // library marker kkossev.commonLib, line 853
    return retValue // library marker kkossev.commonLib, line 854
} // library marker kkossev.commonLib, line 855

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 857

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 859
    List<String> cmds = [] // library marker kkossev.commonLib, line 860
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 861
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 862
    //int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 863
    int tuyaCmd // library marker kkossev.commonLib, line 864
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified!\ // library marker kkossev.commonLib, line 865
    if (this.respondsTo('getDEVICE') && DEVICE?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 866
        tuyaCmd = DEVICE?.device?.tuyaCmd // library marker kkossev.commonLib, line 867
    } // library marker kkossev.commonLib, line 868
    else { // library marker kkossev.commonLib, line 869
        tuyaCmd = /*isFingerbot() ? 0x04 : */ tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 870
    } // library marker kkossev.commonLib, line 871
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 872
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 873
    return cmds // library marker kkossev.commonLib, line 874
} // library marker kkossev.commonLib, line 875

private String getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 877

public void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 879
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 880
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 881
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 882
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 883
} // library marker kkossev.commonLib, line 884


public List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 887
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 888
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 889
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 890
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 891
} // library marker kkossev.commonLib, line 892

List<String> queryAllTuyaDP() { // library marker kkossev.commonLib, line 894
    logTrace 'queryAllTuyaDP()' // library marker kkossev.commonLib, line 895
    List<String> cmds = zigbee.command(0xEF00, 0x03) // library marker kkossev.commonLib, line 896
    return cmds // library marker kkossev.commonLib, line 897
} // library marker kkossev.commonLib, line 898

public void aqaraBlackMagic() { // library marker kkossev.commonLib, line 900
    List<String> cmds = [] // library marker kkossev.commonLib, line 901
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 902
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 903
    } // library marker kkossev.commonLib, line 904
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 905
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 906
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 907
        return // library marker kkossev.commonLib, line 908
    } // library marker kkossev.commonLib, line 909
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 910
} // library marker kkossev.commonLib, line 911

// Invoked from configure() // library marker kkossev.commonLib, line 913
public List<String> initializeDevice() { // library marker kkossev.commonLib, line 914
    List<String> cmds = [] // library marker kkossev.commonLib, line 915
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 916
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 917
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 918
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 919
    } // library marker kkossev.commonLib, line 920
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 921
    return cmds // library marker kkossev.commonLib, line 922
} // library marker kkossev.commonLib, line 923

// Invoked from configure() // library marker kkossev.commonLib, line 925
public List<String> configureDevice() { // library marker kkossev.commonLib, line 926
    List<String> cmds = [] // library marker kkossev.commonLib, line 927
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 928
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 929
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 930
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 931
    } // library marker kkossev.commonLib, line 932
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 933
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 934
    return cmds // library marker kkossev.commonLib, line 935
} // library marker kkossev.commonLib, line 936

/* // library marker kkossev.commonLib, line 938
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 939
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 940
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 941
*/ // library marker kkossev.commonLib, line 942

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 944
    List<String> cmds = [] // library marker kkossev.commonLib, line 945
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 946
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 947
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 948
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 949
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 950
            } // library marker kkossev.commonLib, line 951
        } // library marker kkossev.commonLib, line 952
    } // library marker kkossev.commonLib, line 953
    return cmds // library marker kkossev.commonLib, line 954
} // library marker kkossev.commonLib, line 955

void refresh() { // library marker kkossev.commonLib, line 957
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 958
    checkDriverVersion(state) // library marker kkossev.commonLib, line 959
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 960
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 961
        customCmds = customRefresh() // library marker kkossev.commonLib, line 962
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 963
    } // library marker kkossev.commonLib, line 964
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 965
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 966
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 967
    } // library marker kkossev.commonLib, line 968
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 969
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 970
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 971
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 972
    } // library marker kkossev.commonLib, line 973
    else { // library marker kkossev.commonLib, line 974
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 975
    } // library marker kkossev.commonLib, line 976
} // library marker kkossev.commonLib, line 977

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, 'clearRefreshRequest', [overwrite: true]) } // library marker kkossev.commonLib, line 979
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 980
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 981

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 983
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 984
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 985
        sendEvent(name: 'Status', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 986
    } // library marker kkossev.commonLib, line 987
    else { // library marker kkossev.commonLib, line 988
        logInfo "${info}" // library marker kkossev.commonLib, line 989
        sendEvent(name: 'Status', value: info, type: 'digital') // library marker kkossev.commonLib, line 990
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 991
    } // library marker kkossev.commonLib, line 992
} // library marker kkossev.commonLib, line 993

public void ping() { // library marker kkossev.commonLib, line 995
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 996
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 997
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 998
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID // library marker kkossev.commonLib, line 999
    if (isVirtual()) { runInMillis(10, 'virtualPong') } // library marker kkossev.commonLib, line 1000
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) } // library marker kkossev.commonLib, line 1001
    logDebug 'ping...' // library marker kkossev.commonLib, line 1002
} // library marker kkossev.commonLib, line 1003

private void virtualPong() { // library marker kkossev.commonLib, line 1005
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1006
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1007
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1008
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1009
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1010
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1011
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1012
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1013
        sendRttEvent() // library marker kkossev.commonLib, line 1014
    } // library marker kkossev.commonLib, line 1015
    else { // library marker kkossev.commonLib, line 1016
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1017
    } // library marker kkossev.commonLib, line 1018
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1019
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1020
} // library marker kkossev.commonLib, line 1021

public void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1023
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1024
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1025
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1026
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1027
    if (value == null) { // library marker kkossev.commonLib, line 1028
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1029
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1030
    } // library marker kkossev.commonLib, line 1031
    else { // library marker kkossev.commonLib, line 1032
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1033
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1034
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1035
    } // library marker kkossev.commonLib, line 1036
} // library marker kkossev.commonLib, line 1037

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1039
    if (cluster != null) { // library marker kkossev.commonLib, line 1040
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1041
    } // library marker kkossev.commonLib, line 1042
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1043
    return 'NULL' // library marker kkossev.commonLib, line 1044
} // library marker kkossev.commonLib, line 1045

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1047
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1048
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1049
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1050
} // library marker kkossev.commonLib, line 1051

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1053
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1054
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1055
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1056
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1057
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1058
    } // library marker kkossev.commonLib, line 1059
} // library marker kkossev.commonLib, line 1060

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1062
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1063
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1064
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1065
} // library marker kkossev.commonLib, line 1066

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1068
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1069
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1070
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1071
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1072
    } // library marker kkossev.commonLib, line 1073
    else { // library marker kkossev.commonLib, line 1074
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1075
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1076
    } // library marker kkossev.commonLib, line 1077
} // library marker kkossev.commonLib, line 1078

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1080
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1081
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1082
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1083
} // library marker kkossev.commonLib, line 1084

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1086
private void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1087
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1088
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1089
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1090
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1091
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1092
    } // library marker kkossev.commonLib, line 1093
} // library marker kkossev.commonLib, line 1094

private void deviceHealthCheck() { // library marker kkossev.commonLib, line 1096
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1097
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1098
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1099
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1100
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1101
            logWarn 'not present!' // library marker kkossev.commonLib, line 1102
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1103
        } // library marker kkossev.commonLib, line 1104
    } // library marker kkossev.commonLib, line 1105
    else { // library marker kkossev.commonLib, line 1106
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})" // library marker kkossev.commonLib, line 1107
    } // library marker kkossev.commonLib, line 1108
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1109
} // library marker kkossev.commonLib, line 1110

private void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1112
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1113
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1114
    if (value == 'online') { // library marker kkossev.commonLib, line 1115
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1116
    } // library marker kkossev.commonLib, line 1117
    else { // library marker kkossev.commonLib, line 1118
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1119
    } // library marker kkossev.commonLib, line 1120
} // library marker kkossev.commonLib, line 1121

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1123
void updated() { // library marker kkossev.commonLib, line 1124
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1125
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1126
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1127
    unschedule() // library marker kkossev.commonLib, line 1128

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1130
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1131
        runIn(86400, 'logsOff') // library marker kkossev.commonLib, line 1132
    } // library marker kkossev.commonLib, line 1133
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1134
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1135
        runIn(1800, 'traceOff') // library marker kkossev.commonLib, line 1136
    } // library marker kkossev.commonLib, line 1137

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1139
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1140
        // schedule the periodic timer // library marker kkossev.commonLib, line 1141
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1142
        if (interval > 0) { // library marker kkossev.commonLib, line 1143
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1144
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1145
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1146
        } // library marker kkossev.commonLib, line 1147
    } // library marker kkossev.commonLib, line 1148
    else { // library marker kkossev.commonLib, line 1149
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1150
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1151
    } // library marker kkossev.commonLib, line 1152
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1153
        customUpdated() // library marker kkossev.commonLib, line 1154
    } // library marker kkossev.commonLib, line 1155

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1157
} // library marker kkossev.commonLib, line 1158

private void logsOff() { // library marker kkossev.commonLib, line 1160
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1161
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1162
} // library marker kkossev.commonLib, line 1163
private void traceOff() { // library marker kkossev.commonLib, line 1164
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1165
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1166
} // library marker kkossev.commonLib, line 1167

public void configure(String command) { // library marker kkossev.commonLib, line 1169
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1170
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1171
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1172
        return // library marker kkossev.commonLib, line 1173
    } // library marker kkossev.commonLib, line 1174
    // // library marker kkossev.commonLib, line 1175
    String func // library marker kkossev.commonLib, line 1176
    try { // library marker kkossev.commonLib, line 1177
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1178
        "$func"() // library marker kkossev.commonLib, line 1179
    } // library marker kkossev.commonLib, line 1180
    catch (e) { // library marker kkossev.commonLib, line 1181
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1182
        return // library marker kkossev.commonLib, line 1183
    } // library marker kkossev.commonLib, line 1184
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1185
} // library marker kkossev.commonLib, line 1186

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1188
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1189
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1190
} // library marker kkossev.commonLib, line 1191

public void loadAllDefaults() { // library marker kkossev.commonLib, line 1193
    logDebug 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1194
    deleteAllSettings() // library marker kkossev.commonLib, line 1195
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1196
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1197
    deleteAllStates() // library marker kkossev.commonLib, line 1198
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1199

    initialize() // library marker kkossev.commonLib, line 1201
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1202
    updated() // library marker kkossev.commonLib, line 1203
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1204
} // library marker kkossev.commonLib, line 1205

private void configureNow() { // library marker kkossev.commonLib, line 1207
    configure() // library marker kkossev.commonLib, line 1208
} // library marker kkossev.commonLib, line 1209

/** // library marker kkossev.commonLib, line 1211
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1212
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1213
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1214
 */ // library marker kkossev.commonLib, line 1215
void configure() { // library marker kkossev.commonLib, line 1216
    List<String> cmds = [] // library marker kkossev.commonLib, line 1217
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1218
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1219
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1220
    if (isTuya()) { // library marker kkossev.commonLib, line 1221
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1222
    } // library marker kkossev.commonLib, line 1223
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1224
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1225
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1226
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1227
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1228
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1229
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1230
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1231
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1232
    } // library marker kkossev.commonLib, line 1233
    else { // library marker kkossev.commonLib, line 1234
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1235
    } // library marker kkossev.commonLib, line 1236
} // library marker kkossev.commonLib, line 1237

 // Invoked when the device is installed with this driver automatically selected. // library marker kkossev.commonLib, line 1239
void installed() { // library marker kkossev.commonLib, line 1240
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1241
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1242
    // populate some default values for attributes // library marker kkossev.commonLib, line 1243
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1244
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1245
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1246
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1247
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1248
} // library marker kkossev.commonLib, line 1249

private void queryPowerSource() { // library marker kkossev.commonLib, line 1251
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1252
} // library marker kkossev.commonLib, line 1253

 // Invoked from 'LoadAllDefaults' // library marker kkossev.commonLib, line 1255
private void initialize() { // library marker kkossev.commonLib, line 1256
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1257
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1258
    if (device.getDataValue('powerSource') == null) { // library marker kkossev.commonLib, line 1259
        logInfo "initializing device powerSource 'unknown'" // library marker kkossev.commonLib, line 1260
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1261
    } // library marker kkossev.commonLib, line 1262
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1263
    updateTuyaVersion() // library marker kkossev.commonLib, line 1264
    updateAqaraVersion() // library marker kkossev.commonLib, line 1265
} // library marker kkossev.commonLib, line 1266

/* // library marker kkossev.commonLib, line 1268
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1269
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1270
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1271
*/ // library marker kkossev.commonLib, line 1272

static Integer safeToInt(Object val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1274
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1275
} // library marker kkossev.commonLib, line 1276

static Double safeToDouble(Object val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1278
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1279
} // library marker kkossev.commonLib, line 1280

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1282
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1283
} // library marker kkossev.commonLib, line 1284

public void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1286
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

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1305

private String getDeviceInfo() { // library marker kkossev.commonLib, line 1307
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1308
} // library marker kkossev.commonLib, line 1309

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1311
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1312
} // library marker kkossev.commonLib, line 1313

@CompileStatic // library marker kkossev.commonLib, line 1315
public void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1316
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
        if (deviceProfilesV3 != null && this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1386
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
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024 // library marker kkossev.commonLib, line 1412
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024 // library marker kkossev.commonLib, line 1413
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1414
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1415
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1416
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1417
    // // library marker kkossev.commonLib, line 1418
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers! // library marker kkossev.commonLib, line 1419
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024 // library marker kkossev.commonLib, line 1420
    // // library marker kkossev.commonLib, line 1421
    // custom device driver specific initialization should be at the end // library marker kkossev.commonLib, line 1422
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1423
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1424
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1425

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1427
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1428
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1429
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1430
    if ( ep  != null) { // library marker kkossev.commonLib, line 1431
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1432
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1433
    } // library marker kkossev.commonLib, line 1434
    else { // library marker kkossev.commonLib, line 1435
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1436
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1437
    } // library marker kkossev.commonLib, line 1438
} // library marker kkossev.commonLib, line 1439

// not used!? // library marker kkossev.commonLib, line 1441
void setDestinationEP() { // library marker kkossev.commonLib, line 1442
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1443
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1444
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1445
} // library marker kkossev.commonLib, line 1446

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1448
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1449
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1450
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1451

// _DEBUG mode only // library marker kkossev.commonLib, line 1453
void getAllProperties() { // library marker kkossev.commonLib, line 1454
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1455
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1456
} // library marker kkossev.commonLib, line 1457

// delete all Preferences // library marker kkossev.commonLib, line 1459
void deleteAllSettings() { // library marker kkossev.commonLib, line 1460
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1461
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1462
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1463
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1464
} // library marker kkossev.commonLib, line 1465

// delete all attributes // library marker kkossev.commonLib, line 1467
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1468
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1469
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1470
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1471
} // library marker kkossev.commonLib, line 1472

// delete all State Variables // library marker kkossev.commonLib, line 1474
void deleteAllStates() { // library marker kkossev.commonLib, line 1475
    String stateDeleted = '' // library marker kkossev.commonLib, line 1476
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1477
    state.clear() // library marker kkossev.commonLib, line 1478
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1479
} // library marker kkossev.commonLib, line 1480

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1482
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1483
} // library marker kkossev.commonLib, line 1484

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1486
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1487
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1488
} // library marker kkossev.commonLib, line 1489

void testParse(String par) { // library marker kkossev.commonLib, line 1491
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1492
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1493
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1494
    parse(par) // library marker kkossev.commonLib, line 1495
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1496
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1497
} // library marker kkossev.commonLib, line 1498

Object testJob() { // library marker kkossev.commonLib, line 1500
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1501
} // library marker kkossev.commonLib, line 1502

/** // library marker kkossev.commonLib, line 1504
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1505
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1506
 */ // library marker kkossev.commonLib, line 1507
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1508
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1509
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1510
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1511
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1512
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1513
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1514
    String cron // library marker kkossev.commonLib, line 1515
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1516
    else { // library marker kkossev.commonLib, line 1517
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1518
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1519
    } // library marker kkossev.commonLib, line 1520
    return cron // library marker kkossev.commonLib, line 1521
} // library marker kkossev.commonLib, line 1522

// credits @thebearmay // library marker kkossev.commonLib, line 1524
String formatUptime() { // library marker kkossev.commonLib, line 1525
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1526
} // library marker kkossev.commonLib, line 1527

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1529
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1530
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1531
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1532
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1533
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1534
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1535
} // library marker kkossev.commonLib, line 1536

boolean isTuya() { // library marker kkossev.commonLib, line 1538
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1539
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1540
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1541
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1542
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1543
} // library marker kkossev.commonLib, line 1544

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1546
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1547
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1548
    if (application != null) { // library marker kkossev.commonLib, line 1549
        Integer ver // library marker kkossev.commonLib, line 1550
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1551
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1552
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1553
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1554
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1555
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1556
        } // library marker kkossev.commonLib, line 1557
    } // library marker kkossev.commonLib, line 1558
} // library marker kkossev.commonLib, line 1559

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1561

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1563
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1564
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1565
    if (application != null) { // library marker kkossev.commonLib, line 1566
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1567
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1568
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1569
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1570
        } // library marker kkossev.commonLib, line 1571
    } // library marker kkossev.commonLib, line 1572
} // library marker kkossev.commonLib, line 1573

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1575
    try { // library marker kkossev.commonLib, line 1576
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1577
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1578
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1579
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1580
    } catch (e) { // library marker kkossev.commonLib, line 1581
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1582
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1583
    } // library marker kkossev.commonLib, line 1584
} // library marker kkossev.commonLib, line 1585

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1587
    try { // library marker kkossev.commonLib, line 1588
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1589
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1590
        return date.getTime() // library marker kkossev.commonLib, line 1591
    } catch (e) { // library marker kkossev.commonLib, line 1592
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1593
        return now() // library marker kkossev.commonLib, line 1594
    } // library marker kkossev.commonLib, line 1595
} // library marker kkossev.commonLib, line 1596

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1598
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1599
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1600
    int seconds = time % 60 // library marker kkossev.commonLib, line 1601
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1602
} // library marker kkossev.commonLib, line 1603

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (171) kkossev.batteryLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoJavaUtilDate, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
library( // library marker kkossev.batteryLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Battery Library', name: 'batteryLib', namespace: 'kkossev', // library marker kkossev.batteryLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/batteryLib.groovy', documentationLink: '', // library marker kkossev.batteryLib, line 4
    version: '3.2.2' // library marker kkossev.batteryLib, line 5
) // library marker kkossev.batteryLib, line 6
/* // library marker kkossev.batteryLib, line 7
 *  Zigbee Level Library // library marker kkossev.batteryLib, line 8
 * // library marker kkossev.batteryLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.batteryLib, line 10
 * // library marker kkossev.batteryLib, line 11
 * ver. 3.0.0  2024-04-06 kkossev  - added batteryLib.groovy // library marker kkossev.batteryLib, line 12
 * ver. 3.0.1  2024-04-06 kkossev  - customParsePowerCluster bug fix // library marker kkossev.batteryLib, line 13
 * ver. 3.0.2  2024-04-14 kkossev  - batteryPercentage bug fix (was x2); added bVoltCtr; added battertRefresh // library marker kkossev.batteryLib, line 14
 * ver. 3.2.0  2024-05-21 kkossev  - commonLib 3.2.0 allignment; added lastBattery; added handleTuyaBatteryLevel // library marker kkossev.batteryLib, line 15
 * ver. 3.2.1  2024-07-06 kkossev  - added tuyaToBatteryLevel and handleTuyaBatteryLevel; added batteryInitializeVars // library marker kkossev.batteryLib, line 16
 * ver. 3.2.2  2024-07-18 kkossev  - added BatteryVoltage and BatteryDelay device capability checks // library marker kkossev.batteryLib, line 17
 * // library marker kkossev.batteryLib, line 18
 *                                   TODO:  // library marker kkossev.batteryLib, line 19
 *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.batteryLib, line 20
*/ // library marker kkossev.batteryLib, line 21

static String batteryLibVersion()   { '3.2.2' } // library marker kkossev.batteryLib, line 23
static String batteryLibStamp() { '2024/07/18 2:34 PM' } // library marker kkossev.batteryLib, line 24

metadata { // library marker kkossev.batteryLib, line 26
    capability 'Battery' // library marker kkossev.batteryLib, line 27
    attribute  'batteryVoltage', 'number' // library marker kkossev.batteryLib, line 28
    attribute  'lastBattery', 'date'         // last battery event time - added in 3.2.0 05/21/2024 // library marker kkossev.batteryLib, line 29
    // no commands // library marker kkossev.batteryLib, line 30
    preferences { // library marker kkossev.batteryLib, line 31
        if (device && advancedOptions == true) { // library marker kkossev.batteryLib, line 32
            if ('BatteryVoltage' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 33
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: 'Convert battery voltage to battery Percentage remaining.' // library marker kkossev.batteryLib, line 34
            } // library marker kkossev.batteryLib, line 35
            if ('BatteryDelay' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 36
                input(name: 'batteryDelay', type: 'enum', title: '<b>Battery Events Delay</b>', description:'Select the Battery Events Delay<br>(default is <b>no delay</b>)', options: DelayBatteryOpts.options, defaultValue: DelayBatteryOpts.defaultValue) // library marker kkossev.batteryLib, line 37
            } // library marker kkossev.batteryLib, line 38
        } // library marker kkossev.batteryLib, line 39
    } // library marker kkossev.batteryLib, line 40
} // library marker kkossev.batteryLib, line 41

@Field static final Map DelayBatteryOpts = [ defaultValue: 0, options: [0: 'No delay', 30: '30 seconds', 3600: '1 hour', 14400: '4 hours', 28800: '8 hours', 43200: '12 hours']] // library marker kkossev.batteryLib, line 43

public void standardParsePowerCluster(final Map descMap) { // library marker kkossev.batteryLib, line 45
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.batteryLib, line 46
    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.batteryLib, line 47
    if (descMap.attrId == '0020') { // battery voltage // library marker kkossev.batteryLib, line 48
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 49
        state.stats['bVoltCtr'] = (state.stats['bVoltCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 50
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.batteryLib, line 51
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.batteryLib, line 52
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.batteryLib, line 53
        } // library marker kkossev.batteryLib, line 54
    } // library marker kkossev.batteryLib, line 55
    else if (descMap.attrId == '0021') { // battery percentage // library marker kkossev.batteryLib, line 56
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 57
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 58
        if (isTuya()) { // library marker kkossev.batteryLib, line 59
            sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 60
        } // library marker kkossev.batteryLib, line 61
        else { // library marker kkossev.batteryLib, line 62
            sendBatteryPercentageEvent((rawValue / 2) as int) // library marker kkossev.batteryLib, line 63
        } // library marker kkossev.batteryLib, line 64
    } // library marker kkossev.batteryLib, line 65
    else { // library marker kkossev.batteryLib, line 66
        logWarn "customParsePowerCluster: zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.batteryLib, line 67
    } // library marker kkossev.batteryLib, line 68
} // library marker kkossev.batteryLib, line 69

public void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.batteryLib, line 71
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.batteryLib, line 72
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 73
    Map result = [:] // library marker kkossev.batteryLib, line 74
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.batteryLib, line 75
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.batteryLib, line 76
        BigDecimal minVolts = 2.2 // library marker kkossev.batteryLib, line 77
        BigDecimal maxVolts = 3.2 // library marker kkossev.batteryLib, line 78
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.batteryLib, line 79
        int roundedPct = Math.round(pct * 100) // library marker kkossev.batteryLib, line 80
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.batteryLib, line 81
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.batteryLib, line 82
        if (convertToPercent == true) { // library marker kkossev.batteryLib, line 83
            result.value = Math.min(100, roundedPct) // library marker kkossev.batteryLib, line 84
            result.name = 'battery' // library marker kkossev.batteryLib, line 85
            result.unit  = '%' // library marker kkossev.batteryLib, line 86
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.batteryLib, line 87
        } // library marker kkossev.batteryLib, line 88
        else { // library marker kkossev.batteryLib, line 89
            result.value = volts // library marker kkossev.batteryLib, line 90
            result.name = 'batteryVoltage' // library marker kkossev.batteryLib, line 91
            result.unit  = 'V' // library marker kkossev.batteryLib, line 92
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.batteryLib, line 93
        } // library marker kkossev.batteryLib, line 94
        result.type = 'physical' // library marker kkossev.batteryLib, line 95
        result.isStateChange = true // library marker kkossev.batteryLib, line 96
        logInfo "${result.descriptionText}" // library marker kkossev.batteryLib, line 97
        sendEvent(result) // library marker kkossev.batteryLib, line 98
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 99
    } // library marker kkossev.batteryLib, line 100
    else { // library marker kkossev.batteryLib, line 101
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.batteryLib, line 102
    } // library marker kkossev.batteryLib, line 103
} // library marker kkossev.batteryLib, line 104

public void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.batteryLib, line 106
    if ((batteryPercent as int) == 255) { // library marker kkossev.batteryLib, line 107
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.batteryLib, line 108
        return // library marker kkossev.batteryLib, line 109
    } // library marker kkossev.batteryLib, line 110
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 111
    Map map = [:] // library marker kkossev.batteryLib, line 112
    map.name = 'battery' // library marker kkossev.batteryLib, line 113
    map.timeStamp = now() // library marker kkossev.batteryLib, line 114
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.batteryLib, line 115
    map.unit  = '%' // library marker kkossev.batteryLib, line 116
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.batteryLib, line 117
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.batteryLib, line 118
    map.isStateChange = true // library marker kkossev.batteryLib, line 119
    // // library marker kkossev.batteryLib, line 120
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.batteryLib, line 121
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.batteryLib, line 122
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.batteryLib, line 123
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.batteryLib, line 124
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.batteryLib, line 125
        // send it now! // library marker kkossev.batteryLib, line 126
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.batteryLib, line 127
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 128
    } // library marker kkossev.batteryLib, line 129
    else { // library marker kkossev.batteryLib, line 130
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.batteryLib, line 131
        map.delayed = delayedTime // library marker kkossev.batteryLib, line 132
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.batteryLib, line 133
        map.lastBattery = lastBattery // library marker kkossev.batteryLib, line 134
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.batteryLib, line 135
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.batteryLib, line 136
    } // library marker kkossev.batteryLib, line 137
} // library marker kkossev.batteryLib, line 138

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.batteryLib, line 140
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 141
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 142
    sendEvent(map) // library marker kkossev.batteryLib, line 143
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 144
} // library marker kkossev.batteryLib, line 145

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.batteryLib, line 147
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.batteryLib, line 148
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 149
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 150
    sendEvent(map) // library marker kkossev.batteryLib, line 151
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 152
} // library marker kkossev.batteryLib, line 153

public int tuyaToBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 155
    int rawValue = fncmd // library marker kkossev.batteryLib, line 156
    switch (fncmd) { // library marker kkossev.batteryLib, line 157
        case 0: rawValue = 100; break // Battery Full // library marker kkossev.batteryLib, line 158
        case 1: rawValue = 75;  break // Battery High // library marker kkossev.batteryLib, line 159
        case 2: rawValue = 50;  break // Battery Medium // library marker kkossev.batteryLib, line 160
        case 3: rawValue = 25;  break // Battery Low // library marker kkossev.batteryLib, line 161
        case 4: rawValue = 100; break // Tuya 3 in 1 -> USB powered // library marker kkossev.batteryLib, line 162
        // for all other values >4 we will use the raw value, expected to be the real battery level 4..100% // library marker kkossev.batteryLib, line 163
    } // library marker kkossev.batteryLib, line 164
    return rawValue // library marker kkossev.batteryLib, line 165
} // library marker kkossev.batteryLib, line 166

public void handleTuyaBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 168
    int rawValue = tuyaToBatteryLevel(fncmd) // library marker kkossev.batteryLib, line 169
    sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 170
} // library marker kkossev.batteryLib, line 171

public void batteryInitializeVars( boolean fullInit = false ) { // library marker kkossev.batteryLib, line 173
    logDebug "batteryInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.batteryLib, line 174
    if (device.hasCapability('Battery')) { // library marker kkossev.batteryLib, line 175
        if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.batteryLib, line 176
        if (fullInit || settings?.batteryDelay == null) { device.updateSetting('batteryDelay', [value: DelayBatteryOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.batteryLib, line 177
    } // library marker kkossev.batteryLib, line 178
} // library marker kkossev.batteryLib, line 179

public List<String> batteryRefresh() { // library marker kkossev.batteryLib, line 181
    List<String> cmds = [] // library marker kkossev.batteryLib, line 182
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 100)         // battery voltage // library marker kkossev.batteryLib, line 183
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 100)         // battery percentage // library marker kkossev.batteryLib, line 184
    return cmds // library marker kkossev.batteryLib, line 185
} // library marker kkossev.batteryLib, line 186

// ~~~~~ end include (171) kkossev.batteryLib ~~~~~

// ~~~~~ start include (172) kkossev.temperatureLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.temperatureLib, line 1
library( // library marker kkossev.temperatureLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Temperature Library', name: 'temperatureLib', namespace: 'kkossev', // library marker kkossev.temperatureLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/temperatureLib.groovy', documentationLink: '', // library marker kkossev.temperatureLib, line 4
    version: '3.2.3' // library marker kkossev.temperatureLib, line 5
) // library marker kkossev.temperatureLib, line 6
/* // library marker kkossev.temperatureLib, line 7
 *  Zigbee Temperature Library // library marker kkossev.temperatureLib, line 8
 * // library marker kkossev.temperatureLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.temperatureLib, line 10
 * // library marker kkossev.temperatureLib, line 11
 * ver. 3.0.0  2024-04-06 kkossev  - added temperatureLib.groovy // library marker kkossev.temperatureLib, line 12
 * ver. 3.0.1  2024-04-19 kkossev  - temperature rounding fix // library marker kkossev.temperatureLib, line 13
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0 allignment; added temperatureRefresh() // library marker kkossev.temperatureLib, line 14
 * ver. 3.2.1  2024-06-07 kkossev  - excluded maxReportingTime for mmWaveSensor and Thermostat // library marker kkossev.temperatureLib, line 15
 * ver. 3.2.2  2024-07-06 kkossev  - fixed T/H clusters attribute different than 0 (temperature, humidity MeasuredValue) bug // library marker kkossev.temperatureLib, line 16
 * ver. 3.2.3  2024-07-18 kkossev  - added 'ReportingConfiguration' capability check for minReportingTime and maxReportingTime // library marker kkossev.temperatureLib, line 17
 * // library marker kkossev.temperatureLib, line 18
 *                                   TODO: // library marker kkossev.temperatureLib, line 19
 *                                   TODO: add temperatureOffset // library marker kkossev.temperatureLib, line 20
 *                                   TODO: unschedule('sendDelayedTempEvent') only if needed (add boolean flag to sendDelayedTempEvent()) // library marker kkossev.temperatureLib, line 21
 *                                   TODO: check for negative temperature values in standardParseTemperatureCluster() // library marker kkossev.temperatureLib, line 22
*/ // library marker kkossev.temperatureLib, line 23

static String temperatureLibVersion()   { '3.2.3' } // library marker kkossev.temperatureLib, line 25
static String temperatureLibStamp() { '2024/07/18 3:08 PM' } // library marker kkossev.temperatureLib, line 26

metadata { // library marker kkossev.temperatureLib, line 28
    capability 'TemperatureMeasurement' // library marker kkossev.temperatureLib, line 29
    // no commands // library marker kkossev.temperatureLib, line 30
    preferences { // library marker kkossev.temperatureLib, line 31
        if (device && advancedOptions == true) { // library marker kkossev.temperatureLib, line 32
            if ('ReportingConfiguration' in DEVICE?.capabilities) { // library marker kkossev.temperatureLib, line 33
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: 'Minimum reporting interval, seconds <i>(1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 34
                if (!(deviceType in ['mmWaveSensor', 'Thermostat', 'TRV'])) { // library marker kkossev.temperatureLib, line 35
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: 'Maximum reporting interval, seconds <i>(120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.temperatureLib, line 36
                } // library marker kkossev.temperatureLib, line 37
            } // library marker kkossev.temperatureLib, line 38
        } // library marker kkossev.temperatureLib, line 39
    } // library marker kkossev.temperatureLib, line 40
} // library marker kkossev.temperatureLib, line 41

void standardParseTemperatureCluster(final Map descMap) { // library marker kkossev.temperatureLib, line 43
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.temperatureLib, line 44
    if (descMap.attrId == '0000') { // library marker kkossev.temperatureLib, line 45
        int value = hexStrToSignedInt(descMap.value) // library marker kkossev.temperatureLib, line 46
        handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.temperatureLib, line 47
    } // library marker kkossev.temperatureLib, line 48
    else { // library marker kkossev.temperatureLib, line 49
        logWarn "standardParseTemperatureCluster() - unknown attribute ${descMap.attrId} value=${descMap.value}" // library marker kkossev.temperatureLib, line 50
    } // library marker kkossev.temperatureLib, line 51
} // library marker kkossev.temperatureLib, line 52

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.temperatureLib, line 54
    Map eventMap = [:] // library marker kkossev.temperatureLib, line 55
    BigDecimal temperature = safeToBigDecimal(temperaturePar).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 56
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.temperatureLib, line 57
    eventMap.name = 'temperature' // library marker kkossev.temperatureLib, line 58
    if (location.temperatureScale == 'F') { // library marker kkossev.temperatureLib, line 59
        temperature = ((temperature * 1.8) + 32).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 60
        eventMap.unit = '\u00B0F' // library marker kkossev.temperatureLib, line 61
    } // library marker kkossev.temperatureLib, line 62
    else { // library marker kkossev.temperatureLib, line 63
        eventMap.unit = '\u00B0C' // library marker kkossev.temperatureLib, line 64
    } // library marker kkossev.temperatureLib, line 65
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 66
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 67
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.temperatureLib, line 68
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.temperatureLib, line 69
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.temperatureLib, line 70
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.temperatureLib, line 71
        return // library marker kkossev.temperatureLib, line 72
    } // library marker kkossev.temperatureLib, line 73
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.temperatureLib, line 74
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.temperatureLib, line 75
    if (state.states['isRefresh'] == true) { // library marker kkossev.temperatureLib, line 76
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.temperatureLib, line 77
        eventMap.isStateChange = true // library marker kkossev.temperatureLib, line 78
    } // library marker kkossev.temperatureLib, line 79
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.temperatureLib, line 80
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 81
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.temperatureLib, line 82
    if (timeElapsed >= minTime) { // library marker kkossev.temperatureLib, line 83
        logInfo "${eventMap.descriptionText}" // library marker kkossev.temperatureLib, line 84
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.temperatureLib, line 85
        state.lastRx['tempTime'] = now() // library marker kkossev.temperatureLib, line 86
        sendEvent(eventMap) // library marker kkossev.temperatureLib, line 87
    } // library marker kkossev.temperatureLib, line 88
    else {         // queue the event // library marker kkossev.temperatureLib, line 89
        eventMap.type = 'delayed' // library marker kkossev.temperatureLib, line 90
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.temperatureLib, line 91
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.temperatureLib, line 92
    } // library marker kkossev.temperatureLib, line 93
} // library marker kkossev.temperatureLib, line 94

void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.temperatureLib, line 96
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.temperatureLib, line 97
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.temperatureLib, line 98
    sendEvent(eventMap) // library marker kkossev.temperatureLib, line 99
} // library marker kkossev.temperatureLib, line 100

List<String> temperatureLibInitializeDevice() { // library marker kkossev.temperatureLib, line 102
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 103
    cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.temperatureLib, line 104
    logDebug "temperatureLibInitializeDevice() cmds=${cmds}" // library marker kkossev.temperatureLib, line 105
    return cmds // library marker kkossev.temperatureLib, line 106
} // library marker kkossev.temperatureLib, line 107

List<String> temperatureRefresh() { // library marker kkossev.temperatureLib, line 109
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 110
    cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.temperatureLib, line 111
    return cmds // library marker kkossev.temperatureLib, line 112
} // library marker kkossev.temperatureLib, line 113

// ~~~~~ end include (172) kkossev.temperatureLib ~~~~~

// ~~~~~ start include (173) kkossev.humidityLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.humidityLib, line 1
library( // library marker kkossev.humidityLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Humidity Library', name: 'humidityLib', namespace: 'kkossev', // library marker kkossev.humidityLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/humidityLib.groovy', documentationLink: '', // library marker kkossev.humidityLib, line 4
    version: '3.2.2' // library marker kkossev.humidityLib, line 5
) // library marker kkossev.humidityLib, line 6
/* // library marker kkossev.humidityLib, line 7
 *  Zigbee Humidity Library // library marker kkossev.humidityLib, line 8
 * // library marker kkossev.humidityLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.humidityLib, line 10
 * // library marker kkossev.humidityLib, line 11
 * ver. 3.0.0  2024-04-06 kkossev  - added humidityLib.groovy // library marker kkossev.humidityLib, line 12
 * ver. 3.2.0  2024-05-29 kkossev  - commonLib 3.2.0 allignment; added humidityRefresh() // library marker kkossev.humidityLib, line 13
 * ver. 3.2.2  2024-07-02 kkossev  - fixed T/H clusters attribute different than 0 (temperature, humidity MeasuredValue) bug // library marker kkossev.humidityLib, line 14
 * // library marker kkossev.humidityLib, line 15
 *                                   TODO: // library marker kkossev.humidityLib, line 16
*/ // library marker kkossev.humidityLib, line 17

static String humidityLibVersion()   { '3.2.2' } // library marker kkossev.humidityLib, line 19
static String humidityLibStamp() { '2024/07/02 11:17 PM' } // library marker kkossev.humidityLib, line 20

metadata { // library marker kkossev.humidityLib, line 22
    capability 'RelativeHumidityMeasurement' // library marker kkossev.humidityLib, line 23
    // no commands // library marker kkossev.humidityLib, line 24
    preferences { // library marker kkossev.humidityLib, line 25
        // the minReportingTime and maxReportingTime are already defined in the temperatureLib.groovy // library marker kkossev.humidityLib, line 26
    } // library marker kkossev.humidityLib, line 27
} // library marker kkossev.humidityLib, line 28

void standardParseHumidityCluster(final Map descMap) { // library marker kkossev.humidityLib, line 30
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.humidityLib, line 31
    if (descMap.attrId == '0000') { // library marker kkossev.humidityLib, line 32
        final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.humidityLib, line 33
        handleHumidityEvent(value / 100.0F as BigDecimal) // library marker kkossev.humidityLib, line 34
    } // library marker kkossev.humidityLib, line 35
    else { // library marker kkossev.humidityLib, line 36
        logWarn "standardParseHumidityCluster() - unknown attribute ${descMap.attrId} value=${descMap.value}" // library marker kkossev.humidityLib, line 37
    } // library marker kkossev.humidityLib, line 38
} // library marker kkossev.humidityLib, line 39

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) { // library marker kkossev.humidityLib, line 41
    Map eventMap = [:] // library marker kkossev.humidityLib, line 42
    BigDecimal humidity = safeToBigDecimal(humidityPar) // library marker kkossev.humidityLib, line 43
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.humidityLib, line 44
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0) // library marker kkossev.humidityLib, line 45
    if (humidity <= 0.0 || humidity > 100.0) { // library marker kkossev.humidityLib, line 46
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})" // library marker kkossev.humidityLib, line 47
        return // library marker kkossev.humidityLib, line 48
    } // library marker kkossev.humidityLib, line 49
    eventMap.value = humidity.setScale(0, BigDecimal.ROUND_HALF_UP) // library marker kkossev.humidityLib, line 50
    eventMap.name = 'humidity' // library marker kkossev.humidityLib, line 51
    eventMap.unit = '% RH' // library marker kkossev.humidityLib, line 52
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.humidityLib, line 53
    //eventMap.isStateChange = true // library marker kkossev.humidityLib, line 54
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.humidityLib, line 55
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.humidityLib, line 56
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.humidityLib, line 57
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.humidityLib, line 58
    if (timeElapsed >= minTime) { // library marker kkossev.humidityLib, line 59
        logInfo "${eventMap.descriptionText}" // library marker kkossev.humidityLib, line 60
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.humidityLib, line 61
        state.lastRx['humiTime'] = now() // library marker kkossev.humidityLib, line 62
        sendEvent(eventMap) // library marker kkossev.humidityLib, line 63
    } // library marker kkossev.humidityLib, line 64
    else { // library marker kkossev.humidityLib, line 65
        eventMap.type = 'delayed' // library marker kkossev.humidityLib, line 66
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.humidityLib, line 67
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.humidityLib, line 68
    } // library marker kkossev.humidityLib, line 69
} // library marker kkossev.humidityLib, line 70

void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.humidityLib, line 72
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.humidityLib, line 73
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.humidityLib, line 74
    sendEvent(eventMap) // library marker kkossev.humidityLib, line 75
} // library marker kkossev.humidityLib, line 76

List<String> humidityLibInitializeDevice() { // library marker kkossev.humidityLib, line 78
    List<String> cmds = [] // library marker kkossev.humidityLib, line 79
    cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.humidityLib, line 80
    return cmds // library marker kkossev.humidityLib, line 81
} // library marker kkossev.humidityLib, line 82

List<String> humidityRefresh() { // library marker kkossev.humidityLib, line 84
    List<String> cmds = [] // library marker kkossev.humidityLib, line 85
    cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.humidityLib, line 86
    return cmds // library marker kkossev.humidityLib, line 87
} // library marker kkossev.humidityLib, line 88

// ~~~~~ end include (173) kkossev.humidityLib ~~~~~

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

// ~~~~~ start include (178) kkossev.iasLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.iasLib, line 1
library( // library marker kkossev.iasLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee IASLibrary', name: 'iasLib', namespace: 'kkossev', // library marker kkossev.iasLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/iasLib.groovy', documentationLink: '', // library marker kkossev.iasLib, line 4
    version: '3.2.2' // library marker kkossev.iasLib, line 5

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
 * ver. 3.2.1  2024-07-06 kkossev  - added standardParseIasMessage (debug only); zs null check // library marker kkossev.iasLib, line 21
 * ver. 3.2.2  2024-08-09 kkossev  - zs null check // library marker kkossev.iasLib, line 22
 * // library marker kkossev.iasLib, line 23
 *                                   TODO: // library marker kkossev.iasLib, line 24
*/ // library marker kkossev.iasLib, line 25

static String iasLibVersion()   { '3.2.2' } // library marker kkossev.iasLib, line 27
static String iasLibStamp() { '2024/08/09 12:03 PM' } // library marker kkossev.iasLib, line 28

metadata { // library marker kkossev.iasLib, line 30
    // no capabilities // library marker kkossev.iasLib, line 31
    // no attributes // library marker kkossev.iasLib, line 32
    // no commands // library marker kkossev.iasLib, line 33
    preferences { // library marker kkossev.iasLib, line 34
    // no prefrences // library marker kkossev.iasLib, line 35
    } // library marker kkossev.iasLib, line 36
} // library marker kkossev.iasLib, line 37

@Field static final Map<Integer, String> IAS_ATTRIBUTES = [ // library marker kkossev.iasLib, line 39
    //  Zone Information // library marker kkossev.iasLib, line 40
    0x0000: 'zone state', // library marker kkossev.iasLib, line 41
    0x0001: 'zone type', // library marker kkossev.iasLib, line 42
    0x0002: 'zone status', // library marker kkossev.iasLib, line 43
    //  Zone Settings // library marker kkossev.iasLib, line 44
    0x0010: 'CIE addr',    // EUI64 // library marker kkossev.iasLib, line 45
    0x0011: 'Zone Id',     // uint8 // library marker kkossev.iasLib, line 46
    0x0012: 'Num zone sensitivity levels supported',     // uint8 // library marker kkossev.iasLib, line 47
    0x0013: 'Current zone sensitivity level',            // uint8 // library marker kkossev.iasLib, line 48
    0xF001: 'Current zone keep time'                     // uint8 // library marker kkossev.iasLib, line 49
] // library marker kkossev.iasLib, line 50

@Field static final Map<Integer, String> ZONE_TYPE = [ // library marker kkossev.iasLib, line 52
    0x0000: 'Standard CIE', // library marker kkossev.iasLib, line 53
    0x000D: 'Motion Sensor', // library marker kkossev.iasLib, line 54
    0x0015: 'Contact Switch', // library marker kkossev.iasLib, line 55
    0x0028: 'Fire Sensor', // library marker kkossev.iasLib, line 56
    0x002A: 'Water Sensor', // library marker kkossev.iasLib, line 57
    0x002B: 'Carbon Monoxide Sensor', // library marker kkossev.iasLib, line 58
    0x002C: 'Personal Emergency Device', // library marker kkossev.iasLib, line 59
    0x002D: 'Vibration Movement Sensor', // library marker kkossev.iasLib, line 60
    0x010F: 'Remote Control', // library marker kkossev.iasLib, line 61
    0x0115: 'Key Fob', // library marker kkossev.iasLib, line 62
    0x021D: 'Key Pad', // library marker kkossev.iasLib, line 63
    0x0225: 'Standard Warning Device', // library marker kkossev.iasLib, line 64
    0x0226: 'Glass Break Sensor', // library marker kkossev.iasLib, line 65
    0x0229: 'Security Repeater', // library marker kkossev.iasLib, line 66
    0xFFFF: 'Invalid Zone Type' // library marker kkossev.iasLib, line 67
] // library marker kkossev.iasLib, line 68

@Field static final Map<Integer, String> ZONE_STATE = [ // library marker kkossev.iasLib, line 70
    0x00: 'Not Enrolled', // library marker kkossev.iasLib, line 71
    0x01: 'Enrolled' // library marker kkossev.iasLib, line 72
] // library marker kkossev.iasLib, line 73

public void standardParseIasMessage(final String description) { // library marker kkossev.iasLib, line 75
    // https://developer.tuya.com/en/docs/iot-device-dev/tuya-zigbee-water-sensor-access-standard?id=K9ik6zvon7orn // library marker kkossev.iasLib, line 76
    Map zs = zigbee.parseZoneStatusChange(description) // library marker kkossev.iasLib, line 77
    if (zs == null) { // library marker kkossev.iasLib, line 78
        logWarn "standardParseIasMessage: zs is null!" // library marker kkossev.iasLib, line 79
        return // library marker kkossev.iasLib, line 80
    } // library marker kkossev.iasLib, line 81
    if (zs.alarm1Set == true) { // library marker kkossev.iasLib, line 82
        logDebug "standardParseIasMessage: Alarm 1 is set" // library marker kkossev.iasLib, line 83
        //handleMotion(true) // library marker kkossev.iasLib, line 84
    } // library marker kkossev.iasLib, line 85
    else { // library marker kkossev.iasLib, line 86
        logDebug "standardParseIasMessage: Alarm 1 is cleared" // library marker kkossev.iasLib, line 87
        //handleMotion(false) // library marker kkossev.iasLib, line 88
    } // library marker kkossev.iasLib, line 89
} // library marker kkossev.iasLib, line 90

public void standardParseIASCluster(final Map descMap) { // library marker kkossev.iasLib, line 92
    logDebug "standardParseIASCluster: cluster=${descMap} attrInt=${descMap.attrInt} value=${descMap.value}" // library marker kkossev.iasLib, line 93
    if (descMap.cluster != '0500') { return } // not IAS cluster // library marker kkossev.iasLib, line 94
    if (descMap.attrInt == null) { return } // missing attribute // library marker kkossev.iasLib, line 95
    //String zoneSetting = IAS_ATTRIBUTES[descMap.attrInt] // library marker kkossev.iasLib, line 96
    if ( IAS_ATTRIBUTES[descMap.attrInt] == null ) { // library marker kkossev.iasLib, line 97
        logWarn "standardParseIASCluster: Unknown IAS attribute ${descMap?.attrId} (value:${descMap?.value})" // library marker kkossev.iasLib, line 98
        return // library marker kkossev.iasLib, line 99
    } // unknown IAS attribute // library marker kkossev.iasLib, line 100
    /* // library marker kkossev.iasLib, line 101
    logDebug "standardParseIASCluster: Don't know how to handle IAS attribute 0x${descMap?.attrId} '${zoneSetting}' (value:${descMap?.value})!" // library marker kkossev.iasLib, line 102
    return // library marker kkossev.iasLib, line 103
    */ // library marker kkossev.iasLib, line 104

    String clusterInfo = 'standardParseIASCluster:' // library marker kkossev.iasLib, line 106

    if (descMap?.cluster == '0500' && descMap?.command in ['01', '0A']) {    //IAS read attribute response // library marker kkossev.iasLib, line 108
        logDebug "${standardParseIASCluster} IAS read attribute ${descMap?.attrId} response is ${descMap?.value}" // library marker kkossev.iasLib, line 109
        if (descMap?.attrId == '0000') { // library marker kkossev.iasLib, line 110
            int value = Integer.parseInt(descMap?.value, 16) // library marker kkossev.iasLib, line 111
            String status = "${ZONE_STATE[value]}" // library marker kkossev.iasLib, line 112
            if (value == 0 ) { status = "<b>${status}</b>" ; logWarn "${clusterInfo} is NOT ENROLLED!" } // library marker kkossev.iasLib, line 113
            logInfo "${clusterInfo} IAS Zone State report is '${status}' (${value})" // library marker kkossev.iasLib, line 114
        } // library marker kkossev.iasLib, line 115
        else if (descMap?.attrId == '0001') { // library marker kkossev.iasLib, line 116
            int value = Integer.parseInt(descMap?.value, 16) // library marker kkossev.iasLib, line 117
            logInfo "${clusterInfo} IAS Zone Type report is '${ZONE_TYPE[value]}' (${value})" // library marker kkossev.iasLib, line 118
        } // library marker kkossev.iasLib, line 119
        else if (descMap?.attrId == '0002') { // library marker kkossev.iasLib, line 120
            logInfo "${clusterInfo} IAS Zone status repoted: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}" // library marker kkossev.iasLib, line 121
        } // library marker kkossev.iasLib, line 122
        else if (descMap?.attrId == '0010') { // library marker kkossev.iasLib, line 123
            logInfo "${clusterInfo} IAS Zone Address received (bitmap = ${descMap?.value})" // library marker kkossev.iasLib, line 124
        } // library marker kkossev.iasLib, line 125
        else if (descMap?.attrId == '0011') { // library marker kkossev.iasLib, line 126
            logInfo "${clusterInfo} IAS Zone ID: ${descMap.value}" // library marker kkossev.iasLib, line 127
        } // library marker kkossev.iasLib, line 128
        else if (descMap?.attrId == '0012') { // library marker kkossev.iasLib, line 129
            logInfo "${clusterInfo} IAS Num zone sensitivity levels supported: ${descMap.value}" // library marker kkossev.iasLib, line 130
        } // library marker kkossev.iasLib, line 131
        else if (descMap?.attrId == '0013') { // library marker kkossev.iasLib, line 132
            int value = Integer.parseInt(descMap?.value, 16) // library marker kkossev.iasLib, line 133
            //logInfo "${clusterInfo} IAS Current Zone Sensitivity Level = ${sensitivityOpts.options[value]} (${value})" // library marker kkossev.iasLib, line 134
            logInfo "${clusterInfo} IAS Current Zone Sensitivity Level = (${value})" // library marker kkossev.iasLib, line 135
        // device.updateSetting('settings.sensitivity', [value:value.toString(), type:'enum']) // library marker kkossev.iasLib, line 136
        } // library marker kkossev.iasLib, line 137
        else if (descMap?.attrId == 'F001') {    // [raw:7CC50105000801F02000, dni:7CC5, endpoint:01, cluster:0500, size:08, attrId:F001, encoding:20, command:0A, value:00, clusterInt:1280, attrInt:61441] // library marker kkossev.iasLib, line 138
            int value = Integer.parseInt(descMap?.value, 16) // library marker kkossev.iasLib, line 139
            //String str   = getKeepTimeOpts().options[value] // library marker kkossev.iasLib, line 140
            //logInfo "${clusterInfo} Current IAS Zone Keep-Time =  ${str} (${value})" // library marker kkossev.iasLib, line 141
            logInfo "${clusterInfo} Current IAS Zone Keep-Time =  (${value})" // library marker kkossev.iasLib, line 142
        //device.updateSetting('keepTime', [value: value.toString(), type: 'enum']) // library marker kkossev.iasLib, line 143
        } // library marker kkossev.iasLib, line 144
        else { // library marker kkossev.iasLib, line 145
            logDebug "${clusterInfo} Zone status attribute ${descMap?.attrId}: <b>NOT PROCESSED</b> ${descMap}" // library marker kkossev.iasLib, line 146
        } // library marker kkossev.iasLib, line 147
    } // if IAS read attribute response // library marker kkossev.iasLib, line 148
    else if (descMap?.clusterId == '0500' && descMap?.command == '04') {    //write attribute response (IAS) // library marker kkossev.iasLib, line 149
        logDebug "${clusterInfo} AS write attribute response is ${descMap?.data[0] == '00' ? 'success' : '<b>FAILURE</b>'}" // library marker kkossev.iasLib, line 150
    } // library marker kkossev.iasLib, line 151
    else { // library marker kkossev.iasLib, line 152
        logDebug "${clusterInfo} <b>NOT PROCESSED</b> ${descMap}" // library marker kkossev.iasLib, line 153
    } // library marker kkossev.iasLib, line 154
} // library marker kkossev.iasLib, line 155

List<String> refreshAllIas() { // library marker kkossev.iasLib, line 157
    logDebug 'refreshAllIas()' // library marker kkossev.iasLib, line 158
    List<String> cmds = [] // library marker kkossev.iasLib, line 159
    IAS_ATTRIBUTES.each { key, value -> // library marker kkossev.iasLib, line 160
        cmds += zigbee.readAttribute(0x0500, key, [:], delay = 199) // library marker kkossev.iasLib, line 161
    } // library marker kkossev.iasLib, line 162
    return cmds // library marker kkossev.iasLib, line 163
} // library marker kkossev.iasLib, line 164

// ~~~~~ end include (178) kkossev.iasLib ~~~~~

// ~~~~~ start include (180) kkossev.motionLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.motionLib, line 1
library( // library marker kkossev.motionLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Motion Library', name: 'motionLib', namespace: 'kkossev', // library marker kkossev.motionLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/motionLib.groovy', documentationLink: '', // library marker kkossev.motionLib, line 4
    version: '3.2.0' // library marker kkossev.motionLib, line 5
) // library marker kkossev.motionLib, line 6
/*  Zigbee Motion Library // library marker kkossev.motionLib, line 7
 * // library marker kkossev.motionLib, line 8
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.motionLib, line 9
 * // library marker kkossev.motionLib, line 10
 * ver. 3.2.0  2024-07-06 kkossev  - added motionLib.groovy; added [digital] [physical] to the descriptionText // library marker kkossev.motionLib, line 11
 * // library marker kkossev.motionLib, line 12
 *                                   TODO: // library marker kkossev.motionLib, line 13
*/ // library marker kkossev.motionLib, line 14

static String motionLibVersion()   { '3.2.0' } // library marker kkossev.motionLib, line 16
static String motionLibStamp() { '2024/07/06 8:28 PM' } // library marker kkossev.motionLib, line 17

metadata { // library marker kkossev.motionLib, line 19
    capability 'MotionSensor' // library marker kkossev.motionLib, line 20
    // no custom attributes // library marker kkossev.motionLib, line 21
    command 'setMotion', [[name: 'setMotion', type: 'ENUM', constraints: ['No selection', 'active', 'inactive'], description: 'Force motion active/inactive (for tests)']] // library marker kkossev.motionLib, line 22
    preferences { // library marker kkossev.motionLib, line 23
        if (device) { // library marker kkossev.motionLib, line 24
            if (('motionReset' in DEVICE?.preferences) && (DEVICE?.preferences.motionReset == true)) { // library marker kkossev.motionLib, line 25
                input(name: 'motionReset', type: 'bool', title: '<b>Reset Motion to Inactive</b>', description: 'Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b>', defaultValue: false) // library marker kkossev.motionLib, line 26
                if (settings?.motionReset?.value == true) { // library marker kkossev.motionLib, line 27
                    input('motionResetTimer', 'number', title: '<b>Motion Reset Timer</b>', description: 'After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds', range: '0..7200', defaultValue: 60) // library marker kkossev.motionLib, line 28
                } // library marker kkossev.motionLib, line 29
            } // library marker kkossev.motionLib, line 30
            if (advancedOptions == true) { // library marker kkossev.motionLib, line 31
                if ('invertMotion' in DEVICE?.preferences) { // library marker kkossev.motionLib, line 32
                    input(name: 'invertMotion', type: 'bool', title: '<b>Invert Motion Active/Not Active</b>', description: 'Some Tuya motion sensors may report the motion active/inactive inverted...', defaultValue: false) // library marker kkossev.motionLib, line 33
                } // library marker kkossev.motionLib, line 34
            } // library marker kkossev.motionLib, line 35
        } // library marker kkossev.motionLib, line 36
    } // library marker kkossev.motionLib, line 37
} // library marker kkossev.motionLib, line 38

public void handleMotion(final boolean motionActive, final boolean isDigital=false) { // library marker kkossev.motionLib, line 40
    boolean motionActiveCopy = motionActive // library marker kkossev.motionLib, line 41

    if (settings.invertMotion == true) {    // patch!! fix it! // library marker kkossev.motionLib, line 43
        motionActiveCopy = !motionActiveCopy // library marker kkossev.motionLib, line 44
    } // library marker kkossev.motionLib, line 45

    //log.trace "handleMotion: motionActive=${motionActiveCopy}, isDigital=${isDigital}" // library marker kkossev.motionLib, line 47
    if (motionActiveCopy) { // library marker kkossev.motionLib, line 48
        int timeout = settings?.motionResetTimer ?: 0 // library marker kkossev.motionLib, line 49
        // If the sensor only sends a motion detected message, the reset to motion inactive must be  performed in code // library marker kkossev.motionLib, line 50
        if (settings?.motionReset == true && timeout != 0) { // library marker kkossev.motionLib, line 51
            runIn(timeout, 'resetToMotionInactive', [overwrite: true]) // library marker kkossev.motionLib, line 52
        } // library marker kkossev.motionLib, line 53
        if (device.currentState('motion')?.value != 'active') { // library marker kkossev.motionLib, line 54
            state.motionStarted = unix2formattedDate(now()) // library marker kkossev.motionLib, line 55
        } // library marker kkossev.motionLib, line 56
    } // library marker kkossev.motionLib, line 57
    else { // library marker kkossev.motionLib, line 58
        if (device.currentState('motion')?.value == 'inactive') { // library marker kkossev.motionLib, line 59
            logDebug "ignored motion inactive event after ${getSecondsInactive()}s" // library marker kkossev.motionLib, line 60
            return      // do not process a second motion inactive event! // library marker kkossev.motionLib, line 61
        } // library marker kkossev.motionLib, line 62
    } // library marker kkossev.motionLib, line 63
    sendMotionEvent(motionActiveCopy, isDigital) // library marker kkossev.motionLib, line 64
} // library marker kkossev.motionLib, line 65

public void sendMotionEvent(final boolean motionActive, boolean isDigital=false) { // library marker kkossev.motionLib, line 67
    String descriptionText = 'Detected motion' // library marker kkossev.motionLib, line 68
    if (motionActive) { // library marker kkossev.motionLib, line 69
        descriptionText = device.currentValue('motion') == 'active' ? "Motion is active ${getSecondsInactive()}s" : 'Detected motion' // library marker kkossev.motionLib, line 70
    } // library marker kkossev.motionLib, line 71
    else { // library marker kkossev.motionLib, line 72
        descriptionText = "Motion reset to inactive after ${getSecondsInactive()}s" // library marker kkossev.motionLib, line 73
    } // library marker kkossev.motionLib, line 74
    if (isDigital) { descriptionText += ' [digital]' } // library marker kkossev.motionLib, line 75
    logInfo "${descriptionText}" // library marker kkossev.motionLib, line 76
    sendEvent( // library marker kkossev.motionLib, line 77
            name            : 'motion', // library marker kkossev.motionLib, line 78
            value            : motionActive ? 'active' : 'inactive', // library marker kkossev.motionLib, line 79
            type            : isDigital == true ? 'digital' : 'physical', // library marker kkossev.motionLib, line 80
            descriptionText : descriptionText // library marker kkossev.motionLib, line 81
    ) // library marker kkossev.motionLib, line 82
    //runIn(1, formatAttrib, [overwrite: true]) // library marker kkossev.motionLib, line 83
} // library marker kkossev.motionLib, line 84

public void resetToMotionInactive() { // library marker kkossev.motionLib, line 86
    if (device.currentState('motion')?.value == 'active') { // library marker kkossev.motionLib, line 87
        String descText = "Motion reset to inactive after ${getSecondsInactive()}s (software timeout)" // library marker kkossev.motionLib, line 88
        sendEvent( // library marker kkossev.motionLib, line 89
            name : 'motion', // library marker kkossev.motionLib, line 90
            value : 'inactive', // library marker kkossev.motionLib, line 91
            isStateChange : true, // library marker kkossev.motionLib, line 92
            type:  'digital', // library marker kkossev.motionLib, line 93
            descriptionText : descText // library marker kkossev.motionLib, line 94
        ) // library marker kkossev.motionLib, line 95
        logInfo "${descText}" // library marker kkossev.motionLib, line 96
    } // library marker kkossev.motionLib, line 97
    else { // library marker kkossev.motionLib, line 98
        logDebug "ignored resetToMotionInactive (software timeout) after ${getSecondsInactive()}s" // library marker kkossev.motionLib, line 99
    } // library marker kkossev.motionLib, line 100
} // library marker kkossev.motionLib, line 101

public void setMotion(String mode) { // library marker kkossev.motionLib, line 103
    if (mode == 'active') { // library marker kkossev.motionLib, line 104
        handleMotion(motionActive = true, isDigital = true) // library marker kkossev.motionLib, line 105
    } else if (mode == 'inactive') { // library marker kkossev.motionLib, line 106
        handleMotion(motionActive = false, isDigital = true) // library marker kkossev.motionLib, line 107
    } else { // library marker kkossev.motionLib, line 108
        if (settings?.txtEnable) { // library marker kkossev.motionLib, line 109
            log.warn "${device.displayName} please select motion action" // library marker kkossev.motionLib, line 110
        } // library marker kkossev.motionLib, line 111
    } // library marker kkossev.motionLib, line 112
} // library marker kkossev.motionLib, line 113

public int getSecondsInactive() { // library marker kkossev.motionLib, line 115
    Long unixTime = 0 // library marker kkossev.motionLib, line 116
    try { unixTime = formattedDate2unix(state.motionStarted) } catch (Exception e) { logWarn "getSecondsInactive: ${e}" } // library marker kkossev.motionLib, line 117
    if (unixTime) { return Math.round((now() - unixTime) / 1000) as int } // library marker kkossev.motionLib, line 118
    return settings?.motionResetTimer ?: 0 // library marker kkossev.motionLib, line 119
} // library marker kkossev.motionLib, line 120

public List<String> refreshAllMotion() { // library marker kkossev.motionLib, line 122
    logDebug 'refreshAllMotion()' // library marker kkossev.motionLib, line 123
    List<String> cmds = [] // library marker kkossev.motionLib, line 124
    return cmds // library marker kkossev.motionLib, line 125
} // library marker kkossev.motionLib, line 126

public void motionInitializeVars( boolean fullInit = false ) { // library marker kkossev.motionLib, line 128
    logDebug "motionInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.motionLib, line 129
    if (device.hasCapability('MotionSensor')) { // library marker kkossev.motionLib, line 130
        if (fullInit == true || settings.motionReset == null) device.updateSetting('motionReset', false) // library marker kkossev.motionLib, line 131
        if (fullInit == true || settings.invertMotion == null) device.updateSetting('invertMotion', false) // library marker kkossev.motionLib, line 132
        if (fullInit == true || settings.motionResetTimer == null) device.updateSetting('motionResetTimer', 60) // library marker kkossev.motionLib, line 133
    } // library marker kkossev.motionLib, line 134
} // library marker kkossev.motionLib, line 135




// ~~~~~ end include (180) kkossev.motionLib ~~~~~
