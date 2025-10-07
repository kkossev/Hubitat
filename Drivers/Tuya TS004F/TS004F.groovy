/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplementationAsType, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodParameterTypeRequired, MethodReturnTypeRequired, MethodSize, NoDef, NoDouble, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryPackageReference, UnnecessarySetter, UnnecessaryTernaryExpression, VariableTypeRequired */
/**
 *  Tuya Scene Switch TS004F w/ healthStatus driver for Hubitat Elevation hub.
 *
 *  Supports Tuya (Moes, Zemismart, LoraTap, .....) buttons and scene switches and remotes (1,2,3,4,5,6 buttons), smart knobs, Konke, icasa
 *
 *  https://community.hubitat.com/t/release-tuya-scene-switch-ts004f-driver-w-healthstatus/92823
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
 *  The inital version was based on ST DH "Zemismart Button", namespace: SangBoy, author: YooSangBeom
 *
 * ver. 1.0.0 2021-05-08 kkossev     - SmartThings version
 * ver. 2.0.0 2021-10-03 kkossev     - First version for Hubitat in 'Scene Control'mode - AFTER PAIRING FIRST to Tuya Zigbee gateway!
 * ver. 2.1.0 2021-10-20 kkossev     - typos fixed; button wrong event names bug fixed; extended debug logging; added experimental switchToDimmerMode command
 * ver. 2.1.1 2021-10-20 kkossev     - numberOfButtons event bug fix;
 * ver. 2.2.0 2021-10-20 kkossev     - First succesfuly working version with HE!
 * ver. 2.2.1 2021-10-23 kkossev     - added "Reverse button order" preference option
 * ver. 2.2.2 2021-11-17 kkossev     - added battery reporting capability; added buttons handlers for use in Hubutat Dashboards; code cleanup
 * ver. 2.2.3 2021-12-01 kkossev     - added fingerprint for Tuya Remote _TZ3000_pcqjmcud
 * ver. 2.2.4 2021-12-05 kkossev     - added support for 'YSR-MINI-Z Remote TS004F'
 * ver. 2.3.0 2022-02-13 kkossev     - added support for 'Tuya Smart Knob TS004F'
 * ver. 2.4.0 2022-03-31 kkossev     - added support for 'MOES remote TS0044', singleThreaded: true; bug fix: debouncing timer was not started for TS0044
 * ver. 2.4.1 2022-04-23 kkossev     - improved tracing of debouncing logic code; option [overwrite: true] is set explicitely on debouncing timer restart; debounce timer increased to 1000ms
 * ver. 2.4.2 2022-05-07 kkossev     - added LoraTap 6 button Scene Controller; device.getDataValue bug fix;
 * ver. 2.4.3 2022-09-18 kkossev     - added TS0042 Tuya Zigbee 2 Gang Wireless Smart Switch; removed 'release' event for TS0044 switches (not supported by hardware); 'release' digital event bug fix.
 * ver. 2.4.4 2022-10-22 kkossev     - _TZ3000_vp6clf9d fingerprint correction; importURL changed to dev. branch; added _TZ3000_w8jwkczz and other TS0041, TS0042, TS0043, TS004 fingerprints
 * ver. 2.4.5 2022-10-27 kkossev     - added icasa ICZB-KPD18S 8 button controller.
 * ver. 2.4.6 2022-11-20 kkossev     - added TS004F _TZ3000_ja5osu5g - 1 button!; isTuya() bug fix
 * ver. 2.4.7 2022-12-22 kkossev     - added TS004F _TZ3000_rco1yzb1 LIDL Smart Button SSBM A1; added _TZ3000_u3nv1jwk
 * ver. 2.5.0 2023-01-14 kkossev     - bug fix: battery percentage remaining automatic reporting was not configured, now hardcoded to 8 hours; bug fix: 'released'event; debug info improvements; declared supportedButtonValues attribute
 * ver. 2.5.1 2023-01-20 kkossev     - battery percentage remaining HomeKit compatibility
 * ver. 2.5.2 2023-01-28 kkossev     - _TZ3000_vp6clf9d (TS0044) debouncing; added Loratap TS0046 (6 buttons);
 * ver. 2.6.0 2023-01-28 kkossev     - added healthStatus; Initialize button is disabled;
 * ver. 2.6.1 2023-02-05 kkossev     - added _TZ3000_mh9px7cq; isSmartKnob() typo fix; added capability 'Health Check'; added powerSource attribute 'battery'; added dummy ping() code; added _TZ3000_famkxci2
 * ver. 2.6.2 2023-02-23 kkossev     - added Konke button model: 3AFE280100510001 ; LoraTap _TZ3000_iszegwpd TS0046 buttons 5&6;
 * ver. 2.6.3 2023-03-11 kkossev     - added TS0215 _TYZB01_qm6djpta _TZ3000_fsiepnrh _TZ3000_p6ju8myv; added state.stats{rxCtr,txCtr,rejoinCtr}; added Advanced options; added batteryReportingOptions; battery reporting is not changed by default!
 * ver. 2.6.4 2023-04-27 kkossev     - added Sonoff SNZB-01; added IKEA Tradfri Shortcut Button E1812; added AC0251600NJ/AC0251100NJ OSRAM Lightify Switch Mini; added TS0041 _TZ3000_fa9mlvja 1 button; TS0215A _TZ3000_2izubafb inClusters correction
 * ver. 2.6.5 2023-05-15 kkossev     - TS0215A _TZ3000_pkfazisv iAlarm (Meian) SOS button fingerprint correction; number of buttons and supportedValues correction for SOS buttons; added _TZ3000_abrsvsou
 * ver. 2.6.6 2023-05-30 kkossev     - reverseButton default value bug fix;
 *
 * ver. 2.6.9 2023-10-14 kkossev     - REVERTED BACK TO VERSION 2.6.6 timeStamp 2023/05/30 1:51 PM
 * ver. 2.6.10 2023-12-01 kkossev    - added _TZ3000_ur5fpg7p in the needsDebouncing list; added Sonoff SNZB-01P
 * ver. 2.7.0 2024-03-06 kkossev     - Groovy lint; added TS0021 _TZ3210_3ulg9kpo
 * ver. 2.7.1 2024-04-23 kkossev     - added _TZ3000_wkai4ga5 to the needsDebouncing() list; added TS004F _TZ3000_b3mgfu0d and _TZ3000_czuyt8lz;
 * ver. 2.7.2 2024-05-06 kkossev     - bug fix: TS0044 _TZ3000_vp6clf9d _TZ3000_ur5fpg7p _TZ3000_wkai4ga5 needed to be pushed twice to active a button; Configure button will reset the statistics;
 * ver. 2.7.3 2024-06-22 kkossev     - added TS0041 _TZ3000_s0i14ubi; added TS0041 _TZ3000_mrpevh8p
 * ver. 2.7.4 2024-12-03 kkossev     - debounce for TS0043 TZ3000_gbm10jnj
 * ver. 2.8.0 2024-12-04 kkossev     - added forcedDebounce preference; default debounce timer changed to 1200ms
 * ver. 2.8.1 2025-01-12 kkossev     - added SiHAS models SBM300Z2, SBM300Z3, SBM300Z4, SBM300Z5, SBM300Z6, ISM300Z3
 * ver. 2.8.2 2025-05-04 kkossev     - added TS0044 _TZ3000_5tqxpine 
 * ver. 2.8.3 2025-10-07 sbohrer     - added TS0041 _TZ3000_rsqqkdxv 
 *
 *                                   - TODO: debounce timer configuration (1000ms may be too low when repeaters are in use);
 *                                   - TODO: batteryReporting is not initialized!
 *                                   - TODO: unschedule jobs from other drivers: https://community.hubitat.com/t/moes-4-button-zigbee-switch/78119/20?u=kkossev
 *                                   - TODO: configre (override) the numberOfButtons in the AdvancedOptions
 *                                   - TODO: Lightify initialization like in the stock HE driver'; add Aqara button;
 *                                   - TODO: Sonoff button - battery reporting to be enabled by default; Refresh to read battery level/voltage';
 *                                   - TODO: add IAS Zone (0x0500) and IAS ACE (0x0501) support; enroll for TS0215/TS0215A
 *                                   - TODO: Debug logs off after 24 hours
 *                                   - TODO: Remove battery percentage reporting configuration for TS0041 and TS0046 : https://github.com/Koenkk/zigbee2mqtt/issues/6313#issuecomment-780746430 // https://github.com/Koenkk/zigbee2mqtt/issues/15340
 *                                   - TODO: Try to send default responses after button press for TS004F devices : https://github.com/Koenkk/zigbee2mqtt/issues/8149
 *                                   - TODO: Advanced option 'batteryVoltage' 'enum' ['report voltage', 'voltage + battery%'']
 *                                   - TODO: calculate battery % from Voltage event for Konke button!
 *                                   - TODO: add 'auto revert to scene mode' option
 */

static String version() { '2.8.3' }
static String timeStamp() { '2025/10/07 8:07 AM' }

@Field static final Boolean DEBUG = false
@Field static final Integer healthStatusCountTreshold = 4

import groovy.transform.Field
import groovy.json.JsonOutput

metadata {
    definition(name: 'Tuya Scene Switch TS004F', namespace: 'kkossev', author: 'Krassimir Kossev', importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20TS004F/TS004F.groovy', singleThreaded: true ) {
        capability 'Refresh'
        capability 'PushableButton'
        capability 'DoubleTapableButton'
        capability 'HoldableButton'
        capability 'ReleasableButton'
        capability 'Battery'
        capability 'PowerSource'
        capability 'Configuration'
        capability 'Health Check'

        attribute 'supportedButtonValues', 'JSON_OBJECT'
        attribute 'switchMode', 'enum', ['dimmer', 'scene']
        attribute 'batteryVoltage', 'number'
        attribute 'healthStatus', 'enum', ['offline', 'online']
        attribute 'powerSource', 'enum', ['battery', 'dc', 'mains', 'unknown']

        if (DEBUG == true) {
            command 'switchMode', [[name: 'mode*', type: 'ENUM', constraints: ['dimmer', 'scene'], description: 'Select device mode']]
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']]
        }

        fingerprint inClusters: '0000,0001,0003,0004,0006,1000', outClusters: '0019,000A,0003,0004,0005,0006,0008,1000', manufacturer: '_TZ3000_xabckq1v', model: 'TS004F', deviceJoinName: 'Tuya Scene Switch TS004F'
        fingerprint inClusters: '0000,0001,0003,0004,0006,1000', outClusters: '0019,000A,0003,0004,0005,0006,0008,1000', manufacturer: '_TZ3000_pcqjmcud', model: 'TS004F', deviceJoinName: 'YSR-MINI-Z Remote TS004F'
        fingerprint inClusters: '0000,0001,0003,0004,0006,1000', outClusters: '0019,000A,0003,0004,0005,0006,0008,1000', manufacturer: '_TZ3000_4fjiwweb', model: 'TS004F', deviceJoinName: 'Tuya Smart Knob TS004F'
        fingerprint inClusters: '0000,0001,0003,0004,0006,1000', outClusters: '0019,000A,0003,0004,0005,0006,0008,1000', manufacturer: '_TZ3000_uri7ongn', model: 'TS004F', deviceJoinName: 'Tuya Smart Knob TS004F'    // not tested
        fingerprint inClusters: '0000,0001,0003,0004,0006,1000', outClusters: '0019,000A,0003,0004,0005,0006,0008,1000', manufacturer: '_TZ3000_ixla93vd', model: 'TS004F', deviceJoinName: 'Tuya Smart Knob TS004F'    // not tested
        fingerprint inClusters: '0000,0001,0003,0004,0006,1000', outClusters: '0019,000A,0003,0004,0005,0006,0008,1000', manufacturer: '_TZ3000_qja6nq5z', model: 'TS004F', deviceJoinName: 'Tuya Smart Knob TS004F'    // not tested
        fingerprint inClusters: '0000,0001,0003,0004,0006,1000', outClusters: '0019,000A,0003,0004,0005,0006,0008,1000', manufacturer: '_TZ3000_csflgqj2', model: 'TS004F', deviceJoinName: 'Tuya Smart Knob TS004F'    // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0004,0006,1000,0000', outClusters:'0003,0004,0005,0006,0008,1000,0019,000A', model:'TS004F', manufacturer:'_TZ3000_abrsvsou', deviceJoinName: 'Tuya Smart Knob TS004F' //KK

        fingerprint inClusters: '0000,0001,0006', outClusters: '0019,000A', manufacturer: '_TZ3400_keyjqthh', model: 'TS0041', deviceJoinName: 'Tuya YSB22 TS0041'
        fingerprint inClusters: '0000,0001,0006', outClusters: '0019,000A', manufacturer: '_TZ3400_tk3s5tyg', model: 'TS0041', deviceJoinName: 'Tuya TS0041' // not tested
        fingerprint inClusters: '0000,0001,0006', outClusters: '0019,000A', manufacturer: '_TZ3000_xrqsdxq6', model: 'TS0041', deviceJoinName: 'Zigbee Tuya 1 Button' // not tested
        fingerprint inClusters: '0000,0001,0006', outClusters: '0019,000A', manufacturer: '_TZ3400_tk3s5tyg', model: 'TS0041', deviceJoinName: 'Zigbee Tuya 1 Button'
        fingerprint inClusters: '0000,0001,0006', outClusters: '0019,000A', manufacturer: '_TZ3000_tk3s5tyg', model: 'TS0041', deviceJoinName: 'Zigbee Tuya 1 Button'
        fingerprint inClusters: '0000,0001,0006', outClusters: '0019,000A', manufacturer: '_TZ3000_adkvzooy', model: 'TS0041', deviceJoinName: 'Zigbee Tuya 1 Button' // not tested
        fingerprint inClusters: '0000,000A,0001,0006', outClusters: '0019,000A', manufacturer: '_TZ3000_peszejy7', model: 'TS0041', deviceJoinName: 'Zigbee Tuya 1 Button'
        fingerprint inClusters: '0000,0001,0006', outClusters: '0019', manufacturer: '_TYZB02_key8kk7r', model: 'TS0041', deviceJoinName: 'Zigbee Tuya 1 Button'
        fingerprint profileId: '0104', endpointId:'01', inClusters:'0001,0006,E000,0000', outClusters:'0019,000A', model:'TS0041', manufacturer:'_TZ3000_fa9mlvja'    // https://www.aliexpress.com/item/1005005363529624.html
        fingerprint profileId: '0104', endpointId:'01', inClusters:'0001,0006,E000,0000', outClusters:'0019,000A', model:'TS0041', manufacturer:'_TZ3000_s0i14ubi'    // https://community.hubitat.com/t/release-tuya-scene-switch-ts004f-driver-w-healthstatus/92823/231?u=kkossev https://www.aliexpress.us/item/2255800908957715.html
        fingerprint profileId: '0104', endpointId:'01', inClusters:'0001,0006,E000,0000', outClusters:'0019,000A', model:'TS0041', manufacturer:'_TZ3000_mrpevh8p'    // https://community.hubitat.com/t/release-tuya-scene-switch-ts004f-driver-w-healthstatus/92823/236?u=kkossev
        fingerprint inClusters: '0000,0001,0006', outClusters: '0019,000A', manufacturer: '_TZ3000_rsqqkdxv', model: 'TS0041', deviceJoinName: 'Zigbee Tuya 1 Button' // https://github.com/kkossev/Hubitat/pull/43#issue-3484293750 

        fingerprint inClusters: '0000,0001,0003,0004,0006,1000,E001', outClusters: '0019,000A,0003,0004,0006,0008,1000', manufacturer: '_TZ3000_ja5osu5g', model: 'TS004F', deviceJoinName: 'MOES Smart Button (ZT-SY-SR-MS)' // MOES ZigBee IP55 Waterproof Smart Button Scene Switch & Wireless Remote Dimmer (ZT-SY-SR-MS)
        fingerprint inClusters: '0000,0001,0003,0004,0006,1000,E001', outClusters: '0019,000A,0003,0004,0005,0006,0008,1000', manufacturer: '_TZ3000_rco1yzb1', model: 'TS004F', deviceJoinName: 'LIDL Smart Button SSBM A1'

        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0006,E000,0000', outClusters:'0019,000A', model:'TS0042', manufacturer:'_TZ3000_tzvbimpq', deviceJoinName: 'Tuya 2 button Scene Switch'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0006,E000,0000', outClusters:'0019,000A', model:'TS0042', manufacturer:'_TZ3000_t8hzpgnd', deviceJoinName: 'Tuya 2 button Scene Switch'    // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0006,E000,0000', outClusters:'0019,000A', model:'TS0042', manufacturer:'_TYZB02_keyjhapk', deviceJoinName: 'Tuya 2 button Scene Switch'    // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0006,E000,0000', outClusters:'0019,000A', model:'TS0042', manufacturer:'_TZ3400_keyjhapk', deviceJoinName: 'Tuya 2 button Scene Switch'    // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0006,E000,0000', outClusters:'0019,000A', model:'TS0042', manufacturer:'_TZ3000_dfgbtub0', deviceJoinName: 'Tuya 2 button Scene Switch'    // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0006,E000,0000', outClusters:'0019,000A', model:'TS0042', manufacturer:'_TZ3000_h1c2eamp', deviceJoinName: 'Tuya 2 button Scene Switch'    // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0006,E000,0000', outClusters:'0019,000A', model:'TS0042', manufacturer:'_TZ3000_owgcnkrh', deviceJoinName: 'Tuya 2 button Scene Switch'    // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0006,E000,0000', outClusters:'0019,000A', model:'TS0042', manufacturer:'_TZ3000_fkvaniuu', deviceJoinName: 'Tuya 2 button Scene Switch'    // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters: '0000,0001,0006', outClusters: '0019', manufacturer: '_TYZB02_key8kk7r', model: 'TS0042', deviceJoinName: 'Tuya 2 button Scene Switch'

        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0006', outClusters:'0019,000A', model:'TS0043', manufacturer:'_TZ3000_w8jwkczz', deviceJoinName: 'Tuya 3 button Scene Switch'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0006', outClusters:'0019,000A', model:'TS0043', manufacturer:'_TZ3000_gbm10jnj', deviceJoinName: 'Tuya Zigbee 3 button'          // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0006', outClusters:'0019,000A', model:'TS0043', manufacturer:'_TYZB02_key8kk7r', deviceJoinName: 'Tuya 3 button Scene Switch'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0006', outClusters:'0019,000A', model:'TS0043', manufacturer:'_TZ3000_qzjcsmar', deviceJoinName: 'Tuya 3 button Scene Switch'    // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,000A,0001,0006', outClusters:'0019', model:'TS0043', manufacturer:'_TZ3000_bi6lpsew', deviceJoinName: 'Tuya 3 button Scene Switch'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0006', outClusters:'0019,000A', model:'TS0043', manufacturer:'_TZ3000_imnwsek2', deviceJoinName: 'Tuya 3 button Scene Switch'    // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0006', outClusters:'0019,000A', model:'TS0043', manufacturer:'_TZ3000_rrjr1q0u', deviceJoinName: 'Tuya 3 button Scene Switch'    // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0006', outClusters:'0019,000A', model:'TS0043', manufacturer:'_TZ3000_w4thianr', deviceJoinName: 'Tuya 3 button Scene Switch'    // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0006', outClusters:'0019,000A', model:'TS0043', manufacturer:'_TZ3000_a7ouggvs', deviceJoinName: 'Zigbee Lonsonho 3 Button'      // not tested
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0006', outClusters:'0019,000A', model:'TS0043', manufacturer:'_TZ3000_famkxci2', deviceJoinName: 'Loratap 3 Button rRemote'      // https://community.hubitat.com/t/zigbee-3-switch-remote-driver/111935/3?u=kkossev

        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0006', outClusters:'0019,000A', model:'TS0044', manufacturer:'_TZ3000_vp6clf9d', deviceJoinName: 'Zemismart Wireless Scene Switch'
        fingerprint inClusters: '0000,000A,0001,0006', outClusters: '0019', manufacturer: '_TZ3000_vp6clf9d', model: 'TS0044', deviceJoinName: 'Zemismart 4 Button Remote (ESW-0ZAA-EU)'                      // needs debouncing
        fingerprint inClusters: '0000,000A,0001,0006', outClusters: '0019', manufacturer: '_TZ3000_ufhtxr59', model: 'TS0044', deviceJoinName: 'Tuya 4 button Scene Switch'
        fingerprint inClusters: '0000,000A,0001,0006', outClusters: '0019', manufacturer: '_TZ3000_wkai4ga5', model: 'TS0044', deviceJoinName: 'Tuya 4 button Scene Switch'        // https://community.hubitat.com/t/release-tuya-scene-switch-ts004f-driver/92823/79?u=kkossev
        fingerprint inClusters: '0000,000A,0001,0006', outClusters: '0019', manufacturer: '_TZ3000_abci1hiu', model: 'TS0044', deviceJoinName: 'Tuya 4 button Scene Switch'        // not tested
        fingerprint inClusters: '0000,000A,0001,0006', outClusters: '0019', manufacturer: '_TZ3000_ee8nrt2l', model: 'TS0044', deviceJoinName: 'Tuya 4 button Scene Switch'        // not tested
        fingerprint inClusters: '0000,000A,0001,0006', outClusters: '0019', manufacturer: '_TZ3000_dku2cfsc', model: 'TS0044', deviceJoinName: 'Tuya 4 button Scene Switch'        // not tested
        fingerprint inClusters: '0000,000A,0001,0006', outClusters: '0019', manufacturer: '_TYZB01_cnlmkhbk', model: 'TS0044', deviceJoinName: 'Tuya 4 button Scene Switch'        // not tested
        fingerprint inClusters: '0000,0001,0006', outClusters: '0019,000A', manufacturer: '_TZ3000_u3nv1jwk', model: 'TS0044', deviceJoinName: 'Tuya 4 button Scene Switch'        // not tested https://community.hubitat.com/t/zigbee-wireless-scene-switch/108146?u=kkossev
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0006,E000,0000', outClusters: '0019,000A', model: 'TS0044', manufacturer: '_TZ3000_mh9px7cq', deviceJoinName: 'Moes 4 button controller'    // https://community.hubitat.com/t/release-tuya-scene-switch-ts004f-driver/92823/75?u=kkossev
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0006,E000,0000', outClusters: '0019,000A', model: 'TS0044', manufacturer: '_TZ3000_5tqxpine', deviceJoinName: 'Tuya 4 button controller'    // https://community.hubitat.com/t/release-tuya-scene-switch-ts004f-driver-w-healthstatus/92823/268?u=kkossev

        fingerprint inClusters: '0000,0001,0003,0004,0006,1000', outClusters: '0019,000A,0003,0004,0005,0006,0008,1000', manufacturer: '_TZ3000_abci1hiu', model: 'TS0044', deviceJoinName: 'MOES Remote TS0044'

        fingerprint profileId: '0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_2m38mh6k', deviceJoinName: 'LoraTap 6 button Scene Switch'
        fingerprint profileId: '0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_zqtiam4u', deviceJoinName: 'Tuya 6 button Scene Switch'    // not tested
        fingerprint profileId: '0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0046', manufacturer:'_TZ3000_iszegwpd', deviceJoinName: 'LoraTap 6 Scene Switch'       // https://community.hubitat.com/t/loratap-6-button-controller-drivers/91951/20?u=kkossev
        fingerprint profileId: '0104', endpointId:'01', inClusters:'0001,0006,E000,0000', outClusters:'0019,000A', model:'TS0046', manufacturer:'_TZ3000_iszegwpd', deviceJoinName: 'LoraTap 6 Scene Switch'       // https://user-images.githubusercontent.com/42491156/210933986-16fc9854-b7d8-4239-a6ef-311ba631e480.png

        fingerprint profileId: '0104', endpointId: '01', inClusters: '0000,0001,0003,0B05,1000', outClusters: '0003,0004,0005,0006,0008,0019,0300,1000', model:'ICZB-KPD18S', manufacturer:'icasa', deviceJoinName: 'Icasa 8 button Scene Switch'    //https://community.hubitat.com/t/beginners-question-fantastic-button-controller-not-working/103914
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0000,0001,0003,0006,FCC0', outClusters: '0003,FCC0', model: '3AFE280100510001', manufacturer: 'Konke', deviceJoinName: 'Konke button'         // sends Voltage (only!) every 2 hours
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0000,0001,0003,0004,0005,0006', outClusters: '0003', model: '3AFE170100510001', manufacturer: 'Konke', deviceJoinName: 'Konke button'
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0000,0003,0001', outClusters: '0006,0003', model: 'WB01', manufacturer: 'eWeLink', deviceJoinName: 'Sonoff SNZB-01 button'
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0000,0003,0001', outClusters: '0006,0003', model: 'WB-01', manufacturer: 'eWeLink', deviceJoinName: 'Sonoff SNZB-01 button'
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0000,0020,0001,0003,FC57', outClusters: '0003,0006,0019', model: 'SNZB-01P', manufacturer: 'eWeLink', deviceJoinName: 'Sonoff SNZB-01 button'
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0000,0001,0003,0009,0020,1000', outClusters:'0003,0004,0006,0008,0019,0102,1000', model:'TRADFRI SHORTCUT Button', manufacturer:'IKEA of Sweden', deviceJoinName: 'IKEA Tradfri Shortcut Button E1812'
        // OSRAM Lightify - use HE inbuilt driver to pair first !
        //fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0020,1000,FD00", outClusters:"0003,0004,0005,0006,0008,0019,0300,1000", model:"Lightify Switch Mini", manufacturer:"OSRAM", deviceJoinName: "Lightify Switch Mini"
        // 4 button
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500,0B05', outClusters:'0019,0501', model:'TS0215', manufacturer:'_TYZB01_qm6djpta', deviceJoinName: 'Tuya 4 Key Arm Disarm Home SOS Button'     // https://www.aliexpress.com/item/4001062612446.html KK
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500,0B05', outClusters:'0019,0501', model:'TS0215', manufacturer:'_TZ3000_fsiepnrh', deviceJoinName: '4 Button Smart Remote Controller'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500,0B05', outClusters:'0019,0501', model:'TS0215', manufacturer:'_TZ3000_p6ju8myv', deviceJoinName: '4 Button Smart Remote Controller'
        // 4 button Security remote control (cluster: 'ssIasAce') = command_arm, command_emergency; ['disarm', 'arm_day_zones', 'arm_night_zones', 'arm_all_zones', 'exit_delay', 'emergency']
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,0501', outClusters: '0019,000A', model: 'TS0215A', manufacturer: '_TZ3000_fsiepnrh', deviceJoinName: 'Nedis Zigbee 4 Button Fob'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,0501', outClusters: '0019,000A', model: 'TS0215A', manufacturer: '_TZ3000_ug1vtuzn', deviceJoinName: 'Tuya Security remote control' // - 1 button ???
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,0501', outClusters: '0019,000A', model: 'TS0215A', manufacturer: '_TZ3000_0zrccfgx', deviceJoinName: 'Tuya Security remote control'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,0501', outClusters: '0019,000A', model: 'TS0215A', manufacturer: '_TZ3000_p6ju8myv', deviceJoinName: 'Tuya Security remote control'

        // SOS 1 button - command_emergency
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,0501', outClusters: '0019,000A', model: 'TS0215A', manufacturer: '_TZ3000_4fsgukof', deviceJoinName: 'Tuya SOS button'    // 1 button
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,0501', outClusters: '0019,000A', model: 'TS0215A', manufacturer: '_TZ3000_wr2ucaj9', deviceJoinName: 'Tuya SOS button'    // 1 button
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,0501', outClusters: '0019,000A', model: 'TS0215A', manufacturer: '_TZ3000_zsh6uat3', deviceJoinName: 'Tuya SOS button'    // 1 button
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,0501', outClusters: '0019,000A', model: 'TS0215A', manufacturer: '_TZ3000_tj4pwzzm', deviceJoinName: 'Tuya SOS button'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0500,0000', outClusters: '0019,000A', model: 'TS0215A', manufacturer: '_TZ3000_2izubafb', deviceJoinName: 'Tuya SOS button'    // @abraham
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0500,0000', outClusters: '0501,0019,000A', model: 'TS0215A', manufacturer: '_TZ3000_pkfazisv', deviceJoinName: 'iAlarm (Meian) SOS button'    // https://community.hubitat.com/t/request-adding-fingerprints-for-ialarm-devices/118166/2?u=kkossev

        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0500,EF00,0000', outClusters: '0019,000A', model: 'TS0021', manufacturer: '_TZ3210_3ulg9kpo', deviceJoinName: 'Tuya 2 button'    // https://community.hubitat.com/t/request-adding-fingerprints-for-ialarm-devices/118166/2?u=kkossev

        fingerprint inClusters: '0000,0001,0003,0004,1000', outClusters: '0019,000A,0003,0004,0005,0006,0008,0300,1000', manufacturer: '_TZ3000_b3mgfu0d', model: 'TS0044', deviceJoinName: 'Candeo remote' //https://community.hubitat.com/t/release-tuya-scene-switch-ts004f-driver-w-healthstatus/92823/187?u=kkossev
        fingerprint inClusters: '0000,0001,0003,0004,1000', outClusters: '0019,000A,0003,0004,0005,0006,0008,0300,1000', manufacturer: '_TZ3000_czuyt8lz', model: 'TS0044', deviceJoinName: 'Candeo remote'

		// SiHAS Switch (2~6 Gang) https://github.com/SmartThingsCommunity/SmartThingsPublic/blob/eb3cee1775bc55148813909aa9e891631de1e2e8/devicetypes/smartthings/zigbee-multi-switch.src/zigbee-multi-switch.groovy#L102
		fingerprint inClusters: "0000,0003,0006,0019", outClusters: "0003,0004,0019", manufacturer: "ShinaSystem", model: "SBM300Z2", deviceJoinName: "SiHAS Switch 2"
		fingerprint inClusters: "0000,0003,0006,0019", outClusters: "0003,0004,0019", manufacturer: "ShinaSystem", model: "SBM300Z3", deviceJoinName: "SiHAS Switch 3"
		fingerprint inClusters: "0000,0003,0006,0019", outClusters: "0003,0004,0019", manufacturer: "ShinaSystem", model: "SBM300Z4", deviceJoinName: "SiHAS Switch 4"
		fingerprint inClusters: "0000,0003,0006,0019", outClusters: "0003,0004,0019", manufacturer: "ShinaSystem", model: "SBM300Z5", deviceJoinName: "SiHAS Switch 5"
		fingerprint inClusters: "0000,0003,0006,0019", outClusters: "0003,0004,0019", manufacturer: "ShinaSystem", model: "SBM300Z6", deviceJoinName: "SiHAS Switch 6"
		fingerprint inClusters: "0000,0003,0006,0019", outClusters: "0003,0004,0019", manufacturer: "ShinaSystem", model: "ISM300Z3", deviceJoinName: "SiHAS Switch 3"        
    }
    preferences {
        input(name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_LOG_ENABLE)
        input(name: 'txtEnable', type: 'bool', title: '<b>Enable description text logging</b>', defaultValue: true)
        input(name: 'reverseButton', type: 'bool', title: '<b>Reverse button order</b>', defaultValue: true)
        input(name: 'advancedOptions', type: 'bool', title: 'Advanced options', defaultValue: false)
        if (device) {
            if (advancedOptions == true) {
                input(name: 'forcedDebounce', type: 'bool', title: '<b>Force debounce</b>', defaultValue: false)
                if (!isUSBpowered()) {
                    input name: 'batteryReporting', type: 'enum', title: '<b>Battery Reporting Interval</b>', options: batteryReportingOptions.options, defaultValue: batteryReportingOptions.defaultValue, description: \
                    '<i>Keep the battery reporting interval to <b>Default</b>, except when battery level is not reported at all for a long period.<br><b>Caution</b>:some devices are repored to deplete the battery very fast, if the battery reporting is set different than the default!</i>'
                }
            }
        }
    }
}

// Constants
@Field static final Integer DIMMER_MODE = 0
@Field static final Integer SCENE_MODE  = 1
@Field static final Integer DEBOUNCE_TIME = 1200
@Field static final Boolean DEFAULT_LOG_ENABLE = true

@Field static final Map batteryReportingOptions = [
    defaultValue: 00,
    options     : [00: 'Default', 14400: 'Every 4 Hours', 28800: 'Every 8 Hours', 43200: 'Every 12 Hours', 86400: 'Every 24 Hours']
]

boolean isTuya()  { device.getDataValue('model') in ['TS0601', 'TS004F', 'TS0044', 'TS0043', 'TS0042', 'TS0041', 'TS0046', 'TS0215', 'TS0215A', 'TS0021'] }
boolean isIcasa() { device.getDataValue('manufacturer') == 'icasa' }
boolean isSmartKnob() { device.getDataValue('manufacturer') in ['_TZ3000_4fjiwweb', '_TZ3000_rco1yzb1', '_TZ3000_uri7ongn', '_TZ3000_ixla93vd', '_TZ3000_qja6nq5z', '_TZ3000_csflgqj2', '_TZ3000_abrsvsou'] }
boolean isKonkeButton() { device.getDataValue('model') in ['3AFE280100510001', '3AFE170100510001'] }
boolean isSonoff() { device.getDataValue('manufacturer') == 'eWeLink' }
boolean isIkea() { device.getDataValue('manufacturer') == 'IKEA of Sweden' }
boolean isOsram() { device.getDataValue('manufacturer') == 'OSRAM' }
boolean needsDebouncing() { (settings?.forcedDebounce == true) || (device.getDataValue('model') == 'TS004F' || (device.getDataValue('manufacturer') in ['_TZ3000_abci1hiu', '_TZ3000_vp6clf9d', '_TZ3000_ur5fpg7p', '_TZ3000_wkai4ga5']) || (device.getDataValue('model') == 'TS0043' && device.getDataValue('manufacturer') in ['TZ3000_gbm10jnj'])) }
boolean needsMagic() { device.getDataValue('model') in ['TS004F', 'TS0044', 'TS0043', 'TS0042', 'TS0041', 'TS0046'] }
boolean isSOSbutton() { device.getDataValue('manufacturer') in ['_TZ3000_4fsgukof', '_TZ3000_wr2ucaj9', '_TZ3000_zsh6uat3', '_TZ3000_tj4pwzzm', '_TZ3000_2izubafb', '_TZ3000_pkfazisv' ] }
boolean isUSBpowered() { device.getDataValue('manufacturer') in ['_TZ3000_b3mgfu0d', '_TZ3000_czuyt8lz'] }
boolean isSiHAS() { device.getDataValue('manufacturer') == 'ShinaSystem' }

// Parse incoming device messages to generate events
void parse(String description) {
    checkDriverVersion()
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 }
    setHealthStatusOnline()
    if (logEnable) { log.debug "${device.displayName} description is $description" }
    Map event = [:]
    try {
        event = zigbee.getEvent(description)
    }
    catch (e) {
        if (logEnable) { log.warn "${device.displayName } exception caught while procesing event $description" }
    }
    Map result = [:]
    int buttonNumber = 0

    if (event != null && event != [:]) {
        if (logEnable) { log.debug "${device.displayName} Event enter: $event" }
        switch (event.name) {
            case 'battery' :
                event.value = event.value as int    // HomeKit
                event.unit = '%'
                break
            case 'batteryVoltage' :
                event.unit = 'V'
                break
            case 'switch' : // Konke button
                if (isKonkeButton()) {
                    processKonkeButton(description)
                    return
                }
                break
            default :
                if (logEnable) { log.debug "${device.displayName} Unexpected event: $event" }
                break
        }
        event.descriptionText = "${event.name} is ${event.value} ${event.unit}"
        event.isStateChange = true
        event.type = 'physical'
        if (txtEnable) { log.info "${device.displayName} ${event.descriptionText}" }
        result = event
    }
    else if (description?.startsWith('catchall')) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (logEnable) { log.debug "${device.displayName} catchall descMap: $descMap" }
        String buttonState = 'unknown'
        // when TS004F initialized in Scene switch mode!
        if (descMap.clusterInt == 0x0006 && descMap.command == 'FD') {
            if (descMap.sourceEndpoint == '03') {
                buttonNumber = reverseButton == true ? 3 : 1
            }
            else if (descMap.sourceEndpoint == '04') {
                buttonNumber = reverseButton == true  ? 4 : 2
            }
            else if (descMap.sourceEndpoint == '02') {
                buttonNumber = reverseButton == true  ? 2 : 3
            }
            else if (descMap.sourceEndpoint == '01') {
                buttonNumber = reverseButton == true  ? 1 : 4
            }
            else if (descMap.sourceEndpoint == '05') {    // LoraTap TS0046
                buttonNumber = reverseButton == true  ? 5 : 5
            }
            else if (descMap.sourceEndpoint == '06') {
                buttonNumber = reverseButton == true  ? 6 : 6
            }
            if (descMap.data[0] == '00') {
                buttonState = 'pushed'
            }
            else if (descMap.data[0] == '01') { buttonState = 'doubleTapped' }
            else if (descMap.data[0] == '02') { buttonState = 'held' }
            else {
                if (logEnable) { log.warn "${device.displayName} unkknown data in event from cluster ${descMap.clusterInt} sourceEndpoint ${descMap.sourceEndpoint} data[0] = ${descMap.data[0]}" }
                return
            }
        } // command == "FD"
        else if (isSonoff() && (descMap.clusterInt == 0x0006 && (descMap.command in ['00', '01', '02' ]))) {
            // Sonoff SNZB-01
            buttonNumber = 1
            buttonState = descMap.command == '02' ? 'pushed' : descMap.command == '01' ? 'doubleTapped' : descMap.command == '00' ? 'held' : 'unknown'
        }
        else if (isIkea() && ((descMap.clusterInt == 0x0006 || descMap.clusterInt == 0x0008) && (descMap.command in ['01', '05', '07' ]))) {
            // IKEA Tradfri Shortcut Button E1812
            buttonNumber = 1
            if (descMap.clusterInt == 0x0006 && descMap.command == '01') { buttonState = 'pushed' }
            else if (descMap.clusterInt == 0x0008 && descMap.command == '05') { buttonState = 'held' }
            else if (descMap.clusterInt == 0x0008 && descMap.command == '07') { buttonState = 'released' }
            else { buttonState = 'unknown' }
        }
        else if (isOsram() && ((descMap.clusterInt == 0x0006 || descMap.clusterInt == 0x0008) && (descMap.command in ['01', '03', '05', '04', '00' ]))) {
            // OSRAM Lightify Mini
            buttonNumber = safeToInt(descMap.sourceEndpoint)
            if (descMap.command == '01') { buttonState = 'pushed' }
            else if (descMap.command == '04') { buttonState = 'pushed' }
            else if (descMap.command == '00') { buttonState = 'pushed' }
            else if (descMap.command == '05') { buttonState = 'held' }
            else if (descMap.command == '03') { buttonState = 'released' }
            else { buttonState = 'unknown' }
        }
        else if (descMap.clusterInt == 0x0006 && descMap.command in ['00', '01', '02']) {
            // Tuya Single Button (TS0041 or similar) uses On/Off cluster commands for scene events:
            // 02 = Toggle (Single Push)
            // 01 = Off (Double Tap)
            // 00 = On (Held/Released)

            buttonNumber = 1

            switch (descMap.command) {
                case '02':
                    buttonState = 'pushed'
                    if (logEnable) { log.debug "${device.displayName} **TS0041 Single Pushed** via Cluster 0006/Command 02" }
                    break
                case '01':
                    buttonState = 'doubleTapped'
                    if (logEnable) { log.debug "${device.displayName} **TS0041 Double Tapped** via Cluster 0006/Command 01" }
                    break
                case '00':
                    buttonState = 'held'
                    if (logEnable) { log.debug "${device.displayName} **TS0041 Held** via Cluster 0006/Command 00" }
                    break
                default:
                    buttonState = 'unknown'
                    if (logEnable) { log.warn "${device.displayName} Unknown command 0006/${descMap.command}" }
                    break
            }
        }
        else if (descMap.clusterInt == 0x0501) {
            // TODO: Make the button numbers compatible with Muxa's driver : 1 - Arm Away (left); 2 - Disarm (right); 3 - Arm Home (top); 4 - Panic (bottom) // https://community.hubitat.com/t/release-heiman-zigbee-key-fob-driver/27002
            if (descMap.command == '02' && descMap.data.size() == 0)  {
                buttonNumber = reverseButton == true  ? 1 : 3
            }
            else if (descMap.command == '00' && descMap.data.size() >= 1) {
                if (descMap.data[0] == '03') {
                    buttonNumber = reverseButton == true  ? 2 : 4
                }
                else if (descMap.data[0] == '01') {
                    buttonNumber = reverseButton == true  ? 3 : 1
                }
                else if (descMap.data[0] == '00') {
                    buttonNumber = reverseButton == true  ? 4 : 2
                }
            }
            if (buttonNumber != 0) {
                buttonState = 'pushed'
            }
            else {
                if (logEnable) { log.warn "${device.displayName } unkknown event from cluster=${descMap.clusterInt } command=${descMap.command} data=${descMap?.data}" }
                return
            }
        }
        else if (descMap.clusterInt == 0x0006 && descMap.command == 'FC') {
            // Smart knob
            if (descMap.data[0] == '00') {            // Rotate one click right
                buttonNumber = 2
            }
            else if (descMap.data[0] == '01') {       // Rotate one click left
                buttonNumber = 3
            }
            buttonState = 'pushed'
        }
        else if (descMap.clusterId in ['0000', '0006', 'E001'] && descMap.command == '01' && descMap.data?.size() >= 3) { // read attribute response
            if (descMap.data[2] == '86') {
                if (logEnable) { log.debug "${device.displayName} readAttributeResponse cluster: ${descMap.clusterId} unsupported attribute ${descMap.data[1] + descMap.data[0]} status:${descMap.data[2]}" }
            }
            else {
                if (logEnable) { log.debug "${device.displayName} readAttributeResponse cluster: ${descMap.clusterId} ${descMap.data[1] + descMap.data[0]} status:${descMap.data[2]} value:${descMap?.value}" }
            }
            return
        }
        else if (descMap.clusterInt == 0x0006 && descMap.command == '04') { // write attribute response
            if (logEnable) { log.debug "${device.displayName} writeAttributeResponse cluster: ${descMap.clusterId} status:${descMap.data[0]}" }
            return
        }
        else if (descMap?.profileId == '0000' && descMap?.clusterId == '0013') { // device announcement
            logInfo "received device announcement, Device network ID: ${descMap.data[2] + descMap.data[1]}"
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1
            return
        }
        else if (descMap?.profileId == '0000' && descMap?.clusterId == '8021') { // bind response
            logInfo "received bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})"
            return
        }
        else if (descMap?.profileId == '0000' && descMap?.clusterId == '8034') { // leave response
            logInfo "leave response cluster: ${descMap.clusterId}"
            return
        }
        // TODO: (on pairing new device) :  Zigbee parsed:[raw:catchall: 0000 8005 00 00 0040 00 0F4B 00 00 0000 00 00 3B004B0F0101, profileId:0000, clusterId:8005, clusterInt:32773, sourceEndpoint:00, destinationEndpoint:00, options:0040, messageType:00, dni:0F4B, isClusterSpecific:false, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:00, data:[3B, 00, 4B, 0F, 01, 01]]
        else if (descMap.clusterId == 'EF00' && descMap.command in ['01', '06']) { // check for LoraTap button events command '01' or Tuya TS0021 2 button command '06'
            if (descMap.data.size() == 10 && descMap.data[2] == '0A') {
                int value = zigbee.convertHexToInt(descMap?.data[9])
                String descText = "battery is ${value} %"
                if (txtEnable) { log.info "${device.displayName} ${descText}" }
                sendEvent(createEvent(name: 'battery', value: value, unit: '%', descriptionText: descText, type: 'physical'))
                return
            }
            else if (descMap.data.size() == 7 && descMap.data[2] >= '01' && descMap.data[2] <= '06') {
                buttonNumber = zigbee.convertHexToInt(descMap.data[2])
                if (descMap.data[6] == '00') { buttonState = 'pushed' }
                else if (descMap.data[6] == '01') { buttonState = 'doubleTapped' }
                else if (descMap.data[6] == '02') { buttonState = 'held' }
            }
            else {
                if (logEnable) { log.debug "${device.displayName } unprocessed Tuya cluster EF00 command descMap: $descMap" }
            }
        }
        else if (isIcasa()) {
            (buttonNumber,buttonState) = processIcasa(descMap)
        }
        else {
            if (logEnable) { log.debug "${device.displayName } unprocessed catchall from cluster ${descMap.clusterInt } sourceEndpoint ${descMap.sourceEndpoint}" }
            if (logEnable) { log.debug "${device.displayName } catchall descMap: $descMap" }
        }
        //
        if (buttonNumber != 0) {
            if (needsDebouncing()) {
                if (state.lastButtonNumber == buttonNumber) {    // debouncing timer still active!
                    if (logEnable) { log.warn "${device.displayName } ignored event for button ${state.lastButtonNumber } - still in the debouncing time period!" }
                    runInMillis(DEBOUNCE_TIME, buttonDebounce, [overwrite: true])    // restart the debouncing timer again
                    if (logEnable) { log.debug "${device.displayName } restarted debouncing timer ${DEBOUNCE_TIME }ms for button ${buttonNumber} (lastButtonNumber=${state.lastButtonNumber})" }
                    return
                }
            }
            state.lastButtonNumber = buttonNumber
        }
        else {
            if (logEnable) { log.warn "${device.displayName } UNHANDLED event for button ${buttonNumber },  lastButtonNumber=${state.lastButtonNumber}" }
        }
        if (buttonState != 'unknown' && buttonNumber != 0) {
            String descriptionText = "button $buttonNumber was $buttonState"
            event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: 'physical']
            if (txtEnable) { log.info "${device.displayName } $descriptionText" }
        }

        if (event) {
            result = createEvent(event)
            if (needsDebouncing()) { // bug fixed 5/6/2024
                runInMillis(DEBOUNCE_TIME, buttonDebounce, [overwrite: true])
            }
        }
    } // if catchall
    else {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (logEnable) { log.debug "${device.displayName} raw: descMap: $descMap" }
        //log.trace "${device.displayName} descMap.cluster=${descMap.cluster} descMap.attrId=${descMap.attrId} descMap.command=${descMap.command} "
        if (descMap.cluster == '0006' && descMap.attrId == '8004') {
            if (descMap.value == '00') {
                sendEvent(name: 'switchMode', value: 'dimmer', isStateChange: true)
                if (txtEnable) { log.info "${device.displayName} mode is <b>dimmer</b>" }
            }
            else if (descMap.value == '01') {
                sendEvent(name: 'switchMode', value: 'scene', isStateChange: true)
                if (txtEnable) { log.info "${device.displayName} mode is <b>scene</b>" }
            }
            else {
                if (logEnable) { log.warn "${device.displayName} unknown attrId ${descMap.attrId} value ${descMap.value}" }
            }
        }
        else if (descMap?.cluster == '0000' && descMap?.command in ['01']) { // Basic Cluster responses
            if (logEnable) { log.debug "${device.displayName} skipping Basic cluster ${descMap?.cluster} response" }
            return
        }
        else {
            if (logEnable) { log.debug "${device.displayName } did not parse descMap: $descMap" }
        }
    }
    if (result != null && result != [:]) {
        sendEvent(result)
    }
    return
}

@Field static final Integer BUTTON_I = 8
@Field static final Integer BUTTON_O = 7

List processIcasa(final Map descMap) {
    int buttonNumber = 0
    String buttonState = 'unknown'
    //log.trace "descMap=${descMap}"
    if (descMap?.clusterId == '0006') {
        switch (descMap?.command) {
            case '00' :    // button "O" -> 7
                buttonNumber = BUTTON_O
                buttonState = 'pushed'
                break
            case '01' :    // pushed button "I" -> 8
                buttonNumber = BUTTON_I
                buttonState = 'pushed'
                break
            default :
                if (logEnable) { log.warn "${device.displayName} unprocessed ICASA cluster 0006 command ${descMap?.command}" }
        }
    }
    else if (descMap?.clusterId == '0008') {
        switch (descMap?.command) {
            case '05' :    // HELD for both buttons I and O
                if (descMap?.data[0] == '00') { buttonNumber = BUTTON_I }
                else if (descMap?.data[0] == '01') { buttonNumber = BUTTON_O }
                else {
                    if (logEnable) { log.warn "${device.displayName} unprocessed ICASA cluster 0008 HOLD command data=${descMap?.data}" }
                }
                buttonState = 'held'
                break
            case '07' :    // RELEASE after HOLD for both buttons I and O
                buttonNumber = state.lastButtonNumber
                buttonState = 'released'
                if (logEnable) { log.debug "${device.displayName} generating release for state.lastButtonNumber ${state.lastButtonNumber}" }
                break
            default :
                if (logEnable) { log.warn "${device.displayName} unprocessed ICASA cluster 0008 command ${descMap?.command}" }
        }
    }
    else if (descMap?.clusterId == '0005') {
        switch (descMap?.command) {
            case '05' :    // CLICK for buttons S1..S6
                buttonNumber = zigbee.convertHexToInt(descMap?.data[2])
                buttonState = 'pushed'
                break
            case '04' :    //  HELD for buttons S1..S6
                buttonNumber = zigbee.convertHexToInt(descMap?.data[2])
                buttonState = 'held'
                break
            default :
                if (logEnable) { log.warn "${device.displayName} unprocessed ICASA cluster 0005 command ${descMap?.command}" }
        }
    }
    else {
        if (logEnable) { log.warn "${device.displayName} unprocessed ICASA cluster ${decMap?.clusterId} message ${descMap}" }
    }
    return [buttonNumber, buttonState]
}

void processKonkeButton( description ) {
    int buttonNumber = 0
    String buttonState = 'unknown'
    Map descMap = zigbee.parseDescriptionAsMap(description)
    if (logEnable) { log.debug "${device.displayName} KonkeButton descMap: $descMap" }
    if (descMap.cluster != '0006' ) {
        return
    }
    buttonNumber = 1
    if (descMap.value == '80') {
        buttonState = 'pushed'
    }
    else if (descMap.value == '81') {
        buttonState = 'doubleTapped'
    }
    else if (descMap.value == '82') {
        buttonState = 'held'
    }
    else if (descMap.value == 'CD') {
        logInfo "${device.displayName} KonkeButton reset/pair button was pressed"
        return
    }
    else {
        return
    }
    state.lastButtonNumber = buttonNumber
    buttonEvent(buttonNumber, buttonState)
}

/* groovylint-disable-next-line EmptyMethod */
void refresh() {
}

static String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() }

void checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        if (txtEnable == true) { log.debug "${device.displayName} updating the settings from the current driver version ${(state.driverVersion ?: 'UNKNOWN')} to the new version ${driverVersionAndTimeStamp()}" }
        initializeVars( fullInit = false )
        scheduleDeviceHealthCheck()
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

void initializeVars(boolean fullInit = false) {
    if (settings?.txtEnable) { log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}" }
    if (fullInit == true ) {
        state.clear()
        state.driverVersion = driverVersionAndTimeStamp()
        state.stats = [:]
    }
    if (state.stats == null) { state.stats = [:] }
    state.comment = 'Works with Tuya TS004F TS0041 TS0042 TS0043 TS0044 TS0046 TS0601, icasa, Konke, Sonoff'
    if (fullInit == true || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_LOG_ENABLE) }
    if (fullInit == true || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) }
    if (fullInit == true || settings?.reverseButton == null) { device.updateSetting('reverseButton', true) }
    if (fullInit == true || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', false) }
    if (fullInit == true || settings?.forcedDebounce == null) { device.updateSetting('forcedDebounce', false) }
    if (fullInit == true || state.notPresentCounter == null) { state.notPresentCounter = 0 }
    if (fullInit == true || state.lastButtonNumber == null) { state.lastButtonNumber = 0 }

}

void configure() {
    if (logEnable) { log.debug "${device.displayName} Configuring device model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} ..." }
    initialize()
}

void installed() {
    logInfo 'installed()...'
    initializeVars( fullInit = true )
    initialize()
}

void initialize() {
    if (isTuya()) {
        tuyaMagic()
    }
    else if (isSonoff()) {
        sendZigbeeCommands(["zdo bind ${device.deviceNetworkId} ${device.endpointId} 0x01 0x0006 {${device.zigbeeId}} {}", 'delay 50', ])
        sendZigbeeCommands(["he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0001 0x0021 {}", 'delay 200', ])
    }
    else if (isOsram()) {
        sendZigbeeCommands(["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}", 'delay 50', ])
        sendZigbeeCommands(["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0008 {${device.zigbeeId}} {}", 'delay 50', ])
        sendZigbeeCommands(["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0300 {${device.zigbeeId}} {}", 'delay 50', ])
        sendZigbeeCommands(["zdo bind ${device.deviceNetworkId} 0x02 0x01 0x0006 {${device.zigbeeId}} {}", 'delay 50', ])
        sendZigbeeCommands(["zdo bind ${device.deviceNetworkId} 0x02 0x01 0x0008 {${device.zigbeeId}} {}", 'delay 50', ])
        sendZigbeeCommands(["zdo bind ${device.deviceNetworkId} 0x02 0x01 0x0300 {${device.zigbeeId}} {}", 'delay 50', ])
        sendZigbeeCommands(["zdo bind ${device.deviceNetworkId} 0x03 0x01 0x0006 {${device.zigbeeId}} {}", 'delay 50', ])
        sendZigbeeCommands(["zdo bind ${device.deviceNetworkId} 0x03 0x01 0x0008 {${device.zigbeeId}} {}", 'delay 50', ])
        sendZigbeeCommands(["zdo bind ${device.deviceNetworkId} 0x03 0x01 0x0300 {${device.zigbeeId}} {}", 'delay 50', ])
    //sendZigbeeCommands(["zdo bind ${device.deviceNetworkId} 0x04 0x01 0x0006 {${device.zigbeeId}} {}", "delay 50", ])
    //sendZigbeeCommands(["zdo bind ${device.deviceNetworkId} 0x04 0x01 0x0008 {${device.zigbeeId}} {}", "delay 50", ])
    }
    else {
        if (logEnable) { log.debug "${device.displayName} skipped TuyaMagic() for non-Tuya device ${device.getDataValue('model')} ..." }
    }

    // determine the number of the buttons and the supported button actions (values) depending on the model/manufactuer
    int numberOfButtons = 4
    List<String> supportedValues = ['pushed', 'double', 'held']
    if (isSOSbutton()) {
        numberOfButtons = 1
        supportedValues = ['pushed']
    }
    else if ((device.getDataValue('model') in ['TS0041', '3AFE280100510001', '3AFE170100510001']) || (device.getDataValue('manufacturer') in ['_TZ3000_ja5osu5g', 'eWeLink'])) {
        numberOfButtons = 1
    }
    else if (device.getDataValue('model') in ['TS0042', 'TS0021', 'SBM300Z2']) {
        numberOfButtons = 2
    }
    else if (device.getDataValue('model') in ['TS0043', 'SBM300Z3', 'ISM300Z3']) {
        numberOfButtons = 3
    }
    else if (device.getDataValue('model') == 'TS004F' || device.getDataValue('model') == 'TS0044') {
        if (isSmartKnob()) {    // Smart Knob
            log.debug "${device.displayName} device ${device.data.manufacturer} identified as Smart Knob model ${device.data.model}"
            numberOfButtons = 3
            supportedValues = ['pushed', 'double', 'held', 'released']
        }
        else {
            log.debug "${device.displayName} device ${device.data.manufacturer} identified as 4 keys scene switch model ${device.data.model}"
            numberOfButtons = 4
            supportedValues = ['pushed', 'double', 'held']    // no released events are generated in scene switch mode
        }
    }
    else if (device.getDataValue('model') in ['TS0215']) {
        numberOfButtons = 4
        supportedValues = ['pushed']
    }
    else if (device.getDataValue('model') in ['TS0045', 'SBM300Z5']) {    // just in case a new Tuya devices manufacturer decides to invent a new  model! :)
        numberOfButtons = 5
    }
    else if (device.getDataValue('model') in ['TS0601', 'TS0046', 'SBM300Z6']) {
        numberOfButtons = 6
    }
    else if (isIcasa()) {
        numberOfButtons = 8
        supportedValues = ['pushed', 'held', 'released']
    }
    else if (isIkea()) {
        numberOfButtons = 1
        supportedValues = ['pushed', 'held', 'released']
    }
    else if (isOsram()) {    // mini
        numberOfButtons = 3
        supportedValues = ['pushed', 'held', 'released']
    }
    else if (device.getDataValue('model') in ['SBM300Z4']) {
        supportedValues = ['pushed']
    }    
    else {
        numberOfButtons = 4    // unknown
        supportedValues = ['pushed', 'double', 'held', 'released']
        log.warn "${device.displayName} <b>unknown device model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')}. Please report this log to the developer.</b>"
    }
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true)
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true)
    if (device.currentValue('healthStatus') == null) { setHealthStatusValue('unknown') }
    if (device.currentValue('powerSource') == null) {
        if (isUSBpowered()) {
            sendEvent(name: 'powerSource', value: 'dc', isStateChange: true)
        }
        else {
            sendEvent(name: 'powerSource', value: 'battery', isStateChange: true)
        }
    }
    state.lastButtonNumber = 0
    if (state.stats == null) { state.stats = [:] }
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0 ; state.stats['rejoinCtr'] = 0
    scheduleDeviceHealthCheck()
}

void updated() {
    if (logEnable) { log.debug "${device.displayName } updated()" }
    scheduleDeviceHealthCheck()
}

/* groovylint-disable-next-line UnusedMethodParameter */
void buttonDebounce(button) {
    if (logEnable) { log.debug "${device.displayName} debouncing timer for button ${state.lastButtonNumber} expired." }
    state.lastButtonNumber = 0
}

void switchToSceneMode() {
    if (logEnable) { log.debug "${device.displayName} Switching TS004F into Scene mode" }
    sendZigbeeCommands(zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x01))
}

void switchToDimmerMode() {
    if (logEnable) { log.debug "${device.displayName} Switching TS004F into Dimmer mode" }
    sendZigbeeCommands(zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x00))
}

void buttonEvent(buttonNumber, final String buttonState, final boolean isDigital=false) {
    Map event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true, type: isDigital == true ? 'digital' : 'physical']
    if (txtEnable) { log.info "${device.displayName} $event.descriptionText" }
    sendEvent(event)
}

void push(buttonNumber) {
    buttonEvent(buttonNumber, 'pushed', isDigital = true)
}

void doubleTap(buttonNumber) {
    buttonEvent(buttonNumber, 'doubleTapped', isDigital = true)
}

void hold(buttonNumber) {
    buttonEvent(buttonNumber, 'held', isDigital = true)
}

void release(buttonNumber) {
    buttonEvent(buttonNumber, 'released', isDigital = true)
}

void switchMode(final String mode) {
    if (mode == 'dimmer') {
        switchToDimmerMode()
    }
    else if (mode == 'scene') {
        switchToSceneMode()
    }
}

/* groovylint-disable-next-line NoDef */
Integer safeToInt(val, Integer defaultVal=0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

/* groovylint-disable-next-line NoDef */
Double safeToDouble(val, Double defaultVal=0.0) {
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

void tuyaMagic() {
    List<String> cmd = []
    cmd += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay = 200)    // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, Unknown 0xfffe
    /*
    cmd +=  "raw 0x0000  {10 00 00 04 00 00 00 01 00 05 00 07 00 FE FF}"
    cmd +=  "send 0x${device.deviceNetworkId} 1 255"
    cmd += "delay 200"
    */
    if (needsMagic()) {
        cmd += zigbee.readAttribute(0x0006, 0x8004, [:], delay = 50)                      // success / 0x00
        cmd += zigbee.readAttribute(0xE001, 0xD011, [:], delay = 50)                      // Unsupported attribute (0x86)
        cmd += zigbee.readAttribute(0x0001, [0x0020, 0x0021], [:], delay = 50)            // Battery voltage + Battery Percentage Remaining
        cmd += zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x01, [:], delay = 50)         // switch into Scene Mode !
        cmd += zigbee.readAttribute(0x0006, 0x8004, [:], delay = 50)
    }
    // binding for battery reporting was added on 2023/01/04 (ver 2.5.0), but thee are doubts that it may cause device re-joins and depletes the battery!
    int batteryReportinginterval = (settings.batteryReporting as Integer) ?: 0
    if (batteryReportinginterval > 0) {
        logInfo "setting the battery reporting interval to ${(batteryReportinginterval / 3600) as int} hours"
        cmd += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 600, batteryReportinginterval, 0x01, [:], delay = 150)
        cmd += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 600, batteryReportinginterval, 0x01, [:], delay = 150)        // 0x21 is NOT supported by all devices?
    }
    else {
        logInfo 'battery reporting interval not changed.'
    }

    sendZigbeeCommands(cmd)
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (logEnable) { log.trace "${device.displayName } sendZigbeeCommands(cmd=$cmd)" }
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
    }
    if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0)  + 1 }
    sendHubCommand(allActions)
}

void scheduleDeviceHealthCheck() {
    Random rnd = new Random()
    //schedule("1 * * * * ? *", 'deviceHealthCheck') // test
    schedule("${rnd.nextInt(59)} ${rnd.nextInt(59)} 1/3 * * ? *", 'deviceHealthCheck')
}

// called every 3 hours
void deviceHealthCheck() {
    state.notPresentCounter = (state.notPresentCounter ?: 0) + 1
    if (state.notPresentCounter > healthStatusCountTreshold) {
        if (!(device.currentValue('healthStatus', true) in ['offline'])) {
            setHealthStatusValue('offline')
            log.warn "${device.displayName} is offline!"
        }
    }
    else {
        if (logEnable) { log.debug "${device.displayName} deviceHealthCheck - online (notPresentCounter=${state.notPresentCounter})" }
    }
}

void setHealthStatusOnline() {
    state.notPresentCounter = 0
    if (!(device.currentValue('healthStatus', true) in ['online'])) {
        setHealthStatusValue('online')
        log.info "${device.displayName} is online"
    }
}

void setHealthStatusValue(final String value) {
    sendEvent(name: 'healthStatus', value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

void ping() {
    if (logEnable) { log.debug 'ping() is not implemented' }
}

void logDebug(final String msg) {
    if (settings?.logEnable) {
        log.debug "${device.displayName} " + msg
    }
}

void logInfo(final String msg) {
    if (settings?.txtEnable) {
        log.info "${device.displayName} " + msg
    }
}

void logWarn(final String msg) {
    if (settings?.logEnable) {
        log.warn "${device.displayName} " + msg
    }
}

void test(String description) {
    log.warn "test: ${description}"
    parse(description)
// TODO: add Centralite / Iris buttons : https://raw.githubusercontent.com/chalford-st/SmartThingsPublic/master/devicetypes/smartthings/zigbee-button.src/zigbee-button.groovy
// TODO: Check Osrma mini driver: https://raw.githubusercontent.com/chalford-st/SmartThingsPublic/master/devicetypes/chalford/osram-lightify-switch-mini.src/osram-lightify-switch-mini.groovy
// TODO: Check Aqara driver : https://raw.githubusercontent.com/jsconstantelos/SmartThings/master/devicetypes/jsconstantelos/my-aqara-double-rocker-switch-no-neutral.src/my-aqara-double-rocker-switch-no-neutral.groovy
// TODO: Check Ikea quirk : https://github.com/TheJulianJES/zha-device-handlers/blob/05c59d01683e0e929f982bf90a338c7596b3e119/zhaquirks/ikea/fourbtnremote.py
}
