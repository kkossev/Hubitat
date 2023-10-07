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
 * ver. 1.6.0  2023-10-08 kkossev  - (dev. branch) application version is updated; major refactoring of the preferences input; all preference settings are reset to defaults when changing device profile; added 'all' attribute; present state 'motionStarted' in a human-readable form.
 *                                   setPar and sendCommand major refactoring +parameters changed from enum to string; 
 *
 *                                   TODO: add rtt measurement for ping()
 *                                   TODO: add extraPreferences to deviceProfilesV2
 *                                   TODO: command for black radar LED
 *                                   TODO: TS0601_IJXVKHD0_RADAR preferences - send events
 *                                   TODO: TS0601_IJXVKHD0_RADAR preferences configuration
 *                                   TODO: TS0225_HL0SS9OA_RADAR - add presets
 *                                   TODO: TS0225_HL0SS9OA_RADAR - add enum type pars in setPars !
 *                                   TOOD: Tuya 2in1 illuminance_interval (dp=102) !
 *                                   TODO: humanMotionState - add preference: enum "disabled", "enabled", "enabled w/ timing" ...; add delayed event
 *                                   TODO: publish examples of SetPar usage : https://community.hubitat.com/t/4-in-1-parameter-for-adjusting-reporting-time/115793/12?u=kkossev
 *                                   TODO: ignore invalid humidity reprots (>100 %)
 *                                   TODO: use getKeepTimeOpts() for processing dp=0x0A (10) keep time ! ( 2-in-1 time is wrong)
 *                                   TODO: RADAR profile devices are not automtically updated from 'UNKNOWN'!
 *                                   TODO: add to state 'last battery' the time when the battery was last reported.
 *                                   TODO: check the bindings commands in configure()
 *                                   TODO: implement getActiveEndpoints()
*/

def version() { "1.6.0" }
def timeStamp() {"2023/10/08 1:27 AM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol
import hubitat.zigbee.clusters.iaszone.ZoneStatus
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap

@Field static final Boolean _DEBUG = false
@Field static final Boolean _TRACE_ALL = false

metadata {
    definition (name: "Tuya Multi Sensor 4 In 1", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Multi%20Sensor%204%20In%201/Tuya%20Multi%20Sensor%204%20In%201.groovy", singleThreaded: true ) {
        capability "Sensor"
        capability "Configuration"
        capability "Battery"
        capability "MotionSensor"
        capability "TemperatureMeasurement"        
        capability "RelativeHumidityMeasurement"
        capability "IlluminanceMeasurement"
        capability "TamperAlert"
        capability "PowerSource"
        capability "HealthCheck"
        capability "Refresh"
        //capability "PushableButton"        // uncomment for TS0202 _TZ3210_cwamkvua [Motion Sensor and Scene Switch]
        //capability "DoubleTapableButton"
        //capability "HoldableButton"

        attribute "all", "string"
        attribute "batteryVoltage", "number"
        attribute "healthStatus", "enum", ["offline", "online"]
        attribute "distance", "number"              // Tuya Radar
        attribute "unacknowledgedTime", "number"    // AIR models
        attribute "motionType", "enum",  ["none", "presence", "peacefull", "smallMove", "largeMove"]    // blackSensor
        attribute "existance_time", "number"        // BlackSquareRadar & LINPTECH
        attribute "leave_time", "number"            // BlackSquareRadar only
        
        attribute "radarSensitivity", "number" 
        attribute "detectionDelay", "decimal" 
        attribute "fadingTime", "decimal" 
        attribute "minimumDistance", "decimal" 
        attribute "maximumDistance", "decimal"
        attribute "radarStatus", "enum", ["checking", "check_success", "check_failure", "others", "comm_fault", "radar_fault"] 
        attribute "humanMotionState", "enum", TS0225humanMotionState.values() as List<String>
        
        command "configure", [[name: "Configure the sensor after switching drivers"]]
        command "initialize", [[name: "Initialize the sensor after switching drivers.  \n\r   ***** Will load device default values! *****" ]]
        command "setMotion", [[name: "setMotion", type: "ENUM", constraints: ["No selection", "active", "inactive"], description: "Force motion active/inactive (for tests)"]]
        command "refresh",   [[name: "May work for some DC/mains powered sensors only"]] 
        command "setPar", [
                [name:"par", type: "STRING", description: "preference parameter name", constraints: ["STRING"]],
                [name:"val", type: "STRING", description: "preference parameter value", constraints: ["STRING"]]
        ]
        command "sendCommand", [[name: "sendCommand", type: "STRING", constraints: ["STRING"], description: "send Tuya Radar commands"]]
        if (_DEBUG == true) {
            command "testTuyaCmd", [
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"] 
            ]
            command "testParse", [[name:"val", type: "STRING", description: "description", constraints: ["STRING"]]]
            command "test", [[name:"val", type: "STRING", description: "preference parameter value", constraints: ["STRING"]]]
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
            input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "<i>Display sensor states on HE log page. The recommended value is <b>true</b></i>", defaultValue: true)
            input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. The recommended value is <b>false</b></i>", defaultValue: true)
            if (("motionReset" in DEVICE?.preferences)) {
                input (name: "motionReset", type: "bool", title: "<b>Reset Motion to Inactive</b>", description: "<i>Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b></i>", defaultValue: false)
                if (motionReset.value == true) {
                    input ("motionResetTimer", "number", title: "After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds", description: "", range: "0..7200", defaultValue: 60)
                }
            }
            if (false) {    // TODO!
                input ("temperatureOffset", "decimal", title: "Temperature offset", description: "Select how many degrees to adjust the temperature.", range: "-100..100", defaultValue: 0.0)
                input ("humidityOffset", "decimal", title: "Humidity offset", description: "Enter a percentage to adjust the humidity.", range: "-50..50",  defaultValue: 0.0)
            }
        }
        if (("reportingTime4in1" in DEVICE?.preferences)) {    // 4in1()
            input ("reportingTime4in1", "number", title: "<b>4-in-1 Reporting Time</b>", description: "<i>4-in-1 Reporting Time configuration, minutes.<br>0 will enable real-time (10 seconds) reporting!</i>", range: "0..7200", defaultValue: DEFAULT_REPORTING_4IN1)
        }
        if (("ledEnable" in DEVICE?.preferences)) {            // 4in1()
            input (name: "ledEnable", type: "bool", title: "<b>Enable LED</b>", description: "<i>Enable LED blinking when motion is detected (4in1 only)</i>", defaultValue: true)
        }
        if (DEVICE?.device?.isIAS == true || ("keepTime" in DEVICE?.preferences)) {
            input (name: "keepTime", type: "enum", title: "<b>Motion Keep Time</b>", description:"Select PIR sensor keep time (s)", options: getKeepTimeOpts().options, defaultValue: getKeepTimeOpts().defaultValue)
        }
        if (DEVICE?.device?.isIAS == true || ("sensitivity" in DEVICE?.preferences)) {
            input (name: "sensitivity", type: "enum", title: "<b>Motion Sensitivity</b>", description:"Select PIR sensor sensitivity", options: sensitivityOpts.options, defaultValue: sensitivityOpts.defaultValue)
        }
        if (advancedOptions == true || advancedOptions == false) { 
            if ((DEVICE?.capabilities?.IlluminanceMeasurement == true) ) {
                input ("luxThreshold", "number", title: "<b>Lux threshold</b>", description: "Minimum change in the lux which will trigger an event", range: "0..999", defaultValue: 5)   
                input name: "illuminanceCoeff", type: "decimal", title: "<b>Illuminance Correction Coefficient</b>", description: "<i>Illuminance correction coefficient, range (0.10..10.00)</i>", range: "0.10..10.00", defaultValue: 1.00
            }
        }
        if (("DistanceMeasurement" in DEVICE?.capabilities)) {              
            input (name: "ignoreDistance", type: "bool", title: "<b>Ignore distance reports</b>", description: "If not used, ignore the distance reports received every 1 second!", defaultValue: true)
        }
        if (isZY_M100Radar()) {
            // itterate over all DEVICE.preferences and inputIt
            DEVICE.preferences.each { key, value ->
               input inputIt(key)
            }
        }
        else {
            // isRadar() preferences  TODO: itterate over all DEVICE.preferences 
            if ("radarSensitivity" in DEVICE?.preferences) {    // TODO: range is 0..10 for HumanPresenceSensorFall and HumanPresenceSensorScene
                //input ("radarSensitivity", "number", title: "<b>Radar sensitivity (1..9)</b>", description: "", range: "0..9", defaultValue: 7)
                input inputIt("radarSensitivity")
            }
            if ("detectionDelay" in DEVICE?.preferences) {              //input ("detectionDelay", "decimal", title: "<b>Detection delay, seconds</b>", description: "", range: "0.0..120.0", defaultValue: 0.2)   
                input inputIt("detectionDelay")
            }
            if ("fadingTime" in DEVICE?.preferences) {
                input inputIt("fadingTime")    // will display "Which?" if inputIt return null ... 
            }
            if ("minimumDistance" in DEVICE?.preferences) {         // input ("minimumDistance", "decimal", title: "<b>Minimum detection distance, meters</b>", description: "", range: "0.0..9.5", defaultValue: 0.25)
                input inputIt("minimumDistance")
            }
            if ("maximumDistance" in DEVICE?.preferences) {
                input inputIt("maximumDistance")                    // input ("maximumDistance", "decimal", title: "<b>Maximum detection distance, meters</b>", description: "", range: "0.0..9.5", defaultValue: 8.0)   
            }
        }

        //    if (isHL0SS9OAradar() || is2AAELWXKradar()) {
        if ("presenceKeepTime" in DEVICE?.preferences) {
            input ("presenceKeepTime", "number", title: "<b>Presence Keep Time (0..28800), seconds</b>", description: "<i>Fading time</i>",  range: "0..28800", defaultValue: 30)
        }
        if ("ledIndicator" in DEVICE?.preferences) {
            input (name:"ledIndicator", type: "bool", title: "<b>Enable LED</b>", description: "<i>Enable LED blinking when motion is detected</i>", defaultValue: false)
        }
        if ("radarAlarmMode" in DEVICE?.preferences) {
            input (name: "radarAlarmMode", type: "enum", title: "<b>Radar Alarm Mode</b>", description:"Select radar alarm mode", options: TS0225alarmMode.options, defaultValue: TS0225alarmMode.defaultValue)
        }
        if ("radarAlarmVolume" in DEVICE?.preferences) {
            input (name: "radarAlarmVolume", type: "enum", title: "<b>Radar Alarm Volume</b>", description:"Select radar alarm volume", options: TS0225alarmVolume.options, defaultValue: TS0225alarmVolume.defaultValue)
        }
        if ("radarAlarmTime" in DEVICE?.preferences) {
            input ("radarAlarmTime", "number", title: "<b>Radar Alarm Time</b>", description: "<i>Alarm sounding duration, (1..60)seconds</i>",  range: "1..60", defaultValue: 2)   
        }

        if ("textLargeMotion" in DEVICE?.preferences) {
            input (name: 'textLargeMotion', type: 'text', title: "<b>Motion Detection Settigs &#8680;</b>", description: "<b>Settings for movement types such as walking, trotting, fast running, circling, jumping and other movements </b>")        
        }
        if ("motionFalseDetection" in DEVICE?.preferences) {
            input (name:"motionFalseDetection", type: "bool", title: "<b>Motion False Detection</b>", description: "<i>Disable/Enable motion false detection</i>", defaultValue: true)
        }
        if ("motionDetectionSensitivity" in DEVICE?.preferences) {
            input inputIt("motionDetectionSensitivity")
        }
        if ("motionMinimumDistance" in DEVICE?.preferences) {
            input ("motionMinimumDistance", "decimal", title: "<b>Motion Minimum Distance</b>", description: "<i>Motion(movement) minimum distance, (0.0..10.0) meters</i>", range: "0.0..10.0", defaultValue: 0.0)
        }
        if ("motionDetectionDistance" in DEVICE?.preferences) {
            input inputIt("motionDetectionDistance")
        }       

        if ("textSmallMotion" in DEVICE?.preferences) {
            input (name: 'textSmallMotion', type: 'text', title: "<b>Small Motion Detection Settigs &#8658;</b>", description: "<b>Settings for small movement types such as tilting the head, waving, raising the hand, flicking the body, playing with the mobile phone, turning over the book, etc.. </b>")        
        }
        if ("smallMotionDetectionSensitivity" in DEVICE?.preferences) {
            input ("smallMotionDetectionSensitivity", "number", title: "<b>Small Motion Detection Sensitivity</b>", description: "<i>Small motion detection sensitivity, (0..10)</i>",  range: "0..10", defaultValue: 7)   
        }
        if ("smallMotionMinimumDistance" in DEVICE?.preferences) {
            input ("smallMotionMinimumDistance", "decimal", title: "<b>Small Motion Minimum Distance</b>", description: "<i>Small motion minimum distance, (0.0..6.0) meters</i>", range: "0.0..6.0", defaultValue: 5.0)
        }
        if ("smallMotionDetectionDistance" in DEVICE?.preferences) {
            input ("smallMotionDetectionDistance", "decimal", title: "<b>Small Motion Detection Distance</b>", description: "<i>Small motion detection maximum distance, (0.0..6.0) meters </i>", range: "0.0..6.0", defaultValue: 5.0)
        }

        if ("textStaticDetection" in DEVICE?.preferences) {
            input (name: 'textStaticDetection', type: 'text', title: "<b>Static Detection Settigs &#8680;</b>", description: "<b>The sensor can detect breathing within a certain range to determine people presence in the detection area (for example, while sleeping or reading).</b>")        
        }
        if ("breatheFalseDetection" in DEVICE?.preferences) {
            input (name:"breatheFalseDetection", type: "bool", title: "<b>Breathe False Detection</b>", description: "<i>Disable/Enable breathe false detection</i>", defaultValue: false)
        }
        if ("staticDetectionSensitivity" in DEVICE?.preferences) {
            input inputIt("staticDetectionSensitivity")
        }
        if ("staticDetectionMinimumDistance" in DEVICE?.preferences) {
            input ("staticDetectionMinimumDistance", "decimal", title: "<b>Static Detection Minimum Distance</b>", description: "<i>Static detection minimum distance, (0.0..6.0) meters</i>", range: "0.0..6.0", defaultValue: 0.0)
        }
        if ("staticDetectionDistance" in DEVICE?.preferences) {
            input ("staticDetectionDistance", "decimal", title: "<b>Static Detection Distance</b>", description: "<i>Static detection maximum distance,  (0.0..6.0) meters</i>", range: "0.0..6.0", defaultValue: 6.0)
        }
        if ("vacancyDelay" in DEVICE?.preferences) {
            input (name: "vacancyDelay", type: "number", title: "Vacancy Delay", description: "Select vacancy delay (0..1000), seconds", range: "0..1000", defaultValue: 10)   
        }
        if ("ledStatusAIR" in DEVICE?.preferences) {        // HumanPresenceSensorAIR
            input (name: "ledStatusAIR", type: "enum", title: "LED Status", description:"Select LED Status", defaultValue: -1, options: ledStatusOptions)
        }
        if ("detectionMode" in DEVICE?.preferences) {       // HumanPresenceSensorAIR
            input (name: "detectionMode", type: "enum", title: "Detection Mode", description:"Select Detection Mode", defaultValue: -1, options: detectionModeOptions)
        }
        if ("vSensitivity" in DEVICE?.preferences) {        // HumanPresenceSensorAIR
            input (name: "vSensitivity", type: "enum", title: "V Sensitivity", description:"Select V Sensitivity", defaultValue: -1, options: vSensitivityOptions)
        }
        if ("oSensitivity" in DEVICE?.preferences) {        // HumanPresenceSensorAIR
            input (name: "oSensitivity", type: "enum", title: "O Sensitivity", description:"Select O Sensitivity", defaultValue: -1, options: oSensitivityOptions)
        }
        if ("inductionTime" in DEVICE?.preferences) {       // BlackPIRsensor
            input (name: "inductionTime", type: "number", title: "Induction Time", description: "Induction time (24..300) seconds", range: "24..300", defaultValue: 24)
        }
        if ("targetDistance" in DEVICE?.preferences) {      // BlackPIRsensor
            input (name: "targetDistance", type: "enum", title: "Target Distance", description:"Select target distance", defaultValue: -1, options: blackSensorDistanceOptions)
        }
        if (("indicatorLight" in DEVICE?.preferences)) {
            input (name: "indicatorLight", type: "enum", title: "Indicator Light", description: "Red LED is lit when presence detected", defaultValue: "0", options: blackRadarLedOptions)  
        }
        
        input (name: "advancedOptions", type: "bool", title: "<b>Advanced Options</b>", description: "<i>Enables showing the advanced options/preferences. Hit F5 in the browser to refresh the Preferences list<br>.May not work for all device types!</i>", defaultValue: false)
        if (advancedOptions == true) {
            input (name: "forcedProfile", type: "enum", title: "<b>Device Profile</b>", description: "<i>Forcely change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!</i>", 
                   options: getDeviceProfilesMap())
            if ("Battery" in DEVICE?.capabilities) {
                input (name: "batteryDelay", type: "enum", title: "<b>Battery Events Delay</b>", description:"<i>Select the Battery Events Delay<br>(default is <b>no delay</b>)</i>", options: delayBatteryOpts.options, defaultValue: delayBatteryOpts.defaultValue)
            }
            if ("invertMotion" in DEVICE?.preferences) {
               input (name: "invertMotion", type: "bool", title: "<b>Invert Motion Active/Not Active</b>", description: "<i>Some Tuya motion sensors may report the motion active/inactive inverted...</i>", defaultValue: false)
            }
        }
        input (name: "allStatusTextEnable", type:  "bool", title: "<b>Enable 'all' Status Attribute Creation?</b>",  description: "<i>Status attribute for Devices/Rooms</i>", defaultValue: false)
    }
}


@Field static final Boolean _IGNORE_ZCL_REPORTS = true

@Field static final String UNKNOWN =  'UNKNOWN'
@Field static final Map inductionStateOptions = [ "0":"Occupied", "1":"Vacancy" ]
@Field static final Map vSensitivityOptions =   [ "0":"Speed Priority", "1":"Standard", "2":"Accuracy Priority" ]    // HumanPresenceSensorAIR
@Field static final Map oSensitivityOptions =   [ "0":"Sensitive", "1":"Normal", "2":"Cautious" ]                    // HumanPresenceSensorAIR
@Field static final Map detectionModeOptions =  [ "0":"General Model", "1":"Temporary Stay", "2":"Basic Detecton", "3":"PIR Sensor Test" ]    // HumanPresenceSensorAIR
@Field static final Map ledStatusOptions =      [ "0" : "Switch On", "1" : "Switch Off", "2" : "Default"  ]          // HumanPresenceSensorAIR
@Field static final Map blackSensorDistanceOptions =   [ "0":"0.5 m", "1":"1.0 m", "2":"1.5 m", "3":"2.0 m", "4":"2.5 m", "5":"3.0 m", "6":"3.5 m", "7":"4.0 m", "8":"4.5 m", "9":"5.0 m" ]    // BlackSensor - not working!
@Field static final Map blackSensorMotionTypeOptions =   [ "0":"None", "1":"Presence", "2":"Peacefull", "3":"Small Move", "4":"Large Move"]    // BlackSensor - not working!
@Field static final Map blackRadarLedOptions =      [ "0" : "Off", "1" : "On" ]      // HumanPresenceSensorAIR
@Field static final Map radarSelfCheckingStatus =  [ "0":"checking", "1":"check_success", "2":"check_failure", "3":"others", "4":"comm_fault", "5":"radar_fault",  ] 

@Field static final Map TS0225SelfCheckingStatus =  [ "0":"check_success", "1":"checking", "2":"check_failure", "3":"others", "4":"comm_fault", "5":"radar_fault",  ] 
@Field static final Map TS0225humanMotionState = [ "0": "none", "1": "moving", "2": "small_move", "3": "stationary"  ]
@Field static final Map TS0225alarmMode =   [ defaultValue: 1, options: [0: "Armed", 1: "Disarmed", 2: "Alarm"]]
@Field static final Map TS0225alarmVolume = [ defaultValue: 3, options: [0: "Low", 1: "Middle", 2: "High", 3: "Mute" ]]
@Field static final Map TS0225quickSetup = [ "0": "Living Room", "1": "Bedroom", "2": "Washroom", "3": "Aisle", "4": "Kitchen"  ]
@Field static final Map TS0225optimizations = [ 
    defaultValue: 0, 
    options: [
        0: " No optimizatioons", 
        2: "+Hide humanMotionState change logs", 
        1: "+Hide illuminance change logs", 
        3: "+Ignore move<>small_move changes",
        4: "+Ignore all humanMotionState changes"
    ]
]


/*
                               LivingRoom    Bedroom    Washroom    Aisle    Kitchen
Motion detection distance    -     800        500        300        900        1000
Motion detection sensitivity -     6x         6x         5x         8x         6x
Small motion detection distance -  600        400        400        400        600
Small motion detection sensitivity 9          6          8          5          8
Static detection distance          600        600        400        0          0
Static detection sensitivity       9x         9x         8x         8x         8x
*/

@Field static final Map sensitivityOpts =  [ defaultValue: 2, options: [0: 'low', 1: 'medium', 2: 'high']]
@Field static final Map keepTime4in1Opts = [ defaultValue: 0, options: [0: '10 seconds', 1: '30 seconds', 2: '60 seconds', 3: '120 seconds', 4: '240 seconds', 5: '480 seconds']]
@Field static final Map keepTime2in1Opts = [ defaultValue: 0, options: [0: '10 seconds', 1: '30 seconds', 2: '60 seconds', 3: '120 seconds']]
@Field static final Map keepTime3in1Opts = [ defaultValue: 0, options: [0: '30 seconds', 1: '60 seconds', 2: '120 seconds']]
@Field static final Map keepTimeIASOpts =  [ defaultValue: 0, options: [0: '30 seconds', 1: '60 seconds', 2: '120 seconds']]
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']]
@Field static final Map delayBatteryOpts = [ defaultValue: 0, options: [0: 'No delay', 30: '30 seconds', 3600: '1 hour', 14400: '4 hours', 28800: '8 hours', 43200: '12 hours']]

def getKeepTimeOpts() { return is4in1() ? keepTime4in1Opts : is3in1() ? keepTime3in1Opts : is2in1() ? keepTime2in1Opts : keepTimeIASOpts}

@Field static final Integer presenceCountTreshold = 4
@Field static final Integer defaultPollingInterval = 3600
@Field static final Integer DEFAULT_REPORTING_4IN1 = 5    // time in minutes

def getDeviceGroup()     { state.deviceProfile ?: "UNKNOWN" }
def getDEVICE()          { deviceProfilesV2[getDeviceGroup()] }
def getDeviceProfiles()      { deviceProfilesV2.keySet() }
def getDeviceProfilesMap()   {deviceProfilesV2.values().description as List<String>}
def is4in1() { return getDeviceGroup().contains("TS0202_4IN1") }
def is3in1() { return getDeviceGroup().contains("TS0601_3IN1") }
def is2in1() { return getDeviceGroup().contains("TS0601_2IN1") }
def isMotionSwitch() { return getDeviceGroup().contains("TS0202_MOTION_SWITCH") }
def isIAS()  { DEVICE?.device?.isIAS == true  }
def isTS0601_PIR() { (DEVICE.device?.type == "PIR") && (("keepTime" in DEVICE.preferences) || ("sensitivity" in DEVICE.preferences)) }
//def isConfigurable() { return isIAS() }   // TS0202 models ['_TZ3000_mcxw5ehu', '_TZ3000_msl6wxk9']

def isZY_M100Radar()             { return getDeviceGroup().contains("TS0601_TUYA_RADAR") } 
def isBlackPIRsensor()    { return getDeviceGroup().contains("TS0601_PIR_PRESENCE") }     
def isBlackSquareRadar()  { return getDeviceGroup().contains("TS0601_BLACK_SQUARE_RADAR") }
def isHumanPresenceSensorAIR()     { return getDeviceGroup().contains("TS0601_PIR_AIR") }
def isHumanPresenceSensorScene()   { return getDeviceGroup().contains("TS0601_RADAR_MIR-HE200-TY") }
def isHumanPresenceSensorFall()    { return getDeviceGroup().contains("TS0601_RADAR_MIR-TY-FALL") }
def isYXZBRB58radar()              { return getDeviceGroup().contains("TS0601_YXZBRB58_RADAR") }
def isSXM7L9XAradar()              { return getDeviceGroup().contains("TS0601_SXM7L9XA_RADAR") }
def isIJXVKHD0radar()              { return getDeviceGroup().contains("TS0601_IJXVKHD0_RADAR") }
def isHL0SS9OAradar()              { return getDeviceGroup().contains("TS0225_HL0SS9OA_RADAR") }
def is2AAELWXKradar()              { return getDeviceGroup().contains("TS0225_2AAELWXK_RADAR") }    // same as HL0SS9OA, but another set of DPs
def isSBYX0LM6radar()              { return getDeviceGroup().contains("TS0601_SBYX0LM6_RADAR") }
def isLINPTECHradar()              { return getDeviceGroup().contains("TS0225_LINPTECH_RADAR") }
def isEGNGMRZHradar()              { return getDeviceGroup().contains("TS0225_EGNGMRZH_RADAR") }
def isKAPVNNLKradar()              { return getDeviceGroup().contains("TS0601_KAPVNNLK_RADAR") }



def isChattyRadarReport(descMap) { 
    if ((isZY_M100Radar() || isSBYX0LM6radar()) && (settings?.ignoreDistance == true) ) {
        return (descMap?.clusterId == "EF00" && (descMap.command in ["01", "02"]) && descMap.data?.size > 2  && descMap.data[2] == "09") 
    }
    else if ((isYXZBRB58radar() || isSXM7L9XAradar()) && (settings?.ignoreDistance == true)) {
        return (descMap?.clusterId == "EF00" && (descMap.command in ["01", "02"]) && descMap.data?.size > 2  && descMap.data[2] == "6D") 
    } /*
    else if (isKAPVNNLKradar() && settings?.ignoreDistance == true) {
        return (descMap?.clusterId == "EF00" && (descMap.command in ["01", "02"]) && descMap.data?.size > 2  && descMap.data[2] == "13") 
    }  */
//    if (isHL0SS9OAradar()) {    // humanMotionState
//        return (descMap?.clusterId == "EF00" && (descMap.command in ["01", "02"]) && descMap.data?.size > 2  && descMap.data[2] == "0B") 
//    }
    else {
        return false
    }
}


@Field static final Map deviceProfilesV2 = [
    "TS0202_4IN1"  : [
            description   : "Tuya 4in1 (motion/temp/humi/lux) sensor",
            models        : ["TS0202"],
            device        : [type: "4IN1", isIAS:true, powerSource: "dc", isSleepy:true],    // check powerSource and isSleepy!
            capabilities  : ["MotionSensor": true, "TemperatureMeasurement": true, "RelativeHumidityMeasurement": true, "IlluminanceMeasurement": true, "tamper": true, "Battery": true],
            preferences   : ["motionReset":true, "reportingTime4in1":true, "ledEnable":true],
            commands      : ["reportingTime4in1", "reportingTime4in1"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00", outClusters:"0019,000A", model:"TS0202",  manufacturer:"_TZ3210_zmy9hjay", deviceJoinName: "Tuya TS0202 Multi Sensor 4 In 1"],        // pairing: double click!
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00", outClusters:"0019,000A", model:"5j6ifxj", manufacturer:"_TYST11_i5j6ifxj", deviceJoinName: "Tuya TS0202 Multi Sensor 4 In 1"],       
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00", outClusters:"0019,000A", model:"hfcudw5", manufacturer:"_TYST11_7hfcudw5", deviceJoinName: "Tuya TS0202 Multi Sensor 4 In 1"],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00", outClusters:"0019,000A", model:"TS0202",  manufacturer:"_TZ3210_rxqls8v0", deviceJoinName: "Tuya TS0202 Multi Sensor 4 In 1"],        // not tested
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00", outClusters:"0019,000A", model:"TS0202",  manufacturer:"_TZ3210_wuhzzfqg", deviceJoinName: "Tuya TS0202 Multi Sensor 4 In 1"]        // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars/92441/282?u=kkossev
            ],
            deviceJoinName: "Tuya Multi Sensor 4 In 1",
            configuration : ["battery": false]
    ],
    
    "TS0601_3IN1"  : [                                // https://szneo.com/en/products/show.php?id=239 // https://www.banggood.com/Tuya-Smart-Linkage-ZB-Motion-Sensor-Human-Infrared-Detector-Mobile-Phone-Remote-Monitoring-PIR-Sensor-p-1858413.html?cur_warehouse=CN 
            description   : "Tuya 3in1 (Motion/Temp/Humi) sensor",
            models        : ["TS0601"],
            device        : [type: "3IN1", powerSource: "dc", isSleepy:true],    // check powerSource and isSleepy!
            capabilities  : ["MotionSensor": true, "TemperatureMeasurement": true, "RelativeHumidityMeasurement": true, "tamper": true, "Battery": true],
            preferences   : ["motionReset":true],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_7hfcudw5", deviceJoinName: "Tuya NAS-PD07 Multi Sensor 3 In 1"]
            ],
            deviceJoinName: "Tuya Multi Sensor 3 In 1",
            configuration : ["battery": false]
    ],

    "TS0601_2IN1"  : [
            description   : "Tuya 2in1 (Motion and Illuminance) sensor",
            models         : ["TS0601"],
            device        : [type: "2IN1", isIAS:true, powerSource: "battery", isSleepy:true],
            capabilities  : ["MotionSensor": true, "TemperatureMeasurement": true, "IlluminanceMeasurement": true, "Battery": true],
            preferences   : ["motionReset":true, "invertMotion":true],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_3towulqd", deviceJoinName: "Tuya 2 in 1 Zigbee Mini PIR Motion Detector + Bright Lux ZG-204ZL"],          // https://www.aliexpress.com/item/1005004095233195.html
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_bh3n6gk8", deviceJoinName: "Tuya 2 in 1 Zigbee Mini PIR Motion Detector + Bright Lux ZG-204ZL"],          // https://community.hubitat.com/t/tze200-bh3n6gk8-motion-sensor-not-working/123213?u=kkossev
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_1ibpyhdc", deviceJoinName: "Tuya 2 in 1 Zigbee Mini PIR Motion Detector + Bright Lux ZG-204ZL"]          //
            ],
            deviceJoinName: "Tuya Multi Sensor 2 In 1",
            configuration : ["battery": false]
    ],
    
    "TS0202_MOTION_IAS"   : [
            description   : "Tuya TS0202 Motion sensor (IAS)",
            models        : ["TS0202","RH3040"],
            device        : [type: "PIR", isIAS:true, powerSource: "battery", isSleepy:true],
            capabilities  : ["MotionSensor": true, "Battery": true],
            preferences   : ["motionReset":true],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_mcxw5ehu", deviceJoinName: "Tuya TS0202 ZM-35H-Q Motion Sensor"],    // TODO: PIR sensor sensitivity and PIR keep time in seconds
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_msl6wxk9", deviceJoinName: "Tuya TS0202 ZM-35H-Q Motion Sensor"],    // TODO: fz.ZM35HQ_attr        
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500", outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TYZB01_dl7cejts", deviceJoinName: "Tuya TS0202 Motion Sensor"],             // KK model: 'ZM-RT201'// 5 seconds (!) reset period for testing
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_mmtwjmaq", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_otvn3lne", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_jytabjkb", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_ef5xlc9q", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_vwqnz1sn", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_2b8f6cio", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZE200_bq5c8xfe", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_qjqgmqxr", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_zwvaj5wy", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_bsvqrxru", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_tv3wxhcz", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_hqbdru35", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_tiwq83wk", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_ykwcwxmz", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_6ygjfyll", deviceJoinName: "Tuya TS0202 Motion Sensor"],            // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars/92441/289?u=kkossev
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3040_6ygjfyll", deviceJoinName: "Tuya TS0202 Motion Sensor"],            // https://community.hubitat.com/t/tuya-motion-sensor-driver/72000/54?u=kkossev
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_hgu1dlak", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_hktqahrq", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_jmrgyl7o", deviceJoinName: "Tuya TS0202 Motion Sensor"],            // not tested! //https://zigbee.blakadder.com/Luminea_ZX-5311.html
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"WHD02",  manufacturer:"_TZ3000_kmh5qpmb", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3040_usvkzkyn", deviceJoinName: "Tuya TS0202 Motion Sensor"],            // not tested // https://www.amazon.ae/Rechargeable-Detector-Security-Devices-Required/dp/B0BKKJ48QH
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-53o41joc", deviceJoinName: "TUYATEC RH3040 Motion Sensor"],                                            // 60 seconds reset period        
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-b5g40alm", deviceJoinName: "TUYATEC RH3040 Motion Sensor"], 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-deetibst", deviceJoinName: "TUYATEC RH3040 Motion Sensor"], 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-bd5faf9p", deviceJoinName: "Nedis/Samotech RH3040 Motion Sensor"], 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-zn9wyqtr", deviceJoinName: "Samotech RH3040 Motion Sensor"],                                           // vendor: 'Samotech', model: 'SM301Z'
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-b3ov3nor", deviceJoinName: "Zemismart RH3040 Motion Sensor"],                                          // vendor: 'Nedis', model: 'ZBSM10WT'
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-2gn2zf9e", deviceJoinName: "TUYATEC RH3040 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500,0B05", outClusters:"0019", model:"TY0202", manufacturer:"_TZ1800_fcdjzz3s", deviceJoinName: "Lidl TY0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500,0B05,FCC0", outClusters:"0019,FCC0", model:"TY0202", manufacturer:"_TZ3000_4ggd8ezp", deviceJoinName: "Bond motion sensor ZX-BS-J11W"],        // https://community.hubitat.com/t/what-driver-to-use-for-this-motion-sensor-zx-bs-j11w-or-ty0202/103953/4
                [profileId:"0104", endpointId:"01", inClusters:"0001,0003,0004,0500,0000", outClusters:"0004,0006,1000,0019,000A", model:"TS0202", manufacturer:"_TZ3040_bb6xaihh", deviceJoinName: "Tuya TS0202 Motion Sensor"],  // https://github.com/Koenkk/zigbee2mqtt/issues/17364
                [profileId:"0104", endpointId:"01", inClusters:"0001,0003,0004,0500,0000", outClusters:"0004,0006,1000,0019,000A", model:"TS0202", manufacturer:"_TZ3040_wqmtjsyk", deviceJoinName: "Tuya TS0202 Motion Sensor"],  // not tested
                [profileId:"0104", endpointId:"01", inClusters:"0001,0003,0004,0500,0000", outClusters:"0004,0006,1000,0019,000A", model:"TS0202", manufacturer:"_TZ3000_h4wnrtck", deviceJoinName: "Tuya TS0202 Motion Sensor"]   // not tested
                
            ],
            deviceJoinName: "Tuya TS0202 Motion Sensor",
            configuration : ["battery": false]
    ],
    
    "TS0202_MOTION_SWITCH": [
            description   : "Tuya Motion Sensor and Scene Switch",
            models        : ["TS0202"],
            device        : [type: "PIR", isIAS:true, powerSource: "battery", isSleepy:true],
            capabilities  : ["MotionSensor":true, "switch":true, "Battery":true],
            preferences   : ["motionReset":true],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,EF00,0000", outClusters:"0019,000A", model:"TS0202", manufacturer:"_TZ3210_cwamkvua", deviceJoinName: "Tuya Motion Sensor and Scene Switch"]
                
            ],
            deviceJoinName: "Tuya Motion Sensor and Scene Switch",
            configuration : ["battery": false]
    ],
    
    "TS0601_PIR_PRESENCE"   : [
            description   : "Tuya PIR Human Motion Presence Sensor (Black)",
            models        : ["TS0601"],
            device        : [type: "PIR", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "Battery": true],
            preferences   : ["motionReset":true, "inductionTime":"TODO", "targetDistance":"TODO"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_9qayzqa8", deviceJoinName: "Smart PIR Human Motion Presence Sensor (Black)"]    // https://www.aliexpress.com/item/1005004296422003.html
            ],
            deviceJoinName: "Tuya PIR Human Motion Presence Sensor LQ-CG01-RDR",
            configuration : ["battery": false]
    ],
    
    "TS0601_PIR_AIR"      : [    // Human presence sensor AIR (PIR sensor!) - o_sensitivity, v_sensitivity, led_status, vacancy_delay, light_on_luminance_prefer, light_off_luminance_prefer, mode, luminance_level, reference_luminance, vacant_confirm_time
            description   : "Tuya PIR Human Motion Presence Sensor AIR",
            models        : ["TS0601"],
            device        : [type: "PIR", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "Battery": true],                // TODO - check if battery powered?
            preferences   : ["vacancyDelay":"TODO", "ledStatusAIR":"TODO", "detectionMode":"TODO", "vSensitivity":"TODO", "oSensitivity":"TODO"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_auin8mzr", deviceJoinName: "Tuya PIR Human Motion Presence Sensor AIR"]        // Tuya LY-TAD-K616S-ZB
            ],
            deviceJoinName: "Tuya PIR Human Motion Presence Sensor AIR",
            configuration : ["battery": false]
    ],

    "NONTUYA_MOTION_IAS"   : [
            description   : "Other OEM Motion sensors (IAS)",
            models        : ["TS0202","RH3040"],
            device        : [type: "PIR", isIAS:true, powerSource: "battery", isSleepy:true],
            capabilities  : ["MotionSensor": true, "Battery": true],
            preferences   : ["motionReset":true],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,0001", outClusters:"0003", model:"ms01", manufacturer:"eWeLink", deviceJoinName: "eWeLink Motion Sensor"],        // for testL 60 seconds re-triggering period!
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,0001", outClusters:"0003", model:"msO1", manufacturer:"eWeLink", deviceJoinName: "eWeLink Motion Sensor"],        // second variant
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,0001", outClusters:"0003", model:"MS01", manufacturer:"eWeLink", deviceJoinName: "eWeLink Motion Sensor"],        // third variant
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,0400,0402,0500", outClusters:"0019", model:"MOT003", manufacturer:"HiveHome.com", deviceJoinName: "Hive Motion Sensor"]         // https://community.hubitat.com/t/hive-motion-sensors-can-we-get-custom-driver-sorted/108177?u=kkossev
            ],
            deviceJoinName: "Other OEM Motion sensor (IAS)",
            configuration : ["battery": false]
    ],
    
    "---"   : [
            description   : "--------------------------------------",
            models        : [],
            fingerprints  : [],
    ],           

    
// ------------------------------------------- mmWave Radars ------------------------------    
    "TS0601_TUYA_RADAR"   : [        // Smart Human presence sensors - illuminance, presence, target_distance; radar_sensitivity; minimum_range; maximum_range; detection_delay; fading_time; CLI; self_test (checking, check_success, check_failure, others, comm_fault, radar_fault)
            description   : "Tuya Human Presence mmWave Radar ZY-M100",        // spammy devices!
            models        : ["TS0601"],
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "IlluminanceMeasurement": true, "DistanceMeasurement":true],
            preferences   : ["radarSensitivity":"2", "detectionDelay":"101", "fadingTime":"102", "minimumDistance":"3", "maximumDistance":"4"],
            commands      : ["resetStats":"resetStats"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ztc6ggyl", deviceJoinName: "Tuya ZigBee Breath Presence Sensor ZY-M100"],       // KK
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_ztc6ggyl", deviceJoinName: "Tuya ZigBee Breath Presence Sensor ZY-M100"],       // KK
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ikvncluo", deviceJoinName: "Moes TuyaHuman Presence Detector Radar 2 in 1"],    // jw970065
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_lyetpprm", deviceJoinName: "Tuya ZigBee Breath Presence Sensor"],   
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_wukb7rhc", deviceJoinName: "Moes Smart Human Presence Detector"],               // https://www.moeshouse.com/collections/smart-sensor-security/products/smart-zigbee-human-presence-detector-pir-mmwave-radar-detection-sensor-ceiling-mount
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_jva8ink8", deviceJoinName: "AUBESS Human Presence Detector"],                   // https://www.aliexpress.com/item/1005004262109070.html 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_mrf6vtua", deviceJoinName: "Tuya Human Presence Detector"],                     // not tested
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ar0slwnd", deviceJoinName: "Tuya Human Presence Detector"],                     // not tested
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_sfiy5tfs", deviceJoinName: "Tuya Human Presence Detector"],                     // not tested
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_holel4dk", deviceJoinName: "Tuya Human Presence Detector"],                     // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars/92441/280?u=kkossev
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_xpq2rzhq", deviceJoinName: "Tuya Human Presence Detector"],                     // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/432?u=kkossev
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_qasjif9e", deviceJoinName: "Tuya Human Presence Detector"],                     // 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_xsm7l9xa", deviceJoinName: "Tuya Human Presence Detector"]                      // 

            ],
            tuyaDPs:        [
                [dp:1,   name:'motion',             type:"enum",    rw: "ro", min:0,   max:1 ,    defaultValue:0,     step:1,  scale:1, map:[ "0":"inactive", "1":"active"] ,   unit:"",     title:"<b>Presence state</b>", description:'<i>Presence state</i>'], 
                [dp:2,   name:'radarSensitivity',   type:"number",  rw: "rw", min:0,   max:9 ,    defaultValue:7,     step:1,  scale:1,    unit:"x",        title:"<b>Radar sensitivity</b>",          description:'<i>Sensitivity of the radar</i>'],
                [dp:3,   name:'minimumDistance',    type:"decimal", rw: "rw", min:0.0, max:10.0,  defaultValue:0.1,   step:1,  scale:100,  unit:"meters",   title:"<b>Minimim detection distance</b>", description:'<i>Minimim (near) detection distance</i>'],
                [dp:4,   name:'maximumDistance',    type:"decimal", rw: "rw", min:0.0, max:10.0,  defaultValue:6.0,   step:1,  scale:100,  unit:"meters",   title:"<b>Maximum detection distance</b>", description:'<i>Maximum (far) detection distance</i>'],
                [dp:6,   name:'radarStatus',        type:"enum",    rw: "ro", min:0,   max:5 ,    defaultValue:1,     step:1,  scale:1, map:[ "0":"checking", "1":"check_success", "2":"check_failure", "3":"others", "4":"comm_fault", "5":"radar_fault"] ,   unit:"TODO",     title:"<b>Radar self checking status</b>", description:'<i>Radar self checking status</i>'],            // radarSeradarSelfCheckingStatus[fncmd.toString()]
                [dp:101, name:'detectionDelay',     type:"decimal", rw: "rw", min:0.0, max:10.0,  defaultValue:0.2,   step:1,  scale:10,   unit:"seconds",  title:"<b>Detection delay</b>",            description:'<i>Presence detection delay timer</i>'],
                [dp:102, name:'fadingTime',         type:"decimal", rw: "rw", min:0.5, max:500.0, defaultValue:60.0,  step:1,  scale:10,   unit:"seconds",  title:"<b>Fading time</b>",                description:'<i>Presence inactivity delay timer</i>'],                                  // aka 'nobody time'
                [dp:103, name:'debugCLI',           type:"number",  rw: "ro", min:0,   max:99999, defaultValue:0,     step:1,  scale:1,    unit:"?",        title:"<b>debugCLI</b>",                   description:'<i>debug CLI</i>'],
                [dp:104, name:'illuminance_lux',    type:"number",  rw: "ro", min:0,   max:2000,  defaultValue:0,     step:1,  scale:1,    unit:"lx",       title:"<b>illuminance</b>",                description:'<i>illuminance</i>'],

            ],
            spammyDPsToIgnore : [9],
            spammyDPsToNotTrace : [9, 103],
            deviceJoinName: "Tuya Human Presence Detector ZY-M100",
            configuration : [:]
    ],

    "TS0601_KAPVNNLK_RADAR"   : [        // 24GHz spammy radar w/ battery backup - no illuminance!
            description   : "Tuya TS0601_KAPVNNLK 24GHz Radar",        // https://www.amazon.com/dp/B0CDRBX1CQ?psc=1&ref=ppx_yo2ov_dt_b_product_details  // https://www.aliexpress.com/item/1005005834366702.html  // https://github.com/Koenkk/zigbee2mqtt/issues/18632 
            models        : ["TS0601"],                                // https://www.aliexpress.com/item/1005005858609756.html     // https://www.aliexpress.com/item/1005005946786561.html    // https://www.aliexpress.com/item/1005005946931559.html 
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "DistanceMeasurement":true, "HumanMotionState":true],
            preferences   : ["radarSensitivity":"15", "fadingTime":"12", "maximumDistance":"13"],
            commands      : ["radarSensitivity":"radarSensitivity", "maximumDistance":"maximumDistance", "smallMotionDetectionSensitivity":"smallMotionDetectionSensitivity"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_kapvnnlk", deviceJoinName: "Tuya 24 GHz Human Presence Detector NEW"]           // https://community.hubitat.com/t/tuya-smart-human-presence-sensor-micromotion-detect-human-motion-detector-zigbee-ts0601-tze204-sxm7l9xa/111612/71?u=kkossev 
            ],
            tuyaDPs:        [
                [dp:1,   name:"motion",                          type:"bool",    rw: "ro", min:0,   max:1, map:[0:"inactive", 1:"active"],  desc:'Presence state'],
                [dp:11,  name:"humanMotionState",                type:"enum",    rw: "ro", min:0,   max:2, map:[0:"none", 1:"small_move", 2:"large_move"],  desc:'Human motion state'],        // "none", "small_move", "large_move"]
                [dp:12,  name:'fadingTime',                      type:"number",  rw: "rw", min:3,   max:600, defaultValue:60, step:1,  scale:1,   unit:"seconds",  title: "<b>Fading time</b>", description:'<i>Presence inactivity timer, seconds</i>'],
                [dp:13,  name:'maximumDistance',                 type:"decimal", rw: "rw", min:150, max:600,   step:75, scale:100, unit:"meters",    desc:'Large motion detection distance'],
                [dp:15 , name:'radarSensitivity',                type:"number",  rw: "rw", min:0,   max:7,     step:1,  scale:1,   unit:"x",         desc:'Large motion detection sensitivity'],
                [dp:16 , name:'smallMotionDetectionSensitivity', type:"number",  rw: "rw", min:0,   max:7,     step:1,  scale:1,   unit:"x",         desc:'Small motion detection sensitivity'],
                [dp:19,  name:"distance",                        type:"decimal", rw: "ro", min:0,   max:10000, step:1,  scale:100, unit:"meters",    desc:'Distance'],
                [dp:101, name:'batteryLevel',                    type:"number",  rw: "rO", min:0,   max:100,   step:1,  scale:1,   unit:"%",         desc:'Battery level']
            ],
            spammyDPsToIgnore : [19],
            spammyDPsToNotTrace : [19],
            deviceJoinName: "Tuya 24 GHz Human Presence Detector NEW",
            configuration : [:]
    ],
    
    // TODO - very similar to the TY-FALL model, join together as TS0601_RADAR_MIR-HE200-TY
    "TS0601_RADAR_MIR-HE200-TY"   : [        // Human presence sensor radar 'MIR-HE200-TY' - illuminance, presence, occupancy, motion_speed, motion_direction, radar_sensitivity, radar_scene ('default', 'area', 'toilet', 'bedroom', 'parlour', 'office', 'hotel')
            description   : "Tuya Human Presence Sensor MIR-HE200-TY",
            models        : ["TS0601"],
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "IlluminanceMeasurement": true],
            preferences   : ["radarSensitivity":"TODO"],        // TODO: range is 0..10
            commands      : ["radarSensitivity":"radarSensitivity"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_vrfecyku", deviceJoinName: "Tuya Human presence sensor MIR-HE200-TY"]
            ],
            deviceJoinName: "Tuya Human Presence Sensor MIR-HE200-TY",
            configuration : [:]
    ],     
        
    "TS0601_RADAR_MIR-TY-FALL"   : [         // Human presence sensor radar 'MIR-HE200-TY_fall' - illuminance, presence, occupancy, motion_speed, motion_direction, radar_sensitivity, radar_scene, tumble_switch, fall_sensitivity, tumble_alarm_time, fall_down_status, static_dwell_alarm
            description   : "Tuya Human Presence Sensor MIR-TY-FALL",
            models        : ["TS0601"],
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "IlluminanceMeasurement": true],
            preferences   : ["radarSensitivity":"TODO"],        // TODO: range is 0..10
            commands      : ["radarSensitivity":"radarSensitivity"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_lu01t0zl", deviceJoinName: "Tuya Human presence sensor with fall function"],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ypprdwsl", deviceJoinName: "Tuya Human presence sensor with fall function"]
            ],
            deviceJoinName: "Tuya Human Presence Sensor MIR-TY-FALL",
            configuration : [:]
    ],     
    
    
    "TS0601_BLACK_SQUARE_RADAR"   : [        // // 24GHz Big Black Square Radar w/ annoying LED    // isBlackSquareRadar()
            description   : "Tuya Black Square Radar",
            models        : ["TS0601"],
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor":true],
            preferences   : ["testX":22, "indicatorLight":103, "test": "none"],
            commands      : [],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_0u3bj3rc", deviceJoinName: "24GHz Black Square Human Presence Radar w/ LED"],
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_v6ossqfy", deviceJoinName: "24GHz Black Square Human Presence Radar w/ LED"],
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_mx6u6l4y", deviceJoinName: "24GHz Black Square Human Presence Radar w/ LED"]
            ],
            tuyaDPs:        [
                [dp:1,   name:"motion",         type:"enum",  rw: "ro", min:0, max:1, map:[0:"inactive", 1:"active"],    desc:'Presence'],
                [dp:101, name:'existance_time', type:"value", rw: "ro", min:0, max:9999,   scale:1,   unit:"minutes",    desc:'Shows the presence duration in minutes'],
                [dp:102, name:'leave_time',     type:"value", rw: "ro", min:0, max:9999,   scale:1,   unit:"minutes",    desc:'Shows the duration of the absence in minutes'],
                [dp:103, name:'indicatorLight', type:"enum",  rw: "rw", min:0, max:1, default: 0, map:[0:"OFF", 1:"ON"], desc:'Turns the onboard LED on or off', isPreference:true]
            ],
            spammyDPsToIgnore : [103],                    // we don't need to know the LED status every 4 seconds!
            spammyDPsToNotTrace : [1, 101, 102, 103],     // very spammy device - 4 packates are sent every 4 seconds!
            deviceJoinName: "24GHz Black Square Human Presence Radar w/ LED",
    ],
    
   
    "TS0601_YXZBRB58_RADAR"   : [        // Seller: shenzhenshixiangchuangyeshiyey Manufacturer: Shenzhen Eysltime Intelligent LTD    Item model number: YXZBRB58 
            description   : "Tuya YXZBRB58 Radar",
            models        : ["TS0601"],
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "IlluminanceMeasurement": true, "DistanceMeasurement":true],    // https://github.com/Koenkk/zigbee2mqtt/issues/18318
            preferences   : ["radarSensitivity":"TODO", "detectionDelay":"TODO", "fadingTime":"102", "minimumDistance":"TODO", "maximumDistance":"TODO"],
            commands      : ["resetStats":"resetStats"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_sooucan5", deviceJoinName: "Tuya Human Presence Detector YXZBRB58"]                      // https://www.amazon.com/dp/B0BYDCY4YN                   
            ],
            tuyaDPs:        [        // TODO - use already defined DPs and preferences !!
                [dp:1,   name:"motion",                 type:"bool",  rw: "ro", min:0, max:2, map:[0:"inactive", 1:"active"],  desc:'Presence state'],
                [dp:2,   name:'radarSensitivity',       type:"value", rw: "rw", min:0, max:9 ,    scale:1,    unit:"x",        desc:'Sensitivity of the radar'],
                [dp:3,   name:'minimumDistance',        type:"value", rw: "rw", min:0, max:1000,  scale:100,  unit:"meters",   desc:'Min detection distance'],
                [dp:4,   name:'maximumDistance',        type:"value", rw: "rw", min:0, max:1000,  scale:100,  unit:"meters",   desc:'Max detection distance'],
                [dp:101, name:'illuminance_lux',        type:"value", rw: "ro",                   scale:1,    unit:"lx",       desc:'Illuminance'],
                [dp:102, name:'fadingTime',             type:"number", rw: "rw", min:5,   max:1500, defaultValue:60, step:1,  scale:1,   unit:"seconds",  title: "<b>Fading time</b>", description:'<i>Presence inactivity timer, seconds</i>'],
                [dp:103, name:'detectionDelay',         type:"value", rw: "rw", min:0, max:10,    scale:10,   unit:"x",        desc:'Detection delay'],
                [dp:104, name:'radar_scene',            type:"enum",  rw: "rw", min:0, max:4,  map:[0:"default", 1:"bathroom", 2:"bedroom", 3:"sleeping"],  desc:'Presets for sensitivity for presence and movement'],    // https://github.com/kirovilya/zigbee-herdsman-converters/blob/b9bb6695fdf5d26ab4195cca9fcb1f2bd73afa71/src/devices/tuya.ts
                [dp:105, name:"distance",               type:"value", rw: "ro", min:0, max:10000, scale:100,  unit:"meters",   desc:'Distance']
            ],                    // https://github.com/zigpy/zha-device-handlers/issues/2429 
            spammyDPsToIgnore : [105],
            spammyDPsToNotTrace : [105],
            deviceJoinName: "Tuya Human Presence Detector YXZBRB58",    // https://www.aliexpress.com/item/1005005764168560.html 
            configuration : [:]
    ],    

    // TODO !!! check whether the fading time (110) and the detection delay (111) are not swapped ????? // TODO !!!        https://github.com/dresden-elektronik/deconz-rest-plugin/issues/6998#issuecomment-1612113340 
    "TS0601_SXM7L9XA_RADAR"   : [                                       // https://gist.github.com/Koenkk/9295fc8afcc65f36027f9ab4d319ce64 
            description   : "Tuya Human Presence Detector SXM7L9XA",    // https://github.com/zigpy/zha-device-handlers/issues/2378#issuecomment-1558777494
            models        : ["TS0601"],
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "IlluminanceMeasurement": true, "DistanceMeasurement":true],
            preferences   : ["radarSensitivity":"TODO", "detectionDelay":"TODO", "fadingTime":"110", "minimumDistance":"TODO", "maximumDistance":"TODO"],
            commands      : ["resetStats":"resetStats"],            
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_sxm7l9xa", deviceJoinName: "Tuya Human Presence Detector SXM7L9XA"]       // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
            ],
            tuyaDPs:        [        // TODO - use already defined DPs and preferences !!
                [dp:104, name:'illuminance_lux',        type:"value", rw: "ro",                   scale:1, unit:"lx",          desc:'illuminance'],
                [dp:105, name:"motion",                 type:"bool",  rw: "ro", min:0, max:2, map:[0:"inactive", 1:"active"],  desc:'Presence state'],
                [dp:106, name:'radarSensitivity',       type:"value", rw: "rw", min:0, max:9 ,    scale:1,    unit:"x",        desc:'Motion sensitivity'],
                [dp:107, name:'maximumDistance',        type:"value", rw: "rw", min:0, max:9500,  scale:100,  unit:"meters",   desc:'Max detection distance'],
                [dp:108, name:'minimumDistance',        type:"value", rw: "rw", min:0, max:9500,  scale:100,  unit:"meters",   desc:'Min detection distance'],       // TODO - check DP!
                [dp:109, name:"distance",               type:"value", rw: "ro", min:0, max:10000, scale:100,  unit:"meters",   desc:'Distance'],                      // TODO - check DP!
                // TODO - check scaling!
                [dp:110, name:'fadingTime',             type:"decimal", rw: "rw", min:0.5,   max:150.0, defaultValue:60, step:1,  scale:10,   unit:"seconds",  title: "<b>Fading time</b>", description:'<i>Presence inactivity timer, seconds</i>'],
                [dp:111, name:'detectionDelay',         type:"value", rw: "rw", min:0, max:10,    scale:10,   unit:"x",        desc:'Presence sensitivity']
            ],
            deviceJoinName: "Tuya Human Presence Detector SXM7L9XA",
            configuration : [:],
    ],    
    
    
    // '24G MmWave radar human presence motion sensor'
    "TS0601_IJXVKHD0_RADAR"   : [                                       // isIJXVKHD0radar() - TODO!
            description   : "Tuya Human Presence Detector IJXVKHD0",    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/5acadaf16b0e85c1a8401223ddcae3d31ce970eb/src/devices/tuya.ts#L5747
            models        : ["TS0601"],
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "IlluminanceMeasurement": true, "DistanceMeasurement":true],
            preferences   : [:],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_ijxvkhd0", deviceJoinName: "Tuya Human Presence Detector IJXVKHD0"]       // 
            ],
            tuyaDPs:        [        // TODO - use already defined DPs and preferences !!
                [dp:1, name:"unknown",                 type:"bool",  rw: "ro", min:0, max:2, map:[0:"inactive", 1:"active"],  desc:'unknown state'],
                [dp:2, name:"unknown",                 type:"bool",  rw: "ro", min:0, max:2, map:[0:"inactive", 1:"active"],  desc:'unknown state'],
                [dp:104, name:'illuminance_lux',        type:"value", rw: "ro",                   scale:1, unit:"lx",                  desc:'illuminance'],
                [dp:105, name:"presence_state",         type:"enum",  rw: "ro", min:0, max:2, map:[0:"none", 1:"present", 2:"moving"], desc:'Presence state'],
                [dp:106, name:'motion_sensitivity',     type:"value", rw: "rw", min:1, max:10,   scale:1,   unit:"x",                  desc:'Motion sensitivity'],
                [dp:107, name:'detection_distance_max', type:"value", rw: "rw", min:0, max:1000, scale:100, unit:"meters",             desc:'Max detection distance'],
                [dp:109, name:'distance',               type:"value", rw: "ro", min:0, max:1000, scale:100, unit:"meters",             desc:'Target distance'],
                [dp:110, name:'fading_time',            type:"value", rw: "rw", min:1, max:15,   scale:1,   unit:"seconds",            desc:'Delay time'],
                [dp:111, name:'presence_sensitivity',   type:"value", rw: "rw", min:1, max:10,   scale:1,   unit:"x",                  desc:'Presence sensitivity'],
                [dp:123, name:"presence",               type:"enum",  rw: "ro", min:0, max:1, map:[0:"none", 1:"presence"],            desc:'Presence']
            ],
            spammyDPsToIgnore : [109],
            spammyDPsToNotTrace : [109],
            deviceJoinName: "Tuya Human Presence Detector IJXVKHD0",
            configuration : [:]
    ],

    "TS0601_YENSYA2C_RADAR"   : [                                       // Loginovo Zigbee Mmwave Human Presence Sensor (rectangular)    // TODO: update thread first post
            description   : "Tuya Human Presence Detector YENSYA2C",    // https://github.com/Koenkk/zigbee2mqtt/issues/18646
            models        : ["TS0601"],                                 // https://www.aliexpress.com/item/1005005677110270.html
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "IlluminanceMeasurement": true, "DistanceMeasurement":true],
            preferences   : [:],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_yensya2c", deviceJoinName: "Tuya Human Presence Detector YENSYA2C"],       // 
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_mhxn2jso", deviceJoinName: "Tuya Human Presence Detector New"]       //  https://github.com/Koenkk/zigbee2mqtt/issues/18623
                //        ^^^similar DPs, but not a full match? ^^^ TODO
            ],
            tuyaDPs:        [
                [dp:1,   name:"presence_state",     type:"enum",  rw: "ro", min:0,  max:1, map:[0:"none", 1:"presence"],  desc:'Presence state'],
                [dp:12,  name:"presence_time",      type:"value", rw: "rw", min:1,  max:3600,  scale:1,   unit:"seconds", desc:'Presence time'],
                [dp:19,  name:"distance",           type:"value", rw: "ro", min:0,  max:10000, scale:100, unit:"meters",  desc:'Distance'],
                [dp:20,  name:"illuminance",        type:"value", rw: "ro", min:0,  max:10000, scale:1,   unit:"lx",      desc:'illuminance'],
                [dp:101, name:"sensitivity",        type:"value", rw: "rw", min:0,  max:10,    scale:1,   unit:"x",       desc:'sensitivity'],
                [dp:102, name:"presence_delay",     type:"value", rw: "rw", min:5,  max:3600,  scale:1,   unit:"seconds", desc:'presence delay'],
                [dp:111, name:"breath_min",         type:"value", rw: "rw", min:0,  max:10000, scale:100, unit:"meters",  desc:'breath detection minimum distatnce'],
                [dp:112, name:"breath_max",         type:"value", rw: "rw", min:50, max:10000, scale:100, unit:"meters",  desc:'breath detection maximum distatnce'],
                [dp:113, name:"breathe_flag",       type:"bool",  rw: "rw"],        // rw
                [dp:114, name:"small_flag",         type:"bool",  rw: "rw"],        // rw
                [dp:115, name:"large_flag",         type:"bool",  rw: "rw"],        // rw
                [dp:116, name:"presence_delay_time",type:"value", rw: "rw", min:0,  max:3600,  scale:1,   unit:"seconds", desc:'Presence delay time']
            ],
            deviceJoinName: "Tuya Human Presence Detector YENSYA2C",
            configuration : [:]
    ],
    
    
    // the new 5.8 GHz radar w/ humanMotionState and a lot of configuration options, 'not-so-spammy' !   - pedestal mount form-factor
    "TS0225_HL0SS9OA_RADAR"   : [
            description   : "Tuya TS0225_HL0SS9OA Radar",        // https://www.aliexpress.com/item/1005005761971083.html 
            models        : ["TS0225"],
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "IlluminanceMeasurement": true, "HumanMotionState":true], 
            preferences   : ["presenceKeepTime":"12", "ledIndicator":"24", "radarAlarmMode":"105", "radarAlarmVolume":"102", "radarAlarmTime":"101", \
                             "textLargeMotion":"NONE", "motionFalseDetection":"112", "motionDetectionSensitivity":"15", "motionMinimumDistance":"106", "motionDetectionDistance":"13", \
                             "textSmallMotion":"NONE", "smallMotionDetectionSensitivity":"16", "smallMotionMinimumDistance":"107", "smallMotionDetectionDistance":"14", \
                             "textStaticDetection":"NONE", "breatheFalseDetection":"115", "staticDetectionSensitivity":"104", "staticDetectionMinimumDistance":"108", "staticDetectionDistance":"103" \
                            ], 
            commands      : ['resetSettings':'resetSettings', 'moveSelfTest':'moveSelfTest', 'smallMoveSelfTest':'smallMoveSelfTest', 'breatheSelfTest':'breatheSelfTest', 'motionFalseDetection':'motionFalseDetection', \
                             'motionDetectionDistance':'motionDetectionDistance', 'motionMinimumDistance':'motionMinimumDistance', 'motionDetectionSensitivity':'motionDetectionSensitivity', "smallMotionDetectionSensitivity":"smallMotionDetectionSensitivity", \
                             "breatheFalseDetection":"breatheFalseDetection", "staticDetectionMinimumDistance":"staticDetectionMinimumDistance", "staticDetectionSensitivity":"staticDetectionSensitivity", "staticDetectionDistance":"staticDetectionDistance", \
                             "smallMotionDetectionDistance":"smallMotionDetectionDistance", "smallMotionMinimumDistance":"smallMotionMinimumDistance"
            ],          
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,E002,EF00", outClusters:"0019,000A", model:"TS0225", manufacturer:"_TZE200_hl0ss9oa", deviceJoinName: "Tuya TS0225_HL0SS9OA Human Presence Detector"]       // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
            ],
            tuyaDPs:        [        // TODO - use already defined DPs and preferences !!
                [dp:1,   name:"motion",                          type:"bool",  rw: "ro", min:0, max:1, map:[0:"inactive", 1:"active"],  desc:'Presence state'],
                [dp:11,  name:"humanMotionState",                type:"enum",  rw: "ro", min:0, max:2, map:[0:"none", 1:"large", 2:"small", 3:"static"],  desc:'Human motion state'],
                [dp:12,  name:'presenceKeepTime',                type:"value", rw: "rw", min:5, max:3600,  scale:1,   unit:"seconds",   desc:'Presence keep time'],
                [dp:13,  name:'motionDetectionDistance',         type:"decimal", dt: "UINT16", rw: "rw", min:0.00, max:10.00, defaultValue:6.0,  step:1, scale:100, unit:"meters", title: "<b>Motion Detection Distance</b>",     description:'<i>Large motion detection distance, meters</i>'],
                [dp:14,  name:'smallMotionDetectionDistance',    type:"value", rw: "rw", min:0, max:600,   scale:100, unit:"meters",    desc:'Small motion detection distance'],
                [dp:15 ,  name:'motionDetectionSensitivity',      type:"number",  dt: "UINT8",  rw: "rw", min:0,  max:10, defaultValue:7,    step:1,  scale:1,   unit:"x",         title: "<b>Motion Detection Sensitivity</b>",  description:'<i>Large motion detection sensitivity</i>'],           // aka Motionless Detection Sensitivity
                [dp:16 , name:'smallMotionDetectionSensitivity', type:"value", rw: "rw", min:0, max:10 ,   scale:1,   unit:"x",         desc:'Small motion detection sensitivity'],
                [dp:20,  name:'illuminance_lux',                 type:"value", rw: "ro",                   scale:10,  unit:"lx",        desc:'Illuminance'],
                [dp:24,  name:"ledIndicator",                    type:"bool",  rw: "ro", min:0, max:1, map:[0:"OFF", 1:"ON"],           desc:'LED indicator mode'],
                [dp:101, name:'radarAlarmTime',                  type:"value", rw: "rw", min:0, max:60 ,   scale:1,   unit:"seconds",   desc:'Alarm time'],
                [dp:102, name:"radarAlarmVolume",                type:"enum",  rw: "ro", min:0, max:3, map:[0:"low", 1:"medium", 2:"high", 3:"mute"],  desc:'Alarm volume'],
                [dp:103, name:'staticDetectionDistance',         type:"value", rw: "rw", min:0, max:6000,  scale:100, unit:"meters",    desc:'Static detection distance'],
                [dp:104, name:'staticDetectionSensitivity',      type:"number",  dt: "UINT8",  rw: "rw", min:0,  max:10, defaultValue:7,  step:1,  scale:1,   unit:"x",         title: "<b>Static Detection Sensitivity</b>",  description:'<i>Static detection sensitivity</i>'],                 // aka Motionless Detection Sensitivity 
                [dp:105, name:"radarAlarmMode",                  type:"enum",  rw: "ro", min:0, max:3, map:[0:"arm", 1:"off", 2:"alarm", 3:"doorbell"],  desc:'Alarm mode'],    // TODO !!!
                [dp:106, name:'motionMinimumDistance',           type:"value", rw: "rw", min:0, max:6000,  scale:100, unit:"meters",    desc:'Motion minimumD distance'],
                [dp:107, name:'smallMotionMinimumDistance',      type:"value", rw: "rw", min:0, max:6000,  scale:100, unit:"meters",    desc:'Motion minimumD distance'],
                [dp:108, name:'staticDetectionMinimumDistance',  type:"value", rw: "rw", min:0, max:6000,  scale:100, unit:"meters",    desc:'Static detection minimum distance'],
                [dp:109, name:'checkingTime',                    type:"value", rw: "ro",                   scale:10,  unit:"seconds",   desc:'Checking time'],
                [dp:110, name:"radarStatus",                     type:"bool",  rw: "ro", min:0, max:1, map:[0:"disabled", 1:"enabled"], desc:'Radar small move self-test'],
                [dp:111, name:"radarStatus",                     type:"bool",  rw: "ro", min:0, max:1, map:[0:"disabled", 1:"enabled"], desc:'Radar breathe self-test'],
                [dp:112, name:"motionFalseDetection",            type:"bool",  rw: "ro", min:0, max:1, map:[0:"disabled", 1:"enabled"], desc:'Motion false detection'],
                [dp:113, name:"radarReset",                      type:"bool",  rw: "ro", min:0, max:1, map:[0:"disabled", 1:"enabled"], desc:'Radar reset'],
                [dp:114, name:"radarStatus",                     type:"bool",  rw: "ro", min:0, max:1, map:[0:"disabled", 1:"enabled"], desc:'Radar move self-test'],
                [dp:115, name:"breatheFalseDetection",           type:"bool",  rw: "ro", min:0, max:1, map:[0:"disabled", 1:"enabled"], desc:'Motion false detection'],
                [dp:116, name:'existance_time',                  type:"value", rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   desc:'Radar presence duration'],    // not received
                [dp:117, name:'leave_time',                      type:"value", rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   desc:'Radar absence duration'],     // not received
                [dp:118, name:'radarDurationStatus',             type:"value", rw: "ro", min:0, max:60 ,   scale:1,   unit:"seconds",   desc:'Radar duration status']       // not received
            ],
            deviceJoinName: "Tuya TS0225_HL0SS9OA Human Presence Detector",
            configuration : [:]
    ],    

   
    
    // the new 5.8GHz radar w/ humanMotionState and a lot of configuration options, 'not-so-spammy' !   - wall mount form-factor    is2AAELWXKradar() 
    "TS0225_2AAELWXK_RADAR"   : [                                     // https://github.com/Koenkk/zigbee2mqtt/issues/18612
            description   : "Tuya TS0225_2AAELWXK 5.8 GHz Radar",        // https://community.hubitat.com/t/the-new-tuya-24ghz-human-presence-sensor-ts0225-tze200-hl0ss9oa-finally-a-good-one/122283/72?u=kkossev 
            models        : ["TS0225"],                                // ZG-205Z
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "IlluminanceMeasurement": true, "HumanMotionState":true],
            // TODO - preferences and DPs !!!!!!!!!!!!!!!!!!!!
            preferences   : ["presenceKeepTime":"102", "ledIndicator":"107", "radarAlarmMode":"117", "radarAlarmVolume":"116", "radarAlarmTime":"115", \
                             "textLargeMotion":"NONE", "motionFalseDetection":"103", "motionDetectionSensitivity":"2", "motionMinimumDistance":"3", "motionDetectionDistance":"4", \
                             "textSmallMotion":"NONE", "smallMotionDetectionSensitivity":"105", "smallMotionMinimumDistance":"110", "smallMotionDetectionDistance":"104", \
                             "textStaticDetection":"NONE", "breatheFalseDetection":"113", "staticDetectionSensitivity":"109", /*"staticDetectionMinimumDistance":"108",*/ "staticDetectionDistance":"108" \
                            ], 
            commands      : ['resetSettings':'resetSettings', 'moveSelfTest':'moveSelfTest', 'smallMoveSelfTest':'smallMoveSelfTest', 'breatheSelfTest':'breatheSelfTest', 'motionFalseDetection':'motionFalseDetection', \
                             'motionDetectionDistance':'motionDetectionDistance', 'motionMinimumDistance':'motionMinimumDistance', 'motionDetectionSensitivity':'motionDetectionSensitivity', "smallMotionDetectionSensitivity":"smallMotionDetectionSensitivity", \
                             "breatheFalseDetection":"breatheFalseDetection", "staticDetectionMinimumDistance":"staticDetectionMinimumDistance", "staticDetectionSensitivity":"staticDetectionSensitivity", "staticDetectionDistance":"staticDetectionDistance", \
                             "smallMotionDetectionDistance":"smallMotionDetectionDistance", "smallMotionMinimumDistance":"smallMotionMinimumDistance"
            ],
            fingerprints  : [                                          // reports illuminance and motion using clusters 0x400 and 0x500 !
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,E002,EF00,EE00,E000,0400", outClusters:"0019,000A", model:"TS0225", manufacturer:"_TZE200_2aaelwxk", deviceJoinName: "Tuya TS0225_2AAELWXK 24Ghz Human Presence Detector"]       // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
            ],
            tuyaDPs:        [        // TODO - use already defined DPs and preferences !!
                [dp:1,   name:"motion",                          type:"bool",  rw: "ro", min:0, max:1, map:[0:"inactive", 1:"active"],  desc:'Presence state'],
                [dp:101, name:"humanMotionState",                type:"enum",  rw: "ro", min:0, max:2, map:[0:"none", 1:"large", 2:"small", 3:"static"],  desc:'Human motion state'],
                [dp:102, name:'presenceKeepTime',                type:"value", rw: "rw", min:5, max:3600,  scale:1,   unit:"seconds",   desc:'Presence keep time'],
                [dp:4,   name:'motionDetectionDistance',         type:"decimal", dt: "UINT16", rw: "rw", min:0.00, max:10.00, defaultValue:6.0,  step:1, scale:100, unit:"meters", title: "<b>Motion Detection Distance</b>",     description:'<i>Large motion detection distance, meters</i>'],
                [dp:104, name:'smallMotionDetectionDistance',    type:"value", rw: "rw", min:0, max:600,   scale:100, unit:"meters",    desc:'Small motion detection distance'],
                [dp:2 ,  name:'motionDetectionSensitivity',      type:"number",  dt: "UINT8",  rw: "rw", min:0,  max:10, defaultValue:7,   step:1,  scale:1,   unit:"x",         title: "<b>Motion Detection Sensitivity</b>",  description:'<i>Large motion detection sensitivity</i>'],           // aka Motionless Detection Sensitivity
                [dp:105, name:'smallMotionDetectionSensitivity', type:"value", rw: "rw", min:0, max:10 ,   scale:1,   unit:"x",         desc:'Small motion detection sensitivity'],
                [dp:106, name:'illuminance_lux',                 type:"value", rw: "ro",                   scale:10,  unit:"lx",        desc:'Illuminance'],
                [dp:107, name:"ledIndicator",                    type:"bool",  rw: "ro", min:0, max:1, map:[0:"OFF", 1:"ON"],           desc:'LED indicator mode'],
                [dp:115, name:'radarAlarmTime',                  type:"value", rw: "rw", min:0, max:60 ,   scale:1,   unit:"seconds",   desc:'Alarm time'],
                [dp:116, name:"radarAlarmVolume",                type:"enum",  rw: "ro", min:0, max:3, map:[0:"low", 1:"medium", 2:"high", 3:"mute"],  desc:'Alarm volume'],
                [dp:108, name:'staticDetectionDistance',         type:"value", rw: "rw", min:0, max:6000,  scale:100, unit:"meters",    desc:'Static detection distance'],
                [dp:109, name:'staticDetectionSensitivity',      type:"number",  dt: "UINT8",  rw: "rw", min:0,  max:10, defaultValue:7,  step:1,  scale:1,   unit:"x",         title: "<b>Static Detection Sensitivity</b>",  description:'<i>Static detection sensitivity</i>'],                 // aka Motionless Detection Sensitivity 
                [dp:117, name:"radarAlarmMode",                  type:"enum",  rw: "ro", min:0, max:3, map:[0:"arm", 1:"off", 2:"alarm", 3:"doorbell"],  desc:'Alarm mode'],    // TODO !!!
                [dp:3,   name:'motionMinimumDistance',           type:"value", rw: "rw", min:0, max:6000,  scale:100, unit:"meters",    desc:'Motion minimum distance'],
                [dp:110, name:'smallMotionMinimumDistance',      type:"value", rw: "rw", min:0, max:6000,  scale:100, unit:"meters",    desc:'Small motion minimum distance'],
     //           [dp:111, name:'staticDetectionMinimumDistance',  type:"value", rw: "rw", min:0, max:6000,  scale:100, unit:"meters",    desc:'Static detection minimum distance'], // TODO - check !!
                [dp:114, name:'checkingTime',                    type:"value", rw: "ro",                   scale:10,  unit:"seconds",   desc:'Checking time'],
                [dp:118, name:"radarStatus",                     type:"bool",  rw: "ro", min:0, max:1, map:[0:"disabled", 1:"enabled"], desc:'Radar small move self-test'],    // TODO - check !!
                [dp:119, name:"radarStatus",                     type:"bool",  rw: "ro", min:0, max:1, map:[0:"disabled", 1:"enabled"], desc:'Radar breathe self-test'],        // TODO - check !
                [dp:103, name:"motionFalseDetection",            type:"bool",  rw: "ro", min:0, max:1, map:[0:"disabled", 1:"enabled"], desc:'Motion false detection'],
                [dp:112, name:"radarReset",                      type:"bool",  rw: "ro", min:0, max:1, map:[0:"disabled", 1:"enabled"], desc:'Radar reset'],
                [dp:120, name:"radarStatus",                     type:"bool",  rw: "ro", min:0, max:1, map:[0:"disabled", 1:"enabled"], desc:'Radar move self-test'],        // TODO - check !
                [dp:113, name:"breatheFalseDetection",           type:"bool",  rw: "ro", min:0, max:1, map:[0:"disabled", 1:"enabled"], desc:'Motion false detection']
            ],
            deviceJoinName: "Tuya TS0225_2AAELWXK 5.8 Ghz Human Presence Detector",
            configuration : [:]
    ],    
    
    // isSBYX0LM6radar()                                               // https://github.com/Koenkk/zigbee-herdsman-converters/issues/5930#issuecomment-1662456347 
    "TS0601_SBYX0LM6_RADAR"   : [                                      // _TZE204_sbyx0lm6    TS0601   model: 'MTG075-ZB-RL', '5.8G Human presence sensor with relay',
            description   : "Tuya Human Presence Detector SBYX0LM6",   // https://github.com/vit-um/hass/blob/main/zigbee2mqtt/tuya_h_pr.js    
            models        : ["TS0601"],                                // https://github.com/Koenkk/zigbee-herdsman-converters/issues/5930
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "IlluminanceMeasurement": true, "DistanceMeasurement":true],
            preferences   : [:],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_sbyx0lm6", deviceJoinName: "Tuya Human Presence Detector w/ relay"],      // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_dtzziy1e", deviceJoinName: "Tuya Human Presence Detector w/ relay"],      // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_clrdrnya", deviceJoinName: "Tuya Human Presence Detector w/ relay"]       // https://www.aliexpress.com/item/1005005865536713.html                  // https://github.com/Koenkk/zigbee2mqtt/issues/18677?notification_referrer_id=NT_kwDOAF5zfrI3NDQ1Mzc2NTAxOjYxODk5NTA
            ],
            states: ["presence", "illuminance"],
            tuyaDPs:        [
                [dp:1,   name:"presence",           type:"bool",  rw: "ro"],
                [dp:2,   name:'radar_sensitivity',  type:"value", rw: "rw", min:0,  max:9,    scale:1,   unit:"x",            desc:'sensitivity of the radar'],
                [dp:3,   name:'shield_range',       type:"value", rw: "rw", min:0,  max:800,  scale:100, unit:"meters",       desc:'Shield range of the radar'],
                [dp:4,   name:'detection_range',    type:"value", rw: "rw", min:0,  max:800,  scale:100, unit:"meters",       desc:'Detection range'],
                [dp:6,   name:'equipment_status',   type:"value", rw: "ro"], 
                [dp:9,   name:'target_distance',    type:"value", rw: "ro",                   scale:100, unit:"meters",       desc:'Distance to target'],
                [dp:101, name:'entry_filter_time',  type:"value", rw: "rw", min:0,  max:100,  scale:10,  unit:"seconds",      desc:'Entry filter time'],
                [dp:102, name:'departure_delay',    type:"value", rw: "rw", min:0,  max:600,  scale:1,   unit:"seconds",      desc:'Turn off delay'],
                [dp:103, name:'cline',              type:"value", rw: "ro"], 
                [dp:104, name:'illuminance_lux',    type:"value", rw: "ro",                   scale:10,  unit:"lx",           desc:'illuminance'],            // divideBy10 !
                [dp:105, name:'entry_sensitivity',  type:"value", rw: "rw", min:0,  max:9,    scale:1,   unit:"x",            desc:'Entry sensitivity'],
                [dp:106, name:'entry_distance_indentation', type:"value", rw: "rw", min:0, max:800, scale:100, unit:"meters", desc:'Entry distance indentation'],
                [dp:107, name:'breaker_mode',       type:"enum",  rw: "rw", min:0,  max:1,    map:[0:"standard", 1:"local"],  desc:'Status Breaker mode: standard is external, local is auto'],
                [dp:108, name:'breaker_status',     type:"enum",  rw: "rw", min:0,  max:1,    map:[0:"OFF", 1:"ON"],          desc:'Breaker status changes with breaker_mode->standard'],
                [dp:109, name:'status_indication',  type:"enum",  rw: "rw", min:0,  max:1,    map:[0:"OFF", 1:"ON"],          desc:'Led backlight when triggered'],
                [dp:110, name:'illumin_threshold',  type:"value", rw: "rw", min:0,  max:4200, scale:10,  unit:"lx",           desc:'Illumination threshold for switching on'],           // divideBy10 ! 
                [dp:111, name:'breaker_polarity',   type:"enum",  rw: "ro", min:0,  max:1,    map:[0:"NC", 1:"NO"],           desc:'normally open / normally closed factory setting'],
                [dp:112, name:'block_time',         type:"value", rw: "rw", min:0,  max:100,  scale:1,   unit:"seconds",      desc:'Block time'],
                [dp:113, name:'parameter_setting_result', type:"value", rw: "ro"],
                [dp:114, name:'factory_parameters', type:"value", rw: "ro"],
                [dp:115, name:'sensor', type:"bool",  rw: "ro"]
            ],        
            deviceJoinName: "Tuya Human Presence Detector SBYX0LM6",
            configuration : [:]
    ],    
    
    // isLINPTECHradar() 
    "TS0225_LINPTECH_RADAR"   : [                                      // https://github.com/Koenkk/zigbee2mqtt/issues/18637 
            description   : "Tuya TS0225_LINPTECH 24GHz Radar",        // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/646?u=kkossev
            models        : ["TS0225"],                                // DPs are unknown    // https://home.miot-spec.com/spec/linp.sensor_occupy.hb01 
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "IlluminanceMeasurement": true, "DistanceMeasurement":true],
            preferences   : ["fadingTime":"101", "motionDetectionDistance":"0xE002:0xE00B","motionDetectionSensitivity":"0xE002:0xE004", "staticDetectionSensitivity":"0xE002:0xE005"],                             
            fingerprints  : [                                          // https://www.amazon.com/dp/B0C7C6L66J?ref=ppx_yo2ov_dt_b_product_details&th=1 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,E002,4000,EF00,0500", outClusters:"0019,000A", model:"TS0225", manufacturer:"_TZ3218_awarhusb", deviceJoinName: "Tuya TS0225_LINPTECH 24Ghz Human Presence Detector"]       // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
            ],
            commands      : ['refresh':'refresh', 'motionDetectionDistance':'motionDetectionDistance', 'motionDetectionSensitivity':'motionDetectionSensitivity', "staticDetectionSensitivity":"staticDetectionSensitivity"],
            // the tuyaDPs revealed from iot.tuya.com are actually not used by the device! The only exception is dp:101 
            tuyaDPs:       [ 
                [dp:101, name:'fadingTime',                                   type:"number", rw: "rw", min:10, max:9999, defaultValue:10,  step:1,  scale:1,   unit:"seconds", title: "<b>Fading time</b>", description:'<i>Presence inactivity timer, seconds</i>']                                  // aka 'nobody time'
            ],
            // LINPTECH / MOES are using a custom cluster 0xE002 for the settings (except for the fadingTime), ZCL cluster 0x0400 for illuminance (malformed reports!) and the IAS cluster 0x0500 for motion detection
            attributes:       [ 
                [at:"0xE002:0xE001",  name:'existance_time',                  type:"number",  dt: "UINT16", rw: "ro", min:0,  max:65535,  step:1,  scale:1,   unit:"minutes",   title: "<b>Existance time/b>",                 description:'<i>existance (presence) time</i>'],                    // aka Presence Time
                [at:"0xE002:0xE004",  name:'motionDetectionSensitivity',      type:"number",  dt: "UINT8",  rw: "rw", min:0,  max:5,      defaultValue:5, step:1,  scale:1,   unit:"x",         title: "<b>Motion Detection Sensitivity</b>",  description:'<i>Large motion detection sensitivity</i>'],           // aka Motionless Detection Sensitivity
                [at:"0xE002:0xE005",  name:'staticDetectionSensitivity',      type:"number",  dt: "UINT8",  rw: "rw", min:0,  max:5,      defaultValue:5,      step:1,  scale:1,   unit:"x",         title: "<b>Static Detection Sensitivity</b>",  description:'<i>Static detection sensitivity</i>'],                 // aka Motionless Detection Sensitivity 
                [at:"0xE002:0xE00A",  name:"distance",                        type:"decimal", dt: "UINT16", rw: "ro", min:0,  max:600,    scale:100,  unit:"meters",            title: "<b>Distance</b>",                      description:'<i>Measured distance</i>'],                            // aka Current Distance    
                [at:"0xE002:0xE00B",  name:'motionDetectionDistance',         type:"decimal", dt: "UINT16", rw: "rw", min:0.75, max:6.00, defaultValue:6.0,  step:75, scale:100, unit:"meters", title: "<b>Motion Detection Distance</b>",     description:'<i>Large motion detection distance, meters</i>']               // aka Far Detection
            ],
            spammyDPsToIgnore : [19],
            spammyDPsToNotTrace : [19],
            deviceJoinName: "Tuya TS0225_LINPTECH 24Ghz Human Presence Detector",
            configuration : [:]
    ],    
  
    
/*

X        Presence State                        1        (none/presence)
X        Far Detection                        4        (600cm)
X        Presence Time                         12        (2Min)
X        Motion Detection Sensitivity        15     (5)
X        Motionless Detection Sensitivity    16        (5)
X        Current Distance                    19        (270cm, 97cm ... )
X        Illuminance Value                     20        (228lux)
X        nobody time                            101        (10min)

*/
    
    
    //  no-name 240V AC ceiling radar presence sensor                
    "TS0225_EGNGMRZH_RADAR"   : [                                    // https://github.com/sprut/Hub/issues/2489
            description   : "Tuya TS0225_EGNGMRZH 24GHz Radar",      // isEGNGMRZHradar()
            models        : ["TS0225"],                              
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "IlluminanceMeasurement": true, "DistanceMeasurement":true],
            preferences   : [:],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,,0500,1000,EF00,0003,0004,0008", outClusters:"0019,000A", model:"TS0225", manufacturer:"_TZFED8_egngmrzh", deviceJoinName: "Tuya TS0225_EGNGMRZH 24Ghz Human Presence Detector"]       // https://www.aliexpress.com/item/1005004788260949.html                  // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/539?u=kkossev
            ],
            // uses IAS for occupancy!
            tuyaDPs:        [
                [dp:101, name:"illuminance",        type:"value", rw: "ro", min:0,  max:10000, scale:1,   unit:"lx"],        // https://github.com/Koenkk/zigbee-herdsman-converters/issues/6001
                [dp:103, name:"distance",           type:"value", rw: "ro", min:0,  max:100,   scale:10,  unit:"meters"],
                [dp:104, name:"unknown",            type:"value",  rw: "ro"],    //68
                [dp:105, name:"unknown",            type:"value",  rw: "ro"],    //69
                [dp:109, name:"unknown",            type:"value",  rw: "ro"],    //6D
                [dp:110, name:"unknown",            type:"value",  rw: "ro"],    //6E
                [dp:111, name:"unknown",            type:"value",  rw: "ro"],    //6F
                [dp:114, name:"unknown",            type:"value",  rw: "ro"],    //72
                [dp:115, name:"unknown",            type:"value",  rw: "ro"],    //73
                [dp:116, name:"unknown",            type:"value",  rw: "ro"],    //74
                [dp:118, name:"unknown",            type:"value",  rw: "ro"],    //76
                [dp:119, name:"unknown",            type:"value",  rw: "ro"]     //77
            ],
            deviceJoinName: "Tuya TS0225_AWARHUSB 24Ghz Human Presence Detector",
            configuration : ["battery": false]
    ],    

    //
    
    "OWON_OCP305_RADAR"   : [
            description   : "OWON OCP305 Radar",
            models        : ["OCP305"],
            device        : [type: "radar", powerSource: "dc", isSleepy:false],
            capabilities  : ["MotionSensor": true, "Battery": true],
            preferences   : [:],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0406", outClusters:"0003", model:"OCP305", manufacturer:"OWON"]
            ],
            deviceJoinName: "OWON OCP305 Radar",
            configuration : ["0x0406":"bind"]
    ],
    
    
    "UNKNOWN"             : [                        // the Device Profile key (shown in the State Variables)
            description   : "Unknown device",        // the Device Profile description (shown in the Preferences)
            models        : ["UNKNOWN"],             // used to match a Device profile if the individuak fingerprints do not match
            device        : [
                type: "unknown_device_type",         // 'radar'
                isIAS:true,                          // define it for PIR sensors only!
                powerSource: "dc",                   // determines the powerSource value - can be 'battery', 'dc', 'mains'
                isSleepy:false                       // determines the update and ping behaviour
            ],
            capabilities  : ["MotionSensor": true, "IlluminanceMeasurement": true, "Battery": true],
            preferences   : ["motionReset":true],
            //fingerprints  : [
            //    [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0406", outClusters:"0003", model:"model", manufacturer:"manufacturer"]
            //],
            tuyaDPs:        [
                [
                    dp:1,
                    name:"presence_state",
                    type:"enum",
                    rw: "ro",
                    min:0,
                    max:1,
                    map:[0:"none", 1:"presence"],
                    desc:'Presence state'
                ]
            ],        
            deviceJoinName: "Unknown device",        // used during the inital pairing, if no individual fingerprint deviceJoinName was found
            configuration : ["battery": true],
            batteries     : "unknown"
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
        if (profileMap.description.equals(valueStr)) {
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
def getPteferenceMap( String param, boolean debug=false ) {
    Map foundMap = [:]
    if (!(param in DEVICE.preferences)) {
        if (debug) log.warn "getPteferenceMap: preference ${param} not defined for this device!"
        return null
    }
    def preference = DEVICE.preferences["$param"]
    if (debug) log.debug "getPteferenceMap: preference ${param} found. value is ${preference} isTuyaDP=${preference.isNumber()}"
    if (preference.isNumber()) {
        // find the preference in the tuyaDPs map
        int dp = safeToInt(preference)
        def dpMaps   =  DEVICE.tuyaDPs 
        foundMap = dpMaps.find { it.dp == dp }
    }
    else { // cluster:attribute
        if (debug) log.trace "${DEVICE.attributes}"
        def dpMaps   =  DEVICE.tuyaDPs 
        foundMap = DEVICE.attributes.find { it.at == preference }
    }
    if (debug) log.debug "getPteferenceMap: foundMap = ${foundMap}"
    return foundMap     
}

/**
 * Resets the device preferences to their default values.
 * @param debug A boolean indicating whether to output debug information.
 */
def resetPreferencesToDefaults(boolean debug=false ) {
    Map preferences = DEVICE.preferences
    Map parMap = [:]
    preferences.each{ parName, mapValue -> 
        if (debug) log.trace "$parName $mapValue"
        // find the individual preference map
        parMap = getPteferenceMap(parName, false)
        log.trace "parMap = $parMap"
        // parMap = [at:0xE002:0xE005, name:staticDetectionSensitivity, type:number, dt:UINT8, rw:rw, min:0, max:5, step:1, scale:1, unit:x, title:Static Detection Sensitivity, description:Static detection sensitivity]
        if (parMap.defaultValue == null) {
            return // continue
        }
        if (debug) log.info "par ${parName} defaultValue = ${parMap.defaultValue}"
        device.updateSetting("${parMap.name}",[value:parMap.defaultValue, type:parMap.type])
    }
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
private getDP_TYPE_RAW()        { "01" }    // [ bytes ]
private getDP_TYPE_BOOL()       { "01" }    // [ 0/1 ]
private getDP_TYPE_VALUE()      { "02" }    // [ 4 byte value ]
private getDP_TYPE_STRING()     { "03" }    // [ N byte string ]
private getDP_TYPE_ENUM()       { "04" }    // [ 0-255 ]
private getDP_TYPE_BITMAP()     { "05" }    // [ 1,2,4 bytes ] as bits


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
            logDebug "ignored IAS zone status"
            return
        }
        else {
            parseIasMessage(description)    // TS0202 Motion sensor
        }
    }
    else if (description?.startsWith('enroll request')) {
        logDebug "parse: enroll request: $description"
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */
        if (settings?.logEnable) log.info "${device.displayName} Sending IAS enroll response..."
        ArrayList<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
        logDebug "enroll response: ${cmds}"
        sendZigbeeCommands( cmds )  
    }    
    else if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        try  {
            descMap = myParseDescriptionAsMap(description)
        }
        catch (e) {
            logWarn "parse: exception '${e}' caught while processing description ${description}"
            return
        }
        if ((isChattyRadarReport(descMap) || isSpammyDPsToIgnore(descMap)) && (_TRACE_ALL != true))  {
            // do not even log these spammy distance reports ...
            return
        }
        //
        if (!isSpammyDPsToNotTrace(descMap) || (_TRACE_ALL == true)) {
            logDebug "parse: (${device.getDataValue('manufacturer')}, ${(getDeviceGroup())}, ${driverVersionAndTimeStamp()}) descMap = ${descMap}"
        }
        //
        if (descMap.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
            if (descMap.attrInt == 0x0021) {
                getBatteryPercentageResult(Integer.parseInt(descMap.value,16))
            } else if (descMap.attrInt == 0x0020){
                sendBatteryVoltageEvent(Integer.parseInt(descMap.value, 16))
            }
            else {
                logWarn "power cluster not parsed attrint $descMap.attrInt"
            }
        }     
        else if (descMap.cluster == "0400" && descMap.attrId == "0000") {
            def rawLux = Integer.parseInt(descMap.value,16)
            if ((isHL0SS9OAradar() || is2AAELWXKradar()) && _IGNORE_ZCL_REPORTS == true) {
                logDebug "ignored ZCL illuminance report (raw:Lux=${rawLux})"
                return
            }
            else {  // including isLINPTECHradar
                illuminanceEvent( rawLux )
            }
        }  
        else if (descMap.cluster == "0402" && descMap.attrId == "0000") {
            def raw = Integer.parseInt(descMap.value,16)
            temperatureEvent( raw / 10.0 )
        }
        else if (descMap.cluster == "0405" && descMap.attrId == "0000") {
            def raw = Integer.parseInt(descMap.value,16)
            humidityEvent( raw / 1.0 )
        }
        else if (descMap.cluster == "0406" && descMap.attrId == "0000") {    // OWON
            def raw = Integer.parseInt(descMap.value,16)
            handleMotion( raw & 0x01 )
        }
        else if (descMap?.clusterInt == CLUSTER_TUYA) {
            processTuyaCluster( descMap )
        }
        else if (descMap.cluster  == "E002") {
            processE002Cluster( descMap )
        }
        else if (descMap.profileId == "0000") {    // zdo
            parseZDOcommand(descMap)
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "0001") {
            if (settings?.logEnable) log.info "${device.displayName} Tuya check-in (application version is ${descMap?.value})"
            def application = device.getDataValue("application") 
            if (application == null || (application as String) != (descMap?.value as String)) {
                device.updateDataValue("application", descMap?.value)
                logInfo "application version set to ${descMap?.value}"
                updateTuyaVersion()
            }
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "0004") {
            if (settings?.logEnable) log.info "${device.displayName} received device manufacturer ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "0007") {
            //def value = descMap?.value == "00" ? "battery" : descMap?.value == "01" ? "mains" : descMap?.value == "03" ? "battery" : descMap?.value == "04" ? "dc" : "unknown" 
            def powerSourceReported = powerSourceOpts.options[descMap?.value as int]
            logInfo "reported Power source <b>${powerSourceReported}</b> (${descMap?.value})"
            // the powerSource reported by the device is very often not correct ...
            if (DEVICE.device?.powerSource != null) {
                powerSourceReported = DEVICE.device?.powerSource
                logDebug "forcing the powerSource to <b>${powerSourceReported}</b>"
            }
            else if (is4in1() || ((DEVICE.device?.type == "radar")  || isEGNGMRZHradar() || isSBYX0LM6radar() || isHL0SS9OAradar() || is2AAELWXKradar() || isYXZBRB58radar() || isSXM7L9XAradar() || isHumanPresenceSensorAIR() || isBlackPIRsensor() ))  {     // for radars force powerSource 'dc'
                powerSourceReported = powerSourceOpts.options[04]    // force it to dc !
            }
            powerSourceEvent( powerSourceReported )
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFDF") {
            logDebug "Tuya check-in (cluster revision=${descMap?.value})"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFE2") {
            logDebug "Tuya AppVersion is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && (descMap?.attrId in ["FFE0", "FFE1", "FFE3", "FFE4"])) {
            logDebug "Tuya unknown attribute ${descMap?.attrId} value is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFFE") {
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0500" && descMap?.command in ["01", "0A"] ) {    //IAS read attribute response
            //if (settings?.logEnable) log.debug "${device.displayName} IAS read attribute ${descMap?.attrId} response is ${descMap?.value}"
            if (descMap?.attrId == "0000") {
                def value = Integer.parseInt(descMap?.value, 16)
                logInfo "IAS Zone State repot is '${ZONE_STATE[value]}' (${value})"
            } else if (descMap?.attrId == "0001") {
                def value = Integer.parseInt(descMap?.value, 16)
                logInfo "IAS Zone Type repot is '${ZONE_TYPE[value]}' (${value})"
            } else if (descMap?.attrId == "0002") {
                logDebug "IAS Zone status repoted: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}"
                handleMotion(Integer.parseInt(descMap?.value, 16))
            } else if (descMap?.attrId == "0010") {
                logDebug "IAS Zone Address received (bitmap = ${descMap?.value})"
            } else if (descMap?.attrId == "0011") {
                logDebug "IAS Zone ID: ${descMap.value}" 
            } else if (descMap?.attrId == "0012") {
                logDebug "IAS Num zone sensitivity levels supported: ${descMap.value}" 
            } else if (descMap?.attrId == "0013") {
                def value = Integer.parseInt(descMap?.value, 16)
                logInfo "IAS Current Zone Sensitivity Level = ${sensitivityOpts.options[value]} (${value})"
                device.updateSetting("settings.sensitivity", [value:value.toString(), type:"enum"])                
            }
            else if (descMap?.attrId == "F001") {    // [raw:7CC50105000801F02000, dni:7CC5, endpoint:01, cluster:0500, size:08, attrId:F001, encoding:20, command:0A, value:00, clusterInt:1280, attrInt:61441]
                def value = Integer.parseInt(descMap?.value, 16)
                def str   = getKeepTimeOpts().options[value]
                logInfo "Current IAS Zone Keep-Time =  ${str} (${value})"
                device.updateSetting("keepTime", [value: value.toString(), type: 'enum'])                
            }
            else {
                logWarn "Zone status attribute ${descMap?.attrId}: NOT PROCESSED ${descMap}" 
            }
        } // if IAS read attribute response
        else if (descMap?.clusterId == "0500" && descMap?.command == "04") {    //write attribute response (IAS)
            logDebug "IAS write attribute response is ${descMap?.data[0] == '00' ? 'success' : '<b>FAILURE</b>'}"
        } 
        else if (descMap?.command == "04") {    // write attribute response (other)
            logDebug "write attribute response is ${descMap?.data[0] == '00' ? 'success' : '<b>FAILURE</b>'}"
        } 
        else if (descMap?.command == "0B") {    // default command response
            String commandId = descMap.data[0]
            String status = "0x${descMap.data[1]}"
            logDebug "zigbee default command response cluster: ${clusterLookup(descMap.clusterInt)} command: 0x${commandId} status: ${descMap.data[1]== '00' ? 'success' : '<b>FAILURE</b>'} (${status})"
        } 
        else if (descMap?.command == "00" && descMap?.clusterId == "8021" ) {    // bind response
            logDebug "bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1]=="00" ? 'success' : '<b>FAILURE</b>'})"
        } 
        else {
            logDebug "<b> NOT PARSED </b> : descMap = ${descMap}"
        }
    } // if 'catchall:' or 'read attr -'
    else if (description.startsWith('raw')) {
        // description=raw:11E201E0020A0AE0219F00, dni:11E2, endpoint:01, cluster:E002, size:0A, attrId:E00A, encoding:21, command:0A, value:009F, clusterInt:57346, attrInt:57354
        descMap = [:]
        descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry ->
            def pair = entry.split(':')
            [(pair.first().trim()): pair.last().trim()]
        }        
        logDebug "parsed row : cluster=${descMap.cluster} attrId=${descMap.attrId}"
        if (descMap.cluster  ==  "E002") {
            processE002Cluster( descMap )
        }
        else {
            logWarn "<b>UNPROCESSED RAW cluster ${descMap?.cluster}</b> description = ${description}"
        }
    }
    else {
        logDebug "<b> UNPROCESSED </b> description = ${description} descMap = ${zigbee.parseDescriptionAsMap(description)}"
    }
}

Map myParseDescriptionAsMap( String description )
{
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

def parseZDOcommand( Map descMap ) {
    switch (descMap.clusterId) {
        case "0006" :
            if (settings?.logEnable) log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7]+descMap.data[6]})"
            break
        case "0013" : // device announcement
            if (settings?.logEnable) log.info "${device.displayName} Received device announcement, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2]+descMap.data[1]}, Capability Information: ${descMap.data[11]})"
            break
        case "8004" : // simple descriptor response
            if (settings?.logEnable) log.info "${device.displayName} Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}"
            //parseSimpleDescriptorResponse( descMap )
            break
        case "8005" : // endpoint response
            def endpointCount = descMap.data[4]
            def endpointList = descMap.data[5]
            if (settings?.logEnable) log.info "${device.displayName} zdo command: cluster: ${descMap.clusterId} (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}"
            break
        case "8021" : // bind response
            if (settings?.logEnable) log.info "${device.displayName} Received bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1]=="00" ? 'Success' : '<b>Failure</b>'})"
            break
        case "8022" : //unbind request
            if (settings?.logEnable) log.info "${device.displayName} zdo command: cluster: ${descMap.clusterId} (unbind request)"
            break
        case "8034" : //leave response
            if (settings?.logEnable) log.info "${device.displayName} zdo command: cluster: ${descMap.clusterId} (leave response)"
            break
        default :
            if (settings?.logEnable) log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
    }
}

def processE002Cluster( descMap ) {
    // raw:11E201E0020A0AE0219F00, dni:11E2, endpoint:01, cluster:E002, size:0A, attrId:E00A, encoding:21, command:0A, value:009F, clusterInt:57346, attrInt:57354
    def value = zigbee.convertHexToInt(descMap.value) 
    switch (descMap.attrId) {
        case "E001" :    // the existance_time in minutes, UINT16
            sendEvent("name": "existance_time", "value": value, "unit": "minutes", "type": "physical", "descriptionText": "Presence is active for ${value} minutes")
            logDebug "Cluster ${descMap.cluster} Attribute ${descMap.attrId} (existance_time) value is ${value} (0x${descMap.value} minutes)"
            break
        case "E004" :    // value:05    // motionDetectionSensitivity, UINT8
            if (settings?.logEnable == true || settings?.motionDetectionSensitivity != (value as int)) { logInfo "received LINPTECH radar motionDetectionSensitivity : ${value}"} else {logDebug "skipped ${settings?.motionDetectionSensitivity} == ${value as int}"}
            device.updateSetting("motionDetectionSensitivity", [value:value as int , type:"number"])
            break
        case "E005" :    // value:05    // staticDetectionSensitivity, UINT8
            if (settings?.logEnable == true || settings?.staticDetectionSensitivity != (value as int)) { logInfo "received LINPTECH radar staticDetectionSensitivity : ${value}"} else {logDebug "skipped ${settings?.staticDetectionSensitivity} == ${value as int}"}
            device.updateSetting("staticDetectionSensitivity", [value:value as int , type:"number"])
            break
        case "E00A" :    // value:009F, 6E, 2E, .....00B6 0054 - distance, UINT16
            if (settings?.ignoreDistance == false) {
                logDebug "LINPTECH radar target distance is ${value/100} m"
                sendEvent(name : "distance", value : value/100, unit : "m")
            }        
            break
        case "E00B" :    // value:value:600 -- motionDetectionDistance, UINT16
            if (settings?.logEnable == true || (safeToInt(settings?.motionDetectionDistance)*100 != value)) {logInfo "received LINPTECH radar Motion Detection Distance  : ${value/100} m"}
            device.updateSetting("motionDetectionDistance", [value:value/100, type:"decimal"])
            break
        default : 
            logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"
            break
    }
}


def processTuyaCluster( descMap ) {
    if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME
        logDebug "time synchronization request from device, descMap = ${descMap}"
        def offset = 0
        try {
            offset = location.getTimeZone().getOffset(new Date().getTime())
            //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}"
        }
        catch(e) {
            if (settings?.logEnable) log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero"
        }
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8))
        logDebug "sending time data : ${cmds}"
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
        if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response
        String clusterCmd = descMap?.data[0]
        def status = descMap?.data[1]            
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
        if (status != "00" && !(isHL0SS9OAradar() || is2AAELWXKradar())) {
            logWarn "ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                
        }
    } 
    else if ((descMap?.clusterInt==CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02"|| descMap?.command == "06"))
    {
  //      try {
            def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
            def dp = zigbee.convertHexToInt(descMap?.data[2])                // "dp" field describes the action/message of a command frame
            def dp_id = zigbee.convertHexToInt(descMap?.data[3])             // "dp_identifier" is device dependant
            def fncmd = getTuyaAttributeValue(descMap?.data)                 // 
            def dp_len = zigbee.convertHexToInt(descMap?.data[5])            // the length of the DP - 1 or 4 ...
            
            updateStateTuyaDPs(descMap, dp, dp_id, fncmd, dp_len)
            processTuyaDP(descMap, dp, dp_id, fncmd, dp_len) 
  //      }
 //       catch (e) {
 //           logWarn "<b>catched exception</b> ${e} while processing descMap: ${descMap}"
  //          return
  //      }
    } // Tuya commands '01' and '02'
    else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "11" ) {
        // dont'know what command "11" means, it is sent by the square black radar when powered on. Will use it to restore the LED on/off configuration :) 
        logDebug "Tuya <b>descMap?.command = ${descMap?.command}</b> descMap.data = ${descMap?.data}" 
        if (("indicatorLight" in DEVICE.preferences))  {
            if (settings?.indicatorLight != null) {
                ArrayList<String> cmds = []
                def value = safeToInt(indicatorLight.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2) 
                cmds += sendTuyaCommand("67", DP_TYPE_BOOL, dpValHex)
                if (settings?.logEnable) log.info "${device.displayName} restoring indicator light to : ${blackRadarLedOptions[value.toString()]} (${value})"  
                sendZigbeeCommands( cmds ) 
            }
        }
        else {
            logWarn "unhandled Tuya <b>command 11</b> descMap: ${descMap}" 
        }
    }
    else {
        logWarn "<b>NOT PROCESSED</b> Tuya <b>descMap?.command = ${descMap?.command}</b> cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
    }
}

//
// called from processTuyaCluster for every Tuya device, prior to processing the DP
// updates state.tuyaDPs, increasing the DP count and updating the DP value
//
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
void updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len) {
    if (state.unknownDPs == null) state.unknownDPs = []
    if (dp == null || dp < 0 || dp > 255) return
    if (state.unknownDPs == [] || !(dp in state.unknownDPs)) {
        List list = state.unknownDPs as List
        list.add(dp)
        list = list.sort()
        state.unknownDPs = list
    }
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
    if (!(descMap?.clusterId == "EF00" && (descMap?.command in ["01", "02"]))) { return false }
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
    if (!(descMap?.clusterId == "EF00" && (descMap?.command in ["01", "02"]))) { return false }
    if (descMap?.data?.size <= 2) { return false }
    Integer dp = zigbee.convertHexToInt(descMap.data[2]) 
    def spammyList = deviceProfilesV2[getDeviceGroup()].spammyDPsToNotTrace
    return (spammyList != null && (dp in spammyList))
}


def compareAndConvertTuyaToHubitatPreferenceValue(foundItem, fncmd, preference) {
    if (foundItem == null || fncmd == null || preference == null) { return [true, "none"] }
    if (foundItem.type == null) { return [true, "none"] }
    def tuyaValue
    boolean isEqual
    def preferenceValue
    def tuyaValueScaled
    switch (foundItem.type) {
        case "bool" :       // [0:"OFF", 1:"ON"] 
        case "enum" :       // [0:"inactive", 1:"active"]
            tuyaValueScaled  = foundItem.map[fncmd.toString()] ?: "unknown"
            preferenceValue = preference as String
            isEqual    = ((attrValue  as String) == (tuyaValue as String))
            logDebug "compareAndConvertTuyaToHubitatPreferenceValue: <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}"
            break
        case "value" :      // depends on foundItem.scale
        case "number" :
        case "decimal" :
            if (foundItem.scale == null || foundItem.scale == 0 || foundItem.scale == 1) {    // compare as integer
                tuyaValueScaled  = safeToInt(fncmd)
                preferenceValue = preference as Integer
                isEqual    = ((preferenceValue as int) == (tuyaValueScaled as int))
                //log.trace "fncmd=${valueScaled} valueScaled=${valueScaled} isEqual=${isEqual}"
            }
            else {        // compare as float
                tuyaValue = safeToInt(fncmd)
                tuyaValueScaled  = (tuyaValue as double) / (foundItem.scale as double) 
                preferenceValue = preference as Double                
                //logDebug "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preferenceValue}"
                isEqual = Math.abs((preferenceValue as double) - (tuyaValueScaled as double)) < 0.001 
            }
            break
        default :
            logWarn "compareAndConvertTuyaToHubitatEventValue: unsupported type %{foundItem.type}"
            return [true, "none"]   // fallback - assume equal
    }
    //log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } tuyaValueScaled=${tuyaValueScaled} "
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
//    hubitatValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value
//
//  TODO: refactor!
//
def compareAndConvertTuyaToHubitatEventValue(foundItem, fncmd) {
    if (foundItem == null) { return [true, "none"] }
    def dpType = foundItem.type
    if (dpType == null) { return [true, "none"] }
    def attrValue 
    def tuyaValue
    def hubitatValue
    String name = foundItem.name
    boolean isEqual
    switch (dpType) {
        case "bool" :       // [0:"OFF", 1:"ON"] 
        case "enum" :       // [0:"inactive", 1:"active"]
            attrValue  = device.currentValue(name) ?: "unknown"
            tuyaValue  = foundItem.map[fncmd.toString()] ?: "unknown"
            hubitatValue = tuyaValue
            isEqual    = ((attrValue  as String) == (tuyaValue as String))
            logDebug "compareAndConvertTuyaToHubitatEventValue: <b>dpType=${dpType}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} tuyaValue=${tuyaValue} fncmd=${fncmd}"
            break
        case "value" :      // depends on foundItem.scale
        case "number" :
        case "decimal" :
            if (foundItem.scale == null || foundItem.scale == 0 || foundItem.scale == 1) {    // compare as integer
                attrValue  = safeToInt(device.currentValue(name))
                tuyaValue  = safeToInt(fncmd)
                hubitatValue = tuyaValue
                isEqual    = ((attrValue as int) == (tuyaValue as int))
                //log.trace "fncmd=${valueScaled} valueScaled=${valueScaled} isEqual=${isEqual}"
            }
            else {        // compare as float
                //log.trace "float"
                attrValue = safeToDouble(device.currentValue(name))
                tuyaValue = safeToInt(fncmd)
                double valueScaled  = (tuyaValue as double) / (foundItem.scale as double) 
                hubitatValue = valueScaled                
                isEqual = Math.abs((attrValue as double) - (valueScaled as double)) < 0.001 
                //log.trace "fncmd=${valueScaled} valueScaled=${valueScaled} isEqual=${isEqual}"
            }
            break
        default :
            logWarn "compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{}"
            return [true, "none"]   // fallback - assume equal
    }
    //log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} "
    return [isEqual, hubitatValue]
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
boolean processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) {
    if (state.deviceProfile == null)  { return false }
    if (!(isBlackSquareRadar() || isKAPVNNLKradar() || isLINPTECHradar() || isZY_M100Radar()))      { return false }       // only these models are handled here for now ...
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

    def name = foundItem.name                                    // preference name as in the tuyaDPs map
    def existingPrefName = settings[name]                        // preference name as in Hubitat settings (preferences), if already created.
    def perfValue = null   // preference value
    boolean preferenceExists = existingPrefName != null          // check if there is an existing preference for this dp  
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this dp
    boolean isEqual = false
    boolean wasChanged = false
    boolean doNotTrace = isSpammyDPsToNotTrace(descMap)          // do not log/trace the spammy DP's
    if (!doNotTrace) {
        logDebug "dp=${dp} ${foundItem.name} (type ${foundItem.type}, rw=${foundItem.rw} isAttribute=${isAttribute}, preferenceExists=${preferenceExists}) value is ${fncmd} - ${foundItem.description}"
    }
    // check if the dp has the same value as the last one, or the value has changed
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ...
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : ""
    String valueScaled
    String descText = descText  = "${name} is ${fncmd} ${unitText}"    // the default description text for log events
    
    // TODO - check if DP is in the list of the received state.tuyaDPs - then we have something to compare !
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this dp is not stored anywhere - just seend an Info log if Debug is enabled
        if (!doNotTrace) {                                      // only if the DP is not in the spammy list
            if (settings.logEnable) { logInfo "${descText}"}
        }
        // no more processing is needed, as this DP is not a preference and not an attribute
        return true
    }
    
    
    // first, check if there is a preference defined to be updated
    if (preferenceExists) {
        // preference exists and its's value is extracted
        (isEqual, perfValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, fncmd, existingPrefName)    
        if (isEqual == true) {                                 // the DP value is the same as the preference value - no need to update the preference
            logDebug "no change: preference '${name}' scaled value ${perfValue} equals dp value ${fncmd}"
        }
        else {
            logDebug "preference '${name}' value ${perfValue} <b>differs</b> from dp value ${fncmd}"
            if (debug) log.info "updating par ${name} from ${perfValue} to ${fncmd}}"
            device.updateSetting("${name}",[value:fncmd, type:parMap.type])
            wasChanged = true                // send an event also!
        }
    }
    else {    // no preference exists for this dp
        // if not in the spammy list - log it!
        unitText = foundItem.unit != null ? "$foundItem.unit" : ""
        //logInfo "${name} is ${fncmd} ${unitText}"
    }    
    
    // second, send an event if this is declared as an attribute!
    if (isAttribute) {                                         // this DP has an attribute that must be sent in an Event
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, fncmd)
        descText  = "${name} is ${valueScaled} ${unitText} (raw:${fncmd})"
        
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along!
            if (!doNotTrace) {
                if (settings.logEnable) { logInfo "${descText} (no change)"}
            }
            return true                                      // we are done (if there was potentially a preference, it should be already set to the same value)
        }
        
        // DP value (fncmd) is not equal to the attribute last value or was changed- we must send an event!
        switch (name) {
            case "motion" :
                handleMotion(motionActive = fncmd)
                break
            case "temperature" :
                temperatureEvent(fncmd / 10.0)      // TODO - DivideByX
                break
            case "humidity" :
                humidityEvent(fncmd)                // TODO - DivideByX
                break
            case "illuminance" :
                illuminanceEventLux(fncmd)          // TODO - DivideByX
                break
            default :
                sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: "physical", isStateChange: true)    // attribute value is changed - send an event !
                logDebug "event ${name} sent w/ value ${valueScaled}"
                if (!doNotTrace) {
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
            logDebug "Tuya cluster: dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
        }
        //
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {
            // sucessfuly processed the new way - we are done.
            return
        }
        switch (dp) {
            case 0x01 : // motion for 2-in-1 TS0601 (_TZE200_3towulqd) and presence stat? for all radars, including isHumanPresenceSensorAIR, isHumanPresenceSensorFall and isBlackSquareRadar and is2AAELWXKradar()
                if (isIJXVKHD0radar()) {
                    logDebug "ignored IJXVKHD0radar event ${dp} fncmd = ${fncmd}"
                }
                else {
                    logDebug "(DP=0x01) motion event fncmd = ${fncmd}"
                    // 03/29/2023 settings.invertMotion handles the 2-in-1 TS0601 wierness ..
                    handleMotion(motionActive = fncmd)
                }
                break
            case 0x02 :
                if (/*isZY_M100Radar() ||*/ isYXZBRB58radar() || isSBYX0LM6radar()) {    // including HumanPresenceSensorScene and isHumanPresenceSensorFall
                    if (settings?.logEnable == true || settings?.radarSensitivity != safeToInt(device.currentValue("radarSensitivity"))) {logInfo "received Radar sensitivity : ${fncmd}"} //else {log.warn "skipped ${settings?.radarSensitivity} == ${fncmd as int}"}
                    device.updateSetting("radarSensitivity", [value:fncmd as int , type:"number"])
                    sendEvent(name : "radarSensitivity", value : fncmd as int)
                }
                else if (is2AAELWXKradar()) {
                    logDebug "TS0225 Radar Motion Detection Sensitivity dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || settings?.motionDetectionSensitivity != (fncmd as int)) { logInfo "received motionDetectionSensitivity : ${fncmd}"} else {logDebug "skipped ${settings?.motionDetectionSensitivity} == ${fncmd as int}"}
                    device.updateSetting("motionDetectionSensitivity", [value:fncmd as int , type:"number"])
                }            
                else if (isIJXVKHD0radar()) {
                    logDebug "ignored IJXVKHD0radar event ${dp} fncmd = ${fncmd}"
                }
                else {
                    logWarn "${device.displayName} non-radar event ${dp} fncmd = ${fncmd}"
                }
                break
            case 0x03 :
                if (/*isZY_M100Radar() ||*/ isYXZBRB58radar() || isSBYX0LM6radar()) {
                    if (settings?.logEnable == true || (settings?.minimumDistance != safeToDouble(device.currentValue("minimumDistance")))) {logInfo "received Radar Minimum detection distance : ${fncmd/100} m"}
                    device.updateSetting("minimumDistance", [value:fncmd/100, type:"decimal"])
                    sendEvent(name : "minimumDistance", value : fncmd/100, unit : "m")
                }
                else if (is2AAELWXKradar()) {
                    logDebug "TS0225 Radar Move Minimum Distance dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (safeToInt(settings?.motionMinimumDistance)*100 != fncmd)) {logInfo "received Radar Motion Minimum Distance  : ${fncmd/100} m"}
                    device.updateSetting("motionMinimumDistance", [value:fncmd/100, type:"decimal"])
                }
            
                else {        // also battery level STATE for TS0202 ? 
                    logWarn "non-radar event ${dp} fncmd = ${fncmd}"
                }
                break
            case 0x04 :    // maximumDistance for radars or Battery level for _TZE200_3towulqd (2in1)
                if (/*isZY_M100Radar() ||*/ isYXZBRB58radar() || isSBYX0LM6radar()) {
                    if (settings?.logEnable == true || (settings?.maximumDistance != safeToDouble(device.currentValue("maximumDistance")))) {logInfo "received Radar Maximum detection distance : ${fncmd/100} m"}
                    device.updateSetting("maximumDistance", [value:fncmd/100 , type:"decimal"])
                    sendEvent(name : "maximumDistance", value : fncmd/100, unit : "m")
                }
                else if (is2AAELWXKradar() /*|| isLINPTECHradar()*/) {
                    logDebug "TS0225/Linptech Radar Motion Detection Distance dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (safeToInt(settings?.motionDetectionDistance)*100 != fncmd)) {logInfo "received Radar Motion Detection Distance  : ${fncmd/100} m"}
                    device.updateSetting("motionDetectionDistance", [value:fncmd/100, type:"decimal"])
                }
                else {        // also battery level for TS0202 and TS0601 2in1 ; battery1 for Fantem 4-in-1 (100% or 0% )
                    logDebug "Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    handleTuyaBatteryLevel( fncmd )                    
                }
                break
            case 0x05 :     // tamper alarm for TS0202 4-in-1
                if (isYXZBRB58radar() || isSXM7L9XAradar()) {
                    logDebug "YXZBRB58/SXM7L9XA radar unknown event DP=0x05 fncmd = ${fncmd}"  
                }
                else {
                    def value = fncmd==0 ? 'clear' : 'detected'
                    logInfo "${device.displayName} tamper alarm is ${value} (dp=05,fncmd=${fncmd})"
                     sendEvent(name : "tamper",    value : value, isStateChange : true)
                }
                break
            case 0x06 :
                if (/*isZY_M100Radar() || */isSBYX0LM6radar()) {
                    if (settings?.logEnable == true || (radarSelfCheckingStatus[fncmd.toString()] != device.currentValue("radarStatus"))) {logInfo "Radar self checking status : ${radarSelfCheckingStatus[fncmd.toString()]} (${fncmd})"}        // @Field static final Map radarSelfCheckingStatus =  [ "0":"checking", "1":"check_success", "2":"check_failure", "3":"others", "4":"comm_fault", "5":"radar_fault",  ] 
                    sendEvent(name : "radarStatus", value : radarSelfCheckingStatus[fncmd.toString()])
                }
                else {    // TODO liminance for 4-in-1 !!!
                    logWarn "non-radar event ${dp} fncmd = ${fncmd}"
                }
                break
            case 0x07 : // temperature for 4-in-1 (no data)
                logDebug "4-in-1 temperature (dp=07) is ${fncmd / 10.0 } ${fncmd}"
                temperatureEvent( fncmd / 10.0 )
                break
            case 0x08 : // humidity for 4-in-1 (no data)
                logDebug "4-in-1 humidity (dp=08) is ${fncmd} ${fncmd}"
                humidityEvent( fncmd )
                break
            case 0x09 :
                if (/*isZY_M100Radar() || */isSBYX0LM6radar()) {
                    if (settings?.ignoreDistance == false) {
                        logInfo "Radar target distance is ${fncmd/100} m"
                        sendEvent(name : "distance", value : fncmd/100, unit : "m")
                    }
                }
                else {
                    // sensitivity for TS0202 4-in-1 and 2in1 _TZE200_3towulqd 
                    logInfo "received sensitivity : ${sensitivityOpts.options[fncmd]} (${fncmd})"
                    device.updateSetting("sensitivity", [value:fncmd.toString(), type:"enum"])                
                }
                break
            case 0x0A : // (10) keep time for TS0202 4-in-1 and 2in1 _TZE200_3towulqd
                logInfo "Keep Time (dp=0x0A) is ${keepTimeIASOpts.options[fncmd]} (${fncmd})"
                device.updateSetting("keepTime", [value:fncmd.toString(), type:"enum"])                
                break
            case 0x0B : // (11)    // isHL0SS9OAradar() 
                logDebug "TS0225 Radar Human Motion State is ${TS0225humanMotionState[fncmd.toString()]}"
                sendEvent(name : "humanMotionState", value : TS0225humanMotionState[fncmd.toString()])
                break
            case 0x0C : // (12)
                if (isHL0SS9OAradar()) {
                    logDebug "TS0225 Radar Presence Keep Time dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || settings?.presenceKeepTime != (fncmd as int)) { logInfo "received presenceKeepTime : ${fncmd}"} else {logDebug "skipped ${settings?.presenceKeepTime} == ${fncmd as int}"}
                    device.updateSetting("presenceKeepTime", [value:fncmd as int , type:"number"])                    
                }
                /*else if (isLINPTECHradar()) {
                    existanceTimeEvent(fncmd)
                }*/
                else {
                    illuminanceEventLux( fncmd )    // illuminance for TS0601 2-in-1
                }
                break
            case 0x0D : // (13)    // isHL0SS9OAradar() 
                logDebug "TS0225 Radar Motion Detection Distance dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                if (settings?.logEnable == true || (safeToInt(settings?.motionDetectionDistance)*100 != fncmd)) {logInfo "received Radar Motion Detection Distance  : ${fncmd/100} m"}
                device.updateSetting("motionDetectionDistance", [value:fncmd/100, type:"decimal"])
                break
            case 0x0E : // (14)    // isHL0SS9OAradar() 
                logDebug "TS0225 Radar Small Motion Detection Distance dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                if (settings?.logEnable == true || (safeToInt(settings?.smallMotionDetectionDistance)*100 != fncmd)) {logInfo "received Small Motion Detection Distance  : ${fncmd/100} m"}
                device.updateSetting("smallMotionDetectionDistance", [value:fncmd/100, type:"decimal"])
                break
            case 0x0F : // (15)    // isHL0SS9OAradar() isLINPTECHradar()
                logDebug "TS0225/Linptech Radar Motion Detection Sensitivity dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                if (settings?.logEnable == true || settings?.motionDetectionSensitivity != (fncmd as int)) { logInfo "received motionDetectionSensitivity : ${fncmd}"} else {logDebug "skipped ${settings?.motionDetectionSensitivity} == ${fncmd as int}"}
                device.updateSetting("motionDetectionSensitivity", [value:fncmd as int , type:"number"])
                break
            case 0x10 : // (16)    
                if (isLINPTECHradar()) { /*
                    logDebug "Linptech Radar Static Detection Sensitivity dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || settings?.staticDetectionSensitivity != (fncmd as int)) { logInfo "received staticDetectionSensitivity : ${fncmd}"} else {logDebug "skipped ${settings?.staticDetectionSensitivity} == ${fncmd as int}"}
                    device.updateSetting("staticDetectionSensitivity", [value:fncmd as int , type:"number"]) */
                }
                else {    // isHL0SS9OAradar() 
                    logDebug "TS0225 Radar Small Motion Detection Sensitivity dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || settings?.smallMotionDetectionSensitivity != (fncmd as int)) { logInfo "received smallMotionDetectionSensitivity : ${fncmd}"} else {logDebug "skipped ${settings?.smallMotionDetectionSensitivity} == ${fncmd as int}"}
                    device.updateSetting("smallMotionDetectionSensitivity", [value:fncmd as int , type:"number"])
                }
                break
            
            case 0x13 : // (19)
                if (isLINPTECHradar()) { /*
                    if (settings?.ignoreDistance == false) {
                        logInfo "Radar target distance is ${fncmd/100} m"
                        sendEvent(name : "distance", value : fncmd/100, unit : "m", type: "physical")
                    } */
                }
                else {
                    if (settings?.txtEnable) log.info "${device.displayName} reported unknown parameter dp=${dp} value=${fncmd}"
                }            
            
            case 0x14 : // (20)    // isHL0SS9OAradar() 
                if (isLINPTECHradar()) { /*
                    logDebug "skipped Linptech Tuya DP illuminance report dp=${dp} value=${fncmd}" */
                }
                else {
                    illuminanceEventLux( Math.round(fncmd / 10))    // illuminance for TS0225 radar
                }
                break
            case 0x18 : // (24)    // isHL0SS9OAradar() 
                if (settings?.logEnable) { logInfo "TS0225 Radar Indicator is ${fncmd?'On':'Off'} (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})" }
                if (settings?.logEnable || (settings?.ledIndicator ? 1:0) != (fncmd as int)) { logInfo "received ledIndicator : ${fncmd}"} else {logDebug "skipped ${settings?.ledIndicator} == ${fncmd as int}"}
                device.updateSetting("ledIndicator", [value:fncmd as Boolean , type:"bool"])
                break
            case 0x19 : // (25) 
                logDebug "Motion Switch battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                handleTuyaBatteryLevel( fncmd )
                break
            case 0x65 :    // (101)
                if (isYXZBRB58radar()) {    // [0x65, 'illuminance_lux', tuya.valueConverter.raw],
                    logDebug "(101) YXZBRB58 radar illuminance is ${fncmd}"
                    illuminanceEventLux( fncmd )                
                }
                else if (/*isZY_M100Radar() ||*/ isSBYX0LM6radar()) {                               // Tuya mmWave Presence Sensor (ZY-M100)
                    def value = fncmd / 10
                    if (settings?.logEnable == true || (settings?.detectionDelay) != safeToDouble(device.currentValue("detectionDelay")) ) {logInfo "received Radar detection delay : ${value} seconds (${fncmd})"}
                    device.updateSetting("detectionDelay", [value:value , type:"decimal"])
                    sendEvent(name : "detectionDelay", value : value)
                }
                else if (isHL0SS9OAradar()) {
                    logInfo "TS0225 Radar Alarm Time is ${fncmd} seconds (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})"
                    if (settings?.logEnable || (settings?.radarAlarmTime ?: 1) != (fncmd as int)) { logInfo "received radar Alarm Time : ${fncmd}"} else {logDebug "skipped ${settings?.radarAlarmTime} == ${fncmd as int}"}
                    device.updateSetting("radarAlarmTime", [value:fncmd as int , type:"number"])
                }
                else if (is2AAELWXKradar()) {
                    logDebug "TS0225 Radar Human Motion State is ${TS0225humanMotionState[fncmd.toString()]}"
                    sendEvent(name : "humanMotionState", value : TS0225humanMotionState[fncmd.toString()])
                }
                else if (isEGNGMRZHradar()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported unknown parameter dp=${dp} value=${fncmd}"
                }
                else if (isLINPTECHradar()) { /*
                    def value = fncmd 
                    if (settings?.logEnable == true || (settings?.fadingTime) != safeToDouble(device.currentValue("fadingTime")) ) {logInfo "received Radar fading time : ${value} seconds (${fncmd})"}
                    device.updateSetting("fadingTime", [value:value , type:"decimal"])
                    sendEvent(name : "fadingTime", value : value, unit : "s") */
                }
                else if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported V_Sensitivity <b>${vSensitivityOptions[fncmd.toString()]}</b> (${fncmd})"
                    device.updateSetting("vSensitivity", [type:"enum", value: fncmd.toString()])
                }
                else if (isHumanPresenceSensorFall()) {
                    logDebug "radar reset_flag_code dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                }
                else if (isMotionSwitch()) {    // button 'single': 0, 'hold': 1, 'double': 2
                    def action = fncmd == 2 ? 'held' : fncmd == 1 ? 'doubleTapped' : fncmd == 0 ? 'pushed' : 'unknown'
                    logInfo "button 1 was $action"
                    sendEvent(name: action, value: '1', data: [buttonNumber: 1], descriptionText: "button 1 was pushed", isStateChange: true, type: 'physical')
                }
                else {     //  Tuya 3 in 1 (101) -> motion (ocupancy) + TUYATEC
                    logDebug "motion event 0x65 fncmd = ${fncmd}"
                    handleMotion(motionActive=fncmd)
                }
                break            
            case 0x66 :     // (102)
                if (/*isZY_M100Radar() || */isSBYX0LM6radar() || isYXZBRB58radar()) {                              // TODO !!! check whether the time is in seconds or in tenths of seconds?  https://templates.blakadder.com/ZY-M100.html  // TODO !!!
                    def value = fncmd / 10
                    if (settings?.logEnable == true || (settings?.fadingTime) != safeToDouble(device.currentValue("fadingTime")) ) {logInfo "received Radar fading time : ${value} seconds (${fncmd})"}
                    device.updateSetting("fadingTime", [value:value , type:"decimal"])
                    sendEvent(name : "fadingTime", value : value, unit : "s")
                }                    
                else if (isHL0SS9OAradar()) {
                    logInfo "TS0225 Radar received Alarm Volume ${TS0225alarmVolume.options[fncmd]} (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})"
                    device.updateSetting("radarAlarmVolume", [value:fncmd.toString(), type:"enum"])
                }
                else if (is2AAELWXKradar()) {
                    logDebug "TS0225 Radar Presence Keep Time dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || settings?.presenceKeepTime != (fncmd as int)) { logInfo "received presenceKeepTime : ${fncmd}"} else {logDebug "skipped ${settings?.presenceKeepTime} == ${fncmd as int}"}
                    device.updateSetting("presenceKeepTime", [value:fncmd as int , type:"number"])                    
                }
                else if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported O_Sensitivity <b>${oSensitivityOptions[fncmd.toString()]}</b> (${fncmd})"
                    device.updateSetting("oSensitivity", [type:"enum", value: fncmd.toString()])
                }
                else if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) {                     // trsfMotionState: (102) for TuYa Radar Sensor with fall function
                    logInfo "(0x66) motion state is ${fncmd}"
                    // commented out 05/24/2023 handleMotion(motionActive=fncmd)
                    // TODO - what is motion state ?
                }
                else if (is4in1()) {    // // case 102 //reporting time intervl for 4 in 1 
                    logInfo "4-in-1 reporting time interval is ${fncmd} minutes"
                    device.updateSetting("reportingTime4in1", [value:fncmd as int , type:"number"])
                }
                else if (isMotionSwitch()) {
                    illuminanceEventLux( fncmd )    // 0 = 'dark' 1 = 'bright'
                }
                else if (is3in1()) {     // battery level for 3 in 1;  
                    logDebug "Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    handleTuyaBatteryLevel( fncmd )                    
                }
                else if (is2in1()) {     // https://github.com/Koenkk/zigbee-herdsman-converters/blob/bf32ce2b74689328048b407e56ca936dc7a54a0b/src/devices/tuya.ts#L4568
                    logDebug "Tuya 2in1 illuminance_interval time is ${fncmd} minutes"
                    // TODO !!!
                    //device.updateSetting("reportingTime4in1", [value:fncmd as int , type:"number"])                  
                }            
                else {
                    logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
                }            
            
                break
            case 0x67 :     // (103)
                if (/*isZY_M100Radar() || */isSBYX0LM6radar()) {
                    if (settings?.logEnable) log.info "${device.displayName} Radar DP_103 (Debug CLI) is ${fncmd}"
                }
                else if (isYXZBRB58radar()) {    // [0x67, 'detection_delay', tuya.valueConverter.divideBy10],
                    logInfo "YXZBRB58 radar reported detection_delay dp=${dp} value=${fncmd}"
                    // TODO = update the preference!
                }
                else if (isHL0SS9OAradar()) {
                    logDebug "TS0225 Radar Static Detection Distance dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (safeToInt(settings?.staticDetectionDistance)*100 != fncmd)) {logInfo "received staticDetectionDistance  : ${fncmd/100} m"}
                    device.updateSetting("staticDetectionDistance", [value:fncmd/100, type:"decimal"])
                }
                else if (is2AAELWXKradar()) {
                    if (settings?.logEnable) { logInfo "TS0225 Radar Motion False Detection is ${fncmd?'On':'Off'} (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})" }
                    if (settings?.logEnable || (settings?.motionFalseDetection ? 1:0) != (fncmd as int)) { logInfo "received motionFalseDetection : ${fncmd}"} else {logDebug "skipped ${settings?.motionFalseDetection} == ${fncmd as int}"}
                    device.updateSetting("motionFalseDetection", [value:fncmd as Boolean , type:"bool"])
                }
                else if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported <b>Vacancy Delay</b> ${fncmd} s"
                    device.updateSetting("vacancyDelay", [value:fncmd as int , type:"number"])
                }
                else if (isEGNGMRZHradar()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported unknown parameter dp=${dp} value=${fncmd}"
                }
                else if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) { // trsfIlluminanceLux for TuYa Radar Sensor with fall function
                    logDebug "(103) radar illuminance is ${fncmd}"
                    illuminanceEventLux( fncmd )
                }
                else if (is3in1()) {        //  Tuya 3 in 1 (103) -> tamper
                    def value = fncmd==0 ? 'clear' : 'detected'
                    if (settings?.txtEnable) log.info "${device.displayName} tamper alarm is ${value} (dp=67,fncmd=${fncmd})"
                    sendEvent(name : "tamper",    value : value, isStateChange : true)
                }
                else {
                    logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
                }            
                break            
            case 0x68 :     // (104)
                if (/*isZY_M100Radar() || */isSBYX0LM6radar() || isSXM7L9XAradar() || isIJXVKHD0radar()) {
                    illuminanceEventLux( fncmd )
                }
                else if (isYXZBRB58radar()) {    // [0x68, 'radar_scene', tuya.valueConverterBasic.lookup({ 'default': tuya.enum(0), 'bathroom': tuya.enum(1), 'bedroom': tuya.enum(2), 'sleeping': tuya.enum(3), })],
                    logInfo "YXZBRB58 radar reported radar_scene dp=${dp} value=${fncmd}"
                }
                else if (isHL0SS9OAradar()) {
                    logDebug "TS0225 Radar Static Detection Sensitivity dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || settings?.staticDetectionSensitivity != (fncmd as int)) { logInfo "received staticDetectionSensitivity : ${fncmd}"} else {logDebug "skipped ${settings?.staticDetectionSensitivity} == ${fncmd as int}"}
                    device.updateSetting("staticDetectionSensitivity", [value:fncmd as int , type:"number"])
                }
                else if (is2AAELWXKradar()) {
                    logDebug "TS0225 Radar Small Motion Detection Distance dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (safeToInt(settings?.smallMotionDetectionDistance)*100 != fncmd)) {logInfo "received Small Motion Detection Distance  : ${fncmd/100} m"}
                    device.updateSetting("smallMotionDetectionDistance", [value:fncmd/100, type:"decimal"])
                }
                else if (isEGNGMRZHradar()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported unknown parameter dp=${dp} value=${fncmd}"
                }
                else if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported Detection Mode <b>${detectionModeOptions[fncmd.toString()]}</b> (${fncmd})"
                    device.updateSetting("detectionMode", [type:"enum", value: fncmd.toString()])
                }
                else if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) { // detection data  for TuYa Radar Sensor with scene; detection_flag_code for the fall radar
                    if (settings?.logEnable) log.info "${device.displayName} radar detection data is ${fncmd}"
                }
                else if (is4in1()) {    // case 104: // 0x68 temperature compensation
                    def val = fncmd;
                    // for negative values produce complimentary hex (equivalent to negative values)
                    if (val > 4294967295) val = val - 4294967295;                    
                    logInfo "4-in-1 temperature calibration is ${val / 10.0}"
                }
                else if (is3in1()) {    //  Tuya 3 in 1 (104) -> temperature in �C
                    temperatureEvent( fncmd / 10.0 )
                }
                else {
                    logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
                }
                break            
            case 0x69 :    // (105) 
                if (isYXZBRB58radar() || isSXM7L9XAradar()) {
                    logDebug "radar presence event DP=0x69 (105) fncmd = ${fncmd}"
                    handleMotion(motionActive = fncmd)
                }
                else if (isIJXVKHD0radar()) {    // [105, 'presence_state', tuya.valueConverterBasic.lookup({'none': tuya.enum(0), 'present': tuya.enum(1), 'moving': tuya.enum(2)})],
                    logDebug "IJXVKHD0 radar presence_state DP=0x69 (105) fncmd = ${fncmd}"
                    // TODO - make it attribute !
                }
                else if (isSBYX0LM6radar()) {
                    logDebug "radar entry_sensitivity DP=0x69 (105) fncmd = ${fncmd}"
                    // TODO - make it preference !
                }
                else if (isHL0SS9OAradar()) {    // disarmed armed alarming
                    logInfo "TS0225 Radar received Alarm Mode ${TS0225alarmMode.options[fncmd]} (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})"
                    device.updateSetting("radarAlarmMode", [type:"enum", value:fncmd.toString()])
                }
                else if (is2AAELWXKradar()) {
                    logDebug "TS0225 Radar Small Motion Detection Sensitivity dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || settings?.smallMotionDetectionSensitivity != (fncmd as int)) { logInfo "received smallMotionDetectionSensitivity : ${fncmd}"} else {logDebug "skipped ${settings?.smallMotionDetectionSensitivity} == ${fncmd as int}"}
                    device.updateSetting("smallMotionDetectionSensitivity", [value:fncmd as int , type:"number"])
                }        
                else if (isEGNGMRZHradar()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported unknown parameter dp=${dp} value=${fncmd}"
                }
                else if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported unacknowledgedTime ${fncmd} s"
                    sendEvent(name : "unacknowledgedTime", value : fncmd, unit : "s")
                }
                else if (isBlackPIRsensor()) {
                    logInfo "reported target distance <b>${blackSensorDistanceOptions[fncmd.toString()]}</b> (${fncmd})"
                    device.updateSetting("targetDistance", [type:"enum", value: fncmd.toString()])
                }
                else if (isHumanPresenceSensorFall()) {
                    // trsfTumbleSwitch for TuYa Radar Sensor with fall function
                    logInfo "radar Tumble Switch (dp=69) is ${fncmd}"
                }
                else if (is4in1()) {    // case 105:// 0x69 humidity calibration (compensation)
                    def val = fncmd;
                    if (val > 4294967295) val = val - 4294967295;                    
                    logInfo "4-in-1 humidity calibration is ${val}"                
                }
                else if (is3in1()){    //  Tuya 3 in 1 (105) -> humidity in %
                    humidityEvent(fncmd)
                }
                else {
                    logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
                }
                break
            case 0x6A : // (106)
                if (isYXZBRB58radar() || isSXM7L9XAradar()) {
                    if (settings?.logEnable == true || settings?.radarSensitivity != safeToInt(device.currentValue("radarSensitivity"))) {logInfo "received YXZBRB58/SXM7L9XA Radar sensitivity : ${fncmd}"}
                    device.updateSetting("radarSensitivity", [value:fncmd as int , type:"number"])
                    sendEvent(name : "radarSensitivity", value : fncmd as int)
                }
                else if (isIJXVKHD0radar()) {    // [106, 'motion_sensitivity', tuya.valueConverter.divideBy10],
                    logDebug "IJXVKHD0 radar motion_sensitivity DP=0x6A (106) fncmd = ${fncmd/10.0} (raw=${fncmd})"
                    // TODO - make it preference !
                }
                else if (isSBYX0LM6radar()) {    // https://github.com/Koenkk/zigbee-herdsman-converters/issues/5930#issuecomment-1651270524
                    logDebug "radar entry_distance_indentation DP=0x6A (106) fncmd = ${fncmd}"
                    // TODO - make it preference !
                } 
                else if (isHL0SS9OAradar()) {
                    logDebug "TS0225 Radar Move Minimum Distance dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (safeToInt(settings?.motionMinimumDistance)*100 != fncmd)) {logInfo "received Radar Motion Minimum Distance  : ${fncmd/100} m"}
                    device.updateSetting("motionMinimumDistance", [value:fncmd/100, type:"decimal"])
                }
                else if (is2AAELWXKradar()) {
                    illuminanceEventLux( Math.round(fncmd / 10))    // illuminance for TS0225 radar
                }            
                else if (isHumanPresenceSensorAIR()) {
                    //if (settings?.logEnable) log.info "${device.displayName} reported Reference Luminance ${fncmd}"
                    illuminanceEventLux( fncmd )
                }
                else if (isHumanPresenceSensorFall()) {
                    // trsfTumbleAlarmTime
                    logInfo "radar Tumble Alarm Time (dp=106) is ${fncmd}"
                }
                else if (is4in1()) {    // case 106: // 0x6a lux calibration (compensation)
                    def val = fncmd;
                    if (val > 4294967295) val = val - 4294967295;                    
                    logInfo "4-in-1 lux calibration is ${val}"                
                }
                else if (is3in1()) {    //  Tuya 3 in 1 temperature scale Celsius/Fahrenheit
                    if (settings?.logEnable) log.info "${device.displayName} Temperature Scale is: ${fncmd == 0 ? 'Celsius' : 'Fahrenheit'} (DP=0x6A fncmd = ${fncmd})"  
                }
                else {
                    logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
                }
                break
            case 0x6B : // (107)
                if (isYXZBRB58radar() || isSXM7L9XAradar()) {
                    if (settings?.logEnable == true || (settings?.maximumDistance != safeToDouble(device.currentValue("maximumDistance")))) {logInfo "received YXZBRB58/SXM7L9XA Radar Maximum detection distance : ${fncmd/100} m"}
                    device.updateSetting("maximumDistance", [value:fncmd/100 , type:"decimal"])
                    sendEvent(name : "maximumDistance", value : fncmd/100, unit : "m")
                }
                else if (isIJXVKHD0radar()) {    // [107, 'detection_distance_max', tuya.valueConverter.divideBy100],
                    logDebug "IJXVKHD0 radar detection_distance_max DP=0x6B (107) fncmd = ${fncmd/100.0} meters (raw=${fncmd})"
                    // TODO - make it preference !
                }
                else if (isSBYX0LM6radar()) {
                    logDebug "radar breaker_mode DP=0x6B (107) fncmd = ${fncmd}"    // {'Local': tuya.enum(0), 'Auto': tuya.enum(1)})],//false
                    // TODO - make it preference !
                } 
                else if (isHL0SS9OAradar()) {
                    logDebug "TS0225 Radar Small Motion Minimum Distance dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (safeToInt(settings?.smallMotionMinimumDistance)*100 != fncmd)) {logInfo "received Radar Small Motion Minimum Distance  : ${fncmd/100} m"}
                    device.updateSetting("smallMotionMinimumDistance", [value:fncmd/100, type:"decimal"])
                }
                else if (is2AAELWXKradar()) {
                    if (settings?.logEnable) { logInfo "TS0225 Radar Indicator is ${fncmd?'On':'Off'} (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})" }
                    if (settings?.logEnable || (settings?.ledIndicator ? 1:0) != (fncmd as int)) { logInfo "received ledIndicator : ${fncmd}"} else {logDebug "skipped ${settings?.ledIndicator} == ${fncmd as int}"}
                    device.updateSetting("ledIndicator", [value:fncmd as Boolean , type:"bool"])
                }            
                else if (isHumanPresenceSensorFall()) {
                    if (settings?.logEnable) log.info "${device.displayName} radar_check_end_code (dp=107) is ${fncmd}"
                }
                else if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported Light On Luminance Preference ${fncmd} Lux"
                }
                else if (is4in1()) {    //  Tuya 4 in 1 (107) -> temperature in �C
                    temperatureEvent( fncmd / 10.0 )
                }
                else if (is3in1()) { // 3in1
                    logDebug "Min Temp is: ${fncmd} (DP=0x6B)"  
                }
                else {
                    logDebug "(UNEXPECTED) : ${fncmd} (DP=0x6B)"  
                }
                break            
            case 0x6C : //  (108) Tuya 4 in 1 -> humidity in %
                if (isYXZBRB58radar() || isSXM7L9XAradar()) {
                    if (settings?.logEnable == true || (settings?.minimumDistance != safeToDouble(device.currentValue("minimumDistance")))) {logInfo "received YXZBRB58/SXM7L9XA Radar Minimum detection distance : ${fncmd/100} m"}
                    device.updateSetting("minimumDistance", [value:fncmd/100, type:"decimal"])
                    sendEvent(name : "minimumDistance", value : fncmd/100, unit : "m")
                }
                else if (isSBYX0LM6radar()) {
                    logDebug "radar breaker_status DP=0x6C (108) fncmd = ${fncmd}"    //onOff
                    // TODO - make it preference !
                } 
                else if (isHL0SS9OAradar()) {
                    logDebug "TS0225 Radar Static Detection Minimum Distance dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (safeToInt(settings?.staticDetectionMinimumDistance)*100 != fncmd)) {logInfo "received Radar Static Detection Minimum Distance  : ${fncmd/100} m"}
                    device.updateSetting("staticDetectionMinimumDistance", [value:fncmd/100, type:"decimal"])
                }
                else if (is2AAELWXKradar()) {
                    logDebug "TS0225 Radar Static Detection Distance dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (safeToInt(settings?.staticDetectionDistance)*100 != fncmd)) {logInfo "received staticDetectionDistance  : ${fncmd/100} m"}
                    device.updateSetting("staticDetectionDistance", [value:fncmd/100, type:"decimal"])
                }
                else if (isHumanPresenceSensorFall()) {
                    if (settings?.logEnable) log.info "${device.displayName} radar_check_start_code (dp=108) is ${fncmd}"
                }
                else if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported Light Off Luminance Preference ${fncmd} Lux"
                }
                else if (is4in1()) {
                    humidityEvent (fncmd)
                }
                else if (is3in1()) { // 3in1
                    logDebug "(3in1) Max Temp is: ${fncmd} (DP=0x6C)"  
                }
                else {
                    logDebug "(UNEXPECTED) : ${fncmd} (DP=0x6C)"  
                }
                break
            case 0x6D :    // (109)
                if (isYXZBRB58radar() || isSXM7L9XAradar() || isIJXVKHD0radar()) {
                    if (settings?.ignoreDistance == false) {
                        logDebug "YXZBRB58/SXM7L9XA radar target distance is ${fncmd/100} m"
                        sendEvent(name : "distance", value : fncmd/100, unit : "m")
                    }
                }
                else if (isSBYX0LM6radar()) {
                    logDebug "radar status_indication DP=0x6D (109) fncmd = ${fncmd}"
                }
                else if (isHL0SS9OAradar()) {
                    logDebug "TS0225 Radar Time dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    sendEvent(name : "checkingTime", value : fncmd)   
                }
                else if (is2AAELWXKradar()) {
                    logDebug "TS0225 Radar Static Detection Sensitivity dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || settings?.staticDetectionSensitivity != (fncmd as int)) { logInfo "received staticDetectionSensitivity : ${fncmd}"} else {logDebug "skipped ${settings?.staticDetectionSensitivity} == ${fncmd as int}"}
                    device.updateSetting("staticDetectionSensitivity", [value:fncmd as int , type:"number"])
                }
                else if (isEGNGMRZHradar()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported unknown parameter dp=${dp} value=${fncmd}"
                }
                else if (isHumanPresenceSensorFall()) {
                    if (settings?.logEnable) log.info "${device.displayName} radar hw_version_code (dp=109) is ${fncmd}"
                }
                else if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported Luminance Level ${fncmd}" // Ligter, Medium, ... ?
                }
                else if (is4in1()) {   // case 109: 0x6d PIR enable (PIR power)
                    logInfo "4-in-1 enable is ${fncmd}"                
                }
                else { // 3in1
                    if (settings?.logEnable) log.info "${device.displayName} Min Humidity is: ${fncmd} (DP=0x6D)"  
                }
                break
            case 0x6E : // (110) Tuya 4 in 1
                if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported Led Status <b>${ledStatusOptions[fncmd.toString()]}</b> (${fncmd})"
                    device.updateSetting("ledStatusAIR", [type:"enum", value: fncmd.toString()])
                }
                else if (isIJXVKHD0radar()) {    // [110, 'fading_time', tuya.valueConverter.raw],
                    logDebug "IJXVKHD0 radar fading time DP=0x6E (110) fncmd = ${fncmd} seconds (raw=${fncmd})"
                    // TODO - make it preference ! 
                }
                else if (isHL0SS9OAradar()) {
                    logDebug "TS0225 Radar Small Move Self-Test dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (TS0225SelfCheckingStatus[fncmd.toString()] != device.currentValue("radarStatus"))) {logInfo "Radar self checking status : ${TS0225SelfCheckingStatus[fncmd.toString()]} (${fncmd})"}
                    sendEvent(name : "radarStatus", value : TS0225SelfCheckingStatus[fncmd.toString()])                    
                }
                else if (is2AAELWXKradar()) {
                    logDebug "TS0225 Radar Small Motion Minimum Distance dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (safeToInt(settings?.smallMotionMinimumDistance)*100 != fncmd)) {logInfo "received Radar Small Motion Minimum Distance  : ${fncmd/100} m"}
                    device.updateSetting("smallMotionMinimumDistance", [value:fncmd/100, type:"decimal"])
                }
                else if (isSBYX0LM6radar()) {
                    logDebug "radar status_indication DP=0x6E (110) fncmd = ${fncmd}"
                    // TODO - make it preference !
                } 
                else if (isYXZBRB58radar() || isSXM7L9XAradar()) {
                    def value = fncmd / 10
                    if (settings?.logEnable == true || (settings?.fadingTime) != safeToDouble(device.currentValue("fadingTime")) ) {logInfo "received YXZBRB58/SXM7L9XA radar fading time : ${value} seconds (${fncmd})"}
                    device.updateSetting("fadingTime", [value:value , type:"decimal"])
                    sendEvent(name : "fadingTime", value : value, unit : "s")
                }/*
                else if (isZY_M100Radar()){
                    if (settings?.txtEnable) log.info "${device.displayName} radar LED status is ${fncmd}"                
                }*/
                else if (isEGNGMRZHradar()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported unknown parameter dp=${dp} value=${fncmd}"
                }
                else if (isHumanPresenceSensorFall()) {
                    if (settings?.logEnable) log.info "${device.displayName} radar sw_version_code (dp=110) is ${fncmd}"
                }
                else if (is4in1()) {
                    logDebug "Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    handleTuyaBatteryLevel( fncmd )
                }
                else {  //  3in1
                    if (settings?.logEnable) log.info "${device.displayName} Max Humidity is: ${fncmd} (DP=0x6E)"  
                }
                break 
            case 0x6F : // (111) Tuya 4 in 1: // 0x6f led enable
                if (isYXZBRB58radar() || isSXM7L9XAradar()) {
                    def value = fncmd / 10
                    if (settings?.logEnable == true || (settings?.detectionDelay) != safeToDouble(device.currentValue("detectionDelay")) ) {logInfo "received YXZBRB58/SXM7L9XA radar detection delay : ${value} seconds (${fncmd})"}
                    device.updateSetting("detectionDelay", [value:value , type:"decimal"])
                    sendEvent(name : "detectionDelay", value : value)
                }
                else if (isIJXVKHD0radar()) {    // [111, 'presence_sensitivity', tuya.valueConverter.divideBy10],
                    logDebug "IJXVKHD0 radar presence_sensitivity DP=0x6F (111) fncmd = ${fncmd/10.0} (raw=${fncmd})"
                    // TODO - make it preference !
                }
                else if (isHL0SS9OAradar()) {
                    logDebug "TS0225 Radar Breathe Self-Test : ${}(dp_id=${dp_id} dp=${dp} fncmd=${fncmd})"
                    if (settings?.logEnable == true || (TS0225SelfCheckingStatus[fncmd.toString()] != device.currentValue("radarStatus"))) {logInfo "Radar self checking status : ${TS0225SelfCheckingStatus[fncmd.toString()]} (${fncmd})"}
                    sendEvent(name : "radarStatus", value : TS0225SelfCheckingStatus[fncmd.toString()])                    
                }
                else if (is2AAELWXKradar()) {
                    logDebug "TS0225 Radar Static Detection Minimum Distance dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (safeToInt(settings?.staticDetectionMinimumDistance)*100 != fncmd)) {logInfo "received Radar Static Detection Minimum Distance  : ${fncmd/100} m"}
                    device.updateSetting("staticDetectionMinimumDistance", [value:fncmd/100, type:"decimal"])
                }
                else if (isSBYX0LM6radar()) {
                    logDebug "radar breaker_polarity DP=0x6F (111) fncmd = ${fncmd}"
                    // TODO - make it preference !
                } 
                else if (isEGNGMRZHradar()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported unknown parameter dp=${dp} value=${fncmd}"
                }
                else if (isHumanPresenceSensorFall()) {
                    if (settings?.logEnable) log.info "${device.displayName} radar radar_id_code (dp=111) is ${fncmd}"
                }
                else if (is4in1()) { 
                    logInfo "4-in-1 LED is: ${fncmd == 1 ? 'enabled' :'disabled'}"
                    device.updateSetting("ledEnable", [value:fncmd as boolean, type:"boolean"])
                }
                else { // 3in1 - temperature alarm switch
                    if (settings?.logEnable) log.info "${device.displayName} Temperature alarm switch is: ${fncmd} (DP=0x6F)"  
                }
                break
            case 0x70 : // (112)
                if (is4in1()) {   // case 112: 0x70 reporting enable (Alarm type)
                    if (settings?.txtEnable) log.info "${device.displayName} reporting enable is ${fncmd}"                
                }
                else if (isSBYX0LM6radar()) {
                    logDebug "radar block_time DP=0x70 (112) fncmd = ${fncmd}"
                    // TODO - make it preference !
                } 
                else if (isIJXVKHD0radar()) {    // [112, 'presence', tuya.valueConverter.trueFalseEnum1],
                    logDebug "IJXVKHD0 radar presence DP=0x70 (112) fncmd = ${fncmd}"
                    handleMotion(motionActive = fncmd)
                }
                else if (isHL0SS9OAradar()) {
                    if (settings?.logEnable) { logInfo "TS0225 Radar Motion False Detection is ${fncmd?'On':'Off'} (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})" }
                    if (settings?.logEnable || (settings?.motionFalseDetection ? 1:0) != (fncmd as int)) { logInfo "received motionFalseDetection : ${fncmd}"} else {logDebug "skipped ${settings?.motionFalseDetection} == ${fncmd as int}"}
                    device.updateSetting("motionFalseDetection", [value:fncmd as Boolean , type:"bool"])
                }
                else if (is2AAELWXKradar()) {
                    logInfo "TS0225 Radar Reset Settings dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                }
                else if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) {    // trsfScene, not used in fall radar?
                    logInfo "radar Scene (dp=70) is ${fncmd}"
                }
                else {
                    if (settings?.logEnable) log.info "${device.displayName} Humidity alarm switch is: ${fncmd} (DP=0x6F)"  
                }
                break
            case 0x71 : // (113)
                if (is4in1()) {   // case 113: 0x71 unknown  ( ENUM)
                    if (settings?.logEnable) log.info "${device.displayName} <b>UNKNOWN</b> (0x71 reporting enable?) DP=0x71 fncmd = ${fncmd}"  
                }
                else if (isSBYX0LM6radar()) {
                    logDebug "radar parameter_setting_result DP=0x71 (113) fncmd = ${fncmd}"
                    // TODO - make it preference !
                } 
                else if (isHL0SS9OAradar()) {
                    logInfo "TS0225 Radar Reset Settings dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                }
                else if (is2AAELWXKradar()) {
                    if (settings?.logEnable) { logInfo "TS0225 Radar Breathe False Detection is ${fncmd?'On':'Off'} (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})" }
                    if (settings?.logEnable || (settings?.breatheFalseDetection ? 1:0) != (fncmd as int)) { logInfo "received breatheFalseDetection : ${fncmd}"} else {logDebug "skipped ${settings?.breatheFalseDetection} == ${fncmd as int}"}
                    device.updateSetting("breatheFalseDetection", [value:fncmd as Boolean , type:"bool"])
                }
                else {    // 3in1 - Alarm Type
                    if (settings?.txtEnable) log.info "${device.displayName} Alarm type is: ${fncmd}"                
                }
                break
            case 0x72 : // (114)
                if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) {    // trsfMotionDirection
                    logInfo "radar motion direction is ${fncmd}"                
                }
                else if (isSBYX0LM6radar()) {
                    logDebug "radar factory_parameters DP=0x72 (114) fncmd = ${fncmd}"
                    // TODO - make it preference !
                } 
                else if (isHL0SS9OAradar()) {
                    logDebug "TS0225 Radar Move Self-Test dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (TS0225SelfCheckingStatus[fncmd.toString()] != device.currentValue("radarStatus"))) {logInfo "Radar self checking status : ${TS0225SelfCheckingStatus[fncmd.toString()]} (${fncmd})"}
                    sendEvent(name : "radarStatus", value : TS0225SelfCheckingStatus[fncmd.toString()])                    
                }
                else if (is2AAELWXKradar()) {
                    logDebug "TS0225 Radar Time dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    sendEvent(name : "checkingTime", value : fncmd)   
                }
                else if (isEGNGMRZHradar()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported unknown parameter dp=${dp} value=${fncmd}"
                }
                else {
                    if (settings?.txtEnable) log.warn "${device.displayName} non-radar motion direction 0x72 fncmd = ${fncmd}"
                }
                break
            case 0x73 : // (115)
                if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) {    // trsfMotionSpeed
                    if (settings?.logEnable) log.info "${device.displayName} radar motion speed is ${fncmd}"                    // sent VERY frequently?
                }
                else if (isSBYX0LM6radar()) {
                    logDebug "radar sensor DP=0x73 (115) fncmd = ${fncmd}"    // onOff
                    // TODO - make it preference !
                } 
                else if (isHL0SS9OAradar()) {
                    if (settings?.logEnable) { logInfo "TS0225 Radar Breathe False Detection is ${fncmd?'On':'Off'} (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})" }
                    if (settings?.logEnable || (settings?.breatheFalseDetection ? 1:0) != (fncmd as int)) { logInfo "received breatheFalseDetection : ${fncmd}"} else {logDebug "skipped ${settings?.breatheFalseDetection} == ${fncmd as int}"}
                    device.updateSetting("breatheFalseDetection", [value:fncmd as Boolean , type:"bool"])
                }
                else if (is2AAELWXKradar()) {
                    logInfo "TS0225 Radar Alarm Time is ${fncmd} seconds (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})"
                    if (settings?.logEnable || (settings?.radarAlarmTime ?: 1) != (fncmd as int)) { logInfo "received radar Alarm Time : ${fncmd}"} else {logDebug "skipped ${settings?.radarAlarmTime} == ${fncmd as int}"}
                    device.updateSetting("radarAlarmTime", [value:fncmd as int , type:"number"])
                }            
                else if (isEGNGMRZHradar()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported unknown parameter dp=${dp} value=${fncmd}"
                }
                else {
                    logDebug "non-radar motion speed 0x73 fncmd = ${fncmd}"
                }
                break
            case 0x74 : // (116)
                if (isHumanPresenceSensorFall()) {    // trsfFallDownStatus
                    logInfo "radar fall down status is ${fncmd}"                
                }
                else if (isHL0SS9OAradar()) {
                    logDebug "TS0225 Radar Presence Duration Time dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                }
                else if (is2AAELWXKradar()) {
                    logInfo "TS0225 Radar received Alarm Volume ${TS0225alarmVolume.options[fncmd]} (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})"
                    device.updateSetting("radarAlarmVolume", [value:fncmd.toString(), type:"enum"])
                }
                else if (isEGNGMRZHradar()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported unknown parameter dp=${dp} value=${fncmd}"
                }
                else {
                    logDebug "non-radar fall down status 0x74 fncmd = ${fncmd}"
                }
                break
            case 0x75 : // (117)
                if (isHumanPresenceSensorFall()) {    // trsfStaticDwellAlarm
                    logInfo "radar static dwell alarm is ${fncmd}"                
                }
                else if (isHL0SS9OAradar()) {
                    logDebug "TS0225 Radar None Duration Time dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                }
                else if (is2AAELWXKradar()) {    // disarmed armed alarming
                    logInfo "TS0225 Radar received Alarm Mode ${TS0225alarmMode.options[fncmd]} (dp_id=${dp_id} dp=${dp} fncmd=${fncmd})"
                    device.updateSetting("radarAlarmMode", [type:"enum", value:fncmd.toString()])
                }
                else {
                    logDebug "${device.displayName} non-radar static dwell alarm 0x75 fncmd = ${fncmd}"
                }
                break
            case 0x76 : // (118)
                if (isHumanPresenceSensorFall()) {
                    logInfo "radar fall sensitivity is ${fncmd}"
                }
                else if (isHL0SS9OAradar()) {
                    logDebug "TS0225 Radar Duration Status dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                }
                else if (is2AAELWXKradar()) {    // not sure if this DP is for the 'small move self-test'
                    logDebug "TS0225 Radar Small Move Self-Test dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (TS0225SelfCheckingStatus[fncmd.toString()] != device.currentValue("radarStatus"))) {logInfo "Radar self checking status : ${TS0225SelfCheckingStatus[fncmd.toString()]} (${fncmd})"}
                    sendEvent(name : "radarStatus", value : TS0225SelfCheckingStatus[fncmd.toString()])                    
                }
                else if (isEGNGMRZHradar()) {
                    logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
                }
                else {
                    logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
                }
                break
            case 0x77 : // (119)
                if (isEGNGMRZHradar()) {
                    logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
                }
                else if (is2AAELWXKradar()) {    // not sure this is the DP
                    logDebug "TS0225 Radar Breathe Self-Test : ${}(dp_id=${dp_id} dp=${dp} fncmd=${fncmd})"
                    if (settings?.logEnable == true || (TS0225SelfCheckingStatus[fncmd.toString()] != device.currentValue("radarStatus"))) {logInfo "Radar self checking status : ${TS0225SelfCheckingStatus[fncmd.toString()]} (${fncmd})"}
                    sendEvent(name : "radarStatus", value : TS0225SelfCheckingStatus[fncmd.toString()])                    
                }
                else {
                    //if (isBlackPIRsensor()) {
                        if (settings?.logEnable) logInfo "(0x77) motion state is ${fncmd}"
                        handleMotion(motionActive=fncmd)
                    //}
                }
                break
            case 0x78 : // (120)
                if (is2AAELWXKradar()) {    // not sure this is the DP
                    logDebug "TS0225 Radar Move Self-Test dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (settings?.logEnable == true || (TS0225SelfCheckingStatus[fncmd.toString()] != device.currentValue("radarStatus"))) {logInfo "Radar self checking status : ${TS0225SelfCheckingStatus[fncmd.toString()]} (${fncmd})"}
                    sendEvent(name : "radarStatus", value : TS0225SelfCheckingStatus[fncmd.toString()])                    
                }
                else {
                    logDebug "reported unknown parameter dp=${dp} value=${fncmd}"
                }
                break
            case 0x8D : // (141)
                //if (isBlackPIRsensor()) {
                    def strMotionType = blackSensorMotionTypeOptions[fncmd.toString()]
                    if (strMotionType == null) strMotionType = "???"
                    ilogDebug "motion type reported is ${strMotionType} (${fncmd})"
                    sendEvent(name : "motionType", value : strMotionType, type: "physical")
                //}
                break
            default :
                logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                break
        }
}


private int getTuyaAttributeValue(ArrayList _data) {
    int retValue = 0
    
    if (_data.size() >= 6) {
        int dataLength = _data[5] as Integer
        int power = 1;
        for (i in dataLength..1) {
            retValue = retValue + power * zigbee.convertHexToInt(_data[i+5])
            power = power * 256
        }
    }
    return retValue
}


def handleTuyaBatteryLevel( fncmd ) {
    def rawValue = 0
    if (fncmd == 0) rawValue = 100           // Battery Full
    else if (fncmd == 1) rawValue = 75       // Battery High
    else if (fncmd == 2) rawValue = 50       // Battery Medium
    else if (fncmd == 3) rawValue = 25       // Battery Low
    else if (fncmd == 4) rawValue = 100      // Tuya 3 in 1 -> USB powered
    else rawValue = fncmd
    getBatteryPercentageResult(rawValue*2)
}

// not used
def parseIasReport(Map descMap) {
    logDebug "pareseIasReport: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}"
    def zs = new ZoneStatus(Integer.parseInt(descMap?.value, 16))
    if (settings?.logEnable) {
        log.debug "zs.alarm1 = $zs.alarm1"
        log.debug  "zs.alarm2 = $zs.alarm2"
        log.debug  "zs.tamper = $zs.tamper"
        log.debug  "zs.battery = $zs.battery"
        log.debug  "zs.supervisionReports = $zs.supervisionReports"
        log.debug  "zs.restoreReports = $zs.restoreReports"
        log.debug  "zs.trouble = $zs.trouble"
        log.debug  "zs.ac = $zs.ac"
        log.debug  "zs.test = $zs.test"
        log.debug  "zs.batteryDefect = $zs.batteryDefect"
    }    
    handleMotion(zs.alarm1)  
}
       

def parseIasMessage(String description) {
    // https://developer.tuya.com/en/docs/iot-device-dev/tuya-zigbee-water-sensor-access-standard?id=K9ik6zvon7orn 
    try {
        Map zs = zigbee.parseZoneStatusChange(description)
        if (zs.alarm1Set == true) {
            handleMotion(motionActive=true)
        }
        else {
            handleMotion(motionActive=false)
        }
    }
    catch (e) {
        log.error "${device.displayName} This driver requires HE version 2.2.7 (May 2021) or newer!"
        return null
    }
}

private handleMotion( motionActive, isDigital=false ) {
    if (settings.invertMotion == true) {
        motionActive = ! motionActive
    }
    if (motionActive) {
        def timeout = motionResetTimer ?: 0
        // If the sensor only sends a motion detected message, the reset to motion inactive must be  performed in code
        if (settings.motionReset == true && timeout != 0) {
            runIn(timeout, resetToMotionInactive, [overwrite: true])
        }
        if (device.currentState('motion')?.value != "active") {
            state.motionStarted = unix2formattedDate(now().toString())
        }
    }
    else {
        if (device.currentState('motion')?.value == "inactive") {
            logDebug "ignored motion inactive event after ${getSecondsInactive()}s"
            return [:]   // do not process a second motion inactive event!
        }
    }
    sendMotionEvent(motionActive, isDigital)
}

def sendMotionEvent( motionActive, isDigital=false ) {
    def descriptionText = "Detected motion"
    if (!motionActive) {
        descriptionText = "Motion reset to inactive after ${getSecondsInactive()}s"
    }
    else {
        descriptionText = device.currentValue("motion") == "active" ? "Motion is active ${getSecondsInactive()}s" : "Detected motion"
    }
    /*
    if (isBlackSquareRadar() && device.currentValue("motion", true) == "active" && (motionActive as boolean) == true) {    // TODO - obsolete
        return    // the black square radar sends 'motion active' every 4 seconds!
    }
    else {
        //log.trace "device.currentValue('motion', true) = ${device.currentValue('motion', true)} motionActive = ${motionActive}"
    }
    */
    if (txtEnable) log.info "${device.displayName} ${descriptionText}"
    sendEvent (
            name            : 'motion',
            value            : motionActive ? 'active' : 'inactive',
            //isStateChange   : true,
            type            : isDigital == true ? "digital" : "physical",
            descriptionText : descriptionText
    )
    runIn( 1, formatAttrib, [overwrite: true])    
}

def resetToMotionInactive() {
    if (device.currentState('motion')?.value == "active") {
        def descText = "Motion reset to inactive after ${getSecondsInactive()}s (software timeout)"
        sendEvent(
            name : "motion",
            value : "inactive",
            isStateChange : true,
            type:  "digital",
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
    if (unixTime) {
        return Math.round((now() - unixTime)/1000)
    } else {
        return motionResetTimer ?: 0
    }
}



def temperatureEvent( temperature ) {
    def map = [:] 
    map.name = "temperature"
    map.unit = "\u00B0"+"${location.temperatureScale}"
    String tempConverted = convertTemperatureIfNeeded(temperature, "C", precision=1)
    map.value = tempConverted
    map.type = "physical"
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    map.isStateChange = true
    logInfo "${map.descriptionText}"
    sendEvent(map)
    runIn( 1, formatAttrib, [overwrite: true])    
}

def humidityEvent( humidity ) {
    def map = [:] 
    map.name = "humidity"
    map.value = humidity as int
    map.unit = "% RH"
    map.type = "physical"
    map.isStateChange = true
    map.descriptionText = "${map.name} is ${Math.round((humidity) * 10) / 10} ${map.unit}"
    logInfo "${map.descriptionText}"
    sendEvent(map)
    runIn( 1, formatAttrib, [overwrite: true])    
}

def illuminanceEvent( rawLux ) {
    def lux = rawLux > 0 ? Math.round(Math.pow(10,(rawLux/10000))) : 0
    illuminanceEventLux( lux as Integer) 
}

def illuminanceEventLux( lux ) {
    Integer illumCorrected = Math.round((lux * ((settings?.illuminanceCoeff ?: 1.00) as float)))
    Integer delta = Math.abs(safeToInt(device.currentValue("illuminance")) - (illumCorrected as int))
    if (device.currentValue("illuminance", true) == null || (delta >= safeToInt(settings?.luxThreshold))) {
        sendEvent("name": "illuminance", "value": illumCorrected, "unit": "lx", "type": "physical", "descriptionText": "Illuminance is ${lux} Lux")
        logInfo "Illuminance is ${illumCorrected} Lux"
    }
    else {
        logDebug "ignored illuminance event ${illumCorrected} lx : the change of ${delta} lx is less than the ${safeToInt(settings?.luxThreshold)} lux threshold!"
    }
    runIn( 1, formatAttrib, [overwrite: true])    
}

def existanceTimeEvent( Integer time ) {
    if (device.currentValue("existance_time") == null ||  device.currentValue("existance_time") != time) {
        sendEvent("name": "existance_time", "value": time, "unit": "minutes", "type": "physical", "descriptionText": "Presence is active for ${time} minutes")
        logInfo "existance time is ${time} minutes"
    }
}

def leaveTimeEvent( Integer time ) {
    if (device.currentValue("leave_time") == null ||  device.currentValue("leave_time") != time) {
        sendEvent("name": "leave_time", "value": time, "unit": "minutes", "type": "physical", "descriptionText": "Presence is inactive for ${time} minutes")
        logInfo "leave time is ${time} minutes"
    }
}

def powerSourceEvent( state = null) {
    String ps = null
    if (state != null && state == 'unknown' ) {
        ps = "unknown"
    }
    else if (state != null ) {
        ps = state
    }
    else {
        if (DEVICE.device?.powerSource !=null) {
            ps = DEVICE.device?.powerSource 
        }
        else if (!("Battery" in DEVICE.capabilities)) {
            ps = "dc"
        }
        else {
            ps = "unknown"
        }
    }
    sendEvent(name : "powerSource",    value : ps, descriptionText: "device is back online", type: "digital")
}

// called on initial install of device during discovery
// also called from initialize() in this driver!
def installed() {
    log.info "${device.displayName} installed()..."
    initialize( fullInit = true )
    //unschedule()
}

// called when preferences are saved
def updated() {
    log.info "${device.displayName} updated()..."
    checkDriverVersion()
    updateTuyaVersion()
    ArrayList<String> cmds = []
    
    logInfo "Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} <b>deviceProfile=${state.deviceProfile}</b>"
    logInfo "Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(86400, logsOff, [overwrite: true])    // turn off debug logging after 24 hours
        logInfo "Debug logging will be turned off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }
    if (settings.allStatusTextEnable == false) {
        device.deleteCurrentState("all")
    }
    else {
        formatAttrib()
    }
    
    if (settings?.forcedProfile != null) {
        logDebug "state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${getProfileKey(settings?.forcedProfile)}"
        if (getProfileKey(settings?.forcedProfile) != state.deviceProfile) {
            logWarn "changing the device profile from ${state.deviceProfile} to ${getProfileKey(settings?.forcedProfile)}"
            state.deviceProfile = getProfileKey(settings?.forcedProfile)
            initializeVars( fullInit = true ) 
            log.warn "all preference settings are reset to defaults!"
            logInfo "press F5 to refresh the page"
        }
    }
    else {
        logDebug "forcedProfile is not set"
    }
    
    //    LED enable
    if (is4in1()) {
        logDebug "4-in-1: changing ledEnable to : ${settings?.ledEnable }"                
        cmds += sendTuyaCommand("6F", DP_TYPE_BOOL, settings?.ledEnable == true ? "01" : "00")
        logDebug "4-in-1: changing reportingTime4in1 to : ${settings?.reportingTime4in1} minutes"                
        cmds += sendTuyaCommand("66", DP_TYPE_VALUE, zigbee.convertToHexString(settings?.reportingTime4in1 as int, 8))
    }
    // sensitivity
    if (isZY_M100Radar() || isSBYX0LM6radar() || isYXZBRB58radar() || isSXM7L9XAradar() || isHumanPresenceSensorFall() || isHumanPresenceSensorScene()) { 
        cmds += setRadarSensitivity( settings?.radarSensitivity )
    }
    else {
        // settings?.sensitivity was changed in version 1.3.0
        def sensitivityNew 
        try {
            sensitivityNew = settings?.sensitivity as Integer
        }
        catch (e) {
            logWarn "sensitivity was reset to the default value!"
            sensitivityNew = sensitivityOpts.defaultValue
        }
        if (isTS0601_PIR()) {
            def val = sensitivityNew
            cmds += sendTuyaCommand("09", DP_TYPE_ENUM, zigbee.convertToHexString(val as int, 2))
            if (settings?.logEnable) { log.warn "${device.displayName} changing TS0601 sensitivity to : ${val}" }
        }
        else if (isIAS()) {
            def val = sensitivityNew
            if (val != null) {
                logDebug "changing IAS sensitivity to : ${sensitivityOpts.options[val]} (${val})"
                cmds += sendSensitivityIAS(val)
            }
       }
    }
    // keep time
    if (/*isZY_M100Radar() || */isSBYX0LM6radar() || isYXZBRB58radar() || isSXM7L9XAradar()) {
        // do nothing
    }
    else if (isTS0601_PIR()) {
        def val = settings?.keepTime as int
        cmds += sendTuyaCommand("0A", DP_TYPE_ENUM, zigbee.convertToHexString(val as int, 2))    // was 8
        if (settings?.logEnable) { log.warn "${device.displayName} changing TS0601 Keep Time to : ${val}" }           
    }
    else if (isIAS()) {
       if (settings?.keepTime != null) {
           cmds += sendKeepTimeIAS( settings?.keepTime )
           logDebug "changing IAS Keep Time to : ${keepTime4in1Opts.options[settings?.keepTime as int]} (${settings?.keepTime})"                
       }
    }
    // 
    if (isLINPTECHradar()) {
        // TODO - itterate through all settings
                cmds += setFadingTime(settings?.fadingTime ?: 10)
                cmds += setMotionDetectionDistance( settings?.motionDetectionDistance )
                cmds += setMotionDetectionSensitivity( settings?.motionDetectionSensitivity )
                cmds += setStaticDetectionSensitivity( settings?.staticDetectionSensitivity )
    }
    else if (isZY_M100Radar() || isSBYX0LM6radar() || isYXZBRB58radar() || isSXM7L9XAradar()) { 
                cmds += setDetectionDelay( settings?.detectionDelay )        // radar detection delay
                cmds += setFadingTime( settings?.fadingTime ?: 30)           // radar fading time
                cmds += setMinimumDistance( settings?.minimumDistance )      // radar minimum distance
                cmds += setMaximumDistance( settings?.maximumDistance )      // radar maximum distance
    }
        
    if (false) {            // TODO - not ready yet!
            cmds += setPreferencesFromDeviceProfile()
    }
    else if (isHL0SS9OAradar() || is2AAELWXKradar()) {
            cmds += setFadingTime( settings?.presenceKeepTime)               // TS0225 radar presenceKeepTime (in seconds)
            cmds += setRadarLedIndicator( settings?.ledIndicator )
            cmds += setRadarAlarmMode( settings?.radarAlarmMode )
            cmds += setRadarAlarmVolume( settings?.radarAlarmVolume )
            cmds += setRadarAlarmTime( settings?.radarAlarmTime )
            cmds += setMotionFalseDetection( settings?.motionFalseDetection )
            cmds += setBreatheFalseDetection( settings?.breatheFalseDetection )
            cmds += setMotionDetectionDistance( settings?.motionDetectionDistance )
            cmds += setMotionMinimumDistance( settings?.motionMinimumDistance )
            cmds += setMotionDetectionSensitivity( settings?.motionDetectionSensitivity )
            cmds += setSmallMotionDetectionDistance( settings?.smallMotionDetectionDistance )
            cmds += setSmallMotionMinimumDistance( settings?.smallMotionMinimumDistance )
            cmds += setSmallMotionDetectionSensitivity( settings?.smallMotionDetectionSensitivity )
            cmds += setStaticDetectionDistance( settings?.staticDetectionDistance )
            cmds += setStaticDetectionSensitivity( settings?.staticDetectionSensitivity )
            cmds += setStaticDetectionMinimumDistance( settings?.staticDetectionMinimumDistance )
    }
    //
    if (isZY_M100Radar() || isSBYX0LM6radar() || isYXZBRB58radar() || isSXM7L9XAradar() || "DistanceMeasurement" in DEVICE.capabilities /*isLINPTECHradar()*/) {
        if (settings?.ignoreDistance == true ) {
                device.deleteCurrentState('distance')
        }
    }
    //
    if (isHumanPresenceSensorAIR()) {
            if (vacancyDelay != null) {
                def val = settings?.vacancyDelay
                cmds += sendTuyaCommand("67", DP_TYPE_VALUE, zigbee.convertToHexString(val as int, 8))
                logDebug "setting Sensor AIR vacancy delay : ${val}"                
            }
            if (ledStatusAIR != null && ledStatusAIR != "99") {
                def value = safeToInt(ledStatusAIR.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2)
                cmds += sendTuyaCommand("6E", DP_TYPE_ENUM, dpValHex)
                logDebug "setting Sensor AIR LED status : ${ledStatusOptions[value.toString()]} (${value})"                
            }
            if (detectionMode != null && detectionMode != "99") {
                def value = safeToInt(detectionMode.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2)
                cmds += sendTuyaCommand("68", DP_TYPE_ENUM, dpValHex)
                logDebug "setting Sensor AIR detection mode : ${detectionModeOptions[value.toString()]} (${value})"                
            }
            if (vSensitivity != null) {
                def value = safeToInt(vSensitivity.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2)
                cmds += sendTuyaCommand("65", DP_TYPE_ENUM, dpValHex)
                logDebug "setting Sensor AIR v-sensitivity : ${vSensitivityOptions[value.toString()]} (${value})"                
            }
            if (oSensitivity != null) {
                def value = safeToInt(oSensitivity.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2)
                cmds += sendTuyaCommand("66", DP_TYPE_ENUM, dpValHex)
                logDebug "setting Sensor AIR o-sensitivity : ${oSensitivityOptions[value.toString()]} (${value})"                
            }
    }
    if (isBlackPIRsensor()) {
            if (inductionTime != null) {
                def val = settings?.inductionTime
                cmds += sendTuyaCommand("66", DP_TYPE_VALUE, zigbee.convertToHexString(val as int, 8))
                logDebug "setting induction time to : ${val}"                
            }
            if (targetDistance != null) {
                def value = safeToInt(targetDistance.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2)
                cmds += sendTuyaCommand("69", DP_TYPE_ENUM, dpValHex)
                logDebug "setting target distance to : ${blackSensorDistanceOptions[value.toString()]} (${value})"                
            }
    }
    if (("indicatorLight" in DEVICE.preferences)) {    // BlackSquareRadar
            if (indicatorLight != null) {
                def value = safeToInt(indicatorLight.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2) 
                cmds += sendTuyaCommand("67", DP_TYPE_BOOL, dpValHex)
                logDebug "setting indicator light to : ${blackRadarLedOptions[value.toString()]} (${value})"  
            }
    }
    //
    if (settings.allStatusTextEnable == true) {
        runIn( 1, formatAttrib, [overwrite: true])    
    }
    //    
    if (cmds != null) {
        logDebug "sending the changed AdvancedOptions"
        sendZigbeeCommands( cmds )  
    }
    logInfo "preferencies updates are sent to the device..."
}

def ping() {
    logInfo "ping() is not implemented" 
}

def refresh() {
    logInfo "refresh()..."
    checkDriverVersion()
    updateTuyaVersion()
    ArrayList<String> cmds = []
    cmds += zigbee.readAttribute(0x0000, 0x0007, [:], delay=200)             // Power Source
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)             // batteryVoltage
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay=200)             // batteryPercentageRemaining
    
    if (isIAS() || is4in1()) {
        IAS_ATTRIBUTES.each { key, value ->
            cmds += zigbee.readAttribute(0x0500, key, [:], delay=200)
        }
    }
    if (is4in1()) {
        cmds += zigbee.command(0xEF00, 0x07, "00")    // Fantem Tuya Magic
    }
    cmds += zigbee.command(0xEF00, 0x03)
    if (settings.allStatusTextEnable == true) {
        runIn( 1, formatAttrib, [overwrite: true])    
    }
    sendZigbeeCommands( cmds ) 
}

def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

def checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logInfo "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        unschedule('pollPresence')    // now replaced with deviceHealthCheck
        scheduleDeviceHealthCheck()
        updateTuyaVersion()
        initializeVars( fullInit = false ) 
        state.driverVersion = driverVersionAndTimeStamp()
        if (state.tuyaDPs == null) state.tuyaDPs = [:]
        if (state.lastPresenceState != null) state.remove('lastPresenceState')    // removed in version 1.0.6 
        if (state.hashStringPars != null)    state.remove('hashStringPars')       // removed in version 1.1.0
        if (state.lastBattery != null)       state.remove('lastBattery')          // removed in version 1.3.0
        
        if ("DistanceMeasurement" in DEVICE?.capabilities) {
            if (settings?.ignoreDistance == true ) {
                device.deleteCurrentState('distance')
            }
            device.deleteCurrentState('battery')
            device.deleteCurrentState('tamper')
            device.deleteCurrentState('temperature')
            
        }
    }
}

void scheduleDeviceHealthCheck() {
    Random rnd = new Random()
    //schedule("1 * * * * ? *", 'deviceHealthCheck') // for quick test
    schedule("${rnd.nextInt(59)} ${rnd.nextInt(59)} 1/3 * * ? *", 'deviceHealthCheck')
}

def logInitializeRezults() {
    if (settings?.txtEnable) log.info "${device.displayName} manufacturer  = ${device.getDataValue("manufacturer")}"
    if (settings?.txtEnable) log.info "${device.displayName} Initialization finished\r                          version=${version()} (Timestamp: ${timeStamp()})"
}

// delete all attributes
void deleteAllCurrentStates() {
    device.properties.supportedAttributes.each { it->
        logDebug "deleting $it"
        device.deleteCurrentState("$it")
    }
    logInfo "All current states (attributes) DELETED"
}

void resetStats() {
    logInfo "Stats are reset..."
    state.tuyaDPs = [:]
    state.txCounter = 0
    state.txCounter = 0
}

// called by initialize() button
void initializeVars( boolean fullInit = false ) {
    logInfo "${device.displayName} InitializeVars( fullInit = ${fullInit} )..."
    if (fullInit == true) {
        deleteAllCurrentStates()
        state.clear()
        resetStats()        
        state.driverVersion = driverVersionAndTimeStamp()
        state.motionStarted = unix2formattedDate(now())
    }
    if (fullInit == true || state.deviceProfile == null) {
        setDeviceNameAndProfile()
    }
    //
    state.packetID = 0
    state.rxCounter = 0
    state.txCounter = 0
    if (fullInit == true || state.notPresentCounter == null) state.notPresentCounter = 0
    //
    if (fullInit == true || settings.logEnable == null) device.updateSetting("logEnable", true)
    if (fullInit == true || settings.txtEnable == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || settings.motionReset == null) device.updateSetting("motionReset", false)
    if (fullInit == true || settings.motionResetTimer == null) device.updateSetting("motionResetTimer", 60)
    if (fullInit == true || settings.advancedOptions == null)  device.updateSetting("advancedOptions", false)
    if (fullInit == true || settings.sensitivity == null) device.updateSetting("sensitivity", [value:"2", type:"enum"])
    if (fullInit == true || settings.keepTime == null) device.updateSetting("keepTime", [value:"0", type:"enum"])
    if (fullInit == true || settings.ignoreDistance == null) device.updateSetting("ignoreDistance", true)
    if (fullInit == true || settings.ledEnable == null) device.updateSetting("ledEnable", true)
    if (fullInit == true || settings.temperatureOffset == null) device.updateSetting("temperatureOffset",[value:0.0, type:"decimal"])
    if (fullInit == true || settings.humidityOffset == null) device.updateSetting("humidityOffset",[value:0.0, type:"decimal"])
    if (fullInit == true || settings.luxOffset == null) device.updateSetting("luxOffset",[value:1.0, type:"decimal"])
    if (fullInit == true || settings.radarSensitivity == null) device.updateSetting("radarSensitivity", [value:7, type:"number"])
    if (fullInit == true || settings.detectionDelay == null) device.updateSetting("detectionDelay", [value:0.2, type:"decimal"])
    if (fullInit == true || settings.fadingTime == null) device.updateSetting("fadingTime", [value:60.0, type:"decimal"])
    if (fullInit == true || settings.minimumDistance == null) device.updateSetting("minimumDistance", [value:0.25, type:"decimal"])
    if (fullInit == true || settings.maximumDistance == null) device.updateSetting("maximumDistance",[value:8.0, type:"decimal"])
    if (fullInit == true || settings.luxThreshold == null) device.updateSetting("luxThreshold", [value:5, type:"number"])
    if (fullInit == true || settings.illuminanceCoeff == null) device.updateSetting("illuminanceCoeff", [value:1.0, type:"decimal"])
    if (fullInit == true || settings.parEvents == null) device.updateSetting("parEvents", true)
    if (fullInit == true || settings.invertMotion == null) device.updateSetting("invertMotion", is2in1() ? true : false)
    if (fullInit == true || settings.reportingTime4in1 == null) device.updateSetting("reportingTime4in1", [value:DEFAULT_REPORTING_4IN1, type:"number"])
    if (fullInit == true || settings.allStatusTextEnable == null) device.updateSetting("allStatusTextEnable", false)
    
    if (isHL0SS9OAradar() || is2AAELWXKradar()) {
        if (fullInit == true || settings.radarAlarmMode == null) device.updateSetting("radarAlarmMode", [value:TS0225alarmMode.defaultValue.toString(), type:"enum"])
        if (fullInit == true || settings.radarAlarmVolume == null) device.updateSetting("radarAlarmVolume", [value:TS0225alarmVolume.defaultValue.toString(), type:"enum"])
        if (fullInit == true || settings.radarAlarmTime == null) device.updateSetting("radarAlarmTime", [value:2, type:"number"])
        
        if (fullInit == true || settings.motionMinimumDistance == null) device.updateSetting("motionMinimumDistance", [value:0.0, type:"decimal"])
        if (fullInit == true || settings.motionDetectionDistance == null) device.updateSetting("motionDetectionDistance", [value:8.0, type:"decimal"])
        if (fullInit == true || settings.motionDetectionSensitivity == null) device.updateSetting("motionDetectionSensitivity", [value:7, type:"number"])
        
        if (fullInit == true || settings.smallMotionDetectionDistance == null) device.updateSetting("smallMotionDetectionDistance", [value:5.0, type:"decimal"])
        if (fullInit == true || settings.smallMotionDetectionSensitivity == null) device.updateSetting("smallMotionDetectionSensitivity", [value:7, type:"number"])
        if (fullInit == true || settings.smallMotionMinimumDistance == null) device.updateSetting("smallMotionMinimumDistance", [value:5.0, type:"decimal"])
        
        if (fullInit == true || settings.staticDetectionDistance == null) device.updateSetting("staticDetectionDistance", [value:5.0, type:"decimal"])
        if (fullInit == true || settings.staticDetectionSensitivity == null) device.updateSetting("staticDetectionSensitivity", [value:7, type:"number"])
        if (fullInit == true || settings.staticDetectionMinimumDistance == null) device.updateSetting("staticDetectionMinimumDistance", [value:5.0, type:"decimal"])
                
        if (fullInit == true || settings.presenceKeepTime == null) device.updateSetting("presenceKeepTime", [value:30, type:"number"])
        if (fullInit == true || settings.ledIndicator == null) device.updateSetting("ledIndicator", false)
        if (fullInit == true || settings.motionFalseDetection == null) device.updateSetting("motionFalseDetection", true)
        if (fullInit == true || settings.breatheFalseDetection == null) device.updateSetting("breatheFalseDetection", false)

    }  
    if (isSBYX0LM6radar()) {
        // TODO !
    }
    /*
    if (isLINPTECHradar()) {
        if (fullInit == true || settings.ignoreDistance == null) device.updateSetting("ignoreDistance", false)
    }
    */
    // version 1.6.0 - load DeviceProfile specific defaults ...
    if (isLINPTECHradar()) {
        resetPreferencesToDefaults()
    }
    
    //
    if (fullInit == true) sendEvent(name : "powerSource",    value : "?", isStateChange : true)    // TODO !!!
    if (device.currentValue('healthStatus') == null) sendHealthStatusEvent('unknown')    
    //
}

def tuyaBlackMagic() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200)    // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, attributeReportingStatus
    cmds += zigbee.writeAttribute(0x0000, 0xffde, 0x20, 0x13, [:], delay=200)
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
    cmds += tuyaBlackMagic()    
    
    if (isIAS() ) {
        cmds += zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
        logDebug "Enrolling IAS device: ${cmds}"
    }
    else if (("0x0406" in DEVICE.configuration)) {
        cmds += "delay 200"
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}"    // OWON motion/occupancy cluster
    }
    else if (!(DEVICE?.type == "radar" || is2in1())) {    // skip the binding for all the radars!                // TODO: check EPs !!!
        cmds += "delay 200"
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0402 {${device.zigbeeId}} {}"
        cmds += "delay 200"
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0405 {${device.zigbeeId}} {}"
        cmds += "delay 200"
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x03 0x01 0x0400 {${device.zigbeeId}} {}"
    }

    sendZigbeeCommands(cmds)    
}

// called when used with capability "Initialize" it will call this method every time the hub boots up. So for things that need refreshing or re-connecting (LAN integrations come to mind here) ..
// runs first time driver loads, ie system startup 
// when capability Initialize exists, a Initialize command is added to the ui.
def initialize( boolean fullInit = true ) {
    log.info "${device.displayName} Initialize( fullInit = ${fullInit} )..."
    unschedule()
    initializeVars( fullInit )
    configure()
    runIn( 1, 'updated', [overwrite: true])
    runIn( 3, 'logInitializeRezults', [overwrite: true])
    runIn( 4, 'refresh', [overwrite: true])
}

private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    int tuyaCmd = is4in1() ? 0x04 : SETDATA
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
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
    state.packetID = ((state.packetID ?: 0) + 1 ) % 65536
    return zigbee.convertToHexString(state.packetID, 4)
}

private getDescriptionText(msg) {
    def descriptionText = "${device.displayName} ${msg}"
    if (settings?.txtEnable) log.info "${descriptionText}"
    return descriptionText
}

def logsOff(){
    if (settings?.logEnable) log.info "${device.displayName} debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
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
    if (!(rawValue == 0 || rawValue == 255)) {
        def minVolts = 2.1
        def maxVolts = 3.0
        def pct = (volts - minVolts) / (maxVolts - minVolts)
        def roundedPct = Math.round(pct * 100)
        if (roundedPct <= 0) roundedPct = 1
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

def sendBatteryEvent( batteryPercent, isDigital=false ) {
    def map = [:]
    map.name = 'battery'
    map.timeStamp = now()
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int)
    map.unit  = '%'
    map.type = isDigital ? 'digital' : 'physical'    
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    map.isStateChange = true
    // 
    def latestBatteryEvent = device.latestState('battery', skipCache=true)
    def latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now()
    def timeDiff = ((now() - latestBatteryEventTime) / 1000) as int
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) {
        // send it now!
        sendDelayedBatteryEvent(map)
    }
    else {
        def delayedTime = (settings?.batteryDelay as int) - timeDiff
        map.delayed = delayedTime
        map.descriptionText += " [delayed ${map.delayed} seconds]"
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds"
        runIn( delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map])
    }
}

private void sendDelayedBatteryEvent(Map map) {
    logInfo "${map.descriptionText}"
    //map.each {log.trace "$it"}
    sendEvent(map)
}


def setMotion( mode ) {
    switch (mode) {
        case "active" : 
            handleMotion(motionActive=true, isDigital=true)
            break
        case "inactive" :
            handleMotion(motionActive=false, isDigital=true)
            break
        default :
            if (settings?.logEnable) log.warn "${device.displayName} please select motion action)"
            break
    }
}

import java.security.MessageDigest
String generateMD5(String s) {
    if(s != null) {
        return MessageDigest.getInstance("MD5").digest(s.bytes).encodeHex().toString()
    } else {
        return "null"
    }
}

def sendSensitivityIAS( lvl ) {
    def sensitivityLevel = safeToInt(lvl, -1)
    if (sensitivityLevel < 0 || sensitivityLevel > 2) {
        logWarn "IAS sensitivity is not set for ${device.getDataValue('manufacturer')}, invalid value ${sensitivityLevel}"
        return null
    }
    ArrayList<String> cmds = []
    String str = sensitivityOpts.options[sensitivityLevel]
    cmds += zigbee.writeAttribute(0x0500, 0x0013, DataType.UINT8, sensitivityLevel as int, [:], delay=200)
    logDebug "${device.displayName} sending IAS sensitivity : ${str} (${sensitivityLevel})"
    // only prepare the cmds here!
    return cmds
}

def sendKeepTimeIAS( lvl ) {
    def keepTimeVal = safeToInt(lvl, -1)
    if (keepTimeVal < 0 || keepTimeVal > 5) {
        logWarn "IAS Keep Time  is not set for ${device.getDataValue('manufacturer')}, invalid value ${keepTimeVal}"
        return null
    }
    ArrayList<String> cmds = []
    String str = keepTime4in1Opts.options[keepTimeVal]
    cmds += zigbee.writeAttribute(0x0500, 0xF001, DataType.UINT8, keepTimeVal as int, [:], delay=200)
    logDebug "${device.displayName} sending IAS Keep Time : ${str} (${keepTimeVal})"
    // only prepare the cmds here!
    return cmds
}


// called when any event was received from the Zigbee device in parse() method..
def setPresent() {
    if ((device.currentValue("healthStatus") ?: "unknown") != "online") {
        sendHealthStatusEvent("online")
        powerSourceEvent() // sent only once now - 2023-01-31        // TODO - check!
        runIn( 1, formatAttrib, [overwrite: true])    
        logInfo "is present"
    }    
    state.notPresentCounter = 0    
}


def deviceHealthCheck() {
    state.notPresentCounter = (state.notPresentCounter ?: 0) + 1
    if (state.notPresentCounter > presenceCountTreshold) {
        if ((device.currentValue("healthStatus", true) ?: "unknown") != "offline" ) {
            sendHealthStatusEvent("offline")
            if (settings?.txtEnable) log.warn "${device.displayName} is not present!"
            /* commented out ver. 1.5.0 
             powerSourceEvent("unknown")        // TODO - check !!
            */
            if (!(device.currentValue('motion', true) in ['inactive', '?'])) {
                handleMotion(false, isDigital=true)
                if (settings?.txtEnable) log.warn "${device.displayName} forced motion to '<b>inactive</b>"
            }
            /* commented out ver. 1.5.0 
            if (safeToInt(device.currentValue('battery', true)) != 0) {
                if (settings?.txtEnable) log.warn "${device.displayName} forced battery to '<b>0 %</b>"
                sendBatteryEvent( 0, isDigital=true )
            }
            */
            runIn( 1, formatAttrib, [overwrite: true])    
        }
    }
    else {
        logDebug "deviceHealthCheck - online (notPresentCounter=${state.notPresentCounter})"
    }
    
}

def sendHealthStatusEvent(value) {
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

void formatAttrib() {
    if (settings.allStatusTextEnable == false) {    // do not send empty html or text attributes
        return
    }
    logDebug "formatAttrib - text"
    String attrStr = ""
    attrStr += addToAttr("status", "healthStatus")
    attrStr += addToAttr("motion", "motion")
    if (DEVICE.capabilities?.DistanceMeasurement == true && settings?.ignoreDistance == false) {
        attrStr += addToAttr("distance", "distance")
    }
    if (DEVICE.capabilities?.Battery == true) {
        attrStr += addToAttr("battery", "battery")
    }
    if (DEVICE.capabilities?.IlluminanceMeasurement == true) {
        attrStr += addToAttr("illuminance", "illuminance")
    }
    if (DEVICE.capabilities?.TemperatureMeasurement == true) {
        attrStr += addToAttr("temperature", "temperature")
    }
    if (DEVICE.capabilities?.RelativeHumidityMeasurement == true) {
        attrStr += addToAttr("humidity", "humidity")
    }
    attrStr = attrStr.substring(0, attrStr.length() - 3);    // remove ',  '
    updateAttr("all", attrStr)
    if (attrStr.length() > 64) { 
        updateAttr("all", "Max Attribute Size Exceeded: ${attrStr.length()}") 
    }
}

String addToAttr(String name, String key, String convert = "none") {
    String retResult = '' 
    String attrUnit = getUnitFromState(key)
    if (attrUnit == null) { attrUnit = "" }
    def curVal = device.currentValue(key,true)
    if (curVal != null) {
        if (convert == "int") {
              retResult += safeToInt(curVal).toString() + "" + attrUnit
        } 
        else if (convert == "double") {
            retResult += safeToDouble(curVal).toString() + "" + attrUnit
        } 
        else 
            retResult += curVal.toString() + "" + attrUnit
    }
    else {
        retResult += "n/a"
    }
    retResult += ',  '
    return retResult
}

String getUnitFromState(String attrName){
       return device.currentState(attrName)?.unit
}

void updateAttr(String aKey, aValue, String aUnit = "") {
    sendEvent(name:aKey, value:aValue, unit:aUnit, type: "digital")
}


def deleteAllStatesAndJobs() {
    state.clear()    // clear all states
    unschedule()
    device.deleteCurrentState('')
    //device.removeDataValue("softwareBuild")
    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}"
}

def setLEDMode(String mode) {
    Short paramVal = safeToInt(ledStatusOptions.find{ it.value == mode }?.key)    
    if (paramVal != null && paramVal != 99) {
        ArrayList<String> cmds = []
        def dpValHex = zigbee.convertToHexString(paramVal as int, 2)
        log.warn " sending LED command=${'6E'} value=${dpValHex}"
        sendZigbeeCommands( sendTuyaCommand("6E", DP_TYPE_ENUM, dpValHex) )
    }
    else {
        log.warn "Please select LED mode"
    }
}

def setDetectionMode(String mode) {
    Short paramVal = safeToInt(detectionModeOptions.find{ it.value == mode }?.key)    
    if (paramVal != null && paramVal != 99) {
        ArrayList<String> cmds = []
        def dpValHex = zigbee.convertToHexString(paramVal as int, 2)
        log.warn " sending Detection Mode command=${'6E'} value=${dpValHex}"
        sendZigbeeCommands( sendTuyaCommand("68", DP_TYPE_ENUM, dpValHex) )
    }
    else {
        log.warn "Please select Detection Mode  mode"
    }
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
    if (settings?.txtEnable) {
        log.warn "${device.displayName} " + msg
    }
}



def setReportingTime4in1( val ) {
    if (is4in1()) { 
        def value = safeToInt(val, -1)
        if (value >= 0) {
            logDebug "changing reportingTime4in1 to ${value==0 ? 10 : $val} ${value==0 ? 'seconds' : 'minutes'}  (raw=${value})"    
            return sendTuyaCommand("66", DP_TYPE_VALUE, zigbee.convertToHexString(value, 8))        
        }
    }
}

def setDetectionDelay( val ) {
    if (isZY_M100Radar() || isSBYX0LM6radar() || isYXZBRB58radar() || isSXM7L9XAradar()) { 
        def value = ((val as double) * 10.0) as int
        logDebug "changing radar detection delay to ${val} seconds (raw=${value})"                
        return sendTuyaCommand((isYXZBRB58radar() || isSXM7L9XAradar()) ? "6F" : "65", DP_TYPE_VALUE, zigbee.convertToHexString(value, 8))
    }
}

def setFadingTime( val ) {
    if (isZY_M100Radar() || isSBYX0LM6radar() || isYXZBRB58radar() || isSXM7L9XAradar()) { 
        def value = ((val as double) * 10.0) as int
        logDebug "changing radar fading time to ${val} seconds (raw=${value})"                
        return sendTuyaCommand((isYXZBRB58radar() || isSXM7L9XAradar())  ? "6E" : "66", DP_TYPE_VALUE, zigbee.convertToHexString(value, 8))
    }
    else if (isHL0SS9OAradar() || is2AAELWXKradar()) {
        def value = val as int
        logDebug "changing radar Presence Keep Time to ${val} seconds (raw=${value})"                
        return sendTuyaCommand( isHL0SS9OAradar() ? "0C" : "66", DP_TYPE_VALUE, zigbee.convertToHexString(value, 8))
    }
    else if (isLINPTECHradar()) {
        def value = safeToInt(val)
        logDebug "changing LINPTECH radar fadingTime to ${value} seconds"                // CHECK!
        return sendTuyaCommand( "65", DP_TYPE_VALUE, zigbee.convertToHexString(value, 8))
    }
    else { logWarn "setFadingTime: unsupported model!"; return null }
}

def setMinimumDistance( val ) {
    if (isZY_M100Radar() || isSBYX0LM6radar() || isYXZBRB58radar() || isSXM7L9XAradar()) { 
        int value = ((val as double) * 100.0) as int
        logDebug "changing radar minimum distance to ${val} m (raw=${value})"                
        return sendTuyaCommand((isYXZBRB58radar() || isSXM7L9XAradar()) ? "6C" : "03", DP_TYPE_VALUE, zigbee.convertToHexString(value as int, 8))
    }
}

def setMaximumDistance( val ) {
    if (isZY_M100Radar() || isSBYX0LM6radar() || isYXZBRB58radar() || isSXM7L9XAradar()) { 
        int value = ((val as double) * 100.0) as int
        logDebug "changing radar maximum distance to : ${val} m (raw=${value})"                
        return sendTuyaCommand((isYXZBRB58radar() || isSXM7L9XAradar()) ? "6B" : "04", DP_TYPE_VALUE, zigbee.convertToHexString(value as int, 8))
    }
}     

def setRadarSensitivity( val ) {
    if (isZY_M100Radar() || isSBYX0LM6radar() || isYXZBRB58radar() || isSXM7L9XAradar() || isHumanPresenceSensorFall() || isHumanPresenceSensorScene() || isLINPTECHradar()) { 
        logDebug "changing radar sensitivity to : ${val}"                
        return sendTuyaCommand((isYXZBRB58radar() || isSXM7L9XAradar()) ? "6A" : isLINPTECHradar() ? "0F" : "02", DP_TYPE_VALUE, zigbee.convertToHexString(val as int, 8))
    }
}

def setRadarLedIndicator( val ) {
    def value = val ? "01" : "00"
    return setRadarParameter("radaradarLedIndicator", isHL0SS9OAradar() ? "18" : "6B", DP_TYPE_BOOL, value)
}

def setRadarAlarmMode( val ) {
    def value = val as int
    return setRadarParameter("radarAlarmMode", isHL0SS9OAradar() ? "69" : "75", DP_TYPE_ENUM, value)
}

def setRadarAlarmVolume( val ) {
    def value = val as int
    return setRadarParameter("radarAlarmVolume", isHL0SS9OAradar() ? "66" : "74", DP_TYPE_ENUM, value)
}

def setRadarAlarmTime( val ) {
    def value = val as int
    return setRadarParameter("setRadarAlarmTime", isHL0SS9OAradar() ? "65" : "73", DP_TYPE_VALUE, value)
}

def setMotionFalseDetection( val ) {
    def value = val ? "01" : "00"
    return setRadarParameter("motionFalseDetection", isHL0SS9OAradar() ? "70" : "67", DP_TYPE_BOOL, value)
}

def setBreatheFalseDetection( val ) {
    def value = val ? "01" : "00"
    return setRadarParameter("breatheFalseDetection", isHL0SS9OAradar() ? "73" : "71", DP_TYPE_BOOL, value)
}

def setMotionDetectionDistance( val ) {
    def value = Math.round(val * 100)
    if (isLINPTECHradar()) {
        logDebug "changing LINPTECH radar MotionDetectionDistance to ${val}m (raw ${value})"
        return zigbee.writeAttribute(0xE002, 0xE00B, 0x21, value as int, [:], delay=200)
    }
    else {
        return setRadarParameter("motionDetectionDistance", isHL0SS9OAradar() ? "0D" : "04", DP_TYPE_VALUE, value)
    }
}

def setMotionMinimumDistance( val ) {
    def value = Math.round(val * 100)
    return setRadarParameter("motionMinimumDistance", isHL0SS9OAradar() ? "6A" : "03", DP_TYPE_VALUE, value)
}

def setMotionDetectionSensitivity( val ) {
    def value = val as int
    if (isLINPTECHradar()) {
        logDebug "changing LINPTECH radar MotionDetectionSensitivity to ${value}"
        return zigbee.writeAttribute(0xE002, 0xE004, 0x20, value as int, [:], delay=200)
    }
    else {        
        return setRadarParameter("motionDetectionSensitivity", (isHL0SS9OAradar() || isLINPTECHradar()) ? "0F" : "02", DP_TYPE_ENUM, value)
    }
}

def setSmallMotionDetectionDistance( val ) {
    def value = Math.round(val * 100)
    return setRadarParameter("smallMotionDetectionDistance", isHL0SS9OAradar() ? "0E" : "68", DP_TYPE_VALUE, value)
}

def setSmallMotionDetectionSensitivity( val ) {
    def value = val as int
    return setRadarParameter("smallMotionDetectionSensitivity", isHL0SS9OAradar() ? "10" : "69" , DP_TYPE_ENUM, value)
}

def setSmallMotionMinimumDistance( val ) {
    def value = Math.round(val * 100)
    return setRadarParameter("smallMotionDetectionDistance", isHL0SS9OAradar() ? "6B" : "6E", DP_TYPE_VALUE, value)
}

def setStaticDetectionDistance( val ) {
    def value = Math.round(val * 100)
    return setRadarParameter("staticDetectionDistance", isHL0SS9OAradar() ? "67" : "6C", DP_TYPE_VALUE, value)
}

def setStaticDetectionSensitivity( val ) {
    def value = val as int
    if (isLINPTECHradar()) {
        logDebug "changing LINPTECH radar StaticDetectionSensitivity to ${value}"
        return zigbee.writeAttribute(0xE002, 0xE005, 0x20, value as int, [:], delay=200)
    }
    else { 
        return setRadarParameter("staticDetectionSensitivity", isHL0SS9OAradar() ? "68" : isLINPTECHradar() ? "10" : "6D", DP_TYPE_ENUM, value)
    }
}

def setStaticDetectionMinimumDistance( val ) {
    def value = Math.round(val * 100)
    return setRadarParameter("staticDetectionMinimumDistance", isHL0SS9OAradar() ? "6C" : "6F", DP_TYPE_VALUE, value)
}

/*
            cmds += setFadingTime( settings?.presenceKeepTime)               // TS0225 radar presenceKeepTime (in seconds)
            cmds += setRadarLedIndicator( settings?.ledIndicator )
            cmds += setRadarAlarmMode( settings?.radarAlarmMode )
            cmds += setRadarAlarmVolume( settings?.radarAlarmVolume )
            cmds += setRadarAlarmTime( settings?.radarAlarmTime )
            cmds += setMotionFalseDetection( settings?.motionFalseDetection )
            cmds += setBreatheFalseDetection( settings?.breatheFalseDetection )
            cmds += setMotionDetectionDistance( settings?.motionDetectionDistance )
            cmds += setMotionMinimumDistance( settings?.motionMinimumDistance )
            cmds += setMotionDetectionSensitivity( settings?.motionDetectionSensitivity )
            cmds += setSmallMotionDetectionDistance( settings?.smallMotionDetectionDistance )
            cmds += setSmallMotionMinimumDistance( settings?.smallMotionMinimumDistance )
            cmds += setSmallMotionDetectionSensitivity( settings?.smallMotionDetectionSensitivity )
            cmds += setStaticDetectionDistance( settings?.staticDetectionDistance )
            cmds += setStaticDetectionSensitivity( settings?.staticDetectionSensitivity )
            cmds += setStaticDetectionMinimumDistance( settings?.staticDetectionMinimumDistance )
*/

def setPreferencesFromDeviceProfile() {
    ArrayList<String> cmds = []
    
    def preferences = DEVICE.preferences
    //logDebug "preferences=${preferences}"
    if (preferences == null || preferences == [:]) {
        logDebug "no preferences defined for device profile ${getDeviceGroup()}"
        return
    }
    
    String   dpType  = ""
    Map      tuyaDPs = [:]
    Integer  intValue = 0
    Integer preferenceValue = 0
    preferences.each { name, dpValue -> 
        intValue = safeToInt(dpValue)
        //log.trace "$name $intValue"
        if (intValue <=0 || intValue >=255) {
            logDebug "skipping ${name}, DP is ${intValue}"
            return    // continue
        }
        def dpMaps   =  DEVICE.tuyaDPs 
        def foundMap = dpMaps.find { it.dp == intValue }
        log.debug "foundMap = ${foundMap}"
        if (foundMap != null) {
            // TODO - calculate intValue from the preferences ( multiplied by the Scale ) !!
            // preferenceValue = getScaledPrefrenceValueByName(name, foundMap)
            dpType = foundMap.type == "bool" ? DP_TYPE_BOOL : foundMap.type == "enum" ? DP_TYPE_ENUM : (foundMap.type in ["value", "number", "decimal"]) ? DP_TYPE_VALUE: "unknown"
            cmds += setRadarParameter( name, zigbee.convertToHexString(intValue, 2), dpType, preferenceValue.toString())
        }
        else {
            logWarn "warning: couldn't find tuyaDPs map for dp ${dpValue}"
            return
        }
    }    
    
    
    //logDebug "preparing radar parameter ${parName} value ${DPval} (raw=${value}) Tuya dp=${DPcommand} (${zigbee.convertHexToInt(DPcommand)})"    
    
    logDebug "setPreferencesFromDeviceProfile: ${cmds}"
    return cmds
}


def setRadarParameter( String parName, String DPcommand, String DPType, DPval) {
    ArrayList<String> cmds = []
    if (!(isHL0SS9OAradar() || is2AAELWXKradar())) {
        logWarn "${parName}: unsupported model ${state.deviceProfile} !"
        return null 
    }
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
    cmds = sendTuyaCommand( DPcommand, DPType, value)
    logDebug "sending radar parameter ${parName} value ${DPval} (raw=${value}) Tuya dp=${DPcommand} (${zigbee.convertHexToInt(DPcommand)})"                
    return cmds
}



// TODO - commands DP to be extracted from the device profile
def resetSettings(val)     { return radarCommand("resetSettings", isHL0SS9OAradar() ? "71" : "70", DP_TYPE_BOOL) }
def moveSelfTest(val)      { return radarCommand("moveSelfTest", isHL0SS9OAradar() ? "72" : "76", DP_TYPE_BOOL) }        // check!
def smallMoveSelfTest(val) { return radarCommand("smallMoveSelfTest", isHL0SS9OAradar() ? "6E" : "77", DP_TYPE_BOOL) }   // check!
def breatheSelfTest(val)   { return radarCommand("breatheSelfTest", isHL0SS9OAradar() ? "6F" : "78", DP_TYPE_BOOL) }     // check!

def radarCommand( String command, String DPcommand, String DPType) {
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
            value = "00"
            cmds = sendTuyaCommand( DPcommand, DPType, value)
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
def sendCommand( command=null, val=null )
{
    logDebug "sending command ${command}(${val}))"
    ArrayList<String> cmds = []
    def supportedCommandsMap = DEVICE.commands 
    if (supportedCommandsMap == null || supportedCommandsMap == [:]) {
        logWarn "sendCommand: no commands defined for device profile ${getDeviceGroup()} !"
        return
    }
    // TODO: compare ignoring the upper/lower case of the command.
    def supportedCommandsList =  DEVICE.commands.keySet() as List 
    // check if the command is defined in the DEVICE commands map
    if (command == null || !(command in supportedCommandsList)) {
        logWarn "sendCommand: the command <b>${(command ?: '')}</b> must be one of these : ${supportedCommandsList}"
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
        sendZigbeeCommands( cmds )
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
        log.trace "validPars=${validPars}"
    }
    return validPars
}

/**
 * Validates the parameter value based on the given dpMap.
 * 
 * @param dpMap The map containing the parameter type, minimum and maximum values.
 * @param val The value to be validated.
 * @return The validated value if it is within the specified range, null otherwise.
 */
def validateAndScaleParameterValue(Map dpMap, String val) {
    def value = null
    def parType = dpMap.type
    if (parType == "number") {
        value = safeToInt(val, -1)
    }
    else if (parType == "decimal") {
        value = safeToDouble(val, -1.0)
    }
    else if (parType == "bool") {
        if (val == '0' || val == 'false') {
            value = 0
        }
        else if (val == '1' || val == 'true') {
            value = 1
        }
        else {
            log.warn "${device.displayName} setPar: bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>"
            return null
        }
    }
    else {
        log.warn "${device.displayName} setPar: unsupported parameter type <b>${parType}</b>"
        return null
    }
    Boolean validated = true
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) {
        log.warn "${device.displayName} setPar: invalid ${par} parameter value <b>${val}</b> value must be within ${dpMap.min} and ${dpMap.max}"
        return null
    }
    return value
}


/**
 * Sets the parameter value for the device.
 * @param par The name of the parameter to set.
 * @param val The value to set the parameter to.
 * @return Nothing.
 */
def setPar( par=null, val=null )
{
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) {
        // new method
        logDebug "setPar new method: setting parameter ${par} to ${val}"
        ArrayList<String> cmds = []
        
        Boolean validated = false
        if (par == null) {
            log.warn "${device.displayName} setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"
            return
        }        
        if (!(par in getValidParsPerModel())) {
            log.warn "${device.displayName} setPar: parameter '${par}' must be one of these : ${getValidParsPerModel()}"
            return
        }
        // find the tuayDPs map for the par
        Map dpMap = getPteferenceMap(par, false)
        if ( dpMap == null ) {
            log.warn "${device.displayName} setPar: tuyaDPs map not found for parameter <b>${par}</b>"
            return
        }
        // convert the val to the correct type and scales it if needed
        def tuyaValue = validateAndScaleParameterValue(dpMap, val as String)
        if (tuyaValue == null) {
            log.warn "${device.displayName} setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"
            return
        }
        // update the device setting
        device.updateSetting("$par", [value:val, type:dpMap.type])
        logDebug "parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${value}"
        // search for set function
        String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1]
        String setFunction = "set${capitalizedFirstChar}"
        // check if setFunction method exists
        if (!this.respondsTo(setFunction)) {
            log.warn "${device.displayName} setPar: set function <b>${setFunction}</b> not found"
            return
        }
        logDebug "setFunction=${setFunction}"
        // execute the setFunction
        try {
            cmds = "$setFunction"(tuyaValue)
        }
        catch (e) {
            logWarn "setPar: Exception '${e}'caught while processing <b>$setFunction</b>(<b>$tuyaValue</b>) (val=${val}))"
            return
        }
        logInfo "executed setPar <b>$setFunction</b>(<b>$tuyaValue</b>)"
        sendZigbeeCommands( cmds )
        return
    }
}

/**
 * Updates the Tuya version of the device based on the application version.
 * If the Tuya version has changed, updates the device data value and logs the change.
 */
void updateTuyaVersion() {
    def application = device.getDataValue("application") 
    if (application != null) {
        Integer ver
        try {
            ver = zigbee.convertHexToInt(application)
        }
        catch (e) {
            logWarn "exception caught while converting application version ${application} to tuyaVersion"
            return
        }
        def str = ((ver&0xC0)>>6).toString() + "." + ((ver&0x30)>>4).toString() + "." + (ver&0x0F).toString()
        if (device.getDataValue("tuyaVersion") != str) {
            device.updateDataValue("tuyaVersion", str)
            logInfo "tuyaVersion set to $str"
        }
    }
    else {
        logWarn "application version is NULL"
    }
}

/**
 * Returns the device name and profile based on the device model and manufacturer.
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value.
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value.
 * @return A list containing the device name and profile.
 */
def getDeviceNameAndProfile( model=null, manufacturer=null) {
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
        logWarn "<b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}"
    }
    return [deviceName, deviceProfile]
}

// called from  initializeVars( fullInit = true)
def setDeviceNameAndProfile( model=null, manufacturer=null) {
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer)
    if (deviceProfile == null) {
        logWarn "unknown model ${deviceModel} manufacturer ${deviceManufacturer}"
        // don't change the device name when unknown
        state.deviceProfile = UNKNOWN
    }
    def dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN
    def dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN
    if (deviceName != NULL && deviceName != UNKNOWN  ) {
        device.setName(deviceName)
        state.deviceProfile = deviceProfile
        //logDebug "before: forcedProfile = ${settings.forcedProfile} to be set to ${deviceProfilesV2[deviceProfile].description}"
        device.updateSetting("forcedProfile", [value:deviceProfilesV2[deviceProfile].description, type:"enum"])
        //pause(1)
        //logDebug "after : forcedProfile = ${settings.forcedProfile}"
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>"
    } else {
        logWarn "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!"
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

def testTuyaCmd( dpCommand, dpValue, dpTypeString ) {
    ArrayList<String> cmds = []
    def dpType   = dpTypeString=="DP_TYPE_VALUE" ? DP_TYPE_VALUE : dpTypeString=="DP_TYPE_BOOL" ? DP_TYPE_BOOL : dpTypeString=="DP_TYPE_ENUM" ? DP_TYPE_ENUM : null
    def dpValHex = dpTypeString=="DP_TYPE_VALUE" ? zigbee.convertToHexString(dpValue as int, 8) : dpValue
    log.warn " sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}"
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}    




def inputIt( String param, boolean debug=false ) {
    Map input = [:]
    Map foundMap = [:]
    if (!(param in DEVICE.preferences)) {
        if (debug) log.warn "inputIt: preference ${param} not defined for this device!"
        return null
    }
    def preference = DEVICE.preferences["$param"]
    boolean isTuyaDP = preference.isNumber()
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}"
    if (isTuyaDP) {
        // find the preference in the tuyaDPs map
        int dp = safeToInt(preference)
        def dpMaps   =  DEVICE.tuyaDPs 
        foundMap = dpMaps.find { it.dp == dp }
    }
    else { // cluster:attribute
        
        if (debug) log.trace "${DEVICE.attributes}"
        def dpMaps   =  DEVICE.tuyaDPs 
        foundMap = DEVICE.attributes.find { it.at == preference }
    }
    
    if (true) {
        //if (debug) log.debug "foundMap = ${foundMap}"
        if (foundMap == null) {
            if (debug) log.warn "inputIt: map not found for param '${param}'!"
            return null
        }
        if (foundMap.rw != "rw") {
            //if (debug) log.warn "inputIt: param '${param}' is read only!"
            return null
        }        
        input.name = foundMap.name
        input.type = foundMap.type    // bool, number, decimal
        input.title = foundMap.title
        input.description = foundMap.description
        if (foundMap.min != null && foundMap.max != null) {
            input.range = "${foundMap.min}..${foundMap.max}"
        }
        if (input.range != null && input.description !=null) {
            input.description += "<br><i>Range: ${input.range}</i>"
            if (foundMap.unit != null) {
                input.description += " <i>(${foundMap.unit})</i>"
            }
        }
        input.defaultValue = foundMap.defaultValue
    }
    else {    
        //if (debug) log.warn "not implemented!"
        return null
    }
    
    return input
}


def testParse(description) {
    logWarn "testParse: ${description}"
    parse(description)
    log.trace "---end of testParse---"
}


String unix2formattedDate( unixTime ) {
    try {
        if (unixTime == null) return null
        def date = new Date(unixTime.toLong())
        return date.format("yyyy-MM-dd HH:mm:ss.SSS", location.timeZone)
    } catch (Exception e) {
        logWarn "Error formatting date: ${e.message}. Returning current time instead."
        return new Date().format("yyyy-MM-dd HH:mm:ss.SSS", location.timeZone)
    }
}

def formattedDate2unix( formattedDate ) {
    try {
        if (formattedDate == null) return null
        def date = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", formattedDate)
        return date.getTime()
    } catch (Exception e) {
        logWarn "Error parsing formatted date: ${formattedDate}. Returning current time instead."
        return now()
    }
}


@Field static final Map<Integer, Map> SettableParsFieldMap = new ConcurrentHashMap<>().withDefault {
    new ConcurrentHashMap<Integer, Map>()
}

def getSettableParsList() {
    if (device?.id == null) {
        return ["SEE LOGS"]
    }
    if (SettableParsFieldMap.get(device?.id)) {
        return SettableParsFieldMap.get(device?.id).pars.keySet().toList()
    }
    // put a map in the SettableParsFieldMap for the device.id if it doesn't exist, containing the settable parameters
    Map settableParsMap = [:]
    settableParsMap['pars'] = DEVICE.preferences
    SettableParsFieldMap.put(device?.id, settableParsMap)
    def result = SettableParsFieldMap.get(device?.id).pars.keySet().toList()
    log.trace  "${result}"
    log.warn "stored ${SettableParsFieldMap.get(device?.id)}"
    return result

}


def test( val ) {

    def list = getSettableParsList()

    logWarn "test list: ${list})"
}
