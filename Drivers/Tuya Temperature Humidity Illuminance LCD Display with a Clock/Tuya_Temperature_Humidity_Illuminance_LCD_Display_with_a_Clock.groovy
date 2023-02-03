/**
 *  Tuya Temperature Humidity Illuminance LCD Display with a Clock
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 * ver. 1.0.0 2022-01-02 kkossev  - Inital test version
 * ver. 1.0.1 2022-02-05 kkossev  - Added Zemismart ZXZTH fingerprint; added _TZE200_locansqn; Fahrenheit scale + rounding; temperatureScaleParameter; temperatureSensitivity; minTempAlarm; maxTempAlarm
 * ver. 1.0.2 2022-02-06 kkossev  - Tuya commands refactoring; TS0222 T/H poll on illuminance change (EP2); modelGroupPreference bug fix; dyncamic parameters
 * ver. 1.0.3 2022-02-13 kkossev  - _TZE200_c7emyjom fingerprint added;
 * ver. 1.0.4 2022-02-20 kkossev  - Celsius/Fahrenheit correction for TS0601_Tuya devices
 * ver. 1.0.5 2022-04-25 kkossev  - added TS0601_AUBESS (illuminance only); ModelGroup is shown in State Variables
 * ver. 1.0.6 2022-05-09 kkossev  - new model 'TS0201_LCZ030' (_TZ3000_qaaysllp)
 * ver. 1.0.7 2022-06-09 kkossev  - new model 'TS0601_Contact'(_TZE200_pay2byax); illuminance unit changed to 'lx;  Bug fix - all settings were reset back in to the defaults on hub reboot
 * ver. 1.0.8 2022-08-13 kkossev  - _TZE200_pay2byax bug fixes; '_TZE200_locansqn' (TS0601_Haozee) bug fixes; removed degrees symbol from the logs; removed temperatureScaleParameter'preference (use HE scale setting); decimal/number bug fixes;
 *                                   added temperature and humidity offesets; configured parameters (including C/F HE scale) are sent to the device when paired again to HE; added Minimum time between temperature and humidity reports;
 * ver. 1.0.9 2022-10-02 kkossev  - configure _TZ2000_a476raq2 reporting time; added TS0601 _TZE200_bjawzodf; code cleanup
 * ver. 1.0.10 2022-10-11 kkossev - '_TZ3000_itnrsufe' reporting configuration bug fix?; reporting configuration result Info log; added Sonoff SNZB-02 fingerprint; reportingConfguration is sent on pairing to HE;
 * ver. 1.0.11 2022-10-31 kkossev - added _TZE200_whkgqxse; fingerprint correction; _TZ3000_bguser20 _TZ3000_fllyghyj _TZ3000_yd2e749y _TZ3000_6uzkisv2
 * ver. 1.1.0  2022-12-18 kkossev - added _info_ attribute; delayed reporting configuration when the sleepy device wakes up; excluded TS0201 model devices in the delayed configuration; _TZE200_locansqn fingerprint correction and max reporting periods formula correction
 *                                  added TS0601_Soil _TZE200_myd45weu ; added _TZE200_znbl8dj5 _TZE200_a8sdabtg _TZE200_qoy0ekbd
 * ver. 1.1.1  2023-01-14 kkossev - added _TZ3000_ywagc4rj TS0201_TH; bug fix: negative temperatures not calculated correctly;
 * ver. 1.2.0  2023-01-15 kkossev - parsing multiple DP received in one command;
 * ver. 1.2.1  2023-01-15 kkossev - _TZE200_locansqn fixes;_TZ3000_bguser20 correct model;
 * ver. 1.3.0  2023-02-02 kkossev - healthStatus; added capability 'Health Check'
 * 
 *                                  TODO:  TS0201 - bindings are sent, even if nothing to configure?
 *                                  TODO: add Battery minimum reporting time default 8 hours?
 *
*/

def version() { "1.3.0" }
def timeStamp() {"2023/02/02 10:56 PM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol

@Field static final Boolean debug = false
@Field static final Integer defaultMinReportingTime = 10


metadata {
    definition (name: "Tuya Temperature Humidity Illuminance LCD Display with a Clock", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Temperature%20Humidity%20Illuminance%20LCD%20Display%20with%20a%20Clock/Tuya_Temperature_Humidity_Illuminance_LCD_Display_with_a_Clock.groovy", singleThreaded: true ) {
        capability "Refresh"
        capability "Sensor"
        capability "Battery"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "IlluminanceMeasurement"
        //capability "ContactSensor"   // uncomment for _TZE200_pay2byax contact w/ illuminance sensor
        //capability "MotionSensor"    // uncomment for SiHAS multi sensor
        capability "Health Check"

        if (debug == true) {        
            command "zTest", [
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"]
            ]
            command "test", [[name:"test", type: "STRING", description: "test", constraints: ["STRING"]]]
        }
        
        command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****" ]]
        
        attribute   "_info", "string"        // when defined as attributes, will be shown on top of the 'Current States' list ...
        attribute "healthStatus", "enum", ["offline", "online"]

        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_lve3dvpy", deviceJoinName: "Tuya Temperature Humidity Illuminance LCD Display with a Clock"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_c7emyjom", deviceJoinName: "Tuya Temperature Humidity Illuminance LCD Display with a Clock"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_locansqn", deviceJoinName: "Haozee Temperature Humidity Illuminance LCD Display with a Clock" // https://de.aliexpress.com/item/1005003634353180.html
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_bq5c8xfe", deviceJoinName: "Haozee Temperature Humidity Illuminance LCD Display with a Clock"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0402,0405", outClusters:"0019",      model:"TS0201", manufacturer:"_TZ2000_hjsgdkfl", deviceJoinName: "AVATTO S-H02"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0402,0405", outClusters:"0019",      model:"TS0201", manufacturer:"_TZ2000_a476raq2", deviceJoinName: "Tuya Temperature Humidity LCD display" 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0400,E002", outClusters:"0019,000A", model:"TS0201", manufacturer:"_TZ3000_qaaysllp", deviceJoinName: "NAS-TH02B LCZ030 T/H/I/LCD"  // Neo Coolcam ?  // NOT TESTED!
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0400",      outClusters:"0019,000A", model:"TS0222", manufacturer:"_TYZB01_kvwjujy9", deviceJoinName: "MOES ZSS-ZK-THL"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0400,0001,0500", outClusters:"0019,000A", model:"TS0222", manufacturer:"_TYZB01_4mdqxxnn", deviceJoinName: "Tuya Illuminance Sensor TS0222_2"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0400,0001,0500", outClusters:"0019,000A", model:"TS0222", manufacturer:"_TZ3000_lfa05ajd", deviceJoinName: "Zemismart ZXZTH"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_pisltm67", deviceJoinName: "AUBESS Light Sensor S-LUX-ZB"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TYST11_pisltm67", deviceJoinName: "AUBESS Light Sensor S-LUX-ZB"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000",      outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_pay2byax", deviceJoinName: "Tuya Contact and Illuminance Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0003,0402,0405,E002,0000", outClusters:"0003,0019,000A", model:"TS0201", manufacturer:"_TZ3000_itnrsufe", deviceJoinName: "Tuya temperature and humidity sensor RCTW1Z"         
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0003,0402,0405,E002,0000", outClusters:"0003,0019,000A", model:"TS0201", manufacturer:"_TZ3000_ywagc4rj", deviceJoinName: "Tuya temperature and humidity sensor"       // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock/88093/211?u=kkossev
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0004,0005,0402,0405,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_bjawzodf", deviceJoinName: "Tuya like Temperature Humidity LCD Display" // https://de.aliexpress.com/item/4000739457722.html?gatewayAdapt=glo2deu 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_bjawzodf", deviceJoinName: "Tuya like Temperature Humidity LCD Display"                // https://de.aliexpress.com/item/4000739457722.html?gatewayAdapt=glo2deu 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_myd45weu", deviceJoinName: "Tuya Temperature Humidity Soil Monitoring Sensor"          // https://www.aliexpress.com/item/1005004979025740.html
        // model: 'ZG-227ZL',
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0004,0005,0402,0405,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_qoy0ekbd", deviceJoinName: "Tuya Temperature Humidity LCD Display"      // not tested
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0004,0005,0402,0405,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_a8sdabtg", deviceJoinName: "Tuya Temperature Humidity (no screen)"      // https://community.hubitat.com/t/new-temp-humidity-device-not-working-correctly-generic-zigbee-th-driver/109725?u=kkossev
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0004,0005,0402,0405,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_znbl8dj5", deviceJoinName: "Tuya Temperature Humidity"                  // not tested
        //
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_whkgqxse", deviceJoinName: "Tuya Zigbee Temperature Humidity Sensor With Backlight"    // https://www.aliexpress.com/item/1005003980647546.html
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0003,0402,0405,0000", outClusters:"0003,0019,000A", model:"TS0201", manufacturer:"_TZ3000_bguser20", deviceJoinName: "Tuya Temperature Humidity sensor WSD500A" 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0003,0402,0405,0000", outClusters:"0003,0019,000A", model:"TS0201", manufacturer:"_TZ3000_xr3htd96", deviceJoinName: "Tuya Temperature Humidity sensor WSD500A" 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0003,0402,0405,0000", outClusters:"0003,0019,000A", model:"TS0201", manufacturer:"_TZ3000_fllyghyj", deviceJoinName: "Tuya Temperature Humidity sensor" // not tested
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0003,0402,0405,0000", outClusters:"0003,0019,000A", model:"TS0201", manufacturer:"_TZ3000_yd2e749y", deviceJoinName: "Tuya Temperature Humidity sensor" // not tested
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0003,0402,0405,0000", outClusters:"0003,0019,000A", model:"TS0201", manufacturer:"_TZ3000_6uzkisv2", deviceJoinName: "Tuya Temperature Humidity sensor" // not tested
        //
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0402,0405,0001", outClusters:"0003", model:"TH01", manufacturer:"eWeLink", deviceJoinName: "Sonoff Temperature and Humidity Sensor SNZB-02" 
        
    }
    preferences {
        //input description: "Once you change values on this page, the attribute value \"needUpdate\" will show \"YES\" until all configuration parameters are updated.", title: "<b>Settings</b>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
        input (name: "logEnable", type: "bool", title: "Debug logging", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: true)
        input (name: "txtEnable", type: "bool", title: "Description text logging", description: "<i>Display measured values in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
        input (name: "modelGroupPreference", type: "enum", title: "Model Group", description:"Recommended value is <b>Auto detect</b></i>", defaultValue: 0, options:
               ["Auto detect":"Auto detect", "TS0601_Tuya":"TS0601_Tuya", "TS0601_Haozee":"TS0601_Haozee", "TS0601_AUBESS":"TS0601_AUBESS", "TS0201":"TS0201", "TS0222":"TS0222", 'TS0201_LCZ030': 'TS0201_LCZ030',
                "TS0222_2":"TS0222_2", "TS0201_TH":"TS0201_TH", "TS0601_Soil":"TS0601_Soil", "Zigbee NON-Tuya":"Zigbee NON-Tuya"])
        input (name: "advancedOptions", type: "bool", title: "Advanced options", description: "May not be supported by all devices!", defaultValue: false)
        if (advancedOptions == true) {
            if (isConfigurable()) {
                input (title: "To configure a sleepy device, try any of the methods below :", description: "<b> * Rapidly change the temperature or the humidity<br> * Remove the battery for at least 1 minute<br> * Pair the device again to HE</b>", type: "paragraph", element: "paragraph")
            }
            configParams.each {
                //log.warn "it.value.input.limit = ${it.value.input.limit}"
                if (it.value.input.limit == null || 'ALL' in it.value.input.limit || getModelGroup() in it.value.input.limit) {
                    //log.trace "it = ${it.value.input.limit}"
                    input it.value.input
                }
            }
        }
    }
}


@Field static final Integer numberOfconfigParams = 8
@Field static Map configParams = [

        0: [input: [name: "temperatureOffset", type: "decimal", title: "Temperature offset", description: "Select how many degrees to adjust the temperature.", defaultValue: 0.0, range: "-100.0..100.0",
                   limit:['ALL']]],

        1: [input: [name: "humidityOffset", type: "decimal", title: "Humidity offset", description: "Enter a percentage to adjust the humidity.", defaultValue: 0.0, range: "-100.0..100.0",
                   limit:['ALL']]],

        2: [input: [name: "temperatureSensitivity", type: "decimal", title: "Temperature Sensitivity", description: "Temperature change for reporting, "+"\u00B0"+"C", defaultValue: 0.5, range: "0.1..50.0",
                   limit:['TS0601_Tuya', 'TS0601_Haozee', 'TS0201_TH', "Zigbee NON-Tuya"]]],

        3: [input: [name: "humiditySensitivity", type: "number", title: "Humidity Sensitivity", description: "Humidity change for reporting, %", defaultValue: 5, range: "1..50",
                   limit:['TS0601_Tuya', 'TS0601_Haozee', 'TS0201_TH', "Zigbee NON-Tuya"]]],

        4: [input: [name: "illuminanceSensitivity", type: "number", title: "Illuminance Sensitivity", description: "Illuminance change for reporting, %", defaultValue: 12, range: "10..100",                // TS0222 "MOES ZSS-ZK-THL"
                   limit:['TS0222']]],

        5: [input: [name: "minTempAlarmPar", type: "decimal", title: "Minimum Temperature Alarm", description: "Minimum Temperature Alarm, C", defaultValue: 0.0, range: "-20.0..60.0",
                   limit:['TS0601_Tuya', /*'TS0601_Haozee',*/ 'TS0201_LCZ030']]],

        6: [input: [name: "maxTempAlarmPar", type: "decimal", title: "Maximum Temperature Alarm", description: "Maximum Temperature Alarm, C", defaultValue: 39.0, range: "-20.0..60.0",
                   limit:['TS0601_Tuya', /*'TS0601_Haozee',*/ 'TS0201_LCZ030']]],

        7: [input: [name: "minHumidityAlarmPar", type: "number", title: "Minimal Humidity Alarm", description: "Minimum Humidity Alarm, %", defaultValue: 20, range: "0..100",           // 'TS0601_Haozee' only!
                   limit:[/*'TS0601_Haozee',*/ /*'TS0201_LCZ030'*/]]],

        8: [input: [name: "maxHumidityAlarmPar", type: "number", title: "Maximum Humidity Alarm", description: "Maximum Humidity Alarm, %", defaultValue: 60, range: "0..100",            // 'TS0601_Haozee' only!
                   limit:[/*'TS0601_Haozee',*/ /*'TS0201_LCZ030'*/]]],
    
        9: [input: [name: "minReportingTimeTemp", type: "number", title: "Minimum time between temperature reports", description: "Minimum time between temperature reporting, seconds", defaultValue: 10, range: "1..3600",
                   limit:['ALL']]],
    
       10: [input: [name: "maxReportingTimeTemp", type: "number", title: "Maximum time between temperature reports", description: "Maximum time between temperature reporting, seconds", defaultValue: 3600, range: "10..43200",
                   limit:['TS0601_Haozee', 'TS0201_TH', "Zigbee NON-Tuya"]]],
  
       11: [input: [name: "minReportingTimeHumidity", type: "number", title: "Minimum time between humidity reports", description: "Minimum time between humidity reporting, seconds", defaultValue: 10, range: "1..3600",
                   limit:['ALL']]],
    
       12: [input: [name: "maxReportingTimeHumidity", type: "number", title: "Maximum time between humidity reports", description: "Maximum time between humidity reporting, seconds", defaultValue: 3600, range: "10..43200",
                   limit:['TS0601_Haozee', 'TS0201_TH', "Zigbee NON-Tuya"]]],

       13: [input: [name: "alarmTempPar", type: "enum", title: "Temperature Alarm", description:"Temperature Alarm", defaultValue: 0, options: [0:"Below min temp", 1:"Over max temp", 2:"off"],
                   limit:[/*'TS0201_LCZ030'*/]]],

       14: [input: [name: "alarmHumidityPar", type: "enum", title: "Humidity Alarm", description:"Temperature Alarm", defaultValue: 0, options: [0:"Below min hum.", 1:"Over max hum", 2:"off"],
                   limit:[/*'TS0201_LCZ030'*/]]]
]

@Field static final Map<String, String> Models = [
    '_TZE200_lve3dvpy'  : 'TS0601_Tuya',         // Tuya Temperature Humidity LCD Display with a Clock
    '_TZE200_c7emyjom'  : 'TS0601_Tuya',         // Tuya Temperature Humidity LCD Display with a Clock
    '_TZE200_whkgqxse'  : 'TS0601_Tuya',         // Tuya Zigbee Temperature Humidity Sensor With Backlight    https://www.aliexpress.com/item/1005003980647546.html
    '_TZE200_a8sdabtg'  : 'TS0601_Tuya',         // Tuya Zigbee Temperature Humidity Sensor - no display!     https://www.amazon.de/gp/product/B09NKCDXT9 - TODO !
    '_TZE200_qoy0ekbd'  : 'TS0601_Tuya',         // https://www.aliexpress.com/item/1005004896603070.html - TODO !
    '_TZE200_znbl8dj5'  : 'TS0601_Tuya',         // https://www.aliexpress.com/item/1005004116638127.html - TODO !
    '_TZE200_locansqn'  : 'TS0601_Haozee',       // Haozee Temperature Humidity Illuminance LCD Display with a Clock
    '_TZE200_bq5c8xfe'  : 'TS0601_Haozee',       //
    '_TZE200_nnrfa68v'  : 'TS0601_Haozee',       // not tested NOUS E6 https://noussmart.pl/product/e6.html 
    '_TZE200_pisltm67'  : 'TS0601_AUBESS',       // illuminance only sensor
    '_TZ2000_a476raq2'  : 'TS0201',              // KK
    '_TZ3000_lfa05ajd'  : 'TS0201',              // Zemismart ZXZTH
    '_TZ2000_xogb73am'  : 'TS0201',
    '_TZ2000_avdnvykf'  : 'TS0201',
    '_TYZB01_a476raq2'  : 'TS0201',
    '_TYZB01_hjsgdkfl'  : 'TS0201',
    '_TZ2000_hjsgdkfl'  : 'TS0201',             // "AVATTO S-H02"
    '_TZ3000_bguser20'  : 'TS0201',             // Model WSD500A 
    '_TZ3000_xr3htd96'  : 'TS0201',             // Model WSD500A 
    '_TZ3000_fllyghyj'  : 'TS0201',
    '_TZ3000_yd2e749y'  : 'TS0201',
    '_TZ3000_6uzkisv2'  : 'TS0201',    
    '_TZ3000_qaaysllp'  : 'TS0201_LCZ030',      // NAS-TH02B  / NEO Coolcam ?  - T/H/I - testing! // https://github.com/Datakg/tuya/blob/53e33ae7767aedbb5d2138f2a31798badffd80d2/zhaquirks/tuya/ts0201_neo.py
    '_TYZB01_kvwjujy9'  : 'TS0222',             // "MOES ZSS-ZK-THL" e-Ink display
    '_TYZB01_4mdqxxnn'  : 'TS0222_2',           // illuminance only sensor
    '_TZE200_pay2byax'  : 'TS0601_Contact',     // Contact and illuminance sensor
    '_TZ3000_itnrsufe'  : 'TS0201_TH',          // Temperature and humidity sensor; // reports both battery voltage and perceintage; cluster 0xE002, attr 0xE00B: 0-Celsius, 1: Fahrenheit ( 0x30 ENUM)
    '_TZ3000_ywagc4rj'  : 'TS0201_TH',          // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock/88093/210?u=kkossev
    '_TZE200_myd45weu'  : 'TS0601_Soil',        // Soil monitoring sensor
    'eWeLink'           : 'Zigbee NON-Tuya',    // Sonoff Temperature and Humidity Sensor SNZB-02
    ''                  : 'UNKNOWN',
    'ALL'               : 'ALL',
    'TEST'              : 'TEST'

]

def isConfigurable()  { getModelGroup() in ['Zigbee NON-Tuya', 'TS0201_TH'] }

@Field static final Integer MaxRetries = 3
@Field static final Integer ConfigTimer = 15
@Field static final Integer presenceCountTreshold = 4

private getCLUSTER_TUYA()       { 0xEF00 }
private getSETDATA()            { 0x00 }
private getSETTIME()            { 0x24 }

// Tuya Commands
private getTUYA_REQUEST()       { 0x00 }
private getTUYA_REPORTING()     { 0x01 }
private getTUYA_QUERY()         { 0x02 }
private getTUYA_STATUS_SEARCH() { 0x06 }
private getTUYA_TIME_SYNCHRONISATION() { 0x24 }

// tuya DP type
private getDP_TYPE_RAW()        { "01" }    // [ bytes ]
private getDP_TYPE_BOOL()       { "01" }    // [ 0/1 ]
private getDP_TYPE_VALUE()      { "02" }    // [ 4 byte value ]
private getDP_TYPE_STRING()     { "03" }    // [ N byte string ]
private getDP_TYPE_ENUM()       { "04" }    // [ 0-255 ]
private getDP_TYPE_BITMAP()     { "05" }    // [ 1,2,4 bytes ] as bits

// Parse incoming device messages to generate events
def parse(String description) {
    checkDriverVersion()
    Map statsMap = stringToJsonMap(state.stats); try {statsMap['rxCtr']++ } catch (e) {statsMap['rxCtr']=0}; state.stats = mapToJsonString(statsMap)
    if (settings?.logEnable) log.debug "${device.displayName} parse() descMap = ${zigbee.parseDescriptionAsMap(description)}"
    if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
            if (descMap.attrInt == 0x0021) {
                getBatteryPercentageResult(Integer.parseInt(descMap.value,16))
            } else if (descMap.attrInt == 0x0020){
                //log.trace "descMap.attrInt == 0x0020"
                getBatteryResult(Integer.parseInt(descMap.value, 16))
            }
            else {
                log.warn "unparesed attrint $descMap.attrInt"
            }
        }
		else if (descMap.cluster == "0400" && descMap.attrId == "0000") {
            def rawLux = Integer.parseInt(descMap.value,16)
            if (device.getDataValue("manufacturer") in ["ShinaSystem"]) {
                illuminanceEventLux( rawLux )
            }
            else {
                illuminanceEvent( rawLux )
            }
            if (getModelGroup() == 'TS0222') {
                pollTS0222()
            }
		}
		else if (descMap.cluster == "0400" && descMap.attrId == "F001") {        //MOES ZSS-ZK-THL, also TS0201 Neo Coolcam!
            def raw = Integer.parseInt(descMap.value,16)
            if (settings?.txtEnable) log.info "${device.displayName} illuminance sensitivity is ${raw} Lux"
            device.updateSetting("illuminanceSensitivity", [value:raw, type:"number"])
        }
		else if (descMap.cluster == "0402" && descMap.attrId == "0000") {
            if (getModelGroup() != 'TS0222_2') {
                def raw = Integer.parseInt(descMap.value,16)
                if (raw > 32767) {
            	    //Here we deal with negative values
            	    raw = raw - 65536
                }   
                temperatureEvent( raw / 100.0 )
            }
            else {
                if (settings?.logEnable) log.warn "${device.displayName} Ignoring ${getModelGroup()} temperature event"
            }
		}
        else if (descMap.cluster == "0405" && descMap.attrId == "0000") {
            def raw = Integer.parseInt(descMap.value,16)
            if (getModelGroup() != 'TS0201_TH') {
                humidityEvent( raw / 100.0 )
            }
            else {
                 humidityEvent( raw / 10.0 )    // also _TZE200_bjawzodf ?
            }
		}
        else if (descMap.cluster == "0406" && descMap.attrId == "0000") {    // OWON, SiHAS
            def raw = Integer.parseInt(descMap.value,16)
            motionEvent( raw & 0x01 )
		}
        else if (descMap?.clusterInt == CLUSTER_TUYA) {
            processTuyaCluster( descMap )
        }
        else if (descMap?.clusterId == "0013") {    // device announcement, profileId:0000
            logInfo "device announcement"
            try {statsMap['rejoins']++ } catch (e) {statsMap['rejoins']=0}; state.stats = mapToJsonString(statsMap)            
            if (getModelGroup() == 'TS0222') {
                configure()
            }
        }
        else if (descMap.isClusterSpecific == false && descMap.command == "01" ) { //global commands read attribute response
            def status = descMap.data[2]
            if (status == "86") {
                if (settings?.logEnable) log.warn "${device.displayName} Cluster ${descMap.clusterId} read attribute - NOT SUPPORTED!\r ${descMap}"
            }
            else {
                if (settings?.logEnable) log.warn "${device.displayName} <b>UNPROCESSED Global Command</b> :  ${descMap}"
            }
        }
        else if (descMap.profileId == "0000") { //zdo
            parseZDOcommand(descMap)
        } 
        else if (descMap.clusterId != null && descMap.profileId == "0104") { // ZHA global command
            parseZHAcommand(descMap)
        } 
        else {
            if (settings?.logEnable) log.warn "${device.displayName} <b> NOT PARSED </b> :  ${descMap}"
        }
    } // if 'catchall:' or 'read attr -'
    else {
        if (settings?.logEnable) log.debug "${device.displayName} <b> UNPROCESSED </b> parse() descMap = ${zigbee.parseDescriptionAsMap(description)}"
    }
    //
    if (isPendingConfig()) {
        ConfigurationStateMachine()
    }

}

def parseZHAcommand( Map descMap) {
    Map lastRxMap = stringToJsonMap(state.lastRx)
    Map lastTxMap = stringToJsonMap(state.lastTx)
    switch (descMap.command) {
        case "01" : //read attribute response. If there was no error, the successful attribute reading would be processed in the main parse() method.
            def status = descMap.data[2]
            def attrId = descMap.data[1] + descMap.data[0] 
            if (status == "86") {
                if (logEnable==true) log.warn "${device.displayName} Read attribute response: unsupported Attributte ${attrId} cluster ${clusterId}"
            }
            else {
                if (logEnable==true) log.debug "${device.displayName} Read attribute response: status code ${status} Attributte ${attrId} cluster ${descMap.clusterId}"
            } 
            break
        case "04" : //write attribute response
            if (logEnable==true) log.info "${device.displayName} Received Write Attribute Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
            break
        case "07" : // Configure Reporting Response
            if (logEnable==true) log.info "${device.displayName} Received Configure Reporting Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
            // Status: Unreportable Attribute (0x8c)
            break
        case "09" : // Command: Read Reporting Configuration Response (0x09)
            def status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00)
            def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000)
            if (status == 0) {
                def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10)
                def min = zigbee.convertHexToInt(descMap.data[6])*256 + zigbee.convertHexToInt(descMap.data[5])
                def max = zigbee.convertHexToInt(descMap.data[8]+descMap.data[7])
                def delta = 0
                if (descMap.data.size() == 11) { 
                    delta = zigbee.convertHexToInt(descMap.data[10]+descMap.data[9])
                }
                else if (descMap.data.size() == 10) { 
                    delta = zigbee.convertHexToInt(descMap.data[9])
                }
                else {
                    if (logEnable==true) log.debug "${device.displayName} descMap.data.size = ${descMap.data.size()}"
                }
                logDebug "Received Read Reporting Configuration response (0x09) for cluster:${descMap.clusterId} attribite:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}"
                String attributeName
                if (descMap.clusterId == "0405") {
                    attributeName = "humidity"
                    lastRxMap.humiCfg = min.toString() + "," + max.toString() + "," + delta.toString()
                    if (lastRxMap.humiCfg == lastTxMap.humiCfg) {
                        lastTxMap.humiCfgOK = true
                    }
                }
                else if (descMap.clusterId == "0402") {
                    attributeName = "temperature"
                    lastRxMap.tempCfg = min.toString() + "," + max.toString() + "," + delta.toString()
                    if (lastRxMap.tempCfg == lastTxMap.tempCfg) {
                        lastTxMap.tempCfgOK = true
                    }
                }
                else if (descMap.clusterId == "0001") {
                    attributeName = "battery %"
                }
                else {
                    attributeName = descMap.clusterId
                }
                if (lastTxMap.humiCfgOK == true && lastTxMap.tempCfgOK == true) {
                    logDebug "both T&H configured!"    
                }
                if (txtEnable==true) {
                    log.info "${device.displayName} Reporting Configuration Response for ${attributeName}  (status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'}) is: min=${min} max=${max} delta=${delta}"
                }
            }
            else {    // failure
                if (logEnable==true) log.info "${device.displayName} <b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribite:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
            }
            break
        case "0B" : // ZCL Default Response
            def status = descMap.data[1]
            if (status != "00") {
                if (logEnable==true) log.info "${device.displayName} Received ZCL Default Response to Command ${descMap.data[0]} for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[1]=="00" ? 'Success' : '<b>Failure</b>'})"
            }
            break
        default :
            if (logEnable==true) log.warn "${device.displayName} Unprocessed global command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
            break
    }
   state.lastRx = mapToJsonString(lastRxMap)
   state.lastTx = mapToJsonString(lastTxMap)
}    

def parseZDOcommand( Map descMap ) {
    switch (descMap.clusterId) {
        case "0006" :
            if (logEnable) log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7]+descMap.data[6]})"
            break
        case "0013" : // device announcement
            if (logEnable) log.info "${device.displayName} Received device announcement, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2]+descMap.data[1]}, Capability Information: ${descMap.data[11]})"
            break
        case "8004" : // simple descriptor response
            if (logEnable) log.info "${device.displayName} Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}"
            parseSimpleDescriptorResponse( descMap )
            break
        case "8005" : // endpoint response
            if (logEnable) log.info "${device.displayName} Received endpoint response: cluster: ${descMap.clusterId} (endpoint response) endpointCount = ${ descMap.data[4]}  endpointList = ${descMap.data[5]}"
            break
        case "8021" : // bind response
            if (logEnable) log.info "${device.displayName} Received bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1]=="00" ? 'Success' : '<b>Failure</b>'})"
            break
        case "8022" : // unbind response
            if (logEnable) log.info "${device.displayName} Received unbind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1]=="00" ? 'Success' : '<b>Failure</b>'})"
            break
        case "8034" : // leave response
            if (logEnable) log.info "${device.displayName} Received leave response, data=${descMap.data}"
            break
        case "8038" : // Management Network Update Notify
            if (logEnable) log.info "${device.displayName} Received Management Network Update Notify, data=${descMap.data}"
            break
        default :
            if (logEnable) log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
            break    // 2022/09/16
    }
}

def processTuyaCluster( descMap ) {
    if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME
        if (settings?.logEnable) log.debug "${device.displayName} time synchronization request from device, descMap = ${descMap}"
        def offset = 0
        try {
            offset = location.getTimeZone().getOffset(new Date().getTime())
            //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}"
        }
        catch(e) {
            if (settings?.logEnable) log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero"
        }
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8))
        // TODO : send raw command without 'need confirmation' frame control !
        if (settings?.logEnable) log.trace "${device.displayName} now is: ${now()}"  // KK TODO - convert to Date/Time string!
        if (settings?.logEnable) log.debug "${device.displayName} sending time data : ${cmds}"
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
        //if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response
        String clusterCmd = descMap?.data[0]
        def status = descMap?.data[1]
        logDebug "Tuya cluster confirmation for command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
        if (status != "00") {
            if (settings?.logEnable) log.warn "${device.displayName} ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} group = ${getModelGroup()} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"
        }
    }
    else if ((descMap?.clusterInt==CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02"))
    {
        def dataLen = descMap?.data.size()
        //log.warn "dataLen=${dataLen}"
        def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
        for (int i = 0; i < (dataLen-4); ) {
            def dp = zigbee.convertHexToInt(descMap?.data[2+i])                // "dp" field describes the action/message of a command frame
            def dp_id = zigbee.convertHexToInt(descMap?.data[3+i])               // "dp_identifier" is device dependant
            def fncmd_len = zigbee.convertHexToInt(descMap?.data[5+i]) 
            def fncmd = getTuyaAttributeValue(descMap?.data, i)                //
            if (settings?.logEnable) log.trace "${device.displayName}  dp_id=${dp_id} dp=${dp} fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})"
            processTuyaDP( descMap, dp, dp_id, fncmd)
            i = i + fncmd_len + 4;
            //log.warn "next index is : ${i}"
        }
        //log.warn "##### end of parsing ####"
    } // if (descMap?.command == "01" || descMap?.command == "02")
}


def processTuyaDP( descMap, dp, dp_id, fncmd) {
        switch (dp) {
            case 0x01 : // temperature in ?C for most models
                //
                if (getModelGroup() == "TS0601_Contact") {
                    def value = fncmd == 0 ? "closed" : "open"    // inverted!
                    sendEvent("name": "contact", "value": value)
                    if (settings?.txtEnable) log.info "${device.displayName} Contact is ${value}"
                }
                else if (getModelGroup() != "TS0601_AUBESS") { // temperature in C
                    if (fncmd > 32767) {
                	    //Here we deal with negative values
                	    fncmd = fncmd - 65536
                    }
                    temperatureEvent( fncmd / 10.0 )
                }
                else {
                    def lomihi = fncmd == 0 ? "low" : fncmd == 1 ? "medium" : fncmd == 2 ? "high" : "unknown"
                    if (settings?.logEnable) log.debug "${device.displayName} Tuya illuminance status is: ${lomihi} (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})"
                }
                break
            case 0x02 : // humidity % for most of the models; 'TS0601_Contact'illuminance; 'TS0601_Contact'0 battery %
                if (getModelGroup() == 'TS0601_AUBESS') {
                    illuminanceEventLux( safeToInt( fncmd ) )
                }
                else if (getModelGroup() == "TS0601_Contact") {
                    getBatteryPercentageResult(fncmd * 2)
                }
                else {
                    if (device.getDataValue("manufacturer") == "_TZE200_bjawzodf") {
                        humidityEvent( (fncmd / 10.0) as int )
                    }
                    else {
                        humidityEvent( fncmd )
                    }
                }
                break
            case 0x03 : // humidity or  illuminance - NOT TESTED!
                if (getModelGroup() in ['TS0601_Soil']) {
                    logDebug "Soil Sensor humidity raw = ${fncmd}"
                    humidityEvent( fncmd )
                }
                else {
                    illuminanceEvent(fncmd)
                }
                break
            case 0x04 : // battery
                getBatteryPercentageResult(fncmd * 2)
                if (settings?.txtEnable) log.info "${device.displayName} battery is $fncmd %"
                break
            case 0x05 : // Soil Monitor
                if (fncmd > 32767) {
                    // not good for the plants ...
                	fncmd = fncmd - 65536
                }
                temperatureEvent( fncmd )
                break
            case 0x09: // temp. scale  1=Fahrenheit 0=Celsius (TS0601 Tuya and Haoze) TS0601_Tuya does not change the symbol on the LCD !
                if (settings?.logEnable) log.info "${device.displayName} Temperature scale reported by device is: ${fncmd == 1 ? 'Fahrenheit' :'Celsius' }"
                break
            case 0x0A: // (10) Max. Temp Alarm, Value / 10  (both TS0601_Tuya and TS0601_Haozee)
                if (((safeToDouble(settings?.maxTempAlarmPar)*10.0 as int) == (fncmd as int)) || (getModelGroup() in ['TS0601_Haozee']))  {
                    if (settings?.logEnable) log.info "${device.displayName} reported temperature alarm upper limit ${fncmd/10.0 as double} C"
                }
                else {
                    if (settings?.logEnable) log.warn "${device.displayName} warning: temperature alarm upper limit reported by the device (${fncmd/10.0 as double} C) differs from the preference setting (${settings?.maxTempAlarmPar} C)"
                }
                break
            case 0x0B: // (11) Min. Temp Alarm, Value / 10 (both TS0601_Tuya and TS0601_Haozee)
                if (((safeToDouble(settings?.minTempAlarmPar)*10.0 as int) == (fncmd as int)) || (getModelGroup() in ['TS0601_Haozee'])) {
                    if (settings?.logEnable) log.info "${device.displayName} reported temperature alarm lower limit ${fncmd/10.0 as double} C"
                }
                else {
                    if (settings?.logEnable) log.warn "${device.displayName} warning: temperature alarm lower limit reported by the device (${fncmd/10.0 as double} C) differs from the preference setting (${settings?.minTempAlarmPar} C)"
                }
                break
            case 0x0C: // (12) Max?. Humidity Alarm    (Haozee only?)
                if (settings?.logEnable) log.info "${device.displayName} humidity alarm upper limit is ${fncmd} "
                break
            case 0x0D: // (13) Min?. Humidity Alarm    (Haozee only?)
                if (settings?.logEnable) log.info "${device.displayName} humidity alarm lower limit is ${fncmd} "
                //device.updateSetting("minHumidityAlarmPar", [value:fncmd, type:"number"])
                break
            case 0x0E: // (14) Temperature Alarm 0 = low alarm? 1 = high alarm? 2 = alarm cleared
                if (getModelGroup() in ['TS0601_Soil']) {
                    if (settings?.txtEnable) log.info "${device.displayName} battery_state (0x0E) is ${fncmd}" 
                }
                else {
                    if (fncmd == 1) {
                        if (settings?.txtEnable) log.info "${device.displayName} Minimal Temperature Alarm (0x0E=${fncmd}) is active"
                    }
                    else if (fncmd == 0) {    // TS0601_Haozee only?
                        if (settings?.txtEnable) log.info "${device.displayName} Maximal Temperature Alarm (0x0E=${fncmd}) is active"
                    }
                    else if (fncmd == 2 ) {
                        if (getModelGroup() in ['TS0601_Haozee']) {
                            if (settings?.txtEnable) log.info "${device.displayName} Maximal Temperature Alarm (0x0E=${fncmd}) is inactive"
                        }
                        else {
                            if (settings?.txtEnable) log.info "${device.displayName} Minimal Temperature Alarm (0x0E=${fncmd}) is inactive"
                        }
                    }
                    else {
                        if (settings?.txtEnable) log.warn "${device.displayName} Temperature Alarm (0x0E) UNKNOWN value ${fncmd}" // 1 if alarm (lower alarm) ? 2 if lower alam is cleared
                    }
                }
                break
            case 0x0F: // humidity Alarm 0 = low alarm? 1 = high alarm? 2 = alarm cleared    (Haozee only?)
                if (getModelGroup() in ['TS0601_Soil']) {
                    getBatteryPercentageResult(fncmd * 2)                
                }
                else {
                    if (fncmd == 1) {
                        if (settings?.txtEnable) log.info "${device.displayName} Minimal Humidity Alarm (0x0F=${fncmd}) is active"
                    }
                    else if (fncmd == 0) {
                        if (settings?.txtEnable) log.info "${device.displayName} Maximal Humidity Alarm (0x0F=${fncmd}) is active"
                    }
                    else if (fncmd == 2 ) {
                        if (settings?.txtEnable) log.info "${device.displayName} Humidity Alarm (0x0F=${fncmd}) is inactive"
                    }
                    else {
                        if (settings?.logEnable) log.warn "${device.displayName} Temperature Alarm (0x0E) UNKNOWN value ${fncmd}" // 1 if alarm (lower alarm) ? 2 if lower alam is cleared
                    }
                }
                break
            case 0x11 : // (17) temperature max reporting interval, default 120 min (Haozee only) // maxReportingTimeTemp
                if (settings?.maxReportingTimeTemp == ((fncmd*60/2.5) as int)) {
                    if (settings?.logEnable) log.info "${device.displayName} reported temperature max reporting interval ${((fncmd*60/2.5) as int)} seconds"
                }
                else {
                    if (settings?.logEnable) log.warn "${device.displayName} warning: temperature max reporting interval reported by the device (${((fncmd*60/2.5) as int)}s) differs from the preference setting (${settings?.maxReportingTimeTemp}s)"
                }
                break
            case 0x12 : // (18) humidity max reporting interval, default 120 min (Haozee only)
                if (settings?.maxReportingTimeHumidity == ((fncmd*60/2.5) as int)) {
                    if (settings?.logEnable) log.info "${device.displayName} reported humidity max reporting interval ${((fncmd*60/2.5) as int)}  seconds"
                }
                else {
                    if (settings?.logEnable) log.warn "${device.displayName} warning: humidity max reporting interval reported by the device (${((fncmd*60/2.5) as int)}s) differs from the preference setting (${settings?.maxReportingTimeHumidity}s)"
                }
                break
            case 0x13 : // (19) temperature sensitivity(value/2/10) default 0.3C ( divide / 2 for Haozee only?) 
                if ((safeToDouble(settings?.temperatureSensitivity)*20.0 as int) == (fncmd as int)) {
                    if (settings?.logEnable) log.info "${device.displayName} reported temperature sensitivity ${fncmd/20.0} C"
                }
                else {
                    if (settings?.logEnable) log.warn "${device.displayName} warning: temperature sensitivity reported by the device (${fncmd/20.0}) differs from the preference setting (${settings?.temperatureSensitivity})"
                }
                break
            case 0x14 : // (20) humidity sensitivity default 3%  (Haozee only)
                if (settings?.humiditySensitivity == fncmd) {
                    if (settings?.logEnable) log.info "${device.displayName} reported humidity sensitivity ${fncmd} %"
                }
                else {
                    if (settings?.logEnable) log.warn "${device.displayName} warning: humidity sensitivity reported by the device (${fncmd}%) differs from the preference setting (${settings?.humiditySensitivity}%)"
                }
                break
            case 0x65 : // (101)
                illuminanceEventLux( safeToInt( fncmd ) )  // _TZE200_pay2byax
                break
            default :
                if (settings?.logEnable) log.warn "${device.displayName} <b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
                break
        }
}


private int getTuyaAttributeValue(ArrayList _data, index) {
    int retValue = 0

    if (_data.size() >= 6) {
        int dataLength = _data[5+index] as Integer
        int power = 1;
        for (i in dataLength..1) {
            retValue = retValue + power * zigbee.convertHexToInt(_data[index+i+5])
            power = power * 256
        }
    }
    return retValue
}

def getModelGroup() {
    def manufacturer = device.getDataValue("manufacturer")
    def modelGroup = 'UNKNOWN'
    if (modelGroupPreference == null) {
        device.updateSetting("modelGroupPreference",  [value:"Auto detect", type:"enum"])
    }
    if (modelGroupPreference == "Auto detect") {
        if (manufacturer in Models) {
            modelGroup = Models[manufacturer]
        }
        else {
             modelGroup = 'UNKNOWN'
        }
    }
    else {
         modelGroup = modelGroupPreference
    }
    //    if (settings?.logEnable) log.trace "${device.displayName} manufacturer ${manufacturer} group is ${modelGroup}"
    return modelGroup
}


def temperatureEvent( temperature, isDigital=false ) {
    def map = [:]
    map.name = "temperature"
    def Scale = location.temperatureScale
    if (Scale == "F") {
        temperature = (temperature * 1.8) + 32
        map.unit = "\u00B0"+"F"
    }
    else {
        map.unit = "\u00B0"+"C"
    }
    def tempCorrected = temperature + safeToDouble(settings?.temperatureOffset)
    map.value  =  Math.round((tempCorrected - 0.05) * 10) / 10
    map.type = isDigital == true ? "digital" : "physical"
    map.isStateChange = true
    map.descriptionText = "${map.name} is ${tempCorrected} ${map.unit}"
    Map lastRxMap = stringToJsonMap(state.lastRx)
    def timeElapsed = Math.round((now() - lastRxMap['tempTime'])/1000)
    Integer timeRamaining = (minReportingTimeTemp - timeElapsed) as Integer
    if (timeElapsed >= minReportingTimeTemp) {
		if (settings?.txtEnable) {log.info "${device.displayName} ${map.descriptionText}"}
		unschedule("sendDelayedEventTemp")		//get rid of stale queued reports
        lastRxMap['tempTime'] = now()
        sendEvent(map)
	}		
    else {         // queue the event
    	map.type = "delayed"
        if (settings?.logEnable) log.debug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${map}"
        runIn(timeRamaining, 'sendDelayedEventTemp',  [overwrite: true, data: map])
    }
    state.lastRx = mapToJsonString(lastRxMap)
}

private void sendDelayedEventTemp(Map map) {
    if (settings?.txtEnable) {log.info "${device.displayName} ${map.descriptionText} (${map.type})"}
    Map lastRxMap = stringToJsonMap(state.lastRx); try {lastRxMap['tempTime'] = now()} catch (e) {lastRxMap['tempTime']=now()-(minReportingTimeHumidity * 2000)}; state.lastRx = mapToJsonString(lastRxMap)
    sendEvent(map)
}

def humidityEvent( humidity, isDigital=false ) {
    def map = [:]
    def humidityAsDouble = safeToDouble(humidity) +safeToDouble(settings?.humidityOffset)
    humidityAsDouble = humidityAsDouble < 0.0 ? 0.0 : humidityAsDouble > 100.0 ? 100.0 : humidityAsDouble
    map.value = Math.round(humidityAsDouble)
    map.name = "humidity"
    map.unit = "% RH"
    map.type = isDigital == true ? "digital" : "physical"
    map.isStateChange = true
    map.descriptionText = "${map.name} is ${humidityAsDouble.round(1)} ${map.unit}"
    Map lastRxMap = stringToJsonMap(state.lastRx)
    //if (state.lastHumi == null ) state.lastHumi = now() - (minReportingTimeHumidity * 2000)
    def timeElapsed = Math.round((now() - lastRxMap['humiTime'])/1000)
    Integer timeRamaining = (minReportingTimeHumidity - timeElapsed) as Integer
    if (timeElapsed >= minReportingTimeHumidity) {
        if (settings?.txtEnable) {log.info "${device.displayName} ${map.descriptionText}"}
        unschedule("sendDelayedEventHumi")
        lastRxMap['humiTime'] = now()
        sendEvent(map)
    }
    else {         // queue the event 
    	map.type = "delayed"
        if (settings?.logEnable) log.debug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${map}"
        runIn(timeRamaining, 'sendDelayedEventHumi',  [overwrite: true, data: map])
    }
    state.lastRx = mapToJsonString(lastRxMap)
}

private void sendDelayedEventHumi(Map map) {
    if (settings?.txtEnable) {log.info "${device.displayName} ${map.descriptionText} (${map.type})"}
	//state.lastHumi = now()
    Map lastRxMap = stringToJsonMap(state.lastRx); try {lastRxMap['humiTime'] = now()} catch (e) {lastRxMap['humiTime']=now()-(minReportingTimeHumidity * 2000)}; state.lastRx = mapToJsonString(lastRxMap)
	sendEvent(map)
}

def switchEvent( value ) {
    def map = [:]
    map.name = "switch"
    map.value = value
    map.descriptionText = "${device.displayName} switch is ${value}"
    if (settings?.txtEnable) {log.info "${map.descriptionText}"}
    sendEvent(map)
}

def motionEvent( value ) {
    def map = [:]
    map.name = "motion"
    map.value = value  ? 'active' : 'inactive'
    map.descriptionText = "${device.displayName} motion is ${map.value}"
    if (settings?.txtEnable) {log.info "${map.descriptionText}"}
    sendEvent(map)
}

def illuminanceEvent( illuminance, isDigital=false ) {
    //def rawLux = Integer.parseInt(descMap.value,16)
	def lux = illuminance > 0 ? Math.round(Math.pow(10,(illuminance/10000))) : 0
    sendEvent("name": "illuminance", "value": lux, "type": isDigital == true ? 'digital':'physical', "unit": "lx")
    if (settings?.txtEnable) log.info "$device.displayName illuminance is ${lux} Lux"
}

def illuminanceEventLux( Integer lux, isDigital=false ) {
    sendEvent("name": "illuminance", "value": lux, "type": isDigital == true ? 'digital':'physical', "unit": "lx")
    if (settings?.txtEnable) log.info "$device.displayName illuminance is ${lux} Lux"
}

//  called from initialize() and when installed as a new device
def installed() {
    sendEvent(name: "_info", value: "installed", isStateChange: true)
    if (settings?.txtEnable) log.info "${device.displayName} installed()..."
    unschedule()
    initializeVars(fullInit = true )
}

//
def updated() {
    ArrayList<String> cmds = []
    Map lastRxMap = stringToJsonMap(state.lastRx)
    Map lastTxMap = stringToJsonMap(state.lastTx)

    state.modelGroup = getModelGroup()

    if (settings?.txtEnable) log.info "${device.displayName} Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b> modelGroupPreference = <b>${modelGroupPreference}</b> (${getModelGroup()})"
    if (settings?.txtEnable) log.info "${device.displayName} Debug logging is ${logEnable}; Description text logging is ${txtEnable}"
    if (logEnable==true) {
        runIn(86400, "logsOff", [overwrite: true, misfire: "ignore"])    // turn off debug logging after 30 minutes
        if (settings?.txtEnable) log.info "${device.displayName} Debug logging is will be turned off after 24 hours"
    }
    else {
        unschedule("logsOff")
    }
    Integer fncmd
    if (getModelGroup() in ['TS0601_Tuya','TS0601_Haozee']) {
        Integer intValue = ((safeToDouble(settings?.temperatureSensitivity )) * 20.0) as int
        if (settings?.logEnable) log.trace "${device.displayName} setting temperatureSensitivity to ${(intValue as Double)/20.0} C"
        cmds += sendTuyaCommand("13", DP_TYPE_VALUE, zigbee.convertToHexString(intValue as int, 8))
    }
    
    if (getModelGroup() in ['TS0601_Tuya','TS0601_Haozee','TS0201_LCZ030']) {
        if (location.temperatureScale == "C") {    // Celsius
            cmds += sendTuyaCommand("09", DP_TYPE_ENUM, "00")
            if (settings?.logEnable) log.trace "${device.displayName} setting temperature scale to Celsius: ${cmds}"
        }
        else if (location.temperatureScale == "F") {    // Fahrenheit
            cmds += sendTuyaCommand("09", DP_TYPE_ENUM, "01")
            if (settings?.logEnable) log.trace "${device.displayName} setting temperature scale to Fahrenheit: ${cmds}"
        }
        else {
            if (settings?.logEnable) log.warn "${device.displayName} temperatureScaleParameter does NOT MATCH! (${location.temperatureScale})"
        }
    }
    
    if (getModelGroup() in ['TS0601_Tuya','TS0201_LCZ030']) {
        fncmd = (safeToDouble( maxTempAlarmPar ) * 10) as int
        if (settings?.logEnable) log.trace "${device.displayName} setting maxTempAlarm to ${fncmd/10.0 as double} C"
        cmds += sendTuyaCommand("0A", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))

        fncmd = (safeToDouble( minTempAlarmPar ) * 10) as int
        if (settings?.logEnable) log.trace "${device.displayName} setting minTempAlarm to ${fncmd/10.0 as double} C"
        cmds += sendTuyaCommand("0B", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
    }
    if (getModelGroup() in ['TS0601_Haozee']) {
        Integer intValue = settings?.humiditySensitivity as int
        if (settings?.logEnable) log.trace "${device.displayName} setting  humiditySensitivity to ${intValue} %"
        cmds += sendTuyaCommand("14", DP_TYPE_VALUE, zigbee.convertToHexString(intValue as int, 8))
        //
        intValue = ((settings?.maxReportingTimeTemp * 2.5) as int) / 60
        if (settings?.logEnable) log.trace "${device.displayName} setting Temperature Max reporting time to ${(intValue/2.5) as int} minutes"
        cmds += sendTuyaCommand("11", DP_TYPE_VALUE, zigbee.convertToHexString(intValue as int, 8))
        //
        intValue = ((settings?.maxReportingTimeHumidity *2.5) as int) / 60
        if (settings?.logEnable) log.trace "${device.displayName} setting Humidity Max reporting time to ${(intValue/2.5) as int} minutes"
        cmds += sendTuyaCommand("12", DP_TYPE_VALUE, zigbee.convertToHexString(intValue as int, 8))

        /*
        fncmd = safeToInt( maxHumidityAlarmPar )
        if (settings?.logEnable) log.trace "${device.displayName} changing maxHumidityAlarm to= ${fncmd}"
        cmds += sendTuyaCommand("0C", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
        fncmd = safeToInt( minHumidityAlarmPar )
        if (settings?.logEnable) log.trace "${device.displayName} changing minHumidityAlarm to= ${fncmd}"
        cmds += sendTuyaCommand("0D", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
        */
    }
    if (getModelGroup() in ['TS0601_Haozee']) {
        // TODO - write attribute 0xF001, cluster 0x400
    }
    /*
    if (getModelGroup() in ['TS0201_TH']) {    // //temperatureSensitivity  humiditySensitivity minReportingTimeTemp maxReportingTimeTemp c maxReportingTimeHumidity
        lastTxMap.tempCfg = (settings?.minReportingTimeTemp as int).toString() + "," + (maxReportingTimeTemp as int).toString() + "," + ((temperatureSensitivity * 100) as int).toString()
        lastTxMap.humiCfg = (settings?.minReportingTimeHumidity as int).toString() + "," + (maxReportingTimeHumidity as int).toString() + "," + ((humiditySensitivity *100) as int).toString()
        
    	cmds += zigbee.configureReporting(0x0402, 0x0000, 0x29, settings?.minReportingTimeTemp as int, maxReportingTimeTemp as int, (temperatureSensitivity * 100 ) as int, [:], 200)  // Configure temperature - Report after 10 seconds if any change, every 10 minutes if no change
    	cmds += zigbee.configureReporting(0x0405, 0x0000, DataType.UINT16, settings?.minReportingTimeHumidity as int, maxReportingTimeHumidity as int, (humiditySensitivity * 100) as int, [:], 200)  // Configure Humidity - - Report after 10 seconds if any change, every 10 minutes if no change
        cmds += zigbee.reportingConfiguration(0x0402, 0x0000, [:], 250)
        cmds += zigbee.reportingConfiguration(0x0405, 0x0000, [:], 250)
    }
*/
    // 
    if (getModelGroup() in ["Zigbee NON-Tuya", "TS0201_TH"]) {    // //temperatureSensitivity  humiditySensitivity minReportingTimeTemp maxReportingTimeTemp c maxReportingTimeHumidity
       
        lastTxMap.tempCfg = (settings?.minReportingTimeTemp as int).toString() + "," + (settings?.maxReportingTimeTemp as int).toString() + "," + ((settings?.temperatureSensitivity * 100) as int).toString()
        lastTxMap.humiCfg = (settings?.minReportingTimeHumidity as int).toString() + "," + (settings?.maxReportingTimeHumidity as int).toString() + "," + ((settings?.humiditySensitivity *100) as int).toString()
        
        if (lastTxMap.tempCfg != lastRxMap.tempCfg) {
    	    cmds += zigbee.configureReporting(0x0402, 0x0000, DataType.INT16, settings?.minReportingTimeTemp as int, settings?.maxReportingTimeTemp as int, (settings?.temperatureSensitivity * 100) as int, [:], 200)
            log.info "configure temperature reporting (${lastTxMap.tempCfg}) pending ..."
            lastTxMap.tempCfgOK = false
        }
        else {
            logDebug "Temperature reporting already configured (${lastTxMap.tempCfg}), skipping ..."
            lastTxMap.tempCfgOK = true
        }
        if (lastTxMap.humiCfg != lastRxMap.humiCfg) {
    	    cmds += zigbee.configureReporting(0x0405, 0x0000, DataType.UINT16, settings?.minReportingTimeHumidity as int, settings?.maxReportingTimeHumidity as int, (settings?.humiditySensitivity *100) as int, [:], 200)
            log.info "configure humidity reporting (${lastTxMap.humiCfg}) pending ..."
            lastTxMap.humiCfgOK = false
        }
        else {
            logDebug "Humidity reporting already configured (${lastTxMap.humiCfg}), skipping ..."
            lastTxMap.humiCfgOK = true
        }
        cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 10/*600*/, 600/*21600*/, 0x01/*0x01*/, [:], 200)
        //cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 0/*600*/, 300/*21600*/, 0x01/*0x01*/, [:], 200)
        
        cmds += zigbee.reportingConfiguration(0x0402, 0x0000, [:], 250)
        cmds += zigbee.reportingConfiguration(0x0405, 0x0000, [:], 250)
        cmds += zigbee.reportingConfiguration(0x0001, 0x0021, [:], 250)
        //cmds += zigbee.reportingConfiguration(0x0001, 0x0020, [:], 250)
    } 
    
    /* 2022-05-09 - do not configre reporting for multi-EP devices like TS0201 _TZ3000_qaaysllp !!! (binds to wrong EP ?)
    if (getModelGroup() in ["Zigbee NON-Tuya", 'TS0201_LCZ030']) {
    	cmds += zigbee.configureReporting(0x0402, 0x0000, DataType.INT16, 10, 600, 1, [:], 200)  // Configure temperature - Report after 10 seconds if any change, every 10 minutes if no change
    	cmds += zigbee.configureReporting(0x0405, 0x0000, DataType.INT16, 10, 600, 1, [:], 200)  // Configure Humidity - - Report after 10 seconds if any change, every 10 minutes if no change
    	cmds += zigbee.configureReporting(0x0400, 0x0000, DataType.INT16, 10, 600, 5, [:], 200)  // Configure Illuminance - Report after 10 seconds if any change, every 10 minutes if no change, 5 Lux change?
    	cmds += zigbee.configureReporting(0x0403, 0x0000, DataType.INT16, 10, 600, 1, [:], 200)  // Configure Pressure - Report after 10 seconds if any change, every 10 minutes if no change
   		cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 0, 21600, 1, [:], 200)   // Configure Voltage - Report once per 6hrs or if a change of 100mV detected
   		cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 0, 21600, 1, [:], 200)   // Configure Battery % - Report once per 6hrs or if a change of 1% detected
    }
    */

    //illuminanceSensitivity - descMap.cluster == "0400" && descMap.attrId == "F001"
    // TODO !!!!! ( for TS0201 Neo Coolcam and TS0222  MOES ZSS-ZK-THL
    //
    
    state.lastTx = mapToJsonString(lastTxMap)
    def pendingConfig = lastTxMap.tempCfgOK == true ? 0 : 1
    pendingConfig    += lastTxMap.humiCfgOK == true ? 0 : 1
    if (isConfigurable()) {
        logInfo "pending ${pendingConfig} reporting configurations"
        if (pendingConfig != 0 ) {
            updateInfo("Pending ${pendingConfig} configuration(s). Wake up the device!")
        }
    }
    
    sendZigbeeCommands( cmds )
}


def isPendingConfig() {
    Map lastTxMap = stringToJsonMap(state.lastTx)
    if (lastTxMap.tempCfgOK == false || lastTxMap.humiCfgOK == false) {
        return true
    }
    else {
        return false
    }
}

// called from parse() when any packet is received from the awaken device ...
def ConfigurationStateMachine() {
    if (!isConfigurable()) {
        return
    }
    Map lastTxMap = stringToJsonMap(state.lastTx)
    def configState = state.configState
    logDebug "ConfigurationStateMachine configState = ${configState}"
    switch (configState) {
        case 0 : // idle
            if (isPendingConfig()) {
                logDebug "configuration pending ..."
                updateInfo("sending the reporting configuration...") 
                lastTxMap.cfgTimer = ConfigTimer
                updated()
                runIn(1, "configTimer" , [overwrite: true, misfire: "ignore"])
                configState = 1
            }
            else {
                logWarn "ConfigurationStateMachine called without isPendingConfig?"
                unschedule("configTimer")	
            }
            break
        case 1 : // waiting 10 seconds for acknowledge from the device // TODO - process config ERRORS !!!
            if (!isPendingConfig()) {
                updateInfo("configured") 
                lastTxMap.cfgTimer = 0
                configState = 0
                unschedule("configTimer")	
            }
            else if (lastTxMap.cfgTimer == null || lastTxMap.cfgTimer == 0) {    // timeout
                logDebug "Timeout!"
                updateInfo("Timeout!") 
                lastTxMap.cfgTimer = 0
                unschedule("configTimer")	
                configState = 0    // try again next time a packet is received from the device..
            }
            else {
                logDebug "config confirmation still pending ... lastTxMap.cfgTimer is ${lastTxMap.cfgTimer}"
            }
            break
        default : 
            logWarn "ConfigurationStateMachine() unknown state ${configState}"
            unschedule("configTimer")	
            configState = 0
            break
    }
    state.configState = configState
    state.lastTx = mapToJsonString(lastTxMap)
}

// started from ConfigurationStateMachine
def configTimer() {
    Map lastTxMap = stringToJsonMap(state.lastTx)
    logDebug "configTimer() callled"
    if (lastTxMap.cfgTimer != null) {
        if (!isPendingConfig()) {
            logDebug "configuration is successful! "
            ConfigurationStateMachine()
        }
        else {
            lastTxMap.cfgTimer = lastTxMap.cfgTimer - 1
            if (lastTxMap.cfgTimer >= 0 ) {
                state.lastTx = mapToJsonString(lastTxMap)    // flush the timer!
                ConfigurationStateMachine()
                runIn(1, "configTimer" /*, [overwrite: true, misfire: "ignore"]*/)
                logDebug "scheduling again configTimer = ${lastTxMap.cfgTimer}"
            }
            else {
                logDebug "configTimer expired! Do not restart it."
            }
        }
    }
    else {
        lastTxMap.cfgTimer = 0
    }
    state.lastTx = mapToJsonString(lastTxMap)
}

def pollTS0222() {
    List<String> cmds = []
	cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay=200)  // Battery Percent
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0402 0x0000 {}" //, "delay 200",
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0405 0x0000 {}" //, "delay 200",
	sendZigbeeCommands(cmds)
}

def refresh() {
    checkDriverVersion()
    if (getModelGroup() == 'TS0222') {
        pollTS0222()
    }
    else if (getModelGroup() in ['TS0201_TH']) {
        List<String> cmds = []
        cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay=200) 
	    cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay=200)
    	cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay=200)
        sendZigbeeCommands( cmds )     
    }
    else {
        logInfo "refresh() is not implemented for this sleepy Zigbee device"
    }
}

def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

def checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logInfo "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        initializeVars( fullInit = false )
        //
        if (state.rxCounter != null) state.remove("rxCounter")
        if (state.txCounter != null) state.remove("txCounter")
        if (state.packetID != null)  state.remove("packetID")
        
        if (state.lastRx == null || state.stats == null || state.lastTx == null) {
            resetStats()
        }

        //
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

def resetStats() {
    Map stats = [
        rxCtr : 0,
        txCtr : 0,
        rejoins: 0
    ]
    
    Map lastRx = [
        tempTime : now() - defaultMinReportingTime * 1000,
        humiTime : now() - defaultMinReportingTime * 1000,
        tempCfg : '-1,-1,-1',
        humiCfg : '-1,-1,-1'
    ]
    
    Map lastTx = [
        tempCfg : '-1,-1,-1',
        humiCfg : '-1,-1,-1',
        tempCfgOK : false,
        humiCfgOK : false,
        cfgTimer : 0
    ]
    state.stats  =  mapToJsonString( stats )
    state.lastRx =  mapToJsonString( lastRx )
    state.lastTx =  mapToJsonString( lastTx )
    if (txtEnable==true) log.info "${device.displayName} Statistics were reset. Press F5 to refresh the device page"
}


def logInitializeRezults() {
    if (settings?.txtEnable) log.info "${device.displayName} manufacturer  = ${device.getDataValue("manufacturer")} ModelGroup = ${getModelGroup()}"
    if (settings?.txtEnable) log.info "${device.displayName} Initialization finished\r                          version=${version()} (Timestamp: ${timeStamp()})"
}

// called by initialize() button
void initializeVars(boolean fullInit = true ) {
    log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        resetStats()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    state.configState = 0    // reset the configuration state machine

    if (fullInit == true || settings?.modelGroupPreference == null) device.updateSetting("modelGroupPreference", [value:"Auto detect", type:"enum"])
    if (fullInit == true || settings?.logEnable == null) device.updateSetting("logEnable", true)
    if (fullInit == true || settings?.txtEnable == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || settings?.temperatureOffset == null) device.updateSetting("temperatureOffset", [value:0.0, type:"decimal"])
    if (fullInit == true || settings?.humidityOffset == null) device.updateSetting("humidityOffset", [value:0.0, type:"decimal"])
    if (fullInit == true || settings?.advancedOptions == null) device.updateSetting("advancedOptions", false)
    if (fullInit == true || settings?.temperatureSensitivity == null) device.updateSetting("temperatureSensitivity", [value:0.5, type:"decimal"])
    if (fullInit == true || settings?.humiditySensitivity == null) device.updateSetting("humiditySensitivity", [value:5, type:"number"])
    if (fullInit == true || settings?.illuminanceSensitivity == null) device.updateSetting("illuminanceSensitivity", [value:12, type:"number"])
    if (fullInit == true || settings?.minTempAlarmPar == null) device.updateSetting("minTempAlarmPar",  [value:0.0, type:"decimal"])
    if (fullInit == true || settings?.maxTempAlarmPar == null) device.updateSetting("maxTempAlarmPar",  [value:39.0, type:"decimal"])
    if (fullInit == true || settings?.minHumidityAlarmPar == null) device.updateSetting("minHumidityAlarmPar",  [value:20, type:"number"])
    if (fullInit == true || settings?.maxHumidityAlarmPar == null) device.updateSetting("maxHumidityAlarmPar",  [value:60, type:"number"])
    if (fullInit == true || settings?.minReportingTimeTemp == null) device.updateSetting("minReportingTimeTemp",  [value:10, type:"number"])
    if (fullInit == true || settings?.maxReportingTimeTemp == null) device.updateSetting("maxReportingTimeTemp",  [value:3600, type:"number"])
    if (fullInit == true || settings?.minReportingTimeHumidity == null) device.updateSetting("minReportingTimeHumidity",  [value:10, type:"number"])
    if (fullInit == true || settings?.maxReportingTimeHumidity == null) device.updateSetting("maxReportingTimeHumidity",  [value:3600, type:"number"])
    if (fullInit == true || state.notPresentCounter == null) state.notPresentCounter = 0
    //
    if (fullInit == true || state.modelGroup == null)  state.modelGroup = getModelGroup()
    //if (fullInit == true || state.lastTemp == null) state.lastTemp = now() - defaultMinReportingTime * 1000
    //if (fullInit == true || state.lastHumi == null) state.lastHumi = now() - defaultMinReportingTime * 1000
    
}

def tuyaBlackMagic() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200)    // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, attributeReportingStatus
    cmds += zigbee.writeAttribute(0x0000, 0xffde, 0x20, 0x13, [:], delay=200)    // was commented out ver 1.0.10  2022/11/10; returned back ver 1.20 01/15/2023
    return  cmds
}

def configure() {
    if (settings?.txtEnable) log.info "${device.displayName} configure().."
    List<String> cmds = []
    cmds += tuyaBlackMagic()
    sendZigbeeCommands(cmds)
    runIn(1, updated) // send the default or previously configured preference parameters during the Zigbee pairing process..
}

// NOT called when the driver is initialized as a new device, because the Initialize capability is NOT declared!
def initialize() {
    log.info "${device.displayName} Initialize()..."
    unschedule()
    initializeVars(fullInit = true)
    installed()
    configure()
    runIn( 3, logInitializeRezults)
}

private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    cmds += zigbee.command(CLUSTER_TUYA, SETDATA, [:], delay=200, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    if (settings?.logEnable) log.trace "${device.displayName} sendTuyaCommand = ${cmds}"
    return cmds
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (settings?.logEnable) {log.trace "${device.displayName} sendZigbeeCommands(cmd=$cmd)"}
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
            //if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    Map statsMap = stringToJsonMap(state.stats); try {statsMap['txCtr']++ } catch (e) {statsMap['txCtr']=0}; state.stats = mapToJsonString(statsMap)
    sendHubCommand(allActions)
}

private getPACKET_ID() {
    //state.packetID = ((state.packetID ?: 0) + 1 ) % 65536
    //return zigbee.convertToHexString(state.packetID, 4)
    return zigbee.convertToHexString(new Random().nextInt(65536), 4)
}

private getDescriptionText(msg) {
	def descriptionText = "${device.displayName} ${msg}"
	if (settings?.txtEnable) log.info "${descriptionText}"
	return descriptionText
}

def logsOff(){
    log.warn "${device.displayName} debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def getBatteryPercentageResult(rawValue) {
    if (settings?.logEnable) log.debug "${device.displayName} Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%"
    def result = [:]

    if (0 <= rawValue && rawValue <= 200) {
        result.name = 'battery'
        result.translatable = true
        result.value = Math.round(rawValue / 2)
        result.descriptionText = "${device.displayName} battery is ${result.value}%"
        result.isStateChange = true
        result.unit = "%"
        result.type = 'physical'
        sendEvent(result)
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} ignoring BatteryPercentageResult(${rawValue})"
    }
}

private Map getBatteryResult(rawValue) {
    if (settings?.logEnable) log.debug "${device.displayName} getBatteryResult volts = ${(double)rawValue / 10.0}"
    def linkText = getLinkText(device)

    def result = [:]

    def volts = rawValue / 10
    if (!(rawValue == 0 || rawValue == 255)) {
        def minVolts = 2.1
        def maxVolts = 3.0
        def pct = (volts - minVolts) / (maxVolts - minVolts)
        def roundedPct = Math.round(pct * 100)
        if (roundedPct <= 0)
        roundedPct = 1
        result.value = Math.min(100, roundedPct)
        result.descriptionText = "${linkText} battery is ${result.value}%"
        result.name = 'battery'
        result.isStateChange = true
        result.type = 'physical'
        result.unit = "%"
        sendEvent(result)
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} ignoring BatteryResult(${rawValue})"
    }
}

// called when any event was received from the Zigbee device in parse() method..
def setPresent() {
    if ((device.currentValue("healthStatus", true) ?: "unknown") != "online") {
        sendHealthStatusEvent("online")
        powerSourceEvent() // sent ony once now - 2023-01-31
        if (settings?.txtEnable) log.info "${device.displayName} is present"
        if (!isRadar()) {
            if (device.currentValue('battery', true) == 0 ) {
                if (state.lastBattery != null &&  safeToInt(state.lastBattery) != 0) {
                    sendBatteryEvent(safeToInt(state.lastBattery), isDigital=true)
                }
            }
        }
    }    
    state.notPresentCounter = 0    
}

def deviceHealthCheck() {
    state.notPresentCounter = (state.notPresentCounter ?: 0) + 1
    if (state.notPresentCounter > presenceCountTreshold) {
        if ((device.currentValue("healthStatus", true) ?: "unknown") != "offline" ) {
            sendHealthStatusEvent("offline")
            if (settings?.txtEnable) log.warn "${device.displayName} is not present!"
 	        powerSourceEvent("unknown")
            if (!(device.currentValue('motion', true) in ['inactive', '?'])) {
                handleMotion(false, isDigital=true)
                if (settings?.txtEnable) log.warn "${device.displayName} forced motion to '<b>inactive</b>"
            }
            if (safeToInt(device.currentValue('battery', true)) != 0) {
                if (settings?.txtEnable) log.warn "${device.displayName} forced battery to '<b>0 %</b>"
                sendBatteryEvent( 0, isDigital=true )
            }
        }
    }
    else {
        if (logEnable) log.debug "${device.displayName} deviceHealthCheck - online (notPresentCounter=${state.notPresentCounter})"
    }
    
}

def sendHealthStatusEvent(value) {
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}


String mapToJsonString( Map map) {
    if (map==null || map==[:]) return ""
    String str = JsonOutput.toJson(map)
    return str
}

Map stringToJsonMap( String str) {
    if (str==null) return [:]
    def jsonSlurper = new JsonSlurper()
    def map = jsonSlurper.parseText( str )
    return map
}

Integer safeToInt(val, Integer defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

Double safeToDouble(val, Double defaultVal=0.0) {
	return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

def logDebug(msg) {
    if (settings?.logEnable) {
        log.debug "${device.displayName} " + msg
    }
}

def logInfo(msg) {
    if (settings?.txtEnable) {
        log.info "${device.displayName} " + msg
    }
}

def logWarn(msg) {
    if (settings?.logEnable) {
        log.warn "${device.displayName} " + msg
    }
}

def updateInfo(msg= ' ') {
    sendEvent(name: "_info" , value: msg, isStateChange: false)
}

def zTest( dpCommand, dpValue, dpTypeString ) {
    ArrayList<String> cmds = []
    def dpType   = dpTypeString=="DP_TYPE_VALUE" ? DP_TYPE_VALUE : dpTypeString=="DP_TYPE_BOOL" ? DP_TYPE_BOOL : dpTypeString=="DP_TYPE_ENUM" ? DP_TYPE_ENUM : null
    def dpValHex = dpTypeString=="DP_TYPE_VALUE" ? zigbee.convertToHexString(dpValue as int, 8) : dpValue

    if (settings?.logEnable) log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}"

    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}

def test( String description) {
    log.warn "parising : ${description}"
    parse( description)
}
