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
 * ver. 4.0.1  2025-09-14 kkossev  - added new debug commands; added debug info in states gitHubV4 and profilesV4; added g_loadProfilesCooldown logic - prevent multiple profile loading attempts after JSON parsing errors within short time
 * ver. 4.0.2  2025-09-17 kkossev  - added HOBEIAN ZG-204ZV and TS0601 _TZE200_uli8wasj _TZE200_grgol3xp _TZE200_rhgsbacq _TZE200_y8jijhba into TS0601_HOBEIAN_RADAR profile; profilesV4 code moved to the library; temperature and humidity as custom attributes; 
 *                                   changed the default offlineCheck for mmWave sensors to 60 minutes; LoadAllDefaults reloades the profilesV4 cache from Hubitat storage;
 *                                   moved TS0601 _TZE284_iadro9bf _TZE204_iadro9bf _TZE204_qasjif9e _TZE204_ztqnh5cg into a new TS0601_TUYA_RADAR_2 profile
 * ver. 4.0.3  2025-09-18 kkossev  - (dev. branch) cooldwown timer is started on JSON local storage read or parsing error; importUrl updated; added _TZE204_muvkrjr5 into TS0601_TUYA_RADAR_2 profile; 
 *                                   automatically load the standard JSON file from GitHub on driver installation if not present locally (one time action after installation or hub reboot)
 *                                   
 *                                   TODO: 
 *                                   TODO: On 'Update from GitHub' - show the JSON version, timestamp in the sendInfoEvent (WIP)
 *                                   TODO: load custom JSON file (WIP)
 *                                   TODO: Force device profile is not reflected in the Preferences page!
 *                                   TODO: Show both the profile key and the profile name in the Preferences page!
 *                                   TODO: handle the Unprocessed ZDO command: cluster=8032 after hub reboot
 *                                   TODO: go to the bottom of the reason for : loadProfilesFromJSON exception: error converting JSON: Unable to determine the current character, it is not a string, number, array, or object
 *                                   TODO: do not load profiles when metadata is not available (device just paired)
 *                                   TODO: test the state. after reboot 
 *                                   TODO: 
*/

static String version() { "4.0.3" }
static String timeStamp() {"2025/09/18 10:22 PM"}

@Field static final Boolean _DEBUG = false           // debug commands
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







deviceType = "mmWaveSensor"
@Field static final String DEVICE_TYPE = "mmWaveSensor"

metadata {
    definition (
        name: 'Tuya Zigbee mmWave Sensor',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/deviceProfileV4/Drivers/Tuya%20Zigbee%20mmWave%20Sensor/Tuya_Zigbee_mmWave_Sensor_lib_included.groovy',
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
        attribute 'temperature', 'number'                       // TS0601_HOBEIAN_RADAR
        attribute 'humidity', 'number'                          // TS0601_HOBEIAN_RADAR

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
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: '60', type: 'enum']) }
    if (fullInit || settings?.customJSON == null) { device.updateSetting('customJSON', [value: '', type: 'text']) }
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
            logInfo "cacheTest Initialize: ensureProfilesLoaded(${getProfilesFilename()}) -> ${ok}; size now ${g_deviceProfilesV4.size()}"
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
  * ver. 4.0.0  2025-09-17 kkossev  - deviceProfileV4; HOBEIAN as Tuya device; customInitialize() hook; // library marker kkossev.commonLib, line 26
  * // library marker kkossev.commonLib, line 27
  *                                   TODO: change the offline threshold to 2  // library marker kkossev.commonLib, line 28
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
String commonLibStamp() { '2025/09/17 10:42 PM' } // library marker kkossev.commonLib, line 45

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
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 285
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 286
            break // library marker kkossev.commonLib, line 287
        case 0x0006 : // library marker kkossev.commonLib, line 288
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 289
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 290
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 291
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
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 813
        //logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 814
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 815
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 816
        } // library marker kkossev.commonLib, line 817
    } // library marker kkossev.commonLib, line 818
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 819
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 820
        //logTrace 'standardProcessTuyaDP: processTuyaDPfromDeviceProfile exists, calling it...' // library marker kkossev.commonLib, line 821
        if (this.respondsTo('isInCooldown') && isInCooldown()) { // library marker kkossev.commonLib, line 822
            logDebug "standardProcessTuyaDP: device is in cooldown, skipping processing of dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 823
            return // library marker kkossev.commonLib, line 824
        } // library marker kkossev.commonLib, line 825
        if (this.respondsTo('ensureCurrentProfileLoaded')) { // library marker kkossev.commonLib, line 826
            ensureCurrentProfileLoaded() // library marker kkossev.commonLib, line 827
        } // library marker kkossev.commonLib, line 828
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 829
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 830
        } // library marker kkossev.commonLib, line 831
    } // library marker kkossev.commonLib, line 832
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 833
} // library marker kkossev.commonLib, line 834

public int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 836
    int retValue = 0 // library marker kkossev.commonLib, line 837
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 838
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 839
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 840
        int power = 1 // library marker kkossev.commonLib, line 841
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 842
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 843
            power = power * 256 // library marker kkossev.commonLib, line 844
        } // library marker kkossev.commonLib, line 845
    } // library marker kkossev.commonLib, line 846
    return retValue // library marker kkossev.commonLib, line 847
} // library marker kkossev.commonLib, line 848

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 850

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 852
    List<String> cmds = [] // library marker kkossev.commonLib, line 853
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 854
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 855
    int tuyaCmd // library marker kkossev.commonLib, line 856
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified! // library marker kkossev.commonLib, line 857
    if (this.respondsTo('getDEVICE') && DEVICE?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 858
        tuyaCmd = DEVICE?.device?.tuyaCmd // library marker kkossev.commonLib, line 859
    } // library marker kkossev.commonLib, line 860
    else { // library marker kkossev.commonLib, line 861
        tuyaCmd = tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 862
    } // library marker kkossev.commonLib, line 863
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 864
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 865
    return cmds // library marker kkossev.commonLib, line 866
} // library marker kkossev.commonLib, line 867

private String getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 869

public void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 871
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 872
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 873
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 874
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 875
} // library marker kkossev.commonLib, line 876


public List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 879
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 880
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 881
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 882
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 883
} // library marker kkossev.commonLib, line 884

public List<String> queryAllTuyaDP() { // library marker kkossev.commonLib, line 886
    logTrace 'queryAllTuyaDP()' // library marker kkossev.commonLib, line 887
    List<String> cmds = zigbee.command(0xEF00, 0x03) // library marker kkossev.commonLib, line 888
    return cmds // library marker kkossev.commonLib, line 889
} // library marker kkossev.commonLib, line 890

public void aqaraBlackMagic() { // library marker kkossev.commonLib, line 892
    List<String> cmds = [] // library marker kkossev.commonLib, line 893
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 894
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 895
    } // library marker kkossev.commonLib, line 896
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 897
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 898
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 899
        return // library marker kkossev.commonLib, line 900
    } // library marker kkossev.commonLib, line 901
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 902
} // library marker kkossev.commonLib, line 903

// Invoked from configure() // library marker kkossev.commonLib, line 905
public List<String> initializeDevice() { // library marker kkossev.commonLib, line 906
    List<String> cmds = [] // library marker kkossev.commonLib, line 907
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 908
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 909
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 910
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 911
    } // library marker kkossev.commonLib, line 912
    else { logDebug 'no customInitializeDevice method defined' } // library marker kkossev.commonLib, line 913
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 914
    return cmds // library marker kkossev.commonLib, line 915
} // library marker kkossev.commonLib, line 916

// Invoked from configure() // library marker kkossev.commonLib, line 918
public List<String> configureDevice() { // library marker kkossev.commonLib, line 919
    List<String> cmds = [] // library marker kkossev.commonLib, line 920
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 921
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 922
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 923
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 924
    } // library marker kkossev.commonLib, line 925
    else { logDebug 'no customConfigureDevice method defined' } // library marker kkossev.commonLib, line 926
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 927
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 928
    return cmds // library marker kkossev.commonLib, line 929
} // library marker kkossev.commonLib, line 930

/* // library marker kkossev.commonLib, line 932
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 933
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 934
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 935
*/ // library marker kkossev.commonLib, line 936

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 938
    List<String> cmds = [] // library marker kkossev.commonLib, line 939
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 940
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 941
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 942
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 943
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 944
            } // library marker kkossev.commonLib, line 945
        } // library marker kkossev.commonLib, line 946
    } // library marker kkossev.commonLib, line 947
    return cmds // library marker kkossev.commonLib, line 948
} // library marker kkossev.commonLib, line 949

public void refresh() { // library marker kkossev.commonLib, line 951
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 952
    checkDriverVersion(state) // library marker kkossev.commonLib, line 953
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 954
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 955
        customCmds = customRefresh() // library marker kkossev.commonLib, line 956
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 957
    } // library marker kkossev.commonLib, line 958
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 959
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 960
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 961
    } // library marker kkossev.commonLib, line 962
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 963
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 964
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 965
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 966
    } // library marker kkossev.commonLib, line 967
    else { // library marker kkossev.commonLib, line 968
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 969
    } // library marker kkossev.commonLib, line 970
} // library marker kkossev.commonLib, line 971

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, 'clearRefreshRequest', [overwrite: true]) } // library marker kkossev.commonLib, line 973
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 974
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 975

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 977
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 978
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 979
        sendEvent(name: '_status_', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 980
    } // library marker kkossev.commonLib, line 981
    else { // library marker kkossev.commonLib, line 982
        logInfo "${info}" // library marker kkossev.commonLib, line 983
        sendEvent(name: '_status_', value: info, type: 'digital') // library marker kkossev.commonLib, line 984
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 985
    } // library marker kkossev.commonLib, line 986
} // library marker kkossev.commonLib, line 987

public void ping() { // library marker kkossev.commonLib, line 989
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 990
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true // library marker kkossev.commonLib, line 991
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 992
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID // library marker kkossev.commonLib, line 993
    if (isVirtual()) { runInMillis(10, 'virtualPong') } // library marker kkossev.commonLib, line 994
    else if (device.getDataValue('manufacturer') == 'Aqara') { // library marker kkossev.commonLib, line 995
        logDebug 'Aqara device ping...' // library marker kkossev.commonLib, line 996
        sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [destEndpoint: 0x01], 0) ) // library marker kkossev.commonLib, line 997
    } // library marker kkossev.commonLib, line 998
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) } // library marker kkossev.commonLib, line 999
    logDebug 'ping...' // library marker kkossev.commonLib, line 1000
} // library marker kkossev.commonLib, line 1001

private void virtualPong() { // library marker kkossev.commonLib, line 1003
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1004
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1005
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1006
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1007
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1008
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '9999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1009
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1010
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1011
        sendRttEvent() // library marker kkossev.commonLib, line 1012
    } // library marker kkossev.commonLib, line 1013
    else { // library marker kkossev.commonLib, line 1014
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1015
    } // library marker kkossev.commonLib, line 1016
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1017
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1018
} // library marker kkossev.commonLib, line 1019

public void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1021
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1022
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1023
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1024
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1025
    if (value == null) { // library marker kkossev.commonLib, line 1026
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1027
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1028
    } // library marker kkossev.commonLib, line 1029
    else { // library marker kkossev.commonLib, line 1030
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1031
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1032
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1033
    } // library marker kkossev.commonLib, line 1034
} // library marker kkossev.commonLib, line 1035

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1037
    if (cluster != null) { // library marker kkossev.commonLib, line 1038
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1039
    } // library marker kkossev.commonLib, line 1040
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1041
    return 'NULL' // library marker kkossev.commonLib, line 1042
} // library marker kkossev.commonLib, line 1043

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1045
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1046
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1047
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1048
} // library marker kkossev.commonLib, line 1049

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1051
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1052
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1053
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1054
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1055
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1056
    } // library marker kkossev.commonLib, line 1057
} // library marker kkossev.commonLib, line 1058

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1060
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1061
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1062
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1063
    if (state.health?.isHealthCheck == true) { // library marker kkossev.commonLib, line 1064
        logWarn 'device health check failed!' // library marker kkossev.commonLib, line 1065
        state.health?.checkCtr3 = (state.health?.checkCtr3 ?: 0 ) + 1 // library marker kkossev.commonLib, line 1066
        if (state.health?.checkCtr3 >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1067
            if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1068
                sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1069
            } // library marker kkossev.commonLib, line 1070
        } // library marker kkossev.commonLib, line 1071
        state.health['isHealthCheck'] = false // library marker kkossev.commonLib, line 1072
    } // library marker kkossev.commonLib, line 1073
} // library marker kkossev.commonLib, line 1074

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1076
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1077
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1078
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1079
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1080
    } // library marker kkossev.commonLib, line 1081
    else { // library marker kkossev.commonLib, line 1082
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1083
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1084
    } // library marker kkossev.commonLib, line 1085
} // library marker kkossev.commonLib, line 1086

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1088
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1089
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1090
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1091
} // library marker kkossev.commonLib, line 1092

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1094
private void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1095
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1096
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1097
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1098
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1099
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1100
    } // library marker kkossev.commonLib, line 1101
} // library marker kkossev.commonLib, line 1102

private void deviceHealthCheck() { // library marker kkossev.commonLib, line 1104
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1105
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1106
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1107
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1108
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1109
            logWarn 'not present!' // library marker kkossev.commonLib, line 1110
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1111
        } // library marker kkossev.commonLib, line 1112
    } // library marker kkossev.commonLib, line 1113
    else { // library marker kkossev.commonLib, line 1114
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})" // library marker kkossev.commonLib, line 1115
    } // library marker kkossev.commonLib, line 1116
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1117
    // added 03/06/2025 // library marker kkossev.commonLib, line 1118
    if (settings?.healthCheckMethod as int == 2) { // library marker kkossev.commonLib, line 1119
        state.health['isHealthCheck'] = true // library marker kkossev.commonLib, line 1120
        ping()  // proactively ping the device... // library marker kkossev.commonLib, line 1121
    } // library marker kkossev.commonLib, line 1122
} // library marker kkossev.commonLib, line 1123

private void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1125
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1126
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1127
    if (value == 'online') { // library marker kkossev.commonLib, line 1128
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1129
    } // library marker kkossev.commonLib, line 1130
    else { // library marker kkossev.commonLib, line 1131
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1132
    } // library marker kkossev.commonLib, line 1133
} // library marker kkossev.commonLib, line 1134

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1136
void updated() { // library marker kkossev.commonLib, line 1137
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1138
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1139
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1140
    unschedule() // library marker kkossev.commonLib, line 1141

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1143
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1144
        runIn(86400, 'logsOff') // library marker kkossev.commonLib, line 1145
    } // library marker kkossev.commonLib, line 1146
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1147
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1148
        runIn(1800, 'traceOff') // library marker kkossev.commonLib, line 1149
    } // library marker kkossev.commonLib, line 1150

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1152
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1153
        // schedule the periodic timer // library marker kkossev.commonLib, line 1154
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1155
        if (interval > 0) { // library marker kkossev.commonLib, line 1156
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1157
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1158
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1159
        } // library marker kkossev.commonLib, line 1160
    } // library marker kkossev.commonLib, line 1161
    else { // library marker kkossev.commonLib, line 1162
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1163
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1164
    } // library marker kkossev.commonLib, line 1165
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1166
        customUpdated() // library marker kkossev.commonLib, line 1167
    } // library marker kkossev.commonLib, line 1168

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1170
} // library marker kkossev.commonLib, line 1171

private void logsOff() { // library marker kkossev.commonLib, line 1173
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1174
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1175
} // library marker kkossev.commonLib, line 1176
private void traceOff() { // library marker kkossev.commonLib, line 1177
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1178
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1179
} // library marker kkossev.commonLib, line 1180

public void configure(String command) { // library marker kkossev.commonLib, line 1182
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1183
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1184
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1185
        return // library marker kkossev.commonLib, line 1186
    } // library marker kkossev.commonLib, line 1187
    // // library marker kkossev.commonLib, line 1188
    String func // library marker kkossev.commonLib, line 1189
    try { // library marker kkossev.commonLib, line 1190
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1191
        "$func"() // library marker kkossev.commonLib, line 1192
    } // library marker kkossev.commonLib, line 1193
    catch (e) { // library marker kkossev.commonLib, line 1194
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1195
        return // library marker kkossev.commonLib, line 1196
    } // library marker kkossev.commonLib, line 1197
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1198
} // library marker kkossev.commonLib, line 1199

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1201
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1202
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1203
} // library marker kkossev.commonLib, line 1204

public void loadAllDefaults() { // library marker kkossev.commonLib, line 1206
    logDebug 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1207
    deleteAllSettings() // library marker kkossev.commonLib, line 1208
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1209
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1210
    deleteAllStates() // library marker kkossev.commonLib, line 1211
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1212

    initialize() // library marker kkossev.commonLib, line 1214
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1215
    updated() // library marker kkossev.commonLib, line 1216
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1217
} // library marker kkossev.commonLib, line 1218

private void configureNow() { // library marker kkossev.commonLib, line 1220
    configure() // library marker kkossev.commonLib, line 1221
} // library marker kkossev.commonLib, line 1222

/** // library marker kkossev.commonLib, line 1224
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1225
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1226
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1227
 */ // library marker kkossev.commonLib, line 1228
void configure() { // library marker kkossev.commonLib, line 1229
    List<String> cmds = [] // library marker kkossev.commonLib, line 1230
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1231
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1232
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1233
    if (isTuya()) { // library marker kkossev.commonLib, line 1234
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1235
    } // library marker kkossev.commonLib, line 1236
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1237
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1238
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1239
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1240
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1241
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1242
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1243
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1244
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1245
    } // library marker kkossev.commonLib, line 1246
    else { // library marker kkossev.commonLib, line 1247
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1248
    } // library marker kkossev.commonLib, line 1249
} // library marker kkossev.commonLib, line 1250

 // Invoked when the device is installed with this driver automatically selected. // library marker kkossev.commonLib, line 1252
void installed() { // library marker kkossev.commonLib, line 1253
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1254
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1255
    // populate some default values for attributes // library marker kkossev.commonLib, line 1256
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1257
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1258
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1259
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1260
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1261
} // library marker kkossev.commonLib, line 1262

private void queryPowerSource() { // library marker kkossev.commonLib, line 1264
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1265
} // library marker kkossev.commonLib, line 1266

 // Invoked from 'LoadAllDefaults' // library marker kkossev.commonLib, line 1268
private void initialize() { // library marker kkossev.commonLib, line 1269
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1270
    logDebug "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1271
    if (device.getDataValue('powerSource') == null) { // library marker kkossev.commonLib, line 1272
        logDebug "initializing device powerSource 'unknown'" // library marker kkossev.commonLib, line 1273
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1274
    } // library marker kkossev.commonLib, line 1275
    if (this.respondsTo('customInitialize')) { customInitialize() }  // library marker kkossev.commonLib, line 1276
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1277
    updateTuyaVersion() // library marker kkossev.commonLib, line 1278
    updateAqaraVersion() // library marker kkossev.commonLib, line 1279
} // library marker kkossev.commonLib, line 1280

/* // library marker kkossev.commonLib, line 1282
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1283
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1284
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1285
*/ // library marker kkossev.commonLib, line 1286

static Integer safeToInt(Object val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1288
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1289
} // library marker kkossev.commonLib, line 1290

static Double safeToDouble(Object val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1292
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1293
} // library marker kkossev.commonLib, line 1294

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1296
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1297
} // library marker kkossev.commonLib, line 1298

public void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1300
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1301
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1302
        return // library marker kkossev.commonLib, line 1303
    } // library marker kkossev.commonLib, line 1304
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1305
    cmd.each { // library marker kkossev.commonLib, line 1306
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1307
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1308
            return // library marker kkossev.commonLib, line 1309
        } // library marker kkossev.commonLib, line 1310
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1311
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1312
    } // library marker kkossev.commonLib, line 1313
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1314
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1315
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1316
} // library marker kkossev.commonLib, line 1317

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1319

private String getDeviceInfo() { // library marker kkossev.commonLib, line 1321
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1322
} // library marker kkossev.commonLib, line 1323

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1325
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1326
} // library marker kkossev.commonLib, line 1327

//@CompileStatic // library marker kkossev.commonLib, line 1329
public void checkDriverVersion(final Map stateCopy) { // library marker kkossev.commonLib, line 1330
    if (stateCopy.driverVersion == null || driverVersionAndTimeStamp() != stateCopy.driverVersion) { // library marker kkossev.commonLib, line 1331
        logDebug "checkDriverVersion: updating the settings from the current driver version ${stateCopy.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1332
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()} from version ${stateCopy.driverVersion ?: 'unknown'}") // library marker kkossev.commonLib, line 1333
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1334
        initializeVars(false) // library marker kkossev.commonLib, line 1335
        updateTuyaVersion() // library marker kkossev.commonLib, line 1336
        updateAqaraVersion() // library marker kkossev.commonLib, line 1337
        if (this.respondsTo('customcheckDriverVersion')) { customcheckDriverVersion(stateCopy) } // library marker kkossev.commonLib, line 1338
    } // library marker kkossev.commonLib, line 1339
    if (state.states == null) { state.states = [:] } ; if (state.lastRx == null) { state.lastRx = [:] } ; if (state.lastTx == null) { state.lastTx = [:] } ; if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1340
} // library marker kkossev.commonLib, line 1341

// credits @thebearmay // library marker kkossev.commonLib, line 1343
String getModel() { // library marker kkossev.commonLib, line 1344
    try { // library marker kkossev.commonLib, line 1345
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1346
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1347
    } catch (ignore) { // library marker kkossev.commonLib, line 1348
        try { // library marker kkossev.commonLib, line 1349
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1350
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1351
                return model // library marker kkossev.commonLib, line 1352
            } // library marker kkossev.commonLib, line 1353
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1354
            return '' // library marker kkossev.commonLib, line 1355
        } // library marker kkossev.commonLib, line 1356
    } // library marker kkossev.commonLib, line 1357
} // library marker kkossev.commonLib, line 1358

// credits @thebearmay // library marker kkossev.commonLib, line 1360
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1361
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1362
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1363
    String revision = tokens.last() // library marker kkossev.commonLib, line 1364
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1365
} // library marker kkossev.commonLib, line 1366

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1368
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1369
    unschedule() // library marker kkossev.commonLib, line 1370
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1371
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1372

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1374
} // library marker kkossev.commonLib, line 1375

void resetStatistics() { // library marker kkossev.commonLib, line 1377
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1378
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1379
} // library marker kkossev.commonLib, line 1380

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1382
void resetStats() { // library marker kkossev.commonLib, line 1383
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1384
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1385
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1386
    state.stats.rxCtr = 0 ; state.stats.txCtr = 0 // library marker kkossev.commonLib, line 1387
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1388
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1389
    if (this.respondsTo('customResetStats')) { customResetStats() } // library marker kkossev.commonLib, line 1390
    logInfo 'statistics reset!' // library marker kkossev.commonLib, line 1391
} // library marker kkossev.commonLib, line 1392

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1394
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1395
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1396
        state.clear() // library marker kkossev.commonLib, line 1397
        unschedule() // library marker kkossev.commonLib, line 1398
        resetStats() // library marker kkossev.commonLib, line 1399
        if (this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1400
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1401
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1402
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1403
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1404
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1405
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1406
    } // library marker kkossev.commonLib, line 1407

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1409
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1410
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1411
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1412
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1413

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1415
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1416
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1417
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1418
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1419
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1420
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1421

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1423

    // common libraries initialization // library marker kkossev.commonLib, line 1425
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024 // library marker kkossev.commonLib, line 1426
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024 // library marker kkossev.commonLib, line 1427
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1428
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1429
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1430
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1431
    // // library marker kkossev.commonLib, line 1432
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers! // library marker kkossev.commonLib, line 1433
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024 // library marker kkossev.commonLib, line 1434
    // // library marker kkossev.commonLib, line 1435
    // custom device driver specific initialization should be at the end // library marker kkossev.commonLib, line 1436
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1437
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1438
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1439

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1441
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1442
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1443
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1444
    if ( ep  != null) { // library marker kkossev.commonLib, line 1445
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1446
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1447
    } // library marker kkossev.commonLib, line 1448
    else { // library marker kkossev.commonLib, line 1449
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1450
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1451
    } // library marker kkossev.commonLib, line 1452
} // library marker kkossev.commonLib, line 1453

// not used!? // library marker kkossev.commonLib, line 1455
void setDestinationEP() { // library marker kkossev.commonLib, line 1456
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1457
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1458
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1459
} // library marker kkossev.commonLib, line 1460

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1462
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1463
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1464
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1465
void logError(final String msg) { if (settings?.txtEnable)   { log.error "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1466

// _DEBUG mode only // library marker kkossev.commonLib, line 1468
void getAllProperties() { // library marker kkossev.commonLib, line 1469
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1470
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1471
} // library marker kkossev.commonLib, line 1472

// delete all Preferences // library marker kkossev.commonLib, line 1474
void deleteAllSettings() { // library marker kkossev.commonLib, line 1475
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1476
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1477
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1478
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1479
} // library marker kkossev.commonLib, line 1480

// delete all attributes // library marker kkossev.commonLib, line 1482
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1483
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1484
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1485
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1486
} // library marker kkossev.commonLib, line 1487

// delete all State Variables // library marker kkossev.commonLib, line 1489
void deleteAllStates() { // library marker kkossev.commonLib, line 1490
    String stateDeleted = '' // library marker kkossev.commonLib, line 1491
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1492
    state.clear() // library marker kkossev.commonLib, line 1493
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1494
} // library marker kkossev.commonLib, line 1495

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1497
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1498
} // library marker kkossev.commonLib, line 1499

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1501
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1502
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1503
} // library marker kkossev.commonLib, line 1504

void testParse(String par) { // library marker kkossev.commonLib, line 1506
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1507
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1508
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1509
    parse(par) // library marker kkossev.commonLib, line 1510
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1511
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1512
} // library marker kkossev.commonLib, line 1513

Object testJob() { // library marker kkossev.commonLib, line 1515
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1516
} // library marker kkossev.commonLib, line 1517

/** // library marker kkossev.commonLib, line 1519
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1520
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1521
 */ // library marker kkossev.commonLib, line 1522
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1523
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1524
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1525
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1526
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1527
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1528
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1529
    String cron // library marker kkossev.commonLib, line 1530
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1531
    else { // library marker kkossev.commonLib, line 1532
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1533
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1534
    } // library marker kkossev.commonLib, line 1535
    return cron // library marker kkossev.commonLib, line 1536
} // library marker kkossev.commonLib, line 1537

// credits @thebearmay // library marker kkossev.commonLib, line 1539
String formatUptime() { // library marker kkossev.commonLib, line 1540
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1541
} // library marker kkossev.commonLib, line 1542

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1544
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1545
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1546
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1547
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1548
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1549
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1550
} // library marker kkossev.commonLib, line 1551

boolean isTuya() { // library marker kkossev.commonLib, line 1553
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1554
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1555
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1556
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1557
    return ((model?.startsWith('TS') && manufacturer?.startsWith('_T')) || model == 'HOBEIAN') ? true : false // library marker kkossev.commonLib, line 1558
} // library marker kkossev.commonLib, line 1559

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1561
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1562
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1563
    if (application != null) { // library marker kkossev.commonLib, line 1564
        Integer ver // library marker kkossev.commonLib, line 1565
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1566
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1567
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1568
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1569
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1570
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1571
        } // library marker kkossev.commonLib, line 1572
    } // library marker kkossev.commonLib, line 1573
} // library marker kkossev.commonLib, line 1574

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1576

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1578
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1579
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1580
    if (application != null) { // library marker kkossev.commonLib, line 1581
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1582
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1583
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1584
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1585
        } // library marker kkossev.commonLib, line 1586
    } // library marker kkossev.commonLib, line 1587
} // library marker kkossev.commonLib, line 1588

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1590
    try { // library marker kkossev.commonLib, line 1591
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1592
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1593
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1594
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1595
    } catch (e) { // library marker kkossev.commonLib, line 1596
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1597
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1598
    } // library marker kkossev.commonLib, line 1599
} // library marker kkossev.commonLib, line 1600

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1602
    try { // library marker kkossev.commonLib, line 1603
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1604
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1605
        return date.getTime() // library marker kkossev.commonLib, line 1606
    } catch (e) { // library marker kkossev.commonLib, line 1607
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1608
        return now() // library marker kkossev.commonLib, line 1609
    } // library marker kkossev.commonLib, line 1610
} // library marker kkossev.commonLib, line 1611

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1613
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1614
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1615
    int seconds = time % 60 // library marker kkossev.commonLib, line 1616
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1617
} // library marker kkossev.commonLib, line 1618

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (197) kkossev.deviceProfileLibV4 ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NestedBlockDepth, NoDouble, NoFloat, NoWildcardImports, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.deviceProfileLibV4, line 1
library( // library marker kkossev.deviceProfileLibV4, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLibV4', namespace: 'kkossev', // library marker kkossev.deviceProfileLibV4, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/deviceProfileLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-deviceProfileLib', // library marker kkossev.deviceProfileLibV4, line 4
    version: '4.0.2' // library marker kkossev.deviceProfileLibV4, line 5
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
 * ver. 4.0.1  2025-09-15 kkossev  - added debug commands to sendCommand();  // library marker kkossev.deviceProfileLibV4, line 23
 * ver. 4.0.2  2025-09-18 kkossev  - (dev. branch) cooldown timer is started on JSON local storage read or parsing error; // library marker kkossev.deviceProfileLibV4, line 24
 * // library marker kkossev.deviceProfileLibV4, line 25
 *                                   TODO - updateStateUnknownDPs() from the earlier versions of 4 in 1 driver // library marker kkossev.deviceProfileLibV4, line 26
 * // library marker kkossev.deviceProfileLibV4, line 27
*/ // library marker kkossev.deviceProfileLibV4, line 28

static String deviceProfileLibVersion()   { '4.0.2' } // library marker kkossev.deviceProfileLibV4, line 30
static String deviceProfileLibStamp() { '2025/09/18 2:31 PM' } // library marker kkossev.deviceProfileLibV4, line 31
import groovy.json.* // library marker kkossev.deviceProfileLibV4, line 32
import groovy.transform.Field // library marker kkossev.deviceProfileLibV4, line 33
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLibV4, line 34
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLibV4, line 35
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLibV4, line 36

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLibV4, line 38

@Field static  Map g_deviceProfilesV4 = [:] // library marker kkossev.deviceProfileLibV4, line 40
@Field static  boolean g_profilesLoading = false // library marker kkossev.deviceProfileLibV4, line 41
@Field static  boolean g_profilesLoaded = false // library marker kkossev.deviceProfileLibV4, line 42
@Field static  Map g_deviceFingerprintsV4 = [:] // library marker kkossev.deviceProfileLibV4, line 43
@Field static  Map g_currentProfilesV4 = [:]            // Key: device?.deviceNetworkId, Value: complete profile data // library marker kkossev.deviceProfileLibV4, line 44
@Field static  boolean g_loadProfilesCooldown = false // library marker kkossev.deviceProfileLibV4, line 45
@Field static  int LOAD_PROFILES_COOLDOWN_MS = 30000  // 30 seconds cooldown to prevent multiple profile loading within short time // library marker kkossev.deviceProfileLibV4, line 46


metadata { // library marker kkossev.deviceProfileLibV4, line 49
    // no capabilities // library marker kkossev.deviceProfileLibV4, line 50
    // no attributes // library marker kkossev.deviceProfileLibV4, line 51
    /* // library marker kkossev.deviceProfileLibV4, line 52
    // copy the following commands to the main driver, if needed // library marker kkossev.deviceProfileLibV4, line 53
    command 'sendCommand', [ // library marker kkossev.deviceProfileLibV4, line 54
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLibV4, line 55
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLibV4, line 56
    ] // library marker kkossev.deviceProfileLibV4, line 57
    command 'setPar', [ // library marker kkossev.deviceProfileLibV4, line 58
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLibV4, line 59
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLibV4, line 60
    ] // library marker kkossev.deviceProfileLibV4, line 61
    */ // library marker kkossev.deviceProfileLibV4, line 62

    preferences { // library marker kkossev.deviceProfileLibV4, line 64
        if (device) { // library marker kkossev.deviceProfileLibV4, line 65
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLibV4, line 66
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLibV4, line 67
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLibV4, line 68
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLibV4, line 69
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLibV4, line 70
                        input inputMap // library marker kkossev.deviceProfileLibV4, line 71
                    } // library marker kkossev.deviceProfileLibV4, line 72
                } // library marker kkossev.deviceProfileLibV4, line 73
            } // library marker kkossev.deviceProfileLibV4, line 74
            input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLibV4, line 75
            if (settings?.advancedOptions == true) { // library marker kkossev.deviceProfileLibV4, line 76
                input name: 'customJSON', type: 'text', title: '<b>Custom Device Profiles JSON</b>', description: 'Paste here a custom JSON file with device profiles. The format must be the same as the standard JSON file.<br>Warning! Incorrectly formatted JSON may cause problems when loading device profiles!', defaultValue: '' // library marker kkossev.deviceProfileLibV4, line 77
            } // library marker kkossev.deviceProfileLibV4, line 78
        } // library marker kkossev.deviceProfileLibV4, line 79
    } // library marker kkossev.deviceProfileLibV4, line 80

} // library marker kkossev.deviceProfileLibV4, line 82

private boolean is2in1() { return getDeviceProfile().startsWith('TS0601_2IN1')  }   // patch! // library marker kkossev.deviceProfileLibV4, line 84

public String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLibV4, line 86
public Map     getDEVICE()              {  // library marker kkossev.deviceProfileLibV4, line 87
    // Use V4 approach only. Backward compatibility with V3 is dropped // library marker kkossev.deviceProfileLibV4, line 88
    if (this.hasProperty('g_currentProfilesV4')) { // library marker kkossev.deviceProfileLibV4, line 89
        ensureCurrentProfileLoaded() // library marker kkossev.deviceProfileLibV4, line 90
        return getCurrentDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 91
    }  // library marker kkossev.deviceProfileLibV4, line 92
    return [:] // library marker kkossev.deviceProfileLibV4, line 93
} // library marker kkossev.deviceProfileLibV4, line 94

// ---- V4 Profile Management Methods ---- // library marker kkossev.deviceProfileLibV4, line 96

/** // library marker kkossev.deviceProfileLibV4, line 98
 * Gets the current device's profile data from g_currentProfilesV4 map // library marker kkossev.deviceProfileLibV4, line 99
 * Falls back to g_deviceProfilesV4 if g_currentProfilesV4 entry doesn't exist // library marker kkossev.deviceProfileLibV4, line 100
 * @return Map containing the device profile data // library marker kkossev.deviceProfileLibV4, line 101
 */ // library marker kkossev.deviceProfileLibV4, line 102
private Map getCurrentDeviceProfile() { // library marker kkossev.deviceProfileLibV4, line 103
    if (!this.hasProperty('g_currentProfilesV4') || g_currentProfilesV4 == null) {  // library marker kkossev.deviceProfileLibV4, line 104
        return [:]  // NO fallback to V3 method // library marker kkossev.deviceProfileLibV4, line 105
    } // library marker kkossev.deviceProfileLibV4, line 106

    String dni = device?.deviceNetworkId // library marker kkossev.deviceProfileLibV4, line 108
    Map currentProfile = g_currentProfilesV4[dni] // library marker kkossev.deviceProfileLibV4, line 109

    if (currentProfile != null && currentProfile != [:]) { // library marker kkossev.deviceProfileLibV4, line 111
        return currentProfile // library marker kkossev.deviceProfileLibV4, line 112
    } else { // library marker kkossev.deviceProfileLibV4, line 113
        // Profile not loaded yet, use V3 fallback // library marker kkossev.deviceProfileLibV4, line 114
        return [:] // library marker kkossev.deviceProfileLibV4, line 115
    } // library marker kkossev.deviceProfileLibV4, line 116
} // library marker kkossev.deviceProfileLibV4, line 117

/** // library marker kkossev.deviceProfileLibV4, line 119
 * Ensures the current device's profile is loaded in g_currentProfilesV4 // library marker kkossev.deviceProfileLibV4, line 120
 * Should be called before accessing device-specific profile data // library marker kkossev.deviceProfileLibV4, line 121
 */ // library marker kkossev.deviceProfileLibV4, line 122
private void ensureCurrentProfileLoaded() { // library marker kkossev.deviceProfileLibV4, line 123
    if (!this.hasProperty('g_currentProfilesV4')) {  // library marker kkossev.deviceProfileLibV4, line 124
        return  // V4 not available, stick with V3 // library marker kkossev.deviceProfileLibV4, line 125
    } // library marker kkossev.deviceProfileLibV4, line 126
    if (isInCooldown()) { // library marker kkossev.deviceProfileLibV4, line 127
        logTrace "ensureCurrentProfileLoaded: in cooldown period, skipping profile load" // library marker kkossev.deviceProfileLibV4, line 128
        return // library marker kkossev.deviceProfileLibV4, line 129
    } // library marker kkossev.deviceProfileLibV4, line 130

    String dni = device?.deviceNetworkId // library marker kkossev.deviceProfileLibV4, line 132
    if (!g_currentProfilesV4 || !g_currentProfilesV4.containsKey(dni)) { // library marker kkossev.deviceProfileLibV4, line 133
        populateCurrentProfile(dni) // library marker kkossev.deviceProfileLibV4, line 134
    } // library marker kkossev.deviceProfileLibV4, line 135
} // library marker kkossev.deviceProfileLibV4, line 136

/** // library marker kkossev.deviceProfileLibV4, line 138
 * Populates g_currentProfilesV4 entry for the specified device // library marker kkossev.deviceProfileLibV4, line 139
 * Extracts complete profile data from g_deviceProfilesV4 (excluding fingerprints) // library marker kkossev.deviceProfileLibV4, line 140
 * @param dni Device Network ID to use as key // library marker kkossev.deviceProfileLibV4, line 141
 */ // library marker kkossev.deviceProfileLibV4, line 142
private void populateCurrentProfile(String dni) { // library marker kkossev.deviceProfileLibV4, line 143
    logDebug "populateCurrentProfile: populating profile for device ${dni}" // library marker kkossev.deviceProfileLibV4, line 144
    if (!this.hasProperty('g_currentProfilesV4') || !this.hasProperty('g_deviceProfilesV4')) {  // library marker kkossev.deviceProfileLibV4, line 145
        return // library marker kkossev.deviceProfileLibV4, line 146
    } // library marker kkossev.deviceProfileLibV4, line 147

    String profileName = getDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 149
    if (!profileName || profileName == 'UNKNOWN') { // library marker kkossev.deviceProfileLibV4, line 150
        logWarn "populateCurrentProfile: cannot populate profile for ${dni} - profile name is ${profileName}" // library marker kkossev.deviceProfileLibV4, line 151
        return // library marker kkossev.deviceProfileLibV4, line 152
    } // library marker kkossev.deviceProfileLibV4, line 153
    logDebug "ensuring profiles loaded for device ${dni}" // library marker kkossev.deviceProfileLibV4, line 154
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 155
    if (g_deviceProfilesV4 == null || g_deviceProfilesV4?.isEmpty()) { // library marker kkossev.deviceProfileLibV4, line 156
        logWarn "populateCurrentProfile: cannot populate profile for ${dni} - g_deviceProfilesV4 is null or empty" // library marker kkossev.deviceProfileLibV4, line 157
        return // library marker kkossev.deviceProfileLibV4, line 158
    } // library marker kkossev.deviceProfileLibV4, line 159
    Map sourceProfile = g_deviceProfilesV4[profileName] // library marker kkossev.deviceProfileLibV4, line 160
    if (sourceProfile) { // library marker kkossev.deviceProfileLibV4, line 161
        // Clone the profile data and remove fingerprints (already in g_deviceFingerprintsV4) // library marker kkossev.deviceProfileLibV4, line 162
        Map profileData = sourceProfile.clone() // library marker kkossev.deviceProfileLibV4, line 163
        profileData.remove('fingerprints') // library marker kkossev.deviceProfileLibV4, line 164
        if (g_currentProfilesV4 == null) { g_currentProfilesV4 = [:] }   // initialize if null // library marker kkossev.deviceProfileLibV4, line 165
        g_currentProfilesV4[dni] = profileData // library marker kkossev.deviceProfileLibV4, line 166
        sendInfoEvent "Successfully loaded profile '${profileName}' for device ${dni}" // library marker kkossev.deviceProfileLibV4, line 167
    } else { // library marker kkossev.deviceProfileLibV4, line 168
        logWarn "populateCurrentProfile: profile '${profileName}' not found in g_deviceProfilesV4 for device ${dni}" // library marker kkossev.deviceProfileLibV4, line 169
    } // library marker kkossev.deviceProfileLibV4, line 170
} // library marker kkossev.deviceProfileLibV4, line 171

/** // library marker kkossev.deviceProfileLibV4, line 173
 * Disposes of V3 profile data to free memory once all devices have their profiles loaded // library marker kkossev.deviceProfileLibV4, line 174
 * Should only be called when it's safe to remove V3 data // library marker kkossev.deviceProfileLibV4, line 175
 */ // library marker kkossev.deviceProfileLibV4, line 176
void disposeV3ProfilesIfReady() { // library marker kkossev.deviceProfileLibV4, line 177
    if (!this.hasProperty('g_currentProfilesV4') || !this.hasProperty('g_deviceProfilesV4')) {  // library marker kkossev.deviceProfileLibV4, line 178
        return // library marker kkossev.deviceProfileLibV4, line 179
    } // library marker kkossev.deviceProfileLibV4, line 180

    String dni = device?.deviceNetworkId // library marker kkossev.deviceProfileLibV4, line 182
    if (g_currentProfilesV4?.containsKey(dni)) { // library marker kkossev.deviceProfileLibV4, line 183
        // This device has its profile loaded, it's safe to dispose V3 for this device // library marker kkossev.deviceProfileLibV4, line 184
        logDebug "disposeV3ProfilesIfReady: device ${dni} has current profile loaded - V3 can be disposed" // library marker kkossev.deviceProfileLibV4, line 185
        // Note: In a production environment, you might want more sophisticated logic // library marker kkossev.deviceProfileLibV4, line 186
        // to check if ALL active devices have their profiles loaded before disposing V3 // library marker kkossev.deviceProfileLibV4, line 187
    } else { // library marker kkossev.deviceProfileLibV4, line 188
        logDebug "disposeV3ProfilesIfReady: device ${dni} does not have current profile loaded - keeping V3" // library marker kkossev.deviceProfileLibV4, line 189
    } // library marker kkossev.deviceProfileLibV4, line 190
} // library marker kkossev.deviceProfileLibV4, line 191

/** // library marker kkossev.deviceProfileLibV4, line 193
 * Forces disposal of V4 profiles to free memory // library marker kkossev.deviceProfileLibV4, line 194
 * Use with caution - only when you're sure all needed profiles are in g_currentProfilesV4 // library marker kkossev.deviceProfileLibV4, line 195
 */ // library marker kkossev.deviceProfileLibV4, line 196
void forceDisposeV4Profiles() { // library marker kkossev.deviceProfileLibV4, line 197
    if (!this.hasProperty('g_deviceProfilesV4')) {  // library marker kkossev.deviceProfileLibV4, line 198
        return // library marker kkossev.deviceProfileLibV4, line 199
    } // library marker kkossev.deviceProfileLibV4, line 200

    int sizeBefore = g_deviceProfilesV4?.size() ?: 0 // library marker kkossev.deviceProfileLibV4, line 202
    g_deviceProfilesV4 = null // library marker kkossev.deviceProfileLibV4, line 203
    if (this.hasProperty('g_profilesLoaded')) { g_profilesLoaded = false } // library marker kkossev.deviceProfileLibV4, line 204
    logInfo "forceDisposeV3Profiles: disposed ${sizeBefore} V3 profiles to free memory" // library marker kkossev.deviceProfileLibV4, line 205
} // library marker kkossev.deviceProfileLibV4, line 206

public Set     getDeviceProfiles()      { g_deviceProfilesV4 != null ? g_deviceProfilesV4?.keySet() : [] } // library marker kkossev.deviceProfileLibV4, line 208

// TODO - check why it returns list instead of set or map ??? TODO // library marker kkossev.deviceProfileLibV4, line 210
public List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLibV4, line 211
    // if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 212
    // better don't ... // library marker kkossev.deviceProfileLibV4, line 213
    if (g_deviceProfilesV4 == null || g_deviceProfilesV4?.isEmpty()) { return [] } // library marker kkossev.deviceProfileLibV4, line 214
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLibV4, line 215
    g_deviceProfilesV4.each { profileName, profileMap -> // library marker kkossev.deviceProfileLibV4, line 216
        if ((profileMap.device?.isDepricated ?: false) != true) { // library marker kkossev.deviceProfileLibV4, line 217
            activeProfiles.add(profileMap.description ?: '---') // library marker kkossev.deviceProfileLibV4, line 218
        } // library marker kkossev.deviceProfileLibV4, line 219
    } // library marker kkossev.deviceProfileLibV4, line 220
    return activeProfiles // library marker kkossev.deviceProfileLibV4, line 221
} // library marker kkossev.deviceProfileLibV4, line 222

// ---------------------------------- g_deviceProfilesV4 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLibV4, line 224

/** // library marker kkossev.deviceProfileLibV4, line 226
 * Returns the device fingerprints map // library marker kkossev.deviceProfileLibV4, line 227
 * @return The g_deviceFingerprintsV4 map containing description and fingerprints for each profile // library marker kkossev.deviceProfileLibV4, line 228
 */ // library marker kkossev.deviceProfileLibV4, line 229
public Map getDeviceFingerprintsV4() { // library marker kkossev.deviceProfileLibV4, line 230
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 231
    return this.hasProperty('g_deviceFingerprintsV4') ? g_deviceFingerprintsV4 : [:] // library marker kkossev.deviceProfileLibV4, line 232
} // library marker kkossev.deviceProfileLibV4, line 233

/** // library marker kkossev.deviceProfileLibV4, line 235
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLibV4, line 236
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLibV4, line 237
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLibV4, line 238
 */ // library marker kkossev.deviceProfileLibV4, line 239
public String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLibV4, line 240
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 241
    if (g_deviceProfilesV4 != null) { return g_deviceProfilesV4.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLibV4, line 242
    else { return null } // library marker kkossev.deviceProfileLibV4, line 243
} // library marker kkossev.deviceProfileLibV4, line 244

/** // library marker kkossev.deviceProfileLibV4, line 246
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLibV4, line 247
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLibV4, line 248
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLibV4, line 249
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLibV4, line 250
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLibV4, line 251
 */ // library marker kkossev.deviceProfileLibV4, line 252
private Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLibV4, line 253
    Map foundMap = [:] // library marker kkossev.deviceProfileLibV4, line 254
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLibV4, line 255
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 256
    def preference // library marker kkossev.deviceProfileLibV4, line 257
    try { // library marker kkossev.deviceProfileLibV4, line 258
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLibV4, line 259
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLibV4, line 260
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLibV4, line 261
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLibV4, line 262
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLibV4, line 263
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLibV4, line 264
        } // library marker kkossev.deviceProfileLibV4, line 265
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLibV4, line 266
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLibV4, line 267
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLibV4, line 268
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLibV4, line 269
        } // library marker kkossev.deviceProfileLibV4, line 270
        else { // cluster:attribute // library marker kkossev.deviceProfileLibV4, line 271
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLibV4, line 272
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLibV4, line 273
        } // library marker kkossev.deviceProfileLibV4, line 274
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLibV4, line 275
    } catch (e) { // library marker kkossev.deviceProfileLibV4, line 276
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLibV4, line 277
        return [:] // library marker kkossev.deviceProfileLibV4, line 278
    } // library marker kkossev.deviceProfileLibV4, line 279
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLibV4, line 280
    return foundMap // library marker kkossev.deviceProfileLibV4, line 281
} // library marker kkossev.deviceProfileLibV4, line 282

public Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLibV4, line 284
    Map foundMap = [:] // library marker kkossev.deviceProfileLibV4, line 285
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLibV4, line 286
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLibV4, line 287
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLibV4, line 288
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLibV4, line 289
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLibV4, line 290
        if (foundMap != null) { // library marker kkossev.deviceProfileLibV4, line 291
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLibV4, line 292
            return foundMap // library marker kkossev.deviceProfileLibV4, line 293
        } // library marker kkossev.deviceProfileLibV4, line 294
    } // library marker kkossev.deviceProfileLibV4, line 295
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLibV4, line 296
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLibV4, line 297
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLibV4, line 298
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLibV4, line 299
        if (foundMap != null) { // library marker kkossev.deviceProfileLibV4, line 300
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLibV4, line 301
            return foundMap // library marker kkossev.deviceProfileLibV4, line 302
        } // library marker kkossev.deviceProfileLibV4, line 303
    } // library marker kkossev.deviceProfileLibV4, line 304
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLibV4, line 305
    return [:] // library marker kkossev.deviceProfileLibV4, line 306
} // library marker kkossev.deviceProfileLibV4, line 307

/** // library marker kkossev.deviceProfileLibV4, line 309
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLibV4, line 310
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLibV4, line 311
 */ // library marker kkossev.deviceProfileLibV4, line 312
public void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLibV4, line 313
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLibV4, line 314
    if (DEVICE == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLibV4, line 315
    Map preferences = DEVICE?.preferences ?: [:] // library marker kkossev.deviceProfileLibV4, line 316
    if (preferences == null || preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLibV4, line 317
    Map parMap = [:] // library marker kkossev.deviceProfileLibV4, line 318
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLibV4, line 319
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLibV4, line 320
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLibV4, line 321
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLibV4, line 322
            return // continue // library marker kkossev.deviceProfileLibV4, line 323
        } // library marker kkossev.deviceProfileLibV4, line 324
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLibV4, line 325
        if (parMap == null || parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLibV4, line 326
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLibV4, line 327
        if (parMap?.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLibV4, line 328
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLibV4, line 329
        String str = parMap.name // library marker kkossev.deviceProfileLibV4, line 330
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLibV4, line 331
    } // library marker kkossev.deviceProfileLibV4, line 332
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLibV4, line 333
} // library marker kkossev.deviceProfileLibV4, line 334

/** // library marker kkossev.deviceProfileLibV4, line 336
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLibV4, line 337
 * // library marker kkossev.deviceProfileLibV4, line 338
 * @return List of valid parameters. // library marker kkossev.deviceProfileLibV4, line 339
 */ // library marker kkossev.deviceProfileLibV4, line 340
private List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLibV4, line 341
    List<String> validPars = [] // library marker kkossev.deviceProfileLibV4, line 342
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLibV4, line 343
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLibV4, line 344
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLibV4, line 345
    } // library marker kkossev.deviceProfileLibV4, line 346
    return validPars // library marker kkossev.deviceProfileLibV4, line 347
} // library marker kkossev.deviceProfileLibV4, line 348

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 350
private def getScaledPreferenceValue(String preference, Map dpMap) {        // TODO - not used ??? // library marker kkossev.deviceProfileLibV4, line 351
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 352
    def value = settings."${preference}" // library marker kkossev.deviceProfileLibV4, line 353
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 354
    def scaledValue // library marker kkossev.deviceProfileLibV4, line 355
    if (value == null) { // library marker kkossev.deviceProfileLibV4, line 356
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLibV4, line 357
        return null // library marker kkossev.deviceProfileLibV4, line 358
    } // library marker kkossev.deviceProfileLibV4, line 359
    switch (dpMap.type) { // library marker kkossev.deviceProfileLibV4, line 360
        case 'number' : // library marker kkossev.deviceProfileLibV4, line 361
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLibV4, line 362
            break // library marker kkossev.deviceProfileLibV4, line 363
        case 'decimal' : // library marker kkossev.deviceProfileLibV4, line 364
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLibV4, line 365
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLibV4, line 366
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLibV4, line 367
            } // library marker kkossev.deviceProfileLibV4, line 368
            break // library marker kkossev.deviceProfileLibV4, line 369
        case 'bool' : // library marker kkossev.deviceProfileLibV4, line 370
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLibV4, line 371
            break // library marker kkossev.deviceProfileLibV4, line 372
        case 'enum' : // library marker kkossev.deviceProfileLibV4, line 373
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLibV4, line 374
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLibV4, line 375
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLibV4, line 376
                return null // library marker kkossev.deviceProfileLibV4, line 377
            } // library marker kkossev.deviceProfileLibV4, line 378
            scaledValue = value // library marker kkossev.deviceProfileLibV4, line 379
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLibV4, line 380
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLibV4, line 381
            } // library marker kkossev.deviceProfileLibV4, line 382
            break // library marker kkossev.deviceProfileLibV4, line 383
        default : // library marker kkossev.deviceProfileLibV4, line 384
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLibV4, line 385
            return null // library marker kkossev.deviceProfileLibV4, line 386
    } // library marker kkossev.deviceProfileLibV4, line 387
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLibV4, line 388
    return scaledValue // library marker kkossev.deviceProfileLibV4, line 389
} // library marker kkossev.deviceProfileLibV4, line 390

// called from customUpdated() method in the custom driver // library marker kkossev.deviceProfileLibV4, line 392
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLibV4, line 393
public void updateAllPreferences() { // library marker kkossev.deviceProfileLibV4, line 394
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLibV4, line 395
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLibV4, line 396
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLibV4, line 397
        return // library marker kkossev.deviceProfileLibV4, line 398
    } // library marker kkossev.deviceProfileLibV4, line 399
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 400
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLibV4, line 401
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLibV4, line 402
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLibV4, line 403
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLibV4, line 404
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLibV4, line 405
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLibV4, line 406
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLibV4, line 407
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLibV4, line 408
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLibV4, line 409
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLibV4, line 410
                // scale the value // library marker kkossev.deviceProfileLibV4, line 411
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLibV4, line 412
            } // library marker kkossev.deviceProfileLibV4, line 413
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLibV4, line 414
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLibV4, line 415
            } // library marker kkossev.deviceProfileLibV4, line 416
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLibV4, line 417
        } // library marker kkossev.deviceProfileLibV4, line 418
        else { logDebug "warning: couldn't find map for preference ${name}" ; return }  // TODO - supress the warning if the preference was boolean true/false // library marker kkossev.deviceProfileLibV4, line 419
    } // library marker kkossev.deviceProfileLibV4, line 420
    return // library marker kkossev.deviceProfileLibV4, line 421
} // library marker kkossev.deviceProfileLibV4, line 422

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 424
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLibV4, line 425
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLibV4, line 426
int divideBy10(int val) { // library marker kkossev.deviceProfileLibV4, line 427
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLibV4, line 428
    else { return (val as int) } // library marker kkossev.deviceProfileLibV4, line 429
} // library marker kkossev.deviceProfileLibV4, line 430
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLibV4, line 431
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLibV4, line 432
int signedInt(int val) { // library marker kkossev.deviceProfileLibV4, line 433
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLibV4, line 434
    else { return (val as int) } // library marker kkossev.deviceProfileLibV4, line 435
} // library marker kkossev.deviceProfileLibV4, line 436
int invert(int val) { // library marker kkossev.deviceProfileLibV4, line 437
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 } // library marker kkossev.deviceProfileLibV4, line 438
    else { return val } // library marker kkossev.deviceProfileLibV4, line 439
} // library marker kkossev.deviceProfileLibV4, line 440

// called from setPar and sendAttribite methods for non-Tuya DPs // library marker kkossev.deviceProfileLibV4, line 442
private List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLibV4, line 443
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLibV4, line 444
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 445
    Map map = [:] // library marker kkossev.deviceProfileLibV4, line 446
    // cluster:attribute // library marker kkossev.deviceProfileLibV4, line 447
    try { // library marker kkossev.deviceProfileLibV4, line 448
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLibV4, line 449
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLibV4, line 450
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLibV4, line 451
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLibV4, line 452
        map['ep'] = (attributesMap.ep != null && attributesMap.ep != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.ep) as Integer : null // library marker kkossev.deviceProfileLibV4, line 453
    } // library marker kkossev.deviceProfileLibV4, line 454
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLibV4, line 455
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLibV4, line 456
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLibV4, line 457
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLibV4, line 458
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLibV4, line 459
    } // library marker kkossev.deviceProfileLibV4, line 460
    if ((map.mfgCode != null && map.mfgCode != '') || (map.ep != null && map.ep != '')) { // library marker kkossev.deviceProfileLibV4, line 461
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLibV4, line 462
        Map ep = map.ep != null ? ['destEndpoint':map.ep] : [:] // library marker kkossev.deviceProfileLibV4, line 463
        Map mapOptions = [:] // library marker kkossev.deviceProfileLibV4, line 464
        if (mfgCode) mapOptions.putAll(mfgCode) // library marker kkossev.deviceProfileLibV4, line 465
        if (ep) mapOptions.putAll(ep) // library marker kkossev.deviceProfileLibV4, line 466
        //log.trace "$mapOptions" // library marker kkossev.deviceProfileLibV4, line 467
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mapOptions, delay = 50) // library marker kkossev.deviceProfileLibV4, line 468
    } // library marker kkossev.deviceProfileLibV4, line 469
    else { // library marker kkossev.deviceProfileLibV4, line 470
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 50) // library marker kkossev.deviceProfileLibV4, line 471
    } // library marker kkossev.deviceProfileLibV4, line 472
    return cmds // library marker kkossev.deviceProfileLibV4, line 473
} // library marker kkossev.deviceProfileLibV4, line 474

/** // library marker kkossev.deviceProfileLibV4, line 476
 * Called from setPar() method only! // library marker kkossev.deviceProfileLibV4, line 477
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLibV4, line 478
 * // library marker kkossev.deviceProfileLibV4, line 479
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLibV4, line 480
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLibV4, line 481
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLibV4, line 482
 */ // library marker kkossev.deviceProfileLibV4, line 483
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 484
private def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLibV4, line 485
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 486
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLibV4, line 487
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 488
    def scaledValue        // // library marker kkossev.deviceProfileLibV4, line 489
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLibV4, line 490
    switch (dpMap.type) { // library marker kkossev.deviceProfileLibV4, line 491
        case 'number' : // library marker kkossev.deviceProfileLibV4, line 492
            // TODO - negative values ! // library marker kkossev.deviceProfileLibV4, line 493
            // TODO - better conversion to integer! // library marker kkossev.deviceProfileLibV4, line 494
            value = safeToInt(val, 0) // library marker kkossev.deviceProfileLibV4, line 495
            //scaledValue = value // library marker kkossev.deviceProfileLibV4, line 496
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLibV4, line 497
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLibV4, line 498
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLibV4, line 499
            } // library marker kkossev.deviceProfileLibV4, line 500
            else { // library marker kkossev.deviceProfileLibV4, line 501
                scaledValue = value // library marker kkossev.deviceProfileLibV4, line 502
            } // library marker kkossev.deviceProfileLibV4, line 503
            break // library marker kkossev.deviceProfileLibV4, line 504

        case 'decimal' : // library marker kkossev.deviceProfileLibV4, line 506
            value = safeToDouble(val, 0.0) // library marker kkossev.deviceProfileLibV4, line 507
            // scale the value // library marker kkossev.deviceProfileLibV4, line 508
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLibV4, line 509
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLibV4, line 510
            } // library marker kkossev.deviceProfileLibV4, line 511
            else { // library marker kkossev.deviceProfileLibV4, line 512
                scaledValue = value // library marker kkossev.deviceProfileLibV4, line 513
            } // library marker kkossev.deviceProfileLibV4, line 514
            break // library marker kkossev.deviceProfileLibV4, line 515

        case 'bool' : // library marker kkossev.deviceProfileLibV4, line 517
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLibV4, line 518
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLibV4, line 519
            else { // library marker kkossev.deviceProfileLibV4, line 520
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLibV4, line 521
                return null // library marker kkossev.deviceProfileLibV4, line 522
            } // library marker kkossev.deviceProfileLibV4, line 523
            break // library marker kkossev.deviceProfileLibV4, line 524
        case 'enum' : // library marker kkossev.deviceProfileLibV4, line 525
            // enums are always integer values // library marker kkossev.deviceProfileLibV4, line 526
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLibV4, line 527
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLibV4, line 528
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLibV4, line 529
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLibV4, line 530
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLibV4, line 531
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLibV4, line 532
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLibV4, line 533
            } // library marker kkossev.deviceProfileLibV4, line 534
            else { // library marker kkossev.deviceProfileLibV4, line 535
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLibV4, line 536
            } // library marker kkossev.deviceProfileLibV4, line 537
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLibV4, line 538
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLibV4, line 539
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLibV4, line 540
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLibV4, line 541
                // find the key for the value // library marker kkossev.deviceProfileLibV4, line 542
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLibV4, line 543
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLibV4, line 544
                if (key == null) { // library marker kkossev.deviceProfileLibV4, line 545
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLibV4, line 546
                    return null // library marker kkossev.deviceProfileLibV4, line 547
                } // library marker kkossev.deviceProfileLibV4, line 548
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLibV4, line 549
            //return null // library marker kkossev.deviceProfileLibV4, line 550
            } // library marker kkossev.deviceProfileLibV4, line 551
            break // library marker kkossev.deviceProfileLibV4, line 552
        default : // library marker kkossev.deviceProfileLibV4, line 553
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLibV4, line 554
            return null // library marker kkossev.deviceProfileLibV4, line 555
    } // library marker kkossev.deviceProfileLibV4, line 556
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLibV4, line 557
    // check if the value is within the specified range // library marker kkossev.deviceProfileLibV4, line 558
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLibV4, line 559
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLibV4, line 560
        return null // library marker kkossev.deviceProfileLibV4, line 561
    } // library marker kkossev.deviceProfileLibV4, line 562
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLibV4, line 563
    return scaledValue // library marker kkossev.deviceProfileLibV4, line 564
} // library marker kkossev.deviceProfileLibV4, line 565

/** // library marker kkossev.deviceProfileLibV4, line 567
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLibV4, line 568
 * // library marker kkossev.deviceProfileLibV4, line 569
 * @param par The parameter name. // library marker kkossev.deviceProfileLibV4, line 570
 * @param val The parameter value. // library marker kkossev.deviceProfileLibV4, line 571
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLibV4, line 572
 */ // library marker kkossev.deviceProfileLibV4, line 573
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLibV4, line 574
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 575
    //Boolean validated = false // library marker kkossev.deviceProfileLibV4, line 576
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLibV4, line 577
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLibV4, line 578
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLibV4, line 579
    String par = parPar.trim() // library marker kkossev.deviceProfileLibV4, line 580
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLibV4, line 581
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLibV4, line 582
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLibV4, line 583
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 584
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLibV4, line 585
    if (scaledValue == null) { // library marker kkossev.deviceProfileLibV4, line 586
        log.trace "$dpMap  ${dpMap.map}" // library marker kkossev.deviceProfileLibV4, line 587
        String helpTxt = "setPar: invalid parameter ${par} value <b>${val}</b>." // library marker kkossev.deviceProfileLibV4, line 588
        if (dpMap.min != null && dpMap.max != null) { helpTxt += " Must be in the range ${dpMap.min} to ${dpMap.max}" } // library marker kkossev.deviceProfileLibV4, line 589
        if (dpMap.map != null) { helpTxt += " Must be one of ${dpMap.map}" } // library marker kkossev.deviceProfileLibV4, line 590
        logInfo helpTxt // library marker kkossev.deviceProfileLibV4, line 591
        return false // library marker kkossev.deviceProfileLibV4, line 592
    } // library marker kkossev.deviceProfileLibV4, line 593

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLibV4, line 595
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLibV4, line 596
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLibV4, line 597
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLibV4, line 598
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLibV4, line 599
        // execute the customSetFunction // library marker kkossev.deviceProfileLibV4, line 600
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLibV4, line 601
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLibV4, line 602
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLibV4, line 603
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLibV4, line 604
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLibV4, line 605
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLibV4, line 606
            return true // library marker kkossev.deviceProfileLibV4, line 607
        } // library marker kkossev.deviceProfileLibV4, line 608
        else { // library marker kkossev.deviceProfileLibV4, line 609
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLibV4, line 610
        // continue with the default processing // library marker kkossev.deviceProfileLibV4, line 611
        } // library marker kkossev.deviceProfileLibV4, line 612
    } // library marker kkossev.deviceProfileLibV4, line 613
    if (isVirtual()) { // library marker kkossev.deviceProfileLibV4, line 614
        // set a virtual attribute // library marker kkossev.deviceProfileLibV4, line 615
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 616
        def valMiscType // library marker kkossev.deviceProfileLibV4, line 617
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLibV4, line 618
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLibV4, line 619
            // find the key for the value // library marker kkossev.deviceProfileLibV4, line 620
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLibV4, line 621
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLibV4, line 622
            if (key == null) { // library marker kkossev.deviceProfileLibV4, line 623
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLibV4, line 624
                return false // library marker kkossev.deviceProfileLibV4, line 625
            } // library marker kkossev.deviceProfileLibV4, line 626
            valMiscType = dpMap.map[key as String] // library marker kkossev.deviceProfileLibV4, line 627
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLibV4, line 628
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLibV4, line 629
        } // library marker kkossev.deviceProfileLibV4, line 630
        else { // library marker kkossev.deviceProfileLibV4, line 631
            valMiscType = val // library marker kkossev.deviceProfileLibV4, line 632
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLibV4, line 633
        } // library marker kkossev.deviceProfileLibV4, line 634
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLibV4, line 635
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLibV4, line 636
        logInfo descriptionText // library marker kkossev.deviceProfileLibV4, line 637
        return true // library marker kkossev.deviceProfileLibV4, line 638
    } // library marker kkossev.deviceProfileLibV4, line 639

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLibV4, line 641
    boolean isTuyaDP // library marker kkossev.deviceProfileLibV4, line 642

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLibV4, line 644
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLibV4, line 645
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLibV4, line 646
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLibV4, line 647
        // Tuya DP // library marker kkossev.deviceProfileLibV4, line 648
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLibV4, line 649
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLibV4, line 650
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLibV4, line 651
            return false // library marker kkossev.deviceProfileLibV4, line 652
        } // library marker kkossev.deviceProfileLibV4, line 653
        else { // library marker kkossev.deviceProfileLibV4, line 654
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLibV4, line 655
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLibV4, line 656
            return false // library marker kkossev.deviceProfileLibV4, line 657
        } // library marker kkossev.deviceProfileLibV4, line 658
    } // library marker kkossev.deviceProfileLibV4, line 659
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLibV4, line 660
        // cluster:attribute // library marker kkossev.deviceProfileLibV4, line 661
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLibV4, line 662
        int signedIntScaled = convertSignedInts(scaledValue, dpMap) // library marker kkossev.deviceProfileLibV4, line 663
        cmds = zclWriteAttribute(dpMap, signedIntScaled) // library marker kkossev.deviceProfileLibV4, line 664
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLibV4, line 665
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLibV4, line 666
            return false // library marker kkossev.deviceProfileLibV4, line 667
        } // library marker kkossev.deviceProfileLibV4, line 668
    } // library marker kkossev.deviceProfileLibV4, line 669
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLibV4, line 670
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLibV4, line 671
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLibV4, line 672
    return true // library marker kkossev.deviceProfileLibV4, line 673
} // library marker kkossev.deviceProfileLibV4, line 674

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLibV4, line 676
// TODO - reuse it !!! // library marker kkossev.deviceProfileLibV4, line 677
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 678
public List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLibV4, line 679
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLibV4, line 680
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 681
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLibV4, line 682
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLibV4, line 683
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLibV4, line 684
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLibV4, line 685
        return [] // library marker kkossev.deviceProfileLibV4, line 686
    } // library marker kkossev.deviceProfileLibV4, line 687
    String dpType // library marker kkossev.deviceProfileLibV4, line 688
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLibV4, line 689
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLibV4, line 690
    } // library marker kkossev.deviceProfileLibV4, line 691
    else { // library marker kkossev.deviceProfileLibV4, line 692
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLibV4, line 693
    } // library marker kkossev.deviceProfileLibV4, line 694
    if (dpType == null) { // library marker kkossev.deviceProfileLibV4, line 695
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLibV4, line 696
        return [] // library marker kkossev.deviceProfileLibV4, line 697
    } // library marker kkossev.deviceProfileLibV4, line 698
    // sendTuyaCommand // library marker kkossev.deviceProfileLibV4, line 699
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLibV4, line 700
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLibV4, line 701
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLibV4, line 702
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLibV4, line 703
    } // library marker kkossev.deviceProfileLibV4, line 704
    else { // library marker kkossev.deviceProfileLibV4, line 705
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLibV4, line 706
    } // library marker kkossev.deviceProfileLibV4, line 707
    return cmds // library marker kkossev.deviceProfileLibV4, line 708
} // library marker kkossev.deviceProfileLibV4, line 709

private int convertSignedInts(int val, Map dpMap) { // library marker kkossev.deviceProfileLibV4, line 711
    if (dpMap.dt == '0x28') { // library marker kkossev.deviceProfileLibV4, line 712
        if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLibV4, line 713
        else { return (val as int) } // library marker kkossev.deviceProfileLibV4, line 714
    } // library marker kkossev.deviceProfileLibV4, line 715
    else if (dpMap.dt == '0x29') { // library marker kkossev.deviceProfileLibV4, line 716
        if (val > 32767) { return (val as int) - 65536 } // library marker kkossev.deviceProfileLibV4, line 717
        else { return (val as int) } // library marker kkossev.deviceProfileLibV4, line 718
    } // library marker kkossev.deviceProfileLibV4, line 719
    else { return (val as int) } // library marker kkossev.deviceProfileLibV4, line 720
} // library marker kkossev.deviceProfileLibV4, line 721

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 723
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLibV4, line 724
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 725
    //Boolean validated = false // library marker kkossev.deviceProfileLibV4, line 726
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLibV4, line 727
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'DEVICE.preferences is empty!' ; return false } // library marker kkossev.deviceProfileLibV4, line 728

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLibV4, line 730
    l//log.trace "sendAttribute: dpMap=${dpMap}" // library marker kkossev.deviceProfileLibV4, line 731
    if (dpMap == null || dpMap?.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLibV4, line 732
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLibV4, line 733
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 734
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLibV4, line 735
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLibV4, line 736
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLibV4, line 737
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLibV4, line 738
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLibV4, line 739
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLibV4, line 740
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLibV4, line 741
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLibV4, line 742
        // execute the customSetFunction // library marker kkossev.deviceProfileLibV4, line 743
        try { // library marker kkossev.deviceProfileLibV4, line 744
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLibV4, line 745
        } // library marker kkossev.deviceProfileLibV4, line 746
        catch (e) { // library marker kkossev.deviceProfileLibV4, line 747
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLibV4, line 748
            return false // library marker kkossev.deviceProfileLibV4, line 749
        } // library marker kkossev.deviceProfileLibV4, line 750
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLibV4, line 751
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLibV4, line 752
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLibV4, line 753
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLibV4, line 754
            return true // library marker kkossev.deviceProfileLibV4, line 755
        } // library marker kkossev.deviceProfileLibV4, line 756
        else { // library marker kkossev.deviceProfileLibV4, line 757
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLibV4, line 758
        // continue with the default processing // library marker kkossev.deviceProfileLibV4, line 759
        } // library marker kkossev.deviceProfileLibV4, line 760
    } // library marker kkossev.deviceProfileLibV4, line 761
    else { // library marker kkossev.deviceProfileLibV4, line 762
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLibV4, line 763
    } // library marker kkossev.deviceProfileLibV4, line 764
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLibV4, line 765
    if (isVirtual()) { // library marker kkossev.deviceProfileLibV4, line 766
        // send a virtual attribute // library marker kkossev.deviceProfileLibV4, line 767
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLibV4, line 768
        // patch !! // library marker kkossev.deviceProfileLibV4, line 769
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLibV4, line 770
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLibV4, line 771
        } // library marker kkossev.deviceProfileLibV4, line 772
        else { // library marker kkossev.deviceProfileLibV4, line 773
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLibV4, line 774
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLibV4, line 775
            logInfo descriptionText // library marker kkossev.deviceProfileLibV4, line 776
        } // library marker kkossev.deviceProfileLibV4, line 777
        return true // library marker kkossev.deviceProfileLibV4, line 778
    } // library marker kkossev.deviceProfileLibV4, line 779
    else { // library marker kkossev.deviceProfileLibV4, line 780
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLibV4, line 781
    } // library marker kkossev.deviceProfileLibV4, line 782
    boolean isTuyaDP // library marker kkossev.deviceProfileLibV4, line 783
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 784
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLibV4, line 785
    try { // library marker kkossev.deviceProfileLibV4, line 786
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLibV4, line 787
    } // library marker kkossev.deviceProfileLibV4, line 788
    catch (e) { // library marker kkossev.deviceProfileLibV4, line 789
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLibV4, line 790
        return false // library marker kkossev.deviceProfileLibV4, line 791
    } // library marker kkossev.deviceProfileLibV4, line 792
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLibV4, line 793
        // Tuya DP // library marker kkossev.deviceProfileLibV4, line 794
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLibV4, line 795
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLibV4, line 796
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLibV4, line 797
            return false // library marker kkossev.deviceProfileLibV4, line 798
        } // library marker kkossev.deviceProfileLibV4, line 799
        else { // library marker kkossev.deviceProfileLibV4, line 800
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLibV4, line 801
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLibV4, line 802
            return true // library marker kkossev.deviceProfileLibV4, line 803
        } // library marker kkossev.deviceProfileLibV4, line 804
    } // library marker kkossev.deviceProfileLibV4, line 805
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLibV4, line 806
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLibV4, line 807
    // send a virtual attribute // library marker kkossev.deviceProfileLibV4, line 808
    } // library marker kkossev.deviceProfileLibV4, line 809
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLibV4, line 810
        // cluster:attribute // library marker kkossev.deviceProfileLibV4, line 811
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLibV4, line 812
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLibV4, line 813
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLibV4, line 814
            return false // library marker kkossev.deviceProfileLibV4, line 815
        } // library marker kkossev.deviceProfileLibV4, line 816
    } // library marker kkossev.deviceProfileLibV4, line 817
    else { // library marker kkossev.deviceProfileLibV4, line 818
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLibV4, line 819
        return false // library marker kkossev.deviceProfileLibV4, line 820
    } // library marker kkossev.deviceProfileLibV4, line 821
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLibV4, line 822
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLibV4, line 823
    return true // library marker kkossev.deviceProfileLibV4, line 824
} // library marker kkossev.deviceProfileLibV4, line 825

@Field static Map debugCommandsMap = ['printFingerprintsV4':'', 'printPreferences':'', 'resetStats':'', 'updateAllPreferences': '', 'resetPreferencesToDefaults': '', 'initialize': '', 'validateAndFixPreferences': '', 'profilesV4info': '', 'clearProfilesCache': 'clearProfilesCacheInfo'] // library marker kkossev.deviceProfileLibV4, line 827

/** // library marker kkossev.deviceProfileLibV4, line 829
 * SENDS a list of Zigbee commands to be sent to the device. // library marker kkossev.deviceProfileLibV4, line 830
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLibV4, line 831
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLibV4, line 832
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLibV4, line 833
 */ // library marker kkossev.deviceProfileLibV4, line 834
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLibV4, line 835
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLibV4, line 836
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLibV4, line 837
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLibV4, line 838
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 839
    Map supportedCommandsMap = DEVICE?.commands as Map ?: [:] // library marker kkossev.deviceProfileLibV4, line 840

    // Only add debug commands if debug is enabled // library marker kkossev.deviceProfileLibV4, line 842
    if (_DEBUG || settings.logEnable) { // library marker kkossev.deviceProfileLibV4, line 843
        logDebug "sendCommand: original supportedCommandsMap = ${supportedCommandsMap}" // library marker kkossev.deviceProfileLibV4, line 844
        // add the debug commands to the supported commands map // library marker kkossev.deviceProfileLibV4, line 845
        supportedCommandsMap += debugCommandsMap // library marker kkossev.deviceProfileLibV4, line 846
        logDebug "sendCommand: updated supportedCommandsMap = ${supportedCommandsMap}" // library marker kkossev.deviceProfileLibV4, line 847
    } // library marker kkossev.deviceProfileLibV4, line 848

    if (supportedCommandsMap == null || supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLibV4, line 850
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLibV4, line 851
        return false // library marker kkossev.deviceProfileLibV4, line 852
    } // library marker kkossev.deviceProfileLibV4, line 853

    // Create supportedCommandsList based on the same condition // library marker kkossev.deviceProfileLibV4, line 855
    List supportedCommandsList // library marker kkossev.deviceProfileLibV4, line 856
    if (_DEBUG || settings.logEnable) { // library marker kkossev.deviceProfileLibV4, line 857
        supportedCommandsList = supportedCommandsMap.keySet() as List // library marker kkossev.deviceProfileLibV4, line 858
    } else { // library marker kkossev.deviceProfileLibV4, line 859
        supportedCommandsList = DEVICE?.commands?.keySet() as List ?: [] // library marker kkossev.deviceProfileLibV4, line 860
    } // library marker kkossev.deviceProfileLibV4, line 861

    // check if the command is defined in the supported commands // library marker kkossev.deviceProfileLibV4, line 863
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLibV4, line 864
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLibV4, line 865
        return false // library marker kkossev.deviceProfileLibV4, line 866
    } // library marker kkossev.deviceProfileLibV4, line 867

    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 869
    def func, funcResult // library marker kkossev.deviceProfileLibV4, line 870
    try { // library marker kkossev.deviceProfileLibV4, line 871
        // Search in the merged supportedCommandsMap (includes debug commands when enabled) // library marker kkossev.deviceProfileLibV4, line 872
        func = supportedCommandsMap.find { it.key == command }.value // library marker kkossev.deviceProfileLibV4, line 873
        // added 01/25/2025 : the commands now can be shortened // library marker kkossev.deviceProfileLibV4, line 874
        if (func == null || func == '') { // library marker kkossev.deviceProfileLibV4, line 875
            func = command // library marker kkossev.deviceProfileLibV4, line 876
        } // library marker kkossev.deviceProfileLibV4, line 877
        if (val != null && val != '') { // library marker kkossev.deviceProfileLibV4, line 878
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLibV4, line 879
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLibV4, line 880
        } // library marker kkossev.deviceProfileLibV4, line 881
        else { // library marker kkossev.deviceProfileLibV4, line 882
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLibV4, line 883
            funcResult = "${func}"() // library marker kkossev.deviceProfileLibV4, line 884
        } // library marker kkossev.deviceProfileLibV4, line 885
    } // library marker kkossev.deviceProfileLibV4, line 886
    catch (e) { // library marker kkossev.deviceProfileLibV4, line 887
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLibV4, line 888
        return false // library marker kkossev.deviceProfileLibV4, line 889
    } // library marker kkossev.deviceProfileLibV4, line 890
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLibV4, line 891
    // check if the result is a list of commands // library marker kkossev.deviceProfileLibV4, line 892
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLibV4, line 893
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLibV4, line 894
        cmds = funcResult // library marker kkossev.deviceProfileLibV4, line 895
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLibV4, line 896
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLibV4, line 897
        } // library marker kkossev.deviceProfileLibV4, line 898
    } // library marker kkossev.deviceProfileLibV4, line 899
    else if (funcResult == null) { // library marker kkossev.deviceProfileLibV4, line 900
        return false // library marker kkossev.deviceProfileLibV4, line 901
    } // library marker kkossev.deviceProfileLibV4, line 902
     else { // library marker kkossev.deviceProfileLibV4, line 903
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLibV4, line 904
        return false // library marker kkossev.deviceProfileLibV4, line 905
    } // library marker kkossev.deviceProfileLibV4, line 906
    return true // library marker kkossev.deviceProfileLibV4, line 907
} // library marker kkossev.deviceProfileLibV4, line 908

/** // library marker kkossev.deviceProfileLibV4, line 910
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLibV4, line 911
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLibV4, line 912
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLibV4, line 913
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLibV4, line 914
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLibV4, line 915
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLibV4, line 916
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLibV4, line 917
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLibV4, line 918
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLibV4, line 919
 * @return A map containing the input details. // library marker kkossev.deviceProfileLibV4, line 920
 */ // library marker kkossev.deviceProfileLibV4, line 921
public Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLibV4, line 922
    String param = paramPar.trim() // library marker kkossev.deviceProfileLibV4, line 923
    Map input = [:] // library marker kkossev.deviceProfileLibV4, line 924
    Map foundMap = [:] // library marker kkossev.deviceProfileLibV4, line 925
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLibV4, line 926
    Object preference // library marker kkossev.deviceProfileLibV4, line 927
    try { preference = DEVICE?.preferences["$param"] } // library marker kkossev.deviceProfileLibV4, line 928
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLibV4, line 929
    //  check for boolean values // library marker kkossev.deviceProfileLibV4, line 930
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } } // library marker kkossev.deviceProfileLibV4, line 931
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLibV4, line 932
    /* // library marker kkossev.deviceProfileLibV4, line 933
    // TODO - check if this is neccessary? isTuyaDP is not defined! // library marker kkossev.deviceProfileLibV4, line 934
    try { isTuyaDP = preference.isNumber() } // library marker kkossev.deviceProfileLibV4, line 935
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  } // library marker kkossev.deviceProfileLibV4, line 936
    */ // library marker kkossev.deviceProfileLibV4, line 937
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLibV4, line 938
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLibV4, line 939
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLibV4, line 940
    if (foundMap == null || foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  } // library marker kkossev.deviceProfileLibV4, line 941
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  } // library marker kkossev.deviceProfileLibV4, line 942
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) { // library marker kkossev.deviceProfileLibV4, line 943
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" } // library marker kkossev.deviceProfileLibV4, line 944
        return [:] // library marker kkossev.deviceProfileLibV4, line 945
    } // library marker kkossev.deviceProfileLibV4, line 946
    input.name = foundMap.name // library marker kkossev.deviceProfileLibV4, line 947
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLibV4, line 948
    input.title = foundMap.title // library marker kkossev.deviceProfileLibV4, line 949
    //input.description = (foundMap.description ?: foundMap.title)?.replaceAll(/<\/?b>/, '')  // if description is not defined, use the title // library marker kkossev.deviceProfileLibV4, line 950
    input.description = foundMap.description ?: ''   // if description is not defined, skip it // library marker kkossev.deviceProfileLibV4, line 951
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLibV4, line 952
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLibV4, line 953
            //input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLibV4, line 954
            input.range = "${Math.ceil(foundMap.min) as int}..${Math.ceil(foundMap.max) as int}" // library marker kkossev.deviceProfileLibV4, line 955
        } // library marker kkossev.deviceProfileLibV4, line 956
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLibV4, line 957
            if (input.description != '') { input.description += '<br>' } // library marker kkossev.deviceProfileLibV4, line 958
            input.description += "<i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLibV4, line 959
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLibV4, line 960
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLibV4, line 961
            } // library marker kkossev.deviceProfileLibV4, line 962
        } // library marker kkossev.deviceProfileLibV4, line 963
    } // library marker kkossev.deviceProfileLibV4, line 964
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLibV4, line 965
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLibV4, line 966
        input.options = foundMap.map // library marker kkossev.deviceProfileLibV4, line 967
    }/* // library marker kkossev.deviceProfileLibV4, line 968
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLibV4, line 969
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLibV4, line 970
    }*/ // library marker kkossev.deviceProfileLibV4, line 971
    else { // library marker kkossev.deviceProfileLibV4, line 972
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLibV4, line 973
        return [:] // library marker kkossev.deviceProfileLibV4, line 974
    } // library marker kkossev.deviceProfileLibV4, line 975
    if (input.defVal != null) { // library marker kkossev.deviceProfileLibV4, line 976
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLibV4, line 977
    } // library marker kkossev.deviceProfileLibV4, line 978
    return input // library marker kkossev.deviceProfileLibV4, line 979
} // library marker kkossev.deviceProfileLibV4, line 980

/** // library marker kkossev.deviceProfileLibV4, line 982
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLibV4, line 983
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLibV4, line 984
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLibV4, line 985
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLibV4, line 986
 */ // library marker kkossev.deviceProfileLibV4, line 987
public List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLibV4, line 988
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 989
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLibV4, line 990
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLibV4, line 991
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLibV4, line 992

    // Use g_deviceFingerprintsV4 for more efficient fingerprint matching // library marker kkossev.deviceProfileLibV4, line 994
    if (this.hasProperty('g_deviceFingerprintsV4') && g_deviceFingerprintsV4 != null) { // library marker kkossev.deviceProfileLibV4, line 995
        g_deviceFingerprintsV4.each { profileName, profileData -> // library marker kkossev.deviceProfileLibV4, line 996
            profileData.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLibV4, line 997
                if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLibV4, line 998
                    deviceProfile = profileName // library marker kkossev.deviceProfileLibV4, line 999
                    deviceName = fingerprint.deviceJoinName ?: profileData.description ?: UNKNOWN // library marker kkossev.deviceProfileLibV4, line 1000
                    logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLibV4, line 1001
                    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLibV4, line 1002
                } // library marker kkossev.deviceProfileLibV4, line 1003
            } // library marker kkossev.deviceProfileLibV4, line 1004
        } // library marker kkossev.deviceProfileLibV4, line 1005
    } else if (g_deviceProfilesV4 != null && !g_deviceProfilesV4.isEmpty()) { // library marker kkossev.deviceProfileLibV4, line 1006
        // Fallback to g_deviceProfilesV4 if g_deviceFingerprintsV4 is not available // library marker kkossev.deviceProfileLibV4, line 1007
        g_deviceProfilesV4.each { profileName, profileMap -> // library marker kkossev.deviceProfileLibV4, line 1008
            profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLibV4, line 1009
                if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLibV4, line 1010
                    deviceProfile = profileName // library marker kkossev.deviceProfileLibV4, line 1011
                    deviceName = fingerprint.deviceJoinName ?: g_deviceProfilesV4[deviceProfile].description ?: UNKNOWN // library marker kkossev.deviceProfileLibV4, line 1012
                    logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLibV4, line 1013
                    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLibV4, line 1014
                } // library marker kkossev.deviceProfileLibV4, line 1015
            } // library marker kkossev.deviceProfileLibV4, line 1016
        } // library marker kkossev.deviceProfileLibV4, line 1017
    } // library marker kkossev.deviceProfileLibV4, line 1018
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLibV4, line 1019
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLibV4, line 1020
    } // library marker kkossev.deviceProfileLibV4, line 1021
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLibV4, line 1022
} // library marker kkossev.deviceProfileLibV4, line 1023

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLibV4, line 1025
public void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLibV4, line 1026
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 1027

    // Store previous profile for change detection // library marker kkossev.deviceProfileLibV4, line 1029
    String previousProfile = getDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 1030

    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLibV4, line 1032
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLibV4, line 1033
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLibV4, line 1034
        // don't change the device name when unknown // library marker kkossev.deviceProfileLibV4, line 1035
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLibV4, line 1036
    } // library marker kkossev.deviceProfileLibV4, line 1037
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLibV4, line 1038
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLibV4, line 1039
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLibV4, line 1040
        device.setName(deviceName) // library marker kkossev.deviceProfileLibV4, line 1041
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLibV4, line 1042
        device.updateSetting('forcedProfile', [value:g_deviceProfilesV4[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLibV4, line 1043
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLibV4, line 1044

        // V4 Profile Management: Handle profile loading and changes // library marker kkossev.deviceProfileLibV4, line 1046
        if (this.hasProperty('g_currentProfilesV4')) { // library marker kkossev.deviceProfileLibV4, line 1047
            String dni = device?.deviceNetworkId // library marker kkossev.deviceProfileLibV4, line 1048

            // Detect profile change // library marker kkossev.deviceProfileLibV4, line 1050
            if (previousProfile != deviceProfile && previousProfile != 'UNKNOWN') { // library marker kkossev.deviceProfileLibV4, line 1051
                logInfo "Profile changed from '${previousProfile}' to '${deviceProfile}' - clearing old profile data for device ${dni}" // library marker kkossev.deviceProfileLibV4, line 1052
                g_currentProfilesV4.remove(dni) // library marker kkossev.deviceProfileLibV4, line 1053
            } // library marker kkossev.deviceProfileLibV4, line 1054

            // Ensure current profile is loaded // library marker kkossev.deviceProfileLibV4, line 1056
            ensureCurrentProfileLoaded() // library marker kkossev.deviceProfileLibV4, line 1057
        } // library marker kkossev.deviceProfileLibV4, line 1058
    } else { // library marker kkossev.deviceProfileLibV4, line 1059
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLibV4, line 1060
    } // library marker kkossev.deviceProfileLibV4, line 1061
} // library marker kkossev.deviceProfileLibV4, line 1062

public List<String> refreshFromConfigureReadList(List<String> refreshList) { // library marker kkossev.deviceProfileLibV4, line 1064
    logDebug "refreshFromConfigureReadList(${refreshList})" // library marker kkossev.deviceProfileLibV4, line 1065
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 1066
    if (refreshList != null && !refreshList.isEmpty()) { // library marker kkossev.deviceProfileLibV4, line 1067
        //List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLibV4, line 1068
        for (String k : refreshList) { // library marker kkossev.deviceProfileLibV4, line 1069
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLibV4, line 1070
            if (k != null) { // library marker kkossev.deviceProfileLibV4, line 1071
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLibV4, line 1072
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLibV4, line 1073
                if (map != null) { // library marker kkossev.deviceProfileLibV4, line 1074
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLibV4, line 1075
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLibV4, line 1076
                } // library marker kkossev.deviceProfileLibV4, line 1077
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLibV4, line 1078
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLibV4, line 1079
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLibV4, line 1080
                } // library marker kkossev.deviceProfileLibV4, line 1081
            } // library marker kkossev.deviceProfileLibV4, line 1082
        } // library marker kkossev.deviceProfileLibV4, line 1083
    } // library marker kkossev.deviceProfileLibV4, line 1084
    return cmds // library marker kkossev.deviceProfileLibV4, line 1085
} // library marker kkossev.deviceProfileLibV4, line 1086

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLibV4, line 1088
public List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLibV4, line 1089
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLibV4, line 1090
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 1091
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLibV4, line 1092
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLibV4, line 1093
        for (String k : refreshList) { // library marker kkossev.deviceProfileLibV4, line 1094
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLibV4, line 1095
            if (k != null) { // library marker kkossev.deviceProfileLibV4, line 1096
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLibV4, line 1097
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLibV4, line 1098
                if (map != null) { // library marker kkossev.deviceProfileLibV4, line 1099
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLibV4, line 1100
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLibV4, line 1101
                } // library marker kkossev.deviceProfileLibV4, line 1102
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLibV4, line 1103
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLibV4, line 1104
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLibV4, line 1105
                } // library marker kkossev.deviceProfileLibV4, line 1106
            } // library marker kkossev.deviceProfileLibV4, line 1107
        } // library marker kkossev.deviceProfileLibV4, line 1108
    } // library marker kkossev.deviceProfileLibV4, line 1109
    return cmds // library marker kkossev.deviceProfileLibV4, line 1110
} // library marker kkossev.deviceProfileLibV4, line 1111

// TODO! - remove? // library marker kkossev.deviceProfileLibV4, line 1113
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLibV4, line 1114
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 1115
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLibV4, line 1116
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLibV4, line 1117
    return cmds // library marker kkossev.deviceProfileLibV4, line 1118
} // library marker kkossev.deviceProfileLibV4, line 1119

// TODO ! - remove? // library marker kkossev.deviceProfileLibV4, line 1121
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLibV4, line 1122
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 1123
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLibV4, line 1124
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLibV4, line 1125
    return cmds // library marker kkossev.deviceProfileLibV4, line 1126
} // library marker kkossev.deviceProfileLibV4, line 1127

// TODO! - remove? // library marker kkossev.deviceProfileLibV4, line 1129
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLibV4, line 1130
    List<String> cmds = [] // library marker kkossev.deviceProfileLibV4, line 1131
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLibV4, line 1132
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLibV4, line 1133
    return cmds // library marker kkossev.deviceProfileLibV4, line 1134
} // library marker kkossev.deviceProfileLibV4, line 1135

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLibV4, line 1137
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLibV4, line 1138
    // Eager loading during initialization // library marker kkossev.deviceProfileLibV4, line 1139
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 1140
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLibV4, line 1141
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLibV4, line 1142
    } // library marker kkossev.deviceProfileLibV4, line 1143
} // library marker kkossev.deviceProfileLibV4, line 1144

public void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLibV4, line 1146
    String ps = DEVICE?.device?.powerSource // library marker kkossev.deviceProfileLibV4, line 1147
    logDebug "initEventsDeviceProfile(${fullInit}) for deviceProfile=${state.deviceProfile} DEVICE?.device?.powerSource=${ps} ps.isEmpty()=${ps?.isEmpty()}" // library marker kkossev.deviceProfileLibV4, line 1148
    if (ps != null && !ps.isEmpty()) { // library marker kkossev.deviceProfileLibV4, line 1149
        sendEvent(name: 'powerSource', value: ps, descriptionText: "Power Source set to '${ps}'", type: 'digital') // library marker kkossev.deviceProfileLibV4, line 1150
    } // library marker kkossev.deviceProfileLibV4, line 1151
} // library marker kkossev.deviceProfileLibV4, line 1152

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLibV4, line 1154

// // library marker kkossev.deviceProfileLibV4, line 1156
// called from parse() // library marker kkossev.deviceProfileLibV4, line 1157
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profile // library marker kkossev.deviceProfileLibV4, line 1158
//          false - the processing can continue // library marker kkossev.deviceProfileLibV4, line 1159
// // library marker kkossev.deviceProfileLibV4, line 1160
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLibV4, line 1161
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLibV4, line 1162
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLibV4, line 1163
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLibV4, line 1164
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLibV4, line 1165
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLibV4, line 1166
    Map currentProfile = getCurrentDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 1167
    List spammyList = currentProfile?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLibV4, line 1168
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLibV4, line 1169
} // library marker kkossev.deviceProfileLibV4, line 1170

// // library marker kkossev.deviceProfileLibV4, line 1172
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLibV4, line 1173
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profile // library marker kkossev.deviceProfileLibV4, line 1174
//          false - debug logs can be generated // library marker kkossev.deviceProfileLibV4, line 1175
// // library marker kkossev.deviceProfileLibV4, line 1176
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLibV4, line 1177
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLibV4, line 1178
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLibV4, line 1179
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLibV4, line 1180
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLibV4, line 1181
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLibV4, line 1182
    Map currentProfile = getCurrentDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 1183
    List spammyList = currentProfile?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLibV4, line 1184
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLibV4, line 1185
} // library marker kkossev.deviceProfileLibV4, line 1186

// all DPs are spammy - sent periodically! (this function is not used?) // library marker kkossev.deviceProfileLibV4, line 1188
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLibV4, line 1189
    Map currentProfile = getCurrentDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 1190
    if (!currentProfile) { return false } // library marker kkossev.deviceProfileLibV4, line 1191
    Boolean isSpammy = currentProfile?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLibV4, line 1192
    return isSpammy // library marker kkossev.deviceProfileLibV4, line 1193
} // library marker kkossev.deviceProfileLibV4, line 1194

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLibV4, line 1196
private List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLibV4, line 1197
    //logTrace "compareAndConvertStrings: tuyaValue='${tuyaValue}' hubitatValue='${hubitatValue}'" // library marker kkossev.deviceProfileLibV4, line 1198
    String convertedValue = tuyaValue ?: "" // library marker kkossev.deviceProfileLibV4, line 1199
    boolean isEqual // library marker kkossev.deviceProfileLibV4, line 1200
    if (hubitatValue == null || tuyaValue == null) { // library marker kkossev.deviceProfileLibV4, line 1201
        // Per requirement: any null hubitatValue forces inequality, regardless of tuyaValue // library marker kkossev.deviceProfileLibV4, line 1202
        isEqual = false // library marker kkossev.deviceProfileLibV4, line 1203
    } else { // library marker kkossev.deviceProfileLibV4, line 1204
        // Safe comparison (may yield true only if hubitatValue non-null and matches tuyaValue) // library marker kkossev.deviceProfileLibV4, line 1205
        isEqual = ((tuyaValue as String) == (hubitatValue as String)) // library marker kkossev.deviceProfileLibV4, line 1206
    } // library marker kkossev.deviceProfileLibV4, line 1207
    if (foundItem?.scale != null && !(foundItem.scale in [0, 1])) { // library marker kkossev.deviceProfileLibV4, line 1208
        logTrace "compareAndConvertStrings: scale=${foundItem.scale} tuyaValue=${tuyaValue} convertedValue=${convertedValue} hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLibV4, line 1209
    } // library marker kkossev.deviceProfileLibV4, line 1210
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLibV4, line 1211
} // library marker kkossev.deviceProfileLibV4, line 1212

private List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLibV4, line 1214
    Integer convertedValue // library marker kkossev.deviceProfileLibV4, line 1215
    boolean isEqual // library marker kkossev.deviceProfileLibV4, line 1216
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLibV4, line 1217
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLibV4, line 1218
    } // library marker kkossev.deviceProfileLibV4, line 1219
    else { // library marker kkossev.deviceProfileLibV4, line 1220
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLibV4, line 1221
    } // library marker kkossev.deviceProfileLibV4, line 1222
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLibV4, line 1223
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLibV4, line 1224
} // library marker kkossev.deviceProfileLibV4, line 1225

private List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLibV4, line 1227
    Double convertedValue // library marker kkossev.deviceProfileLibV4, line 1228
    boolean isEqual // library marker kkossev.deviceProfileLibV4, line 1229
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLibV4, line 1230
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLibV4, line 1231
    } // library marker kkossev.deviceProfileLibV4, line 1232
    else { // library marker kkossev.deviceProfileLibV4, line 1233
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLibV4, line 1234
    } // library marker kkossev.deviceProfileLibV4, line 1235
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLibV4, line 1236
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLibV4, line 1237
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLibV4, line 1238
} // library marker kkossev.deviceProfileLibV4, line 1239

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 1241
private List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLibV4, line 1242
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLibV4, line 1243
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1244
    def convertedValue // library marker kkossev.deviceProfileLibV4, line 1245
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLibV4, line 1246
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLibV4, line 1247
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLibV4, line 1248
    } // library marker kkossev.deviceProfileLibV4, line 1249
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLibV4, line 1250
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLibV4, line 1251
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLibV4, line 1252
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLibV4, line 1253
            isEqual = false // library marker kkossev.deviceProfileLibV4, line 1254
        } // library marker kkossev.deviceProfileLibV4, line 1255
        else { // compare as double (float) // library marker kkossev.deviceProfileLibV4, line 1256
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLibV4, line 1257
        } // library marker kkossev.deviceProfileLibV4, line 1258
    } // library marker kkossev.deviceProfileLibV4, line 1259
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLibV4, line 1260
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLibV4, line 1261
} // library marker kkossev.deviceProfileLibV4, line 1262

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLibV4, line 1264
private List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLibV4, line 1265
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLibV4, line 1266
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLibV4, line 1267
    boolean isEqual // library marker kkossev.deviceProfileLibV4, line 1268
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1269
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLibV4, line 1270
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1271
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLibV4, line 1272
    switch (foundItem.type) { // library marker kkossev.deviceProfileLibV4, line 1273
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLibV4, line 1274
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLibV4, line 1275
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLibV4, line 1276
            break // library marker kkossev.deviceProfileLibV4, line 1277
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLibV4, line 1278
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLibV4, line 1279
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLibV4, line 1280
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLibV4, line 1281
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLibV4, line 1282
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLibV4, line 1283
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLibV4, line 1284
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLibV4, line 1285
            } // library marker kkossev.deviceProfileLibV4, line 1286
            else { // library marker kkossev.deviceProfileLibV4, line 1287
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLibV4, line 1288
            } // library marker kkossev.deviceProfileLibV4, line 1289
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLibV4, line 1290
            break // library marker kkossev.deviceProfileLibV4, line 1291
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLibV4, line 1292
        case 'number' : // library marker kkossev.deviceProfileLibV4, line 1293
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLibV4, line 1294
            logTrace "tuyaValue=${fncmd} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLibV4, line 1295
            break // library marker kkossev.deviceProfileLibV4, line 1296
       case 'decimal' : // library marker kkossev.deviceProfileLibV4, line 1297
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLibV4, line 1298
            logTrace "comparing as float tuyaValue=${fncmd} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLibV4, line 1299
            break // library marker kkossev.deviceProfileLibV4, line 1300
        default : // library marker kkossev.deviceProfileLibV4, line 1301
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLibV4, line 1302
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLibV4, line 1303
    } // library marker kkossev.deviceProfileLibV4, line 1304
    if (isEqual == false) { // library marker kkossev.deviceProfileLibV4, line 1305
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLibV4, line 1306
    } // library marker kkossev.deviceProfileLibV4, line 1307
    // // library marker kkossev.deviceProfileLibV4, line 1308
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLibV4, line 1309
} // library marker kkossev.deviceProfileLibV4, line 1310

// // library marker kkossev.deviceProfileLibV4, line 1312
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLibV4, line 1313
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLibV4, line 1314
// returns: (two results!) // library marker kkossev.deviceProfileLibV4, line 1315
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLibV4, line 1316
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLibV4, line 1317
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLibV4, line 1318
// // library marker kkossev.deviceProfileLibV4, line 1319
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLibV4, line 1320
// // library marker kkossev.deviceProfileLibV4, line 1321
//  TODO: refactor! // library marker kkossev.deviceProfileLibV4, line 1322
// // library marker kkossev.deviceProfileLibV4, line 1323
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLibV4, line 1324
private List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd_orig, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLibV4, line 1325
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLibV4, line 1326
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLibV4, line 1327
    int fncmd = fncmd_orig ?: 0 // library marker kkossev.deviceProfileLibV4, line 1328
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1329
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLibV4, line 1330
    boolean isEqual = false // library marker kkossev.deviceProfileLibV4, line 1331
    switch (foundItem.type) { // library marker kkossev.deviceProfileLibV4, line 1332
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLibV4, line 1333
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as String] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLibV4, line 1334
            break // library marker kkossev.deviceProfileLibV4, line 1335
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLibV4, line 1336
            //logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLibV4, line 1337
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLibV4, line 1338
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLibV4, line 1339
            //logTrace "latestEvent is ${latestEvent} dataType is ${dataType}" // library marker kkossev.deviceProfileLibV4, line 1340
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLibV4, line 1341
            if (dataType == null || dataType == 'ENUM') { // library marker kkossev.deviceProfileLibV4, line 1342
                //logTrace "compareAndConvertTuyaToHubitatEventValue: comparing as string fncmd=${fncmd} foundItem.name=${foundItem.name} foundItem.map=${foundItem.map}" // library marker kkossev.deviceProfileLibV4, line 1343
                String foundItemMapValue = foundItem.map[fncmd as String] ?: 'unknown'      // map indexes are of a type String in g_deviceProfilesV4 !!! // library marker kkossev.deviceProfileLibV4, line 1344
                //String foundItemMapValue = 'unknown' // library marker kkossev.deviceProfileLibV4, line 1345
                //logTrace "foundItem.map[fncmd as String] = ${foundItemMapValue}" // library marker kkossev.deviceProfileLibV4, line 1346
                //logTrace "device.currentValue(${foundItem.name}) = ${device.currentValue(foundItem.name) ?: 'unknown'}" // library marker kkossev.deviceProfileLibV4, line 1347
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItemMapValue, device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLibV4, line 1348
                //logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLibV4, line 1349
            } // library marker kkossev.deviceProfileLibV4, line 1350
            else { // library marker kkossev.deviceProfileLibV4, line 1351
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLibV4, line 1352
            } // library marker kkossev.deviceProfileLibV4, line 1353
            //logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLibV4, line 1354
            break // library marker kkossev.deviceProfileLibV4, line 1355
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLibV4, line 1356
        case 'number' : // library marker kkossev.deviceProfileLibV4, line 1357
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLibV4, line 1358
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLibV4, line 1359
            break // library marker kkossev.deviceProfileLibV4, line 1360
        case 'decimal' : // library marker kkossev.deviceProfileLibV4, line 1361
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLibV4, line 1362
            break // library marker kkossev.deviceProfileLibV4, line 1363
        default : // library marker kkossev.deviceProfileLibV4, line 1364
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLibV4, line 1365
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLibV4, line 1366
    } // library marker kkossev.deviceProfileLibV4, line 1367
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLibV4, line 1368
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLibV4, line 1369
} // library marker kkossev.deviceProfileLibV4, line 1370

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLibV4, line 1372
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLibV4, line 1373
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLibV4, line 1374
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLibV4, line 1375
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLibV4, line 1376
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLibV4, line 1377
    // check if preProc method exists // library marker kkossev.deviceProfileLibV4, line 1378
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLibV4, line 1379
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLibV4, line 1380
        return fncmd_orig // library marker kkossev.deviceProfileLibV4, line 1381
    } // library marker kkossev.deviceProfileLibV4, line 1382
    // execute the preProc function // library marker kkossev.deviceProfileLibV4, line 1383
    try { // library marker kkossev.deviceProfileLibV4, line 1384
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLibV4, line 1385
    } // library marker kkossev.deviceProfileLibV4, line 1386
    catch (e) { // library marker kkossev.deviceProfileLibV4, line 1387
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd_orig})" // library marker kkossev.deviceProfileLibV4, line 1388
        return fncmd_orig // library marker kkossev.deviceProfileLibV4, line 1389
    } // library marker kkossev.deviceProfileLibV4, line 1390
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLibV4, line 1391
    return fncmd // library marker kkossev.deviceProfileLibV4, line 1392
} // library marker kkossev.deviceProfileLibV4, line 1393

// TODO: refactor! // library marker kkossev.deviceProfileLibV4, line 1395
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLibV4, line 1396
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLibV4, line 1397
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLibV4, line 1398
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLibV4, line 1399
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLibV4, line 1400
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLibV4, line 1401

    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 1403
    Map currentProfile = getCurrentDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 1404
    List<Map> attribMap = currentProfile?.attributes // library marker kkossev.deviceProfileLibV4, line 1405
    if (attribMap == null || attribMap?.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLibV4, line 1406

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLibV4, line 1408
    int value // library marker kkossev.deviceProfileLibV4, line 1409
    try { // library marker kkossev.deviceProfileLibV4, line 1410
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLibV4, line 1411
    } // library marker kkossev.deviceProfileLibV4, line 1412
    catch (e) { // library marker kkossev.deviceProfileLibV4, line 1413
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLibV4, line 1414
        return false // library marker kkossev.deviceProfileLibV4, line 1415
    } // library marker kkossev.deviceProfileLibV4, line 1416
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLibV4, line 1417
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLibV4, line 1418
        // clusterAttribute was not found in the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLibV4, line 1419
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLibV4, line 1420
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLibV4, line 1421
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLibV4, line 1422
        return false // library marker kkossev.deviceProfileLibV4, line 1423
    } // library marker kkossev.deviceProfileLibV4, line 1424
    value = convertSignedInts(value, foundItem) // library marker kkossev.deviceProfileLibV4, line 1425
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLibV4, line 1426
} // library marker kkossev.deviceProfileLibV4, line 1427

/** // library marker kkossev.deviceProfileLibV4, line 1429
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLibV4, line 1430
 * // library marker kkossev.deviceProfileLibV4, line 1431
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLibV4, line 1432
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLibV4, line 1433
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLibV4, line 1434
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLibV4, line 1435
 * // library marker kkossev.deviceProfileLibV4, line 1436
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLibV4, line 1437
 */ // library marker kkossev.deviceProfileLibV4, line 1438
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLibV4, line 1439
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLibV4, line 1440
    logTrace "processTuyaDPfromDeviceProfile: descMap = ${descMap}, dp = ${dp}, dp_id = ${dp_id}, fncmd_orig = ${fncmd_orig}, dp_len = ${dp_len}" // library marker kkossev.deviceProfileLibV4, line 1441
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLibV4, line 1442
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLibV4, line 1443
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLibV4, line 1444
    if (isInCooldown()) { logDebug "processTuyaDPfromDeviceProfile: in cooldown period, skipping DP processing"; return true }               // do not perform any further processing, if we are in the cooldown period // library marker kkossev.deviceProfileLibV4, line 1445

    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() } // library marker kkossev.deviceProfileLibV4, line 1447
    Map currentProfile = getCurrentDeviceProfile() // library marker kkossev.deviceProfileLibV4, line 1448
    List<Map> tuyaDPsMap = currentProfile?.tuyaDPs // library marker kkossev.deviceProfileLibV4, line 1449
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLibV4, line 1450

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLibV4, line 1452
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLibV4, line 1453
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLibV4, line 1454
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLibV4, line 1455
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLibV4, line 1456
        return false // library marker kkossev.deviceProfileLibV4, line 1457
    } // library marker kkossev.deviceProfileLibV4, line 1458
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLibV4, line 1459
} // library marker kkossev.deviceProfileLibV4, line 1460

/* // library marker kkossev.deviceProfileLibV4, line 1462
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLibV4, line 1463
 */ // library marker kkossev.deviceProfileLibV4, line 1464
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLibV4, line 1465
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLibV4, line 1466
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLibV4, line 1467
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLibV4, line 1468
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLibV4, line 1469
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLibV4, line 1470
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLibV4, line 1471
        if (preProcValue != value) { // library marker kkossev.deviceProfileLibV4, line 1472
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLibV4, line 1473
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLibV4, line 1474
            value = preProcValue as int // library marker kkossev.deviceProfileLibV4, line 1475
        } // library marker kkossev.deviceProfileLibV4, line 1476
    } // library marker kkossev.deviceProfileLibV4, line 1477
    //else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLibV4, line 1478

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLibV4, line 1480
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLibV4, line 1481
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1482
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLibV4, line 1483
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLibV4, line 1484
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences?.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLibV4, line 1485
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLibV4, line 1486
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLibV4, line 1487
    boolean isEqual = false // library marker kkossev.deviceProfileLibV4, line 1488
    boolean wasChanged = false // library marker kkossev.deviceProfileLibV4, line 1489
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLibV4, line 1490
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLibV4, line 1491
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLibV4, line 1492
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLibV4, line 1493
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1494
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLibV4, line 1495
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLibV4, line 1496

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLibV4, line 1498
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLibV4, line 1499
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLibV4, line 1500
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLibV4, line 1501
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLibV4, line 1502
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLibV4, line 1503
            if (settings.logEnable) { logInfo "${descText} (Debug logging is enabled)" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLibV4, line 1504
        } // library marker kkossev.deviceProfileLibV4, line 1505
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLibV4, line 1506
    } // library marker kkossev.deviceProfileLibV4, line 1507

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLibV4, line 1509
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLibV4, line 1510
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLibV4, line 1511
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLibV4, line 1512
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLibV4, line 1513
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLibV4, line 1514
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLibV4, line 1515
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLibV4, line 1516
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLibV4, line 1517
            } // library marker kkossev.deviceProfileLibV4, line 1518
        } // library marker kkossev.deviceProfileLibV4, line 1519
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLibV4, line 1520
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLibV4, line 1521
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLibV4, line 1522
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLibV4, line 1523
            } // library marker kkossev.deviceProfileLibV4, line 1524
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLibV4, line 1525
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLibV4, line 1526
            try { // library marker kkossev.deviceProfileLibV4, line 1527
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLibV4, line 1528
                wasChanged = true // library marker kkossev.deviceProfileLibV4, line 1529
            } // library marker kkossev.deviceProfileLibV4, line 1530
            catch (e) { // library marker kkossev.deviceProfileLibV4, line 1531
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLibV4, line 1532
            } // library marker kkossev.deviceProfileLibV4, line 1533
        } // library marker kkossev.deviceProfileLibV4, line 1534
    } // library marker kkossev.deviceProfileLibV4, line 1535
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLibV4, line 1536
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLibV4, line 1537
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLibV4, line 1538
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLibV4, line 1539
    } // library marker kkossev.deviceProfileLibV4, line 1540

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLibV4, line 1542
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLibV4, line 1543
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLibV4, line 1544
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLibV4, line 1545
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLibV4, line 1546
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLibV4, line 1547
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLibV4, line 1548
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLibV4, line 1549
            if (!doNotTrace) { // library marker kkossev.deviceProfileLibV4, line 1550
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLibV4, line 1551
            } // library marker kkossev.deviceProfileLibV4, line 1552
            if (foundItem.processDuplicated == true) { // library marker kkossev.deviceProfileLibV4, line 1553
                logDebug 'processDuplicated=true -> continue' // library marker kkossev.deviceProfileLibV4, line 1554
            } // library marker kkossev.deviceProfileLibV4, line 1555

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLibV4, line 1557
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLibV4, line 1558
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLibV4, line 1559
            // continue ... // library marker kkossev.deviceProfileLibV4, line 1560
            } // library marker kkossev.deviceProfileLibV4, line 1561

            else { // library marker kkossev.deviceProfileLibV4, line 1563
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLibV4, line 1564
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLibV4, line 1565
                } // library marker kkossev.deviceProfileLibV4, line 1566
                else { // library marker kkossev.deviceProfileLibV4, line 1567
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLibV4, line 1568
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLibV4, line 1569
                } // library marker kkossev.deviceProfileLibV4, line 1570
            } // library marker kkossev.deviceProfileLibV4, line 1571
        } // library marker kkossev.deviceProfileLibV4, line 1572

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLibV4, line 1574
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLibV4, line 1575
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLibV4, line 1576
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLibV4, line 1577
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLibV4, line 1578
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLibV4, line 1579
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLibV4, line 1580
        } // library marker kkossev.deviceProfileLibV4, line 1581
        else { // library marker kkossev.deviceProfileLibV4, line 1582
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLibV4, line 1583
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLibV4, line 1584
            if (!doNotTrace) { // library marker kkossev.deviceProfileLibV4, line 1585
                logTrace "event ${name} sent w/ valueScaled ${valueScaled}" // library marker kkossev.deviceProfileLibV4, line 1586
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLibV4, line 1587
            } // library marker kkossev.deviceProfileLibV4, line 1588
        } // library marker kkossev.deviceProfileLibV4, line 1589
    } // library marker kkossev.deviceProfileLibV4, line 1590
    return true     // all processing was done here! // library marker kkossev.deviceProfileLibV4, line 1591
} // library marker kkossev.deviceProfileLibV4, line 1592

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLibV4, line 1594
public boolean validateAndFixPreferences(String debugStr) { return validateAndFixPreferences(debugStr.toBoolean() as boolean) } // library marker kkossev.deviceProfileLibV4, line 1595
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLibV4, line 1596
    //debug = true // library marker kkossev.deviceProfileLibV4, line 1597
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLibV4, line 1598
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLibV4, line 1599
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLibV4, line 1600
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLibV4, line 1601
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLibV4, line 1602
    String settingType = '' // library marker kkossev.deviceProfileLibV4, line 1603
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLibV4, line 1604
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLibV4, line 1605
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLibV4, line 1606
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLibV4, line 1607
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLibV4, line 1608
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLibV4, line 1609
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLibV4, line 1610
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLibV4, line 1611
            validationFailures ++ // library marker kkossev.deviceProfileLibV4, line 1612
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLibV4, line 1613
            try { // library marker kkossev.deviceProfileLibV4, line 1614
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLibV4, line 1615
            } catch (e) { // library marker kkossev.deviceProfileLibV4, line 1616
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLibV4, line 1617
            } // library marker kkossev.deviceProfileLibV4, line 1618
            // first, try to use the old setting value // library marker kkossev.deviceProfileLibV4, line 1619
            try { // library marker kkossev.deviceProfileLibV4, line 1620
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLibV4, line 1621
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLibV4, line 1622
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLibV4, line 1623
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLibV4, line 1624
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLibV4, line 1625
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLibV4, line 1626
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLibV4, line 1627
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLibV4, line 1628
                    } // library marker kkossev.deviceProfileLibV4, line 1629
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLibV4, line 1630
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLibV4, line 1631
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLibV4, line 1632
                    } else { // library marker kkossev.deviceProfileLibV4, line 1633
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLibV4, line 1634
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLibV4, line 1635
                    } // library marker kkossev.deviceProfileLibV4, line 1636
                } // library marker kkossev.deviceProfileLibV4, line 1637
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLibV4, line 1638
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLibV4, line 1639
                validationFixes ++ // library marker kkossev.deviceProfileLibV4, line 1640
            } // library marker kkossev.deviceProfileLibV4, line 1641
            catch (e) { // library marker kkossev.deviceProfileLibV4, line 1642
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLibV4, line 1643
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLibV4, line 1644
                try { // library marker kkossev.deviceProfileLibV4, line 1645
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLibV4, line 1646
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLibV4, line 1647
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLibV4, line 1648
                    validationFixes ++ // library marker kkossev.deviceProfileLibV4, line 1649
                } catch (e2) { // library marker kkossev.deviceProfileLibV4, line 1650
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLibV4, line 1651
                } // library marker kkossev.deviceProfileLibV4, line 1652
            } // library marker kkossev.deviceProfileLibV4, line 1653
        } // library marker kkossev.deviceProfileLibV4, line 1654
        total ++ // library marker kkossev.deviceProfileLibV4, line 1655
    } // library marker kkossev.deviceProfileLibV4, line 1656
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLibV4, line 1657
    return true // library marker kkossev.deviceProfileLibV4, line 1658
} // library marker kkossev.deviceProfileLibV4, line 1659

public String fingerprintIt(Map profileMap, Map fingerprint) { // library marker kkossev.deviceProfileLibV4, line 1661
    if (profileMap == null) { return 'profileMap is null' } // library marker kkossev.deviceProfileLibV4, line 1662
    if (fingerprint == null) { return 'fingerprint is null' } // library marker kkossev.deviceProfileLibV4, line 1663
    Map defaultFingerprint = profileMap.defaultFingerprint ?: [:] // library marker kkossev.deviceProfileLibV4, line 1664
    // if there is no defaultFingerprint, use the fingerprint as is // library marker kkossev.deviceProfileLibV4, line 1665
    if (defaultFingerprint == [:]) { // library marker kkossev.deviceProfileLibV4, line 1666
        return fingerprint.toString() // library marker kkossev.deviceProfileLibV4, line 1667
    } // library marker kkossev.deviceProfileLibV4, line 1668
    // for the missing keys, use the default values // library marker kkossev.deviceProfileLibV4, line 1669
    String fingerprintStr = '' // library marker kkossev.deviceProfileLibV4, line 1670
    defaultFingerprint.each { key, value -> // library marker kkossev.deviceProfileLibV4, line 1671
        String keyValue = fingerprint[key] ?: value // library marker kkossev.deviceProfileLibV4, line 1672
        fingerprintStr += "${key}:'${keyValue}', " // library marker kkossev.deviceProfileLibV4, line 1673
    } // library marker kkossev.deviceProfileLibV4, line 1674
    // remove the last comma and space // library marker kkossev.deviceProfileLibV4, line 1675
    fingerprintStr = fingerprintStr[0..-3] // library marker kkossev.deviceProfileLibV4, line 1676
    return fingerprintStr // library marker kkossev.deviceProfileLibV4, line 1677
} // library marker kkossev.deviceProfileLibV4, line 1678

// debug/test method - prints all fingerprints in the g_deviceFingerprintsV4 map // library marker kkossev.deviceProfileLibV4, line 1680
public void printFingerprintsV4() { // library marker kkossev.deviceProfileLibV4, line 1681
    int count = 0 // library marker kkossev.deviceProfileLibV4, line 1682
    String fingerprintsText = "printFingerprintsV4: <br>" // library marker kkossev.deviceProfileLibV4, line 1683

    if (g_deviceFingerprintsV4 != null && !g_deviceFingerprintsV4?.isEmpty()) { // library marker kkossev.deviceProfileLibV4, line 1685
        g_deviceFingerprintsV4.each { profileName, profileData -> // library marker kkossev.deviceProfileLibV4, line 1686
            profileData.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLibV4, line 1687
                fingerprintsText += "  ${fingerprint}<br>" // library marker kkossev.deviceProfileLibV4, line 1688
                count++ // library marker kkossev.deviceProfileLibV4, line 1689
            } // library marker kkossev.deviceProfileLibV4, line 1690
        } // library marker kkossev.deviceProfileLibV4, line 1691
    } else { // library marker kkossev.deviceProfileLibV4, line 1692
        fingerprintsText += "<b>No g_deviceFingerprintsV4 available!</b><br>" // library marker kkossev.deviceProfileLibV4, line 1693
    } // library marker kkossev.deviceProfileLibV4, line 1694
    fingerprintsText += "<br><b>Total fingerprints: ${count}</b> size of fingerprintsText=${fingerprintsText.length()}" // library marker kkossev.deviceProfileLibV4, line 1695
    logInfo fingerprintsText // library marker kkossev.deviceProfileLibV4, line 1696
} // library marker kkossev.deviceProfileLibV4, line 1697

public void printPreferences() { // library marker kkossev.deviceProfileLibV4, line 1699
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLibV4, line 1700
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLibV4, line 1701
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLibV4, line 1702
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLibV4, line 1703
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLibV4, line 1704
                log.info inputMap // library marker kkossev.deviceProfileLibV4, line 1705
            } // library marker kkossev.deviceProfileLibV4, line 1706
        } // library marker kkossev.deviceProfileLibV4, line 1707
    } // library marker kkossev.deviceProfileLibV4, line 1708
} // library marker kkossev.deviceProfileLibV4, line 1709

void profilesV4info() { // library marker kkossev.deviceProfileLibV4, line 1711
    int size = g_deviceProfilesV4?.size() ?: 0 // library marker kkossev.deviceProfileLibV4, line 1712
    int fingerprintSize = g_deviceFingerprintsV4?.size() ?: 0 // library marker kkossev.deviceProfileLibV4, line 1713
    int currentProfileSize = g_currentProfilesV4?.size() ?: 0 // library marker kkossev.deviceProfileLibV4, line 1714
    List keys = g_deviceProfilesV4 ? new ArrayList(g_deviceProfilesV4.keySet()) : [] // library marker kkossev.deviceProfileLibV4, line 1715
    List fingerprintKeys = g_deviceFingerprintsV4 ? new ArrayList(g_deviceFingerprintsV4.keySet()) : [] // library marker kkossev.deviceProfileLibV4, line 1716
    List currentProfileKeys = g_currentProfilesV4 ? new ArrayList(g_currentProfilesV4.keySet()) : [] // library marker kkossev.deviceProfileLibV4, line 1717

    // Count computed fingerprints // library marker kkossev.deviceProfileLibV4, line 1719
    int totalComputedFingerprints = 0 // library marker kkossev.deviceProfileLibV4, line 1720
    g_deviceFingerprintsV4.each { key, value -> // library marker kkossev.deviceProfileLibV4, line 1721
        totalComputedFingerprints += value.computedFingerprints?.size() ?: 0 // library marker kkossev.deviceProfileLibV4, line 1722
    } // library marker kkossev.deviceProfileLibV4, line 1723

    String dni = device?.deviceNetworkId // library marker kkossev.deviceProfileLibV4, line 1725
    boolean hasCurrentProfile = g_currentProfilesV4.containsKey(dni) // library marker kkossev.deviceProfileLibV4, line 1726

    logInfo "profilesV4info: g_deviceProfilesV4 size=${size} keys=${keys}" // library marker kkossev.deviceProfileLibV4, line 1728
    logInfo "profilesV4info: g_deviceFingerprintsV4 size=${fingerprintSize}" // library marker kkossev.deviceProfileLibV4, line 1729
    logInfo "profilesV4info: g_currentProfilesV4 size=${currentProfileSize} keys=${currentProfileKeys}" // library marker kkossev.deviceProfileLibV4, line 1730
    logInfo "profilesV4info: total computed fingerprint strings=${totalComputedFingerprints}" // library marker kkossev.deviceProfileLibV4, line 1731
    if (hasCurrentProfile) { // library marker kkossev.deviceProfileLibV4, line 1732
        Map currentProfile = g_currentProfilesV4[dni] // library marker kkossev.deviceProfileLibV4, line 1733
        logInfo "profilesV4info: current profile for this device (${dni}) has ${currentProfile?.keySet()?.size() ?: 0} sections" // library marker kkossev.deviceProfileLibV4, line 1734
        logInfo "profilesV4info: current profile JSON data: ${JsonOutput.toJson(currentProfile)}" // library marker kkossev.deviceProfileLibV4, line 1735
    } // library marker kkossev.deviceProfileLibV4, line 1736
    else { // library marker kkossev.deviceProfileLibV4, line 1737
        logWarn "profilesV4info: this device (${dni}) has no current profile loaded" // library marker kkossev.deviceProfileLibV4, line 1738
    } // library marker kkossev.deviceProfileLibV4, line 1739
} // library marker kkossev.deviceProfileLibV4, line 1740

void clearProfilesCache() { // library marker kkossev.deviceProfileLibV4, line 1742
//    g_deviceProfilesV4.clear() // library marker kkossev.deviceProfileLibV4, line 1743
//    g_deviceFingerprintsV4.clear() // library marker kkossev.deviceProfileLibV4, line 1744
//    g_currentProfilesV4.clear() // library marker kkossev.deviceProfileLibV4, line 1745
    g_deviceProfilesV4 = null // library marker kkossev.deviceProfileLibV4, line 1746
    g_deviceFingerprintsV4 = null // library marker kkossev.deviceProfileLibV4, line 1747
    g_currentProfilesV4 = null // library marker kkossev.deviceProfileLibV4, line 1748
    g_profilesLoaded = false // library marker kkossev.deviceProfileLibV4, line 1749
    g_profilesLoading = false // library marker kkossev.deviceProfileLibV4, line 1750
} // library marker kkossev.deviceProfileLibV4, line 1751

void clearProfilesCacheInfo() { // library marker kkossev.deviceProfileLibV4, line 1753
    int before = g_deviceProfilesV4.size() // library marker kkossev.deviceProfileLibV4, line 1754
    int beforeFingerprints = g_deviceFingerprintsV4.size() // library marker kkossev.deviceProfileLibV4, line 1755
    int beforeCurrentProfiles = g_currentProfilesV4.size() // library marker kkossev.deviceProfileLibV4, line 1756
    clearProfilesCache() // library marker kkossev.deviceProfileLibV4, line 1757
    logInfo "clearProfilesCache: cleared ${before} V4 profiles, ${beforeFingerprints} fingerprint entries, and ${beforeCurrentProfiles} current profiles" // library marker kkossev.deviceProfileLibV4, line 1758
} // library marker kkossev.deviceProfileLibV4, line 1759


/** // library marker kkossev.deviceProfileLibV4, line 1762
 * Reconstructs a complete fingerprint Map by merging original fingerprint with defaultFingerprint values // library marker kkossev.deviceProfileLibV4, line 1763
 * This is similar to fingerprintIt() but returns a Map instead of a formatted String // library marker kkossev.deviceProfileLibV4, line 1764
 */ // library marker kkossev.deviceProfileLibV4, line 1765
private Map reconstructFingerprint(Map profileMap, Map fingerprint) { // library marker kkossev.deviceProfileLibV4, line 1766
    if (profileMap == null || fingerprint == null) {  // library marker kkossev.deviceProfileLibV4, line 1767
        return fingerprint ?: [:]  // library marker kkossev.deviceProfileLibV4, line 1768
    } // library marker kkossev.deviceProfileLibV4, line 1769

    Map defaultFingerprint = profileMap.defaultFingerprint ?: [:] // library marker kkossev.deviceProfileLibV4, line 1771
    // if there is no defaultFingerprint, use the fingerprint as is // library marker kkossev.deviceProfileLibV4, line 1772
    if (defaultFingerprint == [:]) { // library marker kkossev.deviceProfileLibV4, line 1773
        return fingerprint // library marker kkossev.deviceProfileLibV4, line 1774
    } // library marker kkossev.deviceProfileLibV4, line 1775

    // Create a new Map with default values, then overlay with actual fingerprint values // library marker kkossev.deviceProfileLibV4, line 1777
    Map reconstructed = [:] // library marker kkossev.deviceProfileLibV4, line 1778
    defaultFingerprint.each { key, defaultValue -> // library marker kkossev.deviceProfileLibV4, line 1779
        reconstructed[key] = fingerprint[key] ?: defaultValue // library marker kkossev.deviceProfileLibV4, line 1780
    } // library marker kkossev.deviceProfileLibV4, line 1781

    // Add any additional keys that exist in fingerprint but not in defaultFingerprint // library marker kkossev.deviceProfileLibV4, line 1783
    fingerprint.each { key, value -> // library marker kkossev.deviceProfileLibV4, line 1784
        if (!reconstructed.containsKey(key)) { // library marker kkossev.deviceProfileLibV4, line 1785
            reconstructed[key] = value // library marker kkossev.deviceProfileLibV4, line 1786
        } // library marker kkossev.deviceProfileLibV4, line 1787
    } // library marker kkossev.deviceProfileLibV4, line 1788

    return reconstructed // library marker kkossev.deviceProfileLibV4, line 1790
} // library marker kkossev.deviceProfileLibV4, line 1791

String getProfilesFilename() { // library marker kkossev.deviceProfileLibV4, line 1793
    return (settings.customJSON == null || settings.customJSON.trim().isEmpty()) ? DEFAULT_PROFILES_FILENAME : settings.customJSON // library marker kkossev.deviceProfileLibV4, line 1794
} // library marker kkossev.deviceProfileLibV4, line 1795

@Field static boolean g_OneTimeProfileLoadAttempted = false // library marker kkossev.deviceProfileLibV4, line 1797

boolean loadProfilesFromJSON() { // library marker kkossev.deviceProfileLibV4, line 1799
    if (isInCooldown()) { // library marker kkossev.deviceProfileLibV4, line 1800
        logDebug "loadProfilesFromJSON: in cooldown period, skipping profile load attempt" // library marker kkossev.deviceProfileLibV4, line 1801
        return false // library marker kkossev.deviceProfileLibV4, line 1802
    } // library marker kkossev.deviceProfileLibV4, line 1803
    String fileName = getProfilesFilename() // library marker kkossev.deviceProfileLibV4, line 1804
    state.profilesV4['lastUsedHeFile'] = fileName // library marker kkossev.deviceProfileLibV4, line 1805
    def data = readFile(fileName) // library marker kkossev.deviceProfileLibV4, line 1806
    if (data == null) { // library marker kkossev.deviceProfileLibV4, line 1807
        logWarn "loadProfilesFromJSON: readFile returned null for ${fileName}" // library marker kkossev.deviceProfileLibV4, line 1808
        String lastError = state.profilesV4['lastReadFileError'] ?: 'unknown error' // library marker kkossev.deviceProfileLibV4, line 1809
        logWarn "loadProfilesFromJSON: lastReadFileError = ${lastError} for ${fileName} g_OneTimeProfileLoadAttempted=${g_OneTimeProfileLoadAttempted}" // library marker kkossev.deviceProfileLibV4, line 1810
        // if the file was not found, and we have not yet attempted to download it from GitHub, do it now // library marker kkossev.deviceProfileLibV4, line 1811
        if ((lastError.contains('Not Found') || lastError.contains('404')) && !g_OneTimeProfileLoadAttempted) { // library marker kkossev.deviceProfileLibV4, line 1812
            sendInfoEvent "loadProfilesFromJSON: file ${fileName} not found - one-time attempt to download from GitHub..." // library marker kkossev.deviceProfileLibV4, line 1813
            g_OneTimeProfileLoadAttempted = true // library marker kkossev.deviceProfileLibV4, line 1814
            clearProfilesCache()   // clear any partially loaded profiles // library marker kkossev.deviceProfileLibV4, line 1815
            runIn(2, 'oneTimeUpdateFromGitHub', [data: [fileName: fileName]]) // library marker kkossev.deviceProfileLibV4, line 1816
        } // library marker kkossev.deviceProfileLibV4, line 1817
        startCooldownTimer() // library marker kkossev.deviceProfileLibV4, line 1818
        return false // library marker kkossev.deviceProfileLibV4, line 1819
    } // library marker kkossev.deviceProfileLibV4, line 1820
    return loadProfilesFromJSONstring(data) // library marker kkossev.deviceProfileLibV4, line 1821
} // library marker kkossev.deviceProfileLibV4, line 1822

void oneTimeUpdateFromGitHub(Map data) { // library marker kkossev.deviceProfileLibV4, line 1824
    String gitHubUrl = url?.trim() ?: defaultGitHubURL // library marker kkossev.deviceProfileLibV4, line 1825
    String fileName = DEFAULT_PROFILES_FILENAME // library marker kkossev.deviceProfileLibV4, line 1826
    logDebug "oneTimeUpdateFromGitHub: attempting to download ${fileName} and update product profiles from GitHub url ${gitHubUrl}..." // library marker kkossev.deviceProfileLibV4, line 1827
    updateFromGitHub(gitHubUrl) // library marker kkossev.deviceProfileLibV4, line 1828
} // library marker kkossev.deviceProfileLibV4, line 1829


// called froloadProfilesFromJSON  // library marker kkossev.deviceProfileLibV4, line 1832
def readFile(fName) { // library marker kkossev.deviceProfileLibV4, line 1833
    long contentStartTime = now() // library marker kkossev.deviceProfileLibV4, line 1834
    //uri = "http://${location.hub.localIP}:8080/local/deviceProfilesV4_mmWave.json" // library marker kkossev.deviceProfileLibV4, line 1835
    uri = "http://${location.hub.localIP}:8080/local/${fName}" // library marker kkossev.deviceProfileLibV4, line 1836

    def params = [ // library marker kkossev.deviceProfileLibV4, line 1838
        uri: uri, // library marker kkossev.deviceProfileLibV4, line 1839
        textParser: true, // library marker kkossev.deviceProfileLibV4, line 1840
    ] // library marker kkossev.deviceProfileLibV4, line 1841
    if (state.profilesV4 == null) { state.profilesV4 = [:] } // library marker kkossev.deviceProfileLibV4, line 1842
    state.profilesV4['lastReadFileError'] = '' // library marker kkossev.deviceProfileLibV4, line 1843
    try { // library marker kkossev.deviceProfileLibV4, line 1844
        httpGet(params) { resp -> // library marker kkossev.deviceProfileLibV4, line 1845
            if(resp!= null) { // library marker kkossev.deviceProfileLibV4, line 1846
                def data = resp.getData(); // library marker kkossev.deviceProfileLibV4, line 1847
                logDebug "readFile: read ${data.length} chars from ${uri}" // library marker kkossev.deviceProfileLibV4, line 1848
                long contentEndTime = now() // library marker kkossev.deviceProfileLibV4, line 1849
                long contentDuration = contentEndTime - contentStartTime // library marker kkossev.deviceProfileLibV4, line 1850
                logDebug "Performance: Content=${contentDuration}ms" // library marker kkossev.deviceProfileLibV4, line 1851
                state.profilesV4['lastReadFileError'] = 'OK' // library marker kkossev.deviceProfileLibV4, line 1852
                return data // library marker kkossev.deviceProfileLibV4, line 1853
            } // library marker kkossev.deviceProfileLibV4, line 1854
            else { // library marker kkossev.deviceProfileLibV4, line 1855
                log.error "${device?.displayName}  Null Response" // library marker kkossev.deviceProfileLibV4, line 1856
                state.profilesV4['lastReadFileError'] = 'null response' // library marker kkossev.deviceProfileLibV4, line 1857
            } // library marker kkossev.deviceProfileLibV4, line 1858
        } // library marker kkossev.deviceProfileLibV4, line 1859
    } catch (exception) { // library marker kkossev.deviceProfileLibV4, line 1860
        log.error "${device?.displayName} Connection Exception: ${exception.message}" // library marker kkossev.deviceProfileLibV4, line 1861
        state.profilesV4['lastReadFileError'] = exception.message // library marker kkossev.deviceProfileLibV4, line 1862
        return null; // library marker kkossev.deviceProfileLibV4, line 1863
    } // library marker kkossev.deviceProfileLibV4, line 1864
} // library marker kkossev.deviceProfileLibV4, line 1865



boolean loadProfilesFromJSONstring(stringifiedJSON) { // library marker kkossev.deviceProfileLibV4, line 1869
    long startTime = now() // library marker kkossev.deviceProfileLibV4, line 1870

    // idempotent : don't re-parse if already populated // library marker kkossev.deviceProfileLibV4, line 1872
    if (g_deviceProfilesV4 != null && !g_deviceProfilesV4?.isEmpty()) { // library marker kkossev.deviceProfileLibV4, line 1873
        logDebug "loadProfilesFromJSON: already loaded (${g_deviceProfilesV4.size()} profiles)" // library marker kkossev.deviceProfileLibV4, line 1874
        return true // library marker kkossev.deviceProfileLibV4, line 1875
    } // library marker kkossev.deviceProfileLibV4, line 1876
    try { // library marker kkossev.deviceProfileLibV4, line 1877
        logDebug "loadProfilesFromJSON: start loading device profiles from JSON..." // library marker kkossev.deviceProfileLibV4, line 1878
        if (!stringifiedJSON) { // library marker kkossev.deviceProfileLibV4, line 1879
            logWarn "loadProfilesFromJSON: stringifiedJSON is empty/null" // library marker kkossev.deviceProfileLibV4, line 1880
            return false // library marker kkossev.deviceProfileLibV4, line 1881
        } // library marker kkossev.deviceProfileLibV4, line 1882

        def jsonSlurper = new JsonSlurper(); // library marker kkossev.deviceProfileLibV4, line 1884
        def parsed = jsonSlurper.parseText("${stringifiedJSON}"); // library marker kkossev.deviceProfileLibV4, line 1885

        def dp = parsed?.deviceProfiles // library marker kkossev.deviceProfileLibV4, line 1887
        if (!(dp instanceof Map) || dp.isEmpty()) { // library marker kkossev.deviceProfileLibV4, line 1888
            logWarn "loadProfilesFromJSON: parsed deviceProfiles missing or empty" // library marker kkossev.deviceProfileLibV4, line 1889
            startCooldownTimer() // library marker kkossev.deviceProfileLibV4, line 1890
            return false // library marker kkossev.deviceProfileLibV4, line 1891
        } // library marker kkossev.deviceProfileLibV4, line 1892
        resetCooldownFlag() // library marker kkossev.deviceProfileLibV4, line 1893
        // !!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLibV4, line 1894
        // Populate g_deviceProfilesV4 // library marker kkossev.deviceProfileLibV4, line 1895
        if (g_deviceProfilesV4 == null) { g_deviceProfilesV4 = [:] }   // initialize if null // library marker kkossev.deviceProfileLibV4, line 1896
        else { g_deviceProfilesV4.clear() }                             // clear existing entries if any // library marker kkossev.deviceProfileLibV4, line 1897
        g_deviceProfilesV4.putAll(dp as Map) // library marker kkossev.deviceProfileLibV4, line 1898
        logDebug "loadProfilesFromJSON: g_deviceProfilesV4 populated with ${g_deviceProfilesV4.size()} profiles" // library marker kkossev.deviceProfileLibV4, line 1899

        // Populate g_deviceFingerprintsV4 using bulk assignment for better performance // library marker kkossev.deviceProfileLibV4, line 1901
        // Use fingerprintIt() logic to reconstruct complete fingerprint data // library marker kkossev.deviceProfileLibV4, line 1902
        Map localFingerprints = [:] // library marker kkossev.deviceProfileLibV4, line 1903

        g_deviceProfilesV4.each { profileKey, profileMap -> // library marker kkossev.deviceProfileLibV4, line 1905
            // Reconstruct complete fingerprint Maps and pre-compute strings // library marker kkossev.deviceProfileLibV4, line 1906
            List<Map> reconstructedFingerprints = [] // library marker kkossev.deviceProfileLibV4, line 1907
            List<String> computedFingerprintStrings = [] // library marker kkossev.deviceProfileLibV4, line 1908

            if (profileMap.fingerprints != null) { // library marker kkossev.deviceProfileLibV4, line 1910
                profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLibV4, line 1911
                    // Reconstruct complete fingerprint using fingerprintIt logic // library marker kkossev.deviceProfileLibV4, line 1912
                    Map reconstructedFingerprint = reconstructFingerprint(profileMap, fingerprint) // library marker kkossev.deviceProfileLibV4, line 1913
                    reconstructedFingerprints.add(reconstructedFingerprint) // library marker kkossev.deviceProfileLibV4, line 1914

                    // Also create formatted string for debugging // library marker kkossev.deviceProfileLibV4, line 1916
                    String fpString = fingerprintIt(profileMap, fingerprint) // library marker kkossev.deviceProfileLibV4, line 1917
                    if (fpString && fpString != 'profileMap is null' && fpString != 'fingerprint is null') { // library marker kkossev.deviceProfileLibV4, line 1918
                        computedFingerprintStrings.add(fpString) // library marker kkossev.deviceProfileLibV4, line 1919
                    } // library marker kkossev.deviceProfileLibV4, line 1920
                } // library marker kkossev.deviceProfileLibV4, line 1921
            } // library marker kkossev.deviceProfileLibV4, line 1922

            localFingerprints[profileKey] = [ // library marker kkossev.deviceProfileLibV4, line 1924
                description: profileMap.description ?: '', // library marker kkossev.deviceProfileLibV4, line 1925
                fingerprints: reconstructedFingerprints, // Use reconstructed complete fingerprints // library marker kkossev.deviceProfileLibV4, line 1926
                computedFingerprints: computedFingerprintStrings // library marker kkossev.deviceProfileLibV4, line 1927
            ] // library marker kkossev.deviceProfileLibV4, line 1928
        } // library marker kkossev.deviceProfileLibV4, line 1929
        if (g_deviceFingerprintsV4 == null) { g_deviceFingerprintsV4 = [:] }   // initialize if null // library marker kkossev.deviceProfileLibV4, line 1930
        else { g_deviceFingerprintsV4.clear() }                             // clear existing entries if any // library marker kkossev.deviceProfileLibV4, line 1931
        g_deviceFingerprintsV4.putAll(localFingerprints) // library marker kkossev.deviceProfileLibV4, line 1932
        logDebug "loadProfilesFromJSON: g_deviceFingerprintsV4 populated with ${g_deviceFingerprintsV4.size()} entries" // library marker kkossev.deviceProfileLibV4, line 1933

        // Count total computed fingerprint strings // library marker kkossev.deviceProfileLibV4, line 1935
        int totalComputedFingerprints = 0 // library marker kkossev.deviceProfileLibV4, line 1936
        localFingerprints.each { key, value -> // library marker kkossev.deviceProfileLibV4, line 1937
            totalComputedFingerprints += value.computedFingerprints?.size() ?: 0 // library marker kkossev.deviceProfileLibV4, line 1938
        } // library marker kkossev.deviceProfileLibV4, line 1939

        // NOTE: g_profilesLoaded flag is managed by ensureProfilesLoaded(), not here // library marker kkossev.deviceProfileLibV4, line 1941
        // This keeps loadProfilesFromJSON() as a pure function // library marker kkossev.deviceProfileLibV4, line 1942
        long endTime = now() // library marker kkossev.deviceProfileLibV4, line 1943
        long executionTime = endTime - startTime // library marker kkossev.deviceProfileLibV4, line 1944

        logDebug "loadProfilesFromJSON: loaded ${g_deviceProfilesV4.size()} profiles: ${g_deviceProfilesV4.keySet()}" // library marker kkossev.deviceProfileLibV4, line 1946
        logDebug "loadProfilesFromJSON: populated ${g_deviceFingerprintsV4.size()} fingerprint entries" // library marker kkossev.deviceProfileLibV4, line 1947
        logDebug "loadProfilesFromJSON: pre-computed ${totalComputedFingerprints} fingerprint strings" // library marker kkossev.deviceProfileLibV4, line 1948
        logDebug "loadProfilesFromJSON: execution time: ${executionTime}ms" // library marker kkossev.deviceProfileLibV4, line 1949
        return true // library marker kkossev.deviceProfileLibV4, line 1950

    } catch (Exception e) { // library marker kkossev.deviceProfileLibV4, line 1952
        long endTime = now() // library marker kkossev.deviceProfileLibV4, line 1953
        long executionTime = endTime - startTime // library marker kkossev.deviceProfileLibV4, line 1954
        logError "loadProfilesFromJSON exception: error converting JSON: ${e.message} (execution time: ${executionTime}ms)" // library marker kkossev.deviceProfileLibV4, line 1955
        startCooldownTimer() // library marker kkossev.deviceProfileLibV4, line 1956
        return false // library marker kkossev.deviceProfileLibV4, line 1957
    } // library marker kkossev.deviceProfileLibV4, line 1958
} // library marker kkossev.deviceProfileLibV4, line 1959


void startCooldownTimer() { // library marker kkossev.deviceProfileLibV4, line 1962
    if (g_loadProfilesCooldown) { // library marker kkossev.deviceProfileLibV4, line 1963
        return // library marker kkossev.deviceProfileLibV4, line 1964
    } // library marker kkossev.deviceProfileLibV4, line 1965
    g_loadProfilesCooldown = true // library marker kkossev.deviceProfileLibV4, line 1966
    runInMillis(LOAD_PROFILES_COOLDOWN_MS, resetCooldownFlag, [overwrite: true]) // library marker kkossev.deviceProfileLibV4, line 1967
    logWarn "startCooldownTimer: starting cooldown timer for ${LOAD_PROFILES_COOLDOWN_MS} ms to prevent multiple profile loading attempts" // library marker kkossev.deviceProfileLibV4, line 1968
} // library marker kkossev.deviceProfileLibV4, line 1969

void resetCooldownFlag() { // library marker kkossev.deviceProfileLibV4, line 1971
    g_loadProfilesCooldown = false // library marker kkossev.deviceProfileLibV4, line 1972
    logDebug "resetCooldownFlag: cooldown period ended, can attempt profile loading again" // library marker kkossev.deviceProfileLibV4, line 1973
} // library marker kkossev.deviceProfileLibV4, line 1974

boolean isInCooldown() { // library marker kkossev.deviceProfileLibV4, line 1976
    return g_loadProfilesCooldown // library marker kkossev.deviceProfileLibV4, line 1977
} // library marker kkossev.deviceProfileLibV4, line 1978



/** // library marker kkossev.deviceProfileLibV4, line 1982
 * Ensures that device profiles are loaded with thread-safe lazy loading // library marker kkossev.deviceProfileLibV4, line 1983
 * This is the main function that should be called before accessing g_deviceProfilesV4 // library marker kkossev.deviceProfileLibV4, line 1984
 * @return true if profiles are loaded successfully, false otherwise // library marker kkossev.deviceProfileLibV4, line 1985
 */ // library marker kkossev.deviceProfileLibV4, line 1986
private boolean ensureProfilesLoaded() { // library marker kkossev.deviceProfileLibV4, line 1987
    // Fast path: already loaded // library marker kkossev.deviceProfileLibV4, line 1988
//    if (!g_deviceProfilesV4.isEmpty() && g_profilesLoaded) { // library marker kkossev.deviceProfileLibV4, line 1989
    if (g_profilesLoaded && !g_currentProfilesV4?.isEmpty()) {       // !!!!!!!!!!!!!!!!!!!!!!!! TODO - check !!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLibV4, line 1990
        return true // library marker kkossev.deviceProfileLibV4, line 1991
    } // library marker kkossev.deviceProfileLibV4, line 1992
    if (state.profilesV4 == null) { state.profilesV4 = [:] }   // initialize state variable if not present // library marker kkossev.deviceProfileLibV4, line 1993
    if (isInCooldown()) { // library marker kkossev.deviceProfileLibV4, line 1994
        state.profilesV4['cooldownSkipsCtr'] = (state.profilesV4['cooldownSkipsCtr'] ?: 0) + 1 // library marker kkossev.deviceProfileLibV4, line 1995
        logDebug "ensureProfilesLoaded: in cooldown period, skipping profile load attempt" // library marker kkossev.deviceProfileLibV4, line 1996
        return false // library marker kkossev.deviceProfileLibV4, line 1997
    } // library marker kkossev.deviceProfileLibV4, line 1998
    // Check if another thread is already loading // library marker kkossev.deviceProfileLibV4, line 1999
    if (g_profilesLoading) { // library marker kkossev.deviceProfileLibV4, line 2000
        // Wait briefly for other thread to finish // library marker kkossev.deviceProfileLibV4, line 2001
        for (int i = 0; i < 10; i++) { // library marker kkossev.deviceProfileLibV4, line 2002
            state.profilesV4['waitForOtherThreadCtr'] = (state.profilesV4['waitForOtherThreadCtr'] ?: 0) + 1 // library marker kkossev.deviceProfileLibV4, line 2003
            logInfo "ensureProfilesLoaded: waiting <b>100ms</b> for other thread to finish loading... try ${i+1}/10" // library marker kkossev.deviceProfileLibV4, line 2004
            pauseExecution(100) // library marker kkossev.deviceProfileLibV4, line 2005
            if (g_profilesLoaded && !g_deviceProfilesV4?.isEmpty()) { // library marker kkossev.deviceProfileLibV4, line 2006
                sendInfoEvent "ensureProfilesLoaded: other thread finished loading" // library marker kkossev.deviceProfileLibV4, line 2007
                return true // library marker kkossev.deviceProfileLibV4, line 2008
            } // library marker kkossev.deviceProfileLibV4, line 2009
        } // library marker kkossev.deviceProfileLibV4, line 2010
        // If still loading after wait, return false - don't interfere with other thread // library marker kkossev.deviceProfileLibV4, line 2011
        sendInfoEvent "ensureProfilesLoaded: timeout waiting for other thread, giving up!" // library marker kkossev.deviceProfileLibV4, line 2012
        state.profilesV4['waitForOtherThreadTimeouts'] = (state.profilesV4['waitForOtherThreadTimeouts'] ?: 0) + 1 // library marker kkossev.deviceProfileLibV4, line 2013
        return false // library marker kkossev.deviceProfileLibV4, line 2014
    } // library marker kkossev.deviceProfileLibV4, line 2015

    // Acquire loading lock // library marker kkossev.deviceProfileLibV4, line 2017
    g_profilesLoading = true // library marker kkossev.deviceProfileLibV4, line 2018
    try { // library marker kkossev.deviceProfileLibV4, line 2019
        // Double-check after acquiring lock // library marker kkossev.deviceProfileLibV4, line 2020
        Boolean isEmpty = (g_deviceProfilesV4 == null) ? true : g_deviceProfilesV4?.isEmpty() // library marker kkossev.deviceProfileLibV4, line 2021
        if (isEmpty || !g_profilesLoaded) { // library marker kkossev.deviceProfileLibV4, line 2022
            state.profilesV4['loadProfilesCtr'] = (state.profilesV4['loadProfilesCtr'] ?: 0) + 1 // library marker kkossev.deviceProfileLibV4, line 2023
            logDebug "ensureProfilesLoaded: loading device profiles...(isEmpty=${isEmpty}, g_profilesLoaded=${g_profilesLoaded})" // library marker kkossev.deviceProfileLibV4, line 2024
            boolean result = loadProfilesFromJSON() // library marker kkossev.deviceProfileLibV4, line 2025
            if (result) { // library marker kkossev.deviceProfileLibV4, line 2026
                g_profilesLoaded = true // library marker kkossev.deviceProfileLibV4, line 2027
                sendInfoEvent "Successfully loaded ${g_deviceProfilesV4.size()} deviceProfilesV4 profiles" // library marker kkossev.deviceProfileLibV4, line 2028
            } else { // library marker kkossev.deviceProfileLibV4, line 2029
                sendInfoEvent "ensureProfilesLoaded: failed to load device profiles (loadProfilesFromJSON() failed)" // library marker kkossev.deviceProfileLibV4, line 2030
                startCooldownTimer() // library marker kkossev.deviceProfileLibV4, line 2031
            } // library marker kkossev.deviceProfileLibV4, line 2032
            g_profilesLoading = false // library marker kkossev.deviceProfileLibV4, line 2033
            return result // library marker kkossev.deviceProfileLibV4, line 2034
        } // library marker kkossev.deviceProfileLibV4, line 2035
        return true // library marker kkossev.deviceProfileLibV4, line 2036
    } finally { // library marker kkossev.deviceProfileLibV4, line 2037
        state.profilesV4['loadProfilesExceptionsCtr'] = (state.profilesV4['loadProfilesExceptionsCtr'] ?: 0) + 1 // library marker kkossev.deviceProfileLibV4, line 2038
        g_profilesLoading = false // library marker kkossev.deviceProfileLibV4, line 2039
    } // library marker kkossev.deviceProfileLibV4, line 2040
} // library marker kkossev.deviceProfileLibV4, line 2041

void updateFromGitHub(String url = '') { // library marker kkossev.deviceProfileLibV4, line 2043
    dwownloadFromGitHubAndSaveToHE(url) // library marker kkossev.deviceProfileLibV4, line 2044
    clearProfilesCache() // library marker kkossev.deviceProfileLibV4, line 2045
    ensureProfilesLoaded() // library marker kkossev.deviceProfileLibV4, line 2046
    ensureCurrentProfileLoaded() // library marker kkossev.deviceProfileLibV4, line 2047
} // library marker kkossev.deviceProfileLibV4, line 2048


// updateFromGitHub command - download JSON profiles from GitHub and store to Hubitat local storage // library marker kkossev.deviceProfileLibV4, line 2051
void dwownloadFromGitHubAndSaveToHE(String url = '') { // library marker kkossev.deviceProfileLibV4, line 2052
    long startTime = now() // library marker kkossev.deviceProfileLibV4, line 2053
    String gitHubUrl = url?.trim() ?: defaultGitHubURL // library marker kkossev.deviceProfileLibV4, line 2054
    String fileName = DEFAULT_PROFILES_FILENAME // library marker kkossev.deviceProfileLibV4, line 2055

    // If URL is provided, try to extract filename from it // library marker kkossev.deviceProfileLibV4, line 2057
    if (url?.trim()) { // library marker kkossev.deviceProfileLibV4, line 2058
        try { // library marker kkossev.deviceProfileLibV4, line 2059
            String urlPath = gitHubUrl.split('/').last() // library marker kkossev.deviceProfileLibV4, line 2060
            if (urlPath.toLowerCase().endsWith('.json')) { // library marker kkossev.deviceProfileLibV4, line 2061
                fileName = urlPath // library marker kkossev.deviceProfileLibV4, line 2062
            } // library marker kkossev.deviceProfileLibV4, line 2063
        } catch (Exception e) { } // library marker kkossev.deviceProfileLibV4, line 2064
    } // library marker kkossev.deviceProfileLibV4, line 2065
    logInfo "updateFromGitHub: downloading ${fileName} from ${gitHubUrl}" // library marker kkossev.deviceProfileLibV4, line 2066
    try { // library marker kkossev.deviceProfileLibV4, line 2067
        // Download JSON content from GitHub // library marker kkossev.deviceProfileLibV4, line 2068
        long downloadStartTime = now() // library marker kkossev.deviceProfileLibV4, line 2069
        def params = [ // library marker kkossev.deviceProfileLibV4, line 2070
            uri: gitHubUrl, // library marker kkossev.deviceProfileLibV4, line 2071
            //textParser: true  // This is the key! Same as working readFile method // library marker kkossev.deviceProfileLibV4, line 2072
        ] // library marker kkossev.deviceProfileLibV4, line 2073

        logDebug "updateFromGitHub: HTTP params: ${params}" // library marker kkossev.deviceProfileLibV4, line 2075

        httpGet(params) { resp -> // library marker kkossev.deviceProfileLibV4, line 2077
            logDebug "updateFromGitHub: Response status: ${resp?.status}" // library marker kkossev.deviceProfileLibV4, line 2078
            state.gitHubV4['httpGetCallsCtr'] = (state.gitHubV4['httpGetCallsCtr'] ?: 0) + 1 // library marker kkossev.deviceProfileLibV4, line 2079
            state.gitHubV4['httpGetLastStatus'] = resp?.status // library marker kkossev.deviceProfileLibV4, line 2080

            if (resp?.status == 200 && resp?.data) { // library marker kkossev.deviceProfileLibV4, line 2082
                // Fix StringReader issue - get actual text content without explicit class references // library marker kkossev.deviceProfileLibV4, line 2083
                String jsonContent = "" // library marker kkossev.deviceProfileLibV4, line 2084
                def responseData = resp.getData() // library marker kkossev.deviceProfileLibV4, line 2085

                if (responseData instanceof String) { // library marker kkossev.deviceProfileLibV4, line 2087
                    jsonContent = responseData // library marker kkossev.deviceProfileLibV4, line 2088
                } else if (responseData?.hasProperty('text')) { // library marker kkossev.deviceProfileLibV4, line 2089
                    // Handle StringReader without explicit class reference // library marker kkossev.deviceProfileLibV4, line 2090
                    jsonContent = responseData.text // library marker kkossev.deviceProfileLibV4, line 2091
                } else { // library marker kkossev.deviceProfileLibV4, line 2092
                    jsonContent = responseData.toString() // library marker kkossev.deviceProfileLibV4, line 2093
                } // library marker kkossev.deviceProfileLibV4, line 2094

                long downloadEndTime = now() // library marker kkossev.deviceProfileLibV4, line 2096
                long downloadDuration = downloadEndTime - downloadStartTime // library marker kkossev.deviceProfileLibV4, line 2097
                //logInfo "updateFromGitHub: downloaded ${jsonContent.length()} characters" // library marker kkossev.deviceProfileLibV4, line 2098
                //logDebug "updateFromGitHub: first 100 chars: ${jsonContent.take(100)}" // library marker kkossev.deviceProfileLibV4, line 2099
                //logInfo "updateFromGitHub: Performance - Download: ${downloadDuration}ms" // library marker kkossev.deviceProfileLibV4, line 2100
                sendInfoEvent "Successfully downloaded ${fileName} from GitHub, ${jsonContent.length()} characters in ${downloadDuration}ms" // library marker kkossev.deviceProfileLibV4, line 2101
                state.gitHubV4['lastDownloadSize'] = jsonContent.length() // library marker kkossev.deviceProfileLibV4, line 2102
                state.gitHubV4['lastDownloadTime'] = now() // library marker kkossev.deviceProfileLibV4, line 2103
                state.gitHubV4['lastDownloadDuration'] = downloadDuration // library marker kkossev.deviceProfileLibV4, line 2104

                // Validate it's actually JSON content // library marker kkossev.deviceProfileLibV4, line 2106
                if (jsonContent.length() < 100 || !jsonContent.trim().startsWith("{")) { // library marker kkossev.deviceProfileLibV4, line 2107
                    //logWarn "updateFromGitHub: ? Downloaded content doesn't appear to be valid JSON" // library marker kkossev.deviceProfileLibV4, line 2108
                    logWarn "updateFromGitHub: Content preview: ${jsonContent.take(200)}" // library marker kkossev.deviceProfileLibV4, line 2109
                    state.gitHubV4['lastDownloadError'] = "Invalid JSON" // library marker kkossev.deviceProfileLibV4, line 2110
                    sendInfoEvent "updateFromGitHub: ? Downloaded content doesn't appear to be valid JSON" // library marker kkossev.deviceProfileLibV4, line 2111
                    return // library marker kkossev.deviceProfileLibV4, line 2112
                } // library marker kkossev.deviceProfileLibV4, line 2113
                state.gitHubV4['lastDownloadError'] = null // library marker kkossev.deviceProfileLibV4, line 2114

                // Parse and extract version/timestamp information for debugging // library marker kkossev.deviceProfileLibV4, line 2116
                try { // library marker kkossev.deviceProfileLibV4, line 2117
                    def jsonSlurper = new groovy.json.JsonSlurper() // library marker kkossev.deviceProfileLibV4, line 2118
                    def parsedJson = jsonSlurper.parseText(jsonContent) // library marker kkossev.deviceProfileLibV4, line 2119

                    def version = parsedJson?.version ?: "unknown" // library marker kkossev.deviceProfileLibV4, line 2121
                    def timestamp = parsedJson?.timestamp ?: "unknown" // library marker kkossev.deviceProfileLibV4, line 2122
                    def author = parsedJson?.author ?: "unknown" // library marker kkossev.deviceProfileLibV4, line 2123
                    def profileCount = parsedJson?.deviceProfiles?.size() ?: 0 // library marker kkossev.deviceProfileLibV4, line 2124

                    logDebug "updateFromGitHub: JSON Metadata - Version: ${version}, Timestamp: ${timestamp}" // library marker kkossev.deviceProfileLibV4, line 2126
                    logDebug "updateFromGitHub: JSON Metadata - Author: ${author}, Device Profiles: ${profileCount}" // library marker kkossev.deviceProfileLibV4, line 2127
                    state.gitHubV4['lastDownloadVersion'] = version // library marker kkossev.deviceProfileLibV4, line 2128
                    state.gitHubV4['lastDownloadTimestamp'] = timestamp // library marker kkossev.deviceProfileLibV4, line 2129

                } catch (Exception jsonException) { // library marker kkossev.deviceProfileLibV4, line 2131
                    logWarn "updateFromGitHub: Could not parse JSON metadata: ${jsonException.message}" // library marker kkossev.deviceProfileLibV4, line 2132
                    state.gitHubV4['lastDownloadVersion'] = null // library marker kkossev.deviceProfileLibV4, line 2133
                    state.gitHubV4['lastDownloadTimestamp'] = null // library marker kkossev.deviceProfileLibV4, line 2134
                    sendInfoEvent "updateFromGitHub: ? Could not parse JSON metadata: ${jsonException.message}" // library marker kkossev.deviceProfileLibV4, line 2135
                    return // library marker kkossev.deviceProfileLibV4, line 2136
                } // library marker kkossev.deviceProfileLibV4, line 2137

                // Store the content to Hubitat local storage using uploadHubFile // library marker kkossev.deviceProfileLibV4, line 2139
                try { // library marker kkossev.deviceProfileLibV4, line 2140
                    long uploadStartTime = now() // library marker kkossev.deviceProfileLibV4, line 2141
                    // Use uploadHubFile to save content directly to local storage (correct API method) // library marker kkossev.deviceProfileLibV4, line 2142
                    def fileBytes = jsonContent.getBytes("UTF-8") // library marker kkossev.deviceProfileLibV4, line 2143
                    uploadHubFile(fileName, fileBytes)  // void method - no return value // library marker kkossev.deviceProfileLibV4, line 2144

                    long uploadEndTime = now() // library marker kkossev.deviceProfileLibV4, line 2146
                    long uploadDuration = uploadEndTime - uploadStartTime // library marker kkossev.deviceProfileLibV4, line 2147

                    sendInfoEvent "updateFromGitHub: Successfully uploaded ${fileName} to Hubitat local storage, ${uploadDuration}ms, ${fileBytes.length} bytes" // library marker kkossev.deviceProfileLibV4, line 2149
                    //logInfo "updateFromGitHub: File size: ${jsonContent.length()} characters" // library marker kkossev.deviceProfileLibV4, line 2150
                    //logInfo "updateFromGitHub: Performance - Upload: ${uploadDuration}ms" // library marker kkossev.deviceProfileLibV4, line 2151
                    //sendInfoEvent "Successfully updated ${fileName} (${jsonContent.length()} characters) in Hubitat local storage" // library marker kkossev.deviceProfileLibV4, line 2152

                    // Optional: Clear current profiles to force reload on next access // library marker kkossev.deviceProfileLibV4, line 2154
                    //g_deviceProfilesV4.clear() // library marker kkossev.deviceProfileLibV4, line 2155
                    //g_deviceFingerprintsV4.clear() // library marker kkossev.deviceProfileLibV4, line 2156
                    //g_currentProfilesV4.clear() // library marker kkossev.deviceProfileLibV4, line 2157
                    /* // library marker kkossev.deviceProfileLibV4, line 2158
                    g_deviceProfilesV4 = null // library marker kkossev.deviceProfileLibV4, line 2159
                    g_deviceFingerprintsV4 = null // library marker kkossev.deviceProfileLibV4, line 2160
                    g_currentProfilesV4 = null // library marker kkossev.deviceProfileLibV4, line 2161
                    g_profilesLoaded = false // library marker kkossev.deviceProfileLibV4, line 2162
                    g_profilesLoading = false // library marker kkossev.deviceProfileLibV4, line 2163

                    logInfo "updateFromGitHub: Cleared cached profiles - they will be reloaded on next access" // library marker kkossev.deviceProfileLibV4, line 2165
                    */ // library marker kkossev.deviceProfileLibV4, line 2166

                    long endTime = now() // library marker kkossev.deviceProfileLibV4, line 2168
                    long totalDuration = endTime - startTime // library marker kkossev.deviceProfileLibV4, line 2169
                    logInfo "updateFromGitHub: Performance - Total: ${totalDuration}ms" // library marker kkossev.deviceProfileLibV4, line 2170
                } catch (Exception fileException) { // library marker kkossev.deviceProfileLibV4, line 2171
                    logWarn "updateFromGitHub: ? Error saving file: ${fileException.message}" // library marker kkossev.deviceProfileLibV4, line 2172
                    logDebug "updateFromGitHub: File save exception: ${fileException}" // library marker kkossev.deviceProfileLibV4, line 2173
                    sendInfoEvent "Error saving file: ${fileException.message}" // library marker kkossev.deviceProfileLibV4, line 2174
                    state.gitHubV4['lastDownloadError'] = "File save error" // library marker kkossev.deviceProfileLibV4, line 2175
                } // library marker kkossev.deviceProfileLibV4, line 2176
            } else { // library marker kkossev.deviceProfileLibV4, line 2177
                logWarn "updateFromGitHub: ? Failed to download from GitHub. HTTP status: ${resp?.status}" // library marker kkossev.deviceProfileLibV4, line 2178
                state.gitHubV4['lastDownloadError'] = "HTTP status ${resp?.status}" // library marker kkossev.deviceProfileLibV4, line 2179
                sendInfoEvent "Failed to download from GitHub. HTTP status: ${resp?.status}" // library marker kkossev.deviceProfileLibV4, line 2180
            } // library marker kkossev.deviceProfileLibV4, line 2181
        } // library marker kkossev.deviceProfileLibV4, line 2182

    } catch (Exception e) { // library marker kkossev.deviceProfileLibV4, line 2184
        if (state.gitHubV4 == null) { state.gitHubV4 = [:] } // library marker kkossev.deviceProfileLibV4, line 2185
        state.gitHubV4['catchedExceptionsCtr'] = (state.gitHubV4['catchedExceptionsCtr'] ?: 0) + 1 // library marker kkossev.deviceProfileLibV4, line 2186
        state.gitHubV4['lastException'] = e.message // library marker kkossev.deviceProfileLibV4, line 2187
        state.gitHubV4['lastExceptionTime'] = now() // library marker kkossev.deviceProfileLibV4, line 2188
        logWarn "updateFromGitHub: ? Error: ${e.message}" // library marker kkossev.deviceProfileLibV4, line 2189
        logDebug "updateFromGitHub: Full exception: ${e}" // library marker kkossev.deviceProfileLibV4, line 2190
        sendInfoEvent "updateFromGitHub: ? Error: ${e.message}" // library marker kkossev.deviceProfileLibV4, line 2191
    } // library marker kkossev.deviceProfileLibV4, line 2192
} // library marker kkossev.deviceProfileLibV4, line 2193



// ~~~~~ end include (197) kkossev.deviceProfileLibV4 ~~~~~

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
