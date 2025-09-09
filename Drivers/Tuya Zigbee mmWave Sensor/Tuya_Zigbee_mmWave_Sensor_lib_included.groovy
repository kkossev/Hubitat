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
 *                                   TODO: change offlineCheck to 15 minutes
*/

static String version() { "4.0.0" }
static String timeStamp() {"2025/09/09 7:00 AM"}

@Field static final Boolean _DEBUG = false           // debug logging
@Field static final Boolean _TRACE_ALL = false      // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true 

@Field static final String DEFAULT_PROFILES_FILENAME = "deviceProfilesV4_mmWave.json"
//@Field static final String DEFAULT_PROFILES_FILENAME = "deviceProfilesV4_mmWave.minimal.json"
//@Field static final String DEFAULT_PROFILES_FILENAME = "deviceProfilesV4_mmWave_TS0601_TUYA_RADAR.json"
//@Field static final String DEFAULT_PROFILES_FILENAME = "deviceProfilesV4_mmWave-test.json"

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput







deviceType = "mmWaveSensor"
@Field static final String DEVICE_TYPE = "mmWaveSensor"

@Field static  Map deviceProfilesV4 = [:]
@Field static  boolean profilesLoading = false
@Field static  boolean profilesLoaded = false
@Field static  Map deviceFingerprintsV4 = [:]
@Field static  Map currentProfilesV4 = [:]  // Key: device?.deviceNetworkId, Value: complete profile data

@Field static String defaultGitHubURL = 'https://raw.githubusercontent.com/kkossev/Hubitat/deviceProfileV4/Drivers/Tuya%20Zigbee%20mmWave%20Sensor/deviceProfilesV4_mmWave.json'

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
        command 'updateFromGitHub', [[name: "url", type: "STRING", description: "GitHub URL (optional)", defaultValue: ""]]
        if (_DEBUG) {
            command 'test', [[name: "test", type: "STRING", description: "test", defaultValue : ""]] 
            // testParse is defined in the common library
            // tuyaTest is defined in the common library
            command 'cacheTest', [[name: "action", type: "ENUM", description: "Cache action", constraints: ["Info", "Initialize", "ReconstructedFingerprints", "CurrentProfilesV4", "currentProfilesV4 Dump", "LoadCurrentProfile", "DisposeV3", "TestFileRead", "Clear"], defaultValue: "Info"]]
        }
        
        // Generate fingerprints from optimized deviceFingerprintsV4 map (fast access!)
        // Uses pre-loaded fingerprint data instead of processing deviceProfilesV4
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
*/

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
    //return loadProfilesFromJSONstring(testJSON)
    return loadProfilesFromJSONstring(readFile(DEFAULT_PROFILES_FILENAME))
}

import groovy.json.JsonSlurper

boolean loadProfilesFromJSONstring(stringifiedJSON) {
    long startTime = now()
    
    // idempotent : don't re-parse if already populated
    if (!deviceProfilesV4.isEmpty()) {
        logDebug "loadProfilesFromJSON: already loaded (${deviceProfilesV4.size()} profiles)"
        return true
    }
    try {
        logDebug "loadProfilesFromJSON: start loading device profiles from JSON..."
        if (!stringifiedJSON) {
            logWarn "loadProfilesFromJSON: stringifiedJSON is empty/null"
            return false
        }

        def jsonSlurper = new JsonSlurper();
        def parsed = jsonSlurper.parseText("${stringifiedJSON}");

        def dp = parsed?.deviceProfiles
        if (!(dp instanceof Map) || dp.isEmpty()) {
            logWarn "loadProfilesFromJSON: parsed deviceProfiles missing or empty"
            return false
        }
        // !!!!!!!!!!!!!!!!!!!!!!!
        // Populate deviceProfilesV4
        deviceProfilesV4.putAll(dp as Map)
        logInfo "loadProfilesFromJSON: deviceProfilesV4 populated with ${deviceProfilesV4.size()} profiles"

        // Populate deviceFingerprintsV4 using bulk assignment for better performance
        // Use fingerprintIt() logic to reconstruct complete fingerprint data
        Map localFingerprints = [:]
        
        deviceProfilesV4.each { profileKey, profileMap ->
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
        logInfo "loadProfilesFromJSON: deviceFingerprintsV4 populated with ${deviceFingerprintsV4.size()} entries"

        // Count total computed fingerprint strings
        int totalComputedFingerprints = 0
        localFingerprints.each { key, value ->
            totalComputedFingerprints += value.computedFingerprints?.size() ?: 0
        }

        // NOTE: profilesLoaded flag is managed by ensureProfilesLoaded(), not here
        // This keeps loadProfilesFromJSON() as a pure function
        long endTime = now()
        long executionTime = endTime - startTime
        
        logDebug "loadProfilesFromJSON: loaded ${deviceProfilesV4.size()} profiles: ${deviceProfilesV4.keySet()}"
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
 * This is the main function that should be called before accessing deviceProfilesV4
 * @return true if profiles are loaded successfully, false otherwise
 */
private boolean ensureProfilesLoaded() {
    // Fast path: already loaded
//    if (!deviceProfilesV4.isEmpty() && profilesLoaded) {
    if (profilesLoaded && !currentProfilesV4.isEmpty()) {       // !!!!!!!!!!!!!!!!!!!!!!!! TODO - check !!!!!!!!!!!!!!!!!!!!!!!!!!
        return true
    }
    
    // Check if another thread is already loading
    if (profilesLoading) {
        // Wait briefly for other thread to finish
        for (int i = 0; i < 10; i++) {
            logDebug "ensureProfilesLoaded: waiting <b>100ms</b> for other thread to finish loading..."
            pauseExecution(100)
            if (profilesLoaded && !deviceProfilesV4.isEmpty()) {
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
        if (deviceProfilesV4.isEmpty() || !profilesLoaded) {
            logWarn "ensureProfilesLoaded: loading device profiles...(deviceProfilesV4.isEmpty()=${deviceProfilesV4.isEmpty()}, profilesLoaded=${profilesLoaded})"
            boolean result = loadProfilesFromJSON()
            if (result) {
                profilesLoaded = true
                logInfo "ensureProfilesLoaded: successfully loaded ${deviceProfilesV4.size()} deviceProfilesV4 profiles"
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




// cacheTest command - manage and inspect cached data structures (currently deviceProfilesV4)
void cacheTest(String action) {
    String act = (action ?: 'Info').trim()
    switch(act) {
        case 'Info':
            int size = deviceProfilesV4?.size() ?: 0
            int fingerprintSize = deviceFingerprintsV4?.size() ?: 0
            int currentProfileSize = currentProfilesV4?.size() ?: 0
            List keys = deviceProfilesV4 ? new ArrayList(deviceProfilesV4.keySet()) : []
            List fingerprintKeys = deviceFingerprintsV4 ? new ArrayList(deviceFingerprintsV4.keySet()) : []
            List currentProfileKeys = currentProfilesV4 ? new ArrayList(currentProfilesV4.keySet()) : []
            
            // Count computed fingerprints
            int totalComputedFingerprints = 0
            deviceFingerprintsV4.each { key, value ->
                totalComputedFingerprints += value.computedFingerprints?.size() ?: 0
            }
            
            String dni = device?.deviceNetworkId
            boolean hasCurrentProfile = currentProfilesV4.containsKey(dni)
            
            logInfo "cacheTest Info: deviceProfilesV4 size=${size} keys=${keys}"
            logInfo "cacheTest Info: deviceFingerprintsV4 size=${fingerprintSize} keys=${fingerprintKeys}"
            logInfo "cacheTest Info: currentProfilesV4 size=${currentProfileSize} keys=${currentProfileKeys}"
            logInfo "cacheTest Info: total computed fingerprint strings=${totalComputedFingerprints}"
            if (hasCurrentProfile) {
                Map currentProfile = currentProfilesV4[dni]
                logInfo "cacheTest Info: current profile for this device (${dni}) has ${currentProfile?.keySet()?.size() ?: 0} sections"
            }
            else {
                logWarn "cacheTest Info: this device (${dni}) has no current profile loaded"
            }
            break
        case 'Initialize':
            boolean ok = ensureProfilesLoaded()
            logInfo "cacheTest Initialize: ensureProfilesLoaded() -> ${ok}; size now ${deviceProfilesV4.size()}"
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
        case 'CurrentProfilesV4':
            int currentSize = currentProfilesV4?.size() ?: 0
            List currentKeys = currentProfilesV4 ? new ArrayList(currentProfilesV4.keySet()) : []
            String dni = device?.deviceNetworkId
            boolean hasCurrentProfile = currentProfilesV4.containsKey(dni)
            
            logInfo "cacheTest CurrentProfilesV4: size=${currentSize} keys=${currentKeys}"
            logInfo "cacheTest CurrentProfilesV4: this device (${dni}) has profile loaded=${hasCurrentProfile}"
            
            if (hasCurrentProfile) {
                Map currentProfile = currentProfilesV4[dni]
                List profileKeys = currentProfile ? new ArrayList(currentProfile.keySet()) : []
                logInfo "cacheTest CurrentProfilesV4: current profile keys=${profileKeys}"
            }
            break
        case 'currentProfilesV4 Dump':
            if (currentProfilesV4.isEmpty()) {
                logInfo "cacheTest currentProfilesV4 Dump: currentProfilesV4 is empty"
            } else {
                logInfo "cacheTest currentProfilesV4 Dump: dumping entire currentProfilesV4 map:"
                currentProfilesV4.each { dni, profileData ->
                    logInfo "cacheTest currentProfilesV4 Dump: DNI '${dni}' -> ${profileData}"
                }
                logInfo "cacheTest currentProfilesV4 Dump: completed"
            }
            break
        case 'LoadCurrentProfile':
            String dni = device?.deviceNetworkId
            String profileName = getDeviceProfile()
            logInfo "cacheTest LoadCurrentProfile: attempting to load profile '${profileName}' for device ${dni}"
            
            ensureCurrentProfileLoaded()
            
            boolean loaded = currentProfilesV4.containsKey(dni)
            logInfo "cacheTest LoadCurrentProfile: profile loaded=${loaded}"
            if (loaded) {
                Map profile = currentProfilesV4[dni]
                logInfo "cacheTest LoadCurrentProfile: profile contains ${profile?.keySet()?.size() ?: 0} sections"
            }
            break
        case 'DisposeV3':
            int v4SizeBefore = deviceProfilesV4?.size() ?: 0
            int currentSizeCheck = currentProfilesV4?.size() ?: 0
            String dni = device?.deviceNetworkId
            boolean hasCurrentProfile = currentProfilesV4.containsKey(dni)
            
            if (!hasCurrentProfile) {
                logWarn "cacheTest DisposeV3: current device profile not loaded - cannot dispose V4 safely"
            } else {
                deviceProfilesV4.clear()
                profilesLoaded = false
                logInfo "cacheTest DisposeV3: disposed V4 profiles (was ${v4SizeBefore}) - currentProfilesV4 still has ${currentSizeCheck} entries"
            }
            break
        case 'TestFileRead':
            // Test just the file we know exists first
            logInfo "cacheTest TestFileRead: Testing known file '${DEFAULT_PROFILES_FILENAME}'..."
            //String content = readFile("deviceProfilesV4_mmWave.json")
            // big_json_test.json
            def content = readFile(DEFAULT_PROFILES_FILENAME)
            if (content != null) {
                logInfo "cacheTest TestFileRead: ? SUCCESS - '${DEFAULT_PROFILES_FILENAME}' (${content.length} chars)"
                //logDebug "TestFileRead parse has ${content?.keySet()?.size() ?: 0} top-level keys: ${content?.keySet() ?: 'null'}"
                // Show first 200 chars as preview
                //String preview = content.length > 2000 ? content.substring(0, 2000) + "..." : content
                //logInfo "cacheTest TestFileRead: Preview: ${preview}"
            } else {
                logWarn "cacheTest TestFileRead: ? FAILED - '${DEFAULT_PROFILES_FILENAME}' returned null"
            }
            logInfo "clearing existing profiles and re-loading from the read content..."
            deviceProfilesV4.clear()
            profilesLoaded = false
            logInfo "now trying to parse the content as JSON..."
            loadProfilesFromJSONstring(content)
            logInfo "cacheTest TestFileRead: completed"
            break
        case 'Clear':
            int before = deviceProfilesV4.size()
            int beforeFingerprints = deviceFingerprintsV4.size()
            int beforeCurrentProfiles = currentProfilesV4.size()
            deviceProfilesV4.clear()
            deviceFingerprintsV4.clear()
            currentProfilesV4.clear()
            profilesLoaded = false
            profilesLoading = false
            logInfo "cacheTest Clear: cleared ${before} V4 profiles, ${beforeFingerprints} fingerprint entries, and ${beforeCurrentProfiles} current profiles"
            break
        default:
            logWarn "cacheTest: unknown action '${action}'"
    }
}

// updateFromGitHub command - download JSON profiles from GitHub and store to Hubitat local storage
void updateFromGitHub(String url = '') {
    long startTime = now()
    String gitHubUrl = url?.trim() ?: defaultGitHubURL
    String fileName = DEFAULT_PROFILES_FILENAME
    
    logInfo "updateFromGitHub: downloading from ${gitHubUrl}"
    // uri: raw.githubusercontent.com/kkossev/Hubitat/...
    // https://raw.githubusercontent.com/kkossev/Hubitat/...

    
    try {
        // Download JSON content from GitHub
        long downloadStartTime = now()
        def params = [
            uri: gitHubUrl,
            //textParser: true  // This is the key! Same as working readFile method
        ]
        
        logDebug "updateFromGitHub: HTTP params: ${params}"
        
        httpGet(params) { resp ->
            logDebug "updateFromGitHub: Response status: ${resp?.status}"
            
            if (resp?.status == 200 && resp?.data) {
                // Fix StringReader issue - get actual text content without explicit class references
                String jsonContent = ""
                def responseData = resp.getData()
                
                if (responseData instanceof String) {
                    jsonContent = responseData
                } else if (responseData?.hasProperty('text')) {
                    // Handle StringReader without explicit class reference
                    jsonContent = responseData.text
                } else {
                    jsonContent = responseData.toString()
                }
                
                long downloadEndTime = now()
                long downloadDuration = downloadEndTime - downloadStartTime
                logInfo "updateFromGitHub: downloaded ${jsonContent.length()} characters"
                logDebug "updateFromGitHub: first 100 chars: ${jsonContent.take(100)}"
                logInfo "updateFromGitHub: Performance - Download: ${downloadDuration}ms"
                
                // Validate it's actually JSON content
                if (jsonContent.length() < 100 || !jsonContent.trim().startsWith("{")) {
                    logWarn "updateFromGitHub: ? Downloaded content doesn't appear to be valid JSON"
                    logWarn "updateFromGitHub: Content preview: ${jsonContent.take(200)}"
                    return
                }
                
                // Parse and extract version/timestamp information for debugging
                try {
                    def jsonSlurper = new groovy.json.JsonSlurper()
                    def parsedJson = jsonSlurper.parseText(jsonContent)
                    
                    def version = parsedJson?.version ?: "unknown"
                    def timestamp = parsedJson?.timestamp ?: "unknown"
                    def author = parsedJson?.author ?: "unknown"
                    def profileCount = parsedJson?.deviceProfiles?.size() ?: 0
                    
                    logDebug "updateFromGitHub: JSON Metadata - Version: ${version}, Timestamp: ${timestamp}"
                    logDebug "updateFromGitHub: JSON Metadata - Author: ${author}, Device Profiles: ${profileCount}"
                    
                } catch (Exception jsonException) {
                    logWarn "updateFromGitHub: Could not parse JSON metadata: ${jsonException.message}"
                }
                
                // Store the content to Hubitat local storage using uploadHubFile
                try {
                    long uploadStartTime = now()
                    // Use uploadHubFile to save content directly to local storage (correct API method)
                    def fileBytes = jsonContent.getBytes("UTF-8")
                    uploadHubFile(fileName, fileBytes)  // void method - no return value
                    
                    long uploadEndTime = now()
                    long uploadDuration = uploadEndTime - uploadStartTime
                    
                    logInfo "updateFromGitHub: ? Successfully updated ${fileName} in Hubitat local storage"
                    logInfo "updateFromGitHub: File size: ${jsonContent.length()} characters"
                    logInfo "updateFromGitHub: Performance - Upload: ${uploadDuration}ms"
                    
                    // Optional: Clear current profiles to force reload on next access
                    deviceProfilesV4.clear()
                    deviceFingerprintsV4.clear()
                    currentProfilesV4.clear()
                    profilesLoaded = false
                    profilesLoading = false
                    
                    logInfo "updateFromGitHub: Cleared cached profiles - they will be reloaded on next access"
                    
                    long endTime = now()
                    long totalDuration = endTime - startTime
                    logInfo "updateFromGitHub: Performance - Total: ${totalDuration}ms"
                } catch (Exception fileException) {
                    logWarn "updateFromGitHub: ? Error saving file: ${fileException.message}"
                    logDebug "updateFromGitHub: File save exception: ${fileException}"
                }
            } else {
                logWarn "updateFromGitHub: ? Failed to download from GitHub. HTTP status: ${resp?.status}"
            }
        }
        
    } catch (Exception e) {
        logWarn "updateFromGitHub: ? Error: ${e.message}"
        logDebug "updateFromGitHub: Full exception: ${e}"
    }
}

//////// https://community.hubitat.com/t/release-file-manager-device/91092 ///////
// credits @thebearmay
/////// https://github.com/thebearmay/hubitat/blob/main/fileMgr.groovy 

//@Field static String mmWaveFileName = "deviceProfilesV4_mmWave.json"

def readFile(fName, Closure closure) {
    closure(readFile(fName))
}

def readFile(fName) {
    long contentStartTime = now()
    //uri = "http://${location.hub.localIP}:8080/local/deviceProfilesV4_mmWave.json"
    uri = "http://${location.hub.localIP}:8080/local/${fName}"

    def params = [
        uri: uri,
        textParser: true,
    ]

    try {
        httpGet(params) { resp ->
            if(resp!= null) {
                def data = resp.getData();
                logDebug "test() read ${data.length} chars from ${uri}"
                long contentEndTime = now()
                long contentDuration = contentEndTime - contentStartTime
                logInfo "Performance: Content=${contentDuration}ms"
                return data
            }
            else {
                log.error "Null Response"
            }
        }
    } catch (exception) {
        log.error "Connection Exception: ${exception.message}"
        return null;
    }
}





////////


void testFunc( par) {
    parse('catchall: 0104 EF00 01 01 0040 00 7770 01 00 0000 02 01 00556701000100') 
}


void test(String par) {
    long startTime = now()
    logWarn "test() started at ${startTime}"


    /*
    def xx = getDeviceProfilesMap()
    logDebug "test() getDeviceProfilesMap() returned ${xx?.size() ?: 0} profiles"
    */
    /*
     String dni = device?.deviceNetworkId
    if (!currentProfilesV4.containsKey(dni)) {
        populateCurrentProfile(dni)
    }
    */
    /*

    boolean loaded = ensureProfilesLoaded()
    if (!loaded) {
        logWarn "test(): profiles not loaded, aborting test()"
        return
    }
    List<Map> attribMap = deviceProfilesV4[state.deviceProfile]?.attributes
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

    //uri = "http://${location.hub.localIP}:8080/local/deviceProfilesV4_mmWave_TS0601_TUYA_RADAR.json"
    uri = "http://${location.hub.localIP}:8080/local/deviceProfilesV4_mmWave.json"

    def params = [
        uri: uri,
        textParser: true,
    ]

    try {
        httpGet(params) { resp ->
            if(resp!= null) {
                def data = resp.getData();
                def jsonSlurper = new JsonSlurper();
                def parse = jsonSlurper.parseText("${data}");
                logDebug "test() read ${data.length} chars from ${uri}"
                logDebug "test() parse has ${parse?.keySet()?.size() ?: 0} top-level keys: ${parse?.keySet() ?: 'null'}"
                if (parse?.deviceProfiles != null) {
                    logDebug "test() parse.deviceProfiles has ${parse.deviceProfiles?.size() ?: 0} profiles: ${parse.deviceProfiles?.keySet() ?: 'null'}"
                } else {
                    logWarn "test() parse.deviceProfiles is null"
                }
                
            }
            else {
                log.error "Null Response"
            }
        }
    } catch (exception) {
        log.error "Connection Exception: ${exception.message}"
        return null;
    }




    long endTime = now()
    logWarn "test() ended at ${endTime} (duration ${endTime - startTime}ms)"
}


// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

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

// ~~~~~ start include (180) kkossev.motionLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.motionLib, line 1
library( // library marker kkossev.motionLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Motion Library', name: 'motionLib', namespace: 'kkossev', // library marker kkossev.motionLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/motionLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-motionLib', // library marker kkossev.motionLib, line 4
    version: '3.2.1' // library marker kkossev.motionLib, line 5
) // library marker kkossev.motionLib, line 6
/*  Zigbee Motion Library // library marker kkossev.motionLib, line 7
 * // library marker kkossev.motionLib, line 8
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.motionLib, line 9
 * // library marker kkossev.motionLib, line 10
 * ver. 3.2.0  2024-07-06 kkossev  - added motionLib.groovy; added [digital] [physical] to the descriptionText // library marker kkossev.motionLib, line 11
 * ver. 3.2.1  2025-03-24 kkossev  - (dev.branch) documentation // library marker kkossev.motionLib, line 12
 * // library marker kkossev.motionLib, line 13
 *                                   TODO: // library marker kkossev.motionLib, line 14
*/ // library marker kkossev.motionLib, line 15

static String motionLibVersion()   { '3.2.1' } // library marker kkossev.motionLib, line 17
static String motionLibStamp() { '2025/03/06 12:52 PM' } // library marker kkossev.motionLib, line 18

metadata { // library marker kkossev.motionLib, line 20
    capability 'MotionSensor' // library marker kkossev.motionLib, line 21
    // no custom attributes // library marker kkossev.motionLib, line 22
    command 'setMotion', [[name: 'setMotion', type: 'ENUM', constraints: ['No selection', 'active', 'inactive'], description: 'Force motion active/inactive (for tests)']] // library marker kkossev.motionLib, line 23
    preferences { // library marker kkossev.motionLib, line 24
        if (device) { // library marker kkossev.motionLib, line 25
            if (('motionReset' in DEVICE?.preferences) && (DEVICE?.preferences.motionReset == true)) { // library marker kkossev.motionLib, line 26
                input(name: 'motionReset', type: 'bool', title: '<b>Reset Motion to Inactive</b>', description: 'Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b>', defaultValue: false) // library marker kkossev.motionLib, line 27
                if (settings?.motionReset?.value == true) { // library marker kkossev.motionLib, line 28
                    input('motionResetTimer', 'number', title: '<b>Motion Reset Timer</b>', description: 'After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds', range: '0..7200', defaultValue: 60) // library marker kkossev.motionLib, line 29
                } // library marker kkossev.motionLib, line 30
            } // library marker kkossev.motionLib, line 31
            if (advancedOptions == true) { // library marker kkossev.motionLib, line 32
                if ('invertMotion' in DEVICE?.preferences) { // library marker kkossev.motionLib, line 33
                    input(name: 'invertMotion', type: 'bool', title: '<b>Invert Motion Active/Not Active</b>', description: 'Some Tuya motion sensors may report the motion active/inactive inverted...', defaultValue: false) // library marker kkossev.motionLib, line 34
                } // library marker kkossev.motionLib, line 35
            } // library marker kkossev.motionLib, line 36
        } // library marker kkossev.motionLib, line 37
    } // library marker kkossev.motionLib, line 38
} // library marker kkossev.motionLib, line 39

public void handleMotion(final boolean motionActive, final boolean isDigital=false) { // library marker kkossev.motionLib, line 41
    boolean motionActiveCopy = motionActive // library marker kkossev.motionLib, line 42

    if (settings.invertMotion == true) {    // patch!! fix it! // library marker kkossev.motionLib, line 44
        motionActiveCopy = !motionActiveCopy // library marker kkossev.motionLib, line 45
    } // library marker kkossev.motionLib, line 46

    //log.trace "handleMotion: motionActive=${motionActiveCopy}, isDigital=${isDigital}" // library marker kkossev.motionLib, line 48
    if (motionActiveCopy) { // library marker kkossev.motionLib, line 49
        int timeout = settings?.motionResetTimer ?: 0 // library marker kkossev.motionLib, line 50
        // If the sensor only sends a motion detected message, the reset to motion inactive must be  performed in code // library marker kkossev.motionLib, line 51
        if (settings?.motionReset == true && timeout != 0) { // library marker kkossev.motionLib, line 52
            runIn(timeout, 'resetToMotionInactive', [overwrite: true]) // library marker kkossev.motionLib, line 53
        } // library marker kkossev.motionLib, line 54
        if (device.currentState('motion')?.value != 'active') { // library marker kkossev.motionLib, line 55
            state.motionStarted = unix2formattedDate(now()) // library marker kkossev.motionLib, line 56
        } // library marker kkossev.motionLib, line 57
    } // library marker kkossev.motionLib, line 58
    else { // library marker kkossev.motionLib, line 59
        if (device.currentState('motion')?.value == 'inactive') { // library marker kkossev.motionLib, line 60
            logDebug "ignored motion inactive event after ${getSecondsInactive()}s" // library marker kkossev.motionLib, line 61
            return      // do not process a second motion inactive event! // library marker kkossev.motionLib, line 62
        } // library marker kkossev.motionLib, line 63
    } // library marker kkossev.motionLib, line 64
    sendMotionEvent(motionActiveCopy, isDigital) // library marker kkossev.motionLib, line 65
} // library marker kkossev.motionLib, line 66

public void sendMotionEvent(final boolean motionActive, boolean isDigital=false) { // library marker kkossev.motionLib, line 68
    String descriptionText = 'Detected motion' // library marker kkossev.motionLib, line 69
    if (motionActive) { // library marker kkossev.motionLib, line 70
        descriptionText = device.currentValue('motion') == 'active' ? "Motion is active ${getSecondsInactive()}s" : 'Detected motion' // library marker kkossev.motionLib, line 71
    } // library marker kkossev.motionLib, line 72
    else { // library marker kkossev.motionLib, line 73
        descriptionText = "Motion reset to inactive after ${getSecondsInactive()}s" // library marker kkossev.motionLib, line 74
    } // library marker kkossev.motionLib, line 75
    if (isDigital) { descriptionText += ' [digital]' } // library marker kkossev.motionLib, line 76
    logInfo "${descriptionText}" // library marker kkossev.motionLib, line 77
    sendEvent( // library marker kkossev.motionLib, line 78
            name            : 'motion', // library marker kkossev.motionLib, line 79
            value            : motionActive ? 'active' : 'inactive', // library marker kkossev.motionLib, line 80
            type            : isDigital == true ? 'digital' : 'physical', // library marker kkossev.motionLib, line 81
            descriptionText : descriptionText // library marker kkossev.motionLib, line 82
    ) // library marker kkossev.motionLib, line 83
    //runIn(1, formatAttrib, [overwrite: true]) // library marker kkossev.motionLib, line 84
} // library marker kkossev.motionLib, line 85

public void resetToMotionInactive() { // library marker kkossev.motionLib, line 87
    if (device.currentState('motion')?.value == 'active') { // library marker kkossev.motionLib, line 88
        String descText = "Motion reset to inactive after ${getSecondsInactive()}s (software timeout)" // library marker kkossev.motionLib, line 89
        sendEvent( // library marker kkossev.motionLib, line 90
            name : 'motion', // library marker kkossev.motionLib, line 91
            value : 'inactive', // library marker kkossev.motionLib, line 92
            isStateChange : true, // library marker kkossev.motionLib, line 93
            type:  'digital', // library marker kkossev.motionLib, line 94
            descriptionText : descText // library marker kkossev.motionLib, line 95
        ) // library marker kkossev.motionLib, line 96
        logInfo "${descText}" // library marker kkossev.motionLib, line 97
    } // library marker kkossev.motionLib, line 98
    else { // library marker kkossev.motionLib, line 99
        logDebug "ignored resetToMotionInactive (software timeout) after ${getSecondsInactive()}s" // library marker kkossev.motionLib, line 100
    } // library marker kkossev.motionLib, line 101
} // library marker kkossev.motionLib, line 102

public void setMotion(String mode) { // library marker kkossev.motionLib, line 104
    if (mode == 'active') { // library marker kkossev.motionLib, line 105
        handleMotion(motionActive = true, isDigital = true) // library marker kkossev.motionLib, line 106
    } else if (mode == 'inactive') { // library marker kkossev.motionLib, line 107
        handleMotion(motionActive = false, isDigital = true) // library marker kkossev.motionLib, line 108
    } else { // library marker kkossev.motionLib, line 109
        if (settings?.txtEnable) { // library marker kkossev.motionLib, line 110
            log.warn "${device.displayName} please select motion action" // library marker kkossev.motionLib, line 111
        } // library marker kkossev.motionLib, line 112
    } // library marker kkossev.motionLib, line 113
} // library marker kkossev.motionLib, line 114

public int getSecondsInactive() { // library marker kkossev.motionLib, line 116
    Long unixTime = 0 // library marker kkossev.motionLib, line 117
    try { unixTime = formattedDate2unix(state.motionStarted) } catch (Exception e) { logWarn "getSecondsInactive: ${e}" } // library marker kkossev.motionLib, line 118
    if (unixTime) { return Math.round((now() - unixTime) / 1000) as int } // library marker kkossev.motionLib, line 119
    return settings?.motionResetTimer ?: 0 // library marker kkossev.motionLib, line 120
} // library marker kkossev.motionLib, line 121

public List<String> refreshAllMotion() { // library marker kkossev.motionLib, line 123
    logDebug 'refreshAllMotion()' // library marker kkossev.motionLib, line 124
    List<String> cmds = [] // library marker kkossev.motionLib, line 125
    return cmds // library marker kkossev.motionLib, line 126
} // library marker kkossev.motionLib, line 127

public void motionInitializeVars( boolean fullInit = false ) { // library marker kkossev.motionLib, line 129
    logDebug "motionInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.motionLib, line 130
    if (device.hasCapability('MotionSensor')) { // library marker kkossev.motionLib, line 131
        if (fullInit == true || settings.motionReset == null) { device.updateSetting('motionReset', false) } // library marker kkossev.motionLib, line 132
        if (fullInit == true || settings.invertMotion == null) { device.updateSetting('invertMotion', false) } // library marker kkossev.motionLib, line 133
        if (fullInit == true || settings.motionResetTimer == null) { device.updateSetting('motionResetTimer', 60) } // library marker kkossev.motionLib, line 134
    } // library marker kkossev.motionLib, line 135
} // library marker kkossev.motionLib, line 136

// ~~~~~ end include (180) kkossev.motionLib ~~~~~

// ~~~~~ start include (171) kkossev.batteryLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoJavaUtilDate, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
library( // library marker kkossev.batteryLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Battery Library', name: 'batteryLib', namespace: 'kkossev', // library marker kkossev.batteryLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/batteryLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-batteryLib', // library marker kkossev.batteryLib, line 4
    version: '3.2.3' // library marker kkossev.batteryLib, line 5
) // library marker kkossev.batteryLib, line 6
/* // library marker kkossev.batteryLib, line 7
 *  Zigbee Battery Library // library marker kkossev.batteryLib, line 8
 * // library marker kkossev.batteryLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.batteryLib, line 10
 * // library marker kkossev.batteryLib, line 11
 * ver. 3.0.0  2024-04-06 kkossev  - added batteryLib.groovy // library marker kkossev.batteryLib, line 12
 * ver. 3.0.1  2024-04-06 kkossev  - customParsePowerCluster bug fix // library marker kkossev.batteryLib, line 13
 * ver. 3.0.2  2024-04-14 kkossev  - batteryPercentage bug fix (was x2); added bVoltCtr; added battertRefresh // library marker kkossev.batteryLib, line 14
 * ver. 3.2.0  2024-05-21 kkossev  - commonLib 3.2.0 allignment; added lastBattery; added handleTuyaBatteryLevel // library marker kkossev.batteryLib, line 15
 * ver. 3.2.1  2024-07-06 kkossev  - added tuyaToBatteryLevel and handleTuyaBatteryLevel; added batteryInitializeVars // library marker kkossev.batteryLib, line 16
 * ver. 3.2.2  2024-07-18 kkossev  - added BatteryVoltage and BatteryDelay device capability checks // library marker kkossev.batteryLib, line 17
 * ver. 3.2.3  2025-07-13 kkossev  - bug fix: corrected runIn method name from 'sendDelayedBatteryEvent' to 'sendDelayedBatteryPercentageEvent' // library marker kkossev.batteryLib, line 18
 * // library marker kkossev.batteryLib, line 19
 *                                   TODO: add an Advanced Option resetBatteryToZeroWhenOffline // library marker kkossev.batteryLib, line 20
 *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.batteryLib, line 21
*/ // library marker kkossev.batteryLib, line 22

static String batteryLibVersion()   { '3.2.3' } // library marker kkossev.batteryLib, line 24
static String batteryLibStamp() { '2025/07/13 7:45 PM' } // library marker kkossev.batteryLib, line 25

metadata { // library marker kkossev.batteryLib, line 27
    capability 'Battery' // library marker kkossev.batteryLib, line 28
    attribute  'batteryVoltage', 'number' // library marker kkossev.batteryLib, line 29
    attribute  'lastBattery', 'date'         // last battery event time - added in 3.2.0 05/21/2024 // library marker kkossev.batteryLib, line 30
    // no commands // library marker kkossev.batteryLib, line 31
    preferences { // library marker kkossev.batteryLib, line 32
        if (device && advancedOptions == true) { // library marker kkossev.batteryLib, line 33
            if ('BatteryVoltage' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 34
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: 'Convert battery voltage to battery Percentage remaining.' // library marker kkossev.batteryLib, line 35
            } // library marker kkossev.batteryLib, line 36
            if ('BatteryDelay' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 37
                input(name: 'batteryDelay', type: 'enum', title: '<b>Battery Events Delay</b>', description:'Select the Battery Events Delay<br>(default is <b>no delay</b>)', options: DelayBatteryOpts.options, defaultValue: DelayBatteryOpts.defaultValue) // library marker kkossev.batteryLib, line 38
            } // library marker kkossev.batteryLib, line 39
        } // library marker kkossev.batteryLib, line 40
    } // library marker kkossev.batteryLib, line 41
} // library marker kkossev.batteryLib, line 42

@Field static final Map DelayBatteryOpts = [ defaultValue: 0, options: [0: 'No delay', 30: '30 seconds', 3600: '1 hour', 14400: '4 hours', 28800: '8 hours', 43200: '12 hours']] // library marker kkossev.batteryLib, line 44

public void standardParsePowerCluster(final Map descMap) { // library marker kkossev.batteryLib, line 46
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.batteryLib, line 47
    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.batteryLib, line 48
    if (descMap.attrId == '0020') { // battery voltage // library marker kkossev.batteryLib, line 49
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 50
        state.stats['bVoltCtr'] = (state.stats['bVoltCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 51
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.batteryLib, line 52
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.batteryLib, line 53
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.batteryLib, line 54
        } // library marker kkossev.batteryLib, line 55
    } // library marker kkossev.batteryLib, line 56
    else if (descMap.attrId == '0021') { // battery percentage // library marker kkossev.batteryLib, line 57
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 58
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 59
        if (isTuya()) { // library marker kkossev.batteryLib, line 60
            sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 61
        } // library marker kkossev.batteryLib, line 62
        else { // library marker kkossev.batteryLib, line 63
            sendBatteryPercentageEvent((rawValue / 2) as int) // library marker kkossev.batteryLib, line 64
        } // library marker kkossev.batteryLib, line 65
    } // library marker kkossev.batteryLib, line 66
    else { // library marker kkossev.batteryLib, line 67
        logWarn "customParsePowerCluster: zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.batteryLib, line 68
    } // library marker kkossev.batteryLib, line 69
} // library marker kkossev.batteryLib, line 70

public void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.batteryLib, line 72
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.batteryLib, line 73
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 74
    Map result = [:] // library marker kkossev.batteryLib, line 75
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.batteryLib, line 76
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.batteryLib, line 77
        BigDecimal minVolts = 2.2 // library marker kkossev.batteryLib, line 78
        BigDecimal maxVolts = 3.2 // library marker kkossev.batteryLib, line 79
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.batteryLib, line 80
        int roundedPct = Math.round(pct * 100) // library marker kkossev.batteryLib, line 81
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.batteryLib, line 82
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.batteryLib, line 83
        if (convertToPercent == true) { // library marker kkossev.batteryLib, line 84
            result.value = Math.min(100, roundedPct) // library marker kkossev.batteryLib, line 85
            result.name = 'battery' // library marker kkossev.batteryLib, line 86
            result.unit  = '%' // library marker kkossev.batteryLib, line 87
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.batteryLib, line 88
        } // library marker kkossev.batteryLib, line 89
        else { // library marker kkossev.batteryLib, line 90
            result.value = volts // library marker kkossev.batteryLib, line 91
            result.name = 'batteryVoltage' // library marker kkossev.batteryLib, line 92
            result.unit  = 'V' // library marker kkossev.batteryLib, line 93
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.batteryLib, line 94
        } // library marker kkossev.batteryLib, line 95
        result.type = 'physical' // library marker kkossev.batteryLib, line 96
        result.isStateChange = true // library marker kkossev.batteryLib, line 97
        logInfo "${result.descriptionText}" // library marker kkossev.batteryLib, line 98
        sendEvent(result) // library marker kkossev.batteryLib, line 99
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 100
    } // library marker kkossev.batteryLib, line 101
    else { // library marker kkossev.batteryLib, line 102
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.batteryLib, line 103
    } // library marker kkossev.batteryLib, line 104
} // library marker kkossev.batteryLib, line 105

public void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.batteryLib, line 107
    if ((batteryPercent as int) == 255) { // library marker kkossev.batteryLib, line 108
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.batteryLib, line 109
        return // library marker kkossev.batteryLib, line 110
    } // library marker kkossev.batteryLib, line 111
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 112
    Map map = [:] // library marker kkossev.batteryLib, line 113
    map.name = 'battery' // library marker kkossev.batteryLib, line 114
    map.timeStamp = now() // library marker kkossev.batteryLib, line 115
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.batteryLib, line 116
    map.unit  = '%' // library marker kkossev.batteryLib, line 117
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.batteryLib, line 118
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.batteryLib, line 119
    map.isStateChange = true // library marker kkossev.batteryLib, line 120
    // // library marker kkossev.batteryLib, line 121
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.batteryLib, line 122
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.batteryLib, line 123
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.batteryLib, line 124
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.batteryLib, line 125
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.batteryLib, line 126
        // send it now! // library marker kkossev.batteryLib, line 127
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.batteryLib, line 128
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 129
    } // library marker kkossev.batteryLib, line 130
    else { // library marker kkossev.batteryLib, line 131
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.batteryLib, line 132
        map.delayed = delayedTime // library marker kkossev.batteryLib, line 133
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.batteryLib, line 134
        map.lastBattery = lastBattery // library marker kkossev.batteryLib, line 135
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.batteryLib, line 136
        runIn(delayedTime, 'sendDelayedBatteryPercentageEvent', [overwrite: true, data: map]) // library marker kkossev.batteryLib, line 137
    } // library marker kkossev.batteryLib, line 138
} // library marker kkossev.batteryLib, line 139

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.batteryLib, line 141
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 142
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 143
    sendEvent(map) // library marker kkossev.batteryLib, line 144
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 145
} // library marker kkossev.batteryLib, line 146

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.batteryLib, line 148
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.batteryLib, line 149
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 150
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 151
    sendEvent(map) // library marker kkossev.batteryLib, line 152
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 153
} // library marker kkossev.batteryLib, line 154

public int tuyaToBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 156
    int rawValue = fncmd // library marker kkossev.batteryLib, line 157
    switch (fncmd) { // library marker kkossev.batteryLib, line 158
        case 0: rawValue = 100; break // Battery Full // library marker kkossev.batteryLib, line 159
        case 1: rawValue = 75;  break // Battery High // library marker kkossev.batteryLib, line 160
        case 2: rawValue = 50;  break // Battery Medium // library marker kkossev.batteryLib, line 161
        case 3: rawValue = 25;  break // Battery Low // library marker kkossev.batteryLib, line 162
        case 4: rawValue = 100; break // Tuya 3 in 1 -> USB powered // library marker kkossev.batteryLib, line 163
        // for all other values >4 we will use the raw value, expected to be the real battery level 4..100% // library marker kkossev.batteryLib, line 164
    } // library marker kkossev.batteryLib, line 165
    return rawValue // library marker kkossev.batteryLib, line 166
} // library marker kkossev.batteryLib, line 167

public void handleTuyaBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 169
    int rawValue = tuyaToBatteryLevel(fncmd) // library marker kkossev.batteryLib, line 170
    sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 171
} // library marker kkossev.batteryLib, line 172

public void batteryInitializeVars( boolean fullInit = false ) { // library marker kkossev.batteryLib, line 174
    logDebug "batteryInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.batteryLib, line 175
    if (device.hasCapability('Battery')) { // library marker kkossev.batteryLib, line 176
        if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.batteryLib, line 177
        if (fullInit || settings?.batteryDelay == null) { device.updateSetting('batteryDelay', [value: DelayBatteryOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.batteryLib, line 178
    } // library marker kkossev.batteryLib, line 179
} // library marker kkossev.batteryLib, line 180

public List<String> batteryRefresh() { // library marker kkossev.batteryLib, line 182
    List<String> cmds = [] // library marker kkossev.batteryLib, line 183
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 100)         // battery voltage // library marker kkossev.batteryLib, line 184
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 100)         // battery percentage // library marker kkossev.batteryLib, line 185
    return cmds // library marker kkossev.batteryLib, line 186
} // library marker kkossev.batteryLib, line 187

// ~~~~~ end include (171) kkossev.batteryLib ~~~~~

// ~~~~~ start include (197) kkossev.deviceProfileLibV4 ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NestedBlockDepth, NoDouble, NoFloat, NoWildcardImports, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.deviceProfileLibV4, line 1
library( // library marker kkossev.deviceProfileLibV4, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLibV4', namespace: 'kkossev', // library marker kkossev.deviceProfileLibV4, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/deviceProfileLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-deviceProfileLib', // library marker kkossev.deviceProfileLibV4, line 4
    version: '4.0.0' // library marker kkossev.deviceProfileLibV4, line 5
) // library marker kkossev.deviceProfileLibV4, line 6
/* // library marker kkossev.deviceProfileLibV4, line 7
 *  Device Profile Library V4 // library marker kkossev.deviceProfileLibV4, line 8
 * // library marker kkossev.deviceProfileLibV4, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.deviceProfileLibV4, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.deviceProfileLibV4, line 11
 * // library marker kkossev.deviceProfileLibV4, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.deviceProfileLibV4, line 13
 * // library marker kkossev.deviceProfileLibV4, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.deviceProfileLibV4, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.deviceProfileLibV4, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.deviceProfileLibV4, line 17
 * // library marker kkossev.deviceProfileLibV4, line 18
 * ver. 1.0.0  2023-11-04 kkossev  - added deviceProfileLib (based on Tuya 4 In 1 driver) // library marker kkossev.deviceProfileLibV4, line 19
 * ................................... // library marker kkossev.deviceProfileLibV4, line 20
 * ver. 3.5.0  2025-08-14 kkossev  - zclWriteAttribute() support for forced destinationEndpoint in the attributes map // library marker kkossev.deviceProfileLibV4, line 21
 * ver. 4.0.0  2025-09-03 kkossev  - deviceProfileV4 BRANCH created; deviceProfilesV2 support is dropped;  // library marker kkossev.deviceProfileLibV4, line 22
 * // library marker kkossev.deviceProfileLibV4, line 23
 *                                   TODO -  // library marker kkossev.deviceProfileLibV4, line 24
 * // library marker kkossev.deviceProfileLibV4, line 25
*/ // library marker kkossev.deviceProfileLibV4, line 26

static String deviceProfileLibVersion()   { '4.0.0' } // library marker kkossev.deviceProfileLibV4, line 28
static String deviceProfileLibStamp() { '2025/09/05 5:18 PM' } // library marker kkossev.deviceProfileLibV4, line 29
import groovy.json.* // library marker kkossev.deviceProfileLibV4, line 30
import groovy.transform.Field // library marker kkossev.deviceProfileLibV4, line 31
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLibV4, line 32
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLibV4, line 33
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLibV4, line 34

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLibV4, line 36

metadata { // library marker kkossev.deviceProfileLibV4, line 38
    // no capabilities // library marker kkossev.deviceProfileLibV4, line 39
    // no attributes // library marker kkossev.deviceProfileLibV4, line 40
    /* // library marker kkossev.deviceProfileLibV4, line 41
    // copy the following commands to the main driver, if needed // library marker kkossev.deviceProfileLibV4, line 42
    command 'sendCommand', [ // library marker kkossev.deviceProfileLibV4, line 43
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLibV4, line 44
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLibV4, line 45
    ] // library marker kkossev.deviceProfileLibV4, line 46
    command 'setPar', [ // library marker kkossev.deviceProfileLibV4, line 47
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLibV4, line 48
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLibV4, line 49
    ] // library marker kkossev.deviceProfileLibV4, line 50
    */ // library marker kkossev.deviceProfileLibV4, line 51

    preferences { // library marker kkossev.deviceProfileLibV4, line 53
        if (device) { // library marker kkossev.deviceProfileLibV4, line 54
            input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLibV4, line 55
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLibV4, line 56
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLibV4, line 57
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLibV4, line 58
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLibV4, line 59
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLibV4, line 60
                        input inputMap // library marker kkossev.deviceProfileLibV4, line 61
                    } // library marker kkossev.deviceProfileLibV4, line 62
                } // library marker kkossev.deviceProfileLibV4, line 63
            } // library marker kkossev.deviceProfileLibV4, line 64
        } // library marker kkossev.deviceProfileLibV4, line 65
    } // library marker kkossev.deviceProfileLibV4, line 66

} // library marker kkossev.deviceProfileLibV4, line 68

private boolean is2in1() { return getDeviceProfile().startsWith('TS0601_2IN1')  }   // patch! // library marker kkossev.deviceProfileLibV4, line 70

public String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLibV4, line 72
public Map     getDEVICE()              {  // library marker kkossev.deviceProfileLibV4, line 73
    // Use V4 approach only. Backward compatibility with V3 is dropped // library marker kkossev.deviceProfileLibV4, line 74
    if (this.hasProperty('currentProfilesV4')) { // library marker kkossev.deviceProfileLibV4, line 75
        ensureCurrentProfileLoaded() // library marker kkossev.deviceProfileLibV4, line 76
        return getCurrentDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 77
    }  // library marker kkossev.deviceProfileLibV4, line 78
    return [:] // library marker kkossev.deviceProfileLibV4, line 79
} // library marker kkossev.deviceProfileLibV4, line 80

// ---- V4 Profile Management Methods ---- // library marker kkossev.deviceProfileLibV4, line 82

/** // library marker kkossev.deviceProfileLibV4, line 84
 * Gets the current device's profile data from currentProfilesV4 map // library marker kkossev.deviceProfileLibV4, line 85
 * Falls back to deviceProfilesV4 if currentProfilesV4 entry doesn't exist // library marker kkossev.deviceProfileLibV4, line 86
 * @return Map containing the device profile data // library marker kkossev.deviceProfileLibV4, line 87
 */ // library marker kkossev.deviceProfileLibV4, line 88
private Map getCurrentDeviceProfile() { // library marker kkossev.deviceProfileLibV4, line 89
    if (!this.hasProperty('currentProfilesV4')) {  // library marker kkossev.deviceProfileLibV4, line 90
        return [:]  // NO fallback to V3 method // library marker kkossev.deviceProfileLibV4, line 91
    } // library marker kkossev.deviceProfileLibV4, line 92

    String dni = device?.deviceNetworkId // library marker kkossev.deviceProfileLibV4, line 94
    Map currentProfile = currentProfilesV4[dni] // library marker kkossev.deviceProfileLibV4, line 95

    if (currentProfile != null && currentProfile != [:]) { // library marker kkossev.deviceProfileLibV4, line 97
        return currentProfile // library marker kkossev.deviceProfileLibV4, line 98
    } else { // library marker kkossev.deviceProfileLibV4, line 99
        // Profile not loaded yet, use V3 fallback // library marker kkossev.deviceProfileLibV4, line 100
        return [:] // library marker kkossev.deviceProfileLibV4, line 101
    } // library marker kkossev.deviceProfileLibV4, line 102
} // library marker kkossev.deviceProfileLibV4, line 103

/** // library marker kkossev.deviceProfileLibV4, line 105
 * Ensures the current device's profile is loaded in currentProfilesV4 // library marker kkossev.deviceProfileLibV4, line 106
 * Should be called before accessing device-specific profile data // library marker kkossev.deviceProfileLibV4, line 107
 */ // library marker kkossev.deviceProfileLibV4, line 108
private void ensureCurrentProfileLoaded() { // library marker kkossev.deviceProfileLibV4, line 109
    if (!this.hasProperty('currentProfilesV4')) {  // library marker kkossev.deviceProfileLibV4, line 110
        return  // V4 not available, stick with V3 // library marker kkossev.deviceProfileLibV4, line 111
    } // library marker kkossev.deviceProfileLibV4, line 112

    String dni = device?.deviceNetworkId // library marker kkossev.deviceProfileLibV4, line 114
    if (!currentProfilesV4.containsKey(dni)) { // library marker kkossev.deviceProfileLibV4, line 115
        populateCurrentProfile(dni) // library marker kkossev.deviceProfileLibV4, line 116
    } // library marker kkossev.deviceProfileLibV4, line 117
} // library marker kkossev.deviceProfileLibV4, line 118

/** // library marker kkossev.deviceProfileLibV4, line 120
 * Populates currentProfilesV4 entry for the specified device // library marker kkossev.deviceProfileLibV4, line 121
 * Extracts complete profile data from deviceProfilesV4 (excluding fingerprints) // library marker kkossev.deviceProfileLibV4, line 122
 * @param dni Device Network ID to use as key // library marker kkossev.deviceProfileLibV4, line 123
 */ // library marker kkossev.deviceProfileLibV4, line 124
private void populateCurrentProfile(String dni) { // library marker kkossev.deviceProfileLibV4, line 125
    logDebug "populateCurrentProfile: populating profile for device ${dni}" // library marker kkossev.deviceProfileLibV4, line 126
    if (!this.hasProperty('currentProfilesV4') || !this.hasProperty('deviceProfilesV4')) {  // library marker kkossev.deviceProfileLibV4, line 127
        return // library marker kkossev.deviceProfileLibV4, line 128
    } // library marker kkossev.deviceProfileLibV4, line 129

    String profileName = getDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 131
    if (!profileName || profileName == 'UNKNOWN') { // library marker kkossev.deviceProfileLibV4, line 132
        logWarn "populateCurrentProfile: cannot populate profile for ${dni} - profile name is ${profileName}" // library marker kkossev.deviceProfileLibV4, line 133
        return // library marker kkossev.deviceProfileLibV4, line 134
    } // library marker kkossev.deviceProfileLibV4, line 135
    logDebug "ensuring profiles loaded for device ${dni}" // library marker kkossev.deviceProfileLibV4, line 136
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 137

    Map sourceProfile = deviceProfilesV4[profileName] // library marker kkossev.deviceProfileLibV4, line 139
    if (sourceProfile) { // library marker kkossev.deviceProfileLibV4, line 140
        // Clone the profile data and remove fingerprints (already in deviceFingerprintsV4) // library marker kkossev.deviceProfileLibV4, line 141
        Map profileData = sourceProfile.clone() // library marker kkossev.deviceProfileLibV4, line 142
        profileData.remove('fingerprints') // library marker kkossev.deviceProfileLibV4, line 143
        currentProfilesV4[dni] = profileData // library marker kkossev.deviceProfileLibV4, line 144
        logInfo "populateCurrentProfile: loaded profile '${profileName}' for device ${dni}" // library marker kkossev.deviceProfileLibV4, line 145
    } else { // library marker kkossev.deviceProfileLibV4, line 146
        logWarn "populateCurrentProfile: profile '${profileName}' not found in deviceProfilesV4 for device ${dni}" // library marker kkossev.deviceProfileLibV4, line 147
    } // library marker kkossev.deviceProfileLibV4, line 148
} // library marker kkossev.deviceProfileLibV4, line 149

/** // library marker kkossev.deviceProfileLibV4, line 151
 * Disposes of V3 profile data to free memory once all devices have their profiles loaded // library marker kkossev.deviceProfileLibV4, line 152
 * Should only be called when it's safe to remove V3 data // library marker kkossev.deviceProfileLibV4, line 153
 */ // library marker kkossev.deviceProfileLibV4, line 154
void disposeV3ProfilesIfReady() { // library marker kkossev.deviceProfileLibV4, line 155
    if (!this.hasProperty('currentProfilesV4') || !this.hasProperty('deviceProfilesV4')) {  // library marker kkossev.deviceProfileLibV4, line 156
        return // library marker kkossev.deviceProfileLibV4, line 157
    } // library marker kkossev.deviceProfileLibV4, line 158

    String dni = device?.deviceNetworkId // library marker kkossev.deviceProfileLibV4, line 160
    if (currentProfilesV4.containsKey(dni)) { // library marker kkossev.deviceProfileLibV4, line 161
        // This device has its profile loaded, it's safe to dispose V3 for this device // library marker kkossev.deviceProfileLibV4, line 162
        logDebug "disposeV3ProfilesIfReady: device ${dni} has current profile loaded - V3 can be disposed" // library marker kkossev.deviceProfileLibV4, line 163
        // Note: In a production environment, you might want more sophisticated logic // library marker kkossev.deviceProfileLibV4, line 164
        // to check if ALL active devices have their profiles loaded before disposing V3 // library marker kkossev.deviceProfileLibV4, line 165
    } else { // library marker kkossev.deviceProfileLibV4, line 166
        logDebug "disposeV3ProfilesIfReady: device ${dni} does not have current profile loaded - keeping V3" // library marker kkossev.deviceProfileLibV4, line 167
    } // library marker kkossev.deviceProfileLibV4, line 168
} // library marker kkossev.deviceProfileLibV4, line 169

/** // library marker kkossev.deviceProfileLibV4, line 171
 * Forces disposal of V4 profiles to free memory // library marker kkossev.deviceProfileLibV4, line 172
 * Use with caution - only when you're sure all needed profiles are in currentProfilesV4 // library marker kkossev.deviceProfileLibV4, line 173
 */ // library marker kkossev.deviceProfileLibV4, line 174
void forceDisposeV4Profiles() { // library marker kkossev.deviceProfileLibV4, line 175
    if (!this.hasProperty('deviceProfilesV4')) {  // library marker kkossev.deviceProfileLibV4, line 176
        return // library marker kkossev.deviceProfileLibV4, line 177
    } // library marker kkossev.deviceProfileLibV4, line 178

    int sizeBefore = deviceProfilesV4?.size() ?: 0 // library marker kkossev.deviceProfileLibV4, line 180
    deviceProfilesV4.clear() // library marker kkossev.deviceProfileLibV4, line 181
    if (this.hasProperty('profilesLoaded')) { profilesLoaded = false } // library marker kkossev.deviceProfileLibV4, line 182
    logInfo "forceDisposeV3Profiles: disposed ${sizeBefore} V3 profiles to free memory" // library marker kkossev.deviceProfileLibV4, line 183
} // library marker kkossev.deviceProfileLibV4, line 184

public Set     getDeviceProfiles()      { deviceProfilesV4 != null ? deviceProfilesV4?.keySet() : [] } // library marker kkossev.deviceProfileLibV4, line 186

// TODO - check why it returns list instead of set or map ??? TODO // library marker kkossev.deviceProfileLibV4, line 188
public List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLibV4, line 189
    // if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 190
    // better don't ... // library marker kkossev.deviceProfileLibV4, line 191
    if (deviceProfilesV4 == null || deviceProfilesV4.isEmpty()) { return [] } // library marker kkossev.deviceProfileLibV4, line 192
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLibV4, line 193
    deviceProfilesV4.each { profileName, profileMap -> // library marker kkossev.deviceProfileLibV4, line 194
        if ((profileMap.device?.isDepricated ?: false) != true) { // library marker kkossev.deviceProfileLibV4, line 195
            activeProfiles.add(profileMap.description ?: '---') // library marker kkossev.deviceProfileLibV4, line 196
        } // library marker kkossev.deviceProfileLibV4, line 197
    } // library marker kkossev.deviceProfileLibV4, line 198
    return activeProfiles // library marker kkossev.deviceProfileLibV4, line 199
} // library marker kkossev.deviceProfileLibV4, line 200

// ---------------------------------- deviceProfilesV4 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLibV4, line 202

/** // library marker kkossev.deviceProfileLibV4, line 204
 * Returns the device fingerprints map // library marker kkossev.deviceProfileLibV4, line 205
 * @return The deviceFingerprintsV4 map containing description and fingerprints for each profile // library marker kkossev.deviceProfileLibV4, line 206
 */ // library marker kkossev.deviceProfileLibV4, line 207
public Map getDeviceFingerprintsV4() { // library marker kkossev.deviceProfileLibV4, line 208
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 209
    return this.hasProperty('deviceFingerprintsV4') ? deviceFingerprintsV4 : [:] // library marker kkossev.deviceProfileLibV4, line 210
} // library marker kkossev.deviceProfileLibV4, line 211

/** // library marker kkossev.deviceProfileLibV4, line 213
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLibV4, line 214
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLibV4, line 215
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLibV4, line 216
 */ // library marker kkossev.deviceProfileLibV4, line 217
public String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLibV4, line 218
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 219
    if (deviceProfilesV4 != null) { return deviceProfilesV4.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLibV4, line 220
    else { return null } // library marker kkossev.deviceProfileLibV4, line 221
} // library marker kkossev.deviceProfileLibV4, line 222

/** // library marker kkossev.deviceProfileLibV4, line 224
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLibV4, line 225
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLibV4, line 226
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLibV4, line 227
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLibV4, line 228
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLibV4, line 229
 */ // library marker kkossev.deviceProfileLibV4, line 230
private Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLibV4, line 231
    Map foundMap = [:] // library marker kkossev.deviceProfileLibV4, line 232
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLibV4, line 233
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 234
    def preference // library marker kkossev.deviceProfileLibV4, line 235
    try { // library marker kkossev.deviceProfileLibV4, line 236
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLibV4, line 237
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLibV4, line 238
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLibV4, line 239
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLibV4, line 240
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLibV4, line 241
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLibV4, line 242
        } // library marker kkossev.deviceProfileLibV4, line 243
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLibV4, line 244
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLibV4, line 245
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLibV4, line 246
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLibV4, line 247
        } // library marker kkossev.deviceProfileLibV4, line 248
        else { // cluster:attribute // library marker kkossev.deviceProfileLibV4, line 249
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLibV4, line 250
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLibV4, line 251
        } // library marker kkossev.deviceProfileLibV4, line 252
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLibV4, line 253
    } catch (e) { // library marker kkossev.deviceProfileLibV4, line 254
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLibV4, line 255
        return [:] // library marker kkossev.deviceProfileLibV4, line 256
    } // library marker kkossev.deviceProfileLibV4, line 257
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLibV4, line 258
    return foundMap // library marker kkossev.deviceProfileLibV4, line 259
} // library marker kkossev.deviceProfileLibV4, line 260

public Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLibV4, line 262
    Map foundMap = [:] // library marker kkossev.deviceProfileLibV4, line 263
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLibV4, line 264
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLibV4, line 265
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLibV4, line 266
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLibV4, line 267
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLibV4, line 268
        if (foundMap != null) { // library marker kkossev.deviceProfileLibV4, line 269
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLibV4, line 270
            return foundMap // library marker kkossev.deviceProfileLibV4, line 271
        } // library marker kkossev.deviceProfileLibV4, line 272
    } // library marker kkossev.deviceProfileLibV4, line 273
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLibV4, line 274
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLibV4, line 275
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLibV4, line 276
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLibV4, line 277
        if (foundMap != null) { // library marker kkossev.deviceProfileLibV4, line 278
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLibV4, line 279
            return foundMap // library marker kkossev.deviceProfileLibV4, line 280
        } // library marker kkossev.deviceProfileLibV4, line 281
    } // library marker kkossev.deviceProfileLibV4, line 282
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLibV4, line 283
    return [:] // library marker kkossev.deviceProfileLibV4, line 284
} // library marker kkossev.deviceProfileLibV4, line 285

/** // library marker kkossev.deviceProfileLibV4, line 287
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLibV4, line 288
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLibV4, line 289
 */ // library marker kkossev.deviceProfileLibV4, line 290
public void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLibV4, line 291
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLibV4, line 292
    if (DEVICE == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLibV4, line 293
    Map preferences = DEVICE?.preferences ?: [:] // library marker kkossev.deviceProfileLibV4, line 294
    if (preferences == null || preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLibV4, line 295
    Map parMap = [:] // library marker kkossev.deviceProfileLibV4, line 296
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLibV4, line 297
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLibV4, line 298
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLibV4, line 299
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLibV4, line 300
            return // continue // library marker kkossev.deviceProfileLibV4, line 301
        } // library marker kkossev.deviceProfileLibV4, line 302
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLibV4, line 303
        if (parMap == null || parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLibV4, line 304
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLibV4, line 305
        if (parMap?.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLibV4, line 306
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLibV4, line 307
        String str = parMap.name // library marker kkossev.deviceProfileLibV4, line 308
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLibV4, line 309
    } // library marker kkossev.deviceProfileLibV4, line 310
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLibV4, line 311
} // library marker kkossev.deviceProfileLibV4, line 312

/** // library marker kkossev.deviceProfileLibV4, line 314
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLibV4, line 315
 * // library marker kkossev.deviceProfileLibV4, line 316
 * @return List of valid parameters. // library marker kkossev.deviceProfileLibV4, line 317
 */ // library marker kkossev.deviceProfileLibV4, line 318
private List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLibV4, line 319
    List<String> validPars = [] // library marker kkossev.deviceProfileLibV4, line 320
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLibV4, line 321
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLibV4, line 322
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLibV4, line 323
    } // library marker kkossev.deviceProfileLibV4, line 324
    return validPars // library marker kkossev.deviceProfileLibV4, line 325
} // library marker kkossev.deviceProfileLibV4, line 326

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 328
private def getScaledPreferenceValue(String preference, Map dpMap) {        // TODO - not used ??? // library marker kkossev.deviceProfileLibV4, line 329
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 330
    def value = settings."${preference}" // library marker kkossev.deviceProfileLibV4, line 331
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 332
    def scaledValue // library marker kkossev.deviceProfileLibV4, line 333
    if (value == null) { // library marker kkossev.deviceProfileLibV4, line 334
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLibV4, line 335
        return null // library marker kkossev.deviceProfileLibV4, line 336
    } // library marker kkossev.deviceProfileLibV4, line 337
    switch (dpMap.type) { // library marker kkossev.deviceProfileLibV4, line 338
        case 'number' : // library marker kkossev.deviceProfileLibV4, line 339
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLibV4, line 340
            break // library marker kkossev.deviceProfileLibV4, line 341
        case 'decimal' : // library marker kkossev.deviceProfileLibV4, line 342
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLibV4, line 343
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLibV4, line 344
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLibV4, line 345
            } // library marker kkossev.deviceProfileLibV4, line 346
            break // library marker kkossev.deviceProfileLibV4, line 347
        case 'bool' : // library marker kkossev.deviceProfileLibV4, line 348
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLibV4, line 349
            break // library marker kkossev.deviceProfileLibV4, line 350
        case 'enum' : // library marker kkossev.deviceProfileLibV4, line 351
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLibV4, line 352
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLibV4, line 353
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLibV4, line 354
                return null // library marker kkossev.deviceProfileLibV4, line 355
            } // library marker kkossev.deviceProfileLibV4, line 356
            scaledValue = value // library marker kkossev.deviceProfileLibV4, line 357
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLibV4, line 358
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLibV4, line 359
            } // library marker kkossev.deviceProfileLibV4, line 360
            break // library marker kkossev.deviceProfileLibV4, line 361
        default : // library marker kkossev.deviceProfileLibV4, line 362
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLibV4, line 363
            return null // library marker kkossev.deviceProfileLibV4, line 364
    } // library marker kkossev.deviceProfileLibV4, line 365
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLibV4, line 366
    return scaledValue // library marker kkossev.deviceProfileLibV4, line 367
} // library marker kkossev.deviceProfileLibV4, line 368

// called from customUpdated() method in the custom driver // library marker kkossev.deviceProfileLibV4, line 370
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLibV4, line 371
public void updateAllPreferences() { // library marker kkossev.deviceProfileLibV4, line 372
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLibV4, line 373
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLibV4, line 374
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLibV4, line 375
        return // library marker kkossev.deviceProfileLibV4, line 376
    } // library marker kkossev.deviceProfileLibV4, line 377
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 378
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLibV4, line 379
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLibV4, line 380
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLibV4, line 381
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLibV4, line 382
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLibV4, line 383
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLibV4, line 384
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLibV4, line 385
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLibV4, line 386
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLibV4, line 387
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLibV4, line 388
                // scale the value // library marker kkossev.deviceProfileLibV4, line 389
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLibV4, line 390
            } // library marker kkossev.deviceProfileLibV4, line 391
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLibV4, line 392
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLibV4, line 393
            } // library marker kkossev.deviceProfileLibV4, line 394
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLibV4, line 395
        } // library marker kkossev.deviceProfileLibV4, line 396
        else { logDebug "warning: couldn't find map for preference ${name}" ; return }  // TODO - supress the warning if the preference was boolean true/false // library marker kkossev.deviceProfileLibV4, line 397
    } // library marker kkossev.deviceProfileLibV4, line 398
    return // library marker kkossev.deviceProfileLibV4, line 399
} // library marker kkossev.deviceProfileLibV4, line 400

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 402
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLibV4, line 403
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLibV4, line 404
int divideBy10(int val) { // library marker kkossev.deviceProfileLibV4, line 405
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLibV4, line 406
    else { return (val as int) } // library marker kkossev.deviceProfileLibV4, line 407
} // library marker kkossev.deviceProfileLibV4, line 408
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLibV4, line 409
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLibV4, line 410
int signedInt(int val) { // library marker kkossev.deviceProfileLibV4, line 411
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLibV4, line 412
    else { return (val as int) } // library marker kkossev.deviceProfileLibV4, line 413
} // library marker kkossev.deviceProfileLibV4, line 414
int invert(int val) { // library marker kkossev.deviceProfileLibV4, line 415
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 } // library marker kkossev.deviceProfileLibV4, line 416
    else { return val } // library marker kkossev.deviceProfileLibV4, line 417
} // library marker kkossev.deviceProfileLibV4, line 418

// called from setPar and sendAttribite methods for non-Tuya DPs // library marker kkossev.deviceProfileLibV4, line 420
private List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLibV4, line 421
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLibV4, line 422
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 423
    Map map = [:] // library marker kkossev.deviceProfileLibV4, line 424
    // cluster:attribute // library marker kkossev.deviceProfileLibV4, line 425
    try { // library marker kkossev.deviceProfileLibV4, line 426
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLibV4, line 427
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLibV4, line 428
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLibV4, line 429
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLibV4, line 430
        map['ep'] = (attributesMap.ep != null && attributesMap.ep != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.ep) as Integer : null // library marker kkossev.deviceProfileLibV4, line 431
    } // library marker kkossev.deviceProfileLibV4, line 432
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLibV4, line 433
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLibV4, line 434
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLibV4, line 435
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLibV4, line 436
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLibV4, line 437
    } // library marker kkossev.deviceProfileLibV4, line 438
    if ((map.mfgCode != null && map.mfgCode != '') || (map.ep != null && map.ep != '')) { // library marker kkossev.deviceProfileLibV4, line 439
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLibV4, line 440
        Map ep = map.ep != null ? ['destEndpoint':map.ep] : [:] // library marker kkossev.deviceProfileLibV4, line 441
        Map mapOptions = [:] // library marker kkossev.deviceProfileLibV4, line 442
        if (mfgCode) mapOptions.putAll(mfgCode) // library marker kkossev.deviceProfileLibV4, line 443
        if (ep) mapOptions.putAll(ep) // library marker kkossev.deviceProfileLibV4, line 444
        //log.trace "$mapOptions" // library marker kkossev.deviceProfileLibV4, line 445
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mapOptions, delay = 50) // library marker kkossev.deviceProfileLibV4, line 446
    } // library marker kkossev.deviceProfileLibV4, line 447
    else { // library marker kkossev.deviceProfileLibV4, line 448
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 50) // library marker kkossev.deviceProfileLibV4, line 449
    } // library marker kkossev.deviceProfileLibV4, line 450
    return cmds // library marker kkossev.deviceProfileLibV4, line 451
} // library marker kkossev.deviceProfileLibV4, line 452

/** // library marker kkossev.deviceProfileLibV4, line 454
 * Called from setPar() method only! // library marker kkossev.deviceProfileLibV4, line 455
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLibV4, line 456
 * // library marker kkossev.deviceProfileLibV4, line 457
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLibV4, line 458
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLibV4, line 459
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLibV4, line 460
 */ // library marker kkossev.deviceProfileLibV4, line 461
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 462
private def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLibV4, line 463
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 464
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLibV4, line 465
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 466
    def scaledValue        // // library marker kkossev.deviceProfileLibV4, line 467
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLibV4, line 468
    switch (dpMap.type) { // library marker kkossev.deviceProfileLibV4, line 469
        case 'number' : // library marker kkossev.deviceProfileLibV4, line 470
            // TODO - negative values ! // library marker kkossev.deviceProfileLibV4, line 471
            // TODO - better conversion to integer! // library marker kkossev.deviceProfileLibV4, line 472
            value = safeToInt(val, 0) // library marker kkossev.deviceProfileLibV4, line 473
            //scaledValue = value // library marker kkossev.deviceProfileLibV4, line 474
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLibV4, line 475
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLibV4, line 476
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLibV4, line 477
            } // library marker kkossev.deviceProfileLibV4, line 478
            else { // library marker kkossev.deviceProfileLibV4, line 479
                scaledValue = value // library marker kkossev.deviceProfileLibV4, line 480
            } // library marker kkossev.deviceProfileLibV4, line 481
            break // library marker kkossev.deviceProfileLibV4, line 482

        case 'decimal' : // library marker kkossev.deviceProfileLibV4, line 484
            value = safeToDouble(val, 0.0) // library marker kkossev.deviceProfileLibV4, line 485
            // scale the value // library marker kkossev.deviceProfileLibV4, line 486
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLibV4, line 487
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLibV4, line 488
            } // library marker kkossev.deviceProfileLibV4, line 489
            else { // library marker kkossev.deviceProfileLibV4, line 490
                scaledValue = value // library marker kkossev.deviceProfileLibV4, line 491
            } // library marker kkossev.deviceProfileLibV4, line 492
            break // library marker kkossev.deviceProfileLibV4, line 493

        case 'bool' : // library marker kkossev.deviceProfileLibV4, line 495
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLibV4, line 496
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLibV4, line 497
            else { // library marker kkossev.deviceProfileLibV4, line 498
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLibV4, line 499
                return null // library marker kkossev.deviceProfileLibV4, line 500
            } // library marker kkossev.deviceProfileLibV4, line 501
            break // library marker kkossev.deviceProfileLibV4, line 502
        case 'enum' : // library marker kkossev.deviceProfileLibV4, line 503
            // enums are always integer values // library marker kkossev.deviceProfileLibV4, line 504
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLibV4, line 505
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLibV4, line 506
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLibV4, line 507
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLibV4, line 508
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLibV4, line 509
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLibV4, line 510
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLibV4, line 511
            } // library marker kkossev.deviceProfileLibV4, line 512
            else { // library marker kkossev.deviceProfileLibV4, line 513
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLibV4, line 514
            } // library marker kkossev.deviceProfileLibV4, line 515
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLibV4, line 516
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLibV4, line 517
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLibV4, line 518
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLibV4, line 519
                // find the key for the value // library marker kkossev.deviceProfileLibV4, line 520
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLibV4, line 521
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLibV4, line 522
                if (key == null) { // library marker kkossev.deviceProfileLibV4, line 523
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLibV4, line 524
                    return null // library marker kkossev.deviceProfileLibV4, line 525
                } // library marker kkossev.deviceProfileLibV4, line 526
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLibV4, line 527
            //return null // library marker kkossev.deviceProfileLibV4, line 528
            } // library marker kkossev.deviceProfileLibV4, line 529
            break // library marker kkossev.deviceProfileLibV4, line 530
        default : // library marker kkossev.deviceProfileLibV4, line 531
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLibV4, line 532
            return null // library marker kkossev.deviceProfileLibV4, line 533
    } // library marker kkossev.deviceProfileLibV4, line 534
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLibV4, line 535
    // check if the value is within the specified range // library marker kkossev.deviceProfileLibV4, line 536
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLibV4, line 537
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLibV4, line 538
        return null // library marker kkossev.deviceProfileLibV4, line 539
    } // library marker kkossev.deviceProfileLibV4, line 540
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLibV4, line 541
    return scaledValue // library marker kkossev.deviceProfileLibV4, line 542
} // library marker kkossev.deviceProfileLibV4, line 543

/** // library marker kkossev.deviceProfileLibV4, line 545
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLibV4, line 546
 * // library marker kkossev.deviceProfileLibV4, line 547
 * @param par The parameter name. // library marker kkossev.deviceProfileLibV4, line 548
 * @param val The parameter value. // library marker kkossev.deviceProfileLibV4, line 549
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLibV4, line 550
 */ // library marker kkossev.deviceProfileLibV4, line 551
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLibV4, line 552
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 553
    //Boolean validated = false // library marker kkossev.deviceProfileLibV4, line 554
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLibV4, line 555
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLibV4, line 556
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLibV4, line 557
    String par = parPar.trim() // library marker kkossev.deviceProfileLibV4, line 558
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLibV4, line 559
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLibV4, line 560
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLibV4, line 561
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 562
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLibV4, line 563
    if (scaledValue == null) { // library marker kkossev.deviceProfileLibV4, line 564
        log.trace "$dpMap  ${dpMap.map}" // library marker kkossev.deviceProfileLibV4, line 565
        String helpTxt = "setPar: invalid parameter ${par} value <b>${val}</b>." // library marker kkossev.deviceProfileLibV4, line 566
        if (dpMap.min != null && dpMap.max != null) { helpTxt += " Must be in the range ${dpMap.min} to ${dpMap.max}" } // library marker kkossev.deviceProfileLibV4, line 567
        if (dpMap.map != null) { helpTxt += " Must be one of ${dpMap.map}" } // library marker kkossev.deviceProfileLibV4, line 568
        logInfo helpTxt // library marker kkossev.deviceProfileLibV4, line 569
        return false // library marker kkossev.deviceProfileLibV4, line 570
    } // library marker kkossev.deviceProfileLibV4, line 571

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLibV4, line 573
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLibV4, line 574
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLibV4, line 575
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLibV4, line 576
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLibV4, line 577
        // execute the customSetFunction // library marker kkossev.deviceProfileLibV4, line 578
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLibV4, line 579
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLibV4, line 580
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLibV4, line 581
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLibV4, line 582
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLibV4, line 583
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLibV4, line 584
            return true // library marker kkossev.deviceProfileLibV4, line 585
        } // library marker kkossev.deviceProfileLibV4, line 586
        else { // library marker kkossev.deviceProfileLibV4, line 587
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLibV4, line 588
        // continue with the default processing // library marker kkossev.deviceProfileLibV4, line 589
        } // library marker kkossev.deviceProfileLibV4, line 590
    } // library marker kkossev.deviceProfileLibV4, line 591
    if (isVirtual()) { // library marker kkossev.deviceProfileLibV4, line 592
        // set a virtual attribute // library marker kkossev.deviceProfileLibV4, line 593
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 594
        def valMiscType // library marker kkossev.deviceProfileLibV4, line 595
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLibV4, line 596
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLibV4, line 597
            // find the key for the value // library marker kkossev.deviceProfileLibV4, line 598
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLibV4, line 599
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLibV4, line 600
            if (key == null) { // library marker kkossev.deviceProfileLibV4, line 601
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLibV4, line 602
                return false // library marker kkossev.deviceProfileLibV4, line 603
            } // library marker kkossev.deviceProfileLibV4, line 604
            valMiscType = dpMap.map[key as String] // library marker kkossev.deviceProfileLibV4, line 605
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLibV4, line 606
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLibV4, line 607
        } // library marker kkossev.deviceProfileLibV4, line 608
        else { // library marker kkossev.deviceProfileLibV4, line 609
            valMiscType = val // library marker kkossev.deviceProfileLibV4, line 610
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLibV4, line 611
        } // library marker kkossev.deviceProfileLibV4, line 612
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLibV4, line 613
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLibV4, line 614
        logInfo descriptionText // library marker kkossev.deviceProfileLibV4, line 615
        return true // library marker kkossev.deviceProfileLibV4, line 616
    } // library marker kkossev.deviceProfileLibV4, line 617

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLibV4, line 619
    boolean isTuyaDP // library marker kkossev.deviceProfileLibV4, line 620

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLibV4, line 622
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLibV4, line 623
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLibV4, line 624
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLibV4, line 625
        // Tuya DP // library marker kkossev.deviceProfileLibV4, line 626
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLibV4, line 627
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLibV4, line 628
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLibV4, line 629
            return false // library marker kkossev.deviceProfileLibV4, line 630
        } // library marker kkossev.deviceProfileLibV4, line 631
        else { // library marker kkossev.deviceProfileLibV4, line 632
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLibV4, line 633
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLibV4, line 634
            return false // library marker kkossev.deviceProfileLibV4, line 635
        } // library marker kkossev.deviceProfileLibV4, line 636
    } // library marker kkossev.deviceProfileLibV4, line 637
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLibV4, line 638
        // cluster:attribute // library marker kkossev.deviceProfileLibV4, line 639
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLibV4, line 640
        int signedIntScaled = convertSignedInts(scaledValue, dpMap) // library marker kkossev.deviceProfileLibV4, line 641
        cmds = zclWriteAttribute(dpMap, signedIntScaled) // library marker kkossev.deviceProfileLibV4, line 642
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLibV4, line 643
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLibV4, line 644
            return false // library marker kkossev.deviceProfileLibV4, line 645
        } // library marker kkossev.deviceProfileLibV4, line 646
    } // library marker kkossev.deviceProfileLibV4, line 647
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLibV4, line 648
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLibV4, line 649
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLibV4, line 650
    return true // library marker kkossev.deviceProfileLibV4, line 651
} // library marker kkossev.deviceProfileLibV4, line 652

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLibV4, line 654
// TODO - reuse it !!! // library marker kkossev.deviceProfileLibV4, line 655
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 656
public List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLibV4, line 657
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLibV4, line 658
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 659
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLibV4, line 660
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLibV4, line 661
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLibV4, line 662
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLibV4, line 663
        return [] // library marker kkossev.deviceProfileLibV4, line 664
    } // library marker kkossev.deviceProfileLibV4, line 665
    String dpType // library marker kkossev.deviceProfileLibV4, line 666
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLibV4, line 667
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLibV4, line 668
    } // library marker kkossev.deviceProfileLibV4, line 669
    else { // library marker kkossev.deviceProfileLibV4, line 670
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLibV4, line 671
    } // library marker kkossev.deviceProfileLibV4, line 672
    if (dpType == null) { // library marker kkossev.deviceProfileLibV4, line 673
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLibV4, line 674
        return [] // library marker kkossev.deviceProfileLibV4, line 675
    } // library marker kkossev.deviceProfileLibV4, line 676
    // sendTuyaCommand // library marker kkossev.deviceProfileLibV4, line 677
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLibV4, line 678
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLibV4, line 679
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLibV4, line 680
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLibV4, line 681
    } // library marker kkossev.deviceProfileLibV4, line 682
    else { // library marker kkossev.deviceProfileLibV4, line 683
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLibV4, line 684
    } // library marker kkossev.deviceProfileLibV4, line 685
    return cmds // library marker kkossev.deviceProfileLibV4, line 686
} // library marker kkossev.deviceProfileLibV4, line 687

private int convertSignedInts(int val, Map dpMap) { // library marker kkossev.deviceProfileLibV4, line 689
    if (dpMap.dt == '0x28') { // library marker kkossev.deviceProfileLibV4, line 690
        if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLibV4, line 691
        else { return (val as int) } // library marker kkossev.deviceProfileLibV4, line 692
    } // library marker kkossev.deviceProfileLibV4, line 693
    else if (dpMap.dt == '0x29') { // library marker kkossev.deviceProfileLibV4, line 694
        if (val > 32767) { return (val as int) - 65536 } // library marker kkossev.deviceProfileLibV4, line 695
        else { return (val as int) } // library marker kkossev.deviceProfileLibV4, line 696
    } // library marker kkossev.deviceProfileLibV4, line 697
    else { return (val as int) } // library marker kkossev.deviceProfileLibV4, line 698
} // library marker kkossev.deviceProfileLibV4, line 699

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 701
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLibV4, line 702
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 703
    //Boolean validated = false // library marker kkossev.deviceProfileLibV4, line 704
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLibV4, line 705
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'DEVICE.preferences is empty!' ; return false } // library marker kkossev.deviceProfileLibV4, line 706

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLibV4, line 708
    l//log.trace "sendAttribute: dpMap=${dpMap}" // library marker kkossev.deviceProfileLibV4, line 709
    if (dpMap == null || dpMap?.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLibV4, line 710
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLibV4, line 711
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 712
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLibV4, line 713
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLibV4, line 714
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLibV4, line 715
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLibV4, line 716
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLibV4, line 717
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLibV4, line 718
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLibV4, line 719
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLibV4, line 720
        // execute the customSetFunction // library marker kkossev.deviceProfileLibV4, line 721
        try { // library marker kkossev.deviceProfileLibV4, line 722
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLibV4, line 723
        } // library marker kkossev.deviceProfileLibV4, line 724
        catch (e) { // library marker kkossev.deviceProfileLibV4, line 725
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLibV4, line 726
            return false // library marker kkossev.deviceProfileLibV4, line 727
        } // library marker kkossev.deviceProfileLibV4, line 728
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLibV4, line 729
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLibV4, line 730
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLibV4, line 731
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLibV4, line 732
            return true // library marker kkossev.deviceProfileLibV4, line 733
        } // library marker kkossev.deviceProfileLibV4, line 734
        else { // library marker kkossev.deviceProfileLibV4, line 735
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLibV4, line 736
        // continue with the default processing // library marker kkossev.deviceProfileLibV4, line 737
        } // library marker kkossev.deviceProfileLibV4, line 738
    } // library marker kkossev.deviceProfileLibV4, line 739
    else { // library marker kkossev.deviceProfileLibV4, line 740
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLibV4, line 741
    } // library marker kkossev.deviceProfileLibV4, line 742
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLibV4, line 743
    if (isVirtual()) { // library marker kkossev.deviceProfileLibV4, line 744
        // send a virtual attribute // library marker kkossev.deviceProfileLibV4, line 745
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLibV4, line 746
        // patch !! // library marker kkossev.deviceProfileLibV4, line 747
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLibV4, line 748
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLibV4, line 749
        } // library marker kkossev.deviceProfileLibV4, line 750
        else { // library marker kkossev.deviceProfileLibV4, line 751
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLibV4, line 752
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLibV4, line 753
            logInfo descriptionText // library marker kkossev.deviceProfileLibV4, line 754
        } // library marker kkossev.deviceProfileLibV4, line 755
        return true // library marker kkossev.deviceProfileLibV4, line 756
    } // library marker kkossev.deviceProfileLibV4, line 757
    else { // library marker kkossev.deviceProfileLibV4, line 758
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLibV4, line 759
    } // library marker kkossev.deviceProfileLibV4, line 760
    boolean isTuyaDP // library marker kkossev.deviceProfileLibV4, line 761
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 762
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLibV4, line 763
    try { // library marker kkossev.deviceProfileLibV4, line 764
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLibV4, line 765
    } // library marker kkossev.deviceProfileLibV4, line 766
    catch (e) { // library marker kkossev.deviceProfileLibV4, line 767
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLibV4, line 768
        return false // library marker kkossev.deviceProfileLibV4, line 769
    } // library marker kkossev.deviceProfileLibV4, line 770
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLibV4, line 771
        // Tuya DP // library marker kkossev.deviceProfileLibV4, line 772
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLibV4, line 773
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLibV4, line 774
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLibV4, line 775
            return false // library marker kkossev.deviceProfileLibV4, line 776
        } // library marker kkossev.deviceProfileLibV4, line 777
        else { // library marker kkossev.deviceProfileLibV4, line 778
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLibV4, line 779
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLibV4, line 780
            return true // library marker kkossev.deviceProfileLibV4, line 781
        } // library marker kkossev.deviceProfileLibV4, line 782
    } // library marker kkossev.deviceProfileLibV4, line 783
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLibV4, line 784
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLibV4, line 785
    // send a virtual attribute // library marker kkossev.deviceProfileLibV4, line 786
    } // library marker kkossev.deviceProfileLibV4, line 787
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLibV4, line 788
        // cluster:attribute // library marker kkossev.deviceProfileLibV4, line 789
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLibV4, line 790
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLibV4, line 791
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLibV4, line 792
            return false // library marker kkossev.deviceProfileLibV4, line 793
        } // library marker kkossev.deviceProfileLibV4, line 794
    } // library marker kkossev.deviceProfileLibV4, line 795
    else { // library marker kkossev.deviceProfileLibV4, line 796
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLibV4, line 797
        return false // library marker kkossev.deviceProfileLibV4, line 798
    } // library marker kkossev.deviceProfileLibV4, line 799
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLibV4, line 800
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLibV4, line 801
    return true // library marker kkossev.deviceProfileLibV4, line 802
} // library marker kkossev.deviceProfileLibV4, line 803

/** // library marker kkossev.deviceProfileLibV4, line 805
 * SENDS a list of Zigbee commands to be sent to the device. // library marker kkossev.deviceProfileLibV4, line 806
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLibV4, line 807
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLibV4, line 808
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLibV4, line 809
 */ // library marker kkossev.deviceProfileLibV4, line 810
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLibV4, line 811
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLibV4, line 812
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLibV4, line 813
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLibV4, line 814
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 815
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLibV4, line 816
    if (supportedCommandsMap == null || supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLibV4, line 817
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLibV4, line 818
        return false // library marker kkossev.deviceProfileLibV4, line 819
    } // library marker kkossev.deviceProfileLibV4, line 820
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLibV4, line 821
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLibV4, line 822
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLibV4, line 823
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLibV4, line 824
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLibV4, line 825
        return false // library marker kkossev.deviceProfileLibV4, line 826
    } // library marker kkossev.deviceProfileLibV4, line 827
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 828
    def func, funcResult // library marker kkossev.deviceProfileLibV4, line 829
    try { // library marker kkossev.deviceProfileLibV4, line 830
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLibV4, line 831
        // added 01/25/2025 : the commands now can be shorted : instead of a map kay and value 'printFingerprints':'printFingerprints' we can skip the value when it is the same:  'printFingerprints:'  - the value is the same as the key // library marker kkossev.deviceProfileLibV4, line 832
        if (func == null || func == '') { // library marker kkossev.deviceProfileLibV4, line 833
            func = command // library marker kkossev.deviceProfileLibV4, line 834
        } // library marker kkossev.deviceProfileLibV4, line 835
        if (val != null && val != '') { // library marker kkossev.deviceProfileLibV4, line 836
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLibV4, line 837
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLibV4, line 838
        } // library marker kkossev.deviceProfileLibV4, line 839
        else { // library marker kkossev.deviceProfileLibV4, line 840
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLibV4, line 841
            funcResult = "${func}"() // library marker kkossev.deviceProfileLibV4, line 842
        } // library marker kkossev.deviceProfileLibV4, line 843
    } // library marker kkossev.deviceProfileLibV4, line 844
    catch (e) { // library marker kkossev.deviceProfileLibV4, line 845
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLibV4, line 846
        return false // library marker kkossev.deviceProfileLibV4, line 847
    } // library marker kkossev.deviceProfileLibV4, line 848
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLibV4, line 849
    // check if the result is a list of commands // library marker kkossev.deviceProfileLibV4, line 850
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLibV4, line 851
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLibV4, line 852
        cmds = funcResult // library marker kkossev.deviceProfileLibV4, line 853
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLibV4, line 854
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLibV4, line 855
        } // library marker kkossev.deviceProfileLibV4, line 856
    } // library marker kkossev.deviceProfileLibV4, line 857
    else if (funcResult == null) { // library marker kkossev.deviceProfileLibV4, line 858
        return false // library marker kkossev.deviceProfileLibV4, line 859
    } // library marker kkossev.deviceProfileLibV4, line 860
     else { // library marker kkossev.deviceProfileLibV4, line 861
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLibV4, line 862
        return false // library marker kkossev.deviceProfileLibV4, line 863
    } // library marker kkossev.deviceProfileLibV4, line 864
    return true // library marker kkossev.deviceProfileLibV4, line 865
} // library marker kkossev.deviceProfileLibV4, line 866

/** // library marker kkossev.deviceProfileLibV4, line 868
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLibV4, line 869
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLibV4, line 870
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLibV4, line 871
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLibV4, line 872
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLibV4, line 873
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLibV4, line 874
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLibV4, line 875
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLibV4, line 876
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLibV4, line 877
 * @return A map containing the input details. // library marker kkossev.deviceProfileLibV4, line 878
 */ // library marker kkossev.deviceProfileLibV4, line 879
public Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLibV4, line 880
    String param = paramPar.trim() // library marker kkossev.deviceProfileLibV4, line 881
    Map input = [:] // library marker kkossev.deviceProfileLibV4, line 882
    Map foundMap = [:] // library marker kkossev.deviceProfileLibV4, line 883
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLibV4, line 884
    Object preference // library marker kkossev.deviceProfileLibV4, line 885
    try { preference = DEVICE?.preferences["$param"] } // library marker kkossev.deviceProfileLibV4, line 886
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLibV4, line 887
    //  check for boolean values // library marker kkossev.deviceProfileLibV4, line 888
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } } // library marker kkossev.deviceProfileLibV4, line 889
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLibV4, line 890
    /* // library marker kkossev.deviceProfileLibV4, line 891
    // TODO - check if this is neccessary? isTuyaDP is not defined! // library marker kkossev.deviceProfileLibV4, line 892
    try { isTuyaDP = preference.isNumber() } // library marker kkossev.deviceProfileLibV4, line 893
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  } // library marker kkossev.deviceProfileLibV4, line 894
    */ // library marker kkossev.deviceProfileLibV4, line 895
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLibV4, line 896
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLibV4, line 897
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLibV4, line 898
    if (foundMap == null || foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  } // library marker kkossev.deviceProfileLibV4, line 899
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  } // library marker kkossev.deviceProfileLibV4, line 900
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) { // library marker kkossev.deviceProfileLibV4, line 901
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" } // library marker kkossev.deviceProfileLibV4, line 902
        return [:] // library marker kkossev.deviceProfileLibV4, line 903
    } // library marker kkossev.deviceProfileLibV4, line 904
    input.name = foundMap.name // library marker kkossev.deviceProfileLibV4, line 905
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLibV4, line 906
    input.title = foundMap.title // library marker kkossev.deviceProfileLibV4, line 907
    //input.description = (foundMap.description ?: foundMap.title)?.replaceAll(/<\/?b>/, '')  // if description is not defined, use the title // library marker kkossev.deviceProfileLibV4, line 908
    input.description = foundMap.description ?: ''   // if description is not defined, skip it // library marker kkossev.deviceProfileLibV4, line 909
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLibV4, line 910
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLibV4, line 911
            //input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLibV4, line 912
            input.range = "${Math.ceil(foundMap.min) as int}..${Math.ceil(foundMap.max) as int}" // library marker kkossev.deviceProfileLibV4, line 913
        } // library marker kkossev.deviceProfileLibV4, line 914
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLibV4, line 915
            if (input.description != '') { input.description += '<br>' } // library marker kkossev.deviceProfileLibV4, line 916
            input.description += "<i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLibV4, line 917
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLibV4, line 918
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLibV4, line 919
            } // library marker kkossev.deviceProfileLibV4, line 920
        } // library marker kkossev.deviceProfileLibV4, line 921
    } // library marker kkossev.deviceProfileLibV4, line 922
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLibV4, line 923
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLibV4, line 924
        input.options = foundMap.map // library marker kkossev.deviceProfileLibV4, line 925
    }/* // library marker kkossev.deviceProfileLibV4, line 926
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLibV4, line 927
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLibV4, line 928
    }*/ // library marker kkossev.deviceProfileLibV4, line 929
    else { // library marker kkossev.deviceProfileLibV4, line 930
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLibV4, line 931
        return [:] // library marker kkossev.deviceProfileLibV4, line 932
    } // library marker kkossev.deviceProfileLibV4, line 933
    if (input.defVal != null) { // library marker kkossev.deviceProfileLibV4, line 934
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLibV4, line 935
    } // library marker kkossev.deviceProfileLibV4, line 936
    return input // library marker kkossev.deviceProfileLibV4, line 937
} // library marker kkossev.deviceProfileLibV4, line 938

/** // library marker kkossev.deviceProfileLibV4, line 940
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLibV4, line 941
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLibV4, line 942
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLibV4, line 943
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLibV4, line 944
 */ // library marker kkossev.deviceProfileLibV4, line 945
public List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLibV4, line 946
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 947
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLibV4, line 948
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLibV4, line 949
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLibV4, line 950

    // Use deviceFingerprintsV4 for more efficient fingerprint matching // library marker kkossev.deviceProfileLibV4, line 952
    if (this.hasProperty('deviceFingerprintsV4') && deviceFingerprintsV4 != null) { // library marker kkossev.deviceProfileLibV4, line 953
        deviceFingerprintsV4.each { profileName, profileData -> // library marker kkossev.deviceProfileLibV4, line 954
            profileData.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLibV4, line 955
                if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLibV4, line 956
                    deviceProfile = profileName // library marker kkossev.deviceProfileLibV4, line 957
                    deviceName = fingerprint.deviceJoinName ?: profileData.description ?: UNKNOWN // library marker kkossev.deviceProfileLibV4, line 958
                    logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLibV4, line 959
                    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLibV4, line 960
                } // library marker kkossev.deviceProfileLibV4, line 961
            } // library marker kkossev.deviceProfileLibV4, line 962
        } // library marker kkossev.deviceProfileLibV4, line 963
    } else {    // TODO - check if this is needed // library marker kkossev.deviceProfileLibV4, line 964
        // Fallback to deviceProfilesV4 if deviceFingerprintsV4 is not available // library marker kkossev.deviceProfileLibV4, line 965
        deviceProfilesV4.each { profileName, profileMap -> // library marker kkossev.deviceProfileLibV4, line 966
            profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLibV4, line 967
                if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLibV4, line 968
                    deviceProfile = profileName // library marker kkossev.deviceProfileLibV4, line 969
                    deviceName = fingerprint.deviceJoinName ?: deviceProfilesV4[deviceProfile].description ?: UNKNOWN // library marker kkossev.deviceProfileLibV4, line 970
                    logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLibV4, line 971
                    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLibV4, line 972
                } // library marker kkossev.deviceProfileLibV4, line 973
            } // library marker kkossev.deviceProfileLibV4, line 974
        } // library marker kkossev.deviceProfileLibV4, line 975
    } // library marker kkossev.deviceProfileLibV4, line 976
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLibV4, line 977
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLibV4, line 978
    } // library marker kkossev.deviceProfileLibV4, line 979
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLibV4, line 980
} // library marker kkossev.deviceProfileLibV4, line 981

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLibV4, line 983
public void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLibV4, line 984
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 985

    // Store previous profile for change detection // library marker kkossev.deviceProfileLibV4, line 987
    String previousProfile = getDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 988

    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLibV4, line 990
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLibV4, line 991
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLibV4, line 992
        // don't change the device name when unknown // library marker kkossev.deviceProfileLibV4, line 993
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLibV4, line 994
    } // library marker kkossev.deviceProfileLibV4, line 995
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLibV4, line 996
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLibV4, line 997
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLibV4, line 998
        device.setName(deviceName) // library marker kkossev.deviceProfileLibV4, line 999
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLibV4, line 1000
        device.updateSetting('forcedProfile', [value:deviceProfilesV4[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLibV4, line 1001
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLibV4, line 1002

        // V4 Profile Management: Handle profile loading and changes // library marker kkossev.deviceProfileLibV4, line 1004
        if (this.hasProperty('currentProfilesV4')) { // library marker kkossev.deviceProfileLibV4, line 1005
            String dni = device?.deviceNetworkId // library marker kkossev.deviceProfileLibV4, line 1006

            // Detect profile change // library marker kkossev.deviceProfileLibV4, line 1008
            if (previousProfile != deviceProfile && previousProfile != 'UNKNOWN') { // library marker kkossev.deviceProfileLibV4, line 1009
                logInfo "Profile changed from '${previousProfile}' to '${deviceProfile}' - clearing old profile data for device ${dni}" // library marker kkossev.deviceProfileLibV4, line 1010
                currentProfilesV4.remove(dni) // library marker kkossev.deviceProfileLibV4, line 1011
            } // library marker kkossev.deviceProfileLibV4, line 1012

            // Ensure current profile is loaded // library marker kkossev.deviceProfileLibV4, line 1014
            ensureCurrentProfileLoaded() // library marker kkossev.deviceProfileLibV4, line 1015
        } // library marker kkossev.deviceProfileLibV4, line 1016
    } else { // library marker kkossev.deviceProfileLibV4, line 1017
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLibV4, line 1018
    } // library marker kkossev.deviceProfileLibV4, line 1019
} // library marker kkossev.deviceProfileLibV4, line 1020

public List<String> refreshFromConfigureReadList(List<String> refreshList) { // library marker kkossev.deviceProfileLibV4, line 1022
    logDebug "refreshFromConfigureReadList(${refreshList})" // library marker kkossev.deviceProfileLibV4, line 1023
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 1024
    if (refreshList != null && !refreshList.isEmpty()) { // library marker kkossev.deviceProfileLibV4, line 1025
        //List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLibV4, line 1026
        for (String k : refreshList) { // library marker kkossev.deviceProfileLibV4, line 1027
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLibV4, line 1028
            if (k != null) { // library marker kkossev.deviceProfileLibV4, line 1029
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLibV4, line 1030
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLibV4, line 1031
                if (map != null) { // library marker kkossev.deviceProfileLibV4, line 1032
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLibV4, line 1033
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLibV4, line 1034
                } // library marker kkossev.deviceProfileLibV4, line 1035
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLibV4, line 1036
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLibV4, line 1037
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLibV4, line 1038
                } // library marker kkossev.deviceProfileLibV4, line 1039
            } // library marker kkossev.deviceProfileLibV4, line 1040
        } // library marker kkossev.deviceProfileLibV4, line 1041
    } // library marker kkossev.deviceProfileLibV4, line 1042
    return cmds // library marker kkossev.deviceProfileLibV4, line 1043
} // library marker kkossev.deviceProfileLibV4, line 1044

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLibV4, line 1046
public List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLibV4, line 1047
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLibV4, line 1048
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 1049
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLibV4, line 1050
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLibV4, line 1051
        for (String k : refreshList) { // library marker kkossev.deviceProfileLibV4, line 1052
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLibV4, line 1053
            if (k != null) { // library marker kkossev.deviceProfileLibV4, line 1054
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLibV4, line 1055
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLibV4, line 1056
                if (map != null) { // library marker kkossev.deviceProfileLibV4, line 1057
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLibV4, line 1058
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLibV4, line 1059
                } // library marker kkossev.deviceProfileLibV4, line 1060
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLibV4, line 1061
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLibV4, line 1062
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLibV4, line 1063
                } // library marker kkossev.deviceProfileLibV4, line 1064
            } // library marker kkossev.deviceProfileLibV4, line 1065
        } // library marker kkossev.deviceProfileLibV4, line 1066
    } // library marker kkossev.deviceProfileLibV4, line 1067
    return cmds // library marker kkossev.deviceProfileLibV4, line 1068
} // library marker kkossev.deviceProfileLibV4, line 1069

// TODO! - remove? // library marker kkossev.deviceProfileLibV4, line 1071
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLibV4, line 1072
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 1073
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLibV4, line 1074
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLibV4, line 1075
    return cmds // library marker kkossev.deviceProfileLibV4, line 1076
} // library marker kkossev.deviceProfileLibV4, line 1077

// TODO ! - remove? // library marker kkossev.deviceProfileLibV4, line 1079
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLibV4, line 1080
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 1081
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLibV4, line 1082
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLibV4, line 1083
    return cmds // library marker kkossev.deviceProfileLibV4, line 1084
} // library marker kkossev.deviceProfileLibV4, line 1085

// TODO! - remove? // library marker kkossev.deviceProfileLibV4, line 1087
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLibV4, line 1088
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 1089
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLibV4, line 1090
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLibV4, line 1091
    return cmds // library marker kkossev.deviceProfileLibV4, line 1092
} // library marker kkossev.deviceProfileLibV4, line 1093

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLibV4, line 1095
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLibV4, line 1096
    // Eager loading during initialization // library marker kkossev.deviceProfileLibV4, line 1097
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 1098
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLibV4, line 1099
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLibV4, line 1100
    } // library marker kkossev.deviceProfileLibV4, line 1101
} // library marker kkossev.deviceProfileLibV4, line 1102

public void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLibV4, line 1104
    String ps = DEVICE?.device?.powerSource // library marker kkossev.deviceProfileLibV4, line 1105
    logDebug "initEventsDeviceProfile(${fullInit}) for deviceProfile=${state.deviceProfile} DEVICE?.device?.powerSource=${ps} ps.isEmpty()=${ps?.isEmpty()}" // library marker kkossev.deviceProfileLibV4, line 1106
    if (ps != null && !ps.isEmpty()) { // library marker kkossev.deviceProfileLibV4, line 1107
        sendEvent(name: 'powerSource', value: ps, descriptionText: "Power Source set to '${ps}'", type: 'digital') // library marker kkossev.deviceProfileLibV4, line 1108
    } // library marker kkossev.deviceProfileLibV4, line 1109
} // library marker kkossev.deviceProfileLibV4, line 1110

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLibV4, line 1112

// // library marker kkossev.deviceProfileLibV4, line 1114
// called from parse() // library marker kkossev.deviceProfileLibV4, line 1115
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profile // library marker kkossev.deviceProfileLibV4, line 1116
//          false - the processing can continue // library marker kkossev.deviceProfileLibV4, line 1117
// // library marker kkossev.deviceProfileLibV4, line 1118
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLibV4, line 1119
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLibV4, line 1120
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLibV4, line 1121
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLibV4, line 1122
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLibV4, line 1123
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLibV4, line 1124
    Map currentProfile = getCurrentDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 1125
    List spammyList = currentProfile?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLibV4, line 1126
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLibV4, line 1127
} // library marker kkossev.deviceProfileLibV4, line 1128

// // library marker kkossev.deviceProfileLibV4, line 1130
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLibV4, line 1131
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profile // library marker kkossev.deviceProfileLibV4, line 1132
//          false - debug logs can be generated // library marker kkossev.deviceProfileLibV4, line 1133
// // library marker kkossev.deviceProfileLibV4, line 1134
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLibV4, line 1135
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLibV4, line 1136
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLibV4, line 1137
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLibV4, line 1138
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLibV4, line 1139
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLibV4, line 1140
    Map currentProfile = getCurrentDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 1141
    List spammyList = currentProfile?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLibV4, line 1142
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLibV4, line 1143
} // library marker kkossev.deviceProfileLibV4, line 1144

// all DPs are spammy - sent periodically! (this function is not used?) // library marker kkossev.deviceProfileLibV4, line 1146
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLibV4, line 1147
    Map currentProfile = getCurrentDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 1148
    if (!currentProfile) { return false } // library marker kkossev.deviceProfileLibV4, line 1149
    Boolean isSpammy = currentProfile?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLibV4, line 1150
    return isSpammy // library marker kkossev.deviceProfileLibV4, line 1151
} // library marker kkossev.deviceProfileLibV4, line 1152

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLibV4, line 1154
private List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLibV4, line 1155
    //logTrace "compareAndConvertStrings: tuyaValue='${tuyaValue}' hubitatValue='${hubitatValue}'" // library marker kkossev.deviceProfileLibV4, line 1156
    String convertedValue = tuyaValue ?: "" // library marker kkossev.deviceProfileLibV4, line 1157
    boolean isEqual // library marker kkossev.deviceProfileLibV4, line 1158
    if (hubitatValue == null || tuyaValue == null) { // library marker kkossev.deviceProfileLibV4, line 1159
        // Per requirement: any null hubitatValue forces inequality, regardless of tuyaValue // library marker kkossev.deviceProfileLibV4, line 1160
        isEqual = false // library marker kkossev.deviceProfileLibV4, line 1161
    } else { // library marker kkossev.deviceProfileLibV4, line 1162
        // Safe comparison (may yield true only if hubitatValue non-null and matches tuyaValue) // library marker kkossev.deviceProfileLibV4, line 1163
        isEqual = ((tuyaValue as String) == (hubitatValue as String)) // library marker kkossev.deviceProfileLibV4, line 1164
    } // library marker kkossev.deviceProfileLibV4, line 1165
    if (foundItem?.scale != null && !(foundItem.scale in [0, 1])) { // library marker kkossev.deviceProfileLibV4, line 1166
        logTrace "compareAndConvertStrings: scale=${foundItem.scale} tuyaValue=${tuyaValue} convertedValue=${convertedValue} hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLibV4, line 1167
    } // library marker kkossev.deviceProfileLibV4, line 1168
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLibV4, line 1169
} // library marker kkossev.deviceProfileLibV4, line 1170

private List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLibV4, line 1172
    Integer convertedValue // library marker kkossev.deviceProfileLibV4, line 1173
    boolean isEqual // library marker kkossev.deviceProfileLibV4, line 1174
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLibV4, line 1175
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLibV4, line 1176
    } // library marker kkossev.deviceProfileLibV4, line 1177
    else { // library marker kkossev.deviceProfileLibV4, line 1178
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLibV4, line 1179
    } // library marker kkossev.deviceProfileLibV4, line 1180
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLibV4, line 1181
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLibV4, line 1182
} // library marker kkossev.deviceProfileLibV4, line 1183

private List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLibV4, line 1185
    Double convertedValue // library marker kkossev.deviceProfileLibV4, line 1186
    boolean isEqual // library marker kkossev.deviceProfileLibV4, line 1187
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLibV4, line 1188
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLibV4, line 1189
    } // library marker kkossev.deviceProfileLibV4, line 1190
    else { // library marker kkossev.deviceProfileLibV4, line 1191
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLibV4, line 1192
    } // library marker kkossev.deviceProfileLibV4, line 1193
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLibV4, line 1194
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLibV4, line 1195
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLibV4, line 1196
} // library marker kkossev.deviceProfileLibV4, line 1197

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 1199
private List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLibV4, line 1200
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLibV4, line 1201
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1202
    def convertedValue // library marker kkossev.deviceProfileLibV4, line 1203
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLibV4, line 1204
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLibV4, line 1205
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLibV4, line 1206
    } // library marker kkossev.deviceProfileLibV4, line 1207
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLibV4, line 1208
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLibV4, line 1209
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLibV4, line 1210
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLibV4, line 1211
            isEqual = false // library marker kkossev.deviceProfileLibV4, line 1212
        } // library marker kkossev.deviceProfileLibV4, line 1213
        else { // compare as double (float) // library marker kkossev.deviceProfileLibV4, line 1214
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLibV4, line 1215
        } // library marker kkossev.deviceProfileLibV4, line 1216
    } // library marker kkossev.deviceProfileLibV4, line 1217
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLibV4, line 1218
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLibV4, line 1219
} // library marker kkossev.deviceProfileLibV4, line 1220

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 1222
private List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLibV4, line 1223
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLibV4, line 1224
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLibV4, line 1225
    boolean isEqual // library marker kkossev.deviceProfileLibV4, line 1226
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1227
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLibV4, line 1228
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1229
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLibV4, line 1230
    switch (foundItem.type) { // library marker kkossev.deviceProfileLibV4, line 1231
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLibV4, line 1232
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLibV4, line 1233
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLibV4, line 1234
            break // library marker kkossev.deviceProfileLibV4, line 1235
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLibV4, line 1236
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLibV4, line 1237
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLibV4, line 1238
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLibV4, line 1239
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLibV4, line 1240
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLibV4, line 1241
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLibV4, line 1242
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLibV4, line 1243
            } // library marker kkossev.deviceProfileLibV4, line 1244
            else { // library marker kkossev.deviceProfileLibV4, line 1245
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLibV4, line 1246
            } // library marker kkossev.deviceProfileLibV4, line 1247
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLibV4, line 1248
            break // library marker kkossev.deviceProfileLibV4, line 1249
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLibV4, line 1250
        case 'number' : // library marker kkossev.deviceProfileLibV4, line 1251
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLibV4, line 1252
            logTrace "tuyaValue=${fncmd} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLibV4, line 1253
            break // library marker kkossev.deviceProfileLibV4, line 1254
       case 'decimal' : // library marker kkossev.deviceProfileLibV4, line 1255
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLibV4, line 1256
            logTrace "comparing as float tuyaValue=${fncmd} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLibV4, line 1257
            break // library marker kkossev.deviceProfileLibV4, line 1258
        default : // library marker kkossev.deviceProfileLibV4, line 1259
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLibV4, line 1260
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLibV4, line 1261
    } // library marker kkossev.deviceProfileLibV4, line 1262
    if (isEqual == false) { // library marker kkossev.deviceProfileLibV4, line 1263
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLibV4, line 1264
    } // library marker kkossev.deviceProfileLibV4, line 1265
    // // library marker kkossev.deviceProfileLibV4, line 1266
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLibV4, line 1267
} // library marker kkossev.deviceProfileLibV4, line 1268

// // library marker kkossev.deviceProfileLibV4, line 1270
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLibV4, line 1271
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLibV4, line 1272
// returns: (two results!) // library marker kkossev.deviceProfileLibV4, line 1273
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLibV4, line 1274
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLibV4, line 1275
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLibV4, line 1276
// // library marker kkossev.deviceProfileLibV4, line 1277
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLibV4, line 1278
// // library marker kkossev.deviceProfileLibV4, line 1279
//  TODO: refactor! // library marker kkossev.deviceProfileLibV4, line 1280
// // library marker kkossev.deviceProfileLibV4, line 1281
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLibV4, line 1282
private List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd_orig, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLibV4, line 1283
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLibV4, line 1284
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLibV4, line 1285
    int fncmd = fncmd_orig ?: 0 // library marker kkossev.deviceProfileLibV4, line 1286
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1287
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLibV4, line 1288
    boolean isEqual = false // library marker kkossev.deviceProfileLibV4, line 1289
    switch (foundItem.type) { // library marker kkossev.deviceProfileLibV4, line 1290
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLibV4, line 1291
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as String] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLibV4, line 1292
            break // library marker kkossev.deviceProfileLibV4, line 1293
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLibV4, line 1294
            //logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLibV4, line 1295
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLibV4, line 1296
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLibV4, line 1297
            //logTrace "latestEvent is ${latestEvent} dataType is ${dataType}" // library marker kkossev.deviceProfileLibV4, line 1298
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLibV4, line 1299
            if (dataType == null || dataType == 'ENUM') { // library marker kkossev.deviceProfileLibV4, line 1300
                //logTrace "compareAndConvertTuyaToHubitatEventValue: comparing as string fncmd=${fncmd} foundItem.name=${foundItem.name} foundItem.map=${foundItem.map}" // library marker kkossev.deviceProfileLibV4, line 1301
                String foundItemMapValue = foundItem.map[fncmd as String] ?: 'unknown'      // map indexes are of a type String in deviceProfilesV4 !!! // library marker kkossev.deviceProfileLibV4, line 1302
                //String foundItemMapValue = 'unknown' // library marker kkossev.deviceProfileLibV4, line 1303
                //logTrace "foundItem.map[fncmd as String] = ${foundItemMapValue}" // library marker kkossev.deviceProfileLibV4, line 1304
                //logTrace "device.currentValue(${foundItem.name}) = ${device.currentValue(foundItem.name) ?: 'unknown'}" // library marker kkossev.deviceProfileLibV4, line 1305
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItemMapValue, device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLibV4, line 1306
                //logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLibV4, line 1307
            } // library marker kkossev.deviceProfileLibV4, line 1308
            else { // library marker kkossev.deviceProfileLibV4, line 1309
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLibV4, line 1310
            } // library marker kkossev.deviceProfileLibV4, line 1311
            //logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLibV4, line 1312
            break // library marker kkossev.deviceProfileLibV4, line 1313
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLibV4, line 1314
        case 'number' : // library marker kkossev.deviceProfileLibV4, line 1315
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLibV4, line 1316
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLibV4, line 1317
            break // library marker kkossev.deviceProfileLibV4, line 1318
        case 'decimal' : // library marker kkossev.deviceProfileLibV4, line 1319
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLibV4, line 1320
            break // library marker kkossev.deviceProfileLibV4, line 1321
        default : // library marker kkossev.deviceProfileLibV4, line 1322
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLibV4, line 1323
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLibV4, line 1324
    } // library marker kkossev.deviceProfileLibV4, line 1325
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLibV4, line 1326
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLibV4, line 1327
} // library marker kkossev.deviceProfileLibV4, line 1328

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLibV4, line 1330
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLibV4, line 1331
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLibV4, line 1332
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLibV4, line 1333
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLibV4, line 1334
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLibV4, line 1335
    // check if preProc method exists // library marker kkossev.deviceProfileLibV4, line 1336
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLibV4, line 1337
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLibV4, line 1338
        return fncmd_orig // library marker kkossev.deviceProfileLibV4, line 1339
    } // library marker kkossev.deviceProfileLibV4, line 1340
    // execute the preProc function // library marker kkossev.deviceProfileLibV4, line 1341
    try { // library marker kkossev.deviceProfileLibV4, line 1342
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLibV4, line 1343
    } // library marker kkossev.deviceProfileLibV4, line 1344
    catch (e) { // library marker kkossev.deviceProfileLibV4, line 1345
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLibV4, line 1346
        return fncmd_orig // library marker kkossev.deviceProfileLibV4, line 1347
    } // library marker kkossev.deviceProfileLibV4, line 1348
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLibV4, line 1349
    return fncmd // library marker kkossev.deviceProfileLibV4, line 1350
} // library marker kkossev.deviceProfileLibV4, line 1351

// TODO: refactor! // library marker kkossev.deviceProfileLibV4, line 1353
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLibV4, line 1354
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLibV4, line 1355
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLibV4, line 1356
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLibV4, line 1357
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLibV4, line 1358
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLibV4, line 1359

    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 1361
    Map currentProfile = getCurrentDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 1362
    List<Map> attribMap = currentProfile?.attributes // library marker kkossev.deviceProfileLibV4, line 1363
    if (attribMap == null || attribMap?.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLibV4, line 1364

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLibV4, line 1366
    int value // library marker kkossev.deviceProfileLibV4, line 1367
    try { // library marker kkossev.deviceProfileLibV4, line 1368
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLibV4, line 1369
    } // library marker kkossev.deviceProfileLibV4, line 1370
    catch (e) { // library marker kkossev.deviceProfileLibV4, line 1371
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLibV4, line 1372
        return false // library marker kkossev.deviceProfileLibV4, line 1373
    } // library marker kkossev.deviceProfileLibV4, line 1374
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLibV4, line 1375
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLibV4, line 1376
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLibV4, line 1377
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLibV4, line 1378
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLibV4, line 1379
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLibV4, line 1380
        return false // library marker kkossev.deviceProfileLibV4, line 1381
    } // library marker kkossev.deviceProfileLibV4, line 1382
    value = convertSignedInts(value, foundItem) // library marker kkossev.deviceProfileLibV4, line 1383
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLibV4, line 1384
} // library marker kkossev.deviceProfileLibV4, line 1385

/** // library marker kkossev.deviceProfileLibV4, line 1387
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLibV4, line 1388
 * // library marker kkossev.deviceProfileLibV4, line 1389
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLibV4, line 1390
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLibV4, line 1391
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLibV4, line 1392
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLibV4, line 1393
 * // library marker kkossev.deviceProfileLibV4, line 1394
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLibV4, line 1395
 */ // library marker kkossev.deviceProfileLibV4, line 1396
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLibV4, line 1397
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLibV4, line 1398
    logTrace "processTuyaDPfromDeviceProfile: descMap = ${descMap}, dp = ${dp}, dp_id = ${dp_id}, fncmd_orig = ${fncmd_orig}, dp_len = ${dp_len}" // library marker kkossev.deviceProfileLibV4, line 1399
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLibV4, line 1400
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLibV4, line 1401
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLibV4, line 1402

    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 1404
    Map currentProfile = getCurrentDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 1405
    List<Map> tuyaDPsMap = currentProfile?.tuyaDPs // library marker kkossev.deviceProfileLibV4, line 1406
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLibV4, line 1407

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLibV4, line 1409
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLibV4, line 1410
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLibV4, line 1411
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLibV4, line 1412
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLibV4, line 1413
        return false // library marker kkossev.deviceProfileLibV4, line 1414
    } // library marker kkossev.deviceProfileLibV4, line 1415
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLibV4, line 1416
} // library marker kkossev.deviceProfileLibV4, line 1417

/* // library marker kkossev.deviceProfileLibV4, line 1419
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLibV4, line 1420
 */ // library marker kkossev.deviceProfileLibV4, line 1421
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLibV4, line 1422
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLibV4, line 1423
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLibV4, line 1424
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLibV4, line 1425
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLibV4, line 1426
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLibV4, line 1427
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLibV4, line 1428
        if (preProcValue != value) { // library marker kkossev.deviceProfileLibV4, line 1429
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLibV4, line 1430
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLibV4, line 1431
            value = preProcValue as int // library marker kkossev.deviceProfileLibV4, line 1432
        } // library marker kkossev.deviceProfileLibV4, line 1433
    } // library marker kkossev.deviceProfileLibV4, line 1434
    //else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLibV4, line 1435

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLibV4, line 1437
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLibV4, line 1438
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1439
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLibV4, line 1440
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLibV4, line 1441
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences?.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLibV4, line 1442
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLibV4, line 1443
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLibV4, line 1444
    boolean isEqual = false // library marker kkossev.deviceProfileLibV4, line 1445
    boolean wasChanged = false // library marker kkossev.deviceProfileLibV4, line 1446
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLibV4, line 1447
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLibV4, line 1448
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLibV4, line 1449
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLibV4, line 1450
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1451
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLibV4, line 1452
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLibV4, line 1453

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLibV4, line 1455
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLibV4, line 1456
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLibV4, line 1457
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLibV4, line 1458
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLibV4, line 1459
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLibV4, line 1460
            if (settings.logEnable) { logInfo "${descText} (Debug logging is enabled)" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLibV4, line 1461
        } // library marker kkossev.deviceProfileLibV4, line 1462
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLibV4, line 1463
    } // library marker kkossev.deviceProfileLibV4, line 1464

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLibV4, line 1466
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLibV4, line 1467
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLibV4, line 1468
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLibV4, line 1469
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLibV4, line 1470
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLibV4, line 1471
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLibV4, line 1472
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLibV4, line 1473
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLibV4, line 1474
            } // library marker kkossev.deviceProfileLibV4, line 1475
        } // library marker kkossev.deviceProfileLibV4, line 1476
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLibV4, line 1477
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLibV4, line 1478
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLibV4, line 1479
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLibV4, line 1480
            } // library marker kkossev.deviceProfileLibV4, line 1481
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLibV4, line 1482
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLibV4, line 1483
            try { // library marker kkossev.deviceProfileLibV4, line 1484
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLibV4, line 1485
                wasChanged = true // library marker kkossev.deviceProfileLibV4, line 1486
            } // library marker kkossev.deviceProfileLibV4, line 1487
            catch (e) { // library marker kkossev.deviceProfileLibV4, line 1488
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLibV4, line 1489
            } // library marker kkossev.deviceProfileLibV4, line 1490
        } // library marker kkossev.deviceProfileLibV4, line 1491
    } // library marker kkossev.deviceProfileLibV4, line 1492
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLibV4, line 1493
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLibV4, line 1494
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLibV4, line 1495
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLibV4, line 1496
    } // library marker kkossev.deviceProfileLibV4, line 1497

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLibV4, line 1499
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLibV4, line 1500
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLibV4, line 1501
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLibV4, line 1502
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLibV4, line 1503
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLibV4, line 1504
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLibV4, line 1505
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLibV4, line 1506
            if (!doNotTrace) { // library marker kkossev.deviceProfileLibV4, line 1507
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLibV4, line 1508
            } // library marker kkossev.deviceProfileLibV4, line 1509
            if (foundItem.processDuplicated == true) { // library marker kkossev.deviceProfileLibV4, line 1510
                logDebug 'processDuplicated=true -> continue' // library marker kkossev.deviceProfileLibV4, line 1511
            } // library marker kkossev.deviceProfileLibV4, line 1512

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLibV4, line 1514
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLibV4, line 1515
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLibV4, line 1516
            // continue ... // library marker kkossev.deviceProfileLibV4, line 1517
            } // library marker kkossev.deviceProfileLibV4, line 1518

            else { // library marker kkossev.deviceProfileLibV4, line 1520
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLibV4, line 1521
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLibV4, line 1522
                } // library marker kkossev.deviceProfileLibV4, line 1523
                else { // library marker kkossev.deviceProfileLibV4, line 1524
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLibV4, line 1525
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLibV4, line 1526
                } // library marker kkossev.deviceProfileLibV4, line 1527
            } // library marker kkossev.deviceProfileLibV4, line 1528
        } // library marker kkossev.deviceProfileLibV4, line 1529

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLibV4, line 1531
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLibV4, line 1532
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLibV4, line 1533
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLibV4, line 1534
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLibV4, line 1535
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLibV4, line 1536
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLibV4, line 1537
        } // library marker kkossev.deviceProfileLibV4, line 1538
        else { // library marker kkossev.deviceProfileLibV4, line 1539
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLibV4, line 1540
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLibV4, line 1541
            if (!doNotTrace) { // library marker kkossev.deviceProfileLibV4, line 1542
                logTrace "event ${name} sent w/ valueScaled ${valueScaled}" // library marker kkossev.deviceProfileLibV4, line 1543
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLibV4, line 1544
            } // library marker kkossev.deviceProfileLibV4, line 1545
        } // library marker kkossev.deviceProfileLibV4, line 1546
    } // library marker kkossev.deviceProfileLibV4, line 1547
    return true     // all processing was done here! // library marker kkossev.deviceProfileLibV4, line 1548
} // library marker kkossev.deviceProfileLibV4, line 1549

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLibV4, line 1551
public boolean validateAndFixPreferences(String debugStr) { return validateAndFixPreferences(debugStr.toBoolean() as boolean) } // library marker kkossev.deviceProfileLibV4, line 1552
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLibV4, line 1553
    //debug = true // library marker kkossev.deviceProfileLibV4, line 1554
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLibV4, line 1555
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLibV4, line 1556
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLibV4, line 1557
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1558
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLibV4, line 1559
    String settingType = '' // library marker kkossev.deviceProfileLibV4, line 1560
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLibV4, line 1561
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLibV4, line 1562
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLibV4, line 1563
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLibV4, line 1564
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLibV4, line 1565
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLibV4, line 1566
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLibV4, line 1567
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLibV4, line 1568
            validationFailures ++ // library marker kkossev.deviceProfileLibV4, line 1569
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLibV4, line 1570
            try { // library marker kkossev.deviceProfileLibV4, line 1571
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLibV4, line 1572
            } catch (e) { // library marker kkossev.deviceProfileLibV4, line 1573
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLibV4, line 1574
            } // library marker kkossev.deviceProfileLibV4, line 1575
            // first, try to use the old setting value // library marker kkossev.deviceProfileLibV4, line 1576
            try { // library marker kkossev.deviceProfileLibV4, line 1577
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLibV4, line 1578
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLibV4, line 1579
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLibV4, line 1580
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLibV4, line 1581
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLibV4, line 1582
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLibV4, line 1583
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLibV4, line 1584
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLibV4, line 1585
                    } // library marker kkossev.deviceProfileLibV4, line 1586
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLibV4, line 1587
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLibV4, line 1588
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLibV4, line 1589
                    } else { // library marker kkossev.deviceProfileLibV4, line 1590
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLibV4, line 1591
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLibV4, line 1592
                    } // library marker kkossev.deviceProfileLibV4, line 1593
                } // library marker kkossev.deviceProfileLibV4, line 1594
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLibV4, line 1595
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLibV4, line 1596
                validationFixes ++ // library marker kkossev.deviceProfileLibV4, line 1597
            } // library marker kkossev.deviceProfileLibV4, line 1598
            catch (e) { // library marker kkossev.deviceProfileLibV4, line 1599
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLibV4, line 1600
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLibV4, line 1601
                try { // library marker kkossev.deviceProfileLibV4, line 1602
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLibV4, line 1603
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLibV4, line 1604
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLibV4, line 1605
                    validationFixes ++ // library marker kkossev.deviceProfileLibV4, line 1606
                } catch (e2) { // library marker kkossev.deviceProfileLibV4, line 1607
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLibV4, line 1608
                } // library marker kkossev.deviceProfileLibV4, line 1609
            } // library marker kkossev.deviceProfileLibV4, line 1610
        } // library marker kkossev.deviceProfileLibV4, line 1611
        total ++ // library marker kkossev.deviceProfileLibV4, line 1612
    } // library marker kkossev.deviceProfileLibV4, line 1613
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLibV4, line 1614
    return true // library marker kkossev.deviceProfileLibV4, line 1615
} // library marker kkossev.deviceProfileLibV4, line 1616

public String fingerprintIt(Map profileMap, Map fingerprint) { // library marker kkossev.deviceProfileLibV4, line 1618
    if (profileMap == null) { return 'profileMap is null' } // library marker kkossev.deviceProfileLibV4, line 1619
    if (fingerprint == null) { return 'fingerprint is null' } // library marker kkossev.deviceProfileLibV4, line 1620
    Map defaultFingerprint = profileMap.defaultFingerprint ?: [:] // library marker kkossev.deviceProfileLibV4, line 1621
    // if there is no defaultFingerprint, use the fingerprint as is // library marker kkossev.deviceProfileLibV4, line 1622
    if (defaultFingerprint == [:]) { // library marker kkossev.deviceProfileLibV4, line 1623
        return fingerprint.toString() // library marker kkossev.deviceProfileLibV4, line 1624
    } // library marker kkossev.deviceProfileLibV4, line 1625
    // for the missing keys, use the default values // library marker kkossev.deviceProfileLibV4, line 1626
    String fingerprintStr = '' // library marker kkossev.deviceProfileLibV4, line 1627
    defaultFingerprint.each { key, value -> // library marker kkossev.deviceProfileLibV4, line 1628
        String keyValue = fingerprint[key] ?: value // library marker kkossev.deviceProfileLibV4, line 1629
        fingerprintStr += "${key}:'${keyValue}', " // library marker kkossev.deviceProfileLibV4, line 1630
    } // library marker kkossev.deviceProfileLibV4, line 1631
    // remove the last comma and space // library marker kkossev.deviceProfileLibV4, line 1632
    fingerprintStr = fingerprintStr[0..-3] // library marker kkossev.deviceProfileLibV4, line 1633
    return fingerprintStr // library marker kkossev.deviceProfileLibV4, line 1634
} // library marker kkossev.deviceProfileLibV4, line 1635

public void printFingerprints() { // library marker kkossev.deviceProfileLibV4, line 1637
    int count = 0 // library marker kkossev.deviceProfileLibV4, line 1638
    deviceProfilesV4.each { profileName, profileMap -> // library marker kkossev.deviceProfileLibV4, line 1639
        logInfo "Device Profile: ${profileName}" // library marker kkossev.deviceProfileLibV4, line 1640
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLibV4, line 1641
            log.info "${fingerprintIt(profileMap, fingerprint)}" // library marker kkossev.deviceProfileLibV4, line 1642
            count++ // library marker kkossev.deviceProfileLibV4, line 1643
        } // library marker kkossev.deviceProfileLibV4, line 1644
    } // library marker kkossev.deviceProfileLibV4, line 1645
    logInfo "Total fingerprints: ${count}" // library marker kkossev.deviceProfileLibV4, line 1646
} // library marker kkossev.deviceProfileLibV4, line 1647

public void printPreferences() { // library marker kkossev.deviceProfileLibV4, line 1649
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLibV4, line 1650
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLibV4, line 1651
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLibV4, line 1652
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLibV4, line 1653
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLibV4, line 1654
                log.info inputMap // library marker kkossev.deviceProfileLibV4, line 1655
            } // library marker kkossev.deviceProfileLibV4, line 1656
        } // library marker kkossev.deviceProfileLibV4, line 1657
    } // library marker kkossev.deviceProfileLibV4, line 1658
} // library marker kkossev.deviceProfileLibV4, line 1659

// ~~~~~ end include (197) kkossev.deviceProfileLibV4 ~~~~~

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDouble, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/commonLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-commonLib', // library marker kkossev.commonLib, line 4
    version: '4.0.0' // library marker kkossev.commonLib, line 5
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
  * .............................. // library marker kkossev.commonLib, line 24
  * ver. 3.5.2  2025-08-13 kkossev  - Status attribute renamed to _status_ // library marker kkossev.commonLib, line 25
  * ver. 4.0.0  2025-09-08 kkossev  - deviceProfileV4 // library marker kkossev.commonLib, line 26
  * // library marker kkossev.commonLib, line 27
  *                                   TODO:  // library marker kkossev.commonLib, line 28
  *                                   TODO:  // library marker kkossev.commonLib, line 29
  *                                   TODO: add GetInfo (endpoints list) command (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 30
  *                                   TODO: make the configure() without parameter smart - analyze the State variables and call delete states.... call ActiveAndpoints() or/amd initialize() or/and configure() // library marker kkossev.commonLib, line 31
  *                                   TODO: check - offlineCtr is not increasing? (ZBMicro); // library marker kkossev.commonLib, line 32
  *                                   TODO: check deviceCommandTimeout() // library marker kkossev.commonLib, line 33
  *                                   TODO: when device rejoins the network, read the battery percentage again (probably in custom handler, not for all devices) // library marker kkossev.commonLib, line 34
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 35
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 36
  *                                   TODO: MOVE ZDO counters to health state? // library marker kkossev.commonLib, line 37
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 38
  *                                   TODO: Versions of the main module + included libraries (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 39
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 40
  * // library marker kkossev.commonLib, line 41
*/ // library marker kkossev.commonLib, line 42

String commonLibVersion() { '4.0.0' } // library marker kkossev.commonLib, line 44
String commonLibStamp() { '2025/09/08 8:21 AM' } // library marker kkossev.commonLib, line 45

import groovy.transform.Field // library marker kkossev.commonLib, line 47
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 48
import hubitat.device.Protocol // library marker kkossev.commonLib, line 49
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 50
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 51
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 52
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 53
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 54
import java.math.BigDecimal // library marker kkossev.commonLib, line 55

metadata { // library marker kkossev.commonLib, line 57
        if (_DEBUG) { // library marker kkossev.commonLib, line 58
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 59
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 60
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 61
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 62
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 63
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 64
            ] // library marker kkossev.commonLib, line 65
        } // library marker kkossev.commonLib, line 66

        // common capabilities for all device types // library marker kkossev.commonLib, line 68
        capability 'Configuration' // library marker kkossev.commonLib, line 69
        capability 'Refresh' // library marker kkossev.commonLib, line 70
        capability 'HealthCheck' // library marker kkossev.commonLib, line 71
        capability 'PowerSource'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 72

        // common attributes for all device types // library marker kkossev.commonLib, line 74
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 75
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 76
        attribute '_status_', 'string' // library marker kkossev.commonLib, line 77

        // common commands for all device types // library marker kkossev.commonLib, line 79
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM', constraints: ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 80

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 82
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 83

    preferences { // library marker kkossev.commonLib, line 85
        // txtEnable and logEnable moved to the custom driver settings - copy& paste there ... // library marker kkossev.commonLib, line 86
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 87
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 88

        if (device) { // library marker kkossev.commonLib, line 90
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'The advanced options should be already automatically set in an optimal way for your device...Click on the "Save and Close" button when toggling this option!', defaultValue: false // library marker kkossev.commonLib, line 91
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 92
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 93
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 94
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 95
            } // library marker kkossev.commonLib, line 96
        } // library marker kkossev.commonLib, line 97
    } // library marker kkossev.commonLib, line 98
} // library marker kkossev.commonLib, line 99

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 101
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 102
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 103
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 104
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 105
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 106
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 107
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 108
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 109
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 110
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 111

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 113
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 114
] // library marker kkossev.commonLib, line 115
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 116
    defaultValue: 240, options: [2: 'Every 2 Mins', 10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 117
] // library marker kkossev.commonLib, line 118

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 120
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'], // library marker kkossev.commonLib, line 121
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 122
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 123
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 124
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 125
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 126
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 127
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 128
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 129
    '           -             '  : [key:1, function: 'configureHelp'] // library marker kkossev.commonLib, line 130
] // library marker kkossev.commonLib, line 131

public boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 133

/** // library marker kkossev.commonLib, line 135
 * Parse Zigbee message // library marker kkossev.commonLib, line 136
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 137
 */ // library marker kkossev.commonLib, line 138
public void parse(final String description) { // library marker kkossev.commonLib, line 139

    Map stateCopy = state            // .clone() throws java.lang.CloneNotSupportedException in HE platform version 2.4.1.155 ! // library marker kkossev.commonLib, line 141
    checkDriverVersion(stateCopy)    // +1 ms // library marker kkossev.commonLib, line 142
    if (state.stats != null) { state.stats?.rxCtr= (state.stats?.rxCtr ?: 0) + 1 } else { state.stats = [:] }  // updateRxStats(state) // +1 ms // library marker kkossev.commonLib, line 143
    if (state.lastRx != null) { state.lastRx?.timeStamp = unix2formattedDate(now()) } else { state.lastRx = [:] } // library marker kkossev.commonLib, line 144
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 145
    setHealthStatusOnline(state)    // +2 ms // library marker kkossev.commonLib, line 146

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 148
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 149
        if (this.respondsTo('customParseIasMessage')) { customParseIasMessage(description) } // library marker kkossev.commonLib, line 150
        else if (this.respondsTo('standardParseIasMessage')) { standardParseIasMessage(description) } // library marker kkossev.commonLib, line 151
        else if (this.respondsTo('parseIasMessage')) { parseIasMessage(description) } // library marker kkossev.commonLib, line 152
        else { logDebug "ignored IAS zone status (no IAS parser) description: $description" } // library marker kkossev.commonLib, line 153
        return // library marker kkossev.commonLib, line 154
    } // library marker kkossev.commonLib, line 155
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 156
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 157
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 158
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 159
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 160
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 161
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 162
        return // library marker kkossev.commonLib, line 163
    } // library marker kkossev.commonLib, line 164

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 166
        return // library marker kkossev.commonLib, line 167
    } // library marker kkossev.commonLib, line 168
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 169

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 171
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 172

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 174
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 175
        return // library marker kkossev.commonLib, line 176
    } // library marker kkossev.commonLib, line 177
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 178
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 179
        return // library marker kkossev.commonLib, line 180
    } // library marker kkossev.commonLib, line 181
    // // library marker kkossev.commonLib, line 182
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 183
    // // library marker kkossev.commonLib, line 184
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 185
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 186
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 187
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 188
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 189
            } // library marker kkossev.commonLib, line 190
            break // library marker kkossev.commonLib, line 191
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 192
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 193
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 194
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 195
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
    0x0000: 'Basic',             0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x0006: 'OnOff',           0x0007:'onOffConfiguration',      0x0008: 'LevelControl',  // library marker kkossev.commonLib, line 207
    0x000C: 'AnalogInput',       0x0012: 'MultistateInput',  0x0020: 'PollControl',      0x0102: 'WindowCovering',   0x0201: 'Thermostat',  0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 208
    0x0400: 'Illuminance',       0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 209
    0x0B04: 'ElectricalMeasure', 0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC03: 'FC03',            0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 210
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 211
] // library marker kkossev.commonLib, line 212

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 214
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 215
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 216
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 217
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 218
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 219
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 220
        return false // library marker kkossev.commonLib, line 221
    } // library marker kkossev.commonLib, line 222
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 223
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 224
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 225
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 226
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 227
        return true // library marker kkossev.commonLib, line 228
    } // library marker kkossev.commonLib, line 229
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 230
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 231
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 232
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 233
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 234
        return true // library marker kkossev.commonLib, line 235
    } // library marker kkossev.commonLib, line 236
    if (device?.getDataValue('model') != 'ZigUSB' && descMap.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 237
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 238
    } // library marker kkossev.commonLib, line 239
    return false // library marker kkossev.commonLib, line 240
} // library marker kkossev.commonLib, line 241

// not used - throws exception :  error groovy.lang.MissingPropertyException: No such property: rxCtr for class: java.lang.String on line 1568 (method parse) // library marker kkossev.commonLib, line 243
private static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 244
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 245
} // library marker kkossev.commonLib, line 246

public boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 248
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 249
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 250
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 251
    } // library marker kkossev.commonLib, line 252
    return false // library marker kkossev.commonLib, line 253
} // library marker kkossev.commonLib, line 254

public boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 256
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 257
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 258
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 259
    } // library marker kkossev.commonLib, line 260
    return false // library marker kkossev.commonLib, line 261
} // library marker kkossev.commonLib, line 262

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 264
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 265
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 266
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 267
] // library marker kkossev.commonLib, line 268

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 270
private void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 271
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 272
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 273
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 274
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 275
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 276
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 277
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 278
    List<String> cmds = [] // library marker kkossev.commonLib, line 279
    switch (clusterId) { // library marker kkossev.commonLib, line 280
        case 0x0005 : // library marker kkossev.commonLib, line 281
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 282
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 283
            // send the active endpoint response // library marker kkossev.commonLib, line 284
            cmds += ["he raw ${device?.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 285
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 286
            break // library marker kkossev.commonLib, line 287
        case 0x0006 : // library marker kkossev.commonLib, line 288
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 289
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 290
            cmds += ["he raw ${device?.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 291
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 292
            break // library marker kkossev.commonLib, line 293
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 294
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 295
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 296
            break // library marker kkossev.commonLib, line 297
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 298
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 299
            if (this.respondsTo('parseSimpleDescriptorResponse')) { parseSimpleDescriptorResponse(descMap) } // library marker kkossev.commonLib, line 300
            break // library marker kkossev.commonLib, line 301
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 302
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 303
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 304
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 305
            break // library marker kkossev.commonLib, line 306
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 307
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 308
            break // library marker kkossev.commonLib, line 309
        case 0x0002 : // Node Descriptor Request // library marker kkossev.commonLib, line 310
        case 0x0036 : // Permit Joining Request // library marker kkossev.commonLib, line 311
        case 0x8022 : // unbind request // library marker kkossev.commonLib, line 312
        case 0x8034 : // leave response // library marker kkossev.commonLib, line 313
            if (settings?.logEnable) { log.debug "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 314
            break // library marker kkossev.commonLib, line 315
        default : // library marker kkossev.commonLib, line 316
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 317
            break // library marker kkossev.commonLib, line 318
    } // library marker kkossev.commonLib, line 319
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 320
} // library marker kkossev.commonLib, line 321

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 323
private void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 324
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 325
    switch (commandId) { // library marker kkossev.commonLib, line 326
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 327
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 328
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 329
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 330
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 331
        default: // library marker kkossev.commonLib, line 332
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 333
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 334
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 335
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 336
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 337
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 338
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 339
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 340
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 341
            } // library marker kkossev.commonLib, line 342
            break // library marker kkossev.commonLib, line 343
    } // library marker kkossev.commonLib, line 344
} // library marker kkossev.commonLib, line 345

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 347
private void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 348
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 349
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 350
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 351
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 352
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 353
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 354
    } // library marker kkossev.commonLib, line 355
    else { // library marker kkossev.commonLib, line 356
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 357
    } // library marker kkossev.commonLib, line 358
} // library marker kkossev.commonLib, line 359

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 361
private void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 362
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 363
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 364
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 365
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 366
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 367
    } // library marker kkossev.commonLib, line 368
    else { // library marker kkossev.commonLib, line 369
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 370
    } // library marker kkossev.commonLib, line 371
} // library marker kkossev.commonLib, line 372

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 374
private void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 375
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 376
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 377
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 378
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 379
        state.reportingEnabled = true // library marker kkossev.commonLib, line 380
    } // library marker kkossev.commonLib, line 381
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 382
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 383
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 384
    } else { // library marker kkossev.commonLib, line 385
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 386
    } // library marker kkossev.commonLib, line 387
} // library marker kkossev.commonLib, line 388

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 390
private void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 391
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 392
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 393
    if (status == 0) { // library marker kkossev.commonLib, line 394
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 395
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 396
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 397
        int delta = 0 // library marker kkossev.commonLib, line 398
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 399
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 400
        } // library marker kkossev.commonLib, line 401
        else { // library marker kkossev.commonLib, line 402
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 403
        } // library marker kkossev.commonLib, line 404
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 405
    } // library marker kkossev.commonLib, line 406
    else { // library marker kkossev.commonLib, line 407
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 408
    } // library marker kkossev.commonLib, line 409
} // library marker kkossev.commonLib, line 410

private Boolean executeCustomHandler(String handlerName, Object handlerArgs) { // library marker kkossev.commonLib, line 412
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 413
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 414
        return false // library marker kkossev.commonLib, line 415
    } // library marker kkossev.commonLib, line 416
    // execute the customHandler function // library marker kkossev.commonLib, line 417
    Boolean result = false // library marker kkossev.commonLib, line 418
    try { // library marker kkossev.commonLib, line 419
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 420
    } // library marker kkossev.commonLib, line 421
    catch (e) { // library marker kkossev.commonLib, line 422
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 423
        return false // library marker kkossev.commonLib, line 424
    } // library marker kkossev.commonLib, line 425
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 426
    return result // library marker kkossev.commonLib, line 427
} // library marker kkossev.commonLib, line 428

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 430
private void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 431
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 432
    final String commandId = data[0] // library marker kkossev.commonLib, line 433
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 434
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 435
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 436
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 437
    } else { // library marker kkossev.commonLib, line 438
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 439
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 440
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 441
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 442
        } // library marker kkossev.commonLib, line 443
    } // library marker kkossev.commonLib, line 444
} // library marker kkossev.commonLib, line 445

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 447
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 448
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 449
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 450

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 452
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 453
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 454
] // library marker kkossev.commonLib, line 455

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 457
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 458
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 459
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 460
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 461
] // library marker kkossev.commonLib, line 462

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 464
private BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 465
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 466
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 467
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 468
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 469
    return avg // library marker kkossev.commonLib, line 470
} // library marker kkossev.commonLib, line 471

private void handlePingResponse() { // library marker kkossev.commonLib, line 473
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 474
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 475
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 476

    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 478
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 479
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 480
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 481
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 482
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 483
        sendRttEvent() // library marker kkossev.commonLib, line 484
    } // library marker kkossev.commonLib, line 485
    else { // library marker kkossev.commonLib, line 486
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 487
    } // library marker kkossev.commonLib, line 488
    state.states['isPing'] = false // library marker kkossev.commonLib, line 489
} // library marker kkossev.commonLib, line 490

/* // library marker kkossev.commonLib, line 492
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 493
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 494
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 495
*/ // library marker kkossev.commonLib, line 496
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 497

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 499
private void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 500
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 501
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 502
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 503
    boolean isPing = state.states?.isPing ?: false // library marker kkossev.commonLib, line 504
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 505
        case 0x0000: // library marker kkossev.commonLib, line 506
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 507
            break // library marker kkossev.commonLib, line 508
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 509
            if (isPing) { // library marker kkossev.commonLib, line 510
                handlePingResponse() // library marker kkossev.commonLib, line 511
            } // library marker kkossev.commonLib, line 512
            else { // library marker kkossev.commonLib, line 513
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 514
            } // library marker kkossev.commonLib, line 515
            break // library marker kkossev.commonLib, line 516
        case 0x0004: // library marker kkossev.commonLib, line 517
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 518
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 519
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 520
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 521
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 522
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 523
            } // library marker kkossev.commonLib, line 524
            break // library marker kkossev.commonLib, line 525
        case 0x0005: // library marker kkossev.commonLib, line 526
            if (isPing) { // library marker kkossev.commonLib, line 527
                handlePingResponse() // library marker kkossev.commonLib, line 528
            } // library marker kkossev.commonLib, line 529
            else { // library marker kkossev.commonLib, line 530
                logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 531
                // received device model Remote Control N2 // library marker kkossev.commonLib, line 532
                String model = device.getDataValue('model') // library marker kkossev.commonLib, line 533
                if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 534
                    logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 535
                    device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 536
                } // library marker kkossev.commonLib, line 537
            } // library marker kkossev.commonLib, line 538
            break // library marker kkossev.commonLib, line 539
        case 0x0007: // library marker kkossev.commonLib, line 540
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 541
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 542
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 543
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 544
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 545
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 546
            } // library marker kkossev.commonLib, line 547
            break // library marker kkossev.commonLib, line 548
        case 0xFFDF: // library marker kkossev.commonLib, line 549
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 550
            break // library marker kkossev.commonLib, line 551
        case 0xFFE2: // library marker kkossev.commonLib, line 552
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 553
            break // library marker kkossev.commonLib, line 554
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 555
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 556
            break // library marker kkossev.commonLib, line 557
        case 0xFFFE: // library marker kkossev.commonLib, line 558
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 559
            break // library marker kkossev.commonLib, line 560
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 561
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 562
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 563
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 564
            break // library marker kkossev.commonLib, line 565
        default: // library marker kkossev.commonLib, line 566
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 567
            break // library marker kkossev.commonLib, line 568
    } // library marker kkossev.commonLib, line 569
} // library marker kkossev.commonLib, line 570

private void standardParsePollControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 572
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 573
        case 0x0000: logDebug "PollControl cluster: CheckInInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 574
        case 0x0001: logDebug "PollControl cluster: LongPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 575
        case 0x0002: logDebug "PollControl cluster: ShortPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 576
        case 0x0003: logDebug "PollControl cluster: FastPollTimeout = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 577
        case 0x0004: logDebug "PollControl cluster: CheckInIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 578
        case 0x0005: logDebug "PollControl cluster: LongPollIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 579
        case 0x0006: logDebug "PollControl cluster: FastPollTimeoutMax = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 580
        default: logWarn "zigbee received unknown PollControl cluster attribute 0x${descMap.attrId} (value ${descMap.value})" ; break // library marker kkossev.commonLib, line 581
    } // library marker kkossev.commonLib, line 582
} // library marker kkossev.commonLib, line 583

public void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 585
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 586
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 587

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 589
    Map descMap = [:] // library marker kkossev.commonLib, line 590
    try { // library marker kkossev.commonLib, line 591
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 592
    } // library marker kkossev.commonLib, line 593
    catch (e1) { // library marker kkossev.commonLib, line 594
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 595
        // try alternative custom parsing // library marker kkossev.commonLib, line 596
        descMap = [:] // library marker kkossev.commonLib, line 597
        try { // library marker kkossev.commonLib, line 598
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 599
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 600
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 601
            } // library marker kkossev.commonLib, line 602
        } // library marker kkossev.commonLib, line 603
        catch (e2) { // library marker kkossev.commonLib, line 604
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 605
            return [:] // library marker kkossev.commonLib, line 606
        } // library marker kkossev.commonLib, line 607
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 608
    } // library marker kkossev.commonLib, line 609
    return descMap // library marker kkossev.commonLib, line 610
} // library marker kkossev.commonLib, line 611

// return true if the messages is processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 613
// return false if the cluster is not a Tuya cluster // library marker kkossev.commonLib, line 614
private boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 615
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 616
        return false // library marker kkossev.commonLib, line 617
    } // library marker kkossev.commonLib, line 618
    // try to parse ... // library marker kkossev.commonLib, line 619
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 620
    Map descMap = [:] // library marker kkossev.commonLib, line 621
    try { // library marker kkossev.commonLib, line 622
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 623
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 624
    } // library marker kkossev.commonLib, line 625
    catch (e) { // library marker kkossev.commonLib, line 626
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 627
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 628
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 629
        return true // library marker kkossev.commonLib, line 630
    } // library marker kkossev.commonLib, line 631

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 633
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 634
    } // library marker kkossev.commonLib, line 635
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 636
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 637
    } // library marker kkossev.commonLib, line 638
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 639
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 640
    } // library marker kkossev.commonLib, line 641
    else { // library marker kkossev.commonLib, line 642
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 643
        return false // library marker kkossev.commonLib, line 644
    } // library marker kkossev.commonLib, line 645
    return true    // processed // library marker kkossev.commonLib, line 646
} // library marker kkossev.commonLib, line 647

// return true if processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 649
private boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 650
  /* // library marker kkossev.commonLib, line 651
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 652
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 653
        return true // library marker kkossev.commonLib, line 654
    } // library marker kkossev.commonLib, line 655
*/ // library marker kkossev.commonLib, line 656
    Map descMap = [:] // library marker kkossev.commonLib, line 657
    try { // library marker kkossev.commonLib, line 658
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 659
    } // library marker kkossev.commonLib, line 660
    catch (e1) { // library marker kkossev.commonLib, line 661
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 662
        // try alternative custom parsing // library marker kkossev.commonLib, line 663
        descMap = [:] // library marker kkossev.commonLib, line 664
        try { // library marker kkossev.commonLib, line 665
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 666
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 667
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 668
            } // library marker kkossev.commonLib, line 669
        } // library marker kkossev.commonLib, line 670
        catch (e2) { // library marker kkossev.commonLib, line 671
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 672
            return true // library marker kkossev.commonLib, line 673
        } // library marker kkossev.commonLib, line 674
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 675
    } // library marker kkossev.commonLib, line 676
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 677
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 678
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 679
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 680
        return false // library marker kkossev.commonLib, line 681
    } // library marker kkossev.commonLib, line 682
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 683
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 684
    // attribute report received // library marker kkossev.commonLib, line 685
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 686
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 687
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 688
    } // library marker kkossev.commonLib, line 689
    attrData.each { // library marker kkossev.commonLib, line 690
        if (it.status == '86') { // library marker kkossev.commonLib, line 691
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 692
        // TODO - skip parsing? // library marker kkossev.commonLib, line 693
        } // library marker kkossev.commonLib, line 694
        switch (it.cluster) { // library marker kkossev.commonLib, line 695
            case '0000' : // library marker kkossev.commonLib, line 696
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 697
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 698
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 699
                } // library marker kkossev.commonLib, line 700
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 701
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 702
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 703
                } // library marker kkossev.commonLib, line 704
                else { // library marker kkossev.commonLib, line 705
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 706
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 707
                } // library marker kkossev.commonLib, line 708
                break // library marker kkossev.commonLib, line 709
            default : // library marker kkossev.commonLib, line 710
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 711
                break // library marker kkossev.commonLib, line 712
        } // switch // library marker kkossev.commonLib, line 713
    } // for each attribute // library marker kkossev.commonLib, line 714
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 715
} // library marker kkossev.commonLib, line 716

public String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 718
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 719
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 720
} // library marker kkossev.commonLib, line 721

public String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 723
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 724
} // library marker kkossev.commonLib, line 725

/* // library marker kkossev.commonLib, line 727
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 728
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 729
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 730
*/ // library marker kkossev.commonLib, line 731
private static int getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 732
private static int getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 733
private static int getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 734

// Tuya Commands // library marker kkossev.commonLib, line 736
private static int getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 737
private static int getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 738
private static int getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 739
private static int getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 740
private static int getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 741

// tuya DP type // library marker kkossev.commonLib, line 743
private static String getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 744
private static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 745
private static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 746
private static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 747
private static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 748
private static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 749

private void syncTuyaDateTime() { // library marker kkossev.commonLib, line 751
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 752
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 753
    long offset = 0 // library marker kkossev.commonLib, line 754
    int offsetHours = 0 // library marker kkossev.commonLib, line 755
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 756
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 757
    try { // library marker kkossev.commonLib, line 758
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 759
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 760
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 761
    } catch (e) { // library marker kkossev.commonLib, line 762
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 763
    } // library marker kkossev.commonLib, line 764
    // // library marker kkossev.commonLib, line 765
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 766
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 767
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 768
} // library marker kkossev.commonLib, line 769

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 771
public void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 772
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 773
        syncTuyaDateTime() // library marker kkossev.commonLib, line 774
    } // library marker kkossev.commonLib, line 775
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 776
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 777
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 778
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 779
        if (status != '00') { // library marker kkossev.commonLib, line 780
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 781
        } // library marker kkossev.commonLib, line 782
    } // library marker kkossev.commonLib, line 783
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 784
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 785
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 786
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 787
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 788
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 789
            return // library marker kkossev.commonLib, line 790
        } // library marker kkossev.commonLib, line 791
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 792
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 793
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 794
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 795
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 796
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 797
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 798
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 799
            } // library marker kkossev.commonLib, line 800
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 801
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 802
        } // library marker kkossev.commonLib, line 803
    } // library marker kkossev.commonLib, line 804
    else { // library marker kkossev.commonLib, line 805
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 806
    } // library marker kkossev.commonLib, line 807
} // library marker kkossev.commonLib, line 808

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 810
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 811
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 812
    if (this.respondsTo('ensureCurrentProfileLoaded')) { // library marker kkossev.commonLib, line 813
        ensureCurrentProfileLoaded() // library marker kkossev.commonLib, line 814
    } // library marker kkossev.commonLib, line 815
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 816
        //logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 817
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 818
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 819
        } // library marker kkossev.commonLib, line 820
    } // library marker kkossev.commonLib, line 821
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 822
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 823
        //logTrace 'standardProcessTuyaDP: processTuyaDPfromDeviceProfile exists, calling it...' // library marker kkossev.commonLib, line 824
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 825
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 826
        } // library marker kkossev.commonLib, line 827
    } // library marker kkossev.commonLib, line 828
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data} (deviceProfile = ${state.deviceProfile}, deviceProfilesV4 count = ${deviceProfilesV4?.size() ?: 0}) currentProfilesV4 = ${currentProfilesV4?.size() ?: 0} dni=${device?.deviceNetworkId} currentProfilesV4[device.deviceNetworkId]=${currentProfilesV4?."${device?.deviceNetworkId}"}" // library marker kkossev.commonLib, line 829
//    ensureCurrentProfileLoaded() // library marker kkossev.commonLib, line 830
} // library marker kkossev.commonLib, line 831

public int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 833
    int retValue = 0 // library marker kkossev.commonLib, line 834
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 835
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 836
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 837
        int power = 1 // library marker kkossev.commonLib, line 838
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 839
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 840
            power = power * 256 // library marker kkossev.commonLib, line 841
        } // library marker kkossev.commonLib, line 842
    } // library marker kkossev.commonLib, line 843
    return retValue // library marker kkossev.commonLib, line 844
} // library marker kkossev.commonLib, line 845

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 847

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 849
    List<String> cmds = [] // library marker kkossev.commonLib, line 850
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 851
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 852
    int tuyaCmd // library marker kkossev.commonLib, line 853
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified! // library marker kkossev.commonLib, line 854
    if (this.respondsTo('getDEVICE') && DEVICE?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 855
        tuyaCmd = DEVICE?.device?.tuyaCmd // library marker kkossev.commonLib, line 856
    } // library marker kkossev.commonLib, line 857
    else { // library marker kkossev.commonLib, line 858
        tuyaCmd = tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 859
    } // library marker kkossev.commonLib, line 860
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 861
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 862
    return cmds // library marker kkossev.commonLib, line 863
} // library marker kkossev.commonLib, line 864

private String getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 866

public void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 868
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 869
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 870
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 871
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 872
} // library marker kkossev.commonLib, line 873


public List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 876
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 877
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 878
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 879
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 880
} // library marker kkossev.commonLib, line 881

public List<String> queryAllTuyaDP() { // library marker kkossev.commonLib, line 883
    logTrace 'queryAllTuyaDP()' // library marker kkossev.commonLib, line 884
    List<String> cmds = zigbee.command(0xEF00, 0x03) // library marker kkossev.commonLib, line 885
    return cmds // library marker kkossev.commonLib, line 886
} // library marker kkossev.commonLib, line 887

public void aqaraBlackMagic() { // library marker kkossev.commonLib, line 889
    List<String> cmds = [] // library marker kkossev.commonLib, line 890
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 891
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 892
    } // library marker kkossev.commonLib, line 893
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 894
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 895
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 896
        return // library marker kkossev.commonLib, line 897
    } // library marker kkossev.commonLib, line 898
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 899
} // library marker kkossev.commonLib, line 900

// Invoked from configure() // library marker kkossev.commonLib, line 902
public List<String> initializeDevice() { // library marker kkossev.commonLib, line 903
    List<String> cmds = [] // library marker kkossev.commonLib, line 904
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 905
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 906
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 907
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 908
    } // library marker kkossev.commonLib, line 909
    else { logDebug 'no customInitializeDevice method defined' } // library marker kkossev.commonLib, line 910
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 911
    return cmds // library marker kkossev.commonLib, line 912
} // library marker kkossev.commonLib, line 913

// Invoked from configure() // library marker kkossev.commonLib, line 915
public List<String> configureDevice() { // library marker kkossev.commonLib, line 916
    List<String> cmds = [] // library marker kkossev.commonLib, line 917
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 918
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 919
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 920
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 921
    } // library marker kkossev.commonLib, line 922
    else { logDebug 'no customConfigureDevice method defined' } // library marker kkossev.commonLib, line 923
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 924
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 925
    return cmds // library marker kkossev.commonLib, line 926
} // library marker kkossev.commonLib, line 927

/* // library marker kkossev.commonLib, line 929
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 930
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 931
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 932
*/ // library marker kkossev.commonLib, line 933

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 935
    List<String> cmds = [] // library marker kkossev.commonLib, line 936
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 937
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 938
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 939
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 940
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 941
            } // library marker kkossev.commonLib, line 942
        } // library marker kkossev.commonLib, line 943
    } // library marker kkossev.commonLib, line 944
    return cmds // library marker kkossev.commonLib, line 945
} // library marker kkossev.commonLib, line 946

public void refresh() { // library marker kkossev.commonLib, line 948
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 949
    checkDriverVersion(state) // library marker kkossev.commonLib, line 950
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 951
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 952
        customCmds = customRefresh() // library marker kkossev.commonLib, line 953
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 954
    } // library marker kkossev.commonLib, line 955
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 956
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 957
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 958
    } // library marker kkossev.commonLib, line 959
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 960
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 961
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 962
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 963
    } // library marker kkossev.commonLib, line 964
    else { // library marker kkossev.commonLib, line 965
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 966
    } // library marker kkossev.commonLib, line 967
} // library marker kkossev.commonLib, line 968

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, 'clearRefreshRequest', [overwrite: true]) } // library marker kkossev.commonLib, line 970
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 971
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 972

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 974
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 975
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 976
        sendEvent(name: '_status_', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 977
    } // library marker kkossev.commonLib, line 978
    else { // library marker kkossev.commonLib, line 979
        logInfo "${info}" // library marker kkossev.commonLib, line 980
        sendEvent(name: '_status_', value: info, type: 'digital') // library marker kkossev.commonLib, line 981
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 982
    } // library marker kkossev.commonLib, line 983
} // library marker kkossev.commonLib, line 984

public void ping() { // library marker kkossev.commonLib, line 986
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 987
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true // library marker kkossev.commonLib, line 988
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 989
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID // library marker kkossev.commonLib, line 990
    if (isVirtual()) { runInMillis(10, 'virtualPong') } // library marker kkossev.commonLib, line 991
    else if (device.getDataValue('manufacturer') == 'Aqara') { // library marker kkossev.commonLib, line 992
        logDebug 'Aqara device ping...' // library marker kkossev.commonLib, line 993
        sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [destEndpoint: 0x01], 0) ) // library marker kkossev.commonLib, line 994
    } // library marker kkossev.commonLib, line 995
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) } // library marker kkossev.commonLib, line 996
    logDebug 'ping...' // library marker kkossev.commonLib, line 997
} // library marker kkossev.commonLib, line 998

private void virtualPong() { // library marker kkossev.commonLib, line 1000
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1001
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1002
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1003
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1004
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1005
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '9999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1006
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1007
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1008
        sendRttEvent() // library marker kkossev.commonLib, line 1009
    } // library marker kkossev.commonLib, line 1010
    else { // library marker kkossev.commonLib, line 1011
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1012
    } // library marker kkossev.commonLib, line 1013
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1014
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1015
} // library marker kkossev.commonLib, line 1016

public void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1018
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1019
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1020
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1021
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1022
    if (value == null) { // library marker kkossev.commonLib, line 1023
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1024
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1025
    } // library marker kkossev.commonLib, line 1026
    else { // library marker kkossev.commonLib, line 1027
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1028
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1029
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1030
    } // library marker kkossev.commonLib, line 1031
} // library marker kkossev.commonLib, line 1032

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1034
    if (cluster != null) { // library marker kkossev.commonLib, line 1035
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1036
    } // library marker kkossev.commonLib, line 1037
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1038
    return 'NULL' // library marker kkossev.commonLib, line 1039
} // library marker kkossev.commonLib, line 1040

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1042
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1043
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1044
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1045
} // library marker kkossev.commonLib, line 1046

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1048
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1049
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1050
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1051
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1052
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1053
    } // library marker kkossev.commonLib, line 1054
} // library marker kkossev.commonLib, line 1055

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1057
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1058
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1059
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1060
    if (state.health?.isHealthCheck == true) { // library marker kkossev.commonLib, line 1061
        logWarn 'device health check failed!' // library marker kkossev.commonLib, line 1062
        state.health?.checkCtr3 = (state.health?.checkCtr3 ?: 0 ) + 1 // library marker kkossev.commonLib, line 1063
        if (state.health?.checkCtr3 >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1064
            if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1065
                sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1066
            } // library marker kkossev.commonLib, line 1067
        } // library marker kkossev.commonLib, line 1068
        state.health['isHealthCheck'] = false // library marker kkossev.commonLib, line 1069
    } // library marker kkossev.commonLib, line 1070
} // library marker kkossev.commonLib, line 1071

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1073
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1074
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1075
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1076
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1077
    } // library marker kkossev.commonLib, line 1078
    else { // library marker kkossev.commonLib, line 1079
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1080
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1081
    } // library marker kkossev.commonLib, line 1082
} // library marker kkossev.commonLib, line 1083

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1085
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1086
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1087
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1088
} // library marker kkossev.commonLib, line 1089

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1091
private void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1092
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1093
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1094
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1095
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1096
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1097
    } // library marker kkossev.commonLib, line 1098
} // library marker kkossev.commonLib, line 1099

private void deviceHealthCheck() { // library marker kkossev.commonLib, line 1101
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1102
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1103
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1104
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1105
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1106
            logWarn 'not present!' // library marker kkossev.commonLib, line 1107
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1108
        } // library marker kkossev.commonLib, line 1109
    } // library marker kkossev.commonLib, line 1110
    else { // library marker kkossev.commonLib, line 1111
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})" // library marker kkossev.commonLib, line 1112
    } // library marker kkossev.commonLib, line 1113
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1114
    // added 03/06/2025 // library marker kkossev.commonLib, line 1115
    if (settings?.healthCheckMethod as int == 2) { // library marker kkossev.commonLib, line 1116
        state.health['isHealthCheck'] = true // library marker kkossev.commonLib, line 1117
        ping()  // proactively ping the device... // library marker kkossev.commonLib, line 1118
    } // library marker kkossev.commonLib, line 1119
} // library marker kkossev.commonLib, line 1120

private void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1122
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1123
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1124
    if (value == 'online') { // library marker kkossev.commonLib, line 1125
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1126
    } // library marker kkossev.commonLib, line 1127
    else { // library marker kkossev.commonLib, line 1128
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1129
    } // library marker kkossev.commonLib, line 1130
} // library marker kkossev.commonLib, line 1131

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1133
void updated() { // library marker kkossev.commonLib, line 1134
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1135
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1136
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1137
    unschedule() // library marker kkossev.commonLib, line 1138

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1140
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1141
        runIn(86400, 'logsOff') // library marker kkossev.commonLib, line 1142
    } // library marker kkossev.commonLib, line 1143
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1144
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1145
        runIn(1800, 'traceOff') // library marker kkossev.commonLib, line 1146
    } // library marker kkossev.commonLib, line 1147

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1149
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1150
        // schedule the periodic timer // library marker kkossev.commonLib, line 1151
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1152
        if (interval > 0) { // library marker kkossev.commonLib, line 1153
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1154
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1155
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1156
        } // library marker kkossev.commonLib, line 1157
    } // library marker kkossev.commonLib, line 1158
    else { // library marker kkossev.commonLib, line 1159
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1160
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1161
    } // library marker kkossev.commonLib, line 1162
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1163
        customUpdated() // library marker kkossev.commonLib, line 1164
    } // library marker kkossev.commonLib, line 1165

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1167
} // library marker kkossev.commonLib, line 1168

private void logsOff() { // library marker kkossev.commonLib, line 1170
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1171
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1172
} // library marker kkossev.commonLib, line 1173
private void traceOff() { // library marker kkossev.commonLib, line 1174
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1175
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1176
} // library marker kkossev.commonLib, line 1177

public void configure(String command) { // library marker kkossev.commonLib, line 1179
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1180
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1181
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1182
        return // library marker kkossev.commonLib, line 1183
    } // library marker kkossev.commonLib, line 1184
    // // library marker kkossev.commonLib, line 1185
    String func // library marker kkossev.commonLib, line 1186
    try { // library marker kkossev.commonLib, line 1187
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1188
        "$func"() // library marker kkossev.commonLib, line 1189
    } // library marker kkossev.commonLib, line 1190
    catch (e) { // library marker kkossev.commonLib, line 1191
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1192
        return // library marker kkossev.commonLib, line 1193
    } // library marker kkossev.commonLib, line 1194
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1195
} // library marker kkossev.commonLib, line 1196

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1198
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1199
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1200
} // library marker kkossev.commonLib, line 1201

public void loadAllDefaults() { // library marker kkossev.commonLib, line 1203
    logDebug 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1204
    deleteAllSettings() // library marker kkossev.commonLib, line 1205
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1206
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1207
    deleteAllStates() // library marker kkossev.commonLib, line 1208
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1209

    initialize() // library marker kkossev.commonLib, line 1211
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1212
    updated() // library marker kkossev.commonLib, line 1213
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1214
} // library marker kkossev.commonLib, line 1215

private void configureNow() { // library marker kkossev.commonLib, line 1217
    configure() // library marker kkossev.commonLib, line 1218
} // library marker kkossev.commonLib, line 1219

/** // library marker kkossev.commonLib, line 1221
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1222
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1223
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1224
 */ // library marker kkossev.commonLib, line 1225
void configure() { // library marker kkossev.commonLib, line 1226
    List<String> cmds = [] // library marker kkossev.commonLib, line 1227
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1228
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1229
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1230
    if (isTuya()) { // library marker kkossev.commonLib, line 1231
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1232
    } // library marker kkossev.commonLib, line 1233
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1234
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1235
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1236
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1237
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1238
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1239
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1240
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1241
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1242
    } // library marker kkossev.commonLib, line 1243
    else { // library marker kkossev.commonLib, line 1244
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1245
    } // library marker kkossev.commonLib, line 1246
} // library marker kkossev.commonLib, line 1247

 // Invoked when the device is installed with this driver automatically selected. // library marker kkossev.commonLib, line 1249
void installed() { // library marker kkossev.commonLib, line 1250
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1251
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1252
    // populate some default values for attributes // library marker kkossev.commonLib, line 1253
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1254
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1255
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1256
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1257
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1258
} // library marker kkossev.commonLib, line 1259

private void queryPowerSource() { // library marker kkossev.commonLib, line 1261
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1262
} // library marker kkossev.commonLib, line 1263

 // Invoked from 'LoadAllDefaults' // library marker kkossev.commonLib, line 1265
private void initialize() { // library marker kkossev.commonLib, line 1266
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1267
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1268
    if (device.getDataValue('powerSource') == null) { // library marker kkossev.commonLib, line 1269
        logInfo "initializing device powerSource 'unknown'" // library marker kkossev.commonLib, line 1270
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1271
    } // library marker kkossev.commonLib, line 1272
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1273
    updateTuyaVersion() // library marker kkossev.commonLib, line 1274
    updateAqaraVersion() // library marker kkossev.commonLib, line 1275
} // library marker kkossev.commonLib, line 1276

/* // library marker kkossev.commonLib, line 1278
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1279
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1280
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1281
*/ // library marker kkossev.commonLib, line 1282

static Integer safeToInt(Object val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1284
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1285
} // library marker kkossev.commonLib, line 1286

static Double safeToDouble(Object val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1288
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1289
} // library marker kkossev.commonLib, line 1290

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1292
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1293
} // library marker kkossev.commonLib, line 1294

public void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1296
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1297
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1298
        return // library marker kkossev.commonLib, line 1299
    } // library marker kkossev.commonLib, line 1300
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1301
    cmd.each { // library marker kkossev.commonLib, line 1302
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1303
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1304
            return // library marker kkossev.commonLib, line 1305
        } // library marker kkossev.commonLib, line 1306
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1307
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1308
    } // library marker kkossev.commonLib, line 1309
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1310
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1311
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1312
} // library marker kkossev.commonLib, line 1313

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1315

private String getDeviceInfo() { // library marker kkossev.commonLib, line 1317
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1318
} // library marker kkossev.commonLib, line 1319

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1321
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1322
} // library marker kkossev.commonLib, line 1323

//@CompileStatic // library marker kkossev.commonLib, line 1325
public void checkDriverVersion(final Map stateCopy) { // library marker kkossev.commonLib, line 1326
    if (stateCopy.driverVersion == null || driverVersionAndTimeStamp() != stateCopy.driverVersion) { // library marker kkossev.commonLib, line 1327
        logDebug "checkDriverVersion: updating the settings from the current driver version ${stateCopy.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1328
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1329
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1330
        initializeVars(false) // library marker kkossev.commonLib, line 1331
        updateTuyaVersion() // library marker kkossev.commonLib, line 1332
        updateAqaraVersion() // library marker kkossev.commonLib, line 1333
    } // library marker kkossev.commonLib, line 1334
    if (state.states == null) { state.states = [:] } ; if (state.lastRx == null) { state.lastRx = [:] } ; if (state.lastTx == null) { state.lastTx = [:] } ; if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1335
} // library marker kkossev.commonLib, line 1336

// credits @thebearmay // library marker kkossev.commonLib, line 1338
String getModel() { // library marker kkossev.commonLib, line 1339
    try { // library marker kkossev.commonLib, line 1340
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1341
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1342
    } catch (ignore) { // library marker kkossev.commonLib, line 1343
        try { // library marker kkossev.commonLib, line 1344
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1345
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1346
                return model // library marker kkossev.commonLib, line 1347
            } // library marker kkossev.commonLib, line 1348
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1349
            return '' // library marker kkossev.commonLib, line 1350
        } // library marker kkossev.commonLib, line 1351
    } // library marker kkossev.commonLib, line 1352
} // library marker kkossev.commonLib, line 1353

// credits @thebearmay // library marker kkossev.commonLib, line 1355
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1356
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1357
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1358
    String revision = tokens.last() // library marker kkossev.commonLib, line 1359
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1360
} // library marker kkossev.commonLib, line 1361

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1363
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1364
    unschedule() // library marker kkossev.commonLib, line 1365
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1366
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1367

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1369
} // library marker kkossev.commonLib, line 1370

void resetStatistics() { // library marker kkossev.commonLib, line 1372
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1373
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1374
} // library marker kkossev.commonLib, line 1375

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1377
void resetStats() { // library marker kkossev.commonLib, line 1378
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1379
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1380
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1381
    state.stats.rxCtr = 0 ; state.stats.txCtr = 0 // library marker kkossev.commonLib, line 1382
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1383
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1384
} // library marker kkossev.commonLib, line 1385

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1387
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1388
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1389
        state.clear() // library marker kkossev.commonLib, line 1390
        unschedule() // library marker kkossev.commonLib, line 1391
        resetStats() // library marker kkossev.commonLib, line 1392
        if (this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1393
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1394
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1395
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1396
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1397
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1398
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1399
    } // library marker kkossev.commonLib, line 1400

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1402
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1403
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1404
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1405
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1406

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1408
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1409
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1410
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1411
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1412
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1413
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1414

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1416

    // common libraries initialization // library marker kkossev.commonLib, line 1418
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024 // library marker kkossev.commonLib, line 1419
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024 // library marker kkossev.commonLib, line 1420
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1421
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1422
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1423
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1424
    // // library marker kkossev.commonLib, line 1425
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers! // library marker kkossev.commonLib, line 1426
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024 // library marker kkossev.commonLib, line 1427
    // // library marker kkossev.commonLib, line 1428
    // custom device driver specific initialization should be at the end // library marker kkossev.commonLib, line 1429
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1430
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1431
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1432

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1434
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1435
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1436
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1437
    if ( ep  != null) { // library marker kkossev.commonLib, line 1438
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1439
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1440
    } // library marker kkossev.commonLib, line 1441
    else { // library marker kkossev.commonLib, line 1442
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1443
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1444
    } // library marker kkossev.commonLib, line 1445
} // library marker kkossev.commonLib, line 1446

// not used!? // library marker kkossev.commonLib, line 1448
void setDestinationEP() { // library marker kkossev.commonLib, line 1449
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1450
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1451
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1452
} // library marker kkossev.commonLib, line 1453

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1455
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1456
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1457
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1458
void logError(final String msg) { if (settings?.txtEnable)   { log.error "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1459

// _DEBUG mode only // library marker kkossev.commonLib, line 1461
void getAllProperties() { // library marker kkossev.commonLib, line 1462
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1463
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1464
} // library marker kkossev.commonLib, line 1465

// delete all Preferences // library marker kkossev.commonLib, line 1467
void deleteAllSettings() { // library marker kkossev.commonLib, line 1468
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1469
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1470
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1471
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1472
} // library marker kkossev.commonLib, line 1473

// delete all attributes // library marker kkossev.commonLib, line 1475
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1476
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1477
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1478
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1479
} // library marker kkossev.commonLib, line 1480

// delete all State Variables // library marker kkossev.commonLib, line 1482
void deleteAllStates() { // library marker kkossev.commonLib, line 1483
    String stateDeleted = '' // library marker kkossev.commonLib, line 1484
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1485
    state.clear() // library marker kkossev.commonLib, line 1486
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1487
} // library marker kkossev.commonLib, line 1488

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1490
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1491
} // library marker kkossev.commonLib, line 1492

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1494
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1495
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1496
} // library marker kkossev.commonLib, line 1497

void testParse(String par) { // library marker kkossev.commonLib, line 1499
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1500
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1501
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1502
    parse(par) // library marker kkossev.commonLib, line 1503
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1504
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1505
} // library marker kkossev.commonLib, line 1506

Object testJob() { // library marker kkossev.commonLib, line 1508
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1509
} // library marker kkossev.commonLib, line 1510

/** // library marker kkossev.commonLib, line 1512
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1513
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1514
 */ // library marker kkossev.commonLib, line 1515
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1516
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1517
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1518
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1519
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1520
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1521
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1522
    String cron // library marker kkossev.commonLib, line 1523
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1524
    else { // library marker kkossev.commonLib, line 1525
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1526
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1527
    } // library marker kkossev.commonLib, line 1528
    return cron // library marker kkossev.commonLib, line 1529
} // library marker kkossev.commonLib, line 1530

// credits @thebearmay // library marker kkossev.commonLib, line 1532
String formatUptime() { // library marker kkossev.commonLib, line 1533
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1534
} // library marker kkossev.commonLib, line 1535

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1537
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1538
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1539
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1540
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1541
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1542
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1543
} // library marker kkossev.commonLib, line 1544

boolean isTuya() { // library marker kkossev.commonLib, line 1546
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1547
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1548
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1549
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1550
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1551
} // library marker kkossev.commonLib, line 1552

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1554
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1555
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1556
    if (application != null) { // library marker kkossev.commonLib, line 1557
        Integer ver // library marker kkossev.commonLib, line 1558
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1559
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1560
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1561
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1562
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1563
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1564
        } // library marker kkossev.commonLib, line 1565
    } // library marker kkossev.commonLib, line 1566
} // library marker kkossev.commonLib, line 1567

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1569

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1571
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1572
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1573
    if (application != null) { // library marker kkossev.commonLib, line 1574
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1575
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1576
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1577
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1578
        } // library marker kkossev.commonLib, line 1579
    } // library marker kkossev.commonLib, line 1580
} // library marker kkossev.commonLib, line 1581

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1583
    try { // library marker kkossev.commonLib, line 1584
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1585
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1586
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1587
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1588
    } catch (e) { // library marker kkossev.commonLib, line 1589
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1590
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1591
    } // library marker kkossev.commonLib, line 1592
} // library marker kkossev.commonLib, line 1593

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1595
    try { // library marker kkossev.commonLib, line 1596
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1597
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1598
        return date.getTime() // library marker kkossev.commonLib, line 1599
    } catch (e) { // library marker kkossev.commonLib, line 1600
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1601
        return now() // library marker kkossev.commonLib, line 1602
    } // library marker kkossev.commonLib, line 1603
} // library marker kkossev.commonLib, line 1604

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1606
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1607
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1608
    int seconds = time % 60 // library marker kkossev.commonLib, line 1609
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1610
} // library marker kkossev.commonLib, line 1611

// ~~~~~ end include (144) kkossev.commonLib ~~~~~
