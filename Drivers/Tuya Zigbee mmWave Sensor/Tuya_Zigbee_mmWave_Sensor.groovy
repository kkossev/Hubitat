/* groovylint-disable NglParseError, ImplicitReturnStatement, InsecureRandom, MethodReturnTypeRequired, MethodSize, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnusedImport, VariableName */ 
 /*
 *  Tuya Zigbee mmWave Sensor - driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/w-i-p-tuya-zigbee-mmwave-sensors/137410/1
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
 * Credits: Hubitat and Zigbee2MQTT communities, Jonathan Bradshaw, w35l3y and many others.
 *
 * ver. 3.0.6  2024-04-06 kkossev  - first version (derived from Tuya 4 In 1 driver)
 * ..............................
 * ver. 4.0.0  2025-09-04 kkossev  - deviceProfileV4 BRANCH created
 * ver. 4.0.1  2025-09-14 kkossev  - (deviceProfileV4 dev. branch) added new debug commands; added debug info in states gitHubV4 and profilesV4; added g_loadProfilesCooldown logic - prevent multiple profile loading attempts after JSON parsing errors within short time
 * ver. 4.0.2  2025-09-17 kkossev  - (deviceProfileV4 dev. branch) added HOBEIAN ZG-204ZV and TS0601 _TZE200_uli8wasj _TZE200_grgol3xp _TZE200_rhgsbacq _TZE200_y8jijhba into TS0601_HOBEIAN_RADAR profile; profilesV4 code moved to the library; temperature and humidity as custom attributes; 
 *                                   changed the default offlineCheck for mmWave sensors to 60 minutes; LoadAllDefaults reloades the profilesV4 cache from Hubitat storage;
 *                                   moved TS0601 _TZE284_iadro9bf _TZE204_iadro9bf _TZE204_qasjif9e _TZE204_ztqnh5cg into a new TS0601_TUYA_RADAR_2 profile
 * ver. 4.0.3  2025-09-19 kkossev  - (deviceProfileV4 dev. branch) cooldwown timer is started on JSON local storage read or parsing error; importUrl updated; added _TZE204_muvkrjr5 into TS0601_TUYA_RADAR_2 profile; 
 *                                   automatically load the standard JSON file from GitHub on driver installation if not present locally (one time action after installation or hub reboot)
 * ver. 4.1.0  2025-10-11 kkossev  - changed the default URLs to the development branch; added 'Update From Local Storage' command, show the JSON version and timestamp in the sendInfoEvent; 
 * ver. 4.2.0  2025-10-12 kkossev  - added 'Load User Custom Profiles From Local Storage' command and functionality (per device); show the currently loaded profile filename in the deviceProfileFile attribute;
 * ver. 4.2.1  2025-10-19 kkossev  - added attributes 'switch', 'switchOnTime', 'switchState' for NEO NAS-PS10B2; added 'blockTime', 'motionDetectionDelayTime', 'radarScene', 'sensorMode', 'distanceReportMode' for TS0225_LEAPMMW_RADAR Z2M compatibility
 * ver. 4.2.2  2026-06-27 kkossev  - added HOBEIAN ZG-204ZK 24 GHz Human Presence Detector (TS0601 _TZE200_ka8l86iu)
 * ver. 4.2.3  2026-06-28 kkossev  - added ignoreSSLIssues=true for HTTPS profile JSON downloads;
 * ver. 4.2.4  2026-07-09 kkossev  - mutiple bug fixes
 * ver. 4.2.5  2026-08-24 kkossev  - (dev.branch) added support for SONOFF SNZB-06P24 Presence Sensor
 *
 *                                   TODO: new info page on WiKi
 *                                   TODO: Show both the profile key and the profile name in the Preferences page!
 *                                   TODO: handle the Unprocessed ZDO command: cluster=8032 after hub reboot
 *                                   TODO: go to the bottom of the reason for : loadProfilesFromJSON exception: error converting JSON: Unable to determine the current character, it is not a string, number, array, or object
 *                                   TODO: do not load profiles when metadata is not available (device just paired)
 *                                   TODO: 
*/

static String version() { "4.2.5" }
static String timeStamp() {"2026/08/24 11:44 PM"}

@Field static final Boolean _DEBUG = false           // debug commands
@Field static final Boolean _TRACE_ALL = false      // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true 

@Field static final String DEFAULT_PROFILES_FILENAME = "deviceProfilesV4_mmWave.json"
@Field static String defaultGitHubURL = 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20mmWave%20Sensor/deviceProfilesV4_mmWave.json'

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

#include kkossev.commonLib
#include kkossev.deviceProfileLibV4
#include kkossev.motionLib
#include kkossev.batteryLib
#include kkossev.illuminanceLib

deviceType = "mmWaveSensor"
@Field static final String DEVICE_TYPE = "mmWaveSensor"

metadata {
    definition (
        name: 'Tuya Zigbee mmWave Sensor',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Drivers/Tuya%20Zigbee%20mmWave%20Sensor/Tuya_Zigbee_mmWave_Sensor_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {

        capability 'MotionSensor'
        capability 'IlluminanceMeasurement'
        capability 'Configuration'
        capability 'Refresh'
        capability 'Health Check'
        
        attribute 'absenceTime', 'number'                       // BlackSquareRadar only
        attribute 'batteryVoltage', 'number'
        attribute 'antiInterference', 'enum', ['0 - OFF', '1 - ON']
        attribute 'detectionDelay', 'decimal'
        attribute 'deviceProfileFile', 'string'                 // shows the currently loaded profile filename
        attribute 'distance', 'number'                          // Tuya Radar
        attribute 'distanceReporting', 'enum', ['disabled', 'enabled']
        attribute 'fadingTime', 'decimal'
        attribute 'healthStatus', 'enum', ['offline', 'online']
        attribute 'humanMotionState', 'enum', ['none', 'moving', 'small', 'stationary', 'static', 'present', 'peaceful', 'large']
        attribute 'humidity', 'number'                          // TS0601_HOBEIAN_RADAR
        attribute 'illumState', 'enum', ['dark', 'light', 'unknown']
        attribute 'keepTime', 'enum', ['10 seconds', '30 seconds', '60 seconds', '120 seconds']
        attribute 'ledIndicator', 'number'
        attribute 'maximumDistance', 'decimal'
        attribute 'minimumDistance', 'decimal'
        attribute 'motionDetectionDistance', 'decimal'          // entry_distance //changed 05/11/2024 - was 'number'
        attribute 'motionDetectionMode', 'enum', ['0 - onlyPIR', '1 - PIRandRadar', '2 - onlyRadar']    // added 07/24/2024
        attribute 'motionDetectionSensitivity', 'number'        // entry_sensitivity
        attribute 'motionDetectionDelayTime', 'decimal'         // entry_filter_time
        attribute 'occupiedTime', 'number'                      // BlackSquareRadar & LINPTECH // was existance_time
        attribute 'radarAlarmMode', 'enum',   ['0 - arm', '1 - off', '2 - alarm', '3 - doorbell']
        attribute 'radarAlarmVolume', 'enum', ['0 - low', '1 - medium', '2 - high', '3 - mute']
        attribute 'blockTime', 'decimal'
        attribute 'radarSensitivity', 'number'
        attribute 'radarScene', 'enum', ['custom', 'toilet', 'kitchen', 'hallway', 'bedroom', 'livingroom', 'meetingroom', 'default']
        attribute 'radarStatus', 'enum', ['checking', 'check_success', 'check_failure', 'others', 'comm_fault', 'radar_fault']
        attribute 'sensorMode', 'enum', ['normal', 'occupied', 'unoccupied']
        attribute 'smallMotionDetectionSensitivity', 'number'   // added 04/25/2024
        attribute 'spatialLearningState', 'enum', ['idle', 'learning', 'success', 'failed', 'timeout']  // SONOFF SNZB-06P24
        attribute 'staticDetectionDistance', 'decimal'          // added 05/1/2024
        attribute 'staticDetectionSensitivity', 'number'        // added 10/29/2023
        attribute 'switch', 'enum', ['manual', 'auto']          // NEO NAS-PS10B2
        attribute 'switchOnTime', 'number'                      // NEO NAS-PS10B2
        attribute 'switchState', 'enum', ['OFF', 'ON']          // NEO NAS-PS10B2
        attribute 'tamper', 'enum', ['clear', 'detected']
        attribute 'distanceReportMode', 'enum', ['normal', 'occupancy detection']
        attribute 'temperature', 'number'                       // TS0601_HOBEIAN_RADAR
        attribute 'unacknowledgedTime', 'number'                // AIR models
        attribute 'WARNING', 'string'
        attribute 'zoneEnable', 'number'                        // SONOFF SNZB-06P24 - raw zone enable bitmap (0xFC11:0x2016)
        attribute 'zonesEnabled', 'string'                      // SONOFF SNZB-06P24 - human readable list of the enabled detection zones
        attribute 'zonesOccupied', 'string'                     // SONOFF SNZB-06P24 - human readable list of the currently occupied zones (experimental)
        attribute 'zoneStatus', 'number'                        // SONOFF SNZB-06P24 - raw zone occupancy bitmap (0xFC11:0x2015, experimental)

        command 'sendCommand', [
            [name:'command', type: 'STRING', description: '⚡ Send a device-specific command with optional parameter value • Click the Run button to see the list of available commands', constraints: ['STRING']],
            [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']]
        ]
        command 'setPar', [
                [name:'par', type: 'STRING', description: '⚙️ Update a device preference parameter and send it to the device • Click the Run button to see the list of available parameters', constraints: ['STRING']],
                [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']]
        ]
        command 'loadStandardProfilesFromGitHub', [[name: '📥 Download and load STANDARD device profiles from GitHub<br>• Downloads latest official profiles<br>• Overwrites local file: deviceProfilesV4.json<br>• Clears any custom profiles for this device<br>• This choice persists after hub reboot']]
        command 'loadStandardProfilesFromLocalStorage', [[name: '📂 Load STANDARD device profiles from local storage<br>• Reloads from: deviceProfilesV4.json<br>• Clears any custom profiles for this device<br>• Use after manual edits to local standard JSON file<br>• This choice persists after hub reboot']]
        command 'loadUserCustomProfilesFromLocalStorage', [[name: 'filename', type: 'STRING', description: '📝 Custom JSON filename (e.g., deviceProfilesV4_custom.json)<br>• Loads CUSTOM device profiles from local storage<br>• This choice persists after hub reboot', constraints: ['STRING']]]
        if (_DEBUG) {
            command 'test', [[name: "test", type: "STRING", description: "test", defaultValue : ""]] 
            // testParse is defined in the common library
            // tuyaTest is defined in the common library
            command 'cacheTest', [[name: "action", type: "ENUM", description: "Cache action", constraints: ["Info", "Initialize", "currentProfilesV4 Dump", "Clear"], defaultValue: "Info"]]
        }
        
        // Generate fingerprints from optimized g_deviceFingerprintsV4 map (fast access!)
        // Uses pre-loaded fingerprint data instead of processing g_deviceProfilesV4
        if (g_deviceFingerprintsV4 && !g_deviceFingerprintsV4.isEmpty()) {
            g_deviceFingerprintsV4.each { profileName, fingerprintData ->
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
        if (DEVICE?.attributes?.any { it.name == 'zoneStatus' }) {      // SONOFF SNZB-06P24
            input(name: 'ignoreZoneStatus', type: 'bool', title: '<b>Ignore zone status reports</b>', description: 'The sensor reports which zone is occupied about once per second while a person is present. If you do not use the zonesOccupied attribute, turn this on to keep those events out of the event log.', defaultValue: false)
        }
    }
}


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
    if (val in [1, 2]) {
        handleMotion(true)
    }
    else {
        handleMotion(false)
    }
    return val
}

Integer motionOrNotUXLLNYWP(int val) {
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
    if (this.respondsTo('ensureCurrentProfileLoaded')) { ensureCurrentProfileLoaded() }
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseE002Cluster: zigbee received 0xE002 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logWarn "customParseE002Cluster: received unknown 0xE002 attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

void customParseFC11Cluster(final Map descMap) {
    if (this.respondsTo('ensureCurrentProfileLoaded')) { ensureCurrentProfileLoaded() }
    // SONOFF SNZB-06P24 reports the progress of the spatial learning (calibration) as a cluster-specific command 0x04,
    // not as an attribute report - such a frame arrives as a 'catchall' and has no attrId/value at all.
    if (descMap.isClusterSpecific == true) {
        if (safeToInt(hexStrToUnsignedInt(descMap.command)) == SONOFF_SPATIAL_LEARNING_CMD) { parseSonoffSpatialLearning(descMap) }
        else { logWarn "customParseFC11Cluster: received unknown 0xFC11 cluster-specific command 0x${descMap.command} (data ${descMap.data})" }
        return
    }
    // not every 0xFC11 attribute holds a number - SNZB-06P24 answers a read of 0x2017 with a ZCL ARRAY (encoding 0x48)
    // of 16 uint8, and hexStrToUnsignedInt() throws NumberFormatException on a value that wide.
    Integer value
    try { value = safeToInt(hexStrToUnsignedInt(descMap.value)) }
    catch (e) {
        logWarn "customParseFC11Cluster: 0xFC11 attribute 0x${descMap.attrId} is not a number (encoding 0x${descMap.encoding}, raw ${descMap.value}) - ignored"
        return
    }
    logTrace "customParseFC11Cluster: zigbee received 0xFC11 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logWarn "customParseFC11Cluster: received unknown 0xFC11 attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}
void customParseOccupancyCluster(final Map descMap) {
    if (this.respondsTo('ensureCurrentProfileLoaded')) { ensureCurrentProfileLoaded() } 
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseOccupancyCluster: zigbee received cluster 0x0406 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logWarn "customParseOccupancyCluster: received unknown 0x0406 attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

void customParseEC03Cluster(final Map descMap) {
    if (this.respondsTo('ensureCurrentProfileLoaded')) { ensureCurrentProfileLoaded() }
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
        case 'zoneEnable' :         // SONOFF SNZB-06P24 - also publish the bitmap as a human readable list of zones
            sendEvent(eventMap)
            sendSonoffZonesEvent('zonesEnabled', safeToInt(valueScaled), 'enabled detection zones', true)
            break
        case 'zoneStatus' :         // SONOFF SNZB-06P24 - arrives about once per second while a person is present
            if (settings?.ignoreZoneStatus == true) {
                logTrace 'zoneStatus report ignored (ignoreZoneStatus is true)'
                break
            }
            sendEvent(eventMap)
            sendSonoffZonesEvent('zonesOccupied', safeToInt(valueScaled), 'occupied zones', false)
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

    // Read-and-clear: was setDeviceNameAndProfile() just run successfully earlier in THIS SAME execution
    // (e.g. as part of a loadAllDefaults() chain)? If so, trust that fresh result unconditionally and skip
    // the forcedProfile check entirely - Hubitat's 'settings' binding does not reflect updateSetting()/
    // removeSetting() calls made earlier in the same run, so settings.forcedProfile can look stale here
    // even though it was just correctly synced (or deleted) moments ago in this very execution.
    boolean justAutoDetected = state.profilesV4?.justAutoDetected == true
    if (state.profilesV4 != null) { state.profilesV4['justAutoDetected'] = false }

    if (justAutoDetected) {
        logDebug "customUpdated: profile was just freshly auto-detected in this run (deviceProfile=${state.deviceProfile}) - skipping forcedProfile check (settings may be stale within this execution)"
    }
    else if (settings?.forcedProfile != null) {
        String forcedProfileKey = getProfileKey(settings?.forcedProfile)
        String lastAutoSyncedProfile = state.profilesV4?.lastAutoSyncedProfile
        logDebug "current state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${forcedProfileKey}, lastAutoSyncedProfile=${lastAutoSyncedProfile}"
        if (forcedProfileKey != state.deviceProfile) {
            if (forcedProfileKey == lastAutoSyncedProfile) {
                // forcedProfile still holds whatever auto-detection itself last wrote there (e.g. a stale
                // value left over from a previous device that used to occupy this DNI) - state.deviceProfile
                // has since been (re)detected to something else, so trust the fresh fingerprint match instead
                // of silently overwriting it with the stale dropdown value. Not a deliberate user override.
                logDebug "forcedProfile (${forcedProfileKey}) matches the last auto-synced value, not the current deviceProfile (${state.deviceProfile}) - ignoring as stale, not a deliberate override"
            } else {
                // forcedProfile points somewhere neither auto-detection nor the current state agrees with -
                // this is a deliberate user-chosen override, honor it
                logInfo "changing the device profile from ${state.deviceProfile} to ${forcedProfileKey}"
                state.deviceProfile = forcedProfileKey
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

    // Itterates through all settings and calls setPar() for each setting
    updateAllPreferences()

    if (getDeviceProfile() == 'SONOFF_SNZB-06P_RADAR') {
        setRefreshRequest() 
        runIn(2, customRefresh, [overwrite: true])
    }
}

void customResetStats() {
    logDebug "customResetStats()"
    state.gitHubV4 = [:]
    state.profilesV4 = [:]
}

void customInitialize() {
    logDebug "customInitialize()"
    g_OneTimeProfileLoadAttempted = false
    /*
    clearProfilesCache()    // deviceProfileLib
    ensureProfilesLoaded()
    ensureCurrentProfileLoaded()
    */
}

void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (shouldDetectDeviceProfile()) {
        setDeviceNameAndProfile()               // in deviceProfileiLib.groovy
    }
    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
    if (fullInit == true || settings?.ignoreDistance == null) { device.updateSetting('ignoreDistance', true) }
    if (fullInit == true || state.motionStarted == null) { state.motionStarted = unix2formattedDate(now()) }
    if (fullInit == true || state.gitHubV4 == null) { state.gitHubV4 = [:] }
    // NOT unconditionally reset on fullInit like the other state maps above: setDeviceNameAndProfile() (called
    // earlier in this same fullInit cycle, just above) may have already written lastAutoSyncedProfile/justAutoDetected
    // into it - wiping it here would erase that and reintroduce the stale-forcedProfile-wins bug on every full reset.
    if (state.profilesV4 == null) { state.profilesV4 = [:] }
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: '60', type: 'enum']) }
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value: true, type: 'bool']) } // since ver 4.1.0
    resetCooldownFlag()
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
    if (fullInit == true || device.currentValue('motion') == null) {
        sendEvent(name: 'motion', value: 'unknown', descriptionText: 'Motion state is unknown', type: 'digital', isStateChange: true)
    }
}

void customcheckDriverVersion(final Map stateCopy) {
    logDebug "customcheckDriverVersion()"
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
        updateIndicatorLight()
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

/*
 * -----------------------------------------------------------------------------
 * SONOFF SNZB-06P24 - detection zones and spatial learning (calibration)
 * Cluster 0xFC11, manufacturer code 0x1286 (Shenzhen Coolkit)
 * References : Zigbee2MQTT src/devices/sonoff.ts, ZHA quirk PR zigpy/zha-device-handlers#4907
 * -----------------------------------------------------------------------------
*/

@Field static final Integer SONOFF_FC11_CLUSTER = 0xFC11
@Field static final String  SONOFF_MFG_CODE     = '0x1286'
@Field static final Integer SONOFF_SPATIAL_LEARNING_CMD = 0x04
@Field static final Integer SONOFF_ZONES_COUNT  = 7
@Field static final Integer SONOFF_LEARNING_DEFAULT_TIMEOUT = 90     // seconds - used until the device tells us the real duration
@Field static final Integer SONOFF_LEARNING_GRACE = 15               // seconds added on top of the duration reported by the device
@Field static final List<String> SONOFF_ZONE_RANGES = ['0-1m', '1-1.5m', '1.5-2m', '2-2.5m', '2.5-3m', '3-3.5m', '3.5-4m']

// Zone 1 occupies BOTH bit 0 and bit 1 (the device merges the two 0.5 m slots), zones 2..7 use bit 2..bit 7.
private int sonoffZoneBitMask(final int zone) { return zone == 1 ? 0x03 : (1 << zone) }

private String sonoffZonesToString(final int bitmap) {
    List<String> zones = []
    for (int z = 1; z <= SONOFF_ZONES_COUNT; z++) {
        if ((bitmap & sonoffZoneBitMask(z)) != 0) { zones.add(z.toString()) }
    }
    return zones.isEmpty() ? 'none' : zones.join(',')
}

// infoLog=false for zonesOccupied : the device pushes it about once per second while a person is present,
// so it must not fill the log with Info lines. The event is still sent, that is what automations subscribe to.
private void sendSonoffZonesEvent(final String attribName, final int bitmap, final String what, final boolean infoLog) {
    String zones = sonoffZonesToString(bitmap)
    String descText = "${what}: ${zones}"
    sendEvent(name: attribName, value: zones, descriptionText: descText, type: 'physical')
    if (infoLog) { logInfo "${descText} (bitmap 0x${zigbee.convertToHexString(bitmap & 0xFF, 2)})" }
    else { logDebug "${descText} (bitmap 0x${zigbee.convertToHexString(bitmap & 0xFF, 2)})" }
}

private String sonoffZonesHelp() {
    List<String> help = []
    for (int z = 1; z <= SONOFF_ZONES_COUNT; z++) { help.add("${z}=${SONOFF_ZONE_RANGES[z - 1]}") }
    return help.join(' ')
}

private List<String> sonoffWriteZoneEnable(final int bitmap) {
    int bm = bitmap & 0xFF
    logInfo "setting the enabled detection zones to ${sonoffZonesToString(bm)} (bitmap 0x${zigbee.convertToHexString(bm, 2)})"
    device.updateSetting('zoneEnable', [value: bm.toString(), type: 'number'])
    // 0x19 = BITMAP16, as used by both Zigbee2MQTT and the ZHA quirk
    return zigbee.writeAttribute(SONOFF_FC11_CLUSTER, 0x2016, 0x19, bm, ['mfgCode': SONOFF_MFG_CODE], delay = 200) +
           zigbee.readAttribute(SONOFF_FC11_CLUSTER, 0x2016, ['mfgCode': SONOFF_MFG_CODE], delay = 500)
}

// sendCommand('setZone', '<zone 1..7> <on|off>')  e.g.  setZone('3 off')
List<String> setZone(String val = null) {
    if (val == null || val.trim().isEmpty()) {
        logInfo "setZone: expected '&lt;zone&gt; &lt;on|off&gt;', for example <b>3 off</b> . Zone ranges: ${sonoffZonesHelp()}"
        return []
    }
    List<String> parts = val.trim().toLowerCase().split('[\\s,:=]+') as List<String>
    int zone = safeToInt(parts[0], -1)
    if (zone < 1 || zone > SONOFF_ZONES_COUNT) {
        logWarn "setZone: invalid zone '${parts[0]}' - must be 1..${SONOFF_ZONES_COUNT} (${sonoffZonesHelp()})"
        return []
    }
    boolean enable = true
    if (parts.size() > 1) { enable = !(parts[1] in ['off', '0', 'false', 'no', 'disable', 'disabled']) }
    // the last value reported by the device is the most authoritative one, the preference is the fallback
    int bitmap = safeToInt(device.currentValue('zoneEnable'), safeToInt(settings?.zoneEnable, 0xFF))
    int mask = sonoffZoneBitMask(zone)
    int newBitmap = enable ? (bitmap | mask) : (bitmap & ~mask)
    if (newBitmap == bitmap) {
        logInfo "setZone: zone ${zone} (${SONOFF_ZONE_RANGES[zone - 1]}) is already ${enable ? 'enabled' : 'disabled'}"
        return []
    }
    logInfo "setZone: ${enable ? 'enabling' : 'disabling'} zone ${zone} (${SONOFF_ZONE_RANGES[zone - 1]})"
    return sonoffWriteZoneEnable(newBitmap)
}

// sendCommand('setAllZones', 'all' | 'none' | '1,2,5')
List<String> setAllZones(String val = null) {
    if (val == null || val.trim().isEmpty()) {
        logInfo "setAllZones: expected <b>all</b>, <b>none</b>, or a list of zones such as <b>1,2,5</b> . Zone ranges: ${sonoffZonesHelp()}"
        return []
    }
    String v = val.trim().toLowerCase()
    int bitmap = 0
    if (v in ['all', 'on', 'enable', 'enabled']) {
        for (int z = 1; z <= SONOFF_ZONES_COUNT; z++) { bitmap |= sonoffZoneBitMask(z) }
    }
    else if (v in ['none', 'off', 'disable', 'disabled']) {
        bitmap = 0
    }
    else {
        for (String p : v.split('[\\s,;]+')) {
            int z = safeToInt(p, -1)
            if (z < 1 || z > SONOFF_ZONES_COUNT) {
                logWarn "setAllZones: invalid zone '${p}' - must be 1..${SONOFF_ZONES_COUNT} (${sonoffZonesHelp()})"
                return []
            }
            bitmap |= sonoffZoneBitMask(z)
        }
    }
    return sonoffWriteZoneEnable(bitmap)
}

// little-endian hex string of the lowest 'bytes' bytes of value, as expected by the SONOFF payload
private String sonoffLongToLeHex(final long value, final int bytes) {
    StringBuilder sb = new StringBuilder()
    long v = value
    for (int i = 0; i < bytes; i++) {
        sb.append(zigbee.convertToHexString((int)(v & 0xFFL), 2))
        v = v >>> 8
    }
    return sb.toString()
}

// reads 'len' little-endian bytes starting at 'offset' from a catchall data list
private Long sonoffLeHexToLong(final List<String> data, final int offset, final int len) {
    if (data == null || data.size() < (offset + len)) { return null }
    StringBuilder sb = new StringBuilder()
    for (int i = offset + len - 1; i >= offset; i--) { sb.append(data[i]) }
    try { return Long.parseLong(sb.toString(), 16) }
    catch (e) { logWarn "sonoffLeHexToLong: exception ${e} caught while parsing '${sb}'" ; return null }
}

private void sendSpatialLearningStateEvent(final String value, final String descText) {
    sendEvent(name: 'spatialLearningState', value: value, descriptionText: descText, type: 'digital', isStateChange: true)
}

// sendCommand('spatialLearning') - starts the space learning calibration. The room must be EMPTY while it runs!
List<String> spatialLearning(String val = null) {
    // sub command 0x00 = start, followed by an uint64 little-endian sequence number (the current time in milliseconds)
    String payload = '00' + sonoffLongToLeHex(now(), 8)
    logInfo 'spatialLearning: starting the spatial learning (calibration) - LEAVE THE ROOM and keep it empty until it completes!'
    sendSpatialLearningStateEvent('learning', 'spatial learning was started')
    runIn(SONOFF_LEARNING_DEFAULT_TIMEOUT, 'spatialLearningTimeout', [overwrite: true])
    return zigbee.command(SONOFF_FC11_CLUSTER, SONOFF_SPATIAL_LEARNING_CMD, ['mfgCode': SONOFF_MFG_CODE], 200, payload)
}

void spatialLearningTimeout() {
    if (device.currentValue('spatialLearningState') == 'learning') {
        logWarn 'spatialLearning: timed out - the device did not report the result of the calibration'
        sendSpatialLearningStateEvent('timeout', 'spatial learning timed out')
    }
}

// cluster 0xFC11 cluster-specific command 0x04 sent by the device : sub command 0x01 = accepted, 0x02 = finished
void parseSonoffSpatialLearning(final Map descMap) {
    List<String> data = descMap.data as List<String>
    if (data == null || data.isEmpty()) {
        logWarn "parseSonoffSpatialLearning: empty payload (descMap=${descMap})"
        return
    }
    logDebug "parseSonoffSpatialLearning: raw payload ${data}"
    int subCmd = safeToInt(hexStrToUnsignedInt(data[0]))
    switch (subCmd) {
        case 0x01 :     // start acknowledge : sequence (uint64 LE) + expected end time (uint64 LE)
            Long seq = sonoffLeHexToLong(data, 1, 8)
            Long expectedEnd = sonoffLeHexToLong(data, 9, 8)
            if (seq == null || expectedEnd == null) {
                logWarn "parseSonoffSpatialLearning: sub command 0x01 with a too short payload ${data}"
                return
            }
            long durationMs = expectedEnd - seq
            if (durationMs < 0L) { durationMs = 0L }
            int duration = (int)(durationMs.intdiv(1000L))
            logInfo "spatialLearning: accepted by the device, it will take ${duration} seconds - keep the room EMPTY!"
            sendSpatialLearningStateEvent('learning', "spatial learning is running, ${duration} seconds remaining")
            runIn(duration + SONOFF_LEARNING_GRACE, 'spatialLearningTimeout', [overwrite: true])
            break
        case 0x02 :     // result : sequence (uint64 LE) + state (uint8) + reason (uint8)
            if (data.size() < 11) {
                logWarn "parseSonoffSpatialLearning: sub command 0x02 with a too short payload ${data}"
                return
            }
            int learnState = safeToInt(hexStrToUnsignedInt(data[9]))
            int reason = safeToInt(hexStrToUnsignedInt(data[10]))
            unschedule('spatialLearningTimeout')
            if (learnState == 0x00 && reason == 0x00) {
                logInfo 'spatialLearning: completed successfully'
                sendSpatialLearningStateEvent('success', 'spatial learning completed successfully')
            }
            else {
                logWarn "spatialLearning: FAILED (state=${learnState} reason=${reason})"
                sendSpatialLearningStateEvent('failed', "spatial learning failed (state=${learnState} reason=${reason})")
            }
            break
        default :
            logWarn "parseSonoffSpatialLearning: unknown sub command 0x${data[0]} (payload ${data})"
            break
    }
}

/*
 * -----------------------------------------------------------------------------
 * Bindings and reporting configuration, driven by the optional 'configureReporting'
 * section of the device profile. Profiles that do not declare it are not affected.
 *
 * Note on the 'bind' list : zigbee.configureReporting() already emits its own 'zdo bind' for the
 * cluster it configures, so a cluster that has a 'reporting' entry must NOT also be listed under
 * 'bind' - that just sends the same bind twice. Use 'bind' only for clusters you want bound
 * without configuring any reporting on them.
 * -----------------------------------------------------------------------------
*/

private Integer hexStringToIntSafe(final String hex) {
    if (hex == null) { return null }
    String s = hex.trim()
    if (s.toLowerCase().startsWith('0x')) { s = s.substring(2) }
    try { return Integer.parseInt(s, 16) }
    catch (e) { logWarn "hexStringToIntSafe: invalid hex value '${hex}'" ; return null }
}

List<String> customConfigureDevice() {
    List<String> cmds = []
    if (this.respondsTo('ensureCurrentProfileLoaded')) { ensureCurrentProfileLoaded() }
    Map cfg = DEVICE?.configureReporting as Map
    if (cfg == null || cfg.isEmpty()) {
        logDebug "customConfigureDevice: no 'configureReporting' section in the ${getDeviceProfile()} profile - nothing to bind or configure"
        return cmds
    }
    ((cfg.bind ?: []) as List).each { bindCluster ->
        Integer clusterInt = hexStringToIntSafe(bindCluster as String)
        if (clusterInt == null) { return }
        String clusterHex = zigbee.convertToHexString(clusterInt, 4)
        logDebug "customConfigureDevice: binding cluster 0x${clusterHex}"
        cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x${clusterHex} {${device.zigbeeId}} {}", 'delay 251']
    }
    ((cfg.reporting ?: []) as List).each { entry ->
        Map r = entry as Map
        String at = r?.at as String
        if (at == null || !at.contains(':')) { logWarn "customConfigureDevice: invalid reporting entry ${r}" ; return }
        Integer clusterInt = hexStringToIntSafe(at.split(':')[0])
        Integer attrInt    = hexStringToIntSafe(at.split(':')[1])
        Integer dt         = hexStringToIntSafe(r.dt as String)
        if (clusterInt == null || attrInt == null || dt == null) { logWarn "customConfigureDevice: invalid reporting entry ${r}" ; return }
        int minTime = safeToInt(r.min, 0)
        int maxTime = safeToInt(r.max, 3600)
        // discrete data types (bitmaps, enums, booleans) must be configured WITHOUT a reportable change value
        Integer delta = r.containsKey('delta') ? safeToInt(r.delta, 0) : null
        Map opts = r.mfgCode != null ? ['mfgCode': r.mfgCode as String] : [:]
        logDebug "customConfigureDevice: configuring reporting for ${at} dt=0x${zigbee.convertToHexString(dt, 2)} min=${minTime} max=${maxTime} delta=${delta}"
        cmds += zigbee.configureReporting(clusterInt, attrInt, dt, minTime, maxTime, delta, opts, 251)
    }
    logDebug "customConfigureDevice: cmds=${cmds}"
    return cmds
}



// -------------- new test functions - add here !!! -------------------------


// cacheTest command - manage and inspect cached data structures (currently g_deviceProfilesV4)
void cacheTest(String action) {
    String act = (action ?: 'Info').trim()
    switch(act) {
        case 'Info':
            profilesV4info()    // in deviceProfileLib
            break
        case 'Initialize':
            clearProfilesCacheInfo()  
            boolean ok = ensureProfilesLoaded()
            logInfo "cacheTest Initialize: ensureProfilesLoaded(${DEFAULT_PROFILES_FILENAME}) -> ${ok}; size now ${g_deviceProfilesV4.size()}"
            ok = ensureCurrentProfileLoaded()
            logInfo "cacheTest Initialize: ensureCurrentProfileLoaded() -> ${ok}; current profile now ${state.deviceProfile}"
            break
        case 'currentProfilesV4 Dump':
            if (g_currentProfilesV4?.isEmpty()) {
                logInfo "cacheTest g_currentProfilesV4 Dump: g_currentProfilesV4 is empty"
            } else {
                logInfo "cacheTest g_currentProfilesV4 Dump: dumping entire g_currentProfilesV4 map:"
                g_currentProfilesV4.each { dni, profileData ->
                    logInfo "cacheTest g_currentProfilesV4 Dump: DNI '${dni}' -> ${profileData}"
                }
                logInfo "cacheTest g_currentProfilesV4 Dump: completed"
            }
            break
        case 'Clear':
            clearProfilesCacheInfo()    // in deviceProfileLib
            break
        default:
            logWarn "cacheTest: unknown action '${action}'"
    }
}


void testFunc( par) {
    parse('catchall: 0104 EF00 01 01 0040 00 7770 01 00 0000 02 01 00556701000100') 
}


void test(String par) {
    long startTime = now()
    logWarn "test() started at ${startTime}"

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
                    resetCooldownFlag()
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
