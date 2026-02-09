/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NestedBlockDepth, NoDouble, NoFloat, NoWildcardImports, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLibV4', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/deviceProfileLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-deviceProfileLib',
    version: '4.1.0'
)
/*
 *  Device Profile Library V4
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ver. 1.0.0  2023-11-04 kkossev  - added deviceProfileLib (based on Tuya 4 In 1 driver)
 * ...................................
 * ver. 3.5.0  2025-08-14 kkossev  - zclWriteAttribute() support for forced destinationEndpoint in the attributes map
 * ver. 4.0.0  2025-09-03 kkossev  - deviceProfileV4 BRANCH created; deviceProfilesV2 support is dropped; 
 * ver. 4.0.1  2025-09-15 kkossev  - added debug commands to sendCommand(); 
 * ver. 4.0.2  2025-09-18 kkossev  - (deviceProfileV4 branch) cooldown timer is started on JSON local storage read or parsing error;
 * ver. 4.1.0  2025-10-12 kkossev  - (development branch) zclWriteAttribute delay default is 150ms if tuyaDelay not defined in the device profile;
 *
 *                                   TODO - updateStateUnknownDPs() from the earlier versions of 4 in 1 driver
 *
*/

static String deviceProfileLibVersion()   { '4.1.0' }
static String deviceProfileLibStamp() { '2025/10/18 06:04 PM' }
import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.clusters.iaszone.ZoneStatus
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

@Field static  Map g_deviceProfilesV4 = [:]
@Field static  boolean g_profilesLoading = false
@Field static  boolean g_profilesLoaded = false
@Field static  Map g_deviceFingerprintsV4 = [:]
@Field static  Map g_currentProfilesV4 = [:]            // Key: device?.deviceNetworkId, Value: complete profile data
@Field static  Map g_customProfilesV4 = [:]             // Key: device?.deviceNetworkId, Value: custom profiles for THIS device only
@Field static  boolean g_loadProfilesCooldown = false
@Field static  int LOAD_PROFILES_COOLDOWN_MS = 30000  // 30 seconds cooldown to prevent multiple profile loading within short time


metadata {
    // no capabilities
    attribute 'customJSON', 'string'        // Custom JSON profile filename (if loaded)
    /*
    // copy the following commands to the main driver, if needed
    command 'sendCommand', [
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']],
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']]
    ]
    command 'setPar', [
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']],
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']]
    ]
    */
    
    preferences {
        if (device) {
            // itterate over DEVICE.preferences map and inputIt all
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) {
                (DEVICE?.preferences).each { key, value ->
                    Map inputMap = inputIt(key)
                    if (inputMap != null && inputMap != [:]) {
                        input inputMap
                    }
                }
            }
            input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap())
            // Note: Custom JSON profiles are now managed via loadCustomProfiles() command, not preferences
        }
    }
    
}

private boolean is2in1() { return getDeviceProfile().startsWith('TS0601_2IN1')  }   // patch!

public String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' }
public Map     getDEVICE()              { 
    // Use V4 approach only. Backward compatibility with V3 is dropped
    if (this.hasProperty('g_currentProfilesV4')) {
        ensureCurrentProfileLoaded()
        return getCurrentDeviceProfile()
    } 
    return [:]
}

// ---- V4 Profile Management Methods ----

/**
 * Gets the current device's profile data from g_currentProfilesV4 map
 * Falls back to g_deviceProfilesV4 if g_currentProfilesV4 entry doesn't exist
 * @return Map containing the device profile data
 */
private Map getCurrentDeviceProfile() {
    if (!this.hasProperty('g_currentProfilesV4') || g_currentProfilesV4 == null) { 
        return [:]  // NO fallback to V3 method
    }
    
    String dni = device?.deviceNetworkId
    Map currentProfile = g_currentProfilesV4[dni]
    
    if (currentProfile != null && currentProfile != [:]) {
        return currentProfile
    } else {
        // Profile not loaded yet, use V3 fallback
        return [:]
    }
}

/**
 * Ensures the current device's profile is loaded in g_currentProfilesV4
 * Should be called before accessing device-specific profile data
 */
private void ensureCurrentProfileLoaded() {
    if (!this.hasProperty('g_currentProfilesV4')) { 
        return  // V4 not available, stick with V3
    }
    if (isInCooldown()) {
        logTrace "ensureCurrentProfileLoaded: in cooldown period, skipping profile load"
        return
    }
    
    String dni = device?.deviceNetworkId
    if (!g_currentProfilesV4 || !g_currentProfilesV4.containsKey(dni)) {
        populateCurrentProfile(dni)
    }
}

/**
 * Returns the appropriate profiles map for this device
 * Uses custom profiles if loaded for this device, otherwise standard profiles
 * @return Map of device profiles (either custom or standard)
 */
private Map getDeviceProfilesSource() {
    if (!this.hasProperty('g_customProfilesV4')) {
        return g_deviceProfilesV4  // Fallback if custom profiles not available
    }
    
    String dni = device?.deviceNetworkId
    
    // If this device has custom profiles loaded, use them
    if (g_customProfilesV4?.containsKey(dni) && g_customProfilesV4[dni] != null) {
        logDebug "getDeviceProfilesSource: using CUSTOM profiles for device ${dni}"
        return g_customProfilesV4[dni]
    }
    
    // Otherwise use standard shared profiles
    logTrace "getDeviceProfilesSource: using STANDARD profiles for device ${dni}"
    return g_deviceProfilesV4
}

/**
 * Loads custom profiles from a specific JSON file for a specific device
 * @param dni Device Network ID
 * @param filename Custom JSON filename
 * @return true if loaded successfully, false otherwise
 */
private boolean loadCustomProfilesForDevice(String dni, String filename) {
    logDebug "loadCustomProfilesForDevice: loading ${filename} for device ${dni}"
    
    try {
        // Read custom JSON file
        def data = readFile(filename)
        if (data == null) {
            logWarn "loadCustomProfilesForDevice: failed to read ${filename}"
            return false
        }
        
        // Parse JSON
        def jsonSlurper = new JsonSlurper()
        def parsed = jsonSlurper.parseText("${data}")
        
        def customProfiles = parsed?.deviceProfiles
        if (!(customProfiles instanceof Map) || customProfiles.isEmpty()) {
            logWarn "loadCustomProfilesForDevice: no deviceProfiles found in ${filename}"
            return false
        }
        
        // Store in g_customProfilesV4[dni]
        if (g_customProfilesV4 == null) { g_customProfilesV4 = [:] }
        g_customProfilesV4[dni] = customProfiles
        
        // Also store metadata in state
        if (state.profilesV4 == null) { state.profilesV4 = [:] }
        state.profilesV4['lastJSONSource'] = 'custom'
        state.profilesV4['customFilename'] = filename
        state.profilesV4['customVersion'] = parsed?.version ?: 'unknown'
        state.profilesV4['customTimestamp'] = parsed?.timestamp ?: 'unknown'
        state.profilesV4['customProfileCount'] = customProfiles.size()
        
        // Send deviceProfileFile attribute event
        sendEvent(name: 'deviceProfileFile', value: filename, descriptionText: "Custom profile loaded from ${filename}", type: 'digital')
        
        logInfo "loadCustomProfilesForDevice: loaded ${customProfiles.size()} custom profiles from ${filename} (version: ${parsed?.version}, timestamp: ${parsed?.timestamp})"
        return true
        
    } catch (Exception e) {
        logError "loadCustomProfilesForDevice: exception loading ${filename}: ${e.message}"
        return false
    }
}

/**
 * Populates g_currentProfilesV4 entry for the specified device
 * Extracts complete profile data from g_deviceProfilesV4 (excluding fingerprints)
 * @param dni Device Network ID to use as key
 */
private void populateCurrentProfile(String dni) {
    logDebug "populateCurrentProfile: populating profile for device ${dni}"
    if (!this.hasProperty('g_currentProfilesV4') || !this.hasProperty('g_deviceProfilesV4')) { 
        return
    }
    
    String profileName = getDeviceProfile()
    if (!profileName || profileName == 'UNKNOWN') {
        logWarn "populateCurrentProfile: cannot populate profile for ${dni} - profile name is ${profileName}"
        return
    }
    logDebug "ensuring profiles loaded for device ${dni}"
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() }
    
    Map profiles = getDeviceProfilesSource()  // Use custom or standard profiles
    String source = (this.hasProperty('g_customProfilesV4') && g_customProfilesV4?.containsKey(dni)) ? "CUSTOM" : "STANDARD"
    
    logDebug "populateCurrentProfile: using ${source} profiles source"
    logDebug "populateCurrentProfile: profiles map has ${profiles?.size() ?: 0} entries: ${profiles?.keySet()}"
    
    if (profiles == null || profiles.isEmpty()) {
        logWarn "populateCurrentProfile: cannot populate profile for ${dni} - profiles source is null or empty"
        return
    }
    
    Map sourceProfile = profiles[profileName]
    logDebug "populateCurrentProfile: looking for profile '${profileName}' in ${source} profiles"
    logDebug "populateCurrentProfile: sourceProfile is ${sourceProfile == null ? 'NULL' : 'FOUND with ' + sourceProfile.keySet().size() + ' keys'}"
    
    if (sourceProfile) {
        // Clone the profile data and remove fingerprints (already in g_deviceFingerprintsV4)
        Map profileData = sourceProfile.clone()
        logDebug "populateCurrentProfile: cloned profile has ${profileData?.keySet()?.size() ?: 0} keys: ${profileData?.keySet()}"
        profileData?.remove('fingerprints')
        if (g_currentProfilesV4 == null) { g_currentProfilesV4 = [:] }   // initialize if null
        g_currentProfilesV4[dni] = profileData
        logDebug "populateCurrentProfile: stored profile in g_currentProfilesV4[${dni}]"
        
        sendInfoEvent "Successfully loaded profile '${profileName}' from ${source} profiles for device ${dni}"
        
        // Clear any stale WARNING attribute since we successfully loaded the profile
        device.deleteCurrentState('WARNING')
    } else {
        logWarn "populateCurrentProfile: profile '${profileName}' not found in ${source} profiles for device ${dni}"
        logWarn "populateCurrentProfile: available profiles: ${profiles?.keySet()}"
    }
}

/**
 * Disposes of V3 profile data to free memory once all devices have their profiles loaded
 * Should only be called when it's safe to remove V3 data
 */
void disposeV3ProfilesIfReady() {
    if (!this.hasProperty('g_currentProfilesV4') || !this.hasProperty('g_deviceProfilesV4')) { 
        return
    }
    
    String dni = device?.deviceNetworkId
    if (g_currentProfilesV4?.containsKey(dni)) {
        // This device has its profile loaded, it's safe to dispose V3 for this device
        logDebug "disposeV3ProfilesIfReady: device ${dni} has current profile loaded - V3 can be disposed"
        // Note: In a production environment, you might want more sophisticated logic
        // to check if ALL active devices have their profiles loaded before disposing V3
    } else {
        logDebug "disposeV3ProfilesIfReady: device ${dni} does not have current profile loaded - keeping V3"
    }
}

/**
 * Forces disposal of V4 profiles to free memory
 * Use with caution - only when you're sure all needed profiles are in g_currentProfilesV4
 */
void forceDisposeV4Profiles() {
    if (!this.hasProperty('g_deviceProfilesV4')) { 
        return
    }
    
    int sizeBefore = g_deviceProfilesV4?.size() ?: 0
    g_deviceProfilesV4 = null
    if (this.hasProperty('g_profilesLoaded')) { g_profilesLoaded = false }
    logInfo "forceDisposeV3Profiles: disposed ${sizeBefore} V3 profiles to free memory"
}

public Set     getDeviceProfiles()      { g_deviceProfilesV4 != null ? g_deviceProfilesV4?.keySet() : [] }

// TODO - check why it returns list instead of set or map ??? TODO
public List<String> getDeviceProfilesMap()   {
    // if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() }
    // better don't ...
    Map profiles = getDeviceProfilesSource()  // Use custom or standard profiles
    if (profiles == null || profiles.isEmpty()) { return [] }
    List<String> activeProfiles = []
    profiles.each { profileName, profileMap ->
        if ((profileMap.device?.isDepricated ?: false) != true) {
            activeProfiles.add(profileMap.description ?: '---')
        }
    }
    return activeProfiles
}

// ---------------------------------- g_deviceProfilesV4 helper functions --------------------------------------------

/**
 * Returns the device fingerprints map
 * @return The g_deviceFingerprintsV4 map containing description and fingerprints for each profile
 */
public Map getDeviceFingerprintsV4() {
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() }
    return this.hasProperty('g_deviceFingerprintsV4') ? g_deviceFingerprintsV4 : [:]
}

/**
 * Returns the profile key for a given profile description.
 * @param valueStr The profile description to search for.
 * @return The profile key if found, otherwise null.
 */
public String getProfileKey(final String valueStr) {
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() }
    Map profiles = getDeviceProfilesSource()  // Use custom or standard profiles
    if (profiles != null) { return profiles.find { _, profileMap -> profileMap.description == valueStr }?.key }
    else { return null }
}

/**
 * Finds the preferences map for the given parameter.
 * @param param The parameter to find the preferences map for.
 * @param debug Whether or not to output debug logs.
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found
 * @return empty map [:] if param is not defined for this device.
 */
private Map getPreferencesMapByName(final String param, boolean debug=false) {
    Map foundMap = [:]
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] }
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def preference
    try {
        preference = DEVICE?.preferences["$param"]
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" }
        if (preference in [true, false]) {
            // find the preference in the tuyaDPs map
            logDebug "getPreferencesMapByName: preference ${param} is boolean"
            return [:]     // no maps for predefined preferences !
        }
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) {
            int dp = safeToInt(preference)
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})"
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp }
        }
        else { // cluster:attribute
            //if (debug) { log.trace "${DEVICE?.attributes}" }
            foundMap = DEVICE?.attributes.find { it.at == preference }
        }
    // TODO - could be also 'true' or 'false' ...
    } catch (e) {
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" }
        return [:]
    }
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" }
    return foundMap
}

public Map getAttributesMap(String attribName, boolean debug=false) {
    Map foundMap = [:]
    List<Map> searchMapList = []
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" }
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) {
        searchMapList =  DEVICE?.tuyaDPs
        foundMap = searchMapList.find { it.name == attribName }
        if (foundMap != null) {
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" }
            return foundMap
        }
    }
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" }
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) {
        searchMapList  =  DEVICE?.attributes
        foundMap = searchMapList.find { it.name == attribName }
        if (foundMap != null) {
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" }
            return foundMap
        }
    }
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" }
    return [:]
}

/**
 * Resets the device preferences to their default values.
 * @param debug A boolean indicating whether to output debug information.
 */
public void resetPreferencesToDefaults(boolean debug=false) {
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}"
    if (DEVICE == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'Preferences not found!' ; return }
    Map preferences = DEVICE?.preferences ?: [:]
    if (preferences == null || preferences == [:]) { logDebug 'Preferences not found!' ; return }
    Map parMap = [:]
    preferences.each { parName, mapValue ->
        //if (debug) { log.trace "$parName $mapValue" }
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) {
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here?
            return // continue
        }
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map
        if (parMap == null || parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'],
        if (parMap?.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" }
        String str = parMap.name
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type])
    }
    logInfo 'Preferences reset to default values'
}

/**
 * Returns a list of valid parameters per model based on the device preferences.
 *
 * @return List of valid parameters.
 */
private List<String> getValidParsPerModel() {
    List<String> validPars = []
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) {
        // use the preferences to validate the parameters
        validPars = DEVICE?.preferences.keySet().toList()
    }
    return validPars
}

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */
private def getScaledPreferenceValue(String preference, Map dpMap) {        // TODO - not used ???
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def value = settings."${preference}"
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
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
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}"
            if (dpMap.map == null) {
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!"
                return null
            }
            scaledValue = value
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) {
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale))
            }
            break
        default :
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!"
            return null
    }
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})"
    return scaledValue
}

// called from customUpdated() method in the custom driver
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!!
public void updateAllPreferences() {
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}"
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) {
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}"
        return
    }
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def preferenceValue    // int or String for enums
    // itterate over the preferences map and update the device settings
    (DEVICE?.preferences).each { name, dp ->
        Map foundMap = getPreferencesMapByName(name, false)
        logDebug "updateAllPreferences: foundMap = ${foundMap}"
        if (foundMap != null && foundMap != [:]) {
            // preferenceValue = getScaledPreferenceValue(name, foundMap)
            preferenceValue = settings."${name}"
            logTrace"preferenceValue = ${preferenceValue}"
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) {
                // scale the value
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double
            }
            if (preferenceValue != null) {
                setPar(name, preferenceValue.toString())
            }
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return }
        }
        else { logDebug "warning: couldn't find map for preference ${name}" ; return }  // TODO - supress the warning if the preference was boolean true/false
    }
    return
}

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */
def divideBy100(int val) { return (val as int) / 100 }
int multiplyBy100(int val) { return (val as int) * 100 }
int divideBy10(int val) {
    if (val > 10) { return (val as int) / 10 }
    else { return (val as int) }
}
int multiplyBy10(int val) { return (val as int) * 10 }
int divideBy1(int val) { return (val as int) / 1 }    //tests
int signedInt(int val) {
    if (val > 127) { return (val as int) - 256 }
    else { return (val as int) }
}
int invert(int val) {
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 }
    else { return val }
}

// called from setPar and sendAttribite methods for non-Tuya DPs
private List<String> zclWriteAttribute(Map attributesMap, int scaledValue) {
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] }
    List<String> cmds = []
    Map map = [:]
    // cluster:attribute
    try {
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null
        map['ep'] = (attributesMap.ep != null && attributesMap.ep != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.ep) as Integer : null
    }
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] }
    // dt (data type) is obligatory when writing to a cluster...
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) {
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}"
    }
    int tuyaDelay = DEVICE?.device?.tuyaDelay as Integer ?: 150    
    if ((map.mfgCode != null && map.mfgCode != '') || (map.ep != null && map.ep != '')) {
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:]
        Map ep = map.ep != null ? ['destEndpoint':map.ep] : [:]
        Map mapOptions = [:]
        if (mfgCode) mapOptions.putAll(mfgCode)
        if (ep) mapOptions.putAll(ep)
        //log.trace "$mapOptions"
        // Get delay from device profile or use default
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mapOptions, delay = tuyaDelay)
    }
    else {
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = tuyaDelay)
    }
    return cmds
}

/**
 * Called from setPar() method only!
 * Validates the parameter value based on the given dpMap type and scales it if needed.
 *
 * @param dpMap The map containing the parameter type, minimum and maximum values.
 * @param val The value to be validated and scaled.
 * @return The validated and scaled value if it is within the specified range, null otherwise.
 */
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */
private def validateAndScaleParameterValue(Map dpMap, String val) {
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def value              // validated value - integer, floar
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def scaledValue        //
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}"
    switch (dpMap.type) {
        case 'number' :
            // TODO - negative values !
            // TODO - better conversion to integer!
            value = safeToInt(val, 0)
            //scaledValue = value
            // scale the value - added 10/26/2023 also for integer values !
            if (dpMap.scale != null) {
                scaledValue = (value * dpMap.scale) as Integer
            }
            else {
                scaledValue = value
            }
            break

        case 'decimal' :
            value = safeToDouble(val, 0.0)
            // scale the value
            if (dpMap.scale != null) {
                scaledValue = (value * dpMap.scale) as Integer
            }
            else {
                scaledValue = value
            }
            break

        case 'bool' :
            if (val == '0' || val == 'false')     { value = scaledValue = 0 }
            else if (val == '1' || val == 'true') { value = scaledValue = 1 }
            else {
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>"
                return null
            }
            break
        case 'enum' :
            // enums are always integer values
            // check if the scaling is different than 1 in dpMap
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}"
            Integer scale = safeToInt(dpMap.scale)
            if (scale != null && scale != 0 && scale != 1) {
                // we have a float parameter input - convert it to int
                value = safeToDouble(val, -1.0)
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer
            }
            else {
                value = scaledValue = safeToInt(val, -1)
            }
            if (scaledValue == null || scaledValue < 0) {
                // get the keys of dpMap.map as a List
                //List<String> keys = dpMap.map.keySet().toList()
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>"
                // find the key for the value
                String key = dpMap.map.find { it.value == val }?.key
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}"
                if (key == null) {
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>"
                    return null
                }
                value = scaledValue = key as Integer
            //return null
            }
            break
        default :
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>"
            return null
    }
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}"
    // check if the value is within the specified range
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) {
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}"
        return null
    }
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}"
    return scaledValue
}

/**
 * Sets the value of a parameter for a device.
 *
 * @param par The parameter name.
 * @param val The parameter value.
 * @return true if the parameter was successfully set, false otherwise.
 */
public boolean setPar(final String parPar=null, final String val=null ) {
    List<String> cmds = []
    //Boolean validated = false
    logDebug "setPar(${parPar}, ${val})"
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false }
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false }
    String par = parPar.trim()
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false }
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false }
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed
    if (scaledValue == null) {
        log.trace "$dpMap  ${dpMap.map}"
        String helpTxt = "setPar: invalid parameter ${par} value <b>${val}</b>."
        if (dpMap.min != null && dpMap.max != null) { helpTxt += " Must be in the range ${dpMap.min} to ${dpMap.max}" }
        if (dpMap.map != null) { helpTxt += " Must be one of ${dpMap.map}" }
        logInfo helpTxt
        return false
    }

    // if there is a dedicated set function, use it
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1]
    String customSetFunction = "customSet${capitalizedFirstChar}"
    if (this.respondsTo(customSetFunction)) {
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})"
        // execute the customSetFunction
        try { cmds = "$customSetFunction"(scaledValue) }
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false }
        logDebug "customSetFunction result is ${cmds}"
        if (cmds != null && cmds != []) {
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)"
            sendZigbeeCommands( cmds )
            return true
        }
        else {
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list"
        // continue with the default processing
        }
    }
    if (isVirtual()) {
        // set a virtual attribute
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */
        def valMiscType
        logDebug "setPar: found virtual attribute ${par} value ${val}"
        if (dpMap.type == 'enum') {
            // find the key for the value
            String key = dpMap.map.find { it.value == val }?.key
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}"
            if (key == null) {
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>"
                return false
            }
            valMiscType = dpMap.map[key as String]
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}"
            device.updateSetting("$par", [value:key as String, type:dpMap.type])
        }
        else {
            valMiscType = val
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type])
        }
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]"
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true)
        logInfo descriptionText
        return true
    }

    // check whether this is a tuya DP or a cluster:attribute parameter
    boolean isTuyaDP

    /* groovylint-disable-next-line Instanceof */
    try { isTuyaDP = dpMap.dp instanceof Number }
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false }
    if (dpMap.dp != null && isTuyaDP) {
        // Tuya DP
        cmds = sendTuyaParameter(dpMap,  par, scaledValue)
        if (cmds == null || cmds == []) {
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list"
            return false
        }
        else {
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))"
            sendZigbeeCommands(cmds)
            return false
        }
    }
    else if (dpMap.at != null) {
        // cluster:attribute
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})"
        int signedIntScaled = convertSignedInts(scaledValue, dpMap)
        cmds = zclWriteAttribute(dpMap, signedIntScaled)
        if (cmds == null || cmds == []) {
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}"
            return false
        }
    }
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false }
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)"
    sendZigbeeCommands( cmds )
    return true
}

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap
// TODO - reuse it !!!
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
public List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) {
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}"
    List<String> cmds = []
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] }
    String dp = zigbee.convertToHexString(dpMap.dp, 2)
    if (dpMap.dp <= 0 || dpMap.dp >= 256) {
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>"
        return []
    }
    String dpType
    if (dpMap.dt == null) {
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null
    }
    else {
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value
    }
    if (dpType == null) {
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>"
        return []
    }
    // sendTuyaCommand
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2)
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} "
    if (dpMap.tuyaCmd != null ) {
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int)
    }
    else {
        cmds = sendTuyaCommand( dp, dpType, dpValHex)
    }
    return cmds
}

private int convertSignedInts(int val, Map dpMap) {
    if (dpMap.dt == '0x28') {
        if (val > 127) { return (val as int) - 256 }
        else { return (val as int) }
    }
    else if (dpMap.dt == '0x29') {
        if (val > 32767) { return (val as int) - 65536 }
        else { return (val as int) }
    }
    else { return (val as int) }
}

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
public boolean sendAttribute(String par=null, val=null ) {
    List<String> cmds = []
    //Boolean validated = false
    logDebug "sendAttribute(${par}, ${val})"
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'DEVICE.preferences is empty!' ; return false }

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute
    l//log.trace "sendAttribute: dpMap=${dpMap}"
    if (dpMap == null || dpMap?.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false }
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false }
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false }
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}"
    // if there is a dedicated set function, use it
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1]
    String customSetFunction = "customSet${capitalizedFirstChar}"
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) {
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})"
        // execute the customSetFunction
        try {
            cmds = "$customSetFunction"(scaledValue)
        }
        catch (e) {
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))"
            return false
        }
        logDebug "customSetFunction result is ${cmds}"
        if (cmds != null && cmds != []) {
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)"
            sendZigbeeCommands( cmds )
            return true
        }
        else {
            logDebug "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing"
        // continue with the default processing
        }
    }
    else {
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing"
    }
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device
    if (isVirtual()) {
        // send a virtual attribute
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}"
        // patch !!
        if (par == 'heatingSetpoint') {
            sendHeatingSetpointEvent(val)
        }
        else {
            String descriptionText = "${par} is ${val} [virtual]"
            sendEvent(name:par, value:val, isDigital: true)
            logInfo descriptionText
        }
        return true
    }
    else {
        logTrace "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue "
    }
    boolean isTuyaDP
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def preference = dpMap.dp   // TODO - remove it?
    try {
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number
    }
    catch (e) {
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" }
        return false
    }
    if (dpMap.dp != null && isTuyaDP) {
        // Tuya DP
        cmds = sendTuyaParameter(dpMap,  par, scaledValue)
        if (cmds == null || cmds == []) {
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list"
            return false
        }
        else {
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))"
            sendZigbeeCommands( cmds )
            return true
        }
    }
    /* groovylint-disable-next-line EmptyIfStatement */
    else if (dpMap.at != null && dpMap.at == 'virtual') {
    // send a virtual attribute
    }
    else if (dpMap.at != null) {
        // cluster:attribute
        cmds = zclWriteAttribute(dpMap, scaledValue)
        if (cmds == null || cmds == []) {
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}"
            return false
        }
    }
    else {
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>"
        return false
    }
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)"
    sendZigbeeCommands( cmds )
    return true
}

@Field static Map debugCommandsMap = ['printFingerprintsV4':'', 'printPreferences':'', 'resetStats':'', 'updateAllPreferences': '', 'resetPreferencesToDefaults': '', 'initialize': '', 'validateAndFixPreferences': '', 'profilesV4info': '', 'clearProfilesCache': 'clearProfilesCacheInfo']

/**
 * SENDS a list of Zigbee commands to be sent to the device.
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map.
 * @param val     - The value to send with the command, can be null.
 * @return true on success, false otherwise.
 */
public boolean sendCommand(final String command_orig=null, final String val_orig=null) {
    //logDebug "sending command ${command}(${val}))"
    final String command = command_orig?.trim()
    final String val = val_orig?.trim()
    List<String> cmds = []
    
    // Hidden command to enable/disable debug commands dynamically
    if (command == '_DEBUG') {
        if (val in ['true', '1']) { _DEBUG = true; logInfo "Debug commands ENABLED via _DEBUG command"; return true }
        else if (val in ['false', '0']) { _DEBUG = false; logInfo "Debug commands DISABLED via _DEBUG command"; return true }
        else { logWarn "_DEBUG command requires value: true/false or 1/0"; return false }
    }
    Map supportedCommandsMap = DEVICE?.commands as Map ?: [:]
    
    if (_DEBUG || settings.logEnable) { supportedCommandsMap += debugCommandsMap }
    if (supportedCommandsMap?.isEmpty()) { logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !"; return false }

    // Create supportedCommandsList based on the same condition
    List supportedCommandsList
    if (_DEBUG || settings.logEnable) {
        supportedCommandsList = supportedCommandsMap.keySet() as List
    } else {
        supportedCommandsList = DEVICE?.commands?.keySet() as List ?: []
    }
    
    // check if the command is defined in the supported commands
    if (command == null || !(command in supportedCommandsList)) {
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}"
        return false
    }
    
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def func, funcResult
    try {
        // Search in the merged supportedCommandsMap (includes debug commands when enabled)
        func = supportedCommandsMap.find { it.key == command }.value
        // added 01/25/2025 : the commands now can be shortened
        if (func == null || func == '') {
            func = command
        }
        if (val != null && val != '') {
            logInfo "executed <b>$func</b>($val)"
            funcResult = "${func}"(val)
        }
        else {
            logInfo "executed <b>$func</b>()"
            funcResult = "${func}"()
        }
    }
    catch (e) {
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})"
        return false
    }
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null
    // check if the result is a list of commands
    /* groovylint-disable-next-line Instanceof */
    if (funcResult instanceof List) {
        cmds = funcResult
        if (cmds != null && cmds != []) {
            sendZigbeeCommands( cmds )
        }
    }
    else if (funcResult == null) {
        return false
    }
     else {
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!"
        return false
    }
    return true
}

/**
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details.
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not.
 * It then checks if the input parameter is a boolean value and skips it if it is.
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly.
 * If the input parameter is read-only, the method returns null.
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter.
 * If the input parameter type is not supported, the method returns null.
 * @param param The input parameter to be checked.
 * @param debug A boolean flag indicating whether to log debug messages or not.
 * @return A map containing the input details.
 */
public Map inputIt(String paramPar, boolean debug = false) {
    String param = paramPar.trim()
    Map input = [:]
    Map foundMap = [:]
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] }
    Object preference
    try { preference = DEVICE?.preferences["$param"] }
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] }
    //  check for boolean values
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } }
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] }
    /*
    // TODO - check if this is neccessary? isTuyaDP is not defined!
    try { isTuyaDP = preference.isNumber() }
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  }
    */
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}"
    foundMap = getPreferencesMapByName(param)
    //if (debug) log.debug "foundMap = ${foundMap}"
    if (foundMap == null || foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  }
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  }
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) {
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" }
        return [:]
    }
    input.name = foundMap.name
    input.type = foundMap.type    // bool, enum, number, decimal
    input.title = foundMap.title
    //input.description = (foundMap.description ?: foundMap.title)?.replaceAll(/<\/?b>/, '')  // if description is not defined, use the title
    input.description = foundMap.description ?: ''   // if description is not defined, skip it
    if (input.type in ['number', 'decimal']) {
        if (foundMap.min != null && foundMap.max != null) {
            //input.range = "${foundMap.min}..${foundMap.max}"
            input.range = "${Math.floor(foundMap.min) as int}..${Math.ceil(foundMap.max) as int}"
        }
        if (input.range != null && input.description != null) {
            if (input.description != '') { input.description += '<br>' }
            input.description += "<i>Range: ${input.range}</i>"
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
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" }
        return [:]
    }
    if (input.defVal != null) {
        input.defVal = foundMap.defVal
    }
    return input
}

/**
 * Returns the device name and profile based on the device model and manufacturer.
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value.
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value.
 * @return A list containing the device name and profile.
 */
public List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) {
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() }
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN
    Map profiles = getDeviceProfilesSource()  // Use custom or standard profiles
    logDebug "getDeviceNameAndProfile: model=${deviceModel} manufacturer=${deviceManufacturer} profiles=${profiles != null}"
    
    if (profiles != null && !profiles.isEmpty()) {
        profiles.each { profileName, profileMap ->
            //log.trace "getDeviceNameAndProfile: checking profileName=${profileName} profileMap=${profileMap}"
            profileMap.fingerprints.each { fingerprint ->
                //log.trace "getDeviceNameAndProfile: checking fingerprint=${fingerprint}"
                if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) {
                    deviceProfile = profileName
                    deviceName = fingerprint.deviceJoinName ?: profiles[deviceProfile].description ?: UNKNOWN
                    logDebug "getDeviceNameAndProfile: <b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}"
                    return [deviceName, deviceProfile]
                }
            }
        }
    }
    if (deviceProfile == UNKNOWN) {
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}"
    }
    return [deviceName, deviceProfile]
}

// called from  initializeVars( fullInit = true)
public void setDeviceNameAndProfile(String model=null, String manufacturer=null) {
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() }
    
    // Store previous profile for change detection
    String previousProfile = getDeviceProfile()
    logDebug "setDeviceNameAndProfile: calling getDeviceNameAndProfile(${model}, ${manufacturer}) previousProfile = ${previousProfile}"
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer)
    logDebug "setDeviceNameAndProfile: returned deviceName=${deviceName} deviceProfile=${deviceProfile} previousProfile = ${previousProfile}"
    if (deviceProfile == null || deviceProfile == UNKNOWN) {
        logInfo "setDeviceNameAndProfile: unknown model ${deviceModel} manufacturer ${deviceManufacturer} previousProfile = ${previousProfile} -> setting state.deviceProfile = UNKNOWN"
        // don't change the device name when unknown
        state.deviceProfile = UNKNOWN
    }
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN
    logDebug "setDeviceNameAndProfile: deviceName=${deviceName} model=${dataValueModel} manufacturer=${dataValueManufacturer} previousProfile = ${previousProfile}"
    if (deviceName != NULL && deviceName != UNKNOWN) {
        device.setName(deviceName)
        state.deviceProfile = deviceProfile
        device.updateSetting('forcedProfile', [value:g_deviceProfilesV4[deviceProfile]?.description, type:'enum'])
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>"
        
        // Clear any stale WARNING attribute since we successfully set the profile
        device.deleteCurrentState('WARNING')
        
        // V4 Profile Management: Handle profile loading and changes
        if (this.hasProperty('g_currentProfilesV4')) {
            String dni = device?.deviceNetworkId
            
            // Detect profile change
            if (previousProfile != deviceProfile && previousProfile != 'UNKNOWN') {
                logInfo "Profile changed from '${previousProfile}' to '${deviceProfile}' - clearing old profile data for device ${dni}"
                g_currentProfilesV4.remove(dni)
            }
            
            // Ensure current profile is loaded
            ensureCurrentProfileLoaded()
        }
    } else {
        logInfo "setDeviceNameAndProfile: deviceName=${deviceName} : device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!"
    }
}

public List<String> refreshFromConfigureReadList(List<String> refreshList) {
    logDebug "refreshFromConfigureReadList(${refreshList})"
    List<String> cmds = []
    if (refreshList != null && !refreshList.isEmpty()) {
        //List<String> refreshList = DEVICE.refresh
        for (String k : refreshList) {
            k = k.replaceAll('\\[|\\]', '')
            if (k != null) {
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list
                Map map = DEVICE.attributes.find { it.name == k }
                if (map != null) {
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:]
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100)
                }
                // check whether the string in the refreshList matches a method defined somewhere in the code
                if (this.respondsTo(k)) {
                    cmds += this."${k}"()
                }
            }
        }
    }
    return cmds
}

// called from customRefresh() in the device drivers
public List<String> refreshFromDeviceProfileList() {
    logDebug 'refreshFromDeviceProfileList()'
    List<String> cmds = []
    if (DEVICE?.refresh != null) {
        List<String> refreshList = DEVICE.refresh
        for (String k : refreshList) {
            k = k.replaceAll('\\[|\\]', '')
            if (k != null) {
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list
                Map map = DEVICE.attributes.find { it.name == k }
                if (map != null) {
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:]
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100)
                }
                // check whether the string in the refreshList matches a method defined somewhere in the code
                if (this.respondsTo(k)) {
                    cmds += this."${k}"()
                }
            }
        }
    }
    return cmds
}

// TODO! - remove?
List<String> refreshDeviceProfile() {
    List<String> cmds = []
    if (cmds == []) { cmds = ['delay 299'] }
    logDebug "refreshDeviceProfile() : ${cmds}"
    return cmds
}

// TODO ! - remove?
List<String> configureDeviceProfile() {
    List<String> cmds = []
    logDebug "configureDeviceProfile() : ${cmds}"
    if (cmds == []) { cmds = ['delay 299'] }
    return cmds
}

// TODO! - remove?
List<String> initializeDeviceProfile() {
    List<String> cmds = []
    logDebug "initializeDeviceProfile() : ${cmds}"
    if (cmds == []) { cmds = ['delay 299',] }
    return cmds
}

/**
 * Clears the customJSON attribute if custom profile is not being used
 * Should be called when reverting to standard profiles
 */
private void clearCustomJSONAttribute() {
    String dni = device?.deviceNetworkId
    // Only clear if this device doesn't have custom profiles loaded
    if (!g_customProfilesV4?.containsKey(dni) || g_customProfilesV4[dni] == null) {
        device.deleteCurrentState('customJSON')
        logDebug "clearCustomJSONAttribute: cleared customJSON attribute for device ${dni}"
    }
}

public void deviceProfileInitializeVars(boolean fullInit=false) {
    logDebug "deviceProfileInitializeVars(${fullInit})"
    // Eager loading during initialization
    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() }
    if (state.deviceProfile == null) {
        setDeviceNameAndProfile()
    }
}

public void initEventsDeviceProfile(boolean fullInit=false) {
    String ps = DEVICE?.device?.powerSource
    logDebug "initEventsDeviceProfile(${fullInit}) for deviceProfile=${state.deviceProfile} DEVICE?.device?.powerSource=${ps} ps.isEmpty()=${ps?.isEmpty()}"
    if (ps != null && !ps.isEmpty()) {
        sendEvent(name: 'powerSource', value: ps, descriptionText: "Power Source set to '${ps}'", type: 'digital')
    }
}

///////////////////////////// Tuya DPs /////////////////////////////////

//
// called from parse()
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profile
//          false - the processing can continue
//
public boolean isSpammyDPsToIgnore(Map descMap) {
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}"
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true }
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false }
    if (descMap?.data?.size <= 2) { return false }
    int dp =  zigbee.convertHexToInt(descMap.data[2])
    Map currentProfile = getCurrentDeviceProfile()
    List spammyList = currentProfile?.spammyDPsToIgnore as List
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true))
}

//
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport()
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profile
//          false - debug logs can be generated
//
public boolean isSpammyDPsToNotTrace(Map descMap) {
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}"
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true }
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false }
    if (descMap?.data?.size <= 2) { return false }
    int dp = zigbee.convertHexToInt(descMap.data[2])
    Map currentProfile = getCurrentDeviceProfile()
    List spammyList = currentProfile?.spammyDPsToNotTrace as List
    return (spammyList != null && (dp in spammyList))
}

// all DPs are spammy - sent periodically! (this function is not used?)
public boolean isSpammyDeviceProfile() {
    Map currentProfile = getCurrentDeviceProfile()
    if (!currentProfile) { return false }
    Boolean isSpammy = currentProfile?.device?.isSpammy ?: false
    return isSpammy
}

/* groovylint-disable-next-line UnusedMethodParameter */
private List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) {
    //logTrace "compareAndConvertStrings: tuyaValue='${tuyaValue}' hubitatValue='${hubitatValue}'"
    String convertedValue = tuyaValue ?: ""
    boolean isEqual
    if (hubitatValue == null || tuyaValue == null) {
        // Per requirement: any null hubitatValue forces inequality, regardless of tuyaValue
        isEqual = false
    } else {
        // Safe comparison (may yield true only if hubitatValue non-null and matches tuyaValue)
        isEqual = ((tuyaValue as String) == (hubitatValue as String))
    }
    if (foundItem?.scale != null && !(foundItem.scale in [0, 1])) {
        logTrace "compareAndConvertStrings: scale=${foundItem.scale} tuyaValue=${tuyaValue} convertedValue=${convertedValue} hubitatValue=${hubitatValue} isEqual=${isEqual}"
    }
    return [isEqual, convertedValue]
}

private List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) {
    Integer convertedValue
    boolean isEqual
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer
        convertedValue = tuyaValue as int
    }
    else {
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int
    }
    isEqual = ((convertedValue as int) == (hubitatValue as int))
    return [isEqual, convertedValue]
}

private List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) {
    Double convertedValue
    boolean isEqual
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {
        convertedValue = tuyaValue as double
    }
    else {
        convertedValue = (tuyaValue as double) / (foundItem.scale as double)
    }
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}"
    return [isEqual, convertedValue]
}

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
private List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) {
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}"
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def convertedValue
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {
        convertedValue = tuyaValue as int
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue)))
    }
    else {  // scaled value - divide by scale
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0)
        convertedValue = (tuyaValue as double) / (foundItem.scale as double)
        if (hubitatSafeValue == -1.0) {
            isEqual = false
        }
        else { // compare as double (float)
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001
        }
    }
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}"
    return [isEqual, convertedValue]
}

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
private List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) {
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] }
    if (foundItem?.type == null) { return [true, 'none'] }
    boolean isEqual
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def tuyaValueScaled     // could be integer or float
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def preferenceValue = settings[foundItem.name]
    switch (foundItem.type) {
        case 'bool' :       // [0:"OFF", 1:"ON"]
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference))
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}"
            break
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters']
            Integer scale = (foundItem.scale ?: 0 ) as int
            if (scale != null && scale != 0 && scale != 1) {
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '')
                /* groovylint-disable-next-line ParameterReassignment */
                preference = preference.toString().replace('[', '').replace(']', '')
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}"
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference))
            }
            else {
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference))
            }
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}"
            break
        case 'value' :      // depends on foundItem.scale
        case 'number' :
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference))
            logTrace "tuyaValue=${fncmd} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}"
            break
       case 'decimal' :
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference))
            logTrace "comparing as float tuyaValue=${fncmd} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}"
            break
        default :
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}'
            return [true, 'none']   // fallback - assume equal
    }
    if (isEqual == false) {
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}"
    }
    //
    return [isEqual, tuyaValueScaled]
}

//
// called from process TuyaDP from DeviceProfile()
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
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */
private List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd_orig, boolean doNotTrace=false) {
    if (foundItem == null) { return [true, 'none'] }
    if (foundItem.type == null) { return [true, 'none'] }
    int fncmd = fncmd_orig ?: 0
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def hubitatEventValue   // could be integer or float or string
    boolean isEqual = false
    switch (foundItem.type) {
        case 'bool' :       // [0:"OFF", 1:"ON"]
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as String] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown')
            break
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters]
            //logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}"
            Object latestEvent = device.currentState(foundItem.name)
            String dataType = latestEvent?.dataType
            //logTrace "latestEvent is ${latestEvent} dataType is ${dataType}"
            // if the attribute is of a type enum, the value is a string. Compare the string values!
            if (dataType == null || dataType == 'ENUM') {
                //logTrace "compareAndConvertTuyaToHubitatEventValue: comparing as string fncmd=${fncmd} foundItem.name=${foundItem.name} foundItem.map=${foundItem.map}"
                String foundItemMapValue = foundItem.map[fncmd as String] ?: 'unknown'      // map indexes are of a type String in g_deviceProfilesV4 !!!
                //String foundItemMapValue = 'unknown'
                //logTrace "foundItem.map[fncmd as String] = ${foundItemMapValue}"
                //logTrace "device.currentValue(${foundItem.name}) = ${device.currentValue(foundItem.name) ?: 'unknown'}"
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItemMapValue, device.currentValue(foundItem.name) ?: 'unknown')
                //logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}"
            }
            else {
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name))
            }
            //logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}"
            break
        case 'value' :      // depends on foundItem.scale
        case 'number' :
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}"
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

public Integer preProc(final Map foundItem, int fncmd_orig) {
    Integer fncmd = fncmd_orig
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
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd_orig})"
        return fncmd_orig
    }
    //logDebug "setFunction result is ${fncmd}"
    return fncmd
}

// TODO: refactor!
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...)
// returns true if the DP was processed successfully, false otherwise.
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) {
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}"
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false }
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false }

    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() }
    Map currentProfile = getCurrentDeviceProfile()
    List<Map> attribMap = currentProfile?.attributes
    if (attribMap == null || attribMap?.isEmpty()) { return false }    // no any attributes are defined in the Device Profile

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}"
    int value
    try {
        value = hexStrToUnsignedInt(descMap.value)
    }
    catch (e) {
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer"
        return false
    }
    Map foundItem = attribMap.find { it['at'] == clusterAttribute }
    if (foundItem == null || foundItem == [:]) {
        // clusterAttribute was not found in the attributes list for this particular deviceProfile
        // updateStateUnknownclusterAttribute(descMap)
        // continue processing the descMap report in the old code ...
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}"
        return false
    }
    value = convertSignedInts(value, foundItem)
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap))
}

/**
 * Called from standardProcessTuyaDP method in commonLib
 *
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs.
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute.
 * If no preference exists for the DP, it logs the DP value as an info message.
 * If the DP is spammy (not needed for anything), it does not perform any further processing.
 *
 * @return true if the DP was processed successfully, false otherwise.
 */
/* groovylint-disable-next-line UnusedMethodParameter */
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) {
    logTrace "processTuyaDPfromDeviceProfile: descMap = ${descMap}, dp = ${dp}, dp_id = ${dp_id}, fncmd_orig = ${fncmd_orig}, dp_len = ${dp_len}"
    int fncmd = fncmd_orig
    if (state.deviceProfile == null)  { return false }
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status)
    if (isInCooldown()) { logDebug "processTuyaDPfromDeviceProfile: in cooldown period, skipping DP processing"; return true }               // do not perform any further processing, if we are in the cooldown period

    if (this.respondsTo('ensureProfilesLoaded')) { ensureProfilesLoaded() }
    Map currentProfile = getCurrentDeviceProfile()
    List<Map> tuyaDPsMap = currentProfile?.tuyaDPs
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) }
    if (foundItem == null || foundItem == [:]) {
        // DP was not found into the tuyaDPs list for this particular deviceProfile
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // continue processing the DP report in the old code ...
        return false
    }
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap))
}

/*
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile
 */
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) {
    if (foundItem == null) { return false }
    // added 10/31/2023 - preProc the attribute value if needed
    if (foundItem.preProc != null) {
        /* groovylint-disable-next-line ParameterReassignment */
        Integer preProcValue = preProc(foundItem, value)
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true }
        if (preProcValue != value) {
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}"
            /* groovylint-disable-next-line ParameterReassignment */
            value = preProcValue as int
        }
    }
    //else { logTrace "processFoundItem: no preProc for ${foundItem.name}" }

    String name = foundItem.name                                   // preference name as in the attributes map
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def preferenceValue = null   // preference value
    //log.trace "settings=${settings}"
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences?.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute
    //log.trace "preferenceExists=${preferenceExists}"
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute
    boolean isEqual = false
    boolean wasChanged = false
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" }
    // check if the clusterAttribute has the same value as the last one, or the value has changed
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ...
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : ''
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def valueScaled    // can be number or decimal or string
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare !
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..."
            // TODO - scaledValue ????? TODO!
            descText  = "${name} is ${value} ${unitText}"
            if (settings.logEnable) { logInfo "${descText} (Debug logging is enabled)" }  // only when Debug is enabled!
        }
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute
    }

    // first, check if there is a preference defined in the deviceProfileV3 to be updated
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024
        // preference exists and its's value is extracted
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue)
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>"
        if (isEqual == true) {              // the preference is not changed - do nothing
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}"
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})"
            }
        }
        else {      // the preferences has changed - update it!
            String scaledPreferenceValue = preferenceValue
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) {
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString()
            }
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})"
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" }
            try {
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type])
                wasChanged = true
            }
            catch (e) {
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}"
            }
        }
    }
    else {    // no preference exists for this clusterAttribute
        // if not in the spammy list - log it!
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block
        //logInfo "${name} is ${value} ${unitText}"
    }

    // second, send an event if this is declared as an attribute!
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace)
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" }
        descText  = "${name} is ${valueScaled} ${unitText}"
        if (settings?.logEnable == true) { descText += " (raw:${value})" }
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' }
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along!
            if (!doNotTrace) {
                if (settings.logEnable) { logDebug "${descText } (no change)" }
            }
            if (foundItem.processDuplicated == true) {
                logDebug 'processDuplicated=true -> continue'
            }

            // patch for inverted motion sensor 2-in-1
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !!
                logDebug 'patch for inverted motion sensor 2-in-1'
            // continue ...
            }

            else {
                if (state.states != null && state.states['isRefresh'] == true) {
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...'
                }
                else {
                    //log.trace "should not be here !!!!!!!!!!"
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value)
                }
            }
        }

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event!
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1
        float valueCorrected = value / divider
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" }
        // process the events in the device specific driver..
        if (this.respondsTo('customProcessDeviceProfileEvent')) {
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV
        }
        else {
            // no custom handler - send the event as usual
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event !
            if (!doNotTrace) {
                logTrace "event ${name} sent w/ valueScaled ${valueScaled}"
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ?
            }
        }
    }
    return true     // all processing was done here!
}

// not used ? (except for debugging)? TODO
public boolean validateAndFixPreferences(String debugStr) { return validateAndFixPreferences(debugStr.toBoolean() as boolean) }
public boolean validateAndFixPreferences(boolean debug=false) {
    //debug = true
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" }
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false }
    int validationFailures = 0, validationFixes = 0, total = 0
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def oldSettingValue, newValue
    String settingType = ''
    DEVICE?.preferences.each {
        Map foundMap = getPreferencesMapByName(it.key)
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false }
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key)
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false }
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" }
        if (foundMap.type != settingType) {
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) "
            validationFailures ++
            // remove the setting and create a new one using the foundMap.type
            try {
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}"
            } catch (e) {
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false
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
                    } else {
                        // format the settingValue as a string of the integer value
                        newValue = String.format('%d', oldSettingValue)
                    }
                }
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
                } catch (e2) {
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false
                }
            }
        }
        total ++
    }
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}"
    return true
}

public String fingerprintIt(Map profileMap, Map fingerprint) {
    if (profileMap == null) { return 'profileMap is null' }
    if (fingerprint == null) { return 'fingerprint is null' }
    Map defaultFingerprint = profileMap.defaultFingerprint ?: [:]
    // if there is no defaultFingerprint, use the fingerprint as is
    if (defaultFingerprint == [:]) {
        return fingerprint.toString()
    }
    // for the missing keys, use the default values
    String fingerprintStr = ''
    defaultFingerprint.each { key, value ->
        String keyValue = fingerprint[key] ?: value
        fingerprintStr += "${key}:'${keyValue}', "
    }
    // remove the last comma and space
    fingerprintStr = fingerprintStr[0..-3]
    return fingerprintStr
}

// debug/test method - prints all fingerprints in the g_deviceFingerprintsV4 map
public void printFingerprintsV4() {
    int count = 0
    String fingerprintsText = "printFingerprintsV4: <br>"
    
    if (g_deviceFingerprintsV4 != null && !g_deviceFingerprintsV4?.isEmpty()) {
        g_deviceFingerprintsV4.each { profileName, profileData ->
            profileData.fingerprints?.each { fingerprint ->
                fingerprintsText += "  ${fingerprint}<br>"
                count++
            }
        }
    } else {
        fingerprintsText += "<b>No g_deviceFingerprintsV4 available!</b><br>"
    }
    fingerprintsText += "<br><b>Total fingerprints: ${count}</b> size of fingerprintsText=${fingerprintsText.length()}"
    logInfo fingerprintsText
}

public void printPreferences() {
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}"
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) {
        (DEVICE?.preferences).each { key, value ->
            Map inputMap = inputIt(key, true)   // debug = true
            if (inputMap != null && inputMap != [:]) {
                log.info inputMap
            }
        }
    }
}

void profilesV4info() {
    int size = g_deviceProfilesV4?.size() ?: 0
    int fingerprintSize = g_deviceFingerprintsV4?.size() ?: 0
    int currentProfileSize = g_currentProfilesV4?.size() ?: 0
    List keys = g_deviceProfilesV4 ? new ArrayList(g_deviceProfilesV4.keySet()) : []
    List fingerprintKeys = g_deviceFingerprintsV4 ? new ArrayList(g_deviceFingerprintsV4.keySet()) : []
    List currentProfileKeys = g_currentProfilesV4 ? new ArrayList(g_currentProfilesV4.keySet()) : []
    
    // Count computed fingerprints
    int totalComputedFingerprints = 0
    g_deviceFingerprintsV4.each { key, value ->
        totalComputedFingerprints += value.computedFingerprints?.size() ?: 0
    }
    
    String dni = device?.deviceNetworkId
    boolean hasCurrentProfile = g_currentProfilesV4.containsKey(dni)
    
    // Check for custom profiles
    boolean hasCustomProfiles = (dni && this.hasProperty('g_customProfilesV4') && g_customProfilesV4?.containsKey(dni))
    int customProfileCount = hasCustomProfiles ? (g_customProfilesV4[dni]?.size() ?: 0) : 0
    
    // Get profile source information from state
    String lastJSONSource = state.profilesV4?.get('lastJSONSource') ?: 'unknown'
    String customFilename = state.profilesV4?.get('customFilename')
    String customVersion = state.profilesV4?.get('customVersion')
    String customTimestamp = state.profilesV4?.get('customTimestamp')
    String standardVersion = state.profilesV4?.get('version')
    String standardTimestamp = state.profilesV4?.get('timestamp')
    
    // Determine current source
    String currentSource = hasCustomProfiles ? "CUSTOM" : "STANDARD"
    
    logInfo "profilesV4info: ========== STANDARD PROFILES =========="
    logInfo "profilesV4info: g_deviceProfilesV4 size=${size} keys=${keys}"
    if (standardVersion) {
        logInfo "profilesV4info: Standard JSON version=${standardVersion}, timestamp=${standardTimestamp}"
    }
    logInfo "profilesV4info: g_deviceFingerprintsV4 size=${fingerprintSize}"
    logInfo "profilesV4info: total computed fingerprint strings=${totalComputedFingerprints}"
    
    if (hasCustomProfiles) {
        logInfo "profilesV4info: ========== CUSTOM PROFILES =========="
        logInfo "profilesV4info: g_customProfilesV4 for device ${dni}: ${customProfileCount} profiles"
        List customKeys = g_customProfilesV4[dni] ? new ArrayList(g_customProfilesV4[dni].keySet()) : []
        logInfo "profilesV4info: Custom profile keys=${customKeys}"
        if (customFilename) {
            logInfo "profilesV4info: Custom filename='${customFilename}'"
        }
        if (customVersion) {
            logInfo "profilesV4info: Custom version=${customVersion}, timestamp=${customTimestamp}"
        }
    }
    
    logInfo "profilesV4info: ========== CURRENT DEVICE STATUS =========="
    logInfo "profilesV4info: Device ${dni} is using <b>${currentSource}</b> profiles"
    logInfo "profilesV4info: Last JSON source: ${lastJSONSource}"
    logInfo "profilesV4info: Current device profile name: ${state.deviceProfile ?: 'UNKNOWN'}"
    logInfo "profilesV4info: g_currentProfilesV4 size=${currentProfileSize} keys=${currentProfileKeys}"
    
    if (hasCurrentProfile) {
        Map currentProfile = g_currentProfilesV4[dni]
        logInfo "profilesV4info: Current profile for this device (${dni}) has ${currentProfile?.keySet()?.size() ?: 0} sections"
        if (currentProfile != null && !currentProfile.isEmpty()) {
            logInfo "profilesV4info: Current profile sections: ${currentProfile?.keySet()}"
            logInfo "profilesV4info: Current profile JSON data: ${JsonOutput.toJson(currentProfile)}"
        } else {
            logWarn "profilesV4info: Current profile is NULL or EMPTY"
        }
    }
    else {
        logWarn "profilesV4info: This device (${dni}) has NO current profile loaded"
    }
}

void clearProfilesCache() {
    g_deviceProfilesV4 = null
    g_deviceFingerprintsV4 = null
    g_currentProfilesV4 = null
    g_profilesLoaded = false
    g_profilesLoading = false
    
    // Also clear custom profiles for this device if any
    String dni = device?.deviceNetworkId
    if (dni && this.hasProperty('g_customProfilesV4') && g_customProfilesV4?.containsKey(dni)) {
        g_customProfilesV4.remove(dni)
        logDebug "clearProfilesCache: also cleared custom profiles for device ${dni}"
    }
}

void clearProfilesCacheInfo() {
    int before = g_deviceProfilesV4?.size() ?: 0
    int beforeFingerprints = g_deviceFingerprintsV4?.size() ?: 0
    int beforeCurrentProfiles = g_currentProfilesV4?.size() ?: 0
    
    // Check for custom profiles before clearing
    String dni = device?.deviceNetworkId
    boolean hadCustomProfiles = (dni && this.hasProperty('g_customProfilesV4') && g_customProfilesV4?.containsKey(dni))
    int customProfileCount = hadCustomProfiles ? (g_customProfilesV4[dni]?.size() ?: 0) : 0
    
    clearProfilesCache()
    
    if (hadCustomProfiles) {
        logInfo "clearProfilesCache: cleared ${before} V4 profiles, ${beforeFingerprints} fingerprint entries, ${beforeCurrentProfiles} current profiles, and ${customProfileCount} CUSTOM profiles for device ${dni}"
    } else {
        logInfo "clearProfilesCache: cleared ${before} V4 profiles, ${beforeFingerprints} fingerprint entries, and ${beforeCurrentProfiles} current profiles"
    }
}


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

/**
 * Returns the appropriate JSON filename based on state-based persistence
 * Checks state.profilesV4['lastJSONSource'] to determine standard vs custom
 * @return Filename to load (standard or custom)
 */
String getProfilesFilename() {
    // Check state-based persistence instead of preference
    if (state.profilesV4 == null) { state.profilesV4 = [:] }
    
    String lastSource = state.profilesV4['lastJSONSource']
    String customFilename = state.profilesV4['customJSONFilename']
    
    if (lastSource == 'custom' && customFilename != null && customFilename != '') {
        logDebug "getProfilesFilename: using CUSTOM JSON: ${customFilename} (lastJSONSource=${lastSource})"
        return customFilename
    } else {
        logDebug "getProfilesFilename: using STANDARD JSON: ${DEFAULT_PROFILES_FILENAME} (lastJSONSource=${lastSource})"
        return DEFAULT_PROFILES_FILENAME
    }
}

@Field static boolean g_OneTimeProfileLoadAttempted = false

boolean loadProfilesFromJSON() {
    if (isInCooldown()) {
        logDebug "loadProfilesFromJSON: in cooldown period, skipping profile load attempt"
        return false
    }
    String fileName = getProfilesFilename()
    state.profilesV4['lastUsedHeFile'] = fileName
    def data = readFile(fileName)
    if (data == null) {
        logWarn "loadProfilesFromJSON: readFile returned null for ${fileName}"
        String lastError = state.profilesV4['lastReadFileError'] ?: 'unknown error'
        logWarn "loadProfilesFromJSON: lastReadFileError = ${lastError} for ${fileName} g_OneTimeProfileLoadAttempted=${g_OneTimeProfileLoadAttempted}"
        // if the file was not found, and we have not yet attempted to download it from GitHub, do it now
        if ((lastError.contains('Not Found') || lastError.contains('404')) && !g_OneTimeProfileLoadAttempted) {
            sendInfoEvent "loadProfilesFromJSON: file ${fileName} not found - one-time attempt to download from GitHub..."
            g_OneTimeProfileLoadAttempted = true
            clearProfilesCache()   // clear any partially loaded profiles
            runIn(2, 'oneTimeUpdateFromGitHub', [data: [fileName: fileName]])
        }
        startCooldownTimer()
        return false
    }
    return loadProfilesFromJSONstring(data)
}

void oneTimeUpdateFromGitHub(Map data) {
    String fileName = data?.fileName ?: DEFAULT_PROFILES_FILENAME
    String gitHubUrl = defaultGitHubURL  // Use default URL
    logDebug "oneTimeUpdateFromGitHub: attempting to download ${fileName} and update product profiles from GitHub url ${gitHubUrl}..."
    downloadFromGitHubAndSaveToHE(gitHubUrl)
}


// called froloadProfilesFromJSON 
def readFile(fName) {
    long contentStartTime = now()
    //uri = "http://${location.hub.localIP}:8080/local/deviceProfilesV4_mmWave.json"
    // URL-encode the filename to handle spaces and special characters
    String encodedFileName = URLEncoder.encode(fName, "UTF-8")
    uri = "http://${location.hub.localIP}:8080/local/${encodedFileName}"

    def params = [
        uri: uri,
        textParser: true,
    ]
    if (state.profilesV4 == null) { state.profilesV4 = [:] }
    state.profilesV4['lastReadFileError'] = ''
    try {
        httpGet(params) { resp ->
            if(resp!= null) {
                def data = resp.getData();
                logDebug "readFile: read ${data.length} chars from ${uri}"
                long contentEndTime = now()
                long contentDuration = contentEndTime - contentStartTime
                logDebug "Performance: Content=${contentDuration}ms"
                state.profilesV4['lastReadFileError'] = 'OK'
                return data
            }
            else {
                log.error "${device?.displayName}  Null Response"
                state.profilesV4['lastReadFileError'] = 'null response'
            }
        }
    } catch (exception) {
        log.error "${device?.displayName} Connection Exception: ${exception.message}"
        state.profilesV4['lastReadFileError'] = exception.message
        return null;
    }
}



boolean loadProfilesFromJSONstring(stringifiedJSON) {
    long startTime = now()
    
    // idempotent : don't re-parse if already populated
    if (g_deviceProfilesV4 != null && !g_deviceProfilesV4?.isEmpty()) {
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
            startCooldownTimer()
            return false
        }
        resetCooldownFlag()
        
        // Extract version and timestamp metadata
        if (state.profilesV4 == null) { state.profilesV4 = [:] }
        state.profilesV4['version'] = parsed?.version ?: 'unknown'
        state.profilesV4['timestamp'] = parsed?.timestamp ?: 'unknown'
        logDebug "loadProfilesFromJSON: JSON version=${state.profilesV4['version']}, timestamp=${state.profilesV4['timestamp']}"
        
        // !!!!!!!!!!!!!!!!!!!!!!!
        // Populate g_deviceProfilesV4
        if (g_deviceProfilesV4 == null) { g_deviceProfilesV4 = [:] }   // initialize if null
        else { g_deviceProfilesV4.clear() }                             // clear existing entries if any
        g_deviceProfilesV4.putAll(dp as Map)
        logDebug "loadProfilesFromJSON: g_deviceProfilesV4 populated with ${g_deviceProfilesV4.size()} profiles"

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
        if (g_deviceFingerprintsV4 == null) { g_deviceFingerprintsV4 = [:] }   // initialize if null
        else { g_deviceFingerprintsV4.clear() }                             // clear existing entries if any
        g_deviceFingerprintsV4.putAll(localFingerprints)
        logDebug "loadProfilesFromJSON: g_deviceFingerprintsV4 populated with ${g_deviceFingerprintsV4.size()} entries"

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
        startCooldownTimer()
        return false
    }
}


void startCooldownTimer() {
    if (g_loadProfilesCooldown) {
        return
    }
    g_loadProfilesCooldown = true
    runInMillis(LOAD_PROFILES_COOLDOWN_MS, resetCooldownFlag, [overwrite: true])
    logWarn "startCooldownTimer: starting cooldown timer for ${LOAD_PROFILES_COOLDOWN_MS} ms to prevent multiple profile loading attempts"
}

void resetCooldownFlag() {
    g_loadProfilesCooldown = false
    logDebug "resetCooldownFlag: cooldown period ended, can attempt profile loading again"
}

boolean isInCooldown() {
    return g_loadProfilesCooldown
}



/**
 * Ensures that device profiles are loaded with thread-safe lazy loading
 * This is the main function that should be called before accessing g_deviceProfilesV4
 * @return true if profiles are loaded successfully, false otherwise
 */
private boolean ensureProfilesLoaded() {
    // Fast path: already loaded
//    if (!g_deviceProfilesV4.isEmpty() && g_profilesLoaded) {
    if (g_profilesLoaded && !g_currentProfilesV4?.isEmpty()) {       // !!!!!!!!!!!!!!!!!!!!!!!! TODO - check !!!!!!!!!!!!!!!!!!!!!!!!!!
        return true
    }
    if (state.profilesV4 == null) { state.profilesV4 = [:] }   // initialize state variable if not present
    if (isInCooldown()) {
        state.profilesV4['cooldownSkipsCtr'] = (state.profilesV4['cooldownSkipsCtr'] ?: 0) + 1
        logDebug "ensureProfilesLoaded: in cooldown period, skipping profile load attempt"
        return false
    }
    // Check if another thread is already loading
    if (g_profilesLoading) {
        // Wait briefly for other thread to finish
        for (int i = 0; i < 10; i++) {
            state.profilesV4['waitForOtherThreadCtr'] = (state.profilesV4['waitForOtherThreadCtr'] ?: 0) + 1
            logInfo "ensureProfilesLoaded: waiting <b>100ms</b> for other thread to finish loading... try ${i+1}/10"
            pauseExecution(100)
            if (g_profilesLoaded && !g_deviceProfilesV4?.isEmpty()) {
                sendInfoEvent "ensureProfilesLoaded: other thread finished loading"
                return true
            }
        }
        // If still loading after wait, return false - don't interfere with other thread
        sendInfoEvent "ensureProfilesLoaded: timeout waiting for other thread, giving up!"
        state.profilesV4['waitForOtherThreadTimeouts'] = (state.profilesV4['waitForOtherThreadTimeouts'] ?: 0) + 1
        return false
    }
    
    // Acquire loading lock
    g_profilesLoading = true
    try {
        // Double-check after acquiring lock
        Boolean isEmpty = (g_deviceProfilesV4 == null) ? true : g_deviceProfilesV4?.isEmpty()
        if (isEmpty || !g_profilesLoaded) {
            state.profilesV4['loadProfilesCtr'] = (state.profilesV4['loadProfilesCtr'] ?: 0) + 1
            logDebug "ensureProfilesLoaded: loading device profiles...(isEmpty=${isEmpty}, g_profilesLoaded=${g_profilesLoaded})"
            boolean result = loadProfilesFromJSON()
            if (result) {
                g_profilesLoaded = true
                String version = state.profilesV4?.version ?: 'unknown'
                String timestamp = state.profilesV4?.timestamp ?: 'unknown'
                sendInfoEvent "Successfully loaded ${g_deviceProfilesV4.size()} deviceProfilesV4 profiles (version: ${version}, timestamp: ${timestamp})  ⚠️ Refresh this page to see updated profiles in the dropdown!"
            } else {
                sendInfoEvent "ensureProfilesLoaded: failed to load device profiles (loadProfilesFromJSON() failed)"
                startCooldownTimer()
            }
            g_profilesLoading = false
            
            // State-based persistence: Check if we should auto-load custom profiles
            String lastSource = state.profilesV4?.get('lastJSONSource')
            String customFilename = state.profilesV4?.get('customFilename')
            String dni = device?.deviceNetworkId
            
            if (result && lastSource == 'custom' && customFilename != null && dni) {
                logDebug "ensureProfilesLoaded: lastJSONSource is 'custom', attempting to load ${customFilename} for device ${dni}"
                boolean customResult = loadCustomProfilesForDevice(dni, customFilename)
                if (!customResult) {
                    logWarn "ensureProfilesLoaded: failed to auto-load custom profiles from '${customFilename}', device will use standard profiles"
                    // Don't fail - standard profiles are still loaded and valid
                }
            } else if (result && lastSource == 'standard') {
                logDebug "ensureProfilesLoaded: lastJSONSource is 'standard', using standard profiles"
            }
            
            return result  // Return true if standard profiles loaded, even if custom failed
        }
        
        return true
    } finally {
        state.profilesV4['loadProfilesExceptionsCtr'] = (state.profilesV4['loadProfilesExceptionsCtr'] ?: 0) + 1
        g_profilesLoading = false
    }
}

/**
 * Downloads and loads STANDARD device profiles from GitHub
 * Saves to local storage and remembers this choice after reboot
 */
void loadStandardProfilesFromGitHub() {
    logInfo "loadStandardProfilesFromGitHub: downloading and loading STANDARD profiles from GitHub"
    
    // Download from GitHub and save to local storage
    downloadFromGitHubAndSaveToHE(defaultGitHubURL)
    
    // Clear all cached profiles
    clearProfilesCache()
    
    // Load standard profiles
    boolean result = ensureProfilesLoaded()
    
    if (result) {
        // Remember this choice - user explicitly chose standard profiles
        if (state.profilesV4 == null) { state.profilesV4 = [:] }
        state.profilesV4['lastJSONSource'] = 'standard'
        state.profilesV4.remove('customJSONFilename')  // Clear custom filename
        
        // Clear any custom profiles for this device
        String dni = device?.deviceNetworkId
        if (dni && g_customProfilesV4?.containsKey(dni)) {
            g_customProfilesV4.remove(dni)
            clearCustomJSONAttribute()
        }
        
        ensureCurrentProfileLoaded()
        
        // Update deviceProfileFile attribute to show currently loaded file
        sendEvent(name: 'deviceProfileFile', value: DEFAULT_PROFILES_FILENAME, type: 'digital')
        
        String version = state.profilesV4?.version ?: 'unknown'
        String timestamp = state.profilesV4?.timestamp ?: 'unknown'
        logInfo "✅ Successfully loaded STANDARD profiles from GitHub (version: ${version}, timestamp: ${timestamp})"
        sendInfoEvent "Standard profiles loaded from GitHub. Press F5 to refresh the page."
    } else {
        sendInfoEvent "❌ Failed to download standard profiles from GitHub"
    }
}

// Backward compatibility - redirect old command to new one
void updateFromGitHub() {
    logDebug "updateFromGitHub: redirecting to loadStandardProfilesFromGitHub()"
    loadStandardProfilesFromGitHub()
}

/**
 * Reloads STANDARD device profiles from Hubitat local storage
 * Use after manual edits to the local standard JSON file
 * Remembers this choice after reboot
 */
void loadStandardProfilesFromLocalStorage() {
    logInfo "loadStandardProfilesFromLocalStorage: reloading STANDARD device profiles from Hubitat local storage (${DEFAULT_PROFILES_FILENAME})"
    
    // Clear all cached profiles
    clearProfilesCache()
    
    // Load standard profiles from local storage
    boolean result = ensureProfilesLoaded()
    
    if (result) {
        // Remember this choice - user explicitly chose standard profiles
        if (state.profilesV4 == null) { state.profilesV4 = [:] }
        state.profilesV4['lastJSONSource'] = 'standard'
        state.profilesV4.remove('customJSONFilename')  // Clear custom filename
        
        // Clear any custom profiles for this device
        String dni = device?.deviceNetworkId
        if (dni && g_customProfilesV4?.containsKey(dni)) {
            g_customProfilesV4.remove(dni)
            clearCustomJSONAttribute()
        }
        
        ensureCurrentProfileLoaded()
        
        // Update deviceProfileFile attribute to show currently loaded file
        sendEvent(name: 'deviceProfileFile', value: DEFAULT_PROFILES_FILENAME, type: 'digital')
        
        String version = state.profilesV4?.version ?: 'unknown'
        String timestamp = state.profilesV4?.timestamp ?: 'unknown'
        logInfo "✅ Successfully loaded STANDARD profiles (version: ${version}, timestamp: ${timestamp})"
        sendInfoEvent "Standard profiles loaded from ${DEFAULT_PROFILES_FILENAME}. Press F5 to refresh the page."
    } else {
        sendInfoEvent "❌ Failed to reload standard device profiles from local storage"
    }
}

// Backward compatibility - redirect old command to new one
void updateFromLocalStorage() {
    logDebug "updateFromLocalStorage: redirecting to loadStandardProfilesFromLocalStorage()"
    loadStandardProfilesFromLocalStorage()
}

// Backward compatibility alias
void loadStandardProfiles() {
    logDebug "loadStandardProfiles: redirecting to loadStandardProfilesFromLocalStorage()"
    loadStandardProfilesFromLocalStorage()
}

/**
 * Loads CUSTOM device profiles from a specific JSON file on Hubitat local storage
 * Remembers this choice after reboot via state persistence
 * @param filename Custom JSON filename (e.g., "deviceProfilesV4_custom.json")
 */
void loadUserCustomProfilesFromLocalStorage(String filename) {
    String trimmedFilename = filename?.trim() ?: ""
    
    // If filename is empty, check the deviceProfileFile attribute
    if (trimmedFilename.isEmpty()) {
        String attrValue = device.currentValue('deviceProfileFile')
        if (attrValue != null && !attrValue.isEmpty() && attrValue != DEFAULT_PROFILES_FILENAME) {
            logInfo "loadUserCustomProfilesFromLocalStorage: using deviceProfileFile attribute value: ${attrValue}"
            trimmedFilename = attrValue
        } else {
            logWarn "loadUserCustomProfilesFromLocalStorage: filename parameter is required and deviceProfileFile attribute is empty or default"
            sendInfoEvent "❌ Custom JSON filename is required"
            return
        }
    }
    logInfo "loadUserCustomProfilesFromLocalStorage: loading CUSTOM device profiles from ${trimmedFilename}"
    
    // Clear all cached profiles first
    clearProfilesCache()
    
    // First ensure standard profiles are loaded (needed for fallback)
    boolean standardResult = ensureProfilesLoaded()
    
    if (!standardResult) {
        sendInfoEvent "❌ Failed to load standard profiles - cannot proceed with custom profiles"
        return
    }
    
    // Load custom profiles for this device
    String dni = device?.deviceNetworkId
    if (!dni) {
        logWarn "loadCustomProfiles: device DNI is null"
        sendInfoEvent "❌ Device DNI is null - cannot load custom profiles"
        return
    }
    
    boolean customResult = loadCustomProfilesForDevice(dni, trimmedFilename)
    
    if (customResult) {
        // Remember this choice - user explicitly chose custom profiles
        if (state.profilesV4 == null) { state.profilesV4 = [:] }
        state.profilesV4['lastJSONSource'] = 'custom'
        state.profilesV4['customJSONFilename'] = trimmedFilename
        
        // Reload current profile with custom data
        ensureCurrentProfileLoaded()
        
        // Update deviceProfileFile attribute to show currently loaded custom file
        sendEvent(name: 'deviceProfileFile', value: trimmedFilename, type: 'digital')
        
        String version = state.profilesV4?.customVersion ?: 'unknown'
        String timestamp = state.profilesV4?.customTimestamp ?: 'unknown'
        int count = state.profilesV4?.customProfileCount ?: 0
        logInfo "✅ Successfully loaded CUSTOM profiles from ${trimmedFilename} (version: ${version}, timestamp: ${timestamp}, profiles: ${count})"
        sendInfoEvent "Custom profiles loaded from ${trimmedFilename}. Press F5 to refresh the page."
    } else {
        sendInfoEvent "❌ Failed to load custom profiles from ${trimmedFilename}"
    }
}



// updateFromGitHub command - download JSON profiles from GitHub and store to Hubitat local storage
void downloadFromGitHubAndSaveToHE(String url) {
    long startTime = now()
    String gitHubUrl = url
    String fileName = DEFAULT_PROFILES_FILENAME
    
    // If URL is provided, try to extract filename from it
    if (url?.trim()) {
        try {
            String urlPath = gitHubUrl.split('/').last()
            if (urlPath.toLowerCase().endsWith('.json')) {
                fileName = urlPath
            }
        } catch (Exception e) { }
    }
    logInfo "updateFromGitHub: downloading ${fileName} from ${gitHubUrl}"
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
                //logInfo "updateFromGitHub: downloaded ${jsonContent.length()} characters"
                //logDebug "updateFromGitHub: first 100 chars: ${jsonContent.take(100)}"
                //logInfo "updateFromGitHub: Performance - Download: ${downloadDuration}ms"
                sendInfoEvent "Successfully downloaded ${fileName} from GitHub, ${jsonContent.length()} characters in ${downloadDuration}ms"
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

                    sendInfoEvent "updateFromGitHub: Successfully uploaded ${fileName} to Hubitat local storage, ${uploadDuration}ms, ${fileBytes.length} bytes"
                    //logInfo "updateFromGitHub: File size: ${jsonContent.length()} characters"
                    //logInfo "updateFromGitHub: Performance - Upload: ${uploadDuration}ms"
                    //sendInfoEvent "Successfully updated ${fileName} (${jsonContent.length()} characters) in Hubitat local storage"
                    
                    // Optional: Clear current profiles to force reload on next access
                    //g_deviceProfilesV4.clear()
                    //g_deviceFingerprintsV4.clear()
                    //g_currentProfilesV4.clear()
                    /*
                    g_deviceProfilesV4 = null
                    g_deviceFingerprintsV4 = null
                    g_currentProfilesV4 = null
                    g_profilesLoaded = false
                    g_profilesLoading = false
                    
                    logInfo "updateFromGitHub: Cleared cached profiles - they will be reloaded on next access"
                    */
                    
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


