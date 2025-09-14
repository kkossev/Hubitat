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
 * ver. 3.0.6  2024-04-06 kkossev  - first version (derived from Tuya 4 In 1 driver)
 * ..............................
 * ver. 4.0.0  2025-09-04 kkossev  - deviceProfileV4 BRANCH created
 * ver. 4.0.1  2025-09-14 kkossev  - (dev.branch) added new debug commands; added debug info in states gitHubV4 and profilesV4;
 *                                   
 *                                   TODO: test the state. after reboot 
 *                                   TODO: change the default offlineCheck to 30 minutes
*/

static String version() { "4.0.1" }
static String timeStamp() {"2025/09/14 8:16 AM"}

@Field static final Boolean _DEBUG = true           // debug logging
@Field static final Boolean _TRACE_ALL = false      // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true 

@Field static final String DEFAULT_PROFILES_FILENAME = "deviceProfilesV4_mmWave.json"
@Field static String defaultGitHubURL = 'https://raw.githubusercontent.com/kkossev/Hubitat/deviceProfileV4/Drivers/Tuya%20Zigbee%20mmWave%20Sensor/deviceProfilesV4_mmWave.json'

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

#include kkossev.illuminanceLib
#include kkossev.motionLib
#include kkossev.batteryLib
#include kkossev.deviceProfileLibV4
#include kkossev.commonLib

deviceType = "mmWaveSensor"
@Field static final String DEVICE_TYPE = "mmWaveSensor"

@Field static  Map g_deviceProfilesV4 = [:]
@Field static  boolean g_profilesLoading = false
@Field static  boolean g_profilesLoaded = false
@Field static  Map g_deviceFingerprintsV4 = [:]
@Field static  Map g_currentProfilesV4 = [:]  // Key: device?.deviceNetworkId, Value: complete profile data


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
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
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

void customResetStats() {
    logDebug "customResetStats()"
    state.gitHubV4 = [:]
    state.profilesV4 = [:]
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
    if (fullInit == true || state.gitHubV4 == null) { state.gitHubV4 = [:] }
    if (fullInit == true || state.profilesV4 == null) { state.profilesV4 = [:] }
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


boolean loadProfilesFromJSONstring(stringifiedJSON) {
    long startTime = now()
    
    // idempotent : don't re-parse if already populated
    if (!g_deviceProfilesV4.isEmpty()) {
        logDebug "loadProfilesFromJSON: already loaded (${g_deviceProfilesV4.size()} profiles)"
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
        // Populate g_deviceProfilesV4
        g_deviceProfilesV4.putAll(dp as Map)
        logInfo "loadProfilesFromJSON: g_deviceProfilesV4 populated with ${g_deviceProfilesV4.size()} profiles"

        // Populate g_deviceFingerprintsV4 using bulk assignment for better performance
        // Use fingerprintIt() logic to reconstruct complete fingerprint data
        Map localFingerprints = [:]
        
        g_deviceProfilesV4.each { profileKey, profileMap ->
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
        
        g_deviceFingerprintsV4.clear()
        g_deviceFingerprintsV4.putAll(localFingerprints)
        logInfo "loadProfilesFromJSON: g_deviceFingerprintsV4 populated with ${g_deviceFingerprintsV4.size()} entries"

        // Count total computed fingerprint strings
        int totalComputedFingerprints = 0
        localFingerprints.each { key, value ->
            totalComputedFingerprints += value.computedFingerprints?.size() ?: 0
        }

        // NOTE: g_profilesLoaded flag is managed by ensureProfilesLoaded(), not here
        // This keeps loadProfilesFromJSON() as a pure function
        long endTime = now()
        long executionTime = endTime - startTime
        
        logDebug "loadProfilesFromJSON: loaded ${g_deviceProfilesV4.size()} profiles: ${g_deviceProfilesV4.keySet()}"
        logDebug "loadProfilesFromJSON: populated ${g_deviceFingerprintsV4.size()} fingerprint entries"
        logDebug "loadProfilesFromJSON: pre-computed ${totalComputedFingerprints} fingerprint strings"
        logDebug "loadProfilesFromJSON: execution time: ${executionTime}ms"
        return true
        
    } catch (Exception e) {
        long endTime = now()
        long executionTime = endTime - startTime
        logError "loadProfilesFromJSON exception: error converting JSON: ${e.message} (execution time: ${executionTime}ms)"
        return false
    }
    
}

/**
 * Ensures that device profiles are loaded with thread-safe lazy loading
 * This is the main function that should be called before accessing g_deviceProfilesV4
 * @return true if profiles are loaded successfully, false otherwise
 */
private boolean ensureProfilesLoaded() {
    // Fast path: already loaded
//    if (!g_deviceProfilesV4.isEmpty() && g_profilesLoaded) {
    if (g_profilesLoaded && !g_currentProfilesV4.isEmpty()) {       // !!!!!!!!!!!!!!!!!!!!!!!! TODO - check !!!!!!!!!!!!!!!!!!!!!!!!!!
        return true
    }
    if (state.profilesV4 == null) { state.profilesV4 = [:] }   // initialize state variable if not present
    // Check if another thread is already loading
    if (g_profilesLoading) {
        // Wait briefly for other thread to finish
        for (int i = 0; i < 10; i++) {
            state.profilesV4['waitForOtherThreadCtr'] = (state.profilesV4['waitForOtherThreadCtr'] ?: 0) + 1
            sendInfoEvent "ensureProfilesLoaded: waiting <b>100ms</b> for other thread to finish loading... try ${i+1}/10"
            pauseExecution(100)
            if (g_profilesLoaded && !g_deviceProfilesV4.isEmpty()) {
                sendInfoEvent "ensureProfilesLoaded: other thread finished loading"
                return true
            }
        }
        // If still loading after wait, return false - don't interfere with other thread
        sendInfoEvent "ensureProfilesLoaded: timeout waiting for other thread, returning false"
        state.profilesV4['waitForOtherThreadTimeouts'] = (state.profilesV4['waitForOtherThreadTimeouts'] ?: 0) + 1
        return false
    }
    
    // Acquire loading lock
    g_profilesLoading = true
    try {
        // Double-check after acquiring lock
        if (g_deviceProfilesV4.isEmpty() || !g_profilesLoaded) {
            state.profilesV4['loadProfilesCtr'] = (state.profilesV4['loadProfilesCtr'] ?: 0) + 1
            sendInfoEvent "ensureProfilesLoaded: loading device profiles...(g_deviceProfilesV4.isEmpty()=${g_deviceProfilesV4.isEmpty()}, g_profilesLoaded=${g_profilesLoaded})"
            boolean result = loadProfilesFromJSON()
            if (result) {
                g_profilesLoaded = true
                sendInfoEvent "ensureProfilesLoaded: successfully loaded ${g_deviceProfilesV4.size()} g_deviceProfilesV4 profiles"
            } else {
                sendInfoEvent "ensureProfilesLoaded: failed to load device profiles"
            }
            g_profilesLoading = false
            return result
        }
        return true
    } finally {
        state.profilesV4['loadProfilesExceptionsCtr'] = (state.profilesV4['loadProfilesExceptionsCtr'] ?: 0) + 1
        g_profilesLoading = false
    }
}


// cacheTest command - manage and inspect cached data structures (currently g_deviceProfilesV4)
void cacheTest(String action) {
    String act = (action ?: 'Info').trim()
    switch(act) {
        case 'Info':
            profilesV4info()    // in deviceProfileLib
            break
        case 'Initialize':
            boolean ok = ensureProfilesLoaded()
            logInfo "cacheTest Initialize: ensureProfilesLoaded() -> ${ok}; size now ${g_deviceProfilesV4.size()}"
            break
        case 'currentProfilesV4 Dump':
            if (g_currentProfilesV4.isEmpty()) {
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

// updateFromGitHub command - download JSON profiles from GitHub and store to Hubitat local storage
void updateFromGitHub(String url = '') {
    long startTime = now()
    String gitHubUrl = url?.trim() ?: defaultGitHubURL
    String fileName = DEFAULT_PROFILES_FILENAME

    sendInfoEvent "updateFromGitHub: downloading ${fileName} from ${gitHubUrl}"
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
            state.gitHubV4['httpGetCallsCtr'] = (state.gitHubV4['httpGetCallsCtr'] ?: 0) + 1
            state.gitHubV4['httpGetLastStatus'] = resp?.status
            
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
                sendInfoEvent "downloaded ${jsonContent.length()} characters from GitHub in ${downloadDuration}ms"
                state.gitHubV4['lastDownloadSize'] = jsonContent.length()
                state.gitHubV4['lastDownloadTime'] = now()
                state.gitHubV4['lastDownloadDuration'] = downloadDuration
                
                // Validate it's actually JSON content
                if (jsonContent.length() < 100 || !jsonContent.trim().startsWith("{")) {
                    //logWarn "updateFromGitHub: ❌ Downloaded content doesn't appear to be valid JSON"
                    logWarn "updateFromGitHub: Content preview: ${jsonContent.take(200)}"
                    state.gitHubV4['lastDownloadError'] = "Invalid JSON"
                    sendInfoEvent "updateFromGitHub: ❌ Downloaded content doesn't appear to be valid JSON"
                    return
                }
                state.gitHubV4['lastDownloadError'] = null
                
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
                    state.gitHubV4['lastDownloadVersion'] = version
                    state.gitHubV4['lastDownloadTimestamp'] = timestamp
                    
                } catch (Exception jsonException) {
                    logWarn "updateFromGitHub: Could not parse JSON metadata: ${jsonException.message}"
                    state.gitHubV4['lastDownloadVersion'] = null
                    state.gitHubV4['lastDownloadTimestamp'] = null
                    sendInfoEvent "updateFromGitHub: ❌ Could not parse JSON metadata: ${jsonException.message}"
                    return
                }
                
                // Store the content to Hubitat local storage using uploadHubFile
                try {
                    long uploadStartTime = now()
                    // Use uploadHubFile to save content directly to local storage (correct API method)
                    def fileBytes = jsonContent.getBytes("UTF-8")
                    uploadHubFile(fileName, fileBytes)  // void method - no return value
                    
                    long uploadEndTime = now()
                    long uploadDuration = uploadEndTime - uploadStartTime
                    
                    logInfo "updateFromGitHub: ✅ Successfully updated ${fileName} in Hubitat local storage"
                    logInfo "updateFromGitHub: File size: ${jsonContent.length()} characters"
                    logInfo "updateFromGitHub: Performance - Upload: ${uploadDuration}ms"
                    sendInfoEvent "Successfully updated ${fileName} (${jsonContent.length()} characters) in Hubitat local storage"
                    
                    // Optional: Clear current profiles to force reload on next access
                    g_deviceProfilesV4.clear()
                    g_deviceFingerprintsV4.clear()
                    g_currentProfilesV4.clear()
                    g_profilesLoaded = false
                    g_profilesLoading = false
                    
                    logInfo "updateFromGitHub: Cleared cached profiles - they will be reloaded on next access"
                    
                    long endTime = now()
                    long totalDuration = endTime - startTime
                    logInfo "updateFromGitHub: Performance - Total: ${totalDuration}ms"
                } catch (Exception fileException) {
                    logWarn "updateFromGitHub: ❌ Error saving file: ${fileException.message}"
                    logDebug "updateFromGitHub: File save exception: ${fileException}"
                    sendInfoEvent "Error saving file: ${fileException.message}"
                    state.gitHubV4['lastDownloadError'] = "File save error"
                }
            } else {
                logWarn "updateFromGitHub: ❌ Failed to download from GitHub. HTTP status: ${resp?.status}"
                state.gitHubV4['lastDownloadError'] = "HTTP status ${resp?.status}"
                sendInfoEvent "Failed to download from GitHub. HTTP status: ${resp?.status}"
            }
        }
        
    } catch (Exception e) {
        if (state.gitHubV4 == null) { state.gitHubV4 = [:] }
        state.gitHubV4['catchedExceptionsCtr'] = (state.gitHubV4['catchedExceptionsCtr'] ?: 0) + 1
        state.gitHubV4['lastException'] = e.message
        state.gitHubV4['lastExceptionTime'] = now()
        logWarn "updateFromGitHub: ❌ Error: ${e.message}"
        logDebug "updateFromGitHub: Full exception: ${e}"
        sendInfoEvent "updateFromGitHub: ❌ Error: ${e.message}"
    }
}

//////// https://community.hubitat.com/t/release-file-manager-device/91092 ///////
// credits @thebearmay
/////// https://github.com/thebearmay/hubitat/blob/main/fileMgr.groovy 

/*
def readFile(fName, Closure closure) {
    closure(readFile(fName))
}
*/

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
                logDebug "readFile: read ${data.length} chars from ${uri}"
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
