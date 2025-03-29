/* groovylint-disable CompileStatic, ConsecutiveStringConcatenation, DuplicateMapLiteral, ImplementationAsType, LineLength, MethodParameterTypeRequired, MethodReturnTypeRequired, MethodSize, NoDef, NoDouble, SpaceAfterClosingBrace, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnusedImport, UnusedPrivateMethod, VariableName, VariableTypeRequired */
/**
 *  Tuya Temperature Humidity Illuminance LCD Display with a Clock
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *    in compliance with the License. You may obtain a copy of the License at:
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *    on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *    for the specific language governing permissions and limitations under the License.
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
 * ver. 1.3.1  2023-02-10 kkossev - added RH3052 TUYATEC-gqhxixyk
 * ver. 1.3.2  2023-03-04 kkossev - added TS0601 _TZE200_zl1kmjqx _TZE200_qyflbnbj, added TS0201 _TZ3000_dowj6gyi and _TZ3000_8ybe88nf
 * ver. 1.3.3  2023-04-23 kkossev - _TZE200_znbl8dj5 inClusters correction; ignored invalid humidity values; implemented ping() and rtt (round-trip-time) attribute;
 * ver. 1.3.4  2023-04-24 kkossev - send rtt 'timeout' if ping() fails; added resetStats command; added individual stat.stats counters for T/H/I/battery; configuration possible loop bug fix;
 * ver. 1.3.5  2023-05-28 kkossev - sendRttEvent exception fixed; added _TZE200_cirvgep4 in TS0601_Tuya group; fingerprint correction; battery reports are capped to 100% and not ignored;
 * ver. 1.3.6  2023-06-10 kkossev - added _TZE200_yjjdcqsq to TS0601_Tuya group;
 * ver. 1.3.7  2023-08-02 vpjuslin -Yet another name for Tuya soil sensor: _TZE200_ga1maeof
 * ver. 1.3.8  2023-08-17 kkossev - added OWON THS317-ET for tests; added TS0201 _TZ3000_rdhukkmi; added TS0222 _TYZB01_ftdkanlj
 * ver. 1.3.9  2023-09-29 kkossev - added Sonoff SNZB-02P; added TS0201 _TZ3210_ncw88jfq; moved _TZE200_yjjdcqsq and _TZE200_cirvgep4 to a new group 'TS0601_Tuya_2'; added _TZE204_upagmta9, added battery state 'low', 'medium', 'high'
 * ver. 1.3.10 2023-11-28 kkossev - added TS0222 _TYZB01_fi5yftwv; added temperature scale (C/F) and temperature sensitivity setting for TS0601_Tuya_2 group;
 * ver. 1.4.0  2023-11-28 kkossev - bug fix - healthStatus periodic job was not started; _TZ3000_qaaysllp illuminance dp added;
 * ver. 1.5.0  2024-01-27 kkossev - Groovy lint; added TS0601 _TZE200_vvmbj46n to TS0601_Tuya_2 group; _TZE200_qyflbnbj fingerprint correction; added TS0201 _TZ3000_utwgoauk
 * ver. 1.5.1  2024-02-13 kkossev - bugfix: battery reporting period for non-Tuya devices.
 * ver. 1.5.2  2024-05-14 kkossev - added _TZE204_upagmta9 and _TZE200_upagmta9 to TS0601_Tuya_2 group; healthStatus initialized as 'unknown';
 * ver. 1.6.0  2024-05-19 kkossev - added the correct NOUS TS0601 _TZE200_nnrfa68v fingerprint to group 'TS0601_Tuya'; all Current States and Preferences are cleared on initialize command;
 * ver. 1.6.1  2024-06-10 kkossev - added ThirdReality 3RTHS0224Z and 3RTHS24BZ
 * ver. 1.6.2  2024-06-26 kkossev - added TS000F _TZ3218_7fiyo3kv in DS18B20 group (temperature only); added Tuya cluster command '06' processing; added description in the debug logs
 * ver. 1.6.3  2024-07-16 kkossev - added TS0601 _TZE204_yjjdcqsq to TS0601_Tuya_2 group;
 * ver. 1.6.4  2024-07-23 kkossev - added Tuya Smart Soil Tester _TZE284_aao3yzhs into 'TS0601_Soil_II'
 * ver. 1.6.5  2024-08-09 kkossev - bugfix: TS0201 _TZ3000_dowj6gyi moved back to TS0201 group;
 * ver. 1.6.6  2024-08-14 kkossev - added TS0601 _TZE204_myd45weu; added TS0601 _TZE204_qyflbnbj
 * ver. 1.6.7  2024-10-23 kkossev - added TS0222 _TZ3000_kky16aay in a new 'TS0222_Soil' group
 * ver. 1.6.8  2024-11-19 kkossev - added TS0601 _TZE284_sgabhwa6 and _TZE284_nhgdf6qr into 'TS0601_Soil_II'; added _TZE200_qrztc3ev _TZE200_snloy4rw _TZE200_eanjj2pa _TZE200_ydrdfkim into 'TS0601_Tuya' group
 * ver. 1.7.0  2024-11-23 kkossev - temperatureOffset and humidityOffset moved outside of the configParams; added queryAllTuyaDPs() on Refresh
 * ver. 1.8.0  2024-12-30 kkossev - HE platform 2.4.0.x compatibility patch
 * ver. 1.8.1  2025-02-22 kkossev - added TS000F _TZ3218_ya5d6wth in DS18B20 group (temperature only); added TS0201 _TZ3000_3xduwekl; added Temperature Unit Preference for 'TS0201_TH' group
 * ver. 1.8.2  2025-03-03 kkossev - added TS0601 _TZE204_s139roas - Ink display T/H sensor!
 * ver. 1.8.3  2025-03-29 kkossev - (dev. branch) TS0201 _TZ3210_ncw88jfq change C/F scale @kuzenkohome 
 *
 *                                  TODO: update documentation : 
*/

@Field static final String VERSION = '1.8.3'
@Field static final String TIME_STAMP = '2025/03/29 1:53 PM'

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol

@Field static final Boolean _DEBUG = false

metadata {
    definition(name: 'Tuya Temperature Humidity Illuminance LCD Display with a Clock', namespace: 'kkossev', author: 'Krassimir Kossev', importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Temperature%20Humidity%20Illuminance%20LCD%20Display%20with%20a%20Clock/Tuya_Temperature_Humidity_Illuminance_LCD_Display_with_a_Clock.groovy', singleThreaded: true ) {
        capability 'Refresh'
        capability 'Sensor'
        capability 'Battery'
        capability 'TemperatureMeasurement'
        capability 'RelativeHumidityMeasurement'
        capability 'IlluminanceMeasurement'
        //capability "ContactSensor"   // uncomment for _TZE200_pay2byax contact w/ illuminance sensor
        //capability "MotionSensor"    // uncomment for SiHAS multi sensor
        capability 'Health Check'

        if (_DEBUG == true) {
            command 'zTest', [
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']],
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']],
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type']
            ]
            command 'test', [[name:'test', type: 'STRING', description: 'test', constraints: ['STRING']]]
        }

        command 'resetStats', [[name: 'Reset statistics' ]]
        command 'initialize', [[name: 'Manually initialize the device after switching drivers. ***** Will load device default values! *****' ]]

        attribute   '_info', 'string'        // when defined as attribute, will be shown on top of the 'Current States' list ...
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'rtt', 'number'

        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_lve3dvpy', deviceJoinName: 'Tuya Temperature Humidity Illuminance LCD Display with a Clock'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_c7emyjom', deviceJoinName: 'Tuya Temperature Humidity Illuminance LCD Display with a Clock'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_locansqn', deviceJoinName: 'Haozee Temperature Humidity Illuminance LCD Display with a Clock' // https://de.aliexpress.com/item/1005003634353180.html
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_bq5c8xfe', deviceJoinName: 'Haozee Temperature Humidity Illuminance LCD Display with a Clock'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_nnrfa68v', deviceJoinName: 'NOUS Temperature Humidity Illuminance LCD Display with a Clock'   // https://community.hubitat.com/t/nous-humidity-and-temp-sensor/137764?u=kkossev
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0402,0405', outClusters:'0019',      model:'TS0201', manufacturer:'_TZ2000_hjsgdkfl', deviceJoinName: 'AVATTO S-H02'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0402,0405', outClusters:'0019',      model:'TS0201', manufacturer:'_TZ2000_a476raq2', deviceJoinName: 'Tuya Temperature Humidity LCD display'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0400,E002', outClusters:'0019,000A', model:'TS0201', manufacturer:'_TZ3000_qaaysllp', deviceJoinName: 'NAS-TH02B LCZ030 T/H/I/LCD'  // Neo Coolcam ?  // NOT TESTED!
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0400',      outClusters:'0019,000A', model:'TS0222', manufacturer:'_TYZB01_kvwjujy9', deviceJoinName: 'MOES ZSS-ZK-THL'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0400',      outClusters:'0019,000A', model:'TS0222', manufacturer:'_TYZB01_ftdkanlj', deviceJoinName: 'MOES ZSS-ZK-THL'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0400,0001,0500', outClusters:'0019,000A', model:'TS0222', manufacturer:'_TYZB01_4mdqxxnn', deviceJoinName: 'Tuya Illuminance Sensor TS0222_2'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0400,0001,0500', outClusters:'0019,000A', model:'TS0222', manufacturer:'_TZ3000_lfa05ajd', deviceJoinName: 'Zemismart ZXZTH'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0402,0405,0400,0000', outClusters:'0019,000A', model:'TS0222', manufacturer:'_TZ3000_kky16aay', deviceJoinName: 'Soil Temperature and Humidity Meter QT-075/THE01860'  // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/532?u=kkossev
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_pisltm67', deviceJoinName: 'AUBESS Light Sensor S-LUX-ZB'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TYST11_pisltm67', deviceJoinName: 'AUBESS Light Sensor S-LUX-ZB'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0500,0000',      outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_pay2byax', deviceJoinName: 'Tuya Contact and Illuminance Sensor'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0402,0405,E002,0000', outClusters:'0003,0019,000A', model:'TS0201', manufacturer:'_TZ3000_itnrsufe', deviceJoinName: 'Tuya temperature and humidity sensor RCTW1Z'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0402,0405,E002,0000', outClusters:'0003,0019,000A', model:'TS0201', manufacturer:'_TZ3000_ywagc4rj', deviceJoinName: 'Tuya temperature and humidity sensor'       // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock/88093/211?u=kkossev
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0402,0405,EF00,0000',      outClusters:'0019,000A',      model:'TS0201', manufacturer:'_TZ3210_ncw88jfq', deviceJoinName: 'Tuya temperature and humidity sensor'       // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock/88093/211?u=kkossev
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0402,0405,EF00,0000',      outClusters:'0019,000A',      model:'TS0201', manufacturer:'_TZ3000_3xduwekl', deviceJoinName: 'Tuya temperature and humidity sensor'       // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0004,0005,0402,0405,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_bjawzodf', deviceJoinName: 'Tuya like Temperature Humidity LCD Display' // https://de.aliexpress.com/item/4000739457722.html?gatewayAdapt=glo2deu
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_bjawzodf', deviceJoinName: 'Tuya like Temperature Humidity LCD Display'                // https://de.aliexpress.com/item/4000739457722.html?gatewayAdapt=glo2deu
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_zl1kmjqx', deviceJoinName: 'Tuya Temperature Humidity sensor MIR-TE100-TY'            // https://www.aliexpress.com/item/1005002836127648.html
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_qyflbnbj', deviceJoinName: 'Tuya Temperature Humidity sensor MIR-TE100-TY'            // https://www.aliexpress.com/item/1005002836127648.html
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_qyflbnbj', deviceJoinName: 'Tuya Temperature Humidity sensor MIR-TE100-TY'            // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/522?u=kkossev
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_zppcgbdj', deviceJoinName: 'Tuya Temperature Humidity sensor'

        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_myd45weu', deviceJoinName: 'Tuya Temperature Humidity Soil Monitoring Sensor'          //
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_myd45weu', deviceJoinName: 'Tuya Temperature Humidity Soil Monitoring Sensor'          // https://www.aliexpress.com/item/1005004979025740.html
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ga1maeof', deviceJoinName: 'Tuya Temperature Humidity Soil Monitoring Sensor'          // https://www.aliexpress.com/item/1005004979025740.html
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000,ED00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE284_aao3yzhs", controllerType: "ZGB",  deviceJoinName: 'Tuya Temperature Humidity Soil Monitoring Sensor II'
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000,ED00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE284_sgabhwa6", controllerType: "ZGB",  deviceJoinName: 'Tuya Temperature Humidity Soil Monitoring Sensor II'   // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/538?u=kkossev
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000,ED00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE284_nhgdf6qr", controllerType: "ZGB",  deviceJoinName: 'Tuya Temperature Humidity Soil Monitoring Sensor II'   // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/538?u=kkossev

        // model: 'ZG-227ZL',
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0004,0005,0402,0405,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_qoy0ekbd', deviceJoinName: 'Tuya Temperature Humidity LCD Display'      // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0004,0005,0402,0405,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_a8sdabtg', deviceJoinName: 'Tuya Temperature Humidity (no screen)'      // https://community.hubitat.com/t/new-temp-humidity-device-not-working-correctly-generic-zigbee-th-driver/109725?u=kkossev
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0402,0405,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_znbl8dj5', deviceJoinName: 'Tuya Temperature Humidity'                                 // kk
        //
        // requre more Tuya Magic -  queryOnDeviceAnnounce !!! https://github.com/Koenkk/zigbee-herdsman-converters/blob/c7e670672eb9c584429fe5462eb835c0ae9b0da0/src/lib/tuya.ts#L45
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_yjjdcqsq', deviceJoinName: 'Tuya Temperature Humidity'                                 // kk
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_yjjdcqsq', deviceJoinName: 'Tuya Temperature Humidity'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_9yapgbuv', deviceJoinName: 'Tuya Temperature Humidity'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_9yapgbuv', deviceJoinName: 'Tuya Temperature Humidity'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_upagmta9', deviceJoinName: 'Tuya Temperature Humidity'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_upagmta9', deviceJoinName: 'Tuya Temperature Humidity'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_cirvgep4', deviceJoinName: 'Tuya Temperature Humidity LCD Display with a Clock' //https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/308?u=kkossev
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ysm4dsb1', deviceJoinName: 'Tuya Temperature Humidity'                                  // not tested ! https://github.com/Koenkk/zigbee2mqtt/issues/20815
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE0204_yjjdcqsq', deviceJoinName: 'Tuya Temperature Humidity'                                  // not tested !https://github.com/Koenkk/zigbee2mqtt/issues/20235
        // kk
        //
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_whkgqxse', deviceJoinName: 'Tuya Zigbee Temperature Humidity Sensor With Backlight'    // https://www.aliexpress.com/item/1005003980647546.html
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0402,0405,0000', outClusters:'0003,0019,000A', model:'TS0201', manufacturer:'_TZ3000_bguser20', deviceJoinName: 'Tuya Temperature Humidity sensor WSD500A'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0402,0405,0000', outClusters:'0003,0019,000A', model:'TS0201', manufacturer:'_TZ3000_xr3htd96', deviceJoinName: 'Tuya Temperature Humidity sensor WSD500A'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0402,0405,0000', outClusters:'0003,0019,000A', model:'TS0201', manufacturer:'_TZ3000_fllyghyj', deviceJoinName: 'Tuya Temperature Humidity sensor'                // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0402,0405,0000', outClusters:'0003,0019,000A', model:'TS0201', manufacturer:'_TZ3000_yd2e749y', deviceJoinName: 'Tuya Temperature Humidity sensor'                // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0402,0405,0000', outClusters:'0003,0019,000A', model:'TS0201', manufacturer:'_TZ3000_6uzkisv2', deviceJoinName: 'Tuya Temperature Humidity sensor'                // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0402,0405,0000', outClusters:'0003,0019,000A', model:'TS0201', manufacturer:'_TZ3000_dowj6gyi', deviceJoinName: 'Tuya Temperature Humidity sensor'                // https://community.hubitat.com/t/tuya-humidity-temperature-sensor/76635/79?u=kkossev
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0402,0405,0000', outClusters:'0003,0019,000A', model:'TS0201', manufacturer:'_TZ3000_8ybe88nf', deviceJoinName: 'Tuya Temperature Humidity sensor'                // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/262?u=kkossev
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0402,0405,0000', outClusters:'0003,0019,000A', model:'TS0201', manufacturer:'_TZ3000_rdhukkmi', deviceJoinName: 'Tuya Temperature Humidity sensor'                // https://community.hubitat.com/t/tuya-humidity-temperature-sensor/76635/55?u=kkossev
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0402,0405,0000', outClusters:'0003,0019,000A', model:'TS0201', manufacturer:'_TZ3000_utwgoauk', deviceJoinName: 'Tuya Temperature Humidity sensor'                // https://community.hubitat.com/t/humidity-sensor-usb-powered-zigbee/127569/14?u=kkossev
        //
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0402,0405', outClusters:'0003,0402,0405', model:'RH3052', manufacturer:'TUYATEC-gqhxixyk', deviceJoinName: 'TUYATEC RH3052 Motion Sensor'                    // https://community.hubitat.com/t/moes-zigbee-3-0-temp-humidity-sensor-driver/112318?u=kkossev
        //
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0003,0402,0405,0001', outClusters:'0003', model:'TH01', manufacturer:'eWeLink', deviceJoinName: 'Sonoff Temperature and Humidity Sensor SNZB-02'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0003,0402,0405,0001', outClusters:'0003', model:'TH01', manufacturer:'SONOFF', deviceJoinName: 'Sonoff Temperature and Humidity Sensor SNZB-02'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,0402,0405,FC57,FC11', outClusters:'0019', model:'SNZB-02D', manufacturer:'SONOFF', deviceJoinName: 'Sonoff Temperature and Humidity Sensor SNZB-02D'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,0402,0405,FC57,FC11', outClusters:'0019', model:'SNZB-02D', manufacturer:'eWeLink', deviceJoinName: 'Sonoff Temperature and Humidity Sensor SNZB-02D'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0020,0000,0003,0402', outClusters:'0003', model:'THS317-ET', manufacturer:'OWON', deviceJoinName: 'OWON Temperature sensor'                                             // https://community.hubitat.com/t/newbie-help-with-owon-ths317-et-multi-sensor/122671/5?u=kkossev
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,0402,0405,FC57,FC11', outClusters:'0019', model:'SNZB-02P', manufacturer:'eWeLink', deviceJoinName: 'Sonoff Temperature and Humidity Sensor SNZB-02P'    // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/435?u=kkossev
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,0402,0405,FC57,FC11', outClusters:'0019', model:'SNZB-02P', manufacturer:'SONOFF', deviceJoinName: 'Sonoff Temperature and Humidity Sensor SNZB-02P'
        //
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0400,0402,0405,0B05,FCC0', outClusters:'0019', model:'TS0222', manufacturer:'_TYZB01_fi5yftwv', deviceJoinName: 'Konke THI Sensor KK-ES-J01W'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_vvmbj46n', deviceJoinName: 'Tuya Zigbee Temperature Humidity Sensor With Backlight'     // TODO - configuration options!  +onEventSetTime // https://github.com/Koenkk/zigbee2mqtt/issues/19731
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,0402,0405', outClusters:'0019', model:'3RTHS0224Z', manufacturer:'Third Reality', controllerType: 'ZGB', deviceJoinName: 'ThidReality Temperature Humidity Sensor'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0402,0405', outClusters:'0019', model:'3RTHS24BZ', manufacturer:'Third Reality, Inc', controllerType: 'ZGB', deviceJoinName: 'ThidReality Temperature Humidity Sensor'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,E001,E000,EF00', outClusters:'000A,0019', model:'TS000F', manufacturer:'_TZ3218_7fiyo3kv', deviceJoinName: 'MHCOZY switch with temp sensor'         // https://community.hubitat.com/t/mycozy-switch-with-temp-sensor-driver/139715?u=kkossev
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,E001,E000,EF00', outClusters:'000A,0019', model:'TS000F', manufacturer:'_TZ3218_ya5d6wth', deviceJoinName: 'MHCOZY switch with temp sensor'         // https://community.hubitat.com/t/mhcozy-switch-with-temp-sensor-driver/139715/31?u=kkossev
        //
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_qrztc3ev', deviceJoinName: 'Girier Temperature Humidity Illuminance LCD Display with a Clock'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_snloy4rw', deviceJoinName: 'Tuya Temperature Humidity Illuminance LCD Display with a Clock'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_eanjj2pa', deviceJoinName: 'Tuya Temperature Humidity Illuminance LCD Display with a Clock'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_ydrdfkim', deviceJoinName: 'Tuya Temperature Humidity Illuminance LCD Display with a Clock'
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_s139roas", deviceJoinName: 'AVATTO ZWSH16 TH Sensor Ink Display'
    }
    preferences {
        input(name: 'txtEnable', type: 'bool', title: '<b>Description text logging</b>', description: 'Display measured values in HE log page. <br>The recommended setting is <b>enabled</b>.', defaultValue: true)
        input(name: 'logEnable', type: 'bool', title: '<b>Debug logging</b>', description: 'Debug information, useful for troubleshooting. <br>The recommended value is <b>disabled</b>.', defaultValue: true)
        input(name: 'temperatureOffset', type: 'decimal', title: '<b>Temperature offset</b>', description: 'Select how many degrees to adjust the temperature.', defaultValue: 0.0, range: '-100.0..100.0')
        input(name: 'humidityOffset', type: 'decimal', title: '<b>Humidity offset</b>', description: 'Enter a percentage to adjust the humidity.', defaultValue: 0.0, range: '-100.0..100.0')
        input(name: 'modelGroupPreference', type: 'enum', title: '<b>Model Group</b>', description:'The recommended setting is <b>Auto detect</b>.', defaultValue: 0, options:
               ['Auto detect':'Auto detect', 'TS0601_Tuya':'TS0601_Tuya', 'TS0601_Tuya_2':'TS0601_Tuya_2', 'TS0601_Haozee':'TS0601_Haozee', 'TS0601_AUBESS':'TS0601_AUBESS', 'TS0601_AVATTO_Ink':'TS0601_AVATTO_Ink', 'TS0201':'TS0201', 'TS0222':'TS0222', 'TS0201_LCZ030': 'TS0201_LCZ030',
                'TS0222_2':'TS0222_2', 'TS0222_Soil':'TS0222_Soil', 'TS0201_TH':'TS0201_TH', 'TS0601_Soil':'TS0601_Soil', , 'TS0601_Soil_II':'TS0601_Soil_II', 'Zigbee NON-Tuya':'Zigbee NON-Tuya', 'OWON':'OWON', 'DS18B20':'DS18B20'])
        input(name: 'advancedOptions', type: 'bool', title: '<b>Advanced options</b>', description: 'May not be supported by all devices!', defaultValue: false)
        if (advancedOptions == true) {
            if (isConfigurableSleepyDevice()) {
                input(name: 'dummy', title: 'To configure a sleepy device, try any of the methods below :', description: '<b> * Rapidly change the temperature or the humidity<br> * Remove the battery for at least 1 minute<br> * Pair the device again to HE</b>', type: 'hidden', element: 'paragraph')
            }
            configParams.each {
                if (it.value.input.limit == null || 'ALL' in it.value.input.limit || getModelGroup() in it.value.input.limit) {
                    input it.value.input
                }
            }
        }
    }
}

@Field static Map configParams = [
        // temperatureOffset and humidityOffset moved outside of the configParams 11/23/2024
        2: [input: [name: 'temperatureSensitivity', type: 'decimal', title: '<b>Temperature Sensitivity</b>', description: 'Temperature change for reporting, ' + '\u00B0' + 'C', defaultValue: 0.5, range: '0.1..5.0',
                   limit:['TS0601_Tuya', 'TS0601_Haozee', 'TS0201_TH', 'Zigbee NON-Tuya', 'TS0601_Tuya_2']]],

        3: [input: [name: 'humiditySensitivity', type: 'number', title: '<b>Humidity Sensitivity</b>', description: 'Humidity change for reporting, %', defaultValue: 5, range: '1..10',
                   limit:['TS0601_Tuya', 'TS0601_Haozee', 'TS0201_TH', 'Zigbee NON-Tuya', 'TS0601_Tuya_2']]],

        4: [input: [name: 'illuminanceSensitivity', type: 'number', title: '<b>Illuminance Sensitivity</b>', description: 'Illuminance change for reporting, %', defaultValue: 12, range: '10..100',                // TS0222 "MOES ZSS-ZK-THL"
                   limit:['TS0222']]],

        5: [input: [name: 'minTempAlarmPar', type: 'decimal', title: '<b>Minimum Temperature Alarm</b>', description: 'Minimum Temperature Alarm, C', defaultValue: 0.0, range: '-20.0..60.0',
                   limit:['TS0601_Tuya', /*'TS0601_Haozee',*/ 'TS0201_LCZ030']]],

        6: [input: [name: 'maxTempAlarmPar', type: 'decimal', title: '<b>Maximum Temperature Alarm</b>', description: 'Maximum Temperature Alarm, C', defaultValue: 39.0, range: '-20.0..60.0',
                   limit:['TS0601_Tuya', /*'TS0601_Haozee',*/ 'TS0201_LCZ030']]],

        7: [input: [name: 'minHumidityAlarmPar', type: 'number', title: '<b>Minimal Humidity Alarm</b>', description: 'Minimum Humidity Alarm, %', defaultValue: 20, range: '0..100',           // 'TS0601_Haozee' only!
                   limit:[/*'TS0601_Haozee',*/ /*'TS0201_LCZ030'*/]]],

        8: [input: [name: 'maxHumidityAlarmPar', type: 'number', title: '<b>Maximum Humidity Alarm</b>', description: 'Maximum Humidity Alarm, %', defaultValue: 60, range: '0..100',            // 'TS0601_Haozee' only!
                   limit:[/*'TS0601_Haozee',*/ /*'TS0201_LCZ030'*/]]],

        9: [input: [name: 'minReportingTimeTemp', type: 'number', title: '<b>Minimum time between temperature reports</b>', description: 'Minimum time between temperature reporting, seconds.', defaultValue: 10, range: '1..3600',
                   limit:['ALL']]],

       10: [input: [name: 'maxReportingTimeTemp', type: 'number', title: '<b>Maximum time between temperature reports</b>', description: 'Maximum time between temperature reporting, seconds.', defaultValue: 3600, range: '10..43200',
                   limit:['TS0601_Haozee', 'TS0201_TH', 'Zigbee NON-Tuya']]],

       11: [input: [name: 'minReportingTimeHumidity', type: 'number', title: '<b>Minimum time between humidity reports</b>', description: 'Minimum time between humidity reporting, seconds.', defaultValue: 10, range: '1..3600',
                   limit:['ALL']]],

       12: [input: [name: 'maxReportingTimeHumidity', type: 'number', title: '<b>Maximum time between humidity reports</b>', description: 'Maximum time between humidity reporting, seconds.', defaultValue: 3600, range: '10..43200',
                   limit:['TS0601_Haozee', 'TS0201_TH', 'Zigbee NON-Tuya']]],

       13: [input: [name: 'alarmTempPar', type: 'enum', title: '<b>Temperature Alarm</b>', description:'Temperature Alarm', defaultValue: 0, options: [0:'Below min temp', 1:'Over max temp', 2:'off'],
                   limit:[/*'TS0201_LCZ030'*/]]],

       14: [input: [name: 'alarmHumidityPar', type: 'enum', title: '<b>Humidity Alarm</b>', description:'Temperature Alarm', defaultValue: 0, options: [0:'Below min hum.', 1:'Over max hum', 2:'off'],
                   limit:[/*'TS0201_LCZ030'*/]]],
       // 'TS0201_TH' : cluster 0xE002, attr 0xE00B: 0-Celsius, 1: Fahrenheit ( 0x30 ENUM)
       15: [input: [name: 'temperatureUnit', type: 'enum', title: '<b>Temperature Unit</b>', description:'Temperature Unit', defaultValue: 0, options: [0:'Celsius', 1:'Fahrenheit'],
                   limit:['TS0201_TH']]]
]

@Field static final Map<String, String> Models = [
    '_TZE200_lve3dvpy'  : 'TS0601_Tuya',         // Tuya Temperature Humidity LCD Display with a Clock
    '_TZE200_c7emyjom'  : 'TS0601_Tuya',         // Tuya Temperature Humidity LCD Display with a Clock
    '_TZE200_whkgqxse'  : 'TS0601_Tuya',         // Tuya Zigbee Temperature Humidity Sensor With Backlight    https://www.aliexpress.com/item/1005003980647546.html
    '_TZE200_a8sdabtg'  : 'TS0601_Tuya',         // Tuya Zigbee Temperature Humidity Sensor - no display!     https://www.amazon.de/gp/product/B09NKCDXT9 - TODO !
    '_TZE200_qoy0ekbd'  : 'TS0601_Tuya',         // https://www.aliexpress.com/item/1005004896603070.html - TODO !
    '_TZE200_znbl8dj5'  : 'TS0601_Tuya',         // https://www.aliexpress.com/item/1005004116638127.html - TODO !
    '_TZE200_zl1kmjqx'  : 'TS0601_Tuya',         // https://www.aliexpress.com/item/1005002836127648.html
    '_TZE200_qyflbnbj'  : 'TS0601_Tuya',         // not tested
    '_TZE200_zppcgbdj'  : 'TS0601_Tuya',         // not tested
    '_TZE204_qyflbnbj'  : 'TS0601_Tuya',         // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/522?u=kkossev
    '_TZE200_nnrfa68v'  : 'TS0601_Tuya',         // NOUS E6 https://community.hubitat.com/t/nous-humidity-and-temp-sensor/137764/7?u=kkossev
    '_TZE200_qrztc3ev'  : 'TS0601_Tuya',         // NOUS
    '_TZE200_snloy4rw'  : 'TS0601_Tuya',         // NOUS
    '_TZE200_eanjj2pa'  : 'TS0601_Tuya',         // NOUS
    '_TZE200_ydrdfkim'  : 'TS0601_Tuya',         // NOUS

    '_TZE200_cirvgep4'  : 'TS0601_Tuya_2',       // https://www.aliexpress.com/item/1005005198387789.html
    '_TZE200_yjjdcqsq'  : 'TS0601_Tuya_2',       // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/446?u=kkossev
    '_TZE204_yjjdcqsq'  : 'TS0601_Tuya_2',       // https://community.hubitat.com/t/newbie-help-with-owon-ths317-et-multi-sensor/122671/18?u=kkossev
    '_TZE200_9yapgbuv'  : 'TS0601_Tuya_2',       // not tested
    '_TZE204_9yapgbuv'  : 'TS0601_Tuya_2',       // not tested
    '_TZE200_upagmta9'  : 'TS0601_Tuya_2',       // not tested
    '_TZE204_upagmta9'  : 'TS0601_Tuya_2',       // not tested  // https://github.com/zigpy/zha-device-handlers/issues/2694
    '_TZE200_vvmbj46n'  : 'TS0601_Tuya_2',       // https://community.hubitat.com/t/looking-for-a-zigbee-temperature-humidity-illumination-sensor-with-display/130896/14?u=kkossev
    '_TZE204_vvmbj46n'  : 'TS0601_Tuya_2',       //

    '_TZE200_locansqn'  : 'TS0601_Haozee',       // Haozee Temperature Humidity Illuminance LCD Display with a Clock
    '_TZE200_bq5c8xfe'  : 'TS0601_Haozee',       //

    '_TZE200_pisltm67'  : 'TS0601_AUBESS',       // illuminance only sensor

    '_TZE204_s139roas'  : 'TS0601_AVATTO_Ink',   // AVATTO Ink Display   https://github.com/Koenkk/zigbee-herdsman-converters/blob/a32df6625f31f9e2d9cc6305971b6f5b022cd166/src/devices/avatto.ts#L10-L31

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
    '_TZ3000_dowj6gyi'  : 'TS0201',             // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/512?u=kkossev
    '_TZ3000_8ybe88nf'  : 'TS0201',
    '_TZ3000_rdhukkmi'  : 'TS0201',
    '_TZ3000_utwgoauk'  : 'TS0201',
    'TUYATEC-gqhxixyk'  : 'TS0201',             // model RH3052
    '_TZ3000_qaaysllp'  : 'TS0201_LCZ030',      // NAS-TH02B  / NEO Coolcam ?  - T/H/I - testing! // https://github.com/Datakg/tuya/blob/53e33ae7767aedbb5d2138f2a31798badffd80d2/zhaquirks/tuya/ts0201_neo.py
    '_TYZB01_kvwjujy9'  : 'TS0222',             // "MOES ZSS-ZK-THL" e-Ink display
    '_TYZB01_ftdkanlj'  : 'TS0222',             // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/419?u=kkossev
    '_TYZB01_fi5yftwv'  : 'TS0222',             // https://community.hubitat.com/t/konke-bond-series-enviroment-sensor/126445?u=kkossev
    '_TYZB01_4mdqxxnn'  : 'TS0222_2',           // illuminance only sensor
    '_TZ3000_kky16aay'  : 'TS0222_Soil',        // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/535?u=kkossev
    '_TZE200_pay2byax'  : 'TS0601_Contact',     // Contact and illuminance sensor
    '_TZ3000_itnrsufe'  : 'TS0201_TH',          // Temperature and humidity sensor; // reports both battery voltage and perceintage; cluster 0xE002, attr 0xE00B: 0-Celsius, 1: Fahrenheit ( 0x30 ENUM)
    '_TZ3000_ywagc4rj'  : 'TS0201_TH',          // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock/88093/210?u=kkossev
    '_TZ3210_ncw88jfq'  : 'TS0201_TH',          // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/436?u=kkossev
    '_TZ3000_3xduwekl'  : 'TS0201_TH',          // not tested
    '_TZE200_myd45weu'  : 'TS0601_Soil',        // Soil monitoring sensor
    '_TZE204_myd45weu'  : 'TS0601_Soil',        // https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/519?u=kkossev
    '_TZE200_ga1maeof'  : 'TS0601_Soil',        // Soil monitoring sensor
    '_TZE284_aao3yzhs'  : 'TS0601_Soil_II',     // Soil monitoring sensor II
    '_TZE284_sgabhwa6'  : 'TS0601_Soil_II',     // Soil monitoring sensor II
    '_TZE284_nhgdf6qr'  : 'TS0601_Soil_II',     // Soil monitoring sensor II
    'eWeLink'           : 'Zigbee NON-Tuya',    // Sonoff Temperature and Humidity Sensor SNZB-02, SNZB-02D, SNZB-02P
    'SONOFF'            : 'Zigbee NON-Tuya',    // Sonoff Temperature and Humidity Sensor SNZB-02, SNZB-02D, SNZB-02P
    'ShinaSystem'       : 'Zigbee NON-Tuya',    // USM-300Z
    'Third Reality'     : 'Zigbee NON-Tuya',    //
    'Third Reality, Inc': 'Zigbee NON-Tuya',    //
    'OWON'              : 'OWON',               // model:"THS317-ET", manufacturer:"OWON"
    '_TZ3218_7fiyo3kv'  : 'DS18B20',            // MHCOZY switch with temp sensor
    '_TZ3218_ya5d6wth'  : 'DS18B20',            // MHCOZY switch with temp sensor
    ''                  : 'UNKNOWN',
    'ALL'               : 'ALL',
    'TEST'              : 'TEST'

]

@Field static final Map deviceProfilesV3 = [
    'SONOFF_TEMP_HUMI'  : [
            models        : ['TH01', 'SNZB-02D', 'SNZB-02P'],
            manufacturers : ['eWeLink',  'SONOFF'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0402,0405,0001', outClusters:'0003', model:'TH01', manufacturer:'eWeLink', deviceJoinName: 'Sonoff Temperature and Humidity Sensor SNZB-02'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0402,0405,0001', outClusters:'0003', model:'TH01', manufacturer:'SONOFF', deviceJoinName: 'Sonoff Temperature and Humidity Sensor SNZB-02'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,0402,0405,FC57,FC11', outClusters:'0019', model:'SNZB-02D', manufacturer:'eWeLink', deviceJoinName: 'Sonoff Temperature and Humidity Sensor SNZB-02D'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,0402,0405,FC57,FC11', outClusters:'0019', model:'SNZB-02D', manufacturer:'SONOFF', deviceJoinName: 'Sonoff Temperature and Humidity Sensor SNZB-02D'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,0402,0405,FC57,FC11', outClusters:'0019', model:'SNZB-02P', manufacturer:'eWeLink', deviceJoinName: 'Sonoff Temperature and Humidity Sensor SNZB-02P'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,0402,0405,FC57,FC11', outClusters:'0019', model:'SNZB-02P', manufacturer:'SONOFF', deviceJoinName: 'Sonoff Temperature and Humidity Sensor SNZB-02P']
            ],
            deviceJoinName: 'Sonoff Temperature and Humidity Sensor',
            capabilities  : ['battery': true],
            attributes    : ['healthStatus': 'unknown', 'powerSource': 'battery'],
            configuration : ['battery': false],
            preferences   : [
                'powerOnBehaviour' : [ name: 'powerOnBehaviour', type: 'enum', title: '<b>Power-On Behaviour</b>', description:'Select Power-On Behaviour', defaultValue: '2', options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ]
]

def isConfigurableSleepyDevice()  { getModelGroup() in ['Zigbee NON-Tuya', 'TS0201_TH'] }

@Field static final Integer MaxRetries = 3
@Field static final Integer ConfigTimer = 15
@Field static final Integer presenceCountTreshold = 4
@Field static final Integer defaultMinReportingTime = 10
@Field static final Integer REFRESH_TIMER = 3000
@Field static final Integer COMMAND_TIMEOUT = 10
@Field static final Integer MAX_PING_MILISECONDS = 10000    // rtt more than 10 seconds will be ignored
@Field static String UNKNOWN = 'UNKNOWN'

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
private getDP_TYPE_RAW()        { '01' }    // [ bytes ]
private getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ]
private getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ]
private getDP_TYPE_STRING()     { '03' }    // [ N byte string ]
private getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ]
private getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits

// Parse incoming device messages to generate events
def parse(String description) {
    checkDriverVersion()
    setPresent()
    Map statsMap = stringToJsonMap(state.stats); try { statsMap['rxCtr']++ } catch (e) { statsMap['rxCtr'] = 1 }; state.stats = mapToJsonString(statsMap)
    if (settings?.logEnable) { log.debug "${device.displayName} parse() descMap =${zigbee.parseDescriptionAsMap(description)} description = ${description}" }
    if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
            if (descMap.attrInt == 0x0021) {
                getBatteryPercentageResult(Integer.parseInt(descMap.value, 16))
            } else if (descMap.attrInt == 0x0020) {
                //log.trace "descMap.attrInt == 0x0020"
                getBatteryVoltageResult(Integer.parseInt(descMap.value, 16))
            }
            else {
                log.warn "unparesed attrint $descMap.attrInt"
            }
        }
        else if (descMap.cluster == '0400' && descMap.attrId == '0000') {
            def rawLux = Integer.parseInt(descMap.value, 16)
            if (device.getDataValue('manufacturer') in ['ShinaSystem']) {
                illuminanceEventLux( rawLux )
            }
            else {
                illuminanceEvent( rawLux )
            }
            if (getModelGroup() == 'TS0222') {
                pollTS0222()
            }
        }
        else if (descMap.cluster == '0400' && descMap.attrId == 'F001') {        //MOES ZSS-ZK-THL, also TS0201 Neo Coolcam!
            def raw = Integer.parseInt(descMap.value, 16)
            if (settings?.txtEnable) { log.info "${device.displayName} illuminance sensitivity is ${raw} Lux" }
            device.updateSetting('illuminanceSensitivity', [value:raw, type:'number'])
        }
        else if (descMap.cluster == '0402' && descMap.attrId == '0000') {
            if (getModelGroup() != 'TS0222_2') {
                def raw = Integer.parseInt(descMap.value, 16)
                if (raw > 32767) {
                    //Here we deal with negative values
                    raw = raw - 65536
                }
                temperatureEvent( raw / 100.0 )
            }
            else {
                if (settings?.logEnable) { log.warn "${device.displayName} Ignoring ${getModelGroup()} temperature event" }
            }
        }
        else if (descMap.cluster == '0405' && descMap.attrId == '0000') {
            def raw = Integer.parseInt(descMap.value, 16)
            if (!(getModelGroup() in ['TS0201_TH', 'TS0222_Soil'])) {
                humidityEvent( raw / 100.0 )
            }
            else {
                humidityEvent( raw / 10.0 )    // also _TZE200_bjawzodf, _TZE200_zl1kmjqx ?
            }
        }
        else if (descMap.cluster == '0406' && descMap.attrId == '0000') {    // OWON, SiHAS
            def raw = Integer.parseInt(descMap.value, 16)
            motionEvent( raw & 0x01 )
        }
        else if (descMap.cluster == '0000' && descMap.attrId == '0001') {    // ping
            // descMap = [raw:0D310100000A01002004, dni:0D31, endpoint:01, cluster:0000, size:0A, attrId:0001, encoding:20, command:01, value:04, clusterInt:0, attrInt:1]
            logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap?.value})"
            def now = new Date().getTime()
            Map lastTxMap = stringToJsonMap(state.lastTx)
            def timeRunning = now.toInteger() - (lastTxMap.pingTime ?: '0').toInteger()
            if (timeRunning < MAX_PING_MILISECONDS && timeRunning > 0) {
                sendRttEvent()
            }
            unschedule('deviceCommandTimeout')
        }
        else if (descMap?.clusterInt == CLUSTER_TUYA) {
            processTuyaCluster( descMap )
        }
        else if (descMap?.clusterId == '0013') {    // device announcement, profileId:0000
            logInfo 'device announcement'
            try { statsMap['rejoins']++ } catch (e) { statsMap['rejoins'] = 1 }; state.stats = mapToJsonString(statsMap)
            if (getModelGroup() == 'TS0222') {
                configure()
            }
        }
        else if (descMap.isClusterSpecific == false && descMap.command == '01' ) { //global commands read attribute response
            def status = descMap.data[2]
            if (status == '86') {
                if (settings?.logEnable) { log.warn "${device.displayName} Cluster ${descMap.clusterId} read attribute - NOT SUPPORTED!\r ${descMap}" }
            }
            else {
                if (settings?.logEnable) { log.warn "${device.displayName} <b>UNPROCESSED Global Command</b> :  ${descMap}" }
            }
        }
        else if (descMap.profileId == '0000') { //zdo
            parseZDOcommand(descMap)
        }
        else if (descMap.clusterId != null && descMap.profileId == '0104') { // ZHA global command
            parseZHAcommand(descMap)
        }
        else {
            if (settings?.logEnable) { log.debug "${device.displayName} <b> NOT PARSED </b> :  ${descMap}" }
        }
    } // if 'catchall:' or 'read attr -'
    else {
        if (settings?.logEnable) { log.debug "${device.displayName} <b> UNPROCESSED </b> parse() descMap = ${zigbee.parseDescriptionAsMap(description)}" }
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
        case '01' : //read attribute response. If there was no error, the successful attribute reading would be processed in the main parse() method.
            def status = descMap.data[2]
            def attrId = descMap.data[1] + descMap.data[0]
            if (status == '86') {
                if (logEnable) { log.warn "${device.displayName} Read attribute response: unsupported Attributte ${attrId} cluster ${clusterId}" }
            }
            else {
                if (logEnable) { log.debug "${device.displayName} Read attribute response: status code ${status} Attributte ${attrId} cluster ${descMap.clusterId}" }
            }
            break
        case '04' : //write attribute response
            if (logEnable) { log.info "${device.displayName} Received Write Attribute Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" }
            break
        case '07' : // Configure Reporting Response
            if (logEnable) { log.info "${device.displayName} Received Configure Reporting Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" }
            // Status: Unreportable Attribute (0x8c)
            break
        case '09' : // Command: Read Reporting Configuration Response (0x09)
            def status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00)
            //def attr = zigbee.convertHexToInt(descMap.data[3]) * 256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000)
            if (status == 0) {
                //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10)
                def min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5])
                def max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7])
                def delta = 0
                if (descMap.data.size() == 11) {
                    delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9])
                }
                else if (descMap.data.size() == 10) {
                    delta = zigbee.convertHexToInt(descMap.data[9])
                }
                else {
                    if (logEnable) { log.debug "${device.displayName} descMap.data.size = ${descMap.data.size()}" }
                }
                logDebug "Received Read Reporting Configuration response (0x09) for cluster:${descMap.clusterId} attribite:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}"
                String attributeName
                if (descMap.clusterId == '0405') {
                    attributeName = 'humidity'
                    lastRxMap.humiCfg = min.toString() + ',' + max.toString() + ',' + delta.toString()
                    if (lastRxMap.humiCfg == lastTxMap.humiCfg) {
                        lastTxMap.humiCfgOK = true
                    }
                }
                else if (descMap.clusterId == '0402') {
                    attributeName = 'temperature'
                    lastRxMap.tempCfg = min.toString() + ',' + max.toString() + ',' + delta.toString()
                    if (lastRxMap.tempCfg == lastTxMap.tempCfg) {
                        lastTxMap.tempCfgOK = true
                    }
                }
                else if (descMap.clusterId == '0001') {
                    attributeName = 'battery %'
                }
                else {
                    attributeName = descMap.clusterId
                }
                if (lastTxMap.humiCfgOK == true && lastTxMap.tempCfgOK == true) {
                    logDebug 'both T&H configured!'
                    lastTxMap.cfgFailure = false
                }
                if (txtEnable == true) {
                    log.info "${device.displayName} Reporting Configuration Response for ${attributeName}  (status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) is: min=${min} max=${max} delta=${delta}"
                }
            }
            else {    // failure
                if (logEnable) { log.info "${device.displayName} <b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribite:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" }
            }
            break
        case '0B' : // ZCL Default Response
            def status = descMap.data[1]
            if (status != '00') {
                if (logEnable) { log.info "${device.displayName} Received ZCL Default Response to Command ${descMap.data[0]} for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" }
            }
            break
        default :
            if (logEnable) { log.warn "${device.displayName} Unprocessed global command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" }
            break
    }
    state.lastRx = mapToJsonString(lastRxMap)
    state.lastTx = mapToJsonString(lastTxMap)
}

def parseZDOcommand( Map descMap ) {
    switch (descMap.clusterId) {
        case '0006' :
            if (logEnable) { log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" }
            break
        case '0013' : // device announcement
            if (logEnable) { log.info "${device.displayName} Received device announcement, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" }
            break
        case '8004' : // simple descriptor response
            if (logEnable) { log.info "${device.displayName} Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" }
            parseSimpleDescriptorResponse( descMap )
            break
        case '8005' : // endpoint response
            if (logEnable) { log.info "${device.displayName} Received endpoint response: cluster: ${descMap.clusterId} (endpoint response) endpointCount = ${ descMap.data[4]}  endpointList = ${descMap.data[5]}" }
            break
        case '8021' : // bind response
            if (logEnable) { log.info "${device.displayName} Received bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" }
            break
        case '8022' : // unbind response
            if (logEnable) { log.info "${device.displayName} Received unbind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" }
            break
        case '8034' : // leave response
            if (logEnable) { log.info "${device.displayName} Received leave response, data=${descMap.data}" }
            break
        case '8038' : // Management Network Update Notify
            if (logEnable) { log.info "${device.displayName} Received Management Network Update Notify, data=${descMap.data}" }
            break
        default :
            if (logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" }
            break    // 2022/09/16
    }
}

def processTuyaCluster( descMap ) {
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME
        if (settings?.logEnable) { log.debug "${device.displayName} time synchronization request from device, descMap = ${descMap}" }
        def offset = 0
        try {
            offset = location.getTimeZone().getOffset(new Date().getTime())
        //if (settings?.logEnable) { log.debug "${device.displayName} timezone offset of current location is ${offset}" }
        }
        catch (e) {
            if (settings?.logEnable) { log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" }
        }
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8))
        // TODO : send raw command without 'need confirmation' frame control !
        //if (settings?.logEnable) { log.trace "${device.displayName} now is: ${now()}" }  // KK TODO - convert to Date/Time string!
        if (settings?.logEnable) { log.debug "${device.displayName} sending time data : ${cmds}" }
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response
        String clusterCmd = descMap?.data[0]
        def status = descMap?.data[1]
        logDebug "Tuya cluster confirmation for command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
        if (status != '00') {
            if (settings?.logEnable) { log.warn "${device.displayName} ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} group = ${getModelGroup()} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" }
        }
    }
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) {   // added command 06 - 06/26/2024; added command 05 03/29/2025
        def dataLen = descMap?.data.size()
        //log.warn "dataLen=${dataLen}"
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
        for (int i = 0; i < (dataLen - 4); ) {
            def dp = zigbee.convertHexToInt(descMap?.data[2 + i])                // "dp" field describes the action/message of a command frame
            def dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])               // "dp_identifier" is device dependant
            def fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i])
            def fncmd = getTuyaAttributeValue(descMap?.data, i)                //
            if (settings?.logEnable) { log.trace "${device.displayName}  dp_id=${dp_id} dp=${dp} fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" }
            processTuyaDP( descMap, dp, dp_id, fncmd)
            i = i + fncmd_len + 4
        }
    }
    else {
        if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed Tuya cluster command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" }
    }
}

def processTuyaDP( descMap, dp, dp_id, fncmdPar) {
    def fncmd = fncmdPar
    switch (dp) {
        case 0x01 : // temperature in C for most models
            if (getModelGroup() == 'TS0601_Contact') {
                def value = fncmd == 0 ? 'closed' : 'open'    // inverted!
                sendEvent('name': 'contact', 'value': value)
                if (settings?.txtEnable) { log.info "${device.displayName} Contact is ${value}" }
            }
            else if (getModelGroup() == 'DS18B20') {
                logInfo "DS18B20 Switch is ${fncmd}"
            }
            else if (getModelGroup() != 'TS0601_AUBESS') { // temperature in C, including 'TS0601_Tuya_2', 'TS0601_AVATTO_Ink' - all Tuya EF00 models !
                if (fncmd > 32767) {
                    //Here we deal with negative values
                    fncmd = fncmd - 65536
                }
                temperatureEvent( fncmd / 10.0 )
            }
            else {
                def lomihi = fncmd == 0 ? 'low' : fncmd == 1 ? 'medium' : fncmd == 2 ? 'high' : 'unknown'
                if (settings?.logEnable) { log.debug "${device.displayName} Tuya illuminance status is: ${lomihi} (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})" }
            }
            break
        case 0x02 : // humidity % for most of the Tuya EF00 models; 'TS0601_Contact'illuminance; 'TS0601_Contact'0 battery %
            if (getModelGroup() == 'TS0601_AUBESS') {
                illuminanceEventLux( safeToInt( fncmd ) )
            }
            else if (getModelGroup() == 'TS0601_Contact') {
                getBatteryPercentageResult(fncmd * 2)
            }
            else {
                if (device.getDataValue('manufacturer') in ['_TZE200_bjawzodf', '_TZE200_zl1kmjqx']) {
                    humidityEvent( (fncmd / 10.0) as int )
                }
                else {
                    humidityEvent( fncmd )        // including 'TS0601_Tuya_2', 'TS0601_AVATTO_Ink' - all Tuya EF00 models !
                }
            }
            break
        case 0x03 : // humidity or  illuminance or battery state
            if (getModelGroup() in ['TS0601_Soil', 'TS0601_Soil_II']) {
                logDebug "Soil Sensor humidity raw = ${fncmd}"
                humidityEvent( fncmd )
            }
            else if (getModelGroup() in ['TS0601_Tuya_2']) {
                logDebug "battery_state (0x03) is ${fncmd}"         // ['low', 'medium', 'high']
                def rawValue = 0
                /* groovylint-disable-next-line CouldBeSwitchStatement */
                if (fncmd == 2) { rawValue = 100 }          // Battery High
                    else if (fncmd == 1) { rawValue = 66 }      // Battery Medium
                    else if (fncmd == 0) { rawValue = 33 }      // Battery Low
                getBatteryPercentageResult(rawValue * 2)
            }
            else { // _TZE200_zl1kmjqx link quality?
                illuminanceEvent(fncmd)
            }
            break
        case 0x04 : // battery, including 'TS0601_AVATTO_Ink'
            getBatteryPercentageResult(fncmd * 2)
            if (settings?.txtEnable) { log.info "${device.displayName} battery is $fncmd %" }
            break
        case 0x05 : // Soil Monitor
            if (fncmd > 32767) {
                // not good for the plants ...
                fncmd = fncmd - 65536
            }
            if (getModelGroup() in ['TS0601_Soil']) {
                temperatureEvent( fncmd )
            }
            else if (getModelGroup() in ['TS0601_Soil', 'TS0601_Soil_II']) {
                temperatureEvent( fncmd / 10.0 )
            }
            else {
                if (settings?.logEnable) { log.warn "${device.displayName} Soil Monitor reported value ${fncmd}" }
            }
            break
        case 0x06 :
            logInfo "TS0601_AVATTO_Ink sampling temperature (seconds) is ${fncmd}"
            break
        case 0x07 :
            logInfo "TS0601_AVATTO_Ink sampling humidity (seconds) is ${fncmd}"
            break
        case 0x09 : // temp. scale  1=Fahrenheit 0=Celsius (TS0601 Tuya and Haoze) TS0601_Tuya does not change the symbol on the LCD !    // including 'TS0601_Tuya_2' and TS0601_AVATTO_Ink (_TZE204_s139roas)
            logInfo "${device.displayName} Temperature scale reported by device is: ${fncmd == 1 ? 'Fahrenheit' : 'Celsius' }"       // {'celsius' : new Enum(0), 'fahrenheit' : new Enum(1)}
            break
        case 0x0A : // (10) Max. Temp Alarm, Value / 10  (both TS0601_Tuya and TS0601_Haozee) // including 'TS0601_Tuya_2'
            if (((safeToDouble(settings?.maxTempAlarmPar) * 10.0 as int) == (fncmd as int)) || (getModelGroup() in ['TS0601_Haozee']))  {
                if (settings?.logEnable) { log.info "${device.displayName} reported temperature alarm upper limit ${fncmd / 10.0 as double} C" }
            }
            else {
                if (settings?.logEnable) { log.warn "${device.displayName} warning: temperature alarm upper limit reported by the device (${fncmd / 10.0 as double} C) differs from the preference setting (${settings?.maxTempAlarmPar} C)" }
            }
            break
        case 0x0B : // (11) Min. Temp Alarm, Value / 10 (both TS0601_Tuya and TS0601_Haozee) // including 'TS0601_Tuya_2' and TS0601_AVATTO_Ink (_TZE204_s139roas)
            if (((safeToDouble(settings?.minTempAlarmPar) * 10.0 as int) == (fncmd as int)) || (getModelGroup() in ['TS0601_Haozee'])) {
                if (settings?.logEnable) { log.info "${device.displayName} reported temperature alarm lower limit ${fncmd / 10.0 as double} C" }
            }
            else {
                if (settings?.logEnable) { log.warn "${device.displayName} warning: temperature alarm lower limit reported by the device (${fncmd / 10.0 as double} C) differs from the preference setting (${settings?.minTempAlarmPar} C)" }
            }
            break
        case 0x0C : // (12) Max?. Humidity Alarm    (Haozee only?)  and TS0601_AVATTO_Ink (_TZE204_s139roas)
            def divider = getModelGroup() in ['TS0601_AVATTO_Ink'] ? 10.0 : 1.0
            if (settings?.logEnable) { log.info "${device.displayName} humidity alarm upper limit is ${(fncmd / divider) as int} " }
            break
        case 0x0D : // (13) Min?. Humidity Alarm    (Haozee only?)  and TS0601_AVATTO_Ink (_TZE204_s139roas)
            def divider = getModelGroup() in ['TS0601_AVATTO_Ink'] ? 10.0 : 1.0
            if (settings?.logEnable) { log.info "${device.displayName} humidity alarm lower limit is ${(fncmd / divider) as int} " }
            //device.updateSetting("minHumidityAlarmPar", [value:fncmd, type:"number"])
            break
        case 0x0E : // (14) Temperature Alarm 0 = low alarm? 1 = high alarm? 2 = alarm cleared
            if (getModelGroup() in ['TS0601_Soil', 'TS0601_Soil_II', 'TS0601_AVATTO_Ink']) {
                if (settings?.txtEnable) { log.info "${device.displayName} battery_state (0x0E) is ${fncmd}" }
            }
            else if (getModelGroup() in ['DS18B20']) {
                logDebug "DS18B20 Restart Status is ${fncmd}"
            }
            else {
                if (fncmd == 1) {
                    if (settings?.txtEnable) { log.info "${device.displayName} Minimal Temperature Alarm (0x0E=${fncmd}) is active" }
                }
                else if (fncmd == 0) {    // TS0601_Haozee only?
                    if (settings?.txtEnable) { log.info "${device.displayName} Maximal Temperature Alarm (0x0E=${fncmd}) is active" }
                }
                else if (fncmd == 2 ) {
                    if (getModelGroup() in ['TS0601_Haozee']) {
                        if (settings?.txtEnable) { log.info "${device.displayName} Maximal Temperature Alarm (0x0E=${fncmd}) is inactive" }
                    }
                    else {
                        if (settings?.txtEnable) { log.info "${device.displayName} Minimal Temperature Alarm (0x0E=${fncmd}) is inactive" }
                    }
                }
                else {
                    if (settings?.txtEnable) { log.warn "${device.displayName} Temperature Alarm (0x0E) UNKNOWN value ${fncmd}" }   // 1 if alarm (lower alarm) ? 2 if lower alam is cleared
                }
            }
            break
        case 0x0F : // (15) humidity Alarm 0 = low alarm? 1 = high alarm? 2 = alarm cleared    (Haozee only?)
            if (getModelGroup() in ['TS0601_Soil', 'TS0601_Soil_II']) {
                getBatteryPercentageResult(fncmd * 2)
            }
            else {
                if (fncmd == 1) { if (settings?.txtEnable) { log.info "${device.displayName} Minimal Humidity Alarm (0x0F=${fncmd}) is active" } }
                else if (fncmd == 0) { if (settings?.txtEnable) { log.info "${device.displayName} Maximal Humidity Alarm (0x0F=${fncmd}) is active" } }
                else if (fncmd == 2 ) { if (settings?.txtEnable) { log.info "${device.displayName} Humidity Alarm (0x0F=${fncmd}) is inactive" } }
                else { if (settings?.logEnable) { log.warn "${device.displayName} Temperature Alarm (0x0E) UNKNOWN value ${fncmd}" } }// 1 if alarm (lower alarm) ? 2 if lower alam is cleared
            }
            break
        case 0x10 : // (16) Current Luminance _TZ3000_qaaysllp
            illuminanceEvent(fncmd)
            break
        case 0x11 : // (17) t
            if (getModelGroup() in ['TS0601_AVATTO_Ink']) {
                logInfo "TS0601_AVATTO_Ink temperature periodic reporting interval is ${fncmd} minutes"
            }
            else {  // emperature max reporting interval, default 120 min (Haozee only) // maxReportingTimeTemp
                if (settings?.maxReportingTimeTemp == ((fncmd * 60 / 2.5) as int)) {
                    if (settings?.logEnable) { log.info "${device.displayName} reported temperature max reporting interval ${((fncmd * 60 / 2.5) as int)} seconds" }
                }
                else {
                    if (settings?.logEnable) { log.warn "${device.displayName} warning: temperature max reporting interval reported by the device (${((fncmd * 60 / 2.5) as int)}s) differs from the preference setting (${settings?.maxReportingTimeTemp}s)" }
                }
            }
            break
        case 0x12 : // (18)
            if (getModelGroup() in ['TS0601_AVATTO_Ink']) {
                logInfo "TS0601_AVATTO_Ink humidity periodic reporting interval is ${fncmd} minutes"
            }
            else {  // humidity max reporting interval, default 120 min (Haozee only)
                if (settings?.maxReportingTimeHumidity == ((fncmd * 60 / 2.5) as int)) {
                    if (settings?.logEnable) { log.info "${device.displayName} reported humidity max reporting interval ${((fncmd * 60 / 2.5) as int)}  seconds" }
                }
                else {
                    if (settings?.logEnable) { log.warn "${device.displayName} warning: humidity max reporting interval reported by the device (${((fncmd * 60 / 2.5) as int)}s) differs from the preference setting (${settings?.maxReportingTimeHumidity}s)" }
                }
            }
            break
        case 0x13 : // (19) temperature sensitivity(value/2/10) default 0.3C ( divide / 2 for Haozee only) // including 'TS0601_Tuya_2'
            if (getModelGroup() in ['DS18B20']) {
                logDebug "DS18B20 Delay-off Schedule is ${fncmd}"
            }
            else {
                def divider = getModelGroup() in ['TS0601_Haozee'] ? 20.0 : 10.0
                if ((safeToDouble(settings?.temperatureSensitivity) * divider as int) == (fncmd as int)) {
                    if (settings?.logEnable) { log.info "${device.displayName} reported temperature sensitivity ${(fncmd / divider)} C" }
                }
                else {
                    if (settings?.logEnable) { log.warn "${device.displayName} warning: temperature sensitivity reported by the device (${fncmd / divider}) differs from the preference setting (${settings?.temperatureSensitivity})" }
                }
            }
            break
        case 0x14 : // (20) humidity sensitivity default 3%  (Haozee only) also TS0601_AVATTO_Ink
            if (settings?.humiditySensitivity == fncmd) {
                if (settings?.logEnable) { log.info "${device.displayName} reported humidity sensitivity ${fncmd} %" }
            }
            else {
                if (settings?.logEnable) { log.warn "${device.displayName} warning: humidity sensitivity reported by the device (${fncmd}%) differs from the preference setting (${settings?.humiditySensitivity}%)" }
            }
            break
        case 0x15 : // (21) buzer switch
            if (settings?.logEnable) { log.info "${device.displayName} _TZ3000_qaaysllp buzer switch is ${fncmd} " }
            break
        case 0x65 : // (101)
            if (getModelGroup() in ['DS18B20']) {
                logDebug "DS18B20 Work Mode is ${fncmd}"
            }
            else if (getModelGroup() in ['TS0201_TH']) {
                logInfo "${device.displayName} Temperature scale reported by device is: ${fncmd == 1 ? 'Fahrenheit' : 'Celsius' }"
            }
            else {
                if (settings?.logEnable) { log.warn "${device.displayName} <b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" }
            }
            illuminanceEventLux( safeToInt( fncmd ) )  // _TZE200_pay2byax
            break
        case 0x66 : // (102)
            if (getModelGroup() in ['DS18B20']) {
                if (fncmd > 32767) {
                    fncmd = fncmd - 65536
                }
                temperatureEvent( fncmd / 10.0 )
            }
            else {
                logDebug "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            }
            break
        default :
            if (settings?.logEnable) { log.warn "${device.displayName} <b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" }
            break
    }
}

private int getTuyaAttributeValue(ArrayList _data, index) {
    int retValue = 0

    if (_data.size() >= 6) {
        int dataLength = _data[5 + index] as Integer
        int power = 1
        for (i in dataLength..1) {
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5])
            power = power * 256
        }
    }
    return retValue
}

def getModelGroup() {
    def manufacturer = device.getDataValue('manufacturer')
    def modelGroup = 'UNKNOWN'
    if (modelGroupPreference == null) {
        device.updateSetting('modelGroupPreference',  [value:'Auto detect', type:'enum'])
    }
    if (modelGroupPreference == 'Auto detect') {
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
    //    if (settings?.logEnable) { log.trace "${device.displayName} manufacturer ${manufacturer} group is ${modelGroup}" }
    return modelGroup
}

def temperatureEvent( temperaturePar, isDigital=false ) {
    def temperature = temperaturePar
    def map = [:]
    Map statsMap = stringToJsonMap(state.stats); try { statsMap['tempCtr']++ } catch (e) { statsMap['tempCtr'] = 1 }; state.stats = mapToJsonString(statsMap)
    map.name = 'temperature'
    def Scale = location.temperatureScale
    if (Scale == 'F') {
        temperature = (temperature * 1.8) + 32
        map.unit = '\u00B0' + 'F'
    }
    else {
        map.unit = '\u00B0' + 'C'
    }
    def tempCorrected = temperature + safeToDouble(settings?.temperatureOffset)
    map.value  =  Math.round((tempCorrected - 0.05) * 10) / 10
    map.type = isDigital == true ? 'digital' : 'physical'
    map.isStateChange = true
    map.descriptionText = "${map.name} is ${tempCorrected} ${map.unit}"
    Map lastRxMap = stringToJsonMap(state.lastRx)
    def timeElapsed = Math.round((now() - lastRxMap['tempTime']) / 1000)
    Integer timeRamaining = (minReportingTimeTemp - timeElapsed) as Integer
    if (timeElapsed >= minReportingTimeTemp) {
        if (settings?.txtEnable) { log.info "${device.displayName } ${map.descriptionText }" }
        unschedule('sendDelayedEventTemp')        //get rid of stale queued reports
        lastRxMap['tempTime'] = now()
        sendEvent(map)
    }
    else {         // queue the event
        map.type = 'delayed'
        if (settings?.logEnable) { log.debug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${map}" }
        runIn(timeRamaining, 'sendDelayedEventTemp',  [overwrite: true, data: map])
    }
    state.lastRx = mapToJsonString(lastRxMap)
}

private void sendDelayedEventTemp(Map map) {
    if (settings?.txtEnable) { log.info "${device.displayName } ${map.descriptionText } (${map.type })" }
    Map lastRxMap = stringToJsonMap(state.lastRx); try { lastRxMap['tempTime'] = now() } catch (e) { lastRxMap['tempTime'] = now() - (minReportingTimeTemp * 2000) }; state.lastRx = mapToJsonString(lastRxMap)
    sendEvent(map)
}

def humidityEvent( humidity, isDigital=false ) {
    def map = [:]
    Map statsMap = stringToJsonMap(state.stats); try { statsMap['humiCtr']++ } catch (e) { statsMap['humiCtr'] = 1 }; state.stats = mapToJsonString(statsMap)
    double humidityAsDouble = safeToDouble(humidity) + safeToDouble(settings?.humidityOffset)
    if (humidityAsDouble <= 0.0 || humidityAsDouble > 100.0) {
        logWarn "ignored invalid humidity ${humidity} (${humidityAsDouble})"
        return
    }
    map.value = Math.round(humidityAsDouble)
    map.name = 'humidity'
    map.unit = '% RH'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.isStateChange = true
    map.descriptionText = "${map.name} is ${humidityAsDouble.round(1)} ${map.unit}"
    Map lastRxMap = stringToJsonMap(state.lastRx)
    def timeElapsed = Math.round((now() - lastRxMap['humiTime']) / 1000)
    Integer timeRamaining = (minReportingTimeHumidity - timeElapsed) as Integer
    if (timeElapsed >= minReportingTimeHumidity) {
        if (settings?.txtEnable) { log.info "${device.displayName } ${map.descriptionText }" }
        unschedule('sendDelayedEventHumi')
        lastRxMap['humiTime'] = now()
        sendEvent(map)
    }
    else {         // queue the event
        map.type = 'delayed'
        if (settings?.logEnable) { log.debug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${map}" }
        runIn(timeRamaining, 'sendDelayedEventHumi',  [overwrite: true, data: map])
    }
    state.lastRx = mapToJsonString(lastRxMap)
}

private void sendDelayedEventHumi(Map map) {
    if (settings?.txtEnable) { log.info "${device.displayName} ${map.descriptionText} (${map.type})" }
    //state.lastHumi = now()
    Map lastRxMap = stringToJsonMap(state.lastRx); try { lastRxMap['humiTime'] = now() } catch (e) { lastRxMap['humiTime'] = now() - (minReportingTimeHumidity * 2000) }; state.lastRx = mapToJsonString(lastRxMap)
    sendEvent(map)
}

def switchEvent( value ) {
    def map = [:]
    map.name = 'switch'
    map.value = value
    map.descriptionText = "${device.displayName} switch is ${value}"
    if (settings?.txtEnable) { log.info "${map.descriptionText }" }
    sendEvent(map)
}

def motionEvent( value ) {
    def map = [:]
    map.name = 'motion'
    map.value = value  ? 'active' : 'inactive'
    map.descriptionText = "${device.displayName} motion is ${map.value}"
    if (settings?.txtEnable) { log.info "${map.descriptionText }" }
    sendEvent(map)
}

def illuminanceEvent( illuminance, isDigital=false ) {
    Map statsMap = stringToJsonMap(state.stats); try { statsMap['illumCtr']++ } catch (e) { statsMap['illumCtr'] = 1 }; state.stats = mapToJsonString(statsMap)
    def lux = illuminance > 0 ? Math.round(Math.pow(10, (illuminance / 10000))) : 0
    sendEvent('name' : 'illuminance', 'value' : lux, 'type' : isDigital == true ? 'digital' : 'physical', 'unit' : 'lx')
    if (settings?.txtEnable) { log.info "$device.displayName illuminance is ${lux} Lux" }
}

def illuminanceEventLux( Integer lux, isDigital=false ) {
    Map statsMap = stringToJsonMap(state.stats); try { statsMap['illumCtr']++ } catch (e) { statsMap['illumCtr'] = 1 }; state.stats = mapToJsonString(statsMap)
    sendEvent('name' : 'illuminance', 'value' : lux, 'type' : isDigital == true ? 'digital' : 'physical', 'unit' : 'lx')
    if (settings?.txtEnable) { log.info "$device.displayName illuminance is ${lux} Lux" }
}

//  called from initialize() and when installed as a new device
def installed() {
    sendEvent(name: '_info', value: 'installed', isStateChange: true)
    if (settings?.txtEnable) { log.info "${device.displayName} installed()..." }
    unschedule()
    initializeVars(fullInit = true )
}

//
def updated() {
    ArrayList<String> cmds = []
    Map lastRxMap = stringToJsonMap(state.lastRx)
    Map lastTxMap = stringToJsonMap(state.lastTx)

    state.modelGroup = getModelGroup()

    logInfo "Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b> modelGroupPreference = <b>${modelGroupPreference}</b> (${getModelGroup()})"
    logInfo "Debug logging is ${logEnable}; Description text logging is ${txtEnable}"
    if (logEnable) {
        runIn(86400, 'logsOff', [overwrite: true, misfire: 'ignore'])    // turn off debug logging after 30 minutes
        logInfo 'Debug logging will be turned off after 24 hours'
    }
    else {
        unschedule('logsOff')
    }
    Integer fncmd
    if (getModelGroup() in ['TS0601_Tuya', 'TS0601_Haozee', 'TS0601_Tuya_2']) {
        def divider = getModelGroup() in ['TS0601_Haozee'] ? 20.0 : 10.0
        Integer intValue = ((safeToDouble(settings?.temperatureSensitivity )) * divider) as int
        if (settings?.logEnable) { log.trace "${device.displayName} setting temperatureSensitivity to ${(intValue as Double) / divider} C" }
        cmds += sendTuyaCommand('13', DP_TYPE_VALUE, zigbee.convertToHexString(intValue as int, 8))
    }

    if (getModelGroup() in ['TS0601_Tuya', 'TS0601_Haozee', 'TS0201_LCZ030', 'TS0601_Tuya_2']) {
        if (location.temperatureScale == 'C') {    // Celsius
            cmds += sendTuyaCommand('09', DP_TYPE_ENUM, '00')
            if (settings?.logEnable) { log.trace "${device.displayName} setting temperature scale to Celsius: ${cmds}" }
        }
        else if (location.temperatureScale == 'F') {    // Fahrenheit
            cmds += sendTuyaCommand('09', DP_TYPE_ENUM, '01')
            if (settings?.logEnable) { log.trace "${device.displayName} setting temperature scale to Fahrenheit: ${cmds}" }
        }
        else {
            if (settings?.logEnable) { log.warn "${device.displayName} temperatureScaleParameter does NOT MATCH! (${location.temperatureScale})" }
        }
    }

    if (getModelGroup() in ['TS0601_Tuya', 'TS0201_LCZ030']) {
        fncmd = (safeToDouble( maxTempAlarmPar ) * 10) as int
        if (settings?.logEnable) { log.trace "${device.displayName} setting maxTempAlarm to ${fncmd / 10.0 as double} C" }
        cmds += sendTuyaCommand('0A', DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))

        fncmd = (safeToDouble( minTempAlarmPar ) * 10) as int
        if (settings?.logEnable) { log.trace "${device.displayName} setting minTempAlarm to ${fncmd / 10.0 as double} C" }
        cmds += sendTuyaCommand('0B', DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
    }
    if (getModelGroup() in ['TS0601_Haozee']) {
        Integer intValue = settings?.humiditySensitivity as int
        if (settings?.logEnable) { log.trace "${device.displayName} setting  humiditySensitivity to ${intValue} %" }
        cmds += sendTuyaCommand('14', DP_TYPE_VALUE, zigbee.convertToHexString(intValue as int, 8))
        //
        intValue = ((settings?.maxReportingTimeTemp * 2.5) as int) / 60
        if (settings?.logEnable) { log.trace "${device.displayName} setting Temperature Max reporting time to ${(intValue / 2.5) as int} minutes" }
        cmds += sendTuyaCommand('11', DP_TYPE_VALUE, zigbee.convertToHexString(intValue as int, 8))
        //
        intValue = ((settings?.maxReportingTimeHumidity * 2.5) as int) / 60
        if (settings?.logEnable) { log.trace "${device.displayName} setting Humidity Max reporting time to ${(intValue / 2.5) as int} minutes" }
        cmds += sendTuyaCommand('12', DP_TYPE_VALUE, zigbee.convertToHexString(intValue as int, 8))

        /*
        fncmd = safeToInt( maxHumidityAlarmPar )
        if (settings?.logEnable) { log.trace "${device.displayName} changing maxHumidityAlarm to= ${fncmd}" }
        cmds += sendTuyaCommand("0C", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
        fncmd = safeToInt( minHumidityAlarmPar )
        if (settings?.logEnable) { log.trace "${device.displayName} changing minHumidityAlarm to= ${fncmd}" }
        cmds += sendTuyaCommand("0D", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
        */
    }
    /* groovylint-disable-next-line EmptyIfStatement */
    if (getModelGroup() in ['TS0601_Haozee']) {
    // TODO - write attribute 0xF001, cluster 0x400
    }
    if (getModelGroup() in ['OWON']) {
        cmds += initializeDevice()
    }
    if (isConfigurableSleepyDevice()) {    // ["Zigbee NON-Tuya", "TS0201_TH"]

        lastTxMap.tempCfg = (settings?.minReportingTimeTemp as int).toString() + ',' + (settings?.maxReportingTimeTemp as int).toString() + ',' + ((settings?.temperatureSensitivity * 100) as int).toString()
        lastTxMap.humiCfg = (settings?.minReportingTimeHumidity as int).toString() + ',' + (settings?.maxReportingTimeHumidity as int).toString() + ',' + ((settings?.humiditySensitivity * 100) as int).toString()

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
            cmds += zigbee.configureReporting(0x0405, 0x0000, DataType.UINT16, settings?.minReportingTimeHumidity as int, settings?.maxReportingTimeHumidity as int, (settings?.humiditySensitivity * 100) as int, [:], 200)
            log.info "configure humidity reporting (${lastTxMap.humiCfg}) pending ..."
            lastTxMap.humiCfgOK = false
        }
        else {
            logDebug "Humidity reporting already configured (${lastTxMap.humiCfg}), skipping ..."
            lastTxMap.humiCfgOK = true
        }
        cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 10, 14400, 0x01, [:], 200)

        cmds += zigbee.reportingConfiguration(0x0402, 0x0000, [:], 250)
        cmds += zigbee.reportingConfiguration(0x0405, 0x0000, [:], 250)
        cmds += zigbee.reportingConfiguration(0x0001, 0x0021, [:], 250)
        if (getModelGroup() in ['TS0201_TH']) {
            if (settings?.logEnable) { log.trace "temperatureScale = ${settings?.temperatureUnit}" }
            int temperatureScale = settings?.temperatureUnit as int
            // https://github.com/zigpy/zha-device-handlers/issues/3097#issuecomment-2060104995
            //     CELSIUS = 0x00     FAHRENHEIT = 0x01 TuyaMCUCluster attribute: 101 (0x65)
            if (temperatureScale == 0) {
                cmds += sendTuyaCommand('65', DP_TYPE_ENUM, '00', tuyaCmd=0x04)
                if (settings?.logEnable) { log.trace "${device.displayName} setting temperature scale to Celsius: ${cmds}" }
            }
            else  {    // Fahrenheit
                cmds += sendTuyaCommand('65', DP_TYPE_ENUM, '01', tuyaCmd=0x04)
                if (settings?.logEnable) { log.trace "${device.displayName} setting temperature scale to Fahrenheit: ${cmds}" }
            }
        }
    }

    def pendingConfig = lastTxMap.tempCfgOK == true ? 0 : 1
    pendingConfig    += lastTxMap.humiCfgOK == true ? 0 : 1
    if (isConfigurableSleepyDevice()) {    // ['Zigbee NON-Tuya', 'TS0201_TH']
        if (pendingConfig != 0 ) {
            logInfo "pending ${pendingConfig} reporting configurations"
            updateInfo("Pending ${pendingConfig} configuration(s). Wake up the device!")
            lastTxMap.cfgFailure = false
        }
        else {
            logInfo 'no changed configuration parameters to be sent to the device.'
        }
    // try reading the reporting configuration anyway ...
    }
    state.lastTx = mapToJsonString(lastTxMap)
    sendZigbeeCommands( cmds )
}

def isPendingConfig() {
    Map lastTxMap = stringToJsonMap(state.lastTx)
    def isConfigComplete = lastTxMap.cfgFailure || (lastTxMap.tempCfgOK && lastTxMap.humiCfgOK)
    return isConfigComplete ? false : true
}

// called from parse() when any packet is received from the awaken device ...
/* groovylint-disable-next-line MethodName */
def ConfigurationStateMachine() {
    if (!isConfigurableSleepyDevice()) {
        return
    }
    Map lastTxMap = stringToJsonMap(state.lastTx)
    if (lastTxMap.cfgFailure == true ) {
        updateInfo('configuration failure')
        unschedule('configTimer')
        return
    }
    def configState = state.configState
    logDebug "ConfigurationStateMachine configState = ${configState}"
    switch (configState) {
        case 0 : // idle
            if (isPendingConfig()) {
                logDebug 'configuration pending ...'
                updateInfo('sending the reporting configuration...')
                lastTxMap.cfgTimer = ConfigTimer
                updated()
                runIn(1, 'configTimer' , [overwrite: true, misfire: 'ignore'])
                configState = 1
            }
            else {
                logWarn 'ConfigurationStateMachine called without isPendingConfig?'
                unschedule('configTimer')
            }
            break
        case 1 : // waiting 10 seconds for acknowledge from the device // TODO - process config ERRORS !!!
            if (!isPendingConfig()) {
                updateInfo('configured')
                lastTxMap.cfgTimer = 0
                configState = 0
                unschedule('configTimer')
            }
            else if (lastTxMap.cfgTimer == null || lastTxMap.cfgTimer == 0) {    // timeout
                logDebug 'Timeout!'
                updateInfo('Timeout!')
                lastTxMap.cfgTimer = 0
                unschedule('configTimer')
                configState = 0    // try again next time a packet is received from the device..
            }
            else {
                logDebug "config confirmation still pending ... lastTxMap.cfgTimer is ${lastTxMap.cfgTimer}"
            }
            break
        default :
            logWarn "ConfigurationStateMachine() unknown state ${configState}"
            unschedule('configTimer')
            configState = 0
            break
    }
    state.configState = configState
    state.lastTx = mapToJsonString(lastTxMap)
}

// scheduled from ConfigurationStateMachine
def configTimer() {
    Map lastTxMap = stringToJsonMap(state.lastTx)
    logDebug 'configTimer() callled'
    if (lastTxMap.cfgTimer != null) {
        /* groovylint-disable-next-line InvertedIfElse */
        if (!isPendingConfig()) {
            logDebug 'configuration is successful! '
            ConfigurationStateMachine()
        }
        else {
            lastTxMap.cfgTimer = lastTxMap.cfgTimer - 1
            if (lastTxMap.cfgTimer >= 0 ) {
                state.lastTx = mapToJsonString(lastTxMap)    // flush the timer!
                ConfigurationStateMachine()
                runIn(1, 'configTimer' /*, [overwrite: true, misfire: "ignore"]*/)
                logDebug "scheduling again configTimer = ${lastTxMap.cfgTimer}"
            }
            else {
                logDebug 'configTimer expired! Do not restart it.'
                lastTxMap.cfgFailure = true
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
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)  // Battery Percent
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0402 0x0000 {}" //, "delay 200",
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0405 0x0000 {}" //, "delay 200",
    sendZigbeeCommands(cmds)
}

def refresh() {
    checkDriverVersion()
    if (getModelGroup() == 'TS0222') {
        pollTS0222()
        return
    }
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)
    cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200)
    cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200)
    if (device.getDataValue('model') == 'TS0601') { // queryAllTuyaDP added 11/23/2024
        cmds += zigbee.command(0xEF00, 0x03)
    }
    sendZigbeeCommands( cmds )
}

def ping() {
    logInfo 'ping...'
    scheduleCommandTimeoutCheck()
    Map lastTxMap = stringToJsonMap(state.lastTx)
    lastTxMap.pingTime = new Date().getTime()
    state.lastTx = mapToJsonString(lastTxMap)
    sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) )
}

def sendRttEvent() {
    def now = new Date().getTime()
    Map lastTxMap = stringToJsonMap(state.lastTx)
    def timeRunning = now.toInteger() - (lastTxMap.pingTime ?: '0').toInteger()
    def descriptionText = "Round-trip time is ${timeRunning} (ms)"
    logInfo "${descriptionText}"
    sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true)
}

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) {
    runIn(delay, 'deviceCommandTimeout')
}

void deviceCommandTimeout() {
    logWarn 'no response received (sleepy device or offline?)'
    sendEvent(name: 'rtt', value: 'timeout', descriptionText: 'no response received', unit: '', isDigital: true)
}

def driverVersionAndTimeStamp() { VERSION + ' ' + TIME_STAMP }

def checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logInfo "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        scheduleDeviceHealthCheck()     // version 1.4.0
        initializeVars( fullInit = false )
        //
        if (state.rxCounter != null) { state.remove('rxCounter') }
        if (state.txCounter != null) { state.remove('txCounter') }
        if (state.packetID != null)  { state.remove('packetID') }

        if (state.lastRx == null || state.stats == null || state.lastTx == null) {
            resetStats()
        }

        //
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

void scheduleDeviceHealthCheck() {
    Random rnd = new Random()
    //schedule("1 * * * * ? *", 'deviceHealthCheck') // for quick test
    schedule("${rnd.nextInt(59)} ${rnd.nextInt(59)} 1/3 * * ? *", 'deviceHealthCheck')
}

def resetStats() {
    Map stats = [
        date : new Date().format('yyyy-MM-dd', location.timeZone),
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
        cfgFailure : false,
        cfgTimer : 0
    ]
    state.stats  =  mapToJsonString( stats )
    state.lastRx =  mapToJsonString( lastRx )
    state.lastTx =  mapToJsonString( lastTx )
    if (txtEnable == true) { log.info "${device.displayName} Statistics were reset. Press F5 to refresh the device page" }
}

def logInitializeRezults() {
    if (settings?.txtEnable) { log.info "${device.displayName} manufacturer  = ${device.getDataValue('manufacturer')} ModelGroup = ${getModelGroup()}" }
    if (settings?.txtEnable) { log.info "${device.displayName} Initialization finished\r                          version=${VERSION} (Timestamp: ${TIME_STAMP})" }
}

// delete all Preferences
void deleteAllSettings() {
    String preferencesDeleted = ''
    settings.each { it ->
        preferencesDeleted += "${it.key} (${it.value}), "
        device.removeSetting("${it.key}")
    }
    logDebug "Deleted settings: ${preferencesDeleted}"
    logInfo  'All settings (preferences) DELETED'
}

// delete all attributes
void deleteAllCurrentStates() {
    String attributesDeleted = ''
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") }
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED'
}

// delete all State Variables
void deleteAllStates() {
    String stateDeleted = ''
    state.each { it -> stateDeleted += "${it.key}, " }
    state.clear()
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED'
}

void deleteAllScheduledJobs() {
    unschedule() ; logInfo 'All scheduled jobs DELETED'
}

void deleteAllChildDevices() {
    getChildDevices().each { child ->
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}"
        deleteChildDevice(child.deviceNetworkId)
    }
    logDebug 'All child devices DELETED'
}

void loadAllDefaults() {
    deleteAllSettings()
    deleteAllCurrentStates()
    deleteAllScheduledJobs()
    deleteAllStates()
    deleteAllChildDevices()
    logWarn ('All Defaults Loaded! F5 to refresh')
}

// called by initialize() button
void initializeVars(boolean fullInit = true ) {
    log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        loadAllDefaults()
        resetStats()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    state.configState = 0    // reset the configuration state machine

    if (fullInit == true || settings?.modelGroupPreference == null) { device.updateSetting('modelGroupPreference', [value:'Auto detect', type:'enum']) }
    if (fullInit == true || settings?.logEnable == null) { device.updateSetting('logEnable', true) }
    if (fullInit == true || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) }
    if (fullInit == true || settings?.temperatureOffset == null) { device.updateSetting('temperatureOffset', [value:0.0, type:'decimal']) }
    if (fullInit == true || settings?.humidityOffset == null) { device.updateSetting('humidityOffset', [value:0.0, type:'decimal']) }
    if (fullInit == true || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', false) }
    if (fullInit == true || settings?.temperatureSensitivity == null) { device.updateSetting('temperatureSensitivity', [value:0.5, type:'decimal']) }
    if (fullInit == true || settings?.humiditySensitivity == null) { device.updateSetting('humiditySensitivity', [value:5, type:'number']) }
    if (fullInit == true || settings?.illuminanceSensitivity == null) { device.updateSetting('illuminanceSensitivity', [value:12, type:'number']) }
    if (fullInit == true || settings?.minTempAlarmPar == null) { device.updateSetting('minTempAlarmPar',  [value:0.0, type:'decimal']) }
    if (fullInit == true || settings?.maxTempAlarmPar == null) { device.updateSetting('maxTempAlarmPar',  [value:39.0, type:'decimal']) }
    if (fullInit == true || settings?.minHumidityAlarmPar == null) { device.updateSetting('minHumidityAlarmPar',  [value:20, type:'number']) }
    if (fullInit == true || settings?.maxHumidityAlarmPar == null) { device.updateSetting('maxHumidityAlarmPar',  [value:60, type:'number']) }
    if (fullInit == true || settings?.minReportingTimeTemp == null) { device.updateSetting('minReportingTimeTemp',  [value:10, type:'number']) }
    if (fullInit == true || settings?.maxReportingTimeTemp == null) { device.updateSetting('maxReportingTimeTemp',  [value:3600, type:'number']) }
    if (fullInit == true || settings?.minReportingTimeHumidity == null) { device.updateSetting('minReportingTimeHumidity',  [value:10, type:'number']) }
    if (fullInit == true || settings?.maxReportingTimeHumidity == null) { device.updateSetting('maxReportingTimeHumidity',  [value:3600, type:'number']) }
    if (fullInit == true || state.notPresentCounter == null) { state.notPresentCounter = 0 }
    //
    if (fullInit == true || state.modelGroup == null)  { state.modelGroup = getModelGroup() }
    //if (fullInit == true || state.lastTemp == null) state.lastTemp = now() - defaultMinReportingTime * 1000
    //if (fullInit == true || state.lastHumi == null) state.lastHumi = now() - defaultMinReportingTime * 1000
    sendHealthStatusEvent('unknown')
}

/**
 * initializes the device
 * Invoked from configure()
 * @return zigbee commands
 */
def initializeDevice() {
    ArrayList<String> cmds = []
    logInfo 'initializeDevice...'
    if (getModelGroup() == 'OWON') {    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/e8750f6f2a34a3a6ae87f61e989a00964fb1107f/devices/owon.js
        // It seem this device have 2 version, one using the endpoint 0x01 and one other using the endpoint 0x03
        // https://github.com/dresden-elektronik/deconz-rest-plugin/issues/5738#issuecomment-1579521543
        // there is a firmware bug in the OWON THS317-ET which leads to a reported temperature of 327.67?C if the real sensor temperature is near -20?C e.g. in a fridge.
        cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}", 'delay 200',]
        cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0402 {${device.zigbeeId}} {}", 'delay 200',]
        cmds += ["zdo bind 0x${device.deviceNetworkId} 0x03 0x01 0x0001 {${device.zigbeeId}} {}", 'delay 200',]
        cmds += ["zdo bind 0x${device.deviceNetworkId} 0x03 0x01 0x0402 {${device.zigbeeId}} {}", 'delay 200',]
        cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 60, 3600, 0x01, [:], 200)
        cmds += zigbee.configureReporting(0x0402, 0x0000, DataType.INT16, 60, 300, 0x32, [:], 200)    // or delta =  0x14
        cmds += zigbee.reportingConfiguration(0x0001, 0x0021, [:], 250)
        cmds += zigbee.reportingConfiguration(0x0402, 0x0000, [:], 250)
    }

    //
    if (cmds == []) {
        cmds = ['delay 299',]
    }
    return cmds
}

def tuyaBlackMagic() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay = 200)    // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, attributeReportingStatus
    cmds += zigbee.writeAttribute(0x0000, 0xffde, 0x20, 0x13, [:], delay = 200)    // was commented out ver 1.0.10  2022/11/10; returned back ver 1.20 01/15/2023
    return  cmds
}

def configure() {
    if (settings?.txtEnable) { log.info "${device.displayName} configure().." }
    List<String> cmds = []
    cmds += tuyaBlackMagic()
    cmds += initializeDevice()
    sendZigbeeCommands(cmds)
    scheduleDeviceHealthCheck()
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

private sendTuyaCommand(dp, dp_type, fncmd, tuyaCmd=SETDATA) {
    ArrayList<String> cmds = []
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [:], delay = 200, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd )
    if (settings?.logEnable) { log.trace "${device.displayName} sendTuyaCommand = ${cmds}" }
    return cmds
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (settings?.logEnable) { log.trace "${device.displayName } sendZigbeeCommands(cmd=$cmd)" }
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    Map statsMap = stringToJsonMap(state.stats); try { statsMap['txCtr']++ } catch (e) { statsMap['txCtr'] = 1 }; state.stats = mapToJsonString(statsMap)
    sendHubCommand(allActions)
}

private getPACKET_ID() {
    return zigbee.convertToHexString(new Random().nextInt(65536), 4)
}

private getDescriptionText(msg) {
    def descriptionText = "${device.displayName} ${msg}"
    if (settings?.txtEnable) { log.info "${descriptionText}" }
    return descriptionText
}

def logsOff() {
    log.warn "${device.displayName} debug logging disabled..."
    device.updateSetting('logEnable', [value:'false', type:'bool'])
}

def getBatteryPercentageResult(rawValuePar) {
    def rawValue = rawValuePar as int
    logDebug "getBatteryPercentageResult: rawValue = ${rawValue} -> ${rawValue / 2}%"
    def result = [:]
    Map statsMap = stringToJsonMap(state.stats); try { statsMap['battCtr']++ } catch (e) { statsMap['battCtr'] = 1 }; state.stats = mapToJsonString(statsMap)
    if (rawValue < 0) { rawValue = 0; logWarn "batteryPercentage rawValue corrected to ${rawValue}" }
    if (rawValue > 200 ) { rawValue = 200; logWarn "batteryPercentage rawValue corrected to ${rawValue}" }
    result.name = 'battery'
    result.translatable = true
    result.value = Math.round(rawValue / 2)
    result.descriptionText = "${device.displayName} battery is ${result.value}%"
    result.isStateChange = true
    result.unit = '%'
    result.type = 'physical'
    sendEvent(result)
}

private Map getBatteryVoltageResult(rawValue) {
    logDebug "getBatteryVoltageResult: volts = ${(double)rawValue / 10.0}"
    Map statsMap = stringToJsonMap(state.stats); try { statsMap['battCtr']++ } catch (e) { statsMap['battCtr'] = 1 }; state.stats = mapToJsonString(statsMap)
    def linkText = getLinkText(device)

    def result = [:]

    def volts = rawValue / 10
    if (rawValue != 0 && rawValue != 255) {
        def minVolts = 2.1
        def maxVolts = 3.0
        def pct = (volts - minVolts) / (maxVolts - minVolts)
        def roundedPct = Math.round(pct * 100)
        if (roundedPct <= 0) {
            roundedPct = 1
        }
        result.value = Math.min(100, roundedPct)
        result.descriptionText = "${linkText} battery is ${result.value}%"
        result.name = 'battery'
        result.isStateChange = true
        result.type = 'physical'
        result.unit = '%'
        sendEvent(result)
    }
    else {
        if (settings?.logEnable) { log.warn "${device.displayName} ignoring BatteryResult(${rawValue})" }
    }
}

// called when any event was received from the Zigbee device in parse() method..
def setPresent() {
    if ((device.currentValue('healthStatus') ?: 'unknown') != 'online') {
        sendHealthStatusEvent('online')
        logInfo 'is present'
    }
    state.notPresentCounter = 0
}

def deviceHealthCheck() {
    state.notPresentCounter = (state.notPresentCounter ?: 0) + 1
    if (state.notPresentCounter > presenceCountTreshold) {
        if ((device.currentValue('healthStatus', true) ?: 'unknown') != 'offline' ) {
            sendHealthStatusEvent('offline')
            if (settings?.txtEnable) { log.warn "${device.displayName} is not present!" }
        }
    }
    else {
        if (logEnable) { log.debug "${device.displayName} deviceHealthCheck - online (notPresentCounter=${state.notPresentCounter})" }
    }
}

def sendHealthStatusEvent(value) {
    sendEvent(name: 'healthStatus', value: value, descriptionText: "${device.displayName} healthStatus set to $value", type: 'digital')
}

String mapToJsonString( Map map) {
    if (map == null || map == [:]) { return '' }
    String str = JsonOutput.toJson(map)
    return str
}

Map stringToJsonMap( String str) {
    if (str == null) { return [:] }
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
    sendEvent(name: '_info' , value: msg, isStateChange: false)
}

def zTest( dpCommand, dpValue, dpTypeString ) {
    //ArrayList<String> cmds = []
    def dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null
    def dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue

    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" }

    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}

def test( String description) {
    log.warn "parising : ${description}"
    // "profileId:0104, clusterId:EF00, clusterInt:61184, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:B2BA, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:06, direction:01, data:[06, 2D, 66, 02, 00, 04, 00, 00, 01, 5A]"
    parse( description)

}

// https://github.com/dresden-elektronik/deconz-rest-plugin/issues/5483

