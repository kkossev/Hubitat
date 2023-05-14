/**
 *  Tuya Multi Sensor 4 In 1 driver for Hubitat
 *
 *  https://community.hubitat.com/t/alpha-tuya-zigbee-multi-sensor-4-in-1/92441
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
 *
 *                                   TODO: ignore invalid humidity reprots (>100 %)
 *                                   TODO: add illuminance threshold / configuration
 *                                   TODO: add rtt measurement for ping()
 *                                   TODO: use getKeepTimeOpts() for processing dp=0x0A (10) keep time ! ( 2-in-1 time is wrong)
 *                                   TODO: RADAR profile devices are not automtically updated from 'UNKNOWN'!
 *                                   TODO: present state 'motionStarted' in a human-readable form.
 *                                   TODO: add to state 'last battery' the time when the battery was last reported.
 *                                   TODO: check the bindings commands in configure()
 *                                   TODO: implement getActiveEndpoints()
*/

def version() { "1.3.3" }
def timeStamp() {"2023/05/14 5:45 PM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol
import hubitat.zigbee.clusters.iaszone.ZoneStatus
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap


@Field static final Boolean _DEBUG = false

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

        attribute "batteryVoltage", "number"
        attribute "healthStatus", "enum", ["offline", "online"]
        attribute "distance", "number"              // Tuya Radar
        attribute "unacknowledgedTime", "number"    // AIR models
        attribute "motionType", "enum",  ["none", "presence", "peacefull", "smallMove", "largeMove"]    // blackSensor
        attribute "existance_time", "number"        // BlackSquareRadar
        attribute "leave_time", "number"            // BlackSquareRadar
        
        attribute "radarSensitivity", "number" 
        attribute "detectionDelay", "decimal" 
        attribute "fadingTime", "decimal" 
        attribute "minimumDistance", "decimal" 
        attribute "maximumDistance", "decimal"
        attribute "radarStatus", "enum", ["checking", "check_success", "check_failure", "others", "comm_fault", "radar_fault"] 
        
        command "configure", [[name: "Configure the sensor after switching drivers"]]
        command "initialize", [[name: "Initialize the sensor after switching drivers.  \n\r   ***** Will load device default values! *****" ]]
        command "setMotion", [[name: "setMotion", type: "ENUM", constraints: ["No selection", "active", "inactive"], description: "Force motion active/inactive (for tests)"]]
        command "refresh",   [[name: "May work for some DC/mains powered sensors only"]] 
        command "setPar", [
                [name:"par", type: "ENUM", description: "preference parameter name", constraints: settableParsMap.keySet() as List],
                [name:"val", type: "STRING", description: "preference parameter value", constraints: ["STRING"]]
        ]
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
            input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: true)
            input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "<i>Display sensor states in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
            if (!(isRadar() || isBlackSquareRadar() || isOWONRadar())) {
                input (name: "motionReset", type: "bool", title: "<b>Reset Motion to Inactive</b>", description: "<i>Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b></i>", defaultValue: false)
                if (motionReset.value == true) {
    		        input ("motionResetTimer", "number", title: "After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds", description: "", range: "0..7200", defaultValue: 60)
                }
            }
            if (false) {
                input ("temperatureOffset", "decimal", title: "Temperature offset", description: "Select how many degrees to adjust the temperature.", range: "-100..100", defaultValue: 0.0)
                input ("humidityOffset", "decimal", title: "Humidity offset", description: "Enter a percentage to adjust the humidity.", range: "-50..50",  defaultValue: 0.0)
                input ("luxOffset", "decimal", title: "Illuminance coefficient", description: "Enter a coefficient to multiply the illuminance.", range: "0.1..2.0",  defaultValue: 1.0)
            }
        }
        if (is4in1()) {
            input (name: "ledEnable", type: "bool", title: "<b>Enable LED</b>", description: "<i>Enable LED blinking when motion is detected (4in1 only)</i>", defaultValue: true)
        }
        if (isConfigurable() || is4in1() || is3in1() || is2in1()) {
            input (name: "keepTime", type: "enum", title: "<b>Motion Keep Time</b>", description:"Select PIR sensor keep time (s)", options: getKeepTimeOpts().options, defaultValue: getKeepTimeOpts().defaultValue)
        }
        if (isConfigurable() || is4in1() || is3in1() || is2in1()) {
            input (name: "sensitivity", type: "enum", title: "<b>Motion Sensitivity</b>", description:"Select PIR sensor sensitivity", options: sensitivityOpts.options, defaultValue: sensitivityOpts.defaultValue)
        }
        if (advancedOptions == true || advancedOptions == false) { 
            if (isLuxMeter()) {
                input ("luxThreshold", "number", title: "<b>Lux threshold</b>", description: "Minimum change in the lux which will trigger an event", range: "0..999", defaultValue: 1)   
            }
        }
        input (name: "advancedOptions", type: "bool", title: "Advanced Options", description: "<i>May not work for all device types!</i>", defaultValue: false)
        if (advancedOptions == true) {
            input (name: "forcedProfile", type: "enum", title: "<b>Device Profile</b>", description: "<i>Forcely change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!</i>", 
                   options: getDeviceProfilesMap())
            input (name: "batteryDelay", type: "enum", title: "<b>Battery Events Delay</b>", description:"<i>Select the Battery Events Delay<br>(default is <b>no delay</b>)</i>", options: delayBatteryOpts.options, defaultValue: delayBatteryOpts.defaultValue)
            if (isRadar()) {
                input (name: "ignoreDistance", type: "bool", title: "<b>Ignore distance reports</b>", description: "If not used, ignore the distance reports received every 1 second!", defaultValue: true)
		        input ("radarSensitivity", "number", title: "<b>Radar sensitivity (1..9)</b>", description: "", range: "0..9", defaultValue: 7)   
		        input ("detectionDelay", "decimal", title: "<b>Detection delay, seconds</b>", description: "", range: "0.0..120.0", defaultValue: 0.2)   
		        input ("fadingTime", "decimal", title: "<b>Fading time, seconds</b>", description: "", range: "0.5..500.0", defaultValue: 60.0)   
		        input ("minimumDistance", "decimal", title: "<b>Minimum detection distance, meters</b>", description: "", range: "0.0..9.5", defaultValue: 0.25)   
		        input ("maximumDistance", "decimal", title: "<b>Maximum detection distance, meters</b>", description: "", range: "0.0..9.5", defaultValue: 8.0)   
            }
            if (isHumanPresenceSensorAIR()) {
		        input (name: "vacancyDelay", type: "number", title: "Vacancy Delay", description: "Select vacancy delay (0..1000), seconds", range: "0..1000", defaultValue: 10)   
                input (name: "ledStatusAIR", type: "enum", title: "LED Status", description:"Select LED Status", defaultValue: -1, options: ledStatusOptions)
                input (name: "detectionMode", type: "enum", title: "Detection Mode", description:"Select Detection Mode", defaultValue: -1, options: detectionModeOptions)
                input (name: "vSensitivity", type: "enum", title: "V Sensitivity", description:"Select V Sensitivity", defaultValue: -1, options: vSensitivityOptions)
                input (name: "oSensitivity", type: "enum", title: "O Sensitivity", description:"Select O Sensitivity", defaultValue: -1, options: oSensitivityOptions)
            }
            if (isBlackPIRsensor()) {
		        input (name: "inductionTime", type: "number", title: "Induction Time", description: "Induction time (24..300) seconds", range: "24..300", defaultValue: 24)   
                input (name: "targetDistance", type: "enum", title: "Target Distance", description:"Select target distance", defaultValue: -1, options: blackSensorDistanceOptions)
            }
            if (isBlackSquareRadar()) {
		        input (name: "indicatorLight", type: "enum", title: "Indicator Light", description: "Red LED is lit when presence detected", defaultValue: "0", options: blackRadarLedOptions)  
            }
            if (is4in1()) {
  		        input ("reportingTime4in1", "number", title: "<b>4-in-1 Reporting Time</b>", description: "<i>4-in-1 Reporting Time configuration, minutes.<br>0 will enable real-time (10 seconds) reporting!</i>", range: "0..7200", defaultValue: DEFAULT_REPORTING_4IN1)
            }
            input (name: "invertMotion", type: "bool", title: "<b>Invert Motion Active/Not Active</b>", description: "<i>Some Tuya motion sensors may report the motion active/inactive inverted...</i>", defaultValue: false)

            
        }
    }
}

@Field static final Map settableParsMap = [
    "--- Select ---"   : [ min: null, scale: 0, max: null,  step: 1,    type: 'number',  defaultValue: 7   , function: 'setParSelectHelp'],
    "reportingTime4in1": [ min: 0,    scale: 0, max: 7200,  step: 1,   type: 'number',   defaultValue: 10  , function: 'setReportingTime4in1'],
    "radarSensitivity" : [ min: 1,    scale: 0, max: 9,     step: 1,   type: 'number',   defaultValue: 7   , function: 'setRadarSensitivity'],
    "detectionDelay"   : [ min: 0.0,  scale: 0, max: 120.0, step: 0.1, type: 'decimal',  defaultValue: 0.2 , function: 'setRadarDetectionDelay'],
    "fadingTime"       : [ min: 0.5,  scale: 0, max: 500.0, step: 1.0, type: 'decimal',  defaultValue: 60.0, function: 'setRadarFadingTime'],
    "minimumDistance"  : [ min: 0.0,  scale: 0, max:   9.5, step: 0.1, type: 'decimal',  defaultValue: 0.25, function: 'setRadarMinimumDistance'],
    "maximumDistance"  : [ min: 0.0,  scale: 0, max:   9.5, step: 0.1, type: 'decimal',  defaultValue:  8.0, function: 'setRadarMaximumDistance']
]

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

def getModelGroup()          { return state.deviceProfile ?: "UNKNOWN" }
def getDeviceProfiles()      { deviceProfilesV2.keySet() }
def getDeviceProfilesMap()   {deviceProfilesV2.values().description as List<String>}
def is4in1() { return getModelGroup().contains("TS0202_4IN1") }
def is3in1() { return getModelGroup().contains("TS0601_3IN1") }
def is2in1() { return getModelGroup().contains("TS0601_2IN1") }
def isMotionSwitch() { return getModelGroup().contains("TS0202_MOTION_SWITCH") }
def isIAS()  { return getModelGroup().contains("TS0202_MOTION_IAS") || getModelGroup().contains("TS0202_4IN1") || getModelGroup().contains("TS0601_2IN1") }
def isChattyRadarDistanceReport(descMap)  {return isRadar() && (descMap?.clusterId == "EF00" && descMap.command == "02" && descMap.data?.size > 2  && descMap.data[2] == "09") }

def isTS0601_PIR() { return (device.getDataValue('model') in ['TS0601']) && !(isRadar() || isHumanPresenceSensorAIR() || isBlackPIRsensor() || isHumanPresenceSensorScene() || isHumanPresenceSensorFall() || isBlackSquareRadar()) }

def isConfigurable() { return isIAS() }   // TS0202 models ['_TZ3000_mcxw5ehu', '_TZ3000_msl6wxk9']
def isLuxMeter() { return (is2in1() || is3in1() || is4in1() || isRadar() || isHumanPresenceSensorAIR() || isBlackPIRsensor() || isHumanPresenceSensorScene() || isHumanPresenceSensorFall() || isBlackSquareRadar()) }

def isRadar() { return (device.getDataValue('manufacturer') in ['_TZE200_ztc6ggyl', '_TZE204_ztc6ggyl', '_TZE200_ikvncluo', '_TZE200_lyetpprm', '_TZE200_wukb7rhc', '_TZE200_jva8ink8', '_TZE200_ar0slwnd', '_TZE200_sfiy5tfs',
                                                               '_TZE200_mrf6vtua', '_TZE200_holel4dk', '_TZE200_xpq2rzhq']) || isYXZBRB58radar() }
def isRadarMOES() { return device.getDataValue('manufacturer') in ['_TZE200_ikvncluo'] }
def isBlackPIRsensor() { return device.getDataValue('manufacturer') in ['_TZE200_9qayzqa8'] }
def isBlackSquareRadar() {  return getModelGroup().contains("TS0601_BLACK_SQUARE_RADAR") }
def isOWONRadar() { return device.getDataValue('manufacturer') in ['OWON'] }

def isHumanPresenceSensorAIR()     { return device.getDataValue('manufacturer') in ['_TZE200_auin8mzr'] } 
def isHumanPresenceSensorScene()   { return device.getDataValue('manufacturer') in ['_TZE200_vrfecyku'] } 
def isHumanPresenceSensorFall()    { return device.getDataValue('manufacturer') in ['_TZE200_lu01t0zl'] } 
def isYXZBRB58radar()              { return getModelGroup().contains("TS0601_YXZBRB58_RADAR") }  


@Field static final Map deviceProfilesV2 = [
    "TS0202_4IN1"  : [
            description   : "Tuya 4in1 (motion/temp/humi/lux) sensor",
            models        : ["TS0202"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00", outClusters:"0019,000A", model:"TS0202",  manufacturer:"_TZ3210_zmy9hjay", deviceJoinName: "Tuya TS0202 Multi Sensor 4 In 1"],        // pairing: double click!
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00", outClusters:"0019,000A", model:"5j6ifxj", manufacturer:"_TYST11_i5j6ifxj", deviceJoinName: "Tuya TS0202 Multi Sensor 4 In 1"],       
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00", outClusters:"0019,000A", model:"hfcudw5", manufacturer:"_TYST11_7hfcudw5", deviceJoinName: "Tuya TS0202 Multi Sensor 4 In 1"],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00", outClusters:"0019,000A", model:"TS0202",  manufacturer:"_TZ3210_rxqls8v0", deviceJoinName: "Tuya TS0202 Multi Sensor 4 In 1"],        // not tested
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00", outClusters:"0019,000A", model:"TS0202",  manufacturer:"_TZ3210_wuhzzfqg", deviceJoinName: "Tuya TS0202 Multi Sensor 4 In 1"]        // https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars/92441/282?u=kkossev
            ],
            deviceJoinName: "Tuya Multi Sensor 4 In 1",
            capabilities  : ["motion": true, "temperature": true, "humidity": true, "illuminance": true, "tamper": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "dc"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],
    
    "TS0601_3IN1"  : [                                // https://szneo.com/en/products/show.php?id=239 // https://www.banggood.com/Tuya-Smart-Linkage-ZB-Motion-Sensor-Human-Infrared-Detector-Mobile-Phone-Remote-Monitoring-PIR-Sensor-p-1858413.html?cur_warehouse=CN 
            description   : "Tuya 3in1 (Motion/Temp/Humi) sensor",
            models        : ["TS0601"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_7hfcudw5", deviceJoinName: "Tuya NAS-PD07 Multi Sensor 3 In 1"]
            ],
            deviceJoinName: "Tuya Multi Sensor 3 In 1",
            capabilities  : ["motion": true, "temperature": true, "humidity": true, "tamper": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "dc"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],

    "TS0601_2IN1"  : [
            description   : "Tuya 2in1 (Motion and Illuminance) sensor",
            models         : ["TS0601"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_3towulqd", deviceJoinName: "Tuya 2 in 1 Zigbee Mini PIR Motion Detector + Bright Lux ZG-204ZL"]          // https://www.aliexpress.com/item/1005004095233195.html
            ],
            deviceJoinName: "Tuya Multi Sensor 2 In 1",
            capabilities  : ["motion": true, "temperature": true, "illuminance": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "battery"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],
    
    "TS0202_MOTION_IAS"   : [
            description   : "Tuya TS0202 Motion sensor (IAS)",
            models        : ["TS0202","RH3040"],
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
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"WHD02",  manufacturer:"_TZ3000_hktqahrq", deviceJoinName: "Tuya TS0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-53o41joc", deviceJoinName: "TUYATEC RH3040 Motion Sensor"],                                            // 60 seconds reset period        
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-b5g40alm", deviceJoinName: "TUYATEC RH3040 Motion Sensor"], 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-deetibst", deviceJoinName: "TUYATEC RH3040 Motion Sensor"], 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-bd5faf9p", deviceJoinName: "Nedis/Samotech RH3040 Motion Sensor"], 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-zn9wyqtr", deviceJoinName: "Samotech RH3040 Motion Sensor"],                                           // vendor: 'Samotech', model: 'SM301Z'
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-b3ov3nor", deviceJoinName: "Zemismart RH3040 Motion Sensor"],                                          // vendor: 'Nedis', model: 'ZBSM10WT'
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500", model:"RH3040", manufacturer:"TUYATEC-2gn2zf9e", deviceJoinName: "TUYATEC RH3040 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500,0B05", outClusters:"0019", model:"TY0202", manufacturer:"_TZ1800_fcdjzz3s", deviceJoinName: "Lidl TY0202 Motion Sensor"],
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500,0B05,FCC0", outClusters:"0019,FCC0", model:"TY0202", manufacturer:"_TZ3000_4ggd8ezp", deviceJoinName: "Bond motion sensor ZX-BS-J11W"]         // https://community.hubitat.com/t/what-driver-to-use-for-this-motion-sensor-zx-bs-j11w-or-ty0202/103953/4
            ],
            deviceJoinName: "Tuya TS0202 Motion Sensor",
            capabilities  : ["motion": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "battery"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],
    
    "TS0202_MOTION_SWITCH": [
            description   : "Tuya Motion Sensor and Scene Switch",
            models        : ["TS0202"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0001,0500,EF00,0000", outClusters:"0019,000A", model:"TS0202", manufacturer:"_TZ3210_cwamkvua", deviceJoinName: "Tuya Motion Sensor and Scene Switch"]
                
            ],
            deviceJoinName: "Tuya Motion Sensor and Scene Switch",
            capabilities  : ["motion": true, "switch": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "battery"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],

    
    
    "TS0601_PIR_PRESENCE"   : [
            description   : "Tuya PIR Human Motion Presence Sensor (Black)",
            models        : ["TS0601"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_9qayzqa8", deviceJoinName: "Smart PIR Human Motion Presence Sensor (Black)"]    // https://www.aliexpress.com/item/1005004296422003.html
            ],
            deviceJoinName: "Tuya PIR Human Motion Presence Sensor",
            capabilities  : ["motion": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "dc"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],
        
    "TS0601_PIR_AIR"      : [    // Human presence sensor AIR (PIR sensor!) - o_sensitivity, v_sensitivity, led_status, vacancy_delay, light_on_luminance_prefer, light_off_luminance_prefer, mode, luminance_level, reference_luminance, vacant_confirm_time
            description   : "Tuya PIR Human Motion Presence Sensor AIR",
            models        : ["TS0601"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_auin8mzr", deviceJoinName: "Tuya PIR Human Motion Presence Sensor AIR"]        // Tuya LY-TAD-K616S-ZB
            ],
            deviceJoinName: "Tuya PIR Human Motion Presence Sensor AIR",
            capabilities  : ["motion": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "dc"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],

    "NONTUYA_MOTION_IAS"   : [
            description   : "Other Motion sensors (IAS)",
            models        : ["TS0202","RH3040"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,0001", outClusters:"0003", model:"ms01", manufacturer:"eWeLink", deviceJoinName: "eWeLink Motion Sensor"],        // for testL 60 seconds re-triggering period!
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,0001", outClusters:"0003", model:"msO1", manufacturer:"eWeLink", deviceJoinName: "eWeLink Motion Sensor"],        // second variant
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,0001", outClusters:"0003", model:"MS01", manufacturer:"eWeLink", deviceJoinName: "eWeLink Motion Sensor"]         // third variant
            ],
            deviceJoinName: "Motion sensor (IAS)",
            capabilities  : ["motion": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "battery"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],
    
    "---"   : [
            description   : "--------------------------------------",
            models        : [],
            fingerprints  : [],
    ],           
    
// ------------------------------------------- mmWave Radars ------------------------------    
    "TS0601_TUYA_RADAR"   : [        // Smart Human presence sensors - illuminance, presence, target_distance; radar_sensitivity; minimum_range; maximum_range; detection_delay; fading_time; CLI; self_test (checking, check_success, check_failure, others, comm_fault, radar_fault)
            description   : "Tuya Human Presence mmWave Radar",
            models        : ["TS0601"],
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
            ],
            deviceJoinName: "Tuya Human Presence Detector",
            capabilities  : ["motion": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "dc"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],

    "TS0601_RADAR_MIR-HE200-TY"   : [        // Human presence sensor radar 'MIR-HE200-TY' - illuminance, presence, occupancy, motion_speed, motion_direction, radar_sensitivity, radar_scene ('default', 'area', 'toilet', 'bedroom', 'parlour', 'office', 'hotel')
            description   : "Tuya Human Presence Sensor MIR-HE200-TY",
            models        : ["TS0601"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_vrfecyku", deviceJoinName: "Tuya Human presence sensor MIR-HE200-TY"]
            ],
            deviceJoinName: "Tuya Human Presence Sensor MIR-HE200-TY",
            capabilities  : ["motion": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "dc"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],     
        
    "TS0601_RADAR_MIR-TY-FALL"   : [         // Human presence sensor radar 'MIR-HE200-TY_fall' - illuminance, presence, occupancy, motion_speed, motion_direction, radar_sensitivity, radar_scene, tumble_switch, fall_sensitivity, tumble_alarm_time, fall_down_status, static_dwell_alarm
            description   : "Tuya Human Presence Sensor MIR-TY-FALL",
            models        : ["TS0601"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_lu01t0zl", deviceJoinName: "Tuya Human presence sensor with fall function"]
            ],
            deviceJoinName: "Tuya Human Presence Sensor MIR-TY-FALL",
            capabilities  : ["motion": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "dc"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],     
    
    "TS0601_BLACK_SQUARE_RADAR"   : [        // // 24GHz Black Square Human Presence Radar w/ LED
            description   : "Tuya Black Square Radar",
            models        : ["TS0601"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_0u3bj3rc", deviceJoinName: "24GHz Black Square Human Presence Radar w/ LED"],
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_v6ossqfy", deviceJoinName: "24GHz Black Square Human Presence Radar w/ LED"],
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_mx6u6l4y", deviceJoinName: "24GHz Black Square Human Presence Radar w/ LED"]
            ],
            deviceJoinName: "24GHz Black Square Human Presence Radar w/ LED",
            capabilities  : ["motion": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "dc"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],
    
    "TS0601_YXZBRB58_RADAR"   : [        // Seller: shenzhenshixiangchuangyeshiyey Manufacturer: Shenzhen Eysltime Intelligent LTD    Item model number: YXZBRB58 
            description   : "Tuya YXZBRB58 Radar",
            models        : ["TS0601"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE204_sooucan5", deviceJoinName: "Tuya Human Presence Detector YXZBRB58"]                      // https://www.amazon.com/dp/B0BYDCY4YN                   
                
            ],
            deviceJoinName: "Tuya Human Presence Detector YXZBRB58",
            capabilities  : ["motion": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "dc"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],    

    "OWON_OCP305_RADAR"   : [
            description   : "OWON OCP305 Radar",
            models        : ["OCP305"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0406", outClusters:"0003", model:"OCP305", manufacturer:"OWON"]
            ],
            deviceJoinName: "OWON OCP305 Radar",
            capabilities  : ["motion": true, "battery": true],
            attributes    : ["healthStatus": "unknown", "powerSource": "dc"],
            configuration : ["battery": false],
            preferences   : [
            ]
    ],
    
    
    "UNKNOWN"             : [
            description   : "Unknown device",
            models        : ["UNKNOWN"],
            deviceJoinName: "Unknown device",
            capabilities  : ["motion": true],
            configuration : ["battery": true],
            attributes    : [],
            batteries     : "unknown"
    ]
    
]    

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
    checkDriverVersion()
    if (state.rxCounter != null) state.rxCounter = state.rxCounter + 1
    setPresent()
    //logDebug "parse (${device.getDataValue('manufacturer')}, ${driverVersionAndTimeStamp()}) descMap = ${zigbee.parseDescriptionAsMap(description)}"
    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) {	
        logDebug "parse: zone status: $description"
        parseIasMessage(description)    // TS0202 Motion sensor
    }
    else if (description?.startsWith('enroll request')) {
        logDebug "parse: enroll request: $description"
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */
        if (settings?.logEnable) log.info "${device.displayName} Sending IAS enroll response..."
        ArrayList<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
        if (settings?.logEnable) log.debug "${device.displayName} enroll response: ${cmds}"
        sendZigbeeCommands( cmds )  
    }    
    else if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (isChattyRadarDistanceReport(descMap) && (settings?.ignoreDistance == true)) {
            //log.trace "settings?.ignoreDistance = ${settings?.ignoreDistance}"
            // do not even log these spammy distance reports ...
            return
        }
        //
        logDebug "parse (${device.getDataValue('manufacturer')}, ${driverVersionAndTimeStamp()}) descMap = ${zigbee.parseDescriptionAsMap(description)}"
        //
        if (descMap.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
            if (descMap.attrInt == 0x0021) {
                getBatteryPercentageResult(Integer.parseInt(descMap.value,16))
            } else if (descMap.attrInt == 0x0020){
                sendBatteryVoltageEvent(Integer.parseInt(descMap.value, 16))
            }
            else {
                if (settings?.logEnable) log.warn "${device.displayName} power cluster not parsed attrint $descMap.attrInt"
            }
        }     
		else if (descMap.cluster == "0400" && descMap.attrId == "0000") {
            def rawLux = Integer.parseInt(descMap.value,16)
            illuminanceEvent( rawLux )
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
        else if (descMap.profileId == "0000") {    // zdo
            parseZDOcommand(descMap)
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "0001") {
            if (settings?.logEnable) log.info "${device.displayName} Tuya check-in (application version is ${descMap?.value})"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "0004") {
            if (settings?.logEnable) log.info "${device.displayName} received device manufacturer ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "0007") {
            //def value = descMap?.value == "00" ? "battery" : descMap?.value == "01" ? "mains" : descMap?.value == "03" ? "battery" : descMap?.value == "04" ? "dc" : "unknown" 
            def powerSourceReported = powerSourceOpts.options[descMap?.value as int]
            logInfo "reported Power source <b>${powerSourceReported}</b> (${descMap?.value})"
            if (is4in1() || isRadar() || isHumanPresenceSensorAIR() ||isBlackSquareRadar() || isBlackPIRsensor())  {     // for radars force powerSource 'dc'
                powerSourceReported = powerSourceOpts.options[04]    // force it to dc !
                logDebug "forcing the powerSource to <b>${powerSourceReported}</b>"
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
                //log.trace "str = ${str}"
                device.updateSetting("keepTime", [value: value.toString(), type: 'enum'])                
            }
            else {
                if (settings?.logEnable) log.warn "${device.displayName} Zone status attribute ${descMap?.attrId}: NOT PROCESSED ${descMap}" 
            }
        } // if IAS read attribute response
        else if (descMap?.clusterId == "0500" && descMap?.command == "04") {    //write attribute response (IAS)
            if (settings?.logEnable) log.debug "${device.displayName} IAS write attribute response is ${descMap?.data[0] == '00' ? 'success' : '<b>FAILURE</b>'}"
        } 
        else if (descMap?.command == "04") {    // write attribute response (other)
            if (settings?.logEnable) log.debug "${device.displayName} write attribute response is ${descMap?.data[0] == '00' ? 'success' : '<b>FAILURE</b>'}"
        } 
        else if (descMap?.command == "0B") {    // default command response
            String commandId = descMap.data[0]
            String status = "0x${descMap.data[1]}"
            logDebug "zigbee default command response cluster: ${clusterLookup(descMap.clusterInt)} command: 0x${commandId} status: ${descMap.data[1]== '00' ? 'success' : '<b>FAILURE</b>'} (${status})"
        } 
        else if (descMap?.command == "00" && descMap?.clusterId == "8021" ) {    // bind response
            if (settings?.logEnable) log.debug "${device.displayName }bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1]=="00" ? 'success' : '<b>FAILURE</b>'})"
        } 
        else {
            if (settings?.logEnable) log.debug "${device.displayName} <b> NOT PARSED </b> : descMap = ${descMap}"
        }
    } // if 'catchall:' or 'read attr -'
    else {
        if (settings?.logEnable) log.debug "${device.displayName} <b> UNPROCESSED </b> description = ${description} descMap = ${zigbee.parseDescriptionAsMap(description)}"
    }
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
        //if (settings?.logEnable) log.trace "${device.displayName} now is: ${now()}"  // KK TODO - convert to Date/Time string!        
        if (settings?.logEnable) log.debug "${device.displayName} sending time data : ${cmds}"
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
        if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response
        String clusterCmd = descMap?.data[0]
        def status = descMap?.data[1]            
        if (settings?.logEnable) log.debug "${device.displayName} device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
        if (status != "00") {
            if (settings?.logEnable) log.warn "${device.displayName} ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                
        }
    } 
    else if ((descMap?.clusterInt==CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02"|| descMap?.command == "06"))
    {
        def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
        def dp = zigbee.convertHexToInt(descMap?.data[2])                // "dp" field describes the action/message of a command frame
        def dp_id = zigbee.convertHexToInt(descMap?.data[3])             // "dp_identifier" is device dependant
        def fncmd = getTuyaAttributeValue(descMap?.data)                 // 
        logDebug "Tuya cluster: dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
        switch (dp) {
            case 0x01 : // motion for 2-in-1 TS0601 (_TZE200_3towulqd) and presence stat? for all radars, including isHumanPresenceSensorAIR and isBlackSquareRadar
                logDebug "(DP=0x01) motion event fncmd = ${fncmd}"
                // 03/29/2023 settings.invertMotion handles the 2-in-1 TS0601 wierness ..
                handleMotion(motionActive = fncmd)
                break
            case 0x02 :
                if (isRadar()) {    // including HumanPresenceSensorScene and isHumanPresenceSensorFall
                    if (settings?.logEnable == true || settings?.radarSensitivity != safeToInt(device.currentValue("radarSensitivity"))) {logInfo "received Radar sensitivity : ${fncmd}"} //else {log.warn "skipped ${settings?.radarSensitivity} == ${fncmd as int}"}
                    device.updateSetting("radarSensitivity", [value:fncmd as int , type:"number"])
                    sendEvent(name : "radarSensitivity", value : fncmd as int)
                }
                else {
                    logWarn "${device.displayName} non-radar event ${dp} fncmd = ${fncmd}"
                }
                break
            case 0x03 :
                if (isRadar()) {
                    if (settings?.logEnable == true || (settings?.minimumDistance != safeToDouble(device.currentValue("minimumDistance")))) {logInfo "received Radar Minimum detection distance : ${fncmd/100} m"}
                    device.updateSetting("minimumDistance", [value:fncmd/100, type:"decimal"])
                    sendEvent(name : "minimumDistance", value : fncmd/100, unit : "m")
                }
                else {        // also battery level STATE for TS0202 ? 
                    logWarn "non-radar event ${dp} fncmd = ${fncmd}"
                }
                break
            case 0x04 :    // maximumDistance for radars or Battery level for _TZE200_3towulqd 
                if (isRadar()) {
                    if (settings?.logEnable == true || (settings?.maximumDistance != safeToDouble(device.currentValue("maximumDistance")))) {logInfo "received Radar Maximum detection distance : ${fncmd/100} m"}
                    device.updateSetting("maximumDistance", [value:fncmd/100 , type:"decimal"])
                    sendEvent(name : "maximumDistance", value : fncmd/100, unit : "m")
                }
                else {        // also battery level for TS0202 ; battery1 for Fantem 4-in-1 (100% or 0% )
                    logDebug "Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    handleTuyaBatteryLevel( fncmd )                    
                }
                break
            case 0x05 :     // tamper alarm for TS0202 4-in-1
                if (isYXZBRB58radar()) {
                    logDebug "YXZBRB58 radar unknown event DP=0x05 fncmd = ${fncmd}"  
                }
                else {
                    def value = fncmd==0 ? 'clear' : 'detected'
                    logInfo "${device.displayName} tamper alarm is ${value} (dp=05,fncmd=${fncmd})"
                 	sendEvent(name : "tamper",	value : value, isStateChange : true)
                }
                break
            case 0x06 :
                if (isRadar()) {
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
                if (isRadar()) {
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
            case 0x0C : // (12)
                illuminanceEventLux( fncmd )    // illuminance for TS0601 2-in-1
                break
            case 0x19 : // (25) 
                logDebug "Motion Switch battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                handleTuyaBatteryLevel( fncmd )
                break
            case 0x65 :    // (101)
                if (isBlackSquareRadar() || isYXZBRB58radar()) {    // presence time in minutes (must be the first check!)
                    existanceTimeEvent(fncmd)
                }
                else if (isRadar()) {
                    def value = fncmd / 10
                    if (settings?.logEnable == true || (settings?.detectionDelay) != safeToDouble(device.currentValue("detectionDelay")) ) {logInfo "received Radar detection delay : ${value} seconds (${fncmd})"}
                    device.updateSetting("detectionDelay", [value:value , type:"decimal"])
                    sendEvent(name : "detectionDelay", value : value)
                }
                else if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported V_Sensitivity <b>${vSensitivityOptions[fncmd.toString()]}</b> (${fncmd})"
                    device.updateSetting("vSensitivity", [type:"enum", value: fncmd.toString()])
                }
                else if (isMotionSwitch()) {    // button 'single': 0, 'hold': 1, 'double': 2
                    def action = fncmd == 2 ? 'held' : fncmd == 1 ? 'doubleTapped' : fncmd == 0 ? 'pushed' : 'unknown'
                    logInfo "button 1 was $action"
                    sendEvent(name: action, value: '1', data: [buttonNumber: 1], descriptionText: "button 1 was pushed", isStateChange: true, type: 'physical')
                }
                else {     //  Tuya 3 in 1 (101) -> motion (ocupancy) + TUYATEC
                    if (settings?.logEnable) log.debug "${device.displayName} motion event 0x65 fncmd = ${fncmd}"
                    handleMotion(motionActive=fncmd)
                }
                break            
            case 0x66 :     // (102)
                if (isBlackSquareRadar() || isYXZBRB58radar()) {    // non-presence time in minutes (must be the first check!)
                    leaveTimeEvent(fncmd)
                }
                else if (isRadar()) {
                    def value = fncmd / 10
                    if (settings?.logEnable == true || (settings?.fadingTime) != safeToDouble(device.currentValue("fadingTime")) ) {logInfo "received Radar fading time : ${value} seconds (${fncmd})"}
                    device.updateSetting("fadingTime", [value:value , type:"decimal"])
                    sendEvent(name : "fadingTime", value : value, unit : "s")
                }                    
                else if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported O_Sensitivity <b>${oSensitivityOptions[fncmd.toString()]}</b> (${fncmd})"
                    device.updateSetting("oSensitivity", [type:"enum", value: fncmd.toString()])
                }
                else if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) {                     // trsfMotionState: (102) for TuYa Radar Sensor with fall function
                    if (settings?.logEnable) log.info "${device.displayName} (0x66) motion state is ${fncmd}"
                    handleMotion(motionActive=fncmd)
                }
                else if (is4in1()) {    // // case 102 //reporting time intervl for 4 in 1 
                    logInfo "4-in-1 reporting time interval is ${fncmd} minutes"
                    device.updateSetting("reportingTime4in1", [value:fncmd as int , type:"number"])
                }
                else if (isMotionSwitch()) {
                    illuminanceEventLux( fncmd )    // 0 = 'dark' 1 = 'bright'
                }
                else {     // battery level for 3 in 1;  
                    if (settings?.logEnable) log.debug "${device.displayName} Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    handleTuyaBatteryLevel( fncmd )                    
                }
                break
            case 0x67 :     // (103)
                if (isRadar()) {
                    if (settings?.logEnable) log.info "${device.displayName} Radar DP_103 (Debug CLI) is ${fncmd}"
                }
                else if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported <b>Vacancy Delay</b> ${fncmd} s"
                    device.updateSetting("vacancyDelay", [value:fncmd as int , type:"number"])
                }
                else if (isBlackSquareRadar()) {
                    if (settings?.logEnable) log.info "${device.displayName} BlackSquareRadar Indicator Light is ${blackRadarLedOptions[fncmd.toString()]} (${fncmd})"
                    //device.updateSetting("indicatorLight", [type:"enum", value: fncmd.toString()])              // no need to update the preference every 4 seconds!          
                }
                else if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) { // trsfIlluminanceLux for TuYa Radar Sensor with fall function
                    illuminanceEventLux( fncmd )
                }
                else {        //  Tuya 3 in 1 (103) -> tamper            // TUYATEC- Battery level ????
                    def value = fncmd==0 ? 'clear' : 'detected'
                    if (settings?.txtEnable) log.info "${device.displayName} tamper alarm is ${value} (dp=67,fncmd=${fncmd})"
                	sendEvent(name : "tamper",	value : value, isStateChange : true)
                }
                break            
            case 0x68 :     // (104)
                if (isRadar()) {
                    illuminanceEventLux( fncmd )
                }
                else if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported Detection Mode <b>${detectionModeOptions[fncmd.toString()]}</b> (${fncmd})"
                    device.updateSetting("detectionMode", [type:"enum", value: fncmd.toString()])
                }
                else if (isHumanPresenceSensorScene()) { // detection data  for TuYa Radar Sensor with scene
                    if (settings?.logEnable) log.info "${device.displayName} radar detection data is ${fncmd}"
                }
                else if (is4in1()) {    // case 104: // 0x68 temperature compensation
                    def val = fncmd;
                    // for negative values produce complimentary hex (equivalent to negative values)
                    if (val > 4294967295) val = val - 4294967295;                    
                    logInfo "4-in-1 temperature calibration is ${val / 10.0}"
                }
                else {    //  Tuya 3 in 1 (104) -> temperature in °C
                    temperatureEvent( fncmd / 10.0 )
                }
                break            
            case 0x69 :    // 105 
                if (isYXZBRB58radar()) {
                    logDebug "YXZBRB58 radar unknown event DP=0x69 fncmd = ${fncmd}"  
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
                    if (settings?.txtEnable) log.info "${device.displayName} Tumble Switch (dp=69) is ${fncmd}"
                }
                else if (is4in1()) {    // case 105:// 0x69 humidity calibration (compensation)
                    def val = fncmd;
                    if (val > 4294967295) val = val - 4294967295;                    
                    logInfo "4-in-1 humidity calibration is ${val}"                
                }
                else {    //  Tuya 3 in 1 (105) -> humidity in %
                    humidityEvent (fncmd)
                }
                break
            case 0x6A : // 106
                if (isHumanPresenceSensorAIR()) {
                    //if (settings?.logEnable) log.info "${device.displayName} reported Reference Luminance ${fncmd}"
                    illuminanceEventLux( fncmd )
                }
                else if (isHumanPresenceSensorFall()) {
                    // trsfTumbleAlarmTime
                    if (settings?.txtEnable) log.info "${device.displayName} Tumble Alarm Time (dp=6A) is ${fncmd}"
                }
                else if (is4in1()) {    // case 106: // 0x6a lux calibration (compensation)
                    def val = fncmd;
                    if (val > 4294967295) val = val - 4294967295;                    
                    logInfo "4-in-1 lux calibration is ${val}"                
                }
                else {    //  Tuya 3 in 1 temperature scale Celsius/Fahrenheit
                    if (settings?.logEnable) log.info "${device.displayName} Temperature Scale is: ${fncmd == 0 ? 'Celsius' : 'Fahrenheit'} (DP=0x6A fncmd = ${fncmd})"  
                }
                break
            case 0x6B : // 107
                if (isHumanPresenceSensorAIR()) {
                    if (settings?.txtEnable) log.info "${device.displayName} reported Light On Luminance Preference ${fncmd} Lux"
                }
                else if (is4in1()) {    //  Tuya 4 in 1 (107) -> temperature in °C
                    temperatureEvent( fncmd / 10.0 )
                }
                else if (is3in1()) { // 3in1
                    logDebug "Min Temp is: ${fncmd} (DP=0x6B)"  
                }
                else {
                    logDebug "(UNEXPECTED) : ${fncmd} (DP=0x6B)"  
                }
                break            
            case 0x6C : //  108 Tuya 4 in 1 -> humidity in %
                if (isHumanPresenceSensorAIR()) {
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
            case 0x6D :    // 109
                if (isHumanPresenceSensorAIR()) {
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
                else if (isRadar()){
                    if (settings?.txtEnable) log.info "${device.displayName} radar LED status is ${fncmd}"                
                }
                else if (is4in1()) {
                    if (settings?.logEnable) log.debug "${device.displayName} Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    handleTuyaBatteryLevel( fncmd )
                }
                else {  //  3in1
                    if (settings?.logEnable) log.info "${device.displayName} Max Humidity is: ${fncmd} (DP=0x6E)"  
                }
                break 
            case 0x6F : // (111) Tuya 4 in 1: // 0x6f led enable
                if (is4in1()) { 
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
                else if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) {    // trsfScene
                    if (settings?.txtEnable) log.info "${device.displayName} Scene (dp=70) is ${fncmd}"
                }
                else {
                    if (settings?.logEnable) log.info "${device.displayName} Humidity alarm switch is: ${fncmd} (DP=0x6F)"  
                }
                break
            case 0x71 :
                if (is4in1()) {   // case 113: 0x71 unknown  ( ENUM)
                    if (settings?.logEnable) log.info "${device.displayName} <b>UNKNOWN</b> (0x71 reporting enable?) DP=0x71 fncmd = ${fncmd}"  
                }
                else {    // 3in1 - Alarm Type
                    if (settings?.txtEnable) log.info "${device.displayName} Alarm type is: ${fncmd}"                
                }
                break
            case 0x72 : // (114)
                if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) {    // trsfMotionDirection
                    if (settings?.txtEnable) log.info "${device.displayName} radar motion direction is ${fncmd}"                
                }
                else {
                    if (settings?.txtEnable) log.warn "${device.displayName} non-radar motion direction 0x72 fncmd = ${fncmd}"
                }
                break
            case 0x73 : // (115)
                if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) {    // trsfMotionSpeed
                    if (settings?.txtEnable) log.info "${device.displayName} radar motion speed is ${fncmd}"                
                }
                else {
                    if (settings?.txtEnable) log.warn "${device.displayName} non-radar motion speed 0x73 fncmd = ${fncmd}"
                }
                break
            case 0x74 : // (116)
                if (isHumanPresenceSensorFall()) {    // trsfFallDownStatus
                    if (settings?.txtEnable) log.info "${device.displayName} radar fall down status is ${fncmd}"                
                }
                else {
                    if (settings?.txtEnable) log.warn "${device.displayName} non-radar fall down status 0x74 fncmd = ${fncmd}"
                }
                break
            case 0x75 : // (117)
                if (isHumanPresenceSensorFall()) {    // trsfStaticDwellAlarm
                    if (settings?.txtEnable) log.info "${device.displayName} radar static dwell alarm is ${fncmd}"                
                }
                else {
                    if (settings?.txtEnable) log.warn "${device.displayName} non-radar static dwell alarm 0x75 fncmd = ${fncmd}"
                }
                break
            case 0x76 : // (118)
                if (isHumanPresenceSensorFall()) {
                    if (settings?.txtEnable) log.info "${device.displayName} radar fall sensitivity is ${fncmd}"
                }
                else if (isBlackPIRsensor()) {
                    if (settings?.logEnable) log.debug "${device.displayName} reported unknown parameter dp=${dp} value=${fncmd}"
                }
                else {
                    if (settings?.txtEnable) log.warn "${device.displayName} non-radar fall sensitivity  0x76 fncmd = ${fncmd}"
                }
                break
            case 0x77 : // (119)
                //if (isBlackPIRsensor()) {
                    if (settings?.logEnable) log.info "${device.displayName} (0x77) motion state is ${fncmd}"
                    handleMotion(motionActive=fncmd)
                //}
                break
            case 0x93 : // (147)
            case 0xA8 : // (168)
            case 0xA4 : // (164)
            case 0x8C : // (140)
            case 0x7A : // (122)
            case 0xAD : // (173)
            case 0xAE : // (174)
            case 0xAA : // (170)
                if (settings?.logEnable) log.debug "${device.displayName} reported unknown parameter dp=${dp} value=${fncmd}"
                break
            case 0x8D : // (141)
                //if (isBlackPIRsensor()) {
                    def strMotionType = blackSensorMotionTypeOptions[fncmd.toString()]
                    if (strMotionType == null) strMotionType = "???"
                    if (settings?.txtEnable) log.debug "${device.displayName} motion type reported is ${strMotionType} (${fncmd})"
                    sendEvent(name : "motionType", value : strMotionType, type: "physical")
                //}
                break
            default :
                if (settings?.logEnable) log.warn "${device.displayName} <b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                break
        }
    } // Tuya commands '01' and '02'
    else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "11" ) {
        // dont'know what command "11" means, it is sent by the square black radar when powered on. Will use it to restore the LED on/off configuration :) 
        if (settings?.logEnable) log.debug "${device.displayName} Tuya <b>descMap?.command = ${descMap?.command}</b> descMap.data = ${descMap?.data}" 
        if (isBlackSquareRadar())  {
            if (settings?.indicatorLight != null) {
                ArrayList<String> cmds = []
                def value = safeToInt(indicatorLight.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2) 
                cmds += sendTuyaCommand("67", DP_TYPE_BOOL, dpValHex)
                if (settings?.logEnable) log.info "${device.displayName} restoring indicator light to : ${blackRadarLedOptions[value.toString()]} (${value})"  
                sendZigbeeCommands( cmds ) 
            }
        }
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} <b>NOT PROCESSED</b> Tuya <b>descMap?.command = ${descMap?.command}</b> cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
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
    if (settings?.logEnable) log.debug "pareseIasReport: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}"
    def zs = new ZoneStatus(Integer.parseInt(descMap?.value, 16))
    //log.trace "zs = ${zs}"
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
        //if (settings?.logEnable) log.trace "zs = $zs"
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
            state.motionStarted = now()
        }
    }
    else {
        if (device.currentState('motion')?.value == "inactive") {
            if (settings?.logEnable) log.debug "${device.displayName} ignored motion inactive event after ${getSecondsInactive()}s"
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
    if (isBlackSquareRadar() && device.currentValue("motion", true) == "active" && (motionActive as boolean) == true) {
        return    // the black square radar sends 'motion active' every 4 seconds!
    }
    else {
        //log.trace "device.currentValue('motion', true) = ${device.currentValue('motion', true)} motionActive = ${motionActive}"
    }
    if (txtEnable) log.info "${device.displayName} ${descriptionText}"
	sendEvent (
			name			: 'motion',
			value			: motionActive ? 'active' : 'inactive',
            //isStateChange   : true,
            type            : isDigital == true ? "digital" : "physical",
			descriptionText : descriptionText
	)
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
    if (state.motionStarted) {
        return Math.round((now() - state.motionStarted)/1000)
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
}

def illuminanceEvent( rawLux ) {
	def lux = rawLux > 0 ? Math.round(Math.pow(10,(rawLux/10000))) : 0
    illuminanceEventLux( lux as Integer) 
}

def illuminanceEventLux( lux ) {
    if (device.currentValue("illuminance", true) == null ||  Math.abs(safeToInt(device.currentValue("illuminance")) - (lux as int)) >= safeToInt(settings?.luxThreshold)) {
        sendEvent("name": "illuminance", "value": lux, "unit": "lx", "type": "physical", "descriptionText": "Illuminance is ${lux} Lux")
        logInfo "Illuminance is ${lux} Lux"
    }
    else {
        logDebug "ignored illuminance event ${lux} lux - change is less than ${safeToInt(settings?.luxThreshold)} lux threshold!"
    }
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
    if (state != null && state == 'unknown' ) {
        sendEvent(name : "powerSource",	value : "unknown", descriptionText: "device is OFFLINE", type: "digital")
    }
    else if (state != null ) {
        sendEvent(name : "powerSource",	value : state, descriptionText: "device is back online", type: "digital")
    }
    else {
        if (is4in1() || isRadar() || isHumanPresenceSensorAIR() || isBlackSquareRadar() || isBlackPIRsensor() || isOWONRadar()) {
            sendEvent(name : "powerSource",	value : "dc", descriptionText: "device is back online", type: "digital")
        }
        else {
            sendEvent(name : "powerSource",	value : "battery", descriptionText: "device is back online", type: "digital")
        }
    }
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
    ArrayList<String> cmds = []
    
    logInfo "Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} <b>deviceProfile=${state.deviceProfile}</b>"
    logInfo "Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(86400, logsOff, [overwrite: true])    // turn off debug logging after 24 hours
        logInfo "Debug logging is will be turned off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }

    if (settings?.forcedProfile != null) {
        logDebug "state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${getProfileKey(settings?.forcedProfile)}"
        if (getProfileKey(settings?.forcedProfile) != state.deviceProfile) {
            logWarn "changing the device profile from ${state.deviceProfile} to ${getProfileKey(settings?.forcedProfile)}"
            state.deviceProfile = getProfileKey(settings?.forcedProfile)
            logInfo "press F5 to refresh the page"
        }
    }
    else {
        logDebug "forcedProfile is not set"
    }
    
    if (true) {    // an configurable device parameter was changed
        //    LED enable
        if (true) {
            if (is4in1()) {
                logDebug "4-in-1: changing ledEnable to : ${settings?.ledEnable }"                
                cmds += sendTuyaCommand("6F", DP_TYPE_BOOL, settings?.ledEnable == true ? "01" : "00")
                logDebug "4-in-1: changing reportingTime4in1 to : ${settings?.reportingTime4in1} minutes"                
                cmds += sendTuyaCommand("66", DP_TYPE_VALUE, zigbee.convertToHexString(settings?.reportingTime4in1 as int, 8))
            }
        }
        // sensitivity
        if (true) {    
            if (isRadar()) { 
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
                    if (settings?.logEnable) log.warn "${device.displayName} changing TS0601 sensitivity to : ${val}"                
                }
                else if (isIAS()) {
                    def val = sensitivityNew
                    if (val != null) {
                        logDebug "changing IAS sensitivity to : ${sensitivityOpts.options[val]} (${val})"
                        cmds += sendSensitivityIAS(val)
                    }
                }
            }
        }
        // keep time
        if (true) {    
            if (isRadar()) {
                // do nothing
            }
            else if (isTS0601_PIR()) {
                def val = settings?.keepTime as int
                //log.trace "keepTime=${keepTime} val=${val}"
                cmds += sendTuyaCommand("0A", DP_TYPE_ENUM, zigbee.convertToHexString(val as int, 2))    // was 8
                if (settings?.logEnable) log.warn "${device.displayName} changing TS0601 Keep Time to : ${val}"                
            }
            else if (isIAS()) {
                if (settings?.keepTime != null) {
                    //log.trace "settings?.keepTime = ${settings.keepTime as int}"
                    cmds += sendKeepTimeIAS( settings?.keepTime )
                    logDebug "changing IAS Keep Time to : ${keepTime4in1Opts.options[settings?.keepTime as int]} (${settings?.keepTime})"                
                }
            }
        }
        // 
        if (true) {    
            if (isRadar()) { 
                cmds += setRadarDetectionDelay( settings?.detectionDelay )        // radar detection delay
                cmds += setRadarFadingTime( settings?.fadingTime )                // radar fading time
                cmds += setRadarMinimumDistance( settings?.minimumDistance )      // radar minimum distance
                cmds += setRadarMaximumDistance( settings?.maximumDistance )      // radar maximum distance
            }
        }
        //
        if (isRadar()) {
            if (settings?.ignoreDistance == true ) 
                device.deleteCurrentState('distance')
        }
        //
        if (isHumanPresenceSensorAIR()) {
            if (vacancyDelay != null) {
                def val = settings?.vacancyDelay
                cmds += sendTuyaCommand("67", DP_TYPE_VALUE, zigbee.convertToHexString(val as int, 8))
                if (settings?.logEnable) log.debug "${device.displayName} setting Sensor AIR vacancy delay : ${val}"                
            }
            if (ledStatusAIR != null && ledStatusAIR != "99") {
                def value = safeToInt(ledStatusAIR.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2)
                cmds += sendTuyaCommand("6E", DP_TYPE_ENUM, dpValHex)
                if (settings?.logEnable) log.debug "${device.displayName} setting Sensor AIR LED status : ${ledStatusOptions[value.toString()]} (${value})"                
            }
            if (detectionMode != null && detectionMode != "99") {
                def value = safeToInt(detectionMode.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2)
                cmds += sendTuyaCommand("68", DP_TYPE_ENUM, dpValHex)
                if (settings?.logEnable) log.debug "${device.displayName} setting Sensor AIR detection mode : ${detectionModeOptions[value.toString()]} (${value})"                
            }
            if (vSensitivity != null) {
                def value = safeToInt(vSensitivity.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2)
                cmds += sendTuyaCommand("65", DP_TYPE_ENUM, dpValHex)
                if (settings?.logEnable) log.debug "${device.displayName} setting Sensor AIR v-sensitivity : ${vSensitivityOptions[value.toString()]} (${value})"                
            }
            if (oSensitivity != null) {
                def value = safeToInt(oSensitivity.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2)
                cmds += sendTuyaCommand("66", DP_TYPE_ENUM, dpValHex)
                if (settings?.logEnable) log.debug "${device.displayName} setting Sensor AIR o-sensitivity : ${oSensitivityOptions[value.toString()]} (${value})"                
            }
        }
        if (isBlackPIRsensor()) {
            if (inductionTime != null) {
                def val = settings?.inductionTime
                cmds += sendTuyaCommand("66", DP_TYPE_VALUE, zigbee.convertToHexString(val as int, 8))
                if (settings?.logEnable) log.debug "${device.displayName} setting induction time to : ${val}"                
            }
            if (targetDistance != null) {
                def value = safeToInt(targetDistance.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2)
                cmds += sendTuyaCommand("69", DP_TYPE_ENUM, dpValHex)
                if (settings?.logEnable) log.debug "${device.displayName} setting target distance to : ${blackSensorDistanceOptions[value.toString()]} (${value})"                
            }
        }
        if (isBlackSquareRadar()) {
            if (indicatorLight != null) {
                def value = safeToInt(indicatorLight.value)
                def dpValHex = zigbee.convertToHexString(value as int, 2) 
                cmds += sendTuyaCommand("67", DP_TYPE_BOOL, dpValHex)
                if (settings?.logEnable) log.debug "${device.displayName} setting indicator light to : ${blackRadarLedOptions[value.toString()]} (${value})"  
            }
        }
    }
    //    
    if (cmds != null) {
        if (settings?.logEnable) log.debug "${device.displayName} sending the changed AdvancedOptions"
        sendZigbeeCommands( cmds )  
    }
    if (settings?.txtEnable) log.info "${device.displayName} preferencies updates are sent to the device..."
}

def ping() {
    logInfo "ping() is not implemented" 
}

def refresh() {
    logInfo "refresh()..."
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
    sendZigbeeCommands( cmds ) 
}

def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

def checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        if (txtEnable==true) log.debug "${device.displayName} updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        unschedule('pollPresence')    // now replaced with deviceHealthCheck
        scheduleDeviceHealthCheck()
        updateTuyaVersion()
        initializeVars( fullInit = false ) 
        state.driverVersion = driverVersionAndTimeStamp()
        
        if (state.lastPresenceState != null) state.remove('lastPresenceState')    // removed in version 1.0.6 
        if (state.hashStringPars != null)    state.remove('hashStringPars')       // removed in version 1.1.0
        if (state.lastBattery != null)       state.remove('lastBattery')          // removed in version 1.3.0
        
        if (isRadar() || isHumanPresenceSensorAIR()) {
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

// called by initialize() button
void initializeVars( boolean fullInit = false ) {
    logInfo "${device.displayName} InitializeVars( fullInit = ${fullInit} )..."
    if (fullInit == true) {
        state.clear()
        state.driverVersion = driverVersionAndTimeStamp()
        state.motionStarted = now()
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
    if (fullInit == true || settings.advancedOptions == null) {
        if (isRadar() || isHumanPresenceSensorAIR()) {
            device.updateSetting("advancedOptions", true)
        }
        else {
            device.updateSetting("advancedOptions", false)
        }
    }
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
    if (fullInit == true || settings.luxThreshold == null) device.updateSetting("luxThreshold", [value:1, type:"number"])
    if (fullInit == true || settings.parEvents == null) device.updateSetting("parEvents", true)
    if (fullInit == true || settings.invertMotion == null) device.updateSetting("invertMotion", is2in1() ? true : false)
    if (fullInit == true || settings.reportingTime4in1 == null) device.updateSetting("reportingTime4in1", [value:DEFAULT_REPORTING_4IN1, type:"number"])
    
    
    
    //
    if (fullInit == true) sendEvent(name : "powerSource",	value : "?", isStateChange : true)
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
    state.motionStarted = now()
    ArrayList<String> cmds = []
    cmds += tuyaBlackMagic()    
    
    if (isIAS() ) {
        cmds += zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
        if (settings?.logEnable) log.debug "Enrolling IAS device: ${cmds}"
    }
    else if (isOWONRadar()) {
        cmds += "delay 200"
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}"    // OWON motion/occupancy cluster
    }
    else if (!(isRadar() || is2in1())) {    // skip the binding for all the radars!                // TODO: check EPs !!!
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
    if (settings?.logEnable) log.debug "${device.displayName} <b>sendTuyaCommand</b> = ${cmds}"
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
    if (settings?.logEnable) {log.debug "${device.displayName} <b>sendZigbeeCommands</b> (cmd=$cmd)"}
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
    if (settings?.logEnable) log.debug "${device.displayName} batteryVoltage = ${(double)rawValue / 10.0} V"
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
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}"
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
    cmds += zigbee.writeAttribute(0x0500, 0x0013, DataType.UINT8, sensitivityLevel, [:], delay=200)
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
    cmds += zigbee.writeAttribute(0x0500, 0xF001, DataType.UINT8, keepTimeVal, [:], delay=200)
    logDebug "${device.displayName} sending IAS Keep Time : ${str} (${keepTimeVal})"
    // only prepare the cmds here!
    return cmds
}


// called when any event was received from the Zigbee device in parse() method..
def setPresent() {
    if ((device.currentValue("healthStatus") ?: "unknown") != "online") {
        sendHealthStatusEvent("online")
        powerSourceEvent() // sent ony once now - 2023-01-31
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


def setParSelectHelp( val ) {
    logWarn "setPar: select one of the parameters in this list depending on your device"             
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

def setRadarDetectionDelay( val ) {
    if (isRadar()) { 
        def value = ((val as double) * 10.0) as int
        logDebug "changing radar detection delay to ${val} seconds (raw=${value})"                
        return sendTuyaCommand("65", DP_TYPE_VALUE, zigbee.convertToHexString(value, 8))
    }
}

def setRadarFadingTime( val ) {
    if (isRadar()) { 
        def value = ((val as double) * 10.0) as int
        logDebug "changing radar fading time to ${val} seconds (raw=${value})"                
        return sendTuyaCommand("66", DP_TYPE_VALUE, zigbee.convertToHexString(value, 8))
    }
}

def setRadarMinimumDistance( val ) {
    if (isRadar()) { 
        int value = ((val as double) * 100.0) as int
        logDebug "changing radar minimum distance to ${val} m (raw=${value})"                
        return sendTuyaCommand("03", DP_TYPE_VALUE, zigbee.convertToHexString(value as int, 8))
    }
}

def setRadarMaximumDistance( val ) {
    if (isRadar()) { 
        int value = ((val as double) * 100.0) as int
        logDebug "changing radar maximum distance to : ${val} m (raw=${value})"                
        return sendTuyaCommand("04", DP_TYPE_VALUE, zigbee.convertToHexString(value as int, 8))
    }
}     

def setRadarSensitivity( val ) {
    if (isRadar()) { 
        logDebug "changing radar sensitivity to : ${val}"                
        return sendTuyaCommand("02", DP_TYPE_VALUE, zigbee.convertToHexString(val as int, 8))
    }
}


def setPar( par=null, val=null )
{
    logInfo "setting parameter ${par} to ${val}"
    ArrayList<String> cmds = []
    def value
    Boolean validated = false
    if (par == null || !(par in (settableParsMap.keySet() as List))) {
        logWarn "setPar: parameter <b>${par}</b> must be one of these : ${settableParsMap.keySet() as List}"
        return
    }
    value = settableParsMap[par]?.type == "number" ? safeToInt(val, -1) : safeToDouble(val, -1.0)
    if (value >= settableParsMap[par]?.min && value <= settableParsMap[par]?.max) validated = true
    if (validated == false && settableParsMap[par]?.min != null && settableParsMap[par]?.max != null) {
        log.warn "setPar: parameter <b>par</b> value <b>${val}</b> must be within ${settableParsMap[par]?.min} and  ${settableParsMap[par]?.max} "
        return
    }
    //
    def func
    try {
        func = settableParsMap[par]?.function
        def type = settableParsMap[par]?.type
        device.updateSetting("$par", [value:value, type:type])
        cmds = "$func"(value)
    }
    catch (e) {
        logWarn "Exception caught while processing <b>$func</b>(<b>$val</b>)"
        return
    }

    logDebug "executed <b>$func</b>(<b>$val</b>)"
    sendZigbeeCommands( cmds )
}

def updateTuyaVersion() {
    def application = device.getDataValue("application") 
    if (application != null) {
        def ver = zigbee.convertHexToInt(application)
        def str = ((ver&0xC0)>>6).toString() + "." + ((ver&0x30)>>4).toString() + "." + (ver&0x0F).toString()
        if (device.getDataValue("tuyaVersion") != str) {
            device.updateDataValue("tuyaVersion", str)
            logInfo "tuyaVersion set to $str"
        }
    }
    else {
        return null
    }
}

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

def getProfileKey(String valueStr) {
    def key = null
    deviceProfilesV2.each {  profileName, profileMap ->
        if (profileMap.description.equals(valueStr)) {
            key = profileName
        }
    }
    return key
}

def testParse(description) {
    logWarn "testParse: ${description}"
    parse(description)
    log.trace "---end of testParse---"
}

def test( val ) {
    zigbee.command(0xEF00, 0x07, "00")
}

