/* groovylint-disable CouldBeSwitchStatement, IfStatementBraces, ImplementationAsType, LineLength, MethodReturnTypeRequired, NoDef, NoDouble, NoFloat, NoJavaUtilDate, NoWildcardImports, ParameterName, ReturnNullFromCatchBlock, UnusedPrivateMethod, VariableName, VariableTypeRequired */
/**
 *  Tuya Multi Sensor 4 In 1 driver for Hubitat
 *
 *  https://community.hubitat.com/t/alpha-tuya-zigbee-multi-sensor-4-in-1/92441
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
 * ver. 1.0.0  2022-04-16 kkossev  - Inital test version
 * ver. 1.0.1  2022-04-18 kkossev  - IAS cluster multiple TS0202, TS0210 and RH3040 Motion Sensors fingerprints; ignore repeated motion inactive events
 * ver. 1.0.2  2022-04-21 kkossev  - setMotion command; state.HashStringPars; advancedOptions: ledEnable (4in1); all DP info logs for 3in1!; _TZ3000_msl6wxk9 and other TS0202 devices inClusters correction
 * ver. 1.0.3  2022-05-05 kkossev  - '_TZE200_ztc6ggyl' 'Tuya ZigBee Breath Presence Sensor' tests; Illuminance unit changed to 'lx'
 * ver. 1.0.4  2022-05-06 kkossev  - DeleteAllStatesAndJobs; added isHumanPresenceSensorAIR(); isHumanPresenceSensorScene(); isHumanPresenceSensorFall(); convertTemperatureIfNeeded
 * ver. 1.0.5  2022-06-11 kkossev  - _TZE200_3towulqd +battery; 'Reset Motion to Inactive' made explicit option; sensitivity and keepTime for IAS sensors (TS0202-tested OK) and TS0601(not tested); capability "PowerSource" used as presence
 * ver. 1.0.6  2022-07-10 kkossev  - battery set to 0% and motion inactive when the device goes OFFLINE;
 * ver. 1.0.7  2022-07-17 kkossev  - _TZE200_ikvncluo (MOES) and _TZE200_lyetpprm radars; scale fadingTime and detectionDelay by 10; initialize() will resets to defaults; radar parameters update bug fix; removed unused states and attributes for radars
 * ver. 1.0.8  2022-07-24 kkossev  - _TZE200_auin8mzr (HumanPresenceSensorAIR) unacknowledgedTime; setLEDMode; setDetectionMode commands and  vSensitivity; oSensitivity, vacancyDelay preferences; _TZE200_9qayzqa8 (black sensor) Attributes: motionType; preferences: inductionTime; targetDistance.
 * ver. 1.0.9  2022-08-11 kkossev  - degrees Celsius symbol bug fix; added square black radar _TZE200_0u3bj3rc support, temperatureOffset bug fix; decimal/number type prferences bug fix
 * ver. 1.0.10 2022-08-15 kkossev  - added Lux threshold parameter; square black radar LED configuration is resent back when device is powered on; round black PIR sensor powerSource is set to DC; added OWON OCP305 Presence Sensor
 * ver. 1.0.11 2022-08-22 kkossev  - IAS devices initialization improvements; presence threshold increased to 4 hours; 3in1 exceptions bug fixes; 3in1 and 4in1 exceptions bug fixes;
 * ver. 1.0.12 2022-09-05 kkossev  - added _TZE200_wukb7rhc MOES radar
 * ver. 1.0.13 2022-09-25 kkossev  - added _TZE200_jva8ink8 AUBESS radar; 2-in-1 Sensitivity setting bug fix
 * ver. 1.0.14 2022-10-31 kkossev  - added Bond motion sensor ZX-BS-J11W fingerprint for tests
 * ver. 1.0.15 2022-12-03 kkossev  - OWON 0x0406 cluster binding; added _TZE204_ztc6ggyl _TZE200_ar0slwnd _TZE200_sfiy5tfs _TZE200_mrf6vtua (was wrongly 3in1) mmWave radards;
 * ver. 1.0.16 2022-12-10 kkossev  - _TZE200_3towulqd (2-in-1) motion detection inverted; excluded from IAS group;
 * ver. 1.1.0  2022-12-25 kkossev  - SetPar() command;  added 'Send Event when parameters change' option; code cleanup; added _TZE200_holel4dk; added 4-in-1 _TZ3210_rxqls8v0, _TZ3210_wuhzzfqg
 * ver. 1.1.1  2023-01-08 kkossev  - illuminance event bug fix; fadingTime minimum value 0.5; SetPar command shows in the UI the list of all possible parameters; _TZ3000_6ygjfyll bug fix;
 * ver. 1.2.0  2023-02-07 kkossev  - healthStatus; supressed repetative Radar detection delay and Radar fading time Info messages in the logs; logsOff missed when hub is restarted bug fix; capability 'Health Check'; _TZE200_3towulqd (2in1) new firmware versions fix for motion;
 * ver. 1.2.1  2023-02-10 kkossev  - reverted the unsuccessful changes made in the latest 1.2.0 version for _TZE200_3towulqd (2in1); added _TZE200_v6ossqfy as BlackSquareRadar; removed the wrongly added TUYATEC T/H sensor...
 * ver. 1.2.2  2023-03-18 kkossev  - typo in a log transaction fixed; added TS0202 _TZ3000_kmh5qpmb as a 3-in-1 type device'; added _TZE200_xpq2rzhq radar; bug fix in setMotion()
 * ver. 1.3.0  2023-03-22 kkossev  -'_TYST11_7hfcudw5' moved to 3-in-1 group; added deviceProfiles; fixed initializaiton missing on the first pairing; added batteryVoltage; added tuyaVersion; added delayed battery event;
 *                                   removed state.lastBattery; caught sensitivity par exception; fixed forcedProfile was not set automatically on Initialize;
 * ver. 1.3.1  2023-03-29 kkossev  - added 'invertMotion' option; 4in1 (Fantem) Refresh Tuya Magic; invertMotion is set to true by default for _TZE200_3towulqd;
 * ver. 1.3.2  2023-04-17 kkossev  - 4-in-1 parameter for adjusting the reporting time; supressed debug logs when ignoreDistance is flipped on; 'Send Event when parameters change' parameter is removed (events are always sent when there is a change); fadingTime and detectionDelay change was not logged and not sent as an event;
 * ver. 1.3.3  2023-05-14 kkossev  - code cleanup; added TS0202 _TZ3210_cwamkvua [Motion Sensor and Scene Switch]; added _TZE204_sooucan5 radar in a new TS0601_YXZBRB58_RADAR group (for tests); added reportingTime4in1 to setPar command options;
 * ver. 1.3.4  2023-05-19 kkossev  - added _TZE204_sxm7l9xa mmWave radar to TS0601_YXZBRB58_RADAR group; isRadar() bug fix;
 * ver. 1.3.5  2023-05-28 kkossev  - fixes for _TZE200_lu01t0zlTS0601_RADAR_MIR-TY-FALL mmWave radar (only the basic Motion and radarSensitivity is supported for now).
 * ver. 1.3.6  2023-06-25 kkossev  - chatty radars excessive debug logging bug fix
 * ver. 1.3.7  2023-07-27 kkossev  - fixes for _TZE204_sooucan5; moved _TZE204_sxm7l9xa to a new Device Profile TS0601_SXM7L9XA_RADAR; added TS0202 _TZ3040_bb6xaihh _TZ3040_wqmtjsyk; added _TZE204_qasjif9e radar;
 * ver. 1.4.0  2023-08-06 kkossev  - added new TS0225 _TZE200_hl0ss9oa 24GHz radar (TS0225_HL0SS9OA_RADAR); added  basic support for the new TS0601 _TZE204_sbyx0lm6 radar w/ relay; added Hive MOT003; added sendCommand; added TS0202 _TZ3040_6ygjfyll
 * ver. 1.4.1  2023-08-15 kkossev  - TS0225_HL0SS9OA_RADAR ignoring ZCL illuminance and IAS motion reports; added radarAlarmMode, radarAlarmVolume, radarAlarmTime, Radar Static Detection Minimum Distance; added TS0225_AWARHUSB_RADAR TS0225_EGNGMRZH_RADAR
 * ver. 1.4.2  2023-08-15 kkossev  - 'Tuya Motion Sensor and Scene Switch' driver clone (Button capabilities enabled)
 * ver. 1.4.3  2023-08-17 kkossev  - TS0225 _TZ3218_awarhusb device profile changed to TS0225_LINPTECH_RADAR; cluster 0xE002 parser; added TS0601 _TZE204_ijxvkhd0 to TS0601_IJXVKHD0_RADAR; added _TZE204_dtzziy1e, _TZE200_ypprdwsl _TZE204_xsm7l9xa; YXZBRB58 radar illuminance and fadingTime bug fixes; added new TS0225_2AAELWXK_RADAR profile
 * ver. 1.4.4  2023-08-18 kkossev  - Method too large: Script1.processTuyaCluster ... :( TS0225_LINPTECH_RADAR: myParseDescriptionAsMap & swapOctets(); deleteAllCurrentStates(); TS0225_2AAELWXK_RADAR preferences configuration and commands; added Illuminance correction coefficient; code cleanup
 * ver. 1.4.5  2023-08-26 kkossev  - reduced debug logs;
 * ver. 1.5.0  2023-08-27 kkossev  - added TS0601 _TZE204_yensya2c radar; refactoring: deviceProfilesV2: tuyaDPs; unknownDPs; added _TZE204_clrdrnya; _TZE204_mhxn2jso; 2in1: _TZE200_1ibpyhdc, _TZE200_bh3n6gk8; added TS0202 _TZ3000_jmrgyl7o _TZ3000_hktqahrq _TZ3000_kmh5qpmb _TZ3040_usvkzkyn; added TS0601 _TZE204_kapvnnlk new device profile TS0601_KAPVNNLK_RADAR
 * ver. 1.5.1  2023-09-09 kkossev  - _TZE204_kapvnnlk fingerprint and DPs correction; added 2AAELWXK preferences; TS0225_LINPTECH_RADAR known preferences using E002 cluster
 * ver. 1.5.2  2023-09-14 kkossev  - TS0601_IJXVKHD0_RADAR ignore dp1 dp2; Distance logs changed to Debug; Refresh() updates driver version;
 * ver. 1.5.3  2023-09-30 kkossev  - humanMotionState re-enabled for TS0225_HL0SS9OA_RADAR; tuyaVersion is updated on Refresh; LINPTECH: added existance_time event; illuminance parsing exception changed to debug level; leave_time changed to fadingTime; fadingTime configuration
 *
 * ver. 1.6.0  2023-10-08 kkossev  - (dev. branch) major refactoring of the preferences input; all preference settings are reset to defaults when changing device profile; added 'all' attribute; present state 'motionStarted' in a human-readable form.
 *                                   setPar and sendCommand major refactoring +parameters changed from enum to string; TS0601_KAPVNNLK_RADAR parameters support;
 * ver. 1.6.1  2023-10-12 kkossev  - (dev. branch) TS0601_KAPVNNLK_RADAR TS0225_HL0SS9OA_RADAR TS0225_2AAELWXK_RADAR TS0601_RADAR_MIR-HE200-TY TS0601_YXZBRB58_RADAR TS0601_SXM7L9XA_RADAR TS0601_IJXVKHD0_RADAR TS0601_YENSYA2C_RADAR TS0601_SBYX0LM6_RADAR TS0601_PIR_AIR TS0601_PIR_PRESENCE refactoring; radar enum preferences;
 * ver. 1.6.2  2023-10-14 kkossev  - (dev. branch) LINPTECH preferences changed to enum type; enum preferences - set defVal; TS0601_PIR_PRESENCE - preference inductionTime changed to fadingTime, humanMotionState sent as event; TS0225_2AAELWXK_RADAR - preferences setting; _TZE204_ijxvkhd0 fixes; Linptech fixes; added radarAlarmMode radarAlarmVolume;
 * ver. 1.6.3  2023-10-15 kkossev  - (dev. branch) setPar() and preferences updates bug fixes; automatic fix for preferences which type was changed between the versions, including bool;
 * ver. 1.6.4  2023-10-18 kkossev  - (dev. branch) added TS0601 _TZE204_e5m9c5hl to SXM7L9XA profile; added a bunch of new manufacturers to SBYX0LM6 profile;
 * ver. 1.6.5  2023-10-23 kkossev  - (dev. branch) bugfix: setPar decimal values for enum types; added SONOFF_SNZB-06P_RADAR; added SIHAS_USM-300Z_4_IN_1; added SONOFF_MOTION_IAS; TS0202_MOTION_SWITCH _TZ3210_cwamkvua refactoring; luxThreshold hardcoded to 0 and not configurable!; do not try to input preferences of a type bool
 *                                   TS0601_2IN1 refactoring; added keepTime and sensitivity attributes for PIR sensors; added _TZE200_ppuj1vem 3-in-1; TS0601_3IN1 refactoring; added _TZ3210_0aqbrnts 4in1;
 * ver. 1.6.6  2023-11-02 kkossev  - _TZE204_ijxvkhd0 staticDetectionSensitivity bug fix; SONOFF radar clusters binding; assign profile UNKNOWN for unknown devices; SONOFF radar cluster FC11 attr 2001 processing as occupancy; TS0601_IJXVKHD0_RADAR sensitivity as number; number type pars are scalled also!; _TZE204_ijxvkhd0 sensitivity settings changes; added preProc function; TS0601_IJXVKHD0_RADAR - removed multiplying by 10
 * ver. 1.6.7  2023-11-09 kkossev  - (dev. branch) divideBy10 fix for TS0601_IJXVKHD0_RADAR; added new TS0202_MOTION_IAS_CONFIGURABLE group
 * ver. 1.6.8  2023-11-20 kkossev  - SONOFF SNZB-06P RADAR bug fixes; added radarSensitivity and fadingTime preferences; update parameters for Tuya radars bug fix;
 * ver. 1.7.0  2024-01-14 kkossev  - (dev.branch) Groovy linting; added TS0225_O7OE4N9A_RADAR TS0225 _TZFED8_o7oe4n9a for tests; TS0601 _TZE200_3towulqd new fingerprint @JdThomas24
 *
 *                                   TODO:   https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/926?u=kkossev
 *                                   TODO: if isSleepy - store in state.cmds and send when the device wakes up!  (on both update() and refresh()
 *                                   TODO: TS0202_MOTION_IAS missing sensitivity and retrigger time settings bug fix;
 *                                   TODO: handle preferences of a type TEXT
 *                                   TODO: add Sensitivity Levels Presets
 *                                   TODO: W.I.P. TS0202_4IN1 refactoring
 *                                   TODO: TS0601_3IN1 - process Battery/USB powerSource change events! (0..4)
 *                                   TODO: when device rejoins the network, read the battry percentage again!
 *                                   TODO: check why only voltage is reported for SONOFF_MOTION_IAS;
 *                                   TODO: hide motionKeepTime and motionSensitivity for SONOFF_MOTION_IAS;
 *                                   TODO: Black Square Radar validateAndFixPreferences: map not found for preference indicatorLight
 *                                   TODO: quickRef
 *                                   TODO: command for black radar LED
 *                                   TODO: TS0225_2AAELWXK_RADAR  dont see an attribute as mentioned that shows the distance at which the motion was detected. - https://community.hubitat.com/t/the-new-tuya-human-presence-sensors-ts0225-tze200-hl0ss9oa-tze200-2aaelwxk-have-actually-5-8ghz-modules-inside/122283/294?u=kkossev
 *                                   TODO: TS0225_2AAELWXK_RADAR led setting not working - https://community.hubitat.com/t/the-new-tuya-human-presence-sensors-ts0225-tze200-hl0ss9oa-tze200-2aaelwxk-have-actually-5-8ghz-modules-inside/122283/294?u=kkossev
 *                                   TODO: do not show errors/warnings for  new settings ie breath , led etc if the preferences setsetare not set and saved - https://community.hubitat.com/t/the-new-tuya-human-presence-sensors-ts0225-tze200-hl0ss9oa-tze200-2aaelwxk-have-actually-5-8ghz-modules-inside/122283/294?u=kkossev
 *                                   TODO: delete all previous preferencies when changing the device profile!
 *                                   TODO: Linptech spammyDPsToIgnore[] !
 *                                   TODO: radars - ignore the change of the presence/motion being turned off when changing parameters for a period of 10 seconds ?
 *                                   TODO: add rtt measurement for ping()
 *                                   TODO: TS0225_HL0SS9OA_RADAR - add presets
 *                                   TOOD: Tuya 2in1 illuminance_interval (dp=102) !
 *                                   TODO: humanMotionState - add preference: enum "disabled", "enabled", "enabled w/ timing" ...; add delayed event
 *                                   TODO: publish examples of SetPar usage : https://community.hubitat.com/t/4-in-1-parameter-for-adjusting-reporting-time/115793/12?u=kkossev
 *                                   TODO: ignore invalid humidity reprots (>100 %)
 *                                   TODO: use getKeepTimeOpts() for processing dp=0x0A (10) keep time ! ( 2-in-1 time is wrong)
 *                                   TODO: add to state 'last battery' the time when the battery was last reported.
 *                                   TODO: check the bindings commands in configure()
 *                                   TODO: implement getActiveEndpoints()
*/

def version() { '1.7.0' }
def timeStamp() { '2024/01/14 10:55 AM' }

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.clusters.iaszone.ZoneStatus
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap

@Field static final Boolean _DEBUG = false
@Field static final Boolean _TRACE_ALL = false      // trace all messages, including the spammy onese

metadata {
    definition(name: 'Tuya Multi Sensor 4 In 1', namespace: 'kkossev', author: 'Krassimir Kossev', importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Multi%20Sensor%204%20In%201/Tuya%20Multi%20Sensor%204%20In%201.groovy', singleThreaded: true ) {
        capability 'Sensor'
        //capability "Configuration"
        capability 'Battery'
        capability 'MotionSensor'
        capability 'TemperatureMeasurement'
        capability 'RelativeHumidityMeasurement'
        capability 'IlluminanceMeasurement'
        capability 'TamperAlert'
        capability 'PowerSource'
        capability 'HealthCheck'
        capability 'Refresh'
        //capability "PushableButton"        // uncomment for TS0202 _TZ3210_cwamkvua [Motion Sensor and Scene Switch]
        //capability "DoubleTapableButton"
        //capability "HoldableButton"

        //attribute "occupancy", "enum", ["occupied", "unoccupied"]   // https://developer.smartthings.com/docs/devices/capabilities/capabilities-reference // https://developer.smartthings.com/capabilities/occupancySensor
        attribute 'all', 'string'
        attribute 'batteryVoltage', 'number'
        attribute 'healthStatus', 'enum', ['offline', 'online']
        attribute 'distance', 'number'              // Tuya Radar
        attribute 'unacknowledgedTime', 'number'    // AIR models
        attribute 'existance_time', 'number'        // BlackSquareRadar & LINPTECH
        attribute 'leave_time', 'number'            // BlackSquareRadar only
        attribute' pushed', 'number'                // TS0202 _TZ3210_cwamkvua [Motion Sensor and Scene Switch]
        attribute 'keepTime', 'enum', ['10 seconds', '30 seconds', '60 seconds', '120 seconds']
        attribute 'sensitivity', 'enum', ['low', 'medium', 'high']

        attribute 'radarSensitivity', 'number'
        attribute 'staticDetectionSensitivity', 'number'    // added 10/29/2023
        attribute 'detectionDelay', 'decimal'
        attribute 'fadingTime', 'decimal'
        attribute 'minimumDistance', 'decimal'
        attribute 'maximumDistance', 'decimal'
        attribute 'radarStatus', 'enum', ['checking', 'check_success', 'check_failure', 'others', 'comm_fault', 'radar_fault']
        attribute 'humanMotionState', 'enum', ['none', 'moving', 'small_move', 'stationary', 'presence', 'peaceful', 'large_move']
        attribute 'radarAlarmMode', 'enum',   ['0 - arm', '1 - off', '2 - alarm', '3 - doorbell']
        attribute 'radarAlarmVolume', 'enum', ['0 - low', '1 - medium', '2 - high', '3 - mute']

        command 'configure', [[name: 'Configure the sensor after switching drivers']]
        command 'initialize', [[name: 'Initialize the sensor after switching drivers.  \n\r   ***** Will load device default values! *****' ]]
        command 'setMotion', [[name: 'setMotion', type: 'ENUM', constraints: ['No selection', 'active', 'inactive'], description: 'Force motion active/inactive (for tests)']]
        command 'refresh',   [[name: 'May work for some DC/mains powered sensors only']]
        command 'setPar', [
                [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']],
                [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']]
        ]
        command 'sendCommand', [[name: 'sendCommand', type: 'STRING', constraints: ['STRING'], description: 'send Tuya Radar commands']]
        if (_DEBUG == true) {
            command 'testTuyaCmd', [
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']],
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']],
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type']
            ]
            command 'testParse', [[name:'val', type: 'STRING', description: 'description', constraints: ['STRING']]]
            command 'test', [[name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']]]
        }

        deviceProfilesV2.each { profileName, profileMap ->
            if (profileMap.fingerprints != null) {
                profileMap.fingerprints.each {
                    fingerprint it
                }
            }
        }
    }

    preferences {
        if (advancedOptions == true || advancedOptions == false) { // Groovy ... :)
            //input (name: "quickref",  type: "hidden", title: "$ttStyleStr<a href='https://htmlpreview.github.io/?https://github.com/kkossev/Hubitat/blob/development/Drivers/Tuya%20Multi%20Sensor%204%20In%201/Tuya_Multi_Sensor_4_In_1.html' target='_blank'>Quick Reference v${version()}</a>")
            input(name: 'txtEnable', type: 'bool',   title: '<b>Description text logging</b>', description: '<i>Display sensor states on HE log page. The recommended value is <b>true</b></i>', defaultValue: true)
            input(name: 'logEnable', type: 'bool',   title: '<b>Debug logging</b>', description: '<i>Debug information, useful for troubleshooting. The recommended value is <b>false</b></i>', defaultValue: true)

            if (('motionReset' in DEVICE?.preferences) && (DEVICE?.preferences.motionReset == true)) {
                input(name: 'motionReset', type: 'bool', title: '<b>Reset Motion to Inactive</b>', description: '<i>Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b></i>', defaultValue: false)
                if (motionReset.value == true) {
                    input('motionResetTimer', 'number', title: '<b>Motion Reset Timer</b>', description: '<i>After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds</i>', range: '0..7200', defaultValue: 60)
                }
            }
            /* groovylint-disable-next-line ConstantIfExpression */
            if (false) {    // TODO!
                input('temperatureOffset', 'decimal', title: 'Temperature offset', description: 'Select how many degrees to adjust the temperature.', range: '-100..100', defaultValue: 0.0)
                input('humidityOffset', 'decimal', title: 'Humidity offset', description: 'Enter a percentage to adjust the humidity.', range: '-50..50',  defaultValue: 0.0)
            }
        }
        if (('reportingTime4in1' in DEVICE?.preferences)) {    // 4in1()
            input('reportingTime4in1', 'number', title: '<b>4-in-1 Reporting Time</b>', description: '<i>4-in-1 Reporting Time configuration, minutes.<br>0 will enable real-time (10 seconds) reporting!</i>', range: '0..7200', defaultValue: DEFAULT_REPORTING_4IN1)
        }
        if (('ledEnable' in DEVICE?.preferences)) {            // 4in1()
            input(name: 'ledEnable', type: 'bool', title: '<b>Enable LED</b>', description: '<i>Enable LED blinking when motion is detected (4in1 only)</i>', defaultValue: true)
        }
        if (advancedOptions == true || advancedOptions == false) {
            if ((DEVICE?.capabilities?.IlluminanceMeasurement == true) && (DEVICE?.preferences.luxThreshold != false)) {
                input('luxThreshold', 'number', title: '<b>Lux threshold</b>', description: 'Minimum change in the lux which will trigger an event', range: '0..999', defaultValue: 5)
                input name: 'illuminanceCoeff', type: 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: '<i>Illuminance correction coefficient, range (0.10..10.00)</i>', range: '0.10..10.00', defaultValue: 1.00
            }
        }
        if (('DistanceMeasurement' in DEVICE?.capabilities)) {
            input(name: 'ignoreDistance', type: 'bool', title: '<b>Ignore distance reports</b>', description: 'If not used, ignore the distance reports received every 1 second!', defaultValue: true)
        }

        // itterate over DEVICE.preferences map and inputIt all!
        (DEVICE.preferences).each { key, value ->
            if (inputIt(key) != null) {
                input inputIt(key)
            }
        }

        /* groovylint-disable-next-line ConstantIfExpression */
        if (false) {
            if ('textLargeMotion' in DEVICE?.preferences) {
                input(name: 'textLargeMotion', type: 'text', title: '<b>Motion Detection Settigs &#8680;</b>', description: '<b>Settings for movement types such as walking, trotting, fast running, circling, jumping and other movements </b>')
            }

            if ('textSmallMotion' in DEVICE?.preferences) {
                input(name: 'textSmallMotion', type: 'text', title: '<b>Small Motion Detection Settigs &#8658;</b>', description: '<b>Settings for small movement types such as tilting the head, waving, raising the hand, flicking the body, playing with the mobile phone, turning over the book, etc.. </b>')
            }
            if ('textStaticDetection' in DEVICE?.preferences) {
                input(name: 'textStaticDetection', type: 'text', title: '<b>Static Detection Settigs &#8680;</b>', description: '<b>The sensor can detect breathing within a certain range to determine people presence in the detection area (for example, while sleeping or reading).</b>')
            }
        }

        input(name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>Enables showing the advanced options/preferences. Hit F5 in the browser to refresh the Preferences list<br>.May not work for all device types!</i>', defaultValue: false)
        if (advancedOptions == true) {
            input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: '<i>Forcely change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!</i>',
                   options: getDeviceProfilesMap())
            if ('Battery' in DEVICE?.capabilities) {
                input(name: 'batteryDelay', type: 'enum', title: '<b>Battery Events Delay</b>', description:'<i>Select the Battery Events Delay<br>(default is <b>no delay</b>)</i>', options: delayBatteryOpts.options, defaultValue: delayBatteryOpts.defaultValue)
            }
            if ('invertMotion' in DEVICE?.preferences) {
                input(name: 'invertMotion', type: 'bool', title: '<b>Invert Motion Active/Not Active</b>', description: '<i>Some Tuya motion sensors may report the motion active/inactive inverted...</i>', defaultValue: false)
            }
        }
        input(name: 'allStatusTextEnable', type:  'bool', title: "<b>Enable 'all' Status Attribute Creation?</b>",  description: '<i>Status attribute for Devices/Rooms</i>', defaultValue: false)
    }
}

@Field static final Boolean _IGNORE_ZCL_REPORTS = true

@Field static final String UNKNOWN =  'UNKNOWN'
@Field static final Map blackRadarLedOptions =      [ '0' : 'Off', '1' : 'On' ]      // HumanPresenceSensorAIR
@Field static final Map TS0225humanMotionState = [ '0': 'none', '1': 'moving', '2': 'small_move', '3': 'stationary'  ]
@Field static String ttStyleStr = '<style>.tTip {display:inline-block;border-bottom: 1px dotted black;}.tTip .tTipText {display:none;border-radius: 6px;padding: 5px 0;position: absolute;z-index: 1;}.tTip:hover .tTipText {display:inline-block;background-color:yellow;color:black;}</style>'

/*
                               LivingRoom    Bedroom    Washroom    Aisle    Kitchen
Motion detection distance    -     800        500        300        900        1000
Motion detection sensitivity -     6x         6x         5x         8x         6x
Small motion detection distance -  600        400        400        400        600
Small motion detection sensitivity 9          6          8          5          8
Static detection distance          600        600        400        0          0
Static detection sensitivity       9x         9x         8x         8x         8x
*/

// TODO - remove all the usused sensitivity and keepTime static maps!
@Field static final Map sensitivityOpts =  [ defaultValue: 2, options: [0: 'low', 1: 'medium', 2: 'high']]
@Field static final Map keepTime4in1Opts = [ defaultValue: 0, options: [0: '10 seconds', 1: '30 seconds', 2: '60 seconds', 3: '120 seconds', 4: '240 seconds', 5: '480 seconds']]
@Field static final Map keepTime2in1Opts = [ defaultValue: 0, options: [0: '10 seconds', 1: '30 seconds', 2: '60 seconds', 3: '120 seconds']]
@Field static final Map keepTime3in1Opts = [ defaultValue: 0, options: [0: '30 seconds', 1: '60 seconds', 2: '120 seconds']]
@Field static final Map keepTimeIASOpts =  [ defaultValue: 0, options: [0: '30 seconds', 1: '60 seconds', 2: '120 seconds']]
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']]
@Field static final Map delayBatteryOpts = [ defaultValue: 0, options: [0: 'No delay', 30: '30 seconds', 3600: '1 hour', 14400: '4 hours', 28800: '8 hours', 43200: '12 hours']]

def getKeepTimeOpts() { return is4in1() ? keepTime4in1Opts : is3in1() ? keepTime3in1Opts : is2in1() ? keepTime2in1Opts : keepTimeIASOpts }

@Field static final Integer presenceCountTreshold = 4
@Field static final Integer defaultPollingInterval = 3600
@Field static final Integer DEFAULT_REPORTING_4IN1 = 5    // time in minutes

def getDeviceGroup()     { state.deviceProfile ?: 'UNKNOWN' }
def getDEVICE()          { deviceProfilesV2[getDeviceGroup()] }
def getDeviceProfiles()      { deviceProfilesV2.keySet() }
def getDeviceProfilesMap()   { deviceProfilesV2.values().description as List<String> }
def is4in1() { return getDeviceGroup().contains('TS0202_4IN1') }
def is3in1() { return getDeviceGroup().contains('TS0601_3IN1') }
def is2in1() { return getDeviceGroup().contains('TS0601_2IN1') }
def isMotionSwitch() { return getDeviceGroup().contains('TS0202_MOTION_SWITCH') }
def isIAS()  { DEVICE?.device?.isIAS == true  }
//def isTS0601_PIR() { (DEVICE.device?.type == "PIR") && (("keepTime" in DEVICE.preferences) || ("sensitivity" in DEVICE.preferences)) }
//def isConfigurable() { return isIAS() }   // TS0202 models ['_TZ3000_mcxw5ehu', '_TZ3000_msl6wxk9']
def getTemperatureDiv() {  isSiHAS() ? 100.0 : 10.0 } // temperatureEvent
def getHumidityDiv()    {  isSiHAS() ? 100.0 : 1.0 }   // humidityEvent

def isZY_M100Radar()               { return getDeviceGroup().contains('TS0601_TUYA_RADAR') }
def isBlackPIRsensor()             { return getDeviceGroup().contains('TS0601_PIR_PRESENCE') }
def isBlackSquareRadar()           { return getDeviceGroup().contains('TS0601_BLACK_SQUARE_RADAR') }
def isHumanPresenceSensorAIR()     { return getDeviceGroup().contains('TS0601_PIR_AIR') }           // isHumanPresenceSensorScene() removed in version 1.6.1
//def isHumanPresenceSensorFall()    { return getDeviceGroup().contains("TS0601_RADAR_MIR-TY-FALL") } // NOT USED ver 1.6.1
def isYXZBRB58radar()              { return getDeviceGroup().contains('TS0601_YXZBRB58_RADAR') }
def isSXM7L9XAradar()              { return getDeviceGroup().contains('TS0601_SXM7L9XA_RADAR') }
def isIJXVKHD0radar()              { return getDeviceGroup().contains('TS0601_IJXVKHD0_RADAR') }
def isHL0SS9OAradar()              { return getDeviceGroup().contains('TS0225_HL0SS9OA_RADAR') }
def is2AAELWXKradar()              { return getDeviceGroup().contains('TS0225_2AAELWXK_RADAR') }    // same as HL0SS9OA, but another set of DPs
def isSBYX0LM6radar()              { return getDeviceGroup().contains('TS0601_SBYX0LM6_RADAR') }
def isLINPTECHradar()              { return getDeviceGroup().contains('TS0225_LINPTECH_RADAR') }
def isEGNGMRZHradar()              { return getDeviceGroup().contains('TS0225_EGNGMRZH_RADAR') }
def isKAPVNNLKradar()              { return getDeviceGroup().contains('TS0601_KAPVNNLK_RADAR') }
def isSONOFF()                     { return getDeviceGroup().contains('SONOFF_SNZB-06P_RADAR') }
def isSiHAS()                      { return getDeviceGroup().contains('SIHAS_USM-300Z_4_IN_1') }

// TODO - check if DPs are declared in the device profiles and remove this function
def isChattyRadarReport(descMap) {
    if ((isZY_M100Radar() || isSBYX0LM6radar()) && (settings?.ignoreDistance == true)) {
        return (descMap?.clusterId == 'EF00' && (descMap.command in ['01', '02']) && descMap.data?.size > 2  && descMap.data[2] == '09')
    }
    else if ((isYXZBRB58radar() || isSXM7L9XAradar()) && (settings?.ignoreDistance == true)) {
        return (descMap?.clusterId == 'EF00' && (descMap.command in ['01', '02']) && descMap.data?.size > 2  && descMap.data[2] == '6D')
    } /*        TODO - check !
    else if (isKAPVNNLKradar() && settings?.ignoreDistance == true) {
        return (descMap?.clusterId == "EF00" && (descMap.command in ["01", "02"]) && descMap.data?.size > 2  && descMap.data[2] == "13")
    }  */
    //    if (isHL0SS9OAradar()) {    // humanMotionState
    //        return (descMap?.clusterId == "EF00" && (descMap.command in ["01", "02"]) && descMap.data?.size > 2  && descMap.data[2] == "0B")
    //    }
    /* groovylint-disable-next-line UnnecessaryElseStatement */
    else {
        return false
    }
}

@Field static final Map deviceProfilesV2 = [
    // is4in1()
    'TS0202_4IN1'  : [
            description   : 'Tuya 4in1 (motion/temp/humi/lux) sensor',
            models        : ['TS0202'],         // model: 'ZB003-X'  vendor: 'Fantem'
            device        : [type: 'PIR', isIAS:true, powerSource: 'dc', isSleepy:false],    // check powerSource
            capabilities  : ['MotionSensor': true, 'TemperatureMeasurement': true, 'RelativeHumidityMeasurement': true, 'IlluminanceMeasurement': true, 'tamper': true, 'Battery': true],
            preferences   : ['motionReset':true, 'reportingTime4in1':true, 'ledEnable':true, 'keepTime':true, 'sensitivity':true],
            commands      : ['reportingTime4in1':'reportingTime4in1', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,EF00', outClusters:'0019,000A', model:'TS0202',  manufacturer:'_TZ3210_zmy9hjay', deviceJoinName: 'Tuya TS0202 Multi Sensor 4 In 1'],        // pairing: double click!
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,EF00', outClusters:'0019,000A', model:'5j6ifxj', manufacturer:'_TYST11_i5j6ifxj', deviceJoinName: 'Tuya TS0202 Multi Sensor 4 In 1'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,EF00', outClusters:'0019,000A', model:'hfcudw5', manufacturer:'_TYST11_7hfcudw5', deviceJoinName: 'Tuya TS0202 Multi Sensor 4 In 1'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,EF00', outClusters:'0019,000A', model:'TS0202',  manufacturer:'_TZ3210_rxqls8v0', deviceJoinName: 'Tuya TS0202 Multi Sensor 4 In 1'],        // not tested
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,EF00', outClusters:'0019,000A', model:'TS0202',  manufacturer:'_TZ3210_wuhzzfqg', deviceJoinName: 'Tuya TS0202 Multi Sensor 4 In 1'],        // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars/92441/282?u=kkossev
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0500,EF00', outClusters:'0019,000A', model:'TS0202',  manufacturer:'_TZ3210_0aqbrnts', deviceJoinName: 'Tuya TS0202 Multi Sensor 4 In 1 is-thpl-zb']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',          type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'inactive', 1:'active'] ,   unit:'',  description:'<i>Motion</i>'],
                // ??? check ^^
                [dp:5,   name:'tamper',          type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'clear', 1:'detected'] ,   unit:'',  description:'<i>Tamper detection</i>'],
                // ??? IAS cluster is used instead? {occupancy: (zoneStatus & 1) > 0, tamper: (zoneStatus & 4) > 0};
                [dp:9,   name:'sensitivity',     type:'enum',    rw: 'rw', min:0,     max:2,    defVal:'2',  unit:'',           map:[0:'low', 1:'medium', 2:'high'], title:'<b>Sensitivity</b>',   description:'<i>PIR sensor sensitivity (update at the time motion is activated)</i>'],
                // check ^^^^^ hasOwnProperty('currentZoneSensitivityLevel')    .read('ssIasZone', ['currentZoneSensitivityLevel', 61441, 'zoneStatus']
                [dp:10,  name:'keepTime',        type:'enum',    rw: 'rw', min:0,     max:5,    defVal:'0',  unit:'seconds',    map:[0:'0 seconds', 1:'30 seconds', 2:'60 seconds', 3:'120 seconds', 4:'240 seconds', 5:'480 seconds'], title:'<b>Keep Time</b>',   description:'<i>PIR keep time in seconds (update at the time motion is activated)</i>'],
                // check ^^^^^  hasOwnProperty('61441')
                [dp:25,  name:'battery2',        type:'number',  rw: 'ro', min:0,     max:100,  defVal:100,  scale:1,  unit:'%',          description:'<i>Remaining battery 2 in %</i>'],
                //            ^^^TODO^^^
                [dp:102, name:'reportingTime4in1', type:'number', rw: 'ro', min:0, max:1440, defVal:10, step:5, scale:1, unit:'minutes', title:'<b>Reporting Interval</b>', description:'<i>Reporting interval in minutes</i>'],
                [dp:104, name:'tempCalibration',  type:'decimal', rw:'ro', min:-2.0,  max:2.0,  defVal:0.0,  scale:10, unit:'deg.',  title:'<b>Temperature Calibration</b>',       description:'<i>Temperature calibration (-2.0...2.0)</i>'],
                //           ^^^TODO^^ pre-process negative values !
                [dp:105, name:'humiCalibration', type:'number',  rw: 'ro', min:-15,   max:15,   defVal:0,    scale:1,  unit:'%RH',    title:'<b>Huidity Calibration</b>',     description:'<i>Humidity Calibration</i>'],
                [dp:106, name:'illumCalibration', type:'number', rw: 'ro', min:-20, max:20, defVal:0,        scale:1, unit:'Lx', title:'<b>Illuminance Calibration</b>', description:'<i>Illuminance calibration in lux/i>'],
                [dp:107, name:'temperature',     type:'decimal', rw: 'ro', min:-20.0, max:80.0, defVal:0.0,  scale:10, unit:'deg.',       description:'<i>Temperature</i>'],
                [dp:108, name:'humidity',        type:'number',  rw: 'ro', min:1,     max:100,  defVal:100,  scale:1,  unit:'%RH',        description:'<i>Humidity</i>'],
                [dp:109, name:'pirSensorEnable', type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'1',  scale:1,  map:[0:'disabled', 1:'enabled'] ,   unit:'', title:'<b>MoPIR Sensor Enable</b>',  description:'<i>Enable PIR sensor</i>'],
                [dp:110, name:'battery',         type:'number',  rw: 'ro', min:0,     max:100,  defVal:100,  scale:1,  unit:'%',          description:'<i>Battery level</i>'],
                //            ^^^TODO^^^
                [dp:111, name:'ledEnable',       type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'disabled', 1:'enabled'] ,   unit:'', title:'<b>LED Enable</b>',  description:'<i>Enable LED</i>'],
                [dp:112, name:'reportingEnable', type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'disabled', 1:'enabled'] ,   unit:'', title:'<b>Reporting Enable</b>',  description:'<i>Enable reporting</i>'],
            ],

            deviceJoinName: 'Tuya Multi Sensor 4 In 1',
            configuration : ['battery': false]
    ],

    // is3in1()
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
            preferences   : ['fadingTime':'102', 'targetDistance':'105'],
            commands      : ['resetStats':'resetStats', 'resetPreferencesToDefaults':'resetPreferencesToDefaults'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_9qayzqa8', deviceJoinName: 'Smart PIR Human Motion Presence Sensor (Black)']    // https://www.aliexpress.com/item/1005004296422003.html
            ],
            tuyaDPs:        [                                           // TODO - defaults !!
                [dp:102, name:'fadingTime',          type:'number',  rw: 'rw', min:24,  max:300 ,  defVal:24,        scale:1,    unit:'seconds',      title:'<b>Fading time</b>',   description:'<i>Fading(Induction) time</i>'],
                [dp:105, name:'targetDistance',      type:'enum',    rw: 'rw', min:0,   max:9 ,    defVal:'6',       scale:1,    map:[0:'0.5 m', 1:'1.0 m', 2:'1.5 m', 3:'2.0 m', 4:'2.5 m', 5:'3.0 m', 6:'3.5 m', 7:'4.0 m', 8:'4.5 m', 9:'5.0 m'] ,   unit:'meters',     title:'<b>Target Distance</b>', description:'<i>Target Distance</i>'],
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
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
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
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_xsm7l9xa', deviceJoinName: 'Tuya Human Presence Detector']                      //

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
            deviceJoinName: 'Tuya Human Presence Detector ZY-M100',
            configuration : [:]
    // status: completed
    ],

    'TS0601_KAPVNNLK_RADAR'   : [        // 24GHz spammy radar w/ battery backup - no illuminance!
            description   : 'Tuya TS0601_KAPVNNLK 24GHz Radar',        // https://www.amazon.com/dp/B0CDRBX1CQ?psc=1&ref=ppx_yo2ov_dt_b_product_details  // https://www.aliexpress.com/item/1005005834366702.html  // https://github.com/Koenkk/zigbee2mqtt/issues/18632
            models        : ['TS0601'],                                // https://www.aliexpress.com/item/1005005858609756.html     // https://www.aliexpress.com/item/1005005946786561.html    // https://www.aliexpress.com/item/1005005946931559.html
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'DistanceMeasurement':true, 'HumanMotionState':true],
            preferences   : ['radarSensitivity':'15', 'fadingTime':'12', 'maximumDistance':'13', 'smallMotionDetectionSensitivity':'16'],
            commands      : ['resetStats':'resetStats', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE204_kapvnnlk', deviceJoinName: 'Tuya 24 GHz Human Presence Detector NEW']           // https://community.hubitat.com/t/tuya-smart-human-presence-sensor-micromotion-detect-human-motion-detector-zigbee-ts0601-tze204-sxm7l9xa/111612/71?u=kkossev
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',              type:'enum',    rw: 'ro', min:0,   max:1 ,    defVal:'0',  scale:1,   map:[0:'inactive', 1:'active'] ,   unit:'',     title:'<b>Presence state</b>', description:'<i>Presence state</i>'],
                [dp:11,  name:'humanMotionState',    type:'enum',    rw: 'ro', min:0,   max:2,     defVal:'0', map:[0:'none', 1:'small_move', 2:'large_move'],  description:'Human motion state'],        // "none", "small_move", "large_move"]
                [dp:12,  name:'fadingTime',          type:'number',  rw: 'rw', min:3,   max:600,   defVal:60,   scale:1,   unit:'seconds',    title:'<b>Fading time</b>',                description:'<i>Presence inactivity delay timer</i>'],                                  // aka 'nobody time'
                [dp:13,  name:'maximumDistance',     type:'decimal', rw: 'rw', min:1.5, max:6.0,   defVal:4.0, step:75, scale:100, unit:'meters',     title:'<b>Maximum detection distance</b>', description:'<i>Maximum (far) detection distance</i>'],  // aka 'Large motion detection distance'
                [dp:15,  name:'radarSensitivity',    type:'number',  rw: 'rw', min:0,   max:7 ,    defVal:5,    scale:1,   unit:'',          title:'<b>Radar sensitivity</b>',          description:'<i>Large motion detection sensitivity of the radar</i>'],
                [dp:16 , name:'smallMotionDetectionSensitivity', type:'number',  rw: 'rw', min:0,   max:7,  defVal:5,     scale:1,   unit:'', title:'<b>Small motion sensitivity</b>',   description:'<i>Small motion detection sensitivity</i>'],
                [dp:19,  name:'distance',            type:'decimal', rw: 'ro', min:0.0, max:10.0,  defVal:0.0,  scale:100, unit:'meters',     title:'<b>Distance</b>',                   description:'<i>detected distance</i>'],
                [dp:101, name:'batteryLevel',        type:'number',  rw: 'ro', min:0,   max:100,   defVal:100,  scale:1,   unit:'%',          title:'<b>Battery level</b>',              description:'<i>Battery level</i>']
            ],
            spammyDPsToIgnore : [19],
            spammyDPsToNotTrace : [19],
            deviceJoinName: 'Tuya 24 GHz Human Presence Detector NEW',
            configuration : [:]
    ],

    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/f277bef2f84d50aea70c25261db0c2ded84b7396/src/devices/tuya.ts#L4164
    'TS0601_RADAR_MIR-HE200-TY'   : [        // Human presence sensor radar 'MIR-HE200-TY' - illuminance, presence, occupancy, motion_speed, motion_direction, radar_sensitivity, radar_scene ('default', 'area', 'toilet', 'bedroom', 'parlour', 'office', 'hotel')
            description   : 'Tuya Human Presence Sensor MIR-HE200-TY',
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true],
            preferences   : ['radarSensitivity':'2', 'tumbleSwitch':'105', 'tumbleAlarmTime':'106', /*"radarScene":"112",*/ 'fallSensitivity':'118'],
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
            deviceJoinName: 'Tuya Human Presence Sensor MIR-HE200-TY',
            configuration : [:]
    ],

    'TS0601_BLACK_SQUARE_RADAR'   : [        // // 24GHz Big Black Square Radar w/ annoying LED    // isBlackSquareRadar()
            description   : 'Tuya Black Square Radar',
            models        : ['TS0601'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor':true],
            preferences   : ['indicatorLight':103],
            commands      : ['resetStats':'resetStats'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_0u3bj3rc', deviceJoinName: '24GHz Black Square Human Presence Radar w/ LED'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_v6ossqfy', deviceJoinName: '24GHz Black Square Human Presence Radar w/ LED'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_mx6u6l4y', deviceJoinName: '24GHz Black Square Human Presence Radar w/ LED']
            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',         type:'enum',   rw: 'ro', min:0, max:1,    defVal: '0', map:[0:'inactive', 1:'active'],     description:'Presence'],
                [dp:101, name:'existance_time', type:'number', rw: 'ro', min:0, max:9999, scale:1,   unit:'minutes',    description:'Shows the presence duration in minutes'],
                [dp:102, name:'leave_time',     type:'number', rw: 'ro', min:0, max:9999, scale:1,   unit:'minutes',    description:'Shows the duration of the absence in minutes'],
                [dp:103, name:'indicatorLight', type:'enum',   rw: 'rw', min:0, max:1,    defVal: '0', map:[0:'OFF', 1:'ON'],  title:'<b>Indicator Light</b>', description:'<i>Turns the onboard LED on or off</i>']
            ],
            spammyDPsToIgnore : [103],                    // we don't need to know the LED status every 4 seconds!
            spammyDPsToNotTrace : [1, 101, 102, 103],     // very spammy device - 4 packates are sent every 4 seconds!
            deviceJoinName: '24GHz Black Square Human Presence Radar w/ LED',
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
            spammyDPsToIgnore : [105],
            spammyDPsToNotTrace : [105],
            deviceJoinName: 'Tuya Human Presence Detector YXZBRB58',    // https://www.aliexpress.com/item/1005005764168560.html
            configuration : [:]
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
            spammyDPsToIgnore : [109],
            spammyDPsToNotTrace : [109],
            deviceJoinName: 'Tuya Human Presence Detector SXM7L9XA',
            configuration : [:],
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
            deviceJoinName: 'Tuya Human Presence Detector ZY-M100-24G',
            configuration : [:]
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
            spammyDPsToIgnore : [19],
            spammyDPsToNotTrace : [19],
            deviceJoinName: 'Tuya Human Presence Detector YENSYA2C',
            configuration : [:]
    ],

    // the new 5.8 GHz radar w/ humanMotionState and a lot of configuration options, 'not-so-spammy' !   - pedestal mount form-factor
    'TS0225_HL0SS9OA_RADAR'   : [
            description   : 'Tuya TS0225_HL0SS9OA Radar',        // https://www.aliexpress.com/item/1005005761971083.html
            models        : ['TS0225'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true],
            preferences   : ['presenceKeepTime':'12', 'ledIndicator':'24', 'radarAlarmMode':'105', 'radarAlarmVolume':'102', 'radarAlarmTime':'101', \
                             /*"textLargeMotion":"NONE",*/ 'motionFalseDetection':'112', 'motionDetectionSensitivity':'15', 'motionMinimumDistance':'106', 'motionDetectionDistance':'13', \
                             /*"textSmallMotion":"NONE",*/ 'smallMotionDetectionSensitivity':'16', 'smallMotionMinimumDistance':'107', 'smallMotionDetectionDistance':'14', \
                             /*"textStaticDetection":"NONE",*/ 'breatheFalseDetection':'115', 'staticDetectionSensitivity':'104', 'staticDetectionMinimumDistance':'108', 'staticDetectionDistance':'103' \
                            ],
            commands      : ['resetSettings':'resetSettings', 'moveSelfTest':'moveSelfTest', 'smallMoveSelfTest':'smallMoveSelfTest', 'breatheSelfTest':'breatheSelfTest',  \
                             'resetStats':'resetStats', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences'
            ],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,E002,EF00', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZE200_hl0ss9oa', deviceJoinName: 'Tuya TS0225_HL0SS9OA Human Presence Detector']       // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
            ],
            tuyaDPs:        [        // W.I.P - use already defined DPs and preferences !!  TODO - verify teh default values !
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
                [dp:116, name:'existance_time',                  type:'number',  rw: 'ro', min:0,    max:60 ,   scale:1,   unit:'seconds',   description:'Radar presence duration'],    // not received
                [dp:117, name:'leave_time',                      type:'number',  rw: 'ro', min:0,    max:60 ,   scale:1,   unit:'seconds',   description:'Radar absence duration'],     // not received
                [dp:118, name:'radarDurationStatus',             type:'number',  rw: 'ro', min:0,    max:60 ,   scale:1,   unit:'seconds',   description:'Radar duration status']       // not received
            ],
            spammyDPsToIgnore : [],
            spammyDPsToNotTrace : [11],
            deviceJoinName: 'Tuya TS0225_HL0SS9OA Human Presence Detector',
            configuration : [:]
    ],

    // the new 5.8GHz radar w/ humanMotionState and a lot of configuration options, 'not-so-spammy' !   - wall mount form-factor    is2AAELWXKradar()
    'TS0225_2AAELWXK_RADAR'   : [                                     // https://github.com/Koenkk/zigbee2mqtt/issues/18612
            description   : 'Tuya TS0225_2AAELWXK 5.8 GHz Radar',        // https://community.hubitat.com/t/the-new-tuya-24ghz-human-presence-sensor-ts0225-tze200-hl0ss9oa-finally-a-good-one/122283/72?u=kkossev
            models        : ['TS0225'],                                // ZG-205Z   https://github.com/Koenkk/zigbee-herdsman-converters/blob/38bf79304292c380dc8366966aaefb71ca0b03da/src/devices/tuya.ts#L4793
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'HumanMotionState':true],
            // TODO - preferences and DPs !!!!!!!!!!!!!!!!!!!!
            preferences   : ['presenceKeepTime':'102', 'ledIndicator':'107', 'radarAlarmMode':'117', 'radarAlarmVolume':'116', 'radarAlarmTime':'115', \
                             /*"textLargeMotion":"NONE",*/ 'motionFalseDetection':'103', 'motionDetectionSensitivity':'2', 'motionMinimumDistance':'3', 'motionDetectionDistance':'4', \
                             /*"textSmallMotion":"NONE",*/ 'smallMotionDetectionSensitivity':'105', 'smallMotionMinimumDistance':'110', 'smallMotionDetectionDistance':'104', \
                             /*"textStaticDetection":"NONE",*/ 'breatheFalseDetection':'113', 'staticDetectionSensitivity':'109', /*"staticDetectionMinimumDistance":'108',*/ 'staticDetectionDistance':'108' \
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
                /*
                [dp:116, name:'existance_time',                  type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar presence duration'],    // not received
                [dp:117, name:'leave_time',                      type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar absence duration'],     // not received
                [dp:118, name:'radarDurationStatus',             type:"number",  rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   description:'Radar duration status']       // not received
                */
            ],
            deviceJoinName: 'Tuya TS0225_2AAELWXK 5.8 Ghz Human Presence Detector',
            configuration : [:]
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
            spammyDPsToIgnore : [9],
            spammyDPsToNotTrace : [9],
            deviceJoinName: 'Tuya Human Presence Detector SBYX0LM6',
            configuration : [:]
    ],

    // isLINPTECHradar()
    'TS0225_LINPTECH_RADAR'   : [                                      // https://github.com/Koenkk/zigbee2mqtt/issues/18637
            description   : 'Tuya TS0225_LINPTECH 24GHz Radar',        // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/646?u=kkossev
            models        : ['TS0225'],
            device        : [type: 'radar', powerSource: 'dc', isSleepy:false],
            capabilities  : ['MotionSensor': true, 'IlluminanceMeasurement': true, 'DistanceMeasurement':true],
            preferences   : ['fadingTime':'101', 'motionDetectionDistance':'0xE002:0xE00B', 'motionDetectionSensitivity':'0xE002:0xE004', 'staticDetectionSensitivity':'0xE002:0xE005'],
            fingerprints  : [                                          // https://www.amazon.com/dp/B0C7C6L66J?ref=ppx_yo2ov_dt_b_product_details&th=1
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,E002,4000,EF00,0500', outClusters:'0019,000A', model:'TS0225', manufacturer:'_TZ3218_awarhusb', deviceJoinName: 'Tuya TS0225_LINPTECH 24Ghz Human Presence Detector']       // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs:       [                                           // the tuyaDPs revealed from iot.tuya.com are actually not used by the device! The only exception is dp:101
                [dp:101,              name:'fadingTime',                      type:'number',                rw: 'rw', min:1,    max:9999, defVal:10,    scale:1,   unit:'seconds', title: '<b>Fading time</b>', description:'<i>Presence inactivity timer, seconds</i>']                                  // aka 'nobody time'
            ],
            attributes:       [                                        // LINPTECH / MOES are using a custom cluster 0xE002 for the settings (except for the fadingTime), ZCL cluster 0x0400 for illuminance (malformed reports!) and the IAS cluster 0x0500 for motion detection
                [at:'0xE002:0xE001',  name:'existance_time',                  type:'number',  dt: 'UINT16', rw: 'ro', min:0,    max:65535,  scale:1,    unit:'minutes',   title: '<b>Existance time/b>',                 description:'<i>existance (presence) time, recommended value is > 10 seconds!</i>'],                    // aka Presence Time
                [at:'0xE002:0xE004',  name:'motionDetectionSensitivity',      type:'enum',    dt: 'UINT8',  rw: 'rw', min:1,    max:5,      defVal:'4',    scale:1,   map:[1: '1 - low', 2: '2 - medium low', 3: '3 - medium', 4: '4 - medium high', 5: '5 - high'], unit:'',         title: '<b>Motion Detection Sensitivity</b>',  description:'<i>Large motion detection sensitivity</i>'],           // aka Motionless Detection Sensitivity
                [at:'0xE002:0xE005',  name:'staticDetectionSensitivity',      type:'enum',    dt: 'UINT8',  rw: 'rw', min:1,    max:5,      defVal:'3',    scale:1,   map:[1: '1 - low', 2: '2 - medium low', 3: '3 - medium', 4: '4 - medium high', 5: '5 - high'], unit:'',         title: '<b>Static Detection Sensitivity</b>',  description:'<i>Static detection sensitivity</i>'],                 // aka Motionless Detection Sensitivity
                [at:'0xE002:0xE00A',  name:'distance',                        type:'decimal', dt: 'UINT16', rw: 'ro', min:0.0,  max:6.0,    defVal:0.0,    scale:100,  unit:'meters',            title: '<b>Distance</b>',                      description:'<i>Measured distance</i>'],                            // aka Current Distance
                [at:'0xE002:0xE00B', name:'motionDetectionDistance', type:'enum', dt: 'UINT16', rw: 'rw', min:0.75, max:6.0, defVal:'4.50', step:75, scale:100, map:['0.75': '0.75 meters', '1.50': '1.50 meters', '2.25': '2.25 meters', '3.00': '3.00 meters', '3.75': '3.75 meters', '4.50': '4.50 meters', '5.25': '5.25 meters', '6.00' : '6.00 meters'], unit:'meters', title: '<b>Motion Detection Distance</b>', description:'<i>Large motion detection distance, meters</i>']               // aka Far Detection
            ],
            spammyDPsToIgnore : [19],       // TODO
            spammyDPsToNotTrace : [19],     // TODO
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
                [dp:103, name:'distance',           type:'decimal', rw: 'ro', min:0.0,  max:10.0,  defVal:0.0, scale:10,  unit:'meters'],
                [dp:104, name:'unknown 104 0x68',            type:'number',  rw: 'ro'],    //68
                [dp:105, name:'unknown 105 0x69',            type:'number',  rw: 'ro'],    //69
                [dp:109, name:'unknown 109 0x6D',            type:'number',  rw: 'ro'],    //6D
                [dp:110, name:'unknown 110 0x6E',            type:'number',  rw: 'ro'],    //6E
                [dp:111, name:'unknown 111 0x6F',            type:'number',  rw: 'ro'],    //6F
                [dp:114, name:'unknown 114 0x72',            type:'number',  rw: 'ro'],    //72
                [dp:115, name:'unknown 115 0x73',            type:'number',  rw: 'ro'],    //73
                [dp:116, name:'unknown 116 0x74',            type:'number',  rw: 'ro'],    //74
                [dp:118, name:'unknown 118 0x76',            type:'number',  rw: 'ro'],    //76
                [dp:119, name:'unknown 119 0x77',            type:'number',  rw: 'ro']     //77
            ],
            spammyDPsToIgnore : [103],
            spammyDPsToNotTrace : [103],
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
            //spammyDPsToIgnore : [182],
            //spammyDPsToNotTrace : [182],
            deviceJoinName: 'Aubess Human Presence Detector O7OE4N9A',
            configuration : [:]
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
            preferences   : ['fadingTime':'0x0406:0x0020', 'radarSensitivity':'0x0406:0x0022'],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0406,0500,FC57,FC11', outClusters:'0003,0019', model:'SNZB-06P', manufacturer:'SONOFF', deviceJoinName: 'SONOFF SNZB-06P RADAR']      // https://community.hubitat.com/t/sonoff-zigbee-human-presence-sensor-snzb-06p/126128/14?u=kkossev
            ],
            attributes:       [
                [at:'0x0406:0x0022', name:'radarSensitivity', type:'enum',   rw: 'rw', min:1, max:3,    defVal:'2',  unit:'',           map:[1:'low', 2:'medium', 3:'high'], title:'<b>Radar Sensitivity</b>',   description:'<i>Radar Sensitivity</i>'],
                [at:'0x0406:0x0020', name:'fadingTime',       type:'enum',   rw: 'rw', min:10, max:999, defVal:'60', unit:'seconds',    map:[10:'10 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'<i>Radar fading time in seconds</i>'],
            ],
            deviceJoinName: 'SONOFF SNZB-06P RADAR',
            configuration : ['0x0406':'bind', '0x0FC57':'bind'/*, "0xFC11":"bind"*/]
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

// ---------------------------------- deviceProfilesV2 helper functions --------------------------------------------

/**
 * Returns the profile key for a given profile description.
 * @param valueStr The profile description to search for.
 * @return The profile key if found, otherwise null.
 */
def getProfileKey(String valueStr) {
    def key = null
    deviceProfilesV2.each {  profileName, profileMap ->
        if (profileMap.description == valueStr) {
            key = profileName
        }
    }
    return key
}

/**
 * Finds the preferences map for the given parameter.
 * @param param The parameter to find the preferences map for.
 * @param debug Whether or not to output debug logs.
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found
 * @return null if param is not defined for this device.
 */
def getPreferencesMap(String param, boolean debug=false) {
    Map foundMap = [:]
    if (!(param in DEVICE.preferences)) {
        if (debug) { log.warn "getPreferencesMap: preference ${param} not defined for this device!" }
        return null
    }
    def preference
    try {
        preference = DEVICE.preferences["$param"]
        if (debug) log.debug "getPreferencesMap: preference ${param} found. value is ${preference}"
        if (preference in [true, false]) {
            // find the preference in the tuyaDPs map
            logDebug "getPreferencesMap: preference ${param} is boolean"
            return null     // no maps for predefined preferences !
        }
        if (preference.isNumber()) {
            // find the preference in the tuyaDPs map
            int dp = safeToInt(preference)
            def dpMaps   =  DEVICE.tuyaDPs
            foundMap = dpMaps.find { it.dp == dp }
        }
        else { // cluster:attribute
            if (debug) log.trace "${DEVICE.attributes}"
            //def dpMaps   =  DEVICE.tuyaDPs
            foundMap = DEVICE.attributes.find { it.at == preference }
        }
    // TODO - could be also 'true' or 'false' ...
    } catch (Exception e) {
        if (debug) log.warn "getPreferencesMap: exception ${e} caught when getting preference ${param} !"
        return null
    }
    if (debug) log.debug "getPreferencesMap: foundMap = ${foundMap}"
    return foundMap
}

/**
 * Resets the device preferences to their default values.
 * @param debug A boolean indicating whether to output debug information.
 */
def resetPreferencesToDefaults(boolean debug=false) {
    Map preferences = DEVICE?.preferences
    if (preferences == null) {
        logDebug 'Preferences not found!'
        return
    }
    Map parMap = [:]
    preferences.each { parName, mapValue ->
        if (debug) log.trace "$parName $mapValue"
        // TODO - could be also 'true' or 'false' ...
        if (mapValue in [true, false]) {
            logDebug "Preference ${parName} is predefined -> (${mapValue})"
            // TODO - set the predefined value
            /*
            if (debug) log.info "par ${parName} defVal = ${parMap.defVal}"
            device.updateSetting("${parMap.name}",[value:parMap.defVal, type:parMap.type])
            */
            return // continue
        }
        // find the individual preference map
        parMap = getPreferencesMap(parName, false)
        if (parMap == null) {
            logDebug "Preference ${parName} not found in tuyaDPs or attributes map!"
            return // continue
        }
        // parMap = [at:0xE002:0xE005, name:staticDetectionSensitivity, type:number, dt:UINT8, rw:rw, min:0, max:5, scale:1, unit:x, title:Static Detection Sensitivity, description:Static detection sensitivity]
        if (parMap.defVal == null) {
            logDebug "no default value for preference ${parName} !"
            return // continue
        }
        if (debug) log.info "par ${parName} defVal = ${parMap.defVal}"
        device.updateSetting("${parMap.name}", [value:parMap.defVal, type:parMap.type])
    }
    logInfo 'Preferences reset to default values'
}

@Field static final Map<Integer, String> IAS_ATTRIBUTES = [
    //  Zone Information
    0x0000: 'zone state',
    0x0001: 'zone type',
    0x0002: 'zone status',
    //  Zone Settings
    0x0010: 'CIE addr',    // EUI64
    0x0011: 'Zone Id',     // uint8
    0x0012: 'Num zone sensitivity levels supported',     // uint8
    0x0013: 'Current zone sensitivity level',            // uint8
    0xF001: 'Current zone keep time'                     // enum8 ?
]

@Field static final Map<Integer, String> ZONE_TYPE = [
    0x0000: 'Standard CIE',
    0x000D: 'Motion Sensor',
    0x0015: 'Contact Switch',
    0x0028: 'Fire Sensor',
    0x002A: 'Water Sensor',
    0x002B: 'Carbon Monoxide Sensor',
    0x002C: 'Personal Emergency Device',
    0x002D: 'Vibration Movement Sensor',
    0x010F: 'Remote Control',
    0x0115: 'Key Fob',
    0x021D: 'Key Pad',
    0x0225: 'Standard Warning Device',
    0x0226: 'Glass Break Sensor',
    0x0229: 'Security Repeater',
    0xFFFF: 'Invalid Zone Type'
]

@Field static final Map<Integer, String> ZONE_STATE = [
    0x00: 'Not Enrolled',
    0x01: 'Enrolled'
]

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
    Map descMap = [:]
    checkDriverVersion()
    if (state.rxCounter != null) state.rxCounter = state.rxCounter + 1
    setPresent()
    //logDebug "parse (${device.getDataValue('manufacturer')}, ${driverVersionAndTimeStamp()}) description = ${description}"
    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) {
        logDebug "parse: zone status: $description"
        if ((isHL0SS9OAradar() || is2AAELWXKradar()) && _IGNORE_ZCL_REPORTS == true) {
            logDebug 'ignored IAS zone status'
            return
        }
        parseIasMessage(description)    // TS0202 Motion sensor
    }
    else if (description?.startsWith('enroll request')) {
        logDebug "parse: enroll request: $description"
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */
        if (settings?.logEnable) log.info "${device.displayName} Sending IAS enroll response..."
        ArrayList<String> cmds = zigbee.enrollResponse(300) + zigbee.readAttribute(0x0500, 0x0000, [:], delay=201)
        logDebug "enroll response: ${cmds}"
        sendZigbeeCommands(cmds)
    }
    else if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        try  {
            descMap = myParseDescriptionAsMap(description)
        }
        catch (e) {
            logDebug "parse: exception '${e}' caught while processing description ${description}"
            return
        }
        if ((isChattyRadarReport(descMap) || isSpammyDPsToIgnore(descMap)) && (_TRACE_ALL != true))  {
            // do not even log these spammy distance reports ...
            return
        }
        //
        if (!isSpammyDPsToNotTrace(descMap) || (_TRACE_ALL == true)) {
            logDebug "parse: (${device.getDataValue('manufacturer')}, ${(getDeviceGroup())}, ${driverVersionAndTimeStamp()}) descMap = ${descMap} description = ${description}"
        }
        //
        if (descMap.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
            if (descMap.attrInt == 0x0021) {
                getBatteryPercentageResult(Integer.parseInt(descMap.value, 16))
            } else if (descMap.attrInt == 0x0020) {
                sendBatteryVoltageEvent(Integer.parseInt(descMap.value, 16))
            }
            else {
                logDebug "power cluster not parsed attrint $descMap.attrInt"
            }
        }
        else if (descMap.cluster == '0400' && descMap.attrId == '0000') {
            def rawLux = Integer.parseInt(descMap.value, 16)
            if ((isHL0SS9OAradar() || is2AAELWXKradar()) && _IGNORE_ZCL_REPORTS == true) {
                logDebug "ignored ZCL illuminance report (raw:Lux=${rawLux})"
                return
            }
            if (isSiHAS()) {
                logDebug "parse: illuminanceEventLux report (raw:Lux=${rawLux})"
                illuminanceEventLux(rawLux)
            }
            else {
                logDebug "parse: illuminanceEvent report (raw:Lux=${rawLux})"
                illuminanceEvent(rawLux)
            }
        }
        else if (descMap.cluster == '0402' && descMap.attrId == '0000') {
            def raw = Integer.parseInt(descMap.value, 16)
            temperatureEvent(raw / getTemperatureDiv())
        }
        else if (descMap.cluster == '0405' && descMap.attrId == '0000') {
            def raw = Integer.parseInt(descMap.value, 16)
            humidityEvent(raw / getHumidityDiv())
        }
        else if (descMap.cluster == '0406')  {    // OWON and SONOFF
            if (descMap.attrId == '0000') {
                def raw = Integer.parseInt(descMap.value, 16)
                handleMotion(raw & 0x01)
            }
            else if (descMap.attrId == '0020') {
                def value = zigbee.convertHexToInt(descMap.value)
                sendEvent('name': 'fadingTime', 'value': value, 'unit': 'seconds', 'type': 'physical', 'descriptionText': "fading time is ${value} seconds")
                logDebug "Cluster ${descMap.cluster} Attribute ${descMap.attrId} (fadingTime) value is ${value} (0x${descMap.value} seconds)"
            }
            else if (descMap.attrId == '0022') {
                def value = zigbee.convertHexToInt(descMap.value)
                sendEvent('name': 'radarSensitivity', 'value': value, 'unit': '', 'type': 'physical', 'descriptionText': "radar sensitivity is ${value}")
                logDebug "Cluster ${descMap.cluster} Attribute ${descMap.attrId} (radarSensitivity) value is ${value} (0x${descMap.value})"
            }
            else {
                logDebug "UNPROCESSED Cluster ${descMap.cluster} Attribute ${descMap.attrId} value is ${descMap.value} (0x${descMap.value})"
            }
        }
        else if (descMap?.clusterInt == CLUSTER_TUYA) {
            processTuyaCluster(descMap)
        }
        else if (descMap.cluster  == 'E002') {
            processE002Cluster(descMap)
        }
        else if (descMap.profileId == '0000') {    // zdo
            parseZDOcommand(descMap)
        }
        else if (descMap?.cluster == '0000' && descMap?.attrId == '0001') {
            if (settings?.logEnable) log.info "${device.displayName} Tuya check-in (application version is ${descMap?.value})"
            def application = device.getDataValue('application')
            if (application == null || (application as String) != (descMap?.value as String)) {
                device.updateDataValue('application', descMap?.value)
                logInfo "application version set to ${descMap?.value}"
                updateTuyaVersion()
            }
        }
        else if (descMap?.cluster == '0000' && descMap?.attrId == '0004') {
            if (settings?.logEnable) log.info "${device.displayName} received device manufacturer ${descMap?.value}"
        }
        else if (descMap?.cluster == '0000' && descMap?.attrId == '0007') {
            //def value = descMap?.value == "00" ? "battery" : descMap?.value == "01" ? "mains" : descMap?.value == "03" ? "battery" : descMap?.value == "04" ? "dc" : "unknown"
            def powerSourceReported = powerSourceOpts.options[descMap?.value as int]
            logInfo "reported Power source <b>${powerSourceReported}</b> (${descMap?.value})"
            // the powerSource reported by the device is very often not correct ...
            if (DEVICE.device?.powerSource != null) {
                powerSourceReported = DEVICE.device?.powerSource
                logDebug "forcing the powerSource to <b>${powerSourceReported}</b>"
            }
            else if (is4in1() || ((DEVICE.device?.type == 'radar') || isHumanPresenceSensorAIR() || isBlackPIRsensor()))  {     // for radars force powerSource 'dc'
                powerSourceReported = powerSourceOpts.options[04]    // force it to dc !
            }
            powerSourceEvent(powerSourceReported)
        }
        else if (descMap?.cluster == '0000' && descMap?.attrId == 'FFDF') {
            logDebug "Tuya check-in (cluster revision=${descMap?.value})"
        }
        else if (descMap?.cluster == '0000' && descMap?.attrId == 'FFE2') {
            logDebug "Tuya AppVersion is ${descMap?.value}"
        }
        else if (descMap?.cluster == '0000' && (descMap?.attrId in ['FFE0', 'FFE1', 'FFE3', 'FFE4'])) {
            logDebug "Tuya unknown attribute ${descMap?.attrId} value is ${descMap?.value}"
        }
        else if (descMap?.cluster == '0000' && descMap?.attrId == 'FFFE') {
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value is ${descMap?.value}"
        }
        else if (descMap?.cluster == '0500' && descMap?.command in ['01', '0A']) {    //IAS read attribute response
            //if (settings?.logEnable) log.debug "${device.displayName} IAS read attribute ${descMap?.attrId} response is ${descMap?.value}"
            if (descMap?.attrId == '0000') {
                def value = Integer.parseInt(descMap?.value, 16)
                logInfo "IAS Zone State repot is '${ZONE_STATE[value]}' (${value})"
            } else if (descMap?.attrId == '0001') {
                def value = Integer.parseInt(descMap?.value, 16)
                logInfo "IAS Zone Type repot is '${ZONE_TYPE[value]}' (${value})"
            } else if (descMap?.attrId == '0002') {
                logDebug "IAS Zone status repoted: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}"
                handleMotion(Integer.parseInt(descMap?.value, 16))
            } else if (descMap?.attrId == '0010') {
                logDebug "IAS Zone Address received (bitmap = ${descMap?.value})"
            } else if (descMap?.attrId == '0011') {
                logDebug "IAS Zone ID: ${descMap.value}"
            } else if (descMap?.attrId == '0012') {
                logDebug "IAS Num zone sensitivity levels supported: ${descMap.value}"
            } else if (descMap?.attrId == '0013') {
                def value = Integer.parseInt(descMap?.value, 16)
                logInfo "IAS Current Zone Sensitivity Level = ${sensitivityOpts.options[value]} (${value})"
                device.updateSetting('settings.sensitivity', [value:value.toString(), type:'enum'])
            }
            else if (descMap?.attrId == 'F001') {    // [raw:7CC50105000801F02000, dni:7CC5, endpoint:01, cluster:0500, size:08, attrId:F001, encoding:20, command:0A, value:00, clusterInt:1280, attrInt:61441]
                def value = Integer.parseInt(descMap?.value, 16)
                def str   = getKeepTimeOpts().options[value]
                logInfo "Current IAS Zone Keep-Time =  ${str} (${value})"
                device.updateSetting('keepTime', [value: value.toString(), type: 'enum'])
            }
            else {
                logDebug "Zone status attribute ${descMap?.attrId}: NOT PROCESSED ${descMap}"
            }
        } // if IAS read attribute response
        else if (descMap?.clusterId == '0500' && descMap?.command == '04') {    //write attribute response (IAS)
            logDebug "IAS write attribute response is ${descMap?.data[0] == '00' ? 'success' : '<b>FAILURE</b>'}"
        }
        else if (descMap?.cluster == 'FC11' && descMap?.command in ['01', '0A']) {
            //  descMap = [raw:0DD001FC110801202001, dni:0DD0, endpoint:01, cluster:FC11, size:08, attrId:2001, encoding:20, command:0A, value:01, clusterInt:64529, attrInt:8193]
            if (descMap?.attrId == '2001') {
                logDebug "FC11 attribute 2001 value is ${descMap?.value}"
            //occupancyEvent( Integer.parseInt(descMap?.value, 16) )
            }
            else {
                logDebug "FC11 attribute ${descMap?.attrId}: NOT PROCESSED descMap=${descMap}"
            }
        }
        else if (descMap?.command == '04') {    // write attribute response (other)
            logDebug "write attribute response is ${descMap?.data[0] == '00' ? 'success' : '<b>FAILURE</b>'}"
        }
        else if (descMap?.command == '0B') {    // default command response
            String commandId = descMap.data[0]
            String status = "0x${descMap.data[1]}"
            logDebug "zigbee default command response cluster: ${clusterLookup(descMap.clusterInt)} command: 0x${commandId} status: ${descMap.data[1] == '00' ? 'success' : '<b>FAILURE</b>'} (${status})"
        }
        else if (descMap?.command == '00' && descMap?.clusterId == '8021') {    // bind response
            logDebug "bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'success' : '<b>FAILURE</b>'})"
        }
        else {
            logDebug "<b>NOT PARSED </b> : descMap = ${descMap} description = ${description}"
        }
    } // if 'catchall:' or 'read attr -'
    else if (description.startsWith('raw')) {
        // description=raw:11E201E0020A0AE0219F00, dni:11E2, endpoint:01, cluster:E002, size:0A, attrId:E00A, encoding:21, command:0A, value:009F, clusterInt:57346, attrInt:57354
        /*
        descMap = [:]
        descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry ->
            def pair = entry.split(':')
            [(pair.first().trim()): pair.last().trim()]
        }
        */
        descMap = myParseDescriptionAsMap(description)
        logDebug "parsed raw : cluster=${descMap.cluster} clusterId=${descMap.clusterId} attrId=${descMap.attrId} descMap=${descMap}"
        if (descMap != null) {
            if (descMap.cluster  ==  'E002') {      // TODO !!!!!!!!!!! check if this is correct :  cluster vs clusterId
                processE002Cluster(descMap)
            }
            else if (descMap.profileId == '0000') {    // zdo
                parseZDOcommand(descMap)
            }
            if (descMap.cluster  ==  '0005') {
                logDebug "<b>unprocessed cluster ${descMap.cluster}</b> description = ${description} descMap=${descMap}"
            }
            else {
                logWarn "<b>UNPROCESSED RAW cluster ${descMap.cluster}</b> description = ${description} descMap=${descMap}"
            }
        }
        else {
            logWarn "<b>CAN NOT PARSE RAW cluster</b> description = ${description} descMap=${descMap}"
        }
    }
    else {
        logDebug "<b> UNPROCESSED </b> description = ${description} descMap = ${zigbee.parseDescriptionAsMap(description)}"
    }
}

Map myParseDescriptionAsMap(String description) {
    def descMap = [:]
    try {
        descMap = zigbee.parseDescriptionAsMap(description)
        return descMap    // all OK!
    }
    catch (e1) {
        logDebug "exception ${e1} caught while processing parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}"
        // try alternative custom parsing
        descMap = [:]
        try {
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry ->
                def pair = entry.split(':')
                [(pair.first().trim()): pair.last().trim()]
            }
            if (descMap.value != null) {
                descMap.value = zigbee.swapOctets(descMap.value)
            }
        }
        catch (e2) {
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}"
            return [:]
        }
        logDebug "alternative method parsing success: descMap=${descMap}"
    }
    return descMap
}

def parseZDOcommand(Map descMap) {
    switch (descMap.clusterId) {
        // TODO - add ZDO 0005
        case '0006' :
            if (settings?.logEnable) log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})"
            break
        case '0013' : // device announcement
            if (settings?.logEnable) log.info "${device.displayName} Received device announcement, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})"
            break
        case '8004' : // simple descriptor response
            if (settings?.logEnable) log.info "${device.displayName} Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}"
            //parseSimpleDescriptorResponse( descMap )
            break
        case '8005' : // endpoint response
            def endpointCount = descMap.data[4]
            def endpointList = descMap.data[5]
            if (settings?.logEnable) log.info "${device.displayName} zdo command: cluster: ${descMap.clusterId} (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}"
            break
        case '8021' : // bind response
            if (settings?.logEnable) log.info "${device.displayName} Received bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})"
            break
        case '8022' : //unbind request
            if (settings?.logEnable) log.info "${device.displayName} zdo command: cluster: ${descMap.clusterId} (unbind request)"
            break
        case '8034' : //leave response
            if (settings?.logEnable) log.info "${device.displayName} zdo command: cluster: ${descMap.clusterId} (leave response)"
            break
        default :
            if (settings?.logEnable) log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
    }
}

// TODO - refactoring
def processE002Cluster(descMap) {
    // raw:11E201E0020A0AE0219F00, dni:11E2, endpoint:01, cluster:E002, size:0A, attrId:E00A, encoding:21, command:0A, value:009F, clusterInt:57346, attrInt:57354
    def value = zigbee.convertHexToInt(descMap.value)
    switch (descMap.attrId) {
        case 'E001' :    // the existance_time in minutes, UINT16
            sendEvent('name': 'existance_time', 'value': value, 'unit': 'minutes', 'type': 'physical', 'descriptionText': "Presence is active for ${value} minutes")
            logDebug "Cluster ${descMap.cluster} Attribute ${descMap.attrId} (existance_time) value is ${value} (0x${descMap.value} minutes)"
            break
        case 'E004' :    // value:05    // motionDetectionSensitivity, UINT8    // raw:F2EF01E0020804E02004, dni:F2EF, endpoint:01, cluster:E002, size:08, attrId:E004, encoding:20, command:0A, value:04, clusterInt:57346, attrInt:57348
            if (settings?.logEnable == true || settings?.motionDetectionSensitivity != (value as int)) { logInfo "received LINPTECH radar motionDetectionSensitivity : ${ value }" } else { logDebug "skipped ${settings?.motionDetectionSensitivity } == ${value as int }" }
            device.updateSetting('motionDetectionSensitivity', [value:value as String , type:'enum'])
            sendEvent('name': 'radarSensitivity', 'value': value, 'unit': 'meters', 'type': 'physical', 'descriptionText': "motionDetectionSensitivity is ${value}")
            break
        case 'E005' :    // value:05    // staticDetectionSensitivity, UINT8    // raw:F2EF01E0020805E02005, dni:F2EF, endpoint:01, cluster:E002, size:08, attrId:E005, encoding:20, command:0A, value:05, clusterInt:57346, attrInt:57349
            if (settings?.logEnable == true || settings?.staticDetectionSensitivity != (value as int)) { logInfo "received LINPTECH radar staticDetectionSensitivity : ${ value }" } else { logDebug "skipped ${settings?.staticDetectionSensitivity } == ${value as int }" }
            device.updateSetting('staticDetectionSensitivity', [value:value as String , type:'enum'])
            sendEvent('name': 'staticDetectionSensitivity', 'value': value, 'unit': '', 'type': 'physical', 'descriptionText': "staticDetectionSensitivity is ${value}")
            break
        case 'E00A' :    // value:009F, 6E, 2E, .....00B6 0054 - distance, UINT16
            if (settings?.ignoreDistance == false) {
                logDebug "LINPTECH radar target distance is ${value / 100} m"
                sendEvent(name : 'distance', value : value / 100, unit : 'm')
            }
            break
        case 'E00B' :    // value:value:600 -- motionDetectionDistance, UINT16  // raw:F2EF01E0020A0BE021C201, dni:F2EF, endpoint:01, cluster:E002, size:0A, attrId:E00B, encoding:21, command:0A, value:01C2, clusterInt:57346, attrInt:57355
            Integer settingsScaled = (safeToDouble(settings?.motionDetectionDistance) * 100) as int
            Float valueFloat = value / 100F
            logDebug "motionDetectionDistance raw = ${value} settings = ${settings?.motionDetectionDistance} settingsScaled = ${settingsScaled} valueFloat=${valueFloat} isEqual = ${settingsScaled == value}"
            if (settings?.logEnable == true || (settingsScaled != value)) { logInfo "received LINPTECH radar Motion Detection Distance  : ${value / 100 } m" }
            // format valueFloat to 2 decimals
            String formatted = String.format('%.2f', valueFloat)
            device.updateSetting('motionDetectionDistance', [value:formatted, type:'enum'])
            sendEvent('name': 'maximumDistance', 'value': formatted, 'unit': 'meters', 'type': 'physical', 'descriptionText': "motionDetectionDistance is ${value} meters")
            break
        default :
            logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"
            break
    }
}

def processTuyaCluster(descMap) {
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME
        logDebug "time synchronization request from device, descMap = ${descMap}"
        def offset = 0
        try {
            offset = location.getTimeZone().getOffset(new Date().getTime())
        //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}"
        }
        catch (e) {
            if (settings?.logEnable) { log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" }
        }
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8))
        logDebug "sending time data : ${cmds}"
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
        if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response
        String clusterCmd = descMap?.data[0]
        def status = descMap?.data[1]
        if (!(isHL0SS9OAradar() || is2AAELWXKradar())) {
            logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
        }
        if (status != '00' && !(isHL0SS9OAradar() || is2AAELWXKradar())) {
            logDebug "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"
        }
    }
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '06')) {
        try {
            //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
            def dp      = zigbee.convertHexToInt(descMap?.data[2])           // "dp" field describes the action/message of a command frame
            def dp_id   = zigbee.convertHexToInt(descMap?.data[3])           // "dp_identifier" is device dependant
            def fncmd   = getTuyaAttributeValue(descMap?.data)               //
            def dp_len  = zigbee.convertHexToInt(descMap?.data[5])           // the length of the DP - 1 or 4 ... This is NOT the DP type!!

            updateStateTuyaDPs(descMap, dp, dp_id, fncmd, dp_len)
            processTuyaDP(descMap, dp, dp_id, fncmd, dp_len)
        }
        catch (e) {
            logWarn "<b>catched exception</b> ${e} while processing descMap: ${descMap}"
            return
        }
    } // Tuya commands '01' and '02'
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '11') {
        // dont'know what command "11" means, it is sent by the square black radar when powered on. Will use it to restore the LED on/off configuration :)
        logDebug "Tuya <b>descMap?.command = ${descMap?.command}</b> descMap.data = ${descMap?.data}"
        if (('indicatorLight' in DEVICE.preferences))  {
            if (settings?.indicatorLight != null) {
                ArrayList<String> cmds = []
                def value = safeToInt(indicatorLight.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2)
                cmds += sendTuyaCommand('67', DP_TYPE_BOOL, dpValHex)       // TODO - refactor!
                if (settings?.logEnable) log.info "${device.displayName} restoring indicator light to : ${blackRadarLedOptions[value.toString()]} (${value})"
                sendZigbeeCommands(cmds)
            }
        }
        else {
            logDebug "unhandled Tuya <b>command 11</b> descMap: ${descMap}"
        // TODO - sent also but Tuya radars MY-Z100 30 seconds after power-on !!
        }
    }
    else {
        logDebug "<b>NOT PROCESSED</b> Tuya <b>descMap?.command = ${descMap?.command}</b> cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
    }
}

//
// called from processTuyaCluster for every Tuya device, prior to processing the DP
// updates state.tuyaDPs, increasing the DP count and updating the DP value
//
/* groovylint-disable-next-line UnusedMethodParameter */
void updateStateTuyaDPs(descMap, dp, dp_id, fncmd, dp_len) {
    if (state.tuyaDPs == null) state.tuyaDPs = [:]
    if (dp == null || dp < 0 || dp > 255) return
    def value = fncmd    // value
    def len   = dp_len   // data length in bytes
    def counter = 1      // counter
    if (state.tuyaDPs["$dp"] != null) {
        try {
            counter = safeToInt((state.tuyaDPs["$dp"][2] ?: 0))  + 1
        }
        catch (e) {
            counter = 1
        }
    }
    def upd = [value, len, counter]
    if (state.tuyaDPs["$dp"] == null) {
        // new dp in the list
        state.tuyaDPs["$dp"] = upd
        def tuyaDPsSorted = (state.tuyaDPs.sort { a, b -> return (a.key as int <=> b.key as int) })        // HE sorts the state Map elements as String ...
        state.tuyaDPs = tuyaDPsSorted
    }
    else {
        // just update the existing dp value
        state.tuyaDPs["$dp"] = upd
    }
}

//
// called from processTuyaDPfromDeviceProfile if the DP was not found for the particular device profile
// updates state.unknownDPs, increasing the DP count and updating the DP value
//
/* groovylint-disable-next-line ParameterName, UnusedMethodParameter */
void updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len) {
    if (state.unknownDPs == null) state.unknownDPs = []
    if (dp == null || dp < 0 || dp > 255) return
    if (state.unknownDPs == [] || !(dp in state.unknownDPs)) {
        List list = state.unknownDPs as List
        list.add(dp)
        list = list.sort()
        state.unknownDPs = list
    }
    /* groovylint-disable-next-line EmptyElseBlock */
    else {
    // log.trace "state.unknownDPs= ${state.unknownDPs}"
    }
}

//
// called from parse()
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule
//          false - the processing can continue
//
boolean isSpammyDPsToIgnore(descMap) {
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false }
    if (descMap?.data?.size <= 2) { return false }
    Integer dp =  zigbee.convertHexToInt(descMap.data[2])
    def spammyList = deviceProfilesV2[getDeviceGroup()].spammyDPsToIgnore
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true))
}

//
// called from processTuyaDP(), processTuyaDPfromDeviceProfile()
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule
//          false - debug logs can be generated
//
boolean isSpammyDPsToNotTrace(descMap) {
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false }
    if (descMap?.data?.size <= 2) { return false }
    Integer dp = zigbee.convertHexToInt(descMap.data[2])
    def spammyList = deviceProfilesV2[getDeviceGroup()].spammyDPsToNotTrace
    return (spammyList != null && (dp in spammyList))
}

/* groovylint-disable-next-line UnusedMethodParameter */
def compareAndConvertStrings(foundItem, tuyaValue, hubitatValue) {
    String convertedValue = tuyaValue
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings
    return [isEqual, convertedValue]
}

def compareAndConvertNumbers(foundItem, tuyaValue, hubitatValue) {
    Integer convertedValue
    if (foundItem.scale == null || foundItem.scale == 0 || foundItem.scale == 1) {    // compare as integer
        convertedValue = tuyaValue as int
    }
    else {
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int
    }
    boolean isEqual = ((convertedValue as int) == (hubitatValue as int))
    return [isEqual, convertedValue]
}

def compareAndConvertDecimals(foundItem, tuyaValue, hubitatValue) {
    Double convertedValue
    if (foundItem.scale == null || foundItem.scale == 0 || foundItem.scale == 1) {
        convertedValue = tuyaValue as double
    }
    else {
        convertedValue = (tuyaValue as double) / (foundItem.scale as double)
    }
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001
    return [isEqual, convertedValue]
}

def compareAndConvertTuyaToHubitatPreferenceValue(foundItem, fncmd, preference) {
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] }
    if (foundItem.type == null) { return [true, 'none'] }
    boolean isEqual
    def tuyaValueScaled     // could be integer or float
    switch (foundItem.type) {
        case 'bool' :       // [0:"OFF", 1:"ON"]
        case 'enum' :       // [0:"inactive", 1:"active"]
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference))
            //logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}"
            break
        case 'value' :      // depends on foundItem.scale
        case 'number' :
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference))
            //log.warn "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}"
            break
       case 'decimal' :
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference))
            //logDebug "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}"
            break
        default :
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}'
            return [true, 'none']   // fallback - assume equal
    }
    if (isEqual == false) {
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}"
    }
    //
    return [isEqual, tuyaValueScaled]
}

//
// called from processTuyaDPfromDeviceProfile()
// compares the value of the DP foundItem against a Preference with the same name
// returns: (two results!)
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference)
//            : true  - if a preference with the same name does not exist (no preference value to update)
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!)
//
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value
//
//  TODO: refactor!
//
/* groovylint-disable-next-line UnusedMethodParameter */
def compareAndConvertTuyaToHubitatEventValue(foundItem, fncmd, doNotTrace=false) {
    if (foundItem == null) { return [true, 'none'] }
    if (foundItem.type == null) { return [true, 'none'] }
    def hubitatEventValue   // could be integer or float or string
    boolean isEqual
    switch (foundItem.type) {
        case 'bool' :       // [0:"OFF", 1:"ON"]
        case 'enum' :       // [0:"inactive", 1:"active"]
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown')
            break
        case 'value' :      // depends on foundItem.scale
        case 'number' :
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name)))
            break
        case 'decimal' :
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name)))
            break
        default :
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}'
            return [true, 'none']   // fallback - assume equal
    }
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} "
    return [isEqual, hubitatEventValue]
}

def preProc(foundItem, fncmd_orig) {
    def fncmd = fncmd_orig
    if (foundItem == null) { return fncmd }
    if (foundItem.preProc == null) { return fncmd }
    String preProcFunction = foundItem.preProc
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}"
    // check if preProc method exists
    if (!this.respondsTo(preProcFunction)) {
        logDebug "preProc: function <b>${preProcFunction}</b> not found"
        return fncmd_orig
    }
    // execute the preProc function
    try {
        fncmd = "$preProcFunction"(fncmd_orig)
    }
    catch (e) {
        logWarn "preProc: Exception '${e}'caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))"
        return fncmd_orig
    }
    //logDebug "setFunction result is ${fncmd}"
    return fncmd
}

/**
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs.
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute.
 * If no preference exists for the DP, it logs the DP value as an info message.
 * If the DP is spammy (not needed for anything), it does not perform any further processing.
 *
 * @param descMap The description map of the received DP.
 * @param dp The value of the received DP.
 * @param dp_id The ID of the received DP.
 * @param fncmd The command of the received DP.
 * @param dp_len The length of the received DP.
 * @return true if the DP was processed successfully, false otherwise.
 */
boolean processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd_orig, dp_len) {
    def fncmd = fncmd_orig
    if (state.deviceProfile == null)  { return false }
    //if (!(DEVICE.device?.type == "radar"))      { return false }   // enabled for all devices - 10/22/2023 !!!    // only these models are handled here for now ...
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status)

    def tuyaDPsMap = deviceProfilesV2[state.deviceProfile].tuyaDPs
    if (tuyaDPsMap == null || tuyaDPsMap == []) { return false }    // no any Tuya DPs defined in the Device Profile

    def foundItem = null
    tuyaDPsMap.each { item ->
        if (item['dp'] == (dp as int)) {
            foundItem = item
            return
        }
    }
    if (foundItem == null) {
        // DP was not found into the tuyaDPs list for this particular deviceProfile
        updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)
        // continue processing the DP report in the old code ...
        return false
    }
    // added 10/31/2023 - preProc the DP value if needed
    if (foundItem.preProc != null) {
        fncmd = preProc(foundItem, fncmd_orig)
        logDebug "<b>preProc</b> changed ${foundItem.name} from ${fncmd_orig} to ${fncmd}"
    }
    /* groovylint-disable-next-line EmptyElseBlock */
    else {
    // logDebug "no preProc for ${foundItem.name} : ${foundItem}"
    }

    def name = foundItem.name                                    // preference name as in the tuyaDPs map
    def existingPrefValue = settings[name]                        // preference name as in Hubitat settings (preferences), if already created.
    def perfValue = null   // preference value
    boolean preferenceExists = existingPrefValue != null          // check if there is an existing preference for this dp
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this dp
    boolean isEqual = false
    boolean wasChanged = false
    boolean doNotTrace = isSpammyDPsToNotTrace(descMap)          // do not log/trace the spammy DP's
    /* groovylint-disable-next-line EmptyIfStatement */
    if (!doNotTrace) {
    //logDebug "processTuyaDPfromDeviceProfile dp=${dp} ${foundItem.name} (type ${foundItem.type}, rw=${foundItem.rw} isAttribute=${isAttribute}, preferenceExists=${preferenceExists}) value is ${fncmd} - ${foundItem.description}"
    }
    // check if the dp has the same value as the last one, or the value has changed
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ...
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : ''
    def valueScaled    // can be number or decimal or string
    String descText = descText  = "${name} is ${fncmd} ${unitText}"    // the default description text for log events

    // TODO - check if DP is in the list of the received state.tuyaDPs - then we have something to compare !
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this dp is not stored anywhere - just seend an Info log if Debug is enabled
        if (!doNotTrace) {                                      // only if the DP is not in the spammy list
            (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, fncmd, doNotTrace)
            descText  = "${name} is ${valueScaled} ${unitText}"
            if (settings.logEnable) { logInfo "${descText }" }
        }
        // no more processing is needed, as this DP is not a preference and not an attribute
        return true
    }

    // first, check if there is a preference defined to be updated
    if (preferenceExists) {
        // preference exists and its's value is extracted
        //def oldPerfValue = device.getSetting(name)
        (isEqual, perfValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, fncmd, existingPrefValue)
        if (isEqual == true) {                                 // the DP value is the same as the preference value - no need to update the preference
            logDebug "no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${perfValue} (dp raw value ${fncmd})"
        }
        else {
            logDebug "preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${perfValue} (dp raw value ${fncmd})"
            if (debug) log.info "updating par ${name} from ${existingPrefValue} to ${perfValue} type ${foundItem.type}"
            try {
                device.updateSetting("${name}", [value:perfValue, type:foundItem.type])
                wasChanged = true
            }
            catch (e) {
                logWarn "exception ${e} caught while updating preference ${name} to ${fncmd}, type ${foundItem.type}"
            }
        }
    }
    else {    // no preference exists for this dp
        // if not in the spammy list - log it!
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''
    //logInfo "${name} is ${fncmd} ${unitText}"
    }

    // second, send an event if this is declared as an attribute!
    if (isAttribute) {                                         // this DP has an attribute that must be sent in an Event
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, fncmd, doNotTrace)
        descText  = "${name} is ${valueScaled} ${unitText}"
        if (settings?.logEnable == true) { descText += " (raw:${fncmd})" }

        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along!
            if (!doNotTrace) {
                if (settings.logEnable) { logInfo "${descText } (no change)" }
            }
            // patch for inverted motion sensor 2-in-1
            if (name == 'motion' && is2in1()) {
                logDebug 'patch for inverted motion sensor 2-in-1'
            // continue ...
            }
            else {
                return true      // we are done (if there was potentially a preference, it should be already set to the same value)
            }
        }

        // DP value (fncmd) is not equal to the attribute last value or was changed- we must send an event!
        def value = safeToInt(fncmd)
        def divider = safeToInt(foundItem.scale ?: 1) ?: 1
        def valueCorrected = value / divider
        //if (!doNotTrace) { logDebug "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" }
        switch (name) {
            case 'motion' :
                handleMotion(motionActive = fncmd)
                break
            case 'temperature' :
                temperatureEvent(fncmd / getTemperatureDiv())
                break
            case 'humidity' :
                humidityEvent(fncmd / getHumidityDiv())
                break
            case 'illuminance' :
            case 'illuminance_lux' :
                illuminanceEventLux(valueCorrected)
                break
            case 'pushed' :
                logDebug "button event received fncmd=${fncmd} valueScaled=${valueScaled} valueCorrected=${valueCorrected}"
                buttonEvent(valueScaled)
                break
            default :
                sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event !
                if (!doNotTrace) {
                    logDebug "event ${name} sent w/ value ${valueScaled}"
                    logInfo "${descText}"                                 // send an Info log also (because value changed )  // TODO - check whether Info log will be sent also for spammy DPs ?
                }
                break
        }
    //log.trace "attrValue=${attrValue} valueScaled=${valueScaled} equal=${isEqual}"
    }
    // all processing was done here!
    return true
}

void processTuyaDP(descMap, dp, dp_id, fncmd, dp_len) {
    if (!isSpammyDPsToNotTrace(descMap)) {
        logDebug "processTuyaDP: received: dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
    }
    //
    if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {
        // sucessfuly processed the new way - we are done.
        return
    }
    switch (dp) {
        case 0x01 : // motion for 2-in-1 TS0601 (_TZE200_3towulqd) and presence state for almost of the radars
            logDebug "(DP=0x01) motion event fncmd = ${fncmd}"
            handleMotion(motionActive = fncmd)
            break
        case 0x04 :    // battery level for TS0202 and TS0601 2in1 ; battery1 for Fantem 4-in-1 (100% or 0% ) Battery level for _TZE200_3towulqd (2in1)
            logDebug "Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
            handleTuyaBatteryLevel(fncmd)
            break
        case 0x05 :     // tamper alarm for TS0202 4-in-1
            def value = fncmd == 0 ? 'clear' : 'detected'
            logInfo "${device.displayName} tamper alarm is ${value} (dp=05,fncmd=${fncmd})"
            sendEvent(name : 'tamper',    value : value, isStateChange : true)
            break
        case 0x07 : // temperature for 4-in-1 (no data)
            logDebug "4-in-1 temperature (dp=07) is ${fncmd / 10.0 } ${fncmd}"
            temperatureEvent(fncmd / getTemperatureDiv())
            break
        case 0x08 : // humidity for 4-in-1 (no data)
            logDebug "4-in-1 humidity (dp=08) is ${fncmd} ${fncmd}"
            humidityEvent(fncmd / getHumidityDiv())
            break
        case 0x09 : // sensitivity for TS0202 4-in-1 and 2in1 _TZE200_3towulqd
            logInfo "received sensitivity : ${sensitivityOpts.options[fncmd]} (${fncmd})"
            device.updateSetting('sensitivity', [value:fncmd.toString(), type:'enum'])
            break
        case 0x0A : // (10) keep time for TS0202 4-in-1 and 2in1 _TZE200_3towulqd
            logInfo "Keep Time (dp=0x0A) is ${keepTimeIASOpts.options[fncmd]} (${fncmd})"
            device.updateSetting('keepTime', [value:fncmd.toString(), type:'enum'])
            break
        case 0x19 : // (25)
            logDebug "Motion Switch battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
            handleTuyaBatteryLevel(fncmd)
            break
        case 0x65 :    // (101)
            //  Tuya 3 in 1 (101) -> motion (ocupancy) + TUYATEC
            logDebug "motion event 0x65 fncmd = ${fncmd}"
            handleMotion(motionActive = fncmd)
            break
        case 0x66 :     // (102)
            if (is4in1()) {    // // case 102 //reporting time intervl for 4 in 1
                logInfo "4-in-1 reporting time interval is ${fncmd} minutes"
                device.updateSetting('reportingTime4in1', [value:fncmd as int , type:'number'])
            }
            else if (is3in1()) {     // battery level for 3 in 1;
                logDebug "Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                handleTuyaBatteryLevel(fncmd)
            }
            else {
                logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
            }
            break
        case 0x68 :     // (104)
            if (isYXZBRB58radar()) {    // [0x68, 'radar_scene', tuya.valueConverterBasic.lookup({ 'default': tuya.enum(0), 'bathroom': tuya.enum(1), 'bedroom': tuya.enum(2), 'sleeping': tuya.enum(3), })],
                logInfo "YXZBRB58 radar reported radar_scene dp=${dp} value=${fncmd}"
            }
            else if (is4in1()) {    // case 104: // 0x68 temperature compensation
                def val = fncmd
                // for negative values produce complimentary hex (equivalent to negative values)
                if (val > 4294967295) { val = val - 4294967295 }
                logInfo "4-in-1 temperature calibration is ${val / 10.0}"
            }
            else {
                logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
            }
            break
        case 0x69 :    // (105)
            if (is4in1()) {    // case 105:// 0x69 humidity calibration (compensation)
                def val = fncmd
                if (val > 4294967295) val = val - 4294967295
                logInfo "4-in-1 humidity calibration is ${val}"
            }
            else {
                logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
            }
            break
        case 0x6A : // (106)
            if (is4in1()) {    // case 106: // 0x6a lux calibration (compensation)
                def val = fncmd
                if (val > 4294967295) { val = val - 4294967295 }
                logInfo "4-in-1 lux calibration is ${val}"
            }
            else {
                logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
            }
            break
        case 0x6B : // (107)
            if (is4in1()) {    //  Tuya 4 in 1 (107) -> temperature in ?C
                temperatureEvent(fncmd / getTemperatureDiv())
            }
            else {
                logDebug "(UNEXPECTED) : ${fncmd} (DP=0x6B)"
            }
            break
        case 0x6C : //  (108) Tuya 4 in 1 -> humidity in %
            if (is4in1()) {
                humidityEvent(fncmd / getHumidityDiv())
            }
            else {
                logDebug "(UNEXPECTED) : ${fncmd} (DP=0x6C)"
            }
            break
        case 0x6D :    // (109)
            if (is4in1()) {   // case 109: 0x6d PIR enable (PIR power)
                logInfo "4-in-1 enable is ${fncmd}"
            }
            else {
                logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
            }
            break
        case 0x6E : // (110) Tuya 4 in 1
            if (is4in1()) {
                logDebug "Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                handleTuyaBatteryLevel(fncmd)
            }
            else {
                logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
            }
            break
        case 0x6F : // (111) Tuya 4 in 1: // 0x6f led enable
            if (is4in1()) {
                logInfo "4-in-1 LED is: ${fncmd == 1 ? 'enabled' : 'disabled'}"
                device.updateSetting('ledEnable', [value:fncmd as boolean, type:'boolean'])
            }
            else { // 3in1 - temperature alarm switch
                if (settings?.logEnable) log.info "${device.displayName} Temperature alarm switch is: ${fncmd} (DP=0x6F)"
            }
            break
        case 0x70 : // (112)
            if (is4in1()) {   // case 112: 0x70 reporting enable (Alarm type)
                if (settings?.txtEnable) log.info "${device.displayName} reporting enable is ${fncmd}"
            }
            else {
                if (settings?.logEnable) log.info "${device.displayName} Humidity alarm switch is: ${fncmd} (DP=0x6F)"
            }
            break
        case 0x71 : // (113)
            if (is4in1()) {   // case 113: 0x71 unknown  ( ENUM)
                if (settings?.logEnable) log.info "${device.displayName} <b>UNKNOWN</b> (0x71 reporting enable?) DP=0x71 fncmd = ${fncmd}"
            }
            else {    // 3in1 - Alarm Type
                if (settings?.txtEnable) log.info "${device.displayName} Alarm type is: ${fncmd}"
            }
            break
        default :
                logDebug "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            break
    }
}

private int getTuyaAttributeValue(ArrayList _data) {
    int retValue = 0
    if (_data.size() >= 6) {
        int dataLength = _data[5] as Integer
        int power = 1
        for (i in dataLength..1) {
            retValue = retValue + power * zigbee.convertHexToInt(_data[i + 5])
            power = power * 256
        }
    }
    return retValue
}

def handleTuyaBatteryLevel(fncmd) {
    def rawValue = fncmd
    switch (fncmd) {
        case 0: rawValue = 100; break // Battery Full
        case 1: rawValue = 75;  break // Battery High
        case 2: rawValue = 50;  break // Battery Medium
        case 3: rawValue = 25;  break // Battery Low
        case 4: rawValue = 100; break // Tuya 3 in 1 -> USB powered
    }
    getBatteryPercentageResult(rawValue * 2)
}

// not used
def parseIasReport(Map descMap) {
    logDebug "pareseIasReport: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}"
    def zs = new ZoneStatus(Integer.parseInt(descMap?.value, 16))
    if (settings?.logEnable) {
        with(zs) {
            log.debug "alarm1 = $alarm1, alarm2 = $alarm2, tamper = $tamper, battery = $battery, supervisionReports = $supervisionReports, restoreReports = $restoreReports, trouble = $trouble, ac = $ac, test = $test, batteryDefect = $batteryDefect"
        }
    }
    handleMotion(zs.alarm1)
}

def parseIasMessage(String description) {
    // https://developer.tuya.com/en/docs/iot-device-dev/tuya-zigbee-water-sensor-access-standard?id=K9ik6zvon7orn
    try {
        Map zs = zigbee.parseZoneStatusChange(description)
        if (zs.alarm1Set == true) {
            handleMotion(motionActive = true)
        }
        else {
            handleMotion(motionActive = false)
        }
    }
    catch (e) {
        log.error "${device.displayName} This driver requires HE version 2.2.7 (May 2021) or newer!"
        return null
    }
}

private handleMotion(motionActive, isDigital=false) {
    def motionActiveCopy = motionActive
    if (settings.invertMotion == true) {
        motionActiveCopy = !motionActiveCopy
    }
    if (motionActiveCopy) {
        def timeout = motionResetTimer ?: 0
        // If the sensor only sends a motion detected message, the reset to motion inactive must be  performed in code
        if (settings.motionReset == true && timeout != 0) {
            runIn(timeout, resetToMotionInactive, [overwrite: true])
        }
        if (device.currentState('motion')?.value != 'active') {
            state.motionStarted = unix2formattedDate(now().toString())
        }
    }
    else {
        if (device.currentState('motion')?.value == 'inactive') {
            logDebug "ignored motion inactive event after ${getSecondsInactive()}s"
            return [:]   // do not process a second motion inactive event!
        }
    }
    sendMotionEvent(motionActiveCopy, isDigital)
}

def sendMotionEvent(motionActive, isDigital=false) {
    def descriptionText = 'Detected motion'
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
            //isStateChange   : true,
            type            : isDigital == true ? 'digital' : 'physical',
            descriptionText : descriptionText
    )
    runIn(1, formatAttrib, [overwrite: true])
}

def resetToMotionInactive() {
    if (device.currentState('motion')?.value == 'active') {
        def descText = "Motion reset to inactive after ${getSecondsInactive()}s (software timeout)"
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

def getSecondsInactive() {
    def unixTime = formattedDate2unix(state.motionStarted)
    if (unixTime) { return Math.round((now() - unixTime) / 1000) }
    return motionResetTimer ?: 0
}

def temperatureEvent(temperature) {
    [:].with {
        name = 'temperature'
        unit = "\u00B0${location.temperatureScale}"
        value = convertTemperatureIfNeeded(temperature, 'C', precision = 1)
        type = 'physical'
        descriptionText = "${name} is ${value} ${unit}"
        isStateChange = true
        logInfo "${descriptionText}"
        sendEvent(this)
        runIn(1, formatAttrib, [overwrite: true])
    }
}

def humidityEvent(humidity) {
    // Removed unused variable declaration
    [:].with {
        name = 'humidity'
        value = humidity as int
        unit = '% RH'
        type = 'physical'
        isStateChange = true
        descriptionText = "${name} is ${Math.round((humidity) * 10) / 10} ${unit}"
        logInfo "${descriptionText}"
        sendEvent(this)
        runIn(1, formatAttrib, [overwrite: true])
    }
}

def illuminanceEvent(rawLux) {
    def lux = rawLux > 0 ? Math.round(Math.pow(10, (rawLux / 10000))) : 0
    illuminanceEventLux(lux as Integer)
}

def illuminanceEventLux(lux) {
    Integer illumCorrected = Math.round((lux * ((settings?.illuminanceCoeff ?: 1.00) as float)))
    Integer delta = Math.abs(safeToInt(device.currentValue('illuminance')) - (illumCorrected as int))
    if (device.currentValue('illuminance', true) == null || (delta >= safeToInt(settings?.luxThreshold))) {
        sendEvent('name': 'illuminance', 'value': illumCorrected, 'unit': 'lx', 'type': 'physical', 'descriptionText': "Illuminance is ${lux} Lux")
        logInfo "Illuminance is ${illumCorrected} Lux"
    }
    else {
        logDebug "ignored illuminance event ${illumCorrected} lx : the change of ${delta} lx is less than the ${safeToInt(settings?.luxThreshold)} lux threshold!"
    }
    runIn(1, formatAttrib, [overwrite: true])
}

def occupancyEvent(raw) {
    logDebug "occupancyEvent: raw=${raw}"
    [:].with {
        name = 'occupancy'
        value = raw ? 'occupied' : 'unoccupied'
        unit = ''
        type = 'physical'
        isStateChange = true
        descriptionText = "${name} state is ${value}"
        logInfo "${descriptionText}"
        sendEvent(this)
    }
}

def buttonEvent(action, buttonNumber=1) {
    logInfo "button $buttonNumber was $action"
    sendEvent(name: action, value: '1', data: [buttonNumber: 1], descriptionText: 'button 1 was pushed', isStateChange: true, type: 'physical')
}

def powerSourceEvent(state = null) {
    String ps = null
    if (state != null && state == 'unknown') {
        ps = 'unknown'
    }
    else if (state != null) {
        ps = state
    }
    else {
        if (DEVICE.device?.powerSource != null) {
            ps = DEVICE.device?.powerSource
        }
        else if (!('Battery' in DEVICE.capabilities)) {
            ps = 'dc'
        }
        else {
            ps = 'unknown'
        }
    }
    sendEvent(name : 'powerSource',    value : ps, descriptionText: 'device is back online', type: 'digital')
}

// called on initial install of device during discovery
// also called from initialize() in this driver!
def installed() {
    log.info "${device.displayName} installed()..."
    initialize(fullInit = true)
//unschedule()
}

// called when preferences are saved
def updated() {
    logInfo 'updated()...'
    checkDriverVersion()
    updateTuyaVersion()
    ArrayList<String> cmds = []

    logInfo "Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} <b>deviceProfile=${state.deviceProfile}</b>"
    logInfo "Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (logEnable == true) {
        runIn(86400, logsOff, [overwrite: true])    // turn off debug logging after 24 hours
        logInfo 'Debug logging will be turned off after 24 hours'
    }
    else {
        unschedule(logsOff)
    }
    if (settings.allStatusTextEnable == false) {
        device.deleteCurrentState('all')
    }
    else {
        formatAttrib()
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
    //logDebug "forcedProfile is not set"
    }

    //    LED enable - TODO !
    if (is4in1()) {
        logDebug "4-in-1: changing ledEnable to : ${settings?.ledEnable }"
        cmds += sendTuyaCommand('6F', DP_TYPE_BOOL, settings?.ledEnable == true ? '01' : '00')
        logDebug "4-in-1: changing reportingTime4in1 to : ${settings?.reportingTime4in1} minutes"
        cmds += sendTuyaCommand('66', DP_TYPE_VALUE, zigbee.convertToHexString(settings?.reportingTime4in1 as int, 8))
    }
    // sensitivity for PIR devices
    if (settings?.sensitivity != null) {
        if ((DEVICE.device?.type == 'PIR') && (('sensitivity' in DEVICE.preferences) && (DEVICE.preferences.sensitivity != false))) {
            def val = settings?.sensitivity as int
            if (isIAS()) {
                if (val != null) {
                    logDebug "changing IAS sensitivity to : ${sensitivityOpts.options[val]} (${val})"
                    cmds += sendSensitivityIAS(val)
                }
            }
            else {
                if (settings?.logEnable) { log.warn "${device.displayName} changing TS0601 sensitivity to : ${val}" }
                setPar('sensitivity', val as String)
            }
        }
    }
    else {
        logDebug 'sensitivity is not set'
    }

    // keep time for PIR devices
    if (settings?.keepTime != null) {
        if ((DEVICE.device?.type == 'PIR') && (('keepTime' in DEVICE.preferences) && (DEVICE.preferences.keepTime != false))) {
            if (isIAS() && (settings?.keepTime != null)) {
                cmds += sendKeepTimeIAS(settings?.keepTime)
                logDebug "changing IAS Keep Time to : ${keepTime4in1Opts.options[settings?.keepTime as int]} (${settings?.keepTime})"
            }
            else {
                if (settings?.logEnable) { log.warn "${device.displayName} changing TS0601 Keep Time to : ${(settings?.keepTime as int )}" }
                setPar('keepTime', settings?.keepTime as String)
            }
        }
    }
    else {
        logDebug 'keepTime is not set'
    }
    // new update method for all radars, WITHOUT Linptech - TODO !
    if (isLINPTECHradar()) {
        setPar('fadingTime', settings?.fadingTime ?: 10)
        setPar('motionDetectionDistance', settings?.motionDetectionDistance)
        setPar('motionDetectionSensitivity', settings?.motionDetectionSensitivity)
        setPar('staticDetectionSensitivity', settings?.staticDetectionSensitivity)
    }
    if (isSONOFF()) {
        setPar('fadingTime', settings?.fadingTime ?: 60)
        setPar('radarSensitivity', settings?.radarSensitivity ?: 2)
        // read backk the parameters from the device
        cmds += zigbee.readAttribute(0x0406, 0x0020, [:], delay = 201)
        cmds += zigbee.readAttribute(0x0406, 0x0022, [:], delay = 201)
    }
    else if (DEVICE.device?.type in ['radar', 'PIR']) {
        // Itterates through all settings
        cmds += updateAllPreferences()
    }
    //
    if ('DistanceMeasurement' in DEVICE.capabilities) {
        if (settings?.ignoreDistance == true) {
            device.deleteCurrentState('distance')
        }
    }
    //
    if (('indicatorLight' in DEVICE.preferences)) {    // BlackSquareRadar          TODO !!
        if (indicatorLight != null) {
            def value = safeToInt(indicatorLight.value)
            def dpValHex = zigbee.convertToHexString(value as int, 2)
            cmds += sendTuyaCommand('67', DP_TYPE_BOOL, dpValHex)
            logDebug "setting indicator light to : ${blackRadarLedOptions[value.toString()]} (${value})"
        }
    }
    //
    if (settings.allStatusTextEnable == true) {
        runIn(1, formatAttrib, [overwrite: true])
    }
    int totalSize = 0
    //log.warn "cmds=${cmds} length=${cmds?.size()}"
    if (cmds != null && cmds != [null]) {
        for (int i = 0; i < cmds?.size(); i++) {
            if (cmds[i] != null) totalSize += cmds[i].size()
        }
    }
    if (totalSize >= 7) {
        logDebug "sending the changed AdvancedOptions (size=${cmds?.size()}, totalSize=${totalSize}) to the device..."
        sendZigbeeCommands(cmds)
        logInfo 'preferencies updates are sent to the device...'
    }
    else {
        logDebug "no preferences are changed (totalSize=${totalSize}) cmds=${cmds}"
    }
}

def ping() {
    logInfo 'ping() is not implemented'
}

// TODO - remove SiHAS specific code !!!
private getILLUMINANCE_MEASUREMENT_CLUSTER() { 0x0400 }
private getRELATIVE_HUMIDITY_CLUSTER() { 0x0405 }
private getOCCUPANCY_SENSING_CLUSTER() { 0x0406 }
private getATTRIBUTE_IAS_ZONE_STATUS() { 0x0000 }
private getPOWER_CONFIGURATION_BATTERY_VOLTAGE_ATTRIBUTE() { 0x0020 }
private getTEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE() { 0x0000 }
private getRELATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE() { 0x0000 }
private getILLUMINANCE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE() { 0x0000 }
private getOCCUPANCY_SENSING_OCCUPANCY_ATTRIBUTE() { 0x0000 }

// TODO - move to the standard Device Profile configuration settings !!!
def refreshSiHAS() {
    if (settings?.logEnable) { log.debug "${device.displayName } refreshSiHAS()" }
    def refreshCmds = []

    refreshCmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, POWER_CONFIGURATION_BATTERY_VOLTAGE_ATTRIBUTE, [:], delay = 201)

    refreshCmds += zigbee.readAttribute(RELATIVE_HUMIDITY_CLUSTER, RELATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE, [:], delay = 202)
    refreshCmds += zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE, [:], delay = 203)
    refreshCmds += zigbee.readAttribute(ILLUMINANCE_MEASUREMENT_CLUSTER, ILLUMINANCE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE, [:], delay = 204)
    refreshCmds += zigbee.readAttribute(OCCUPANCY_SENSING_CLUSTER, OCCUPANCY_SENSING_OCCUPANCY_ATTRIBUTE, [:], delay = 205)
    refreshCmds += zigbee.enrollResponse(300)
    return refreshCmds
}

// TODO - move to the standard Device Profile configuration settings !!!
def configureSiHAS() {
    if (settings?.logEnable) { log.debug "${device.displayName } configureSiHAS()" }
    def configCmds = []

    // Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
   // sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])

    // temperature minReportTime 30 seconds, maxReportTime 5 min. Reporting interval if no activity
    // battery minReport 30 seconds, maxReportTime 6 hrs by default
    // humidity minReportTime 30 seconds, maxReportTime 60 min
    // illuminance minReportTime 30 seconds, maxReportTime 60 min
    // occupancy sensing minReportTime 10 seconds, maxReportTime 60 min
    // ex) zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 600, 21600, 0x01)
    // This is for cluster 0x0001 (power cluster), attribute 0x0021 (battery level), whose type is UINT8,
    // the minimum time between reports is 10 minutes (600 seconds) and the maximum time between reports is 6 hours (21600 seconds),
    // and the amount of change needed to trigger a report is 1 unit (0x01).
    configCmds += zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, POWER_CONFIGURATION_BATTERY_VOLTAGE_ATTRIBUTE, DataType.UINT8, 30, 21600, 0x01/*100mv*1*/, [:], delay = 221)

    configCmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE, DataType.INT16, 15, 300, 10/*10/100 = 0.1도*/, [:], delay = 222)
    configCmds += zigbee.configureReporting(RELATIVE_HUMIDITY_CLUSTER, RELATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE, DataType.UINT16, 15, 300, 40/*10/100 = 0.4%*/, [:], delay = 223)
    configCmds += zigbee.configureReporting(ILLUMINANCE_MEASUREMENT_CLUSTER, ILLUMINANCE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE, DataType.UINT16, 15, 3600, 1/*1 lux*/, [:], delay = 224)
    configCmds += zigbee.configureReporting(OCCUPANCY_SENSING_CLUSTER, OCCUPANCY_SENSING_OCCUPANCY_ATTRIBUTE, DataType.BITMAP8, 1, 600, 1, [:], delay = 225)
    //configCmds += zigbee.configureReporting(zigbee.IAS_ZONE_CLUSTER, ATTRIBUTE_IAS_ZONE_STATUS, DataType.BITMAP16, 0, 0xffff, null, [:], delay=226) // set : none reporting flag, device sends out notification to the bound devices.
    configCmds +=  ["zdo bind 0x${device.deviceNetworkId} 0x${endpoint} 0x01 0x0500 {${device.zigbeeId}} {}", 'delay 229', ]
    configCmds += zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)

    return configCmds + refreshSiHAS()
}

def refresh() {
    logInfo 'refresh()...'
    checkDriverVersion()
    updateTuyaVersion()
    ArrayList<String> cmds = []
    cmds += zigbee.readAttribute(0x0000, 0x0007, [:], delay = 191)             // Power Source
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 192)             // batteryVoltage
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 193)             // batteryPercentageRemaining

    if (isIAS() || is4in1()) {
        IAS_ATTRIBUTES.each { key, value ->
            cmds += zigbee.readAttribute(0x0500, key, [:], delay = 199)
        }
    }
    if (is4in1()) {
        cmds += zigbee.command(0xEF00, 0x07, '00')    // Fantem Tuya Magic
    }
    if (isTuya()) {
        cmds += zigbee.command(0xEF00, 0x03)
    }
    if (isSiHAS()) {
        cmds += refreshSiHAS()
    }
    if (settings.allStatusTextEnable == true) {
        runIn(1, formatAttrib, [overwrite: true])
    }
    sendZigbeeCommands(cmds)
}

def driverVersionAndTimeStamp() { version() + ' ' + timeStamp() }

def checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logInfo "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        unschedule('pollPresence')    // now replaced with deviceHealthCheck
        scheduleDeviceHealthCheck()
        updateTuyaVersion()
        initializeVars(fullInit = false)
        state.driverVersion = driverVersionAndTimeStamp()
        if (state.tuyaDPs == null) state.tuyaDPs = [:]
        if (state.lastPresenceState != null) state.remove('lastPresenceState')    // removed in version 1.0.6
        if (state.hashStringPars != null)    state.remove('hashStringPars')       // removed in version 1.1.0
        if (state.lastBattery != null)       state.remove('lastBattery')          // removed in version 1.3.0

        if ('DistanceMeasurement' in DEVICE?.capabilities) {
            if (settings?.ignoreDistance == true) {
                device.deleteCurrentState('distance')
            }
            device.deleteCurrentState('battery')
            device.deleteCurrentState('tamper')
            device.deleteCurrentState('temperature')
        }
        validateAndFixPreferences()   // new in version 1.6.3
    }
}

void scheduleDeviceHealthCheck() {
    Random rnd = new Random()
    //schedule("1 * * * * ? *", 'deviceHealthCheck') // for quick test
    schedule("${rnd.nextInt(59)} ${rnd.nextInt(59)} 1/3 * * ? *", 'deviceHealthCheck')
}

def logInitializeRezults() {
    if (settings?.txtEnable) log.info "${device.displayName} manufacturer  = ${device.getDataValue('manufacturer')}"
    if (settings?.txtEnable) log.info "${device.displayName} Initialization finished\r                          version=${version()} (Timestamp: ${timeStamp()})"
}

// delete all attributes
void deleteAllCurrentStates() {
    def ctr = 0
    device.properties.supportedAttributes.each { it ->
        //logDebug "deleting $it"
        device.deleteCurrentState("$it")
        ctr++
    }
    logInfo "All ${ctr} current states (attributes) DELETED"
}

void resetStats() {
    logInfo 'Stats are reset...'
    state.tuyaDPs = [:]
    state.txCounter = 0
    state.txCounter = 0
}

// called by initialize() button
void initializeVars(boolean fullInit = false) {
    logInfo "InitializeVars( fullInit = ${fullInit} )..."
    if (fullInit == true) {
        deleteAllCurrentStates()
        state.clear()
        resetStats()
        state.driverVersion = driverVersionAndTimeStamp()
        state.motionStarted = unix2formattedDate(now())
    }
    if (/*fullInit == true || */state.deviceProfile == null) {
        setDeviceNameAndProfile()
    }
    //
    state.packetID = 0
    state.rxCounter = 0
    state.txCounter = 0
    if (fullInit == true || state.notPresentCounter == null) state.notPresentCounter = 0
    //
    if (settings.logEnable == null) device.updateSetting('logEnable', true)
    if (settings.txtEnable == null) device.updateSetting('txtEnable', true)
    if (fullInit == true || settings.motionReset == null) device.updateSetting('motionReset', false)
    if (fullInit == true || settings.motionResetTimer == null) device.updateSetting('motionResetTimer', 60)
    if (settings.advancedOptions == null)  device.updateSetting('advancedOptions', false)
    if (fullInit == true || settings.sensitivity == null) device.updateSetting('sensitivity', [value:'2', type:'enum'])
    if (fullInit == true || settings.keepTime == null) device.updateSetting('keepTime', [value:'0', type:'enum'])
    if (fullInit == true || settings.ignoreDistance == null) device.updateSetting('ignoreDistance', true)
    if (fullInit == true || settings.ledEnable == null) device.updateSetting('ledEnable', true)
    if (fullInit == true || settings.temperatureOffset == null) device.updateSetting('temperatureOffset', [value:0.0, type:'decimal'])
    if (fullInit == true || settings.humidityOffset == null) device.updateSetting('humidityOffset', [value:0.0, type:'decimal'])
    if (fullInit == true || settings.luxOffset == null) device.updateSetting('luxOffset', [value:1.0, type:'decimal'])
    if (fullInit == true || settings.luxThreshold == null) device.updateSetting('luxThreshold', [value:5, type:'number'])
    if ((DEVICE?.capabilities?.IlluminanceMeasurement == true) && (DEVICE?.preferences.luxThreshold  == false)) {
        logDebug 'setting luxThreshold to 0'
        device.updateSetting('luxThreshold', [value:0, type:'number'])
    }
    else {
        logDebug "luxThreshold is not set to 0 (luxThreshold=${DEVICE?.preferences.luxThreshold}, IlluminanceMeasurement=${DEVICE?.capabilities?.IlluminanceMeasurement})"
    }
    if (fullInit == true || settings.illuminanceCoeff == null) device.updateSetting('illuminanceCoeff', [value:1.0, type:'decimal'])
    if (fullInit == true || settings.parEvents == null) device.updateSetting('parEvents', true)
    if (fullInit == true || settings.invertMotion == null) device.updateSetting('invertMotion', is2in1() ? true : false)
    if (fullInit == true || settings.reportingTime4in1 == null) device.updateSetting('reportingTime4in1', [value:DEFAULT_REPORTING_4IN1, type:'number'])
    if (fullInit == true || settings.allStatusTextEnable == null) device.updateSetting('allStatusTextEnable', false)
    // version 1.6.0 - load DeviceProfile specific defaults ...
    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
    //
    if (fullInit == true) sendEvent(name : 'powerSource',    value : '?', isStateChange : true)    // TODO !!!
    if (device.currentValue('healthStatus') == null) sendHealthStatusEvent('unknown')
//
}

// TODO - refine !
def isTuya() {
    return (device.getDataValue('model')?.startsWith('TS') == true)
}

def tuyaBlackMagic() {
    List<String> cmds = []
    if (isTuya()) {
        cmds += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay = 200)    // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, attributeReportingStatus
        cmds += zigbee.writeAttribute(0x0000, 0xffde, 0x20, 0x13, [:], delay = 200)
    }
    else {
        logDebug 'tuyaBlackMagic for non-Tuya device ...'
        cmds += zigbee.readAttribute(0x0000, [0x0004, 0x0005, 0x4000], [:], delay = 200)      // manufacturer, model, SoftwareBuildID
        if (isIAS()) {
            logDebug 'tuyaBlackMagic for IAS device ...'
            cmds += zigbee.readAttribute(0x0500, 0x0001, [:], delay = 201)                    // IAS Zone Cluster, Zone Type
        }
    }
    return  cmds
}

// called when used with capability "Configuration" is called when the configure button is pressed on the device page.
// Runs when driver is installed, after installed() is run. if capability Configuration exists, a Configure command is added to the ui
// It is also called on initial install after discovery.
def configure() {
    if (settings?.txtEnable) log.info "${device.displayName} configure().."
    //runIn( defaultPollingInterval, pollPresence, [overwrite: true])
    scheduleDeviceHealthCheck()
    state.motionStarted = unix2formattedDate(now())
    ArrayList<String> cmds = []
    cmds += tuyaBlackMagic()     // commented out 10/21/2023 - we aleady have the BlackMagic in the initialize() method !
    int intMinTime = safeToInt(3600)    // TODO: make it configurable
    int intMaxTime = safeToInt(7200)    // TODO: make it configurable
    if (isIAS()) {
        cmds += zigbee.enrollResponse(300) + zigbee.readAttribute(0x0500, 0x0000, [:], delay = 224)
        logDebug 'Enrolling IAS device ...'
    }
    if (isSiHAS()) {
        cmds += configureSiHAS()
    }
    else  {
        if ('0x0001' in DEVICE.configuration) {    // Power Configuration cluster
            logDebug "configuring the battery reporting... (min=${intMinTime}, max=${intMaxTime}, delta=0x02)"
            cmds += zigbee.configureReporting(0x0001, 0x20, DataType.UINT8, intMinTime, intMaxTime, 0x02, [:], delay = 226)  // TEST - seems to be overwritten by the next line configuration?
            cmds += zigbee.configureReporting(0x0001, 0x21, DataType.UINT8, intMinTime, intMaxTime, 0x02, [:], delay = 225)  // delta 0x02 = 1% change battery percentage remaining
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 228)    // try also battery voltage
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 227)    // battery percentage   - SONOFF GW configures and reads only attr 0x0021 !
        }
        if ('0x0500' in DEVICE.configuration) {
            cmds += zigbee.configureReporting(0x0500, 0x0002, 0x19, 0, 3600, 0x00, [:], delay = 227)
        }
        if ('0x0400' in DEVICE.configuration) {
            cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0400 {${device.zigbeeId}} {}", 'delay 229', ]
        }
        if ('0x0402' in DEVICE.configuration) {
            cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0402 {${device.zigbeeId}} {}", 'delay 229', ]
        }
        if ('0x0405' in DEVICE.configuration) {
            cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0405 {${device.zigbeeId}} {}", 'delay 229', ]
        }
        if ('0x0406' in DEVICE.configuration) {
            cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}", 'delay 229', ]    // OWON and SONOFF motion/occupancy cluster
        }
        if ('0xFC11' in DEVICE.configuration) {
            cmds += zigbee.configureReporting(0xFC11, 0x2001, DataType.UINT16, 0, 1440, 0x01, [:], delay = 230)  // attribute 2001 - ??
        }
    }
    sendZigbeeCommands(cmds)
}

// called when used with capability "Initialize" it will call this method every time the hub boots up. So for things that need refreshing or re-connecting (LAN integrations come to mind here) ..
// runs first time driver loads, ie system startup
// when capability Initialize exists, a Initialize command is added to the ui.
def initialize(boolean fullInit = true) {
    log.info "${device.displayName} Initialize( fullInit = ${fullInit} )..."
    unschedule()
    initializeVars(fullInit)
    configure()
    runIn(1, 'updated', [overwrite: true])
    runIn(3, 'logInitializeRezults', [overwrite: true])
    runIn(4, 'refresh', [overwrite: true])
}

private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    int tuyaCmd = is4in1() ? 0x04 : SETDATA
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd)
    logDebug "${device.displayName} <b>sendTuyaCommand</b> = ${cmds}"
    if (state.txCounter != null) state.txCounter = state.txCounter + 1
    return cmds
}

Integer safeToInt(val, Integer defaultVal=0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

Double safeToDouble(val, Double defaultVal=0.0) {
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    logDebug "<b>sendZigbeeCommands</b> (cmd=$cmd)"
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
        if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    sendHubCommand(allActions)
}

private getPACKET_ID() {
    state.packetID = ((state.packetID ?: 0) + 1) % 65536
    return zigbee.convertToHexString(state.packetID, 4)
}

private getDescriptionText(msg) {
    def descriptionText = "${device.displayName} ${msg}"
    if (settings?.txtEnable) log.info "${descriptionText}"
    return descriptionText
}

def logsOff() {
    if (settings?.logEnable) log.info "${device.displayName} debug logging disabled..."
    device.updateSetting('logEnable', [value:'false', type:'bool'])
}

def getBatteryPercentageResult(rawValue) {
    def value = Math.round(rawValue / 2)
    //logDebug "getBatteryPercentageResult: rawValue = ${rawValue} -> ${value} %"
    if (value >= 0 && value <= 100) {
        sendBatteryEvent(value)
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} ignoring getBatteryPercentageResult (${rawValue})"
    }
}

def sendBatteryVoltageEvent(rawValue) {
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V"
    def result = [:]
    def volts = rawValue / 10
    if (rawValue != 0 && rawValue != 255) {
        def minVolts = 2.1
        def maxVolts = 3.0
        def pct = (volts - minVolts) / (maxVolts - minVolts)
        def roundedPct = Math.round(pct * 100)
        if (roundedPct <= 0) roundedPct = 1
        /* groovylint-disable-next-line ConstantIfExpression */
        if (false) {
            result.value = Math.min(100, roundedPct)
            result.name = 'battery'
            result.unit  = '%'
            result.descriptionText = "${device.displayName} battery is ${roundedPct} %"
        }
        else {
            result.value = volts
            result.name = 'batteryVoltage'
            result.unit  = 'V'
            result.descriptionText = "${device.displayName} battery is ${volts} Volts"
        }
        result.type = 'physical'
        result.isStateChange = true
        if (settings?.txtEnable) log.info "${result.descriptionText}"
        sendEvent(result)
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} ignoring BatteryResult(${rawValue})"
    }
}

def sendBatteryEvent(batteryPercent, isDigital=false) {
    [:].with {
        name = 'battery'
        timeStamp = now()
        value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int)
        unit = '%'
        type = isDigital ? 'digital' : 'physical'
        descriptionText = "${name} is ${value} ${unit}"
        isStateChange = true

        def latestBatteryEvent = device.latestState('battery', skipCache=true)
        def latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now()
        def timeDiff = ((now() - latestBatteryEventTime) / 1000) as int
        if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) {
            // send it now!
            sendDelayedBatteryEvent(this)
        } else {
            def delayedTime = (settings?.batteryDelay as int) - timeDiff
            delayed = delayedTime
            descriptionText += " [delayed ${delayed} seconds]"
            logDebug "this battery event (${value}%) will be delayed ${delayedTime} seconds"
            runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: this])
        }
    }
}
private void sendDelayedBatteryEvent(Map map) {
    logInfo "${map.descriptionText}"
    //map.each {log.trace "$it"}
    sendEvent(map)
}

def setMotion(mode) {
    switch (mode) {
        case 'active' :
            handleMotion(motionActive = true, isDigital = true)
            break
        case 'inactive' :
            handleMotion(motionActive = false, isDigital = true)
            break
        default :
            if (settings?.logEnable) log.warn "${device.displayName} please select motion action)"
            break
    }
}

def sendSensitivityIAS(lvl) {
    def sensitivityLevel = safeToInt(lvl, -1)
    if (sensitivityLevel < 0 || sensitivityLevel > 2) {
        logWarn "IAS sensitivity is not set for ${device.getDataValue('manufacturer')}, invalid value ${sensitivityLevel}"
        return null
    }
    ArrayList<String> cmds = []
    String str = sensitivityOpts.options[sensitivityLevel]
    cmds += zigbee.writeAttribute(0x0500, 0x0013, DataType.UINT8, sensitivityLevel as int, [:], delay = 200)
    logDebug "${device.displayName} sending IAS sensitivity : ${str} (${sensitivityLevel})"
    // only prepare the cmds here!
    return cmds
}

def sendKeepTimeIAS(lvl) {
    def keepTimeVal = safeToInt(lvl, -1)
    if (keepTimeVal < 0 || keepTimeVal > 5) {
        logWarn "IAS Keep Time  is not set for ${device.getDataValue('manufacturer')}, invalid value ${keepTimeVal}"
        return null
    }
    ArrayList<String> cmds = []
    String str = keepTime4in1Opts.options[keepTimeVal]
    cmds += zigbee.writeAttribute(0x0500, 0xF001, DataType.UINT8, keepTimeVal as int, [:], delay = 200)
    logDebug "${device.displayName} sending IAS Keep Time : ${str} (${keepTimeVal})"
    // only prepare the cmds here!
    return cmds
}

// called when any event was received from the Zigbee device in parse() method..
def setPresent() {
    if ((device.currentValue('healthStatus') ?: 'unknown') != 'online') {
        sendHealthStatusEvent('online')
        powerSourceEvent() // sent only once now - 2023-01-31        // TODO - check!
        runIn(1, formatAttrib, [overwrite: true])
        logInfo 'is online'
    }
    state.notPresentCounter = 0
}

def deviceHealthCheck() {
    state.notPresentCounter = (state.notPresentCounter ?: 0) + 1
    if (state.notPresentCounter > presenceCountTreshold) {
        if ((device.currentValue('healthStatus', true) ?: 'unknown') != 'offline') {
            sendHealthStatusEvent('offline')
            if (settings?.txtEnable) { log.warn "${device.displayName} is offline!" }
            if (!(device.currentValue('motion', true) in ['inactive', '?'])) {
                handleMotion(false, isDigital = true)
                if (settings?.txtEnable) log.warn "${device.displayName} forced motion to '<b>inactive</b>"
            }
            runIn(1, formatAttrib, [overwrite: true])
        }
    }
    else {
        logDebug "deviceHealthCheck - online (notPresentCounter=${state.notPresentCounter})"
    }
}

def sendHealthStatusEvent(value) {
    sendEvent(name: 'healthStatus', value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

void formatAttrib() {
    if (settings.allStatusTextEnable == false) {    // do not send empty html or text attributes
        return
    }
    String attrStr = ''
    attrStr += addToAttr('status', 'healthStatus')
    attrStr += addToAttr('motion', 'motion')
    if (DEVICE.capabilities?.DistanceMeasurement == true && settings?.ignoreDistance == false) { attrStr += addToAttr('distance', 'distance') }
    if (DEVICE.capabilities?.Battery == true) { attrStr += addToAttr('battery', 'battery') }
    if (DEVICE.capabilities?.IlluminanceMeasurement == true) { attrStr += addToAttr('illuminance', 'illuminance') }
    if (DEVICE.capabilities?.TemperatureMeasurement == true) { attrStr += addToAttr('temperature', 'temperature') }
    if (DEVICE.capabilities?.RelativeHumidityMeasurement == true) { attrStr += addToAttr('humidity', 'humidity')  }
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

void updateAttr(String aKey, aValue, String aUnit = '') {
    sendEvent(name:aKey, value:aValue, unit:aUnit, type: 'digital')
}

def deleteAllStatesAndJobs() {
    state.clear()    // clear all states
    unschedule()
    device.deleteCurrentState('')
    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}"
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

def setReportingTime4in1(val) {
    if (is4in1()) {
        def value = safeToInt(val, -1)
        if (value >= 0) {
            logDebug "changing reportingTime4in1 to ${value == 0 ? 10 : $val} ${value == 0 ? 'seconds' : 'minutes'}  (raw=${value})"
            return sendTuyaCommand('66', DP_TYPE_VALUE, zigbee.convertToHexString(value, 8))
        }
    }
    else {
        return null
    }
}

// Linptech radar exception code below
// TODO - refactor it!

def setRadarSensitivity(val) {
    if (isSONOFF()) {
        //def value = safeToInt(val)
        logDebug "changing SONOFF radar sensitivity to ${val} "
        return zigbee.writeAttribute(0x0406, 0x0022, 0x20, val as int, [:], delay = 200)
    }
    return null
}

def setFadingTime(val) {
    if (isLINPTECHradar()) {
        def value = safeToInt(val)
        logDebug "changing LINPTECH radar fadingTime to ${value} seconds"
        return sendTuyaCommand('65', DP_TYPE_VALUE, zigbee.convertToHexString(value, 8))   // this is the only Linptech command that used Tuya DPs ...
    }
    else if (isSONOFF()) {
        //def value = safeToInt(val)
        logDebug "changing SONOFF radar fadingTime to ${val} seconds"
        return zigbee.writeAttribute(0x0406, 0x0020, 0x21, val as int, [:], delay = 200)
    }
    return null
}

// Linptech specific - refactor it!
def setMotionDetectionDistance(scaledValue) {
    if (isLINPTECHradar()) {
        logDebug "changing LINPTECH radar MotionDetectionDistance to scaledValue=${scaledValue}"
        return zigbee.writeAttribute(0xE002, 0xE00B, 0x21, scaledValue as int, [:], delay = 200)
    }
    return null
}

def setMotionDetectionSensitivity(val) {
    if (isLINPTECHradar()) {
        logDebug "changing LINPTECH radar MotionDetectionSensitivity to ${val}"
        return zigbee.writeAttribute(0xE002, 0xE004, 0x20, val as int, [:], delay = 200)
    }
    return null
}

def setStaticDetectionSensitivity(val) {
    if (isLINPTECHradar()) {
        logDebug "changing LINPTECH radar StaticDetectionSensitivity to ${val}"
        return zigbee.writeAttribute(0xE002, 0xE005, 0x20, val as int, [:], delay = 200)
    }
    return null
}

/**
 * Returns the scaled value of a preference based on its type and scale.
 * @param preference The name of the preference to retrieve.
 * @param dpMap A map containing the type and scale of the preference.
 * @return The scaled value of the preference, or null if the preference is not found or has an unsupported type.
 */
def getScaledPreferenceValue(String preference, Map dpMap) {
    def value = settings."${preference}"
    def scaledValue
    if (value == null) {
        logDebug "getScaledPreferenceValue: preference ${preference} not found!"
        return null
    }
    switch (dpMap.type) {
        case 'number' :
            scaledValue = safeToInt(value)
            break
        case 'decimal' :
            scaledValue = safeToDouble(value)
            if (dpMap.scale != null && dpMap.scale != 1) {
                scaledValue = Math.round(scaledValue * dpMap.scale)
            }
            break
        case 'bool' :
            scaledValue = value == 'true' ? 1 : 0
            break
        case 'enum' :
            //log.warn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}"
            if (dpMap.map == null) {
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!"
                return null
            }
            scaledValue = value
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) {
                scaledValue = Math.round(safeToDouble(scaledValue) * safeToInt(dpMap.scale))
            }
            break
        default :
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!"
            return null
    }
    logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})"
    return scaledValue
}

// called from updated() method
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!!
def updateAllPreferences() {
    logDebug "updateAllPreferences: preferences=${DEVICE.preferences}"
    ArrayList<String> cmds = []
    if (DEVICE.preferences == null || DEVICE.preferences == [:]) {
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceGroup()}"
        return null
    }
    Integer dpInt = 0
    def scaledValue    // int or String for enums
    (DEVICE.preferences).each { name, dp ->
        dpInt = safeToInt(dp, -1)
        if (dpInt <= 0) {
            // this is the IAS and other non-Tuya DPs preferences ....
            logDebug "updateAllPreferences: preference ${name} has invalid Tuya dp value ${dp}"
            return null
        }
        //def dpMaps   =  DEVICE.tuyaDPs
        Map foundMap
        foundMap = getPreferencesMap(name)
        //logDebug "foundMap = ${foundMap}"
        if (foundMap != null) {
            scaledValue = getScaledPreferenceValue(name, foundMap)
            if (scaledValue != null) {
                logDebug "updateAllPreferences: preference ${foundMap.name} type:${foundMap.type} scaledValue = ${scaledValue} "
                if (foundMap.type == 'enum') {
                    logDebug "updateAllPreferences: <b>ENUM</b> preference ${foundMap.name} type:${foundMap.type} scaledValue = ${scaledValue} "
                }
                String DPType = (foundMap.type in ['number', 'decimal']) ? DP_TYPE_VALUE : foundMap.type == 'bool' ? DP_TYPE_BOOL : foundMap.type == 'enum' ? DP_TYPE_ENUM : 'unknown'
                if (scaledValue != null) {
                    cmds += setRadarParameterTuya(foundMap.name, zigbee.convertToHexString(dpInt, 2), DPType, scaledValue as int)
                }
                else {
                    logDebug "updateAllPreferences: preference ${foundMap.name} type:${foundMap.type} scaledValue = ${scaledValue} "
                }
            }
            else {
                logDebug "updateAllPreferences: preference ${foundMap.name} value not found!"
                return null
            }
        }
        else {
            logDebug "warning: couldn't find tuyaDPs map for preference ${name} (dp = ${dp})"
            return null
        }
    }
    logDebug "updateAllPreferences: ${cmds}"
    return cmds
}

def divideBy100(val) { return (val as int) / 100 }
def multiplyBy100(val) { return (val as int) * 100 }
def divideBy10(val) {
    if (val > 10) { return (val as int) / 10 }
    return (val as int)
}
def multiplyBy10(val) { return (val as int) * 10 }
def divideBy1(val) { return (val as int) / 1 }    //tests

/**
 * Sets the radar parameter for the Tuya Multi Sensor 4 In 1 device.
 * @param parName The name of the parameter to set.
 * @param DPcommand The Tuya DP command to send.
 * @param DPType The type of the Tuya DP command.
 * @param DPval The value to set for the parameter.
 * @return An ArrayList of commands sent to the device.
 */
// TODO - replace this device-specific method !!!
def setRadarParameterTuya(String parName, String DPcommand, String DPType, Integer DPval) {
    ArrayList<String> cmds = []
    def value
    switch (DPType) {
        case DP_TYPE_BOOL :
        case DP_TYPE_ENUM :
            value = zigbee.convertToHexString(DPval as int, 2)
        case DP_TYPE_VALUE :
            value = zigbee.convertToHexString(DPval as int, 8)
            break
        default :
            logWarn "${command}: unsupported DPType ${DPType} !"
            return null
    }
    cmds = sendTuyaCommand(DPcommand, DPType, value)
    logDebug "sending radar parameter ${parName} value ${DPval} (raw=${value}) Tuya dp=${DPcommand} (${zigbee.convertHexToInt(DPcommand)})"
    return cmds
}

// TODO - commands DP to be extracted from the device profile
def resetSettings(/*val*/)     { return radarCommand('resetSettings', isHL0SS9OAradar() ? '71' : '70', DP_TYPE_BOOL) }
def moveSelfTest(/*val*/)      { return radarCommand('moveSelfTest', isHL0SS9OAradar() ? '72' : '76', DP_TYPE_BOOL) }        // check!
def smallMoveSelfTest(/*val*/) { return radarCommand('smallMoveSelfTest', isHL0SS9OAradar() ? '6E' : '77', DP_TYPE_BOOL) }   // check!
def breatheSelfTest(/*val*/)   { return radarCommand('breatheSelfTest', isHL0SS9OAradar() ? '6F' : '78', DP_TYPE_BOOL) }     // check!

// TODO - refactor !!!
def radarCommand(String command, String DPcommand, String DPType) {
    ArrayList<String> cmds = []
    if (!(isHL0SS9OAradar() || is2AAELWXKradar())) {
        logWarn "${command}: unsupported model ${state.deviceProfile} !"
        return null
    }
    def value
    switch (DPType) {
        case DP_TYPE_BOOL :
        case DP_TYPE_ENUM :
        case DP_TYPE_VALUE :
            value = '00'
            cmds = sendTuyaCommand(DPcommand, DPType, value)
            break
        default :
            logWarn "${command}: unsupported DPType ${DPType} !"
            return null
    }
    logDebug "sending ${state.deviceProfile} radarCommand ${command} dp=${DPcommand} (${zigbee.convertHexToInt(DPcommand)})"
    return cmds
}

/**
 * Sends a command to the device.
 * @param command The command to send. Must be one of the commands defined in the DEVICE.commands map.
 * @param val The value to send with the command.
 * @return void
 */
def sendCommand(command=null, val=null) {
    //logDebug "sending command ${command}(${val}))"
    ArrayList<String> cmds = []
    def supportedCommandsMap = DEVICE.commands
    if (supportedCommandsMap == null || supportedCommandsMap == []) {
        logInfo "sendCommand: no commands defined for device profile ${getDeviceGroup()} !"
        return
    }
    // TODO: compare ignoring the upper/lower case of the command.
    def supportedCommandsList =  DEVICE.commands.keySet() as List
    // check if the command is defined in the DEVICE commands map
    if (command == null || !(command in supportedCommandsList)) {
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> must be one of these : ${supportedCommandsList}"
        return
    }
    def func
    try {
        func = DEVICE.commands.find { it.key == command }.value
        if (val != null) {
            cmds = "${func}"(val)
            logInfo "executed <b>$func</b>($val)"
        }
        else {
            cmds = "${func}"()
            logInfo "executed <b>$func</b>()"
        }
    }
    catch (e) {
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})"
        return
    }
    if (cmds != null && cmds != []) {
        sendZigbeeCommands(cmds)
    }
}

/**
 * Returns a list of valid parameters per model based on the device preferences.
 *
 * @return List of valid parameters.
 */
def getValidParsPerModel() {
    List<String> validPars = []
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) {
        // use the preferences to validate the parameters
        validPars = DEVICE.preferences.keySet().toList()
    }
    return validPars
}

/**
 * Called from setPar() method only!
 * Validates the parameter value based on the given dpMap type and scales it if needed.
 *
 * @param dpMap The map containing the parameter type, minimum and maximum values.
 * @param val The value to be validated and scaled.
 * @return The validated and scaled value if it is within the specified range, null otherwise.
 */
def validateAndScaleParameterValue(Map dpMap, String val) {
    def value = null    // validated value - integer, floar
    def scaledValue = null
    logDebug "validateAndScaleParameterValue dpMap=${dpMap} val=${val}"
    switch (dpMap.type) {
        case 'number' :
            value = safeToInt(val, -1)
            scaledValue = value
            // scale the value - added 10/26/2023 also for integer values !
            if (dpMap.scale != null) {
                scaledValue = (value * dpMap.scale) as Integer
            }
            break
        case 'decimal' :
            value = safeToDouble(val, -1.0)
            // scale the value
            if (dpMap.scale != null) {
                scaledValue = (value * dpMap.scale) as Integer
            }
            break
        case 'bool' :
            if (val == '0' || val == 'false')     { value = scaledValue = 0 }
            else if (val == '1' || val == 'true') { value = scaledValue = 1 }
            else {
                log.warn "${device.displayName} sevalidateAndScaleParameterValue: bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>"
                return null
            }
            break
        case 'enum' :
            // val could be both integer or float value ... check if the scaling is different than 1 in dpMap

            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) {
                // we have a float parameter input - convert it to int
                value = safeToDouble(val, -1.0)
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer
            }
            else {
                value = scaledValue = safeToInt(val, -1)
            }
            if (scaledValue == null || scaledValue < 0) {
                // get the keys of dpMap.map as a List
                List<String> keys = dpMap.map.keySet().toList()
                log.warn "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>"
                return null
            }
            break
        default :
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>"
            return null
    }
    //log.warn "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}"
    // check if the value is within the specified range
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) {
        log.warn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}"
        return null
    }
    //log.warn "validateAndScaleParameterValue returning scaledValue=${scaledValue}"
    return scaledValue
}

/**
 * Sets the parameter value for the device.
 * @param par The name of the parameter to set.
 * @param val The value to set the parameter to.
 * @return Nothing.
 */
def setPar(par=null, val=null) {
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) {
        // new method
        logDebug "setPar new method: setting parameter ${par} to ${val}"
        ArrayList<String> cmds = []
        //Boolean validated = false
        if (par == null) {
            log.warn "${device.displayName} setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"
            return
        }
        if (!(par in getValidParsPerModel())) {
            log.warn "${device.displayName} setPar: parameter '${par}' must be one of these : ${getValidParsPerModel()}"
            return
        }
        // find the tuayDPs map for the par
        Map dpMap = getPreferencesMap(par, false)
        if (dpMap == null) {
            log.warn "${device.displayName} setPar: tuyaDPs map not found for parameter <b>${par}</b>"
            return
        }
        if (val == null) {
            log.warn "${device.displayName} setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"
            return
        }
        // convert the val to the correct type and scales it if needed
        def tuyaValue = validateAndScaleParameterValue(dpMap, val as String)
        if (tuyaValue == null) {
            log.warn "${device.displayName} setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"
            return
        }
        // update the device setting
        try {
            device.updateSetting("$par", [value:val, type:dpMap.type])
        }
        catch (e) {
            logWarn "setPar: Exception '${e}'caught while updateSetting <b>$par</b>(<b>$val</b>) type=${dpMap.type}"
            return
        }
        logDebug "parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${tuyaValue} type=${dpMap.type}"
        // search for set function
        String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1]
        String setFunction = "set${capitalizedFirstChar}"
        // check if setFunction method exists
        if (!this.respondsTo(setFunction)) {
            //logDebug "setPar: set function <b>${setFunction}</b> not found"
            // try sending the parameter using the new universal method
            cmds = sendTuyaParameter(dpMap,  par, tuyaValue)
            if (cmds == null || cmds == []) {
                logDebug "setPar: sendTuyaParameter par ${par} tuyaValue ${tuyaValue} returned null or empty list"
                return
            }
            logInfo "setPar: successfluly executed setPar <b>$setFunction</b>(<b>$val</b> (tuyaValue=${tuyaValue}))"
            sendZigbeeCommands(cmds)
            return
        }
        logDebug "setPar: found setFunction=${setFunction}, tuyaValue=${tuyaValue}  (val=${val})"
        // execute the setFunction
        try {
            cmds = "$setFunction"(tuyaValue)
        }
        catch (e) {
            logWarn "setPar: Exception '${e}'caught while processing <b>$setFunction</b>(<b>$tuyaValue</b>) (val=${val}))"
            return
        }
        logDebug "setFunction result is ${cmds}"
        if (cmds == null || cmds == []) {
            logDebug "setPar: <b>$setFunction</b>(<b>$tuyaValue</b>) returned null or empty list"
            // try sending the parameter using the new universal method
            cmds = sendTuyaParameter(dpMap,  par, tuyaValue)
            if (cmds == null || cmds == []) {
                logWarn "setPar: <b>$setFunction</b>(<b>$tuyaValue</b>) returned null or empty list"
                return
            }
            logInfo "setPar: successfluly executed setPar <b>$setFunction</b>(<b>$tuyaValue</b>)"
            sendZigbeeCommands(cmds)
            return
        }
        logInfo "setPar: successfluly executed setPar <b>$setFunction</b>(<b>$tuyaValue</b>)"
        sendZigbeeCommands(cmds)
        return
    }
}

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap
// TODO - reuse it !!!
def sendTuyaParameter(Map dpMap, String par, tuyaValue) {
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}"
    ArrayList<String> cmds = []
    if (dpMap == null) {
        log.warn "${device.displayName} sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>"
        return null
    }
    String dp = zigbee.convertToHexString(dpMap.dp, 2)
    if (dpMap.dp <= 0 || dpMap.dp >= 256) {
        log.warn "${device.displayName} sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>"
        return null
    }
    String dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null
    //log.debug "dpType = ${dpType}"
    if (dpType == null) {
        log.warn "${device.displayName} sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>"
        return null
    }
    // sendTuyaCommand
    def dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2)
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} "
    cmds = sendTuyaCommand(dp, dpType, dpValHex)
    return cmds
}

/**
 * Updates the Tuya version of the device based on the application version.
 * If the Tuya version has changed, updates the device data value and logs the change.
 */
void updateTuyaVersion() {
    if (!isTuya()) {
        return
    }

    def application = device.getDataValue('application')
    if (application != null) {
        Integer ver
        try {
            ver = zigbee.convertHexToInt(application)
        }
        catch (e) {
            logWarn "exception caught while converting application version ${application} to tuyaVersion"
            return
        }
        def str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString()
        if (device.getDataValue('tuyaVersion') != str) {
            device.updateDataValue('tuyaVersion', str)
            logInfo "tuyaVersion set to $str"
        }
    }
    else {
        logDebug 'application version is NULL'
    }
}

/**
 * Returns the device name and profile based on the device model and manufacturer.
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value.
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value.
 * @return A list containing the device name and profile.
 */
def getDeviceNameAndProfile(model=null, manufacturer=null) {
    def deviceName         = UNKNOWN
    def deviceProfile      = UNKNOWN
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN
    deviceProfilesV2.each { profileName, profileMap ->
        profileMap.fingerprints.each { fingerprint ->
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) {
                deviceProfile = profileName
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV2[deviceProfile].deviceJoinName ?: UNKNOWN
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}"
                return [deviceName, deviceProfile]
            }
        }
    }
    if (deviceProfile == UNKNOWN) {
        logInfo "<b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}"
    }
    return [deviceName, deviceProfile]
}

// called from  initializeVars( fullInit = true)
def setDeviceNameAndProfile(model=null, manufacturer=null) {
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer)
    if (deviceProfile == null || deviceProfile == UNKNOWN) {
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}"
        // don't change the device name when unknown
        state.deviceProfile = UNKNOWN
    }
    def dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN
    def dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN
    if (deviceName != NULL && deviceName != UNKNOWN) {
        device.setName(deviceName)
        state.deviceProfile = deviceProfile
        device.updateSetting('forcedProfile', [value:deviceProfilesV2[deviceProfile].description, type:'enum'])
        //logDebug "after : forcedProfile = ${settings.forcedProfile}"
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>"
    } else {
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!"
    }
}

private String clusterLookup(Object cluster) {
    if (cluster) {
        int clusterInt = cluster in String ? hexStrToUnsignedInt(cluster) : cluster.toInteger()
        String label = zigbee.clusterLookup(clusterInt)?.clusterLabel
        String hex = "0x${intToHexStr(clusterInt, 2)}"
        return label ? "${label} (${hex}) cluster" : "cluster ${hex}"
    }
    return 'unknown cluster'
}

def testTuyaCmd(dpCommand, dpValue, dpTypeString) {
    //ArrayList<String> cmds = []
    def dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null
    def dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue
    log.warn " sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}"
    sendZigbeeCommands(sendTuyaCommand(dpCommand, dpType, dpValHex))
}

def inputIt(String param, boolean debug=false) {
    Map input = [:]
    Map foundMap = [:]
    if (!(param in DEVICE.preferences)) {
        if (debug) log.warn "inputIt: preference ${param} not defined for this device!"
        return null
    }
    def preference
    //boolean isTuyaDP
    try {
        preference = DEVICE.preferences["$param"]
    }
    catch (e) {
        if (debug) log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}"
        return null
    }
    //  check for boolean values
    try {
        if (preference in [true, false]) {
            if (debug) log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!"
            return null
        }
    }
    catch (e) {
        if (debug) log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}"
        return null
    }

    try {
        isTuyaDP = preference.isNumber()
    }
    catch (e) {
        if (debug) log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}"
        return null
    }

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}"
    foundMap = getPreferencesMap(param)
    //if (debug) log.debug "foundMap = ${foundMap}"
    if (foundMap == null) {
        if (debug) log.warn "inputIt: map not found for param '${param}'!"
        return null
    }
    if (foundMap.rw != 'rw') {
        if (debug) log.warn "inputIt: param '${param}' is read only!"
        return null
    }
    input.name = foundMap.name
    input.type = foundMap.type    // bool, enum, number, decimal
    input.title = foundMap.title
    input.description = foundMap.description
    if (input.type in ['number', 'decimal']) {
        if (foundMap.min != null && foundMap.max != null) {
            input.range = "${foundMap.min}..${foundMap.max}"
        }
        if (input.range != null && input.description != null) {
            input.description += "<br><i>Range: ${input.range}</i>"
            if (foundMap.unit != null && foundMap.unit != '') {
                input.description += " <i>(${foundMap.unit})</i>"
            }
        }
    }
    /* groovylint-disable-next-line SpaceAfterClosingBrace */
    else if (input.type == 'enum') {
        input.options = foundMap.map
    }/*
    else if (input.type == "bool") {
        input.options = ["true", "false"]
    }*/
    else {
        if (debug) log.warn "inputIt: unsupported type ${input.type} for param '${param}'!"
        return null
    }
    if (input.defVal != null) {
        input.defVal = foundMap.defVal
    }
    return input
}

def testParse(description) {
    logWarn "testParse: ${description}"
    parse(description)
    log.trace '---end of testParse---'
}

String unix2formattedDate(unixTime) {
    try {
        if (unixTime == null) return null
        def date = new Date(unixTime.toLong())
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone)
    } catch (Exception e) {
        logDebug "Error formatting date: ${e.message}. Returning current time instead."
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone)
    }
}

def formattedDate2unix(formattedDate) {
    try {
        if (formattedDate == null) return null
        def date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate)
        return date.getTime()
    } catch (Exception e) {
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead."
        return now()
    }
}

@Field static final Map<Integer, Map> SettableParsFieldMap = new ConcurrentHashMap<>().withDefault {
    new ConcurrentHashMap<Integer, Map>()
}

def getSettableParsList() {
    if (device?.id == null) {
        return ['SEE LOGS']
    }
    if (SettableParsFieldMap.get(device?.id)) {
        return SettableParsFieldMap.get(device?.id).pars.keySet().toList()
    }
    // put a map in the SettableParsFieldMap for the device.id if it doesn't exist, containing the settable parameters
    Map settableParsMap = [:]
    settableParsMap['pars'] = DEVICE.preferences
    SettableParsFieldMap.put(device?.id, settableParsMap)
    def result = SettableParsFieldMap.get(device?.id).pars.keySet().toList()
    //log.trace  "${result}"
    log.warn "stored ${SettableParsFieldMap.get(device?.id)}"
    return result
}

def validateAndFixPreferences() {
    //logDebug "validateAndFixPreferences: preferences=${DEVICE.preferences}"
    if (DEVICE.preferences == null || DEVICE.preferences == [:]) {
        logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceGroup()}"
        return null
    }
    def validationFailures = 0
    def validationFixes = 0
    def oldSettingValue
    def newValue
    String settingType
    DEVICE.preferences.each {
        Map foundMap = getPreferencesMap(it.key)
        if (foundMap == null) {
            logDebug "validateAndFixPreferences: map not found for preference ${it.key}"    // 10/21/2023 - sevirity lowered to debug
            return null
        }
        settingType = device.getSettingType(it.key)
        oldSettingValue = device.getSetting(it.key)
        if (settingType == null) {
            logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}"
            return null
        }
        //logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}"
        if (foundMap.type != settingType) {
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) "
            validationFailures ++
            // remove the setting and create a new one using the foundMap.type
            try {
                device.removeSetting(it.key)
                logDebug "validateAndFixPreferences: removing setting ${it.key}"
            }
            catch (e) {
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}"
                return null
            }
            // first, try to use the old setting value
            try {
                // correct the oldSettingValue type
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() }
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() }
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 }
                else if (foundMap.type == 'enum') {
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) {
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0'
                    }
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) {
                        newValue = String.format('%.2f', oldSettingValue)
                    }
                    else {
                        // format the settingValue as a string of the integer value
                        newValue = String.format('%d', oldSettingValue)
                    }
                }
                //
                device.updateSetting(it.key, [value:newValue, type:foundMap.type])
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}"
                validationFixes ++
            }
            catch (e) {
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}"
                // change the settingValue to the foundMap default value
                try {
                    settingValue = foundMap.defVal
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type])
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} "
                    validationFixes ++
                }
                catch (e2) {
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>"
                    return null
                }
            }
        }
    }
    logDebug "validateAndFixPreferences: validationFailures = ${validationFailures} validationFixes = ${validationFixes}"
}

/* groovylint-disable-next-line UnusedMethodParameter */
def test( val ) {
    /*
    def result = inputIt( val, debug=true )
    logWarn "test inputIt(${val}) = ${result}"
    */
    log.trace settings
    resetPreferencesToDefaults(true)
    log.trace settings
    /*
    settings.each { k, v ->
        String settingName = k
        // remove  [ and ] from the setting name
        settingName = settingName.replaceAll("\\[|\\]", "")
        logWarn "settings ${k} = ${v}   settingName = ${settingName}"
        logWarn "settings ${k} = ${v} type = ${getSettingType('advancedOptions')}"

    }
*/
/*
    settings.each { k, v ->
        def x = device.getSettingType(k)
        log.info ("settings ${k} = ${v} (${x})")
    }
*/
//validateAndFixPreferences()
//resetPreferencesToDefaults(true)
//getPreferencesMap( "motionReset", true)
}
